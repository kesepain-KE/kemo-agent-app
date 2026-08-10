package com.kesepain.kemoapp.data.stream

import com.kesepain.kemoapp.data.remote.ApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable enum class ChatRole { USER, ASSISTANT }
@Serializable enum class ToolStatus { RUNNING, SUCCESS, FAILED }

@Serializable
data class ToolCallUi(
    val callId: String,
    val name: String,
    val arguments: String,
    val status: ToolStatus,
    val resultPreview: String = "",
)

@Serializable
data class ChatUsageUi(
    val totalTokens: Long = 0,
    val cacheHitRate: Double = 0.0,
    val elapsedMs: Long = 0,
)

@Serializable
data class ChatAttachmentUi(val name: String, val path: String)

@Serializable
data class ChatEntry(
    val id: String,
    val role: ChatRole,
    val text: String = "",
    val reasoning: String = "",
    val tools: List<ToolCallUi> = emptyList(),
    val usage: ChatUsageUi? = null,
    val attachments: List<ChatAttachmentUi> = emptyList(),
    val startedAtMs: Long = 0,
)

sealed interface StreamEvent {
    data class Reasoning(val text: String) : StreamEvent
    data class Text(val text: String) : StreamEvent
    data class ToolStart(val call: ToolCallUi) : StreamEvent
    data class ToolEnd(val callId: String, val status: ToolStatus, val resultPreview: String) : StreamEvent
    data class Usage(val value: ChatUsageUi) : StreamEvent
    data class Error(val message: String) : StreamEvent
    data class Done(val value: ChatUsageUi?) : StreamEvent
}

class ChatStreamParser {
    fun parse(line: String): StreamEvent? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isBlank()) return null
        if (payload == "[DONE]") return StreamEvent.Done(null)
        val root = runCatching { ApiClient.json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        return when (root.text("type")) {
            "reasoning_delta" -> root.deltaText()?.let(StreamEvent::Reasoning)
            "text_delta" -> root.deltaText()?.let(StreamEvent::Text)
            "tool_call_start" -> parseToolStart(root)
            "tool_call_result" -> parseToolEnd(root)
            "usage" -> root.usageUi()?.let(StreamEvent::Usage)
            "error" -> StreamEvent.Error(root["error"].preview(500).ifBlank { root.text("message") })
            "done" -> StreamEvent.Done(root.usageUi(includeElapsed = true))
            else -> null
        }
    }

    private fun parseToolStart(root: JsonObject): StreamEvent.ToolStart {
        val nested = root["tool_call"] as? JsonObject
        val callId = root.text("tool_call_id", "call_id", "id").ifBlank { nested?.text("id", "tool_call_id").orEmpty() }
        val name = root.text("tool_name", "name").ifBlank { nested?.text("name", "tool_name").orEmpty() }
        val arguments = (root["arguments"] ?: nested?.get("arguments")).preview(MAX_TOOL_DETAIL)
        return StreamEvent.ToolStart(ToolCallUi(callId.ifBlank { "tool-${name.hashCode()}" }, name, arguments, ToolStatus.RUNNING))
    }

    private fun parseToolEnd(root: JsonObject): StreamEvent.ToolEnd {
        val nested = root["tool_call"] as? JsonObject
        val result = root["result"] ?: root["output"]
        val resultObject = result as? JsonObject
        val callId = root.text("tool_call_id", "call_id", "id").ifBlank { nested?.text("id", "tool_call_id").orEmpty() }
        val ok = (resultObject?.get("ok") as? JsonPrimitive)?.booleanOrNull
        val failed = ok == false || resultObject?.containsKey("error") == true || root.text("status").equals("failed", true)
        return StreamEvent.ToolEnd(
            callId = callId,
            status = if (failed) ToolStatus.FAILED else ToolStatus.SUCCESS,
            resultPreview = result.preview(MAX_TOOL_DETAIL),
        )
    }

    private fun JsonObject.deltaText(): String? = text("delta", "text", "content").takeIf { it.isNotEmpty() }

    private fun JsonObject.usageUi(includeElapsed: Boolean = false): ChatUsageUi? {
        val usage = this["usage"] as? JsonObject ?: return null
        val prompt = usage.long("prompt_tokens", "input_tokens")
        val total = usage.long("total_tokens").takeIf { it > 0 }
            ?: (prompt + usage.long("completion_tokens", "output_tokens"))
        val cached = usage.long("cached_prompt_tokens", "cached_input_tokens", "cache_hit_tokens", "cached_tokens")
        val declaredRate = usage.double("cache_hit_rate")
        val rate = declaredRate ?: if (prompt > 0) cached.toDouble() / prompt.toDouble() else 0.0
        val metadata = this["metadata"] as? JsonObject
        val elapsed = if (includeElapsed) metadata?.long("elapsed_ms") ?: 0L else 0L
        return ChatUsageUi(totalTokens = total, cacheHitRate = rate.coerceIn(0.0, 1.0), elapsedMs = elapsed)
    }

    private fun JsonObject.text(vararg keys: String): String {
        for (key in keys) {
            val primitive = this[key] as? JsonPrimitive ?: continue
            primitive.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    private fun JsonObject.long(vararg keys: String): Long {
        for (key in keys) (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let { return it }
        return 0L
    }

    private fun JsonObject.double(vararg keys: String): Double? {
        for (key in keys) (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.let { return it }
        return null
    }

    private fun JsonElement?.preview(limit: Int): String {
        val value = when (this) {
            null -> ""
            is JsonPrimitive -> contentOrNull.orEmpty()
            else -> toString()
        }
        return value.take(limit) + if (value.length > limit) "…" else ""
    }

    companion object { private const val MAX_TOOL_DETAIL = 2_000 }
}

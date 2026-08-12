package com.kesepain.kemoapp.ui.screens.chat

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal data class ContextTokenSnapshotUi(
    val available: Boolean,
    val systemPromptTokens: Long,
    val toolSchemaTokens: Long,
    val conversationTokens: Long,
    val summaryTokens: Long,
    val otherTokens: Long,
    val totalTokens: Long,
    val capacityTokens: Long,
    val percent: Double,
    val source: String,
    val measurement: String,
)

internal fun contextTokenSnapshot(
    status: JsonElement?,
    fallbackUsed: Long = 0L,
    fallbackCapacity: Long = 0L,
    fallbackPercent: Double = 0.0,
): ContextTokenSnapshotUi {
    val root = status as? JsonObject
    val overview = root.obj("overview")
    val runtime = root.obj("runtime")
    val hasSnapshot = overview.containsKey("context_snapshot")
    val primary = if (hasSnapshot) overview.obj("context_snapshot") else overview.obj("context_window").obj("tokens")
    val runtimeContext = runtime.obj("context")
    val explicitAvailable = primary.bool("available")
    val source = primary.text("source")
    val available = if (hasSnapshot) {
        explicitAvailable == true
    } else {
        primary.isNotEmpty() && !source.equals("unavailable", ignoreCase = true)
    }

    val system = primary.long("system_prompt_tokens") ?: 0L
    val tools = primary.long("tool_schema_tokens") ?: 0L
    val conversation = primary.long("conversation_tokens") ?: 0L
    val summary = primary.long("summary_tokens") ?: 0L
    val other = primary.long("other_tokens") ?: 0L
    val breakdown = system + tools + conversation + summary + other
    val total = primary.long("total_tokens")
        ?: breakdown.takeIf { it > 0L }
        ?: primary.long("context_tokens")
        ?: runtimeContext.long("used_tokens")
        ?: fallbackUsed
    val capacity = primary.long("capacity_tokens")
        ?: runtimeContext.long("max_tokens")
        ?: fallbackCapacity
    val percent = primary.decimal("percent")
        ?: runtimeContext.decimal("percent")
        ?: if (capacity > 0L) total.toDouble() / capacity.toDouble() * 100.0 else fallbackPercent

    return ContextTokenSnapshotUi(
        available = available,
        systemPromptTokens = system.coerceAtLeast(0L),
        toolSchemaTokens = tools.coerceAtLeast(0L),
        conversationTokens = conversation.coerceAtLeast(0L),
        summaryTokens = summary.coerceAtLeast(0L),
        otherTokens = other.coerceAtLeast(0L),
        totalTokens = total.coerceAtLeast(0L),
        capacityTokens = capacity.coerceAtLeast(0L),
        percent = percent.coerceIn(0.0, 100.0),
        source = source,
        measurement = primary.text("measurement"),
    )
}

private fun JsonObject?.obj(key: String): JsonObject = this?.get(key) as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.decimal(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

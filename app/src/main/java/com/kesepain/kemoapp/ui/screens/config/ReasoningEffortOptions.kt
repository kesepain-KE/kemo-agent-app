package com.kesepain.kemoapp.ui.screens.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal val CHAT_REASONING_EFFORTS = listOf("minimal", "low", "medium", "high", "max")

internal data class ReasoningEffortOptions(
    val protocol: String,
    val model: String,
    val selected: String,
    val options: List<String>,
    val available: Boolean,
    val warning: String = "",
)

internal fun reasoningEffortOptions(
    config: JsonElement?,
    capabilities: JsonElement?,
): ReasoningEffortOptions {
    val draft = UserConfigDraft.from(config)
    return reasoningEffortOptions(
        protocol = draft.providerType,
        model = draft.model,
        configuredEffort = draft.reasoningEffort,
        capabilities = capabilities,
    )
}

internal fun reasoningEffortOptions(
    protocol: String,
    model: String,
    configuredEffort: String,
    capabilities: JsonElement?,
): ReasoningEffortOptions {
    val normalizedProtocol = protocol.trim().lowercase().ifBlank { "chat" }
    val configured = configuredEffort.trim().ifBlank { "medium" }
    if (normalizedProtocol != "kemo") {
        val selected = configured.takeIf { it in CHAT_REASONING_EFFORTS } ?: "medium"
        return ReasoningEffortOptions(
            protocol = "chat",
            model = model,
            selected = selected,
            options = CHAT_REASONING_EFFORTS,
            available = true,
        )
    }

    val root = capabilities as? JsonObject ?: JsonObject(emptyMap())
    val responseModel = root.text("model")
    val matchesModel = responseModel.isBlank() || responseModel == model
    val reasoning = root.obj("capabilities").obj("reasoning")
    val supported = reasoning.bool("supported") == true
    val efforts = if (matchesModel && supported) {
        (reasoning["efforts"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .map(String::trim)
            .filter(::isValidReasoningEffort)
            .distinct()
    } else {
        emptyList()
    }
    val selected = when {
        configured in efforts -> configured
        "medium" in efforts -> "medium"
        efforts.isNotEmpty() -> efforts.first()
        else -> configured
    }
    return ReasoningEffortOptions(
        protocol = "kemo",
        model = model,
        selected = selected,
        options = efforts,
        available = efforts.isNotEmpty(),
        warning = root.text("warning"),
    )
}

private fun isValidReasoningEffort(value: String): Boolean =
    value.isNotBlank() &&
        value.length <= 64 &&
        !value.equals("none", ignoreCase = true) &&
        value.none(Char::isISOControl)

private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

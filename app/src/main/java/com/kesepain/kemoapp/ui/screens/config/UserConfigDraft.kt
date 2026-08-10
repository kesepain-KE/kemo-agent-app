package com.kesepain.kemoapp.ui.screens.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class UserConfigDraft(
    val providerType: String = "chat",
    val model: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val stream: Boolean = true,
    val reasoningEffort: String = "medium",
    val imageInput: Boolean = false,
    val audioInput: Boolean = false,
    val videoInput: Boolean = false,
    val fileInput: Boolean = false,
    val agentDefault: String = "",
    val agentCheap: String = "",
    val agentReasoning: String = "",
    val multimodal: Map<String, String> = MULTIMODAL_KEYS.associateWith { "" },
    val visionRouting: String = "auto",
    val useSharedKnowledge: Boolean = true,
    val useGlobalKnowledge: Boolean = true,
    val expandPromptInjection: Boolean = true,
    val expandRealtimeInjection: Boolean = false,
    val perceptionPromptInjection: Boolean = true,
    val perceptionRealtimeInjection: Boolean = false,
    val skillsSharedWhitelist: String = "",
    val expandSharedWhitelist: String = "",
    val expandGlobalWhitelist: String = "",
    val perceptionGlobalWhitelist: String = "",
    val pluginsWhitelist: String = "",
    val taskPlanAutoAccept: Boolean = false,
) {
    fun toChanges(): JsonObject = buildJsonObject {
        put("provider", buildJsonObject {
            put("type", providerType)
            put("model", model)
            put("base_url", baseUrl)
            if (apiKey.isNotBlank()) put("api_key", apiKey)
            put("stream", stream)
            put("reasoning_effort", reasoningEffort)
            put("input_modalities", buildJsonArray {
                add(JsonPrimitive("text"))
                if (imageInput) add(JsonPrimitive("image"))
                if (audioInput) add(JsonPrimitive("audio"))
                if (videoInput) add(JsonPrimitive("video"))
                if (fileInput) add(JsonPrimitive("file"))
            })
        })
        put("agent_models", buildJsonObject {
            put("default", agentDefault); put("cheap", agentCheap); put("reasoning", agentReasoning)
        })
        put("multimodal_models", buildJsonObject { multimodal.forEach { (key, value) -> put(key, value) } })
        put("multimodal_routing", buildJsonObject { put("vision", visionRouting) })
        put("knowledge", buildJsonObject { put("use_shared", useSharedKnowledge); put("use_global", useGlobalKnowledge) })
        put("skills", buildJsonObject { put("shared_whitelist", stringArray(skillsSharedWhitelist)) })
        put("expand", buildJsonObject {
            put("shared_whitelist", stringArray(expandSharedWhitelist)); put("global_whitelist", stringArray(expandGlobalWhitelist))
            put("prompt_injection", expandPromptInjection); put("realtime_injection", expandRealtimeInjection)
        })
        put("perception", buildJsonObject {
            put("global_whitelist", stringArray(perceptionGlobalWhitelist))
            put("prompt_injection", perceptionPromptInjection); put("realtime_injection", perceptionRealtimeInjection)
        })
        put("plugins", buildJsonObject { put("whitelist", stringArray(pluginsWhitelist)) })
        put("task_plan", buildJsonObject { put("auto_accept", taskPlanAutoAccept) })
    }

    companion object {
        val MULTIMODAL_KEYS = listOf("vision", "image_generation", "image_edit", "audio_transcription", "speech_generation", "speech_to_speech", "video_understanding", "video_generation")

        fun from(value: JsonElement?): UserConfigDraft {
            val envelope = value as? JsonObject ?: return UserConfigDraft()
            val root = envelope["config"] as? JsonObject ?: envelope
            val provider = root.obj("provider")
            val agentModels = root.obj("agent_models")
            val multimodal = root.obj("multimodal_models")
            val multimodalRouting = root.obj("multimodal_routing")
            val knowledge = root.obj("knowledge")
            val skills = root.obj("skills")
            val expand = root.obj("expand")
            val perception = root.obj("perception")
            val plugins = root.obj("plugins")
            val taskPlan = root.obj("task_plan")
            val modalities = (provider["input_modalities"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
            return UserConfigDraft(
                providerType = provider.text("type").ifBlank { "chat" },
                model = provider.text("model"),
                baseUrl = provider.text("base_url"),
                apiKey = "",
                stream = provider.bool("stream", true),
                reasoningEffort = provider.text("reasoning_effort").ifBlank { "medium" },
                imageInput = "image" in modalities,
                audioInput = "audio" in modalities,
                videoInput = "video" in modalities,
                fileInput = "file" in modalities,
                agentDefault = agentModels.text("default"), agentCheap = agentModels.text("cheap"), agentReasoning = agentModels.text("reasoning"),
                multimodal = MULTIMODAL_KEYS.associateWith { multimodal.text(it) },
                visionRouting = multimodalRouting.text("vision").ifBlank { "auto" },
                useSharedKnowledge = knowledge.bool("use_shared", true), useGlobalKnowledge = knowledge.bool("use_global", true),
                expandPromptInjection = expand.bool("prompt_injection", true), expandRealtimeInjection = expand.bool("realtime_injection", false),
                perceptionPromptInjection = perception.bool("prompt_injection", true), perceptionRealtimeInjection = perception.bool("realtime_injection", false),
                skillsSharedWhitelist = skills.listText("shared_whitelist"),
                expandSharedWhitelist = expand.listText("shared_whitelist"), expandGlobalWhitelist = expand.listText("global_whitelist"),
                perceptionGlobalWhitelist = perception.listText("global_whitelist"), pluginsWhitelist = plugins.listText("whitelist"),
                taskPlanAutoAccept = taskPlan.bool("auto_accept", false),
            )
        }
    }
}

private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.bool(key: String, default: Boolean): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: default
private fun JsonObject.listText(key: String): String = (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
private fun stringArray(value: String): JsonArray = buildJsonArray {
    value.split(',', '\n').map(String::trim).filter(String::isNotBlank).distinct().forEach { add(JsonPrimitive(it)) }
}

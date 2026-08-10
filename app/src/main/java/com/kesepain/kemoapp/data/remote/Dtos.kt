package com.kesepain.kemoapp.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class UserLoginDto(val username: String, val password: String)
@Serializable data class AuthResponseDto(@SerialName("session_token") val sessionToken: String = "", @SerialName("expires_at") val expiresAt: Long = 0, val username: String = "")
@Serializable data class ChatRequestDto(
    @SerialName("session_id") val sessionId: String,
    val prompt: String,
    @SerialName("run_id") val runId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("uploaded_files") val uploadedFiles: List<String> = emptyList(),
    @SerialName("reasoning_effort") val reasoningEffort: String = "medium",
)
@Serializable data class EventDto(val type: String, val ts: Long = 0, val data: JsonElement? = null)
data class ApiSecrets(val deviceToken: String, val sessionToken: String)

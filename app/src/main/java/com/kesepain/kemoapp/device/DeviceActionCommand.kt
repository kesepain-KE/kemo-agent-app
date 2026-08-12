package com.kesepain.kemoapp.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DeviceActionCommand(
    @SerialName("protocol_version") val protocolVersion: Int = 1,
    @SerialName("command_id") val commandId: String,
    val user: String = "",
    @SerialName("device_id") val deviceId: String = "",
    val action: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
    val confirmation: String = "system_ui",
    val status: String = "queued",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("expires_at") val expiresAt: Long = 0,
)

data class DeviceActionResult(
    val status: String,
    val detail: Map<String, String> = emptyMap(),
)

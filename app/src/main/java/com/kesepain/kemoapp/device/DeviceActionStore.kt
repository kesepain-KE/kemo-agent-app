package com.kesepain.kemoapp.device

import android.content.Context
import com.kesepain.kemoapp.data.remote.ApiClient
import kotlinx.serialization.json.put

object DeviceActionStore {
    private const val PREFS = "kemo_device_actions"
    private const val PENDING_PREFIX = "pending_command:"
    private const val PROCESSED = "processed_ids"
    private const val RESULT_PREFIX = "pending_result:"

    fun savePending(context: Context, command: DeviceActionCommand) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PENDING_PREFIX + command.commandId, ApiClient.json.encodeToString(DeviceActionCommand.serializer(), command))
            .apply()
    }

    fun getPending(context: Context, commandId: String): DeviceActionCommand? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = PENDING_PREFIX + commandId
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { ApiClient.json.decodeFromString(DeviceActionCommand.serializer(), raw) }.getOrNull()
    }

    fun removePending(context: Context, commandId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(PENDING_PREFIX + commandId)
            .apply()
    }

    data class PendingResult(
        val commandId: String,
        val user: String,
        val deviceId: String,
        val status: String,
        val detail: Map<String, String>,
    )

    fun recordResult(context: Context, command: DeviceActionCommand, status: String, detail: Map<String, String>) {
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("command_id", command.commandId)
            put("user", command.user)
            put("device_id", command.deviceId)
            put("status", status)
            put("detail", kotlinx.serialization.json.JsonObject(detail.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(RESULT_PREFIX + command.commandId, payload.toString())
            .apply()
    }

    fun pendingResults(context: Context, user: String, deviceId: String): List<PendingResult> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, raw) ->
            if (!key.startsWith(RESULT_PREFIX) || raw !is String) return@mapNotNull null
            val value = runCatching { ApiClient.json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject }.getOrNull() ?: return@mapNotNull null
            val id = (value["command_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val resultUser = (value["user"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val resultDeviceId = (value["device_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val status = (value["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val detail = (value["detail"] as? kotlinx.serialization.json.JsonObject)?.mapValues { (_, item) ->
                (item as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            }.orEmpty()
            if (id.isBlank() || status.isBlank() || resultUser != user || resultDeviceId != deviceId) null
            else PendingResult(id, resultUser, resultDeviceId, status, detail)
        }
    }

    fun removeResult(context: Context, commandId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(RESULT_PREFIX + commandId).apply()
    }

    fun isProcessed(context: Context, commandId: String): Boolean =
        commandId in context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(PROCESSED, emptySet()).orEmpty()

    fun markProcessed(context: Context, commandId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = LinkedHashSet(prefs.getStringSet(PROCESSED, emptySet()).orEmpty())
        values += commandId
        while (values.size > 100) values.remove(values.first())
        prefs.edit().putStringSet(PROCESSED, values).apply()
    }
}

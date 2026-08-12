package com.kesepain.kemoapp.device

import android.os.Build
import com.kesepain.kemoapp.KemoApp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DeviceCapabilityRegistry {
    fun payload(): JsonObject {
        val context = KemoApp.instance
        fun available(action: String, arguments: JsonObject): Boolean =
            DeviceActionExecutor.isSupported(
                context,
                DeviceActionCommand(commandId = "capability", action = action, arguments = arguments),
            )
        val alarmArgs = buildJsonObject { put("hour", 8); put("minute", 0) }
        val timerArgs = buildJsonObject { put("duration_seconds", 60) }
        val eventArgs = buildJsonObject {
            put("title", "Kemo")
            put("start_at", "2026-08-13T08:00:00+08:00")
            put("end_at", "2026-08-13T09:00:00+08:00")
        }
        val todoArgs = buildJsonObject { put("title", "Kemo") }
        return buildJsonObject {
            put("type", "device.capabilities")
            put("data", buildJsonObject {
                put("protocol_version", 1)
                put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                put("android_api", Build.VERSION.SDK_INT)
                put("actions", buildJsonObject {
                    putAction("alarm.create", available("alarm.create", alarmArgs))
                    putAction("timer.start", available("timer.start", timerArgs))
                    putAction("calendar.event.create", available("calendar.event.create", eventArgs))
                    putAction("todo.create", available("todo.create", todoArgs))
                })
            })
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putAction(name: String, available: Boolean) {
        put(name, buildJsonObject {
            put("available", available)
            put("execution_mode", "system_ui")
        })
    }
}

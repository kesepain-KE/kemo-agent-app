package com.kesepain.kemoapp.device

import android.content.Context

object DeviceActionReporter {
    private data class BoundSink(
        val user: String,
        val deviceId: String,
        val send: (String, String, Map<String, String>) -> Boolean,
    )

    @Volatile private var sink: BoundSink? = null

    fun attach(user: String, deviceId: String, value: (String, String, Map<String, String>) -> Boolean) {
        sink = BoundSink(user, deviceId, value)
    }
    fun detach() { sink = null }

    fun report(context: Context, command: DeviceActionCommand, result: DeviceActionResult) {
        val current = sink
        val delivered = current != null &&
            current.user == command.user &&
            current.deviceId == command.deviceId &&
            current.send(command.commandId, result.status, result.detail)
        if (delivered) DeviceActionStore.removeResult(context, command.commandId)
        else DeviceActionStore.recordResult(context, command, result.status, result.detail)
    }

    fun flush(context: Context, user: String, deviceId: String) {
        DeviceActionStore.pendingResults(context, user, deviceId).forEach { result ->
            val current = sink
            if (current != null && current.user == user && current.deviceId == deviceId &&
                current.send(result.commandId, result.status, result.detail)
            ) {
                DeviceActionStore.removeResult(context, result.commandId)
            }
        }
    }
}

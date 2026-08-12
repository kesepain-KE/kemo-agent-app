package com.kesepain.kemoapp.device

import com.kesepain.kemoapp.data.remote.ApiClient
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceActionCommandTest {
    @Test
    fun decodesBridgeCommandContract() {
        val command = ApiClient.json.decodeFromString(
            DeviceActionCommand.serializer(),
            """{"protocol_version":1,"command_id":"cmd_1","user":"kesepain","device_id":"phone","action":"timer.start","arguments":{"duration_seconds":120}}""",
        )
        assertEquals("cmd_1", command.commandId)
        assertEquals("timer.start", command.action)
        assertEquals(120, (command.arguments["duration_seconds"] as JsonPrimitive).content.toInt())
    }
}

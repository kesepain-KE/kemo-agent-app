package com.kesepain.kemoapp.device

import com.kesepain.kemoapp.data.remote.ApiClient
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceActionCommandTest {
    private fun projectFile(path: String): File {
        var directory = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("project file not found: $path")
    }

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

    @Test
    fun manifestDeclaresSystemHandlerVisibilityForEveryDeviceAction() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.intent.action.SET_ALARM"))
        assertTrue(manifest.contains("android.intent.action.SET_TIMER"))
        assertTrue(manifest.contains("vnd.android.cursor.item/event"))
        assertTrue(manifest.contains("vnd.android.cursor.item/task"))
    }
}

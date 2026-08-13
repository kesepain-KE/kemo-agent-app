package com.kesepain.kemoapp.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatKeepAliveContractTest {
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
    fun manifestDeclaresDataSyncForegroundServiceAndPermissions() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains(".chat.ChatKeepAliveService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
    }

    @Test
    fun streamLifecycleStartsAndStopsKeepAliveService() {
        val viewModel = projectFile(
            "app/src/main/java/com/kesepain/kemoapp/MainViewModel.kt",
        ).readText()
        assertTrue(viewModel.contains("ChatKeepAliveService.start(getApplication())"))
        assertTrue(viewModel.contains("ChatKeepAliveService.stop(getApplication())"))
    }
}

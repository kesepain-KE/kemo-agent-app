package com.kesepain.kemoapp.device

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.theme.KemoTheme
import com.kesepain.kemoapp.ui.theme.KemoTone

class DeviceActionActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = DeviceActionStore.getPending(this, intent.getStringExtra(EXTRA_COMMAND_ID).orEmpty())
        if (command == null) {
            finish()
            return
        }
        setContent {
            KemoTheme(KemoTone.Purple, darkTheme = false, dynamicColor = true, backgroundActive = false) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        DeviceActionExecutor.summary(this@DeviceActionActivity, command),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                }
                AlertDialog(
                    onDismissRequest = { finishCommand(command, DeviceActionResult("cancelled")) },
                    title = { Text(DeviceActionExecutor.title(this@DeviceActionActivity, command.action)) },
                    text = { Text(DeviceActionExecutor.summary(this@DeviceActionActivity, command)) },
                    dismissButton = {
                        TextButton(onClick = {
                            finishCommand(command, DeviceActionResult("cancelled"))
                        }) { Text(getString(R.string.cancel)) }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val result = DeviceActionExecutor.execute(this@DeviceActionActivity, command)
                            finishCommand(command, result)
                        }) { Text(getString(R.string.device_action_continue)) }
                    },
                )
            }
        }
    }

    private fun finishCommand(command: DeviceActionCommand, result: DeviceActionResult) {
        if (DeviceActionStore.isProcessed(this, command.commandId)) {
            finish()
            return
        }
        DeviceActionStore.markProcessed(this, command.commandId)
        DeviceActionStore.removePending(this, command.commandId)
        DeviceActionReporter.report(this, command, result)
        finish()
    }

    companion object { const val EXTRA_COMMAND_ID = "device_command_id" }
}

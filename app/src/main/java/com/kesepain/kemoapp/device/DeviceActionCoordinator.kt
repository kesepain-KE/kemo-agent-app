package com.kesepain.kemoapp.device

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kesepain.kemoapp.KemoApp
import com.kesepain.kemoapp.R

object DeviceActionCoordinator {
    fun accept(context: Context, command: DeviceActionCommand): DeviceActionResult {
        if (DeviceActionStore.isProcessed(context, command.commandId)) {
            return DeviceActionResult("duplicate_ignored")
        }
        if (command.expiresAt > 0 && command.expiresAt <= System.currentTimeMillis() / 1000L) {
            DeviceActionStore.markProcessed(context, command.commandId)
            return DeviceActionResult("expired", mapOf("reason" to "command_expired"))
        }
        if (command.protocolVersion != 1) {
            DeviceActionStore.markProcessed(context, command.commandId)
            return DeviceActionResult("unsupported", mapOf("reason" to "protocol_version"))
        }
        if (!DeviceActionExecutor.isSupported(context, command)) {
            DeviceActionStore.markProcessed(context, command.commandId)
            return DeviceActionResult("unsupported", mapOf("reason" to "system_handler_unavailable"))
        }
        DeviceActionStore.savePending(context, command)
        return if (KemoApp.isForeground) {
            runCatching {
                context.startActivity(confirmationIntent(context, command.commandId))
            }.fold(
                onSuccess = { DeviceActionResult("waiting_user") },
                onFailure = {
                    DeviceActionStore.removePending(context, command.commandId)
                    DeviceActionStore.markProcessed(context, command.commandId)
                    DeviceActionResult("failed", mapOf("reason" to "confirmation_unavailable"))
                },
            )
        } else {
            if (postNotification(context, command)) {
                DeviceActionResult("waiting_user", mapOf("surface" to "notification"))
            } else {
                DeviceActionStore.removePending(context, command.commandId)
                DeviceActionStore.markProcessed(context, command.commandId)
                DeviceActionResult("failed", mapOf("reason" to "notifications_unavailable"))
            }
        }
    }

    private fun postNotification(context: Context, command: DeviceActionCommand): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val intent = confirmationIntent(context, command.commandId)
        val pending = PendingIntent.getActivity(
            context,
            command.commandId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, KemoApp.DEVICE_ACTION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(DeviceActionExecutor.title(context, command.action))
            .setContentText(DeviceActionExecutor.summary(context, command))
            .setStyle(NotificationCompat.BigTextStyle().bigText(DeviceActionExecutor.summary(context, command)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return runCatching {
            manager.notify(command.commandId.hashCode(), notification)
            true
        }.getOrDefault(false)
    }

    private fun confirmationIntent(context: Context, commandId: String): Intent =
        Intent(context, DeviceActionActivity::class.java)
            .putExtra(DeviceActionActivity.EXTRA_COMMAND_ID, commandId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

package com.kesepain.kemoapp.chat

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kesepain.kemoapp.KemoApp
import com.kesepain.kemoapp.MainActivity
import com.kesepain.kemoapp.R

/**
 * Keeps the live App subscription eligible to continue while the UI is
 * backgrounded. The bridge owns the framework run independently, so Android
 * process removal only ends this local subscription; the next App process can
 * replay and resume the persisted bridge snapshot.
 */
class ChatKeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            runningNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    private fun runningNotification() = NotificationCompat.Builder(this, KemoApp.CHAT_RUNTIME_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(getString(R.string.notification_chat_running_title))
        .setContentText(getString(R.string.notification_chat_running_body))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    companion object {
        private const val NOTIFICATION_ID = 43101

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ChatKeepAliveService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatKeepAliveService::class.java))
        }
    }
}

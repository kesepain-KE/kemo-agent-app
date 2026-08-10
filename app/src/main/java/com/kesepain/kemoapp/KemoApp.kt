package com.kesepain.kemoapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.kesepain.kemoapp.notification.NotificationPollWorker
import com.kesepain.kemoapp.widget.WidgetUpdater

class KemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(
                listOf(
                    NotificationChannel(CHAT_CHANNEL, getString(R.string.notification_chat), NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(TASK_CHANNEL, getString(R.string.notification_tasks), NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(SYSTEM_CHANNEL, getString(R.string.notification_system), NotificationManager.IMPORTANCE_DEFAULT),
                )
            )
        }
        WidgetUpdater.schedule(this)
        NotificationPollWorker.schedule(this)
    }

    companion object {
        const val CHAT_CHANNEL = "kemo_chat"
        const val TASK_CHANNEL = "kemo_tasks"
        const val SYSTEM_CHANNEL = "kemo_system"
    }
}

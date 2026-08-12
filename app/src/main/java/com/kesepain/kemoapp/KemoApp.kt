package com.kesepain.kemoapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kesepain.kemoapp.notification.NotificationPollWorker
import com.kesepain.kemoapp.widget.WidgetUpdater

class KemoApp : Application(), DefaultLifecycleObserver {
    override fun onCreate() {
        super<Application>.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(
                listOf(
                    NotificationChannel(CHAT_CHANNEL, getString(R.string.notification_chat), NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(TASK_CHANNEL, getString(R.string.notification_tasks), NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(SYSTEM_CHANNEL, getString(R.string.notification_system), NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(DEVICE_ACTION_CHANNEL, getString(R.string.device_action_channel), NotificationManager.IMPORTANCE_HIGH),
                )
            )
        }
        WidgetUpdater.schedule(this)
        NotificationPollWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) { isForeground = true }
    override fun onStop(owner: LifecycleOwner) { isForeground = false }

    companion object {
        const val CHAT_CHANNEL = "kemo_chat"
        const val TASK_CHANNEL = "kemo_tasks"
        const val SYSTEM_CHANNEL = "kemo_system"
        const val DEVICE_ACTION_CHANNEL = "kemo_device_actions"
        @Volatile var isForeground: Boolean = false
            private set
        lateinit var instance: KemoApp
            private set
    }
}

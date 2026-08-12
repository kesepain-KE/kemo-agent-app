package com.kesepain.kemoapp.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kesepain.kemoapp.KemoApp
import com.kesepain.kemoapp.MainActivity
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.local.Prefs
import com.kesepain.kemoapp.data.repo.KemoRepository
import com.kesepain.kemoapp.ui.components.records
import java.util.concurrent.TimeUnit

class NotificationPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = Prefs(applicationContext).snapshot()
        if (!preferences.notifications || preferences.currentAccountId.isBlank()) return Result.success()
        val store = applicationContext.getSharedPreferences(
            "$STORE:${preferences.currentAccountId}",
            Context.MODE_PRIVATE,
        )
        val initialized = store.getBoolean(INITIALIZED, false)
        return runCatching {
            val repo = KemoRepository(applicationContext)
            val plans = repo.taskPlans().records("plans", "task_plans", "items")
            val crons = repo.cron().records("cron_tasks", "crons", "cron", "scheduled", "items")
            val conversations = repo.conversations().records("sessions", "items")
            val editor = store.edit()

            crons.forEach { cron ->
                val id = cron.text("task_id", "id")
                val latest = cron.text("latest_run_at", "updated_at")
                val state = cron.text("last_state", "status").lowercase()
                val key = "cron:$id"
                val previous = store.getString(key, "").orEmpty()
                if (initialized && latest.isNotBlank() && latest != previous) {
                    notify(
                        key,
                        if (state == "failed") applicationContext.getString(R.string.notification_cron_failed_title)
                        else applicationContext.getString(R.string.notification_cron_complete_title),
                        cron.text("title", "name").ifBlank { id },
                        KemoApp.TASK_CHANNEL,
                        openTasks = true,
                    )
                }
                editor.putString(key, latest)
            }

            plans.forEach { plan ->
                val id = plan.text("plan_id", "id")
                val status = plan.text("status", "state").lowercase()
                val sessionId = plan.text("session_id", "session")
                val key = "plan:$id"
                val previous = store.getString(key, "").orEmpty()
                if (initialized && status != previous && sessionId.isNotBlank() && sessionId == preferences.chatSessionId) {
                    val title = when (status) {
                        "failed" -> applicationContext.getString(R.string.notification_task_failed_title)
                        "pending", "approved" -> applicationContext.getString(R.string.notification_task_approval_title)
                        "completed", "done" -> applicationContext.getString(R.string.notification_task_complete_title)
                        else -> ""
                    }
                    if (title.isNotBlank()) notify(key, title, plan.text("title", "name").ifBlank { id }, KemoApp.TASK_CHANNEL, true)
                }
                editor.putString(key, status)
            }

            conversations.forEach { conversation ->
                val sessionId = conversation.text("session_id", "id")
                if (!sessionId.startsWith("app-")) return@forEach
                val rounds = conversation.number("rounds")?.toLong() ?: 0L
                val key = "conversation:$sessionId"
                val previous = store.getLong(key, 0L)
                if (initialized && rounds > previous) {
                    notify(
                        key,
                        applicationContext.getString(R.string.notification_chat_complete_title),
                        applicationContext.getString(
                            R.string.notification_chat_complete_body,
                            conversation.text("title").ifBlank { sessionId },
                        ),
                        KemoApp.CHAT_CHANNEL,
                        openTasks = false,
                    )
                }
                editor.putLong(key, rounds)
            }
            editor.putBoolean(INITIALIZED, true).apply()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun notify(key: String, title: String, text: String, channel: String, openTasks: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (openTasks) data = Uri.parse("kemo://task/$key")
        }
        val id = key.hashCode()
        val pending = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (text.contains(applicationContext.getString(R.string.notification_open_app))) text
            else "$text · ${applicationContext.getString(R.string.notification_open_app)}"
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    companion object {
        private const val STORE = "kemo_notification_poll"
        private const val INITIALIZED = "initialized"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "kemo_notifications",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

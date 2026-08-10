package com.kesepain.kemoapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kesepain.kemoapp.data.repo.KemoRepository
import java.util.concurrent.TimeUnit

class WidgetUpdater(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { KemoRepository(applicationContext).updateWidgetSummary() }
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, TaskWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        TaskWidgetProvider().onUpdate(applicationContext, manager, ids)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdater>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("kemo_widget", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

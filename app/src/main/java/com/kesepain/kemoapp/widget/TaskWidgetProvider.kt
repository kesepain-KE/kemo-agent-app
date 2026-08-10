package com.kesepain.kemoapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kesepain.kemoapp.MainActivity
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.local.Prefs
import kotlinx.coroutines.runBlocking

class TaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val snapshot = runBlocking { Prefs(context).snapshot() }
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.task_widget)
            views.setTextViewText(R.id.widget_pending, context.getString(R.string.widget_pending, snapshot.widgetPending))
            views.setTextViewText(R.id.widget_latest, if (snapshot.widgetLatest.isBlank()) context.getString(R.string.widget_no_data) else context.getString(R.string.widget_latest, snapshot.widgetLatest))
            val intent = Intent(context, MainActivity::class.java).setData(Uri.parse("kemo://task/widget"))
            views.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }
}

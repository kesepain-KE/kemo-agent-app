package com.kesepain.kemoapp.device

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.OffsetDateTime

object DeviceActionExecutor {
    fun isSupported(context: Context, command: DeviceActionCommand): Boolean =
        runCatching { buildIntent(command)?.resolveActivity(context.packageManager) != null }.getOrDefault(false)

    fun execute(context: Context, command: DeviceActionCommand): DeviceActionResult {
        if (command.expiresAt > 0 && command.expiresAt <= System.currentTimeMillis() / 1000L) {
            return DeviceActionResult("expired", mapOf("reason" to "command_expired"))
        }
        val intent = runCatching { buildIntent(command) }.getOrNull()
            ?: return DeviceActionResult("unsupported", mapOf("reason" to "unsupported_action"))
        if (intent.resolveActivity(context.packageManager) == null) {
            return DeviceActionResult("unsupported", mapOf("reason" to "system_handler_unavailable"))
        }
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            DeviceActionResult("presented", mapOf("action" to command.action))
        }.getOrElse { error ->
            DeviceActionResult("failed", mapOf("reason" to (error.message ?: error::class.java.simpleName)))
        }
    }

    fun title(context: Context, action: String): String = when (action) {
        "alarm.create" -> context.getString(com.kesepain.kemoapp.R.string.device_action_alarm_title)
        "timer.start" -> context.getString(com.kesepain.kemoapp.R.string.device_action_timer_title)
        "calendar.event.create" -> context.getString(com.kesepain.kemoapp.R.string.device_action_calendar_title)
        "todo.create" -> context.getString(com.kesepain.kemoapp.R.string.device_action_todo_title)
        else -> context.getString(com.kesepain.kemoapp.R.string.device_action_request_title)
    }

    fun summary(context: Context, command: DeviceActionCommand): String = when (command.action) {
        "alarm.create" -> context.getString(
            com.kesepain.kemoapp.R.string.device_action_alarm_summary,
            command.int("hour"), command.int("minute"), command.text("label"),
        )
        "timer.start" -> context.getString(
            com.kesepain.kemoapp.R.string.device_action_timer_summary,
            command.int("duration_seconds"), command.text("label"),
        )
        "calendar.event.create", "todo.create" -> command.text("title")
        else -> command.action
    }.trim().trimEnd('·').trim()

    private fun buildIntent(command: DeviceActionCommand): Intent? = when (command.action) {
        "alarm.create" -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, command.int("hour"))
            putExtra(AlarmClock.EXTRA_MINUTES, command.int("minute"))
            command.text("label").takeIf(String::isNotBlank)?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_VIBRATE, command.bool("vibrate", true))
            val days = command.arguments["repeat_days"]?.toString()
                ?.trim('[', ']')?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                ?.map { if (it == 7) java.util.Calendar.SUNDAY else it + 1 }
                ?.let(::ArrayList)
            if (!days.isNullOrEmpty()) putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, days)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        "timer.start" -> Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, command.int("duration_seconds"))
            command.text("label").takeIf(String::isNotBlank)?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        "calendar.event.create" -> Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            // Some vendor calendars (including current ColorOS/OxygenOS builds) require
            // the standard event MIME type in addition to CalendarContract's content URI.
            type = "vnd.android.cursor.item/event"
            putExtra(CalendarContract.Events.TITLE, command.text("title"))
            putExtra(CalendarContract.Events.DESCRIPTION, command.text("description"))
            putExtra(CalendarContract.Events.EVENT_LOCATION, command.text("location"))
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, command.millis("start_at"))
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, command.millis("end_at"))
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, command.bool("all_day"))
        }
        "todo.create" -> Intent(Intent.ACTION_INSERT).apply {
            type = "vnd.android.cursor.item/task"
            putExtra("title", command.text("title"))
            putExtra(Intent.EXTRA_TITLE, command.text("title"))
            putExtra(Intent.EXTRA_TEXT, command.text("notes"))
            command.text("due_at").takeIf(String::isNotBlank)?.let {
                val millis = OffsetDateTime.parse(it).toInstant().toEpochMilli()
                putExtra("dueDate", millis)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
            }
            command.text("reminder_at").takeIf(String::isNotBlank)?.let {
                putExtra("reminderTime", OffsetDateTime.parse(it).toInstant().toEpochMilli())
            }
        }
        else -> null
    }

    private fun DeviceActionCommand.text(key: String): String =
        (arguments[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun DeviceActionCommand.int(key: String): Int =
        (arguments[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun DeviceActionCommand.bool(key: String, fallback: Boolean = false): Boolean =
        (arguments[key] as? JsonPrimitive)?.booleanOrNull ?: fallback

    private fun DeviceActionCommand.millis(key: String): Long =
        OffsetDateTime.parse(text(key)).toInstant().toEpochMilli()
}

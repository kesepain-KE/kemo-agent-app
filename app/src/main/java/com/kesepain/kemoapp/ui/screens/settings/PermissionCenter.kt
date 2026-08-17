package com.kesepain.kemoapp.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kesepain.kemoapp.R

private enum class PermissionLevel { Granted, Required, Review }

private data class PermissionEntry(
    val icon: ImageVector,
    val title: Int,
    val summary: Int,
    val level: PermissionLevel,
    val actionLabel: Int? = null,
    val action: (() -> Unit)? = null,
    val secondaryLabel: Int? = null,
    val secondaryAction: (() -> Unit)? = null,
)

@Composable
internal fun PermissionCenterCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { revision += 1 }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { revision += 1 }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationGranted = remember(revision) { notificationsGranted(context) }
    val batteryGranted = remember(revision) { batteryOptimizationIgnored(context) }
    val installGranted = remember(revision) {
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    }
    val deviceActionsAvailable = remember(revision) { deviceActionsAvailable(context) }
    val isOplus = remember {
        val maker = Build.MANUFACTURER.lowercase()
        maker.contains("oneplus") || maker.contains("oppo") || maker.contains("realme")
    }

    fun launch(intent: Intent) {
        runCatching { settingsLauncher.launch(intent) }
            .onFailure { openAppDetails(context) { fallback -> settingsLauncher.launch(fallback) } }
    }

    val entries = listOf(
        PermissionEntry(
            icon = Icons.Outlined.Notifications,
            title = R.string.permission_notifications_title,
            summary = R.string.permission_notifications_summary,
            level = if (notificationGranted) PermissionLevel.Granted else PermissionLevel.Required,
            actionLabel = if (notificationGranted) null else R.string.permission_request,
            action = if (notificationGranted) null else {
                {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        launch(notificationSettingsIntent(context))
                    }
                }
            },
            secondaryLabel = R.string.permission_open_settings,
            secondaryAction = { launch(notificationSettingsIntent(context)) },
        ),
        PermissionEntry(
            icon = Icons.Outlined.BatteryChargingFull,
            title = R.string.permission_background_title,
            summary = R.string.permission_background_summary,
            level = if (batteryGranted) PermissionLevel.Granted else PermissionLevel.Required,
            actionLabel = if (batteryGranted) null else R.string.permission_request,
            action = if (batteryGranted) null else {
                { launch(requestBatteryExemptionIntent(context)) }
            },
            secondaryLabel = R.string.permission_open_settings,
            secondaryAction = { launch(batterySettingsIntent(context)) },
        ),
        PermissionEntry(
            icon = Icons.Outlined.Android,
            title = R.string.permission_autostart_title,
            summary = if (isOplus) R.string.permission_autostart_summary_coloros else R.string.permission_autostart_summary_android,
            level = PermissionLevel.Review,
            actionLabel = R.string.permission_manage,
            action = { openAppDetails(context) { intent -> settingsLauncher.launch(intent) } },
        ),
        PermissionEntry(
            icon = Icons.Outlined.InstallMobile,
            title = R.string.permission_install_title,
            summary = R.string.permission_install_summary,
            level = if (installGranted) PermissionLevel.Granted else PermissionLevel.Review,
            actionLabel = if (installGranted) null else R.string.permission_manage,
            action = if (installGranted) null else {
                { launch(unknownSourcesIntent(context)) }
            },
        ),
        PermissionEntry(
            icon = Icons.Outlined.Alarm,
            title = R.string.permission_device_title,
            summary = R.string.permission_device_summary,
            level = if (deviceActionsAvailable) PermissionLevel.Granted else PermissionLevel.Required,
        ),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(stringResource(R.string.permission_center), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.permission_center_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                PermissionEntryRow(entry)
            }
        }
    }
}

@Composable
private fun PermissionEntryRow(entry: PermissionEntry) {
    val statusText = when (entry.level) {
        PermissionLevel.Granted -> R.string.permission_status_granted
        PermissionLevel.Required -> R.string.permission_status_action_required
        PermissionLevel.Review -> R.string.permission_status_review
    }
    val statusColor = when (entry.level) {
        PermissionLevel.Granted -> MaterialTheme.colorScheme.primary
        PermissionLevel.Required -> MaterialTheme.colorScheme.error
        PermissionLevel.Review -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(entry.title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(entry.summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(statusText),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
            if (entry.action != null || entry.secondaryAction != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.action != null && entry.actionLabel != null) {
                        OutlinedButton(onClick = entry.action) { Text(stringResource(entry.actionLabel)) }
                    }
                    if (entry.secondaryAction != null && entry.secondaryLabel != null) {
                        OutlinedButton(onClick = entry.secondaryAction) { Text(stringResource(entry.secondaryLabel)) }
                    }
                }
            }
        }
    }
}

private fun notificationsGranted(context: Context): Boolean {
    val runtimeGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun batteryOptimizationIgnored(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

private fun deviceActionsAvailable(context: Context): Boolean {
    val packageManager = context.packageManager
    val alarm = Intent(AlarmClock.ACTION_SET_ALARM).resolveActivity(packageManager) != null
    val timer = Intent(AlarmClock.ACTION_SET_TIMER).resolveActivity(packageManager) != null
    val calendar = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        type = "vnd.android.cursor.item/event"
    }.resolveActivity(packageManager) != null
    val todo = Intent(Intent.ACTION_INSERT).apply { type = "vnd.android.cursor.item/task" }
        .resolveActivity(packageManager) != null
    return alarm && timer && calendar && todo
}

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun requestBatteryExemptionIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }

private fun batterySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }

private fun unknownSourcesIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:${context.packageName}")
    }

private fun openAppDetails(context: Context, launch: (Intent) -> Unit) {
    launch(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        },
    )
}

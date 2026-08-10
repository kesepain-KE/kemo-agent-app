package com.kesepain.kemoapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CronTaskCard(title: String, type: String, schedule: String, nextRun: String, enabled: Boolean, busy: Boolean = false, onEnabledChange: ((Boolean) -> Unit)? = null) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onEnabledChange != null) BusySwitch(enabled, onEnabledChange, busy = busy) else StatusChip(if (enabled) "enabled" else "disabled")
            }
            if (type.isNotBlank()) StatusChip(type)
            if (schedule.isNotBlank()) Text(schedule, style = MaterialTheme.typography.bodyMedium)
            if (nextRun.isNotBlank()) Text(nextRun, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

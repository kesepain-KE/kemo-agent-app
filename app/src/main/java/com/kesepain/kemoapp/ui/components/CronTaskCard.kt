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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R

@Composable
fun CronTaskCard(
    id: String,
    title: String,
    type: String,
    schedule: String,
    nextRun: String,
    enabled: Boolean,
    status: String = "",
    details: String = "",
) {
    var showDetails by rememberSaveable(id) { mutableStateOf(false) }
    val state = status.ifBlank { if (enabled) "enabled" else "disabled" }
    Card(
        onClick = { showDetails = true },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(state)
            }
            if (type.isNotBlank()) StatusChip(type)
            if (schedule.isNotBlank()) Text(schedule, style = MaterialTheme.typography.bodyMedium)
            if (nextRun.isNotBlank()) Text(nextRun, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showDetails) {
        DetailBottomSheet(title, onDismissRequest = { showDetails = false }) {
            DetailRow(stringResource(R.string.cron_status), state)
            if (type.isNotBlank()) DetailRow(stringResource(R.string.cron_type), type)
            if (schedule.isNotBlank()) DetailRow(stringResource(R.string.cron_schedule), schedule)
            if (nextRun.isNotBlank()) DetailRow(stringResource(R.string.cron_next_run), nextRun)
            if (details.isNotBlank()) {
                Text(stringResource(R.string.details), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(details, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
    }
}

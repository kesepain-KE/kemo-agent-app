package com.kesepain.kemoapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import com.kesepain.kemoapp.R

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val normalized = status.lowercase()
    val colors = when {
        normalized in setOf("ok", "success", "done", "completed", "healthy", "online") -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        normalized in setOf("running", "active", "processing") -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        normalized in setOf("failed", "error", "offline") -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        normalized in setOf("pending", "waiting", "approved", "awaiting_approval") -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val display = when {
        normalized in setOf("ok", "success", "done", "completed", "healthy") -> stringResource(R.string.status_completed)
        normalized in setOf("online", "connected", "ready") -> stringResource(R.string.connected)
        normalized in setOf("running", "active", "processing") -> stringResource(R.string.filter_running)
        normalized in setOf("failed", "error", "offline", "aborted") -> stringResource(R.string.filter_failed)
        normalized in setOf("pending", "waiting", "approved", "awaiting_approval") -> stringResource(R.string.filter_pending)
        normalized == "enabled" -> stringResource(R.string.enabled)
        normalized == "disabled" -> stringResource(R.string.disabled)
        else -> status.ifBlank { "—" }
    }
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = colors.first) {
        Text(display, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = colors.second)
    }
}

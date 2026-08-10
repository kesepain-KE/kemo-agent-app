package com.kesepain.kemoapp.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.BusySwitch

@Composable
fun NotificationsScreen(enabled: Boolean, onEnabledChanged: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.notifications), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.push_notifications), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.push_notifications_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BusySwitch(checked = enabled, onCheckedChange = onEnabledChanged)
            }
        }
    }
}

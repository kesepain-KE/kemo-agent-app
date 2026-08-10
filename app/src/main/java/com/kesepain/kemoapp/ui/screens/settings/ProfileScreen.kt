package com.kesepain.kemoapp.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.local.AccountConfig
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.StatusChip

@Composable
fun ProfileScreen(
    accounts: List<AccountConfig>,
    currentId: String,
    connected: Boolean,
    notifications: Boolean,
    tone: String,
    themeMode: String,
    avatarBytes: ByteArray?,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onConfiguration: () -> Unit,
    onAppSettings: () -> Unit,
    onNotifications: () -> Unit,
    onSecurity: () -> Unit,
    onStatus: () -> Unit,
    onVersion: () -> Unit,
    onLogout: () -> Unit,
) {
    val current = accounts.firstOrNull { it.id == currentId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(avatarBytes, current?.username.orEmpty())
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(current?.username ?: stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.kemo_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(if (connected) stringResource(R.string.connected) else stringResource(R.string.disconnected))
            }
        }

        item { SectionHeader(stringResource(R.string.account)) }
        items(accounts, key = { it.id }) { account ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (account.id == currentId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.username, style = MaterialTheme.typography.titleMedium)
                        Text(account.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (account.id != currentId) OutlinedButton(onClick = { onSwitch(account.id) }) { Text(stringResource(R.string.switch_account)) }
                }
            }
        }
        item { OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_account)) } }

        item { SectionHeader(stringResource(R.string.configuration)) }
        item { NavigationCard(stringResource(R.string.agent_configuration), stringResource(R.string.agent_configuration_summary), onConfiguration) }
        item { NavigationCard(stringResource(R.string.app_configuration), stringResource(R.string.app_configuration_summary), onAppSettings) }
        item { SectionHeader(stringResource(R.string.security)) }
        item { NavigationCard(stringResource(R.string.security), stringResource(R.string.security_summary), onSecurity) }
        item { SectionHeader(stringResource(R.string.notifications)) }
        item { NavigationCard(stringResource(R.string.push_notifications), stringResource(if (notifications) R.string.enabled else R.string.disabled), onNotifications) }
        item { SectionHeader(stringResource(R.string.appearance)) }
        item { NavigationCard(stringResource(R.string.theme), "$tone · $themeMode", onSettings) }
        item { SectionHeader(stringResource(R.string.status_title)) }
        item { NavigationCard(stringResource(R.string.status_title), stringResource(R.string.status_summary), onStatus) }
        item { SectionHeader(stringResource(R.string.about)) }
        item { NavigationCard(stringResource(R.string.app_name), stringResource(R.string.version_details), onVersion) }
        item { OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout)) } }
    }
}

@Composable
private fun UserAvatar(bytes: ByteArray?, username: String) {
    val bitmap = remember(bytes?.contentHashCode()) {
        bytes?.let { value -> runCatching { BitmapFactory.decodeByteArray(value, 0, value.size)?.asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, stringResource(R.string.user_avatar), Modifier.fillMaxSize())
        else Text(username.take(1).uppercase().ifBlank { "K" }, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun NavigationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

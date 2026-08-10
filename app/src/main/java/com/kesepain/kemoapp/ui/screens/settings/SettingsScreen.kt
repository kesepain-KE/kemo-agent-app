package com.kesepain.kemoapp.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.local.AppPreferences
import com.kesepain.kemoapp.ui.components.BusySwitch
import com.kesepain.kemoapp.ui.theme.KemoTone

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onTheme: (String) -> Unit,
    onTone: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    providerType: String,
    onModels: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall) }
        if (providerType.equals("kemo", ignoreCase = true)) {
            item { SettingCard(stringResource(R.string.models), onModels) }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { item ->
                            FilterChip(selected = preferences.themeMode == item.first, onClick = { onTheme(item.first) }, label = { Text(stringResource(item.second)) })
                        }
                    }
                    Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        KemoTone.entries.forEach { tone ->
                            Box(
                                Modifier.size(38.dp).background(tone.seed, CircleShape).clickable { onTone(tone.name) },
                                contentAlignment = Alignment.Center,
                            ) { if (preferences.tone == tone.name) Text("✓", color = androidx.compose.ui.graphics.Color.White) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dynamic_color), modifier = Modifier.weight(1f))
                        BusySwitch(preferences.dynamicColor, onDynamicColor)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                    AssistChip(
                        onClick = { onLanguage(if (preferences.language == "zh") "en" else "zh") },
                        label = { Text(stringResource(if (preferences.language == "zh") R.string.language_switch_zh else R.string.language_switch_en)) },
                    )
                }
            }
        }
        item { SettingCard(stringResource(R.string.about)) { uriHandler.openUri("https://github.com/kesepain-KE/kemo-agent-app") } }
        item { Text(stringResource(R.string.version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingCard(title: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

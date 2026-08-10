package com.kesepain.kemoapp.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    onBackgroundChanged: (String, String) -> Unit,
    onResetTheme: () -> Unit,
) {
    val context = LocalContext.current
    var languageExpanded by remember { mutableStateOf(false) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
                onBackgroundChanged(uri.toString(), mimeType)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.theme_settings), style = MaterialTheme.typography.headlineSmall) }
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
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.restore_theme), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.restore_theme_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onResetTheme) { Text(stringResource(R.string.restore)) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.background_theme), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.background_theme_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (preferences.themeBackgroundUri.isNotBlank()) {
                        Text(
                            stringResource(
                                if (preferences.themeBackgroundMime.startsWith("video/")) R.string.background_video_active else R.string.background_image_active,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedButton(
                        onClick = { backgroundPicker.launch(arrayOf("image/*", "video/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (preferences.themeBackgroundUri.isBlank()) R.string.upload_background else R.string.replace_background))
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.language_current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        OutlinedButton(onClick = { languageExpanded = true }) {
                            Text(stringResource(if (preferences.language == "en") R.string.language_en else R.string.language_zh))
                        }
                        DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.language_zh)) },
                                onClick = { languageExpanded = false; onLanguage("zh") },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.language_en)) },
                                onClick = { languageExpanded = false; onLanguage("en") },
                            )
                        }
                    }
                }
            }
        }
    }
}

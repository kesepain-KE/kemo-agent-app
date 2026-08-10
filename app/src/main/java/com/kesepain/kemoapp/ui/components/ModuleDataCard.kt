package com.kesepain.kemoapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ModuleDataCard(
    key: String,
    name: String,
    updatedAt: String,
    status: String,
    data: JsonElement?,
    enabled: Boolean? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val body = data.pretty()
    val hasData = body.isNotBlank()
    val healthy = status.lowercase() in setOf("ready", "active", "ok", "online", "healthy", "true", "正常")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = hasData) { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).background(
                        if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                )
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                if (enabled != null && onEnabledChange != null) Switch(checked = enabled, onCheckedChange = onEnabledChange)
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (hasData) Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (updatedAt.isNotBlank()) Text(formatUpdatedAt(updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedVisibility(
                visible = hasData && expanded,
                enter = expandVertically(animationSpec = spring()),
                exit = shrinkVertically(animationSpec = spring()),
            ) {
                SelectionContainer {
                    SafeMarkdown(
                        content = body,
                        streaming = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp)).padding(14.dp),
                    )
                }
            }
        }
    }
}

private fun formatUpdatedAt(value: String): String {
    val seconds = value.toDoubleOrNull() ?: return value
    return runCatching {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli((seconds * 1000).toLong()))
    }.getOrDefault(value)
}

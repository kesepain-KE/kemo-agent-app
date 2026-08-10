package com.kesepain.kemoapp.ui.screens.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.JsonCard
import com.kesepain.kemoapp.ui.components.LoadingOutlinedButton
import com.kesepain.kemoapp.ui.components.MetricCard
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.metricValues
import kotlinx.serialization.json.JsonElement

@Composable
fun StatusScreen(value: JsonElement?, refreshing: Boolean, onRefresh: () -> Unit) {
    val metrics = value.metricValues(32)
    val visibleMetrics = statusMetricsForDisplay(metrics)
    val online = metrics.any { (key, metric) -> key.contains("health", true) && metric.lowercase() in setOf("ok", "online", "healthy", "true") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader(stringResource(R.string.status_title)) { LoadingOutlinedButton(onClick = onRefresh, loading = refreshing) { Text(stringResource(R.string.refresh)) } } }
        item {
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f))) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.service_status), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(if (online) R.string.service_online else R.string.service_offline), color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.latest_snapshot), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items((visibleMetrics.size + 1) / 2) { rowIndex ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val first = visibleMetrics.getOrNull(rowIndex * 2)
                val second = visibleMetrics.getOrNull(rowIndex * 2 + 1)
                if (first != null) LocalizedMetric(first.first, first.second, Modifier.weight(1f))
                if (second != null) LocalizedMetric(second.first, second.second, Modifier.weight(1f))
                else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        if (metrics.isEmpty()) item { JsonCard(stringResource(R.string.unrecognized_data), value) }
    }
}

internal fun statusMetricsForDisplay(metrics: List<Pair<String, String>>): List<Pair<String, String>> {
    val healthIndex = metrics.indexOfFirst { metricCategory(it.first) == "health" }
    val summaryRowStart = if (healthIndex >= 0) healthIndex - (healthIndex % 2) else -1
    return metrics.asSequence()
        .filterIndexed { index, _ -> summaryRowStart < 0 || index !in summaryRowStart..(summaryRowStart + 1) }
        .distinctBy { metricCategory(it.first) }
        .toList()
}

internal fun metricCategory(path: String): String {
    val normalized = path.lowercase()
    return when {
        normalized.contains("health") || normalized.contains("status") -> "health"
        normalized.contains("context") -> "context"
        normalized.contains("cache") -> "cache"
        normalized.contains("image") || normalized.contains("draw") || normalized.contains("paint") -> "image"
        normalized.contains("session") || normalized.contains("conversation") -> "session"
        normalized.contains("queue") || normalized.contains("pending") -> "queue"
        normalized.contains("message") -> "message"
        normalized.contains("provider") || normalized.contains("model") || normalized.contains("upstream") -> "provider"
        normalized.contains("version") -> "version"
        normalized.contains("congestion") -> "congestion"
        else -> normalized.substringAfterLast(" · ").replace(Regex("[^a-z0-9_]+"), "_")
    }
}

@Composable
private fun LocalizedMetric(path: String, rawValue: String, modifier: Modifier = Modifier) {
    val label = when {
        path.contains("version", true) -> stringResource(R.string.metric_version)
        path.contains("upstream", true) -> stringResource(R.string.metric_upstream)
        path.contains("health", true) || path.contains("status", true) -> stringResource(R.string.metric_health)
        path.contains("session", true) -> stringResource(R.string.metric_sessions)
        path.contains("queue", true) || path.contains("pending", true) -> stringResource(R.string.metric_queue)
        path.contains("cache", true) -> stringResource(R.string.metric_cache)
        path.contains("context", true) -> stringResource(R.string.metric_context)
        path.contains("message", true) -> stringResource(R.string.metric_messages)
        path.contains("congestion", true) -> stringResource(R.string.metric_congestion)
        path.contains("provider", true) || path.contains("model", true) -> stringResource(R.string.metric_provider)
        else -> stringResource(R.string.metric_runtime)
    }
    val value = when (rawValue.lowercase()) {
        "ok", "healthy" -> stringResource(R.string.status_healthy)
        "online", "connected", "true" -> stringResource(R.string.connected)
        "offline", "disconnected", "false" -> stringResource(R.string.disconnected)
        else -> rawValue
    }
    var expanded by rememberSaveable(path) { mutableStateOf(false) }
    Column(modifier.clickable { expanded = !expanded }) {
        MetricCard(label, value)
        AnimatedVisibility(expanded) {
            Text("$label：$value", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

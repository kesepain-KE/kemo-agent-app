package com.kesepain.kemoapp.ui.screens.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.JsonCard
import com.kesepain.kemoapp.ui.components.LoadingOutlinedButton
import com.kesepain.kemoapp.ui.components.SectionHeader
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StatusScreen(value: JsonElement?, refreshing: Boolean, onRefresh: () -> Unit) {
    val root = value as? JsonObject
    val health = root.obj("health")
    val overview = root.obj("overview")
    val runtime = root.obj("runtime")
    val runtimeApi = runtime.obj("api")
    val runtimeContext = runtime.obj("context")
    val runtimeTokens = runtime.obj("tokens")
    val overviewProvider = overview.obj("provider")
    val overviewContext = overview.obj("context")
    val overviewWindowTokens = overview.obj("context_window").obj("tokens")
    val counts = overview.obj("counts")

    val service = health.text("service").ifBlank { stringResource(R.string.kemo_service) }
    val online = health.text("status").lowercase() in setOf("ok", "online", "healthy", "connected", "true")
    val user = overview.text("user", "username")
    val protocol = runtimeApi.text("type", "protocol").ifBlank { overviewProvider.text("type", "protocol") }
    val model = runtimeApi.text("model").ifBlank { overviewProvider.text("model") }
    val effort = runtimeApi.text("thinking_effort", "reasoning_effort").ifBlank { overviewProvider.text("reasoning_effort", "thinking_effort") }
    val contextUsed = runtimeContext.number("used_tokens").takeIf { it > 0 } ?: overviewWindowTokens.number("context_tokens")
    val contextMax = runtimeContext.number("max_tokens").takeIf { it > 0 } ?: overviewWindowTokens.number("capacity_tokens").takeIf { it > 0 } ?: overviewContext.number("limit")
    val contextPercent = runtimeContext.decimal("percent") ?: overviewWindowTokens.decimal("percent") ?: if (contextMax > 0) contextUsed.toDouble() / contextMax * 100.0 else 0.0
    val rounds = runtimeContext.number("rounds").takeIf { it > 0 } ?: overviewContext.number("rounds")
    val roundLimit = runtimeContext.number("round_limit").takeIf { it > 0 } ?: overviewContext.number("round_limit")
    val knowledgeCount = counts.number("knowledge_documents", "knowledge", "documents")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(stringResource(R.string.status_title)) { LoadingOutlinedButton(onClick = onRefresh, loading = refreshing) { Text(stringResource(R.string.refresh)) } } }
        item {
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f))) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (online) stringResource(R.string.service_connected_format, service) else stringResource(R.string.service_disconnected_format, service),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(stringResource(R.string.latest_snapshot), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            StatusPanel(stringResource(R.string.status_context_panel)) {
                StatusRow(stringResource(R.string.status_context_capacity), formatCount(contextMax))
                StatusRow(stringResource(R.string.status_context_used), stringResource(R.string.status_used_percent, formatCount(contextUsed), contextPercent))
                LinearProgressIndicator(
                    progress = { (contextPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                StatusRow(stringResource(R.string.status_rounds), stringResource(R.string.status_rounds_value, rounds, roundLimit))
            }
        }
        item {
            StatusPanel(stringResource(R.string.status_model_panel)) {
                StatusRow(stringResource(R.string.status_model_protocol), protocol.ifBlank { "—" })
                StatusRow(stringResource(R.string.status_current_model), model.ifBlank { "—" })
                StatusRow(stringResource(R.string.reasoning_effort), effort.ifBlank { "—" })
            }
        }
        item {
            StatusPanel(stringResource(R.string.status_token_panel)) {
                StatusRow(stringResource(R.string.status_token_total), formatCount(runtimeTokens.number("total_tokens")))
                StatusRow(stringResource(R.string.status_token_sent), formatCount(runtimeTokens.number("sent_tokens")))
                StatusRow(stringResource(R.string.status_token_received), formatCount(runtimeTokens.number("received_tokens")))
                StatusRow(stringResource(R.string.status_token_cached), formatCount(runtimeTokens.number("cached_tokens")))
                StatusRow(stringResource(R.string.status_cache_rate), stringResource(R.string.status_percent_value, runtimeTokens.decimal("cache_rate") ?: 0.0))
                StatusRow(stringResource(R.string.status_request_count), formatCount(runtimeTokens.number("request_count")))
            }
        }
        item {
            StatusPanel(stringResource(R.string.status_other_panel)) {
                StatusRow(stringResource(R.string.status_current_user), user.ifBlank { "—" })
                StatusRow(stringResource(R.string.status_knowledge_count), formatCount(knowledgeCount))
            }
        }
        if (root == null) item { JsonCard(stringResource(R.string.unrecognized_data), value) }
    }
}

@Composable
private fun StatusPanel(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

private fun JsonObject?.text(vararg keys: String): String {
    keys.forEach { key -> (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)?.let { return it } }
    return ""
}

private fun JsonObject?.number(vararg keys: String): Long {
    keys.forEach { key ->
        val primitive = this?.get(key) as? JsonPrimitive ?: return@forEach
        primitive.contentOrNull?.toLongOrNull()?.let { return it }
        primitive.doubleOrNull?.toLong()?.let { return it }
    }
    return 0L
}

private fun JsonObject?.decimal(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull

private fun formatCount(value: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

// Retained for compatibility with existing display-policy unit tests. The structured
// status screen no longer relies on flattened metric paths for rendering.
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

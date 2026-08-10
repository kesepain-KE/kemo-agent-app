package com.kesepain.kemoapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R

data class PlanStepUi(val title: String, val done: Boolean)

@Composable
fun PlanCard(id: String, title: String, status: String, progress: Float?, steps: List<PlanStepUi>, pendingKeys: Set<String>, onAction: (String, String) -> Unit) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val done = steps.count(PlanStepUi::done)
    val normalizedProgress = progress ?: if (steps.isEmpty()) 0f else done.toFloat() / steps.size
    val actions = when (status.lowercase()) {
        "pending", "awaiting_approval", "approval", "waiting" -> listOf("approve", "abort")
        "running", "in_progress", "active" -> listOf("pause", "abort")
        "paused", "suspended" -> listOf("resume", "abort")
        else -> emptyList()
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title.ifBlank { id }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(status)
            }
            LinearProgressIndicator(progress = { normalizedProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { action ->
                        LoadingOutlinedButton(
                            onClick = { onAction(id, action) },
                            loading = "task:$id:$action" in pendingKeys,
                        ) {
                            Text(stringResource(when (action) {
                                "approve" -> R.string.approve
                                "pause" -> R.string.pause
                                "resume" -> R.string.resume
                                else -> R.string.abort
                            }))
                        }
                    }
                }
            }
            AnimatedVisibility(expanded && steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.steps_progress, done, steps.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    val currentIndex = steps.indexOfFirst { !it.done }
                    steps.forEachIndexed { index, step ->
                        val current = index == currentIndex
                        Row(
                            Modifier.fillMaxWidth().background(
                                if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f) else androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.shapes.medium,
                            ).padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(22.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                if (step.done) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                else Spacer(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                step.title,
                                style = if (current) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                                color = if (step.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

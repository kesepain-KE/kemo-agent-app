package com.kesepain.kemoapp.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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

data class PlanStepUi(
    val title: String,
    val done: Boolean,
    val description: String = "",
    val reference: String = "",
)

@Composable
fun PlanCard(
    id: String,
    title: String,
    status: String,
    progress: Float?,
    steps: List<PlanStepUi>,
    description: String = "",
    reference: String = "",
) {
    var showDetails by rememberSaveable(id) { mutableStateOf(false) }
    val done = steps.count(PlanStepUi::done)
    val normalizedProgress = progress ?: if (steps.isEmpty()) 0f else done.toFloat() / steps.size
    val displayTitle = title.ifBlank { id }

    Card(
        onClick = { showDetails = true },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(displayTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(status)
            }
            LinearProgressIndicator(
                progress = { normalizedProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }

    if (showDetails) {
        DetailBottomSheet(displayTitle, onDismissRequest = { showDetails = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusChip(status)
            }
            Text(stringResource(R.string.steps_progress, done, steps.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { normalizedProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
            )
            if (description.isNotBlank()) {
                DetailSection(stringResource(R.string.plan_description), description)
            }
            if (steps.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.plan_steps), style = MaterialTheme.typography.titleMedium)
                val currentIndex = steps.indexOfFirst { !it.done }
                steps.forEachIndexed { index, step ->
                    val current = index == currentIndex
                    Row(
                        Modifier.fillMaxWidth().background(
                            if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
                            else MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.shapes.medium,
                        ).padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.size(22.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                            if (step.done) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            else Spacer(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(step.title, style = if (current) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium)
                            if (step.description.isNotBlank()) Text(step.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (step.reference.isNotBlank()) Text(step.reference, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            if (reference.isNotBlank()) {
                HorizontalDivider()
                DetailSection(stringResource(R.string.plan_reference), reference)
            }
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

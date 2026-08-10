package com.kesepain.kemoapp.ui.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.CronTaskCard
import com.kesepain.kemoapp.ui.components.IllustratedEmptyState
import com.kesepain.kemoapp.ui.components.LoadingOutlinedButton
import com.kesepain.kemoapp.ui.components.PlanCard
import com.kesepain.kemoapp.ui.components.PlanStepUi
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.pretty
import com.kesepain.kemoapp.ui.components.records
import kotlinx.serialization.json.JsonElement

@Composable
fun TasksScreen(tasks: JsonElement?, cron: JsonElement?, pendingKeys: Set<String>, onRefresh: () -> Unit) {
    val plans = tasks.records("plans", "task_plans", "items").filter { it.text("plan_id", "id").isNotBlank() }
    val cronItems = cron.records("cron_tasks", "crons", "cron", "scheduled", "items").filter { it.text("task_id", "id").isNotBlank() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(stringResource(R.string.tasks_title)) { LoadingOutlinedButton(onClick = onRefresh, loading = "refresh:tasks" in pendingKeys) { Text(stringResource(R.string.refresh)) } } }
        if (plans.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_tasks)) }
        items(plans, key = { it.text("plan_id", "id") }) { plan ->
            val steps = plan.children("steps", "items").map { step ->
                PlanStepUi(
                    step.text("title", "name", "description"),
                    step.boolean("done", "completed") == true || step.text("status") in setOf("done", "completed"),
                    description = step.text("description", "detail", "summary"),
                    reference = step.element("reference", "references", "source")?.pretty().orEmpty(),
                )
            }
            val rawProgress = plan.number("progress", "percent", "completion")
            val progress = rawProgress?.let { if (it > 1.0) (it / 100.0).toFloat() else it.toFloat() }
            PlanCard(
                id = plan.text("plan_id", "id"),
                title = plan.text("title", "name"),
                status = plan.text("status", "state"),
                progress = progress,
                steps = steps,
                description = plan.text("description", "summary", "objective"),
                reference = plan.element("reference", "references", "source", "context")?.pretty().orEmpty(),
            )
        }

        item { SectionHeader(stringResource(R.string.cron_title)) }
        if (cronItems.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_scheduled_tasks)) }
        items(cronItems, key = { it.text("task_id", "id") }) { item ->
            CronTaskCard(
                id = item.text("task_id", "id"),
                title = item.text("title", "name").ifBlank { item.text("task_id", "id") },
                type = item.text("type", "schedule_type", "kind"),
                schedule = item.text("schedule", "cron", "interval", "time"),
                nextRun = item.text("next_run", "next_run_at", "next"),
                enabled = item.boolean("enabled", "active") ?: true,
                status = item.text("last_state", "status", "state"),
                details = item.raw().pretty(),
            )
        }
    }
}

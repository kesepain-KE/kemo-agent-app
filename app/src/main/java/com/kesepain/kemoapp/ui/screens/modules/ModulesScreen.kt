package com.kesepain.kemoapp.ui.screens.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.IllustratedEmptyState
import com.kesepain.kemoapp.ui.components.ModuleDataCard
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.records
import kotlinx.serialization.json.JsonElement

@Composable
fun ModulesScreen(expands: JsonElement?, senses: JsonElement?, onRefresh: () -> Unit, onToggle: (String, String, String, Boolean) -> Unit) {
    val expandItems = expands.records("expands")
    val senseItems = senses.records("sources")
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(stringResource(R.string.modules_title)) { OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) } } }
        item { SectionHeader(stringResource(R.string.expands), expandItems.size) }
        if (expandItems.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_modules)) }
        items(expandItems, key = { "expand-${it.text("scope")}-${it.text("name", "id")}" }) { module ->
            val name = module.text("display_name", "name", "id")
            ModuleDataCard(
                key = "${module.text("scope")}-$name",
                name = name,
                status = module.text("status", "state"),
                updatedAt = module.text("updated", "updated_at", "recent_update", "last_update"),
                data = module.element("data"),
                enabled = module.boolean("enabled", "open_input", "active_for_main_agent", "active") ?: false,
                onEnabledChange = { enabled -> onToggle("expand", module.text("scope").ifBlank { "global" }, module.text("name", "id"), enabled) },
            )
        }
        item { SectionHeader(stringResource(R.string.senses), senseItems.size) }
        if (senseItems.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_senses)) }
        items(senseItems, key = { "sense-${it.text("id", "name")}" }) { sense ->
            ModuleDataCard(
                key = "sense-${sense.text("id", "name")}",
                name = sense.text("display_name", "name", "id"),
                status = sense.text("status", "health"),
                updatedAt = sense.text("updated_at", "recent_update"),
                data = sense.element("injected_markdown"),
                enabled = sense.boolean("active_for_main_agent", "enabled", "whitelisted") ?: false,
                onEnabledChange = { enabled -> onToggle("sense", "global", sense.text("name", "id"), enabled) },
            )
        }
    }
}

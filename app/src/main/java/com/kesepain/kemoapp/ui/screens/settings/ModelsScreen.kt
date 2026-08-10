package com.kesepain.kemoapp.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.IllustratedEmptyState
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.records
import com.kesepain.kemoapp.ui.components.stringItems
import kotlinx.serialization.json.JsonElement

@Composable
fun ModelsScreen(value: JsonElement?, onRefresh: () -> Unit, onSelect: (String) -> Unit) {
    val records = value.records("models", "items", "data")
    val names = (records.map { it.text("id", "name", "model") } + value.stringItems("models", "items", "data")).filter(String::isNotBlank).distinct()
    val serverSelected = records.firstOrNull { it.boolean("selected", "current", "active") == true }?.text("id", "name", "model").orEmpty()
    var selected by remember(value) { mutableStateOf(serverSelected) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader(stringResource(R.string.models), names.size) { OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) } } }
        if (names.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_models)) }
        items(names, key = { it }) { model ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { selected = model; onSelect(model) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = if (selected == model) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == model, onClick = { selected = model; onSelect(model) })
                    Text(model, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

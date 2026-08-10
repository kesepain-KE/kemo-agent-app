package com.kesepain.kemoapp.ui.screens.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.IllustratedEmptyState
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.StatusChip
import com.kesepain.kemoapp.ui.components.records
import kotlinx.serialization.json.JsonElement

@Composable
fun KnowledgeScreen(value: JsonElement?, onRefresh: () -> Unit, onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val entries = value.records("items", "hits", "documents", "knowledge")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader(stringResource(R.string.knowledge), entries.size) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
                Button(onClick = { if (query.isBlank()) onRefresh() else onSearch(query) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.search)) }
            }
        }
        if (entries.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_knowledge)) }
        itemsIndexed(entries, key = { index, entry -> "knowledge-$index-${entry.text("path", "id", "title", "name")}" }) { _, entry ->
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(entry.text("title", "name", "filename").ifBlank { entry.text("path", "id") }, style = MaterialTheme.typography.titleMedium)
                    val summary = entry.text("summary", "description", "snippet", "content")
                    if (summary.isNotBlank()) Text(summary.take(260), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entry.text("source", "scope", "category").takeIf(String::isNotBlank)?.let { StatusChip(it) }
                }
            }
        }
    }
}

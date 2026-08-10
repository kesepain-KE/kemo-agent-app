package com.kesepain.kemoapp.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.records
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun VersionScreen(value: JsonElement?) {
    val root = value as? JsonObject
    val mainVersion = (root?.get("version") as? JsonPrimitive)?.contentOrNull.orEmpty()
    val components = value.records("components").associateBy { it.text("id") }
    val rows = listOf(
        R.string.main_version to mainVersion,
        R.string.core_version to components["core"]?.text("version").orEmpty(),
        R.string.web_version to components["web"]?.text("version").orEmpty(),
        R.string.agent_version to components["agents"]?.text("version").orEmpty(),
        R.string.tools_version to components["plugins"]?.text("version").orEmpty(),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.kemo_brand_internal),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(148.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(stringResource(R.string.version_details), style = MaterialTheme.typography.headlineSmall)
            }
        }
        rows.forEach { (label, version) ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(version.ifBlank { "—" }, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

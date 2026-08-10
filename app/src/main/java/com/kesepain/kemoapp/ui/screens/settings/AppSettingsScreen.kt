package com.kesepain.kemoapp.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R

@Composable
fun AppSettingsScreen(downloadDirectoryUri: String, onDownloadDirectoryChanged: (String) -> Unit) {
    val context = LocalContext.current
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onDownloadDirectoryChanged(uri.toString())
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_configuration), style = MaterialTheme.typography.headlineSmall)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.download_location), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (downloadDirectoryUri.isBlank()) stringResource(R.string.system_download_directory) else downloadDirectoryUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { directoryPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.choose_download_directory))
                }
                if (downloadDirectoryUri.isNotBlank()) {
                    OutlinedButton(onClick = { onDownloadDirectoryChanged("") }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.restore_default_download_directory))
                    }
                }
            }
        }
    }
}

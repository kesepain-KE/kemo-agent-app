package com.kesepain.kemoapp.ui.screens.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.FilePreviewUi
import com.kesepain.kemoapp.ui.components.IllustratedEmptyState
import com.kesepain.kemoapp.ui.components.LoadingOutlinedButton
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.records
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FilesScreen(
    uploadValue: JsonElement?,
    generatedValue: JsonElement?,
    pendingKeys: Set<String>,
    error: String,
    onBrowse: (String, String, Int) -> Unit,
    onUpload: (Uri, String) -> Unit,
    onDownload: (String, String) -> Unit,
    onDelete: (String, String) -> Unit,
    preview: FilePreviewUi?,
    onPreview: (String, String, String) -> Unit,
    onClosePreview: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val uploadFiles = uploadValue.records("items", "files", "entries")
    val generatedFiles = generatedValue.records("items", "files", "entries")
    val files = if (selected == 0) uploadFiles else generatedFiles
    val scope = if (selected == 0) "upload" else "download"
    val listing = if (selected == 0) uploadValue else generatedValue
    val currentPath = listing.listingText("path")
    val currentPage = listing.paginationInt("page", 1)
    val totalPages = listing.paginationInt("total_pages", 1)
    val uploadBusy = "upload" in pendingKeys
    val refreshBusy = "refresh:files:$scope" in pendingKeys
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onUpload(uri, currentPath)
    }
    LaunchedEffect(Unit) {
        onBrowse("upload", "", 1)
        onBrowse("download", "", 1)
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text(stringResource(R.string.upload_files)) })
            Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text(stringResource(R.string.generated_files)) })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionHeader(stringResource(if (selected == 0) R.string.upload_files else R.string.generated_files)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected == 0) {
                            LoadingOutlinedButton(onClick = { uploadPicker.launch(arrayOf("*/*")) }, loading = uploadBusy) {
                                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(if (uploadBusy) R.string.uploading else R.string.upload_file))
                            }
                        }
                        LoadingOutlinedButton(onClick = { onBrowse(scope, currentPath, currentPage) }, loading = refreshBusy) {
                            Text(stringResource(R.string.refresh))
                        }
                    }
                }
            }
            if (error.isNotBlank()) {
                item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (currentPath.isNotBlank()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { onBrowse(scope, parentPath(currentPath), 1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_parent))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.current_folder), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentPath, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        }
                    }
                }
            }
            if (files.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_files)) }
            itemsIndexed(files, key = { index, file -> "$scope-$index-${file.text("path", "id", "name")}" }) { _, file ->
                val path = file.text("relative_path", "path", "id", "name")
                val rawName = file.text("name", "filename").ifBlank { path.substringAfterLast('/').substringAfterLast('\\') }
                val isDirectory = file.text("type", "kind").lowercase() in setOf("directory", "dir", "folder") ||
                    file.boolean("is_dir", "is_directory") == true
                Card(
                    onClick = {
                        if (isDirectory) onBrowse(scope, path, 1)
                        else onPreview(scope, path, rawName)
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(truncateFileName(rawName), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                if (isDirectory) {
                                    stringResource(R.string.folder_items, (file.number("child_count", "items") ?: 0.0).toInt())
                                } else {
                                    listOf(
                                        formatFileSize(file.number("size", "size_bytes") ?: file.text("size", "size_bytes").toDoubleOrNull()),
                                        formatFileTime(file.text("modified_at", "updated_at", "mtime", "created_at")),
                                    ).filter(String::isNotBlank).joinToString(" · ")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isDirectory) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.open_folder))
                        } else if (selected == 0) {
                            LoadingOutlinedButton(onClick = { onDelete("upload", path) }, loading = "file:upload:$path" in pendingKeys) { Text(stringResource(R.string.delete)) }
                        } else {
                            LoadingOutlinedButton(onClick = { onDownload("download", path) }, loading = "download:download:$path" in pendingKeys) { Text(stringResource(R.string.download)) }
                        }
                    }
                }
            }
            if (totalPages > 1) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingOutlinedButton(onClick = { onBrowse(scope, currentPath, currentPage - 1) }, enabled = currentPage > 1, loading = refreshBusy) {
                            Text(stringResource(R.string.previous_page))
                        }
                        Text(stringResource(R.string.page_counter, currentPage, totalPages), style = MaterialTheme.typography.bodySmall)
                        LoadingOutlinedButton(onClick = { onBrowse(scope, currentPath, currentPage + 1) }, enabled = currentPage < totalPages, loading = refreshBusy) {
                            Text(stringResource(R.string.next_page))
                        }
                    }
                }
            }
        }
    }
    preview?.let { value ->
        FilePreviewDialog(
            preview = value,
            onDismiss = onClosePreview,
            downloading = "download:${value.scope}:${value.path}" in pendingKeys,
            onDownload = { onDownload(value.scope, value.path) },
        )
    }
}

private fun JsonElement?.listingText(key: String): String =
    (((this as? JsonObject)?.get(key)) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonElement?.paginationInt(key: String, fallback: Int): Int =
    ((((this as? JsonObject)?.get("pagination") as? JsonObject)?.get(key)) as? JsonPrimitive)
        ?.contentOrNull?.toIntOrNull()?.coerceAtLeast(1) ?: fallback

private fun parentPath(path: String): String = path.trim('/').substringBeforeLast('/', "")

private fun truncateFileName(value: String): String = if (value.length <= 30) value else value.take(29) + "…"

private fun formatFileSize(bytes: Double?): String {
    if (bytes == null || bytes < 0) return ""
    val kilobytes = bytes / 1024.0
    return if (kilobytes >= 1024.0) {
        String.format(Locale.US, "%.2f MB", kilobytes / 1024.0)
    } else {
        String.format(Locale.US, "%.1f KB", kilobytes)
    }
}

private fun formatFileTime(value: String): String {
    if (value.isBlank()) return ""
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val instant = value.toDoubleOrNull()?.let { number ->
        if (number > 10_000_000_000L) Instant.ofEpochMilli(number.toLong()) else Instant.ofEpochSecond(number.toLong())
    } ?: runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    if (instant != null) return formatter.withZone(ZoneId.systemDefault()).format(instant)
    return runCatching { LocalDateTime.parse(value).format(formatter) }.getOrDefault(value.take(19).replace('T', ' '))
}

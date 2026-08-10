package com.kesepain.kemoapp.ui.screens.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.FilePreviewUi
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.SafeMarkdown
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewDialog(preview: FilePreviewUi, onDismiss: () -> Unit, onDownload: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(preview.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
                Button(onClick = onDownload) { Text(stringResource(R.string.download)) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            when {
                preview.extension in setOf("md", "markdown") -> SelectionContainer {
                    SafeMarkdown(preview.text, streaming = false, modifier = Modifier.fillMaxSize().padding(16.dp), compact = true)
                }
                preview.extension == "pdf" || preview.mimeType == "application/pdf" -> PdfPreview(preview)
                preview.mimeType.startsWith("image/") || preview.extension in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp") -> ImagePreview(preview)
                preview.text.isNotBlank() -> SelectionContainer {
                    Text(
                        preview.text,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.preview_external_hint), modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(preview: FilePreviewUi) {
    val bitmap = remember(preview.bytes.contentHashCode()) {
        BitmapFactory.decodeByteArray(preview.bytes, 0, preview.bytes.size)?.asImageBitmap()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap == null) CircularProgressIndicator()
        else Image(
            bitmap = bitmap,
            contentDescription = preview.name,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
        )
    }
}

@Composable
private fun PdfPreview(preview: FilePreviewUi) {
    val context = LocalContext.current
    val file = remember(preview.bytes.contentHashCode()) {
        File.createTempFile("kemo-preview-", ".pdf", context.cacheDir).apply { writeBytes(preview.bytes) }
    }
    DisposableEffect(file) { onDispose { file.delete() } }
    val pageCount = remember(file) { pdfPageCount(file) }
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    val bitmap = remember(file, pageIndex) { renderPdfPage(file, pageIndex) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text(stringResource(R.string.previous_page)) }
            Text(stringResource(R.string.page_counter, pageIndex + 1, pageCount.coerceAtLeast(1)), modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { pageIndex++ }, enabled = pageIndex + 1 < pageCount) { Text(stringResource(R.string.next_page)) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (bitmap == null) CircularProgressIndicator()
            else Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = preview.name,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
            )
        }
    }
}

private fun pdfPageCount(file: File): Int = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { it.pageCount }
    }
}.getOrDefault(0)

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)).use { page ->
                Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}.getOrNull()

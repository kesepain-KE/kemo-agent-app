package com.kesepain.kemoapp.ui.components

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.stream.ChatMediaUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ChatMediaCard(
    media: ChatMediaUi,
    onLoad: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = remember(media.type, media.mimeType, media.name) {
        mediaKind(media.type, media.mimeType, media.name)
    }
    LaunchedEffect(media.assetId, media.path, media.localUri, media.loading, media.error) {
        if (kind == "image" && media.localUri.isBlank() && !media.loading && media.error.isBlank() && media.size <= AUTO_IMAGE_LIMIT_BYTES) {
            onLoad()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                media.localUri.isNotBlank() && kind == "image" -> InlineImage(media.localUri, media.name)
                media.localUri.isNotBlank() && kind == "audio" -> InlineAudio(media.localUri)
                media.localUri.isNotBlank() && kind == "video" -> InlineVideo(media.localUri)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (kind) {
                        "image" -> Icons.Default.Image
                        "audio" -> Icons.Default.AudioFile
                        "video" -> Icons.Default.VideoFile
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                    Text(media.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(
                        "${media.mimeType} · ${formatMediaBytes(media.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (media.error.isNotBlank()) {
                        Text(media.error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (kind in MEDIA_KINDS && media.localUri.isBlank()) {
                    IconButton(onClick = onLoad, enabled = !media.loading) {
                        if (media.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, stringResource(R.string.media_load_preview))
                    }
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, stringResource(R.string.download))
                }
            }
        }
    }
}

@Composable
private fun InlineImage(uri: String, name: String) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var decoding by remember(uri) { mutableStateOf(true) }
    LaunchedEffect(uri) {
        decoding = true
        bitmap = withContext(Dispatchers.IO) {
            decodePreviewBitmap(context, Uri.parse(uri))?.asImageBitmap()
        }
        decoding = false
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp),
        contentAlignment = Alignment.Center,
    ) {
        val value = bitmap
        when {
            value != null -> Image(
                bitmap = value,
                contentDescription = name,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Fit,
            )
            decoding -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else -> Text(
                stringResource(R.string.media_preview_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * Generated images can be small on disk but still expand to hundreds of megabytes when decoded.
 * Decode only the resolution the in-chat preview can reasonably display; the download action keeps
 * the original asset available.
 */
private fun decodePreviewBitmap(context: android.content.Context, uri: Uri): android.graphics.Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_PREVIEW_DIMENSION)
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()

private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    var longest = maxOf(width, height)
    while (longest > maxDimension) {
        sample *= 2
        longest /= 2
    }
    return sample
}

@Composable
private fun InlineAudio(uri: String) {
    val context = LocalContext.current
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(uri) { mutableStateOf(false) }
    var position by remember(uri) { mutableIntStateOf(0) }
    var duration by remember(uri) { mutableIntStateOf(0) }
    var failed by remember(uri) { mutableStateOf(false) }
    DisposableEffect(uri) {
        val value = runCatching { MediaPlayer.create(context, Uri.parse(uri)) }.getOrNull()
        player = value
        duration = value?.duration?.coerceAtLeast(0) ?: 0
        failed = value == null
        value?.setOnErrorListener { _, _, _ ->
            failed = true
            playing = false
            true
        }
        value?.setOnCompletionListener {
            playing = false
            position = duration
        }
        onDispose {
            value?.release()
            if (player === value) player = null
        }
    }
    LaunchedEffect(playing, player) {
        while (playing) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(300)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                val value = player ?: return@IconButton
                if (value.isPlaying) value.pause() else {
                    if (duration > 0 && position >= duration) value.seekTo(0)
                    value.start()
                }
                playing = value.isPlaying
            },
            enabled = player != null,
        ) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) R.string.media_pause else R.string.media_play))
        }
        Slider(
            value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { next -> position = next.toInt() },
            onValueChangeFinished = { player?.seekTo(position) },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = player != null && duration > 0,
            modifier = Modifier.weight(1f),
        )
        Text("${formatMediaTime(position)}/${formatMediaTime(duration)}", style = MaterialTheme.typography.labelSmall)
      }
      if (failed) {
          Text(
              stringResource(R.string.media_preview_failed),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
          )
      }
    }
}

@Composable
private fun InlineVideo(uri: String) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(Uri.parse(uri))
                tag = uri
            }
        },
        update = { view ->
            if (view.tag != uri) {
                view.setVideoURI(Uri.parse(uri))
                view.tag = uri
            }
        },
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
    )
}

private fun formatMediaBytes(value: Long): String = when {
    value <= 0 -> "—"
    value >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format(Locale.US, "%.1f KB", value / 1024.0)
    else -> "$value B"
}

private fun formatMediaTime(milliseconds: Int): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

/** Backend artifacts are not fully consistent: some audio outputs are labelled
 * `audio_output`/`speech` and some only provide a MIME type. Keep rendering
 * resilient by deriving the presentation kind from all available metadata. */
private fun mediaKind(type: String, mimeType: String, name: String): String {
    val normalizedType = type.trim().lowercase()
    if (normalizedType in MEDIA_KINDS) return normalizedType
    if (normalizedType in setOf("audio_output", "speech", "voice", "sound")) return "audio"
    if (normalizedType in setOf("video_output", "movie")) return "video"
    if (normalizedType in setOf("image_output", "picture", "photo")) return "image"
    val mime = mimeType.trim().lowercase()
    if (mime.startsWith("audio/")) return "audio"
    if (mime.startsWith("video/")) return "video"
    if (mime.startsWith("image/")) return "image"
    return when (name.substringAfterLast('.', "").lowercase()) {
        in AUDIO_EXTENSIONS -> "audio"
        in VIDEO_EXTENSIONS -> "video"
        in IMAGE_EXTENSIONS -> "image"
        else -> "file"
    }
}

private val MEDIA_KINDS = setOf("image", "audio", "video")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "flac", "opus", "amr", "3gp")
private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v", "avi")
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
private const val AUTO_IMAGE_LIMIT_BYTES = 24L * 1024L * 1024L
private const val MAX_IMAGE_PREVIEW_DIMENSION = 1920

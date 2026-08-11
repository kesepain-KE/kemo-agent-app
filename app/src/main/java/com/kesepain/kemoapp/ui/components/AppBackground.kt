package com.kesepain.kemoapp.ui.components

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun AppBackground(
    uriValue: String,
    mimeType: String,
    darkTheme: Boolean,
    reloadKey: Long = 0L,
    modifier: Modifier = Modifier,
) {
    if (uriValue.isBlank()) return
    val chatScrolling by BackgroundPerformanceController.scrolling.collectAsState()
    Box(modifier.fillMaxSize()) {
        val mediaModifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.01f
            scaleY = 1.01f
        }.blur(if (chatScrolling) 0.dp else 1.dp)
        // Some document providers reuse the same content URI when a user picks
        // a replacement. The explicit revision key recreates both bitmap and
        // MediaPlayer state even when uriValue and mimeType did not change.
        key(uriValue, mimeType, reloadKey) {
            if (mimeType.startsWith("video/")) {
                VideoBackground(uriValue, chatScrolling, mediaModifier)
            } else {
                ImageBackground(uriValue, mediaModifier)
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                if (darkTheme) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.34f),
            ),
        )
    }
}

@Composable
private fun ImageBackground(uriValue: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uriValue) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriValue)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 2160 || bounds.outHeight / sample > 2160) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun VideoBackground(uriValue: String, suspendPlayback: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uriValue) { MediaPlayer() }
    var prepared by remember(uriValue) { mutableStateOf(false) }

    LaunchedEffect(prepared, suspendPlayback, lifecycleOwner) {
        if (!prepared) return@LaunchedEffect
        if (suspendPlayback) {
            if (player.isPlaying) runCatching { player.pause() }
        } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !player.isPlaying) {
            runCatching { player.start() }
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (prepared && !player.isPlaying) runCatching { player.start() }
                Lifecycle.Event.ON_STOP -> if (prepared && player.isPlaying) runCatching { player.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { player.release() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextureView(viewContext).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            player.setSurface(Surface(texture))
                            player.setDataSource(context, Uri.parse(uriValue))
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            player.setOnPreparedListener { mediaPlayer ->
                                prepared = true
                                updateVideoTransform(this@apply, width, height, mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                            }
                            player.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                                updateVideoTransform(this@apply, this@apply.width, this@apply.height, videoWidth, videoHeight)
                            }
                            player.setOnErrorListener { _, _, _ -> true }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        updateVideoTransform(this@apply, width, height, player.videoWidth, player.videoHeight)
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        runCatching { player.setSurface(null) }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

object BackgroundPerformanceController {
    private val _scrolling = MutableStateFlow(false)
    val scrolling = _scrolling.asStateFlow()

    fun setChatScrolling(value: Boolean) {
        _scrolling.value = value
    }
}

private fun updateVideoTransform(view: TextureView, viewWidth: Int, viewHeight: Int, videoWidth: Int, videoHeight: Int) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val defaultScaleX = viewWidth.toFloat() / videoWidth.toFloat()
    val defaultScaleY = viewHeight.toFloat() / videoHeight.toFloat()
    val cropScale = max(defaultScaleX, defaultScaleY)
    view.setTransform(
        Matrix().apply {
            setScale(
                cropScale / defaultScaleX,
                cropScale / defaultScaleY,
                viewWidth / 2f,
                viewHeight / 2f,
            )
        },
    )
}

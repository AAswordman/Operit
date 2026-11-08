package com.ai.assistance.operit.ui.theme

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.logging.AppLogger
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView

@Composable
fun AppBackgroundLayer(
    darkTheme: Boolean,
    useBackgroundImage: Boolean,
    backgroundImageUri: String?,
    backgroundImageOpacity: Float,
    backgroundMediaType: String,
    videoBackgroundMuted: Boolean,
    videoBackgroundLoop: Boolean,
    useBackgroundBlur: Boolean,
    backgroundBlurRadius: Float,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val context = LocalContext.current
    val baseBackgroundColor = if (darkTheme) Color.Black else Color.White

    val exoPlayer =
        remember(
            useBackgroundImage,
            backgroundImageUri,
            backgroundMediaType,
            videoBackgroundLoop,
            videoBackgroundMuted,
        ) {
            if (
                useBackgroundImage &&
                backgroundImageUri != null &&
                backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_VIDEO
            ) {
                ExoPlayer.Builder(context)
                    .setLoadControl(
                        DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                5000,
                                10000,
                                500,
                                1000,
                            )
                            .setTargetBufferBytes(5 * 1024 * 1024)
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build()
                    )
                    .build()
                    .apply {
                        repeatMode =
                            if (videoBackgroundLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                        volume = if (videoBackgroundMuted) 0f else 1f
                        playWhenReady = true

                        try {
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(backgroundImageUri))
                                .build()
                            setMediaItem(mediaItem)
                            prepare()
                        } catch (e: Exception) {
                            AppLogger.e(
                                "AppBackgroundLayer",
                                "Error loading video background: ${e.message}",
                                e
                            )
                        }
                    }
            } else {
                null
            }
        }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                exoPlayer?.release()
            } catch (e: Exception) {
                AppLogger.e("AppBackgroundLayer", "ExoPlayer release error", e)
            }
        }
    }

    Box(modifier = modifier.background(baseBackgroundColor)) {
        if (useBackgroundImage && backgroundImageUri != null) {
            val uri = Uri.parse(backgroundImageUri)

            if (backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
                val painter = rememberAsyncImagePainter(model = uri)

                LaunchedEffect(painter.state) {
                    if (painter.state is AsyncImagePainter.State.Error) {
                        AppLogger.e(
                            "AppBackgroundLayer",
                            "Error loading background image from URI: $backgroundImageUri"
                        )
                    }
                }

                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxSize()
                            .alpha(backgroundImageOpacity)
                            .then(
                                if (useBackgroundBlur) {
                                    Modifier.blur(radius = backgroundBlurRadius.dp)
                                } else {
                                    Modifier
                                }
                            ),
                    contentScale = ContentScale.Crop,
                )
            } else {
                exoPlayer?.let { player ->
                    val overlayAlpha = ((1f - backgroundImageOpacity) * 255).toInt().coerceIn(0, 255)
                    val overlayColor =
                        android.graphics.Color.argb(
                            overlayAlpha,
                            if (darkTheme) 0 else 255,
                            if (darkTheme) 0 else 255,
                            if (darkTheme) 0 else 255,
                        )

                    AndroidView(
                        factory = { ctx ->
                            (LayoutInflater.from(ctx)
                                .inflate(R.layout.view_background_texture_player, null, false) as StyledPlayerView)
                                .apply {
                                    this.player = player
                                    useController = false
                                    layoutParams =
                                        android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    setBackgroundColor(if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                    setShutterBackgroundColor(if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                    setKeepContentOnPlayerReset(true)
                                    foreground = android.graphics.drawable.ColorDrawable(overlayColor)
                                }
                        },
                        update = { view ->
                            if (view.player != player) {
                                view.player = player
                            }

                            view.setBackgroundColor(if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                            view.setShutterBackgroundColor(if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                            view.setKeepContentOnPlayerReset(true)
                            view.foreground = android.graphics.drawable.ColorDrawable(overlayColor)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

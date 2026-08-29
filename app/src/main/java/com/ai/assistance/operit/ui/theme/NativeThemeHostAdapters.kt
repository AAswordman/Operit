package com.ai.assistance.operit.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal sealed interface NativeThemeMainWindowChromeState {
    val navigationBarColor: Int
    val navigationBarContrastEnforced: Boolean
    val lightNavigationBarIcons: Boolean

    data class Hidden(
        override val navigationBarColor: Int,
        override val navigationBarContrastEnforced: Boolean,
        override val lightNavigationBarIcons: Boolean,
    ) : NativeThemeMainWindowChromeState

    data class Visible(
        val statusBarColor: Int,
        val lightStatusBarIcons: Boolean,
        override val navigationBarColor: Int,
        override val navigationBarContrastEnforced: Boolean,
        override val lightNavigationBarIcons: Boolean,
    ) : NativeThemeMainWindowChromeState
}

internal fun resolveNativeThemeMainWindowChromeState(
    resolvedTheme: ResolvedNativeThemeV1,
): NativeThemeMainWindowChromeState {
    val backgroundEnabled = resolvedTheme.background.enabled
    val navigationBarColor =
        if (backgroundEnabled) {
            AndroidColor.TRANSPARENT
        } else {
            resolvedTheme.colorScheme.background.toArgb()
        }
    val navigationBarContrastEnforced = !backgroundEnabled
    val lightNavigationBarIcons =
        if (backgroundEnabled) {
            !resolvedTheme.darkTheme
        } else {
            !isNativeThemeColorLight(resolvedTheme.colorScheme.background)
        }

    if (resolvedTheme.systemChrome.statusBarHidden) {
        return NativeThemeMainWindowChromeState.Hidden(
            navigationBarColor = navigationBarColor,
            navigationBarContrastEnforced = navigationBarContrastEnforced,
            lightNavigationBarIcons = lightNavigationBarIcons,
        )
    }

    val customStatusBarColor = resolvedTheme.systemChrome.customStatusBarColor
    val statusBarColor =
        when {
            resolvedTheme.systemChrome.statusBarTransparent -> Color.Transparent.toArgb()
            backgroundEnabled -> Color.Transparent.toArgb()
            resolvedTheme.systemChrome.useCustomStatusBarColor && customStatusBarColor != null ->
                customStatusBarColor
            else -> resolvedTheme.colorScheme.primary.toArgb()
        }

    return NativeThemeMainWindowChromeState.Visible(
        statusBarColor = statusBarColor,
        lightStatusBarIcons = isNativeThemeColorLight(Color(statusBarColor)),
        navigationBarColor = navigationBarColor,
        navigationBarContrastEnforced = navigationBarContrastEnforced,
        lightNavigationBarIcons = lightNavigationBarIcons,
    )
}

@Composable
internal fun NativeThemeMainWindowChromeHostAdapter(resolvedTheme: ResolvedNativeThemeV1) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val state = resolveNativeThemeMainWindowChromeState(resolvedTheme)
            when (state) {
                is NativeThemeMainWindowChromeState.Hidden -> {
                    insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                    insetsController?.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }

                is NativeThemeMainWindowChromeState.Visible -> {
                    insetsController?.show(WindowInsetsCompat.Type.statusBars())
                    window.statusBarColor = state.statusBarColor
                    insetsController?.isAppearanceLightStatusBars = state.lightStatusBarIcons
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = state.navigationBarContrastEnforced
            }
            window.navigationBarColor = state.navigationBarColor
            insetsController?.isAppearanceLightNavigationBars = state.lightNavigationBarIcons
        }
    }
}

internal enum class NativeThemeBackgroundPlaybackBehavior {
    FOLLOW_LIFECYCLE,
    START_IMMEDIATELY,
}

internal enum class NativeThemeVideoOverlayAlphaPolicy {
    PRESERVE_CALCULATED_VALUE,
    CLAMP_TO_COLOR_RANGE,
}

internal data class NativeThemeBackgroundLoadFailure(
    val uri: String,
    val mediaType: String,
    val cause: Throwable?,
)

@Composable
internal fun NativeThemeBackgroundMediaHostAdapter(
    darkTheme: Boolean,
    background: NativeThemeBackgroundSpec,
    playbackBehavior: NativeThemeBackgroundPlaybackBehavior,
    videoOverlayAlphaPolicy: NativeThemeVideoOverlayAlphaPolicy,
    contentDescription: String?,
    modifier: Modifier,
    onLoadFailure: (NativeThemeBackgroundLoadFailure) -> Unit,
) {
    val uri = background.uri
    if (!background.enabled || uri == null) {
        return
    }

    when (background.mediaType) {
        UserPreferencesManager.MEDIA_TYPE_IMAGE ->
            NativeThemeImageBackgroundMedia(
                darkTheme = darkTheme,
                background = background,
                uri = uri,
                contentDescription = contentDescription,
                modifier = modifier,
                onLoadFailure = onLoadFailure,
            )

        UserPreferencesManager.MEDIA_TYPE_VIDEO ->
            NativeThemeVideoBackgroundMedia(
                darkTheme = darkTheme,
                background = background,
                uri = uri,
                playbackBehavior = playbackBehavior,
                videoOverlayAlphaPolicy = videoOverlayAlphaPolicy,
                modifier = modifier,
                onLoadFailure = onLoadFailure,
            )
    }
}

@Composable
private fun NativeThemeImageBackgroundMedia(
    darkTheme: Boolean,
    background: NativeThemeBackgroundSpec,
    uri: String,
    contentDescription: String?,
    modifier: Modifier,
    onLoadFailure: (NativeThemeBackgroundLoadFailure) -> Unit,
) {
    val painter =
        rememberAsyncImagePainter(
            model = Uri.parse(uri),
            error = rememberAsyncImagePainter(if (darkTheme) Color.Black else Color.White),
        )
    val painterState = painter.state

    LaunchedEffect(painterState) {
        if (painterState is AsyncImagePainter.State.Error) {
            onLoadFailure(
                NativeThemeBackgroundLoadFailure(
                    uri = uri,
                    mediaType = UserPreferencesManager.MEDIA_TYPE_IMAGE,
                    cause = painterState.result.throwable,
                ),
            )
        }
    }

    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier =
            Modifier.fillMaxSize()
                .alpha(background.opacity)
                .then(
                    if (background.blurEnabled) {
                        Modifier.blur(radius = background.blurRadius.dp)
                    } else {
                        Modifier
                    },
                )
                .then(modifier),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun NativeThemeVideoBackgroundMedia(
    darkTheme: Boolean,
    background: NativeThemeBackgroundSpec,
    uri: String,
    playbackBehavior: NativeThemeBackgroundPlaybackBehavior,
    videoOverlayAlphaPolicy: NativeThemeVideoOverlayAlphaPolicy,
    modifier: Modifier,
    onLoadFailure: (NativeThemeBackgroundLoadFailure) -> Unit,
) {
    val context = LocalContext.current
    val latestOnLoadFailure by rememberUpdatedState(onLoadFailure)
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialPlayWhenReady =
        when (playbackBehavior) {
            NativeThemeBackgroundPlaybackBehavior.FOLLOW_LIFECYCLE ->
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            NativeThemeBackgroundPlaybackBehavior.START_IMMEDIATELY -> true
        }
    val exoPlayer =
        remember(
            uri,
            background.videoLoop,
            background.videoMuted,
            playbackBehavior,
        ) {
            ExoPlayer.Builder(context)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(5000, 10000, 500, 1000)
                        .setTargetBufferBytes(5 * 1024 * 1024)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build(),
                )
                .build()
                .apply {
                    repeatMode =
                        if (background.videoLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                    volume = if (background.videoMuted) 0f else 1f
                    playWhenReady = initialPlayWhenReady

                    try {
                        setMediaItem(MediaItem.Builder().setUri(Uri.parse(uri)).build())
                        prepare()
                    } catch (e: Exception) {
                        onLoadFailure(
                            NativeThemeBackgroundLoadFailure(
                                uri = uri,
                                mediaType = UserPreferencesManager.MEDIA_TYPE_VIDEO,
                                cause = e,
                            ),
                        )
                    }
                }
        }

    DisposableEffect(exoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    latestOnLoadFailure(
                        NativeThemeBackgroundLoadFailure(
                            uri = uri,
                            mediaType = UserPreferencesManager.MEDIA_TYPE_VIDEO,
                            cause = error,
                        ),
                    )
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (e: Exception) {
                AppLogger.e("NativeThemeBackgroundMediaHostAdapter", "Error releasing video background", e)
            }
        }
    }

    if (playbackBehavior == NativeThemeBackgroundPlaybackBehavior.FOLLOW_LIFECYCLE) {
        DisposableEffect(lifecycleOwner, exoPlayer) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            exoPlayer.playWhenReady = false
                            exoPlayer.pause()
                        }

                        Lifecycle.Event.ON_RESUME -> {
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        }

                        else -> Unit
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val videoBackgroundColor = if (darkTheme) AndroidColor.BLACK else AndroidColor.WHITE
    val videoOverlayColor =
        AndroidColor.argb(
            resolveNativeThemeVideoOverlayAlpha(background.opacity, videoOverlayAlphaPolicy),
            if (darkTheme) 0 else 255,
            if (darkTheme) 0 else 255,
            if (darkTheme) 0 else 255,
        )
    AndroidView(
        factory = { viewContext ->
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.view_background_texture_player, null, false) as StyledPlayerView)
                .apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(videoBackgroundColor)
                    setShutterBackgroundColor(videoBackgroundColor)
                    setKeepContentOnPlayerReset(true)
                    foreground = ColorDrawable(videoOverlayColor)
                }
        },
        update = { view ->
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }

            view.setBackgroundColor(videoBackgroundColor)
            view.setShutterBackgroundColor(videoBackgroundColor)
            view.setKeepContentOnPlayerReset(true)
            view.foreground = ColorDrawable(videoOverlayColor)
        },
        modifier = Modifier.fillMaxSize().then(modifier),
    )
}

private fun isNativeThemeColorLight(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}

internal fun resolveNativeThemeVideoOverlayAlpha(
    opacity: Float,
    policy: NativeThemeVideoOverlayAlphaPolicy,
): Int {
    val calculatedAlpha = ((1f - opacity) * 255).toInt()
    return when (policy) {
        NativeThemeVideoOverlayAlphaPolicy.PRESERVE_CALCULATED_VALUE -> calculatedAlpha
        NativeThemeVideoOverlayAlphaPolicy.CLAMP_TO_COLOR_RANGE -> calculatedAlpha.coerceIn(0, 255)
    }
}

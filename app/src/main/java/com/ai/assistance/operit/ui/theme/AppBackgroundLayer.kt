package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.util.AppLogger

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
    val background =
        NativeThemeBackgroundSpec(
            enabled = useBackgroundImage && backgroundImageUri != null,
            uri = backgroundImageUri,
            mediaType = backgroundMediaType,
            opacity = backgroundImageOpacity,
            blurEnabled = useBackgroundBlur,
            blurRadius = backgroundBlurRadius,
            videoMuted = videoBackgroundMuted,
            videoLoop = videoBackgroundLoop,
        )

    NativeThemeBackgroundLayer(
        darkTheme = darkTheme,
        background = background,
        modifier = modifier,
    )
}

@Composable
internal fun ResolvedThemeBackgroundLayer(
    resolvedTheme: ResolvedNativeThemeV1,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    NativeThemeBackgroundLayer(
        darkTheme = resolvedTheme.darkTheme,
        background = resolvedTheme.background,
        modifier = modifier,
    )
}

@Composable
private fun NativeThemeBackgroundLayer(
    darkTheme: Boolean,
    background: NativeThemeBackgroundSpec,
    modifier: Modifier,
) {
    val baseBackgroundColor = if (darkTheme) Color.Black else Color.White

    Box(modifier = modifier.background(baseBackgroundColor)) {
        NativeThemeBackgroundMediaHostAdapter(
            darkTheme = darkTheme,
            background = background,
            playbackBehavior = NativeThemeBackgroundPlaybackBehavior.START_IMMEDIATELY,
            videoOverlayAlphaPolicy = NativeThemeVideoOverlayAlphaPolicy.CLAMP_TO_COLOR_RANGE,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            onLoadFailure = { failure ->
                val cause = failure.cause
                val message = "Error loading ${failure.mediaType} background from URI: ${failure.uri}"
                if (cause != null) {
                    AppLogger.e("AppBackgroundLayer", message, cause)
                } else {
                    AppLogger.e("AppBackgroundLayer", message)
                }
            },
        )
    }
}

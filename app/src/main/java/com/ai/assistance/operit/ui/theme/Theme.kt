package com.ai.assistance.operit.ui.theme

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val themeSnapshot = rememberActiveThemePreferenceSnapshot()
    val backgroundTarget = themeSnapshot.toThemeTarget()

    fun disableBackgroundForTarget(
        target: ActivePrompt,
        failure: NativeThemeBackgroundLoadFailure,
    ) {
        coroutineScope.launch {
            activePromptManager.mutateActiveThemeForPrompt(target) { values ->
                // A delayed failure must not overwrite a resource selected after loading began.
                if (shouldDisableBackgroundForFailure(values, failure)) {
                    values.withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, false)
                } else {
                    values
                }
            }
        }
    }

    val systemDarkTheme = isSystemInDarkTheme()
    val resolvedTheme =
        resolveNativeThemeV1(
            snapshot = themeSnapshot,
            environment =
                NativeThemeEnvironment(
                    hostSurface = NativeThemeHostSurface.MAIN,
                    systemDarkTheme = systemDarkTheme,
                ),
            baseColorScheme = { darkTheme ->
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        if (darkTheme) dynamicDarkColorScheme(context)
                        else dynamicLightColorScheme(context)
                    }
                    darkTheme -> NativeThemeV1DarkColorScheme
                    else -> NativeThemeV1LightColorScheme
                }
            },
        )
    val backgroundSpec = resolvedTheme.background
    val typographySpec = resolvedTheme.typography
    val darkTheme = resolvedTheme.darkTheme

    // 创建自定义 Typography
    val customTypography = remember(typographySpec) {
        createCustomTypography(
            context = context,
            useCustomFont = typographySpec.useCustomFont,
            fontType = typographySpec.fontType,
            systemFontName = typographySpec.systemFontName,
            customFontPath = typographySpec.customFontPath,
            fontScale = typographySpec.fontScale,
        )
    }

    NativeThemeMainWindowChromeHostAdapter(resolvedTheme)

    // 应用主题和自定义背景
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val liquidGlassBackdrop = rememberLayerBackdrop()
        val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null

        CompositionLocalProvider(
            LocalThemePreferenceSnapshot provides themeSnapshot,
            LocalResolvedNativeThemeV1 provides resolvedTheme,
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop)
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(if (darkTheme) Color.Black else Color.White)
                            .then(
                                if (waterGlassState != null) {
                                    Modifier.liquefiable(waterGlassState)
                                } else {
                                    Modifier
                                },
                            )
                )

                NativeThemeBackgroundMediaHostAdapter(
                    darkTheme = darkTheme,
                    background = backgroundSpec,
                    playbackBehavior = NativeThemeBackgroundPlaybackBehavior.FOLLOW_LIFECYCLE,
                    videoOverlayAlphaPolicy =
                        NativeThemeVideoOverlayAlphaPolicy.PRESERVE_CALCULATED_VALUE,
                    contentDescription = "Background Image",
                    modifier =
                        Modifier.fillMaxSize().then(
                            if (waterGlassState != null) {
                                Modifier.liquefiable(waterGlassState)
                            } else {
                                Modifier
                            },
                        ),
                    onLoadFailure = { failure ->
                        logNativeThemeMainBackgroundLoadFailure(failure)
                        disableBackgroundForTarget(backgroundTarget, failure)
                    },
                )
            }

            MaterialTheme(
                colorScheme = resolvedTheme.contentColorScheme,
                typography = customTypography,
                content = content,
            )
        }
    }
}

private fun logNativeThemeMainBackgroundLoadFailure(failure: NativeThemeBackgroundLoadFailure) {
    val cause = failure.cause
    val message = "Error loading ${failure.mediaType} background from URI: ${failure.uri}"
    if (cause != null) {
        AppLogger.e("OperitTheme", message, cause)
    } else {
        AppLogger.e("OperitTheme", message)
    }

    if (failure.mediaType == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
        val uri = Uri.parse(failure.uri)
        if (uri.scheme == "file") {
            val file = uri.path?.let(::File)
            if (file == null || !file.exists()) {
                AppLogger.e("OperitTheme", "Internal file doesn't exist: ${file?.absolutePath}")
            } else {
                AppLogger.e(
                    "OperitTheme",
                    "File exists but couldn't be loaded: ${file.absolutePath}, size: ${file.length()}",
                )
            }
        }
    }
}

internal fun shouldDisableBackgroundForFailure(
    values: ThemePreferenceValues,
    failure: NativeThemeBackgroundLoadFailure,
): Boolean =
    values.boolean(NativeThemePreferenceSchemaV1.useBackgroundImage) == true &&
        values.string(NativeThemePreferenceSchemaV1.backgroundImageUri) == failure.uri &&
        values.string(NativeThemePreferenceSchemaV1.backgroundMediaType) == failure.mediaType

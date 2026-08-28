package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.theme.LocalResolvedNativeThemeV1
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.NativeThemeEnvironment
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.NativeThemeV1DarkColorScheme
import com.ai.assistance.operit.ui.theme.NativeThemeV1LightColorScheme
import com.ai.assistance.operit.ui.theme.createCustomTypography
import com.ai.assistance.operit.ui.theme.resolveNativeThemeV1

@Composable
internal fun NativeThemeEditorPreviewTheme(
    values: ThemePreferenceValues,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val lightColorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            NativeThemeV1LightColorScheme
        }
    val darkColorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            NativeThemeV1DarkColorScheme
        }
    val previewSnapshot =
        remember(values) {
            ThemePreferenceSnapshot(
                source = "theme_editor_preview",
                values = values,
            )
        }
    val resolvedTheme =
        remember(previewSnapshot, systemDarkTheme, lightColorScheme, darkColorScheme) {
            resolveNativeThemeV1(
                snapshot = previewSnapshot,
                environment =
                    NativeThemeEnvironment(
                        hostSurface = NativeThemeHostSurface.OFFSCREEN,
                        systemDarkTheme = systemDarkTheme,
                    ),
                baseColorScheme = { darkTheme ->
                    if (darkTheme) darkColorScheme else lightColorScheme
                },
            )
        }
    val typography =
        remember(context, resolvedTheme.typography) {
            createCustomTypography(
                context = context,
                useCustomFont = resolvedTheme.typography.useCustomFont,
                fontType = resolvedTheme.typography.fontType,
                systemFontName = resolvedTheme.typography.systemFontName,
                customFontPath = resolvedTheme.typography.customFontPath,
                fontScale = resolvedTheme.typography.fontScale,
            )
        }

    CompositionLocalProvider(
        LocalThemePreferenceSnapshot provides previewSnapshot,
        LocalResolvedNativeThemeV1 provides resolvedTheme,
    ) {
        MaterialTheme(
            colorScheme = resolvedTheme.contentColorScheme,
            typography = typography,
            content = content,
        )
    }
}

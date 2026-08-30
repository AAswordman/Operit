package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.resolveNativeThemeForDetachedComposeHost

@Composable
internal fun NativeThemeEditorPreviewTheme(
    values: ThemePreferenceValues,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val previewSnapshot =
        remember(values) {
            ThemePreferenceSnapshot(
                source = "theme_editor_preview",
                values = values,
            )
        }
    val resolvedTheme =
        resolveNativeThemeForDetachedComposeHost(
            context = context,
            snapshot = previewSnapshot,
            hostSurface = NativeThemeHostSurface.EDITOR_PREVIEW,
            systemDarkTheme = systemDarkTheme,
        )

    NativeThemeOffscreenHost(
        snapshot = previewSnapshot,
        resolvedTheme = resolvedTheme,
        content = content,
    )
}

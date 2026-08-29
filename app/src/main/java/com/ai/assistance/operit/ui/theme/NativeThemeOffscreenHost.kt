package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot

internal fun resolveNativeThemeOffscreen(
    context: Context,
    snapshot: ThemePreferenceSnapshot,
    systemDarkTheme: Boolean,
): ResolvedNativeThemeV1 =
    resolveNativeThemeForDetachedComposeHost(
        context = context,
        snapshot = snapshot,
        hostSurface = NativeThemeHostSurface.OFFSCREEN,
        systemDarkTheme = systemDarkTheme,
    )

internal fun resolveNativeThemeForDetachedComposeHost(
    context: Context,
    snapshot: ThemePreferenceSnapshot,
    hostSurface: NativeThemeHostSurface,
    systemDarkTheme: Boolean,
): ResolvedNativeThemeV1 {
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

    return resolveNativeThemeForDetachedComposeHost(
        snapshot = snapshot,
        hostSurface = hostSurface,
        systemDarkTheme = systemDarkTheme,
        lightColorScheme = lightColorScheme,
        darkColorScheme = darkColorScheme,
    )
}

internal fun resolveNativeThemeOffscreen(
    snapshot: ThemePreferenceSnapshot,
    systemDarkTheme: Boolean,
    lightColorScheme: ColorScheme,
    darkColorScheme: ColorScheme,
): ResolvedNativeThemeV1 =
    resolveNativeThemeForDetachedComposeHost(
        snapshot = snapshot,
        hostSurface = NativeThemeHostSurface.OFFSCREEN,
        systemDarkTheme = systemDarkTheme,
        lightColorScheme = lightColorScheme,
        darkColorScheme = darkColorScheme,
    )

internal fun resolveNativeThemeForDetachedComposeHost(
    snapshot: ThemePreferenceSnapshot,
    hostSurface: NativeThemeHostSurface,
    systemDarkTheme: Boolean,
    lightColorScheme: ColorScheme,
    darkColorScheme: ColorScheme,
): ResolvedNativeThemeV1 =
    resolveNativeThemeV1(
        snapshot = snapshot,
        environment =
            NativeThemeEnvironment(
                hostSurface = hostSurface,
                systemDarkTheme = systemDarkTheme,
            ),
        baseColorScheme = { darkTheme -> if (darkTheme) darkColorScheme else lightColorScheme },
    )

@Composable
internal fun NativeThemeOffscreenHost(
    snapshot: ThemePreferenceSnapshot,
    resolvedTheme: ResolvedNativeThemeV1,
    content: @Composable () -> Unit,
) = NativeThemeResolvedComposeHost(
    snapshot = snapshot,
    resolvedTheme = resolvedTheme,
    content = content,
)

@Composable
internal fun NativeThemeFloatingHost(content: @Composable () -> Unit) {
    NativeThemeActiveDetachedComposeHost(
        hostSurface = NativeThemeHostSurface.FLOATING,
        content = content,
    )
}

@Composable
internal fun NativeThemeOverlayHost(content: @Composable () -> Unit) {
    NativeThemeActiveDetachedComposeHost(
        hostSurface = NativeThemeHostSurface.OVERLAY,
        content = content,
    )
}

@Composable
private fun NativeThemeActiveDetachedComposeHost(
    hostSurface: NativeThemeHostSurface,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val snapshot = rememberActiveThemePreferenceSnapshot()
    val resolvedTheme =
        resolveNativeThemeForDetachedComposeHost(
            context = context,
            snapshot = snapshot,
            hostSurface = hostSurface,
            systemDarkTheme = isSystemInDarkTheme(),
        )

    NativeThemeResolvedComposeHost(
        snapshot = snapshot,
        resolvedTheme = resolvedTheme,
        content = content,
    )
}

@Composable
private fun NativeThemeResolvedComposeHost(
    snapshot: ThemePreferenceSnapshot,
    resolvedTheme: ResolvedNativeThemeV1,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
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
        LocalThemePreferenceSnapshot provides snapshot,
        LocalResolvedNativeThemeV1 provides resolvedTheme,
    ) {
        MaterialTheme(
            colorScheme = resolvedTheme.contentColorScheme,
            typography = typography,
            content = content,
        )
    }
}

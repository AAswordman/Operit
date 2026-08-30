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
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot

internal fun resolveGlobalThemeOffscreen(
    context: Context,
    presentation: GlobalPresentationSnapshot,
    systemDarkTheme: Boolean,
): ResolvedGlobalTheme =
    resolveGlobalThemeForDetachedComposeHost(
        context = context,
        presentation = presentation,
        hostSurface = NativeThemeHostSurface.OFFSCREEN,
        systemDarkTheme = systemDarkTheme,
    )

internal fun resolveGlobalThemeForDetachedComposeHost(
    context: Context,
    presentation: GlobalPresentationSnapshot,
    hostSurface: NativeThemeHostSurface,
    systemDarkTheme: Boolean,
): ResolvedGlobalTheme {
    val (lightColorScheme, darkColorScheme) = resolveNativeThemeDetachedBaseColorSchemes(context)

    return resolveGlobalThemeForDetachedComposeHost(
        presentation = presentation,
        hostSurface = hostSurface,
        systemDarkTheme = systemDarkTheme,
        lightColorScheme = lightColorScheme,
        darkColorScheme = darkColorScheme,
    )
}

internal fun resolveNativeThemeDetachedBaseColorSchemes(
    context: Context,
): Pair<ColorScheme, ColorScheme> {
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
    return lightColorScheme to darkColorScheme
}

internal fun resolveGlobalThemeForDetachedComposeHost(
    presentation: GlobalPresentationSnapshot,
    hostSurface: NativeThemeHostSurface,
    systemDarkTheme: Boolean,
    lightColorScheme: ColorScheme,
    darkColorScheme: ColorScheme,
): ResolvedGlobalTheme =
    resolveGlobalThemeV1(
        presentation = presentation,
        environment =
            NativeThemeEnvironment(
                hostSurface = hostSurface,
                systemDarkTheme = systemDarkTheme,
            ),
        baseColorScheme = { darkTheme -> if (darkTheme) darkColorScheme else lightColorScheme },
    )

@Composable
internal fun NativeThemeOffscreenHost(
    presentation: GlobalPresentationSnapshot,
    resolvedTheme: ResolvedGlobalTheme,
    content: @Composable () -> Unit,
) = NativeThemeResolvedComposeHost(
    presentation = presentation,
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
    val presentation = rememberGlobalPresentation()
    val resolvedTheme =
        resolveGlobalThemeForDetachedComposeHost(
            context = context,
            presentation = presentation,
            hostSurface = hostSurface,
            systemDarkTheme = isSystemInDarkTheme(),
        )

    NativeThemeResolvedComposeHost(
        presentation = presentation,
        resolvedTheme = resolvedTheme,
        content = content,
    )
}

@Composable
private fun NativeThemeResolvedComposeHost(
    presentation: GlobalPresentationSnapshot,
    resolvedTheme: ResolvedGlobalTheme,
    content: @Composable () -> Unit,
) {
    val typography =
        remember(resolvedTheme.fontScale) {
            createCustomTypography(fontScale = resolvedTheme.fontScale)
        }

    CompositionLocalProvider(
        LocalGlobalPresentation provides presentation,
        LocalResolvedGlobalTheme provides resolvedTheme,
    ) {
        MaterialTheme(
            colorScheme = resolvedTheme.colorScheme,
            typography = typography,
            content = content,
        )
    }
}

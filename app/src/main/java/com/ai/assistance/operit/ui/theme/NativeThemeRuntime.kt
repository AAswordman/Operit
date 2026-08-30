package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import kotlinx.serialization.Serializable

@Serializable
internal enum class NativeThemeHostSurface {
    MAIN,
    FLOATING,
    OVERLAY,
    OFFSCREEN,
    GLANCE,
    DIAGNOSTIC,
}

internal data class NativeThemeEnvironment(
    val hostSurface: NativeThemeHostSurface,
    val systemDarkTheme: Boolean,
)

internal data class ResolvedGlobalTheme(
    val environment: NativeThemeEnvironment,
    val darkTheme: Boolean,
    val colorScheme: ColorScheme,
    val fontScale: Float,
)

internal val NativeThemeV1DarkColorScheme =
    darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

internal val NativeThemeV1LightColorScheme =
    lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

internal fun resolveGlobalThemeV1(
    presentation: GlobalPresentationSnapshot,
    environment: NativeThemeEnvironment,
    baseColorScheme: (darkTheme: Boolean) -> ColorScheme,
): ResolvedGlobalTheme {
    val darkTheme =
        presentation.themeMode == GlobalThemeMode.DARK ||
            (presentation.themeMode == GlobalThemeMode.SYSTEM && environment.systemDarkTheme)
    return ResolvedGlobalTheme(
        environment = environment,
        darkTheme = darkTheme,
        colorScheme = baseColorScheme(darkTheme),
        fontScale = presentation.fontScale,
    )
}

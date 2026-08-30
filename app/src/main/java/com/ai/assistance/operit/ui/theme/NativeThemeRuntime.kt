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

internal const val NATIVE_THEME_V1_DEFINITION_ID = "operit.native_v1"

internal fun resolveGlobalThemeV1(
    presentation: GlobalPresentationSnapshot,
    environment: NativeThemeEnvironment,
    baseColorScheme: (darkTheme: Boolean) -> ColorScheme,
    primaryColor: Color? = null,
): ResolvedGlobalTheme {
    val darkTheme =
        presentation.themeMode == GlobalThemeMode.DARK ||
            (presentation.themeMode == GlobalThemeMode.SYSTEM && environment.systemDarkTheme)
    val baseline = baseColorScheme(darkTheme)
    return ResolvedGlobalTheme(
        environment = environment,
        darkTheme = darkTheme,
        colorScheme =
            primaryColor?.let { color ->
                deriveColorSchemeWithPrimary(baseline, color, darkTheme)
            } ?: baseline,
        fontScale = presentation.fontScale,
    )
}

/**
 * Restricted primary-color derivation for theme packages: primary roles shift to the theme
 * color with readable container/on roles; every other role stays on the host baseline so a
 * package can never gut system colors by declaring one parameter.
 */
private fun deriveColorSchemeWithPrimary(
    baseline: ColorScheme,
    primary: Color,
    darkTheme: Boolean,
): ColorScheme {
    val onPrimary = contrastingNativeThemeColor(primary)
    return if (darkTheme) {
        val adjustedPrimary = lightenNativeThemeColor(primary, 0.2f)
        baseline.copy(
            primary = adjustedPrimary,
            onPrimary = contrastingNativeThemeColor(adjustedPrimary),
            primaryContainer = darkenNativeThemeColor(primary, 0.3f),
            onPrimaryContainer = Color.White,
        )
    } else {
        val primaryContainer = lightenNativeThemeColor(primary, 0.7f)
        baseline.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = contrastingNativeThemeColor(primaryContainer),
        )
    }
}

private fun contrastingNativeThemeColor(color: Color): Color {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return if (luminance > 0.5) Color.Black else Color.White
}

private fun lightenNativeThemeColor(color: Color, factor: Float): Color =
    Color(
        red = color.red + (1f - color.red) * factor,
        green = color.green + (1f - color.green) * factor,
        blue = color.blue + (1f - color.blue) * factor,
        alpha = color.alpha,
    )

private fun darkenNativeThemeColor(color: Color, factor: Float): Color =
    Color(
        red = color.red * (1f - factor),
        green = color.green * (1f - factor),
        blue = color.blue * (1f - factor),
        alpha = color.alpha,
    )

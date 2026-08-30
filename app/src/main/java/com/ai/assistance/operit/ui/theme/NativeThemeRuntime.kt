package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_DARK
import com.ai.assistance.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_LIGHT
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

internal data class NativeThemeBackgroundSpec(
    val enabled: Boolean,
    val uri: String?,
    val mediaType: String,
    val opacity: Float,
    val blurEnabled: Boolean,
    val blurRadius: Float,
    val videoMuted: Boolean,
    val videoLoop: Boolean,
)

internal data class NativeThemeTypographySpec(
    val useCustomFont: Boolean,
    val fontType: String,
    val systemFontName: String,
    val customFontPath: String?,
    val fontScale: Float,
)

internal data class NativeThemeSystemChromeSpec(
    val useCustomStatusBarColor: Boolean,
    val customStatusBarColor: Int?,
    val statusBarTransparent: Boolean,
    val statusBarHidden: Boolean,
)

internal data class ResolvedNativeThemeV1(
    val definitionId: String,
    val environment: NativeThemeEnvironment,
    val source: String,
    val sourceId: String?,
    val darkTheme: Boolean,
    val colorScheme: ColorScheme,
    val contentColorScheme: ColorScheme,
    val background: NativeThemeBackgroundSpec,
    val typography: NativeThemeTypographySpec,
    val systemChrome: NativeThemeSystemChromeSpec,
)

internal const val NATIVE_THEME_V1_DEFINITION_ID = "operit.native_v1"

internal val NativeThemeV1DarkColorScheme =
    darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

internal val NativeThemeV1LightColorScheme =
    lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

internal fun resolveNativeThemeV1(
    snapshot: ThemePreferenceSnapshot,
    environment: NativeThemeEnvironment,
    baseColorScheme: (darkTheme: Boolean) -> ColorScheme,
): ResolvedNativeThemeV1 {
    val darkTheme =
        if (snapshot.useSystemTheme) {
            environment.systemDarkTheme
        } else {
            snapshot.themeMode == UserPreferencesManager.THEME_MODE_DARK
        }
    var colorScheme = baseColorScheme(darkTheme)

    if (snapshot.useCustomColors) {
        snapshot.customPrimaryColor?.let { primaryArgb ->
            val primary = Color(primaryArgb)
            val secondary = snapshot.customSecondaryColor?.let(::Color) ?: colorScheme.secondary
            colorScheme =
                if (darkTheme) {
                    generateNativeThemeV1DarkColorScheme(primary, secondary, snapshot.onColorMode)
                } else {
                    generateNativeThemeV1LightColorScheme(primary, secondary, snapshot.onColorMode)
                }
        }
    }

    val backgroundEnabled = snapshot.useBackgroundImage && snapshot.backgroundImageUri != null
    val contentColorScheme =
        if (backgroundEnabled) {
            colorScheme.copy(
                surface = colorScheme.surface.copy(alpha = 1f),
                surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 1f),
                background = colorScheme.background.copy(alpha = 1f),
                surfaceContainer = colorScheme.surfaceContainer.copy(alpha = 1f),
                surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = 1f),
                surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = 1f),
                surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = 1f),
                surfaceContainerLowest = colorScheme.surfaceContainerLowest.copy(alpha = 1f),
            )
        } else {
            colorScheme
        }

    return ResolvedNativeThemeV1(
        definitionId = NATIVE_THEME_V1_DEFINITION_ID,
        environment = environment,
        source = snapshot.source,
        sourceId = snapshot.sourceId,
        darkTheme = darkTheme,
        colorScheme = colorScheme,
        contentColorScheme = contentColorScheme,
        background =
            NativeThemeBackgroundSpec(
                enabled = backgroundEnabled,
                uri = snapshot.backgroundImageUri,
                mediaType = snapshot.backgroundMediaType,
                opacity = snapshot.backgroundImageOpacity,
                blurEnabled = snapshot.useBackgroundBlur,
                blurRadius = snapshot.backgroundBlurRadius,
                videoMuted = snapshot.videoBackgroundMuted,
                videoLoop = snapshot.videoBackgroundLoop,
            ),
        typography =
            NativeThemeTypographySpec(
                useCustomFont = snapshot.useCustomFont,
                fontType = snapshot.fontType,
                systemFontName = snapshot.systemFontName,
                customFontPath = snapshot.customFontPath,
                fontScale = snapshot.fontScale,
            ),
        systemChrome =
            NativeThemeSystemChromeSpec(
                useCustomStatusBarColor = snapshot.useCustomStatusBarColor,
                customStatusBarColor = snapshot.customStatusBarColor,
                statusBarTransparent = snapshot.statusBarTransparent,
                statusBarHidden = snapshot.statusBarHidden,
            ),
    )
}

private fun generateNativeThemeV1LightColorScheme(
    primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String,
): ColorScheme {
    val primaryContainer = lightenNativeThemeV1Color(primaryColor, 0.7f)
    val secondaryContainer = lightenNativeThemeV1Color(secondaryColor, 0.7f)
    return NativeThemeV1LightColorScheme.copy(
        primary = primaryColor,
        onPrimary = nativeThemeV1OnColor(primaryColor, onColorMode),
        primaryContainer = primaryContainer,
        onPrimaryContainer = nativeThemeV1ContrastingColor(primaryContainer),
        secondary = secondaryColor,
        onSecondary = nativeThemeV1OnColor(secondaryColor, onColorMode),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = nativeThemeV1ContrastingColor(secondaryContainer),
        onSurface = Color.Black,
        onSurfaceVariant = Color.Black.copy(alpha = 0.7f),
        onBackground = Color.Black,
    )
}

private fun generateNativeThemeV1DarkColorScheme(
    primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String,
): ColorScheme {
    val adjustedPrimaryColor = lightenNativeThemeV1Color(primaryColor, 0.2f)
    val adjustedSecondaryColor = lightenNativeThemeV1Color(secondaryColor, 0.2f)
    val primaryContainer = darkenNativeThemeV1Color(primaryColor, 0.3f)
    val secondaryContainer = darkenNativeThemeV1Color(secondaryColor, 0.3f)
    return NativeThemeV1DarkColorScheme.copy(
        primary = adjustedPrimaryColor,
        onPrimary = nativeThemeV1OnColor(adjustedPrimaryColor, onColorMode),
        primaryContainer = primaryContainer,
        onPrimaryContainer = nativeThemeV1ContrastingColor(primaryContainer, forceLight = true),
        secondary = adjustedSecondaryColor,
        onSecondary = nativeThemeV1OnColor(adjustedSecondaryColor, onColorMode),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = nativeThemeV1ContrastingColor(secondaryContainer, forceLight = true),
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        onBackground = Color.White,
    )
}

private fun nativeThemeV1OnColor(color: Color, onColorMode: String): Color =
    when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> nativeThemeV1ContrastingColor(color)
    }

private fun nativeThemeV1ContrastingColor(
    backgroundColor: Color,
    forceLight: Boolean = false,
): Color {
    if (forceLight) return Color.White
    val luminance =
        0.299 * backgroundColor.red +
            0.587 * backgroundColor.green +
            0.114 * backgroundColor.blue
    return if (luminance > 0.5) Color.Black else Color.White
}

private fun lightenNativeThemeV1Color(color: Color, factor: Float): Color =
    Color(
        red = color.red + (1f - color.red) * factor,
        green = color.green + (1f - color.green) * factor,
        blue = color.blue + (1f - color.blue) * factor,
        alpha = color.alpha,
    )

private fun darkenNativeThemeV1Color(color: Color, factor: Float): Color =
    Color(
        red = color.red * (1f - factor),
        green = color.green * (1f - factor),
        blue = color.blue * (1f - factor),
        alpha = color.alpha,
    )

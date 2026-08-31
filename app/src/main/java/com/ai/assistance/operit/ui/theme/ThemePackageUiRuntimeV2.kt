package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import com.ai.assistance.operit.data.theme.packages.LinkedThemeRuntimeV2
import com.ai.assistance.operit.data.theme.packages.ResolvedThemeParametersV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentStateSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeInstanceV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialColorSchemeV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageRuntimeLinkerV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageSelectionRepositoryV2
import com.ai.assistance.operit.data.theme.packages.ThemeRuntimeRepositoryV2
import com.ai.assistance.operit.data.theme.packages.ThemeSystemFontFamilyV2
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenResolverV1
import com.ai.assistance.operit.ui.theme.scene.render.ThemeSceneAssetRepositoryV1

internal enum class ThemeComponentStateV2 {
    NORMAL,
    DISABLED,
    SELECTED,
    FOCUSED,
    ERROR,
}

@Immutable
internal data class ResolvedThemeComponentSkinV2(
    val container: Color,
    val content: Color,
    val outline: Color?,
    val outlineWidthDp: Float,
    val cornerRadiusDp: Float,
    val elevationDp: Float,
    val paddingStartDp: Float,
    val paddingTopDp: Float,
    val paddingEndDp: Float,
    val paddingBottomDp: Float,
)

/** Runtime projection used by every Operit-owned Compose root. */
@Immutable
internal data class ThemePackageUiRuntimeV2(
    val linked: LinkedThemeRuntimeV2,
    val parameters: ResolvedThemeParametersV2,
    val darkTheme: Boolean,
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val tokens: ThemeSceneTokenResolverV1,
    val assets: ThemeSceneAssetRepositoryV1,
) {
    fun componentSkin(
        component: com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2,
        state: ThemeComponentStateV2 = ThemeComponentStateV2.NORMAL,
    ): ResolvedThemeComponentSkinV2 {
        val skin = requireNotNull(linked.componentSkins[component]) {
            "Active theme has no skin for ${component.value}."
        }
        return resolveComponentState(skin, state)
    }

    private fun resolveComponentState(
        skin: ThemeComponentSkinV2,
        state: ThemeComponentStateV2,
    ): ResolvedThemeComponentSkinV2 {
        val selected =
            when (state) {
                ThemeComponentStateV2.NORMAL -> skin.normal
                ThemeComponentStateV2.DISABLED -> skin.disabled ?: skin.normal
                ThemeComponentStateV2.SELECTED -> skin.selected ?: skin.normal
                ThemeComponentStateV2.FOCUSED -> skin.focused ?: skin.normal
                ThemeComponentStateV2.ERROR -> skin.error ?: skin.normal
            }
        return selected.resolve(tokens, darkTheme)
    }
}

/**
 * 单一解析入口：主界面、悬浮窗、overlay 与离屏导出共用同一套链接结果，
 * 消除“主界面用主题包、其余表面用动态配色”的分叉。
 */
@Composable
internal fun rememberActiveThemePackageRuntimeV2(): ThemePackageUiRuntimeV2 {
    val context = LocalContext.current
    val presentation = rememberGlobalPresentation()
    val instance by remember(context) {
        ThemePackageSelectionRepositoryV2.getInstance(context).selectionFlow
    }.collectAsState(initial = ThemeInstanceV2.defaultBundled())
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        presentation.themeMode == GlobalThemeMode.DARK ||
            (presentation.themeMode == GlobalThemeMode.SYSTEM && systemDarkTheme)
    return remember(instance, darkTheme, presentation.fontScale) {
        val linked = ThemeRuntimeRepositoryV2.require(instance.reference.coordinate)
        val parameters = ThemePackageRuntimeLinkerV2.resolveParameters(instance, linked)
        createThemePackageUiRuntimeV2(
            linked = linked,
            parameters = parameters,
            darkTheme = darkTheme,
            userFontScale = presentation.fontScale,
        )
    }
}

internal fun createThemePackageUiRuntimeV2(
    linked: LinkedThemeRuntimeV2,
    parameters: ResolvedThemeParametersV2,
    darkTheme: Boolean,
    userFontScale: Float,
): ThemePackageUiRuntimeV2 {
    val tokens = ThemeSceneTokenResolverV1(linked.tokens)
    val material = linked.material
    return ThemePackageUiRuntimeV2(
        linked = linked,
        parameters = parameters,
        darkTheme = darkTheme,
        colorScheme = material.colors.toColorScheme(tokens, darkTheme),
        typography = material.typography.toTypography(userFontScale),
        shapes =
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(material.shapes.extraSmallDp.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(material.shapes.smallDp.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(material.shapes.mediumDp.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(material.shapes.largeDp.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(material.shapes.extraLargeDp.dp),
            ),
        tokens = tokens,
        assets = ThemeSceneAssetRepositoryV1(linked.assets),
    )
}

private fun ThemeComponentStateSkinV2.resolve(
    tokens: ThemeSceneTokenResolverV1,
    darkTheme: Boolean,
): ResolvedThemeComponentSkinV2 =
    ResolvedThemeComponentSkinV2(
        container = tokens.color(ThemeSceneTokenIdV1(containerToken), darkTheme),
        content = tokens.color(ThemeSceneTokenIdV1(contentToken), darkTheme),
        outline = outlineToken?.let { token -> tokens.color(ThemeSceneTokenIdV1(token), darkTheme) },
        outlineWidthDp = outlineWidthDp,
        cornerRadiusDp = cornerRadiusDp,
        elevationDp = elevationDp,
        paddingStartDp = contentPadding.startDp,
        paddingTopDp = contentPadding.topDp,
        paddingEndDp = contentPadding.endDp,
        paddingBottomDp = contentPadding.bottomDp,
    )

private fun ThemeMaterialColorSchemeV2.toColorScheme(
    tokens: ThemeSceneTokenResolverV1,
    darkTheme: Boolean,
): ColorScheme {
    fun color(tokenId: String): Color = tokens.color(ThemeSceneTokenIdV1(tokenId), darkTheme)
    val baseline = if (darkTheme) darkColorScheme() else lightColorScheme()
    return baseline.copy(
        primary = color(primary),
        onPrimary = color(onPrimary),
        primaryContainer = color(primaryContainer),
        onPrimaryContainer = color(onPrimaryContainer),
        inversePrimary = color(inversePrimary),
        secondary = color(secondary),
        onSecondary = color(onSecondary),
        secondaryContainer = color(secondaryContainer),
        onSecondaryContainer = color(onSecondaryContainer),
        tertiary = color(tertiary),
        onTertiary = color(onTertiary),
        tertiaryContainer = color(tertiaryContainer),
        onTertiaryContainer = color(onTertiaryContainer),
        background = color(background),
        onBackground = color(onBackground),
        surface = color(surface),
        onSurface = color(onSurface),
        surfaceVariant = color(surfaceVariant),
        onSurfaceVariant = color(onSurfaceVariant),
        surfaceTint = color(surfaceTint),
        inverseSurface = color(inverseSurface),
        inverseOnSurface = color(inverseOnSurface),
        error = color(error),
        onError = color(onError),
        errorContainer = color(errorContainer),
        onErrorContainer = color(onErrorContainer),
        outline = color(outline),
        outlineVariant = color(outlineVariant),
        scrim = color(scrim),
        surfaceBright = color(surfaceBright),
        surfaceDim = color(surfaceDim),
        surfaceContainerLowest = color(surfaceContainerLowest),
        surfaceContainerLow = color(surfaceContainerLow),
        surfaceContainer = color(surfaceContainer),
        surfaceContainerHigh = color(surfaceContainerHigh),
        surfaceContainerHighest = color(surfaceContainerHighest),
    )
}

private fun com.ai.assistance.operit.data.theme.packages.ThemeTypographyV2.toTypography(
    userFontScale: Float,
): Typography {
    val baseline = createCustomTypography(userFontScale)
    val resolvedFamily =
        when (this.family) {
            ThemeSystemFontFamilyV2.DEFAULT -> FontFamily.Default
            ThemeSystemFontFamilyV2.SANS_SERIF -> FontFamily.SansSerif
            ThemeSystemFontFamilyV2.SERIF -> FontFamily.Serif
            ThemeSystemFontFamilyV2.MONOSPACE -> FontFamily.Monospace
        }
    fun TextStyle.withTheme(scale: Float): TextStyle =
        copy(
            fontFamily = resolvedFamily,
            fontSize = fontSize * scale,
            lineHeight = lineHeight * scale,
            letterSpacing = letterSpacingEm.sp,
        )
    return Typography(
        displayLarge = baseline.displayLarge.withTheme(displayScale),
        displayMedium = baseline.displayMedium.withTheme(displayScale),
        displaySmall = baseline.displaySmall.withTheme(displayScale),
        headlineLarge = baseline.headlineLarge.withTheme(titleScale),
        headlineMedium = baseline.headlineMedium.withTheme(titleScale),
        headlineSmall = baseline.headlineSmall.withTheme(titleScale),
        titleLarge = baseline.titleLarge.withTheme(titleScale),
        titleMedium = baseline.titleMedium.withTheme(titleScale),
        titleSmall = baseline.titleSmall.withTheme(titleScale),
        bodyLarge = baseline.bodyLarge.withTheme(bodyScale),
        bodyMedium = baseline.bodyMedium.withTheme(bodyScale),
        bodySmall = baseline.bodySmall.withTheme(bodyScale),
        labelLarge = baseline.labelLarge.withTheme(labelScale),
        labelMedium = baseline.labelMedium.withTheme(labelScale),
        labelSmall = baseline.labelSmall.withTheme(labelScale),
    )
}

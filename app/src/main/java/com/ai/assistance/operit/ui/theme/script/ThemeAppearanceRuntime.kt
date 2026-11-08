package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.theme.script.ThemeAppearancePalette
import com.ai.assistance.operit.data.theme.script.ThemeAppearanceShape
import com.ai.assistance.operit.data.theme.script.ThemeAppearanceText
import com.ai.assistance.operit.data.theme.script.ThemeScriptLayer
import com.ai.assistance.operit.data.theme.script.ThemeScriptSurface
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import com.ai.assistance.operit.data.theme.script.actionButtonSurface
internal fun themeColor(value: String): Color = Color(android.graphics.Color.parseColor(value))

/** 显式声明的 token 覆盖原配色，其余角色沿用当前角色主题。 */
internal fun applyThemePalette(base: ColorScheme, palette: ThemeAppearancePalette, dark: Boolean): ColorScheme {
    val values = if (dark) palette.dark else palette.light
    fun value(id: String, current: Color): Color = if (id in values) themeColor(values.getValue(id)) else current
    return base.copy(
        primary = value("primary", base.primary),
        onPrimary = value("onPrimary", base.onPrimary),
        primaryContainer = value("primaryContainer", base.primaryContainer),
        onPrimaryContainer = value("onPrimaryContainer", base.onPrimaryContainer),
        inversePrimary = value("inversePrimary", base.inversePrimary),
        secondary = value("secondary", base.secondary),
        onSecondary = value("onSecondary", base.onSecondary),
        secondaryContainer = value("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = value("onSecondaryContainer", base.onSecondaryContainer),
        tertiary = value("tertiary", base.tertiary),
        onTertiary = value("onTertiary", base.onTertiary),
        tertiaryContainer = value("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = value("onTertiaryContainer", base.onTertiaryContainer),
        background = value("background", base.background),
        onBackground = value("onBackground", base.onBackground),
        surface = value("surface", base.surface),
        onSurface = value("onSurface", base.onSurface),
        surfaceVariant = value("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = value("onSurfaceVariant", base.onSurfaceVariant),
        surfaceTint = value("surfaceTint", base.surfaceTint),
        inverseSurface = value("inverseSurface", base.inverseSurface),
        inverseOnSurface = value("inverseOnSurface", base.inverseOnSurface),
        error = value("error", base.error),
        onError = value("onError", base.onError),
        errorContainer = value("errorContainer", base.errorContainer),
        onErrorContainer = value("onErrorContainer", base.onErrorContainer),
        outline = value("outline", base.outline),
        outlineVariant = value("outlineVariant", base.outlineVariant),
        scrim = value("scrim", base.scrim),
        surfaceBright = value("surfaceBright", base.surfaceBright),
        surfaceDim = value("surfaceDim", base.surfaceDim),
        surfaceContainerLowest = value("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceContainerLow = value("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainer = value("surfaceContainer", base.surfaceContainer),
        surfaceContainerHigh = value("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = value("surfaceContainerHighest", base.surfaceContainerHighest),
    )
}

/** 全局文字色同步到默认正文角色，让显式读取 onSurface 的页面保持一致。 */
internal fun applyThemeTextColors(colors: ColorScheme, text: ThemeAppearanceText): ColorScheme {
    val color = text.color?.let(::themeColor) ?: return colors
    return colors.copy(onBackground = color, onSurface = color, onSurfaceVariant = color)
}
internal fun themeColorTokens(colors: ColorScheme): Map<String, String> {
    fun hex(color: Color): String = java.lang.String.format(java.util.Locale.ROOT, "#%08X", color.toArgb())
    return mapOf(
        "primary" to hex(colors.primary),
        "onPrimary" to hex(colors.onPrimary),
        "primaryContainer" to hex(colors.primaryContainer),
        "onPrimaryContainer" to hex(colors.onPrimaryContainer),
        "inversePrimary" to hex(colors.inversePrimary),
        "secondary" to hex(colors.secondary),
        "onSecondary" to hex(colors.onSecondary),
        "secondaryContainer" to hex(colors.secondaryContainer),
        "onSecondaryContainer" to hex(colors.onSecondaryContainer),
        "tertiary" to hex(colors.tertiary),
        "onTertiary" to hex(colors.onTertiary),
        "tertiaryContainer" to hex(colors.tertiaryContainer),
        "onTertiaryContainer" to hex(colors.onTertiaryContainer),
        "background" to hex(colors.background),
        "onBackground" to hex(colors.onBackground),
        "surface" to hex(colors.surface),
        "onSurface" to hex(colors.onSurface),
        "surfaceVariant" to hex(colors.surfaceVariant),
        "onSurfaceVariant" to hex(colors.onSurfaceVariant),
        "surfaceTint" to hex(colors.surfaceTint),
        "inverseSurface" to hex(colors.inverseSurface),
        "inverseOnSurface" to hex(colors.inverseOnSurface),
        "error" to hex(colors.error),
        "onError" to hex(colors.onError),
        "errorContainer" to hex(colors.errorContainer),
        "onErrorContainer" to hex(colors.onErrorContainer),
        "outline" to hex(colors.outline),
        "outlineVariant" to hex(colors.outlineVariant),
        "scrim" to hex(colors.scrim),
        "surfaceBright" to hex(colors.surfaceBright),
        "surfaceDim" to hex(colors.surfaceDim),
        "surfaceContainerLowest" to hex(colors.surfaceContainerLowest),
        "surfaceContainerLow" to hex(colors.surfaceContainerLow),
        "surfaceContainer" to hex(colors.surfaceContainer),
        "surfaceContainerHigh" to hex(colors.surfaceContainerHigh),
        "surfaceContainerHighest" to hex(colors.surfaceContainerHighest),
    )
}

internal fun applyThemeText(style: TextStyle, text: ThemeAppearanceText, images: Map<String, ThemeScriptImage> = emptyMap()): TextStyle {
    if (text == ThemeAppearanceText()) return style
    val family = if (text.family?.startsWith("asset:") == true) {
        (images.getValue(text.family.removePrefix("asset:")) as ThemeScriptImage.Font).family
    } else when (text.family) {
        null -> style.fontFamily
        "default" -> FontFamily.Default
        "sans-serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> error("Unsupported theme font family")
    }
    return style.copy(
        fontFamily = family,
        color = text.color?.let(::themeColor) ?: style.color,
        fontSize = if (style.fontSize.isSpecified) style.fontSize * text.scale else style.fontSize,
        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * (text.scale * text.lineHeight) else style.lineHeight,
        letterSpacing = if (text.letterSpacing == 0f) style.letterSpacing else text.letterSpacing.sp,
    )
}

internal fun applyThemeTypography(base: Typography, text: ThemeAppearanceText, images: Map<String, ThemeScriptImage> = emptyMap()): Typography = base.copy(
    displayLarge = applyThemeText(base.displayLarge, text, images),
    displayMedium = applyThemeText(base.displayMedium, text, images),
    displaySmall = applyThemeText(base.displaySmall, text, images),
    headlineLarge = applyThemeText(base.headlineLarge, text, images),
    headlineMedium = applyThemeText(base.headlineMedium, text, images),
    headlineSmall = applyThemeText(base.headlineSmall, text, images),
    titleLarge = applyThemeText(base.titleLarge, text, images),
    titleMedium = applyThemeText(base.titleMedium, text, images),
    titleSmall = applyThemeText(base.titleSmall, text, images),
    bodyLarge = applyThemeText(base.bodyLarge, text, images),
    bodyMedium = applyThemeText(base.bodyMedium, text, images),
    bodySmall = applyThemeText(base.bodySmall, text, images),
    labelLarge = applyThemeText(base.labelLarge, text, images),
    labelMedium = applyThemeText(base.labelMedium, text, images),
    labelSmall = applyThemeText(base.labelSmall, text, images),
)

/** 区域正文色也进入排版，避免全局 TextStyle.color 压住区域的内容色。 */
internal fun surfaceTypography(surface: ThemeScriptSurface?): ThemeAppearanceText =
    if (surface == null) ThemeAppearanceText() else surface.textStyle.copy(color = surface.contentColor ?: surface.textStyle.color)
@Composable
internal fun themeSurface(id: String): ThemeScriptSurface? {
    val appearance = (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.appearance ?: return null
    val surface = if (id in ThemeScriptContract.actionButtonIds) appearance.actionButtonSurface(id) else appearance.surfaces[id]
    return surface?.takeUnless { it == ThemeScriptSurface() }?.let { resolvedThemeSurface(id, it) }
}

@Composable
fun themeScriptShape(id: String, original: Shape): Shape {
    val fragment = LocalThemeSurfaceFragments.current.getOrDefault(id, ThemeSurfaceFragment.Whole)
    val shape = themeSurface(id)?.shape
    return fragment.shape(if (shape == null) original else appearanceShape(shape))
}

/** 玻璃效果读取角点信息，解析结果需要保留原有角点形状的静态类型。 */
@Composable
fun themeScriptShape(id: String, original: CornerBasedShape): CornerBasedShape {
    val fragment = LocalThemeSurfaceFragments.current.getOrDefault(id, ThemeSurfaceFragment.Whole)
    val shape = themeSurface(id)?.shape
    return fragment.shape(if (shape == null) original else appearanceShape(shape))
}

internal fun appearanceShape(shape: ThemeAppearanceShape): CornerBasedShape = when (shape.type) {
    "rounded" -> RoundedCornerShape(shape.radius.dp)
    "cut" -> CutCornerShape(shape.radius.dp)
    "circle" -> CircleShape
    else -> error("Unsupported theme shape")
}

@Composable
fun themeScriptFill(id: String, original: Color, backgroundOpacity: Float? = null): Color {
    val surface = themeSurface(id)
    val configured = surface?.fillColor?.let(::themeColor)
    val color = configured ?: original
    val baseAlpha = if (configured == null) backgroundOpacity ?: original.alpha else configured.alpha * (backgroundOpacity ?: 1f)
    val opacity = if (id in LocalThemeHandledOpacity.current) 1f else surface?.opacity ?: 1f
    return color.copy(alpha = baseAlpha * opacity)
}

@Composable
fun themeScriptContentColor(id: String, original: Color): Color {
    val surface = themeSurface(id)
    val global = (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.appearance?.typography?.color
    // 状态或区域正文色优先，随后是区域排版色、全局排版色和业务默认色。
    val color = surface?.contentColor ?: surface?.textStyle?.color ?: global
    return if (color == null) original else themeColor(color)
}

@Composable
fun themeScriptAvatarSize(id: String, original: Dp): Dp {
    val size = themeSurface(id)?.size
    return if (size == null) original else size.dp
}

@Composable
fun themeScriptMessageFraction(id: String): Float = themeSurface(id)?.messageWidth ?: 1f

@Composable
fun themeScriptTypography(id: String, original: Typography): Typography =
    applyThemeTypography(original, surfaceTypography(themeSurface(id)), (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.images.orEmpty())

@Composable
fun themeScriptMessageWidth(id: String, original: Dp): Dp = original * (themeSurface(id)?.messageWidth ?: 1f)

@Composable
internal fun themeScriptBorder(id: String, original: BorderStroke?): BorderStroke? {
    return appearanceBorder(themeSurface(id), original)
}

/** 继承边框保留原生画刷；正宽度可以调整既有边框，显式颜色与零宽度共同决定自定义描边。 */
internal fun appearanceBorder(style: ThemeScriptSurface?, original: BorderStroke?, opacity: Float = 1f): BorderStroke? {
    if (style == null) return original
    val color = style.strokeColor
    if (color == null) {
        return if (style.strokeWidth > 0f && original != null) BorderStroke(style.strokeWidth.dp, original.brush) else original
    }
    if (style.strokeWidth == 0f) return null
    val resolved = themeColor(color)
    return BorderStroke(style.strokeWidth.dp, resolved.copy(alpha = resolved.alpha * opacity))
}

@Composable
fun themeScriptTextStyle(id: String, original: TextStyle): TextStyle =
    applyThemeText(original, surfaceTypography(themeSurface(id)), (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.images.orEmpty())

/** 素材只替换图像，外层原生按钮继续持有点击、长按和无障碍语义。 */
@Composable
fun ThemeScriptIcon(
    id: String,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val buttonColor = if (id in ThemeScriptContract.actionButtonIds) themeSurface(id)?.contentColor else null
    val resolvedTint = buttonColor?.let(::themeColor) ?: tint
    val state = LocalThemeScriptState.current
    val icon = (state as? ThemeScriptState.Ready)?.appearance?.icons?.get(id)
    if (icon == null) {
        Icon(imageVector, contentDescription, modifier, resolvedTint)
    } else {
        val images = (state as ThemeScriptState.Ready).images
        Canvas(modifier.requiredSize(icon.size.dp).then(
            if (contentDescription == null) Modifier else Modifier.semantics { this.contentDescription = contentDescription }
        )) {
            drawThemeLayer(ThemeScriptLayer("image", asset = icon.asset, fit = "fit", opacity = icon.opacity), images, 1f, tintColor = icon.tintColor?.let(::themeColor) ?: if (icon.useContentTint) resolvedTint else null)
        }
    }
}

package com.ai.assistance.operit.ui.theme.style.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderAlignmentV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderSideV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderStackSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBrushV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleColorSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleCornerRadiusUnitV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleCornerRadiusV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleIconContainerSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleMaterialSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleShapeSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleFontFamilyV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeSystemFontFamilyV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeTextStyleSpecV1
import kotlin.math.roundToInt

@Composable
internal fun NativeThemeStyledStatPreviewV1(
    plan: NativeThemeStatStylePlanV1,
    darkTheme: Boolean,
    label: String,
    value: String,
    leading: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.alpha(plan.opacity),
        shape = plan.shape.toComposeShape(),
        color = plan.surfaceColor,
        border = plan.border?.toComposeBorder(darkTheme),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = plan.contentPaddingDp.dp,
                    vertical = (plan.contentPaddingDp * 0.75f).dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeThemeStatPreviewIconV1(
                leadingColor = plan.leadingColor,
                iconContainer = plan.leadingIconContainer,
                darkTheme = darkTheme,
                leading = leading,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = plan.value.toComposeTextStyle(MaterialTheme.typography.titleMedium),
                )
                Text(
                    text = label,
                    style = plan.label.toComposeTextStyle(MaterialTheme.typography.bodySmall),
                )
            }
        }
    }
}

private fun NativeThemeStatTextStylePlanV1.toComposeTextStyle(base: TextStyle): TextStyle =
    spec.toComposeTextStyle(base = base, color = color)

private fun NativeThemeTextStyleSpecV1.toComposeTextStyle(
    base: TextStyle,
    color: Color,
): TextStyle =
    base.copy(
        color = color,
        fontFamily = family.toComposeFontFamily(base.fontFamily),
        fontWeight = FontWeight(fontWeight),
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        letterSpacing = letterSpacingEm.em,
    )

private fun NativeThemeStyleFontFamilyV1.toComposeFontFamily(base: FontFamily?): FontFamily? =
    when (this) {
        is NativeThemeStyleFontFamilyV1.System ->
            when (family) {
                NativeThemeSystemFontFamilyV1.SYSTEM -> base
                NativeThemeSystemFontFamilyV1.SANS_SERIF -> FontFamily.SansSerif
                NativeThemeSystemFontFamilyV1.SERIF -> FontFamily.Serif
                NativeThemeSystemFontFamilyV1.MONOSPACE -> FontFamily.Monospace
                NativeThemeSystemFontFamilyV1.CURSIVE -> FontFamily.Cursive
            }

        is NativeThemeStyleFontFamilyV1.Asset ->
            error("The Stat renderer does not support font asset ${resourceId.value}.")
    }

@Composable
private fun NativeThemeStatPreviewIconV1(
    leadingColor: Color,
    iconContainer: NativeThemeStyleIconContainerSpecV1.Container?,
    darkTheme: Boolean,
    leading: @Composable (Modifier) -> Unit,
) {
    if (iconContainer == null) {
        CompositionLocalProvider(LocalContentColor provides leadingColor) {
            leading(Modifier.size(20.dp))
        }
        return
    }

    Surface(
        modifier = Modifier.size(iconContainer.containerSizeDp.dp),
        shape = iconContainer.shape.toComposeShape(),
        color = iconContainer.material.toComposeSurfaceColor(darkTheme),
        border = iconContainer.border?.toComposeBorder(darkTheme),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompositionLocalProvider(
                LocalContentColor provides iconContainer.contentColor.toComposeColorV1(darkTheme),
            ) {
                leading(Modifier.size(iconContainer.iconSizeDp.dp))
            }
        }
    }
}

private fun NativeThemeStyleShapeSpecV1.toComposeShape(): Shape =
    when (this) {
        NativeThemeStyleShapeSpecV1.Rectangle -> RectangleShape
        NativeThemeStyleShapeSpecV1.Capsule -> RoundedCornerShape(percent = 50)
        is NativeThemeStyleShapeSpecV1.RoundedCorners ->
            RoundedCornerShape(
                topStart = topStart.toCornerSize(),
                topEnd = topEnd.toCornerSize(),
                bottomEnd = bottomEnd.toCornerSize(),
                bottomStart = bottomStart.toCornerSize(),
            )
    }

private fun NativeThemeStyleCornerRadiusV1.toCornerSize(): CornerSize =
    when (unit) {
        NativeThemeStyleCornerRadiusUnitV1.DP -> CornerSize(value.dp)
        NativeThemeStyleCornerRadiusUnitV1.PERCENT -> CornerSize(value.roundToInt())
    }

private fun NativeThemeStyleBorderStackSpecV1.toComposeBorder(
    darkTheme: Boolean,
): BorderStroke {
    require(layers.size == 1) { "The Stat preview supports one border layer." }
    val layer = layers.single()
    require(layer.alignment == NativeThemeStyleBorderAlignmentV1.INSIDE) {
        "The Stat preview supports inside borders."
    }
    require(layer.offsetDp == 0f && layer.dash == null) {
        "The Stat preview does not support border offsets or dash patterns."
    }
    require(layer.sides == NativeThemeStyleBorderSideV1.entries.toSet()) {
        "The Stat preview requires a border on every side."
    }
    val brush = layer.brush
    require(brush is NativeThemeStyleBrushV1.Solid) {
        "The Stat preview supports solid border brushes."
    }
    return BorderStroke(
        width = layer.widthDp.dp,
        color = brush.color.toComposeColorV1(darkTheme).copy(alpha = layer.opacity),
    )
}

private fun NativeThemeStyleMaterialSpecV1.toComposeSurfaceColor(
    darkTheme: Boolean,
): Color =
    when (this) {
        is NativeThemeStyleMaterialSpecV1.Solid -> color.toComposeColorV1(darkTheme)
        is NativeThemeStyleMaterialSpecV1.Translucent -> tint.toComposeColorV1(darkTheme).copy(alpha = opacity)
        else -> error("The Stat preview does not support ${this::class.simpleName} material.")
    }

internal fun NativeThemeStyleColorSpecV1.toComposeColorV1(darkTheme: Boolean): Color =
    resolveColor(darkTheme)

internal fun NativeThemeStatStylePlanV1.borderWidth(): Float? =
    border?.layers?.singleOrNull()?.widthDp

internal fun NativeThemeStatStylePlanV1.cornerRadiusDp(): Float =
    when (val resolvedShape = shape) {
        NativeThemeStyleShapeSpecV1.Rectangle -> 0f
        NativeThemeStyleShapeSpecV1.Capsule -> 20f
        is NativeThemeStyleShapeSpecV1.RoundedCorners ->
            when (resolvedShape.topStart.unit) {
                NativeThemeStyleCornerRadiusUnitV1.DP -> resolvedShape.topStart.value
                NativeThemeStyleCornerRadiusUnitV1.PERCENT -> 20f
            }
    }

internal fun NativeThemeStatStylePlanV1.borderColor(darkTheme: Boolean): Color? {
    val brush = border?.layers?.singleOrNull()?.brush ?: return null
    return when (brush) {
        is NativeThemeStyleBrushV1.Solid -> brush.color.toComposeColorV1(darkTheme)
        else -> error("The Stat preview supports solid border brushes.")
    }
}

internal fun NativeThemeStatStylePlanV1.iconContainerColor(darkTheme: Boolean): Color? {
    val material = leadingIconContainer?.material ?: return null
    return material.toComposeSurfaceColor(darkTheme)
}

package com.ai.assistance.operit.ui.theme

import android.os.Build
import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

val LocalLiquidGlassBackdrop = compositionLocalOf<Backdrop?> { null }

private const val LiquidGlassMinApi = Build.VERSION_CODES.TIRAMISU

fun isLiquidGlassSupported(): Boolean = Build.VERSION.SDK_INT >= LiquidGlassMinApi

@Composable
fun Modifier.liquidGlass(
    enabled: Boolean,
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    containerColor: Color,
    enableLens: Boolean = false,
    backgroundOpacity: Float? = null,
    region: GlassRegion = GlassRegion.GLOBAL,
): Modifier {
    if (!enabled || backgroundOpacity == 0f) {
        return this
    }

    val appearance = rememberGlassAppearance(region)
    val customParameters = region.isCustom(LocalThemePreferenceSnapshot.current.values)
    // 独立染色强度与区域背景透明度共同生效；跟随全局时保持既有透明度语义。
    val tintOpacity = if (customParameters) {
        appearance.tintOpacity * (backgroundOpacity?.coerceIn(0f, 1f) ?: 1f)
    } else backgroundOpacity?.coerceIn(0f, 1f) ?: appearance.tintOpacity
    val backdrop = if (isLiquidGlassSupported()) LocalLiquidGlassBackdrop.current else null
    val isLightGlass = containerColor.luminance() >= 0.5f
    if (backdrop == null) {
        val borderColor = Color.White.copy(alpha = appearance.highlightAlpha)

        return this
            .shadow(
                elevation = appearance.shadowRadius.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isLightGlass) 0.10f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (isLightGlass) 0.10f else 0.18f),
            )
            .then(
                if (appearance.edgeWidth > 0f && appearance.highlightAlpha > 0f) {
                    Modifier.border(appearance.edgeWidth.dp, borderColor, shape)
                } else Modifier
            )
            .background(color = containerColor.copy(alpha = tintOpacity), shape = shape)
    }
    val surfaceTint =
        containerColor.copy(alpha = tintOpacity)
    val edgeWidth = appearance.edgeWidth.dp
    val shadowRadius = appearance.shadowRadius.dp
    val shadowColor =
        if (isLightGlass) {
            Color.Black.copy(alpha = 0.10f)
        } else {
            Color.Black.copy(alpha = 0.18f)
        }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(appearance.blurRadius.dp.toPx())
            if (enableLens) {
                lens(
                    refractionHeight = 12.dp.toPx(),
                    refractionAmount = 18.dp.toPx(),
                    chromaticAberration = true,
                )
            }
        },
        highlight = {
            if (appearance.edgeWidth == 0f || appearance.highlightAlpha == 0f) null else Highlight(
                width = edgeWidth,
                blurRadius = edgeWidth * 2.4f,
                alpha = appearance.highlightAlpha,
            )
        },
        shadow = {
            if (appearance.shadowRadius == 0f) null else Shadow(
                radius = shadowRadius,
                color = shadowColor,
            )
        },
        onDrawSurface = {
            drawRect(surfaceTint)
        },
    )
}

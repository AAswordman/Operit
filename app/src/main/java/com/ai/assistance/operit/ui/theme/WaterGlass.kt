package com.ai.assistance.operit.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * WaterGlass effect stub.
 *
 * The original glass-morphism effect has been removed in favor of a flat,
 * minimal design. This file retains the API surface (CompositionLocal,
 * support check, and Modifier extension) for forward compatibility, but
 * the extension is now a no-op.
 */
val LocalWaterGlassState = compositionLocalOf<Any?> { null }

private const val WaterGlassMinApi = Build.VERSION_CODES.TIRAMISU

fun isWaterGlassSupported(): Boolean = Build.VERSION.SDK_INT >= WaterGlassMinApi

@Composable
fun Modifier.waterGlass(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(0.dp),
    containerColor: Color,
    shadowElevation: Dp = 14.dp,
    borderWidth: Dp = 1.dp,
    overlayAlphaBoost: Float = 0f,
): Modifier = this

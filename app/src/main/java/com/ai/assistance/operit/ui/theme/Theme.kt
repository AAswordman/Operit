package com.ai.assistance.operit.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val presentation = rememberGlobalPresentation()
    val systemDarkTheme = isSystemInDarkTheme()
    val resolvedTheme =
        resolveGlobalThemeV1(
            presentation = presentation,
            environment =
                NativeThemeEnvironment(
                    hostSurface = NativeThemeHostSurface.MAIN,
                    systemDarkTheme = systemDarkTheme,
                ),
            baseColorScheme = { darkTheme ->
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        if (darkTheme) dynamicDarkColorScheme(context)
                        else dynamicLightColorScheme(context)
                    }
                    darkTheme -> NativeThemeV1DarkColorScheme
                    else -> NativeThemeV1LightColorScheme
                }
            },
        )
    val darkTheme = resolvedTheme.darkTheme

    val customTypography =
        remember(resolvedTheme.fontScale) {
            createCustomTypography(fontScale = resolvedTheme.fontScale)
        }

    NativeThemeMainWindowChromeHostAdapter(resolvedTheme)

    Box(modifier = Modifier.fillMaxSize()) {
        val liquidGlassBackdrop = rememberLayerBackdrop()
        val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null

        CompositionLocalProvider(
            LocalGlobalPresentation provides presentation,
            LocalResolvedGlobalTheme provides resolvedTheme,
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop)
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(if (darkTheme) Color.Black else Color.White)
                            .then(
                                if (waterGlassState != null) {
                                    Modifier.liquefiable(waterGlassState)
                                } else {
                                    Modifier
                                },
                            )
                )
            }

            MaterialTheme(
                colorScheme = resolvedTheme.colorScheme,
                typography = customTypography,
                content = content,
            )
        }
    }
}

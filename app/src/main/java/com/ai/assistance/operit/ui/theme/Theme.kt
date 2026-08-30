package com.ai.assistance.operit.ui.theme

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.theme.packages.ActiveGlobalThemeParameterResolverV1
import com.ai.assistance.operit.data.theme.packages.ActiveGlobalThemeParametersV1
import com.ai.assistance.operit.data.theme.packages.ThemeInstanceV1
import com.ai.assistance.operit.data.theme.packages.ThemePackageBuiltInReferenceV1
import com.ai.assistance.operit.data.theme.packages.ThemePackageInstallerV1
import com.ai.assistance.operit.data.theme.packages.ThemePackageReferenceV1
import com.ai.assistance.operit.data.theme.packages.ThemePackageSelectionRepository
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

@Composable
private fun rememberActiveThemeInstance(): ThemeInstanceV1 {
    val context = LocalContext.current
    val instance by remember(context) {
        ThemePackageSelectionRepository.getInstance(context).selectionFlow
    }.collectAsState(initial = ThemeInstanceV1.defaultBuiltIn())
    return instance
}

@Composable
private fun rememberActiveThemeParameters(): ActiveGlobalThemeParametersV1 {
    val context = LocalContext.current
    val instance = rememberActiveThemeInstance()
    return remember(instance, context) {
        ActiveGlobalThemeParameterResolverV1.resolve(instance) { reference ->
            when (reference) {
                is ThemePackageReferenceV1.BuiltIn -> ThemePackageBuiltInReferenceV1.manifest()
                is ThemePackageReferenceV1.Installed ->
                    ThemePackageInstallerV1.getInstance(context)
                        .find(reference.coordinate)
                        ?.manifest
            }
        }
    }
}

@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val presentation = rememberGlobalPresentation()
    val themeParameters = rememberActiveThemeParameters()
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
            primaryColor = themeParameters.primaryColorArgb?.let { argb -> Color(argb.toInt()) },
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
            LocalActiveGlobalThemeParameters provides themeParameters,
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
                themeParameters.backgroundImageUri?.let { backgroundUri ->
                    Image(
                        painter =
                            rememberAsyncImagePainter(
                                model = android.net.Uri.parse(backgroundUri),
                            ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            MaterialTheme(
                colorScheme = resolvedTheme.colorScheme,
                typography = customTypography,
                content = content,
            )
        }
    }
}

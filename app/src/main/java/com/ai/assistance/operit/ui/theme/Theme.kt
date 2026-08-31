package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/**
 * V2 全应用主题入口：颜色、排版、形状、场景与组件皮肤全部来自激活主题包的
 * 链接运行时。此处不再存在动态配色基线或主色覆盖路径——那会让主题包
 * 失去对顶栏/系统栏视觉的所有权。
 */
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val presentation = rememberGlobalPresentation()
    val packageRuntime = rememberActiveThemePackageRuntimeV2()
    val resolvedTheme =
        remember(packageRuntime, presentation) {
            ResolvedGlobalTheme(
                environment =
                    NativeThemeEnvironment(
                        hostSurface = NativeThemeHostSurface.MAIN,
                        systemDarkTheme = packageRuntime.darkTheme,
                    ),
                darkTheme = packageRuntime.darkTheme,
                colorScheme = packageRuntime.colorScheme,
                fontScale = presentation.fontScale,
            )
        }

    NativeThemeMainWindowChromeHostAdapter(packageRuntime)

    val liquidGlassBackdrop = rememberLayerBackdrop()
    val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null
    CompositionLocalProvider(
        LocalGlobalPresentation provides presentation,
        LocalResolvedGlobalTheme provides resolvedTheme,
        LocalThemePackageUiRuntimeV2 provides packageRuntime,
        LocalResolvedThemeParametersV2 provides packageRuntime.parameters,
        LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
        LocalWaterGlassState provides waterGlassState,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(liquidGlassBackdrop)
                    .then(
                        if (waterGlassState != null) {
                            Modifier.liquefiable(waterGlassState)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            MaterialTheme(
                colorScheme = packageRuntime.colorScheme,
                typography = packageRuntime.typography,
                shapes = packageRuntime.shapes,
                content = content,
            )
        }
    }
}

/** 供 detached 宿主复用的深浅判定；与主界面一致的主题模式解析。 */
internal fun resolveThemeDarkMode(
    presentation: GlobalPresentationSnapshot,
    systemDarkTheme: Boolean,
): Boolean =
    presentation.themeMode == GlobalThemeMode.DARK ||
        (presentation.themeMode == GlobalThemeMode.SYSTEM && systemDarkTheme)

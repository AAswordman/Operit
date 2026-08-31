package com.ai.assistance.operit.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.theme.packages.ThemePackageRuntimeLinkerV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageSelectionRepositoryV2
import com.ai.assistance.operit.data.theme.packages.ThemeRuntimeRepositoryV2
import kotlinx.coroutines.flow.first

/**
 * 非组合环境（离屏导出、WebChat 桥）构建激活主题包的运行时投影。
 * 与主界面共享同一链接结果，保证导出图与页面视觉一致。
 */
internal suspend fun buildActiveThemePackageRuntimeV2(
    context: Context,
    presentation: GlobalPresentationSnapshot,
    systemDarkTheme: Boolean,
): ThemePackageUiRuntimeV2 {
    val instance =
        ThemePackageSelectionRepositoryV2.getInstance(context).selectionFlow.first()
    val linked = ThemeRuntimeRepositoryV2.require(instance.reference.coordinate)
    val parameters = ThemePackageRuntimeLinkerV2.resolveParameters(instance, linked)
    return createThemePackageUiRuntimeV2(
        linked = linked,
        parameters = parameters,
        darkTheme = resolveThemeDarkMode(presentation, systemDarkTheme),
        userFontScale = presentation.fontScale,
    )
}

// Glance 桌面小组件属于固定界面：不跟随主题包，仅使用系统动态配色基线。
internal fun resolveGlobalThemeForDetachedComposeHost(
    presentation: GlobalPresentationSnapshot,
    hostSurface: NativeThemeHostSurface,
    systemDarkTheme: Boolean,
    lightColorScheme: ColorScheme,
    darkColorScheme: ColorScheme,
): ResolvedGlobalTheme =
    resolveGlobalThemeV1(
        presentation = presentation,
        environment =
            NativeThemeEnvironment(
                hostSurface = hostSurface,
                systemDarkTheme = systemDarkTheme,
            ),
        baseColorScheme = { darkTheme -> if (darkTheme) darkColorScheme else lightColorScheme },
    )

internal fun resolveNativeThemeDetachedBaseColorSchemes(
    context: Context,
): Pair<ColorScheme, ColorScheme> {
    val lightColorScheme =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            NativeThemeV1LightColorScheme
        }
    val darkColorScheme =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            NativeThemeV1DarkColorScheme
        }
    return lightColorScheme to darkColorScheme
}

/** 离屏导出宿主：使用与主界面相同的主题包运行时。 */
@Composable
internal fun NativeThemeOffscreenHost(
    presentation: GlobalPresentationSnapshot,
    packageRuntime: ThemePackageUiRuntimeV2,
    content: @Composable () -> Unit,
) = NativeThemeResolvedComposeHost(
    presentation = presentation,
    packageRuntime = packageRuntime,
    content = content,
)

/** 悬浮窗宿主：chat.floating 是日常界面，必须跟随激活主题包。 */
@Composable
internal fun NativeThemeFloatingHost(content: @Composable () -> Unit) {
    NativeThemeActiveDetachedComposeHost(content = content)
}

/** 应用内 overlay 宿主（权限提示等）：跟随激活主题包。 */
@Composable
internal fun NativeThemeOverlayHost(content: @Composable () -> Unit) {
    NativeThemeActiveDetachedComposeHost(content = content)
}

@Composable
private fun NativeThemeActiveDetachedComposeHost(
    content: @Composable () -> Unit,
) {
    val presentation = rememberGlobalPresentation()
    val packageRuntime = rememberActiveThemePackageRuntimeV2()

    NativeThemeResolvedComposeHost(
        presentation = presentation,
        packageRuntime = packageRuntime,
        content = content,
    )
}

@Composable
private fun NativeThemeResolvedComposeHost(
    presentation: GlobalPresentationSnapshot,
    packageRuntime: ThemePackageUiRuntimeV2,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlobalPresentation provides presentation,
        LocalThemePackageUiRuntimeV2 provides packageRuntime,
        LocalResolvedThemeParametersV2 provides packageRuntime.parameters,
    ) {
        MaterialTheme(
            colorScheme = packageRuntime.colorScheme,
            typography = packageRuntime.typography,
            shapes = packageRuntime.shapes,
            content = content,
        )
    }
}

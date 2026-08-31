package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
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

internal data class ResolvedGlobalTheme(
    val environment: NativeThemeEnvironment,
    val darkTheme: Boolean,
    val colorScheme: ColorScheme,
    val fontScale: Float,
)

internal val NativeThemeV1DarkColorScheme =
    darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

internal val NativeThemeV1LightColorScheme =
    lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

internal const val NATIVE_THEME_V1_DEFINITION_ID = "operit.native_v1"

/**
 * 固定界面（Glance 桌面小组件）专用基线：仅解析深浅模式与系统动态配色。
 * 日常界面一律走 [ThemePackageUiRuntimeV2]；此函数不再提供任何主题包主色
 * 派生路径——旧实现允许单个主色参数改写 primary 系角色，正是主题失去
 * 顶栏视觉所有权的入口。
 */
internal fun resolveGlobalThemeV1(
    presentation: GlobalPresentationSnapshot,
    environment: NativeThemeEnvironment,
    baseColorScheme: (darkTheme: Boolean) -> ColorScheme,
): ResolvedGlobalTheme {
    val darkTheme =
        presentation.themeMode == GlobalThemeMode.DARK ||
            (presentation.themeMode == GlobalThemeMode.SYSTEM && environment.systemDarkTheme)
    return ResolvedGlobalTheme(
        environment = environment,
        darkTheme = darkTheme,
        colorScheme = baseColorScheme(darkTheme),
        fontScale = presentation.fontScale,
    )
}

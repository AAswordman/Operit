package com.ai.assistance.operit.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2

internal data class NativeThemeMainWindowChromeState(
    val statusBarColor: Int,
    val lightStatusBarIcons: Boolean,
    val navigationBarColor: Int,
    val navigationBarContrastEnforced: Boolean,
    val lightNavigationBarIcons: Boolean,
)

/**
 * V2：系统栏颜色来自主题包皮肤，而非 Material primary。
 * 旧实现把 primary 同时刷进 TopAppBar 与状态栏，是“整条青色顶栏”缺陷的直接来源。
 */
internal fun resolveNativeThemeMainWindowChromeState(
    runtime: ThemePackageUiRuntimeV2,
): NativeThemeMainWindowChromeState {
    val statusBarColor = runtime.componentSkin(ThemeComponentCatalogV2.APP_BAR).container
    val navigationBarColor = runtime.colorScheme.background
    return NativeThemeMainWindowChromeState(
        statusBarColor = statusBarColor.toArgb(),
        lightStatusBarIcons = isNativeThemeColorLight(statusBarColor),
        navigationBarColor = navigationBarColor.toArgb(),
        navigationBarContrastEnforced = true,
        lightNavigationBarIcons = !isNativeThemeColorLight(navigationBarColor),
    )
}

@Composable
internal fun NativeThemeMainWindowChromeHostAdapter(runtime: ThemePackageUiRuntimeV2) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val state = resolveNativeThemeMainWindowChromeState(runtime)
            insetsController?.show(WindowInsetsCompat.Type.statusBars())
            window.statusBarColor = state.statusBarColor
            insetsController?.isAppearanceLightStatusBars = state.lightStatusBarIcons

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = state.navigationBarContrastEnforced
            }
            window.navigationBarColor = state.navigationBarColor
            insetsController?.isAppearanceLightNavigationBars = state.lightNavigationBarIcons
        }
    }
}

private fun isNativeThemeColorLight(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}

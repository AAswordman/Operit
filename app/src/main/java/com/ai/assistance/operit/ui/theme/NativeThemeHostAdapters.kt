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

internal data class NativeThemeMainWindowChromeState(
    val statusBarColor: Int,
    val lightStatusBarIcons: Boolean,
    val navigationBarColor: Int,
    val navigationBarContrastEnforced: Boolean,
    val lightNavigationBarIcons: Boolean,
)

internal fun resolveNativeThemeMainWindowChromeState(
    resolvedTheme: ResolvedGlobalTheme,
): NativeThemeMainWindowChromeState {
    val navigationBarColor = resolvedTheme.colorScheme.background.toArgb()
    val statusBarColor = resolvedTheme.colorScheme.primary.toArgb()
    return NativeThemeMainWindowChromeState(
        statusBarColor = statusBarColor,
        lightStatusBarIcons = isNativeThemeColorLight(Color(statusBarColor)),
        navigationBarColor = navigationBarColor,
        navigationBarContrastEnforced = true,
        lightNavigationBarIcons = !isNativeThemeColorLight(resolvedTheme.colorScheme.background),
    )
}

@Composable
internal fun NativeThemeMainWindowChromeHostAdapter(resolvedTheme: ResolvedGlobalTheme) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val state = resolveNativeThemeMainWindowChromeState(resolvedTheme)
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

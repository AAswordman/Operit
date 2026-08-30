package com.ai.assistance.operit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.GlobalPresentationManager
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.theme.packages.ActiveGlobalThemeParametersV1
import com.ai.assistance.operit.ui.theme.scene.ActiveThemeSceneRuntimeV1

val LocalGlobalPresentation =
    compositionLocalOf<GlobalPresentationSnapshot> {
        error("LocalGlobalPresentation is not provided.")
    }

internal val LocalResolvedGlobalTheme =
    compositionLocalOf<ResolvedGlobalTheme> {
        error("LocalResolvedGlobalTheme is not provided.")
    }

internal val LocalActiveGlobalThemeParameters =
    compositionLocalOf<ActiveGlobalThemeParametersV1> {
        error("LocalActiveGlobalThemeParameters is not provided.")
    }

internal val LocalActiveThemeSceneRuntime =
    compositionLocalOf<ActiveThemeSceneRuntimeV1> {
        error("LocalActiveThemeSceneRuntime is not provided.")
    }

@Composable
fun rememberGlobalPresentation(): GlobalPresentationSnapshot {
    val context = LocalContext.current
    val manager = remember(context) { GlobalPresentationManager.getInstance(context) }
    val presentation by manager.snapshotFlow.collectAsState(
        initial = GlobalPresentationSnapshot.default(),
    )
    return presentation
}

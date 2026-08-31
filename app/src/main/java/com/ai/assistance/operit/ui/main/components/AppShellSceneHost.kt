package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceCatalogV2
import com.ai.assistance.operit.ui.theme.LocalThemePackageUiRuntimeV2
import com.ai.assistance.operit.ui.theme.sceneFor
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneSlotIdV1
import com.ai.assistance.operit.ui.theme.scene.render.ThemeSceneV1

/**
 * app.shell 场景宿主：顶栏导航/标题/动作与路由内容由宿主提供并保留全部交互，
 * 场景树负责壳层背景与布局。状态栏 inset 属于宿主职责，因此包裹在各顶栏槽位上，
 * 主题无需感知系统栏高度即可让壳层背景延伸到状态栏后方。
 */
@Composable
internal fun AppShellSceneHost(
    appBarNavigation: @Composable () -> Unit,
    appBarTitle: @Composable () -> Unit,
    appBarActions: @Composable () -> Unit,
    routeContent: @Composable () -> Unit,
    announcement: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val runtime = LocalThemePackageUiRuntimeV2.current
    val movableNavigation = rememberMovableShellSlot { Box(Modifier.statusBarsPadding()) { appBarNavigation() } }
    val movableTitle = rememberMovableShellSlot { Box(Modifier.statusBarsPadding()) { appBarTitle() } }
    val movableActions = rememberMovableShellSlot { Box(Modifier.statusBarsPadding()) { appBarActions() } }
    val movableRoute = rememberMovableShellSlot(routeContent)
    val movableAnnouncement = rememberMovableShellSlot(announcement)

    ThemeSceneV1(
        stage = runtime.sceneFor(ThemeSurfaceCatalogV2.APP_SHELL),
        tokens = runtime.tokens,
        assets = runtime.assets,
        hostSlots =
            mapOf(
                ThemeSceneSlotIdV1("app_bar.navigation") to movableNavigation,
                ThemeSceneSlotIdV1("app_bar.title") to movableTitle,
                ThemeSceneSlotIdV1("app_bar.actions") to movableActions,
                ThemeSceneSlotIdV1("route.content") to movableRoute,
                ThemeSceneSlotIdV1("announcement") to movableAnnouncement,
            ),
        textResolver = { key ->
            error("Active shell theme has no text resource for ${key.value}.")
        },
        darkTheme = runtime.darkTheme,
        modifier = modifier,
    )
}

@Composable
private fun rememberMovableShellSlot(
    content: @Composable () -> Unit,
): @Composable () -> Unit {
    val latestContent = rememberUpdatedState(content)
    return remember {
        movableContentOf {
            latestContent.value()
        }
    }
}

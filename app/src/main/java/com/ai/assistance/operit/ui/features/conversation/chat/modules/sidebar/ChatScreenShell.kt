package com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.ui.theme.script.*
import kotlinx.coroutines.launch

/** 嵌入式聊天自行持有当前侧栏与控件行，不再进入旧顶栏和旧历史面板分支。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreenShell(
    embedded: Boolean,
    viewModel: ChatViewModel,
    sidebarState: ChatSidebarState,
    onOpenCharacterSettings: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!embedded) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val appearance = rememberNavigationDrawerAppearance()
    val runChatAction: (() -> Unit) -> Unit = { action -> scope.launch { drawer.close(); action() } }
    CompositionLocalProvider(
        LocalMainChatViewModel provides viewModel,
        LocalMainChatSidebarState provides sidebarState,
        LocalOpenMainChatSidebar provides { scope.launch { drawer.open() } },
        LocalRunMainChatAction provides runChatAction,
    ) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxHeight(),
                    drawerShape = themeScriptShape("navigation", RoundedCornerShape(0.dp)),
                    drawerContainerColor = themeScriptFill("navigation", appearance.containerColor),
                    drawerContentColor = themeScriptContentColor("navigation", appearance.itemColor),
                ) {
                    ThemeScriptSurface("navigation", drawFill = false) {
                        ChatSidebarContent(
                            brandName = stringResource(R.string.app_name),
                            appearance = appearance,
                            pluginEntries = emptyList(),
                            selectedItem = null,
                            selectedRouteId = "embedded-chat",
                            permissionBadgeText = "",
                            onNavItemClick = {},
                            onNavigationEntryClick = {},
                            onOpenCharacterSettings = onOpenCharacterSettings,
                            onCloseSidebar = { scope.launch { drawer.close() } },
                            onRunChatAction = runChatAction,
                            onCreateChat = runChatAction,
                            onSelectCharacter = viewModel::switchActiveCharacterTarget,
                            bottomContent = {},
                            // 嵌入宿主只展示对话与角色管理，应用级导航仍由外层宿主持有。
                            showNavigationShortcuts = false,
                        )
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
                MainChatControls(Modifier.align(Alignment.TopStart).padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
    }
}

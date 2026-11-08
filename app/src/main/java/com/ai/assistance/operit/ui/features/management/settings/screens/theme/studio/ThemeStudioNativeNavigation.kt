package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders.ChatFolderRow
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders.FolderTreeList
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders.HistoryCreateCapsule
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.ChatSidebarBody
import com.ai.assistance.operit.ui.main.components.CompactNavigationDrawerItem
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.waterGlass
import com.ai.assistance.operit.ui.theme.script.*

/** 侧栏使用正式布局和历史行，仅向列表提供固定样例数据。 */
@Composable
internal fun StudioNavigationPreview(state: ThemeStudioState) {
    val appearance = rememberNavigationDrawerAppearance()
    val values = LocalThemePreferenceSnapshot.current.values
    val title = stringResource(R.string.studio_sample_history)
    val histories = remember(title) { (1..3).map { ChatHistory(id = "theme-preview-$it", title = "$title $it", messages = emptyList()) } }
    val rows = remember(histories) { histories.map { ChatFolderRow.Chat(it, 0, "theme-preview", null) } }
    val shape = themeScriptShape("navigation", RoundedCornerShape(0.dp))
    val item = stringResource(R.string.nav_group_plugins)
    val plugin = remember(item) { NavigationEntrySpec("theme-preview", "theme-preview", NavigationSurface.MAIN_SIDEBAR_PLUGINS, item, icon = Icons.Default.Settings) }
    Column(Modifier.fillMaxSize()) {
        StudioPreviewAppBar(state)
        val selected = state.target.id.takeIf { it == "navigationItem" || it == "navigation.item" } ?: "navigation"
        StudioPick(state, selected, Modifier.weight(1f).widthIn(max = 360.dp).fillMaxWidth()) {
            Surface(
                modifier = Modifier.waterGlass(appearance.waterGlassEnabled, shape, appearance.containerColor, region = GlassRegion.SIDEBAR),
                shape = shape,
                color = if (appearance.waterGlassEnabled) Color.Transparent else themeScriptFill("navigation", appearance.containerColor),
                contentColor = themeScriptContentColor("navigation", appearance.itemColor),
            ) {
                ThemeScriptSurface("navigation", drawFill = false) {
                    ChatSidebarBody(
                        brandName = "Operit", appearance = appearance, pluginEntries = listOf(plugin), selectedItem = null,
                        selectedRouteId = "theme-preview", permissionBadgeText = stringResource(R.string.sidebar_status_normal),
                        characterName = state.targetName, avatar = values.string("custom_ai_avatar_uri"),
                        onNavItemClick = {}, onNavigationEntryClick = {}, onOpenCharacterSettings = {},
                        onOpenCharacterSelector = {}, onCloseSidebar = {},
                        bottomContent = { CompactNavigationDrawerItem(Icons.Default.Settings, stringResource(R.string.studio_group_settings), true, appearance, {}) },
                        historyContent = {
                            HistoryCreateCapsule({}, {}, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                            FolderTreeList(
                                rows = rows, folders = emptyMap(), listState = rememberLazyListState(), currentId = histories.first().id,
                                activeStreamingIds = emptySet(), allChats = histories, draggingEnabled = false, manualSort = false,
                                collapsedBindings = emptySet(), onSelectChat = {}, onChatMenu = {}, onEditChat = {}, onDeleteChat = {},
                                onFolderMenu = {}, onToggleFolder = {}, onToggleBinding = {}, onBindingCreate = {}, onHoverExpand = {},
                                onMove = {}, onInteractionChange = {}, modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentColor = appearance.itemColor, selectedContainerColor = appearance.selectedContainerColor,
                                selectedContentColor = appearance.selectedContentColor,
                            )
                        },
                    )
                }
            }
        }
    }
}
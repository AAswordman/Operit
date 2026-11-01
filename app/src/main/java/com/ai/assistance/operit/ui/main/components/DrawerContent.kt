package com.ai.assistance.operit.ui.main.components
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import com.ai.assistance.operit.ui.theme.transition.LocalThemeRevealController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.ChatSidebarContent
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.SidebarButtonSurface
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalRunMainChatAction
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalMainChatViewModel
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalMainChatSidebarState
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.permissions.AndroidPermissionLevel

import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.theme.liquidGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SidebarPermissionStatus(
        val badgeTextResId: Int
)

private suspend fun resolveSidebarPermissionStatus(
        context: android.content.Context,
        preferredPermissionLevel: AndroidPermissionLevel?
): SidebarPermissionStatus {
        return when (preferredPermissionLevel) {
                null,
                AndroidPermissionLevel.STANDARD ->
                        SidebarPermissionStatus(
                                badgeTextResId = R.string.sidebar_status_normal
                        )
                AndroidPermissionLevel.ADMIN -> {
                        val capabilities = PermissionCapabilityResolver.statusUiSnapshot(context, preferredPermissionLevel)
                        SidebarPermissionStatus(
                                badgeTextResId = if (capabilities.canUseAccessibility || capabilities.canUseShizuku)
                                        R.string.sidebar_status_normal else R.string.unauthorized
                        )
                }
                AndroidPermissionLevel.ROOT -> {
                        val capabilities = PermissionCapabilityResolver.statusShellSnapshot(context, preferredPermissionLevel)
                        SidebarPermissionStatus(
                                badgeTextResId = if (capabilities.canUseRoot)
                                        R.string.sidebar_status_normal else R.string.unauthorized
                        )
                }
        }
}

/** Content for the expanded navigation drawer */
@Composable
fun DrawerContent(
        navItems: List<NavItem>,
        pluginEntries: List<NavigationEntrySpec>,
        selectedItem: NavItem?,
        selectedRouteId: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance,
        topContentPadding: Dp? = null,
        onCollapseSidebar: (() -> Unit)? = null,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        onScreenSelected: (Screen) -> Unit,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit
) {
        val context = LocalContext.current
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        fun dismissSidebarInput() {
                focus.clearFocus()
                keyboard?.hide()
        }
        val userPreferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
        val softwareIdentity by
                userPreferencesManager.softwareIdentity.collectAsState(
                        initial = UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT
                )
        val preferredPermissionLevel by
                androidPermissionPreferences.preferredPermissionLevelFlow.collectAsState(initial = null)
        val drawerBrandName =
                if (softwareIdentity == UserPreferencesManager.SOFTWARE_IDENTITY_LINGSHU) {
                        context.getString(R.string.software_identity_option_lingshu)
                } else {
                        context.getString(R.string.software_identity_option_operit)
                }
        val bottomInset =
                WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
        val resolvedTopContentPadding =
                topContentPadding ?:
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val permissionStatus by
                produceState(
                        initialValue =
                                SidebarPermissionStatus(
                                        badgeTextResId = R.string.sidebar_status_normal
                                ),
                        selectedRouteId,
                        preferredPermissionLevel
                ) {
                        value =
                                withContext(Dispatchers.IO) {
                                        resolveSidebarPermissionStatus(
                                                context = context,
                                                preferredPermissionLevel = preferredPermissionLevel
                                        )
                                }
                }
        val handleScreenSelection: (Screen) -> Unit = { screen ->
                dismissSidebarInput()
                val shouldCloseBeforeNavigate =
                        drawerState.currentValue == DrawerValue.Open ||
                                drawerState.targetValue == DrawerValue.Open

                // 页面切换与收起并行，避免等待抽屉关闭后才开始响应导航。
                onScreenSelected(screen)
                if (shouldCloseBeforeNavigate) {
                        scope.launch { drawerState.close() }
                }
        }
        val handleNavItemClick: (NavItem) -> Unit = { item ->
                handleScreenSelection(ScreenRouteRegistry.defaultScreenForNavItem(item))
        }
        val handleNavigationEntryClick: (NavigationEntrySpec) -> Unit = { entry ->
                dismissSidebarInput()
                val shouldCloseBeforeNavigate =
                        drawerState.currentValue == DrawerValue.Open ||
                                drawerState.targetValue == DrawerValue.Open
                // 页面切换与收起并行，避免等待抽屉关闭后才开始响应导航。
                onNavigationEntrySelected(entry)
                if (shouldCloseBeforeNavigate) {
                        scope.launch { drawerState.close() }
                }
        }

        val runMainChatAction = LocalRunMainChatAction.current
        val mainChatViewModel = LocalMainChatViewModel.current
        val closeSidebar: () -> Unit = {
            dismissSidebarInput()
            if (onCollapseSidebar != null) onCollapseSidebar()
            else scope.launch { drawerState.close() }
        }
        fun afterDrawerCloses(action: () -> Unit) {
            dismissSidebarInput()
            scope.launch {
                if (drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open) {
                    drawerState.close()
                }
                action()
            }
        }
        ChatSidebarContent(
            modifier = Modifier.fillMaxHeight().padding(top = resolvedTopContentPadding, bottom = bottomInset),
            brandName = drawerBrandName,
            appearance = appearance,
            pluginEntries = pluginEntries,
            permissionBadgeText = stringResource(permissionStatus.badgeTextResId),
            selectedItem = selectedItem,
            selectedRouteId = selectedRouteId,
            onNavItemClick = handleNavItemClick,
            onNavigationEntryClick = handleNavigationEntryClick,
            onOpenCharacterSettings = { handleScreenSelection(Screen.ModelPromptsSettings) },
            onCloseSidebar = closeSidebar,
            onRunChatAction = { action -> afterDrawerCloses { runMainChatAction(action) } },
            onCreateChat = { action ->
                dismissSidebarInput()
                runMainChatAction(action)
                closeSidebar()
            },
            onSelectCharacter = { target -> afterDrawerCloses {
                runMainChatAction {
                    mainChatViewModel.showChatPage()
                    mainChatViewModel.switchActiveCharacterTarget(target)
                }
            } },
            bottomContent = {
                DrawerBottomShortcutRow(selectedItem, appearance, handleNavItemClick)
            },
        )
}

/** Content for the collapsed navigation drawer (for tablet mode) */
@Composable
fun CollapsedDrawerContent(
        navItems: List<NavItem>,
        pluginEntries: List<NavigationEntrySpec>,
        selectedItem: NavItem?,
        selectedRouteId: String,
        isNetworkAvailable: Boolean,
        appearance: NavigationDrawerAppearance,
        onScreenSelected: (Screen) -> Unit,
        onExpandSidebar: () -> Unit,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit
) {
        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                        modifier =
                                Modifier.size(44.dp)
                                        .liquidGlass(
                                                enabled = appearance.buttonLiquidGlassEnabled,
                                                shape = CircleShape,
                                                containerColor = appearance.buttonContainerColor,
                                                enableLens = false,
                                            region = GlassRegion.SIDEBAR_BUTTONS,
                                        )
                                        .clip(CircleShape),
                        color = Color.Transparent,
                        shape = CircleShape
                ) {
                        IconButton(onClick = onExpandSidebar) {
                                Icon(
                                        imageVector =
                                                Icons.Default.Menu,
                                        contentDescription = stringResource(id = R.string.app_content_expand_sidebar),
                                        tint = appearance.statusAvailableColor,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.6f),
                        color = appearance.dividerColor
                )
                Spacer(modifier = Modifier.height(16.dp))

                for (item in navItems) {
                        val selectedGlassOverlayColor =
                                if (selectedItem == item) {
                                        appearance.selectedContainerColor.copy(alpha = 0.18f)
                                } else {
                                        Color.Transparent
                                }
                        Surface(
                                modifier =
                                        Modifier.padding(vertical = 8.dp)
                                                .size(44.dp)
                                                .liquidGlass(
                                                        enabled = appearance.buttonLiquidGlassEnabled,
                                                        shape = CircleShape,
                                                        containerColor =
                                                                appearance.buttonContainerColor,
                                                        enableLens = false,
                                                    region = GlassRegion.SIDEBAR_BUTTONS,
                                                )
                                                .clip(CircleShape)
                                                .background(selectedGlassOverlayColor),
                                color = Color.Transparent,
                                shape = CircleShape
                        ) {
                                IconButton(
                                        onClick = {
                                                if (item == NavItem.AiChat) onExpandSidebar()
                                                else onScreenSelected(
                                                        ScreenRouteRegistry.defaultScreenForNavItem(item)
                                                )
                                        }
                                ) {
                                        Icon(
                                                imageVector = item.icon,
                                                contentDescription = stringResource(id = item.titleResId),
                                                tint =
                                                        if (selectedItem == item) {
                                                                if (appearance.buttonLiquidGlassEnabled) {
                                                                        appearance.selectedContentColor
                                                                } else {
                                                                        appearance.titleColor
                                                                }
                                                        } else {
                                                                appearance.itemColor
                                                        },
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }
                }

                if (pluginEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(0.45f),
                                color = appearance.dividerColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        pluginEntries.forEach { entry ->
                                val selectedGlassOverlayColor =
                                        if (selectedRouteId == entry.routeId) {
                                                appearance.selectedContainerColor.copy(alpha = 0.18f)
                                        } else {
                                                Color.Transparent
                                        }
                                Surface(
                                        modifier =
                                                Modifier.padding(vertical = 8.dp)
                                                        .size(44.dp)
                                                        .liquidGlass(
                                                                enabled = appearance.buttonLiquidGlassEnabled,
                                                                shape = CircleShape,
                                                                containerColor = appearance.buttonContainerColor,
                                                                enableLens = false,
                                                            region = GlassRegion.SIDEBAR_BUTTONS,
                                                        )
                                                        .clip(CircleShape)
                                                        .background(selectedGlassOverlayColor),
                                        color = Color.Transparent,
                                        shape = CircleShape
                                ) {
                                        IconButton(onClick = { onNavigationEntrySelected(entry) }) {
                                                Icon(
                                                        imageVector = entry.icon,
                                                        contentDescription = entry.title,
                                                        tint =
                                                                if (selectedRouteId == entry.routeId) {
                                                                        if (appearance.buttonLiquidGlassEnabled) {
                                                                                appearance.selectedContentColor
                                                                        } else {
                                                                                appearance.titleColor
                                                                        }
                                                                } else {
                                                                        appearance.itemColor
                                                                },
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))
        }
}

@Composable
private fun DrawerBottomShortcutRow(
        selectedItem: NavItem?,
        appearance: NavigationDrawerAppearance,
        onNavItemClick: (NavItem) -> Unit
) {
        val context = LocalContext.current
        val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
        val snapshot = LocalThemePreferenceSnapshot.current
        val target = LocalMainChatSidebarState.current.activePrompt
        val darkTheme = if (snapshot.useSystemTheme) isSystemInDarkTheme()
                else snapshot.themeMode == UserPreferencesManager.THEME_MODE_DARK
        val reveal = LocalThemeRevealController.current
        var toggleCenter by remember { mutableStateOf(Offset.Zero) }
        val nextMode = if (darkTheme) UserPreferencesManager.THEME_MODE_LIGHT else UserPreferencesManager.THEME_MODE_DARK
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                BottomShortcutDrawerItem(Modifier.weight(1f), NavItem.Packages.icon,
                        stringResource(NavItem.Packages.titleResId), selectedItem == NavItem.Packages,
                        appearance, onClick = { onNavItemClick(NavItem.Packages) })
                BottomShortcutDrawerItem(Modifier.weight(1f), NavItem.Settings.icon,
                        stringResource(NavItem.Settings.titleResId), selectedItem == NavItem.Settings,
                        appearance, onClick = { onNavItemClick(NavItem.Settings) })
                BottomShortcutDrawerItem(Modifier.width(36.dp).onGloballyPositioned { toggleCenter = it.boundsInRoot().center },
                        if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        null, false, appearance,
                        description = stringResource(if (darkTheme) R.string.theme_light else R.string.theme_dark),
                        onClick = {
                                // 写入当前角色主题，切换为显式模式后不再被系统主题覆盖。
                                reveal.revealFrom(toggleCenter) {
                                        preferences.mutateThemeForPrompt(target) { values ->
                                                values.withBoolean("use_system_theme", false)
                                                        .withString("theme_mode", nextMode)
                                        }
                                }
                        })
        }
}

@Composable
private fun BottomShortcutDrawerItem(
        modifier: Modifier,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String?,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        description: String? = null,
        onClick: () -> Unit
) {
        val shape = RoundedCornerShape(8.dp)
        val contentColor = if (selected) appearance.selectedContentColor else appearance.itemColor
        SidebarButtonSurface(
                onClick = onClick,
                modifier = modifier.height(36.dp),
                shape = shape,
                appearance = appearance,
                containerColor = if (selected) appearance.selectedContainerColor
                    else if (appearance.buttonLiquidGlassEnabled) appearance.buttonContainerColor else Color.Transparent,
                contentColor = contentColor,
                border = BorderStroke(0.5.dp, appearance.itemColor.copy(alpha = 0.25f)),
        ) {
                Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                        Icon(icon, contentDescription = description, tint = contentColor, modifier = Modifier.size(16.dp))
                        if (label != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                }
        }
}

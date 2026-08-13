package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.action.ActionListenerFactory
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.screens.Screen
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
                AndroidPermissionLevel.DEBUGGER ->
                        when {
                                !ShizukuAuthorizer.isShizukuInstalled(context) ->
                                        SidebarPermissionStatus(
                                                badgeTextResId = R.string.status_not_installed
                                        )
                                !ShizukuAuthorizer.isShizukuServiceRunning() ->
                                        SidebarPermissionStatus(
                                                badgeTextResId = R.string.status_not_running
                                        )
                                ShizukuAuthorizer.hasShizukuPermission() ->
                                        SidebarPermissionStatus(
                                                badgeTextResId = R.string.sidebar_status_normal
                                        )
                                else ->
                                        SidebarPermissionStatus(
                                                badgeTextResId = R.string.unauthorized
                                        )
                        }
                AndroidPermissionLevel.ACCESSIBILITY,
                AndroidPermissionLevel.ADMIN,
                AndroidPermissionLevel.ROOT -> {
                        val permissionStatus =
                                ActionListenerFactory.getListener(context, preferredPermissionLevel)
                                        .hasPermission()
                        SidebarPermissionStatus(
                                badgeTextResId =
                                        if (permissionStatus.granted) {
                                                R.string.sidebar_status_normal
                                        } else {
                                                R.string.unauthorized
                                        }
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
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        onScreenSelected: (Screen) -> Unit,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit
) {
        val context = LocalContext.current
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
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val resolvedTopContentPadding =
                topContentPadding ?:
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fixedBottomItems = remember {
                setOf(NavItem.Settings, NavItem.Help, NavItem.About)
        }
        val quickActionItems = remember {
                setOf(NavItem.Packages, NavItem.Workflow)
        }
        val packageManager = remember(context) {
                PackageManager.getInstance(context, AIToolHandler.getInstance(context))
        }
        val workflowRepository = remember(context) { WorkflowRepository(context) }
        val activePackageCount by
                produceState(initialValue = 0, selectedRouteId) {
                        value =
                                withContext(Dispatchers.IO) {
                                        packageManager.getEnabledPackageNames().size
                                }
                }
        val workflowCount by
                produceState(initialValue = 0, selectedRouteId) {
                        value =
                                withContext(Dispatchers.IO) {
                                        workflowRepository.getAllWorkflows().getOrDefault(emptyList()).size
                                }
                }
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
        val primaryNavItems =
                remember(navItems) {
                        navItems.filterNot {
                                it in fixedBottomItems ||
                                        it in quickActionItems ||
                                        it == NavItem.ShizukuCommands
                        }
                }
        val handleScreenSelection: (Screen) -> Unit = { screen ->
                val shouldCloseBeforeNavigate =
                        drawerState.currentValue == DrawerValue.Open ||
                                drawerState.targetValue == DrawerValue.Open

                scope.launch {
                        if (shouldCloseBeforeNavigate) {
                                drawerState.close()
                        }
                        onScreenSelected(screen)
                }
        }
        val handleNavItemClick: (NavItem) -> Unit = { item ->
                handleScreenSelection(ScreenRouteRegistry.defaultScreenForNavItem(item))
        }
        val handleNavigationEntryClick: (NavigationEntrySpec) -> Unit = { entry ->
                val shouldCloseBeforeNavigate =
                        drawerState.currentValue == DrawerValue.Open ||
                                drawerState.targetValue == DrawerValue.Open
                scope.launch {
                        if (shouldCloseBeforeNavigate) {
                                drawerState.close()
                        }
                        onNavigationEntrySelected(entry)
                }
        }

        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .padding(
                                        top = resolvedTopContentPadding,
                                        end = 8.dp,
                                        bottom = bottomInset
                                )
        ) {
                Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                        NewSidebarTopContent(
                                selectedItem = selectedItem,
                                pluginEntries = pluginEntries,
                                selectedRouteId = selectedRouteId,
                                brandName = drawerBrandName,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                appearance = appearance,
                                navItems = primaryNavItems,
                                activePackageCount = activePackageCount,
                                workflowCount = workflowCount,
                                permissionStatus = permissionStatus,
                                onNavItemClick = handleNavItemClick,
                                onNavigationEntryClick = handleNavigationEntryClick
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = appearance.dividerColor.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                DrawerBottomShortcutRow(
                        selectedItem = selectedItem,
                        appearance = appearance,
                        onNavItemClick = handleNavItemClick
                )
        }
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
                                Modifier.size(44.dp).clip(CircleShape),
                        color = Color.Transparent,
                        shape = CircleShape
                ) {
                        IconButton(onClick = { }) {
                                Icon(
                                        imageVector =
                                                if (isNetworkAvailable) Icons.Default.Wifi
                                                else Icons.Default.WifiOff,
                                        contentDescription = stringResource(id = R.string.network_status_label),
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
                        Surface(
                                modifier =
                                        Modifier.padding(vertical = 8.dp)
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        if (selectedItem == item) appearance.selectedContainerColor.copy(alpha = 0.12f)
                                                        else Color.Transparent
                                                ),
                                color = Color.Transparent,
                                shape = CircleShape
                        ) {
                                IconButton(
                                        onClick = {
                                                onScreenSelected(
                                                        ScreenRouteRegistry.defaultScreenForNavItem(item)
                                                )
                                        }
                                ) {
                                        Icon(
                                                imageVector = item.icon,
                                                contentDescription = stringResource(id = item.titleResId),
                                                tint =
                                                        if (selectedItem == item) {
                                                                appearance.selectedContentColor
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
                                Surface(
                                        modifier =
                                                Modifier.padding(vertical = 8.dp)
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (selectedRouteId == entry.routeId) appearance.selectedContainerColor.copy(alpha = 0.12f)
                                                                else Color.Transparent
                                                        ),
                                        color = Color.Transparent,
                                        shape = CircleShape
                                ) {
                                        IconButton(onClick = { onNavigationEntrySelected(entry) }) {
                                                Icon(
                                                        imageVector = entry.icon,
                                                        contentDescription = entry.title,
                                                        tint =
                                                                if (selectedRouteId == entry.routeId) {
                                                                        appearance.selectedContentColor
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
private fun NewSidebarTopContent(
        selectedItem: NavItem?,
        pluginEntries: List<NavigationEntrySpec>,
        selectedRouteId: String,
        brandName: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance,
        navItems: List<NavItem>,
        activePackageCount: Int,
        workflowCount: Int,
        permissionStatus: SidebarPermissionStatus,
        onNavItemClick: (NavItem) -> Unit,
        onNavigationEntryClick: (NavigationEntrySpec) -> Unit
) {
        Spacer(modifier = Modifier.height(24.dp))

        SidebarInfoCard(
                brandName = brandName,
                isNetworkAvailable = isNetworkAvailable,
                networkType = networkType,
                appearance = appearance
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                SidebarQuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = NavItem.Packages.icon,
                        label = stringResource(id = NavItem.Packages.titleResId),
                        badgeText = activePackageCount.toString(),
                        selected = selectedItem == NavItem.Packages,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Packages) }
                )
                SidebarQuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = NavItem.ShizukuCommands.icon,
                        label = stringResource(id = R.string.sidebar_permission_short),
                        badgeText = stringResource(id = permissionStatus.badgeTextResId),
                        selected = selectedItem == NavItem.ShizukuCommands,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.ShizukuCommands) }
                )
                SidebarQuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = NavItem.Workflow.icon,
                        label = stringResource(id = NavItem.Workflow.titleResId),
                        badgeText = workflowCount.toString(),
                        selected = selectedItem == NavItem.Workflow,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Workflow) }
                )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
                text = stringResource(id = R.string.nav_group_ai_features),
                style = MaterialTheme.typography.titleSmall,
                color = appearance.titleColor.copy(alpha = 0.82f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 28.dp, end = 20.dp, bottom = 2.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        navItems.forEach { item ->
                CompactNavigationDrawerItem(
                        icon = item.icon,
                        label = stringResource(id = item.titleResId),
                        selected = selectedItem == item,
                        appearance = appearance,
                        onClick = { onNavItemClick(item) }
                )
        }
        if (pluginEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                        text = stringResource(id = R.string.nav_group_plugins),
                        style = MaterialTheme.typography.titleSmall,
                        color = appearance.titleColor.copy(alpha = 0.82f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 28.dp, end = 20.dp, bottom = 2.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                pluginEntries.forEach { entry ->
                        CompactNavigationDrawerItem(
                                icon = entry.icon,
                                label = entry.title,
                                selected = selectedRouteId == entry.routeId,
                                appearance = appearance,
                                onClick = { onNavigationEntryClick(entry) }
                        )
                }
        }
}

@Composable
private fun SidebarInfoCard(
        brandName: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance
) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
                Text(
                        text = brandName,
                        style = MaterialTheme.typography.titleLarge.copy(
                                letterSpacing = 0.5.sp
                        ),
                        color = appearance.titleColor,
                        fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                        shape = RoundedCornerShape(50),
                        color = appearance.statusAvailableColor.copy(alpha = 0.12f)
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                                Box(
                                        modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        if (isNetworkAvailable) Color(0xFF4CAF50)
                                                        else Color(0xFFEF5350)
                                                )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                        imageVector =
                                                if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                                        contentDescription = stringResource(id = R.string.network_status_label),
                                        tint = appearance.statusAvailableColor,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                        text = networkType,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = appearance.statusAvailableColor,
                                        fontWeight = FontWeight.Medium
                                )
                        }
                }
        }
}

@Composable
private fun SidebarQuickActionCard(
        modifier: Modifier = Modifier,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        badgeText: String,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
        val itemShape = RoundedCornerShape(16.dp)
        Surface(
                modifier =
                        modifier.height(52.dp)
                                .clip(itemShape)
                                .background(
                                        if (selected) appearance.selectedContainerColor.copy(alpha = 0.12f)
                                        else appearance.buttonContainerColor.copy(alpha = 0.06f)
                                ),
                onClick = onClick,
                color = Color.Transparent,
                shape = itemShape
        ) {
                Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (selected) {
                                Box(
                                        modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 8.dp, end = 8.dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2D7FF9))
                                )
                        }
                        SidebarQuickActionBadge(
                                text = badgeText,
                                appearance = appearance,
                                selected = selected,
                                modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                        )
                        Column(
                                modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                                letterSpacing = 0.2.sp
                                        ),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                }
        }
}

@Composable
private fun SidebarQuickActionBadge(
        text: String,
        appearance: NavigationDrawerAppearance,
        selected: Boolean,
        modifier: Modifier = Modifier
) {
        Surface(
                modifier = modifier,
                shape = RoundedCornerShape(50),
                color =
                        if (selected) {
                                appearance.selectedContentColor.copy(alpha = 0.16f)
                        } else {
                                appearance.selectedContainerColor.copy(alpha = 0.18f)
                        }
        ) {
                Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.3.sp
                        ),
                        color =
                                if (selected) appearance.selectedContentColor
                                else appearance.titleColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                )
        }
}

@Composable
private fun DrawerBottomShortcutRow(
        selectedItem: NavItem?,
        appearance: NavigationDrawerAppearance,
        onNavItemClick: (NavItem) -> Unit
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                BottomShortcutDrawerItem(
                        modifier = Modifier.weight(1f),
                        item = NavItem.About,
                        selected = selectedItem == NavItem.About,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.About) }
                )
                BottomShortcutDrawerItem(
                        modifier = Modifier.weight(1f),
                        item = NavItem.Help,
                        selected = selectedItem == NavItem.Help,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Help) }
                )
                BottomShortcutDrawerItem(
                        modifier = Modifier.weight(1f),
                        item = NavItem.Settings,
                        selected = selectedItem == NavItem.Settings,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Settings) }
                )
        }
}

@Composable
private fun BottomShortcutDrawerItem(
        modifier: Modifier = Modifier,
        item: NavItem,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
        val itemShape = RoundedCornerShape(16.dp)

        Surface(
                modifier =
                        modifier
                                .height(52.dp)
                                .clip(itemShape)
                                .background(
                                        if (selected) appearance.selectedContainerColor.copy(alpha = 0.12f)
                                        else appearance.buttonContainerColor.copy(alpha = 0.06f)
                                ),
                onClick = onClick,
                color = Color.Transparent,
                shape = itemShape
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        if (selected) {
                                Box(
                                        modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 8.dp, end = 8.dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2D7FF9))
                                )
                        }
                        Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = stringResource(id = item.titleResId),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                                letterSpacing = 0.1.sp
                                        ),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        color =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }
                }
        }
}

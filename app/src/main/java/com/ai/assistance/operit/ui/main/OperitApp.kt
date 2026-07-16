package com.ai.assistance.operit.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.announcement.RemoteAnnouncementDisplay
import com.ai.assistance.operit.data.announcement.RemoteAnnouncementRepository
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.RemoteAnnouncementPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.announcement.RemoteAnnouncementDialog
import com.ai.assistance.operit.ui.main.layout.PhoneLayout
import com.ai.assistance.operit.ui.main.layout.TabletLayout
import com.ai.assistance.operit.ui.main.navigation.AppNavigationModel
import com.ai.assistance.operit.ui.main.navigation.AppRouteCatalog
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.navigation.AppRouterGateway
import com.ai.assistance.operit.ui.main.navigation.AppRouterState
import com.ai.assistance.operit.ui.main.navigation.AppRouteDiscoveryGateway
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.LocalRouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.navigation.RouteBackGuardRegistry
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.update.screens.UpdateScreen
import com.ai.assistance.operit.util.AppLogger

// 为TopAppBar的actions提供CompositionLocal
// 它允许子组件（如AIChatScreen）向上提供它们的action Composable
val LocalTopBarActions = compositionLocalOf<(@Composable (RowScope.() -> Unit)) -> Unit> { {} }

class TopBarTitleContent(val content: @Composable () -> Unit)

val LocalTopBarTitleContent = compositionLocalOf<(TopBarTitleContent?) -> Unit> { {} }
val LocalAppNavigationModel = compositionLocalOf<AppNavigationModel?> { null }

enum class NavigationTransitionSource {
    DEFAULT,
    DRAWER
}

private const val TAG = "OperitApp"

private data class NetworkStateSnapshot(
    val isAvailable: Boolean,
    val type: String
)

@Composable
fun OperitApp(
    initialNavItem: NavItem = NavItem.AiChat,
    toolHandler: AIToolHandler? = null,
    shortcutNavRequest: NavItem? = null,
    shortcutNavRequestId: Long = 0L,
    routeNavRequest: String? = null,
    routeNavArgs: Map<String, Any?> = emptyMap(),
    routeNavRequestId: Long = 0L,
    onShortcutNavHandled: (Long) -> Unit = {},
    onCurrentNavItemChanged: (NavItem) -> Unit = {},
    onRouteNavHandled: (Long) -> Unit = {}
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val remoteAnnouncementRepository = remember { RemoteAnnouncementRepository() }
    val remoteAnnouncementPreferences = remember { RemoteAnnouncementPreferences(context) }
    var navigationRevision by remember { mutableStateOf(0) }
    val configuration = LocalConfiguration.current
    val navigationModel = remember(context, configuration, navigationRevision) { AppRouteCatalog.build(context) }

    val routerState = remember {
        AppRouterState(AppRouteCatalog.initialEntry(initialNavItem))
    }
    val routeBackGuardRegistry = remember { RouteBackGuardRegistry() }
    val currentRouteEntry = routerState.currentEntry
    val currentScreen = AppRouteCatalog.resolveScreen(navigationModel, currentRouteEntry) ?: Screen.AiChat
    val selectedItem = currentScreen.navItem
    val pluginSidebarEntries =
        remember(navigationModel) {
            navigationModel.navigationEntries.filter {
                it.surface == NavigationSurface.MAIN_SIDEBAR_PLUGINS
            }
        }

    // 跟踪是否是返回操作
    var isNavigatingBack by remember { mutableStateOf(false) }
    var navigationTransitionSource by remember {
        mutableStateOf(NavigationTransitionSource.DEFAULT)
    }

    // 用于存储由子屏幕提供的TopAppBar Actions
    var topBarActions by remember { mutableStateOf<@Composable RowScope.() -> Unit>({}) }
    var topBarTitleContent by remember { mutableStateOf<TopBarTitleContent?>(null) }
    var lastHandledShortcutRequestId by remember { mutableStateOf(0L) }
    var lastHandledRouteRequestId by remember { mutableStateOf(0L) }

    LaunchedEffect(selectedItem) {
        selectedItem?.let { navItem ->
            onCurrentNavItemChanged(navItem)
        }
    }

    LaunchedEffect(shortcutNavRequestId, shortcutNavRequest) {
        val requestNavItem = shortcutNavRequest
        if (requestNavItem == null || shortcutNavRequestId == 0L) {
            return@LaunchedEffect
        }
        if (shortcutNavRequestId == lastHandledShortcutRequestId) {
            return@LaunchedEffect
        }

        val targetEntry = AppRouteCatalog.initialEntry(requestNavItem)
        isNavigatingBack = false
        navigationTransitionSource = NavigationTransitionSource.DEFAULT
        routerState.resetTo(targetEntry)
        lastHandledShortcutRequestId = shortcutNavRequestId
        onShortcutNavHandled(shortcutNavRequestId)
    }

    LaunchedEffect(routeNavRequestId, routeNavRequest, routeNavArgs, navigationModel) {
        val requestRouteId = routeNavRequest?.trim().orEmpty()
        if (requestRouteId.isBlank() || routeNavRequestId == 0L) {
            return@LaunchedEffect
        }
        if (routeNavRequestId == lastHandledRouteRequestId) {
            return@LaunchedEffect
        }
        if (navigationModel.routesById[requestRouteId] == null) {
            AppLogger.w(TAG, "Ignored pending route navigation for unknown routeId=$requestRouteId")
            lastHandledRouteRequestId = routeNavRequestId
            onRouteNavHandled(routeNavRequestId)
            return@LaunchedEffect
        }
        isNavigatingBack = false
        navigationTransitionSource = NavigationTransitionSource.DEFAULT
        routerState.resetTo(
            com.ai.assistance.operit.ui.main.navigation.RouteEntry(
                routeId = requestRouteId,
                args = routeNavArgs,
                source = RouteEntrySource.DEFAULT
            )
        )
        lastHandledRouteRequestId = routeNavRequestId
        onRouteNavHandled(routeNavRequestId)
    }

    // 当currentScreen改变时，检查是否需要清空TopBarActions
    // 这是为了解决从有action的屏幕导航到无action的屏幕时，action残留的问题
    LaunchedEffect(currentScreen) {
        if (currentScreen !is Screen.AiChat && currentScreen !is Screen.TokenConfig) {
            topBarActions = {}
        }
        topBarTitleContent = null
    }

    // Navigation functions
    fun navigateTo(newScreen: Screen, fromDrawer: Boolean = false) {
        isNavigatingBack = false
        navigationTransitionSource =
            if (fromDrawer) NavigationTransitionSource.DRAWER
            else NavigationTransitionSource.DEFAULT
        val nextEntry =
            AppRouteCatalog.toEntry(
                screen = newScreen,
                source =
                    if (fromDrawer) RouteEntrySource.DRAWER
                    else RouteEntrySource.DEFAULT
            )
        if (currentRouteEntry.routeId == nextEntry.routeId && currentRouteEntry.args == nextEntry.args) {
            return
        }
        if (fromDrawer) {
            routerState.resetTo(nextEntry)
        } else {
            routerState.navigate(
                routeId = nextEntry.routeId,
                args = nextEntry.args,
                source = nextEntry.source,
                routeSpec = navigationModel.routesById[nextEntry.routeId]
            )
        }
    }

    fun performGoBack() {
        if (routerState.canPop) {
            isNavigatingBack = true
            navigationTransitionSource = NavigationTransitionSource.DEFAULT
            routerState.pop()
        } else if (currentScreen !is Screen.AiChat) {
            isNavigatingBack = true
            navigationTransitionSource = NavigationTransitionSource.DEFAULT
            routerState.resetTo(AppRouteCatalog.toEntry(Screen.AiChat))
        }
    }

    var isBackRequestInProgress by remember { mutableStateOf(false) }

    fun requestGoBack() {
        val requestedRouteInstanceId = routerState.currentEntry.instanceId
        if (!routeBackGuardRegistry.hasGuard(requestedRouteInstanceId)) {
            performGoBack()
            return
        }
        if (isBackRequestInProgress) {
            return
        }

        isBackRequestInProgress = true
        scope.launch {
            try {
                val canNavigateBack =
                    routeBackGuardRegistry.canNavigateBack(requestedRouteInstanceId)
                if (
                    canNavigateBack &&
                        routerState.currentEntry.instanceId == requestedRouteInstanceId
                ) {
                    performGoBack()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "返回处理失败", e)
            } finally {
                isBackRequestInProgress = false
            }
        }
    }

    fun navigateToNavigationEntry(entry: NavigationEntrySpec) {
        val action = entry.action
        if (action != null) {
            val ownerPackageName = entry.ownerPackageName ?: return
            scope.launch(Dispatchers.IO) {
                packageManager.runToolPkgNavigationEntryAction(
                    containerPackageName = ownerPackageName,
                    entryId = entry.entryId,
                    functionName = action.functionName,
                    inlineFunctionSource = action.functionSource,
                    eventPayload =
                        mapOf(
                            "entryId" to entry.entryId,
                            "routeId" to entry.routeId,
                            "surface" to entry.surface.name.lowercase(),
                            "title" to entry.title,
                            "description" to entry.description
                        )
                ).onFailure { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg navigation action failed: entryId=${entry.entryId}, package=$ownerPackageName",
                        error
                    )
                }
            }
            return
        }
        if (currentRouteEntry.routeId == entry.routeId && currentRouteEntry.args == entry.routeArgs) {
            return
        }
        isNavigatingBack = false
        navigationTransitionSource = NavigationTransitionSource.DRAWER
        routerState.resetTo(
            com.ai.assistance.operit.ui.main.navigation.RouteEntry(
                routeId = entry.routeId,
                args = entry.routeArgs,
                source = RouteEntrySource.DRAWER
            )
        )
    }

    // Function to navigate to TokenConfig, treated as sub-navigation.
    fun navigateToTokenConfig() {
        navigateTo(Screen.TokenConfig)
    }

    BackHandler(enabled = currentScreen !is Screen.AiChat, onBack = { requestGoBack() })

    val canGoBack = routerState.canPop

    var isLoading by remember { mutableStateOf(false) }

    // Tablet mode sidebar state
    var isTabletSidebarExpanded by remember { mutableStateOf(false) }
    var tabletSidebarWidth by remember { mutableStateOf(280.dp) } // 侧边栏默认宽度
    val collapsedTabletSidebarWidth = 64.dp // 收起时的宽度

    // Device screen size calculation
    val screenWidthDp = configuration.screenWidthDp

    // Determine if using tablet layout based on screen width
    // Using Material Design 3 guidelines:
    // - Less than 600dp: phone
    // - 600dp and above: tablet
    val useTabletLayout = screenWidthDp >= 600

    var remoteAnnouncement by remember { mutableStateOf<RemoteAnnouncementDisplay?>(null) }

    fun dismissRemoteAnnouncement() {
        remoteAnnouncement?.let { announcement ->
            remoteAnnouncementPreferences.setAcknowledgedVersion(announcement.version)
        }
        remoteAnnouncement = null
    }

    val navItems = listOf(
        NavItem.AiChat,
        NavItem.AssistantConfig,
        NavItem.Packages,
        NavItem.MemoryBase,
        NavItem.Toolbox,
        NavItem.ShizukuCommands,
        NavItem.Workflow,
        NavItem.Settings,
        NavItem.Help,
        NavItem.About
    )

    // Network state monitoring
    var isNetworkAvailable by remember { mutableStateOf(false) }
    var networkType by remember { mutableStateOf(context.getString(R.string.not_connected)) }

    // Periodically check network status
    LaunchedEffect(Unit) {
        while (true) {
            val snapshot =
                withContext(Dispatchers.IO) {
                    NetworkStateSnapshot(
                        isAvailable = NetworkUtils.isNetworkAvailable(appContext),
                        type = NetworkUtils.getNetworkType(appContext)
                    )
                }
            isNetworkAvailable = snapshot.isAvailable
            networkType = snapshot.type
            delay(10000) // Check every 10 seconds
        }
    }

    // Fetch remote announcement when network becomes available.
    LaunchedEffect(isNetworkAvailable) {
        if (!isNetworkAvailable || remoteAnnouncement != null) return@LaunchedEffect

        val announcement = remoteAnnouncementRepository.fetchDisplayableAnnouncement()
        if (announcement != null && remoteAnnouncementPreferences.shouldShow(announcement.version)) {
            remoteAnnouncement = announcement
        }
    }

    // Get FPS counter display setting
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val showFpsCounter = displayPreferencesManager.showFpsCounter.collectAsState(initial = false).value
    val enableNavigationAnimation =
        displayPreferencesManager.enableNavigationAnimation
            .collectAsState(initial = true)
            .value

    // Create an instance of MCPRepository
    val mcpRepository = remember { MCPRepository(context) }

    // Initialize MCP plugin status
    LaunchedEffect(Unit) {
        launch {
            // First scan local installed plugins
            mcpRepository.syncInstalledStatus()
        }
    }

    // Calculate drawer width for phone mode
    val drawerWidth = (screenWidthDp * 0.75).dp // Drawer width is 3/4 of screen width

    // Main app container
    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        DisposableEffect(packageManager) {
            val listener = PackageManager.ToolPkgRuntimeChangeListener { _ ->
                navigationRevision += 1
            }
            packageManager.addToolPkgRuntimeChangeListener(listener)
            onDispose {
                packageManager.removeToolPkgRuntimeChangeListener(listener)
            }
        }
        DisposableEffect(routerState, navigationModel) {
            AppRouterGateway.install(
                handler = { routeId, args, source ->
                    val routeSpec = navigationModel.routesById[routeId] ?: return@install
                    isNavigatingBack = false
                    navigationTransitionSource =
                        if (source == RouteEntrySource.DRAWER) NavigationTransitionSource.DRAWER
                        else NavigationTransitionSource.DEFAULT
                    routerState.navigate(routeId = routeId, args = args, source = source, routeSpec = routeSpec)
                },
                reset = { routeId, args, source ->
                    navigationModel.routesById[routeId] ?: return@install
                    isNavigatingBack = false
                    navigationTransitionSource =
                        if (source == RouteEntrySource.DRAWER) NavigationTransitionSource.DRAWER
                        else NavigationTransitionSource.DEFAULT
                    routerState.resetTo(
                        com.ai.assistance.operit.ui.main.navigation.RouteEntry(
                            routeId = routeId,
                            args = args,
                            source = source
                        )
                    )
                }
            )
            AppRouteDiscoveryGateway.install {
                navigationModel.routes
            }
            onDispose {
                AppRouterGateway.clear()
                AppRouteDiscoveryGateway.clear()
            }
        }
        CompositionLocalProvider(
            LocalAppNavigationModel provides navigationModel,
            LocalRouteBackGuardRegistry provides routeBackGuardRegistry,
            LocalTopBarActions provides { actions: @Composable RowScope.() -> Unit ->
                topBarActions = actions
            },
            LocalTopBarTitleContent provides { titleContent ->
                topBarTitleContent = titleContent
            }
        ) {
            if (useTabletLayout) {
                // Tablet layout
                TabletLayout(
                    currentRouteEntry = currentRouteEntry,
                    currentScreen = currentScreen,
                    selectedItem = selectedItem,
                    isTabletSidebarExpanded = isTabletSidebarExpanded,
                    isLoading = isLoading,
                    navItems = navItems,
                    pluginSidebarEntries = pluginSidebarEntries,
                    selectedRouteId = currentRouteEntry.routeId,
                    isNetworkAvailable = isNetworkAvailable,
                    networkType = networkType,
                    navController = navController,
                    scope = scope,
                    drawerState = drawerState,
                    showFpsCounter = showFpsCounter,
                    enableNavigationAnimation = enableNavigationAnimation,
                    navigationTransitionSource = navigationTransitionSource,
                    tabletSidebarWidth = tabletSidebarWidth,
                    collapsedTabletSidebarWidth = collapsedTabletSidebarWidth,
                    onScreenChange = { screen -> navigateTo(screen) },
                    onDrawerItemSelected = { screen ->
                        navigateTo(screen, fromDrawer = true)
                    },
                    onNavigationEntrySelected = ::navigateToNavigationEntry,
                    onToggleSidebar = {
                        isTabletSidebarExpanded = !isTabletSidebarExpanded
                    },
                    navigateToTokenConfig = ::navigateToTokenConfig,
                    canGoBack = canGoBack,
                    onGoBack = ::requestGoBack,
                    isNavigatingBack = isNavigatingBack,
                    topBarActions = { topBarActions() },
                    topBarTitleContent = topBarTitleContent
                )
            } else {
                // Phone layout
                PhoneLayout(
                    currentRouteEntry = currentRouteEntry,
                    currentScreen = currentScreen,
                    selectedItem = selectedItem,
                    isLoading = isLoading,
                    navItems = navItems,
                    pluginSidebarEntries = pluginSidebarEntries,
                    selectedRouteId = currentRouteEntry.routeId,
                    isNetworkAvailable = isNetworkAvailable,
                    networkType = networkType,
                    drawerWidth = drawerWidth,
                    navController = navController,
                    scope = scope,
                    drawerState = drawerState,
                    showFpsCounter = showFpsCounter,
                    enableNavigationAnimation = enableNavigationAnimation,
                    navigationTransitionSource = navigationTransitionSource,
                    onScreenChange = { screen -> navigateTo(screen) },
                    onDrawerItemSelected = { screen ->
                        navigateTo(screen, fromDrawer = true)
                    },
                    onNavigationEntrySelected = ::navigateToNavigationEntry,
                    navigateToTokenConfig = ::navigateToTokenConfig,
                    canGoBack = canGoBack,
                    onGoBack = ::requestGoBack,
                    isNavigatingBack = isNavigatingBack,
                    topBarActions = { topBarActions() },
                    topBarTitleContent = topBarTitleContent
                )
            }
        }

        remoteAnnouncement?.let { announcement ->
            RemoteAnnouncementDialog(
                title = announcement.title,
                body = announcement.body,
                acknowledgeText = announcement.acknowledgeText,
                countdownSeconds = announcement.countdownSec,
                onAcknowledge = { dismissRemoteAnnouncement() }
            )
        }
    }
}

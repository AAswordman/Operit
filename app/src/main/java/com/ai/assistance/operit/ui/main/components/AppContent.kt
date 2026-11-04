package com.ai.assistance.operit.ui.main.components

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIcon
import com.ai.assistance.operit.ui.theme.script.themeScriptShape
import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration
import com.ai.assistance.operit.ui.theme.script.themeScriptFill
import com.ai.assistance.operit.ui.theme.script.themeScriptContentColor
import com.ai.assistance.operit.ui.theme.script.themeSurface
import androidx.compose.foundation.background
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubPage
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalMainChatViewModel
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.MainChatControls
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.common.displays.FpsCounter
import com.ai.assistance.operit.ui.main.NavigationTransitionSource
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.LocalRouteInstanceId
import com.ai.assistance.operit.ui.main.navigation.ScreenRouteViewModelStoreOwnerManager
import com.ai.assistance.operit.ui.main.navigation.retainedRouteKeysOnContentAttach
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.common.composedsl.ToolPkgComposeDslToolScreen
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import com.ai.assistance.operit.api.chat.AIForegroundService

// 定义一个 CompositionLocal，用于向下传递当前屏幕是否可见的状态
val LocalIsCurrentScreen = compositionLocalOf { true }
val LocalSetScreenSoftInputMode = compositionLocalOf<(Int?) -> Unit> { {} }
val LocalSetUseScreenImePadding = compositionLocalOf<(Boolean) -> Unit> { {} }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Activity.manifestSoftInputMode(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getActivityInfo(
            componentName,
            PackageManager.ComponentInfoFlags.of(0),
        ).softInputMode
    } else {
        @Suppress("DEPRECATION")
        packageManager.getActivityInfo(componentName, 0).softInputMode
    }

@Composable
private fun ImeWakeListeningEffect(
    context: Context,
    density: androidx.compose.ui.unit.Density,
) {
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(context, imeVisible) {
        AIForegroundService.setWakeListeningSuspendedForIme(context, imeVisible)
    }
}

// 用于屏幕切换动画的状态
private enum class ScreenVisibility {
    VISIBLE,
    HIDDEN
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AppContent(
        currentRouteEntry: RouteEntry,
        currentScreen: Screen,
        selectedItem: NavItem?,
        useTabletLayout: Boolean,
        isTabletSidebarExpanded: Boolean,
        isLoading: Boolean,
        navController: NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        enableNavigationAnimation: Boolean,
        navigationTransitionSource: NavigationTransitionSource,
        onScreenChange: (Screen) -> Unit,
        onToggleSidebar: () -> Unit,
        navigateToTokenConfig: () -> Unit,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {},
        canGoBack: Boolean,
        onGoBack: () -> Unit,
        isNavigatingBack: Boolean = false,
        actions: @Composable RowScope.() -> Unit = {},
        titleContent: TopBarTitleContent? = null,
        /** 当前导航栈中仍存活的路由 screenKey（路由级 ViewModelStore 清理依据）。 */
        aliveScreenKeys: Set<String>
) {
    val workspaceVisible = if (currentScreen is Screen.AiChat) {
        val workspacePage by LocalMainChatViewModel.current.workspaceHubPage.collectAsState()
        workspacePage != WorkspaceHubPage.CHAT
    } else false
    // Get background image state
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
    val manifestSoftInputMode = remember(hostActivity) { hostActivity?.manifestSoftInputMode() }
    val density = LocalDensity.current
    val pageTransitionDurationMillis = if (enableNavigationAnimation) 280 else 0
    val drawerRelayTransitionDurationMillis = if (enableNavigationAnimation) 320 else 0
    val pageTransitionOffsetPx =
        with(density) { if (useTabletLayout) 28.dp.toPx() else 20.dp.toPx() }
    val drawerNavigationOffsetPx =
        with(density) { if (useTabletLayout) 40.dp.toPx() else 30.dp.toPx() }
    ImeWakeListeningEffect(context = context, density = density)
    val themeSnapshot = LocalThemePreferenceSnapshot.current
    val useBackgroundImage = themeSnapshot.useBackgroundImage
    val backgroundImageUri = themeSnapshot.backgroundImageUri
    val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null
    val backgroundDecoration = themeSurface("background")
    // 工作室背景与媒体背景共用底层画布，内容底色不能盖住已声明的填色或图层。
    val hasBackgroundDecoration = backgroundDecoration != null &&
        (backgroundDecoration.fillColor != null || backgroundDecoration.layers.isNotEmpty())

    // 标准页面顶栏连同状态栏统一使用主题底色，避免聊天壁纸透出。
    val appBarContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f)
    val appBarContentColor = MaterialTheme.colorScheme.onBackground

    // 屏幕缓存 Map - 保存已访问过的屏幕，使其状态得以保留
    val screenCache = remember { mutableStateMapOf<String, @Composable () -> Unit>() }
    val screenKeepAliveCache = remember { mutableStateMapOf<String, Boolean>() }
    val screenStateHolder = rememberSaveableStateHolder()
    // 路由级 ViewModelStore 管理器：自身是 Activity 级 ViewModel（跨配置变化
    // 保留 owner/VM）；路由出栈/替换/清栈时清理对应 store（P1：离页查询不保留）。
    val routeViewModelStoreManager: ScreenRouteViewModelStoreOwnerManager = viewModel()
    val currentScreenKey =
        remember(currentRouteEntry.instanceId, currentScreen) {
            currentScreen.screenKey(currentRouteEntry.instanceId)
        }
    var currentScreenSoftInputMode by remember(currentScreenKey) { mutableStateOf<Int?>(null) }
    var currentScreenUsesImePadding by remember(currentScreenKey) { mutableStateOf(false) }

    // 全新组合（配置变化/跨 600dp 布局重建）：screenCache 已随组合重置，
    // 但 Activity 级 manager/routerState 保留的导航栈仍存活，只保留当前页会
    // 误清 backStack 其他路由的 owner（P1）。首次组合（attach）时按
    // aliveScreenKeys + 当前键同步一次。key 必须用 Unit：只在组合进入时执行
    // 一次，不得随 alive 变化重启——pop 后 alive 立即更新，若按它重启会在
    // 退出动画完成前清理仍渲染的离页 owner。pop/replace/clear 的清理只由
    // 转场完成分支 retainOnly(aliveRouteKeys()) 执行（含过渡/keepAlive 时机）；
    // Phone/Tablet 切换会销毁本组合，新组合 attach 时重新执行并用新传入 alive。
    LaunchedEffect(Unit) {
        routeViewModelStoreManager.retainOnly(
            retainedRouteKeysOnContentAttach(currentScreenKey, aliveScreenKeys)
        )
    }
    val effectiveSoftInputMode =
        currentScreenSoftInputMode
            ?: manifestSoftInputMode
            ?: WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN

    SideEffect {
        hostActivity?.window?.setSoftInputMode(effectiveSoftInputMode)
    }

    androidx.compose.runtime.DisposableEffect(hostActivity, manifestSoftInputMode) {
        onDispose {
            if (hostActivity != null && manifestSoftInputMode != null) {
                hostActivity.window.setSoftInputMode(manifestSoftInputMode)
            }
        }
    }

    CompositionLocalProvider(
        LocalAppBarContentColor provides appBarContentColor,
    ) {
        // 使用Scaffold来正确处理顶部栏和内容的布局
        // contentWindowInsets = WindowInsets(0) 让内容可以延伸到系统栏下方，使背景能够完全填充
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (currentScreen !is Screen.AiChat && currentScreen !is Screen.GlobalDisplaySettings) {
                    // 单一工具栏 - 使用小型化的设计
                    // 使用 windowInsets 参数让 TopAppBar 自己处理状态栏的 insets
                    TopAppBar(
                        modifier = Modifier.clip(themeScriptShape("appBar", androidx.compose.ui.graphics.RectangleShape))
                            .background(themeScriptFill("appBar", appBarContainerColor))
                            .themeScriptDecoration("appBar", drawFill = false),
                        expandedHeight = 48.dp,
                        windowInsets = WindowInsets.statusBars,
                        title = {
                            if (titleContent != null) {
                                titleContent.content()
                            } else {
                                Text(
                                    text = when {
                                        currentScreen.getTitle().isNotBlank() -> currentScreen.getTitle()
                                        selectedItem?.titleResId != null && selectedItem.titleResId != 0 ->
                                            stringResource(selectedItem.titleResId)
                                        else -> ""
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = appBarContentColor
                                )
                            }
                        },
                        navigationIcon = {
                            // 导航按钮逻辑
                            ThemeScriptIconButton(
                                actionId = if (canGoBack) "appBar.back" else "appBar.menu",
                                onClick = {
                                    if (canGoBack) {
                                        onGoBack()
                                    } else {
                                        // 平板模式下切换侧边栏展开/收起状态
                                        if (useTabletLayout) {
                                            onToggleSidebar()
                                        } else {
                                            // 手机模式下打开抽屉
                                            scope.launch { drawerState.open() }
                                        }
                                    }
                                },
                            ) {
                                ThemeScriptIcon(
                                    id = if (canGoBack) "appBar.back" else "appBar.menu",
                                    imageVector = if (canGoBack) Icons.Default.ArrowBack
                                    else if (useTabletLayout)
                                    // 平板模式下使用开关图标表示收起/展开
                                        if (isTabletSidebarExpanded) Icons.Filled.ChevronLeft
                                        else Icons.Default.Menu
                                    else Icons.Default.Menu,
                                    contentDescription =
                                    when {
                                        canGoBack -> stringResource(R.string.app_content_navigate_back)
                                        useTabletLayout ->
                                            if (isTabletSidebarExpanded) stringResource(R.string.app_content_collapse_sidebar)
                                            else stringResource(R.string.app_content_expand_sidebar)
                                        else -> stringResource(id = R.string.menu)
                                    },
                                    tint = appBarContentColor
                                )
                            }
                        },
                        actions = actions,
                        colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            titleContentColor = appBarContentColor,
                            navigationIconContentColor = appBarContentColor,
                            actionIconContentColor = appBarContentColor
                        ),
                        // Scaffold会处理 insets, 这里不再需要手动添加 modifier
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            // 主内容区域
            // 添加底部导航栏的 padding，确保内容不会被导航栏遮挡
            Surface(
                modifier = Modifier
                    .padding(innerPadding)
                    // 背景先于状态栏留白绘制，透明系统栏下也有完整的主题底色。
                    .then(if (workspaceVisible) Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 1f)) else Modifier)
                    .then(if (currentScreen is Screen.AiChat) Modifier.statusBarsPadding() else Modifier)
                    .consumeWindowInsets(innerPadding)
                    .navigationBarsPadding()
                    .then(
                        if (currentScreenUsesImePadding) {
                            Modifier.imePadding()
                        } else {
                            Modifier
                        }
                    )
                    .fillMaxSize(),
                color =
                if (workspaceVisible) MaterialTheme.colorScheme.surface.copy(alpha = 1f)
                else if (hasBackgroundImage || hasBackgroundDecoration) Color.Transparent
                else MaterialTheme.colorScheme.background
            ) {
                if (isLoading) {
                    // 加载中状态
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.app_content_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // 主要内容 - 使用 Box 堆叠所有访问过的屏幕，保留状态
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 将当前屏幕的 Composable 缓存起来
                        if (!screenCache.containsKey(currentScreenKey)) {
                            val screenSnapshot = currentScreen
                            screenKeepAliveCache[currentScreenKey] = screenSnapshot.keepAlive
                            screenCache[currentScreenKey] = {
                                when (screenSnapshot) {
                                    is Screen.ToolPkgComposeDsl ->
                                        ToolPkgComposeDslToolScreen(
                                            navController = navController,
                                            routeInstanceId = currentScreenKey,
                                            containerPackageName = screenSnapshot.containerPackageName,
                                            uiModuleId = screenSnapshot.uiModuleId,
                                            fallbackTitle = screenSnapshot.title
                                        )
                                    is Screen.ToolPkgPluginConfig ->
                                        ToolPkgComposeDslToolScreen(
                                            navController = navController,
                                            routeInstanceId = currentScreenKey,
                                            containerPackageName = screenSnapshot.containerPackageName,
                                            uiModuleId = screenSnapshot.uiModuleId,
                                            fallbackTitle = screenSnapshot.title
                                        )
                                    else -> {
                                        val content: @Composable () -> Unit = {
                                            screenSnapshot.Content(
                                                navController = navController,
                                                navigateTo = onScreenChange,
                                                onGoBack = onGoBack,
                                                hasBackgroundImage = hasBackgroundImage,
                                                onLoading = onLoading,
                                                onError = onError,
                                                onGestureConsumed = if (screenSnapshot is Screen.AiChat) onGestureConsumed else { _ -> }
                                            )
                                        }
                                        if (screenSnapshot.usesRouteViewModelStore) {
                                            // 路由级 ViewModelStore（P1）：配置变化保留实例，
                                            // 路由出栈/替换/清栈时 store.clear() 触发 onCleared
                                            val routeOwner =
                                                routeViewModelStoreManager.ownerFor(currentScreenKey)
                                            CompositionLocalProvider(
                                                LocalViewModelStoreOwner provides routeOwner
                                            ) {
                                                content()
                                            }
                                        } else {
                                            content()
                                        }
                                    }
                                }
                            }
                        }

                        // 优化渲染：只渲染当前屏幕和正在过渡的上一个屏幕
                        // 使用稳定的状态机，避免同一 key 被重复触发转场
                        var lastObservedCurrentKey by remember { mutableStateOf(currentScreenKey) }
                        var lastObservedScreen by remember { mutableStateOf(currentScreen) }
                        var transitionFromKey by remember { mutableStateOf<String?>(null) }
                        var pendingRemovalKey by remember { mutableStateOf<String?>(null) }
                        var isTransitioning by remember { mutableStateOf(false) }
                        var transitionAllowsCrossfade by remember { mutableStateOf(true) }

                        val allowCrossfadeForActiveTransition =
                            when {
                                currentScreenKey != lastObservedCurrentKey ->
                                    enableNavigationAnimation &&
                                        lastObservedScreen.participatesInCrossfadeTransition &&
                                        currentScreen.participatesInCrossfadeTransition
                                isTransitioning -> transitionAllowsCrossfade
                                else -> true
                            }

                        val effectivePreviousKey =
                            when {
                                !allowCrossfadeForActiveTransition -> null
                                currentScreenKey != lastObservedCurrentKey -> lastObservedCurrentKey
                                isTransitioning -> transitionFromKey
                                else -> null
                            }

                        // 计算当前仍应存活的路由键：导航栈 + 仍渲染的 keepAlive 缓存 +
                        // 当前键（在转场完成时执行，避免清理仍在渲染的过渡页）
                        fun aliveRouteKeys(): Set<String> =
                            aliveScreenKeys +
                                screenKeepAliveCache.filterValues { it }.keys +
                                currentScreenKey

                        LaunchedEffect(currentScreenKey) {
                            val fromKey = lastObservedCurrentKey
                            if (currentScreenKey == fromKey) return@LaunchedEffect

                            val canCrossfade =
                                enableNavigationAnimation &&
                                    lastObservedScreen.participatesInCrossfadeTransition &&
                                    currentScreen.participatesInCrossfadeTransition
                            val removalKey = if (isNavigatingBack) fromKey else null

                            transitionAllowsCrossfade = canCrossfade
                            transitionFromKey = if (canCrossfade) fromKey else null
                            pendingRemovalKey = removalKey
                            isTransitioning = canCrossfade
                            lastObservedCurrentKey = currentScreenKey
                            lastObservedScreen = currentScreen

                            if (!canCrossfade) {
                                if (removalKey != null && removalKey != currentScreenKey) {
                                    screenCache.remove(removalKey)
                                    screenKeepAliveCache.remove(removalKey)
                                    screenStateHolder.removeState(removalKey)
                                }
                                // 路由已离开栈：清理其 ViewModelStore（pop/replace/清栈）
                                routeViewModelStoreManager.retainOnly(aliveRouteKeys())
                                pendingRemovalKey = null
                                return@LaunchedEffect
                            }

                            // 等待动画完成后停止过渡状态
                            val transitionDurationMillis =
                                if (!useTabletLayout &&
                                    navigationTransitionSource == NavigationTransitionSource.DRAWER &&
                                    !isNavigatingBack
                                ) {
                                    drawerRelayTransitionDurationMillis
                                } else {
                                    pageTransitionDurationMillis
                                }
                            kotlinx.coroutines.delay(transitionDurationMillis.toLong())

                            isTransitioning = false
                            transitionFromKey = null
                            transitionAllowsCrossfade = true
                            pendingRemovalKey?.let { keyToRemove ->
                                if (keyToRemove != currentScreenKey) {
                                    screenCache.remove(keyToRemove)
                                    screenKeepAliveCache.remove(keyToRemove)
                                    screenStateHolder.removeState(keyToRemove)
                                }
                            }
                            // 动画结束、旧路由已离开栈：清理其 ViewModelStore
                            routeViewModelStoreManager.retainOnly(aliveRouteKeys())
                            pendingRemovalKey = null
                        }

                        val renderKeys = buildList {
                            screenKeepAliveCache.forEach { (screenKey, keepAlive) ->
                                if (keepAlive && screenKey != currentScreenKey) {
                                    add(screenKey)
                                }
                            }
                            if (effectivePreviousKey != null && effectivePreviousKey != currentScreenKey) {
                                add(effectivePreviousKey)
                            }
                            add(currentScreenKey)
                        }.distinct()

                        renderKeys.forEach { screenKey ->
                            val screenContent = screenCache[screenKey] ?: return@forEach
                            val isCurrentScreen = screenKey == currentScreenKey
                            val isDrawerRelayTransition =
                                !useTabletLayout &&
                                    navigationTransitionSource == NavigationTransitionSource.DRAWER &&
                                    !isNavigatingBack &&
                                    allowCrossfadeForActiveTransition

                            key(screenKey) {
                                // 为每个屏幕维护一个独立的可见性状态
                                var visibility by remember { mutableStateOf(ScreenVisibility.HIDDEN) }

                                // 使用 LaunchedEffect 在 isCurrentScreen 状态变化后更新可见性
                                LaunchedEffect(isCurrentScreen) {
                                    visibility = if (isCurrentScreen) ScreenVisibility.VISIBLE else ScreenVisibility.HIDDEN
                                }

                                val transition = updateTransition(
                                    targetState = visibility,
                                    label = "ScreenVisibilityTransition"
                                )

                                val alpha by transition.animateFloat(
                                    transitionSpec = {
                                        tween(
                                            durationMillis =
                                                if (isDrawerRelayTransition) drawerRelayTransitionDurationMillis
                                                else pageTransitionDurationMillis,
                                            easing =
                                                if (isDrawerRelayTransition) {
                                                    if (targetState == ScreenVisibility.VISIBLE) {
                                                        LinearOutSlowInEasing
                                                    } else {
                                                        FastOutLinearInEasing
                                                    }
                                                } else if (enableNavigationAnimation) {
                                                    if (targetState == ScreenVisibility.VISIBLE) {
                                                        LinearOutSlowInEasing
                                                    } else {
                                                        FastOutLinearInEasing
                                                    }
                                                } else {
                                                    FastOutSlowInEasing
                                                }
                                        )
                                    },
                                    label = "ScreenAlphaAnimation"
                                ) { currentVisibility ->
                                    if (!allowCrossfadeForActiveTransition) {
                                        if (isCurrentScreen) 1f else 0f
                                    } else if (currentVisibility == ScreenVisibility.VISIBLE) {
                                        1f
                                    } else {
                                        0f
                                    }
                                }

                                val translationX by transition.animateFloat(
                                    transitionSpec = {
                                        tween(
                                            durationMillis =
                                                if (isDrawerRelayTransition) drawerRelayTransitionDurationMillis
                                                else pageTransitionDurationMillis,
                                            easing = FastOutSlowInEasing
                                        )
                                    },
                                    label = "ScreenTranslationXAnimation"
                                ) { currentVisibility ->
                                    if (!allowCrossfadeForActiveTransition) {
                                        0f
                                    } else if (isDrawerRelayTransition) {
                                        if (currentVisibility == ScreenVisibility.VISIBLE) {
                                            0f
                                        } else if (isCurrentScreen) {
                                            -drawerNavigationOffsetPx
                                        } else {
                                            drawerNavigationOffsetPx * 0.18f
                                        }
                                    } else if (!enableNavigationAnimation) {
                                        0f
                                    } else if (currentVisibility == ScreenVisibility.VISIBLE) {
                                        0f
                                    } else if (isCurrentScreen) {
                                        if (isNavigatingBack) -pageTransitionOffsetPx else pageTransitionOffsetPx
                                    } else {
                                        if (isNavigatingBack) pageTransitionOffsetPx * 0.45f
                                        else -pageTransitionOffsetPx * 0.45f
                                    }
                                }

                                val scale by transition.animateFloat(
                                    transitionSpec = {
                                        tween(
                                            durationMillis =
                                                if (isDrawerRelayTransition) drawerRelayTransitionDurationMillis
                                                else pageTransitionDurationMillis,
                                            easing = FastOutSlowInEasing
                                        )
                                    },
                                    label = "ScreenScaleAnimation"
                                ) { currentVisibility ->
                                    if (!allowCrossfadeForActiveTransition) {
                                        1f
                                    } else if (isDrawerRelayTransition) {
                                        if (currentVisibility == ScreenVisibility.VISIBLE) {
                                            1f
                                        } else if (isCurrentScreen) {
                                            0.975f
                                        } else {
                                            0.995f
                                        }
                                    } else if (!enableNavigationAnimation) {
                                        1f
                                    } else if (currentVisibility == ScreenVisibility.VISIBLE) {
                                        1f
                                    } else if (isCurrentScreen) {
                                        0.985f
                                    } else {
                                        0.992f
                                    }
                                }

                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .clipToBounds()
                                            // 保留页面组合状态，但隐藏页不放置，也不参与触摸命中。
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                layout(placeable.width, placeable.height) {
                                                    if (isCurrentScreen || screenKey == effectivePreviousKey) {
                                                        placeable.placeRelative(0, 0)
                                                    }
                                                }
                                            }
                                            .then(
                                                if (isCurrentScreen) Modifier else Modifier
                                                    .clearAndSetSemantics {}
                                                    .focusProperties { canFocus = false }
                                                    .pointerInput(Unit) {
                                                        awaitPointerEventScope {
                                                            while (true) {
                                                                awaitPointerEvent(PointerEventPass.Initial)
                                                                    .changes.forEach { it.consume() }
                                                            }
                                                        }
                                                    }
                                            )
                                            .zIndex(if (isCurrentScreen) 1f else 0f)
                                            .graphicsLayer {
                                                // 没有跨页转场时直接呈现当前页，避免复用页面沿用上次的透明度。
                                                val crossfading = effectivePreviousKey != null
                                                this.alpha = if (crossfading) alpha else if (isCurrentScreen) 1f else 0f
                                                this.translationX = if (crossfading) translationX else 0f
                                                scaleX = if (crossfading) scale else 1f
                                                scaleY = if (crossfading) scale else 1f
                                            }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        screenStateHolder.SaveableStateProvider(screenKey) {
                                            CompositionLocalProvider(
                                                LocalIsCurrentScreen provides isCurrentScreen,
                                                LocalRouteInstanceId provides screenKey,
                                                LocalSetScreenSoftInputMode provides { mode ->
                                                    if (isCurrentScreen && currentScreenSoftInputMode != mode) {
                                                        currentScreenSoftInputMode = mode
                                                    }
                                                },
                                                LocalSetUseScreenImePadding provides { enabled ->
                                                    if (isCurrentScreen && currentScreenUsesImePadding != enabled) {
                                                        currentScreenUsesImePadding = enabled
                                                    }
                                                }
                                            ) {
                                                screenContent()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 主聊天页使用透明控件行，标题和三个操作不再占用应用工具栏。
                        if (currentScreen is Screen.AiChat) {
                            MainChatControls(
                                modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 6.dp, vertical = 4.dp).zIndex(3f)
                            )
                        }

                        // 帧率计数器 - 放在所有屏幕之上
                        if (showFpsCounter) {
                            FpsCounter(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 80.dp, end = 16.dp)
                                    .zIndex(2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

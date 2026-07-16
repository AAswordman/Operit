package com.ai.assistance.operit.ui.main.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.common.displays.FpsCounter
import com.ai.assistance.operit.ui.main.NavigationTransitionSource
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.LocalRouteInstanceId
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.common.composedsl.ToolPkgComposeDslToolScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.ui.components.CustomScaffold
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
        titleContent: TopBarTitleContent? = null
) {
    // Get background image state
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
    val manifestSoftInputMode = remember(hostActivity) { hostActivity?.manifestSoftInputMode() }
    val density = LocalDensity.current
    val pageTransitionDurationMillis = if (enableNavigationAnimation) 280 else 400
    val drawerRelayTransitionDurationMillis = 320
    val pageTransitionOffsetPx =
        with(density) { if (useTabletLayout) 28.dp.toPx() else 20.dp.toPx() }
    val drawerNavigationOffsetPx =
        with(density) { if (useTabletLayout) 40.dp.toPx() else 30.dp.toPx() }
    ImeWakeListeningEffect(context = context, density = density)
    val preferencesManager = UserPreferencesManager.getInstance(context)
    val useBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
            preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

    // Get toolbar transparency setting
    val toolbarTransparent =
            preferencesManager.toolbarTransparent.collectAsState(initial = false).value
    
    // Get AppBar custom color settings
    val useCustomAppBarColor =
            preferencesManager.useCustomAppBarColor.collectAsState(initial = false).value
    val customAppBarColor =
            preferencesManager.customAppBarColor.collectAsState(initial = null).value

    // Get AppBar content color settings
    val forceAppBarContentColor =
            preferencesManager.forceAppBarContentColor.collectAsState(initial = false).value
    val appBarContentColorMode =
            preferencesManager.appBarContentColorMode.collectAsState(
                            initial = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT
                    )
                    .value

    val appBarContentColor =
            if (forceAppBarContentColor) {
                when (appBarContentColorMode) {
                    UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT -> Color.White
                    UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK -> Color.Black
                    else -> MaterialTheme.colorScheme.onPrimary
                }
            } else {
                MaterialTheme.colorScheme.onPrimary
            }

    // 获取聊天历史管理器
    val chatHistoryManager = ChatHistoryManager.getInstance(context)
    val currentChatId = chatHistoryManager.currentChatIdFlow.collectAsState(initial = null).value
    val chatHistories =
            chatHistoryManager.chatHistoriesFlow.collectAsState(initial = emptyList()).value

    // Get custom chat title from preferences
    val customChatTitle by preferencesManager.customChatTitle.collectAsState(initial = null)


    // 当前聊天标题
    val currentChatTitle =
            if (currentChatId != null) {
                chatHistories.find { it.id == currentChatId }?.title ?: ""
            } else {
                ""
            }
    // 屏幕缓存 Map - 保存已访问过的屏幕，使其状态得以保留
    val screenCache = remember { mutableStateMapOf<String, @Composable () -> Unit>() }
    val screenKeepAliveCache = remember { mutableStateMapOf<String, Boolean>() }
    val screenStateHolder = rememberSaveableStateHolder()
    val currentScreenKey =
        remember(currentRouteEntry.instanceId, currentScreen) {
            if (currentScreen.keepAlive) {
                currentScreen.stableScreenKey() ?: currentRouteEntry.instanceId
            } else {
                currentRouteEntry.instanceId
            }
        }
    var currentScreenSoftInputMode by remember(currentScreenKey) { mutableStateOf<Int?>(null) }
    var currentScreenUsesImePadding by remember(currentScreenKey) { mutableStateOf(false) }
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
                // 单一工具栏 - 使用小型化的设计
                // 使用 windowInsets 参数让 TopAppBar 自己处理状态栏的 insets
                TopAppBar(
                    windowInsets = WindowInsets.statusBars,
                    title = {
                        if (titleContent != null) {
                            titleContent.content()
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 使用Screen的标题或导航项的标题
                                Text(
                                    text =
                                    when {
                                        // 如果是AI对话界面且有自定义标题，则优先显示
                                        currentScreen is Screen.AiChat && !customChatTitle.isNullOrEmpty() ->
                                            customChatTitle!!
                                        // 优先使用Screen的标题
                                        currentScreen.getTitle().isNotBlank() ->
                                            currentScreen.getTitle()
                                        // 回退到导航项的标题资源
                                        selectedItem?.titleResId != null &&
                                            selectedItem.titleResId != 0 ->
                                            stringResource(id = selectedItem.titleResId)
                                        // 最后的默认值
                                        else -> ""
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = appBarContentColor
                                )

                                // 显示当前聊天标题（仅在AI对话页面)
                                if (currentScreen is Screen.AiChat && currentChatTitle.isNotBlank()) {
                                    Text(
                                        text = "- $currentChatTitle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appBarContentColor.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        // 导航按钮逻辑
                        IconButton(
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
                            }
                        ) {
                            Icon(
                                if (canGoBack) Icons.Default.ArrowBack
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
                        containerColor =
                        when {
                            toolbarTransparent -> Color.Transparent
                            useCustomAppBarColor && customAppBarColor != null -> Color(customAppBarColor)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        titleContentColor = appBarContentColor,
                        navigationIconContentColor = appBarContentColor,
                        actionIconContentColor = appBarContentColor
                    ),
                    // Scaffold会处理 insets, 这里不再需要手动添加 modifier
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            // 主内容区域
            // 添加底部导航栏的 padding，确保内容不会被导航栏遮挡
            Surface(
                modifier = Modifier
                    .padding(innerPadding)
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
                if (hasBackgroundImage) Color.Transparent
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
                                    else ->
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

                        LaunchedEffect(currentScreenKey) {
                            val fromKey = lastObservedCurrentKey
                            if (currentScreenKey == fromKey) return@LaunchedEffect

                            val canCrossfade =
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
                                        1f
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
                                            .zIndex(if (isCurrentScreen) 1f else 0f)
                                            .graphicsLayer {
                                                this.alpha = alpha
                                                this.translationX = translationX
                                                scaleX = scale
                                                scaleY = scale
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

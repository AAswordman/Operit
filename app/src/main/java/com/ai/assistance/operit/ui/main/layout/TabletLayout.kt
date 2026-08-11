package com.ai.assistance.operit.ui.main.layout

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.NavigationTransitionSource
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.components.CollapsedDrawerContent
import com.ai.assistance.operit.ui.main.components.DrawerContent
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.RowScope

/** Layout for tablet devices with a permanent side navigation drawer */
@Composable
fun TabletLayout(
        currentRouteEntry: RouteEntry,
        currentScreen: Screen,
        selectedItem: NavItem?,
        isTabletSidebarExpanded: Boolean,
        isLoading: Boolean,
        navItems: List<NavItem>,
        pluginSidebarEntries: List<NavigationEntrySpec>,
        selectedRouteId: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        navController: androidx.navigation.NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        enableNavigationAnimation: Boolean,
        navigationTransitionSource: NavigationTransitionSource,
        tabletSidebarWidth: androidx.compose.ui.unit.Dp,
        collapsedTabletSidebarWidth: androidx.compose.ui.unit.Dp,
        onScreenChange: (Screen) -> Unit,
        onDrawerItemSelected: (Screen) -> Unit,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit,
        onToggleSidebar: () -> Unit,
        navigateToTokenConfig: () -> Unit,
        canGoBack: Boolean,
        onGoBack: () -> Unit,
        isNavigatingBack: Boolean = false,
        topBarActions: @Composable RowScope.() -> Unit = {},
        topBarTitleContent: TopBarTitleContent? = null
) {
        val drawerAppearance = rememberNavigationDrawerAppearance()
        val sidebarWidthAnimationDurationMillis = 280
        val sidebarContentFadeDurationMillis = 160
        var isSidebarWidthExpanded by remember { mutableStateOf(isTabletSidebarExpanded) }
        var isSidebarContentExpanded by remember { mutableStateOf(isTabletSidebarExpanded) }

        LaunchedEffect(isTabletSidebarExpanded) {
                if (isTabletSidebarExpanded) {
                        isSidebarWidthExpanded = true
                        kotlinx.coroutines.delay(sidebarWidthAnimationDurationMillis.toLong())
                        isSidebarContentExpanded = true
                } else {
                        isSidebarContentExpanded = false
                        kotlinx.coroutines.delay(sidebarContentFadeDurationMillis.toLong())
                        isSidebarWidthExpanded = false
                }
        }

        // 计算侧边栏的动画宽度，轻微调整动画时间为280ms，保持原有效果但稍快
        val animatedSidebarWidth by
                animateDpAsState(
                        targetValue =
                                if (isSidebarWidthExpanded) tabletSidebarWidth
                                else collapsedTabletSidebarWidth,
                        animationSpec = tween(durationMillis = sidebarWidthAnimationDurationMillis),
                        label = "sidebarWidth"
                )

        // 使用Box作为顶层容器，这样可以允许子元素重叠
        Box(modifier = Modifier.fillMaxSize()) {
                // 计算主内容区域的宽度（屏幕宽度减去侧边栏宽度），轻微调整动画时间
                val contentWidth by
                        animateDpAsState(
                                targetValue =
                                        if (isSidebarWidthExpanded)
                                                androidx.compose.ui.platform.LocalConfiguration
                                                        .current
                                                        .screenWidthDp
                                                        .dp - tabletSidebarWidth + 1.dp // 增加1dp解决右侧白线问题
                                        else
                                                androidx.compose.ui.platform.LocalConfiguration
                                                        .current
                                                        .screenWidthDp
                                                        .dp - collapsedTabletSidebarWidth + 1.dp, // 增加1dp解决右侧白线问题
                                animationSpec = tween(durationMillis = sidebarWidthAnimationDurationMillis),
                                label = "contentWidth"
                        )

                // 侧边栏区域，使用动画宽度，无圆角以完全遮住背景
                Surface(
                        modifier =
                                Modifier.width(animatedSidebarWidth)
                                        .fillMaxHeight()
                                        .zIndex(2f), // 确保侧边栏在主内容之上
                        shadowElevation = 4.dp,
                        color = drawerAppearance.containerColor
                ) {
                        Crossfade(
                                targetState = isSidebarContentExpanded,
                                animationSpec = tween(durationMillis = sidebarContentFadeDurationMillis),
                                label = "sidebarContent"
                        ) { isContentExpanded ->
                                if (isContentExpanded) {
                                        DrawerContent(
                                                navItems = navItems,
                                                pluginEntries = pluginSidebarEntries,
                                                selectedItem = selectedItem,
                                                selectedRouteId = selectedRouteId,
                                                isNetworkAvailable = isNetworkAvailable,
                                                networkType = networkType,
                                                appearance = drawerAppearance,
                                                scope = scope,
                                                drawerState = drawerState,
                                                onScreenSelected = onDrawerItemSelected,
                                                onNavigationEntrySelected = onNavigationEntrySelected
                                        )
                                } else {
                                        CollapsedDrawerContent(
                                                navItems = navItems,
                                                pluginEntries = pluginSidebarEntries,
                                                selectedItem = selectedItem,
                                                selectedRouteId = selectedRouteId,
                                                isNetworkAvailable = isNetworkAvailable,
                                                appearance = drawerAppearance,
                                                onScreenSelected = onDrawerItemSelected,
                                                onNavigationEntrySelected = onNavigationEntrySelected
                                        )
                                }
                        }
                }

                // 主内容区域 - 使用width+offset方式，保留稳定的布局
                Box(
                        modifier =
                                Modifier.width(contentWidth)
                                        .fillMaxHeight()
                                        .offset(x = animatedSidebarWidth)
                                        .zIndex(1f)
                ) {
                        AppContent(
                                currentRouteEntry = currentRouteEntry,
                                currentScreen = currentScreen,
                                selectedItem = selectedItem,
                                useTabletLayout = true,
                                isTabletSidebarExpanded = isTabletSidebarExpanded,
                                isLoading = isLoading,
                                navController = navController,
                                scope = scope,
                                drawerState = drawerState,
                                showFpsCounter = showFpsCounter,
                                enableNavigationAnimation = enableNavigationAnimation,
                                navigationTransitionSource = navigationTransitionSource,
                                onScreenChange = onScreenChange,
                                onToggleSidebar = onToggleSidebar,
                                navigateToTokenConfig = navigateToTokenConfig,
                                onGestureConsumed = { consumed ->
                                        // 当聊天页面的手势被消费时，这里不需要特别处理
                                        // 因为平板模式不像手机模式那样有侧滑抽屉
                                },
                                canGoBack = canGoBack,
                                onGoBack = onGoBack,
                                isNavigatingBack = isNavigatingBack,
                                actions = topBarActions,
                                titleContent = topBarTitleContent
                        )
                }
        }
}

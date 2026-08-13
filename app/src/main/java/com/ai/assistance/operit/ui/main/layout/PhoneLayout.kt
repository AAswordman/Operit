package com.ai.assistance.operit.ui.main.layout

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.NavigationTransitionSource
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.components.DrawerContent
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Layout for phone devices with a modal navigation drawer */
@Composable
fun PhoneLayout(
        currentRouteEntry: RouteEntry,
        currentScreen: Screen,
        selectedItem: NavItem?,
        isLoading: Boolean,
        navItems: List<NavItem>,
        pluginSidebarEntries: List<NavigationEntrySpec>,
        selectedRouteId: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        drawerWidth: Dp,
        navController: androidx.navigation.NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        enableNavigationAnimation: Boolean,
        navigationTransitionSource: NavigationTransitionSource,
        onScreenChange: (Screen) -> Unit,
        onDrawerItemSelected: (Screen) -> Unit,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit,
        navigateToTokenConfig: () -> Unit,
        canGoBack: Boolean,
        onGoBack: () -> Unit,
        isNavigatingBack: Boolean = false,
        topBarActions: @Composable RowScope.() -> Unit = {},
        topBarTitleContent: TopBarTitleContent? = null
) {
        // 使用 updateTransition 创建顺滑的无弹抽屉动画
        val transition = updateTransition(drawerState.targetValue, label = "drawer_transition")
        val drawerTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val drawerProgress by
                transition.animateFloat(
                        label = "drawerProgress",
                        transitionSpec = {
                                spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                )
                        }
                ) { state -> if (state == DrawerValue.Open) 1f else 0f }
        val isDrawerOpen =
                drawerState.currentValue == DrawerValue.Open ||
                        drawerState.targetValue == DrawerValue.Open
        // 极简动画：仅保留水平平移和轻微阴影
        val contentTranslationX = drawerWidth * (0.82f * drawerProgress)
        val contentShadowElevation = 12.dp * drawerProgress
        val drawerOffset = -drawerWidth * (1f - drawerProgress)
        val scrimColor = Color.Black.copy(alpha = 0.32f * drawerProgress)
        val drawerAppearance = rememberNavigationDrawerAppearance()
        val drawerShape =
                MaterialTheme.shapes.medium.copy(
                        topEnd = CornerSize(20.dp),
                        bottomEnd = CornerSize(20.dp),
                        topStart = CornerSize(0.dp),
                        bottomStart = CornerSize(0.dp)
                )
        // 侧边栏相关拖拽状态
        var currentDrag by remember { mutableStateOf(0f) }
        var verticalDrag by remember { mutableStateOf(0f) }
        val dragThreshold = 40f
        val draggableState = rememberDraggableState { delta ->
                if (!GestureStateHolder.isChatScreenGestureConsumed) {
                        currentDrag += delta
                        if (!isDrawerOpen &&
                                currentDrag > dragThreshold &&
                                Math.abs(currentDrag) > Math.abs(verticalDrag)
                        ) {
                                scope.launch {
                                        drawerState.open()
                                        currentDrag = 0f
                                        verticalDrag = 0f
                                }
                        }
                        if (isDrawerOpen && currentDrag < -dragThreshold) {
                                scope.launch {
                                        drawerState.close()
                                        currentDrag = 0f
                                        verticalDrag = 0f
                                }
                        }
                }
        }
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .draggable(
                                        state = draggableState,
                                        orientation = Orientation.Horizontal,
                                        onDragStarted = {
                                                currentDrag = 0f
                                                verticalDrag = 0f
                                        },
                                        onDragStopped = {
                                                currentDrag = 0f
                                                verticalDrag = 0f
                                        }
                                )
                                .draggable(
                                        state =
                                                rememberDraggableState { delta ->
                                                        verticalDrag += delta
                                                },
                                        orientation = Orientation.Vertical,
                                        onDragStarted = {},
                                        onDragStopped = {}
                                )
        ) {
                // 主内容区域 - 仅平移，不旋转不缩放
                Surface(
                    modifier =
                            Modifier.fillMaxSize()
                                    .graphicsLayer {
                                            translationX = contentTranslationX.toPx()
                                    }
                                    .zIndex(1f),
                    shape = RoundedCornerShape(0.dp),
                    color = Color.Transparent,
                    shadowElevation = contentShadowElevation
                ) {
                    AppContent(
                        currentRouteEntry = currentRouteEntry,
                        currentScreen = currentScreen,
                        selectedItem = selectedItem,
                        useTabletLayout = false,
                        isTabletSidebarExpanded = false,
                        isLoading = isLoading,
                        navController = navController,
                        scope = scope,
                        drawerState = drawerState,
                        showFpsCounter = showFpsCounter,
                        enableNavigationAnimation = enableNavigationAnimation,
                        navigationTransitionSource = navigationTransitionSource,
                        onScreenChange = onScreenChange,
                        onToggleSidebar = {},
                        navigateToTokenConfig = navigateToTokenConfig,
                        canGoBack = canGoBack,
                        onGoBack = onGoBack,
                        isNavigatingBack = isNavigatingBack,
                        actions = topBarActions,
                        titleContent = topBarTitleContent
                    )
                }
                // 抽屉内容 - 仅平移，不缩放不透明度动画
                Surface(
                        modifier =
                                Modifier.width(drawerWidth)
                                        .padding(top = drawerTopInset)
                                        .fillMaxHeight()
                                        .graphicsLayer {
                                                translationX = drawerOffset.toPx()
                                        }
                                        .zIndex(2f),
                        shape = drawerShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 0.dp
                ) {
                        DrawerContent(
                                navItems = navItems,
                                pluginEntries = pluginSidebarEntries,
                                selectedItem = selectedItem,
                                selectedRouteId = selectedRouteId,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                appearance = drawerAppearance,
                                topContentPadding = 0.dp,
                                scope = scope,
                                drawerState = drawerState,
                                onScreenSelected = onDrawerItemSelected,
                                onNavigationEntrySelected = onNavigationEntrySelected
                        )
                }
                // 遮罩层 - 柔和暗色 scrim
                if (isDrawerOpen) {
                        Box(
                                modifier = Modifier.fillMaxSize().zIndex(1.5f)
                        ) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .padding(start = drawerWidth)
                                                        .background(scrimColor)
                                                        .clickable(
                                                                interactionSource =
                                                                        remember {
                                                                                MutableInteractionSource()
                                                                        },
                                                                indication = null,
                                                                onClick = {
                                                                        scope.launch {
                                                                                drawerState.close()
                                                                        }
                                                                }
                                                        )
                                )
                        }
                }
        }
}

package com.ai.assistance.operit.ui.floating.ui.window.screen

import android.annotation.SuppressLint
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.features.chat.components.ChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.chat.components.ScrollToBottomButton
import com.ai.assistance.operit.ui.features.chat.components.rememberChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.CursorStyleChatMessage
import com.ai.assistance.operit.ui.floating.ui.window.components.*
import com.ai.assistance.operit.ui.floating.ui.window.models.*
import com.ai.assistance.operit.ui.floating.ui.window.viewmodel.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.plugins.chatview.ChatViewEvent
import com.ai.assistance.operit.plugins.chatview.ChatViewHookParams
import com.ai.assistance.operit.plugins.chatview.ChatViewHookPluginRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** 渲染悬浮窗的窗口模式界面 - 简化版 */
@Composable
fun FloatingChatWindowMode(floatContext: FloatContext) {
    val viewModel = rememberFloatingChatWindowModeViewModel(floatContext)
    ReportFloatingChatViewEffect(floatContext)

    LaunchedEffect(
        floatContext.windowWidthState,
        floatContext.windowHeightState,
        floatContext.windowScale
    ) {
        viewModel.syncWindowState()
    }

    FloatingChatWindowContent(floatContext, viewModel)
}

@Composable
private fun ReportFloatingChatViewEffect(floatContext: FloatContext) {
    val service = floatContext.chatService ?: return
    val chatCore = remember(service) { service.getChatCore() }
    val chatHistories = chatCore.chatHistories.collectAsState(initial = emptyList()).value
    val currentChatId = chatCore.currentChatId.collectAsState(initial = null).value
    val currentChat = remember(chatHistories, currentChatId) {
        chatHistories.find { it.id == currentChatId }
    }
    val viewId = rememberSaveable { UUID.randomUUID().toString() }
    val latestChatViewParams by rememberUpdatedState(
        ChatViewHookParams(
            context = service,
            viewId = viewId,
            chatId = currentChatId,
            workspacePath = currentChat?.workspace,
            workspaceEnv = currentChat?.workspaceEnv,
            runtime = "floating",
            title = currentChat?.title
        )
    )
    var hasDispatchedOpen by remember(viewId) { mutableStateOf(false) }
    LaunchedEffect(
        viewId,
        currentChatId,
        currentChat?.workspace,
        currentChat?.workspaceEnv,
        currentChat?.title
    ) {
        val event =
            if (hasDispatchedOpen) {
                ChatViewEvent.VIEW_UPDATED
            } else {
                hasDispatchedOpen = true
                ChatViewEvent.VIEW_OPENED
            }
        ChatViewHookPluginRegistry.dispatchAsync(event, latestChatViewParams)
    }
    DisposableEffect(viewId) {
        onDispose {
            ChatViewHookPluginRegistry.dispatchAsync(
                ChatViewEvent.VIEW_CLOSED,
                latestChatViewParams
            )
        }
    }
}

/** 主窗口内容 */
@Composable
private fun FloatingChatWindowContent(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    val density = LocalDensity.current
    val cornerRadius = 12.dp
    val borderThickness = 3.dp
    val edgeHighlightColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    var showRecentChatSelector by remember { mutableStateOf(false) }

    Layout(
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                MainWindowBox(
                    floatContext = floatContext,
                    viewModel = viewModel,
                    cornerRadius = cornerRadius,
                    borderThickness = borderThickness,
                    edgeHighlightColor = edgeHighlightColor,
                    backgroundColor = backgroundColor,
                    showRecentChatSelector = showRecentChatSelector,
                    onToggleRecentChatSelector = { showRecentChatSelector = !showRecentChatSelector }
                )
            }
        },
        modifier = Modifier.graphicsLayer { alpha = floatContext.animatedAlpha.value }
    ) { measurables, _ ->
        val widthInPx = with(density) { viewModel.windowState.width.toPx() }
        val heightInPx = with(density) { viewModel.windowState.height.toPx() }
        val scale = viewModel.windowState.scale

        val placeable = measurables.first().measure(
            androidx.compose.ui.unit.Constraints.fixed(
                width = widthInPx.roundToInt(),
                height = heightInPx.roundToInt()
            )
        )

        layout(
            width = (widthInPx * scale).roundToInt(),
            height = (heightInPx * scale).roundToInt()
        ) {
            placeable.placeRelativeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = floatContext.animatedAlpha.value
            }
        }
    }
}

@Composable
private fun RecentChatSelectorOverlay(
    floatContext: FloatContext,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val chatCore = remember(floatContext.chatService) {
        floatContext.chatService?.getChatCore()
    }
    val chatHistoriesState = chatCore?.chatHistories?.collectAsState(initial = emptyList())
    val currentChatIdState = chatCore?.currentChatId?.collectAsState(initial = null)
    val chatHistories = chatHistoriesState?.value ?: emptyList()
    val currentChatId = currentChatIdState?.value
    val context = floatContext.chatService ?: return
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val ungroupedText = stringResource(R.string.ungrouped)
    val items = remember(chatHistories) {
        chatHistories.sortedByDescending { it.updatedAt }.take(20)
    }

    val avatarUriMap = remember { mutableStateMapOf<String, String?>() }
    LaunchedEffect(items) {
        items.forEach { history ->
            val characterName = history.characterCardName?.takeIf { it.isNotBlank() }
            if (characterName != null && !avatarUriMap.containsKey(history.id)) {
                coroutineScope.launch {
                    val card = characterCardManager.findCharacterCardByName(characterName)
                    val uri = card?.id?.let { id ->
                        userPreferencesManager.getAiAvatarForCharacterCardFlow(id).first()
                    }
                    avatarUriMap[history.id] = uri
                }
            } else if (!avatarUriMap.containsKey(history.id)) {
                avatarUriMap[history.id] = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.chat_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items) { history ->
                        val isActive = history.id == currentChatId
                        val avatarUri = avatarUriMap[history.id]
                        val groupText = history.group?.takeIf { it.isNotBlank() } ?: ungroupedText
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isActive)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else
                                        Color.Transparent
                                )
                                .clickable {
                                    chatCore?.switchChatLocal(history.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = history.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = groupText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 主窗口盒子 */
@Composable
private fun MainWindowBox(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    cornerRadius: Dp,
    borderThickness: Dp,
    edgeHighlightColor: Color,
    backgroundColor: Color,
    showRecentChatSelector: Boolean,
    onToggleRecentChatSelector: () -> Unit
) {
    var isResizingHeight by remember { mutableStateOf(false) }
    var isScaling by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(8.dp, RoundedCornerShape(cornerRadius))
            .border(
                width = borderThickness,
                color = if (floatContext.isEdgeResizing || isResizingHeight || isScaling) edgeHighlightColor else Color.Transparent,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CloseButtonEffect(floatContext, viewModel)
            TitleBar(floatContext, viewModel, onToggleRecentChatSelector)
            ChatContentArea(floatContext, viewModel)
            ProcessingStatusIndicator(floatContext)
            FloatingChatWindowInputControls(floatContext, viewModel)
        }

        RecentChatSelectorOverlay(
            floatContext = floatContext,
            visible = showRecentChatSelector,
            onDismiss = onToggleRecentChatSelector
        )

        // 底部高度调整分隔线
        BottomResizeHandle(
            floatContext = floatContext,
            viewModel = viewModel,
            onResizingChange = { isResizingHeight = it }
        )

        // 左右边框缩放手柄
        if (!floatContext.showInputDialog) {
            RightEdgeScaleHandle(
                floatContext = floatContext,
                viewModel = viewModel,
                onScalingChange = { isScaling = it }
            )
        }

        // 四个角落的缩放手柄
        if (!floatContext.showInputDialog) {
            CornerScaleHandle(
                floatContext = floatContext,
                viewModel = viewModel,
                alignment = Alignment.BottomEnd,
                onScalingChange = { isScaling = it }
            )
        }
    }
}

/** 关闭按钮效果 */
@Composable
private fun CloseButtonEffect(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    LaunchedEffect(viewModel.closeButtonPressed) {
        if (viewModel.closeButtonPressed) {
            floatContext.animatedAlpha.animateTo(0f, animationSpec = tween(200))
            floatContext.onClose()
            viewModel.closeButtonPressed = false
        }
    }
}

/** 标题栏 */
@Composable
private fun TitleBar(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    onToggleRecentChatSelector: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (viewModel.titleBarHover) 0.3f else 0.2f
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        viewModel.titleBarHover = event.changes.any { it.pressed }
                    }
                }
            }
    ) {
        if (floatContext.contentVisible) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TitleBarButton(
                        icon = Icons.Default.History,
                        description = stringResource(R.string.chat_history),
                        onClick = onToggleRecentChatSelector
                    )
                    TitleBarButton(
                        icon = Icons.Default.Fullscreen,
                        description = stringResource(R.string.floating_fullscreen),
                        onClick = { floatContext.onModeChange(FloatingMode.FULLSCREEN) }
                    )
                    MinimizeButton(viewModel, primaryColor) {
                        floatContext.onModeChange(FloatingMode.BALL)
                    }
                }

                // 中间可拖动区域（只占用中间空间，避免覆盖左右按钮点击）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { viewModel.startDragging() },
                                onDragEnd = {
                                    viewModel.endDragging()
                                    floatContext.saveWindowState?.invoke()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    viewModel.handleMove(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                )

                // 右侧按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 返回主应用按钮
                    TitleBarButton(
                        icon = Icons.Default.Home,
                        description = stringResource(R.string.floating_back_to_main),
                        onClick = {
                            // 启动 MainActivity 返回主应用
                            try {
                                val context = floatContext.chatService
                                if (context != null) {
                                    runBlocking {
                                        try {
                                            context.getChatCore().syncCurrentChatIdToGlobal()
                                        } catch (_: Exception) {
                                        }
                                    }
                                    val intent = Intent(
                                        context,
                                        com.ai.assistance.operit.ui.main.MainActivity::class.java
                                    ).apply {
                                        flags =
                                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                AppLogger.e("FloatingChatWindow", "启动 MainActivity 失败", e)
                            }
                            // 然后关闭悬浮窗
                            floatContext.onClose()
                        }
                    )
                    // 关闭按钮
                    CloseButton(
                        viewModel = viewModel,
                        errorColor = errorColor,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    ) {
                        viewModel.closeButtonPressed = true
                    }
                }
            }
        }
    }
}

/** 标题栏按钮 */
@Composable
private fun TitleBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 最小化按钮 */
@Composable
private fun MinimizeButton(
    viewModel: FloatingChatWindowModeViewModel,
    primaryColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(30.dp)
            .background(
                color = if (viewModel.minimizeHover) primaryColor.copy(alpha = 0.1f) else Color.Transparent,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        viewModel.minimizeHover = event.changes.any { it.pressed }
                    }
                }
            }
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.floating_minimize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 关闭按钮 */
@Composable
private fun CloseButton(
    viewModel: FloatingChatWindowModeViewModel,
    errorColor: Color,
    onSurfaceVariantColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(30.dp)
            .background(
                color = if (viewModel.closeHover) errorColor.copy(alpha = 0.1f) else Color.Transparent,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        viewModel.closeHover = event.changes.any { it.pressed }
                    }
                }
            }
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.floating_close),
            tint = if (viewModel.closeHover) errorColor else onSurfaceVariantColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 聊天内容区域 */
@Composable
private fun ColumnScope.ChatContentArea(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    floatContext.onInputFocusRequest?.invoke(false)
                }
            }
    ) {
        if (!floatContext.showInputDialog) {
            ChatMessagesView(floatContext, viewModel)
        } else {
            InputDialogView(floatContext, viewModel)
        }
    }
}

/** 聊天消息视图 */
@SuppressLint("SuspiciousIndentation")
@Composable
private fun ChatMessagesView(
    floatContext: FloatContext,
    _viewModel: FloatingChatWindowModeViewModel
) {
    val chatCore = remember(floatContext.chatService) {
        floatContext.chatService?.getChatCore()
    }
    val currentChatIdState =
        chatCore?.currentChatId?.collectAsState(initial = null)
            ?: remember { mutableStateOf<String?>(null) }
    val hasOlderDisplayHistoryState =
        chatCore?.currentChatHasOlderDisplayHistory?.collectAsState(initial = false)
            ?: remember { mutableStateOf(false) }
    val hasNewerDisplayHistoryState =
        chatCore?.currentChatHasNewerDisplayHistory?.collectAsState(initial = false)
            ?: remember { mutableStateOf(false) }
    val isLoadingDisplayWindowState =
        chatCore?.currentChatIsLoadingDisplayWindow?.collectAsState(initial = false)
            ?: remember { mutableStateOf(false) }
    val currentChatId = currentChatIdState.value
    val hasOlderDisplayHistory = hasOlderDisplayHistoryState.value
    val hasNewerDisplayHistory = hasNewerDisplayHistoryState.value
    val isLoadingDisplayWindow = isLoadingDisplayWindowState.value
    val scrollState = rememberLazyListState()
    val messagesCount = floatContext.messages.size
    val displayMessages =
        floatContext.messages
            .filter { it.sender != "think" }
            .asReversed()
    val messageHeightMemory = rememberChatMessageHeightMemory(displayMessages)
    val coroutineScope = rememberCoroutineScope()
    val lastMessage = floatContext.messages.lastOrNull()
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 滚动状态
    var autoScrollToBottom by remember { mutableStateOf(true) }
    val onAutoScrollToBottomChange = remember { { it: Boolean -> autoScrollToBottom = it } }

    LaunchedEffect(
        messagesCount,
        floatContext.messages.firstOrNull()?.timestamp,
        floatContext.messages.lastOrNull()?.timestamp,
        autoScrollToBottom,
        hasNewerDisplayHistory,
        isLoadingDisplayWindow,
        lastMessage?.content?.length,
    ) {
        if (autoScrollToBottom && hasNewerDisplayHistory && !isLoadingDisplayWindow && chatCore != null) {
            chatCore.showLatestMessagesForCurrentChat()
        } else if (floatContext.messages.isNotEmpty() && autoScrollToBottom) {
            scrollState.animateScrollToItem(0)
        }
    }

    val inputProcessingState = floatContext.inputProcessingState.value
    val isLoading =
        inputProcessingState !is InputProcessingState.Idle &&
            inputProcessingState !is InputProcessingState.Completed
    val showLoadingIndicator =
        isLoading &&
            (
                lastMessage?.sender == "user" ||
                    (lastMessage?.sender == "ai" && lastMessage.content.isBlank())
            )
    val loadMoreText = stringResource(id = R.string.load_more_history)
    val loadNewerText = stringResource(id = R.string.load_newer_history)
    val loadMoreTextStyle = MaterialTheme.typography.bodyMedium
    val renderItems = buildList<Any> {
        if (hasNewerDisplayHistory) {
            add(FloatingLoadNewerItem)
        }
        if (showLoadingIndicator) {
            add(FloatingLoadingItem)
        }
        addAll(displayMessages)
        if (hasOlderDisplayHistory) {
            add(FloatingLoadOlderItem)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = scrollState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            itemsIndexed(
                items = renderItems,
                key = { renderIndex, item ->
                    when (item) {
                        is com.ai.assistance.operit.data.model.ChatMessage -> "${renderIndex}_${item.timestamp}"
                        FloatingLoadOlderItem -> "floating_load_older_history"
                        FloatingLoadNewerItem -> "floating_load_newer_history"
                        FloatingLoadingItem -> "floating_loading_indicator"
                        else -> item.hashCode()
                    }
                },
            ) { renderIndex, item ->
                when (item) {
                    FloatingLoadOlderItem -> {
                        Text(
                            text = loadMoreText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (
                                        hasOlderDisplayHistory &&
                                        !isLoadingDisplayWindow &&
                                        chatCore != null
                                    ) {
                                        chatCore.loadOlderMessagesForCurrentChat()
                                    }
                                }
                                .padding(vertical = 16.dp),
                            style = loadMoreTextStyle,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    FloatingLoadNewerItem -> {
                        Text(
                            text = loadNewerText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (
                                        hasNewerDisplayHistory &&
                                        !isLoadingDisplayWindow &&
                                        chatCore != null
                                    ) {
                                        chatCore.loadNewerMessagesForCurrentChat()
                                    }
                                }
                                .padding(vertical = 16.dp),
                            style = loadMoreTextStyle,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    is ChatMessage -> {
                        val messageListOffset =
                            (if (hasNewerDisplayHistory) 1 else 0) +
                                (if (showLoadingIndicator) 1 else 0)
                        val displayIndex = renderIndex - messageListOffset
                        val actualIndex = messagesCount - 1 - displayIndex

                        FloatingMessageItem(
                            index = actualIndex,
                            message = item,
                            userMessageColor = userMessageColor,
                            aiMessageColor = aiMessageColor,
                            userTextColor = userTextColor,
                            aiTextColor = aiTextColor,
                            systemMessageColor = systemMessageColor,
                            systemTextColor = systemTextColor,
                            thinkingBackgroundColor = thinkingBackgroundColor,
                            thinkingTextColor = thinkingTextColor,
                            heightMemory = messageHeightMemory,
                            onSelectMessageToEdit = null,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    FloatingLoadingItem -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 0.dp)
                        ) {
                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                LoadingDotsIndicator(MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }

        // 滚动到底部按钮
        ScrollToBottomButton(
            scrollState = scrollState,
            coroutineScope = coroutineScope,
            autoScrollToBottom = autoScrollToBottom,
            hasNewerDisplayHistory = hasNewerDisplayHistory,
            onRequestLatestMessages = {
                chatCore?.showLatestMessagesForCurrentChat()
            },
            reverseLayout = true,
            onAutoScrollToBottomChange = onAutoScrollToBottomChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

private data object FloatingLoadOlderItem

private data object FloatingLoadNewerItem

private data object FloatingLoadingItem

@Composable
private fun FloatingMessageItem(
    index: Int,
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    heightMemory: ChatMessageHeightMemory,
    onSelectMessageToEdit: ((Int, ChatMessage, String) -> Unit)?,
) {
    CursorStyleChatMessage(
        message = message,
        userMessageColor = userMessageColor,
        aiMessageColor = aiMessageColor,
        userTextColor = userTextColor,
        aiTextColor = aiTextColor,
        systemMessageColor = systemMessageColor,
        systemTextColor = systemTextColor,
        thinkingBackgroundColor = thinkingBackgroundColor,
        thinkingTextColor = thinkingTextColor,
        heightMemory = heightMemory,
        index = index,
        enableDialogs = false,
        onEditSummary = { summaryMessage ->
            onSelectMessageToEdit?.invoke(index, summaryMessage, "summary")
        },
    )
}

/** 输入对话框视图 */
@Composable
private fun InputDialogView(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        InputDialogHeader(viewModel)
        Spacer(modifier = Modifier.height(8.dp))

        if (floatContext.attachments.isNotEmpty()) {
            AttachmentsList(floatContext)
            Spacer(modifier = Modifier.height(8.dp))
        }

        InputTextField(floatContext, viewModel)
    }
}

/** 输入对话框头部 */
@Composable
private fun InputDialogHeader(viewModel: FloatingChatWindowModeViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.send_message),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = { viewModel.hideInputDialog() }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.floating_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 附件列表 */
@Composable
private fun AttachmentsList(floatContext: FloatContext) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        floatContext.attachments.forEach { attachment ->
            AttachmentChip(
                attachmentInfo = attachment,
                onRemove = { floatContext.onRemoveAttachment?.invoke(attachment.filePath) },
                onInsert = { }
            )
        }
    }
}

/** 输入文本框 */
@Composable
private fun ColumnScope.InputTextField(
    floatContext: FloatContext,
    _viewModel: FloatingChatWindowModeViewModel
) {
    val focusRequester = remember { FocusRequester() }

    // 检测 AI 是否正在处理消息 - 使用 chatService 的 isLoading 状态
    val isProcessing =
        floatContext.chatService?.getChatCore()?.isLoading?.collectAsState()?.value ?: false

    DisposableEffect(floatContext.showInputDialog) {
        if (floatContext.showInputDialog) {
            floatContext.coroutineScope.launch {
                delay(300)
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    AppLogger.e("FloatingChatWindow", "Failed to request focus", e)
                }
            }
        }
        onDispose {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
    ) {
        OutlinedTextField(
            value = floatContext.userMessage,
            onValueChange = { floatContext.userMessage = it },
            placeholder = { Text(stringResource(R.string.floating_enter_your_question)) },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            textStyle = TextStyle.Default,
            maxLines = Int.MAX_VALUE,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                autoCorrectEnabled = true
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (floatContext.userMessage.isNotBlank() || floatContext.attachments.isNotEmpty()) {
                        floatContext.onSendMessage?.invoke(
                            floatContext.userMessage,
                            PromptFunctionType.CHAT
                        )
                        floatContext.userMessage = ""
                        floatContext.showInputDialog = false
                        floatContext.showAttachmentPanel = false
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // 发送/取消按钮
        FloatingActionButton(
            onClick = {
                when {
                    isProcessing -> {
                        // 取消当前消息处理
                        floatContext.onCancelMessage?.invoke()
                    }

                    floatContext.userMessage.isNotBlank() || floatContext.attachments.isNotEmpty() -> {
                        // 发送消息
                        floatContext.onSendMessage?.invoke(
                            floatContext.userMessage,
                            PromptFunctionType.CHAT
                        )
                        floatContext.userMessage = ""
                        floatContext.showInputDialog = false
                        floatContext.showAttachmentPanel = false
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(46.dp),
            containerColor = if (isProcessing)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = if (isProcessing) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isProcessing) stringResource(R.string.floating_chat_cancel) else stringResource(R.string.floating_chat_send),
                tint = if (isProcessing)
                    MaterialTheme.colorScheme.onError
                else
                    MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** 右边框缩放手柄 */
@Composable
private fun BoxScope.RightEdgeScaleHandle(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    onScalingChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    var isHovering by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        isHovering = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        viewModel.startEdgeResize()
                        onScalingChange(true)
                    },
                    onDragEnd = {
                        viewModel.endEdgeResize()
                        onScalingChange(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val widthDelta = with(density) { dragAmount.x.toDp() }
                        val newWidth = viewModel.windowState.width + widthDelta
                        viewModel.handleResize(newWidth, viewModel.windowState.height)
                    }
                )
            }
    ) {
        val lineColor =
            if (isHovering || floatContext.isEdgeResizing) {
                primaryColor.copy(alpha = 0.8f)
            } else {
                primaryColor.copy(alpha = 0.4f)
            }

        Box(
            modifier = Modifier
                .width(6.dp)
                .height(60.dp)
                .align(Alignment.Center)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineSpacing = size.width / 4

                drawLine(
                    color = lineColor,
                    start = Offset(lineSpacing, 0f),
                    end = Offset(lineSpacing, size.height),
                    strokeWidth = 2f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(lineSpacing * 2, 0f),
                    end = Offset(lineSpacing * 2, size.height),
                    strokeWidth = 2f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(lineSpacing * 3, 0f),
                    end = Offset(lineSpacing * 3, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}

/** 角落缩放手柄 */
@Composable
private fun BoxScope.CornerScaleHandle(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    alignment: Alignment,
    onScalingChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    var dragStartScale by remember { mutableStateOf(1f) }
    var baseWidthInPx by remember { mutableStateOf(0f) }
    var baseHeightInPx by remember { mutableStateOf(0f) }
    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }
    var isHovering by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .align(alignment)
            .size(24.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        isHovering = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        viewModel.startDragging()
                        onScalingChange(true)
                        dragStartScale = viewModel.windowState.scale
                        baseWidthInPx = with(density) { viewModel.windowState.width.toPx() }
                        baseHeightInPx = with(density) { viewModel.windowState.height.toPx() }
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        viewModel.endDragging()
                        onScalingChange(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        val alignmentFactorX =
                            if (alignment == Alignment.TopStart || alignment == Alignment.BottomStart) -1 else 1
                        val alignmentFactorY =
                            if (alignment == Alignment.TopStart || alignment == Alignment.TopEnd) -1 else 1

                        val effectiveDragX = totalDragX * alignmentFactorX
                        val effectiveDragY = totalDragY * alignmentFactorY

                        val scaleDelta =
                            if (kotlin.math.abs(effectiveDragX) > kotlin.math.abs(effectiveDragY)) {
                                if (baseWidthInPx > 0) effectiveDragX / baseWidthInPx else 0f
                            } else {
                                if (baseHeightInPx > 0) effectiveDragY / baseHeightInPx else 0f
                            }
                        val newScale = dragStartScale + scaleDelta
                        viewModel.handleScaleChange(newScale)
                    }
                )
            }
    ) {
        // 高亮显示
        if (isHovering) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.Center)
                    .background(primaryColor.copy(alpha = 0.6f), CircleShape)
            )
        }
    }
}

/** 底部高度调整手柄 */
@Composable
private fun BoxScope.BottomResizeHandle(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    onResizingChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    var isHovering by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(12.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        isHovering = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        viewModel.startEdgeResize()
                        onResizingChange(true)
                    },
                    onDragEnd = {
                        viewModel.endEdgeResize()
                        onResizingChange(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // 直接使用拖动距离，不需要除以 scale
                        val heightDelta = with(density) { dragAmount.y.toDp() }
                        val newHeight = viewModel.windowState.height + heightDelta
                        viewModel.handleResize(viewModel.windowState.width, newHeight)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 拖动手柄的视觉表示（三条横线）
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineColor = if (isHovering || floatContext.isEdgeResizing) {
                    primaryColor.copy(alpha = 0.8f)
                } else {
                    primaryColor.copy(alpha = 0.4f)
                }
                val lineSpacing = size.height / 4

                // 绘制三条横线
                drawLine(
                    color = lineColor,
                    start = Offset(0f, lineSpacing),
                    end = Offset(size.width, lineSpacing),
                    strokeWidth = 2f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, lineSpacing * 2),
                    end = Offset(size.width, lineSpacing * 2),
                    strokeWidth = 2f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, lineSpacing * 3),
                    end = Offset(size.width, lineSpacing * 3),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
private fun ProcessingStatusIndicator(floatContext: FloatContext) {
    val state = floatContext.inputProcessingState.value
    
    if (state !is InputProcessingState.Idle && state !is InputProcessingState.Completed) {
        val text = when (state) {
            is InputProcessingState.Processing -> state.message
            is InputProcessingState.Connecting -> state.message
            is InputProcessingState.Receiving -> state.message
            is InputProcessingState.ExecutingTool -> stringResource(R.string.floating_using_tool, state.toolName)
            is InputProcessingState.ProcessingToolResult -> stringResource(R.string.floating_processing_tool_result, state.toolName)
            is InputProcessingState.Summarizing -> state.message
            is InputProcessingState.ExecutingPlan -> state.message
            is InputProcessingState.Error -> stringResource(R.string.floating_error, state.message)
            else -> stringResource(R.string.floating_processing)
        }
        
        val backgroundColor = if (state is InputProcessingState.Error) 
            MaterialTheme.colorScheme.errorContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            
        val contentColor = if (state is InputProcessingState.Error) 
            MaterialTheme.colorScheme.onErrorContainer 
        else 
            MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state !is InputProcessingState.Error) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}



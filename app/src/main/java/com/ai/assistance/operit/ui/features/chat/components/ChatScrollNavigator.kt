package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.lazy.LazyListState as ComposeLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.ui.features.chat.components.lazy.LazyListState as ChatLazyListState
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

private data class ChatScrollNavigatorSnapshot(
    val centeredMessageIndex: Int?,
    val isScrollInProgress: Boolean,
)

private const val LOCATOR_PREVIEW_CHAR_COUNT = 48
private const val TAG = "ChatScrollNavigator"
private const val NAVIGATOR_HIDE_DELAY_MS = 1200L

private data class ChatMessageLocatorEntry(
    val index: Int,
    val preview: ChatMessageLocatorPreview,
)

@Composable
internal fun ChatScrollNavigator(
    chatHistory: List<ChatMessage>,
    currentChatId: String? = null,
    scrollState: ChatLazyListState,
    minVisibleIndex: Int,
    visibleMessageCount: Int,
    loadLocatorEntries: (suspend (String, String) -> List<ChatMessageLocatorPreview>)? = null,
    onJumpToMessageTimestamp: ((Long) -> Unit)? = null,
    onJumpToMessage: (Int) -> Unit,
    onToggleFavoriteMessage: ((Long, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (chatHistory.size <= 1 || visibleMessageCount <= 0) {
        return
    }

    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()
    val currentIsDragged by rememberUpdatedState(isDragged)
    var showNavigatorChip by remember { mutableStateOf(false) }
    var userScrollSessionActive by remember { mutableStateOf(false) }
    var showLocatorDialog by remember { mutableStateOf(false) }
    var currentMessageIndex by remember(chatHistory) {
        mutableStateOf(chatHistory.lastIndex.takeIf { it >= 0 })
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrollSessionActive = true
            showNavigatorChip = true
        }
    }

    LaunchedEffect(scrollState, minVisibleIndex, visibleMessageCount, chatHistory.size) {
        snapshotFlow {
            ChatScrollNavigatorSnapshot(
                centeredMessageIndex =
                    resolveCenteredMessageIndex(
                        scrollState = scrollState,
                        minVisibleIndex = minVisibleIndex,
                        visibleMessageCount = visibleMessageCount,
                        totalMessageCount = chatHistory.size,
                    ),
                isScrollInProgress = scrollState.isScrollInProgress,
            )
        }.collectLatest { snapshot ->
            snapshot.centeredMessageIndex?.let { currentMessageIndex = it }
            if (!userScrollSessionActive) {
                return@collectLatest
            }
            if (snapshot.isScrollInProgress || currentIsDragged) {
                showNavigatorChip = true
                return@collectLatest
            }
            delay(NAVIGATOR_HIDE_DELAY_MS)
            if (!scrollState.isScrollInProgress && !currentIsDragged) {
                showNavigatorChip = false
                userScrollSessionActive = false
            }
        }
    }

    val activeMessageIndex = currentMessageIndex
    val activeMessageTimestamp = activeMessageIndex?.let { chatHistory.getOrNull(it)?.timestamp }
    var locatorEntries by remember(currentChatId) { mutableStateOf<List<ChatMessageLocatorPreview>>(emptyList()) }
    var isLoadingLocatorEntries by remember(currentChatId) { mutableStateOf(false) }
    var locatorLoadFailed by remember(currentChatId) { mutableStateOf(false) }

    LaunchedEffect(
        currentChatId,
        chatHistory.size,
        chatHistory.firstOrNull()?.timestamp,
        chatHistory.lastOrNull()?.timestamp,
    ) {
        if (currentChatId.isNullOrBlank() || loadLocatorEntries == null) {
            locatorEntries = chatHistory.map(::toLocatorPreview)
            isLoadingLocatorEntries = false
            locatorLoadFailed = false
            return@LaunchedEffect
        }

        isLoadingLocatorEntries = true
        locatorLoadFailed = false
        try {
            locatorEntries = loadLocatorEntries(currentChatId, "")
            locatorLoadFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载聊天定位预览失败", e)
            locatorEntries = emptyList()
            locatorLoadFailed = true
        }
        isLoadingLocatorEntries = false
    }

    val activeGlobalMessageIndex =
        activeMessageTimestamp?.let { timestamp ->
            locatorEntries.indexOfFirst { it.timestamp == timestamp }.takeIf { it >= 0 }
        }

    AnimatedVisibility(
        visible = showNavigatorChip && activeMessageIndex != null,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = fadeOut(animationSpec = tween(120)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        val bubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        val anchorLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        val anchorDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        val navigatorBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
        val navigatorShape =
            RoundedCornerShape(
                topStart = 14.dp,
                bottomStart = 14.dp,
                topEnd = 10.dp,
                bottomEnd = 10.dp,
            )
        val progressTotalCount =
            locatorEntries.size.takeIf { it > 0 } ?: chatHistory.size
        val progressIndex =
            activeGlobalMessageIndex ?: activeMessageIndex ?: 0
        val progress =
            if (progressTotalCount <= 1) {
                1f
            } else {
                (progressIndex.toFloat() / (progressTotalCount - 1).toFloat()).coerceIn(0f, 1f)
            }

        Row(
            modifier =
                Modifier.clickable {
                    showLocatorDialog = true
                    showNavigatorChip = false
                    userScrollSessionActive = false
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(navigatorShape)
                        .background(bubbleColor)
                        .border(1.dp, navigatorBorderColor, navigatorShape),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(20.dp)
                            .height(58.dp)
                            .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(width = 8.dp, height = 34.dp)) {
                        val centerX = size.width / 2f
                        val topY = 2.dp.toPx()
                        val bottomY = size.height - 2.dp.toPx()
                        val dotCenterY = topY + (bottomY - topY) * progress
                        drawLine(
                            color = anchorLineColor,
                            start = Offset(centerX, topY),
                            end = Offset(centerX, bottomY),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        drawCircle(
                            color = anchorDotColor,
                            radius = 3.dp.toPx(),
                            center = Offset(centerX, dotCenterY),
                        )
                    }
                }
            }

            Canvas(
                modifier =
                    Modifier
                        .offset(x = (-1).dp)
                        .size(width = 9.dp, height = 18.dp),
            ) {
                val arrowPath =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                drawPath(path = arrowPath, color = bubbleColor)
            }
        }
    }

    if (showLocatorDialog && activeMessageTimestamp != null) {
        ChatMessageLocatorDialog(
            locatorEntries = locatorEntries,
            currentMessageTimestamp = activeMessageTimestamp,
            isLoading = isLoadingLocatorEntries,
            loadFailed = locatorLoadFailed,
            currentChatId = currentChatId,
            loadLocatorEntries = loadLocatorEntries,
            onDismiss = { showLocatorDialog = false },
            onToggleFavoriteMessage = onToggleFavoriteMessage,
            onJumpToMessage = { targetTimestamp ->
                showLocatorDialog = false
                val targetIndex = chatHistory.indexOfFirst { it.timestamp == targetTimestamp }
                if (targetIndex >= 0) {
                    onJumpToMessage(targetIndex)
                } else {
                    onJumpToMessageTimestamp?.invoke(targetTimestamp)
                }
            },
        )
    }
}

@Composable
internal fun ChatScrollNavigator(
    chatHistory: List<ChatMessage>,
    currentChatId: String? = null,
    scrollState: ComposeLazyListState,
    itemMessageIndices: List<Int>,
    autoScrollToBottom: Boolean,
    hasNewerDisplayHistory: Boolean = false,
    loadLocatorEntries: (suspend (String, String) -> List<ChatMessageLocatorPreview>)? = null,
    onRequestLatestMessages: (() -> Unit)? = null,
    onAutoScrollToBottomChange: ((Boolean) -> Unit)? = null,
    onJumpToMessageTimestamp: ((Long) -> Unit)? = null,
    onJumpToMessage: (Int) -> Unit,
    onToggleFavoriteMessage: ((Long, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (chatHistory.isEmpty()) {
        return
    }

    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()
    val currentIsDragged by rememberUpdatedState(isDragged)
    val coroutineScope = rememberCoroutineScope()
    var showNavigatorChip by remember { mutableStateOf(false) }
    var userScrollSessionActive by remember { mutableStateOf(false) }
    var showLocatorDialog by remember { mutableStateOf(false) }
    var currentMessageIndex by remember(chatHistory) {
        mutableStateOf(chatHistory.lastIndex.takeIf { it >= 0 })
    }
    val currentAutoScrollToBottom by rememberUpdatedState(autoScrollToBottom)
    val currentHasNewerDisplayHistory by rememberUpdatedState(hasNewerDisplayHistory)
    val currentOnRequestLatestMessages by rememberUpdatedState(onRequestLatestMessages)
    val currentOnAutoScrollToBottomChange by rememberUpdatedState(onAutoScrollToBottomChange)

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrollSessionActive = true
            showNavigatorChip = true
        }
    }

    LaunchedEffect(
        scrollState,
        itemMessageIndices,
        chatHistory.size,
        chatHistory.firstOrNull()?.timestamp,
        chatHistory.lastOrNull()?.timestamp,
    ) {
        snapshotFlow {
            ChatScrollNavigatorSnapshot(
                centeredMessageIndex =
                    resolveCenteredMessageIndex(
                        scrollState = scrollState,
                        itemMessageIndices = itemMessageIndices,
                    ),
                isScrollInProgress = scrollState.isScrollInProgress,
            )
        }.collectLatest { snapshot ->
            snapshot.centeredMessageIndex?.let { currentMessageIndex = it }
            if (!userScrollSessionActive) {
                return@collectLatest
            }
            if (snapshot.isScrollInProgress || currentIsDragged) {
                showNavigatorChip = true
                return@collectLatest
            }
            delay(NAVIGATOR_HIDE_DELAY_MS)
            if (!scrollState.isScrollInProgress && !currentIsDragged) {
                showNavigatorChip = false
                userScrollSessionActive = false
            }
        }
    }

    LaunchedEffect(scrollState) {
        var lastPosition = scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
        snapshotFlow { scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collectLatest { currentPosition ->
                if (scrollState.isScrollInProgress && userScrollSessionActive) {
                    val movedAwayFromBottom = currentPosition.first < lastPosition.first ||
                        (currentPosition.first == lastPosition.first && currentPosition.second < lastPosition.second)
                    if (movedAwayFromBottom) {
                        if (currentAutoScrollToBottom) {
                            currentOnAutoScrollToBottomChange?.invoke(false)
                        }
                    } else {
                        val isAtBottom =
                            !scrollState.canScrollForward &&
                                !currentHasNewerDisplayHistory
                        if (isAtBottom && !currentAutoScrollToBottom) {
                            currentOnAutoScrollToBottomChange?.invoke(true)
                        }
                    }
                }
                lastPosition = currentPosition
            }
    }

    val activeMessageIndex = currentMessageIndex
    val activeMessageTimestamp = activeMessageIndex?.let { chatHistory.getOrNull(it)?.timestamp }
    var locatorEntries by remember(currentChatId) { mutableStateOf<List<ChatMessageLocatorPreview>>(emptyList()) }
    var isLoadingLocatorEntries by remember(currentChatId) { mutableStateOf(false) }
    var locatorLoadFailed by remember(currentChatId) { mutableStateOf(false) }

    LaunchedEffect(
        currentChatId,
        chatHistory.size,
        chatHistory.firstOrNull()?.timestamp,
        chatHistory.lastOrNull()?.timestamp,
    ) {
        if (currentChatId.isNullOrBlank() || loadLocatorEntries == null) {
            locatorEntries = chatHistory.map(::toLocatorPreview)
            isLoadingLocatorEntries = false
            locatorLoadFailed = false
            return@LaunchedEffect
        }

        isLoadingLocatorEntries = true
        locatorLoadFailed = false
        try {
            locatorEntries = loadLocatorEntries(currentChatId, "")
            locatorLoadFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载聊天定位预览失败", e)
            locatorEntries = emptyList()
            locatorLoadFailed = true
        }
        isLoadingLocatorEntries = false
    }

    val activeGlobalMessageIndex =
        activeMessageTimestamp?.let { timestamp ->
            locatorEntries.indexOfFirst { it.timestamp == timestamp }.takeIf { it >= 0 }
        }
    val shouldShowNavigatorControl = activeMessageIndex != null && showNavigatorChip

    AnimatedVisibility(
        visible = shouldShowNavigatorControl,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = fadeOut(animationSpec = tween(120)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        val bubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        val anchorLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        val anchorDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        val navigatorBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
        val navigatorShape =
            RoundedCornerShape(
                topStart = 14.dp,
                bottomStart = 14.dp,
                topEnd = 10.dp,
                bottomEnd = 10.dp,
            )
        val progressTotalCount =
            locatorEntries.size.takeIf { it > 0 } ?: chatHistory.size
        val progressIndex =
            activeGlobalMessageIndex ?: activeMessageIndex ?: 0
        val progress =
            if (progressTotalCount <= 1) {
                1f
            } else {
                (progressIndex.toFloat() / (progressTotalCount - 1).toFloat()).coerceIn(0f, 1f)
            }

        Box(modifier = Modifier.size(width = 34.dp, height = 114.dp)) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 4.5.dp)
                        .clickable {
                            showLocatorDialog = true
                            showNavigatorChip = false
                            userScrollSessionActive = false
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(navigatorShape)
                            .background(bubbleColor)
                            .border(1.dp, navigatorBorderColor, navigatorShape),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(20.dp)
                                .height(58.dp)
                                .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(width = 8.dp, height = 34.dp)) {
                            val centerX = size.width / 2f
                            val topY = 2.dp.toPx()
                            val bottomY = size.height - 2.dp.toPx()
                            val dotCenterY = topY + (bottomY - topY) * progress
                            drawLine(
                                color = anchorLineColor,
                                start = Offset(centerX, topY),
                                end = Offset(centerX, bottomY),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                            drawCircle(
                                color = anchorDotColor,
                                radius = 3.dp.toPx(),
                                center = Offset(centerX, dotCenterY),
                            )
                        }
                    }
                }

                Canvas(
                    modifier =
                        Modifier
                            .offset(x = (-1).dp)
                            .size(width = 9.dp, height = 18.dp),
                ) {
                    val arrowPath =
                        Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        }
                    drawPath(path = arrowPath, color = bubbleColor)
                }
            }

            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .align(Alignment.TopStart)
                        .offset(x = 2.5.dp, y = 90.dp)
                        .clip(CircleShape)
                        .background(bubbleColor)
                        .border(1.dp, navigatorBorderColor, CircleShape)
                        .clickable {
                            coroutineScope.launch {
                                if (currentHasNewerDisplayHistory) {
                                    currentOnRequestLatestMessages?.invoke()
                                }
                                scrollState.animateScrollToEnd()
                            }
                            currentOnAutoScrollToBottomChange?.invoke(true)
                        },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.history_scroll_to_bottom),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }

    if (showLocatorDialog && activeMessageTimestamp != null) {
        ChatMessageLocatorDialog(
            locatorEntries = locatorEntries,
            currentMessageTimestamp = activeMessageTimestamp,
            isLoading = isLoadingLocatorEntries,
            loadFailed = locatorLoadFailed,
            currentChatId = currentChatId,
            loadLocatorEntries = loadLocatorEntries,
            onDismiss = { showLocatorDialog = false },
            onToggleFavoriteMessage = onToggleFavoriteMessage,
            onJumpToMessage = { targetTimestamp ->
                showLocatorDialog = false
                val targetIndex = chatHistory.indexOfFirst { it.timestamp == targetTimestamp }
                if (targetIndex >= 0) {
                    onJumpToMessage(targetIndex)
                } else {
                    onJumpToMessageTimestamp?.invoke(targetTimestamp)
                }
            },
        )
    }
}

@Composable
private fun ChatMessageLocatorDialog(
    locatorEntries: List<ChatMessageLocatorPreview>,
    currentMessageTimestamp: Long,
    isLoading: Boolean,
    loadFailed: Boolean,
    currentChatId: String?,
    loadLocatorEntries: (suspend (String, String) -> List<ChatMessageLocatorPreview>)?,
    onDismiss: () -> Unit,
    onToggleFavoriteMessage: ((Long, Boolean) -> Unit)?,
    onJumpToMessage: (Long) -> Unit,
) {
    val currentMessageIndex = locatorEntries.indexOfFirst { it.timestamp == currentMessageTimestamp }
    val initialIndex =
        currentMessageIndex
            .takeIf { it >= 0 }
            ?.let { (it - 2).coerceAtLeast(0) }
            ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    var searchQuery by remember { mutableStateOf("") }
    var searchEntries by remember { mutableStateOf<List<ChatMessageLocatorPreview>>(emptyList()) }
    var isLoadingSearchEntries by remember { mutableStateOf(false) }
    var searchLoadFailed by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favoriteOverrides by remember(locatorEntries) { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    val hiddenPlaceholderText = stringResource(R.string.chat_hidden_user_message_placeholder)
    val normalizedSearchQuery = normalizeMessageSearchText(searchQuery)
    val activeLocatorEntries =
        if (normalizedSearchQuery.isBlank()) {
            locatorEntries
        } else {
            searchEntries
        }
    val dialogIsLoading = isLoading || isLoadingSearchEntries
    val dialogLoadFailed =
        if (normalizedSearchQuery.isBlank()) {
            loadFailed
        } else {
            searchLoadFailed
        }
    val indexedEntries =
        activeLocatorEntries.mapIndexed { index, preview ->
            ChatMessageLocatorEntry(index = preview.messageIndex ?: index, preview = preview)
        }
    val filteredEntries =
        if (dialogIsLoading) {
            indexedEntries
        } else {
            indexedEntries.filter { entry ->
                val isFavorite =
                    favoriteOverrides[entry.preview.timestamp] ?: entry.preview.isFavorite
                val matchesFavorite = !favoritesOnly || isFavorite
                matchesFavorite
            }
        }
    val maxMessageLength =
        remember(activeLocatorEntries) {
            activeLocatorEntries.maxOfOrNull { messageContentLength(it, hiddenPlaceholderText) }
                ?.coerceAtLeast(1) ?: 1
        }

    LaunchedEffect(normalizedSearchQuery, currentChatId, loadLocatorEntries) {
        if (normalizedSearchQuery.isBlank()) {
            searchEntries = emptyList()
            isLoadingSearchEntries = false
            searchLoadFailed = false
            return@LaunchedEffect
        }

        if (currentChatId.isNullOrBlank() || loadLocatorEntries == null) {
            searchEntries = emptyList()
            isLoadingSearchEntries = false
            searchLoadFailed = true
            return@LaunchedEffect
        }

        isLoadingSearchEntries = true
        searchLoadFailed = false
        delay(180)
        try {
            searchEntries = loadLocatorEntries(currentChatId, normalizedSearchQuery)
            searchLoadFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "搜索聊天定位预览失败", e)
            searchEntries = emptyList()
            searchLoadFailed = true
        } finally {
            isLoadingSearchEntries = false
        }
    }

    LaunchedEffect(normalizedSearchQuery, filteredEntries.size, currentMessageIndex, dialogIsLoading) {
        if (dialogIsLoading || filteredEntries.isEmpty()) {
            return@LaunchedEffect
        }

        val targetListIndex =
            if (normalizedSearchQuery.isBlank()) {
                filteredEntries.indexOfFirst { it.index == currentMessageIndex }
                    .takeIf { it >= 0 }
                    ?.let { (it - 2).coerceAtLeast(0) }
                    ?: 0
            } else {
                filteredEntries.indices.minByOrNull { entryIndex ->
                    abs(filteredEntries[entryIndex].index - currentMessageIndex)
                } ?: 0
            }
        listState.scrollToItem(targetListIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.chat_message_locator_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.chat_message_locator_current,
                                    (currentMessageIndex + 1).coerceAtLeast(0),
                                    locatorEntries.size,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.close))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = {
                            Text(text = stringResource(R.string.chat_message_locator_search_label))
                        },
                        placeholder = {
                            Text(text = stringResource(R.string.chat_message_locator_search_placeholder))
                        },
                    )

                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color =
                            if (favoritesOnly) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        border =
                            BorderStroke(
                                width = 1.dp,
                                color =
                                    if (favoritesOnly) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
                                    },
                            ),
                    ) {
                        IconButton(
                            onClick = { favoritesOnly = !favoritesOnly },
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        ) {
                            Icon(
                                imageVector =
                                    if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = stringResource(R.string.chat_message_locator_favorites_filter),
                                tint =
                                    if (favoritesOnly) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }

                if (normalizedSearchQuery.isBlank() && !favoritesOnly) {
                    Text(
                        text = stringResource(R.string.chat_message_locator_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (filteredEntries.isNotEmpty()) {
                    Text(
                        text =
                            stringResource(
                                R.string.chat_message_locator_search_results,
                                filteredEntries.size,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (dialogIsLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else if (dialogLoadFailed) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.loading_failed,
                                    stringResource(R.string.chat_message_locator_title),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_message_locator_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = filteredEntries,
                            key = { index, entry -> "${index}_${entry.preview.timestamp}_${entry.index}" },
                        ) { _, entry ->
                            ChatMessageLocatorRow(
                                index = entry.index,
                                preview = entry.preview,
                                isFavorite =
                                    favoriteOverrides[entry.preview.timestamp] ?: entry.preview.isFavorite,
                                isCurrent = entry.index == currentMessageIndex,
                                maxMessageLength = maxMessageLength,
                                searchQuery = searchQuery,
                                onToggleFavorite = {
                                    val nextFavorite =
                                        !(favoriteOverrides[entry.preview.timestamp]
                                            ?: entry.preview.isFavorite)
                                    favoriteOverrides =
                                        favoriteOverrides.toMutableMap().apply {
                                            put(entry.preview.timestamp, nextFavorite)
                                        }
                                    onToggleFavoriteMessage?.invoke(entry.preview.timestamp, nextFavorite)
                                },
                                onClick = { onJumpToMessage(entry.preview.timestamp) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageLocatorRow(
    index: Int,
    preview: ChatMessageLocatorPreview,
    isFavorite: Boolean,
    isCurrent: Boolean,
    maxMessageLength: Int,
    searchQuery: String,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val hiddenPlaceholderText = stringResource(R.string.chat_hidden_user_message_placeholder)
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (fillColor, rawPreviewTextColor, fillAlpha) =
        if (isDarkSurface) {
            when (preview.sender) {
                "user" ->
                    Triple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        0.9f,
                    )
                "summary" ->
                    Triple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        0.9f,
                    )
                "system" ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.9f,
                    )
                "think" ->
                    Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        0.9f,
                    )
                else ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.9f,
                    )
            }
        } else {
            when (preview.sender) {
                "user" ->
                    Triple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        0.98f,
                    )
                "ai" ->
                    Triple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        0.98f,
                    )
                "summary" ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.92f,
                    )
                "system" ->
                    Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        0.86f,
                    )
                "think" ->
                    Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        0.78f,
                    )
                else ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.9f,
                    )
            }
        }
    val previewTextColor =
        if (isDarkSurface) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (isCurrent) 0.96f else 0.88f)
        } else {
            rawPreviewTextColor
        }
    val containerColor =
        if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val borderColor =
        if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        }
    val messageLength = messageContentLength(preview, hiddenPlaceholderText)
    val previewText =
        buildMessagePreview(
            preview = preview,
            hiddenPlaceholderText = hiddenPlaceholderText,
            searchQuery = searchQuery,
        )

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        tonalElevation = if (isCurrent) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.width(90.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription =
                                    stringResource(
                                        if (isFavorite) {
                                            R.string.web_session_remove_bookmark
                                        } else {
                                            R.string.web_session_add_bookmark
                                        }
                                    ),
                                tint =
                                    if (isFavorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    },
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(senderLabelRes(preview.sender)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(messageBarFraction(messageLength, maxMessageLength))
                            .clip(RoundedCornerShape(12.dp))
                            .background(fillColor.copy(alpha = fillAlpha)),
                )
                Text(
                    text = previewText,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = previewTextColor,
                )
            }

            Text(
                text = messageLength.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun resolveCenteredMessageIndex(
    scrollState: ChatLazyListState,
    minVisibleIndex: Int,
    visibleMessageCount: Int,
    totalMessageCount: Int,
): Int? {
    if (visibleMessageCount <= 0 || totalMessageCount <= 0) {
        return null
    }
    val layoutInfo = scrollState.layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val visibleMessageItem =
        layoutInfo.visibleItemsInfo
            .filter { it.index in 0 until visibleMessageCount }
            .minByOrNull { item ->
                abs((item.offset + item.size / 2f) - viewportCenter)
            } ?: return null

    return (minVisibleIndex + visibleMessageItem.index).coerceIn(0, totalMessageCount - 1)
}

private fun resolveCenteredMessageIndex(
    scrollState: ComposeLazyListState,
    itemMessageIndices: List<Int>,
): Int? {
    val layout = scrollState.layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    val item = layout.visibleItemsInfo
        .filter { itemMessageIndices.getOrNull(it.index)?.let { index -> index >= 0 } == true }
        .minByOrNull { abs(it.offset + it.size / 2f - center) } ?: return null
    return itemMessageIndices[item.index]
}

private fun senderLabelRes(sender: String): Int =
    when (sender) {
        "user" -> R.string.chat_sender_user
        "ai" -> R.string.chat_sender_ai
        "summary" -> R.string.chat_sender_summary
        "system" -> R.string.chat_sender_system
        "think" -> R.string.chat_sender_think
        else -> R.string.chat_sender_other
    }

private fun toLocatorPreview(message: ChatMessage): ChatMessageLocatorPreview {
    val previewContent =
        if (
            message.sender == "user" &&
            message.displayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
        ) {
            ""
        } else {
            normalizeMessageSearchText(message.content).take(LOCATOR_PREVIEW_CHAR_COUNT)
        }
    val contentLength =
        if (
            message.sender == "user" &&
            message.displayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
        ) {
            0
        } else {
            normalizeMessageSearchText(message.content).length
        }
    return ChatMessageLocatorPreview(
        timestamp = message.timestamp,
        sender = message.sender,
        previewContent = previewContent,
        contentLength = contentLength,
        displayMode = message.displayMode.name,
        isFavorite = message.isFavorite,
    )
}

private fun visibleLocatorContent(preview: ChatMessageLocatorPreview, hiddenPlaceholderText: String): String {
    return if (
        preview.sender == "user" &&
        preview.resolvedDisplayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
    ) {
        hiddenPlaceholderText
    } else {
        preview.previewContent
    }
}

private fun messageContentLength(
    preview: ChatMessageLocatorPreview,
    hiddenPlaceholderText: String,
): Int =
    preview.contentLength
        .takeIf { it > 0 }
        ?: visibleLocatorContent(preview, hiddenPlaceholderText).length.coerceAtLeast(1)

private fun messageBarFraction(messageLength: Int, maxMessageLength: Int): Float {
    if (maxMessageLength <= 0) {
        return 0.18f
    }
    return sqrt(messageLength.toFloat() / maxMessageLength.toFloat()).coerceIn(0.18f, 1f)
}

private fun buildMessagePreview(
    preview: ChatMessageLocatorPreview,
    hiddenPlaceholderText: String,
    searchQuery: String = "",
): String {
    val content = normalizeMessageSearchText(visibleLocatorContent(preview, hiddenPlaceholderText))
    if (content.isEmpty()) {
        return preview.sender
    }

    val normalizedSearchQuery = normalizeMessageSearchText(searchQuery)
    if (normalizedSearchQuery.isNotEmpty()) {
        val matchIndex = content.indexOf(normalizedSearchQuery, ignoreCase = true)
        if (matchIndex >= 0) {
            val previewLength = 72
            val preferredStart = (matchIndex - 18).coerceAtLeast(0)
            val start = preferredStart.coerceAtMost((content.length - previewLength).coerceAtLeast(0))
            val end = (start + previewLength).coerceAtMost(content.length)
            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < content.length) "..." else ""
            val snippet = content.substring(start, end).trim()
            if (snippet.isNotEmpty()) {
                return prefix + snippet + suffix
            }
        }
    }

    return content.take(72).let { preview ->
        if (preview.length < content.length) {
            preview.trimEnd() + "..."
        } else {
            preview
        }
    }
}

private fun normalizeMessageSearchText(text: String): String {
    if (text.isEmpty()) {
        return ""
    }

    val previewBuilder = StringBuilder(text.length)
    var pendingWhitespace = false

    for (char in text) {
        val normalizedChar =
            when (char) {
                '\n', '\r', '\t' -> ' '
                else -> char
            }
        if (normalizedChar.isWhitespace()) {
            pendingWhitespace = previewBuilder.isNotEmpty()
            continue
        }
        if (pendingWhitespace) {
            previewBuilder.append(' ')
            pendingWhitespace = false
        }
        previewBuilder.append(normalizedChar)
    }

    return previewBuilder.toString().trim()
}

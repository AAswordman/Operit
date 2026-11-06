package com.ai.assistance.operit.ui.features.chat.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatHistoryDisplayMode
import com.ai.assistance.operit.ui.features.chat.viewmodel.AiTitleGenerationState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.ui.common.rememberLocal
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

private data class GroupTarget(
    val groupName: String,
    val characterCardName: String?
)

private fun resolveBindingForCreate(
    historyDisplayMode: ChatHistoryDisplayMode,
    activePrompt: ActivePrompt,
    activeCharacterCardName: String?
): Pair<String?, String?> {
    if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) {
        return Pair(null, null)
    }
    return when (val prompt = activePrompt) {
        is ActivePrompt.CharacterGroup -> Pair(null, prompt.id)
        is ActivePrompt.CharacterCard -> Pair(activeCharacterCardName, null)
    }
}

private sealed interface HistoryListItem {
    data class CharacterHeader(
        val key: String,
        val name: String,
        val characterCardName: String? = null,
        val characterGroupId: String? = null
    ) : HistoryListItem
    data class Header(
        val key: String, 
        val name: String, 
        val groupValue: String?,
        val characterCardName: String? = null
    ) : HistoryListItem
    data class Item(val history: ChatHistory) : HistoryListItem
}

@Composable
private fun HistoryQuickScrollButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color =
            if (enabled) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f)
            } else {
                Color.Transparent
            },
        shadowElevation = if (enabled) 1.dp else 0.dp
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(14.dp),
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                    }
            )
        }
    }
}

@Composable
private fun HistoryQuickScroller(
    listState: LazyListState,
    itemCount: Int,
    onInteractionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val visibleItemCount = visibleItems.size
    val totalItemCount = layoutInfo.totalItemsCount.takeIf { it > 0 } ?: itemCount
    val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0
    val viewportHeightPx =
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat().coerceAtLeast(1f)
    val averageVisibleItemHeightPx =
        visibleItems
            .map { it.size }
            .average()
            .toFloat()
            .takeIf { it > 0f }
            ?: viewportHeightPx
    val estimatedContentHeightPx =
        (averageVisibleItemHeightPx * totalItemCount).coerceAtLeast(viewportHeightPx)
    val estimatedScrollOffsetPx =
        (
            listState.firstVisibleItemIndex * averageVisibleItemHeightPx +
                listState.firstVisibleItemScrollOffset.toFloat()
        ).coerceAtLeast(0f)
    val maxScrollOffsetPx = (estimatedContentHeightPx - viewportHeightPx).coerceAtLeast(1f)
    val canScrollBackward =
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    val canScrollForward = totalItemCount > 0 && lastVisibleIndex < totalItemCount - 1
    val shouldShow = totalItemCount > 1 && visibleItemCount > 0 && totalItemCount > visibleItemCount

    if (!shouldShow) {
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minThumbHeight = with(density) { 36.dp.toPx() }
    val trackWidth = 12.dp
    var trackHeightPx by remember { mutableStateOf(0f) }
    var isHandlingTouch by remember { mutableStateOf(false) }
    val scrollProgress =
        if (totalItemCount <= 1) {
            0f
        } else {
            (estimatedScrollOffsetPx / maxScrollOffsetPx)
                .coerceIn(0f, 1f)
        }
    val visibleFraction =
        (viewportHeightPx / estimatedContentHeightPx).coerceIn(0.12f, 1f)
    val thumbHeightPx =
        when {
            trackHeightPx <= 0f -> minThumbHeight
            trackHeightPx <= minThumbHeight -> trackHeightPx
            else -> (trackHeightPx * visibleFraction).coerceIn(minThumbHeight, trackHeightPx)
        }
    val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = maxThumbOffsetPx * scrollProgress
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
    val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
    val fastScrollDescription = stringResource(R.string.history_fast_scroll)
    val quickScrollerAlpha = if (listState.isScrollInProgress || isHandlingTouch) 0.9f else 0.5f
    val hostView = LocalView.current
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val currentMaxScrollOffsetPx by rememberUpdatedState(maxScrollOffsetPx)
    val currentEstimatedScrollOffsetPx by rememberUpdatedState(estimatedScrollOffsetPx)
    val currentTotalItemCount by rememberUpdatedState(totalItemCount)
    val currentOnInteractionChange by rememberUpdatedState(onInteractionChange)
    DisposableEffect(Unit) {
        onDispose {
            onInteractionChange(false)
        }
    }

    fun jumpToPointer(pointerY: Float) {
        if (currentTrackHeightPx <= 0f || currentTotalItemCount <= 1) {
            return
        }
        val trackableHeight = (currentTrackHeightPx - currentThumbHeightPx).coerceAtLeast(1f)
        val normalizedOffset = (pointerY - currentThumbHeightPx / 2f).coerceIn(0f, trackableHeight)
        val progress = (normalizedOffset / trackableHeight).coerceIn(0f, 1f)
        val targetScrollOffsetPx = progress * currentMaxScrollOffsetPx
        val scrollDeltaPx = targetScrollOffsetPx - currentEstimatedScrollOffsetPx
        if (scrollDeltaPx != 0f) {
            listState.dispatchRawDelta(scrollDeltaPx)
        }
    }

    fun scrollByPointerDelta(pointerDeltaY: Float) {
        if (currentTrackHeightPx <= 0f || currentTotalItemCount <= 1) {
            return
        }
        val trackableHeight = (currentTrackHeightPx - currentThumbHeightPx).coerceAtLeast(1f)
        val contentDeltaPx = pointerDeltaY * (currentMaxScrollOffsetPx / trackableHeight)
        if (contentDeltaPx != 0f) {
            listState.dispatchRawDelta(contentDeltaPx)
        }
    }

    fun startHandlingTouch(pointerY: Float) {
        isHandlingTouch = true
        currentOnInteractionChange(true)
        hostView.parent?.requestDisallowInterceptTouchEvent(true)
        jumpToPointer(pointerY)
    }

    fun stopHandlingTouch() {
        isHandlingTouch = false
        currentOnInteractionChange(false)
        hostView.parent?.requestDisallowInterceptTouchEvent(false)
    }

    Column(
        modifier = modifier
            .width(20.dp)
            .alpha(quickScrollerAlpha)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistoryQuickScrollButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.history_scroll_to_top),
            enabled = canScrollBackward,
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .width(28.dp)
                .padding(vertical = 4.dp)
                .onGloballyPositioned { coordinates ->
                    trackHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        startHandlingTouch(down.position.y)
                        down.consume()
                        try {
                            var previousPointerY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change =
                                    event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                val pointerDeltaY = change.position.y - previousPointerY
                                previousPointerY = change.position.y
                                if (pointerDeltaY != 0f) {
                                    scrollByPointerDelta(pointerDeltaY)
                                }
                                change.consume()
                            }
                        } finally {
                            stopHandlingTouch()
                        }
                    }
                }
                .semantics {
                    contentDescription = fastScrollDescription
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(trackWidth)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = thumbOffsetDp)
                        .width(8.dp)
                        .height(thumbHeightDp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                )
            }
        }

        HistoryQuickScrollButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.history_scroll_to_bottom),
            enabled = canScrollForward,
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem((totalItemCount - 1).coerceAtLeast(0))
                }
            }
        )
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun ChatHistorySelector(
        modifier: Modifier = Modifier,
        onNewChat: (characterCardName: String?, characterGroupId: String?) -> Unit,
        onSelectChat: (String) -> Unit,
        onDeleteChat: (String) -> Unit,
        onUpdateChatTitle: (chatId: String, newTitle: String) -> Unit,
        onGenerateAiTitle: (chatId: String) -> Unit,
        onDismissAiTitleResult: (chatId: String) -> Unit,
        aiTitleStates: Map<String, AiTitleGenerationState>,
        onUpdateChatBinding: (chatId: String, characterCardName: String?, characterGroupId: String?) -> Unit,
        onCreateGroup: (groupName: String, characterCardName: String?, characterGroupId: String?) -> Unit,
        onUpdateChatOrderAndGroup: (reorderedHistories: List<ChatHistory>, movedItem: ChatHistory, targetGroup: String?) -> Unit,
        onUpdateGroupName: (oldName: String, newName: String, characterCardName: String?) -> Unit,
        onDeleteGroup: (groupName: String, deleteChats: Boolean, characterCardName: String?) -> Unit,
        chatHistories: List<ChatHistory>,
        currentId: String?,
        activeStreamingChatIds: Set<String> = emptySet(),
        lazyListState: LazyListState? = null,
        onBack: (() -> Unit)? = null,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        historyDisplayMode: ChatHistoryDisplayMode,
        onDisplayModeChange: (ChatHistoryDisplayMode) -> Unit,
        autoSwitchCharacterCard: Boolean,
        onAutoSwitchCharacterCardChange: (Boolean) -> Unit,
        autoSwitchChatOnCharacterSelect: Boolean,
        onAutoSwitchChatOnCharacterSelectChange: (Boolean) -> Unit,
        onQuickScrollInteractionChange: (Boolean) -> Unit = {},
        activePrompt: ActivePrompt
) {
    var chatToEdit by remember { mutableStateOf<ChatHistory?>(null) }
    var chatToDelete by remember { mutableStateOf<ChatHistory?>(null) }
    var chatItemActionTarget by remember { mutableStateOf<ChatHistory?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var collapsedGroups by rememberLocal("chat_history_collapsed_groups", emptySet<String>())
    var collapsedCharacters by rememberLocal("chat_history_collapsed_characters", emptySet<String>())

    var groupActionTarget by remember { mutableStateOf<GroupTarget?>(null) }
    var groupToRename by remember { mutableStateOf<GroupTarget?>(null) }
    var groupToDelete by remember { mutableStateOf<GroupTarget?>(null) }
    var hasLongPressedGroup by rememberLocal("has_long_pressed_group", defaultValue = false)
    
    // 搜索相关状态
    var showSearchBox by remember { mutableStateOf(false) }
    var matchedChatIdsByContent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val deleteAnimationDurationMs = 220L
    var deletingChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var availableCharacterCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var availableCharacterGroups by remember { mutableStateOf<List<CharacterGroupCard>>(emptyList()) }
    var resolvedGroupNameById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun promptDeleteChat(history: ChatHistory) {
        if (history.locked) {
            onDeleteChat(history.id)
            return
        }
        if (deletingChatIds.contains(history.id)) {
            return
        }
        chatToDelete = history
    }

    fun requestDeleteChat(history: ChatHistory) {
        if (history.locked) {
            onDeleteChat(history.id)
            return
        }
        if (deletingChatIds.contains(history.id)) {
            return
        }
        deletingChatIds = deletingChatIds + history.id
        coroutineScope.launch {
            delay(deleteAnimationDurationMs)
            onDeleteChat(history.id)
        }
    }

    if (chatToDelete != null) {
        val deletingChat = chatToDelete!!
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_chat)) },
            text = {
                Text(
                    text = stringResource(R.string.delete_chat_confirmation, deletingChat.title)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        requestDeleteChat(deletingChat)
                        chatToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        characterCardManager.characterCardListFlow.collectLatest { ids ->
            val cards = ids.mapNotNull { id ->
                runCatching { characterCardManager.getCharacterCard(id) }.getOrNull()
            }
            availableCharacterCards = cards
        }
    }
    LaunchedEffect(Unit) {
        characterGroupCardManager.allCharacterGroupCardsFlow.collectLatest { groups ->
            availableCharacterGroups = groups
        }
    }
    val groupNameById = remember(availableCharacterGroups, resolvedGroupNameById) {
        val fromList = availableCharacterGroups.associate { it.id to it.name }
        fromList + resolvedGroupNameById
    }
    val activeCharacterCardName = remember(activePrompt, availableCharacterCards) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard ->
                availableCharacterCards.firstOrNull { it.id == prompt.id }?.name
            is ActivePrompt.CharacterGroup -> null
        }
    }
    val actualLazyListState = lazyListState ?: rememberLazyListState()
    val ungroupedText = stringResource(R.string.ungrouped)

    // 当搜索查询改变时，执行内容搜索（带防抖延迟）
    LaunchedEffect(searchQuery, chatHistories) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isBlank()) {
            matchedChatIdsByContent = emptySet()
            isSearching = false
            return@LaunchedEffect
        }

        val hasTitleOrGroupMatch =
            chatHistories.any { history ->
                history.title.contains(trimmedQuery, ignoreCase = true) ||
                    (history.group?.contains(trimmedQuery, ignoreCase = true) == true)
            }

        val shouldSearchByContent = !hasTitleOrGroupMatch && trimmedQuery.length >= 2
        if (!shouldSearchByContent) {
            matchedChatIdsByContent = emptySet()
            isSearching = false
            return@LaunchedEffect
        }

        // 延迟400ms，如果用户继续输入则取消本次搜索（LaunchedEffect会自动取消）
        delay(400)
        // 注意：如果 searchQuery 在延迟期间改变，LaunchedEffect 会重新启动，这里检查的是当前值
        isSearching = true
        try {
            matchedChatIdsByContent = chatHistoryManager.searchChatIdsByContent(trimmedQuery)
        } catch (e: Exception) {
            matchedChatIdsByContent = emptySet()
        } finally {
            isSearching = false
        }
    }

    val filteredHistories = remember(chatHistories, searchQuery, matchedChatIdsByContent) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isNotBlank()) {
            chatHistories.filter { history ->
                val matchesTitleOrGroup = history.title.contains(trimmedQuery, ignoreCase = true) ||
                        (history.group?.contains(trimmedQuery, ignoreCase = true) == true)
                val matchesContent = matchedChatIdsByContent.contains(history.id)
                matchesTitleOrGroup || matchesContent
            }
        } else {
            chatHistories
        }
    }
    val groupIdsInFilteredHistories = remember(filteredHistories) {
        filteredHistories
            .mapNotNull { it.characterGroupId?.trim()?.takeIf { id -> id.isNotBlank() } }
            .toSet()
    }
    LaunchedEffect(groupIdsInFilteredHistories, groupNameById) {
        val missingIds = groupIdsInFilteredHistories.filter { groupNameById[it].isNullOrBlank() }
        if (missingIds.isEmpty()) {
            return@LaunchedEffect
        }
        val fetched = mutableMapOf<String, String>()
        missingIds.forEach { groupId ->
            val groupName = runCatching {
                characterGroupCardManager.getCharacterGroupCard(groupId)?.name
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (!groupName.isNullOrBlank()) {
                fetched[groupId] = groupName
            }
        }
        if (fetched.isNotEmpty()) {
            resolvedGroupNameById = resolvedGroupNameById + fetched
        }
    }

    val unboundCharacterText = stringResource(R.string.unbound_character_card)
    val groupPrefix = stringResource(R.string.character_group_binding_prefix)
    val flatItems =
            remember(
                    filteredHistories,
                    collapsedGroups,
                    collapsedCharacters,
                    ungroupedText,
                    unboundCharacterText,
                    availableCharacterGroups,
                    groupNameById,
                    groupPrefix,
                    historyDisplayMode,
                    activeCharacterCardName
            ) {
                fun characterKey(name: String) = "character::$name"
                fun groupKey(characterName: String?, groupValue: String?): String {
                    val characterPart = characterName ?: "all"
                    val groupPart = groupValue ?: "ungrouped"
                    return "group::$characterPart::$groupPart"
                }

                when (historyDisplayMode) {
                    ChatHistoryDisplayMode.BY_CHARACTER_CARD -> {
                        data class BindingBucket(
                            val key: String,
                            val displayName: String,
                            val characterCardName: String?,
                            val characterGroupId: String?
                        )
                        fun resolveBindingBucket(history: ChatHistory): BindingBucket {
                            val groupId = history.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
                            if (!groupId.isNullOrBlank()) {
                                val groupName = groupNameById[groupId]?.takeIf { it.isNotBlank() }
                                val displayName =
                                    if (!groupName.isNullOrBlank()) {
                                        "$groupPrefix: $groupName"
                                    } else {
                                        context.getString(R.string.missing_character_group_id, groupId)
                                    }
                                return BindingBucket(
                                    key = "binding::group::$groupId",
                                    displayName = displayName,
                                    characterCardName = null,
                                    characterGroupId = groupId
                                )
                            }

                            val cardName = history.characterCardName?.takeIf { it.isNotBlank() }
                            if (!cardName.isNullOrBlank()) {
                                return BindingBucket(
                                    key = "binding::card::$cardName",
                                    displayName = cardName,
                                    characterCardName = cardName,
                                    characterGroupId = null
                                )
                            }

                            return BindingBucket(
                                key = "binding::unbound",
                                displayName = unboundCharacterText,
                                characterCardName = null,
                                characterGroupId = null
                            )
                        }

                        filteredHistories
                            .groupBy { resolveBindingBucket(it) }
                            .flatMap { (bindingBucket, histories) ->
                                val cKey = characterKey(bindingBucket.key)
                                val children =
                                        if (collapsedCharacters.contains(cKey)) {
                                            emptyList()
                                        } else {
                                            histories
                                                .groupBy { it.group }
                                                .flatMap { (groupValue, groupedHistories) ->
                                                    val displayName = groupValue ?: ungroupedText
                                                    val gKey = groupKey(bindingBucket.key, groupValue)
                                                    val header =
                                                            HistoryListItem.Header(
                                                                    key = gKey,
                                                                    name = displayName,
                                                                    groupValue = groupValue,
                                                                    characterCardName = bindingBucket.characterCardName
                                                            )
                                                    val items =
                                                            if (collapsedGroups.contains(gKey)) {
                                                                emptyList()
                                                            } else {
                                                                groupedHistories.map {
                                                                    HistoryListItem.Item(it)
                                                                }
                                                    }
                                                    listOf(header) + items
                                                }
                                        }
                                listOf(
                                    HistoryListItem.CharacterHeader(
                                        key = cKey,
                                        name = bindingBucket.displayName,
                                        characterCardName = bindingBucket.characterCardName,
                                        characterGroupId = bindingBucket.characterGroupId
                                    )
                                ) + children
                            }
                    }
                    else -> {
                        filteredHistories
                            .groupBy { it.group }
                            .flatMap { (groupValue, histories) ->
                                val displayName = groupValue ?: ungroupedText
                                val gKey = groupKey(null, groupValue)
                                // 在仅显示当前角色卡模式下，使用当前角色卡名称
                                val effectiveCharacterCardName = if (historyDisplayMode == ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY) {
                                    activeCharacterCardName
                                } else {
                                    null
                                }
                                val header =
                                        HistoryListItem.Header(
                                                key = gKey,
                                                name = displayName,
                                                groupValue = groupValue,
                                                characterCardName = effectiveCharacterCardName
                                        )
                                val items =
                                        if (collapsedGroups.contains(gKey)) {
                                            emptyList()
                                        } else {
                                            histories.map { HistoryListItem.Item(it) }
                                        }
                                listOf(header) + items
                            }
                    }
                }
            }

    val reorderableState = rememberReorderableLazyListState(actualLazyListState) { from, to ->
        val movedItem = flatItems.getOrNull(from.index) as? HistoryListItem.Item
                ?: return@rememberReorderableLazyListState

        val reorderedFlatList = flatItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }

        var newGroup: String? = null
        var newCharacterCardName: String? = null
        var newCharacterGroupId: String? = null
        val newOrderedHistories =
                reorderedFlatList
                    .mapNotNull {
                        when (it) {
                            is HistoryListItem.CharacterHeader -> {
                                // 在绑定分类模式下，更新当前绑定（角色卡/群组）
                                newCharacterCardName = it.characterCardName
                                newCharacterGroupId = it.characterGroupId
                                newGroup = null
                                null
                            }
                            is HistoryListItem.Header -> {
                                newGroup = it.groupValue
                                null
                            }
                            is HistoryListItem.Item -> {
                                // 根据当前显示模式决定是否更新 characterCardName
                                val updatedHistory = if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                    it.history.copy(
                                        group = newGroup,
                                        characterCardName = newCharacterCardName,
                                        characterGroupId = newCharacterGroupId
                                    )
                                } else {
                                    it.history.copy(group = newGroup)
                                }
                                updatedHistory
                            }
                        }
                    }
                    .mapIndexed { index, history -> history.copy(displayOrder = index.toLong()) }

        val finalMovedItem =
                newOrderedHistories.find { it.id == movedItem.history.id }
                        ?: return@rememberReorderableLazyListState

        // 如果角色卡绑定发生了变化，需要额外通知
        if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD &&
            (
                finalMovedItem.characterCardName != movedItem.history.characterCardName ||
                    finalMovedItem.characterGroupId != movedItem.history.characterGroupId
            )
        ) {
            onUpdateChatBinding(
                finalMovedItem.id,
                finalMovedItem.characterCardName,
                finalMovedItem.characterGroupId
            )
        }

        onUpdateChatOrderAndGroup(newOrderedHistories, finalMovedItem, finalMovedItem.group)
    }

    if (chatItemActionTarget != null) {
        val resolvedTargetChat = remember(chatItemActionTarget, chatHistories) {
            val target = chatItemActionTarget
            if (target == null) {
                null
            } else {
                chatHistories.firstOrNull { it.id == target.id } ?: target
            }
        }
        // 只关心当前菜单目标的状态，其余会话的生成结果留在 ViewModel 里不影响这里
        val targetChatId = resolvedTargetChat?.id
        val aiTitleState = targetChatId?.let { aiTitleStates[it] }
        // 菜单以任何方式收起都清掉上次的结果，避免下次长按看到过期的错误
        DisposableEffect(targetChatId) {
            onDispose { targetChatId?.let(onDismissAiTitleResult) }
        }
        Dialog(onDismissRequest = { chatItemActionTarget = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.chat_history),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Text(
                        text = resolvedTargetChat?.title ?: chatItemActionTarget!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 编辑选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.edit_title)
                            }
                            .clickable {
                                chatToEdit = chatItemActionTarget
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.edit_title), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // AI 总结标题选项：显式触发时才会覆盖已有标题
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.ai_summary_title)
                            }
                            .clickable(enabled = aiTitleState !is AiTitleGenerationState.Running) {
                                val targetChat = resolvedTargetChat ?: return@clickable
                                onGenerateAiTitle(targetChat.id)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (aiTitleState is AiTitleGenerationState.Running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clearAndSetSemantics {}
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ai_summary_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clearAndSetSemantics {}
                                )
                                when (aiTitleState) {
                                    is AiTitleGenerationState.Running -> {
                                        Text(
                                            text = stringResource(R.string.ai_summary_title_generating),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    is AiTitleGenerationState.Failed -> {
                                        Text(
                                            text = aiTitleState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    null -> Unit
                                }
                            }
                            if (aiTitleState is AiTitleGenerationState.Failed) {
                                TextButton(onClick = { targetChatId?.let(onGenerateAiTitle) }) {
                                    Text(stringResource(R.string.ai_summary_title_retry))
                                }
                            }
                        }
                    }

                    // 上移选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.move_up)
                            }
                            .clickable {
                                val targetChat = chatItemActionTarget!!
                                val currentIndex = filteredHistories.indexOfFirst { it.id == targetChat.id }
                                if (currentIndex > 0) {
                                    val newHistories = filteredHistories.toMutableList()
                                    newHistories.removeAt(currentIndex)
                                    newHistories.add(currentIndex - 1, targetChat)
                                    val reorderedHistories = newHistories.mapIndexed { index, history ->
                                        history.copy(displayOrder = index.toLong())
                                    }
                                    onUpdateChatOrderAndGroup(reorderedHistories, targetChat, targetChat.group)
                                }
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.move_up), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // 下移选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.move_down)
                            }
                            .clickable {
                                val targetChat = chatItemActionTarget!!
                                val currentIndex = filteredHistories.indexOfFirst { it.id == targetChat.id }
                                if (currentIndex >= 0 && currentIndex < filteredHistories.size - 1) {
                                    val newHistories = filteredHistories.toMutableList()
                                    newHistories.removeAt(currentIndex)
                                    newHistories.add(currentIndex + 1, targetChat)
                                    val reorderedHistories = newHistories.mapIndexed { index, history ->
                                        history.copy(displayOrder = index.toLong())
                                    }
                                    onUpdateChatOrderAndGroup(reorderedHistories, targetChat, targetChat.group)
                                }
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.move_down), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }

                    // 置顶/取消置顶选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription =
                                    if (resolvedTargetChat?.pinned == true) {
                                        context.getString(R.string.unpin_chat)
                                    } else {
                                        context.getString(R.string.pin_chat)
                                    }
                            }
                            .clickable {
                                val targetChat = resolvedTargetChat!!
                                val newPinned = !targetChat.pinned
                                coroutineScope.launch {
                                    chatHistoryManager.updateChatPinned(targetChat.id, newPinned)
                                }
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = if (resolvedTargetChat?.pinned == true) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }

                    // 锁定/解锁选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription =
                                    if (resolvedTargetChat?.locked == true) {
                                        context.getString(R.string.unlock_chat)
                                    } else {
                                        context.getString(R.string.lock_chat)
                                    }
                            }
                            .clickable {
                                val targetChat = resolvedTargetChat ?: chatItemActionTarget!!
                                val newLocked = !targetChat.locked
                                coroutineScope.launch {
                                    chatHistoryManager.updateChatLocked(targetChat.id, newLocked)
                                }
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (resolvedTargetChat?.locked == true) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = if (resolvedTargetChat?.locked == true) stringResource(R.string.unlock_chat) else stringResource(R.string.lock_chat),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // 删除选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.delete)
                            }
                            .clickable {
                                promptDeleteChat(chatItemActionTarget!!)
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.delete), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            resolvedTargetChat?.id?.let(onDismissAiTitleResult)
                            chatItemActionTarget = null
                        },
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (groupActionTarget != null) {
        Dialog(onDismissRequest = { groupActionTarget = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.manage_group),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Text(
                        text = groupActionTarget!!.groupName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 重命名选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.rename_group)
                            }
                            .clickable {
                                groupToRename = groupActionTarget
                                groupActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DriveFileRenameOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.rename_group), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // 删除选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.delete_group)
                            }
                            .clickable {
                                groupToDelete = groupActionTarget
                                groupActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.delete_group), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { groupActionTarget = null },
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (groupToRename != null) {
        var newGroupNameText by remember(groupToRename) { mutableStateOf(groupToRename!!.groupName) }
        AlertDialog(
            onDismissRequest = { groupToRename = null },
            title = { Text(stringResource(R.string.rename_group)) },
            text = {
                OutlinedTextField(
                    value = newGroupNameText,
                    onValueChange = { newGroupNameText = it },
                    label = { Text(stringResource(R.string.new_group_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupNameText.isNotBlank() && newGroupNameText != groupToRename!!.groupName) {
                            onUpdateGroupName(
                                groupToRename!!.groupName, 
                                newGroupNameText,
                                groupToRename!!.characterCardName
                            )
                        }
                        groupToRename = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToRename = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (groupToDelete != null) {
        Dialog(onDismissRequest = { groupToDelete = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.confirm_delete_group),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = groupToDelete!!.groupName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.choose_delete_method),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            onDeleteGroup(
                                groupToDelete!!.groupName, 
                                true,
                                groupToDelete!!.characterCardName
                            )
                            groupToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.delete_group_and_chats),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.delete_operation_irreversible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
            }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            onDeleteGroup(
                                groupToDelete!!.groupName, 
                                false,
                                groupToDelete!!.characterCardName
                            )
                            groupToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.delete_group_only),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.chats_move_to_ungrouped),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { groupToDelete = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (chatToEdit != null) {
        val editingChat = chatToEdit!!
        var newTitle by remember(editingChat) { mutableStateOf(editingChat.title) }
        var selectedCharacterCardName by remember(editingChat) {
            mutableStateOf(editingChat.characterCardName)
        }
        var selectedCharacterGroupId by remember(editingChat) {
            mutableStateOf(editingChat.characterGroupId)
        }
        var bindingMenuExpanded by remember { mutableStateOf(false) }
        data class ChatBindingOption(
            val label: String,
            val characterCardName: String?,
            val characterGroupId: String?
        )
        val unboundLabel = stringResource(R.string.unbound_character_card)
        val bindingLabel = stringResource(R.string.bind_character_card)
        val bindingHint = stringResource(R.string.chat_binding_scope_hint)
        val groupPrefix = stringResource(R.string.character_group_binding_prefix)
        val bindingOptions = remember(availableCharacterCards, availableCharacterGroups, unboundLabel, groupPrefix) {
            buildList {
                add(ChatBindingOption(unboundLabel, null, null))
                availableCharacterCards.forEach { card ->
                    add(ChatBindingOption(card.name, card.name, null))
                }
                availableCharacterGroups.forEach { group ->
                    add(ChatBindingOption("$groupPrefix: ${group.name}", null, group.id))
                }
            }
        }
        val selectedBindingLabel = remember(
            selectedCharacterCardName,
            selectedCharacterGroupId,
            groupNameById,
            unboundLabel,
            groupPrefix
        ) {
            when {
                !selectedCharacterGroupId.isNullOrBlank() -> {
                    val normalizedGroupId = selectedCharacterGroupId?.trim()?.takeIf { it.isNotBlank() }
                    val groupName = normalizedGroupId?.let { groupNameById[it] }?.takeIf { it.isNotBlank() }
                    if (!groupName.isNullOrBlank()) {
                        "$groupPrefix: $groupName"
                    } else {
                        context.getString(R.string.missing_character_group_id, normalizedGroupId ?: "")
                    }
                }
                !selectedCharacterCardName.isNullOrBlank() -> selectedCharacterCardName!!
                else -> unboundLabel
            }
        }
        val density = LocalDensity.current
        var bindingMenuWidth by remember { mutableStateOf(0.dp) }
        val dropdownClickSource = remember { MutableInteractionSource() }

        AlertDialog(
                onDismissRequest = { chatToEdit = null },
                title = { Text(stringResource(R.string.edit_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                label = { Text(stringResource(R.string.new_title)) },
                                modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                    value = selectedBindingLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(bindingLabel) },
                                    trailingIcon = {
                                        Icon(
                                                imageVector = if (bindingMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                        )
                                    },
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coordinates ->
                                                bindingMenuWidth = with(density) { coordinates.size.width.toDp() }
                                            }
                            )
                            Box(
                                    modifier = Modifier
                                            .matchParentSize()
                                            .clickable(
                                                    interactionSource = dropdownClickSource,
                                                    indication = null
                                            ) { bindingMenuExpanded = !bindingMenuExpanded }
                            )
                            DropdownMenu(
                                    expanded = bindingMenuExpanded,
                                    onDismissRequest = { bindingMenuExpanded = false },
                                    modifier = if (bindingMenuWidth > 0.dp) Modifier.width(bindingMenuWidth) else Modifier
                            ) {
                                bindingOptions.forEach { option ->
                                    DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                selectedCharacterCardName = option.characterCardName
                                                selectedCharacterGroupId = option.characterGroupId
                                                bindingMenuExpanded = false
                                            }
                                    )
                                }
                            }
                        }
                        Text(
                                text = bindingHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (newTitle != editingChat.title) {
                                    onUpdateChatTitle(editingChat.id, newTitle)
                                }
                                if (
                                    selectedCharacterCardName != editingChat.characterCardName ||
                                    selectedCharacterGroupId != editingChat.characterGroupId
                                ) {
                                    onUpdateChatBinding(
                                        editingChat.id,
                                        selectedCharacterCardName,
                                        selectedCharacterGroupId
                                    )
                                }
                                chatToEdit = null
                            }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatToEdit = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
    }

    if (showSettingsDialog) {
        val dialogMetrics = rememberCompactDialogMetrics()
        val outerPadding = if (dialogMetrics.isCompact) 8.dp else 16.dp
        val contentPadding = if (dialogMetrics.isCompact) 12.dp else 16.dp
        val scrollState = rememberCompactDialogScrollState()
        val cardModifier =
            Modifier
                .fillMaxWidth()
                .padding(outerPadding)
                .let { base ->
                    if (dialogMetrics.isCompact) {
                        base.heightIn(max = dialogMetrics.maxHeight - outerPadding * 2)
                    } else {
                        base
                    }
                }
        val contentModifier =
            Modifier
                .padding(contentPadding)
                .verticalScrollWhenCompact(dialogMetrics, scrollState)

        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(modifier = contentModifier) {
                    Text(
                        text = stringResource(R.string.chat_history_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = stringResource(R.string.chat_display_mode),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    listOf(
                        Triple(
                            ChatHistoryDisplayMode.BY_CHARACTER_CARD,
                            stringResource(R.string.history_filter_role_card),
                            stringResource(R.string.history_filter_role_card_desc)
                        ),
                        Triple(
                            ChatHistoryDisplayMode.BY_FOLDER,
                            stringResource(R.string.history_filter_folder),
                            stringResource(R.string.history_filter_folder_desc)
                        ),
                        Triple(
                            ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY,
                            stringResource(R.string.history_filter_current_card),
                            stringResource(R.string.history_filter_current_card_desc)
                        )
                    ).forEach { (mode, title, description) ->
                        val selected = historyDisplayMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onDisplayModeChange(mode)
                                },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                onAutoSwitchCharacterCardChange(!autoSwitchCharacterCard)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.history_auto_switch_character),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.history_auto_switch_character_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoSwitchCharacterCard,
                                onCheckedChange = onAutoSwitchCharacterCardChange,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                onAutoSwitchChatOnCharacterSelectChange(!autoSwitchChatOnCharacterSelect)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.history_auto_switch_chat),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.history_auto_switch_chat_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoSwitchChatOnCharacterSelect,
                                onCheckedChange = onAutoSwitchChatOnCharacterSelectChange,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = { showSettingsDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (showNewGroupDialog) {
        AlertDialog(
                onDismissRequest = { showNewGroupDialog = false },
                title = { Text(stringResource(R.string.new_group)) },
                text = {
                    OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text(stringResource(R.string.group_name)) },
                            modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    val normalizedGroupName = newGroupName.trim()
                                    if (normalizedGroupName.isBlank()) {
                                        return@Button
                                    }
                                    val (characterCardName, characterGroupId) = resolveBindingForCreate(
                                        historyDisplayMode = historyDisplayMode,
                                        activePrompt = activePrompt,
                                        activeCharacterCardName = activeCharacterCardName
                                    )
                                    onCreateGroup(normalizedGroupName, characterCardName, characterGroupId)
                                    newGroupName = ""
                                    showNewGroupDialog = false
                                }
                            }
                    ) {
                        Text(stringResource(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewGroupDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSearchBox = !showSearchBox },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (showSearchBox) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = if (showSearchBox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 新建对话按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { 
                    val (characterCardName, characterGroupId) = resolveBindingForCreate(
                        historyDisplayMode = historyDisplayMode,
                        activePrompt = activePrompt,
                        activeCharacterCardName = activeCharacterCardName
                    )
                    onNewChat(characterCardName, characterGroupId)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.new_chat))
            }
            IconButton(
                onClick = { showNewGroupDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.AddCircleOutline,
                    contentDescription = stringResource(R.string.new_group),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 搜索框
        if (showSearchBox) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.search)) },
                placeholder = { Text(stringResource(R.string.search_chat_history_hint)) },
                leadingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank() && !isSearching) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.SearchOff, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        var showSwipeHint by rememberLocal(key = "show_swipe_hint", defaultValue = true)

        if (showSwipeHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showSwipeHint = false },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.swipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 2.dp)
        ) {
            LazyColumn(
                state = actualLazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 22.dp)
            ) {
                items(
                    items = flatItems,
                    key = {
                        when (it) {
                            is HistoryListItem.CharacterHeader -> it.key
                            is HistoryListItem.Header -> it.key
                            is HistoryListItem.Item -> it.history.id
                        }
                    }
                ) { item ->
                    when (item) {
                        is HistoryListItem.CharacterHeader -> {
                        val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
                        val groupId = item.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
                        val groupAvatarUri by remember(groupId) {
                            groupId?.let { userPreferencesManager.getAiAvatarForCharacterGroupFlow(it) }
                                ?: flowOf(null)
                        }.collectAsState(initial = null)
                        val groupFallbackMemberCardId = remember(groupId, availableCharacterGroups) {
                            val group = if (groupId.isNullOrBlank()) null else {
                                availableCharacterGroups.firstOrNull { it.id == groupId }
                            }
                            val sortedMembers = group?.members?.sortedBy { it.orderIndex }.orEmpty()
                            sortedMembers.firstOrNull()?.characterCardId
                        }
                        val groupFallbackMemberAvatarUri by remember(groupFallbackMemberCardId) {
                            groupFallbackMemberCardId?.let {
                                userPreferencesManager.getAiAvatarForCharacterCardFlow(it)
                            } ?: flowOf(null)
                        }.collectAsState(initial = null)
                        val characterCardId = remember(item.characterCardName, availableCharacterCards) {
                            val cardName = item.characterCardName?.takeIf { it.isNotBlank() } ?: return@remember null
                            availableCharacterCards.firstOrNull { it.name == cardName }?.id
                        }
                        val characterCardAvatarUri by remember(characterCardId) {
                            characterCardId?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) }
                                ?: flowOf(null)
                        }.collectAsState(initial = null)
                        val avatarUri =
                            if (!groupId.isNullOrBlank()) {
                                groupAvatarUri ?: groupFallbackMemberAvatarUri
                            } else {
                                characterCardAvatarUri
                            }
                        
                        val isExpanded = !collapsedCharacters.contains(item.key)
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp)
                                .semantics {
                                    contentDescription = "${item.name}, $stateDescription"
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            collapsedCharacters =
                                                if (collapsedCharacters.contains(item.key)) {
                                                    collapsedCharacters - item.key
                                                } else {
                                                    collapsedCharacters + item.key
                                                }
                                        }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, bottomStart = 4.dp)
                                    )
                                    .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (avatarUri != null) Color.Transparent 
                                            else MaterialTheme.colorScheme.primaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarUri != null) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clearAndSetSemantics {},
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (!groupId.isNullOrBlank()) Icons.Default.Groups else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clearAndSetSemantics {}
                                        )
                                    }
                                }
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clearAndSetSemantics {}
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .padding(horizontal = 8.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            Icon(
                                imageVector = if (collapsedCharacters.contains(item.key)) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                        }
                    }
                    is HistoryListItem.Header -> {
                        val isExpanded = !collapsedGroups.contains(item.key)
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .padding(start = 16.dp, end = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(40.dp)
                                            .align(Alignment.Center)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(1.dp)
                                            )
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .semantics {
                                        contentDescription = "${item.name}, $stateDescription"
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                collapsedGroups = if (collapsedGroups.contains(item.key)) {
                                                    collapsedGroups - item.key
                                                } else {
                                                    collapsedGroups + item.key
                                                }
                                            },
                                            onLongPress = {
                                                if (item.name != ungroupedText) {
                                                    groupActionTarget = GroupTarget(
                                                        groupName = item.name,
                                                        characterCardName = item.characterCardName
                                                    )
                                                    hasLongPressedGroup = true
                                                }
                                            }
                                        )
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = 2.dp,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clearAndSetSemantics {}
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.clearAndSetSemantics {}
                                            )
                                            if (item.name != ungroupedText && !hasLongPressedGroup) {
                                                Text(
                                                    text = " (" + stringResource(R.string.long_press_manage) + ")",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.clearAndSetSemantics {}
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = if (collapsedGroups.contains(item.key)) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        modifier = Modifier.clearAndSetSemantics {}
                                    )
                                }
                            }
                        }
                    }
                    is HistoryListItem.Item -> {
                        val deleteAction = SwipeAction(
                            onSwipe = { promptDeleteChat(item.history) },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = Color.White
                                )
                            },
                            background = MaterialTheme.colorScheme.error
                        )

                        val editAction = SwipeAction(
                            onSwipe = { chatToEdit = item.history },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_title),
                                    tint = Color.White
                                )
                            },
                            background = MaterialTheme.colorScheme.primary
                        )

                        ReorderableItem(
                            reorderableState,
                            key = item.history.id,
                            animateItemModifier = Modifier.animateItem(placementSpec = null)
                        ) { isDragging ->
                            val isDeleting = deletingChatIds.contains(item.history.id)
                            val isSelected = item.history.id == currentId
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isDeleting,
                                exit =
                                    shrinkVertically(
                                        animationSpec = tween(deleteAnimationDurationMs.toInt()),
                                        shrinkTowards = Alignment.Top
                                    ) + fadeOut(animationSpec = tween(deleteAnimationDurationMs.toInt()))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                        Box(
                                            modifier = Modifier
                                                .width(32.dp)
                                                .padding(start = 16.dp, end = 8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(50.dp)
                                                    .align(Alignment.Center)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(1.dp)
                                                    )
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SwipeableActionsBox(
                                            startActions = listOf(editAction),
                                            endActions = listOf(deleteAction),
                                            swipeThreshold = 100.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(MaterialTheme.shapes.medium)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth(),
                                                color = containerColor,
                                                shape = MaterialTheme.shapes.medium,
                                                shadowElevation = if (isDragging) 8.dp else 0.dp
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                ) {
                                                    val titlePreview = item.history.title.take(20)
                                                    val groupName = item.history.group ?: ungroupedText
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp)
                                                            .semantics(mergeDescendants = false) {
                                                                contentDescription = "$titlePreview, $groupName"
                                                            }
                                                            .pointerInput(Unit) {
                                                                detectTapGestures(
                                                                    onTap = { onSelectChat(item.history.id) },
                                                                    onLongPress = { chatItemActionTarget = item.history }
                                                                )
                                                            },
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val dragDescription = stringResource(R.string.drag_item, item.history.title)
                                                        IconButton(
                                                            modifier = Modifier
                                                                .draggableHandle()
                                                                .semantics {
                                                                    contentDescription = dragDescription
                                                                },
                                                            onClick = {}
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.DragHandle,
                                                                contentDescription = null,
                                                                tint = contentColor
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .semantics { contentDescription = "" }
                                                        ) {
                                                            Text(
                                                                text = item.history.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = contentColor,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )

                                                            // 如果是分支，在右侧显示分支图标和父对话标题
                                                            if (item.history.parentChatId != null) {
                                                                val parentChat = chatHistories.find { it.id == item.history.parentChatId }
                                                                if (parentChat != null) {
                                                                    Spacer(modifier = Modifier.height(2.dp))
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        modifier = Modifier.semantics { contentDescription = "" }
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.AccountTree,
                                                                            contentDescription = null,
                                                                            tint = contentColor.copy(alpha = 0.6f),
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text(
                                                                            text = parentChat.title,
                                                                            style = MaterialTheme.typography.bodySmall,
                                                                            color = contentColor.copy(alpha = 0.6f),
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        if (activeStreamingChatIds.contains(item.history.id)) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(12.dp),
                                                                strokeWidth = 1.5.dp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        if (item.history.pinned) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.PushPin,
                                                                contentDescription = null,
                                                                tint = contentColor.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        if (item.history.locked) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = null,
                                                                tint = contentColor.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            HistoryQuickScroller(
                listState = actualLazyListState,
                itemCount = flatItems.size,
                onInteractionChange = onQuickScrollInteractionChange,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 12.dp, bottom = 12.dp)
            )
        }
    }
}

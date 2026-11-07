package com.ai.assistance.operit.ui.features.chat.components

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.material3.FilterChip
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatHistoryDisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatFolderEntity
import com.ai.assistance.operit.data.model.SYSTEM_UNGROUPED_FOLDER_ID
import com.ai.assistance.operit.ui.features.chat.historytree.HistoryTreeNode
import com.ai.assistance.operit.ui.features.chat.historytree.HistoryTreeProjection
import com.ai.assistance.operit.ui.features.chat.historytree.UNGROUPED_FOLDER_STABLE_KEY
import com.ai.assistance.operit.ui.features.chat.historytree.buildVisibleHistoryTree
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.HistorySiblingKind
import com.ai.assistance.operit.data.repository.HistorySiblingSnapshot
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class GroupTarget(
    val folderId: String,
    val groupName: String,
)

private const val MAX_CHAT_FOLDER_DEPTH = 3
private val HistoryTreeIndent = 6.dp
private val HistoryRowMinHeight = 44.dp

private data class FolderDragTarget(
    val targetParentFolderId: String?,
    val anchorNodeKey: String? = null,
    val insertBeforeAnchor: Boolean? = null,
    val nestFolderId: String?,
    val hoverStartedAt: Long,
)

private data class ChatDragTarget(
    val targetFolderId: String?,
    val anchorNodeKey: String? = null,
    val insertBeforeAnchor: Boolean? = null,
    val nestFolderId: String? = null,
    val nestImmediately: Boolean = false,
    val hoverStartedAt: Long = 0L,
)

private data class PendingHistoryMoveAck(
    val expectedSiblingsByParent: Map<String?, List<HistorySiblingSnapshot>>,
    val repositoryCallCompleted: Boolean = false,
)

private data class HistorySiblingStructure(
    val kind: HistorySiblingKind,
    val id: String,
    val parentFolderId: String?,
    val displayOrder: Long,
)

private fun HistorySiblingSnapshot.structure(): HistorySiblingStructure =
    HistorySiblingStructure(
        kind = kind,
        id = id,
        parentFolderId = parentFolderId,
        displayOrder = displayOrder,
    )

// Mutation snapshots include hidden normal/branch chats, matching ChatFolderRepository.siblings.
// The displayed and searchable lists use their own visibility filters.
internal fun buildHistorySiblingSnapshot(
    parentFolderId: String?,
    folders: List<ChatFolderEntity>,
    chats: List<ChatHistory>,
): List<HistorySiblingSnapshot> =
    (
        folders
            .asSequence()
            .filter { it.parentFolderId == parentFolderId }
            .map(HistorySiblingSnapshot::fromFolder) +
            chats
                .asSequence()
                .filter {
                    it.folderId == parentFolderId
                }
                .map {
                    HistorySiblingSnapshot(
                        kind = HistorySiblingKind.CHAT,
                        id = it.id,
                        parentFolderId = it.folderId,
                        displayOrder = it.displayOrder,
                        pinned = it.pinned,
                        isFavorite = it.isFavorite,
                        characterCardName = it.characterCardName,
                        characterGroupId = it.characterGroupId,
                    )
                }
    ).sortedWith(
        compareBy<HistorySiblingSnapshot> { it.displayOrder }
            .thenBy { it.kind }
            .thenBy { it.id }
    ).toList()

internal fun buildVisibleHistorySiblingSnapshot(
    parentFolderId: String?,
    folders: List<ChatFolderEntity>,
    chats: List<ChatHistory>,
): List<HistorySiblingSnapshot> =
    buildHistorySiblingSnapshot(parentFolderId, folders, chats)

internal fun resolveBindingForCreate(
    historyDisplayMode: ChatHistoryDisplayMode,
    activePrompt: ActivePrompt,
    activeCharacterCardName: String?
): Pair<String?, String?> {
    if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) {
        return when (val prompt = activePrompt) {
            is ActivePrompt.CharacterGroup -> Pair(null, prompt.id)
            is ActivePrompt.CharacterCard -> Pair(null, null)
        }
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
        val folderId: String? = null,
        val characterCardName: String? = null,
        val depth: Int = 1,
    ) : HistoryListItem
    data class Item(val history: ChatHistory, val depth: Int = 0) : HistoryListItem
}

@Composable
private fun HistoryTimeSectionHeader(section: ChatHistoryTimeSection) {
    val labelRes =
        when (section) {
            ChatHistoryTimeSection.TODAY -> R.string.chat_history_time_today
            ChatHistoryTimeSection.YESTERDAY -> R.string.chat_history_time_yesterday
            ChatHistoryTimeSection.MONDAY -> R.string.chat_history_time_monday
            ChatHistoryTimeSection.TUESDAY -> R.string.chat_history_time_tuesday
            ChatHistoryTimeSection.WEDNESDAY -> R.string.chat_history_time_wednesday
            ChatHistoryTimeSection.THURSDAY -> R.string.chat_history_time_thursday
            ChatHistoryTimeSection.FRIDAY -> R.string.chat_history_time_friday
            ChatHistoryTimeSection.SATURDAY -> R.string.chat_history_time_saturday
            ChatHistoryTimeSection.SUNDAY -> R.string.chat_history_time_sunday
            ChatHistoryTimeSection.SEVEN_DAYS_AGO -> R.string.chat_history_time_seven_days_ago
        }
    Text(
        text = stringResource(labelRes),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun rememberCurrentLocalDateContext(): CurrentLocalDateContext {
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentContext by remember { mutableStateOf(currentLocalDateContext()) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    currentContext = currentLocalDateContext()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val zoneId = ZoneId.systemDefault()
            delay(localDateRefreshDelayMillis(Instant.now(), zoneId))
            currentContext = currentLocalDateContext()
        }
    }
    return currentContext
}

private data class CurrentLocalDateContext(
    val date: LocalDate,
    val zoneId: ZoneId,
)

private fun currentLocalDateContext(): CurrentLocalDateContext {
    val zoneId = ZoneId.systemDefault()
    return CurrentLocalDateContext(LocalDate.now(zoneId), zoneId)
}

/** Reserves room at the row start for the line drawn by [historyGuideLine]. */
@Composable
private fun HistoryHierarchyGuide() {
    Spacer(modifier = Modifier.width(8.dp))
}

/** Draws the hierarchy line over the whole row height, so taller rows never leave a gap. */
@Composable
private fun Modifier.historyGuideLine(enabled: Boolean): Modifier {
    if (!enabled) return this
    val color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    return drawBehind {
        val lineWidth = 2.dp.toPx()
        val inset = 2.dp.toPx()
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - inset - lineWidth else inset
        drawRoundRect(
            color = color,
            topLeft = Offset(x, 0f),
            size = Size(lineWidth, size.height),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
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

@Composable
internal fun ChatHistoryCategoryNewChatControls(
    selectedCategory: ChatHistoryCategory,
    canManageFolders: Boolean,
    onSelectedCategoryChange: (ChatHistoryCategory) -> Unit,
    onNewChat: (inheritGroupFromCurrent: Boolean) -> Unit,
    onCreateFolder: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            ChatHistoryCategory.ALL to stringResource(R.string.chat_category_all),
            ChatHistoryCategory.RECENT to stringResource(R.string.chat_category_recent),
            ChatHistoryCategory.FAVORITES to stringResource(R.string.chat_category_favorites),
        ).forEach { (category, label) ->
            FilterChip(
                modifier = Modifier.weight(1f).heightIn(min = 30.dp),
                selected = selectedCategory == category,
                onClick = { onSelectedCategoryChange(category) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = {
                onNewChat(shouldInheritCurrentFolderForNewChat(selectedCategory))
            },
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.new_chat),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                stringResource(R.string.new_chat),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            )
        }
        if (canManageFolders) {
            IconButton(
                onClick = onCreateFolder,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.AddCircleOutline,
                    contentDescription = stringResource(R.string.new_group),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun ChatHistorySelector(
        modifier: Modifier = Modifier,
        onNewChat: (
            characterCardName: String?,
            characterGroupId: String?,
            inheritGroupFromCurrent: Boolean,
        ) -> Unit,
        onCreateFolderWithInitialChat: (
            parentFolderId: String?,
            folderName: String,
            characterCardName: String?,
            characterGroupId: String?,
            onResult: (Result<String>) -> Unit,
        ) -> Unit,
        onSelectChat: (String) -> Unit,
        onDeleteChat: (String) -> Unit,
        onUpdateChatTitle: (chatId: String, newTitle: String) -> Unit,
        onUpdateChatBinding: (chatId: String, characterCardName: String?, characterGroupId: String?) -> Unit,
        chatHistories: List<ChatHistory>,
        orderingChatHistories: List<ChatHistory>,
        searchableChatHistories: List<ChatHistory>,
        chatFolders: List<ChatFolderEntity>,
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
        activePrompt: ActivePrompt,
        selectedCategory: ChatHistoryCategory,
        onSelectedCategoryChange: (ChatHistoryCategory) -> Unit,
        collapsedGroups: Set<String>,
        onCollapsedGroupsChange: (Set<String>) -> Unit,
        collapsedCharacters: Set<String>,
        onCollapsedCharactersChange: (Set<String>) -> Unit,
) {
    var chatToEdit by remember { mutableStateOf<ChatHistory?>(null) }
    var chatToDelete by remember { mutableStateOf<ChatHistory?>(null) }
    var chatItemActionTarget by remember { mutableStateOf<ChatHistory?>(null) }
    var chatToMove by remember { mutableStateOf<ChatHistory?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newFolderParentId by remember { mutableStateOf<String?>(null) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var groupActionTarget by remember { mutableStateOf<GroupTarget?>(null) }
    var groupToRename by remember { mutableStateOf<GroupTarget?>(null) }
    var groupToDelete by remember { mutableStateOf<GroupTarget?>(null) }
    var groupToMove by remember { mutableStateOf<GroupTarget?>(null) }
    var hasLongPressedGroup by rememberLocal("has_long_pressed_group", defaultValue = false)
    
    // 搜索相关状态
    var showSearchBox by remember { mutableStateOf(false) }
    var matchedChatIdsByContent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val operationFailedText = stringResource(R.string.operation_failed)
    val editTitleText = stringResource(R.string.edit_title)
    val moveToFolderText = stringResource(R.string.move_to_folder)
    val moveUpText = stringResource(R.string.move_up)
    val moveDownText = stringResource(R.string.move_down)
    val pinChatText = stringResource(R.string.pin_chat)
    val unpinChatText = stringResource(R.string.unpin_chat)
    val addToFavoritesText = stringResource(R.string.add_to_favorites)
    val removeFromFavoritesText = stringResource(R.string.remove_from_favorites)
    val lockChatText = stringResource(R.string.lock_chat)
    val unlockChatText = stringResource(R.string.unlock_chat)
    val createSubfolderText = stringResource(R.string.create_subfolder)
    val renameGroupText = stringResource(R.string.rename_group)
    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val deleteAnimationDurationMs = 220L
    var deletingChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var availableCharacterCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var availableCharacterGroups by remember { mutableStateOf<List<CharacterGroupCard>>(emptyList()) }
    var resolvedGroupNameById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val folderChoiceEntries =
        remember(chatFolders) {
            val byId = chatFolders.associateBy { it.id }
            fun pathFor(folder: ChatFolderEntity): String {
                val names = mutableListOf<String>()
                val visited = mutableSetOf<String>()
                var cursor: ChatFolderEntity? = folder
                while (cursor != null && visited.add(cursor.id)) {
                    names += cursor.name
                    cursor = cursor.parentFolderId?.let(byId::get)
                }
                return names.asReversed().joinToString(" / ")
            }
            val paths = chatFolders.associate { it.id to pathFor(it) }
            val duplicatePaths = paths.values.groupingBy { it }.eachCount()
            paths.mapValues { (id, path) ->
                val suffix = if (duplicatePaths[path] == 1) "" else " · ${id.take(8)}"
                (path + suffix) to (byId.getValue(id).name + suffix)
            }
        }
    val folderChoiceLabels = remember(folderChoiceEntries) {
        folderChoiceEntries.mapValues { it.value.first }
    }
    val folderChoiceNames = remember(folderChoiceEntries) {
        folderChoiceEntries.mapValues { it.value.second }
    }
    fun historySiblingSnapshot(parentFolderId: String?): List<HistorySiblingSnapshot> =
        buildHistorySiblingSnapshot(parentFolderId, chatFolders, orderingChatHistories)

    fun visibleHistorySiblingSnapshot(parentFolderId: String?): List<HistorySiblingSnapshot> =
        buildVisibleHistorySiblingSnapshot(parentFolderId, chatFolders, orderingChatHistories)

    fun captureHistorySiblingSnapshots(): Map<String?, List<HistorySiblingSnapshot>> {
        val foldersByParent = chatFolders.groupBy { it.parentFolderId }
        val chatsByFolder = orderingChatHistories.groupBy { it.folderId }
        return (listOf<String?>(null) + chatFolders.map { it.id })
            .distinct()
            .associateWith { parentId ->
                buildHistorySiblingSnapshot(
                    parentId, foldersByParent[parentId].orEmpty(), chatsByFolder[parentId].orEmpty(),
                )
            }
    }

    fun normalizedSnapshots(
        parentFolderId: String?,
        siblings: List<HistorySiblingSnapshot>,
    ): List<HistorySiblingSnapshot> =
        siblings.mapIndexed { index, sibling ->
            sibling.copy(
                parentFolderId = parentFolderId,
                displayOrder = index.toLong(),
            )
        }

    fun expectedVisibleReorder(
        parentFolderId: String?,
        source: List<HistorySiblingSnapshot>,
        orderedVisibleNodeKeys: List<String>,
    ): List<HistorySiblingSnapshot> {
        val visibleKeys = orderedVisibleNodeKeys.toSet()
        val byKey = source.associateBy { it.stableKey }
        val iterator = orderedVisibleNodeKeys.iterator()
        val merged =
            source.map { sibling ->
                if (sibling.stableKey in visibleKeys) {
                    requireNotNull(byKey[iterator.next()])
                } else {
                    sibling
                }
            }
        return normalizedSnapshots(parentFolderId, merged)
    }

    fun expectedAnchoredMove(
        movingNodeKey: String,
        sourceParentFolderId: String?,
        targetParentFolderId: String?,
        beforeNodeKey: String?,
        afterNodeKey: String?,
        snapshots: Map<String?, List<HistorySiblingSnapshot>>,
    ): Map<String?, List<HistorySiblingSnapshot>> {
        val source = snapshots.getValue(sourceParentFolderId)
        val moving = requireNotNull(source.firstOrNull { it.stableKey == movingNodeKey })
        val sourceAfter = source.filterNot { it.stableKey == movingNodeKey }
        val targetAfter =
            if (sourceParentFolderId == targetParentFolderId) {
                sourceAfter.toMutableList()
            } else {
                snapshots.getValue(targetParentFolderId)
                    .filterNot { it.stableKey == movingNodeKey }
                    .toMutableList()
            }
        val insertionIndex =
            when {
                beforeNodeKey != null ->
                    targetAfter.indexOfFirst { it.stableKey == beforeNodeKey }
                        .also { require(it >= 0) }
                afterNodeKey != null ->
                    targetAfter.indexOfFirst { it.stableKey == afterNodeKey }
                        .also { require(it >= 0) } + 1
                else -> targetAfter.size
            }
        targetAfter.add(
            insertionIndex.coerceIn(0, targetAfter.size),
            moving.copy(parentFolderId = targetParentFolderId),
        )
        return buildMap {
            if (sourceParentFolderId != targetParentFolderId) {
                put(
                    sourceParentFolderId,
                    normalizedSnapshots(sourceParentFolderId, sourceAfter),
                )
            }
            put(
                targetParentFolderId,
                normalizedSnapshots(targetParentFolderId, targetAfter),
            )
        }
    }

    fun visibleSiblingNodeKeys(
        items: List<HistoryListItem>,
        parentFolderId: String?,
    ): List<String> =
        items
            .mapNotNull { visibleItem ->
                when (visibleItem) {
                    is HistoryListItem.Header -> {
                        val folderId = visibleItem.folderId ?: return@mapNotNull null
                        chatFolders
                            .firstOrNull { it.id == folderId }
                            ?.takeIf { it.parentFolderId == parentFolderId }
                            ?.let { "folder:${it.id}" }
                    }
                    is HistoryListItem.Item ->
                        visibleItem.history
                            .takeIf { it.folderId == parentFolderId }
                            ?.let { "chat:${it.id}" }
                    is HistoryListItem.CharacterHeader -> null
                }
            }
            .distinct()

    fun visibleChatReorderNodeKeys(
        items: List<HistoryListItem>,
        parentFolderId: String?,
    ): List<String> =
        visibleSiblingNodeKeys(items, parentFolderId).let { keys ->
            if (parentFolderId == null) {
                keys.filter { it.startsWith("chat:") }
            } else {
                keys
            }
        }

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
                    text =
                        stringResource(R.string.delete_chat_confirmation, deletingChat.title)
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
    val historyTimeContext = rememberCurrentLocalDateContext()
    val ungroupedText = stringResource(R.string.ungrouped)
    val categoryHistories =
        remember(chatHistories, selectedCategory) {
            selectChatHistoriesForCategory(chatHistories, selectedCategory)
        }
    val searchCandidateHistories =
        remember(categoryHistories, searchableChatHistories, searchQuery) {
            if (searchQuery.isBlank()) categoryHistories else searchableChatHistories
        }
    val canReorder = canReorderChatHistory(selectedCategory, searchQuery)
    val canManageFolders =
        canManageChatFolders(selectedCategory) && searchQuery.isBlank()
    val folderById = remember(chatFolders) { chatFolders.associateBy { it.id } }
    fun canMoveFolderToParent(folderId: String, targetParentFolderId: String?): Boolean {
        if (targetParentFolderId == null) return true
        if (
            targetParentFolderId == folderId ||
                targetParentFolderId == SYSTEM_UNGROUPED_FOLDER_ID
        ) {
            return false
        }
        var targetDepth = 0
        var cursor: String? = targetParentFolderId
        val visitedParents = hashSetOf<String>()
        while (cursor != null && visitedParents.add(cursor)) {
            if (cursor == folderId) return false
            targetDepth++
            cursor = folderById[cursor]?.parentFolderId
        }
        fun subtreeHeight(currentId: String, visiting: MutableSet<String>): Int {
            if (!visiting.add(currentId)) return MAX_CHAT_FOLDER_DEPTH + 1
            val childHeight =
                chatFolders
                    .asSequence()
                    .filter { it.parentFolderId == currentId }
                    .maxOfOrNull { subtreeHeight(it.id, visiting) }
                    ?: 0
            visiting.remove(currentId)
            return childHeight + 1
        }
        return targetDepth + subtreeHeight(folderId, hashSetOf()) <=
            MAX_CHAT_FOLDER_DEPTH
    }
    val systemUngroupedReady =
        chatFolders.any { it.id == SYSTEM_UNGROUPED_FOLDER_ID }
    LaunchedEffect(chatFolders) {
        if (!systemUngroupedReady) return@LaunchedEffect
        val validKeys = chatFolders.mapTo(hashSetOf()) { "folder:${it.id}" }
        val validCollapsedGroups =
            collapsedGroups.filterTo(hashSetOf()) { key ->
                key in validKeys || key.endsWith(UNGROUPED_FOLDER_STABLE_KEY)
            }
        if (validCollapsedGroups != collapsedGroups) {
            onCollapsedGroupsChange(validCollapsedGroups)
        }
    }
    val folderPathById =
        remember(chatFolders) {
            val folderById = chatFolders.associateBy { it.id }
            chatFolders.associate { folder ->
                val names = mutableListOf<String>()
                val visited = hashSetOf<String>()
                var current: ChatFolderEntity? = folder
                while (current != null && visited.add(current.id)) {
                    names += current.name
                    current = current.parentFolderId?.let(folderById::get)
                }
                folder.id to names.asReversed().joinToString(" / ")
            }
        }

    // 当搜索查询改变时，执行内容搜索（带防抖延迟）
    LaunchedEffect(searchQuery, searchCandidateHistories) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isBlank()) {
            matchedChatIdsByContent = emptySet()
            isSearching = false
            return@LaunchedEffect
        }

        val hasTitleOrFolderMatch =
            searchCandidateHistories.any { history ->
                history.title.contains(trimmedQuery, ignoreCase = true) ||
                    (
                        history.folderId?.let(folderPathById::get)
                            ?.contains(trimmedQuery, ignoreCase = true) == true
                    )
            }

        val shouldSearchByContent = !hasTitleOrFolderMatch && trimmedQuery.length >= 2
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

    val filteredHistories =
        remember(searchCandidateHistories, searchQuery, matchedChatIdsByContent, folderPathById) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isNotBlank()) {
            searchCandidateHistories.filter { history ->
                val matchesTitleOrFolder =
                    history.title.contains(trimmedQuery, ignoreCase = true) ||
                        (
                            history.folderId?.let(folderPathById::get)
                                ?.contains(trimmedQuery, ignoreCase = true) == true
                        )
                val matchesContent = matchedChatIdsByContent.contains(history.id)
                matchesTitleOrFolder || matchesContent
            }
        } else {
            searchCandidateHistories
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
            chatFolders,
            collapsedGroups,
            collapsedCharacters,
            selectedCategory,
            historyDisplayMode,
            groupNameById,
            groupPrefix,
            unboundCharacterText,
            searchQuery,
            ungroupedText,
        ) {
            val searching = searchQuery.isNotBlank()
            val collapsedFolderIds =
                if (searching) emptySet() else collapsedGroups.mapTo(hashSetOf()) { it.substringAfterLast("folder:") }

            fun treeItems(
                histories: List<ChatHistory>,
                projection: HistoryTreeProjection,
                keyPrefix: String = "",
            ): List<HistoryListItem> {
                val includeUngroupedFolder = true
                val ungroupedFolderKey = "$keyPrefix$UNGROUPED_FOLDER_STABLE_KEY"
                return buildVisibleHistoryTree(
                    folders = chatFolders,
                    histories = histories,
                    projection = projection,
                    collapsedFolderIds = collapsedFolderIds,
                    includeUngroupedFolder = includeUngroupedFolder,
                    isUngroupedFolderCollapsed =
                        !searching && ungroupedFolderKey in collapsedGroups,
                    includedFolderIds =
                        if (searching) folderChoiceLabels.filterValues {
                            it.contains(searchQuery.trim(), ignoreCase = true)
                        }.keys else emptySet(),
                ).nodes.map { node ->
                    when (node) {
                        is HistoryTreeNode.Folder ->
                            HistoryListItem.Header(
                                key = "$keyPrefix${node.stableKey}",
                                name = node.folder.name,
                                groupValue = null,
                                folderId = node.folder.id,
                                depth = node.depth,
                            )
                        is HistoryTreeNode.Chat ->
                            HistoryListItem.Item(
                                history = node.history,
                                depth = node.depth,
                            )
                        HistoryTreeNode.Ungrouped ->
                            HistoryListItem.Header(
                                key = ungroupedFolderKey,
                                name = ungroupedText,
                                groupValue = null,
                                folderId = SYSTEM_UNGROUPED_FOLDER_ID,
                                depth = node.depth,
                            )
                    }
                }
            }

            if (selectedCategory == ChatHistoryCategory.RECENT) {
                filteredHistories.map { HistoryListItem.Item(it) }
            } else if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                data class BindingBucket(
                    val key: String,
                    val displayName: String,
                    val characterCardName: String?,
                    val characterGroupId: String?,
                )

                fun bindingBucket(history: ChatHistory): BindingBucket {
                    val characterGroupId =
                        history.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
                    if (characterGroupId != null) {
                        val groupName = groupNameById[characterGroupId]
                        return BindingBucket(
                            key = "binding:group:$characterGroupId",
                            displayName =
                                groupName?.takeIf { it.isNotBlank() }
                                    ?.let { "$groupPrefix: $it" }
                                    ?: context.getString(
                                        R.string.missing_character_group_id,
                                        characterGroupId,
                                    ),
                            characterCardName = null,
                            characterGroupId = characterGroupId,
                        )
                    }
                    val characterCardName =
                        history.characterCardName?.trim()?.takeIf { it.isNotBlank() }
                    return if (characterCardName != null) {
                        BindingBucket(
                            key = "binding:card:$characterCardName",
                            displayName = characterCardName,
                            characterCardName = characterCardName,
                            characterGroupId = null,
                        )
                    } else {
                        BindingBucket(
                            key = "binding:unbound",
                            displayName = unboundCharacterText,
                            characterCardName = null,
                            characterGroupId = null,
                        )
                    }
                }

                filteredHistories
                    .groupBy(::bindingBucket)
                    .flatMap { (bucket, histories) ->
                        val header =
                            HistoryListItem.CharacterHeader(
                                key = bucket.key,
                                name = bucket.displayName,
                                characterCardName = bucket.characterCardName,
                                characterGroupId = bucket.characterGroupId,
                            )
                        if (!searching && bucket.key in collapsedCharacters) {
                            listOf(header)
                        } else {
                            listOf(header) +
                                treeItems(
                                    histories = histories,
                                    projection =
                                        if (selectedCategory == ChatHistoryCategory.FAVORITES) {
                                            HistoryTreeProjection.FAVORITES
                                        } else {
                                            HistoryTreeProjection.FILTERED
                                        },
                                    keyPrefix = "${bucket.key}:",
                                )
                        }
                    }
            } else {
                treeItems(
                    histories = filteredHistories,
                    projection =
                        when {
                            searching -> HistoryTreeProjection.FILTERED
                            selectedCategory == ChatHistoryCategory.FAVORITES ->
                                HistoryTreeProjection.FAVORITES
                            historyDisplayMode == ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY ->
                                HistoryTreeProjection.FILTERED
                            else -> HistoryTreeProjection.ALL
                        },
                )
            }
        }

    val reorderItems =
        remember {
            mutableStateListOf<HistoryListItem>().apply { addAll(flatItems) }
        }
    var dragInProgress by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    // Neighbours slide out of the way while dragging; other list updates keep snapping as before.
    val dragPlacementSpec =
        if (dragInProgress) {
            spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset.VisibilityThreshold,
            )
        } else {
            null
        }
    var suppressedFolderDragDescendantKeys by
        remember { mutableStateOf<Set<String>>(emptySet()) }
    var suppressedFolderDragRootId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flatItems) {
        val currentSuppressedKeys =
            buildSet {
                addAll(suppressedFolderDragDescendantKeys)
                val rootId = suppressedFolderDragRootId
                val rootIndex =
                    rootId?.let { folderId ->
                        flatItems.indexOfFirst { item ->
                            item is HistoryListItem.Header && item.folderId == folderId
                        }
                    } ?: -1
                val root = flatItems.getOrNull(rootIndex) as? HistoryListItem.Header
                if (root != null) {
                    var index = rootIndex + 1
                    while (index < flatItems.size) {
                        val descendant = flatItems[index]
                        val depth =
                            when (descendant) {
                                is HistoryListItem.CharacterHeader -> 0
                                is HistoryListItem.Header -> descendant.depth
                                is HistoryListItem.Item -> descendant.depth
                            }
                        if (depth <= root.depth) break
                        add(
                            when (descendant) {
                                is HistoryListItem.CharacterHeader -> descendant.key
                                is HistoryListItem.Header -> descendant.key
                                is HistoryListItem.Item -> "chat:${descendant.history.id}"
                            }
                        )
                        index++
                    }
                }
            }
        val visibleDragItems =
            if (dragInProgress && currentSuppressedKeys.isNotEmpty()) {
                flatItems.filterNot { item ->
                    val key =
                        when (item) {
                            is HistoryListItem.CharacterHeader -> item.key
                            is HistoryListItem.Header -> item.key
                            is HistoryListItem.Item -> "chat:${item.history.id}"
                        }
                    key in currentSuppressedKeys
                }
            } else {
                flatItems
            }
        if (!dragInProgress) {
            reorderItems.clear()
            reorderItems.addAll(flatItems)
        } else {
            // Keep the optimistic order if another visible-tree update happens mid-drag.
            // Remove rows hidden by collapsing, then merge new rows next to their canonical
            // predecessor without discarding the optimistic order.
            val merged = com.ai.assistance.operit.ui.features.chat.historytree.mergeHistoryDragOrder(
                reorderItems.toList(), visibleDragItems,
            ) { item ->
                when (item) {
                    is HistoryListItem.CharacterHeader -> item.key
                    is HistoryListItem.Header -> item.key
                    is HistoryListItem.Item -> "chat:${item.history.id}"
                }
            }
            reorderItems.clear()
            reorderItems.addAll(merged)
        }
    }
    fun resetOptimisticOrder() {
        suppressedFolderDragDescendantKeys = emptySet()
        suppressedFolderDragRootId = null
        reorderItems.clear()
        reorderItems.addAll(flatItems)
    }
    fun prepareOptimisticFolderDrag(folderId: String) {
        suppressedFolderDragRootId = folderId
        val headerIndex =
            reorderItems.indexOfFirst { item ->
                item is HistoryListItem.Header && item.folderId == folderId
            }
        val header =
            reorderItems.getOrNull(headerIndex) as? HistoryListItem.Header
                ?: return
        val descendantIndex = headerIndex + 1
        val suppressedKeys = mutableSetOf<String>()
        while (descendantIndex < reorderItems.size) {
            val descendant = reorderItems[descendantIndex]
            val depth =
                when (descendant) {
                    is HistoryListItem.CharacterHeader -> 0
                    is HistoryListItem.Header -> descendant.depth
                    is HistoryListItem.Item -> descendant.depth
                }
            if (depth <= header.depth) break
            suppressedKeys +=
                when (descendant) {
                    is HistoryListItem.CharacterHeader -> descendant.key
                    is HistoryListItem.Header -> descendant.key
                    is HistoryListItem.Item -> "chat:${descendant.history.id}"
                }
            reorderItems.removeAt(descendantIndex)
        }
        suppressedFolderDragDescendantKeys = suppressedKeys
    }
    fun applyOptimisticMove(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in reorderItems.indices) return
        val moved = reorderItems.removeAt(fromIndex)
        reorderItems.add(toIndex.coerceIn(0, reorderItems.size), moved)
    }
    fun applyOptimisticFolderMove(fromIndex: Int, toIndex: Int): Boolean {
        val header = reorderItems.getOrNull(fromIndex) as? HistoryListItem.Header ?: return false
        var blockEnd = fromIndex + 1
        while (blockEnd < reorderItems.size) {
            val depth =
                when (val item = reorderItems[blockEnd]) {
                    is HistoryListItem.CharacterHeader -> 0
                    is HistoryListItem.Header -> item.depth
                    is HistoryListItem.Item -> item.depth
                }
            if (depth <= header.depth) break
            blockEnd++
        }
        if (toIndex in fromIndex until blockEnd) return false

        val target =
            reorderItems.getOrNull(toIndex)
                ?: return false
        val targetKey =
            when (target) {
                is HistoryListItem.CharacterHeader -> target.key
                is HistoryListItem.Header -> target.key
                is HistoryListItem.Item -> "chat:${target.history.id}"
            }
        val block = reorderItems.subList(fromIndex, blockEnd).toList()
        repeat(block.size) { reorderItems.removeAt(fromIndex) }
        val targetIndex =
            reorderItems.indexOfFirst { item ->
                when (item) {
                    is HistoryListItem.CharacterHeader -> item.key
                    is HistoryListItem.Header -> item.key
                    is HistoryListItem.Item -> "chat:${item.history.id}"
                } == targetKey
            }
        if (targetIndex < 0) {
            reorderItems.addAll(fromIndex.coerceAtMost(reorderItems.size), block)
            return false
        }
        val insertionIndex =
            (targetIndex + if (fromIndex < toIndex) 1 else 0)
                .coerceIn(0, reorderItems.size)
        reorderItems.addAll(insertionIndex, block)
        return true
    }

    var activeChatDragId by remember { mutableStateOf<String?>(null) }
    var pendingChatDragTarget by remember { mutableStateOf<ChatDragTarget?>(null) }
    var historyMoveInFlight by remember { mutableStateOf(false) }
    var pendingHistoryMoveAck by remember { mutableStateOf<PendingHistoryMoveAck?>(null) }
    var chatDragSnapshots by
        remember {
            mutableStateOf<Map<String?, List<HistorySiblingSnapshot>>>(emptyMap())
        }
    var activeFolderDragId by remember { mutableStateOf<String?>(null) }
    var pendingFolderDragTarget by remember { mutableStateOf<FolderDragTarget?>(null) }
    var folderDragSnapshots by
        remember {
            mutableStateOf<Map<String?, List<HistorySiblingSnapshot>>>(emptyMap())
        }

    LaunchedEffect(orderingChatHistories, chatFolders, pendingHistoryMoveAck) {
        val pending = pendingHistoryMoveAck ?: return@LaunchedEffect
        if (!pending.repositoryCallCompleted) return@LaunchedEffect
        val matchesExpected =
            pending.expectedSiblingsByParent.all { (parentFolderId, expected) ->
                historySiblingSnapshot(parentFolderId).map { it.structure() } ==
                    expected.map { it.structure() }
            }
        if (!matchesExpected) {
            // Folders and chats refresh separately, so the list can sit half updated for a
            // moment. Unlock only after it has settled; a drag started from that half-updated
            // order is rejected by the repository as "order changed" and snaps back.
            // Any new list update restarts this effect, so the expected order unlocks at once;
            // the wait only matters when the saved order never matches what we expected.
            delay(1_500L)
        }
        pendingHistoryMoveAck = null
        historyMoveInFlight = false
    }

    val reorderableState =
        rememberReorderableLazyListState(actualLazyListState) { from, to ->
            if (!canReorder || historyMoveInFlight) {
                return@rememberReorderableLazyListState
            }
            val moved = reorderItems.getOrNull(from.index)
                ?: return@rememberReorderableLazyListState
            val target = reorderItems.getOrNull(to.index)
                ?: return@rememberReorderableLazyListState

            when (moved) {
                is HistoryListItem.Item -> {
                    val nextTarget =
                        when (target) {
                            is HistoryListItem.Item -> {
                            if (
                                !canManageFolders &&
                                    moved.history.folderId != target.history.folderId
                            ) {
                                return@rememberReorderableLazyListState
                            }
                            if (
                                historyDisplayMode ==
                                    ChatHistoryDisplayMode.BY_CHARACTER_CARD &&
                                    (
                                        moved.history.characterCardName !=
                                            target.history.characterCardName ||
                                            moved.history.characterGroupId !=
                                                target.history.characterGroupId
                                    )
                            ) {
                                return@rememberReorderableLazyListState
                            }
                            ChatDragTarget(
                                targetFolderId = target.history.folderId,
                                anchorNodeKey = "chat:${target.history.id}",
                                insertBeforeAnchor = from.index > to.index,
                            )
                        }
                        is HistoryListItem.Header -> {
                            if (!canManageFolders) {
                                return@rememberReorderableLazyListState
                            }
                            val targetFolder =
                                target.folderId?.let {
                                    chatFolders.firstOrNull { folder -> folder.id == it }
                                }
                            if (
                                targetFolder == null ||
                                    targetFolder.id == SYSTEM_UNGROUPED_FOLDER_ID
                            ) {
                                ChatDragTarget(
                                    targetFolderId = null,
                                    anchorNodeKey =
                                        visibleHistorySiblingSnapshot(null)
                                            .firstOrNull {
                                                it.kind == HistorySiblingKind.CHAT
                                            }
                                            ?.stableKey,
                                    insertBeforeAnchor = true,
                                )
                            } else {
                                val movingFromUngrouped = moved.history.folderId == null
                                val targetFolderIsEmpty =
                                    visibleHistorySiblingSnapshot(targetFolder.id).isEmpty()
                                ChatDragTarget(
                                    targetFolderId =
                                        if (movingFromUngrouped) {
                                            null
                                        } else {
                                            targetFolder.parentFolderId
                                        },
                                    anchorNodeKey =
                                        if (movingFromUngrouped) {
                                            null
                                        } else {
                                            "folder:${targetFolder.id}"
                                        },
                                    insertBeforeAnchor =
                                        if (movingFromUngrouped) {
                                            null
                                        } else {
                                            from.index > to.index
                                    },
                                    nestFolderId = targetFolder.id,
                                    nestImmediately = targetFolderIsEmpty,
                                    hoverStartedAt = SystemClock.uptimeMillis(),
                                )
                            }
                        }
                        is HistoryListItem.CharacterHeader ->
                            return@rememberReorderableLazyListState
                    }
                    val previous = pendingChatDragTarget
                    val updatedTarget =
                        if (
                            previous != null &&
                                previous.targetFolderId == nextTarget.targetFolderId &&
                                previous.anchorNodeKey == nextTarget.anchorNodeKey &&
                                previous.insertBeforeAnchor == nextTarget.insertBeforeAnchor &&
                                previous.nestFolderId == nextTarget.nestFolderId &&
                                previous.nestImmediately == nextTarget.nestImmediately
                        ) {
                            nextTarget.copy(hoverStartedAt = previous.hoverStartedAt)
                        } else {
                            nextTarget
                        }
                    activeChatDragId = activeChatDragId ?: moved.history.id
                    pendingChatDragTarget = updatedTarget
                    applyOptimisticMove(from.index, to.index)
                    hapticFeedback.performHapticFeedback(DragMoveHaptic)
                }
                is HistoryListItem.Header -> {
                    if (
                        !canManageFolders ||
                            historyDisplayMode != ChatHistoryDisplayMode.BY_FOLDER
                    ) {
                        return@rememberReorderableLazyListState
                    }
                    val movedFolderId =
                        moved.folderId ?: return@rememberReorderableLazyListState
                    val folderById = chatFolders.associateBy { it.id }
                    val movedFolder =
                        folderById[movedFolderId] ?: return@rememberReorderableLazyListState
                    val nextTarget =
                        when (target) {
                            is HistoryListItem.Header -> {
                                val targetFolderId = target.folderId
                                val targetFolder = targetFolderId?.let(folderById::get)
                                val isUngroupedTarget =
                                    targetFolderId == SYSTEM_UNGROUPED_FOLDER_ID
                                if (
                                    movedFolderId == SYSTEM_UNGROUPED_FOLDER_ID &&
                                        targetFolder?.parentFolderId != null
                                ) {
                                    return@rememberReorderableLazyListState
                                }
                                FolderDragTarget(
                                    targetParentFolderId =
                                        if (isUngroupedTarget) {
                                            null
                                        } else {
                                            targetFolder?.parentFolderId
                                        },
                                    anchorNodeKey =
                                        targetFolderId?.let { "folder:$it" }
                                            ?: visibleHistorySiblingSnapshot(null)
                                                .firstOrNull {
                                                    it.stableKey != "folder:$movedFolderId"
                                                }
                                                ?.stableKey,
                                    insertBeforeAnchor =
                                        targetFolderId?.let { from.index > to.index } ?: true,
                                    nestFolderId =
                                        targetFolderId.takeUnless {
                                            isUngroupedTarget ||
                                                movedFolderId == SYSTEM_UNGROUPED_FOLDER_ID
                                        },
                                    hoverStartedAt = SystemClock.uptimeMillis(),
                                )
                            }
                            is HistoryListItem.Item -> {
                                val parentId = target.history.folderId
                                if (
                                    movedFolderId == SYSTEM_UNGROUPED_FOLDER_ID &&
                                        parentId != null
                                ) {
                                    return@rememberReorderableLazyListState
                                }
                                FolderDragTarget(
                                    targetParentFolderId = parentId,
                                    anchorNodeKey = "chat:${target.history.id}",
                                    insertBeforeAnchor = from.index > to.index,
                                    nestFolderId = null,
                                    hoverStartedAt = SystemClock.uptimeMillis(),
                                )
                            }
                            is HistoryListItem.CharacterHeader ->
                                return@rememberReorderableLazyListState
                        }
                    val previous = pendingFolderDragTarget
                    val updatedTarget =
                        if (
                            previous != null &&
                                previous.targetParentFolderId == nextTarget.targetParentFolderId &&
                                previous.anchorNodeKey == nextTarget.anchorNodeKey &&
                                previous.insertBeforeAnchor == nextTarget.insertBeforeAnchor &&
                                previous.nestFolderId == nextTarget.nestFolderId
                        ) {
                            nextTarget.copy(hoverStartedAt = previous.hoverStartedAt)
                        } else {
                            nextTarget
                        }
                    if (!applyOptimisticFolderMove(from.index, to.index)) {
                        return@rememberReorderableLazyListState
                    }
                    hapticFeedback.performHapticFeedback(DragMoveHaptic)
                    pendingFolderDragTarget = updatedTarget
                    if (activeFolderDragId == null) {
                        activeFolderDragId = movedFolderId
                    }
                }
                is HistoryListItem.CharacterHeader -> Unit
            }
        }

    fun commitChatDrag() {
        val chatId = activeChatDragId
        val target = pendingChatDragTarget
        val expectedSnapshots = chatDragSnapshots
        if (chatId != null && target != null) hapticFeedback.performHapticFeedback(DragDropHaptic)
        activeChatDragId = null
        pendingChatDragTarget = null
        chatDragSnapshots = emptyMap()
        dragInProgress = false
        if (chatId == null || target == null) {
            resetOptimisticOrder()
            return
        }
        val moving = chatHistories.firstOrNull { it.id == chatId }
        if (moving == null) {
            resetOptimisticOrder()
            return
        }
        val shouldNest =
            target.nestFolderId != null &&
                (
                    target.nestImmediately ||
                        SystemClock.uptimeMillis() - target.hoverStartedAt >= 650L
                )
        val resolvedTargetFolderId =
            if (shouldNest) target.nestFolderId else target.targetFolderId
        val resolvedAnchorNodeKey = target.anchorNodeKey.takeUnless { shouldNest }
        val resolvedInsertBeforeAnchor = target.insertBeforeAnchor.takeUnless { shouldNest }
        val orderedVisibleNodeKeys =
            if (moving.folderId == resolvedTargetFolderId) {
                visibleChatReorderNodeKeys(reorderItems, moving.folderId)
            } else {
                null
            }
        val expectedAfterMove =
            runCatching {
                if (orderedVisibleNodeKeys != null) {
                    mapOf(
                        moving.folderId to
                            expectedVisibleReorder(
                                parentFolderId = moving.folderId,
                                source = expectedSnapshots.getValue(moving.folderId),
                                orderedVisibleNodeKeys = orderedVisibleNodeKeys,
                            )
                    )
                } else {
                    expectedAnchoredMove(
                        movingNodeKey = "chat:$chatId",
                        sourceParentFolderId = moving.folderId,
                        targetParentFolderId = resolvedTargetFolderId,
                        beforeNodeKey =
                            resolvedAnchorNodeKey.takeIf {
                                resolvedInsertBeforeAnchor == true
                            },
                        afterNodeKey =
                            resolvedAnchorNodeKey.takeIf {
                                resolvedInsertBeforeAnchor == false
                            },
                        snapshots = expectedSnapshots,
                    )
                }
            }.getOrElse { error ->
                resetOptimisticOrder()
                Toast.makeText(
                    context,
                    error.message ?: operationFailedText,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
        historyMoveInFlight = true
        pendingHistoryMoveAck =
            PendingHistoryMoveAck(
                expectedSiblingsByParent = expectedAfterMove,
            )
        coroutineScope.launch {
            val result = runCatching {
                if (moving.folderId == resolvedTargetFolderId) {
                    chatHistoryManager.moveChat(
                        chatId = chatId,
                        targetFolderId = resolvedTargetFolderId,
                        orderedVisibleNodeKeys = requireNotNull(orderedVisibleNodeKeys),
                        expectedSourceSiblings =
                            expectedSnapshots.getValue(moving.folderId),
                        expectedTargetSiblings =
                            expectedSnapshots.getValue(moving.folderId),
                    )
                } else {
                    chatHistoryManager.moveChat(
                        chatId = chatId,
                        targetFolderId = resolvedTargetFolderId,
                        beforeNodeKey =
                            resolvedAnchorNodeKey.takeIf {
                                resolvedInsertBeforeAnchor == true
                            },
                        afterNodeKey =
                            resolvedAnchorNodeKey.takeIf {
                                resolvedInsertBeforeAnchor == false
                            },
                        expectedSourceSiblings =
                            expectedSnapshots.getValue(moving.folderId),
                        expectedTargetSiblings =
                            expectedSnapshots.getValue(resolvedTargetFolderId),
                        allowAppendToNonEmptyTarget = resolvedAnchorNodeKey == null,
                    )
                }
            }
            result.onFailure { error ->
                resetOptimisticOrder()
                Toast.makeText(
                    context,
                    error.message ?: operationFailedText,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            if (result.isFailure) {
                pendingHistoryMoveAck = null
                historyMoveInFlight = false
            } else {
                pendingHistoryMoveAck =
                    pendingHistoryMoveAck?.copy(repositoryCallCompleted = true)
            }
        }
    }

    fun commitFolderDrag() {
        val folderId = activeFolderDragId
        val target = pendingFolderDragTarget
        val expectedSnapshots = folderDragSnapshots
        if (folderId != null && target != null) hapticFeedback.performHapticFeedback(DragDropHaptic)
        activeFolderDragId = null
        pendingFolderDragTarget = null
        folderDragSnapshots = emptyMap()
        dragInProgress = false
        suppressedFolderDragDescendantKeys = emptySet()
        suppressedFolderDragRootId = null
        if (folderId == null || target == null) {
            resetOptimisticOrder()
            return
        }

        val shouldNest =
            target.nestFolderId != null &&
                target.nestFolderId != folderId &&
                folderId != SYSTEM_UNGROUPED_FOLDER_ID &&
                target.nestFolderId != SYSTEM_UNGROUPED_FOLDER_ID &&
                SystemClock.uptimeMillis() - target.hoverStartedAt >= 650L
        val resolvedParent =
            if (shouldNest) target.nestFolderId else target.targetParentFolderId
        if (!canMoveFolderToParent(folderId, resolvedParent)) {
            resetOptimisticOrder()
            return
        }
        val resolvedAnchorNodeKey = target.anchorNodeKey.takeUnless { shouldNest }
        val resolvedInsertBeforeAnchor = target.insertBeforeAnchor.takeUnless { shouldNest }
        val sourceParent =
            chatFolders.firstOrNull { it.id == folderId }?.parentFolderId
        val expectedAfterMove =
            runCatching {
                expectedAnchoredMove(
                    movingNodeKey = "folder:$folderId",
                    sourceParentFolderId = sourceParent,
                    targetParentFolderId = resolvedParent,
                    beforeNodeKey =
                        resolvedAnchorNodeKey.takeIf { resolvedInsertBeforeAnchor == true },
                    afterNodeKey =
                        resolvedAnchorNodeKey.takeIf { resolvedInsertBeforeAnchor == false },
                    snapshots = expectedSnapshots,
                )
            }.getOrElse { error ->
                resetOptimisticOrder()
                Toast.makeText(
                    context,
                    error.message ?: operationFailedText,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
        historyMoveInFlight = true
        pendingHistoryMoveAck =
            PendingHistoryMoveAck(
                expectedSiblingsByParent = expectedAfterMove,
            )
        coroutineScope.launch {
            val result = runCatching {
                chatHistoryManager.moveFolder(
                    folderId = folderId,
                    targetParentFolderId = resolvedParent,
                    expectedSourceSiblings =
                        expectedSnapshots.getValue(
                            sourceParent
                        ),
                    expectedTargetSiblings = expectedSnapshots.getValue(resolvedParent),
                    beforeNodeKey =
                        resolvedAnchorNodeKey.takeIf { resolvedInsertBeforeAnchor == true },
                    afterNodeKey =
                        resolvedAnchorNodeKey.takeIf { resolvedInsertBeforeAnchor == false },
                    allowAppendToNonEmptyTarget = resolvedAnchorNodeKey == null,
                )
            }
            result.onFailure { error ->
                resetOptimisticOrder()
                Toast.makeText(
                    context,
                    error.message ?: operationFailedText,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            if (result.isFailure) {
                pendingHistoryMoveAck = null
                historyMoveInFlight = false
            } else {
                pendingHistoryMoveAck =
                    pendingHistoryMoveAck?.copy(repositoryCallCompleted = true)
            }
        }
    }

    fun moveVisibleChatByOffset(targetChat: ChatHistory, offset: Int) {
        if (historyMoveInFlight) return
        val visibleSiblingKeys =
            visibleChatReorderNodeKeys(flatItems, targetChat.folderId).toMutableList()
        val chatKey = "chat:${targetChat.id}"
        val currentIndex = visibleSiblingKeys.indexOf(chatKey)
        val targetIndex = currentIndex + offset
        if (currentIndex < 0 || targetIndex !in visibleSiblingKeys.indices) return
        visibleSiblingKeys.add(targetIndex, visibleSiblingKeys.removeAt(currentIndex))
        val sourceSnapshot = historySiblingSnapshot(targetChat.folderId)
        val expectedAfterMove =
            expectedVisibleReorder(
                parentFolderId = targetChat.folderId,
                source = sourceSnapshot,
                orderedVisibleNodeKeys = visibleSiblingKeys,
            )
        historyMoveInFlight = true
        pendingHistoryMoveAck =
            PendingHistoryMoveAck(
                expectedSiblingsByParent =
                    mapOf(targetChat.folderId to expectedAfterMove),
            )
        coroutineScope.launch {
            val result = runCatching {
                chatHistoryManager.moveChat(
                    chatId = targetChat.id,
                    targetFolderId = targetChat.folderId,
                    orderedVisibleNodeKeys = visibleSiblingKeys,
                    expectedSourceSiblings = sourceSnapshot,
                    expectedTargetSiblings = sourceSnapshot,
                )
            }
            result.onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: operationFailedText,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            if (result.isFailure) {
                pendingHistoryMoveAck = null
                historyMoveInFlight = false
            } else {
                pendingHistoryMoveAck =
                    pendingHistoryMoveAck?.copy(repositoryCallCompleted = true)
            }
        }
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
                                contentDescription = editTitleText
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

                    if (canManageFolders) {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .semantics {
                                        contentDescription =
                                            moveToFolderText
                                    }
                                    .clickable {
                                        chatToMove = resolvedTargetChat
                                        chatItemActionTarget = null
                                    },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    stringResource(R.string.move_to_folder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            }
                        }
                    }
                    
                    if (canReorder) {
                    // 上移选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = moveUpText
                            }
                            .clickable {
                                val targetChat = chatItemActionTarget!!
                                moveVisibleChatByOffset(targetChat, -1)
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
                                contentDescription = moveDownText
                            }
                            .clickable {
                                val targetChat = chatItemActionTarget!!
                                moveVisibleChatByOffset(targetChat, 1)
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
                                        unpinChatText
                                    } else {
                                        pinChatText
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

                    // 收藏/取消收藏选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription =
                                    if (resolvedTargetChat?.isFavorite == true) {
                                        removeFromFavoritesText
                                    } else {
                                        addToFavoritesText
                                    }
                            }
                            .clickable {
                                val targetChat = resolvedTargetChat!!
                                coroutineScope.launch {
                                    chatHistoryManager.updateChatFavorite(
                                        targetChat.id,
                                        !targetChat.isFavorite,
                                    )
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
                                imageVector =
                                    if (resolvedTargetChat?.isFavorite == true) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text =
                                    if (resolvedTargetChat?.isFavorite == true) {
                                        stringResource(R.string.remove_from_favorites)
                                    } else {
                                        stringResource(R.string.add_to_favorites)
                                    },
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
                                        unlockChatText
                                    } else {
                                        lockChatText
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
                    val deleteChatDescription = stringResource(R.string.delete)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = deleteChatDescription
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
                        onClick = { chatItemActionTarget = null },
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

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .semantics {
                                    contentDescription =
                                        createSubfolderText
                                }
                                .clickable {
                                    newFolderParentId = groupActionTarget!!.folderId
                                    newGroupName = ""
                                    showNewGroupDialog = true
                                    groupActionTarget = null
                                },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.create_subfolder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .semantics {
                                    contentDescription =
                                        moveToFolderText
                                }
                                .clickable {
                                    groupToMove = groupActionTarget
                                    groupActionTarget = null
                                },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.move_to_folder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                    
                    // 重命名选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = renameGroupText
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
                    val deleteGroupDescription = stringResource(R.string.delete_group)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = deleteGroupDescription
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

    if (chatToMove != null) {
        val targetChat = chatToMove!!
        val chatMoveOptions =
            buildList {
                add(
                    FolderPickerOption(
                        id = null,
                        name = stringResource(R.string.ungrouped),
                        isCurrent = targetChat.folderId == null,
                        isTopLevel = true,
                    )
                )
                chatFolders
                    .asSequence()
                    .filterNot { it.id == SYSTEM_UNGROUPED_FOLDER_ID }
                    .sortedWith(
                        compareBy<ChatFolderEntity> { folderChoiceLabels[it.id] }
                            .thenBy { it.id }
                    )
                    .forEach { folder ->
                        add(
                            FolderPickerOption(
                                id = folder.id,
                                name = folderChoiceNames.getValue(folder.id),
                                parentPath = folder.parentFolderId?.let { folderChoiceLabels[it] },
                                isCurrent = folder.id == targetChat.folderId,
                            )
                        )
                    }
            }
        FolderPickerDialog(
            title = stringResource(R.string.move_to_folder_title),
            subtitle = targetChat.title,
            options = chatMoveOptions,
            onDismiss = { chatToMove = null },
            onSelect = { folderId ->
                if (folderId != targetChat.folderId) {
                    coroutineScope.launch {
                        runCatching {
                            chatHistoryManager.moveChat(
                                chatId = targetChat.id,
                                targetFolderId = folderId,
                                expectedSourceSiblings =
                                    historySiblingSnapshot(targetChat.folderId),
                                expectedTargetSiblings =
                                    historySiblingSnapshot(folderId),
                            )
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: operationFailedText,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                chatToMove = null
            },
        )
    }

    if (groupToMove != null) {
        val targetFolder = groupToMove!!
        val currentParentFolderId =
            chatFolders.firstOrNull { it.id == targetFolder.folderId }?.parentFolderId
        val groupMoveOptions =
            buildList {
                add(
                    FolderPickerOption(
                        id = null,
                        name = stringResource(R.string.folder_root),
                        isCurrent = currentParentFolderId == null,
                        isTopLevel = true,
                    )
                )
                chatFolders
                    .asSequence()
                    .filter {
                        it.id != targetFolder.folderId &&
                            it.id != SYSTEM_UNGROUPED_FOLDER_ID &&
                            canMoveFolderToParent(targetFolder.folderId, it.id)
                    }
                    .sortedWith(
                        compareBy<ChatFolderEntity> { folderChoiceLabels[it.id] }
                            .thenBy { it.id }
                    )
                    .forEach { folder ->
                        add(
                            FolderPickerOption(
                                id = folder.id,
                                name = folderChoiceNames.getValue(folder.id),
                                parentPath = folder.parentFolderId?.let { folderChoiceLabels[it] },
                                isCurrent = folder.id == currentParentFolderId,
                            )
                        )
                    }
            }
        FolderPickerDialog(
            title = stringResource(R.string.move_to_folder_title),
            subtitle = targetFolder.groupName,
            options = groupMoveOptions,
            onDismiss = { groupToMove = null },
            onSelect = { parentFolderId ->
                if (parentFolderId != currentParentFolderId) {
                    coroutineScope.launch {
                        runCatching {
                            chatHistoryManager.moveFolder(
                                folderId = targetFolder.folderId,
                                targetParentFolderId = parentFolderId,
                                expectedSourceSiblings =
                                    historySiblingSnapshot(currentParentFolderId),
                                expectedTargetSiblings =
                                    historySiblingSnapshot(parentFolderId),
                            )
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: operationFailedText,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                groupToMove = null
            },
        )
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
                            val target = groupToRename!!
                            coroutineScope.launch {
                                runCatching {
                                    chatHistoryManager.renameFolder(
                                        target.folderId,
                                        newGroupNameText,
                                    )
                                }.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        error.message ?: operationFailedText,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
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
        val target = groupToDelete!!
        val targetFolder = chatFolders.firstOrNull { it.id == target.folderId }
        val parentFolder =
            targetFolder?.parentFolderId?.let { parentId ->
                chatFolders.firstOrNull { it.id == parentId }
            }
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_group)) },
            text = {
                Text(
                    text =
                        "${target.groupName}\n\n" +
                            if (parentFolder != null) {
                                stringResource(
                                    R.string.folder_contents_move_to_parent,
                                    parentFolder.name,
                                )
                            } else {
                                stringResource(R.string.folder_contents_move_to_root)
                            },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            runCatching {
                                chatHistoryManager.deleteFolder(target.folderId)
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: operationFailedText,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        groupToDelete = null
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.delete_group_only))
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
        val normalizedSelectedCharacterGroupId =
            selectedCharacterGroupId?.trim()?.takeIf { it.isNotBlank() }
        val missingCharacterGroupLabel =
            stringResource(
                R.string.missing_character_group_id,
                normalizedSelectedCharacterGroupId ?: "",
            )
        val selectedBindingLabel = remember(
            selectedCharacterCardName,
            normalizedSelectedCharacterGroupId,
            groupNameById,
            unboundLabel,
            groupPrefix,
            missingCharacterGroupLabel,
        ) {
            when {
                normalizedSelectedCharacterGroupId != null -> {
                    val groupName =
                        groupNameById[normalizedSelectedCharacterGroupId]?.takeIf {
                            it.isNotBlank()
                        }
                    if (!groupName.isNullOrBlank()) {
                        "$groupPrefix: $groupName"
                    } else {
                        missingCharacterGroupLabel
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
                onDismissRequest = {
                    if (!isCreatingFolder) {
                        newFolderParentId = null
                        showNewGroupDialog = false
                    }
                },
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
                                    isCreatingFolder = true
                                    val (characterCardName, characterGroupId) =
                                        resolveBindingForCreate(
                                            historyDisplayMode = historyDisplayMode,
                                            activePrompt = activePrompt,
                                            activeCharacterCardName =
                                                activeCharacterCardName,
                                        )
                                    onCreateFolderWithInitialChat(
                                        newFolderParentId,
                                        normalizedGroupName,
                                        characterCardName,
                                        characterGroupId,
                                    ) { result ->
                                        result.onSuccess {
                                            newGroupName = ""
                                            newFolderParentId = null
                                            isCreatingFolder = false
                                            showNewGroupDialog = false
                                        }.onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                error.message
                                                    ?: operationFailedText,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            isCreatingFolder = false
                                        }
                                    }
                                }
                            },
                            enabled = !isCreatingFolder,
                    ) {
                        Text(stringResource(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            newFolderParentId = null
                            showNewGroupDialog = false
                        },
                        enabled = !isCreatingFolder,
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_history),
                    style = MaterialTheme.typography.titleSmall,
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
                            modifier = Modifier.size(18.dp)
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
                            modifier = Modifier.size(18.dp)
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
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        ChatHistoryCategoryNewChatControls(
            selectedCategory = selectedCategory,
            canManageFolders = canManageFolders,
            onSelectedCategoryChange = { category ->
                onSelectedCategoryChange(category)
                coroutineScope.launch {
                    actualLazyListState.scrollToItem(0)
                }
            },
            onNewChat = { inheritGroupFromCurrent ->
                val (characterCardName, characterGroupId) = resolveBindingForCreate(
                    historyDisplayMode = historyDisplayMode,
                    activePrompt = activePrompt,
                    activeCharacterCardName = activeCharacterCardName
                )
                onNewChat(
                    characterCardName,
                    characterGroupId,
                    inheritGroupFromCurrent,
                )
            },
            onCreateFolder = {
                newFolderParentId = null
                newGroupName = ""
                showNewGroupDialog = true
            },
        )

        // A single onboarding hint keeps every folder row single-line and leaves its name room.
        if (
            canManageFolders &&
                !hasLongPressedGroup &&
                searchQuery.isBlank() &&
                reorderItems.any {
                    it is HistoryListItem.Header &&
                        it.folderId != null &&
                        it.folderId != SYSTEM_UNGROUPED_FOLDER_ID
                }
        ) {
            Text(
                text = stringResource(R.string.long_press_manage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
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
                    .padding(horizontal = 12.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        var showSwipeHint by rememberLocal(key = "show_swipe_hint", defaultValue = true)

        if (showSwipeHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(end = 2.dp)
        ) {
            LazyColumn(
                state = actualLazyListState,
                modifier = Modifier
                    // Short histories still need the whole viewport while a row is dragged down.
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 22.dp)
            ) {
                itemsIndexed(
                    items = reorderItems,
                    key = { _, item ->
                        when (item) {
                            is HistoryListItem.CharacterHeader -> item.key
                            is HistoryListItem.Header -> item.key
                            is HistoryListItem.Item -> "chat:${item.history.id}"
                        }
                    }
                ) { index, item ->
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
                        
                        val isExpanded = searchQuery.isNotBlank() || !collapsedCharacters.contains(item.key)
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 6.dp)
                                .semantics {
                                    contentDescription = "${item.name}, $stateDescription"
                                }
                                .pointerInput(collapsedCharacters, searchQuery) {
                                    if (searchQuery.isNotBlank()) return@pointerInput
                                    detectTapGestures(
                                        onTap = {
                                            onCollapsedCharactersChange(
                                                if (collapsedCharacters.contains(item.key)) {
                                                    collapsedCharacters - item.key
                                                } else {
                                                    collapsedCharacters + item.key
                                                }
                                            )
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
                                    .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
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
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        // Capped so a long name never pushes the fold arrow off the row.
                                        .widthIn(max = 140.dp)
                                        .clearAndSetSemantics {}
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
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(20.dp)
                                    .clearAndSetSemantics {}
                            )
                        }
                    }
                    is HistoryListItem.Header -> {
                        val isUngroupedFolder =
                            item.folderId == SYSTEM_UNGROUPED_FOLDER_ID
                        val collapseKey =
                            if (isUngroupedFolder) {
                                item.key
                            } else {
                                item.folderId?.let { "folder:$it" } ?: item.key
                            }
                        val isExpanded = searchQuery.isNotBlank() || !collapsedGroups.contains(collapseKey)
                        val layoutDirection = LocalLayoutDirection.current
                        val expandedIndicatorRotation =
                            if (layoutDirection == LayoutDirection.Ltr) 90f else -90f
                        val expansionIndicatorRotation by
                            animateFloatAsState(
                                targetValue = if (isExpanded) expandedIndicatorRotation else 0f,
                                animationSpec = tween(durationMillis = 150),
                                label = "folderExpansionIndicator",
                            )
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }
                        
                        ReorderableItem(
                            reorderableState,
                            key = item.key,
                            animateItemModifier = Modifier.animateItem(placementSpec = dragPlacementSpec),
                        ) { isDragging ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = HistoryTreeIndent * (item.depth - 1).coerceAtLeast(0))
                                    .historyGuideLine(
                                        historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD ||
                                            historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER
                                    )
                                    // Vertical padding sits inside the line so rows join up without gaps.
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            if (
                                historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD ||
                                    historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER
                            ) {
                                HistoryHierarchyGuide()
                            }
                            if (
                                canManageFolders &&
                                    historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER &&
                                    item.folderId != null &&
                                    (
                                        item.folderId != SYSTEM_UNGROUPED_FOLDER_ID ||
                                            systemUngroupedReady
                                    )
                            ) {
                                val folderDragDescription =
                                    stringResource(R.string.drag_item, item.name)
                                val folderDragHandleModifier =
                                    if (historyMoveInFlight) {
                                        Modifier
                                    } else {
                                        Modifier.draggableHandle(
                                            onDragStarted = {
                                                if (!historyMoveInFlight) {
                                                    dragInProgress = true
                                                    hapticFeedback.performHapticFeedback(
                                                        DragPickUpHaptic
                                                    )
                                                    activeFolderDragId = item.folderId
                                                    pendingFolderDragTarget = null
                                                    folderDragSnapshots =
                                                        captureHistorySiblingSnapshots()
                                                    // The reorderable list moves one row at a
                                                    // time. Temporarily hide this folder's
                                                    // descendants so the whole subtree behaves
                                                    // as one draggable row instead of repeatedly
                                                    // snapping against its own children.
                                                    prepareOptimisticFolderDrag(
                                                        item.folderId
                                                    )
                                                }
                                            },
                                            onDragStopped = ::commitFolderDrag,
                                        )
                                    }
                                // A plain 32dp handle: the old IconButton kept a 48dp slot that squeezed the title.
                                Box(
                                    modifier =
                                        folderDragHandleModifier
                                            .size(32.dp)
                                            .semantics {
                                                contentDescription = folderDragDescription
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (historyMoveInFlight) 0.38f else 1f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .semantics {
                                        contentDescription = "${item.name}, $stateDescription"
                                    }
                                    .pointerInput(
                                        searchQuery,
                                        selectedCategory,
                                        collapseKey,
                                        collapsedGroups,
                                        canManageFolders,
                                        isUngroupedFolder,
                                        item.folderId,
                                        item.name,
                                    ) {
                                        if (searchQuery.isNotBlank()) return@pointerInput
                                        detectTapGestures(
                                            onTap = {
                                                onCollapsedGroupsChange(
                                                    if (collapsedGroups.contains(collapseKey)) {
                                                        collapsedGroups - collapseKey
                                                    } else {
                                                        collapsedGroups + collapseKey
                                                    }
                                                )
                                            },
                                            onLongPress = {
                                                if (
                                                    canManageFolders &&
                                                        !isUngroupedFolder &&
                                                        item.folderId != null
                                                ) {
                                                    groupActionTarget = GroupTarget(
                                                        folderId = item.folderId,
                                                        groupName = item.name,
                                                    )
                                                    hasLongPressedGroup = true
                                                }
                                            }
                                        )
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = if (isDragging) 8.dp else 0.dp,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = HistoryRowMinHeight)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector =
                                            if (isExpanded) {
                                                Icons.Default.FolderOpen
                                            } else {
                                                Icons.Default.Folder
                                            },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp).clearAndSetSemantics {}
                                    )
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).clearAndSetSemantics {},
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(18.dp)
                                                .graphicsLayer {
                                                    rotationZ = expansionIndicatorRotation
                                                }
                                                .clearAndSetSemantics {},
                                    )
                                }
                            }
                        }
                        }
                    }
                    is HistoryListItem.Item -> {
                        if (
                            selectedCategory == ChatHistoryCategory.RECENT &&
                                searchQuery.isBlank()
                        ) {
                            val section =
                                item.history.timeSection(
                                    historyTimeContext.date,
                                    historyTimeContext.zoneId,
                                )
                            val previousSection =
                                (reorderItems.getOrNull(index - 1) as? HistoryListItem.Item)
                                    ?.history
                                    ?.timeSection(
                                        historyTimeContext.date,
                                        historyTimeContext.zoneId,
                                    )
                            if (section != previousSection) {
                                HistoryTimeSectionHeader(section)
                            }
                        }
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
                            key = "chat:${item.history.id}",
                            animateItemModifier = Modifier.animateItem(placementSpec = dragPlacementSpec)
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
                                val showHierarchyGuide =
                                    selectedCategory != ChatHistoryCategory.RECENT &&
                                        (
                                            historyDisplayMode ==
                                                ChatHistoryDisplayMode.BY_CHARACTER_CARD ||
                                                historyDisplayMode ==
                                                    ChatHistoryDisplayMode.BY_FOLDER
                                        )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = HistoryTreeIndent * item.depth.coerceAtLeast(0))
                                        .historyGuideLine(showHierarchyGuide)
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (showHierarchyGuide) {
                                        HistoryHierarchyGuide()
                                    }
                                    if (canReorder) {
                                        val dragDescription =
                                            stringResource(
                                                R.string.drag_item,
                                                item.history.title,
                                            )
                                        val dragHandleModifier =
                                            if (historyMoveInFlight) {
                                                Modifier
                                            } else {
                                                Modifier.draggableHandle(
                                                    onDragStarted = {
                                                        if (!historyMoveInFlight) {
                                                            dragInProgress = true
                                                            hapticFeedback.performHapticFeedback(
                                                                DragPickUpHaptic
                                                            )
                                                            activeChatDragId =
                                                                item.history.id
                                                            pendingChatDragTarget = null
                                                            chatDragSnapshots =
                                                                captureHistorySiblingSnapshots()
                                                        }
                                                    },
                                                    onDragStopped = ::commitChatDrag,
                                                )
                                            }
                                        // A plain 32dp handle: the old IconButton kept a 48dp slot that squeezed the title.
                                        Box(
                                            modifier =
                                                dragHandleModifier
                                                    .size(32.dp)
                                                    .semantics {
                                                        contentDescription = dragDescription
                                                    },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = null,
                                                tint = contentColor.copy(alpha = if (historyMoveInFlight) 0.38f else 1f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
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
                                                    val groupName =
                                                        chatFolders.firstOrNull {
                                                            it.id == item.history.folderId
                                                        }?.name ?: ungroupedText
                                                     Row(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .heightIn(min = HistoryRowMinHeight)
                                                             .padding(horizontal = 8.dp)
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
                                                        Column(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .semantics { contentDescription = "" }
                                                        ) {
                                                            Text(
                                                                text = item.history.title,
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    fontSize = 13.sp,
                                                                    lineHeight = 18.sp,
                                                                ),
                                                                color = contentColor,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )

                                                            // 分支搜索结果标出其父聊天来源。
                                                            if (item.history.parentChatId != null) {
                                                                val parentChat =
                                                                    searchableChatHistories.find {
                                                                        it.id == item.history.parentChatId
                                                                    }
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
                itemCount = reorderItems.size,
                onInteractionChange = onQuickScrollInteractionChange,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 12.dp, bottom = 12.dp)
            )
        }
    }
}

// The gesture haptic types need Android 11; older phones get the closest classic ones.
private val DragPickUpHaptic =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackType.GestureThresholdActivate
    else HapticFeedbackType.LongPress
private val DragMoveHaptic =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackType.SegmentFrequentTick
    else HapticFeedbackType.TextHandleMove
private val DragDropHaptic =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackType.GestureEnd
    else HapticFeedbackType.TextHandleMove

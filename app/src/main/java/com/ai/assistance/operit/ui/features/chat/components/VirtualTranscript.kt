package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.ui.common.markdown.LocalResponseProcessExpanded
import com.ai.assistance.operit.ui.common.markdown.ResponseActivityHeader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.common.markdown.LocalTranscriptMarkdownSlice
import com.ai.assistance.operit.ui.common.markdown.prepareTranscriptMarkdown
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.features.chat.components.part.ThinkToolsXmlNodeGrouper
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.LocalBubbleAiRenderSettings
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.rememberBubbleAiRenderSettings
import androidx.compose.ui.text.rememberTextMeasurer
import com.ai.assistance.operit.ui.common.markdown.LocalTranscriptTextMeasurer

private data class TranscriptBookmark(val key: String, val offset: Int, val timestamp: Long?)
private object TranscriptBookmarks {
    private val entries = LinkedHashMap<String, TranscriptBookmark>()
    fun get(chatId: String) = entries[chatId]
    fun put(chatId: String, value: TranscriptBookmark) {
        entries.remove(chatId)
        entries[chatId] = value
        if (entries.size > 100) entries.remove(entries.keys.first())
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun VirtualTranscript(
    chatId: String,
    messages: List<ChatMessage>,
    process: ResponseProcessState,
    following: Boolean,
    onFollowingChange: ((Boolean) -> Unit)?,
    hasOlder: Boolean,
    hasNewer: Boolean,
    loadingPage: Boolean,
    onOlder: (() -> Unit)?,
    onNewer: (() -> Unit)?,
    onLatest: (() -> Unit)?,
    loadLocator: (suspend (String, String) -> List<ChatMessageLocatorPreview>)?,
    reveal: (suspend (Long) -> Boolean)?,
    onFavorite: ((Long, Boolean) -> Unit)?,
    textColor: Color,
    horizontalPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier,
    renderMessage: @Composable (Int) -> Unit,
    footer: @Composable () -> Unit,
    splitMarkdown: Boolean = true,
) {
    if (!process.ready) {
        Box(modifier) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    val context = LocalContext.current
    val displayPreferences = remember(context) { DisplayPreferencesManager.getInstance(context) }
    val userPreferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val runRepository = remember(context) {
        com.ai.assistance.operit.data.repository.SubagentRunRepository.getInstance(context)
    }
    val runs by remember(chatId, runRepository) {
        runRepository.observeByParentChatId(chatId)
    }.collectAsState(initial = emptyList())
    val runSnapshot = remember(chatId, runs) { TranscriptRunSnapshot(chatId, runs) }
    val toolModeValue by remember(displayPreferences) {
        displayPreferences.toolCollapseMode.map { it as ToolCollapseMode? }
    }.collectAsState(initial = null)
    val showThinkingValue = com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot.current.showThinkingProcess
    val collapseValue by remember(displayPreferences) {
        displayPreferences.collapseCompletedProcess.map { it as Boolean? }
    }.collectAsState(initial = null)
    val bubbleSettings = rememberBubbleAiRenderSettings()
    val toolLabelTextMeasurer = rememberTextMeasurer(cacheSize = 256)
    if (toolModeValue == null || showThinkingValue == null || collapseValue == null || bubbleSettings == null) {
        Box(modifier) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    val toolMode = toolModeValue!!
    val showThinking = showThinkingValue!!
    val collapse = collapseValue!!
    val grouper = remember(toolMode, showThinking) { ThinkToolsXmlNodeGrouper(showThinking, toolCollapseMode = toolMode) }
    val documents = remember(chatId, grouper) { mutableStateMapOf<Long, TranscriptDocumentEntry>() }
    val baseRows = transcriptRows(messages, process)
    val expandedIds = TranscriptExpansionState.expandedIds(chatId)
    val expandedProcesses = process.groups.values.filter { process.isExpanded(it.key) }.mapTo(hashSetOf()) { it.key }
    val currentFollowingChange by rememberUpdatedState(onFollowingChange)
    val toggleExpansion = remember(chatId) {
        { id: String ->
            currentFollowingChange?.invoke(false)
            TranscriptExpansionState.toggle(chatId, id)
        }
    }
    val rows = remember(baseRows, messages, documents.toMap(), splitMarkdown, collapse, expandedIds, expandedProcesses) {
        transcriptMarkdownRows(baseRows, messages, documents, splitMarkdown, collapse,
            { it in expandedIds },
            toggleExpansion,
            { it in expandedProcesses })
    }
    val bookmark = remember(chatId) { TranscriptBookmarks.get(chatId) }
    var restoring by remember(chatId) { mutableStateOf(bookmark != null) }
    var restoreUnavailable by remember(chatId) { mutableStateOf(false) }
    val restoreTimestamp = bookmark?.timestamp
    val listState = remember(chatId) {
        LazyListState(
            cacheWindow = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 1f),
            firstVisibleItemIndex = rows.indexOfFirst { it.key == bookmark?.key }.coerceAtLeast(0),
            // The saved markdown slice may not exist until preparation finishes.
            firstVisibleItemScrollOffset = 0,
        )
    }
    val scope = rememberCoroutineScope()
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var direction by remember(chatId) { mutableStateOf(0) }
    var lastPageRequest by remember(chatId) { mutableStateOf<Pair<Int, Long>?>(null) }
    var pagesWithoutMovement by remember(chatId) { mutableStateOf(0) }
    LaunchedEffect(dragging) {
        if (dragging) {
            lastPageRequest = null
            pagesWithoutMovement = 0
        }
    }
    var pendingJump by remember(chatId) { mutableStateOf<Long?>(null) }
    val currentRows by key(chatId) { rememberUpdatedState(rows) }
    val currentBaseRows by rememberUpdatedState(baseRows)
    val currentMessages by key(chatId) { rememberUpdatedState(messages) }
    val currentReveal by rememberUpdatedState(reveal)
    val prepareCandidates by remember(listState) { derivedStateOf {
        val indices = listState.layoutInfo.visibleItemsInfo.mapNotNull {
            currentRows.getOrNull(it.index)?.messageIndex?.takeIf { index -> index >= 0 }
        }
        val positions = currentBaseRows.indices.filter { currentBaseRows[it].messageIndex in indices }
        val first = (positions.minOrNull() ?: 0).minus(3).coerceAtLeast(0)
        val last = (positions.maxOrNull() ?: 0).plus(3).coerceAtMost(currentBaseRows.lastIndex)
        val nearby = if (last < first) emptyList() else currentBaseRows.subList(first, last + 1)
            .filter { it.section != ResponseMessageSection.HEADER }
            .mapNotNull { currentMessages.getOrNull(it.messageIndex) }
        val restoreMessage = if (restoring) currentMessages.firstOrNull { it.timestamp == restoreTimestamp } else null
        (listOfNotNull(restoreMessage) + nearby)
            .filter { canSplitTranscriptMessage(it) }.distinctBy { it.timestamp }
            .map { it.timestamp to it.content }
    } }
    LaunchedEffect(chatId, prepareCandidates, grouper, splitMarkdown) {
        if (!splitMarkdown) return@LaunchedEffect
        // Keep block identities for the loaded window. Evicting a document solely because
        // it left the screen would turn its rows back into one placeholder during a fling.
        val keep = currentMessages.mapTo(hashSetOf()) { it.timestamp }
        documents.keys.toList().filterNot { it in keep }.forEach { documents.remove(it) }
        prepareCandidates.forEach { (timestamp, content) ->
            if (documents[timestamp]?.content != content) {
                val document = prepareTranscriptMarkdown(content, grouper)
                documents[timestamp] = TranscriptDocumentEntry(content, document)
            }
        }
    }
    // Loading the missing anchor must survive row changes from asynchronous markdown preparation.
    // A rows-keyed effect can cancel the IO after marking it requested and never retry it.
    LaunchedEffect(chatId, bookmark, restoring) {
        if (restoring && restoreTimestamp != null &&
            currentMessages.none { it.timestamp == restoreTimestamp }) {
            restoreUnavailable = currentReveal?.invoke(restoreTimestamp) != true
        }
    }
    LaunchedEffect(chatId, rows.map { it.key to it.preparingMarkdown }, restoring, restoreUnavailable) {
        if (!restoring) return@LaunchedEffect
        val saved = bookmark ?: return@LaunchedEffect
        onFollowingChange?.invoke(false)
        val index = rows.indexOfFirst { it.key == saved.key }
        val messageIndex = messages.indexOfFirst { it.timestamp == restoreTimestamp }
        if (messageIndex >= 0 && rows.any { it.messageIndex == messageIndex && it.preparingMarkdown }) {
            return@LaunchedEffect
        }
        if (index >= 0) {
            listState.scrollToItem(index, saved.offset)
            restoring = false
        } else if (messageIndex >= 0) {
            val fallback = rows.indexOfFirst { it.messageIndex == messageIndex }
            if (fallback >= 0) {
                listState.scrollToItem(fallback)
                restoring = false
            }
        } else if (restoreTimestamp == null || restoreUnavailable) {
            restoring = false
        }
    }
    val connection = remember(chatId, listState) {
        object : NestedScrollConnection {
            private var before = 0 to 0
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                before = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    currentFollowingChange?.invoke(false)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val after = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                if (source == NestedScrollSource.UserInput &&
                    (before != after || available.y != 0f)
                ) {
                    restoring = false
                    pagesWithoutMovement = 0
                    direction = if (after.first < before.first ||
                        (after.first == before.first && after.second < before.second) || available.y > 0
                    ) -1 else 1
                    pendingJump = null
                }
                return Offset.Zero
            }
        }
    }
    fun currentBookmark(): TranscriptBookmark? {
        // visibleItemsInfo also contains rows in contentPadding; their key must not be paired
        // with another row's firstVisibleItemScrollOffset.
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == listState.firstVisibleItemIndex
        } ?: return null
        val row = currentRows.firstOrNull { it.key == item.key.toString() } ?: return null
        val timestamp = currentMessages.getOrNull(row.messageIndex)?.timestamp
            ?: row.group?.let { currentMessages.getOrNull(it.finalIndex)?.timestamp }
        return TranscriptBookmark(row.key,listState.firstVisibleItemScrollOffset,timestamp)
    }
    LaunchedEffect(chatId, listState) {
        snapshotFlow { if (restoring) null else currentBookmark() }.collect { value ->
            if (value != null) TranscriptBookmarks.put(chatId,value)
        }
    }
    DisposableEffect(chatId,listState) {
        onDispose {
            // Flush the last frame when navigation disposes the collector before it can run.
            if (!restoring) currentBookmark()?.let { TranscriptBookmarks.put(chatId,it) }
        }
    }
    val atEnd by remember(listState) { derivedStateOf { !listState.canScrollForward } }
    val nearStart by remember(listState) { derivedStateOf {
        val layout = listState.layoutInfo
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        layout.visibleItemsInfo.firstOrNull()?.let {
            it.index <= 2 && it.offset >= layout.viewportStartOffset - viewport
        } == true
    } }
    val nearEnd by remember(listState) { derivedStateOf {
        val layout = listState.layoutInfo
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        layout.visibleItemsInfo.lastOrNull()?.let {
            it.index >= layout.totalItemsCount - 4 &&
                it.offset + it.size <= layout.viewportEndOffset + viewport
        } == true
    } }
    val processPrefetchKey by remember(listState) { derivedStateOf {
        val layout = listState.layoutInfo
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        layout.visibleItemsInfo.firstNotNullOfOrNull { item ->
            val row = currentRows.getOrNull(item.index)
            val next = currentRows.getOrNull(item.index + 1)
            when {
                row?.loadMore == true -> row.group?.key
                next?.loadMore == true && item.offset + item.size <= layout.viewportEndOffset + viewport ->
                    next.group?.key
                else -> null
            }
        }
    } }
    LaunchedEffect(processPrefetchKey, messages.size) {
        processPrefetchKey?.let { process.loadMore(it) }
    }
    LaunchedEffect(direction, nearStart, nearEnd, atEnd, loadingPage, hasOlder, hasNewer,
        restoring, messages.firstOrNull()?.timestamp, messages.lastOrNull()?.timestamp) {
        if (loadingPage || restoring) return@LaunchedEffect
        val cursor = if (direction < 0) messages.firstOrNull()?.timestamp else messages.lastOrNull()?.timestamp
        val request = cursor?.let { direction to it }
        fun requestPage(load: (() -> Unit)?) {
            if (request != null && lastPageRequest != request && pagesWithoutMovement < 3) {
                lastPageRequest = request
                pagesWithoutMovement++
                load?.invoke()
            }
        }
        when {
            direction < 0 && nearStart && hasOlder -> requestPage(onOlder)
            direction > 0 && nearEnd && hasNewer -> requestPage(onNewer)
            direction > 0 && atEnd && !hasNewer -> onFollowingChange?.invoke(true)
        }
    }
    LaunchedEffect(following, hasNewer, loadingPage, restoring) {
        if (following && hasNewer && !loadingPage && !restoring) onLatest?.invoke()
    }
    LaunchedEffect(chatId, following, hasNewer, loadingPage, dragging, restoring) {
        if (following && !hasNewer && !loadingPage && !dragging && !restoring) {
            snapshotFlow {
                val layout = listState.layoutInfo
                Triple(layout.totalItemsCount, layout.visibleItemsInfo.lastOrNull()?.let { it.offset + it.size },
                    listState.canScrollForward)
            }.distinctUntilChanged().collect {
                if (currentRows.isNotEmpty()) listState.scrollToItem(currentRows.size)
            }
        }
    }
    LaunchedEffect(pendingJump, rows.map { it.key }) {
        val timestamp = pendingJump ?: return@LaunchedEffect
        val index = rows.indexOfFirst { row ->
            row.section != ResponseMessageSection.HEADER &&
                messages.getOrNull(row.messageIndex)?.timestamp == timestamp
        }
        if (index >= 0) {
            listState.scrollToItem(index)
            pendingJump = null
        } else {
            val messageIndex = messages.indexOfFirst { it.timestamp == timestamp }
            process.groups[messageIndex]?.let { process.expand(it.key) }
        }
    }
    var showLocator by remember(chatId) { mutableStateOf(false) }
    var locatorAnchor by remember(chatId) { mutableStateOf(0L) }
    var locatorEntries by remember(chatId) { mutableStateOf<List<ChatMessageLocatorPreview>>(emptyList()) }
    var locatorLoading by remember(chatId) { mutableStateOf(false) }
    var locatorFailed by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(showLocator, chatId) {
        if (!showLocator) return@LaunchedEffect
        locatorLoading = true
        locatorFailed = false
        try { locatorEntries = loadLocator?.invoke(chatId, "").orEmpty() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { locatorFailed = true }
        finally { locatorLoading = false }
    }
    com.ai.assistance.operit.ui.theme.ProvideAiMarkdownTextLayoutSettings {
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(connection),
            contentPadding = PaddingValues(
                start = horizontalPadding, end = horizontalPadding,
                top = topPadding, bottom = bottomPadding + 16.dp,
            ),
        ) {
            items(rows, key = { it.key }, contentType = { it.section }) { row ->
                // Keep each chronological block together for accessibility traversal,
                // instead of geometrically sorting all of its descendants with other rows.
                Column(Modifier.semantics { isTraversalGroup = true }) {
                    // Keep the paging sentinel key without cutting a transparent hole in the reply.
                    CompositionLocalProvider(
                        LocalTranscriptChatId provides chatId,
                        LocalTranscriptMessageId provides messages.getOrNull(row.messageIndex)?.timestamp,
                        LocalResponseMessageSection provides row.section,
                        LocalResponseProcessExpanded provides row.group?.let { process.isExpanded(it.key) },
                        LocalTranscriptMarkdownSlice provides row.markdownSlice,
                        LocalTranscriptRuns provides runSnapshot,
                        LocalTranscriptCardEnds provides
                            TranscriptCardEnds(row.cardFirst, row.cardLast),
                        LocalBubbleAiRenderSettings provides bubbleSettings,
                        LocalTranscriptTextMeasurer provides toolLabelTextMeasurer,
                    ) {
                        if (row.preparingMarkdown) {
                            Box(Modifier.fillMaxWidth().height(480.dp)) {
                                CircularProgressIndicator(Modifier.align(Alignment.Center).size(20.dp))
                            }
                        } else if (row.messageIndex >= 0) renderMessage(row.messageIndex)
                    }
                    if (row.section == ResponseMessageSection.HEADER && row.group != null) {
                        ResponseActivityHeader(row.group.durationMs, process.isExpanded(row.group.key), textColor) {
                            onFollowingChange?.invoke(false)
                            process.toggle(row.group.key)
                        }
                    }
                    // Only the end of a reply leaves space outside its background.
                    if (row.cardLast && row.markdownSlice?.last != false) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item("footer") { footer() }
        }
        Column(Modifier.align(Alignment.CenterEnd)) {
            TranscriptPositionIndicator(
                progress = {
                    val layout = listState.layoutInfo
                    if (!listState.canScrollForward) 1f
                    else if (!listState.canScrollBackward) 0f
                    else {
                        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                        val item = layout.visibleItemsInfo.asSequence().filter {
                            rows.getOrNull(it.index)?.messageIndex?.let { index ->
                                index in messages.indices
                            } == true
                        }.minByOrNull {
                            kotlin.math.abs(it.offset + it.size / 2 - center)
                        }
                        val messageIndex = item?.let { rows.getOrNull(it.index)?.messageIndex } ?: 0
                        messageIndex.coerceAtLeast(0).toFloat() / messages.lastIndex.coerceAtLeast(1)
                    }
                },
                onClick = {
                    val layout = listState.layoutInfo
                    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                    locatorAnchor = layout.visibleItemsInfo
                        .filter { item ->
                            rows.getOrNull(item.index)?.messageIndex?.let { it in messages.indices } == true
                        }
                        .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
                        ?.let { item -> messages.getOrNull(rows[item.index].messageIndex)?.timestamp }
                        ?: messages.firstOrNull()?.timestamp ?: 0L
                    showLocator = true
                },
            )
            if (!following || hasNewer) {
                IconButton(onClick = { onFollowingChange?.invoke(true); onLatest?.invoke() }) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.history_scroll_to_bottom))
                }
            }
        }
        if (loadingPage) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).size(20.dp))
    }
    }
    if (showLocator) {
        ChatMessageLocatorDialog(
            locatorEntries, locatorAnchor, locatorLoading, locatorFailed,
            chatId, loadLocator, { showLocator = false }, onFavorite,
        ) { timestamp ->
            showLocator = false
            onFollowingChange?.invoke(false)
            pendingJump = timestamp
            if (messages.none { it.timestamp == timestamp }) scope.launch {
                if (reveal?.invoke(timestamp) != true) pendingJump = null
            }
        }
    }
}

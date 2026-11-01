package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass

import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.motion.DissolvingChatTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.data.chat.folders.ChatFolder
import com.ai.assistance.operit.data.chat.folders.folderAncestors
import com.ai.assistance.operit.data.chat.folders.ChatFolderMove
import com.ai.assistance.operit.data.model.ChatHistory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

@Composable
internal fun FolderTreeList(
    rows: List<ChatFolderRow>, folders: Map<String, ChatFolder>, listState: LazyListState,
    currentId: String?, activeStreamingIds: Set<String>, allChats: List<ChatHistory>,
    draggingEnabled: Boolean, manualSort: Boolean, collapsedBindings: Set<String>,
    onSelectChat: (String) -> Unit, onChatMenu: (ChatHistory) -> Unit,
    onEditChat: (ChatHistory) -> Unit, onDeleteChat: (ChatHistory) -> Unit,
    onFolderMenu: (ChatFolderRow.Folder) -> Unit, onToggleFolder: (ChatFolderRow.Folder) -> Unit,
    onToggleBinding: (String) -> Unit, onBindingCreate: (ChatFolderRow.Binding) -> Unit,
    onHoverExpand: (ChatFolderRow.Folder) -> Unit, onMove: (ChatFolderMove) -> Unit,
    onInteractionChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    selectionEnabled: Boolean = true,
    deletionProgress: Map<String, Float> = emptyMap(),
) {
    var dragSource by remember { mutableStateOf<ChatFolderRow?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var target by remember { mutableStateOf<ChatFolderRow?>(null) }
    var zone by remember { mutableStateOf(FolderDropZone.INSIDE) }
    var drop by remember { mutableStateOf<ChatFolderMove?>(null) }
    // 指针移动和逐行重组共用索引，避免每帧扫描全部目录或对话。
    val glassAppearance = rememberGlassAppearance()
    val rowsByKey = remember(rows) { rows.associateBy { it.key } }
    val chatsById = remember(allChats) { allChats.associateBy { it.id } }
    val latestRowsByKey by rememberUpdatedState(rowsByKey)
    val latestFolders by rememberUpdatedState(folders)
    val latestManualSort by rememberUpdatedState(manualSort)
    val latestMove by rememberUpdatedState(onMove)
    val latestInteraction by rememberUpdatedState(onInteractionChange)
    val hostView = LocalView.current
    val density = LocalDensity.current
    val edgePx = with(density) { 44.dp.toPx() }
    val scrollStep = with(density) { 10.dp.toPx() }
    fun updateTarget() {
        val source = dragSource ?: return
        if (rootBounds.contains(pointer)) {
            target = null
            zone = FolderDropZone.INSIDE
            drop = resolveFolderDrop(source, null, zone, latestFolders, latestManualSort)
            return
        }
        if (!listBounds.contains(pointer)) { target = null; drop = null; return }
        val localY = pointer.y - listBounds.top
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            localY >= it.offset && localY < it.offset + it.size
        }
        val row = info?.let { latestRowsByKey[it.key as String] }
        if (row == null || info == null) { target = null; drop = null; return }
        val fraction = (localY - info.offset) / info.size.toFloat()
        target = row
        zone = when {
            row is ChatFolderRow.Binding -> FolderDropZone.INSIDE
            row is ChatFolderRow.Folder && fraction in 0.25f..0.75f -> FolderDropZone.INSIDE
            fraction < 0.5f -> FolderDropZone.BEFORE
            else -> FolderDropZone.AFTER
        }
        // 最近排序允许移动目录；显式插入会在提交时切换为默认顺序。
        drop = resolveFolderDrop(source, row, zone, latestFolders, allowReorder = true)
    }
    fun finishDrag(commit: Boolean) {
        if (commit) { updateTarget(); drop?.let(latestMove) }
        dragSource = null
        target = null
        drop = null
        latestInteraction(false)
        hostView.parent?.requestDisallowInterceptTouchEvent(false)
    }
    LaunchedEffect(draggingEnabled) {
        if (!draggingEnabled && dragSource != null) finishDrag(false)
    }
    DisposableEffect(Unit) {
        onDispose {
            latestInteraction(false)
            hostView.parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
    LaunchedEffect(dragSource?.key) {
        while (isActive && dragSource != null) {
            val delta = when {
                pointer.y < listBounds.top + edgePx && pointer.y >= listBounds.top -> -scrollStep
                pointer.y > listBounds.bottom - edgePx && pointer.y <= listBounds.bottom -> scrollStep
                else -> 0f
            }
            if (delta != 0f) {
                listState.dispatchRawDelta(delta)
                updateTarget()
            }
            delay(16)
        }
    }
    LaunchedEffect(target?.key, zone, dragSource?.key) {
        val folder = target as? ChatFolderRow.Folder
        if (folder != null && !folder.expanded && zone == FolderDropZone.INSIDE && drop != null) {
            delay(650)
            onHoverExpand(folder)
        }
    }
    Column(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().onGloballyPositioned { rootBounds = it.boundsInRoot() },
            color = if (dragSource != null && rootBounds.contains(pointer) && drop != null)
                selectedContainerColor else Color.Transparent,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                stringResource(if (dragSource == null) R.string.folder_root else R.string.folder_drop_root),
                style = MaterialTheme.typography.labelMedium,
                color = if (dragSource != null && rootBounds.contains(pointer) && drop != null)
                    selectedContentColor else contentColor.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { listBounds = it.boundsInRoot() }) {
            LazyColumn(
                state = listState, contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize().padding(end = 20.dp),
            ) {
                items(rows, key = { it.key }, contentType = { it::class }) { row ->
                    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    val highlighted = dragSource != null && target?.key == row.key && drop != null
                    val deleting = row is ChatFolderRow.Chat && row.history.id in deletionProgress
                    val selected = row is ChatFolderRow.Chat &&
                        if (selectionMode) row.history.id in selectedIds else row.history.id == currentId
                    val rowColor = when {
                        highlighted && zone == FolderDropZone.INSIDE -> MaterialTheme.colorScheme.secondaryContainer
                        selected -> selectedContainerColor
                        else -> Color.Transparent
                    }
                    // 不继承聊天正文的文字颜色，目录文字应与当前行背景配对。
                    val rowContentColor = when {
                        highlighted && zone == FolderDropZone.INSIDE -> getTextColorForBackground(rowColor)
                        selected -> selectedContentColor
                        else -> contentColor
                    }
                    // 混合高度的历史行直接使用列表布局坐标，避免复用时叠加旧的位移和退场绘制。
                    Column(Modifier.fillMaxWidth().drawBehind {
                        if (row.depth > 0) {
                            val x = (8 + (row.depth.coerceAtMost(4) - 1) * 12 + 6).dp.toPx()
                            drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        }
                    }.alpha(if (dragSource?.key == row.key) 0.4f else 1f)) {
                        if (highlighted && zone == FolderDropZone.BEFORE) HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
                        val glassEnabled = row is ChatFolderRow.Chat && glassAppearance.conversationList
                        val glassColor = if (rowColor == Color.Transparent) MaterialTheme.colorScheme.surfaceContainer else rowColor
                        val content: @Composable () -> Unit = {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = (8 + row.depth.coerceAtMost(4) * 12).dp)
                                    .padding(vertical = if (glassEnabled) 3.dp else 0.dp)
                                    .liquidGlass(glassEnabled, RoundedCornerShape(10.dp), glassColor,
                                        region = GlassRegion.CONVERSATION_LIST,
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (glassEnabled) Color.Transparent else rowColor)
                                    .heightIn(min = 44.dp)
                                    .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val title = when (row) {
                                    is ChatFolderRow.Chat -> row.history.title
                                    is ChatFolderRow.Folder -> row.folder.name
                                    is ChatFolderRow.Binding -> row.name
                                }
                                // 点击区不包含操作按钮和拖动柄，避免拖动时选中对话或触发菜单。
                                Row(
                                    modifier = Modifier.weight(1f).heightIn(min = 36.dp).then(
                                        if (selectionMode && row is ChatFolderRow.Chat) Modifier.toggleable(
                                            value = selected, enabled = !deleting && selectionEnabled,
                                            role = Role.Checkbox, onValueChange = { onSelectChat(row.history.id) },
                                        ) else Modifier.clickable(enabled = !deleting && selectionEnabled, role = Role.Button) {
                                            when (row) {
                                                is ChatFolderRow.Chat -> onSelectChat(row.history.id)
                                                is ChatFolderRow.Folder -> onToggleFolder(row)
                                                is ChatFolderRow.Binding -> onToggleBinding(row.key)
                                            }
                                        }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val icon = when (row) {
                                        is ChatFolderRow.Folder -> if (row.expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight
                                        is ChatFolderRow.Binding -> if (row.key in collapsedBindings) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown
                                        is ChatFolderRow.Chat -> Icons.Default.ChatBubbleOutline
                                    }
                                    if (selectionMode && row is ChatFolderRow.Chat) {
                                        Checkbox(checked = selected, onCheckedChange = null,
                                            enabled = !deleting && selectionEnabled, modifier = Modifier.size(20.dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = rowContentColor,
                                                checkmarkColor = rowColor,
                                                uncheckedColor = rowContentColor.copy(alpha = 0.75f),
                                            ))
                                    } else Icon(icon, null, Modifier.size(18.dp), tint = rowContentColor.copy(alpha = 0.75f))
                                    Spacer(Modifier.width(6.dp))
                                    if (row is ChatFolderRow.Folder) {
                                        Icon(Icons.Default.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        if (row is ChatFolderRow.Chat) {
                                            DissolvingChatTitle(title, deletionProgress[row.history.id] ?: 0f, rowContentColor)
                                        } else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium, color = rowContentColor,
                                            fontWeight = FontWeight.Medium)
                                        if (row.depth > 4) {
                                            val parentId = when (row) {
                                                is ChatFolderRow.Folder -> row.folder.parentId
                                                is ChatFolderRow.Chat -> row.history.folderId
                                                is ChatFolderRow.Binding -> null
                                            }
                                            Text(folderAncestors(parentId, folders).joinToString(" › ") { it.name },
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall, color = rowContentColor.copy(alpha = 0.75f))
                                        }
                                        if (row is ChatFolderRow.Chat && row.history.parentChatId != null) {
                                            val parent = chatsById[row.history.parentChatId]
                                            if (parent != null) Text(parent.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall, color = rowContentColor.copy(alpha = 0.75f))
                                        }
                                    }

                                    if (row is ChatFolderRow.Chat) {
                                        if (row.history.id in activeStreamingIds) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                        if (row.history.pinned) Icon(Icons.Default.PushPin, stringResource(R.string.pin_chat), Modifier.size(14.dp), tint = rowContentColor)
                                        if (row.history.locked) Icon(Icons.Default.Lock, stringResource(R.string.lock_chat), Modifier.size(14.dp), tint = rowContentColor)
                                    }
                                }
                                if (!selectionMode) IconButton(
                                    onClick = {
                                        when (row) {
                                            is ChatFolderRow.Chat -> onChatMenu(row.history)
                                            is ChatFolderRow.Folder -> onFolderMenu(row)
                                            is ChatFolderRow.Binding -> onBindingCreate(row)
                                        }
                                    }, enabled = !deleting, modifier = Modifier.size(30.dp),
                                ) { Icon(Icons.Default.MoreVert, stringResource(R.string.folder_actions), Modifier.size(16.dp), tint = rowContentColor.copy(alpha = 0.75f)) }
                                if (row.node() != null && draggingEnabled && !deleting) {
                                    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                    val description = stringResource(R.string.drag_item, title)
                                    Box(
                                        Modifier.size(26.dp).onGloballyPositioned { coordinates = it }
                                            .pointerInput(row.key) {
                                                detectDragGestures(
                                                    onDragStart = { start ->
                                                        pointer = requireNotNull(coordinates).localToRoot(start)
                                                        dragSource = row
                                                        latestInteraction(true)
                                                        hostView.parent?.requestDisallowInterceptTouchEvent(true)
                                                        updateTarget()
                                                    },
                                                    onDragEnd = { finishDrag(true) }, onDragCancel = { finishDrag(false) },
                                                    onDrag = { change, amount -> change.consume(); pointer += amount; updateTarget() },
                                                )
                                            }.semantics { contentDescription = description },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Default.DragHandle, null, Modifier.size(16.dp), tint = rowContentColor.copy(alpha = 0.6f)) }
                                }
                            }
                        }
                        if (row is ChatFolderRow.Chat && !deleting && !selectionMode) {
                            val edit = SwipeAction(onSwipe = { onEditChat(row.history) }, background = MaterialTheme.colorScheme.primary,
                                icon = { Icon(Icons.Default.Edit, stringResource(R.string.edit_title), Modifier.padding(12.dp), tint = Color.White) })
                            val delete = SwipeAction(onSwipe = { onDeleteChat(row.history) }, background = MaterialTheme.colorScheme.error,
                                icon = { Icon(Icons.Default.Delete, stringResource(R.string.delete), Modifier.padding(12.dp), tint = Color.White) })
                            SwipeableActionsBox(startActions = listOf(edit), endActions = if (row.history.locked) emptyList() else listOf(delete), swipeThreshold = 100.dp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))) { content() }
                        } else content()
                        if (highlighted && zone == FolderDropZone.AFTER) HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
                    }
                }
            }
            HistoryQuickScroller(listState, rows.size, onInteractionChange, Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp))
            if (dragSource != null) {
                val title = when (val source = dragSource) {
                    is ChatFolderRow.Folder -> source.folder.name
                    is ChatFolderRow.Chat -> source.history.title
                    else -> ""
                }
                Surface(
                    modifier = Modifier.offset { IntOffset(8, (pointer.y - listBounds.top - 24).toInt()) }
                        .fillMaxWidth().padding(end = 28.dp),
                    shape = RoundedCornerShape(8.dp), shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { Text(title, Modifier.padding(12.dp), color = getTextColorForBackground(MaterialTheme.colorScheme.surfaceContainerHigh),
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.ChatArchiveFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.chat.folders.*
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatHistoryDisplayMode
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchAction
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.batch.ChatBatchActionBar
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.batch.ChatBatchDeleteDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.batch.ChatBatchMoveDialog
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchProgress
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.batch.ChatBatchSelectionHeader

private data class FolderEditRequest(
    val folder: ChatFolder? = null, val parentId: String? = null,
    val binding: ChatFolderBinding, val scope: String,
)

@Composable
fun FolderChatHistoryPanel(
    modifier: Modifier, onNewChat: (String?, String?, String?) -> Unit,
    onSelectChat: (String) -> Unit, onDeleteChat: (String, Boolean) -> Unit,
    onUpdateChatTitle: (String, String) -> Unit, onSummarizeChatTitle: (String) -> Unit,
    onUpdateChatBinding: (String, String?, String?) -> Unit,
    chatHistories: List<ChatHistory>, currentId: String?, activeStreamingChatIds: Set<String>,
    lazyListState: LazyListState?, onBack: (() -> Unit)?, searchQuery: String, onSearchQueryChange: (String) -> Unit,
    historyDisplayMode: ChatHistoryDisplayMode, onDisplayModeChange: (ChatHistoryDisplayMode) -> Unit,
    autoSwitchCharacterCard: Boolean, onAutoSwitchCharacterCardChange: (Boolean) -> Unit,
    autoSwitchChatOnCharacterSelect: Boolean, onAutoSwitchChatOnCharacterSelectChange: (Boolean) -> Unit,
    onQuickScrollInteractionChange: (Boolean) -> Unit, activePrompt: ActivePrompt,
    batchProgress: ChatBatchProgress?,
    onRunBatch: (ChatBatchAction, List<String>) -> Unit,
    embeddedInSidebar: Boolean = false,
    deletionProgress: Map<String, Float> = emptyMap(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val context = LocalContext.current
    val manager = remember(context) { ChatHistoryManager.getInstance(context) }
    val repository = manager.chatFolders
    val cardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val groupManager = remember(context) { CharacterGroupCardManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var cards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var groups by remember { mutableStateOf<List<CharacterGroupCard>>(emptyList()) }
    val observedSnapshots = remember(repository) { repository.snapshots.catch { cause ->
        AppLogger.e("ChatFolders", "读取文件夹列表失败", cause)
        error = context.getString(R.string.folder_operation_failed)
    } }
    val snapshot by observedSnapshots.collectAsState(initial = null)
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var batchDeleteIds by remember { mutableStateOf<List<String>?>(null) }
    var batchMoveIds by remember { mutableStateOf<List<String>?>(null) }
    var movingChats by remember { mutableStateOf(false) }
    var moveError by remember { mutableStateOf<String?>(null) }
    val batchBusy = batchProgress != null || movingChats
    val inSelectionMode = selectionMode || batchBusy
    val allFolders = snapshot?.folders.orEmpty()
    val foldersById = remember(allFolders) { allFolders.associateBy { it.id } }
    val eligibleIds = remember(chatHistories) { chatHistories.map { it.id }.toSet() }
    val eligibleHistories = remember(snapshot, eligibleIds) {
        snapshot?.chats.orEmpty().filter { it.id in eligibleIds }.map { it.toChatHistory(emptyList()) }
    }
    val histories = remember(eligibleHistories, showArchived) {
        eligibleHistories.filter { it.archived == showArchived }
    }
    // 归档视图只展开含归档对话的路径，不把所有空文件夹带进来。
    val folders = remember(allFolders, histories, showArchived) {
        if (!showArchived) allFolders else {
            val pathIds = histories.flatMap { chat ->
                folderAncestors(chat.folderId, foldersById).map { it.id }
            }.toSet()
            allFolders.filter { it.id in pathIds }
        }
    }
    var collapsed by rememberLocal("chat_history_collapsed_folder_ids", emptySet<String>())
    var collapsedBindings by rememberLocal("chat_history_collapsed_characters", emptySet<String>())
    var sort by rememberLocal("chat_history_sort_mode", ChatHistorySortMode.DEFAULT)
    var showSearch by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDragHint by rememberLocal("chat_history_folder_drag_hint", true)
    var matchedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searching by remember { mutableStateOf(false) }
    var folderEdit by remember { mutableStateOf<FolderEditRequest?>(null) }
    var folderMenu by remember { mutableStateOf<ChatFolderRow.Folder?>(null) }
    var bindingMenu by remember { mutableStateOf<ChatFolderRow.Binding?>(null) }
    var folderDelete by remember { mutableStateOf<ChatFolder?>(null) }
    var chatMenu by remember { mutableStateOf<ChatHistory?>(null) }
    var chatEdit by remember { mutableStateOf<ChatHistory?>(null) }
    var chatDelete by remember { mutableStateOf<ChatHistory?>(null) }
    val listState = lazyListState ?: rememberLazyListState()
    LaunchedEffect(showArchived) { listState.scrollToItem(0) }
    fun perform(onSuccess: () -> Unit = {}, operation: suspend () -> Unit) {
        coroutineScope.launch {
            try { withContext(Dispatchers.IO) { operation() }; onSuccess() }
            catch (cause: CancellationException) { throw cause }
            catch (cause: Exception) {
                AppLogger.e("ChatFolders", "文件夹操作失败", cause)
                error = context.getString(R.string.folder_operation_failed)
            }
        }
    }
    LaunchedEffect(cardManager) {
        try {
            cardManager.characterCardListFlow.collectLatest { ids ->
                cards = withContext(Dispatchers.IO) { ids.mapNotNull { cardManager.getCharacterCard(it) } }
            }
        } catch (cause: CancellationException) { throw cause } catch (cause: Exception) {
            AppLogger.e("ChatFolders", "读取角色卡失败", cause)
            error = context.getString(R.string.folder_operation_failed)
        }
    }
    LaunchedEffect(groupManager) { groupManager.allCharacterGroupCardsFlow.collectLatest { groups = it } }
    val unboundLabel = stringResource(R.string.unbound_character_card)
    val groupPrefix = stringResource(R.string.character_group_binding_prefix)
    val rootLabel = stringResource(R.string.folder_root)
    fun label(binding: ChatFolderBinding): String = when {
        binding.characterGroupId != null -> {
            val group = groups.firstOrNull { it.id == binding.characterGroupId }
            if (group != null) "$groupPrefix: ${group.name}"
            else context.getString(R.string.missing_character_group_id, binding.characterGroupId)
        }
        binding.characterCardName != null -> binding.characterCardName
        else -> unboundLabel
    }
    val activeCard = (activePrompt as? ActivePrompt.CharacterCard)?.let { prompt -> cards.firstOrNull { it.id == prompt.id } }
    val activeBinding = when (activePrompt) {
        is ActivePrompt.CharacterCard -> ChatFolderBinding(activeCard?.name, null)
        is ActivePrompt.CharacterGroup -> ChatFolderBinding(null, activePrompt.id)
    }
    val createBinding = if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) ChatFolderBinding(null, null) else activeBinding
    fun scope(binding: ChatFolderBinding): String = when {
        binding.characterGroupId != null -> "binding:group:${binding.characterGroupId}"
        binding.characterCardName != null -> "binding:card:${binding.characterCardName}"
        else -> "binding:unbound"
    }
    fun location(parentId: String?, binding: ChatFolderBinding): String {
        val prefix = if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) rootLabel else label(binding)
        return (listOf(prefix) + folderAncestors(parentId, foldersById).map { it.name }).joinToString(" › ")
    }
    LaunchedEffect(searchQuery) {
        matchedIds = emptySet()
        searching = false
        val query = searchQuery.trim()
        if (query.length < 2) return@LaunchedEffect
        delay(350)
        searching = true
        try { matchedIds = manager.searchChatIdsByContent(query) }
        catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) {
            AppLogger.e("ChatFolders", "搜索对话内容失败", cause)
            error = context.getString(R.string.folder_operation_failed)
        } finally { searching = false }
    }
    val options = buildList {
        add(HistoryBindingOption(unboundLabel, ChatFolderBinding(null, null)))
        cards.forEach { add(HistoryBindingOption(it.name, ChatFolderBinding(it.name, null))) }
        groups.forEach { add(HistoryBindingOption("$groupPrefix: ${it.name}", ChatFolderBinding(null, it.id))) }
    }
    val bindings = (histories.map { ChatFolderBinding(it.characterCardName, it.characterGroupId) } +
        folders.map { ChatFolderBinding(it.characterCardName, it.characterGroupId) } + activeBinding).distinct().sortedBy(::label)
    val rows = remember(snapshot, histories, folders, collapsed, collapsedBindings, searchQuery, matchedIds, sort,
        historyDisplayMode, activeBinding, activeCard?.isDefault, bindings, groups, unboundLabel, showArchived) {
        if (snapshot == null || (showArchived && histories.isEmpty())) emptyList() else when (historyDisplayMode) {
            ChatHistoryDisplayMode.BY_CHARACTER_CARD -> bindings.flatMap { binding ->
                val key = scope(binding)
                val children = buildChatFolderTree(
                    histories.filter { ChatFolderBinding(it.characterCardName, it.characterGroupId) == binding }, folders,
                    folders.filter { ChatFolderBinding(it.characterCardName, it.characterGroupId) == binding }.map { it.id }.toSet(),
                    key, binding, collapsed, searchQuery, matchedIds, sort, baseDepth = 1,
                )
                if (searchQuery.isNotBlank() && children.isEmpty()) emptyList() else {
                    listOf(ChatFolderRow.Binding(key, label(binding), binding, key)) +
                        if (key in collapsedBindings && searchQuery.isBlank()) emptyList() else children
                }
            }
            else -> {
                val owned = if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) folders else folders.filter {
                    val binding = ChatFolderBinding(it.characterCardName, it.characterGroupId)
                    binding == activeBinding || (activeCard?.isDefault == true && binding == ChatFolderBinding(null, null))
                }
                buildChatFolderTree(histories, folders, owned.map { it.id }.toSet(),
                    if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) "folders" else scope(activeBinding),
                    null,
                    collapsed, searchQuery, matchedIds, sort)
            }
        }
    }
    val selectableChats = remember(rows, deletionProgress.keys) {
        rows.filterIsInstance<ChatFolderRow.Chat>().map { it.history }
            .filter { it.id !in deletionProgress }.distinctBy { it.id }
    }
    val selectableIds = remember(selectableChats) { selectableChats.map { it.id }.toSet() }
    val selectedChats = selectableChats.filter { it.id in selectedIds }
    LaunchedEffect(snapshot != null, selectableIds) {
        // 过滤、折叠或角色范围变化后不保留隐藏选择，避免操作列表外的对话。
        if (snapshot != null) selectedIds = selectedIds.intersect(selectableIds)
    }
    fun enterSelection() {
        selectionMode = true
        selectedIds = emptySet()
        chatMenu = null
        folderMenu = null
        bindingMenu = null
        showSettings = false
        onQuickScrollInteractionChange(false)
    }
    fun move(request: ChatFolderMove) {
        perform(onSuccess = { if (request.anchor != null) sort = ChatHistorySortMode.DEFAULT }) {
            repository.move(request)
        }
    }
    fun promptDelete(chat: ChatHistory) {
        if (!chat.locked && chat.id !in deletionProgress) chatDelete = chat
    }
    Column(modifier) {
        if (inSelectionMode) {
            ChatBatchSelectionHeader(
                selectedCount = selectedChats.size,
                allSelected = selectableIds.isNotEmpty() && selectedChats.size == selectableIds.size,
                canSelect = selectableIds.isNotEmpty(),
                busy = batchBusy,
                contentColor = contentColor,
                onSelectAll = {
                    selectedIds = if (selectedChats.size == selectableIds.size) emptySet() else selectableIds
                },
                onExit = { selectionMode = false; selectedIds = emptySet() },
            )
        } else if (embeddedInSidebar) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HistoryCreateCapsule(
                    onNewChat = { onNewChat(createBinding.characterCardName, createBinding.characterGroupId,
                        histories.firstOrNull { it.id == currentId }?.folderId) },
                    onNewFolder = {
                        folderEdit = FolderEditRequest(binding = createBinding,
                            scope = if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) "folders" else scope(createBinding))
                    }, modifier = Modifier.weight(1f),
                )
                // 搜索和设置各左移一个槽位，最右侧留给批量操作。
                IconButton(onClick = {
                    showSearch = !showSearch
                    requestSearchFocus = showSearch
                    if (!showSearch) onSearchQueryChange("")
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Search, stringResource(R.string.search), tint = contentColor, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showSettings = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Tune, stringResource(R.string.chat_history_settings), tint = contentColor, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = ::enterSelection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Checklist, stringResource(R.string.chat_batch_enter),
                        tint = contentColor, modifier = Modifier.size(18.dp))
                }
            }
            if (showSearch) {
                val focusRequester = remember { FocusRequester() }
                // 只有主动打开搜索才请求焦点，隐藏抽屉保存查询时不弹出输入法。
                LaunchedEffect(requestSearchFocus) {
                    if (requestSearchFocus) {
                        focusRequester.requestFocus()
                        requestSearchFocus = false
                    }
                }
                BasicTextField(
                    value = searchQuery, onValueChange = onSearchQueryChange, singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = contentColor),
                    cursorBrush = SolidColor(contentColor),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .height(36.dp).border(0.5.dp, contentColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .focusRequester(focusRequester),
                    decorationBox = { input ->
                        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (searching) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            else Icon(Icons.Default.Search, null, tint = contentColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                if (searchQuery.isEmpty()) Text(stringResource(R.string.search_chat_history_hint),
                                    style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.6f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                input()
                            }
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.clear_search), tint = contentColor, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) onSearchQueryChange("") }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.Search, stringResource(R.string.search), Modifier.size(20.dp))
                }
                IconButton(onClick = { showSettings = true }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.Tune, stringResource(R.string.chat_history_settings), Modifier.size(20.dp))
                }
                if (onBack != null) IconButton(onClick = onBack, Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), Modifier.size(20.dp))
                }
                IconButton(onClick = ::enterSelection, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Checklist, stringResource(R.string.chat_batch_enter), Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryCreateCapsule(
                    onNewChat = { onNewChat(createBinding.characterCardName, createBinding.characterGroupId,
                        histories.firstOrNull { it.id == currentId }?.folderId) },
                    onNewFolder = { folderEdit = FolderEditRequest(binding = createBinding,
                        scope = if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) "folders" else scope(createBinding)) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(48.dp))
            }
            if (showSearch) OutlinedTextField(searchQuery, onSearchQueryChange, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_chat_history_hint)) },
                leadingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
        ChatArchiveFilter(
            archived = showArchived,
            archivedCount = eligibleHistories.count { it.archived },
            onChange = {
                if (!batchBusy) { selectedIds = emptySet(); showArchived = it }
            },
            contentColor = contentColor,
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = selectedContentColor,
        )
        AnimatedVisibility(visible = inSelectionMode, enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()) {
            ChatBatchActionBar(
                selectedChats = selectedChats,
                archived = showArchived,
                progress = batchProgress,
                moving = movingChats,
                onMove = {
                    batchMoveIds = selectedChats.map { it.id }
                    moveError = null
                },
                containerColor = selectedContainerColor,
                contentColor = selectedContentColor,
                onAction = { action ->
                    val ids = selectedChats.map { it.id }
                    if (action == ChatBatchAction.DELETE) batchDeleteIds = ids
                    else onRunBatch(action, ids)
                },
            )
        }
        if (showDragHint && !inSelectionMode) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.folder_drag_hint), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f))
            IconButton(onClick = { showDragHint = false }, Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.cancel), Modifier.size(14.dp), tint = contentColor)
            }
        }
        if (snapshot == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (rows.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(if (searchQuery.isNotBlank()) R.string.folder_no_results
                else if (showArchived) R.string.chat_archive_empty else R.string.folder_empty_history),
                style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.75f))
        } else FolderTreeList(rows, foldersById, listState, currentId, activeStreamingChatIds, histories,
            draggingEnabled = searchQuery.isBlank() && !inSelectionMode,
            manualSort = sort == ChatHistorySortMode.DEFAULT, collapsedBindings = collapsedBindings,
            selectionMode = inSelectionMode, selectedIds = selectedIds, selectionEnabled = !batchBusy,
            onSelectChat = { id ->
                if (inSelectionMode) {
                    if (!batchBusy) selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                } else onSelectChat(id)
            }, onChatMenu = { chatMenu = it }, onEditChat = { chatEdit = it }, onDeleteChat = ::promptDelete,
            onFolderMenu = { folderMenu = it }, onToggleFolder = { row ->
                if (!batchBusy && searchQuery.isBlank()) collapsed = if (row.key in collapsed) collapsed - row.key else collapsed + row.key
            }, onToggleBinding = { key -> if (!batchBusy && searchQuery.isBlank()) collapsedBindings = if (key in collapsedBindings) collapsedBindings - key else collapsedBindings + key },
            onBindingCreate = { bindingMenu = it }, onHoverExpand = { collapsed = collapsed - it.key },
            onMove = ::move, onInteractionChange = onQuickScrollInteractionChange, modifier = Modifier.weight(1f),
            contentColor = contentColor, selectedContainerColor = selectedContainerColor, selectedContentColor = selectedContentColor,
            deletionProgress = deletionProgress)
    }
    batchMoveIds?.let { ids ->
        ChatBatchMoveDialog(
            selectedCount = ids.size,
            folders = allFolders,
            moving = movingChats,
            error = moveError,
            onDismiss = { if (!movingChats) { batchMoveIds = null; moveError = null } },
            onMove = { folderId ->
                if (batchProgress == null && !movingChats) {
                    movingChats = true
                    moveError = null
                    coroutineScope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) { repository.moveSelectedChats(ids, folderId) }
                            selectedIds = selectedIds - ids.toSet()
                            batchMoveIds = null
                            OKToast.success(context, context.getString(R.string.chat_batch_move_success, count)).show()
                        } catch (cause: CancellationException) {
                            throw cause
                        } catch (cause: Exception) {
                            AppLogger.e("ChatFolders", "批量移动对话失败", cause)
                            moveError = context.getString(R.string.chat_batch_move_failed)
                        } finally {
                            movingChats = false
                        }
                    }
                }
            },
        )
    }
    batchDeleteIds?.let { ids ->
        val targets = eligibleHistories.filter { it.id in ids }
        ChatBatchDeleteDialog(targets, onDismiss = { batchDeleteIds = null }, onConfirm = {
            onRunBatch(ChatBatchAction.DELETE, targets.map { it.id })
            batchDeleteIds = null
        })
    }
    folderEdit?.let { request ->
        FolderNameDialog(
            stringResource(if (request.folder != null) R.string.folder_rename else if (request.parentId != null) R.string.folder_create_child else R.string.folder_create),
            request.folder?.name.orEmpty(), location(request.parentId, request.binding), { folderEdit = null },
        ) { name ->
            perform {
                if (request.folder != null) repository.rename(request.folder.id, name)
                else repository.create(name, request.parentId, request.binding)
            }
            if (request.parentId != null) collapsed = collapsed - folderKey(request.scope, request.parentId)
            folderEdit = null
        }
    }
    folderMenu?.let { row ->
        val binding = if (historyDisplayMode == ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY) activeBinding
            else row.binding ?: ChatFolderBinding(row.folder.characterCardName, row.folder.characterGroupId)
        HistoryActionsDialog(row.folder.name, listOf(
            HistoryAction(stringResource(R.string.new_chat), Icons.Default.Add) { onNewChat(binding.characterCardName, binding.characterGroupId, row.folder.id); folderMenu = null },
            HistoryAction(stringResource(R.string.folder_create_child), Icons.Default.CreateNewFolder) { folderEdit = FolderEditRequest(parentId = row.folder.id, binding = binding, scope = row.scope); folderMenu = null },
            HistoryAction(stringResource(R.string.folder_rename), Icons.Default.Edit) { folderEdit = FolderEditRequest(row.folder, row.folder.parentId, binding, row.scope); folderMenu = null },
            HistoryAction(stringResource(R.string.folder_delete), Icons.Default.Delete) { folderDelete = row.folder; folderMenu = null },
        )) { folderMenu = null }
    }
    bindingMenu?.let { row ->
        HistoryActionsDialog(row.name, listOf(
            HistoryAction(stringResource(R.string.new_chat), Icons.Default.Add) { onNewChat(row.binding.characterCardName, row.binding.characterGroupId, null); bindingMenu = null },
            HistoryAction(stringResource(R.string.folder_create), Icons.Default.CreateNewFolder) { folderEdit = FolderEditRequest(binding = row.binding, scope = row.scope); bindingMenu = null },
        )) { bindingMenu = null }
    }
    folderDelete?.let { folder ->
        AlertDialog(onDismissRequest = { folderDelete = null }, title = { Text(stringResource(R.string.folder_delete)) },
            text = { Text(stringResource(R.string.folder_delete_keep_contents, folder.name)) },
            confirmButton = { TextButton(onClick = { perform { repository.deleteKeepingContents(folder.id) }; folderDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { folderDelete = null }) { Text(stringResource(R.string.cancel)) } })
    }
    chatMenu?.let { selected ->
        val chat = histories.firstOrNull { it.id == selected.id }
        if (chat != null) HistoryActionsDialog(chat.title, listOf(
            HistoryAction(stringResource(R.string.edit_title), Icons.Default.Edit) { chatEdit = chat; chatMenu = null },
            HistoryAction(stringResource(R.string.summarize_chat_title), Icons.Default.AutoAwesome) { onSummarizeChatTitle(chat.id); chatMenu = null },
            HistoryAction(
                stringResource(if (chat.archived) R.string.chat_unarchive else R.string.chat_archive),
                if (chat.archived) Icons.Default.Restore else Icons.Default.Archive,
            ) { perform { manager.updateChatArchived(chat.id, !chat.archived) }; chatMenu = null },
            HistoryAction(stringResource(if (chat.pinned) R.string.unpin_chat else R.string.pin_chat), Icons.Default.PushPin) { perform { manager.updateChatPinned(chat.id, !chat.pinned) }; chatMenu = null },
            HistoryAction(stringResource(if (chat.locked) R.string.unlock_chat else R.string.lock_chat), Icons.Default.Lock) { perform { manager.updateChatLocked(chat.id, !chat.locked) }; chatMenu = null },
            HistoryAction(stringResource(R.string.delete), Icons.Default.Delete, enabled = !chat.locked) { promptDelete(chat); chatMenu = null },
        )) { chatMenu = null }
    }
    chatEdit?.let { chat -> HistoryChatEditDialog(chat, options, ::label, { chatEdit = null }) { title, binding ->
        onUpdateChatTitle(chat.id, title)
        if (binding != ChatFolderBinding(chat.characterCardName, chat.characterGroupId)) onUpdateChatBinding(chat.id, binding.characterCardName, binding.characterGroupId)
        chatEdit = null
    } }
    chatDelete?.let { chat -> AlertDialog(onDismissRequest = { chatDelete = null },
        title = { Text(stringResource(R.string.confirm_delete_chat)) }, text = { Text(stringResource(R.string.delete_chat_confirmation, chat.title)) },
        confirmButton = { TextButton(onClick = { onDeleteChat(chat.id, android.animation.ValueAnimator.areAnimatorsEnabled()); chatDelete = null },
            enabled = chat.id !in deletionProgress && histories.any { it.id == chat.id && !it.locked }) { Text(stringResource(R.string.confirm_delete_action)) } },
        dismissButton = { TextButton(onClick = { chatDelete = null }) { Text(stringResource(R.string.cancel)) } }) }
    if (showSettings) FolderHistorySettingsDialog(historyDisplayMode, onDisplayModeChange, sort, { sort = it },
        autoSwitchCharacterCard, onAutoSwitchCharacterCardChange, autoSwitchChatOnCharacterSelect, onAutoSwitchChatOnCharacterSelectChange,
        onExpandAll = { collapsed = emptySet(); collapsedBindings = emptySet() },
        onCollapseAll = {
            collapsed = collapsed + folders.flatMap { folder -> (bindings.map(::scope) + "folders").map { folderKey(it, folder.id) } }
            collapsedBindings = collapsedBindings + bindings.map(::scope)
        }, onDismiss = { showSettings = false })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text(stringResource(R.string.folder_actions)) },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.cancel)) } }) }
}

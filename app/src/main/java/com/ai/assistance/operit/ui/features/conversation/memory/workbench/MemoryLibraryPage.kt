package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.conversation.memory.screens.FolderNavigator
import com.ai.assistance.operit.ui.features.conversation.memory.screens.dialogs.*
import com.ai.assistance.operit.ui.features.conversation.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.conversation.memory.viewmodel.MemoryViewModelFactory
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.ai.assistance.operit.ui.features.conversation.memory.screens.dialogs.BatchDeleteConfirmDialog
import com.ai.assistance.operit.ui.features.conversation.memory.screens.dialogs.DocumentViewDialog
import com.ai.assistance.operit.ui.features.conversation.memory.screens.dialogs.MemorySearchSettingsDialog
import com.ai.assistance.operit.ui.features.conversation.memory.screens.dialogs.MemorySearchSimulationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryLibraryPage(spaceId: String, onSwitchSpace: (String) -> Unit, onConfigureEmbedding: () -> Unit, initialList: Boolean, initialMemoryUuid: String?) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val viewModel: MemoryViewModel = viewModel(key = spaceId, factory = MemoryViewModelFactory(context, spaceId))
    val uiState by viewModel.uiState.collectAsState()
    val historyProgress by viewModel.memoryHistoryRebuildProgress.collectAsState()
    val isCurrentScreen = LocalIsCurrentScreen.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val spaceIds by preferences.memorySpaceListFlow.collectAsState(initial = emptyList())
    var spaces by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    var listView by rememberSaveable { mutableStateOf(initialList) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var relatedId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreFolder by rememberSaveable { mutableStateOf("") }
    var restoreList by rememberSaveable { mutableStateOf(false) }
    var restoreExpanded by rememberSaveable { mutableStateOf(false) }
    var restoreQuery by rememberSaveable { mutableStateOf("") }
    var showCross by rememberSaveable { mutableStateOf(true) }
    var allLabels by rememberSaveable { mutableStateOf(false) }
    var searchBody by rememberSaveable { mutableStateOf(true) }
    var spaceSheet by remember { mutableStateOf(false) }
    var folderSheet by remember { mutableStateOf(false) }
    var showFolderNavigator by remember { mutableStateOf(false) }
    var displaySheet by remember { mutableStateOf(false) }
    var searchSheet by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var bottomAddMenu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var locateToken by remember { mutableIntStateOf(0) }
    var openedInitial by rememberSaveable { mutableStateOf(false) }
    var linkInitialIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val uncategorized = stringResource(R.string.memory_uncategorized)

    LaunchedEffect(Unit) { viewModel.resetLibraryView() }
    LaunchedEffect(spaceIds, isCurrentScreen) {
        if (isCurrentScreen) {
            spaces = spaceIds.map { preferences.getMemorySpaceFlow(it).first() }
            viewModel.loadMemoryGraph()
            viewModel.loadFolderPaths()
        }
    }
    LaunchedEffect(uiState.searchQuery, searchBody, uiState.searchConfig) { delay(300); viewModel.searchMemories(searchBody) }
    LaunchedEffect(initialMemoryUuid, uiState.memories) {
        if (!openedInitial && initialMemoryUuid != null) uiState.memories.firstOrNull { it.uuid == initialMemoryUuid }?.let { openedInitial = true; viewModel.selectMemory(it) }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { OKToast.typed(context, it, uiState.messageKind, OKToast.LENGTH_SHORT).show(); viewModel.clearMessage() }
    }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null && !uiState.isSearchSettingsDialogVisible) OKToast.error(context, uiState.error.orEmpty(), OKToast.LENGTH_SHORT).show()
    }
    LaunchedEffect(uiState.selectedNodeId) {
        if (uiState.selectedNodeId == null && relatedId != null) relatedId = null
    }
    fun locate(memory: Memory) { relatedId = null; expanded = true; listView = false; viewModel.locateMemory(memory); locateToken++ }
    fun focus(id: String) {
        viewModel.dismissMemoryDetails()
        if (relatedId == null) {
            restoreFolder = uiState.selectedFolderPath; restoreList = listView; restoreExpanded = expanded; restoreQuery = uiState.searchQuery
            relatedId = id; expanded = true; listView = false; viewModel.onSearchQueryChange("")
        } else {
            relatedId = null; listView = restoreList; expanded = restoreExpanded
            viewModel.selectFolder(restoreFolder, clearSelection = false)
            viewModel.onSearchQueryChange(restoreQuery)
        }
        locateToken++
    }
    fun link() {
        linkInitialIds = if (uiState.boxSelectedNodeIds.size == 2) uiState.boxSelectedNodeIds.toList() else listOfNotNull(uiState.selectedNodeId)
        viewModel.toggleLinkingMode(true)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val document = readMemoryDocument(context, uri)
                relatedId = null; expanded = true
                viewModel.importDocument(document.title, document.uri, document.content)
            } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { OKToast.error(context, context.getString(R.string.memory_workbench_import_error, failure.message.orEmpty()), OKToast.LENGTH_SHORT).show() }
        }
    }
    fun addDocument() { importer.launch(arrayOf("text/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }
    val searching = uiState.searchQuery.isNotBlank()
    val overview = uiState.selectedFolderPath.isEmpty() && !expanded && !searching && relatedId == null && !uiState.isBoxSelectionMode && uiState.selectedNodeId == null
    val scoped = remember(uiState.graph, uiState.selectedFolderPath, showCross, relatedId, uiState.searchResultIds, uncategorized) { memoryGraphScope(uiState.graph, uiState.selectedFolderPath, uncategorized, showCross, relatedId, uiState.searchResultIds) }
    val graph = remember(scoped, overview, uncategorized) { if (overview) memoryFolderGraph(scoped, uncategorized) else scoped }
    val nodeById = remember(uiState.graph) { uiState.graph.nodes.associateBy { it.id } }
    val list = remember(uiState.memories, uiState.graph, uiState.selectedFolderPath, uiState.searchResultIds, uncategorized) {
        uiState.memories.filter { memory -> nodeById[memory.uuid]?.let { memoryMatchesFolder(it, uiState.selectedFolderPath, uncategorized) } == true && (uiState.searchResultIds == null || memory.uuid in uiState.searchResultIds.orEmpty()) }.sortedByDescending { it.updatedAt.time }
    }
    val selected = uiState.memories.firstOrNull { it.uuid == uiState.selectedNodeId }
    val hasPeek = selected != null && !listView && !searching && !uiState.isBoxSelectionMode && !overview
    BackHandler(enabled = relatedId != null && uiState.selectedMemory == null && !uiState.isEditing && !uiState.isLinkingMode) { relatedId?.let { focus(it) } }

    CustomScaffold { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val controlsHeight = maxHeight * 0.2f
            Column(Modifier.fillMaxSize()) {
                MemoryLibraryControls(
                    modifier = Modifier.height(controlsHeight),
                    spaceControls = { compact ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).clickable { spaceSheet = true }) {
                                if (!compact) Text(
                                    stringResource(R.string.screen_title_memory_base),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        spaces.firstOrNull { it.id == spaceId }?.name.orEmpty(),
                                        Modifier.weight(1f),
                                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.memory_overview_space_actions))
                                }
                            }
                            Box {
                                IconButton(onClick = { toolsMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.MoreHoriz, stringResource(R.string.memory_overview_space_actions))
                                }
                                MemoryMenu(toolsMenu, { toolsMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.memory_workbench_display)) }, leadingIcon = { Icon(Icons.Default.Tune, null) }, onClick = { toolsMenu = false; displaySheet = true })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.memory_workbench_manage_folders)) }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { toolsMenu = false; showFolderNavigator = true })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.memory_settings_title)) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { toolsMenu = false; viewModel.showSearchSettingsDialog(true) })
                                }
                            }
                        }
                    },
                    searchControls = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { relatedId = null; viewModel.onSearchQueryChange(it) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp),
                            placeholder = { Text(stringResource(R.string.memory_search_hint), maxLines = 1) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            shape = RoundedCornerShape(15.dp),
                            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                            trailingIcon = {
                                Row {
                                    if (searching) IconButton(onClick = { viewModel.onSearchQueryChange("") }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Close, stringResource(R.string.memory_workbench_clear_search), Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { searchSheet = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Tune, stringResource(R.string.memory_workbench_search_scope), Modifier.size(20.dp))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); viewModel.searchMemories(searchBody) }),
                        )
                    },
                    folderControls = { compact ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            TextButton(
                                onClick = { folderSheet = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(uiState.selectedFolderPath.ifEmpty { stringResource(R.string.memory_workbench_all_folders) }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(15.dp))
                            }
                            Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Row(Modifier.padding(2.dp)) {
                                    listOf(false, true).forEach { showList ->
                                        val action = if (showList) R.string.memory_workbench_list else R.string.memory_workbench_graph
                                        val icon = if (showList) Icons.Default.List else Icons.Default.Share
                                        val selectedView = listView == showList
                                        val chooseView = {
                                            listView = showList
                                            if (showList) { relatedId = null; viewModel.clearSelection() }
                                        }
                                        if (compact) IconButton(
                                            onClick = chooseView,
                                            modifier = Modifier.size(34.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                containerColor = if (selectedView) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                contentColor = if (selectedView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        ) { Icon(icon, stringResource(action), Modifier.size(18.dp)) }
                                        else TextButton(
                                            onClick = chooseView,
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            colors = ButtonDefaults.textButtonColors(containerColor = if (selectedView) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                        ) {
                                            Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(action), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                            IconButton(
                                onClick = { relatedId = null; if (!uiState.isBoxSelectionMode) expanded = true; viewModel.toggleBoxSelectionMode(!uiState.isBoxSelectionMode) },
                                modifier = Modifier.size(36.dp),
                            ) { Icon(if (uiState.isBoxSelectionMode) Icons.Default.Close else Icons.Default.SelectAll, stringResource(if (uiState.isBoxSelectionMode) R.string.memory_workbench_finish else R.string.memory_workbench_select), Modifier.size(19.dp)) }
                            IconButton(enabled = uiState.graph.nodes.size >= 2 && !uiState.isLoading, onClick = { link() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Link, stringResource(R.string.memory_workbench_link), Modifier.size(19.dp))
                            }
                            Box {
                                IconButton(onClick = { addMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Add, stringResource(R.string.memory_workbench_add), Modifier.size(21.dp))
                                }
                                MemoryMenu(addMenu, { addMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.memory_action_create)) }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { addMenu = false; viewModel.startEditing() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.memory_action_import)) }, leadingIcon = { Icon(Icons.Default.UploadFile, null) }, onClick = { addMenu = false; addDocument() })
                                }
                            }
                        }
                    },
                )
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        uiState.memories.isEmpty() && !uiState.isLoading -> Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Share, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.memory_workbench_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.memory_workbench_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { viewModel.startEditing() }) { Text(stringResource(R.string.memory_action_create)) }
                        }
                        listView || searching -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 100.dp)) {
                            item { Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.memory_workbench_count_memories, list.size), style = MaterialTheme.typography.labelSmall); Text(stringResource(if (uiState.isSearching) R.string.memory_workbench_search_pending else R.string.memory_workbench_latest_sort), style = MaterialTheme.typography.labelSmall) } }
                            if (list.isEmpty() && !uiState.isSearching) item { Text(stringResource(R.string.memory_workbench_empty_search), Modifier.padding(vertical = 36.dp)); if (searching) TextButton(onClick = { viewModel.onSearchQueryChange("") }) { Text(stringResource(R.string.memory_workbench_clear_search)) } }
                            items(list, key = { it.uuid }) { memory ->
                                MemoryListRow(memory, uiState.graph.edges.count { it.sourceId == memory.uuid || it.targetId == memory.uuid }, memory.uuid in uiState.boxSelectedNodeIds, uiState.isBoxSelectionMode) {
                                    val node = nodeById[memory.uuid]
                                    if (uiState.isBoxSelectionMode && node != null) viewModel.selectNode(node) else if (searching) locate(memory) else viewModel.selectMemory(memory)
                                }
                            }
                        }
                        else -> {
                            MemoryGraphCanvas(graph = graph, modifier = Modifier.fillMaxSize(), overview = overview, resetKey = uiState.selectedFolderPath + ":" + expanded, scopeFolder = uiState.selectedFolderPath, selectedId = uiState.selectedNodeId, focusedId = relatedId, selectedEdgeId = uiState.selectedEdge?.id, selectedIds = uiState.boxSelectedNodeIds, boxSelection = uiState.isBoxSelectionMode, linkingIds = uiState.linkingNodeIds, allLabels = allLabels, locateToken = locateToken, hasPeek = hasPeek,
                                onNodeClick = { node -> if (overview) { relatedId = null; viewModel.selectFolder(node.metadata["folder"].orEmpty()) } else { if (relatedId != null) relatedId = node.id; viewModel.selectNode(node) } }, onEdgeClick = { viewModel.selectEdge(it) }, onNodesSelected = viewModel::addNodesToSelection)
                            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { relatedId = null; expanded = false; viewModel.clearSelection(); viewModel.selectFolder("") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) { Icon(Icons.Default.GridView, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(if (overview) stringResource(R.string.memory_workbench_overview) else uiState.selectedFolderPath.ifEmpty { stringResource(R.string.memory_workbench_all_folders) }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    Text(stringResource(if (overview) R.string.memory_workbench_count_memories else R.string.memory_workbench_count_nodes, if (overview) scoped.nodes.size else graph.nodes.size), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(stringResource(when { overview -> R.string.memory_workbench_overview_hint; relatedId != null -> R.string.memory_workbench_filtered_hint; uiState.isBoxSelectionMode -> R.string.memory_workbench_select_hint; else -> R.string.memory_workbench_node_hint }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (uiState.isLoading || uiState.isSearching) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    if (hasPeek && selected != null) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f)), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(selected.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis); IconButton(onClick = { relatedId = null; viewModel.clearSelection() }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Close, stringResource(R.string.close), Modifier.size(18.dp)) } }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { focus(selected.uuid) }) { Text(stringResource(if (relatedId == null) R.string.memory_workbench_focus else R.string.memory_workbench_show_all)) }; TextButton(onClick = { viewModel.selectMemory(selected) }) { Text(stringResource(R.string.memory_details_title)) } }
                        }
                    }
                    if (uiState.isBoxSelectionMode) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleBoxSelectionMode(false) }) { Icon(Icons.Default.Close, stringResource(R.string.memory_workbench_finish)) }
                            Text(stringResource(R.string.memory_workbench_select_count, uiState.boxSelectedNodeIds.size), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            IconButton(enabled = uiState.boxSelectedNodeIds.isNotEmpty(), onClick = { moving = true }) { Icon(Icons.Default.DriveFileMove, stringResource(R.string.memory_workbench_move_selected)) }
                            IconButton(enabled = uiState.boxSelectedNodeIds.size == 2, onClick = { link() }) { Icon(Icons.Default.Link, stringResource(R.string.memory_action_link)) }
                            IconButton(enabled = uiState.boxSelectedNodeIds.isNotEmpty(), onClick = { viewModel.showBatchDeleteConfirm() }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.memory_action_delete_selected), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (!hasPeek && !uiState.isBoxSelectionMode) Box(Modifier.align(Alignment.BottomEnd).padding(14.dp)) {
                        SmallFloatingActionButton(onClick = { bottomAddMenu = true }) { Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.memory_workbench_plain)) } }
                        MemoryMenu(bottomAddMenu, { bottomAddMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.memory_action_create)) }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { bottomAddMenu = false; viewModel.startEditing() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.memory_action_import)) }, leadingIcon = { Icon(Icons.Default.UploadFile, null) }, onClick = { bottomAddMenu = false; addDocument() })
                        }
                    }
                }
            }
                        if (uiState.isSearchSettingsDialogVisible) {
                MemorySearchSettingsDialog(
                    spaceId = spaceId,
                    currentConfig = uiState.searchConfig,
                    autoSaveIntervalMinutes = uiState.autoSaveIntervalMinutes,
                    memoryExtractionCustomRules = uiState.memoryExtractionCustomRules,
                    details = uiState.memorySettingsDetails,
                    embeddingModelName = uiState.memoryEmbeddingModelName,
                    onConfigureEmbedding = {
                        viewModel.showSearchSettingsDialog(false)
                        onConfigureEmbedding()
                    },
                    dimensionUsage = uiState.embeddingDimensionUsage,
                    rebuildProgress = uiState.embeddingRebuildProgress,
                    historyProgress = historyProgress,
                    error = uiState.error,
                    isRebuilding = uiState.isEmbeddingRebuildRunning,
                    isSaving = uiState.isSearchSettingsSaving,
                    initialQuery = uiState.searchQuery,
                    onDismiss = { viewModel.showSearchSettingsDialog(false) },
                    onSave = { config, interval, rules, policy ->
                        viewModel.saveSearchSettings(config, interval, rules, policy)
                    },
                    onRebuild = { viewModel.rebuildVectorIndex() },
                    onSimulateSearch = { config, query, allowEmbedding, threshold ->
                        viewModel.openSearchSimulationDialog(config, query, allowEmbedding, threshold)
                    },
                    onStartHistoryRebuild = viewModel::startMemoryHistoryRebuild,
                    onCancelHistoryRebuild = viewModel::cancelMemoryHistoryRebuild,
                    onRefresh = { viewModel.refreshMemorySettingsDetails(reloadSettings = !uiState.memorySettingsDetails.loaded) },
                )
            }

            if (uiState.isSearchSimulationDialogVisible) {
                MemorySearchSimulationDialog(
                    query = uiState.searchSimulationQuery,
                    isRunning = uiState.isSearchSimulationRunning,
                    result = uiState.searchSimulationResult,
                    error = uiState.searchSimulationError,
                    onQueryChange = { viewModel.onSearchSimulationQueryChange(it) },
                    onRun = { viewModel.runSearchSimulation() },
                    onDismiss = { viewModel.showSearchSimulationDialog(false) }
                )
            }

            if (uiState.isDocumentViewOpen && uiState.selectedMemory != null) {
                var memoryTitle by remember(uiState.selectedMemory?.uuid) { mutableStateOf(uiState.selectedMemory!!.title) }
                val chunkStates = remember(uiState.selectedMemory?.uuid) {
                    mutableStateMapOf<Long, String>().apply {
                        uiState.selectedDocumentChunks.forEach { put(it.id, it.content) }
                    }
                }
                // 当chunks列表变化时，同步状态
                LaunchedEffect(uiState.selectedDocumentChunks) {
                    chunkStates.clear()
                    uiState.selectedDocumentChunks.forEach { chunk ->
                        chunkStates[chunk.id] = chunk.content
                    }
                }

                DocumentViewDialog(
                    memoryTitle = memoryTitle,
                    onTitleChange = { memoryTitle = it },
                    chunks = uiState.selectedDocumentChunks,
                    chunkStates = chunkStates,
                    onChunkChange = { id, content -> chunkStates[id] = content },
                    searchQuery = uiState.documentSearchQuery,
                    onSearchQueryChange = { viewModel.onDocumentSearchQueryChange(it) },
                    onPerformSearch = { viewModel.performSearchInDocument() },
                    onDismiss = { viewModel.closeDocumentView() },
                    onSave = {
                        // 保存标题
                        if (memoryTitle != uiState.selectedMemory!!.title) {
                            viewModel.updateMemory(
                                memory = uiState.selectedMemory!!,
                                newTitle = memoryTitle,
                                newContent = uiState.selectedMemory!!.content,
                                newContentType = uiState.selectedMemory!!.contentType,
                                newSource = uiState.selectedMemory!!.source,
                                newCredibility = uiState.selectedMemory!!.credibility,
                                newImportance = uiState.selectedMemory!!.importance,
                                newFolderPath = uiState.selectedMemory!!.folderPath ?: "",
                                newTags = uiState.selectedMemory!!.tags.map { it.name }
                            )
                        }
                        // 保存有变动的chunks
                        chunkStates.forEach { (id, content) ->
                            val originalContent =
                                uiState.selectedDocumentChunks.find { it.id == id }?.content
                            if (content != originalContent) {
                                viewModel.updateChunkContent(id, content)
                            }
                        }
                        viewModel.closeDocumentView()
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) },
                    folderPath = uiState.selectedMemory?.folderPath ?: ""
                )
            }

            if (!uiState.isDocumentViewOpen) uiState.selectedMemory?.let { memory ->
                MemoryDetails(memory, uiState.graph, relatedId != null, viewModel::dismissMemoryDetails, { viewModel.startEditing(memory) }, { viewModel.deleteMemory(memory.id) }, { focus(memory.uuid) }, { locate(memory) }, { id -> nodeById[id]?.let { if (relatedId != null) relatedId = id; viewModel.selectNode(it) } }, { viewModel.openDocument(memory) })
            }
            uiState.selectedEdge?.let { edge -> MemoryEdgeDetails(edge, uiState.graph, viewModel::dismissMemoryDetails, { viewModel.startEditingEdge(edge) }, { viewModel.deleteEdge(edge.id) }, { nodeById[edge.targetId]?.let { viewModel.selectNode(it) } }) }
            if (uiState.isEditing) MemoryEditor(uiState.editingMemory, uiState.folderPaths, uiState.selectedFolderPath, uiState.isLoading, uiState.error, viewModel::cancelEditing) { memory, title, content, contentType, source, credibility, importance, folder, tags ->
                relatedId = null; expanded = true
                if (memory == null) viewModel.createMemory(title, content, contentType, source, credibility, importance, folder, tags)
                else viewModel.updateMemory(memory, title, content, contentType, source, credibility, importance, folder, tags)
            }
            if (uiState.isEditingEdge) uiState.editingEdge?.let { edge -> MemoryEdgeEditor(edge, uiState.graph, uiState.isLoading, uiState.error, viewModel::cancelEditingEdge) { type, weight, description -> viewModel.updateEdge(edge, type, weight, description) } }
            if (uiState.isLinkingMode) MemoryLinkEditor(uiState.graph, linkInitialIds, uiState.isLoading, uiState.error, { viewModel.toggleLinkingMode(false) }, viewModel::linkMemories)
            if (uiState.showBatchDeleteConfirm) BatchDeleteConfirmDialog(uiState.boxSelectedNodeIds.size, viewModel::dismissBatchDeleteConfirm, viewModel::deleteSelectedNodes)
            if (showFolderNavigator) ModalBottomSheet(onDismissRequest = { showFolderNavigator = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                FolderNavigator(uiState.folderPaths, uiState.selectedFolderPath, { relatedId = null; viewModel.selectFolder(it); showFolderNavigator = false }, viewModel::renameFolder, viewModel::deleteFolder, viewModel::createFolder, viewModel::refreshFolderList, { showFolderNavigator = false }, Modifier.fillMaxWidth().heightIn(max = 560.dp))
            }
        }
    }
    if (spaceSheet) MemoryChoiceSheet(stringResource(R.string.memory_workbench_choose_space), spaces.map { MemoryChoice(it.id, it.name) }, spaceId, { spaceSheet = false }, { id -> spaceSheet = false; onSwitchSpace(id) })
    if (folderSheet || moving) {
        val choices = (if (moving) listOf(MemoryChoice("", uncategorized)) else listOf(MemoryChoice("", stringResource(R.string.memory_workbench_all_folders)))) + uiState.folderPaths.map { path -> MemoryChoice(path, path, stringResource(R.string.memory_workbench_count_memories, uiState.graph.nodes.count { memoryMatchesFolder(it, path, uncategorized) })) }
        MemoryChoiceSheet(stringResource(if (moving) R.string.memory_workbench_move_selected else R.string.memory_folder), choices.distinctBy { it.id }, uiState.selectedFolderPath, { folderSheet = false; moving = false }, { path ->
            if (moving) viewModel.moveSelectedMemoriesToFolder(if (path == uncategorized) "" else path) else { relatedId = null; viewModel.selectFolder(path) }
            folderSheet = false; moving = false
        })
    }
    if (displaySheet) ModalBottomSheet(onDismissRequest = { displaySheet = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.memory_workbench_display), style = MaterialTheme.typography.titleLarge)
            DisplaySwitch(stringResource(R.string.memory_workbench_cross), stringResource(R.string.memory_workbench_cross_hint), showCross) { showCross = it }
            DisplaySwitch(stringResource(R.string.memory_workbench_labels), stringResource(R.string.memory_workbench_labels_hint), allLabels) { allLabels = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { relatedId = null; expanded = true; listView = false; viewModel.clearSelection(); viewModel.selectFolder(""); displaySheet = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.memory_workbench_expand_all)) }
                Button(onClick = { displaySheet = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.done)) }
            }
        }
    }
    if (searchSheet) ModalBottomSheet(onDismissRequest = { searchSheet = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.memory_workbench_search_scope), style = MaterialTheme.typography.titleLarge)
            DisplaySwitch(stringResource(R.string.memory_workbench_search_body), stringResource(R.string.memory_workbench_search_body_hint), searchBody) { searchBody = it }
            TextButton(onClick = { searchSheet = false; viewModel.showSearchSettingsDialog(true) }) { Text(stringResource(R.string.memory_settings_title)) }
        }
    }
}

@Composable
private fun DisplaySwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange)
    }
    HorizontalDivider()
}

@Composable
private fun MemoryListRow(memory: Memory, links: Int, checked: Boolean, selecting: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(if (memory.isDocumentNode) Icons.Default.Description else Icons.Default.Share, null, Modifier.padding(10.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(memory.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(memory.content, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((memory.folderPath ?: stringResource(R.string.memory_uncategorized)) + " · " + stringResource(R.string.memory_workbench_count_links, links) + " · " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(memory.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (selecting) { if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank } else Icons.Default.ChevronRight, null, Modifier.size(18.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
}
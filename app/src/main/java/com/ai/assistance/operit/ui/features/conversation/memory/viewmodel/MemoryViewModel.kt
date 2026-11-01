package com.ai.assistance.operit.ui.features.conversation.memory.viewmodel

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.embedding.isEmbeddingReady
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.EmbeddingRebuildProgress
import com.ai.assistance.operit.data.model.MemorySearchConfig
import com.ai.assistance.operit.data.model.MemorySearchDebugInfo
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Node
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildManager
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildTimeScope
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemorySettingsDetails
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemorySettingsDataSource
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemorySettingsPolicy
import kotlinx.coroutines.CancellationException
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Memory UI State Represents the current state of the Memory screen. */
data class MemoryUiState(
        val memories: List<Memory> = emptyList(),
        val searchResultIds: Set<String>? = null,
        val isSearching: Boolean = false,
        val graph: Graph = Graph(emptyList(), emptyList()),
        val selectedMemory: Memory? = null,
        val selectedNodeId: String? = null,
        val isLoading: Boolean = false,
        val searchQuery: String = "",
        val error: String? = null,
        val editingMemory: Memory? = null, // 新增：用于编辑/新建
        val isEditing: Boolean = false, // 新增：是否处于编辑/新建状态
        val isLinkingMode: Boolean = false, // 是否处于连接模式
        val linkingNodeIds: List<String> = emptyList(), // 已选择的连接节点
        val selectedEdge: Edge? = null,
        val editingEdge: Edge? = null,
        val isEditingEdge: Boolean = false,
        val isBoxSelectionMode: Boolean = false, // 新增：是否处于框选模式
        val boxSelectedNodeIds: Set<String> = emptySet(), // 新增：框选中的节点ID
        val showBatchDeleteConfirm: Boolean = false, // 新增：是否显示批量删除确认对话框

        // --- 新增：文档相关状态 ---
        val selectedDocumentChunks: List<DocumentChunk> = emptyList(),
        val documentSearchQuery: String = "",
        val isDocumentViewOpen: Boolean = false,

        // --- 新增：工具测试相关状态 ---
        val isToolTestDialogVisible: Boolean = false,
        val toolTestResult: String = "",
        val isToolTestLoading: Boolean = false,

        // --- 新增：文件夹相关状态 ---
        val folderPaths: List<String> = emptyList(), // 所有文件夹路径
        val selectedFolderPath: String = "", // 当前选中的文件夹路径，空字符串表示显示全部

        // --- 新增：搜索设置 ---
        val isSearchSettingsDialogVisible: Boolean = false,
        val isSearchSettingsSaving: Boolean = false,
        val memorySettingsDetails: MemorySettingsDetails = MemorySettingsDetails(),
        val searchConfig: MemorySearchConfig = MemorySearchConfig(),
        val autoSaveIntervalMinutes: Int = MemorySearchSettingsPreferences.DEFAULT_AUTO_SAVE_INTERVAL_MINUTES,
        val memoryExtractionCustomRules: String =
            MemorySearchSettingsPreferences.DEFAULT_MEMORY_EXTRACTION_CUSTOM_RULES,
        val memoryEmbeddingModelName: String? = null,
        val embeddingDimensionUsage: EmbeddingDimensionUsage = EmbeddingDimensionUsage(),
        val isEmbeddingRebuildRunning: Boolean = false,
        val embeddingRebuildProgress: EmbeddingRebuildProgress = EmbeddingRebuildProgress(),

        // --- 搜索模拟 ---
        val isSearchSimulationDialogVisible: Boolean = false,
        val searchSimulationQuery: String = "",
        val searchSimulationConfig: MemorySearchConfig = MemorySearchConfig(),
        val searchSimulationAllowCloudEmbedding: Boolean = true,
        val searchSimulationThreshold: Double = MemoryRepository.DEFAULT_RELEVANCE_THRESHOLD,
        val isSearchSimulationRunning: Boolean = false,
        val searchSimulationResult: MemorySearchDebugInfo? = null,
        val searchSimulationError: String? = null,
        val messageKind: com.ai.assistance.operit.ui.common.oktoast.OKToast.Kind = com.ai.assistance.operit.ui.common.oktoast.OKToast.Kind.Info,
        val message: String? = null
)

/**
 * ViewModel for the Memory/Memory Library screen. It handles the business logic for interacting
 * with the MemoryRepository.
 */
class MemoryViewModel(
    private val repository: MemoryRepository,
    private val context: Context,
    private val profileId: String
) : ViewModel() {

    companion object {
        private const val TAG = "MemoryViewModel"
    }

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()
    private val searchSettingsPreferences = MemorySearchSettingsPreferences(context, profileId)
    private val memorySettingsDataSource = MemorySettingsDataSource(context, profileId)
    private val historyRebuildManager = ChatMemoryRebuildManager.getInstance(context)
    val memoryHistoryRebuildProgress = historyRebuildManager.progress
    private var settingsLoadJob: Job? = null
    private var simulationJob: Job? = null
    private var memorySearchJob: Job? = null

    init {
        loadSearchSettings()
        loadMemoryEmbeddingSettings()
        refreshEmbeddingDimensionUsage()
        // Initially load the graph
        loadMemoryGraph()
        loadFolderPaths()
        viewModelScope.launch {
            historyRebuildManager.progress.collect { progress ->
                if (progress.memorySpaceId == profileId && progress.status in setOf(
                    ChatMemoryRebuildManager.Status.COMPLETED,
                    ChatMemoryRebuildManager.Status.CANCELLED,
                )) {
                    loadMemoryGraph()
                    loadFolderPaths()
                    refreshEmbeddingDimensionUsage()
                    if (_uiState.value.isSearchSettingsDialogVisible) refreshMemorySettingsDetails()
                }
            }
        }
    }

    private suspend fun refreshGraph(): Graph {
        val memories = repository.getAllMemories()
        val graph = repository.getMemoryGraph()
        _uiState.update { it.copy(memories = memories) }
        return graph
    }
    /** Loads the entire memory graph from the repository. */
    fun loadMemoryGraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val graphData = refreshGraph()
                _uiState.update { it.copy(graph = graphData, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_load_graph, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /**
     * Searches memories and updates the graph with the results. If the query is empty, it reloads
     * the full graph.
     */
    fun searchMemories(includeContent: Boolean = true) {
        memorySearchJob?.cancel()
        val snapshot = _uiState.value
        if (snapshot.searchQuery.isBlank()) {
            _uiState.update { it.copy(searchResultIds = null, isSearching = false) }
            return
        }
        memorySearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val config = snapshot.searchConfig
                val results = repository.searchMemories(
                    query = snapshot.searchQuery,
                    scoreMode = config.scoreMode,
                    keywordWeight = config.keywordWeight,
                    tagWeight = config.tagWeight,
                    semanticWeight = config.vectorWeight,
                    edgeWeight = config.edgeWeight,
                )
                val ids = results.filter { includeContent || it.title.contains(snapshot.searchQuery.trim(), ignoreCase = true) }.map { it.uuid }.toSet()
                if (_uiState.value.searchQuery == snapshot.searchQuery) _uiState.update { it.copy(searchResultIds = ids, isSearching = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { it.copy(isSearching = false, error = context.getString(R.string.memory_error_search, failure.message.orEmpty())) }
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        memorySearchJob?.cancel()
        _uiState.update { it.copy(searchQuery = newQuery, searchResultIds = if (newQuery.isBlank()) null else emptySet(), isSearching = false) }
    }
    fun showSearchSettingsDialog(visible: Boolean) {
        if (_uiState.value.isSearchSettingsSaving) return
        _uiState.update { it.copy(
            isSearchSettingsDialogVisible = visible,
            error = null,
            memorySettingsDetails = if (visible) MemorySettingsDetails() else it.memorySettingsDetails,
        ) }
        if (visible) refreshMemorySettingsDetails(reloadSettings = true)
    }

    fun openSearchSimulationDialog(config: MemorySearchConfig, query: String, allowCloudEmbedding: Boolean, relevanceThreshold: Double) {
        _uiState.update {
            it.copy(
                isSearchSimulationDialogVisible = true,
                searchSimulationQuery = query,
                searchSimulationConfig = config.normalized(),
                searchSimulationAllowCloudEmbedding = allowCloudEmbedding,
                searchSimulationThreshold = relevanceThreshold.coerceIn(0.0, 1.0),
                searchSimulationResult = null,
                searchSimulationError = null
            )
        }
    }

    fun showSearchSimulationDialog(visible: Boolean) {
        if (!visible) simulationJob?.cancel()
        _uiState.update {
            it.copy(
                isSearchSimulationDialogVisible = visible,
                searchSimulationResult = if (visible) it.searchSimulationResult else null,
                searchSimulationError = if (visible) it.searchSimulationError else null,
                isSearchSimulationRunning = if (visible) it.isSearchSimulationRunning else false
            )
        }
    }

    fun onSearchSimulationQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchSimulationQuery = newQuery, searchSimulationResult = null, searchSimulationError = null) }
    }

    fun runSearchSimulation() {
        if (simulationJob?.isActive == true) return
        simulationJob = viewModelScope.launch {
            val currentState = _uiState.value
            val query = currentState.searchSimulationQuery
            val config = currentState.searchSimulationConfig
            val folderPath = currentState.selectedFolderPath.takeIf { it.isNotBlank() }

            _uiState.update {
                it.copy(
                    isSearchSimulationRunning = true,
                    searchSimulationError = null
                )
            }

            try {
                val debugInfo = repository.searchMemoriesDebug(
                    query = query,
                    folderPath = folderPath,
                    scoreMode = config.scoreMode,
                    keywordWeight = config.keywordWeight,
                    tagWeight = config.tagWeight,
                    semanticWeight = config.vectorWeight,
                    edgeWeight = config.edgeWeight,
                    allowCloudEmbedding = currentState.searchSimulationAllowCloudEmbedding,
                    relevanceThreshold = currentState.searchSimulationThreshold,
                )
                _uiState.update {
                    it.copy(
                        isSearchSimulationRunning = false,
                        searchSimulationResult = debugInfo,
                        searchSimulationError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearchSimulationRunning = false,
                        searchSimulationError = context.getString(
                            R.string.memory_error_search,
                            e.message ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }

    fun saveSearchSettings(
        newConfig: MemorySearchConfig,
        autoSaveIntervalMinutes: Int,
        memoryExtractionCustomRules: String,
        policy: MemorySettingsPolicy,
    ) {
        if (_uiState.value.isSearchSettingsSaving) return
        val normalizedSearchConfig = newConfig.normalized()
        val normalizedInterval = autoSaveIntervalMinutes.coerceIn(
            MemorySearchSettingsPreferences.MIN_AUTO_SAVE_INTERVAL_MINUTES,
            MemorySearchSettingsPreferences.MAX_AUTO_SAVE_INTERVAL_MINUTES,
        )
        _uiState.update { it.copy(isSearchSettingsSaving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                memorySettingsDataSource.savePolicy(policy)
                searchSettingsPreferences.save(normalizedSearchConfig)
                searchSettingsPreferences.saveAutoSaveIntervalMinutes(normalizedInterval)
                searchSettingsPreferences.saveMemoryExtractionCustomRules(memoryExtractionCustomRules)
                _uiState.update { it.copy(
                    searchConfig = normalizedSearchConfig,
                    autoSaveIntervalMinutes = normalizedInterval,
                    memoryExtractionCustomRules = memoryExtractionCustomRules,
                    memorySettingsDetails = it.memorySettingsDetails.copy(policy = policy),
                    isSearchSettingsDialogVisible = false,
                    message = context.getString(R.string.settings_saved),
                    messageKind = com.ai.assistance.operit.ui.common.oktoast.OKToast.Kind.Success,
                ) }
                searchMemories()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                AppLogger.e(TAG, "保存记忆设置失败", failure)
                _uiState.update { it.copy(error = failure.message ?: failure.javaClass.simpleName) }
            } finally {
                _uiState.update { it.copy(isSearchSettingsSaving = false) }
            }
        }
    }

    fun refreshMemorySettingsDetails(reloadSettings: Boolean = false) {
        if (settingsLoadJob?.isActive == true) return
        _uiState.update { it.copy(memorySettingsDetails = it.memorySettingsDetails.copy(loading = true, error = null)) }
        settingsLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val details = memorySettingsDataSource.load()
                val config = repository.loadMemoryEmbeddingModel()
                val usage = repository.getEmbeddingDimensionUsage()
                val searchConfig = if (reloadSettings) searchSettingsPreferences.load() else _uiState.value.searchConfig
                val interval = if (reloadSettings) searchSettingsPreferences.loadAutoSaveIntervalMinutes() else _uiState.value.autoSaveIntervalMinutes
                val rules = if (reloadSettings) searchSettingsPreferences.loadMemoryExtractionCustomRules() else _uiState.value.memoryExtractionCustomRules
                _uiState.update { it.copy(
                    memorySettingsDetails = details,
                    memoryEmbeddingModelName = if (config.isEmbeddingReady()) config?.modelName else null,
                    embeddingDimensionUsage = usage,
                    searchConfig = searchConfig,
                    autoSaveIntervalMinutes = interval,
                    memoryExtractionCustomRules = rules,
                    error = null,
                ) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                AppLogger.e(TAG, "读取记忆设置状态失败", failure)
                _uiState.update { it.copy(memorySettingsDetails = it.memorySettingsDetails.copy(
                    loading = false,
                    error = failure.message ?: failure.javaClass.simpleName,
                )) }
            }
        }
    }

    fun startMemoryHistoryRebuild(chatIds: List<String>, windowMessageCount: Int, timeScope: ChatMemoryRebuildTimeScope) {
        try {
            val allowedIds = _uiState.value.memorySettingsDetails.chats.map { it.id }.toSet()
            require(chatIds.isNotEmpty() && chatIds.all { it in allowedIds }) { "选择了当前记忆空间以外的聊天" }
            historyRebuildManager.startForMemorySpace(profileId, chatIds, windowMessageCount, timeScope)
        } catch (failure: Exception) {
            AppLogger.e(TAG, "启动历史记忆重建失败", failure)
            _uiState.update { it.copy(error = failure.message ?: failure.javaClass.simpleName) }
        }
    }

    fun cancelMemoryHistoryRebuild() {
        if (historyRebuildManager.progress.value.memorySpaceId == profileId) historyRebuildManager.cancel()
    }

    fun resetSearchSettings() {
        val defaults = MemorySearchConfig()
        _uiState.update { it.copy(searchConfig = defaults) }
    }

    private fun loadSearchSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = searchSettingsPreferences.load()
            val autoSaveIntervalMinutes = searchSettingsPreferences.loadAutoSaveIntervalMinutes()
            val memoryExtractionCustomRules =
                searchSettingsPreferences.loadMemoryExtractionCustomRules()
            _uiState.update {
                it.copy(
                    searchConfig = config,
                    autoSaveIntervalMinutes = autoSaveIntervalMinutes,
                    memoryExtractionCustomRules = memoryExtractionCustomRules
                )
            }
        }
    }

    private fun loadMemoryEmbeddingSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = repository.loadMemoryEmbeddingModel()
                _uiState.update { it.copy(memoryEmbeddingModelName = if (config.isEmbeddingReady()) config?.modelName else null) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                com.ai.assistance.operit.util.logging.AppLogger.e("MemoryEmbedding", "读取记忆向量功能绑定失败", error)
                _uiState.update { it.copy(memoryEmbeddingModelName = null, error = error.message) }
            }
        }
    }

    fun refreshEmbeddingDimensionUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val usage = repository.getEmbeddingDimensionUsage()
            _uiState.update { it.copy(embeddingDimensionUsage = usage) }
        }
    }

    fun rebuildVectorIndex() {
        if (_uiState.value.isEmbeddingRebuildRunning) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isEmbeddingRebuildRunning = true,
                    error = null,
                    message = null,
                    embeddingRebuildProgress = EmbeddingRebuildProgress(
                        total = 0,
                        processed = 0,
                        failed = 0,
                        currentStage = "preparing"
                    )
                )
            }

            try {
                val finalProgress = repository.rebuildVectorIndices { progress ->
                    _uiState.update { state ->
                        state.copy(embeddingRebuildProgress = progress)
                    }
                }
                val usage = repository.getEmbeddingDimensionUsage()
                _uiState.update {
                    it.copy(
                        isEmbeddingRebuildRunning = false,
                        embeddingDimensionUsage = usage,
                        embeddingRebuildProgress = finalProgress,
                        messageKind = if (finalProgress.total > 0) com.ai.assistance.operit.ui.common.oktoast.OKToast.Kind.Success else com.ai.assistance.operit.ui.common.oktoast.OKToast.Kind.Info,
                        message = if (finalProgress.total > 0) {
                            context.getString(
                                R.string.memory_embedding_rebuild_completed,
                                finalProgress.processed
                            )
                        } else {
                            context.getString(R.string.memory_embedding_rebuild_empty)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isEmbeddingRebuildRunning = false,
                        error = context.getString(
                            R.string.memory_embedding_rebuild_failed,
                            e.message ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // --- 文件夹相关方法 ---

    /** 加载所有文件夹路径列表 */
    fun loadFolderPaths() {
        viewModelScope.launch {
            try {
                val folders = repository.getAllFolderPaths()
                com.ai.assistance.operit.util.logging.AppLogger.d("MemoryViewModel", "Loaded ${folders.size} folders: $folders")
                _uiState.update { it.copy(folderPaths = folders) }
                // 保持空字符串选中态表示“全部”，避免刷新目录后自动跳转到具体文件夹。
            } catch (e: Exception) {
                _uiState.update { it.copy(error = context.getString(R.string.memory_error_load_folders, e.message ?: "Unknown error")) }
            }
        }
    }

    /** 手动刷新文件夹列表 */
    fun refreshFolderList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val folders = repository.getAllFolderPaths()
                _uiState.update { it.copy(folderPaths = folders, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = context.getString(R.string.memory_error_refresh_folders, e.message ?: "Unknown error"), isLoading = false) }
            }
        }
    }

    /** 选择文件夹并加载该文件夹的图谱 */
    fun selectFolder(folderPath: String, clearSelection: Boolean = true) {
        memorySearchJob?.cancel()
        _uiState.update { it.copy(selectedFolderPath = folderPath, selectedNodeId = if (clearSelection) null else it.selectedNodeId, selectedMemory = null, selectedEdge = null, searchQuery = "", searchResultIds = null, isSearching = false, boxSelectedNodeIds = emptySet()) }
    }
    /** 移动选中的记忆到目标文件夹 */
    fun moveSelectedMemoriesToFolder(targetFolderPath: String) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.boxSelectedNodeIds
            if (selectedIds.isEmpty()) return@launch
            
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 将UUID转换为Memory ID
                val memoryIds = selectedIds.mapNotNull { uuid ->
                    repository.findMemoryByUuid(uuid)?.id
                }
                
                val success = repository.moveMemoriesToFolder(memoryIds, targetFolderPath)
                if (success) {
                    loadFolderPaths()
                    val graphData = refreshGraph()
                    _uiState.update {
                        it.copy(
                            graph = graphData,
                            isLoading = false,
                            boxSelectedNodeIds = emptySet(),
                            isBoxSelectionMode = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.memory_error_move_memories)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.memory_error_move_memories_detail, e.message ?: "Unknown error")) }
            }
        }
    }

    /** Selects a memory to view its details. */
    fun selectMemory(memory: Memory) {
        _uiState.update { it.copy(selectedMemory = memory, selectedNodeId = memory.uuid, selectedEdge = null, isDocumentViewOpen = false) }
    }

    fun selectNode(node: Node) {
        val snapshot = _uiState.value
        if (snapshot.isLinkingMode) {
            _uiState.update { it.copy(linkingNodeIds = (it.linkingNodeIds + node.id).distinct().take(2)) }
        } else if (snapshot.isBoxSelectionMode) {
            _uiState.update { it.copy(boxSelectedNodeIds = if (node.id in it.boxSelectedNodeIds) it.boxSelectedNodeIds - node.id else it.boxSelectedNodeIds + node.id) }
        } else viewModelScope.launch {
            try {
                repository.getMemoryByUuid(node.id)?.let(::selectMemory)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { it.copy(error = context.getString(R.string.memory_workbench_load_error, failure.message.orEmpty())) }
            }
        }
    }

    /** 新进入或切换查看空间时，清理上次停留的弹窗与临时选择。 */
    fun resetLibraryView() {
        memorySearchJob?.cancel()
        _uiState.update { it.copy(selectedFolderPath = "", selectedMemory = null, selectedNodeId = null, selectedEdge = null, isEditing = false, editingMemory = null, isEditingEdge = false, editingEdge = null, isLinkingMode = false, linkingNodeIds = emptyList(), isBoxSelectionMode = false, boxSelectedNodeIds = emptySet(), showBatchDeleteConfirm = false, searchQuery = "", searchResultIds = null, isSearching = false, isDocumentViewOpen = false, selectedDocumentChunks = emptyList(), error = null) }
    }

    fun dismissMemoryDetails() {
        _uiState.update { it.copy(selectedMemory = null, selectedEdge = null) }
    }

    /** 完整详情和原有文档分块编辑分别保留入口。 */
    fun openDocument(memory: Memory) {
        viewModelScope.launch {
            try {
                val query = _uiState.value.searchQuery
                val chunks = if (query.isBlank()) repository.getChunksForMemory(memory.id) else repository.searchChunksInDocument(memory.id, query)
                _uiState.update { it.copy(selectedMemory = memory, selectedNodeId = memory.uuid, isDocumentViewOpen = true, selectedDocumentChunks = chunks, documentSearchQuery = query) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { it.copy(error = context.getString(R.string.memory_workbench_load_error, failure.message.orEmpty())) }
            }
        }
    }

    fun locateMemory(memory: Memory) {
        memorySearchJob?.cancel()
        _uiState.update { it.copy(selectedMemory = null, selectedNodeId = memory.uuid, selectedEdge = null, selectedFolderPath = memory.folderPath.orEmpty(), searchQuery = "", searchResultIds = null, isBoxSelectionMode = false, boxSelectedNodeIds = emptySet()) }
    }
    /** Selects an edge in the graph. */
    fun selectEdge(edge: Edge) {
        _uiState.update { it.copy(selectedEdge = edge, selectedMemory = null) }
    }

    /** Clears any selection (node or edge). */
    fun clearSelection() {
        _uiState.update { it.copy(selectedMemory = null, selectedNodeId = null, selectedEdge = null) }
    }

    /** 关闭文档视图 */
    fun closeDocumentView() {
        _uiState.update { it.copy(isDocumentViewOpen = false, documentSearchQuery = "", selectedDocumentChunks = emptyList(), selectedMemory = null, selectedNodeId = null) }
    }

    /** 更新文档内搜索的查询词 */
    fun onDocumentSearchQueryChange(query: String) {
        _uiState.update { it.copy(documentSearchQuery = query) }
    }

    /** 在选定文档中执行搜索 */
    fun performSearchInDocument() {
        val query = _uiState.value.documentSearchQuery
        // 如果查询为空，则显示所有块
        if (query.isBlank()) {
            val memoryId = _uiState.value.selectedMemory?.id ?: return
            viewModelScope.launch {
                val chunks = repository.getChunksForMemory(memoryId)
                _uiState.update { it.copy(selectedDocumentChunks = chunks) }
            }
            return
        }

        val memoryId = _uiState.value.selectedMemory?.id ?: return
        viewModelScope.launch {
            val chunks = repository.searchChunksInDocument(memoryId, query)
            _uiState.update { it.copy(selectedDocumentChunks = chunks) }
        }
    }

    /**
     * 显示或隐藏工具测试对话框
     */
    fun showToolTestDialog(visible: Boolean) {
        _uiState.update { it.copy(isToolTestDialogVisible = visible, toolTestResult = "") } // 打开时清空上次结果
    }

    /**
     * 执行记忆查询工具的测试
     */
    fun testQueryTool(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isToolTestLoading = true, toolTestResult = "") }
            try {
                val aiToolHandler = AIToolHandler.getInstance(context)
                // 确保工具已注册
                if (aiToolHandler.getToolExecutor("query_memory") == null) {
                    aiToolHandler.registerDefaultTools()
                }

                val tool = AITool(
                    name = "query_memory",
                    parameters = listOf(ToolParameter("query", query))
                )

                val result = aiToolHandler.executeTool(tool)

                val resultString = if (result.success) {
                    // 使用Gson进行格式化输出，更美观
                    Gson().newBuilder().setPrettyPrinting().create().toJson(result.result)
                } else {
                    "Error: ${result.error}"
                }
                _uiState.update { it.copy(isToolTestLoading = false, toolTestResult = resultString) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isToolTestLoading = false, toolTestResult = "An unexpected error occurred: ${e.message}") }
            }
        }
    }

    /**
     * 更新文档区块的内容。
     * @param chunkId 要更新的区块ID。
     * @param newContent 新内容。
     */
    fun updateChunkContent(chunkId: Long, newContent: String) {
        viewModelScope.launch {
            repository.updateChunk(chunkId, newContent)
            // 可选：更新后刷新当前文档的区块列表
            val memoryId = _uiState.value.selectedMemory?.id ?: return@launch
            val chunks = repository.getChunksForMemory(memoryId)
            _uiState.update { it.copy(selectedDocumentChunks = chunks) }
        }
    }

    /** 从外部文件导入记忆 */
    fun importDocument(title: String, filePath: String, fileContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val currentFolder = _uiState.value.selectedFolderPath
                val imported = repository.createMemoryFromDocument(title, filePath, fileContent, currentFolder)
                // 刷新图谱和文件夹列表
                val updatedGraph = refreshGraph()
                loadFolderPaths()
                _uiState.update { it.copy(graph = updatedGraph, isLoading = false, selectedNodeId = imported.uuid, searchQuery = "", searchResultIds = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_import_document, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /** 新建记忆 */
    fun createMemory(title: String, content: String, contentType: String = "text/plain", source: String = "user_input", credibility: Float = .8f, importance: Float = .5f, folderPath: String = _uiState.value.selectedFolderPath, tags: List<String> = emptyList()) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val created = checkNotNull(repository.createMemory(title, content, contentType, source, folderPath, tags, credibility, importance))
                val updatedGraph = refreshGraph()
                loadFolderPaths()
                _uiState.update { it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph, selectedNodeId = created.uuid, selectedFolderPath = created.folderPath.orEmpty(), searchQuery = "", searchResultIds = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.memory_error_create_memory, failure.message.orEmpty())) }
            }
        }
    }
    /** 编辑记忆 */
    fun updateMemory(
        memory: Memory,
        newTitle: String,
        newContent: String,
        newContentType: String,
        newSource: String,
        newCredibility: Float,
        newImportance: Float,
        newFolderPath: String,
        newTags: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 文档节点的内容不允许自由编辑，保持固定格式
                val contentToUpdate = if (memory.isDocumentNode) memory.content else newContent
                repository.updateMemory(
                    memory = memory,
                    newTitle = newTitle,
                    newContent = contentToUpdate,
                    newContentType = newContentType,
                    newSource = newSource,
                    newCredibility = newCredibility,
                    newImportance = newImportance,
                    newFolderPath = newFolderPath.ifBlank { null }, // 空字符串视为未分类
                    newTags = newTags
                )
                val updatedGraph = refreshGraph()
                // 刷新文件夹列表（如果记忆移动到新文件夹或从文件夹移出）
                loadFolderPaths()
                _uiState.update {
                    it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph, isDocumentViewOpen = false, selectedMemory = null, selectedNodeId = memory.uuid, selectedFolderPath = memory.folderPath.orEmpty(), searchQuery = "", searchResultIds = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_update_memory, e.message ?: "Unknown error"))
                }
            }
        }
    }
    /** 删除记忆 */
    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteMemory(memoryId)
                val updatedGraph = refreshGraph()
                // 刷新文件夹列表（删除记忆可能导致文件夹变空）
                loadFolderPaths()
                _uiState.update {
                    it.copy(isLoading = false, selectedMemory = null, selectedNodeId = null, graph = updatedGraph, isDocumentViewOpen = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_delete_memory, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /** 显示批量删除确认对话框 */
    fun showBatchDeleteConfirm() {
        val selectedIds = _uiState.value.boxSelectedNodeIds
        if (selectedIds.isEmpty()) {
            com.ai.assistance.operit.util.logging.AppLogger.d("MemoryViewModel", "No nodes selected, aborting delete.")
            return
        }
        _uiState.update { it.copy(showBatchDeleteConfirm = true) }
    }

    /** 隐藏批量删除确认对话框 */
    fun dismissBatchDeleteConfirm() {
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
    }

    /** 批量删除框选中的记忆（确认后执行） */
    fun deleteSelectedNodes() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.boxSelectedNodeIds
            com.ai.assistance.operit.util.logging.AppLogger.d("MemoryViewModel", "deleteSelectedNodes called with ${selectedIds.size} nodes.")
            if (selectedIds.isEmpty()) {
                com.ai.assistance.operit.util.logging.AppLogger.d("MemoryViewModel", "No nodes selected, aborting delete.")
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, showBatchDeleteConfirm = false) }
            try {
                com.ai.assistance.operit.util.logging.AppLogger.d("MemoryViewModel", "Calling repository.deleteMemoriesByUuids with IDs: $selectedIds")
                repository.deleteMemoriesByUuids(selectedIds)
                val updatedGraph = refreshGraph()
                com.ai.assistance.operit.util.logging.AppLogger.d("MemoryViewModel", "Graph refreshed after deletion.")
                // 刷新文件夹列表（批量删除可能导致文件夹变空）
                loadFolderPaths()
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            graph = updatedGraph,
                            isBoxSelectionMode = false,
                            boxSelectedNodeIds = emptySet()
                    )
                }
            } catch (e: Exception) {
                com.ai.assistance.operit.util.logging.AppLogger.e("MemoryViewModel", "Failed to delete selected memories", e)
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_delete_selected, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /** 将框选的节点添加到已选择集合中 */
    fun addNodesToSelection(nodeIds: Set<String>) {
        _uiState.update {
            it.copy(boxSelectedNodeIds = it.boxSelectedNodeIds + nodeIds)
        }
    }

    /** 进入新建/编辑状态 */
    fun startEditing(memory: Memory? = null) {
        _uiState.update { it.copy(isEditing = true, editingMemory = memory, selectedMemory = null, error = null) }
    }
    /** 取消编辑 */
    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingMemory = null, error = null) }
    }

    /** 进入/退出边的编辑状态 */
    fun startEditingEdge(edge: Edge) {
        _uiState.update { it.copy(isEditingEdge = true, editingEdge = edge, selectedEdge = null, error = null) }
    }
    fun cancelEditingEdge() {
        _uiState.update { it.copy(isEditingEdge = false, editingEdge = null, error = null) }
    }

    /** 更新边的信息 */
    fun updateEdge(edge: Edge, type: String, weight: Float, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.updateLink(edge.id, type, weight, description)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedEdge = updatedGraph.edges.find { edge.id == it.id },
                        isEditingEdge = false,
                        editingEdge = null,
                        graph = updatedGraph
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.memory_error_update_link, e.message ?: "Unknown error")) }
            }
        }
    }

    /** 删除边 */
    fun deleteEdge(edgeId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteLink(edgeId)
                val updatedGraph = refreshGraph()
                _uiState.update { it.copy(isLoading = false, selectedEdge = null, graph = updatedGraph) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.memory_error_delete_link, e.message ?: "Unknown error")) }
            }
        }
    }

    /** 切换连接模式 */
    fun toggleLinkingMode(enabled: Boolean) {
        if (enabled) {
            // 进入连接模式，确保退出其他模式，并清理状态
            _uiState.update {
                it.copy(
                    isLinkingMode = true,
                    selectedMemory = null,
                    selectedEdge = null,
                    linkingNodeIds = emptyList(),
                    isBoxSelectionMode = false,
                    boxSelectedNodeIds = emptySet()
                )
            }
        } else {
            // 退出连接模式
            _uiState.update {
                it.copy(isLinkingMode = false, linkingNodeIds = emptyList(), error = null)
            }
        }
    }

    /** 切换框选模式 */
    fun toggleBoxSelectionMode(enabled: Boolean) {
        if (enabled) {
            // 进入框选模式，确保退出其他模式，并清理状态
            _uiState.update {
                it.copy(
                    isBoxSelectionMode = true,
                    selectedMemory = null,
                    selectedEdge = null,
                    selectedNodeId = null,
                    boxSelectedNodeIds = emptySet(),
                    isLinkingMode = false,
                    linkingNodeIds = emptyList()
                )
            }
        } else {
            // 退出框选模式
            _uiState.update {
                it.copy(isBoxSelectionMode = false, boxSelectedNodeIds = emptySet())
            }
        }
    }

    /** 创建两个记忆之间的连接 */
    fun linkMemories(
            sourceUuid: String,
            targetUuid: String,
            type: String = "related",
            weight: Float = 1.0f,
            description: String = ""
    ) {
        if (sourceUuid == targetUuid) {
            _uiState.update { it.copy(error = context.getString(R.string.memory_workbench_self_link)) }
            return
        }
        if (_uiState.value.graph.edges.any { it.sourceId == sourceUuid && it.targetId == targetUuid && it.label == type }) {
            _uiState.update { it.copy(error = context.getString(R.string.memory_workbench_duplicate_link)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val source = repository.findMemoryByUuid(sourceUuid)
                val target = repository.findMemoryByUuid(targetUuid)
                if (source != null && target != null) {
                    repository.linkMemories(source, target, type, weight, description)
                    val updatedGraph = refreshGraph()
                    _uiState.update { it.copy(isLoading = false, isLinkingMode = false, linkingNodeIds = emptyList(), graph = updatedGraph) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.memory_workbench_load_error, sourceUuid + " → " + targetUuid)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_link_memories, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /**
     * 创建新文件夹
     */
    fun createFolder(folderPath: String) {
        viewModelScope.launch {
            try {
                // 创建一个空的占位记忆，确保文件夹路径存在
                repository.createMemory(
                    title = ".folder_placeholder",
                    content = context.getString(R.string.memory_folder_placeholder),
                    contentType = "text",
                    folderPath = folderPath
                )
                // 重新加载文件夹列表
                val graph = refreshGraph()
                _uiState.update { it.copy(graph = graph, isLoading = false) }
                loadFolderPaths()
                // 自动选择新创建的文件夹
                selectFolder(folderPath)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = context.getString(R.string.memory_error_create_folder, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /**
     * 重命名文件夹
     */
    fun renameFolder(oldPath: String, newPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.renameFolder(oldPath, newPath)
                // 重新加载文件夹列表
                val graph = refreshGraph()
                _uiState.update { it.copy(graph = graph, isLoading = false) }
                loadFolderPaths()
                // 如果当前选中的就是被重命名的文件夹，更新选中状态
                if (_uiState.value.selectedFolderPath == oldPath) {
                    selectFolder(newPath)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_rename_folder, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /**
     * 删除文件夹
     */
    fun deleteFolder(folderPath: String) {
        viewModelScope.launch {
            AppLogger.d(TAG, "deleteFolder() 开始删除文件夹: $folderPath")
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteFolder(folderPath)
                AppLogger.d(TAG, "deleteFolder() 文件夹删除成功: $folderPath")
                // 重新加载文件夹列表
                val graph = refreshGraph()
                _uiState.update { it.copy(graph = graph, isLoading = false) }
                loadFolderPaths()
                // 如果当前选中的就是被删除的文件夹，切换到"所有记忆"
                if (_uiState.value.selectedFolderPath == folderPath) {
                    selectFolder("")
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "deleteFolder() 删除文件夹失败: $folderPath", e)
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_delete_folder, e.message ?: "Unknown error"))
                }
            }
        }
    }
}

/** Factory for creating MemoryViewModel instances with dependencies. */
class MemoryViewModelFactory(private val context: Context, private val profileId: String) :
        ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") val repository = MemoryRepository(context, profileId)
            return MemoryViewModel(repository, context.applicationContext, profileId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

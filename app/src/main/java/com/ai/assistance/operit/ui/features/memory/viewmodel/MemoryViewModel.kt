package com.ai.assistance.operit.ui.features.memory.viewmodel

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.EmbeddingRebuildProgress
import com.ai.assistance.operit.data.model.MemorySearchConfig
import com.ai.assistance.operit.data.model.MemorySearchDebugInfo
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.R
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Memory UI State Represents the current state of the Memory screen. */
data class MemoryUiState(
        val memories: List<Memory> = emptyList(), // Keep for potential list view
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
        val searchConfig: MemorySearchConfig = MemorySearchConfig(),
        val autoSaveIntervalMinutes: Int = MemorySearchSettingsPreferences.DEFAULT_AUTO_SAVE_INTERVAL_MINUTES,
        val memoryExtractionCustomRules: String =
            MemorySearchSettingsPreferences.DEFAULT_MEMORY_EXTRACTION_CUSTOM_RULES,
        val cloudEmbeddingConfig: CloudEmbeddingConfig = CloudEmbeddingConfig(),
        val embeddingDimensionUsage: EmbeddingDimensionUsage = EmbeddingDimensionUsage(),
        val isEmbeddingRebuildRunning: Boolean = false,
        val embeddingRebuildProgress: EmbeddingRebuildProgress = EmbeddingRebuildProgress(),

        // --- 搜索模拟 ---
        val isSearchSimulationDialogVisible: Boolean = false,
        val searchSimulationQuery: String = "",
        val isSearchSimulationRunning: Boolean = false,
        val searchSimulationResult: MemorySearchDebugInfo? = null,
        val searchSimulationError: String? = null,
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

    init {
        loadSearchSettings()
        loadCloudEmbeddingSettings()
        refreshEmbeddingDimensionUsage()
        // Initially load the graph
        loadMemoryGraph()
        loadFolderPaths()
    }

    private suspend fun refreshGraph(): Graph {
        val selectedFolder = _uiState.value.selectedFolderPath
        return if (selectedFolder.isEmpty()) {
            repository.getMemoryGraph()
        } else {
            repository.getGraphForFolder(selectedFolder)
        }
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
    fun searchMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val query = _uiState.value.searchQuery
                val config = _uiState.value.searchConfig
                // Keep the search scoped to the folder the user is currently browsing,
                // so the search field does not silently widen the scope.
                val folderPath = _uiState.value.selectedFolderPath.takeIf { it.isNotBlank() }
                val memories =
                    if (query.isBlank()) {
                        repository.searchMemories(
                            query = "",
                            folderPath = folderPath,
                            scoreMode = config.scoreMode,
                            keywordWeight = config.keywordWeight,
                            tagWeight = config.tagWeight,
                            semanticWeight = config.vectorWeight,
                            edgeWeight = config.edgeWeight
                        )
                    } else {
                        repository.searchMemories(
                            query = query,
                            folderPath = folderPath,
                            scoreMode = config.scoreMode,
                            keywordWeight = config.keywordWeight,
                            tagWeight = config.tagWeight,
                            semanticWeight = config.vectorWeight,
                            edgeWeight = config.edgeWeight
                        )
                    }
                val graphData = repository.getGraphForMemories(memories)
                _uiState.update { it.copy(graph = graphData, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_search, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /** Updates the search query in the state. */
    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    fun showSearchSettingsDialog(visible: Boolean) {
        _uiState.update { it.copy(isSearchSettingsDialogVisible = visible) }
        if (visible) {
            loadSearchSettings()
            loadCloudEmbeddingSettings()
            refreshEmbeddingDimensionUsage()
        }
    }

    fun openSearchSimulationDialog() {
        _uiState.update {
            it.copy(
                isSearchSettingsDialogVisible = false,
                isSearchSimulationDialogVisible = true,
                searchSimulationQuery = it.searchQuery,
                searchSimulationResult = null,
                searchSimulationError = null
            )
        }
    }

    fun showSearchSimulationDialog(visible: Boolean) {
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
        _uiState.update { it.copy(searchSimulationQuery = newQuery) }
    }

    fun runSearchSimulation() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val query = currentState.searchSimulationQuery
            val config = currentState.searchConfig
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
                    edgeWeight = config.edgeWeight
                )
                _uiState.update {
                    it.copy(
                        isSearchSimulationRunning = false,
                        searchSimulationResult = debugInfo,
                        searchSimulationError = null
                    )
                }
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
        newCloudConfig: CloudEmbeddingConfig,
        autoSaveIntervalMinutes: Int,
        memoryExtractionCustomRules: String
    ) {
        val normalizedSearchConfig = newConfig.normalized()
        val normalizedCloudConfig = newCloudConfig.normalized()
        val normalizedInterval =
            autoSaveIntervalMinutes.coerceIn(
                MemorySearchSettingsPreferences.MIN_AUTO_SAVE_INTERVAL_MINUTES,
                MemorySearchSettingsPreferences.MAX_AUTO_SAVE_INTERVAL_MINUTES
            )
        _uiState.update {
            it.copy(
                searchConfig = normalizedSearchConfig,
                cloudEmbeddingConfig = normalizedCloudConfig,
                autoSaveIntervalMinutes = normalizedInterval,
                memoryExtractionCustomRules = memoryExtractionCustomRules
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            searchSettingsPreferences.save(normalizedSearchConfig)
            searchSettingsPreferences.saveAutoSaveIntervalMinutes(normalizedInterval)
            searchSettingsPreferences.saveMemoryExtractionCustomRules(memoryExtractionCustomRules)
            repository.saveCloudEmbeddingConfig(normalizedCloudConfig)
        }
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

    private fun loadCloudEmbeddingSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.loadCloudEmbeddingConfig()
            _uiState.update { it.copy(cloudEmbeddingConfig = config) }
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
                com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "Loaded ${folders.size} folders: $folders")
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
    fun selectFolder(folderPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedFolderPath = folderPath) }
            try {
                val graphData = refreshGraph()
                _uiState.update { it.copy(graph = graphData, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_load_folder_graph, e.message ?: "Unknown error"))
                }
            }
        }
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
        _uiState.update { it.copy(selectedMemory = memory) }
    }

    /** Selects a node in the graph. Fetches the full memory details for the selected node. */
    fun selectNode(node: Node) {
        viewModelScope.launch {
            if (_uiState.value.isLinkingMode) {
                // 连接模式
                val currentLinkingIds = _uiState.value.linkingNodeIds.toMutableList()
                if (node.id !in currentLinkingIds) {
                    currentLinkingIds.add(node.id)
                }
                _uiState.update { it.copy(linkingNodeIds = currentLinkingIds) }
            } else if (_uiState.value.isBoxSelectionMode) {
                // 框选模式
                val currentSelectedIds = _uiState.value.boxSelectedNodeIds.toMutableSet()
                if (node.id in currentSelectedIds) {
                    currentSelectedIds.remove(node.id)
                } else {
                    currentSelectedIds.add(node.id)
                }
                _uiState.update { it.copy(boxSelectedNodeIds = currentSelectedIds) }
            } else {
                // 普通模式
                val memory = repository.getMemoryByUuid(node.id)
                if (memory?.isDocumentNode == true) {
                    // 如果是文档节点，检查是否有全局搜索词
                    val globalQuery = _uiState.value.searchQuery

                    val chunks = if (globalQuery.isNotBlank()) {
                        com.ai.assistance.operit.util.AppLogger.d("MemoryVM", "Node click on doc, searching with global query: '$globalQuery'")
                        repository.searchChunksInDocument(memory.id, globalQuery)
                    } else {
                        com.ai.assistance.operit.util.AppLogger.d("MemoryVM", "Node click on doc, no global query. Getting all chunks.")
                        repository.getChunksForMemory(memory.id)
                    }

                    _uiState.update {
                        it.copy(
                            selectedNodeId = node.id,
                            selectedMemory = memory,
                            selectedEdge = null,
                            isDocumentViewOpen = true,
                            selectedDocumentChunks = chunks,
                            documentSearchQuery = globalQuery // 预填内部搜索框
                        )
                    }
                } else {
                    _uiState.update { it.copy(selectedNodeId = node.id, selectedMemory = memory, selectedEdge = null, isDocumentViewOpen = false) }
                }
            }
        }
    }

    /** Selects an edge in the graph. */
    fun selectEdge(edge: Edge) {
        _uiState.update { it.copy(selectedEdge = edge, selectedNodeId = null, selectedMemory = null) }
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
                repository.createMemoryFromDocument(title, filePath, fileContent, currentFolder)
                // 刷新图谱和文件夹列表
                val updatedGraph = refreshGraph()
                loadFolderPaths()
                _uiState.update { it.copy(graph = updatedGraph, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_import_document, e.message ?: "Unknown error"))
                }
            }
        }
    }

    /** 新建记忆 */
    fun createMemory(title: String, content: String, contentType: String = "text/plain") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val currentFolder = _uiState.value.selectedFolderPath
                repository.createMemory(title, content, contentType, folderPath = currentFolder)
                val updatedGraph = refreshGraph()
                loadFolderPaths()
                _uiState.update {
                    it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_create_memory, e.message ?: "Unknown error"))
                }
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
                    it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph, isDocumentViewOpen = false)
                }
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
            com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "No nodes selected, aborting delete.")
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
            com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "deleteSelectedNodes called with ${selectedIds.size} nodes.")
            if (selectedIds.isEmpty()) {
                com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "No nodes selected, aborting delete.")
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, showBatchDeleteConfirm = false) }
            try {
                com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "Calling repository.deleteMemoriesByUuids with IDs: $selectedIds")
                repository.deleteMemoriesByUuids(selectedIds)
                val updatedGraph = refreshGraph()
                com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "Graph refreshed after deletion.")
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
                com.ai.assistance.operit.util.AppLogger.e("MemoryViewModel", "Failed to delete selected memories", e)
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
        _uiState.update { it.copy(isEditing = true, editingMemory = memory) }
    }
    /** 取消编辑 */
    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingMemory = null) }
    }

    /** 进入/退出边的编辑状态 */
    fun startEditingEdge(edge: Edge) {
        _uiState.update { it.copy(isEditingEdge = true, editingEdge = edge) }
    }
    fun cancelEditingEdge() {
        _uiState.update { it.copy(isEditingEdge = false, editingEdge = null) }
    }

    /** 更新边的信息 */
    fun updateEdge(edge: Edge, type: String, weight: Float, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isEditingEdge = false, editingEdge = null) }
            try {
                repository.updateLink(edge.id, type, weight, description)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedEdge = null, // 彻底清空选中状态
                        graph = updatedGraph
                    )
                }
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
                    linkingNodeIds = emptyList(),
                    isBoxSelectionMode = false,
                    boxSelectedNodeIds = emptySet()
                )
            }
        } else {
            // 退出连接模式
            _uiState.update {
                it.copy(isLinkingMode = false, linkingNodeIds = emptyList())
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
                    _uiState.update { it.copy(isLoading = false) }
                }
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

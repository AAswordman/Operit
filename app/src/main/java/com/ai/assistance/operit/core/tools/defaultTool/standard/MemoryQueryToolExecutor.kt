package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.tools.MemoryQueryResultData
import com.ai.assistance.operit.core.tools.MemoryLinkResultData
import com.ai.assistance.operit.core.tools.MemoryLinkQueryResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.preferences.UserProfileDocumentRepository
import com.ai.assistance.operit.data.repository.MemoryRepository
import kotlinx.coroutines.flow.first
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*
import com.ai.assistance.operit.data.preferences.preferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes queries against the AI's memory graph and the explicit user.md update tool.
 */
class MemoryQueryToolExecutor(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "MemoryQueryToolExecutor"
        private const val MAX_QUERY_SNAPSHOTS_PER_PROFILE = 32
        private const val DEFAULT_RELEVANCE_THRESHOLD = 0.0

        private data class QuerySnapshotState(
            val id: String,
            val seenMemoryIds: MutableSet<Long> = ConcurrentHashMap.newKeySet<Long>(),
            val lock: Any = Any(),
            @Volatile var lastAccessAtMs: Long = System.currentTimeMillis()
        )

        private val querySnapshotsByProfile =
            ConcurrentHashMap<String, ConcurrentHashMap<String, QuerySnapshotState>>()
    }

    private val memoryRepositories = ConcurrentHashMap<String, MemoryRepository>()
    private val settingsRepositories = ConcurrentHashMap<String, MemorySearchSettingsPreferences>()

    private fun resolveGlobalActiveProfileId(): String {
        return kotlinx.coroutines.runBlocking { preferencesManager.activeMemorySpaceIdFlow.first() }
    }

    private fun resolveCallerCardId(tool: AITool): String? {
        val explicitCallerCardId =
            tool.parameters
                .find { it.name == "caller_card_id" }
                ?.value
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        if (explicitCallerCardId != null) {
            return explicitCallerCardId
        }
        return ToolExecutionManager.currentToolRuntimeContext()
            ?.callerCardId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveRoleCardProfileId(callerCardId: String?): String? {
        val resolvedCardId = callerCardId?.takeIf { it.isNotBlank() } ?: return null
        val characterCard = CharacterCardManager.getInstance(context).getCharacterCard(resolvedCardId)
        val bindingMode =
            CharacterCardMemoryProfileBindingMode.normalize(characterCard.memoryProfileBindingMode)
        val boundProfileId = characterCard.memoryProfileId?.takeIf { it.isNotBlank() }
        return if (
            bindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE &&
            boundProfileId != null
        ) {
            boundProfileId
        } else {
            null
        }
    }

    private suspend fun resolveActiveProfileId(tool: AITool): String {
        return resolveRoleCardProfileId(resolveCallerCardId(tool))
            ?: resolveGlobalActiveProfileId()
    }

    private fun getMemoryRepository(profileId: String): MemoryRepository =
        memoryRepositories.computeIfAbsent(profileId) { MemoryRepository(context, it) }

    private fun getMemorySearchSettingsPreferences(profileId: String): MemorySearchSettingsPreferences =
        settingsRepositories.computeIfAbsent(profileId) { MemorySearchSettingsPreferences(context, it) }

    private fun getQuerySnapshotStore(profileId: String): ConcurrentHashMap<String, QuerySnapshotState> {
        return querySnapshotsByProfile.computeIfAbsent(profileId) { ConcurrentHashMap() }
    }

    private fun getOrCreateQuerySnapshot(profileId: String, requestedSnapshotId: String?): Pair<QuerySnapshotState, Boolean> {
        val store = getQuerySnapshotStore(profileId)
        val now = System.currentTimeMillis()

        if (requestedSnapshotId == null) {
            while (true) {
                val generatedSnapshot = QuerySnapshotState(
                    id = UUID.randomUUID().toString(),
                    lastAccessAtMs = now
                )
                if (store.putIfAbsent(generatedSnapshot.id, generatedSnapshot) == null) {
                    trimOldSnapshots(store)
                    return generatedSnapshot to true
                }
            }
        }

        store[requestedSnapshotId]?.let { existingSnapshot ->
            existingSnapshot.lastAccessAtMs = now
            return existingSnapshot to false
        }

        val requestedSnapshot = QuerySnapshotState(
            id = requestedSnapshotId,
            lastAccessAtMs = now
        )
        val existingSnapshot = store.putIfAbsent(requestedSnapshotId, requestedSnapshot)
        val resolvedSnapshot = existingSnapshot ?: requestedSnapshot
        val snapshotCreated = existingSnapshot == null
        resolvedSnapshot.lastAccessAtMs = now
        if (snapshotCreated) {
            trimOldSnapshots(store)
        }
        return resolvedSnapshot to snapshotCreated
    }

    private fun trimOldSnapshots(store: ConcurrentHashMap<String, QuerySnapshotState>) {
        if (store.size <= MAX_QUERY_SNAPSHOTS_PER_PROFILE) {
            return
        }
        val overflow = store.size - MAX_QUERY_SNAPSHOTS_PER_PROFILE
        store.values
            .sortedBy { it.lastAccessAtMs }
            .take(overflow)
            .forEach { stale ->
                store.remove(stale.id, stale)
            }
    }

    private fun parseTimeBoundary(value: String?, isEnd: Boolean): Long? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val timezone = TimeZone.getDefault()

        fun parseExact(pattern: String): Date? {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = timezone
            }
            val position = ParsePosition(0)
            val parsed = formatter.parse(trimmed, position)
            return if (parsed != null && position.index == trimmed.length) parsed else null
        }

        parseExact("yyyy-MM-dd HH:mm")?.let { parsed ->
            return Calendar.getInstance(timezone).apply {
                time = parsed
                set(Calendar.SECOND, if (isEnd) 59 else 0)
                set(Calendar.MILLISECOND, if (isEnd) 999 else 0)
            }.timeInMillis
        }

        parseExact("yyyy-MM-dd")?.let { parsed ->
            return Calendar.getInstance(timezone).apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, if (isEnd) 23 else 0)
                set(Calendar.MINUTE, if (isEnd) 59 else 0)
                set(Calendar.SECOND, if (isEnd) 59 else 0)
                set(Calendar.MILLISECOND, if (isEnd) 999 else 0)
            }.timeInMillis
        }

        return null
    }

    override fun invoke(tool: AITool): ToolResult {
        return kotlinx.coroutines.runBlocking {
            return@runBlocking when (tool.name) {
                "query_memory" -> executeQueryMemory(tool)
                "get_memory_by_title" -> executeGetMemoryByTitle(tool)
                "create_memory" -> executeCreateMemory(tool)
                "update_memory" -> executeUpdateMemory(tool)
                "delete_memory" -> executeDeleteMemory(tool)
                "move_memory" -> executeMoveMemory(tool)
                "update_user_profile" -> executeUpdateUserProfile(tool)
                "update_user_preferences" -> executeLegacyUserPreferencesUpdate(tool)
                "link_memories" -> executeLinkMemories(tool)
                "query_memory_links" -> executeQueryMemoryLinks(tool)
                "update_memory_link" -> executeUpdateMemoryLink(tool)
                "delete_memory_link" -> executeDeleteMemoryLink(tool)
                else -> ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Unknown tool: ${tool.name}"
                )
            }
        }
    }

    private suspend fun executeQueryMemory(tool: AITool): ToolResult {
        val profileId = resolveActiveProfileId(tool)
        val memoryRepository = getMemoryRepository(profileId)
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""
        val folderPath = tool.parameters.find { it.name == "folder_path" }?.value
        val settings = getMemorySearchSettingsPreferences(profileId).load()
        val limitParam = tool.parameters.find { it.name == "limit" }?.value
        val limit = limitParam?.toIntOrNull()
        val startTimeParam = tool.parameters.find { it.name == "start_time" }?.value
        val endTimeParam = tool.parameters.find { it.name == "end_time" }?.value
        val snapshotIdParam = tool.parameters.find { it.name == "snapshot_id" }?.value
        val thresholdParam = tool.parameters.find { it.name == "threshold" }?.value
        val normalizedSnapshotId = snapshotIdParam?.trim()?.takeIf { it.isNotEmpty() }
        val threshold = thresholdParam?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        if (!thresholdParam.isNullOrBlank() && threshold == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid threshold. Expected a non-negative number."
            )
        }

        if (threshold != null && threshold < 0.0) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid threshold. Expected a non-negative number."
            )
        }

        val startTimeMs = parseTimeBoundary(startTimeParam, isEnd = false)
        if (!startTimeParam.isNullOrBlank() && startTimeMs == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid start_time. Expected format YYYY-MM-DD or YYYY-MM-DD HH:mm."
            )
        }

        val endTimeMs = parseTimeBoundary(endTimeParam, isEnd = true)
        if (!endTimeParam.isNullOrBlank() && endTimeMs == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid end_time. Expected format YYYY-MM-DD or YYYY-MM-DD HH:mm."
            )
        }

        if (startTimeMs != null && endTimeMs != null && startTimeMs > endTimeMs) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid time range: start_time must be <= end_time."
            )
        }
        
        // 如果查询是 "*" 且用户没有显式指定 limit，则返回所有结果
        val isWildcardQuery = query.trim() == "*"
        val defaultLimit = if (isWildcardQuery && limit == null) {
            Int.MAX_VALUE // 使用最大值表示返回所有结果
        } else {
            20 // 普通查询默认返回 20 条
        }
        val finalLimit = limit ?: defaultLimit

        if (query.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Query parameter cannot be empty.")
        }

        // limit 无上限，但至少为 1
        val validLimit = if (finalLimit < 1) 1 else finalLimit
        val (snapshotState, snapshotCreated) = getOrCreateQuerySnapshot(profileId, normalizedSnapshotId)

        AppLogger.d(
            TAG,
            "Executing memory query: '$query' in folder: '${folderPath ?: "All"}', snapshot_id=${snapshotState.id}, snapshot_created=$snapshotCreated, start_time: ${startTimeMs ?: "null"}, end_time: ${endTimeMs ?: "null"}, limit: $validLimit, threshold=${threshold ?: DEFAULT_RELEVANCE_THRESHOLD}, mode=${settings.scoreMode}, keywordWeight=${settings.keywordWeight}, tagWeight=${settings.tagWeight}, vectorWeight=${settings.vectorWeight}, edgeWeight=${settings.edgeWeight}"
        )

        return try {
            val results = memoryRepository.searchMemories(
                query = query,
                folderPath = folderPath,
                scoreMode = settings.scoreMode,
                keywordWeight = settings.keywordWeight,
                tagWeight = settings.tagWeight,
                semanticWeight = settings.vectorWeight,
                edgeWeight = settings.edgeWeight,
                relevanceThreshold = threshold ?: DEFAULT_RELEVANCE_THRESHOLD,
                createdAtStartMs = startTimeMs,
                createdAtEndMs = endTimeMs
            )

            // Keep de-duplication stable even when multiple calls share the same snapshot in parallel.
            val (excludedBySnapshotCount, returnedMemories) = synchronized(snapshotState.lock) {
                val excludedCount = results.count { it.id in snapshotState.seenMemoryIds }
                val unseenResults = results.filterNot { it.id in snapshotState.seenMemoryIds }
                val selectedResults = unseenResults.take(validLimit)
                if (selectedResults.isNotEmpty()) {
                    snapshotState.seenMemoryIds.addAll(selectedResults.map { it.id })
                }
                snapshotState.lastAccessAtMs = System.currentTimeMillis()
                excludedCount to selectedResults
            }

            val formattedResult = buildResultData(
                memoryRepository = memoryRepository,
                memories = returnedMemories,
                query = query,
                limit = validLimit,
                snapshotId = snapshotState.id,
                snapshotCreated = snapshotCreated,
                excludedBySnapshotCount = excludedBySnapshotCount
            )
            AppLogger.d(TAG, "Memory query result for '$query':\n$formattedResult")
            ToolResult(toolName = tool.name, success = true, result = formattedResult)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Memory query failed", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to execute memory query: ${e.message}")
        }
    }

    private suspend fun executeGetMemoryByTitle(tool: AITool): ToolResult {
        val profileId = resolveActiveProfileId(tool)
        val memoryRepository = getMemoryRepository(profileId)
        val title = tool.parameters.find { it.name == "title" }?.value
        if (title.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "title parameter is required"
            )
        }

        // 提取可选的分块相关参数
        val chunkIndexParam = tool.parameters.find { it.name == "chunk_index" }?.value
        val chunkRangeParam = tool.parameters.find { it.name == "chunk_range" }?.value
        val queryParam = tool.parameters.find { it.name == "query" }?.value
        val chunkLimitParam = tool.parameters.find { it.name == "limit" }?.value

        AppLogger.d(TAG, "Getting memory by title: $title, chunk_index: $chunkIndexParam, chunk_range: $chunkRangeParam, query: $queryParam, limit: $chunkLimitParam")

        return try {
            val memory = memoryRepository.findMemoryByTitle(title)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $title"
                )
            }

            // 如果是文档节点且提供了分块参数，则进行特殊处理
            if (memory.isDocumentNode && (chunkIndexParam != null || chunkRangeParam != null || queryParam != null)) {
                return handleDocumentChunkRetrieval(
                    toolName = tool.name,
                    memoryRepository = memoryRepository,
                    memory = memory,
                    chunkIndexParam = chunkIndexParam,
                    chunkRangeParam = chunkRangeParam,
                    queryParam = queryParam,
                    limitParam = chunkLimitParam
                )
            }

            // 默认行为：返回完整记忆
            val formattedResult = buildResultData(memoryRepository, listOf(memory), title, 1)
            AppLogger.d(TAG, "Found memory by title '$title':\n$formattedResult")
            ToolResult(
                toolName = tool.name,
                success = true,
                result = formattedResult
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get memory by title", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to get memory by title: ${e.message}"
            )
        }
    }

    private suspend fun handleDocumentChunkRetrieval(
        toolName: String,
        memoryRepository: MemoryRepository,
        memory: Memory,
        chunkIndexParam: String?,
        chunkRangeParam: String?,
        queryParam: String?,
        limitParam: String?
    ): ToolResult = withContext(Dispatchers.IO) {
        val totalChunks = memoryRepository.getTotalChunkCount(memory.id)
        val validLimit = (limitParam?.toIntOrNull() ?: 20).coerceAtLeast(1)
        
        try {
            // 优先级：query > chunk_range > chunk_index
            val chunks = when {
                // 模糊搜索分块
                !queryParam.isNullOrBlank() -> {
                    AppLogger.d(TAG, "Searching chunks in document '${memory.title}' with query: '$queryParam', limit: $validLimit")
                    memoryRepository.searchChunksInDocument(memory.id, queryParam, validLimit)
                }
                // 范围查询
                !chunkRangeParam.isNullOrBlank() -> {
                    val rangeParts = chunkRangeParam.split("-")
                    if (rangeParts.size != 2) {
                        return@withContext ToolResult(
                            toolName = toolName,
                            success = false,
                            result = StringResultData(""),
                            error = "Invalid chunk_range format. Expected 'start-end' (e.g., '3-7')"
                        )
                    }
                    // 解析为1-based索引，转换为0-based
                    val startIndex = (rangeParts[0].toIntOrNull() ?: 1) - 1
                    val endIndex = (rangeParts[1].toIntOrNull() ?: totalChunks) - 1
                    
                    if (startIndex < 0 || endIndex >= totalChunks || startIndex > endIndex) {
                        return@withContext ToolResult(
                            toolName = toolName,
                            success = false,
                            result = StringResultData(""),
                            error = "Chunk range out of bounds. Document has $totalChunks chunks. Valid range: 1-$totalChunks"
                        )
                    }
                    AppLogger.d(TAG, "Retrieving chunk range ${startIndex + 1}-${endIndex + 1} from document '${memory.title}'")
                    memoryRepository.getChunksByRange(memory.id, startIndex, endIndex)
                }
                // 单个分块
                !chunkIndexParam.isNullOrBlank() -> {
                    // 解析为1-based索引，转换为0-based
                    val chunkIndex = (chunkIndexParam.toIntOrNull() ?: 1) - 1
                    if (chunkIndex < 0 || chunkIndex >= totalChunks) {
                        return@withContext ToolResult(
                            toolName = toolName,
                            success = false,
                            result = StringResultData(""),
                            error = "Chunk index out of bounds. Document has $totalChunks chunks. Valid range: 1-$totalChunks"
                        )
                    }
                    AppLogger.d(TAG, "Retrieving chunk ${chunkIndex + 1} from document '${memory.title}'")
                    val chunk = memoryRepository.getChunkByIndex(memory.id, chunkIndex)
                    listOfNotNull(chunk)
                }
                else -> emptyList()
            }

            if (chunks.isEmpty()) {
                return@withContext ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "No matching chunks found"
                )
            }

            // 格式化返回结果
            val content = "Document: ${memory.title}\n" +
                chunks.joinToString("\n---\n") { chunk ->
                    "Chunk ${chunk.chunkIndex + 1}/$totalChunks:\n${chunk.content}"
                }

            val chunkIndices = chunks.map { it.chunkIndex }
            val chunkInfo = if (chunks.size == 1) {
                "Chunk ${chunks[0].chunkIndex + 1}/$totalChunks"
            } else {
                "Chunks ${chunks.map { it.chunkIndex + 1 }.joinToString(", ")}/$totalChunks"
            }

            AppLogger.d(TAG, "Retrieved ${chunks.size} chunks from document '${memory.title}': $chunkInfo")
            
            ToolResult(
                toolName = toolName,
                success = true,
                result = StringResultData(content)
            )
        } catch (e: NumberFormatException) {
            ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Invalid number format in chunk parameters: ${e.message}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to retrieve document chunks", e)
            ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Failed to retrieve document chunks: ${e.message}"
            )
        }
    }

    private suspend fun executeCreateMemory(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val title = tool.parameters.find { it.name == "title" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        
        if (title.isBlank() || content.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Both title and content parameters are required"
            )
        }

        AppLogger.d(TAG, "Creating memory: $title")

        return try {
            val contentType = tool.parameters.find { it.name == "content_type" }?.value ?: "text/plain"
            val source = tool.parameters.find { it.name == "source" }?.value ?: "ai_created"
            val folderPath = tool.parameters.find { it.name == "folder_path" }?.value ?: ""
            val tagsParam = tool.parameters.find { it.name == "tags" }?.value
            val tags = tagsParam
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
             
            val memory = memoryRepository.createMemory(
                title = title,
                content = content,
                contentType = contentType,
                source = source,
                folderPath = folderPath,
                tags = tags
            )
            
            if (memory != null) {
                val message = "Successfully created memory: '$title' (UUID: ${memory.uuid})"
                AppLogger.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to create memory"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to create memory: ${e.message}"
            )
        }
    }

    private suspend fun executeUpdateMemory(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val oldTitle = tool.parameters.find { it.name == "old_title" }?.value
        
        if (oldTitle.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "old_title parameter is required to identify the memory"
            )
        }

        AppLogger.d(TAG, "Updating memory with title: $oldTitle")

        return try {
            val memory = memoryRepository.findMemoryByTitle(oldTitle)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $oldTitle"
                )
            }

            // 获取要更新的字段，如果没有提供则使用原值
            val newTitle = tool.parameters.find { it.name == "new_title" }?.value ?: memory.title
            val newContent = tool.parameters.find { it.name == "content" }?.value ?: memory.content
            val newContentType = tool.parameters.find { it.name == "content_type" }?.value ?: memory.contentType
            val newSource = tool.parameters.find { it.name == "source" }?.value ?: memory.source
            val newCredibility = tool.parameters.find { it.name == "credibility" }?.value?.toFloatOrNull() ?: memory.credibility
            val newImportance = tool.parameters.find { it.name == "importance" }?.value?.toFloatOrNull() ?: memory.importance
            val newFolderPath = tool.parameters.find { it.name == "folder_path" }?.value ?: memory.folderPath
            val tagsParam = tool.parameters.find { it.name == "tags" }?.value
            val newTags = tagsParam?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            
            val updatedMemory = memoryRepository.updateMemory(
                memory = memory,
                newTitle = newTitle,
                newContent = newContent,
                newContentType = newContentType,
                newSource = newSource,
                newCredibility = newCredibility,
                newImportance = newImportance,
                newFolderPath = newFolderPath,
                newTags = newTags
            )
            
            if (updatedMemory != null) {
                val message = "Successfully updated memory from '$oldTitle' to '$newTitle'"
                AppLogger.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to update memory"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update memory: ${e.message}"
            )
        }
    }

    private suspend fun executeDeleteMemory(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val title = tool.parameters.find { it.name == "title" }?.value
        
        if (title.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "title parameter is required to identify the memory"
            )
        }

        AppLogger.d(TAG, "Deleting memory with title: $title")

        return try {
            val memory = memoryRepository.findMemoryByTitle(title)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $title"
                )
            }

            val deleted = memoryRepository.deleteMemory(memory.id)
            
            if (deleted) {
                val message = "Successfully deleted memory: '$title'"
                AppLogger.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to delete memory"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete memory: ${e.message}"
            )
        }
    }

    private suspend fun executeUpdateUserProfile(tool: AITool): ToolResult {
        AppLogger.d(TAG, "Executing user.md update")

        return try {
            val markdown = tool.parameters.find { it.name == "markdown" }?.value
            if (markdown == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "markdown parameter is required"
                )
            }

            withContext(Dispatchers.IO) {
                UserProfileDocumentRepository.getInstance(context).save(markdown)
            }

            val message = "Successfully updated user.md"
            AppLogger.d(TAG, message)
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(message)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update user.md", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update user.md: ${e.message}"
            )
        }
    }

    /**
     * Compatibility adapter for released tool packages and persisted tool calls. The replacement
     * prompt exposes only update_user_profile; this path preserves an old call's information in
     * user.md without restoring the structured-profile runtime.
     */
    private suspend fun executeLegacyUserPreferencesUpdate(tool: AITool): ToolResult {
        val fieldLabels =
            mapOf(
                "birth_date" to "Birth date (Unix milliseconds)",
                "gender" to "Gender",
                "personality" to "Personality",
                "identity" to "Identity",
                "occupation" to "Occupation",
                "ai_style" to "Preferred assistant style"
            )
        val updates =
            tool.parameters.mapNotNull { parameter ->
                fieldLabels[parameter.name]?.let { label -> label to parameter.value }
            }
        if (updates.isEmpty()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "At least one preference parameter must be provided"
            )
        }

        return try {
            val repository = UserProfileDocumentRepository.getInstance(context)
            withContext(Dispatchers.IO) {
                val current = repository.load().trimEnd()
                val importedSection =
                    buildString {
                        appendLine("## Imported profile update")
                        appendLine()
                        updates.forEach { (label, value) -> appendLine("- $label: $value") }
                    }.trimEnd()
                repository.save("$current\n\n$importedSection\n")
            }
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Successfully preserved the preference update in user.md")
            )
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to preserve legacy preference update in user.md", error)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update user.md: ${error.message}"
            )
        }
    }

    private suspend fun executeLinkMemories(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value
        val targetTitle = tool.parameters.find { it.name == "target_title" }?.value
        
        if (sourceTitle.isNullOrBlank() || targetTitle.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Both source_title and target_title parameters are required"
            )
        }

        AppLogger.d(TAG, "Linking memories: '$sourceTitle' -> '$targetTitle'")

        return try {
            // 提取可选参数
            val linkType = tool.parameters.find { it.name == "link_type" }?.value ?: "related"
            val weight = tool.parameters.find { it.name == "weight" }?.value?.toFloatOrNull() ?: 0.7f
            val description = tool.parameters.find { it.name == "description" }?.value ?: ""
            
            // 限制 weight 在有效范围内
            val validWeight = weight.coerceIn(0.0f, 1.0f)
            
            // 查找源记忆和目标记忆
            val sourceMemory = memoryRepository.findMemoryByTitle(sourceTitle)
            if (sourceMemory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source memory not found with title: $sourceTitle"
                )
            }
            
            val targetMemory = memoryRepository.findMemoryByTitle(targetTitle)
            if (targetMemory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Target memory not found with title: $targetTitle"
                )
            }
            
            // 创建链接
            memoryRepository.linkMemories(
                source = sourceMemory,
                target = targetMemory,
                type = linkType,
                weight = validWeight,
                description = description
            )
            
            val resultData = MemoryLinkResultData(
                sourceTitle = sourceTitle,
                targetTitle = targetTitle,
                linkType = linkType,
                weight = validWeight,
                description = description
            )
            
            AppLogger.d(TAG, "Successfully linked memories: '$sourceTitle' -> '$targetTitle' (type: $linkType, weight: $validWeight)")
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = resultData
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to link memories", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to link memories: ${e.message}"
            )
        }
    }

    private suspend fun executeQueryMemoryLinks(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val linkIdRaw = tool.parameters.find { it.name == "link_id" }?.value
        val linkId = linkIdRaw?.toLongOrNull()
        if (!linkIdRaw.isNullOrBlank() && linkId == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid link_id. Expected integer."
            )
        }

        val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value?.trim()
        val targetTitle = tool.parameters.find { it.name == "target_title" }?.value?.trim()
        val linkType = tool.parameters.find { it.name == "link_type" }?.value?.trim()?.takeIf { it.isNotEmpty() }
        val limitRaw = tool.parameters.find { it.name == "limit" }?.value
        val parsedLimit = limitRaw?.toIntOrNull()
        if (!limitRaw.isNullOrBlank() && parsedLimit == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid limit. Expected integer."
            )
        }
        val limit = (parsedLimit ?: 20).coerceIn(1, 200)

        return try {
            val sourceMemoryId = if (!sourceTitle.isNullOrBlank()) {
                memoryRepository.findMemoryByTitle(sourceTitle)?.id ?: return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source memory not found with title: $sourceTitle"
                )
            } else {
                null
            }

            val targetMemoryId = if (!targetTitle.isNullOrBlank()) {
                memoryRepository.findMemoryByTitle(targetTitle)?.id ?: return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Target memory not found with title: $targetTitle"
                )
            } else {
                null
            }

            val links = memoryRepository.queryMemoryLinks(
                linkId = linkId,
                sourceMemoryId = sourceMemoryId,
                targetMemoryId = targetMemoryId,
                linkType = linkType,
                limit = limit
            )

            val linkInfos = links.mapNotNull { link ->
                val source = link.source.target
                val target = link.target.target
                if (source == null || target == null) {
                    null
                } else {
                    MemoryLinkQueryResultData.LinkInfo(
                        linkId = link.id,
                        sourceTitle = source.title,
                        targetTitle = target.title,
                        linkType = link.type,
                        weight = link.weight,
                        description = link.description
                    )
                }
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = MemoryLinkQueryResultData(
                    totalCount = linkInfos.size,
                    links = linkInfos
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to query memory links", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to query memory links: ${e.message}"
            )
        }
    }

    private suspend fun executeMoveMemory(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val targetFolderPath = tool.parameters.find { it.name == "target_folder_path" }?.value
        val sourceFolderPath = tool.parameters.find { it.name == "source_folder_path" }?.value
        val hasSourceFolderParam = tool.parameters.any { it.name == "source_folder_path" }
        val titlesRaw = tool.parameters.find { it.name == "titles" }?.value
        val titles = parseTitlesParam(titlesRaw)

        if (targetFolderPath == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "target_folder_path parameter is required"
            )
        }

        if (titles.isEmpty() && !hasSourceFolderParam) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Provide titles and/or source_folder_path to select memories to move"
            )
        }

        AppLogger.d(
            TAG,
            "Moving memories. target_folder_path='$targetFolderPath', source_folder_path='${sourceFolderPath ?: ""}', has_source_folder_param=$hasSourceFolderParam, titles_count=${titles.size}"
        )

        return try {
            val selectedByTitle = if (titles.isNotEmpty()) {
                titles.flatMap { title -> memoryRepository.findMemoriesByTitle(title) }
            } else {
                emptyList()
            }
            val selectedByFolder = if (hasSourceFolderParam) {
                memoryRepository.getMemoriesByFolderPath(sourceFolderPath ?: "")
            } else {
                emptyList()
            }

            val selected = when {
                titles.isNotEmpty() && hasSourceFolderParam -> {
                    val folderIds = selectedByFolder.map { it.id }.toHashSet()
                    selectedByTitle.filter { folderIds.contains(it.id) }
                }
                titles.isNotEmpty() -> selectedByTitle
                else -> selectedByFolder
            }

            val uniqueMemories = LinkedHashMap<Long, Memory>()
            selected.forEach { uniqueMemories[it.id] = it }
            val memoryIds = uniqueMemories.keys.toList()

            if (memoryIds.isEmpty()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "No matching memories found to move"
                )
            }

            val moved = memoryRepository.moveMemoriesToFolder(memoryIds, targetFolderPath)
            if (!moved) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to move selected memories"
                )
            }

            val destination = if (targetFolderPath.isBlank()) "uncategorized" else targetFolderPath
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Successfully moved ${memoryIds.size} memories to '$destination'")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to move memories", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to move memories: ${e.message}"
            )
        }
    }

    private fun parseTitlesParam(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split(',', '\n', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private suspend fun executeUpdateMemoryLink(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val linkId = tool.parameters.find { it.name == "link_id" }?.value?.toLongOrNull()
        val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value
        val targetTitle = tool.parameters.find { it.name == "target_title" }?.value
        val locatorLinkType = tool.parameters.find { it.name == "link_type" }?.value

        val newLinkType = tool.parameters.find { it.name == "new_link_type" }?.value
        val newWeightParam = tool.parameters.find { it.name == "weight" }?.value
        val newWeight = newWeightParam?.toFloatOrNull()
        val newDescription = tool.parameters.find { it.name == "description" }?.value

        if (newLinkType.isNullOrBlank() && newWeight == null && newDescription == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "At least one of new_link_type, weight, description must be provided"
            )
        }

        return try {
            val link = if (linkId != null) {
                memoryRepository.findLinkById(linkId)
            } else {
                if (sourceTitle.isNullOrBlank() || targetTitle.isNullOrBlank()) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Provide link_id, or provide both source_title and target_title"
                    )
                }

                val sourceMemory = memoryRepository.findMemoryByTitle(sourceTitle)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Source memory not found with title: $sourceTitle"
                    )
                val targetMemory = memoryRepository.findMemoryByTitle(targetTitle)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Target memory not found with title: $targetTitle"
                    )

                val candidates = sourceMemory.links.filter {
                    it.target.target?.id == targetMemory.id &&
                        (locatorLinkType.isNullOrBlank() || it.type == locatorLinkType)
                }
                when {
                    candidates.isEmpty() -> null
                    candidates.size > 1 -> {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Multiple links matched. Provide link_id or a more specific link_type."
                        )
                    }
                    else -> candidates.first()
                }
            }

            if (link == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = if (linkId != null) "Link not found with id: $linkId" else "No matching link found"
                )
            }

            val updated = memoryRepository.updateLink(
                linkId = link.id,
                type = newLinkType ?: link.type,
                weight = (newWeight ?: link.weight).coerceIn(0.0f, 1.0f),
                description = newDescription ?: link.description
            )

            if (updated == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to update memory link"
                )
            }

            val source = updated.source.target?.title ?: sourceTitle ?: ""
            val target = updated.target.target?.title ?: targetTitle ?: ""

            ToolResult(
                toolName = tool.name,
                success = true,
                result = MemoryLinkResultData(
                    sourceTitle = source,
                    targetTitle = target,
                    linkType = updated.type,
                    weight = updated.weight,
                    description = updated.description
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update memory link", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update memory link: ${e.message}"
            )
        }
    }

    private suspend fun executeDeleteMemoryLink(tool: AITool): ToolResult {
        val memoryRepository = getMemoryRepository(resolveActiveProfileId(tool))
        val linkId = tool.parameters.find { it.name == "link_id" }?.value?.toLongOrNull()
        val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value
        val targetTitle = tool.parameters.find { it.name == "target_title" }?.value
        val locatorLinkType = tool.parameters.find { it.name == "link_type" }?.value

        return try {
            val resolvedLinkId = if (linkId != null) {
                linkId
            } else {
                if (sourceTitle.isNullOrBlank() || targetTitle.isNullOrBlank()) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Provide link_id, or provide both source_title and target_title"
                    )
                }

                val sourceMemory = memoryRepository.findMemoryByTitle(sourceTitle)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Source memory not found with title: $sourceTitle"
                    )
                val targetMemory = memoryRepository.findMemoryByTitle(targetTitle)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Target memory not found with title: $targetTitle"
                    )

                val candidates = sourceMemory.links.filter {
                    it.target.target?.id == targetMemory.id &&
                        (locatorLinkType.isNullOrBlank() || it.type == locatorLinkType)
                }

                when {
                    candidates.isEmpty() -> {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "No matching link found"
                        )
                    }
                    candidates.size > 1 -> {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Multiple links matched. Provide link_id or a more specific link_type."
                        )
                    }
                    else -> candidates.first().id
                }
            }

            val deleted = memoryRepository.deleteLink(resolvedLinkId)
            if (!deleted) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to delete memory link with id: $resolvedLinkId"
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Successfully deleted memory link: $resolvedLinkId")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete memory link", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete memory link: ${e.message}"
            )
        }
    }

    private suspend fun buildResultData(
        memoryRepository: MemoryRepository,
        memories: List<Memory>,
        query: String,
        limit: Int,
        snapshotId: String? = null,
        snapshotCreated: Boolean = false,
        excludedBySnapshotCount: Int = 0
    ): MemoryQueryResultData = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val isWildcardQuery = query.trim() == "*"
        
        val memoryInfos = memories.map { memory ->
            val content: String
            val chunkInfo: String?
            val chunkIndices: List<Int>?
            
            if (memory.isDocumentNode) {
                // 对于文档节点，执行"二次探查"，获取匹配的区块内容
                AppLogger.d(TAG, "Memory result is a document ('${memory.title}'). Fetching specific matching chunks for query: '$query'")
                val matchingChunks = memoryRepository.searchChunksInDocument(memory.id, query, limit)
                val totalChunks = memoryRepository.getTotalChunkCount(memory.id)

                if (matchingChunks.isNotEmpty()) {
                    // 收集分块索引（使用1-based显示）
                    chunkIndices = matchingChunks.map { it.chunkIndex }
                    
                    // 生成分块信息摘要
                    chunkInfo = if (matchingChunks.size == 1) {
                        "Chunk ${matchingChunks[0].chunkIndex + 1}/$totalChunks"
                    } else {
                        "Chunks ${matchingChunks.map { it.chunkIndex + 1 }.take(5).joinToString(", ")}/$totalChunks"
                    }
                    
                    if (isWildcardQuery || limit > 20) {
                        // 截断模式：只显示文档标题和分块信息
                        content = "Document: ${memory.title} ($totalChunks chunks)"
                    } else {
                        // 将匹配的区块内容拼接起来，每个区块显示编号
                        content = "Document: ${memory.title}\n" +
                            matchingChunks.take(5) // 最多取5个最相关的区块
                                .joinToString("\n---\n") { chunk -> 
                                    "Chunk ${chunk.chunkIndex + 1}/$totalChunks:\n${chunk.content}"
                                }
                    }
                } else {
                    // 如果二次探查未找到（理论上很少见，因为全局搜索已经认为它相关），提供一个回退信息
                    chunkInfo = null
                    chunkIndices = null
                    if (isWildcardQuery || limit > 20) {
                        content = "Document: ${memory.title}"
                    } else {
                        content = "Document '${memory.title}' was found, but no specific chunks matched the query '$query'. The document's general content is: ${memory.content}"
                    }
                }
            } else {
                // 对于普通记忆，只有通配查询时返回短摘要，其余始终返回完整内容
                chunkInfo = null
                chunkIndices = null
                content = if (isWildcardQuery) {
                    val summary = memory.content.take(10)
                    if (memory.content.length > 10) "$summary..." else summary
                } else {
                    memory.content
                }
            }

            MemoryQueryResultData.MemoryInfo(
                title = memory.title,
                content = content,
                source = memory.source,
                tags = memory.tags.map { it.name },
                createdAt = sdf.format(memory.createdAt),
                chunkInfo = chunkInfo,
                chunkIndices = chunkIndices
            )
        }
        MemoryQueryResultData(
            memories = memoryInfos,
            snapshotId = snapshotId,
            snapshotCreated = snapshotCreated,
            excludedBySnapshotCount = excludedBySnapshotCount
        )
    }


    override fun validateParameters(tool: AITool): ToolValidationResult {
        if (tool.name == "query_memory") {
            val query = tool.parameters.find { it.name == "query" }?.value
            if (query.isNullOrBlank()) {
                return ToolValidationResult(valid = false, errorMessage = "Missing or empty required parameter: query")
            }
        }
        return ToolValidationResult(valid = true)
    }
}

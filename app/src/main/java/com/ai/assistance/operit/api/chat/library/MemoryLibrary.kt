package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.preferences.MemorySpaceProfileDocumentRepository
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记忆库管理类 - 提供分析对话内容并存储为结构化记忆图谱的功能。
 */
object MemoryLibrary {
    private const val TAG = "MemoryLibrary"
    private const val DEFAULT_ANALYSIS_HISTORY_MESSAGE_COUNT = 10
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apiPreferences: ApiPreferences? = null
    private val mutex = Mutex()

    @Volatile private var isInitialized = false

    internal data class ParsedLink(
        val sourceTitle: String,
        val targetTitle: String,
        val type: String,
        val description: String,
        val weight: Float
    )

    internal data class ParsedEntity(
        val title: String,
        val content: String,
        val tags: List<String>,
        val aliasFor: String?,
        val folderPath: String
    )

    internal data class ParsedUpdate(
        val titleToUpdate: String,
        val newContent: String,
        val reason: String,
        val newCredibility: Float?,
        val newImportance: Float?
    )

    internal data class ParsedMerge(
        val sourceTitles: List<String>,
        val newTitle: String,
        val newContent: String,
        val newTags: List<String>,
        val folderPath: String,
        val reason: String
    )

    internal data class ParsedAnalysis(
        val mainProblem: ParsedEntity?,
        val extractedEntities: List<ParsedEntity> = emptyList(),
        val links: List<ParsedLink> = emptyList(),
        val updatedEntities: List<ParsedUpdate> = emptyList(),
        val mergedEntities: List<ParsedMerge> = emptyList(),
        val profileMarkdown: String? = null
    )


    fun initialize(context: Context) {
        synchronized(MemoryLibrary::class.java) {
            if (isInitialized) return
            AppLogger.d(TAG, "正在初始化 MemoryLibrary")
            apiPreferences = ApiPreferences.getInstance(context.applicationContext)
            isInitialized = true
            AppLogger.d(TAG, "MemoryLibrary 初始化完成")
        }
    }

    /**
     * 自动为未分类的记忆分配文件夹路径
     * 在后台异步执行，不阻塞主线程
     */
    fun autoCategorizeMemoriesAsync(context: Context, aiService: AIService) {
        ensureInitialized(context)
        
        coroutineScope.launch {
            try {
                autoCategorizeMemories(context, aiService)
            } catch (e: Exception) {
                AppLogger.e(TAG, "自动分类记忆失败", e)
            }
        }
    }

    fun saveMemoryAsync(
            context: Context,
            toolHandler: AIToolHandler,
            conversationHistory: List<Pair<String, String>>,
            content: String,
            aiService: AIService,
            profileIdOverride: String? = null,
            onSuccess: (suspend () -> Unit)? = null,
            onError: (suspend (Exception) -> Unit)? = null
    ) {
        ensureInitialized(context)

        coroutineScope.launch {
            try {
                saveMemoryNow(
                    context,
                    toolHandler,
                    conversationHistory,
                    content,
                    aiService,
                    profileIdOverride = profileIdOverride
                )
                onSuccess?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "保存记忆失败", e)
                onError?.invoke(e)
            }
        }
    }

    suspend fun saveMemoryNow(
        context: Context,
        toolHandler: AIToolHandler,
        conversationHistory: List<Pair<String, String>>,
        content: String,
        aiService: AIService,
        profileIdOverride: String? = null
    ) {
        saveMemory(
            context = context,
            toolHandler = toolHandler,
            conversationHistory = conversationHistory,
            content = content,
            aiService = aiService,
            profileIdOverride = profileIdOverride,
            analysisHistoryLimit = DEFAULT_ANALYSIS_HISTORY_MESSAGE_COUNT,
            propagateAnalysisFailure = true
        )
    }

    suspend fun saveMemoryWindowNow(
        context: Context,
        toolHandler: AIToolHandler,
        conversationHistory: List<Pair<String, String>>,
        content: String,
        aiService: AIService,
        profileIdOverride: String?,
        analysisHistoryLimit: Int
    ) {
        require(analysisHistoryLimit > 0) { "Analysis history limit must be positive" }
        ensureInitialized(context)
        saveMemory(
            context = context,
            toolHandler = toolHandler,
            conversationHistory = conversationHistory,
            content = content,
            aiService = aiService,
            profileIdOverride = profileIdOverride,
            analysisHistoryLimit = analysisHistoryLimit,
            propagateAnalysisFailure = true
        )
    }

    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            initialize(context)
        }
    }

    /**
     * 查询未分类记忆并批量调用 AI 进行分类
     */
    private suspend fun autoCategorizeMemories(context: Context, aiService: AIService) {
        mutex.withLock {
            val profileId = preferencesManager.activeMemorySpaceIdFlow.first()
            val memoryRepository = MemoryRepository(context, profileId)
            
            // 使用 searchMemories("") 获取所有记忆，然后过滤未分类的
            val allMemories = memoryRepository.searchMemories("")
            val uncategorizedMemories = allMemories.filter { memory ->
                memory.folderPath.isNullOrEmpty()
            }
            
            if (uncategorizedMemories.isEmpty()) {
                AppLogger.d(TAG, "没有未分类的记忆，跳过自动分类")
                return@withLock
            }
            
            AppLogger.d(TAG, "找到 ${uncategorizedMemories.size} 条未分类记忆，开始批量分类...")
            
            // 获取现有文件夹列表
            val existingFolders = memoryRepository.getAllFolderPaths()
            
            // 分批处理（每批10条）
            val batches = uncategorizedMemories.chunked(10)
            batches.forEachIndexed { batchIndex: Int, batch: List<Memory> ->
                try {
                    AppLogger.d(TAG, "处理第 ${batchIndex + 1} 批记忆（共 ${batch.size} 条）...")
                    categorizeBatch(context, batch, existingFolders, memoryRepository, aiService)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "处理第 ${batchIndex + 1} 批记忆失败", e)
                }
            }
            
            AppLogger.d(TAG, "自动分类完成")
        }
    }

    /**
     * 使用 AI 为一批记忆分类
     */
    private suspend fun categorizeBatch(
        context: Context,
        memories: List<Memory>,
        existingFolders: List<String>,
        repository: MemoryRepository,
        aiService: AIService
    ) {
        val useEnglish = !LocaleUtils.usesChineseContent(context)
        val memoriesDigest = memories.joinToString("\n") { "- title: ${it.title}, content: ${it.content.take(100)}..." }
        val systemPrompt = FunctionalPrompts.buildMemoryAutoCategorizePrompt(
            existingFolders = existingFolders,
            memoriesDigest = memoriesDigest,
            useEnglish = useEnglish
        )

        val userMessage = FunctionalPrompts.memoryAutoCategorizeUserMessage(useEnglish)
            val messages = listOf(Pair("system", systemPrompt), Pair("user", userMessage)).toPromptTurns()
        val result = StringBuilder()
        
        withContext(Dispatchers.IO) {
            val stream =
                aiService.sendMessage(
                    context = context,
                    chatHistory = messages,
                )
            stream.collect { content -> result.append(content) }
        }

        // 解析 AI 返回的 JSON 并更新记忆
        parseAndApplyCategorization(result.toString(), memories, repository)
    }

    /**
     * 解析 AI 返回的分类结果并更新记忆
     */
    private suspend fun parseAndApplyCategorization(
        jsonString: String,
        memories: List<Memory>,
        repository: MemoryRepository
    ) {
        try {
            val cleanJson = ChatUtils.extractJsonArray(jsonString)
            if (cleanJson.isEmpty() || !cleanJson.startsWith("[")) return
            
            val jsonArray = JSONArray(cleanJson)
            val titleToFolderMap = mutableMapOf<String, String>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val folder = obj.getString("folder")
                titleToFolderMap[title] = folder
            }
            
            // 为每个记忆更新分类和重新生成 embedding
            memories.forEach { memory ->
                val newFolder = titleToFolderMap[memory.title]
                if (newFolder != null) {
                    AppLogger.d(TAG, "更新记忆 '${memory.title}' 的分类为: $newFolder")
                    
                    // 直接调用 updateMemory，它会自动重新生成 embedding
                    repository.updateMemory(
                        memory = memory,
                        newTitle = memory.title,
                        newContent = memory.content,
                        newFolderPath = newFolder
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析分类结果失败: $jsonString", e)
        }
    }

    /**
     * Analyzes conversation and saves it as a structured Memory graph.
     */
    private suspend fun saveMemory(
            context: Context,
            toolHandler: AIToolHandler,
            conversationHistory: List<Pair<String, String>>,
            content: String,
            aiService: AIService,
            profileIdOverride: String? = null,
            analysisHistoryLimit: Int,
            propagateAnalysisFailure: Boolean = false
    ) {
        mutex.withLock {
            val profileId = profileIdOverride ?: preferencesManager.activeMemorySpaceIdFlow.first()
            val memoryRepository = MemoryRepository(context, profileId)

            // Tool output and assistant reasoning are execution traces, not durable facts.
            // Keeping them here makes long windows repeat noise and obscures the actual outcome.
            val prunedContent =
                ChatUtils.removeThinkingContent(
                    ChatUtils.stripGeminiThoughtSignatureMeta(
                        pruneToolResultContent(context, content)
                    )
                )

            // Process conversation history: remove system messages and clean user messages
            val processedHistory = conversationHistory
                .filter { it.first != "system" }
                .map { (role, msgContent) ->
                    val cleanedContent = if (role == "user") {
                        msgContent.replace(Regex("<memory>.*?</memory>", RegexOption.DOT_MATCHES_ALL), "").trim()
                    } else {
                        msgContent
                    }
                    role to ChatUtils.removeThinkingContent(
                        ChatUtils.stripGeminiThoughtSignatureMeta(
                            pruneToolResultContent(context, cleanedContent)
                        )
                    )
                }

            if (processedHistory.isEmpty()) {
                AppLogger.w(TAG, "处理后的会話历史为空，跳过保存记忆")
                return@withLock
            }

            val query = processedHistory.lastOrNull { it.first == "user" }?.second ?: ""
            if (query.isEmpty()) {
                AppLogger.w(TAG, "未找到用户查询消息，跳过保存")
                return@withLock
            }

            // Generate the graph analysis from the conversation
            val analysis = generateAnalysis(
                context = context,
                aiService = aiService,
                query = query,
                solution = prunedContent,
                conversationHistory = processedHistory,
                memoryRepository = memoryRepository,
                profileId = profileId,
                analysisHistoryLimit = analysisHistoryLimit,
                propagateFailure = propagateAnalysisFailure
            )

            analysis.profileMarkdown?.let { markdown ->
                try {
                    val saved = MemorySpaceProfileDocumentRepository.getInstance(context)
                        .saveAutomatic(profileId, markdown)
                    if (saved) AppLogger.d(TAG, "记忆空间资料已自动更新: profileId=$profileId")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e(TAG, "自动更新记忆空间资料失败: profileId=$profileId", error)
                }
            }

            // If analysis is empty (trivial conversation), abort early.
            if (analysis.mainProblem == null && analysis.extractedEntities.isEmpty() &&
                analysis.updatedEntities.isEmpty() && analysis.mergedEntities.isEmpty() &&
                analysis.links.isEmpty() &&
                analysis.profileMarkdown == null
            ) {
                AppLogger.d(TAG, "分析结果为空，判断为无需记忆的对话，跳过保存。")
                return@withLock
            }

            // Create a map to track all memories (new and updated) for linking
            val createdMemories = mutableMapOf<String, Memory>()

            // First, apply any merges to existing memories
            if (analysis.mergedEntities.isNotEmpty()) {
                AppLogger.d(TAG, "开始合并 ${analysis.mergedEntities.size} 组记忆...")
                analysis.mergedEntities.forEach { merge ->
                    AppLogger.d(TAG, "正在合并: ${merge.sourceTitles.joinToString(", ")} -> '${merge.newTitle}'. 原因: ${merge.reason}")
                    val mergedMemory = memoryRepository.mergeMemories(
                        sourceTitles = merge.sourceTitles,
                        newTitle = merge.newTitle,
                        newContent = merge.newContent,
                        newTags = merge.newTags,
                        folderPath = merge.folderPath
                    )
                    if (mergedMemory != null) {
                        createdMemories[mergedMemory.title] = mergedMemory
                    }
                }
            }

            // Second, apply any updates to existing memories
            if (analysis.updatedEntities.isNotEmpty()) {
                AppLogger.d(TAG, "开始更新 ${analysis.updatedEntities.size} 个现有记忆...")
                analysis.updatedEntities.forEach { update ->
                    val memoryToUpdate = memoryRepository.findMemoryByTitle(update.titleToUpdate)
                    if (memoryToUpdate != null) {
                        AppLogger.d(TAG, "正在更新记忆: '${update.titleToUpdate}'. 原因: ${update.reason}")
                        val updatedMemory = memoryRepository.updateMemory(
                                memory = memoryToUpdate,
                                newTitle = memoryToUpdate.title, // For now, let's not change the title
                                newContent = update.newContent,
                                newCredibility = update.newCredibility ?: memoryToUpdate.credibility,
                                newImportance = update.newImportance ?: memoryToUpdate.importance
                        )
                        if (updatedMemory != null) {
                            createdMemories[updatedMemory.title] = updatedMemory
                        }
                    } else {
                        AppLogger.w(TAG, "想要更新的记忆未找到: '${update.titleToUpdate}'")
                    }
                }
            }

            AppLogger.d(TAG, "开始构建记忆图谱...")
            AppLogger.d(
                TAG,
                "AI分析结果 - 主要事件: '${analysis.mainProblem?.title ?: "无"}', " +
                    "实体: ${analysis.extractedEntities.size}, 链接: ${analysis.links.size}"
            )


            try {
                // 1. Create main problem memory
                val mainProblemMemory = analysis.mainProblem?.let { mainProblem ->
                    val existingMemory = memoryRepository.findMemoryByTitle(mainProblem.title)
                    if (existingMemory != null) {
                        AppLogger.d(TAG, "1. 发现同名核心记忆，更新内容: '${mainProblem.title}'")
                        existingMemory.content = mainProblem.content
                        memoryRepository.saveMemory(existingMemory)
                        existingMemory
                    } else {
                        AppLogger.d(TAG, "1. 创建主要问题记忆节点: '${mainProblem.title}'")
                        val memory = Memory(
                            title = mainProblem.title,
                            content = mainProblem.content,
                            importance = 0.8f, // Main problems are highly important
                            credibility = 1.0f,
                            folderPath = mainProblem.folderPath
                        )
                        memoryRepository.saveMemory(memory)
                        mainProblem.tags.forEach { tagName ->
                            memoryRepository.addTagToMemory(memory, tagName)
                        }
                        memory
                    }
                }
                mainProblemMemory?.let {
                    createdMemories[it.title] = it
                }

                // 2. Process entities with new LLM-driven deduplication logic
                analysis.extractedEntities.forEach { entity ->
                    AppLogger.d(TAG, "2. 处理实体: '${entity.title}'")
                    var memory: Memory? = null

                    if (!entity.aliasFor.isNullOrBlank()) {
                        // This entity is an alias for an existing one, as determined by the LLM.
                        AppLogger.d(TAG, "   -> LLM 识别此实体为 '${entity.aliasFor}' 的别名。")
                        // Try to find the canonical memory, first in the ones we just created, then in the DB.
                        memory = createdMemories[entity.aliasFor] ?: memoryRepository.findMemoryByTitle(entity.aliasFor)

                        if (memory != null) {
                            AppLogger.d(TAG, "   -> 复用已存在的记忆节点 (ID: ${memory.id}).")
                        } else {
                            // This is an edge case: LLM said it's an alias, but we can't find the original.
                            // We will treat it as a new entity.
                            AppLogger.w(TAG, "   -> 无法找到别名 '${entity.aliasFor}' 的原始记忆。将其作为新实体处理。")
                        }
                    }

                    // If it's not an alias, or if the original for the alias wasn't found, create a new memory.
                    if (memory == null) {
                        AppLogger.d(TAG, "   -> 创建新的记忆节点。")
                        memory = Memory(
                            title = entity.title,
                            content = entity.content,
                            source = "memory_analysis",
                            folderPath = entity.folderPath
                        )
                        memoryRepository.saveMemory(memory)
                        entity.tags.forEach { tagName ->
                            memoryRepository.addTagToMemory(memory, tagName)
                        }
                    }

                    // Map the title of the entity (whether it's an alias or new) to the resolved memory object.
                    // This ensures that links pointing to the alias title will resolve to the correct canonical memory.
                    createdMemories[entity.title] = memory
                }

                // 3. Create links between the memories
                AppLogger.d(TAG, "3. 开始创建记忆链接...")
                analysis.links.forEach { link ->
                    // Try to find source: first in newly created/updated memories, then in existing DB
                    val source = createdMemories[link.sourceTitle] 
                        ?: memoryRepository.findMemoryByTitle(link.sourceTitle)
                    
                    // Try to find target: first in newly created/updated memories, then in existing DB
                    val target = createdMemories[link.targetTitle] 
                        ?: memoryRepository.findMemoryByTitle(link.targetTitle)
                    
                    if (source != null && target != null) {
                        AppLogger.d(TAG, "   -> 正在链接: '${link.sourceTitle}' --(${link.type}, weight=${link.weight})--> '${link.targetTitle}'")
                        memoryRepository.linkMemories(source, target, link.type, weight = link.weight, description = link.description)
                    } else {
                        AppLogger.w(TAG, "   -> 无法创建链接，源或目标实体未找到: ${link.sourceTitle} -> ${link.targetTitle}")
                        if (source == null) AppLogger.w(TAG, "      源节点 '${link.sourceTitle}' 未找到")
                        if (target == null) AppLogger.w(TAG, "      目标节点 '${link.targetTitle}' 未找到")
                    }
                }

                AppLogger.d(TAG, "成功从对话中提取并保存了记忆图谱")

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "保存记忆图谱失败", e)
                if (propagateAnalysisFailure) {
                    throw e
                }
            }
        }
    }

    /**
     * Generates a structured analysis of the conversation for graph creation.
     */
    private suspend fun generateAnalysis(
        context: Context,
        aiService: AIService,
        query: String,
        solution: String,
        conversationHistory: List<Pair<String, String>>,
        memoryRepository: MemoryRepository,
        profileId: String,
        analysisHistoryLimit: Int,
        propagateFailure: Boolean
    ): ParsedAnalysis {
        try {
            val useEnglish = !LocaleUtils.usesChineseContent(context)
            val profileDocumentRepository =
                MemorySpaceProfileDocumentRepository.getInstance(context)
            profileDocumentRepository.initialize()
            val memorySpace = preferencesManager.getMemorySpaceFlow(profileId).first()
            val profileUpdateEnabled =
                memorySpace.profileAutoUpdateEnabled && !memorySpace.profileAutoUpdateLocked
            val profileDocument =
                if (profileUpdateEnabled) profileDocumentRepository.load(profileId) else ""
            // --- Hybrid Strategy: Local rough search + LLM final decision ---
            // 1. Use a compact search query (question-focused) for rough candidate selection.
            val contextQuery = buildCandidateSearchQuery(query, solution, conversationHistory)
            val memorySearchSettings = MemorySearchSettingsPreferences(context, profileId)
            val searchConfig = memorySearchSettings.load()
            val memoryExtractionCustomRules =
                memorySearchSettings.loadMemoryExtractionCustomRules()
            val candidateMemories = memoryRepository.searchMemories(
                query = contextQuery,
                scoreMode = searchConfig.scoreMode,
                keywordWeight = searchConfig.keywordWeight,
                tagWeight = searchConfig.tagWeight,
                semanticWeight = searchConfig.vectorWeight,
                edgeWeight = searchConfig.edgeWeight
            ).take(15)

            AppLogger.d(
                TAG,
                "候选记忆检索完成: count=${candidateMemories.size}, " +
                    "mode=${searchConfig.scoreMode}, " +
                    "keywordWeight=${searchConfig.keywordWeight}, tagWeight=${searchConfig.tagWeight}, vectorWeight=${searchConfig.vectorWeight}, edgeWeight=${searchConfig.edgeWeight}, " +
                    "searchQueryLen=${contextQuery.length}"
            )
            AppLogger.d(TAG, "候选检索查询（截断）: ${contextQuery.take(220)}")
            if (candidateMemories.isEmpty()) {
                AppLogger.d(TAG, "候选记忆列表为空（通过阈值过滤后无结果）。")
            } else {
                candidateMemories.forEachIndexed { index, memory ->
                    val preview = memory.content
                        .replace("\r\n", " ")
                        .replace("\n", " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(120)
                    AppLogger.d(
                        TAG,
                        "候选记忆[$index] id=${memory.id}, title='${memory.title}', " +
                            "folder='${memory.folderPath ?: ""}', importance=${String.format("%.2f", memory.importance)}, " +
                            "credibility=${String.format("%.2f", memory.credibility)}, preview='$preview'"
                    )
                }
            }

            // 2. Proactively find duplicates among candidates and instruct LLM to merge them
            val duplicatesPromptPart = findAndDescribeDuplicates(candidateMemories, memoryRepository, useEnglish)

            val existingMemoriesPrompt = if (candidateMemories.isNotEmpty()) {
                FunctionalPrompts.knowledgeGraphExistingMemoriesPrefix(useEnglish) +
                    candidateMemories.joinToString("\n") { "- \"${it.title}\": ${it.content.take(150).replace("\n", " ")}..." }
            } else {
                FunctionalPrompts.knowledgeGraphNoExistingMemoriesMessage(useEnglish)
            }

            // 获取现有文件夹列表
            val existingFolders = memoryRepository.getAllFolderPaths()
            val existingFoldersPrompt = FunctionalPrompts.knowledgeGraphExistingFoldersPrompt(
                existingFolders = existingFolders,
                useEnglish = useEnglish
            )

            val systemPrompt = FunctionalPrompts.buildKnowledgeGraphExtractionPrompt(
                duplicatesPromptPart = duplicatesPromptPart,
                existingMemoriesPrompt = existingMemoriesPrompt,
                existingFoldersPrompt = existingFoldersPrompt,
                useEnglish = useEnglish,
                profileDocument = profileDocument,
                profileUpdateEnabled = profileUpdateEnabled,
                memoryExtractionCustomRules = memoryExtractionCustomRules
            )

            val analysisMessage = buildAnalysisMessage(
                context = context,
                query = query,
                solution = solution,
                conversationHistory = conversationHistory,
                useEnglish = useEnglish,
                historyLimit = analysisHistoryLimit
            )
            val messages = listOf(Pair("system", systemPrompt), Pair("user", analysisMessage)).toPromptTurns()
            val result = StringBuilder()

            withContext(Dispatchers.IO) {
                val stream =
                    aiService.sendMessage(
                        context = context,
                        chatHistory = messages,
                    )
                stream.collect { content -> result.append(content) }
            }

            return parseAnalysisResult(ChatUtils.removeThinkingContent(result.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成分析失败", e)
            if (propagateFailure) {
                throw e
            }
            return ParsedAnalysis(null)
        }
    }

    private fun buildCandidateSearchQuery(
        query: String,
        solution: String,
        conversationHistory: List<Pair<String, String>>
    ): String {
        val coreQuestion = extractCoreQuestionText(query)
        val fallbackQuestion = normalizeCandidateSearchText(query, maxLen = 800)

        val selectedQuestion = if (coreQuestion.isNotBlank()) coreQuestion else fallbackQuestion
        val conciseSolution = normalizeCandidateSearchText(solution, maxLen = 180)
        val recentWindowContext =
            normalizeCandidateSearchText(
                raw = conversationHistory.takeLast(12).joinToString("\n") { (_, content) -> content },
                maxLen = 1200
            )

        // A short final question such as "try ls" lacks the subject of the window.
        // Include a bounded recent context so retrieval can distinguish SSH facts from unrelated tools.
        return listOf(selectedQuestion, conciseSolution, recentWindowContext)
            .filter(String::isNotBlank)
            .joinToString("\n")
    }

    private fun extractCoreQuestionText(rawQuery: String): String {
        val compact = rawQuery.replace("\r\n", "\n")

        val cn = Regex("(?s)问题\\s*[：:]\\s*(.+?)(?:\\n\\s*解决方案\\s*[：:]|\\z)")
            .find(compact)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        val en = Regex("(?s)Question\\s*:\\s*(.+?)(?:\\n\\s*Solution\\s*:|\\z)")
            .find(compact)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        val selected = when {
            !cn.isNullOrBlank() -> cn
            !en.isNullOrBlank() -> en
            else -> compact
        }

        val filtered = selected
            .lineSequence()
            .filterNot { it.trimStart().startsWith("历史记录:") }
            .filterNot { it.trimStart().startsWith("History:") }
            .joinToString("\n")

        return normalizeCandidateSearchText(filtered, maxLen = 500)
    }

    private fun normalizeCandidateSearchText(raw: String, maxLen: Int): String {
        return raw
            .replace(ChatMarkupRegex.toolTag, " ")
            .replace(ChatMarkupRegex.toolSelfClosingTag, " ")
            .replace(ChatMarkupRegex.toolResultTag, " ")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, " ")
            .replace(ChatMarkupRegex.statusTag, " ")
            .replace(ChatMarkupRegex.statusSelfClosingTag, " ")
            .replace(ChatMarkupRegex.thinkTag, " ")
            .replace(ChatMarkupRegex.thinkSelfClosingTag, " ")
            .replace(ChatMarkupRegex.searchTag, " ")
            .replace(ChatMarkupRegex.searchSelfClosingTag, " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[`*_#>]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLen)
    }

    /**
     * Finds duplicates within a list of candidate memories and creates a prompt instruction for the LLM.
     */
    private suspend fun findAndDescribeDuplicates(candidateMemories: List<Memory>, memoryRepository: MemoryRepository, useEnglish: Boolean): String {
        val titles = candidateMemories.map { it.title }.distinct()
        val duplicatesFound = mutableListOf<String>()

        for (title in titles) {
            val memoriesWithSameTitle = memoryRepository.findMemoriesByTitle(title)
            if (memoriesWithSameTitle.size > 1) {
                duplicatesFound.add(
                    FunctionalPrompts.knowledgeGraphDuplicateTitleInstruction(
                        title = title,
                        count = memoriesWithSameTitle.size,
                        useEnglish = useEnglish
                    )
                )
            }
        }

        return if (duplicatesFound.isNotEmpty()) {
            FunctionalPrompts.knowledgeGraphDuplicateHeader(useEnglish) + duplicatesFound.joinToString("\n") + "\n"
        } else {
            ""
        }
    }

    private fun buildAnalysisMessage(
            context: Context,
            query: String,
            solution: String,
            conversationHistory: List<Pair<String, String>>,
            useEnglish: Boolean,
            historyLimit: Int
    ): String {
        val messageBuilder = StringBuilder()
        if (useEnglish) {
            messageBuilder.appendLine("Question:")
            messageBuilder.appendLine(query)
            messageBuilder.appendLine()
            messageBuilder.appendLine("Solution:")
            messageBuilder.appendLine(solution.take(3000))
            messageBuilder.appendLine()
        } else {
            messageBuilder.appendLine(context.getString(R.string.memory_analysis_question))
            messageBuilder.appendLine(query)
            messageBuilder.appendLine()
            messageBuilder.appendLine(context.getString(R.string.memory_analysis_solution))
            messageBuilder.appendLine(solution.take(3000))
            messageBuilder.appendLine()
        }
        val recentHistory = conversationHistory.takeLast(historyLimit)
        if (recentHistory.isNotEmpty()) {
            messageBuilder.appendLine(if (useEnglish) "History:" else context.getString(R.string.memory_analysis_history))
            recentHistory.forEachIndexed { index, (role, content) ->
                messageBuilder.appendLine("#${index + 1} $role: ${content.take(4000)}")
            }
        }
        return messageBuilder.toString()
    }

    /** Parses the object-based memory graph protocol returned by the AI. */
    internal fun parseAnalysisResult(jsonString: String): ParsedAnalysis {
        val cleanJson = ChatUtils.extractJson(jsonString)
        require(cleanJson.isNotEmpty() && cleanJson.startsWith("{")) {
            "Memory analysis must return a JSON object"
        }
        if (cleanJson == "{}") return ParsedAnalysis(null)

        val json = JSONObject(cleanJson)
        AppLogger.d(TAG, "AI 返回的完整 JSON 指令:\n${json.toString(2)}")

        return ParsedAnalysis(
            mainProblem = json.requiredNullableObject("main")?.toParsedEntity("main", aliasAllowed = false),
            extractedEntities = json.requiredObjectArray("new").mapIndexed { index, entity ->
                entity.toParsedEntity("new[$index]", aliasAllowed = true)
            },
            updatedEntities = json.requiredObjectArray("update").mapIndexed { index, update ->
                update.toParsedUpdate("update[$index]")
            },
            mergedEntities = json.requiredObjectArray("merge").mapIndexed { index, merge ->
                merge.toParsedMerge("merge[$index]")
            },
            links = json.requiredObjectArray("links").mapIndexed { index, link ->
                link.toParsedLink("links[$index]")
            },
            profileMarkdown = json.optionalString("profile_markdown")
        )
    }

    private fun JSONObject.requiredNullableObject(key: String): JSONObject? = when {
        !has(key) -> throw IllegalArgumentException("Missing required field: $key")
        isNull(key) -> null
        else -> getJSONObject(key)
    }

    private fun JSONObject.requiredObjectArray(key: String): List<JSONObject> {
        require(has(key)) { "Missing required field: $key" }
        val values = getJSONArray(key)
        return List(values.length()) { index -> values.getJSONObject(index) }
    }

    private fun JSONObject.optionalString(key: String): String? = when {
        !has(key) || isNull(key) -> null
        else -> getString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.requiredString(key: String, context: String): String =
        getString(key).takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("$context.$key must not be blank")

    private fun JSONObject.requiredStringArray(key: String, context: String): List<String> {
        val values = getJSONArray(key)
        return List(values.length()) { index ->
            values.getString(index).takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("$context.$key[$index] must not be blank")
        }
    }

    private fun JSONObject.optionalUnitFloat(key: String, context: String): Float? {
        if (!has(key) || isNull(key)) return null
        val value = getDouble(key).toFloat()
        require(value in 0f..1f) { "$context.$key must be between 0.0 and 1.0" }
        return value
    }

    private fun JSONObject.toParsedEntity(context: String, aliasAllowed: Boolean): ParsedEntity =
        ParsedEntity(
            title = requiredString("title", context),
            content = requiredString("content", context),
            tags = requiredStringArray("tags", context),
            aliasFor =
                if (aliasAllowed) optionalString("alias_for") else null,
            folderPath = requiredString("folder_path", context)
        )

    private fun JSONObject.toParsedUpdate(context: String): ParsedUpdate =
        ParsedUpdate(
            titleToUpdate = requiredString("title", context),
            newContent = requiredString("content", context),
            reason = requiredString("reason", context),
            newCredibility = optionalUnitFloat("credibility", context),
            newImportance = optionalUnitFloat("importance", context)
        )

    private fun JSONObject.toParsedMerge(context: String): ParsedMerge =
        ParsedMerge(
            sourceTitles = requiredStringArray("source_titles", context),
            newTitle = requiredString("title", context),
            newContent = requiredString("content", context),
            newTags = requiredStringArray("tags", context),
            folderPath = requiredString("folder_path", context),
            reason = requiredString("reason", context)
        )

    private fun JSONObject.toParsedLink(context: String): ParsedLink =
        ParsedLink(
            sourceTitle = requiredString("source", context),
            targetTitle = requiredString("target", context),
            type = requiredString("type", context),
            description = requiredString("description", context),
            weight = requireNotNull(optionalUnitFloat("weight", context)) {
                "$context.weight is required"
            }
        )

    /**
     * Replaces the content of <tool_result> tags with a placeholder to reduce token count.
     */
    private fun pruneToolResultContent(context: Context, message: String): String {
        return ChatMarkupRegex.pruneToolResultContentPattern.replace(message) { matchResult ->
            val attributes = matchResult.groupValues[1]
            context.getString(R.string.memory_tool_result_pruned, attributes)
        }
    }

}

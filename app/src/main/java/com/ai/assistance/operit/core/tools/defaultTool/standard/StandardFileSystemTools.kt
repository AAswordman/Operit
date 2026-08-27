package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileApplyResultData
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.core.tools.GrepResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.util.PathMapper
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.HttpMultiPartDownloader
import com.ai.assistance.operit.util.FFmpegUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.api.chat.enhance.FileBindingService
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.ripgrep.NativeRipgrep
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Collection of file system operation tools for the AI assistant These tools use Java File APIs for
 * file operations
 */
open class StandardFileSystemTools(protected val context: Context) {
    companion object {
        protected const val TAG = "FileSystemTools"
        // 特殊文件类型扩展名列表（需要特殊处理提取文本的文件类型）
        protected val SPECIAL_FILE_EXTENSIONS = listOf(
            "doc", "docx",      // Word documents
            "pdf",              // PDF documents
            "jpg", "jpeg",      // Image files
            "png", "gif", "bmp",
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus",
            "mp4", "mkv", "mov", "webm", "avi", "m4v"
        )
    }

    // ApiPreferences 实例，用于动态获取配置
    protected val apiPreferences: ApiPreferences by lazy {
        ApiPreferences.getInstance(context)
    }

    // SSH文件管理器（单例，懒加载）
    private val sshFileManager by lazy {
        SSHFileConnectionManager.getInstance(context)
    }

    // TerminalManager（单例，懒加载）
    private val terminalManager by lazy {
        TerminalManager.getInstance(context)
    }

    private var lastLinuxFileSystemProviderLabel: String? = null

    // Linux文件系统提供者，优先使用SSH连接，否则从TerminalManager获取
    protected fun getLinuxFileSystem(): FileSystemProvider {
        // 先尝试获取SSH连接的文件系统
        val sshProvider = sshFileManager.getFileSystemProvider()
        
        // 如果SSH已登录，使用SSH文件系统
        if (sshProvider != null) {
            if (lastLinuxFileSystemProviderLabel != "ssh") {
                AppLogger.d(TAG, "Using SSH file system provider")
                lastLinuxFileSystemProviderLabel = "ssh"
            }
            return sshProvider
        }
        
        // 否则使用本地Terminal的文件系统
        if (lastLinuxFileSystemProviderLabel != "local") {
            AppLogger.d(TAG, "Using local terminal file system provider")
            lastLinuxFileSystemProviderLabel = "local"
        }
        return terminalManager.getFileSystemProvider()
    }

    // Linux文件系统工具实例
    protected val linuxTools: LinuxFileSystemTools by lazy {
        LinuxFileSystemTools(context)
    }

    private val safTools: SafFileSystemTools by lazy {
        SafFileSystemTools(context, apiPreferences)
    }

    protected fun isSafEnvironment(environment: String?): Boolean {
        return environment?.startsWith("repo:", ignoreCase = true) == true
    }

    /** 检查是否是Linux环境 */
    protected fun isLinuxEnvironment(environment: String?): Boolean {
        return environment?.lowercase() == "linux"
    }

    protected data class GrepContextCandidate(
        val filePath: String,
        val lineNumber: Int,
        val lineContent: String,
        val matchContext: String?,
        val query: String,
        val round: Int
    )

    protected data class RipgrepBlock(
        val filePath: String,
        val firstMatchLine: Int,
        val lineContent: String,
        val matchContext: String,
        val matchCount: Int
    )

    private data class RipgrepQueryExecution(
        val index: Int,
        val query: String,
        val parsedBlocks: List<RipgrepBlock>
    )

    protected suspend fun getGrepService(): AIService {
        return EnhancedAIService.getAIServiceForFunction(context, FunctionType.GREP)
    }

    protected suspend fun getGrepModelParameters(): List<ModelParameter<*>> {
        val functionalConfigManager = FunctionalConfigManager(context)
        functionalConfigManager.initializeIfNeeded()
        val modelConfigManager = ModelConfigManager(context)
        val mapping = functionalConfigManager.getConfigMappingForFunction(FunctionType.GREP)
        return modelConfigManager.getModelParametersForConfig(mapping.configId)
    }

    protected suspend fun runGrepModel(prompt: String): String {
        val service = getGrepService()
        val modelParameters = getGrepModelParameters()
        val sb = StringBuilder()
        val stream =
            service.sendMessage(
                context = context,
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                modelParameters = modelParameters,
                enableThinking = false,
                stream = false,
                availableTools = null
            )
        stream.collect { chunk -> sb.append(chunk) }
        return sb.toString().trim()
    }

    protected fun extractFirstJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    protected fun parseQueryListFromModelOutput(text: String, fallback: List<String>): List<String> {
        val obj = extractFirstJsonObject(text) ?: return fallback
        val arr = obj.optJSONArray("queries") ?: return fallback
        val queries = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val q = arr.optString(i, "").trim()
            if (q.isNotBlank()) queries.add(q)
        }
        return if (queries.isEmpty()) fallback else queries
    }

    protected fun parseSelectedIdsFromModelOutput(text: String): List<Int> {
        val obj = extractFirstJsonObject(text) ?: return emptyList()
        val arr = obj.optJSONArray("selected") ?: return emptyList()
        val ids = mutableListOf<Int>()
        for (i in 0 until arr.length()) {
            val v = arr.optInt(i, -1)
            if (v >= 0) ids.add(v)
        }
        return ids
    }

    protected fun parseReadIdsFromModelOutput(text: String): List<Int> {
        val obj = extractFirstJsonObject(text) ?: return emptyList()
        val arr = obj.optJSONArray("read") ?: return emptyList()
        val ids = mutableListOf<Int>()
        for (i in 0 until arr.length()) {
            val v = arr.optInt(i, -1)
            if (v >= 0) ids.add(v)
        }
        return ids
    }

    protected fun normalizeQueries(queries: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (q in queries) {
            val trimmed = q.trim()
            if (trimmed.isNotBlank()) {
                val isDotPlaceholder = trimmed.length >= 3 && trimmed.all { it == '.' }
                val isEllipsisPlaceholder = trimmed.all { it == '…' }
                if (isDotPlaceholder || isEllipsisPlaceholder) continue
                seen.add(trimmed)
            }
        }
        return seen.toList()
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun clipGrepText(raw: String, maxChars: Int): String {
        val trimmed = raw.trim()
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars) + "...(truncated)"
    }

    private suspend fun searchNativeRipgrepBlocks(
        path: String,
        patterns: List<String>,
        filePattern: String,
        caseInsensitive: Boolean,
        contextLines: Int,
        maxResults: Int
    ): Pair<List<RipgrepBlock>, Int> =
        withContext(Dispatchers.IO) {
            val rawResult =
                NativeRipgrep.searchJson(
                    path = path,
                    patterns = patterns.toTypedArray(),
                    filePattern = filePattern,
                    caseInsensitive = caseInsensitive,
                    literal = false,
                    contextLines = contextLines.coerceAtLeast(0),
                    maxResults = maxResults.coerceAtLeast(0)
                )
            parseNativeRipgrepBlocks(rawResult)
        }

    private fun parseNativeRipgrepBlocks(output: String): Pair<List<RipgrepBlock>, Int> {
        val json = JSONObject(output)
        if (!json.optBoolean("success", false)) {
            throw IllegalStateException(json.optString("error").ifBlank { "native ripgrep search failed" })
        }

        val blocksJson = json.optJSONArray("blocks")
        val blocks = mutableListOf<RipgrepBlock>()
        if (blocksJson != null) {
            for (index in 0 until blocksJson.length()) {
                val block = blocksJson.optJSONObject(index) ?: continue
                blocks.add(
                    RipgrepBlock(
                        filePath = block.optString("filePath"),
                        firstMatchLine = block.optInt("firstMatchLine", 0),
                        lineContent = block.optString("lineContent"),
                        matchContext = block.optString("matchContext"),
                        matchCount = block.optInt("matchCount", 0)
                    )
                )
            }
        }
        return Pair(blocks, json.optInt("filesSearched", 0))
    }

    protected fun buildCandidateDigestForModel(
        candidates: List<GrepContextCandidate>,
        maxCharsPerItem: Int
    ): String {
        if (candidates.isEmpty()) return "(no matches)"
        val sb = StringBuilder()
        candidates.forEachIndexed { idx, c ->
            sb.append("#").append(idx)
                .append(" file=").append(c.filePath)
                .append(" line=").append(c.lineNumber)
                .append(" round=").append(c.round)
                .append(" query=\"").append(c.query.replace("\"", "'"))
                .append("\"\n")

            val ctx = (c.matchContext ?: c.lineContent).trim()
            val limited = if (ctx.length > maxCharsPerItem) ctx.take(maxCharsPerItem) else ctx
            sb.append(limited).append("\n\n")
        }
        return sb.toString().trim()
    }

    protected suspend fun enrichCandidatesWithReadContext(
        candidates: List<GrepContextCandidate>,
        environment: String?,
        readContextLines: Int,
        maxCandidatesToRead: Int,
        selectedCandidateIndexes: List<Int>? = null
    ): List<GrepContextCandidate> {
        if (candidates.isEmpty()) return candidates

        val indexesToRead =
            if (selectedCandidateIndexes == null) {
                (0 until minOf(maxCandidatesToRead, candidates.size)).toList()
            } else {
                selectedCandidateIndexes
                    .asSequence()
                    .distinct()
                    .filter { it >= 0 && it < candidates.size }
                    .take(maxCandidatesToRead)
                    .toList()
            }

        if (indexesToRead.isEmpty()) return candidates

        val indexSet = indexesToRead.toHashSet()
        val enriched = ArrayList<GrepContextCandidate>(candidates.size)

        for ((idx, c) in candidates.withIndex()) {
            if (!indexSet.contains(idx)) {
                enriched.add(c)
                continue
            }

            val startLine = maxOf(1, c.lineNumber - readContextLines)
            val endLine = c.lineNumber + readContextLines
            val params = mutableListOf(
                ToolParameter("path", c.filePath),
                ToolParameter("start_line", startLine.toString()),
                ToolParameter("end_line", endLine.toString())
            )
            if (!environment.isNullOrBlank()) {
                params.add(ToolParameter("environment", environment))
            }

            val readRes = readFilePart(AITool(name = "read_file_part", parameters = params))
            val snippet = (readRes.result as? FilePartContentData)?.content

            if (readRes.success && !snippet.isNullOrBlank()) {
                enriched.add(c.copy(matchContext = snippet))
            } else {
                enriched.add(c)
            }
        }

        return enriched
    }

    protected suspend fun runGrepCodeBatch(
        searchPath: String,
        environment: String?,
        filePattern: String,
        queries: List<String>,
        perQueryMaxResults: Int,
        round: Int,
        prefetchedFiles: List<String>? = null,
        toolNameForProgress: String? = null,
        progressBase: Float = 0f,
        progressSpan: Float = 0f,
        progressMessage: String = ""
    ): Pair<List<GrepContextCandidate>, Int> {
        val limitedQueries = normalizeQueries(queries).take(8)
        val candidates = mutableListOf<GrepContextCandidate>()
        val dedup = HashSet<String>()

        if (limitedQueries.isEmpty()) {
            return Pair(emptyList(), 0)
        }

        if (prefetchedFiles != null && prefetchedFiles.isEmpty()) {
            return Pair(emptyList(), 0)
        }

        val indexedQueries =
            limitedQueries.mapIndexedNotNull { index, query ->
                if (runCatching { Regex(query, RegexOption.IGNORE_CASE) }.isSuccess) {
                    index to query
                } else {
                    null
                }
            }

        if (indexedQueries.isEmpty()) {
            return Pair(emptyList(), 0)
        }

        val completedQueries = AtomicInteger(0)
        val executions =
            coroutineScope {
                indexedQueries.map { (index, query) ->
                    async {
                        val (parsedBlocks, _) =
                            searchNativeRipgrepBlocks(
                                path = searchPath,
                                patterns = listOf(query),
                                filePattern = filePattern,
                                caseInsensitive = true,
                                contextLines = 3,
                                maxResults = perQueryMaxResults
                            )

                        if (toolNameForProgress != null && progressSpan > 0f) {
                            val completed = completedQueries.incrementAndGet()
                            val fraction =
                                (completed.toFloat() / indexedQueries.size.toFloat()).coerceIn(0f, 1f)
                            val msg = if (progressMessage.isNotBlank()) progressMessage else "Searching..."
                            ToolProgressBus.update(
                                toolNameForProgress,
                                (progressBase + progressSpan * fraction).coerceIn(0f, 0.99f),
                                "$msg (query $completed/${indexedQueries.size})"
                            )
                        }

                        RipgrepQueryExecution(
                            index = index,
                            query = query,
                            parsedBlocks = parsedBlocks
                        )
                    }
                }.awaitAll()
            }.sortedBy { it.index }

        executions.forEach { execution ->
            if (execution.parsedBlocks.isEmpty()) {
                return@forEach
            }

            var remaining = perQueryMaxResults
            execution.parsedBlocks.forEach { block ->
                if (remaining <= 0) return@forEach
                val candidate =
                    GrepContextCandidate(
                        filePath = block.filePath,
                        lineNumber = block.firstMatchLine,
                        lineContent = block.lineContent,
                        matchContext = block.matchContext,
                        query = execution.query,
                        round = round
                    )
                val key =
                    "${candidate.filePath}#${candidate.lineNumber}#${(candidate.matchContext ?: "").take(120)}"
                if (!dedup.add(key)) return@forEach
                candidates.add(candidate)
                remaining--
            }
        }

        if (toolNameForProgress != null && progressSpan > 0f) {
            val msg = if (progressMessage.isNotBlank()) progressMessage else "Searching..."
            ToolProgressBus.update(
                toolNameForProgress,
                (progressBase + progressSpan).coerceIn(0f, 0.99f),
                "$msg (query ${indexedQueries.size}/${indexedQueries.size})"
            )
        }

        return Pair(candidates, 0)
    }

    private fun groupRipgrepBlocks(
        blocks: List<RipgrepBlock>
    ): List<GrepResultData.FileMatch> {
        val grouped = LinkedHashMap<String, MutableList<GrepResultData.LineMatch>>()
        blocks.forEach { block ->
            val lineMatches =
                grouped.getOrPut(block.filePath) { mutableListOf() }
            lineMatches.add(
                GrepResultData.LineMatch(
                    lineNumber = block.firstMatchLine,
                    lineContent = block.lineContent,
                    matchContext = block.matchContext
                )
            )
        }

        return grouped.map { (filePath, lineMatches) ->
            GrepResultData.FileMatch(
                filePath = filePath,
                lineMatches = lineMatches
            )
        }
    }

    protected suspend fun grepCodeWithNativeRipgrep(
        toolName: String,
        path: String,
        pattern: String,
        filePattern: String,
        caseInsensitive: Boolean,
        contextLines: Int,
        maxResults: Int,
        envLabel: String
    ): ToolResult {
        return try {
            val effectiveMaxResults = maxResults.coerceAtLeast(0)
            if (effectiveMaxResults == 0) {
                ToolProgressBus.update(toolName, 1f, "Search completed")
                return ToolResult(
                    toolName = toolName,
                    success = true,
                    result = GrepResultData(
                        searchPath = path,
                        pattern = pattern,
                        matches = emptyList(),
                        totalMatches = 0,
                        filesSearched = 0,
                        env = envLabel
                    ),
                    error = ""
                )
            }

            ToolProgressBus.update(toolName, 0.1f, "Running native ripgrep...")
            val (parsedBlocks, filesSearched) =
                searchNativeRipgrepBlocks(
                    path = path,
                    patterns = listOf(pattern),
                    filePattern = filePattern,
                    caseInsensitive = caseInsensitive,
                    contextLines = contextLines,
                    maxResults = effectiveMaxResults
                )
            val limitedBlocks = parsedBlocks.take(effectiveMaxResults)
            val fileMatches = groupRipgrepBlocks(limitedBlocks)
            ToolProgressBus.update(toolName, 1f, "Search completed")

            ToolResult(
                toolName = toolName,
                success = true,
                result = GrepResultData(
                    searchPath = path,
                    pattern = pattern,
                    matches = fileMatches.take(20),
                    totalMatches = limitedBlocks.size,
                    filesSearched = filesSearched,
                    env = envLabel
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing native ripgrep code search", e)
            ToolProgressBus.update(toolName, 1f, "Search failed")
            ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Error performing native grep search: ${e.message}"
            )
        }
    }

    protected suspend fun grepContextAgentic(
        toolName: String,
        displayPath: String,
        searchPath: String,
        environment: String?,
        intent: String,
        filePattern: String,
        maxResults: Int,
        envLabel: String
    ): ToolResult {
        return try {
            val overallStartTime = System.currentTimeMillis()
            ToolProgressBus.update(toolName, 0f, "Preparing search...")

            val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")

            val fallback = listOf(intent.take(60)).filter { it.isNotBlank() }
            var queries = normalizeQueries(fallback).take(8)
            if (queries.isEmpty()) queries = fallback
            ToolProgressBus.update(toolName, 0.05f, "Starting search rounds...")

            val allCandidates = mutableListOf<GrepContextCandidate>()
            val overallDedup = HashSet<String>()

            val perRoundSearchSpan = 0.2f
            val perRoundRefineSpan = 0.05f

            for (round in 1..3) {
                val roundBase = 0.1f + (round - 1) * (perRoundSearchSpan + perRoundRefineSpan)
                AppLogger.d(TAG, "grep_context: Starting search round $round/3. queries=${queries.joinToString(" | ") { it.take(60) }}")
                val (batchCandidates, _) =
                    runGrepCodeBatch(
                        searchPath = searchPath,
                        environment = environment,
                        filePattern = filePattern,
                        queries = queries,
                        perQueryMaxResults = 30,
                        round = round,
                        toolNameForProgress = toolName,
                        progressBase = roundBase,
                        progressSpan = perRoundSearchSpan,
                        progressMessage = "Searching (round $round/3)"
                    )

                var storedBatchCandidates = batchCandidates

                val digestCandidates = storedBatchCandidates.take(24)
                val digest = buildCandidateDigestForModel(digestCandidates, 800)

                val planPrompt =
                    FunctionalPrompts.grepContextRefineWithReadPrompt(
                        intent = intent,
                        displayPath = displayPath,
                        filePattern = filePattern,
                        lastRoundDigest = digest,
                        maxRead = 8,
                        useEnglish = useEnglish
                    )

                ToolProgressBus.update(toolName, roundBase + perRoundSearchSpan, "Planning next steps (round $round/3)...")
                val planStart = System.currentTimeMillis()
                val planRaw = runGrepModel(planPrompt)
                val plannedQueries = normalizeQueries(parseQueryListFromModelOutput(planRaw, queries)).take(8)
                val readIds = parseReadIdsFromModelOutput(planRaw)
                    .distinct()
                    .filter { it >= 0 && it < digestCandidates.size }
                    .take(8)

                if (readIds.isNotEmpty()) {
                    ToolProgressBus.update(toolName, roundBase + perRoundSearchSpan, "Reading selected snippets (round $round/3)...")
                    val enrichedDigestCandidates =
                        enrichCandidatesWithReadContext(
                            candidates = digestCandidates,
                            environment = environment,
                            readContextLines = 25,
                            maxCandidatesToRead = 8,
                            selectedCandidateIndexes = readIds
                        )

                    val contextByKey = HashMap<String, String>()
                    for (id in readIds) {
                        val c = enrichedDigestCandidates.getOrNull(id) ?: continue
                        val ctx = c.matchContext ?: continue
                        contextByKey["${c.filePath}#${c.lineNumber}"] = ctx
                    }

                    storedBatchCandidates =
                        storedBatchCandidates.map { c ->
                            val key = "${c.filePath}#${c.lineNumber}"
                            val ctx = contextByKey[key]
                            if (!ctx.isNullOrBlank()) c.copy(matchContext = ctx) else c
                        }
                }

                val planElapsed = System.currentTimeMillis() - planStart
                if (round < 3 && plannedQueries.isNotEmpty()) {
                    queries = plannedQueries
                }
                ToolProgressBus.update(toolName, roundBase + perRoundSearchSpan + perRoundRefineSpan, "Prepared next steps")
                AppLogger.d(
                    TAG,
                    "grep_context: Plan after round $round/3 completed in ${planElapsed}ms. nextQueries=${plannedQueries.joinToString(" | ") { it.take(60) }} readIds=${readIds.joinToString(",") }"
                )

                storedBatchCandidates.forEach { c ->
                    val key = "${c.filePath}#${c.lineNumber}"
                    if (overallDedup.add(key)) {
                        allCandidates.add(c)
                    }
                }
            }

            if (allCandidates.isEmpty()) {
                ToolProgressBus.update(toolName, 1f, "Search completed, found 0")
                return ToolResult(
                    toolName = toolName,
                    success = true,
                    result =
                    GrepResultData(
                        searchPath = displayPath,
                        pattern = intent,
                        matches = emptyList(),
                        totalMatches = 0,
                        filesSearched = 0,
                        env = envLabel
                    ),
                    error = ""
                )
            }

            val selectionDigest = buildCandidateDigestForModel(allCandidates.take(60), 1000)
            val selectPrompt =
                FunctionalPrompts.grepContextSelectPrompt(
                    intent = intent,
                    displayPath = displayPath,
                    candidatesDigest = selectionDigest,
                    maxResults = maxResults,
                    useEnglish = useEnglish
                )

            ToolProgressBus.update(toolName, 0.85f, "Selecting most relevant matches...")
            val selectedIds = parseSelectedIdsFromModelOutput(runGrepModel(selectPrompt))
            val selectedCandidates =
                if (selectedIds.isNotEmpty()) {
                    selectedIds.mapNotNull { id -> allCandidates.getOrNull(id) }.take(maxResults)
                } else {
                    allCandidates.take(maxResults)
                }

            val fileOrder = selectedCandidates.map { it.filePath }.distinct()
            val fileMatches =
                fileOrder.map { filePath ->
                    val lineMatches =
                        selectedCandidates
                            .filter { it.filePath == filePath }
                            .map {
                                GrepResultData.LineMatch(
                                    lineNumber = it.lineNumber,
                                    lineContent = it.lineContent,
                                    matchContext = it.matchContext
                                )
                            }
                    GrepResultData.FileMatch(filePath = filePath, lineMatches = lineMatches)
                }

            ToolProgressBus.update(toolName, 1f, "Search completed, found ${selectedCandidates.size}")
            val overallElapsed = System.currentTimeMillis() - overallStartTime
            AppLogger.d(
                TAG,
                "grep_context: Completed in ${overallElapsed}ms. selected=${selectedCandidates.size} candidates=${allCandidates.size}"
            )
            ToolResult(
                toolName = toolName,
                success = true,
                result =
                GrepResultData(
                    searchPath = displayPath,
                    pattern = intent,
                    matches = fileMatches,
                    totalMatches = selectedCandidates.size,
                    filesSearched = 0,
                    env = envLabel
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing context search", e)
            ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Error performing context search: ${e.message}"
            )
        }
    }

    /** Adds line numbers to a string of content. */
    protected fun addLineNumbers(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""
        val maxDigits = lines.size.toString().length
        return lines.mapIndexed { index, line ->
            "${(index + 1).toString().padStart(maxDigits, ' ')}| $line"
        }.joinToString("\n")
    }

    /** Adds line numbers to a string of content, starting from a specific line number. */
    protected fun addLineNumbers(content: String, startLine: Int, totalLines: Int): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""
        val maxDigits =
            if (totalLines > 0) totalLines.toString().length else lines.size.toString().length
        return lines.mapIndexed { index, line ->
            "${(startLine + index + 1).toString().padStart(maxDigits, ' ')}| $line"
        }.joinToString("\n")
    }

    /** 判断是否为需要特殊处理的文件类型 */
    protected fun isSpecialFileType(fileExtension: String): Boolean {
        return fileExtension.lowercase() in SPECIAL_FILE_EXTENSIONS
    }

    /** 统计文件总行数 */
    protected fun countFileLines(file: File): Int {
        var totalLines = 0
        file.bufferedReader().use { reader ->
            while (reader.readLine() != null) {
                totalLines++
            }
        }
        return totalLines
    }

    /** 从文件中读取指定范围的行 */
    private fun readLinesFromFile(file: File, startLine: Int, endLine: Int): String {
        val partContent = StringBuilder()
        var currentLine = 0
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (currentLine >= endLine) return@useLines
                if (currentLine >= startLine) {
                    partContent.append(line).append('\n')
                }
                currentLine++
            }
        }
        // Remove last newline if content is not empty
        if (partContent.isNotEmpty()) {
            partContent.setLength(partContent.length - 1)
        }
        return partContent.toString()
    }

    /** List files in a directory */
    open suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.listFiles(tool)
        }
        if (isSafEnvironment(environment)) {
            return safTools.listFiles(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            val directory = File(path)

            if (!directory.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Directory does not exist: $path"
                )
            }

            if (!directory.isDirectory) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a directory: $path"
                )
            }

            val entries = mutableListOf<DirectoryListingData.FileEntry>()
            val files = directory.listFiles() ?: emptyArray()

            val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)

            for (file in files) {
                if (file.name != "." && file.name != "..") {
                    entries.add(
                        DirectoryListingData.FileEntry(
                            name = file.name,
                            isDirectory = file.isDirectory,
                            size = file.length(),
                            permissions = getFilePermissions(file),
                            lastModified =
                            dateFormat.format(
                                Date(file.lastModified())
                            )
                        )
                    )
                }
            }

            AppLogger.d(TAG, "Listed ${entries.size} entries in directory $path")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = DirectoryListingData(path, entries),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error listing directory: ${e.message}"
            )
        }
    }

    /** Get file permissions as a string like "rwxr-xr-x" */
    protected fun getFilePermissions(file: File): String {
        // Java has limited capabilities for getting Unix-style file permissions
        // This is a simplified version that checks basic permissions
        val canRead = if (file.canRead()) 'r' else '-'
        val canWrite = if (file.canWrite()) 'w' else '-'
        val canExecute = if (file.canExecute()) 'x' else '-'

        // For simplicity, we'll use the same permissions for user, group, and others
        return "$canRead$canWrite$canExecute$canRead-$canExecute$canRead-$canExecute"
    }

    /**
     * Handles reading special file types that require conversion or OCR. Returns a ToolResult
     * if the file type is special, otherwise null.
     */
    protected open suspend fun handleSpecialFileRead(
        tool: AITool,
        path: String,
        fileExt: String
    ): ToolResult? {
        return when (fileExt) {
            "doc", "docx" -> {
                AppLogger.d(
                    TAG,
                    "Detected Word document, attempting to extract text"
                )
                val tempFilePath =
                    "${path}_converted_${System.currentTimeMillis()}.txt"
                try {
                    val sourceFile = File(path)
                    val tempFile = File(tempFilePath)
                    val success = com.ai.assistance.operit.util.DocumentConversionUtil
                        .extractTextFromWord(sourceFile, tempFile, fileExt)

                    if (success && tempFile.exists()) {
                        AppLogger.d(
                            TAG,
                            "Successfully extracted text from Word document"
                        )
                        val content = tempFile.readText()
                        tempFile.delete() // Clean up
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileContentData(
                                path = path,
                                content = content,
                                size = content.length.toLong(),
                            ),
                            error = ""
                        )
                    } else {
                        AppLogger.w(
                            TAG,
                            "Word text extraction failed, returning error"
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to extract text from Word document"
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error during Word document text extraction", e)
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error extracting text from Word document: ${e.message}"
                    )
                }
            }

            "pdf" -> {
                AppLogger.d(
                    TAG,
                    "Detected PDF document, attempting to extract text"
                )
                val tempFilePath =
                    "${path}_converted_${System.currentTimeMillis()}.txt"
                try {
                    val sourceFile = File(path)
                    val tempFile = File(tempFilePath)
                    val success = com.ai.assistance.operit.util.DocumentConversionUtil
                        .extractTextFromPdf(context, sourceFile, tempFile)

                    if (success && tempFile.exists()) {
                        AppLogger.d(
                            TAG,
                            "Successfully extracted text from PDF document"
                        )
                        val content = tempFile.readText()
                        tempFile.delete() // Clean up
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileContentData(
                                path = path,
                                content = content,
                                size = content.length.toLong(),
                            ),
                            error = ""
                        )
                    } else {
                        AppLogger.w(
                            TAG,
                            "PDF text extraction failed, returning error"
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to extract text from PDF document"
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error during PDF document text extraction", e)
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error extracting text from PDF document: ${e.message}"
                    )
                }
            }

            "jpg", "jpeg", "png", "gif", "bmp" -> {
                // 获取可选的intent参数和direct_image参数
                val intent = tool.parameters.find { it.name == "intent" }?.value
                val directImage = tool.parameters.find { it.name == "direct_image" }?.value?.toBoolean() ?: false

                AppLogger.d(
                    TAG,
                    "Detected image file, intent=${intent ?: "无"}, direct_image=$directImage"
                )

                // 情况1：direct_image 为 true，直接返回图片链接，供支持识图的聊天模型自己查看
                if (directImage) {
                    try {
                        val imageId = ImagePoolManager.addImage(path)
                        if (imageId == "error") {
                            AppLogger.e(TAG, "Failed to register image for direct_image, falling back to intent/OCR: $path")
                        } else {
                            val link = "<link type=\"image\" id=\"$imageId\"></link>"
                            AppLogger.d(TAG, "Generated image link for direct_image: $link")
                            return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = FileContentData(
                                    path = path,
                                    content = link,
                                    size = link.length.toLong()
                                ),
                                error = ""
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error generating direct image link, falling back to intent/OCR", e)
                    }
                    // 如果生成图片链接失败，则继续走下面的 intent/OCR 逻辑
                }

                // 情况2：提供了 intent，使用后端识图模型
                if (!intent.isNullOrBlank()) {
                    try {
                        val enhancedService =
                            com.ai.assistance.operit.api.chat.EnhancedAIService.getInstance(context)
                        val analysisResult = kotlinx.coroutines.runBlocking {
                            enhancedService.analyzeImageWithIntent(path, intent)
                        }

                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileContentData(
                                path = path,
                                content = analysisResult,
                                size = analysisResult.length.toLong()
                            ),
                            error = ""
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "识图模型调用失败，回退到OCR", e)
                        // 回退到默认OCR处理
                    }
                }

                // 情况3：默认OCR处理
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeFile(path)
                    }
                    if (bitmap != null) {
                        val ocrText =
                            kotlinx.coroutines.runBlocking {
                                com.ai.assistance.operit.util
                                    .OCRUtils.recognizeText(
                                        context,
                                        bitmap
                                    )
                            }
                        if (ocrText.isNotBlank()) {
                            AppLogger.d(
                                TAG,
                                "Successfully extracted text from image using OCR"
                            )
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                FileContentData(
                                    path = path,
                                    content = ocrText,
                                    size =
                                    ocrText.length
                                        .toLong()
                                ),
                                error = ""
                            )
                        } else {
                            AppLogger.w(
                                TAG,
                                "OCR extraction returned empty text, returning no text detected message"
                            )
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                FileContentData(
                                    path = path,
                                    content =
                                    "No text detected in image.",
                                    size =
                                    "No text detected in image."
                                        .length
                                        .toLong()
                                ),
                                error = ""
                            )
                        }
                    } else {
                        AppLogger.w(
                            TAG,
                            "Failed to decode image file, returning error message"
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileContentData(
                                path = path,
                                content =
                                "Failed to decode image file.",
                                size =
                                "Failed to decode image file."
                                    .length
                                    .toLong()
                            ),
                            error = ""
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error during OCR text extraction", e)
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileContentData(
                            path = path,
                            content =
                            "Error extracting text from image: ${e.message}",
                            size =
                            "Error extracting text from image: ${e.message}"
                                .length.toLong()
                        ),
                        error = ""
                    )
                }
            }

            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus" -> {
                val intent = tool.parameters.find { it.name == "intent" }?.value
                val directAudio = tool.parameters.find { it.name == "direct_audio" }?.value?.toBoolean() ?: false

                AppLogger.d(TAG, "Detected audio file, intent=${intent ?: "无"}, direct_audio=$directAudio")

                if (directAudio) {
                    try {
                        val derivedMimeType =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt)
                                ?: "audio/*"
                        val audioId = MediaPoolManager.addMedia(path, derivedMimeType)
                        if (audioId == "error") {
                            AppLogger.e(TAG, "Failed to register audio for direct_audio, falling back to intent/info: $path")
                        } else {
                            val link = "<link type=\"audio\" id=\"$audioId\"></link>"
                            AppLogger.d(TAG, "Generated audio link for direct_audio: $link")
                            return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = FileContentData(
                                    path = path,
                                    content = link,
                                    size = link.length.toLong()
                                ),
                                error = ""
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error generating direct audio link, falling back to intent/info", e)
                    }
                }

                if (!intent.isNullOrBlank()) {
                    try {
                        val enhancedService = com.ai.assistance.operit.api.chat.EnhancedAIService.getInstance(context)
                        val analysisResult = kotlinx.coroutines.runBlocking {
                            enhancedService.analyzeAudioWithIntent(path, intent)
                        }

                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileContentData(
                                path = path,
                                content = analysisResult,
                                size = analysisResult.length.toLong()
                            ),
                            error = ""
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "音频识别模型调用失败，回退到媒体信息", e)
                    }
                }

                val file = File(path)
                val mediaInfo = FFmpegUtil.getMediaInfo(path)
                val infoText = buildString {
                    appendLine("Audio file info:")
                    appendLine("- path: $path")
                    appendLine("- size_bytes: ${file.length()}")
                    appendLine("- extension: $fileExt")
                    if (mediaInfo != null) {
                        appendLine("- format: ${mediaInfo.format}")
                        appendLine("- duration: ${mediaInfo.duration}")
                        appendLine("- bitrate: ${mediaInfo.bitrate}")
                        val streams = mediaInfo.streams
                        if (!streams.isNullOrEmpty()) {
                            val audioStreams = streams.filter { it.type.equals("audio", ignoreCase = true) }
                            appendLine("- audio_streams: ${audioStreams.size}")
                        }
                    }
                }.trim()

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(
                        path = path,
                        content = infoText,
                        size = infoText.length.toLong()
                    ),
                    error = ""
                )
            }

            "mp4", "mkv", "mov", "webm", "avi", "m4v" -> {
                val intent = tool.parameters.find { it.name == "intent" }?.value
                val directVideo = tool.parameters.find { it.name == "direct_video" }?.value?.toBoolean() ?: false

                AppLogger.d(TAG, "Detected video file, intent=${intent ?: "无"}, direct_video=$directVideo")

                if (directVideo) {
                    try {
                        val derivedMimeType =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt)
                                ?: "video/*"
                        val videoId = MediaPoolManager.addMedia(path, derivedMimeType)
                        if (videoId == "error") {
                            AppLogger.e(TAG, "Failed to register video for direct_video, falling back to intent/info: $path")
                        } else {
                            val link = "<link type=\"video\" id=\"$videoId\"></link>"
                            AppLogger.d(TAG, "Generated video link for direct_video: $link")
                            return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = FileContentData(
                                    path = path,
                                    content = link,
                                    size = link.length.toLong()
                                ),
                                error = ""
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error generating direct video link, falling back to intent/info", e)
                    }
                }

                if (!intent.isNullOrBlank()) {
                    try {
                        val enhancedService = com.ai.assistance.operit.api.chat.EnhancedAIService.getInstance(context)
                        val analysisResult = kotlinx.coroutines.runBlocking {
                            enhancedService.analyzeVideoWithIntent(path, intent)
                        }

                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileContentData(
                                path = path,
                                content = analysisResult,
                                size = analysisResult.length.toLong()
                            ),
                            error = ""
                        )
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "视频识别模型调用失败，回退到媒体信息", e)
                    }
                }

                val file = File(path)
                val mediaInfo = FFmpegUtil.getMediaInfo(path)
                val infoText = buildString {
                    appendLine("Video file info:")
                    appendLine("- path: $path")
                    appendLine("- size_bytes: ${file.length()}")
                    appendLine("- extension: $fileExt")
                    if (mediaInfo != null) {
                        appendLine("- format: ${mediaInfo.format}")
                        appendLine("- duration: ${mediaInfo.duration}")
                        appendLine("- bitrate: ${mediaInfo.bitrate}")
                        val streams = mediaInfo.streams
                        if (!streams.isNullOrEmpty()) {
                            val videoStreams = streams.filter { it.type.equals("video", ignoreCase = true) }
                            val audioStreams = streams.filter { it.type.equals("audio", ignoreCase = true) }
                            appendLine("- video_streams: ${videoStreams.size}")
                            appendLine("- audio_streams: ${audioStreams.size}")
                        }
                    }
                }.trim()

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(
                        path = path,
                        content = infoText,
                        size = infoText.length.toLong()
                    ),
                    error = ""
                )
            }

            else -> null
        }
    }

    /**
     * Reads the full content of a file as a new tool, handling different file types. This
     * function does not enforce a size limit.
     */
    open suspend fun readFileFull(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val textOnly = tool.parameters.find { it.name == "text_only" }?.value?.toBoolean() ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFileFull(tool)
        }

        if (isSafEnvironment(environment)) {
            return safTools.readFileFull(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            if (!file.isFile) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = file.extension.lowercase()
            
            // 如果启用了 text_only 模式，检查文件是否为文本
            if (textOnly) {
                // 读取文件前 512 字节进行判断
                if (!FileUtils.isTextLike(file, 512)) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Skipped non-text file: $path"
                    )
                }
            }

            // 尝试特殊文件处理
            if (isSpecialFileType(fileExt)) {
                val specialReadResult = handleSpecialFileRead(tool, path, fileExt)
                if (specialReadResult != null) {
                    return specialReadResult
                }
            }

            // Check if file is text-like by analyzing content
            if (FileUtils.isTextLike(file)) {
                val content = file.readText()
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileContentData(
                        path = path,
                        content = content,
                        size = file.length()
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not appear to be a text file. Use specialized tools for binary files."
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file (full)", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** Read binary file content and return base64-encoded data */
    open suspend fun readFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFileBinary(tool)
        }

        if (isSafEnvironment(environment)) {
            return safTools.readFileBinary(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist or is not a regular file: $path"
                    )
                }

                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BinaryFileContentData(
                        path = path,
                        contentBase64 = base64,
                        size = file.length()
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error reading binary file", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading binary file: ${e.message}"
                )
            }
        }
    }

    /** Read file content, truncated to configured max size */
    open suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFile(tool)
        }

        if (isSafEnvironment(environment)) {
            return safTools.readFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            // 从配置中获取最大文件大小
            val maxFileSizeBytes = ToolExecutionLimits.MAX_FILE_READ_BYTES

            val file = File(path)
            if (!file.exists() || !file.isFile) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = file.extension.lowercase()

            // For special types, full read then truncate text is the only way.
            if (isSpecialFileType(fileExt)) {
                val fullResult = readFileFull(tool)
                if (!fullResult.success) return fullResult

                val contentData = fullResult.result as FileContentData
                var content = contentData.content
                val isTruncated = content.length > maxFileSizeBytes
                if (isTruncated) {
                    content = content.substring(0, maxFileSizeBytes)
                }

                var contentWithLineNumbers = addLineNumbers(content)
                if (isTruncated) {
                    contentWithLineNumbers += "\n\n... (file content truncated) ..."
                }
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileContentData(
                        path = path,
                        content = contentWithLineNumbers,
                        size = contentWithLineNumbers.length.toLong()
                    ),
                    error = ""
                )
            }

            // For text-based files, read only the beginning.
            // Check if file is text-like by analyzing content
            if (!FileUtils.isTextLike(file)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                    "File does not appear to be a text file. Use readFileFull tool for special file types."
                )
            }

            val content =
                file.bufferedReader().use {
                    val buffer = CharArray(maxFileSizeBytes)
                    val charsRead = it.read(buffer, 0, maxFileSizeBytes)
                    if (charsRead > 0) String(buffer, 0, charsRead) else ""
                }

            val truncated = file.length() > maxFileSizeBytes
            var finalContent = addLineNumbers(content)
            if (truncated) {
                finalContent += "\n\n... (file content truncated) ..."
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileContentData(
                    path = path,
                    content = finalContent,
                    size = finalContent.length.toLong()
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** 按行号范围读取文件内容（行号从1开始，包括开始行和结束行） */
    open suspend fun readFilePart(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val startLineParam = tool.parameters.find { it.name == "start_line" }?.value?.toIntOrNull() ?: 1
        val endLineParam = tool.parameters.find { it.name == "end_line" }?.value?.toIntOrNull()

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.readFilePart(tool)
        }

        if (isSafEnvironment(environment)) {
            return safTools.readFilePart(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                val maxFileSizeBytes = ToolExecutionLimits.MAX_FILE_READ_BYTES

                if (!file.exists() || !file.isFile) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist or is not a regular file: $path"
                    )
                }

                // 1. 准备数据源 & 获取总行数
                // 如果是特殊文件，先提取所有文本到内存；如果是普通文件，只统计行数
                val inMemoryLines: List<String>?
                val totalLines: Int

                if (isSpecialFileType(file.extension)) {
                    val specialResult = handleSpecialFileRead(tool, path, file.extension.lowercase())
                    if (specialResult != null && !specialResult.success) return@withContext specialResult

                    if (specialResult != null) {
                        val content = (specialResult.result as FileContentData).content
                        inMemoryLines = content.lines()
                        totalLines = inMemoryLines.size
                    } else {
                        // Fallback if handleSpecialFileRead returns null (shouldn't happen given check)
                        inMemoryLines = null
                        totalLines = 0
                    }
                } else {
                    inMemoryLines = null
                    totalLines = countFileLines(file)
                }

                // 2. 计算实际的行号范围（行号从1开始，转换为0-based索引）
                val startLine = maxOf(1, startLineParam).coerceIn(1, maxOf(1, totalLines))
                val endLine =
                    (endLineParam
                            ?: (startLine + ToolExecutionLimits.DEFAULT_FILE_READ_PART_LINES - 1))
                        .coerceIn(startLine, maxOf(1, totalLines))
                
                // 转换为0-based索引
                val startIndex = startLine - 1
                val endIndex = endLine // endLine 本身就是最后一行的1-based行号，转成exclusive的end需要不减1

                // 3. 获取分段内容
                val partContent = if (inMemoryLines != null) {
                    // 内存模式：直接切片
                    if (totalLines > 0 && startIndex < totalLines) {
                        inMemoryLines.subList(startIndex, minOf(endIndex, totalLines)).joinToString("\n")
                    } else ""
                } else {
                    // 磁盘流模式：读取指定行
                    if (totalLines > 0) readLinesFromFile(file, startIndex, endIndex) else ""
                }

                // 4. 封装返回结果
                var truncatedPartContent = partContent
                val isTruncated = truncatedPartContent.length > maxFileSizeBytes
                if (isTruncated) {
                    truncatedPartContent = truncatedPartContent.substring(0, maxFileSizeBytes)
                }

                var contentWithLineNumbers = addLineNumbers(truncatedPartContent, startIndex, totalLines)
                if (isTruncated) {
                    contentWithLineNumbers += "\n\n... (file content truncated) ..."
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FilePartContentData(
                        path = path,
                        content = contentWithLineNumbers,
                        partIndex = 0, // 保留兼容性，但不再使用
                        totalParts = 1, // 保留兼容性，但不再使用
                        startLine = startIndex,
                        endLine = minOf(endIndex, totalLines),
                        totalLines = totalLines
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error reading file part", e)
                return@withContext ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading file part: ${e.message}"
                )
            }
        }
    }

    /** Write content to a file */
    open suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append =
            tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.writeFile(tool)
        }
        if (isSafEnvironment(environment)) {
            return safTools.writeFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "write",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)

                // Create parent directories if needed
                val parentDir = file.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        AppLogger.w(
                            TAG,
                            "Failed to create parent directory: ${parentDir.absolutePath}"
                        )
                    }
                }

                // Write content to file
                if (append && file.exists()) {
                    file.appendText(content)
                } else {
                    file.writeText(content)
                }

                // Verify write was successful
                if (!file.exists()) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation =
                            if (append) "append" else "write",
                            path = path,
                            successful = false,
                            details =
                            "Write completed but file does not exist. Possible permission issue."
                        ),
                        error =
                        "Write completed but file does not exist. Possible permission issue."
                    )
                }

                if (file.length() == 0L && content.isNotEmpty()) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation =
                            if (append) "append" else "write",
                            path = path,
                            successful = false,
                            details =
                            "File was created but appears to be empty. Possible write failure."
                        ),
                        error =
                        "File was created but appears to be empty. Possible write failure."
                    )
                }

                val operation = if (append) "append" else "write"
                val details =
                    if (append) "Content appended to $path"
                    else "Content written to $path"

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = operation,
                        path = path,
                        successful = true,
                        details = details
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error writing to file", e)

                val errorMessage =
                    when {
                        e is IOException ->
                            "File I/O error: ${e.message}. Please check if the path has write permissions."

                        e.message?.contains("permission", ignoreCase = true) ==
                                true ->
                            "Permission denied, cannot write to file: ${e.message}. Please check if the app has proper permissions."

                        else -> "Error writing to file: ${e.message}"
                    }

                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = if (append) "append" else "write",
                        path = path,
                        successful = false,
                        details = errorMessage
                    ),
                    error = errorMessage
                )
            }
        }
    }

    /** Write base64 encoded content to a binary file */
    open suspend fun writeFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val base64Content = tool.parameters.find { it.name == "base64Content" }?.value ?: ""

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.writeFileBinary(tool)
        }
        if (isSafEnvironment(environment)) {
            return safTools.writeFileBinary(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "write_binary",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)

            // Create parent directories if needed
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    AppLogger.w(
                        TAG,
                        "Failed to create parent directory: ${parentDir.absolutePath}"
                    )
                }
            }

            // Decode base64 and write bytes
            val decodedBytes =
                android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            file.writeBytes(decodedBytes)

            // Verify write was successful
            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "write_binary",
                        path = path,
                        successful = false,
                        details =
                        "Write completed but file does not exist. Possible permission issue."
                    ),
                    error =
                    "Write completed but file does not exist. Possible permission issue."
                )
            }

            if (file.length() == 0L && decodedBytes.isNotEmpty()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "write_binary",
                        path = path,
                        successful = false,
                        details =
                        "File was created but appears to be empty. Possible write failure."
                    ),
                    error =
                    "File was created but appears to be empty. Possible write failure."
                )
            }

            val details = "Binary content written to $path"

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = true,
                    details = details
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error writing binary file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = false,
                    details = "Error writing binary file: ${e.message}"
                ),
                error = "Error writing binary file: ${e.message}"
            )
        }
    }

    /** Delete a file or directory */
    open suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val recursive =
            tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.deleteFile(tool)
        }
        if (isSafEnvironment(environment)) {
            return safTools.deleteFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "delete",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }


        return try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = false,
                        details =
                        "File or directory does not exist: $path"
                    ),
                    error = "File or directory does not exist: $path"
                )
            }

            var success = false

            if (file.isDirectory) {
                if (recursive) {
                    success = file.deleteRecursively()
                } else {
                    // Only delete if directory is empty
                    val files = file.listFiles() ?: emptyArray()
                    if (files.isEmpty()) {
                        success = file.delete()
                    } else {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                            FileOperationData(
                                operation = "delete",
                                path = path,
                                successful = false,
                                details =
                                "Directory is not empty and recursive flag is not set"
                            ),
                            error =
                            "Directory is not empty and recursive flag is not set"
                        )
                    }
                }
            } else {
                success = file.delete()
            }

            if (success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = true,
                        details = "Successfully deleted $path"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = false,
                        details =
                        "Failed to delete: permission denied or file in use"
                    ),
                    error = "Failed to delete: permission denied or file in use"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting file/directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "delete",
                    path = path,
                    successful = false,
                    details = "Error deleting file/directory: ${e.message}"
                ),
                error = "Error deleting file/directory: ${e.message}"
            )
        }
    }

    /** Check if a file or directory exists */
    open suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.fileExists(tool)
        }
        if (isSafEnvironment(environment)) {
            return safTools.fileExists(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)
            val exists = file.exists()

            if (!exists) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileExistsData(path = path, exists = false),
                    error = ""
                )
            }

            val isDirectory = file.isDirectory
            val size = file.length()

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileExistsData(
                    path = path,
                    exists = true,
                    isDirectory = isDirectory,
                    size = size
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking file existence", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileExistsData(
                    path = path,
                    exists = false,
                    isDirectory = false,
                    size = 0
                ),
                error = "Error checking file existence: ${e.message}"
            )
        }
    }

    /** Move or rename a file or directory */
    open suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.moveFile(tool)
        }

        if (isSafEnvironment(environment)) {
            return safTools.moveFile(tool)
        }
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details =
                    "Source and destination parameters are required"
                ),
                error = "Source and destination parameters are required"
            )
        }


        return try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = false,
                        details =
                        "Source file does not exist: $sourcePath"
                    ),
                    error = "Source file does not exist: $sourcePath"
                )
            }

            // Create parent directory if needed
            val destParent = destFile.parentFile
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs()
            }

            // Perform move operation
            if (sourceFile.renameTo(destFile)) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = true,
                        details =
                        "Successfully moved $sourcePath to $destPath"
                    ),
                    error = ""
                )
            } else {
                // If simple rename fails, try copy and delete (could be across
                // filesystems)
                if (sourceFile.isDirectory) {
                    // For directories, use directory copy utility
                    val copySuccess = copyDirectory(sourceFile, destFile)
                    if (copySuccess && sourceFile.deleteRecursively()) {
                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileOperationData(
                                operation = "move",
                                path = sourcePath,
                                successful = true,
                                details =
                                "Successfully moved $sourcePath to $destPath (via copy and delete)"
                            ),
                            error = ""
                        )
                    }
                } else {
                    // For files, copy the content then delete original
                    sourceFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (destFile.exists() &&
                        destFile.length() == sourceFile.length() &&
                        sourceFile.delete()
                    ) {
                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileOperationData(
                                operation = "move",
                                path = sourcePath,
                                successful = true,
                                details =
                                "Successfully moved $sourcePath to $destPath (via copy and delete)"
                            ),
                            error = ""
                        )
                    }
                }

                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = false,
                        details =
                        "Failed to move file: possibly a permissions issue or destination already exists"
                    ),
                    error =
                    "Failed to move file: possibly a permissions issue or destination already exists"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error moving file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details = "Error moving file: ${e.message}"
                ),
                error = "Error moving file: ${e.message}"
            )
        }
    }

    /** Helper method to recursively copy a directory */
    private fun copyDirectory(sourceDir: File, destDir: File): Boolean {
        try {
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            sourceDir.listFiles()?.forEach { file ->
                val destFile = File(destDir, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    file.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying directory", e)
            return false
        }
    }

    /**
     * 跨环境复制文件或目录
     * 支持 Android <-> Linux 之间的文件复制
     */
    private suspend fun copyFileCrossEnvironment(
        toolName: String,
        sourcePath: String,
        destPath: String,
        sourceEnvironment: String,
        destEnvironment: String,
        recursive: Boolean
    ): ToolResult {
        // 目标路径保持原样，让 Linux 文件系统提供者处理 ~ 的展开
        val finalDestPath = destPath

        return try {
            AppLogger.d(
                TAG,
                "Cross-environment copy: $sourceEnvironment:$sourcePath -> $destEnvironment:$finalDestPath"
            )

            // 1. 检查源文件是否存在
            val sourceExists = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().exists(sourcePath)
            } else {
                File(sourcePath).exists()
            }

            if (!sourceExists) {
                return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details = "Failed to read source file"
                    ),
                    error = "Failed to read source file"
                )
            }

            // 2. 检查是否是目录
            val isDirectory = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().isDirectory(sourcePath)
            } else {
                File(sourcePath).isDirectory
            }

            if (isDirectory) {
                if (!recursive) {
                    return ToolResult(
                        toolName = toolName,
                        success = false,
                        result = FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details = "Cannot copy directory without recursive flag"
                        ),
                        error = "Cannot copy directory without recursive flag"
                    )
                }

                // 目录复制：递归复制所有文件
                return copyDirectoryCrossEnvironment(
                    toolName,
                    sourcePath,
                    finalDestPath,
                    sourceEnvironment,
                    destEnvironment
                )
            }

            // 3. 获取文件大小
            val fileSize = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().getFileSize(sourcePath)
            } else {
                File(sourcePath).length()
            }

            // 4. 统一分块传输（10MB 缓冲）
            val BUFFER_SIZE = 10 * 1024 * 1024
            var totalBytes = 0L

            if (isLinuxEnvironment(sourceEnvironment)) {
                // 从 Linux 读取并写入
                val content = getLinuxFileSystem().readFile(sourcePath) ?: return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details = "Failed to read source file"
                    ),
                    error = "Failed to read source file"
                )
                val bytes = content.toByteArray(Charsets.UTF_8)

                if (isLinuxEnvironment(destEnvironment)) {
                    val result = getLinuxFileSystem().writeFileBytes(finalDestPath, bytes)
                    if (!result.success) {
                        return ToolResult(
                            toolName = toolName,
                            success = false,
                            result = FileOperationData(
                                operation = "copy",
                                path = sourcePath,
                                successful = false,
                                details = result.message
                            ),
                            error = result.message
                        )
                    }
                } else {
                    File(finalDestPath).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                }
                totalBytes = bytes.size.toLong()
            } else {
                // 从 Android 读取并写入
                val sourceFile = File(sourcePath)
                sourceFile.inputStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    val outputStream = if (isLinuxEnvironment(destEnvironment)) {
                        java.io.ByteArrayOutputStream()
                    } else {
                        File(finalDestPath).apply { parentFile?.mkdirs() }.outputStream()
                    }

                    outputStream.use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                    }

                    if (isLinuxEnvironment(destEnvironment)) {
                        val bytes = (outputStream as java.io.ByteArrayOutputStream).toByteArray()
                        val result = getLinuxFileSystem().writeFileBytes(finalDestPath, bytes)
                        if (!result.success) {
                            return ToolResult(
                                toolName = toolName,
                                success = false,
                                result = FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = result.message
                                ),
                                error = result.message
                            )
                        }
                    }
                }
            }

            // 5. 验证成功
            AppLogger.d(TAG, "Successfully copied file cross-environment: $totalBytes bytes")
            return ToolResult(
                toolName = toolName,
                success = true,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = true,
                    details = "Successfully copied file from $sourceEnvironment:$sourcePath to $destEnvironment:$finalDestPath ($totalBytes bytes)"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying file cross-environment", e)
            return ToolResult(
                toolName = toolName,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Error copying file cross-environment: ${e.message}"
                ),
                error = "Error copying file cross-environment: ${e.message}"
            )
        }
    }

    /**
     * 跨环境递归复制目录
     */
    private suspend fun copyDirectoryCrossEnvironment(
        toolName: String,
        sourcePath: String,
        destPath: String,
        sourceEnvironment: String,
        destEnvironment: String
    ): ToolResult {
        // 目标路径保持原样，让 Linux 文件系统提供者处理 ~ 的展开
        val finalDestPath = destPath

        return try {
            AppLogger.d(
                TAG,
                "Cross-environment directory copy: $sourceEnvironment:$sourcePath -> $destEnvironment:$finalDestPath"
            )

            // 1. 创建目标目录
            if (isLinuxEnvironment(destEnvironment)) {
                val result = getLinuxFileSystem().createDirectory(finalDestPath, createParents = true)
                if (!result.success) {
                    return ToolResult(
                        toolName = toolName,
                        success = false,
                        result = FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details = "Failed to create destination directory: ${result.message}"
                        ),
                        error = "Failed to create destination directory: ${result.message}"
                    )
                }
            } else {
                val destDir = File(finalDestPath)
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
            }

            // 2. 列出源目录内容
            val entries = if (isLinuxEnvironment(sourceEnvironment)) {
                getLinuxFileSystem().listDirectory(sourcePath)?.map { fileInfo ->
                    Pair(fileInfo.name, fileInfo.isDirectory)
                } ?: emptyList()
            } else {
                File(sourcePath).listFiles()?.map { file ->
                    Pair(file.name, file.isDirectory)
                } ?: emptyList()
            }

            // 3. 递归复制每个条目
            var copiedFiles = 0
            var copiedDirs = 0
            for ((name, isDir) in entries) {
                val srcFullPath =
                    if (sourcePath.endsWith("/")) "$sourcePath$name" else "$sourcePath/$name"
                val dstFullPath =
                    if (finalDestPath.endsWith("/")) "$finalDestPath$name" else "$finalDestPath/$name"

                if (isDir) {
                    val result = copyDirectoryCrossEnvironment(
                        toolName,
                        srcFullPath,
                        dstFullPath,
                        sourceEnvironment,
                        destEnvironment
                    )
                    if (result.success) {
                        copiedDirs++
                    } else {
                        AppLogger.w(TAG, "Failed to copy directory: $srcFullPath")
                    }
                } else {
                    val result = copyFileCrossEnvironment(
                        toolName,
                        srcFullPath,
                        dstFullPath,
                        sourceEnvironment,
                        destEnvironment,
                        recursive = false
                    )
                    if (result.success) {
                        copiedFiles++
                    } else {
                        AppLogger.w(TAG, "Failed to copy file: $srcFullPath")
                    }
                }
            }

            AppLogger.d(
                TAG,
                "Successfully copied directory: $copiedFiles files, $copiedDirs subdirectories"
            )
            return ToolResult(
                toolName = toolName,
                success = true,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = true,
                    details = "Successfully copied directory from $sourceEnvironment:$sourcePath to $destEnvironment:$finalDestPath ($copiedFiles files, $copiedDirs subdirectories)"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying directory cross-environment", e)
            return ToolResult(
                toolName = toolName,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Error copying directory cross-environment: ${e.message}"
                ),
                error = "Error copying directory cross-environment: ${e.message}"
            )
        }
    }

    /** Copy a file or directory */
    open suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val sourceEnvironment = tool.parameters.find { it.name == "source_environment" }?.value
        val destEnvironment = tool.parameters.find { it.name == "dest_environment" }?.value
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val recursive =
            tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details =
                    "Source and destination parameters are required"
                ),
                error = "Source and destination parameters are required"
            )
        }

        // 确定源和目标环境
        val srcEnv = sourceEnvironment ?: environment ?: "android"
        val dstEnv = destEnvironment ?: environment ?: "android"

        if ((isSafEnvironment(srcEnv) || isSafEnvironment(dstEnv)) &&
            (isLinuxEnvironment(srcEnv) || isLinuxEnvironment(dstEnv))) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Repository environment cannot be used with linux environment"
                ),
                error = "Repository environment cannot be used with linux environment"
            )
        }

        // 检查是否是跨环境复制
        val isCrossEnvironment = srcEnv.lowercase() != dstEnv.lowercase()

        // 如果是跨环境复制，使用特殊处理
        if (isCrossEnvironment) {
            return copyFileCrossEnvironment(
                toolName = tool.name,
                sourcePath = sourcePath,
                destPath = destPath,
                sourceEnvironment = srcEnv,
                destEnvironment = dstEnv,
                recursive = recursive
            )
        }

        // 同环境复制 - 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(srcEnv)) {
            return linuxTools.copyFile(
                AITool(
                    name = tool.name,
                    parameters = listOf(
                        ToolParameter("source", sourcePath),
                        ToolParameter("destination", destPath),
                        ToolParameter("recursive", recursive.toString())
                    )
                )
            )
        }

        if (isSafEnvironment(srcEnv) || isSafEnvironment(dstEnv) || isSafEnvironment(environment)) {
            return safTools.copyFile(tool)
        }
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        // Android环境内复制
        return try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details =
                        "Source path does not exist: $sourcePath"
                    ),
                    error = "Source path does not exist: $sourcePath"
                )
            }

            // Create parent directory if needed
            val destParent = destFile.parentFile
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs()
            }

            if (sourceFile.isDirectory) {
                if (recursive) {
                    val success = copyDirectory(sourceFile, destFile)
                    if (success) {
                        return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                            FileOperationData(
                                operation = "copy",
                                path = sourcePath,
                                successful = true,
                                details =
                                "Successfully copied directory $sourcePath to $destPath"
                            ),
                            error = ""
                        )
                    } else {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                            FileOperationData(
                                operation = "copy",
                                path = sourcePath,
                                successful = false,
                                details =
                                "Failed to copy directory: possible permission issue"
                            ),
                            error =
                            "Failed to copy directory: possible permission issue"
                        )
                    }
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details =
                            "Cannot copy directory without recursive flag"
                        ),
                        error =
                        "Cannot copy directory without recursive flag"
                    )
                }
            } else {
                // Copy file
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Verify copy was successful
                if (destFile.exists() && destFile.length() == sourceFile.length()) {
                    return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = true,
                            details =
                            "Successfully copied file $sourcePath to $destPath"
                        ),
                        error = ""
                    )
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "copy",
                            path = sourcePath,
                            successful = false,
                            details =
                            "Copy operation completed but verification failed"
                        ),
                        error =
                        "Copy operation completed but verification failed"
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying file/directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details =
                    "Error copying file/directory: ${e.message}"
                ),
                error = "Error copying file/directory: ${e.message}"
            )
        }
    }

    /** Create a directory */
    open suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val createParents =
            tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean()
                ?: false

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.makeDirectory(tool)
        }
        if (isSafEnvironment(environment)) {
            return safTools.makeDirectory(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "mkdir",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val directory = File(path)

            // Check if directory already exists
            if (directory.exists()) {
                if (directory.isDirectory) {
                    return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileOperationData(
                            operation = "mkdir",
                            path = path,
                            successful = true,
                            details =
                            "Directory already exists: $path"
                        ),
                        error = ""
                    )
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "mkdir",
                            path = path,
                            successful = false,
                            details =
                            "Path exists but is not a directory: $path"
                        ),
                        error = "Path exists but is not a directory: $path"
                    )
                }
            }

            // Create directory
            val success =
                if (createParents) {
                    directory.mkdirs()
                } else {
                    directory.mkdir()
                }

            if (success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "mkdir",
                        path = path,
                        successful = true,
                        details =
                        "Successfully created directory $path"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "mkdir",
                        path = path,
                        successful = false,
                        details =
                        "Failed to create directory: parent directory may not exist or permission denied"
                    ),
                    error =
                    "Failed to create directory: parent directory may not exist or permission denied"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "mkdir",
                    path = path,
                    successful = false,
                    details = "Error creating directory: ${e.message}"
                ),
                error = "Error creating directory: ${e.message}"
            )
        }
    }

    /** Search for files matching a pattern */
    open suspend fun findFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.findFiles(tool)
        }
        if (isSafEnvironment(environment)) {
            return safTools.findFiles(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank() || pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = emptyList(),
                    env = "android"
                ),
                error = "Path and pattern parameters are required"
            )
        }

        return try {
            ToolProgressBus.update(tool.name, 0f, "Searching...")
            val rootDir = File(path)

            // Get search options
            val usePathPattern =
                tool.parameters
                    .find { it.name == "use_path_pattern" }
                    ?.value
                    ?.toBoolean()
                    ?: false
            val caseInsensitive =
                tool.parameters
                    .find { it.name == "case_insensitive" }
                    ?.value
                    ?.toBoolean()
                    ?: false
            val maxDepth =
                tool.parameters
                    .find { it.name == "max_depth" }
                    ?.value
                    ?.toIntOrNull()
                    ?: -1

            // Convert glob pattern to regex
            val regex = globToRegex(pattern, caseInsensitive)

            if (rootDir.exists() && rootDir.isFile) {
                val testString = if (usePathPattern) rootDir.name else rootDir.name
                val matchingFiles = if (regex.matches(testString)) {
                    listOf(rootDir.absolutePath)
                } else {
                    emptyList()
                }

                ToolProgressBus.update(tool.name, 1f, "Search completed, found ${matchingFiles.size}")

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FindFilesResultData(
                        path = path,
                        pattern = pattern,
                        files = matchingFiles,
                        env = "android"
                    ),
                    error = ""
                )
            }

            if (!rootDir.exists() || !rootDir.isDirectory) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FindFilesResultData(
                        path = path,
                        pattern = pattern,
                        files = emptyList(),
                        env = "android"
                    ),
                    error = "Path does not exist or is not a directory: $path"
                )
            }

            // Recursively find matching files
            val matchingFiles = mutableListOf<String>()
            val progress = FindFilesProgressState(lastPath = rootDir.absolutePath)
            findMatchingFiles(
                rootDir,
                regex,
                matchingFiles,
                usePathPattern,
                maxDepth,
                0,
                rootDir.absolutePath,
                tool.name,
                progress
            )

            ToolProgressBus.update(tool.name, 1f, "Search completed, found ${matchingFiles.size}")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = matchingFiles,
                    env = "android"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error searching for files", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = emptyList(),
                    env = "android"
                ),
                error = "Error searching for files: ${e.message}"
            )
        }
    }

    /** Helper method to convert glob pattern to regex */
    protected fun globToRegex(glob: String, caseInsensitive: Boolean): Regex {
        val regex = StringBuilder("^")

        for (i in glob.indices) {
            val c = glob[i]
            when (c) {
                '*' -> regex.append(".*")
                '?' -> regex.append(".")
                '.' -> regex.append("\\.")
                '\\' -> regex.append("\\\\")
                '[' -> regex.append("[")
                ']' -> regex.append("]")
                '(' -> regex.append("\\(")
                ')' -> regex.append("\\)")
                '{' -> regex.append("(")
                '}' -> regex.append(")")
                ',' -> regex.append("|")
                else -> regex.append(c)
            }
        }

        regex.append("$")

        return if (caseInsensitive) {
            Regex(regex.toString(), RegexOption.IGNORE_CASE)
        } else {
            Regex(regex.toString())
        }
    }

    private data class FindFilesProgressState(
        var visited: Int = 0,
        var discovered: Int = 1,
        var matched: Int = 0,
        var progressFloor: Float = 0f,
        var lastUpdateTimeMs: Long = 0L,
        var lastPath: String = ""
    )

    /** Helper method to recursively find files matching a pattern */
    private fun findMatchingFiles(
        dir: File,
        regex: Regex,
        results: MutableList<String>,
        usePathPattern: Boolean,
        maxDepth: Int,
        currentDepth: Int,
        rootPath: String,
        toolName: String,
        progress: FindFilesProgressState
    ) {
        if (maxDepth >= 0 && currentDepth > maxDepth) {
            return
        }

        val files = dir.listFiles() ?: return

        progress.visited++
        progress.discovered += files.size
        progress.lastPath = dir.absolutePath

        val now = System.currentTimeMillis()
        if (progress.lastUpdateTimeMs == 0L) {
            progress.lastUpdateTimeMs = now
        }
        if (now - progress.lastUpdateTimeMs >= 500L || progress.visited % 200 == 0) {
            val candidate =
                if (progress.discovered > 0) {
                    (progress.visited.toFloat() / progress.discovered.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            if (candidate > progress.progressFloor) {
                progress.progressFloor = candidate
            }
            val rel =
                if (progress.lastPath.startsWith(rootPath)) {
                    progress.lastPath.removePrefix(rootPath).trimStart(File.separatorChar).ifBlank { "." }
                } else {
                    progress.lastPath
                }
            ToolProgressBus.update(
                toolName,
                progress.progressFloor,
                "Searching... scanned ${progress.visited}, found ${progress.matched} (at $rel)"
            )
            progress.lastUpdateTimeMs = now
        }

        for (file in files) {
            val relativePath = file.absolutePath.substring(rootPath.length + 1)

            val testString = if (usePathPattern) relativePath else file.name

            if (regex.matches(testString)) {
                results.add(file.absolutePath)
                progress.matched++
            }

            if (file.isDirectory) {
                if (maxDepth >= 0 && currentDepth + 1 > maxDepth) {
                    continue
                }
                findMatchingFiles(
                    file,
                    regex,
                    results,
                    usePathPattern,
                    maxDepth,
                    currentDepth + 1,
                    rootPath,
                    toolName,
                    progress
                )
            } else {
                progress.visited++
                progress.lastPath = file.absolutePath
                val nowFile = System.currentTimeMillis()
                if (nowFile - progress.lastUpdateTimeMs >= 500L || progress.visited % 200 == 0) {
                    val candidate =
                        if (progress.discovered > 0) {
                            (progress.visited.toFloat() / progress.discovered.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    if (candidate > progress.progressFloor) {
                        progress.progressFloor = candidate
                    }
                    val rel =
                        if (progress.lastPath.startsWith(rootPath)) {
                            progress.lastPath.removePrefix(rootPath).trimStart(File.separatorChar)
                        } else {
                            progress.lastPath
                        }
                    ToolProgressBus.update(
                        toolName,
                        progress.progressFloor,
                        "Searching... scanned ${progress.visited}, found ${progress.matched} (at $rel)"
                    )
                    progress.lastUpdateTimeMs = nowFile
                }
            }
        }
    }

    /** Get file information */
    open suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.fileInfo(tool)
        }

        if (isSafEnvironment(environment)) {
            return safTools.fileInfo(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileInfoData(
                    path = "",
                    exists = false,
                    fileType = "",
                    size = 0,
                    permissions = "",
                    owner = "",
                    group = "",
                    lastModified = "",
                    rawStatOutput = "",
                    env = "android"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileInfoData(
                        path = path,
                        exists = false,
                        fileType = "",
                        size = 0,
                        permissions = "",
                        owner = "",
                        group = "",
                        lastModified = "",
                        rawStatOutput = "",
                        env = "android"
                    ),
                    error = "File or directory does not exist: $path"
                )
            }

            // Get file type
            val fileType =
                when {
                    file.isDirectory -> "directory"
                    file.isFile -> "file"
                    else -> "other"
                }

            // Get permissions
            val permissions = getFilePermissions(file)

            // Owner and group info are not easily available in Java
            val owner = System.getProperty("user.name") ?: ""
            val group = ""

            // Last modified time
            val lastModified =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(Date(file.lastModified()))

            // Size
            val size = if (file.isFile) file.length() else 0

            // Collect all file info into a raw string
            val rawInfo = StringBuilder()
            rawInfo.append("File: $path\n")
            rawInfo.append("Size: $size bytes\n")
            rawInfo.append("Type: $fileType\n")
            rawInfo.append("Permissions: $permissions\n")
            rawInfo.append("Last Modified: $lastModified\n")
            rawInfo.append("Owner: $owner\n")
            if (file.canRead()) rawInfo.append("Access: Readable\n")
            if (file.canWrite()) rawInfo.append("Access: Writable\n")
            if (file.canExecute()) rawInfo.append("Access: Executable\n")
            if (file.isHidden()) rawInfo.append("Hidden: Yes\n")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileInfoData(
                    path = path,
                    exists = true,
                    fileType = fileType,
                    size = size,
                    permissions = permissions,
                    owner = owner,
                    group = group,
                    lastModified = lastModified,
                    rawStatOutput = rawInfo.toString(),
                    env = "android"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting file information", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileInfoData(
                    path = path,
                    exists = false,
                    fileType = "",
                    size = 0,
                    permissions = "",
                    owner = "",
                    group = "",
                    lastModified = "",
                    rawStatOutput = "",
                    env = "android"
                ),
                error = "Error getting file information: ${e.message}"
            )
        }
    }

    /** Zip files or directories */
    open suspend fun zipFiles(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val zipPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val includeRootDirectoryParameter = tool.parameters.find { it.name == "include_root_directory" }
        val includeRootDirectory =
            if (includeRootDirectoryParameter == null) {
                true
            } else {
                includeRootDirectoryParameter.value.toBooleanStrictOrNull()
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "include_root_directory must be true or false"
                    )
            }
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(zipPath, tool.name, "destination")?.let { return it }

        val actualSourcePath = PathMapper.resolvePath(context, sourcePath, environment)
        val actualZipPath = PathMapper.resolvePath(context, zipPath, environment)

        if (sourcePath.isBlank() || zipPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Source and destination parameters are required"
            )
        }

        return try {
            val sourceFile = File(actualSourcePath)
            val destZipFile = File(actualZipPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                    "Source file or directory does not exist: $sourcePath"
                )
            }

            // Create parent directory for zip file if needed
            val zipDir = destZipFile.parentFile
            if (zipDir != null && !zipDir.exists()) {
                zipDir.mkdirs()
            }

            // Initialize buffer for file operations
            val buffer = ByteArray(1024)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFile))).use { zos ->
                if (sourceFile.isDirectory) {
                    // Keep legacy behavior by default, but allow callers to zip only directory contents.
                    if (includeRootDirectory) {
                        addDirectoryToZip(sourceFile, sourceFile.name, zos)
                    } else {
                        addDirectoryContentsToZip(sourceFile, sourceFile, zos)
                    }
                } else {
                    // For a single file, add it directly
                    val entryName = sourceFile.name
                    zos.putNextEntry(ZipEntry(entryName))

                    FileInputStream(sourceFile).use { fis ->
                        BufferedInputStream(fis).use { bis ->
                            var len: Int
                            while (bis.read(buffer).also { len = it } >
                                0) {
                                zos.write(buffer, 0, len)
                            }
                        }
                    }

                    zos.closeEntry()
                }
            }

            if (destZipFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileOperationData(
                        operation = "zip",
                        path = sourcePath,
                        successful = true,
                        details =
                        "Successfully compressed $sourcePath to $zipPath"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to create zip file"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error compressing files", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error compressing files: ${e.message}"
            )
        }
    }

    /** Helper method to add directory contents to zip */
    private fun addDirectoryToZip(dir: File, baseName: String, zos: ZipOutputStream) {
        val buffer = ByteArray(1024)
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                addDirectoryToZip(file, "$baseName/${file.name}", zos)
                continue
            }

            val entryName = "$baseName/${file.name}"
            zos.putNextEntry(ZipEntry(entryName))

            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    var len: Int
                    while (bis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
            }

            zos.closeEntry()
        }
    }

    /** Helper method to add directory contents to zip without prefixing the source directory name */
    private fun addDirectoryContentsToZip(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        val buffer = ByteArray(1024)
        val files = currentDir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                addDirectoryContentsToZip(rootDir, file, zos)
                continue
            }

            val entryName = file.relativeTo(rootDir).invariantSeparatorsPath
            zos.putNextEntry(ZipEntry(entryName))

            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    var len: Int
                    while (bis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
            }

            zos.closeEntry()
        }
    }

    /** Unzip a zip file */
    open suspend fun unzipFiles(tool: AITool): ToolResult {
        val zipPath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        PathValidator.validateAndroidPath(zipPath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        val actualZipPath = PathMapper.resolvePath(context, zipPath, environment)
        val actualDestPath = PathMapper.resolvePath(context, destPath, environment)

        if (zipPath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Source and destination parameters are required"
            )
        }

        return try {
            ToolProgressBus.update(tool.name, -1f, "Preparing to unzip...")
            val zipFile = File(actualZipPath)
            val destDir = File(actualDestPath)

            if (!zipFile.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Zip file does not exist: $zipPath"
                )
            }

            if (!zipFile.isFile) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source path is not a file: $zipPath"
                )
            }

            // Create destination directory if needed
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            val totalEntries = try {
                ZipFile(zipFile).use { it.size() }
            } catch (_: Exception) {
                null
            }
            var processedEntries = 0

            val destDirCanonical = destDir.canonicalPath + File.separator
            val buffer = ByteArray(64 * 1024)

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var zipEntry: ZipEntry? = zis.nextEntry

                while (zipEntry != null) {
                    val fileName = zipEntry.name
                    val newFile = File(destDir, fileName)

                    val newFileCanonical = newFile.canonicalPath
                    if (!newFileCanonical.startsWith(destDirCanonical)) {
                        throw SecurityException("Zip entry is outside of the target dir: $fileName")
                    }

                    // Create parent directories if needed
                    val parentDir = newFile.parentFile
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs()
                    }

                    if (zipEntry.isDirectory) {
                        // Create directory if it doesn't exist
                        if (!newFile.exists()) {
                            newFile.mkdirs()
                        }
                    } else {
                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (zis.read(buffer).also {
                                        len = it
                                    } > 0) {
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                    }

                    zis.closeEntry()
                    processedEntries++
                    val progress =
                        if (totalEntries != null && totalEntries > 0) {
                            (processedEntries.toFloat() / totalEntries.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                    val msg =
                        if (totalEntries != null && totalEntries > 0) {
                            "Unzipping... ($processedEntries/$totalEntries)"
                        } else {
                            "Unzipping..."
                        }
                    ToolProgressBus.update(tool.name, progress, msg)
                    zipEntry = zis.nextEntry
                }
            }

            ToolProgressBus.update(tool.name, 1f, "Unzip completed")
            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "unzip",
                    path = zipPath,
                    successful = true,
                    details =
                    "Successfully extracted $zipPath to $destPath"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting zip file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error extracting zip file: ${e.message}"
            )
        } finally {
            ToolProgressBus.clear()
        }
    }

    /** Create a file by delegating to apply_file with type=create */
    open suspend fun createFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val newContent = tool.parameters.find { it.name == "new" }?.value ?: ""

        val applyResult = applyFile(
            AITool(
                name = "apply_file",
                parameters = listOfNotNull(
                    ToolParameter("path", path),
                    ToolParameter("type", "create"),
                    ToolParameter("new", newContent),
                    environment?.takeIf { it.isNotBlank() }?.let { ToolParameter("environment", it) }
                )
            )
        ).last()

        return wrapApplyFileResult(tool.name, applyResult)
    }

    /** Edit a file by delegating to apply_file with type=replace */
    open suspend fun editFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val oldContent = tool.parameters.find { it.name == "old" }?.value ?: ""
        val newContent = tool.parameters.find { it.name == "new" }?.value ?: ""

        val applyResult = applyFile(
            AITool(
                name = "apply_file",
                parameters = listOfNotNull(
                    ToolParameter("path", path),
                    ToolParameter("type", "replace"),
                    ToolParameter("old", oldContent),
                    ToolParameter("new", newContent),
                    environment?.takeIf { it.isNotBlank() }?.let { ToolParameter("environment", it) }
                )
            )
        ).last()

        return wrapApplyFileResult(tool.name, applyResult)
    }

    private fun wrapApplyFileResult(toolName: String, applyResult: ToolResult): ToolResult {
        return applyResult.copy(toolName = toolName)
    }

    /**
     * 智能应用文件绑定，将AI生成的代码与原始文件内容智能合并 该工具会读取原始文件内容，应用AI生成的代码（通常包含//existing code标记）， 然后将合并后的内容写回文件
     */
    open fun applyFile(tool: AITool): Flow<ToolResult> = flow {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val typeParam = tool.parameters.find { it.name == "type" }?.value
        val oldParam = tool.parameters.find { it.name == "old" }?.value
        val newParam = tool.parameters.find { it.name == "new" }?.value
        ToolProgressBus.update(tool.name, 0f, "Preparing...")
        try {
            PathValidator.validateAndroidPath(path, tool.name)?.let {
                emit(it)
                return@flow
            }

            if (path.isBlank()) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            path = "",
                            successful = false,
                            details = "Path parameter is required"
                        ),
                        error = "Path parameter is required"
                    )
                )
                return@flow
            }

            val operationType = typeParam?.trim()?.lowercase()
            if (operationType.isNullOrBlank()) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                            FileOperationData(
                                operation = "apply",
                                path = path,
                                successful = false,
                                details = "Type parameter is required (replace | delete | create)"
                            ),
                        error = "Type parameter is required (replace | delete | create)"
                    )
                )
                return@flow
            }

            ToolProgressBus.update(tool.name, 0.05f, "Checking file...")
            val fileExistsResult =
                fileExists(
                    AITool(
                        name = "file_exists", parameters = listOf(
                            ToolParameter("path", path),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

            if (!fileExistsResult.success ||
                !(fileExistsResult.result as FileExistsData).exists
            ) {
                if (operationType != "create") {
                    emit(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                FileOperationData(
                                    operation = "apply",
                                    path = path,
                                    successful = false,
                                    details = "File does not exist. Use type=create with 'new' to create it."
                                ),
                            error = "File does not exist. Use type=create with 'new' to create it."
                        )
                    )
                    return@flow
                }

                val newContent = newParam ?: ""
                if (newContent.isBlank()) {
                    emit(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                FileOperationData(
                                    operation = "create",
                                    env = environment.orEmpty().trim().ifBlank { "android" },
                                    path = path,
                                    successful = false,
                                    details = "Parameter 'new' is required for type=create"
                                ),
                            error = "Parameter 'new' is required for type=create"
                        )
                    )
                    return@flow
                }

                ToolProgressBus.update(tool.name, 0.4f, "Creating file...")
                AppLogger.d(TAG, "File does not exist. Creating new file '$path'...")

                val writeResult =
                    writeFile(
                        AITool(
                            name = "write_file",
                            parameters =
                                listOf(
                                    ToolParameter("path", path),
                                    ToolParameter("content", newContent),
                                    ToolParameter("environment", environment ?: "")
                                )
                        )
                    )

                val diffContent = FileBindingService(context).generateUnifiedDiff("", newContent)

                if (writeResult.success) {
                    ToolProgressBus.update(tool.name, 1f, "Completed")
                    emit(
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                FileApplyResultData(
                                    operation =
                                        FileOperationData(
                                            operation = "create",
                                            env = environment.orEmpty().trim().ifBlank { "android" },
                                            path = path,
                                            successful = true,
                                            details = "Successfully created new file: $path"
                                        ),
                                    aiDiffInstructions = "",
                                    diffContent = diffContent
                                )
                        )
                    )
                } else {
                    emit(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                FileOperationData(
                                    operation = "create",
                                    env = environment.orEmpty().trim().ifBlank { "android" },
                                    path = path,
                                    successful = false,
                                    details = "Failed to create new file: ${writeResult.error}"
                                ),
                            error = "Failed to create new file: ${writeResult.error}"
                        )
                    )
                }
                return@flow
            }

            if (operationType == "create") {
                val errorMsg =
                    "If you need to rewrite a whole existing file: do NOT use apply_file to overwrite it. Instead, call delete_file first, then write_file."
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                            FileOperationData(
                                operation = "apply",
                                env = environment.orEmpty().trim().ifBlank { "android" },
                                path = path,
                                successful = false,
                                details = errorMsg
                            ),
                        error = errorMsg
                    )
                )
                return@flow
            }

            ToolProgressBus.update(tool.name, 0.15f, "Reading file...")
            val readResult =
                readFileFull(
                    AITool(
                        name = "read_file_full", parameters = listOf(
                            ToolParameter("path", path),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

            if (!readResult.success) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            env = environment.orEmpty().trim().ifBlank { "android" },
                            path = path,
                            successful = false,
                            details =
                            "Failed to read original file: ${readResult.error}"
                        ),
                        error = "Failed to read original file: ${readResult.error}"
                    )
                )
                return@flow
            }

            val originalContent = (readResult.result as? FileContentData)?.content ?: ""

            val editOperations =
                when (operationType) {
                    "replace" -> {
                        val oldContent = oldParam ?: ""
                        val newContent = newParam ?: ""
                        if (oldContent.isBlank() || newContent.isBlank()) {
                            emit(
                                ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result =
                                        FileOperationData(
                                            operation = "apply",
                                            env = environment.orEmpty().trim().ifBlank { "android" },
                                            path = path,
                                            successful = false,
                                            details = "Both 'old' and 'new' are required for type=replace"
                                        ),
                                    error = "Both 'old' and 'new' are required for type=replace"
                                )
                            )
                            return@flow
                        }
                        listOf(
                            FileBindingService.StructuredEditOperation(
                                action = FileBindingService.StructuredEditAction.REPLACE,
                                oldContent = oldContent,
                                newContent = newContent
                            )
                        )
                    }
                    "delete" -> {
                        val oldContent = oldParam ?: ""
                        if (oldContent.isBlank()) {
                            emit(
                                ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result =
                                        FileOperationData(
                                            operation = "apply",
                                            env = environment.orEmpty().trim().ifBlank { "android" },
                                            path = path,
                                            successful = false,
                                            details = "Parameter 'old' is required for type=delete"
                                        ),
                                    error = "Parameter 'old' is required for type=delete"
                                )
                            )
                            return@flow
                        }
                        listOf(
                            FileBindingService.StructuredEditOperation(
                                action = FileBindingService.StructuredEditAction.DELETE,
                                oldContent = oldContent
                            )
                        )
                    }
                    else -> {
                        emit(
                            ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                    FileOperationData(
                                        operation = "apply",
                                        env = environment.orEmpty().trim().ifBlank { "android" },
                                        path = path,
                                        successful = false,
                                        details = "Unsupported type: $operationType (expected replace | delete | create)"
                                    ),
                                error = "Unsupported type: $operationType (expected replace | delete | create)"
                            )
                        )
                        return@flow
                    }
                }

            ToolProgressBus.update(tool.name, 0.2f, "Applying patch...")
            val lastEmitMs = java.util.concurrent.atomic.AtomicLong(0L)
            val bindingResult =
                EnhancedAIService.applyFileBindingOperations(context, originalContent, editOperations) { p, msg ->
                    val now = System.currentTimeMillis()
                    val last = lastEmitMs.get()
                    if (now - last < 200L) return@applyFileBindingOperations
                    if (!lastEmitMs.compareAndSet(last, now)) return@applyFileBindingOperations

                    val mapped = (0.2f + 0.6f * p).coerceIn(0f, 0.99f)
                    ToolProgressBus.update(tool.name, mapped, msg)
                }
            val mergedContent = bindingResult.first
            val aiInstructions = bindingResult.second

            if (aiInstructions.startsWith("Error", ignoreCase = true)) {
                AppLogger.e(TAG, "File binding failed: $aiInstructions")
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            env = environment.orEmpty().trim().ifBlank { "android" },
                            path = path,
                            successful = false,
                            details = "File binding failed: $aiInstructions"
                        ),
                        error = aiInstructions
                    )
                )
                return@flow
            }

            ToolProgressBus.update(tool.name, 0.85f, "Writing file...")
            val writeResult =
                writeFile(
                    AITool(
                        name = "write_file",
                        parameters =
                        listOf(
                            ToolParameter("path", path),
                            ToolParameter("content", mergedContent),
                            ToolParameter("append", "false"),
                            ToolParameter("environment", environment ?: "")
                        )
                    )
                )

            if (!writeResult.success) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "apply",
                            env = environment.orEmpty().trim().ifBlank { "android" },
                            path = path,
                            successful = false,
                            details =
                            "Failed to write merged content: ${writeResult.error}"
                        ),
                        error = "Failed to write merged content: ${writeResult.error}"
                    )
                )
                return@flow
            }

            val operationData =
                FileOperationData(
                    operation = "apply",
                    env = environment.orEmpty().trim().ifBlank { "android" },
                    path = path,
                    successful = true,
                    details = "Successfully applied AI code to file: $path"
                )

            ToolProgressBus.update(tool.name, 0.92f, "Generating diff...")
            val diffContent =
                FileBindingService(context).generateUnifiedDiff(originalContent, mergedContent)
            ToolProgressBus.update(tool.name, 1f, "Completed")

            emit(
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    FileApplyResultData(
                        operation = operationData,
                        aiDiffInstructions = aiInstructions,
                        diffContent = diffContent
                    ),
                    error = ""
                )
            )
        } finally {
            ToolProgressBus.clear()
        }
    }
        .catch { e ->
            AppLogger.e(TAG, "Error applying file binding", e)
            val path = tool.parameters.find { it.name == "path" }?.value ?: "unknown"
            emit(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "apply",
                        env = tool.parameters.find { it.name == "environment" }?.value.orEmpty().trim().ifBlank { "android" },
                        path = path,
                        successful = false,
                        details = "Error applying file binding: ${e.message}"
                    ),
                    error = "Error applying file binding: ${e.message}"
                )
            )
        }

    /** Download file from URL */
    open suspend fun downloadFile(tool: AITool): ToolResult {
        val urlParam = tool.parameters.find { it.name == "url" }?.value ?: ""
        val visitKey = tool.parameters.find { it.name == "visit_key" }?.value ?: ""
        val linkNumberStr = tool.parameters.find { it.name == "link_number" }?.value
        val imageNumberStr = tool.parameters.find { it.name == "image_number" }?.value

        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val headersParam = tool.parameters.find { it.name == "headers" }?.value
        val environment = tool.parameters.find { it.name == "environment" }?.value
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        val actualDestPath = PathMapper.resolvePath(context, destPath, environment)

        fun parseHeaders(headersJson: String?): Map<String, String> {
            if (headersJson.isNullOrBlank()) return emptyMap()
            return try {
                val result = mutableMapOf<String, String>()
                val jsonObj = JSONObject(headersJson)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = jsonObj.getString(key)
                }
                result
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun parseIndex(raw: String?): Int? {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty()) return null
            return v.toIntOrNull()
        }

        var resolvedUrl = urlParam

        if (resolvedUrl.isBlank()) {
            val linkNumber = parseIndex(linkNumberStr)
            val imageNumber = parseIndex(imageNumberStr)
            if (visitKey.isBlank() || (linkNumber == null && imageNumber == null)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "download",
                        path = destPath,
                        successful = false,
                        details = "Either url or (visit_key + link_number/image_number) is required"
                    ),
                    error = "Either url or (visit_key + link_number/image_number) is required"
                )
            }

            val cached = StandardWebVisitTool.getCachedVisitResult(visitKey)
            if (cached == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "download",
                        path = destPath,
                        successful = false,
                        details = "Invalid visit key."
                    ),
                    error = "Invalid visit key."
                )
            }

            resolvedUrl =
                when {
                    linkNumber != null -> cached.links.getOrNull(linkNumber - 1)?.url
                    else -> cached.imageLinks.getOrNull(imageNumber!! - 1)
                } ?: ""

            if (resolvedUrl.isBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "download",
                        path = destPath,
                        successful = false,
                        details = "Index out of bounds."
                    ),
                    error = "Index out of bounds."
                )
            }
        }

        if (resolvedUrl.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "download",
                    path = destPath,
                    successful = false,
                    details =
                    "URL and destination parameters are required"
                ),
                error = "URL and destination parameters are required"
            )
        }

        // Validate URL format
        if (!resolvedUrl.startsWith("http://") && !resolvedUrl.startsWith("https://")) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "download",
                    path = destPath,
                    successful = false,
                    details = "URL must start with http:// or https://"
                ),
                error = "URL must start with http:// or https://"
            )
        }

        return try {
            val destFile = File(actualDestPath)

            fun formatSize(bytes: Long): String {
                return when {
                    bytes > 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                    bytes > 1024 -> String.format("%.2f KB", bytes / 1024.0)
                    else -> "$bytes bytes"
                }
            }

            ToolProgressBus.update(tool.name, 0f, "Connecting...")
            try {
                val destParent = destFile.parentFile
                if (destParent != null && !destParent.exists()) {
                    destParent.mkdirs()
                }

                val lastEmitMs = java.util.concurrent.atomic.AtomicLong(0L)
                val headers = parseHeaders(headersParam)
                HttpMultiPartDownloader.download(resolvedUrl, destFile, headers = headers, threadCount = 4) { downloaded, total ->
                    val now = System.currentTimeMillis()
                    val last = lastEmitMs.get()
                    if (now - last < 200L) return@download
                    if (!lastEmitMs.compareAndSet(last, now)) return@download

                    val p = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f
                    val msg =
                        if (total > 0L) {
                            val percent = ((downloaded.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                            "Downloading... $percent% (${formatSize(downloaded)}/${formatSize(total)})"
                        } else {
                            "Downloading... ${formatSize(downloaded)}"
                        }
                    ToolProgressBus.update(tool.name, p, msg)
                }
                ToolProgressBus.update(tool.name, 1f, "Completed")

                if (destFile.exists()) {
                    val fileSize = destFile.length()
                    val formattedSize = formatSize(fileSize)
                    return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileOperationData(
                            operation = "download",
                            path = destPath,
                            successful = true,
                            details =
                            "File downloaded successfully: $resolvedUrl -> $destPath (file size: $formattedSize)"
                        ),
                        error = ""
                    )
                } else {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                        FileOperationData(
                            operation = "download",
                            path = destPath,
                            successful = false,
                            details =
                            "Download completed but file was not created"
                        ),
                        error = "Download completed but file was not created"
                    )
                }
            } finally {
                ToolProgressBus.clear()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error downloading file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "download",
                    path = destPath,
                    successful = false,
                    details = "Error downloading file: ${e.message}"
                ),
                error = "Error downloading file: ${e.message}"
            )
        }
    }

    /** Open file with system default app */
    open suspend fun openFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.openFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "open",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "open",
                        env = "android",
                        path = path,
                        successful = false,
                        details = "File does not exist: $path"
                    ),
                    error = "File does not exist: $path"
                )
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    ?: "*/*"

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            context.startActivity(intent)

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "open",
                    env = "android",
                    path = path,
                    successful = true,
                    details = "Request to open file sent to system: $path"
                ),
                error = ""
            )
        } catch (e: ActivityNotFoundException) {
            AppLogger.e(TAG, "No activity found to handle opening file: $path", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "open",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "No application found to open this file type."
                ),
                error = "No application found to open this file type."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error opening file", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "open",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "Error opening file: ${e.message}"
                ),
                error = "Error opening file: ${e.message}"
            )
        }
    }

    /**
     * Grep代码搜索工具 - 在指定目录中搜索包含指定模式的代码
     * 依赖 findFiles 和 readFileFull 函数，不直接使用 File 类
     */
    open suspend fun grepCode(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
        val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
        val caseInsensitive =
            tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean() ?: false
        val contextLines =
            tool.parameters.find { it.name == "context_lines" }?.value?.toIntOrNull() ?: 3
        val maxResults =
            tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 100

        val envLabel = environment.orEmpty().trim().ifBlank { "android" }

        AppLogger.d(TAG, "grep_code: Starting search - path=$path, pattern=\"$pattern\", file_pattern=$filePattern, max_results=$maxResults")

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.grepCode(tool)
        }

        if (isSafEnvironment(environment)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "grep_code is not supported for SAF/repo environment"
            )
        }

        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        if (pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Pattern parameter is required"
            )
        }

        return grepCodeWithNativeRipgrep(
            toolName = tool.name,
            path = path,
            pattern = pattern,
            filePattern = filePattern,
            caseInsensitive = caseInsensitive,
            contextLines = contextLines,
            maxResults = maxResults,
            envLabel = envLabel
        )
    }

    open suspend fun grepContext(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
        val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
        val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 10

        if (isLinuxEnvironment(environment)) {
            return linuxTools.grepContext(tool)
        }

        if (isSafEnvironment(environment)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "grep_context is not supported for SAF/repo environment"
            )
        }

        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        if (intent.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Intent parameter is required"
            )
        }

        return try {
            val file = File(path)
            if (file.isFile) {
                grepContextInFile(
                    path = path,
                    intent = intent,
                    maxResults = maxResults,
                    toolName = tool.name
                )
            } else {
                grepContextAgentic(
                    toolName = tool.name,
                    displayPath = path,
                    searchPath = path,
                    environment = null,
                    intent = intent,
                    filePattern = filePattern,
                    maxResults = maxResults,
                    envLabel = "android"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing context search", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error performing context search: ${e.message}"
            )
        }
    }

    protected suspend fun grepContextInFile(
        path: String,
        intent: String,
        maxResults: Int,
        toolName: String
    ): ToolResult {
        val file = File(path)
        val parent = file.parent ?: path
        return grepContextAgentic(
            toolName = toolName,
            displayPath = path,
            searchPath = parent,
            environment = null,
            intent = intent,
            filePattern = file.name,
            maxResults = maxResults,
            envLabel = "android"
        )
    }

    /** Share file via system share dialog */
    open suspend fun shareFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val title = tool.parameters.find { it.name == "title" }?.value ?: "Share File"

        // 如果是Linux环境，委托给LinuxFileSystemTools
        if (isLinuxEnvironment(environment)) {
            return linuxTools.shareFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "share",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                    FileOperationData(
                        operation = "share",
                        env = "android",
                        path = path,
                        successful = false,
                        details = "File does not exist: $path"
                    ),
                    error = "File does not exist: $path"
                )
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    ?: "*/*"

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            val chooser =
                Intent.createChooser(intent, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(chooser)

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileOperationData(
                    operation = "share",
                    env = "android",
                    path = path,
                    successful = true,
                    details = "Share dialog for file opened: $path"
                ),
                error = ""
            )
        } catch (e: ActivityNotFoundException) {
            AppLogger.e(TAG, "No activity found to handle sharing file: $path", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "share",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "No application found to share this file type."
                ),
                error = "No application found to share this file type."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error sharing file", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                FileOperationData(
                    operation = "share",
                    env = "android",
                    path = path,
                    successful = false,
                    details = "Error sharing file: ${e.message}"
                ),
                error = "Error sharing file: ${e.message}"
            )
        }
    }
}

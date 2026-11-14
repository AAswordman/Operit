package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.util.concurrent.*
import kotlin.math.abs

class FileBindingService(context: Context) {

    companion object {
        private const val TAG = "FileBindingService"

        // 修改意图：read_file/read_file_part 的输出带 "N| " 行号前缀，模型常把整段输出粘贴进
        // old；匹配引擎的空白归一化不去除数字与竖线，前缀会同时破坏精确匹配与 n-gram 相似度。
        // 在编辑入口按行剥离该前缀（仅作用于 old；new 是模型新写的内容，保持原样）。
        private val LINE_NUMBER_PREFIX_REGEX = Regex("(?m)^\\s*\\d+\\| ")

        /** 模糊匹配接受阈值 */
        private const val FUZZY_ACCEPT_THRESHOLD = 0.9
    }

    private enum class EditAction {
        REPLACE,
        DELETE
    }

    enum class StructuredEditAction {
        REPLACE,
        DELETE
    }

    /**
     * @param occurrence 显式指定的第 N 处匹配（1-based）；null 表示未指定——
     *   多候选时未指定 occurrence 一律拒绝，绝不静默选第一处
     */
    data class StructuredEditOperation(
        val action: StructuredEditAction,
        val oldContent: String,
        val newContent: String = "",
        val occurrence: Int? = null
    )

    /**
     * 编辑应用结果。
     * 失败约定沿用既有契约：diff 以 "Error" 开头、mergedContent 为原始内容，
     * 调用方（StandardFileSystemTools.applyFile）据此识别失败。
     */
    data class FileEditApplyResult(
        val mergedContent: String,
        val diff: String,
        /** 每个 op 的实际匹配位置（1-based 行号区间），成功时非空 */
        val appliedRanges: List<String>
    )

    private data class EditOperation(
            val action: EditAction,
            val oldContent: String,
            val newContent: String,
            val occurrence: Int?
    )

    private data class MatchSearchResult(
            val bestScore: Double,
            val startLine: Int,
            val endLine: Int,
            val sizeDiff: Int,
            val lengthDiff: Int,
            val windows: Int,
            val lcsCalculations: Int,
            /** 本线程收集到的全部高相似窗口（start, end, score），供跨线程聚类 */
            val highScoreWindows: List<Triple<Int, Int, Double>>
    )

    /** 一个独立的候选位置（1 行号区间 0-based inclusive） */
    private data class MatchCandidate(val start: Int, val end: Int, val score: Double)

    private class MatchOutcome(
        /** 最佳命中（0-based inclusive）；未命中为 -1 */
        val start: Int,
        val end: Int,
        val score: Double,
        /** 命中来自精确匹配（归一化后完全一致） */
        val exact: Boolean,
        /** 独立候选位置，按得分降序；精确匹配时为全部出现位置（均 1.0） */
        val candidates: List<MatchCandidate>,
        /** 未命中时的参考信息：最高相似度与其位置 */
        val bestRejectedScore: Double,
        val bestRejectedRange: Pair<Int, Int>,
    ) {
        val matched: Boolean get() = start >= 0
    }

    /**
     * 对一组结构化编辑操作执行"精确优先、模糊兜底"的匹配并应用。
     *
     * @return [FileEditApplyResult]：成功时 mergedContent 为编辑后内容、diff 为统一 diff、
     *   appliedRanges 为各 op 的匹配行号；失败时 mergedContent 为原内容、diff 以 "Error" 开头。
     */
    suspend fun processFileBindingOperations(
        originalContent: String,
        operations: List<StructuredEditOperation>,
        onProgress: ((Float, String) -> Unit)? = null
    ): FileEditApplyResult {
        if (operations.isEmpty()) {
            return FileEditApplyResult(originalContent, "Error: No valid edit operations provided", emptyList())
        }

        val internalOps = operations.mapNotNull { op ->
            // 修改意图：剥离模型从 read_file/read_file_part 输出整段粘贴带来的 "N| " 行号前缀
            val old = op.oldContent.replace(LINE_NUMBER_PREFIX_REGEX, "")
            if (old.isBlank()) return@mapNotNull null
            when (op.action) {
                StructuredEditAction.REPLACE -> {
                    val newContent = op.newContent
                    if (newContent.isBlank()) return@mapNotNull null
                    EditOperation(EditAction.REPLACE, old, newContent, op.occurrence)
                }
                StructuredEditAction.DELETE -> {
                    EditOperation(EditAction.DELETE, old, "", op.occurrence)
                }
            }
        }

        if (internalOps.isEmpty()) {
            return FileEditApplyResult(originalContent, "Error: No valid edit operations provided", emptyList())
        }

        onProgress?.invoke(0f, "Searching match...")
        val (success, mergedContent, message, appliedRanges) = applyFuzzyOperations(originalContent, internalOps, onProgress)
        if (success) {
            onProgress?.invoke(1f, "Patch applied")
            val diffString = generateDiff(originalContent.replace("\r\n", "\n"), mergedContent)
            return FileEditApplyResult(mergedContent, diffString, appliedRanges)
        }
        return FileEditApplyResult(originalContent, "Error: Could not apply patch. Reason: $message", emptyList())
    }

    private fun generateDiff(original: String, modified: String): String {
        return generateUnifiedDiff(original, modified)
    }

    /**
     * Generates a unified diff string with line numbers and change indicators (+, -).
     * This is a public utility that can be used by other services.
     *
     * @param original The original text content.
     * @param modified The modified text content.
     * @return A formatted string representing the unified diff.
     */
    fun generateUnifiedDiff(original: String, modified: String): String {
        val originalLines = if (original.isEmpty()) emptyList() else original.lines()
        val modifiedLines = if (modified.isEmpty()) emptyList() else modified.lines()
        val patch = DiffUtils.diff(originalLines, modifiedLines)

        if (patch.deltas.isEmpty()) {
            return "No changes detected (files are identical)"
        }

        // First, calculate stats
        val sb = StringBuilder()
        var additions = 0
        var deletions = 0
        patch.deltas.forEach { delta ->
            when (delta.type) {
                com.github.difflib.patch.DeltaType.INSERT -> additions += delta.target.lines.size
                com.github.difflib.patch.DeltaType.DELETE -> deletions += delta.source.lines.size
                com.github.difflib.patch.DeltaType.CHANGE -> {
                    additions += delta.target.lines.size
                    deletions += delta.source.lines.size
                }
                else -> {}
            }
        }
        sb.appendLine("Changes: +$additions -$deletions lines")

        // Generate a standard unified diff to process
        val unifiedDiffLines = UnifiedDiffUtils.generateUnifiedDiff(
            "a/file",
            "b/file",
            originalLines,
            patch,
            3 // Context lines
        )

        val resultLines = mutableListOf<String>()
        var origLineNum = 0
        var newLineNum = 0
        val hunkHeaderRegex = """^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*$""".toRegex()

        for (line in unifiedDiffLines) {
            when {
                line.startsWith("---") || line.startsWith("+++") -> continue // Skip header lines
                line.startsWith("@@") -> {
                    resultLines.add(line)
                    hunkHeaderRegex.find(line)?.let {
                        origLineNum = it.groupValues[1].toInt()
                        newLineNum = it.groupValues[3].toInt()
                    }
                }
                line.startsWith("-") -> {
                    resultLines.add("-${origLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    origLineNum++
                }
                line.startsWith("+") -> {
                    resultLines.add("+${newLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    newLineNum++
                }
                line.startsWith(" ") -> {
                    resultLines.add(" ${origLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    origLineNum++
                    newLineNum++
                }
            }
        }

        sb.append(resultLines.joinToString("\n"))
        return sb.toString()
    }

    private data class FuzzyApplyOutcome(
        val success: Boolean,
        val mergedContent: String,
        val message: String,
        val appliedRanges: List<String>
    )

    private fun applyFuzzyOperations(
        originalContent: String,
        operations: List<EditOperation>,
        onProgress: ((Float, String) -> Unit)? = null
    ): FuzzyApplyOutcome {
        onProgress?.invoke(0f, "Searching match...")

        val originalLines = originalContent.lines().toMutableList()
        val enrichedOps = mutableListOf<Triple<EditOperation, Int, Int>>()
        val appliedRanges = mutableListOf<String>()

        val totalOps = operations.size.coerceAtLeast(1)
        val matchPhaseWeight = 0.8f
        val applyPhaseWeight = 0.2f

        for ((index, op) in operations.withIndex()) {
            val outcome = findBestMatchRange(originalLines, op.oldContent) { p, msg ->
                val overall = (matchPhaseWeight * ((index.toFloat() + p) / totalOps.toFloat()))
                    .coerceIn(0f, 0.99f)
                onProgress?.invoke(overall, "Matching ${index + 1}/$totalOps: $msg")
            }

            if (!outcome.matched) {
                AppLogger.w(TAG, "Could not find a suitable match for OLD block: ${op.oldContent.take(100)}...")
                val bestHint =
                    if (outcome.bestRejectedScore > 0.0) {
                        val (bs, be) = outcome.bestRejectedRange
                        " (best candidate: lines ${bs + 1}-${be + 1}, similarity ${(outcome.bestRejectedScore * 100).toInt()}%, below the ${(FUZZY_ACCEPT_THRESHOLD * 100).toInt()}% threshold)"
                    } else {
                        ""
                    }
                return FuzzyApplyOutcome(
                    false,
                    originalContent,
                    "Could not find a match for an OLD block$bestHint. Re-read the target lines with read_file_part, refine the old block, then retry." +
                        " Do NOT fall back to shell commands to edit files.",
                    emptyList()
                )
            }

            val candidates = outcome.candidates
            val chosen =
                if (candidates.size == 1) {
                    candidates[0]
                } else if (op.occurrence != null && op.occurrence in 1..candidates.size) {
                    candidates[op.occurrence - 1]
                } else {
                    // 修改意图：多候选时绝不静默选第一处（旧实现只拒绝"完美重复"，0.9-0.99
                    // 相似度的近似重复会被静默替换为最优窗，存在改错位置的风险）。
                    // 未指定 occurrence 一律拒绝并列出各候选行号，模型可补上下文或指定 occurrence
                    val listing = candidates.joinToString("; ") { c ->
                        "lines ${c.start + 1}-${c.end + 1} (${(c.score * 100).toInt()}%)"
                    }
                    AppLogger.w(TAG, "Multiple match locations for OLD block; aborting to avoid ambiguous replacement.")
                    return FuzzyApplyOutcome(
                        false,
                        originalContent,
                        "The OLD block matches ${candidates.size} distinct locations: $listing. " +
                            "Add more surrounding context to make it unique, or pass occurrence=1..${candidates.size} to target the Nth match.",
                        emptyList()
                    )
                }

            val start = chosen.start
            val end = chosen.end
            enrichedOps.add(Triple(op, start, end))
            appliedRanges.add(
                "${op.action.name.lowercase()} → lines ${start + 1}-${end + 1} (${(chosen.score * 100).toInt()}%" +
                    (if (outcome.exact) ", exact" else ", fuzzy") + ")"
            )
        }

        // Sort operations by start line in descending order to apply from the bottom up
        enrichedOps.sortByDescending { it.second }

        var applied = 0
        for ((op, start, end) in enrichedOps) {
            AppLogger.d(TAG, "Applying ${op.action} at lines ${start + 1}-${end + 1}")

            val originalSegment = originalLines.subList(start, end + 1).toList()
            val boundaryPreservingLines = tryApplyBoundaryPreservingEdit(originalSegment, op)

            for (i in end downTo start) {
                originalLines.removeAt(i)
            }

            if (boundaryPreservingLines != null) {
                originalLines.addAll(start, boundaryPreservingLines)
            } else if (op.action == EditAction.REPLACE) {
                val newLinesRaw = op.newContent.lines()

                val newLines = if (originalSegment.isNotEmpty() &&
                    start == end &&
                    newLinesRaw.size == 1
                ) {
                    val originalFirstLine = originalSegment.first()
                    val indentPrefix = originalFirstLine.takeWhile { it == ' ' || it == '\t' }
                    val newLine = newLinesRaw.first()

                    if (indentPrefix.isNotEmpty() &&
                        !newLine.startsWith(" ") &&
                        !newLine.startsWith("\t")
                    ) {
                        listOf(indentPrefix + newLine)
                    } else {
                        newLinesRaw
                    }
                } else {
                    newLinesRaw
                }

                originalLines.addAll(start, newLines)
            }

            applied++
            val overall = (matchPhaseWeight + (applyPhaseWeight * (applied.toFloat() / totalOps.toFloat())))
                .coerceIn(0f, 0.99f)
            onProgress?.invoke(overall, "Applying ${applied}/$totalOps")
        }

        return FuzzyApplyOutcome(true, originalLines.joinToString("\n"), "", appliedRanges)
    }

    private fun tryApplyBoundaryPreservingEdit(
        originalSegment: List<String>,
        op: EditOperation
    ): List<String>? {
        if (originalSegment.isEmpty()) return null

        val oldLines = op.oldContent.lines()
        if (oldLines.isEmpty()) return null

        val startIndex = findUniqueOccurrence(originalSegment.first(), oldLines.first()) ?: return null
        val endIndex = findUniqueOccurrence(originalSegment.last(), oldLines.last()) ?: return null

        val prefix = originalSegment.first().substring(0, startIndex)
        val suffix = originalSegment.last().substring(endIndex + oldLines.last().length)

        return when (op.action) {
            EditAction.REPLACE -> {
                val newLines = op.newContent.lines()
                if (newLines.isEmpty()) return null
                when (newLines.size) {
                    1 -> listOf(prefix + newLines.first() + suffix)
                    else -> buildList {
                        add(prefix + newLines.first())
                        addAll(newLines.subList(1, newLines.lastIndex))
                        add(newLines.last() + suffix)
                    }
                }
            }
            EditAction.DELETE -> {
                val updatedLine = prefix + suffix
                if (updatedLine.isEmpty()) emptyList() else listOf(updatedLine)
            }
        }
    }

    private fun findUniqueOccurrence(line: String, fragment: String): Int? {
        if (fragment.isEmpty()) return null

        val firstIndex = line.indexOf(fragment)
        if (firstIndex == -1) return null

        val duplicateMatchIndex = line.indexOf(fragment, firstIndex + fragment.length)
        if (duplicateMatchIndex != -1) return null

        return firstIndex
    }

    /**
     * 查找 old 块的匹配位置。
     *
     * 修改意图（三处结构性修正）：
     * 1. 精确匹配快速路径——归一化后 indexOf 唯一命中直接返回，跳过滑动窗模糊搜索；
     *    旧实现即使完美匹配也要跑完整并行扫描。
     * 2. 多候选拒绝——模糊搜索收集全部 >0.9 的窗口并按行区间重叠聚类为"独立位置"，
     *    独立位置 ≥2 时交由调用方（applyFuzzyOperations）按 occurrence 语义处理；
     *    旧实现只拒绝"完美重复"，近似重复会被静默替换为最优窗（可能改错位置）。
     * 3. 失败可诊断——未命中时回传最高相似度与其行号，供错误信息指导模型重试。
     */
    private fun findBestMatchRange(
        originalLines: List<String>,
        oldContent: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): MatchOutcome {
        val oldContentLines = oldContent.lines()
        val numOldLines = oldContentLines.size
        if (numOldLines == 0 || originalLines.isEmpty()) {
            return MatchOutcome(-1, -1, 0.0, false, emptyList(), 0.0, -1 to -1)
        }

        AppLogger.d(TAG, "开始查找最佳匹配范围，原始文件行数: ${originalLines.size}, 目标块行数: $numOldLines")
        val startTime = System.currentTimeMillis()
        var totalWindows = 0
        var lcsCalculations = 0

        // --- 预计算与规范化 ---
        val normalizedOldContent = oldContent.replace(Regex("\\s+"), "")
        val normalizedOldLength = normalizedOldContent.length
        val baseNgrams = buildNgrams(normalizedOldContent)

        val lineStartIndices = mutableListOf<Int>()
        val normalizedOriginalContent = buildString {
            originalLines.forEachIndexed { index, line ->
                if (index % 1000 == 0 && index > 0) {
                    AppLogger.d(TAG, "正在预处理行: $index/${originalLines.size}")
                }
                lineStartIndices.add(length)
                append(line.replace(Regex("\\s+"), ""))
            }
            lineStartIndices.add(length) // 添加一个末尾索引，方便计算最后一行
        }

        /** 字符索引 → 行索引（lineStartIndices 升序，二分找最后一个 <= charIndex 的行起点） */
        fun lineOfChar(charIndex: Int): Int {
            var lo = 0
            var hi = lineStartIndices.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (lineStartIndices[mid] <= charIndex) lo = mid else hi = mid - 1
            }
            return lo
        }

        // --- 精确匹配快速路径 ---
        if (normalizedOldLength > 0) {
            val exactPositions = mutableListOf<Int>()
            var scanIndex = normalizedOriginalContent.indexOf(normalizedOldContent)
            while (scanIndex >= 0) {
                exactPositions.add(scanIndex)
                scanIndex = normalizedOriginalContent.indexOf(normalizedOldContent, scanIndex + normalizedOldLength)
            }
            if (exactPositions.isNotEmpty()) {
                val candidates = exactPositions.map { charStart ->
                    val charEnd = charStart + normalizedOldLength
                    val startLine = lineOfChar(charStart)
                    val endLine = lineOfChar(charEnd - 1).coerceAtLeast(startLine)
                    MatchCandidate(startLine, endLine, 1.0)
                }
                AppLogger.d(TAG, "精确匹配命中 ${candidates.size} 处（归一化后完全一致）。")
                return MatchOutcome(
                    start = candidates[0].start,
                    end = candidates[0].end,
                    score = 1.0,
                    exact = true,
                    candidates = candidates,
                    bestRejectedScore = 0.0,
                    bestRejectedRange = -1 to -1,
                )
            }
            // 精确未命中：old 去空白后过短则连 n-gram 都无法构建，直接失败
            if (baseNgrams.isEmpty()) {
                AppLogger.w(TAG, "OLD 块在去空白后过短，无法构建 n-gram 且无精确匹配，放弃匹配。")
                return MatchOutcome(-1, -1, 0.0, false, emptyList(), 0.0, -1 to -1)
            }
        } else {
            return MatchOutcome(-1, -1, 0.0, false, emptyList(), 0.0, -1 to -1)
        }

        // --- 模糊路径：滑动窗口尺寸范围 ---
        val delta = (numOldLines * 0.2).toInt() + 2 // 扩大到20%的容错范围，并确保至少有2行的浮动
        val targetSizes = (maxOf(1, numOldLines - delta))..(numOldLines + delta)

        var bestMatchScore = 0.0
        var bestMatchRange = -1 to -1
        var bestMatchSizeDiff = Int.MAX_VALUE
        var bestMatchLengthDiff = Int.MAX_VALUE

        // --- 并行滑动窗口搜索 ---
        val totalIterations = originalLines.size.toLong() * targetSizes.count().toLong()
        AppLogger.d(TAG, "精确未命中，开始滑动窗口匹配（并行），总迭代次数: $totalIterations")

        val processedWindows = java.util.concurrent.atomic.AtomicLong(0L)
        val lastProgressEmitMs = java.util.concurrent.atomic.AtomicLong(0L)

        fun maybeEmitProgress(processed: Long) {
            if (onProgress == null) return
            val total = totalIterations.coerceAtLeast(1L)
            val p = (processed.toDouble() / total.toDouble()).coerceIn(0.0, 0.99)
            val now = System.currentTimeMillis()
            val last = lastProgressEmitMs.get()
            if (now - last < 200L) return
            if (!lastProgressEmitMs.compareAndSet(last, now)) return
            onProgress.invoke(p.toFloat(), "Searching... ${(p * 100).toInt()}%")
        }

        val availableCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val threadCount = minOf(availableCores, originalLines.size)
        val segmentSize = (originalLines.size + threadCount - 1) / threadCount

        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
        val allHighScoreWindows = mutableListOf<Triple<Int, Int, Double>>()
        try {
            val tasks = mutableListOf<java.util.concurrent.Future<MatchSearchResult>>()

            for (threadIndex in 0 until threadCount) {
                val startLine = threadIndex * segmentSize
                val endExclusive = minOf(originalLines.size, startLine + segmentSize)
                if (startLine >= endExclusive) continue

                val task = java.util.concurrent.Callable {
                    var localBestScore = 0.0
                    var localBestStart = -1
                    var localBestEnd = -1
                    var localBestSizeDiff = Int.MAX_VALUE
                    var localBestLengthDiff = Int.MAX_VALUE
                    var localWindows = 0
                    var localLcs = 0
                    var localChunk = 0L
                    // 修改意图：收集本线程所有 >0.9 的窗口（上限防退化），
                    // 合并后按行区间重叠聚类出"独立匹配位置"
                    val localHighScoreWindows = mutableListOf<Triple<Int, Int, Double>>()

                    for (i in startLine until endExclusive) {
                        for (size in targetSizes) {
                            val endLine = i + size
                            if (endLine > originalLines.size) {
                                break
                            }

                            localWindows++
                            localChunk++
                            if (localChunk >= 2048L) {
                                val processed = processedWindows.addAndGet(localChunk)
                                localChunk = 0L
                                maybeEmitProgress(processed)
                            }

                            val startCharIndex = lineStartIndices[i]
                            val endCharIndex = lineStartIndices[endLine]
                            val normalizedWindow =
                                normalizedOriginalContent.substring(startCharIndex, endCharIndex)

                            val sizeDiff = abs(size - numOldLines)
                            val lengthDiff = abs(normalizedWindow.length - normalizedOldLength)

                            localLcs++
                            val score = ngramSimilarity(baseNgrams, normalizedWindow)

                            if (score > FUZZY_ACCEPT_THRESHOLD && localHighScoreWindows.size < 5000) {
                                localHighScoreWindows.add(Triple(i, endLine - 1, score))
                            }

                            val isBetter =
                                (score > localBestScore) ||
                                    (score == localBestScore &&
                                        (sizeDiff < localBestSizeDiff ||
                                            (sizeDiff == localBestSizeDiff &&
                                                (lengthDiff < localBestLengthDiff ||
                                                    (lengthDiff == localBestLengthDiff &&
                                                        (localBestStart == -1 || i < localBestStart))))))

                            if (isBetter) {
                                localBestScore = score
                                localBestStart = i
                                localBestEnd = endLine - 1
                                localBestSizeDiff = sizeDiff
                                localBestLengthDiff = lengthDiff
                                val matchPercentage = (localBestScore * 100).toInt()
                                AppLogger.d(
                                    TAG,
                                    "并行块[$threadIndex] 发现更佳匹配: 行 ${i + 1}-$endLine, 相似度: $matchPercentage%"
                                )
                            }
                        }
                    }

                    if (localChunk > 0L) {
                        val processed = processedWindows.addAndGet(localChunk)
                        maybeEmitProgress(processed)
                    }

                    MatchSearchResult(
                        localBestScore,
                        localBestStart,
                        localBestEnd,
                        localBestSizeDiff,
                        localBestLengthDiff,
                        localWindows,
                        localLcs,
                        localHighScoreWindows
                    )
                }

                tasks.add(executor.submit(task))
            }

            for (future in tasks) {
                try {
                    val result = future.get()
                    totalWindows += result.windows
                    lcsCalculations += result.lcsCalculations
                    allHighScoreWindows.addAll(result.highScoreWindows)

                    val isBetter =
                        (result.bestScore > bestMatchScore) ||
                            (result.bestScore == bestMatchScore &&
                                (result.sizeDiff < bestMatchSizeDiff ||
                                    (result.sizeDiff == bestMatchSizeDiff &&
                                        (result.lengthDiff < bestMatchLengthDiff ||
                                            (result.lengthDiff == bestMatchLengthDiff &&
                                                (bestMatchRange.first == -1 || result.startLine < bestMatchRange.first))))))

                    if (isBetter) {
                        bestMatchScore = result.bestScore
                        bestMatchRange = result.startLine to result.endLine
                        bestMatchSizeDiff = result.sizeDiff
                        bestMatchLengthDiff = result.lengthDiff
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting file binding search result", e)
                }
            }
        } finally {
            executor.shutdown()
        }

        // 记录最终结果
        val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
        AppLogger.d(
            TAG,
            "匹配完成! 最佳相似度: ${(bestMatchScore * 100).toInt()}%, " +
                "总耗时: ${String.format("%.2f", totalTime)}s, " +
                "总窗口数: $totalWindows, 总LCS计算: $lcsCalculations"
        )
        onProgress?.invoke(1f, "Search done")

        if (bestMatchScore <= FUZZY_ACCEPT_THRESHOLD) {
            AppLogger.w(TAG, "未找到足够好的匹配 (最高相似度: ${(bestMatchScore * 100).toInt()}% < ${(FUZZY_ACCEPT_THRESHOLD * 100).toInt()}%)")
            return MatchOutcome(
                -1, -1, bestMatchScore, false, emptyList(),
                bestRejectedScore = bestMatchScore,
                bestRejectedRange = bestMatchRange,
            )
        }

        // 高相似窗口按行区间重叠聚类为"独立匹配位置"，每簇取最优窗
        val candidates = clusterOverlappingWindows(allHighScoreWindows)
        val top = candidates.first()
        AppLogger.d(TAG, "模糊匹配命中 ${candidates.size} 个独立位置，最优: 行 ${top.start + 1}-${top.end + 1}")
        return MatchOutcome(
            start = top.start,
            end = top.end,
            score = top.score,
            exact = false,
            candidates = candidates,
            bestRejectedScore = 0.0,
            bestRejectedRange = -1 to -1,
        )
    }

    /** 将高相似窗口按行区间重叠聚类为独立位置，每簇保留得分最高的窗口，按得分降序返回 */
    private fun clusterOverlappingWindows(
        windows: List<Triple<Int, Int, Double>>
    ): List<MatchCandidate> {
        if (windows.isEmpty()) return emptyList()
        val sorted = windows.sortedWith(compareBy({ it.first }, { it.second }))
        val candidates = mutableListOf<MatchCandidate>()

        var clusterBest = sorted[0]
        var clusterEnd = sorted[0].second
        fun flush() {
            candidates.add(MatchCandidate(clusterBest.first, clusterBest.second, clusterBest.third))
        }

        for (window in sorted.drop(1)) {
            if (window.first <= clusterEnd) {
                // 与当前簇重叠，归入同簇（传递闭包）
                if (window.third > clusterBest.third) clusterBest = window
                clusterEnd = maxOf(clusterEnd, window.second)
            } else {
                flush()
                clusterBest = window
                clusterEnd = window.second
            }
        }
        flush()

        return candidates.sortedByDescending { it.score }
    }

    private fun buildNgrams(s: String, n: Int = 3): Set<String> {
        if (s.length < n) return emptySet()
        return s.windowed(n, 1).toSet()
    }

    private fun ngramSimilarity(baseNgrams: Set<String>, s2: String, n: Int = 3): Double {
        if (baseNgrams.isEmpty() || s2.isEmpty()) return 0.0
        if (s2.length < n) return 0.0

        val ngrams2 = s2.windowed(n, 1).toSet()
        if (ngrams2.isEmpty()) return 0.0

        val intersection = baseNgrams.intersect(ngrams2).size
        val union = baseNgrams.size + ngrams2.size - intersection

        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }
}

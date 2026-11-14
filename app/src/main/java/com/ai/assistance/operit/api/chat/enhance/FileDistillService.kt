package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardFileSystemTools
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import java.io.BufferedReader
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * 修改意图：文件读取 AI 提炼拦截层（docs/TODO/chat_file_tool_hardening/08-AI读取提炼.md）。
 * read_file / read_file_part / read_file_full 默认经功能模型（FunctionType.FILE_DISTILL，
 * 未配置时经 FunctionalConfigManager 既有链路默认主模型）提炼出与 focus 相关的带行号摘录，
 * 降低长任务中文件读取的上下文占用。
 *
 * 透传边界（以下情况一律执行原始读取、行为与现状一致，均属功能范围定义而非降级）：
 * - 开关关闭（ApiPreferences.FILE_DISTILL_ENABLED）
 * - 调用参数 raw=true
 * - repo:/linux 环境（提炼扫描基于 java.io.File 直读，仅覆盖默认 Android 本地环境）
 * - 特殊类型（图片/PDF/文档/音视频，SPECIAL_FILE_EXTENSIONS 抽取管线一律透传）
 * - 文件 ≤ FILE_DISTILL_MIN_BYTES（提炼收益不足以抵消一次模型调用）
 *
 * 失败路径全部显式：模型超时/异常/JSON 不可解析 → 失败 ToolResult 并指引 raw=true 重读，
 * 不做任何回退；focus 缺失 → 失败并要求补传（显式契约）。
 */
object FileDistillService {
    private const val TAG = "FileDistillService"

    /** raw 参数名：true 时直接返回原始内容 */
    private const val PARAM_RAW = "raw"

    /** focus 参数名：AI 提炼模式下描述"想读什么"，必填 */
    private const val PARAM_FOCUS = "focus"

    /** 各环境实现统一的截断标记（read_file 32K 截断时追加），用于向提炼模型声明截断状态 */
    private const val TRUNCATION_MARKER = "(file content truncated)"

    /** 提炼结果头部说明，声明行号语义（可与 edit_file 直接配合） */
    private const val DISTILL_HEADER_PREFIX = "[AI distilled read"

    /** 单份摘录的输出上限；超出按字符截断并注明（资源上限，非语义处理） */
    private const val EXCERPT_BUDGET_CHARS = 24_000

    /** 提炼失败时统一附加的补救指引 */
    private const val RAW_RETRY_HINT = " Set raw=true to read the original content instead."

    /** 拦截的读取工具变体，决定提炼路径 */
    enum class Variant { READ_FILE, READ_FULL, READ_PART }

    // ---------- 对外入口 ----------

    /**
     * read 族工具统一拦截入口。透传判定通过后按变体进入提炼流程；
     * [rawRead] 为原始工具执行（含全部既有校验与报错语义），透传与错误场景直接复用
     */
    suspend fun maybeDistillRead(
        context: android.content.Context,
        tool: AITool,
        variant: Variant,
        rawRead: suspend () -> ToolResult,
    ): ToolResult {
        val rawParam = tool.parameters.find { it.name == PARAM_RAW }?.value?.lowercase()
        if (rawParam == "true" || rawParam == "1") {
            return rawRead()
        }

        val enabled = ApiPreferences.getInstance(context).fileDistillEnabledFlow.first()
        if (!enabled) {
            return rawRead()
        }

        // 修改意图：提炼扫描基于 java.io.File 直读，仅覆盖默认 Android 本地环境；
        // repo:/linux 环境的文件系统不经应用进程可见，保持原始读取
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment != null &&
            (environment.startsWith("repo:", ignoreCase = true) ||
                environment.equals("linux", ignoreCase = true))
        ) {
            return rawRead()
        }

        return when (variant) {
            Variant.READ_FILE -> distillReadFile(context, tool, rawRead)
            Variant.READ_FULL -> distillReadFull(context, tool, rawRead)
            Variant.READ_PART -> distillReadPart(context, tool, rawRead)
        }
    }

    // ---------- read_file：先原始读取（含特殊类型抽取），再对文本结果提炼 ----------

    private suspend fun distillReadFile(
        context: android.content.Context,
        tool: AITool,
        rawRead: suspend () -> ToolResult,
    ): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val result = rawRead()
        // 原始读取失败（路径/大小/二进制门禁等）原样透传，错误语义不变
        if (!result.success) return result
        val data = result.result as? FileContentData ?: return result

        val file = File(path)
        if (path.isBlank() || !file.isFile) return result
        // 特殊类型（图片 OCR/PDF 抽取等）结果一律透传，相关行为不改动
        if (StandardFileSystemTools.isSpecialFileExtension(file.extension)) return result
        if (file.length() <= ToolExecutionLimits.FILE_DISTILL_MIN_BYTES) return result

        val focus = focusParam(tool) ?: return missingFocusResult(tool.name)

        val truncatedNote =
            if (data.content.contains(TRUNCATION_MARKER)) {
                "Note: the content below is a truncated prefix of a larger file."
            } else {
                "The content below is the complete file."
            }

        // 修改意图：单次提炼的模型调用/解析异常统一转显式失败结果并指引 raw=true 重读，
        // 不冒泡到执行框架（与 scanFile 的失败路径同构）
        val verdict =
            try {
                distillSingle(context, path, focus, data.content, truncatedNote)
            } catch (e: TimeoutCancellationException) {
                AppLogger.w(TAG, "文件提炼超时: ${tool.name}, $path")
                return distillFailedResult(tool.name, "AI distill timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "文件提炼失败: ${tool.name}, ${e.message}")
                return distillFailedResult(tool.name, "AI distill failed: ${e.message ?: e.javaClass.simpleName}.")
            }
        return when (verdict) {
            is SingleVerdict.Unrelated -> notFoundResult(tool.name, path, verdict.fileSummary)
            // 修改意图：≥一半内容相关时提炼不省 token，按原样返回原始读取结果（含行号）
            is SingleVerdict.NotWorth -> result
            is SingleVerdict.Excerpt ->
                distilledResult(tool.name, path, focus, verdict.excerpt, scanNote = null)
        }
    }

    // ---------- read_file_full：纯文本自读（带行号），小文件单次提炼、大文件扫描 ----------

    private suspend fun distillReadFull(
        context: android.content.Context,
        tool: AITool,
        rawRead: suspend () -> ToolResult,
    ): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        if (path.isBlank()) return rawRead()

        val file = File(path)
        // 存在性/类型/文本门禁交由原始读取给出与现状一致的报错
        if (!file.exists() || !file.isFile) return rawRead()
        // 特殊类型（图片/PDF/文档/音视频）抽取管线一律透传，相关行为不改动
        if (StandardFileSystemTools.isSpecialFileExtension(file.extension)) return rawRead()
        if (!FileUtils.isTextLike(file)) return rawRead()
        if (file.length() <= ToolExecutionLimits.FILE_DISTILL_MIN_BYTES) return rawRead()

        val focus = focusParam(tool) ?: return missingFocusResult(tool.name)

        if (file.length() <= ToolExecutionLimits.FILE_DISTILL_SINGLE_INPUT_BYTES) {
            val numbered =
                file.bufferedReader().use { reader ->
                    readNumberedChunk(
                        reader,
                        startLine = 1,
                        endLineCap = null,
                        charBudget = ToolExecutionLimits.FILE_DISTILL_SINGLE_INPUT_BYTES,
                    )
                }
            // 修改意图：单次提炼的模型调用/解析异常统一转显式失败结果（与 scanFile 同构）
            val verdict =
                try {
                    distillSingle(
                        context,
                        path,
                        focus,
                        numbered.text,
                        truncatedNote = "The content below is the complete file.",
                    )
                } catch (e: TimeoutCancellationException) {
                    AppLogger.w(TAG, "文件提炼超时: ${tool.name}, $path")
                    return distillFailedResult(tool.name, "AI distill timed out.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "文件提炼失败: ${tool.name}, ${e.message}")
                    return distillFailedResult(tool.name, "AI distill failed: ${e.message ?: e.javaClass.simpleName}.")
                }
            return when (verdict) {
                is SingleVerdict.Unrelated -> notFoundResult(tool.name, path, verdict.fileSummary)
                // 修改意图：≥一半内容相关时直接返回完整原文（带行号），提炼不省 token
                is SingleVerdict.NotWorth ->
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                        FileContentData(
                            path = path,
                            content = numbered.text,
                            size = file.length()
                        ),
                        error = ""
                    )
                is SingleVerdict.Excerpt ->
                    distilledResult(tool.name, path, focus, verdict.excerpt, scanNote = null)
            }
        }

        return scanFile(context, tool, path, file, focus, startLine = 1)
    }

    // ---------- read_file_part：扫描协议，start_line/end_line 可选限定扫描范围 ----------

    private suspend fun distillReadPart(
        context: android.content.Context,
        tool: AITool,
        rawRead: suspend () -> ToolResult,
    ): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        if (path.isBlank()) return rawRead()
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        val file = File(path)
        // 存在性/类型/文本门禁交由原始读取给出与现状一致的报错
        if (!file.exists() || !file.isFile) return rawRead()
        if (!FileUtils.isTextLike(file)) return rawRead()
        if (file.length() <= ToolExecutionLimits.FILE_DISTILL_MIN_BYTES) return rawRead()

        val focus = focusParam(tool) ?: return missingFocusResult(tool.name)
        val startLine =
            tool.parameters.find { it.name == "start_line" }?.value?.toIntOrNull()?.takeIf { it > 0 }
                ?: 1
        val endLineCap = tool.parameters.find { it.name == "end_line" }?.value?.toIntOrNull()
        // 修改意图：start > end 的非法范围交由原始读取给出与现状一致的校验报错
        if (endLineCap != null && endLineCap < startLine) return rawRead()

        return scanFile(context, tool, path, file, focus, startLine, endLineCap)
    }

    // ---------- 扫描协议 ----------

    /**
     * 逐块滚动扫描：每块约 FILE_DISTILL_SCAN_CHUNK_CHARS 字符（带行号）送提炼模型，
     * 每轮收集摘录与 continue_scan；结束后若摘录占比过高（大部分内容都相关）则返回
     * 已扫描区域原文。轮数与扫描总量受 ToolExecutionLimits 上限约束，触顶在结果中明示
     * "扫描未到文件尾"，不静默截断
     */
    private suspend fun scanFile(
        context: android.content.Context,
        tool: AITool,
        path: String,
        file: File,
        focus: String,
        startLine: Int,
        endLineCap: Int? = null,
    ): ToolResult {
        val excerpt = StringBuilder()
        val scannedRaw = StringBuilder()
        var scannedChars = 0
        var excerptChars = 0
        var round = 0
        var continueScan = true
        var reachedEnd = false
        var fileSummary = ""

        try {
            file.bufferedReader().use { reader ->
                // 跳到 startLine（AI 模式下 start_line 缺省为 1，即全文扫描）
                var lineNo = 1
                while (lineNo < startLine) {
                    reader.readLine() ?: break
                    lineNo++
                }

                while (round < ToolExecutionLimits.FILE_DISTILL_SCAN_MAX_ROUNDS &&
                    continueScan &&
                    !reachedEnd
                ) {
                    val chunk =
                        readNumberedChunk(
                            reader,
                            startLine = lineNo,
                            endLineCap = endLineCap,
                            charBudget = ToolExecutionLimits.FILE_DISTILL_SCAN_CHUNK_CHARS,
                        )
                    val chunkText = chunk.text
                    if (chunkText.isBlank()) {
                        reachedEnd = chunk.endReached
                        break
                    }
                    round++
                    scannedChars += chunkText.length
                    scannedRaw.append(chunkText)
                    // 修改意图：扫描总量受单条工具结果预算约束；触顶后停止后续轮次，
                    // reachedEnd 保持 false 使结果明示"扫描未到文件尾"，不静默截断
                    if (scannedChars >= ToolExecutionLimits.MAX_SINGLE_TOOL_RESULT_MESSAGE_CHARS) {
                        continueScan = false
                    }

                    val response =
                        withTimeout(ToolExecutionLimits.FILE_DISTILL_SCAN_ROUND_TIMEOUT_MS) {
                            EnhancedAIService.getInstance(context).callFunctionModel(
                                functionType = FunctionType.FILE_DISTILL,
                                turns =
                                listOf(
                                    PromptTurn(
                                        kind = PromptTurnKind.USER,
                                        content =
                                        FunctionalPrompts.fileDistillScanChunkPrompt(
                                            path = path,
                                            focus = focus,
                                            round = round,
                                            startLine = chunk.firstLine,
                                            endLine = chunk.lastLine,
                                            isLastChunk = chunk.endReached,
                                            hasPriorExcerpt = excerptChars > 0,
                                            chunk = chunkText,
                                        ),
                                    )
                                ),
                                recordTokenUsage = true,
                            )
                        }

                    val parsed = parseScanResponse(response)
                    if (parsed.fileSummary.isNotBlank()) {
                        fileSummary = parsed.fileSummary
                    }
                    if (parsed.related && parsed.excerpt.isNotBlank()) {
                        val piece = capExcerpt(parsed.excerpt)
                        excerpt.append(piece).append('\n')
                        excerptChars += piece.length
                    }
                    continueScan = parsed.continueScan && !chunk.endReached
                    reachedEnd = chunk.endReached
                    lineNo = chunk.lastLine + 1
                }
            }
        } catch (e: TimeoutCancellationException) {
            AppLogger.w(TAG, "文件提炼扫描超时: ${tool.name}, $path")
            return distillFailedResult(tool.name, "AI distill scan timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "文件提炼扫描失败: ${tool.name}, ${e.message}")
            return distillFailedResult(tool.name, "AI distill scan failed: ${e.message ?: e.javaClass.simpleName}.")
        }

        if (excerptChars == 0) {
            return notFoundResult(tool.name, path, fileSummary)
        }

        // 修改意图：摘录占比过高说明大部分内容都相关，提炼不省 token，
        // 按用户规则直接返回相关原文（此处为已扫描区域原文，保留行号）
        if (excerptChars > scannedChars * ToolExecutionLimits.FILE_DISTILL_EXCERPT_RATIO_LIMIT) {
            val original = capTail(scannedRaw.toString(), keepChars = 56_000)
            val note =
                "\n\n(AI distilled read: most of the scanned content matched the focus, " +
                    "so the original scanned region is returned as-is." +
                    if (reachedEnd) ")" else " Scan stopped before the end of the file.)"
            return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                FileContentData(
                    path = path,
                    content = original + note,
                    size = (original.length + note.length).toLong()
                ),
                error = ""
            )
        }

        val scanNote =
            if (!reachedEnd) {
                "Scan stopped before the end of the file (round/chunk limits); " +
                    "more relevant content may exist further down."
            } else {
                null
            }
        return distilledResult(tool.name, path, focus, excerpt.toString(), scanNote)
    }

    // ---------- 单次协议 ----------

    private sealed class SingleVerdict {
        data class Unrelated(val fileSummary: String) : SingleVerdict()

        /** 内容大部分相关，提炼不省 token：调用方返回原文 */
        object NotWorth : SingleVerdict()

        data class Excerpt(val excerpt: String) : SingleVerdict()
    }

    private class ScanVerdict(
        val related: Boolean,
        val excerpt: String,
        val continueScan: Boolean,
        val fileSummary: String,
    )

    private suspend fun distillSingle(
        context: android.content.Context,
        path: String,
        focus: String,
        content: String,
        truncatedNote: String,
    ): SingleVerdict {
        val response =
            withTimeout(ToolExecutionLimits.FILE_DISTILL_SINGLE_TIMEOUT_MS) {
                EnhancedAIService.getInstance(context).callFunctionModel(
                    functionType = FunctionType.FILE_DISTILL,
                    turns =
                    listOf(
                        PromptTurn(
                            kind = PromptTurnKind.USER,
                            content =
                            FunctionalPrompts.fileDistillSinglePrompt(
                                path = path,
                                focus = focus,
                                content = content,
                                truncatedNote = truncatedNote,
                            ),
                        )
                    ),
                    recordTokenUsage = true,
                )
            }
        return parseSingleResponse(response)
    }

    // ---------- 结果构造 ----------

    /** AI 提炼成功结果：头部声明行号语义与 focus，附扫描未完成提示（如有） */
    private fun distilledResult(
        toolName: String,
        path: String,
        focus: String,
        excerpt: String,
        scanNote: String?,
    ): ToolResult {
        val header =
            "$DISTILL_HEADER_PREFIX of $path; focus: $focus]\n" +
                "[Line numbers refer to the original file and can be used with edit_file.]\n" +
                "---\n"
        val body = header + excerpt
        val content =
            if (scanNote != null) "$body\n\n($scanNote)" else body
        return ToolResult(
            toolName = toolName,
            success = true,
            result =
            FileContentData(
                path = path,
                content = content,
                size = content.length.toLong()
            ),
            error = ""
        )
    }

    /** 未找到相关内容：读取本身成功，返回文件概要供主模型决定下一步 */
    private fun notFoundResult(toolName: String, path: String, fileSummary: String): ToolResult {
        val content =
            "[AI distilled read of $path]\n" +
                "No content matching the focus was found in this file.\n" +
                "File summary: ${fileSummary.ifBlank { "n/a" }}"
        return ToolResult(
            toolName = toolName,
            success = true,
            result =
            FileContentData(
                path = path,
                content = content,
                size = content.length.toLong()
            ),
            error = ""
        )
    }

    private fun missingFocusResult(toolName: String): ToolResult =
        ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(""),
            error =
            "The focus parameter is required for AI distilled read. " +
                "Provide focus describing what you want from the file," + RAW_RETRY_HINT
        )

    private fun distillFailedResult(toolName: String, message: String): ToolResult =
        ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(""),
            error = message + RAW_RETRY_HINT
        )

    // ---------- 参数与解析 ----------

    private fun focusParam(tool: AITool): String? {
        val focus =
            tool.parameters.find { it.name == PARAM_FOCUS }?.value?.trim().orEmpty()
        return focus.ifBlank { null }
    }

    /** 一块带行号的扫描文本：firstLine/lastLine 为起止行号，endReached 表示已读到范围末尾 */
    private class NumberedChunk(
        val text: String,
        val firstLine: Int,
        val lastLine: Int,
        val endReached: Boolean,
    )

    /**
     * 从 [reader] 读取一块文本并加 "N| " 行号前缀（格式与实现层 addLineNumbers 一致，
     * 分段间可直接衔接）。行数达到 [charBudget] 字符、越过 [endLineCap]（含）或读至
     * EOF 时停止；endReached 仅在后两种情形为 true
     */
    private fun readNumberedChunk(
        reader: BufferedReader,
        startLine: Int,
        endLineCap: Int?,
        charBudget: Int,
    ): NumberedChunk {
        val chunk = StringBuilder()
        var lineNo = startLine - 1
        var endReached = false
        while (chunk.length < charBudget) {
            if (endLineCap != null && lineNo >= endLineCap) {
                endReached = true
                break
            }
            val line = reader.readLine()
            if (line == null) {
                endReached = true
                break
            }
            lineNo++
            chunk.append(lineNo).append("| ").append(line).append('\n')
        }
        return NumberedChunk(
            text = chunk.toString(),
            firstLine = startLine,
            lastLine = lineNo,
            endReached = endReached,
        )
    }

    /** 单份摘录超预算时按字符截断并注明（资源上限，非语义处理） */
    private fun capExcerpt(excerpt: String): String =
        if (excerpt.length > EXCERPT_BUDGET_CHARS) {
            excerpt.take(EXCERPT_BUDGET_CHARS) + "\n... (excerpt truncated at budget) ..."
        } else {
            excerpt
        }

    /** 保留尾部 keepChars 字符；被裁掉的前缀以省略标记明示 */
    private fun capTail(text: String, keepChars: Int): String =
        if (text.length > keepChars) {
            "(earlier scanned lines omitted)\n" + text.takeLast(keepChars)
        } else {
            text
        }

    /** 宽松解析单次协议 JSON；无法解析视为提炼失败（抛出，由调用方统一转为失败结果） */
    private fun parseSingleResponse(response: String): SingleVerdict {
        val jsonText =
            response.substringBeforeLast("}")
                .substringAfter("{", missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() }
                ?.let { "{$it}" }
        if (jsonText.isNullOrBlank()) {
            throw IllegalStateException("AI distill returned unparseable output.")
        }
        val json = JSONObject(jsonText)
        val related = json.optBoolean("related", false)
        if (!related) {
            return SingleVerdict.Unrelated(json.optString("file_summary", "").trim())
        }
        return if (json.optBoolean("worth", false)) {
            SingleVerdict.Excerpt(json.optString("excerpt", "").trim())
        } else {
            SingleVerdict.NotWorth
        }
    }

    /** 宽松解析扫描单轮 JSON；无法解析视为提炼失败（抛出，由调用方统一转为失败结果） */
    private fun parseScanResponse(response: String): ScanVerdict {
        val jsonText =
            response.substringBeforeLast("}")
                .substringAfter("{", missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() }
                ?.let { "{$it}" }
        if (jsonText.isNullOrBlank()) {
            throw IllegalStateException("AI distill scan returned unparseable output.")
        }
        val json = JSONObject(jsonText)
        return ScanVerdict(
            related = json.optBoolean("related", false),
            excerpt = json.optString("excerpt", "").trim(),
            continueScan = json.optBoolean("continue_scan", false),
            fileSummary = json.optString("file_summary", "").trim(),
        )
    }
}

package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.HighRiskToolCatalog
import com.ai.assistance.operit.core.tools.WorkspacePathChecker
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** 工具调用（文件工具 + 高危工具）的大模型合规审查结果 */
sealed class ToolReviewOutcome {
    /** 审查通过：调用方直接放行（不弹窗） */
    data class Compliant(val reason: String) : ToolReviewOutcome()

    /** 审查不合规：调用方弹窗展示 reason，由用户决定是否放行 */
    data class NonCompliant(val reason: String) : ToolReviewOutcome()

    /** 审查失败（未配置/报错/超时）：调用方弹窗原询问窗口并提示错误信息，由用户决定 */
    data class Failed(val message: String) : ToolReviewOutcome()
}

/**
 * 修改意图：工具模式为"允许"时的工具调用合规初筛（审查范围从文件工具族
 * 泛化为文件工具 + HighRiskToolCatalog 高危工具调用，开关分区见调用方）。
 * 复用功能模型调用范式（FunctionType.TOOL_REVIEW → callFunctionModel），
 * 未配置时 FunctionalConfigManager 自动回落主模型配置；审查失败不静默放行，
 * 统一转为弹窗人工确认（需求确认项）。
 */
object ToolReviewService {
    private const val TAG = "ToolReviewService"

    /** 审查模型调用超时；超时语义与调用失败一致（弹窗人工确认） */
    private const val REVIEW_TIMEOUT_MS = 15_000L

    /** 审查 prompt 参数摘要的截断上限（单值与总量共用） */
    private const val CONTENT_SUMMARY_LIMIT = 500

    /** 调用点前上下文：取最近 10 条大模型消息（含其工具调用），单条截断上限 */
    private const val RECENT_MESSAGE_COUNT = 10
    private const val RECENT_ENTRY_LIMIT = 300

    /** 最近一条用户指令的截断上限 */
    private const val USER_INSTRUCTION_LIMIT = 800

    /** 调用说明（call_purpose）的截断上限 */
    private const val CALL_PURPOSE_LIMIT = 200

    suspend fun review(
        context: android.content.Context,
        tool: AITool,
        scopeLabel: String,
        chatWorkspaceRoot: String?,
        callerChatId: String?,
    ): ToolReviewOutcome {
        // 修改意图：参数摘要从 content 类单参数扩展为全参数 name=value 汇总，
        // 覆盖 command/url/action/package_name/tool_name 等高危工具参数；
        // 单值与总量都截断，避免大内容挤爆审查上下文
        val contentSummary = tool.parameters
            .joinToString("; ") { param -> "${param.name}=${param.value.take(CONTENT_SUMMARY_LIMIT)}" }
            .take(CONTENT_SUMMARY_LIMIT)

        // 调用说明：可审查工具的可选声明参数，与调用点上下文做一致性比对
        val callPurpose = tool.parameters
            .find { it.name == HighRiskToolCatalog.CALL_PURPOSE_PARAM }
            ?.value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(CALL_PURPOSE_LIMIT)
            .orEmpty()

        val (recentContext, userInstruction) = buildReviewContext(context, callerChatId)

        // 高危工具走目录摘要；文件工具与未知名称沿用既有文件族映射
        val operationSummary = HighRiskToolCatalog.operationSummary(tool.name)
            ?: fileOperationSummary(tool.name)

        val targetPaths = WorkspacePathChecker.extractPaths(tool).joinToString(", ")

        return try {
            val response =
                withTimeout(REVIEW_TIMEOUT_MS) {
                    EnhancedAIService.getInstance(context).callFunctionModel(
                        functionType = FunctionType.TOOL_REVIEW,
                        turns =
                            listOf(
                                PromptTurn(
                                    kind = PromptTurnKind.USER,
                                    content =
                                        FunctionalPrompts.toolReviewPrompt(
                                            toolName = tool.name,
                                            operationSummary = operationSummary,
                                            targetPaths = targetPaths,
                                            scope = scopeLabel,
                                            workspaceRoot = chatWorkspaceRoot ?: "",
                                            contentSummary = contentSummary,
                                            callPurpose = callPurpose,
                                            userInstruction = userInstruction,
                                            recentContext = recentContext,
                                        ),
                                )
                            ),
                        recordTokenUsage = false,
                    )
                }

            parseVerdict(response)
        } catch (e: TimeoutCancellationException) {
            AppLogger.w(TAG, "工具审查超时: ${tool.name}")
            ToolReviewOutcome.Failed("Tool review timed out after ${REVIEW_TIMEOUT_MS / 1000} seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "工具审查调用失败: ${tool.name}, ${e.message}")
            ToolReviewOutcome.Failed("Tool review failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 修改意图：审查上下文直读数据库全量历史（ChatHistoryManager.loadChatMessages），
     * 不经运行态压缩摘要——最近一条用户指令必须是真实指令，不受上下文压缩影响。
     * 调用点前上下文取最近 10 条大模型消息（其内容含该轮的工具调用），
     * 剔除 tool_result 结果块避免结果刷屏，仅保留模型文本与调用声明。
     * 无 callerChatId（悬浮球等无会话来源的调用）时返回空串，审查按无上下文判定。
     */
    private suspend fun buildReviewContext(
        context: android.content.Context,
        callerChatId: String?,
    ): Pair<String, String> {
        if (callerChatId.isNullOrBlank()) {
            return "" to ""
        }
        val messages =
            ChatHistoryManager.getInstance(context).loadChatMessages(callerChatId)
        val userInstruction = messages
            .lastOrNull { it.sender == "user" }
            ?.content
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(USER_INSTRUCTION_LIMIT)
            .orEmpty()
        val recentContext = messages
            .filter { it.sender == "ai" }
            .takeLast(RECENT_MESSAGE_COUNT)
            .mapIndexed { index, message ->
                val cleaned =
                    message.content
                        .replace(ChatMarkupRegex.toolResultAnyPattern, " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(RECENT_ENTRY_LIMIT)
                "${index + 1}. $cleaned"
            }
            .joinToString("\n")
        return recentContext to userInstruction
    }

    /** 文件工具族的英文操作摘要；高危目录未覆盖的名称给出通用描述 */
    private fun fileOperationSummary(toolName: String): String =
        when (toolName.substringAfter(':')) {
            "delete_file" -> "delete a file/directory"
            "write_file", "write_file_binary", "create_file" -> "write/create a file"
            "edit_file", "apply_file" -> "edit a file"
            "move_file" -> "move/rename"
            "copy_file" -> "copy"
            "zip_files", "unzip_files" -> "archive operation"
            "read_file", "read_file_part", "read_file_full", "read_file_binary",
            "list_files", "file_exists", "file_info", "find_files", "grep_code",
            "grep_context", -> "read/search (non-destructive)"
            else -> "file operation"
        }

    /** 宽松解析 {"compliant": bool, "reason": string}；无法解析视为审查失败 */
    private fun parseVerdict(response: String): ToolReviewOutcome {
        val jsonText =
            response.substringBeforeLast("}")
                .substringAfter("{", missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() }
                ?.let { "{$it}" }
        if (jsonText.isNullOrBlank()) {
            return ToolReviewOutcome.Failed("Tool review returned unparseable output.")
        }
        return runCatching {
            val json = JSONObject(jsonText)
            val compliant = json.optBoolean("compliant", false)
            val reason = json.optString("reason", "").trim()
            if (compliant) {
                ToolReviewOutcome.Compliant(reason.ifBlank { "review passed" })
            } else {
                ToolReviewOutcome.NonCompliant(reason.ifBlank { "review flagged this call as non-compliant" })
            }
        }.getOrElse {
            ToolReviewOutcome.Failed("Tool review returned unparseable output.")
        }
    }
}

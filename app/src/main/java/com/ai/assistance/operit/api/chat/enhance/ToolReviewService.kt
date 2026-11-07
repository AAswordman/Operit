package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.WorkspacePathChecker
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** 文件工具调用的大模型合规审查结果 */
sealed class ToolReviewOutcome {
    /** 审查通过：调用方直接放行（不弹窗） */
    data class Compliant(val reason: String) : ToolReviewOutcome()

    /** 审查不合规：调用方弹窗展示 reason，由用户决定是否放行 */
    data class NonCompliant(val reason: String) : ToolReviewOutcome()

    /** 审查失败（未配置/报错/超时）：调用方弹窗原询问窗口并提示错误信息，由用户决定 */
    data class Failed(val message: String) : ToolReviewOutcome()
}

/**
 * 修改意图：工具模式为"允许"时的文件工具调用合规初筛。
 * 复用功能模型调用范式（FunctionType.TOOL_REVIEW → callFunctionModel），
 * 未配置时 FunctionalConfigManager 自动回落主模型配置；审查失败不静默放行，
 * 统一转为弹窗人工确认（需求确认项）。
 */
object ToolReviewService {
    private const val TAG = "ToolReviewService"

    /** 审查模型调用超时；超时语义与调用失败一致（弹窗人工确认） */
    private const val REVIEW_TIMEOUT_MS = 15_000L

    /** 内容类参数送入审查 prompt 的摘要上限 */
    private const val CONTENT_SUMMARY_LIMIT = 500

    suspend fun review(
        context: android.content.Context,
        tool: AITool,
        zone: WorkspacePathChecker.Zone,
        chatWorkspaceRoot: String?,
    ): ToolReviewOutcome {
        val zoneLabel =
            when (zone) {
                WorkspacePathChecker.Zone.INSIDE -> "inside the workspace"
                WorkspacePathChecker.Zone.OUTSIDE ->
                    "OUTSIDE the workspace (or no workspace bound)"
            }

        // 内容类参数只送摘要，避免大内容挤爆审查上下文
        val contentSummary =
            (tool.parameters.find { it.name == "content" }?.value
                ?: tool.parameters.find { it.name == "new" }?.value
                ?: tool.parameters.find { it.name == "base64Content" }?.value
                ?: "").take(CONTENT_SUMMARY_LIMIT)

        val operationSummary =
            when (tool.name.substringAfter(':')) {
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
                                            zone = zoneLabel,
                                            workspaceRoot = chatWorkspaceRoot ?: "",
                                            contentSummary = contentSummary,
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

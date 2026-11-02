package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.core.tools.FileApplyResultData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.PackageActivationErrorData
import com.ai.assistance.operit.core.tools.protocol.ToolResult

/** 在结果生成阶段分开错误说明和独立输出，最终正文只组装一次。 */
internal data class ToolFailurePayload(val message: String, val output: String) {
    fun render(): String = listOf(message, output).filter { it.isNotEmpty() }.joinToString("\n\n")
}

/** 来源取自结构化数据，不从已生成的正文中剥离或匹配来源标签。 */
internal fun toolFailurePayload(result: ToolResult): ToolFailurePayload {
    when (val data = result.result) {
        is FileOperationData -> if (!data.successful) {
            return ToolFailurePayload(data.toString().trim(), "")
        }
        is FileApplyResultData -> if (!data.operation.successful) {
            val output = listOfNotNull(
                data.diffContent,
                data.aiDiffInstructions.takeIf { it.isNotEmpty() && !it.startsWith("Error") },
            ).filter { it.isNotBlank() }.joinToString("\n").trim()
            return ToolFailurePayload(data.operation.toString().trim(), output)
        }
        is PackageActivationErrorData -> return ToolFailurePayload(data.message.trim(), "")
        else -> Unit
    }
    val message = result.error.orEmpty().trim()
    val output = result.result.toString().trim()
    // 原有纯文本结果全文相同时不重复拼接；不同输出原样保留，不逐行清理。
    return ToolFailurePayload(message, if (output == message) "" else output)
}

package com.ai.assistance.operit.core.tools.packTool.runtime

import com.ai.assistance.operit.core.tools.PackageActivationErrorCode
import com.ai.assistance.operit.core.tools.PackageActivationErrorData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult

/** 激活结果在产生错误的分支确定状态，不根据描述文字猜测成功与否。 */
internal sealed class PackageActivationResult {
    abstract val message: String

    data class Activated(override val message: String) : PackageActivationResult()
    data class Failed(
        val code: PackageActivationErrorCode,
        val packageName: String,
        override val message: String,
    ) : PackageActivationResult()

    fun toToolResult(toolName: String): ToolResult = when (this) {
        is Activated -> ToolResult(toolName, true, StringResultData(message))
        is Failed -> ToolResult(
            toolName = toolName,
            success = false,
            result = PackageActivationErrorData(code, packageName, message),
            error = message,
        )
    }
}

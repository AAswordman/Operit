package com.ai.assistance.operit.data.model

enum class InputProcessingErrorSource(val wireName: String) {
    AI("ai"),
    TOOL("tool"),
    API("api"),
    SYSTEM("system")
}

/** UI状态，用于显示AI服务在做什么 */
sealed class InputProcessingState {
    /** 空闲状态 */
    object Idle : InputProcessingState()

    /** 正在处理，例如准备请求或解析响应 */
    data class Processing(val message: String) : InputProcessingState()

    /** 正在连接到AI服务 */
    data class Connecting(val message: String) : InputProcessingState()

    /** 正在从AI服务接收数据 */
    data class Receiving(val message: String) : InputProcessingState()

    /** 新增：正在执行工具 */
    data class ExecutingTool(val toolName: String) : InputProcessingState()

    data class ToolProgress(
        val toolName: String,
        val progress: Float,
        val message: String = ""
    ) : InputProcessingState()

    /** 新增：正在处理工具结果 */
    data class ProcessingToolResult(val toolName: String) : InputProcessingState()

    /** 新增：正在总结记忆 */
    data class Summarizing(val message: String) : InputProcessingState()

    /** 新增：正在执行计划 */
    data class ExecutingPlan(val message: String) : InputProcessingState()

    /** 处理完成 */
    object Completed : InputProcessingState()

    /** 工具执行器已经开始执行，等待工具返回结果 */
    data class WaitingToolResult(val toolName: String) : InputProcessingState()

    data class Retrying(
        val message: String = "",
        val retryAttempt: Int? = null,
        val maxRetryAttempts: Int? = null,
        val retryAfterMs: Long? = null,
        val errorCode: String? = null,
        val errorSource: InputProcessingErrorSource = InputProcessingErrorSource.API,
        val providerCode: String? = null,
        val httpStatusCode: Int? = null,
        val recoverable: Boolean = true
    ) : InputProcessingState()

    /** AI 输出异常，通常会进入重试流程 */
    data class AiError(
        val code: String,
        val message: String = "",
        val recoverable: Boolean = true,
        val retryAttempt: Int? = null
    ) : InputProcessingState()

    /** 工具参数或执行失败 */
    data class ToolError(
        val toolName: String,
        val code: String,
        val message: String = "",
        val recoverable: Boolean = true,
        val retryAttempt: Int? = null
    ) : InputProcessingState()

    /** 发生了未被更具体错误类型覆盖的错误 */
    data class Error(
        val message: String,
        val code: String = "unknown",
        val errorSource: InputProcessingErrorSource = InputProcessingErrorSource.SYSTEM,
        val recoverable: Boolean = false,
        val retryAttempt: Int? = null,
        val maxRetryAttempts: Int? = null,
        val appCode: Int? = null,
        val providerCode: String? = null,
        val httpStatusCode: Int? = null,
        val retryAfterMs: Long? = null
    ) : InputProcessingState()
}

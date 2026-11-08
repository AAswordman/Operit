package com.ai.assistance.operit.core.workflow.runtime

import com.ai.assistance.operit.core.workflow.model.WorkflowExecutionIssue

/** 将工具和运行环境的错误归为稳定的语义；用户界面通过资源本地化，不展示异常类和堆栈。 */
internal fun classifyWorkflowIssue(reason: String): WorkflowExecutionIssue {
    val text = reason.lowercase()
    fun has(vararg words: String) = words.any(text::contains)
    return when {
        has("timeout", "timed out", "超时") -> WorkflowExecutionIssue.TIMEOUT
        has("cancel", "取消") -> WorkflowExecutionIssue.CANCELLED
        has("circular", "cycle", "循环依赖", "环形") -> WorkflowExecutionIssue.CYCLE
        has("no trigger", "trigger node", "触发节点不存在", "没有触发", "未找到触发") -> WorkflowExecutionIssue.NO_TRIGGER
        has("401", "403", "invalid api key", "unauthorized", "authentication", "无效密钥", "认证失败") -> WorkflowExecutionIssue.AUTHENTICATION
        has("429", "quota", "insufficient", "余额", "限流", "rate limit") -> WorkflowExecutionIssue.QUOTA
        has("permission", "权限", "access denied") -> WorkflowExecutionIssue.PERMISSION
        has("already running", "busy", "正在处理", "正在执行", "忙碌") -> WorkflowExecutionIssue.BUSY
        (text.contains("tool") && has("not found", "not exist", "unavailable")) -> WorkflowExecutionIssue.TOOL_MISSING
        has("unknown tool", "tool not found", "not registered", "未注册", "工具不存在", "未找到工具") -> WorkflowExecutionIssue.TOOL_MISSING
        has("referenc", "引用节点", "引用的节点") -> WorkflowExecutionIssue.REFERENCE
        has("missing required", "parameter", "argument", "invalid input", "参数", "未配置", "no action", "actiontype", "没有配置执行") -> WorkflowExecutionIssue.INVALID_INPUT
        has("network", "connect", "socket", "dns", "网络", "连接失败", "无法连接") -> WorkflowExecutionIssue.NETWORK
        has("initializ", "runtime", "初始化", "运行环境") -> WorkflowExecutionIssue.RUNTIME
        else -> WorkflowExecutionIssue.UNKNOWN
    }
}

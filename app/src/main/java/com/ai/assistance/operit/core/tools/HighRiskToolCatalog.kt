package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.ToolParameterSchema

/**
 * 高危工具调用大模型审查的纯函数判定器（三开关之「高危工具」开关的适用范围）。
 *
 * 与 [WorkspacePathChecker] 的文件工具族并列：文件工具按工作区路径判区审查，
 * 本清单内的工具没有工作区路径概念，由独立的「自动审查高危工具」开关管辖。
 *
 * 入选标准（需求确认的核心破坏集）：任意命令/代码执行、跨应用动作、
 * 应用与系统管理、网络外发、可代理执行任意工具的入口、工作流编排。
 */
object HighRiskToolCatalog {

    /**
     * 调用说明参数名（审查声明）：可审查工具调用时可附带一句"为什么发起本次调用"，
     * 供审查模型与调用点上下文做一致性比对。参数可选，模型未提供不构成违规；
     * 该参数仅供审查流读取，执行前由 ToolExecutionManager 从工具参数剥离。
     */
    const val CALL_PURPOSE_PARAM = "call_purpose"

    /** 高危工具族（审查适用范围，共 18 个） */
    val HIGH_RISK_TOOLS: Set<String> = setOf(
        // 命令/代码执行
        "execute_shell",
        "execute_in_terminal_session",
        "execute_in_terminal_session_streaming",
        "execute_hidden_terminal_command",
        "input_in_terminal_session",
        "execute_sandbox_script_direct",
        // Intent/广播
        "execute_intent",
        "send_broadcast",
        // 应用与系统管理
        "install_app",
        "uninstall_app",
        "stop_app",
        "modify_system_setting",
        // 网络外发
        "http_request",
        "multipart_request",
        // 代理（可执行任意工具的入口）
        "use_package",
        "package_proxy",
        "proxy",
        // 编排
        "trigger_workflow",
    )

    fun isHighRiskTool(toolName: String): Boolean {
        if (toolName in HIGH_RISK_TOOLS) return true
        // 修改意图：扩展包工具（如 somepkg:http_request）与内置高危工具同族，
        // 按限定名后缀纳入同一套审查，与 isWorkspaceProtectedTool 的匹配模式一致
        val localName = toolName.substringAfter(':', "")
        return localName.isNotEmpty() && localName in HIGH_RISK_TOOLS
    }

    /** 可审查工具本地名全集（高危工具 + 文件工具族）：审查判定与调用说明参数文档注入的统一口径 */
    val REVIEWABLE_TOOL_LOCAL_NAMES: Set<String> =
        HIGH_RISK_TOOLS + WorkspacePathChecker.WORKSPACE_PROTECTED_FILE_TOOLS

    /** 是否为可审查工具（含限定名后缀匹配，与审查门控同一匹配模式） */
    fun isReviewableTool(toolName: String): Boolean =
        REVIEWABLE_TOOL_LOCAL_NAMES.contains(toolName.substringAfter(':'))

    /**
     * 调用说明参数的文档 schema（required=false，模型可省略）。
     * 文本提示词与 function-calling schema 两条注入路径共用，保证口径一致。
     */
    fun callPurposeParamSchema(): ToolParameterSchema =
        ToolParameterSchema(
            name = CALL_PURPOSE_PARAM,
            type = "string",
            description =
                "optional, one short sentence declaring WHY this call is needed (purpose and " +
                    "expected effect), used for AI compliance review; " +
                    "可选：一句话说明本次调用的目的与预期效果，用于 AI 合规审查",
            required = false
        )

    /** 高危工具的英文操作摘要，供审查 prompt 描述本次调用；非高危工具返回 null */
    fun operationSummary(toolName: String): String? {
        val localName = toolName.substringAfter(':', "")
        return when (localName) {
            "execute_shell" -> "execute an arbitrary shell command"
            "execute_in_terminal_session", "execute_in_terminal_session_streaming" ->
                "execute a command in a terminal session"
            "execute_hidden_terminal_command" ->
                "execute a hidden terminal command without visible UI"
            "input_in_terminal_session" ->
                "send input into a running terminal session (may drive an interactive shell/REPL)"
            "execute_sandbox_script_direct" -> "execute a sandbox script (arbitrary code)"
            "execute_intent" -> "fire an Android Intent to start an activity/service across apps"
            "send_broadcast" -> "send a system or app broadcast"
            "install_app" -> "install an application"
            "uninstall_app" -> "uninstall an application"
            "stop_app" -> "force-stop an application"
            "modify_system_setting" -> "modify system settings"
            "http_request" -> "perform an arbitrary HTTP request (potential data exfiltration)"
            "multipart_request" -> "perform a multipart HTTP upload (potential data exfiltration)"
            "use_package" -> "invoke an arbitrary package tool (proxy execution)"
            "package_proxy", "proxy" -> "proxy-execute another tool by name"
            "trigger_workflow" -> "trigger a saved workflow that runs a chain of tools"
            else -> null
        }
    }
}

package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.AIToolHook
import com.ai.assistance.operit.core.tools.AIToolHookDecision
import com.ai.assistance.operit.core.tools.WorkspacePathChecker
import com.ai.assistance.operit.data.model.AITool
import java.io.File

/**
 * 修改意图：JS 扩展包经 toolCall → AIToolHandler.executeTool 的嵌套调用绕过了
 * ToolExecutionManager 的权限弹窗链路，工作区防护需要一个兜底拦截点。
 *
 * 判定依赖 ToolExecutionManager 的 ToolRuntimeContext ThreadLocal——它只在
 * executeTool 的执行作用域内可见，因此：
 * - 主链路（模型发起）的 checkToolInterception 发生在 ThreadLocal 设置之前，
 *   本 Hook 直接放行，由 checkToolPermission 的工作区阶段负责弹窗/拒绝
 * - 嵌套调用（扩展包 toolCall）发生在 ThreadLocal 作用域内，本 Hook 生效
 *
 * Hook 无 UI 能力：区外调用一律拒绝并回传理由；本轮已由用户确认过的区外路径
 * （approvedOutsidePaths）不重复拦截。
 */
internal class WorkspaceFileToolHook : AIToolHook {

    override fun onToolCallIntercept(tool: AITool): AIToolHookDecision {
        if (!WorkspacePathChecker.isWorkspaceProtectedTool(tool.name)) {
            return AIToolHookDecision.Allow
        }

        val runtimeContext = ToolExecutionManager.currentToolRuntimeContext() ?: return AIToolHookDecision.Allow

        val paths = WorkspacePathChecker.extractPaths(tool)
        if (paths.isEmpty()) {
            return AIToolHookDecision.Allow
        }

        // 本轮已由用户确认过的区外路径直接放行
        val allConfirmed =
            paths.all { path ->
                val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
                canonical in runtimeContext.approvedOutsidePaths
            }
        if (allConfirmed) {
            return AIToolHookDecision.Allow
        }

        val zone =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = runtimeContext.chatWorkspaceRoot,
                chatWorkspaceEnv = runtimeContext.chatWorkspaceEnv,
                tool = tool,
            )
        if (zone == WorkspacePathChecker.Zone.INSIDE) {
            return AIToolHookDecision.Allow
        }

        return AIToolHookDecision.Block(
            reason =
                "File tool '${tool.name}' targets paths outside the chat workspace " +
                    "(or the chat has no workspace bound): ${paths.joinToString(", ")}. " +
                    "Nested/package calls cannot be confirmed by the user and are denied."
        )
    }
}

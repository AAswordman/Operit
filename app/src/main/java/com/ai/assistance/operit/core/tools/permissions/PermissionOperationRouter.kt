package com.ai.assistance.operit.core.tools.permissions

/** 只读操作允许重试；有副作用的操作只在提交前选择后端。 */
object PermissionOperationRouter {
    suspend fun <T> execute(
        capabilities: PermissionCapabilities,
        readOnly: Boolean,
        privileged: suspend () -> T,
        standard: suspend () -> T,
        isSuccess: (T) -> Boolean
    ): T {
        if (!capabilities.hasPrivilegedShell) return standard()
        val result = privileged()
        if (readOnly && !isSuccess(result)) {
            val standardResult = standard()
            if (isSuccess(standardResult)) return standardResult
        }
        return result
    }
}

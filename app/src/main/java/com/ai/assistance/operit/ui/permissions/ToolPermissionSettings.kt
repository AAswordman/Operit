package com.ai.assistance.operit.ui.permissions

/** 页面与执行器共用持久权限规则，保留未注册工具的已有单项配置。 */
data class ToolPermissionSettings(
    val master: PermissionLevel,
    val overrides: Map<String, PermissionLevel>,
)

/** 只保存上一次页面操作实际改变的字段，不覆盖其他工具。 */
data class ToolPermissionChange(
    val master: PermissionLevel?,
    val overrides: Map<String, PermissionLevel?>,
) {
    val isEmpty: Boolean
        get() = master == null && overrides.isEmpty()
}

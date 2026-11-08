package com.ai.assistance.operit.ui.features.conversation.workspace.hub

/** 工作区入口和子页面共用一份导航状态，避免终端与工作区同时显示。 */
enum class WorkspaceHubPage {
    CHAT,
    HOME,
    WORKSPACE,
    TERMINAL,
}

/** 主页入口指定要打开的现有工作区功能。 */
enum class WorkspaceEntryAction {
    DEFAULT,
    CREATE,
    SELECT,
    FILES,
}

data class WorkspaceEntryRequest(
    val action: WorkspaceEntryAction = WorkspaceEntryAction.DEFAULT,
    val sequence: Long = 0,
)

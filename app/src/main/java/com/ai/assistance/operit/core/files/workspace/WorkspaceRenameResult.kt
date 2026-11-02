package com.ai.assistance.operit.core.files.workspace

data class WorkspaceRenameResult(
    val workspacePath: String,
    val workspaceEnv: String?,
    val workspaceName: String
)

package com.ai.assistance.operit.ui.features.conversation.workspace.process

import android.content.Context
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WorkspaceRuleFileReader {
    private val WORKSPACE_RULE_FILE_NAMES = listOf("AGENT.md", "AGENTS.md")

    data class WorkspaceRuleFile(val name: String, val content: String)

    suspend fun readWorkspaceRootRuleFile(
        context: Context,
        workspacePath: String?,
        workspaceEnv: String? = null
    ): WorkspaceRuleFile? = withContext(Dispatchers.IO) {
        if (workspacePath.isNullOrBlank()) {
            return@withContext null
        }

        val toolHandler = AIToolHandler.getInstance(context)
        for (fileName in WORKSPACE_RULE_FILE_NAMES) {
            val result =
                toolHandler.executeTool(
                    AITool(
                        name = "read_file_full",
                        parameters = buildList {
                            add(ToolParameter("path", buildWorkspaceChildPath(workspacePath, fileName)))
                            add(ToolParameter("text_only", "true"))
                            if (!workspaceEnv.isNullOrBlank()) {
                                add(ToolParameter("environment", workspaceEnv))
                            }
                        }
                    )
                )
            val content = (result.result as? FileContentData)?.content?.trim().orEmpty()
            if (result.success && content.isNotBlank()) {
                return@withContext WorkspaceRuleFile(name = fileName, content = content)
            }
        }

        null
    }

    private fun buildWorkspaceChildPath(workspacePath: String, childName: String): String {
        val normalizedRoot = workspacePath.trim().ifBlank { "/" }
        return if (normalizedRoot == "/") {
            "/$childName"
        } else {
            normalizedRoot.trimEnd('/') + "/$childName"
        }
    }
}

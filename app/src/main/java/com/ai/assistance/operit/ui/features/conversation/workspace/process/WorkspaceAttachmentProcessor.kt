package com.ai.assistance.operit.ui.features.conversation.workspace.process

import android.content.Context
import com.ai.assistance.operit.R

object WorkspaceAttachmentProcessor {
    fun generateWorkspaceAttachment(
        context: Context,
        workspaceEnv: String?,
        changes: WorkspaceChangeSnapshot = WorkspaceChangeSnapshot(emptyList(), 0)
    ): String {
        val workspaceTag = workspaceEnv?.trim().orEmpty()
        return buildString {
            appendLine(context.getString(R.string.workspace_attachment_attached))
            if (workspaceTag.isNotEmpty()) {
                appendLine(context.getString(R.string.workspace_attachment_environment, escapeText(workspaceTag)))
            }
            changes.initialRootStructure?.takeIf { it.isNotBlank() }?.let { rootStructure ->
                appendLine(context.getString(R.string.workspace_attachment_initial_load))
                appendLine(escapeText(rootStructure))
            }
            if (changes.changes.isNotEmpty() || changes.omittedCount > 0) {
                appendLine(context.getString(R.string.workspace_attachment_file_changes))
                changes.changes.forEach { change ->
                    appendLine("- ${change.kind.displayName(context)} ${escapeText(change.relativePath)}")
                }
                if (changes.omittedCount > 0) {
                    appendLine(
                        context.getString(
                            R.string.workspace_attachment_omitted_changes,
                            changes.omittedCount
                        )
                    )
                }
            }
        }.trim()
    }

    private fun WorkspaceFileChangeKind.displayName(context: Context): String {
        return when (this) {
            WorkspaceFileChangeKind.CREATED -> context.getString(R.string.workspace_attachment_change_created)
            WorkspaceFileChangeKind.MODIFIED -> context.getString(R.string.workspace_attachment_change_modified)
            WorkspaceFileChangeKind.DELETED -> context.getString(R.string.workspace_attachment_change_deleted)
            WorkspaceFileChangeKind.MOVED -> context.getString(R.string.workspace_attachment_change_moved)
        }
    }

    private fun escapeText(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.OperitArchivedFolder

internal fun validateArchivedFolderGraph(folders: List<OperitArchivedFolder>) {
    val byId = folders.associateBy { it.id }
    folders.forEach { folder ->
        val visited = hashSetOf<String>()
        var currentId: String? = folder.id
        while (currentId != null) {
            require(visited.add(currentId)) { "Archive v4 folder graph contains a cycle" }
            // Reject before following an untrusted, potentially very long parent chain.
            require(visited.size <= 3) { "Archive v4 folder depth exceeds 3" }
            currentId = byId.getValue(currentId).parentFolderId
        }
    }
}

internal val ARCHIVE_FOLDER_SELECTION_SQL = """
    CREATE TABLE chat_folders AS
    SELECT id, name, parentFolderId, displayOrder, createdAt
    FROM operit_export_source.chat_folders
    WHERE NOT EXISTS (
        SELECT 1 FROM operit_export_source.chats
        WHERE id NOT IN (SELECT id FROM export_chat_ids)
    ) OR id IN (
        WITH RECURSIVE ancestors(id) AS (
            SELECT folderId FROM chats WHERE folderId IS NOT NULL
            UNION
            SELECT folder.parentFolderId
            FROM operit_export_source.chat_folders AS folder
            JOIN ancestors ON folder.id = ancestors.id
            WHERE folder.parentFolderId IS NOT NULL
        )
        SELECT id FROM ancestors
    )
""".trimIndent()

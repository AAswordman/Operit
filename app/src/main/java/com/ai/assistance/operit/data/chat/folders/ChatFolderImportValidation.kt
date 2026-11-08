package com.ai.assistance.operit.data.chat.folders

enum class ChatFolderImportErrorKind { EMPTY_ID, DUPLICATE_ID, EMPTY_NAME, MISSING_PARENT, CYCLE }

data class ChatFolderImportIssue(
    val recordNumber: Int,
    val folderId: String,
    val kind: ChatFolderImportErrorKind,
    val relatedId: String? = null,
)

fun validateImportedFolders(imported: List<ChatFolder>, existing: List<ChatFolder>): ChatFolderImportIssue? {
    val ids = mutableSetOf<String>()
    imported.forEachIndexed { index, folder ->
        val kind = when {
            folder.id.isBlank() -> ChatFolderImportErrorKind.EMPTY_ID
            !ids.add(folder.id) -> ChatFolderImportErrorKind.DUPLICATE_ID
            folder.name.isBlank() -> ChatFolderImportErrorKind.EMPTY_NAME
            else -> null
        }
        if (kind != null) return ChatFolderImportIssue(index + 1, folder.id, kind)
    }
    val all = (existing + imported).associateBy { it.id }
    val checked = mutableSetOf<String>()
    imported.forEachIndexed { index, folder ->
        val path = mutableSetOf<String>()
        var current: String? = folder.id
        while (current != null && current !in checked) {
            if (!path.add(current)) {
                return ChatFolderImportIssue(index + 1, folder.id, ChatFolderImportErrorKind.CYCLE, current)
            }
            val ancestor = all[current]
                ?: return ChatFolderImportIssue(index + 1, folder.id, ChatFolderImportErrorKind.MISSING_PARENT, current)
            current = ancestor.parentId
        }
        checked.addAll(path)
    }
    return null
}

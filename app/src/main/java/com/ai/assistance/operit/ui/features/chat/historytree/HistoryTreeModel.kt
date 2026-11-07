package com.ai.assistance.operit.ui.features.chat.historytree

import com.ai.assistance.operit.data.model.ChatFolderEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.SYSTEM_UNGROUPED_FOLDER_ID

sealed interface HistoryTreeNode {
    val stableKey: String
    val depth: Int

    data class Folder(
        val folder: ChatFolderEntity,
        override val depth: Int,
        val path: List<ChatFolderEntity>,
    ) : HistoryTreeNode {
        override val stableKey: String = "folder:${folder.id}"
    }

    data class Chat(
        val history: ChatHistory,
        override val depth: Int,
    ) : HistoryTreeNode {
        override val stableKey: String = "chat:${history.id}"
    }

    data object Ungrouped : HistoryTreeNode {
        override val stableKey: String = UNGROUPED_FOLDER_STABLE_KEY
        override val depth: Int = 1
    }
}

const val UNGROUPED_FOLDER_STABLE_KEY = "virtual-folder:ungrouped"

enum class HistoryTreeProjection {
    ALL,
    FAVORITES,
    FILTERED,
}

data class HistoryTreeBuildResult(
    val nodes: List<HistoryTreeNode>,
    val structurallyInvalidFolderIds: Set<String>,
)

/**
 * Builds one mixed folder/chat visible list without trusting the persisted graph.
 *
 * Missing parents and nodes left behind by a cycle are promoted to the virtual root so chats and
 * folders never disappear. Callers may log the returned IDs, but must not include folder names or
 * chat content in structural diagnostics.
 */
fun buildVisibleHistoryTree(
    folders: List<ChatFolderEntity>,
    histories: List<ChatHistory>,
    projection: HistoryTreeProjection,
    includeChat: (ChatHistory) -> Boolean = { true },
    collapsedFolderIds: Set<String> = emptySet(),
    includeUngroupedFolder: Boolean = false,
    isUngroupedFolderCollapsed: Boolean = false,
    includedFolderIds: Set<String> = emptySet(),
): HistoryTreeBuildResult {
    val folderById = folders.associateBy { it.id }
    val folderComparator =
        compareBy<ChatFolderEntity> { it.displayOrder }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    val chatComparator =
        compareByDescending<ChatHistory> { it.pinned }
            .thenBy { it.displayOrder }
            .thenBy { it.createdAt }
            .thenBy { it.id }

    val invalidIds = linkedSetOf<String>()
    val normalizedParentById =
        folders.associate { folder ->
            val parentId =
                folder.parentFolderId?.takeIf { parent ->
                    if (
                        folder.id == SYSTEM_UNGROUPED_FOLDER_ID ||
                            parent == folder.id ||
                            parent !in folderById
                    ) {
                        invalidIds += folder.id
                        false
                    } else {
                        true
                    }
                }
            folder.id to parentId
        }
    val childrenByParent =
        folders.groupBy { normalizedParentById[it.id] }.mapValues { (_, children) ->
            children.sortedWith(folderComparator)
        }
    val visibleChats =
        histories
            .asSequence()
            .filter(includeChat)
            .filter { projection != HistoryTreeProjection.FAVORITES || it.isFavorite }
            .toList()
    val chatsByFolder =
        visibleChats.groupBy { history ->
            history.folderId
                ?.takeUnless { it == SYSTEM_UNGROUPED_FOLDER_ID }
                ?.takeIf { it in folderById }
        }.mapValues { (_, chats) -> chats.sortedWith(chatComparator) }

    val subtreeVisibilityMemo = mutableMapOf<String, Boolean>()
    fun hasVisibleContent(folderId: String, visiting: MutableSet<String>): Boolean {
        subtreeVisibilityMemo[folderId]?.let { return it }
        if (!visiting.add(folderId)) {
            invalidIds += folderId
            return chatsByFolder[folderId].orEmpty().isNotEmpty()
        }
        val visible =
            folderId in includedFolderIds ||
                chatsByFolder[folderId].orEmpty().isNotEmpty() ||
                childrenByParent[folderId].orEmpty().any { child ->
                    hasVisibleContent(child.id, visiting)
                }
        visiting.remove(folderId)
        subtreeVisibilityMemo[folderId] = visible
        return visible
    }

    val result = mutableListOf<HistoryTreeNode>()
    val visited = linkedSetOf<String>()
    fun markHiddenDescendantsVisited(folderId: String) {
        childrenByParent[folderId].orEmpty().forEach { child ->
            if (visited.add(child.id)) {
                markHiddenDescendantsVisited(child.id)
            }
        }
    }
    fun sortedSiblings(
        parentId: String?,
    ): List<SortableHistorySibling> =
        (
            childrenByParent[parentId].orEmpty().map { SortableHistorySibling(folder = it) } +
                chatsByFolder[parentId].orEmpty().map { SortableHistorySibling(history = it) }
        ).sortedWith(
            compareByDescending<SortableHistorySibling> { it.history?.pinned == true }
                .thenBy { it.displayOrder }
                .thenBy { it.kindOrder }
                .thenBy { it.id }
        )

    fun appendFolder(folder: ChatFolderEntity, depth: Int, path: List<ChatFolderEntity>) {
        if (!visited.add(folder.id)) {
            invalidIds += folder.id
            return
        }
        if (
            projection != HistoryTreeProjection.ALL &&
                !hasVisibleContent(folder.id, mutableSetOf())
        ) {
            return
        }
        val currentPath = path + folder
        result += HistoryTreeNode.Folder(folder, depth, currentPath)
        val isCollapsed = collapsedFolderIds.contains(folder.id)
        if (isCollapsed) {
            markHiddenDescendantsVisited(folder.id)
            return
        }
        sortedSiblings(folder.id).forEach { sibling ->
            sibling.folder?.let { child ->
                appendFolder(child, depth + 1, currentPath)
            } ?: run {
                result += HistoryTreeNode.Chat(requireNotNull(sibling.history), depth + 1)
            }
        }
    }

    val systemUngroupedFolder = folderById[SYSTEM_UNGROUPED_FOLDER_ID]
    val rootFolders =
        childrenByParent[null].orEmpty().filterNot { it.id == SYSTEM_UNGROUPED_FOLDER_ID }
    val rootContainers =
        (
            rootFolders.map { RootHistoryContainer(folder = it) } +
                if (includeUngroupedFolder) {
                    listOf(
                        RootHistoryContainer(
                            isUngrouped = true,
                            displayOrder = systemUngroupedFolder?.displayOrder ?: Long.MIN_VALUE,
                        )
                    )
                } else {
                    emptyList()
                }
        ).sortedWith(
            compareBy<RootHistoryContainer> { it.displayOrder }
                .thenBy { if (it.isUngrouped) 0 else 1 }
                .thenBy { it.folder?.id.orEmpty() }
        )
    systemUngroupedFolder?.let { visited += it.id }
    rootContainers.forEach { container ->
        if (container.isUngrouped) {
            result += HistoryTreeNode.Ungrouped
            if (!isUngroupedFolderCollapsed) {
                chatsByFolder[null].orEmpty().forEach { history ->
                    result += HistoryTreeNode.Chat(history, 2)
                }
            }
        } else {
            appendFolder(requireNotNull(container.folder), 1, emptyList())
        }
    }
    if (!includeUngroupedFolder) {
        chatsByFolder[null].orEmpty().forEach { history ->
            result += HistoryTreeNode.Chat(history, 1)
        }
    }
    folders.sortedWith(folderComparator).filterNot { it.id in visited }.forEach { orphanOrCycle ->
        invalidIds += orphanOrCycle.id
        appendFolder(orphanOrCycle, 1, emptyList())
    }

    return HistoryTreeBuildResult(
        nodes = result,
        structurallyInvalidFolderIds = invalidIds,
    )
}

private data class SortableHistorySibling(
    val folder: ChatFolderEntity? = null,
    val history: ChatHistory? = null,
) {
    val displayOrder: Long
        get() = folder?.displayOrder ?: requireNotNull(history).displayOrder
    val kindOrder: Int
        get() = if (folder != null) 0 else 1
    val id: String
        get() = folder?.id ?: requireNotNull(history).id
}

private data class RootHistoryContainer(
    val folder: ChatFolderEntity? = null,
    val isUngrouped: Boolean = false,
    val displayOrder: Long = folder?.displayOrder ?: 0L,
)

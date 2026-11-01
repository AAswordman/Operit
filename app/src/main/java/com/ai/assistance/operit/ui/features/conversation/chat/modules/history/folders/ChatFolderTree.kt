package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders

import com.ai.assistance.operit.data.chat.folders.ChatFolder
import com.ai.assistance.operit.data.chat.folders.ChatFolderBinding
import com.ai.assistance.operit.data.chat.folders.ChatFolderMove
import com.ai.assistance.operit.data.chat.folders.ChatFolderNode
import com.ai.assistance.operit.data.chat.folders.canMoveFolder
import com.ai.assistance.operit.data.chat.folders.folderAncestors
import com.ai.assistance.operit.data.model.ChatHistory

enum class ChatHistorySortMode { DEFAULT, RECENT }

sealed interface ChatFolderRow {
    val key: String
    val depth: Int
    val binding: ChatFolderBinding?
    val scope: String

    data class Binding(
        override val key: String, val name: String, override val binding: ChatFolderBinding,
        override val scope: String,
    ) : ChatFolderRow { override val depth = 0 }

    data class Folder(
        val folder: ChatFolder, override val depth: Int, override val scope: String,
        override val binding: ChatFolderBinding?, val expanded: Boolean,
    ) : ChatFolderRow { override val key = folderKey(scope, folder.id) }

    data class Chat(
        val history: ChatHistory, override val depth: Int, override val scope: String,
        override val binding: ChatFolderBinding?,
    ) : ChatFolderRow { override val key = "$scope::chat::${history.id}" }
}

fun folderKey(scope: String, id: String) = "$scope::folder::$id"
fun ChatFolderRow.node(): ChatFolderNode? = when (this) {
    is ChatFolderRow.Folder -> ChatFolderNode.Folder(folder.id)
    is ChatFolderRow.Chat -> ChatFolderNode.Chat(history.id)
    is ChatFolderRow.Binding -> null
}

private data class TreeEntry(
    val folder: ChatFolder? = null, val chat: ChatHistory? = null,
) {
    val id: String get() = if (folder != null) folder.id else requireNotNull(chat).id
    val order: Long get() = if (folder != null) folder.displayOrder else requireNotNull(chat).displayOrder
}

/** 文件夹树只做角色投影，不改变数据库中唯一的父子关系。 */
fun buildChatFolderTree(
    chats: List<ChatHistory>, folders: List<ChatFolder>, ownedFolderIds: Set<String>,
    scope: String, binding: ChatFolderBinding?, collapsed: Set<String>,
    query: String, matchedChatIds: Set<String>, sort: ChatHistorySortMode,
    baseDepth: Int = 0,
): List<ChatFolderRow> {
    val foldersById = folders.associateBy { it.id }
    val paths = chats.associate { it.id to folderAncestors(it.folderId, foldersById) }
    val scopedIds = (ownedFolderIds + paths.values.flatten().map { it.id }).toMutableSet()
    ownedFolderIds.forEach { id -> scopedIds.addAll(folderAncestors(id, foldersById).map { it.id }) }
    val trimmed = query.trim()
    val searching = trimmed.isNotEmpty()
    fun matchesName(folder: ChatFolder) = folder.name.contains(trimmed, ignoreCase = true)
    val visibleChats = if (searching) chats.filter {
        it.title.contains(trimmed, ignoreCase = true) || it.id in matchedChatIds || paths.getValue(it.id).any(::matchesName)
    } else chats
    val visibleIds = if (searching) {
        val ids = visibleChats.flatMap { paths.getValue(it.id).map(ChatFolder::id) }.toMutableSet()
        scopedIds.forEach { id ->
            val path = folderAncestors(id, foldersById)
            if (path.any(::matchesName)) ids.addAll(path.map(ChatFolder::id))
        }
        ids
    } else scopedIds
    val activity = mutableMapOf<String, Long>()
    visibleChats.forEach { chat ->
        paths.getValue(chat.id).forEach { folder ->
            activity[folder.id] = maxOf(activity[folder.id] ?: 0L, chat.lastUsedAt)
        }
    }
    val entriesByParent = mutableMapOf<String?, MutableList<TreeEntry>>()
    folders.filter { it.id in visibleIds }.forEach {
        entriesByParent.getOrPut(it.parentId) { mutableListOf() }.add(TreeEntry(folder = it))
    }
    visibleChats.forEach {
        entriesByParent.getOrPut(it.folderId) { mutableListOf() }.add(TreeEntry(chat = it))
    }
    val comparator = compareByDescending<TreeEntry> { it.chat?.pinned == true }
        .thenByDescending {
            if (sort == ChatHistorySortMode.RECENT) {
                if (it.folder != null) activity[it.folder.id] ?: 0L else requireNotNull(it.chat).lastUsedAt
            } else 0L
        }.thenBy { it.order }.thenBy { if (it.folder == null) 1 else 0 }.thenBy { it.id }
    val pending = ArrayDeque<Pair<TreeEntry, Int>>()
    fun enqueue(parentId: String?, depth: Int) {
        entriesByParent[parentId].orEmpty().sortedWith(comparator).asReversed().forEach {
            pending.addLast(it to depth)
        }
    }
    val result = mutableListOf<ChatFolderRow>()
    enqueue(null, baseDepth)
    while (pending.isNotEmpty()) {
        val (entry, depth) = pending.removeLast()
        val folder = entry.folder
        if (folder != null) {
            val expanded = searching || folderKey(scope, folder.id) !in collapsed
            result.add(ChatFolderRow.Folder(folder, depth, scope, binding, expanded))
            if (expanded) enqueue(folder.id, depth + 1)
        } else {
            result.add(ChatFolderRow.Chat(requireNotNull(entry.chat), depth, scope, binding))
        }
    }
    return result
}

enum class FolderDropZone { BEFORE, INSIDE, AFTER }

/** 同级插入与移入目录分开计算；整棵子树只需修改根节点。 */
fun resolveFolderDrop(
    source: ChatFolderRow, target: ChatFolderRow?, zone: FolderDropZone,
    folders: Map<String, ChatFolder>, allowReorder: Boolean,
): ChatFolderMove? {
    val node = source.node() ?: return null
    if (source.key == target?.key) return null
    val parentId = when (target) {
        is ChatFolderRow.Folder -> if (zone == FolderDropZone.INSIDE) target.folder.id else target.folder.parentId
        is ChatFolderRow.Chat -> target.history.folderId
        else -> null
    }
    if (node is ChatFolderNode.Folder && !canMoveFolder(node.id, parentId, folders)) return null
    val anchor = if (zone == FolderDropZone.INSIDE) null else target?.node()
    if (!allowReorder && anchor != null) return null
    if (anchor == node) return null
    return ChatFolderMove(node, parentId, anchor, zone == FolderDropZone.AFTER, target?.binding)
}

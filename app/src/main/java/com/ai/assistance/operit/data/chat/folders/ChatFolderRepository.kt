package com.ai.assistance.operit.data.chat.folders

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import com.ai.assistance.operit.data.model.ChatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.ai.assistance.operit.data.db.AppDatabase

data class ChatFolderSnapshot(val folders: List<ChatFolder>, val chats: List<ChatEntity>)

class ChatFolderRepository(private val database: AppDatabase) {
    private val dao = database.chatFolderDao()
    private val chats = database.chatDao()
    val folders = dao.observeFolders()

    // 目录与聊天一起读取，避免迁移、移动时两个 Flow 的中间状态破坏树结构。
    val snapshots: Flow<ChatFolderSnapshot> = database.invalidationTracker.createFlow(
        "chat_folders", "chats", emitInitialState = true,
    ).map {
        database.withTransaction { ChatFolderSnapshot(dao.getFolders(), chats.getAllChatsDirectly()) }
    }.flowOn(Dispatchers.IO)

    suspend fun rebindFolders(source: ChatFolderBinding, target: ChatFolderBinding) {
        dao.rebind(source.characterCardName, source.characterGroupId, target.characterCardName, target.characterGroupId)
    }

    suspend fun getFolders(): List<ChatFolder> = dao.getFolders()

    suspend fun getFolder(id: String): ChatFolder =
        requireNotNull(dao.getFolder(id)) { "文件夹不存在：$id" }

    suspend fun create(name: String, parentId: String?, binding: ChatFolderBinding): ChatFolder =
        database.withTransaction {
            val normalized = name.trim()
            require(normalized.isNotEmpty()) { "文件夹名称不能为空" }
            if (parentId != null) getFolder(parentId)
            val folder = ChatFolder(
                name = normalized,
                parentId = parentId,
                characterCardName = binding.characterCardName,
                characterGroupId = binding.characterGroupId,
            )
            dao.insert(folder)
            folder
        }

    /** 旧工具接口仍以组名指定根文件夹，统一在此转换为 ID。 */
    suspend fun ensureLegacyRoot(name: String, binding: ChatFolderBinding): ChatFolder =
        database.withTransaction {
            val existing = dao.getFolders().firstOrNull { it.parentId == null && it.name == name }
            if (existing != null) existing else create(name, null, binding)
        }

    suspend fun rename(id: String, name: String) = database.withTransaction {
        getFolder(id)
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "文件夹名称不能为空" }
        dao.rename(id, normalized)
        chats.updateFolderName(id, normalized)
    }

    suspend fun assignChats(chatIds: List<String>, folderId: String?): Int = database.withTransaction {
        val name = if (folderId == null) null else getFolder(folderId).name
        chats.assignChatFolder(chatIds, folderId, name)
    }

    /** 批量移动在同一事务内完成；有对话被删除时整批回滚，避免只移动一部分。 */
    suspend fun moveSelectedChats(chatIds: List<String>, folderId: String?): Int = database.withTransaction {
        val ids = chatIds.distinct()
        require(ids.isNotEmpty()) { "未选择对话" }
        val name = folderId?.let { getFolder(it).name }
        // 每批控制绑定参数数目，完整事务仍覆盖全部所选对话。
        val moved = ids.chunked(900).sumOf { chats.assignChatFolder(it, folderId, name) }
        check(moved == ids.size) { "所选对话已发生变化，请重新选择" }
        moved
    }

    private data class Sibling(val node: ChatFolderNode, val order: Long)

    suspend fun move(request: ChatFolderMove) = database.withTransaction {
        val foldersById = dao.getFolders().associateBy { it.id }
        if (request.parentId != null) require(foldersById.containsKey(request.parentId))
        when (val node = request.node) {
            is ChatFolderNode.Folder -> {
                require(foldersById.containsKey(node.id))
                require(canMoveFolder(node.id, request.parentId, foldersById)) { "不能移入自身或子文件夹" }
            }
            is ChatFolderNode.Chat -> requireNotNull(chats.getChatById(node.id))
        }
        val siblings = buildList {
            foldersById.values.filter { it.parentId == request.parentId }.forEach {
                add(Sibling(ChatFolderNode.Folder(it.id), it.displayOrder))
            }
            chats.getAllChatsDirectly().filter { it.folderId == request.parentId }.forEach {
                add(Sibling(ChatFolderNode.Chat(it.id), it.displayOrder))
            }
        }.filter { it.node != request.node }.sortedWith(compareBy<Sibling> { it.order }.thenBy { if (it.node is ChatFolderNode.Folder) 0 else 1 }.thenBy { it.node.id })
            .map { it.node }.toMutableList()
        val index = if (request.anchor == null) siblings.size else {
            val anchorIndex = siblings.indexOf(request.anchor)
            require(anchorIndex >= 0) { "移动目标已发生变化" }
            anchorIndex + if (request.afterAnchor) 1 else 0
        }
        siblings.add(index, request.node)
        val folderName = request.parentId?.let { foldersById.getValue(it).name }
        siblings.forEachIndexed { order, node ->
            when (node) {
                is ChatFolderNode.Folder -> dao.move(node.id, request.parentId, order.toLong())
                is ChatFolderNode.Chat -> chats.moveChatFolder(node.id, request.parentId, folderName, order.toLong())
            }
        }
        if (request.node is ChatFolderNode.Chat && request.binding != null) {
            chats.updateChatCharacterBinding(
                request.node.id, request.binding.characterCardName, request.binding.characterGroupId,
            )
        }
    }

    /** 只删除文件夹，把直接内容插入原位置；子文件夹的后代保持原有关系。 */
    suspend fun deleteKeepingContents(id: String) = database.withTransaction {
        val folder = getFolder(id)
        val allFolders = dao.getFolders()
        val allChats = chats.getAllChatsDirectly()
        val promoted = promotedFolderContents(
            folder, allFolders, allChats.map { FolderChatPosition(it.id, it.folderId, it.displayOrder) },
        )
        val parentName = folder.parentId?.let { getFolder(it).name }
        promoted.forEachIndexed { order, node ->
            when (node) {
                is ChatFolderNode.Folder -> dao.move(node.id, folder.parentId, order.toLong())
                is ChatFolderNode.Chat -> chats.moveChatFolder(node.id, folder.parentId, parentName, order.toLong())
            }
        }
        dao.delete(id)
    }

    suspend fun importFolders(
        imported: List<ChatFolder>, describeIssue: (ChatFolderImportIssue) -> String,
    ) = database.withTransaction {
        validateImportedFolders(imported, dao.getFolders())?.let { issue ->
            throw IllegalArgumentException(describeIssue(issue))
        }
        dao.upsert(imported)
        imported.forEach { chats.updateFolderName(it.id, it.name) }
    }

    suspend fun exportFolders(chatIds: Set<String>): List<ChatFolder> {
        val allFolders = dao.getFolders()
        val allChats = chats.getAllChatsDirectly()
        if (allChats.all { it.id in chatIds }) return allFolders
        val foldersById = allFolders.associateBy { it.id }
        val ids = allChats.filter { it.id in chatIds }.flatMap {
            folderAncestors(it.folderId, foldersById).map(ChatFolder::id)
        }.toSet()
        return allFolders.filter { it.id in ids }
    }
}

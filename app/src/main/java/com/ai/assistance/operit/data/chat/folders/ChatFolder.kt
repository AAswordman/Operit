package com.ai.assistance.operit.data.chat.folders

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

/** 文件夹独立于对话存在，父节点使用稳定 ID，不限制层级。 */
@Serializable
@Entity(tableName = "chat_folders", indices = [Index(value = ["parentId"])])
data class ChatFolder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val displayOrder: Long = -System.currentTimeMillis(),
    val characterCardName: String? = null,
    val characterGroupId: String? = null,
)

data class ChatFolderBinding(val characterCardName: String?, val characterGroupId: String?)

sealed interface ChatFolderNode {
    val id: String
    data class Folder(override val id: String) : ChatFolderNode
    data class Chat(override val id: String) : ChatFolderNode
}

data class ChatFolderMove(
    val node: ChatFolderNode,
    val parentId: String?,
    val anchor: ChatFolderNode? = null,
    val afterAnchor: Boolean = false,
    val binding: ChatFolderBinding? = null,
)

/** 迭代遍历避免深层嵌套占用调用栈，也用于校验导入数据。 */
fun folderAncestors(id: String?, folders: Map<String, ChatFolder>): List<ChatFolder> {
    val result = mutableListOf<ChatFolder>()
    val visited = mutableSetOf<String>()
    var current = id
    while (current != null) {
        require(visited.add(current)) { "文件夹父子关系包含循环" }
        val folder = requireNotNull(folders[current]) { "文件夹不存在：$current" }
        result.add(folder)
        current = folder.parentId
    }
    return result.asReversed()
}

fun canMoveFolder(id: String, parentId: String?, folders: Map<String, ChatFolder>): Boolean =
    folderAncestors(parentId, folders).none { it.id == id }

data class FolderChatPosition(val id: String, val folderId: String?, val order: Long)

/** 删除目录时在原位置提升直接内容，包含锁定对话，不拆散子树。 */
fun promotedFolderContents(
    folder: ChatFolder, folders: List<ChatFolder>, chats: List<FolderChatPosition>,
): List<ChatFolderNode> {
    fun children(parentId: String?): List<ChatFolderNode> = buildList<Pair<ChatFolderNode, Long>> {
        folders.filter { it.parentId == parentId }.forEach {
            add(ChatFolderNode.Folder(it.id) to it.displayOrder)
        }
        chats.filter { it.folderId == parentId }.forEach {
            add(ChatFolderNode.Chat(it.id) to it.order)
        }
    }.sortedWith(compareBy<Pair<ChatFolderNode, Long>> { it.second }.thenBy { if (it.first is ChatFolderNode.Folder) 0 else 1 }.thenBy { it.first.id })
        .map { it.first }
    return children(folder.parentId).flatMap {
        if (it == ChatFolderNode.Folder(folder.id)) {
            children(folder.id)
        } else {
            listOf(it)
        }
    }
}

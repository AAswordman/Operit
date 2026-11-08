package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders

import com.ai.assistance.operit.data.chat.folders.ChatFolder
import com.ai.assistance.operit.data.chat.folders.ChatFolderBinding
import com.ai.assistance.operit.data.chat.folders.ChatFolderNode
import com.ai.assistance.operit.data.model.ChatHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderChatHistoryTreeTest {
    private fun chat(id: String, folder: String? = null, order: Long = 0L, used: Long = 0L, pinned: Boolean = false) =
        ChatHistory(id = id, title = id, messages = emptyList(), folderId = folder, displayOrder = order, lastUsedAt = used, pinned = pinned)

    private fun tree(
        chats: List<ChatHistory>, folders: List<ChatFolder>, query: String = "",
        collapsed: Set<String> = emptySet(), sort: ChatHistorySortMode = ChatHistorySortMode.DEFAULT,
        owned: Set<String> = folders.map { it.id }.toSet(), matches: Set<String> = emptySet(),
    ) = buildChatFolderTree(chats, folders, owned, "folders", null, collapsed, query, matches, sort)

    @Test
    fun emptyFoldersAndDeepPathsDoNotRequirePlaceholderChats() {
        val folders = (0 until 64).map { index ->
            ChatFolder(index.toString(), "目录$index", if (index == 0) null else (index - 1).toString())
        }
        val rows = tree(listOf(chat("leaf", "63")), folders)
        assertEquals(65, rows.size)
        assertEquals(64, rows.last().depth)
        assertEquals(64, tree(emptyList(), folders).size)
    }

    @Test
    fun searchShowsAncestorPathsWithoutChangingCollapsedState() {
        val folders = listOf(ChatFolder("root", "项目"), ChatFolder("child", "Operit", "root"))
        val collapsed = setOf(folderKey("folders", "root"))
        val chats = listOf(chat("UI重构", "child"))
        assertEquals(1, tree(chats, folders, collapsed = collapsed).size)
        assertEquals(3, tree(chats, folders, query = "UI", collapsed = collapsed).size)
        assertEquals(3, tree(chats, folders, query = "正文", collapsed = collapsed, matches = setOf("UI重构")).size)
        assertEquals(1, tree(chats, folders, collapsed = collapsed).size)
    }

    @Test
    fun recentSortPropagatesActivityAndKeepsPinnedChatsFirst() {
        val folders = listOf(ChatFolder("oldFolder", "旧", displayOrder = 0), ChatFolder("newFolder", "新", displayOrder = 1))
        val chats = listOf(chat("older", "oldFolder", used = 1), chat("newer", "newFolder", used = 50), chat("pin", used = 2, pinned = true))
        assertEquals("pin", tree(chats, folders, sort = ChatHistorySortMode.RECENT).first().node()?.id)
        val ordered = tree(chats, folders, sort = ChatHistorySortMode.RECENT).filterIsInstance<ChatFolderRow.Folder>()
        assertEquals(listOf("newFolder", "oldFolder"), ordered.map { it.folder.id })
        assertEquals(listOf("oldFolder", "newFolder"), tree(chats, folders).filterIsInstance<ChatFolderRow.Folder>().map { it.folder.id })
    }

    @Test
    fun roleProjectionKeepsAncestorsAndItsOwnEmptyFolders() {
        val folders = listOf(ChatFolder("root", "共享"), ChatFolder("child", "项目", "root"), ChatFolder("empty", "空"), ChatFolder("other", "其他"))
        val rows = tree(listOf(chat("Echo对话", "child")), folders, owned = setOf("empty"))
        assertEquals(setOf("root", "child", "empty"), rows.filterIsInstance<ChatFolderRow.Folder>().map { it.folder.id }.toSet())
    }

    @Test
    fun dropDistinguishesInsertionFromMovingInsideAndRejectsDescendants() {
        val root = ChatFolder("root", "根")
        val child = ChatFolder("child", "子", "root")
        val target = ChatFolder("target", "归档")
        val folders = listOf(root, child, target).associateBy { it.id }
        val source = ChatFolderRow.Folder(root, 0, "folders", null, true)
        val childRow = ChatFolderRow.Folder(child, 1, "folders", null, false)
        val targetRow = ChatFolderRow.Folder(target, 0, "folders", null, false)
        assertNull(resolveFolderDrop(source, childRow, FolderDropZone.INSIDE, folders, true))
        assertNull(resolveFolderDrop(source, childRow, FolderDropZone.BEFORE, folders, true))
        val inside = requireNotNull(resolveFolderDrop(source, targetRow, FolderDropZone.INSIDE, folders, true))
        assertEquals("target", inside.parentId)
        assertNull(inside.anchor)
        val insertion = requireNotNull(resolveFolderDrop(source, targetRow, FolderDropZone.BEFORE, folders, true))
        assertNull(insertion.parentId)
        assertEquals(ChatFolderNode.Folder("target"), insertion.anchor)
        assertEquals("root", child.parentId)
    }

    @Test
    fun draggingChatAcrossRoleHeadersCarriesTargetBinding() {
        val source = ChatFolderRow.Chat(chat("chat"), 0, "cardA", ChatFolderBinding("A", null))
        val target = ChatFolderRow.Binding("cardB", "B", ChatFolderBinding("B", null), "cardB")
        val drop = requireNotNull(resolveFolderDrop(source, target, FolderDropZone.INSIDE, emptyMap(), true))
        assertEquals(ChatFolderBinding("B", null), drop.binding)
        assertNull(drop.parentId)
    }
}

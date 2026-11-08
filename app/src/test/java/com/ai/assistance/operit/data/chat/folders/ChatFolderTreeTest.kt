package com.ai.assistance.operit.data.chat.folders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFolderTreeTest {
    @Test
    fun ancestorsKeepNamesAndRejectCycles() {
        val root = ChatFolder("root", "Echo")
        val child = ChatFolder("child", "项目", root.id)
        val leaf = ChatFolder("leaf", "Operit", child.id)
        val folders = listOf(root, child, leaf).associateBy { it.id }
        assertEquals(listOf("Echo", "项目", "Operit"), folderAncestors(leaf.id, folders).map { it.name })
        assertFalse(canMoveFolder(root.id, leaf.id, folders))
        assertTrue(canMoveFolder(child.id, null, folders))
        val cyclic = folders + (root.id to root.copy(parentId = leaf.id))
        var rejected = false
        try { folderAncestors(root.id, cyclic) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test
    fun deletePromotesDirectContentsAtOriginalPositionAndKeepsSubtree() {
        val root = ChatFolder("root", "Echo")
        val middle = ChatFolder("middle", "项目", root.id, displayOrder = 1)
        val child = ChatFolder("child", "Operit", middle.id, displayOrder = 2)
        val leaf = ChatFolder("leaf", "代码", child.id)
        val sibling = ChatFolder("sibling", "旁支", root.id, displayOrder = 3)
        val promoted = promotedFolderContents(
            middle, listOf(root, middle, child, leaf, sibling),
            listOf(
                FolderChatPosition("before", root.id, 0),
                FolderChatPosition("chat", middle.id, 1),
                FolderChatPosition("locked", middle.id, 4),
                FolderChatPosition("deep", leaf.id, 0),
            ),
        )
        assertEquals(
            listOf(
                ChatFolderNode.Chat("before"), ChatFolderNode.Chat("chat"),
                ChatFolderNode.Folder("child"), ChatFolderNode.Chat("locked"),
                ChatFolderNode.Folder("sibling"),
            ),
            promoted,
        )
        assertFalse(promoted.contains(ChatFolderNode.Chat("deep")))
    }
}

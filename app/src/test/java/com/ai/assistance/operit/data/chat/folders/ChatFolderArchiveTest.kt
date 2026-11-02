package com.ai.assistance.operit.data.chat.folders

import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.chat.archive.OperitArchivedChat
import com.ai.assistance.operit.data.chat.archive.OperitChatArchive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatFolderArchiveTest {
    @Test
    fun archiveRoundTripKeepsEmptyFoldersPathsAndRecentUsage() {
        val root = ChatFolder("root", "项目", displayOrder = 0)
        val child = ChatFolder("child", "Operit", root.id, displayOrder = 1)
        val empty = ChatFolder("empty", "空目录", root.id, displayOrder = 2)
        val history = ChatHistory(
            id = "chat", title = "重构", messages = emptyList(), folderId = child.id,
            group = child.name, displayOrder = 5, lastUsedAt = 100L,
            characterCardName = "Echo", locked = true, pinned = true,
        )
        val archived = OperitArchivedChat.fromChatHistory(history, emptyList()).copy(folderPath = listOf(root, child))
        val original = OperitChatArchive(chats = listOf(archived), folders = listOf(root, child, empty))
        // 与归档导出一致地编码默认值，避免 exportedAt 被省略后在解码时重新生成。
        val archiveJson = Json { encodeDefaults = true }
        val restored = archiveJson.decodeFromString<OperitChatArchive>(archiveJson.encodeToString(original))
        assertEquals(original, restored)
        val restoredHistory = restored.chats.single().toChatHistory()
        assertEquals(history, restoredHistory)
        val entity = ChatEntity.fromChatHistory(restoredHistory)
        assertEquals(child.id, entity.folderId)
        assertEquals(100L, entity.lastUsedAt)
    }

    @Test
    fun oldArchivedChatCanBeDecodedWithoutFolderFields() {
        val archived = Json.decodeFromString<OperitArchivedChat>("""
            {"id":"old","title":"旧对话","messages":[],"group":"项目"}
        """.trimIndent())
        assertNull(archived.folderId)
        assertEquals("项目", archived.group)
        assertEquals(emptyList<ChatFolder>(), archived.folderPath)
    }
}

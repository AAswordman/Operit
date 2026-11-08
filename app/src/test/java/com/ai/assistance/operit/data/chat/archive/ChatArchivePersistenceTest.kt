package com.ai.assistance.operit.data.chat.archive

import com.ai.assistance.operit.data.converter.ChatHistoryCsv
import com.ai.assistance.operit.core.chat.context.TokenUsageBreakdown
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.chat.archive.OperitArchivedChat
import com.ai.assistance.operit.data.chat.archive.OperitChatArchive
import com.ai.assistance.operit.data.stats.JvmSupportSQLiteDatabase
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatArchivePersistenceTest {
    @Test
    fun publishedChatsRemainVisibleAndKeepMessagesAfterUpgrade() {
        JvmSupportSQLiteDatabase.open(":memory:").use { db ->
            db.execSQL("CREATE TABLE chats (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, locked INTEGER NOT NULL, pinned INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE messages (chatId TEXT NOT NULL, content TEXT NOT NULL)")
            db.execSQL("INSERT INTO chats VALUES ('old', '已有会话', 1, 1)")
            db.execSQL("INSERT INTO messages VALUES ('old', '保留消息')")
            ChatArchiveMigration.migrate(db)
            // 验证新增字段的默认值也适用于升级后仍未显式填写归档状态的记录。
            db.execSQL("INSERT INTO chats (id, title, locked, pinned) VALUES ('new', '新会话', 0, 0)")
            db.query("SELECT title, locked, pinned, archived FROM chats WHERE id = 'old'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("已有会话", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
            }
            db.query("SELECT archived FROM chats WHERE id = 'new'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT content FROM messages WHERE chatId = 'old'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("保留消息", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun archivedChatSurvivesEntityAndBackupRoundTripsWithItsOrganization() {
        val history = archivedHistory()
        val restoredFromEntity = ChatEntity.fromChatHistory(history).toChatHistory(history.messages)
        assertEquals(history, restoredFromEntity)
        val archive = OperitChatArchive(
            chats = listOf(OperitArchivedChat.fromChatHistory(restoredFromEntity, emptyList())),
        )
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString<OperitChatArchive>(json.encodeToString(archive))
        assertEquals(history, restored.chats.single().toChatHistory())
    }

    @Test
    fun publishedBackupsWithoutArchiveFlagRemainVisible() {
        val legacy = Json.decodeFromString<OperitArchivedChat>(
            """{"id":"legacy","title":"旧备份","messages":[],"locked":true,"pinned":true}""",
        ).toChatHistory()
        assertFalse(legacy.archived)
        assertEquals(null, legacy.usageBreakdownJson)
        assertTrue(legacy.locked)
        assertTrue(legacy.pinned)
        assertTrue(ChatHistoryCsv.supportsHeader(ChatHistoryCsv.HEADER - setOf("archived", "usage_breakdown_json")))
    }

    @Test
    fun csvExportsArchiveFlagWithoutLosingFolderAndLockMetadata() {
        val writer = StringWriter()
        ChatHistoryCsv.writeHeader(writer)
        ChatHistoryCsv.writeChat(writer, OperitArchivedChat.fromChatHistory(archivedHistory(), emptyList()))
        val reader = ChatHistoryCsv.RowReader(StringReader(writer.toString()))
        val header = requireNotNull(reader.nextRow())
        val chat = requireNotNull(reader.nextRow())
        assertEquals(header.size, chat.size)
        assertEquals("true", chat[header.indexOf("archived")])
        assertEquals("true", chat[header.indexOf("locked")])
        assertEquals("true", chat[header.indexOf("pinned")])
        assertEquals("folder", chat[header.indexOf("folder_id")])
        assertEquals("Echo", chat[header.indexOf("character_card_name")])
        assertEquals("标题含,逗号", chat[header.indexOf("title")])
        assertEquals(archivedHistory().usageBreakdownJson, chat[header.indexOf("usage_breakdown_json")])
    }

    private fun archivedHistory(): ChatHistory {
        val date = LocalDateTime.of(2026, 10, 19, 12, 0)
        return ChatHistory(
            id = "archived",
            title = "标题含,逗号",
            messages = emptyList(),
            createdAt = date,
            updatedAt = date,
            inputTokens = 123,
            outputTokens = 45,
            usageBreakdownJson = TokenUsageBreakdown(
                userMessageTokens = 100L, systemPromptTokens = 23L, outputTokens = 45L,
            ).encode(),
            group = "资料夹",
            displayOrder = -42,
            workspace = "/workspace",
            workspaceEnv = "linux",
            characterCardName = "Echo",
            locked = true,
            pinned = true,
            folderId = "folder",
            lastUsedAt = 12345,
            archived = true,
        )
    }
}
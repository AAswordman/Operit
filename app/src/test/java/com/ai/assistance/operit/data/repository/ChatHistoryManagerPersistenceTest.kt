package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.Room
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.db.JdbcSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatHistoryManagerPersistenceTest {
    @Test fun processMetadataDistinguishesBlankFinalWithoutHidingProcessRows() = withDatabase { database ->
        database.chatDao().insertChat(ChatEntity(id = "root", title = "Root"))
        val bodies = listOf("", " \n\t", "\u3000", "answer")
        bodies.forEachIndexed { index, body ->
            database.messageDao().insertMessage(com.ai.assistance.operit.data.model.MessageEntity(
                chatId = "root", sender = "ai", content = body, timestamp = index.toLong(),
                orderIndex = index, sentAt = 10, completedAt = 20,
            ))
        }
        database.messageDao().insertMessage(com.ai.assistance.operit.data.model.MessageEntity(
            chatId = "root", sender = "ai", content = "", timestamp = 4,
            orderIndex = 4, sentAt = 10, completedAt = 20, displayMode = "ASSISTANT_INTERMEDIATE",
        ))
        assertEquals(listOf(false, false, false, true, true),
            database.messageDao().getProcessMetadata("root").map { it.hasContent })
    }
    @Test fun restartingArchivedRunMakesItVisibleAgain() = withDatabase { database ->
        val parent = ChatEntity(id = "parent", title = "Parent", group = "Work")
        val child = ChatEntity(id = "child", title = "Child", parentChatId = parent.id,
            chatKind = "SUBAGENT", group = parent.group)
        database.chatDao().insertChat(parent)
        database.chatDao().insertChat(child)
        database.subagentRunDao().insert(com.ai.assistance.operit.data.model.SubagentRunEntity(
            id = "run", parentChatId = parent.id, childChatId = child.id,
            agentProfileId = "profile", title = "Child", status = "COMPLETED", archivedAt = 10L,
        ))
        assertEquals(1, database.subagentRunDao().updateStatus("run", listOf("COMPLETED"), "QUEUED"))
        org.junit.Assert.assertNull(database.subagentRunDao().getById("run")?.archivedAt)
        assertEquals("Work", database.chatDao().getChatById(child.id)?.toChatHistory(emptyList())?.group)
        assertEquals("SUBAGENT", database.chatDao().getChatById(child.id)?.toChatHistory(emptyList())?.chatKind)
    }

    @Test fun nestedSubagentsAreDeletedLeafFirstWithoutDeletingSiblingChats() = withDatabase { database ->
        database.chatDao().insertChat(ChatEntity(id = "root", title = "Root"))
        database.chatDao().insertChat(ChatEntity(id = "unrelated", title = "Keep"))
        // Exercise the real generated DAO and FK graph. This JVM fixture uses SQLiteDriver,
        // whereas Android's repository transactions use SupportSQLiteOpenHelper.
        suspend fun child(parent: String): ChatEntity {
            val chat = ChatEntity(title = "Child", parentChatId = parent, chatKind = "SUBAGENT")
            database.chatDao().insertChat(chat)
            database.subagentRunDao().insert(com.ai.assistance.operit.data.model.SubagentRunEntity(
                id = chat.id, parentChatId = parent, childChatId = chat.id,
                agentProfileId = "profile", title = "Child",
            ))
            return chat
        }
        suspend fun deleteTree(id: String) {
            ChatDeletionGraphPolicy.orderChildFirst(database.chatDao().getChatAndSubagentDescendants(id))
                .forEach { database.chatDao().deleteChat(it.id) }
        }
        val first = child("root")
        val nested = child(first.id)
        val sibling = child("root")
        assertEquals(4, database.chatDao().getChatAndSubagentDescendants("root").size)
        deleteTree(first.id)
        org.junit.Assert.assertNull(database.chatDao().getChatById(nested.id))
        org.junit.Assert.assertNotNull(database.chatDao().getChatById(sibling.id))
        deleteTree("root")
        assertEquals(listOf("unrelated"), database.chatDao().getAllChatsDirectly().map { it.id })
        org.junit.Assert.assertTrue(database.subagentRunDao().getAll().isEmpty())
    }

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("subagent-persistence-test").toFile()
        val database =
            Room.databaseBuilder(mockContext(tempDir), AppDatabase::class.java, "app_database")
                .setDriver(JdbcSQLiteDriver())
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking { block(database) }
        } finally {
            database.close()
            tempDir.deleteRecursively()
        }
    }

    private fun mockContext(filesDir: File): Context {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.packageName).thenReturn("com.ai.assistance.operit")
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.getDatabasePath(any())).thenAnswer { invocation ->
            File(filesDir, invocation.getArgument<String>(0))
        }
        return context
    }

    private companion object {
        const val FOLDER_ID = "folder"
        const val CURRENT_CHAT_ID = "current"
    }
}

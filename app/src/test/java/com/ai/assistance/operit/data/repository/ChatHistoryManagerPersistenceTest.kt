package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.Room
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatFolderEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.SYSTEM_UNGROUPED_FOLDER_ID
import com.ai.assistance.operit.data.db.JdbcSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatHistoryManagerPersistenceTest {
    @Test
    fun `system ungrouped folder id is normalized to null`() {
        assertEquals(null, normalizeChatFolderId(SYSTEM_UNGROUPED_FOLDER_ID))
        assertEquals("regular", normalizeChatFolderId("regular"))
    }

    @Test
    fun `normal save preserves repository owned folder placement`() {
        val existing =
            ChatEntity(
                id = "chat",
                title = "before",
                folderId = "new-folder",
                displayOrder = 7,
                group = "legacy",
            )
        val staleIncoming =
            existing.copy(
                title = "after",
                folderId = null,
                displayOrder = 0,
                group = "stale-group",
            )

        val merged =
            mergePersistedChatEntity(
                incoming = staleIncoming,
                existing = existing,
                preserveStructure = true,
            )

        assertEquals("after", merged.title)
        assertEquals("new-folder", merged.folderId)
        assertEquals(7, merged.displayOrder)
        assertEquals("legacy", merged.group)
    }

    @Test
    fun `archive import may intentionally replace folder placement`() {
        val existing =
            ChatEntity(
                id = "chat",
                title = "before",
                folderId = "local-folder",
                displayOrder = 7,
                group = "legacy",
            )
        val imported =
            existing.copy(
                folderId = "archive-folder",
                displayOrder = 2,
                group = null,
            )

        val merged =
            mergePersistedChatEntity(
                incoming = imported,
                existing = existing,
                preserveStructure = false,
            )

        assertEquals("archive-folder", merged.folderId)
        assertEquals(2, merged.displayOrder)
        assertEquals("legacy", merged.group)
    }

    @Test
    fun `new chat without an inheritance source is persisted as ungrouped`() =
        withDatabase { database ->
            insertFolderedCurrentChat(database)

            val created =
                createAndPersistNewChatHistory(
                    chatDao = database.chatDao(),
                    chatFolderDao = database.chatFolderDao(),
                    folderId = null,
                    inheritGroupFromChatId = null,
                ) { resolvedFolderId ->
                    newHistory(id = "new-ungrouped", folderId = resolvedFolderId)
                }

            assertEquals(null, created.folderId)
            assertEquals(null, database.chatDao().getChatById(created.id)?.folderId)
        }

    @Test
    fun `new chat with an inheritance source keeps the current folder`() =
        withDatabase { database ->
            insertFolderedCurrentChat(database)

            val created =
                createAndPersistNewChatHistory(
                    chatDao = database.chatDao(),
                    chatFolderDao = database.chatFolderDao(),
                    folderId = null,
                    inheritGroupFromChatId = CURRENT_CHAT_ID,
                ) { resolvedFolderId ->
                    newHistory(id = "new-inherited", folderId = resolvedFolderId)
                }

            assertEquals(FOLDER_ID, created.folderId)
            assertEquals(FOLDER_ID, database.chatDao().getChatById(created.id)?.folderId)
        }

    private suspend fun insertFolderedCurrentChat(database: AppDatabase) {
        database.chatFolderDao().insertFolder(
            ChatFolderEntity(
                id = FOLDER_ID,
                name = "Folder",
                parentFolderId = null,
                displayOrder = 0,
                createdAt = 1,
            )
        )
        database.chatDao().insertChat(
            ChatEntity(
                id = CURRENT_CHAT_ID,
                title = "Current",
                folderId = FOLDER_ID,
            )
        )
    }

    private fun newHistory(id: String, folderId: String?): ChatHistory =
        ChatHistory(
            id = id,
            title = id,
            messages = emptyList(),
            folderId = folderId,
        )

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("new-chat-folder-test").toFile()
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

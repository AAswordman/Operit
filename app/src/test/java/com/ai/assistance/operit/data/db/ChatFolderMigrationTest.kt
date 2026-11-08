package com.ai.assistance.operit.data.db

import com.ai.assistance.operit.data.stats.JvmSupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFolderMigrationTest {
    @Test
    fun migrationPreservesChatsBindingsAndOrderWhileCreatingStableFolders() {
        JvmSupportSQLiteDatabase.open(":memory:").use { db ->
            db.execSQL("""
                CREATE TABLE chats (
                    id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, `group` TEXT,
                    displayOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    characterCardName TEXT, characterGroupId TEXT, locked INTEGER NOT NULL, pinned INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE TABLE messages (chatId TEXT NOT NULL, timestamp INTEGER NOT NULL, sender TEXT NOT NULL)")
            db.execSQL("INSERT INTO chats VALUES ('a', '原对话', '项目/Operit', -20, 10, 999, 'Echo', NULL, 1, 1)")
            db.execSQL("INSERT INTO chats VALUES ('b', '另一角色', '项目/Operit', 4, 30, 999, NULL, 'team', 0, 0)")
            db.execSQL("INSERT INTO chats VALUES ('c', '未分组', NULL, 5, 40, 999, NULL, NULL, 0, 0)")
            db.execSQL("INSERT INTO messages VALUES ('a', 20, 'user'), ('a', 90, 'ai')")
            AppDatabase.MIGRATION_21_22.migrate(db)

            val folderId = db.query("SELECT id, name, parentId, displayOrder FROM chat_folders").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("项目/Operit", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertEquals(-20L, cursor.getLong(3))
                val id = cursor.getString(0)
                assertNotNull(id)
                assertFalse(cursor.moveToNext())
                id
            }
            db.query("SELECT id, folderId, lastUsedAt, displayOrder, characterCardName, characterGroupId, locked, pinned FROM chats ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("a", cursor.getString(0))
                assertEquals(folderId, cursor.getString(1))
                assertEquals(20L, cursor.getLong(2))
                assertEquals(-20L, cursor.getLong(3))
                assertEquals("Echo", cursor.getString(4))
                assertTrue(cursor.isNull(5))
                assertEquals(1, cursor.getInt(6))
                assertEquals(1, cursor.getInt(7))
                assertTrue(cursor.moveToNext())
                assertEquals(folderId, cursor.getString(1))
                assertEquals(30L, cursor.getLong(2))
                assertEquals("team", cursor.getString(5))
                assertTrue(cursor.moveToNext())
                assertTrue(cursor.isNull(1))
                assertEquals(40L, cursor.getLong(2))
                assertFalse(cursor.moveToNext())
            }
            db.query("SELECT COUNT(*) FROM messages").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        }
    }
}

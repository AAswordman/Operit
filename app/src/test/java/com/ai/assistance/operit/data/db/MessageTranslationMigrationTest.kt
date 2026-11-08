package com.ai.assistance.operit.data.db

import com.ai.assistance.operit.data.stats.JvmSupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTranslationMigrationTest {
    @Test
    fun version26UpgradePreservesHistoryAndSeparatesLanguageAndChatCaches() {
        JvmSupportSQLiteDatabase.open(":memory:").use { db ->
            createPublishedChats(db)
            AppDatabase.MIGRATION_26_27.migrate(db)
            save(db, "chat-one", "en", "first-hash", "First result")
            save(db, "chat-one", "ja", "first-hash", "別の言語")
            save(db, "chat-two", "en", "second-hash", "Other chat")
            save(db, "chat-one", "en", "edited-hash", "Updated result")

            db.query("SELECT COUNT(*) FROM message_translations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
            db.query("""
                SELECT sourceTextHash, translatedText FROM message_translations
                WHERE chatId = 'chat-one' AND messageTimestamp = 100 AND targetLanguageCode = 'en'
            """.trimIndent()).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("edited-hash", cursor.getString(0))
                assertEquals("Updated result", cursor.getString(1))
                assertFalse(cursor.moveToNext())
            }
            db.query("SELECT title FROM chats ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("原有聊天一", cursor.getString(0))
                assertTrue(cursor.moveToNext())
                assertEquals("原有聊天二", cursor.getString(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    @Test
    fun deletingChatRemovesItsTranslationCacheOnly() {
        JvmSupportSQLiteDatabase.open(":memory:").use { db ->
            createPublishedChats(db)
            AppDatabase.MIGRATION_26_27.migrate(db)
            save(db, "chat-one", "en", "one", "First result")
            save(db, "chat-one", "ja", "one", "別の言語")
            save(db, "chat-two", "en", "two", "Other chat")
            db.execSQL("DELETE FROM chats WHERE id = 'chat-one'")
            db.query("SELECT chatId FROM message_translations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("chat-two", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun createPublishedChats(db: JvmSupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("CREATE TABLE chats (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
        db.execSQL("INSERT INTO chats VALUES ('chat-one', '原有聊天一'), ('chat-two', '原有聊天二')")
    }

    private fun save(db: JvmSupportSQLiteDatabase, chatId: String, language: String, hash: String, text: String) {
        db.execSQL(
            """
                INSERT OR REPLACE INTO message_translations
                    (chatId, messageTimestamp, targetLanguageCode, sourceTextHash, translatedText, updatedAt)
                VALUES (?, 100, ?, ?, ?, 200)
            """.trimIndent(),
            arrayOf(chatId, language, hash, text),
        )
    }
}
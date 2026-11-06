package com.ai.assistance.operit.data.db

import androidx.sqlite.SQLiteConnection
import org.junit.Assert.*
import org.junit.Test

class ChatFeatureMigrationTest {
    @Test
    fun releasedHistorySurvivesFolderMigration() {
        val db = JdbcSQLiteDriver().open(":memory:")
        try {
            db.execute("PRAGMA foreign_keys = ON")
            db.execute(
                """CREATE TABLE chats (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    inputTokens INTEGER NOT NULL, outputTokens INTEGER NOT NULL,
                    currentWindowSize INTEGER NOT NULL, `group` TEXT, displayOrder INTEGER NOT NULL,
                    workspace TEXT, workspaceEnv TEXT, parentChatId TEXT,
                    characterCardName TEXT, characterGroupId TEXT,
                    locked INTEGER NOT NULL, pinned INTEGER NOT NULL
                )"""
            )
            db.execute(
                """CREATE TABLE messages (
                    messageId INTEGER NOT NULL PRIMARY KEY, chatId TEXT NOT NULL,
                    timestamp INTEGER NOT NULL, content TEXT NOT NULL,
                    FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE
                )"""
            )
            db.execute("CREATE TABLE token_usage_records (id INTEGER PRIMARY KEY, tokens INTEGER)")
            db.execute("INSERT INTO token_usage_records VALUES (1, 5000000000)")
            for ((id, card, group) in listOf(
                Triple("root", "alice", "Work"),
                Triple("branch", "alice", "Work"),
                Triple("other-card", "bob", "Work"),
                Triple("ungrouped", "alice", " "),
            )) {
                db.execute(
                    """INSERT INTO chats VALUES (
                        '$id', '$id', 100, 200, 5000000000, 6000000000, 7000000000,
                        '$group', 1, '/workspace', 'linux',
                        ${if (id == "branch") "'root'" else "NULL"},
                        '$card', NULL, 0, 0
                    )"""
                )
            }
            db.execute("INSERT INTO messages VALUES (1, 'root', 321, 'keep this history')")
            ChatFolderMigration.migrate(db)

            assertEquals(4L, db.number("SELECT COUNT(*) FROM chats"))
            assertEquals(2L, db.number("SELECT COUNT(*) FROM chat_folders"))
            db.prepare("SELECT id FROM chat_folders").use { statement ->
                while (statement.step()) {
                    val id = statement.getText(0)
                    assertEquals(id, java.util.UUID.fromString(id).toString())
                }
            }
            assertEquals(1L, db.number(
                "SELECT COUNT(*) FROM chats a JOIN chats b ON a.folderId = b.folderId WHERE a.id='root' AND b.id='branch'"
            ))
            assertEquals(0L, db.number(
                "SELECT COUNT(*) FROM chats a JOIN chats b ON a.folderId = b.folderId WHERE a.id='root' AND b.id='other-card'"
            ))
            assertEquals(1L, db.number("SELECT COUNT(*) FROM chats WHERE id='ungrouped' AND folderId IS NULL"))
            assertEquals(321L, db.number("SELECT lastMessageAt FROM chats WHERE id='root'"))
            assertEquals(5000000000L, db.number("SELECT inputTokens FROM chats WHERE id='root'"))
            assertEquals(7000000000L, db.number("SELECT currentWindowSize FROM chats WHERE id='root'"))
            assertEquals(5000000000L, db.number("SELECT tokens FROM token_usage_records"))
            assertEquals("keep this history", db.text("SELECT content FROM messages"))

            // Removing a classification must not remove the user's conversations.
            db.execute("DELETE FROM chat_folders")
            assertEquals(4L, db.number("SELECT COUNT(*) FROM chats WHERE folderId IS NULL"))
            db.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        } finally {
            db.close()
        }
    }

    private fun SQLiteConnection.execute(sql: String) = prepare(sql).use { it.step(); Unit }
    private fun SQLiteConnection.number(sql: String): Long =
        prepare(sql).use { assertTrue(it.step()); it.getLong(0) }
    private fun SQLiteConnection.text(sql: String): String =
        prepare(sql).use { assertTrue(it.step()); it.getText(0) }
}

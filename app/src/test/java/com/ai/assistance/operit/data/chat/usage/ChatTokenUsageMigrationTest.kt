package com.ai.assistance.operit.data.chat.usage

import com.ai.assistance.operit.data.stats.JvmSupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test

class ChatTokenUsageMigrationTest {
    @Test
    fun migrationPreservesOldTotalsAndDoesNotFabricateBreakdowns() {
        JvmSupportSQLiteDatabase.open(":memory:").use { db ->
            db.execSQL("CREATE TABLE chats (id TEXT PRIMARY KEY, inputTokens INTEGER NOT NULL, outputTokens INTEGER NOT NULL)")
            db.execSQL("INSERT INTO chats VALUES ('old', 1000, 75)")
            ChatTokenUsageMigration.migrate(db)
            db.query("SELECT inputTokens, outputTokens, usageBreakdownJson FROM chats WHERE id = 'old'").use { rows ->
                assertTrue(rows.moveToFirst())
                assertEquals(1000L, rows.getLong(0))
                assertEquals(75L, rows.getLong(1))
                assertTrue(rows.isNull(2))
            }
        }
    }
}

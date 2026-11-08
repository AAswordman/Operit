package com.ai.assistance.operit.data.db

import com.ai.assistance.operit.data.stats.JvmSupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedDatabaseMigrationTest {
    @Test
    fun upgradesVersion21WithoutLosingChatsVariantsOrPrices() = assertUpgrade(21)

    @Test
    fun upgradesVersion22IntegrationBaseWithoutRepeatingFolderMigration() = assertUpgrade(22)

    private fun assertUpgrade(startVersion: Int) {
        JvmSupportSQLiteDatabase.open(":memory:").use { db ->
            createLegacyHistory(db)
            AppDatabase.MIGRATION_20_21.migrate(db)
            db.execSQL("""
                INSERT INTO token_stats_models (
                    configId, provider, model, currency, inputPricePerMillion
                ) VALUES ('config', 'OPENAI', 'model', 'USD', 2.5)
            """.trimIndent())
            if (startVersion == 22) AppDatabase.MIGRATION_21_22.migrate(db)

            val migrations = listOf(
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
            )
            var version = startVersion
            for (migration in migrations.filter { it.startVersion >= startVersion }) {
                assertEquals(version, migration.startVersion)
                migration.migrate(db)
                version = migration.endVersion
            }
            assertEquals(AppDatabase.DATABASE_VERSION, version)
            assertEquals(29, version)

            db.query("SELECT title, folderId, lastUsedAt, displayOrder, locked, pinned, archived FROM chats").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("保留对话", cursor.getString(0))
                assertFalse(cursor.isNull(1))
                assertEquals(100L, cursor.getLong(2))
                assertEquals(-5L, cursor.getLong(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(1, cursor.getInt(5))
                assertEquals(0, cursor.getInt(6))
                assertFalse(cursor.moveToNext())
            }
            db.query("SELECT name FROM chat_folders").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("测试分组", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
            db.query("SELECT searchText, selectedVariantIndex, inputTokens, outputTokens FROM messages WHERE messageId = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("原回复", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(120L, cursor.getLong(2))
                assertEquals(30L, cursor.getLong(3))
            }
            db.query("SELECT searchText, variantIndex FROM message_variants WHERE variantId = 9").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("候选回复", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            db.query("SELECT inputPricePerMillion, peakPricingEnabled, longContextPricingEnabled, weekendOffPeakPricingEnabled, holidayOffPeakPricingEnabled FROM token_stats_models").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2.5, cursor.getDouble(0), 0.0)
                for (index in 1..4) assertTrue(cursor.isNull(index))
            }
            db.query("SELECT COUNT(*) FROM token_usage_records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM api_key_attempt_records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    private fun createLegacyHistory(db: JvmSupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE chats (
                id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, `group` TEXT,
                displayOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                characterCardName TEXT, characterGroupId TEXT, locked INTEGER NOT NULL, pinned INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE messages (
                messageId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId TEXT NOT NULL, sender TEXT NOT NULL, content TEXT NOT NULL,
                timestamp INTEGER NOT NULL, orderIndex INTEGER NOT NULL, roleName TEXT NOT NULL,
                selectedVariantIndex INTEGER NOT NULL, provider TEXT NOT NULL, modelName TEXT NOT NULL,
                inputTokens INTEGER NOT NULL, outputTokens INTEGER NOT NULL, cachedInputTokens INTEGER NOT NULL,
                sentAt INTEGER NOT NULL, outputDurationMs INTEGER NOT NULL, waitDurationMs INTEGER NOT NULL,
                completedAt INTEGER NOT NULL, displayMode TEXT NOT NULL, isFavorite INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE message_variants (
                variantId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId TEXT NOT NULL, messageTimestamp INTEGER NOT NULL, variantIndex INTEGER NOT NULL,
                content TEXT NOT NULL, roleName TEXT NOT NULL, provider TEXT NOT NULL, modelName TEXT NOT NULL,
                inputTokens INTEGER NOT NULL, outputTokens INTEGER NOT NULL, cachedInputTokens INTEGER NOT NULL,
                sentAt INTEGER NOT NULL, outputDurationMs INTEGER NOT NULL, waitDurationMs INTEGER NOT NULL,
                completedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO chats VALUES ('chat', '保留对话', '测试分组', -5, 100, 300, '角色', NULL, 1, 1)")
        db.execSQL("INSERT INTO messages VALUES (7, 'chat', 'ai', '原回复', 200, 0, '角色', 1, 'OPENAI', 'model', 120, 30, 20, 180, 15, 5, 220, 'NORMAL', 0)")
        db.execSQL("INSERT INTO message_variants VALUES (9, 'chat', 200, 1, '候选回复', '角色', 'OPENAI', 'model', 140, 40, 25, 180, 16, 4, 221)")
    }
}

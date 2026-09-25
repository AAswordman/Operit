package com.ai.assistance.operit.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {
    @Test
    fun migration20To21CreatesMessageIndexesExpectedByRoom() {
        val executedSql = mutableListOf<String>()

        AppDatabase.MIGRATION_20_21.migrate(recordingDatabase(executedSql))

        val normalizedSql = executedSql.map { it.replace(Regex("\\s+"), " ").trim() }
        assertSqlWasExecuted(
            normalizedSql,
            "CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)"
        )
        assertSqlWasExecuted(
            normalizedSql,
            "CREATE INDEX IF NOT EXISTS `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)"
        )
    }
    @Test
    fun migration21To22CreatesApiKeyAttemptTable() {
        val executedSql = mutableListOf<String>()

        AppDatabase.MIGRATION_21_22.migrate(recordingDatabase(executedSql))

        val normalizedSql = executedSql.map { it.replace(Regex("\\s+"), " ").trim() }
        assertTrue(
            normalizedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `api_key_attempt_records`") }
        )
        assertSqlWasExecuted(
            normalizedSql,
            "CREATE INDEX IF NOT EXISTS `index_api_key_attempt_records_occurredAtMs` ON `api_key_attempt_records` (`occurredAtMs`)"
        )
    }


    private fun recordingDatabase(executedSql: MutableList<String>): SupportSQLiteDatabase {
        val handler =
            InvocationHandler { _, method, args ->
                if (
                    method.name == "execSQL" &&
                        args != null &&
                        args.size == 1 &&
                        args[0] is String
                ) {
                    executedSql += args[0] as String
                    return@InvocationHandler Unit
                }

                throw UnsupportedOperationException(
                    "Unexpected SupportSQLiteDatabase call: ${method.name}"
                )
            }

        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase
    }

    private fun assertSqlWasExecuted(normalizedSql: List<String>, expectedSql: String) {
        assertTrue(
            "Expected migration SQL was not executed: $expectedSql",
            normalizedSql.contains(expectedSql)
        )
    }
}

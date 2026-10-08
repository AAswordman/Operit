package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteDatabase

/** 先完成结构升级；旧正文留在 searchText，后台再解析成 sections。 */
internal object MessageSectionsMigration : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) = migrate(cursorDatabase(db))

    override fun migrate(connection: SQLiteConnection) = migrate(connectionDatabase(connection))

    internal interface MigrationDatabase {
        fun execute(sql: String, args: List<Any> = emptyList())
        fun longValue(sql: String, args: List<Any> = emptyList()): Long?
    }

    internal fun migrate(db: MigrationDatabase) {
        rebuild(
            db,
            table = "messages",
            createSql = """
                CREATE TABLE `messages` (
                    `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chatId` TEXT NOT NULL,
                    `sender` TEXT NOT NULL,
                    `sections` TEXT NOT NULL,
                    `searchText` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    `roleName` TEXT NOT NULL,
                    `selectedVariantIndex` INTEGER NOT NULL,
                    `provider` TEXT NOT NULL,
                    `modelName` TEXT NOT NULL,
                    `inputTokens` INTEGER NOT NULL,
                    `outputTokens` INTEGER NOT NULL,
                    `cachedInputTokens` INTEGER NOT NULL,
                    `sentAt` INTEGER NOT NULL,
                    `outputDurationMs` INTEGER NOT NULL,
                    `waitDurationMs` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL,
                    `displayMode` TEXT NOT NULL,
                    `isFavorite` INTEGER NOT NULL,
                    FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                )
            """.trimIndent(),
            insertSql = """
                INSERT INTO `messages` (
                    `messageId`, `chatId`, `sender`, `sections`, `searchText`, `timestamp`, `orderIndex`,
                    `roleName`, `selectedVariantIndex`, `provider`, `modelName`, `inputTokens`, `outputTokens`,
                    `cachedInputTokens`, `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt`,
                    `displayMode`, `isFavorite`
                )
                SELECT
                    `messageId`, `chatId`, `sender`, $pendingSections, $pendingSearch, `timestamp`, `orderIndex`,
                    `roleName`, `selectedVariantIndex`, `provider`, `modelName`, `inputTokens`, `outputTokens`,
                    `cachedInputTokens`, `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt`,
                    `displayMode`, `isFavorite`
                FROM `messages_content_v21`
            """.trimIndent(),
        )
        db.execute("CREATE INDEX `index_messages_chatId` ON `messages` (`chatId`)")
        db.execute("CREATE INDEX `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)")
        rebuild(
            db,
            table = "message_variants",
            createSql = """
                CREATE TABLE `message_variants` (
                    `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chatId` TEXT NOT NULL,
                    `messageTimestamp` INTEGER NOT NULL,
                    `variantIndex` INTEGER NOT NULL,
                    `sections` TEXT NOT NULL,
                    `searchText` TEXT NOT NULL,
                    `roleName` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `modelName` TEXT NOT NULL,
                    `inputTokens` INTEGER NOT NULL,
                    `outputTokens` INTEGER NOT NULL,
                    `cachedInputTokens` INTEGER NOT NULL,
                    `sentAt` INTEGER NOT NULL,
                    `outputDurationMs` INTEGER NOT NULL,
                    `waitDurationMs` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                )
            """.trimIndent(),
            insertSql = """
                INSERT INTO `message_variants` (
                    `variantId`, `chatId`, `messageTimestamp`, `variantIndex`, `sections`, `searchText`,
                    `roleName`, `provider`, `modelName`, `inputTokens`, `outputTokens`, `cachedInputTokens`,
                    `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt`
                )
                SELECT
                    `variantId`, `chatId`, `messageTimestamp`, `variantIndex`, $pendingSections, $pendingSearch,
                    `roleName`, `provider`, `modelName`, `inputTokens`, `outputTokens`, `cachedInputTokens`,
                    `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt`
                FROM `message_variants_content_v21`
            """.trimIndent(),
        )
        db.execute("CREATE INDEX `index_message_variants_chatId_messageTimestamp` ON `message_variants` (`chatId`, `messageTimestamp`)")
        db.execute("CREATE UNIQUE INDEX `index_message_variants_chatId_messageTimestamp_variantIndex` ON `message_variants` (`chatId`, `messageTimestamp`, `variantIndex`)")
    }

    private fun rebuild(db: MigrationDatabase, table: String, createSql: String, insertSql: String) {
        val sequence = db.longValue("SELECT seq FROM sqlite_sequence WHERE name = ?", listOf(table))
        db.execute("ALTER TABLE `$table` RENAME TO `${table}_content_v21`")
        db.execute(createSql)
        db.execute(insertSql)
        db.execute("DROP TABLE `${table}_content_v21`")
        if (sequence != null) {
            db.execute("DELETE FROM sqlite_sequence WHERE name = ?", listOf(table))
            db.execute("INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)", listOf(table, sequence))
        }
    }

    private fun cursorDatabase(db: SupportSQLiteDatabase) = object : MigrationDatabase {
        override fun execute(sql: String, args: List<Any>) {
            db.execSQL(sql, args.toTypedArray())
        }
        override fun longValue(sql: String, args: List<Any>): Long? =
            db.query(sql, args.toTypedArray()).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }

    private fun connectionDatabase(connection: SQLiteConnection) = object : MigrationDatabase {
        private fun prepare(sql: String, args: List<Any>): SQLiteStatement {
            val statement = connection.prepare(sql)
            args.forEachIndexed { index, value ->
                when (value) {
                    is Long -> statement.bindLong(index + 1, value)
                    is String -> statement.bindText(index + 1, value)
                    else -> error("Unsupported migration parameter")
                }
            }
            return statement
        }
        override fun execute(sql: String, args: List<Any>) {
            val statement = prepare(sql, args)
            try { statement.step() } finally { statement.close() }
        }
        override fun longValue(sql: String, args: List<Any>): Long? {
            val statement = prepare(sql, args)
            return try {
                if (statement.step() && !statement.isNull(0)) statement.getLong(0) else null
            } finally { statement.close() }
        }
    }
    private const val pendingSections = "''"
    private const val pendingSearch = "content"

}

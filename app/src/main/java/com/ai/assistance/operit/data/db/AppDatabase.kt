package com.ai.assistance.operit.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.dao.ChatContentDao
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.dao.MessageDao
import com.ai.assistance.operit.data.dao.MessageVariantDao
import com.ai.assistance.operit.data.dao.TokenUsageDao
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import com.ai.assistance.operit.data.persistence.RoomRecoveryStorage
import com.ai.assistance.operit.data.persistence.StorageReplacementGate
import com.ai.assistance.operit.util.AppLogger
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID

/** 应用数据库，包含聊天表和消息表 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageVariantEntity::class,
        TokenUsageRecordEntity::class,
        TokenStatsModelEntity::class,
    ],
    version = 21,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** 获取聊天DAO */
    abstract fun chatDao(): ChatDao

    /** 获取消息DAO */
    abstract fun messageDao(): MessageDao

    abstract fun messageVariantDao(): MessageVariantDao

    abstract fun chatContentDao(): ChatContentDao

    abstract fun tokenUsageDao(): TokenUsageDao

    internal data class SnapshotFile(
        val relativePath: String,
        val file: File
    )

    internal class SnapshotExport internal constructor(
        val files: List<SnapshotFile>,
        private val stagingDirectory: File
    ) : Closeable {
        override fun close() {
            if (stagingDirectory.exists() && !stagingDirectory.deleteRecursively()) {
                AppLogger.w(
                    "AppDatabase",
                    "Failed to remove Room raw snapshot staging directory"
                )
            }
        }
    }

    companion object {
        const val DATABASE_VERSION = 21

        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var databaseContext: Context? = null

        // 定义从版本1到2的迁移
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 创建chats表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `chats` (
                                `id` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent()
                    )

                    // 创建messages表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `messages` (
                                `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `sender` TEXT NOT NULL,
                                `content` TEXT NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `orderIndex` INTEGER NOT NULL,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )

                    // 为messages表创建索引
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
                }

            }

        // 定义从版本10到11的迁移
        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspaceEnv列
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `workspaceEnv` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本11到12的迁移
        private val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加characterGroupId列（用于绑定群组角色卡）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `characterGroupId` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        private val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `inputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `cachedInputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `sentAt` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `waitDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                }
            }

        private val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `problem_records`")
                }
            }

        private val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `selectedVariantIndex` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `message_variants` (
                                `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `messageTimestamp` INTEGER NOT NULL,
                                `variantIndex` INTEGER NOT NULL,
                                `content` TEXT NOT NULL,
                                `roleName` TEXT NOT NULL DEFAULT '',
                                `provider` TEXT NOT NULL DEFAULT '',
                                `modelName` TEXT NOT NULL DEFAULT '',
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                `cachedInputTokens` INTEGER NOT NULL DEFAULT 0,
                                `sentAt` INTEGER NOT NULL DEFAULT 0,
                                `outputDurationMs` INTEGER NOT NULL DEFAULT 0,
                                `waitDurationMs` INTEGER NOT NULL DEFAULT 0,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp` ON `message_variants` (`chatId`, `messageTimestamp`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp_variantIndex` ON `message_variants` (`chatId`, `messageTimestamp`, `variantIndex`)"
                    )
                }
            }

        private val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `displayMode` TEXT NOT NULL DEFAULT 'NORMAL'"
                    )
                }
            }

        private val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)"
                    )
                }
            }

        private val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE message_variants ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_19_20 =
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v20 -> v21: final two-table token statistics schema. Intermediate v21 was unpublished. */
        internal val MIGRATION_20_21 =
            object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    runSql { db.execSQL(it) }
                }

                override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                    runSql { sql ->
                        val stmt = connection.prepare(sql)
                        try {
                            stmt.step()
                        } finally {
                            stmt.close()
                        }
                    }
                }

                private fun runSql(exec: (String) -> Unit) {
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `token_usage_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `importKey` TEXT,
                            `occurredAtMs` INTEGER,
                            `source` TEXT NOT NULL,
                            `configId` TEXT,
                            `provider` TEXT NOT NULL,
                            `model` TEXT NOT NULL,
                            `category` TEXT,
                            `status` TEXT,
                            `requestCount` INTEGER,
                            `uncachedInputTokens` INTEGER,
                            `cachedInputTokens` INTEGER,
                            `cacheWriteTokens` INTEGER,
                            `totalInputTokens` INTEGER,
                            `outputTokens` INTEGER,
                            `reasoningTokens` INTEGER,
                            `ttftMs` INTEGER,
                            `durationMs` INTEGER
                        )
                        """.trimIndent()
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_token_usage_records_occurredAtMs` " +
                            "ON `token_usage_records` (`occurredAtMs`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS " +
                            "`index_token_usage_records_provider_model_configId_occurredAtMs` " +
                            "ON `token_usage_records` " +
                            "(`provider`, `model`, `configId`, `occurredAtMs`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_token_usage_records_source_occurredAtMs` " +
                            "ON `token_usage_records` (`source`, `occurredAtMs`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS " +
                            "`index_token_usage_records_category_status_occurredAtMs` " +
                            "ON `token_usage_records` (`category`, `status`, `occurredAtMs`)"
                    )
                    exec(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_token_usage_records_importKey` " +
                            "ON `token_usage_records` (`importKey`)"
                    )
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `token_stats_models` (
                            `configId` TEXT NOT NULL,
                            `provider` TEXT NOT NULL,
                            `model` TEXT NOT NULL,
                            `billingMode` TEXT,
                            `currency` TEXT,
                            `inputPricePerMillion` REAL,
                            `cachedInputPricePerMillion` REAL,
                            `cacheWritePricePerMillion` REAL,
                            `outputPricePerMillion` REAL,
                            `pricePerRequest` REAL,
                            PRIMARY KEY(`configId`, `provider`, `model`)
                        )
                        """.trimIndent()
                    )
                    // Copy history once so statistics deletion remains independent from chat storage.
                    exec(
                        """
                        INSERT INTO `token_usage_records` (
                            `occurredAtMs`, `source`, `configId`, `provider`, `model`,
                            `category`, `status`, `requestCount`, `uncachedInputTokens`,
                            `cachedInputTokens`, `cacheWriteTokens`, `totalInputTokens`,
                            `outputTokens`, `reasoningTokens`, `ttftMs`, `durationMs`
                        )
                        SELECT
                            `timestamp`, 'CONVERSATION', NULL, `provider`, `modelName`,
                            'CHAT', 'COMPLETED', NULL, MAX(`inputTokens` - `cachedInputTokens`, 0),
                            `cachedInputTokens`, NULL, `inputTokens`, `outputTokens`, NULL,
                            NULLIF(`waitDurationMs`, 0), NULLIF(`outputDurationMs`, 0)
                        FROM `messages`
                        WHERE `sender` = 'ai'
                            AND TRIM(`provider`) <> ''
                            AND TRIM(`modelName`) <> ''
                            AND (`inputTokens` > 0 OR `cachedInputTokens` > 0 OR `outputTokens` > 0)
                        """.trimIndent()
                    )
                    exec(
                        """
                        INSERT INTO `token_usage_records` (
                            `occurredAtMs`, `source`, `configId`, `provider`, `model`,
                            `category`, `status`, `requestCount`, `uncachedInputTokens`,
                            `cachedInputTokens`, `cacheWriteTokens`, `totalInputTokens`,
                            `outputTokens`, `reasoningTokens`, `ttftMs`, `durationMs`
                        )
                        SELECT
                            variants.`messageTimestamp`, 'CONVERSATION', NULL,
                            variants.`provider`, variants.`modelName`, 'CHAT', 'COMPLETED', NULL,
                            MAX(variants.`inputTokens` - variants.`cachedInputTokens`, 0),
                            variants.`cachedInputTokens`, NULL, variants.`inputTokens`,
                            variants.`outputTokens`, NULL, NULLIF(variants.`waitDurationMs`, 0),
                            NULLIF(variants.`outputDurationMs`, 0)
                        FROM `message_variants` AS variants
                        WHERE TRIM(variants.`provider`) <> ''
                            AND TRIM(variants.`modelName`) <> ''
                            AND (
                                variants.`inputTokens` > 0
                                OR variants.`cachedInputTokens` > 0
                                OR variants.`outputTokens` > 0
                            )
                        """.trimIndent()
                    )
                }
            }

        // 定义从版本2到3的迁移
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加group列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `group` TEXT")
                }
            }

        // 定义从版本3到4的迁移
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加displayOrder列，并用updatedAt填充现有数据
                    db.execSQL(
                        "ALTER TABLE chats ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL("UPDATE chats SET displayOrder = updatedAt")
                }
            }

        // 定义从版本4到5的迁移
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspace列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `workspace` TEXT")
                }
            }

        // 定义从版本5到6的迁移
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 检查currentWindowSize列是否已存在，如果不存在则添加
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `currentWindowSize` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本6到7的迁移
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加roleName列
                    db.execSQL("ALTER TABLE messages ADD COLUMN `roleName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本7到8的迁移
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加parentChatId列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `parentChatId` TEXT")
                    // 向chats表添加characterCardName列（用于绑定角色卡）
                    db.execSQL("ALTER TABLE chats ADD COLUMN `characterCardName` TEXT")
                }
            }

        // 定义从版本8到9的迁移
        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加provider列（供应商）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `provider` TEXT NOT NULL DEFAULT ''")
                    // 向messages表添加modelName列（模型名称）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本9到10的迁移
        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加locked列（锁定聊天，禁止删除）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        /** 获取数据库实例，单例模式 */
        fun getDatabase(context: Context): AppDatabase {
            return StorageReplacementGate.withStorageAccess {
                INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                            ?: run {
                                val appContext = context.applicationContext
                                val wasMissing =
                                    !appContext.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME)
                                        .isFile
                                RoomRecoveryStorage.prepareForOpen(appContext)
                                var instance =
                                    buildDatabase(appContext, RoomRecoveryStorage.DATABASE_NAME)
                                if (wasMissing) {
                                    try {
                                        try {
                                            instance.openHelper.writableDatabase
                                        } finally {
                                            instance.close()
                                        }
                                        RoomRecoveryStorage.checkpointClosed(appContext)
                                        RoomRecoveryStorage.prepareForOpen(appContext)
                                    } catch (e: Exception) {
                                        RoomRecoveryStorage.invalidatePreparedState()
                                        throw e
                                    }
                                    instance =
                                        buildDatabase(appContext, RoomRecoveryStorage.DATABASE_NAME)
                                }
                                databaseContext = appContext
                                INSTANCE = instance
                                instance
                            }
                    }
                }
        }

        fun closeDatabase() {
            synchronized(this) {
                val context = databaseContext
                try {
                    INSTANCE?.close()
                } finally {
                    INSTANCE = null
                    databaseContext = null
                }
                if (context != null) {
                    try {
                        RoomRecoveryStorage.checkpointClosed(context)
                    } catch (e: Exception) {
                        AppLogger.e("AppDatabase", "Failed to checkpoint closed Room database", e)
                    }
                } else {
                    // Startup preflight can complete without constructing the singleton. A later
                    // raw replacement must still force the imported database through validation.
                    RoomRecoveryStorage.invalidatePreparedState()
                }
            }
        }

        /**
         * Keeps the singleton monitor held from close through replacement. Without this boundary,
         * a background repository can reopen Room after close and race the raw file replacement.
         */
        fun replaceWithVerifiedDatabase(
            context: Context,
            replacementDatabaseFile: File,
            reason: String
        ) {
            synchronized(this) {
                closeDatabase()
                RoomRecoveryStorage.replaceWithVerifiedDatabase(
                    context.applicationContext,
                    replacementDatabaseFile,
                    reason
                )
            }
        }

        /**
         * Opens an isolated copy through Room so page-valid SQLite files with a damaged Room
         * schema never become recovery snapshots.
         */
        internal fun validateRecoveryCandidate(context: Context, candidate: File): Boolean {
            if (!candidate.isFile) return false
            val appContext = context.applicationContext
            val validationName =
                "room_recovery_validation_${UUID.randomUUID().toString().replace("-", "")}"
            val validationFile = appContext.getDatabasePath(validationName)
            validationFile.parentFile?.mkdirs()
            return try {
                candidate.copyTo(validationFile, overwrite = false)
                // Room normally trusts a matching identity hash. Removing the identity table from
                // this isolated copy forces RoomOpenHelper to compare the actual tables, columns,
                // foreign keys, and indexes before the candidate can become a recovery snapshot.
                SQLiteDatabase.openDatabase(
                    validationFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                ).use { database ->
                    database.execSQL("DROP TABLE IF EXISTS `room_master_table`")
                }
                val database = buildDatabase(appContext, validationName)
                try {
                    database.openHelper.writableDatabase
                    true
                } finally {
                    database.close()
                }
            } catch (e: Exception) {
                AppLogger.e("AppDatabase", "Room schema validation failed", e)
                if (isOperationalValidationFailure(e)) throw e
                false
            } finally {
                databaseFiles(validationFile).forEach { file ->
                    if (file.exists() && !file.delete()) {
                        AppLogger.w(
                            "AppDatabase",
                            "Failed to delete Room validation file: ${file.name}"
                        )
                    }
                }
            }
        }

        internal fun stageForSnapshotExport(context: Context): SnapshotExport {
            val appContext = context.applicationContext
            val stagingDirectory =
                File(appContext.cacheDir, "room_raw_export_${UUID.randomUUID()}")
            check(stagingDirectory.mkdirs()) {
                "Failed to create Room raw snapshot staging directory"
            }
            val stagedDatabase = File(stagingDirectory, RoomRecoveryStorage.DATABASE_NAME)

            try {
                synchronized(this) {
                    val instance = INSTANCE
                    if (instance != null) {
                        instance.runInTransaction(Runnable {
                            copyDatabaseForSnapshot(appContext, stagedDatabase)
                        })
                    } else {
                        copyDatabaseForSnapshot(appContext, stagedDatabase)
                    }
                }

                check(RoomRecoveryStorage.validateDatabaseSet(appContext, stagedDatabase)) {
                    "Room raw snapshot copy failed database and schema validation"
                }
                databaseFiles(stagedDatabase).drop(1).forEach { sidecar ->
                    if (sidecar.exists() && !sidecar.deleteRecursively()) {
                        throw IllegalStateException(
                            "Failed to remove Room raw snapshot sidecar: ${sidecar.name}"
                        )
                    }
                }
                check(stagedDatabase.isFile) {
                    "Room raw snapshot validation removed the staged database"
                }
                return SnapshotExport(
                    files =
                        listOf(
                            SnapshotFile(
                                relativePath = RoomRecoveryStorage.DATABASE_NAME,
                                file = stagedDatabase
                            )
                        ),
                    stagingDirectory = stagingDirectory
                )
            } catch (e: Exception) {
                if (!stagingDirectory.deleteRecursively()) {
                    AppLogger.w(
                        "AppDatabase",
                        "Failed to clean rejected Room raw snapshot staging directory"
                    )
                }
                throw e
            }
        }

        private fun copyDatabaseForSnapshot(context: Context, target: File) {
            val source = context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME)
            check(source.isFile) { "Room raw snapshot source database is missing" }
            source.copyTo(target, overwrite = false)

            val sourceWal = File(source.absolutePath + "-wal")
            if (sourceWal.exists()) {
                check(sourceWal.isFile) { "Room raw snapshot WAL path is not a file" }
                sourceWal.copyTo(File(target.absolutePath + "-wal"), overwrite = false)
            }
        }

        private fun buildDatabase(context: Context, databaseName: String): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                databaseName
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21
                )
                .build()

        private fun databaseFiles(databaseFile: File): List<File> =
            listOf(
                databaseFile,
                File(databaseFile.absolutePath + "-wal"),
                File(databaseFile.absolutePath + "-shm"),
                File(databaseFile.absolutePath + "-journal")
            )

        private fun isOperationalValidationFailure(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                if (current is IOException ||
                    current is SQLiteAccessPermException ||
                    current is SQLiteCantOpenDatabaseException ||
                    current is SQLiteDiskIOException ||
                    current is SQLiteFullException ||
                    current is SQLiteReadOnlyDatabaseException
                ) {
                    return true
                }
                val cause = current.cause
                if (cause === current) break
                current = cause
            }
            return false
        }
    }
}

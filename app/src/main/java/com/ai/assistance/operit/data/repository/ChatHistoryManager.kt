package com.ai.assistance.operit.data.repository

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.SubagentCoordinator
import com.ai.assistance.operit.data.backup.OperitBackupDirs
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.model.CharacterCardChatStats
import com.ai.assistance.operit.data.model.CharacterGroupChatStats
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import com.ai.assistance.operit.data.model.OperitArchivedChat
import com.ai.assistance.operit.data.model.OperitArchivedMessage
import com.ai.assistance.operit.data.model.OperitArchivedMessageVariant
import com.ai.assistance.operit.data.model.OperitArchivedSubagentRun
import com.ai.assistance.operit.data.model.OperitChatArchive
import com.ai.assistance.operit.data.model.SubagentRunStatus
import com.ai.assistance.operit.data.model.WorkspaceRenameResult
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.data.converter.*
import com.ai.assistance.operit.data.exporter.*
import com.google.gson.GsonBuilder
import com.google.gson.internal.Streams
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.io.BufferedWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// 仅保留这个DataStore用于存储当前聊天ID
private val Context.currentChatIdDataStore by preferencesDataStore(name = "current_chat_id")

data class ChatExportProgress(
    val isLongText: Boolean,
    val progress: Float,
    val processedCharacters: Long,
    val totalCharacters: Long,
)

data class ChatExportResult(
    val filePath: String,
    val chatCount: Int,
)

data class AddedMessageVariant(
    val selectedVariantIndex: Int,
    val message: ChatMessage,
)
internal val OPERIT_ARCHIVE_SUBAGENT_RUN_SNAPSHOT_COLUMNS =
    listOf(
        "id",
        "parentChatId",
        "childChatId",
        "parentToolCallId",
        "agentProfileId",
        "title",
        "status",
        "createdAt",
        "startedAt",
        "completedAt",
        "error",
        "agentConfigSnapshot",
        "modelConfigIdSnapshot",
        "modelIndexSnapshot",
        "modelRoundCount", "externalOwnerId", "externalOwnerType",
        "toolInvocationCount",
        "archivedAt",
    )

internal fun remapArchivedParentToolCallIds(
    chat: OperitArchivedChat,
    callIdRemap: Map<String, String>,
    taskIdRemap: Map<String, String> = emptyMap(),
    chatIdRemap: Map<String, String> = emptyMap(),
): OperitArchivedChat {
    if (callIdRemap.isEmpty() && taskIdRemap.isEmpty() && chatIdRemap.isEmpty()) return chat

    fun remapContent(content: String): String =
        remapPersistedTaskIds(
            callIdRemap.entries.fold(content) { current, (oldCallId, newCallId) ->
                Regex(
                    """(\bcall_id\s*=\s*")${Regex.escape(oldCallId)}(")""",
                    RegexOption.IGNORE_CASE,
                ).replace(current) { match ->
                    match.groupValues[1] + newCallId + match.groupValues[2]
                }
            },
            taskIdRemap,
        )

    return chat.copy(
        messages =
            chat.messages.map { archivedMessage ->
                archivedMessage.copy(
                    baseMessage =
                        archivedMessage.baseMessage.copy(
                            content = remapCollaborationReferences(remapContent(archivedMessage.baseMessage.content), chatIdRemap, archivedMessage.baseMessage.displayMode.isCollaborationEvent, archivedMessage.baseMessage.sender == "ai")
                        ),
                    variants =
                        archivedMessage.variants.map { variant ->
                            variant.copy(content = remapCollaborationReferences(remapContent(variant.content), chatIdRemap, archivedMessage.baseMessage.displayMode.isCollaborationEvent, archivedMessage.baseMessage.sender == "ai"))
                        },
                )
            }
    )
}

class ChatHistoryManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ChatHistoryManager"
        private const val LOCATOR_PREVIEW_CHAR_COUNT = 48
        private const val TEXT_EXPORT_STREAMING_THRESHOLD_CHARACTER_COUNT = 4_000_000L
        private const val TEXT_EXPORT_WRITER_BUFFER_SIZE = 64 * 1024
        private const val TEXT_EXPORT_PROGRESS_UPDATE_CHARACTER_COUNT = 256 * 1024L

        @Volatile
        private var INSTANCE: ChatHistoryManager? = null

        fun getInstance(context: Context): ChatHistoryManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: ChatHistoryManager(context.applicationContext).also { instance ->
                            INSTANCE = instance
                        }
                }
        }
    }

    // 使用Room数据库
    private val database = AppDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val chatBranchRepository = ChatBranchRepository(database)
    private val messageDao = database.messageDao()
    private val messageVariantDao = database.messageVariantDao()
    private val chatContentDao = database.chatContentDao()
    private val subagentRunDao = database.subagentRunDao()
    private val subagentRunRepository = SubagentRunRepository.getInstance(context)
    private val operitArchiveExportMutex = Mutex()
    private val operitArchiveJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
            isLenient = true
        }

    private data class ImportCounters(
        var newCount: Int = 0,
        var updatedCount: Int = 0,
        var skippedCount: Int = 0,
    )

    private data class ArchivedChatMetadata(
        val id: String,
        val title: String,
        val displayOrder: Long,
        val createdAt: Long,
        val hasMessages: Boolean,
        val parentChatId: String?,
        val chatKind: String?,
        val hasChatKindField: Boolean,
    )

    private data class ScannedArchive(
        val archiveType: String,
        val formatVersion: Int,
        val chats: List<ArchivedChatMetadata>,
        val subagentRuns: List<OperitArchivedSubagentRun>,
    )

    private fun hydrateMessages(
        messageEntities: List<MessageEntity>,
        variants: List<MessageVariantEntity>,
    ): List<ChatMessage> {
        val variantsByTimestamp = variants.groupBy { it.messageTimestamp }
        return messageEntities.map { messageEntity ->
            val baseMessage = messageEntity.toChatMessage()
            val messageVariants = variantsByTimestamp[messageEntity.timestamp].orEmpty()
            val variantCount = messageVariants.size + 1
            if (messageEntity.selectedVariantIndex == 0) {
                baseMessage.copy(
                    selectedVariantIndex = 0,
                    variantCount = variantCount,
                )
            } else {
                val selectedVariant =
                    messageVariants.first { it.variantIndex == messageEntity.selectedVariantIndex }
                selectedVariant.applyTo(baseMessage, variantCount)
            }
        }
    }

    private suspend fun hydrateMessages(
        chatId: String,
        messageEntities: List<MessageEntity>,
    ): List<ChatMessage> {
        if (messageEntities.isEmpty()) {
            return emptyList()
        }
        val visibleTimestamps = messageEntities.map { it.timestamp }
        val variants = chatContentDao.getVariantsForMessages(chatId, visibleTimestamps)
        return hydrateMessages(messageEntities, variants)
    }

    private suspend fun loadDisplayHistory(chatHistory: ChatHistory): ChatHistory {
        val messages = loadChatMessages(chatHistory.id)
        return chatHistory.copy(messages = messages)
    }

    private suspend fun loadDisplayHistories(chatHistories: List<ChatHistory>): List<ChatHistory> {
        val completeHistories = mutableListOf<ChatHistory>()
        for (chatHistory in chatHistories) {
            completeHistories.add(loadDisplayHistory(chatHistory))
        }
        return completeHistories
    }

    private fun Cursor.stringOrNull(column: String): String? =
        getColumnIndexOrThrow(column).let { index -> if (isNull(index)) null else getString(index) }

    private fun Cursor.longOrNull(column: String): Long? =
        getColumnIndexOrThrow(column).let { index -> if (isNull(index)) null else getLong(index) }

    private fun Cursor.toSnapshotChatEntity(): ChatEntity =
        ChatEntity(
            id = getString(getColumnIndexOrThrow("id")),
            title = getString(getColumnIndexOrThrow("title")),
            createdAt = getLong(getColumnIndexOrThrow("createdAt")),
            updatedAt = getLong(getColumnIndexOrThrow("updatedAt")),
            inputTokens = getLong(getColumnIndexOrThrow("inputTokens")),
            outputTokens = getLong(getColumnIndexOrThrow("outputTokens")),
            currentWindowSize = getLong(getColumnIndexOrThrow("currentWindowSize")),
            group = stringOrNull("group"),
            displayOrder = getLong(getColumnIndexOrThrow("displayOrder")),
            workspace = stringOrNull("workspace"),
            workspaceEnv = stringOrNull("workspaceEnv"),
            parentChatId = stringOrNull("parentChatId"),
            chatKind =
                stringOrNull("chatKind")
                    ?: if (stringOrNull("parentChatId") == null) {
                        ChatKind.NORMAL.name
                    } else {
                        ChatKind.BRANCH.name
                    },
            characterCardName = stringOrNull("characterCardName"),
            characterGroupId = stringOrNull("characterGroupId"),
            locked = getInt(getColumnIndexOrThrow("locked")) != 0,
            pinned = getInt(getColumnIndexOrThrow("pinned")) != 0,
        )

    private fun Cursor.toSnapshotMessageEntity(): MessageEntity =
        MessageEntity(
            messageId = getLong(getColumnIndexOrThrow("messageId")),
            chatId = getString(getColumnIndexOrThrow("chatId")),
            sender = getString(getColumnIndexOrThrow("sender")),
            content = getString(getColumnIndexOrThrow("content")),
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            orderIndex = getInt(getColumnIndexOrThrow("orderIndex")),
            roleName = getString(getColumnIndexOrThrow("roleName")),
            selectedVariantIndex = getInt(getColumnIndexOrThrow("selectedVariantIndex")),
            provider = getString(getColumnIndexOrThrow("provider")),
            modelName = getString(getColumnIndexOrThrow("modelName")),
            inputTokens = getLong(getColumnIndexOrThrow("inputTokens")),
            outputTokens = getLong(getColumnIndexOrThrow("outputTokens")),
            cachedInputTokens = getLong(getColumnIndexOrThrow("cachedInputTokens")),
            sentAt = getLong(getColumnIndexOrThrow("sentAt")),
            outputDurationMs = getLong(getColumnIndexOrThrow("outputDurationMs")),
            waitDurationMs = getLong(getColumnIndexOrThrow("waitDurationMs")),
            completedAt = getLong(getColumnIndexOrThrow("completedAt")),
            displayMode = getString(getColumnIndexOrThrow("displayMode")),
            isFavorite = getInt(getColumnIndexOrThrow("isFavorite")) != 0,
        )

    private fun Cursor.toSnapshotMessageVariantEntity(): MessageVariantEntity =
        MessageVariantEntity(
            variantId = getLong(getColumnIndexOrThrow("variantId")),
            chatId = getString(getColumnIndexOrThrow("chatId")),
            messageTimestamp = getLong(getColumnIndexOrThrow("messageTimestamp")),
            variantIndex = getInt(getColumnIndexOrThrow("variantIndex")),
            content = getString(getColumnIndexOrThrow("content")),
            roleName = getString(getColumnIndexOrThrow("roleName")),
            provider = getString(getColumnIndexOrThrow("provider")),
            modelName = getString(getColumnIndexOrThrow("modelName")),
            inputTokens = getLong(getColumnIndexOrThrow("inputTokens")),
            outputTokens = getLong(getColumnIndexOrThrow("outputTokens")),
            cachedInputTokens = getLong(getColumnIndexOrThrow("cachedInputTokens")),
            sentAt = getLong(getColumnIndexOrThrow("sentAt")),
            outputDurationMs = getLong(getColumnIndexOrThrow("outputDurationMs")),
            waitDurationMs = getLong(getColumnIndexOrThrow("waitDurationMs")),
            completedAt = getLong(getColumnIndexOrThrow("completedAt")),
        )

    private fun Cursor.toArchivedSubagentRun(): OperitArchivedSubagentRun =
        OperitArchivedSubagentRun(
            id = getString(getColumnIndexOrThrow("id")),
            parentChatId = getString(getColumnIndexOrThrow("parentChatId")),
            childChatId = getString(getColumnIndexOrThrow("childChatId")),
            parentToolCallId = stringOrNull("parentToolCallId"),
            agentProfileId = getString(getColumnIndexOrThrow("agentProfileId")),
            title = getString(getColumnIndexOrThrow("title")),
            status = getString(getColumnIndexOrThrow("status")),
            createdAt = getLong(getColumnIndexOrThrow("createdAt")),
            startedAt = longOrNull("startedAt"),
            completedAt = longOrNull("completedAt"),
            error = stringOrNull("error"),
            agentConfigSnapshot = stringOrNull("agentConfigSnapshot"),
            modelConfigIdSnapshot = stringOrNull("modelConfigIdSnapshot"),
            modelIndexSnapshot =
                getColumnIndexOrThrow("modelIndexSnapshot").let { index ->
                    if (isNull(index)) null else getInt(index)
                },
            modelRoundCount = getInt(getColumnIndexOrThrow("modelRoundCount")),
            externalOwnerId = stringOrNull("externalOwnerId"),
            externalOwnerType = stringOrNull("externalOwnerType"),
            toolInvocationCount = getInt(getColumnIndexOrThrow("toolInvocationCount")),
            archivedAt = longOrNull("archivedAt"),
        )

    private fun buildOperitArchivedChatFromSnapshot(
        snapshot: SQLiteDatabase,
        chatEntity: ChatEntity,
    ): OperitArchivedChat {
        val messageEntities =
            snapshot.rawQuery(
                """SELECT rowid AS exportRowId, messageId, chatId, sender, '' AS content,
                    timestamp, orderIndex, roleName, selectedVariantIndex, provider, modelName,
                    inputTokens, outputTokens, cachedInputTokens, sentAt, outputDurationMs,
                    waitDurationMs, completedAt, displayMode, isFavorite
                    FROM messages WHERE chatId = ? ORDER BY orderIndex""",
                arrayOf(chatEntity.id),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSnapshotMessageEntity().copy(content = readSnapshotContent(
                            snapshot, "messages", cursor.getLong(cursor.getColumnIndexOrThrow("exportRowId"))
                        )))
                    }
                }
            }
        val variantsByTimestamp =
            snapshot.rawQuery(
                """SELECT rowid AS exportRowId, variantId, chatId, messageTimestamp, variantIndex,
                    '' AS content, roleName, provider, modelName, inputTokens, outputTokens,
                    cachedInputTokens, sentAt, outputDurationMs, waitDurationMs, completedAt
                    FROM message_variants WHERE chatId = ? ORDER BY messageTimestamp, variantIndex""",
                arrayOf(chatEntity.id),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSnapshotMessageVariantEntity().copy(content = readSnapshotContent(
                            snapshot, "message_variants", cursor.getLong(cursor.getColumnIndexOrThrow("exportRowId"))
                        )))
                    }
                }
            }.groupBy { it.messageTimestamp }
        val archivedMessages =
            messageEntities.map { messageEntity ->
                val messageVariants = variantsByTimestamp[messageEntity.timestamp].orEmpty()
                OperitArchivedMessage(
                    baseMessage =
                        messageEntity.toChatMessage().copy(
                            variantCount = messageVariants.size + 1,
                        ),
                    variants = messageVariants.map(OperitArchivedMessageVariant::fromEntity),
                )
            }
        val history =
            chatEntity.toChatHistory(emptyList())
        return OperitArchivedChat.fromChatHistory(history, archivedMessages)
    }

    private fun readSnapshotContent(snapshot: SQLiteDatabase, table: String, rowId: Long): String {
        // Read bytes rather than SQLite text slices: preserve embedded NUL and split UTF-8 sequences.
        require(table == "messages" || table == "message_variants")
        val bytes = java.io.ByteArrayOutputStream()
        var offset = 1L
        while (true) {
            val chunk = snapshot.rawQuery(
                "SELECT substr(CAST(content AS BLOB), ?, 65536) FROM $table WHERE rowid = ?",
                arrayOf(offset.toString(), rowId.toString()),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Missing message in export snapshot" }
                cursor.getBlob(0)
            }
            bytes.write(chunk)
            if (chunk.size < 65536) break
            offset += chunk.size
        }
        return bytes.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun createOperitArchiveSqliteSnapshot(snapshotFile: File, selectedChatIds: Set<String>) {
        val sourceFile = context.getDatabasePath("app_database")
        require(sourceFile.isFile) { "Chat database does not exist" }
        SQLiteDatabase.openOrCreateDatabase(snapshotFile, null).use { sqliteDb ->
            sqliteDb.execSQL(
                "ATTACH DATABASE ? AS operit_export_source",
                arrayOf(sourceFile.absolutePath),
            )
            try {
                sqliteDb.beginTransaction()
                try {
                    sqliteDb.execSQL("CREATE TEMP TABLE export_chat_ids (id TEXT NOT NULL PRIMARY KEY)")
                    selectedChatIds.forEach { id ->
                        sqliteDb.execSQL("INSERT INTO export_chat_ids VALUES (?)", arrayOf(id))
                    }
                    // A branch and its subagent runs form a graph: keep the referenced parents
                    // and descendants in the same snapshot so it can be imported independently.
                    sqliteDb.execSQL(
                        """
                        WITH RECURSIVE edges(fromId, toId) AS (
                            SELECT id, parentChatId FROM operit_export_source.chats
                            WHERE parentChatId IS NOT NULL
                            UNION
                            SELECT parentChatId, childChatId FROM operit_export_source.subagent_runs
                        ), expanded(id) AS (
                            SELECT id FROM export_chat_ids
                            UNION
                            SELECT edges.toId FROM edges JOIN expanded ON edges.fromId = expanded.id
                        )
                        INSERT OR IGNORE INTO export_chat_ids SELECT id FROM expanded
                        """.trimIndent(),
                    )
                    sqliteDb.execSQL(
                        """
                        CREATE TABLE chats AS
                        SELECT id, title, createdAt, updatedAt, inputTokens, outputTokens,
                               currentWindowSize, `group`, displayOrder, workspace,
                               workspaceEnv,
                               CASE WHEN chatKind = 'BRANCH' AND parentChatId NOT IN (SELECT id FROM operit_export_source.chats)
                                    THEN NULL ELSE parentChatId END AS parentChatId,
                               CASE WHEN chatKind = 'BRANCH' AND (parentChatId IS NULL OR parentChatId NOT IN (SELECT id FROM operit_export_source.chats))
                                    THEN 'NORMAL' ELSE chatKind END AS chatKind,
                               characterCardName, characterGroupId,
                               locked, pinned
                        FROM operit_export_source.chats
                        WHERE id IN (SELECT id FROM export_chat_ids)
                        """.trimIndent(),
                    )
                    sqliteDb.execSQL(
                        """
                        CREATE TABLE messages AS
                        SELECT messageId, chatId, sender, content, timestamp, orderIndex, roleName,
                               selectedVariantIndex, provider, modelName, inputTokens, outputTokens,
                               cachedInputTokens, sentAt, outputDurationMs, waitDurationMs,
                               completedAt, displayMode, isFavorite
                        FROM operit_export_source.messages
                        WHERE chatId IN (SELECT id FROM chats)
                        """.trimIndent(),
                    )
                    sqliteDb.execSQL(
                        """
                        CREATE TABLE message_variants AS
                        SELECT variantId, chatId, messageTimestamp, variantIndex, content, roleName,
                               provider, modelName, inputTokens, outputTokens, cachedInputTokens,
                               sentAt, outputDurationMs, waitDurationMs, completedAt
                        FROM operit_export_source.message_variants
                        WHERE chatId IN (SELECT id FROM chats)
                        """.trimIndent(),
                    )
                    sqliteDb.execSQL(
                        """
                        CREATE TABLE subagent_runs AS
                        SELECT ${OPERIT_ARCHIVE_SUBAGENT_RUN_SNAPSHOT_COLUMNS.joinToString()}
                        FROM operit_export_source.subagent_runs
                        WHERE parentChatId IN (SELECT id FROM chats)
                          AND childChatId IN (SELECT id FROM chats)
                        """.trimIndent(),
                    )
                    sqliteDb.execSQL(
                        "CREATE INDEX messages_export_order ON messages(chatId, orderIndex)"
                    )
                    sqliteDb.execSQL(
                        "CREATE INDEX variants_export_order ON " +
                            "message_variants(chatId, messageTimestamp, variantIndex)"
                    )
                    sqliteDb.setTransactionSuccessful()
                } finally {
                    sqliteDb.endTransaction()
                }
            } finally {
                sqliteDb.execSQL("DETACH DATABASE operit_export_source")
            }
        }
    }

    private suspend fun exportOperitArchiveJsonStream(
        file: File,
        chatHistories: List<ChatHistory>,
    ) = operitArchiveExportMutex.withLock {
        AppLogger.d(TAG, "开始流式导出 Operit 聊天记录，目标=${file.absolutePath}")
        val snapshotFile =
            File(context.cacheDir, "operit_chat_export_${java.util.UUID.randomUUID()}.db")
        try {
            createOperitArchiveSqliteSnapshot(snapshotFile, chatHistories.map { it.id }.toSet())
            SQLiteDatabase.openDatabase(
                snapshotFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { snapshot ->
                val chatCount =
                    snapshot.rawQuery("SELECT COUNT(*) FROM chats", emptyArray()).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getInt(0)
                    }
                BufferedWriter(
                    OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8),
                ).use { writer ->
                    writer.append("{\n")
                    writer.append("  \"archiveType\": ")
                    writer.append(operitArchiveJson.encodeToString(OperitChatArchive.ARCHIVE_TYPE))
                    writer.append(",\n")
                    writer.append("  \"formatVersion\": ${OperitChatArchive.CURRENT_FORMAT_VERSION},\n")
                    writer.append("  \"exportedAt\": ${System.currentTimeMillis()},\n")
                    writer.append("  \"chats\": [")

                    snapshot.rawQuery(
                        "SELECT * FROM chats ORDER BY pinned DESC, displayOrder ASC",
                        emptyArray(),
                    ).use { cursor ->
                        var index = 0
                        while (cursor.moveToNext()) {
                            val archivedChat =
                                buildOperitArchivedChatFromSnapshot(
                                    snapshot,
                                    cursor.toSnapshotChatEntity(),
                                )
                            if (index == 0) writer.append('\n') else writer.append(",\n")
                            writer.append(operitArchiveJson.encodeToString(archivedChat))
                            index++
                            if (index % 20 == 0 || index == chatCount) {
                                AppLogger.d(
                                    TAG,
                                    "流式导出进度: $index/$chatCount，chatId=${archivedChat.id}，messages=${archivedChat.messages.size}",
                                )
                            }
                        }
                    }

                    if (chatCount > 0) writer.append('\n')
                    writer.append("  ],\n")
                    val archivedSubagentRuns =
                        snapshot.rawQuery(
                            "SELECT * FROM subagent_runs ORDER BY createdAt ASC, id ASC",
                            emptyArray(),
                        ).use { cursor ->
                            buildList {
                                while (cursor.moveToNext()) {
                                    add(cursor.toArchivedSubagentRun())
                                }
                            }
                        }
                    writer.append("  \"subagentRuns\": ")
                    writer.append(operitArchiveJson.encodeToString(archivedSubagentRuns))
                    writer.append('\n')
                    writer.append("}\n")
                }
                AppLogger.d(
                    TAG,
                    "流式导出 Operit 聊天记录完成，共 $chatCount 个会话，目标=${file.absolutePath}",
                )
            }
        } finally {
            listOf(
                snapshotFile,
                File("${snapshotFile.absolutePath}-journal"),
                File("${snapshotFile.absolutePath}-wal"),
                File("${snapshotFile.absolutePath}-shm"),
            ).forEach { it.delete() }
        }
    }

    private suspend fun consumeImportedArchiveChat(
        archivedChat: OperitArchivedChat,
        formatVersion: Int,
        existingIds: MutableSet<String>,
        counters: ImportCounters,
        importedIndex: Int,
    ) {
        if (archivedChat.messages.isEmpty() && formatVersion < 5) {
            counters.skippedCount++
            AppLogger.w(
                TAG,
                "导入跳过空归档会话: index=$importedIndex, chatId=${archivedChat.id}",
            )
            return
        }
        val existed = existingIds.contains(archivedChat.id)
        if (existed) {
            counters.updatedCount++
        } else {
            counters.newCount++
            existingIds.add(archivedChat.id)
        }

        saveArchivedChatInCurrentTransaction(archivedChat)

        if (importedIndex % 20 == 0) {
            AppLogger.d(
                TAG,
                "导入进度: index=$importedIndex, archive chatId=${archivedChat.id}, messages=${archivedChat.messages.size}, new=${counters.newCount}, updated=${counters.updatedCount}, skipped=${counters.skippedCount}",
            )
        }
    }

    private suspend fun buildOperitArchivedChat(chatHistory: ChatHistory): OperitArchivedChat {
        val messageEntities = chatContentDao.getMessagesForChat(chatHistory.id)
        val variantsByTimestamp =
            chatContentDao.getVariantsForChat(chatHistory.id).groupBy { it.messageTimestamp }
        val archivedMessages =
            messageEntities.map { messageEntity ->
                val messageVariants = variantsByTimestamp[messageEntity.timestamp].orEmpty()
                OperitArchivedMessage(
                    baseMessage =
                        messageEntity.toChatMessage().copy(
                            variantCount = messageVariants.size + 1,
                        ),
                    variants = messageVariants.map(OperitArchivedMessageVariant::fromEntity),
                )
            }
        return OperitArchivedChat.fromChatHistory(chatHistory, archivedMessages)
    }

    private suspend fun exportOperitArchiveCsvStream(
        file: File,
        chatHistories: List<ChatHistory>,
    ) {
        AppLogger.d(TAG, "开始导出 Operit CSV 聊天记录，共 ${chatHistories.size} 个会话，目标=${file.absolutePath}")
        BufferedWriter(
            OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8),
        ).use { writer ->
            ChatHistoryCsv.writeHeader(writer)
            chatHistories.forEachIndexed { index, chatHistory ->
                val archivedChat = buildOperitArchivedChat(chatHistory)
                ChatHistoryCsv.writeChat(writer, archivedChat)
                archivedChat.messages.forEachIndexed { messageIndex, archivedMessage ->
                    ChatHistoryCsv.writeMessage(
                        writer = writer,
                        chatId = archivedChat.id,
                        orderIndex = messageIndex,
                        message = archivedMessage.baseMessage,
                    )
                    archivedMessage.variants.forEach { variant ->
                        ChatHistoryCsv.writeVariant(
                            writer = writer,
                            chatId = archivedChat.id,
                            messageTimestamp = archivedMessage.baseMessage.timestamp,
                            variant = variant,
                        )
                    }
                }
                writer.flush()
                if ((index + 1) % 20 == 0 || index == chatHistories.lastIndex) {
                    AppLogger.d(
                        TAG,
                        "CSV 导出进度: ${index + 1}/${chatHistories.size}，chatId=${archivedChat.id}，messages=${archivedChat.messages.size}",
                    )
                }
            }
        }
        AppLogger.d(TAG, "CSV 导出 Operit 聊天记录完成，共 ${chatHistories.size} 个会话，目标=${file.absolutePath}")
    }

    private fun validateV5Archive(archive: ScannedArchive): List<OperitArchivedSubagentRun> {
        val chatsById = archive.chats.associateBy { it.id }

        require(chatsById.size == archive.chats.size && chatsById.keys.none { it.isBlank() }) {
            "Archive contains blank or duplicate chat IDs"
        }
        val checked = mutableSetOf<String>()
        for (chat in archive.chats) {
            val path = mutableSetOf<String>()
            var id: String? = chat.id
            while (id != null && id !in checked) {
                require(path.add(id)) { "Archive chat parent graph contains a cycle" }
                id = chatsById[id]?.parentChatId
            }
            checked.addAll(path)
        }
        archive.chats.forEach { chat ->
            val kind =
                requireNotNull(
                    chat.chatKind?.let { value ->
                        runCatching { ChatKind.valueOf(value) }.getOrNull()
                    }
                ) {
                    "Archive v5 chat ${chat.id} has an invalid chatKind"
                }
            when (kind) {
                ChatKind.NORMAL ->
                    require(chat.parentChatId == null) {
                        "Archive v5 NORMAL chat ${chat.id} cannot have a parent"
                    }
                ChatKind.BRANCH,
                ChatKind.SUBAGENT -> {
                    require(chat.parentChatId != null && chat.parentChatId in chatsById) {
                        "Archive v5 ${kind.name} chat ${chat.id} has an invalid parent"
                    }
                }
            }
        }

        val runIds = archive.subagentRuns.map { it.id }
        require(runIds.all { it.isNotBlank() } && runIds.distinct().size == runIds.size) {
            "Archive v5 contains blank or duplicate task IDs"
        }
        val childIds = archive.subagentRuns.map { it.childChatId }
        require(childIds.distinct().size == childIds.size) {
            "Archive v5 contains multiple runs for one child chat"
        }
        val parentCallIds =
            archive.subagentRuns.mapNotNull { run ->
                run.parentToolCallId?.let { callId -> run.parentChatId to callId }
            }
        require(parentCallIds.distinct().size == parentCallIds.size) {
            "Archive v5 contains duplicate parent tool call IDs within one parent chat"
        }
        archive.subagentRuns.forEach { run ->
            val child =
                requireNotNull(chatsById[run.childChatId]) {
                    "Archive v5 run ${run.id} references a missing child chat"
                }
            require(run.parentChatId in chatsById) {
                "Archive v5 run ${run.id} references a missing parent chat"
            }
            require(
                child.chatKind == ChatKind.SUBAGENT.name &&
                    child.parentChatId == run.parentChatId
            ) {
                "Archive v5 run ${run.id} does not match its child relationship"
            }
            require(
                runCatching { SubagentRunStatus.valueOf(run.status) }.isSuccess
            ) {
                "Archive v5 run ${run.id} has an invalid status"
            }
        }

        val subagentChildIds =
            archive.chats
                .filterTo(linkedSetOf()) { it.chatKind == ChatKind.SUBAGENT.name }
                .mapTo(linkedSetOf()) { it.id }
        require(childIds.toSet() == subagentChildIds) {
            "Archive v5 contains a Subagent child without exactly one run record"
        }
        return archive.subagentRuns
    }

    private suspend fun importV5Archive(
        archive: ScannedArchive,
        archiveFile: File,
        existingIds: MutableSet<String>,
        counters: ImportCounters,
    ): Int {
        val completeRuns = validateV5Archive(archive)
        val localChatIds = chatDao.getAllChatsDirectly().mapTo(hashSetOf()) { it.id }
        val allocatedChatIds =
            hashSetOf<String>().apply {
                addAll(localChatIds)
                addAll(archive.chats.map { it.id })
            }
        val chatIdRemap =
            archive.chats.associate { chat ->
                val resolvedId =
                    if (chat.id !in localChatIds) {
                        chat.id
                    } else {
                        var candidate: String
                        do {
                            candidate = java.util.UUID.randomUUID().toString()
                        } while (!allocatedChatIds.add(candidate))
                        candidate
                    }
                chat.id to resolvedId
            }

        val localRuns = subagentRunDao.getAll()
        val localTaskIds = localRuns.mapTo(hashSetOf()) { it.id }
        val allocatedTaskIds =
            hashSetOf<String>().apply {
                addAll(localTaskIds)
                addAll(completeRuns.map { it.id })
            }
        val taskIdRemap =
            completeRuns.associate { run ->
                val resolvedId =
                    if (run.id !in localTaskIds) {
                        run.id
                    } else {
                        var candidate: String
                        do {
                            candidate = java.util.UUID.randomUUID().toString()
                        } while (!allocatedTaskIds.add(candidate))
                        candidate
                    }
                run.id to resolvedId
            }

        val allocatedParentToolCallIds =
            localRuns.mapNotNullTo(hashSetOf()) { it.parentToolCallId }
        val parentToolCallIdRemap =
            completeRuns
                .mapNotNull { run ->
                    val oldCallId = run.parentToolCallId ?: return@mapNotNull null
                    val resolvedCallId =
                        if (allocatedParentToolCallIds.add(oldCallId)) {
                            oldCallId
                        } else {
                            var candidate: String
                            do {
                                candidate = java.util.UUID.randomUUID().toString()
                            } while (!allocatedParentToolCallIds.add(candidate))
                            candidate
                        }
                    (run.parentChatId to oldCallId) to resolvedCallId
                }
                .toMap()

        fun remapChat(chat: OperitArchivedChat): OperitArchivedChat =
            chat.copy(
                id = chatIdRemap.getValue(chat.id),
                parentChatId = chat.parentChatId?.let(chatIdRemap::getValue),
                chatKind =
                    chat.chatKind
                        ?: if (chat.parentChatId == null) {
                            ChatKind.NORMAL.name
                        } else {
                            ChatKind.BRANCH.name
                        },
                messages =
                    remapArchivedParentToolCallIds(
                        chat = chat,
                        chatIdRemap = chatIdRemap,
                        callIdRemap =
                            parentToolCallIdRemap
                                .filterKeys { (parentChatId, _) -> parentChatId == chat.id }
                                .mapKeys { (key, _) -> key.second },
                        taskIdRemap =
                            completeRuns
                                .asSequence()
                                .filter { run -> run.parentChatId == chat.id }
                                .associate { run -> run.id to taskIdRemap.getValue(run.id) },
                    ).messages,
            )

        val activeStatuses =
            setOf(
                SubagentRunStatus.CREATED.name,
                SubagentRunStatus.QUEUED.name,
                SubagentRunStatus.RUNNING.name,
            )
        val now = System.currentTimeMillis()
        val remappedRuns =
            completeRuns.map { run ->
                val wasActive = run.status in activeStatuses
                run.copy(
                    id = taskIdRemap.getValue(run.id),
                    parentChatId = chatIdRemap.getValue(run.parentChatId),
                    childChatId = chatIdRemap.getValue(run.childChatId),
                    parentToolCallId =
                        run.parentToolCallId?.let { oldCallId ->
                            parentToolCallIdRemap.getValue(run.parentChatId to oldCallId)
                        },
                    status =
                        if (wasActive) {
                            SubagentRunStatus.INTERRUPTED.name
                        } else {
                            run.status
                        },
                    completedAt = if (wasActive) now else run.completedAt,
                    error =
                        if (wasActive) {
                            SubagentInterruption.ARCHIVE_IMPORT
                        } else {
                            run.error
                        },
                )
            }


        database.withTransaction {
            forEachArchivedChat(archiveFile) { chat, _, index ->
                consumeImportedArchiveChat(
                    archivedChat = remapChat(chat),
                    formatVersion = 5,
                    existingIds = existingIds,
                    counters = counters,
                    importedIndex = index,
                )
            }
            remappedRuns.forEach { run -> subagentRunDao.insert(run.toEntity()) }
        }
        return 0
    }

    private fun <T> decodeStreamObject(
        reader: JsonReader,
        description: String,
        decode: (String) -> T,
    ): Pair<T, Set<String>> {
        val element = Streams.parse(reader)
        require(element.isJsonObject) { "$description must be an object" }
        val jsonObject = element.asJsonObject
        return decode(jsonObject.toString()) to jsonObject.keySet().toSet()
    }

    private fun scanOperitArchive(file: File): ScannedArchive {
        var archiveType: String? = null
        var formatVersion: Int? = null
        var chatsPresent = false
        var subagentRunsPresent = false
        val chats = mutableListOf<ArchivedChatMetadata>()
        val subagentRuns = mutableListOf<OperitArchivedSubagentRun>()

        JsonReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            require(reader.peek() == JsonToken.BEGIN_OBJECT) {
                "Operit archive root must be an object"
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "archiveType" -> archiveType = reader.nextString()
                    "formatVersion" -> formatVersion = reader.nextInt()
                    "chats" -> {
                        chatsPresent = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val (chat, fields) =
                                decodeStreamObject(reader, "Archived chat") {
                                    operitArchiveJson.decodeFromString<OperitArchivedChat>(it)
                                }
                            chat.messages.forEach { message ->
                                if (message.variants.isNotEmpty()) {
                                    validateArchivedMessageVariants(
                                        message.baseMessage,
                                        message.variants,
                                    )
                                }
                            }
                            chats +=
                                ArchivedChatMetadata(
                                    id = chat.id,
                                    title = chat.title,
                                    displayOrder = chat.displayOrder,
                                    createdAt =
                                        chat.createdAt
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli(),
                                    hasMessages = chat.messages.isNotEmpty(),
                                    parentChatId = chat.parentChatId,
                                    chatKind = chat.chatKind,
                                    hasChatKindField = "chatKind" in fields,
                                )
                        }
                        reader.endArray()
                    }
                    "subagentRuns" -> {
                        subagentRunsPresent = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            subagentRuns +=
                                decodeStreamObject(reader, "Archived Subagent run") {
                                    operitArchiveJson
                                        .decodeFromString<OperitArchivedSubagentRun>(it)
                                }.first
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) {
                "Operit archive contains trailing JSON data"
            }
        }

        val resolvedArchiveType =
            archiveType ?: throw IllegalArgumentException("Operit archive is missing archiveType")
        val resolvedFormatVersion =
            formatVersion
                ?: throw IllegalArgumentException("Operit archive is missing formatVersion")
        require(chatsPresent) { "Operit archive is missing chats" }
        ChatArchiveImportPolicy.validateHeader(
            resolvedArchiveType,
            resolvedFormatVersion,
        )
        if (resolvedFormatVersion == 5) {
            require(subagentRunsPresent) { "Archive v5 is missing subagentRuns" }
            require(chats.all { it.hasChatKindField }) {
                "Archive v5 contains a chat without chatKind"
            }
        }
        return ScannedArchive(
            archiveType = resolvedArchiveType,
            formatVersion = resolvedFormatVersion,
            chats = chats,
            subagentRuns = subagentRuns,
        )
    }

    private suspend fun forEachArchivedChat(
        file: File,
        consume: suspend (OperitArchivedChat, Set<String>, Int) -> Unit,
    ) {
        var chatsPresent = false
        var importedIndex = 0
        JsonReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() != "chats") {
                    reader.skipValue()
                    continue
                }
                chatsPresent = true
                reader.beginArray()
                while (reader.hasNext()) {
                    importedIndex++
                    val (chat, fields) =
                        decodeStreamObject(reader, "Archived chat") {
                            operitArchiveJson.decodeFromString<OperitArchivedChat>(it)
                        }
                    consume(chat, fields, importedIndex)
                }
                reader.endArray()
            }
            reader.endObject()
        }
        require(chatsPresent) { "Operit archive is missing chats" }
    }

    private suspend fun forEachArchivedSubagentRun(
        file: File,
        consume: suspend (OperitArchivedSubagentRun) -> Unit,
    ) {
        var runsPresent = false
        JsonReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() != "subagentRuns") {
                    reader.skipValue()
                    continue
                }
                runsPresent = true
                reader.beginArray()
                while (reader.hasNext()) {
                    consume(
                        decodeStreamObject(reader, "Archived Subagent run") {
                            operitArchiveJson.decodeFromString<OperitArchivedSubagentRun>(it)
                        }.first
                    )
                }
                reader.endArray()
            }
            reader.endObject()
        }
        require(runsPresent) { "Operit archive is missing subagentRuns" }
    }

    private suspend fun importLegacyChatArray(
        file: File,
        existingIds: MutableSet<String>,
        counters: ImportCounters,
    ) {
        var importedIndex = 0
        JsonReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            reader.beginArray()
            while (reader.hasNext()) {
                importedIndex++
                val (decoded, fields) =
                    decodeStreamObject(reader, "Legacy chat") {
                        operitArchiveJson.decodeFromString<ChatHistory>(it)
                    }
                val history = if ("chatKind" !in fields && decoded.parentChatId != null) {
                    decoded.copy(chatKind = ChatKind.BRANCH.name)
                } else decoded
                if (history.messages.isEmpty()) {
                    counters.skippedCount++
                } else {
                    val existed = existingIds.contains(history.id)
                    if (existed) counters.updatedCount++ else {
                        counters.newCount++
                        existingIds.add(history.id)
                    }
                    saveChatHistoryInternal(
                        history = history,
                    )
                }
                if (importedIndex % 20 == 0) {
                    AppLogger.d(TAG, "旧版聊天数组导入进度: $importedIndex")
                }
            }
            reader.endArray()
        }
    }

    private suspend fun importOperitChatHistoriesStream(
        inputStream: InputStream,
        existingIds: MutableSet<String>,
    ): ChatImportResult {
        val counters = ImportCounters()
        val importFile = File.createTempFile("operit-chat-import-", ".json", context.cacheDir)
        try {
            FileOutputStream(importFile).use { output -> inputStream.copyTo(output) }
            if (importFile.length() == 0L) {
                throw Exception(context.getString(R.string.chat_history_imported_file_empty))
            }
            val rootToken =
                JsonReader(
                    InputStreamReader(importFile.inputStream(), StandardCharsets.UTF_8)
                ).use { reader ->
                    reader.isLenient = true
                    reader.peek()
                }
            when (rootToken) {
                JsonToken.BEGIN_OBJECT -> {
                    val archive = scanOperitArchive(importFile)
                    if (archive.formatVersion == 5) {
                            importV5Archive(
                                archive = archive,
                                archiveFile = importFile,
                                existingIds = existingIds,
                                counters = counters,
                            )
                    } else {
                        database.withTransaction {
                            forEachArchivedChat(importFile) { archivedChat, fields, index ->
                                consumeImportedArchiveChat(
                                    archivedChat = archivedChat,
                                    formatVersion = archive.formatVersion,
                                    existingIds = existingIds,
                                    counters = counters,
                                    importedIndex = index,
                                )
                            }
                        }
                    }
                }
                JsonToken.BEGIN_ARRAY ->
                    importLegacyChatArray(importFile, existingIds, counters)
                JsonToken.END_DOCUMENT ->
                    throw Exception(context.getString(R.string.chat_history_imported_file_empty))
                else ->
                    throw Exception(
                        context.getString(
                            R.string.chat_history_parse_backup_failed,
                            "unexpected json token",
                        )
                    )
            }
        } finally {
            if (!importFile.delete()) {
                importFile.deleteOnExit()
            }
        }

        AppLogger.d(
            TAG,
            "导入完成: new=${counters.newCount}, updated=${counters.updatedCount}, skipped=${counters.skippedCount}",
        )
        return ChatImportResult(
            new = counters.newCount,
            updated = counters.updatedCount,
            skipped = counters.skippedCount,
        )
    }

    init {
        // 确保数据库被初始化
        AppLogger.d(TAG, "ChatHistoryManager初始化，预加载数据库")
        // 使用独立的协程作用域触发数据库初始化
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 预先尝试执行一个简单查询
                val chats = chatDao.getAllChats().first()
                AppLogger.d(TAG, "数据库预加载完成，现有聊天数：${chats.size}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "数据库预加载失败", e)
            }
        }
    }

    // 互斥锁用于同步操作
    private val globalMutex = Mutex()
    private val chatMutexes = ConcurrentHashMap<String, Mutex>()

    private fun chatMutex(chatId: String): Mutex {
        return chatMutexes.getOrPut(chatId) { Mutex() }
    }

    // DataStore键
    private object PreferencesKeys {
        val CURRENT_CHAT_ID = stringPreferencesKey("current_chat_id")
    }

    // 辅助函数：将ChatEntity转换为ChatHistory

    private fun ChatEntity.toChatHistory(): ChatHistory = toChatHistory(emptyList())

    private val historyFlowScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun Flow<List<ChatEntity>>.toHistoryFlow(): Flow<List<ChatHistory>> =
        map { chats -> chats.map { it.toChatHistory() } }

    /** Internal data source used by dispatch, archive, migration and consistency checks. */
    internal val allChatHistoriesUpdatesFlow =
        chatDao
            .getAllChats()
            .toHistoryFlow()

    val allChatHistoriesInternalFlow =
        allChatHistoriesUpdatesFlow
            .stateIn(
                historyFlowScope,
                SharingStarted.Lazily,
                emptyList(),
            )

    /** Default UI data source. Subagent child chats are entered only through task navigation. */
    val chatHistoriesFlow =
        chatDao
            .getVisibleChats()
            .toHistoryFlow()
            .stateIn(
            historyFlowScope,
            SharingStarted.Lazily,
            emptyList()
        )

    suspend fun getChatHistorySnapshot(chatId: String): ChatHistory? =
        withContext(Dispatchers.IO) {
            chatDao.getChatById(chatId)?.toChatHistory()
        }


    suspend fun getChatHistoriesSnapshot(): List<ChatHistory> =
        withContext(Dispatchers.IO) { chatDao.getAllChatsDirectly().map { it.toChatHistory() } }

    suspend fun getTotalChatCount(): Int {
        return withContext(Dispatchers.IO) { chatDao.getTotalChatCount() }
    }

    suspend fun getTotalMessageCount(): Int {
        return withContext(Dispatchers.IO) { messageDao.getTotalMessageCount() }
    }

    suspend fun getUserMessageCount(chatId: String): Int = messageDao.getUserMessageCount(chatId)

    suspend fun getMessageCountsByChatId(): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            try {
                messageDao.getMessageCountsByChatId().associate { it.chatId to it.count }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to get message counts by chatId", e)
                emptyMap()
            }
        }
    }

    // 角色卡聊天统计
    val characterCardStatsFlow: Flow<List<CharacterCardChatStats>> =
        chatDao.getCharacterCardChatStats()
    val characterGroupStatsFlow: Flow<List<CharacterGroupChatStats>> =
        chatDao.getCharacterGroupChatStats()

    /**
     * 根据角色卡过滤聊天历史
     * @param characterCardName 角色卡名称
     * @param isDefault 是否为默认角色卡
     * @return 过滤后的聊天历史Flow
     */
    fun getChatHistoriesByCharacterCard(
        characterCardName: String,
        isDefault: Boolean
    ): Flow<List<ChatHistory>> {
        val sourceFlow = if (isDefault) {
            // 默认角色卡：显示该角色卡名称的对话 + 所有characterCardName为null的对话
            chatDao.getChatsByCharacterCardOrNull(characterCardName)
        } else {
            // 非默认角色卡：只显示该角色卡名称的对话
            chatDao.getChatsByCharacterCard(characterCardName)
        }

        return sourceFlow.map { chatEntities ->
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                chatEntities.map { it.toChatHistory() }
            }
        }
    }

    // 获取当前聊天ID
    private val _currentChatIdFlow: Flow<String?> =
        context.currentChatIdDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences -> preferences[PreferencesKeys.CURRENT_CHAT_ID] }

    // 转换为StateFlow以便共享
    val currentChatIdFlow =
        _currentChatIdFlow.stateIn(
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            SharingStarted.Lazily,
            null
        )

    private fun validateArchivedMessageVariants(
        message: ChatMessage,
        variants: List<OperitArchivedMessageVariant>,
    ) {
        if (variants.isEmpty()) {
            return
        }

        require(message.sender == "ai") {
            "Only AI messages can contain archived variants"
        }
        require(message.selectedVariantIndex >= 0) {
            "Selected variant index must not be negative for message ${message.timestamp}"
        }

        val variantIndices = variants.map { it.variantIndex }
        require(variantIndices.all { it > 0 }) {
            "Variant indices must be positive for message ${message.timestamp}"
        }
        require(variantIndices.distinct().size == variantIndices.size) {
            "Duplicate variant indices found for message ${message.timestamp}"
        }
        require(
            message.selectedVariantIndex == 0 ||
                variantIndices.contains(message.selectedVariantIndex),
        ) {
            "Selected variant ${message.selectedVariantIndex} is missing for message ${message.timestamp}"
        }
    }

    private suspend fun saveChatHistoryInternal(
        history: ChatHistory,
        variantsByTimestamp: Map<Long, List<OperitArchivedMessageVariant>> = emptyMap(),
    ) {
        chatMutex(history.id).withLock {
            database.withTransaction {
                persistChatHistoryInCurrentTransaction(
                    history = history,
                    variantsByTimestamp = variantsByTimestamp,
                )
            }
        }
    }

    private suspend fun persistChatHistoryInCurrentTransaction(
        history: ChatHistory,
        variantsByTimestamp: Map<Long, List<OperitArchivedMessageVariant>> = emptyMap(),
    ) {

        val resolvedHistory = history
        val chatEntity = ChatEntity.fromChatHistory(history)
        if (chatDao.getChatById(chatEntity.id) == null) {
            chatDao.insertChat(chatEntity)
        } else {
            chatDao.updateChat(chatEntity)
        }

        messageDao.deleteAllMessagesForChat(chatEntity.id)
        messageVariantDao.deleteAllVariantsForChat(chatEntity.id)
        val messageEntities =
            resolvedHistory.messages.mapIndexed { index, message ->
                val archivedVariants =
                    variantsByTimestamp[message.timestamp]
                        .orEmpty()
                        .sortedBy { it.variantIndex }
                if (archivedVariants.isNotEmpty()) {
                    validateArchivedMessageVariants(message, archivedVariants)
                }
                MessageEntity.fromChatMessage(
                    chatEntity.id,
                    if (archivedVariants.isEmpty()) {
                        message.copy(selectedVariantIndex = 0, variantCount = 1)
                    } else {
                        message.copy(variantCount = archivedVariants.size + 1)
                    },
                    index,
                )
            }
        messageDao.insertMessages(messageEntities)

        val variantEntities =
            resolvedHistory.messages.flatMap { message ->
                variantsByTimestamp[message.timestamp]
                    .orEmpty()
                    .sortedBy { it.variantIndex }
                    .map { variant ->
                        variant.toEntity(
                            chatId = chatEntity.id,
                            messageTimestamp = message.timestamp,
                        )
                    }
            }
        if (variantEntities.isNotEmpty()) {
            messageVariantDao.insertVariants(variantEntities)
        }
    }

    // 保存聊天历史
    suspend fun saveChatHistory(history: ChatHistory) {
        saveChatHistoryInternal(history)
    }

    private suspend fun saveArchivedChat(
        history: OperitArchivedChat,
    ) {
        val variantsByTimestamp =
            history.messages.associate { archivedMessage ->
                archivedMessage.baseMessage.timestamp to archivedMessage.variants
            }
        saveChatHistoryInternal(
            history.toChatHistory(),
            variantsByTimestamp,
        )
    }

    private suspend fun saveArchivedChatInCurrentTransaction(
        history: OperitArchivedChat,
    ) {
        val variantsByTimestamp =
            history.messages.associate { archivedMessage ->
                archivedMessage.baseMessage.timestamp to archivedMessage.variants
            }
        persistChatHistoryInCurrentTransaction(
            history = history.toChatHistory(),
            variantsByTimestamp = variantsByTimestamp,
        )
    }

    suspend fun updateChatLocked(chatId: String, locked: Boolean) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatLocked(chatId, locked)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat locked state for chat $chatId", e)
                throw e
            }
        }
    }

    /** 更新聊天置顶状态 */
    suspend fun updateChatPinned(chatId: String, pinned: Boolean) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatPinned(chatId, pinned)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat pinned state for chat $chatId", e)
                throw e
            }
        }
    }

    private suspend fun persistMessageLocked(chatId: String, messageToPersist: ChatMessage): ChatMessage {
        val nextOrderIndex = (messageDao.getMaxOrderIndex(chatId) ?: -1) + 1
        val messageEntity =
            MessageEntity.fromChatMessage(
                chatId = chatId,
                message = messageToPersist,
                orderIndex = nextOrderIndex,
            )
        messageDao.insertMessage(messageEntity)

        chatDao.getChatById(chatId)?.let { chat ->
            chatDao.updateChatMetadata(
                chatId = chatId,
                title = chat.title,
                timestamp = System.currentTimeMillis(),
                inputTokens = chat.inputTokens,
                outputTokens = chat.outputTokens,
                currentWindowSize = chat.currentWindowSize
            )
        }

        return messageToPersist
    }

    private suspend fun resolveAnchoredMessageLocked(
        chatId: String,
        message: ChatMessage,
        beforeTimestamp: Long?,
        afterTimestamp: Long?,
    ): ChatMessage? {
        if (beforeTimestamp == null && afterTimestamp == null) {
            val hasAnyMessages = messageDao.existsAnyMessage(chatId)
            return if (hasAnyMessages) {
                AppLogger.w(TAG, "缺少插入锚点，拒绝在非空聊天中插入消息: chatId=$chatId")
                null
            } else {
                message
            }
        }

        val beforeMessage =
            when {
                beforeTimestamp != null -> chatContentDao.getMessageByTimestamp(chatId, beforeTimestamp)
                afterTimestamp != null ->
                    chatContentDao
                        .getMessagesForChatBeforeTimestampExclusiveDesc(
                            chatId,
                            afterTimestamp,
                            1,
                        ).firstOrNull()

                else -> null
            }
        val afterMessage =
            when {
                beforeTimestamp != null && afterTimestamp == null ->
                    chatContentDao
                        .getMessagesForChatAfterTimestampExclusiveAsc(
                            chatId,
                            beforeTimestamp,
                            1,
                        ).firstOrNull()
                afterTimestamp != null -> chatContentDao.getMessageByTimestamp(chatId, afterTimestamp)
                else -> null
            }

        if (beforeTimestamp != null && beforeMessage == null) {
            AppLogger.w(
                TAG,
                "插入消息失败，未找到前置锚点: chatId=$chatId, beforeTimestamp=$beforeTimestamp",
            )
            return null
        }
        if (afterTimestamp != null && afterMessage == null) {
            AppLogger.w(
                TAG,
                "插入消息失败，未找到后置锚点: chatId=$chatId, afterTimestamp=$afterTimestamp",
            )
            return null
        }

        val actualBeforeTimestamp = beforeMessage?.timestamp
        val actualAfterTimestamp = afterMessage?.timestamp

        if (
            actualBeforeTimestamp != null &&
            actualAfterTimestamp != null &&
            actualBeforeTimestamp >= actualAfterTimestamp
        ) {
            AppLogger.w(
                TAG,
                "插入消息失败，前后锚点顺序非法: chatId=$chatId, before=$actualBeforeTimestamp, after=$actualAfterTimestamp",
            )
            return null
        }

        return when {
            actualBeforeTimestamp != null && actualAfterTimestamp != null -> {
                if (actualAfterTimestamp - actualBeforeTimestamp <= 1L) {
                    AppLogger.w(
                        TAG,
                        "插入消息失败，前后锚点时间戳间隔不足: chatId=$chatId, before=$actualBeforeTimestamp, after=$actualAfterTimestamp",
                    )
                    null
                } else {
                    message.copy(
                        timestamp =
                            actualBeforeTimestamp +
                                (actualAfterTimestamp - actualBeforeTimestamp) / 2L,
                    )
                }
            }

            actualBeforeTimestamp != null -> {
                message.copy(timestamp = actualBeforeTimestamp + 1L)
            }

            actualAfterTimestamp != null -> {
                message.copy(timestamp = actualAfterTimestamp - 1L)
            }

            else -> message
        }
    }

    suspend fun addSummaryMessageBetweenSliceNeighbors(
        chatId: String,
        message: ChatMessage,
        beforeTimestamp: Long?,
        afterTimestamp: Long?,
    ): ChatMessage? {
        chatMutex(chatId).withLock {
            try {
                val beforeMessage =
                    when {
                        beforeTimestamp != null -> chatContentDao.getMessageByTimestamp(chatId, beforeTimestamp)
                        afterTimestamp != null ->
                            chatContentDao
                                .getMessagesForChatBeforeTimestampExclusiveDesc(
                                    chatId,
                                    afterTimestamp,
                                    1,
                                ).firstOrNull()
                        else -> null
                    }
                val afterMessage =
                    when {
                        beforeTimestamp != null && afterTimestamp == null ->
                            chatContentDao
                                .getMessagesForChatAfterTimestampExclusiveAsc(
                                    chatId,
                                    beforeTimestamp,
                                    1,
                                ).firstOrNull()
                        afterTimestamp != null -> chatContentDao.getMessageByTimestamp(chatId, afterTimestamp)
                        else -> null
                    }

                if (beforeMessage?.sender == "summary" || afterMessage?.sender == "summary") {
                    AppLogger.w(
                        TAG,
                        "相邻消息已是 summary，取消插入: chatId=$chatId, before=${beforeMessage?.timestamp}, after=${afterMessage?.timestamp}",
                    )
                    return null
                }

                val messageToPersist =
                    resolveAnchoredMessageLocked(
                        chatId = chatId,
                        message = message,
                        beforeTimestamp = beforeTimestamp,
                        afterTimestamp = afterTimestamp,
                    ) ?: return null
                return persistMessageLocked(chatId, messageToPersist)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to add summary message between slice neighbors for chat $chatId", e)
                throw e
            }
        }
    }

    // 添加单条消息，并返回最终持久化的消息
    suspend fun addMessage(chatId: String, message: ChatMessage): ChatMessage {
        chatMutex(chatId).withLock {
            try {
                return persistMessageLocked(chatId, message)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to add message for chat $chatId", e)
                throw e
            }
        }
    }

    /**
     * 批量更新聊天记录的顺序和分组
     * @param updatedHistories 包含更新信息的ChatHistory列表
     */
    suspend fun updateChatOrderAndGroup(updatedHistories: List<ChatHistory>) {
        globalMutex.withLock {
            try {
                val timestamp = System.currentTimeMillis()
                val entitiesToUpdate = updatedHistories.map { history ->
                    // Find the original entity to keep other fields intact
                    val originalEntity = chatDao.getChatById(history.id)
                    originalEntity?.copy(
                        displayOrder = history.displayOrder,
                        group = history.group,
                        updatedAt = timestamp
                    ) ?: ChatEntity.fromChatHistory(history.copy(updatedAt = LocalDateTime.now()))
                }
                chatDao.updateChats(entitiesToUpdate)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat order and group", e)
                throw e
            }
        }
    }

    /**
     * 重命名分组
     * @param oldName 旧的分组名称
     * @param newName 新的分组名称
     * @param characterCardName 角色卡名称，如果为null则更新所有同名分组
     */
    suspend fun updateGroupName(oldName: String, newName: String, characterCardName: String?) {
        globalMutex.withLock {
            try {
                if (characterCardName != null) {
                    // 只更新指定角色卡下的分组（使用 SQL 批量操作）
                    chatDao.updateGroupNameForCharacter(oldName, newName, characterCardName)
                } else {
                    // 更新所有同名分组
                    chatDao.updateGroupName(oldName, newName)
                }
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Failed to rename group from $oldName to $newName (character: $characterCardName)",
                    e
                )
                throw e
            }
        }
    }

    /**
     * 删除分组
     * @param groupName 要删除的分组名称
     * @param deleteChats 是否同时删除分组下的聊天记录
     * @param characterCardName 角色卡名称，如果为null则删除所有同名分组
     */
    suspend fun deleteGroup(groupName: String, deleteChats: Boolean, characterCardName: String?) {
        globalMutex.withLock {
            try {
                fun selectCandidates(chats: List<ChatEntity>): List<ChatEntity> =
                    ChatDeletionGraphPolicy.selectDeletableChats(
                        chats,
                        chats.filter {
                            it.group == groupName &&
                                (characterCardName == null || it.characterCardName == characterCardName)
                        }.mapTo(hashSetOf()) { it.id },
                    )
                val expected = if (deleteChats) selectCandidates(chatDao.getAllChatsDirectly()) else emptyList()
                val expectedIds = expected.mapTo(linkedSetOf()) { it.id }
                // Stop affected tasks before deleting their transcripts and validate the graph again
                // inside the transaction, since a new child may have appeared while stopping them.
                SubagentCoordinator.getInstance(context).withChatDeletionsPrepared(expectedIds) {
                    database.withTransaction {
                        val current = if (deleteChats) selectCandidates(chatDao.getAllChatsDirectly()) else emptyList()
                        check(current.mapTo(hashSetOf()) { it.id } == expectedIds) {
                            "Group deletion candidates changed while preparing deletion"
                        }
                        ChatDeletionGraphPolicy.orderChildFirst(current).forEach { chatDao.deleteChat(it.id) }
                        if (characterCardName != null) {
                            chatDao.removeGroupFromChatsForCharacter(groupName, characterCardName)
                        } else {
                            chatDao.removeGroupFromChats(groupName)
                        }
                    }
                }
                if (currentChatIdFlow.first() in expectedIds) clearCurrentChatId()
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Failed to delete group $groupName (deleteChats: $deleteChats, character: $characterCardName)",
                    e
                )
                throw e
            }
        }
    }

    /**
     * 删除单条消息.
     * @param chatId 聊天ID
     * @param timestamp 消息时间戳
     */
    suspend fun deleteMessage(chatId: String, timestamp: Long) {
        chatMutex(chatId).withLock {
            try {
                AppLogger.d(TAG, "正在从数据库删除消息. ChatId: $chatId, Timestamp: $timestamp")
                messageVariantDao.deleteVariantsForMessage(chatId, timestamp)
                messageDao.deleteMessageByTimestamp(chatId, timestamp)
                AppLogger.d(TAG, "消息从数据库删除成功.")

                // Update chat metadata
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = chat.inputTokens,
                        outputTokens = chat.outputTokens,
                        currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to delete message with timestamp $timestamp for chat $chatId", e)
                throw e
            }
        }
    }

    suspend fun deleteMessageVariant(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ) {
        chatMutex(chatId).withLock {
            try {
                val baseMessage =
                    chatContentDao.getMessageByTimestamp(chatId, messageTimestamp)
                        ?: throw IllegalArgumentException(
                            "Message $messageTimestamp does not exist in chat $chatId",
                        )
                if (baseMessage.sender != "ai") {
                    throw IllegalArgumentException("Only AI messages can delete variants")
                }

                val variants =
                    chatContentDao.getVariantsForMessage(chatId, messageTimestamp)
                        .sortedBy { it.variantIndex }
                if (variants.isEmpty()) {
                    throw IllegalStateException("Message $messageTimestamp has no deletable variants")
                }

                if (variantIndex == 0) {
                    val replacementVariant =
                        variants.firstOrNull()
                            ?: throw IllegalStateException(
                                "Message $messageTimestamp has no replacement variant",
                            )
                    val promotedBaseMessage =
                        baseMessage.copy(
                            content = replacementVariant.content,
                            roleName = replacementVariant.roleName.ifBlank { baseMessage.roleName },
                            selectedVariantIndex = 0,
                            provider = replacementVariant.provider,
                            modelName = replacementVariant.modelName,
                            inputTokens = replacementVariant.inputTokens,
                            outputTokens = replacementVariant.outputTokens,
                            cachedInputTokens = replacementVariant.cachedInputTokens,
                            sentAt = replacementVariant.sentAt,
                            outputDurationMs = replacementVariant.outputDurationMs,
                            waitDurationMs = replacementVariant.waitDurationMs,
                            completedAt = replacementVariant.completedAt,
                        )
                    messageDao.updateMessage(promotedBaseMessage)
                    messageVariantDao.deleteVariant(
                        chatId = chatId,
                        messageTimestamp = messageTimestamp,
                        variantIndex = replacementVariant.variantIndex,
                    )
                    variants
                        .asSequence()
                        .filter { it.variantIndex > replacementVariant.variantIndex }
                        .forEach { variant ->
                            messageVariantDao.updateVariant(
                                variant.copy(variantIndex = variant.variantIndex - 1),
                            )
                        }
                } else {
                    val targetVariant =
                        variants.firstOrNull { it.variantIndex == variantIndex }
                            ?: throw IllegalArgumentException(
                                "Variant $variantIndex does not exist for message $messageTimestamp",
                            )
                    messageVariantDao.deleteVariant(
                        chatId = chatId,
                        messageTimestamp = messageTimestamp,
                        variantIndex = targetVariant.variantIndex,
                    )
                    variants
                        .asSequence()
                        .filter { it.variantIndex > targetVariant.variantIndex }
                        .forEach { variant ->
                            messageVariantDao.updateVariant(
                                variant.copy(variantIndex = variant.variantIndex - 1),
                            )
                        }
                    val newSelectedVariantIndex =
                        when {
                            variants.any { it.variantIndex > targetVariant.variantIndex } -> targetVariant.variantIndex
                            else -> (targetVariant.variantIndex - 1).coerceAtLeast(0)
                        }
                    messageDao.updateSelectedVariantIndex(
                        chatId = chatId,
                        timestamp = messageTimestamp,
                        selectedVariantIndex = newSelectedVariantIndex,
                    )
                }

                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = chat.inputTokens,
                        outputTokens = chat.outputTokens,
                        currentWindowSize = chat.currentWindowSize,
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Failed to delete variant $variantIndex for message $messageTimestamp in chat $chatId",
                    e,
                )
                throw e
            }
        }
    }

    // 更新现有消息
    suspend fun updateMessage(chatId: String, message: ChatMessage) {
        chatMutex(chatId).withLock {
            try {
                // 找到相应的消息实体
                val existingMessage = chatContentDao.getMessageByTimestamp(chatId, message.timestamp)

                if (existingMessage != null) {
                    if (message.selectedVariantIndex > 0) {
                        val existingVariant =
                            chatContentDao.getVariantForMessage(
                                chatId,
                                message.timestamp,
                                message.selectedVariantIndex,
                            ) ?: throw IllegalStateException(
                                "Missing variant ${message.selectedVariantIndex} for message ${message.timestamp}",
                            )
                        messageVariantDao.updateVariant(
                            MessageVariantEntity.fromChatMessage(
                                chatId = chatId,
                                messageTimestamp = message.timestamp,
                                variantIndex = message.selectedVariantIndex,
                                message = message,
                                variantId = existingVariant.variantId,
                            )
                        )
                        messageDao.updateSelectedVariantIndex(
                            chatId,
                            message.timestamp,
                            message.selectedVariantIndex,
                        )
                        chatDao.getChatById(chatId)?.let { chat ->
                            chatDao.updateChatMetadata(
                                chatId = chatId,
                                title = chat.title,
                                timestamp = System.currentTimeMillis(),
                                inputTokens = chat.inputTokens,
                                outputTokens = chat.outputTokens,
                                currentWindowSize = chat.currentWindowSize
                            )
                        }
                        return@withLock
                    }

                    val shouldUpdateChatMetadata =
                        message.contentStream == null ||
                            (existingMessage.content.isEmpty() && message.content.isNotEmpty())
                    val updatedMessageEntity =
                        MessageEntity.fromChatMessage(
                            chatId = chatId,
                            message = message,
                            orderIndex = existingMessage.orderIndex,
                            messageId = existingMessage.messageId
                        )
                    messageDao.updateMessage(updatedMessageEntity)

                    if (shouldUpdateChatMetadata) {
                        // 更新聊天元数据时间戳
                        val chat = chatDao.getChatById(chatId)
                        if (chat != null) {
                            chatDao.updateChatMetadata(
                                chatId = chatId,
                                title = chat.title,
                                timestamp = System.currentTimeMillis(),
                                inputTokens = chat.inputTokens,
                                outputTokens = chat.outputTokens,
                                currentWindowSize = chat.currentWindowSize
                            )
                        }
                    }
                } else {
                    // 如果找不到现有消息，则插入新消息（避免在同一互斥锁下递归调用 addMessage）
                    val nextOrderIndex = (messageDao.getMaxOrderIndex(chatId) ?: -1) + 1
                    val messageEntity = MessageEntity.fromChatMessage(
                        chatId = chatId,
                        message = message,
                        orderIndex = nextOrderIndex,
                    )
                    messageDao.insertMessage(messageEntity)

                    // 更新聊天元数据
                    val chat = chatDao.getChatById(chatId)
                    if (chat != null) {
                        chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = chat.inputTokens,
                            outputTokens = chat.outputTokens,
                            currentWindowSize = chat.currentWindowSize
                        )
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun setMessageFavorite(chatId: String, timestamp: Long, isFavorite: Boolean) {
        chatMutex(chatId).withLock {
            try {
                val existingMessage =
                    chatContentDao.getMessageByTimestamp(chatId, timestamp) ?: return@withLock
                if (existingMessage.isFavorite == isFavorite) {
                    return@withLock
                }
                messageDao.updateMessageFavorite(chatId, timestamp, isFavorite)
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Failed to update favorite state for message $timestamp in chat $chatId",
                    e,
                )
                throw e
            }
        }
    }

    suspend fun addMessageVariant(
        chatId: String,
        messageTimestamp: Long,
        message: ChatMessage,
    ): AddedMessageVariant {
        return chatMutex(chatId).withLock {
            val baseMessage =
                chatContentDao.getMessageByTimestamp(chatId, messageTimestamp)
                    ?: throw IllegalArgumentException("Message $messageTimestamp does not exist in chat $chatId")
            if (baseMessage.sender != "ai") {
                throw IllegalArgumentException("Only AI messages can have regenerated variants")
            }
            val nextVariantIndex =
                chatContentDao.getVariantsForMessage(chatId, messageTimestamp).size + 1
            val variant =
                MessageVariantEntity.fromChatMessage(
                    chatId = chatId,
                    messageTimestamp = messageTimestamp,
                    variantIndex = nextVariantIndex,
                    message = message.copy(selectedVariantIndex = nextVariantIndex, variantCount = nextVariantIndex),
                )
            messageVariantDao.insertVariant(variant)
            val persistedMessage = variant.applyTo(baseMessage.toChatMessage(), nextVariantIndex)
            messageDao.updateSelectedVariantIndex(chatId, messageTimestamp, nextVariantIndex)
            chatDao.getChatById(chatId)?.let { chat ->
                chatDao.updateChatMetadata(
                    chatId = chatId,
                    title = chat.title,
                    timestamp = System.currentTimeMillis(),
                    inputTokens = chat.inputTokens,
                    outputTokens = chat.outputTokens,
                    currentWindowSize = chat.currentWindowSize
                )
            }
            AddedMessageVariant(
                selectedVariantIndex = nextVariantIndex,
                message = persistedMessage,
            )
        }
    }

    suspend fun selectMessageVariant(
        chatId: String,
        messageTimestamp: Long,
        selectedVariantIndex: Int,
    ) {
        chatMutex(chatId).withLock {
            chatContentDao.getMessageByTimestamp(chatId, messageTimestamp)
                ?: throw IllegalArgumentException("Message $messageTimestamp does not exist in chat $chatId")
            if (selectedVariantIndex > 0) {
                chatContentDao.getVariantForMessage(chatId, messageTimestamp, selectedVariantIndex)
                    ?: throw IllegalArgumentException(
                        "Variant $selectedVariantIndex does not exist for message $messageTimestamp",
                    )
            }
            messageDao.updateSelectedVariantIndex(chatId, messageTimestamp, selectedVariantIndex)
        }
    }

    /**
     * 从数据库中删除指定时间戳之后的所有消息。 这需要您在MessageDao中添加相应的@Query。
     *
     * 示例:
     * ```
     * @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp >= :timestamp")
     * suspend fun deleteMessagesFrom(chatId: String, timestamp: Long)
     * ```
     */
    suspend fun deleteMessagesFrom(chatId: String, timestamp: Long) {
        chatMutex(chatId).withLock {
            try {
                AppLogger.d(TAG, "正在从数据库删除消息. ChatId: $chatId, Timestamp >=: $timestamp")
                messageVariantDao.deleteVariantsFrom(chatId, timestamp)
                messageDao.deleteMessagesFrom(chatId, timestamp)
                AppLogger.d(TAG, "后续消息从数据库删除成功.")
                // 更新聊天元数据时间戳
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = chat.inputTokens,
                        outputTokens = chat.outputTokens,
                        currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "从 $timestamp 开始为聊天 $chatId 删除消息失败", e)
                throw e
            }
        }
    }

    /**
     * 清除一个聊天中的所有消息，但保留聊天本身。
     *
     * 这需要您在MessageDao中添加相应的@Query。
     * ```
     * @Query("DELETE FROM messages WHERE chatId = :chatId")
     * suspend fun deleteAllMessagesForChat(chatId: String)
     * ```
     */
    suspend fun clearChatMessages(chatId: String) {
        chatMutex(chatId).withLock {
            try {
                messageVariantDao.deleteAllVariantsForChat(chatId)
                messageDao.deleteAllMessagesForChat(chatId)
                // 更新聊天元数据
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = 0,
                        outputTokens = 0,
                        currentWindowSize = 0
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "为聊天 $chatId 清除消息失败", e)
                throw e
            }
        }
    }

    // 更新聊天标题
    suspend fun updateChatTitle(chatId: String, title: String) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatTitle(chatId, title)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat title for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天绑定的角色卡
    suspend fun updateChatCharacterCardName(chatId: String, characterCardName: String?) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatCharacterCardName(chatId, characterCardName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat character card for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天的token计数
    suspend fun updateChatTokenCounts(
        chatId: String,
        inputTokens: Long,
        outputTokens: Long,
        currentWindowSize: Long
    ) {
        chatMutex(chatId).withLock {
            try {
                val chat = chatDao.getChatById(chatId)
                if (chat != null) {
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        currentWindowSize = currentWindowSize
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 设置当前聊天ID
    suspend fun setCurrentChatId(chatId: String) {
        context.currentChatIdDataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_CHAT_ID] = chatId
        }
    }

    // 清除当前聊天ID
    suspend fun clearCurrentChatId() {
        context.currentChatIdDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CURRENT_CHAT_ID)
        }
    }

    // 检查聊天是否存在
    suspend fun chatExists(chatId: String): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                chatDao.getChatById(chatId) != null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to check chat existence for chat $chatId", e)
                false
            }
        }
    }

    suspend fun canDeleteChatHistory(chatId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val chat = chatDao.getChatById(chatId)
                chat != null && chat.locked != true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to check whether chat $chatId can be deleted", e)
                false
            }
        }
    }

    suspend fun getSubagentChildCount(chatId: String): Int =
        withContext(Dispatchers.IO) {
            chatDao.getChatAndSubagentDescendants(chatId).count { it.id != chatId }
        }

    // 删除聊天历史
    suspend fun deleteChatHistory(chatId: String): Boolean {
        chatMutex(chatId).withLock {
            try {
                val chat = chatDao.getChatById(chatId)
                if (chat?.locked == true) {
                    AppLogger.w(TAG, "Chat $chatId is locked; skip deletion")
                    return false
                }
                if (chat == null) {
                    return false
                }
                val tree = chatDao.getChatAndSubagentDescendants(chatId)
                if (tree.any { it.locked }) return false
                val deleted = SubagentCoordinator.getInstance(context)
                    .withChatDeletionsPrepared(tree.map { it.id }) {
                        subagentRunRepository.deleteParentChatAndChildren(chatId)
                    }
                if (!deleted) return false

                // 如果删除的是当前聊天，清除当前聊天ID
                val currentChatId = currentChatIdFlow.first()
                if (currentChatId != null && chatDao.getChatById(currentChatId) == null) {
                    context.currentChatIdDataStore.edit { preferences ->
                        preferences.remove(PreferencesKeys.CURRENT_CHAT_ID)
                    }
                }
                return true
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 创建新对话
    suspend fun createNewChat(
        group: String? = null,
        inheritGroupFromChatId: String? = null,
        characterCardName: String? = null,
        characterGroupId: String? = null,
        setAsCurrentChat: Boolean = true
    ): ChatHistory {
        val dateTime = LocalDateTime.now()
        val formattedTime =
            "${dateTime.hour}:${
                dateTime.minute.toString().padStart(2, '0')
            }:${dateTime.second.toString().padStart(2, '0')}"

        val localizedContext = LocaleUtils.getLocalizedContext(context)

        // 确定新对话的分组
        val finalGroup = when {
            // 如果显式指定了分组，使用指定的分组
            group != null -> group
            // 如果要继承分组，尝试从指定的对话获取分组
            inheritGroupFromChatId != null -> {
                chatDao.getChatById(inheritGroupFromChatId)?.group
            }
            // 默认为空分组（不分组）
            else -> null
        }

        val newHistory =
            ChatHistory(
                title = "${localizedContext.getString(R.string.new_conversation)} $formattedTime",
                messages = listOf<ChatMessage>(),
                inputTokens = 0,
                outputTokens = 0,
                group = finalGroup,
                characterCardName = characterCardName, // 使用传入的角色卡名称，如果为null则不绑定
                characterGroupId = characterGroupId // 绑定群组角色卡ID（可选）
            )

        // 保存新聊天
        val chatEntity = ChatEntity.fromChatHistory(newHistory)
        chatDao.insertChat(chatEntity)

        // 设置为当前聊天
        if (setAsCurrentChat) {
            setCurrentChatId(newHistory.id)
        }

        return newHistory
    }

    suspend fun createSubagentSliceChat(
        parentChatId: String,
        title: String,
    ): ChatHistory =
        withContext(Dispatchers.IO) {
            val parent =
                requireNotNull(chatDao.getChatById(parentChatId)) {
                    "Parent chat does not exist: $parentChatId"
                }
            val now = System.currentTimeMillis()
            val child =
                ChatEntity(
                    title = title,
                    createdAt = now,
                    updatedAt = now,
                    group = parent.group,
                    displayOrder = -now,
                    workspace = parent.workspace,
                    workspaceEnv = parent.workspaceEnv,
                    parentChatId = parent.id,
                    chatKind = ChatKind.SUBAGENT.name,
                    characterCardName = parent.characterCardName,
                )
            chatDao.insertChat(child)
            child.toChatHistory(emptyList())
        }

    /** 更新聊天工作区 */
    suspend fun updateChatWorkspace(chatId: String, workspace: String?, workspaceEnv: String?) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatWorkspace(chatId, workspace, workspaceEnv)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat workspace for chat $chatId", e)
                throw e
            }
        }
    }

    suspend fun renameManagedWorkspace(
        chatId: String,
        newWorkspaceName: String
    ): WorkspaceRenameResult {
        chatMutex(chatId).withLock {
            val trimmedName = newWorkspaceName.trim()
            if (trimmedName.isEmpty()) {
                throw IllegalArgumentException(context.getString(R.string.workspace_rename_name_empty))
            }
            if (
                trimmedName == "." ||
                trimmedName == ".." ||
                trimmedName.contains('/') ||
                trimmedName.contains('\\')
            ) {
                throw IllegalArgumentException(context.getString(R.string.workspace_rename_name_invalid))
            }

            val chat = chatDao.getChatById(chatId)
                ?: throw IllegalStateException(context.getString(R.string.workspace_rename_chat_missing))
            val workspacePath = chat.workspace
                ?: throw IllegalStateException(context.getString(R.string.chat_not_bound_to_workspace))
            if (!chat.workspaceEnv.isNullOrBlank()) {
                throw IllegalStateException(
                    context.getString(R.string.workspace_rename_only_managed_supported)
                )
            }

            val workspaceRoot = File(context.filesDir, "workspace").apply { mkdirs() }.canonicalFile
            val sourceDir = File(workspacePath).canonicalFile
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                throw IllegalStateException(context.getString(R.string.workspace_directory_invalid))
            }
            if (sourceDir.parentFile?.canonicalFile != workspaceRoot) {
                throw IllegalStateException(
                    context.getString(R.string.workspace_rename_only_managed_supported)
                )
            }

            val targetDir = File(workspaceRoot, trimmedName).canonicalFile
            if (targetDir.parentFile?.canonicalFile != workspaceRoot) {
                throw IllegalArgumentException(context.getString(R.string.workspace_rename_name_invalid))
            }
            if (targetDir != sourceDir && targetDir.exists()) {
                throw IllegalArgumentException(context.getString(R.string.workspace_rename_name_exists))
            }

            if (targetDir != sourceDir && !sourceDir.renameTo(targetDir)) {
                throw IOException(context.getString(R.string.workspace_rename_failed))
            }

            chatDao.updateChatTitleAndWorkspace(
                chatId = chatId,
                title = trimmedName,
                workspace = targetDir.absolutePath,
                workspaceEnv = chat.workspaceEnv
            )

            return WorkspaceRenameResult(
                workspacePath = targetDir.absolutePath,
                workspaceEnv = chat.workspaceEnv,
                workspaceName = trimmedName
            )
        }
    }

    // 更新聊天分组
    suspend fun updateChatGroup(chatId: String, group: String?) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatGroup(chatId, group)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat group for chat $chatId", e)
                throw e
            }
        }
    }

    suspend fun getChatTitle(chatId: String): String? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                chatDao.getChatById(chatId)?.title
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to get chat title for chat $chatId", e)
                null
            }
        }
    }

    // 直接加载聊天消息
    suspend fun latestSubagentAnswer(chatId: String): String? = withContext(Dispatchers.IO) {
        chatContentDao.latestAnswerContent(chatId)
            ?.let(com.ai.assistance.operit.core.agent.SubagentResultExtractor::forTranscript)
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun loadChatMessages(chatId: String): List<ChatMessage> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // AppLogger.d(TAG, "直接从数据库加载聊天 $chatId 的消息")
                val messageEntities = chatContentDao.getMessagesForChat(chatId)
                // AppLogger.d(TAG, "聊天 $chatId 共加载 ${messages.size} 条消息")
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadChatMessages(
        chatId: String,
        order: String? = null,
        limit: Int? = null
    ): List<ChatMessage> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val normalizedOrder = order?.trim()?.lowercase()
                val effectiveLimit = limit?.coerceAtLeast(1)

                val messageEntities = when (normalizedOrder) {
                    "desc" -> {
                        if (effectiveLimit != null) {
                            chatContentDao.getMessagesForChatDesc(chatId, effectiveLimit)
                        } else {
                            chatContentDao.getMessagesForChat(chatId).asReversed()
                        }
                    }

                    else -> {
                        if (effectiveLimit != null) {
                            chatContentDao.getMessagesForChatAsc(chatId, effectiveLimit)
                        } else {
                            chatContentDao.getMessagesForChat(chatId)
                        }
                    }
                }

                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadChatMessagesRange(
        chatId: String,
        order: String? = null,
        start: Int,
        end: Int,
    ): List<ChatMessage> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val normalizedOrder = order?.trim()?.lowercase()
                val limit = end - start + 1

                val messageEntities = when (normalizedOrder) {
                    "desc" -> chatContentDao.getMessagesForChatDescRange(chatId, start, limit)
                    else -> chatContentDao.getMessagesForChatAscRange(chatId, start, limit)
                }

                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "按区间加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    /** 搜索包含特定关键词的聊天ID列表 */
    suspend fun searchChatIdsByContent(query: String): Set<String> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                if (query.isBlank()) {
                    return@withContext emptySet()
                }
                val escapedQuery =
                    query
                        .trim()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")

                val chatIds = messageDao.searchChatIdsByContent(escapedQuery)
                chatIds.toSet()
            } catch (e: Exception) {
                AppLogger.e(TAG, "搜索聊天内容失败: $query", e)
                emptySet()
            }
        }
    }

    /**
     * 创建对话分支
     * @param parentChatId 父对话ID
     * @param upToMessageTimestamp 复制消息到指定时间戳（包含该时间戳的消息）
     * @return 新创建的分支对话
     */
    suspend fun createBranch(
        parentChatId: String,
        upToMessageTimestamp: Long? = null
    ): ChatHistory {
        return globalMutex.withLock {
            try {
                // 获取父对话
                val parentChat = chatDao.getChatById(parentChatId)
                    ?: throw IllegalArgumentException(context.getString(R.string.chat_history_parent_not_exist, parentChatId))

                val branchEntity =
                    ChatEntity(
                        title = parentChat.title,
                        inputTokens = parentChat.inputTokens,
                        outputTokens = parentChat.outputTokens,
                        currentWindowSize = parentChat.currentWindowSize,
                        group = parentChat.group,
                        workspace = parentChat.workspace,
                        workspaceEnv = parentChat.workspaceEnv,
                        parentChatId = parentChatId,
                        chatKind = ChatKind.BRANCH.name,
                        characterCardName = parentChat.characterCardName,
                        characterGroupId = parentChat.characterGroupId,
                        locked = false,
                        pinned = false,
                    )

                val copyResult =
                    chatBranchRepository.copyBranch(
                        sourceChatId = parentChatId,
                        branch = branchEntity,
                        upToTimestampInclusive = upToMessageTimestamp,
                    )

                val branchHistory =
                    copyResult.branch
                        .toChatHistory(emptyList())

                // 设置为当前聊天
                setCurrentChatId(branchHistory.id)

                AppLogger.d(
                    TAG,
                    "创建分支对话: ${branchHistory.id}, 父对话: $parentChatId, " +
                        "消息数: ${copyResult.copiedMessageCount}, " +
                        "Subagent数: ${copyResult.copiedSubagentCount}"
                )
                branchHistory
            } catch (e: Exception) {
                AppLogger.e(TAG, "创建分支对话失败", e)
                throw e
            }
        }
    }

    /**
     * 获取指定对话的所有分支
     * @param parentChatId 父对话ID
     * @return 分支对话列表
     */
    suspend fun getBranches(parentChatId: String): List<ChatHistory> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val branchEntities = chatDao.getBranchesByParentId(parentChatId)
                branchEntities.map { entity ->
                    val createdAt = Instant.ofEpochMilli(entity.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                    val updatedAt = Instant.ofEpochMilli(entity.updatedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()

                    ChatHistory(
                        id = entity.id,
                        title = entity.title,
                        messages = emptyList(), // 不加载消息以提高性能
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        inputTokens = entity.inputTokens,
                        outputTokens = entity.outputTokens,
                        currentWindowSize = entity.currentWindowSize,
                        group = entity.group,
                        displayOrder = entity.displayOrder,
                        workspace = entity.workspace,
                        parentChatId = entity.parentChatId,
                        characterCardName = entity.characterCardName,
                        characterGroupId = entity.characterGroupId,
                        locked = entity.locked,
                        pinned = entity.pinned
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取分支对话失败: $parentChatId", e)
                emptyList()
            }
        }
    }

    /**
     * 获取指定对话的所有分支（Flow版本）
     * @param parentChatId 父对话ID
     * @return 分支对话Flow
     */
    fun getBranchesFlow(parentChatId: String): Flow<List<ChatHistory>> {
        return chatDao.getBranchesByParentIdFlow(parentChatId).map { branchEntities ->
            branchEntities.map { entity ->
                val createdAt = Instant.ofEpochMilli(entity.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                val updatedAt = Instant.ofEpochMilli(entity.updatedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()

                ChatHistory(
                    id = entity.id,
                    title = entity.title,
                    messages = emptyList(), // 不加载消息以提高性能
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    inputTokens = entity.inputTokens,
                    outputTokens = entity.outputTokens,
                    currentWindowSize = entity.currentWindowSize,
                    group = entity.group,
                    displayOrder = entity.displayOrder,
                    workspace = entity.workspace,
                    parentChatId = entity.parentChatId,
                    characterCardName = entity.characterCardName,
                    characterGroupId = entity.characterGroupId,
                    locked = entity.locked,
                    pinned = entity.pinned
                )
            }
        }
    }

    /**
     * 导出指定聊天记录到「下载/Operit」目录。
     * @param selectedChatIds 要导出的聊天记录 ID，不能为空
     * @param format 导出格式
     * @param onProgress 超长文本导出进度回调
     * @return 导出结果，失败时返回null
     */
    suspend fun exportChatHistoriesToDownloads(
        selectedChatIds: Set<String>,
        format: ExportFormat,
        onProgress: ((ChatExportProgress) -> Unit)?,
    ): ChatExportResult? =
        withContext(Dispatchers.IO) {
            var pendingExportFile: File? = null
            try {
                require(selectedChatIds.isNotEmpty()) { "At least one chat must be selected for export." }
                val chatHistoriesBasic = chatHistoriesFlow.first().filter { it.id in selectedChatIds }
                if (chatHistoriesBasic.isEmpty()) {
                    AppLogger.w(TAG, "所选聊天记录已不存在，取消导出")
                    return@withContext null
                }
                val exportedChatIds = chatHistoriesBasic.map { it.id }.toSet()
                val isTextExportFormat = format == ExportFormat.HTML || format == ExportFormat.TXT
                val textCharacterCountsByChat = if (isTextExportFormat) {
                    chatContentDao.getSelectedContentCharacterCountsByChat().filter {
                        it.chatId in exportedChatIds
                    }
                } else {
                    emptyList()
                }
                val totalTextCharacters = textCharacterCountsByChat.sumOf { it.contentCharacterCount }
                val useStreamingTextExport =
                    isTextExportFormat &&
                        totalTextCharacters >= TEXT_EXPORT_STREAMING_THRESHOLD_CHARACTER_COUNT
                val totalMessageCount = if (useStreamingTextExport) {
                    messageDao.getMessageCountsByChatId()
                        .filter { it.chatId in exportedChatIds }
                        .sumOf { it.count }
                } else {
                    0
                }

                if (useStreamingTextExport) {
                    onProgress?.invoke(
                        ChatExportProgress(
                            isLongText = true,
                            progress = 0f,
                            processedCharacters = 0L,
                            totalCharacters = totalTextCharacters,
                        )
                    )
                }

                val exportDir = OperitBackupDirs.chatDir()

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())

                val exportFile = when (format) {
                    ExportFormat.MARKDOWN -> {
                        val completeHistories = loadDisplayHistories(chatHistoriesBasic)
                        val zipFile = File(exportDir, "chat_backup_$timestamp.zip")
                        pendingExportFile = zipFile
                        val usedNames = HashSet<String>()
                        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                            for (history in completeHistories) {
                                val content = MarkdownExporter.exportSingle(context, history)
                                // 处理文件名中的非法字符
                                var safeTitle = history.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                // 避免文件名过长
                                if (safeTitle.length > 50) {
                                    safeTitle = safeTitle.substring(0, 50)
                                }
                                safeTitle = safeTitle.trim()
                                
                                // 确保文件名唯一
                                var baseName = "$safeTitle.md"
                                var counter = 1
                                while (usedNames.contains(baseName)) {
                                    baseName = "$safeTitle ($counter).md"
                                    counter++
                                }
                                usedNames.add(baseName)

                                zos.putNextEntry(ZipEntry(baseName))
                                zos.write(content.toByteArray())
                                zos.closeEntry()
                            }
                        }
                        zipFile
                    }

                    ExportFormat.JSON -> {
                        val file = File(exportDir, "chat_backup_$timestamp.json")
                        pendingExportFile = file
                        exportOperitArchiveJsonStream(file, chatHistoriesBasic)
                        file
                    }

                    ExportFormat.HTML -> {
                        val file = File(exportDir, "chat_backup_$timestamp.html")
                        pendingExportFile = file
                        if (useStreamingTextExport) {
                            exportLongTextHistories(
                                file = file,
                                format = format,
                                chatHistories = chatHistoriesBasic,
                                totalMessageCount = totalMessageCount,
                                totalTextCharacters = totalTextCharacters,
                                onProgress = onProgress,
                            )
                        } else {
                            val completeHistories = loadDisplayHistories(chatHistoriesBasic)
                            file.writeText(HtmlExporter.exportMultiple(context, completeHistories))
                        }
                        file
                    }

                    ExportFormat.TXT -> {
                        val file = File(exportDir, "chat_backup_$timestamp.txt")
                        pendingExportFile = file
                        if (useStreamingTextExport) {
                            exportLongTextHistories(
                                file = file,
                                format = format,
                                chatHistories = chatHistoriesBasic,
                                totalMessageCount = totalMessageCount,
                                totalTextCharacters = totalTextCharacters,
                                onProgress = onProgress,
                            )
                        } else {
                            val completeHistories = loadDisplayHistories(chatHistoriesBasic)
                            file.writeText(TextExporter.exportMultiple(context, completeHistories))
                        }
                        file
                    }

                    ExportFormat.CSV -> {
                        val file = File(exportDir, "chat_backup_$timestamp.csv")
                        pendingExportFile = file
                        exportOperitArchiveCsvStream(file, chatHistoriesBasic)
                        file
                    }
                }

                pendingExportFile = null
                ChatExportResult(
                    filePath = exportFile.absolutePath,
                    chatCount = chatHistoriesBasic.size,
                )
            } catch (e: Exception) {
                pendingExportFile?.let { incompleteFile ->
                    if (incompleteFile.exists() && !incompleteFile.delete()) {
                        AppLogger.w(TAG, "无法删除未完成的聊天导出文件: ${incompleteFile.absolutePath}")
                    }
                }
                AppLogger.e(TAG, "导出聊天记录失败", e)
                null
            }
        }

    private suspend fun exportLongTextHistories(
        file: File,
        format: ExportFormat,
        chatHistories: List<ChatHistory>,
        totalMessageCount: Int,
        totalTextCharacters: Long,
        onProgress: ((ChatExportProgress) -> Unit)?,
    ) {
        check(format == ExportFormat.HTML || format == ExportFormat.TXT)

        var processedCharacters = 0L
        var lastReportedCharacters = 0L

        fun reportProgress(force: Boolean = false) {
            val clampedProcessedCharacters = processedCharacters.coerceAtMost(totalTextCharacters)
            val shouldReport =
                force ||
                    clampedProcessedCharacters - lastReportedCharacters >=
                        TEXT_EXPORT_PROGRESS_UPDATE_CHARACTER_COUNT
            if (!shouldReport) {
                return
            }

            lastReportedCharacters = clampedProcessedCharacters
            val progress = if (totalTextCharacters == 0L) {
                1f
            } else {
                clampedProcessedCharacters.toFloat() / totalTextCharacters.toFloat()
            }
            onProgress?.invoke(
                ChatExportProgress(
                    isLongText = true,
                    progress = progress.coerceIn(0f, 1f),
                    processedCharacters = clampedProcessedCharacters,
                    totalCharacters = totalTextCharacters,
                )
            )
        }

        BufferedWriter(
            OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8),
            TEXT_EXPORT_WRITER_BUFFER_SIZE,
        ).use { writer ->
            when (format) {
                ExportFormat.HTML ->
                    HtmlExporter.writeMultipleHeader(
                        context = context,
                        chatHistories = chatHistories,
                        totalMessageCount = totalMessageCount,
                        writer = writer,
                    )

                ExportFormat.TXT ->
                    TextExporter.writeMultipleHeader(
                        context = context,
                        chatHistories = chatHistories,
                        totalMessageCount = totalMessageCount,
                        writer = writer,
                    )

                ExportFormat.JSON, ExportFormat.MARKDOWN, ExportFormat.CSV -> check(false)
            }

            chatHistories.forEachIndexed { index, chatHistoryBasic ->
                when (format) {
                    ExportFormat.HTML -> HtmlExporter.writeConversationSeparator(writer, index)
                    ExportFormat.TXT -> TextExporter.writeConversationSeparator(writer, index)
                    ExportFormat.JSON, ExportFormat.MARKDOWN, ExportFormat.CSV -> check(false)
                }

                val completeHistory = loadDisplayHistory(chatHistoryBasic)
                val onContentCharactersWritten: (Long) -> Unit = { characterCount ->
                    processedCharacters += characterCount
                    reportProgress()
                }
                when (format) {
                    ExportFormat.HTML ->
                        HtmlExporter.writeConversationToWriter(
                            context = context,
                            writer = writer,
                            chatHistory = completeHistory,
                            onContentCharactersWritten = onContentCharactersWritten,
                        )

                    ExportFormat.TXT ->
                        TextExporter.writeSingleToWriter(
                            context = context,
                            chatHistory = completeHistory,
                            writer = writer,
                            onContentCharactersWritten = onContentCharactersWritten,
                        )

                    ExportFormat.JSON, ExportFormat.MARKDOWN, ExportFormat.CSV -> check(false)
                }
                writer.flush()
                reportProgress(force = true)
                yield()
            }

            when (format) {
                ExportFormat.HTML -> HtmlExporter.writeMultipleFooter(context, writer)
                ExportFormat.TXT -> TextExporter.writeMultipleFooter(context, writer)
                ExportFormat.JSON, ExportFormat.MARKDOWN, ExportFormat.CSV -> check(false)
            }
        }

        processedCharacters = totalTextCharacters
        reportProgress(force = true)
    }

    /**
     * 从指定URI导入聊天记录（指定格式）
     * @param uri 备份文件URI
     * @param format 指定的格式
     * @return 导入结果统计
     */
    suspend fun importChatHistoriesFromUri(uri: Uri, format: ChatFormat): ChatImportResult =
        withContext(Dispatchers.IO) {
            try {
                val chatHistories = mutableListOf<ChatHistory>()
                var isZipProcessed = false

                // 如果是 Markdown 格式，尝试作为 Zip 处理
                if (format == ChatFormat.MARKDOWN) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { fis ->
                            // 尝试作为 Zip 读取
                            // 注意：ZipInputStream 可能会消耗流，如果不是 Zip，后续重读需要重新 openInputStream
                            ZipInputStream(fis).use { zipStream ->
                                var entry = zipStream.nextEntry
                                if (entry != null) {
                                    // 确实是 Zip 文件
                                    do {
                                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".md")) {
                                            val buffer = ByteArrayOutputStream()
                                            val data = ByteArray(4096)
                                            var count: Int
                                            while (zipStream.read(data).also { count = it } != -1) {
                                                buffer.write(data, 0, count)
                                            }
                                            val content = buffer.toString("UTF-8")
                                            if (content.isNotBlank()) {
                                                chatHistories.addAll(convertToOperitFormat(content, ChatFormat.MARKDOWN))
                                            }
                                        }
                                        zipStream.closeEntry()
                                        entry = zipStream.nextEntry
                                    } while (entry != null)
                                    isZipProcessed = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "尝试解析 Zip 失败，将尝试作为普通文件读取: ${e.message}")
                    }
                }

                if (!isZipProcessed) {
                    AppLogger.d(TAG, "使用指定格式导入: $format")
                    if (format == ChatFormat.OPERIT) {
                        val existingIds =
                            allChatHistoriesInternalFlow.first().map { it.id }.toMutableSet()
                        val inputStream =
                            context.contentResolver.openInputStream(uri)
                                ?: return@withContext ChatImportResult(0, 0, 0)
                        inputStream.use { stream ->
                            AppLogger.d(TAG, "开始流式导入 Operit JSON: uri=$uri")
                            return@withContext importOperitChatHistoriesStream(stream, existingIds)
                        }
                    } else {
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: return@withContext ChatImportResult(0, 0, 0)
                        val content = inputStream.bufferedReader().use { it.readText() }

                        if (content.isBlank()) {
                            throw Exception(context.getString(R.string.chat_history_imported_file_empty))
                        }

                        // 转换为 ChatHistory 列表
                        chatHistories.addAll(convertToOperitFormat(content, format))
                    }
                }

                if (chatHistories.isEmpty()) {
                    return@withContext ChatImportResult(0, 0, 0)
                }

                // 保存导入的对话
                val existingIds =
                    allChatHistoriesInternalFlow.first().map { it.id }.toMutableSet()

                var newCount = 0
                var updatedCount = 0
                var skippedCount = 0

                for (chatHistory in chatHistories) {
                    if (chatHistory.messages.isEmpty()) {
                        skippedCount++
                        continue
                    }

                    if (existingIds.contains(chatHistory.id)) {
                        updatedCount++
                    } else {
                        newCount++
                        existingIds.add(chatHistory.id)
                    }

                    saveChatHistory(chatHistory)
                }

                AppLogger.d(TAG, "导入完成: 新增=$newCount, 更新=$updatedCount, 跳过=$skippedCount")
                ChatImportResult(newCount, updatedCount, skippedCount)
            } catch (e: Exception) {
                AppLogger.e(TAG, "导入聊天记录失败", e)
                throw e
            }
        }

    private fun parseLegacyOperitChatHistories(content: String): List<ChatHistory> {
        try {
            return operitArchiveJson.decodeFromString<List<ChatHistory>>(content)
        } catch (e: Exception) {
            val gson =
                GsonBuilder()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                    .create()
            val type = object : TypeToken<List<ChatHistory>>() {}.type
            return gson.fromJson<List<ChatHistory>>(content, type)
        }
    }

    private fun convertToOperitFormat(content: String, format: ChatFormat): List<ChatHistory> {
        return try {
            when (format) {
                ChatFormat.OPERIT -> {
                    parseLegacyOperitChatHistories(content)
                }
                
                ChatFormat.CHATGPT -> {
                    AppLogger.d(TAG, "使用 ChatGPT 转换器")
                    ChatGPTConverter().convert(content)
                }
                
                ChatFormat.CHATBOX -> {
                    AppLogger.d(TAG, "使用 ChatBox 转换器")
                    ChatBoxConverter(context).convert(content)
                }
                
                ChatFormat.MARKDOWN -> {
                    AppLogger.d(TAG, "使用 Markdown 转换器")
                    MarkdownConverter(context).convert(content)
                }
                
                ChatFormat.GENERIC_JSON -> {
                    AppLogger.d(TAG, "使用通用 JSON 转换器")
                    GenericJsonConverter().convert(content)
                }
                
                ChatFormat.CLAUDE -> {
                    // Claude 格式暂不支持，回退到通用 JSON
                    AppLogger.d(TAG, "Claude 格式回退到通用 JSON 转换器")
                    GenericJsonConverter().convert(content)
                }
                
                else -> {
                    throw ConversionException(context.getString(R.string.chat_history_unsupported_format, format))
                }
            }
        } catch (e: ConversionException) {
            throw Exception(context.getString(R.string.chat_history_convert_format_failed, e.message ?: ""), e)
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.chat_history_parse_backup_failed, e.message ?: ""), e)
        }
    }

    /**
     * 清理绑定已删除角色卡的对话（将characterCardName设为null）
     * @param characterCardName 已删除的角色卡名称
     */
    suspend fun clearCharacterCardBinding(characterCardName: String) {
        try {
            withContext(Dispatchers.IO) {
                chatDao.clearCharacterCardBinding(characterCardName)
                AppLogger.d(TAG, "已清理绑定角色卡 '$characterCardName' 的对话")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理角色卡绑定失败: $characterCardName", e)
        }
    }

    /**
     * 将指定角色卡或未绑定的对话转移到新的角色卡
     * @return 受影响的对话数量
     */
    suspend fun reassignChatsToCharacterCard(
            sourceCharacterCardName: String?,
            targetCharacterCardName: String
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val updated = if (sourceCharacterCardName == null) {
                    chatDao.assignCharacterCardToUnbound(targetCharacterCardName)
                } else {
                    chatDao.renameCharacterCardBinding(sourceCharacterCardName, targetCharacterCardName)
                }
                AppLogger.d(
                        TAG,
                        "角色卡聊天重分配: ${sourceCharacterCardName ?: "未绑定"} -> $targetCharacterCardName, 更新 $updated 条记录"
                )
                updated
            } catch (e: Exception) {
                AppLogger.e(
                        TAG,
                        "重命名角色卡绑定失败: ${sourceCharacterCardName ?: "未绑定"} -> $targetCharacterCardName",
                        e
                )
                throw e
            }
        }
    }

    suspend fun getLatestSummaryTimestamp(chatId: String): Long? {
        return withContext(Dispatchers.IO) {
            try {
                messageDao.getLatestSummaryTimestamp(chatId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取最新 summary 时间戳失败", e)
                null
            }
        }
    }

    suspend fun loadMessagesAfterLatestSummaryInRange(
        chatId: String,
        beforeTimestampExclusive: Long? = null,
        upToTimestampInclusive: Long? = null,
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val latestSummaryTimestamp =
                    when {
                        beforeTimestampExclusive != null ->
                            messageDao.getLatestSummaryTimestampBefore(
                                chatId,
                                beforeTimestampExclusive,
                            )
                        upToTimestampInclusive != null ->
                            messageDao.getLatestSummaryTimestampUpTo(
                                chatId,
                                upToTimestampInclusive,
                            )
                        else -> messageDao.getLatestSummaryTimestamp(chatId)
                    }
                val messageEntities =
                    chatContentDao.getMessagesForChatInRangeAsc(
                        chatId = chatId,
                        afterTimestampExclusive = latestSummaryTimestamp,
                        beforeTimestampExclusive = beforeTimestampExclusive,
                        upToTimestampInclusive = upToTimestampInclusive,
                    )
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "按总结窗口加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun hasUserMessage(chatId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                messageDao.existsUserMessage(chatId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "检查聊天是否存在用户消息失败", e)
                false
            }
        }
    }

    suspend fun loadRuntimeChatMessages(chatId: String): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val latestSummaryTimestamp = messageDao.getLatestSummaryTimestamp(chatId)
                val messageEntities =
                    if (latestSummaryTimestamp != null) {
                        chatContentDao.getMessagesForChatFromTimestampAsc(chatId, latestSummaryTimestamp)
                    } else {
                        chatContentDao.getMessagesForChat(chatId)
                    }
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载运行态聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadRuntimeChatMessagesUpTo(
        chatId: String,
        upToTimestampInclusive: Long
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            val latestSummaryTimestamp =
                messageDao.getLatestSummaryTimestampUpTo(chatId, upToTimestampInclusive)
            val messageEntities =
                if (latestSummaryTimestamp != null) {
                    chatContentDao.getMessagesForChatWindowAsc(
                        chatId = chatId,
                        startTimestampInclusive = latestSummaryTimestamp,
                        endTimestampInclusive = upToTimestampInclusive
                    )
                } else {
                    chatContentDao.getMessagesForChatInRangeAsc(
                        chatId = chatId,
                        afterTimestampExclusive = null,
                        beforeTimestampExclusive = null,
                        upToTimestampInclusive = upToTimestampInclusive
                    )
                }
            hydrateMessages(chatId, messageEntities)
        }
    }

    suspend fun loadChatMessageProcessMetadata(
        chatId: String,
    ): List<com.ai.assistance.operit.data.model.ChatMessageProcessMetadata> =
        withContext(Dispatchers.IO) { messageDao.getProcessMetadata(chatId) }

    suspend fun loadChatMessagesByTimestamps(chatId: String, timestamps: List<Long>): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            timestamps.distinct().chunked(400).flatMap { chunk ->
                hydrateMessages(chatId, chatContentDao.getMessagesByTimestamps(chatId, chunk))
            }.sortedBy { it.timestamp }
        }

    suspend fun loadChatMessageLocatorPreviews(
        chatId: String,
        query: String = "",
    ): List<ChatMessageLocatorPreview> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedQuery = query.trim()
                if (normalizedQuery.isBlank()) {
                    messageDao.getLocatorPreviewsForChat(chatId, LOCATOR_PREVIEW_CHAR_COUNT)
                } else {
                    messageDao.searchLocatorPreviewsForChat(
                        chatId = chatId,
                        query = normalizedQuery,
                        previewCharCount = LOCATOR_PREVIEW_CHAR_COUNT,
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载聊天定位轻量预览失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadChatMessagesFromTimestamp(
        chatId: String,
        startTimestampInclusive: Long,
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val messageEntities =
                    chatContentDao.getMessagesForChatFromTimestampAsc(chatId, startTimestampInclusive)
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "按起始时间加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadChatMessagesWindow(
        chatId: String,
        startTimestampInclusive: Long,
        endTimestampInclusive: Long,
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val messageEntities =
                    chatContentDao.getMessagesForChatWindowAsc(
                        chatId = chatId,
                        startTimestampInclusive = startTimestampInclusive,
                        endTimestampInclusive = endTimestampInclusive,
                    )
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "按时间窗口加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadChatMessagesAscAfter(
        chatId: String,
        afterTimestampExclusive: Long,
        limit: Int,
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val messageEntities =
                    chatContentDao.getMessagesForChatAfterTimestampExclusiveAsc(
                        chatId,
                        afterTimestampExclusive,
                        limit,
                    )
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "按起始时间后分页加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadOlderChatMessages(
        chatId: String,
        beforeTimestampExclusive: Long,
        limit: Int,
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val messageEntities =
                    chatContentDao
                        .getMessagesForChatBeforeTimestampExclusiveDesc(
                            chatId,
                            beforeTimestampExclusive,
                            limit,
                        ).asReversed()
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载更早聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun hasMessagesBefore(
        chatId: String,
        beforeTimestampExclusive: Long,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                messageDao.existsMessagesBeforeTimestamp(chatId, beforeTimestampExclusive)
            } catch (e: Exception) {
                AppLogger.e(TAG, "检查是否存在更早聊天消息失败", e)
                false
            }
        }
    }

    suspend fun hasMessagesAfter(
        chatId: String,
        afterTimestampExclusive: Long,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                messageDao.existsMessagesAfterTimestamp(chatId, afterTimestampExclusive)
            } catch (e: Exception) {
                AppLogger.e(TAG, "检查是否存在更新聊天消息失败", e)
                false
            }
        }
    }

    suspend fun loadChatMessagesDesc(
        chatId: String,
        limit: Int,
        beforeTimestampExclusive: Long? = null,
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val messageEntities =
                    if (beforeTimestampExclusive != null) {
                        chatContentDao.getMessagesForChatBeforeTimestampExclusiveDesc(
                            chatId,
                            beforeTimestampExclusive,
                            limit,
                        )
                    } else {
                        chatContentDao.getMessagesForChatDesc(chatId, limit)
                    }
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "按倒序分页加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    suspend fun loadChatMessagesDescUpTo(
        chatId: String,
        maxTimestampInclusive: Long,
        limit: Int,
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val messageEntities =
                    chatContentDao.getMessagesForChatBeforeTimestampDesc(
                        chatId,
                        maxTimestampInclusive,
                        limit,
                    )
                hydrateMessages(chatId, messageEntities)
            } catch (e: Exception) {
                AppLogger.e(TAG, "按截止时间倒序分页加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    /**
     * 将指定角色群组下的对话转移到新的角色群组
     * @return 受影响的对话数量
     */
    suspend fun reassignChatsToCharacterGroup(
        sourceCharacterGroupId: String?,
        targetCharacterGroupId: String
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val updated = if (sourceCharacterGroupId == null) {
                    chatDao.assignCharacterGroupToUnbound(targetCharacterGroupId)
                } else {
                    chatDao.renameCharacterGroupBinding(sourceCharacterGroupId, targetCharacterGroupId)
                }
                AppLogger.d(
                    TAG,
                    "角色群组聊天重分配: ${sourceCharacterGroupId ?: "未绑定"} -> $targetCharacterGroupId, 更新 $updated 条记录"
                )
                updated
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "重命名角色群组绑定失败: ${sourceCharacterGroupId ?: "未绑定"} -> $targetCharacterGroupId",
                    e
                )
                throw e
            }
        }
    }

    // 更新聊天绑定的群组角色卡
    suspend fun updateChatCharacterGroupId(chatId: String, characterGroupId: String?) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatCharacterGroupId(chatId, characterGroupId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat character group for chat $chatId", e)
                throw e
            }
        }
    }

    // 同时更新聊天绑定的角色卡与群组
    suspend fun updateChatCharacterBinding(
        chatId: String,
        characterCardName: String?,
        characterGroupId: String?
    ) {
        chatMutex(chatId).withLock {
            try {
                chatDao.updateChatCharacterBinding(chatId, characterCardName, characterGroupId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat character binding for chat $chatId", e)
                throw e
            }
        }
    }

    /**
     * 清理绑定已删除角色群组的对话（将characterGroupId设为null）
     * @return 受影响的对话数量
     */
    suspend fun clearCharacterGroupBinding(characterGroupId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val updated = chatDao.clearCharacterGroupBinding(characterGroupId)
                AppLogger.d(TAG, "已清理绑定角色群组 '$characterGroupId' 的对话: $updated")
                updated
            } catch (e: Exception) {
                AppLogger.e(TAG, "清理角色群组绑定失败: $characterGroupId", e)
                throw e
            }
        }
    }

    /**
     * 批量删除绑定到缺失角色卡（或未绑定）的未锁定对话
     * @return 实际删除的对话数量
     */
    suspend fun deleteChatsByCharacterCardBinding(sourceCharacterCardName: String?): Int {
        return withContext(Dispatchers.IO) {
            try {
                val currentChatId = currentChatIdFlow.first()
                fun selectDeletionCandidates(allChats: List<ChatEntity>): List<ChatEntity> {
                    val scopedChatIds =
                        allChats
                            .asSequence()
                            .filter { chat ->
                                if (sourceCharacterCardName == null) {
                                    chat.characterCardName == null &&
                                        chat.characterGroupId == null
                                } else {
                                    chat.characterCardName == sourceCharacterCardName
                                }
                            }
                            .mapTo(hashSetOf(), ChatEntity::id)
                    return ChatDeletionGraphPolicy.selectDeletableChats(
                        allChats,
                        scopedChatIds,
                    )
                }
                val expectedChats = selectDeletionCandidates(chatDao.getAllChatsDirectly())
                val expectedChatIds = expectedChats.mapTo(linkedSetOf(), ChatEntity::id)
                val deletedCount =
                    SubagentCoordinator.getInstance(context)
                        .withChatDeletionsPrepared(expectedChatIds) {
                            database.withTransaction {
                                val currentChats =
                                    selectDeletionCandidates(chatDao.getAllChatsDirectly())
                                val currentChatIds =
                                    currentChats.mapTo(linkedSetOf(), ChatEntity::id)
                                check(currentChatIds == expectedChatIds) {
                                    "Character-bound deletion candidates changed while preparing deletion"
                                }
                                ChatDeletionGraphPolicy.orderChildFirst(currentChats).forEach {
                                    chatDao.deleteChat(it.id)
                                }
                                currentChats.count { it.chatKind != ChatKind.SUBAGENT.name }
                            }
                        }

                val currentChatShouldBeCleared =
                    currentChatId != null && currentChatId in expectedChatIds

                if (currentChatShouldBeCleared) {
                    context.currentChatIdDataStore.edit { preferences ->
                        preferences.remove(PreferencesKeys.CURRENT_CHAT_ID)
                    }
                }

                AppLogger.d(
                    TAG,
                    "删除缺失角色卡残留对话: ${sourceCharacterCardName ?: "未绑定"}, 删除 $deletedCount 条"
                )
                deletedCount
            } catch (e: Exception) {
                AppLogger.e(TAG, "删除缺失角色卡残留对话失败: ${sourceCharacterCardName ?: "未绑定"}", e)
                throw e
            }
        }
    }

    /**
     * 批量为特定聊天更新角色卡绑定
     * @return 受影响的对话数量
     */
    suspend fun assignCharacterCardToChats(
        chatIds: List<String>,
        targetCharacterCardName: String?
    ): Int {
        if (chatIds.isEmpty()) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                chatDao.updateCharacterCardForChats(chatIds, targetCharacterCardName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量更新聊天角色卡失败: $targetCharacterCardName, chatIds=$chatIds", e)
                throw e
            }
        }
    }

    /**
     * 批量为特定聊天更新角色群组绑定
     * @return 受影响的对话数量
     */
    suspend fun assignCharacterGroupToChats(
        chatIds: List<String>,
        targetCharacterGroupId: String?
    ): Int {
        if (chatIds.isEmpty()) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                chatDao.updateCharacterGroupForChats(chatIds, targetCharacterGroupId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量更新聊天角色群组失败: $targetCharacterGroupId, chatIds=$chatIds", e)
                throw e
            }
        }
    }

    /**
     * 批量为特定聊天移除角色群组绑定
     * @return 受影响的对话数量
     */
    suspend fun clearCharacterGroupBindingForChats(
        chatIds: List<String>
    ): Int {
        if (chatIds.isEmpty()) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                chatDao.clearCharacterGroupForChats(chatIds)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量清理聊天角色群组失败: chatIds=$chatIds", e)
                throw e
            }
        }
    }

    /**
     * 批量为特定聊天更新分组
     * @return 受影响的对话数量
     */
    suspend fun assignGroupToChats(
        chatIds: List<String>,
        groupName: String?
    ): Int {
        if (chatIds.isEmpty()) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                chatDao.updateGroupForChats(chatIds, groupName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量更新聊天分组失败: $groupName, chatIds=$chatIds", e)
                throw e
            }
        }
    }

    /**
     * 批量重命名对话中绑定的角色卡名称
     * @return 受影响的对话数量
     */
    suspend fun renameCharacterCardInChats(oldName: String, newName: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                chatDao.renameCharacterCardBinding(oldName, newName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量重命名对话绑定角色卡失败: $oldName -> $newName", e)
                throw e
            }
        }
    }

    /**
     * 批量重命名消息中的角色名称
     * @return 受影响的消息数量
     */
    suspend fun renameRoleNameInMessages(oldName: String, newName: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                messageDao.renameRoleName(oldName, newName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量重命名消息中的角色名失败: $oldName -> $newName", e)
                throw e
            }
        }
    }
}

data class ChatImportResult(
    val new: Int,
    val updated: Int,
    val skipped: Int
) {
    val total: Int
        get() = new + updated
}

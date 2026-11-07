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
import com.ai.assistance.operit.data.backup.OperitBackupDirs
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.dao.ChatFolderDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatFolderEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.model.CharacterCardChatStats
import com.ai.assistance.operit.data.model.CharacterGroupChatStats
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import com.ai.assistance.operit.data.model.SYSTEM_UNGROUPED_FOLDER_ID
import com.ai.assistance.operit.data.model.OperitArchivedChat
import com.ai.assistance.operit.data.model.OperitArchivedFolder
import com.ai.assistance.operit.data.model.OperitArchivedMessage
import com.ai.assistance.operit.data.model.OperitArchivedMessageVariant
import com.ai.assistance.operit.data.model.OperitChatArchive
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
internal fun normalizeChatFolderId(folderId: String?): String? =
    folderId.takeUnless { it == SYSTEM_UNGROUPED_FOLDER_ID }

internal suspend fun createAndPersistNewChatHistory(
    chatDao: ChatDao,
    chatFolderDao: ChatFolderDao,
    folderId: String?,
    inheritGroupFromChatId: String?,
    buildHistory: (folderId: String?) -> ChatHistory,
): ChatHistory {
    val requestedFolderId = normalizeChatFolderId(folderId)
    if (requestedFolderId != null) {
        require(chatFolderDao.getFolder(requestedFolderId) != null) {
            "Unknown folderId: $requestedFolderId"
        }
    }
    val finalFolderId =
        when {
            folderId != null -> requestedFolderId
            inheritGroupFromChatId != null ->
                chatDao
                    .getChatById(inheritGroupFromChatId)
                    ?.folderId
                    .let(::normalizeChatFolderId)
            else -> null
        }

    val newHistory = buildHistory(finalFolderId).copy(folderId = finalFolderId)
    chatDao.insertChat(ChatEntity.fromChatHistory(newHistory))
    return newHistory
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
    private val chatFolderDao = database.chatFolderDao()
    private val chatFolderRepository = ChatFolderRepository(database)
    private val messageDao = database.messageDao()
    private val messageVariantDao = database.messageVariantDao()
    private val chatContentDao = database.chatContentDao()
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
        var folderCount: Int = 0,
    )

    private data class LegacyFolderBucketKey(
        val characterCardName: String?,
        val characterGroupId: String?,
        val rawGroup: String,
    )

    private data class LegacyFolderResolutionKey(
        val groupName: String,
        val characterCardName: String?,
        val characterGroupId: String?,
    )

    private data class ArchivedChatMetadata(
        val id: String,
        val title: String,
        val folderId: String?,
        val legacyFolderBucketKey: LegacyFolderBucketKey?,
        val displayOrder: Long,
        val createdAt: Long,
        val hasMessages: Boolean,
        val hasFolderIdField: Boolean,
        val parentChatId: String?,
        val isFavorite: Boolean?,
    )

    private data class ScannedFolder(
        val folder: OperitArchivedFolder,
        val hasRequiredV4Fields: Boolean,
    )

    private data class ScannedArchive(
        val archiveType: String,
        val formatVersion: Int,
        val folders: List<ScannedFolder>,
        val chats: List<ArchivedChatMetadata>,
    )

    private fun OperitArchivedChat.legacyFolderBucketKey(): LegacyFolderBucketKey? {
        val rawGroup = group ?: return null
        if (rawGroup.isBlank()) return null
        return LegacyFolderBucketKey(characterCardName, characterGroupId, rawGroup)
    }

    private fun ChatHistory.legacyFolderBucketKey(): LegacyFolderBucketKey? {
        val rawGroup = group ?: return null
        if (rawGroup.isBlank()) return null
        return LegacyFolderBucketKey(characterCardName, characterGroupId, rawGroup)
    }

    private suspend fun findLegacyFolderId(
        key: LegacyFolderBucketKey,
    ): String? =
        withContext(Dispatchers.IO) {
            val normalizedName = key.rawGroup.trim()
            val candidates =
                chatFolderDao.getFolders().filter {
                    it.id != SYSTEM_UNGROUPED_FOLDER_ID && it.name == normalizedName
                }
            if (candidates.isEmpty()) {
                return@withContext null
            }
            val matchingFolderIds =
                chatDao.getAllChatsDirectly()
                    .asSequence()
                    .filter {
                        it.characterCardName == key.characterCardName &&
                            it.characterGroupId == key.characterGroupId
                    }
                    .mapNotNull { normalizeChatFolderId(it.folderId) }
                    .toSet()
            candidates
                .filter { it.id in matchingFolderIds }
                .minWithOrNull(
                    compareBy<ChatFolderEntity> { it.displayOrder }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )
                ?.id
        }

    private suspend fun createLegacyImportFolders(
        chats: List<ArchivedChatMetadata>,
        counters: ImportCounters,
    ): Map<LegacyFolderBucketKey, String> {
        val grouped = chats.mapNotNull { metadata ->
            metadata.legacyFolderBucketKey
                ?.takeIf { metadata.hasMessages }
                ?.let { it to metadata }
        }.groupBy({ it.first }, { it.second })
        if (grouped.isEmpty()) return emptyMap()

        fun compareNullable(left: String?, right: String?): Int =
            when {
                left == null && right == null -> 0
                left == null -> -1
                right == null -> 1
                else -> left.compareTo(right)
            }
        val keyComparator =
            Comparator<LegacyFolderBucketKey> { left, right ->
                compareNullable(left.characterCardName, right.characterCardName)
                    .takeIf { it != 0 }
                    ?: compareNullable(left.characterGroupId, right.characterGroupId)
                        .takeIf { it != 0 }
                    ?: left.rawGroup.compareTo(right.rawGroup)
            }
        val sorted =
            grouped.entries.sortedWith(
                Comparator { left, right ->
                    left.value.minOf { it.displayOrder }
                        .compareTo(right.value.minOf { it.displayOrder })
                        .takeIf { it != 0 }
                        ?: left.value.minOf { it.createdAt }
                            .compareTo(right.value.minOf { it.createdAt })
                            .takeIf { it != 0 }
                        ?: keyComparator.compare(left.key, right.key)
                }
            )
        val existingFolders = chatFolderDao.getFolders()
        val allocatedIds = existingFolders.mapTo(hashSetOf()) { it.id }
        var nextOrder =
            existingFolders.filter { it.parentFolderId == null }.maxOfOrNull { it.displayOrder }
                ?.plus(1) ?: 0L
        return buildMap {
            sorted.forEach { (key, bucketChats) ->
                findLegacyFolderId(key)?.let { existingId ->
                    put(key, existingId)
                    return@forEach
                }
                var id: String
                do {
                    id = java.util.UUID.randomUUID().toString()
                } while (!allocatedIds.add(id))
                val createdAt = bucketChats.minOf { it.createdAt }
                chatFolderDao.insertFolder(
                    ChatFolderEntity(
                        id = id,
                        name = key.rawGroup.trim(),
                        parentFolderId = null,
                        displayOrder = nextOrder++,
                        createdAt = createdAt,
                    )
                )
                counters.folderCount++
                put(key, id)
            }
        }
    }

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

    private suspend fun buildFolderPathsByChatId(
        chatHistories: List<ChatHistory>,
    ): Map<String, String> {
        val folderById = chatFolderDao.getFolders().associateBy { it.id }
        val pathMemo = mutableMapOf<String, String?>()
        fun resolve(folderId: String, visiting: MutableSet<String>): String? {
            pathMemo[folderId]?.let { return it }
            val folder = folderById[folderId] ?: return null
            if (!visiting.add(folderId)) return null
            val parentPath = folder.parentFolderId?.let { resolve(it, visiting) }
            visiting.remove(folderId)
            val path = if (parentPath.isNullOrBlank()) folder.name else "$parentPath / ${folder.name}"
            pathMemo[folderId] = path
            return path
        }
        return chatHistories.mapNotNull { history ->
            val path = history.folderId?.let { resolve(it, mutableSetOf()) }
            if (path == null) null else history.id to path
        }.toMap()
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
            folderId = stringOrNull("folderId"),
            displayOrder = getLong(getColumnIndexOrThrow("displayOrder")),
            workspace = stringOrNull("workspace"),
            workspaceEnv = stringOrNull("workspaceEnv"),
            parentChatId = stringOrNull("parentChatId"),
            characterCardName = stringOrNull("characterCardName"),
            characterGroupId = stringOrNull("characterGroupId"),
            locked = getInt(getColumnIndexOrThrow("locked")) != 0,
            pinned = getInt(getColumnIndexOrThrow("pinned")) != 0,
            isFavorite = getInt(getColumnIndexOrThrow("isFavorite")) != 0,
            lastMessageAt = longOrNull("lastMessageAt"),
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
            chatEntity.toChatHistory(emptyList()).copy(
                folderId = normalizeChatFolderId(chatEntity.folderId),
            )
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
                    sqliteDb.execSQL(
                        """
                        CREATE TABLE chats AS
                        SELECT id, title, createdAt, updatedAt, inputTokens, outputTokens,
                               currentWindowSize, `group`, folderId, displayOrder, workspace,
                               workspaceEnv, parentChatId, characterCardName, characterGroupId,
                               locked, pinned, isFavorite, lastMessageAt
                        FROM operit_export_source.chats
                        WHERE id IN (SELECT id FROM export_chat_ids)
                        """.trimIndent(),
                    )
                    sqliteDb.execSQL(ARCHIVE_FOLDER_SELECTION_SQL)
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
                val archivedFolders =
                    snapshot.rawQuery(
                        "SELECT id, name, parentFolderId, displayOrder, createdAt FROM chat_folders " +
                            "WHERE id != ? ORDER BY displayOrder, createdAt, id",
                        arrayOf(SYSTEM_UNGROUPED_FOLDER_ID),
                    ).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(
                                    OperitArchivedFolder(
                                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                                        parentFolderId = cursor.stringOrNull("parentFolderId"),
                                        displayOrder =
                                            cursor.getLong(
                                                cursor.getColumnIndexOrThrow("displayOrder")
                                            ),
                                        createdAt =
                                            cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                                    )
                                )
                            }
                        }
                    }
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
                    writer.append("  \"folders\": ")
                    writer.append(operitArchiveJson.encodeToString(archivedFolders))
                    writer.append(",\n")
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
                    writer.append("  ]\n")
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
        folderId: String?,
        formatVersion: Int,
        existingIds: MutableSet<String>,
        counters: ImportCounters,
        importedIndex: Int,
    ) {
        if (archivedChat.messages.isEmpty()) {
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

        saveArchivedChatInCurrentTransaction(archivedChat, formatVersion, folderId)

        if (importedIndex % 20 == 0) {
            AppLogger.d(
                TAG,
                "导入进度: index=$importedIndex, archive chatId=${archivedChat.id}, messages=${archivedChat.messages.size}, new=${counters.newCount}, updated=${counters.updatedCount}, skipped=${counters.skippedCount}",
            )
        }
    }

    private data class StagedV4Archive(
        val folders: List<OperitArchivedFolder>,
        val folderIds: Set<String>,
        val chats: List<ArchivedChatMetadata>,
        val orphanParentCount: Int,
        val orphanChatFolderCount: Int,
    )

    private fun stageV4Archive(archive: ScannedArchive): StagedV4Archive {
        fun requireCanonicalUuid(value: String, field: String) {
            require(
                runCatching { java.util.UUID.fromString(value).toString() == value }.getOrDefault(false)
            ) {
                "$field must be a canonical UUID"
            }
        }

        val folders = archive.folders.map { it.folder }
        val folderIds = folders.map { it.id }
        require(folderIds.distinct().size == folderIds.size) {
            "Archive v4 contains duplicate folder IDs"
        }
        folders.forEach { folder ->
            requireCanonicalUuid(folder.id, "Folder ID")
            folder.parentFolderId?.let { requireCanonicalUuid(it, "Parent folder ID") }
            require(folder.name.trim().isNotEmpty()) { "Folder name must not be blank" }
        }
        val chatIds = archive.chats.map { it.id }
        require(chatIds.all { it.isNotBlank() }) { "Chat ID must not be blank" }
        require(chatIds.distinct().size == chatIds.size) {
            "Archive v4 contains duplicate chat IDs"
        }

        val folderIdSet = folderIds.toSet()
        var orphanParentCount = 0
        val normalizedFolders =
            folders.map { folder ->
                val normalizedParent =
                    folder.parentFolderId?.takeIf { parentId ->
                        (parentId in folderIdSet).also { exists ->
                            if (!exists) orphanParentCount++
                        }
                    }
                folder.copy(
                    name = folder.name.trim(),
                    parentFolderId = normalizedParent,
                )
            }
        val orphanChatFolderCount =
            archive.chats.count { metadata ->
                metadata.folderId != null && metadata.folderId !in folderIdSet
            }
        validateArchivedFolderGraph(normalizedFolders)
        return StagedV4Archive(
            folders = normalizedFolders,
            folderIds = folderIdSet,
            chats = archive.chats,
            orphanParentCount = orphanParentCount,
            orphanChatFolderCount = orphanChatFolderCount,
        )
    }

    private fun topologicallySortFolders(
        folders: List<OperitArchivedFolder>,
    ): List<OperitArchivedFolder> {
        val remaining = folders.associateByTo(linkedMapOf()) { it.id }
        val emitted = hashSetOf<String>()
        val result = mutableListOf<OperitArchivedFolder>()
        while (remaining.isNotEmpty()) {
            val ready =
                remaining.values
                    .filter { it.parentFolderId == null || it.parentFolderId in emitted }
                    .sortedWith(
                        compareBy<OperitArchivedFolder> { it.displayOrder }
                            .thenBy { it.createdAt }
                            .thenBy { it.id }
                    )
            require(ready.isNotEmpty()) { "Archive v4 folder graph is not acyclic" }
            ready.forEach { folder ->
                result += folder
                emitted += folder.id
                remaining.remove(folder.id)
            }
        }
        return result
    }

    private suspend fun importV4Archive(
        staged: StagedV4Archive,
        archiveFile: File,
        existingIds: MutableSet<String>,
        counters: ImportCounters,
        formatVersion: Int = 4,
        chatTransform: (OperitArchivedChat) -> OperitArchivedChat = { it },
        afterChatsInTransaction: suspend () -> Unit = {},
    ): Int {
        var importedFolderCount = 0
        database.withTransaction {
            val localChats = chatDao.getAllChatsDirectly()
            existingIds.clear()
            existingIds.addAll(localChats.map { it.id })
            val localFolders = chatFolderDao.getFolders()
            val localFolderIds = localFolders.mapTo(hashSetOf()) { it.id }
            val archiveFolderIds = staged.folders.mapTo(hashSetOf()) { it.id }
            val allocated = hashSetOf<String>().apply {
                addAll(localFolderIds)
                addAll(archiveFolderIds)
            }
            val archiveFolderById = staged.folders.associateBy { it.id }
            val localFolderById = localFolders.associateBy { it.id }
            val archiveChildrenByParent = staged.folders.groupBy { it.parentFolderId }
            val localChildrenByParent = localFolders.groupBy { it.parentFolderId }
            val archiveSubtreeIds = mutableMapOf<String, Set<String>>()
            fun archiveSubtree(folderId: String): Set<String> =
                archiveSubtreeIds.getOrPut(folderId) {
                    buildSet {
                        add(folderId)
                        archiveChildrenByParent[folderId].orEmpty().forEach { child ->
                            addAll(archiveSubtree(child.id))
                        }
                    }
                }
            val localSubtreeIds = mutableMapOf<String, Set<String>>()
            fun localSubtree(folderId: String): Set<String> =
                localSubtreeIds.getOrPut(folderId) {
                    buildSet {
                        add(folderId)
                        localChildrenByParent[folderId].orEmpty().forEach { child ->
                            addAll(localSubtree(child.id))
                        }
                    }
                }
            val archiveSignatures = mutableMapOf<String, String>()
            fun archiveSignature(folderId: String): String =
                archiveSignatures.getOrPut(folderId) {
                    val folder = archiveFolderById.getValue(folderId)
                    val children =
                        archiveChildrenByParent[folderId]
                            .orEmpty()
                            .map { archiveSignature(it.id) }
                            .sorted()
                            .joinToString(separator = ",", prefix = "[", postfix = "]")
                    "${folder.name}\u0000${folder.displayOrder}\u0000${folder.createdAt}$children"
                }
            val localSignatures = mutableMapOf<String, String>()
            fun localSignature(folderId: String): String =
                localSignatures.getOrPut(folderId) {
                    val folder = localFolderById.getValue(folderId)
                    val children =
                        localChildrenByParent[folderId]
                            .orEmpty()
                            .map { localSignature(it.id) }
                            .sorted()
                            .joinToString(separator = ",", prefix = "[", postfix = "]")
                    "${folder.name}\u0000${folder.displayOrder}\u0000${folder.createdAt}$children"
                }
            val localChatFolderIdByChatId =
                localChats.associate { it.id to normalizeChatFolderId(it.folderId) }
            val existingArchivedChatIds =
                staged.chats
                    .asSequence()
                    .filter { it.hasMessages }
                    .map { it.id }
                    .filter(localChatFolderIdByChatId::containsKey)
                    .toSet()
            val reusedLocalFolderIds = hashSetOf<String>()
            val folderIdRemap = linkedMapOf<String, String>()
            topologicallySortFolders(staged.folders).forEach { folder ->
                val resolvedParentId = folder.parentFolderId?.let(folderIdRemap::getValue)
                fun matchesMetadata(local: ChatFolderEntity): Boolean =
                    local.name == folder.name &&
                        local.parentFolderId == resolvedParentId &&
                        local.displayOrder == folder.displayOrder &&
                        local.createdAt == folder.createdAt

                val parentCandidates =
                    localFolders
                        .asSequence()
                        .filter { it.id !in reusedLocalFolderIds }
                        .filter { it.parentFolderId == resolvedParentId }
                        .toList()
                val archivedChatIds =
                    staged.chats
                        .asSequence()
                        .filter { it.hasMessages && it.folderId in archiveSubtree(folder.id) }
                        .map { it.id }
                        .filter { it in existingArchivedChatIds }
                        .toSet()
                val identityCandidates =
                    if (folder.id in localFolderIds) {
                        parentCandidates.filter { candidate ->
                            if (archivedChatIds.isEmpty()) {
                                candidate.createdAt == folder.createdAt
                            } else {
                                val candidateSubtree = localSubtree(candidate.id)
                                existingArchivedChatIds
                                    .filterTo(hashSetOf()) { chatId ->
                                        localChatFolderIdByChatId.getValue(chatId) in candidateSubtree
                                    } == archivedChatIds
                            }
                        }
                    } else {
                        emptyList()
                    }
                var reusableCandidates =
                    if (folder.id in localFolderIds) {
                        identityCandidates.ifEmpty {
                            parentCandidates.filter(::matchesMetadata)
                        }
                    } else {
                        emptyList()
                    }
                identityCandidates
                    .firstOrNull { it.id == folder.id }
                    ?.let { exactIdentity -> reusableCandidates = listOf(exactIdentity) }
                if (reusableCandidates.size > 1) {
                    reusableCandidates =
                        reusableCandidates.filter { candidate ->
                            val candidateSubtree = localSubtree(candidate.id)
                            existingArchivedChatIds
                                .filterTo(hashSetOf()) { chatId ->
                                    localChatFolderIdByChatId.getValue(chatId) in candidateSubtree
                                } == archivedChatIds
                        }
                }
                if (reusableCandidates.size > 1) {
                    val expectedSignature = archiveSignature(folder.id)
                    reusableCandidates =
                        reusableCandidates.filter {
                            localSignature(it.id) == expectedSignature
                        }
                }
                val reusableId =
                    reusableCandidates
                        .sortedWith(
                            compareByDescending<ChatFolderEntity> { it.id == folder.id }
                                .thenBy { it.id }
                        )
                        .firstOrNull()
                        ?.id
                val resolvedId =
                    reusableId
                        ?: if (folder.id !in localFolderIds) {
                            folder.id
                        } else {
                            var candidate: String
                            do {
                                candidate = java.util.UUID.randomUUID().toString()
                            } while (!allocated.add(candidate))
                            candidate
                        }
                if (reusableId != null) {
                    reusedLocalFolderIds += reusableId
                }
                folderIdRemap[folder.id] = resolvedId
            }
            val remappedFolders =
                staged.folders.map { folder ->
                    folder.copy(
                        id = folderIdRemap.getValue(folder.id),
                        parentFolderId = folder.parentFolderId?.let(folderIdRemap::getValue),
                    )
                }
            val remappedFolderIds = remappedFolders.map { it.id }
            require(remappedFolderIds.distinct().size == remappedFolderIds.size) {
                "Remapped archive contains duplicate folder IDs"
            }
            val remappedFolderIdSet = remappedFolderIds.toSet()
            remappedFolders.forEach { folder ->
                require(
                    runCatching {
                        java.util.UUID.fromString(folder.id).toString() == folder.id
                    }.getOrDefault(false)
                ) { "Remapped folder ID must be a canonical UUID" }
                require(folder.name.isNotBlank() && folder.name == folder.name.trim()) {
                    "Remapped folder name is invalid"
                }
                require(
                    folder.parentFolderId == null ||
                        folder.parentFolderId in remappedFolderIdSet
                ) { "Remapped parent folder reference is invalid" }
            }
            validateArchivedFolderGraph(remappedFolders)
            topologicallySortFolders(remappedFolders).forEach { folder ->
                if (folder.id !in localFolderIds) {
                    chatFolderDao.insertFolder(folder.toEntity())
                    importedFolderCount++
                } else if (folder.id in reusedLocalFolderIds) {
                    chatFolderDao.updateFolder(folder.toEntity())
                }
            }
            forEachArchivedChat(archiveFile) { archivedChat, _, index ->
                val transformedChat = chatTransform(archivedChat)
                val normalizedFolderId =
                    transformedChat.folderId
                        ?.takeIf { it in staged.folderIds }
                        ?.let(folderIdRemap::getValue)
                consumeImportedArchiveChat(
                    archivedChat = transformedChat,
                    folderId = normalizedFolderId,
                    formatVersion = formatVersion,
                    existingIds = existingIds,
                    counters = counters,
                    importedIndex = index,
                )
            }
            afterChatsInTransaction()
            val foreignKeyViolation =
                database.openHelper.writableDatabase
                    .query("PRAGMA foreign_key_check")
                    .use { cursor -> cursor.moveToFirst() }
            check(!foreignKeyViolation) {
                "Archive v$formatVersion import violates foreign keys"
            }
        }
        if (staged.orphanParentCount > 0 || staged.orphanChatFolderCount > 0) {
            AppLogger.w(
                TAG,
                "Archive v4 normalized structural references: parents=${staged.orphanParentCount}, chats=${staged.orphanChatFolderCount}",
            )
        }
        return importedFolderCount
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
        val folderPathsByChatId = buildFolderPathsByChatId(chatHistories)
        BufferedWriter(
            OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8),
        ).use { writer ->
            ChatHistoryCsv.writeHeader(writer)
            chatHistories.forEachIndexed { index, chatHistory ->
                val archivedChat = buildOperitArchivedChat(chatHistory)
                ChatHistoryCsv.writeChat(writer, archivedChat, folderPathsByChatId[archivedChat.id])
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
        var foldersPresent = false
        var chatsPresent = false
        val folders = mutableListOf<ScannedFolder>()
        val chats = mutableListOf<ArchivedChatMetadata>()

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
                    "folders" -> {
                        foldersPresent = true
                        val requiredFields =
                            setOf("id", "name", "parentFolderId", "displayOrder", "createdAt")
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val (folder, fields) =
                                decodeStreamObject(reader, "Archive folder") {
                                    operitArchiveJson.decodeFromString<OperitArchivedFolder>(it)
                                }
                            folders +=
                                ScannedFolder(
                                    folder = folder,
                                    hasRequiredV4Fields = requiredFields.all(fields::contains),
                                )
                        }
                        reader.endArray()
                    }
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
                                    folderId = chat.folderId,
                                    legacyFolderBucketKey = chat.legacyFolderBucketKey(),
                                    displayOrder = chat.displayOrder,
                                    createdAt =
                                        chat.createdAt
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli(),
                                    hasMessages = chat.messages.isNotEmpty(),
                                    hasFolderIdField = "folderId" in fields,
                                    parentChatId = chat.parentChatId,
                                    isFavorite = chat.isFavorite,
                                )
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
        if (resolvedFormatVersion >= 3) {
            require(chats.all { it.isFavorite != null }) {
                "Archive v$resolvedFormatVersion contains a chat without isFavorite"
            }
        }
        if (resolvedFormatVersion >= 4) {
            require(foldersPresent) { "Archive v$resolvedFormatVersion is missing folders" }
            require(folders.all { it.hasRequiredV4Fields }) {
                "Archive v$resolvedFormatVersion contains a folder with missing fields"
            }
            require(chats.all { it.hasFolderIdField }) {
                "Archive v$resolvedFormatVersion contains a chat without folderId"
            }
        }
        return ScannedArchive(
            archiveType = resolvedArchiveType,
            formatVersion = resolvedFormatVersion,
            folders = folders,
            chats = chats,
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

    private suspend fun importLegacyChatArray(
        file: File,
        existingIds: MutableSet<String>,
        counters: ImportCounters,
    ) {
        val importedFolderIdsByBucket = mutableMapOf<LegacyFolderBucketKey, String>()
        val existingFolderIds = chatFolderDao.getFolders().mapTo(hashSetOf()) { it.id }
        var importedIndex = 0
        JsonReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            reader.beginArray()
            while (reader.hasNext()) {
                importedIndex++
                val history =
                    decodeStreamObject(reader, "Legacy chat") {
                        operitArchiveJson.decodeFromString<ChatHistory>(it)
                    }.first
                if (history.messages.isEmpty()) {
                    counters.skippedCount++
                } else {
                    val existed = existingIds.contains(history.id)
                    if (existed) counters.updatedCount++ else {
                        counters.newCount++
                        existingIds.add(history.id)
                    }
                    val legacyFolderBucket = history.legacyFolderBucketKey()
                    val normalizedHistory =
                        if (history.folderId == null && legacyFolderBucket != null) {
                            val folderId =
                                importedFolderIdsByBucket[legacyFolderBucket]
                                    ?: (
                                        findLegacyFolderId(legacyFolderBucket)
                                            ?: createFolder(
                                                parentFolderId = null,
                                                name = legacyFolderBucket.rawGroup.trim(),
                                            )
                                    ).also {
                                        importedFolderIdsByBucket[legacyFolderBucket] = it
                                    }
                            if (existingFolderIds.add(folderId)) {
                                counters.folderCount++
                            }
                            history.copy(group = null, folderId = folderId)
                        } else {
                            history.copy(group = null)
                        }
                    saveChatHistoryInternal(
                        history = normalizedHistory,
                        preserveStructure = false,
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
                    if (archive.formatVersion == 4) {
                        counters.folderCount =
                            importV4Archive(
                                staged = stageV4Archive(archive),
                                archiveFile = importFile,
                                existingIds = existingIds,
                                counters = counters,
                            )
                    } else {
                        database.withTransaction {
                            val folderIdByBucket =
                                createLegacyImportFolders(archive.chats, counters)
                            forEachArchivedChat(importFile) { archivedChat, fields, index ->
                                // v2/v3 distinguish a missing legacy group field from explicit null/blank.
                                val folderId =
                                    if ("group" in fields) {
                                        archivedChat
                                            .legacyFolderBucketKey()
                                            ?.let(folderIdByBucket::get)
                                    } else {
                                        chatDao.getChatById(archivedChat.id)?.folderId
                                    }
                                consumeImportedArchiveChat(
                                    archivedChat = archivedChat,
                                    folderId = folderId,
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
            foldersCreated = counters.folderCount,
            mayLeavePreviousEmptyFolders = counters.folderCount > 0,
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
    private val folderStructureMutex = Mutex()
    private val legacyFolderResolutionMutex = Mutex()
    private val reservedLegacyFolderIds = mutableMapOf<LegacyFolderResolutionKey, String>()
    private val chatMutexes = ConcurrentHashMap<String, Mutex>()

    private fun chatMutex(chatId: String): Mutex {
        return chatMutexes.getOrPut(chatId) { Mutex() }
    }

    // DataStore键
    private object PreferencesKeys {
        val CURRENT_CHAT_ID = stringPreferencesKey("current_chat_id")
    }

    // 辅助函数：将ChatEntity转换为ChatHistory
    private fun ChatEntity.toChatHistory(): ChatHistory {
        return toChatHistory(emptyList())
    }

    private val historyFlowScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun Flow<List<ChatEntity>>.toHistoryFlow(): Flow<List<ChatHistory>> =
        map { chatEntities ->
            withContext(Dispatchers.IO) {
                chatEntities.map {
                    it.toChatHistory().copy(folderId = normalizeChatFolderId(it.folderId))
                }
            }
        }

    internal val allChatHistoriesUpdatesFlow = chatDao.getAllChats().toHistoryFlow()
    val chatHistoriesFlow = allChatHistoriesUpdatesFlow.stateIn(
        historyFlowScope, SharingStarted.Lazily, emptyList(),
    )

    val chatFoldersFlow =
        chatFolderDao
            .observeFolders()
            .onStart { chatFolderRepository.ensureUngroupedFolder() }
            .stateIn(
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            SharingStarted.Lazily,
            emptyList(),
        )

    /** Reads persisted metadata for a specific chat without subscribing to the UI cache. */
    suspend fun getChatHistorySnapshot(chatId: String): ChatHistory? =
        withContext(Dispatchers.IO) {
            chatDao.getChatById(chatId)?.let { entity ->
                entity.toChatHistory().copy(folderId = normalizeChatFolderId(entity.folderId))
            }
        }

    suspend fun getChatHistoriesSnapshot(): List<ChatHistory> =
        withContext(Dispatchers.IO) {
            chatDao.getAllChatsDirectly().map {
                it.toChatHistory().copy(folderId = normalizeChatFolderId(it.folderId))
            }
        }

    suspend fun getChatFoldersSnapshot(): List<ChatFolderEntity> =
        withContext(Dispatchers.IO) {
            chatFolderRepository.ensureUngroupedFolder()
            chatFolderDao.getFolders()
        }

    suspend fun createFolder(parentFolderId: String?, name: String): String =
        chatFolderRepository.createFolder(parentFolderId, name)

    suspend fun findLegacyFolderId(
        groupName: String,
        characterCardName: String? = null,
        characterGroupId: String? = null,
    ): String? =
        withContext(Dispatchers.IO) {
            val normalizedName = groupName.trim()
            if (normalizedName.isEmpty()) {
                return@withContext null
            }
            val candidates =
                chatFolderDao.getFolders().filter {
                    it.id != SYSTEM_UNGROUPED_FOLDER_ID && it.name == normalizedName
                }
            if (candidates.isEmpty()) {
                return@withContext null
            }
            val normalizedCharacterCardName =
                characterCardName?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedCharacterGroupId =
                characterGroupId?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedCharacterCardName == null && normalizedCharacterGroupId == null) {
                return@withContext candidates.minWithOrNull(
                    compareBy<ChatFolderEntity> { it.displayOrder }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )?.id
            }
            val matchingFolderIds =
                chatDao.getAllChatsDirectly()
                    .asSequence()
                    .filter {
                        when {
                            normalizedCharacterGroupId != null ->
                                it.characterGroupId == normalizedCharacterGroupId
                            else ->
                                it.characterCardName == normalizedCharacterCardName &&
                                    it.characterGroupId == null
                        }
                    }
                    .mapNotNull { normalizeChatFolderId(it.folderId) }
                    .toSet()
            candidates
                .filter { it.id in matchingFolderIds }
                .minWithOrNull(
                    compareBy<ChatFolderEntity> { it.displayOrder }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )
                ?.id
        }

    suspend fun resolveOrCreateLegacyFolderId(
        groupName: String,
        characterCardName: String? = null,
        characterGroupId: String? = null,
    ): String {
        val normalizedName = groupName.trim()
        require(normalizedName.isNotEmpty()) { "Group name must not be blank" }
        val normalizedCharacterCardName =
            characterCardName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedCharacterGroupId =
            characterGroupId?.trim()?.takeIf { it.isNotEmpty() }
        val key =
            LegacyFolderResolutionKey(
                normalizedName,
                normalizedCharacterCardName,
                normalizedCharacterGroupId,
            )
        return legacyFolderResolutionMutex.withLock {
            resolveOrCreateLegacyFolderIdLocked(key)
        }
    }

    private suspend fun resolveOrCreateLegacyFolderIdLocked(
        key: LegacyFolderResolutionKey,
    ): String {
        findLegacyFolderId(
            key.groupName,
            key.characterCardName,
            key.characterGroupId,
        )?.let { folderId ->
            reservedLegacyFolderIds.remove(key)
            return folderId
        }
        reservedLegacyFolderIds[key]
            ?.takeIf { reservedId ->
                chatFolderDao.getFolder(reservedId)?.name == key.groupName
            }
            ?.let { return it }
        reservedLegacyFolderIds.remove(key)
        return createFolder(parentFolderId = null, name = key.groupName).also { folderId ->
            reservedLegacyFolderIds[key] = folderId
        }
    }

    private suspend fun releaseLegacyFolderReservations(folderIds: Collection<String?>) {
        val persistedFolderIds = folderIds.mapNotNull(::normalizeChatFolderId).toSet()
        if (persistedFolderIds.isEmpty()) {
            return
        }
        legacyFolderResolutionMutex.withLock {
            reservedLegacyFolderIds.entries.removeAll { it.value in persistedFolderIds }
        }
    }

    suspend fun renameFolderIfExists(folderId: String, newName: String): Boolean =
        folderStructureMutex.withLock {
            if (
                folderId == SYSTEM_UNGROUPED_FOLDER_ID ||
                chatFolderDao.getFolder(folderId) == null
            ) {
                return@withLock false
            }
            chatFolderRepository.renameFolder(folderId, newName)
            true
        }

    suspend fun renameFolder(folderId: String, newName: String) =
        folderStructureMutex.withLock {
            chatFolderRepository.renameFolder(folderId, newName)
        }

    suspend fun moveFolder(
        folderId: String,
        targetParentFolderId: String?,
        expectedSourceSiblings: List<HistorySiblingSnapshot>,
        expectedTargetSiblings: List<HistorySiblingSnapshot>,
        beforeNodeKey: String? = null,
        afterNodeKey: String? = null,
        allowAppendToNonEmptyTarget: Boolean = true,
    ) =
        chatFolderRepository.moveFolder(
            folderId = folderId,
            targetParentFolderId = targetParentFolderId,
            expectedSourceSiblings = expectedSourceSiblings,
            expectedTargetSiblings = expectedTargetSiblings,
            beforeNodeKey = beforeNodeKey,
            afterNodeKey = afterNodeKey,
            allowAppendToNonEmptyTarget = allowAppendToNonEmptyTarget,
        )

    suspend fun moveChat(
        chatId: String,
        targetFolderId: String?,
        expectedSourceSiblings: List<HistorySiblingSnapshot>,
        expectedTargetSiblings: List<HistorySiblingSnapshot>,
        orderedVisibleNodeKeys: List<String>? = null,
        beforeNodeKey: String? = null,
        afterNodeKey: String? = null,
        allowAppendToNonEmptyTarget: Boolean = true,
    ) =
        chatFolderRepository.moveChat(
            chatId = chatId,
            targetFolderId = targetFolderId,
            orderedVisibleNodeKeys = orderedVisibleNodeKeys,
            beforeNodeKey = beforeNodeKey,
            afterNodeKey = afterNodeKey,
            expectedSourceSiblings = expectedSourceSiblings,
            expectedTargetSiblings = expectedTargetSiblings,
            allowAppendToNonEmptyTarget = allowAppendToNonEmptyTarget,
        )

    suspend fun deleteFolderIfExists(folderId: String): Boolean =
        folderStructureMutex.withLock {
            if (
                folderId == SYSTEM_UNGROUPED_FOLDER_ID ||
                chatFolderDao.getFolder(folderId) == null
            ) {
                return@withLock false
            }
            chatFolderRepository.deleteFolder(folderId)
            true
        }

    suspend fun deleteFolderWithChatsIfExists(
        folderId: String,
        characterCardName: String?,
        characterGroupId: String?,
    ): Boolean =
        folderStructureMutex.withLock {
            if (
                folderId == SYSTEM_UNGROUPED_FOLDER_ID ||
                chatFolderDao.getFolder(folderId) == null
            ) {
                return@withLock false
            }
            val normalizedCharacterCardName =
                characterCardName?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedCharacterGroupId =
                characterGroupId?.trim()?.takeIf { it.isNotEmpty() }
            val expectedChatIds =
                chatFolderRepository.getFolderDeletionChatIds(
                    folderId = folderId,
                    characterCardName = normalizedCharacterCardName,
                    characterGroupId = normalizedCharacterGroupId,
                )
            val deletedChatIds =
                    chatFolderRepository.deleteFolderWithChats(
                        folderId = folderId,
                        characterCardName = normalizedCharacterCardName,
                        characterGroupId = normalizedCharacterGroupId,
                        expectedChatIds = expectedChatIds,
                    )
            context.currentChatIdDataStore.edit { preferences ->
                if (preferences[PreferencesKeys.CURRENT_CHAT_ID] in deletedChatIds) {
                    preferences.remove(PreferencesKeys.CURRENT_CHAT_ID)
                }
            }
            true
        }

    suspend fun deleteFolder(folderId: String) =
        folderStructureMutex.withLock {
            chatFolderRepository.deleteFolder(folderId)
        }

    suspend fun reorderProjectedChats(
        expectedHistories: List<ChatHistory>,
        orderedHistories: List<ChatHistory>,
    ): Boolean =
        folderStructureMutex.withLock {
            val expectedFolderIdsByChatId =
                expectedHistories.associate { it.id to normalizeChatFolderId(it.folderId) }
            val expectedDisplayOrdersByChatId =
                expectedHistories.associate { it.id to it.displayOrder }
            if (
                expectedFolderIdsByChatId.size != expectedHistories.size ||
                    orderedHistories.mapTo(hashSetOf()) { it.id } !=
                        expectedFolderIdsByChatId.keys ||
                    orderedHistories.any {
                        normalizeChatFolderId(it.folderId) !=
                            expectedFolderIdsByChatId[it.id]
                    }
            ) {
                return@withLock false
            }
            chatFolderRepository.reorderProjectedChats(
                expectedChatIds = expectedHistories.map { it.id },
                orderedChatIds = orderedHistories.map { it.id },
                expectedFolderIdsByChatId = expectedFolderIdsByChatId,
                expectedDisplayOrdersByChatId = expectedDisplayOrdersByChatId,
            )
        }

    suspend fun getTotalChatCount(): Int {
        return withContext(Dispatchers.IO) { chatDao.getTotalChatCount() }
    }

    suspend fun getTotalMessageCount(): Int {
        return withContext(Dispatchers.IO) { messageDao.getTotalMessageCount() }
    }

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
                chatEntities.map {
                    it.toChatHistory().copy(folderId = normalizeChatFolderId(it.folderId))
                }
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
        archiveFormatVersion: Int? = null,
        archivedFavorite: Boolean? = null,
        preserveStructure: Boolean = true,
    ) {
        chatMutex(history.id).withLock {
            database.withTransaction {
                persistChatHistoryInCurrentTransaction(
                    history = history,
                    variantsByTimestamp = variantsByTimestamp,
                    archiveFormatVersion = archiveFormatVersion,
                    archivedFavorite = archivedFavorite,
                    preserveStructure = preserveStructure,
                )
            }
        }
        releaseLegacyFolderReservations(listOf(history.folderId))
    }

    private suspend fun persistChatHistoryInCurrentTransaction(
        history: ChatHistory,
        variantsByTimestamp: Map<Long, List<OperitArchivedMessageVariant>> = emptyMap(),
        archiveFormatVersion: Int? = null,
        archivedFavorite: Boolean? = null,
        preserveStructure: Boolean = true,
    ) {
        val resolvedHistory =
            (
                if (archiveFormatVersion != null) {
                    val localFavorite = chatDao.getChatById(history.id)?.isFavorite ?: false
                    history.copy(
                        isFavorite =
                            ChatArchiveImportPolicy.resolveFavorite(
                                formatVersion = archiveFormatVersion,
                                archivedFavorite = archivedFavorite,
                                localFavorite = localFavorite,
                            )
                    )
                } else {
                    history
                }
            ).copy(folderId = normalizeChatFolderId(history.folderId))
        val chatEntity =
            ChatEntity.fromChatHistory(resolvedHistory).let { entity ->
                if (archiveFormatVersion == null) {
                    entity
                } else {
                    entity.copy(displayOrder = resolvedHistory.displayOrder)
                }
            }
        val existingEntity = chatDao.getChatById(chatEntity.id)
        if (existingEntity == null) {
            chatDao.insertChat(chatEntity)
        } else {
            val merged =
                mergePersistedChatEntity(
                    incoming = chatEntity,
                    existing = existingEntity,
                    preserveStructure = preserveStructure && archiveFormatVersion == null,
                )
            chatDao.updateChat(
                merged.copy(folderId = normalizeChatFolderId(merged.folderId))
            )
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
        chatDao.recalculateLastMessageAt(chatEntity.id)
    }

    // 保存聊天历史
    suspend fun saveChatHistory(history: ChatHistory) {
        saveChatHistoryInternal(history)
    }

    private suspend fun saveArchivedChat(
        history: OperitArchivedChat,
        formatVersion: Int,
        folderId: String?,
    ) {
        val variantsByTimestamp =
            history.messages.associate { archivedMessage ->
                archivedMessage.baseMessage.timestamp to archivedMessage.variants
            }
        saveChatHistoryInternal(
            history.toChatHistory(history.isFavorite ?: false, folderId),
            variantsByTimestamp,
            archiveFormatVersion = formatVersion,
            archivedFavorite = history.isFavorite,
        )
    }

    private suspend fun saveArchivedChatInCurrentTransaction(
        history: OperitArchivedChat,
        formatVersion: Int,
        folderId: String?,
    ) {
        val variantsByTimestamp =
            history.messages.associate { archivedMessage ->
                archivedMessage.baseMessage.timestamp to archivedMessage.variants
            }
        persistChatHistoryInCurrentTransaction(
            history = history.toChatHistory(history.isFavorite ?: false, folderId),
            variantsByTimestamp = variantsByTimestamp,
            archiveFormatVersion = formatVersion,
            archivedFavorite = history.isFavorite,
        )
    }

    /** 更新聊天锁定状态 */
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

    /** 收藏状态不修改消息收藏、updatedAt 或 lastMessageAt。 */
    suspend fun updateChatFavorite(chatId: String, isFavorite: Boolean) {
        chatMutex(chatId).withLock {
            chatDao.updateChatFavorite(chatId, isFavorite)
        }
    }

    private suspend fun persistMessageLocked(chatId: String, messageToPersist: ChatMessage): ChatMessage {
        database.withTransaction {
            val nextOrderIndex = (messageDao.getMaxOrderIndex(chatId) ?: -1) + 1
            val messageEntity =
                MessageEntity.fromChatMessage(
                    chatId = chatId,
                    message = messageToPersist,
                    orderIndex = nextOrderIndex,
                )
            messageDao.insertMessage(messageEntity)
            chatDao.advanceLastMessageAt(chatId, messageToPersist.timestamp)

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
     * 删除单条消息.
     * @param chatId 聊天ID
     * @param timestamp 消息时间戳
     */
    suspend fun deleteMessage(chatId: String, timestamp: Long) {
        chatMutex(chatId).withLock {
            try {
                AppLogger.d(TAG, "正在从数据库删除消息. ChatId: $chatId, Timestamp: $timestamp")
                database.withTransaction {
                    messageVariantDao.deleteVariantsForMessage(chatId, timestamp)
                    messageDao.deleteMessageByTimestamp(chatId, timestamp)
                    chatDao.recalculateLastMessageAt(chatId)

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
                }
                AppLogger.d(TAG, "消息从数据库删除成功.")
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
                database.withTransaction {
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
                database.withTransaction {
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
                            return@withTransaction
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
                        }
                    } else {
                        val nextOrderIndex = (messageDao.getMaxOrderIndex(chatId) ?: -1) + 1
                        val messageEntity =
                            MessageEntity.fromChatMessage(
                                chatId = chatId,
                                message = message,
                                orderIndex = nextOrderIndex,
                            )
                        messageDao.insertMessage(messageEntity)
                        chatDao.advanceLastMessageAt(chatId, message.timestamp)

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
            database.withTransaction {
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
                database.withTransaction {
                    messageVariantDao.deleteVariantsFrom(chatId, timestamp)
                    messageDao.deleteMessagesFrom(chatId, timestamp)
                    chatDao.recalculateLastMessageAt(chatId)
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
                }
                AppLogger.d(TAG, "后续消息从数据库删除成功.")
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
                database.withTransaction {
                    messageVariantDao.deleteAllVariantsForChat(chatId)
                    messageDao.deleteAllMessagesForChat(chatId)
                    chatDao.recalculateLastMessageAt(chatId)
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
                // 删除聊天实体（级联删除所有消息）
                chatDao.deleteChat(chatId)

                // 如果删除的是当前聊天，清除当前聊天ID
                val currentChatId = currentChatIdFlow.first()
                if (currentChatId == chatId) {
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

    private fun buildNewChatHistory(
        folderId: String?,
        characterCardName: String?,
        characterGroupId: String?,
    ): ChatHistory {
        val dateTime = LocalDateTime.now()
        val formattedTime =
            "${dateTime.hour}:${
                dateTime.minute.toString().padStart(2, '0')
            }:${dateTime.second.toString().padStart(2, '0')}"
        val localizedContext = LocaleUtils.getLocalizedContext(context)

        return ChatHistory(
            title = "${localizedContext.getString(R.string.new_conversation)} $formattedTime",
            messages = emptyList(),
            inputTokens = 0,
            outputTokens = 0,
            folderId = folderId,
            characterCardName = characterCardName,
            characterGroupId = characterGroupId,
        )
    }

    // 创建新对话
    suspend fun createNewChat(
        folderId: String? = null,
        inheritGroupFromChatId: String? = null,
        characterCardName: String? = null,
        characterGroupId: String? = null,
        setAsCurrentChat: Boolean = true
    ): ChatHistory {
        val newHistory =
            createAndPersistNewChatHistory(
                chatDao = chatDao,
                chatFolderDao = chatFolderDao,
                folderId = folderId,
                inheritGroupFromChatId = inheritGroupFromChatId,
            ) { finalFolderId ->
                buildNewChatHistory(
                    folderId = finalFolderId,
                    characterCardName = characterCardName,
                    characterGroupId = characterGroupId,
                )
            }
        releaseLegacyFolderReservations(listOf(newHistory.folderId))

        // 设置为当前聊天
        if (setAsCurrentChat) {
            setCurrentChatId(newHistory.id)
        }

        return newHistory
    }

    suspend fun createFolderWithInitialChat(
        parentFolderId: String?,
        folderName: String,
        characterCardName: String?,
        characterGroupId: String?,
        openingMessage: ChatMessage? = null,
    ): ChatHistory {
        val initialHistory =
            buildNewChatHistory(
                folderId = null,
                characterCardName = characterCardName,
                characterGroupId = characterGroupId,
            )
        val initialEntity = ChatEntity.fromChatHistory(initialHistory)
        val openingMessageEntity =
            openingMessage?.let {
                MessageEntity.fromChatMessage(
                    chatId = initialHistory.id,
                    message = it,
                    orderIndex = 0,
                )
            }
        val persistedEntity =
            chatFolderRepository.createFolderWithInitialChat(
                parentFolderId = parentFolderId,
                name = folderName,
                initialChat = initialEntity,
                openingMessage = openingMessageEntity,
            )
        return persistedEntity.toChatHistory(listOfNotNull(openingMessage))
    }

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

    suspend fun updateChatFolder(chatId: String, folderId: String?) {
        chatMutex(chatId).withLock {
            try {
                val normalizedFolderId =
                    normalizeChatFolderId(folderId)
                if (normalizedFolderId != null) {
                    require(chatFolderDao.getFolder(normalizedFolderId) != null) {
                        "Unknown folderId: $normalizedFolderId"
                    }
                }
                chatDao.updateChatFolder(chatId, normalizedFolderId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat folder for chat $chatId", e)
                throw e
            }
        }
    }

    suspend fun updateChatFromWeb(
        chatId: String,
        title: String?,
        updateFolder: Boolean,
        folderId: String?,
        locked: Boolean?,
        pinned: Boolean?,
        updateBinding: Boolean,
        characterCardName: String?,
        characterGroupId: String?,
    ): Boolean {
        val updated =
            chatMutex(chatId).withLock {
                database.withTransaction {
                    if (chatDao.getChatById(chatId) == null) {
                        return@withTransaction false
                    }
                    val normalizedFolderId =
                        normalizeChatFolderId(folderId)
                    if (updateFolder && normalizedFolderId != null) {
                        require(chatFolderDao.getFolder(normalizedFolderId) != null) {
                            "Unknown folderId: $normalizedFolderId"
                        }
                    }
                    val timestamp = System.currentTimeMillis()
                    title?.let { chatDao.updateChatTitle(chatId, it, timestamp) }
                    if (updateFolder) {
                        chatDao.updateChatFolder(chatId, normalizedFolderId, timestamp)
                    }
                    locked?.let { chatDao.updateChatLocked(chatId, it, timestamp) }
                    pinned?.let { chatDao.updateChatPinned(chatId, it, timestamp) }
                    if (updateBinding) {
                        chatDao.updateChatCharacterBinding(
                            chatId = chatId,
                            characterCardName = characterCardName,
                            characterGroupId = characterGroupId,
                            timestamp = timestamp,
                        )
                    }
                    true
                }
            }
        if (updated && updateFolder) {
            releaseLegacyFolderReservations(listOf(folderId))
        }
        return updated
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
                        folderId = normalizeChatFolderId(parentChat.folderId),
                        workspace = parentChat.workspace,
                        workspaceEnv = parentChat.workspaceEnv,
                        parentChatId = parentChatId,
                        characterCardName = parentChat.characterCardName,
                        characterGroupId = parentChat.characterGroupId,
                        locked = false,
                        pinned = false,
                    )

                chatDao.insertChat(branchEntity)

                val copiedMessageCount =
                    messageDao.countMessagesForChatUpToTimestamp(parentChatId, upToMessageTimestamp)
                if (copiedMessageCount > 0) {
                    messageDao.copyMessagesToChat(
                        sourceChatId = parentChatId,
                        targetChatId = branchEntity.id,
                        upToTimestampInclusive = upToMessageTimestamp,
                    )
                    messageVariantDao.copyVariantsToChat(
                        sourceChatId = parentChatId,
                        targetChatId = branchEntity.id,
                        upToTimestampInclusive = upToMessageTimestamp,
                    )
                }

                chatDao.recalculateLastMessageAt(branchEntity.id)
                val branchHistory = branchEntity.toChatHistory(emptyList())

                // 设置为当前聊天
                setCurrentChatId(branchHistory.id)

                AppLogger.d(
                    TAG,
                    "创建分支对话: ${branchHistory.id}, 父对话: $parentChatId, 消息数: $copiedMessageCount"
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
                branchEntities.map { it.toChatHistory() }
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
            branchEntities.map { it.toChatHistory() }
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
                val folderPathsByChatId = buildFolderPathsByChatId(chatHistoriesBasic)
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
                                val content =
                                    MarkdownExporter.exportSingle(
                                        context,
                                        history,
                                        folderPathsByChatId[history.id],
                                    )
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
                                folderPathsByChatId = folderPathsByChatId,
                            )
                        } else {
                            val completeHistories = loadDisplayHistories(chatHistoriesBasic)
                            file.writeText(HtmlExporter.exportMultiple(context, completeHistories, folderPathsByChatId))
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
                                folderPathsByChatId = folderPathsByChatId,
                            )
                        } else {
                            val completeHistories = loadDisplayHistories(chatHistoriesBasic)
                            file.writeText(TextExporter.exportMultiple(context, completeHistories, folderPathsByChatId))
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
        folderPathsByChatId: Map<String, String>,
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
                            folderPath = folderPathsByChatId[chatHistoryBasic.id],
                        )

                    ExportFormat.TXT ->
                        TextExporter.writeSingleToWriter(
                            context = context,
                            chatHistory = completeHistory,
                            writer = writer,
                            onContentCharactersWritten = onContentCharactersWritten,
                            folderPath = folderPathsByChatId[chatHistoryBasic.id],
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
                            chatHistoriesFlow.first().map { it.id }.toMutableSet()
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
                    chatHistoriesFlow.first().map { it.id }.toMutableSet()

                var newCount = 0
                var updatedCount = 0
                var skippedCount = 0
                val importedFolderIdsByBucket = mutableMapOf<LegacyFolderBucketKey, String>()

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

                    val legacyFolderBucket = chatHistory.legacyFolderBucketKey()
                    val normalizedHistory =
                        if (chatHistory.folderId == null && legacyFolderBucket != null) {
                            val folderId =
                                importedFolderIdsByBucket[legacyFolderBucket]
                                    ?: (
                                        findLegacyFolderId(legacyFolderBucket)
                                            ?: createFolder(
                                                parentFolderId = null,
                                                name = legacyFolderBucket.rawGroup.trim(),
                                            )
                                    ).also {
                                        importedFolderIdsByBucket[legacyFolderBucket] = it
                                    }
                            chatHistory.copy(group = null, folderId = folderId)
                        } else {
                            chatHistory.copy(group = null)
                        }
                    saveChatHistoryInternal(
                        history = normalizedHistory,
                        preserveStructure = false,
                    )
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
                    return allChats.filter { it.id in scopedChatIds && !it.locked }
                }
                val expectedChats = selectDeletionCandidates(chatDao.getAllChatsDirectly())
                val expectedChatIds = expectedChats.mapTo(linkedSetOf(), ChatEntity::id)
                val deletedCount =
                            database.withTransaction {
                                val currentChats =
                                    selectDeletionCandidates(chatDao.getAllChatsDirectly())
                                val currentChatIds =
                                    currentChats.mapTo(linkedSetOf(), ChatEntity::id)
                                check(currentChatIds == expectedChatIds) {
                                    "Character-bound deletion candidates changed while preparing deletion"
                                }
                                currentChats.forEach {
                                    chatDao.deleteChat(it.id)
                                }
                                currentChats.size
                            }

                if (currentChatId in expectedChatIds) {
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
     * 批量为特定聊天更新文件夹归属。
     * @return 受影响的对话数量
     */
    suspend fun assignFolderToChats(
        chatIds: List<String>,
        targetFolderId: String?,
    ): Int {
        if (chatIds.isEmpty()) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                val normalizedFolderId = normalizeChatFolderId(targetFolderId)
                if (normalizedFolderId != null) {
                    require(chatFolderDao.getFolder(normalizedFolderId) != null) {
                        "Unknown folderId: $normalizedFolderId"
                    }
                }
                chatDao.updateFolderForChats(chatIds, normalizedFolderId)
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "批量更新聊天文件夹失败: folderId=$targetFolderId, chatIds=$chatIds",
                    e,
                )
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

internal fun mergePersistedChatEntity(
    incoming: ChatEntity,
    existing: ChatEntity,
    preserveStructure: Boolean,
): ChatEntity =
    incoming.copy(
        // The legacy group column is diagnostic-only and must never be revived by a normal save.
        group = existing.group,
        // Folder placement is repository-owned. A streaming/current-chat save may carry a stale
        // UI snapshot and must not undo a completed structural move.
        folderId = if (preserveStructure) existing.folderId else incoming.folderId,
        displayOrder = if (preserveStructure) existing.displayOrder else incoming.displayOrder,
    )

data class ChatImportResult(
    val new: Int,
    val updated: Int,
    val skipped: Int,
    val foldersCreated: Int = 0,
    val mayLeavePreviousEmptyFolders: Boolean = false,
) {
    val total: Int
        get() = new + updated
}

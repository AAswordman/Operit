package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity

// 按 BLOB 字节读取，避免 TEXT 的 LENGTH/SUBSTR 在 NUL 处截断；每个字段单块不超过 64 KiB。
private const val CONTENT_CHUNK_BYTE_COUNT = 65_536

private const val MESSAGE_CONTENT_ROW_QUERY =
    """
    SELECT
        messageId,
        chatId,
        sender,
        '' AS sections,
        '' AS searchText,
        timestamp,
        orderIndex,
        roleName,
        selectedVariantIndex,
        provider,
        modelName,
        inputTokens,
        outputTokens,
        cachedInputTokens,
        sentAt,
        outputDurationMs,
        waitDurationMs,
        completedAt,
        displayMode,
        isFavorite,
        LENGTH(CAST(sections AS BLOB)) AS sectionsByteCount,
        COALESCE(CAST(SUBSTR(CAST(sections AS BLOB), 1, $CONTENT_CHUNK_BYTE_COUNT) AS BLOB), X'') AS sectionsBytes,
        LENGTH(CAST(searchText AS BLOB)) AS searchTextByteCount,
        COALESCE(CAST(SUBSTR(CAST(searchText AS BLOB), 1, $CONTENT_CHUNK_BYTE_COUNT) AS BLOB), X'') AS searchTextBytes
    FROM messages
    """

private const val MESSAGE_VARIANT_CONTENT_ROW_QUERY =
    """
    SELECT
        variantId,
        chatId,
        messageTimestamp,
        variantIndex,
        '' AS sections,
        '' AS searchText,
        roleName,
        provider,
        modelName,
        inputTokens,
        outputTokens,
        cachedInputTokens,
        sentAt,
        outputDurationMs,
        waitDurationMs,
        completedAt,
        LENGTH(CAST(sections AS BLOB)) AS sectionsByteCount,
        COALESCE(CAST(SUBSTR(CAST(sections AS BLOB), 1, $CONTENT_CHUNK_BYTE_COUNT) AS BLOB), X'') AS sectionsBytes,
        LENGTH(CAST(searchText AS BLOB)) AS searchTextByteCount,
        COALESCE(CAST(SUBSTR(CAST(searchText AS BLOB), 1, $CONTENT_CHUNK_BYTE_COUNT) AS BLOB), X'') AS searchTextBytes
    FROM message_variants
    """

data class MessageContentRow(
    @Embedded val message: MessageEntity,
    val sectionsByteCount: Long,
    val sectionsBytes: ByteArray,
    val searchTextByteCount: Long,
    val searchTextBytes: ByteArray,
)

data class MessageVariantContentRow(
    @Embedded val variant: MessageVariantEntity,
    val sectionsByteCount: Long,
    val sectionsBytes: ByteArray,
    val searchTextByteCount: Long,
    val searchTextBytes: ByteArray,
)

data class ChatContentCharacterCount(
    val chatId: String,
    val contentCharacterCount: Long,
)

/** Reads message text in bounded rows so a single large message cannot overflow CursorWindow. */
@Dao
abstract class ChatContentDao {
    @Query(
        "SELECT messageTimestamp, COUNT(*) AS variantCount FROM message_variants" +
            " WHERE chatId = :chatId AND messageTimestamp >= :minTimestamp" +
            " AND messageTimestamp <= :maxTimestamp GROUP BY messageTimestamp"
    )
    protected abstract suspend fun queryVariantCountsForMessageRange(
        chatId: String,
        minTimestamp: Long,
        maxTimestamp: Long,
    ): List<MessageVariantCount>

    @Query(
        MESSAGE_VARIANT_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND messageTimestamp >= :minTimestamp" +
            " AND messageTimestamp <= :maxTimestamp" +
            " AND EXISTS (SELECT 1 FROM messages" +
            " WHERE messages.chatId = message_variants.chatId" +
            " AND messages.timestamp = message_variants.messageTimestamp" +
            " AND messages.selectedVariantIndex = message_variants.variantIndex)" +
            " ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    protected abstract suspend fun querySelectedVariantsForMessageRange(
        chatId: String,
        minTimestamp: Long,
        maxTimestamp: Long,
    ): List<MessageVariantContentRow>

    @Query(MESSAGE_CONTENT_ROW_QUERY + " WHERE chatId = :chatId ORDER BY timestamp ASC")
    protected abstract suspend fun queryMessagesForChat(chatId: String): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND timestamp >= :startTimestampInclusive ORDER BY timestamp ASC"
    )
    protected abstract suspend fun queryMessagesForChatFromTimestampAsc(
        chatId: String,
        startTimestampInclusive: Long,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND timestamp >= :startTimestampInclusive" +
            " AND timestamp <= :endTimestampInclusive ORDER BY timestamp ASC"
    )
    protected abstract suspend fun queryMessagesForChatWindowAsc(
        chatId: String,
        startTimestampInclusive: Long,
        endTimestampInclusive: Long,
    ): List<MessageContentRow>

    @Query(MESSAGE_CONTENT_ROW_QUERY + " WHERE chatId = :chatId ORDER BY timestamp ASC LIMIT :limit")
    protected abstract suspend fun queryMessagesForChatAsc(
        chatId: String,
        limit: Int,
    ): List<MessageContentRow>

    @Query(MESSAGE_CONTENT_ROW_QUERY + " WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    protected abstract suspend fun queryMessagesForChatDesc(
        chatId: String,
        limit: Int,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset"
    )
    protected abstract suspend fun queryMessagesForChatAscRange(
        chatId: String,
        offset: Int,
        limit: Int,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset"
    )
    protected abstract suspend fun queryMessagesForChatDescRange(
        chatId: String,
        offset: Int,
        limit: Int,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND timestamp > :afterTimestampExclusive" +
            " ORDER BY timestamp ASC LIMIT :limit"
    )
    protected abstract suspend fun queryMessagesForChatAfterTimestampExclusiveAsc(
        chatId: String,
        afterTimestampExclusive: Long,
        limit: Int,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId" +
            " AND (:afterTimestampExclusive IS NULL OR timestamp > :afterTimestampExclusive)" +
            " AND (:beforeTimestampExclusive IS NULL OR timestamp < :beforeTimestampExclusive)" +
            " AND (:upToTimestampInclusive IS NULL OR timestamp <= :upToTimestampInclusive)" +
            " ORDER BY timestamp ASC"
    )
    protected abstract suspend fun queryMessagesForChatInRangeAsc(
        chatId: String,
        afterTimestampExclusive: Long?,
        beforeTimestampExclusive: Long?,
        upToTimestampInclusive: Long?,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND timestamp <= :maxTimestamp" +
            " ORDER BY timestamp DESC LIMIT :limit"
    )
    protected abstract suspend fun queryMessagesForChatBeforeTimestampDesc(
        chatId: String,
        maxTimestamp: Long,
        limit: Int,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND timestamp < :beforeTimestampExclusive" +
            " ORDER BY timestamp DESC LIMIT :limit"
    )
    protected abstract suspend fun queryMessagesForChatBeforeTimestampExclusiveDesc(
        chatId: String,
        beforeTimestampExclusive: Long,
        limit: Int,
    ): List<MessageContentRow>

    @Query(
        MESSAGE_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND timestamp = :timestamp LIMIT 1"
    )
    protected abstract suspend fun queryMessageByTimestamp(
        chatId: String,
        timestamp: Long,
    ): MessageContentRow?

    @Query(
        "SELECT CAST(SUBSTR(CAST(sections AS BLOB), :startByte, :byteCount) AS BLOB)" +
            " FROM messages WHERE messageId = :messageId"
    )
    protected abstract suspend fun queryMessageContentChunk(
        messageId: Long,
        startByte: Long,
        byteCount: Int,
    ): ByteArray?

    @Query(
        MESSAGE_VARIANT_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    protected abstract suspend fun queryVariantsForChat(chatId: String): List<MessageVariantContentRow>

    @Query(
        MESSAGE_VARIANT_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId" +
            " AND messageTimestamp >= :minTimestamp" +
            " AND messageTimestamp <= :maxTimestamp" +
            " ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    protected abstract suspend fun queryVariantsForMessageRange(
        chatId: String,
        minTimestamp: Long,
        maxTimestamp: Long,
    ): List<MessageVariantContentRow>

    @Query(
        MESSAGE_VARIANT_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp" +
            " ORDER BY variantIndex ASC"
    )
    protected abstract suspend fun queryVariantsForMessage(
        chatId: String,
        messageTimestamp: Long,
    ): List<MessageVariantContentRow>

    @Query(
        MESSAGE_VARIANT_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp" +
            " AND variantIndex = :variantIndex LIMIT 1"
    )
    protected abstract suspend fun queryVariantForMessage(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): MessageVariantContentRow?

    @Query(
        "SELECT CAST(SUBSTR(CAST(sections AS BLOB), :startByte, :byteCount) AS BLOB)" +
            " FROM message_variants WHERE variantId = :variantId"
    )
    protected abstract suspend fun queryMessageVariantContentChunk(
        variantId: Long,
        startByte: Long,
        byteCount: Int,
    ): ByteArray?

    @Query(
        """
        SELECT
            chats.id AS chatId,
            COALESCE(
                SUM(
                    CASE
                        WHEN messages.selectedVariantIndex = 0 THEN LENGTH(messages.searchText)
                        ELSE LENGTH(selectedVariant.searchText)
                    END
                ),
                0
            ) AS contentCharacterCount
        FROM chats
        LEFT JOIN messages
            ON messages.chatId = chats.id
        LEFT JOIN message_variants AS selectedVariant
            ON selectedVariant.chatId = messages.chatId
            AND selectedVariant.messageTimestamp = messages.timestamp
            AND selectedVariant.variantIndex = messages.selectedVariantIndex
        GROUP BY chats.id
        """
    )
    abstract suspend fun getSelectedContentCharacterCountsByChat(): List<ChatContentCharacterCount>

    @Query(
        """
        SELECT
            chats.id AS chatId,
            COALESCE(
                (SELECT SUM(LENGTH(sections)) FROM messages WHERE messages.chatId = chats.id),
                0
            ) + COALESCE(
                (SELECT SUM(LENGTH(sections)) FROM message_variants WHERE message_variants.chatId = chats.id),
                0
            ) AS contentCharacterCount
        FROM chats
        """
    )
    abstract suspend fun getArchiveContentCharacterCountsByChat(): List<ChatContentCharacterCount>

    @Transaction
    open suspend fun getMessagesForChat(chatId: String): List<MessageEntity> =
        materializeMessages(queryMessagesForChat(chatId))

    @Transaction
    open suspend fun getMessagesForChatFromTimestampAsc(
        chatId: String,
        startTimestampInclusive: Long,
    ): List<MessageEntity> =
        materializeMessages(queryMessagesForChatFromTimestampAsc(chatId, startTimestampInclusive))

    @Transaction
    open suspend fun getMessagesForChatWindowAsc(
        chatId: String,
        startTimestampInclusive: Long,
        endTimestampInclusive: Long,
    ): List<MessageEntity> =
        materializeMessages(
            queryMessagesForChatWindowAsc(chatId, startTimestampInclusive, endTimestampInclusive)
        )

    @Transaction
    open suspend fun getMessagesForChatAsc(chatId: String, limit: Int): List<MessageEntity> =
        materializeMessages(queryMessagesForChatAsc(chatId, limit))

    @Transaction
    open suspend fun getMessagesForChatDesc(chatId: String, limit: Int): List<MessageEntity> =
        materializeMessages(queryMessagesForChatDesc(chatId, limit))

    @Transaction
    open suspend fun getMessagesForChatAscRange(
        chatId: String,
        offset: Int,
        limit: Int,
    ): List<MessageEntity> = materializeMessages(queryMessagesForChatAscRange(chatId, offset, limit))

    @Transaction
    open suspend fun getMessagesForChatDescRange(
        chatId: String,
        offset: Int,
        limit: Int,
    ): List<MessageEntity> = materializeMessages(queryMessagesForChatDescRange(chatId, offset, limit))

    @Transaction
    open suspend fun getMessagesForChatAfterTimestampExclusiveAsc(
        chatId: String,
        afterTimestampExclusive: Long,
        limit: Int,
    ): List<MessageEntity> =
        materializeMessages(
            queryMessagesForChatAfterTimestampExclusiveAsc(chatId, afterTimestampExclusive, limit)
        )

    @Transaction
    open suspend fun getMessagesForChatInRangeAsc(
        chatId: String,
        afterTimestampExclusive: Long?,
        beforeTimestampExclusive: Long?,
        upToTimestampInclusive: Long?,
    ): List<MessageEntity> =
        materializeMessages(
            queryMessagesForChatInRangeAsc(
                chatId,
                afterTimestampExclusive,
                beforeTimestampExclusive,
                upToTimestampInclusive,
            )
        )

    @Transaction
    open suspend fun getMessagesForChatBeforeTimestampDesc(
        chatId: String,
        maxTimestamp: Long,
        limit: Int,
    ): List<MessageEntity> =
        materializeMessages(queryMessagesForChatBeforeTimestampDesc(chatId, maxTimestamp, limit))

    @Transaction
    open suspend fun getMessagesForChatBeforeTimestampExclusiveDesc(
        chatId: String,
        beforeTimestampExclusive: Long,
        limit: Int,
    ): List<MessageEntity> =
        materializeMessages(
            queryMessagesForChatBeforeTimestampExclusiveDesc(chatId, beforeTimestampExclusive, limit)
        )

    @Transaction
    open suspend fun getMessageByTimestamp(chatId: String, timestamp: Long): MessageEntity? =
        queryMessageByTimestamp(chatId, timestamp)?.let { materializeMessage(it) }

    @Transaction
    open suspend fun getVariantsForChat(chatId: String): List<MessageVariantEntity> =
        materializeVariants(queryVariantsForChat(chatId))

    @Transaction
    open suspend fun getVariantsForMessages(
        chatId: String,
        messageTimestamps: List<Long>,
    ): List<MessageVariantEntity> {
        if (messageTimestamps.isEmpty()) {
            return emptyList()
        }

        val requestedTimestamps = messageTimestamps.toHashSet()
        val minTimestamp = messageTimestamps.minOrNull() ?: return emptyList()
        val maxTimestamp = messageTimestamps.maxOrNull() ?: return emptyList()

        // Preserve exact timestamp-set semantics while avoiding Room expanding a large list into SQLite bind variables.
        val rows =
            queryVariantsForMessageRange(
                chatId = chatId,
                minTimestamp = minTimestamp,
                maxTimestamp = maxTimestamp,
            ).filter { row -> row.variant.messageTimestamp in requestedTimestamps }
        return materializeVariants(rows)
    }

    @Transaction
    open suspend fun getVariantsForMessage(
        chatId: String,
        messageTimestamp: Long,
    ): List<MessageVariantEntity> =
        materializeVariants(queryVariantsForMessage(chatId, messageTimestamp))

    @Transaction
    open suspend fun getVariantForMessage(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): MessageVariantEntity? =
        queryVariantForMessage(chatId, messageTimestamp, variantIndex)?.let {
            materializeVariant(it)
        }

    @Query("SELECT CAST(SUBSTR(CAST(searchText AS BLOB), :startByte, :byteCount) AS BLOB) FROM messages WHERE messageId = :id")
    protected abstract suspend fun queryMessageSearchTextChunk(
        id: Long, startByte: Long, byteCount: Int,
    ): ByteArray?

    @Query("SELECT CAST(SUBSTR(CAST(searchText AS BLOB), :startByte, :byteCount) AS BLOB) FROM message_variants WHERE variantId = :id")
    protected abstract suspend fun queryVariantSearchTextChunk(
        id: Long, startByte: Long, byteCount: Int,
    ): ByteArray?

    /** 事务内读取基础消息、候选数量及选中正文，确保切换候选不会拼出混合快照。 */
    @Transaction
    open suspend fun getRuntimeMessagesForChatWindow(
        chatId: String,
        startTimestampInclusive: Long?,
        endTimestampInclusive: Long?,
    ): RuntimeChatMessageSnapshot {
        // 复用已有时间窗口查询，保持总结起点、重生成截止位置和索引使用方式相同。
        val rows = when {
            startTimestampInclusive != null && endTimestampInclusive != null ->
                queryMessagesForChatWindowAsc(chatId, startTimestampInclusive, endTimestampInclusive)
            startTimestampInclusive != null ->
                queryMessagesForChatFromTimestampAsc(chatId, startTimestampInclusive)
            endTimestampInclusive != null ->
                queryMessagesForChatInRangeAsc(chatId, null, null, endTimestampInclusive)
            else -> queryMessagesForChat(chatId)
        }
        val messages = materializeMessages(rows)
        if (messages.isEmpty()) {
            return RuntimeChatMessageSnapshot(emptyList(), emptyList(), emptyList())
        }
        val minTimestamp = messages.first().timestamp
        val maxTimestamp = messages.last().timestamp
        val variantCounts = queryVariantCountsForMessageRange(chatId, minTimestamp, maxTimestamp)
        val selectedVariants = if (messages.any { it.selectedVariantIndex != 0 }) {
            materializeVariants(querySelectedVariantsForMessageRange(chatId, minTimestamp, maxTimestamp))
        } else {
            emptyList()
        }
        return RuntimeChatMessageSnapshot(messages, selectedVariants, variantCounts)
    }

    private suspend fun materializeMessages(rows: List<MessageContentRow>): List<MessageEntity> =
        rows.map { materializeMessage(it) }

    private suspend fun materializeMessage(row: MessageContentRow): MessageEntity {
        val sections = readUtf8Content(row.sectionsByteCount, row.sectionsBytes, "messages.sections:${row.message.messageId}") { start, count ->
            queryMessageContentChunk(row.message.messageId, start, count)
        }
        val searchText = readUtf8Content(row.searchTextByteCount, row.searchTextBytes, "messages.searchText:${row.message.messageId}") { start, count ->
            queryMessageSearchTextChunk(row.message.messageId, start, count)
        }
        return row.message.copy(sections = sections, searchText = searchText)
    }

    private suspend fun materializeVariants(
        rows: List<MessageVariantContentRow>
    ): List<MessageVariantEntity> = rows.map { materializeVariant(it) }

    private suspend fun materializeVariant(row: MessageVariantContentRow): MessageVariantEntity {
        val sections = readUtf8Content(row.sectionsByteCount, row.sectionsBytes, "message_variants.sections:${row.variant.variantId}") { start, count ->
            queryMessageVariantContentChunk(row.variant.variantId, start, count)
        }
        val searchText = readUtf8Content(row.searchTextByteCount, row.searchTextBytes, "message_variants.searchText:${row.variant.variantId}") { start, count ->
            queryVariantSearchTextChunk(row.variant.variantId, start, count)
        }
        return row.variant.copy(sections = sections, searchText = searchText)
    }

    /** 先拼接完整字节再解码，允许一个汉字或表情跨越分块边界。 */
    private suspend fun readUtf8Content(
        byteCount: Long,
        firstChunk: ByteArray,
        field: String,
        fetchChunk: suspend (startByte: Long, byteCount: Int) -> ByteArray?,
    ): String {
        check(byteCount in 0..Int.MAX_VALUE.toLong()) { "内容长度超出可读取范围：$field ($byteCount)" }
        check(firstChunk.size.toLong() == minOf(byteCount, CONTENT_CHUNK_BYTE_COUNT.toLong())) {
            "首块长度与存储长度不一致：$field"
        }
        if (byteCount <= CONTENT_CHUNK_BYTE_COUNT) return firstChunk.toString(Charsets.UTF_8)
        val content = ByteArray(byteCount.toInt())
        firstChunk.copyInto(content)
        var offset = firstChunk.size
        while (offset < content.size) {
            val count = minOf(CONTENT_CHUNK_BYTE_COUNT, content.size - offset)
            val chunk = checkNotNull(fetchChunk(offset.toLong() + 1L, count)) {
                "分块读取时消息已不存在：$field"
            }
            check(chunk.size == count) { "分块长度与存储长度不一致：$field ($offset)" }
            chunk.copyInto(content, offset)
            offset += chunk.size
        }
        return content.toString(Charsets.UTF_8)
    }
}

package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity

// SQLite 的 LENGTH/SUBSTR 把 TEXT 当字符处理，遇到第一个 U+0000 就截断；PDF 提取文本里的
// NUL 会让消息被误判为只有前几个字符。这里改为按 BLOB 字节分段：字节偏移不受 NUL 影响，
// 且每个返回行最多 64 KiB，仍然远低于 CursorWindow 容量上限。
private const val CONTENT_CHUNK_BYTE_COUNT = 65_536

private const val MESSAGE_CONTENT_ROW_QUERY =
    """
    SELECT
        messageId,
        chatId,
        sender,
        '' AS content,
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
        LENGTH(CAST(content AS BLOB)) AS contentByteCount,
        COALESCE(
            CAST(SUBSTR(CAST(content AS BLOB), 1, $CONTENT_CHUNK_BYTE_COUNT) AS BLOB),
            X''
        ) AS contentBytes
    FROM messages
    """

private const val MESSAGE_VARIANT_CONTENT_ROW_QUERY =
    """
    SELECT
        variantId,
        chatId,
        messageTimestamp,
        variantIndex,
        '' AS content,
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
        LENGTH(CAST(content AS BLOB)) AS contentByteCount,
        COALESCE(
            CAST(SUBSTR(CAST(content AS BLOB), 1, $CONTENT_CHUNK_BYTE_COUNT) AS BLOB),
            X''
        ) AS contentBytes
    FROM message_variants
    """

data class MessageContentRow(
    @Embedded val message: MessageEntity,
    val contentByteCount: Long,
    val contentBytes: ByteArray,
)

data class MessageVariantContentRow(
    @Embedded val variant: MessageVariantEntity,
    val contentByteCount: Long,
    val contentBytes: ByteArray,
)

data class ChatContentCharacterCount(
    val chatId: String,
    val contentCharacterCount: Long,
)

/** Reads message text in bounded rows so a single large message cannot overflow CursorWindow. */
@Dao
abstract class ChatContentDao {
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
        "SELECT CAST(SUBSTR(CAST(content AS BLOB), :startByte, :byteCount) AS BLOB)" +
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
        "SELECT CAST(SUBSTR(CAST(content AS BLOB), :startByte, :byteCount) AS BLOB)" +
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
                        WHEN messages.selectedVariantIndex = 0 THEN LENGTH(messages.content)
                        ELSE LENGTH(selectedVariant.content)
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

    private suspend fun materializeMessages(rows: List<MessageContentRow>): List<MessageEntity> =
        rows.map { materializeMessage(it) }

    private suspend fun materializeMessage(row: MessageContentRow): MessageEntity {
        val contentBytes =
            if (row.contentByteCount <= CONTENT_CHUNK_BYTE_COUNT) {
                check(row.contentBytes.size.toLong() == row.contentByteCount) {
                    "Message content byte count mismatch: messageId=${row.message.messageId}" +
                        " expected=${row.contentByteCount} actual=${row.contentBytes.size}"
                }
                row.contentBytes
            } else {
                readContentBytes(
                    contentByteCount = row.contentByteCount,
                    firstChunk = row.contentBytes,
                    fetchChunk = { startByte, byteCount ->
                        queryMessageContentChunk(row.message.messageId, startByte, byteCount)
                    },
                    missingRowMessage =
                        "Message disappeared while reading content: messageId=${row.message.messageId}",
                    emptyChunkMessage =
                        "Message content ended before its recorded length: messageId=${row.message.messageId}",
                    byteCountMismatchMessage =
                        "Message content byte count mismatch: messageId=${row.message.messageId}",
                )
            }
        return row.message.copy(content = decodeUtf8(contentBytes))
    }

    private suspend fun materializeVariants(
        rows: List<MessageVariantContentRow>
    ): List<MessageVariantEntity> = rows.map { materializeVariant(it) }

    private suspend fun materializeVariant(row: MessageVariantContentRow): MessageVariantEntity {
        val contentBytes =
            if (row.contentByteCount <= CONTENT_CHUNK_BYTE_COUNT) {
                check(row.contentBytes.size.toLong() == row.contentByteCount) {
                    "Message variant content byte count mismatch: variantId=${row.variant.variantId}" +
                        " expected=${row.contentByteCount} actual=${row.contentBytes.size}"
                }
                row.contentBytes
            } else {
                readContentBytes(
                    contentByteCount = row.contentByteCount,
                    firstChunk = row.contentBytes,
                    fetchChunk = { startByte, byteCount ->
                        queryMessageVariantContentChunk(row.variant.variantId, startByte, byteCount)
                    },
                    missingRowMessage =
                        "Message variant disappeared while reading content: variantId=${row.variant.variantId}",
                    emptyChunkMessage =
                        "Message variant content ended before its recorded length: variantId=${row.variant.variantId}",
                    byteCountMismatchMessage =
                        "Message variant content byte count mismatch: variantId=${row.variant.variantId}",
                )
            }
        return row.variant.copy(content = decodeUtf8(contentBytes))
    }

    private suspend fun readContentBytes(
        contentByteCount: Long,
        firstChunk: ByteArray,
        fetchChunk: suspend (startByte: Long, byteCount: Int) -> ByteArray?,
        missingRowMessage: String,
        emptyChunkMessage: String,
        byteCountMismatchMessage: String,
    ): ByteArray {
        check(contentByteCount <= Int.MAX_VALUE) {
            "$byteCountMismatchMessage: stored content is too large to materialize in memory"
        }
        check(firstChunk.size.toLong() == minOf(contentByteCount, CONTENT_CHUNK_BYTE_COUNT.toLong())) {
            "$byteCountMismatchMessage first chunk size=${firstChunk.size} recorded=$contentByteCount"
        }
        val content = ByteArray(contentByteCount.toInt())
        firstChunk.copyInto(content)
        var offset = firstChunk.size
        var startByte = CONTENT_CHUNK_BYTE_COUNT.toLong() + 1L
        while (startByte <= contentByteCount) {
            val chunk = checkNotNull(fetchChunk(startByte, CONTENT_CHUNK_BYTE_COUNT)) {
                missingRowMessage
            }
            check(chunk.isNotEmpty()) { emptyChunkMessage }
            check(chunk.size <= content.size - offset) {
                "$byteCountMismatchMessage chunk overflow at byte=$startByte"
            }
            chunk.copyInto(content, offset)
            offset += chunk.size
            startByte += CONTENT_CHUNK_BYTE_COUNT
        }
        check(offset == content.size) {
            "$byteCountMismatchMessage expected=$contentByteCount actual=$offset"
        }
        return content
    }

    private fun decodeUtf8(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8)
}

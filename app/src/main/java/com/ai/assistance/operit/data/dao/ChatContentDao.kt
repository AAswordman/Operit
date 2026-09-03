package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity

// SQLite LENGTH/SUBSTR count characters, and this bound keeps every returned text row well below CursorWindow size.
private const val CONTENT_CHUNK_CHARACTER_COUNT = 65_536

private const val MESSAGE_CONTENT_ROW_QUERY =
    """
    SELECT
        messageId,
        chatId,
        sender,
        SUBSTR(content, 1, $CONTENT_CHUNK_CHARACTER_COUNT) AS content,
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
        LENGTH(content) AS contentCharacterCount
    FROM messages
    """

private const val MESSAGE_VARIANT_CONTENT_ROW_QUERY =
    """
    SELECT
        variantId,
        chatId,
        messageTimestamp,
        variantIndex,
        SUBSTR(content, 1, $CONTENT_CHUNK_CHARACTER_COUNT) AS content,
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
        LENGTH(content) AS contentCharacterCount
    FROM message_variants
    """

data class MessageContentRow(
    @Embedded val message: MessageEntity,
    val contentCharacterCount: Long,
)

data class MessageVariantContentRow(
    @Embedded val variant: MessageVariantEntity,
    val contentCharacterCount: Long,
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
        "SELECT SUBSTR(content, :startCharacter, :characterCount)" +
            " FROM messages WHERE messageId = :messageId"
    )
    protected abstract suspend fun queryMessageContentChunk(
        messageId: Long,
        startCharacter: Long,
        characterCount: Int,
    ): String?

    @Query(
        MESSAGE_VARIANT_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    protected abstract suspend fun queryVariantsForChat(chatId: String): List<MessageVariantContentRow>

    @Query(
        MESSAGE_VARIANT_CONTENT_ROW_QUERY +
            " WHERE chatId = :chatId AND messageTimestamp IN (:messageTimestamps)" +
            " ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    protected abstract suspend fun queryVariantsForMessages(
        chatId: String,
        messageTimestamps: List<Long>,
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
        "SELECT SUBSTR(content, :startCharacter, :characterCount)" +
            " FROM message_variants WHERE variantId = :variantId"
    )
    protected abstract suspend fun queryMessageVariantContentChunk(
        variantId: Long,
        startCharacter: Long,
        characterCount: Int,
    ): String?

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

    @Query(
        """
        SELECT
            chats.id AS chatId,
            COALESCE(
                (SELECT SUM(LENGTH(content)) FROM messages WHERE messages.chatId = chats.id),
                0
            ) + COALESCE(
                (SELECT SUM(LENGTH(content)) FROM message_variants WHERE message_variants.chatId = chats.id),
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
    ): List<MessageVariantEntity> =
        materializeVariants(queryVariantsForMessages(chatId, messageTimestamps))

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
        if (row.contentCharacterCount <= CONTENT_CHUNK_CHARACTER_COUNT) {
            return row.message
        }

        val content = StringBuilder(row.message.content)
        var startCharacter = CONTENT_CHUNK_CHARACTER_COUNT.toLong() + 1L
        while (startCharacter <= row.contentCharacterCount) {
            val chunk =
                checkNotNull(
                    queryMessageContentChunk(
                        row.message.messageId,
                        startCharacter,
                        CONTENT_CHUNK_CHARACTER_COUNT,
                    )
                ) {
                    "Message disappeared while reading content: messageId=${row.message.messageId}"
                }
            check(chunk.isNotEmpty()) {
                "Message content ended before its recorded length: messageId=${row.message.messageId}"
            }
            content.append(chunk)
            startCharacter += CONTENT_CHUNK_CHARACTER_COUNT
        }
        return row.message.copy(content = content.toString())
    }

    private suspend fun materializeVariants(
        rows: List<MessageVariantContentRow>
    ): List<MessageVariantEntity> = rows.map { materializeVariant(it) }

    private suspend fun materializeVariant(row: MessageVariantContentRow): MessageVariantEntity {
        if (row.contentCharacterCount <= CONTENT_CHUNK_CHARACTER_COUNT) {
            return row.variant
        }

        val content = StringBuilder(row.variant.content)
        var startCharacter = CONTENT_CHUNK_CHARACTER_COUNT.toLong() + 1L
        while (startCharacter <= row.contentCharacterCount) {
            val chunk =
                checkNotNull(
                    queryMessageVariantContentChunk(
                        row.variant.variantId,
                        startCharacter,
                        CONTENT_CHUNK_CHARACTER_COUNT,
                    )
                ) {
                    "Message variant disappeared while reading content: variantId=${row.variant.variantId}"
                }
            check(chunk.isNotEmpty()) {
                "Message variant content ended before its recorded length: variantId=${row.variant.variantId}"
            }
            content.append(chunk)
            startCharacter += CONTENT_CHUNK_CHARACTER_COUNT
        }
        return row.variant.copy(content = content.toString())
    }
}

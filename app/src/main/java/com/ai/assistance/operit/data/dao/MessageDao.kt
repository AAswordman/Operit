package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.model.MessageEntity

/** 消息DAO接口，定义对消息表的数据访问方法 */
@Dao
interface MessageDao {
    /** 获取消息总数 */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalMessageCount(): Int

    @Query(
        "SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND (:upToTimestampInclusive IS NULL OR timestamp <= :upToTimestampInclusive)"
    )
    suspend fun countMessagesForChatUpToTimestamp(
        chatId: String,
        upToTimestampInclusive: Long?,
    ): Int

    @Query(
        """
        SELECT
            (
                SELECT COUNT(*)
                FROM messages AS earlier
                WHERE earlier.chatId = messages.chatId
                    AND earlier.timestamp < messages.timestamp
            ) AS messageIndex,
            timestamp AS timestamp,
            sender AS sender,
            CASE
                WHEN sender = 'user' AND displayMode = 'HIDDEN_PLACEHOLDER' THEN ''
                ELSE SUBSTR(searchText, 1, :previewCharCount)
            END AS previewContent,
            CASE
                WHEN sender = 'user' AND displayMode = 'HIDDEN_PLACEHOLDER' THEN 0
                ELSE LENGTH(searchText)
            END AS contentLength,
            displayMode AS displayMode,
            isFavorite AS isFavorite
        FROM messages
        WHERE chatId = :chatId
        ORDER BY timestamp ASC
        """
    )
    suspend fun getLocatorPreviewsForChat(
        chatId: String,
        previewCharCount: Int,
    ): List<ChatMessageLocatorPreview>

    @Query(
        """
        SELECT
            (
                SELECT COUNT(*)
                FROM messages AS earlier
                WHERE earlier.chatId = messages.chatId
                    AND earlier.timestamp < messages.timestamp
            ) AS messageIndex,
            timestamp AS timestamp,
            sender AS sender,
            SUBSTR(
                searchText,
                MAX(1, INSTR(LOWER(searchText), LOWER(:query)) - (:previewCharCount / 2)),
                :previewCharCount
            ) AS previewContent,
            LENGTH(searchText) AS contentLength,
            displayMode AS displayMode,
            isFavorite AS isFavorite
        FROM messages
        WHERE chatId = :chatId
            AND NOT (sender = 'user' AND displayMode = 'HIDDEN_PLACEHOLDER')
            AND INSTR(LOWER(searchText), LOWER(:query)) > 0
        ORDER BY timestamp ASC
        """
    )
    suspend fun searchLocatorPreviewsForChat(
        chatId: String,
        query: String,
        previewCharCount: Int,
    ): List<ChatMessageLocatorPreview>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId AND timestamp < :beforeTimestampExclusive LIMIT 1)"
    )
    suspend fun existsMessagesBeforeTimestamp(
        chatId: String,
        beforeTimestampExclusive: Long,
    ): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId AND timestamp > :afterTimestampExclusive LIMIT 1)"
    )
    suspend fun existsMessagesAfterTimestamp(
        chatId: String,
        afterTimestampExclusive: Long,
    ): Boolean

    @Query(
        "SELECT timestamp FROM messages WHERE chatId = :chatId AND sender = 'summary' ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun getLatestSummaryTimestamp(chatId: String): Long?

    @Query(
        "SELECT timestamp FROM messages WHERE chatId = :chatId AND sender = 'summary' AND timestamp < :beforeTimestampExclusive ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun getLatestSummaryTimestampBefore(
        chatId: String,
        beforeTimestampExclusive: Long,
    ): Long?

    @Query(
        "SELECT timestamp FROM messages WHERE chatId = :chatId AND sender = 'summary' AND timestamp <= :upToTimestampInclusive ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun getLatestSummaryTimestampUpTo(
        chatId: String,
        upToTimestampInclusive: Long,
    ): Long?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId AND sender = 'user' LIMIT 1)"
    )
    suspend fun existsUserMessage(chatId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId LIMIT 1)")
    suspend fun existsAnyMessage(chatId: String): Boolean

    @Query("SELECT chatId AS chatId, COUNT(*) AS count FROM messages GROUP BY chatId")
    suspend fun getMessageCountsByChatId(): List<ChatMessageCount>

    /** 插入单条消息并返回消息ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    /** 批量插入消息 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query(
        """
        INSERT INTO messages (
            chatId,
            sender,
            sections,
            searchText,
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
            isFavorite
        )
        SELECT
            :targetChatId,
            sender,
            sections,
            searchText,
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
            isFavorite
        FROM messages
        WHERE chatId = :sourceChatId
            AND (:upToTimestampInclusive IS NULL OR timestamp <= :upToTimestampInclusive)
        """
    )
    suspend fun copyMessagesToChat(
        sourceChatId: String,
        targetChatId: String,
        upToTimestampInclusive: Long?,
    )

    /** 更新消息内容 */
    @Query("UPDATE messages SET sections = :sections, searchText = :searchText WHERE messageId = :messageId")
    suspend fun updateMessageSections(messageId: Long, sections: String, searchText: String)

    /** 更新整条消息 */
    @Update
    suspend fun updateMessage(message: MessageEntity)

    /** 获取指定聊天中最大的序号 */
    @Query("SELECT MAX(orderIndex) FROM messages WHERE chatId = :chatId")
    suspend fun getMaxOrderIndex(chatId: String): Int?

    /** 删除指定聊天的所有消息 */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesForChat(chatId: String)

    /** 删除指定聊天中从某个时间戳开始的所有消息 */
    @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp >= :timestamp")
    suspend fun deleteMessagesFrom(chatId: String, timestamp: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp = :timestamp")
    suspend fun deleteMessageByTimestamp(chatId: String, timestamp: Long)

    @Query(
        "UPDATE messages SET selectedVariantIndex = :selectedVariantIndex WHERE chatId = :chatId AND timestamp = :timestamp"
    )
    suspend fun updateSelectedVariantIndex(
        chatId: String,
        timestamp: Long,
        selectedVariantIndex: Int,
    )

    @Query(
        "UPDATE messages SET isFavorite = :isFavorite WHERE chatId = :chatId AND timestamp = :timestamp"
    )
    suspend fun updateMessageFavorite(
        chatId: String,
        timestamp: Long,
        isFavorite: Boolean,
    )

    /** 查找包含特定关键词的聊天ID列表（不重复） */
    @Query("SELECT DISTINCT chatId FROM messages WHERE searchText LIKE '%' || :query || '%' ESCAPE '\\' COLLATE NOCASE")
    suspend fun searchChatIdsByContent(query: String): List<String>

    /** 批量重命名消息中的角色名 */
    @Query("UPDATE messages SET roleName = :newName WHERE roleName = :oldName")
    suspend fun renameRoleName(oldName: String, newName: String): Int
}

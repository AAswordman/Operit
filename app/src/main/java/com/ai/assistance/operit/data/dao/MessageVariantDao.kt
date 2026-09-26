package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.MessageVariantEntity

@Dao
interface MessageVariantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: MessageVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<MessageVariantEntity>)

    @Query(
        """
        INSERT INTO message_variants (
            chatId,
            messageTimestamp,
            variantIndex,
            sections,
            searchText,
            roleName,
            provider,
            modelName,
            inputTokens,
            outputTokens,
            cachedInputTokens,
            sentAt,
            outputDurationMs,
            waitDurationMs,
            completedAt
        )
        SELECT
            :targetChatId,
            messageTimestamp,
            variantIndex,
            sections,
            searchText,
            roleName,
            provider,
            modelName,
            inputTokens,
            outputTokens,
            cachedInputTokens,
            sentAt,
            outputDurationMs,
            waitDurationMs,
            completedAt
        FROM message_variants
        WHERE chatId = :sourceChatId
            AND (:upToTimestampInclusive IS NULL OR messageTimestamp <= :upToTimestampInclusive)
        """
    )
    suspend fun copyVariantsToChat(
        sourceChatId: String,
        targetChatId: String,
        upToTimestampInclusive: Long?,
    )

    @Update
    suspend fun updateVariant(variant: MessageVariantEntity)

    @Query(
        "DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp AND variantIndex = :variantIndex"
    )
    suspend fun deleteVariant(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    )

    @Query("DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp")
    suspend fun deleteVariantsForMessage(chatId: String, messageTimestamp: Long)

    @Query("DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp >= :messageTimestamp")
    suspend fun deleteVariantsFrom(chatId: String, messageTimestamp: Long)

    @Query("DELETE FROM message_variants WHERE chatId = :chatId")
    suspend fun deleteAllVariantsForChat(chatId: String)
}

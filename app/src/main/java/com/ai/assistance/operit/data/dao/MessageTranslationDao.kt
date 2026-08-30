package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.assistance.operit.data.model.MessageTranslationEntity

@Dao
interface MessageTranslationDao {
    @Query(
        """
        SELECT * FROM message_translations
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND targetLanguageCode = :targetLanguageCode
            AND sourceTextHash = :sourceTextHash
        LIMIT 1
        """
    )
    suspend fun getTranslation(
        chatId: String,
        messageTimestamp: Long,
        targetLanguageCode: String,
        sourceTextHash: String,
    ): MessageTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslation(translation: MessageTranslationEntity)
}

package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.MessageTranslationEntity

class MessageTranslationRepository private constructor(context: Context) {
    private val translationDao =
        AppDatabase.getDatabase(context.applicationContext).messageTranslationDao()

    suspend fun getCachedTranslation(
        chatId: String,
        messageTimestamp: Long,
        sourceText: String,
        targetLanguageCode: String,
    ): MessageTranslationEntity? {
        return translationDao.getTranslation(
            chatId = chatId,
            messageTimestamp = messageTimestamp,
            targetLanguageCode = targetLanguageCode,
            sourceTextHash = MessageTranslationCacheKey.sourceHash(sourceText),
        )
    }

    suspend fun saveTranslation(
        chatId: String,
        messageTimestamp: Long,
        sourceText: String,
        targetLanguageCode: String,
        translatedText: String,
    ) {
        translationDao.upsertTranslation(
            MessageTranslationEntity(
                chatId = chatId,
                messageTimestamp = messageTimestamp,
                targetLanguageCode = targetLanguageCode,
                sourceTextHash = MessageTranslationCacheKey.sourceHash(sourceText),
                translatedText = translatedText,
            )
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: MessageTranslationRepository? = null

        fun getInstance(context: Context): MessageTranslationRepository {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: MessageTranslationRepository(context.applicationContext).also {
                            INSTANCE = it
                        }
                }
        }
    }
}

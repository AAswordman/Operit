package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "message_translations",
    primaryKeys = ["chatId", "messageTimestamp", "targetLanguageCode"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("chatId")],
)
data class MessageTranslationEntity(
    val chatId: String,
    val messageTimestamp: Long,
    val targetLanguageCode: String,
    val sourceTextHash: String,
    val translatedText: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

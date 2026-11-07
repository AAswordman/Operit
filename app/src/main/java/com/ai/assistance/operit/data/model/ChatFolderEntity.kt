package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_folders",
    indices = [Index(value = ["parentFolderId", "displayOrder"])],
    foreignKeys = [
        ForeignKey(
            entity = ChatFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentFolderId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
)
data class ChatFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentFolderId: String?,
    val displayOrder: Long,
    val createdAt: Long,
)

const val SYSTEM_UNGROUPED_FOLDER_ID = "__operit_system_ungrouped__"
const val SYSTEM_UNGROUPED_FOLDER_NAME = "__ungrouped__"

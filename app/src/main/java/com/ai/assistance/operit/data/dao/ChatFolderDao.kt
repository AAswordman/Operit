package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.ChatFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatFolderDao {
    @Query("SELECT * FROM chat_folders ORDER BY displayOrder, createdAt, id")
    fun observeFolders(): Flow<List<ChatFolderEntity>>

    @Query("SELECT * FROM chat_folders ORDER BY displayOrder, createdAt, id")
    suspend fun getFolders(): List<ChatFolderEntity>

    @Query("SELECT * FROM chat_folders WHERE id = :folderId")
    suspend fun getFolder(folderId: String): ChatFolderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: ChatFolderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolders(folders: List<ChatFolderEntity>)

    @Update
    suspend fun updateFolder(folder: ChatFolderEntity)

    @Update
    suspend fun updateFolders(folders: List<ChatFolderEntity>)

    @Query("DELETE FROM chat_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)
}

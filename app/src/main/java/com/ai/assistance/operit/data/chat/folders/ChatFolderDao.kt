package com.ai.assistance.operit.data.chat.folders

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatFolderDao {
    @Query("SELECT * FROM chat_folders ORDER BY displayOrder, id")
    fun observeFolders(): Flow<List<ChatFolder>>

    @Query("SELECT * FROM chat_folders ORDER BY displayOrder, id")
    suspend fun getFolders(): List<ChatFolder>

    @Query("SELECT * FROM chat_folders WHERE id = :id")
    suspend fun getFolder(id: String): ChatFolder?

    @Insert
    suspend fun insert(folder: ChatFolder)

    @Upsert
    suspend fun upsert(folders: List<ChatFolder>)

    @Query("UPDATE chat_folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE chat_folders SET parentId = :parentId, displayOrder = :order WHERE id = :id")
    suspend fun move(id: String, parentId: String?, order: Long)

    @Query("DELETE FROM chat_folders WHERE id = :id")
    suspend fun delete(id: String)
    @Query("UPDATE chat_folders SET characterCardName = :targetCard, characterGroupId = :targetGroup WHERE characterCardName IS :sourceCard AND characterGroupId IS :sourceGroup")
    suspend fun rebind(sourceCard: String?, sourceGroup: String?, targetCard: String?, targetGroup: String?)
}

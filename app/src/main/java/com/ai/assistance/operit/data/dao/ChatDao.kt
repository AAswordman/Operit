package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.CharacterCardChatStats
import com.ai.assistance.operit.data.model.CharacterGroupChatStats
import kotlinx.coroutines.flow.Flow

/** 聊天DAO接口，定义对聊天表的数据访问方法 */
@Dao
interface ChatDao {
    /** 获取所有聊天，按显示顺序排列 */
    @Query("SELECT * FROM chats ORDER BY pinned DESC, displayOrder ASC")
    fun getAllChats(): Flow<List<ChatEntity>>

    /** 获取聊天总数 */
    @Query("SELECT COUNT(*) FROM chats")
    suspend fun getTotalChatCount(): Int

    /** 获取所有聊天（挂起函数版本） */
    @Query("SELECT * FROM chats ORDER BY pinned DESC, displayOrder ASC")
    suspend fun getAllChatsDirectly(): List<ChatEntity>

    /** 根据ID获取单个聊天 */
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    /** 插入或更新聊天 */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChat(chat: ChatEntity)

    /** 删除聊天 */
    @Query("DELETE FROM chats WHERE id = :chatId") suspend fun deleteChat(chatId: String)

    /** 更新聊天元数据 */
    @Query(
            "UPDATE chats SET updatedAt = :timestamp, title = :title, inputTokens = :inputTokens, outputTokens = :outputTokens, currentWindowSize = :currentWindowSize WHERE id = :chatId"
    )
    suspend fun updateChatMetadata(
            chatId: String,
            title: String,
            timestamp: Long,
            inputTokens: Long,
            outputTokens: Long,
            currentWindowSize: Long
    )

    /** 普通累计快照只增加计数，避免迟到的会话快照覆盖较新的统计。 */
    @Query(
        "UPDATE chats SET updatedAt = :timestamp, inputTokens = MAX(inputTokens, :inputTokens), outputTokens = MAX(outputTokens, :outputTokens), currentWindowSize = :currentWindowSize WHERE id = :chatId"
    )
    suspend fun updateChatTokenCounts(
        chatId: String,
        inputTokens: Long,
        outputTokens: Long,
        currentWindowSize: Long,
        timestamp: Long = System.currentTimeMillis(),
    )

    /** 刷新窗口时不修改累计用量。 */
    @Query("UPDATE chats SET updatedAt = :timestamp, currentWindowSize = :currentWindowSize WHERE id = :chatId")
    suspend fun updateChatContextWindow(
        chatId: String,
        currentWindowSize: Long,
        timestamp: Long = System.currentTimeMillis(),
    )

    /** 更新聊天标题 */
    @Query("UPDATE chats SET title = :title, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatTitle(chatId: String, title: String, timestamp: Long = System.currentTimeMillis())

    /** 更新聊天工作区 */
    @Query("UPDATE chats SET `workspace` = :workspace, `workspaceEnv` = :workspaceEnv, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatWorkspace(chatId: String, workspace: String?, workspaceEnv: String?, timestamp: Long = System.currentTimeMillis())

    /** 同时更新聊天标题和工作区 */
    @Query(
        "UPDATE chats SET title = :title, `workspace` = :workspace, `workspaceEnv` = :workspaceEnv, updatedAt = :timestamp WHERE id = :chatId"
    )
    suspend fun updateChatTitleAndWorkspace(
        chatId: String,
        title: String,
        workspace: String?,
        workspaceEnv: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    /** 更新聊天分组 */
    @Query("UPDATE chats SET `group` = :group, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatGroup(chatId: String, group: String?, timestamp: Long = System.currentTimeMillis())

    /** 更新聊天绑定的角色卡名称 */
    @Query("UPDATE chats SET characterCardName = :characterCardName, characterGroupId = NULL, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatCharacterCardName(chatId: String, characterCardName: String?, timestamp: Long = System.currentTimeMillis())

    /** 更新聊天绑定的群组角色卡ID */
    @Query("UPDATE chats SET characterCardName = NULL, characterGroupId = :characterGroupId, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatCharacterGroupId(chatId: String, characterGroupId: String?, timestamp: Long = System.currentTimeMillis())

    /** 同时更新聊天绑定的角色卡名称与群组ID */
    @Query(
        "UPDATE chats SET characterCardName = :characterCardName, characterGroupId = :characterGroupId, updatedAt = :timestamp WHERE id = :chatId"
    )
    suspend fun updateChatCharacterBinding(
        chatId: String,
        characterCardName: String?,
        characterGroupId: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    /** 更新聊天锁定状态 */
    @Query("UPDATE chats SET locked = :locked, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatLocked(chatId: String, locked: Boolean, timestamp: Long = System.currentTimeMillis())

    /** 更新聊天置顶状态 */
    @Query("UPDATE chats SET pinned = :pinned, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatPinned(chatId: String, pinned: Boolean, timestamp: Long = System.currentTimeMillis())

    /** 更新单个聊天的顺序和分组 */
    @Query("UPDATE chats SET displayOrder = :displayOrder, `group` = :group, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatOrderAndGroup(chatId: String, displayOrder: Long, group: String?, timestamp: Long = System.currentTimeMillis())

    /** 批量更新聊天的顺序和分组 */
    @Update
    suspend fun updateChats(chats: List<ChatEntity>)

    /** 重命名分组 */
    @Query("UPDATE chats SET `group` = :newName WHERE `group` = :oldName")
    suspend fun updateGroupName(oldName: String, newName: String)
    
    /** 重命名指定角色卡下的分组 */
    @Query("UPDATE chats SET `group` = :newName WHERE `group` = :oldName AND characterCardName = :characterCardName")
    suspend fun updateGroupNameForCharacter(oldName: String, newName: String, characterCardName: String)

    /** 删除分组下的所有聊天 */
    @Query("DELETE FROM chats WHERE `group` = :groupName AND locked = 0")
    suspend fun deleteChatsInGroup(groupName: String)
    
    /** 删除指定角色卡下分组的所有聊天 */
    @Query("DELETE FROM chats WHERE `group` = :groupName AND characterCardName = :characterCardName AND locked = 0")
    suspend fun deleteChatsInGroupForCharacter(groupName: String, characterCardName: String)

    /** 将分组下的所有聊天移动到"未分组" */
    @Query("UPDATE chats SET `group` = NULL, updatedAt = :timestamp WHERE `group` = :groupName")
    suspend fun removeGroupFromChats(groupName: String, timestamp: Long = System.currentTimeMillis())

    /** 将分组下的所有【锁定】聊天移动到"未分组"（用于删除分组但保留锁定聊天） */
    @Query("UPDATE chats SET `group` = NULL, updatedAt = :timestamp WHERE `group` = :groupName AND locked = 1")
    suspend fun removeGroupFromLockedChats(groupName: String, timestamp: Long = System.currentTimeMillis())
    
    /** 将指定角色卡下分组的所有聊天移动到"未分组" */
    @Query("UPDATE chats SET `group` = NULL, updatedAt = :timestamp WHERE `group` = :groupName AND characterCardName = :characterCardName")
    suspend fun removeGroupFromChatsForCharacter(groupName: String, characterCardName: String, timestamp: Long = System.currentTimeMillis())

    /** 将指定角色卡下分组的所有【锁定】聊天移动到"未分组"（用于删除分组但保留锁定聊天） */
    @Query("UPDATE chats SET `group` = NULL, updatedAt = :timestamp WHERE `group` = :groupName AND characterCardName = :characterCardName AND locked = 1")
    suspend fun removeGroupFromLockedChatsForCharacter(groupName: String, characterCardName: String, timestamp: Long = System.currentTimeMillis())

    /** 根据parentChatId获取所有分支对话 */
    @Query("SELECT * FROM chats WHERE parentChatId = :parentChatId ORDER BY pinned DESC, displayOrder ASC")
    suspend fun getBranchesByParentId(parentChatId: String): List<ChatEntity>

    /** 根据parentChatId获取所有分支对话（Flow版本） */
    @Query("SELECT * FROM chats WHERE parentChatId = :parentChatId ORDER BY pinned DESC, displayOrder ASC")
    fun getBranchesByParentIdFlow(parentChatId: String): Flow<List<ChatEntity>>

    /** 获取所有没有父对话的对话（即主对话） */
    @Query("SELECT * FROM chats WHERE parentChatId IS NULL ORDER BY pinned DESC, displayOrder ASC")
    suspend fun getMainChats(): List<ChatEntity>

    /** 获取所有没有父对话的对话（Flow版本） */
    @Query("SELECT * FROM chats WHERE parentChatId IS NULL ORDER BY pinned DESC, displayOrder ASC")
    fun getMainChatsFlow(): Flow<List<ChatEntity>>

    /** 根据角色卡名称过滤聊天（非默认角色卡：只显示该角色卡名称的对话） */
    @Query("SELECT * FROM chats WHERE characterCardName = :characterCardName AND characterGroupId IS NULL ORDER BY pinned DESC, displayOrder ASC")
    fun getChatsByCharacterCard(characterCardName: String): Flow<List<ChatEntity>>

    /** 根据群组ID过滤聊天 */
    @Query("SELECT * FROM chats WHERE characterGroupId = :characterGroupId ORDER BY pinned DESC, displayOrder ASC")
    fun getChatsByCharacterGroupId(characterGroupId: String): Flow<List<ChatEntity>>

    /** 根据角色卡名称过滤聊天（默认角色卡：显示该角色卡名称的对话 + 所有characterCardName为null的对话） */
    @Query("SELECT * FROM chats WHERE characterCardName = :characterCardName OR (characterCardName IS NULL AND characterGroupId IS NULL) ORDER BY pinned DESC, displayOrder ASC")
    fun getChatsByCharacterCardOrNull(characterCardName: String): Flow<List<ChatEntity>>

    /** 批量清理绑定特定角色卡名称的对话（将characterCardName设为null） */
    @Query("UPDATE chats SET characterCardName = NULL, updatedAt = :timestamp WHERE characterCardName = :characterCardName")
    suspend fun clearCharacterCardBinding(characterCardName: String, timestamp: Long = System.currentTimeMillis())

    /** 批量删除绑定特定角色卡名称的未锁定对话 */
    @Query("DELETE FROM chats WHERE characterCardName = :characterCardName AND locked = 0")
    suspend fun deleteUnlockedChatsByCharacterCardName(characterCardName: String): Int

    /** 批量删除未绑定角色卡的未锁定对话 */
    @Query("DELETE FROM chats WHERE characterCardName IS NULL AND characterGroupId IS NULL AND locked = 0")
    suspend fun deleteUnlockedUnboundChats(): Int

    /** 批量重命名角色卡绑定 */
    @Query("UPDATE chats SET characterCardName = :newName, updatedAt = :timestamp WHERE characterCardName = :oldName")
    suspend fun renameCharacterCardBinding(
            oldName: String,
            newName: String,
            timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 批量重命名角色群组绑定 */
    @Query("UPDATE chats SET characterCardName = NULL, characterGroupId = :targetGroupId, updatedAt = :timestamp WHERE characterGroupId = :sourceGroupId")
    suspend fun renameCharacterGroupBinding(
        sourceGroupId: String,
        targetGroupId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 将所有未绑定角色卡的聊天归类到指定角色卡 */
    @Query("UPDATE chats SET characterCardName = :newName, updatedAt = :timestamp WHERE characterCardName IS NULL AND characterGroupId IS NULL")
    suspend fun assignCharacterCardToUnbound(
            newName: String,
            timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 将所有未绑定角色群组的聊天归类到指定角色群组 */
    @Query("UPDATE chats SET characterCardName = NULL, characterGroupId = :targetGroupId, updatedAt = :timestamp WHERE characterGroupId IS NULL AND characterCardName IS NULL")
    suspend fun assignCharacterGroupToUnbound(
        targetGroupId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 批量为指定聊天更新角色卡绑定 */
    @Query("UPDATE chats SET characterCardName = :newName, characterGroupId = NULL, updatedAt = :timestamp WHERE id IN (:chatIds)")
    suspend fun updateCharacterCardForChats(
        chatIds: List<String>,
        newName: String?,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 批量为指定聊天更新角色群组绑定 */
    @Query("UPDATE chats SET characterCardName = NULL, characterGroupId = :characterGroupId, updatedAt = :timestamp WHERE id IN (:chatIds)")
    suspend fun updateCharacterGroupForChats(
        chatIds: List<String>,
        characterGroupId: String?,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 批量为指定聊天移除角色群组绑定 */
    @Query("UPDATE chats SET characterGroupId = NULL, updatedAt = :timestamp WHERE id IN (:chatIds)")
    suspend fun clearCharacterGroupForChats(
        chatIds: List<String>,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 批量清理绑定特定角色群组ID的对话（将characterGroupId设为null） */
    @Query("UPDATE chats SET characterGroupId = NULL, updatedAt = :timestamp WHERE characterGroupId = :sourceGroupId")
    suspend fun clearCharacterGroupBinding(
        sourceGroupId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 批量为指定聊天更新分组 */
    @Query("UPDATE chats SET `group` = :groupName, updatedAt = :timestamp WHERE id IN (:chatIds)")
    suspend fun updateGroupForChats(
        chatIds: List<String>,
        groupName: String?,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 获取按角色卡分组的聊天与消息统计 */
    @Query(
        """
        SELECT 
            c.characterCardName AS characterCardName,
            COUNT(c.id) AS chatCount,
            IFNULL(SUM(mc.messageCount), 0) AS messageCount
        FROM chats c
        LEFT JOIN (
            SELECT chatId, COUNT(*) AS messageCount
            FROM messages
            GROUP BY chatId
        ) mc ON c.id = mc.chatId
        WHERE c.characterGroupId IS NULL
        GROUP BY c.characterCardName
        """
    )
    fun getCharacterCardChatStats(): Flow<List<CharacterCardChatStats>>

    /** 获取按角色群组分组的聊天与消息统计 */
    @Query(
        """
        SELECT 
            c.characterGroupId AS characterGroupId,
            COUNT(c.id) AS chatCount,
            IFNULL(SUM(mc.messageCount), 0) AS messageCount
        FROM chats c
        LEFT JOIN (
            SELECT chatId, COUNT(*) AS messageCount
            FROM messages
            GROUP BY chatId
        ) mc ON c.id = mc.chatId
        WHERE c.characterCardName IS NULL
        GROUP BY c.characterGroupId
        """
    )
    fun getCharacterGroupChatStats(): Flow<List<CharacterGroupChatStats>>
}

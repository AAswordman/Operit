package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.personaCardChatDataStore by recoverablePreferencesDataStore(
    name = "persona_card_chat_history"
)

/**
 * 人设卡生成界面的对话历史管理器
 * 为每个角色卡提供单独的对话历史存储槽位
 */
class PersonaCardChatHistoryManager private constructor(private val context: Context) {
    
    private val dataStore = context.personaCardChatDataStore
    private val gson = Gson()
    
    companion object {
        @Volatile
        private var INSTANCE: PersonaCardChatHistoryManager? = null
        
        fun getInstance(context: Context): PersonaCardChatHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PersonaCardChatHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 聊天消息数据类
     */
    data class ChatMessage(
        val role: String, // "user" | "assistant"
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 获取指定角色卡的聊天历史Key
     */
    private fun getChatHistoryKey(characterCardId: String): Preferences.Key<String> {
        return stringPreferencesKey("chat_history_$characterCardId")
    }
    
    /**
     * 保存聊天历史
     */
    suspend fun saveChatHistory(characterCardId: String, messages: List<ChatMessage>) {
        dataStore.edit { preferences ->
            val json = gson.toJson(messages)
            preferences[getChatHistoryKey(characterCardId)] = json
        }
    }
    
    /**
     * 加载聊天历史
     */
    suspend fun loadChatHistory(characterCardId: String): List<ChatMessage> {
        val preferences = dataStore.data.first()
        val json = preferences[getChatHistoryKey(characterCardId)] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取聊天历史Flow
     */
    fun getChatHistoryFlow(characterCardId: String): Flow<List<ChatMessage>> {
        return dataStore.data.map { preferences ->
            val json = preferences[getChatHistoryKey(characterCardId)] ?: return@map emptyList()
            try {
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * 清空指定角色卡的聊天历史
     */
    suspend fun clearChatHistory(characterCardId: String) {
        dataStore.edit { preferences ->
            preferences.remove(getChatHistoryKey(characterCardId))
        }
    }
    
    /**
     * 清空所有聊天历史
     */
    suspend fun clearAllChatHistory() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

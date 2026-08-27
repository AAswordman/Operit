package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.R
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.promptTagDataStore by preferencesDataStore(
    name = "prompt_tags"
)

/**
 * 提示词标签管理器
 */
class PromptTagManager private constructor(private val context: Context) {

    private val dataStore = context.promptTagDataStore

    companion object {
        private val PROMPT_TAG_LIST = stringSetPreferencesKey("prompt_tag_list")

        @Volatile
        private var INSTANCE: PromptTagManager? = null

        /**
         * 获取全局单例实例
         */
        fun getInstance(context: Context): PromptTagManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PromptTagManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 标签列表流
    val tagListFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[PROMPT_TAG_LIST]?.toList() ?: emptyList()
    }

    val allTagsFlow: Flow<List<PromptTag>> = dataStore.data.map { preferences ->
        val tagIds = preferences[PROMPT_TAG_LIST]?.toList() ?: emptyList()
        tagIds.map { id ->
            getPromptTagFromPreferences(preferences, id)
        }.sortedByDescending { it.updatedAt }
    }

    // 获取标签流
    fun getPromptTagFlow(id: String): Flow<PromptTag> = dataStore.data.map { preferences ->
        getPromptTagFromPreferences(preferences, id)
    }

    // 从Preferences中获取标签
    private fun getPromptTagFromPreferences(preferences: Preferences, id: String): PromptTag {
        val nameKey = stringPreferencesKey("prompt_tag_${id}_name")
        val descriptionKey = stringPreferencesKey("prompt_tag_${id}_description")
        val promptContentKey = stringPreferencesKey("prompt_tag_${id}_prompt_content")
        val tagTypeKey = stringPreferencesKey("prompt_tag_${id}_tag_type")
        val createdAtKey = longPreferencesKey("prompt_tag_${id}_created_at")
        val updatedAtKey = longPreferencesKey("prompt_tag_${id}_updated_at")

        return PromptTag(
            id = id,
            name = preferences[nameKey] ?: context.getString(R.string.prompt_tag_unnamed),
            description = preferences[descriptionKey] ?: "",
            promptContent = preferences[promptContentKey] ?: "",
            tagType = try {
                TagType.valueOf(preferences[tagTypeKey] ?: TagType.CUSTOM.name)
            } catch (e: IllegalArgumentException) {
                TagType.CUSTOM
            },
            createdAt = preferences[createdAtKey] ?: System.currentTimeMillis(),
            updatedAt = preferences[updatedAtKey] ?: System.currentTimeMillis()
        )
    }

    // 创建标签
    suspend fun createPromptTag(
        name: String,
        description: String = "",
        promptContent: String = "",
        tagType: TagType = TagType.CUSTOM
    ): String {
        val id = UUID.randomUUID().toString()

        dataStore.edit { preferences ->
            // 添加到标签列表
            val currentList = preferences[PROMPT_TAG_LIST]?.toMutableSet() ?: mutableSetOf()
            currentList.add(id)
            preferences[PROMPT_TAG_LIST] = currentList

            // 设置标签数据
            val nameKey = stringPreferencesKey("prompt_tag_${id}_name")
            val descriptionKey = stringPreferencesKey("prompt_tag_${id}_description")
            val promptContentKey = stringPreferencesKey("prompt_tag_${id}_prompt_content")
            val tagTypeKey = stringPreferencesKey("prompt_tag_${id}_tag_type")
            val createdAtKey = longPreferencesKey("prompt_tag_${id}_created_at")
            val updatedAtKey = longPreferencesKey("prompt_tag_${id}_updated_at")

            preferences[nameKey] = name
            preferences[descriptionKey] = description
            preferences[promptContentKey] = promptContent
            preferences[tagTypeKey] = tagType.name
            preferences[createdAtKey] = System.currentTimeMillis()
            preferences[updatedAtKey] = System.currentTimeMillis()
        }

        return id
    }

    // 更新标签
    suspend fun updatePromptTag(
        id: String,
        name: String? = null,
        description: String? = null,
        promptContent: String? = null,
        tagType: TagType? = null
    ) {
        dataStore.edit { preferences ->
            name?.let { preferences[stringPreferencesKey("prompt_tag_${id}_name")] = it }
            description?.let { preferences[stringPreferencesKey("prompt_tag_${id}_description")] = it }
            promptContent?.let { preferences[stringPreferencesKey("prompt_tag_${id}_prompt_content")] = it }
            tagType?.let { preferences[stringPreferencesKey("prompt_tag_${id}_tag_type")] = it.name }

            // 更新修改时间
            preferences[longPreferencesKey("prompt_tag_${id}_updated_at")] = System.currentTimeMillis()
        }
    }

    // 删除标签
    suspend fun deletePromptTag(id: String) {
        dataStore.edit { preferences ->
            // 从列表中移除
            val currentList = preferences[PROMPT_TAG_LIST]?.toMutableSet() ?: mutableSetOf()
            currentList.remove(id)
            preferences[PROMPT_TAG_LIST] = currentList

            removeTagPreferenceKeys(preferences, id)
        }
    }

    // 获取所有标签
    suspend fun getAllTags(): List<PromptTag> {
        val tagIds = tagListFlow.first()
        return tagIds.mapNotNull { id ->
            try {
                getPromptTagFlow(id).first()
            } catch (e: Exception) {
                null
            }
        }
    }

    // 根据类型获取标签
    suspend fun getTagsByType(tagType: TagType): List<PromptTag> {
        return getAllTags().filter { it.tagType == tagType }
    }

    // 查找具有相同内容的标签（不包括标签标题）
    suspend fun findTagWithSameContent(promptContent: String): PromptTag? {
        return getAllTags().find { tag ->
            tag.promptContent.trim() == promptContent.trim()
        }
    }

    // 创建或复用标签（如果内容相同则复用现有标签）
    suspend fun createOrReusePromptTag(
        name: String,
        description: String = "",
        promptContent: String = "",
        tagType: TagType = TagType.CUSTOM
    ): String {
        val existingTag = findTagWithSameContent(promptContent)
        return if (existingTag != null) {
            existingTag.id
        } else {
            createPromptTag(name, description, promptContent, tagType)
        }
    }

    // 清理已移除的历史内置功能标签（chat/voice/desktop pet）
    suspend fun removeLegacyBuiltInTags() {
        dataStore.edit { preferences ->
            val currentList = preferences[PROMPT_TAG_LIST]?.toMutableSet() ?: mutableSetOf()
            val legacySystemTagIdsByFlag = currentList.filter { id ->
                preferences[booleanPreferencesKey("prompt_tag_${id}_is_system_tag")] == true
            }
            val legacySystemTagIdsByType = currentList.filter { id ->
                (preferences[stringPreferencesKey("prompt_tag_${id}_tag_type")] ?: "")
                    .startsWith("SYSTEM_")
            }
            val idsToRemove = (legacySystemTagIdsByFlag + legacySystemTagIdsByType).toSet()

            if (idsToRemove.isNotEmpty()) {
                currentList.removeAll(idsToRemove)
                preferences[PROMPT_TAG_LIST] = currentList
                idsToRemove.forEach { id ->
                    removeTagPreferenceKeys(preferences, id)
                }
            }

            // 清理残留的历史标记字段
            val legacySystemFlagKeys = preferences.asMap().keys
                .map { it.name }
                .filter { it.startsWith("prompt_tag_") && it.endsWith("_is_system_tag") }
            legacySystemFlagKeys.forEach { key ->
                preferences.remove(booleanPreferencesKey(key))
            }
        }
    }

    private fun removeTagPreferenceKeys(preferences: MutablePreferences, id: String) {
        val keysToRemove = listOf(
            "prompt_tag_${id}_name",
            "prompt_tag_${id}_description",
            "prompt_tag_${id}_prompt_content",
            "prompt_tag_${id}_tag_type",
            "prompt_tag_${id}_is_system_tag",
            "prompt_tag_${id}_created_at",
            "prompt_tag_${id}_updated_at"
        )

        keysToRemove.forEach { key ->
            when {
                key.endsWith("_is_system_tag") -> preferences.remove(booleanPreferencesKey(key))
                key.endsWith("_created_at") || key.endsWith("_updated_at") -> preferences.remove(longPreferencesKey(key))
                else -> preferences.remove(stringPreferencesKey(key))
            }
        }
    }
}

package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CustomEmoji
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.customEmojiDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "custom_emoji_settings")

/**
 * 自定义表情 DataStore Preferences 管理类
 *
 * 使用 DataStore 按角色卡/角色组存储自定义表情元数据（JSON格式）。
 * 文件本身存储在 filesDir/custom_emoji/<target>/ 目录下。
 */
class CustomEmojiPreferences private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: CustomEmojiPreferences? = null

        fun getInstance(context: Context): CustomEmojiPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomEmojiPreferences(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private const val TAG = "CustomEmojiPreferences"
        private val LEGACY_CUSTOM_EMOJIS = stringPreferencesKey("custom_emojis")
        private val LEGACY_ALL_CATEGORIES = stringSetPreferencesKey("all_categories")
        private val LEGACY_BUILTIN_EMOJIS_INITIALIZED = booleanPreferencesKey("builtin_emojis_initialized")

        val BUILTIN_EMOTIONS = listOf(
            "happy", "sad", "angry", "surprised", "confused",
            "crying", "like_you", "miss_you", "speechless"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun targetPrefix(target: ActivePrompt): String {
        return when (target) {
            is ActivePrompt.CharacterCard -> "character_card_custom_emoji_${target.id}_"
            is ActivePrompt.CharacterGroup -> "character_group_custom_emoji_${target.id}_"
        }
    }

    private fun customEmojisKey(target: ActivePrompt) =
        stringPreferencesKey("${targetPrefix(target)}custom_emojis")

    private fun categoriesKey(target: ActivePrompt) =
        stringSetPreferencesKey("${targetPrefix(target)}all_categories")

    private fun builtinInitializedKey(target: ActivePrompt) =
        booleanPreferencesKey("${targetPrefix(target)}builtin_emojis_initialized")

    private fun decodeCustomEmojis(jsonString: String): List<CustomEmoji> {
        return try {
            json.decodeFromString<List<CustomEmoji>>(jsonString)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding custom emojis", e)
            emptyList()
        }
    }

    fun getCustomEmojisFlow(target: ActivePrompt): Flow<List<CustomEmoji>> {
        return context.customEmojiDataStore.data.map { preferences ->
            decodeCustomEmojis(preferences[customEmojisKey(target)] ?: "[]")
        }
    }

    suspend fun setCustomEmojis(target: ActivePrompt, emojis: List<CustomEmoji>) {
        context.customEmojiDataStore.edit { preferences ->
            preferences[customEmojisKey(target)] = json.encodeToString(emojis)
        }
    }

    suspend fun addCustomEmoji(target: ActivePrompt, emoji: CustomEmoji) {
        context.customEmojiDataStore.edit { preferences ->
            val currentList = decodeCustomEmojis(preferences[customEmojisKey(target)] ?: "[]")
            preferences[customEmojisKey(target)] = json.encodeToString(currentList + emoji)
            AppLogger.d(TAG, "Added emoji: ${emoji.id} to target: $target category: ${emoji.emotionCategory}")
        }
    }

    suspend fun deleteCustomEmoji(target: ActivePrompt, emojiId: String) {
        context.customEmojiDataStore.edit { preferences ->
            val currentList = decodeCustomEmojis(preferences[customEmojisKey(target)] ?: "[]")
            val updatedList = currentList.filter { it.id != emojiId }
            preferences[customEmojisKey(target)] = json.encodeToString(updatedList)
            AppLogger.d(TAG, "Deleted emoji: $emojiId from target: $target")
        }
    }

    fun getEmojisForCategory(target: ActivePrompt, category: String): Flow<List<CustomEmoji>> {
        return getCustomEmojisFlow(target).map { emojis ->
            emojis.filter { it.emotionCategory == category }
        }
    }

    suspend fun deleteCategory(target: ActivePrompt, category: String) {
        context.customEmojiDataStore.edit { preferences ->
            val currentList = decodeCustomEmojis(preferences[customEmojisKey(target)] ?: "[]")
            val updatedList = currentList.filter { it.emotionCategory != category }
            preferences[customEmojisKey(target)] = json.encodeToString(updatedList)

            val currentCategories = preferences[categoriesKey(target)] ?: emptySet()
            val updatedCategories = currentCategories - category
            if (updatedCategories.isEmpty()) {
                preferences.remove(categoriesKey(target))
            } else {
                preferences[categoriesKey(target)] = updatedCategories
            }
            AppLogger.d(TAG, "Deleted category: $category from target: $target")
        }
    }

    fun getAllCategories(target: ActivePrompt): Flow<List<String>> {
        return context.customEmojiDataStore.data.map { preferences ->
            val storedCategories = preferences[categoriesKey(target)] ?: emptySet()
            val builtin = BUILTIN_EMOTIONS.filter { it in storedCategories }
            val custom = storedCategories.filter { it !in BUILTIN_EMOTIONS }.sorted()
            builtin + custom
        }
    }

    suspend fun setAllCategories(target: ActivePrompt, categories: Set<String>) {
        context.customEmojiDataStore.edit { preferences ->
            if (categories.isEmpty()) {
                preferences.remove(categoriesKey(target))
            } else {
                preferences[categoriesKey(target)] = categories
            }
        }
    }

    suspend fun addCategory(target: ActivePrompt, categoryName: String) {
        context.customEmojiDataStore.edit { preferences ->
            val currentCategories = preferences[categoriesKey(target)] ?: emptySet()
            if (!currentCategories.contains(categoryName)) {
                preferences[categoriesKey(target)] = currentCategories + categoryName
                AppLogger.d(TAG, "Added category: $categoryName to target: $target")
            }
        }
    }

    suspend fun addCategories(target: ActivePrompt, categoryNames: List<String>) {
        context.customEmojiDataStore.edit { preferences ->
            val currentCategories = preferences[categoriesKey(target)] ?: emptySet()
            val newCategories = categoryNames.filter { it !in currentCategories }
            if (newCategories.isNotEmpty()) {
                preferences[categoriesKey(target)] = currentCategories + newCategories
                AppLogger.d(TAG, "Added categories: $newCategories to target: $target")
            }
        }
    }

    suspend fun clearAllEmojis(target: ActivePrompt) {
        context.customEmojiDataStore.edit { preferences ->
            preferences[customEmojisKey(target)] = "[]"
            preferences.remove(categoriesKey(target))
            preferences.remove(builtinInitializedKey(target))
            AppLogger.d(TAG, "Cleared all emojis for target: $target")
        }
    }

    fun isBuiltinEmojisInitialized(target: ActivePrompt): Flow<Boolean> {
        return context.customEmojiDataStore.data.map { preferences ->
            preferences[builtinInitializedKey(target)] ?: false
        }
    }

    suspend fun setBuiltinEmojisInitialized(target: ActivePrompt, initialized: Boolean) {
        context.customEmojiDataStore.edit { preferences ->
            if (initialized) {
                preferences[builtinInitializedKey(target)] = true
            } else {
                preferences.remove(builtinInitializedKey(target))
            }
        }
    }

    suspend fun deleteTarget(target: ActivePrompt) {
        context.customEmojiDataStore.edit { preferences ->
            preferences.remove(customEmojisKey(target))
            preferences.remove(categoriesKey(target))
            preferences.remove(builtinInitializedKey(target))
        }
    }

    suspend fun clearLegacyGlobalStorage() {
        context.customEmojiDataStore.edit { preferences ->
            preferences.remove(LEGACY_CUSTOM_EMOJIS)
            preferences.remove(LEGACY_ALL_CATEGORIES)
            preferences.remove(LEGACY_BUILTIN_EMOJIS_INITIALIZED)
        }
    }
}

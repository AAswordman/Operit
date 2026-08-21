package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.waifuDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "waifu_settings")

class WaifuPreferences private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: WaifuPreferences? = null

        fun getInstance(context: Context): WaifuPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = WaifuPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // Keys for Waifu Mode
        val ENABLE_WAIFU_MODE = booleanPreferencesKey("enable_waifu_mode")
        val WAIFU_CHAR_DELAY = intPreferencesKey("waifu_char_delay") // 每字符延迟（毫秒）
        val WAIFU_REMOVE_PUNCTUATION = booleanPreferencesKey("waifu_remove_punctuation") // 是否移除标点符号
        val WAIFU_ENABLE_EMOTICONS = booleanPreferencesKey("waifu_enable_emoticons") // 是否启用表情包
        val WAIFU_ENABLE_SELFIE = booleanPreferencesKey("waifu_enable_selfie") // 是否启用自拍功能
        val WAIFU_ENABLE_MERGE_SEND = booleanPreferencesKey("waifu_enable_merge_send") // 是否启用合并发送
        val WAIFU_MERGE_SEND_DELAY_MS = intPreferencesKey("waifu_merge_send_delay_ms") // 合并发送等待时间
        val WAIFU_CUSTOM_PROMPT = stringPreferencesKey("waifu_custom_prompt") // Waifu模式额外提示词
        val WAIFU_SELFIE_PROMPT = stringPreferencesKey("waifu_selfie_prompt") // 自拍功能的外貌提示词

        // Default value for Waifu Mode
        const val DEFAULT_ENABLE_WAIFU_MODE = false
        const val DEFAULT_WAIFU_CHAR_DELAY = 250 // 250ms per character (4 chars per second)
        const val DEFAULT_WAIFU_REMOVE_PUNCTUATION = false // 默认保留标点符号
        const val DEFAULT_WAIFU_ENABLE_EMOTICONS = false // 默认不启用表情包
        const val DEFAULT_WAIFU_ENABLE_SELFIE = false // 默认不启用自拍功能
        const val DEFAULT_WAIFU_ENABLE_MERGE_SEND = false // 默认关闭合并发送
        const val DEFAULT_WAIFU_MERGE_SEND_DELAY_MS = 5000
        const val DEFAULT_WAIFU_CUSTOM_PROMPT = "你必须遵守：禁止使用动作表情，禁止描述动作表情，只允许使用纯文本进行对话。" // 默认Waifu附加提示词
        const val DEFAULT_WAIFU_SELFIE_PROMPT = "kipfel vrchat, long hair, Matcha color hair, purple eyes, sweater vest, black skirt, black necktie, collared shirt, long sleeves, black headwear, beanie, pleated skirt, hair bun, white shirt, hair ribbon, hairclip, hair between eyes, black footwear, blush, hair ornament, cat hat, very long hair, sweater, animal ear headwear, bag, bandaid on leg, socks" // 默认外貌提示词
    }

    // Flow for Waifu Mode
    val enableWaifuModeFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[ENABLE_WAIFU_MODE] ?: DEFAULT_ENABLE_WAIFU_MODE
        }

    val waifuCharDelayFlow: Flow<Int> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_CHAR_DELAY] ?: DEFAULT_WAIFU_CHAR_DELAY
        }

    val waifuRemovePunctuationFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_REMOVE_PUNCTUATION] ?: DEFAULT_WAIFU_REMOVE_PUNCTUATION
        }

    val waifuEnableEmoticonsFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_ENABLE_EMOTICONS] ?: DEFAULT_WAIFU_ENABLE_EMOTICONS
        }

    val waifuEnableSelfieFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_ENABLE_SELFIE] ?: DEFAULT_WAIFU_ENABLE_SELFIE
        }

    val waifuEnableMergeSendFlow: Flow<Boolean> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_ENABLE_MERGE_SEND] ?: DEFAULT_WAIFU_ENABLE_MERGE_SEND
        }

    val waifuMergeSendDelayMsFlow: Flow<Int> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_MERGE_SEND_DELAY_MS] ?: DEFAULT_WAIFU_MERGE_SEND_DELAY_MS
        }

    val waifuCustomPromptFlow: Flow<String> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_CUSTOM_PROMPT] ?: DEFAULT_WAIFU_CUSTOM_PROMPT
        }

    val waifuSelfiePromptFlow: Flow<String> =
        context.waifuDataStore.data.map { preferences ->
            preferences[WAIFU_SELFIE_PROMPT] ?: DEFAULT_WAIFU_SELFIE_PROMPT
        }

    // Save Waifu Mode setting
    suspend fun saveEnableWaifuMode(isEnabled: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[ENABLE_WAIFU_MODE] = isEnabled
        }
    }

    suspend fun saveWaifuCharDelay(delayMs: Int) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_CHAR_DELAY] = delayMs
        }
    }

    suspend fun saveWaifuRemovePunctuation(removePunctuation: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_REMOVE_PUNCTUATION] = removePunctuation
        }
    }

    suspend fun saveWaifuEnableEmoticons(enableEmoticons: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_ENABLE_EMOTICONS] = enableEmoticons
        }
    }

    suspend fun saveWaifuEnableSelfie(enableSelfie: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_ENABLE_SELFIE] = enableSelfie
        }
    }

    suspend fun saveWaifuEnableMergeSend(enableMergeSend: Boolean) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_ENABLE_MERGE_SEND] = enableMergeSend
        }
    }

    suspend fun saveWaifuMergeSendDelayMs(delayMs: Int) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_MERGE_SEND_DELAY_MS] = delayMs
        }
    }

    suspend fun saveWaifuCustomPrompt(prompt: String) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_CUSTOM_PROMPT] = prompt
        }
    }

    suspend fun saveWaifuSelfiePrompt(prompt: String) {
        context.waifuDataStore.edit { preferences ->
            preferences[WAIFU_SELFIE_PROMPT] = prompt
        }
    }

    // ========== Waifu模式角色卡/群组绑定功能 ==========

    private fun getCharacterCardWaifuPrefix(characterCardId: String): String =
        "character_card_waifu_${characterCardId}_"

    private fun getCharacterGroupWaifuPrefix(characterGroupId: String): String =
        "character_group_waifu_${characterGroupId}_"

    private fun getAllBooleanWaifuKeys(): List<Preferences.Key<Boolean>> {
        return listOf(
            ENABLE_WAIFU_MODE,
            WAIFU_REMOVE_PUNCTUATION,
            WAIFU_ENABLE_EMOTICONS,
            WAIFU_ENABLE_SELFIE,
            WAIFU_ENABLE_MERGE_SEND
        )
    }

    private fun getAllIntWaifuKeys(): List<Preferences.Key<Int>> {
        return listOf(
            WAIFU_CHAR_DELAY,
            WAIFU_MERGE_SEND_DELAY_MS
        )
    }

    private fun getAllStringWaifuKeys(): List<Preferences.Key<String>> {
        return listOf(
            WAIFU_CUSTOM_PROMPT,
            WAIFU_SELFIE_PROMPT
        )
    }

    private suspend fun copyCurrentWaifuSettingsToPrefix(prefix: String) {
        context.waifuDataStore.edit { preferences ->
            getAllBooleanWaifuKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[booleanPreferencesKey("${prefix}${key.name}")] = value
                }
            }
            getAllIntWaifuKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[intPreferencesKey("${prefix}${key.name}")] = value
                }
            }
            getAllStringWaifuKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[stringPreferencesKey("${prefix}${key.name}")] = value
                }
            }
        }
    }

    private suspend fun cloneWaifuSettingsBetweenPrefixes(sourcePrefix: String, targetPrefix: String) {
        context.waifuDataStore.edit { preferences ->
            getAllBooleanWaifuKeys().forEach { key ->
                val sourceKey = booleanPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = booleanPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }

            getAllIntWaifuKeys().forEach { key ->
                val sourceKey = intPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = intPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }

            getAllStringWaifuKeys().forEach { key ->
                val sourceKey = stringPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = stringPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }
        }
    }

    private suspend fun switchToWaifuSettingsByPrefix(prefix: String) {
        context.waifuDataStore.edit { preferences ->
            getAllBooleanWaifuKeys().forEach { key ->
                val cardKey = booleanPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
            getAllIntWaifuKeys().forEach { key ->
                val cardKey = intPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
            getAllStringWaifuKeys().forEach { key ->
                val cardKey = stringPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
        }
    }

    private suspend fun deleteWaifuSettingsByPrefix(prefix: String) {
        context.waifuDataStore.edit { preferences ->
            getAllBooleanWaifuKeys().forEach { key ->
                preferences.remove(booleanPreferencesKey("${prefix}${key.name}"))
            }
            getAllIntWaifuKeys().forEach { key ->
                preferences.remove(intPreferencesKey("${prefix}${key.name}"))
            }
            getAllStringWaifuKeys().forEach { key ->
                preferences.remove(stringPreferencesKey("${prefix}${key.name}"))
            }
        }
    }

    private suspend fun hasWaifuSettingsByPrefix(prefix: String): Boolean {
        val preferences = context.waifuDataStore.data.first()
        return getAllBooleanWaifuKeys().any { key -> preferences.contains(booleanPreferencesKey("${prefix}${key.name}")) } ||
                getAllIntWaifuKeys().any { key -> preferences.contains(intPreferencesKey("${prefix}${key.name}")) } ||
                getAllStringWaifuKeys().any { key -> preferences.contains(stringPreferencesKey("${prefix}${key.name}")) }
    }

    suspend fun copyCurrentWaifuSettingsToCharacterCard(characterCardId: String) {
        copyCurrentWaifuSettingsToPrefix(getCharacterCardWaifuPrefix(characterCardId))
    }

    suspend fun cloneWaifuSettingsBetweenCharacterCards(
        sourceCharacterCardId: String,
        targetCharacterCardId: String
    ) {
        cloneWaifuSettingsBetweenPrefixes(
            getCharacterCardWaifuPrefix(sourceCharacterCardId),
            getCharacterCardWaifuPrefix(targetCharacterCardId)
        )
    }

    suspend fun switchToCharacterCardWaifuSettings(characterCardId: String) {
        switchToWaifuSettingsByPrefix(getCharacterCardWaifuPrefix(characterCardId))
    }

    suspend fun saveCurrentWaifuSettingsToCharacterCard(characterCardId: String) {
        copyCurrentWaifuSettingsToCharacterCard(characterCardId)
    }

    suspend fun deleteCharacterCardWaifuSettings(characterCardId: String) {
        deleteWaifuSettingsByPrefix(getCharacterCardWaifuPrefix(characterCardId))
    }

    suspend fun hasCharacterCardWaifuSettings(characterCardId: String): Boolean {
        return hasWaifuSettingsByPrefix(getCharacterCardWaifuPrefix(characterCardId))
    }

    suspend fun copyCurrentWaifuSettingsToCharacterGroup(characterGroupId: String) {
        copyCurrentWaifuSettingsToPrefix(getCharacterGroupWaifuPrefix(characterGroupId))
    }

    suspend fun cloneWaifuSettingsBetweenCharacterGroups(
        sourceCharacterGroupId: String,
        targetCharacterGroupId: String
    ) {
        cloneWaifuSettingsBetweenPrefixes(
            getCharacterGroupWaifuPrefix(sourceCharacterGroupId),
            getCharacterGroupWaifuPrefix(targetCharacterGroupId)
        )
    }

    suspend fun switchToCharacterGroupWaifuSettings(characterGroupId: String) {
        switchToWaifuSettingsByPrefix(getCharacterGroupWaifuPrefix(characterGroupId))
    }

    suspend fun saveCurrentWaifuSettingsToCharacterGroup(characterGroupId: String) {
        copyCurrentWaifuSettingsToCharacterGroup(characterGroupId)
    }

    suspend fun deleteCharacterGroupWaifuSettings(characterGroupId: String) {
        deleteWaifuSettingsByPrefix(getCharacterGroupWaifuPrefix(characterGroupId))
    }

    suspend fun hasCharacterGroupWaifuSettings(characterGroupId: String): Boolean {
        return hasWaifuSettingsByPrefix(getCharacterGroupWaifuPrefix(characterGroupId))
    }
}

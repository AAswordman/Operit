package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.backup.OperitBackupDirs
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.CharacterCardToolAccessConfig
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import com.ai.assistance.operit.data.model.TavernCharacterCard
import com.ai.assistance.operit.data.model.TavernCharacterData
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.TavernExtensions
import com.ai.assistance.operit.data.model.OperitTavernExtension
import com.ai.assistance.operit.data.model.OperitAttachedTagPayload
import com.ai.assistance.operit.data.model.OperitCharacterCardPayload
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import java.util.UUID
import com.ai.assistance.operit.util.AppLogger
import android.util.Base64
import java.io.InputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.characterCardDataStore by preferencesDataStore(
    name = "character_cards"
)

/**
 * 角色卡管理器
 */
class CharacterCardManager private constructor(private val context: Context) {
    
    private val dataStore = context.characterCardDataStore
    private val tagManager = PromptTagManager.getInstance(context)
    // 添加UserPreferencesManager引用用于主题管理
    private val userPreferencesManager = UserPreferencesManager.getInstance(context)
    // 添加WaifuPreferences引用用于Waifu模式配置管理
    private val waifuPreferences = WaifuPreferences.getInstance(context)
    private val customEmojiRepository by lazy { CustomEmojiRepository.getInstance(context) }
    
    companion object {
        private val CHARACTER_CARD_LIST = stringSetPreferencesKey("character_card_list")
        private val ACTIVE_CHARACTER_CARD_ID = stringPreferencesKey("active_character_card_id")

        // 默认角色卡ID
        const val DEFAULT_CHARACTER_CARD_ID = "default_character"

        const val DEFAULT_CHARACTER_NAME = "Operit"
        
        @Volatile
        private var INSTANCE: CharacterCardManager? = null
        
        /**
         * 获取全局单例实例
         */
        fun getInstance(context: Context): CharacterCardManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CharacterCardManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 角色卡列表流
    val characterCardListFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[CHARACTER_CARD_LIST]?.toList() ?: emptyList()
    }
    
    // 活跃角色卡ID流（可以为null）
    private val activeCharacterCardIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_CHARACTER_CARD_ID]
    }
    
    // 获取角色卡流
    fun getCharacterCardFlow(id: String): Flow<CharacterCard> = dataStore.data.map { preferences ->
        getCharacterCardFromPreferences(preferences, id)
    }
    
    // 获取活跃角色卡流（可能为null）
    private val activeCharacterCardFlow: Flow<CharacterCard?> = dataStore.data.map { preferences ->
        val activeId = preferences[ACTIVE_CHARACTER_CARD_ID]
        if (activeId != null) {
            getCharacterCardFromPreferences(preferences, activeId)
        } else {
            null
        }
    }

    internal fun observeActiveCharacterCardId(): Flow<String?> = activeCharacterCardIdFlow

    private val toolAccessConfigJson = Json { ignoreUnknownKeys = true }

    private fun toolAccessConfigKey(id: String) =
        stringPreferencesKey("character_card_${id}_tool_access_config_json")

    private fun parseToolAccessConfig(raw: String?): CharacterCardToolAccessConfig {
        if (raw.isNullOrBlank()) {
            return CharacterCardToolAccessConfig()
        }
        return runCatching {
            toolAccessConfigJson.decodeFromString<CharacterCardToolAccessConfig>(raw).normalized()
        }.getOrElse {
            AppLogger.e("CharacterCardManager", "解析角色卡工具白名单失败", it)
            CharacterCardToolAccessConfig()
        }
    }

    private fun writeToolAccessConfig(
        preferences: MutablePreferences,
        id: String,
        config: CharacterCardToolAccessConfig
    ) {
        val normalizedConfig = config.normalized()
        val key = toolAccessConfigKey(id)
        if (normalizedConfig == CharacterCardToolAccessConfig()) {
            preferences.remove(key)
            return
        }
        preferences[key] = toolAccessConfigJson.encodeToString(normalizedConfig)
    }
    
    // 从Preferences中获取角色卡
    private fun getCharacterCardFromPreferences(preferences: Preferences, id: String): CharacterCard {
        val nameKey = stringPreferencesKey("character_card_${id}_name")
        val descriptionKey = stringPreferencesKey("character_card_${id}_description")
        val characterSettingKey = stringPreferencesKey("character_card_${id}_character_setting")
        val openingStatementKey = stringPreferencesKey("character_card_${id}_opening_statement") // 新增
        val otherContentChatKey = stringPreferencesKey("character_card_${id}_other_content_chat")
        val otherContentVoiceKey = stringPreferencesKey("character_card_${id}_other_content_voice")
        val attachedTagIdsKey = stringSetPreferencesKey("character_card_${id}_attached_tag_ids")
        val advancedCustomPromptKey = stringPreferencesKey("character_card_${id}_advanced_custom_prompt")
        val marksKey = stringPreferencesKey("character_card_${id}_marks")
        val chatModelBindingModeKey = stringPreferencesKey("character_card_${id}_chat_model_binding_mode")
        val chatModelConfigIdKey = stringPreferencesKey("character_card_${id}_chat_model_config_id")
        val chatModelIndexKey = intPreferencesKey("character_card_${id}_chat_model_index")
        val memoryProfileBindingModeKey = stringPreferencesKey("character_card_${id}_memory_profile_binding_mode")
        val memoryProfileIdKey = stringPreferencesKey("character_card_${id}_memory_profile_id")
        val toolAccessConfigKey = toolAccessConfigKey(id)
        val isDefaultKey = booleanPreferencesKey("character_card_${id}_is_default")
        val createdAtKey = longPreferencesKey("character_card_${id}_created_at")
        val updatedAtKey = longPreferencesKey("character_card_${id}_updated_at")
        
        return CharacterCard(
            id = id,
            name = preferences[nameKey] ?: context.getString(R.string.default_character_card),
            description = preferences[descriptionKey] ?: "",
            characterSetting = preferences[characterSettingKey] ?: "",
            openingStatement = preferences[openingStatementKey] ?: "", // 新增
            otherContentChat = preferences[otherContentChatKey] ?: "",
            otherContentVoice = preferences[otherContentVoiceKey] ?: "",
            attachedTagIds = preferences[attachedTagIdsKey]?.toList() ?: emptyList(),
            advancedCustomPrompt = preferences[advancedCustomPromptKey] ?: "",
            marks = preferences[marksKey] ?: "",
            chatModelBindingMode = CharacterCardChatModelBindingMode.normalize(preferences[chatModelBindingModeKey]),
            chatModelConfigId = preferences[chatModelConfigIdKey],
            chatModelIndex = (preferences[chatModelIndexKey] ?: 0).coerceAtLeast(0),
            memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.normalize(preferences[memoryProfileBindingModeKey]),
            memoryProfileId = preferences[memoryProfileIdKey],
            toolAccessConfig = parseToolAccessConfig(preferences[toolAccessConfigKey]),
            isDefault = (id == DEFAULT_CHARACTER_CARD_ID) || (preferences[isDefaultKey] ?: false),
            createdAt = preferences[createdAtKey] ?: System.currentTimeMillis(),
            updatedAt = preferences[updatedAtKey] ?: System.currentTimeMillis()
        )
    }
    
    // 获取角色卡快照
    suspend fun getCharacterCard(id: String): CharacterCard {
        val preferences = dataStore.data.first()
        return getCharacterCardFromPreferences(preferences, id)
    }

    /**
     * 为角色卡创建默认主题配置
     */
    private suspend fun createDefaultThemeForCharacterCard(
        characterCardId: String,
        emojiSourcePrompt: ActivePrompt
    ) {
        // 新建角色卡使用默认主题，不继承创建时的当前主题。
        userPreferencesManager.deleteCharacterCardTheme(characterCardId)
        // 同时也复制当前Waifu模式配置
        waifuPreferences.copyCurrentWaifuSettingsToCharacterCard(characterCardId)
        // 同时复制创建前活跃目标的自定义表情配置
        customEmojiRepository.cloneEmojiSet(
            emojiSourcePrompt,
            ActivePrompt.CharacterCard(characterCardId)
        )
    }


    suspend fun cloneBindingsFromCharacterCard(sourceCharacterCardId: String, targetCharacterCardId: String) {
        val activePromptManager = ActivePromptManager.getInstance(context)
        activePromptManager.runThemeTransition {
            cloneBindingsFromCharacterCardLocked(sourceCharacterCardId, targetCharacterCardId)
            if (activePromptManager.getActivePrompt() == ActivePrompt.CharacterCard(targetCharacterCardId)) {
                switchToCharacterCardWaifuSettings(targetCharacterCardId)
            }
        }
    }

    private suspend fun cloneBindingsFromCharacterCardLocked(
        sourceCharacterCardId: String,
        targetCharacterCardId: String,
    ) {
        try {
            userPreferencesManager.cloneThemeBetweenCharacterCards(sourceCharacterCardId, targetCharacterCardId)
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "克隆角色卡主题配置失败", e)
        }

        try {
            waifuPreferences.cloneWaifuSettingsBetweenCharacterCards(sourceCharacterCardId, targetCharacterCardId)
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "克隆角色卡Waifu配置失败", e)
        }

        try {
            customEmojiRepository.cloneEmojisBetweenCharacterCards(sourceCharacterCardId, targetCharacterCardId)
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "克隆角色卡自定义表情失败", e)
        }
    }

    // 创建角色卡
    suspend fun createCharacterCard(card: CharacterCard): String {
        // Target activation must not interleave with a theme snapshot save.
        return ActivePromptManager.getInstance(context).runThemeTransition {
            createCharacterCardLocked(card)
        }
    }

    private suspend fun createCharacterCardLocked(card: CharacterCard): String {
        val emojiSourcePrompt = resolveEmojiSourcePrompt()
        val id = if (card.isDefault) DEFAULT_CHARACTER_CARD_ID else UUID.randomUUID().toString()
        val newCard = card.copy(id = id)
        var activatedCard = false

        dataStore.edit { preferences ->
            // 添加到角色卡列表
            val currentList = preferences[CHARACTER_CARD_LIST]?.toMutableSet() ?: mutableSetOf(DEFAULT_CHARACTER_CARD_ID)
            if (!currentList.contains(id)) {
                currentList.add(id)
                preferences[CHARACTER_CARD_LIST] = currentList
            }
            
            // 设置角色卡数据
            preferences[stringPreferencesKey("character_card_${id}_name")] = newCard.name
            preferences[stringPreferencesKey("character_card_${id}_description")] = newCard.description
            preferences[stringPreferencesKey("character_card_${id}_character_setting")] = newCard.characterSetting
            preferences[stringPreferencesKey("character_card_${id}_opening_statement")] = newCard.openingStatement // 新增
        preferences[stringPreferencesKey("character_card_${id}_other_content_chat")] = newCard.otherContentChat
        preferences[stringPreferencesKey("character_card_${id}_other_content_voice")] = newCard.otherContentVoice
            preferences[stringSetPreferencesKey("character_card_${id}_attached_tag_ids")] = newCard.attachedTagIds.toSet()
            preferences[stringPreferencesKey("character_card_${id}_advanced_custom_prompt")] = newCard.advancedCustomPrompt
            preferences[stringPreferencesKey("character_card_${id}_marks")] = newCard.marks
            preferences[stringPreferencesKey("character_card_${id}_chat_model_binding_mode")] =
                CharacterCardChatModelBindingMode.normalize(newCard.chatModelBindingMode)
            val chatModelConfigIdKey = stringPreferencesKey("character_card_${id}_chat_model_config_id")
            if (newCard.chatModelConfigId.isNullOrBlank()) {
                preferences.remove(chatModelConfigIdKey)
            } else {
                preferences[chatModelConfigIdKey] = newCard.chatModelConfigId
            }
            preferences[intPreferencesKey("character_card_${id}_chat_model_index")] = newCard.chatModelIndex.coerceAtLeast(0)
            preferences[stringPreferencesKey("character_card_${id}_memory_profile_binding_mode")] =
                CharacterCardMemoryProfileBindingMode.normalize(newCard.memoryProfileBindingMode)
            val memoryProfileIdKey = stringPreferencesKey("character_card_${id}_memory_profile_id")
            if (newCard.memoryProfileId.isNullOrBlank()) {
                preferences.remove(memoryProfileIdKey)
            } else {
                preferences[memoryProfileIdKey] = newCard.memoryProfileId
            }
            writeToolAccessConfig(preferences, id, newCard.toolAccessConfig)
            preferences[booleanPreferencesKey("character_card_${id}_is_default")] = newCard.isDefault
            preferences[longPreferencesKey("character_card_${id}_created_at")] = newCard.createdAt
            preferences[longPreferencesKey("character_card_${id}_updated_at")] = newCard.updatedAt
            
            // 如果是第一个角色卡或设为默认，设为活跃
            if (newCard.isDefault || preferences[ACTIVE_CHARACTER_CARD_ID] == null) {
                preferences[ACTIVE_CHARACTER_CARD_ID] = id
                activatedCard = true
            }
        }

        // 为新角色卡创建默认主题配置
        if (!newCard.isDefault) {
            createDefaultThemeForCharacterCard(id, emojiSourcePrompt)
        }

        val activeGroupId = CharacterGroupCardManager.getInstance(context)
            .observeActiveCharacterGroupId()
            .first()
        if (activatedCard && activeGroupId.isNullOrBlank()) {
            switchToCharacterCardWaifuSettings(id)
        }
        
        return id
    }
    
    // 更新角色卡
    suspend fun updateCharacterCard(card: CharacterCard) {
        val previousName = getCharacterCard(card.id).name
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("character_card_${card.id}_name")] = card.name
            preferences[stringPreferencesKey("character_card_${card.id}_description")] = card.description
            preferences[stringPreferencesKey("character_card_${card.id}_character_setting")] = card.characterSetting
            preferences[stringPreferencesKey("character_card_${card.id}_opening_statement")] = card.openingStatement // 新增
        preferences[stringPreferencesKey("character_card_${card.id}_other_content_chat")] = card.otherContentChat
        preferences[stringPreferencesKey("character_card_${card.id}_other_content_voice")] = card.otherContentVoice
            preferences[stringSetPreferencesKey("character_card_${card.id}_attached_tag_ids")] = card.attachedTagIds.toSet()
            preferences[stringPreferencesKey("character_card_${card.id}_advanced_custom_prompt")] = card.advancedCustomPrompt
            preferences[stringPreferencesKey("character_card_${card.id}_marks")] = card.marks
            preferences[stringPreferencesKey("character_card_${card.id}_chat_model_binding_mode")] =
                CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode)
            val chatModelConfigIdKey = stringPreferencesKey("character_card_${card.id}_chat_model_config_id")
            if (card.chatModelConfigId.isNullOrBlank()) {
                preferences.remove(chatModelConfigIdKey)
            } else {
                preferences[chatModelConfigIdKey] = card.chatModelConfigId
            }
            preferences[intPreferencesKey("character_card_${card.id}_chat_model_index")] = card.chatModelIndex.coerceAtLeast(0)
            preferences[stringPreferencesKey("character_card_${card.id}_memory_profile_binding_mode")] =
                CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode)
            val memoryProfileIdKey = stringPreferencesKey("character_card_${card.id}_memory_profile_id")
            if (card.memoryProfileId.isNullOrBlank()) {
                preferences.remove(memoryProfileIdKey)
            } else {
                preferences[memoryProfileIdKey] = card.memoryProfileId
            }
            writeToolAccessConfig(preferences, card.id, card.toolAccessConfig)
            
            // 更新修改时间
            preferences[longPreferencesKey("character_card_${card.id}_updated_at")] = System.currentTimeMillis()
        }
        if (previousName != card.name) {
            ChatHistoryManager.getInstance(context).renameCharacterCardInChats(previousName, card.name)
        }
    }
    
    // 删除角色卡
    suspend fun deleteCharacterCard(id: String) {
        if (id == DEFAULT_CHARACTER_CARD_ID) return

        // Target activation must not interleave with a theme snapshot save.
        ActivePromptManager.getInstance(context).runThemeTransition {
            deleteCharacterCardLocked(id)
        }
    }

    private suspend fun deleteCharacterCardLocked(id: String) {
        val deletedCardName = getCharacterCard(id).name
        var deletedActiveCard = false
        dataStore.edit { preferences ->
            // 从列表中移除
            val currentList = preferences[CHARACTER_CARD_LIST]?.toMutableSet() ?: mutableSetOf(DEFAULT_CHARACTER_CARD_ID)
            currentList.remove(id)
            preferences[CHARACTER_CARD_LIST] = currentList
            
            // 清除角色卡数据
            val keysToRemove = listOf(
                "character_card_${id}_name",
                "character_card_${id}_description",
                "character_card_${id}_character_setting",
                "character_card_${id}_opening_statement", // 新增
                "character_card_${id}_other_content",
                "character_card_${id}_other_content_chat",
                "character_card_${id}_other_content_voice",
                "character_card_${id}_attached_tag_ids",
                "character_card_${id}_advanced_custom_prompt",
                "character_card_${id}_marks",
                "character_card_${id}_chat_model_binding_mode",
                "character_card_${id}_chat_model_config_id",
                "character_card_${id}_chat_model_index",
                "character_card_${id}_memory_profile_binding_mode",
                "character_card_${id}_memory_profile_id",
                "character_card_${id}_tool_access_config_json",
                "character_card_${id}_is_default",
                "character_card_${id}_created_at",
                "character_card_${id}_updated_at"
            )
            
            keysToRemove.forEach { key ->
                when {
                    key.endsWith("_attached_tag_ids") -> preferences.remove(stringSetPreferencesKey(key))
                    key.endsWith("_chat_model_index") -> preferences.remove(intPreferencesKey(key))
                    key.endsWith("_is_default") -> preferences.remove(booleanPreferencesKey(key))
                    key.endsWith("_created_at") || key.endsWith("_updated_at") -> preferences.remove(longPreferencesKey(key))
                    else -> preferences.remove(stringPreferencesKey(key))
                }
            }
            
            // 如果这是活跃角色卡，切换到默认
            if (preferences[ACTIVE_CHARACTER_CARD_ID] == id) {
                deletedActiveCard = true
                preferences[ACTIVE_CHARACTER_CARD_ID] = DEFAULT_CHARACTER_CARD_ID
            }
        }

        // 删除角色卡对应的主题配置
        userPreferencesManager.deleteCharacterCardTheme(id)
        // 删除角色卡对应的Waifu模式配置
        waifuPreferences.deleteCharacterCardWaifuSettings(id)
        // 删除角色卡对应的自定义表情配置
        customEmojiRepository.deleteCharacterCardEmojis(id)
        val hasSameNamedCard = getAllCharacterCards().any { card -> card.name == deletedCardName }
        if (!hasSameNamedCard) {
            ChatHistoryManager.getInstance(context).clearCharacterCardBinding(deletedCardName)
        }

        val activeGroupId = CharacterGroupCardManager.getInstance(context)
            .observeActiveCharacterGroupId()
            .first()
        if (deletedActiveCard && activeGroupId.isNullOrBlank()) {
            switchToCharacterCardWaifuSettings(DEFAULT_CHARACTER_CARD_ID)
        }
    }
    
    // 设置活跃角色卡
    suspend fun setActiveCharacterCard(id: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_CHARACTER_CARD_ID] = id
        }

        // 切换到对应角色卡的Waifu模式配置
        switchToCharacterCardWaifuSettings(id)
    }

    // 清空活跃角色卡
    suspend fun clearActiveCharacterCard() {
        dataStore.edit { preferences ->
            preferences.remove(ACTIVE_CHARACTER_CARD_ID)
        }
    }
    
    // 组合提示词（角色设定 + 其他内容 + 标签 + 高级自定义）
    suspend fun combinePrompts(
        characterCardId: String,
        additionalTagIds: List<String> = emptyList(),
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT
    ): String {
        val characterCard = getCharacterCardFlow(characterCardId).first()
        val allTagIds = (characterCard.attachedTagIds + additionalTagIds).distinct()
        val attachedTags = allTagIds.mapNotNull { tagId ->
            try {
                tagManager.getPromptTagFlow(tagId).first()
            } catch (e: Exception) {
                // Log or handle error if a tag ID is invalid
                null
            }
        }
        
        val combinedPrompt = buildString {
            if (characterCard.characterSetting.isNotBlank()) {
                append(characterCard.characterSetting)
                append("\n\n")
            }
            
            val otherContent =
                if (promptFunctionType == PromptFunctionType.VOICE) {
                    characterCard.otherContentVoice
                } else {
                    characterCard.otherContentChat
                }
            if (otherContent.isNotBlank()) {
                append(otherContent)
                append("\n\n")
            }
            
            attachedTags.forEach { tag ->
                if (tag.promptContent.isNotBlank()) {
                    append(tag.promptContent)
                    append("\n\n")
                }
            }
            
            if (characterCard.advancedCustomPrompt.isNotBlank()) {
                append(characterCard.advancedCustomPrompt)
                append("\n\n")
            }
        }
        
        return combinedPrompt.trim()
    }
    
    // 初始化默认角色卡
    suspend fun initializeIfNeeded() {
        var isInitialized = false
        dataStore.edit { preferences ->
            val cardListKey = CHARACTER_CARD_LIST
            val currentList = preferences[cardListKey]?.toMutableSet()

            if (currentList == null || currentList.isEmpty()) {
                isInitialized = true
                // 首次安装，创建默认角色卡
                val defaultCardId = DEFAULT_CHARACTER_CARD_ID
                preferences[cardListKey] = setOf(defaultCardId)
                // 不再自动设置活跃角色卡，让用户自己选择角色卡或群组
                // preferences[ACTIVE_CHARACTER_CARD_ID] = defaultCardId

                // 设置默认角色卡数据
                setupDefaultCharacterCard(preferences, defaultCardId)
            }
        }

        val activePromptManager = ActivePromptManager.getInstance(context)
        activePromptManager.runThemeTransition {
            val activePrompt = activePromptManager.getActivePrompt()
            val activeCardId = (activePrompt as? ActivePrompt.CharacterCard)?.id
            userPreferencesManager.migrateLegacyDefaultCharacterThemeIfEligible(
                activeCharacterCardId = activeCardId,
                defaultCharacterWasCreated = isInitialized && activeCardId == DEFAULT_CHARACTER_CARD_ID,
            )

            if (isInitialized) {
                val defaultAvatarUri = userPreferencesManager
                    .getAiAvatarForCharacterCardFlow(DEFAULT_CHARACTER_CARD_ID)
                    .first()
                if (defaultAvatarUri.isNullOrBlank()) {
                    userPreferencesManager.saveAiAvatarForCharacterCard(
                        DEFAULT_CHARACTER_CARD_ID,
                        "file:///android_asset/operit.png",
                    )
                }
            }

        }

        // 清理历史内置功能标签（chat/voice/desktop pet）
        tagManager.removeLegacyBuiltInTags()
        removeDeletedTagReferencesFromCharacterCards()
        migrateLegacyOtherContentToChat()
    }

    // 重置默认角色卡
    suspend fun resetDefaultCharacterCard() {
        dataStore.edit { preferences ->
            setupDefaultCharacterCard(preferences, DEFAULT_CHARACTER_CARD_ID)
        }
        val defaultTarget = ActivePrompt.CharacterCard(DEFAULT_CHARACTER_CARD_ID)
        val activePromptManager = ActivePromptManager.getInstance(context)
        val currentTheme =
            userPreferencesManager.resolveThemePreferenceSnapshot(
                characterCardId = DEFAULT_CHARACTER_CARD_ID,
            ).values
        activePromptManager.resetThemeDraft(defaultTarget, currentTheme)
        activePromptManager.saveAiAvatarForPrompt(
            defaultTarget,
            "file:///android_asset/operit.png",
        )
    }
    
    private fun setupDefaultCharacterCard(preferences: MutablePreferences, id: String) {
        val nameKey = stringPreferencesKey("character_card_${id}_name")
        val descriptionKey = stringPreferencesKey("character_card_${id}_description")
        val characterSettingKey = stringPreferencesKey("character_card_${id}_character_setting")
        val openingStatementKey = stringPreferencesKey("character_card_${id}_opening_statement")
        val otherContentChatKey = stringPreferencesKey("character_card_${id}_other_content_chat")
        val otherContentVoiceKey = stringPreferencesKey("character_card_${id}_other_content_voice")
        val attachedTagIdsKey = stringSetPreferencesKey("character_card_${id}_attached_tag_ids")
        val advancedCustomPromptKey = stringPreferencesKey("character_card_${id}_advanced_custom_prompt")
        val marksKey = stringPreferencesKey("character_card_${id}_marks")
        val chatModelBindingModeKey = stringPreferencesKey("character_card_${id}_chat_model_binding_mode")
        val chatModelConfigIdKey = stringPreferencesKey("character_card_${id}_chat_model_config_id")
        val chatModelIndexKey = intPreferencesKey("character_card_${id}_chat_model_index")
        val memoryProfileBindingModeKey = stringPreferencesKey("character_card_${id}_memory_profile_binding_mode")
        val memoryProfileIdKey = stringPreferencesKey("character_card_${id}_memory_profile_id")
        val toolAccessConfigKey = toolAccessConfigKey(id)
        val isDefaultKey = booleanPreferencesKey("character_card_${id}_is_default")
        val createdAtKey = longPreferencesKey("character_card_${id}_created_at")
        val updatedAtKey = longPreferencesKey("character_card_${id}_updated_at")

        preferences[nameKey] = DEFAULT_CHARACTER_NAME
        preferences[descriptionKey] = CharacterCardBilingualData.getDefaultDescription(context)
        preferences[characterSettingKey] = CharacterCardBilingualData.getDefaultCharacterSetting(context)
        preferences[openingStatementKey] = ""
        preferences[otherContentChatKey] = CharacterCardBilingualData.getDefaultOtherContentChat(context)
        preferences[otherContentVoiceKey] = CharacterCardBilingualData.getDefaultOtherContentVoice(context)
        preferences[attachedTagIdsKey] = setOf<String>()
        preferences[advancedCustomPromptKey] = ""
        preferences[marksKey] = ""
        preferences[chatModelBindingModeKey] = CharacterCardChatModelBindingMode.FOLLOW_GLOBAL
        preferences.remove(chatModelConfigIdKey)
        preferences[chatModelIndexKey] = 0
        preferences[memoryProfileBindingModeKey] = CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL
        preferences.remove(memoryProfileIdKey)
        preferences.remove(toolAccessConfigKey)
        preferences[isDefaultKey] = true
        preferences[createdAtKey] = System.currentTimeMillis()
        preferences[updatedAtKey] = System.currentTimeMillis()
    }
    
    // 获取所有角色卡
    suspend fun getAllCharacterCards(): List<CharacterCard> {
        val cardIds = characterCardListFlow.first()
        return cardIds.mapNotNull { id ->
            try {
                getCharacterCardFlow(id).first()
            } catch (e: Exception) {
                null
            }
        }
    }

    data class CharacterCardsBackupFile(
        val schema: String = "operit_character_cards_backup_v1",
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val characterCards: List<CharacterCard> = emptyList(),
        val promptTags: List<PromptTag> = emptyList()
    )

    private data class CharacterCardImportPayload(
        val id: String = "",
        val name: String = "",
        val description: String = "",
        val characterSetting: String = "",
        val openingStatement: String = "",
        val otherContentChat: String = "",
        val otherContentVoice: String = "",
        val attachedTagIds: List<String> = emptyList(),
        val advancedCustomPrompt: String = "",
        val marks: String = "",
        val chatModelBindingMode: String = CharacterCardChatModelBindingMode.FOLLOW_GLOBAL,
        val chatModelConfigId: String? = null,
        val chatModelIndex: Int = 0,
        val memoryProfileBindingMode: String = CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL,
        val memoryProfileId: String? = null,
        val toolAccessConfig: CharacterCardToolAccessConfig? = null,
        val isDefault: Boolean = false,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        fun toCharacterCard(): CharacterCard {
            return CharacterCard(
                id = id,
                name = name,
                description = description,
                characterSetting = characterSetting,
                openingStatement = openingStatement,
                otherContentChat = otherContentChat,
                otherContentVoice = otherContentVoice,
                attachedTagIds = attachedTagIds,
                advancedCustomPrompt = advancedCustomPrompt,
                marks = marks,
                chatModelBindingMode = CharacterCardChatModelBindingMode.normalize(chatModelBindingMode),
                chatModelConfigId = chatModelConfigId?.takeIf { it.isNotBlank() },
                chatModelIndex = chatModelIndex.coerceAtLeast(0),
                memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.normalize(memoryProfileBindingMode),
                memoryProfileId = memoryProfileId?.takeIf { it.isNotBlank() },
                toolAccessConfig = toolAccessConfig?.normalized() ?: CharacterCardToolAccessConfig(),
                isDefault = isDefault,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    data class CharacterCardImportResult(
        val new: Int,
        val updated: Int,
        val skipped: Int
    ) {
        val total: Int
            get() = new + updated
    }

    suspend fun exportAllCharacterCardsToBackupFile(): String? {
        return try {
            val cards = getAllCharacterCards()
            val referencedTagIds = cards.flatMap { it.attachedTagIds }.distinct()
            val attachedTags = referencedTagIds.mapNotNull { tagId ->
                runCatching { tagManager.getPromptTagFlow(tagId).first() }.getOrNull()
            }
            val exportDir = OperitBackupDirs.characterCardsDir()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val exportFile = File(exportDir, "character_cards_backup_$timestamp.json")

            val gson = GsonBuilder().setPrettyPrinting().create()
            val wrapper = CharacterCardsBackupFile(characterCards = cards, promptTags = attachedTags)
            exportFile.writeText(gson.toJson(wrapper))
            exportFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "导出角色卡备份失败", e)
            null
        }
    }

    suspend fun importAllCharacterCardsFromBackupContent(jsonContent: String): CharacterCardImportResult {
        if (jsonContent.isBlank()) {
            throw Exception(context.getString(R.string.charactercard_import_file_empty))
        }

        val gson = Gson()
        val root: JsonElement = try {
            JsonParser.parseString(jsonContent)
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.charactercard_json_format_error, e.message ?: ""))
        }

        val (cards, exportedPromptTags) = try {
            if (root.isJsonObject && root.asJsonObject.has("characterCards")) {
                val rootObject = root.asJsonObject
                val cardsArr = rootObject.get("characterCards")
                val parsedCards = gson.fromJson(cardsArr, Array<CharacterCardImportPayload>::class.java)
                    ?.map { it.toCharacterCard() }
                    ?: emptyList()
                val parsedTags = if (rootObject.has("promptTags")) {
                    val tagsArr = rootObject.get("promptTags")
                    gson.fromJson(tagsArr, Array<PromptTag>::class.java)?.toList() ?: emptyList()
                } else {
                    emptyList()
                }
                parsedCards to parsedTags
            } else if (root.isJsonArray) {
                val parsedCards = gson.fromJson(root, Array<CharacterCardImportPayload>::class.java)
                    ?.map { it.toCharacterCard() }
                    ?: emptyList()
                parsedCards to emptyList()
            } else {
                emptyList<CharacterCard>() to emptyList<PromptTag>()
            }
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.charactercard_parse_failed, e.message ?: ""))
        }

        if (cards.isEmpty()) {
            return CharacterCardImportResult(0, 0, 0)
        }

        var newCount = 0
        var updatedCount = 0
        var skippedCount = 0

        val existingIds = characterCardListFlow.first().toSet()
        val importedTagIdMap = importOrReusePromptTags(exportedPromptTags)

        for (card in cards) {
            if (card.id.isBlank() || card.name.isBlank()) {
                skippedCount++
                continue
            }

            val finalCard = if (card.id == DEFAULT_CHARACTER_CARD_ID) {
                card.copy(
                    isDefault = true,
                    attachedTagIds = remapAttachedTagIds(card.attachedTagIds, importedTagIdMap),
                    chatModelBindingMode = CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode),
                    chatModelConfigId = card.chatModelConfigId?.takeIf { it.isNotBlank() },
                    chatModelIndex = card.chatModelIndex.coerceAtLeast(0),
                    memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode),
                    memoryProfileId = card.memoryProfileId?.takeIf { it.isNotBlank() }
                )
            } else {
                card.copy(
                    isDefault = false,
                    attachedTagIds = remapAttachedTagIds(card.attachedTagIds, importedTagIdMap),
                    chatModelBindingMode = CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode),
                    chatModelConfigId = card.chatModelConfigId?.takeIf { it.isNotBlank() },
                    chatModelIndex = card.chatModelIndex.coerceAtLeast(0),
                    memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode),
                    memoryProfileId = card.memoryProfileId?.takeIf { it.isNotBlank() }
                )
            }

            if (existingIds.contains(finalCard.id)) {
                updatedCount++
            } else {
                newCount++
            }

            upsertCharacterCardWithId(finalCard)
        }

        return CharacterCardImportResult(newCount, updatedCount, skippedCount)
    }

    private suspend fun upsertCharacterCardWithId(card: CharacterCard) {
        ActivePromptManager.getInstance(context).runThemeTransition {
            upsertCharacterCardWithIdLocked(card)
        }
    }

    private suspend fun upsertCharacterCardWithIdLocked(card: CharacterCard) {
        var activatedCardId: String? = null
        dataStore.edit { preferences ->
            val id = card.id
            val currentList = preferences[CHARACTER_CARD_LIST]?.toMutableSet() ?: mutableSetOf(DEFAULT_CHARACTER_CARD_ID)
            if (!currentList.contains(id)) {
                currentList.add(id)
                preferences[CHARACTER_CARD_LIST] = currentList
            }

            preferences[stringPreferencesKey("character_card_${id}_name")] = card.name
            preferences[stringPreferencesKey("character_card_${id}_description")] = card.description
            preferences[stringPreferencesKey("character_card_${id}_character_setting")] = card.characterSetting
            preferences[stringPreferencesKey("character_card_${id}_opening_statement")] = card.openingStatement
            preferences[stringPreferencesKey("character_card_${id}_other_content_chat")] = card.otherContentChat
            preferences[stringPreferencesKey("character_card_${id}_other_content_voice")] = card.otherContentVoice
            preferences[stringSetPreferencesKey("character_card_${id}_attached_tag_ids")] = card.attachedTagIds.toSet()
            preferences[stringPreferencesKey("character_card_${id}_advanced_custom_prompt")] = card.advancedCustomPrompt
            preferences[stringPreferencesKey("character_card_${id}_marks")] = card.marks
            preferences[stringPreferencesKey("character_card_${id}_chat_model_binding_mode")] =
                CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode)
            val chatModelConfigIdKey = stringPreferencesKey("character_card_${id}_chat_model_config_id")
            if (card.chatModelConfigId.isNullOrBlank()) {
                preferences.remove(chatModelConfigIdKey)
            } else {
                preferences[chatModelConfigIdKey] = card.chatModelConfigId
            }
            preferences[intPreferencesKey("character_card_${id}_chat_model_index")] = card.chatModelIndex.coerceAtLeast(0)
            preferences[stringPreferencesKey("character_card_${id}_memory_profile_binding_mode")] =
                CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode)
            val memoryProfileIdKey = stringPreferencesKey("character_card_${id}_memory_profile_id")
            if (card.memoryProfileId.isNullOrBlank()) {
                preferences.remove(memoryProfileIdKey)
            } else {
                preferences[memoryProfileIdKey] = card.memoryProfileId
            }
            writeToolAccessConfig(preferences, id, card.toolAccessConfig)
            preferences[booleanPreferencesKey("character_card_${id}_is_default")] = card.isDefault
            preferences[longPreferencesKey("character_card_${id}_created_at")] = card.createdAt
            preferences[longPreferencesKey("character_card_${id}_updated_at")] = card.updatedAt

            if (preferences[ACTIVE_CHARACTER_CARD_ID] == null) {
                preferences[ACTIVE_CHARACTER_CARD_ID] = DEFAULT_CHARACTER_CARD_ID
                activatedCardId = DEFAULT_CHARACTER_CARD_ID
            }
        }

        if (card.id != DEFAULT_CHARACTER_CARD_ID) {
            if (!userPreferencesManager.hasCharacterCardTheme(card.id)) {
                createDefaultThemeForCharacterCard(card.id, resolveEmojiSourcePrompt())
            }
        }

        val activeGroupId = CharacterGroupCardManager.getInstance(context)
            .observeActiveCharacterGroupId()
            .first()
        if (activatedCardId != null && activeGroupId.isNullOrBlank()) {
            switchToCharacterCardWaifuSettings(activatedCardId!!)
        }
    }

    private suspend fun resolveEmojiSourcePrompt(): ActivePrompt {
        val activeGroupId = CharacterGroupCardManager.getInstance(context).observeActiveCharacterGroupId().first()
        if (!activeGroupId.isNullOrBlank()) {
            return ActivePrompt.CharacterGroup(activeGroupId)
        }

        val activeCardId = observeActiveCharacterCardId().first()
        return ActivePrompt.CharacterCard(activeCardId ?: DEFAULT_CHARACTER_CARD_ID)
    }
    
    // 根据角色名查找角色卡
    suspend fun findCharacterCardByName(name: String): CharacterCard? {
        val allCards = getAllCharacterCards()
        return allCards.find { it.name == name }
    }
    
    /**
     * 从酒馆角色卡JSON字符串创建角色卡
     */
    suspend fun createCharacterCardFromTavernJson(jsonString: String): Result<String> {
        return try {
            val gson = Gson()
            val tavernCard = gson.fromJson(jsonString, TavernCharacterCard::class.java)
            
            if (tavernCard.data.name.isBlank()) {
                return Result.failure(Exception(context.getString(R.string.charactercard_name_empty)))
            }

            // --- New logic for Character Book ---
            var worldBookTagId: String? = null
            tavernCard.data.character_book?.let { book ->
                if (book.entries.isNotEmpty()) {
                    val worldBookContent = buildString {
                        book.entries.forEach { entry ->
                            if (entry.content.isNotBlank()) {
                                // 使用更结构化的格式作为提示词
                                append("[${entry.name}]\n${entry.content}\n\n")
                            }
                        }
                    }.trim()

                    if (worldBookContent.isNotBlank()) {
                        worldBookTagId = tagManager.createOrReusePromptTag(
                            name = CharacterCardBilingualData.getWorldBookTagName(context, tavernCard.data.name),
                            description = CharacterCardBilingualData.getWorldBookTagDescription(context, tavernCard.data.name),
                            promptContent = worldBookContent,
                            tagType = TagType.FUNCTION
                        )
                    }
                }
            }

            val operitPayload = tavernCard.data.extensions?.operit
                ?.takeIf { it.schema == "operit_character_card_v1" }
                ?.character_card

            val importedOperitTags = if (operitPayload != null) {
                importOrReuseOperitTags(operitPayload.attachedTags)
            } else {
                ImportedTagResult(emptyMap(), emptyList())
            }

            val characterCard = if (operitPayload != null) {
                CharacterCard(
                    id = "",
                    name = operitPayload.name,
                    description = operitPayload.description,
                    characterSetting = operitPayload.characterSetting,
                    openingStatement = operitPayload.openingStatement,
                    otherContentChat = operitPayload.otherContentChat.ifBlank { operitPayload.otherContent },
                    otherContentVoice = operitPayload.otherContentVoice,
                    attachedTagIds = if (operitPayload.attachedTagIds.isNotEmpty()) {
                        remapAttachedTagIds(
                            sourceIds = operitPayload.attachedTagIds,
                            idMap = importedOperitTags.idMap
                        )
                    } else {
                        if (importedOperitTags.importedIds.isNotEmpty()) {
                            importedOperitTags.importedIds
                        } else {
                            importedOperitTags.idMap.values.distinct()
                        }
                    },
                    advancedCustomPrompt = operitPayload.advancedCustomPrompt,
                    marks = operitPayload.marks,
                    chatModelBindingMode = CharacterCardChatModelBindingMode.normalize(operitPayload.chatModelBindingMode),
                    chatModelConfigId = operitPayload.chatModelConfigId?.takeIf { it.isNotBlank() },
                    chatModelIndex = operitPayload.chatModelIndex.coerceAtLeast(0),
                    memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.normalize(operitPayload.memoryProfileBindingMode),
                    memoryProfileId = operitPayload.memoryProfileId?.takeIf { it.isNotBlank() },
                    toolAccessConfig = operitPayload.toolAccessConfig?.normalized() ?: CharacterCardToolAccessConfig(),
                    isDefault = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                convertTavernCardToCharacterCard(tavernCard)
            }

            val finalCard = worldBookTagId?.let {
                characterCard.copy(attachedTagIds = (characterCard.attachedTagIds + it).distinct())
            } ?: characterCard

            val id = createCharacterCard(finalCard)
            Result.success(id)
        } catch (e: JsonSyntaxException) {
            Result.failure(Exception(context.getString(R.string.charactercard_json_format_error, e.message ?: "")))
        } catch (e: Exception) {
            Result.failure(Exception(context.getString(R.string.charactercard_parse_failed, e.message ?: "")))
        }
    }
    
    /**
     * 从PNG图片文件中提取酒馆角色卡数据
     */
    suspend fun createCharacterCardFromTavernPng(inputStream: InputStream): Result<String> {
        return try {
            val jsonString = extractJsonFromPng(inputStream)
            createCharacterCardFromTavernJson(jsonString)
        } catch (e: Exception) {
            Result.failure(Exception(context.getString(R.string.charactercard_png_parse_failed, e.message ?: "")))
        }
    }

    suspend fun exportCharacterCardToTavernJson(characterCardId: String): Result<String> {
        return try {
            val card = getCharacterCard(characterCardId)
            val attachedTags = card.attachedTagIds.mapNotNull { tagId ->
                runCatching { tagManager.getPromptTagFlow(tagId).first() }.getOrNull()
            }
            val tagNames = attachedTags.map { it.name }

            val operitExt = OperitTavernExtension(
                character_card = OperitCharacterCardPayload(
                    name = card.name,
                    description = card.description,
                    characterSetting = card.characterSetting,
                    openingStatement = card.openingStatement,
                    otherContent = card.otherContentChat,
                    otherContentChat = card.otherContentChat,
                    otherContentVoice = card.otherContentVoice,
                    attachedTagIds = card.attachedTagIds,
                    attachedTags = attachedTags.map { tag ->
                        OperitAttachedTagPayload(
                            id = tag.id,
                            name = tag.name,
                            description = tag.description,
                            promptContent = tag.promptContent,
                            tagType = tag.tagType.name
                        )
                    },
                    advancedCustomPrompt = card.advancedCustomPrompt,
                    marks = card.marks,
                    chatModelBindingMode = CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode),
                    chatModelConfigId = card.chatModelConfigId?.takeIf { it.isNotBlank() },
                    chatModelIndex = card.chatModelIndex.coerceAtLeast(0),
                    memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode),
                    memoryProfileId = card.memoryProfileId?.takeIf { it.isNotBlank() },
                    toolAccessConfig = card.toolAccessConfig.normalized()
                )
            )

            val tavernCard = TavernCharacterCard(
                spec = "chara_card_v2",
                spec_version = "2.0",
                data = TavernCharacterData(
                    name = card.name,
                    description = card.description,
                    personality = "",
                    first_mes = card.openingStatement,
                    avatar = "",
                    mes_example = card.otherContentChat,
                    scenario = "",
                    creator_notes = card.marks,
                    system_prompt = card.characterSetting,
                    post_history_instructions = card.advancedCustomPrompt,
                    alternate_greetings = emptyList(),
                    tags = tagNames,
                    creator = "",
                    character_version = "",
                    extensions = TavernExtensions(operit = operitExt),
                    character_book = null
                )
            )

            val gson = Gson()
            Result.success(gson.toJson(tavernCard))
        } catch (e: Exception) {
            Result.failure(Exception(context.getString(R.string.charactercard_export_failed, e.message ?: "")))
        }
    }
    
    /**
     * 从PNG图片的tEXt块中提取JSON数据
     */
    private fun extractJsonFromPng(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        
        // PNG文件头检查
        if (bytes.size < 8 || !isPngHeader(bytes)) {
            throw Exception(context.getString(R.string.charactercard_invalid_png))
        }
        
        var offset = 8 // 跳过PNG头
        
        while (offset < bytes.size - 12) { // 确保有足够的字节读取块头
            // 读取块长度
            val chunkLength = readUInt32BigEndian(bytes, offset)
            offset += 4
            
            // 读取块类型
            val chunkType = String(bytes.sliceArray(offset until offset + 4), Charsets.ISO_8859_1)
            offset += 4
            
            // 如果是tEXt块
            if (chunkType == "tEXt") {
                val chunkData = bytes.sliceArray(offset until offset + chunkLength.toInt())
                val textData = String(chunkData, Charsets.ISO_8859_1)
                
                // 查找关键字"chara"
                val nullIndex = textData.indexOf('\u0000')
                if (nullIndex > 0) {
                    val keyword = textData.substring(0, nullIndex)
                    if (keyword == "chara") {
                        val base64Data = textData.substring(nullIndex + 1)
                        return decodeBase64ToJson(base64Data)
                    }
                }
            }
            
            // 跳到下一个块 (数据长度 + 4字节CRC)
            offset += chunkLength.toInt() + 4
        }
        
        throw Exception(context.getString(R.string.charactercard_no_data_in_png))
    }
    
    /**
     * 检查PNG文件头
     */
    private fun isPngHeader(bytes: ByteArray): Boolean {
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        return bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(pngSignature)
    }
    
    /**
     * 从字节数组中读取大端序的32位无符号整数
     */
    private fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Long {
        return (((bytes[offset].toInt() and 0xFF) shl 24) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
               (bytes[offset + 3].toInt() and 0xFF)).toLong()
    }
    
    /**
     * 解码Base64数据为JSON字符串
     */
    private fun decodeBase64ToJson(base64Data: String): String {
        try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            return String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.charactercard_base64_decode_failed, e.message ?: ""))
        }
    }

    private suspend fun importOrReusePromptTags(exportedTags: List<PromptTag>): Map<String, String> {
        val idMap = mutableMapOf<String, String>()
        exportedTags.forEach { exportedTag ->
            val safeTagTypeName = runCatching { exportedTag.tagType.name }.getOrDefault(TagType.CUSTOM.name)
            val localTagId = importOrReuseTag(
                exportedTagId = exportedTag.id,
                name = exportedTag.name,
                description = exportedTag.description,
                promptContent = exportedTag.promptContent,
                tagTypeName = safeTagTypeName
            )
            if (localTagId != null && exportedTag.id.isNotBlank()) {
                idMap[exportedTag.id] = localTagId
            }
        }
        return idMap
    }

    private data class ImportedTagResult(
        val idMap: Map<String, String>,
        val importedIds: List<String>
    )

    private suspend fun importOrReuseOperitTags(exportedTags: List<OperitAttachedTagPayload>): ImportedTagResult {
        val idMap = mutableMapOf<String, String>()
        val importedIds = mutableListOf<String>()
        exportedTags.forEach { exportedTag ->
            val localTagId = importOrReuseTag(
                exportedTagId = exportedTag.id,
                name = exportedTag.name,
                description = exportedTag.description,
                promptContent = exportedTag.promptContent,
                tagTypeName = exportedTag.tagType
            )
            if (localTagId != null) {
                importedIds.add(localTagId)
                if (exportedTag.id.isNotBlank()) {
                    idMap[exportedTag.id] = localTagId
                }
            }
        }
        return ImportedTagResult(idMap = idMap, importedIds = importedIds.distinct())
    }

    private suspend fun importOrReuseTag(
        exportedTagId: String,
        name: String,
        description: String,
        promptContent: String,
        tagTypeName: String
    ): String? {
        if (name.isBlank() && description.isBlank() && promptContent.isBlank()) {
            return null
        }

        val existingTagByContent = tagManager.findTagWithSameContent(promptContent)
        if (existingTagByContent != null) {
            return existingTagByContent.id
        }

        val safeTagType = runCatching { TagType.valueOf(tagTypeName) }.getOrDefault(TagType.CUSTOM)
        val fallbackName = exportedTagId.ifBlank { "Imported Tag" }
        return tagManager.createPromptTag(
            name = name.ifBlank { fallbackName },
            description = description,
            promptContent = promptContent,
            tagType = safeTagType
        )
    }

    private fun remapAttachedTagIds(
        sourceIds: List<String>,
        idMap: Map<String, String>
    ): List<String> {
        val remapped = sourceIds.map { sourceId ->
            idMap[sourceId] ?: sourceId
        }

        return remapped.distinct()
    }
    
    /**
     * 将酒馆角色卡转换为本地角色卡格式
     */
    private suspend fun convertTavernCardToCharacterCard(tavernCard: TavernCharacterCard): CharacterCard {
        val data = tavernCard.data
        
        // 组合角色设定
        val characterSetting = buildString {
            if (data.description.isNotBlank()) {
                append(CharacterCardBilingualData.getCharacterDescriptionLabel(context))
                append("\n${data.description}\n\n")
            }
            if (data.personality.isNotBlank()) {
                append(CharacterCardBilingualData.getPersonalityLabel(context))
                append("\n${data.personality}\n\n")
            }
            if (data.scenario.isNotBlank()) {
                append(CharacterCardBilingualData.getScenarioLabel(context))
                append("\n${data.scenario}\n\n")
            }
        }.trim()

        // 组合其他内容
        val otherContentChat = buildString {
            if (data.mes_example.isNotBlank()) {
                append(CharacterCardBilingualData.getDialogueExampleLabel(context))
                append("\n${data.mes_example}\n\n")
            }
            if (data.system_prompt.isNotBlank()) {
                append(CharacterCardBilingualData.getSystemPromptLabel(context))
                append("\n${data.system_prompt}\n\n")
            }
            if (data.post_history_instructions.isNotBlank()) {
                append(CharacterCardBilingualData.getPostHistoryInstructionsLabel(context))
                append("\n${data.post_history_instructions}\n\n")
            }

            // 添加备用问候语
            if (data.alternate_greetings.isNotEmpty()) {
                append(CharacterCardBilingualData.getAlternateGreetingsLabel(context))
                data.alternate_greetings.forEachIndexed { index, greeting ->
                    append("${index + 1}. $greeting\n")
                }
                append("\n")
            }
        }.trim()

        // 组合高级自定义提示词
        val advancedCustomPrompt = buildString {


            data.extensions?.depth_prompt?.let { depthPrompt ->
                if (depthPrompt.prompt.isNotBlank()) {
                    append(CharacterCardBilingualData.getDepthPromptLabel(context))
                    append("\n${depthPrompt.prompt}\n\n")
                }
            }
        }.trim()

        // 生成描述（简化版，不包含作者信息）
        val description = if (data.tags.isNotEmpty()) {
            CharacterCardBilingualData.getTagsLabel(context) + data.tags.take(5).joinToString(", ") +
            if (data.tags.size > 5) CharacterCardBilingualData.getEtAlLabel(context) + "${data.tags.size}" else ""
        } else ""

        // 生成备注信息
        val marks = buildString {
            append(CharacterCardBilingualData.getSourceLabel(context))
            if (data.creator.isNotBlank()) {
                append(CharacterCardBilingualData.getAuthorLabel(context))
                append("${data.creator}\n")
            }
            if (data.creator_notes.isNotBlank()) {
                append(CharacterCardBilingualData.getAuthorNotesLabel(context))
                append("${data.creator_notes}\n\n")
            }
            if (data.character_version.isNotBlank()) {
                append(CharacterCardBilingualData.getVersionLabel(context))
                append("${data.character_version}\n")
            }
            if (data.tags.isNotEmpty()) {
                append(CharacterCardBilingualData.getOriginalTagsLabel(context))
                append("${data.tags.joinToString(", ")}\n")
            }
            if (tavernCard.spec.isNotBlank()) {
                append(CharacterCardBilingualData.getFormatLabel(context))
                append("${tavernCard.spec}")
                if (tavernCard.spec_version.isNotBlank()) {
                    append(" v${tavernCard.spec_version}")
                }
                append("\n")
            }
        }.trim()
        
        return CharacterCard(
            id = "", // 将在createCharacterCard中生成
            name = data.name,
            description = description,
            characterSetting = characterSetting,
            openingStatement = data.first_mes, // 从first_mes获取开场白
            otherContentChat = otherContentChat,
            otherContentVoice = "",
            attachedTagIds = emptyList(), // 可以后续根据tags创建标签
            advancedCustomPrompt = advancedCustomPrompt,
            marks = marks,
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun removeDeletedTagReferencesFromCharacterCards() {
        val validTagIds = tagManager.getAllTags().map { it.id }.toSet()
        dataStore.edit { preferences ->
            val cardIds = preferences[CHARACTER_CARD_LIST]?.toSet() ?: setOf(DEFAULT_CHARACTER_CARD_ID)
            cardIds.forEach { cardId ->
                val attachedKey = stringSetPreferencesKey("character_card_${cardId}_attached_tag_ids")
                val attached = preferences[attachedKey] ?: return@forEach
                val filtered = attached.filterTo(mutableSetOf()) { it in validTagIds }
                if (filtered.size != attached.size) {
                    preferences[attachedKey] = filtered
                }
            }
        }
    }

    private suspend fun migrateLegacyOtherContentToChat() {
        dataStore.edit { preferences ->
            val cardIds = preferences[CHARACTER_CARD_LIST]?.toSet() ?: setOf(DEFAULT_CHARACTER_CARD_ID)
            cardIds.forEach { cardId ->
                val legacyKey = stringPreferencesKey("character_card_${cardId}_other_content")
                val chatKey = stringPreferencesKey("character_card_${cardId}_other_content_chat")
                val voiceKey = stringPreferencesKey("character_card_${cardId}_other_content_voice")
                val legacyValue = preferences[legacyKey]
                if (!legacyValue.isNullOrBlank() && preferences[chatKey].isNullOrBlank()) {
                    preferences[chatKey] = legacyValue
                }
                if (preferences[voiceKey].isNullOrBlank() && cardId == DEFAULT_CHARACTER_CARD_ID) {
                    preferences[voiceKey] = CharacterCardBilingualData.getDefaultOtherContentVoice(context)
                }
                preferences.remove(legacyKey)
            }
        }
    }

    /**
     * 切换到指定角色卡的Waifu模式配置
     */
    private suspend fun switchToCharacterCardWaifuSettings(characterCardId: String) {
        try {
            // 始终调用切换方法，即使角色卡没有配置也会清空当前配置，避免保留上一个角色卡的设置
            waifuPreferences.switchToCharacterCardWaifuSettings(characterCardId)
            
            if (waifuPreferences.hasCharacterCardWaifuSettings(characterCardId)) {
                AppLogger.d("CharacterCardManager", "已切换到角色卡 $characterCardId 的Waifu模式配置")
            } else {
                AppLogger.d("CharacterCardManager", "角色卡 $characterCardId 没有Waifu模式配置，已清空当前配置")
            }
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "切换角色卡Waifu模式配置失败", e)
        }
    }

    /**
     * 为当前活跃角色卡保存Waifu模式配置
     */
    suspend fun saveWaifuSettingsForActiveCharacterCard() {
        try {
            val activeCard = activeCharacterCardFlow.first()
            if (activeCard != null) {
                waifuPreferences.saveCurrentWaifuSettingsToCharacterCard(activeCard.id)
                AppLogger.d("CharacterCardManager", "已为角色卡 ${activeCard.id} 保存Waifu模式配置")
            }
        } catch (e: Exception) {
            AppLogger.e("CharacterCardManager", "为活跃角色卡保存Waifu配置失败", e)
        }
    }
} 

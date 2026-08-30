package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.LegacyUserProfile
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.util.LocaleUtils.LanguageCodes

private val Context.userPreferencesDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "user_preferences")

// 向后兼容的全局实例访问方式
val preferencesManager: UserPreferencesManager
    get() = UserPreferencesManager.instance ?: throw IllegalStateException(
        "UserPreferencesManager not initialized. Call UserPreferencesManager.getInstance(context) first."
    )

fun initUserPreferencesManager(context: Context, defaultProfileName: String = "Default") {
    val manager = UserPreferencesManager.getInstance(context)

    // Migration must finish before the default memory space is created. Otherwise a fresh default
    // entry could hide released profile metadata that still owns existing ObjectBox databases.
    GlobalScope.launch {
        MemorySpaceProfileDocumentRepository.getInstance(context).initialize()
        manager.ensureDefaultMemorySpace(defaultProfileName)
    }
}

data class LegacyUserProfileSnapshot(
    val activeProfileId: String,
    val profiles: List<LegacyUserProfile>,
    val hasLegacyCategoryLocks: Boolean = false
)

class UserPreferencesManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        internal val instance: UserPreferencesManager?
            get() = INSTANCE

        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext ?: context
                    UserPreferencesManager(appContext).also { INSTANCE = it }
                }
            }
        }

        // Released structured-profile keys. These are read only by schema-v2 migration.
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val PROFILE_LIST = stringPreferencesKey("profile_list")

        // Memory spaces replace preference profiles while retaining their stable identifiers.
        private val ACTIVE_MEMORY_SPACE_ID = stringPreferencesKey("active_memory_space_id")
        private val MEMORY_SPACE_LIST = stringPreferencesKey("memory_space_list")

        // 应用语言设置
        private val APP_LANGUAGE = stringPreferencesKey("app_language")

        // 分类锁定状态
        private val BIRTH_DATE_LOCKED = booleanPreferencesKey("birth_date_locked")
        private val GENDER_LOCKED = booleanPreferencesKey("gender_locked")
        private val PERSONALITY_LOCKED = booleanPreferencesKey("personality_locked")
        private val IDENTITY_LOCKED = booleanPreferencesKey("identity_locked")
        private val OCCUPATION_LOCKED = booleanPreferencesKey("occupation_locked")
        private val AI_STYLE_LOCKED = booleanPreferencesKey("ai_style_locked")

        // 主题存储迁移标记与目标元数据键
        private val THEME_METADATA_MIGRATION_COMPLETED =
            booleanPreferencesKey("theme_metadata_migration_completed")
        private val KEY_CUSTOM_AI_AVATAR_URI = stringPreferencesKey("custom_ai_avatar_uri")
        private val KEY_CUSTOM_CHAT_TITLE = stringPreferencesKey("custom_chat_title")
        // 默认配置文件ID
        private const val DEFAULT_PROFILE_ID = "default"


        // AppBar 内容颜色模式常量

        // 背景媒体类型常量
        
        // 默认语言
        const val DEFAULT_LANGUAGE = LanguageCodes.AUTO

        // Sidebar software identity (drawer header brand text)
        const val SOFTWARE_IDENTITY_OPERIT = "operit_ai"
        const val SOFTWARE_IDENTITY_LINGSHU = "lingshu_ai"



        private val KEY_UI_ACCESSIBILITY_MODE = booleanPreferencesKey("ui_accessibility_mode")
        private val KEY_BETA_PLAN_ENABLED = booleanPreferencesKey("beta_plan_enabled")
        private val KEY_SOFTWARE_IDENTITY = stringPreferencesKey("software_identity")


        // 布局调整设置
        private val CHAT_SETTINGS_BUTTON_END_PADDING = floatPreferencesKey("chat_settings_button_end_padding")
        private val CHAT_AREA_HORIZONTAL_PADDING = floatPreferencesKey("chat_area_horizontal_padding")
        private val AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER =
            floatPreferencesKey("global_text_line_height_multiplier")
        private val AI_MARKDOWN_LETTER_SPACING =
            floatPreferencesKey("global_text_letter_spacing")
        private val AI_MARKDOWN_PARAGRAPH_SPACING =
            floatPreferencesKey("ai_markdown_paragraph_spacing")
        private val CONVERT_LONG_PASTED_TEXT_TO_FILE =
            booleanPreferencesKey("convert_long_pasted_text_to_file")
        private val LONG_PASTED_TEXT_FILE_THRESHOLD =
            intPreferencesKey("long_pasted_text_file_threshold")

        // 最近使用颜色




        // 字体类型常量
        
        // 系统字体名称常量
        const val SYSTEM_FONT_SERIF = "serif"
        const val SYSTEM_FONT_SANS_SERIF = "sans-serif"
        const val SYSTEM_FONT_MONOSPACE = "monospace"
        const val SYSTEM_FONT_CURSIVE = "cursive"

        const val DEFAULT_LONG_PASTED_TEXT_FILE_THRESHOLD = 3000
    }

    // 获取应用语言设置
    val appLanguage: Flow<String> = 
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[APP_LANGUAGE] ?: DEFAULT_LANGUAGE
            }
    
    // 保存应用语言设置
    suspend fun saveAppLanguage(languageCode: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = languageCode
        }
    }
    
    // 同步获取当前语言设置
    fun getCurrentLanguage(): String {
        return runBlocking {
            appLanguage.first()
        }
    }

    suspend fun saveUiAccessibilityMode(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[KEY_UI_ACCESSIBILITY_MODE] = enabled
        }
    }

    suspend fun saveBetaPlanEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[KEY_BETA_PLAN_ENABLED] = enabled
        }
    }

    suspend fun saveSoftwareIdentity(identity: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[KEY_SOFTWARE_IDENTITY] = identity
        }
    }

    fun isUiAccessibilityModeEnabled(): Boolean {
        return runBlocking {
            uiAccessibilityMode.first()
        }
    }

    fun isBetaPlanEnabled(): Boolean {
        return runBlocking {
            betaPlanEnabled.first()
        }
    }

    val activeMemorySpaceIdFlow: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[ACTIVE_MEMORY_SPACE_ID] ?: DEFAULT_PROFILE_ID
        }

    val memorySpaceListFlow: Flow<List<String>> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[MEMORY_SPACE_LIST]
                ?.let { Json.decodeFromString<List<String>>(it) }
                .orEmpty()
        }

    suspend fun hasMemorySpaceMetadata(): Boolean {
        return context.userPreferencesDataStore.data.first().contains(MEMORY_SPACE_LIST)
    }

    /**
     * A raw +4 snapshot can be restored while a newer process has already created a default
     * memory space. The legacy list is still authoritative until its records are consumed.
     */
    suspend fun hasLegacyUserProfileMetadata(): Boolean {
        return context.userPreferencesDataStore.data.first().contains(PROFILE_LIST)
    }

    fun getMemorySpaceFlow(memorySpaceId: String = ""): Flow<MemorySpace> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val targetId =
                memorySpaceId.ifBlank {
                    preferences[ACTIVE_MEMORY_SPACE_ID] ?: DEFAULT_PROFILE_ID
                }
            val encoded =
                requireNotNull(preferences[stringPreferencesKey("memory_space_$targetId")]) {
                    "Missing memory space metadata: $targetId"
                }
            Json.decodeFromString<MemorySpace>(encoded)
        }
    }

    suspend fun ensureDefaultMemorySpace(defaultName: String) {
        val ids = memorySpaceListFlow.first()
        val storedDefault =
            context.userPreferencesDataStore.data.first()[stringPreferencesKey("memory_space_$DEFAULT_PROFILE_ID")]
        if (!ids.contains(DEFAULT_PROFILE_ID) || storedDefault == null) {
            createMemorySpace(defaultName, isDefault = true)
        }
    }

    suspend fun createMemorySpace(name: String, isDefault: Boolean = false): String {
        val id = if (isDefault) DEFAULT_PROFILE_ID else "memory_${System.currentTimeMillis()}"
        val space = MemorySpace(id, name)
        context.userPreferencesDataStore.edit { preferences ->
            val ids = decodeIdList(preferences[MEMORY_SPACE_LIST]).toMutableList()
            if (!ids.contains(id)) ids.add(id)
            preferences[MEMORY_SPACE_LIST] = Json.encodeToString(ids)
            preferences[stringPreferencesKey("memory_space_$id")] = Json.encodeToString(space)
            if (preferences[ACTIVE_MEMORY_SPACE_ID] == null) {
                preferences[ACTIVE_MEMORY_SPACE_ID] = id
            }
        }
        MemorySpaceProfileDocumentRepository.getInstance(context).load(id)
        return id
    }

    suspend fun setActiveMemorySpace(memorySpaceId: String) {
        context.userPreferencesDataStore.edit { preferences ->
            val ids = decodeIdList(preferences[MEMORY_SPACE_LIST])
            require(ids.contains(memorySpaceId)) { "Unknown memory space: $memorySpaceId" }
            preferences[ACTIVE_MEMORY_SPACE_ID] = memorySpaceId
        }
    }

    suspend fun updateMemorySpace(space: MemorySpace) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[stringPreferencesKey("memory_space_${space.id}")] = Json.encodeToString(space)
        }
    }

    suspend fun deleteMemorySpace(memorySpaceId: String) {
        if (memorySpaceId == DEFAULT_PROFILE_ID) return
        val characterCardManager = CharacterCardManager.getInstance(context)
        characterCardManager.getAllCharacterCards()
            .filter { it.memoryProfileId == memorySpaceId }
            .forEach { card ->
                characterCardManager.updateCharacterCard(
                    card.copy(
                        memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL,
                        memoryProfileId = null
                    )
                )
            }
        context.userPreferencesDataStore.edit { preferences ->
            val ids = decodeIdList(preferences[MEMORY_SPACE_LIST]).toMutableList()
            ids.remove(memorySpaceId)
            preferences[MEMORY_SPACE_LIST] = Json.encodeToString(ids)
            preferences.remove(stringPreferencesKey("memory_space_$memorySpaceId"))
            if (preferences[ACTIVE_MEMORY_SPACE_ID] == memorySpaceId) {
                preferences[ACTIVE_MEMORY_SPACE_ID] = DEFAULT_PROFILE_ID
            }
        }
        MemorySpaceProfileDocumentRepository.getInstance(context).delete(memorySpaceId)
        ObjectBoxManager.delete(context, memorySpaceId)
    }

    suspend fun readLegacyUserProfiles(): LegacyUserProfileSnapshot {
        val preferences = context.userPreferencesDataStore.data.first()
        if (preferences[PROFILE_LIST] == null && preferences[MEMORY_SPACE_LIST] != null) {
            // A process may stop after the DataStore rewrite and before the separate schema marker
            // is committed. Reconstructing the snapshot from the new keys makes migration
            // idempotent and prevents a retry from collapsing existing spaces to only "default".
            val memorySpaceIds = decodeIdList(preferences[MEMORY_SPACE_LIST]).toMutableList()
            if (!memorySpaceIds.contains(DEFAULT_PROFILE_ID)) {
                memorySpaceIds.add(0, DEFAULT_PROFILE_ID)
            }
            val spaces = memorySpaceIds.distinct().map { id ->
                val encoded =
                    requireNotNull(preferences[stringPreferencesKey("memory_space_$id")]) {
                        "Missing migrated memory space metadata: $id"
                    }
                val name = Json.decodeFromString<MemorySpace>(encoded).name
                LegacyUserProfile(id = id, name = name)
            }
            return LegacyUserProfileSnapshot(
                activeProfileId = preferences[ACTIVE_MEMORY_SPACE_ID] ?: DEFAULT_PROFILE_ID,
                profiles = spaces
            )
        }

        val activeId = preferences[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
        val ids = decodeIdList(preferences[PROFILE_LIST]).toMutableList()
        if (!ids.contains(DEFAULT_PROFILE_ID)) ids.add(0, DEFAULT_PROFILE_ID)
        val profiles = ids.distinct().map { id ->
            val encoded = preferences[stringPreferencesKey("profile_$id")]
            if (encoded == null) {
                createDefaultProfile(id)
            } else {
                Json.decodeFromString<LegacyUserProfile>(encoded)
            }
        }
        val hasLegacyCategoryLocks =
            listOf(
                BIRTH_DATE_LOCKED,
                GENDER_LOCKED,
                PERSONALITY_LOCKED,
                IDENTITY_LOCKED,
                OCCUPATION_LOCKED,
                AI_STYLE_LOCKED
            ).any { preferences[it] == true }
        return LegacyUserProfileSnapshot(activeId, profiles, hasLegacyCategoryLocks)
    }

    suspend fun migrateLegacyProfilesToMemorySpaces(snapshot: LegacyUserProfileSnapshot) {
        context.userPreferencesDataStore.edit { preferences ->
            val profiles =
                snapshot.profiles.ifEmpty {
                    listOf(createDefaultProfile(DEFAULT_PROFILE_ID))
                }
            val ids = profiles.map { it.id }.distinct().toMutableList()
            if (!ids.contains(DEFAULT_PROFILE_ID)) ids.add(0, DEFAULT_PROFILE_ID)
            preferences[MEMORY_SPACE_LIST] = Json.encodeToString(ids)
            val activeId = snapshot.activeProfileId.takeIf(ids::contains) ?: DEFAULT_PROFILE_ID
            preferences[ACTIVE_MEMORY_SPACE_ID] = activeId
            profiles.forEach { profile ->
                // A released partial category lock cannot be represented as a document-wide
                // lock. Locking the document avoids rewriting a field the user previously
                // protected; the new memory-space UI lets the user choose the new policy.
                val space = MemorySpace(
                    id = profile.id,
                    name = profile.name,
                    profileAutoUpdateLocked = snapshot.hasLegacyCategoryLocks
                )
                preferences[stringPreferencesKey("memory_space_${profile.id}")] =
                    Json.encodeToString(space)
                preferences.remove(stringPreferencesKey("profile_${profile.id}"))
            }
            preferences.remove(ACTIVE_PROFILE_ID)
            preferences.remove(PROFILE_LIST)
            preferences.remove(BIRTH_DATE_LOCKED)
            preferences.remove(GENDER_LOCKED)
            preferences.remove(PERSONALITY_LOCKED)
            preferences.remove(IDENTITY_LOCKED)
            preferences.remove(OCCUPATION_LOCKED)
            preferences.remove(AI_STYLE_LOCKED)
        }
    }

    private fun decodeIdList(encoded: String?): List<String> {
        return encoded?.let { Json.decodeFromString<List<String>>(it) }.orEmpty()
    }

    private fun createDefaultProfile(profileId: String): LegacyUserProfile {
        return LegacyUserProfile(
            id = profileId,
            name = if (profileId == DEFAULT_PROFILE_ID) "Default" else profileId
        )
    }

    val uiAccessibilityMode: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_UI_ACCESSIBILITY_MODE] ?: false
        }

    val betaPlanEnabled: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_BETA_PLAN_ENABLED] ?: false
        }

    val softwareIdentity: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SOFTWARE_IDENTITY] ?: SOFTWARE_IDENTITY_OPERIT
        }

    // 布局调整设置
    val chatSettingsButtonEndPadding: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CHAT_SETTINGS_BUTTON_END_PADDING] ?: 2f // 默认2dp
        }

    val chatAreaHorizontalPadding: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CHAT_AREA_HORIZONTAL_PADDING] ?: 16f // 默认16dp
        }

    val aiMarkdownLineHeightMultiplier: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER] ?: 1f
        }

    val aiMarkdownLetterSpacing: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[AI_MARKDOWN_LETTER_SPACING] ?: 0f
        }

    val aiMarkdownParagraphSpacing: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[AI_MARKDOWN_PARAGRAPH_SPACING] ?: 12f
        }

    val convertLongPastedTextToFile: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CONVERT_LONG_PASTED_TEXT_TO_FILE] ?: true
        }

    val longPastedTextFileThreshold: Flow<Int> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[LONG_PASTED_TEXT_FILE_THRESHOLD]
                ?: DEFAULT_LONG_PASTED_TEXT_FILE_THRESHOLD
        }

    // 保存聊天设置按钮右边距
    suspend fun saveChatSettingsButtonEndPadding(padding: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CHAT_SETTINGS_BUTTON_END_PADDING] = padding
        }
    }

    // 保存聊天区域水平内边距
    suspend fun saveChatAreaHorizontalPadding(padding: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CHAT_AREA_HORIZONTAL_PADDING] = padding
        }
    }

    suspend fun saveAiMarkdownLineHeightMultiplier(value: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER] = value
        }
    }

    suspend fun saveAiMarkdownLetterSpacing(value: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[AI_MARKDOWN_LETTER_SPACING] = value
        }
    }

    suspend fun saveAiMarkdownParagraphSpacing(value: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[AI_MARKDOWN_PARAGRAPH_SPACING] = value
        }
    }

    suspend fun saveConvertLongPastedTextToFile(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CONVERT_LONG_PASTED_TEXT_TO_FILE] = enabled
        }
    }

    suspend fun saveLongPastedTextFileThreshold(threshold: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[LONG_PASTED_TEXT_FILE_THRESHOLD] = threshold
        }
    }

    // 重置布局设置
    suspend fun resetLayoutSettings() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences.remove(CHAT_SETTINGS_BUTTON_END_PADDING)
            preferences.remove(CHAT_AREA_HORIZONTAL_PADDING)
            preferences.remove(AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER)
            preferences.remove(AI_MARKDOWN_LETTER_SPACING)
            preferences.remove(AI_MARKDOWN_PARAGRAPH_SPACING)
        }
    }

    fun getAiAvatarForCharacterCardFlow(characterCardId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterCardMetadataPrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            preferences[key]
        }
    }
    
    suspend fun saveAiAvatarForCharacterCard(characterCardId: String, avatarUri: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterCardMetadataPrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            if (avatarUri != null) {
                preferences[key] = avatarUri
            } else {
                preferences.remove(key)
            }
        }
    }

    fun getAiAvatarForCharacterGroupFlow(characterGroupId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterGroupMetadataPrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            preferences[key]
        }
    }

    suspend fun saveAiAvatarForCharacterGroup(characterGroupId: String, avatarUri: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterGroupMetadataPrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            if (avatarUri != null) {
                preferences[key] = avatarUri
            } else {
                preferences.remove(key)
            }
        }
    }

    suspend fun saveCustomChatTitleForCharacterCard(characterCardId: String, title: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterCardMetadataPrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            if (!title.isNullOrEmpty()) {
                preferences[key] = title
            } else {
                preferences.remove(key)
            }
        }
    }

    fun getCustomChatTitleForCharacterCardFlow(characterCardId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterCardMetadataPrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            preferences[key]
        }
    }

    suspend fun saveCustomChatTitleForCharacterGroup(characterGroupId: String, title: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterGroupMetadataPrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            if (!title.isNullOrEmpty()) {
                preferences[key] = title
            } else {
                preferences.remove(key)
            }
        }
    }

    fun getCustomChatTitleForCharacterGroupFlow(characterGroupId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterGroupMetadataPrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            preferences[key]
        }
    }

    // ========== 角色卡/群组业务元数据 ==========

    private fun getCharacterCardMetadataPrefix(characterCardId: String): String =
        "character_card_metadata_${characterCardId}_"

    private fun getCharacterGroupMetadataPrefix(characterGroupId: String): String =
        "character_group_metadata_${characterGroupId}_"

    /**
     * 一次性迁移：把主题前缀下的业务元数据（AI 头像、聊天标题）搬到独立 metadata 前缀，
     * 并清除全部旧目标级视觉主题键。返回默认角色卡遗留的用户头像供调用方并入全局
     * 用户头像（仅当全局为空时写入）；无遗留时返回 null。
     */
    suspend fun migrateLegacyThemeStorage(): String? {
        var pendingGlobalUserAvatar: String? = null
        context.userPreferencesDataStore.edit { preferences ->
            if (preferences[THEME_METADATA_MIGRATION_COMPLETED] == true) return@edit
            val defaultUserAvatarKey = stringPreferencesKey(
                "character_card_theme_${CharacterCardManager.DEFAULT_CHARACTER_CARD_ID}_custom_user_avatar_uri",
            )
            preferences.asMap().keys.toList().forEach { key ->
                val name = key.name
                if (!name.startsWith("character_card_theme_") &&
                    !name.startsWith("character_group_theme_")
                ) {
                    return@forEach
                }
                if (name.endsWith("_custom_ai_avatar_uri") || name.endsWith("_custom_chat_title")) {
                    val migratedKey = stringPreferencesKey(name.replace("_theme_", "_metadata_"))
                    (preferences[key] as? String)?.let { value -> preferences[migratedKey] = value }
                }
                if (key == defaultUserAvatarKey) {
                    (preferences[key] as? String)?.let { value -> pendingGlobalUserAvatar = value }
                }
                preferences.remove(key)
            }
            preferences[THEME_METADATA_MIGRATION_COMPLETED] = true
        }
        return pendingGlobalUserAvatar
    }
}

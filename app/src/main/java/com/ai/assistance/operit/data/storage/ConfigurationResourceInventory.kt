package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.features.settings.sections.getProviderDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class ConfigurationResourceKind {
    CHARACTER_CARD,
    MODEL_CONFIG,
    CHARACTER_ASSETS,
}

data class ConfigurationResourceEntry(
    val id: String,
    val kind: ConfigurationResourceKind,
    val name: String,
    val subtitle: String,
    val bytes: Long,
    val boundCount: Int,
    val inUse: Boolean,
    val locked: Boolean,
    val avatarUri: String? = null,
    val providerTypeId: String? = null,
    val providerDisplayName: String? = null,
    val primaryModelName: String? = null,
)

data class ConfigurationResourceSnapshot(
    val cards: List<ConfigurationResourceEntry>,
    val configs: List<ConfigurationResourceEntry>,
    val assets: List<ConfigurationResourceEntry>,
    val totalBytes: Long,
    val scannedAtMillis: Long,
)

class ConfigurationResourceInventory(context: Context) {
    private val appContext = context.applicationContext
    private val characterCardManager = CharacterCardManager.getInstance(appContext)
    private val modelConfigManager = ModelConfigManager(appContext)
    private val functionalConfigManager = FunctionalConfigManager(appContext)
    private val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
    private val userPreferences = UserPreferencesManager.getInstance(appContext)
    private val storageRepository = DataStorageRepository(appContext)

    suspend fun load(): ConfigurationResourceSnapshot = withContext(Dispatchers.IO) {
        val cards = characterCardManager.getAllCharacterCards()
        val chats = chatHistoryManager.chatHistoriesFlow.first()
        val currentChatId = chatHistoryManager.currentChatIdFlow.first()
        val currentCardName = chats.firstOrNull { it.id == currentChatId }?.characterCardName
        val functionMapping = functionalConfigManager.functionConfigMappingFlow.first()
        val configSummaries = modelConfigManager.getAllConfigSummaries()
        val cardEntries = cards.map { card ->
            val bound = chats.count { it.characterCardName == card.name }
            val bytes = estimateTextBytes(
                (card.characterSetting.length + card.description.length + card.openingStatement.length).toLong(),
            )
            ConfigurationResourceEntry(
                id = "card:${card.id}",
                kind = ConfigurationResourceKind.CHARACTER_CARD,
                name = card.name,
                subtitle = card.description,
                bytes = bytes,
                boundCount = bound,
                inUse = currentCardName == card.name,
                locked = card.id == CharacterCardManager.DEFAULT_CHARACTER_CARD_ID || card.isDefault,
                avatarUri = userPreferences.getAiAvatarForCharacterCardFlow(card.id).first(),
            )
        }

        val configEntries = configSummaries.map { config ->
            val inUse = functionMapping.values.contains(config.id)
            val providerTypeId = config.apiProviderTypeId.ifBlank { config.apiProviderType.name }
            val providerName = getProviderDisplayName(providerTypeId, appContext)
            val primaryModel = getModelByIndex(config.modelName, config.modelIndex).ifBlank { config.modelName }
            ConfigurationResourceEntry(
                id = "config:${config.id}",
                kind = ConfigurationResourceKind.MODEL_CONFIG,
                name = config.name,
                subtitle = listOfNotNull(
                    providerName.takeIf { it.isNotBlank() },
                    primaryModel.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                bytes = estimateTextBytes((config.name.length + config.modelName.length).toLong()),
                boundCount = functionMapping.values.count { it == config.id },
                inUse = inUse,
                locked = config.id == ModelConfigManager.DEFAULT_CONFIG_ID || inUse,
                providerTypeId = providerTypeId,
                providerDisplayName = providerName,
                primaryModelName = primaryModel,
            )
        }

        ConfigurationResourceSnapshot(
            cards = cardEntries,
            configs = configEntries,
            assets = emptyList(),
            totalBytes = cardEntries.sumOf { it.bytes } + configEntries.sumOf { it.bytes },
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(entries: List<ConfigurationResourceEntry>, onProgress: (String, Int, Int, Long) -> Unit): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        entries.forEachIndexed { index, entry ->
            onProgress(entry.name, index, entries.size, released)
            if (entry.locked) {
                failed++
                return@forEachIndexed
            }
            val ok = runCatching {
                when (entry.kind) {
                    ConfigurationResourceKind.CHARACTER_CARD -> {
                        characterCardManager.deleteCharacterCard(entry.id.removePrefix("card:"))
                        true
                    }
                    ConfigurationResourceKind.MODEL_CONFIG -> {
                        modelConfigManager.deleteConfig(entry.id.removePrefix("config:")).let { true }
                    }
                    ConfigurationResourceKind.CHARACTER_ASSETS -> false
                }
            }.getOrDefault(false)
            if (ok) {
                deleted++
                released += entry.bytes
            } else {
                failed++
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }
}

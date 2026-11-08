package com.ai.assistance.operit.ui.features.conversation.memory.settings

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.memory.resolveChatMemorySpaceId
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import kotlinx.coroutines.flow.first

/** 只读当前窗口指定的记忆空间；设置保存沿用该空间的既有资料策略。 */
class MemorySettingsDataSource(private val context: Context, private val spaceId: String) {
    private val preferences = UserPreferencesManager.getInstance(context)
    private val searchSettings = MemorySearchSettingsPreferences(context, spaceId)

    suspend fun load(): MemorySettingsDetails {
        require(spaceId in preferences.memorySpaceListFlow.first()) { context.getString(R.string.memory_settings_space_missing) }
        val space = preferences.getMemorySpaceFlow(spaceId).first()
        val activeId = preferences.activeMemorySpaceIdFlow.first()
        val cards = CharacterCardManager.getInstance(context).getAllCharacterCards()
        val histories = ChatHistoryManager.getInstance(context).chatHistoriesFlow.first()
        return MemorySettingsDetails(
            loading = false,
            loaded = true,
            spaceName = space.name,
            policy = MemorySettingsPolicy(space.profileAutoUpdateEnabled, space.profileAutoUpdateLocked, searchSettings.loadCloudEmbeddingEnabled()),
            hasLegacyEmbeddingConfiguration = searchSettings.hasLegacyCloudEmbeddingConfiguration(),
            queue = MemoryAutoSaveCandidateRepository(context, spaceId).readQueueStatus(),
            nextRunAtMs = searchSettings.loadNextAutoSaveRunAtMs(),
            chats = histories.filter { resolveChatMemorySpaceId(it, cards, activeId) == spaceId }
                .map { MemorySettingsChat(it.id, it.title) },
        )
    }

    suspend fun savePolicy(policy: MemorySettingsPolicy) {
        require(spaceId in preferences.memorySpaceListFlow.first()) { context.getString(R.string.memory_settings_space_missing) }
        val space = preferences.getMemorySpaceFlow(spaceId).first()
        preferences.updateMemorySpace(space.copy(
            profileAutoUpdateEnabled = policy.profileAutoUpdateEnabled,
            profileAutoUpdateLocked = policy.profileAutoUpdateLocked,
        ))
        searchSettings.saveCloudEmbeddingEnabled(policy.cloudEmbeddingEnabled)
    }
}

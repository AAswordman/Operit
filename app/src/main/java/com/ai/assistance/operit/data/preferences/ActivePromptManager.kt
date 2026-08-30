package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.data.model.ActivePrompt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

class ActivePromptManager private constructor(context: Context) {

    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(context)
    private val userPreferencesManager = UserPreferencesManager.getInstance(context)
    private val themeOperations = ThemeTargetOperationCoordinator()

    val activePromptFlow: Flow<ActivePrompt> =
        combine(
            characterGroupCardManager.observeActiveCharacterGroupId(),
            characterCardManager.observeActiveCharacterCardId()
        ) { groupId, cardId ->
            when {
                !groupId.isNullOrBlank() -> ActivePrompt.CharacterGroup(groupId)
                !cardId.isNullOrBlank() -> ActivePrompt.CharacterCard(cardId)
                else -> ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
            }
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeCustomChatTitleFlow: Flow<String?> =
        activePromptFlow
            .flatMapLatest { prompt ->
                when (prompt) {
                    is ActivePrompt.CharacterGroup ->
                        userPreferencesManager.getCustomChatTitleForCharacterGroupFlow(prompt.id)

                    is ActivePrompt.CharacterCard ->
                        userPreferencesManager.getCustomChatTitleForCharacterCardFlow(prompt.id)
                }
            }
            .distinctUntilChanged()

    suspend fun getActivePrompt(): ActivePrompt = activePromptFlow.first()

    suspend fun setActivePrompt(prompt: ActivePrompt) {
        themeOperations.runTransition {
            when (prompt) {
                is ActivePrompt.CharacterGroup -> {
                    characterGroupCardManager.setActiveCharacterGroupCard(prompt.id)
                    characterCardManager.clearActiveCharacterCard()
                }
                is ActivePrompt.CharacterCard -> {
                    characterCardManager.setActiveCharacterCard(prompt.id)
                    characterGroupCardManager.setActiveCharacterGroupCard(null)
                }
            }
        }
    }

    internal suspend fun <T> runThemeTransition(action: suspend () -> T): T {
        return themeOperations.runTransition(action)
    }

    internal suspend fun <T> runThemeTransitionForExistingTarget(
        target: ActivePrompt,
        action: suspend () -> T,
    ): T {
        return themeOperations.runTransition {
            val exists =
                when (target) {
                    is ActivePrompt.CharacterCard -> characterCardManager.hasCharacterCard(target.id)
                    is ActivePrompt.CharacterGroup -> characterGroupCardManager.hasCharacterGroupCard(target.id)
                }
            require(exists) { "Theme target no longer exists: $target" }
            action()
        }
    }

    suspend fun mutateActiveThemeForPrompt(
        target: ActivePrompt,
        transform: (ThemePreferenceValues) -> ThemePreferenceValues,
    ) {
        themeOperations.runTransition {
            if (getActivePrompt() != target) return@runTransition
            userPreferencesManager.mutateThemeForPrompt(
                target = target,
                transform = transform,
            )
        }
    }

    suspend fun resetThemeDraft(
        target: ActivePrompt,
        values: ThemePreferenceValues,
    ) {
        themeOperations.runTransition {
            userPreferencesManager.resetVisualThemeForPrompt(
                target = target,
                values = values,
            )
        }
    }

    suspend fun saveAiAvatarForPrompt(target: ActivePrompt, avatarUri: String?) {
        themeOperations.runTransition {
            when (target) {
                is ActivePrompt.CharacterGroup ->
                    userPreferencesManager.saveAiAvatarForCharacterGroup(target.id, avatarUri)

                is ActivePrompt.CharacterCard ->
                    userPreferencesManager.saveAiAvatarForCharacterCard(target.id, avatarUri)
            }
        }
    }

    suspend fun saveCustomChatTitleForPrompt(target: ActivePrompt, title: String?) {
        themeOperations.runTransition {
            when (target) {
                is ActivePrompt.CharacterGroup ->
                    userPreferencesManager.saveCustomChatTitleForCharacterGroup(target.id, title)

                is ActivePrompt.CharacterCard ->
                    userPreferencesManager.saveCustomChatTitleForCharacterCard(target.id, title)
            }
        }
    }

    suspend fun activateForChatBinding(characterCardName: String?, characterGroupId: String?) {
        val normalizedGroupId = characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        if (!normalizedGroupId.isNullOrBlank()) {
            setActivePrompt(ActivePrompt.CharacterGroup(normalizedGroupId))
            return
        }

        val normalizedCardName = characterCardName?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedCardName != null) {
            val targetCard = characterCardManager.findCharacterCardByName(normalizedCardName)
            if (targetCard != null) {
                setActivePrompt(ActivePrompt.CharacterCard(targetCard.id))
                return
            }
        }

        setActivePrompt(ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID))
    }

    suspend fun resolveActiveCardIdForSend(): String {
        return when (val prompt = getActivePrompt()) {
            is ActivePrompt.CharacterCard -> prompt.id
            is ActivePrompt.CharacterGroup -> CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ActivePromptManager? = null

        fun getInstance(context: Context): ActivePromptManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivePromptManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

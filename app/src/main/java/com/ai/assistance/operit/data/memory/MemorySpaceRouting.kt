package com.ai.assistance.operit.data.memory

import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.character.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.ChatHistory

/** 记忆设置和历史提取共用现有的角色绑定规则，浏览空间不改变全局选择。 */
internal fun resolveChatMemorySpaceId(
    history: ChatHistory,
    characterCards: List<CharacterCard>,
    activeMemorySpaceId: String,
): String {
    if (!history.characterGroupId.isNullOrBlank()) return activeMemorySpaceId
    val card = characterCards.firstOrNull { it.name == history.characterCardName }
    return if (card != null &&
        CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode) ==
            CharacterCardMemoryProfileBindingMode.FIXED_PROFILE && !card.memoryProfileId.isNullOrBlank()
    ) {
        requireNotNull(card.memoryProfileId)
    } else {
        activeMemorySpaceId
    }
}

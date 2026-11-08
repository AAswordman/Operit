package com.ai.assistance.operit.data.character

import com.ai.assistance.operit.data.model.CharacterCardToolAccessConfig

object CharacterCardChatModelBindingMode {
    const val FOLLOW_GLOBAL = "FOLLOW_GLOBAL"
    const val FIXED_CONFIG = "FIXED_CONFIG"

    fun normalize(mode: String?): String {
        return if (mode == FIXED_CONFIG) FIXED_CONFIG else FOLLOW_GLOBAL
    }
}

object CharacterCardMemoryProfileBindingMode {
    const val FOLLOW_GLOBAL = "FOLLOW_GLOBAL"
    const val FIXED_PROFILE = "FIXED_PROFILE"

    fun normalize(mode: String?): String {
        return if (mode == FIXED_PROFILE) FIXED_PROFILE else FOLLOW_GLOBAL
    }
}

/**
 * 酒馆角色卡格式数据模型
 */
data class TavernCharacterCard(
    val spec: String = "",
    val spec_version: String = "",
    val data: TavernCharacterData
)

data class TavernCharacterData(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val first_mes: String = "",
    val avatar: String = "",
    val mes_example: String = "",
    val scenario: String = "",
    val creator_notes: String = "",
    val system_prompt: String = "",
    val post_history_instructions: String = "",
    val alternate_greetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val character_version: String = "",
    val extensions: TavernExtensions? = null,
    val character_book: TavernCharacterBook? = null
)

data class TavernExtensions(
    val chub: TavernChubExtension? = null,
    val depth_prompt: TavernDepthPrompt? = null,
    val operit: OperitTavernExtension? = null
)

data class OperitTavernExtension(
    val schema: String = "operit_character_card_v1",
    val character_card: OperitCharacterCardPayload
)

data class OperitCharacterCardPayload(
    val name: String = "",
    val description: String = "",
    val characterSetting: String = "",
    val openingStatement: String = "",
    val otherContent: String = "",
    val otherContentChat: String = "",
    val otherContentVoice: String = "",
    val attachedTagIds: List<String> = emptyList(),
    val attachedTags: List<OperitAttachedTagPayload> = emptyList(),
    val advancedCustomPrompt: String = "",
    val marks: String = "",
    val chatModelBindingMode: String = CharacterCardChatModelBindingMode.FOLLOW_GLOBAL,
    val chatModelConfigId: String? = null,
    val chatModelIndex: Int = 0,
    val memoryProfileBindingMode: String = CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL,
    val memoryProfileId: String? = null,
    val toolAccessConfig: CharacterCardToolAccessConfig? = null
)

data class OperitAttachedTagPayload(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val promptContent: String = "",
    val tagType: String = "CUSTOM"
)

data class TavernChubExtension(
    val id: Long = 0,
    val preset: String? = null,
    val full_path: String = "",
    val extensions: List<String> = emptyList(),
    val expressions: String? = null,
    val alt_expressions: Map<String, String> = emptyMap(),
    val background_image: String? = null,
    val related_lorebooks: List<String> = emptyList()
)

data class TavernDepthPrompt(
    val role: String = "",
    val depth: Int = 0,
    val prompt: String = ""
)

data class TavernCharacterBook(
    val name: String = "",
    val description: String = "",
    val scan_depth: Int = 0,
    val token_budget: Int = 0,
    val recursive_scanning: Boolean = false,
    val extensions: Map<String, Any> = emptyMap(),
    val entries: List<TavernBookEntry> = emptyList()
)

data class TavernBookEntry(
    val name: String = "",
    val keys: List<String> = emptyList(),
    val secondary_keys: List<String> = emptyList(),
    val content: String = "",
    val enabled: Boolean = true,
    val insertion_order: Int = 0,
    val case_sensitive: Boolean = false,
    val priority: Int = 0,
    val id: Int = 0,
    val comment: String = "",
    val selective: Boolean = false,
    val constant: Boolean = false,
    val position: String = "",
    val extensions: Map<String, Any> = emptyMap(),
    val probability: Int = 100,
    val selectiveLogic: Int = 0
)

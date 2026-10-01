package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class CharacterCardToolAccessConfig(
    val enabled: Boolean = false,
    val allowedBuiltinTools: List<String> = emptyList(),
    val allowedPackages: List<String> = emptyList(),
    val allowedSkills: List<String> = emptyList(),
    val allowedMcpServers: List<String> = emptyList()
) {
    fun normalized(): CharacterCardToolAccessConfig {
        return copy(
            allowedBuiltinTools = normalizeEntries(allowedBuiltinTools),
            allowedPackages = normalizeEntries(allowedPackages),
            allowedSkills = normalizeEntries(allowedSkills),
            allowedMcpServers = normalizeEntries(allowedMcpServers)
        )
    }

    fun hasExternalSelections(): Boolean {
        return allowedPackages.isNotEmpty() ||
            allowedSkills.isNotEmpty() ||
            allowedMcpServers.isNotEmpty()
    }

    private fun normalizeEntries(values: List<String>): List<String> {
        return values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

/**
 * 角色卡数据模型
 */
@Entity(tableName = "character_cards")
data class CharacterCard(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val characterSetting: String = "", // 角色设定（引导词）
    val openingStatement: String = "", // 新增：开场白
    val otherContentChat: String = "", // 其他内容（聊天）
    val otherContentVoice: String = "", // 其他内容（语音）
    val attachedTagIds: List<String> = emptyList(), // 附着的标签ID列表
    val advancedCustomPrompt: String = "", // 高级设置的自定义（引导词）
    val marks: String = "", // 备注信息（不会被拼接到提示词中）
    val chatModelBindingMode: String = CharacterCardChatModelBindingMode.FOLLOW_GLOBAL, // 对话模型绑定模式
    val chatModelConfigId: String? = null, // 固定绑定时使用的配置ID
    val chatModelIndex: Int = 0, // 固定绑定时使用的模型索引
    val memoryProfileBindingMode: String = CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL, // 记忆配置绑定模式
    val memoryProfileId: String? = null, // 固定绑定时使用的记忆配置ID
    val defaultWorkspaceName: String? = null, // 默认工作区（SAF 仓库书签名称），留空表示不绑定
    val toolAccessConfig: CharacterCardToolAccessConfig = CharacterCardToolAccessConfig(), // 角色卡自定义工具白名单
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

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
 * 角色卡默认工作区解析结果，字段语义与对话上的 workspace / workspaceEnv 一致。
 */
data class CharacterCardWorkspaceBinding(
    val workspacePath: String,
    val workspaceEnv: String
) {
    companion object {
        private const val REPO_ENV_PREFIX = "repo:"
        private const val REPO_ROOT_PATH = "/"

        /**
         * 只有书签仍然存在时才返回绑定，被删除或改名的书签解析为空，
         * 让新对话保持未绑定状态而不是指向一个已经不存在的工作区。
         */
        fun resolveRepoBookmark(
            defaultWorkspaceName: String?,
            availableBookmarkNames: Collection<String>
        ): CharacterCardWorkspaceBinding? {
            val name = defaultWorkspaceName?.trim().orEmpty()
            if (name.isEmpty()) return null
            if (availableBookmarkNames.none { it == name }) return null
            return CharacterCardWorkspaceBinding(REPO_ROOT_PATH, REPO_ENV_PREFIX + name)
        }
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
    val defaultWorkspaceName: String? = null,
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

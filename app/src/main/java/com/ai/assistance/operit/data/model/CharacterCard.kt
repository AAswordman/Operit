package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.ai.assistance.operit.data.character.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.character.CharacterCardMemoryProfileBindingMode

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
    val toolAccessConfig: CharacterCardToolAccessConfig = CharacterCardToolAccessConfig(), // 角色卡自定义工具白名单
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

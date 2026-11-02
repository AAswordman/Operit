package com.ai.assistance.operit.data.character

/**
 * 群组角色卡成员配置
 */
data class GroupMemberConfig(
    val characterCardId: String,
    val orderIndex: Int = 0
)

/**
 * 群组角色卡
 */
data class CharacterGroupCard(
    val id: String,
    val name: String,
    val description: String = "",
    val members: List<GroupMemberConfig> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

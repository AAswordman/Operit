package com.ai.assistance.operit.api.chat.keypool

import kotlinx.serialization.Serializable

/**
 * API Key详细信息
 * @property id 唯一标识符
 * @property key API密钥
 * @property name 用户自定义名称
 * @property isEnabled 是否启用
 * @property usageCount 使用次数统计
 * @property lastUsed 最后使用时间戳
 * @property errorCount 连续错误次数
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus")
@Serializable
enum class ApiKeyAvailabilityStatus {
    UNTESTED,
    AVAILABLE,
    UNAVAILABLE
}

/**
 * API Key详细信息
 * @property id 唯一标识符
 * @property key API密钥
 * @property name 用户自定义名称
 * @property isEnabled 是否启用
 * @property usageCount 使用次数统计
 * @property lastUsed 最后使用时间戳
 * @property errorCount 连续错误次数
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ApiKeyInfo")
@Serializable
data class ApiKeyInfo(
    val id: String,
    val key: String,
    val name: String = "",
    val isEnabled: Boolean = true,
    val availabilityStatus: ApiKeyAvailabilityStatus = ApiKeyAvailabilityStatus.UNTESTED,
    val usageCount: Long = 0,
    val lastUsed: Long = 0,
    val errorCount: Long = 0
)

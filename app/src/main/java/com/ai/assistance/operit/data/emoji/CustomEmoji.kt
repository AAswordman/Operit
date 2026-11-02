package com.ai.assistance.operit.data.emoji

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 自定义表情数据模型
 *
 * @property id 唯一标识符（UUID）
 * @property emotionCategory 情绪类别（如 "happy", "sad" 或用户自定义）
 * @property fileName 文件名（如 "uuid.jpg"），不包含路径
 * @property isBuiltInCategory 旧版内置类别标记，仅为读取已有表情库保留
 * @property createdAt 创建时间戳
 *
 * 注意：只存储fileName，完整路径在使用时会根据当前角色目标动态构建。
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.CustomEmoji")
@Serializable
data class CustomEmoji(
    val id: String = UUID.randomUUID().toString(),
    val emotionCategory: String,
    val fileName: String,
    val isBuiltInCategory: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

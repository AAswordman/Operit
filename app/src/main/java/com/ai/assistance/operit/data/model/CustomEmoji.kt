package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 自定义表情数据模型
 * 
 * @property id 唯一标识符（UUID）
 * @property emotionCategory 情绪类别（如 "happy", "sad" 或用户自定义）
 * @property fileName 文件名（如 "uuid.jpg"），不包含路径
 * @property isBuiltInCategory 旧版本内置表情标记，仅用于迁移
 * @property createdAt 创建时间戳
 * 
 * 注意：只存储fileName，完整路径在使用时会根据当前角色目标动态构建。
 */
@Serializable
data class CustomEmoji(
    val id: String = UUID.randomUUID().toString(),
    val emotionCategory: String,
    val fileName: String,
    val isBuiltInCategory: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)


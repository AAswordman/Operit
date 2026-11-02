package com.ai.assistance.operit.data.memory.export

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Date

/**
 * Date 类型的序列化器
 * 将 Date 序列化为时间戳（Long）
 */
object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeLong(value.time)
    }

    override fun deserialize(decoder: Decoder): Date {
        return Date(decoder.decodeLong())
    }
}

/**
 * 可序列化的记忆数据
 * 用于导出导入时的数据传输
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.SerializableMemory")
@Serializable
data class SerializableMemory(
    val uuid: String,
    val title: String,
    val content: String,
    val contentType: String,
    val source: String,
    val credibility: Float,
    val importance: Float,
    val folderPath: String?,
    @Serializable(with = DateSerializer::class)
    val createdAt: Date,
    @Serializable(with = DateSerializer::class)
    val updatedAt: Date,
    val tagNames: List<String> // 标签名称列表
)

/**
 * 可序列化的链接关系
 * 使用 UUID 来引用记忆
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.SerializableLink")
@Serializable
data class SerializableLink(
    val sourceUuid: String,
    val targetUuid: String,
    val type: String,
    val weight: Float,
    val description: String
)

/**
 * 记忆导出数据容器
 * 包含完整的记忆库数据
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.MemoryExportData")
@Serializable
data class MemoryExportData(
    val memories: List<SerializableMemory>,
    val links: List<SerializableLink>,
    @Serializable(with = DateSerializer::class)
    val exportDate: Date,
    val version: String = "1.0" // 数据格式版本
)

/**
 * 导入策略枚举
 * 定义遇到重复记忆时的处理方式
 */
enum class ImportStrategy {
    /**
     * 跳过已存在的记忆
     */
    SKIP,

    /**
     * 更新已存在的记忆
     */
    UPDATE,

    /**
     * 创建新的记忆（即使UUID相同也创建新记录）
     */
    CREATE_NEW
}

/**
 * 记忆导入结果
 * 记录导入操作的统计信息
 */
data class MemoryImportResult(
    val newMemories: Int = 0,      // 新创建的记忆数
    val updatedMemories: Int = 0,  // 更新的记忆数
    val skippedMemories: Int = 0,  // 跳过的记忆数
    val newLinks: Int = 0          // 新创建的链接数
)

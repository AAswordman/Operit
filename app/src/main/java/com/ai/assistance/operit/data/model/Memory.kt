package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.util.Date
import java.util.UUID
import com.ai.assistance.operit.data.db.converter.EmbeddingConverter

/**
 * 核心记忆单元 (Memory Unit)
 * 代表一个独立的知识片段、事件、概念或任何AI需要记住的东西。
 */
@Entity
data class Memory(
    @Id var id: Long = 0,
    var uuid: String = UUID.randomUUID().toString(),

    // --- 核心内容 (Core Content) ---
    var title: String = "", // 记忆的简短标题/摘要
    var content: String = "", // 详细内容 (可以是文本、JSON、文件路径等)
    var contentType: String = "text/plain", // 内容类型 (e.g., "text/plain", "image/jpeg", "application/json")

    // --- 元数据 (Metadata) ---
    var source: String = "unknown", // 来源 (e.g., "user_input", "chat_summary", "web_scrape")
    var credibility: Float = 0.5f, // 可信度 (0.0 to 1.0)
    var importance: Float = 0.5f,  // 重要性 (0.0 to 1.0)

    // 新增：如果这是一个文档节点，则存储文档的路径/URI
    var documentPath: String? = null,
    // 新增：标记此记忆是否代表一个外部文档
    var isDocumentNode: Boolean = false,
    // 新增：如果这是一个文档节点，则存储其区块索引文件的路径
    var chunkIndexFilePath: String? = null,

    // 新增：文件夹路径，用于分类组织记忆（如 "工作/项目A" 或 "生活/健康"）
    // 使用可空类型以兼容旧数据，null 视为"未分类"
    @Index
    var folderPath: String? = null,

    // 文本内容的向量嵌入
    @Convert(converter = EmbeddingConverter::class, dbType = ByteArray::class)
    var embedding: Embedding? = null,

    // --- 时间戳 (Timestamps) ---
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var lastAccessedAt: Date = Date()
) {
    // 使用 ToMany 关联多个标签
    lateinit var tags: ToMany<MemoryTag>

    // 存储与该记忆相关的任意键值对属性
    lateinit var properties: ToMany<MemoryProperty>

    // 从这个记忆出发的关联
    lateinit var links: ToMany<MemoryLink>

    // 这个记忆作为目标被哪些关联指向 (反向链接)
    @Backlink(to = "target")
    lateinit var backlinks: ToMany<MemoryLink>

    // 新增：如果这是一个文档节点，则包含其所有内容区块
    @Backlink(to = "memory")
    lateinit var documentChunks: ToMany<DocumentChunk>
}

/**
 * 记忆标签 (Memory Tag)
 * 用于对记忆进行分类和组织，支持层级结构。
 */
@Entity
data class MemoryTag(
    @Id var id: Long = 0,
    var name: String = "" // 标签名称
) {
    // 父标签，用于构建层级关系
    lateinit var parent: ToOne<MemoryTag>

    // 该标签下的所有记忆
    @Backlink(to = "tags")
    lateinit var memories: ToMany<Memory>
}

/**
 * 记忆关联 (Memory Link)
 * 定义记忆之间的关系。
 */
@Entity
data class MemoryLink(
    @Id var id: Long = 0,
    var type: String = "related", // 关联类型 (e.g., "causes", "explains", "part_of")
    var weight: Float = 1.0f, // 关联强度 (0.0 to 1.0)
    var description: String = "" // 关联的详细描述
) {
    // 关联的源头和目标
    lateinit var source: ToOne<Memory>
    lateinit var target: ToOne<Memory>
}

/**
 * 记忆属性 (Memory Property)
 * 灵活的键值对存储，用于扩展记忆的元数据。
 */
@Entity
data class MemoryProperty(
    @Id var id: Long = 0,
    var key: String = "",
    var value: String = ""
) 
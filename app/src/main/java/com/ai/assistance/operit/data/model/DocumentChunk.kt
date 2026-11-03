package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne
import java.io.Serializable
import com.ai.assistance.operit.data.db.converter.EmbeddingConverter

/**
 * 表示外部文档中的一个内容区块（例如，一个段落）。
 * 每个区块都有自己的内容和向量嵌入，以便进行独立的语义搜索。
 */
@Entity
data class DocumentChunk(
    @Id var id: Long = 0,

    // 区块的文本内容
    var content: String = "",

    // 区块在文档中的顺序索引
    var chunkIndex: Int = 0,

    // 文本内容的向量嵌入
    @Convert(converter = EmbeddingConverter::class, dbType = ByteArray::class)
    var embedding: Embedding? = null
) {
    lateinit var memory: ToOne<Memory>
} 
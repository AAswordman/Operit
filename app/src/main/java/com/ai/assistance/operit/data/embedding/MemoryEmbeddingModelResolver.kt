package com.ai.assistance.operit.data.embedding

import android.content.Context
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.config.model.getModelList
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import java.net.URI

/** 记忆生成、检索、重建和功能连接测试统一读取记忆向量绑定。 */
class MemoryEmbeddingModelResolver(context: Context) {
    private val functions = FunctionalConfigManager(context)
    private val models = ModelConfigManager(context)

    suspend fun resolve(): ModelConfigData? {
        val mapping = functions.getConfigMappingForFunction(FunctionType.MEMORY_EMBEDDING)
        if (mapping.configId.isEmpty()) return null
        val model = requireNotNull(models.getModelConfig(mapping.configId)) {
            "记忆向量绑定的模型配置不存在：${mapping.configId}"
        }
        return selectMemoryEmbeddingModel(model, mapping.modelIndex)
    }
}

internal fun selectMemoryEmbeddingModel(config: ModelConfigData, modelIndex: Int): ModelConfigData {
    val names = getModelList(config.modelName)
    if (names.isEmpty()) {
        require(modelIndex == 0) { "记忆向量模型索引越界：$modelIndex" }
        return config.copy(modelName = "")
    }
    require(modelIndex in names.indices) { "记忆向量模型索引越界：$modelIndex" }
    return config.copy(modelName = names[modelIndex])
}

fun ModelConfigData?.isEmbeddingReady(): Boolean {
    if (this == null) return false
    val hasKey = if (useMultipleApiKeys) apiKeyPool.any { it.isEnabled && it.key.isNotBlank() }
        else apiKey.isNotBlank()
    return apiEndpoint.isNotBlank() && modelName.isNotBlank() && hasKey
}

/** 接入普通模型配置的 OpenAI 兼容端点，完整自定义地址继续使用 # 标记。 */
internal fun memoryEmbeddingEndpoint(endpoint: String): String {
    val trimmed = endpoint.trim()
    val pinned = trimmed.endsWith('#')
    val address = if (pinned) trimmed.dropLast(1) else trimmed
    val uri = URI(address)
    require(uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) {
        "向量模型端点必须使用 HTTP 或 HTTPS"
    }
    require(!uri.host.isNullOrBlank() && uri.fragment == null) { "向量模型端点不是有效的 HTTP 地址" }
    if (pinned) return address

    // 保留路径和查询参数的原始编码，避免租户或签名参数在端点转换时发生变化。
    val path = uri.rawPath.orEmpty().trimEnd('/')
    val embeddingPath = when {
        path.endsWith("/chat/completions") -> path.removeSuffix("/chat/completions") + "/embeddings"
        path.endsWith("/responses") -> path.removeSuffix("/responses") + "/embeddings"
        path.isEmpty() -> "/v1/embeddings"
        path.endsWith("/v1") -> "$path/embeddings"
        else -> path
    }
    return buildString {
        append(uri.scheme).append("://").append(uri.rawAuthority).append(embeddingPath)
        uri.rawQuery?.let { append('?').append(it) }
    }
}
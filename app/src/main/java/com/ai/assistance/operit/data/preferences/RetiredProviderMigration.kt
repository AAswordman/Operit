package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.data.config.model.ApiProviderType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 迁移已退役提供商的已发布配置，保留配置标识、模型及凭据。 */
internal object RetiredProviderMigration {
    private const val RETIRED_PROVIDER_ID = "IFLOW"
    private val json = Json { isLenient = true }

    fun rewriteConfig(raw: String): String {
        if (!raw.contains(RETIRED_PROVIDER_ID)) return raw
        val source = json.parseToJsonElement(raw) as JsonObject
        return migrateConfig(source).toString()
    }

    fun rewriteExport(raw: String): String {
        if (!raw.contains(RETIRED_PROVIDER_ID)) return raw
        return when (val source = json.parseToJsonElement(raw)) {
            is JsonArray -> migrateConfigs(source).toString()
            is JsonObject -> JsonObject(source.toMutableMap().apply {
                val configs = source["configs"]
                if (configs is JsonArray) put("configs", migrateConfigs(configs))
            }).toString()
            else -> raw
        }
    }

    private fun migrateConfigs(configs: JsonArray): JsonArray =
        JsonArray(configs.map { migrateConfig(it as JsonObject) })

    private fun migrateConfig(source: JsonObject): JsonElement {
        val provider = source["apiProviderType"]?.jsonPrimitive?.contentOrNull
        val providerId = source["apiProviderTypeId"]?.jsonPrimitive?.contentOrNull
        if (provider != RETIRED_PROVIDER_ID && providerId != RETIRED_PROVIDER_ID) return source

        return JsonObject(source.toMutableMap().apply {
            put("apiProviderType", JsonPrimitive(ApiProviderType.OPENAI_GENERIC.name))
            if (providerId.isNullOrBlank() || providerId == RETIRED_PROVIDER_ID) {
                put("apiProviderTypeId", JsonPrimitive(ApiProviderType.OPENAI_GENERIC.name))
            }
            val endpoint = source["apiEndpoint"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val host = endpoint.substringAfter("://").substringBefore('/').substringBefore(':')
            // 停用的官方地址必须重新配置，用户自己的兼容服务地址保持不变。
            if (host.equals("apis.iflow.cn", ignoreCase = true) || host.equals("iflow.cn", ignoreCase = true)) {
                put("apiEndpoint", JsonPrimitive(""))
            }
        })
    }
}

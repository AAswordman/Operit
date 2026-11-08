package com.ai.assistance.operit.data.mcp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import kotlinx.serialization.json.Json

/** 配置中的可选元数据遵循模型默认值，避免 Gson 绕过构造器后留下非法空值。 */
internal fun createMcpConfigGson(): Gson = GsonBuilder()
    .registerTypeAdapter(MCPLocalServer.PluginMetadata::class.java, PluginMetadataDeserializer)
    .setPrettyPrinting()
    .create()

private object PluginMetadataDeserializer : JsonDeserializer<MCPLocalServer.PluginMetadata> {
    private val metadataJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): MCPLocalServer.PluginMetadata = metadataJson.decodeFromString(
        MCPLocalServer.PluginMetadata.serializer(), json.toString(),
    )
}

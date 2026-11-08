package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.ToolResultData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val lifecycleResultJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "__type"
}

/** 直接把序列化节点转换为事件字段，避免大输出经历完整 JSON 文本编码和反向解析。 */
internal fun encodeToolLifecycleResult(result: ToolResultData): Map<String, Any?> {
    return lifecycleResultJson
        .encodeToJsonElement(ToolResultData.serializer(), result)
        .jsonObject
        .mapValues { (_, value) -> value.toLifecycleValue() }
}

private fun JsonElement.toLifecycleValue(): Any? =
    when (this) {
        JsonNull -> null
        is JsonObject -> mapValues { (_, value) -> value.toLifecycleValue() }
        is JsonArray -> map { value -> value.toLifecycleValue() }
        is JsonPrimitive ->
            when {
                isString -> content
                booleanOrNull != null -> booleanOrNull
                longOrNull != null -> {
                    val number = longOrNull!!
                    if (number in Int.MIN_VALUE..Int.MAX_VALUE) number.toInt() else number
                }
                else -> content.toDouble()
            }
    }

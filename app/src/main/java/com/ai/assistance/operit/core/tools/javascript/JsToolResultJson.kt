package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.SandboxScriptExecutionResultData
import com.ai.assistance.operit.core.tools.ToolResultData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

internal object JsToolResultJson {
    private val structuredJson = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "__type"
    }

    fun encodeStructured(result: ToolResultData): JsonElement {
        // 直接保留字段对应的 JSON 节点，避免大输出先编码为字符串再完整解析一遍。
        return if (result is SandboxScriptExecutionResultData) {
            Json.encodeToJsonElement(SandboxScriptExecutionResultData.serializer(), result)
        } else {
            structuredJson.encodeToJsonElement(ToolResultData.serializer(), result)
        }
    }
}

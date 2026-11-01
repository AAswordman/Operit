package com.ai.assistance.operit.core.tools.builtin.a2ui

import android.content.Context
import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.common.a2ui.CardAuthoringCatalog
import com.ai.assistance.operit.ui.common.a2ui.CardIcons
import com.ai.assistance.operit.util.android.LocaleUtils
import java.io.File
import kotlinx.serialization.json.*

/** 与其他内置工具一样注册，沿用角色卡可见性和工具权限检查。 */
internal object A2uiTools {
    private val definitions = A2uiToolDefinitions.category(useEnglish = true).tools.associateBy { it.name }

    fun register(handler: AIToolHandler, context: Context) {
        val localized = A2uiToolDefinitions.category(useEnglish = !LocaleUtils.usesChineseContent(context))
        localized.tools.forEach { definition ->
            handler.registerTool(
                name = definition.name,
                descriptionGenerator = { definition.description },
                executor = { tool -> execute(tool, context.filesDir, ToolExecutionManager.currentToolRuntimeContext()) },
            )
        }
    }

    internal fun execute(
        tool: AITool,
        filesDirectory: File,
        runtimeContext: ToolExecutionManager.ToolRuntimeContext?,
    ): ToolResult {
        val report = try {
            val definition = requireNotNull(definitions[tool.name]) { "Unknown A2UI operation." }
            val allowed = definition.parametersStructured.orEmpty().map { it.name }.toSet()
            require(tool.parameters.all { it.name in allowed }) { "Unexpected A2UI parameter." }
            require(tool.parameters.map { it.name }.distinct().size == tool.parameters.size) { "Duplicate A2UI parameter." }
            val values = tool.parameters.associate { it.name to it.value }
            fun required(name: String): String = requireNotNull(values[name]) { "Missing parameter: $name" }
            fun integer(name: String, default: Int): Int = values[name]?.let {
                requireNotNull(it.toIntOrNull()) { "Parameter $name must be an integer." }
            } ?: default

            when (tool.name) {
                "a2ui_prepare" -> {
                    val live = values["live"]?.let {
                        requireNotNull(it.toBooleanStrictOrNull()) { "Parameter live must be a boolean." }
                    } ?: false
                    A2uiToolRuntime.prepare(filesDirectory, runtimeContext, required("source"), live)
                }
                "a2ui_revise" -> {
                    val remove = values["remove"]?.let { json ->
                        require(json.length <= 16_384)
                        Json.parseToJsonElement(json).jsonArray.map {
                            require(it is JsonPrimitive && it.isString) { "Remove names must be strings." }
                            it.content
                        }
                    }.orEmpty()
                    A2uiToolRuntime.revise(runtimeContext, required("reference"), required("changes"), remove)
                }
                "a2ui_patch_data" -> {
                    val encoded = required("data")
                    require(encoded.length <= 8192) { "Card data exceeds 8192 characters." }
                    val version = requireNotNull(required("expected_version").toIntOrNull()) { "Expected version must be an integer." }
                    A2uiToolRuntime.patchData(runtimeContext, required("reference"), version, Json.parseToJsonElement(encoded).jsonObject)
                }
                "a2ui_catalog" -> buildJsonObject {
                    put("valid", true)
                    put("catalog", CardAuthoringCatalog.guide())
                }
                "a2ui_icons" -> {
                    val query = values["query"].orEmpty()
                    val limit = integer("limit", 30)
                    val offset = integer("offset", 0)
                    require(query.length <= 80 && limit in 1..100 && offset in 0..10_000) { "Invalid icon query or page range." }
                    Json.parseToJsonElement(CardIcons.search(query, limit, offset)).jsonObject
                }
                else -> error("Unregistered A2UI operation.")
            }
        } catch (error: IllegalArgumentException) {
            A2uiToolRuntime.failure("invalid_arguments", error.message?.take(240) ?: "Invalid A2UI parameters.")
        }
        val success = report["valid"]?.jsonPrimitive?.booleanOrNull != false
        return ToolResult(
            toolName = tool.name,
            success = success,
            result = StringResultData(report.toString()),
            error = if (success) null else report["message"]?.jsonPrimitive?.content,
        )
    }
}
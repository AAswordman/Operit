package com.ai.assistance.operit.core.tools.packTool.runtime

/**
 * Mirror of ToolPkg xml_render hook result shape used by JS toolpkg.
 */
data class ToolPkgXmlRenderHookComposeDslResult(
    val screen: String,
    val state: Map<String, Any?> = emptyMap(),
    val memo: Map<String, Any?> = emptyMap(),
    val moduleSpec: Map<String, Any?>? = null
)

data class ToolPkgXmlRenderHookObjectResult(
    val handled: Boolean? = null,
    val text: String? = null,
    val content: String? = null,
    val composeDsl: ToolPkgXmlRenderHookComposeDslResult? = null
)

data class ToolPkgToolLifecycleEventPayload(
    val toolName: String,
    val parameters: Map<String, String> = emptyMap(),
    val description: String? = null,
    val granted: Boolean? = null,
    val reason: String? = null,
    val success: Boolean? = null,
    val errorMessage: String? = null,
    val resultText: String? = null,
    val resultJson: Map<String, Any?>? = null
)

data class ToolPkgPromptTurn(
    val kind: String,
    val content: String,
    val toolName: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

data class ToolPkgPromptHookObjectResult(
    val rawInput: String? = null,
    val processedInput: String? = null,
    val chatHistory: List<ToolPkgPromptTurn>? = null,
    val preparedHistory: List<ToolPkgPromptTurn>? = null,
    val systemPrompt: String? = null,
    val toolPrompt: String? = null,
    val availableTools: List<Map<String, Any?>>? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

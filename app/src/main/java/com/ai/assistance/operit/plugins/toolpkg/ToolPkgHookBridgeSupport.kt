package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.javascript.JsJavaBridgeDelegates
import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionErrorMessage
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class ToolPkgAppLifecycleHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val event: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgMessageProcessingHookRegistration(
    val containerPackageName: String,
    val pluginId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgXmlRenderHookRegistration(
    val containerPackageName: String,
    val pluginId: String,
    val tag: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgInputMenuToggleHookRegistration(
    val containerPackageName: String,
    val pluginId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgChatInputHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgChatViewHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgChatMessageHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgChatRuntimeStateHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgToolLifecycleHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgPromptHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgAiProviderRegistration(
    val containerPackageName: String,
    val providerId: String,
    val displayName: String,
    val description: String,
    val listModelsFunctionName: String,
    val listModelsFunctionSource: String? = null,
    val sendMessageFunctionName: String,
    val sendMessageFunctionSource: String? = null,
    val testConnectionFunctionName: String,
    val testConnectionFunctionSource: String? = null,
    val calculateInputTokensFunctionName: String,
    val calculateInputTokensFunctionSource: String? = null
) {
    /** Historical counters use the full provider ID while new requests use the display name. */
    val releasedTokenProviderAliases: Map<String, String>
        get() {
            val id = providerId.trim()
            require(id.isNotEmpty()) { "ToolPkg AI provider id must not be blank" }
            val display = displayName.trim()
            require(display.isNotEmpty()) { "ToolPkg AI provider display name must not be blank" }
            return mapOf(
                id to display,
                "TOOLPKG_$id" to display,
                "TOOLPKG_${id.lowercase()}" to display,
                display to display,
            )
        }
}

internal fun toolPkgPackageManager(): PackageManager {
    val application = OperitApplication.instance.applicationContext
    return PackageManager.getInstance(application, AIToolHandler.getInstance(application))
}

internal fun decodeToolPkgHookResult(raw: Any?): Any? {
    val text = raw?.toString().orEmpty()
    if (text.isEmpty()) {
        return null
    }
    val normalized = text.trim()
    if (normalized.isEmpty()) {
        return text
    }
    val errorMessage = extractJsExecutionErrorMessage(normalized)
    if (errorMessage != null) {
        throw IllegalStateException(errorMessage)
    }
    return try {
        JSONTokener(normalized).nextValue()
    } catch (_: Exception) {
        text
    }
}

internal fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any?> {
    return JsJavaBridgeDelegates.decodePlainJsonValue(jsonObject) as? Map<String, Any?> ?: emptyMap()
}

internal fun jsonArrayToList(jsonArray: JSONArray): List<Any?> {
    return JsJavaBridgeDelegates.decodePlainJsonValue(jsonArray) as? List<Any?> ?: emptyList()
}

internal fun jsonValueToKotlin(value: Any?): Any? {
    return JsJavaBridgeDelegates.decodePlainJsonValue(value)
}

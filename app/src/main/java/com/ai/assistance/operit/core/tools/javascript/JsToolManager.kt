package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import com.ai.assistance.operit.core.tools.ScriptExecutionTraceData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class JsToolManager private constructor(
    private val context: Context,
    private val packageManager: PackageManager
) {

    private class ToolParameterConversionException(
        message: String
    ) : IllegalArgumentException(message)

    companion object {
        private const val TAG = "JsToolManager"
        private const val MAX_CONCURRENT_ENGINES = 4

        @Volatile
        private var instance: JsToolManager? = null

        fun getInstance(context: Context, packageManager: PackageManager): JsToolManager {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: JsToolManager(context.applicationContext, packageManager).also {
                            instance = it
                        }
                }
        }
    }

    private val engines = List(MAX_CONCURRENT_ENGINES) { JsEngine(context) }
    private val enginePool = Channel<JsEngine>(capacity = MAX_CONCURRENT_ENGINES).also { pool ->
        engines.forEach(pool::trySend)
    }

    private suspend fun <T> withEngine(block: suspend (JsEngine) -> T): T {
        val engine = enginePool.receive()
        return try {
            block(engine)
        } finally {
            enginePool.trySend(engine)
        }
    }

    private fun <T> withEngineBlocking(block: (JsEngine) -> T): T {
        return runBlocking {
            withEngine { engine -> block(engine) }
        }
    }

    private fun parseDotCall(toolName: String): Pair<String, String>? {
        val separatorIndex = toolName.lastIndexOf('.')
        if (separatorIndex <= 0 || separatorIndex >= toolName.lastIndex) {
            return null
        }
        return toolName.substring(0, separatorIndex) to toolName.substring(separatorIndex + 1)
    }

    private fun parsePackageToolName(toolName: String): Pair<String, String>? {
        val separatorIndex = toolName.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex >= toolName.lastIndex) {
            return null
        }
        return toolName.substring(0, separatorIndex) to toolName.substring(separatorIndex + 1)
    }

    private fun buildRuntimeParams(
        packageName: String,
        params: Map<String, Any?>
    ): MutableMap<String, Any?> {
        val runtimeParams = params.toMutableMap()
        packageManager.getActivePackageStateId(packageName)?.let {
            runtimeParams["__operit_package_state"] = it
        }

        listOf(
            "__operit_package_caller_name",
            "__operit_package_chat_id",
            "__operit_package_caller_card_id"
        ).forEach { key ->
            val value = runtimeParams[key]?.toString()?.takeIf { it.isNotBlank() }
            if (value == null) {
                runtimeParams.remove(key)
            } else {
                runtimeParams[key] = value
            }
        }

        runtimeParams["__operit_package_name"] = packageName
        runtimeParams["__operit_toolpkg_runtime_kind"] = "sandbox"

        packageManager.resolveToolPkgSubpackageRuntimeInternal(packageName)?.let { runtime ->
            runtimeParams["__operit_execution_context_key"] =
                "toolpkg_main:${runtime.containerPackageName}"
            runtimeParams["__operit_toolpkg_subpackage_id"] = runtime.subpackageId
            runtimeParams["containerPackageName"] = runtime.containerPackageName
            runtimeParams["toolPkgId"] = runtime.containerPackageName
            runtimeParams["__operit_ui_package_name"] = runtime.containerPackageName
            runtimeParams["__operit_script_screen"] = runtime.entryPath
        } ?: run {
            runtimeParams.remove("__operit_toolpkg_subpackage_id")
            runtimeParams.remove("containerPackageName")
            runtimeParams.remove("toolPkgId")
            runtimeParams.remove("__operit_ui_package_name")
            runtimeParams.remove("__operit_script_screen")
        }

        return runtimeParams
    }

    private suspend fun <T> withExecutionEngineForPackage(
        packageName: String,
        block: suspend (JsEngine) -> T
    ): T {
        val toolPkgRuntime = packageManager.resolveToolPkgSubpackageRuntimeInternal(packageName)
        if (toolPkgRuntime != null) {
            val contextKey = "toolpkg_main:${toolPkgRuntime.containerPackageName}"
            return block(
                packageManager.getToolPkgExecutionEngine(
                    contextKey = contextKey,
                    containerPackageName = toolPkgRuntime.containerPackageName
                )
            )
        }
        return withEngine(block)
    }

    private fun <T> withExecutionEngineForPackageBlocking(
        packageName: String,
        block: (JsEngine) -> T
    ): T {
        return runBlocking {
            withExecutionEngineForPackage(packageName) { engine -> block(engine) }
        }
    }

    private fun convertToolParameters(
        tool: AITool,
        packageName: String,
        functionName: String
    ): MutableMap<String, Any?> {
        val toolDefinition = packageManager.getPackageTools(packageName)
            ?.tools
            ?.find { it.name == functionName }
        val parameterDefinitions = toolDefinition?.parameters?.associateBy { it.name }.orEmpty()
        val missingRequiredParameters = toolDefinition
            ?.parameters
            ?.filter { definition ->
                definition.required && tool.parameters.none { it.name == definition.name }
            }
            ?.map { it.name }
            .orEmpty()

        if (missingRequiredParameters.isNotEmpty()) {
            throw ToolParameterConversionException(
                "Missing required parameters: ${missingRequiredParameters.joinToString(", ")}"
            )
        }

        val converted = linkedMapOf<String, Any?>()
        tool.parameters.forEach { parameter ->
            val type = parameterDefinitions[parameter.name]?.type?.lowercase() ?: "string"
            converted[parameter.name] = convertToolParameterValue(
                toolName = tool.name,
                parameterName = parameter.name,
                rawValue = parameter.value,
                type = type
            )
        }

        return buildRuntimeParams(packageName, converted)
    }

    private fun convertToolParameterValue(
        toolName: String,
        parameterName: String,
        rawValue: String,
        type: String
    ): Any? {
        val normalizedValue = rawValue.trim()
        return when (type) {
            "number" -> parseNumberValue(normalizedValue)
                ?: throw invalidParameterType(toolName, parameterName, type, rawValue)
            "integer" -> normalizedValue.toLongOrNull()
                ?: throw invalidParameterType(toolName, parameterName, type, rawValue)
            "boolean" -> parseBooleanValue(normalizedValue)
                ?: throw invalidParameterType(toolName, parameterName, type, rawValue)
            "array" -> runCatching { jsonArrayToKotlin(JSONArray(rawValue)) }
                .getOrElse { throw invalidParameterType(toolName, parameterName, type, rawValue, it) }
            "object" -> runCatching { jsonObjectToKotlin(JSONObject(rawValue)) }
                .getOrElse { throw invalidParameterType(toolName, parameterName, type, rawValue, it) }
            else -> rawValue
        }
    }

    private fun parseNumberValue(value: String): Number? {
        if (value.isEmpty()) {
            return null
        }
        if (!value.contains('.') && !value.contains('e', ignoreCase = true)) {
            value.toLongOrNull()?.let { return it }
        }
        return value.toDoubleOrNull()
    }

    private fun parseBooleanValue(value: String): Boolean? {
        return when (value.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    private fun invalidParameterType(
        toolName: String,
        parameterName: String,
        expectedType: String,
        rawValue: String,
        cause: Throwable? = null
    ): ToolParameterConversionException {
        val detail = cause?.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val preview = rawValue.replace("\n", "\\n").take(120)
        AppLogger.w(
            TAG,
            "Strict parameter conversion failed: tool=$toolName, param=$parameterName, type=$expectedType, value=$preview${if (rawValue.length > 120) "..." else ""}${detail}"
        )
        return ToolParameterConversionException(
            "Invalid parameter '$parameterName' for tool '$toolName': expected $expectedType"
        )
    }

    private fun jsonObjectToKotlin(jsonObject: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonValueToKotlin(jsonObject.opt(key))
        }
        return result
    }

    private fun jsonArrayToKotlin(jsonArray: JSONArray): List<Any?> {
        val result = mutableListOf<Any?>()
        for (index in 0 until jsonArray.length()) {
            result.add(jsonValueToKotlin(jsonArray.opt(index)))
        }
        return result
    }

    private fun jsonValueToKotlin(value: Any?): Any? {
        return when (value) {
            null,
            JSONObject.NULL -> null
            is JSONObject -> jsonObjectToKotlin(value)
            is JSONArray -> jsonArrayToKotlin(value)
            else -> value
        }
    }

    private fun success(toolName: String, value: Any?): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = true,
            result = StringResultData(value?.toString() ?: "null")
        )
    }

    private fun failure(toolName: String, message: String): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(""),
            error = message
        )
    }

    private fun failure(toolName: String, failure: JsExecutionFailure): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(failure.dataText),
            error = failure.message
        )
    }

    private fun trace(
        toolName: String,
        kind: String,
        message: String,
        level: String? = null,
        callId: String? = null
    ): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = true,
            result = ScriptExecutionTraceData(
                kind = kind,
                level = level,
                message = message,
                callId = callId
            )
        )
    }

    fun executeScript(toolName: String, params: Map<String, String>): String {
        val parsed = parseDotCall(toolName)
            ?: return "Invalid tool name format: $toolName. Expected format: packageName.functionName"
        val (packageName, functionName) = parsed
        val script = packageManager.getPackageScript(packageName)
            ?: return "Package not found: $packageName"

        val runtimeParams = buildRuntimeParams(
            packageName = packageName,
            params = params.mapValues { it.value as Any? }
        )
        return withExecutionEngineForPackageBlocking(packageName) { engine ->
            try {
                engine.executeScriptFunction(
                    script = script,
                    functionName = functionName,
                    params = runtimeParams
                )?.toString()
                    ?: "null"
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Script execution failed: package=$packageName, function=$functionName, error=${e.message}",
                    e
                )
                buildJsExecutionErrorPayload(e.message.orEmpty())
            }
        }
    }

    fun executeScript(script: String, tool: AITool): Flow<ToolResult> = channelFlow {
        val parsed = parsePackageToolName(tool.name)
        if (parsed == null) {
            send(failure(tool.name, "Invalid tool name format. Expected 'packageName:toolName'"))
            return@channelFlow
        }

        val (packageName, functionName) = parsed
        val runtimeParams = try {
            convertToolParameters(tool, packageName, functionName)
        } catch (e: ToolParameterConversionException) {
            send(failure(tool.name, e.message ?: "Invalid tool parameters"))
            return@channelFlow
        }
        withExecutionEngineForPackage(packageName) { engine ->
            val traceListener =
                object : JsExecutionListener {
                    override fun onCallLog(callId: String, level: String, message: String) {
                        trySend(
                            trace(
                                toolName = tool.name,
                                kind = "log",
                                message = message,
                                level = level,
                                callId = callId
                            )
                        )
                    }

                    override fun onIntermediateResult(callId: String, value: Any?) {
                        trySend(
                            trace(
                                toolName = tool.name,
                                kind = "intermediate",
                                message = value?.toString() ?: "null",
                                callId = callId
                            )
                        )
                    }
                }
            try {
                withTimeout(JsTimeoutConfig.SCRIPT_TIMEOUT_MS) {
                    val result = engine.executeScriptFunction(
                        script = script,
                        functionName = functionName,
                        params = runtimeParams,
                        executionListener = traceListener
                    )

                    val normalizedFailure = extractJsExecutionFailure(result)
                    if (normalizedFailure != null) {
                        send(failure(tool.name, normalizedFailure))
                    } else {
                        send(success(tool.name, result))
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                send(
                    failure(
                        tool.name,
                        "Script execution timed out after ${JsTimeoutConfig.SCRIPT_TIMEOUT_MS}ms"
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Script execution failed: tool=${tool.name}, error=${e.message}", e)
                send(failure(tool.name, "Script execution failed: ${e.message}"))
            }
        }
    }

    fun executeComposeDsl(
        script: String,
        packageName: String = "",
        uiModuleId: String = "",
        toolPkgId: String = "",
        state: Map<String, Any?> = emptyMap(),
        memo: Map<String, Any?> = emptyMap(),
        moduleSpec: Map<String, Any?> = emptyMap(),
        envOverrides: Map<String, String> = emptyMap()
    ): String {
        val runtimeOptions = mutableMapOf<String, Any?>()
        if (packageName.isNotBlank()) {
            runtimeOptions["packageName"] = packageName
        }
        if (uiModuleId.isNotBlank()) {
            runtimeOptions["uiModuleId"] = uiModuleId
        }
        if (toolPkgId.isNotBlank()) {
            runtimeOptions["toolPkgId"] = toolPkgId
        }
        if (state.isNotEmpty()) {
            runtimeOptions["state"] = state
        }
        if (memo.isNotEmpty()) {
            runtimeOptions["memo"] = memo
        }
        if (moduleSpec.isNotEmpty()) {
            runtimeOptions["moduleSpec"] = moduleSpec
        }
        if (packageName.isNotBlank()) {
            packageManager.getActivePackageStateId(packageName)?.let {
                runtimeOptions["__operit_package_state"] = it
            }
        }

        return withEngineBlocking { engine ->
            try {
                engine.executeComposeDslScript(
                    script = script,
                    runtimeOptions = runtimeOptions,
                    envOverrides = envOverrides
                )?.toString() ?: "null"
            } catch (e: Exception) {
                AppLogger.e(TAG, "Compose DSL execution failed: ${e.message}", e)
                buildJsExecutionErrorPayload(e.message.orEmpty())
            }
        }
    }

    fun destroy() {
        enginePool.close()
        engines.forEach(JsEngine::destroy)
    }
}

package com.ai.assistance.operit.core.tools.javascript

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class ScriptExecutionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScriptExecutionReceiver"

        const val ACTION_EXECUTE_JS = "com.ai.assistance.operit.EXECUTE_JS"
        const val EXTRA_EXECUTION_MODE = "execution_mode"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_CODE_TEXT = "code_text"
        const val EXTRA_FUNCTION_NAME = "function_name"
        const val EXTRA_PARAMS = "params"
        const val EXTRA_PARAMS_FILE_PATH = "params_file_path"
        const val EXTRA_TEMP_FILE = "temp_file"
        const val EXTRA_TEMP_PARAMS_FILE = "temp_params_file"
        const val EXTRA_ENV_FILE_PATH = "env_file_path"
        const val EXTRA_TEMP_ENV_FILE = "temp_env_file"
        const val EXTRA_RESULT_FILE_PATH = "result_file_path"

        const val EXECUTION_MODE_FUNCTION = "function"
        const val EXECUTION_MODE_SCRIPT = "script"
        const val EXECUTION_MODE_CODE = "code"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXECUTE_JS) {
            return
        }

        val executionMode =
            intent.getStringExtra(EXTRA_EXECUTION_MODE)?.trim()?.lowercase().orEmpty()
                .ifBlank { EXECUTION_MODE_FUNCTION }
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val codeText = intent.getStringExtra(EXTRA_CODE_TEXT)
        val functionName =
            intent.getStringExtra(EXTRA_FUNCTION_NAME)
                ?.trim()
                .orEmpty()
                .ifBlank {
                    if (executionMode == EXECUTION_MODE_FUNCTION) {
                        "main"
                    } else {
                        ""
                    }
                }
        val hasValidRequest =
            when (executionMode) {
                EXECUTION_MODE_FUNCTION -> !filePath.isNullOrBlank() && functionName.isNotBlank()
                EXECUTION_MODE_SCRIPT -> !filePath.isNullOrBlank()
                EXECUTION_MODE_CODE -> !codeText.isNullOrBlank()
                else -> false
            }
        if (!hasValidRequest) {
            AppLogger.e(
                TAG,
                "Missing required parameters: mode=$executionMode, filePath=$filePath, functionName=$functionName, codeLength=${codeText?.length ?: 0}"
            )
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                execute(
                    context = context,
                    executionMode = executionMode,
                    filePath = filePath,
                    codeText = codeText,
                    functionName = functionName,
                    rawParamsJson = intent.getStringExtra(EXTRA_PARAMS),
                    paramsFilePath = intent.getStringExtra(EXTRA_PARAMS_FILE_PATH),
                    tempScript = intent.getBooleanExtra(EXTRA_TEMP_FILE, false),
                    tempParamsFile = intent.getBooleanExtra(EXTRA_TEMP_PARAMS_FILE, false),
                    envFilePath = intent.getStringExtra(EXTRA_ENV_FILE_PATH),
                    tempEnvFile = intent.getBooleanExtra(EXTRA_TEMP_ENV_FILE, false),
                    resultFilePath = intent.getStringExtra(EXTRA_RESULT_FILE_PATH)
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun execute(
        context: Context,
        executionMode: String,
        filePath: String?,
        codeText: String?,
        functionName: String,
        rawParamsJson: String?,
        paramsFilePath: String?,
        tempScript: Boolean,
        tempParamsFile: Boolean,
        envFilePath: String?,
        tempEnvFile: Boolean,
        resultFilePath: String?
    ) {
        val normalizedMode = executionMode.trim().lowercase().ifBlank { EXECUTION_MODE_FUNCTION }
        val scriptIdentityPath =
            when (normalizedMode) {
                EXECUTION_MODE_CODE -> filePath?.takeIf { it.isNotBlank() } ?: "<inline-code>"
                else -> filePath?.takeIf { it.isNotBlank() } ?: "<script>"
            }
        val scriptFile = filePath?.takeIf { it.isNotBlank() }?.let(::File)
        val resultFile = resolveResultFile(resultFilePath, scriptIdentityPath, functionName, normalizedMode)
        val paramsJson =
            try {
                resolveParamsJson(rawParamsJson = rawParamsJson, paramsFilePath = paramsFilePath)
            } catch (e: Exception) {
                val fallbackParamsJson = rawParamsJson.orEmpty().ifBlank { "{}" }
                val traceRecorder =
                    JsExecutionTraceRecorder(
                        scriptPath = scriptIdentityPath,
                        functionName =
                            if (normalizedMode == EXECUTION_MODE_FUNCTION) {
                                functionName
                            } else {
                                normalizedMode
                            },
                        paramsJson = fallbackParamsJson,
                        envFilePath = envFilePath
                    )
                val errorMessage = e.message ?: "Failed to resolve params JSON"
                AppLogger.e(TAG, errorMessage, e)
                runCatching {
                    traceRecorder.writeTo(
                        resultFile,
                        traceRecorder.buildPayload(
                            success = false,
                            result = null,
                            error = errorMessage
                        )
                    )
                }
                return
            }
        val traceRecorder =
            JsExecutionTraceRecorder(
                scriptPath = scriptIdentityPath,
                functionName =
                    if (normalizedMode == EXECUTION_MODE_FUNCTION) {
                        functionName
                    } else {
                        normalizedMode
                    },
                paramsJson = paramsJson,
                envFilePath = envFilePath
            )
        val scriptText =
            when (normalizedMode) {
                EXECUTION_MODE_CODE -> codeText.orEmpty()
                EXECUTION_MODE_SCRIPT, EXECUTION_MODE_FUNCTION -> {
                    if (scriptFile == null || !scriptFile.exists()) {
                        AppLogger.e(TAG, "JavaScript file not found: $scriptIdentityPath")
                        runCatching {
                            traceRecorder.writeTo(
                                resultFile,
                                traceRecorder.buildPayload(
                                    success = false,
                                    result = null,
                                    error = "JavaScript file not found: $scriptIdentityPath"
                                )
                            )
                        }
                        return
                    }
                    scriptFile.readText()
                }
                else -> {
                    val error = "Unsupported execution mode: $normalizedMode"
                    AppLogger.e(TAG, error)
                    runCatching {
                        traceRecorder.writeTo(
                            resultFile,
                            traceRecorder.buildPayload(
                                success = false,
                                result = null,
                                error = error
                            )
                        )
                    }
                    return
                }
            }
        if (normalizedMode == EXECUTION_MODE_CODE && scriptText.isBlank()) {
            AppLogger.e(TAG, "JavaScript code is empty for inline execution")
            runCatching {
                traceRecorder.writeTo(
                    resultFile,
                    traceRecorder.buildPayload(
                        success = false,
                        result = null,
                        error = "JavaScript code is empty"
                    )
                )
            }
            return
        }

        try {
            val engine = JsEngine(context)
            val parsedParams =
                parseParams(paramsJson).toMutableMap().apply {
                    put("__operit_toolpkg_runtime_kind", "sandbox")
                }
            val envOverrides = parseEnvFile(envFilePath)
            val result =
                when (normalizedMode) {
                    EXECUTION_MODE_SCRIPT, EXECUTION_MODE_CODE ->
                        engine.executeScriptCode(
                            script = scriptText,
                            params = parsedParams,
                            envOverrides = envOverrides,
                            executionListener = traceRecorder
                        )
                    else ->
                        engine.executeScriptFunction(
                            script = scriptText,
                            functionName = functionName,
                            params = parsedParams,
                            envOverrides = envOverrides,
                            executionListener = traceRecorder
                        )
                }
            val error = extractJsExecutionErrorMessage(result)
            val success = error == null
            traceRecorder.writeTo(
                resultFile,
                traceRecorder.buildPayload(
                    success = success,
                    result = result,
                    error = error
                )
            )
            AppLogger.d(TAG, "JavaScript execution result written to: ${resultFile.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing JavaScript: ${e.message}", e)
            runCatching {
                traceRecorder.writeTo(
                    resultFile,
                    traceRecorder.buildPayload(
                        success = false,
                        result = null,
                        error = e.message ?: e.javaClass.simpleName
                    )
                )
            }.onFailure { writeError ->
                AppLogger.e(TAG, "Error writing JS execution result file: ${writeError.message}", writeError)
            }
        } finally {
            if (scriptFile != null) {
                deleteIfNeeded(scriptFile, tempScript, "temporary script")
            }
            if (tempParamsFile && !paramsFilePath.isNullOrBlank()) {
                deleteIfNeeded(File(paramsFilePath), true, "temporary params file")
            }
            if (tempEnvFile && !envFilePath.isNullOrBlank()) {
                deleteIfNeeded(File(envFilePath), true, "temporary env file")
            }
        }
    }

    private fun resolveResultFile(
        rawPath: String?,
        scriptIdentityPath: String,
        functionName: String,
        executionMode: String
    ): File {
        val normalized = rawPath?.trim().orEmpty()
        if (normalized.isNotBlank()) {
            return File(normalized)
        }
        val safeScriptName =
            File(scriptIdentityPath).nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "script" }
        val safeFunctionName =
            when (executionMode) {
                EXECUTION_MODE_FUNCTION ->
                    functionName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "main" }
                else -> executionMode.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "script" }
            }
        val resultDir = File(OperitPaths.testDir(), "js_results")
        if (!resultDir.exists()) {
            resultDir.mkdirs()
        }
        return File(
            resultDir,
            "${safeScriptName}_${safeFunctionName}_${System.currentTimeMillis()}.json"
        )
    }

    private fun resolveParamsJson(rawParamsJson: String?, paramsFilePath: String?): String {
        val candidateFilePath = paramsFilePath?.trim().orEmpty()
        val rawPayload =
            if (candidateFilePath.isNotBlank()) {
                val paramsFile = File(candidateFilePath)
                if (!paramsFile.exists() || !paramsFile.isFile) {
                    throw IllegalArgumentException(
                        "params_file_path must point to an existing file: $candidateFilePath"
                    )
                }
                paramsFile.readText()
            } else {
                rawParamsJson.orEmpty()
            }

        return normalizeParamsJson(rawPayload)
    }

    private fun normalizeParamsJson(rawPayload: String): String {
        val trimmed = rawPayload.removePrefix("\uFEFF").trim()
        if (trimmed.isBlank()) {
            return "{}"
        }
        if (canParseJsonObject(trimmed)) {
            return trimmed
        }

        val unescapedQuotes = trimmed.replace("\\\"", "\"")
        if (unescapedQuotes != trimmed && canParseJsonObject(unescapedQuotes)) {
            AppLogger.w(TAG, "Recovered params JSON from quote-escaped transport payload")
            return unescapedQuotes
        }

        return trimmed
    }

    private fun canParseJsonObject(raw: String): Boolean {
        return runCatching { Json.parseToJsonElement(raw) }
            .getOrNull() is JsonObject
    }

    private fun parseParams(paramsJson: String): Map<String, Any?> {
        return try {
            val payload = Json.parseToJsonElement(paramsJson)
            if (payload is JsonObject) {
                payload.entries.associate { (key, value) ->
                    key to jsonElementToValue(value)
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing params: $paramsJson", e)
            emptyMap()
        }
    }

    private fun parseEnvFile(envFilePath: String?): Map<String, String> {
        if (envFilePath.isNullOrBlank()) {
            return emptyMap()
        }

        return try {
            val envFile = File(envFilePath)
            if (!envFile.exists()) {
                AppLogger.w(TAG, "Env file not found: $envFilePath")
                return emptyMap()
            }

            buildMap {
                envFile.readLines().forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith("#")) {
                        return@forEach
                    }
                    val separatorIndex = line.indexOf('=')
                    if (separatorIndex <= 0) {
                        return@forEach
                    }
                    val key = line.substring(0, separatorIndex).trim()
                    if (key.isEmpty()) {
                        return@forEach
                    }
                    val value = line.substring(separatorIndex + 1).trim().removeWrappingQuotes()
                    put(key, value)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing env file: $envFilePath", e)
            emptyMap()
        }
    }

    private fun String.removeWrappingQuotes(): String {
        if (length < 2) {
            return this
        }
        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, length - 1)
        } else {
            this
        }
    }

    private fun deleteIfNeeded(file: File, enabled: Boolean, label: String) {
        if (!enabled) {
            return
        }
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }.onFailure { error ->
            AppLogger.e(TAG, "Error deleting $label: ${file.absolutePath}", error)
        }
    }

    private fun jsonElementToValue(element: JsonElement): Any? {
        return when (element) {
            is JsonObject ->
                element.entries.associate { (key, value) ->
                    key to jsonElementToValue(value)
                }
            is JsonArray -> element.map(::jsonElementToValue)
            is JsonNull -> null
            is JsonPrimitive -> {
                if (element.isString) {
                    element.content
                } else {
                    element.booleanOrNull
                        ?: element.longOrNull
                        ?: element.doubleOrNull
                        ?: element.content
                }
            }
        }
    }
}

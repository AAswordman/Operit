package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ModelConfigConnectionTester
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.voice.TtsException
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.core.tools.FunctionModelBindingResultData
import com.ai.assistance.operit.core.tools.FunctionModelConfigResultData
import com.ai.assistance.operit.core.tools.FunctionModelConfigsResultData
import com.ai.assistance.operit.core.tools.FunctionModelMappingResultItem
import com.ai.assistance.operit.core.tools.CharacterCardActivationResultData
import com.ai.assistance.operit.core.tools.CharacterCardCreateResultData
import com.ai.assistance.operit.core.tools.CharacterCardDeleteResultData
import com.ai.assistance.operit.core.tools.CharacterCardExportResultData
import com.ai.assistance.operit.core.tools.CharacterCardImportResultData
import com.ai.assistance.operit.core.tools.CharacterCardResultData
import com.ai.assistance.operit.core.tools.CharacterCardResultItem
import com.ai.assistance.operit.core.tools.CharacterCardToolAccessConfigResultItem
import com.ai.assistance.operit.core.tools.CharacterCardUpdateResultData
import com.ai.assistance.operit.core.tools.CharacterCardsResultData
import com.ai.assistance.operit.core.tools.EnvironmentVariableReadResultData
import com.ai.assistance.operit.core.tools.EnvironmentVariableWriteResultData
import com.ai.assistance.operit.core.tools.McpRestartLogPluginResultItem
import com.ai.assistance.operit.core.tools.McpRestartWithLogsResultData
import com.ai.assistance.operit.core.tools.ModelConfigConnectionTestItemResultData
import com.ai.assistance.operit.core.tools.ModelConfigConnectionTestResultData
import com.ai.assistance.operit.core.tools.ModelConfigCreateResultData
import com.ai.assistance.operit.core.tools.ModelConfigDeleteResultData
import com.ai.assistance.operit.core.tools.ModelConfigResultItem
import com.ai.assistance.operit.core.tools.ModelConfigUpdateResultData
import com.ai.assistance.operit.core.tools.ModelConfigsResultData
import com.ai.assistance.operit.core.tools.SandboxPackageResultItem
import com.ai.assistance.operit.core.tools.SandboxPackageUpdateResultData
import com.ai.assistance.operit.core.tools.SandboxPackagesResultData
import com.ai.assistance.operit.core.tools.SandboxScriptExecutionResultData
import com.ai.assistance.operit.core.tools.SpeechServicesConfigResultData
import com.ai.assistance.operit.core.tools.SpeechServicesTtsPlaybackTestResultData
import com.ai.assistance.operit.core.tools.SpeechServicesUpdateResultData
import com.ai.assistance.operit.core.tools.SpeechSttHttpConfigResultItem
import com.ai.assistance.operit.core.tools.SpeechTtsHttpConfigResultItem
import com.ai.assistance.operit.core.tools.SpeechTtsVitsPackageConfigResultItem
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.javascript.JsExecutionTraceRecorder
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingStateRegistry
import com.ai.assistance.operit.ui.features.startup.screens.PluginStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 软件设置修改工具（包含 MCP 重启与日志收集） */
class StandardSoftwareSettingsModifyTools(private val context: Context) {

    fun readEnvironmentVariable(tool: AITool): ToolResult {
        val key = tool.parameters.find { it.name == "key" }?.value?.trim().orEmpty()
        if (key.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = EnvironmentVariableReadResultData(key = "", value = null, exists = false),
                error = "Missing required parameter: key"
            )
        }

        return try {
            val value = EnvPreferences.getInstance(context).getEnv(key)
            ToolResult(
                toolName = tool.name,
                success = true,
                result = EnvironmentVariableReadResultData(key = key, value = value, exists = value != null)
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = EnvironmentVariableReadResultData(key = key, value = null, exists = false),
                error = e.message ?: "Failed to read environment variable: $key"
            )
        }
    }

    fun writeEnvironmentVariable(tool: AITool): ToolResult {
        val key = tool.parameters.find { it.name == "key" }?.value?.trim().orEmpty()
        if (key.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    EnvironmentVariableWriteResultData(
                        key = "",
                        requestedValue = "",
                        value = null,
                        exists = false,
                        cleared = false
                    ),
                error = "Missing required parameter: key"
            )
        }

        val value = tool.parameters.find { it.name == "value" }?.value ?: ""
        return try {
            val envPreferences = EnvPreferences.getInstance(context)
            if (value.trim().isEmpty()) {
                envPreferences.removeEnv(key)
            } else {
                envPreferences.setEnv(key, value.trim())
            }

            val current = envPreferences.getEnv(key)
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    EnvironmentVariableWriteResultData(
                        key = key,
                        requestedValue = value,
                        value = current,
                        exists = current != null,
                        cleared = value.trim().isEmpty()
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    EnvironmentVariableWriteResultData(
                        key = key,
                        requestedValue = value,
                        value = null,
                        exists = false,
                        cleared = value.trim().isEmpty()
                    ),
                error = e.message ?: "Failed to write environment variable: $key"
            )
        }
    }

    fun listSandboxPackages(tool: AITool, packageManager: PackageManager): ToolResult {
        return try {
            val availablePackages = packageManager.getAvailablePackages(forceRefresh = true)
            val enabledSet = packageManager.getEnabledPackageNames().toSet()
            val disabledSet = packageManager.getDisabledPackages().toSet()
            val externalPackagesPath = packageManager.getExternalPackagesPath()
            val packageLoadErrors = packageManager.getPackageLoadErrors()

            val packages =
                availablePackages.entries
                .sortedBy { it.key.lowercase() }
                .map { (packageName, pkg) ->
                    val enabled = enabledSet.contains(packageName)
                    SandboxPackageResultItem(
                        packageName = packageName,
                        displayName = pkg.displayName.resolve(context),
                        description = pkg.description.resolve(context),
                        isBuiltIn = pkg.isBuiltIn,
                        enabledByDefault = pkg.enabledByDefault,
                        enabled = enabled,
                        imported = enabled,
                        isDisabledByUser = disabledSet.contains(packageName),
                        toolCount = pkg.tools.size,
                        manageMode = if (pkg.isBuiltIn) "toggle_only" else "file_and_toggle"
                    )
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    SandboxPackagesResultData(
                        externalPackagesPath = externalPackagesPath,
                        scriptDevGuide = "https://github.com/AAswordman/Operit/blob/main/docs/SCRIPT_DEV_SKILL.md",
                        totalCount = availablePackages.size,
                        builtInCount = availablePackages.values.count { it.isBuiltIn },
                        externalCount = availablePackages.values.count { !it.isBuiltIn },
                        enabledCount = availablePackages.keys.count { enabledSet.contains(it) },
                        disabledCount = availablePackages.keys.count { !enabledSet.contains(it) },
                        packages = packages,
                        packageLoadErrors = packageLoadErrors
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    SandboxPackagesResultData(
                        externalPackagesPath = "",
                        scriptDevGuide = "https://github.com/AAswordman/Operit/blob/main/docs/SCRIPT_DEV_SKILL.md",
                        totalCount = 0,
                        builtInCount = 0,
                        externalCount = 0,
                        enabledCount = 0,
                        disabledCount = 0,
                        packages = emptyList(),
                        packageLoadErrors = emptyMap()
                    ),
                error = e.message ?: "Failed to list sandbox packages"
            )
        }
    }

    fun setSandboxPackageEnabled(tool: AITool, packageManager: PackageManager): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value?.trim().orEmpty()
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    SandboxPackageUpdateResultData(
                        packageName = "",
                        requestedEnabled = false,
                        previousEnabled = false,
                        currentEnabled = false,
                        message = ""
                    ),
                error = "Missing required parameter: package_name"
            )
        }

        val enabledRaw = tool.parameters.find { it.name == "enabled" }?.value
        val enabled = parseBooleanParameter(enabledRaw)
        if (enabled == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    SandboxPackageUpdateResultData(
                        packageName = packageName,
                        requestedEnabled = false,
                        previousEnabled = false,
                        currentEnabled = false,
                        message = ""
                    ),
                error = "Invalid required parameter: enabled (expected true/false)"
            )
        }

        val availablePackages = packageManager.getAvailablePackages(forceRefresh = true)
        if (!availablePackages.containsKey(packageName)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    SandboxPackageUpdateResultData(
                        packageName = packageName,
                        requestedEnabled = enabled,
                        previousEnabled = false,
                        currentEnabled = false,
                        message = ""
                    ),
                error = "Sandbox package not found: $packageName"
            )
        }

        val previousEnabled = packageManager.isPackageEnabled(packageName)
        val operationMessage =
            if (enabled) {
                packageManager.enablePackage(packageName)
            } else {
                packageManager.disablePackage(packageName)
            }
        val currentEnabled = packageManager.isPackageEnabled(packageName)
        val success = currentEnabled == enabled

        return ToolResult(
            toolName = tool.name,
            success = success,
            result =
                SandboxPackageUpdateResultData(
                    packageName = packageName,
                    requestedEnabled = enabled,
                    previousEnabled = previousEnabled,
                    currentEnabled = currentEnabled,
                    message = operationMessage
                ),
            error =
                if (success) {
                    null
                } else {
                    "Failed to update sandbox package switch: $packageName"
                }
        )
    }

    fun executeSandboxScriptDirect(tool: AITool): ToolResult {
        val payload = executeSandboxScriptDirectResult(tool)
        return ToolResult(
            toolName = tool.name,
            success = payload.success,
            result = payload,
            error = payload.error
        )
    }

    private fun executeSandboxScriptDirectResult(tool: AITool): SandboxScriptExecutionResultData {
        val sourcePath =
            tool.parameters.find { it.name == "source_path" }?.value?.trim().orEmpty().ifBlank { "" }
        val sourceCode = tool.parameters.find { it.name == "source_code" }?.value.orEmpty()
        val paramsJson =
            tool.parameters.find { it.name == "params_json" }?.value?.takeIf { it.isNotBlank() }
                ?: "{}"
        val envFilePath =
            tool.parameters.find { it.name == "env_file_path" }?.value?.trim().orEmpty().ifBlank {
                ""
            }
        val scriptLabel =
            tool.parameters.find { it.name == "script_label" }?.value?.trim().orEmpty().ifBlank {
                "sandbox_script"
            }
        val waitMs =
            tool.parameters.find { it.name == "wait_ms" }?.value?.toLongOrNull()?.coerceAtLeast(1000L)
                ?: 15000L

        val hasSourcePath = sourcePath.isNotBlank()
        val hasSourceCode = sourceCode.isNotBlank()
        val executionMode = if (hasSourceCode) "code" else "script"
        val traceRecorder =
            JsExecutionTraceRecorder(
                scriptPath = if (hasSourcePath) sourcePath else "<inline:$scriptLabel>",
                functionName = executionMode,
                paramsJson = paramsJson,
                envFilePath = envFilePath.ifBlank { null }
            )

        fun buildFailure(message: String, result: Any? = null): SandboxScriptExecutionResultData {
            return traceRecorder.buildResultData(
                success = false,
                result = result,
                error = message,
                executionMode = executionMode,
                scriptLabel = scriptLabel,
                requestedWaitMs = waitMs
            )
        }

        if (hasSourcePath == hasSourceCode) {
            return buildFailure("Exactly one of source_path or source_code is required")
        }

        val parsedParams =
            try {
                parseJsonObjectToMap(paramsJson)
            } catch (e: Exception) {
                return buildFailure(e.message ?: "Invalid params_json")
            }

        val envOverrides =
            try {
                parseEnvFile(envFilePath.ifBlank { null })
            } catch (e: Exception) {
                return buildFailure(e.message ?: "Invalid env_file_path")
            }

        val scriptText =
            try {
                if (hasSourceCode) {
                    sourceCode
                } else {
                    val file = File(sourcePath)
                    if (!file.exists() || !file.isFile) {
                        return buildFailure("source_path must point to an existing file: $sourcePath")
                    }
                    file.readText()
                }
            } catch (e: Exception) {
                return buildFailure(e.message ?: "Failed to read script input")
            }

        val timeoutSec = ((waitMs + 999L) / 1000L).coerceAtLeast(1L)
        return try {
            val result =
                JsEngine(context).executeScriptCode(
                    script = scriptText,
                    params = parsedParams,
                    envOverrides = envOverrides,
                    timeoutSec = timeoutSec,
                    executionListener = traceRecorder
                )
            val success = !result.toString().startsWith("Error:", ignoreCase = true)
            val error =
                if (success) {
                    null
                } else {
                    result?.toString()?.removePrefix("Error:")?.trim().orEmpty()
                }
            traceRecorder.buildResultData(
                success = success,
                result = result,
                error = error,
                executionMode = executionMode,
                scriptLabel = scriptLabel,
                requestedWaitMs = waitMs
            )
        } catch (e: Exception) {
            buildFailure(e.message ?: "Sandbox script execution failed")
        }
    }

    suspend fun getSpeechServicesConfig(tool: AITool): ToolResult {
        return try {
            val profilePrefs = SpeechServiceProfilesPreferences(context)
            val ttsProfile = profilePrefs.getCurrentTtsProfile()
            val sttProfile = profilePrefs.getCurrentSttProfile()
            val ttsServiceType = ttsProfile.serviceType
            val ttsHttpConfig = ttsProfile.httpConfig
            val ttsVitsConfig = ttsProfile.vitsConfig
            val ttsCleanerRegexs = ttsProfile.cleanerRegexs
            val ttsSpeechRate = ttsProfile.speechRate
            val ttsPitch = ttsProfile.pitch

            val sttServiceType = sttProfile.serviceType
            val sttHttpConfig = sttProfile.httpConfig

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    SpeechServicesConfigResultData(
                        ttsServiceType = ttsServiceType.name,
                        ttsHttpConfig =
                            SpeechTtsHttpConfigResultItem(
                                urlTemplate = ttsHttpConfig.urlTemplate,
                                apiKeySet = ttsHttpConfig.apiKey.isNotBlank(),
                                apiKeyPreview = maskSecret(ttsHttpConfig.apiKey),
                                headers = ttsHttpConfig.headers,
                                httpMethod = ttsHttpConfig.httpMethod,
                                requestBody = ttsHttpConfig.requestBody,
                                contentType = ttsHttpConfig.contentType,
                                localeTag = ttsHttpConfig.localeTag,
                                voiceId = ttsHttpConfig.voiceId,
                                modelName = ttsHttpConfig.modelName,
                                responsePipeline = ttsHttpConfig.responsePipeline
                            ),
                        ttsVitsPackageConfig =
                            SpeechTtsVitsPackageConfigResultItem(
                                packagePath = ttsVitsConfig.packagePath,
                                speakerId = ttsVitsConfig.speakerId,
                                options = ttsVitsConfig.options
                            ),
                        ttsCleanerRegexs = ttsCleanerRegexs,
                        ttsSpeechRate = ttsSpeechRate,
                        ttsPitch = ttsPitch,
                        sttServiceType = sttServiceType.name,
                        sttHttpConfig =
                            SpeechSttHttpConfigResultItem(
                                endpointUrl = sttHttpConfig.endpointUrl,
                                apiKeySet = sttHttpConfig.apiKey.isNotBlank(),
                                apiKeyPreview = maskSecret(sttHttpConfig.apiKey),
                                modelName = sttHttpConfig.modelName
                            )
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to get speech services config"
            )
        }
    }

    suspend fun setSpeechServicesConfig(tool: AITool): ToolResult {
        return try {
            val profilePrefs = SpeechServiceProfilesPreferences(context)
            val currentTtsProfile = profilePrefs.getCurrentTtsProfile()
            val currentSttProfile = profilePrefs.getCurrentSttProfile()
            val currentTtsServiceType = currentTtsProfile.serviceType
            val currentTtsHttpConfig = currentTtsProfile.httpConfig
            val currentTtsVitsConfig = currentTtsProfile.vitsConfig
            val currentTtsCleanerRegexs = currentTtsProfile.cleanerRegexs
            val currentTtsSpeechRate = currentTtsProfile.speechRate
            val currentTtsPitch = currentTtsProfile.pitch

            val currentSttServiceType = currentSttProfile.serviceType
            val currentSttHttpConfig = currentSttProfile.httpConfig

            val hasField = { name: String -> tool.parameters.any { it.name == name } }

            val ttsServiceType =
                getParameterValue(tool, "tts_service_type")?.let { raw ->
                    VoiceServiceFactory.VoiceServiceType.values().firstOrNull {
                        it.name.equals(raw.trim(), ignoreCase = true)
                    } ?: throw IllegalArgumentException("Invalid tts_service_type: $raw")
                } ?: currentTtsServiceType

            val sttServiceType =
                getParameterValue(tool, "stt_service_type")?.let { raw ->
                    when {
                        raw.trim().equals("SHERPA_MNN", ignoreCase = true) ->
                            SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN

                        else ->
                            SpeechServiceFactory.SpeechServiceType.values().firstOrNull {
                                it.name.equals(raw.trim(), ignoreCase = true)
                            } ?: throw IllegalArgumentException("Invalid stt_service_type: $raw")
                    }
                } ?: currentSttServiceType

            val ttsHeaders =
                if (hasField("tts_headers")) {
                    val raw = getParameterValue(tool, "tts_headers").orEmpty().trim()
                    if (raw.isBlank()) {
                        emptyMap()
                    } else {
                        val jsonObj =
                            try {
                                JSONObject(raw)
                            } catch (_: Exception) {
                                throw IllegalArgumentException("Invalid JSON object parameter: tts_headers")
                            }
                        val headers = mutableMapOf<String, String>()
                        jsonObj.keys().forEach { key ->
                            headers[key] = jsonObj.optString(key, "")
                        }
                        headers
                    }
                } else {
                    currentTtsHttpConfig.headers
                }

            val ttsCleanerRegexs =
                if (hasField("tts_cleaner_regexs")) {
                    val raw = getParameterValue(tool, "tts_cleaner_regexs").orEmpty().trim()
                    if (raw.isBlank()) {
                        emptyList()
                    } else {
                        val arr =
                            try {
                                JSONArray(raw)
                            } catch (_: Exception) {
                                throw IllegalArgumentException(
                                    "Invalid JSON array parameter: tts_cleaner_regexs"
                                )
                            }
                        buildList {
                            for (i in 0 until arr.length()) {
                                val item = arr.optString(i, "").trim()
                                if (item.isNotBlank()) add(item)
                            }
                        }
                    }
                } else {
                    currentTtsCleanerRegexs
                }

            val ttsResponsePipeline =
                if (hasField("tts_response_pipeline")) {
                    val raw = getParameterValue(tool, "tts_response_pipeline").orEmpty()
                    try {
                        HttpTtsResponsePipelineStep.parseList(raw)
                    } catch (_: Exception) {
                        throw IllegalArgumentException("Invalid JSON array parameter: tts_response_pipeline")
                    }
                } else {
                    currentTtsHttpConfig.responsePipeline
                }

            val ttsHttpMethod =
                if (hasField("tts_http_method")) {
                    val method = getParameterValue(tool, "tts_http_method").orEmpty().trim().uppercase()
                    if (method != "GET" && method != "POST") {
                        throw IllegalArgumentException("Invalid tts_http_method: $method (expected GET/POST)")
                    }
                    method
                } else {
                    currentTtsHttpConfig.httpMethod
                }

            val ttsSpeechRate =
                if (hasField("tts_speech_rate")) {
                    getParameterValue(tool, "tts_speech_rate")?.trim()?.toFloatOrNull()
                        ?: throw IllegalArgumentException("Invalid number parameter: tts_speech_rate")
                } else {
                    currentTtsSpeechRate
                }

            val ttsPitch =
                if (hasField("tts_pitch")) {
                    getParameterValue(tool, "tts_pitch")?.trim()?.toFloatOrNull()
                        ?: throw IllegalArgumentException("Invalid number parameter: tts_pitch")
                } else {
                    currentTtsPitch
                }

            val ttsHttpConfig =
                currentTtsHttpConfig.copy(
                    urlTemplate =
                        if (hasField("tts_url_template")) {
                            getParameterValue(tool, "tts_url_template").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.urlTemplate
                        },
                    apiKey =
                        if (hasField("tts_api_key")) {
                            getParameterValue(tool, "tts_api_key").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.apiKey
                        },
                    headers = ttsHeaders,
                    httpMethod = ttsHttpMethod,
                    requestBody =
                        if (hasField("tts_request_body")) {
                            getParameterValue(tool, "tts_request_body").orEmpty()
                        } else {
                            currentTtsHttpConfig.requestBody
                        },
                    contentType =
                        if (hasField("tts_content_type")) {
                            getParameterValue(tool, "tts_content_type").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.contentType
                        },
                    localeTag =
                        if (hasField("tts_locale")) {
                            getParameterValue(tool, "tts_locale").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.localeTag
                        },
                    voiceId =
                        if (hasField("tts_voice_id")) {
                            getParameterValue(tool, "tts_voice_id").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.voiceId
                        },
                    modelName =
                        if (hasField("tts_model_name")) {
                            getParameterValue(tool, "tts_model_name").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.modelName
                        },
                    responsePipeline = ttsResponsePipeline
                )

            val ttsVitsOptions =
                if (hasField("tts_vits_options")) {
                    val raw = getParameterValue(tool, "tts_vits_options").orEmpty().trim()
                    if (raw.isBlank()) {
                        emptyMap()
                    } else {
                        val jsonObj =
                            try {
                                JSONObject(raw)
                            } catch (_: Exception) {
                                throw IllegalArgumentException("Invalid JSON object parameter: tts_vits_options")
                            }
                        val options = mutableMapOf<String, String>()
                        jsonObj.keys().forEach { key ->
                            options[key] = jsonObj.optString(key, "")
                        }
                        options
                    }
                } else {
                    currentTtsVitsConfig.options
                }

            val ttsVitsConfig =
                currentTtsVitsConfig.copy(
                    packagePath =
                        if (hasField("tts_vits_package_path")) {
                            getParameterValue(tool, "tts_vits_package_path").orEmpty().trim()
                        } else {
                            currentTtsVitsConfig.packagePath
                        },
                    speakerId =
                        if (hasField("tts_vits_speaker_id")) {
                            getParameterValue(tool, "tts_vits_speaker_id").orEmpty().trim()
                        } else {
                            currentTtsVitsConfig.speakerId
                        },
                    options = ttsVitsOptions
                )

            val sttHttpConfig =
                currentSttHttpConfig.copy(
                    endpointUrl =
                        if (hasField("stt_endpoint_url")) {
                            getParameterValue(tool, "stt_endpoint_url").orEmpty().trim()
                        } else {
                            currentSttHttpConfig.endpointUrl
                        },
                    apiKey =
                        if (hasField("stt_api_key")) {
                            getParameterValue(tool, "stt_api_key").orEmpty().trim()
                        } else {
                            currentSttHttpConfig.apiKey
                        },
                    modelName =
                        if (hasField("stt_model_name")) {
                            getParameterValue(tool, "stt_model_name").orEmpty().trim()
                        } else {
                            currentSttHttpConfig.modelName
                        }
                )

            val updateFieldNames =
                listOf(
                    "tts_service_type",
                    "tts_url_template",
                    "tts_api_key",
                    "tts_headers",
                    "tts_http_method",
                    "tts_request_body",
                    "tts_content_type",
                    "tts_locale",
                    "tts_voice_id",
                    "tts_model_name",
                    "tts_response_pipeline",
                    "tts_vits_package_path",
                    "tts_vits_speaker_id",
                    "tts_vits_options",
                    "tts_cleaner_regexs",
                    "tts_speech_rate",
                    "tts_pitch",
                    "stt_service_type",
                    "stt_endpoint_url",
                    "stt_api_key",
                    "stt_model_name"
                ).filter { hasField(it) }
            val hasAnyUpdate = updateFieldNames.isNotEmpty()

            if (!hasAnyUpdate) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "No update fields provided for speech services config"
                )
            }

            profilePrefs.updateTtsProfile(
                currentTtsProfile.copy(
                    serviceType = ttsServiceType,
                    httpConfig = ttsHttpConfig,
                    vitsConfig = ttsVitsConfig,
                    cleanerRegexs = ttsCleanerRegexs,
                    speechRate = ttsSpeechRate,
                    pitch = ttsPitch,
                ),
            )
            profilePrefs.updateSttProfile(
                currentSttProfile.copy(
                    serviceType = sttServiceType,
                    httpConfig = sttHttpConfig,
                ),
            )

            VoiceServiceFactory.resetInstance()
            SpeechServiceFactory.resetInstance()

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    SpeechServicesUpdateResultData(
                        updated = true,
                        changedFields = updateFieldNames,
                        ttsServiceType = ttsServiceType.name,
                        sttServiceType = sttServiceType.name,
                        ttsApiKeySet = ttsHttpConfig.apiKey.isNotBlank(),
                        sttApiKeySet = sttHttpConfig.apiKey.isNotBlank()
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to set speech services config"
            )
        }
    }

    suspend fun testTtsPlayback(tool: AITool): ToolResult {
        val text = getParameterValue(tool, "text")?.trim().orEmpty()
        if (text.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: text"
            )
        }

        val profilePrefs = SpeechServiceProfilesPreferences(context)
        var ttsServiceTypeName = ""
        var providerClass = ""
        var initialized = false
        var interrupt = true
        var speechRate = 0f
        var pitch = 0f

        return try {
            val ttsProfile = profilePrefs.getCurrentTtsProfile()
            val ttsServiceType = ttsProfile.serviceType
            ttsServiceTypeName = ttsServiceType.name
            val hasSpeechRateOverride = tool.parameters.any { it.name == "speech_rate" }
            val hasPitchOverride = tool.parameters.any { it.name == "pitch" }
            interrupt =
                getParameterValue(tool, "interrupt")?.let { raw ->
                    parseBooleanParameter(raw)
                        ?: throw IllegalArgumentException("Invalid boolean parameter: interrupt")
                } ?: true
            speechRate =
                if (hasSpeechRateOverride) {
                    getParameterValue(tool, "speech_rate")?.trim()?.toFloatOrNull()
                        ?: throw IllegalArgumentException("Invalid number parameter: speech_rate")
                } else {
                    ttsProfile.speechRate
                }
            pitch =
                if (hasPitchOverride) {
                    getParameterValue(tool, "pitch")?.trim()?.toFloatOrNull()
                        ?: throw IllegalArgumentException("Invalid number parameter: pitch")
                } else {
                    ttsProfile.pitch
                }

            VoiceServiceFactory.resetInstance()
            val voiceService = VoiceServiceFactory.getInstance(context)
            providerClass = voiceService.javaClass.simpleName
            initialized = voiceService.initialize()
            if (!initialized) {
                val errorMessage = "TTS service initialization returned false"
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = buildTtsPlaybackTestResult(
                        ttsServiceTypeName = ttsServiceTypeName,
                        providerClass = providerClass,
                        initialized = false,
                        playbackTriggered = false,
                        interrupt = interrupt,
                        textLength = text.length,
                        speechRate = speechRate,
                        pitch = pitch,
                        errorMessageOverride = errorMessage
                    ),
                    error = errorMessage
                )
            }

            val playbackTriggered = voiceService.speak(
                text = text,
                interrupt = interrupt,
                rate = speechRate,
                pitch = pitch
            )

            val errorMessage = if (playbackTriggered) null else "TTS playback did not start"
            ToolResult(
                toolName = tool.name,
                success = playbackTriggered,
                result = buildTtsPlaybackTestResult(
                    ttsServiceTypeName = ttsServiceTypeName,
                    providerClass = providerClass,
                    initialized = true,
                    playbackTriggered = playbackTriggered,
                    interrupt = interrupt,
                    textLength = text.length,
                    speechRate = speechRate,
                    pitch = pitch,
                    errorMessageOverride = errorMessage
                ),
                error = errorMessage
            )
        } catch (e: IllegalArgumentException) {
            val errorMessage = e.message ?: "Invalid parameter"
            ToolResult(
                toolName = tool.name,
                success = false,
                result = buildTtsPlaybackTestResult(
                    ttsServiceTypeName = ttsServiceTypeName,
                    providerClass = providerClass,
                    initialized = initialized,
                    playbackTriggered = false,
                    interrupt = interrupt,
                    textLength = text.length,
                    speechRate = speechRate,
                    pitch = pitch,
                    error = e,
                    errorMessageOverride = errorMessage
                ),
                error = errorMessage
            )
        } catch (e: Exception) {
            val errorMessage = formatTtsPlaybackError(e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = buildTtsPlaybackTestResult(
                    ttsServiceTypeName = ttsServiceTypeName,
                    providerClass = providerClass,
                    initialized = initialized,
                    playbackTriggered = false,
                    interrupt = interrupt,
                    textLength = text.length,
                    speechRate = speechRate,
                    pitch = pitch,
                    error = e,
                    errorMessageOverride = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    suspend fun listModelConfigs(tool: AITool): ToolResult {
        return try {
            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val configIds = modelConfigManager.configListFlow.first()
            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()

            val configById = mutableMapOf<String, ModelConfigData>()
            val configs = mutableListOf<ModelConfigResultItem>()
            configIds.forEach { configId ->
                val config = modelConfigManager.getModelConfigFlow(configId).first()
                configById[configId] = config
                configs.add(modelConfigToResultItem(config))
            }

            val functionMappings = mutableListOf<FunctionModelMappingResultItem>()
            mappingWithIndex.entries
                .sortedBy { it.key.name }
                .forEach { (functionType, mapping) ->
                    val config = configById[mapping.configId]
                    functionMappings.add(
                        FunctionModelMappingResultItem(
                            functionType = functionType.name,
                            configId = mapping.configId,
                            configName = config?.name,
                            modelIndex = mapping.modelIndex,
                            selectedModel = config?.let { getModelByIndex(it.modelName, mapping.modelIndex) }
                        )
                    )
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigsResultData(
                        totalConfigCount = configIds.size,
                        defaultConfigId = ModelConfigManager.DEFAULT_CONFIG_ID,
                        configs = configs,
                        functionMappings = functionMappings
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to list model configs"
            )
        }
    }

    suspend fun createModelConfig(tool: AITool): ToolResult {
        return try {
            val modelConfigManager = ModelConfigManager(context)
            modelConfigManager.initializeIfNeeded()

            val name =
                getParameterValue(tool, "name")?.trim().takeUnless { it.isNullOrBlank() }
                    ?: "New Model Config"
            val configId = modelConfigManager.createConfig(name)
            val created = modelConfigManager.getModelConfigFlow(configId).first()

            val (updated, changedFields) = applyModelConfigUpdates(tool, created, includeName = false)
            val finalConfig =
                if (changedFields.isNotEmpty()) {
                    modelConfigManager.saveModelConfig(updated)
                    updated
                } else {
                    created
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigCreateResultData(
                        created = true,
                        config = modelConfigToResultItem(finalConfig),
                        changedFields = changedFields
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to create model config"
            )
        }
    }

    suspend fun updateModelConfig(tool: AITool): ToolResult {
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }

        return try {
            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val current =
                modelConfigManager.getModelConfig(configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: $configId"
                    )

            val (updated, changedFields) = applyModelConfigUpdates(tool, current, includeName = true)
            val finalConfig =
                if (changedFields.isNotEmpty()) {
                    modelConfigManager.saveModelConfig(updated)
                    updated
                } else {
                    current
                }

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val affectedFunctions =
                mappingWithIndex.entries
                    .filter { it.value.configId == configId }
                    .map { it.key }
                    .sortedBy { it.name }
            affectedFunctions.forEach { functionType ->
                runCatching { EnhancedAIService.refreshServiceForFunction(context, functionType) }
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigUpdateResultData(
                        updated = changedFields.isNotEmpty(),
                        config = modelConfigToResultItem(finalConfig),
                        changedFields = changedFields,
                        affectedFunctions = affectedFunctions.map { it.name }
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to update model config: $configId"
            )
        }
    }

    suspend fun deleteModelConfig(tool: AITool): ToolResult {
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }
        if (configId == ModelConfigManager.DEFAULT_CONFIG_ID) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "The default model config cannot be deleted"
            )
        }

        return try {
            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val configList = modelConfigManager.configListFlow.first()
            if (!configList.contains(configId)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Model config not found: $configId"
                )
            }

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val updatedMapping = mappingWithIndex.toMutableMap()
            val affectedFunctions = mutableListOf<FunctionType>()

            mappingWithIndex.forEach { (functionType, mapping) ->
                if (mapping.configId == configId) {
                    updatedMapping[functionType] =
                        FunctionConfigMapping(
                            configId = FunctionalConfigManager.DEFAULT_CONFIG_ID,
                            modelIndex = 0
                        )
                    affectedFunctions.add(functionType)
                }
            }

            if (affectedFunctions.isNotEmpty()) {
                functionalConfigManager.saveFunctionConfigMappingWithIndex(updatedMapping)
            }

            modelConfigManager.deleteConfig(configId)

            affectedFunctions
                .sortedBy { it.name }
                .forEach { functionType ->
                    runCatching { EnhancedAIService.refreshServiceForFunction(context, functionType) }
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigDeleteResultData(
                        deleted = true,
                        configId = configId,
                        affectedFunctions = affectedFunctions.sortedBy { it.name }.map { it.name },
                        fallbackConfigId = FunctionalConfigManager.DEFAULT_CONFIG_ID
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to delete model config: $configId"
            )
        }
    }

    suspend fun listFunctionModelConfigs(tool: AITool): ToolResult {
        return try {
            val functionalConfigManager = FunctionalConfigManager(context)
            functionalConfigManager.initializeIfNeeded()

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()

            val mappings = mutableListOf<FunctionModelMappingResultItem>()
            FunctionType.values().forEach { functionType ->
                val mapping =
                    mappingWithIndex[functionType]
                        ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
                mappings.add(
                    FunctionModelMappingResultItem(
                        functionType = functionType.name,
                        configId = mapping.configId,
                        modelIndex = mapping.modelIndex
                    )
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    FunctionModelConfigsResultData(
                        defaultConfigId = FunctionalConfigManager.DEFAULT_CONFIG_ID,
                        mappings = mappings
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to list function model configs"
            )
        }
    }

    suspend fun getFunctionModelConfig(tool: AITool): ToolResult {
        val functionTypeRaw = getParameterValue(tool, "function_type")?.trim().orEmpty()
        if (functionTypeRaw.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: function_type"
            )
        }

        return try {
            val functionType =
                parseFunctionType(functionTypeRaw)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid function_type: $functionTypeRaw"
                    )

            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val mapping =
                mappingWithIndex[functionType]
                    ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)

            val config =
                modelConfigManager.getModelConfig(mapping.configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: ${mapping.configId}"
                    )

            val actualModelIndex = getValidModelIndex(config.modelName, mapping.modelIndex)
            val selectedModel = getModelByIndex(config.modelName, actualModelIndex)

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    FunctionModelConfigResultData(
                        defaultConfigId = FunctionalConfigManager.DEFAULT_CONFIG_ID,
                        functionType = functionType.name,
                        configId = mapping.configId,
                        configName = config.name,
                        modelIndex = mapping.modelIndex,
                        actualModelIndex = actualModelIndex,
                        selectedModel = selectedModel,
                        config = modelConfigToResultItem(config)
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to get function model config"
            )
        }
    }

    suspend fun setFunctionModelConfig(tool: AITool): ToolResult {
        val functionTypeRaw = getParameterValue(tool, "function_type")?.trim().orEmpty()
        if (functionTypeRaw.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: function_type"
            )
        }
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }

        return try {
            val functionType =
                parseFunctionType(functionTypeRaw)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid function_type: $functionTypeRaw"
                    )
            val requestedModelIndex =
                getOptionalIntParameter(tool, "model_index")?.coerceAtLeast(0) ?: 0

            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val config =
                modelConfigManager.getModelConfig(configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: $configId"
                    )
            val actualModelIndex = getValidModelIndex(config.modelName, requestedModelIndex)
            val selectedModel = getModelByIndex(config.modelName, actualModelIndex)

            functionalConfigManager.setConfigForFunction(functionType, configId, actualModelIndex)
            runCatching { EnhancedAIService.refreshServiceForFunction(context, functionType) }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    FunctionModelBindingResultData(
                        functionType = functionType.name,
                        configId = configId,
                        configName = config.name,
                        requestedModelIndex = requestedModelIndex,
                        actualModelIndex = actualModelIndex,
                        selectedModel = selectedModel
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to set function model config"
            )
        }
    }

    suspend fun testModelConfigConnection(tool: AITool): ToolResult {
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }

        return try {
            val requestedModelIndex =
                getOptionalIntParameter(tool, "model_index")?.coerceAtLeast(0) ?: 0
            val modelConfigManager = ModelConfigManager(context)
            modelConfigManager.initializeIfNeeded()

            val config =
                modelConfigManager.getModelConfig(configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: $configId"
                    )

            val report =
                ModelConfigConnectionTester.run(
                    context = context,
                    modelConfigManager = modelConfigManager,
                    config = config,
                    requestedModelIndex = requestedModelIndex
                )

            val testItems =
                report.items.map { item ->
                    ModelConfigConnectionTestItemResultData(
                        type = item.type.name.lowercase(),
                        success = item.success,
                        error = item.error
                    )
                }

            ToolResult(
                toolName = tool.name,
                success = report.success,
                result =
                    ModelConfigConnectionTestResultData(
                        configId = report.configId,
                        configName = report.configName,
                        providerType = report.providerType,
                        requestedModelIndex = report.requestedModelIndex,
                        actualModelIndex = report.actualModelIndex,
                        testedModelName = report.testedModelName,
                        success = report.success,
                        totalTests = report.items.size,
                        passedTests = report.items.count { it.success },
                        failedTests = report.items.count { !it.success },
                        tests = testItems
                    ),
                error = if (report.success) null else "One or more connection tests failed"
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to test model config connection"
            )
        }
    }

    suspend fun listCharacterCards(tool: AITool): ToolResult {
        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            val cards = characterCardManager.getAllCharacterCards()
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardsResultData(
                        totalCount = cards.size,
                        activeCharacterCardId = activeCharacterCardId(characterCardManager),
                        cards = cards.map(::characterCardToResultItem)
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    CharacterCardsResultData(
                        totalCount = 0,
                        activeCharacterCardId = null,
                        cards = emptyList()
                    ),
                error = e.toString()
            )
        }
    }

    suspend fun getCharacterCard(tool: AITool): ToolResult {
        val characterCardId = getParameterValue(tool, "character_card_id")?.trim().orEmpty()
        if (characterCardId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: character_card_id"
            )
        }

        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            val card = requireCharacterCard(characterCardManager, characterCardId)
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardResultData(
                        card = characterCardToResultItem(card),
                        activeCharacterCardId = activeCharacterCardId(characterCardManager)
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun createCharacterCard(tool: AITool): ToolResult {
        return try {
            val (draft, changedFields) =
                applyCharacterCardUpdates(
                    tool = tool,
                    current = CharacterCard(id = "", name = ""),
                    requireName = true
                )
            val characterCardManager = CharacterCardManager.getInstance(context)
            val characterCardId = characterCardManager.createCharacterCard(draft)
            val card = requireCharacterCard(characterCardManager, characterCardId)
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardCreateResultData(
                        created = true,
                        card = characterCardToResultItem(card),
                        activeCharacterCardId = activeCharacterCardId(characterCardManager),
                        changedFields = changedFields
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun updateCharacterCard(tool: AITool): ToolResult {
        val characterCardId = getParameterValue(tool, "character_card_id")?.trim().orEmpty()
        if (characterCardId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: character_card_id"
            )
        }

        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            val current = requireCharacterCard(characterCardManager, characterCardId)
            val (updated, changedFields) =
                applyCharacterCardUpdates(
                    tool = tool,
                    current = current,
                    requireName = false
                )
            if (changedFields.isEmpty()) {
                throw IllegalArgumentException("At least one character card update field is required")
            }
            characterCardManager.updateCharacterCard(updated)
            val card = requireCharacterCard(characterCardManager, characterCardId)
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardUpdateResultData(
                        updated = true,
                        card = characterCardToResultItem(card),
                        activeCharacterCardId = activeCharacterCardId(characterCardManager),
                        changedFields = changedFields
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun deleteCharacterCard(tool: AITool): ToolResult {
        val characterCardId = getParameterValue(tool, "character_card_id")?.trim().orEmpty()
        if (characterCardId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: character_card_id"
            )
        }

        if (characterCardId == CharacterCardManager.DEFAULT_CHARACTER_CARD_ID) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "The default character card cannot be deleted"
            )
        }

        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            requireCharacterCard(characterCardManager, characterCardId)
            characterCardManager.deleteCharacterCard(characterCardId)
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardDeleteResultData(
                        deleted = true,
                        characterCardId = characterCardId,
                        activeCharacterCardId = activeCharacterCardId(characterCardManager)
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun setActiveCharacterCard(tool: AITool): ToolResult {
        val characterCardId = getParameterValue(tool, "character_card_id")?.trim().orEmpty()
        if (characterCardId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: character_card_id"
            )
        }

        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            requireCharacterCard(characterCardManager, characterCardId)
            characterCardManager.setActiveCharacterCard(characterCardId)
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardActivationResultData(
                        activeCharacterCardId = activeCharacterCardId(characterCardManager)
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun clearActiveCharacterCard(tool: AITool): ToolResult {
        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            characterCardManager.clearActiveCharacterCard()
            ToolResult(
                toolName = tool.name,
                success = true,
                result = CharacterCardActivationResultData(activeCharacterCardId = null)
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun importCharacterCardFromTavernJson(tool: AITool): ToolResult {
        val tavernJson = getParameterValue(tool, "tavern_json")?.takeIf { it.isNotBlank() }
        if (tavernJson == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: tavern_json"
            )
        }

        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            val importResult = characterCardManager.createCharacterCardFromTavernJson(tavernJson)
            if (importResult.isFailure) {
                val importError = importResult.exceptionOrNull()?.toString()
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = importError
                )
            }
            val characterCardId = importResult.getOrThrow()
            val card = requireCharacterCard(characterCardManager, characterCardId)
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardImportResultData(
                        imported = true,
                        card = characterCardToResultItem(card),
                        activeCharacterCardId = activeCharacterCardId(characterCardManager)
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun exportCharacterCardToTavernJson(tool: AITool): ToolResult {
        val characterCardId = getParameterValue(tool, "character_card_id")?.trim().orEmpty()
        if (characterCardId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: character_card_id"
            )
        }

        return try {
            val characterCardManager = CharacterCardManager.getInstance(context)
            requireCharacterCard(characterCardManager, characterCardId)
            val exportResult = characterCardManager.exportCharacterCardToTavernJson(characterCardId)
            if (exportResult.isFailure) {
                val exportError = exportResult.exceptionOrNull()?.toString()
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = exportError
                )
            }
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardExportResultData(
                        characterCardId = characterCardId,
                        tavernJson = exportResult.getOrThrow()
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.toString()
            )
        }
    }

    suspend fun restartMcpWithLogs(tool: AITool): ToolResult {
        val timeoutMs =
            tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull()
                ?.coerceIn(5000L, 600000L)
                ?: 120000L

        val pluginLoadingState = PluginLoadingStateRegistry.getState()
        val lifecycleScope = PluginLoadingStateRegistry.getScope()

        if (pluginLoadingState == null || lifecycleScope == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    McpRestartWithLogsResultData(
                        timeoutMs = timeoutMs,
                        elapsedMs = 0L,
                        timedOut = false,
                        progress = 0.0,
                        message = "",
                        pluginsTotal = 0,
                        pluginsStarted = 0,
                        successCount = 0,
                        failedCount = 0,
                        plugins = emptyList(),
                        extraLogs = emptyMap()
                    ),
                error = "Plugin loading state is unavailable. Open the main screen and retry."
            )
        }

        pluginLoadingState.reset()
        pluginLoadingState.show()
        pluginLoadingState.initializeMCPServer(context, lifecycleScope)

        val startAt = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startAt
            val finished =
                pluginLoadingState.progress.value >= 0.999f &&
                    pluginLoadingState.message.value.isNotBlank()
            if (finished || elapsed >= timeoutMs) {
                break
            }
            delay(250L)
        }

        val elapsedMs = System.currentTimeMillis() - startAt
        val timedOut = elapsedMs >= timeoutMs
        val plugins = pluginLoadingState.plugins.value
        val pluginLogs = pluginLoadingState.pluginLogs.value
        val failedCount = plugins.count { it.status == PluginStatus.FAILED }
        val successCount = plugins.count { it.status == PluginStatus.SUCCESS }

        val pluginItems =
            plugins.map { plugin ->
                McpRestartLogPluginResultItem(
                    id = plugin.id,
                    displayName = plugin.displayName,
                    shortName = plugin.shortName,
                    status = plugin.status.name.lowercase(),
                    message = plugin.message,
                    serviceName = plugin.serviceName,
                    log = pluginLogs[plugin.id].orEmpty()
                )
            }
        val extraLogs =
            pluginLogs.filterKeys { pluginId ->
                plugins.none { plugin -> plugin.id == pluginId }
            }

        val hasFailures = failedCount > 0
        return ToolResult(
            toolName = tool.name,
            success = !timedOut && !hasFailures,
            result =
                McpRestartWithLogsResultData(
                    timeoutMs = timeoutMs,
                    elapsedMs = elapsedMs,
                    timedOut = timedOut,
                    progress = pluginLoadingState.progress.value.toDouble(),
                    message = pluginLoadingState.message.value,
                    pluginsTotal = pluginLoadingState.pluginsTotal.value,
                    pluginsStarted = pluginLoadingState.pluginsStarted.value,
                    successCount = successCount,
                    failedCount = failedCount,
                    plugins = pluginItems,
                    extraLogs = extraLogs
                ),
            error =
                when {
                    timedOut -> "MCP restart timed out after ${elapsedMs}ms"
                    hasFailures -> "Some MCP plugins failed to start"
                    else -> null
                }
        )
    }

    private suspend fun activeCharacterCardId(
        characterCardManager: CharacterCardManager
    ): String? {
        return characterCardManager.observeActiveCharacterCardId().first()
    }

    private suspend fun requireCharacterCard(
        characterCardManager: CharacterCardManager,
        characterCardId: String
    ): CharacterCard {
        return characterCardManager
            .getAllCharacterCards()
            .firstOrNull { card -> card.id == characterCardId }
            ?: throw IllegalArgumentException("Character card not found: $characterCardId")
    }

    private fun applyCharacterCardUpdates(
        tool: AITool,
        current: CharacterCard,
        requireName: Boolean
    ): Pair<CharacterCard, List<String>> {
        var updated = current
        val changedFields = mutableListOf<String>()

        fun applyText(name: String, transform: (CharacterCard, String) -> CharacterCard) {
            val value = getParameterValue(tool, name) ?: return
            updated = transform(updated, value)
            changedFields.add(name)
        }

        getParameterValue(tool, "name")?.let { rawName ->
            val name = rawName.trim()
            if (name.isBlank()) {
                throw IllegalArgumentException("Character card name cannot be blank")
            }
            updated = updated.copy(name = name)
            changedFields.add("name")
        }
        if (requireName && updated.name.isBlank()) {
            throw IllegalArgumentException("Missing required parameter: name")
        }

        applyText("description") { card, value -> card.copy(description = value) }
        applyText("character_setting") { card, value -> card.copy(characterSetting = value) }
        applyText("opening_statement") { card, value -> card.copy(openingStatement = value) }
        applyText("other_content_chat") { card, value -> card.copy(otherContentChat = value) }
        applyText("other_content_voice") { card, value -> card.copy(otherContentVoice = value) }
        applyText("advanced_custom_prompt") { card, value -> card.copy(advancedCustomPrompt = value) }
        applyText("marks") { card, value -> card.copy(marks = value) }

        getParameterValue(tool, "attached_tag_ids")?.let { raw ->
            updated = updated.copy(
                attachedTagIds = parseStringArrayParameter(raw, "attached_tag_ids")
            )
            changedFields.add("attached_tag_ids")
        }

        getParameterValue(tool, "chat_model_binding_mode")?.let { raw ->
            val mode = parseCharacterCardChatModelBindingMode(raw)
            updated = updated.copy(chatModelBindingMode = mode)
            changedFields.add("chat_model_binding_mode")
        }
        getParameterValue(tool, "chat_model_config_id")?.let { raw ->
            updated = updated.copy(chatModelConfigId = raw.trim().takeIf { it.isNotEmpty() })
            changedFields.add("chat_model_config_id")
        }
        getParameterValue(tool, "chat_model_index")?.let { raw ->
            val index = raw.trim().toIntOrNull()
                ?: throw IllegalArgumentException("Invalid integer parameter: chat_model_index")
            if (index < 0) {
                throw IllegalArgumentException("chat_model_index must be greater than or equal to 0")
            }
            updated = updated.copy(chatModelIndex = index)
            changedFields.add("chat_model_index")
        }
        getParameterValue(tool, "memory_profile_binding_mode")?.let { raw ->
            val mode = parseCharacterCardMemoryProfileBindingMode(raw)
            updated = updated.copy(memoryProfileBindingMode = mode)
            changedFields.add("memory_profile_binding_mode")
        }
        getParameterValue(tool, "memory_profile_id")?.let { raw ->
            updated = updated.copy(memoryProfileId = raw.trim().takeIf { it.isNotEmpty() })
            changedFields.add("memory_profile_id")
        }

        var toolAccessConfig = updated.toolAccessConfig
        getParameterValue(tool, "tool_access_enabled")?.let { raw ->
            val enabled = parseBooleanParameter(raw)
                ?: throw IllegalArgumentException("Invalid boolean parameter: tool_access_enabled")
            toolAccessConfig = toolAccessConfig.copy(enabled = enabled)
            changedFields.add("tool_access_enabled")
        }
        getParameterValue(tool, "allowed_builtin_tools")?.let { raw ->
            toolAccessConfig = toolAccessConfig.copy(
                allowedBuiltinTools = parseStringArrayParameter(raw, "allowed_builtin_tools")
            )
            changedFields.add("allowed_builtin_tools")
        }
        getParameterValue(tool, "allowed_packages")?.let { raw ->
            toolAccessConfig = toolAccessConfig.copy(
                allowedPackages = parseStringArrayParameter(raw, "allowed_packages")
            )
            changedFields.add("allowed_packages")
        }
        getParameterValue(tool, "allowed_skills")?.let { raw ->
            toolAccessConfig = toolAccessConfig.copy(
                allowedSkills = parseStringArrayParameter(raw, "allowed_skills")
            )
            changedFields.add("allowed_skills")
        }
        getParameterValue(tool, "allowed_mcp_servers")?.let { raw ->
            toolAccessConfig = toolAccessConfig.copy(
                allowedMcpServers = parseStringArrayParameter(raw, "allowed_mcp_servers")
            )
            changedFields.add("allowed_mcp_servers")
        }
        if (toolAccessConfig != updated.toolAccessConfig) {
            updated = updated.copy(toolAccessConfig = toolAccessConfig.normalized())
        }

        return updated to changedFields.distinct()
    }

    private fun parseCharacterCardChatModelBindingMode(value: String): String {
        return when (value.trim().uppercase()) {
            CharacterCardChatModelBindingMode.FOLLOW_GLOBAL ->
                CharacterCardChatModelBindingMode.FOLLOW_GLOBAL
            CharacterCardChatModelBindingMode.FIXED_CONFIG ->
                CharacterCardChatModelBindingMode.FIXED_CONFIG
            else -> throw IllegalArgumentException("Invalid chat_model_binding_mode: $value")
        }
    }

    private fun parseCharacterCardMemoryProfileBindingMode(value: String): String {
        return when (value.trim().uppercase()) {
            CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL ->
                CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL
            CharacterCardMemoryProfileBindingMode.FIXED_PROFILE ->
                CharacterCardMemoryProfileBindingMode.FIXED_PROFILE
            else -> throw IllegalArgumentException("Invalid memory_profile_binding_mode: $value")
        }
    }

    private fun parseStringArrayParameter(raw: String, parameterName: String): List<String> {
        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON array parameter: $parameterName")
        }
        return (0 until array.length()).map { index ->
            val value = array.get(index)
            if (value !is String) {
                throw IllegalArgumentException(
                    "Invalid JSON array parameter: $parameterName[$index] must be a string"
                )
            }
            value.trim()
        }.filter { value -> value.isNotEmpty() }.distinct()
    }

    private fun characterCardToResultItem(card: CharacterCard): CharacterCardResultItem {
        val toolAccessConfig = card.toolAccessConfig.normalized()
        return CharacterCardResultItem(
            id = card.id,
            name = card.name,
            description = card.description,
            characterSetting = card.characterSetting,
            openingStatement = card.openingStatement,
            otherContentChat = card.otherContentChat,
            otherContentVoice = card.otherContentVoice,
            attachedTagIds = card.attachedTagIds,
            advancedCustomPrompt = card.advancedCustomPrompt,
            marks = card.marks,
            chatModelBindingMode = card.chatModelBindingMode,
            chatModelConfigId = card.chatModelConfigId,
            chatModelIndex = card.chatModelIndex,
            memoryProfileBindingMode = card.memoryProfileBindingMode,
            memoryProfileId = card.memoryProfileId,
            toolAccessConfig =
                CharacterCardToolAccessConfigResultItem(
                    enabled = toolAccessConfig.enabled,
                    allowedBuiltinTools = toolAccessConfig.allowedBuiltinTools,
                    allowedPackages = toolAccessConfig.allowedPackages,
                    allowedSkills = toolAccessConfig.allowedSkills,
                    allowedMcpServers = toolAccessConfig.allowedMcpServers
                ),
            isDefault = card.isDefault,
            createdAt = card.createdAt,
            updatedAt = card.updatedAt
        )
    }

    private fun getParameterValue(tool: AITool, name: String): String? {
        return tool.parameters.find { it.name == name }?.value
    }

    private fun getOptionalIntParameter(tool: AITool, name: String): Int? {
        val raw = getParameterValue(tool, name) ?: return null
        return raw.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Invalid integer parameter: $name")
    }

    private fun parseFunctionType(value: String): FunctionType? {
        return FunctionType.values().firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }

    private fun parseApiProviderType(value: String): ApiProviderType? {
        return ApiProviderType.values().firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }

    private fun applyModelConfigUpdates(
        tool: AITool,
        current: ModelConfigData,
        includeName: Boolean
    ): Pair<ModelConfigData, List<String>> {
        var updated = current
        val changedFields = mutableListOf<String>()

        fun applyString(name: String, transform: (ModelConfigData, String) -> ModelConfigData) {
            val value = getParameterValue(tool, name) ?: return
            val trimmed = value.trim()
            updated = transform(updated, trimmed)
            changedFields.add(name)
        }

        fun applyInt(name: String, transform: (ModelConfigData, Int) -> ModelConfigData) {
            val raw = getParameterValue(tool, name) ?: return
            val parsed =
                raw.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid integer parameter: $name")
            updated = transform(updated, parsed)
            changedFields.add(name)
        }

        fun applyFloat(name: String, transform: (ModelConfigData, Float) -> ModelConfigData) {
            val raw = getParameterValue(tool, name) ?: return
            val parsed =
                raw.trim().toFloatOrNull()
                    ?: throw IllegalArgumentException("Invalid number parameter: $name")
            updated = transform(updated, parsed)
            changedFields.add(name)
        }

        fun applyBoolean(name: String, transform: (ModelConfigData, Boolean) -> ModelConfigData) {
            val raw = getParameterValue(tool, name) ?: return
            val parsed =
                parseBooleanParameter(raw)
                    ?: throw IllegalArgumentException("Invalid boolean parameter: $name")
            updated = transform(updated, parsed)
            changedFields.add(name)
        }

        if (includeName) {
            applyString("name") { config, value -> config.copy(name = value) }
        }

        applyString("api_key") { config, value -> config.copy(apiKey = value) }
        applyString("api_endpoint") { config, value -> config.copy(apiEndpoint = value) }
        applyString("model_name") { config, value -> config.copy(modelName = value) }
        getParameterValue(tool, "api_provider_type")?.let { raw ->
            val providerTypeId = raw.trim()
            if (providerTypeId.isEmpty()) {
                throw IllegalArgumentException("Invalid api_provider_type: $raw")
            }
            val provider = parseApiProviderType(providerTypeId) ?: ApiProviderType.OTHER
            updated = updated.copy(
                apiProviderType = provider,
                apiProviderTypeId = providerTypeId
            )
            changedFields.add("api_provider_type")
        }

        applyBoolean("max_tokens_enabled") { config, value -> config.copy(maxTokensEnabled = value) }
        applyInt("max_tokens") { config, value -> config.copy(maxTokens = value.coerceAtLeast(1)) }
        applyBoolean("temperature_enabled") { config, value -> config.copy(temperatureEnabled = value) }
        applyFloat("temperature") { config, value -> config.copy(temperature = value) }
        applyBoolean("top_p_enabled") { config, value -> config.copy(topPEnabled = value) }
        applyFloat("top_p") { config, value -> config.copy(topP = value) }
        applyBoolean("top_k_enabled") { config, value -> config.copy(topKEnabled = value) }
        applyInt("top_k") { config, value -> config.copy(topK = value.coerceAtLeast(0)) }
        applyBoolean("presence_penalty_enabled") { config, value ->
            config.copy(presencePenaltyEnabled = value)
        }
        applyFloat("presence_penalty") { config, value -> config.copy(presencePenalty = value) }
        applyBoolean("frequency_penalty_enabled") { config, value ->
            config.copy(frequencyPenaltyEnabled = value)
        }
        applyFloat("frequency_penalty") { config, value -> config.copy(frequencyPenalty = value) }
        applyBoolean("repetition_penalty_enabled") { config, value ->
            config.copy(repetitionPenaltyEnabled = value)
        }
        applyFloat("repetition_penalty") { config, value -> config.copy(repetitionPenalty = value) }
        applyFloat("context_length") { config, value ->
            config.copy(contextLength = value.coerceAtLeast(1f))
        }
        applyFloat("max_context_length") { config, value ->
            config.copy(maxContextLength = value.coerceAtLeast(1f))
        }
        applyBoolean("enable_max_context_mode") { config, value ->
            config.copy(enableMaxContextMode = value)
        }
        applyFloat("summary_token_threshold") { config, value ->
            config.copy(summaryTokenThreshold = value.coerceIn(0f, 1f))
        }
        applyBoolean("enable_summary") { config, value -> config.copy(enableSummary = value) }
        applyBoolean("enable_summary_by_message_count") { config, value ->
            config.copy(enableSummaryByMessageCount = value)
        }
        applyInt("summary_message_count_threshold") { config, value ->
            config.copy(summaryMessageCountThreshold = value.coerceAtLeast(1))
        }
        applyString("custom_parameters") { config, value ->
            val json = value.ifBlank { "[]" }
            try {
                JSONArray(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON array parameter: custom_parameters")
            }
            config.copy(
                customParameters = json,
                hasCustomParameters = json != "[]"
            )
        }
        applyString("custom_headers") { config, value ->
            val json = value.ifBlank { "{}" }
            try {
                JSONObject(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON object parameter: custom_headers")
            }
            config.copy(customHeaders = json)
        }

        applyInt("mnn_forward_type") { config, value -> config.copy(mnnForwardType = value) }
        applyInt("mnn_thread_count") { config, value -> config.copy(mnnThreadCount = value.coerceAtLeast(1)) }
        applyInt("llama_thread_count") { config, value -> config.copy(llamaThreadCount = value.coerceAtLeast(1)) }
        applyInt("llama_context_size") { config, value -> config.copy(llamaContextSize = value.coerceAtLeast(1)) }
        applyInt("llama_batch_size") { config, value -> config.copy(llamaBatchSize = value.coerceAtLeast(1)) }
        applyInt("llama_ubatch_size") { config, value -> config.copy(llamaUBatchSize = value.coerceAtLeast(1)) }
        applyInt("llama_gpu_layers") { config, value -> config.copy(llamaGpuLayers = value.coerceAtLeast(0)) }
        applyBoolean("llama_use_mmap") { config, value -> config.copy(llamaUseMmap = value) }
        applyBoolean("llama_flash_attention") { config, value ->
            config.copy(llamaFlashAttention = value)
        }
        applyBoolean("llama_kv_unified") { config, value -> config.copy(llamaKvUnified = value) }
        applyBoolean("llama_offload_kqv") { config, value ->
            config.copy(llamaOffloadKqv = value)
        }
        applyInt("request_limit_per_minute") { config, value ->
            config.copy(requestLimitPerMinute = value.coerceAtLeast(0))
        }
        applyInt("max_concurrent_requests") { config, value ->
            config.copy(maxConcurrentRequests = value.coerceAtLeast(0))
        }

        applyBoolean("enable_direct_image_processing") { config, value ->
            config.copy(enableDirectImageProcessing = value)
        }
        applyBoolean("enable_direct_audio_processing") { config, value ->
            config.copy(enableDirectAudioProcessing = value)
        }
        applyBoolean("enable_direct_video_processing") { config, value ->
            config.copy(enableDirectVideoProcessing = value)
        }
        applyBoolean("enable_google_search") { config, value -> config.copy(enableGoogleSearch = value) }
        applyBoolean("enable_claude_1h_prompt_cache") { config, value ->
            config.copy(enableClaude1hPromptCache = value)
        }
        applyBoolean("enable_tool_call") { config, value -> config.copy(enableToolCall = value) }

        if (updated.llamaGpuLayers <= 0 && updated.llamaOffloadKqv) {
            updated = updated.copy(llamaOffloadKqv = false)
        }

        return updated to changedFields.distinct()
    }

    private fun modelConfigToResultItem(config: ModelConfigData): ModelConfigResultItem {
        return ModelConfigResultItem(
            id = config.id,
            name = config.name,
            apiProviderType = config.apiProviderTypeId,
            apiProviderTypeId = config.apiProviderTypeId,
            apiEndpoint = config.apiEndpoint,
            modelName = config.modelName,
            modelList = getModelList(config.modelName),
            apiKeySet = config.apiKey.isNotBlank(),
            apiKeyPreview = maskSecret(config.apiKey),
            maxTokensEnabled = config.maxTokensEnabled,
            maxTokens = config.maxTokens,
            temperatureEnabled = config.temperatureEnabled,
            temperature = config.temperature,
            topPEnabled = config.topPEnabled,
            topP = config.topP,
            topKEnabled = config.topKEnabled,
            topK = config.topK,
            presencePenaltyEnabled = config.presencePenaltyEnabled,
            presencePenalty = config.presencePenalty,
            frequencyPenaltyEnabled = config.frequencyPenaltyEnabled,
            frequencyPenalty = config.frequencyPenalty,
            repetitionPenaltyEnabled = config.repetitionPenaltyEnabled,
            repetitionPenalty = config.repetitionPenalty,
            hasCustomParameters = config.hasCustomParameters,
            customParameters = config.customParameters,
            hasCustomHeaders = config.customHeaders.trim().let { it.isNotEmpty() && it != "{}" },
            customHeaders = config.customHeaders,
            contextLength = config.contextLength,
            maxContextLength = config.maxContextLength,
            enableMaxContextMode = config.enableMaxContextMode,
            summaryTokenThreshold = config.summaryTokenThreshold,
            enableSummary = config.enableSummary,
            enableSummaryByMessageCount = config.enableSummaryByMessageCount,
            summaryMessageCountThreshold = config.summaryMessageCountThreshold,
            mnnForwardType = config.mnnForwardType,
            mnnThreadCount = config.mnnThreadCount,
            llamaThreadCount = config.llamaThreadCount,
            llamaContextSize = config.llamaContextSize,
            llamaBatchSize = config.llamaBatchSize,
            llamaUBatchSize = config.llamaUBatchSize,
            llamaGpuLayers = config.llamaGpuLayers,
            llamaUseMmap = config.llamaUseMmap,
            llamaFlashAttention = config.llamaFlashAttention,
            llamaKvUnified = config.llamaKvUnified,
            llamaOffloadKqv = config.llamaOffloadKqv,
            enableDirectImageProcessing = config.enableDirectImageProcessing,
            enableDirectAudioProcessing = config.enableDirectAudioProcessing,
            enableDirectVideoProcessing = config.enableDirectVideoProcessing,
            enableGoogleSearch = config.enableGoogleSearch,
            enableClaude1hPromptCache = config.enableClaude1hPromptCache,
            enableToolCall = config.enableToolCall,
            requestLimitPerMinute = config.requestLimitPerMinute,
            maxConcurrentRequests = config.maxConcurrentRequests,
            useMultipleApiKeys = config.useMultipleApiKeys,
            apiKeyPoolCount = config.apiKeyPool.size
        )
    }

    private fun maskSecret(value: String): String {
        if (value.isBlank()) return ""
        return when {
            value.length <= 4 -> "*".repeat(value.length)
            else -> "${value.take(3)}***${value.takeLast(2)}"
        }
    }

    private fun buildTtsPlaybackTestResult(
        ttsServiceTypeName: String,
        providerClass: String,
        initialized: Boolean,
        playbackTriggered: Boolean,
        interrupt: Boolean,
        textLength: Int,
        speechRate: Float,
        pitch: Float,
        error: Throwable? = null,
        errorMessageOverride: String? = null
    ): SpeechServicesTtsPlaybackTestResultData {
        val ttsError = error as? TtsException
        return SpeechServicesTtsPlaybackTestResultData(
            ttsServiceType = ttsServiceTypeName,
            providerClass = providerClass,
            initialized = initialized,
            playbackTriggered = playbackTriggered,
            interrupt = interrupt,
            textLength = textLength,
            speechRate = speechRate,
            pitch = pitch,
            errorType = error?.javaClass?.simpleName,
            errorMessage = errorMessageOverride ?: error?.let { formatTtsPlaybackError(it) },
            httpStatusCode = ttsError?.httpStatusCode,
            errorBody = ttsError?.errorBody?.takeIf { it.isNotBlank() },
            causeMessage = error?.cause?.message?.takeIf { it.isNotBlank() }
        )
    }

    private fun formatTtsPlaybackError(error: Throwable): String {
        return when (error) {
            is TtsException -> {
                val code = error.httpStatusCode
                val body = error.errorBody?.takeIf { it.isNotBlank() }
                when {
                    code != null && body != null -> "TTS service error (HTTP $code): $body"
                    code != null -> "TTS service error, status code: $code"
                    body != null -> "TTS service error: $body"
                    !error.message.isNullOrBlank() -> "TTS service error: ${error.message}"
                    !error.cause?.message.isNullOrBlank() -> "TTS service error: ${error.cause?.message}"
                    else -> "TTS service unknown error"
                }
            }

            is UnknownHostException ->
                "Network error: Unable to reach host, please check network connection and DNS settings."

            is SocketTimeoutException ->
                "Network timeout: Server response timeout, please check network status."

            is ConnectException ->
                "Network error: Unable to connect to server, please check server address and port."

            is ProtocolException ->
                "Network protocol error: ${error.message ?: error.javaClass.simpleName}"

            is IOException ->
                "Network IO error: ${error.message ?: "Please check device network connection."}"

            else -> {
                val directMessage = error.message?.takeIf { it.isNotBlank() }
                val causeMessage = error.cause?.message?.takeIf { it.isNotBlank() }
                listOfNotNull(
                    directMessage?.let { "${error.javaClass.simpleName}: $it" }
                        ?: error.javaClass.simpleName,
                    causeMessage?.let { "Cause: $it" }
                ).joinToString("\n")
            }
        }
    }

    private fun parseBooleanParameter(value: String?): Boolean? {
        return when (value?.trim()?.lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
    }

    private fun parseJsonObjectToMap(raw: String): Map<String, Any?> {
        val payload = Json.parseToJsonElement(raw)
        require(payload is JsonObject) { "params_json must be a JSON object" }
        return payload.entries.associate { (key, value) ->
            key to jsonElementToValue(value)
        }
    }

    private fun parseEnvFile(envFilePath: String?): Map<String, String> {
        if (envFilePath.isNullOrBlank()) {
            return emptyMap()
        }

        val envFile = File(envFilePath)
        if (!envFile.exists() || !envFile.isFile) {
            throw IllegalArgumentException("env_file_path must point to an existing file: $envFilePath")
        }

        return buildMap {
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

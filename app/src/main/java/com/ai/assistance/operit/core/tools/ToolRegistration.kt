package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.tools.climode.CliToolModeSupport
import com.ai.assistance.operit.core.tools.climode.ToolExposureMode
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.CharacterCardToolAccessResolver
import com.ai.assistance.operit.data.preferences.ResolvedCharacterCardToolAccess
import com.ai.assistance.operit.integrations.tasker.triggerAIAgentAction
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * This file contains all tool registrations centralized for easier maintenance and integration It
 * extracts the registerTools logic from AIToolHandler into a dedicated file
 */

/**
 * Register all available tools with the AIToolHandler
 * @param handler The AIToolHandler instance to register tools with
 * @param context Application context for tools that need it
 */
fun registerAllTools(handler: AIToolHandler, context: Context) {

    // Helper function to wrap UI tool execution with visibility changes
    suspend fun executeUiToolWithVisibility(
        tool: AITool,
        showStatusIndicator: Boolean = true,
        delayMs: Long = 50,
        action: suspend (AITool) -> ToolResult
    ): ToolResult {
        val floatingService = FloatingChatService.getInstance()
        return try {
            floatingService?.setFloatingWindowVisible(false)
            if (showStatusIndicator) {
                floatingService?.setStatusIndicatorVisible(true)
            } else {
                floatingService?.setStatusIndicatorVisible(false)
            }
            delay(delayMs)
            action(tool)
        } finally {
            floatingService?.setFloatingWindowVisible(true)
            floatingService?.setStatusIndicatorVisible(false)
        }
    }

    fun s(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    fun formatEnvInfo(environment: String?): String {
        return if (!environment.isNullOrBlank() && environment != "android") {
            s(R.string.toolreg_env_info, environment)
        } else {
            ""
        }
    }

    fun formatEnvArrowInfo(sourceEnv: String, destEnv: String): String {
        return if (sourceEnv != "android" || destEnv != "android") {
            s(R.string.toolreg_env_arrow_info, sourceEnv, destEnv)
        } else {
            ""
        }
    }

    val packageContextParamNames = setOf(
        "__operit_package_caller_name",
        "__operit_package_chat_id",
        "__operit_package_caller_card_id"
    )

    class ParsedProxyInvocation(
        val targetToolName: String,
        val forwardedParameters: MutableList<ToolParameter>
    )

    fun isEnglishLanguage(): Boolean {
        return LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
    }

    fun buildToolErrorResult(tool: AITool, error: String): ToolResult {
        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = error
        )
    }

    fun parseProxyInvocation(
        tool: AITool,
        requireQualifiedTarget: Boolean
    ): Pair<ParsedProxyInvocation?, ToolResult?> {
        val allowedParamNames = setOf("tool_name", "params") + packageContextParamNames
        val unknownParamNames = tool.parameters.map { it.name }.filter { it !in allowedParamNames }
        if (unknownParamNames.isNotEmpty()) {
            return null to buildToolErrorResult(
                tool,
                "Unexpected parameters: ${unknownParamNames.joinToString(", ")}. Only tool_name, params, and supported system context parameters are allowed"
            )
        }

        val toolNameParams = tool.parameters.filter { it.name == "tool_name" }
        if (toolNameParams.size != 1) {
            return null to buildToolErrorResult(
                tool,
                "Exactly one tool_name parameter is required"
            )
        }
        val targetToolName = toolNameParams.first().value.trim()
        if (targetToolName.isBlank()) {
            return null to buildToolErrorResult(
                tool,
                "Missing required parameter: tool_name"
            )
        }

        if (requireQualifiedTarget && !targetToolName.contains(':')) {
            return null to buildToolErrorResult(
                tool,
                "tool_name must use packageName:toolName format"
            )
        }

        val paramsParams = tool.parameters.filter { it.name == "params" }
        if (paramsParams.size != 1) {
            return null to buildToolErrorResult(
                tool,
                "Exactly one params parameter is required"
            )
        }
        val paramsRaw = paramsParams.first().value.trim()
        if (paramsRaw.isBlank()) {
            return null to buildToolErrorResult(tool, "params must be a JSON object")
        }

        val paramsObject = try {
            JSONObject(paramsRaw)
        } catch (_: Exception) {
            return null to buildToolErrorResult(tool, "params must be a valid JSON object")
        }

        val forwardedParameters = mutableListOf<ToolParameter>()
        val keys = paramsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = paramsObject.opt(key)
            val valueString = when (value) {
                null, JSONObject.NULL -> "null"
                is String -> value
                else -> value.toString()
            }
            forwardedParameters.add(ToolParameter(name = key, value = valueString))
        }

        packageContextParamNames.forEach { paramName ->
            val value = tool.parameters
                .firstOrNull { it.name == paramName }
                ?.value
                ?.trim()
            if (!value.isNullOrBlank() && forwardedParameters.none { it.name == paramName }) {
                forwardedParameters.add(ToolParameter(name = paramName, value = value))
            }
        }

        return ParsedProxyInvocation(
            targetToolName = targetToolName,
            forwardedParameters = forwardedParameters
        ) to null
    }

    fun resolveCurrentRoleCardToolAccess(): ResolvedCharacterCardToolAccess {
        val runtimeContext = ToolExecutionManager.currentToolRuntimeContext()
        return runBlocking {
            CharacterCardToolAccessResolver
                .getInstance(context)
                .resolve(
                    roleCardId = runtimeContext?.callerCardId,
                    packageManager = handler.getOrCreatePackageManager()
                )
        }
    }

    fun isProxyTargetAllowedForRoleCard(
        targetToolName: String,
        forwardedParameters: List<ToolParameter>,
        roleCardToolAccess: ResolvedCharacterCardToolAccess
    ): Boolean {
        val usePackageSourceName =
            if (targetToolName == "use_package") {
                forwardedParameters
                    .firstOrNull { it.name == "package_name" }
                    ?.value
                    ?.trim()
                    .orEmpty()
                    .ifBlank { null }
            } else {
                null
            }

        return CliToolModeSupport.isToolNameAllowedForRoleCard(
            toolName = targetToolName,
            usePackageSourceName = usePackageSourceName,
            roleCardToolAccess = roleCardToolAccess
        )
    }

    fun executeProxyTargetWithPermissionCheck(
        targetToolName: String,
        forwardedParameters: List<ToolParameter>,
        useEnglish: Boolean
    ): ToolResult {
        val proxiedTool = AITool(
            name = targetToolName,
            parameters = forwardedParameters
        )
        val executor = handler.getToolExecutorOrActivate(targetToolName)
        if (executor == null) {
            return ToolResult(
                toolName = targetToolName,
                success = false,
                result = StringResultData(""),
                error = CliToolModeSupport.buildProxyTargetUnavailableMessage(targetToolName, useEnglish)
            )
        }

        val hasPermission = runBlocking {
            handler.getToolPermissionSystem().checkToolPermission(proxiedTool)
        }
        if (!hasPermission) {
            val errorMessage = "User cancelled the tool execution."
            handler.notifyToolPermissionChecked(
                proxiedTool,
                granted = false,
                reason = errorMessage
            )
            return ToolResult(
                toolName = targetToolName,
                success = false,
                result = StringResultData(""),
                error = errorMessage
            )
        }

        handler.notifyToolPermissionChecked(proxiedTool, granted = true)
        val proxiedResult = handler.executeTool(proxiedTool)
        return ToolResult(
            toolName = targetToolName,
            success = proxiedResult.success,
            result = proxiedResult.result,
            error = proxiedResult.error
        )
    }

    // 不在提示词加入的工具
    handler.registerTool(
            name = "execute_shell",
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                s(R.string.toolreg_execute_shell_desc, command)
            },
            executor = { tool ->
                val adbTool = ToolGetter.getShellToolExecutor(context)
                adbTool.invoke(tool)
            }
    )

    handler.registerTool(
            name = "close_all_virtual_displays",
            descriptionGenerator = { _ -> s(R.string.toolreg_close_all_virtual_displays_desc) },
            executor = { tool ->
                try {
                    VirtualDisplayOverlay.hideAll()
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("OK")
                    )
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = e.message
                    )
                }
            }
    )

    // 终端命令执行工具 - 一次性收集输出
    handler.registerTool(
            name = "create_terminal_session",
            descriptionGenerator = { tool ->
                val sessionName = tool.parameters.find { it.name == "session_name" }?.value
                val displayName = sessionName ?: s(R.string.toolreg_unnamed)
                s(R.string.toolreg_create_terminal_session_desc, displayName)
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.createOrGetSession(tool)
            }
    )

    handler.registerTool(
            name = "execute_in_terminal_session",
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                s(R.string.toolreg_execute_in_terminal_session_desc, sessionId ?: "", command)
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.executeCommandInSession(tool)
            }
    )

    handler.registerTool(
            name = "execute_in_terminal_session_streaming",
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                s(R.string.toolreg_execute_in_terminal_session_desc, sessionId ?: "", command)
            },
            executor =
                    object : ToolExecutor {
                        override fun invoke(tool: AITool): ToolResult {
                            val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                            return terminalTool.executeCommandInSession(tool)
                        }

                        override fun invokeAndStream(
                                tool: AITool
                        ): kotlinx.coroutines.flow.Flow<ToolResult> {
                            val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                            return terminalTool.executeCommandInSessionStream(tool)
                        }
                    }
    )

    handler.registerTool(
            name = "execute_hidden_terminal_command",
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val executorKey =
                        tool.parameters.find { it.name == "executor_key" }?.value ?: "default"
                s(R.string.toolreg_execute_hidden_terminal_command_desc, executorKey, command)
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.executeHiddenCommand(tool)
            }
    )

    handler.registerTool(
            name = "close_terminal_session",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                s(R.string.toolreg_close_terminal_session_desc, sessionId ?: "")
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.closeSession(tool)
            }
    )

    handler.registerTool(
            name = "input_in_terminal_session",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                val control = tool.parameters.find { it.name == "control" }?.value ?: "-"
                s(R.string.toolreg_input_in_terminal_session_desc, sessionId ?: "", control)
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.inputInSession(tool)
            }
    )

    handler.registerTool(
            name = "get_terminal_session_screen",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value ?: ""
                s(R.string.toolreg_get_terminal_session_screen_desc, sessionId)
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.getSessionScreen(tool)
            }
    )

    // 音乐播放工具
    val musicPlaybackTools = ToolGetter.getMusicPlaybackTools(context)

    handler.registerTool(
            name = "music_play",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                "Play music: $source"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.play(tool) }
            }
    )

    handler.registerTool(
            name = "music_play_queue",
            descriptionGenerator = { tool ->
                val items = tool.parameters.find { it.name == "items" }?.value ?: ""
                "Play music queue: $items"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.playQueue(tool) }
            }
    )

    handler.registerTool(
            name = "music_pause",
            descriptionGenerator = { _ -> "Pause music playback" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.pause(tool) }
            }
    )

    handler.registerTool(
            name = "music_resume",
            descriptionGenerator = { _ -> "Resume music playback" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.resume(tool) }
            }
    )

    handler.registerTool(
            name = "music_stop",
            descriptionGenerator = { _ -> "Stop music playback" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.stop(tool) }
            }
    )

    handler.registerTool(
            name = "music_seek",
            descriptionGenerator = { tool ->
                val positionMs = tool.parameters.find { it.name == "position_ms" }?.value ?: ""
                "Seek music playback to ${positionMs}ms"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.seek(tool) }
            }
    )

    handler.registerTool(
            name = "music_set_volume",
            descriptionGenerator = { tool ->
                val volume = tool.parameters.find { it.name == "volume" }?.value ?: ""
                "Set music playback volume to $volume"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.setVolume(tool) }
            }
    )

    handler.registerTool(
            name = "music_status",
            descriptionGenerator = { _ -> "Get music playback status" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { musicPlaybackTools.status(tool) }
            }
    )

    handler.registerTool(
            name = "read_environment_variable",
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                "Read environment variable: $key"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                softwareSettingsTools.readEnvironmentVariable(tool)
            }
    )

    handler.registerTool(
            name = "write_environment_variable",
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                val value = tool.parameters.find { it.name == "value" }?.value
                val mode = if (value.isNullOrBlank()) "clear" else "set"
                "Write environment variable: $key ($mode)"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                softwareSettingsTools.writeEnvironmentVariable(tool)
            }
    )

    handler.registerTool(
            name = "list_sandbox_packages",
            descriptionGenerator = { _ ->
                "List sandbox packages and their enabled states"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                val packageManager = handler.getOrCreatePackageManager()
                softwareSettingsTools.listSandboxPackages(tool, packageManager)
            }
    )

    handler.registerTool(
            name = "set_sandbox_package_enabled",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                val enabled = tool.parameters.find { it.name == "enabled" }?.value ?: ""
                "Set sandbox package enabled state: $packageName -> $enabled"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                val packageManager = handler.getOrCreatePackageManager()
                softwareSettingsTools.setSandboxPackageEnabled(tool, packageManager)
            }
    )

    handler.registerTool(
            name = "execute_sandbox_script_direct",
            descriptionGenerator = { tool ->
                val sourcePath = tool.parameters.find { it.name == "source_path" }?.value?.trim().orEmpty()
                val hasInlineCode =
                        tool.parameters.find { it.name == "source_code" }?.value?.isNotBlank() == true
                val label =
                        tool.parameters.find { it.name == "script_label" }?.value?.trim().orEmpty()
                val target =
                        when {
                            sourcePath.isNotBlank() -> sourcePath
                            label.isNotBlank() -> label
                            hasInlineCode -> "inline code"
                            else -> "sandbox script"
                        }
                "Execute sandbox script directly: $target"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                softwareSettingsTools.executeSandboxScriptDirect(tool)
            }
    )

    handler.registerTool(
            name = "restart_mcp_with_logs",
            descriptionGenerator = { tool ->
                val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value ?: "120000"
                "Restart MCP startup and return per-plugin logs (timeout=${timeoutMs}ms)"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.restartMcpWithLogs(tool) }
            }
    )

    handler.registerTool(
            name = "get_speech_services_config",
            descriptionGenerator = { _ ->
                "Get current TTS/STT speech services configuration"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.getSpeechServicesConfig(tool) }
            }
    )

    handler.registerTool(
            name = "set_speech_services_config",
            descriptionGenerator = { _ ->
                "Update TTS/STT speech services configuration"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.setSpeechServicesConfig(tool) }
            }
    )

    handler.registerTool(
            name = "test_tts_playback",
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value.orEmpty()
                val preview = text.take(24).replace('\n', ' ')
                "Play one TTS test utterance using current speech settings: $preview"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.testTtsPlayback(tool) }
            }
    )

    handler.registerTool(
            name = "list_model_configs",
            descriptionGenerator = { _ ->
                "List all model configs and current function-to-config mappings"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.listModelConfigs(tool) }
            }
    )

    handler.registerTool(
            name = "create_model_config",
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: "New Model Config"
                "Create model config: $name"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.createModelConfig(tool) }
            }
    )

    handler.registerTool(
            name = "update_model_config",
            descriptionGenerator = { tool ->
                val configId = tool.parameters.find { it.name == "config_id" }?.value ?: ""
                "Update model config: $configId"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.updateModelConfig(tool) }
            }
    )

    handler.registerTool(
            name = "delete_model_config",
            descriptionGenerator = { tool ->
                val configId = tool.parameters.find { it.name == "config_id" }?.value ?: ""
                "Delete model config: $configId"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.deleteModelConfig(tool) }
            }
    )

    handler.registerTool(
            name = "list_function_model_configs",
            descriptionGenerator = { _ ->
                "List function model bindings only (function -> config_id + model_index)"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.listFunctionModelConfigs(tool) }
            }
    )

    handler.registerTool(
            name = "get_function_model_config",
            descriptionGenerator = { tool ->
                val functionType = tool.parameters.find { it.name == "function_type" }?.value ?: ""
                "Get function model config: $functionType"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.getFunctionModelConfig(tool) }
            }
    )

    handler.registerTool(
            name = "set_function_model_config",
            descriptionGenerator = { tool ->
                val functionType = tool.parameters.find { it.name == "function_type" }?.value ?: ""
                val configId = tool.parameters.find { it.name == "config_id" }?.value ?: ""
                val modelIndex = tool.parameters.find { it.name == "model_index" }?.value ?: "0"
                "Set function model config: $functionType -> $configId (model_index=$modelIndex)"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.setFunctionModelConfig(tool) }
            }
    )

    handler.registerTool(
            name = "test_model_config_connection",
            descriptionGenerator = { tool ->
                val configId = tool.parameters.find { it.name == "config_id" }?.value ?: ""
                val modelIndex = tool.parameters.find { it.name == "model_index" }?.value ?: "0"
                "Test model config connection: $configId (model_index=$modelIndex)"
            },
            executor = { tool ->
                val softwareSettingsTools = ToolGetter.getSoftwareSettingsModifyTools(context)
                runBlocking(Dispatchers.IO) { softwareSettingsTools.testModelConfigConnection(tool) }
            }
    )

    // 注册记忆库查询工具
    handler.registerTool(
            name = "query_memory",
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                s(R.string.toolreg_query_memory_desc, query)
            },
            executor = { tool ->
                val problemLibraryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                problemLibraryTool.invoke(tool)
            }
    )
    
    // 注册根据标题获取单个记忆工具
    handler.registerTool(
            name = "get_memory_by_title",
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                s(R.string.toolreg_get_memory_by_title_desc, title)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // Register the document-level profile update. The normal tool confirmation UI is the safety
    // boundary; unlike the released analyzer, no background process may rewrite user.md.
    handler.registerTool(
            name = "update_user_profile",
            descriptionGenerator = { tool ->
                s(
                        R.string.toolreg_update_user_profile_desc,
                        "user.md"
                )
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // Compatibility for released packages and persisted calls. It is intentionally absent from
    // the current system-tool prompt, so new conversations use only update_user_profile.
    handler.registerTool(
            name = "update_user_preferences",
            descriptionGenerator = {
                s(R.string.toolreg_update_user_profile_desc, "user.md")
            },
            executor = { tool ->
                ToolGetter.getMemoryQueryToolExecutor(context).invoke(tool)
            }
    )

    // 注册创建记忆工具
    handler.registerTool(
            name = "create_memory",
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                s(R.string.toolreg_create_memory_desc, title)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册更新记忆工具
    handler.registerTool(
            name = "update_memory",
            descriptionGenerator = { tool ->
                val oldTitle = tool.parameters.find { it.name == "old_title" }?.value ?: ""
                val newTitle = tool.parameters.find { it.name == "new_title" }?.value ?: oldTitle
                s(R.string.toolreg_update_memory_desc, oldTitle, newTitle)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册删除记忆工具
    handler.registerTool(
            name = "delete_memory",
            descriptionGenerator = { tool ->
                val title = tool.parameters.find { it.name == "title" }?.value ?: ""
                s(R.string.toolreg_delete_memory_desc, title)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册批量移动记忆工具
    handler.registerTool(
            name = "move_memory",
            descriptionGenerator = { tool ->
                val sourceFolder = tool.parameters.find { it.name == "source_folder_path" }?.value
                val targetFolder = tool.parameters.find { it.name == "target_folder_path" }?.value ?: ""
                val titles = tool.parameters.find { it.name == "titles" }?.value
                when {
                    !titles.isNullOrBlank() && !sourceFolder.isNullOrBlank() ->
                        "Move selected memories from '$sourceFolder' to '$targetFolder'"
                    !titles.isNullOrBlank() ->
                        "Move selected memories to '$targetFolder'"
                    !sourceFolder.isNullOrBlank() ->
                        "Move memories from '$sourceFolder' to '$targetFolder'"
                    else -> "Move memories to '$targetFolder'"
                }
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册链接记忆工具
    handler.registerTool(
            name = "link_memories",
            descriptionGenerator = { tool ->
                val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value ?: ""
                val targetTitle = tool.parameters.find { it.name == "target_title" }?.value ?: ""
                val linkType = tool.parameters.find { it.name == "link_type" }?.value ?: "related"
                s(R.string.toolreg_link_memories_desc, sourceTitle, targetTitle, linkType)
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册查询记忆链接工具
    handler.registerTool(
            name = "query_memory_links",
            descriptionGenerator = { tool ->
                val linkId = tool.parameters.find { it.name == "link_id" }?.value
                val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value
                val targetTitle = tool.parameters.find { it.name == "target_title" }?.value
                val linkType = tool.parameters.find { it.name == "link_type" }?.value
                val locator = when {
                    !linkId.isNullOrBlank() -> "link_id=$linkId"
                    !sourceTitle.isNullOrBlank() || !targetTitle.isNullOrBlank() -> "${sourceTitle ?: "*"} -> ${targetTitle ?: "*"}"
                    else -> "all links"
                }
                "Query memory links: $locator${if (!linkType.isNullOrBlank()) ", type=$linkType" else ""}"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册更新记忆链接工具
    handler.registerTool(
            name = "update_memory_link",
            descriptionGenerator = { tool ->
                val linkId = tool.parameters.find { it.name == "link_id" }?.value
                val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value
                val targetTitle = tool.parameters.find { it.name == "target_title" }?.value
                val locator = when {
                    !linkId.isNullOrBlank() -> "link_id=$linkId"
                    !sourceTitle.isNullOrBlank() && !targetTitle.isNullOrBlank() -> "$sourceTitle -> $targetTitle"
                    else -> "unknown link"
                }
                "Update memory link: $locator"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 注册删除记忆链接工具
    handler.registerTool(
            name = "delete_memory_link",
            descriptionGenerator = { tool ->
                val linkId = tool.parameters.find { it.name == "link_id" }?.value
                val sourceTitle = tool.parameters.find { it.name == "source_title" }?.value
                val targetTitle = tool.parameters.find { it.name == "target_title" }?.value
                val locator = when {
                    !linkId.isNullOrBlank() -> "link_id=$linkId"
                    !sourceTitle.isNullOrBlank() && !targetTitle.isNullOrBlank() -> "$sourceTitle -> $targetTitle"
                    else -> "unknown link"
                }
                "Delete memory link: $locator"
            },
            executor = { tool ->
                val memoryTool = ToolGetter.getMemoryQueryToolExecutor(context)
                memoryTool.invoke(tool)
            }
    )

    // 系统操作工具
    handler.registerTool(
            name = "use_package",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_use_package_desc, packageName)
            },
            executor = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                handler
                    .getOrCreatePackageManager()
                    .executeUsePackageTool(tool.name, packageName)
            }
    )

    handler.registerTool(
            name = CliToolModeSupport.SEARCH_TOOL_NAME,
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                "Search hidden tool catalog: $query"
            },
            executor = { tool ->
                val useEnglish = isEnglishLanguage()
                val runtimeContext = ToolExecutionManager.currentToolRuntimeContext()
                if (runtimeContext?.toolExposureMode != ToolExposureMode.CLI) {
                    return@registerTool ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = CliToolModeSupport.buildCliModeUnavailableMessage(useEnglish)
                    )
                }

                val query = tool.parameters
                    .firstOrNull { it.name == "query" }
                    ?.value
                    ?.trim()
                    .orEmpty()
                if (query.isBlank()) {
                    return@registerTool buildToolErrorResult(tool, "Missing required parameter: query")
                }

                val limit = tool.parameters
                    .firstOrNull { it.name == "limit" }
                    ?.value
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.toIntOrNull()
                    ?: CliToolModeSupport.defaultSearchLimit()

                val roleCardToolAccess = resolveCurrentRoleCardToolAccess()
                val hiddenCatalog = runBlocking {
                    CliToolModeSupport.buildHiddenToolCatalog(
                        context = context,
                        packageManager = handler.getOrCreatePackageManager(),
                        roleCardToolAccess = roleCardToolAccess,
                        useEnglish = useEnglish
                    )
                }
                val results = CliToolModeSupport.searchHiddenToolCatalog(
                    catalog = hiddenCatalog,
                    query = query,
                    limit = limit
                )
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(
                        CliToolModeSupport.formatSearchResults(query, results, useEnglish)
                    )
                )
            }
    )

    handler.registerTool(
            name = CliToolModeSupport.PROXY_TOOL_NAME,
            descriptionGenerator = { tool ->
                val targetToolName = tool.parameters.find { it.name == "tool_name" }?.value ?: ""
                "Proxy call to hidden tool: $targetToolName"
            },
            executor = { tool ->
                val useEnglish = isEnglishLanguage()
                val runtimeContext = ToolExecutionManager.currentToolRuntimeContext()
                if (runtimeContext?.toolExposureMode != ToolExposureMode.CLI) {
                    return@registerTool ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = CliToolModeSupport.buildCliModeUnavailableMessage(useEnglish)
                    )
                }

                val (parsedInvocation, parseError) = parseProxyInvocation(
                    tool = tool,
                    requireQualifiedTarget = false
                )
                if (parseError != null) {
                    return@registerTool parseError
                }
                val resolvedInvocation = parsedInvocation ?: return@registerTool buildToolErrorResult(
                    tool,
                    "Missing required parameter: tool_name"
                )

                if (CliToolModeSupport.isReservedProxyTarget(resolvedInvocation.targetToolName)) {
                    return@registerTool ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = CliToolModeSupport.buildReservedProxyTargetMessage(
                            resolvedInvocation.targetToolName,
                            useEnglish
                        )
                    )
                }

                val roleCardToolAccess = resolveCurrentRoleCardToolAccess()
                if (!isProxyTargetAllowedForRoleCard(
                        targetToolName = resolvedInvocation.targetToolName,
                        forwardedParameters = resolvedInvocation.forwardedParameters,
                        roleCardToolAccess = roleCardToolAccess
                    )
                ) {
                    return@registerTool ToolResult(
                        toolName = resolvedInvocation.targetToolName,
                        success = false,
                        result = StringResultData(""),
                        error = CliToolModeSupport.buildRoleAccessDeniedMessage(useEnglish)
                    )
                }

                executeProxyTargetWithPermissionCheck(
                    targetToolName = resolvedInvocation.targetToolName,
                    forwardedParameters = resolvedInvocation.forwardedParameters,
                    useEnglish = useEnglish
                )
            }
    )

    handler.registerTool(
            name = "package_proxy",
            descriptionGenerator = { tool ->
                val targetToolName = tool.parameters.find { it.name == "tool_name" }?.value ?: ""
                "Proxy call to package tool: $targetToolName"
            },
            executor = { tool ->
                val (parsedInvocation, parseError) = parseProxyInvocation(
                    tool = tool,
                    requireQualifiedTarget = true
                )
                if (parseError != null) {
                    return@registerTool parseError
                }
                val resolvedInvocation = parsedInvocation ?: return@registerTool buildToolErrorResult(
                    tool,
                    "Missing required parameter: tool_name"
                )
                if (resolvedInvocation.targetToolName == CliToolModeSupport.PACKAGE_PROXY_TOOL_NAME) {
                    return@registerTool buildToolErrorResult(tool, "tool_name cannot be package_proxy")
                }

                val proxiedTool = AITool(
                    name = resolvedInvocation.targetToolName,
                    parameters = resolvedInvocation.forwardedParameters
                )
                val proxiedResult = handler.executeTool(proxiedTool)
                ToolResult(
                    toolName = resolvedInvocation.targetToolName,
                    success = proxiedResult.success,
                    result = proxiedResult.result,
                    error = proxiedResult.error
                )
            }
    )

    // ADB命令执行工具

    // 计算器工具
    handler.registerTool(
            name = "calculate",
            descriptionGenerator = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                s(R.string.toolreg_calculate_desc, expression)
            },
            executor = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                try {
                    val result = ToolGetter.getCalculator().evalExpression(expression)
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("Calculation result: $result")
                    )
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Calculation error: ${e.message}"
                    )
                }
            }
    )

    // Web搜索工具
    handler.registerTool(
            name = "visit_web",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value
                val visitKey = tool.parameters.find { it.name == "visit_key" }?.value
                val linkNumber = tool.parameters.find { it.name == "link_number" }?.value

                when {
                    !visitKey.isNullOrBlank() && !linkNumber.isNullOrBlank() ->
                            s(
                                    R.string.toolreg_visit_web_search_link_desc,
                                    linkNumber,
                                    visitKey.take(8)
                            )
                    !url.isNullOrBlank() -> s(R.string.toolreg_visit_web_url_desc, url)
                    else -> s(R.string.toolreg_visit_web_desc)
                }
            },
            executor = { tool ->
                val webVisitTool = ToolGetter.getWebVisitTool(context)
                webVisitTool.invoke(tool)
            }
    )

    handler.registerTool(
            name = "browser_click",
            descriptionGenerator = { tool ->
                val ref = tool.parameters.find { it.name == "ref" }?.value ?: ""
                val selector = tool.parameters.find { it.name == "selector" }?.value ?: ""
                when {
                    ref.isNotBlank() -> "Click browser element ref $ref from browser_snapshot"
                    selector.isNotBlank() -> "Click browser element by selector $selector"
                    else -> "Click browser element (missing ref/selector)"
                }
            },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_close",
            descriptionGenerator = { "Close the current browser tab" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_close_all",
            descriptionGenerator = { "Close all browser tabs" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_console_messages",
            descriptionGenerator = { "Read browser console messages" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_drag",
            descriptionGenerator = { "Drag between browser element refs" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_evaluate",
            descriptionGenerator = { "Evaluate JavaScript against the current browser page" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_file_upload",
            descriptionGenerator = { "Resolve the active browser file chooser" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_fill_form",
            descriptionGenerator = { "Fill multiple browser form fields" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_handle_dialog",
            descriptionGenerator = { "Handle the current browser dialog" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_hover",
            descriptionGenerator = { "Hover a browser element by ref" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_navigate",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                "Navigate browser to ${url.ifBlank { "(missing url)" }}"
            },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_navigate_back",
            descriptionGenerator = { "Navigate browser back" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_network_requests",
            descriptionGenerator = { "Read browser network requests" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_press_key",
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                "Press browser key ${key.ifBlank { "(missing key)" }}"
            },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_resize",
            descriptionGenerator = { "Resize browser viewport" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_run_code",
            descriptionGenerator = { "Run Playwright-like browser code" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_select_option",
            descriptionGenerator = { "Select options in a browser control" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_snapshot",
            descriptionGenerator = { "Capture a browser accessibility snapshot, including same-origin iframe content" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_take_screenshot",
            descriptionGenerator = { "Take a browser screenshot" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_tabs",
            descriptionGenerator = { tool ->
                val action = tool.parameters.find { it.name == "action" }?.value ?: ""
                "Manage browser tabs with action ${action.ifBlank { "(missing action)" }}"
            },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_type",
            descriptionGenerator = { "Type into a browser element by ref" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    handler.registerTool(
            name = "browser_wait_for",
            descriptionGenerator = { "Wait for browser text or time conditions" },
            executor = { tool -> ToolGetter.getBrowserSessionTools(context).invoke(tool) }
    )

    // 休眠工具
    handler.registerTool(
            name = "sleep",
            descriptionGenerator = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000
                s(R.string.toolreg_sleep_desc, durationMs)
            },
            executor = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000

                val safeDuration = durationMs.coerceAtLeast(0)

                // Use runBlocking with Dispatchers.IO to ensure sleep happens on background thread
                runBlocking(Dispatchers.IO) {
                    delay(safeDuration.toLong())
                }

                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = SleepResultData(
                                requestedMs = durationMs,
                                sleptMs = safeDuration
                        )
                )
            }
    )

    // Intent工具
    handler.registerTool(
            name = "execute_intent",
            descriptionGenerator = { tool ->
                val action = tool.parameters.find { it.name == "action" }?.value
                val packageName = tool.parameters.find { it.name == "package" }?.value
                val component = tool.parameters.find { it.name == "component" }?.value
                val type = tool.parameters.find { it.name == "type" }?.value ?: "activity"

                when {
                    !component.isNullOrBlank() ->
                            s(R.string.toolreg_execute_intent_component_desc, component, type)
                    !packageName.isNullOrBlank() && !action.isNullOrBlank() ->
                            s(
                                    R.string.toolreg_execute_intent_action_package_desc,
                                    action,
                                    packageName,
                                    type
                            )
                    !action.isNullOrBlank() -> s(R.string.toolreg_execute_intent_action_desc, action, type)
                    else -> s(R.string.toolreg_execute_android_intent_desc, type)
                }
            },
            executor = { tool ->
                val intentTool = ToolGetter.getIntentToolExecutor(context)
                runBlocking(Dispatchers.IO) { intentTool.invoke(tool) }
            }
    )

    handler.registerTool(
            name = "send_broadcast",
            descriptionGenerator = { tool ->
                val action = tool.parameters.find { it.name == "action" }?.value
                val preview = action?.takeIf { it.isNotBlank() } ?: "(no action)"
                "Send broadcast: $preview"
            },
            executor = { tool ->
                val sendBroadcastTool = ToolGetter.getSendBroadcastToolExecutor(context)
                runBlocking(Dispatchers.IO) { sendBroadcastTool.invoke(tool) }
            }
    )

    // 设备信息工具
    handler.registerTool(
            name = "device_info",
            descriptionGenerator = { _ -> s(R.string.toolreg_device_info_desc) },
            executor = { tool ->
                val deviceInfoTool = ToolGetter.getDeviceInfoToolExecutor(context)
                deviceInfoTool.invoke(tool)
            }
    )
    
    // Tasker事件触发工具
    handler.registerTool(
            name = "trigger_tasker_event",
            descriptionGenerator = { tool ->
                val taskType = tool.parameters.find { it.name == "task_type" }?.value ?: ""
                val args = tool.parameters.filter { it.name.startsWith("arg1") }.joinToString(",")
                s(R.string.toolreg_trigger_tasker_event_desc, taskType, args)
            },
            executor = { tool ->
                val params = tool.parameters.associate { it.name to it.value }
                val taskType = params["task_type"]
                if (taskType.isNullOrBlank()) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = s(R.string.toolreg_missing_required_param, "task_type")
                    )
                } else {
                    val args = params.filterKeys { it != "task_type" }
                    try {
                        context.triggerAIAgentAction(
                            taskType,
                            args
                        )
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    StringResultData(
                                            s(R.string.toolreg_tasker_event_triggered_result, taskType)
                                    )
                        )
                    } catch (e: Exception) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error =
                                    s(
                                            R.string.toolreg_failed_trigger_tasker_event,
                                            e.message ?: ""
                                    )
                        )
                    }
                }
            }
    )

    
    // 工作流工具
    val workflowTools = ToolGetter.getWorkflowTools(context)

    // 获取所有工作流
    handler.registerTool(
            name = "get_all_workflows",
            descriptionGenerator = { _ -> s(R.string.toolreg_get_all_workflows_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.getAllWorkflows(tool) } }
    )

    // 创建工作流
    handler.registerTool(
            name = "create_workflow",
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                s(R.string.toolreg_create_workflow_desc, name)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.createWorkflow(tool) } }
    )

    // 获取工作流详情
    handler.registerTool(
            name = "get_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_get_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.getWorkflow(tool) } }
    )

    // 更新工作流
    handler.registerTool(
            name = "update_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                val name = tool.parameters.find { it.name == "name" }?.value
                if (name != null) {
                    s(R.string.toolreg_update_workflow_with_name_desc, id, name)
                } else {
                    s(R.string.toolreg_update_workflow_desc, id)
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.updateWorkflow(tool) } }
    )

    // 差异更新工作流
    handler.registerTool(
            name = "patch_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_patch_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.patchWorkflow(tool) } }
    )

    // 启用工作流
    handler.registerTool(
            name = "enable_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_enable_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.enableWorkflow(tool) } }
    )

    // 禁用工作流
    handler.registerTool(
            name = "disable_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_disable_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.disableWorkflow(tool) } }
    )

    // 删除工作流
    handler.registerTool(
            name = "delete_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_delete_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.deleteWorkflow(tool) } }
    )

    // 触发工作流执行
    handler.registerTool(
            name = "trigger_workflow",
            descriptionGenerator = { tool ->
                val id = tool.parameters.find { it.name == "workflow_id" }?.value ?: ""
                s(R.string.toolreg_trigger_workflow_desc, id)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { workflowTools.triggerWorkflow(tool) } }
    )

    // 对话管理工具
    val chatManagerTool = ToolGetter.getChatManagerTool(context)

    // 启动聊天服务
    handler.registerTool(
            name = "start_chat_service",
            descriptionGenerator = { _ -> s(R.string.toolreg_start_chat_service_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.startChatService(tool) } }
    )

    // 停止聊天服务
    handler.registerTool(
            name = "stop_chat_service",
            descriptionGenerator = { _ -> s(R.string.toolreg_stop_chat_service_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.stopChatService(tool) } }
    )

    // 新建对话
    handler.registerTool(
            name = "create_new_chat",
            descriptionGenerator = { tool ->
                val group = tool.parameters.find { it.name == "group" }?.value
                if (group.isNullOrBlank()) {
                    s(R.string.toolreg_create_new_chat_desc)
                } else {
                    s(R.string.toolreg_create_new_chat_in_group_desc, group)
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.createNewChat(tool) } }
    )

    // 列出所有对话
    handler.registerTool(
            name = "list_chats",
            descriptionGenerator = { _ -> s(R.string.toolreg_list_chats_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.listChats(tool) } }
    )

    // 查找对话
    handler.registerTool(
            name = "find_chat",
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                s(R.string.toolreg_find_chat_desc, query)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.findChat(tool) } }
    )

    // 查询对话输入状态
    handler.registerTool(
            name = "agent_status",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                s(R.string.toolreg_agent_status_desc, chatId)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.agentStatus(tool) } }
    )

    // 切换对话
    handler.registerTool(
            name = "switch_chat",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                s(R.string.toolreg_switch_chat_desc, chatId)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.switchChat(tool) } }
    )

    // 更新对话标题
    handler.registerTool(
            name = "update_chat_title",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                s(R.string.toolreg_update_chat_title_desc, chatId)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.updateChatTitle(tool) } }
    )

    // 删除对话
    handler.registerTool(
            name = "delete_chat",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                s(R.string.toolreg_delete_chat_desc, chatId)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.deleteChat(tool) } }
    )

    // 发送消息给AI
    handler.registerTool(
            name = "send_message_to_ai",
            descriptionGenerator = { tool ->
                val message = tool.parameters.find { it.name == "message" }?.value ?: ""
                val preview = if (message.length > 30) "${message.take(30)}..." else message
                s(R.string.toolreg_send_message_to_ai_desc, preview)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.sendMessageToAI(tool) } }
    )

    handler.registerTool(
            name = "send_message_to_ai_streaming",
            descriptionGenerator = { tool ->
                val message = tool.parameters.find { it.name == "message" }?.value ?: ""
                val preview = if (message.length > 30) "${message.take(30)}..." else message
                s(R.string.toolreg_send_message_to_ai_desc, preview)
            },
            executor =
                    object : ToolExecutor {
                        override fun invoke(tool: AITool): ToolResult {
                            return runBlocking(Dispatchers.IO) { chatManagerTool.sendMessageToAI(tool) }
                        }

                        override fun invokeAndStream(
                                tool: AITool
                        ): kotlinx.coroutines.flow.Flow<ToolResult> {
                            return chatManagerTool.sendMessageToAIStream(tool)
                        }
                    }
    )

    // 列出所有角色卡
    handler.registerTool(
            name = "list_character_cards",
            descriptionGenerator = { _ -> s(R.string. toolreg_list_character_cards_desc) },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.listCharacterCards(tool) } }
    )

    handler.registerTool(
            name = "get_chat_messages",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                val order = tool.parameters.find { it.name == "order" }?.value
                val limit = tool.parameters.find { it.name == "limit" }?.value
                val orderInfo = if (!order.isNullOrBlank()) " ($order)" else ""
                val limitInfo = if (!limit.isNullOrBlank()) " ($limit)" else ""
                s(R.string.toolreg_get_chat_messages_desc, chatId, orderInfo, limitInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.getChatMessages(tool) } }
    )

    handler.registerTool(
            name = "get_chat_messages_range",
            descriptionGenerator = { tool ->
                val chatId = tool.parameters.find { it.name == "chat_id" }?.value ?: ""
                val order = tool.parameters.find { it.name == "order" }?.value
                val start = tool.parameters.find { it.name == "start" }?.value
                val end = tool.parameters.find { it.name == "end" }?.value
                val orderInfo = if (!order.isNullOrBlank()) " ($order)" else ""
                val rangeInfo = if (!start.isNullOrBlank() && !end.isNullOrBlank()) " ($start-$end)" else ""
                s(R.string.toolreg_get_chat_messages_desc, chatId, orderInfo, rangeInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { chatManagerTool.getChatMessagesRange(tool) } }
    )

    // 文件系统工具
    val fileSystemTools = ToolGetter.getFileSystemTools(context)

    // 列出目录内容
    handler.registerTool(
            name = "list_files",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_list_files_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.listFiles(tool) }
            }
    )

    // 读取文件内容
    handler.registerTool(
            name = "read_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_read_file_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFile(tool) } }
    )

    // 按行号范围读取文件内容
    handler.registerTool(
            name = "read_file_part",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val startLine = tool.parameters.find { it.name == "start_line" }?.value ?: "1"
                val endLine = tool.parameters.find { it.name == "end_line" }?.value
                val envInfo = formatEnvInfo(environment)
                val rangeInfo =
                        if (endLine != null) {
                            s(R.string.toolreg_read_file_part_range_lines, startLine, endLine)
                        } else {
                            s(R.string.toolreg_read_file_part_range_from, startLine)
                        }
                s(R.string.toolreg_read_file_part_desc, rangeInfo, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.readFilePart(tool) }
            }
    )

    // 读取完整文件内容
    handler.registerTool(
            name = "read_file_full",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_read_file_full_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFileFull(tool) } }
    )

    // 读取二进制文件内容（Base64编码）
    handler.registerTool(
            name = "read_file_binary",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_read_file_binary_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.readFileBinary(tool) } }
    )

    // 写入文件
    handler.registerTool(
            name = "write_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val append = tool.parameters.find { it.name == "append" }?.value == "true"
                val envInfo = formatEnvInfo(environment)
                val operation =
                        if (append) {
                            s(R.string.toolreg_write_file_append_operation)
                        } else {
                            s(R.string.toolreg_write_file_overwrite_operation)
                        }
                s(R.string.toolreg_write_file_desc, operation, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.writeFile(tool) }
            }
    )

    // 写入二进制文件
    handler.registerTool(
        name = "write_file_binary",
        descriptionGenerator = { tool ->
            val path = tool.parameters.find { it.name == "path" }?.value ?: ""
            val environment = tool.parameters.find { it.name == "environment" }?.value
            val envInfo = formatEnvInfo(environment)
            s(R.string.toolreg_write_file_binary_desc, path, envInfo)
        },
        executor = { tool ->
            runBlocking(Dispatchers.IO) { fileSystemTools.writeFileBinary(tool) }
        }
    )

    // 删除文件/目录
    handler.registerTool(
            name = "delete_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val recursive = tool.parameters.find { it.name == "recursive" }?.value == "true"
                val envInfo = formatEnvInfo(environment)
                val operation =
                        if (recursive) {
                            s(R.string.toolreg_delete_file_recursive_operation)
                        } else {
                            s(R.string.toolreg_delete_file_operation)
                        }
                s(R.string.toolreg_delete_file_desc, operation, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.deleteFile(tool) } }
    )

    // UI自动化工具
    val uiTools = ToolGetter.getUITools(context)

    // 点击元素
    handler.registerTool(
            name = "click_element",
            descriptionGenerator = { tool ->
                val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
                val className = tool.parameters.find { it.name == "className" }?.value
                val bounds = tool.parameters.find { it.name == "bounds" }?.value
                val index = tool.parameters.find { it.name == "index" }?.value ?: "0"
                val indexSuffix =
                        if (index != "0") {
                            s(R.string.toolreg_index_suffix, index)
                        } else {
                            ""
                        }

                when {
                    resourceId != null ->
                            s(R.string.toolreg_click_element_resourceid_desc, resourceId, indexSuffix)
                    className != null ->
                            s(R.string.toolreg_click_element_classname_desc, className, indexSuffix)
                    bounds != null -> s(R.string.toolreg_click_element_bounds_desc, bounds)
                    else -> s(R.string.toolreg_click_element_desc)
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.clickElement(it) }
                }
            }
    )

    // 点击屏幕坐标
    handler.registerTool(
            name = "tap",
            descriptionGenerator = { tool ->
                val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
                val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
                s(R.string.toolreg_tap_desc, x, y)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.tap(it) }
                }
            }
    )

    handler.registerTool(
            name = "long_press",
            descriptionGenerator = { tool ->
                val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
                val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
                s(R.string.toolreg_long_press_desc, x, y)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.longPress(it) }
                }
            }
    )

    // HTTP请求工具
    val httpTools = ToolGetter.getHttpTools(context)

    // 发送HTTP请求
    handler.registerTool(
            name = "http_request",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val method = tool.parameters.find { it.name == "method" }?.value ?: "GET"
                s(R.string.toolreg_http_request_desc, method, url)
            },
            executor =
                    object : ToolExecutor {
                        override fun invoke(tool: AITool): ToolResult {
                            return runBlocking(Dispatchers.IO) { httpTools.httpRequest(tool) }
                        }

                        override fun invokeAndStream(
                                tool: AITool
                        ): kotlinx.coroutines.flow.Flow<ToolResult> {
                            return runBlocking(Dispatchers.IO) { httpTools.httpRequestStream(tool) }
                        }
                    }
    )

    // 多部分表单请求（文件上传）
    handler.registerTool(
            name = "multipart_request",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val filesParam = tool.parameters.find { it.name == "files" }?.value ?: "[]"
                val filesCount =
                        try {
                            JSONArray(filesParam).length()
                        } catch (e: Exception) {
                            0
                        }
                s(R.string.toolreg_multipart_request_desc, url, filesCount)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { httpTools.multipartRequest(tool) }
            }
    )

    // 管理Cookie工具
    handler.registerTool(
            name = "manage_cookies",
            descriptionGenerator = { tool ->
                val action =
                        tool.parameters.find { it.name == "action" }?.value?.lowercase() ?: "get"
                val domain = tool.parameters.find { it.name == "domain" }?.value ?: ""
                when (action) {
                    "get" ->
                            if (domain.isBlank()) {
                                s(R.string.toolreg_manage_cookies_get_all_desc)
                            } else {
                                s(R.string.toolreg_manage_cookies_get_domain_desc, domain)
                            }
                    "set" -> s(R.string.toolreg_manage_cookies_set_domain_desc, domain)
                    "clear" ->
                            if (domain.isBlank()) {
                                s(R.string.toolreg_manage_cookies_clear_all_desc)
                            } else {
                                s(R.string.toolreg_manage_cookies_clear_domain_desc, domain)
                            }
                    else -> s(R.string.toolreg_manage_cookies_desc, action)
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { httpTools.manageCookies(tool) } }
    )

    // 检查文件是否存在
    handler.registerTool(
            name = "file_exists",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_file_exists_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.fileExists(tool) }
            }
    )

    // 移动/重命名文件或目录
    handler.registerTool(
            name = "move_file",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_move_file_desc, source, destination, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.moveFile(tool) } }
    )

    // 复制文件或目录
    handler.registerTool(
            name = "copy_file",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val sourceEnv = tool.parameters.find { it.name == "source_environment" }?.value
                val destEnv = tool.parameters.find { it.name == "dest_environment" }?.value
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 确定源和目标环境
                val srcEnv = sourceEnv ?: environment ?: "android"
                val dstEnv = destEnv ?: environment ?: "android"

                val envInfo = formatEnvArrowInfo(srcEnv, dstEnv)
                s(R.string.toolreg_copy_file_desc, source, destination, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.copyFile(tool) } }
    )

    // 创建目录
    handler.registerTool(
            name = "make_directory",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_make_directory_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.makeDirectory(tool) }
            }
    )

    // 搜索文件
    handler.registerTool(
            name = "find_files",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: "*"
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_find_files_desc, path, pattern, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.findFiles(tool) }
            }
    )

    // 获取文件信息
    handler.registerTool(
            name = "file_info",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_file_info_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.fileInfo(tool) } }
    )

    // 智能应用文件绑定
    handler.registerTool(
            name = "apply_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_apply_file_desc, path, envInfo)
            },
            executor =
                    object : ToolExecutor {
                        override fun invoke(tool: AITool): ToolResult {
                            return runBlocking { fileSystemTools.applyFile(tool).last() }
                        }

                        override fun invokeAndStream(
                                tool: AITool
                        ): kotlinx.coroutines.flow.Flow<ToolResult> {
                            return fileSystemTools.applyFile(tool)
                        }
                    }
    )

    handler.registerTool(
            name = "create_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                "Create file $path$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.createFile(tool) } }
    )

    handler.registerTool(
            name = "edit_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                "Edit file $path$envInfo"
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.editFile(tool) } }
    )

    // 压缩文件/目录
    handler.registerTool(
            name = "zip_files",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_zip_files_desc, source, destination, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.zipFiles(tool) } }
    )

    // 解压缩文件
    handler.registerTool(
            name = "unzip_files",
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_unzip_files_desc, source, destination, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.unzipFiles(tool) }
            }
    )

    // 打开文件
    handler.registerTool(
            name = "open_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_open_file_desc, path, envInfo)
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { fileSystemTools.openFile(tool) } }
    )

    // 分享文件
    handler.registerTool(
            name = "share_file",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_share_file_desc, path, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.shareFile(tool) }
            }
    )

    // Grep代码搜索
    handler.registerTool(
            name = "grep_code",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
                val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                if (filePattern != null && filePattern != "*") {
                    s(R.string.toolreg_grep_code_with_file_pattern_desc, path, pattern, envInfo, filePattern)
                } else {
                    s(R.string.toolreg_grep_code_desc, path, pattern, envInfo)
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.grepCode(tool) }
            }
    )

    // Grep上下文搜索
    handler.registerTool(
            name = "grep_context",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                val preview = if (intent.length > 40) "${intent.take(40)}..." else intent
                s(R.string.toolreg_grep_context_desc, path, preview, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.grepContext(tool) }
            }
    )

    // 下载文件
    handler.registerTool(
            name = "download_file",
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val envInfo = formatEnvInfo(environment)
                s(R.string.toolreg_download_file_desc, url, destination, envInfo)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { fileSystemTools.downloadFile(tool) }
            }
    )

    // 系统操作工具
    val systemOperationTools = ToolGetter.getSystemOperationTools(context)

    handler.registerTool(
            name = "toast",
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.toast(tool) }
            }
    )

    handler.registerTool(
            name = "send_notification",
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.sendNotification(tool) }
            }
    )

    // 修改系统设置
    handler.registerTool(
            name = "modify_system_setting",
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                val value = tool.parameters.find { it.name == "value" }?.value ?: ""
                s(R.string.toolreg_modify_system_setting_desc, key, value)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.modifySystemSetting(tool) }
            }
    )

    // 获取系统设置
    handler.registerTool(
            name = "get_system_setting",
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                s(R.string.toolreg_get_system_setting_desc, key)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getSystemSetting(tool) }
            }
    )

    // 安装应用
    handler.registerTool(
            name = "install_app",
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                s(R.string.toolreg_install_app_desc, path)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.installApp(tool) }
            }
    )

    // 卸载应用
    handler.registerTool(
            name = "uninstall_app",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_uninstall_app_desc, packageName)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.uninstallApp(tool) }
            }
    )

    // 获取已安装应用列表
    handler.registerTool(
            name = "list_installed_apps",
            descriptionGenerator = { _ -> s(R.string.toolreg_list_installed_apps_desc) },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.listInstalledApps(tool) }
            }
    )

    // 启动应用
    handler.registerTool(
            name = "start_app",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_start_app_desc, packageName)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.startApp(tool) }
            }
    )

    // 停止应用
    handler.registerTool(
            name = "stop_app",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                s(R.string.toolreg_stop_app_desc, packageName)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.stopApp(tool) }
            }
    )

    // 获取设备通知
    handler.registerTool(
            name = "get_notifications",
            descriptionGenerator = { tool ->
                val limit = tool.parameters.find { it.name == "limit" }?.value ?: "10"
                val includeOngoing =
                        tool.parameters.find { it.name == "include_ongoing" }?.value == "true"

                if (includeOngoing) {
                    s(R.string.toolreg_get_notifications_desc_with_ongoing, limit)
                } else {
                    s(R.string.toolreg_get_notifications_desc, limit)
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getNotifications(tool) }
            }
    )

    // 获取应用使用时长
    handler.registerTool(
            name = "get_app_usage_time",
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value.orEmpty()
                val sinceHours = tool.parameters.find { it.name == "since_hours" }?.value ?: "24"
                if (packageName.isNotBlank()) {
                    "Get app usage time for $packageName in the last ${sinceHours} hours"
                } else {
                    "Get app usage time ranking in the last ${sinceHours} hours"
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getAppUsageTime(tool) }
            }
    )

    // 获取设备位置
    handler.registerTool(
            name = "get_device_location",
            descriptionGenerator = { tool ->
                val highAccuracy =
                        tool.parameters.find { it.name == "high_accuracy" }?.value == "true"
                if (highAccuracy) {
                    s(R.string.toolreg_get_device_location_high_accuracy_desc)
                } else {
                    s(R.string.toolreg_get_device_location_desc)
                }
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getDeviceLocation(tool) }
            }
    )

    handler.registerTool(
            name = "request_bluetooth_permission",
            descriptionGenerator = { _ -> "Request Bluetooth nearby devices permission" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.requestBluetoothPermission(tool) }
            }
    )

    handler.registerTool(
            name = "get_bluetooth_state",
            descriptionGenerator = { _ -> "Get Bluetooth adapter state" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.getBluetoothState(tool) }
            }
    )

    handler.registerTool(
            name = "request_enable_bluetooth",
            descriptionGenerator = { _ -> "Open the system dialog to enable Bluetooth" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.requestEnableBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "list_bluetooth_bonded_devices",
            descriptionGenerator = { _ -> "List bonded Bluetooth devices" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.listBluetoothBondedDevices(tool) }
            }
    )

    handler.registerTool(
            name = "scan_bluetooth_devices",
            descriptionGenerator = { _ -> "Scan nearby Bluetooth classic and BLE devices" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.scanBluetoothDevices(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_connect",
            descriptionGenerator = { tool ->
                val address = tool.parameters.find { it.name == "address" }?.value ?: ""
                "Connect to Bluetooth classic device $address"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.connectBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_listen",
            descriptionGenerator = { _ -> "Listen for an incoming Bluetooth classic connection" },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.listenBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_accept",
            descriptionGenerator = { tool ->
                val listenerId = tool.parameters.find { it.name == "listener_session_id" }?.value ?: ""
                "Accept an incoming Bluetooth classic connection from listener $listenerId"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.acceptBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_send",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value ?: ""
                "Send data to Bluetooth session $sessionId"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.sendBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_read",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value ?: ""
                "Read data from Bluetooth session $sessionId"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.readBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_send_and_read",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value ?: ""
                "Send data and read response from Bluetooth session $sessionId"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.sendAndReadBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_close",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value ?: ""
                "Close Bluetooth session $sessionId"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.closeBluetooth(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_ble_connect",
            descriptionGenerator = { tool ->
                val address = tool.parameters.find { it.name == "address" }?.value ?: ""
                "Connect to BLE device $address"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.connectBle(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_ble_discover_services",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value ?: ""
                "Discover BLE services for session $sessionId"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.discoverBleServices(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_ble_read_characteristic",
            descriptionGenerator = { tool ->
                val characteristicUuid = tool.parameters.find { it.name == "characteristic_uuid" }?.value ?: ""
                "Read BLE characteristic $characteristicUuid"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.readBleCharacteristic(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_ble_write_characteristic",
            descriptionGenerator = { tool ->
                val characteristicUuid = tool.parameters.find { it.name == "characteristic_uuid" }?.value ?: ""
                "Write BLE characteristic $characteristicUuid"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.writeBleCharacteristic(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_ble_write_and_read_characteristic",
            descriptionGenerator = { tool ->
                val writeCharacteristicUuid = tool.parameters.find { it.name == "write_characteristic_uuid" }?.value ?: ""
                val readCharacteristicUuid = tool.parameters.find { it.name == "read_characteristic_uuid" }?.value ?: ""
                "Write BLE characteristic $writeCharacteristicUuid and read $readCharacteristicUuid"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.writeAndReadBleCharacteristic(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_ble_subscribe_characteristic",
            descriptionGenerator = { tool ->
                val characteristicUuid = tool.parameters.find { it.name == "characteristic_uuid" }?.value ?: ""
                "Subscribe BLE characteristic $characteristicUuid"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.subscribeBleCharacteristic(tool) }
            }
    )

    handler.registerTool(
            name = "bluetooth_ble_read_notifications",
            descriptionGenerator = { tool ->
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value ?: ""
                "Read BLE notifications from session $sessionId"
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) { systemOperationTools.readBleNotifications(tool) }
            }
    )

    // 获取当前页面/窗口信息
    handler.registerTool(
            name = "get_page_info",
            descriptionGenerator = { _ -> s(R.string.toolreg_get_page_info_desc) },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.getPageInfo(it) }
                }
            }
    )

    handler.registerTool(
            name = "capture_screenshot",
            descriptionGenerator = { _ -> s(R.string.toolreg_capture_screenshot_desc) },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(
                        tool = tool,
                        showStatusIndicator = false,
                        delayMs = 200
                    ) { t ->
                        val (path, _) = uiTools.captureScreenshot(t)
                        if (path.isNullOrBlank()) {
                            ToolResult(toolName = t.name, success = false, result = StringResultData(""), error = "Screenshot failed")
                        } else {
                            ToolResult(toolName = t.name, success = true, result = StringResultData(path), error = null)
                        }
                    }
                }
            }
    )

    handler.registerTool(
            name = "run_ui_subagent",
            descriptionGenerator = { tool ->
                val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
                val maxSteps = tool.parameters.find { it.name == "max_steps" }?.value ?: "20"
                val agentId = tool.parameters.find { it.name == "agent_id" }?.value
                buildString {
                    append(s(R.string.toolreg_run_ui_subagent_desc, intent, maxSteps))
                    if (!agentId.isNullOrBlank()) {
                        append(s(R.string.toolreg_agent_id_suffix, agentId))
                    }
                    append(s(R.string.toolreg_run_ui_subagent_hint))
                }
            },
            executor = { tool -> runBlocking(Dispatchers.IO) { uiTools.runUiSubAgent(tool) } }
    )

    // 在输入框中设置文本
    handler.registerTool(
            name = "set_input_text",
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value ?: ""
                s(R.string.toolreg_set_input_text_desc, text)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.setInputText(it) }
                }
            }
    )

    // 按下特定按键
    handler.registerTool(
            name = "press_key",
            descriptionGenerator = { tool ->
                val keyCode = tool.parameters.find { it.name == "key_code" }?.value ?: ""
                s(R.string.toolreg_press_key_desc, keyCode)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.pressKey(it) }
                }
            }
    )

    // 执行滑动手势
    handler.registerTool(
            name = "swipe",
            descriptionGenerator = { tool ->
                val startX = tool.parameters.find { it.name == "start_x" }?.value ?: "?"
                val startY = tool.parameters.find { it.name == "start_y" }?.value ?: "?"
                val endX = tool.parameters.find { it.name == "end_x" }?.value ?: "?"
                val endY = tool.parameters.find { it.name == "end_y" }?.value ?: "?"
                s(R.string.toolreg_swipe_desc, startX, startY, endX, endY)
            },
            executor = { tool ->
                runBlocking(Dispatchers.IO) {
                    executeUiToolWithVisibility(tool) { uiTools.swipe(it) }
                }
            }
    )

    // FFmpeg工具 - 执行通用FFmpeg命令
    handler.registerTool(
            name = "ffmpeg_execute",
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                s(R.string.toolreg_ffmpeg_execute_desc, command)
            },
            executor = { tool ->
                val ffmpegTool = ToolGetter.getFFmpegToolExecutor(context)
                ffmpegTool.invoke(tool)
            }
    )

    // FFmpeg信息工具 - 获取FFmpeg信息
    handler.registerTool(
            name = "ffmpeg_info",
            descriptionGenerator = { _ -> s(R.string.toolreg_ffmpeg_info_desc) },
            executor = { tool ->
                val ffmpegInfoTool = ToolGetter.getFFmpegInfoToolExecutor()
                ffmpegInfoTool.invoke(tool)
            }
    )

    // FFmpeg视频转换工具 - 简化的视频转换接口
    handler.registerTool(
            name = "ffmpeg_convert",
            descriptionGenerator = { tool ->
                val inputPath = tool.parameters.find { it.name == "input_path" }?.value ?: ""
                val outputPath = tool.parameters.find { it.name == "output_path" }?.value ?: ""
                s(R.string.toolreg_ffmpeg_convert_desc, inputPath, outputPath)
            },
            executor = { tool ->
                val ffmpegConvertTool = ToolGetter.getFFmpegConvertToolExecutor(context)
                ffmpegConvertTool.invoke(tool)
            }
    )
}

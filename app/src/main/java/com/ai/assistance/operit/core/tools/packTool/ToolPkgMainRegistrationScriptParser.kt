package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONObject

internal object ToolPkgMainRegistrationScriptParser {
    private const val TAG = "ToolPkgMainRegParser"

    fun parse(
        script: String,
        toolPkgId: String,
        mainScriptPath: String,
        jsEngine: JsEngine
    ): ToolPkgMainRegistrationParseResult {
        return try {
            val captured =
                jsEngine.executeToolPkgMainRegistrationFunction(
                    script = script,
                    functionName = "registerToolPkg",
                    params =
                        mapOf(
                            "toolPkgId" to toolPkgId,
                            "__operit_ui_package_name" to toolPkgId,
                            "__operit_plugin_id" to "registerToolPkg:$toolPkgId",
                            "__operit_registration_mode" to true,
                            "__operit_script_screen" to mainScriptPath
                        )
                )
            val uiModules = parseRegisteredUiModules(captured.toolboxUiModules)
            val uiRoutes = parseRegisteredUiRoutes(captured.uiRoutes, toolPkgId)
            val navigationEntries = parseRegisteredNavigationEntries(captured.navigationEntries)
            val desktopWidgets = parseRegisteredDesktopWidgets(captured.desktopWidgets)
            val appLifecycleHooks = parseRegisteredAppLifecycleHooks(captured.appLifecycleHooks)
            val messageProcessingPlugins =
                parseRegisteredFunctionHooks(
                    registrations = captured.messageProcessingPlugins,
                    registryName = TOOLPKG_REGISTRATION_MESSAGE_PROCESSING_PLUGIN
                )
            val xmlRenderPlugins =
                parseRegisteredTagFunctionHooks(
                    registrations = captured.xmlRenderPlugins,
                    registryName = TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN
                )
            val inputMenuTogglePlugins =
                parseRegisteredFunctionHooks(
                    registrations = captured.inputMenuTogglePlugins,
                    registryName = TOOLPKG_REGISTRATION_INPUT_MENU_TOGGLE_PLUGIN
                )
            val chatInputHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.chatInputHooks,
                    registryName = TOOLPKG_REGISTRATION_CHAT_INPUT_HOOK
                )
            val chatViewHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.chatViewHooks,
                    registryName = TOOLPKG_REGISTRATION_CHAT_VIEW_HOOK
                )
            val chatMessageHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.chatMessageHooks,
                    registryName = TOOLPKG_REGISTRATION_CHAT_MESSAGE_HOOK
                )
            val toolLifecycleHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.toolLifecycleHooks,
                    registryName = TOOLPKG_REGISTRATION_TOOL_LIFECYCLE_HOOK
                )
            val promptInputHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.promptInputHooks,
                    registryName = TOOLPKG_REGISTRATION_PROMPT_INPUT_HOOK
                )
            val promptHistoryHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.promptHistoryHooks,
                    registryName = TOOLPKG_REGISTRATION_PROMPT_HISTORY_HOOK
                )
            val promptEstimateHistoryHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.promptEstimateHistoryHooks,
                    registryName = TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_HISTORY_HOOK
                )
            val systemPromptComposeHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.systemPromptComposeHooks,
                    registryName = TOOLPKG_REGISTRATION_SYSTEM_PROMPT_COMPOSE_HOOK
                )
            val toolPromptComposeHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.toolPromptComposeHooks,
                    registryName = TOOLPKG_REGISTRATION_TOOL_PROMPT_COMPOSE_HOOK
                )
            val promptFinalizeHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.promptFinalizeHooks,
                    registryName = TOOLPKG_REGISTRATION_PROMPT_FINALIZE_HOOK
                )
            val promptEstimateFinalizeHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.promptEstimateFinalizeHooks,
                    registryName = TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_FINALIZE_HOOK
                )
            val summaryGenerateHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.summaryGenerateHooks,
                    registryName = TOOLPKG_REGISTRATION_SUMMARY_GENERATE_HOOK
                )
            val aiProviders =
                parseRegisteredAiProviders(
                    registrations = captured.aiProviders,
                    registryName = TOOLPKG_REGISTRATION_AI_PROVIDER
                )
            val marketOrigin =
                ToolPkgMarketOriginCodec.validateForPackage(
                    origin = captured.marketOrigin,
                    packageId = toolPkgId
                )
            ToolPkgMainRegistrationParseResult.Success(
                registration =
                    ToolPkgMainRegistration(
                        toolboxUiModules = uiModules,
                        uiRoutes = uiRoutes,
                        navigationEntries = navigationEntries,
                        desktopWidgets = desktopWidgets,
                        appLifecycleHooks = appLifecycleHooks,
                        messageProcessingPlugins = messageProcessingPlugins,
                        xmlRenderPlugins = xmlRenderPlugins,
                        inputMenuTogglePlugins = inputMenuTogglePlugins,
                        chatInputHooks = chatInputHooks,
                        chatViewHooks = chatViewHooks,
                        chatMessageHooks = chatMessageHooks,
                        toolLifecycleHooks = toolLifecycleHooks,
                        promptInputHooks = promptInputHooks,
                        promptHistoryHooks = promptHistoryHooks,
                        promptEstimateHistoryHooks = promptEstimateHistoryHooks,
                        systemPromptComposeHooks = systemPromptComposeHooks,
                        toolPromptComposeHooks = toolPromptComposeHooks,
                        promptFinalizeHooks = promptFinalizeHooks,
                        promptEstimateFinalizeHooks = promptEstimateFinalizeHooks,
                        summaryGenerateHooks = summaryGenerateHooks,
                        aiProviders = aiProviders,
                        marketOrigin = marketOrigin
                    )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse toolpkg main registration: $toolPkgId", e)
            val message =
                buildDeveloperFacingFailureMessage(
                    mainScriptPath = mainScriptPath,
                    error = e
                )
            AppLogger.e(
                "ToolPkg",
                "PKG: main registration parse failed, toolPkgId=$toolPkgId, reason=$message",
                e
            )
            ToolPkgMainRegistrationParseResult.Failure(message)
        }
    }

    private fun buildDeveloperFacingFailureMessage(
        mainScriptPath: String,
        error: Exception
    ): String {
        val rawMessage = error.message?.trim().orEmpty()
        val compactMessage =
            rawMessage
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: error.javaClass.simpleName

        return "main script '$mainScriptPath' failed while loading or running registerToolPkg(): $compactMessage"
    }

    private fun parseRegisteredUiModules(
        registrations: List<String>
    ): List<ToolPkgRegisteredUiModule> {
        val modules = mutableListOf<ToolPkgRegisteredUiModule>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_TOOLBOX_UI_MODULE payload[$index] must be a JSON object",
                        e
                    )
                }

            val id = item.optString("id").trim()
            val screen = item.optString("screen").trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOLBOX_UI_MODULE[$index].id is required")
            }
            if (screen.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOLBOX_UI_MODULE[$index].screen is required")
            }

            val runtime = item.optString("runtime").trim().ifBlank { TOOLPKG_RUNTIME_COMPOSE_DSL }
            val title = parseLocalizedText(item.opt("title"), fallback = id)
            val keepAlive = item.optBoolean("keepAlive", false)
            modules.add(
                ToolPkgRegisteredUiModule(
                    id = id,
                    runtime = runtime,
                    screen = screen,
                    title = title,
                    keepAlive = keepAlive
                )
            )
        }
        return modules
    }

    private fun parseRegisteredUiRoutes(
        registrations: List<String>,
        toolPkgId: String
    ): List<ToolPkgRegisteredUiRoute> {
        val routes = mutableListOf<ToolPkgRegisteredUiRoute>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_UI_ROUTE payload[$index] must be a JSON object",
                        e
                    )
                }

            val id = item.optString("id").trim()
            val screen = item.optString("screen").trim()
            val routeId =
                item.optString("route").trim().ifBlank {
                    item.optString("routeId").trim()
                }.ifBlank {
                    buildToolPkgRouteId(toolPkgId, id)
                }
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_UI_ROUTE[$index].id is required")
            }
            if (screen.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_UI_ROUTE[$index].screen is required")
            }
            if (routeId.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_UI_ROUTE[$index].route is required")
            }

            val runtime = item.optString("runtime").trim().ifBlank { TOOLPKG_RUNTIME_COMPOSE_DSL }
            val title = parseLocalizedText(item.opt("title"), fallback = id)
            val keepAlive = item.optBoolean("keepAlive", false)
            routes.add(
                ToolPkgRegisteredUiRoute(
                    id = id,
                    routeId = routeId,
                    runtime = runtime,
                    screen = screen,
                    title = title,
                    keepAlive = keepAlive
                )
            )
        }
        return routes
    }

    private fun parseRegisteredNavigationEntries(
        registrations: List<String>
    ): List<ToolPkgRegisteredNavigationEntry> {
        val entries = mutableListOf<ToolPkgRegisteredNavigationEntry>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY payload[$index] must be a JSON object",
                        e
                    )
                }
            val id = item.optString("id").trim()
            val routeId =
                item.optString("route").trim().ifBlank {
                    item.optString("routeId").trim()
                }
            val surface = item.optString("surface").trim().lowercase()
            val action = parseNavigationEntryAction(item, index)
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].id is required")
            }
            if (routeId.isBlank() && action == null) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].route or action is required"
                )
            }
            if (surface.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].surface is required")
            }
            entries.add(
                ToolPkgRegisteredNavigationEntry(
                    id = id,
                    routeId = routeId,
                    surface = surface,
                    title = parseLocalizedText(item.opt("title"), fallback = id),
                    action = action,
                    icon = item.optString("icon").trim().ifBlank { null },
                    order = item.optInt("order", 0)
                )
            )
        }
        return entries
    }

    private fun parseRegisteredDesktopWidgets(
        registrations: List<String>
    ): List<ToolPkgRegisteredDesktopWidget> {
        val widgets = mutableListOf<ToolPkgRegisteredDesktopWidget>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_DESKTOP_WIDGET payload[$index] must be a JSON object",
                        e
                    )
                }
            val id = item.optString("id").trim()
            val routeId =
                item.optString("route").trim().ifBlank {
                    item.optString("routeId").trim()
                }
            val renderRouteId =
                item.optString("render").trim().ifBlank {
                    item.optString("renderRouteId").trim()
                }.ifBlank {
                    routeId
                }
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_DESKTOP_WIDGET[$index].id is required")
            }
            if (routeId.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_DESKTOP_WIDGET[$index].route is required")
            }
            widgets.add(
                ToolPkgRegisteredDesktopWidget(
                    id = id,
                    routeId = routeId,
                    renderRouteId = renderRouteId,
                    title = parseLocalizedText(item.opt("title"), fallback = id),
                    subtitle = parseLocalizedText(item.opt("subtitle"), fallback = ""),
                    description = parseLocalizedText(item.opt("description"), fallback = ""),
                    icon = item.optString("icon").trim().ifBlank { null },
                    order = item.optInt("order", 0)
                )
            )
        }
        return widgets
    }

    private fun parseNavigationEntryAction(
        item: JSONObject,
        index: Int
    ): ToolPkgNavigationActionHookRuntime? {
        val directFunctionName = item.optString("action").trim()
        val directFunctionSource = item.optString("function_source").trim().ifBlank { null }
        if (directFunctionName.isNotBlank()) {
            return ToolPkgNavigationActionHookRuntime(
                function = directFunctionName,
                functionSource = directFunctionSource
            )
        }

        val actionObj =
            item.optJSONObject("action")
                ?: return null
        val functionName = actionObj.optString("function").trim()
        if (functionName.isBlank()) {
            throw IllegalArgumentException(
                "$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].action function is required"
            )
        }

        return ToolPkgNavigationActionHookRuntime(
            function = functionName,
            functionSource = actionObj.optString("function_source").trim().ifBlank { null }
        )
    }

    private fun parseRegisteredAppLifecycleHooks(
        registrations: List<String>
    ): List<ToolPkgRegisteredAppLifecycleHook> {
        val hooks = mutableListOf<ToolPkgRegisteredAppLifecycleHook>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK payload[$index] must be a JSON object",
                        e
                    )
                }
            val id = item.optString("id").trim()
            val event = item.optString("event").trim()
            val functionName = item.optString("function").trim()
            val functionSource = item.optString("function_source").trim().ifBlank { null }

            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].id is required")
            }
            if (event.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].event is required")
            }
            if (functionName.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].function is required")
            }

            hooks.add(
                ToolPkgRegisteredAppLifecycleHook(
                    id = id,
                    event = event,
                    function = functionName,
                    functionSource = functionSource
                )
            )
        }
        return hooks
    }

    private fun parseRegisteredFunctionHooks(
        registrations: List<String>,
        registryName: String
    ): List<ToolPkgRegisteredFunctionHook> {
        val hooks = mutableListOf<ToolPkgRegisteredFunctionHook>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$registryName payload[$index] must be a JSON object",
                        e
                    )
                }
            val id = item.optString("id").trim()
            val functionName = item.optString("function").trim()
            val functionSource = item.optString("function_source").trim().ifBlank { null }

            if (id.isBlank()) {
                throw IllegalArgumentException("$registryName[$index].id is required")
            }
            if (functionName.isBlank()) {
                throw IllegalArgumentException("$registryName[$index].function is required")
            }

            hooks.add(
                ToolPkgRegisteredFunctionHook(
                    id = id,
                    function = functionName,
                    functionSource = functionSource
                )
            )
        }
        return hooks
    }

    private fun parseRegisteredTagFunctionHooks(
        registrations: List<String>,
        registryName: String
    ): List<ToolPkgRegisteredTagFunctionHook> {
        val hooks = mutableListOf<ToolPkgRegisteredTagFunctionHook>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$registryName payload[$index] must be a JSON object",
                        e
                    )
                }
            val id = item.optString("id").trim()
            val tagName = item.optString("tag").trim()
            val functionName = item.optString("function").trim()
            val functionSource = item.optString("function_source").trim().ifBlank { null }

            if (id.isBlank()) {
                throw IllegalArgumentException("$registryName[$index].id is required")
            }
            if (tagName.isBlank()) {
                throw IllegalArgumentException("$registryName[$index].tag is required")
            }
            if (functionName.isBlank()) {
                throw IllegalArgumentException("$registryName[$index].function is required")
            }

            hooks.add(
                ToolPkgRegisteredTagFunctionHook(
                    id = id,
                    tag = tagName,
                    function = functionName,
                    functionSource = functionSource
                )
            )
        }
        return hooks
    }

    private fun parseRegisteredAiProviders(
        registrations: List<String>,
        registryName: String
    ): List<ToolPkgRegisteredAiProvider> {
        val providers = mutableListOf<ToolPkgRegisteredAiProvider>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "$registryName payload[$index] must be a JSON object",
                        e
                    )
                }
            val id = item.optString("id").trim()
            val displayName = item.optString("displayName").trim()
            val description = item.optString("description").trim()

            if (id.isBlank()) {
                throw IllegalArgumentException("$registryName[$index].id is required")
            }

            fun parseHandler(fieldName: String): ToolPkgRegisteredAiProviderHandler {
                val rawHandler = item.opt(fieldName)
                val handlerObject =
                    when (rawHandler) {
                        is JSONObject -> rawHandler
                        null, JSONObject.NULL ->
                            throw IllegalArgumentException(
                                "$registryName[$index].$fieldName is required"
                            )
                        else ->
                            throw IllegalArgumentException(
                                "$registryName[$index].$fieldName must be an object"
                            )
                    }
                val functionName = handlerObject.optString("function").trim()
                val functionSource =
                    handlerObject.optString("function_source").trim().ifBlank { null }
                if (functionName.isBlank()) {
                    throw IllegalArgumentException(
                        "$registryName[$index].$fieldName.function is required"
                    )
                }
                return ToolPkgRegisteredAiProviderHandler(
                    function = functionName,
                    functionSource = functionSource
                )
            }

            providers.add(
                ToolPkgRegisteredAiProvider(
                    id = id,
                    displayName = displayName.ifBlank { id },
                    description = description,
                    listModelsHandler = parseHandler("listModels"),
                    sendMessageHandler = parseHandler("sendMessage"),
                    testConnectionHandler = parseHandler("testConnection"),
                    calculateInputTokensHandler = parseHandler("calculateInputTokens")
                )
            )
        }
        return providers
    }

    private fun parseLocalizedText(raw: Any?, fallback: String): LocalizedText {
        if (raw is String) {
            val text = raw.trim()
            if (text.isNotBlank()) {
                return LocalizedText.of(text)
            }
        }

        val json =
            when (raw) {
                is JSONObject -> raw
                is Map<*, *> -> JSONObject(raw)
                is String ->
                    try {
                        JSONObject(raw)
                    } catch (_: Exception) {
                        null
                    }
                else -> null
            }
        if (json != null) {
            val values = linkedMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key).trim()
                if (value.isNotBlank()) {
                    values[key] = value
                }
            }
            if (values.isNotEmpty()) {
                if (!values.containsKey("default")) {
                    values["default"] = values.values.first()
                }
                return LocalizedText(values)
            }
        }

        return LocalizedText.of(fallback)
    }
}

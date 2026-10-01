package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

internal object ToolPkgMainRegistrationScriptParser {
    private const val TAG = "ToolPkgMainRegParser"

    fun parse(
        script: String,
        toolPkgId: String,
        mainScriptPath: String,
        apiVersion: String,
        jsEngine: JsEngine
    ): ToolPkgMainRegistrationParseResult {
        return try {
            val captured =
                jsEngine.executeToolPkgMainRegistrationFunction(
                    script = script,
                    functionName = "registerToolPkg",
                    apiVersion = apiVersion,
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
            val floatingWindows = parseRegisteredFloatingWindows(
                captured.floatingWindows,
                uiRoutes
            )
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
            val chatMessageMenuItems =
                parseRegisteredChatMessageMenuItems(
                    registrations = captured.chatMessageMenuItems,
                    registryName = TOOLPKG_REGISTRATION_CHAT_MESSAGE_MENU_ITEM
                )
            val chatRuntimeHooks =
                parseRegisteredFunctionHooks(
                    registrations = captured.chatRuntimeHooks,
                    registryName = TOOLPKG_REGISTRATION_CHAT_RUNTIME_HOOK
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
                        floatingWindows = floatingWindows,
                        appLifecycleHooks = appLifecycleHooks,
                        messageProcessingPlugins = messageProcessingPlugins,
                        xmlRenderPlugins = xmlRenderPlugins,
                        inputMenuTogglePlugins = inputMenuTogglePlugins,
                        chatInputHooks = chatInputHooks,
                        chatViewHooks = chatViewHooks,
                        chatMessageHooks = chatMessageHooks,
                        chatMessageMenuItems = chatMessageMenuItems,
                        chatRuntimeHooks = chatRuntimeHooks,
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

    private fun parseRegisteredFloatingWindows(
        registrations: List<String>,
        uiRoutes: List<ToolPkgRegisteredUiRoute>
    ): List<ToolPkgRegisteredFloatingWindow> {
        val windows = mutableListOf<ToolPkgRegisteredFloatingWindow>()
        val ids = linkedSetOf<String>()
        registrations.forEachIndexed { index, raw ->
            val item =
                try {
                    JSONObject(raw)
                } catch (error: Exception) {
                    throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_FLOATING_WINDOW payload[$index] must be a JSON object",
                        error
                    )
                }
            val id = item.optString("id").trim()
            val contentRouteId = item.optString("contentRoute").trim()
            if (id.isBlank()) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].id is required"
                )
            }
            if (!ids.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate toolpkg floating window id: $id")
            }
            if (contentRouteId.isBlank()) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].contentRoute is required"
                )
            }
            val route =
                uiRoutes.firstOrNull { candidate ->
                    candidate.routeId.equals(contentRouteId, ignoreCase = true)
                } ?: throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].contentRoute not found: $contentRouteId"
                )
            if (!route.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].contentRoute must use compose_dsl: $contentRouteId"
                )
            }
            val widthDp = item.optInt("widthDp", 320)
            val heightDp = item.optInt("heightDp", 420)
            if (widthDp !in 72..1200 || heightDp !in 72..1600) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index] size is outside supported bounds"
                )
            }
            val refreshIntervalMs = item.optLong("refreshIntervalMs", 60_000L)
            if (refreshIntervalMs != 0L && refreshIntervalMs !in 30_000L..86_400_000L) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].refreshIntervalMs is invalid"
                )
            }
            val refreshFunction = item.optString("onRefresh").trim().ifBlank { null }
            val refreshFunctionSource = item.optString("function_source").trim().ifBlank { null }
            if (refreshFunctionSource != null && refreshFunction == null) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].function_source requires onRefresh"
                )
            }
            val snapMode = item.optString("snapMode", "quarter").trim().lowercase()
            if (snapMode !in setOf("none", "quarter")) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].snapMode must be none or quarter"
                )
            }
            val contentLayout = parseFloatingWindowContentLayout(item, index)
            val follow = parseFloatingWindowFollow(item, index)
            val pressFeedback = parseFloatingWindowFeedback(item, "pressFeedback", index)
            val releaseFeedback = parseFloatingWindowFeedback(item, "releaseFeedback", index)
            windows.add(
                ToolPkgRegisteredFloatingWindow(
                    id = id,
                    contentRouteId = route.routeId,
                    title = parseLocalizedText(item.opt("title"), fallback = id),
                    description = parseLocalizedText(item.opt("description"), fallback = ""),
                    icon = item.optString("icon").trim().ifBlank { null },
                    widthDp = widthDp,
                    heightDp = heightDp,
                    draggable = item.optBoolean("draggable", true),
                    resizable = item.optBoolean("resizable", true),
                    snapMode = snapMode,
                    contentLayout = contentLayout,
                    follow = follow,
                    pressFeedback = pressFeedback,
                    releaseFeedback = releaseFeedback,
                    refreshIntervalMs = refreshIntervalMs,
                    refreshFunction = refreshFunction,
                    refreshFunctionSource = refreshFunctionSource
                )
            )
        }
        val windowIds = windows.map { it.id.lowercase() }.toSet()
        windows.forEach { window ->
            val follow = window.follow ?: return@forEach
            require(follow.windowId.lowercase() in windowIds) {
                "$TOOLPKG_REGISTRATION_FLOATING_WINDOW.follow.windowId not found: ${follow.windowId}"
            }
            require(!follow.windowId.equals(window.id, ignoreCase = true)) {
                "$TOOLPKG_REGISTRATION_FLOATING_WINDOW.follow.windowId cannot reference itself: ${window.id}"
            }
        }
        val followById = windows.associate { it.id.lowercase() to it.follow?.windowId?.lowercase() }
        fun visit(windowId: String, path: Set<String>) {
            val next = followById[windowId] ?: return
            require(next !in path) {
                "$TOOLPKG_REGISTRATION_FLOATING_WINDOW follow.windowId cycle detected"
            }
            visit(next, path + windowId)
        }
        followById.keys.forEach { windowId -> visit(windowId, emptySet()) }
        return windows.map { window ->
            val follow = window.follow?.let { value ->
                value.copy(windowId = windows.first { candidate -> candidate.id.equals(value.windowId, ignoreCase = true) }.id)
            }
            window.copy(follow = follow)
        }
    }

    private fun parseFloatingWindowContentLayout(
        item: JSONObject,
        index: Int
    ): ToolPkgFloatingWindowContentLayout {
        val field = "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].contentLayout"
        val layout = item.optJSONObject("contentLayout")
            ?: throw IllegalArgumentException("$field must be a JSON object")
        val mode = layout.optString("mode").trim().lowercase()
        require(mode == "fixed") { "$field.mode must be fixed" }
        val widthDp = layout.optInt("widthDp", -1)
        val heightDp = layout.optInt("heightDp", -1)
        require(widthDp in 1..1200 && heightDp in 1..1600) {
            "$field size is outside supported bounds"
        }
        val scaleMode = layout.optString("scaleMode").trim().lowercase()
        require(scaleMode == "fit") { "$field.scaleMode must be fit" }
        return ToolPkgFloatingWindowContentLayout(
            mode = mode,
            widthDp = widthDp,
            heightDp = heightDp,
            scaleMode = scaleMode
        )
    }

    private fun parseFloatingWindowFollow(
        item: JSONObject,
        index: Int
    ): ToolPkgFloatingWindowFollow? {
        val field = "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].follow"
        if (!item.has("follow") || item.isNull("follow")) return null
        val follow = item.optJSONObject("follow")
            ?: throw IllegalArgumentException("$field must be a JSON object")
        val windowId = follow.optString("windowId").trim()
        require(windowId.isNotBlank()) { "$field.windowId is required" }
        val placement = follow.optString("placement").trim().lowercase()
        require(placement in setOf("above", "below", "start", "end", "center")) {
            "$field.placement is unsupported: $placement"
        }
        val offset = if (!follow.has("offsetDp") || follow.isNull("offsetDp")) {
            null
        } else {
            follow.optJSONObject("offsetDp")
                ?: throw IllegalArgumentException("$field.offsetDp must be a JSON object")
        }
        val offsetX = readFloatingWindowFloat(offset, "x", 0f, "$field.offsetDp.x")
        val offsetY = readFloatingWindowFloat(offset, "y", 0f, "$field.offsetDp.y")
        require(offsetX in -1200f..1200f && offsetY in -1600f..1600f) {
            "$field.offsetDp is outside supported bounds"
        }
        return ToolPkgFloatingWindowFollow(
            windowId = windowId,
            placement = placement,
            offsetXDp = offsetX,
            offsetYDp = offsetY
        )
    }

    private fun parseFloatingWindowFeedback(
        item: JSONObject,
        key: String,
        index: Int
    ): ToolPkgFloatingWindowFeedback {
        val field = "$TOOLPKG_REGISTRATION_FLOATING_WINDOW[$index].$key"
        if (!item.has(key) || item.isNull(key)) return ToolPkgFloatingWindowFeedback()
        val feedback = item.optJSONObject(key)
            ?: throw IllegalArgumentException("$field must be a JSON object")
        val soundResource = if (!feedback.has("soundResource") || feedback.isNull("soundResource")) {
            null
        } else {
            val value = feedback.opt("soundResource")
            require(value is String) { "$field.soundResource must be a string" }
            value.trim().ifBlank { null }
        }
        val animation = if (!feedback.has("animation") || feedback.isNull("animation")) {
            null
        } else {
            val animationJson = feedback.optJSONObject("animation")
                ?: throw IllegalArgumentException("$field.animation must be a JSON object")
            parseFloatingWindowAnimation(animationJson, "$field.animation")
        }
        return ToolPkgFloatingWindowFeedback(
            soundResource = soundResource,
            animation = animation
        )
    }

    private fun parseFloatingWindowAnimation(
        json: JSONObject,
        field: String
    ): ToolPkgFloatingWindowAnimation {
        val scaleX = readFloatingWindowFloat(json, "scaleX", 1f, "$field.scaleX")
        val scaleY = readFloatingWindowFloat(json, "scaleY", 1f, "$field.scaleY")
        val alpha = readFloatingWindowFloat(json, "alpha", 1f, "$field.alpha")
        val translationX = readFloatingWindowFloat(json, "translationXDp", 0f, "$field.translationXDp")
        val translationY = readFloatingWindowFloat(json, "translationYDp", 0f, "$field.translationYDp")
        val durationMs = readFloatingWindowLong(json, "durationMs", 0L, "$field.durationMs")
        val easing = normalizeToolPkgFloatingWindowAnimationEasing(json.optString("easing", "linear"))
        val pivotX = readFloatingWindowFloat(json, "pivotX", 0.5f, "$field.pivotX")
        val pivotY = readFloatingWindowFloat(json, "pivotY", 0.5f, "$field.pivotY")
        require(scaleX in 0f..4f && scaleY in 0f..4f) {
            "$field scale is outside supported bounds"
        }
        require(alpha in 0f..1f) { "$field.alpha is outside supported bounds" }
        require(translationX in -1200f..1200f && translationY in -1600f..1600f) {
            "$field translation is outside supported bounds"
        }
        require(durationMs in 0L..5000L) { "$field.durationMs is outside supported bounds" }
        require(pivotX in 0f..1f && pivotY in 0f..1f) {
            "$field pivot is outside supported bounds"
        }
        return ToolPkgFloatingWindowAnimation(
            scaleX = scaleX,
            scaleY = scaleY,
            alpha = alpha,
            translationXDp = translationX,
            translationYDp = translationY,
            durationMs = durationMs,
            easing = easing,
            pivotX = pivotX,
            pivotY = pivotY
        )
    }

    private fun readFloatingWindowFloat(
        json: JSONObject?,
        key: String,
        default: Float,
        field: String
    ): Float {
        if (json == null || !json.has(key) || json.isNull(key)) return default
        val value = json.opt(key)
        require(value is Number) { "$field must be a number" }
        val result = value.toFloat()
        require(result.isFinite()) { "$field must be finite" }
        return result
    }

    private fun readFloatingWindowLong(
        json: JSONObject,
        key: String,
        default: Long,
        field: String
    ): Long {
        if (!json.has(key) || json.isNull(key)) return default
        val value = json.opt(key)
        require(value is Number) { "$field must be an integer" }
        val doubleValue = value.toDouble()
        require(doubleValue.isFinite() && doubleValue % 1.0 == 0.0) {
            "$field must be an integer"
        }
        return value.toLong()
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

    private fun parseRegisteredChatMessageMenuItems(
        registrations: List<String>,
        registryName: String
    ): List<ToolPkgRegisteredChatMessageMenuItem> {
        val items = mutableListOf<ToolPkgRegisteredChatMessageMenuItem>()
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

            val dialog =
                item.optJSONObject("dialog")?.let { dialogObject ->
                    val screen = dialogObject.optString("screen").trim()
                    if (screen.isBlank()) {
                        throw IllegalArgumentException("$registryName[$index].dialog.screen is required")
                    }
                    ToolPkgRegisteredChatMessageMenuDialog(
                        screen = screen,
                        title = parseLocalizedText(dialogObject.opt("title"), "")
                    )
                }

            items.add(
                ToolPkgRegisteredChatMessageMenuItem(
                    id = id,
                    title = parseLocalizedText(item.opt("title"), id),
                    icon = item.optString("icon").trim().ifBlank { null },
                    order = item.optInt("order", 0),
                    senders = parseStringList(item.opt("senders")),
                    function = functionName,
                    functionSource = functionSource,
                    dialog = dialog
                )
            )
        }
        return items
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

    private fun parseStringList(raw: Any?): List<String> {
        return when (raw) {
            is JSONArray ->
                buildList {
                    for (index in 0 until raw.length()) {
                        raw.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            is String -> raw.trim().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
            else -> emptyList()
        }
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

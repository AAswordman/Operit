package com.ai.assistance.operit.plugins.toolpkg

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingController
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingExecution
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingHookParams
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingPlugin
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingPluginRegistry
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_INPUT_MENU_TOGGLE
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_MESSAGE_PROCESSING
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_XML_RENDER
import com.ai.assistance.operit.plugins.OperitPlugin
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPlugin
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.ui.common.markdown.XmlRenderResult
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleDefinition
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleHookParams
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuTogglePlugin
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuTogglePluginRegistry
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.core.tools.packTool.ToolPkgXmlRenderHookComposeDslResult
import com.ai.assistance.operit.core.tools.packTool.ToolPkgXmlRenderHookObjectResult
import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionErrorMessage
import com.ai.assistance.operit.util.stream.stream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private const val TAG = "ToolPkgCommonBridge"
private const val TOOLPKG_LOG_TAG = "ToolPkg"

private fun packageManager(context: Context): PackageManager {
    return PackageManager.getInstance(context, AIToolHandler.getInstance(context))
}

private fun decodeHookResult(raw: Any?): Any? {
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

private fun logPreview(text: String, maxLength: Int = 160): String {
    val normalized =
        text.replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
}

private fun summarizeHookValue(value: Any?): String {
    return when (value) {
        null -> "null"
        else -> "${value::class.java.simpleName}(${logPreview(value.toString())})"
    }
}

private object ToolPkgMessageProcessingBridgePlugin : MessageProcessingPlugin {
    override val id: String = "builtin.toolpkg.message-processing-bridge"
    @Volatile
    private var hooks: List<ToolPkgMessageProcessingHookRegistration> = emptyList()

    internal fun replaceHooks(updatedHooks: List<ToolPkgMessageProcessingHookRegistration>) {
        hooks = updatedHooks
    }

    override suspend fun createExecutionIfMatched(
        params: MessageProcessingHookParams
    ): MessageProcessingExecution? {
        val totalStartTime = messageTimingNow()
        val manager = packageManager(params.context)
        val loadHooksStartTime = messageTimingNow()
        val registeredHooks = hooks
        logMessageTiming(
            stage = "toolpkg.messageProcessing.loadHooks",
            startTimeMs = loadHooksStartTime,
            details = "hooks=${registeredHooks.size}"
        )

        val buildPayloadStartTime = messageTimingNow()
        val baseEventPayload = buildMessageEventPayload(params = params, probeOnly = false)
        val probeEventPayload = buildMessageEventPayload(params = params, probeOnly = true)
        logMessageTiming(
            stage = "toolpkg.messageProcessing.buildPayload",
            startTimeMs = buildPayloadStartTime,
            details = "history=${params.chatHistory.size}, messageLength=${params.messageContent.length}"
        )

        for ((index, hook) in registeredHooks.withIndex()) {
            val hookProbeStartTime = messageTimingNow()
            val hookKey = "${hook.containerPackageName}:${hook.pluginId}"
            val probeDecoded =
                runMessageProcessingHook(
                    manager = manager,
                    hook = hook,
                    eventPayload = probeEventPayload
                )
            if (probeDecoded == null) {
                logMessageTiming(
                    stage = "toolpkg.messageProcessing.probeHook",
                    startTimeMs = hookProbeStartTime,
                    details = "index=$index, hook=$hookKey, matched=false, decoded=null"
                )
                continue
            }

            val parseProbeResultStartTime = messageTimingNow()
            val probeResult = parseMessageProcessingResult(probeDecoded)
            logMessageTiming(
                stage = "toolpkg.messageProcessing.parseProbeResult",
                startTimeMs = parseProbeResultStartTime,
                details = "index=$index, hook=$hookKey, matched=${probeResult?.matched == true}, chunks=${probeResult?.chunks?.size ?: 0}"
            )
            if (probeResult == null || !probeResult.matched) {
                logMessageTiming(
                    stage = "toolpkg.messageProcessing.probeHook",
                    startTimeMs = hookProbeStartTime,
                    details = "index=$index, hook=$hookKey, matched=false"
                )
                continue
            }

            logMessageTiming(
                stage = "toolpkg.messageProcessing.probeHook",
                startTimeMs = hookProbeStartTime,
                details = "index=$index, hook=$hookKey, matched=true, chunks=${probeResult.chunks.size}"
            )

            val createExecutionStartTime = messageTimingNow()
            val execution = createStreamingExecution(
                manager = manager,
                hook = hook,
                eventPayload = baseEventPayload,
                executionId = nextMessageProcessingExecutionId(hook)
            )
            logMessageTiming(
                stage = "toolpkg.messageProcessing.createExecution",
                startTimeMs = createExecutionStartTime,
                details = "index=$index, hook=$hookKey"
            )
            logMessageTiming(
                stage = "toolpkg.messageProcessing.matchTotal",
                startTimeMs = totalStartTime,
                details = "hooks=${registeredHooks.size}, matchedHook=$hookKey, index=$index"
            )
            return execution
        }

        logMessageTiming(
            stage = "toolpkg.messageProcessing.matchTotal",
            startTimeMs = totalStartTime,
            details = "hooks=${registeredHooks.size}, matchedHook=none"
        )
        return null
    }

    private fun buildMessageEventPayload(
        params: MessageProcessingHookParams,
        probeOnly: Boolean
    ): Map<String, Any?> {
        return mapOf(
            "chatId" to params.chatId,
            "messageContent" to params.messageContent,
            "chatHistory" to params.chatHistory.map(::promptTurnToMap),
            "workspacePath" to params.workspacePath,
            "maxTokens" to params.maxTokens,
            "tokenUsageThreshold" to params.tokenUsageThreshold,
            "probeOnly" to probeOnly
        )
    }

    private fun promptTurnToMap(turn: PromptTurn): Map<String, Any?> {
        return mapOf(
            "kind" to turn.kind.name,
            "content" to turn.content,
            "toolName" to turn.toolName,
            "metadata" to turn.metadata
        )
    }

    private suspend fun runMessageProcessingHook(
        manager: PackageManager,
        hook: ToolPkgMessageProcessingHookRegistration,
        eventPayload: Map<String, Any?>,
        onIntermediateResult: ((Any?) -> Unit)? = null
    ): Any? {
        val totalStartTime = messageTimingNow()
        val hookKey = "${hook.containerPackageName}:${hook.pluginId}"
        val isProbeOnly = eventPayload["probeOnly"] == true

        val runMainHookStartTime = messageTimingNow()
        val result =
            withContext(Dispatchers.IO) {
                manager.runToolPkgMainHook(
                    containerPackageName = hook.containerPackageName,
                    functionName = hook.functionName,
                    event = TOOLPKG_EVENT_MESSAGE_PROCESSING,
                    pluginId = hook.pluginId,
                    inlineFunctionSource = hook.functionSource,
                    eventPayload = eventPayload,
                    onIntermediateResult = onIntermediateResult
                )
            }
        logMessageTiming(
            stage = "toolpkg.messageProcessing.runMainHook",
            startTimeMs = runMainHookStartTime,
            details = "hook=$hookKey, probeOnly=$isProbeOnly, success=${result.isSuccess}"
        )

        val value =
            result.getOrElse { error ->
                AppLogger.e(
                    TAG,
                    "ToolPkg message processing hook failed: ${hook.containerPackageName}:${hook.pluginId}",
                    error
                )
                null
            }
        if (value == null) {
            logMessageTiming(
                stage = "toolpkg.messageProcessing.hookTotal",
                startTimeMs = totalStartTime,
                details = "hook=$hookKey, probeOnly=$isProbeOnly, decoded=false"
            )
            return null
        }

        val decodeHookResultStartTime = messageTimingNow()
        val decoded =
            runCatching { decodeHookResult(value) }
                .getOrElse { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg message processing hook decode failed: ${hook.containerPackageName}:${hook.pluginId}",
                        error
                    )
                    null
                }
        logMessageTiming(
            stage = "toolpkg.messageProcessing.decodeHookResult",
            startTimeMs = decodeHookResultStartTime,
            details = "hook=$hookKey, probeOnly=$isProbeOnly, decoded=${decoded != null}, valueType=${value::class.java.simpleName}"
        )
        logMessageTiming(
            stage = "toolpkg.messageProcessing.hookTotal",
            startTimeMs = totalStartTime,
            details = "hook=$hookKey, probeOnly=$isProbeOnly, decoded=${decoded != null}"
        )
        return decoded
    }

    private fun nextMessageProcessingExecutionId(
        hook: ToolPkgMessageProcessingHookRegistration
    ): String {
        return "toolpkg-msg:${hook.containerPackageName}:${hook.pluginId}:${UUID.randomUUID()}"
    }

    private fun createStreamingExecution(
        manager: PackageManager,
        hook: ToolPkgMessageProcessingHookRegistration,
        eventPayload: Map<String, Any?>,
        executionId: String
    ): MessageProcessingExecution {
        val executionEventPayload =
            LinkedHashMap<String, Any?>(eventPayload).apply {
                put("executionId", executionId)
            }
        val stream =
            stream<String> {
                val chunkQueue = Channel<String>(capacity = Channel.UNLIMITED)
                var emittedAny = false
                coroutineScope {
                    val forwarder =
                        launch {
                            for (chunk in chunkQueue) {
                                emittedAny = true
                                emit(chunk)
                            }
                        }

                    try {
                        val finalDecoded =
                            runMessageProcessingHook(
                                manager = manager,
                                hook = hook,
                                eventPayload = executionEventPayload,
                                onIntermediateResult = { intermediateRaw ->
                                    val intermediateDecoded =
                                        runCatching { decodeHookResult(intermediateRaw) }
                                            .getOrNull() ?: intermediateRaw
                                    extractMessageChunks(intermediateDecoded)
                                        .filter { it.isNotEmpty() }
                                        .forEach { chunk ->
                                            chunkQueue.trySend(chunk)
                                        }
                                }
                            )
                        val parsed = parseMessageProcessingResult(finalDecoded)
                        if (parsed != null && parsed.matched && !emittedAny) {
                            AppLogger.i(
                                TOOLPKG_LOG_TAG,
                                "message-processing final fallback hook=${
                                    hook.containerPackageName
                                }:${hook.pluginId}:${hook.functionName} chunkCount=${parsed.chunks.size}"
                            )
                            parsed.chunks
                                .filter { it.isNotEmpty() }
                                .forEach { chunk ->
                                    chunkQueue.trySend(chunk)
                                }
                        }
                    } finally {
                        chunkQueue.close()
                        forwarder.join()
                        ToolPkgMessageProcessingCancellationRegistry.unregister(executionId)
                    }
                }
            }
        return MessageProcessingExecution(
            RegisteredMessageProcessingController(executionId, hook),
            stream
        )
    }

    private data class ParsedMessageProcessingResult(
        val matched: Boolean,
        val chunks: List<String>
    )

    private fun parseMessageProcessingResult(decoded: Any?): ParsedMessageProcessingResult? {
        return when (decoded) {
            null -> null
            is Boolean ->
                if (decoded) {
                    ParsedMessageProcessingResult(
                        matched = true,
                        chunks = emptyList()
                    )
                } else {
                    null
                }
            is String ->
                if (decoded.isEmpty()) {
                    null
                } else {
                    ParsedMessageProcessingResult(
                        matched = true,
                        chunks = listOf(decoded)
                    )
                }
            is JSONObject -> {
                val matched = decoded.optBoolean("matched", true)
                if (!matched) {
                    return null
                }
                ParsedMessageProcessingResult(
                    matched = true,
                    chunks = extractMessageChunks(decoded)
                )
            }
            else -> null
        }
    }

    private fun extractMessageChunks(decoded: Any?): List<String> {
        return when (decoded) {
            null -> emptyList()
            is String -> {
                if (decoded.isEmpty()) emptyList() else listOf(decoded)
            }
            is JSONObject -> {
                val chunks = mutableListOf<String>()

                if (decoded.has("chunk") && !decoded.isNull("chunk")) {
                    val chunkText = decoded.optString("chunk")
                    if (chunkText.isNotEmpty()) {
                        chunks.add(chunkText)
                    }
                }

                val chunksArray = decoded.optJSONArray("chunks")
                if (chunksArray != null) {
                    for (index in 0 until chunksArray.length()) {
                        val chunk = chunksArray.optString(index)
                        if (chunk.isNotEmpty()) {
                            chunks.add(chunk)
                        }
                    }
                }

                if (decoded.has("text") && !decoded.isNull("text")) {
                    val text = decoded.optString("text")
                    if (text.isNotEmpty()) {
                        chunks.add(text)
                    }
                } else if (decoded.has("content") && !decoded.isNull("content")) {
                    val content = decoded.optString("content")
                    if (content.isNotEmpty()) {
                        chunks.add(content)
                    }
                }
                chunks
            }
            else -> emptyList()
        }
    }
}

private class RegisteredMessageProcessingController(
    private val executionId: String,
    private val hook: ToolPkgMessageProcessingHookRegistration
) : MessageProcessingController {
    override fun cancel() {
        val handled = ToolPkgMessageProcessingCancellationRegistry.cancel(executionId)
        AppLogger.d(
            TAG,
            "Cancel toolpkg message processing execution: ${hook.containerPackageName}:${hook.pluginId}, executionId=$executionId, handled=$handled"
        )
    }
}

private object ToolPkgXmlRenderBridgePlugin : XmlRenderPlugin {
    override val id: String = "builtin.toolpkg.xml-render-bridge"
    @Volatile
    private var hooksByTag: Map<String, List<ToolPkgXmlRenderHookRegistration>> = emptyMap()

    internal fun replaceHooksByTag(
        updatedHooksByTag: Map<String, List<ToolPkgXmlRenderHookRegistration>>
    ) {
        if (hooksByTag == updatedHooksByTag) {
            return
        }
        hooksByTag = updatedHooksByTag
        XmlRenderPluginRegistry.notifyChanged()
    }

    override fun supports(tagName: String): Boolean {
        val normalizedTagName = tagName.trim().lowercase()
        if (normalizedTagName.isBlank()) {
            return false
        }
        return hooksByTag[normalizedTagName].orEmpty().isNotEmpty()
    }

    override suspend fun resolve(
        context: Context,
        xmlContent: String,
        tagName: String,
        textColor: Color,
        xmlStream: Stream<String>?
    ): XmlRenderResult? {
        val manager = packageManager(context)
        val hooks = hooksByTag[tagName.trim().lowercase()].orEmpty()
        for (hook in hooks) {
            val result =
                withContext(Dispatchers.IO) {
                    manager.runToolPkgMainHook(
                        containerPackageName = hook.containerPackageName,
                        functionName = hook.functionName,
                        event = TOOLPKG_EVENT_XML_RENDER,
                        pluginId = hook.pluginId,
                        inlineFunctionSource = hook.functionSource,
                        eventPayload =
                            mapOf(
                                "xmlContent" to xmlContent,
                                "tagName" to tagName
                            )
                    )
                }
            val value =
                result.getOrElse { error ->
                    AppLogger.e(
                        TOOLPKG_LOG_TAG,
                        "xml-render hook failed tag=$tagName hook=${hook.containerPackageName}:${hook.pluginId}:${hook.functionName}",
                        error
                    )
                    return@getOrElse null
                }
            if (value == null) {
                continue
            }
            val decoded =
                runCatching { decodeHookResult(value) }
                    .getOrElse { error ->
                        AppLogger.e(
                            TOOLPKG_LOG_TAG,
                            "xml-render hook decode failed tag=$tagName hook=${hook.containerPackageName}:${hook.pluginId}:${hook.functionName} raw=${summarizeHookValue(value)}",
                            error
                        )
                        null
                    }
            val parsed = parseXmlRenderHookObjectResult(decoded)
            if (parsed == null) {
                continue
            }
            if (parsed.handled == false) {
                continue
            }
            val composeDsl = parsed.composeDsl
            if (composeDsl != null) {
                return XmlRenderResult.ComposeDslScreen(
                    containerPackageName = hook.containerPackageName,
                    screenPath = composeDsl.screen,
                    state = composeDsl.state,
                    memo = composeDsl.memo,
                    moduleSpec = composeDsl.moduleSpec
                )
            }
            val text = parsed.text?.ifBlank { parsed.content.orEmpty() }?.trim().orEmpty()
            if (text.isNotBlank()) {
                return XmlRenderResult.Text(text)
            }
        }
        return null
    }

    private fun parseXmlRenderHookObjectResult(decoded: Any?): ToolPkgXmlRenderHookObjectResult? {
        return when (decoded) {
            null -> null
            is String -> {
                val text = decoded.trim()
                if (text.isBlank()) null else ToolPkgXmlRenderHookObjectResult(handled = true, text = text)
            }
            is JSONObject -> {
                val handled = decoded.optBoolean("handled", true)
                val text = decoded.optString("text").ifBlank { decoded.optString("content") }.trim()
                val composeDslRaw = decoded.opt("composeDsl")
                val composeDsl = parseComposeDslResult(composeDslRaw)
                ToolPkgXmlRenderHookObjectResult(
                    handled = handled,
                    text = text.ifBlank { null },
                    content = decoded.optString("content").trim().ifBlank { null },
                    composeDsl = composeDsl
                )
            }
            else -> null
        }
    }

    private fun parseComposeDslResult(raw: Any?): ToolPkgXmlRenderHookComposeDslResult? {
        val map =
            when (raw) {
                is JSONObject -> raw
                is Map<*, *> -> JSONObject(raw)
                else -> null
            } ?: return null

        val screen = map.optString("screen").trim()
        if (screen.isBlank()) {
            return null
        }

        val state = asMap(map.opt("state"))
        val memo = asMap(map.opt("memo"))
        val moduleSpec = asMap(map.opt("moduleSpec"))

        return ToolPkgXmlRenderHookComposeDslResult(
            screen = screen,
            state = state,
            memo = memo,
            moduleSpec = if (moduleSpec.isNotEmpty()) moduleSpec else null
        )
    }

    private fun asMap(value: Any?): Map<String, Any?> {
        return when (value) {
            is JSONObject -> {
                val map = linkedMapOf<String, Any?>()
                value.keys().forEach { key ->
                    map[key] = normalizeValue(value.opt(key))
                }
                map
            }
            is Map<*, *> -> {
                value.entries.associate { entry ->
                    entry.key.toString() to normalizeValue(entry.value)
                }
            }
            else -> emptyMap()
        }
    }

    private fun asList(value: Any?): List<Any?> {
        return when (value) {
            is JSONArray -> {
                buildList {
                    for (index in 0 until value.length()) {
                        add(normalizeValue(value.opt(index)))
                    }
                }
            }
            is List<*> -> value.map { normalizeValue(it) }
            else -> emptyList()
        }
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> asMap(value)
            is JSONArray -> asList(value)
            else -> value
        }
    }
}

private object ToolPkgInputMenuToggleBridgePlugin : InputMenuTogglePlugin {
    override val id: String = "builtin.toolpkg.input-menu-toggle-bridge"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var hooks: List<ToolPkgInputMenuToggleHookRegistration> = emptyList()
    @Volatile
    private var specsCache: List<InputMenuSpec> = emptyList()
    @Volatile
    private var hasLoadedOnce = false
    @Volatile
    private var hookRegistryVersion = 0L
    @Volatile
    private var lastHookRegistryVersion = -1L
    @Volatile
    private var lastParamsCacheKey: String? = null
    private val refreshFlag = AtomicBoolean(false)

    internal fun replaceHooks(updatedHooks: List<ToolPkgInputMenuToggleHookRegistration>) {
        if (hooks == updatedHooks) {
            return
        }
        hooks = updatedHooks
        specsCache = emptyList()
        hasLoadedOnce = false
        lastParamsCacheKey = null
        hookRegistryVersion += 1L
        InputMenuTogglePluginRegistry.notifyChanged()
    }

    override fun createToggles(
        params: InputMenuToggleHookParams
    ): List<InputMenuToggleDefinition> {
        val registryVersion = hookRegistryVersion
        val paramsCacheKey = buildCacheKey(params)
        val shouldRefreshForParams = lastParamsCacheKey != paramsCacheKey
        if (shouldRefreshForParams) {
            specsCache = emptyList()
            hasLoadedOnce = false
        }
        val cachedSpecs = specsCache
        if (shouldRefreshForParams || !hasLoadedOnce || lastHookRegistryVersion != registryVersion) {
            triggerRefresh(
                params = params,
                registryVersion = registryVersion,
                paramsCacheKey = paramsCacheKey
            )
            if (cachedSpecs.isEmpty()) {
                return listOf(createLoadingToggle())
            }
        }
        return buildToggleDefinitions(cachedSpecs, params)
    }

    private fun buildCacheKey(params: InputMenuToggleHookParams): String {
        val runtime = params.runtime.orEmpty()
        val chatId = params.chatId.orEmpty()
        return "$runtime|$chatId"
    }

    private fun buildToggleDefinitions(
        specs: List<InputMenuSpec>,
        params: InputMenuToggleHookParams
    ): List<InputMenuToggleDefinition> {
        if (specs.isEmpty()) {
            return emptyList()
        }
        return specs.map { spec ->
            val resolvedChecked = params.featureStates[spec.id] ?: spec.isChecked
            InputMenuToggleDefinition(
                id = spec.id,
                title = spec.title,
                description = spec.description,
                icon = spec.icon,
                isChecked = resolvedChecked,
                slot = spec.slot,
                onToggle = {
                    if (params.featureStates.containsKey(spec.id)) {
                        params.onToggleFeature(spec.id)
                        return@InputMenuToggleDefinition
                    }
                    scope.launch {
                        val manager = packageManager(params.context)
                        manager.runToolPkgMainHook(
                            containerPackageName = spec.containerPackageName,
                            functionName = spec.functionName,
                            event = TOOLPKG_EVENT_INPUT_MENU_TOGGLE,
                            pluginId = spec.pluginId,
                            inlineFunctionSource = spec.functionSource,
                            eventPayload =
                                mapOf(
                                    "action" to "toggle",
                                    "toggleId" to spec.id,
                                    "chatId" to params.chatId,
                                    "runtime" to params.runtime
                                )
                        )
                        triggerRefresh(
                            params = params,
                            registryVersion = hookRegistryVersion,
                            paramsCacheKey = buildCacheKey(params)
                        )
                    }
                }
            )
        }
    }

    private fun createLoadingToggle(): InputMenuToggleDefinition {
        return InputMenuToggleDefinition(
            id = "toolpkg_input_menu_loading",
            titleRes = R.string.loading,
            descriptionRes = R.string.loading,
            isChecked = false,
            isEnabled = false,
            onToggle = {}
        )
    }

    private fun triggerRefresh(
        params: InputMenuToggleHookParams,
        registryVersion: Long,
        paramsCacheKey: String
    ) {
        if (!refreshFlag.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            try {
                val resolved =
                    runCatching { loadSpecs(params = params) }
                        .getOrElse { error ->
                            AppLogger.e(TAG, "ToolPkg input menu refresh failed", error)
                            emptyList()
                        }
                specsCache = resolved
                hasLoadedOnce = true
                lastHookRegistryVersion = registryVersion
                lastParamsCacheKey = paramsCacheKey
            } finally {
                refreshFlag.set(false)
                InputMenuTogglePluginRegistry.notifyChanged()
            }
        }
    }

    private fun loadSpecs(params: InputMenuToggleHookParams): List<InputMenuSpec> {
        val manager = packageManager(params.context)
        val registeredHooks = hooks
        val resolved = mutableListOf<InputMenuSpec>()
        registeredHooks.forEach { hook ->
            val result =
                manager.runToolPkgMainHook(
                    containerPackageName = hook.containerPackageName,
                    functionName = hook.functionName,
                    event = TOOLPKG_EVENT_INPUT_MENU_TOGGLE,
                    pluginId = hook.pluginId,
                    inlineFunctionSource = hook.functionSource,
                    eventPayload =
                        mapOf(
                            "action" to "create",
                            "chatId" to params.chatId,
                            "runtime" to params.runtime
                        )
                )
            val value =
                result.getOrElse { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg input menu hook failed: ${hook.containerPackageName}:${hook.pluginId}",
                        error
                    )
                    return@getOrElse null
                } ?: return@forEach
            val decoded =
                runCatching { decodeHookResult(value) }
                    .getOrElse { error ->
                        AppLogger.e(
                            TAG,
                            "ToolPkg input menu hook decode failed: ${hook.containerPackageName}:${hook.pluginId}",
                            error
                        )
                        null
                    }
            resolved.addAll(
                parseInputMenuDefinitions(
                    decoded = decoded,
                    containerPackageName = hook.containerPackageName,
                    functionName = hook.functionName,
                    pluginId = hook.pluginId,
                    functionSource = hook.functionSource
                )
            )
        }
        return resolved
    }

    private data class InputMenuSpec(
        val containerPackageName: String,
        val functionName: String,
        val pluginId: String,
        val functionSource: String?,
        val id: String,
        val title: String,
        val description: String,
        val icon: String?,
        val isChecked: Boolean,
        val slot: String?
    )

    private fun parseInputMenuDefinitions(
        decoded: Any?,
        containerPackageName: String,
        functionName: String,
        pluginId: String,
        functionSource: String?
    ): List<InputMenuSpec> {
        val array =
            when (decoded) {
                is JSONArray -> decoded
                is JSONObject -> decoded.optJSONArray("toggles")
                else -> null
            } ?: return emptyList()

        val specs = mutableListOf<InputMenuSpec>()
        for (index in 0 until array.length()) {
            val item = array.opt(index) as? JSONObject ?: continue
            val id = item.optString("id").trim()
            val title = item.optString("title").trim()
            if (id.isBlank() || title.isBlank()) {
                continue
            }
            specs.add(
                InputMenuSpec(
                    containerPackageName = containerPackageName,
                    functionName = functionName,
                    pluginId = pluginId,
                    functionSource = functionSource,
                    id = id,
                    title = title,
                    description = item.optString("description").trim(),
                    icon = item.optString("icon").trim().takeIf { it.isNotEmpty() },
                    isChecked = item.optBoolean("isChecked", false),
                    slot = item.optString("slot").trim().takeIf { it.isNotEmpty() }
                )
            )
        }
        return specs
    }
}

object ToolPkgCommonBridgePlugin : OperitPlugin {
    override val id: String = "builtin.toolpkg.common-bridge"
    private val installed = AtomicBoolean(false)
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    override fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        MessageProcessingPluginRegistry.register(ToolPkgMessageProcessingBridgePlugin)
        XmlRenderPluginRegistry.register(ToolPkgXmlRenderBridgePlugin)
        InputMenuTogglePluginRegistry.register(ToolPkgInputMenuToggleBridgePlugin)
        ToolPkgPromptHookBridge.register()
        ToolPkgSummaryHookBridge.register()
        ToolPkgToolLifecycleBridge.register()
        ToolPkgChatInputHookBridge.register()
        ToolPkgChatViewHookBridge.register()
        ToolPkgChatMessageHookBridge.register()
        ToolPkgChatRuntimeStateBridge.register()
        ToolPkgAiProviderRegistry.register()

        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
    }

    private fun syncToolPkgRegistrations(
        activeContainers: List<ToolPkgContainerRuntime>
    ) {
        val messageHooks =
            activeContainers.flatMap { runtime ->
                runtime.messageProcessingPlugins.map { hook ->
                    ToolPkgMessageProcessingHookRegistration(
                        containerPackageName = runtime.packageName,
                        pluginId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedWith(
                compareBy(
                    ToolPkgMessageProcessingHookRegistration::containerPackageName,
                    ToolPkgMessageProcessingHookRegistration::pluginId
                )
            )

        val xmlHooksByTag =
            activeContainers.flatMap { runtime ->
                runtime.xmlRenderPlugins.mapNotNull { hook ->
                    val normalizedTag = hook.tag.trim().lowercase()
                    if (normalizedTag.isBlank()) {
                        null
                    } else {
                        ToolPkgXmlRenderHookRegistration(
                            containerPackageName = runtime.packageName,
                            pluginId = hook.id,
                            tag = normalizedTag,
                            functionName = hook.function,
                            functionSource = hook.functionSource
                        )
                    }
                }
            }
                .groupBy(ToolPkgXmlRenderHookRegistration::tag)
                .mapValues { (_, hooks) ->
                    hooks.sortedWith(
                        compareBy(
                            ToolPkgXmlRenderHookRegistration::containerPackageName,
                            ToolPkgXmlRenderHookRegistration::pluginId
                        )
                    )
                }

        val inputMenuHooks =
            activeContainers.flatMap { runtime ->
                runtime.inputMenuTogglePlugins.map { hook ->
                    ToolPkgInputMenuToggleHookRegistration(
                        containerPackageName = runtime.packageName,
                        pluginId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedWith(
                compareBy(
                    ToolPkgInputMenuToggleHookRegistration::containerPackageName,
                    ToolPkgInputMenuToggleHookRegistration::pluginId
                )
            )

        val chatRuntimeStateHooks =
            activeContainers.flatMap { runtime ->
                runtime.chatRuntimeStateHooks.map { hook ->
                    ToolPkgChatRuntimeStateHookRegistration(
                        containerPackageName = runtime.packageName,
                        hookId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedWith(
                compareBy(
                    ToolPkgChatRuntimeStateHookRegistration::containerPackageName,
                    ToolPkgChatRuntimeStateHookRegistration::hookId
                )
            )

        ToolPkgMessageProcessingBridgePlugin.replaceHooks(messageHooks)
        ToolPkgXmlRenderBridgePlugin.replaceHooksByTag(xmlHooksByTag)
        ToolPkgInputMenuToggleBridgePlugin.replaceHooks(inputMenuHooks)
        ToolPkgChatRuntimeStateBridge.replaceHooks(chatRuntimeStateHooks)
    }
}

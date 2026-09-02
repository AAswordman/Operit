package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.AIToolHookDecision
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.climode.CliToolModeSupport
import com.ai.assistance.operit.core.tools.climode.ToolExposureMode
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.data.preferences.CharacterCardToolAccessResolver
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** Utility class for managing tool executions */
object ToolExecutionManager {
    private const val TAG = "ToolExecutionManager"
    private const val PACKAGE_PROXY_TOOL_NAME = "package_proxy"
    private const val PACKAGE_CALLER_NAME_PARAM = "__operit_package_caller_name"
    private const val PACKAGE_CHAT_ID_PARAM = "__operit_package_chat_id"
    private const val PACKAGE_CALLER_CARD_ID_PARAM = "__operit_package_caller_card_id"
    private const val MAX_TOOL_RESULT_DISPLAY_CHARS_PER_TURN = 256 * 1024
    private const val MAX_TOOL_RESULT_DISPLAY_CHARS_PER_INVOCATION = 16 * 1024
    private const val MIN_TOOL_RESULT_DISPLAY_CHARS = 1_024
    private const val COMPACT_RESULT_MESSAGE =
        "[工具结果已从聊天显示中省略，以避免长任务占用过多内存。]"
    private const val AGGREGATED_TOOL_RESULT_TRUNCATION_SUFFIX =
        "\n[工具返回了多个结果，后续内容已截断。]"
    private val toolRuntimeContextThreadLocal = ThreadLocal<ToolRuntimeContext?>()

    data class ToolRuntimeContext(
        val callerCardId: String? = null,
        val toolExposureMode: ToolExposureMode = ToolExposureMode.FULL
    )

    private data class ResolvedToolTarget(
        val tool: AITool,
        val displayName: String
    )

    private data class OrderedToolInvocation(
        val index: Int,
        val invocation: ToolInvocation,
    )
    internal data class ToolResultDisplayMarkup(

        val full: String,
        val compact: String,
    )

    private fun ToolResult.toCompactDisplayResult(): ToolResult {
        return if (success) {
            copy(result = StringResultData(COMPACT_RESULT_MESSAGE))
        } else {
            copy(
                result = StringResultData(""),
                error = error.orEmpty().take(256).ifBlank { COMPACT_RESULT_MESSAGE },
            )
        }
    }

    private fun formatToolResultDisplayMarkup(
        result: ToolResult,
        maxFullMarkupChars: Int,
    ): ToolResultDisplayMarkup {
        return ToolResultDisplayMarkup(
            full =
                ConversationMarkupManager.formatToolResultForMessage(
                    result = result,
                    maxMessageChars = maxFullMarkupChars,
                ),
            compact =
                ConversationMarkupManager.formatToolResultForMessage(
                    result = result.toCompactDisplayResult(),
                    maxMessageChars = MIN_TOOL_RESULT_DISPLAY_CHARS,
                ),
        )
    }

    /** Buffers one invocation's display markup until ordered emission is safe. */
    internal class ToolResultMarkupBuffer {
        private val markups = mutableListOf<ToolResultDisplayMarkup>()
        private var retainedMarkupChars = 0
        private var lastOverflowMarkup: ToolResultDisplayMarkup? = null

        fun record(result: ToolResult) {
            val remainingChars =
                MAX_TOOL_RESULT_DISPLAY_CHARS_PER_INVOCATION - retainedMarkupChars
            if (remainingChars >= MIN_TOOL_RESULT_DISPLAY_CHARS) {
                val markup =
                    formatToolResultDisplayMarkup(
                        result = result,
                        maxFullMarkupChars = remainingChars,
                    )
                markups += markup
                retainedMarkupChars += markup.full.length
            } else {
                // Preserve the latest terminal state even when intermediate events fill the budget.
                lastOverflowMarkup =
                    formatToolResultDisplayMarkup(
                        result = result,
                        maxFullMarkupChars = MIN_TOOL_RESULT_DISPLAY_CHARS,
                    )
            }
        }

        suspend fun flushTo(
            collector: StreamCollector<String>,
            onDisplayMarkupEmitted: suspend (String) -> Unit,
            displayBudget: ToolResultDisplayBudget,
            emissionMutex: Mutex? = null,
        ) {
            val orderedMarkups =
                buildList {
                    addAll(markups)
                    lastOverflowMarkup?.let(::add)
                }
            for (markup in orderedMarkups) {
                ToolExecutionManager.emitToolResultMarkup(
                    content = displayBudget.select(markup),
                    collector = collector,
                    onDisplayMarkupEmitted = onDisplayMarkupEmitted,
                    emissionMutex = emissionMutex,
                )
            }
            markups.clear()
            lastOverflowMarkup = null
            retainedMarkupChars = 0
        }
    }
    class ToolResultDisplayBudget {

        private val mutex = Mutex()
        private var remainingChars = MAX_TOOL_RESULT_DISPLAY_CHARS_PER_TURN
        internal suspend fun select(markup: ToolResultDisplayMarkup): String = mutex.withLock {

            val selected =
                if (markup.full.length <= remainingChars) {
                    markup.full
                } else {
                    markup.compact
                }
            remainingChars = (remainingChars - selected.length).coerceAtLeast(0)
            selected
        }
    }

    /** Emits completed invocation buffers in original invocation order without blocking execution. */
    internal class OrderedToolResultEmitter(
        private val collector: StreamCollector<String>,
        private val onDisplayMarkupEmitted: suspend (String) -> Unit,
        private val displayBudget: ToolResultDisplayBudget = ToolResultDisplayBudget(),
    ) {
        private val mutex = Mutex()

        private val completedBuffers = mutableMapOf<Int, ToolResultMarkupBuffer>()
        private var nextInvocationIndex = 0

        suspend fun complete(index: Int, buffer: ToolResultMarkupBuffer) {
            mutex.withLock {
                completedBuffers[index] = buffer
                while (true) {
                    val nextBuffer = completedBuffers.remove(nextInvocationIndex) ?: break
                    nextBuffer.flushTo(
                        collector = collector,
                        onDisplayMarkupEmitted = onDisplayMarkupEmitted,
                        displayBudget = displayBudget,
                    )
                    nextInvocationIndex++
                }
            }
        }
    }

    private fun appendBoundedToolResult(
        builder: StringBuilder,
        result: ToolResult,
    ) {
        val nextContent =
            (if (result.success) {
                result.result.toString()
            } else {
                "Step error: ${result.error ?: "Unknown error"}"
            }).trim()
        if (nextContent.isEmpty()) {
            return
        }

        val separator = if (builder.isEmpty()) "" else "\n"
        val remaining =
            ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS - builder.length - separator.length
        if (remaining <= 0) {
            return
        }
        builder.append(separator)
        if (nextContent.length <= remaining) {
            builder.append(nextContent)
            return
        }

        val suffix = AGGREGATED_TOOL_RESULT_TRUNCATION_SUFFIX
        if (remaining <= suffix.length) {
            builder.append(suffix.take(remaining))
        } else {
            builder.append(nextContent.take(remaining - suffix.length).trimEnd())
            builder.append(suffix)
        }
    }

    private fun ensureEndsWithNewline(content: String): String {
        return if (content.endsWith("\n")) content else "$content\n"
    }

    internal suspend fun emitToolResultMarkup(
        content: String,
        collector: StreamCollector<String>,
        onDisplayMarkupEmitted: suspend (String) -> Unit,
        emissionMutex: Mutex? = null,
    ) {
        val displayContent = ensureEndsWithNewline(content)
        suspend fun emitAtomically() {
            onDisplayMarkupEmitted(displayContent)
            collector.emit(displayContent)
        }
        if (emissionMutex == null) {
            emitAtomically()
        } else {
            emissionMutex.withLock { emitAtomically() }
        }
    }

    internal fun resolveRuntimeErrorCode(result: ToolResult): String =
        result.errorCode
            ?: if (result.error?.contains("Invalid parameter", ignoreCase = true) == true) {
                "invalid_arguments"
            } else {
                "tool_execution_failed"
            }

    private fun resolveToolTarget(tool: AITool): ResolvedToolTarget {
        if (tool.name != PACKAGE_PROXY_TOOL_NAME &&
            tool.name != CliToolModeSupport.PROXY_TOOL_NAME
        ) {
            return ResolvedToolTarget(tool = tool, displayName = tool.name)
        }

        val targetToolName = tool.parameters
            .firstOrNull { it.name == "tool_name" }
            ?.value
            ?.trim()
            .orEmpty()
        if (targetToolName.isBlank()) {
            return ResolvedToolTarget(tool = tool, displayName = tool.name)
        }

        val forwardedParameters = resolveProxyParameters(tool)
        return ResolvedToolTarget(
            tool = AITool(name = targetToolName, parameters = forwardedParameters),
            displayName = targetToolName
        )
    }

    private fun resolveDisplayToolName(tool: AITool): String {
        return resolveToolTarget(tool).displayName
    }

    private fun isJsPackageTool(toolName: String, jsPackageNames: Set<String>): Boolean {
        val toolNameParts = toolName.split(':', limit = 2)
        val packageName = toolNameParts.getOrNull(0)
        return toolNameParts.size == 2 &&
            packageName != null &&
            jsPackageNames.contains(packageName)
    }

    internal fun currentToolRuntimeContext(): ToolRuntimeContext? =
        toolRuntimeContextThreadLocal.get()

    private fun addPackageContextParamIfMissing(
        params: MutableList<ToolParameter>,
        name: String,
        value: String?
    ) {
        if (value.isNullOrBlank()) {
            return
        }
        if (params.any { it.name == name }) {
            return
        }
        params.add(ToolParameter(name, value))
    }

    private fun injectPackageCallContext(
        invocation: ToolInvocation,
        jsPackageNames: Set<String>,
        callerName: String?,
        callerChatId: String?,
        callerCardId: String?
    ): ToolInvocation {
        val resolvedTargetTool = resolveToolTarget(invocation.tool).tool
        if (!isJsPackageTool(resolvedTargetTool.name, jsPackageNames)) {
            return invocation
        }

        val updatedParams = invocation.tool.parameters.toMutableList()
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CALLER_NAME_PARAM, callerName)
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CHAT_ID_PARAM, callerChatId)
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CALLER_CARD_ID_PARAM, callerCardId)

        if (updatedParams.size == invocation.tool.parameters.size) {
            return invocation
        }

        return invocation.copy(
            tool = invocation.tool.copy(parameters = updatedParams)
        )
    }

    private fun getParameterValue(tool: AITool, name: String): String? {
        return tool.parameters.firstOrNull { it.name == name }?.value?.trim()
    }

    private fun isInvocationAllowedForRoleCard(
        invocation: ToolInvocation,
        roleCardToolAccess: com.ai.assistance.operit.data.preferences.ResolvedCharacterCardToolAccess
    ): Boolean {
        val toolName = invocation.tool.name.trim()
        val resolvedTarget = resolveToolTarget(invocation.tool).tool

        return when {
            toolName == CliToolModeSupport.SEARCH_TOOL_NAME -> true

            toolName == CliToolModeSupport.PROXY_TOOL_NAME -> {
                isResolvedTargetAllowedForRoleCard(resolvedTarget, roleCardToolAccess)
            }

            toolName == "use_package" -> {
                if (!roleCardToolAccess.isBuiltinToolAllowed("use_package")) {
                    false
                } else {
                    val sourceName = getParameterValue(invocation.tool, "package_name").orEmpty()
                    sourceName.isBlank() || roleCardToolAccess.isExternalSourceAllowed(sourceName)
                }
            }

            toolName == PACKAGE_PROXY_TOOL_NAME -> {
                if (!roleCardToolAccess.isBuiltinToolAllowed("package_proxy")) {
                    false
                } else {
                    val resolvedTargetName = resolvedTarget.name.trim()
                    if (resolvedTargetName.isBlank() || !resolvedTargetName.contains(':')) {
                        true
                    } else {
                        isResolvedTargetAllowedForRoleCard(resolvedTarget, roleCardToolAccess)
                    }
                }
            }

            toolName.contains(':') -> {
                val sourceName = toolName.substringBefore(':').trim()
                sourceName.isBlank() || roleCardToolAccess.isExternalSourceAllowed(sourceName)
            }

            else -> roleCardToolAccess.isBuiltinToolAllowed(toolName)
        }
    }

    private fun buildRoleCardDeniedResult(
        context: Context,
        invocation: ToolInvocation
    ): ToolResult {
        return ToolResult(
            toolName = resolveDisplayToolName(invocation.tool),
            success = false,
            result = StringResultData(""),
            error = context.getString(R.string.character_card_tool_access_denied_runtime),
            errorCode = "permission_denied"
        )
    }

    private fun isEnglishLanguage(context: Context): Boolean {
        return LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
    }

    private fun buildToolExposureDeniedResult(
        context: Context,
        invocation: ToolInvocation,
        toolExposureMode: ToolExposureMode
    ): ToolResult? {
        val toolName = invocation.tool.name.trim()
        val useEnglish = isEnglishLanguage(context)
        val errorMessage =
            when {
                toolExposureMode == ToolExposureMode.CLI &&
                    !CliToolModeSupport.isCliPublicTool(toolName) -> {
                    CliToolModeSupport.buildCliTopLevelRestrictionErrorMessage(
                        attemptedToolName = resolveDisplayToolName(invocation.tool),
                        useEnglish = useEnglish
                    )
                }

                toolExposureMode == ToolExposureMode.FULL &&
                    CliToolModeSupport.isCliPublicTool(toolName) -> {
                    CliToolModeSupport.buildCliModeUnavailableMessage(useEnglish)
                }

                else -> null
            } ?: return null

        val resultToolName =
            if (toolExposureMode == ToolExposureMode.CLI &&
                !CliToolModeSupport.isCliPublicTool(toolName)
            ) {
                resolveDisplayToolName(invocation.tool)
            } else {
                toolName
            }

        return ToolResult(
            toolName = resultToolName,
            success = false,
            result = StringResultData(""),
            error = errorMessage,
            errorCode = "permission_denied"
        )
    }

    private fun isResolvedTargetAllowedForRoleCard(
        resolvedTarget: AITool,
        roleCardToolAccess: com.ai.assistance.operit.data.preferences.ResolvedCharacterCardToolAccess
    ): Boolean {
        val resolvedTargetName = resolvedTarget.name.trim()
        if (resolvedTargetName.isBlank()) {
            return true
        }

        val usePackageSourceName =
            if (resolvedTargetName == "use_package") {
                getParameterValue(resolvedTarget, "package_name")
            } else {
                null
            }

        return CliToolModeSupport.isToolNameAllowedForRoleCard(
            toolName = resolvedTargetName,
            usePackageSourceName = usePackageSourceName,
            roleCardToolAccess = roleCardToolAccess
        )
    }

    private fun resolveProxyParameters(tool: AITool): List<ToolParameter> {
        val paramsRaw = tool.parameters
            .firstOrNull { it.name == "params" }
            ?.value
            ?.trim()
            .orEmpty()
        if (paramsRaw.isBlank()) {
            return emptyList()
        }

        val paramsObject = runCatching { JSONObject(paramsRaw) }.getOrNull() ?: return emptyList()
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
        return forwardedParameters
    }

    /**
     * 从 AI 响应中提取工具调用。
     * @param response AI 的响应字符串。
     * @return 检测到的工具调用列表。
     */
    suspend fun extractToolInvocations(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        val content = response

        val charStream = content.stream()
        val plugins = listOf(StreamXmlPlugin())

        charStream.splitBy(plugins).collect { group ->
            val chunkContent = StringBuilder()
            group.stream.collect { chunk -> chunkContent.append(chunk) }
            val chunkString = chunkContent.toString()

            if (chunkString.isEmpty()) return@collect

            if (group.tag is StreamXmlPlugin) {
                ChatMarkupRegex.toolCallPattern.findAll(chunkString).forEach { toolMatch ->
                    val toolName = toolMatch.groupValues.getOrNull(2) ?: return@forEach
                    val toolBody = toolMatch.groupValues.getOrNull(3).orEmpty()

                    val parameters = mutableListOf<ToolParameter>()
                    MessageContentParser.toolParamPattern.findAll(toolBody)
                        .forEach { paramMatch ->
                            val paramName = paramMatch.groupValues[1]
                            val paramValue = paramMatch.groupValues[2]
                            parameters.add(ToolParameter(paramName, unescapeXml(paramValue)))
                        }

                    val tool = AITool(name = toolName, parameters = parameters)
                    invocations.add(
                        ToolInvocation(
                            tool = tool,
                            rawText = toolMatch.value,
                            responseLocation = toolMatch.range
                        )
                    )
                }
            }
        }

        AppLogger.d(
            TAG,
            "Found ${invocations.size} tool invocations: ${invocations.map { resolveDisplayToolName(it.tool) }}"
        )
        return invocations
    }

    /**
     * Unescapes XML special characters
     * @param input The XML escaped string
     * @return Unescaped string
     */
    private fun unescapeXml(input: String): String {
        var result = input

        // 处理 CDATA 标记
        if (result.startsWith("<![CDATA[") && result.endsWith("]]>")) {
            result = result.substring(9, result.length - 3)
        }

        // 即使没有完整的 CDATA 标记，也尝试清理末尾的 ]]> 和开头的 <![CDATA[
        if (result.endsWith("]]>")) {
            result = result.substring(0, result.length - 3)
        }

        if (result.startsWith("<![CDATA[")) {
            result = result.substring(9)
        }

        return result.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    /**
     * Execute a tool safely, with parameter validation
     *
     * @param invocation The tool invocation to execute
     * @param executor The tool executor to use
     * @return The result of the tool execution
     */
    fun executeToolSafely(
        invocation: ToolInvocation,
        executor: ToolExecutor,
        toolHandler: AIToolHandler? = null
    ): Flow<ToolResult> {
        val validationResult = executor.validateParameters(invocation.tool)
        if (!validationResult.valid) {
            return flow {
                emit(
                    ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid parameters: ${validationResult.errorMessage}",
                        errorCode = "invalid_arguments"
                    )
                )
            }
        }

        return executor.invokeAndStream(invocation.tool).catch { e ->
            AppLogger.e(TAG, "Tool execution error: ${invocation.tool.name}", e)
            toolHandler?.notifyToolExecutionError(invocation.tool, e)
            emit(
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Tool execution error: ${e.message}",
                    errorCode = "tool_execution_failed"
                )
            )
        }
    }

    /**
     * Check if a tool requires permission and verify if it has permission
     *
     * @param toolHandler The AIToolHandler instance to use for permission checks
     * @param invocation The tool invocation to check permissions for
     * @return A pair containing (has permission, error result if no permission)
     */
    suspend fun checkToolPermission(
        toolHandler: AIToolHandler,
        invocation: ToolInvocation,
        toolExposureMode: ToolExposureMode = ToolExposureMode.FULL,
        callerChatId: String? = null
    ): Pair<Boolean, ToolResult?> {
        val resolvedTarget = resolveToolTarget(invocation.tool)
        val permissionTool =
            if (toolExposureMode == ToolExposureMode.CLI &&
                invocation.tool.name == CliToolModeSupport.PROXY_TOOL_NAME
            ) {
                invocation.tool
            } else {
                resolvedTarget.tool
            }

        if (toolExposureMode == ToolExposureMode.CLI &&
            (invocation.tool.name == CliToolModeSupport.SEARCH_TOOL_NAME ||
                invocation.tool.name == CliToolModeSupport.PROXY_TOOL_NAME)
        ) {
            toolHandler.notifyToolPermissionChecked(
                permissionTool,
                granted = true,
                reason = "CLI public tool"
            )
            return Pair(true, null)
        }

        // 检查是否强制拒绝权限（deny_tool标记）
        val hasPromptForPermission = !invocation.rawText.contains("deny_tool")

        if (hasPromptForPermission) {
            // 检查权限，如果需要则弹出权限请求界面
            val toolPermissionSystem = toolHandler.getToolPermissionSystem()
            val permissionResult = toolPermissionSystem.checkToolPermission(
                permissionTool,
                chatId = callerChatId
            )

            if (!permissionResult.isGranted) {
                val errorMessage = requireNotNull(permissionResult.errorMessage)
                val errorCode = requireNotNull(permissionResult.errorCode)
                val errorResult =
                    ToolResult(
                        toolName = resolvedTarget.displayName,
                        success = false,
                        result = StringResultData(""),
                        error = errorMessage,
                        errorCode = errorCode
                    )
                toolHandler.notifyToolPermissionChecked(
                    permissionTool,
                    granted = false,
                    reason = errorMessage
                )
                return Pair(false, errorResult)
            }

            toolHandler.notifyToolPermissionChecked(permissionTool, granted = true)
            return Pair(true, null)
        }

        toolHandler.notifyToolPermissionChecked(
            permissionTool,
            granted = true,
            reason = "Permission check bypassed by deny_tool tag."
        )
        return Pair(true, null)
    }

    /**
     *
     * 执行工具调用，包括权限检查、并行/串行执行和结果聚合。
     * @param invocations 要执行的工具调用列表。
     * @param toolHandler AIToolHandler 的实例。
     * @param packageManager PackageManager 的实例。
     * @param collector 用于实时输出结果的 StreamCollector。
     * @return 所有工具执行结果的列表。
     */
    suspend fun executeInvocations(
        invocations: List<ToolInvocation>,
        context: Context,
        toolHandler: AIToolHandler,
        packageManager: PackageManager,
        collector: StreamCollector<String>,
        toolExposureMode: ToolExposureMode = ToolExposureMode.FULL,
        callerName: String? = null,
        callerChatId: String? = null,
        callerCardId: String? = null,
        onToolExecutionStarted: (suspend (String) -> Unit)? = null,
        onDisplayMarkupEmitted: suspend (String) -> Unit = {},
        displayBudget: ToolResultDisplayBudget = ToolResultDisplayBudget(),
    ): List<ToolResult> = coroutineScope {
        val orderedResultEmitter =
            OrderedToolResultEmitter(
                collector = collector,
                onDisplayMarkupEmitted = onDisplayMarkupEmitted,
                displayBudget = displayBudget,
            )
        val orderedInvocations =
            invocations.mapIndexed { index, invocation ->
                OrderedToolInvocation(index = index, invocation = invocation)
            }
        val resultBuffersByIndex = ConcurrentHashMap<Int, ToolResultMarkupBuffer>()
        val resultsByIndex = ConcurrentHashMap<Int, ToolResult>()

        fun bufferFor(index: Int): ToolResultMarkupBuffer =
            resultBuffersByIndex.getOrPut(index) { ToolResultMarkupBuffer() }
        // 默认工具注册现在可能在启动阶段被延后；这里确保在真正执行工具前已完成注册
        // registerDefaultTools() 是幂等且线程安全的，可安全重复调用
        withContext(Dispatchers.Default) {
            toolHandler.registerDefaultTools()
        }

        val roleCardToolAccess = if (callerCardId.isNullOrBlank()) {
            null
        } else {
            runCatching {
                CharacterCardToolAccessResolver
                    .getInstance(context)
                    .resolve(callerCardId, packageManager)
            }.onFailure { error ->
                AppLogger.e(TAG, "角色卡工具权限解析失败: callerCardId=$callerCardId", error)
            }.getOrNull()
        }
        val toolRuntimeContext =
            ToolRuntimeContext(
                callerCardId = callerCardId,
                toolExposureMode = toolExposureMode
            )

        // Every parsed invocation enters the same request/finish lifecycle, including denials.
        orderedInvocations.forEach { orderedInvocation ->
            toolHandler.notifyToolCallRequested(orderedInvocation.invocation.tool)
        }

        // 1. 顶层工具暴露模式拦截

        val toolExposurePermittedInvocations = mutableListOf<OrderedToolInvocation>()
        for (orderedInvocation in orderedInvocations) {
            val invocation = orderedInvocation.invocation
            val deniedResult = buildToolExposureDeniedResult(context, invocation, toolExposureMode)
            if (deniedResult == null) {
                toolExposurePermittedInvocations.add(orderedInvocation)
            } else {
                resultsByIndex[orderedInvocation.index] = deniedResult
                val resultBuffer = bufferFor(orderedInvocation.index)
                toolHandler.notifyToolExecutionResult(invocation.tool, deniedResult)
                toolHandler.notifyToolExecutionFinished(invocation.tool)
                resultBuffer.record(deniedResult)
                orderedResultEmitter.complete(orderedInvocation.index, resultBuffer)
            }
        }
        // 2. 角色卡工具权限拦截（优先于权限弹窗与包自动激活）

        val roleCardPermittedInvocations = mutableListOf<OrderedToolInvocation>()
        for (orderedInvocation in toolExposurePermittedInvocations) {
            val invocation = orderedInvocation.invocation
            val deniedResult =
                if (
                    roleCardToolAccess?.customEnabled == true &&
                        !isInvocationAllowedForRoleCard(invocation, roleCardToolAccess)
                ) {
                    buildRoleCardDeniedResult(context, invocation)
                } else {
                    null
                }

            if (deniedResult == null) {
                roleCardPermittedInvocations.add(orderedInvocation)
            } else {
                resultsByIndex[orderedInvocation.index] = deniedResult
                val resultBuffer = bufferFor(orderedInvocation.index)
                toolHandler.notifyToolExecutionResult(invocation.tool, deniedResult)
                toolHandler.notifyToolExecutionFinished(invocation.tool)
                resultBuffer.record(deniedResult)
                orderedResultEmitter.complete(orderedInvocation.index, resultBuffer)
            }
        }

        // 3. Hook 拦截与权限检查
        val permittedInvocations = mutableListOf<OrderedToolInvocation>()
        for (orderedInvocation in roleCardPermittedInvocations) {
            val invocation = orderedInvocation.invocation
            val interceptionTool = resolveToolTarget(invocation.tool).tool

            when (val interception = toolHandler.checkToolInterception(interceptionTool)) {
                AIToolHookDecision.Allow -> {
                    val (hasPermission, errorResult) =
                        checkToolPermission(
                            toolHandler = toolHandler,
                            invocation = invocation,
                            toolExposureMode = toolExposureMode,
                            callerChatId = callerChatId,
                        )
                    if (hasPermission) {
                        permittedInvocations.add(orderedInvocation)
                    } else {
                        val deniedResult =
                            errorResult
                                ?: ToolResult(
                                    toolName = resolveDisplayToolName(invocation.tool),
                                    success = false,
                                    result = StringResultData(""),
                                    error = "Tool permission was denied.",
                                    errorCode = "permission_denied",
                                )
                        resultsByIndex[orderedInvocation.index] = deniedResult
                        val resultBuffer = bufferFor(orderedInvocation.index)
                        toolHandler.notifyToolExecutionResult(invocation.tool, deniedResult)
                        toolHandler.notifyToolExecutionFinished(invocation.tool)
                        resultBuffer.record(deniedResult)
                        orderedResultEmitter.complete(orderedInvocation.index, resultBuffer)
                    }
                }

                is AIToolHookDecision.Block -> {
                    val interceptedResult =
                        toolHandler.buildToolInterceptionResult(
                            resolveDisplayToolName(invocation.tool),
                            interception,
                        )
                    resultsByIndex[orderedInvocation.index] = interceptedResult
                    val resultBuffer = bufferFor(orderedInvocation.index)
                    toolHandler.notifyToolExecutionResult(invocation.tool, interceptedResult)
                    toolHandler.notifyToolExecutionFinished(invocation.tool)
                    resultBuffer.record(interceptedResult)
                    orderedResultEmitter.complete(orderedInvocation.index, resultBuffer)
                }
            }
        }

        val injectedInvocations =
            if (
                callerName.isNullOrBlank() &&
                    callerChatId.isNullOrBlank() &&
                    callerCardId.isNullOrBlank()
            ) {
                permittedInvocations
            } else {
                val jsPackageNames = packageManager.getAvailablePackages().keys
                permittedInvocations.map { orderedInvocation ->
                    orderedInvocation.copy(
                        invocation =
                            injectPackageCallContext(
                                invocation = orderedInvocation.invocation,
                                jsPackageNames = jsPackageNames,
                                callerName = callerName,
                                callerChatId = callerChatId,
                                callerCardId = callerCardId,
                            ),
                    )
                }
            }

        // 4. 按并行/串行对工具进行分组
        val parallelizableToolNames = setOf(
            "list_files", "read_file", "read_file_part", "read_file_full", "file_exists",
            "find_files", "file_info", "grep_code", "calculate", "ffmpeg_info",
            "visit_web", "download_file"
        )
        val (parallelInvocations, serialInvocations) = injectedInvocations.partition {
            parallelizableToolNames.contains(it.invocation.tool.name)
        }

        // 5. 执行工具并缓冲每个调用的显示结果。

        // 启动并行工具。它们仍然并行执行，但不会按完成顺序直接写入聊天流。
        val parallelJobs = parallelInvocations.map { orderedInvocation ->
            async {
                val resultBuffer = bufferFor(orderedInvocation.index)
                val result =
                    executeAndEmitTool(
                        invocation = orderedInvocation.invocation,
                        toolHandler = toolHandler,
                        packageManager = packageManager,
                        runtimeContext = toolRuntimeContext,
                        onToolExecutionStarted = onToolExecutionStarted,
                        resultBuffer = resultBuffer,
                    )
                resultsByIndex[orderedInvocation.index] = result
                orderedResultEmitter.complete(orderedInvocation.index, resultBuffer)
            }
        }

        // 串行工具保持原始调用顺序；显示结果同样先缓冲。
        for (orderedInvocation in serialInvocations) {
            val resultBuffer = bufferFor(orderedInvocation.index)
            val result =
                executeAndEmitTool(
                    invocation = orderedInvocation.invocation,
                    toolHandler = toolHandler,
                    packageManager = packageManager,
                    runtimeContext = toolRuntimeContext,
                    onToolExecutionStarted = onToolExecutionStarted,
                    resultBuffer = resultBuffer,
                )
            resultsByIndex[orderedInvocation.index] = result
            orderedResultEmitter.complete(orderedInvocation.index, resultBuffer)
        }

        // Wait for execution completion before returning results to the model.
        // Display emission itself is incremental through orderedResultEmitter.
        parallelJobs.awaitAll()
        orderedInvocations.mapNotNull { resultsByIndex[it.index] }
    }

    /**
     * 封装单个工具的执行、实时输出和结果聚合的辅助函数
     */
    private suspend fun executeAndEmitTool(
        invocation: ToolInvocation,
        toolHandler: AIToolHandler,
        packageManager: PackageManager,
        runtimeContext: ToolRuntimeContext,
        onToolExecutionStarted: (suspend (String) -> Unit)?,
        resultBuffer: ToolResultMarkupBuffer,
    ): ToolResult {
        val toolName = invocation.tool.name
        val displayToolName = resolveDisplayToolName(invocation.tool)

        return withContext(toolRuntimeContextThreadLocal.asContextElement(runtimeContext)) {
            try {
                val executor = toolHandler.getToolExecutorOrActivate(toolName)
                if (executor == null) {
                    // 如果仍然为 null，则构建错误消息
                    val errorMessage =
                        buildToolNotAvailableErrorMessage(toolName, packageManager, toolHandler)
                    val notAvailableResult =
                        ToolResult(
                            toolName = displayToolName,
                            success = false,
                            result = StringResultData(""),
                            error = errorMessage,
                        )
                    resultBuffer.record(notAvailableResult)
                    toolHandler.notifyToolExecutionResult(invocation.tool, notAvailableResult)
                    return@withContext notAvailableResult
                }

                onToolExecutionStarted?.invoke(displayToolName)
                toolHandler.notifyToolExecutionStarted(invocation.tool)

                var lastResult: ToolResult? = null
                val combinedResultBuilder = StringBuilder()
                executeToolSafely(invocation, executor, toolHandler).collect { result ->
                    lastResult = result
                    appendBoundedToolResult(combinedResultBuilder, result)
                    resultBuffer.record(result)
                }

                // 为此调用聚合最终结果
                if (lastResult == null) {
                    val emptyResult =
                        ToolResult(
                            toolName = displayToolName,
                            success = false,
                            result = StringResultData(""),
                            error = "The tool execution returned no results."
                        )
                    resultBuffer.record(emptyResult)
                    toolHandler.notifyToolExecutionResult(invocation.tool, emptyResult)
                    return@withContext emptyResult
                }

                val finalStepResult = checkNotNull(lastResult)
                val finalResult =
                    ToolResult(
                        toolName = displayToolName,
                        success = finalStepResult.success,
                        result = StringResultData(combinedResultBuilder.toString()),
                        error = finalStepResult.error,
                        errorCode = finalStepResult.errorCode,
                    )
                toolHandler.notifyToolExecutionResult(invocation.tool, finalResult)
                return@withContext finalResult
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(TAG, "Tool execution setup error: ${invocation.tool.name}", error)
                val unexpectedFailure =
                    ToolResult(
                        toolName = displayToolName,
                        success = false,
                        result = StringResultData(""),
                        error = "Tool execution error: ${error.message ?: error.javaClass.simpleName}",
                        errorCode = "tool_execution_failed",
                    )
                resultBuffer.record(unexpectedFailure)
                toolHandler.notifyToolExecutionError(invocation.tool, error)
                toolHandler.notifyToolExecutionResult(invocation.tool, unexpectedFailure)
                return@withContext unexpectedFailure
            } finally {
                toolHandler.notifyToolExecutionFinished(invocation.tool)
            }
        }
    }

    /**
     * 构建工具不可用的错误信息，统一逻辑避免重复
     */
    private suspend fun buildToolNotAvailableErrorMessage(
        toolName: String,
        packageManager: PackageManager,
        toolHandler: AIToolHandler
    ): String {
        return when {
            toolName.contains('.') && !toolName.contains(':') -> {
                val parts = toolName.split('.', limit = 2)
                "Tool invocation syntax error: for tools inside a package, use the 'packName:toolName' format instead of '${toolName}'. You may want to call '${parts.getOrNull(0)}:${parts.getOrNull(1)}'."
            }

            toolName.contains(':') -> {
                val parts = toolName.split(':', limit = 2)
                val packName = parts[0]
                val toolNamePart = parts.getOrNull(1) ?: ""
                val isJsPackageAvailable = packageManager.getAvailablePackages().containsKey(packName)
                val isMcpServerAvailable = packageManager.getAvailableServerPackages().containsKey(packName)
                val isAvailable = isJsPackageAvailable || isMcpServerAvailable

                if (!isAvailable) {
                    "The tool package or MCP server '$packName' does not exist."
                } else {
                    // 包存在，检查是否已激活（通过检查该包的任何工具是否已注册）
                    val packageTools =
                        packageManager.getPackageTools(packName)?.tools ?: emptyList()
                    val isAdviceTool = packageTools.any { it.advice && it.name == toolNamePart }
                    val isPackageActivated = packageTools
                        .filter { !it.advice }
                        .any { toolHandler.getToolExecutor("$packName:${it.name}") != null }

                    if (isAdviceTool) {
                        "Tool '$toolNamePart' is an advice-only entry in package '$packName' and is not executable."
                    } else if (isPackageActivated) {
                        // 包已激活但工具不存在
                        "Tool '$toolNamePart' does not exist in tool package '$packName'. Please use the 'use_package' tool and specify package name '$packName' to list all available tools in this package."
                    } else {
                        // 包未激活
                        "Tool package '$packName' is not activated. Auto-activation was attempted but failed, or tool '$toolNamePart' does not exist. Please use 'use_package' with package name '$packName' to check available tools."
                    }
                }
            }

            else -> {
                // 检查是否直接把包名当作工具名调用了
                val isPackageName = packageManager.getAvailablePackages().containsKey(toolName)
                if (isPackageName) {
                    "Error: '$toolName' is a tool package, not a tool. Please use the 'use_package' tool with package name '$toolName' to activate this package before using its tools."
                } else {
                    "Tool '${toolName}' is unavailable or does not exist. If this is a tool inside a package, call it using the 'packName:toolName' format."
                }
            }
        }
    }

}

package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatTrace
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.AIToolHookDecision
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.climode.CliToolModeSupport
import com.ai.assistance.operit.core.tools.climode.ToolExposureMode
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.data.preferences.CharacterCardToolAccessResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import org.json.JSONException

/** Utility class for managing tool executions */
object ToolExecutionManager {
    private const val TAG = "ToolExecutionManager"
    private const val PACKAGE_PROXY_TOOL_NAME = "package_proxy"
    private const val PACKAGE_CALLER_NAME_PARAM = "__operit_package_caller_name"
    private const val PACKAGE_CHAT_ID_PARAM = "__operit_package_chat_id"
    private const val PACKAGE_CALLER_CARD_ID_PARAM = "__operit_package_caller_card_id"
    private const val TOOL_SESSION_ID_PARAM = "__operit_tool_session_id"
    private val toolRuntimeContextThreadLocal = ThreadLocal<ToolRuntimeContext?>()

    data class ToolRuntimeContext(
        val callerCardId: String? = null,
        val toolExposureMode: ToolExposureMode = ToolExposureMode.FULL,
        val callerChatId: String? = null,
        val toolSessionScopeId: String? = null,
        val subtaskExecution: SubtaskExecutionState? = null,
    )

    internal sealed class PackageContextInjection {
        data class Ready(val invocation: ToolInvocation) : PackageContextInjection()
        data class Rejected(val result: ToolResult) : PackageContextInjection()
    }

    private data class ResolvedToolTarget(
        val tool: AITool,
        val displayName: String
    )

    /**
     * Tool round markup is injected into the assistant stream right after model text that usually
     * ends mid-line. The XML block detector only opens a block at a line boundary, so markup glued
     * onto the previous character is rendered as plain model text, and is re-parsed out of history
     * as assistant text instead of a tool result. Keep every injected block on its own line.
     */
    internal fun ensureOwnLine(content: String): String {
        val withLeadingBreak = if (content.startsWith("\n")) content else "\n$content"
        return if (withLeadingBreak.endsWith("\n")) withLeadingBreak else "$withLeadingBreak\n"
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

    internal fun injectPackageCallContext(
        invocation: ToolInvocation,
        jsPackageNames: Set<String>,
        callerName: String?,
        callerChatId: String?,
        callerCardId: String?,
        toolSessionScopeId: String?,
        enforceCallerIdentity: Boolean,
    ): ToolInvocation {
        val resolvedTargetTool = if (enforceCallerIdentity) {
            resolveSubtaskTarget(invocation.tool)
        } else {
            resolveToolTarget(invocation.tool).tool
        }
        if (!isJsPackageTool(resolvedTargetTool.name, jsPackageNames)) {
            return invocation
        }

        val updatedParams = invocation.tool.parameters.toMutableList()
        // Session identity is host-owned. Remove both outer and proxy-JSON overrides from the model.
        val authoritativeNames = mutableSetOf(TOOL_SESSION_ID_PARAM)
        if (enforceCallerIdentity) {
            authoritativeNames.add(PACKAGE_CHAT_ID_PARAM)
            authoritativeNames.add(PACKAGE_CALLER_CARD_ID_PARAM)
        }
        updatedParams.removeAll { it.name in authoritativeNames }
        if (invocation.tool.name == PACKAGE_PROXY_TOOL_NAME ||
            invocation.tool.name == CliToolModeSupport.PROXY_TOOL_NAME
        ) {
            val names = updatedParams.filter { it.name == "tool_name" }
            val payloads = updatedParams.filter { it.name == "params" }
            require(names.size == 1) { "Exactly one tool_name parameter is required" }
            require(payloads.size == 1) { "Exactly one params parameter is required" }
            val rawParams = payloads.single()
            run {
                fun injectPayload(paramsObject: JSONObject, targetName: String, depth: Int): JSONObject {
                    require(depth <= 8) { "Subtask proxy nesting exceeds 8 levels" }
                    authoritativeNames.forEach { paramsObject.remove(it) }
                    toolSessionScopeId?.let { paramsObject.put(TOOL_SESSION_ID_PARAM, it) }
                    if (enforceCallerIdentity) {
                        callerChatId?.let { paramsObject.put(PACKAGE_CHAT_ID_PARAM, it) }
                        callerCardId?.let { paramsObject.put(PACKAGE_CALLER_CARD_ID_PARAM, it) }
                        if (targetName == PACKAGE_PROXY_TOOL_NAME || targetName == CliToolModeSupport.PROXY_TOOL_NAME) {
                            val nestedTarget = paramsObject.getString("tool_name")
                            val nestedParams = JSONObject(paramsObject.get("params").toString())
                            paramsObject.put("params", injectPayload(nestedParams, nestedTarget, depth + 1))
                        }
                    }
                    return paramsObject
                }
                val directTarget = names.single().value.trim()
                val paramsObject = injectPayload(JSONObject(rawParams.value), directTarget, 1)
                updatedParams.removeAll { it.name == "params" }
                updatedParams.add(ToolParameter("params", paramsObject.toString()))
            }
        }
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CALLER_NAME_PARAM, callerName)
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CHAT_ID_PARAM, callerChatId)
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CALLER_CARD_ID_PARAM, callerCardId)
        addPackageContextParamIfMissing(updatedParams, TOOL_SESSION_ID_PARAM, toolSessionScopeId)

        return invocation.copy(
            tool = invocation.tool.copy(parameters = updatedParams)
        )
    }

    /** Input errors belong to this tool's result slot, not to the enclosing conversation. */
    internal fun preparePackageCallContext(
        invocation: ToolInvocation,
        jsPackageNames: Set<String>,
        callerName: String?,
        runtimeContext: ToolRuntimeContext,
    ): PackageContextInjection {
        fun rejected(message: String) = PackageContextInjection.Rejected(ToolResult(
            toolName = resolveDisplayToolName(invocation.tool),
            success = false,
            result = StringResultData(""),
            error = message,
        ))
        return try {
            PackageContextInjection.Ready(injectPackageCallContext(
                invocation = invocation,
                jsPackageNames = jsPackageNames,
                callerName = callerName,
                callerChatId = runtimeContext.callerChatId,
                callerCardId = runtimeContext.callerCardId,
                toolSessionScopeId = runtimeContext.toolSessionScopeId,
                enforceCallerIdentity = runtimeContext.subtaskExecution != null,
            ))
        } catch (_: JSONException) {
            rejected("params must be a valid JSON object")
        } catch (error: IllegalArgumentException) {
            rejected(requireNotNull(error.message))
        }
    }

    internal fun isSubtaskDelegationTarget(tool: AITool, containerPackageName: String?): Boolean {
        val resolved = resolveSubtaskTarget(tool)
        val packageName = resolved.name.substringBefore(':')
        val functionName = resolved.name.substringAfter(':', "")
        return functionName == "subagent_run" &&
            (packageName.equals("subagent", ignoreCase = true) ||
                packageName.equals("com.operit.subagent_bundle", ignoreCase = true) ||
                containerPackageName.equals("com.operit.subagent_bundle", ignoreCase = true))
    }

    /** A delegated run must inspect the real leaf, including proxy calls nested inside proxy params. */
    internal fun resolveSubtaskTarget(tool: AITool): AITool {
        var target = tool
        repeat(9) { depth ->
            if (target.name != PACKAGE_PROXY_TOOL_NAME && target.name != CliToolModeSupport.PROXY_TOOL_NAME) {
                return target
            }
            require(depth < 8) { "Subtask proxy nesting exceeds 8 levels" }
            val names = target.parameters.filter { it.name == "tool_name" }
            val payloads = target.parameters.filter { it.name == "params" }
            require(names.size == 1 && names.single().value.isNotBlank() && payloads.size == 1) {
                "Subtask proxy requires exactly one non-empty tool_name and one params object"
            }
            val payload = JSONObject(payloads.single().value)
            val parameters = mutableListOf<ToolParameter>()
            val keys = payload.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val value = payload.get(name)
                parameters.add(ToolParameter(name, if (value === JSONObject.NULL) "null" else value.toString()))
            }
            target = AITool(names.single().value.trim(), parameters)
        }
        throw IllegalArgumentException("Subtask proxy nesting exceeds 8 levels")
    }

    internal fun withSubtaskEvidence(result: ToolResult, recordId: String): ToolResult = result.copy(
        result = StringResultData("Subtask evidence ID: $recordId\n${result.result}"),
    )

    /** Collect one admitted model call, including cancellation before its final flow emission. */
    internal suspend fun collectSubtaskToolResult(
        state: SubtaskExecutionState,
        tool: AITool,
        displayToolName: String,
        source: () -> Flow<ToolResult>,
    ): ToolResult {
        var lastRawResult: ToolResult? = null
        var recorded = false
        fun record(result: ToolResult): SubtaskExecutionState.RecordedResult {
            // recordToolResult stores before invoking the observer. An observer failure must not
            // create a second final record for a tool that already has evidence in the ledger.
            recorded = true
            return state.recordToolResult(tool, result, executed = true)
        }
        try {
            val aggregated = aggregateToolResults(displayToolName, source().onEach { lastRawResult = it })
            val raw = lastRawResult
            val evidence = if (raw == null) {
                state.stop(SubtaskExecutionState.Reason.EXECUTION_ERROR, aggregated.error)
                record(aggregated)
            } else {
                record(raw)
            }
            return withSubtaskEvidence(aggregated, evidence.id)
        } catch (error: Exception) {
            val cancelled = error is CancellationException
            state.stop(
                if (cancelled) SubtaskExecutionState.Reason.CANCELLED else SubtaskExecutionState.Reason.EXECUTION_ERROR,
                error.message,
            )
            if (!recorded) {
                val previous = lastRawResult
                val partial = if (previous == null) StringResultData("") else previous.result
                record(ToolResult(
                    toolName = displayToolName,
                    success = false,
                    result = partial,
                    error = if (cancelled) "Tool execution cancelled" else error.message,
                ))
            }
            throw error
        }
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
            error = context.getString(R.string.character_card_tool_access_denied_runtime)
        )
    }

    private fun isEnglishLanguage(context: Context): Boolean {
        return !LocaleUtils.usesChineseContent(context)
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
            error = errorMessage
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
            "Found ${invocations.size} tool invocations: ${invocations.map { resolveDisplayToolName(it.tool) }}, response=${ChatTrace.describe(response)}"
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
        toolHandler: AIToolHandler? = null,
        parametersValidated: Boolean = false,
    ): Flow<ToolResult> {
        if (!parametersValidated) {
            val validationResult = executor.validateParameters(invocation.tool)
            if (!validationResult.valid) {
                return flow {
                    emit(
                        ToolResult(
                            toolName = invocation.tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Invalid parameters: ${validationResult.errorMessage}"
                        )
                    )
                }
            }
        }

        return executor.invokeAndStream(invocation.tool).catch { e ->
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "Tool execution error: ${invocation.tool.name}", e)
            toolHandler?.notifyToolExecutionError(invocation.tool, e)
            emit(
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Tool execution error: ${e.message}"
                )
            )
        }
    }

    /**
     * Check if a tool requires permission and verify if it has permission
     *
     * @param androidContext Android context used to resolve permission error messages
     * @param toolHandler The AIToolHandler instance to use for permission checks
     * @param invocation The tool invocation to check permissions for
     * @return A pair containing (has permission, error result if no permission)
     */
    suspend fun checkToolPermission(
        androidContext: Context,
        toolHandler: AIToolHandler,
        invocation: ToolInvocation,
        toolExposureMode: ToolExposureMode = ToolExposureMode.FULL
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
            val permissionResult = toolPermissionSystem.checkToolPermission(permissionTool)

            if (!permissionResult.isGranted) {
                val errorMessage =
                    androidContext.getString(requireNotNull(permissionResult.errorMessageResId))
                val errorResult =
                    ToolResult(
                        toolName = resolvedTarget.displayName,
                        success = false,
                        result = StringResultData(""),
                        error = errorMessage
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
     * @param collector 按原始调用顺序输出最终结果的 StreamCollector。
     * @return 有序结果及已经输出的 XML，供循环内模型历史复用。
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
        subtaskExecution: SubtaskExecutionState? = null,
    ): ToolExecutionBatch = coroutineScope {
        val orderedResults = OrderedToolResults(invocations.size) { markup ->
            collector.emit(ensureOwnLine(markup))
        }
        val subtaskTargets = mutableMapOf<Int, AITool>()
        suspend fun completeUnexecuted(
            index: Int,
            invocation: ToolInvocation,
            result: ToolResult,
            reason: SubtaskExecutionState.Reason,
        ) {
            var modelResult = result
            subtaskExecution?.let { state ->
                state.stop(reason, result.error)
                val recorded = state.recordToolResult(checkNotNull(subtaskTargets[index]), result, executed = false)
                modelResult = withSubtaskEvidence(result, recorded.id)
            }
            orderedResults.complete(index, modelResult)
        }
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
                if (subtaskExecution != null) throw error
            }.getOrNull()
        }
        val toolRuntimeContext =
            ToolRuntimeContext(
                callerCardId = callerCardId,
                toolExposureMode = toolExposureMode,
                callerChatId = callerChatId,
                toolSessionScopeId = subtaskExecution?.toolSessionScopeId ?: callerChatId,
                subtaskExecution = subtaskExecution,
            )

        // 1. 顶层工具暴露模式拦截
        val toolExposurePermittedInvocations = mutableListOf<IndexedValue<ToolInvocation>>()
        for ((index, invocation) in invocations.withIndex()) {
            if (subtaskExecution != null) {
                val target = try {
                    resolveSubtaskTarget(invocation.tool)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    subtaskTargets[index] = invocation.tool
                    val invalid = ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid subtask proxy: ${error.message}",
                    )
                    completeUnexecuted(index, invocation, invalid, SubtaskExecutionState.Reason.EXECUTION_ERROR)
                    continue
                }
                subtaskTargets[index] = target
                val packageName = target.name.substringBefore(':')
                val container = packageManager.resolveToolPkgSubpackageRuntimeInternal(packageName)
                    ?.containerPackageName
                if (isSubtaskDelegationTarget(invocation.tool, container)) {
                    val result = ToolResult(
                        toolName = target.name,
                        success = false,
                        result = StringResultData(""),
                        error = "A subtask cannot delegate another subagent task",
                    )
                    completeUnexecuted(index, invocation, result, SubtaskExecutionState.Reason.DELEGATION_DENIED)
                    continue
                }
                if (subtaskExecution.isStopped()) {
                    val result = ToolResult(
                        toolName = target.name,
                        success = false,
                        result = StringResultData(""),
                        error = subtaskExecution.stopMessage(),
                    )
                    completeUnexecuted(index, invocation, result, SubtaskExecutionState.Reason.EXECUTION_ERROR)
                    continue
                }
            }
            val deniedResult = buildToolExposureDeniedResult(context, invocation, toolExposureMode)
            if (deniedResult == null) {
                toolExposurePermittedInvocations.add(IndexedValue(index, invocation))
            } else {
                toolHandler.notifyToolExecutionResult(invocation.tool, deniedResult)
                completeUnexecuted(index, invocation, deniedResult, SubtaskExecutionState.Reason.PERMISSION_DENIED)
            }
        }

        // 2. 角色卡工具权限拦截（优先于权限弹窗与包自动激活）
        val roleCardPermittedInvocations = mutableListOf<IndexedValue<ToolInvocation>>()
        for ((index, invocation) in toolExposurePermittedInvocations) {
            val deniedResult = if (roleCardToolAccess?.customEnabled == true &&
                !isInvocationAllowedForRoleCard(invocation, roleCardToolAccess)
            ) {
                buildRoleCardDeniedResult(context, invocation)
            } else {
                null
            }

            if (deniedResult == null) {
                roleCardPermittedInvocations.add(IndexedValue(index, invocation))
            } else {
                toolHandler.notifyToolExecutionResult(invocation.tool, deniedResult)
                completeUnexecuted(index, invocation, deniedResult, SubtaskExecutionState.Reason.PERMISSION_DENIED)
            }
        }

        // 3. Hook 拦截与权限检查
        val permittedInvocations = mutableListOf<IndexedValue<ToolInvocation>>()
        for ((index, invocation) in roleCardPermittedInvocations) {
            if (subtaskExecution?.isStopped() == true) {
                val blocked = ToolResult(
                    toolName = checkNotNull(subtaskTargets[index]).name,
                    success = false,
                    result = StringResultData(""),
                    error = subtaskExecution.stopMessage(),
                )
                completeUnexecuted(index, invocation, blocked, SubtaskExecutionState.Reason.EXECUTION_ERROR)
                continue
            }
            toolHandler.notifyToolCallRequested(invocation.tool)
            val interceptionTool = resolveToolTarget(invocation.tool).tool
            when (val interception = toolHandler.checkToolInterception(interceptionTool)) {
                AIToolHookDecision.Allow -> {
                    val (hasPermission, errorResult) =
                        checkToolPermission(context, toolHandler, invocation, toolExposureMode)
                    if (hasPermission) {
                        permittedInvocations.add(IndexedValue(index, invocation))
                    } else {
                        completeUnexecuted(index, invocation, checkNotNull(errorResult), SubtaskExecutionState.Reason.PERMISSION_DENIED)
                    }
                }

                is AIToolHookDecision.Block -> {
                    val interceptedResult =
                        toolHandler.buildToolInterceptionResult(
                            resolveDisplayToolName(invocation.tool),
                            interception
                        )
                    toolHandler.notifyToolExecutionResult(invocation.tool, interceptedResult)
                    toolHandler.notifyToolExecutionFinished(invocation.tool)
                    completeUnexecuted(index, invocation, interceptedResult, SubtaskExecutionState.Reason.PERMISSION_DENIED)
                }
            }
        }

        val jsPackageNames = packageManager.getAvailablePackages().keys +
            packageManager.toolPkgSubpackageByPackageNameInternal.values.map { it.subpackageId }
        val injectedInvocations = mutableListOf<IndexedValue<ToolInvocation>>()
        for ((index, invocation) in permittedInvocations) {
            when (val prepared = preparePackageCallContext(invocation, jsPackageNames, callerName, toolRuntimeContext)) {
                is PackageContextInjection.Ready -> injectedInvocations.add(IndexedValue(index, prepared.invocation))
                is PackageContextInjection.Rejected -> {
                    toolHandler.notifyToolExecutionResult(invocation.tool, prepared.result)
                    completeUnexecuted(index, invocation, prepared.result, SubtaskExecutionState.Reason.EXECUTION_ERROR)
                }
            }
        }

        // 4. 按并行/串行对工具进行分组
        val parallelizableToolNames = setOf(
            "list_files", "read_file", "read_file_part", "read_file_full", "file_exists",
            "find_files", "file_info", "grep_code", "calculate", "ffmpeg_info",
            "visit_web", "download_file"
        )
        val (parallelInvocations, serialInvocations) = injectedInvocations.partition {
            parallelizableToolNames.contains(
                it.value.tool.name
            )
        }

        // 5. 执行工具并收集聚合结果
        // 启动并行工具
        val parallelJobs = parallelInvocations.map { (index, invocation) ->
            async {
                val result =
                    executeTool(
                        invocation = invocation,
                        toolHandler = toolHandler,
                        packageManager = packageManager,
                        runtimeContext = toolRuntimeContext
                    )
                orderedResults.complete(index, result)
            }
        }

        // 顺序执行串行工具
        for ((index, invocation) in serialInvocations) {
            val result =
                executeTool(
                    invocation = invocation,
                    toolHandler = toolHandler,
                    packageManager = packageManager,
                    runtimeContext = toolRuntimeContext
                )
            orderedResults.complete(index, result)
        }

        // 等待所有并行任务完成
        parallelJobs.awaitAll()

        return@coroutineScope orderedResults.finish()
    }

    /**
     * 聚合单个调用的最终结果。发布由原始调用位置统一控制，避免保存分段结果而模型收到聚合结果。
     */
    private suspend fun executeTool(
        invocation: ToolInvocation,
        toolHandler: AIToolHandler,
        packageManager: PackageManager,
        runtimeContext: ToolRuntimeContext
    ): ToolResult {
        val toolName = invocation.tool.name
        val displayToolName = resolveDisplayToolName(invocation.tool)
        fun recordForModel(result: ToolResult, executed: Boolean, evidence: ToolResult = result): ToolResult {
            val state = runtimeContext.subtaskExecution ?: return result
            val record = state.recordToolResult(resolveSubtaskTarget(invocation.tool), evidence, executed)
            return withSubtaskEvidence(result, record.id)
        }

        return withContext(toolRuntimeContextThreadLocal.asContextElement(runtimeContext)) {
            try {
                val subtask = runtimeContext.subtaskExecution
                if (subtask != null && subtask.isStopped()) {
                    val blocked = ToolResult(
                        toolName = displayToolName,
                        success = false,
                        result = StringResultData(""),
                        error = subtask.stopMessage(),
                    )
                    val annotated = recordForModel(blocked, executed = false)
                    toolHandler.notifyToolExecutionResult(invocation.tool, annotated)
                    return@withContext annotated
                }
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
                            error = errorMessage
                        )
                    toolHandler.notifyToolExecutionResult(invocation.tool, notAvailableResult)
                    subtask?.stop(SubtaskExecutionState.Reason.EXECUTION_ERROR, errorMessage)
                    return@withContext recordForModel(notAvailableResult, executed = false)
                }

                if (subtask != null) {
                    val validation = executor.validateParameters(invocation.tool)
                    if (!validation.valid) {
                        val invalid = ToolResult(
                            toolName = displayToolName,
                            success = false,
                            result = StringResultData(""),
                            error = "Invalid parameters: ${validation.errorMessage}",
                        )
                        subtask.stop(SubtaskExecutionState.Reason.EXECUTION_ERROR, invalid.error)
                        return@withContext recordForModel(invalid, executed = false)
                    }
                    if (!subtask.tryStartTool()) {
                        val blocked = ToolResult(
                            toolName = displayToolName,
                            success = false,
                            result = StringResultData(""),
                            error = subtask.stopMessage(),
                        )
                        return@withContext recordForModel(blocked, executed = false)
                    }
                }

                toolHandler.notifyToolExecutionStarted(invocation.tool)

                val annotated = if (subtask == null) {
                    aggregateToolResults(displayToolName, executeToolSafely(invocation, executor, toolHandler))
                } else {
                    collectSubtaskToolResult(subtask, resolveSubtaskTarget(invocation.tool), displayToolName) {
                        executeToolSafely(invocation, executor, toolHandler, parametersValidated = true)
                    }
                }
                toolHandler.notifyToolExecutionResult(invocation.tool, annotated)
                return@withContext annotated
            } catch (error: Exception) {
                val state = runtimeContext.subtaskExecution
                if (state != null) {
                    val cancelled = error is CancellationException
                    state.stop(
                        if (cancelled) SubtaskExecutionState.Reason.CANCELLED else SubtaskExecutionState.Reason.EXECUTION_ERROR,
                        error.message,
                    )
                }
                throw error
            } finally {
                toolHandler.notifyToolExecutionFinished(invocation.tool)
            }
        }
    }

    /** Keep the existing aggregation semantics, but publish this result to both history paths. */
    internal suspend fun aggregateToolResults(
        displayToolName: String,
        source: Flow<ToolResult>
    ): ToolResult {
        val collectedResults = mutableListOf<ToolResult>()
        source.collect { collectedResults.add(it) }
        if (collectedResults.isEmpty()) {
            return ToolResult(
                toolName = displayToolName,
                success = false,
                result = StringResultData(""),
                error = "The tool execution returned no results."
            )
        }
        val lastResult = collectedResults.last()
        val combinedResultString = collectedResults.joinToString("\n") { res ->
            if (res.success) {
                res.result.toString().trim()
            } else {
                // Failure data may contain timeout fields or a subagent's structured final result.
                // Keeping only the error string discards the evidence the caller needs to inspect.
                val detail = res.result.toString().trim()
                val error = "Step error: ${res.error ?: "Unknown error"}"
                if (detail.isEmpty()) error else "$error\n$detail"
            }
        }.trim()
        return ToolResult(
            toolName = displayToolName,
            success = lastResult.success,
            result = StringResultData(combinedResultString),
            error = lastResult.error
        )
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

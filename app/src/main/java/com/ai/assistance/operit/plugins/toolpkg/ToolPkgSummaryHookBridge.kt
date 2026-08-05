package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.SummaryGenerateHook
import com.ai.assistance.operit.core.chat.hooks.SummaryHookContext
import com.ai.assistance.operit.core.chat.hooks.SummaryHookMutation
import com.ai.assistance.operit.core.chat.hooks.SummaryHookRegistry
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_SUMMARY_GENERATE
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ToolPkgSummaryHookBridge"

internal object ToolPkgSummaryHookBridge {
    private val installed = AtomicBoolean(false)
    @Volatile
    private var summaryGenerateHooks: List<ToolPkgPromptHookRegistration> = emptyList()
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    private object SummaryGenerateBridge : SummaryGenerateHook {
        override val id: String = "builtin.toolpkg.summary-generate-hook-bridge"

        override fun onEvent(context: SummaryHookContext): SummaryHookMutation? {
            return dispatchSummaryHooks(
                hooks = summaryGenerateHooks,
                familyEvent = TOOLPKG_EVENT_SUMMARY_GENERATE,
                context = context
            )
        }
    }

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        SummaryHookRegistry.registerSummaryGenerateHook(SummaryGenerateBridge)

        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
    }

    private fun dispatchSummaryHooks(
        hooks: List<ToolPkgPromptHookRegistration>,
        familyEvent: String,
        context: SummaryHookContext
    ): SummaryHookMutation? {
        if (hooks.isEmpty()) {
            return null
        }

        val manager = toolPkgPackageManager()
        val budget = ToolPkgHookExecutionBudget.create()
        var current = context
        for (hook in hooks) {
            val resolvedHookId = hook.hookId
            val resolvedContainer = hook.containerPackageName
            val resolvedFunction = hook.functionName
            val timeoutMillis = budget.remainingMillis()
            if (timeoutMillis == null) {
                budget.logDeadlineReached(
                    tag = TAG,
                    stage = current.stage,
                    containerPackageName = resolvedContainer,
                    hookId = resolvedHookId
                )
                break
            }
            val result =
                manager.runToolPkgMainHook(
                    containerPackageName = resolvedContainer,
                    functionName = resolvedFunction,
                    event = familyEvent,
                    eventName = current.stage,
                    pluginId = resolvedHookId,
                    inlineFunctionSource = hook.functionSource,
                    eventPayload = buildSummaryEventPayload(current),
                    timeoutMillis = timeoutMillis
                )
            if (
                budget.logTimeoutIfPresent(
                    result = result,
                    tag = TAG,
                    stage = current.stage,
                    containerPackageName = resolvedContainer,
                    hookId = resolvedHookId
                )
            ) {
                break
            }
            val decoded =
                result.getOrElse { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg summary hook failed: $resolvedContainer:$resolvedHookId",
                        error
                    )
                    return@getOrElse null
                }?.let { raw ->
                    runCatching { decodeToolPkgHookResult(raw) }
                        .getOrElse { error ->
                            AppLogger.e(
                                TAG,
                                "ToolPkg summary hook decode failed: $resolvedContainer:$resolvedHookId",
                                error
                            )
                            null
                        }
                }
            val mutation = parseSummaryMutation(decoded, current) ?: continue
            current = applyMutation(current, mutation)
        }

        return SummaryHookMutation(
            chatHistory = current.chatHistory,
            preparedHistory = current.preparedHistory,
            systemPrompt = current.systemPrompt,
            summaryPrompt = current.summaryPrompt,
            summaryResult = current.summaryResult,
            metadata = current.metadata
        )
    }

    private fun syncToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        summaryGenerateHooks =
            activeContainers.flatMap { runtime ->
                runtime.summaryGenerateHooks.map { hook ->
                    ToolPkgPromptHookRegistration(
                        containerPackageName = runtime.packageName,
                        hookId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedWith(
                compareBy(
                    ToolPkgPromptHookRegistration::containerPackageName,
                    ToolPkgPromptHookRegistration::hookId
                )
            )
    }

    private fun buildSummaryEventPayload(context: SummaryHookContext): Map<String, Any?> {
        return buildMap {
            put("stage", context.stage)
            put("functionType", FunctionType.SUMMARY.name)
            put("useEnglish", context.useEnglish)
            put("previousSummary", context.previousSummary)
            put("chatHistory", context.chatHistory.map(::promptTurnToMap))
            put("preparedHistory", context.preparedHistory.map(::promptTurnToMap))
            put("systemPrompt", context.systemPrompt)
            put("summaryPrompt", context.summaryPrompt)
            put("summaryResult", context.summaryResult)
            put("modelParameters", context.modelParameters)
            put("metadata", context.metadata)
        }
    }

    private fun promptTurnToMap(message: PromptTurn): Map<String, Any?> {
        return mapOf(
            "kind" to message.kind.name,
            "content" to message.content,
            "toolName" to message.toolName,
            "metadata" to message.metadata
        )
    }

    private fun applyMutation(
        context: SummaryHookContext,
        mutation: SummaryHookMutation
    ): SummaryHookContext {
        return context.copy(
            chatHistory = mutation.chatHistory ?: context.chatHistory,
            preparedHistory = mutation.preparedHistory ?: context.preparedHistory,
            systemPrompt = mutation.systemPrompt ?: context.systemPrompt,
            summaryPrompt = mutation.summaryPrompt ?: context.summaryPrompt,
            summaryResult = mutation.summaryResult ?: context.summaryResult,
            metadata = if (mutation.metadata.isEmpty()) context.metadata else context.metadata + mutation.metadata
        )
    }

    private fun parseSummaryMutation(
        decoded: Any?,
        context: SummaryHookContext
    ): SummaryHookMutation? {
        return when (decoded) {
            null -> null
            is String ->
                if (context.stage == "after_generate_summary") {
                    SummaryHookMutation(summaryResult = decoded)
                } else {
                    SummaryHookMutation(summaryPrompt = decoded)
                }
            is JSONObject -> parseSummaryHookObject(decoded)
            else -> null
        }
    }

    private fun parseSummaryHookObject(jsonObject: JSONObject): SummaryHookMutation {
        val metadata = jsonObject.optJSONObject("metadata")?.let(::jsonObjectToMap).orEmpty()
        return SummaryHookMutation(
            chatHistory = parsePromptTurns(jsonObject.optJSONArray("chatHistory")),
            preparedHistory = parsePromptTurns(jsonObject.optJSONArray("preparedHistory")),
            systemPrompt = jsonObject.optString("systemPrompt").takeIf { it.isNotBlank() },
            summaryPrompt = jsonObject.optString("summaryPrompt").takeIf { it.isNotBlank() },
            summaryResult = jsonObject.optString("summaryResult").takeIf { it.isNotBlank() },
            metadata = metadata
        )
    }

    private fun parsePromptTurns(jsonArray: JSONArray?): List<PromptTurn>? {
        if (jsonArray == null) {
            return null
        }
        val result = mutableListOf<PromptTurn>()
        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.opt(index) as? JSONObject ?: continue
            val kind =
                item.optString("kind")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { PromptTurnKind.valueOf(it.uppercase()) }.getOrNull() }
                    ?: continue
            val content = item.optString("content")
            result.add(
                PromptTurn(
                    kind = kind,
                    content = content,
                    toolName = item.optString("toolName").takeIf { it.isNotBlank() },
                    metadata = item.optJSONObject("metadata")?.let(::jsonObjectToMap).orEmpty()
                )
            )
        }
        return result
    }
}

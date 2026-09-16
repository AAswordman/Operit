package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.core.tools.AIToolHook
import com.ai.assistance.operit.core.tools.AIToolHookDecision
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_TOOL_LIFECYCLE
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private const val TAG = "ToolPkgToolLifecycleBridge"
private const val TOOL_LIFECYCLE_EVENT_TOOL_CALL_INTERCEPT = "tool_call_intercept"

/** 连续失败多少次后，暂停该钩子（断路器），避免坏包持续拖累/刷日志。 */
private const val MAX_CONSECUTIVE_HOOK_FAILURES = 3

/** 断路器打开时长。到期后自动重试一次，坏包修好了就能自愈。 */
private const val HOOK_CIRCUIT_OPEN_MS = 60_000L

private data class ToolLifecycleDispatch(
    val eventName: String,
    val eventPayload: Map<String, Any?>
)

private class HookHealth {
    var consecutiveFailures: Int = 0
    var disabledUntilMs: Long = 0L
}

internal object ToolPkgToolLifecycleBridge : AIToolHook {
    private val installed = AtomicBoolean(false)
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatchChannel = Channel<ToolLifecycleDispatch>(Channel.UNLIMITED)
    @Volatile
    private var hooks: List<ToolPkgToolLifecycleHookRegistration> = emptyList()
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    init {
        dispatchScope.launch {
            for (dispatch in dispatchChannel) {
                deliver(dispatch)
            }
        }
    }

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        val application = OperitApplication.instance.applicationContext
        AIToolHandler.getInstance(application).addToolHook(this)
        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
    }

    override fun onToolCallRequested(tool: AITool) {
        enqueue(
            eventName = "tool_call_requested",
            eventPayload = buildBasePayload(tool)
        )
    }

    override fun onToolCallIntercept(tool: AITool): AIToolHookDecision {
        val eventPayload = buildBasePayload(tool)
        val manager = toolPkgPackageManager()
        hooks.forEach { hook ->
            if (isHookCircuitOpen(hook)) {
                return@forEach
            }
            val raw =
                manager.runToolPkgMainHook(
                    containerPackageName = hook.containerPackageName,
                    functionName = hook.functionName,
                    event = TOOLPKG_EVENT_TOOL_LIFECYCLE,
                    eventName = TOOL_LIFECYCLE_EVENT_TOOL_CALL_INTERCEPT,
                    pluginId = hook.hookId,
                    inlineFunctionSource = hook.functionSource,
                    eventPayload = eventPayload
                ).getOrElse { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg tool lifecycle intercept hook failed: ${hook.containerPackageName}:${hook.hookId}",
                        error
                    )
                    // 基础设施故障不是策略决策：只跳过这个坏钩子，放行本次调用。
                    // 这里若返回 Block，一个坏容器会把整条工具链锁死（含修复用的工具）。
                    noteHookFailure(hook)
                    return@forEach
                }
            val decoded =
                try {
                    decodeToolPkgHookResult(raw)
                } catch (error: Exception) {
                    AppLogger.e(
                        TAG,
                        "ToolPkg tool lifecycle intercept hook returned invalid result: ${hook.containerPackageName}:${hook.hookId}",
                        error
                    )
                    // 非法结果同样不是策略决策，fail-open 并跳过该钩子。
                    noteHookFailure(hook)
                    return@forEach
                }
            noteHookSuccess(hook)
            parseToolCallInterceptDecision(decoded)?.let { decision ->
                return decision
            }
        }
        return AIToolHookDecision.Allow
    }

    override fun onToolPermissionChecked(tool: AITool, granted: Boolean, reason: String?) {
        enqueue(
            eventName = "tool_permission_checked",
            eventPayload = buildBasePayload(tool) + mapOf(
                "granted" to granted,
                "reason" to reason
            )
        )
    }

    override fun onToolExecutionStarted(tool: AITool) {
        enqueue(
            eventName = "tool_execution_started",
            eventPayload = buildBasePayload(tool)
        )
    }

    override fun onToolExecutionResult(tool: AITool, result: ToolResult) {
        enqueue(
            eventName = "tool_execution_result",
            eventPayload = buildBasePayload(tool) + mapOf(
                "success" to result.success,
                "errorMessage" to result.error,
                "resultText" to result.result.toString(),
                "resultJson" to parseToolResultJson(result)
            )
        )
    }

    override fun onToolExecutionError(tool: AITool, throwable: Throwable) {
        enqueue(
            eventName = "tool_execution_error",
            eventPayload = buildBasePayload(tool) + mapOf(
                "success" to false,
                "errorMessage" to (throwable.message ?: throwable.javaClass.simpleName)
            )
        )
    }

    override fun onToolExecutionFinished(tool: AITool) {
        enqueue(
            eventName = "tool_execution_finished",
            eventPayload = buildBasePayload(tool)
        )
    }

    private fun enqueue(eventName: String, eventPayload: Map<String, Any?>) {
        val result = dispatchChannel.trySend(
            ToolLifecycleDispatch(
                eventName = eventName,
                eventPayload = eventPayload
            )
        )
        if (result.isFailure) {
            AppLogger.w(TAG, "Tool lifecycle event dropped: $eventName")
        }
    }

    private fun deliver(dispatch: ToolLifecycleDispatch) {
        val manager = toolPkgPackageManager()
        hooks.forEach { hook ->
            val result =
                manager.runToolPkgMainHook(
                    containerPackageName = hook.containerPackageName,
                    functionName = hook.functionName,
                    event = TOOLPKG_EVENT_TOOL_LIFECYCLE,
                    eventName = dispatch.eventName,
                    pluginId = hook.hookId,
                    inlineFunctionSource = hook.functionSource,
                    eventPayload = dispatch.eventPayload
                )
            result.onFailure { error ->
                AppLogger.e(
                    TAG,
                    "ToolPkg tool lifecycle hook failed: ${hook.containerPackageName}:${hook.hookId}",
                    error
                )
            }
        }
    }

    private val hookHealth = ConcurrentHashMap<String, HookHealth>()

    private fun hookKey(hook: ToolPkgToolLifecycleHookRegistration): String =
        "${hook.containerPackageName}:${hook.hookId}"

    private fun isHookCircuitOpen(hook: ToolPkgToolLifecycleHookRegistration): Boolean {
        val state = hookHealth[hookKey(hook)] ?: return false
        return state.consecutiveFailures >= MAX_CONSECUTIVE_HOOK_FAILURES &&
            System.currentTimeMillis() < state.disabledUntilMs
    }

    private fun noteHookFailure(hook: ToolPkgToolLifecycleHookRegistration) {
        val key = hookKey(hook)
        val state = hookHealth.getOrPut(key) { HookHealth() }
        state.consecutiveFailures += 1
        if (state.consecutiveFailures >= MAX_CONSECUTIVE_HOOK_FAILURES) {
            state.disabledUntilMs = System.currentTimeMillis() + HOOK_CIRCUIT_OPEN_MS
            AppLogger.w(
                TAG,
                "Tool lifecycle hook suspended for ${HOOK_CIRCUIT_OPEN_MS}ms after " +
                    "${state.consecutiveFailures} consecutive failures: $key"
            )
        }
    }

    private fun noteHookSuccess(hook: ToolPkgToolLifecycleHookRegistration) {
        hookHealth.remove(hookKey(hook))
    }

    private fun parseToolCallInterceptDecision(decoded: Any?): AIToolHookDecision? {
        return when (decoded) {
            null -> null
            is JSONObject -> parseToolCallInterceptDecision(jsonObjectToMap(decoded))
            is Map<*, *> -> {
                val action = decoded["action"]
                if (action !is String) {
                    return null
                }
                when (action.trim().lowercase()) {
                    "block" -> {
                        val reasonValue = decoded["reason"]
                        if (reasonValue !is String) {
                            return AIToolHookDecision.Block(
                                "Tool lifecycle hook block result requires a string reason."
                            )
                        }
                        val reason = reasonValue.trim()
                        if (reason.isEmpty()) {
                            AIToolHookDecision.Block(
                                "Tool lifecycle hook block result requires a non-empty reason."
                            )
                        } else {
                            AIToolHookDecision.Block(reason)
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun buildBasePayload(tool: AITool): Map<String, Any?> {
        return buildMap {
            put("toolName", tool.name)
            put("parameters", tool.parameters.associate { parameter -> parameter.name to parameter.value })
            put("description", tool.description)
        }
    }

    private fun parseToolResultJson(result: ToolResult): Any? {
        val text = result.result.toJson().trim()
        if (text.isEmpty()) {
            return null
        }
        val parsed = runCatching { JSONTokener(text).nextValue() }.getOrNull()
        return when (parsed) {
            is JSONObject -> jsonObjectToMap(parsed)
            is JSONArray -> jsonArrayToList(parsed)
            else -> null
        }
    }

    private fun syncToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        hooks =
            activeContainers.flatMap { runtime ->
                runtime.toolLifecycleHooks.map { hook ->
                    ToolPkgToolLifecycleHookRegistration(
                        containerPackageName = runtime.packageName,
                        hookId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedByToolPkgLoadOrder(
                activeContainers = activeContainers,
                containerPackageName = ToolPkgToolLifecycleHookRegistration::containerPackageName,
                registrationId = ToolPkgToolLifecycleHookRegistration::hookId
            )
    }
}

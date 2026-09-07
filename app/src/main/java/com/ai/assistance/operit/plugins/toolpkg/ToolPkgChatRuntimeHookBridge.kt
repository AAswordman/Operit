package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.chat.hooks.ChatRuntimeHook
import com.ai.assistance.operit.core.chat.hooks.ChatRuntimeHookContext
import com.ai.assistance.operit.core.chat.hooks.ChatRuntimeHookEvent
import com.ai.assistance.operit.core.chat.hooks.ChatRuntimeHookRegistry
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_CHAT_RUNTIME
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ToolPkgChatRuntimeHookBridge"

internal object ToolPkgChatRuntimeHookBridge : ChatRuntimeHook {
    private val installed = AtomicBoolean(false)
    @Volatile
    private var hooks: List<ToolPkgChatRuntimeHookRegistration> = emptyList()
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    override val id: String = "builtin.toolpkg.chat-runtime-hook-bridge"

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        ChatRuntimeHookRegistry.registerHook(this)

        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
        syncToolPkgRegistrations(manager.getEnabledToolPkgContainerRuntimes())
    }

    override suspend fun onEvent(
        event: ChatRuntimeHookEvent,
        context: ChatRuntimeHookContext
    ) {
        val activeHooks = hooks
        if (activeHooks.isEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            val manager = toolPkgPackageManager()
            val eventPayload = buildChatRuntimeEventPayload(context)
            activeHooks.forEach { hook ->
                val result =
                    manager.runToolPkgMainHook(
                        containerPackageName = hook.containerPackageName,
                        functionName = hook.functionName,
                        event = TOOLPKG_EVENT_CHAT_RUNTIME,
                        eventName = event.wireName,
                        pluginId = hook.hookId,
                        inlineFunctionSource = hook.functionSource,
                        eventPayload = eventPayload
                    )
                result.onFailure { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg chat runtime hook failed: ${hook.containerPackageName}:${hook.hookId}",
                        error
                    )
                }
            }
        }
    }

    private fun syncToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        hooks =
            activeContainers.flatMap { runtime ->
                runtime.chatRuntimeHooks.map { hook ->
                    ToolPkgChatRuntimeHookRegistration(
                        containerPackageName = runtime.packageName,
                        hookId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedByToolPkgLoadOrder(
                activeContainers = activeContainers,
                containerPackageName = ToolPkgChatRuntimeHookRegistration::containerPackageName,
                registrationId = ToolPkgChatRuntimeHookRegistration::hookId
            )
    }

    private fun buildChatRuntimeEventPayload(context: ChatRuntimeHookContext): Map<String, Any?> =
        mapOf(
            "chatId" to context.chatId,
            "slot" to context.slot.name.lowercase(),
            "state" to stateName(context.state),
            "message" to stateMessage(context.state),
            "toolName" to stateToolName(context.state),
            "progress" to stateProgress(context.state),
            "isActive" to stateIsActive(context.state),
            "activeChatIds" to context.activeChatIds.sorted(),
            "currentTurnToolInvocationCount" to context.currentTurnToolInvocationCount,
            "activeConversationCount" to context.activeConversationCount,
            "currentSessionToolCount" to context.currentSessionToolCount,
            "timestamp" to context.timestamp
        )

    private fun stateName(state: InputProcessingState): String =
        when (state) {
            is InputProcessingState.Idle -> "idle"
            is InputProcessingState.Processing -> "processing"
            is InputProcessingState.Connecting -> "connecting"
            is InputProcessingState.Receiving -> "receiving"
            is InputProcessingState.ExecutingTool -> "executing_tool"
            is InputProcessingState.ToolProgress -> "tool_progress"
            is InputProcessingState.ProcessingToolResult -> "processing_tool_result"
            is InputProcessingState.Summarizing -> "summarizing"
            is InputProcessingState.ExecutingPlan -> "executing_plan"
            is InputProcessingState.Completed -> "completed"
            is InputProcessingState.Error -> "error"
        }

    private fun stateMessage(state: InputProcessingState): String? =
        when (state) {
            is InputProcessingState.Processing -> state.message
            is InputProcessingState.Connecting -> state.message
            is InputProcessingState.Receiving -> state.message
            is InputProcessingState.ToolProgress -> state.message
            is InputProcessingState.Summarizing -> state.message
            is InputProcessingState.ExecutingPlan -> state.message
            is InputProcessingState.Error -> state.message
            else -> null
        }

    private fun stateToolName(state: InputProcessingState): String? =
        when (state) {
            is InputProcessingState.ExecutingTool -> state.toolName
            is InputProcessingState.ToolProgress -> state.toolName
            is InputProcessingState.ProcessingToolResult -> state.toolName
            else -> null
        }

    private fun stateProgress(state: InputProcessingState): Float? =
        when (state) {
            is InputProcessingState.ToolProgress -> state.progress
            else -> null
        }

    private fun stateIsActive(state: InputProcessingState): Boolean =
        when (state) {
            is InputProcessingState.Idle,
            is InputProcessingState.Completed,
            is InputProcessingState.Error -> false
            else -> true
        }
}

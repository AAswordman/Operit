package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_CHAT_MESSAGE
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ToolPkgChatMessageHookBridge"
private const val CHAT_MESSAGE_EVENT_MESSAGE_PERSISTED = "message_persisted"

internal object ToolPkgChatMessageHookBridge {
    private val installed = AtomicBoolean(false)
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var hooks: List<ToolPkgChatMessageHookRegistration> = emptyList()
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }

        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
        syncToolPkgRegistrations(manager.getEnabledToolPkgContainerRuntimes())
    }

    fun dispatchMessagePersisted(chatId: String, message: ChatMessage) {
        val activeHooks = hooks
        if (activeHooks.isEmpty()) {
            return
        }

        val eventPayload = buildChatMessageEventPayload(chatId, message)
        dispatchScope.launch {
            val manager = toolPkgPackageManager()
            activeHooks.forEach { hook ->
                val result =
                    manager.runToolPkgMainHook(
                        containerPackageName = hook.containerPackageName,
                        functionName = hook.functionName,
                        event = TOOLPKG_EVENT_CHAT_MESSAGE,
                        eventName = CHAT_MESSAGE_EVENT_MESSAGE_PERSISTED,
                        pluginId = hook.hookId,
                        inlineFunctionSource = hook.functionSource,
                        eventPayload = eventPayload
                    )
                result.onFailure { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg chat message hook failed: ${hook.containerPackageName}:${hook.hookId}",
                        error
                    )
                }
            }
        }
    }

    private fun syncToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        hooks =
            activeContainers.flatMap { runtime ->
                runtime.chatMessageHooks.map { hook ->
                    ToolPkgChatMessageHookRegistration(
                        containerPackageName = runtime.packageName,
                        hookId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedByToolPkgLoadOrder(
                activeContainers = activeContainers,
                containerPackageName = ToolPkgChatMessageHookRegistration::containerPackageName,
                registrationId = ToolPkgChatMessageHookRegistration::hookId
            )
    }

    private fun buildChatMessageEventPayload(
        chatId: String,
        message: ChatMessage
    ): Map<String, Any?> =
        mapOf(
            "chatId" to chatId,
            "timestamp" to message.timestamp,
            "sender" to message.sender,
            "roleName" to message.roleName,
            "content" to message.content,
            "completedAt" to message.completedAt,
            "provider" to message.provider,
            "modelName" to message.modelName,
            "inputTokens" to message.inputTokens,
            "outputTokens" to message.outputTokens,
            "cachedInputTokens" to message.cachedInputTokens,
            "sentAt" to message.sentAt,
            "outputDurationMs" to message.outputDurationMs,
            "waitDurationMs" to message.waitDurationMs,
            "displayMode" to message.displayMode.name,
            "selectedVariantIndex" to message.selectedVariantIndex,
            "isFavorite" to message.isFavorite
        )
}

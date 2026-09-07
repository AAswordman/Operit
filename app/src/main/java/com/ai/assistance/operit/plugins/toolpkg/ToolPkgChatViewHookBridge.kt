package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_CHAT_VIEW
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.plugins.chatview.ChatViewEvent
import com.ai.assistance.operit.plugins.chatview.ChatViewHookParams
import com.ai.assistance.operit.plugins.chatview.ChatViewHookPlugin
import com.ai.assistance.operit.plugins.chatview.ChatViewHookPluginRegistry
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ToolPkgChatViewHookBridge"

internal object ToolPkgChatViewHookBridge : ChatViewHookPlugin {
    private val installed = AtomicBoolean(false)
    private val replayScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var hooks: List<ToolPkgChatViewHookRegistration> = emptyList()
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncAndReplayToolPkgRegistrations(activeContainers)
        }

    override val id: String = "builtin.toolpkg.chat-view-hook-bridge"

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        ChatViewHookPluginRegistry.register(this)

        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
        syncAndReplayToolPkgRegistrations(manager.getEnabledToolPkgContainerRuntimes())
    }

    override suspend fun onEvent(
        event: ChatViewEvent,
        params: ChatViewHookParams
    ) {
        val activeHooks = hooks
        if (activeHooks.isEmpty()) {
            return
        }

        val eventPayload = buildChatViewEventPayload(params)
        withContext(Dispatchers.IO) {
            val manager = toolPkgPackageManager()
            activeHooks.forEach { hook ->
                val result =
                    manager.runToolPkgMainHook(
                        containerPackageName = hook.containerPackageName,
                        functionName = hook.functionName,
                        event = TOOLPKG_EVENT_CHAT_VIEW,
                        eventName = event.wireName,
                        pluginId = hook.hookId,
                        inlineFunctionSource = hook.functionSource,
                        eventPayload = eventPayload
                    )
                result.onFailure { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg chat view hook failed: ${hook.containerPackageName}:${hook.hookId}",
                        error
                    )
                }
            }
        }
    }

    private fun syncAndReplayToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        val previousHooks = hooks
        val nextHooks =
            activeContainers.flatMap { runtime ->
                runtime.chatViewHooks.map { hook ->
                    ToolPkgChatViewHookRegistration(
                        containerPackageName = runtime.packageName,
                        hookId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedByToolPkgLoadOrder(
                activeContainers = activeContainers,
                containerPackageName = ToolPkgChatViewHookRegistration::containerPackageName,
                registrationId = ToolPkgChatViewHookRegistration::hookId
            )
        hooks = nextHooks

        val hooksToReplay = nextHooks.filterNot { it in previousHooks }
        if (hooksToReplay.isEmpty()) {
            return
        }

        val replayParams = ChatViewHookPluginRegistry.getReplayableOpenViewParams()
        if (replayParams.isEmpty()) {
            return
        }

        replayScope.launch {
            replayOpenViews(hooksToReplay = hooksToReplay, replayParams = replayParams)
        }
    }

    private suspend fun replayOpenViews(
        hooksToReplay: List<ToolPkgChatViewHookRegistration>,
        replayParams: List<ChatViewHookParams>
    ) {
        AppLogger.i(
            TAG,
            "Replaying chat view open events to newly loaded toolpkg hooks: hookCount=${hooksToReplay.size}, viewCount=${replayParams.size}"
        )
        withContext(Dispatchers.IO) {
            val manager = toolPkgPackageManager()
            replayParams.forEach { params ->
                val eventPayload = buildChatViewEventPayload(params)
                hooksToReplay.forEach { hook ->
                    val result =
                        manager.runToolPkgMainHook(
                            containerPackageName = hook.containerPackageName,
                            functionName = hook.functionName,
                            event = TOOLPKG_EVENT_CHAT_VIEW,
                            eventName = ChatViewEvent.VIEW_OPENED.wireName,
                            pluginId = hook.hookId,
                            inlineFunctionSource = hook.functionSource,
                            eventPayload = eventPayload
                        )
                    result.onFailure { error ->
                        AppLogger.e(
                            TAG,
                            "ToolPkg chat view replay failed: ${hook.containerPackageName}:${hook.hookId}",
                            error
                        )
                    }
                }
            }
        }
    }

    private fun buildChatViewEventPayload(params: ChatViewHookParams): Map<String, Any?> =
        mapOf(
            "viewId" to params.viewId,
            "chatId" to params.chatId,
            "workspacePath" to params.workspacePath,
            "workspaceEnv" to params.workspaceEnv,
            "runtime" to params.runtime,
            "title" to params.title
        )
}

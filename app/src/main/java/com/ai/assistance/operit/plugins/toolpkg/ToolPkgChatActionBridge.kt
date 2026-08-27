package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.api.chat.ChatCurrentActionEvent
import com.ai.assistance.operit.api.chat.ChatCurrentActionSnapshot
import com.ai.assistance.operit.api.chat.ChatCurrentActionStore
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_CHAT_ACTION_STATE

import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val TAG = "ToolPkgChatActionBridge"

private data class ChatActionDispatch(
    val event: ChatCurrentActionEvent,
    val scope: String
)

internal object ToolPkgChatActionBridge {
    private val installed = AtomicBoolean(false)
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatchChannel = Channel<ChatActionDispatch>(Channel.UNLIMITED)

    @Volatile
    private var hooks: List<ToolPkgChatActionStateHookRegistration> = emptyList()

    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            replaceHooks(
                activeContainers.flatMap { runtime ->
                    runtime.chatActionStateHooks.map { hook ->
                        ToolPkgChatActionStateHookRegistration(
                            containerPackageName = runtime.packageName,
                            hookId = hook.id,
                            functionName = hook.function,
                            functionSource = hook.functionSource
                        )
                    }
                }.sortedWith(
                    compareBy(
                        ToolPkgChatActionStateHookRegistration::containerPackageName,
                        ToolPkgChatActionStateHookRegistration::hookId
                    )
                )
            )
        }

    init {
        dispatchScope.launch {
            ChatCurrentActionStore.events.collect { event ->
                enqueue(event, scope = "session")
                enqueue(event, scope = "global")
            }
        }
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
        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
        ChatCurrentActionStore.replayEvents().forEach { event ->
            enqueue(event, scope = if (event.session == null) "global" else "session")
        }
    }

    fun replaceHooks(updatedHooks: List<ToolPkgChatActionStateHookRegistration>) {
        hooks = updatedHooks
        if (installed.get()) {
            ChatCurrentActionStore.replayEvents().forEach { event ->
                enqueue(event, scope = if (event.session == null) "global" else "session")
            }
        }
    }

    private fun enqueue(event: ChatCurrentActionEvent, scope: String) {
        if (scope == "session" && event.session == null) {
            return
        }
        val result = dispatchChannel.trySend(ChatActionDispatch(event = event, scope = scope))
        if (result.isFailure) {
            AppLogger.w(TAG, "Chat action event dropped: scope=$scope, event=${event.eventType}")
        }
    }

    private fun deliver(dispatch: ChatActionDispatch) {
        val eventPayload = buildEventPayload(dispatch)
        val manager = toolPkgPackageManager()
        hooks.forEach { hook ->
            val result = manager.runToolPkgMainHook(
                containerPackageName = hook.containerPackageName,
                functionName = hook.functionName,
                event = TOOLPKG_EVENT_CHAT_ACTION_STATE,
                eventName = dispatch.event.eventType,
                pluginId = hook.hookId,
                inlineFunctionSource = hook.functionSource,
                eventPayload = eventPayload
            )
            result.onFailure { error ->
                AppLogger.e(
                    TAG,
                    "ToolPkg chat action hook failed: ${hook.containerPackageName}:${hook.hookId}",
                    error
                )
            }
        }
    }

    private fun buildEventPayload(dispatch: ChatActionDispatch): Map<String, Any?> {
        val event = dispatch.event
        return buildMap {
            put("scope", dispatch.scope)
            put("event", event.eventType)
            put("applicationState", event.global.applicationState.wireName)
            put("activeChatIds", event.global.activeChatIds)
            put("globalActivity", event.global.activity.wireName)
            put(
                "updatedAt",
                if (dispatch.scope == "global") {
                    event.global.updatedAt
                } else {
                    requireNotNull(event.session).updatedAt
                }
            )
            if (dispatch.scope == "session") {
                event.session?.let { session ->
                    putAll(buildSessionPayload(session))
                }
            }
        }
    }

    private fun buildSessionPayload(session: ChatCurrentActionSnapshot): Map<String, Any?> {
        return buildMap {
            put("chatId", session.chatId)
            put("aiBehavior", session.phase.wireName)
            put("userState", session.userState?.wireName)
            put("applicationState", session.applicationState.wireName)
            put("toolName", session.toolName)
            put("updatedAt", session.updatedAt)
            session.error?.let { error ->
                put("errorSource", error.source.wireName)
                put("errorCode", error.code)
                put("errorMessage", error.message)
                put("errorRecoverable", error.recoverable)
                put("retryAttempt", error.retryAttempt)
            }
        }
    }
}
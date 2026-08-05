package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_CHAT_INPUT
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputEvents
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHook
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHookContext
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHookRegistry
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHookResult
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputSubmitActions
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "ToolPkgChatInputHookBridge"

internal object ToolPkgChatInputHookBridge {
    private val installed = AtomicBoolean(false)
    @Volatile
    private var hooks: List<ToolPkgChatInputHookRegistration> = emptyList()
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    private object Bridge : ChatInputHook {
        override val id: String = "builtin.toolpkg.chat-input-hook-bridge"

        override suspend fun onEvent(context: ChatInputHookContext): ChatInputHookResult? {
            return dispatchChatInputHooks(context)
        }
    }

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        ChatInputHookRegistry.register(Bridge)

        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
        syncToolPkgRegistrations(manager.getEnabledToolPkgContainerRuntimes())
    }

    private suspend fun dispatchChatInputHooks(
        context: ChatInputHookContext
    ): ChatInputHookResult? {
        val activeHooks = hooks
        if (activeHooks.isEmpty()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            val manager = toolPkgPackageManager()
            val budget = ToolPkgHookExecutionBudget.create()
            var current = context
            var timeoutNoticeMessage: String? = null
            for (hook in activeHooks) {
                val timeoutMillis = budget.remainingMillis()
                if (timeoutMillis == null) {
                    budget.logDeadlineReached(
                        tag = TAG,
                        stage = current.eventName,
                        containerPackageName = hook.containerPackageName,
                        hookId = hook.hookId
                    )
                    if (current.eventName == ChatInputEvents.SUBMIT_REQUESTED) {
                        timeoutNoticeMessage =
                            context.context.getString(R.string.toolpkg_hook_timeout_continue_sending)
                    }
                    break
                }
                val result =
                    manager.runToolPkgMainHook(
                        containerPackageName = hook.containerPackageName,
                        functionName = hook.functionName,
                        event = TOOLPKG_EVENT_CHAT_INPUT,
                        eventName = current.eventName,
                        pluginId = hook.hookId,
                        inlineFunctionSource = hook.functionSource,
                        eventPayload = buildChatInputEventPayload(current),
                        timeoutMillis = timeoutMillis
                    )
                if (
                    budget.logTimeoutIfPresent(
                        result = result,
                        tag = TAG,
                        stage = current.eventName,
                        containerPackageName = hook.containerPackageName,
                        hookId = hook.hookId
                    )
                ) {
                    if (current.eventName == ChatInputEvents.SUBMIT_REQUESTED) {
                        timeoutNoticeMessage =
                            context.context.getString(R.string.toolpkg_hook_timeout_continue_sending)
                    }
                    break
                }
                val decoded =
                    result.getOrElse { error ->
                        AppLogger.e(
                            TAG,
                            "ToolPkg chat input hook failed: ${hook.containerPackageName}:${hook.hookId}",
                            error
                        )
                        null
                    }?.let { raw ->
                        runCatching { decodeToolPkgHookResult(raw) }
                            .getOrElse { error ->
                                AppLogger.e(
                                    TAG,
                                    "ToolPkg chat input hook decode failed: ${hook.containerPackageName}:${hook.hookId}",
                                    error
                                )
                                null
                            }
                }

                val parsed = parseChatInputHookResult(decoded)
                if (parsed == null) {
                    continue
                }
                if (current.eventName != ChatInputEvents.SUBMIT_REQUESTED) {
                    continue
                }
                when (parsed.action) {
                    ChatInputSubmitActions.BLOCK,
                    ChatInputSubmitActions.CONSUME -> return@withContext parsed
                    ChatInputSubmitActions.REPLACE -> {
                        val replacement = parsed.text ?: current.text
                        current =
                            current.copy(
                                text = replacement,
                                selectionStart = replacement.length,
                                selectionEnd = replacement.length
                            )
                    }
                }
            }

            if (current.eventName == ChatInputEvents.SUBMIT_REQUESTED) {
                ChatInputHookResult(
                    action = ChatInputSubmitActions.ALLOW,
                    text = current.text,
                    noticeMessage = timeoutNoticeMessage
                )
            } else {
                null
            }
        }
    }

    private fun syncToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        hooks =
            activeContainers.flatMap { runtime ->
                runtime.chatInputHooks.map { hook ->
                    ToolPkgChatInputHookRegistration(
                        containerPackageName = runtime.packageName,
                        hookId = hook.id,
                        functionName = hook.function,
                        functionSource = hook.functionSource
                    )
                }
            }.sortedWith(
                compareBy(
                    ToolPkgChatInputHookRegistration::containerPackageName,
                    ToolPkgChatInputHookRegistration::hookId
                )
            )
    }

    private fun buildChatInputEventPayload(context: ChatInputHookContext): Map<String, Any?> =
        mapOf(
            "chatId" to context.chatId,
            "text" to context.text,
            "selectionStart" to context.selectionStart,
            "selectionEnd" to context.selectionEnd,
            "hasAttachments" to context.hasAttachments,
            "attachmentCount" to context.attachmentCount,
            "isProcessing" to context.isProcessing,
            "inputStyle" to context.inputStyle,
            "source" to context.source,
            "submitSource" to context.submitSource
        )

    private fun parseChatInputHookResult(decoded: Any?): ChatInputHookResult? {
        return when (decoded) {
            null -> null
            is String -> {
                if (decoded.isBlank()) {
                    null
                } else {
                    ChatInputHookResult(
                        action = ChatInputSubmitActions.REPLACE,
                        text = decoded
                    )
                }
            }
            is JSONObject -> parseChatInputHookResult(jsonObjectToMap(decoded))
            is Map<*, *> -> {
                val actionValue = decoded["action"]?.toString()?.trim()?.lowercase()
                val textValue = decoded["text"]?.toString()
                val action =
                    when (actionValue) {
                        ChatInputSubmitActions.BLOCK -> ChatInputSubmitActions.BLOCK
                        ChatInputSubmitActions.CONSUME -> ChatInputSubmitActions.CONSUME
                        ChatInputSubmitActions.REPLACE -> ChatInputSubmitActions.REPLACE
                        ChatInputSubmitActions.ALLOW -> ChatInputSubmitActions.ALLOW
                        "" -> if (decoded.containsKey("text")) ChatInputSubmitActions.REPLACE else return null
                        null -> if (decoded.containsKey("text")) ChatInputSubmitActions.REPLACE else return null
                        else -> return null
                    }
                val metadata =
                    (decoded["metadata"] as? Map<*, *>)
                        ?.mapNotNull { (key, value) ->
                            key?.toString()?.let { it to value }
                        }
                        ?.toMap()
                        ?: emptyMap()
                ChatInputHookResult(
                    action = action,
                    text = textValue,
                    message = decoded["message"]?.toString(),
                    clearInput = decoded["clearInput"] == true,
                    metadata = metadata
                )
            }
            else -> null
        }
    }
}

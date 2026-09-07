package com.ai.assistance.operit.plugins.toolpkg

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_CHAT_MESSAGE_MENU_ITEM
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_RUNTIME_COMPOSE_DSL
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.plugins.chatmessage.ChatMessageMenuDialogRequest
import com.ai.assistance.operit.plugins.chatmessage.ChatMessageMenuItemClickResult
import com.ai.assistance.operit.plugins.chatmessage.ChatMessageMenuItemDefinition
import com.ai.assistance.operit.plugins.chatmessage.ChatMessageMenuItemParams
import com.ai.assistance.operit.plugins.chatmessage.ChatMessageMenuItemPlugin
import com.ai.assistance.operit.plugins.chatmessage.ChatMessageMenuItemRegistry
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "ToolPkgChatMessageMenu"
private const val CHAT_MESSAGE_MENU_ITEM_CLICK = "chat_message_menu_item_click"

internal object ToolPkgChatMessageMenuItemBridge : ChatMessageMenuItemPlugin {
    private val installed = AtomicBoolean(false)
    @Volatile
    private var menuItems: List<ToolPkgChatMessageMenuItemRegistration> = emptyList()
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    override val id: String = "builtin.toolpkg.chat-message-menu-item-bridge"

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        ChatMessageMenuItemRegistry.register(this)

        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
        syncToolPkgRegistrations(manager.getEnabledToolPkgContainerRuntimes())
    }

    override fun createMenuItems(
        params: ChatMessageMenuItemParams
    ): List<ChatMessageMenuItemDefinition> {
        val sender = params.message.sender.trim().lowercase()
        return menuItems
            .filter { item ->
                item.senders.isEmpty() || item.senders.any { allowed -> allowed.equals(sender, true) }
            }
            .map { item ->
                ChatMessageMenuItemDefinition(
                    id = "${item.containerPackageName}:${item.itemId}",
                    title = item.title.resolve(params.context).trim().ifBlank { item.itemId },
                    icon = item.icon,
                    order = item.order,
                    onClick = { clickParams -> dispatchClick(item, clickParams) }
                )
            }
    }

    private fun syncToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        menuItems =
            activeContainers.flatMap { runtime ->
                runtime.chatMessageMenuItems.map { item ->
                    ToolPkgChatMessageMenuItemRegistration(
                        containerPackageName = runtime.packageName,
                        itemId = item.id,
                        title = item.title,
                        icon = item.icon,
                        order = item.order,
                        senders = item.senders,
                        functionName = item.function,
                        functionSource = item.functionSource,
                        dialog =
                            item.dialog?.let { dialog ->
                                ToolPkgChatMessageMenuDialogRegistration(
                                    screenPath = dialog.screen,
                                    title = dialog.title
                                )
                            }
                    )
                }
            }.sortedByToolPkgLoadOrder(
                activeContainers = activeContainers,
                containerPackageName = ToolPkgChatMessageMenuItemRegistration::containerPackageName,
                registrationId = ToolPkgChatMessageMenuItemRegistration::itemId
            )
        ChatMessageMenuItemRegistry.notifyChanged()
    }

    private suspend fun dispatchClick(
        item: ToolPkgChatMessageMenuItemRegistration,
        params: ChatMessageMenuItemParams
    ): ChatMessageMenuItemClickResult? {
        val eventPayload = buildEventPayload(params, item.itemId)
        return withContext(Dispatchers.IO) {
            val manager = toolPkgPackageManager()
            val result =
                manager.runToolPkgMainHook(
                    containerPackageName = item.containerPackageName,
                    functionName = item.functionName,
                    event = TOOLPKG_EVENT_CHAT_MESSAGE_MENU_ITEM,
                    eventName = CHAT_MESSAGE_MENU_ITEM_CLICK,
                    pluginId = item.itemId,
                    inlineFunctionSource = item.functionSource,
                    eventPayload = eventPayload
                )
            val raw =
                try {
                    result.getOrThrow()
                } catch (error: Exception) {
                    AppLogger.e(
                        TAG,
                        "ToolPkg chat message menu item failed: ${item.containerPackageName}:${item.itemId}",
                        error
                    )
                    return@withContext null
                }
            val decoded =
                try {
                    decodeToolPkgHookResult(raw)
                } catch (error: Exception) {
                    AppLogger.e(
                        TAG,
                        "ToolPkg chat message menu item decode failed: ${item.containerPackageName}:${item.itemId}",
                        error
                    )
                    return@withContext null
                }
            ChatMessageMenuItemClickResult(
                dialog = buildDialogRequest(
                    context = params.context,
                    item = item,
                    eventPayload = eventPayload,
                    decoded = decoded
                )
            )
        }
    }

    private fun buildDialogRequest(
        context: Context,
        item: ToolPkgChatMessageMenuItemRegistration,
        eventPayload: Map<String, Any?>,
        decoded: Any?
    ): ChatMessageMenuDialogRequest? {
        val decodedMap = decoded.asPlainMap()
        val dialogMap = decodedMap["dialog"].asPlainMap()
        val registeredDialog = item.dialog ?: return null
        val state =
            linkedMapOf<String, Any?>(
                "chatId" to eventPayload["chatId"],
                "messageIndex" to eventPayload["messageIndex"],
                "message" to eventPayload["message"],
                "menuItemId" to item.itemId
            )
        state.putAll(dialogMap["state"].asPlainMap())

        val moduleSpec =
            linkedMapOf<String, Any?>(
                "id" to item.itemId,
                "runtime" to TOOLPKG_RUNTIME_COMPOSE_DSL,
                "screen" to registeredDialog.screenPath,
                "title" to registeredDialog.title.resolve(context),
                "toolPkgId" to item.containerPackageName,
                "menuItemId" to item.itemId
            )
        moduleSpec.putAll(dialogMap["moduleSpec"].asPlainMap())

        val resultTitle = dialogMap["title"]?.toString()?.trim().orEmpty()
        val registeredTitle = registeredDialog.title.resolve(context).trim()
        return ChatMessageMenuDialogRequest(
            containerPackageName = item.containerPackageName,
            screenPath = registeredDialog.screenPath,
            title = resultTitle.ifBlank { registeredTitle },
            state = state,
            moduleSpec = moduleSpec
        )
    }

    private fun buildEventPayload(
        params: ChatMessageMenuItemParams,
        menuItemId: String
    ): Map<String, Any?> =
        mapOf(
            "action" to "click",
            "chatId" to params.chatId,
            "messageIndex" to params.messageIndex,
            "menuItemId" to menuItemId,
            "message" to buildMessagePayload(params.message)
        )

    private fun buildMessagePayload(message: ChatMessage): Map<String, Any?> =
        mapOf(
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
            "variantCount" to message.variantCount,
            "isFavorite" to message.isFavorite
        )

    private fun Any?.asPlainMap(): Map<String, Any?> {
        return when (this) {
            is Map<*, *> ->
                entries.mapNotNull { (key, value) ->
                    key?.toString()?.let { it to value }
                }.toMap()
            is JSONObject -> jsonObjectToMap(this)
            else -> emptyMap()
        }
    }
}

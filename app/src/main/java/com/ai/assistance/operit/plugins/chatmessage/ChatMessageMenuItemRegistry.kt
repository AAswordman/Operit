package com.ai.assistance.operit.plugins.chatmessage

import android.content.Context
import com.ai.assistance.operit.data.model.ChatMessage
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ChatMessageMenuItemParams(
    val context: Context,
    val chatId: String,
    val messageIndex: Int,
    val message: ChatMessage
)

data class ChatMessageMenuDialogRequest(
    val containerPackageName: String,
    val screenPath: String,
    val title: String,
    val state: Map<String, Any?>,
    val moduleSpec: Map<String, Any?>
)

data class ChatMessageMenuItemClickResult(
    val dialog: ChatMessageMenuDialogRequest? = null
)

data class ChatMessageMenuItemDefinition(
    val id: String,
    val title: String,
    val icon: String? = null,
    val order: Int = 0,
    val onClick: suspend (ChatMessageMenuItemParams) -> ChatMessageMenuItemClickResult? = { null }
)

interface ChatMessageMenuItemPlugin {
    val id: String

    fun createMenuItems(params: ChatMessageMenuItemParams): List<ChatMessageMenuItemDefinition>
}

object ChatMessageMenuItemRegistry {
    private val plugins = CopyOnWriteArrayList<ChatMessageMenuItemPlugin>()
    private val changeVersionMutable = MutableStateFlow(0)
    val changeVersion: StateFlow<Int> = changeVersionMutable.asStateFlow()

    @Synchronized
    fun register(plugin: ChatMessageMenuItemPlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
        notifyChanged()
    }

    @Synchronized
    fun unregister(pluginId: String) {
        val changed = plugins.removeAll { it.id == pluginId }
        if (changed) {
            notifyChanged()
        }
    }

    fun notifyChanged() {
        changeVersionMutable.update { current -> current + 1 }
    }

    fun createMenuItems(params: ChatMessageMenuItemParams): List<ChatMessageMenuItemDefinition> {
        return plugins
            .flatMap { plugin -> plugin.createMenuItems(params) }
            .sortedWith(
                compareBy(
                    ChatMessageMenuItemDefinition::order,
                    ChatMessageMenuItemDefinition::title,
                    ChatMessageMenuItemDefinition::id
                )
            )
    }
}

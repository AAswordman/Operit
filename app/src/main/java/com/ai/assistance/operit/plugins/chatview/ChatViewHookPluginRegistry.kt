package com.ai.assistance.operit.plugins.chatview

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class ChatViewEvent(val wireName: String) {
    VIEW_OPENED("view_opened"),
    VIEW_UPDATED("view_updated"),
    VIEW_CLOSED("view_closed")
}

data class ChatViewHookParams(
    val context: Context,
    val viewId: String,
    val chatId: String? = null,
    val workspacePath: String? = null,
    val workspaceEnv: String? = null,
    val runtime: String,
    val title: String? = null
)

interface ChatViewHookPlugin {
    val id: String

    suspend fun onEvent(
        event: ChatViewEvent,
        params: ChatViewHookParams
    )
}

object ChatViewHookPluginRegistry {
    private const val TAG = "ChatViewHooks"
    private val plugins = CopyOnWriteArrayList<ChatViewHookPlugin>()
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()
    private val openViewsById = LinkedHashMap<String, ChatViewHookParams>()

    @Synchronized
    fun register(plugin: ChatViewHookPlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
    }

    @Synchronized
    fun unregister(pluginId: String) {
        plugins.removeAll { it.id == pluginId }
    }

    suspend fun dispatch(
        event: ChatViewEvent,
        params: ChatViewHookParams
    ) {
        recordReplayableState(event, params)
        for (plugin in plugins) {
            try {
                plugin.onEvent(event, params)
            } catch (error: Exception) {
                AppLogger.e(
                    TAG,
                    "Chat view hook plugin failed: ${plugin.id}, event=${event.wireName}, viewId=${params.viewId}",
                    error
                )
            }
        }
    }

    fun dispatchAsync(
        event: ChatViewEvent,
        params: ChatViewHookParams
    ) {
        dispatchScope.launch {
            dispatch(event = event, params = params)
        }
    }

    fun getReplayableOpenViewParams(): List<ChatViewHookParams> {
        synchronized(stateLock) {
            return openViewsById.values.toList()
        }
    }

    private fun recordReplayableState(
        event: ChatViewEvent,
        params: ChatViewHookParams
    ) {
        synchronized(stateLock) {
            when (event) {
                ChatViewEvent.VIEW_OPENED, ChatViewEvent.VIEW_UPDATED -> {
                    openViewsById[params.viewId] = params
                }

                ChatViewEvent.VIEW_CLOSED -> {
                    openViewsById.remove(params.viewId)
                }
            }
        }
    }
}

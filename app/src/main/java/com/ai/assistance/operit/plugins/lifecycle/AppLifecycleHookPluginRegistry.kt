package com.ai.assistance.operit.plugins.lifecycle

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_ACTIVITY_ON_CREATE
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_ACTIVITY_ON_DESTROY
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_ACTIVITY_ON_PAUSE
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_ACTIVITY_ON_RESUME
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_ACTIVITY_ON_START
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_ACTIVITY_ON_STOP
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_APPLICATION_ON_BACKGROUND
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_APPLICATION_ON_CREATE
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_APPLICATION_ON_FOREGROUND
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_APPLICATION_ON_LOW_MEMORY
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_APPLICATION_ON_TERMINATE
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_EVENT_APPLICATION_ON_TRIM_MEMORY

enum class AppLifecycleEvent(val wireName: String) {
    APPLICATION_CREATE(TOOLPKG_EVENT_APPLICATION_ON_CREATE),
    APPLICATION_FOREGROUND(TOOLPKG_EVENT_APPLICATION_ON_FOREGROUND),
    APPLICATION_BACKGROUND(TOOLPKG_EVENT_APPLICATION_ON_BACKGROUND),
    APPLICATION_LOW_MEMORY(TOOLPKG_EVENT_APPLICATION_ON_LOW_MEMORY),
    APPLICATION_TRIM_MEMORY(TOOLPKG_EVENT_APPLICATION_ON_TRIM_MEMORY),
    APPLICATION_TERMINATE(TOOLPKG_EVENT_APPLICATION_ON_TERMINATE),
    ACTIVITY_CREATE(TOOLPKG_EVENT_ACTIVITY_ON_CREATE),
    ACTIVITY_START(TOOLPKG_EVENT_ACTIVITY_ON_START),
    ACTIVITY_RESUME(TOOLPKG_EVENT_ACTIVITY_ON_RESUME),
    ACTIVITY_PAUSE(TOOLPKG_EVENT_ACTIVITY_ON_PAUSE),
    ACTIVITY_STOP(TOOLPKG_EVENT_ACTIVITY_ON_STOP),
    ACTIVITY_DESTROY(TOOLPKG_EVENT_ACTIVITY_ON_DESTROY)
}

data class AppLifecycleHookParams(
    val context: Context,
    val extras: Map<String, Any?> = emptyMap()
)

data class AppLifecycleReplayEvent(
    val event: AppLifecycleEvent,
    val params: AppLifecycleHookParams
)

interface AppLifecycleHookPlugin {
    val id: String

    suspend fun onEvent(
        event: AppLifecycleEvent,
        params: AppLifecycleHookParams
    )
}

object AppLifecycleHookPluginRegistry {
    private const val TAG = "AppLifecycleHooks"
    private val plugins = CopyOnWriteArrayList<AppLifecycleHookPlugin>()
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()
    private var applicationCreateParams: AppLifecycleHookParams? = null
    private var applicationForegroundParams: AppLifecycleHookParams? = null

    @Synchronized
    fun register(plugin: AppLifecycleHookPlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
    }

    @Synchronized
    fun unregister(pluginId: String) {
        plugins.removeAll { it.id == pluginId }
    }

    suspend fun dispatch(
        event: AppLifecycleEvent,
        params: AppLifecycleHookParams
    ) {
        recordReplayableState(event, params)
        for (plugin in plugins) {
            try {
                plugin.onEvent(event, params)
            } catch (e: Exception) {
                AppLogger.e(TAG, "App lifecycle hook plugin failed: ${plugin.id}, event=${event.wireName}", e)
            }
        }
    }

    fun dispatchAsync(
        event: AppLifecycleEvent,
        params: AppLifecycleHookParams
    ) {
        dispatchScope.launch {
            dispatch(event = event, params = params)
        }
    }

    fun getReplayableApplicationEvents(): List<AppLifecycleReplayEvent> {
        synchronized(stateLock) {
            val replayEvents = mutableListOf<AppLifecycleReplayEvent>()
            applicationCreateParams?.let { params ->
                replayEvents.add(
                    AppLifecycleReplayEvent(
                        event = AppLifecycleEvent.APPLICATION_CREATE,
                        params = params
                    )
                )
            }
            applicationForegroundParams?.let { params ->
                replayEvents.add(
                    AppLifecycleReplayEvent(
                        event = AppLifecycleEvent.APPLICATION_FOREGROUND,
                        params = params
                    )
                )
            }
            return replayEvents
        }
    }

    private fun recordReplayableState(
        event: AppLifecycleEvent,
        params: AppLifecycleHookParams
    ) {
        synchronized(stateLock) {
            when (event) {
                AppLifecycleEvent.APPLICATION_CREATE -> {
                    applicationCreateParams = params
                }

                AppLifecycleEvent.APPLICATION_FOREGROUND -> {
                    applicationForegroundParams = params
                }

                AppLifecycleEvent.APPLICATION_BACKGROUND -> {
                    applicationForegroundParams = null
                }

                else -> {
                    // No replay state needed for other lifecycle events.
                }
            }
        }
    }
}

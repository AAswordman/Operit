package com.ai.assistance.operit.plugins.toolbox

import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.plugins.toolpkg.sortedByToolPkgLoadOrder
import com.ai.assistance.operit.plugins.OperitPlugin
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPlugin
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleReplayEvent
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAppLifecycleHookRegistration
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object ToolPkgAppLifecycleHookPlugin : AppLifecycleHookPlugin {
    private const val TAG = "ToolboxPlugin"
    @Volatile
    private var hooksByEvent: Map<String, List<ToolPkgAppLifecycleHookRegistration>> = emptyMap()

    override val id: String = "builtin.toolbox.toolpkg-app-lifecycle"

    override suspend fun onEvent(
        event: AppLifecycleEvent,
        params: AppLifecycleHookParams
    ) {
        val hooks = hooksByEvent[event.wireName.trim().lowercase()].orEmpty()
        dispatchHooks(
            hooks = hooks,
            params = params
        )
    }

    fun syncToolPkgRegistrations(
        activeContainers: List<ToolPkgContainerRuntime>
    ): Map<String, List<ToolPkgAppLifecycleHookRegistration>> {
        val previousHooksByEvent = hooksByEvent
        val nextHooksByEvent =
            activeContainers.flatMap { runtime ->
                    runtime.appLifecycleHooks.mapNotNull { hook ->
                        val normalizedEvent = hook.event.trim().lowercase()
                        if (normalizedEvent.isBlank()) {
                            null
                        } else {
                            ToolPkgAppLifecycleHookRegistration(
                                containerPackageName = runtime.packageName,
                                hookId = hook.id,
                                event = hook.event,
                                functionName = hook.function,
                                functionSource = hook.functionSource
                            )
                        }
                    }
                }
                .groupBy { hook -> hook.event.trim().lowercase() }
                .mapValues { (_, hooks) ->
                    hooks.sortedByToolPkgLoadOrder(
                        activeContainers = activeContainers,
                        containerPackageName = ToolPkgAppLifecycleHookRegistration::containerPackageName,
                        registrationId = ToolPkgAppLifecycleHookRegistration::hookId
                    )
                }
        hooksByEvent = nextHooksByEvent

        return nextHooksByEvent.mapValues { (eventKey, hooks) ->
            val previousSet = previousHooksByEvent[eventKey].orEmpty().toSet()
            hooks.filterNot { it in previousSet }
        }.filterValues { hooks -> hooks.isNotEmpty() }
    }

    suspend fun replayHooks(
        replayEvents: List<AppLifecycleReplayEvent>,
        hooksToReplayByEvent: Map<String, List<ToolPkgAppLifecycleHookRegistration>>
    ) {
        for (replayEvent in replayEvents) {
            val eventKey = replayEvent.event.wireName.trim().lowercase()
            val hooks = hooksToReplayByEvent[eventKey].orEmpty()
            if (hooks.isEmpty()) {
                continue
            }
            AppLogger.i(
                TAG,
                "Replaying app lifecycle event to newly loaded toolpkg hooks: event=${replayEvent.event.wireName}, hookCount=${hooks.size}"
            )
            dispatchHooks(
                hooks = hooks,
                params = replayEvent.params
            )
        }
    }

    private suspend fun dispatchHooks(
        hooks: List<ToolPkgAppLifecycleHookRegistration>,
        params: AppLifecycleHookParams
    ) {
        val context = params.context
        val packageManager =
            PackageManager.getInstance(
                context,
                AIToolHandler.getInstance(context)
            )

        for (hook in hooks) {
            val result =
                withContext(Dispatchers.IO) {
                    packageManager.runToolPkgMainHook(
                        containerPackageName = hook.containerPackageName,
                        functionName = hook.functionName,
                        event = hook.event,
                        pluginId = hook.hookId,
                        inlineFunctionSource = hook.functionSource,
                        eventPayload =
                            mapOf(
                                "extras" to params.extras
                            )
                    )
                }
            result.onFailure { error ->
                AppLogger.e(
                    TAG,
                    "ToolPkg app lifecycle hook failed: ${hook.containerPackageName}:${hook.hookId}",
                    error
                )
            }
        }
    }
}

object ToolboxPlugin : OperitPlugin {
    override val id: String = "builtin.toolbox"
    private val installed = AtomicBoolean(false)
    private val replayScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncAndReplayToolPkgLifecycleHooks(activeContainers)
        }

    override fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        AppLifecycleHookPluginRegistry.register(ToolPkgAppLifecycleHookPlugin)

        val context = OperitApplication.instance.applicationContext
        val packageManager = PackageManager.getInstance(context, AIToolHandler.getInstance(context))
        packageManager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
    }

    private fun syncAndReplayToolPkgLifecycleHooks(
        activeContainers: List<ToolPkgContainerRuntime>
    ) {
        val hooksToReplayByEvent = ToolPkgAppLifecycleHookPlugin.syncToolPkgRegistrations(
            activeContainers
        )
        val replayEvents = AppLifecycleHookPluginRegistry.getReplayableApplicationEvents()
        if (hooksToReplayByEvent.isEmpty() || replayEvents.isEmpty()) {
            return
        }
        replayScope.launch {
            ToolPkgAppLifecycleHookPlugin.replayHooks(
                replayEvents = replayEvents,
                hooksToReplayByEvent = hooksToReplayByEvent
            )
        }
    }
}

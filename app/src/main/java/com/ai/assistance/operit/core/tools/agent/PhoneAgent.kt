package com.ai.assistance.operit.core.tools.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.builtin.ui.StandardUITools
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.permissions.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.UIAutomationProgressOverlay
import com.ai.assistance.operit.ui.common.displays.UIOperationOverlay
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.android.LocaleUtils
import com.ai.assistance.operit.util.media.ImageOutputFormat
import com.ai.assistance.operit.util.media.ImagePoolManager
import com.ai.assistance.operit.util.media.ImageRegistrationOptions
import com.ai.assistance.operit.util.stream.collectRevisionAppliedText

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Configuration for the PhoneAgent. */
data class AgentConfig(
    val maxSteps: Int = 20
)

/** Result of a single agent step. */
data class StepResult(
    val success: Boolean,
    val finished: Boolean,
    val action: ParsedAgentAction?,
    val thinking: String?,
    val message: String? = null
)

/** Parsed action from the model's response. */
data class ParsedAgentAction(
    val metadata: String,
    val actionName: String?,
    val fields: Map<String, String>
)

private data class PrivilegedExecutionState(
    val isAdbOrHigher: Boolean
)

private fun resolvePrivilegedExecutionState(
    context: Context,
    androidPermissionPreferences: AndroidPermissionPreferences,
    onExperimentalFlagReadError: ((Exception) -> Unit)? = null
): PrivilegedExecutionState {
    val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
        ?: AndroidPermissionLevel.STANDARD

    val capabilities = PermissionCapabilityResolver.shellSnapshot(context, preferredLevel)
    var isAdbOrHigher = capabilities.hasPrivilegedShell

    if (isAdbOrHigher) {
        val experimentalEnabled = try {
            DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
        } catch (e: Exception) {
            onExperimentalFlagReadError?.invoke(e)
            true
        }
        if (!experimentalEnabled) {
            isAdbOrHigher = false
        }
    }

    return PrivilegedExecutionState(
        isAdbOrHigher = isAdbOrHigher
    )
}

/**
 * AI-powered agent for automating Android phone interactions.
 *
 * The agent uses a vision-language model to understand screen content
 * and decide on actions to complete user tasks.
 */
class PhoneAgent(
    private val context: Context,
    private val config: AgentConfig,
    private val uiService: AIService,
    private val actionHandler: ActionHandler,
    val agentId: String = "default",
    private val cleanupOnFinish: Boolean = (agentId != "default"),
) {
    private val performance = PhoneAgentPerformance()
    private val observationHistory = PhoneObservationHistory()
    private val planProgress = PhonePlanProgress()
    val performanceSummary: String get() = performance.summary()
    var completedSuccessfully: Boolean = false
        private set
    private var localSession: PhoneAgentLocalSession? = null
    private var imageOnlyRequested = false
    private var lastActionFeedback = ""
    private var mainInputLease: AutoCloseable? = null
    private var _stepCount = 0
    val stepCount: Int
        get() = _stepCount

    private val _contextHistory = mutableListOf<Pair<String, String>>()
    val contextHistory: List<Pair<String, String>>
        get() = _contextHistory.toList()

    private var pauseFlow: StateFlow<Boolean>? = null

    private val requiresVirtualScreen: Boolean = agentId.isNotBlank() && agentId != "default"
    private val isMainScreenAgent: Boolean = agentId.isBlank() || agentId == "default"

    init {
        actionHandler.setAgentId(agentId)
    }

    private suspend fun awaitIfPaused() {
        val flow = pauseFlow ?: return
        if (!flow.value) {
            return
        }
        localSession?.invalidate()
        AppLogger.d("PhoneAgent", "[$agentId] awaitIfPaused: entering pause loop, delay starting")
        val pausedAt = System.nanoTime()
        try {
            while (flow.value) {
                delay(200)
            }
        } finally {
            performance.pausedMs += (System.nanoTime() - pausedAt) / 1_000_000
            AppLogger.d("PhoneAgent", "[$agentId] awaitIfPaused: exiting pause loop")
        }
    }

    private fun hasShowerDisplay(logMessageSuffix: String): Boolean {
        if (isMainScreenAgent) return false
        return try {
            ShowerController.getDisplayId(agentId) != null || ShowerController.getVideoSize(agentId) != null
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] $logMessageSuffix", e)
            false
        }
    }

    private fun shouldUseShowerUi(hasShowerDisplay: Boolean): Boolean {
        // Main-screen Shower only borrows Shower for capture/input; the visible agent UI
        // should stay aligned with the regular main-screen automation experience.
        return !isMainScreenAgent && hasShowerDisplay
    }

    private suspend fun ensureRequiredVirtualScreenOrError(): String? {
        if (!requiresVirtualScreen) return null

        val permissionState = resolvePrivilegedExecutionState(
            context = context,
            androidPermissionPreferences = androidPermissionPreferences
        )
        if (!permissionState.isAdbOrHigher) {
            return context.getString(R.string.phone_agent_need_debug_permission)
        }

        if (hasShowerDisplay("Error checking Shower state before ensure")) {
            return null
        }

        val okServer = try {
            ShowerServerManager.ensureServerStarted(context)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] ensureRequiredVirtualScreen: ensureServerStarted failed", e)
            false
        }

        if (!okServer) {
            return context.getString(R.string.phone_agent_virtual_screen_service_start_failed)
        }

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi
        val bitrateKbps = try {
            DisplayPreferencesManager.getInstance(context).getVirtualDisplayBitrateKbps()
        } catch (_: Exception) {
            3000
        }

        val okDisplay = try {
            ShowerController.ensureDisplay(agentId, context, width, height, dpi, bitrateKbps = bitrateKbps)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] ensureRequiredVirtualScreen: ensureDisplay failed", e)
            false
        }

        val displayId = try {
            ShowerController.getDisplayId(agentId)
        } catch (_: Exception) {
            null
        }

        if (!okDisplay || displayId == null) {
            return context.getString(R.string.phone_agent_virtual_screen_create_failed)
        }

        try {
            VirtualDisplayOverlay.getInstance(context, agentId).show(displayId)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] ensureRequiredVirtualScreen: error showing overlay", e)
        }

        return null
    }

    private suspend fun prewarmShowerIfNeeded(
        hasShowerDisplayAtStart: Boolean,
        targetApp: String?
    ): Pair<Boolean, String?> {
        if (isMainScreenAgent) return Pair(false, null)
        if (hasShowerDisplayAtStart) return Pair(true, null)
        val targetAppForPrewarm = targetApp?.takeIf { it.isNotBlank() } ?: return Pair(false, null)

        val permissionState = resolvePrivilegedExecutionState(
            context = context,
            androidPermissionPreferences = androidPermissionPreferences
        )
        if (!permissionState.isAdbOrHigher) return Pair(false, null)
        AppLogger.d(
            "PhoneAgent",
            "[$agentId] run: prewarming Shower virtual display via Launch(app='$targetAppForPrewarm')"
        )
        val prewarmResult = try {
            actionHandler.executeAgentAction(
                ParsedAgentAction(
                    metadata = "do",
                    actionName = "Launch",
                    fields = mapOf(
                        "action" to "Launch",
                        "app" to targetAppForPrewarm
                    )
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Pair(false, context.getString(R.string.phone_agent_virtual_screen_prewarm_failed, e.message ?: ""))
        }

        val hasShowerAfterPrewarm = hasShowerDisplay("Error checking Shower state after prewarm")
        if (!hasShowerAfterPrewarm) {
            return Pair(false, prewarmResult.message ?: context.getString(R.string.phone_agent_virtual_screen_not_started))
        }

        return Pair(true, null)
    }

    private suspend fun prewarmMainScreenShowerIfPossible(): Boolean {
        if (!isMainScreenAgent) return false

        val permissionState = resolvePrivilegedExecutionState(
            context = context,
            androidPermissionPreferences = androidPermissionPreferences
        )
        if (!permissionState.isAdbOrHigher) return false
        val okServer = try {
            ShowerServerManager.ensureServerStarted(context)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] prewarmMainScreenShower: ensureServerStarted failed", e)
            false
        }
        if (!okServer) return false

        val okMainDisplay = try {
            ShowerController.prepareMainDisplay(agentId, context)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] prewarmMainScreenShower: prepareMainDisplay failed", e)
            false
        }
        if (okMainDisplay) {
            mainInputLease = ShowerController.acquireInputClient(agentId)
            AppLogger.d("PhoneAgent", "[$agentId] main-screen Shower prewarm ready (displayId=0), input client registered")
        }
        return okMainDisplay
    }

    private suspend fun prewarmMainScreenLaunchIfNeeded(targetApp: String?): String? {
        if (!isMainScreenAgent) return null
        val targetAppForPrewarm = targetApp?.takeIf { it.isNotBlank() } ?: return null

        AppLogger.d(
            "PhoneAgent",
            "[$agentId] run: prewarming main-screen launch via Launch(app='$targetAppForPrewarm')"
        )
        val prewarmResult = try {
            actionHandler.executeAgentAction(
                ParsedAgentAction(
                    metadata = "do",
                    actionName = "Launch",
                    fields = mapOf(
                        "action" to "Launch",
                        "app" to targetAppForPrewarm
                    )
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return "Exception while prewarming main-screen app launch: ${e.message}"
        }

        return if (prewarmResult.success) {
            null
        } else {
            prewarmResult.message ?: "Failed to prewarm main-screen app launch"
        }
    }

    /**
     * Run the agent to complete a task.
     *
     * @param task Natural language description of the task.
     * @param systemPrompt System prompt for the UI automation agent.
     * @param onStep Optional callback invoked after each step with the StepResult.
     * @return Final message from the agent.
     */
    suspend fun run(
        task: String,
        systemPrompt: String,
        onStep: (suspend (StepResult) -> Unit)? = null,
        isPausedFlow: StateFlow<Boolean>? = null,
        targetApp: String? = null
    ): String {
        val runStarted = System.nanoTime()
        val progress = if (isMainScreenAgent) UIAutomationProgressOverlay.getInstance(context) else null
        val runJob = currentCoroutineContext()[Job]
        var feedbackSessionStarted = false
        try {
            if (progress != null) {
                useFullscreenStatusIndicatorForAgent(context, agentId)
                progress.show(config.maxSteps, context.getString(R.string.phone_agent_waiting_startup),
                    onCancel = {
                        PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                        runJob?.cancel(CancellationException("User cancelled UI automation"))
                    },
                    onToggleTakeOver = { paused -> (isPausedFlow as? MutableStateFlow<Boolean>)?.value = paused },
                )
            }
            if (isMainScreenAgent) withContext(Dispatchers.Main.immediate) {
                UIOperationOverlay.getInstance(context).beginAgentSession()
                feedbackSessionStarted = true
            }
            return runSession(task, systemPrompt, onStep, isPausedFlow, targetApp)
        } finally {
            val lease = mainInputLease
            mainInputLease = null
            if (lease != null) withContext(NonCancellable + Dispatchers.IO) {
                try {
                    lease.close()
                    AppLogger.d("PhoneAgent", "[$agentId] main-screen input client released")
                } catch (e: Exception) {
                    AppLogger.e("PhoneAgent", "[$agentId] Failed to release main-screen input client", e)
                }
            }
            pauseFlow = null
            if (progress != null) withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (feedbackSessionStarted) UIOperationOverlay.getInstance(context).endAgentSession()
                progress.hide()
                progress.setBorderEnabled(false)
            }
            performance.totalMs = (System.nanoTime() - runStarted) / 1_000_000
            AppLogger.d("PhoneAgent", "[$agentId] performance: ${performance.summary()}")
        }
    }

    private suspend fun runSession(
        task: String,
        systemPrompt: String,
        onStep: (suspend (StepResult) -> Unit)? = null,
        isPausedFlow: StateFlow<Boolean>? = null,
        targetApp: String? = null
    ): String {
        completedSuccessfully = false
        reset()
        pauseFlow = isPausedFlow
        val floatingService = FloatingChatService.getInstance()
        val job = currentCoroutineContext()[Job]

        if (job != null) {
            PhoneAgentJobRegistry.register(agentId, job)
        } else {
            AppLogger.w("PhoneAgent", "[$agentId] run: no Job in coroutineContext, registry disabled")
        }

        val requiredVirtualScreenError = ensureRequiredVirtualScreenOrError()
        if (requiredVirtualScreenError != null) {
            return requiredVirtualScreenError
        }

        if (isMainScreenAgent) {
            val launchOnly = actionHandler.resolveLaunchOnlyTarget(task)
            if (launchOnly != null) {
                val outcome = actionHandler.executeLaunchOnly(launchOnly, ::awaitIfPaused)
                completedSuccessfully = outcome.first
                return outcome.second
            }
        }
        val mainScreenShowerReady = prewarmMainScreenShowerIfPossible()
        actionHandler.setMainScreenShowerPrepared(mainScreenShowerReady)
        if (mainScreenShowerReady) {
            val mainScreenPrewarmError = prewarmMainScreenLaunchIfNeeded(targetApp)
            if (mainScreenPrewarmError != null) {
                return mainScreenPrewarmError
            }
        }

        var hasShowerDisplayAtStart = hasShowerDisplay("Error checking Shower virtual display state")
        val (prewarmedShowerDisplay, prewarmError) = prewarmShowerIfNeeded(hasShowerDisplayAtStart, targetApp)
        if (prewarmError != null) {
            return prewarmError
        }
        hasShowerDisplayAtStart = prewarmedShowerDisplay

        var useShowerUi = shouldUseShowerUi(hasShowerDisplayAtStart)
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)
        var showerOverlay: VirtualDisplayOverlay? = if (useShowerUi) try {
            VirtualDisplayOverlay.getInstance(context, agentId)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] Error getting VirtualDisplayOverlay instance", e)
            null
        } else null

        val pausedMutable = isPausedFlow as? MutableStateFlow<Boolean>

        try {
            // Setup UI for agent run: hide window, then choose indicator based on whether Shower virtual display is active
            floatingService?.setFloatingWindowVisible(false)
            if (useShowerUi) {
                useShowerIndicatorForAgent(context, agentId)
            } else {
                useFullscreenStatusIndicatorForAgent(context, agentId)
            }
            if (useShowerUi) {
                showerOverlay?.showAutomationControls(
                    totalSteps = config.maxSteps,
                    initialStatus = context.getString(R.string.phone_agent_thinking),
                    onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                    onExit = {
                        PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                        job?.cancel(CancellationException("User cancelled UI automation"))
                    }
                )
            } else if (!isMainScreenAgent) {
                progressOverlay.show(
                    config.maxSteps,
                    context.getString(R.string.phone_agent_thinking),
                    onCancel = {
                        PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                        job?.cancel(CancellationException("User cancelled UI automation"))
                    },
                    onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                )
            }

            // The installed provider is main-display-only. Select capabilities once for this run.
            if (isMainScreenAgent && PermissionCapabilityResolver.uiSnapshot(context).canUseAccessibility) {
                localSession = PhoneAgentLocalSession(
                    context,
                    task = task,
                    performance = performance,
                    inputScope = { block -> actionHandler.withLocalInputFeedback(block) },
                    tapAt = { x, y -> actionHandler.tapNode(x, y) },
                    onStep = { index, total, action ->
                        val actionLabel = context.getString(when (action) {
                            PhoneLocalAction.Tap -> R.string.phone_agent_local_tap
                            PhoneLocalAction.Type -> R.string.phone_agent_local_type
                            PhoneLocalAction.Wait -> R.string.phone_agent_local_wait
                        })
                        progressOverlay.updateProgress(stepCount, config.maxSteps,
                            context.getString(R.string.phone_agent_local_step, index, total, actionLabel))
                    }
                )
            }
            val localPrompt = if (localSession != null) {
                PhoneLocalProtocol.prompt(useEnglish = !LocaleUtils.usesChineseContent(context))
            } else ""
            _contextHistory.add("system" to systemPrompt + PhoneLocalProtocol.launchPrompt(
                useEnglish = !LocaleUtils.usesChineseContent(context)
            ) + localPrompt)
            pauseFlow = isPausedFlow

            // First step with user prompt
            AppLogger.d("PhoneAgent", "[$agentId] run: starting first step for task='$task', hasShowerDisplayAtStart=$hasShowerDisplayAtStart")
            awaitIfPaused()
            var result = _executeStep(task, isFirst = true)
            val firstAction = result.action
            val firstStatusText = when {
                result.finished -> result.message ?: context.getString(R.string.phone_agent_completed)
                firstAction != null && firstAction.metadata == "do" -> {
                    val actionName = firstAction.actionName ?: ""
                    if (actionName.isNotEmpty()) context.getString(R.string.phone_agent_executing_action, actionName) else context.getString(R.string.phone_agent_executing)
                }
                else -> context.getString(R.string.phone_agent_thinking)
            }

            if (!useShowerUi) {
                val hasShowerNow = hasShowerDisplay("Error re-checking Shower virtual display state after first step")

                if (shouldUseShowerUi(hasShowerNow)) {
                    useShowerUi = true
                    try {
                        progressOverlay.hide()
                    } catch (_: Exception) {
                    }

                    try {
                        showerOverlay = VirtualDisplayOverlay.getInstance(context, agentId)
                    } catch (e: Exception) {
                        AppLogger.e("PhoneAgent", "[$agentId] Error getting VirtualDisplayOverlay instance when switching (first step)", e)
                        showerOverlay = null
                    }

                    if (showerOverlay != null) {
                        useShowerIndicatorForAgent(context, agentId)
                        showerOverlay?.showAutomationControls(
                            totalSteps = config.maxSteps,
                            initialStatus = firstStatusText,
                            onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                            onExit = {
                                PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                job?.cancel(CancellationException("User cancelled UI automation"))
                            }
                        )
                        showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText)
                    } else {
                        progressOverlay.show(
                            config.maxSteps,
                            "Thinking...",
                            onCancel = {
                                PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                job?.cancel(CancellationException("User cancelled UI automation"))
                            },
                            onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                        )
                        progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText)
                        useShowerUi = false
                    }
                } else {
                    progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText)
                }
            } else {
                showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText)
            }

            onStep?.invoke(result)

            if (result.finished) {
                completedSuccessfully = result.success && result.action?.metadata == "finish"
                return result.message ?: "Task completed"
            }

            // Continue until finished or max steps reached
            while (_stepCount < config.maxSteps) {
                awaitIfPaused()
                result = _executeStep(null, isFirst = false)
                val action = result.action
                val statusText = when {
                    result.finished -> result.message ?: context.getString(R.string.phone_agent_completed)
                    action != null && action.metadata == "do" -> {
                        val actionName = action.actionName ?: ""
                        if (actionName.isNotEmpty()) context.getString(R.string.phone_agent_executing_action, actionName) else context.getString(R.string.phone_agent_executing)
                    }
                    else -> context.getString(R.string.phone_agent_thinking)
                }

                if (!useShowerUi) {
                    val hasShowerNow = hasShowerDisplay("Error re-checking Shower state in loop")

                    if (shouldUseShowerUi(hasShowerNow)) {
                        useShowerUi = true
                        progressOverlay.hide()
                        showerOverlay = VirtualDisplayOverlay.getInstance(context, agentId)
                        if (showerOverlay != null) {
                            useShowerIndicatorForAgent(context, agentId)
                            showerOverlay?.showAutomationControls(
                                totalSteps = config.maxSteps,
                                initialStatus = statusText,
                                onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                                onExit = {
                                    PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                    job?.cancel(CancellationException("User cancelled UI automation"))
                                }
                            )
                            showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText)
                        } else {
                            progressOverlay.show(
                                config.maxSteps,
                                "Thinking...",
                                onCancel = {
                                    PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation")
                                    job?.cancel(CancellationException("User cancelled UI automation"))
                                },
                                onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                            )
                            progressOverlay.updateProgress(stepCount, config.maxSteps, statusText)
                            useShowerUi = false
                        }
                    } else {
                        progressOverlay.updateProgress(stepCount, config.maxSteps, statusText)
                    }
                } else {
                    showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText)
                }

                onStep?.invoke(result)

                if (result.finished) {
                    completedSuccessfully = result.success && result.action?.metadata == "finish"
                    return result.message ?: "Task completed"
                }
            }

            return "Max steps reached"
        } finally {
            AppLogger.d("PhoneAgent", "[$agentId] run: finishing, restoring UI")
            pauseFlow = null
            floatingService?.setFloatingWindowVisible(true)
            if (isMainScreenAgent) {
                floatingService?.setStatusIndicatorVisible(false)
            } else {
                clearAgentIndicators(context, agentId)
            }
            if (useShowerUi) {
                showerOverlay?.hideAutomationControls()
            } else if (!isMainScreenAgent) {
                progressOverlay.hide()
            }
            if (cleanupOnFinish) {
                AppLogger.d("PhoneAgent", "[$agentId] run: cleaning up agent session")
                try {
                    VirtualDisplayOverlay.hide(agentId)
                } catch (_: Exception) {
                }
                try {
                    ShowerController.shutdown(agentId)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Reset the agent state for a new task. */
    fun reset() {
        _contextHistory.clear()
        _stepCount = 0
        performance.reset()
        observationHistory.reset()
        planProgress.reset()
        actionHandler.resetTaskState()
        localSession = null
        imageOnlyRequested = false
        lastActionFeedback = ""
    }

    /** Execute a single step of the agent loop. */
    private suspend fun _executeStep(userPrompt: String?, isFirst: Boolean): StepResult {
        _stepCount++
        val imageOnly = localSession != null && imageOnlyRequested
        imageOnlyRequested = false
        if (imageOnly) localSession?.invalidate()
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: begin, step=$_stepCount")
        if (isMainScreenAgent) UIAutomationProgressOverlay.getInstance(context).updateProgress(
            _stepCount, config.maxSteps, context.getString(
                if (localSession != null && !imageOnly) R.string.phone_agent_reading_nodes else R.string.phone_agent_reading_screenshot
            )
        )

        val observationStarted = System.nanoTime()
        val includeImage = imageOnly || localSession == null
        var screenshotAvailable = false
        val screenInfo = buildString {
            appendLine("[OBSERVATION_MODE] " + if (localSession != null && !imageOnly) "ACCESSIBILITY_MAIN" else "SCREENSHOT")
            appendLine("[ACTION_BUDGET] ${config.maxSteps - performance.actions}")
            val session = localSession
            if (session != null && !imageOnly) {
                try {
                    val expectedPackage = actionHandler.launchState.pendingPackage
                    if (expectedPackage != null) actionHandler.awaitStartupForeground(::awaitIfPaused)
                    val state = PhoneStartupObservation().read(
                        deadlineMs = if (expectedPackage != null) actionHandler.launchState.deadlineMs else null,
                        checkpoint = ::awaitIfPaused,
                    ) { session.observe(expectedPackage) }
                    actionHandler.launchState.observationAvailable(requireNotNull(session.lastPackageName))
                    val stateLine = state.lineSequence().firstOrNull { it.startsWith("[UI_STATE] ") }
                    if (stateLine != null) observationHistory.record(_contextHistory.size, stateLine)
                    appendLine(state)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    actionHandler.launchState.observationUnavailable()
                    appendLine("[UI_STATE_ERROR] ${e.message}. UI observation is unavailable; this does not mean an accepted launch failed. Use Wait or Inspect, not another Launch of the same app. No node action may run without a valid state.")
                }
            }
            if (includeImage) {
                val screenshotStarted = System.nanoTime()
                performance.screenshotAttempts++
                val screenshotLink = try {
                    actionHandler.captureScreenshotForAgent()
                } finally {
                    performance.screenshotNs += System.nanoTime() - screenshotStarted
                }
                if (screenshotLink != null) {
                    performance.screenshots++
                    screenshotAvailable = true
                    appendLine("[SCREENSHOT] $screenshotLink")
                } else {
                    appendLine("[SCREENSHOT_ERROR] No image is available for this observation.")
                }
            }
            actionHandler.launchState.prompt().takeIf { it.isNotEmpty() }?.let { appendLine(it) }
            appendLine(actionHandler.coordinatePrompt())
            if (lastActionFeedback.isNotBlank()) appendLine("[ACTION_RESULT] $lastActionFeedback")
        }.trim()
        performance.observations++
        performance.observationMs += (System.nanoTime() - observationStarted) / 1_000_000
        val userMessage = if (isFirst) "$userPrompt\n\n$screenInfo" else screenInfo

        _contextHistory.add("user" to userMessage)

        if (isMainScreenAgent) {
            val nodeCount = localSession?.lastNodeCount
            val status = when {
                imageOnly -> context.getString(R.string.phone_agent_waiting_controller)
                nodeCount != null -> context.getString(R.string.phone_agent_nodes_ready, nodeCount, localSession?.lastCandidateCount ?: 0, localSession?.lastSentCount ?: 0)
                localSession != null -> context.getString(R.string.phone_agent_nodes_failed)
                else -> context.getString(R.string.phone_agent_waiting_controller)
            }
            UIAutomationProgressOverlay.getInstance(context).updateProgress(_stepCount, config.maxSteps, status)
        }
        val modelStarted = System.nanoTime()
        performance.modelCalls++
        val fullResponse = try {
            uiService.sendMessage(
                context = context,
                chatHistory = observationHistory.forModel(_contextHistory).toPromptTurns(),
                enableThinking = false,
                stream = true,
                preserveThinkInHistory = true
            ).collectRevisionAppliedText().trim()
        } finally {
            val elapsed = (System.nanoTime() - modelStarted) / 1_000_000
            performance.modelMs += elapsed
            if (includeImage) {
                performance.imageModelCalls++
                performance.imageModelMs += elapsed
            } else {
                performance.nodeModelCalls++
                performance.nodeModelMs += elapsed
            }
        }
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: AI response collected, length=${fullResponse.length}")

        val (thinking, answer) = parseThinkingAndAction(fullResponse)
        val historyEntry = "<think>$thinking</think><answer>$answer</answer>"
        _contextHistory.add("assistant" to historyEntry)

        val isLocalCommand = answer.trim().startsWith("{")
        val parsedAction = if (isLocalCommand) {
            ParsedAgentAction("do", "Sequence", emptyMap())
        } else parseAgentAction(answer)
        actionHandler.removeImagesFromLastUserMessage(_contextHistory)

        if (parsedAction.metadata == "finish") {
            val message = parsedAction.fields["message"] ?: "Task finished."
            return StepResult(success = true, finished = true, action = parsedAction, thinking = thinking, message = message)
        }

        if (parsedAction.metadata == "do") {
            if (!isLocalCommand && parsedAction.actionName !in PhoneLocalProtocol.executableActions) {
                lastActionFeedback = "Unsupported action ${parsedAction.actionName}; use only ${PhoneLocalProtocol.executableActions.joinToString()}. No input was dispatched."
                return StepResult(false, false, parsedAction, thinking, lastActionFeedback)
            }
            awaitIfPaused()
            if (isMainScreenAgent) UIAutomationProgressOverlay.getInstance(context).updateProgress(
                _stepCount, config.maxSteps,
                context.getString(R.string.phone_agent_executing_action, parsedAction.actionName.orEmpty())
            )
            val executionStarted = System.nanoTime()
            val pausedBefore = performance.pausedMs
            if (isLocalCommand) {
                val localResult = try {
                    val session = localSession
                    if (session == null) {
                        PhoneAgentLocalSession.Result("Local sequences require a main-screen accessibility session.", 0, 0, false, false, false)
                    } else {
                        session.execute(answer.trim(), config.maxSteps - performance.actions, ::awaitIfPaused)
                    }
                } finally {
                    performance.executionMs += ((System.nanoTime() - executionStarted) / 1_000_000 - (performance.pausedMs - pausedBefore)).coerceAtLeast(0L)
                }
                performance.actions += localResult.attempted
                performance.conditionWaitMs += localResult.waitMs
                if (localResult.sequence) performance.localSequences++
                val stalled = planProgress.record(localResult.verifiedActions, localResult.inspect)
                // Observation changes are explicit; rejected node actions do not silently request
                // an image and discard the result snapshot that is already available.
                imageOnlyRequested = when (localResult.actionName) {
                    "Inspect" -> true
                    "Observe", "Tap", "Type", "Sequence" -> false
                    else -> imageOnly
                }
                lastActionFeedback = localResult.feedback
                if (!localResult.inspect && localResult.verifiedActions == 0) {
                    lastActionFeedback += " No verified UI change in ${planProgress.stalledPlans} local plan(s). Inspect the returned node state; use Inspect when visual context is needed."
                }
                AppLogger.d("PhoneAgent", "[$agentId] local_result: success=${localResult.success}, attempted=${localResult.attempted}, verified=${localResult.verifiedActions}, inspect=${localResult.inspect}, stalled=${planProgress.stalledPlans}")
                val reportedAction = parsedAction.copy(actionName = localResult.actionName ?: if (localResult.inspect) "Inspect" else "Sequence")
                if (stalled) {
                    return StepResult(false, true, reportedAction, thinking,
                        "Stopped after three local plans without a verified UI action. Task not completed. " + localResult.feedback)
                }
                return StepResult(localResult.success, false, reportedAction, thinking, lastActionFeedback)
            }
            if (!screenshotAvailable && parsedAction.actionName in setOf("Tap", "Swipe")) {
                imageOnlyRequested = imageOnly
                localSession?.invalidate()
                lastActionFeedback = "No coordinate action was sent: this observation has no screenshot. Use an observed node ref or request Inspect before selecting coordinates."
                return StepResult(false, false, parsedAction, thinking, lastActionFeedback)
            }
            if (performance.actions >= config.maxSteps && parsedAction.actionName != "Take_over") {
                return StepResult(false, true, parsedAction, thinking, "Max steps reached")
            }
            performance.actions++
            planProgress.reset()
            imageOnlyRequested = imageOnly
            localSession?.invalidate()
            val execResult = try {
                actionHandler.executeAgentAction(parsedAction)
            } finally {
                performance.executionMs += ((System.nanoTime() - executionStarted) / 1_000_000 - (performance.pausedMs - pausedBefore)).coerceAtLeast(0L)
            }
            if (execResult.success && parsedAction.actionName == "Home") actionHandler.launchState.leftApp()
            lastActionFeedback = "success=${execResult.success}; ${execResult.message.orEmpty()}; verify the observed result"
            if (execResult.shouldFinish) {
                 return StepResult(success = execResult.success, finished = true, action = parsedAction, thinking = thinking, message = execResult.message)
            }
            return StepResult(success = execResult.success, finished = false, action = parsedAction, thinking = thinking, message = execResult.message)
        }

        val errorMessage = "Unknown action format: ${parsedAction.metadata}"
        return StepResult(success = false, finished = true, action = parsedAction, thinking = thinking, message = errorMessage)
    }

    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun parseThinkingAndAction(content: String): Pair<String?, String> {
        val full = content.trim()
        val taggedAnswer = extractTagContent(full, "answer")
        if (taggedAnswer != null) return extractTagContent(full, "think") to taggedAnswer
        if (full.startsWith("{")) return null to full
        val finishMarker = "finish(message="
        val finishIndex = full.indexOf(finishMarker)
        if (finishIndex >= 0) {
            val thinking = full.substring(0, finishIndex).trim().ifEmpty { null }
            val action = full.substring(finishIndex).trim()
            return thinking to action
        }
        val doMarker = "do(action="
        val doIndex = full.indexOf(doMarker)
        if (doIndex >= 0) {
            val thinking = full.substring(0, doIndex).trim().ifEmpty { null }
            val action = full.substring(doIndex).trim()
            return thinking to action
        }
        val thinkTag = extractTagContent(full, "think")
        val answerTag = extractTagContent(full, "answer")
        if (thinkTag != null || answerTag != null) {
            return thinkTag to (answerTag ?: full)
        }
        return null to full
    }

    private fun parseAgentAction(raw: String): ParsedAgentAction {
        val original = raw.trim()
        val finishIndex = original.lastIndexOf("finish(")
        val doIndex = original.lastIndexOf("do(")
        val startIndex = when {
            finishIndex >= 0 && doIndex >= 0 -> maxOf(finishIndex, doIndex)
            finishIndex >= 0 -> finishIndex
            doIndex >= 0 -> doIndex
            else -> -1
        }

        val trimmed = if (startIndex >= 0) original.substring(startIndex).trim() else original

        if (trimmed.startsWith("finish")) {
            val messageRegex = Regex("""finish\s*\(\s*message\s*=\s*\"(.*)\"\s*\)""", RegexOption.DOT_MATCHES_ALL)
            val message = messageRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
            return ParsedAgentAction(metadata = "finish", actionName = null, fields = mapOf("message" to message))
        }

        if (!trimmed.startsWith("do")) {
            return ParsedAgentAction(metadata = "unknown", actionName = null, fields = emptyMap())
        }

        val inner = trimmed.removePrefix("do").trim().removeSurrounding("(", ")")
        val fields = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)\s*=\s*(?:\[(.*?)\]|\"(.*?)\"|'([^']*)'|([^,)]+))""")
        regex.findAll(inner).forEach { matchResult ->
            val key = matchResult.groupValues[1]
            val value = matchResult.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            fields[key] = value
        }

        return ParsedAgentAction(metadata = "do", actionName = fields["action"], fields = fields)
    }
}

private suspend fun useFullscreenStatusIndicatorForAgent(context: Context, agentId: String) {
    // Automation owns a dedicated border. Chat/screenshot visibility must not imply tool completion.
    FloatingChatService.getInstance()?.setStatusIndicatorVisible(false)
    UIAutomationProgressOverlay.getInstance(context).setBorderEnabled(true)
}

private suspend fun useShowerIndicatorForAgent(context: Context, agentId: String) {
    UIAutomationProgressOverlay.getInstance(context).setBorderEnabled(false)
    try {
        val overlay = VirtualDisplayOverlay.getInstance(context, agentId)
        overlay.setShowerBorderVisible(true)
    } catch (e: Exception) {
        AppLogger.e("PhoneAgent", "[$agentId] Error enabling Shower border indicator", e)
    }
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(false)
}

private suspend fun clearAgentIndicators(context: Context, agentId: String) {
    UIAutomationProgressOverlay.getInstance(context).setBorderEnabled(false)
    try {
        val overlay = VirtualDisplayOverlay.getInstance(context, agentId)
        overlay.setShowerBorderVisible(false)
    } catch (e: Exception) {
        AppLogger.e("PhoneAgent", "[$agentId] Error disabling Shower border indicator", e)
    }
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(false)
}

/** Handles the execution of parsed actions. */
class ActionHandler(
    private val context: Context,
    private var screenWidth: Int,
    private var screenHeight: Int,
    private val toolImplementations: ToolImplementations
) {
    private var agentId: String = "default"
    private var mainScreenShowerPrepared: Boolean = false
    private var appPackagesSyncedFromTool = false
    private val aiToolManager: AIToolHandler by lazy { AIToolHandler.getInstance(context) }

    fun setAgentId(id: String) {
        agentId = id
    }

    fun setMainScreenShowerPrepared(prepared: Boolean) {
        mainScreenShowerPrepared = prepared
    }

    data class ActionExecResult(
        val success: Boolean,
        val shouldFinish: Boolean,
        val message: String?
    )

    companion object {
        private const val POST_LAUNCH_DELAY_MS = 3000L
        private const val POST_NON_WAIT_ACTION_DELAY_MS = 500L
    }

    private data class ShowerUsageContext(
        val isAdbOrHigher: Boolean,
        val showerDisplayId: Int?
    ) {
        val hasShowerDisplay: Boolean get() = showerDisplayId != null
        val canUseShowerForInput: Boolean get() = isAdbOrHigher && showerDisplayId != null
    }

    private fun isMainScreenAgent(): Boolean = agentId.isBlank() || agentId == "default"

    private fun resolveShowerUsageContext(): ShowerUsageContext {
        if (isMainScreenAgent()) {
            val canUseShower = mainScreenShowerPrepared &&
                resolvePrivilegedExecutionState(context, androidPermissionPreferences).isAdbOrHigher
            return ShowerUsageContext(
                isAdbOrHigher = canUseShower,
                showerDisplayId = if (canUseShower) 0 else null
            )
        }
        val permissionState = resolvePrivilegedExecutionState(
            context = context,
            androidPermissionPreferences = androidPermissionPreferences,
            onExperimentalFlagReadError = { e ->
                AppLogger.e("ActionHandler", "[$agentId] Error reading experimental virtual display flag", e)
            }
        )
        val showerId = try {
            ShowerController.getDisplayId(agentId)
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "[$agentId] Error getting Shower display id", e)
            null
        }
        return ShowerUsageContext(isAdbOrHigher = permissionState.isAdbOrHigher, showerDisplayId = showerId)
    }

    private var screenshotHiddenDepth = 0

    internal suspend fun tapNode(x: Int, y: Int): Boolean =
        dispatchTapAtPixels(x, y, context.getString(R.string.phone_agent_source_nodes)).success

    private suspend fun dispatchTapAtPixels(x: Int, y: Int, source: String): ActionExecResult {
        val channel = resolveShowerUsageContext()
        // Resolve one channel before dispatch; an unconfirmed injection is never resent on another.
        val useAccessibility = isMainScreenAgent() && PermissionCapabilityResolver.uiSnapshot(context).canUseAccessibility
        val inputSource = when {
            channel.canUseShowerForInput -> "shower"
            useAccessibility -> "accessibility"
            else -> "ui_tool"
        }
        AppLogger.d("ActionHandler", "[$agentId] tap_request: source=$inputSource pixel=($x,$y) origin=$source")
        val feedback = if (isMainScreenAgent()) UIOperationOverlay.getInstance(context) else null
        if (feedback != null) withContext(Dispatchers.Main.immediate) { feedback.hideImmediately() }
        var delivered = false
        try {
            return withAgentInputFeedback {
                if (channel.canUseShowerForInput) {
                    val confirmed = ShowerController.tap(agentId, x, y)
                    if (confirmed) ok(message = "Tap injection completed at ($x,$y); verify the app response")
                    else fail(shouldFinish = true, message = "Shower TAP injection unconfirmed at ($x,$y); task incomplete, do not automatically resend")
                } else if (useAccessibility) {
                    val accepted = com.ai.assistance.operit.data.repository.UIHierarchyManager.performClick(context, x, y)
                    if (accepted) ok(message = "Accessibility tap request accepted at ($x,$y); verify the app response")
                    else fail(message = "Accessibility tap request unconfirmed at ($x,$y); do not automatically resend")
                } else {
                    val params = withDisplayParam(listOf(ToolParameter("x", x.toString()), ToolParameter("y", y.toString())))
                    val result = toolImplementations.tap(AITool("tap", params))
                    if (result.success) ok(message = "Tap request accepted at ($x,$y); verify the app response")
                    else fail(message = "Tap at ($x,$y): ${result.error}")
                }
            }.also { delivered = it.success }
        } finally {
            if (feedback != null) withContext(NonCancellable + Dispatchers.Main.immediate) {
                feedback.showAgentTap(x, y, delivered, source)
            }
        }
    }

    internal suspend fun <T> withLocalInputFeedback(block: suspend () -> T): T =
        withAgentInputFeedback(block)

    private suspend fun <T> withAgentInputFeedback(block: suspend () -> T): T {
        if (!isMainScreenAgent()) return block()
        val progress = UIAutomationProgressOverlay.getInstance(context)
        try {
            progress.setInputPassthrough(true)
            return block()
        } finally {
            withContext(NonCancellable) { progress.setInputPassthrough(false) }
        }
    }

    suspend fun captureScreenshotForAgent(): String? {
        val showerCtx = resolveShowerUsageContext()
        return withAgentUiHiddenForScreenshot(showerCtx) {
            var screenshotLink: String? = null
            var dimensions: Pair<Int, Int>? = null
            if (!isMainScreenAgent() && showerCtx.canUseShowerForInput) {
                val (link, dims) = captureScreenshotViaShower()
                screenshotLink = link
                dimensions = dims
            }
            if (screenshotLink == null) {
                val (bitmap, fallbackDims) = toolImplementations.captureScreenshotBitmap(buildScreenshotTool())
                if (bitmap != null) {
                    val (compressedLink, rawDims) = saveCompressedScreenshotFromBitmap(bitmap)
                    screenshotLink = compressedLink
                    dimensions = fallbackDims ?: rawDims
                    bitmap.recycle()
                }
            }
            if (dimensions != null) {
                screenWidth = dimensions.first
                screenHeight = dimensions.second
            }
            screenshotLink
        }
    }

    private fun buildScreenshotTool(): AITool {
        return AITool(
            name = "capture_screenshot",
            parameters = emptyList()
        )
    }

    private suspend fun captureScreenshotViaShower(): Pair<String?, Pair<Int, Int>?> {
        return try {
            check(!isMainScreenAgent()) { "Main-screen capture uses the main-screen screenshot tools" }
            val pngBytes = VirtualDisplayOverlay.getInstance(context, agentId).captureCurrentFramePng()
            if (pngBytes == null || pngBytes.isEmpty()) {
                AppLogger.w("ActionHandler", "[$agentId] Shower WS screenshot returned no data")
                Pair(null, null)
            } else {
                val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                if (bitmap == null) {
                    AppLogger.e("ActionHandler", "[$agentId] Shower screenshot: failed to decode bytes")
                    Pair(null, null)
                } else {
                    val result = saveCompressedScreenshotFromBitmap(bitmap)
                    bitmap.recycle()
                    result
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ActionHandler", "[$agentId] Shower screenshot failed", e)
            Pair(null, null)
        }
    }

    private fun saveCompressedScreenshotFromBitmap(bitmap: Bitmap): Pair<String?, Pair<Int, Int>?> {
        return try {
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height
            val imageId = ImagePoolManager.addImageFromBitmap(
                bitmap = bitmap,
                mimeType = if (bitmap.hasAlpha()) "image/png" else "image/jpeg",
                options = buildScreenshotRegistrationOptions()
            )
            if (imageId == "error") {
                Pair(null, null)
            } else {
                Pair("<link type=\"image\" id=\"$imageId\"></link>", Pair(originalWidth, originalHeight))
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "[$agentId] Error saving compressed screenshot", e)
            Pair(null, null)
        }
    }

    private fun buildScreenshotRegistrationOptions(): ImageRegistrationOptions {
        val prefs = DisplayPreferencesManager.getInstance(context)
        val format = prefs.getScreenshotFormat().uppercase(Locale.getDefault())
        val outputFormat =
            when (format) {
                "JPG", "JPEG" -> ImageOutputFormat.JPEG
                else -> ImageOutputFormat.PNG
            }
        return ImageRegistrationOptions(
            scalePercent = prefs.getScreenshotScalePercent().coerceIn(1, 100),
            outputFormat = outputFormat,
            jpegQuality = prefs.getScreenshotQuality().coerceIn(1, 100),
            normalizeExif = true,
            maxLongEdge = 0
        )
    }

    fun removeImagesFromLastUserMessage(history: MutableList<Pair<String, String>>) {
        val lastUserMessageIndex = history.indexOfLast { it.first == "user" }
        if (lastUserMessageIndex != -1) {
            val (role, content) = history[lastUserMessageIndex]
            if (content.contains("<link type=\"image\"")) {
                val stripped = content.replace(Regex("""<link type=\"image\".*?</link>"""), "").trim()
                history[lastUserMessageIndex] = role to stripped
            }
        }
    }

    internal val launchState = PhoneAppLaunchState()

    internal fun resetTaskState() {
        launchState.reset()
        mainScreenShowerPrepared = false
    }

    private suspend fun isAppForeground(packageName: String): Boolean? = withContext(Dispatchers.IO) {
        val activity = com.ai.assistance.operit.data.repository.UIHierarchyManager.getCurrentActivityName(context)
        if (activity.isNullOrBlank()) return@withContext null
        if ('/' in activity && activity.substringBefore('/') != packageName) return@withContext false
        val flattened = PhoneLaunchRequest.foregroundComponent(activity, packageName) ?: return@withContext null
        val component = android.content.ComponentName.unflattenFromString(flattened) ?: return@withContext null
        try {
            context.packageManager.getActivityInfo(component, 0).packageName == packageName
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }

    private suspend fun awaitForeground(
        packageName: String,
        deadlineMs: Long,
        waitForUnavailable: Boolean,
        checkpoint: suspend () -> Unit,
    ): Boolean {
        while (true) {
            checkpoint()
            val foreground = isAppForeground(packageName)
            if (foreground == true) {
                launchState.foregroundConfirmed()
                return true
            }
            if (foreground == null && !waitForUnavailable) return false
            val remaining = deadlineMs - System.nanoTime() / 1_000_000
            if (remaining <= 0) return false
            delay(minOf(200L, remaining))
        }
    }

    internal suspend fun awaitStartupForeground(checkpoint: suspend () -> Unit) {
        val packageName = launchState.pendingPackage ?: return
        val deadline = requireNotNull(launchState.deadlineMs)
        awaitForeground(packageName, deadline, waitForUnavailable = false, checkpoint = checkpoint)
    }

    internal suspend fun resolveLaunchOnlyTarget(task: String): PhoneLaunchTarget? = withContext(Dispatchers.IO) {
        val query = PhoneLaunchRequest.appQuery(task) ?: return@withContext null
        if (!PermissionCapabilityResolver.uiSnapshot(context).canUseAccessibility) return@withContext null
        try {
            val pkg = resolveAppPackageName(query)
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            val label = context.packageManager.getApplicationLabel(info).toString()
            if (!PhoneLaunchRequest.matchesInstalledApp(task, label, pkg) || context.packageManager.getLaunchIntentForPackage(pkg) == null) {
                return@withContext null
            }
            PhoneLaunchTarget(pkg, label)
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }

    internal suspend fun executeLaunchOnly(target: PhoneLaunchTarget, checkpoint: suspend () -> Unit): Pair<Boolean, String> {
        checkpoint()
        val launched = executeAgentAction(ParsedAgentAction("do", "Launch", mapOf("app" to target.packageName)))
        if (!launched.success) return false to launched.message.orEmpty()
        val confirmed = awaitForeground(target.packageName, requireNotNull(launchState.deadlineMs),
            waitForUnavailable = true, checkpoint = checkpoint)
        return confirmed to context.getString(
            if (confirmed) R.string.phone_agent_launch_confirmed else R.string.phone_agent_launch_unconfirmed,
            target.label,
        )
    }

    suspend fun executeAgentAction(parsed: ParsedAgentAction): ActionExecResult {
        val actionName = parsed.actionName ?: return fail(message = "Missing action name")
        val fields = parsed.fields

        val showerCtx = resolveShowerUsageContext()
        if (!isMainScreenAgent() && !showerCtx.isAdbOrHigher) {
            return fail(shouldFinish = true, message = context.getString(R.string.phone_agent_need_debug_permission))
        }
        return when (actionName) {
            "Launch" -> {
                val app = fields["app"]?.takeIf { it.isNotBlank() } ?: return fail(message = "No app name specified for Launch")
                val packageName = resolveAppPackageName(app)
                if (isMainScreenAgent() && launchState.suppressesLaunch(packageName)) {
                    return ok(message = "No Launch dispatched: the launch request for $packageName was already accepted. Missing UI observations do not revoke it; continue with Wait or Inspect.")
                }
                try {
                    if (showerCtx.isAdbOrHigher && !isMainScreenAgent()) {
                        val pm = context.packageManager
                        val hasLaunchableTarget = pm.getLaunchIntentForPackage(packageName) != null
                        ensureVirtualDisplayIfAdbOrHigher()

                        val metrics = context.resources.displayMetrics
                        val width = metrics.widthPixels
                        val height = metrics.heightPixels
                        val dpi = metrics.densityDpi
                        val bitrateKbps = try {
                            DisplayPreferencesManager.getInstance(context).getVirtualDisplayBitrateKbps()
                        } catch (e: Exception) { 3000 }

                        val created = ShowerController.ensureDisplay(agentId, context, width, height, dpi, bitrateKbps = bitrateKbps)
                        val launched = if (created && hasLaunchableTarget) ShowerController.launchApp(agentId, packageName) else false

                        if (created && launched) {
                            try {
                                VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(packageName)
                            } catch (_: Exception) {}
                            useShowerIndicatorForAgent(context, agentId)
                            delay(POST_LAUNCH_DELAY_MS)
                            ok()
                        } else {
                            val desktopPackage = "com.ai.assistance.operit.desktop"
                            val desktopLaunched = ShowerController.launchApp(agentId, desktopPackage)
                            if (desktopLaunched) {
                                try {
                                    VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(desktopPackage)
                                } catch (_: Exception) {}
                                useShowerIndicatorForAgent(context, agentId)
                                delay(POST_LAUNCH_DELAY_MS)
                                ok()
                            } else {
                                fail(message = "Failed to launch on Shower virtual display")
                            }
                        }
                    } else {
                        val result = aiToolManager.executeTool(
                            AITool("start_app", listOf(ToolParameter("package_name", packageName)))
                        )
                        if (result.success) {
                            launchState.accepted(packageName)
                            ok(message = "Launch request accepted for $packageName; window observation is pending. Do not relaunch because the first node read is unavailable.")
                        } else {
                            fail(message = result.error ?: "Failed to launch app: $packageName")
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    fail(message = "Exception while launching app: ${e.message}")
                }
            }
            "Tap" -> {
                val element = fields["element"] ?: return fail(message = "No element for Tap")
                val (x, y) = parseRelativePoint(element)
                    ?: return fail(message = "Tap requires exactly two normalized integers in 0..999: $element")
                val exec = dispatchTapAtPixels(x, y, context.getString(R.string.phone_agent_source_coordinates))
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Type" -> {
                val text = fields["text"] ?: ""
                val exec = withAgentInputFeedback {
                    if (showerCtx.canUseShowerForInput) {
                        try {
                            var cleared = false
                            val selectedAll = ShowerController.keyWithMeta(agentId, KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
                            if (selectedAll) {
                                delay(80)
                                cleared = ShowerController.key(agentId, KeyEvent.KEYCODE_DEL)
                            }
                            if (!cleared) {
                                cleared = ShowerController.key(agentId, KeyEvent.KEYCODE_CLEAR)
                            }
                            if (!cleared) {
                                ShowerController.key(agentId, KeyEvent.KEYCODE_MOVE_END)
                                repeat(200) {
                                    ShowerController.key(agentId, KeyEvent.KEYCODE_DEL)
                                }
                            }
                            delay(300)
                            if (text.isEmpty()) return@withAgentInputFeedback ok()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                ?: return@withAgentInputFeedback fail(message = "Clipboard unavailable")
                            clipboard.setPrimaryClip(ClipData.newPlainText("operit_input", text))
                            delay(100)
                            val pasted = ShowerController.key(agentId, KeyEvent.KEYCODE_PASTE)
                            if (pasted) ok() else fail(message = "Shower PASTE failed")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            fail(message = "Error typing via Shower: ${e.message}")
                        }
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("text", text)))
                        val result = toolImplementations.setInputText(AITool("set_input_text", params))
                        if (result.success) ok() else fail(message = result.error ?: "Type failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Swipe" -> {
                val start = fields["start"] ?: return fail(message = "Missing swipe start")
                val end = fields["end"] ?: return fail(message = "Missing swipe end")
                val (sx, sy) = parseRelativePoint(start) ?: return fail(message = "Invalid swipe start")
                val (ex, ey) = parseRelativePoint(end) ?: return fail(message = "Invalid swipe end")
                val exec = withAgentInputFeedback {
                    if (showerCtx.canUseShowerForInput) {
                        val okSwipe = ShowerController.swipe(agentId, sx, sy, ex, ey)
                        if (okSwipe) ok() else fail(message = "Shower SWIPE failed")
                    } else {
                        val params = withDisplayParam(listOf(
                            ToolParameter("start_x", sx.toString()), ToolParameter("start_y", sy.toString()),
                            ToolParameter("end_x", ex.toString()), ToolParameter("end_y", ey.toString())
                        ))
                        val result = toolImplementations.swipe(AITool("swipe", params))
                        if (result.success) ok() else fail(message = result.error ?: "Swipe failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Back" -> {
                val exec = withAgentInputFeedback {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_BACK)
                        if (okKey) ok() else fail(message = "Shower BACK failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_BACK")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok() else fail(message = result.error ?: "Back failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Home" -> {
                val exec = withAgentInputFeedback {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_HOME)
                        if (okKey) ok() else fail(message = "Shower HOME failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_HOME")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok() else fail(message = result.error ?: "Home failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS)
                exec
            }
            "Wait" -> {
                val seconds = fields["duration"]?.replace("seconds", "")?.trim()?.toDoubleOrNull() ?: 1.0
                delay((seconds * 1000).toLong().coerceAtLeast(0L))
                ok()
            }
            "Take_over" -> ok(shouldFinish = true, message = fields["message"] ?: "User takeover required")
            else -> fail(message = "Unknown action: $actionName")
        }
    }

    private suspend fun <T> withAgentUiHiddenForScreenshot(
        showerCtx: ShowerUsageContext,
        block: suspend () -> T
    ): T {
        val shouldHideUiDuringAction = isMainScreenAgent() || !showerCtx.canUseShowerForInput
        if (!shouldHideUiDuringAction || screenshotHiddenDepth > 0) return block()

        val floatingService = FloatingChatService.getInstance()
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)
        screenshotHiddenDepth++
        try {
            if (isMainScreenAgent()) {
                floatingService?.setStatusIndicatorVisible(false)
            }
            progressOverlay.setOverlayVisible(false)
            UIOperationOverlay.getInstance(context).setVisibleForScreenshot(false)
            delay(200)
            return block()
        } finally {
            screenshotHiddenDepth--
            withContext(NonCancellable) {
                if (isMainScreenAgent()) floatingService?.setStatusIndicatorVisible(true)
                progressOverlay.setOverlayVisible(true)
                UIOperationOverlay.getInstance(context).setVisibleForScreenshot(true)
            }
        }
    }

    private suspend fun ensureVirtualDisplayIfAdbOrHigher() {
        if (isMainScreenAgent()) return
        try {
            val permissionState = resolvePrivilegedExecutionState(
                context = context,
                androidPermissionPreferences = androidPermissionPreferences,
            )
            if (!permissionState.isAdbOrHigher) return

            val ok = ShowerServerManager.ensureServerStarted(context)
            if (ok) {
                try {
                    VirtualDisplayOverlay.getInstance(context, agentId).show(0)
                } catch (e: Exception) {
                    AppLogger.e("ActionHandler", "[$agentId] Error showing Shower overlay", e)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ActionHandler", "[$agentId] Error ensuring Shower", e)
        }
    }

    private fun withDisplayParam(params: List<ToolParameter>): List<ToolParameter> {
        if (isMainScreenAgent()) return params
        return try {
            val showerId = ShowerController.getDisplayId(agentId)
            if (showerId != null) {
                params + ToolParameter("display", showerId.toString())
            } else {
                params
            }
        } catch (e: Exception) {
            params
        }
    }

    private fun ok(shouldFinish: Boolean = false, message: String? = null) = ActionExecResult(true, shouldFinish, message)
    private fun fail(shouldFinish: Boolean = false, message: String) = ActionExecResult(false, shouldFinish, message)

    internal fun coordinatePrompt(): String =
        "[COORDINATE_SPACE] Legacy do Tap/Swipe use normalized integers 0..999 on each axis, never image pixels. " +
            "Physical screen=${screenWidth}x${screenHeight}; x_pixel=floor(x*width/1000), y_pixel=floor(y*height/1000). " +
            "Image resizing does not change normalized coordinates. Node Sequence targets use selectors, not coordinates."

    private fun parseRelativePoint(value: String): Pair<Int, Int>? =
        mapPhoneNormalizedPoint(value, screenWidth, screenHeight)

    private suspend fun resolveAppPackageName(app: String): String {
        val trimmed = app.trim()
        val lowered = trimmed.lowercase(Locale.getDefault())
        fun lookup(): String? =
            StandardUITools.APP_PACKAGES[app] ?: StandardUITools.APP_PACKAGES[trimmed] ?: StandardUITools.APP_PACKAGES[lowered]

        lookup()?.let { return it }

        syncAppPackagesFromToolIfNeeded()
        return lookup() ?: trimmed
    }

    private suspend fun syncAppPackagesFromToolIfNeeded() {
        if (appPackagesSyncedFromTool) return
        appPackagesSyncedFromTool = true

        val listResult = aiToolManager.executeTool(AITool("list_installed_apps"))
        if (!listResult.success) {
            AppLogger.w("PhoneAgent", "[$agentId] Failed to sync app packages from tool layer: ${listResult.error}")
            return
        }

        val appListData = listResult.result as? AppListData ?: return
        val discoveredPackages = mutableMapOf<String, String>()

        appListData.packages.forEach { entry ->
            val parsed = parseToolAppEntry(entry) ?: return@forEach
            val (appName, packageName) = parsed
            if (appName.isBlank() || packageName.isBlank()) return@forEach

            discoveredPackages.putIfAbsent(appName, packageName)
            discoveredPackages.putIfAbsent(appName.lowercase(Locale.getDefault()), packageName)
        }

        if (discoveredPackages.isNotEmpty()) {
            StandardUITools.addAppPackages(discoveredPackages)
        }
    }

    private fun parseToolAppEntry(entry: String): Pair<String, String>? {
        val left = entry.lastIndexOf('(')
        val right = entry.lastIndexOf(')')
        if (left < 0 || right <= left) return null

        val appName = entry.substring(0, left).trim()
        val packageName = entry.substring(left + 1, right).trim()
        if (packageName.isBlank()) return null
        return Pair(if (appName.isBlank()) packageName else appName, packageName)
    }
}

/** Interface for providing tool implementations to the ActionHandler. */
interface ToolImplementations {
    suspend fun tap(tool: AITool): ToolResult
    suspend fun longPress(tool: AITool): ToolResult
    suspend fun setInputText(tool: AITool): ToolResult
    suspend fun swipe(tool: AITool): ToolResult
    suspend fun pressKey(tool: AITool): ToolResult
    suspend fun captureScreenshot(tool: AITool): Pair<String?, Pair<Int, Int>?>
    suspend fun captureScreenshotBitmap(tool: AITool): Pair<Bitmap?, Pair<Int, Int>?> {
        val (filePath, dimensions) = captureScreenshot(tool)
        if (filePath == null) {
            return Pair(null, dimensions)
        }

        val bitmap = BitmapFactory.decodeFile(filePath) ?: return Pair(null, dimensions)
        val resolvedDimensions = dimensions ?: Pair(bitmap.width, bitmap.height)
        return Pair(bitmap, resolvedDimensions)
    }
}

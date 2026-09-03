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
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.UIAutomationProgressOverlay
import com.ai.assistance.operit.ui.common.displays.UIOperationOverlay
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImageOutputFormat
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.ImageRegistrationOptions
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Configuration for the PhoneAgent. */
data class AgentConfig(
    val maxSteps: Int = 20,
    /** Hard delay after a successful Launch, waiting for the app window to settle. */
    val postLaunchDelayMs: Long = AgentTimings.DEFAULT_POST_LAUNCH_DELAY_MS,
    /** Hard delay after a successful non-Wait action, waiting for the UI to react. */
    val postActionDelayMs: Long = AgentTimings.DEFAULT_POST_ACTION_DELAY_MS,
    /** Hard delay after hiding the agent overlay, before injecting the event. */
    val uiHideSettleDelayMs: Long = AgentTimings.DEFAULT_UI_HIDE_SETTLE_DELAY_MS
)

/** Default hard-wait values for the agent loop, kept in one place so they stay configurable. */
object AgentTimings {
    const val DEFAULT_POST_LAUNCH_DELAY_MS = 1000L
    const val DEFAULT_POST_ACTION_DELAY_MS = 500L
    const val DEFAULT_UI_HIDE_SETTLE_DELAY_MS = 200L
}

private const val MAX_IDENTICAL_TAP_STREAK = 3
private const val MAX_CONSECUTIVE_TAP_STREAK = 8

/** Result of a single agent step. */
data class StepResult(
    val success: Boolean,
    val finished: Boolean,
    val action: ParsedAgentAction?,
    val thinking: String?,
    val message: String? = null,
    /** True when this step contains a pure Wait action. */
    val isPureWait: Boolean = false
)

/** Parsed action from the model's response. */
data class ParsedAgentAction(
    val metadata: String,
    val actionName: String?,
    val fields: Map<String, String>
)

private data class PrivilegedExecutionState(
    val isAdbOrHigher: Boolean,
    val hasDebuggerShizukuAccess: Boolean
)

private fun resolvePrivilegedExecutionState(
    context: Context,
    androidPermissionPreferences: AndroidPermissionPreferences,
    checkDebuggerShizuku: Boolean = true,
    onExperimentalFlagReadError: ((Exception) -> Unit)? = null
): PrivilegedExecutionState {
    val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
        ?: AndroidPermissionLevel.STANDARD

    var isAdbOrHigher = when (preferredLevel) {
        AndroidPermissionLevel.DEBUGGER,
        AndroidPermissionLevel.ROOT -> true
        // Device-admin and accessibility APIs cannot execute the app_process shell command.
        else -> false
    }

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

    val hasDebuggerShizukuAccess = if (checkDebuggerShizuku &&
        isAdbOrHigher &&
        preferredLevel == AndroidPermissionLevel.DEBUGGER
    ) {
        val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
        val hasShizukuPermission = if (isShizukuRunning) ShizukuAuthorizer.hasShizukuPermission() else false
        isShizukuRunning && hasShizukuPermission
    } else {
        true
    }

    return PrivilegedExecutionState(
        isAdbOrHigher = isAdbOrHigher,
        hasDebuggerShizukuAccess = hasDebuggerShizukuAccess
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
    private var _stepCount = 0
    val stepCount: Int
        get() = _stepCount

    private val _contextHistory = mutableListOf<Pair<String, String>>()
    val contextHistory: List<Pair<String, String>>
        get() = _contextHistory.toList()

    /** Deterministic action results waiting to be handed to the model on the next step. */
    private val _pendingActionFeedback = mutableListOf<String>()
    private var consecutiveTapCount = 0
    private var identicalTapCount = 0
    private var lastTapSignature: String? = null

    private var pauseFlow: StateFlow<Boolean>? = null

    private val requiresVirtualScreen: Boolean = agentId.isNotBlank() && agentId != "default"
    private val isMainScreenAgent: Boolean = agentId.isBlank() || agentId == "default"

    init {
        actionHandler.setAgentId(agentId)
        actionHandler.applyTimings(config)
    }

    private suspend fun awaitIfPaused() {
        val flow = pauseFlow ?: return
        if (!flow.value) {
            return
        }
        AppLogger.d("PhoneAgent", "[$agentId] awaitIfPaused: entering pause loop, delay starting")
        try {
            while (flow.value) {
                delay(200)
            }
        } finally {
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

        if (hasShowerDisplay("Error checking Shower state before ensure")) {
            return null
        }

        val permissionState = resolvePrivilegedExecutionState(
            context = context,
            androidPermissionPreferences = androidPermissionPreferences
        )
        if (!permissionState.isAdbOrHigher) {
            return context.getString(R.string.phone_agent_need_debug_permission)
        }

        if (!permissionState.hasDebuggerShizukuAccess) {
            return context.getString(R.string.phone_agent_shizuku_unavailable)
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
        if (!permissionState.hasDebuggerShizukuAccess) {
            return Pair(false, context.getString(R.string.phone_agent_shizuku_unavailable))
        }

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
        if (!permissionState.hasDebuggerShizukuAccess) return false

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
            AppLogger.d("PhoneAgent", "[$agentId] main-screen Shower prewarm ready (displayId=0)")
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
            } else {
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

            reset()
            _contextHistory.add("system" to systemPrompt)
            pauseFlow = isPausedFlow

            // First step with user prompt
            AppLogger.d("PhoneAgent", "[$agentId] run: starting first step for task='$task', hasShowerDisplayAtStart=$hasShowerDisplayAtStart")
            awaitIfPaused()
            var result = _executeStep(task, isFirst = true)
            val firstAction = result.action
            val firstStatusText = when {
                result.finished -> result.message ?: context.getString(R.string.phone_agent_completed)
                firstAction != null && firstAction.metadata == "do" -> {
                    result.message?.takeIf { it.isNotBlank() } ?: run {
                        val actionName = firstAction.actionName ?: ""
                        if (actionName.isNotEmpty()) context.getString(R.string.phone_agent_executing_action, actionName) else context.getString(R.string.phone_agent_executing)
                    }
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
                        result.message?.takeIf { it.isNotBlank() } ?: run {
                            val actionName = action.actionName ?: ""
                            if (actionName.isNotEmpty()) context.getString(R.string.phone_agent_executing_action, actionName) else context.getString(R.string.phone_agent_executing)
                        }
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
                    return result.message ?: "Task completed"
                }
            }

            return "Max steps reached"
        } finally {
            AppLogger.d("PhoneAgent", "[$agentId] run: finishing, restoring UI")
            UIOperationOverlay.getInstance(context).hideImmediately()
            pauseFlow = null
            floatingService?.setFloatingWindowVisible(true)
            if (isMainScreenAgent) {
                floatingService?.setStatusIndicatorVisible(false)
            } else {
                clearAgentIndicators(context, agentId)
            }
            if (useShowerUi) {
                showerOverlay?.hideAutomationControls()
            } else {
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
        _pendingActionFeedback.clear()
        consecutiveTapCount = 0
        identicalTapCount = 0
        lastTapSignature = null
        _stepCount = 0
    }

    /** Stop blind retry loops before they can send an unbounded series of taps. */
    private fun checkTapStreak(action: ParsedAgentAction): String? {
        if (action.actionName != "Tap") {
            consecutiveTapCount = 0
            identicalTapCount = 0
            lastTapSignature = null
            return null
        }

        val signature = action.fields["element"].orEmpty()
        consecutiveTapCount++
        identicalTapCount = if (signature == lastTapSignature) identicalTapCount + 1 else 1
        lastTapSignature = signature

        return when {
            identicalTapCount > MAX_IDENTICAL_TAP_STREAK ->
                "Stopped after repeating Tap at $signature $identicalTapCount times without a state-changing action."
            consecutiveTapCount > MAX_CONSECUTIVE_TAP_STREAK ->
                "Stopped after $consecutiveTapCount consecutive Tap actions without a state-changing action."
            else -> null
        }
    }

    /** Execute a single step of the agent loop. */
    private suspend fun _executeStep(userPrompt: String?, isFirst: Boolean): StepResult {
        _stepCount++
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: begin, step=$_stepCount")

        val screenshotLink = actionHandler.captureScreenshotForAgent()
        val screenInfo = buildString {
            if (screenshotLink != null) {
                appendLine("[SCREENSHOT] Below is the latest screen image:")
                appendLine(screenshotLink)
            } else {
                appendLine("No screenshot available for this step.")
            }
        }.trim()

        val feedback = _pendingActionFeedback.takeIf { it.isNotEmpty() }?.joinToString("\n")
        _pendingActionFeedback.clear()

        val userMessage = if (isFirst) {
            "$userPrompt\n\n$screenInfo"
        } else {
            buildString {
                if (feedback != null) {
                    appendLine("** Last Action Result **")
                    appendLine(feedback)
                    appendLine()
                }
                append("** Screen Info **\n\n$screenInfo")
            }
        }

        _contextHistory.add("user" to userMessage)

        val responseStream = uiService.sendMessage(
            context = context,
            chatHistory = _contextHistory.toList().toPromptTurns(),
            enableThinking = false,
            stream = true,
            preserveThinkInHistory = true
        )

        val contentBuilder = StringBuilder()
        responseStream.collect { chunk -> contentBuilder.append(chunk) }
        val fullResponse = contentBuilder.toString().trim()
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: AI response collected, length=${fullResponse.length}")

        val (thinking, answer) = parseThinkingAndAction(fullResponse)
        val historyEntry = "<think>$thinking</think><answer>$answer</answer>"
        _contextHistory.add("assistant" to historyEntry)

        val parsedActions = parseAgentActions(answer)
        val parsedAction = parsedActions.lastOrNull()
            ?: ParsedAgentAction(metadata = "unknown", actionName = null, fields = emptyMap())
        actionHandler.removeImagesFromLastUserMessage(_contextHistory)

        if (parsedActions.size > 1) {
            AppLogger.d("PhoneAgent", "[$agentId] _executeStep: model returned ${parsedActions.size} chained actions")
        }

        var lastResult: ActionHandler.ActionExecResult? = null
        var lastExecuted: ParsedAgentAction? = null
        val executedMessages = mutableListOf<String>()
        fun combinedMessage(fallback: String?): String? {
            val messages = executedMessages.toMutableList()
            fallback?.takeIf { it.isNotBlank() }?.let { if (messages.lastOrNull() != it) messages.add(it) }
            return messages.joinToString("\n").takeIf { it.isNotBlank() }
        }

        for (action in parsedActions) {
            if (action.metadata == "finish") {
                val message = action.fields["message"] ?: "Task finished."
                return StepResult(
                    success = true,
                    finished = true,
                    action = action,
                    thinking = thinking,
                    message = combinedMessage(message)
                )
            }

            if (action.metadata != "do") {
                val errorMessage = "Unknown action format: ${action.metadata}"
                return StepResult(success = false, finished = true, action = action, thinking = thinking, message = errorMessage)
            }

            val tapGuardMessage = checkTapStreak(action)
            if (tapGuardMessage != null) {
                _pendingActionFeedback.add("${ActionHandler.ACTION_RESULT_PREFIX} Tap blocked: $tapGuardMessage")
                return StepResult(
                    success = false,
                    finished = true,
                    action = action,
                    thinking = thinking,
                    message = combinedMessage(tapGuardMessage)
                )
            }

            awaitIfPaused()
            val execResult = actionHandler.executeAgentAction(action)
            lastResult = execResult
            lastExecuted = action

            val actionLabel = action.actionName ?: "Unknown"
            val outcome = if (execResult.success) "succeeded" else "failed"
            val detail = execResult.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            execResult.message?.takeIf { it.isNotBlank() }?.let { executedMessages.add(it) }
            _pendingActionFeedback.add("${ActionHandler.ACTION_RESULT_PREFIX} $actionLabel $outcome$detail")

            if (execResult.shouldFinish) {
                return StepResult(
                    success = execResult.success,
                    finished = true,
                    action = action,
                    thinking = thinking,
                    message = combinedMessage(execResult.message)
                )
            }
            // A standalone Wait still consumes this model round; batching it with a following action avoids another inference.
            // Stop the chain on the first failure so the model can re-observe the screen.
            if (!execResult.success) break
        }

        if (lastResult != null && lastExecuted != null) {
            return StepResult(
                success = lastResult.success,
                finished = false,
                action = lastExecuted,
                thinking = thinking,
                message = combinedMessage(lastResult.message),
                isPureWait = lastResult.isPureWait
            )
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

    /**
     * Parse every action in the model answer, in output order.
     *
     * AutoGLM normally emits a single `do(...)`, but it is allowed to chain several of them so a
     * whole sub-sequence can run without paying for one screenshot plus one inference per action.
     * A `finish(...)` always terminates the list.
     */
    internal fun parseAgentActions(raw: String): List<ParsedAgentAction> {
        val original = raw.trim()
        val markers = Regex("""\b(do|finish)\s*\(""").findAll(original).map { it.range.first }.toList()
        if (markers.isEmpty()) {
            return listOf(parseSingleAgentAction(original))
        }

        val actions = mutableListOf<ParsedAgentAction>()
        markers.forEachIndexed { index, start ->
            val end = markers.getOrNull(index + 1) ?: original.length
            val segment = original.substring(start, end).trim()
            val parsed = parseSingleAgentAction(segment)
            actions.add(parsed)
            if (parsed.metadata == "finish") return actions
        }
        return actions
    }

    private fun parseSingleAgentAction(raw: String): ParsedAgentAction {
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
    val floatingService = FloatingChatService.getInstance()
    if (floatingService != null) {
        floatingService.setStatusIndicatorVisible(true)
    } else {
        AppLogger.d("PhoneAgent", "[$agentId] No FloatingChatService instance, using standalone rainbow border overlay")
        UIAutomationProgressOverlay.getInstance(context).setBorderEnabled(true)
    }
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
    private var postLaunchDelayMs: Long = AgentTimings.DEFAULT_POST_LAUNCH_DELAY_MS
    private var postActionDelayMs: Long = AgentTimings.DEFAULT_POST_ACTION_DELAY_MS
    private var uiHideSettleDelayMs: Long = AgentTimings.DEFAULT_UI_HIDE_SETTLE_DELAY_MS
    private val aiToolManager: AIToolHandler by lazy { AIToolHandler.getInstance(context) }

    /** Apply the configurable hard-wait values from [AgentConfig]. */
    fun applyTimings(config: AgentConfig) {
        postLaunchDelayMs = config.postLaunchDelayMs.coerceAtLeast(0L)
        postActionDelayMs = config.postActionDelayMs.coerceAtLeast(0L)
        uiHideSettleDelayMs = config.uiHideSettleDelayMs.coerceAtLeast(0L)
    }

    fun setAgentId(id: String) {
        agentId = id
    }

    fun setMainScreenShowerPrepared(prepared: Boolean) {
        mainScreenShowerPrepared = prepared
    }

    data class ActionExecResult(
        val success: Boolean,
        val shouldFinish: Boolean,
        val message: String?,
        /** True when the action is a pure delay and does not add a post-action delay. */
        val isPureWait: Boolean = false
    )

    companion object {
        /** Marker prefix used to feed deterministic action results back to the model. */
        const val ACTION_RESULT_PREFIX = "[ACTION RESULT]"
    }

    private data class ShowerUsageContext(
        val isAdbOrHigher: Boolean,
        val showerDisplayId: Int?
    ) {
        val hasShowerDisplay: Boolean get() = showerDisplayId != null
        val canUseShowerForInput: Boolean get() = isAdbOrHigher && showerDisplayId != null
    }

    private fun isMainScreenAgent(): Boolean = agentId.isBlank() || agentId == "default"

    private fun showTapFeedback(x: Int, y: Int) {
        try {
            if (isMainScreenAgent()) {
                UIOperationOverlay.getInstance(context).showTap(x, y)
            } else {
                VirtualDisplayOverlay.getInstance(context, agentId).showTapFeedback(x, y)
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "[$agentId] Failed to show tap feedback", e)
        }
    }

    private fun showSwipeFeedback(startX: Int, startY: Int, endX: Int, endY: Int) {
        try {
            if (isMainScreenAgent()) {
                UIOperationOverlay.getInstance(context).showSwipe(startX, startY, endX, endY)
            } else {
                VirtualDisplayOverlay.getInstance(context, agentId)
                    .showSwipeFeedback(startX, startY, endX, endY)
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "[$agentId] Failed to show swipe feedback", e)
        }
    }

    private fun resolveShowerUsageContext(): ShowerUsageContext {
        if (isMainScreenAgent()) {
            return ShowerUsageContext(
                isAdbOrHigher = mainScreenShowerPrepared,
                showerDisplayId = if (mainScreenShowerPrepared) 0 else null
            )
        }
        val permissionState = resolvePrivilegedExecutionState(
            context = context,
            androidPermissionPreferences = androidPermissionPreferences,
            checkDebuggerShizuku = false,
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

    suspend fun captureScreenshotForAgent(): String? {
        val showerCtx = resolveShowerUsageContext()
        val floatingService = FloatingChatService.getInstance()
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)

        var screenshotLink: String? = null
        var dimensions: Pair<Int, Int>? = null

        try {
            // Keep screenshot captures clean: hide overlays first, then restore after capture.
            floatingService?.setStatusIndicatorVisible(false)
            UIOperationOverlay.getInstance(context).hideImmediately()
            progressOverlay.setOverlayVisible(false)
            delay(uiHideSettleDelayMs)

            if (showerCtx.canUseShowerForInput) {
                val (link, dims) = captureScreenshotViaShower()
                screenshotLink = link
                dimensions = dims
            }

            if (screenshotLink == null) {
                val screenshotTool = buildScreenshotTool()
                val (bitmap, fallbackDims) = toolImplementations.captureScreenshotBitmap(screenshotTool)

                if (bitmap != null) {
                    val (compressedLink, rawDims) = saveCompressedScreenshotFromBitmap(bitmap)
                    screenshotLink = compressedLink
                    dimensions = fallbackDims ?: rawDims
                    bitmap.recycle()
                }
            }
        } finally {
            val hasShowerDisplayNow = try {
                ShowerController.getDisplayId(agentId) != null
            } catch (e: Exception) {
                AppLogger.e("ActionHandler", "[$agentId] Error checking Shower display state in finally", e)
                false
            }
            if (isMainScreenAgent() || !hasShowerDisplayNow) {
                floatingService?.setStatusIndicatorVisible(true)
            }
            progressOverlay.setOverlayVisible(true)
        }

        if (dimensions != null) {
            screenWidth = dimensions.first
            screenHeight = dimensions.second
        }
        return screenshotLink
    }

    private fun buildScreenshotTool(): AITool {
        return AITool(
            name = "capture_screenshot",
            parameters = emptyList()
        )
    }

    private suspend fun captureScreenshotViaShower(): Pair<String?, Pair<Int, Int>?> {
        return try {
            val pngBytes = if (isMainScreenAgent()) {
                ShowerController.requestScreenshot(agentId)
            } else {
                VirtualDisplayOverlay.getInstance(context, agentId).captureCurrentFramePng()
            }
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

    suspend fun executeAgentAction(parsed: ParsedAgentAction): ActionExecResult {
        val actionName = parsed.actionName ?: return fail(message = "Missing action name")
        val fields = parsed.fields

        val showerCtx = resolveShowerUsageContext()
        return when (actionName) {
            "Launch" -> {
                val app = fields["app"]?.takeIf { it.isNotBlank() } ?: return fail(message = "No app name specified for Launch")
                val packageName = resolveAppPackageName(app)
                try {
                    val permissionState = resolvePrivilegedExecutionState(
                        context = context,
                        androidPermissionPreferences = androidPermissionPreferences
                    )
                    if (permissionState.isAdbOrHigher && !permissionState.hasDebuggerShizukuAccess) {
                        return fail(shouldFinish = true, message = context.getString(R.string.phone_agent_shizuku_unavailable))
                    }

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
                            delay(postLaunchDelayMs)
                            ok(message = "Launch succeeded: $packageName")
                        } else {
                            val desktopPackage = "com.ai.assistance.operit.desktop"
                            val desktopLaunched = ShowerController.launchApp(agentId, desktopPackage)
                            if (desktopLaunched) {
                                try {
                                    VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(desktopPackage)
                                } catch (_: Exception) {}
                                useShowerIndicatorForAgent(context, agentId)
                                delay(postLaunchDelayMs)
                                ok(message = "Launch fallback succeeded: $desktopPackage")
                            } else {
                                fail(message = "Failed to launch on Shower virtual display")
                            }
                        }
                    } else {
                        val result = aiToolManager.executeTool(
                            AITool("start_app", listOf(ToolParameter("package_name", packageName)))
                        )
                        if (result.success) {
                            delay(postLaunchDelayMs)
                            ok(message = "Launch succeeded: $packageName")
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
                val (x, y) = parseRelativePoint(element) ?: return fail(message = "Invalid coordinates for Tap: $element")
                showTapFeedback(x, y)
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okTap = ShowerController.tap(agentId, x, y)
                        if (okTap) ok(message = "Tap sent at ($x, $y)") else fail(message = "Shower TAP failed at ($x,$y)")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("x", x.toString()), ToolParameter("y", y.toString())))
                        val result = toolImplementations.tap(AITool("tap", params))
                        if (result.success) ok(message = "Tap sent at ($x, $y)") else fail(message = result.error ?: "Tap failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(postActionDelayMs)
                exec
            }
            "Type" -> {
                val text = fields["text"] ?: ""
                val exec = withAgentUiHiddenForAction(showerCtx) {
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
                            if (text.isEmpty()) return@withAgentUiHiddenForAction ok(message = "Type completed (empty text)")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                ?: return@withAgentUiHiddenForAction fail(message = "Clipboard unavailable")
                            clipboard.setPrimaryClip(ClipData.newPlainText("operit_input", text))
                            delay(100)
                            val pasted = ShowerController.key(agentId, KeyEvent.KEYCODE_PASTE)
                            if (pasted) ok(message = "Type sent (${text.length} chars)") else fail(message = "Shower PASTE failed")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            fail(message = "Error typing via Shower: ${e.message}")
                        }
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("text", text)))
                        val result = toolImplementations.setInputText(AITool("set_input_text", params))
                        if (result.success) ok(message = "Type sent (${text.length} chars)") else fail(message = result.error ?: "Type failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(postActionDelayMs)
                exec
            }
            "Swipe" -> {
                val start = fields["start"] ?: return fail(message = "Missing swipe start")
                val end = fields["end"] ?: return fail(message = "Missing swipe end")
                val (sx, sy) = parseRelativePoint(start) ?: return fail(message = "Invalid swipe start")
                val (ex, ey) = parseRelativePoint(end) ?: return fail(message = "Invalid swipe end")
                showSwipeFeedback(sx, sy, ex, ey)
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okSwipe = ShowerController.swipe(agentId, sx, sy, ex, ey)
                        if (okSwipe) ok(message = "Swipe sent from ($sx, $sy) to ($ex, $ey)") else fail(message = "Shower SWIPE failed")
                    } else {
                        val params = withDisplayParam(listOf(
                            ToolParameter("start_x", sx.toString()), ToolParameter("start_y", sy.toString()),
                            ToolParameter("end_x", ex.toString()), ToolParameter("end_y", ey.toString())
                        ))
                        val result = toolImplementations.swipe(AITool("swipe", params))
                        if (result.success) ok(message = "Swipe sent from ($sx, $sy) to ($ex, $ey)") else fail(message = result.error ?: "Swipe failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(postActionDelayMs)
                exec
            }
            "Back" -> {
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_BACK)
                        if (okKey) ok(message = "Back key sent") else fail(message = "Shower BACK failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_BACK")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok(message = "Back key sent") else fail(message = result.error ?: "Back failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(postActionDelayMs)
                exec
            }
            "Home" -> {
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_HOME)
                        if (okKey) ok(message = "Home key sent") else fail(message = "Shower HOME failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_HOME")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok(message = "Home key sent") else fail(message = result.error ?: "Home failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) delay(postActionDelayMs)
                exec
            }
            "Wait" -> {
                val seconds = fields["duration"]?.replace("seconds", "")?.trim()?.toDoubleOrNull() ?: 1.0
                delay((seconds * 1000).toLong().coerceAtLeast(0L))
                ActionExecResult(true, false, "Waited ${seconds}s", isPureWait = true)
            }
            "Take_over" -> ok(shouldFinish = true, message = fields["message"] ?: "User takeover required")
            else -> fail(message = "Unknown action: $actionName")
        }
    }

    private suspend fun withAgentUiHiddenForAction(
        showerCtx: ShowerUsageContext,
        block: suspend () -> ActionExecResult
    ): ActionExecResult {
        val shouldHideUiDuringAction = isMainScreenAgent() || !showerCtx.canUseShowerForInput
        if (!shouldHideUiDuringAction) return block()

        val floatingService = FloatingChatService.getInstance()
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)
        try {
            if (isMainScreenAgent()) {
                floatingService?.setStatusIndicatorVisible(false)
            }
            progressOverlay.setOverlayVisible(false)
            delay(uiHideSettleDelayMs)
            return block()
        } finally {
            if (isMainScreenAgent()) {
                val hasShowerDisplayNow = try {
                    ShowerController.getDisplayId(agentId) != null
                } catch (e: Exception) {
                    AppLogger.e("ActionHandler", "[$agentId] Error checking Shower display state after action", e)
                    false
                }
                if (isMainScreenAgent() || !hasShowerDisplayNow) {
                    floatingService?.setStatusIndicatorVisible(true)
                }
            }
            progressOverlay.setOverlayVisible(true)
        }
    }

    private suspend fun ensureVirtualDisplayIfAdbOrHigher() {
        if (isMainScreenAgent()) return
        try {
            val permissionState = resolvePrivilegedExecutionState(
                context = context,
                androidPermissionPreferences = androidPermissionPreferences,
                checkDebuggerShizuku = false
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

    private fun parseRelativePoint(value: String): Pair<Int, Int>? {
        val parts = value.trim().removeSurrounding("[", "]").split(",").map { it.trim() }
        if (parts.size < 2) return null
        val relX = parts[0].toIntOrNull() ?: return null
        val relY = parts[1].toIntOrNull() ?: return null
        return (relX / 1000.0 * screenWidth).toInt() to (relY / 1000.0 * screenHeight).toInt()
    }

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

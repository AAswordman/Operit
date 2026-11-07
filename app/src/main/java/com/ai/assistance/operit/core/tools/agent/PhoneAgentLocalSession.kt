package com.ai.assistance.operit.core.tools.agent

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** An explicit main-display session; the current provider has no virtual-display interface. */
internal class PhoneAgentLocalSession(
    context: Context,
    inputScope: suspend (suspend () -> Boolean) -> Boolean,
    tapAt: suspend (Int, Int) -> Boolean,
    private val onStep: (Int, Int, PhoneLocalAction) -> Unit = { _, _, _ -> },
    private val task: String = "",
    private val performance: PhoneAgentPerformance? = null,
) {
    private val driver = AccessibilityPhoneUiDriver(context, inputScope, tapAt, performance)
    private val store = PhoneSequenceStore(File(context.filesDir, "phone_sequences")) { packageName ->
        val info = context.packageManager.getPackageInfo(packageName, 0)
        "${PackageInfoCompat.getLongVersionCode(info)}:${info.lastUpdateTime}"
    }
    private var observed: PhoneUiSnapshot? = null
    private var pendingObservation: PhoneUiSnapshot? = null
    private var shown = emptyMap<Int, PhoneUiNode>()
    var lastCandidateCount: Int = 0
        private set
    var lastSentCount: Int = 0
        private set

    fun invalidate() {
        observed = null
        pendingObservation = null
        shown = emptyMap()
        lastNodeCount = null
        lastPackageName = null
        lastCandidateCount = 0
        lastSentCount = 0
    }
    var lastNodeCount: Int? = null
        private set

    var lastPackageName: String? = null
        private set

    suspend fun observe(expectedPackage: String? = null): String {
        observed = null
        lastPackageName = null
        lastNodeCount = null
        shown = emptyMap()
        lastCandidateCount = 0
        lastSentCount = 0
        val pending = pendingObservation
        pendingObservation = null
        val age = pending?.let { System.nanoTime() / 1_000_000 - it.capturedAtMs }
        val reuse = pending != null && age != null && age in 0L..1000L
        val state = if (reuse) requireNotNull(pending).also { performance?.let { it.reusedObservations++ } } else driver.read()
        val summaryStarted = System.nanoTime()
        val summary = try {
            state.summary(task)
        } finally {
            performance?.let { it.summaryNs += System.nanoTime() - summaryStarted }
        }
        if (expectedPackage != null && (state.packageName != expectedPackage || summary.shown.isEmpty())) {
            throw PhoneUiUnavailableException("Launched app window is not ready for node observation: $expectedPackage")
        }
        lastPackageName = state.packageName
        lastNodeCount = state.nodes.size
        lastCandidateCount = summary.candidates.size
        lastSentCount = summary.shown.size
        shown = summary.shown.associateBy { it.ref }
        val reusable = try {
            withContext(Dispatchers.IO) { store.catalog(state.packageName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "[SEQUENCE_STORE_ERROR] ${e.javaClass.simpleName}"
        }
        observed = state
        return "[UI_STATE] ${summary.payload}\n[REUSABLE_SEQUENCES] $reusable"
    }

    data class Result(
        val feedback: String,
        val attempted: Int,
        val waitMs: Long,
        val inspect: Boolean,
        val sequence: Boolean,
        val success: Boolean,
        val verifiedActions: Int = 0,
        val actionName: String? = null,
    )

    suspend fun execute(raw: String, remainingActions: Int, checkpoint: suspend () -> Unit): Result {
        // A coordinate/screenshot request invalidates node references; a new node action must
        // refer to exactly the last summary, not a hidden node in the provider's full XML.
        val command = try {
            PhoneLocalPlanParser.parse(raw)
        } catch (e: PhoneUiException) {
            return Result("Local command rejected before execution: ${e.message}", 0, 0, false, false, false)
        }
        if (command == PhoneLocalCommand.Inspect || command == PhoneLocalCommand.Observe) {
            invalidate()
            val image = command == PhoneLocalCommand.Inspect
            return Result(if (image) "Next observation is image-only; use Observe before selecting node references again."
                else "Next observation contains a fresh compact node summary.", 0, 0, true, false, true, actionName = if (image) "Inspect" else "Observe")
        }
        if (command is PhoneLocalCommand.Node) {
            val state = observed ?: return Result("A latest node observation is required.", 0, 0, false, false, false)
            onStep(1, 1, command.action)
            val guard = checkpointFor(state, checkpoint)
            val result = PhoneNodeActionExecutor(driver).execute(command, state, shown, task, remainingActions, guard)
            pendingObservation = if (observed === state) result.snapshot else null
            observed = null
            shown = emptyMap()
            return resultFeedback(result, command.action.name)
        }
        // Finish template lookup before entering the action execution boundary.
        val prepared = try {
            when (command) {
                PhoneLocalCommand.Inspect, PhoneLocalCommand.Observe, is PhoneLocalCommand.Node -> throw PhoneUiException("Command was already handled")
                is PhoneLocalCommand.Sequence -> Triple(command.screen, command.sequence, command.arguments)
                is PhoneLocalCommand.Replay -> Triple(
                    command.screen,
                    withContext(Dispatchers.IO) { store.load(command.packageName, command.name) },
                    command.arguments,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result("Local command rejected before execution: ${e.message}", 0, 0, false, false, false)
        }
        val (screen, plan, arguments) = prepared
        val state = observed
        if (state == null || screen != state.revision) {
            return Result("Command must reference a valid latest main-screen observation.", 0, 0, false, false, false)
        }
        val guard = checkpointFor(state, checkpoint)
        val result = PhonePlanExecutor(driver, onStep = onStep, checkpoint = guard).execute(plan, arguments, state, remainingActions)
        pendingObservation = if (observed === state) result.snapshot else null
        observed = null
        shown = emptyMap()
        var storageMessage = ""
        if (result.success && plan.saveAs != null) {
            try {
                withContext(Dispatchers.IO) { store.save(plan) }
                storageMessage = " Reusable template saved; the arguments object was not stored."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Actions already happened: a storage error must not invite a second execution.
                storageMessage = " Local actions completed, but saving the template failed: ${e.javaClass.simpleName}."
            }
        }
        return resultFeedback(result.copy(message = result.message + storageMessage), "Sequence", sequence = true)
    }

    private fun checkpointFor(state: PhoneUiSnapshot, checkpoint: suspend () -> Unit): suspend () -> Unit = {
        checkpoint()
        // A user can take over while a Binder read is running. On resume, the old observation
        // cannot authorize dispatch or the remaining steps of a previously checked sequence.
        if (observed !== state) throw PhoneUiException("Observation invalidated during takeover; observe before further input")
    }

    private fun resultFeedback(result: PhonePlanResult, actionName: String, sequence: Boolean = false): Result {
        val feedback = JSONObject().put("success", result.success).put("completed", result.completed)
            .put("attempted", result.attempted).put("verified_actions", result.verifiedActions)
            .put("last_action_accepted", result.lastActionAccepted)
            .put("last_action_may_have_run", result.lastActionMayHaveRun)
            .put("message", result.message).toString()
        return Result(feedback, result.attempted, result.conditionWaitMs, false, sequence, result.success, result.verifiedActions, actionName)
    }
}

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
    private val onStep: (Int, Int, PhoneLocalAction) -> Unit = { _, _, _ -> },
) {
    private val driver = AccessibilityPhoneUiDriver(context, inputScope)
    private val store = PhoneSequenceStore(File(context.filesDir, "phone_sequences")) { packageName ->
        val info = context.packageManager.getPackageInfo(packageName, 0)
        "${PackageInfoCompat.getLongVersionCode(info)}:${info.lastUpdateTime}"
    }
    private var observed: PhoneUiSnapshot? = null
    var lastNodeCount: Int? = null
        private set

    suspend fun observe(): String {
        observed = null
        lastNodeCount = null
        val state = driver.read()
        lastNodeCount = state.nodes.size
        val reusable = try {
            withContext(Dispatchers.IO) { store.catalog(state.packageName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "[SEQUENCE_STORE_ERROR] ${e.javaClass.simpleName}"
        }
        observed = state
        return "[UI_STATE] ${state.toPrompt()}\n[REUSABLE_SEQUENCES] $reusable"
    }

    data class Result(
        val feedback: String,
        val attempted: Int,
        val waitMs: Long,
        val inspect: Boolean,
        val sequence: Boolean,
        val success: Boolean,
        val verifiedActions: Int = 0,
    )

    suspend fun execute(raw: String, remainingActions: Int, checkpoint: suspend () -> Unit): Result {
        // Finish parsing and template lookup before entering the action execution boundary.
        val prepared = try {
            when (val command = PhoneLocalPlanParser.parse(raw)) {
                PhoneLocalCommand.Inspect -> return Result("Next observation will include a fresh screenshot.", 0, 0, true, false, true)
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
        val result = PhonePlanExecutor(driver, onStep = onStep, checkpoint = checkpoint).execute(plan, arguments, state, remainingActions)
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
        val feedback = JSONObject().put("success", result.success).put("completed", result.completed)
            .put("attempted", result.attempted).put("verified_actions", result.verifiedActions)
            .put("last_action_accepted", result.lastActionAccepted)
            .put("last_action_may_have_run", result.lastActionMayHaveRun)
            .put("message", result.message + storageMessage).toString()
        return Result(feedback, result.attempted, result.conditionWaitMs, false, true, result.success, result.verifiedActions)
    }
}

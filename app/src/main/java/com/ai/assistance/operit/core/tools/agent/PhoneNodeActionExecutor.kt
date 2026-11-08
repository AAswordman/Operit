package com.ai.assistance.operit.core.tools.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** A single observed control does not require predicting selectors on the next page. */
internal class PhoneNodeActionExecutor(private val driver: PhoneUiDriver) {
    suspend fun execute(
        command: PhoneLocalCommand.Node,
        observed: PhoneUiSnapshot,
        shown: Map<Int, PhoneUiNode>,
        task: String,
        remainingActions: Int,
        checkpoint: suspend () -> Unit,
    ): PhonePlanResult {
        var attempted = 0
        var accepted = false
        var mayHaveRun = false
        var latest: PhoneUiSnapshot? = null
        var settleMs = 0L
        try {
            if (remainingActions < 1) throw PhoneUiException("No actions remain")
            if (command.screen != observed.revision) throw PhoneUiException("Element reference belongs to another observation")
            val previous = shown[command.ref] ?: throw PhoneUiException("ref was not present in the latest model observation")
            checkpoint()
            val before = driver.read().also { latest = it }
            checkpoint()
            if (before.packageName != observed.packageName) throw PhoneUiException("Active app changed before dispatch")
            val matches = before.summary(task).candidates.filter {
                it.sameTargetAs(previous) && it.modelLabel == previous.modelLabel
            }
            if (matches.size != 1) throw PhoneUiException("Observed control changed or became ambiguous; no action was sent")
            val node = matches.single()
            if (!node.enabled || node.password || node.bounds?.isUsable != true) throw PhoneUiException("Control is not available for this action")
            if (command.action == PhoneLocalAction.Type) {
                if (!node.editable || !node.focused) throw PhoneUiException("Type requires a focused ordinary input; tap it first")
                if (before.nodes.count { it.editable && it.focused && before.isVisible(it) } != 1) {
                    throw PhoneUiException("Input focus is ambiguous")
                }
            }
            attempted = 1
            mayHaveRun = true
            latest = null
            accepted = when (command.action) {
                PhoneLocalAction.Tap -> driver.tap(node)
                PhoneLocalAction.Type -> driver.type(node, requireNotNull(command.text))
                PhoneLocalAction.Wait -> throw PhoneUiException("A numbered node action cannot be Wait")
            }
            if (!accepted) throw PhoneUiException("Action delivery was not confirmed; do not resend without inspection")
            // The installed provider has no event subscription. Keep this explicit settling delay
            // separate from event-driven waiting; it is not a claim that the whole page is ready.
            delay(300)
            settleMs = 300
            val after = driver.read().also { latest = it }
            val verified = when (command.action) {
                PhoneLocalAction.Type -> {
                    val inputs = after.nodes.filter {
                        after.packageName == node.packageName && after.isVisible(it) &&
                            it.packageName == node.packageName && it.editable && it.focused && !it.password &&
                            it.className == node.className &&
                            (if (node.resourceId.isNotEmpty()) it.resourceId == node.resourceId
                             else it.description == node.description && it.hint == node.hint)
                    }
                    if (inputs.size != 1 || inputs.single().text != command.text) {
                        return PhonePlanResult(0, 1, true, true, false,
                            "Text was delivered but exact readback was not confirmed; inspect the new state", 300, snapshot = after)
                    }
                    if (node.text != command.text) 1 else 0
                }
                PhoneLocalAction.Tap -> if (interactionState(before) != interactionState(after)) 1 else 0
                PhoneLocalAction.Wait -> 0
            }
            return PhonePlanResult(1, 1, true, true, true,
                if (command.action == PhoneLocalAction.Type) "Input readback matches; verify the user's task outcome"
                else "Tap dispatched; inspect the returned state to determine whether it reached the intended page", 300, verified, after)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PhonePlanResult(0, attempted, accepted, mayHaveRun, false,
                "Node action stopped: ${e.message}." + if (mayHaveRun) " The action may have taken effect; inspect before repeating." else " No input was dispatched.", settleMs, snapshot = latest)
        }
    }

    private fun interactionState(state: PhoneUiSnapshot): Pair<String, List<String>> = state.packageName to state.nodes
        .filter { it.packageName == state.packageName && state.isVisible(it) && (it.clickable || it.editable || it.scrollable) }
        .map { "${it.resourceId}|${it.className}|${it.editable}|${it.focused}|${it.selected}|${it.checked}|${it.enabled}" }
        .sorted()
}

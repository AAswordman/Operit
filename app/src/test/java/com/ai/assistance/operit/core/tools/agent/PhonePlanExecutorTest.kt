package com.ai.assistance.operit.core.tools.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhonePlanExecutorTest {
    private val pkg = "com.example.app"
    private val button = PhoneUiNode(pkg, resourceId = "search", bounds = PhoneBounds(0, 0, 50, 50), clickable = true)
    private val input = PhoneUiNode(pkg, resourceId = "query", className = "android.widget.EditText", editable = true, focused = true)
    private fun snapshot(vararg nodes: PhoneUiNode) = PhoneUiSnapshot(1, pkg, nodes.toList())
    private fun tap(expect: PhoneTarget = PhoneTarget(id = "query"), timeout: Long = 200L) =
        PhoneLocalStep(PhoneLocalAction.Tap, PhoneTarget(id = "search"), expect, null, timeout)
    private fun type(text: String) = PhoneLocalStep(PhoneLocalAction.Type, PhoneTarget(id = "query"), null, text, 200)
    private fun plan(vararg steps: PhoneLocalStep) = PhoneLocalSequence(pkg, steps.toList())

    private class Driver(var state: PhoneUiSnapshot) : PhoneUiDriver {
        var taps = 0
        var types = 0
        var accept = true
        var onTap: () -> Unit = {}
        var onType: (String) -> Unit = {}
        var onRead: () -> Unit = {}
        override suspend fun read(): PhoneUiSnapshot { onRead(); return state }
        override suspend fun tap(node: PhoneUiNode): Boolean { taps++; onTap(); return accept }
        override suspend fun type(node: PhoneUiNode, text: String): Boolean { types++; onType(text); return accept }
    }

    @Test fun readyStepsExecuteWithoutFixedHalfSecondSleeps() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        driver.onTap = { driver.state = snapshot(input) }
        driver.onType = { text -> driver.state = snapshot(input.copy(text = text)) }
        val result = PhonePlanExecutor(driver, { testScheduler.currentTime }).execute(plan(tap(), type("coffee")), emptyMap(), observed, 2)
        assertTrue(result.success)
        assertEquals(2, result.completed)
        assertEquals(1, driver.taps)
        assertEquals(1, driver.types)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun changedScreenIsRejectedBeforeAnyAction() = runTest {
        val observed = snapshot(button)
        val driver = Driver(snapshot(button.copy(text = "another row")))
        val result = PhonePlanExecutor(driver).execute(plan(tap()), emptyMap(), observed, 1)
        assertFalse(result.success)
        assertEquals(0, driver.taps)
    }

    @Test fun duplicateTargetsAreNeverResolvedByTakingTheFirst() = runTest {
        val observed = snapshot(button, button.copy(bounds = PhoneBounds(60, 0, 110, 50)))
        val driver = Driver(observed)
        val result = PhonePlanExecutor(driver).execute(plan(tap()), emptyMap(), observed, 1)
        assertFalse(result.success)
        assertEquals(0, driver.taps)
    }

    @Test fun existingPostconditionCannotProveTapSuccess() = runTest {
        val observed = snapshot(button, input)
        val driver = Driver(observed)
        val result = PhonePlanExecutor(driver).execute(plan(tap()), emptyMap(), observed, 1)
        assertFalse(result.success)
        assertEquals(0, driver.taps)
    }

    @Test fun missingParameterAnywhereRejectsBeforeTheFirstTap() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        val result = PhonePlanExecutor(driver).execute(plan(tap(), type("{{query}}")), emptyMap(), observed, 2)
        assertFalse(result.success)
        assertEquals(0, driver.taps)
    }

    @Test fun actionBudgetIsCheckedBeforeExecution() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        val result = PhonePlanExecutor(driver).execute(plan(tap(), type("coffee")), emptyMap(), observed, 1)
        assertFalse(result.success)
        assertEquals(0, driver.taps)
    }

    @Test fun timeoutStopsLaterStepsAndPreservesAcceptedActionStatus() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        val result = PhonePlanExecutor(driver, { testScheduler.currentTime }).execute(plan(tap(), type("coffee")), emptyMap(), observed, 2)
        assertFalse(result.success)
        assertTrue(result.lastActionAccepted)
        assertTrue(result.lastActionMayHaveRun)
        assertEquals(1, driver.taps)
        assertEquals(0, driver.types)
        assertEquals(200L, result.conditionWaitMs)
    }

    @Test fun exceptionAfterDispatchIsReportedAsPotentiallyDelivered() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        driver.onTap = { throw IllegalStateException("remote disconnect") }
        val result = PhonePlanExecutor(driver).execute(plan(tap()), emptyMap(), observed, 1)
        assertFalse(result.success)
        assertFalse(result.lastActionAccepted)
        assertTrue(result.lastActionMayHaveRun)
        assertEquals(1, result.attempted)
        assertEquals(1, driver.taps)
    }

    @Test fun appSwitchStopsWithoutTypingIntoAnotherApp() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        driver.onTap = { driver.state = PhoneUiSnapshot(2, "com.other.app", listOf(input.copy(packageName = "com.other.app"))) }
        val result = PhonePlanExecutor(driver).execute(plan(tap(), type("coffee")), emptyMap(), observed, 2)
        assertFalse(result.success)
        assertEquals(0, driver.types)
    }

    @Test fun incorrectInputReadbackStopsTheSequence() = runTest {
        val observed = snapshot(input)
        val driver = Driver(observed)
        driver.onType = { driver.state = snapshot(input.copy(text = "wrong")) }
        val result = PhonePlanExecutor(driver, { testScheduler.currentTime }).execute(plan(type("coffee")), emptyMap(), observed, 1)
        assertFalse(result.success)
        assertEquals(1, driver.types)
        assertEquals(0, result.completed)
    }

    @Test fun secretAndUnfocusedInputsAreNotTyped() = runTest {
        for (node in listOf(input.copy(password = true), input.copy(focused = false))) {
            val observed = snapshot(node)
            val driver = Driver(observed)
            val result = PhonePlanExecutor(driver).execute(plan(type("coffee")), emptyMap(), observed, 1)
            assertFalse(result.success)
            assertEquals(0, driver.types)
        }
    }

    @Test fun pausedTimeDoesNotConsumeConditionBudget() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        var paused = false
        val executor = PhonePlanExecutor(driver, { testScheduler.currentTime }) {
            if (driver.taps == 1 && !paused) {
                paused = true
                delay(10000)
                driver.state = snapshot(input)
            }
        }
        val result = executor.execute(plan(tap()), emptyMap(), observed, 1)
        assertTrue(result.success)
        assertEquals(10000L, testScheduler.currentTime)
        assertEquals(0L, result.conditionWaitMs)
    }

    @Test fun cancellationIsPropagatedAndNeverConvertedToRetry() = runTest {
        val observed = snapshot(button)
        val driver = Driver(observed)
        driver.onTap = { throw CancellationException("user stopped") }
        var cancelled = false
        try { PhonePlanExecutor(driver).execute(plan(tap()), emptyMap(), observed, 1) }
        catch (e: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(1, driver.taps)
    }

    @Test fun toggleMustActuallyReachTheRequestedCheckedState() = runTest {
        val observed = snapshot(button.copy(checked = false))
        val driver = Driver(observed)
        driver.onTap = { driver.state = snapshot(button.copy(checked = true)) }
        val result = PhonePlanExecutor(driver).execute(
            plan(tap(PhoneTarget(id = "search", checked = true))), emptyMap(), observed, 1,
        )
        assertTrue(result.success)
        assertEquals(1, driver.taps)
    }

}

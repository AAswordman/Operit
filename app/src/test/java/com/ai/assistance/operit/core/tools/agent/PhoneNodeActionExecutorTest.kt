package com.ai.assistance.operit.core.tools.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneNodeActionExecutorTest {
    private val pkg = "com.example.app"
    private val button = PhoneUiNode(pkg, resourceId = "search", text = "搜索", clickable = true, bounds = PhoneBounds(0, 0, 100, 100))
    private val input = PhoneUiNode(pkg, resourceId = "query", editable = true, focused = true, bounds = PhoneBounds(0, 100, 500, 200))
    private fun state(vararg nodes: PhoneUiNode) = PhoneUiSnapshot(1, pkg, nodes.toList())

    private class Driver(var state: PhoneUiSnapshot) : PhoneUiDriver {
        var reads = 0
        var taps = 0
        var types = 0
        var lastTapped: PhoneUiNode? = null
        var afterType: (String) -> Unit = {}
        var afterTap: () -> Unit = {}
        override suspend fun read(): PhoneUiSnapshot { reads++; return state }
        override suspend fun tap(node: PhoneUiNode): Boolean { taps++; lastTapped = node; afterTap(); return true }
        override suspend fun type(node: PhoneUiNode, text: String): Boolean { types++; afterType(text); return true }
    }

    @Test fun numberedTapReadsBeforeAndAfterAndReturnsItsResultSnapshot() = runTest {
        val observed = state(button)
        val driver = Driver(observed)
        driver.afterTap = { driver.state = state(input) }
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Tap, null), observed,
            observed.summary().shown.associateBy { it.ref }, "搜索", 1, {},
        )
        assertTrue(result.success)
        assertEquals(1, driver.taps)
        assertEquals(2, driver.reads)
        assertEquals(driver.state, result.snapshot)
        assertEquals(1, result.verifiedActions)
    }

    @Test fun referencesOutsideTheShownSummaryNeverDispatch() = runTest {
        val observed = state(button)
        val driver = Driver(observed)
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 2, PhoneLocalAction.Tap, null), observed,
            observed.summary().shown.associateBy { it.ref }, "搜索", 1, {},
        )
        assertFalse(result.success)
        assertEquals(0, driver.taps)
        assertEquals(0, driver.reads)
    }

    @Test fun changedTargetIsRejectedEvenWhenItsOldReferenceWasValid() = runTest {
        val observed = state(button)
        val driver = Driver(state(button.copy(text = "另一个入口")))
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Tap, null), observed,
            observed.summary().shown.associateBy { it.ref }, "搜索", 1, {},
        )
        assertFalse(result.success)
        assertEquals(0, driver.taps)
    }

    @Test fun deduplicatedNativeMatchesAreStillRejectedAsAmbiguous() = runTest {
        val observed = state(button, button)
        val driver = Driver(observed)
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Tap, null), observed,
            observed.summary().shown.associateBy { it.ref }, "搜索", 1, {},
        )
        assertFalse(result.success)
        assertEquals(0, driver.taps)
    }

    @Test fun referencesResolveTheFreshUniqueControlAfterNodeOrderAndBoundsChange() = runTest {
        val observed = state(button)
        val moved = button.copy(bounds = PhoneBounds(200, 0, 300, 100))
        val driver = Driver(state(PhoneUiNode(pkg, text = "新文字", bounds = PhoneBounds(0, 200, 100, 300)), moved))
        driver.afterTap = { driver.state = state(input) }
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Tap, null), observed,
            observed.summary().shown.associateBy { it.ref }, "搜索", 1, {},
        )
        assertTrue(result.success)
        assertEquals(moved.bounds, driver.lastTapped?.bounds)
    }

    @Test fun typeNeedsExactReadbackAndReturnsTheObservedFailureState() = runTest {
        val observed = state(input)
        val driver = Driver(observed)
        driver.afterType = { driver.state = state(input.copy(text = "wrong")) }
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Type, "coffee"), observed,
            observed.summary().shown.associateBy { it.ref }, "输入", 1, {},
        )
        assertFalse(result.success)
        assertTrue(result.lastActionAccepted)
        assertTrue(result.lastActionMayHaveRun)
        assertEquals(1, driver.types)
        assertEquals(driver.state, result.snapshot)
        assertEquals(2, driver.reads)
    }

    @Test fun ambiguousFocusCannotDispatchTypingEvenWithAUniqueRef() = runTest {
        val observed = state(input, input.copy(resourceId = "another-input"))
        val driver = Driver(observed)
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Type, "coffee"), observed,
            observed.summary().shown.associateBy { it.ref }, "输入", 1, {},
        )
        assertFalse(result.success)
        assertEquals(0, driver.types)
    }

    @Test fun unrelatedFeedTextDoesNotProveTheTapReachedItsDestination() = runTest {
        val caption = PhoneUiNode(pkg, text = "caption one", bounds = PhoneBounds(0, 200, 100, 300))
        val observed = state(button, caption)
        val driver = Driver(observed)
        driver.afterTap = { driver.state = state(button, caption.copy(text = "caption two")) }
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Tap, null), observed,
            observed.summary().shown.associateBy { it.ref }, "搜索", 1, {},
        )
        assertTrue(result.success)
        assertEquals(0, result.verifiedActions)
        assertEquals(1, driver.taps)
    }

    @Test fun anExceptionAfterDispatchNeverInvitesAnAutomaticResend() = runTest {
        val observed = state(button)
        val driver = Driver(observed)
        driver.afterTap = { throw IllegalStateException("remote disconnected") }
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Tap, null), observed,
            observed.summary().shown.associateBy { it.ref }, "搜索", 1, {},
        )
        assertFalse(result.success)
        assertTrue(result.lastActionMayHaveRun)
        assertEquals(1, driver.taps)
        assertEquals(1, driver.reads)
    }

    @Test fun exactTypeReadbackCountsTheMutationAndReturnsTheResultSnapshot() = runTest {
        val observed = state(input)
        val driver = Driver(observed)
        driver.afterType = { driver.state = state(input.copy(text = it)) }
        val result = PhoneNodeActionExecutor(driver).execute(
            PhoneLocalCommand.Node(1, 1, PhoneLocalAction.Type, "coffee"), observed,
            observed.summary().shown.associateBy { it.ref }, "输入", 1, {},
        )
        assertTrue(result.success)
        assertEquals(1, result.verifiedActions)
        assertEquals(1, driver.types)
        assertEquals(2, driver.reads)
        assertEquals(driver.state, result.snapshot)
    }
}

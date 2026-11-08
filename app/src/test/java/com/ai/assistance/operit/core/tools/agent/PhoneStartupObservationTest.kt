package com.ai.assistance.operit.core.tools.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneStartupObservationTest {
    private val pkg = "com.example.app"

    @Test fun failedNodeReadDoesNotRevokeAnAcceptedLaunchOrItsForegroundEvidence() {
        val state = PhoneAppLaunchState { 0L }
        state.accepted(pkg)
        state.foregroundConfirmed()
        state.observationUnavailable()
        assertTrue(state.suppressesLaunch(pkg))
        assertTrue(state.foregroundConfirmed)
        assertEquals(pkg, state.pendingPackage)
        val status = JSONObject(state.prompt().removePrefix("[APP_LAUNCH_STATE] "))
        assertEquals("accepted", status.getString("launch_request"))
        assertEquals("unavailable", status.getString("ui_observation"))
        assertFalse(status.getBoolean("same_app_launch_allowed"))
    }

    @Test fun observationFailureBeforeAnyLaunchDoesNotInventLaunchEvidence() {
        val state = PhoneAppLaunchState()
        state.observationUnavailable()
        assertFalse(state.suppressesLaunch(pkg))
        assertTrue(state.prompt().isEmpty())
    }

    @Test fun targetWindowCompletesStartupWithoutAuthorizingAnotherLaunch() {
        val state = PhoneAppLaunchState()
        state.accepted(pkg)
        state.observationAvailable(pkg)
        assertNull(state.pendingPackage)
        assertTrue(state.suppressesLaunch(pkg))
        state.observationUnavailable()
        assertTrue(state.suppressesLaunch(pkg))
    }

    @Test fun anotherObservedAppOrAnExplicitHomeAllowsReturningToTheApp() {
        val state = PhoneAppLaunchState()
        state.accepted(pkg)
        state.observationAvailable("com.example.other")
        assertFalse(state.suppressesLaunch(pkg))
        state.accepted(pkg)
        state.leftApp()
        assertFalse(state.suppressesLaunch(pkg))
        assertNull(state.pendingPackage)
    }

    @Test fun aNewTaskDoesNotInheritTheLaunchGuard() {
        val state = PhoneAppLaunchState()
        state.accepted(pkg)
        state.reset()
        assertFalse(state.suppressesLaunch(pkg))
        assertNull(state.pendingPackage)
        assertNull(state.deadlineMs)
    }

    @Test fun aColdStartupBeyondThreeSecondsCanBecomeReadableWithoutAnotherLaunch() = runTest {
        val state = PhoneAppLaunchState { testScheduler.currentTime }
        state.accepted(pkg)
        var reads = 0
        val result = PhoneStartupObservation { testScheduler.currentTime }.read(state.deadlineMs, {}) {
            reads++
            if (testScheduler.currentTime < 6000L) {
                state.observationUnavailable()
                throw PhoneUiUnavailableException("Starting")
            }
            state.observationAvailable(pkg)
            "ready"
        }
        assertEquals("ready", result)
        assertEquals(6000L, testScheduler.currentTime)
        assertEquals(4, reads)
        assertTrue(state.suppressesLaunch(pkg))
        assertNull(state.pendingPackage)
    }

    @Test fun startupDeadlineStopsPollingButKeepsTheLaunchAndAllowsLaterObservation() = runTest {
        val state = PhoneAppLaunchState { testScheduler.currentTime }
        state.accepted(pkg)
        val observer = PhoneStartupObservation { testScheduler.currentTime }
        var reads = 0
        try {
            observer.read<Unit>(state.deadlineMs, {}) {
                reads++
                state.observationUnavailable()
                throw PhoneUiUnavailableException("Still starting")
            }
            fail("Expected the startup observation to remain unavailable")
        } catch (_: PhoneUiUnavailableException) {
            assertEquals(15000L, testScheduler.currentTime)
            assertEquals(7, reads)
            assertTrue(state.suppressesLaunch(pkg))
        }
        val ready = observer.read(state.deadlineMs, {}) { "ready now" }
        assertEquals("ready now", ready)
        assertTrue(state.suppressesLaunch(pkg))
    }

    @Test fun ordinaryUnreadableObservationsAreNotPolledAsAnotherStartup() = runTest {
        var reads = 0
        try {
            PhoneStartupObservation { testScheduler.currentTime }.read<Unit>(null, {}) {
                reads++
                throw PhoneUiUnavailableException("Unavailable")
            }
            fail("Expected unavailable observation")
        } catch (_: PhoneUiUnavailableException) {
            assertEquals(1, reads)
            assertEquals(0L, testScheduler.currentTime)
        }
    }

    @Test fun invalidHierarchyErrorsAreNotTreatedAsStartupReadiness() = runTest {
        var reads = 0
        try {
            PhoneStartupObservation { testScheduler.currentTime }.read<Unit>(15000L, {}) {
                reads++
                throw PhoneUiException("Hierarchy exceeds the read limit")
            }
            fail("Expected invalid hierarchy")
        } catch (_: PhoneUiException) {
            assertEquals(1, reads)
            assertEquals(0L, testScheduler.currentTime)
        }
    }

    @Test fun timeSpentReadingCountsAgainstTheSharedStartupBudget() = runTest {
        var reads = 0
        try {
            PhoneStartupObservation { testScheduler.currentTime }.read<Unit>(15000L, {}) {
                reads++
                delay(16000)
                throw PhoneUiUnavailableException("Read completed after the deadline")
            }
            fail("Expected unavailable observation")
        } catch (_: PhoneUiUnavailableException) {
            assertEquals(1, reads)
            assertEquals(16000L, testScheduler.currentTime)
        }
    }

    @Test fun cancellationDoesNotTriggerAnotherObservation() = runTest {
        var reads = 0
        try {
            PhoneStartupObservation { testScheduler.currentTime }.read<Unit>(15000L, {}) {
                reads++
                throw CancellationException("Stopped by user")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, reads)
            assertEquals(0L, testScheduler.currentTime)
        }
    }

    @Test fun newTaskCanReuseForegroundWithoutClaimingAnotherLaunchWasSent() {
        val state = PhoneAppLaunchState { 500L }
        state.accepted(pkg)
        state.reset()
        state.reuseForeground(pkg)
        assertTrue(state.foregroundConfirmed)
        assertTrue(state.suppressesLaunch(pkg))
        assertEquals(pkg, state.pendingPackage)
        assertEquals(15500L, state.deadlineMs)
        val status = JSONObject(state.prompt().removePrefix("[APP_LAUNCH_STATE] "))
        assertEquals("not_dispatched", status.getString("launch_request"))
        assertTrue(status.getBoolean("foreground_reused"))
        assertFalse(status.getBoolean("same_app_launch_allowed"))
    }

    @Test fun failedObservationDoesNotAuthorizeRestartingAReusedForegroundPage() {
        val state = PhoneAppLaunchState()
        state.reuseForeground(pkg)
        state.observationUnavailable()
        assertTrue(state.suppressesLaunch(pkg))
        assertTrue(state.foregroundConfirmed)
        state.observationAvailable(pkg)
        assertNull(state.pendingPackage)
        assertTrue(state.suppressesLaunch(pkg))
    }

    @Test fun returningAfterHomeRecordsANewLaunchInsteadOfOldForegroundReuse() {
        val state = PhoneAppLaunchState()
        state.reuseForeground(pkg)
        state.leftApp()
        assertFalse(state.suppressesLaunch(pkg))
        state.accepted(pkg)
        val status = JSONObject(state.prompt().removePrefix("[APP_LAUNCH_STATE] "))
        assertEquals("accepted", status.getString("launch_request"))
        assertFalse(status.getBoolean("foreground_reused"))
        assertFalse(state.foregroundConfirmed)
    }

}

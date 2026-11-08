package com.ai.assistance.operit.data.updates.patch

import org.junit.Assert.*
import org.junit.Test

class PatchChainResolverTest {
    private data class Step(val from: String, val to: String)
    private fun path(steps: List<Step>, from: String = "6", to: String = "8", limit: Int = 64) =
        resolvePatchPath(steps, from, to, limit, Step::from, Step::to)

    @Test fun skippedVersionUsesBothMatchingPatchSteps() {
        val sixToSeven = Step("6", "7")
        val sevenToEight = Step("7", "8")
        assertEquals(listOf(sixToSeven, sevenToEight), path(listOf(sevenToEight, sixToSeven)))
    }
    @Test fun unmatchedTargetPatchIsNeverAppliedToAnOlderBase() {
        assertNull(path(listOf(Step("7", "8"))))
    }
    @Test fun searchDoesNotChooseADeadEndEvenIfListedFirst() {
        val steps = listOf(Step("6", "9-dead-end"), Step("6", "7"), Step("7", "8"))
        assertEquals(listOf(steps[1], steps[2]), path(steps))
    }
    @Test fun directMatchingPatchIsPreferredOverALongerPath() {
        val direct = Step("6", "8")
        assertEquals(listOf(direct), path(listOf(Step("6", "7"), Step("7", "8"), direct)))
    }
    @Test fun cycleCannotPreventFindingAReachableTarget() {
        assertEquals(2, path(listOf(Step("6", "7"), Step("7", "6"), Step("7", "8")))?.size)
    }
    @Test fun maxStepLimitRejectsIncompletePaths() {
        assertNull(path(listOf(Step("6", "7"), Step("7", "8")), limit = 1))
    }
    @Test fun alreadyInstalledTargetRequiresNoPatch() {
        assertEquals(emptyList<Step>(), path(emptyList(), from = "8"))
    }
}

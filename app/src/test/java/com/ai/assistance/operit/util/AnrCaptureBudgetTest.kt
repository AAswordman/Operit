package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the ANR capture budget that keeps [AnrMonitor] from allocating an
 * unbounded dump while the heap is already close to its limit.
 */
class AnrCaptureBudgetTest {

    private fun deepStack(depth: Int, prefix: String = "com.ai.assistance.operit.Deep"): Array<StackTraceElement> =
        Array(depth) { index -> StackTraceElement(prefix, "run$index", "Deep.kt", index + 1) }

    @Test
    fun formatFrames_capsDepthForVeryDeepStacks() {
        val rendered = AnrCaptureBudget.formatFrames(deepStack(5_000))

        val renderedFrames = rendered.lines().size
        assertEquals(AnrCaptureBudget.MAX_STACK_DEPTH, renderedFrames - 1) // last line is the marker
        assertTrue(rendered.endsWith("... (stack truncated)"))
    }

    @Test
    fun formatFrames_staysWithinCharBudget() {
        val wideStack = Array(10_000) {
            StackTraceElement("com.ai.assistance.operit.${"Wide".repeat(60)}", "run$it", "Wide.kt", it)
        }

        val rendered = AnrCaptureBudget.formatFrames(wideStack)

        assertTrue(
            "rendered stack exceeded the char budget: ${rendered.length}",
            rendered.length <= AnrCaptureBudget.MAX_STACK_CHARS,
        )
    }

    @Test
    fun formatFrames_truncatesSingleOverlongFrame() {
        val singleFrame = arrayOf(
            StackTraceElement("com.ai.assistance.operit.${"Long".repeat(2000)}", "run", "Long.kt", 1),
        )

        val rendered = AnrCaptureBudget.formatFrames(singleFrame)

        assertTrue(rendered.length <= AnrCaptureBudget.MAX_FRAME_CHARS)
        assertTrue(rendered.startsWith("    at com.ai.assistance.operit."))
    }

    @Test
    fun formatFrames_keepsShortStacksIntact() {
        val rendered = AnrCaptureBudget.formatFrames(deepStack(3))

        assertEquals(
            listOf(
                "    at com.ai.assistance.operit.Deep.run0(Deep.kt:1)",
                "    at com.ai.assistance.operit.Deep.run1(Deep.kt:2)",
                "    at com.ai.assistance.operit.Deep.run2(Deep.kt:3)",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun formatFrames_skipsLeadingFramesWhenAsked() {
        val rendered = AnrCaptureBudget.formatFrames(deepStack(5), skipFrames = 3)

        assertEquals(2, rendered.lines().size)
        assertTrue(rendered.startsWith("    at com.ai.assistance.operit.Deep.run3"))
    }

    @Test
    fun formatFrames_doesNotEmitMarkerWhenNothingIsCut() {
        val rendered = AnrCaptureBudget.formatFrames(deepStack(AnrCaptureBudget.MAX_STACK_DEPTH))

        assertFalse(rendered.contains("truncated"))
    }

    @Test
    fun clampDump_boundsOversizedDumpAndMarksTheCut() {
        val oversized = "x".repeat(AnrCaptureBudget.MAX_DUMP_CHARS * 12)

        val clamped = AnrCaptureBudget.clampDump(oversized)

        assertEquals(AnrCaptureBudget.MAX_DUMP_CHARS, clamped.length)
        assertTrue(clamped.endsWith("(dump truncated)"))
    }

    @Test
    fun clampDump_leavesSmallDumpUntouched() {
        val dump = "=== 线程转储 ===\n    at com.ai.assistance.operit.X.y(X.kt:1)"

        assertEquals(dump, AnrCaptureBudget.clampDump(dump))
    }

    @Test
    fun stackSignature_isStableForTheSameFrames() {
        val first = AnrCaptureBudget.stackSignature(deepStack(40))
        val second = AnrCaptureBudget.stackSignature(deepStack(40))

        assertEquals(first, second)
    }

    @Test
    fun stackSignature_ignoresFramesBelowSignatureDepth() {
        val shallow = deepStack(AnrCaptureBudget.SIGNATURE_DEPTH)
        val withTail = deepStack(AnrCaptureBudget.SIGNATURE_DEPTH) +
            Array(200) { StackTraceElement("com.ai.assistance.operit.Tail", "run$it", "Tail.kt", it) }

        assertEquals(
            AnrCaptureBudget.stackSignature(shallow),
            AnrCaptureBudget.stackSignature(withTail),
        )
    }

    @Test
    fun stackSignature_separatesDifferentStalls() {
        val blockedOnDisk = AnrCaptureBudget.stackSignature(deepStack(20, "com.ai.assistance.operit.Io"))
        val blockedOnLock = AnrCaptureBudget.stackSignature(deepStack(20, "com.ai.assistance.operit.Lock"))

        assertNotEquals(blockedOnDisk, blockedOnLock)
    }

    @Test
    fun stackSignature_staysSmallForDeepStacks() {
        val signature = AnrCaptureBudget.stackSignature(deepStack(5_000))

        assertEquals(
            AnrCaptureBudget.SIGNATURE_DEPTH,
            signature.lines().size,
        )
        assertTrue(signature.length < 2_000)
    }
}

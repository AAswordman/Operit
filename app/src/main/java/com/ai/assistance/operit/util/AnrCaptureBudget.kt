package com.ai.assistance.operit.util

/**
 * Memory and frequency budget for the diagnostics collected by [AnrMonitor].
 *
 * The watchdog samples the main thread every 100 ms, so a stalled main thread asks for a
 * thread dump over and over while the heap may already be almost full. Every allocation in
 * that path is bounded here, which keeps the cost of one capture independent of how deep the
 * blocked stack is and of how long a single frame name happens to be.
 */
internal object AnrCaptureBudget {
    /** Frames rendered per capture. Deeper stacks are cut off instead of walked in full. */
    const val MAX_STACK_DEPTH = 60

    /** Characters kept for a single rendered frame line. */
    const val MAX_FRAME_CHARS = 240

    /** Characters kept for one rendered stack section. */
    const val MAX_STACK_CHARS = 24_000

    /** Characters kept for a complete thread dump. */
    const val MAX_DUMP_CHARS = 32_000

    /** Minimum delay between two captures, enforced by [AnrCaptureThrottle]. */
    const val CAPTURE_COOLDOWN_MS = 5_000L

    /** Frames that identify a stall. Frames below this are not compared between captures. */
    const val SIGNATURE_DEPTH = 12

    private const val FRAME_PREFIX = "    at "
    private const val STACK_TRUNCATION_MARKER = "\n    ... (stack truncated)"
    private const val DUMP_TRUNCATION_MARKER = "\n... (dump truncated)"

    /**
     * Renders [frames] as indented stack lines within the given budget.
     *
     * Rendering stops at [maxDepth] frames, cuts each line at [maxFrameChars] characters and
     * never returns more than [maxChars] characters, so the result has a fixed upper bound for
     * any input stack.
     *
     * @param skipFrames leading frames to ignore, for callers that capture their own stack.
     */
    fun formatFrames(
        frames: Array<StackTraceElement>,
        skipFrames: Int = 0,
        maxDepth: Int = MAX_STACK_DEPTH,
        maxFrameChars: Int = MAX_FRAME_CHARS,
        maxChars: Int = MAX_STACK_CHARS,
    ): String {
        val first = skipFrames.coerceIn(0, frames.size)
        val last = if (maxDepth <= 0) first else minOf(frames.size, first + maxDepth)
        val builder = StringBuilder(minOf(maxChars, 512))

        var index = first
        while (index < last) {
            val line = FRAME_PREFIX + frames[index].toString()
            val lineText = if (line.length > maxFrameChars) line.substring(0, maxFrameChars) else line
            val separatorLength = if (builder.length == 0) 0 else 1
            if (builder.length + separatorLength + lineText.length > maxChars) {
                break
            }
            if (separatorLength == 1) {
                builder.append('\n')
            }
            builder.append(lineText)
            index += 1
        }

        val isTruncated = index < frames.size
        if (isTruncated && builder.length + STACK_TRUNCATION_MARKER.length <= maxChars) {
            builder.append(STACK_TRUNCATION_MARKER)
        }

        return builder.toString()
    }

    /**
     * Cuts an assembled thread dump to [maxChars] characters, marking the cut.
     *
     * Returns at most [maxChars] characters for any input, which bounds what the monitor keeps
     * in its stack history and hands to the logger.
     */
    fun clampDump(dump: CharSequence, maxChars: Int = MAX_DUMP_CHARS): String {
        if (dump.length <= maxChars) {
            return dump.toString()
        }
        if (maxChars <= DUMP_TRUNCATION_MARKER.length) {
            return dump.subSequence(0, maxChars.coerceAtLeast(0)).toString()
        }
        return dump.subSequence(0, maxChars - DUMP_TRUNCATION_MARKER.length).toString() +
            DUMP_TRUNCATION_MARKER
    }

    /**
     * Builds the identity of a stalled stack, used to recognise a repeated ANR.
     *
     * Only the leading frames are folded in, so the signature stays small no matter how deep
     * the blocked stack is, and a stall that keeps hitting the same code path yields the same
     * signature as the previous sample.
     */
    fun stackSignature(frames: Array<StackTraceElement>, depth: Int = SIGNATURE_DEPTH): String {
        val builder = StringBuilder()
        val last = if (depth <= 0) 0 else minOf(frames.size, depth)
        for (index in 0 until last) {
            if (index > 0) {
                builder.append('\n')
            }
            builder.append(frames[index].toString())
        }
        return builder.toString()
    }
}

/**
 * Rate limiter for ANR captures.
 *
 * A blocked main thread stays blocked across many 100 ms samples, and every sample would
 * otherwise start another dump. [tryAcquire] lets one capture through per [cooldownMs] window
 * regardless of how long the stall lasts, while unrelated ANRs further apart are unaffected.
 */
internal class AnrCaptureThrottle(private val cooldownMs: Long) {
    private var lastCaptureAtMs: Long? = null

    /** @return true when a capture may start now, false while the cooldown is still running. */
    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        val last = lastCaptureAtMs
        if (last != null && nowMs - last < cooldownMs) {
            return false
        }
        lastCaptureAtMs = nowMs
        return true
    }
}

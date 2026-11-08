package com.ai.assistance.operit.core.diagnostics

import java.util.Collections
import java.util.IdentityHashMap

object ThrowableTextFormatter {

    private const val DEFAULT_MAX_CHARS = 24_000
    private const val MIN_MAX_CHARS = 512
    private const val MAX_FRAMES_PER_THROWABLE = 64
    private const val MAX_CAUSE_DEPTH = 8
    private const val TRUNCATED_MARKER = "\n... [truncated]"
    private const val FORMAT_FAILURE_MARKER = "\n<stack trace omitted>"

    @JvmStatic
    fun format(throwable: Throwable?, maxChars: Int = DEFAULT_MAX_CHARS): String {
        if (throwable == null) {
            return ""
        }
        val limit = maxChars.coerceAtLeast(MIN_MAX_CHARS)
        return try {
            val builder = StringBuilder(minOf(2048, limit))
            val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            appendThrowable(builder, throwable, seen, limit, 0)
            builder.toString()
        } catch (_: OutOfMemoryError) {
            buildMinimalText(throwable, limit)
        } catch (_: Throwable) {
            buildMinimalText(throwable, limit)
        }
    }

    @JvmStatic
    fun truncateText(text: String?, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val limit = maxChars.coerceAtLeast(MIN_MAX_CHARS)
        val source = text.orEmpty()
        if (source.length <= limit) {
            return source
        }
        val visibleChars = (limit - TRUNCATED_MARKER.length).coerceAtLeast(0)
        return if (visibleChars == 0) {
            TRUNCATED_MARKER.take(limit)
        } else {
            source.take(visibleChars) + TRUNCATED_MARKER
        }
    }

    private fun buildMinimalText(throwable: Throwable, maxChars: Int): String {
        val base = buildString {
            append(throwable.javaClass.name)
            val message = throwable.message?.trim().takeUnless { it.isNullOrEmpty() }
            if (message != null) {
                append(": ").append(message)
            }
            append(FORMAT_FAILURE_MARKER)
        }
        return truncateText(base, maxChars)
    }

    private fun appendThrowable(
        builder: StringBuilder,
        throwable: Throwable,
        seen: MutableSet<Throwable>,
        maxChars: Int,
        depth: Int
    ): Boolean {
        if (depth >= MAX_CAUSE_DEPTH) {
            return appendBounded(builder, "Caused by: <cause chain truncated>\n", maxChars)
        }
        if (!seen.add(throwable)) {
            return appendBounded(builder, "Caused by: <circular cause omitted>\n", maxChars)
        }
        if (depth > 0 && !appendBounded(builder, "Caused by: ", maxChars)) {
            return false
        }
        if (!appendBounded(builder, throwable.javaClass.name, maxChars)) {
            return false
        }
        val message = throwable.message?.trim().takeUnless { it.isNullOrEmpty() }
        if (message != null && !appendBounded(builder, ": $message", maxChars)) {
            return false
        }
        if (!appendBounded(builder, "\n", maxChars)) {
            return false
        }

        val frames = throwable.stackTrace
        val frameCount = minOf(frames.size, MAX_FRAMES_PER_THROWABLE)
        for (index in 0 until frameCount) {
            if (!appendBounded(builder, "    at ${frames[index]}\n", maxChars)) {
                return false
            }
        }
        if (frames.size > frameCount && !appendBounded(builder, "    ... ${frames.size - frameCount} more frames\n", maxChars)) {
            return false
        }

        val cause = throwable.cause
        if (cause != null && cause !== throwable) {
            return appendThrowable(builder, cause, seen, maxChars, depth + 1)
        }
        return true
    }

    private fun appendBounded(builder: StringBuilder, text: String, maxChars: Int): Boolean {
        if (text.isEmpty()) {
            return true
        }
        val remaining = maxChars - builder.length
        if (remaining <= 0) {
            return false
        }
        if (text.length <= remaining) {
            builder.append(text)
            return true
        }
        if (remaining <= TRUNCATED_MARKER.length) {
            builder.append(TRUNCATED_MARKER, 0, remaining)
            return false
        }
        val visibleChars = remaining - TRUNCATED_MARKER.length
        builder.append(text, 0, visibleChars)
        builder.append(TRUNCATED_MARKER)
        return false
    }
}

package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal fun assertPropagatedException(expected: Throwable, actual: Throwable) {
    assertEquals(expected.javaClass, actual.javaClass)
    assertEquals(expected.message, actual.message)
    // Coroutine stack-trace recovery may copy an exception at a suspension boundary. Its type,
    // message and original cause must survive; reference equality of the recovered copy need not.
    assertTrue(
        "The propagated exception must retain the original failure in its cause chain",
        generateSequence(actual) { it.cause }.any { it === expected },
    )
}

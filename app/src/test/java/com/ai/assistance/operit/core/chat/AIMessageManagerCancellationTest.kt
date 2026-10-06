package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for #1225: cancellation reasons are threaded truthfully down to
 * running ToolPkg executions. User-initiated cancellation keeps the "User cancelled"
 * reason; system-driven cancellations must carry their own real reason.
 */
class AIMessageManagerCancellationTest {

    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun disableAndroidLoggingForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLoggingAndSeam() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
        AIMessageManager.toolPkgCancelOverride = null
    }

    @Test
    fun `user initiated cancellation keeps the User cancelled reason`() {
        var captured: Pair<String, String>? = null
        AIMessageManager.toolPkgCancelOverride = { chatKey, reason ->
            captured = chatKey to reason
        }

        AIMessageManager.cancelOperation("chat-1")

        assertEquals("chat-1" to "User cancelled", captured)
    }

    @Test
    fun `system cancellation reason is forwarded verbatim instead of User cancelled`() {
        var captured: Pair<String, String>? = null
        AIMessageManager.toolPkgCancelOverride = { chatKey, reason ->
            captured = chatKey to reason
        }
        val systemReason = "Execution cancelled: response timeout (180s)"

        AIMessageManager.cancelOperation("chat-1", systemReason)

        assertEquals("chat-1" to systemReason, captured)
    }

    @Test
    fun `blank chat id never touches ToolPkg executions`() {
        var captured: Pair<String, String>? = null
        AIMessageManager.toolPkgCancelOverride = { chatKey, reason ->
            captured = chatKey to reason
        }

        AIMessageManager.cancelOperation("", "whatever")

        assertNull(captured)
    }
}

package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HotStreamTest {

    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    @Test
    fun shareForwardsUpstreamFailureAfterReplayingPartialContent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val expected = IOException("network interrupted")
        val shared =
            stream<String> {
                emit("partial")
                throw expected
            }.share(
                scope = scope,
                replay = Int.MAX_VALUE,
            )
        val received = StringBuilder()

        try {
            shared.collect { chunk -> received.append(chunk) }
            fail("shared stream must propagate the upstream failure")
        } catch (actual: IOException) {
            assertSame(expected, actual)
        } finally {
            scope.cancel()
        }

        assertEquals("partial", received.toString())
    }
}

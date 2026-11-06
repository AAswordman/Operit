package com.ai.assistance.operit.terminal

import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PtyInputDeadlineTest {
    @Test
    fun fullInputQueueTimesOutAndStillAcceptsLaterInputAfterDrain() {
        // A full OS pipe exercises the same nonblocking descriptor writes without starting a
        // shell or depending on the device's line discipline or installed terminal environment.
        val descriptors = Os.pipe()
        val process = object : Process() {
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
            override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
            override fun waitFor() = 0
            override fun exitValue() = 0
            override fun destroy() {}
        }
        val pty = Pty(process, descriptors[1], -1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val flags = Os.fcntlInt(descriptors[0], OsConstants.F_GETFL, 0)
            Os.fcntlInt(descriptors[0], OsConstants.F_SETFL, flags or OsConstants.O_NONBLOCK)
            val result = executor.submit<InterruptedIOException> {
                try {
                    pty.stdin.write(ByteArray(8 * 1024 * 1024) { 65 })
                    throw AssertionError("A full input queue must time out")
                } catch (expected: InterruptedIOException) {
                    expected
                }
            }.get(6, TimeUnit.SECONDS)
            assertTrue(result.bytesTransferred > 0)
            var remaining = result.bytesTransferred
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                remaining -= Os.read(descriptors[0], buffer, 0, minOf(buffer.size, remaining))
            }
            pty.sendInterrupt()
            assertEquals(1, Os.read(descriptors[0], buffer, 0, 1))
            assertEquals(3, buffer[0].toInt())
        } finally {
            executor.shutdownNow()
            pty.destroy()
            Os.close(descriptors[0])
        }
    }
}

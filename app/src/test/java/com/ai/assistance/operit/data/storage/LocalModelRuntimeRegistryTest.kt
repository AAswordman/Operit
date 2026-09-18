package com.ai.assistance.operit.data.storage

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelRuntimeRegistryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() {
        LocalModelRuntimeRegistry.resetForTests()
    }

    @Test
    fun deleteIfUnusedRefusesPathHeldByAcquire() {
        val modelDir = temporaryFolder.newFolder("in-use-model")
        File(modelDir, "weights.bin").writeBytes(ByteArray(8))
        val handle = LocalModelRuntimeRegistry.acquire(modelDir)
        try {
            assertEquals(
                LocalModelDeleteOutcome.IN_USE,
                LocalModelRuntimeRegistry.deleteIfUnused(modelDir),
            )
            assertTrue(File(modelDir, "weights.bin").isFile)
        } finally {
            handle.close()
        }
    }

    @Test
    fun acquireFailsWhileDeleteHoldsThePath() {
        val modelDir = temporaryFolder.newFolder("deleting-model")
        File(modelDir, "weights.bin").writeBytes(ByteArray(32))
        val started = CountDownLatch(1)
        val releaseDelete = CountDownLatch(1)
        val acquiredDuringDelete = AtomicBoolean(false)
        val deleteThread = Thread {
            LocalModelRuntimeRegistry.deleteIfUnused(modelDir) { _, _ ->
                started.countDown()
                releaseDelete.await(2, TimeUnit.SECONDS)
            }
        }
        deleteThread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        val handle = LocalModelRuntimeRegistry.acquire(modelDir)
        if (handle != null) {
            acquiredDuringDelete.set(true)
            handle.close()
        }
        releaseDelete.countDown()
        deleteThread.join(2_000)
        assertFalse(acquiredDuringDelete.get())
        assertFalse(File(modelDir, "weights.bin").exists())
    }
}
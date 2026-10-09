package com.ai.assistance.operit.services.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTokenStatisticsLoaderTest {
    @Test
    fun emptyMemory_restoresLargePersistedCountsDirectly() = runBlocking {
        val memory = mutableMapOf<String, Triple<Long, Long, Long>>()
        val persisted = Triple(2_600_000_000L, 120_000L, 362_370L)
        val loader = ChatTokenStatisticsLoader(
            isLoaded = { it in memory },
            readCounts = { persisted },
            applyCounts = { id, input, output, window -> memory[id] = Triple(input, output, window) },
        )

        assertTrue(loader.ensureLoaded("chat"))
        assertEquals(persisted, memory["chat"])
    }

    @Test
    fun persistedZero_isInitializedAndDoesNotNeedAnotherRead() = runBlocking {
        val memory = mutableMapOf<String, Triple<Long, Long, Long>>()
        var reads = 0
        val loader = ChatTokenStatisticsLoader(
            isLoaded = { it in memory },
            readCounts = { reads++; Triple(0L, 0L, 0L) },
            applyCounts = { id, input, output, window -> memory[id] = Triple(input, output, window) },
        )

        assertTrue(loader.ensureLoaded("chat"))
        assertTrue(loader.ensureLoaded("chat"))
        assertEquals(1, reads)
        assertEquals(Triple(0L, 0L, 0L), memory["chat"])
    }

    @Test
    fun concurrentInitializations_readAndApplyOnce() = runBlocking {
        val memory = mutableMapOf<String, Triple<Long, Long, Long>>()
        val reading = CompletableDeferred<Unit>()
        val resumeRead = CompletableDeferred<Unit>()
        var reads = 0
        var applications = 0
        val loader = ChatTokenStatisticsLoader(
            isLoaded = { it in memory },
            readCounts = {
                reads++
                reading.complete(Unit)
                resumeRead.await()
                Triple(2_600_000_000L, 120_000L, 362_370L)
            },
            applyCounts = { id, input, output, window ->
                applications++
                memory[id] = Triple(input, output, window)
            },
        )

        val first = async { loader.ensureLoaded("chat") }
        reading.await()
        val second = async { loader.ensureLoaded("chat") }
        resumeRead.complete(Unit)

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, reads)
        assertEquals(1, applications)
    }

    @Test
    fun forceReload_refreshesThePersistedSnapshot() = runBlocking {
        val memory = mutableMapOf("chat" to Triple(10L, 2L, 3L))
        var reads = 0
        val loader = ChatTokenStatisticsLoader(
            isLoaded = { it in memory },
            readCounts = { reads++; Triple(100L, 20L, 30L) },
            applyCounts = { id, input, output, window -> memory[id] = Triple(input, output, window) },
        )

        assertTrue(loader.ensureLoaded("chat"))
        assertEquals(0, reads)
        assertTrue(loader.ensureLoaded("chat", forceReload = true))
        assertEquals(1, reads)
        assertEquals(Triple(100L, 20L, 30L), memory["chat"])
    }

    @Test
    fun missingConversation_isNotMarkedInitialized() = runBlocking {
        var applications = 0
        val loader = ChatTokenStatisticsLoader(
            isLoaded = { false },
            readCounts = { null },
            applyCounts = { _, _, _, _ -> applications++ },
        )

        assertFalse(loader.ensureLoaded("missing"))
        assertEquals(0, applications)
    }

    @Test
    fun readFailure_doesNotMarkInitializedOrHoldTheLock() = runBlocking {
        val memory = mutableMapOf<String, Triple<Long, Long, Long>>()
        var failRead = true
        val loader = ChatTokenStatisticsLoader(
            isLoaded = { it in memory },
            readCounts = {
                if (failRead) throw IllegalStateException("读取失败")
                Triple(100L, 20L, 30L)
            },
            applyCounts = { id, input, output, window -> memory[id] = Triple(input, output, window) },
        )

        val failure = runCatching { loader.ensureLoaded("chat") }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertTrue(memory.isEmpty())
        failRead = false
        assertTrue(loader.ensureLoaded("chat"))
        assertEquals(Triple(100L, 20L, 30L), memory["chat"])
    }

    @Test
    fun differentConversations_restoreTheirOwnCounts() = runBlocking {
        val memory = mutableMapOf<String, Triple<Long, Long, Long>>()
        val persisted = mapOf("first" to Triple(100L, 20L, 30L), "second" to Triple(200L, 40L, 60L))
        val loader = ChatTokenStatisticsLoader(
            isLoaded = { it in memory },
            readCounts = { persisted[it] },
            applyCounts = { id, input, output, window -> memory[id] = Triple(input, output, window) },
        )

        assertTrue(loader.ensureLoaded("first"))
        assertTrue(loader.ensureLoaded("second"))
        assertEquals(persisted, memory)
    }
}

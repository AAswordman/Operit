package com.ai.assistance.operit.data.preferences

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeTargetOperationCoordinatorTest {
    @Test
    fun transitionsAreSerialized() = runTest {
        val coordinator = ThemeTargetOperationCoordinator()
        val events = mutableListOf<String>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            coordinator.runTransition {
                events += "first-start"
                firstStarted.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        runCurrent()
        firstStarted.await()

        val second = async {
            coordinator.runTransition {
                events += "second"
            }
        }
        runCurrent()
        assertEquals(listOf("first-start"), events)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        first.await()
        second.await()
        assertEquals(listOf("first-start", "first-end", "second"), events)
    }
}

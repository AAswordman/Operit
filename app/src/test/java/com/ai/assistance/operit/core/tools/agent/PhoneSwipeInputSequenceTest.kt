package com.ai.assistance.operit.core.tools.agent

import com.ai.assistance.shower.input.SwipeInputSequence
import com.ai.assistance.shower.input.SwipeInputSequence.Action
import org.junit.Assert.*
import org.junit.Test

class PhoneSwipeInputSequenceTest {
    private class Clock(var now: Long = 100L, val earlyWake: Boolean = false) : SwipeInputSequence.Clock {
        val sleeps = mutableListOf<Long>()
        override fun uptimeMillis(): Long = now
        override fun sleep(millis: Long) {
            require(millis > 0)
            sleeps += millis
            now += if (earlyWake) maxOf(1L, millis / 2) else millis
        }
    }
    private data class Event(val action: Action, val down: Long, val time: Long, val fraction: Float)

    @Test fun swipeTakesRealTimeAndAllEventsUseTheCurrentClock() {
        val clock = Clock()
        val events = mutableListOf<Event>()
        SwipeInputSequence.run(300L, 10, clock) { action, down, time, fraction ->
            assertEquals(clock.now, time)
            events += Event(action, down, time, fraction)
        }
        assertEquals(400L, clock.now)
        assertEquals(300L, clock.sleeps.sum())
        assertEquals(12, events.size)
        assertEquals(Action.DOWN, events.first().action)
        assertEquals(Action.UP, events.last().action)
        assertEquals(10, events.count { it.action == Action.MOVE })
        assertTrue(events.all { it.down == 100L })
        assertEquals(1f, events.last().fraction, 0f)
        assertTrue(events.zipWithNext().all { (a, b) -> a.time <= b.time })
    }

    @Test fun earlyWakeDoesNotSendFutureDatedEventsOrFinishTheSwipeEarly() {
        val clock = Clock(earlyWake = true)
        SwipeInputSequence.run(300L, 10, clock) { action, down, time, fraction ->
            assertEquals(clock.now, time)
            if (action == Action.MOVE) assertTrue(time >= down + (300L * fraction).toLong())
        }
        assertEquals(400L, clock.now)
        assertTrue(clock.sleeps.size > 10)
    }

    @Test fun slowDispatchUsesActualTimeInsteadOfReplayingPastTimestamps() {
        val clock = Clock()
        val times = mutableListOf<Long>()
        SwipeInputSequence.run(300L, 10, clock) { _, _, time, _ ->
            assertEquals(clock.now, time)
            times += time
            clock.now += 75L
        }
        assertTrue(times.zipWithNext().all { (a, b) -> b >= a + 75L })
        assertTrue(times.last() - times.first() >= 300L)
    }

    @Test fun rejectedMoveCancelsTheOpenStreamAndDoesNotSendUpOrRetry() {
        val clock = Clock()
        val actions = mutableListOf<Action>()
        val rejection = IllegalStateException("InputManager rejected move")
        val error = assertThrows(IllegalStateException::class.java) {
            SwipeInputSequence.run(300L, 10, clock) { action, _, _, fraction ->
                actions += action
                if (action == Action.MOVE && fraction >= 0.5f) throw rejection
            }
        }
        assertSame(rejection, error)
        assertEquals(Action.CANCEL, actions.last())
        assertEquals(1, actions.count { it == Action.DOWN })
        assertEquals(1, actions.count { it == Action.CANCEL })
        assertFalse(Action.UP in actions)
        assertEquals(250L, clock.now)
    }

    @Test fun aRejectedDownStopsBeforeMovesWithoutInventingAnAcceptedStream() {
        val clock = Clock()
        val actions = mutableListOf<Action>()
        assertThrows(IllegalStateException::class.java) {
            SwipeInputSequence.run(300L, 10, clock) { action, _, _, _ ->
                actions += action
                throw IllegalStateException("InputManager rejected down")
            }
        }
        assertEquals(listOf(Action.DOWN), actions)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test fun failedCancellationRetainsTheOriginalDeliveryError() {
        val clock = Clock()
        val rejection = IllegalStateException("move rejected")
        val cleanup = IllegalStateException("cancel rejected")
        val error = assertThrows(IllegalStateException::class.java) {
            SwipeInputSequence.run(300L, 10, clock) { action, _, _, _ ->
                if (action == Action.MOVE) throw rejection
                if (action == Action.CANCEL) throw cleanup
            }
        }
        assertSame(rejection, error)
        assertArrayEquals(arrayOf<Throwable>(cleanup), error.suppressed)
    }

    @Test fun invalidTimingDoesNotDispatchAnyInput() {
        for ((duration, steps) in listOf(0L to 10, -1L to 10, 300L to 0)) {
            var dispatched = false
            assertThrows(IllegalArgumentException::class.java) {
                SwipeInputSequence.run(duration, steps, Clock()) { _, _, _, _ -> dispatched = true }
            }
            assertFalse(dispatched)
        }
    }
}

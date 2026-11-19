package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ChatFollowScrollConnectionTest {
    private var offset = 1_000
    private val changes = mutableListOf<Boolean>()
    private var userScrolls = 0
    private val pagingDirections = mutableListOf<Float>()
    private val connection = ChatFollowScrollConnection(
        position = { offset },
        isAtLatestBottom = { offset == 1_000 },
        onUserScroll = { userScrolls++ },
        onFollowingChange = { changes += it },
        onScrollDirection = { pagingDirections += it },
    )

    @Test
    fun shortFlickReleasesFollowingBeforeAnyScrollAndKeepsItReleasedAfterLift() = runBlocking {
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 12f), NestedScrollSource.UserInput))
        assertEquals(1_000, offset)
        assertFalse(connection.followingAllowed)
        assertEquals(listOf(false), changes)
        assertTrue(pagingDirections.isEmpty())
        offset = 988
        assertEquals(
            Offset.Zero,
            connection.onPostScroll(Offset(0f, 12f), Offset.Zero, NestedScrollSource.UserInput),
        )
        assertEquals(Velocity.Zero, connection.onPostFling(Velocity.Zero, Velocity.Zero))
        assertFalse(connection.userScrollInProgress)
        assertFalse(connection.followingAllowed)
        assertEquals(listOf(false), changes)
    }

    @Test
    fun nestedPanelScrollAndFlingDoNotResumeTranscriptFollowing() = runBlocking {
        connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        // Only the inner thinking panel consumes this gesture; outer offset stays at bottom.
        connection.onPostScroll(Offset(0f, 50f), Offset.Zero, NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, -50f), Offset.Zero, NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.SideEffect)
        connection.onPostScroll(Offset(0f, -20f), Offset.Zero, NestedScrollSource.SideEffect)
        connection.onPostFling(Velocity(0f, -100f), Velocity.Zero)
        assertFalse(connection.followingAllowed)
        assertEquals(listOf(false), changes)
    }

    @Test
    fun outerTranscriptFlingReachingBottomResumesFollowing() = runBlocking {
        offset = 400
        connection.onPreScroll(Offset(0f, 10f), NestedScrollSource.UserInput)
        connection.onPostFling(Velocity.Zero, Velocity.Zero)
        connection.onPreScroll(Offset(0f, -300f), NestedScrollSource.UserInput)
        offset = 700
        connection.onPostScroll(Offset(0f, -300f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(connection.followingAllowed)
        connection.onPreScroll(Offset(0f, -300f), NestedScrollSource.SideEffect)
        offset = 1_000
        connection.onPostScroll(Offset(0f, -300f), Offset.Zero, NestedScrollSource.SideEffect)
        connection.onPostFling(Velocity(0f, -300f), Velocity.Zero)
        assertTrue(connection.followingAllowed)
        assertEquals(listOf(false, true), changes)
    }

    @Test
    fun programmaticScrollDoesNotChangeUserPreference() {
        offset = 900
        connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.SideEffect)
        offset = 1_000
        connection.onPostScroll(Offset(0f, -100f), Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(0, userScrolls)
        assertTrue(changes.isEmpty())
        assertTrue(pagingDirections.isEmpty())
    }

    @Test
    fun pagingOnlyRespondsToOuterMovementOrUnconsumedEdgeGesture() {
        connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, 50f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(pagingDirections.isEmpty())
        connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        offset -= 50
        connection.onPostScroll(Offset(0f, 50f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(listOf(50f), pagingDirections)
        connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset(0f, 20f), NestedScrollSource.UserInput)
        assertEquals(listOf(50f, 20f), pagingDirections)
    }

    @Test
    fun flingEndWakesFollowingEvenWithoutAnotherContentHeightChange() = runBlocking {
        val states = Channel<Boolean>(Channel.UNLIMITED)
        val observing = launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { connection.userScrollInProgress }.collect { states.send(it) }
        }
        try {
            withTimeout(2_000) {
                assertFalse(states.receive())
                connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
                Snapshot.sendApplyNotifications()
                assertTrue(states.receive())
                connection.onPostFling(Velocity.Zero, Velocity.Zero)
                Snapshot.sendApplyNotifications()
                assertFalse(states.receive())
                assertTrue(connection.followingAllowed)
            }
        } finally {
            observing.cancelAndJoin()
            states.close()
        }
    }
}

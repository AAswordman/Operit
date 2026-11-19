package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** Relinquishes following before scrolling consumes a gesture, including a short flick. */
internal class ChatFollowScrollConnection(
    private val position: () -> Int,
    private val isAtLatestBottom: () -> Boolean,
    private val onUserScroll: () -> Unit,
    private val onFollowingChange: (Boolean) -> Unit,
    private val onScrollDirection: (Float) -> Unit = {},
) : NestedScrollConnection {
    var followingAllowed = true
    var userScrollInProgress by mutableStateOf(false)
        private set
    private var positionBeforeScroll = 0

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
            userScrollInProgress = true
            onUserScroll()
            if (available.y > 0f) {
                followingAllowed = false
                onFollowingChange(false)
            }
        }
        positionBeforeScroll = position()
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput) {
            val movement = positionBeforeScroll - position()
            if (movement != 0 || available.y != 0f) {
                onScrollDirection(if (movement != 0) movement.toFloat() else available.y)
            }
        }
        // Scrolling inside a thinking/tool panel must not re-enable following merely
        // because the surrounding transcript happens to be at its bottom.
        if (userScrollInProgress && position() > positionBeforeScroll && isAtLatestBottom()) {
            followingAllowed = true
            onFollowingChange(true)
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        userScrollInProgress = false
        return Velocity.Zero
    }
}

/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy

import androidx.compose.foundation.gestures.Orientation
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutBeyondBoundsInfo.Interval
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.BeyondBoundsLayout
import androidx.compose.ui.layout.BeyondBoundsLayout.BeyondBoundsScope
import androidx.compose.ui.layout.BeyondBoundsLayout.LayoutDirection.Companion.Above
import androidx.compose.ui.layout.BeyondBoundsLayout.LayoutDirection.Companion.After
import androidx.compose.ui.layout.BeyondBoundsLayout.LayoutDirection.Companion.Before
import androidx.compose.ui.layout.BeyondBoundsLayout.LayoutDirection.Companion.Below
import androidx.compose.ui.layout.BeyondBoundsLayout.LayoutDirection.Companion.Left
import androidx.compose.ui.layout.BeyondBoundsLayout.LayoutDirection.Companion.Right
import androidx.compose.ui.layout.BeyondBoundsLayoutProviderModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.remeasureSync
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.LayoutDirection.Rtl

/**
 * This modifier is used to measure and place additional items when the lazy layout receives a
 * request to layout items beyond the visible bounds.
 *
 * We will layout a limited number of items (currently the number of items in the viewport times a
 * constant) so the focus search may give up if focusable items are too far apart.
 */
internal fun Modifier.lazyLayoutBeyondBoundsModifier(
    state: LazyLayoutBeyondBoundsState,
    beyondBoundsInfo: LazyLayoutBeyondBoundsInfo,
    reverseLayout: Boolean,
    orientation: Orientation,
): Modifier =
    this then
        LazyLayoutBeyondBoundsModifierElement(state, beyondBoundsInfo, reverseLayout, orientation)

private class LazyLayoutBeyondBoundsModifierElement(
    val state: LazyLayoutBeyondBoundsState,
    val beyondBoundsInfo: LazyLayoutBeyondBoundsInfo,
    val reverseLayout: Boolean,
    val orientation: Orientation,
) : ModifierNodeElement<LazyLayoutBeyondBoundsProviderModifierNode>() {
    override fun create(): LazyLayoutBeyondBoundsProviderModifierNode {
        return LazyLayoutBeyondBoundsProviderModifierNode(
            state,
            beyondBoundsInfo,
            reverseLayout,
            orientation,
        )
    }

    override fun update(node: LazyLayoutBeyondBoundsProviderModifierNode) {
        node.update(state, beyondBoundsInfo, reverseLayout, orientation)
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + beyondBoundsInfo.hashCode()
        result = 31 * result + reverseLayout.hashCode()
        result = 31 * result + orientation.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is LazyLayoutBeyondBoundsModifierElement) return false

        if (state != other.state) return false
        if (beyondBoundsInfo != other.beyondBoundsInfo) return false
        if (reverseLayout != other.reverseLayout) return false
        if (orientation != other.orientation) return false

        return true
    }

    override fun InspectorInfo.inspectableProperties() {
        // no op
    }
}

internal class LazyLayoutBeyondBoundsProviderModifierNode(
    private var state: LazyLayoutBeyondBoundsState,
    private var beyondBoundsInfo: LazyLayoutBeyondBoundsInfo,
    private var reverseLayout: Boolean,
    private var orientation: Orientation,
) :
    Modifier.Node(),
    LayoutModifierNode,
    BeyondBoundsLayoutProviderModifierNode,
    BeyondBoundsLayout {

    override val beyondBoundsLayout: BeyondBoundsLayout
        get() = this

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    companion object {
        private val emptyBeyondBoundsScope =
            object : BeyondBoundsScope {
                override val hasMoreContent = false
            }
    }

    override fun <T> layout(
        direction: BeyondBoundsLayout.LayoutDirection,
        block: BeyondBoundsScope.() -> T?,
    ): T? {
        // If the lazy list is empty, or if it does not have any visible items (Which implies
        // that there isn't space to add a single item), we don't attempt to layout any more items.
        // if the node is not yet attached or we haven't completed at least one layout pass..
        if (state.itemCount <= 0 || !state.hasVisibleItems || !isAttached) {
            return block.invoke(emptyBeyondBoundsScope)
        }

        // We use a new interval each time because this function is re-entrant.
        val startIndex =
            if (direction.isForward()) {
                state.lastPlacedIndex
            } else {
                state.firstPlacedIndex
            }
        var interval = beyondBoundsInfo.addInterval(startIndex, startIndex)
        var found: T? = null
        // Layout at most one viewport worth of items (times BeyondBoundsViewportFactor).
        val maxItemsToLayout =
            (BeyondBoundsViewportFactor * state.itemsPerViewport()).coerceAtMost(state.itemCount)
        var itemsCreatedSoFar = 0
        while (
            found == null &&
                interval.hasMoreContent(direction) &&
                itemsCreatedSoFar < maxItemsToLayout
        ) {
            // Add one extra beyond bounds item.
            interval =
                addNextInterval(interval, direction).also {
                    beyondBoundsInfo.removeInterval(interval)
                }
            itemsCreatedSoFar++
            remeasureSync()

            // When we invoke this block, the beyond bounds items are present.
            found =
                block.invoke(
                    object : BeyondBoundsScope {
                        override val hasMoreContent: Boolean
                            get() = interval.hasMoreContent(direction)
                    }
                )
        }

        // Dispose the items that are beyond the visible bounds.
        beyondBoundsInfo.removeInterval(interval)
        remeasureSync()
        return found
    }

    private fun BeyondBoundsLayout.LayoutDirection.isForward(): Boolean =
        when (this) {
            Before -> false
            After -> true
            Above -> reverseLayout
            Below -> !reverseLayout
            Left ->
                when (requireLayoutDirection()) {
                    Ltr -> reverseLayout
                    Rtl -> !reverseLayout
                }
            Right ->
                when (requireLayoutDirection()) {
                    Ltr -> !reverseLayout
                    Rtl -> reverseLayout
                }
            else -> unsupportedDirection()
        }

    private fun addNextInterval(
        currentInterval: Interval,
        direction: BeyondBoundsLayout.LayoutDirection,
    ): Interval {
        var start = currentInterval.start
        var end = currentInterval.end
        if (direction.isForward()) {
            end++
        } else {
            start--
        }
        return beyondBoundsInfo.addInterval(start, end)
    }

    private fun Interval.hasMoreContent(direction: BeyondBoundsLayout.LayoutDirection): Boolean {
        if (direction.isOppositeToOrientation()) return false
        return if (direction.isForward()) end < state.itemCount - 1 else start > 0
    }

    private fun BeyondBoundsLayout.LayoutDirection.isOppositeToOrientation(): Boolean {
        return when (this) {
            Above,
            Below -> orientation == Orientation.Horizontal
            Left,
            Right -> orientation == Orientation.Vertical
            Before,
            After -> false
            else -> unsupportedDirection()
        }
    }

    fun update(
        state: LazyLayoutBeyondBoundsState,
        beyondBoundsInfo: LazyLayoutBeyondBoundsInfo,
        reverseLayout: Boolean,
        orientation: Orientation,
    ) {
        this.state = state
        this.beyondBoundsInfo = beyondBoundsInfo
        this.reverseLayout = reverseLayout
        this.orientation = orientation
    }
}

private fun unsupportedDirection(): Nothing =
    error("Lazy list does not support beyond bounds layout for the specified direction")

private const val BeyondBoundsViewportFactor = 2

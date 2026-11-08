/*
 * Copyright 2022 The Android Open Source Project
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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.internal.requirePrecondition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.getScrollViewportLength
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.indexForKey
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.verticalScrollAxisRange
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.lazyLayoutSemantics(
    itemProviderLambda: () -> LazyLayoutItemProvider,
    state: LazyLayoutSemanticState,
    orientation: Orientation,
    userScrollEnabled: Boolean,
    reverseScrolling: Boolean,
): Modifier =
    this then
        LazyLayoutSemanticsModifier(
            itemProviderLambda = itemProviderLambda,
            state = state,
            orientation = orientation,
            userScrollEnabled = userScrollEnabled,
            reverseScrolling = reverseScrolling,
        )

@OptIn(ExperimentalFoundationApi::class)
private class LazyLayoutSemanticsModifier(
    val itemProviderLambda: () -> LazyLayoutItemProvider,
    val state: LazyLayoutSemanticState,
    val orientation: Orientation,
    val userScrollEnabled: Boolean,
    val reverseScrolling: Boolean,
) : ModifierNodeElement<LazyLayoutSemanticsModifierNode>() {
    override fun create(): LazyLayoutSemanticsModifierNode =
        LazyLayoutSemanticsModifierNode(
            itemProviderLambda = itemProviderLambda,
            state = state,
            orientation = orientation,
            userScrollEnabled = userScrollEnabled,
            reverseScrolling = reverseScrolling,
        )

    override fun update(node: LazyLayoutSemanticsModifierNode) {
        node.update(
            itemProviderLambda = itemProviderLambda,
            state = state,
            orientation = orientation,
            userScrollEnabled = userScrollEnabled,
            reverseScrolling = reverseScrolling,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        // Not a public modifier.
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LazyLayoutSemanticsModifier) return false

        if (itemProviderLambda !== other.itemProviderLambda) return false
        if (state != other.state) return false
        if (orientation != other.orientation) return false
        if (userScrollEnabled != other.userScrollEnabled) return false
        if (reverseScrolling != other.reverseScrolling) return false

        return true
    }

    override fun hashCode(): Int {
        var result = itemProviderLambda.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + orientation.hashCode()
        result = 31 * result + userScrollEnabled.hashCode()
        result = 31 * result + reverseScrolling.hashCode()
        return result
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class LazyLayoutSemanticsModifierNode(
    private var itemProviderLambda: () -> LazyLayoutItemProvider,
    private var state: LazyLayoutSemanticState,
    private var orientation: Orientation,
    private var userScrollEnabled: Boolean,
    private var reverseScrolling: Boolean,
) : Modifier.Node(), SemanticsModifierNode {

    override val shouldAutoInvalidate: Boolean
        get() = false

    private val isVertical
        get() = orientation == Orientation.Vertical

    private val collectionInfo
        get() = state.collectionInfo()

    private lateinit var scrollAxisRange: ScrollAxisRange

    private val indexForKeyMapping: (Any) -> Int = { needle ->
        val itemProvider = itemProviderLambda()
        var result = -1
        for (index in 0 until itemProvider.itemCount) {
            if (itemProvider.getKey(index) == needle) {
                result = index
                break
            }
        }
        result
    }

    private var scrollToIndexAction: ((Int) -> Boolean)? = null

    init {
        updateCachedSemanticsValues()
    }

    fun update(
        itemProviderLambda: () -> LazyLayoutItemProvider,
        state: LazyLayoutSemanticState,
        orientation: Orientation,
        userScrollEnabled: Boolean,
        reverseScrolling: Boolean,
    ) {
        // These properties are only read lazily, so we don't need to invalidate
        // semantics if they change.
        this.itemProviderLambda = itemProviderLambda
        this.state = state

        // These properties are read when appling semantics, but don't need to rebuild the cache.
        if (this.orientation != orientation) {
            this.orientation = orientation
            invalidateSemantics()
        }

        // These values are used to build different cached values. If they, we need to rebuild the
        // cache.
        if (
            this.userScrollEnabled != userScrollEnabled || this.reverseScrolling != reverseScrolling
        ) {
            this.userScrollEnabled = userScrollEnabled
            this.reverseScrolling = reverseScrolling
            updateCachedSemanticsValues()
            invalidateSemantics()
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        isTraversalGroup = true
        indexForKey(indexForKeyMapping)

        if (isVertical) {
            verticalScrollAxisRange = scrollAxisRange
        } else {
            horizontalScrollAxisRange = scrollAxisRange
        }

        scrollToIndexAction?.let { scrollToIndex(action = it) }

        getScrollViewportLength { (state.viewport - state.contentPadding).toFloat() }

        collectionInfo = this@LazyLayoutSemanticsModifierNode.collectionInfo
    }

    private fun updateCachedSemanticsValues() {
        scrollAxisRange =
            ScrollAxisRange(
                value = { state.scrollOffset },
                maxValue = { state.maxScrollOffset },
                reverseScrolling = reverseScrolling,
            )

        scrollToIndexAction =
            if (userScrollEnabled) {
                { index ->
                    val itemProvider = itemProviderLambda()
                    requirePrecondition(index >= 0 && index < itemProvider.itemCount) {
                        "Can't scroll to index $index, it is out of " +
                            "bounds [0, ${itemProvider.itemCount})"
                    }
                    coroutineScope.launch { state.scrollToItem(index) }
                    true
                }
            } else {
                null
            }
    }
}

internal interface LazyLayoutSemanticState {
    val viewport: Int
    val contentPadding: Int
    val scrollOffset: Float
    val maxScrollOffset: Float

    fun collectionInfo(): CollectionInfo

    suspend fun scrollToItem(index: Int)
}

// It is impossible for lazy lists to provide an absolute scroll offset because the size of the
// items above the viewport is not known, but the AccessibilityEvent system API expects one
// anyway. So this provides a best-effort pseudo-offset that avoids breaking existing behavior.
//
// The key properties that A11y services are known to actually rely on are:
// A) each scroll change generates a TYPE_VIEW_SCROLLED AccessibilityEvent
// B) the integer offset in the AccessibilityEvent is different than the last one (note that the
// magnitude and direction of the change does not matter for the known use cases)
// C) scrollability is indicated by whether the scroll position is exactly 0 or exactly
// maxScrollOffset
//
// To preserve property B) as much as possible, the constant 500 is chosen to be larger than a
// single scroll delta would realistically be, while small enough to avoid losing precision due
// to the 24-bit float significand of ScrollAxisRange with realistic list sizes (if there are
// fewer than ~16000 items, the integer value is exactly preserved).
internal fun estimatedLazyScrollOffset(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Float {
    return (firstVisibleItemScrollOffset + firstVisibleItemIndex * 500).toFloat()
}

internal fun estimatedLazyMaxScrollOffset(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    canScrollForward: Boolean,
): Float {
    return if (canScrollForward) {
        // If we can scroll further, indicate that by setting it slightly higher than
        // the current value
        estimatedLazyScrollOffset(firstVisibleItemIndex, firstVisibleItemScrollOffset) + 100
    } else {
        // If we can't scroll further, the current value is the max
        estimatedLazyScrollOffset(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }
}

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

@file:Suppress("DEPRECATION") // b/420551535

package com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy

import androidx.compose.foundation.ExperimentalFoundationApi
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutPrefetchState
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.NestedPrefetchScope
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.PrefetchScheduler
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.UnspecifiedNestedPrefetchCount
import androidx.compose.runtime.Stable

/**
 * Implementations of this interface control which indices of a LazyList should be prefetched
 * (precomposed and premeasured during idle time) as the user interacts with it.
 *
 * Implementations should invoke [LazyListPrefetchScope.schedulePrefetch] to schedule prefetches
 * from the [onScroll] and [onVisibleItemsUpdated] callbacks. If any of the returned PrefetchHandles
 * no longer need to be prefetched, use [LazyLayoutPrefetchState.PrefetchHandle.cancel] to cancel
 * the request.
 */
@ExperimentalFoundationApi
interface LazyListPrefetchStrategy {

    /**
     * A [PrefetchScheduler] implementation which will be used to execute prefetch requests for this
     * strategy implementation. If null, the default [PrefetchScheduler] for the platform will be
     * used.
     */
    @Deprecated(
        "Customization of PrefetchScheduler is no longer supported. LazyLayout will attach " +
            "an appropriate scheduler internally."
    )
    val prefetchScheduler: PrefetchScheduler?
        get() = null

    /**
     * onScroll is invoked when the LazyList scrolls, whether or not the visible items have changed.
     * If the visible items have also changed, then this will be invoked in the same frame *after*
     * [onVisibleItemsUpdated].
     *
     * @param delta the change in scroll direction. Delta < 0 indicates scrolling down while delta >
     *   0 indicates scrolling up.
     * @param layoutInfo the current [LazyListLayoutInfo]
     */
    fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo)

    /**
     * onVisibleItemsUpdated is invoked when the LazyList scrolls if the visible items have changed.
     *
     * @param layoutInfo the current [LazyListLayoutInfo]. Info about the updated visible items can
     *   be found in [LazyListLayoutInfo.visibleItemsInfo].
     */
    fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo)

    /**
     * onNestedPrefetch is invoked when a parent LazyLayout has prefetched content which contains
     * this LazyList. It gives this LazyList a chance to request prefetch for some of its own
     * children before coming onto screen.
     *
     * Implementations can use [NestedPrefetchScope.schedulePrefetch] to schedule child prefetches.
     * For example, this is useful if this LazyList is a LazyRow that is a child of a LazyColumn: in
     * that case, [onNestedPrefetch] can schedule the children it expects to be visible when it
     * comes onto screen, giving the LazyLayout infra a chance to compose these children ahead of
     * time and reduce jank.
     *
     * Generally speaking, [onNestedPrefetch] should only request prefetch for children that it
     * expects to actually be visible when this list is scrolled into view.
     *
     * @param firstVisibleItemIndex the index of the first visible item. It should be used to start
     *   prefetching from the correct index in case the list has been created at a non-zero offset.
     */
    fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int)
}

/** Scope for callbacks in [LazyListPrefetchStrategy] which allows prefetches to be requested. */
@ExperimentalFoundationApi
interface LazyListPrefetchScope {

    /**
     * Schedules a prefetch for the given index. Requests are executed in the order they're
     * requested. If a requested prefetch is no longer necessary (for example, due to changing
     * scroll direction), the request should be canceled via
     * [LazyLayoutPrefetchState.PrefetchHandle.cancel].
     *
     * See [PrefetchScheduler].
     *
     * @param index the index of the child to prefetch
     * @param onPrefetchFinished A callback that will be invoked when the prefetching of this item
     *   is completed. This means precomposition and premeasuring. If the request is canceled before
     *   either phases can complete, this callback won't be called. The item index and the main axis
     *   size in pixels of the prefetched item is available as a parameter of this callback. See
     *   [LazyListPrefetchResultScope] for additional information about the prefetched item.
     */
    fun schedulePrefetch(
        index: Int,
        onPrefetchFinished: (LazyListPrefetchResultScope.() -> Unit)? = null,
    ): LazyLayoutPrefetchState.PrefetchHandle
}

/**
 * Creates an instance of the default [LazyListPrefetchStrategy], allowing for customization of the
 * nested prefetch count.
 *
 * @param nestedPrefetchItemCount specifies how many inner items should be prefetched when this
 *   LazyList is nested inside another LazyLayout. For example, if this is the state for a
 *   horizontal LazyList nested in a vertical LazyList, you might want to set this to the number of
 *   items that will be visible when this list is scrolled into view. If automatic nested prefetch
 *   is enabled, this value will be used as the initial count and the strategy will adapt the count
 *   automatically.
 */
@ExperimentalFoundationApi
fun LazyListPrefetchStrategy(nestedPrefetchItemCount: Int = 2): LazyListPrefetchStrategy =
    DefaultLazyListPrefetchStrategy(nestedPrefetchItemCount)

/**
 * The default prefetching strategy for LazyLists - this will be used automatically if no other
 * strategy is provided.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
private class DefaultLazyListPrefetchStrategy(private val initialNestedPrefetchItemCount: Int = 2) :
    LazyListPrefetchStrategy {

    /**
     * The index scheduled to be prefetched (or the last prefetched index if the prefetch is done).
     */
    private var indexToPrefetch = -1

    /** The handle associated with the current index from [indexToPrefetch]. */
    private var currentPrefetchHandle: LazyLayoutPrefetchState.PrefetchHandle? = null

    /**
     * Keeps the scrolling direction during the previous calculation in order to be able to detect
     * the scrolling direction change.
     */
    private var wasScrollingForward = false

    private var previousPassItemCount = UnsetItemCount
    private var previousPassDelta = 0f

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val scrollingForward = delta < 0
            val indexToPrefetch = layoutInfo.calculateIndexToPrefetch(scrollingForward)
            if (indexToPrefetch in 0 until layoutInfo.totalItemsCount) {
                if (indexToPrefetch != this@DefaultLazyListPrefetchStrategy.indexToPrefetch) {
                    if (wasScrollingForward != scrollingForward) {
                        // the scrolling direction has been changed which means the last prefetched
                        // is not going to be reached anytime soon so it is safer to dispose it.
                        // if this item is already visible it is safe to call the method anyway
                        // as it will be no-op
                        resetPrefetchState()
                    }
                    this@DefaultLazyListPrefetchStrategy.wasScrollingForward = scrollingForward
                    this@DefaultLazyListPrefetchStrategy.indexToPrefetch = indexToPrefetch
                    currentPrefetchHandle = schedulePrefetch(indexToPrefetch)
                }
                if (scrollingForward) {
                    val lastItem = layoutInfo.visibleItemsInfo.last()
                    val spacing = layoutInfo.mainAxisItemSpacing
                    val distanceToPrefetchItem =
                        lastItem.offset + lastItem.size + spacing - layoutInfo.viewportEndOffset
                    // if in the next frame we will get the same delta will we reach the item?
                    if (distanceToPrefetchItem < -delta) {
                        currentPrefetchHandle?.markAsUrgent()
                    }
                } else {
                    val firstItem = layoutInfo.visibleItemsInfo.first()
                    val distanceToPrefetchItem = layoutInfo.viewportStartOffset - firstItem.offset
                    // if in the next frame we will get the same delta will we reach the item?
                    if (distanceToPrefetchItem < delta) {
                        currentPrefetchHandle?.markAsUrgent()
                    }
                }
            }
        }
        previousPassDelta = delta
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {

        layoutInfo.evaluatePrefetchForCancellation(indexToPrefetch, wasScrollingForward)

        val currentPassItemCount = layoutInfo.totalItemsCount
        // total item count changed, re-trigger prefetch.
        if (
            previousPassItemCount != UnsetItemCount && // we already have info about the item count
                previousPassDelta != 0.0f && // and scroll direction
                previousPassItemCount != currentPassItemCount && // and the item count changed
                layoutInfo.visibleItemsInfo.isNotEmpty()
        ) {
            val indexToPrefetch = layoutInfo.calculateIndexToPrefetch(previousPassDelta < 0)
            if (indexToPrefetch in 0 until currentPassItemCount) {
                this@DefaultLazyListPrefetchStrategy.indexToPrefetch = indexToPrefetch
                currentPrefetchHandle = schedulePrefetch(indexToPrefetch)
            }
        }

        previousPassItemCount = currentPassItemCount
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) {
        val resolvedNestedPrefetchItemCount =
            if (nestedPrefetchItemCount == UnspecifiedNestedPrefetchCount) {
                initialNestedPrefetchItemCount
            } else {
                nestedPrefetchItemCount
            }
        repeat(resolvedNestedPrefetchItemCount) { i ->
            schedulePrecomposition(firstVisibleItemIndex + i)
        }
    }

    private fun resetPrefetchState() {
        indexToPrefetch = -1
        currentPrefetchHandle?.cancel()
        currentPrefetchHandle = null
    }

    private fun LazyListLayoutInfo.calculateIndexToPrefetch(scrollingForward: Boolean): Int {
        return if (scrollingForward) {
            visibleItemsInfo.last().index + 1
        } else {
            visibleItemsInfo.first().index - 1
        }
    }

    private fun LazyListLayoutInfo.evaluatePrefetchForCancellation(
        currentPrefetchingIndex: Int,
        scrollingForward: Boolean,
    ) {
        if (currentPrefetchingIndex != -1 && visibleItemsInfo.isNotEmpty()) {
            val expectedPrefetchIndex = calculateIndexToPrefetch(scrollingForward)
            if (currentPrefetchingIndex != expectedPrefetchIndex) {
                resetPrefetchState()
            }
        }
    }
}

/**
 * A scope for [LazyListPrefetchScope.schedulePrefetch] callbacks. The scope provides additional
 * information about a prefetched item.
 */
@ExperimentalFoundationApi
sealed interface LazyListPrefetchResultScope {

    /** The index of the prefetched item */
    val index: Int

    /** The main axis size in pixels of the prefetched item */
    val mainAxisSize: Int
}

@OptIn(ExperimentalFoundationApi::class)
internal class LazyListPrefetchResultScopeImpl(
    override val index: Int,
    override val mainAxisSize: Int,
) : LazyListPrefetchResultScope

private const val UnsetItemCount = -1

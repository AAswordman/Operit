/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.annotation.IntRange as AndroidXIntRange
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.internal.checkPrecondition
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyListState.Companion.Saver
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.AwaitFirstLayoutModifier
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.CacheWindowLogic
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutBeyondBoundsInfo
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutCacheWindow
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutItemAnimator
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutPinnedItemList
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutPrefetchState
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutScrollDeltaBetweenPasses
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.ObservableScopeInvalidator
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.animateScrollToItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.traceValue
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Creates a [LazyListState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialFirstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
 * @param initialFirstVisibleItemScrollOffset the initial value for
 *   [LazyListState.firstVisibleItemScrollOffset]
 */
@Composable
fun rememberLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState {
    return rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)
    }
}

/**
 * Creates a [LazyListState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialFirstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
 * @param initialFirstVisibleItemScrollOffset the initial value for
 *   [LazyListState.firstVisibleItemScrollOffset]
 * @param prefetchStrategy the [LazyListPrefetchStrategy] to use for prefetching content in this
 *   list
 */
@ExperimentalFoundationApi
@Composable
fun rememberLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    prefetchStrategy: LazyListPrefetchStrategy = remember { LazyListPrefetchStrategy() },
): LazyListState {
    return rememberSaveable(prefetchStrategy, saver = LazyListState.saver(prefetchStrategy)) {
        LazyListState(
            initialFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset,
            prefetchStrategy,
        )
    }
}

/**
 * Creates a [LazyListState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param cacheWindow specifies the size of the ahead and behind window to be used as per
 *   [LazyLayoutCacheWindow].
 * @param initialFirstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
 * @param initialFirstVisibleItemScrollOffset the initial value for
 *   [LazyListState.firstVisibleItemScrollOffset]
 */
@ExperimentalFoundationApi
@Composable
fun rememberLazyListState(
    cacheWindow: LazyLayoutCacheWindow,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState {
    return rememberSaveable(cacheWindow, saver = LazyListState.saver(cacheWindow)) {
        LazyListState(
            cacheWindow,
            initialFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset,
        )
    }
}

/**
 * A state object that can be hoisted to control and observe scrolling.
 *
 * In most cases, this will be created via [rememberLazyListState].
 *
 * @param firstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
 * @param firstVisibleItemScrollOffset the initial value for
 *   [LazyListState.firstVisibleItemScrollOffset]
 * @param prefetchStrategy the [LazyListPrefetchStrategy] to use for prefetching content in this
 *   list
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class LazyListState
@ExperimentalFoundationApi
constructor(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
    internal val prefetchStrategy: LazyListPrefetchStrategy = LazyListPrefetchStrategy(),
) : ScrollableState {

    /**
     * @param cacheWindow specifies the size of the ahead and behind window to be used as per
     *   [LazyLayoutCacheWindow].
     * @param firstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
     * @param firstVisibleItemScrollOffset the initial value for
     *   [LazyListState.firstVisibleItemScrollOffset]
     */
    @ExperimentalFoundationApi
    constructor(
        cacheWindow: LazyLayoutCacheWindow,
        firstVisibleItemIndex: Int = 0,
        firstVisibleItemScrollOffset: Int = 0,
    ) : this(
        firstVisibleItemIndex,
        firstVisibleItemScrollOffset,
        LazyListCacheWindowStrategy(cacheWindow),
    )

    /**
     * @param firstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
     * @param firstVisibleItemScrollOffset the initial value for
     *   [LazyListState.firstVisibleItemScrollOffset]
     */
    constructor(
        firstVisibleItemIndex: Int = 0,
        firstVisibleItemScrollOffset: Int = 0,
    ) : this(firstVisibleItemIndex, firstVisibleItemScrollOffset, LazyListPrefetchStrategy())

    internal var hasLookaheadOccurred: Boolean = false
        private set

    internal var approachLayoutInfo: LazyListMeasureResult? = null
        private set

    // always execute requests in high priority
    private var executeRequestsInHighPriorityMode = false

    /** The holder class for the current scroll position. */
    private val scrollPosition =
        LazyListScrollPosition(firstVisibleItemIndex, firstVisibleItemScrollOffset)

    /**
     * The index of the first item that is visible within the scrollable viewport area not including
     * items in the content padding region. For the first visible item that includes items in the
     * content padding please use [LazyListLayoutInfo.visibleItemsInfo].
     *
     * Note that this property is observable and if you use it in the composable function it will be
     * recomposed on every change causing potential performance issues.
     *
     * If you want to run some side effects like sending an analytics event or updating a state
     * based on this value consider using "snapshotFlow":
     *
     * @sample androidx.compose.foundation.samples.UsingListScrollPositionForSideEffectSample
     *
     * If you need to use it in the composition then consider wrapping the calculation into a
     * derived state in order to only have recompositions when the derived value changes:
     *
     * @sample androidx.compose.foundation.samples.UsingListScrollPositionInCompositionSample
     */
    val firstVisibleItemIndex: Int
        @FrequentlyChangingValue get() = scrollPosition.index

    /**
     * The scroll offset of the first visible item. Scrolling forward is positive - i.e., the amount
     * that the item is offset backwards.
     *
     * Note that this property is observable and if you use it in the composable function it will be
     * recomposed on every scroll causing potential performance issues.
     *
     * @see firstVisibleItemIndex for samples with the recommended usage patterns.
     */
    val firstVisibleItemScrollOffset: Int
        @FrequentlyChangingValue get() = scrollPosition.scrollOffset

    /** Backing state for [layoutInfo] */
    private val layoutInfoState = mutableStateOf(EmptyLazyListMeasureResult, neverEqualPolicy())

    /**
     * The object of [LazyListLayoutInfo] calculated during the last layout pass. For example, you
     * can use it to calculate what items are currently visible.
     *
     * Note that this property is observable and is updated after every scroll or remeasure. If you
     * use it in the composable function it will be recomposed on every change causing potential
     * performance issues including infinity recomposition loop. Therefore, avoid using it in the
     * composition.
     *
     * If you want to run some side effects like sending an analytics event or updating a state
     * based on this value consider using "snapshotFlow":
     *
     * @sample androidx.compose.foundation.samples.UsingListLayoutInfoForSideEffectSample
     */
    val layoutInfo: LazyListLayoutInfo
        @FrequentlyChangingValue get() = layoutInfoState.value

    /**
     * [InteractionSource] that will be used to dispatch drag events when this list is being
     * dragged. If you want to know whether the fling (or animated scroll) is in progress, use
     * [isScrollInProgress].
     */
    val interactionSource: InteractionSource
        get() = internalInteractionSource

    internal val internalInteractionSource: MutableInteractionSource = MutableInteractionSource()

    /**
     * The amount of scroll to be consumed in the next layout pass. Scrolling forward is negative
     * - that is, it is the amount that the items are offset in y
     */
    internal var scrollToBeConsumed = 0f
        private set

    internal val density: Density
        get() = layoutInfoState.value.density

    /**
     * The ScrollableController instance. We keep it as we need to call stopAnimation on it once we
     * reached the end of the list.
     */
    private val scrollableState = ScrollableState { -onScroll(-it) }

    /** Only used for testing to confirm that we're not making too many measure passes */
    /*@VisibleForTesting*/
    internal var numMeasurePasses: Int = 0
        private set

    /** Only used for testing to disable prefetching when needed to test the main logic. */
    /*@VisibleForTesting*/
    internal var prefetchingEnabled: Boolean = true

    /**
     * The [Remeasurement] object associated with our layout. It allows us to remeasure
     * synchronously during scroll.
     */
    internal var remeasurement: Remeasurement? = null
        private set

    /** The modifier which provides [remeasurement]. */
    internal val remeasurementModifier =
        object : RemeasurementModifier {
            override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                this@LazyListState.remeasurement = remeasurement
            }
        }

    /**
     * Provides a modifier which allows to delay some interactions (e.g. scroll) until layout is
     * ready.
     */
    internal val awaitLayoutModifier = AwaitFirstLayoutModifier()

    internal val itemAnimator = LazyLayoutItemAnimator<LazyListMeasuredItem>()

    internal val beyondBoundsInfo = LazyLayoutBeyondBoundsInfo()

    @Suppress("DEPRECATION") // b/420551535
    internal val prefetchState =
        LazyLayoutPrefetchState(prefetchStrategy.prefetchScheduler) {
            with(prefetchStrategy) {
                onNestedPrefetch(Snapshot.withoutReadObservation { firstVisibleItemIndex })
            }
        }

    private val prefetchScope: LazyListPrefetchScope =
        object : LazyListPrefetchScope {
            override fun schedulePrefetch(
                index: Int,
                onPrefetchFinished: (LazyListPrefetchResultScope.() -> Unit)?,
            ): LazyLayoutPrefetchState.PrefetchHandle {
                // Without read observation since this can be triggered from scroll - this will then
                // cause us to recompose when the measure result changes. We don't care since the
                // prefetch is best effort.
                val lastMeasureResult = Snapshot.withoutReadObservation { layoutInfoState.value }
                return prefetchState.schedulePrecompositionAndPremeasure(
                    index,
                    lastMeasureResult.childConstraints,
                    executeRequestsInHighPriorityMode,
                ) {
                    if (onPrefetchFinished != null) {
                        var mainAxisItemSize = 0
                        repeat(placeablesCount) {
                            mainAxisItemSize +=
                                if (lastMeasureResult.orientation == Orientation.Vertical) {
                                    getSize(it).height
                                } else {
                                    getSize(it).width
                                }
                        }

                        onPrefetchFinished.invoke(
                            LazyListPrefetchResultScopeImpl(index, mainAxisItemSize)
                        )
                    }
                }
            }
        }

    private val _scrollIndicatorState =
        object : ScrollIndicatorState {
            override val scrollOffset: Int
                get() = calculateScrollOffset()

            override val contentSize: Int
                get() = layoutInfo.calculateContentSize()

            override val viewportSize: Int
                get() = layoutInfo.singleAxisViewportSize
        }

    private fun calculateScrollOffset(): Int {
        return (layoutInfo.visibleItemsAverageSize() * firstVisibleItemIndex) +
            firstVisibleItemScrollOffset
    }

    /** Stores currently pinned items which are always composed. */
    internal val pinnedItems = LazyLayoutPinnedItemList()

    internal val nearestRange: IntRange by scrollPosition.nearestRangeState

    /**
     * Instantly brings the item at [index] to the top of the viewport, offset by [scrollOffset]
     * pixels.
     *
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll. Note that
     *   positive offset refers to forward scroll, so in a top-to-bottom list, positive offset will
     *   scroll the item further upward (taking it partly offscreen).
     */
    suspend fun scrollToItem(@AndroidXIntRange(from = 0) index: Int, scrollOffset: Int = 0) {
        scroll { snapToItemIndexInternal(index, scrollOffset, forceRemeasure = true) }
    }

    internal val measurementScopeInvalidator = ObservableScopeInvalidator()

    /**
     * Requests the item at [index] to be at the start of the viewport during the next remeasure,
     * offset by [scrollOffset], and schedules a remeasure.
     *
     * The scroll position will be updated to the requested position rather than maintain the index
     * based on the first visible item key (when a data set change will also be applied during the
     * next remeasure), but *only* for the next remeasure.
     *
     * Any scroll in progress will be cancelled.
     *
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll. Note that
     *   positive offset refers to forward scroll, so in a top-to-bottom list, positive offset will
     *   scroll the item further upward (taking it partly offscreen).
     */
    fun requestScrollToItem(@AndroidXIntRange(from = 0) index: Int, scrollOffset: Int = 0) {
        // Cancel any scroll in progress.
        if (isScrollInProgress) {
            layoutInfoState.value.coroutineScope.launch { scroll {} }
        }

        snapToItemIndexInternal(index, scrollOffset, forceRemeasure = false)
    }

    /**
     * Snaps to the requested scroll position. Synchronously executes remeasure if [forceRemeasure]
     * is true, and schedules a remeasure if false.
     */
    internal fun snapToItemIndexInternal(index: Int, scrollOffset: Int, forceRemeasure: Boolean) {
        val positionChanged =
            scrollPosition.index != index || scrollPosition.scrollOffset != scrollOffset
        // sometimes this method is called not to scroll, but to stay on the same index when
        // the data changes, as by default we maintain the scroll position by key, not index.
        // when this happens we don't need to reset the animations as from the user perspective
        // we didn't scroll anywhere and if there is an offset change for an item, this change
        // should be animated.
        // however, when the request is to really scroll to a different position, we have to
        // reset previously known item positions as we don't want offset changes to be animated.
        // this offset should be considered as a scroll, not the placement change.
        if (positionChanged) {
            itemAnimator.reset()
            // we changed positions, cancel existing requests and wait for the next scroll to
            // refill the window
            (prefetchStrategy as? CacheWindowLogic)?.resetStrategy()
        }
        scrollPosition.requestPositionAndForgetLastKnownKey(index, scrollOffset)

        if (forceRemeasure) {
            remeasurement?.forceRemeasure()
        } else {
            measurementScopeInvalidator.invalidateScope()
        }
    }

    /**
     * Call this function to take control of scrolling and gain the ability to send scroll events
     * via [ScrollScope.scrollBy]. All actions that change the logical scroll position must be
     * performed within a [scroll] block (even if they don't call any other methods on this object)
     * in order to guarantee that mutual exclusion is enforced.
     *
     * If [scroll] is called from elsewhere, this will be canceled.
     */
    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) {
        if (layoutInfoState.value === EmptyLazyListMeasureResult) {
            awaitLayoutModifier.waitForFirstLayout()
        }
        scrollableState.scroll(scrollPriority, block)
    }

    override fun dispatchRawDelta(delta: Float): Float = scrollableState.dispatchRawDelta(delta)

    override val isScrollInProgress: Boolean
        get() = scrollableState.isScrollInProgress

    override var canScrollForward: Boolean by mutableStateOf(false)
        private set

    override var canScrollBackward: Boolean by mutableStateOf(false)
        private set

    @get:Suppress("GetterSetterNames")
    override val lastScrolledForward: Boolean
        get() = scrollableState.lastScrolledForward

    @get:Suppress("GetterSetterNames")
    override val lastScrolledBackward: Boolean
        get() = scrollableState.lastScrolledBackward

    override val scrollIndicatorState: ScrollIndicatorState?
        get() = _scrollIndicatorState

    internal val placementScopeInvalidator = ObservableScopeInvalidator()

    // TODO: Coroutine scrolling APIs will allow this to be private again once we have more
    //  fine-grained control over scrolling
    /*@VisibleForTesting*/
    internal fun onScroll(distance: Float): Float {
        if (distance < 0 && !canScrollForward || distance > 0 && !canScrollBackward) {
            return 0f
        }
        checkPrecondition(abs(scrollToBeConsumed) <= 0.5f) {
            "entered drag with non-zero pending scroll"
        }
        executeRequestsInHighPriorityMode = true
        scrollToBeConsumed += distance

        // scrollToBeConsumed will be consumed synchronously during the forceRemeasure invocation
        // inside measuring we do scrollToBeConsumed.roundToInt() so there will be no scroll if
        // we have less than 0.5 pixels
        if (abs(scrollToBeConsumed) > 0.5f) {
            val preScrollToBeConsumed = scrollToBeConsumed
            val intDelta = scrollToBeConsumed.fastRoundToInt()

            var scrolledLayoutInfo =
                layoutInfoState.value.copyWithScrollDeltaWithoutRemeasure(
                    delta = intDelta,
                    updateAnimations = !hasLookaheadOccurred,
                )
            if (scrolledLayoutInfo != null && this.approachLayoutInfo != null) {
                // if we were able to scroll the lookahead layout info without remeasure, lets
                // try to do the same for approach layout info (sometimes they diverge).
                val scrolledApproachLayoutInfo =
                    approachLayoutInfo?.copyWithScrollDeltaWithoutRemeasure(
                        delta = intDelta,
                        updateAnimations = true,
                    )
                if (scrolledApproachLayoutInfo != null) {
                    // we can apply scroll delta for both phases without remeasure
                    approachLayoutInfo = scrolledApproachLayoutInfo
                } else {
                    // we can't apply scroll delta for approach, so we have to remeasure
                    scrolledLayoutInfo = null
                }
            }

            if (scrolledLayoutInfo != null) {
                applyMeasureResult(
                    result = scrolledLayoutInfo,
                    isLookingAhead = hasLookaheadOccurred,
                    visibleItemsStayedTheSame = true,
                )
                // we don't need to remeasure, so we only trigger re-placement:
                placementScopeInvalidator.invalidateScope()

                notifyPrefetchOnScroll(
                    preScrollToBeConsumed - scrollToBeConsumed,
                    scrolledLayoutInfo,
                )
            } else {
                remeasurement?.forceRemeasure()
                notifyPrefetchOnScroll(preScrollToBeConsumed - scrollToBeConsumed, this.layoutInfo)
            }
        }

        // here scrollToBeConsumed is already consumed during the forceRemeasure invocation
        if (abs(scrollToBeConsumed) <= 0.5f) {
            // We consumed all of it - we'll hold onto the fractional scroll for later, so report
            // that we consumed the whole thing
            return distance
        } else {
            val scrollConsumed = distance - scrollToBeConsumed
            // We did not consume all of it - return the rest to be consumed elsewhere (e.g.,
            // nested scrolling)
            scrollToBeConsumed = 0f // We're not consuming the rest, give it back
            return scrollConsumed
        }
    }

    private fun notifyPrefetchOnScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        if (prefetchingEnabled) {
            with(prefetchStrategy) { prefetchScope.onScroll(delta, layoutInfo) }
        }
    }

    /**
     * Animate (smooth scroll) to the given item.
     *
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll. Note that
     *   positive offset refers to forward scroll, so in a top-to-bottom list, positive offset will
     *   scroll the item further upward (taking it partly offscreen).
     */
    suspend fun animateScrollToItem(@AndroidXIntRange(from = 0) index: Int, scrollOffset: Int = 0) {
        scroll {
            LazyLayoutScrollScope(this@LazyListState, this)
                .animateScrollToItem(index, scrollOffset, NumberOfItemsToTeleport, density)
        }
    }

    /** Updates the state with the new calculated scroll position and consumed scroll. */
    internal fun applyMeasureResult(
        result: LazyListMeasureResult,
        isLookingAhead: Boolean,
        visibleItemsStayedTheSame: Boolean = false,
    ) {
        val adjustedResult =
            if (!visibleItemsStayedTheSame && !isLookingAhead) {
                maybeCompensateForItemResize(result)
            } else {
                result
            }

        // update the prefetch state with the number of nested prefetch items this layout
        // should use.
        prefetchState.idealNestedPrefetchCount = adjustedResult.visibleItemsInfo.size

        if (!isLookingAhead && hasLookaheadOccurred) {
            // If there was already a lookahead pass, record this result as approach result
            approachLayoutInfo = adjustedResult
            Snapshot.withoutReadObservation {
                if (
                    _lazyLayoutScrollDeltaBetweenPasses.isActive &&
                        adjustedResult.firstVisibleItem?.index == scrollPosition.index &&
                        adjustedResult.firstVisibleItemScrollOffset == scrollPosition.scrollOffset
                ) {
                    _lazyLayoutScrollDeltaBetweenPasses.stop()
                }
            }
        } else {
            if (isLookingAhead) {
                hasLookaheadOccurred = true
            }

            canScrollBackward = adjustedResult.canScrollBackward
            canScrollForward = adjustedResult.canScrollForward
            scrollToBeConsumed -= adjustedResult.consumedScroll
            layoutInfoState.value = adjustedResult

            if (visibleItemsStayedTheSame) {
                scrollPosition.updateScrollOffset(adjustedResult.firstVisibleItemScrollOffset)
            } else {
                traceVisibleItems(adjustedResult) // trace when visible window changed
                scrollPosition.updateFromMeasureResult(adjustedResult)
                if (prefetchingEnabled) {
                    with(prefetchStrategy) { prefetchScope.onVisibleItemsUpdated(adjustedResult) }
                }
            }

            if (isLookingAhead) {
                _lazyLayoutScrollDeltaBetweenPasses.updateScrollDeltaForApproach(
                    adjustedResult.scrollBackAmount,
                    adjustedResult.density,
                    adjustedResult.coroutineScope,
                )
            }
            numMeasurePasses++
        }
    }

    private fun maybeCompensateForItemResize(
        result: LazyListMeasureResult,
    ): LazyListMeasureResult {
        if (abs(result.consumedScroll) > 0.5f) {
            return result
        }

        val previousLayoutInfo = layoutInfoState.value
        val previousFirstVisibleItem = previousLayoutInfo.visibleItemsInfo.firstOrNull() ?: return result
        val newFirstVisibleItem = result.firstVisibleItem ?: return result

        if (previousFirstVisibleItem.key != newFirstVisibleItem.key) {
            return result
        }

        val previousAnchor =
            previousLayoutInfo.visibleItemsInfo.firstOrNull { it.key == newFirstVisibleItem.key }
                ?: return result
        val newAnchor =
            result.visibleItemsInfo.firstOrNull { it.key == newFirstVisibleItem.key } ?: return result
        val anchorOffsetDelta = newAnchor.offset - previousAnchor.offset
        if (anchorOffsetDelta == 0) {
            return result
        }

        val adjustedScrollOffset = result.firstVisibleItemScrollOffset + anchorOffsetDelta
        val firstVisibleItemSize = newFirstVisibleItem.mainAxisSizeWithSpacings
        if (adjustedScrollOffset !in 0 until firstVisibleItemSize) {
            return result
        }

        return result.copyWithAnchorOffsetCompensation(anchorOffsetDelta)
    }

    private fun traceVisibleItems(measureResult: LazyListMeasureResult) {
        val firstVisibleItem = measureResult.visibleItemsInfo.firstOrNull()
        val lastVisibleItem = measureResult.visibleItemsInfo.lastOrNull()
        traceValue("firstVisibleItem:index", firstVisibleItem?.index?.toLong() ?: -1L)
        traceValue("lastVisibleItem:index", lastVisibleItem?.index?.toLong() ?: -1L)
    }

    internal val scrollDeltaBetweenPasses: Float
        get() = _lazyLayoutScrollDeltaBetweenPasses.scrollDeltaBetweenPasses

    private val _lazyLayoutScrollDeltaBetweenPasses = LazyLayoutScrollDeltaBetweenPasses()

    /**
     * When the user provided custom keys for the items we can try to detect when there were items
     * added or removed before our current first visible item and keep this item as the first
     * visible one even given that its index has been changed. The scroll position will not be
     * updated if [requestScrollToItem] was called since the last time this method was called.
     */
    internal fun updateScrollPositionIfTheFirstItemWasMoved(
        itemProvider: LazyListItemProvider,
        firstItemIndex: Int,
    ): Int = scrollPosition.updateScrollPositionIfTheFirstItemWasMoved(itemProvider, firstItemIndex)

    companion object {
        /** The default [Saver] implementation for [LazyListState]. */
        val Saver: Saver<LazyListState, *> =
            listSaver(
                save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
                restore = {
                    LazyListState(
                        firstVisibleItemIndex = it[0],
                        firstVisibleItemScrollOffset = it[1],
                    )
                },
            )

        /**
         * A [Saver] implementation for [LazyListState] that handles setting a custom
         * [LazyListPrefetchStrategy].
         */
        internal fun saver(prefetchStrategy: LazyListPrefetchStrategy): Saver<LazyListState, *> =
            listSaver(
                save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
                restore = {
                    LazyListState(
                        firstVisibleItemIndex = it[0],
                        firstVisibleItemScrollOffset = it[1],
                        prefetchStrategy,
                    )
                },
            )

        /**
         * A [Saver] implementation for [LazyListState] that handles setting a custom
         * [LazyLayoutCacheWindow].
         */
        internal fun saver(cacheWindow: LazyLayoutCacheWindow): Saver<LazyListState, *> =
            listSaver(
                save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
                restore = {
                    LazyListState(
                        firstVisibleItemIndex = it[0],
                        firstVisibleItemScrollOffset = it[1],
                        cacheWindow = cacheWindow,
                    )
                },
            )
    }
}

private val EmptyLazyListMeasureResult =
    LazyListMeasureResult(
        firstVisibleItem = null,
        firstVisibleItemScrollOffset = 0,
        canScrollForward = false,
        consumedScroll = 0f,
        measureResult =
            object : MeasureResult {
                override val width: Int = 0
                override val height: Int = 0

                @Suppress("PrimitiveInCollection")
                override val alignmentLines: Map<AlignmentLine, Int> = emptyMap()

                override fun placeChildren() {}
            },
        scrollBackAmount = 0f,
        visibleItemsInfo = emptyList(),
        viewportStartOffset = 0,
        viewportEndOffset = 0,
        totalItemsCount = 0,
        reverseLayout = false,
        orientation = Orientation.Vertical,
        afterContentPadding = 0,
        mainAxisItemSpacing = 0,
        remeasureNeeded = false,
        coroutineScope = CoroutineScope(EmptyCoroutineContext),
        density = Density(1f),
        childConstraints = Constraints(),
    )

private const val NumberOfItemsToTeleport = 100

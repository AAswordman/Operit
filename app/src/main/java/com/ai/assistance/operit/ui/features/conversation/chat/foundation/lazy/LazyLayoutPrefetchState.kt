/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.annotation.IntRange
import androidx.annotation.VisibleForTesting
import androidx.collection.mutableScatterMapOf
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.internal.checkPrecondition
import androidx.compose.foundation.internal.requirePrecondition
import androidx.compose.foundation.internal.requirePreconditionNotNull
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutPrefetchState.PrefetchHandle
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutPrefetchState.PrefetchResultScope
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.trace
import androidx.compose.ui.util.traceValue
import kotlin.time.TimeSource.Monotonic.markNow

/**
 * State for lazy items prefetching, used by lazy layouts to instruct the prefetcher.
 *
 * @sample androidx.compose.foundation.samples.LazyLayoutPrefetchStateSample
 */
@Suppress("DEPRECATION") // b/420551535
@Stable
class LazyLayoutPrefetchState() {

    /**
     * State for lazy items prefetching, used by lazy layouts to instruct the prefetcher.
     *
     * @param prefetchScheduler the [PrefetchScheduler] implementation to use to execute prefetch
     *   requests. If null is provided, the default [PrefetchScheduler] for the platform will be
     *   used.
     * @param onNestedPrefetch a callback which will be invoked when this LazyLayout is prefetched
     *   in context of a parent LazyLayout, giving a chance to recursively prefetch its own
     *   children. See [NestedPrefetchScope].
     */
    @Deprecated("Please use overload without Prefetch Scheduler.")
    @ExperimentalFoundationApi
    constructor(
        prefetchScheduler: PrefetchScheduler? = null,
        onNestedPrefetch: (NestedPrefetchScope.() -> Unit)? = null,
    ) : this() {
        this.prefetchScheduler = prefetchScheduler
        this.onNestedPrefetch = onNestedPrefetch
    }

    /**
     * State for lazy items prefetching, used by lazy layouts to instruct the prefetcher.
     *
     * @param onNestedPrefetch a callback which will be invoked when this LazyLayout is prefetched
     *   in context of a parent LazyLayout, giving a chance to recursively prefetch its own
     *   children. See [NestedPrefetchScope].
     */
    @ExperimentalFoundationApi
    constructor(onNestedPrefetch: (NestedPrefetchScope.() -> Unit)? = null) : this() {
        this.onNestedPrefetch = onNestedPrefetch
    }

    @OptIn(ExperimentalFoundationApi::class)
    internal var prefetchScheduler: PrefetchScheduler? = null
    @OptIn(ExperimentalFoundationApi::class)
    private var onNestedPrefetch: (NestedPrefetchScope.() -> Unit)? = null

    private val prefetchMetrics: PrefetchMetrics = PrefetchMetrics()
    @OptIn(ExperimentalFoundationApi::class)
    internal var prefetchHandleProvider: PrefetchHandleProvider? = null

    /**
     * The nested prefetch count after collecting and averaging ideal counts for multiple lazy
     * layouts
     */
    internal var realizedNestedPrefetchCount: Int = UnspecifiedNestedPrefetchCount

    /**
     * The ideal nested prefetch count this Lazy Layout would like to have prefetched as part of
     * nested prefetching (e.g. number of visible items)
     */
    internal var idealNestedPrefetchCount = UnspecifiedNestedPrefetchCount

    /** The number of items that were nested prefetched in the most recent nested prefetch pass. */
    internal var lastNumberOfNestedPrefetchItems = 0

    /**
     * Schedules precomposition for the new item. If you also want to premeasure the item please use
     * [schedulePrecompositionAndPremeasure] instead. This function should only be called once per
     * item. If the item has already been composed at the time this request executes, either from a
     * previous call to this function or because the item is already visible, this request should
     * have no meaningful effect.
     *
     * @param index item index to prefetch.
     * @return A [PrefetchHandle] which can be used to control the lifecycle of the prefetch
     *   request. Use [PrefetchHandle.cancel] to cancel the request or [PrefetchHandle.markAsUrgent]
     *   to mark the request as urgent.
     */
    fun schedulePrecomposition(@IntRange(from = 0) index: Int): PrefetchHandle =
        schedulePrecomposition(index, true)

    /**
     * Internal implementation only. Schedules precomposition for the new item. If you also want to
     * premeasure the item please use [schedulePrecompositionAndPremeasure] instead. This function
     * should only be called once per item. If the item has already been composed at the time this
     * request executes, either from a previous call to this function or because the item is already
     * visible, this request should have no meaningful effect.
     *
     * @param index item index to prefetch.
     * @param isHighPriority If this request is high priority. High priority requests are executed
     *   in the order they're scheduled, but will take precedence over low priority requests.
     */
    @OptIn(ExperimentalFoundationApi::class)
    internal fun schedulePrecomposition(index: Int, isHighPriority: Boolean): PrefetchHandle {
        return prefetchHandleProvider?.schedulePrecomposition(
            index,
            isHighPriority,
            prefetchMetrics,
        ) ?: DummyHandle
    }

    /**
     * Schedules precomposition and premeasure for the new item. This should be used instead of
     * [schedulePrecomposition] if you also want to premeasure the item. This function should only
     * be called once per item. If the item has already been composed / measured at the time this
     * request executes, either from a previous call to this function or because the item is already
     * visible, this request should have no meaningful effect.
     *
     * @param index item index to prefetch.
     * @param constraints [Constraints] to use for premeasuring.
     * @param onItemPremeasured This callback is called when the item premeasuring is finished. If
     *   the request is canceled or no measuring is performed this callback won't be called. Use
     *   [PrefetchResultScope.getSize] to get the item's size.
     * @return A [PrefetchHandle] which can be used to control the lifecycle of the prefetch
     *   request. Use [PrefetchHandle.cancel] to cancel the request or [PrefetchHandle.markAsUrgent]
     *   to mark the request as urgent.
     */
    fun schedulePrecompositionAndPremeasure(
        @IntRange(from = 0) index: Int,
        constraints: Constraints,
        onItemPremeasured: (PrefetchResultScope.() -> Unit)? = null,
    ): PrefetchHandle =
        schedulePrecompositionAndPremeasure(index, constraints, true, onItemPremeasured)

    /**
     * Internal implementation only. Schedules precomposition and premeasure for the new item. This
     * should be used instead of [schedulePrecomposition] if you also want to premeasure the item.
     * This function should only be called once per item. If the item has already been composed /
     * measured at the time this request executes, either from a previous call to this function or
     * because the item is already visible, this request should have no meaningful effect.
     *
     * @param index item index to prefetch.
     * @param constraints [Constraints] to use for premeasuring.
     * @param isHighPriority If this request is high priority. High priority requests are executed
     *   in the order they're scheduled, but will take precedence over low priority requests.
     * @param onItemPremeasured This callback is called when the item premeasuring is finished. If
     *   the request is canceled or no measuring is performed this callback won't be called. Use
     *   [PrefetchResultScope.getSize] to get the item's size.
     */
    @OptIn(ExperimentalFoundationApi::class)
    internal fun schedulePrecompositionAndPremeasure(
        index: Int,
        constraints: Constraints,
        isHighPriority: Boolean,
        onItemPremeasured: (PrefetchResultScope.() -> Unit)? = null,
    ): PrefetchHandle {
        return prefetchHandleProvider?.schedulePremeasure(
            index,
            constraints,
            prefetchMetrics,
            isHighPriority,
            onItemPremeasured,
        ) ?: DummyHandle
    }

    @OptIn(ExperimentalFoundationApi::class)
    internal fun collectNestedPrefetchRequests(): List<PrefetchRequest> {
        val onNestedPrefetch = onNestedPrefetch ?: return emptyList()

        return NestedPrefetchScopeImpl(realizedNestedPrefetchCount)
            .run {
                onNestedPrefetch()
                requests
            }
            .also {
                // save the number of nested prefetch items we used
                lastNumberOfNestedPrefetchItems = it.size
            }
    }

    /** A handle to control some aspects of the prefetch request. */
    sealed interface PrefetchHandle {
        /**
         * Notifies the prefetcher that previously scheduled item is no longer needed. If the item
         * was precomposed already it will be disposed.
         */
        fun cancel()

        /**
         * Marks this prefetch request as urgent, which is a way to communicate that the requested
         * item is expected to be needed during the next frame.
         *
         * For urgent requests we can proceed with doing the prefetch even if the available time in
         * the frame is less than we spend on similar prefetch requests on average.
         */
        fun markAsUrgent()
    }

    /**
     * A scope for [schedulePrecompositionAndPremeasure] callbacks. The scope provides additional
     * information about a prefetched item.
     */
    sealed interface PrefetchResultScope {

        /** The amount of placeables composed into this item. */
        val placeablesCount: Int

        /** The index of the prefetched item. */
        val index: Int

        /** Retrieves the latest measured size for a given placeable [placeableIndex] in pixels. */
        fun getSize(@IntRange(from = 0) placeableIndex: Int): IntSize
    }

    @OptIn(ExperimentalFoundationApi::class)
    private inner class NestedPrefetchScopeImpl(override val nestedPrefetchItemCount: Int) :
        NestedPrefetchScope {

        val requests: List<PrefetchRequest>
            get() = _requests

        private val _requests: MutableList<PrefetchRequest> = mutableListOf()

        override fun schedulePrecomposition(index: Int) {
            val prefetchHandleProvider = prefetchHandleProvider ?: return
            _requests.add(
                prefetchHandleProvider.createNestedPrefetchRequest(index, prefetchMetrics)
            )
        }

        override fun schedulePrecompositionAndPremeasure(index: Int, constraints: Constraints) {
            val prefetchHandleProvider = prefetchHandleProvider ?: return
            _requests.add(
                prefetchHandleProvider.createNestedPrefetchRequest(
                    index,
                    constraints,
                    prefetchMetrics,
                )
            )
        }
    }
}

internal const val UnspecifiedNestedPrefetchCount = -1

/**
 * A scope which allows nested prefetches to be requested for the precomposition of a LazyLayout.
 */
@ExperimentalFoundationApi
sealed interface NestedPrefetchScope {

    /**
     * The projected number of nested items that should be prefetched during a Nested Prefetching of
     * an internal LazyLayout. This will return -1 if a projection isn't available yet. The parent
     * Lazy Layout will use information about an item's content type and number of visible items to
     * calculate the necessary number of items that a child layout will need to prefetch.
     */
    val nestedPrefetchItemCount: Int
        get() = UnspecifiedNestedPrefetchCount

    /**
     * Requests a child index to be prefetched as part of the prefetch of a parent LazyLayout.
     *
     * The prefetch will only do the precomposition for the new item. If you also want to premeasure
     * please use a second overload accepting a [Constraints] param.
     *
     * @param index item index to prefetch.
     */
    @Deprecated(
        "Please use schedulePrecomposition(index) instead",
        level = DeprecationLevel.WARNING,
    )
    fun schedulePrefetch(index: Int) = schedulePrecomposition(index)

    /**
     * Requests a child index to be precomposed as part of the prefetch of a parent LazyLayout.
     *
     * The prefetch will only do the precomposition for the new item. If you also want to premeasure
     * please use [schedulePrecompositionAndPremeasure].
     *
     * @param index item index to prefetch.
     */
    fun schedulePrecomposition(index: Int)

    /**
     * Requests a child index to be prefetched as part of the prefetch of a parent LazyLayout.
     *
     * @param index the index of the child to prefetch.
     * @param constraints [Constraints] to use for premeasuring.
     */
    @Deprecated(
        "Please use schedulePremeasure(index, constraints) instead",
        level = DeprecationLevel.WARNING,
    )
    fun schedulePrefetch(index: Int, constraints: Constraints) =
        schedulePrecompositionAndPremeasure(index, constraints)

    /**
     * Requests a child index to be precomposed and premeasured as part of the prefetch of a parent
     * LazyLayout. If you just want to precompose an item use [schedulePrecomposition] instead.
     *
     * @param index the index of the child to prefetch.
     * @param constraints [Constraints] to use for premeasuring.
     */
    fun schedulePrecompositionAndPremeasure(index: Int, constraints: Constraints)
}

/**
 * [PrefetchMetrics] tracks timings for subcompositions so that they can be used to estimate whether
 * we can fit prefetch work into idle time without delaying the start of the next frame.
 */
internal class PrefetchMetrics {

    /**
     * We keep the overall average numbers and averages for each content type separately. the idea
     * is once we encounter a new content type we don't want to start with no averages, instead we
     * use the overall averages initially until we collected more data.
     */
    fun getAverage(contentType: Any?): Averages {
        val lastUsedAverage = this@PrefetchMetrics.lastUsedAverage
        return if (lastUsedContentType === contentType && lastUsedAverage != null) {
            lastUsedAverage
        } else {
            averagesByContentType
                .getOrPut(contentType) { Averages() }
                .also {
                    this.lastUsedContentType = contentType
                    this.lastUsedAverage = it
                }
        }
    }

    private val averagesByContentType = mutableScatterMapOf<Any?, Averages>()

    private var lastUsedContentType: Any? = null
    private var lastUsedAverage: Averages? = null
}

internal class Averages {
    /** Average time the full composition phase has taken. */
    var compositionTimeNanos: Long = 0L
    /** Average time needed to resume the pausable composition until the next interruption. */
    var resumeTimeNanos: Long = 0L
    /** Average time needed to pause the pausable composition. */
    var pauseTimeNanos: Long = 0L
    /** Average time the apply phase has taken. */
    var applyTimeNanos: Long = 0L
    /** Average time the measure phase has taken. */
    var measureTimeNanos: Long = 0L
    /** Average number of nested prefetch items. */
    var nestedPrefetchCount: Int = UnspecifiedNestedPrefetchCount

    fun saveCompositionTimeNanos(timeNanos: Long) {
        compositionTimeNanos = calculateAverageTime(timeNanos, compositionTimeNanos)
    }

    fun saveResumeTimeNanos(timeNanos: Long) {
        resumeTimeNanos = calculateAverageTime(timeNanos, resumeTimeNanos)
    }

    fun savePauseTimeNanos(timeNanos: Long) {
        pauseTimeNanos = calculateAverageTime(timeNanos, pauseTimeNanos)
    }

    fun saveApplyTimeNanos(timeNanos: Long) {
        applyTimeNanos = calculateAverageTime(timeNanos, applyTimeNanos)
    }

    fun saveMeasureTimeNanos(timeNanos: Long) {
        measureTimeNanos = calculateAverageTime(timeNanos, measureTimeNanos)
    }

    fun saveNestedPrefetchCount(count: Int) {
        nestedPrefetchCount = calculateAverageCount(count, nestedPrefetchCount)
    }

    private fun calculateAverageTime(new: Long, current: Long): Long {
        // Calculate a weighted moving average of time taken to compose an item. We use weighted
        // moving average to bias toward more recent measurements, and to minimize storage /
        // computation cost. (the idea is taken from RecycledViewPool)
        return if (current == 0L) {
            new
        } else {
            // dividing first to avoid a potential overflow
            current / 4 * 3 + new / 4
        }
    }

    private fun calculateAverageCount(new: Int, current: Int): Int {
        return if (current == UnspecifiedNestedPrefetchCount) {
            new
        } else {
            (current * 3 + new) / 4
        }
    }

    fun clearMeasureTime() {
        measureTimeNanos = 0L
    }
}

private object DummyHandle : PrefetchHandle {
    override fun cancel() {}

    override fun markAsUrgent() {}
}

/**
 * PrefetchHandleProvider is used to connect the [LazyLayoutPrefetchState], which provides the API
 * to schedule prefetches, to a [LazyLayoutItemContentFactory] which resolves key and content from
 * an index, [SubcomposeLayoutState] which knows how to precompose/premeasure, and a specific
 * [PrefetchScheduler] used to execute a request.
 */
@Suppress("DEPRECATION") // b/420551535
@ExperimentalFoundationApi
internal class PrefetchHandleProvider(
    private val itemContentFactory: LazyLayoutItemContentFactory,
    private val subcomposeLayoutState: SubcomposeLayoutState,
    private val executor: PrefetchScheduler,
) {
    // cleared during onDisposed.
    private var isStateActive: Boolean = true

    // when true we will pause the request with "has more work to do" before doing premeasure
    // if we performed precomposed within the same execution.
    @VisibleForTesting internal var shouldPauseBetweenPrecompositionAndPremeasure = false

    fun schedulePrecomposition(
        index: Int,
        isHighPriority: Boolean,
        prefetchMetrics: PrefetchMetrics,
    ): PrefetchHandle =
        HandleAndRequestImpl(index, prefetchMetrics, executor as? PriorityPrefetchScheduler, null)
            .also {
                executor.executeWithPriority(it, isHighPriority)
                traceValue("compose:lazy:schedule_prefetch:index", index.toLong())
            }

    fun onDisposed() {
        isStateActive = false
    }

    fun schedulePremeasure(
        index: Int,
        constraints: Constraints,
        prefetchMetrics: PrefetchMetrics,
        isHighPriority: Boolean,
        onItemPremeasured: (PrefetchResultScope.() -> Unit)?,
    ): PrefetchHandle =
        HandleAndRequestImpl(
                index,
                constraints,
                prefetchMetrics,
                executor as? PriorityPrefetchScheduler,
                onItemPremeasured,
            )
            .also {
                executor.executeWithPriority(it, isHighPriority)
                traceValue("compose:lazy:schedule_prefetch:index", index.toLong())
            }

    fun PrefetchScheduler.executeWithPriority(request: PrefetchRequest, isHighPriority: Boolean) {
        if (this is PriorityPrefetchScheduler) {
            if (isHighPriority) {
                scheduleHighPriorityPrefetch(request)
            } else {
                scheduleLowPriorityPrefetch(request)
            }
        } else {
            schedulePrefetch(request)
        }
    }

    fun createNestedPrefetchRequest(
        index: Int,
        constraints: Constraints,
        prefetchMetrics: PrefetchMetrics,
    ): PrefetchRequest =
        HandleAndRequestImpl(
            index,
            constraints = constraints,
            prefetchMetrics,
            executor as? PriorityPrefetchScheduler,
            null,
        )

    fun createNestedPrefetchRequest(index: Int, prefetchMetrics: PrefetchMetrics): PrefetchRequest =
        HandleAndRequestImpl(index, prefetchMetrics, executor as? PriorityPrefetchScheduler, null)

    @ExperimentalFoundationApi
    private inner class HandleAndRequestImpl(
        override val index: Int,
        private val prefetchMetrics: PrefetchMetrics,
        private val priorityPrefetchScheduler: PriorityPrefetchScheduler?,
        private val onItemPremeasured: (PrefetchResultScope.() -> Unit)?,
    ) : PrefetchHandle, PrefetchRequest, PrefetchResultScope {

        constructor(
            index: Int,
            constraints: Constraints,
            prefetchMetrics: PrefetchMetrics,
            priorityPrefetchScheduler: PriorityPrefetchScheduler?,
            onItemPremeasured: (PrefetchResultScope.() -> Unit)?,
        ) : this(index, prefetchMetrics, priorityPrefetchScheduler, onItemPremeasured) {
            premeasureConstraints = constraints
        }

        private var premeasureConstraints: Constraints? = null
        private var precomposeHandle: SubcomposeLayoutState.PrecomposedSlotHandle? = null
        private var pausedPrecomposition: SubcomposeLayoutState.PausedPrecomposition? = null
        private var isMeasured = false
        private var isCanceled = false
        private var isApplied = false
        private var keyUsedForComposition: Any? = null

        private var hasResolvedNestedPrefetches = false
        private var nestedPrefetchController: NestedPrefetchController? = null
        private var isUrgent = false

        private val isComposed
            get() = isApplied || pausedPrecomposition?.isComplete == true

        override fun cancel() {
            if (!isCanceled) {
                isCanceled = true
                cleanUp()
            }
        }

        override fun markAsUrgent() {
            isUrgent = true
        }

        override val placeablesCount: Int
            get() = (precomposeHandle?.placeablesCount ?: 0)

        override fun getSize(placeableIndex: Int): IntSize {
            return (precomposeHandle?.getSize(placeableIndex) ?: IntSize.Zero)
        }

        private fun shouldExecute(available: Long, average: Long): Boolean {
            // Each step execution is prioritized as follows:
            // 1) If it is urgent, we always execute if we have time in the frame.
            // 2) In regular circumstances, we look at the average time this step took and execute
            // only if we have time.
            val required = if (isUrgent) 0 else average
            return available > required
        }

        private var availableTimeNanos = 0L
        private var elapsedTimeNanos = 0L
        private var startTime = markNow()

        private fun resetAvailableTimeTo(availableTimeNanos: Long) {
            this.availableTimeNanos = availableTimeNanos
            startTime = markNow()
            elapsedTimeNanos = 0L
            traceValue("compose:lazy:prefetch:available_time_nanos", availableTimeNanos)
        }

        private fun updateElapsedAndAvailableTime() {
            val now = markNow()
            elapsedTimeNanos = (now - startTime).inWholeNanoseconds
            availableTimeNanos -= elapsedTimeNanos
            startTime = now
            traceValue("compose:lazy:prefetch:available_time_nanos", availableTimeNanos)
        }

        override fun PrefetchRequestScope.execute(): Boolean {
            // check if the state that generated this request is still active.
            if (!isStateActive) return false
            return if (isUrgent) {
                    trace("compose:lazy:prefetch:execute:urgent") { executeRequest() }
                } else {
                    executeRequest()
                }
                .also {
                    // execution for this item finished, reset the trace value
                    traceValue("compose:lazy:prefetch:execute:item", -1)
                }
        }

        private fun cleanUp() {
            pausedPrecomposition?.cancel()
            pausedPrecomposition = null
            precomposeHandle?.dispose()
            precomposeHandle = null
            nestedPrefetchController = null
        }

        private fun PrefetchRequestScope.executeRequest(): Boolean {
            traceValue("compose:lazy:prefetch:execute:item", index.toLong())
            val itemProvider = itemContentFactory.itemProvider()
            val isValid = !isCanceled && index in 0 until itemProvider.itemCount
            if (!isValid) {
                cleanUp()
                return false
            }

            val key = itemProvider.getKey(index)
            if (keyUsedForComposition != null && key != keyUsedForComposition) {
                // key for the requested index changed, the request is now invalid
                cleanUp()
                return false
            }

            val contentType = itemProvider.getContentType(index)
            val average = prefetchMetrics.getAverage(contentType)
            val wasComposedAtStart = isComposed

            // we save the value we get from availableTimeNanos() into a local variable once
            // and manually update it later by calling updateElapsedAndAvailableTime()
            resetAvailableTimeTo(availableTimeNanos())
            if (!isComposed) {
                if (ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled) {
                    if (
                        shouldExecute(
                            availableTimeNanos,
                            average.resumeTimeNanos + average.pauseTimeNanos,
                        )
                    ) {
                        trace("compose:lazy:prefetch:compose") {
                            performPausableComposition(key, contentType, average)
                        }
                    }
                } else {
                    if (shouldExecute(availableTimeNanos, average.compositionTimeNanos)) {
                        trace("compose:lazy:prefetch:compose") {
                            performFullComposition(key, contentType)
                        }
                        updateElapsedAndAvailableTime()
                        average.saveCompositionTimeNanos(elapsedTimeNanos)
                    }
                }
                if (!isComposed) {
                    return true
                }
            }

            if (pausedPrecomposition != null) {
                if (shouldExecute(availableTimeNanos, average.applyTimeNanos)) {
                    trace("compose:lazy:prefetch:apply") { performApply() }
                    updateElapsedAndAvailableTime()
                    average.saveApplyTimeNanos(elapsedTimeNanos)
                } else {
                    return true
                }
            }

            // Nested prefetch logic is best-effort: if nested LazyLayout children are
            // added/removed/updated after we've resolved nested prefetch states here or
            // resolved nestedPrefetchRequests below, those changes won't be taken into account.
            if (!hasResolvedNestedPrefetches) {
                if (availableTimeNanos > 0) {
                    trace("compose:lazy:prefetch:resolve-nested") {
                        nestedPrefetchController = resolveNestedPrefetchStates()
                        hasResolvedNestedPrefetches = true
                    }
                } else {
                    return true
                }
            }
            val hasMoreWork =
                nestedPrefetchController?.run {
                    executeNestedPrefetches(average.nestedPrefetchCount, isUrgent)
                } ?: false
            if (hasMoreWork) {
                return true
            }

            // only update the time and traces if we actually executed a nested prefetch request
            if (nestedPrefetchController?.executedNestedPrefetch == true) {
                updateElapsedAndAvailableTime()
                // set the item value again since it will have changed in the nested block.
                traceValue("compose:lazy:prefetch:execute:item", index.toLong())
                // re-enable it next time we execute a nested prefetch request
                nestedPrefetchController?.executedNestedPrefetch = false
            }

            val constraints = premeasureConstraints
            if (!isMeasured && constraints != null) {
                if (shouldPauseBetweenPrecompositionAndPremeasure && !wasComposedAtStart) {
                    return true
                }
                if (shouldExecute(availableTimeNanos, average.measureTimeNanos)) {
                    trace("compose:lazy:prefetch:measure") { performMeasure(constraints) }
                    updateElapsedAndAvailableTime()
                    average.saveMeasureTimeNanos(elapsedTimeNanos)
                    onItemPremeasured?.invoke(this@HandleAndRequestImpl)
                } else {
                    return true
                }
            }

            // once we've measured this item we now have the up to date "ideal" number of
            // nested prefetches we'd like to perform, save that to the average.
            val controller = nestedPrefetchController
            if (isMeasured && hasResolvedNestedPrefetches && controller != null) {
                val idealNestedPrefetchCount = controller.collectIdealNestedPrefetchCount()
                average.saveNestedPrefetchCount(idealNestedPrefetchCount)
                val lastNumberOfNestedPrefetchItems = controller.collectNestedPrefetchedItemsCount()
                // if in the last pass we nested prefetched less items than we will in the next
                // pass,
                // this means our measure time for this item will be wrong, let's reset it and
                // collect it again the next time.
                if (lastNumberOfNestedPrefetchItems < idealNestedPrefetchCount) {
                    average.clearMeasureTime()
                }
            }

            // All our work is done.
            return false
        }

        private var pauseRequested = false

        private fun PrefetchRequestScope.performPausableComposition(
            key: Any,
            contentType: Any?,
            averages: Averages,
        ) {
            val composition =
                pausedPrecomposition
                    ?: run {
                        val content = itemContentFactory.getContent(index, key, contentType)
                        subcomposeLayoutState.createPausedPrecomposition(key, content).also {
                            pausedPrecomposition = it
                            keyUsedForComposition = key
                        }
                    }

            pauseRequested = false

            while (!composition.isComplete && !pauseRequested) {
                composition.resume {
                    if (!pauseRequested) {
                        updateElapsedAndAvailableTime()
                        averages.saveResumeTimeNanos(elapsedTimeNanos)
                        pauseRequested =
                            !shouldExecute(
                                availableTimeNanos,
                                averages.resumeTimeNanos + averages.pauseTimeNanos,
                            )
                    }
                    pauseRequested
                }
            }

            updateElapsedAndAvailableTime()
            if (pauseRequested) {
                averages.savePauseTimeNanos(elapsedTimeNanos)
            } else {
                averages.saveResumeTimeNanos(elapsedTimeNanos)
            }
        }

        private fun performFullComposition(key: Any, contentType: Any?) {
            requirePrecondition(precomposeHandle == null) { "Request was already composed!" }
            val content = itemContentFactory.getContent(index, key, contentType)
            keyUsedForComposition = key
            precomposeHandle = subcomposeLayoutState.precompose(key, content)
            isApplied = true
        }

        private fun performApply() {
            val precomposition = requireNotNull(pausedPrecomposition) { "Nothing to apply!" }
            precomposeHandle = precomposition.apply()
            pausedPrecomposition = null
            isApplied = true
        }

        private fun performMeasure(constraints: Constraints) {
            requirePrecondition(!isCanceled) {
                "Callers should check whether the request is still valid before calling " +
                    "performMeasure()"
            }
            requirePrecondition(!isMeasured) { "Request was already measured!" }
            isMeasured = true
            val handle =
                requirePreconditionNotNull(precomposeHandle) {
                    "performComposition() must be called before performMeasure()"
                }
            repeat(handle.placeablesCount) { placeableIndex ->
                handle.premeasure(placeableIndex, constraints)
            }
        }

        private fun resolveNestedPrefetchStates(): NestedPrefetchController? {
            val precomposedSlotHandle =
                requirePreconditionNotNull(precomposeHandle) {
                    "Should precompose before resolving nested prefetch states"
                }

            var nestedStates: MutableList<LazyLayoutPrefetchState>? = null
            precomposedSlotHandle.traverseDescendants(TraversablePrefetchStateNodeKey) {
                val prefetchState = (it as TraversablePrefetchStateNode).prefetchState
                nestedStates =
                    nestedStates?.apply { add(prefetchState) } ?: mutableListOf(prefetchState)
                TraverseDescendantsAction.SkipSubtreeAndContinueTraversal
            }
            return nestedStates?.let { NestedPrefetchController(it) }
        }

        override fun toString(): String =
            "HandleAndRequestImpl { index = $index, constraints = $premeasureConstraints, " +
                "isComposed = $isComposed, isMeasured = $isMeasured, isCanceled = $isCanceled }"

        private inner class NestedPrefetchController(
            private val states: List<LazyLayoutPrefetchState>
        ) {

            // This array is parallel to nestedPrefetchStates, so index 0 in nestedPrefetchStates
            // corresponds to index 0 in this array, etc.
            private val requestsByState: Array<List<PrefetchRequest>?> = arrayOfNulls(states.size)
            private var stateIndex: Int = 0
            private var requestIndex: Int = 0
            var executedNestedPrefetch: Boolean = false

            init {
                requirePrecondition(states.isNotEmpty()) {
                    "NestedPrefetchController shouldn't be created with no states"
                }
            }

            fun PrefetchRequestScope.executeNestedPrefetches(
                nestedPrefetchCount: Int,
                isUrgent: Boolean,
            ): Boolean {
                if (stateIndex >= states.size) {
                    return false
                }
                checkPrecondition(!isCanceled) {
                    "Should not execute nested prefetch on canceled request"
                }

                // If we have automatic nested prefetch enabled, it means we can update the
                // nested prefetch count for some of the layouts in this item.
                trace("compose:lazy:prefetch:update_nested_prefetch_count") {
                    states.fastForEach { it.realizedNestedPrefetchCount = nestedPrefetchCount }
                }
                trace("compose:lazy:prefetch:nested") {
                    while (stateIndex < states.size) {
                        if (requestsByState[stateIndex] == null) {
                            if (availableTimeNanos() <= 0) {
                                // When we have time again, we'll resolve nested requests for this
                                // state
                                return true
                            }

                            requestsByState[stateIndex] =
                                states[stateIndex].collectNestedPrefetchRequests()
                        }

                        val nestedRequests = requestsByState[stateIndex]!!
                        while (requestIndex < nestedRequests.size) {
                            val hasMoreWork =
                                with(nestedRequests[requestIndex]) {
                                    // mark this nested request as urgent, because its parent
                                    // request is
                                    // urgent
                                    if (isUrgent) {
                                        (this as? HandleAndRequestImpl)?.markAsUrgent()
                                    }
                                    executedNestedPrefetch = true
                                    execute()
                                }
                            if (hasMoreWork) {
                                return true
                            } else {
                                requestIndex++
                            }
                        }

                        requestIndex = 0
                        stateIndex++
                    }
                }

                return false
            }

            fun collectIdealNestedPrefetchCount(): Int {
                var count = Int.MAX_VALUE
                states.fastForEach {
                    // use the minimum ideal counts provided by all nested layouts in this item.
                    count = minOf(count, it.idealNestedPrefetchCount)
                }
                return if (count == Int.MAX_VALUE) 0 else count
            }

            fun collectNestedPrefetchedItemsCount(): Int {
                var count = Int.MAX_VALUE
                states.fastForEach {
                    // use the minimum ideal counts provided by all nested layouts in this item.
                    count = minOf(count, it.lastNumberOfNestedPrefetchItems)
                }
                return if (count == Int.MAX_VALUE) 0 else count
            }
        }
    }
}

private const val TraversablePrefetchStateNodeKey =
    "androidx.compose.foundation.lazy.layout.TraversablePrefetchStateNode"

/**
 * A modifier which lets the [LazyLayoutPrefetchState] for a [LazyLayout] to be discoverable via
 * [TraversableNode] traversal.
 */
@ExperimentalFoundationApi
internal fun Modifier.traversablePrefetchState(
    lazyLayoutPrefetchState: LazyLayoutPrefetchState?
): Modifier {
    return lazyLayoutPrefetchState?.let { this then TraversablePrefetchStateModifierElement(it) }
        ?: this
}

@ExperimentalFoundationApi
private class TraversablePrefetchStateNode(var prefetchState: LazyLayoutPrefetchState) :
    Modifier.Node(), TraversableNode {

    override val traverseKey: String = TraversablePrefetchStateNodeKey
}

@ExperimentalFoundationApi
private data class TraversablePrefetchStateModifierElement(
    private val prefetchState: LazyLayoutPrefetchState
) : ModifierNodeElement<TraversablePrefetchStateNode>() {
    override fun create() = TraversablePrefetchStateNode(prefetchState)

    override fun update(node: TraversablePrefetchStateNode) {
        node.prefetchState = prefetchState
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "traversablePrefetchState"
        value = prefetchState
    }
}

private val ZeroConstraints = Constraints(maxWidth = 0, maxHeight = 0)

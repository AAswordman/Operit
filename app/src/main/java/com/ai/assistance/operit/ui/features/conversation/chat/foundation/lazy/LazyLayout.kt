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

import androidx.collection.mutableObjectIntMapOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.SubcomposeSlotReusePolicy
import androidx.compose.ui.unit.Constraints

/**
 * A layout that only composes and lays out currently needed items. Can be used to build efficient
 * complex layouts. Currently needed items depend on the LazyLayout implementation, that is, on how
 * the [LazyLayoutMeasurePolicy] is implemented. Composing items during the measure pass is the
 * signal to indicate which items are "currently needed". In general, only visible items are
 * considered needed, but additional items may be requested by calling
 * [LazyLayoutMeasureScope.compose].
 *
 * This is a low level API for building efficient complex layouts, for a ready-to-use linearly
 * scrollable lazy layout implementation see [androidx.compose.foundation.lazy.LazyColumn] and
 * [androidx.compose.foundation.lazy.LazyRow]. For a grid-like scrollable lazy layout, see
 * [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] and
 * [androidx.compose.foundation.lazy.grid.LazyHorizontalGrid]. For a pager-like lazy layout, see
 * [androidx.compose.foundation.pager.VerticalPager] and
 * [androidx.compose.foundation.pager.HorizontalPager]
 *
 * For a basic lazy layout sample, see:
 *
 * @sample androidx.compose.foundation.samples.LazyLayoutSample
 *
 * For a scrollable lazy layout, see:
 *
 * @sample androidx.compose.foundation.samples.LazyLayoutScrollableSample
 * @param itemProvider lambda producing an item provider containing all the needed info about the
 *   items which could be used to compose and measure items as part of [measurePolicy].
 * @param modifier to apply on the layout
 * @param prefetchState allows to schedule items for prefetching
 * @param measurePolicy Measure policy which allows to only compose and measure needed items.
 */
@Deprecated("Please use overload with LazyLayoutMeasurePolicy", level = DeprecationLevel.HIDDEN)
@ExperimentalFoundationApi
@Composable
fun LazyLayout(
    itemProvider: () -> LazyLayoutItemProvider,
    modifier: Modifier = Modifier,
    prefetchState: LazyLayoutPrefetchState? = null,
    measurePolicy: LazyLayoutMeasureScope.(Constraints) -> MeasureResult,
) = LazyLayout(itemProvider, modifier, prefetchState, LazyLayoutMeasurePolicy(measurePolicy))

/**
 * A layout that only composes and lays out currently needed items. Can be used to build efficient
 * complex layouts. Currently needed items depend on the LazyLayout implementation, that is, on how
 * the [LazyLayoutMeasurePolicy] is implemented. Composing items during the measure pass is the
 * signal to indicate which items are "currently needed". In general, only visible items are
 * considered needed, but additional items may be requested by calling
 * [LazyLayoutMeasureScope.compose].
 *
 * This is a low level API for building efficient complex layouts, for a ready-to-use linearly
 * scrollable lazy layout implementation see [androidx.compose.foundation.lazy.LazyColumn] and
 * [androidx.compose.foundation.lazy.LazyRow]. For a grid-like scrollable lazy layout, see
 * [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] and
 * [androidx.compose.foundation.lazy.grid.LazyHorizontalGrid]. For a pager-like lazy layout, see
 * [androidx.compose.foundation.pager.VerticalPager] and
 * [androidx.compose.foundation.pager.HorizontalPager]
 *
 * For a basic lazy layout sample, see:
 *
 * @sample androidx.compose.foundation.samples.LazyLayoutSample
 *
 * For a scrollable lazy layout, see:
 *
 * @sample androidx.compose.foundation.samples.LazyLayoutScrollableSample
 * @param itemProvider lambda producing an item provider containing all the needed info about the
 *   items which could be used to compose and measure items as part of [measurePolicy]. This is the
 *   bridge between your item data source and the LazyLayout and is implemented as a lambda to
 *   promote a performant implementation. State backed implementations of [LazyLayoutItemProvider]
 *   are supported, though it is encouraged to implement this as an immutable entity that will
 *   return a new instance in case the dataset updates.
 * @param modifier to apply on the layout
 * @param prefetchState allows to schedule items for prefetching. See [LazyLayoutPrefetchState] on
 *   how to control prefetching. Passing null will disable prefetching.
 * @param measurePolicy Measure policy which allows to only compose and measure needed items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyLayout(
    itemProvider: () -> LazyLayoutItemProvider,
    modifier: Modifier = Modifier,
    prefetchState: LazyLayoutPrefetchState? = null,
    measurePolicy: LazyLayoutMeasurePolicy,
) {
    val currentItemProvider = rememberUpdatedState(itemProvider)

    LazySaveableStateHolderProvider { saveableStateHolder ->
        val itemContentFactory = remember {
            LazyLayoutItemContentFactory(saveableStateHolder) { currentItemProvider.value() }
        }
        val subcomposeLayoutState = remember {
            SubcomposeLayoutState(LazyLayoutItemReusePolicy(itemContentFactory))
        }
        if (prefetchState != null) {
            val executor = prefetchState.prefetchScheduler ?: rememberDefaultPrefetchScheduler()
            DisposableEffect(prefetchState, itemContentFactory, subcomposeLayoutState, executor) {
                prefetchState.prefetchHandleProvider =
                    PrefetchHandleProvider(itemContentFactory, subcomposeLayoutState, executor)
                onDispose {
                    // clean up prefetch handle provider
                    prefetchState.prefetchHandleProvider?.onDisposed()
                    prefetchState.prefetchHandleProvider = null
                }
            }
        }

        SubcomposeLayout(
            subcomposeLayoutState,
            modifier.traversablePrefetchState(prefetchState),
            remember(itemContentFactory, measurePolicy) {
                { constraints ->
                    val scope = LazyLayoutMeasureScopeImpl(itemContentFactory, this)
                    with(measurePolicy) { scope.measure(constraints) }
                }
            },
        )
    }
}

private class LazyLayoutItemReusePolicy(private val factory: LazyLayoutItemContentFactory) :
    SubcomposeSlotReusePolicy {
    private val countPerType = mutableObjectIntMapOf<Any?>()

    override fun getSlotsToRetain(slotIds: SubcomposeSlotReusePolicy.SlotIdsSet) {
        countPerType.clear()
        slotIds.fastForEach { slotId ->
            val type = factory.getContentType(slotId)
            val currentCount = countPerType.getOrDefault(type, 0)
            if (currentCount == MaxItemsToRetainForReuse) {
                slotIds.remove(slotId)
            } else {
                countPerType[type] = currentCount + 1
            }
        }
    }

    override fun areCompatible(slotId: Any?, reusableSlotId: Any?): Boolean =
        factory.getContentType(slotId) == factory.getContentType(reusableSlotId)
}

/**
 * We currently use the same number of items to reuse (recycle) items as RecyclerView does: 5
 * (RecycledViewPool.DEFAULT_MAX_SCRAP) + 2 (Recycler.DEFAULT_CACHE_SIZE)
 */
private const val MaxItemsToRetainForReuse = 7

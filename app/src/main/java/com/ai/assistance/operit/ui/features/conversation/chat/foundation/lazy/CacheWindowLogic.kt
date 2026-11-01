/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.collection.mutableIntObjectMapOf
import androidx.collection.mutableIntSetOf
import androidx.compose.foundation.ExperimentalFoundationApi
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutPrefetchState.PrefetchHandle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.traceValue
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

/** Implements the logic for [LazyLayoutCacheWindow] prefetching and item preservation. */
@OptIn(ExperimentalFoundationApi::class)
internal abstract class CacheWindowLogic(
    private val cacheWindow: LazyLayoutCacheWindow,
    private val enableInitialPrefetch: Boolean = true,
) {

    /** Handles for prefetched items in the current forward window. */
    private val prefetchWindowHandles = mutableIntObjectMapOf<List<PrefetchHandle>>()

    private val indicesToRemove = mutableIntSetOf()
    /**
     * Cache for items sizes in the current window. Holds sizes for both visible and non-visible
     * items
     */
    private val windowCache = mutableIntObjectMapOf<CachedItem>()
    private var previousPassDelta = 0f
    private var previousPassItemCount = UnsetItemCount
    private var hasUpdatedVisibleItemsOnce = false

    /**
     * Indices for the start and end of the cache window. The items between
     * [prefetchWindowStartLine] and [prefetchWindowEndLine] can be:
     * 1) Visible.
     * 2) Cached.
     * 3) Scheduled for prefetching.
     * 4) Not scheduled yet.
     */
    internal var prefetchWindowStartLine = Int.MAX_VALUE
        private set

    internal var prefetchWindowEndLine = Int.MIN_VALUE
        private set

    /**
     * Keeps track of the "extra" space used. Extra space starts by being the amount of space
     * occupied by the first and last visible items outside of the viewport, that is, how much
     * they're "peeking" out of view. These values will be updated as we fill the cache window.
     */
    private var prefetchWindowStartExtraSpace = 0
    private var prefetchWindowEndExtraSpace = 0

    /**
     * Signals that we should run the window refilling loop from start. This might re-trigger a
     * prefetch in case the window is not filled with item information. There are 3 conditions in
     * which window refilling will happen:
     * 1) After the first layout pass
     * 2) If any of the visible items were resized since the last measure pass.
     * 3) If the total number of items changed since the last measure pass.
     */
    private var shouldRefillWindow = false

    /** Keep the latest item count where it can be used more easily. */
    private var itemsCount = 0

    fun CacheWindowScope.onScroll(delta: Float) {
        debugLog { "delta=$delta" }
        traceWindowInfo()
        fillCacheWindowBackward(delta)
        fillCacheWindowForward(delta)
        previousPassDelta = delta
        traceWindowInfo()
        debugLog {
            "prefetchWindowStartExtraSpace=$prefetchWindowStartExtraSpace\n" +
                "prefetchWindowEndExtraSpace=$prefetchWindowEndExtraSpace\n" +
                "prefetchWindowStartIndex=$prefetchWindowStartLine\n" +
                "prefetchWindowEndIndex=$prefetchWindowEndLine"
        }
    }

    private fun traceWindowInfo() {
        traceValue("prefetchWindowStartExtraSpace", prefetchWindowStartExtraSpace.toLong())
        traceValue("prefetchWindowEndExtraSpace", prefetchWindowEndExtraSpace.toLong())
        traceValue("prefetchWindowStartIndex", prefetchWindowStartLine.toLong())
        traceValue("prefetchWindowEndIndex", prefetchWindowEndLine.toLong())
    }

    fun CacheWindowScope.onVisibleItemsUpdated() {
        debugLog { "hasUpdatedVisibleItemsOnce=$hasUpdatedVisibleItemsOnce" }
        if (!hasUpdatedVisibleItemsOnce && enableInitialPrefetch) {
            val prefetchForwardWindow =
                with(cacheWindow) { density?.calculateAheadWindow(mainAxisViewportSize) ?: 0 }
            // we won't fill the window if we don't have a prefetch window
            if (prefetchForwardWindow != 0) shouldRefillWindow = true
            hasUpdatedVisibleItemsOnce = true
        }

        /**
         * We already have information about the number of items from before and it actually
         * changed.
         */
        if (previousPassItemCount != UnsetItemCount && previousPassItemCount != totalItemsCount) {
            onDatasetChangedSize()
        }

        itemsCount = totalItemsCount
        // If visible items changed, update cached information. Any items that were visible
        // and became out of bounds will either count for the cache window or be cancelled/removed
        // by [cancelOutOfBounds]. If any items changed sizes we re-trigger the window filling
        // update.
        if (hasVisibleItems) {
            forEachVisibleItem { index, key, mainAxisSize ->
                if (index != InvalidIndex) cacheVisibleItemsInfo(index, key, mainAxisSize)
            }
            if (shouldRefillWindow) {
                // refill window in accordance with last pass delta
                debugLog { "Refill Window Forward=${previousPassDelta <= 0.0f}" }
                refillWindow(previousPassDelta <= 0.0f)
                shouldRefillWindow = false
            }
        } else {
            // if no visible items, it means the dataset is empty and we should reset the window.
            // Next time visible items update we we re-start the window strategy.
            resetStrategy()
        }

        previousPassItemCount = totalItemsCount
    }

    fun hasValidBounds() =
        prefetchWindowStartLine != Int.MAX_VALUE && prefetchWindowEndLine != Int.MIN_VALUE

    private fun CacheWindowScope.fillCacheWindowBackward(delta: Float) {
        if (hasVisibleItems) {
            val viewport = mainAxisViewportSize

            val keepAroundWindow =
                with(cacheWindow) { density?.calculateBehindWindow(viewport) ?: 0 }

            // save latest item count
            itemsCount = totalItemsCount

            debugLog {
                "fillCacheWindowBackward visibleWindowStart=$firstVisibleLineIndex \n" +
                    "visibleWindowEnd=$lastVisibleLineIndex \n" +
                    "keepAroundWindow=$keepAroundWindow \n" +
                    "mainAxisExtraSpaceStart=$mainAxisExtraSpaceStart \n" +
                    "mainAxisExtraSpaceEnd=$mainAxisExtraSpaceEnd \n"
            }

            onKeepAround(
                visibleWindowStart = firstVisibleLineIndex,
                visibleWindowEnd = lastVisibleLineIndex,
                keepAroundWindow = keepAroundWindow,
                scrollDelta = delta,
                itemsCount = totalItemsCount,
                mainAxisExtraSpaceStart = mainAxisExtraSpaceStart,
                mainAxisExtraSpaceEnd = mainAxisExtraSpaceEnd,
            )
        }
    }

    private fun CacheWindowScope.fillCacheWindowForward(delta: Float) {
        if (hasVisibleItems) {
            val viewport = mainAxisViewportSize

            val prefetchForwardWindow =
                with(cacheWindow) { density?.calculateAheadWindow(viewport) ?: 0 }

            debugLog {
                "fillCacheWindowForward visibleWindowStart=$firstVisibleLineIndex \n" +
                    "visibleWindowEnd=$lastVisibleLineIndex \n" +
                    "prefetchForwardWindow=$prefetchForwardWindow \n" +
                    "mainAxisExtraSpaceStart=$mainAxisExtraSpaceStart \n" +
                    "mainAxisExtraSpaceEnd=$mainAxisExtraSpaceEnd \n"
            }

            onPrefetchForward(
                visibleWindowStart = firstVisibleLineIndex,
                visibleWindowEnd = lastVisibleLineIndex,
                prefetchForwardWindow = prefetchForwardWindow,
                scrollDelta = delta,
                mainAxisExtraSpaceStart = mainAxisExtraSpaceStart,
                mainAxisExtraSpaceEnd = mainAxisExtraSpaceEnd,
                applyForwardPrefetch = delta <= 0.0f,
            )
        }
    }

    private fun CacheWindowScope.refillWindow(refillForward: Boolean) {
        if (hasVisibleItems) {
            val viewport = mainAxisViewportSize

            val prefetchForwardWindow =
                with(cacheWindow) { density?.calculateAheadWindow(viewport) ?: 0 }

            onPrefetchForward(
                visibleWindowStart = firstVisibleLineIndex,
                visibleWindowEnd = lastVisibleLineIndex,
                prefetchForwardWindow = prefetchForwardWindow,
                scrollDelta = 0.0f,
                mainAxisExtraSpaceStart = mainAxisExtraSpaceStart,
                mainAxisExtraSpaceEnd = mainAxisExtraSpaceEnd,
                applyForwardPrefetch = refillForward,
            )
        }
    }

    private fun CacheWindowScope.onDatasetChangedSize() {
        debugLog { "Total Items Changed" }
        shouldRefillWindow = true
        prefetchWindowStartLine = prefetchWindowStartLine.coerceAtLeast(0)
        val lastLineIndex = getLastLineIndex()
        if (lastLineIndex != InvalidIndex) {
            prefetchWindowEndLine = prefetchWindowEndLine.coerceAtMost(lastLineIndex)
        }

        /**
         * Resets the window state. We will refill the window on the direction of the last scroll.
         */
        if (previousPassDelta <= 0f) {
            removeOutOfBoundsItems(lastVisibleLineIndex, itemsCount - 1)
        } else {
            removeOutOfBoundsItems(0, firstVisibleLineIndex)
        }
    }

    fun resetStrategy() {
        prefetchWindowStartLine = Int.MAX_VALUE
        prefetchWindowEndLine = Int.MIN_VALUE
        prefetchWindowStartExtraSpace = 0
        prefetchWindowEndExtraSpace = 0
        shouldRefillWindow = false

        windowCache.clear()
        prefetchWindowHandles.removeIf { _, value ->
            value.fastForEach { it.cancel() }
            true
        }
    }

    /**
     * Prefetch Forward Logic: Fill in the forward window with prefetched items from the previous
     * measure pass. If the item is not prefetched yet, schedule a prefetching for it. Once a
     * prefetch returns, we check if the window is filled and if not we schedule the next
     * prefetching.
     */
    private fun CacheWindowScope.onPrefetchForward(
        visibleWindowStart: Int,
        visibleWindowEnd: Int,
        prefetchForwardWindow: Int,
        mainAxisExtraSpaceEnd: Int,
        mainAxisExtraSpaceStart: Int,
        scrollDelta: Float,
        applyForwardPrefetch: Boolean,
    ) {
        val changedScrollDirection = scrollDelta.sign != previousPassDelta.sign

        if (applyForwardPrefetch) { // scrolling forward, starting on last visible
            if (changedScrollDirection || shouldRefillWindow) {
                prefetchWindowEndExtraSpace = (prefetchForwardWindow - mainAxisExtraSpaceEnd)
                prefetchWindowEndLine = visibleWindowEnd
            } else {
                prefetchWindowEndExtraSpace =
                    (prefetchWindowEndExtraSpace + scrollDelta.absoluteValue.roundToInt())
                        .coerceAtMost(prefetchForwardWindow - mainAxisExtraSpaceEnd)
            }

            while (
                prefetchWindowEndExtraSpace > 0 &&
                    getLastIndexInLine(prefetchWindowEndLine) != InvalidIndex &&
                    getLastIndexInLine(prefetchWindowEndLine) < itemsCount - 1
            ) {
                // If we get the same delta in the next frame, would we cover the extra space needed
                // to actually need this item? If so, mark it as urgent
                val isUrgent: Boolean =
                    if (prefetchWindowEndLine + 1 == visibleWindowEnd + 1 && scrollDelta != 0.0f) {
                        scrollDelta.absoluteValue >= mainAxisExtraSpaceEnd
                    } else {
                        false
                    }

                debugLog {
                    "getItemSizeOrPrefetch item=${prefetchWindowEndLine + 1} isUrgent=$isUrgent"
                }
                // no more items available to fill prefetch window if this is null, break
                val itemSize =
                    getItemSizeOrPrefetch(index = prefetchWindowEndLine + 1, isUrgent = isUrgent)

                if (itemSize == InvalidItemSize) break

                prefetchWindowEndLine++
                prefetchWindowEndExtraSpace -= itemSize
            }
        } else { // scrolling backwards, starting on first visible
            if (changedScrollDirection || shouldRefillWindow) {
                prefetchWindowStartExtraSpace = (prefetchForwardWindow - mainAxisExtraSpaceStart)
                prefetchWindowStartLine = visibleWindowStart
            } else {
                prefetchWindowStartExtraSpace =
                    (prefetchWindowStartExtraSpace + scrollDelta.absoluteValue.roundToInt())
                        .coerceAtMost(prefetchForwardWindow - mainAxisExtraSpaceStart)
            }

            while (prefetchWindowStartExtraSpace > 0 && prefetchWindowStartLine > 0) {
                // If we get the same delta in the next frame, would we cover the extra space needed
                // to actually need this item? If so, mark it as urgent
                val isUrgent: Boolean =
                    if (
                        prefetchWindowStartLine - 1 == visibleWindowStart - 1 && scrollDelta != 0.0f
                    ) {
                        scrollDelta.absoluteValue >= mainAxisExtraSpaceStart
                    } else {
                        false
                    }

                debugLog {
                    "getItemSizeOrPrefetch item=${prefetchWindowEndLine + 1} isUrgent=$isUrgent"
                }

                // no more items available to fill prefetch window if this is null, break
                val itemSize =
                    getItemSizeOrPrefetch(index = prefetchWindowStartLine - 1, isUrgent = isUrgent)
                if (itemSize == InvalidItemSize) break
                prefetchWindowStartLine--
                prefetchWindowStartExtraSpace -= itemSize
            }
        }
    }

    /**
     * Keep Around Logic: Keep around items were visible in the previous measure pass. This means
     * that they will be present in [windowCache] along their size information. We loop through
     * items starting in the last visible one and update [prefetchWindowStartExtraSpace] or
     * [prefetchWindowEndExtraSpace] and also [prefetchWindowStartLine] or [prefetchWindowEndLine].
     * We never schedule a prefetch call for keep around items.
     */
    private fun onKeepAround(
        visibleWindowStart: Int,
        visibleWindowEnd: Int,
        mainAxisExtraSpaceEnd: Int,
        mainAxisExtraSpaceStart: Int,
        keepAroundWindow: Int,
        scrollDelta: Float,
        itemsCount: Int,
    ) {

        if (scrollDelta <= 0.0f) { // scrolling forward, keep around from firstVisible
            prefetchWindowStartExtraSpace = (keepAroundWindow - mainAxisExtraSpaceStart)
            prefetchWindowStartLine = visibleWindowStart
            while (prefetchWindowStartExtraSpace > 0 && prefetchWindowStartLine > 0) {
                val item =
                    if (windowCache.containsKey(prefetchWindowStartLine - 1)) {
                        windowCache[prefetchWindowStartLine - 1]!!.mainAxisSize
                    } else {
                        break
                    }

                prefetchWindowStartLine--
                prefetchWindowStartExtraSpace -= item
            }
            removeOutOfBoundsItems(0, prefetchWindowStartLine - 1)
        } else { // scrolling backwards, keep around from last visible
            prefetchWindowEndExtraSpace = (keepAroundWindow - mainAxisExtraSpaceEnd)
            prefetchWindowEndLine = visibleWindowEnd
            while (prefetchWindowEndExtraSpace > 0 && prefetchWindowEndLine < itemsCount - 1) {
                val item =
                    if (windowCache.containsKey(prefetchWindowEndLine + 1)) {
                        windowCache[prefetchWindowEndLine + 1]!!.mainAxisSize
                    } else {
                        break
                    }
                prefetchWindowEndLine++
                prefetchWindowEndExtraSpace -= item
            }
            removeOutOfBoundsItems(prefetchWindowEndLine + 1, itemsCount - 1)
        }
    }

    private fun CacheWindowScope.getItemSizeOrPrefetch(index: Int, isUrgent: Boolean): Int {
        return if (windowCache.containsKey(index)) {
            debugLog { "Item $index is Cached!" }
            windowCache[index]!!.mainAxisSize
        } else if (prefetchWindowHandles.containsKey(index)) {
            // item is scheduled but didn't finish yet
            debugLog { "Item=$index is already scheduled. isUrgent=$isUrgent" }
            if (isUrgent) prefetchWindowHandles[index]?.fastForEach { it.markAsUrgent() }
            InvalidItemSize
        } else {
            // item is not scheduled
            debugLog { "Scheduling Prefetching for Item=$index. isUrgent=$isUrgent" }
            prefetchWindowHandles[index] =
                schedulePrefetch(index) { prefetchedIndex, size ->
                    onItemPrefetched(prefetchedIndex, size)
                }
            if (isUrgent) prefetchWindowHandles[index]?.fastForEach { it.markAsUrgent() }
            InvalidItemSize
        }
    }

    /** Grows the window with measured items and prefetched items. */
    private fun cachePrefetchedItem(index: Int, size: Int) {
        windowCache[index] = updateOrCreateCachedItem(index, size, CachedItem.NoKey)
        if (index > prefetchWindowEndLine) {
            prefetchWindowEndLine = index
            prefetchWindowEndExtraSpace -= size
        } else if (index < prefetchWindowStartLine) {
            prefetchWindowStartLine = index
            prefetchWindowStartExtraSpace -= size
        }
    }

    private fun updateOrCreateCachedItem(index: Int, size: Int, key: Any): CachedItem {
        val cachedItem = windowCache[index]
        return if (cachedItem != null) {
            cachedItem.mainAxisSize = size
            cachedItem.key = key
            cachedItem
        } else {
            CachedItem(CachedItem.NoKey, size)
        }
    }

    /**
     * When caching visible items we need to check if the existing item changed sizes. If so, we
     * will set [shouldRefillWindow] which will trigger a complete window filling and cancel any out
     * of bounds requests. The same is valid if items are replaced (have the same size by key
     * changed).
     */
    private fun cacheVisibleItemsInfo(index: Int, key: Any, size: Int) {
        debugLog { "cacheVisibleItemsInfo item=$index size=$size key=$key" }
        if (windowCache.containsKey(index)) {
            val cachedSize = windowCache[index]!!.mainAxisSize
            val cachedKey = windowCache[index]!!.key
            if (cachedSize != size || cachedKey != key) {
                shouldRefillWindow = true
            }
        }

        windowCache[index] = updateOrCreateCachedItem(index, size, key)
        // We're caching a visible item, remove its handle since we won't need it anymore.
        prefetchWindowStartLine = minOf(prefetchWindowStartLine, index)
        prefetchWindowEndLine = maxOf(prefetchWindowEndLine, index)
        prefetchWindowHandles.remove(index)?.fastForEach { it.cancel() }
    }

    /** Takes care of removing caches and canceling handles for items that we won't use anymore. */
    private fun removeOutOfBoundsItems(startLine: Int, endLine: Int) {
        indicesToRemove.clear()
        prefetchWindowHandles.forEachKey { if (it in startLine..endLine) indicesToRemove.add(it) }

        windowCache.forEachKey { if (it in startLine..endLine) indicesToRemove.add(it) }

        debugLog { "Indices to remove=$indicesToRemove" }

        indicesToRemove.forEach {
            prefetchWindowHandles.remove(it)?.fastForEach { it.cancel() }
            windowCache.remove(it)
        }
    }

    /**
     * Item prefetching finished, we can cache its information and schedule the next prefetching if
     * needed.
     */
    private fun CacheWindowScope.onItemPrefetched(index: Int, itemSize: Int) {
        debugLog { "onItemPrefetched item=$index size=$itemSize" }
        cachePrefetchedItem(index, itemSize)
        scheduleNextItemIfNeeded()
        traceWindowInfo()
    }

    private fun CacheWindowScope.scheduleNextItemIfNeeded() {
        var nextPrefetchableLineIndex: Int = -1
        // if was scrolling forward
        if (previousPassDelta.sign <= 0) {
            if (prefetchWindowEndExtraSpace > 0)
                nextPrefetchableLineIndex = prefetchWindowEndLine + 1
        } else if (previousPassDelta.sign > 0) {
            if (prefetchWindowStartExtraSpace > 0)
                nextPrefetchableLineIndex = prefetchWindowStartLine - 1
        }

        debugLog { "nextPrefetchableLineIndex=$nextPrefetchableLineIndex" }

        if (
            nextPrefetchableLineIndex > 0 &&
                getLastIndexInLine(nextPrefetchableLineIndex) != InvalidIndex &&
                getLastIndexInLine(nextPrefetchableLineIndex) < itemsCount
        ) {
            prefetchWindowHandles[nextPrefetchableLineIndex] =
                schedulePrefetch(nextPrefetchableLineIndex) { index, mainAxisSize ->
                    onItemPrefetched(index, mainAxisSize)
                }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** Bridge between LazyLayout and its implementation. */
internal interface CacheWindowScope {
    val totalItemsCount: Int
    val visibleLineCount: Int
    val hasVisibleItems: Boolean
    val mainAxisExtraSpaceStart: Int
    val mainAxisExtraSpaceEnd: Int
    val firstVisibleLineIndex: Int
    val lastVisibleLineIndex: Int
    val mainAxisViewportSize: Int
    val density: Density?

    fun schedulePrefetch(lineIndex: Int, onItemPrefetched: (Int, Int) -> Unit): List<PrefetchHandle>

    fun getVisibleItemSize(indexInVisibleLines: Int): Int

    fun getVisibleItemLine(indexInVisibleLines: Int): Int

    fun getVisibleLineKey(indexInVisibleLines: Int): Any

    fun getLastIndexInLine(lineIndex: Int): Int

    fun getLastLineIndex(): Int
}

internal inline fun CacheWindowScope.forEachVisibleItem(
    action: (itemIndex: Int, itemKey: Any, mainAxisSize: Int) -> Unit
) {
    repeat(visibleLineCount) {
        action(getVisibleItemLine(it), getVisibleLineKey(it), getVisibleItemSize(it))
    }
}

private const val InvalidItemSize = -1
internal const val InvalidIndex = -1
private const val UnsetItemCount = -1

private const val DebugEnabled = false

private inline fun debugLog(generateMsg: () -> String) {
    if (DebugEnabled) {
        println("CacheWindowLogic: ${generateMsg()}")
    }
}

internal class CachedItem(var key: Any, var mainAxisSize: Int) {
    companion object NoKey
}

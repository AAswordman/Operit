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

import androidx.compose.foundation.ExperimentalFoundationApi
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.CacheWindowLogic
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.CacheWindowScope
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.InvalidIndex
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutCacheWindow
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutPrefetchState.PrefetchHandle
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.NestedPrefetchScope
import androidx.compose.ui.unit.Density
import kotlin.math.absoluteValue

/**
 * This is a transition class based on [androidx.compose.foundation.lazy.LazyListPrefetchStrategy]
 * where we will perform a window based prefetching for items in the direction of the scroll
 * movement (ahead).
 */
@OptIn(ExperimentalFoundationApi::class)
internal class LazyListCacheWindowStrategy(cacheWindow: LazyLayoutCacheWindow) :
    CacheWindowLogic(cacheWindow), LazyListPrefetchStrategy {
    private val cacheWindowScope = LazyListCacheWindowScope()

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        applyWindowScope(layoutInfo) { onScroll(delta) }
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        applyWindowScope(layoutInfo) { onVisibleItemsUpdated() }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) {
        val resolvedNestedPrefetchItemCount =
            if (nestedPrefetchItemCount == -1) {
                DefaultNestedPrefetchCount
            } else {
                nestedPrefetchItemCount
            }
        repeat(resolvedNestedPrefetchItemCount) {
            schedulePrecomposition(firstVisibleItemIndex + it)
        }
    }

    /** Adapts the LazyListPrefetchScope and LazyListLayoutInfo to a single scope. */
    private inline fun LazyListPrefetchScope.applyWindowScope(
        layoutInfo: LazyListLayoutInfo,
        block: CacheWindowScope.() -> Unit,
    ) {
        cacheWindowScope.layoutInfo = layoutInfo
        cacheWindowScope.prefetchScope = this
        block(cacheWindowScope)
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class LazyListCacheWindowScope() : CacheWindowScope {
    lateinit var layoutInfo: LazyListLayoutInfo
    lateinit var prefetchScope: LazyListPrefetchScope

    override val totalItemsCount: Int
        get() = layoutInfo.totalItemsCount

    override val hasVisibleItems: Boolean
        get() = layoutInfo.visibleItemsInfo.isNotEmpty()

    override val mainAxisExtraSpaceStart: Int
        get() {
            val firstVisibleItem = layoutInfo.visibleItemsInfo.first()
            // how much of the first item is peeking out of view at the start of the layout.
            val firstItemOverflowOffset =
                (firstVisibleItem.offset + layoutInfo.beforeContentPadding).coerceAtMost(0)
            // extra space is always positive in this context
            return firstItemOverflowOffset.absoluteValue
        }

    override val mainAxisExtraSpaceEnd: Int
        get() {
            val lastVisibleItem = layoutInfo.visibleItemsInfo.last()
            // how much of the last item is peeking out of view at the end of the layout
            val lastItemOverflowOffset =
                lastVisibleItem.offset + lastVisibleItem.size + layoutInfo.mainAxisItemSpacing

            // extra space is always positive in this context
            return (lastItemOverflowOffset - layoutInfo.viewportEndOffset).absoluteValue
        }

    override val firstVisibleLineIndex: Int
        get() = layoutInfo.visibleItemsInfo.first().index

    override val lastVisibleLineIndex: Int
        get() = layoutInfo.visibleItemsInfo.last().index

    override val mainAxisViewportSize: Int
        get() = layoutInfo.singleAxisViewportSize

    override val density: Density?
        get() = (layoutInfo as? LazyListMeasureResult)?.density

    override fun schedulePrefetch(
        lineIndex: Int,
        onItemPrefetched: (Int, Int) -> Unit,
    ): List<PrefetchHandle> {
        return listOf(
            prefetchScope.schedulePrefetch(
                lineIndex,
                { onItemPrefetched.invoke(index, mainAxisSize) },
            )
        )
    }

    override val visibleLineCount: Int
        get() = layoutInfo.visibleItemsInfo.size

    override fun getVisibleItemSize(indexInVisibleLines: Int): Int =
        layoutInfo.visibleItemsInfo[indexInVisibleLines].size

    override fun getVisibleItemLine(indexInVisibleLines: Int): Int =
        layoutInfo.visibleItemsInfo[indexInVisibleLines].index

    override fun getLastIndexInLine(lineIndex: Int): Int = lineIndex

    override fun getVisibleLineKey(indexInVisibleLines: Int): Any {
        return layoutInfo.visibleItemsInfo[indexInVisibleLines].key
    }

    override fun getLastLineIndex(): Int {
        if (totalItemsCount == 0) return InvalidIndex
        return totalItemsCount - 1
    }
}

// we use 2 here because nested list have usually > 1 visible elements, so 2 is the minimum
// logical value we could use.
private const val DefaultNestedPrefetchCount = 2

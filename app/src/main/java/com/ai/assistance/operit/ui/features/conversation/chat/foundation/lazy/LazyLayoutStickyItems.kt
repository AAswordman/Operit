/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.collection.IntList
import androidx.collection.emptyIntList
import androidx.collection.intListOf
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastFirstOrNull

/** Defines how sticky items should be placed in a Lazy Layout */
internal interface StickyItemsPlacement {

    /**
     * Determines which indices in [stickyItems] should be sticking, that is, have their placement
     * altered.
     *
     * @param firstVisibleItemIndex The first visible item in the layout
     * @param lastVisibleItemIndex The last visible item in the layout
     * @param stickyItems The indices of candidate items for sticking
     * @return A list of items that should be sticking.
     */
    fun getStickingIndices(
        firstVisibleItemIndex: Int,
        lastVisibleItemIndex: Int,
        stickyItems: IntList,
    ): IntList

    /**
     * This method is executed for each item returned from [getStickingIndices] to determine their
     * final placement.
     *
     * @param visibleStickyItems The currently visible sticky items.
     * @param itemIndex The current sticking item index.
     * @param itemSize The current sticking item main axis size.
     * @param itemOffset The initial offset for this sticking item. If this item doesn't have an
     *   offset, that is, it has been just composed, this will be Int.MIN_VALUE.
     * @param beforeContentPadding Padding applied to the start of the layout
     * @param afterContentPadding Padding applied to the end of the layout
     * @param layoutWidth The containing layout's width
     * @param layoutHeight The containing layout's height
     */
    fun calculateStickingItemOffset(
        visibleStickyItems: List<LazyLayoutMeasuredItem>,
        itemIndex: Int,
        itemSize: Int,
        itemOffset: Int,
        beforeContentPadding: Int,
        afterContentPadding: Int,
        layoutWidth: Int,
        layoutHeight: Int,
    ): Int

    companion object {

        /**
         * A [StickyItemsPlacement] that sticks a single item to the top and pushes sticky item when
         * another approaches the top of the layout.
         */
        val StickToTopPlacement =
            object : StickyItemsPlacement {
                override fun calculateStickingItemOffset(
                    visibleStickyItems: List<LazyLayoutMeasuredItem>,
                    itemIndex: Int,
                    itemSize: Int,
                    itemOffset: Int,
                    beforeContentPadding: Int,
                    afterContentPadding: Int,
                    layoutWidth: Int,
                    layoutHeight: Int,
                ): Int {

                    // the next item offset
                    val nextStickyItemOffset =
                        visibleStickyItems.fastFirstOrNull { it.index != itemIndex }?.mainAxisOffset
                            ?: Int.MIN_VALUE

                    debugLog { "Next Item Offset=$nextStickyItemOffset" }

                    var updatedItemOffset =
                        if (itemOffset == Int.MIN_VALUE) {
                            -beforeContentPadding
                        } else {
                            maxOf(-beforeContentPadding, itemOffset)
                        }

                    // If there's a next item overlapping with the current item, the offset
                    // should represent that.
                    if (nextStickyItemOffset != Int.MIN_VALUE) {
                        updatedItemOffset =
                            minOf(updatedItemOffset, nextStickyItemOffset - itemSize)
                    }

                    debugLog {
                        "Item=$itemIndex Initial_Offset=$itemOffset " +
                            "Final_Offset=$updatedItemOffset"
                    }
                    return updatedItemOffset
                }

                override fun getStickingIndices(
                    firstVisibleItemIndex: Int,
                    lastVisibleItemIndex: Int,
                    stickyItems: IntList,
                ): IntList {
                    // no items present
                    if ((lastVisibleItemIndex - firstVisibleItemIndex) < 0 || stickyItems.isEmpty())
                        return emptyIntList()

                    // First non sticking visible item
                    val firstVisible = firstVisibleItemIndex

                    debugLog { "First Visible Item Index=${firstVisible}" }

                    var currentHeaderIndex = -1

                    // The sticking header will be the first one after the first visible item
                    // or the first visible item itself.
                    for (index in stickyItems.indices) {
                        if (stickyItems[index] <= firstVisible) {
                            currentHeaderIndex = stickyItems[index]
                        } else {
                            break
                        }
                    }

                    return if (currentHeaderIndex == -1) {
                        // we have no headers needing special handling
                        emptyIntList()
                    } else {
                        intListOf(currentHeaderIndex)
                    }
                }
            }
    }
}

private const val Debug = false

private inline fun debugLog(generateMsg: () -> String) {
    if (Debug) {
        println("StickToTopBehavior: ${generateMsg()}")
    }
}

private val LazyLayoutMeasuredItem.mainAxisOffset
    get() = getOffset(0).let { if (isVertical) it.y else it.x }

/**
 * This glue logic is not meant to become public. In here we will use [StickyItemsPlacement] to
 * determine which items are sticking, compose (when needed) and position them. The result is a list
 * of [LazyLayoutMeasuredItem] with the sticking items and [positionedItems] with the updated list
 * of existing items.
 */
internal fun <T : LazyLayoutMeasuredItem> StickyItemsPlacement?.applyStickyItems(
    firstVisibleItemIndex: Int,
    lastVisibleItemIndex: Int,
    positionedItems: MutableList<T>,
    stickyItems: IntList,
    beforeContentPadding: Int,
    afterContentPadding: Int,
    layoutWidth: Int,
    layoutHeight: Int,
    getAndMeasure: (Int) -> T,
): List<T> {
    return if (this != null && positionedItems.isNotEmpty() && stickyItems.isNotEmpty()) {
        // gather sticking items
        val stickingItems =
            getStickingIndices(firstVisibleItemIndex, lastVisibleItemIndex, stickyItems)

        val positionedStickingItems = mutableListOf<T>()
        val visibleStickyItems = positionedItems.fastFilter { stickyItems.contains(it.index) }

        // update sticking item offsets
        stickingItems.forEach { stickingIndex ->
            val itemIndex = positionedItems.indexOfFirst { it.index == stickingIndex }
            // Compose or retrieve item
            val item =
                if (itemIndex == -1) {
                    getAndMeasure(stickingIndex)
                } else {
                    positionedItems.removeAt(itemIndex)
                }
            val offset =
                calculateStickingItemOffset(
                    visibleStickyItems,
                    stickingIndex,
                    item.mainAxisSizeWithSpacings,
                    if (itemIndex == -1) Int.MIN_VALUE else item.mainAxisOffset,
                    beforeContentPadding,
                    afterContentPadding,
                    layoutWidth,
                    layoutHeight,
                )
            item.nonScrollableItem = true
            item.position(offset, 0, layoutWidth, layoutHeight)
            positionedStickingItems.add(item)
        }
        positionedStickingItems
    } else {
        emptyList()
    }
}

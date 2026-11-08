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

import androidx.collection.MutableObjectIntMap
import androidx.collection.ObjectIntMap
import androidx.collection.emptyObjectIntMap
import androidx.compose.foundation.internal.checkPrecondition

/**
 * A key-index mapping that can be used by the [LazyLayoutItemProvider] to keep track of indices and
 * keys in [LazyLayout].
 */
interface LazyLayoutKeyIndexMap {
    /** @return current index for given [key] or `-1` if not found. */
    fun getIndex(key: Any): Int

    /** @return key for a given [index] if it is known, or null otherwise. */
    fun getKey(index: Int): Any?
}

/**
 * A [LazyLayoutKeyIndexMap] that keeps a mapping over given [IntRange] of items. Items outside of
 * given range are considered unknown, with null returned as the index.
 *
 * @param itemIndexRange Range of items to keep track of.
 * @param intervalContent Source of item information in the form of [LazyLayoutIntervalContent].
 */
fun LazyLayoutKeyIndexMap(
    itemIndexRange: IntRange,
    intervalContent: LazyLayoutIntervalContent<*>,
): LazyLayoutKeyIndexMap = NearestRangeKeyIndexMap(itemIndexRange, intervalContent)

/**
 * Implementation of [LazyLayoutKeyIndexMap] indexing over given [IntRange] of items. Items outside
 * of given range are considered unknown, with null returned as the index.
 */
internal class NearestRangeKeyIndexMap(
    nearestRange: IntRange,
    intervalContent: LazyLayoutIntervalContent<*>,
) : LazyLayoutKeyIndexMap {
    private val map: ObjectIntMap<Any>
    private val keys: Array<Any?>
    private val keysStartIndex: Int

    init {
        // Traverses the interval [list] in order to create a mapping from the key to the index for
        // all the indexes in the passed [range].
        val list = intervalContent.intervals
        val first = nearestRange.first
        checkPrecondition(first >= 0) { "negative nearestRange.first" }
        val last = minOf(nearestRange.last, list.size - 1)
        if (last < first) {
            map = emptyObjectIntMap()
            keys = emptyArray()
            keysStartIndex = 0
        } else {
            val size = last - first + 1
            keys = arrayOfNulls<Any?>(size)
            keysStartIndex = first
            map =
                MutableObjectIntMap<Any>(size).also { map ->
                    list.forEach(fromIndex = first, toIndex = last) {
                        val keyFactory = it.value.key
                        val start = maxOf(first, it.startIndex)
                        val end = minOf(last, it.startIndex + it.size - 1)
                        for (i in start..end) {
                            val key =
                                keyFactory?.invoke(i - it.startIndex) ?: getDefaultLazyLayoutKey(i)
                            map[key] = i
                            keys[i - keysStartIndex] = key
                        }
                    }
                }
        }
    }

    override fun getIndex(key: Any): Int = map.getOrElse(key) { -1 }

    override fun getKey(index: Int) = keys.getOrElse(index - keysStartIndex) { null }
}

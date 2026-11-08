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

/**
 * Common parts backing the interval-based content of lazy layout defined through `item` DSL.
 *
 * Note: this class is a part of [LazyLayout] harness that allows for building custom lazy layouts.
 * LazyLayout and all corresponding APIs are still under development and are subject to change.
 */
abstract class LazyLayoutIntervalContent<Interval : LazyLayoutIntervalContent.Interval> {
    abstract val intervals: IntervalList<Interval>

    /** The total amount of items in all the intervals. */
    val itemCount: Int
        get() = intervals.size

    /** Returns item key based on a global index. */
    fun getKey(index: Int): Any =
        withInterval(index) { localIndex, content ->
            content.key?.invoke(localIndex) ?: getDefaultLazyLayoutKey(index)
        }

    /** Returns content type based on a global index. */
    fun getContentType(index: Int): Any? =
        withInterval(index) { localIndex, content -> content.type.invoke(localIndex) }

    /**
     * Runs a [block] on the content of the interval associated with the provided [globalIndex] with
     * providing a local index in the given interval.
     */
    inline fun <T> withInterval(
        globalIndex: Int,
        block: (localIntervalIndex: Int, content: Interval) -> T,
    ): T {
        val interval = intervals[globalIndex]
        val localIntervalIndex = globalIndex - interval.startIndex
        return block(localIntervalIndex, interval.value)
    }

    /**
     * Common content of individual intervals in `item` DSL of lazy layouts.
     *
     * Note: this class is a part of [LazyLayout] harness that allows for building custom lazy
     * layouts. LazyLayout and all corresponding APIs are still under development and are subject to
     * change.
     */
    interface Interval {
        /** Returns item key based on a local index for the current interval. */
        val key: ((index: Int) -> Any)?
            get() = null

        /** Returns item type based on a local index for the current interval. */
        val type: ((index: Int) -> Any?)
            get() = { null }
    }
}

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

import androidx.compose.foundation.gestures.ScrollScope
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.ui.util.fastFirstOrNull

/**
 * An implementation of [LazyLayoutScrollScope] that can be used with LazyLists. Please refer to the
 * sample to learn how to use this API.
 *
 * @param state The [LazyListState] associated with the layout where this custom scroll should be
 *   performed.
 * @param scrollScope The base [ScrollScope] where the scroll session was created.
 * @return An implementation of [LazyLayoutScrollScope] that works with [LazyRow] and [LazyColumn].
 * @sample androidx.compose.foundation.samples.LazyListCustomScrollUsingLazyLayoutScrollScopeSample
 */
fun LazyLayoutScrollScope(state: LazyListState, scrollScope: ScrollScope): LazyLayoutScrollScope {

    return object : LazyLayoutScrollScope, ScrollScope by scrollScope {
        override val firstVisibleItemIndex: Int
            get() = state.firstVisibleItemIndex

        override val firstVisibleItemScrollOffset: Int
            get() = state.firstVisibleItemScrollOffset

        override val lastVisibleItemIndex: Int
            get() = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

        override val itemCount: Int
            get() = state.layoutInfo.totalItemsCount

        override fun snapToItem(index: Int, offset: Int) {
            state.snapToItemIndexInternal(index, offset, forceRemeasure = true)
        }

        override fun calculateDistanceTo(targetIndex: Int, targetOffset: Int): Int {
            val layoutInfo = state.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return 0
            return if (targetIndex !in firstVisibleItemIndex..lastVisibleItemIndex) {
                val averageSize = layoutInfo.visibleItemsAverageSize()
                val indexesDiff = targetIndex - firstVisibleItemIndex
                (averageSize * indexesDiff) - firstVisibleItemScrollOffset
            } else {
                val visibleItem =
                    layoutInfo.visibleItemsInfo.fastFirstOrNull { it.index == targetIndex }
                visibleItem?.offset ?: 0
            } + targetOffset
        }
    }
}

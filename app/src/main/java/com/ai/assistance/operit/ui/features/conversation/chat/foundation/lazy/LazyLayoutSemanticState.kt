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

import androidx.compose.foundation.gestures.Orientation
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.LazyLayoutSemanticState
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.estimatedLazyMaxScrollOffset
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.estimatedLazyScrollOffset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.CollectionInfo

internal fun LazyLayoutSemanticState(
    state: LazyListState,
    isVertical: Boolean,
): LazyLayoutSemanticState =
    object : LazyLayoutSemanticState {

        // The total number of items in the list, derived from layout info.
        private val totalItemsCount by derivedStateOf { state.layoutInfo.totalItemsCount }

        override val scrollOffset: Float
            get() =
                estimatedLazyScrollOffset(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                )

        override val maxScrollOffset: Float
            get() =
                estimatedLazyMaxScrollOffset(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                    state.canScrollForward,
                )

        override suspend fun scrollToItem(index: Int) {
            state.scrollToItem(index)
        }

        override fun collectionInfo(): CollectionInfo =
            if (isVertical) {
                CollectionInfo(rowCount = totalItemsCount, columnCount = 1)
            } else {
                CollectionInfo(rowCount = 1, columnCount = totalItemsCount)
            }

        override val viewport: Int
            get() =
                if (state.layoutInfo.orientation == Orientation.Vertical) {
                    state.layoutInfo.viewportSize.height
                } else {
                    state.layoutInfo.viewportSize.width
                }

        override val contentPadding: Int
            get() = state.layoutInfo.beforeContentPadding + state.layoutInfo.afterContentPadding
    }

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

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * This class manages the scroll delta between lookahead pass and approach pass. Lookahead pass is
 * the source of truth for scrolling lazy layouts. However, at times during an animation, the items
 * in approach may not be as large as they are in lookahead yet (i.e. these items have not reached
 * their target size). As such, the same scrolling that lookahead accepts may cause back scroll in
 * approach due to the smaller item size at the end of the list. In this situation, we will be
 * taking the amount of back scroll from the approach and gradually animate it down to 0 to avoid
 * any sudden jump in position via [updateScrollDeltaForApproach].
 */
internal class LazyLayoutScrollDeltaBetweenPasses {

    internal val scrollDeltaBetweenPasses: Float
        get() = _scrollDeltaBetweenPasses.value

    internal var job: Job? = null

    internal val isActive: Boolean
        get() = _scrollDeltaBetweenPasses.value != 0f

    private var _scrollDeltaBetweenPasses: AnimationState<Float, AnimationVector1D> =
        AnimationState(Float.VectorConverter, 0f, 0f)

    // Updates the scroll delta between lookahead & post-lookahead pass
    internal fun updateScrollDeltaForApproach(
        delta: Float,
        density: Density,
        coroutineScope: CoroutineScope,
    ) {
        if (delta <= with(density) { DeltaThresholdForScrollAnimation.toPx() }) {
            // If the delta is within the threshold, scroll by the delta amount instead of animating
            return
        }

        // Scroll delta is updated during lookahead, we don't need to trigger lookahead when
        // the delta changes.
        Snapshot.withoutReadObservation {
            val currentDelta = _scrollDeltaBetweenPasses.value

            job?.cancel()
            if (_scrollDeltaBetweenPasses.isRunning) {
                _scrollDeltaBetweenPasses = _scrollDeltaBetweenPasses.copy(currentDelta - delta)
            } else {
                _scrollDeltaBetweenPasses = AnimationState(Float.VectorConverter, -delta)
            }
            job =
                coroutineScope.launch {
                    _scrollDeltaBetweenPasses.animateTo(
                        0f,
                        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.5f),
                        true,
                    )
                }
        }
    }

    internal fun stop() {
        job?.cancel()
        _scrollDeltaBetweenPasses = AnimationState(Float.VectorConverter, 0f)
    }
}

private val DeltaThresholdForScrollAnimation = 1.dp

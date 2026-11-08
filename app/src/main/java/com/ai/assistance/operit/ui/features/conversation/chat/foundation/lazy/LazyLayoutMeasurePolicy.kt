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

import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.unit.Constraints

/*
 * Defines the measure and layout behavior of a [LazyLayout].
 */
fun interface LazyLayoutMeasurePolicy {
    /**
     * The function that defines the measurement and layout. For each item in this [LazyLayout] we
     * should call [LazyLayoutMeasureScope.compose] and then call [Measurable.measure] with the
     * child [Constraints] to be used.
     *
     * @param constraints The constraints used to measure this Lazy Layout.
     */
    fun LazyLayoutMeasureScope.measure(constraints: Constraints): MeasureResult
}

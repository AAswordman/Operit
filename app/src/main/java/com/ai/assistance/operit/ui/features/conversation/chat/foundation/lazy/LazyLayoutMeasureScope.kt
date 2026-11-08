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

import androidx.annotation.IntRange as AndroidXIntRange
import androidx.collection.mutableIntObjectMapOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit

/**
 * The receiver scope of a [LazyLayout]'s measure lambda. The return value of the measure lambda is
 * [MeasureResult], which should be returned by [layout].
 *
 * Call [compose] to compose items emitted in a content block for a given index.
 */
@Stable
sealed interface LazyLayoutMeasureScope : MeasureScope {

    /**
     * Compose an item of lazy layout.
     *
     * @param index the item index. Should be no larger that [LazyLayoutItemProvider.itemCount].
     * @return List of [Measurable]s. Note that if you emitted multiple children into the item
     *   composable you will receive multiple measurebles.
     */
    fun compose(@AndroidXIntRange(from = 0) index: Int): List<Measurable>

    /**
     * Subcompose and measure the item of lazy layout.
     *
     * @param index the item index. Should be no larger that [LazyLayoutItemProvider.itemCount].
     * @param constraints [Constraints] to measure the children emitted into an item content
     *   composable specified via [LazyLayoutItemProvider.Item].
     * @return List of [Placeable]s. Note that if you emitted multiple children into the item
     *   composable you will receive multiple placeables, each of them will be measured with the
     *   passed [constraints].
     */
    @Deprecated(
        "Please use compose and call Measurable.measure",
        ReplaceWith("compose(index).map { it.measure(constraints) }"),
    )
    @ExperimentalFoundationApi
    fun measure(index: Int, constraints: Constraints): List<Placeable>
}

internal class LazyLayoutMeasureScopeImpl
internal constructor(
    private val itemContentFactory: LazyLayoutItemContentFactory,
    private val subcomposeMeasureScope: SubcomposeMeasureScope,
) : LazyLayoutMeasureScope, MeasureScope by subcomposeMeasureScope {

    private val itemProvider = itemContentFactory.itemProvider()

    /**
     * A cache of the previously composed items. It allows us to support [get] re-executions with
     * the same index during the same measure pass.
     */
    private val placeablesCache = mutableIntObjectMapOf<List<Placeable>>()

    private val measurablesCache = mutableIntObjectMapOf<List<Measurable>>()

    override fun compose(index: Int): List<Measurable> {
        val cachedMeasurable = measurablesCache[index]
        if (cachedMeasurable != null) {
            return cachedMeasurable
        } else {
            val key = itemProvider.getKey(index)
            val contentType = itemProvider.getContentType(index)
            val itemContent = itemContentFactory.getContent(index, key, contentType)
            return subcomposeMeasureScope.subcompose(key, itemContent).also {
                measurablesCache[index] = it
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Deprecated("Please use compose and measure")
    override fun measure(index: Int, constraints: Constraints): List<Placeable> {
        val cachedPlaceable = placeablesCache[index]
        return if (cachedPlaceable != null) {
            cachedPlaceable
        } else {
            val key = itemProvider.getKey(index)
            val contentType = itemProvider.getContentType(index)
            val itemContent = itemContentFactory.getContent(index, key, contentType)
            val measurables = subcomposeMeasureScope.subcompose(key, itemContent)
            List(measurables.size) { i -> measurables[i].measure(constraints) }
                .also { placeablesCache[index] = it }
        }
    }

    /** Below overrides added to work around https://youtrack.jetbrains.com/issue/KT-51672 */
    override fun TextUnit.toDp(): Dp = with(subcomposeMeasureScope) { toDp() }

    override fun Int.toDp(): Dp = with(subcomposeMeasureScope) { toDp() }

    override fun Float.toDp(): Dp = with(subcomposeMeasureScope) { toDp() }

    override fun Float.toSp(): TextUnit = with(subcomposeMeasureScope) { toSp() }

    override fun Int.toSp(): TextUnit = with(subcomposeMeasureScope) { toSp() }

    override fun Dp.toSp(): TextUnit = with(subcomposeMeasureScope) { toSp() }

    override fun DpSize.toSize(): Size = with(subcomposeMeasureScope) { toSize() }

    override fun Size.toDpSize(): DpSize = with(subcomposeMeasureScope) { toDpSize() }
}

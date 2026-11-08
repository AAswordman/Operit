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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class LazyLayoutItemAnimation(
    private val coroutineScope: CoroutineScope,
    private val graphicsContext: GraphicsContext? = null,
    private val onLayerPropertyChanged: () -> Unit = {},
) {
    var fadeInSpec: FiniteAnimationSpec<Float>? = null
    var placementSpec: FiniteAnimationSpec<IntOffset>? = null
    var fadeOutSpec: FiniteAnimationSpec<Float>? = null

    var isRunningMovingAwayAnimation = false
        private set

    /**
     * Returns true when the placement animation is currently in progress so the parent should
     * continue composing this item.
     */
    var isPlacementAnimationInProgress by mutableStateOf(false)
        private set

    /** Returns true when the appearance animation is currently in progress. */
    var isAppearanceAnimationInProgress by mutableStateOf(false)
        private set

    /** Returns true when the disappearance animation is currently in progress. */
    var isDisappearanceAnimationInProgress by mutableStateOf(false)
        private set

    /** Returns true when the disappearance animation has been finished. */
    var isDisappearanceAnimationFinished by mutableStateOf(false)
        private set

    /**
     * This property is managed by the animation manager and is not directly used by this class. It
     * represents the last known offset of this item in the lazy layout coordinate space. It will be
     * updated on every scroll and is allowing the manager to track when the item position changes
     * not because of the scroll event in order to start the animation. When there is an active
     * animation it represents the final/target offset.
     */
    var rawOffset: IntOffset = NotInitialized

    /**
     * The final offset the placeable associated with this animations was placed at. Unlike
     * [rawOffset] it takes into account things like reverse layout and content padding.
     */
    var finalOffset: IntOffset = IntOffset.Zero

    /** Current [GraphicsLayer]. It will be set to null in [release]. */
    var layer: GraphicsLayer? = graphicsContext?.createGraphicsLayer()
        private set

    private val placementDeltaAnimation = Animatable(IntOffset.Zero, IntOffset.VectorConverter)

    private val visibilityAnimation = Animatable(1f, Float.VectorConverter)

    /**
     * Current delta to apply for a placement offset. Updates every animation frame. The settled
     * value is [IntOffset.Zero] so the animation is always targeting this value.
     */
    var placementDelta by mutableStateOf(IntOffset.Zero)
        private set

    /** Cancels the ongoing placement animation if there is one. */
    fun cancelPlacementAnimation() {
        if (isPlacementAnimationInProgress) {
            coroutineScope.launch {
                placementDeltaAnimation.snapTo(IntOffset.Zero)
                placementDelta = IntOffset.Zero
                isPlacementAnimationInProgress = false
            }
        }
    }

    /**
     * Tracks the offset of the item in the lookahead pass. When set, this is the animation target
     * that placementDelta should be applied to.
     */
    var lookaheadOffset: IntOffset = NotInitialized

    /** Animate the placement by the given [delta] offset. */
    fun animatePlacementDelta(delta: IntOffset, isMovingAway: Boolean) {
        val spec = placementSpec ?: return
        val totalDelta = placementDelta - delta
        placementDelta = totalDelta
        isPlacementAnimationInProgress = true
        isRunningMovingAwayAnimation = isMovingAway
        coroutineScope.launch {
            try {
                val finalSpec =
                    if (placementDeltaAnimation.isRunning) {
                        // when interrupted, use the default spring, unless the spec is a spring.
                        if (spec is SpringSpec<IntOffset>) {
                            spec
                        } else {
                            InterruptionSpec
                        }
                    } else {
                        spec
                    }
                if (!placementDeltaAnimation.isRunning) {
                    // if not running we can snap to the initial value and animate to zero
                    placementDeltaAnimation.snapTo(totalDelta)
                    onLayerPropertyChanged()
                }
                // if animation is not currently running the target will be zero, otherwise
                // we have to continue the animation from the current value, but keep the needed
                // total delta for the new animation.
                val animationTarget = placementDeltaAnimation.value - totalDelta
                placementDeltaAnimation.animateTo(animationTarget, finalSpec) {
                    // placementDelta is calculated as if we always animate to target equal to zero
                    placementDelta = value - animationTarget
                    onLayerPropertyChanged()
                }

                isPlacementAnimationInProgress = false
                isRunningMovingAwayAnimation = false
            } catch (_: CancellationException) {
                // we don't reset inProgress in case of cancellation as it means
                // there is a new animation started which would reset it later
            }
        }
    }

    fun animateAppearance() {
        val layer = layer
        val spec = fadeInSpec
        if (isAppearanceAnimationInProgress || spec == null || layer == null) {
            if (isDisappearanceAnimationInProgress) {
                // we have an active disappearance, and then appearance was requested, but the user
                // provided null spec for the appearance. we need to immediately switch to 1f
                layer?.alpha = 1f
                coroutineScope.launch { visibilityAnimation.snapTo(1f) }
            }
            return
        }
        isAppearanceAnimationInProgress = true
        val shouldResetValue = !isDisappearanceAnimationInProgress
        if (shouldResetValue) {
            layer.alpha = 0f
        }
        coroutineScope.launch {
            try {
                if (shouldResetValue) {
                    visibilityAnimation.snapTo(0f)
                }
                visibilityAnimation.animateTo(1f, spec) {
                    layer.alpha = value
                    onLayerPropertyChanged()
                }
            } finally {
                isAppearanceAnimationInProgress = false
            }
        }
    }

    fun animateDisappearance() {
        val layer = layer
        val spec = fadeOutSpec
        if (layer == null || isDisappearanceAnimationInProgress || spec == null) {
            return
        }
        isDisappearanceAnimationInProgress = true
        coroutineScope.launch {
            try {
                visibilityAnimation.animateTo(0f, spec) {
                    layer.alpha = value
                    onLayerPropertyChanged()
                }
                isDisappearanceAnimationFinished = true
            } finally {
                isDisappearanceAnimationInProgress = false
            }
        }
    }

    fun release() {
        if (isPlacementAnimationInProgress) {
            isPlacementAnimationInProgress = false
            coroutineScope.launch { placementDeltaAnimation.stop() }
        }
        if (isAppearanceAnimationInProgress) {
            isAppearanceAnimationInProgress = false
            coroutineScope.launch { visibilityAnimation.stop() }
        }
        if (isDisappearanceAnimationInProgress) {
            isDisappearanceAnimationInProgress = false
            coroutineScope.launch { visibilityAnimation.stop() }
        }
        isRunningMovingAwayAnimation = false
        placementDelta = IntOffset.Zero
        rawOffset = NotInitialized
        layer?.let { graphicsContext?.releaseGraphicsLayer(it) }
        layer = null
        fadeInSpec = null
        fadeOutSpec = null
        placementSpec = null
    }

    companion object {
        val NotInitialized = IntOffset(Int.MAX_VALUE, Int.MAX_VALUE)
    }
}

internal data class LazyLayoutAnimateItemElement(
    private val fadeInSpec: FiniteAnimationSpec<Float>?,
    private val placementSpec: FiniteAnimationSpec<IntOffset>?,
    private val fadeOutSpec: FiniteAnimationSpec<Float>?,
) : ModifierNodeElement<LazyLayoutAnimationSpecsNode>() {

    override fun create(): LazyLayoutAnimationSpecsNode =
        LazyLayoutAnimationSpecsNode(fadeInSpec, placementSpec, fadeOutSpec)

    override fun update(node: LazyLayoutAnimationSpecsNode) {
        node.fadeInSpec = fadeInSpec
        node.placementSpec = placementSpec
        node.fadeOutSpec = fadeOutSpec
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "animateItem"
        properties["fadeInSpec"] = fadeInSpec
        properties["placementSpec"] = placementSpec
        properties["fadeOutSpec"] = fadeOutSpec
    }
}

internal class LazyLayoutAnimationSpecsNode(
    var fadeInSpec: FiniteAnimationSpec<Float>?,
    var placementSpec: FiniteAnimationSpec<IntOffset>?,
    var fadeOutSpec: FiniteAnimationSpec<Float>?,
) : Modifier.Node(), ParentDataModifierNode {

    override fun Density.modifyParentData(parentData: Any?): Any = this@LazyLayoutAnimationSpecsNode
}

/** We switch to this spec when a duration based animation is being interrupted. */
private val InterruptionSpec =
    spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

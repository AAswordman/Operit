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

import androidx.collection.mutableScatterMapOf
import androidx.collection.mutableScatterSetOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CoroutineScope

/**
 * Handles the item animations when it is set via "animateItem" modifiers.
 *
 * This class is responsible for:
 * - animating item appearance for the new items.
 * - detecting when item position changed, figuring our start/end offsets and starting the
 *   animations for placement animations.
 * - animating item disappearance for the removed items.
 */
internal class LazyLayoutItemAnimator<T : LazyLayoutMeasuredItem> {
    // state containing relevant info for active items.
    private val keyToItemInfoMap = mutableScatterMapOf<Any, ItemInfo>()

    // snapshot of the key to index map used for the last measuring.
    private var keyIndexMap: LazyLayoutKeyIndexMap? = null

    // keeps the index of the first visible item index.
    private var firstVisibleIndex = 0

    // stored to not allocate it every pass.
    private val movingAwayKeys = mutableScatterSetOf<Any>()
    private val movingInFromStartBound = mutableListOf<T>()
    private val movingInFromEndBound = mutableListOf<T>()
    private val movingAwayToStartBound = mutableListOf<T>()
    private val movingAwayToEndBound = mutableListOf<T>()
    private val disappearingItems = mutableListOf<LazyLayoutItemAnimation>()
    private var displayingNode: DrawModifierNode? = null

    /**
     * Should be called after the measuring so we can detect position changes and start animations.
     *
     * Note that this method can compose new item and add it into the [positionedItems] list.
     */
    fun onMeasured(
        consumedScroll: Int,
        layoutWidth: Int,
        layoutHeight: Int,
        positionedItems: MutableList<T>,
        keyIndexMap: LazyLayoutKeyIndexMap,
        itemProvider: LazyLayoutMeasuredItemProvider<T>,
        isVertical: Boolean,
        isLookingAhead: Boolean,
        laneCount: Int,
        hasLookaheadOccurred: Boolean,
        layoutMinOffset: Int,
        layoutMaxOffset: Int,
        coroutineScope: CoroutineScope,
        graphicsContext: GraphicsContext,
    ) {
        val previousKeyToIndexMap = this.keyIndexMap
        this.keyIndexMap = keyIndexMap

        val hasAnimations = positionedItems.fastAny { it.hasAnimations }
        if (!hasAnimations && keyToItemInfoMap.isEmpty()) {
            // no animations specified - no work needed - clear animation info
            releaseAnimations()
            return
        }

        val previousFirstVisibleIndex = firstVisibleIndex
        firstVisibleIndex = positionedItems.firstOrNull()?.index ?: 0

        // the consumed scroll is considered as a delta we don't need to animate
        val scrollOffset =
            if (isVertical) {
                IntOffset(0, consumedScroll)
            } else {
                IntOffset(consumedScroll, 0)
            }

        // Only setup animations when we have access to target value in the current pass, which
        // means lookahead pass, or regular pass when not in a lookahead scope.
        val shouldSetupAnimation = isLookingAhead || !hasLookaheadOccurred
        // first add all items we had in the previous run
        keyToItemInfoMap.forEachKey { movingAwayKeys.add(it) }
        // iterate through the items which are visible (without animated offsets)
        positionedItems.fastForEach { item ->
            // remove items we have in the current one as they are still visible.
            movingAwayKeys.remove(item.key)
            if (item.hasAnimations) {
                val itemInfo = keyToItemInfoMap[item.key]
                val previousIndex = previousKeyToIndexMap?.getIndex(item.key) ?: -1
                val shouldAnimateAppearance = previousIndex == -1 && previousKeyToIndexMap != null
                // there is no state associated with this item yet
                if (itemInfo == null) {
                    val newItemInfo = ItemInfo()
                    newItemInfo.updateAnimation(
                        item,
                        coroutineScope,
                        graphicsContext,
                        layoutMinOffset,
                        layoutMaxOffset,
                    )
                    keyToItemInfoMap[item.key] = newItemInfo
                    if (item.index != previousIndex && previousIndex != -1) {
                        if (previousIndex < previousFirstVisibleIndex) {
                            // the larger index will be in the start of the list
                            movingInFromStartBound.add(item)
                        } else {
                            movingInFromEndBound.add(item)
                        }
                    } else {
                        initializeAnimation(
                            item,
                            item.getOffset(0).let { if (item.isVertical) it.y else it.x },
                            newItemInfo,
                        )
                        if (shouldAnimateAppearance) {
                            newItemInfo.animations.forEach { it?.animateAppearance() }
                        }
                    }
                } else {
                    if (shouldSetupAnimation) {
                        itemInfo.updateAnimation(
                            item,
                            coroutineScope,
                            graphicsContext,
                            layoutMinOffset,
                            layoutMaxOffset,
                        )
                        itemInfo.animations.forEach { animation ->
                            if (
                                animation != null &&
                                    animation.rawOffset != LazyLayoutItemAnimation.NotInitialized
                            ) {
                                animation.rawOffset += scrollOffset
                            }
                        }
                        if (shouldAnimateAppearance) {
                            itemInfo.animations.forEach {
                                if (it != null) {
                                    if (it.isDisappearanceAnimationInProgress) {
                                        disappearingItems.remove(it)
                                        displayingNode?.invalidateDraw()
                                    }
                                    it.animateAppearance()
                                }
                            }
                        }
                        startPlacementAnimationsIfNeeded(item)
                    }
                }
            } else {
                // no animation, clean up if needed
                removeInfoForKey(item.key)
            }
        }

        val accumulatedOffsetPerLane = IntArray(laneCount)
        if (shouldSetupAnimation && previousKeyToIndexMap != null) {
            if (movingInFromStartBound.isNotEmpty()) {
                movingInFromStartBound.sortByDescending { previousKeyToIndexMap.getIndex(it.key) }
                movingInFromStartBound.fastForEach { item ->
                    val accumulatedOffset = accumulatedOffsetPerLane.updateAndReturnOffsetFor(item)
                    val mainAxisOffset = layoutMinOffset - accumulatedOffset
                    initializeAnimation(item, mainAxisOffset)
                    startPlacementAnimationsIfNeeded(item)
                }
                accumulatedOffsetPerLane.fill(0)
            }
            if (movingInFromEndBound.isNotEmpty()) {
                movingInFromEndBound.sortBy { previousKeyToIndexMap.getIndex(it.key) }
                movingInFromEndBound.fastForEach { item ->
                    val accumulatedOffset = accumulatedOffsetPerLane.updateAndReturnOffsetFor(item)
                    val mainAxisOffset =
                        layoutMaxOffset + accumulatedOffset - item.mainAxisSizeWithSpacings
                    initializeAnimation(item, mainAxisOffset)
                    startPlacementAnimationsIfNeeded(item)
                }
                accumulatedOffsetPerLane.fill(0)
            }
        }

        movingAwayKeys.forEach { key ->
            // found an item which was in our map previously but is not a part of the
            // positionedItems now
            // TODO(jossiwolf): In some cases, keyToItemInfoMap and movingAwayKeys can get out of
            //  sync. If that's the case, we can not play an animation in any case as the item is
            //  already gone (b/352482051). Follow-up: b/354695943
            val info = keyToItemInfoMap[key] ?: return@forEach
            val newIndex = keyIndexMap.getIndex(key)

            // it is possible that we are being remeasured with smaller laneCount. make sure
            // `lane` and `span` we remembered are not larger than the new max values.
            info.span = minOf(laneCount, info.span)
            info.lane = minOf(laneCount - info.span, info.lane)

            if (newIndex == -1) {
                var isProgress = false
                info.animations.forEachIndexed { index, animation ->
                    if (animation != null) {
                        if (animation.isDisappearanceAnimationInProgress) {
                            isProgress = true
                        } else if (animation.isDisappearanceAnimationFinished) {
                            animation.release()
                            info.animations[index] = null
                            disappearingItems.remove(animation)
                            displayingNode?.invalidateDraw()
                        } else {
                            if (animation.layer != null) {
                                animation.animateDisappearance()
                            }
                            if (animation.isDisappearanceAnimationInProgress) {
                                disappearingItems.add(animation)
                                displayingNode?.invalidateDraw()
                                isProgress = true
                            } else {
                                animation.release()
                                info.animations[index] = null
                            }
                        }
                    }
                }
                if (!isProgress) {
                    removeInfoForKey(key)
                }
            } else {
                val item =
                    itemProvider.getAndMeasure(
                        index = newIndex,
                        constraints = info.constraints!!,
                        lane = info.lane,
                        span = info.span,
                    )

                item.nonScrollableItem = true
                // check if we have any active placement animation on the item
                val inProgress = info.animations.any { it?.isPlacementAnimationInProgress == true }
                if ((!inProgress && newIndex == previousKeyToIndexMap?.getIndex(key))) {
                    removeInfoForKey(key)
                } else {
                    // anytime we compose a new item, and we use it,
                    // we need to update our item info mapping
                    info.updateAnimation(
                        item,
                        coroutineScope,
                        graphicsContext,
                        layoutMinOffset,
                        layoutMaxOffset,
                        crossAxisOffset = info.crossAxisOffset,
                    )
                    if (newIndex < firstVisibleIndex) {
                        movingAwayToStartBound.add(item)
                    } else {
                        movingAwayToEndBound.add(item)
                    }
                }
            }
        }

        if (movingAwayToStartBound.isNotEmpty()) {
            movingAwayToStartBound.sortByDescending { keyIndexMap.getIndex(it.key) }
            movingAwayToStartBound.fastForEach { item ->
                val itemInfo = keyToItemInfoMap[item.key]!!
                val accumulatedOffset = accumulatedOffsetPerLane.updateAndReturnOffsetFor(item)
                val mainAxisOffset =
                    if (isLookingAhead) {
                        positionedItems.first().mainAxisOffset
                    } else {
                        itemInfo.layoutMinOffset
                    } - accumulatedOffset

                item.position(
                    mainAxisOffset = mainAxisOffset,
                    crossAxisOffset = itemInfo.crossAxisOffset,
                    layoutWidth = layoutWidth,
                    layoutHeight = layoutHeight,
                )
                if (shouldSetupAnimation) {
                    startPlacementAnimationsIfNeeded(item, isMovingAway = true)
                }
            }
            accumulatedOffsetPerLane.fill(0)
        }

        if (movingAwayToEndBound.isNotEmpty()) {
            movingAwayToEndBound.sortBy { keyIndexMap.getIndex(it.key) }
            movingAwayToEndBound.fastForEach { item ->
                val itemInfo = keyToItemInfoMap[item.key]!!
                val accumulatedOffset = accumulatedOffsetPerLane.updateAndReturnOffsetFor(item)
                val mainAxisOffset =
                    itemInfo.layoutMaxOffset - item.mainAxisSizeWithSpacings + accumulatedOffset

                item.position(
                    mainAxisOffset = mainAxisOffset,
                    crossAxisOffset = itemInfo.crossAxisOffset,
                    layoutWidth = layoutWidth,
                    layoutHeight = layoutHeight,
                )

                if (shouldSetupAnimation) {
                    startPlacementAnimationsIfNeeded(item, isMovingAway = true)
                }
            }
        }

        // This adds the new items to the list of positioned items while keeping the index of
        // the positioned items sorted in ascending order.
        positionedItems.addAll(0, movingAwayToStartBound.apply { reverse() })
        positionedItems.addAll(movingAwayToEndBound)

        movingInFromStartBound.clear()
        movingInFromEndBound.clear()
        movingAwayToStartBound.clear()
        movingAwayToEndBound.clear()
        movingAwayKeys.clear()
    }

    private fun removeInfoForKey(key: Any) {
        keyToItemInfoMap.remove(key)?.animations?.forEach { it?.release() }
    }

    /**
     * Should be called when the animations are not needed for the next positions change, for
     * example when we snap to a new position.
     */
    fun reset() {
        releaseAnimations()
        keyIndexMap = null
        firstVisibleIndex = -1
    }

    private fun releaseAnimations() {
        if (keyToItemInfoMap.isNotEmpty()) {
            keyToItemInfoMap.forEachValue {
                it.animations.forEach { animation -> animation?.release() }
            }
            keyToItemInfoMap.clear()
        }
    }

    private fun initializeAnimation(
        item: T,
        mainAxisOffset: Int,
        itemInfo: ItemInfo = keyToItemInfoMap[item.key]!!,
    ) {
        val firstPlaceableOffset = item.getOffset(0)

        val targetFirstPlaceableOffset =
            if (item.isVertical) {
                firstPlaceableOffset.copy(y = mainAxisOffset)
            } else {
                firstPlaceableOffset.copy(x = mainAxisOffset)
            }

        // initialize offsets
        itemInfo.animations.forEachIndexed { placeableIndex, animation ->
            if (animation != null) {
                val diffToFirstPlaceableOffset =
                    item.getOffset(placeableIndex) - firstPlaceableOffset
                animation.rawOffset = targetFirstPlaceableOffset + diffToFirstPlaceableOffset
            }
        }
    }

    private fun startPlacementAnimationsIfNeeded(item: T, isMovingAway: Boolean = false) {
        val itemInfo = keyToItemInfoMap[item.key]!!
        itemInfo.animations.forEachIndexed { placeableIndex, animation ->
            if (animation != null) {
                val newTarget = item.getOffset(placeableIndex)
                val currentTarget = animation.rawOffset
                if (
                    currentTarget != LazyLayoutItemAnimation.NotInitialized &&
                        currentTarget != newTarget
                ) {
                    animation.animatePlacementDelta(newTarget - currentTarget, isMovingAway)
                }
                animation.rawOffset = newTarget
            }
        }
    }

    fun getAnimation(key: Any, placeableIndex: Int): LazyLayoutItemAnimation? =
        keyToItemInfoMap[key]?.animations?.get(placeableIndex)

    private fun IntArray.updateAndReturnOffsetFor(item: T): Int {
        val lane = item.lane
        val span = item.span
        var maxOffset = 0
        for (i in lane until lane + span) {
            this[i] += item.mainAxisSizeWithSpacings
            maxOffset = maxOf(maxOffset, this[i])
        }
        return maxOffset
    }

    val minSizeToFitDisappearingItems: IntSize
        get() {
            var size = IntSize.Zero
            disappearingItems.fastForEach {
                val layer = it.layer
                if (layer != null) {
                    size =
                        IntSize(
                            width = maxOf(size.width, it.rawOffset.x + layer.size.width),
                            height = maxOf(size.height, it.rawOffset.y + layer.size.height),
                        )
                }
            }
            return size
        }

    val modifier: Modifier = DisplayingDisappearingItemsElement(this)

    private val T.hasAnimations: Boolean
        get() {
            repeat(placeablesCount) { index ->
                getParentData(index).specs?.let {
                    // found at least one
                    return true
                }
            }
            return false
        }

    private val LazyLayoutMeasuredItem.mainAxisOffset
        get() = getOffset(0).let { if (isVertical) it.y else it.x }

    private val LazyLayoutMeasuredItem.crossAxisOffset
        get() = getOffset(0).let { if (!isVertical) it.y else it.x }

    private inner class ItemInfo {
        /**
         * This array will have the same amount of elements as there are placeables on the item. If
         * the element is not null this means there are specs associated with the given placeable.
         */
        var animations = EmptyArray
            private set

        var constraints: Constraints? = null
        var crossAxisOffset: Int = 0
        var lane: Int = 0
        var span: Int = 1

        private val isRunningPlacement
            get() = animations.any { it?.isRunningMovingAwayAnimation == true }

        var layoutMinOffset = 0
            private set

        var layoutMaxOffset = 0
            private set

        fun updateAnimation(
            positionedItem: T,
            coroutineScope: CoroutineScope,
            graphicsContext: GraphicsContext,
            layoutMinOffset: Int,
            layoutMaxOffset: Int,
            crossAxisOffset: Int = positionedItem.crossAxisOffset,
        ) {
            if (!isRunningPlacement) {
                this.layoutMinOffset = layoutMinOffset
                this.layoutMaxOffset = layoutMaxOffset
            }
            for (i in positionedItem.placeablesCount until animations.size) {
                animations[i]?.release()
            }
            if (animations.size != positionedItem.placeablesCount) {
                animations = animations.copyOf(positionedItem.placeablesCount)
            }
            constraints = positionedItem.constraints
            this.crossAxisOffset = crossAxisOffset
            lane = positionedItem.lane
            span = positionedItem.span
            repeat(positionedItem.placeablesCount) { index ->
                val specs = positionedItem.getParentData(index).specs
                if (specs == null) {
                    animations[index]?.release()
                    animations[index] = null
                } else {
                    val animation =
                        animations[index]
                            ?: LazyLayoutItemAnimation(
                                    coroutineScope = coroutineScope,
                                    graphicsContext = graphicsContext,
                                    // until b/329417380 is fixed we have to trigger any
                                    // invalidation in
                                    // order for the layer properties change to be applied:
                                    onLayerPropertyChanged = { displayingNode?.invalidateDraw() },
                                )
                                .also { animations[index] = it }
                    animation.fadeInSpec = specs.fadeInSpec
                    animation.placementSpec = specs.placementSpec
                    animation.fadeOutSpec = specs.fadeOutSpec
                }
            }
        }
    }

    private data class DisplayingDisappearingItemsElement(
        private val animator: LazyLayoutItemAnimator<*>
    ) : ModifierNodeElement<DisplayingDisappearingItemsNode>() {
        override fun create() = DisplayingDisappearingItemsNode(animator)

        override fun update(node: DisplayingDisappearingItemsNode) {
            node.setAnimator(animator)
        }

        override fun InspectorInfo.inspectableProperties() {
            name = "DisplayingDisappearingItemsElement"
        }
    }

    private data class DisplayingDisappearingItemsNode(
        private var animator: LazyLayoutItemAnimator<*>
    ) : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            animator.disappearingItems.fastForEach {
                val layer = it.layer ?: return@fastForEach
                val x = it.finalOffset.x.toFloat()
                val y = it.finalOffset.y.toFloat()
                translate(x - layer.topLeft.x.toFloat(), y - layer.topLeft.y.toFloat()) {
                    drawLayer(layer)
                }
            }
            drawContent()
        }

        override fun onAttach() {
            animator.displayingNode = this
        }

        override fun onDetach() {
            animator.reset()
        }

        fun setAnimator(animator: LazyLayoutItemAnimator<*>) {
            if (this.animator != animator) {
                if (node.isAttached) {
                    this.animator.reset()
                    animator.displayingNode = this
                    this.animator = animator
                }
            }
        }
    }
}

private val Any?.specs
    get() = this as? LazyLayoutAnimationSpecsNode

private val EmptyArray = emptyArray<LazyLayoutItemAnimation?>()

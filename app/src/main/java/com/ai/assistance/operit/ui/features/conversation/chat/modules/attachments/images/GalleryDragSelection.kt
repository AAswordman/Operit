package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 手势开始前的选择顺序不变，拖动区间随指尖变化；反向移动会撤销本次超出区间的新增项。 */
internal class GalleryDragSelection(
    private val visibleUris: List<String>,
    private val existing: List<String>,
    private val anchor: Int,
) {
    init { require(anchor in visibleUris.indices) }
    private val existingSet = existing.toSet()

    fun through(index: Int): List<String> {
        require(index in visibleUris.indices)
        val dragged = if (index >= anchor) anchor..index else anchor downTo index
        return existing + dragged.map { visibleUris[it] }.filterNot { it in existingSet }
    }
}

/** 仅拦截占主导的横向拖动，保留纵向图库滚动、单击复选与长按预览。 */
@Composable
internal fun galleryDragSelectionModifier(
    gridState: LazyGridState,
    visibleUris: List<String>,
    selectedUris: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
): Modifier {
    val currentSelection = rememberUpdatedState(selectedUris)
    val currentCallback = rememberUpdatedState(onSelectionChanged)
    var drag by remember(visibleUris) { mutableStateOf<GalleryDragSelection?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val maxStep = with(density) { 18.dp.toPx() }

    fun selectAt(session: GalleryDragSelection, position: Offset) {
        val items = gridState.layoutInfo.visibleItemsInfo
        val index = nearestGalleryIndex(position, items) ?: return
        val next = session.through(index)
        if (next != currentSelection.value) currentCallback.value(next)
    }

    LaunchedEffect(drag, gridState) {
        while (isActive && drag != null) {
            val height = gridState.layoutInfo.viewportSize.height.toFloat()
            val delta = when {
                height <= 0f -> 0f
                pointer.y < edge -> -maxStep * ((edge - pointer.y) / edge).coerceIn(0f, 1f)
                pointer.y > height - edge -> maxStep * ((pointer.y - height + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (delta != 0f) {
                gridState.scrollBy(delta)
                drag?.let { selectAt(it, pointer) }
            }
            delay(16)
        }
    }

    return Modifier.pointerInput(gridState, visibleUris) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val anchor = exactGalleryIndex(down.position, gridState.layoutInfo.visibleItemsInfo)
                ?: return@awaitEachGesture
            val change = awaitTouchSlopOrCancellation(down.id) { moved, slop ->
                if (abs(slop.x) > abs(slop.y)) moved.consume()
            }
            if (change == null || !change.isConsumed) return@awaitEachGesture
            val session = GalleryDragSelection(visibleUris, currentSelection.value, anchor)
            drag = session
            pointer = change.position
            selectAt(session, pointer)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val moved = event.changes.firstOrNull { it.id == down.id } ?: break
                    pointer = moved.position
                    selectAt(session, pointer)
                    moved.consume()
                    if (!moved.pressed) break
                }
            } finally {
                drag = null
            }
        }
    }
}

private fun exactGalleryIndex(position: Offset, items: List<LazyGridItemInfo>): Int? =
    items.firstOrNull { item ->
        position.x >= item.offset.x && position.x < item.offset.x + item.size.width &&
            position.y >= item.offset.y && position.y < item.offset.y + item.size.height
    }?.index

private fun nearestGalleryIndex(position: Offset, items: List<LazyGridItemInfo>): Int? =
    items.minByOrNull { item ->
        val dx = max(item.offset.x - position.x, 0f).coerceAtLeast(
            position.x - item.offset.x - item.size.width,
        )
        val dy = max(item.offset.y - position.y, 0f).coerceAtLeast(
            position.y - item.offset.y - item.size.height,
        )
        dx * dx + dy * dy
    }?.index
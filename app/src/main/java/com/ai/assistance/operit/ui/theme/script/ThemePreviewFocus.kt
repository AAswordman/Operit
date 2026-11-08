package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/** 仅小预览提供定位请求；尺寸变化后重新定位，拖动画布不会生成新请求。 */
internal data class ThemePreviewFocus(
    val targetId: String,
    val viewport: IntSize,
    val available: IntSize,
)

internal val LocalThemePreviewFocus = compositionLocalOf<ThemePreviewFocus?> { null }

/** 用真实部件边界滚动定位，等待布局完成后再请求，避免按估算高度寻找菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.themePreviewFocus(id: String): Modifier {
    val focus = LocalThemePreviewFocus.current ?: return this
    if (focus.targetId != id) return this
    val requester = remember(id) { BringIntoViewRequester() }
    var size by remember(focus) { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(focus, size) {
        if (size != null && focus.available.width > 0 && focus.available.height > 0) {
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester).onGloballyPositioned { size = it.size }
}

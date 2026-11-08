package com.ai.assistance.operit.ui.components.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** 锚定按钮展开的统一菜单，内容可复用 ActionSheetItem，长菜单独立滚动。 */
@Composable
internal fun WorkbenchDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val position = remember(margin) { WorkbenchMenuPositionProvider(margin) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.65f).coerceAtMost(400.dp)
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp).heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp), content = content)
        }
    }
}

/** 水平方向跟随布局方向，垂直方向按窗口空间选择按钮下方或上方。 */
private class WorkbenchMenuPositionProvider(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(0)
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val below = anchorBounds.bottom + margin
        val y = if (below + popupContentSize.height <= windowSize.height - margin) {
            below
        } else {
            anchorBounds.top - popupContentSize.height - margin
        }
        return IntOffset(
            x.coerceIn(minOf(margin, maxX), maxX),
            y.coerceIn(minOf(margin, maxY), maxY),
        )
    }
}

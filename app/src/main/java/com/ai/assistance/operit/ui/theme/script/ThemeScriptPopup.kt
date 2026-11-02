package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.theme.ThemeScriptPopupOffset

/** 聊天弹起菜单与制作预览共用定位；内容尺寸改变后重新计算，不能只平移绘制图层。 */
@Composable
fun ThemeScriptPopupPanel(
    surfaceId: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val style = themeSurface(surfaceId)
    val offset = style?.popupOffset ?: ThemeScriptPopupOffset()
    val originalShape = RoundedCornerShape(8.dp)
    val shape = themeScriptShape(surfaceId, originalShape)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // 原生容器统一合成不透明度，填色和内部装饰不再重复相乘。
            CompositionLocalProvider(LocalThemeHandledOpacity provides (LocalThemeHandledOpacity.current + surfaceId)) {
                Surface(
                    modifier = modifier.themePreviewFocus(surfaceId).alpha(style?.opacity ?: 1f),
                    shape = shape,
                    color = themeScriptFill(surfaceId, containerColor),
                    shadowElevation = style?.elevation?.dp ?: 4.dp,
                ) {
                    ThemeScriptSurface(
                        surfaceId,
                        drawFill = false,
                        drawElevation = false,
                        originalShape = originalShape,
                        content = content,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val window = IntSize(constraints.maxWidth, constraints.maxHeight)
        val margin = IntOffset(12.dp.roundToPx(), 44.dp.roundToPx())
        val panel = measurables.single().measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (window.width - margin.x).coerceAtLeast(0),
                maxHeight = (window.height - margin.y).coerceAtLeast(0),
            ),
        )
        val position = themePopupPosition(
            window,
            IntSize(panel.width, panel.height),
            margin,
            IntOffset(-offset.left.dp.roundToPx(), (offset.down - offset.up).dp.roundToPx()),
            layoutDirection,
        )
        layout(window.width, window.height) { panel.place(position.x, position.y) }
    }
}

/** 左移按物理方向解释；布局和命中区域一起移动，并把完整菜单约束在窗口内。 */
internal fun themePopupPosition(
    window: IntSize,
    panel: IntSize,
    margin: IntOffset,
    shift: IntOffset,
    direction: LayoutDirection,
): IntOffset {
    val maxX = (window.width - panel.width).coerceAtLeast(0)
    val maxY = (window.height - panel.height).coerceAtLeast(0)
    val baseX = if (direction == LayoutDirection.Ltr) maxX - margin.x else margin.x
    return IntOffset(
        (baseX + shift.x).coerceIn(0, maxX),
        (maxY - margin.y + shift.y).coerceIn(0, maxY),
    )
}

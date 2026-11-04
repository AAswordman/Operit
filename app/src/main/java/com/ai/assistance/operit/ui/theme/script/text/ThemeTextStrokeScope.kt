package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.themeScriptTypography
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.theme.script.ThemeTextStroke
import com.ai.assistance.operit.data.theme.script.resolveThemeTextStroke
import com.ai.assistance.operit.ui.theme.script.LocalThemeScriptState
import com.ai.assistance.operit.ui.theme.script.ThemeScriptState

/** 已解析的文字轮廓只参与绘制，不改变文字测量、点击和选择语义。 */
data class ResolvedThemeTextStroke(val color: Color, val widthDp: Float) {
    // 原生富文本同样在整层合成时着色，避免逐段画笔或系统文字绘制改变轮廓色。
    internal val nativeTintPaint = android.graphics.Paint().apply {
        colorFilter = android.graphics.PorterDuffColorFilter(this@ResolvedThemeTextStroke.color.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN)
    }
    internal val tintPaint = androidx.compose.ui.graphics.Paint().apply {
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(this@ResolvedThemeTextStroke.color, androidx.compose.ui.graphics.BlendMode.SrcIn)
    }
}

val LocalThemeTextStroke = compositionLocalOf<ResolvedThemeTextStroke?> { null }

internal fun ThemeTextStroke.resolved(): ResolvedThemeTextStroke =
    ResolvedThemeTextStroke(Color(android.graphics.Color.parseColor(color)), width)

@Composable
fun themeTextStroke(surfaceId: String, parentSurfaceId: String? = null): ResolvedThemeTextStroke? {
    val ready = LocalThemeScriptState.current as? ThemeScriptState.Ready ?: return null
    val declared = ready.appearance.surfaces[surfaceId]?.textStyle?.stroke
        ?: parentSurfaceId?.let { ready.appearance.surfaces[it]?.textStyle?.stroke }
    if (declared == null) return LocalThemeTextStroke.current
    return resolveThemeTextStroke(null, declared)?.resolved()
}

/** 区域没有声明时使用全局描边；显式零宽度阻止继承。 */
@Composable
@UiComposable
fun ThemeTextStrokeScope(surfaceId: String, content: @Composable @UiComposable () -> Unit) {
    CompositionLocalProvider(LocalThemeTextStroke provides themeTextStroke(surfaceId), content = content)
}

/** 消息整体的字体与文字轮廓在同一处进入子树，覆盖两种消息风格。 */
@Composable
@UiComposable
fun ThemeTypographyScope(surfaceId: String, content: @Composable @UiComposable () -> Unit) {
    MaterialTheme(typography = themeScriptTypography(surfaceId, MaterialTheme.typography)) {
        ThemeTextStrokeScope(surfaceId, content)
    }
}

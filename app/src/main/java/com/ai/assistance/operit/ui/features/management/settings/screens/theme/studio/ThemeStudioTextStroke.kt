package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeTextStroke

/** 自定义主题的所有字体入口共用文字描边编辑，普通主题设置不增加控件。 */
@Composable
internal fun StudioTextStrokeProperties(state: ThemeStudioState) {
    val global = state.target.kind == StudioKind.Typography
    val text = if (global) state.appearance.typography else state.surface().textStyle
    val stroke = text.stroke
    val mode = when {
        stroke == null -> if (global) "off" else "inherit"
        stroke.width == 0f -> "off"
        else -> "custom"
    }
    var chooseColor by remember { mutableStateOf(false) }
    val title = state.target.label() + " · " + stringResource(R.string.studio_text_stroke_color) + " · " +
        stringResource(if (state.mode == "light") R.string.studio_light else R.string.studio_dark)
    StudioSection(stringResource(R.string.studio_text_stroke)) {
        val choices = buildList {
            if (!global) add("inherit" to stringResource(R.string.studio_inherit))
            add("off" to stringResource(R.string.studio_text_stroke_off))
            add("custom" to stringResource(R.string.studio_text_stroke_custom))
        }
        StudioChoices(mode, choices) { value ->
            state.updateText { before ->
                before.copy(stroke = when (value) {
                    "inherit" -> null
                    "off" -> if (global) null else (before.stroke ?: ThemeTextStroke(width = 0.1f)).copy(width = 0f)
                    "custom" -> (before.stroke ?: state.appearance.typography.stroke ?: ThemeTextStroke(width = 0.1f)).let {
                        if (it.width == 0f) it.copy(width = 0.1f) else it
                    }
                    else -> error("Unknown text stroke mode: $value")
                })
            }
        }
        if (stroke != null && stroke.width > 0f) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.studio_text_stroke_color), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { chooseColor = true }) {
                    androidx.compose.foundation.layout.Box(Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(stroke.color)), RoundedCornerShape(6.dp)))
                }
            }
            // 细线预设与小数输入共同保留亚像素宽度，高密度屏幕也能从很细的轮廓开始。
            StudioChoices(stroke.width, listOf(0.05f, 0.1f, 0.2f, 0.3f, 0.5f).map { width ->
                width to String.format(java.util.Locale.ROOT, "%.2f dp", width)
            }) { width ->
                state.updateText { before -> before.copy(stroke = requireNotNull(before.stroke).copy(width = width)) }
            }
            StudioSlider(state, stringResource(R.string.studio_stroke_width), stroke.width, 0f..8f, "dp", precision = 2, commitOnFinish = true) { width ->
                state.updateText { before -> before.copy(stroke = requireNotNull(before.stroke).copy(width = width)) }
            }
        }
    }
    if (chooseColor) {
        StudioColorPicker(state, title, stroke?.color, Color.Black,
            onChange = { color -> state.updateText { before -> before.copy(stroke = color?.let { (before.stroke ?: ThemeTextStroke(width = 0.1f)).copy(color = it) }) } },
            onClose = { chooseColor = false },
        )
    }
}

package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.data.theme.script.ThemeAppearanceState
import com.ai.assistance.operit.data.theme.script.ThemeScriptSurface

/** 从零宽度开始选择描边颜色时，同时开启可见描边；零宽度仍可由滑块明确关闭。 */
private fun enabledStudioStrokeWidth(color: String?, width: Float): Float =
    if (color != null && width == 0f) 1f else width

/** 颜色入口只写对应模式或状态，保留其余主题声明。 */
internal fun ThemeScriptSurface.withStudioColor(
    field: String,
    value: String?,
    state: String = "always",
): ThemeScriptSurface {
    if (state != "always") {
        val before = states[state] ?: ThemeAppearanceState()
        val next = when (field) {
            "fillColor" -> before.copy(fillColor = value)
            "contentColor" -> before.copy(contentColor = value)
            "strokeColor" -> before.copy(
                strokeColor = value,
                strokeWidth = if (value != null && (before.strokeWidth ?: strokeWidth) == 0f) {
                    enabledStudioStrokeWidth(value, 0f)
                } else before.strokeWidth,
            )
            else -> error("Unknown state color: $field")
        }
        return copy(states = states + (state to next))
    }
    return when (field) {
        "fillColor" -> copy(fillColor = value)
        // 旧主题的排版色仍可读取；主动编辑统一颜色入口时移除该区域的重复覆盖。
        "contentColor" -> copy(contentColor = value, textStyle = textStyle.copy(color = null))
        "strokeColor" -> copy(strokeColor = value, strokeWidth = enabledStudioStrokeWidth(value, strokeWidth))
        "textColor" -> copy(textStyle = textStyle.copy(color = value))
        else -> error("Unknown surface color: $field")
    }
}
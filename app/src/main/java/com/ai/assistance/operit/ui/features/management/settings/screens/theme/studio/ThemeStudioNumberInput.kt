package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

/** 编辑中的小数点不是已确认的零；延迟提交的控件只在完成编辑后写回主题。 */
internal fun studioNumberInputValue(
    input: String,
    range: ClosedFloatingPointRange<Float>,
    commitOnFinish: Boolean,
    finished: Boolean,
): Float? {
    if (commitOnFinish && !finished) return null
    val text = input.trim()
    if (text.isEmpty() || text.endsWith('.')) return null
    return text.toFloatOrNull()?.takeIf { it.isFinite() && it in range }
}

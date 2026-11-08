package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.Serializable

/** 文字轮廓与容器描边独立；区域声明为 null 时继承，宽度为零时明确关闭。 */
@Serializable
data class ThemeTextStroke(
    val color: String = "#FF000000",
    val width: Float = 1f,
)

/** 显式零宽度会阻止继承，全局未声明时保持原有文字绘制。 */
fun resolveThemeTextStroke(global: ThemeTextStroke?, local: ThemeTextStroke?): ThemeTextStroke? =
    (local ?: global)?.takeIf { it.width > 0f }

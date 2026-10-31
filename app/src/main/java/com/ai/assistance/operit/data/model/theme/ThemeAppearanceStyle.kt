package com.ai.assistance.operit.data.model.theme

import kotlinx.serialization.Serializable

/** 全量配色与部件样式属于角色主题的外观数据，不携带业务事件。 */
@Serializable
data class ThemeAppearancePalette(
    val light: Map<String, String> = emptyMap(),
    val dark: Map<String, String> = emptyMap(),
)

@Serializable
data class ThemeAppearanceText(
    val family: String? = null,
    val scale: Float = 1f,
    val lineHeight: Float = 1f,
    val letterSpacing: Float = 0f,
    val color: String? = null,
)

@Serializable
data class ThemeAppearanceShape(
    val type: String = "rounded",
    val radius: Float = 16f,
)

@Serializable
data class ThemeAppearanceIcon(
    val asset: String,
    val size: Float = 24f,
    val opacity: Float = 1f,
    val useContentTint: Boolean = false,
    val tintColor: String? = null,
)

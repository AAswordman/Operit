package com.ai.assistance.operit.data.preferences.transparency

import androidx.datastore.preferences.core.floatPreferencesKey
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import kotlin.math.roundToInt

/** 背景透明度随角色主题保存；旧主题继续使用原来的背景透明值。 */
object ThemeTransparencyPreferences {
    const val INPUT = "chat_input_opacity"
    const val CURSOR_USER = "cursor_user_bubble_opacity"
    const val CURSOR_AI = "cursor_ai_bubble_opacity"
    const val BUBBLE_USER = "bubble_user_bubble_opacity"
    const val BUBBLE_AI = "bubble_ai_bubble_opacity"

    val names = setOf(INPUT, CURSOR_USER, CURSOR_AI, BUBBLE_USER, BUBBLE_AI)
    val floatKeys = names.map(::floatPreferencesKey)

    fun opacity(values: ThemePreferenceValues, name: String, legacyOpacity: Float): Float =
        (values.float(name) ?: legacyOpacity).coerceIn(0f, 1f)

    fun inputOpacity(
        values: ThemePreferenceValues,
        opaqueOpacity: Float = 1f,
        transparentOpacity: Float = 0f,
    ): Float = opacity(
        values,
        INPUT,
        if (values.boolean("chat_input_transparent") == true) transparentOpacity else opaqueOpacity,
    )

    fun transparencyPercent(opacity: Float): Int =
        ((1f - opacity.coerceIn(0f, 1f)) * 100f).roundToInt()

    fun opacityFromPercent(percent: Int): Float =
        1f - percent.coerceIn(0, 100) / 100f
}

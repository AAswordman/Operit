package com.ai.assistance.operit.data.preferences.messagestyle

import com.ai.assistance.operit.data.preferences.UserPreferencesManager

/** 已发布的单风格主题在读取和导入边界展开；新界面和渲染只消费双方各自的键。 */
object MessageStylePreferences {
    const val LEGACY = "chat_style"
    const val USER = "user_chat_style"
    const val AI = "ai_chat_style"
    val sideKeys: Set<String> = setOf(USER, AI)

    fun expandLegacy(values: Map<String, String>): Map<String, String> {
        val result = values.toMutableMap()
        val previousStyle = values[LEGACY] ?: UserPreferencesManager.CHAT_STYLE_CURSOR
        if (USER !in result) result[USER] = previousStyle
        if (AI !in result) result[AI] = previousStyle
        return result
    }
}
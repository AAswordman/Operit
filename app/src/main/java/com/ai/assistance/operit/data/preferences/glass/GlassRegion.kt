package com.ai.assistance.operit.data.preferences.glass

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues

/** 区域覆盖是角色主题的一部分；关闭覆盖时继续使用已发布版本的全局参数。 */
enum class GlassRegion(val key: String) {
    GLOBAL("glass"),
    USER_MESSAGE("glass_user_message"),
    AI_MESSAGE("glass_ai_message"),
    INPUT("glass_input"),
    SIDEBAR("glass_sidebar"),
    SIDEBAR_BUTTONS("glass_sidebar_buttons"),
    CONVERSATION_LIST("glass_conversation_region"),
    SETTINGS_CARDS("glass_settings_region"),
    PACKAGE_CARDS("glass_package_region");

    val overrideKey: String
        get() = "${key}_custom_parameters"

    fun parameterKey(globalKey: String): String =
        if (this == GLOBAL) globalKey else "${key}_${globalKey.removePrefix("glass_")}"

    fun isCustom(values: ThemePreferenceValues): Boolean =
        this != GLOBAL && values.requiredBoolean(overrideKey)

    fun parameters(values: ThemePreferenceValues): Map<String, Float> {
        val source = if (isCustom(values)) this else GLOBAL
        return GlassThemePreferences.globalFloatDefaults.keys.associateWith { name ->
            values.requiredFloat(source.parameterKey(name))
        }
    }

    fun setCustom(values: ThemePreferenceValues, enabled: Boolean): ThemePreferenceValues {
        require(this != GLOBAL)
        // 开启时以当前生效的参数为编辑起点，避免默认草稿与全局设置不一致。
        val updated =
            if (enabled && !isCustom(values)) {
                values.copy(
                    floats = values.floats + parameters(values).mapKeys { parameterKey(it.key) }
                )
            } else values
        return updated.withBoolean(overrideKey, enabled)
    }
}

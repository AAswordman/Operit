package com.ai.assistance.operit.data.preferences.glass

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

/** 玻璃外观与区域开关随当前角色主题一起保存、复制和重置。 */
object GlassThemePreferences {
    private val areaBooleanDefaults =
        mapOf(
            "glass_conversation_list" to false,
            "glass_settings_cards" to false,
            "glass_package_cards" to false,
        )
    val globalFloatDefaults =
        mapOf(
            "glass_blur_radius" to 12f,
            "glass_edge_width" to 0.4f,
            "glass_highlight_alpha" to 0.18f,
            "glass_shadow_radius" to 4f,
            "glass_tint_opacity" to 0.24f,
        )
    val regions = GlassRegion.values().filter { it != GlassRegion.GLOBAL }
    val booleanDefaults = areaBooleanDefaults + regions.associate { it.overrideKey to false }
    val floatDefaults =
        globalFloatDefaults +
            regions
                .flatMap { region ->
                    globalFloatDefaults.map { (key, value) -> region.parameterKey(key) to value }
                }
                .toMap()
    val booleanKeys = booleanDefaults.keys.map(::booleanPreferencesKey)
    val floatKeys = floatDefaults.keys.map(::floatPreferencesKey)
    val intKeys =
        listOf(
            intPreferencesKey("package_market_button_color"),
            intPreferencesKey("package_add_button_color"),
        )
}

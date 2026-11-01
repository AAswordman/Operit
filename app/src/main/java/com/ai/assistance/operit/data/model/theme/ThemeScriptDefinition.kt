package com.ai.assistance.operit.data.model.theme

import kotlinx.serialization.Serializable

@Serializable
data class ThemeScriptManifest(
    val apiVersion: Int,
    val entry: String,
    val assets: Map<String, String> = emptyMap(),
    val appearance: ThemeScriptAppearance = ThemeScriptAppearance(1),
    val studio: ThemeStudioMetadata = ThemeStudioMetadata(),
)

@Serializable
data class ThemeScriptContext(
    val apiVersion: Int = 1,
    val mode: String,
    val colors: Map<String, String>,
    val settings: ThemeScriptSettings,
    val appearance: ThemeScriptAppearance = ThemeScriptAppearance(1),
)

@Serializable
data class ThemeScriptSettings(
    val strings: Map<String, String>,
    val booleans: Map<String, Boolean>,
    val ints: Map<String, Int>,
    val floats: Map<String, Float>,
)

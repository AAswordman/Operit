package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.Serializable

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.theme.ThemeScriptManifest")
@Serializable
data class ThemeScriptManifest(
    val apiVersion: Int,
    val entry: String,
    val assets: Map<String, String> = emptyMap(),
    val appearance: ThemeScriptAppearance = ThemeScriptAppearance(1),
    val studio: ThemeStudioMetadata = ThemeStudioMetadata(),
)

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.theme.ThemeScriptContext")
@Serializable
data class ThemeScriptContext(
    val apiVersion: Int = 1,
    val mode: String,
    val colors: Map<String, String>,
    val settings: ThemeScriptSettings,
    val appearance: ThemeScriptAppearance = ThemeScriptAppearance(1),
)

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.theme.ThemeScriptSettings")
@Serializable
data class ThemeScriptSettings(
    val strings: Map<String, String>,
    val booleans: Map<String, Boolean>,
    val ints: Map<String, Int>,
    val floats: Map<String, Float>,
)

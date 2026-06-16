package com.ai.assistance.operit.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

data class ThemePreset(
    val id: String,
    val name: String,
    val primaryArgb: Int,
    val secondaryArgb: Int,
    val backgroundMode: String, // "light" | "dark" | "oled"
)

val themePresets = listOf(
    ThemePreset("light-mono", "浅色纯色", 0xFFB0B0B4.toInt(), 0xFF9E9EA3.toInt(), "light"),
    ThemePreset("dark-mono",  "深色纯色", 0xFF636366.toInt(), 0xFF8E8E93.toInt(), "dark"),
    ThemePreset("oled-mono",  "OLED纯黑", 0xFF48484A.toInt(), 0xFF636366.toInt(), "oled"),
)

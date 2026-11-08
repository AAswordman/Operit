package com.ai.assistance.operit.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.theme.OperitDarkColorScheme
import com.ai.assistance.operit.ui.theme.OperitLightColorScheme

@Composable
fun OperitUtilityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) OperitDarkColorScheme else OperitLightColorScheme,
        content = content
    )
}

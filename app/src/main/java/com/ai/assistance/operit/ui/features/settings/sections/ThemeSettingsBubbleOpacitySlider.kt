package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ThemeSettingsOpacitySlider(
    title: String,
    opacity: Float,
    enabled: Boolean = true,
    onOpacityChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = opacity.coerceIn(0f, 1f),
            onValueChange = onOpacityChange,
            valueRange = 0f..1f,
            steps = 99,
            enabled = enabled,
        )
    }
}
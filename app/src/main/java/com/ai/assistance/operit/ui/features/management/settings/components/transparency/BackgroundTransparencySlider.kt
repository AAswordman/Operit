package com.ai.assistance.operit.ui.features.management.settings.components.transparency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSlider
import kotlin.math.roundToInt

/** 只调整背景，文字、图标和附件内容不参与透明度合成。 */
@Composable
internal fun BackgroundTransparencySlider(
    title: String,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
) {
    val percent = ThemeTransparencyPreferences.transparencyPercent(opacity)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.bodyMedium)
        }
        ThemeWorkbenchSlider(
            value = percent.toFloat(),
            onValueChange = {
                onOpacityChange(ThemeTransparencyPreferences.opacityFromPercent(it.roundToInt()))
            },
            valueRange = 0f..100f,
            steps = 99,
        )
    }
}

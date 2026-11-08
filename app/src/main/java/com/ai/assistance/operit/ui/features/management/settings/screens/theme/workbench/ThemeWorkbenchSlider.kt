package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.slider.WorkbenchSlider
import com.ai.assistance.operit.ui.components.slider.WorkbenchRangeSlider

/** 主题与记忆共享轨道外观，原有调用和交互源保持一致。 */
@Composable
internal fun ThemeWorkbenchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = WorkbenchSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, interactionSource)

@Composable
internal fun ThemeWorkbenchRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) = WorkbenchRangeSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished)

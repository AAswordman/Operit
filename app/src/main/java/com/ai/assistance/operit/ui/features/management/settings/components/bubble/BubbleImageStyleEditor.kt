package com.ai.assistance.operit.ui.features.management.settings.components.bubble

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRangeSlider
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSlider
import kotlin.math.abs
import kotlin.math.roundToInt

/** 常用项直接对应视觉效果，保留原参数及草稿机制，高级范围成对更新。 */
@Composable
internal fun BubbleImageStyleEditor(
    title: String,
    enabled: Boolean,
    switchEnabled: Boolean = true,
    description: String,
    onEnabledChange: (Boolean) -> Unit,
    imageUri: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    cropLeft: Float,
    onCropLeftChange: (Float) -> Unit,
    cropTop: Float,
    onCropTopChange: (Float) -> Unit,
    cropRight: Float,
    onCropRightChange: (Float) -> Unit,
    cropBottom: Float,
    onCropBottomChange: (Float) -> Unit,
    repeatXStart: Float,
    repeatXEnd: Float,
    repeatYStart: Float,
    repeatYEnd: Float,
    imageScale: Float,
    onImageScaleChange: (Float) -> Unit,
    contentPaddingLeft: Float,
    contentPaddingRight: Float,
    onStretchRegionChange:
        (ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>) -> Unit,
    onContentPaddingChange: (Float, Float) -> Unit,
) {
    var advanced by rememberSaveable(imageUri) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            Modifier.fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = switchEnabled, onClick = onPickImage),
            shape = RoundedCornerShape(10.dp),
            border =
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            if (!imageUri.isNullOrBlank())
                coil.compose.AsyncImage(
                    imageUri,
                    null,
                    Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            else
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.chat_style_bubble_no_image_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPickImage,
                enabled = switchEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.chat_style_bubble_pick_image))
            }
            if (!imageUri.isNullOrBlank())
                TextButton(onClick = onClearImage) {
                    Text(stringResource(R.string.chat_style_bubble_clear_image))
                }
        }
        if (!switchEnabled)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        if (!imageUri.isNullOrBlank())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = enabled,
                    enabled = switchEnabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        if (enabled) {
            if (!imageUri.isNullOrBlank()) {
                Text(
                    stringResource(R.string.chat_bubble_easy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                BubbleValueSlider(
                    label = stringResource(R.string.chat_bubble_decoration_size),
                    value = imageScale,
                    valueText = "${(imageScale.coerceIn(0.2f, 3f) * 100).roundToInt()}%",
                    range = 0.2f..3f,
                    onValueChange = onImageScaleChange,
                )
                BubbleValueSlider(
                    label = stringResource(R.string.chat_bubble_text_space),
                    value = (contentPaddingLeft + contentPaddingRight) / 2f,
                    valueText =
                        stringResource(
                            R.string.chat_bubble_text_space_value,
                            contentPaddingLeft.roundToInt(),
                            contentPaddingRight.roundToInt(),
                        ),
                    range = 0f..32f,
                    onValueChange = { onContentPaddingChange(it, it) },
                )
                TextButton(
                    onClick = {
                        onImageScaleChange(1f)
                        onContentPaddingChange(12f, 12f)
                    }
                ) {
                    Text(stringResource(R.string.chat_bubble_reset_size))
                }
                Text(
                    stringResource(R.string.chat_bubble_border_presets),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                            0.2f to R.string.chat_bubble_border_small,
                            0.35f to R.string.chat_bubble_border_normal,
                            0.45f to R.string.chat_bubble_border_large,
                        )
                        .forEach { (inset, label) ->
                            FilterChip(
                                selected =
                                    abs(repeatXStart - inset) < 0.005f &&
                                        abs(repeatYStart - inset) < 0.005f &&
                                        abs(repeatXEnd - (1f - inset)) < 0.005f &&
                                        abs(repeatYEnd - (1f - inset)) < 0.005f,
                                onClick = {
                                    onStretchRegionChange(inset..(1f - inset), inset..(1f - inset))
                                },
                                label = { Text(stringResource(label)) },
                            )
                        }
                }
                TextButton(onClick = { advanced = !advanced }) {
                    Text(stringResource(R.string.chat_bubble_advanced))
                    Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                if (advanced) {
                    Text(
                        stringResource(R.string.chat_bubble_advanced_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BubbleRegionSlider(
                        stringResource(R.string.chat_bubble_horizontal_region),
                        repeatXStart,
                        repeatXEnd,
                    ) {
                        onStretchRegionChange(it, validBubbleRegion(repeatYStart, repeatYEnd))
                    }
                    BubbleRegionSlider(
                        stringResource(R.string.chat_bubble_vertical_region),
                        repeatYStart,
                        repeatYEnd,
                    ) {
                        onStretchRegionChange(validBubbleRegion(repeatXStart, repeatXEnd), it)
                    }
                    listOf(
                            Triple(
                                R.string.chat_style_bubble_crop_left,
                                cropLeft,
                                onCropLeftChange,
                            ),
                            Triple(R.string.chat_style_bubble_crop_top, cropTop, onCropTopChange),
                            Triple(
                                R.string.chat_style_bubble_crop_right,
                                cropRight,
                                onCropRightChange,
                            ),
                            Triple(
                                R.string.chat_style_bubble_crop_bottom,
                                cropBottom,
                                onCropBottomChange,
                            ),
                        )
                        .forEach { (label, value, change) ->
                            BubbleValueSlider(
                                stringResource(label),
                                value,
                                "${(value * 100).roundToInt()}%",
                                0f..0.45f,
                                change,
                            )
                        }
                    BubbleValueSlider(
                        stringResource(R.string.chat_style_bubble_padding_left),
                        contentPaddingLeft,
                        "${contentPaddingLeft.roundToInt()}dp",
                        0f..32f,
                        { onContentPaddingChange(it, contentPaddingRight) },
                    )
                    BubbleValueSlider(
                        stringResource(R.string.chat_style_bubble_padding_right),
                        contentPaddingRight,
                        "${contentPaddingRight.roundToInt()}dp",
                        0f..32f,
                        { onContentPaddingChange(contentPaddingLeft, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleValueSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(
                valueText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ThemeWorkbenchSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

private fun validBubbleRegion(start: Float, end: Float): ClosedFloatingPointRange<Float> {
    val left = start.coerceIn(0.05f, 0.9f)
    return left..end.coerceIn(left + 0.01f, 0.95f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BubbleRegionSlider(
    label: String,
    start: Float,
    end: Float,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    val region = validBubbleRegion(start, end)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            "$label  ${(region.start * 100).roundToInt()}% – ${(region.endInclusive * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
        )
        ThemeWorkbenchRangeSlider(
            value = region,
            onValueChange = { onValueChange(validBubbleRegion(it.start, it.endInclusive)) },
            valueRange = 0.05f..0.95f,
        )
    }
}

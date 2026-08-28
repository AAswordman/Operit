package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeBooleanControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeChoicePresentation
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorGroupDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorItemDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorTextKey
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeFloatControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeFloatCommitPolicyV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeFloatFormatV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeStringChoiceDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorValueChangeV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorValueOverridesV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.applyNativeThemeBooleanControlV1
import java.text.NumberFormat

@Composable
internal fun NativeThemeEditorGroupV1(
    definition: NativeThemeEditorGroupDefinitionV1,
    values: ThemePreferenceValues,
    editorSession: ThemeEditorSession,
    onColorRequested: ((NativeThemeColorControlDefinitionV1, Int) -> Unit)? = null,
    onAssetRequested: ((NativeThemeAssetControlDefinitionV1) -> Unit)? = null,
    valueOverrides: NativeThemeEditorValueOverridesV1 = NativeThemeEditorValueOverridesV1(),
    onValueChanged: ((NativeThemeEditorValueChangeV1) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val effectiveValues = valueOverrides.applyTo(values)
    if (!definition.isVisible(effectiveValues)) return
    val visibleItems = definition.visibleItems(effectiveValues)
    check(
        onColorRequested != null ||
            visibleItems.none { item -> item is NativeThemeColorControlDefinitionV1 }
    ) {
        "Theme editor group ${definition.id.value} requires a color picker host."
    }
    check(
        onAssetRequested != null ||
            visibleItems.none { item -> item is NativeThemeAssetControlDefinitionV1 }
    ) {
        "Theme editor group ${definition.id.value} requires an asset picker host."
    }
    val regularItems = visibleItems.filterNot { item -> item.advanced }
    val advancedItems = visibleItems.filter { item -> item.advanced }
    val hasCustomizedAdvanced = advancedItems.any { item -> item.isCustomized(effectiveValues) }
    var advancedExpanded by
        rememberSaveable(definition.id.value) { mutableStateOf(hasCustomizedAdvanced) }
    LaunchedEffect(hasCustomizedAdvanced) {
        if (hasCustomizedAdvanced) advancedExpanded = true
    }
    Column(
        modifier = modifier.fillMaxWidth().animateContentSize().padding(vertical = 12.dp),
    ) {
        Text(
            text = definition.title.localizedText(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        definition.description?.let { description ->
            Text(
                text = description.localizedText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        NativeThemeEditorItemsV1(
            definitions = regularItems,
            values = effectiveValues,
            editorSession = editorSession,
            onColorRequested = onColorRequested,
            onAssetRequested = onAssetRequested,
            onValueChanged = onValueChanged,
        )

        if (advancedItems.isNotEmpty()) {
            val advancedStateDescription =
                if (advancedExpanded) {
                    NativeThemeEditorTextKey.EXPANDED.localizedText()
                } else {
                    NativeThemeEditorTextKey.COLLAPSED.localizedText()
                }
            TextButton(
                onClick = { advancedExpanded = !advancedExpanded },
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .semantics { stateDescription = advancedStateDescription },
            ) {
                Text(text = NativeThemeEditorTextKey.ADVANCED_SETTINGS.localizedText())
                Icon(
                    imageVector =
                        if (advancedExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                    contentDescription = null,
                )
            }
            AnimatedVisibility(visible = advancedExpanded) {
                NativeThemeEditorItemsV1(
                    definitions = advancedItems,
                    values = effectiveValues,
                    editorSession = editorSession,
                    onColorRequested = onColorRequested,
                    onAssetRequested = onAssetRequested,
                    onValueChanged = onValueChanged,
                )
            }
        }
    }
}

@Composable
private fun NativeThemeEditorItemsV1(
    definitions: List<NativeThemeEditorItemDefinitionV1>,
    values: ThemePreferenceValues,
    editorSession: ThemeEditorSession,
    onColorRequested: ((NativeThemeColorControlDefinitionV1, Int) -> Unit)?,
    onAssetRequested: ((NativeThemeAssetControlDefinitionV1) -> Unit)?,
    onValueChanged: ((NativeThemeEditorValueChangeV1) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        definitions.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            NativeThemeEditorItemV1(
                definition = item,
                values = values,
                editorSession = editorSession,
                onColorRequested = onColorRequested,
                onAssetRequested = onAssetRequested,
                onValueChanged = onValueChanged,
                modifier = Modifier.padding(top = if (index == 0) 12.dp else 4.dp),
            )
        }
    }
}

@Composable
private fun NativeThemeEditorItemV1(
    definition: NativeThemeEditorItemDefinitionV1,
    values: ThemePreferenceValues,
    editorSession: ThemeEditorSession,
    onColorRequested: ((NativeThemeColorControlDefinitionV1, Int) -> Unit)?,
    onAssetRequested: ((NativeThemeAssetControlDefinitionV1) -> Unit)?,
    onValueChanged: ((NativeThemeEditorValueChangeV1) -> Unit)?,
    modifier: Modifier,
) {
    when (definition) {
        is NativeThemeBooleanControlDefinitionV1 ->
            NativeThemeBooleanControlV1(
                definition = definition,
                checked = values.requiredBoolean(definition.field),
                onCheckedChange = { checked ->
                    if (onValueChanged != null) {
                        onValueChanged(
                            NativeThemeEditorValueChangeV1.BooleanChanged(definition, checked),
                        )
                    } else {
                        editorSession.update { current ->
                            applyNativeThemeBooleanControlV1(current, definition, checked)
                        }
                    }
                },
                modifier = modifier,
            )

        is NativeThemeColorControlDefinitionV1 -> {
            val color = definition.target.displayColor(values, definition.displayDefault)
            NativeThemeColorControlV1(
                definition = definition,
                color = color,
                onClick = {
                    onColorRequested?.invoke(definition, color)
                },
                modifier = modifier,
            )
        }

        is NativeThemeFloatControlDefinitionV1 ->
            NativeThemeFloatControlV1(
                definition = definition,
                value = values.requiredFloat(definition.field),
                onValueChange = { value, finished ->
                    if (onValueChanged != null) {
                        onValueChanged(
                            NativeThemeEditorValueChangeV1.FloatChanged(
                                definition = definition,
                                value = value,
                                finished = finished,
                            ),
                        )
                    } else if (
                        definition.commitPolicy == NativeThemeFloatCommitPolicyV1.IMMEDIATE ||
                            finished
                    ) {
                        editorSession.setFloat(definition.field, value)
                    }
                },
                modifier = modifier,
            )

        is NativeThemeAssetControlDefinitionV1 ->
            NativeThemeAssetControlV1(
                definition = definition,
                value = values.string(definition.field),
                selectLabel = definition.selectLabel(values),
                onSelect = {
                    onAssetRequested?.invoke(definition)
                },
                onClear = { editorSession.setOptionalString(definition.field, null) },
                modifier = modifier,
            )

        is NativeThemeStringChoiceDefinitionV1 ->
            NativeThemeStringChoiceV1(
                definition = definition,
                selectedValue = values.requiredString(definition.field),
                onSelected = { value ->
                    if (onValueChanged != null) {
                        onValueChanged(
                            NativeThemeEditorValueChangeV1.StringChanged(definition, value),
                        )
                    } else {
                        editorSession.setString(definition.field, value)
                    }
                },
                modifier = modifier,
            )
    }
}

@Composable
private fun NativeThemeFloatControlV1(
    definition: NativeThemeFloatControlDefinitionV1,
    value: Float,
    onValueChange: (Float, Boolean) -> Unit,
    modifier: Modifier,
) {
    var sliderValue by
        remember(definition.id.value) {
            mutableStateOf(value.coerceIn(definition.minimum, definition.maximum))
        }
    LaunchedEffect(value, definition.id.value) {
        sliderValue = value.coerceIn(definition.minimum, definition.maximum)
    }
    val formattedValue =
        when (definition.format) {
            NativeThemeFloatFormatV1.DECIMAL_ONE ->
                NumberFormat.getNumberInstance().apply {
                    minimumFractionDigits = 1
                    maximumFractionDigits = 1
                    isGroupingUsed = false
                }.format(value)
            NativeThemeFloatFormatV1.DECIMAL_UP_TO_TWO ->
                NumberFormat.getNumberInstance().apply {
                    minimumFractionDigits = 1
                    maximumFractionDigits = 2
                    isGroupingUsed = false
                }.format(value)
            NativeThemeFloatFormatV1.INTEGER -> value.toInt().toString()
            NativeThemeFloatFormatV1.PERCENT_INTEGER -> (value * 100f).toInt().toString()
        }
    val sliderLabel =
        when (definition.format) {
            NativeThemeFloatFormatV1.PERCENT_INTEGER ->
                definition.title.localizedText((value * 100f).toInt())
            NativeThemeFloatFormatV1.INTEGER ->
                "${definition.title.localizedText()}: $formattedValue"
            else -> definition.title.localizedText(formattedValue)
        }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (definition.displayTitle) {
            Text(text = sliderLabel, style = MaterialTheme.typography.bodyLarge)
        }
        definition.description?.let { description ->
            Text(
                text = description.localizedText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { changedValue ->
                sliderValue = changedValue
                onValueChange(changedValue, false)
            },
            onValueChangeFinished = { onValueChange(sliderValue, true) },
            valueRange = definition.minimum..definition.maximum,
            steps = definition.steps,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = sliderLabel
                        stateDescription = formattedValue
                    },
        )
    }
}

@Composable
private fun NativeThemeAssetControlV1(
    definition: NativeThemeAssetControlDefinitionV1,
    value: String?,
    selectLabel: NativeThemeEditorTextKey,
    onSelect: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    val hasValue = value != null && value.isNotEmpty()
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (definition.displayTitle) {
            Text(text = definition.title.localizedText(), style = MaterialTheme.typography.bodyLarge)
        }
        definition.description?.let { description ->
            Text(
                text = description.localizedText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeThemeAssetSelectButtonV1(
                        definition = definition,
                        label = selectLabel,
                        onClick = onSelect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hasValue) {
                        definition.clearLabel?.let { clearLabel ->
                            NativeThemeAssetClearButtonV1(
                                definition = definition,
                                label = clearLabel,
                                onClick = onClear,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NativeThemeAssetSelectButtonV1(
                        definition = definition,
                        label = selectLabel,
                        onClick = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasValue) {
                        definition.clearLabel?.let { clearLabel ->
                            NativeThemeAssetClearButtonV1(
                                definition = definition,
                                label = clearLabel,
                                onClick = onClear,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        value?.takeIf { it.isNotEmpty() }?.let { currentValue ->
            val valueLabel = definition.currentValueLabel ?: return@let
            Text(
                text =
                    valueLabel.localizedText(
                        currentValue.substringAfterLast("/"),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NativeThemeAssetSelectButtonV1(
    definition: NativeThemeAssetControlDefinitionV1,
    label: NativeThemeEditorTextKey,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(
            text = label.localizedText(),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun NativeThemeAssetClearButtonV1(
    definition: NativeThemeAssetControlDefinitionV1,
    label: NativeThemeEditorTextKey,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(Icons.Default.Clear, contentDescription = null)
        Text(
            text = label.localizedText(),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun NativeThemeColorControlV1(
    definition: NativeThemeColorControlDefinitionV1,
    color: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colorState = "#%08X".format(color)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { stateDescription = colorState }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (definition.displayTitle) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = definition.title.localizedText(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                definition.description?.let { description ->
                    Text(
                        text = description.localizedText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        }
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

@Composable
private fun NativeThemeBooleanControlV1(
    definition: NativeThemeBooleanControlDefinitionV1,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (definition.displayTitle) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = definition.title.localizedText(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                definition.description?.let { description ->
                    Text(
                        text = description.localizedText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        } else {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeThemeStringChoiceV1(
    definition: NativeThemeStringChoiceDefinitionV1,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier,
) {
    val title = definition.title.localizedText()
    Column(modifier = modifier.fillMaxWidth()) {
        if (definition.displayTitle) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        definition.description?.let { description ->
            Text(
                text = description.localizedText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        when (definition.presentation) {
            NativeThemeChoicePresentation.SEGMENTED ->
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useRadioLayout = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f
                    if (useRadioLayout) {
                        NativeThemeRadioChoiceV1(
                            definition = definition,
                            selectedValue = selectedValue,
                            onSelected = onSelected,
                        )
                    } else {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            definition.options.forEachIndexed { index, option ->
                                SegmentedButton(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .defaultMinSize(minWidth = 0.dp)
                                            .heightIn(min = 64.dp),
                                    selected = selectedValue == option.value,
                                    onClick = { onSelected(option.value) },
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = definition.options.size,
                                        ),
                                    label = {
                                        Text(
                                            text = option.title.localizedText(),
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            textAlign = TextAlign.Center,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

            NativeThemeChoicePresentation.RADIO ->
                NativeThemeRadioChoiceV1(
                    definition = definition,
                    selectedValue = selectedValue,
                    onSelected = onSelected,
                )
        }
    }
}

@Composable
private fun NativeThemeRadioChoiceV1(
    definition: NativeThemeStringChoiceDefinitionV1,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
    ) {
        definition.options.forEach { option ->
            val selected = selectedValue == option.value
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelected(option.value) },
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(text = option.title.localizedText())
            }
        }
    }
}

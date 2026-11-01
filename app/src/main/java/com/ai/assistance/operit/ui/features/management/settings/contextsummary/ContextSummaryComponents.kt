package com.ai.assistance.operit.ui.features.management.settings.contextsummary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.SummarySectionConfig

@Composable
internal fun RenderContextSummaryConfigSections(
    componentBackgroundColor: Color,
    contextLengthInput: String,
    onContextLengthInputChange: (String) -> Unit,
    maxContextLengthInput: String,
    onMaxContextLengthInputChange: (String) -> Unit,
    contextError: String?,
    enableSummary: Boolean,
    onEnableSummaryChange: (Boolean) -> Unit,
    summaryTokenThresholdInput: String,
    onSummaryTokenThresholdInputChange: (String) -> Unit,
    enableSummaryByMessageCount: Boolean,
    onEnableSummaryByMessageCountChange: (Boolean) -> Unit,
    summaryMessageCountThresholdInput: String,
    onSummaryMessageCountThresholdInputChange: (String) -> Unit,
    summaryCustomRulesInput: String,
    onSummaryCustomRulesInputChange: (String) -> Unit,
    previousSummaryReviewEnabled: Boolean,
    onPreviousSummaryReviewEnabledChange: (Boolean) -> Unit,
    dialogueReviewEnabled: Boolean,
    onDialogueReviewEnabledChange: (Boolean) -> Unit,
    dialogueReviewTitleInput: String,
    onDialogueReviewTitleChange: (String) -> Unit,
    summarySectionsInput: List<SummarySectionConfig>,
    onSummarySectionsInputChange: (List<SummarySectionConfig>) -> Unit,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit,
    summaryError: String?,
    summaryModelContent: @Composable () -> Unit,
) {
    SectionTitle(
        text = stringResource(id = R.string.settings_context_title),
        icon = Icons.Default.Analytics
    )
    SettingsInfoBanner(
        text = stringResource(id = R.string.settings_context_card_content),
        backgroundColor = componentBackgroundColor
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_context_length),
        subtitle = stringResource(id = R.string.settings_context_length_subtitle),
        value = contextLengthInput,
        onValueChange = onContextLengthInputChange,
        unitText = "K",
        backgroundColor = componentBackgroundColor,
        allowDecimal = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_max_context_length),
        subtitle = stringResource(id = R.string.settings_max_context_length_subtitle),
        value = maxContextLengthInput,
        onValueChange = onMaxContextLengthInputChange,
        unitText = "K",
        backgroundColor = componentBackgroundColor,
        allowDecimal = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
    )
    contextError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    Spacer(modifier = Modifier.size(8.dp))
    SectionTitle(
        text = stringResource(id = R.string.settings_summary_title),
        icon = Icons.Default.Summarize
    )
    SettingsSwitchRow(
        title = stringResource(id = R.string.settings_enable_summary),
        subtitle = stringResource(id = R.string.settings_enable_summary_desc),
        checked = enableSummary,
        onCheckedChange = onEnableSummaryChange,
        backgroundColor = componentBackgroundColor
    )
    SettingsInputField(
        title = stringResource(id = R.string.model_workspace_summary_threshold),
        subtitle = stringResource(id = R.string.model_workspace_summary_threshold_hint),
        unitText = "%",
        value = summaryTokenThresholdInput,
        onValueChange = onSummaryTokenThresholdInputChange,
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary,
        allowDecimal = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    )
    SettingsSwitchRow(
        title = stringResource(id = R.string.settings_enable_summary_by_message_count),
        subtitle = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
        checked = enableSummaryByMessageCount,
        onCheckedChange = onEnableSummaryByMessageCountChange,
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_summary_message_count_threshold),
        subtitle = stringResource(id = R.string.settings_summary_message_count_threshold_subtitle),
        value = summaryMessageCountThresholdInput,
        onValueChange = onSummaryMessageCountThresholdInputChange,
        unitText = stringResource(id = R.string.model_config_unit_items),
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary && enableSummaryByMessageCount
    )
    summaryError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    Spacer(modifier = Modifier.size(8.dp))
    summaryModelContent()
    Spacer(modifier = Modifier.size(12.dp))
    val globalRulesTitle = stringResource(id = R.string.settings_summary_custom_rules)
    GlobalSummaryRulesEditor(
        title = globalRulesTitle,
        subtitle = stringResource(id = R.string.settings_summary_custom_rules_desc),
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary,
        value = summaryCustomRulesInput,
        onValueChange = onSummaryCustomRulesInputChange,
        onOpenFullscreenEditor = onOpenFullscreenEditor
    )
    SettingsSwitchRow(
        title = stringResource(id = R.string.settings_summary_previous_review),
        subtitle = stringResource(id = R.string.settings_summary_previous_review_desc),
        checked = previousSummaryReviewEnabled,
        onCheckedChange = onPreviousSummaryReviewEnabledChange,
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary
    )
    Spacer(modifier = Modifier.size(12.dp))
    SectionTitle(
        text = stringResource(id = R.string.settings_summary_structure),
        icon = Icons.Default.Summarize
    )
    summarySectionsInput.forEachIndexed { index, section ->
        SummarySectionEditor(
            section = section,
            onSectionChange = { updatedSection ->
                onSummarySectionsInputChange(
                    summarySectionsInput.mapIndexed { currentIndex, currentSection ->
                        if (currentIndex == index) updatedSection else currentSection
                    }
                )
            },
            backgroundColor = componentBackgroundColor,
            enabled = enableSummary,
            onOpenFullscreenEditor = onOpenFullscreenEditor
        )
    }
    DialogueReviewEditor(
        enabled = dialogueReviewEnabled,
        onEnabledChange = onDialogueReviewEnabledChange,
        title = dialogueReviewTitleInput,
        onTitleChange = onDialogueReviewTitleChange,
        backgroundColor = componentBackgroundColor,
        summaryEnabled = enableSummary
    )
}

@Composable
private fun GlobalSummaryRulesEditor(
    title: String,
    subtitle: String,
    backgroundColor: Color,
    enabled: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SummaryEditorHeader(
        title = title,
        subtitle = subtitle,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        backgroundColor = backgroundColor,
        enabled = enabled
    )
    AnimatedVisibility(visible = expanded) {
        SettingsMultilineTextField(
            title = title,
            subtitle = subtitle,
            value = value,
            onValueChange = onValueChange,
            backgroundColor = backgroundColor,
            enabled = enabled,
            onOpenFullscreen = {
                onOpenFullscreenEditor(title, value, onValueChange)
            }
        )
    }
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
private fun SummarySectionEditor(
    section: SummarySectionConfig,
    onSectionChange: (SummarySectionConfig) -> Unit,
    backgroundColor: Color,
    enabled: Boolean,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit
) {
    var expanded by rememberSaveable(section.id) { mutableStateOf(false) }
    SummaryConfigEditorHeader(
        title = section.title,
        subtitle = stringResource(id = R.string.settings_summary_section_enabled_desc),
        checked = section.enabled,
        onCheckedChange = { onSectionChange(section.copy(enabled = it)) },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        backgroundColor = backgroundColor,
        enabled = enabled
    )
    AnimatedVisibility(visible = expanded) {
        Column {
            SettingsMultilineTextField(
                title = stringResource(id = R.string.settings_summary_section_title),
                subtitle = stringResource(id = R.string.settings_summary_section_title_desc),
                value = section.title,
                onValueChange = { onSectionChange(section.copy(title = it)) },
                backgroundColor = backgroundColor,
                enabled = enabled && section.enabled,
                singleLine = true,
                minHeight = 40.dp
            )
            SettingsMultilineTextField(
                title = stringResource(id = R.string.settings_summary_section_instruction),
                subtitle = stringResource(id = R.string.settings_summary_section_instruction_desc),
                value = section.instruction,
                onValueChange = { onSectionChange(section.copy(instruction = it)) },
                backgroundColor = backgroundColor,
                enabled = enabled && section.enabled,
                onOpenFullscreen = {
                    onOpenFullscreenEditor(section.title, section.instruction) { value ->
                        onSectionChange(section.copy(instruction = value))
                    }
                }
            )
        }
    }
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
private fun DialogueReviewEditor(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    backgroundColor: Color,
    summaryEnabled: Boolean
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SummaryConfigEditorHeader(
        title = stringResource(id = R.string.settings_summary_dialogue_review),
        subtitle = stringResource(id = R.string.settings_summary_dialogue_review_desc),
        checked = enabled,
        onCheckedChange = onEnabledChange,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        backgroundColor = backgroundColor,
        enabled = summaryEnabled
    )
    AnimatedVisibility(visible = expanded) {
        SettingsMultilineTextField(
            title = stringResource(id = R.string.settings_summary_dialogue_review_title),
            subtitle = stringResource(id = R.string.settings_summary_dialogue_review_title_desc),
            value = title,
            onValueChange = onTitleChange,
            backgroundColor = backgroundColor,
            enabled = summaryEnabled && enabled,
            singleLine = true,
            minHeight = 40.dp
        )
    }
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
private fun SummaryConfigEditorHeader(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .alpha(contentAlpha)
                .clickable(enabled = enabled) { onExpandedChange(!expanded) }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        IconButton(onClick = { onExpandedChange(!expanded) }, enabled = enabled) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.model_config_collapse else R.string.model_config_expand
                )
            )
        }
    }
}

@Composable
private fun SummaryEditorHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .alpha(contentAlpha)
                .clickable(enabled = enabled) { onExpandedChange(!expanded) }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onExpandedChange(!expanded) }, enabled = enabled) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.model_config_collapse else R.string.model_config_expand
                )
            )
        }
    }
}

@Composable
private fun SettingsInfoBanner(text: String, backgroundColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsInputField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    unitText: String? = null,
    backgroundColor: Color,
    enabled: Boolean = true,
    allowDecimal: Boolean = false,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
) {
    val focusManager = LocalFocusManager.current
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = { newText ->
                    if (enabled) {
                        onValueChange(if (allowDecimal) filterDecimalInput(newText) else newText.filter { it.isDigit() })
                    }
                },
                enabled = enabled,
                modifier =
                    Modifier.width(if (allowDecimal) 88.dp else 72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                textStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onDone = { if (enabled) focusManager.clearFocus() }),
                singleLine = true
            )
            if (unitText != null) {
                Text(
                    text = unitText,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

private fun filterDecimalInput(input: String): String {
    var dotSeen = false
    val result = StringBuilder()
    input.forEachIndexed { index, c ->
        when {
            c.isDigit() -> result.append(c)
            c == '.' && !dotSeen -> {
                dotSeen = true
                if (result.isEmpty() && index == 0) result.append("0.") else result.append(c)
            }
        }
    }
    return result.toString()
}

@Composable
private fun SettingsMultilineTextField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minHeight: Dp = 80.dp,
    onOpenFullscreen: (() -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = { newText ->
                if (enabled) {
                    onValueChange(newText)
                }
            },
            enabled = enabled,
            modifier =
                Modifier.fillMaxWidth()
                    .let { if (singleLine) it.heightIn(min = minHeight) else it.heightIn(min = 120.dp) }
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 5,
            maxLines = if (singleLine) 1 else 5,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentAlignment =
                            if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                    if (!singleLine && onOpenFullscreen != null) {
                        IconButton(
                            onClick = onOpenFullscreen,
                            enabled = enabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(id = R.string.chat_fullscreen_input),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

internal data class FullscreenTextEditorRequest(
    val title: String,
    val value: String,
    val onValueChange: (String) -> Unit
)

@Composable
internal fun FullscreenSettingsTextEditor(
    request: FullscreenTextEditorRequest,
    onDismiss: () -> Unit
) {
    var editorValue by remember(request.title, request.value) { mutableStateOf(request.value) }
    fun finishEditing() {
        request.onValueChange(editorValue)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().imePadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.common_close)
                        )
                    }
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { finishEditing() }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.save)
                        )
                    }
                }
                HorizontalDivider()
                TextField(
                    value = editorValue,
                    onValueChange = { editorValue = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

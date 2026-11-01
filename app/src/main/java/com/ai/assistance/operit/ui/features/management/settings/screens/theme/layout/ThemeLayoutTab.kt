package com.ai.assistance.operit.ui.features.management.settings.screens.theme.layout

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDivider
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSlider
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

internal enum class ThemeLayoutField(
    val title: Int,
    val description: Int,
    val range: ClosedFloatingPointRange<Float>,
    val unit: String,
    val value: (ChatLayoutSettings) -> Float,
    val change: (ChatLayoutSettings, Float) -> ChatLayoutSettings,
) {
    BUTTON(
        R.string.theme_layout_button,
        R.string.theme_layout_button_desc,
        0f..50f,
        "dp",
        ChatLayoutSettings::chatSettingsButtonEndPadding,
        { settings, value -> settings.copy(chatSettingsButtonEndPadding = value) },
    ),
    HORIZONTAL(
        R.string.theme_layout_horizontal,
        R.string.theme_layout_horizontal_desc,
        0f..50f,
        "dp",
        ChatLayoutSettings::chatAreaHorizontalPadding,
        { settings, value -> settings.copy(chatAreaHorizontalPadding = value) },
    ),
    LINE_HEIGHT(
        R.string.theme_layout_line,
        R.string.theme_layout_line_desc,
        0.8f..2f,
        "×",
        ChatLayoutSettings::aiMarkdownLineHeightMultiplier,
        { settings, value -> settings.copy(aiMarkdownLineHeightMultiplier = value) },
    ),
    LETTER_SPACING(
        R.string.theme_layout_letter,
        R.string.theme_layout_letter_desc,
        -1f..8f,
        "sp",
        ChatLayoutSettings::aiMarkdownLetterSpacing,
        { settings, value -> settings.copy(aiMarkdownLetterSpacing = value) },
    ),
    PARAGRAPH_SPACING(
        R.string.theme_layout_paragraph,
        R.string.theme_layout_paragraph_desc,
        0f..48f,
        "dp",
        ChatLayoutSettings::aiMarkdownParagraphSpacing,
        { settings, value -> settings.copy(aiMarkdownParagraphSpacing = value) },
    );

    val default: Float
        get() = value(ChatLayoutSettings())
}

private fun layoutNumber(value: Float): String =
    DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT)).format(value)

@Composable
internal fun ThemeLayoutScopeCard() {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Public,
                null,
                Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.theme_layout_global_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.theme_layout_global_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 本页只更新全局布局草稿；保存、撤销和离开确认由现有主题会话统一负责。 */
@Composable
internal fun ThemeLayoutTab(editorSession: ThemeEditorSession) {
    val settings by editorSession.layout.collectAsState()
    var editing by rememberSaveable { mutableStateOf<ThemeLayoutField?>(null) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    ThemeWorkbenchCard(
        stringResource(R.string.theme_layout_chat_spacing),
        icon = Icons.Default.Tune,
        subtitle = stringResource(R.string.theme_layout_chat_spacing_desc),
    ) {
        listOf(ThemeLayoutField.BUTTON, ThemeLayoutField.HORIZONTAL).forEachIndexed { index, field
            ->
            if (index > 0) ThemeWorkbenchDivider()
            ThemeLayoutControl(field, field.value(settings), { editing = field }) { value ->
                editorSession.updateLayout { field.change(it, value) }
            }
        }
    }
    ThemeWorkbenchCard(
        stringResource(R.string.theme_layout_ai_typography),
        icon = Icons.Default.FormatSize,
        subtitle = stringResource(R.string.theme_layout_ai_typography_desc),
    ) {
        listOf(
                ThemeLayoutField.LINE_HEIGHT,
                ThemeLayoutField.LETTER_SPACING,
                ThemeLayoutField.PARAGRAPH_SPACING,
            )
            .forEachIndexed { index, field ->
                if (index > 0) ThemeWorkbenchDivider()
                ThemeLayoutControl(field, field.value(settings), { editing = field }) { value ->
                    editorSession.updateLayout { field.change(it, value) }
                }
            }
    }
    Text(
        stringResource(R.string.theme_layout_input_hint),
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = { showReset = true }) {
            Icon(Icons.Default.RestartAlt, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.theme_layout_reset))
        }
    }
    editing?.let { field ->
        key(field) {
            ThemeLayoutNumberDialog(
                field,
                field.value(settings),
                onDismiss = { editing = null },
                onApply = { value ->
                    editorSession.updateLayout { field.change(it, value) }
                    editing = null
                },
            )
        }
    }
    if (showReset)
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.theme_layout_reset_title)) },
            text = { Text(stringResource(R.string.theme_layout_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        editorSession.resetLayout()
                        showReset = false
                    }
                ) {
                    Text(stringResource(R.string.reset_to_default))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
}

@Composable
private fun ThemeLayoutControl(
    field: ThemeLayoutField,
    current: Float,
    onEdit: () -> Unit,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(field.title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onEdit,
                shape = RoundedCornerShape(9.dp),
                colors =
                    ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp),
            ) {
                Text("${layoutNumber(current)} ${field.unit}")
            }
        }
        Text(
            stringResource(field.description),
            Modifier.padding(top = 3.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThemeWorkbenchSlider(
            current,
            onValueChange = { onChange((it * 100).roundToInt() / 100f) },
            valueRange = field.range,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.theme_layout_range,
                    layoutNumber(field.range.start),
                    layoutNumber(field.range.endInclusive),
                    field.unit,
                    layoutNumber(field.default),
                ),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onChange(field.default) },
                enabled = current != field.default,
                contentPadding = PaddingValues(start = 8.dp),
            ) {
                Text(
                    stringResource(R.string.reset_to_default),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ThemeLayoutNumberDialog(
    field: ThemeLayoutField,
    value: Float,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(layoutNumber(value)) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val applyValue = {
        val parsed = text.toFloatOrNull()
        if (parsed == null || !parsed.isFinite() || parsed !in field.range) invalid = true
        else onApply(parsed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(field.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(field.description), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        invalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = invalid,
                    suffix = { Text(field.unit) },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { applyValue() }),
                    supportingText = {
                        Text(
                            stringResource(
                                if (invalid) R.string.theme_layout_invalid
                                else R.string.theme_layout_range,
                                layoutNumber(field.range.start),
                                layoutNumber(field.range.endInclusive),
                                field.unit,
                                layoutNumber(field.default),
                            )
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { applyValue() }) {
                Text(stringResource(R.string.theme_layout_apply_value))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

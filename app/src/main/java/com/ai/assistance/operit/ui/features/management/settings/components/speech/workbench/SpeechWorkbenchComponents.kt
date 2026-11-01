package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.stt.SttServiceFactory.SpeechServiceType
import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory.VoiceServiceType

internal data class SpeechChoice(val id: String, val title: String, val description: String = "")

@Composable
internal fun SpeechWorkbenchCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun SpeechSectionLabel(title: String, detail: String = "") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SpeechWorkbenchField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String = "",
    secret: Boolean = false,
    multiline: Boolean = false,
    enabled: Boolean = true,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.3f else 0.15f),
            border = BorderStroke(0.7.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = !multiline,
                    minLines = if (multiline) 3 else 1,
                    maxLines = if (multiline) 8 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
                    visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f)),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp).semantics { contentDescription = label },
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            inner()
                        }
                    },
                )
                if (secret) IconButton(onClick = { visible = !visible }, enabled = enabled) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.speech_workbench_secret_visibility), Modifier.size(19.dp))
                }
                trailing?.invoke()
            }
        }
        if (error != null || hint.isNotBlank()) Text(
            error ?: hint,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SpeechWorkbenchRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    description: String = "",
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
            Icon(icon, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, Modifier.widthIn(max = 160.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SpeechWorkbenchFold(title: String, detail: String = "", content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, stringResource(if (expanded) R.string.collapse else R.string.expand), Modifier.size(20.dp))
        }
        if (expanded) content()
    }
}

/** 手机使用底部面板；宽窗口沿用同一内容，避免拉成长条菜单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeechWorkbenchSheet(
    title: String,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val canDismiss by rememberUpdatedState(dismissEnabled)
    val dismiss = { if (canDismiss) onDismiss() }
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.8f).dp).imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = dismiss, enabled = dismissEnabled) { Icon(Icons.Default.Close, stringResource(android.R.string.cancel)) }
            }
            if (scrollable) Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            else content()
        }
    }
    if (configuration.screenWidthDp >= 600) {
        Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { body() }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { canDismiss || it != SheetValue.Hidden })
        ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) { body() }
    }
}

@Composable
internal fun SpeechChoicePanel(
    title: String,
    choices: List<SpeechChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    enabled: Boolean = true,
    dismissEnabled: Boolean = true,
    error: String? = null,
    onRefresh: (() -> Unit)? = null,
    manualValue: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(manualValue.orEmpty()) }
    var manualEditing by rememberSaveable { mutableStateOf(false) }
    val filtered = choices.filter { it.title.contains(query, true) || it.id.contains(query, true) || it.description.contains(query, true) }
    SpeechWorkbenchSheet(title, onDismiss, dismissEnabled = dismissEnabled, scrollable = manualEditing) {
        if (manualEditing) {
            SpeechWorkbenchField(stringResource(R.string.speech_workbench_manual_value), manual, { manual = it })
            Button(onClick = { onSelect(manual) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.speech_workbench_use_value)) }
            TextButton(onClick = { manualEditing = false }) { Text(stringResource(R.string.search)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeechWorkbenchField(stringResource(R.string.search), query, { query = it }, Modifier.weight(1f), placeholder = stringResource(R.string.speech_workbench_search_hint))
                if (onRefresh != null) IconButton(onClick = onRefresh, enabled = enabled && !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.id }) { choice ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().selectable(selected = choice.id == selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(choice.id) }),
                        shape = RoundedCornerShape(10.dp),
                        color = if (choice.id == selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(Modifier.heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(choice.title, style = MaterialTheme.typography.bodyMedium)
                                if (choice.description.isNotBlank()) Text(choice.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (choice.id == selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (filtered.isEmpty() && !loading) item { Text(stringResource(R.string.speech_workbench_empty_choices), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (manualValue != null) TextButton(onClick = { manualEditing = true }, enabled = enabled, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.speech_workbench_manual_value)) }
            footer?.invoke(this)
        }
    }
}

internal fun VoiceServiceType.titleRes(): Int = when (this) {
    VoiceServiceType.SIMPLE_TTS -> R.string.speech_services_tts_type_simple
    VoiceServiceType.HTTP_TTS -> R.string.speech_services_tts_type_http
    VoiceServiceType.OPENAI_WS_TTS -> R.string.speech_services_tts_type_openai_ws
    VoiceServiceType.SILICONFLOW_TTS -> R.string.speech_services_tts_type_siliconflow
    VoiceServiceType.MINIMAX_TTS -> R.string.speech_services_tts_type_minimax
    VoiceServiceType.MIMO_TTS -> R.string.speech_services_tts_type_mimo
    VoiceServiceType.DOUBAO_TTS -> R.string.speech_services_tts_type_doubao
    VoiceServiceType.OPENAI_TTS -> R.string.speech_services_tts_type_openai
    VoiceServiceType.SHERPA_ONNX_TTS -> R.string.hub_tts_provider
    VoiceServiceType.VITS_TTS -> R.string.hub_legacy_tts
}

internal fun SpeechServiceType.titleRes(): Int = when (this) {
    SpeechServiceType.SHERPA_ONNX -> R.string.hub_stt_provider
    SpeechServiceType.SHERPA_NCNN -> R.string.speech_services_stt_type_sherpa
    SpeechServiceType.OPENAI_STT -> R.string.speech_services_stt_type_openai
    SpeechServiceType.DEEPGRAM_STT -> R.string.speech_services_stt_type_deepgram
}

internal val VoiceServiceType.local: Boolean get() = this == VoiceServiceType.SIMPLE_TTS || this == VoiceServiceType.VITS_TTS || this == VoiceServiceType.SHERPA_ONNX_TTS

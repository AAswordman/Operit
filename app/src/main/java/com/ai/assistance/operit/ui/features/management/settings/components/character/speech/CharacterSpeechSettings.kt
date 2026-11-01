package com.ai.assistance.operit.ui.features.management.settings.components.character.speech

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSlider
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.WaifuSettings
import com.ai.assistance.operit.ui.features.management.settings.components.CompactTextFieldWithExpand
import com.ai.assistance.operit.ui.features.management.settings.screens.CustomEmojiManagementScreen

/** 与角色卡编辑的其他页一致，由外层弹窗负责滚动和保存。 */
@Composable
fun CharacterSpeechSettings(
    characterCardId: String,
    settings: WaifuSettings,
    onChange: (WaifuSettings) -> Unit,
    onExpandPrompt: (Boolean) -> Unit,
) {
    var showExplanation by remember { mutableStateOf(false) }
    var showEmojiManager by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.character_speech_scope),
            Modifier.weight(1f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showExplanation = true }) {
            Text(stringResource(R.string.feature_explanation), fontSize = 12.sp)
        }
    }
    SpeechGroup {
        SpeechSwitch(
            stringResource(R.string.enable_waifu_mode),
            stringResource(R.string.waifu_mode_toggle_desc),
            settings.enabled,
        ) {
            onChange(settings.copy(enabled = it))
        }
    }
    SpeechGroup {
        Text(
            stringResource(R.string.typing_speed_settings),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.typing_speed_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val speed = if (settings.charDelay > 0) 1000f / settings.charDelay else 0f
        Text(
            stringResource(R.string.current_speed_format, speed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.speed_fast), style = MaterialTheme.typography.labelSmall)
            ThemeWorkbenchSlider(
                value = settings.charDelay.toFloat(),
                onValueChange = { onChange(settings.copy(charDelay = it.toInt())) },
                valueRange = 200f..1000f,
                steps = 39,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.speed_slow), style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
        SpeechSwitch(
            stringResource(R.string.remove_punctuation),
            stringResource(R.string.remove_punctuation_desc),
            settings.removePunctuation,
        ) {
            onChange(settings.copy(removePunctuation = it))
        }
    }
    SpeechGroup {
        SpeechSwitch(
            stringResource(R.string.waifu_merge_send),
            stringResource(R.string.waifu_merge_send_desc),
            settings.enableMergeSend,
        ) {
            onChange(settings.copy(enableMergeSend = it))
        }
        if (settings.enableMergeSend) {
            Text(
                stringResource(R.string.waifu_merge_send_delay),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(R.string.waifu_merge_send_delay_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.waifu_merge_send_delay_format, settings.mergeSendDelayMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            ThemeWorkbenchSlider(
                value = settings.mergeSendDelayMs.toFloat(),
                onValueChange = { onChange(settings.copy(mergeSendDelayMs = it.toInt())) },
                valueRange = 500f..10000f,
                steps = 94,
            )
        }
    }
    SpeechGroup {
        CompactTextFieldWithExpand(
            value = settings.customPrompt,
            onValueChange = { onChange(settings.copy(customPrompt = it)) },
            label = stringResource(R.string.waifu_custom_prompt),
            placeholder = stringResource(R.string.waifu_custom_prompt_placeholder),
            minLines = 2,
            maxLines = 4,
            onExpandClick = { onExpandPrompt(false) },
        )
        HorizontalDivider()
        SpeechSwitch(
            stringResource(R.string.enable_emoticons),
            stringResource(R.string.emoticons_desc),
            settings.enableEmoticons,
        ) {
            onChange(settings.copy(enableEmoticons = it))
        }
        Row(
            Modifier.fillMaxWidth()
                .clickable(enabled = characterCardId.isNotBlank()) { showEmojiManager = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.manage_custom_emoji),
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (characterCardId.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        if (characterCardId.isBlank()) R.string.character_speech_save_before_emoji
                        else R.string.character_speech_emoji_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        SpeechSwitch(
            stringResource(R.string.enable_selfie_feature),
            stringResource(R.string.selfie_feature_desc),
            settings.enableSelfie,
        ) {
            onChange(settings.copy(enableSelfie = it))
        }
        if (settings.enableSelfie)
            CompactTextFieldWithExpand(
                value = settings.selfiePrompt,
                onValueChange = { onChange(settings.copy(selfiePrompt = it)) },
                label = stringResource(R.string.appearance_prompt),
                placeholder = stringResource(R.string.appearance_prompt_placeholder),
                minLines = 2,
                maxLines = 4,
                onExpandClick = { onExpandPrompt(true) },
            )
    }
    Text(
        stringResource(R.string.character_speech_draft_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (showExplanation)
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(stringResource(R.string.waifu_mode)) },
            text = {
                Text(
                    stringResource(R.string.waifu_mode_explanation),
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text(stringResource(R.string.done))
                }
            },
        )
    if (showEmojiManager)
        Dialog(
            onDismissRequest = { showEmojiManager = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.manage_custom_emoji),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = { showEmojiManager = false }) {
                            Icon(Icons.Default.Close, stringResource(R.string.close))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        CustomEmojiManagementScreen(
                            onNavigateBack = { showEmojiManager = false },
                            target = ActivePrompt.CharacterCard(characterCardId),
                        )
                    }
                }
            }
        }
}

@Composable
private fun SpeechGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun SpeechSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

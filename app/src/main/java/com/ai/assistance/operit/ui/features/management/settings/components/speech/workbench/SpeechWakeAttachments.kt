package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.WakeWordPreferences.VoiceAutoAttachItem
import com.ai.assistance.operit.data.preferences.WakeWordPreferences.VoiceAutoAttachType
import com.ai.assistance.operit.ui.features.management.settings.components.speech.CompactSwitchRow
import java.util.UUID

/** 保留附件项目、启用状态和关键词，只调整设置页的选择与编辑方式。 */
@Composable
internal fun SpeechWakeAttachments(items: List<VoiceAutoAttachItem>, onChange: (List<VoiceAutoAttachItem>) -> Unit) {
    val context = LocalContext.current
    var editingId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val missing = VoiceAutoAttachType.values().filter { type -> items.none { it.type == type } }
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { item ->
                Surface(
                    modifier = Modifier.weight(1f).clickable { editingId = item.id },
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(0.7.dp, if (item.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
                    color = if (item.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (item.type) {
                                VoiceAutoAttachType.SCREEN_OCR -> Icons.Default.ScreenshotMonitor
                                VoiceAutoAttachType.NOTIFICATIONS -> Icons.Default.Notifications
                                VoiceAutoAttachType.LOCATION -> Icons.Default.LocationOn
                                VoiceAutoAttachType.TIME -> Icons.Default.Schedule
                            }, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            if (item.enabled) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(stringResource(item.type.titleRes()), style = MaterialTheme.typography.labelLarge)
                        Text(item.keywords.ifBlank { stringResource(R.string.voice_keyword_attachments_keyword_supporting) }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (missing.isNotEmpty()) TextButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.voice_keyword_attachments_add_item_title))
    }
    val editing = items.firstOrNull { it.id == editingId }
    if (editing != null) {
        var enabled by remember(editing.id) { mutableStateOf(editing.enabled) }
        var keywords by remember(editing.id) { mutableStateOf(editing.keywords) }
        SpeechWorkbenchSheet(stringResource(editing.type.titleRes()), { editingId = null }) {
            CompactSwitchRow(title = stringResource(R.string.voice_keyword_attachments_enabled_title), description = "", checked = enabled, onCheckedChange = { enabled = it })
            SpeechWorkbenchField(stringResource(R.string.speech_workbench_attachment_keywords), keywords, { keywords = it }, multiline = true, hint = stringResource(R.string.voice_keyword_attachments_keyword_supporting))
            Button(onClick = { onChange(items.map { if (it.id == editing.id) editing.copy(enabled = enabled, keywords = keywords) else it }); editingId = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            TextButton(onClick = { onChange(items.filterNot { it.id == editing.id }); editingId = null }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (adding) SpeechChoicePanel(
        title = stringResource(R.string.voice_keyword_attachments_add_item_title),
        choices = missing.map { SpeechChoice(it.name, context.getString(it.titleRes())) },
        selected = "",
        onSelect = {
            onChange(items + VoiceAutoAttachItem(id = UUID.randomUUID().toString(), type = VoiceAutoAttachType.valueOf(it), enabled = true, keywords = ""))
            adding = false
        },
        onDismiss = { adding = false },
    )
}

private fun VoiceAutoAttachType.titleRes(): Int = when (this) {
    VoiceAutoAttachType.SCREEN_OCR -> R.string.voice_keyword_attachments_screen_title
    VoiceAutoAttachType.NOTIFICATIONS -> R.string.voice_keyword_attachments_notifications_title
    VoiceAutoAttachType.LOCATION -> R.string.voice_keyword_attachments_location_title
    VoiceAutoAttachType.TIME -> R.string.voice_keyword_attachments_time_title
}

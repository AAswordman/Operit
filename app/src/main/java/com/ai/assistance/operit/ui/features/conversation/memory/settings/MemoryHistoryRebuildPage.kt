package com.ai.assistance.operit.ui.features.conversation.memory.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildManager
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildTimeScope
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryWindowPlanner
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
internal fun MemoryHistoryRebuildPage(
    spaceId: String,
    details: MemorySettingsDetails,
    progress: ChatMemoryRebuildManager.Progress,
    onStart: (List<String>, Int, ChatMemoryRebuildTimeScope) -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var windowSize by remember { mutableFloatStateOf(ChatMemoryWindowPlanner.DEFAULT_WINDOW_MESSAGE_COUNT.toFloat()) }
    var useRange by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val running = progress.status == ChatMemoryRebuildManager.Status.PREPARING || progress.status == ChatMemoryRebuildManager.Status.RUNNING
    val ownProgress = progress.memorySpaceId == spaceId
    val rangeReady = startDate != null && endDate != null
    val canStart = !running && selectedIds.isNotEmpty() && (!useRange || rangeReady)

    LaunchedEffect(details.chats) {
        selectedIds = selectedIds.intersect(details.chats.map { it.id }.toSet())
    }

    MemorySettingsHint(stringResource(R.string.memory_settings_history_hint))
    MemorySettingsSection(stringResource(R.string.chat_memory_rebuild_window_size)) {
        MemorySettingsSlider(
            title = stringResource(R.string.chat_memory_rebuild_window_size),
            value = windowSize,
            valueText = stringResource(R.string.chat_memory_rebuild_window_size_value, windowSize.roundToInt()),
            valueRange = ChatMemoryWindowPlanner.MIN_WINDOW_MESSAGE_COUNT.toFloat()..ChatMemoryWindowPlanner.MAX_WINDOW_MESSAGE_COUNT.toFloat(),
            steps = ChatMemoryWindowPlanner.MAX_WINDOW_MESSAGE_COUNT - ChatMemoryWindowPlanner.MIN_WINDOW_MESSAGE_COUNT - 1,
            enabled = !running,
            onValueChange = { windowSize = it },
        )
    }
    MemorySettingsSection(stringResource(R.string.chat_memory_rebuild_time_scope)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !useRange, onClick = { useRange = false }, enabled = !running, label = { Text(stringResource(R.string.chat_memory_rebuild_time_scope_all)) })
            FilterChip(selected = useRange, onClick = { useRange = true }, enabled = !running, label = { Text(stringResource(R.string.chat_memory_rebuild_time_scope_range)) })
        }
        if (useRange) {
            if (rangeReady) MemorySettingsHint(stringResource(
                R.string.chat_memory_rebuild_time_scope_value,
                requireNotNull(startDate).format(formatter), requireNotNull(endDate).format(formatter),
            ))
            OutlinedButton(onClick = { showDatePicker = true }, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.chat_memory_rebuild_time_scope_pick)) }
        }
    }
    MemorySettingsSection(stringResource(R.string.memory_settings_select_chats)) {
        if (details.chats.isEmpty()) MemorySettingsHint(stringResource(R.string.memory_settings_no_chats))
        else {
            TextButton(
                enabled = !running,
                onClick = { selectedIds = if (selectedIds.size == details.chats.size) emptySet() else details.chats.map { it.id }.toSet() },
            ) {
                Text(stringResource(if (selectedIds.size == details.chats.size) R.string.memory_settings_clear_selection else R.string.select_all_current_list))
            }
            details.chats.forEach { chat ->
                val selected = chat.id in selectedIds
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = selected,
                        enabled = !running,
                        role = Role.Checkbox,
                        onValueChange = { selectedIds = if (it) selectedIds + chat.id else selectedIds - chat.id },
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selected, onCheckedChange = null, enabled = !running)
                    Text(chat.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(onClick = { showConfirm = true }, enabled = canStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.chat_memory_rebuild_start, selectedIds.size)) }
        if (running && !ownProgress) MemorySettingsHint(stringResource(R.string.memory_settings_rebuild_busy))
        if (running && ownProgress) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.chat_memory_rebuild_cancel)) }
        }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refresh)) }
    }
    if (ownProgress) MemoryHistoryProgress(progress)

    if (showDatePicker) {
        ChatMemoryRebuildDateRangeDialog(
            initialStart = startDate,
            initialEnd = endDate,
            onConfirm = { start, end -> startDate = start; endDate = end; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.chat_memory_rebuild_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.memory_settings_space, details.spaceName))
                    Text(if (useRange) {
                        stringResource(R.string.chat_memory_rebuild_confirm_message_range, selectedIds.size, requireNotNull(startDate).format(formatter), requireNotNull(endDate).format(formatter), windowSize.roundToInt())
                    } else {
                        stringResource(R.string.chat_memory_rebuild_confirm_message, selectedIds.size, windowSize.roundToInt())
                    })
                }
            },
            confirmButton = {
                TextButton(enabled = canStart, onClick = {
                    val timeScope = if (useRange) ChatMemoryRebuildTimeScope.inclusiveDates(requireNotNull(startDate), requireNotNull(endDate), ZoneId.systemDefault())
                    else ChatMemoryRebuildTimeScope.EntireChat
                    onStart(selectedIds.toList(), windowSize.roundToInt(), timeScope)
                    showConfirm = false
                }) { Text(stringResource(R.string.chat_memory_rebuild_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MemoryHistoryProgress(progress: ChatMemoryRebuildManager.Progress) {
    when (progress.status) {
        ChatMemoryRebuildManager.Status.PREPARING -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            MemorySettingsHint(stringResource(R.string.chat_memory_rebuild_preparing))
        }
        ChatMemoryRebuildManager.Status.RUNNING -> {
            LinearProgressIndicator(progress = { progress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            MemorySettingsHint(stringResource(
                R.string.chat_memory_rebuild_progress,
                progress.currentChatTitle, progress.completedWindows + 1, progress.totalWindows,
                progress.processedSourceMessages, progress.totalSourceMessages, progress.failedWindows,
            ))
        }
        ChatMemoryRebuildManager.Status.COMPLETED -> MemorySettingsHint(stringResource(R.string.chat_memory_rebuild_completed, progress.completedChats, progress.completedWindows, progress.failedWindows))
        ChatMemoryRebuildManager.Status.CANCELLED -> MemorySettingsHint(stringResource(R.string.chat_memory_rebuild_cancelled, progress.completedWindows, progress.totalWindows))
        ChatMemoryRebuildManager.Status.FAILED -> Text(stringResource(R.string.chat_memory_rebuild_failed, progress.errorMessage), color = MaterialTheme.colorScheme.error)
        ChatMemoryRebuildManager.Status.IDLE -> Unit
    }
}
package com.ai.assistance.operit.ui.features.conversation.memory.settings

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildTimeScope
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMemoryRebuildDateRangeDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
        initialSelectedEndDateMillis = initialEnd
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
    )
    val headlineFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DateRangePicker(
                    state = pickerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    title = {
                        Text(
                            text = stringResource(R.string.chat_memory_rebuild_date_range_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp)
                        )
                    },
                    headline = {
                        Text(
                            text = formatMemoryDateRangeHeadline(
                                pickerState.selectedStartDateMillis,
                                pickerState.selectedEndDateMillis,
                                headlineFormatter
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                        )
                    },
                    showModeToggle = false
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        enabled =
                            pickerState.selectedStartDateMillis != null &&
                                pickerState.selectedEndDateMillis != null,
                        onClick = {
                            val startMs = pickerState.selectedStartDateMillis ?: return@TextButton
                            val endMs = pickerState.selectedEndDateMillis ?: return@TextButton
                            val start =
                                ChatMemoryRebuildTimeScope.utcMidnightMillisToLocalDate(startMs)
                            val end =
                                ChatMemoryRebuildTimeScope.utcMidnightMillisToLocalDate(endMs)
                            if (end.isBefore(start)) {
                                return@TextButton
                            }
                            onConfirm(start, end)
                        }
                    ) {
                        Text(stringResource(R.string.chat_memory_rebuild_date_range_confirm))
                    }
                }
            }
        }
    }
}

private fun formatMemoryDateRangeHeadline(
    startMillis: Long?,
    endMillis: Long?,
    formatter: DateTimeFormatter
): String {
    if (startMillis == null) {
        return ""
    }
    val start =
        ChatMemoryRebuildTimeScope.utcMidnightMillisToLocalDate(startMillis).format(formatter)
    if (endMillis == null) {
        return start
    }
    val end = ChatMemoryRebuildTimeScope.utcMidnightMillisToLocalDate(endMillis).format(formatter)
    return "$start – $end"
}

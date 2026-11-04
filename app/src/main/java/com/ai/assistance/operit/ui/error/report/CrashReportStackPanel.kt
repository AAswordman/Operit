package com.ai.assistance.operit.ui.error.report

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
internal fun CrashReportStackPanel(
    report: CrashReport,
    colors: CrashReportColors,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    exporting: Boolean,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onExportLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    val lines = remember(report.stackTrace) { report.stackTrace.lines() }
    Surface(
        modifier,
        shape = RoundedCornerShape(19.dp),
        color = colors.panel,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 9.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.crash_report_details_title),
                        color = colors.foreground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        report.exceptionType
                            ?: stringResource(R.string.crash_report_unknown_exception),
                        color = colors.muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        stringResource(R.string.crash_report_copy_button),
                        Modifier.size(17.dp),
                        tint = colors.muted,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            stringResource(R.string.crash_report_more_actions),
                            Modifier.size(19.dp),
                            tint = colors.muted,
                        )
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = colors.panel,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.crash_report_save_report),
                                    fontSize = 12.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, null, Modifier.size(16.dp))
                            },
                            enabled = !exporting,
                            onClick = {
                                menu = false
                                onSave()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.crash_report_export_logs_button),
                                    fontSize = 12.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Description, null, Modifier.size(16.dp))
                            },
                            enabled = !exporting,
                            onClick = {
                                menu = false
                                onExportLogs()
                            },
                        )
                    }
                }
                IconButton(onClick = onExpandedChange, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        stringResource(
                            if (expanded) R.string.crash_report_collapse_stack
                            else R.string.crash_report_expand_stack
                        ),
                        Modifier.size(19.dp),
                        tint = colors.muted,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = colors.outline)
                Row(
                    Modifier.fillMaxWidth().background(colors.code).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.crash_report_stack_lines, lines.size),
                        color = colors.faint,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier.heightIn(min = 36.dp)
                            // 自绘开关用轨道表达状态，关闭整行默认灰色涟漪并保留开关语义。
                            .toggleable(
                                value = wrap,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Switch,
                                onValueChange = { wrap = it },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(width = 22.dp, height = 13.dp)
                                .clip(CircleShape)
                                .background(if (wrap) colors.accent else colors.outline)
                                .padding(2.dp),
                            contentAlignment =
                                if (wrap) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            Box(
                                Modifier.size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (wrap) colors.onAccent else colors.faint)
                            )
                        }
                        Text(
                            stringResource(R.string.crash_report_wrap_stack),
                            color = colors.muted,
                            fontSize = 10.sp,
                        )
                    }
                }
                val vertical = rememberScrollState()
                val horizontal = rememberScrollState()
                SelectionContainer(Modifier.fillMaxWidth().weight(1f).background(colors.code)) {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(vertical)
                            .then(if (wrap) Modifier else Modifier.horizontalScroll(horizontal))
                            .padding(top = 11.dp, bottom = 18.dp)
                    ) {
                        lines.forEachIndexed { index, line ->
                            Row(
                                Modifier.fillMaxWidth().padding(end = 16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                DisableSelection {
                                    Text(
                                        (index + 1).toString(),
                                        Modifier.width(36.dp).padding(end = 10.dp),
                                        color = colors.faint,
                                        fontSize = 10.sp,
                                        lineHeight = 22.sp,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    )
                                }
                                Text(
                                    line,
                                    if (wrap) Modifier.weight(1f) else Modifier,
                                    color = if (index == 0) colors.error else colors.muted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 22.sp,
                                    softWrap = wrap,
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.crash_report_expand_hint),
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 15.dp),
                    color = colors.muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

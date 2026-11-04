package com.ai.assistance.operit.ui.error.report

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogcatExportResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CrashReportScreen(
    report: CrashReport,
    exporting: Boolean,
    exportResult: LogcatExportResult?,
    onRestart: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onExportLogs: () -> Unit,
) {
    val colors = crashReportColors()
    var expanded by rememberSaveable { mutableStateOf(true) }
    Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets.safeDrawing) {
        insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val compact = maxHeight < 560.dp || LocalDensity.current.fontScale > 1.3f
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(scroll) else Modifier)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CrashReportBrand(colors)
                CrashReportSummary(report, colors, onRestart)
                CrashReportStackPanel(
                    report = report,
                    colors = colors,
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    exporting = exporting,
                    onCopy = onCopy,
                    onSave = onSave,
                    onExportLogs = onExportLogs,
                    modifier =
                        when {
                            !expanded -> Modifier.fillMaxWidth()
                            compact -> Modifier.fillMaxWidth().height(360.dp)
                            else -> Modifier.fillMaxWidth().weight(1f)
                        },
                )
                CrashLogExportRow(colors, exporting, onExportLogs)
                exportResult?.let { result ->
                    Surface(
                        color =
                            if (result.success) colors.accentContainer else colors.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                result.message,
                                Modifier.fillMaxWidth().padding(12.dp),
                                color = if (result.success) colors.foreground else colors.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.crash_report_local_only),
                    Modifier.align(Alignment.CenterHorizontally),
                    color = colors.faint,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrashReportSummary(
    report: CrashReport,
    colors: CrashReportColors,
    onRestart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.errorContainer) {
                Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        null,
                        Modifier.size(22.dp),
                        tint = colors.error,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.crash_report_interrupted),
                    color = colors.foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.crash_report_restart_hint),
                    color = colors.muted,
                    fontSize = 13.sp,
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(colors.error))
                Text(
                    stringResource(R.string.crash_report_this_error),
                    color = colors.muted,
                    fontSize = 12.sp,
                )
                Text(
                    stringResource(crashReasonLabel(report.reason)),
                    color = colors.error,
                    fontSize = 11.sp,
                )
            }
            if (report.showTime) {
                val time =
                    remember(report.occurredAtMillis) {
                        SimpleDateFormat("MM/dd · HH:mm", Locale.getDefault())
                            .format(Date(report.occurredAtMillis))
                    }
                Text(time, color = colors.muted, fontSize = 11.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.crash_report_keep_hint),
                Modifier.weight(1f),
                color = colors.muted,
                fontSize = 12.sp,
            )
            Button(
                onClick = onRestart,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 17.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
            ) {
                Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.crash_report_restart_compact), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CrashLogExportRow(
    colors: CrashReportColors,
    exporting: Boolean,
    onExportLogs: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Surface(shape = RoundedCornerShape(11.dp), color = colors.panel) {
            Box(Modifier.size(35.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Description, null, Modifier.size(17.dp), tint = colors.muted)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.crash_report_app_logs),
                color = colors.foreground,
                fontSize = 12.sp,
            )
            Text(
                stringResource(R.string.crash_report_app_logs_hint),
                color = colors.muted,
                fontSize = 11.sp,
            )
        }
        TextButton(
            onClick = onExportLogs,
            enabled = !exporting,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(
                stringResource(R.string.crash_report_export_logs_compact),
                color = colors.accent,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(4.dp))
            if (exporting)
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    color = colors.accent,
                    strokeWidth = 1.5.dp,
                )
            else Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = colors.accent)
        }
    }
}

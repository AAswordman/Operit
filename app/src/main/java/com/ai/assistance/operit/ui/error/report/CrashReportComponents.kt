package com.ai.assistance.operit.ui.error.report

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

internal data class CrashReportColors(
    val background: Color,
    val panel: Color,
    val code: Color,
    val outline: Color,
    val foreground: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val error: Color,
    val errorContainer: Color,
)

/** 跟随工具页主题，颜色固定在报告页，避免依赖出错时的聊天外观。 */
@Composable
internal fun crashReportColors(): CrashReportColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CrashReportColors(
            Color(0xFF16191F),
            Color(0xFF1D2128),
            Color(0xFF13161B),
            Color(0xFF30363E),
            Color(0xFFEDF0F4),
            Color(0xFF9AA5B3),
            Color(0xFF7B8796),
            Color(0xFF9ACBFF),
            Color(0xFF092442),
            Color(0xFF25384B),
            Color(0xFFFF8585),
            Color(0xFF39262B),
        )
    } else {
        CrashReportColors(
            Color(0xFFF8F9FC),
            Color(0xFFFFFFFF),
            Color(0xFFF2F4F8),
            Color(0xFFE1E5EC),
            Color(0xFF1B2533),
            Color(0xFF667383),
            Color(0xFF7C8999),
            Color(0xFF225F9E),
            Color(0xFFFFFFFF),
            Color(0xFFE8F0FA),
            Color(0xFFB9343D),
            Color(0xFFFBE9EC),
        )
    }

@Composable
internal fun CrashReportBrand(colors: CrashReportColors) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter =
                androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_simple_foreground),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Operit",
            color = colors.foreground,
            fontSize = 16.sp,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.crash_report_running_report),
            color = colors.muted,
            fontSize = 12.sp,
        )
    }
}

@StringRes
internal fun crashReasonLabel(reason: CrashReason): Int =
    when (reason) {
        CrashReason.MEMORY -> R.string.crash_reason_memory
        CrashReason.STACK_OVERFLOW -> R.string.crash_reason_stack
        CrashReason.STORAGE_FULL -> R.string.crash_reason_storage
        CrashReason.PERMISSION -> R.string.crash_reason_permission
        CrashReason.NETWORK -> R.string.crash_reason_network
        CrashReason.DATABASE -> R.string.crash_reason_database
        CrashReason.FILE_ACCESS -> R.string.crash_reason_file
        CrashReason.COMPONENT_LOADING -> R.string.crash_reason_component
        CrashReason.REMOTE_PROCESS -> R.string.crash_reason_remote
        CrashReason.NULL_REFERENCE -> R.string.crash_reason_null
        CrashReason.INVALID_STATE -> R.string.crash_reason_state
        CrashReason.UNKNOWN -> R.string.crash_reason_unknown
    }

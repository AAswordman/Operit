package com.ai.assistance.operit.ui.error

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.OperitUtilityTheme
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.error.report.CrashReportScreen
import com.ai.assistance.operit.ui.error.report.prepareCrashReport
import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogExportOptions
import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogExportPreferences
import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogcatExportHelper
import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogcatExportResult
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.diagnostics.CrashRecoveryState
import com.ai.assistance.operit.core.diagnostics.ThrowableTextFormatter
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CrashReportActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_OCCURRED_AT = "extra_occurred_at"
        private const val MAX_CRASH_REPORT_CHARS = 24_000
    }

    private var exportOptions by mutableStateOf(LogExportOptions())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashRecoveryState.consumePendingCrashReportLaunch(this)
        exportOptions = LogExportPreferences(this).load()
        val rawStackTrace =
            ThrowableTextFormatter.truncateText(
                intent.getStringExtra(EXTRA_STACK_TRACE)
                    ?: getString(R.string.crash_report_no_stack),
                MAX_CRASH_REPORT_CHARS,
            )
        val occurredAt = intent.getLongExtra(EXTRA_OCCURRED_AT, System.currentTimeMillis())
        intent.putExtra(EXTRA_OCCURRED_AT, occurredAt)
        // 原始堆栈不再写回日志或交给页面动作，避免绕过同一份过滤选项。
        AppLogger.e("CrashReportActivity", "Displaying crash report, chars=${rawStackTrace.length}")
        setContent {
            OperitUtilityTheme {
                val report =
                    remember(rawStackTrace, occurredAt, exportOptions) {
                        prepareCrashReport(rawStackTrace, occurredAt, exportOptions)
                    }
                val scope = rememberCoroutineScope()
                var exporting by remember { mutableStateOf(false) }
                var result by remember { mutableStateOf<LogcatExportResult?>(null) }
                fun export(action: suspend () -> LogcatExportResult) {
                    if (exporting) return
                    exporting = true
                    result = null
                    scope.launch {
                        try {
                            result = action()
                        } finally {
                            exporting = false
                        }
                    }
                }
                CrashReportScreen(
                    report = report,
                    exporting = exporting,
                    exportResult = result,
                    onRestart = { restartApp(this@CrashReportActivity) },
                    onCopy = { copyToClipboard(this@CrashReportActivity, report.stackTrace) },
                    onSave = {
                        export { exportToFile(this@CrashReportActivity, report.exportText) }
                    },
                    onExportLogs = {
                        export {
                            LogcatExportHelper.exportLogs(this@CrashReportActivity, report.options)
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置返回时重新读取日志查看器保存的选项，显示和后续导出同步更新。
        exportOptions = LogExportPreferences(this).load()
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("error_stack_trace", text))
    OKToast.success(context, R.string.crash_report_copy_success, OKToast.LENGTH_SHORT).show()
}

private suspend fun exportToFile(context: Context, text: String): LogcatExportResult =
    withContext(Dispatchers.IO) {
        try {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val errorDir = File(downloadsDir, "Operit/error")
            if (!errorDir.exists()) errorDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(errorDir, "error-report-$timestamp.log")
            FileOutputStream(file).use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
            LogcatExportResult(
                context.getString(R.string.crash_report_export_success, file.absolutePath),
                true,
            )
        } catch (error: Exception) {
            AppLogger.e("CrashReportActivity", "Failed to export crash report", error)
            LogcatExportResult(
                context.getString(R.string.crash_report_export_failed, error.localizedMessage),
                false,
            )
        }
    }

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    val flags = PendingIntent.FLAG_CANCEL_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.set(
        AlarmManager.RTC,
        System.currentTimeMillis() + 200,
        pendingIntent
    )

    (context as? Activity)?.finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
    kotlin.system.exitProcess(0)
}

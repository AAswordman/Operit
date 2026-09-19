package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LogcatExportResult(
    val message: String,
    val success: Boolean
)

object LogcatExportHelper {
    suspend fun exportLogs(
        context: Context,
        options: LogExportOptions = LogExportOptions()
    ): LogcatExportResult = withContext(Dispatchers.IO) {
        try {
            val logFile = AppLogger.getLogFile()
            if (logFile == null || !logFile.exists() || logFile.length() == 0L) {
                return@withContext LogcatExportResult(
                    message = context.getString(R.string.logcat_no_logs_to_save),
                    success = false
                )
            }

            val exportLines = prepareExportLines(logFile, options)
            if (exportLines == null) {
                val emptyMessage = if (options.errorContextOnly) {
                    context.getString(R.string.logcat_no_error_logs)
                } else {
                    context.getString(R.string.logcat_no_logs_to_save)
                }
                return@withContext LogcatExportResult(
                    message = emptyMessage,
                    success = false
                )
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "operit_log_$timestamp.txt"
            val filePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveUsingMediaStore(context, fileName, exportLines)
            } else {
                saveUsingFileSystem(context, fileName, exportLines)
            }

            LogcatExportResult(
                message = context.getString(R.string.logcat_saved_to, filePath),
                success = true
            )
        } catch (e: Exception) {
            LogcatExportResult(
                message = context.getString(
                    R.string.logcat_save_failed,
                    e.message ?: context.getString(R.string.logcat_unknown_error)
                ),
                success = false
            )
        }
    }

    private fun prepareExportLines(
        logFile: File,
        options: LogExportOptions
    ): PreparedExport? {
        logFile.bufferedReader().use { reader ->
            if (options.isIdentity) {
                val lines = reader.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
                if (lines.isEmpty()) return null
                return PreparedExport(lines = lines, recordCount = lines.size.toLong())
            }
            val result = LogExportFilter.filter(reader.lineSequence(), options)
            if (result.exportedRecordCount == 0 || result.lines.isEmpty()) {
                return null
            }
            return PreparedExport(
                lines = result.lines,
                recordCount = result.exportedRecordCount.toLong()
            )
        }
    }

    private fun writeLogContent(
        context: Context,
        writer: Writer,
        export: PreparedExport
    ) {
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        writer.appendLine(context.getString(R.string.logcat_header))
        writer.appendLine(context.getString(R.string.logcat_date, exportTime))
        writer.appendLine(context.getString(R.string.logcat_total_count, export.recordCount))
        writer.appendLine("===================================")
        writer.appendLine()
        export.lines.forEach { line ->
            writer.appendLine(line)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveUsingMediaStore(
        context: Context,
        fileName: String,
        export: PreparedExport
    ): String {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/operit")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw Exception(context.getString(R.string.logcat_cannot_create_file))

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    writeLogContent(context, writer, export)
                }
            } ?: throw Exception(context.getString(R.string.logcat_cannot_open_output_stream))
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return "${downloadsDir.absolutePath}/operit/$fileName"
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.logcat_mediastore_save_failed, e.message ?: ""))
        }
    }

    private fun saveUsingFileSystem(
        context: Context,
        fileName: String,
        export: PreparedExport
    ): String {
        try {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null || !downloadsDir.exists() && !downloadsDir.mkdirs()) {
                throw Exception(context.getString(R.string.logcat_cannot_create_download_dir))
            }
            val operitDir = File(downloadsDir, "operit")
            if (!operitDir.exists() && !operitDir.mkdirs()) {
                throw Exception(context.getString(R.string.logcat_cannot_create_operit_dir))
            }
            val file = File(operitDir, fileName)
            FileWriter(file).use { writer ->
                writeLogContent(context, writer, export)
            }
            if (!file.exists() || file.length() == 0L) {
                throw Exception(context.getString(R.string.logcat_file_create_failed))
            }
            return file.absolutePath
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.logcat_filesystem_save_failed, e.message ?: ""))
        }
    }

    private data class PreparedExport(
        val lines: List<String>,
        val recordCount: Long
    )
}

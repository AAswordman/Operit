package com.ai.assistance.operit.ui.recovery

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.backup.RawSnapshotBackupManager
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.recovery.PreferencesHealthManager
import com.ai.assistance.operit.data.recovery.RoomDatabaseHealthManager
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataRecoveryViewModel(private val context: Context) : ViewModel() {

    data class QueryResult(
        val columns: List<String>,
        val rows: List<List<String>>
    )

    data class State(
        val isRunning: Boolean = false,
        val status: String? = null,
        val error: String? = null,
        val sqlText: String = SAFE_MESSAGES_QUERY,
        val queryResult: QueryResult? = null,
        val affectedRows: Int? = null,
        val lastSnapshotPath: String? = null,
        val restoreCompleted: Boolean = false,
        val configurationHealthReport: PreferencesHealthManager.Report? = null,
        val databaseHealthReport: RoomDatabaseHealthManager.Report? = null,
        val lastConfigurationRepairArchivePath: String? = null,
        val lastDatabaseRepairArchivePath: String? = null,
        val healthRepairCompleted: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setSqlText(sql: String) {
        _state.value = _state.value.copy(sqlText = sql)
    }

    fun runSql() {
        val sql = sanitizeSql(_state.value.sqlText)
        if (sql.isBlank()) {
            _state.value = _state.value.copy(error = context.getString(R.string.data_recovery_sql_empty), status = null, affectedRows = null)
            return
        }

        _state.value =
            _state.value.copy(isRunning = true, error = null, status = context.getString(R.string.data_recovery_sql_running), affectedRows = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isQueryStatement(sql)) {
                    val result = executeQuery(sql)
                    withContext(Dispatchers.Main) {
                        _state.value =
                            _state.value.copy(
                                isRunning = false,
                                status = context.getString(R.string.data_recovery_query_completed, result.rows.size),
                                queryResult = result,
                                affectedRows = null,
                                configurationHealthReport = null,
                                databaseHealthReport = null
                            )
                    }
                } else {
                    writableDatabase().execSQL(sql)
                    val affectedRows = queryChanges()
                    withContext(Dispatchers.Main) {
                        _state.value =
                            _state.value.copy(
                                isRunning = false,
                                status = context.getString(R.string.data_recovery_sql_completed),
                                queryResult = null,
                                affectedRows = affectedRows,
                                configurationHealthReport = null,
                                databaseHealthReport = null
                            )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Recovery SQL failed", e)
                withContext(Dispatchers.Main) {
                    _state.value =
                        _state.value.copy(
                            isRunning = false,
                            error = e.message ?: e.javaClass.name,
                            status = null,
                            affectedRows = null
                        )
                }
            }
        }
    }

    fun exportRawSnapshot() {
        _state.value =
            _state.value.copy(isRunning = true, error = null, status = context.getString(R.string.data_recovery_export_running), lastSnapshotPath = null)
        viewModelScope.launch {
            try {
                val outFile =
                    RawSnapshotBackupManager.exportToBackupDir(context) { progress ->
                        _state.value =
                            _state.value.copy(
                                status = exportProgressText(progress)
                            )
                    }
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        status = context.getString(R.string.data_recovery_export_completed),
                        lastSnapshotPath = outFile.absolutePath
                    )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Raw snapshot export failed", e)
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        error = e.message ?: e.javaClass.name,
                        status = null
                    )
            }
        }
    }

    fun restoreRawSnapshot(uri: Uri) {
        _state.value =
            _state.value.copy(isRunning = true, error = null, status = context.getString(R.string.data_recovery_restore_running), restoreCompleted = false)
        viewModelScope.launch {
            try {
                RawSnapshotBackupManager.restoreFromBackupUri(context, uri) { progress ->
                    _state.value = _state.value.copy(status = restoreProgressText(progress))
                }
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        status = context.getString(R.string.data_recovery_restore_completed),
                        restoreCompleted = true,
                        configurationHealthReport = null,
                        databaseHealthReport = null
                    )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Raw snapshot restore failed", e)
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        error = e.message ?: e.javaClass.name,
                        status = null
                    )
            }
        }
    }

    fun inspectStorage() {
        _state.value =
            _state.value.copy(
                isRunning = true,
                error = null,
                status = context.getString(R.string.data_recovery_database_check_running),
                affectedRows = null,
                configurationHealthReport = null,
                databaseHealthReport = null,
                lastConfigurationRepairArchivePath = null,
                lastDatabaseRepairArchivePath = null,
                healthRepairCompleted = false
            )
        viewModelScope.launch {
            try {
                val configurationReport = PreferencesHealthManager.inspect(context)
                val databaseReport = RoomDatabaseHealthManager.inspect(context)
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        status = healthSummary(configurationReport, databaseReport),
                        configurationHealthReport = configurationReport,
                        databaseHealthReport = databaseReport
                    )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Configuration and database health inspection failed", e)
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        error = e.message ?: e.javaClass.name,
                        status = null
                    )
            }
        }
    }

    fun repairStorage() {
        val repairConfiguration = _state.value.configurationHealthReport?.canRepair == true
        val repairDatabase = _state.value.databaseHealthReport?.canRepair == true
        if (!repairConfiguration && !repairDatabase) {
            _state.value =
                _state.value.copy(
                    error = context.getString(R.string.data_recovery_database_no_supported_repair),
                    status = null
                )
            return
        }

        _state.value =
            _state.value.copy(
                isRunning = true,
                error = null,
                status = context.getString(R.string.data_recovery_database_repair_running),
                affectedRows = null,
                lastConfigurationRepairArchivePath = null,
                lastDatabaseRepairArchivePath = null,
                healthRepairCompleted = false
            )
        viewModelScope.launch {
            var configurationArchivePath: String? = null
            var databaseArchivePath: String? = null
            try {
                if (repairDatabase) {
                    databaseArchivePath =
                        RoomDatabaseHealthManager.repair(context).sourceArchive.absolutePath
                }
                if (repairConfiguration) {
                    configurationArchivePath =
                        PreferencesHealthManager.repair(context).sourceArchive.absolutePath
                }

                val configurationReport = PreferencesHealthManager.inspect(context)
                val databaseReport = RoomDatabaseHealthManager.inspect(context)
                val status =
                    if (isHealthy(configurationReport, databaseReport)) {
                        context.getString(R.string.data_recovery_database_repair_completed)
                    } else {
                        context.getString(R.string.data_recovery_database_repair_remaining)
                    }
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        status = status,
                        configurationHealthReport = configurationReport,
                        databaseHealthReport = databaseReport,
                        lastConfigurationRepairArchivePath = configurationArchivePath,
                        lastDatabaseRepairArchivePath = databaseArchivePath,
                        healthRepairCompleted = true
                    )
            } catch (e: PreferencesHealthManager.RepairFailedException) {
                AppLogger.e(TAG, "Preferences repair failed after source preservation", e)
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        error =
                            context.getString(
                                R.string.data_recovery_database_repair_failed_preserved,
                                e.sourceArchive.absolutePath
                            ),
                        status = null,
                        lastConfigurationRepairArchivePath = e.sourceArchive.absolutePath,
                        lastDatabaseRepairArchivePath = databaseArchivePath
                    )
            } catch (e: RoomDatabaseHealthManager.RepairFailedException) {
                AppLogger.e(TAG, "Room database repair failed after source preservation", e)
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        error =
                            context.getString(
                                R.string.data_recovery_database_repair_failed_preserved,
                                e.sourceArchive.absolutePath
                            ),
                        status = null,
                        lastConfigurationRepairArchivePath = configurationArchivePath,
                        lastDatabaseRepairArchivePath = e.sourceArchive.absolutePath
                    )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Configuration and database repair failed", e)
                _state.value =
                    _state.value.copy(
                        isRunning = false,
                        error = e.message ?: e.javaClass.name,
                        status = null,
                        lastConfigurationRepairArchivePath = configurationArchivePath,
                        lastDatabaseRepairArchivePath = databaseArchivePath
                    )
            }
        }
    }

    private fun healthSummary(
        configurationReport: PreferencesHealthManager.Report,
        databaseReport: RoomDatabaseHealthManager.Report
    ): String =
        when {
            configurationReport.status == PreferencesHealthManager.Status.MANUAL_RECOVERY_REQUIRED ||
                databaseReport.status == RoomDatabaseHealthManager.Status.MANUAL_RECOVERY_REQUIRED ->
                context.getString(R.string.data_recovery_database_summary_manual)
            configurationReport.status == PreferencesHealthManager.Status.NEEDS_REPAIR ||
                databaseReport.status == RoomDatabaseHealthManager.Status.NEEDS_REPAIR ->
                context.getString(R.string.data_recovery_database_summary_repairable)
            else -> context.getString(R.string.data_recovery_database_summary_healthy)
        }

    private fun isHealthy(
        configurationReport: PreferencesHealthManager.Report,
        databaseReport: RoomDatabaseHealthManager.Report
    ): Boolean =
        configurationReport.status == PreferencesHealthManager.Status.HEALTHY &&
            databaseReport.status == RoomDatabaseHealthManager.Status.HEALTHY

    private fun writableDatabase() = AppDatabase.getDatabase(context).openHelper.writableDatabase

    private fun executeQuery(sql: String): QueryResult {
        writableDatabase().query(sql).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = mutableListOf<List<String>>()
            while (cursor.moveToNext()) {
                rows.add(
                    columns.indices.map { index -> readCell(cursor, index) }
                )
            }
            return QueryResult(columns = columns, rows = rows)
        }
    }

    private fun readCell(cursor: Cursor, index: Int): String {
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> "NULL"
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
            Cursor.FIELD_TYPE_BLOB -> {
                val blob = cursor.getBlob(index)
                "BLOB(${blob.size})"
            }
            else -> cursor.getString(index) ?: ""
        }
    }

    private fun queryChanges(): Int? {
        return try {
            writableDatabase().query("SELECT changes()").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to query SQLite changes", e)
            null
        }
    }

    private fun isQueryStatement(sql: String): Boolean {
        val normalized = sql.trimStart().lowercase()
        return normalized.startsWith("select") || normalized.startsWith("with") || normalized.startsWith("pragma")
    }

    private fun sanitizeSql(sql: String): String {
        return sql.trim().removeSuffix(";")
    }

    private fun exportProgressText(progress: RawSnapshotBackupManager.ExportProgressInfo): String {
        val stage = when (progress.stage) {
            RawSnapshotBackupManager.ExportProgress.PREPARING ->
                context.getString(R.string.backup_raw_snapshot_progress_preparing)
            RawSnapshotBackupManager.ExportProgress.SCANNING_FILES ->
                progress.scannedFiles?.let {
                    context.getString(R.string.backup_raw_snapshot_progress_scanning_files_with_count, it)
                } ?: context.getString(R.string.backup_raw_snapshot_progress_scanning_files)
            RawSnapshotBackupManager.ExportProgress.ZIPPING_FILES ->
                context.getString(R.string.backup_raw_snapshot_progress_zipping_files)
            RawSnapshotBackupManager.ExportProgress.ZIPPING_EXTERNAL_FILES ->
                context.getString(R.string.backup_raw_snapshot_progress_zipping_external_files)
            RawSnapshotBackupManager.ExportProgress.ZIPPING_SHARED_PREFS ->
                context.getString(R.string.backup_raw_snapshot_progress_zipping_shared_prefs)
            RawSnapshotBackupManager.ExportProgress.ZIPPING_DATASTORE ->
                context.getString(R.string.backup_raw_snapshot_progress_zipping_datastore)
            RawSnapshotBackupManager.ExportProgress.ZIPPING_DATABASES ->
                context.getString(R.string.backup_raw_snapshot_progress_zipping_databases)
            RawSnapshotBackupManager.ExportProgress.FINALIZING ->
                context.getString(R.string.backup_raw_snapshot_progress_finalizing)
        }
        val suffix = progress.percent?.let {
            context.getString(R.string.data_recovery_progress_percent, it)
        }.orEmpty()
        return context.getString(R.string.data_recovery_export_progress, stage + suffix)
    }

    private fun restoreProgressText(progress: RawSnapshotBackupManager.RestoreProgress): String {
        val stage = when (progress) {
            RawSnapshotBackupManager.RestoreProgress.PREPARING ->
                context.getString(R.string.backup_raw_snapshot_progress_preparing)
            RawSnapshotBackupManager.RestoreProgress.READING_ZIP ->
                context.getString(R.string.backup_raw_snapshot_progress_reading_zip)
            RawSnapshotBackupManager.RestoreProgress.EXTRACTING ->
                context.getString(R.string.backup_raw_snapshot_progress_extracting)
            RawSnapshotBackupManager.RestoreProgress.REPLACING_FILES ->
                context.getString(R.string.backup_raw_snapshot_progress_replacing_files)
            RawSnapshotBackupManager.RestoreProgress.REPLACING_EXTERNAL_FILES ->
                context.getString(R.string.backup_raw_snapshot_progress_replacing_external_files)
            RawSnapshotBackupManager.RestoreProgress.REPLACING_SHARED_PREFS ->
                context.getString(R.string.backup_raw_snapshot_progress_replacing_shared_prefs)
            RawSnapshotBackupManager.RestoreProgress.REPLACING_DATASTORE ->
                context.getString(R.string.backup_raw_snapshot_progress_replacing_datastore)
            RawSnapshotBackupManager.RestoreProgress.REPLACING_DATABASES ->
                context.getString(R.string.backup_raw_snapshot_progress_replacing_databases)
            RawSnapshotBackupManager.RestoreProgress.FINALIZING ->
                context.getString(R.string.backup_raw_snapshot_progress_finalizing)
        }
        return context.getString(R.string.data_recovery_restore_progress, stage)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DataRecoveryViewModel::class.java)) {
                return DataRecoveryViewModel(LocaleUtils.getLocalizedContext(context.applicationContext)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "DataRecoveryViewModel"

        const val SAFE_MESSAGES_QUERY =
            "SELECT messageId, chatId, timestamp, sender, length(CAST(content AS BLOB)) AS bytes FROM messages ORDER BY bytes DESC LIMIT 50"
        const val SAFE_VARIANTS_QUERY =
            "SELECT variantId, chatId, messageTimestamp, variantIndex, length(CAST(content AS BLOB)) AS bytes FROM message_variants ORDER BY bytes DESC LIMIT 50"
        const val SAFE_CHATS_QUERY =
            "SELECT id, length(CAST(title AS BLOB)) AS titleBytes, length(CAST(ifnull(workspace, '') AS BLOB)) AS workspaceBytes, length(CAST(ifnull(workspaceEnv, '') AS BLOB)) AS workspaceEnvBytes FROM chats ORDER BY max(length(CAST(title AS BLOB)), length(CAST(ifnull(workspace, '') AS BLOB)), length(CAST(ifnull(workspaceEnv, '') AS BLOB))) DESC LIMIT 50"
    }
}

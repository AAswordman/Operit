package com.ai.assistance.operit.data.recovery

import android.app.ActivityManager
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.backup.OperitBackupDirs
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.util.AppLogger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** User-triggered Room diagnostics and narrowly scoped repair operations. */
object RoomDatabaseHealthManager {
    private const val TAG = "RoomDatabaseHealth"
    private const val DATABASE_NAME = "app_database"
    private val missingIndexEntryPattern = Regex("^row \\d+ missing from index .+$")
    private val wrongIndexEntryCountPattern = Regex("^wrong # of entries in index .+$")
    private val operationMutex = Mutex()

    enum class Status {
        HEALTHY,
        NEEDS_REPAIR,
        MANUAL_RECOVERY_REQUIRED
    }

    enum class ItemStatus {
        PASS,
        WARNING,
        FAILURE
    }

    enum class RepairAction {
        REBUILD_INDEXES,
        RUN_ROOM_MIGRATIONS
    }

    private enum class MainProcessState {
        RUNNING,
        NOT_RUNNING,
        UNKNOWN
    }

    data class CheckItem(
        val title: String,
        val detail: String,
        val status: ItemStatus
    )

    data class Report(
        val status: Status,
        val summary: String,
        val databasePath: String,
        val checks: List<CheckItem>,
        val repairActions: List<RepairAction>
    ) {
        val canRepair: Boolean
            get() = status == Status.NEEDS_REPAIR && repairActions.isNotEmpty()
    }

    data class RepairResult(
        val sourceArchive: File,
        val completedActions: List<RepairAction>,
        val report: Report
    )

    class RepairFailedException(
        val sourceArchive: File,
        cause: Throwable
    ) : IllegalStateException(cause.message, cause)

    suspend fun inspect(context: Context): Report =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                inspectLocked(context)
            }
        }

    suspend fun repair(context: Context): RepairResult =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val displayContext = context
                val appContext = context.applicationContext
                val before = inspectLocked(displayContext)
                check(before.canRepair) {
                    displayContext.getString(R.string.data_recovery_database_no_supported_repair)
                }

                requireMainProcessStopped(displayContext)
                val archive = preserveDatabaseFiles(appContext)
                val completed = mutableListOf<RepairAction>()
                try {
                    before.repairActions.forEach { action ->
                        requireMainProcessStopped(displayContext)
                        when (action) {
                            RepairAction.REBUILD_INDEXES -> rebuildIndexes(appContext)
                            RepairAction.RUN_ROOM_MIGRATIONS -> runRoomMigrations(appContext)
                        }
                        completed += action
                    }

                    RepairResult(
                        sourceArchive = archive,
                        completedActions = completed,
                        report = inspectLocked(displayContext)
                    )
                } catch (e: Exception) {
                    throw RepairFailedException(archive, e)
                }
            }
        }

    private fun inspectLocked(context: Context): Report {
        val appContext = context.applicationContext
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        val checks = mutableListOf<CheckItem>()
        val repairActions = linkedSetOf<RepairAction>()
        var hasBlockingFailure = false
        var hasRepairableIssue = false

        try {
            AppDatabase.closeDatabase()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to close Room before health inspection", e)
            return failureReport(
                context = context,
                databaseFile = databaseFile,
                checks =
                    listOf(
                        CheckItem(
                            title = context.getString(R.string.data_recovery_database_check_owner),
                            detail =
                                context.getString(
                                    R.string.data_recovery_database_check_owner_failed,
                                    e.message ?: e.javaClass.simpleName
                                ),
                            status = ItemStatus.FAILURE
                        )
                    )
            )
        }

        when {
            !databaseFile.exists() -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_file),
                        detail = context.getString(R.string.data_recovery_database_file_missing),
                        status = ItemStatus.FAILURE
                    )
                return failureReport(context, databaseFile, checks)
            }

            !databaseFile.isFile -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_file),
                        detail = context.getString(R.string.data_recovery_database_file_invalid_path),
                        status = ItemStatus.FAILURE
                    )
                return failureReport(context, databaseFile, checks)
            }

            !hasExpectedDatabasePath(databaseFile, DATABASE_NAME) -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_file),
                        detail = context.getString(R.string.data_recovery_database_file_invalid_path),
                        status = ItemStatus.FAILURE
                    )
                return failureReport(context, databaseFile, checks)
            }

            else -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_file),
                        detail =
                            context.getString(
                                R.string.data_recovery_database_file_ok,
                                databaseFile.length()
                            ),
                        status = ItemStatus.PASS
                    )
            }
        }

        val sidecars = databaseFiles(databaseFile).drop(1).filter { it.exists() }
        val invalidSidecars =
            sidecars.filterNot { sidecar ->
                sidecar.isFile && hasExpectedDatabasePath(sidecar, sidecar.name)
            }
        if (invalidSidecars.isNotEmpty()) {
            hasBlockingFailure = true
            checks +=
                CheckItem(
                    title = context.getString(R.string.data_recovery_database_check_sidecars),
                    detail =
                        context.getString(
                            R.string.data_recovery_database_sidecars_invalid,
                            invalidSidecars.joinToString { it.name }
                        ),
                    status = ItemStatus.FAILURE
                )
        } else {
            val detail =
                if (sidecars.isEmpty()) {
                    context.getString(R.string.data_recovery_database_sidecars_none)
                } else {
                    sidecars.joinToString { file -> "${file.name}: ${file.length()} B" }
                }
            checks +=
                CheckItem(
                    title = context.getString(R.string.data_recovery_database_check_sidecars),
                    detail = detail,
                    status = ItemStatus.PASS
                )
        }

        val corruptionHandler = PreservingCorruptionHandler()
        val database =
            try {
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    corruptionHandler
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unable to open Room database for health inspection", e)
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_open),
                        detail =
                            context.getString(
                                R.string.data_recovery_database_open_failed,
                                e.message ?: e.javaClass.simpleName
                            ),
                        status = ItemStatus.FAILURE
                    )
                return failureReport(context, databaseFile, checks)
            }

        try {
            database.use { openedDatabase ->
                if (corruptionHandler.corruptionReported) {
                    hasBlockingFailure = true
                    checks +=
                        CheckItem(
                            title = context.getString(R.string.data_recovery_database_check_open),
                            detail = context.getString(R.string.data_recovery_database_corruption_reported),
                            status = ItemStatus.FAILURE
                        )
                } else {
                    checks +=
                        CheckItem(
                            title = context.getString(R.string.data_recovery_database_check_open),
                            detail = context.getString(R.string.data_recovery_database_open_ok),
                            status = ItemStatus.PASS
                        )
                }

                val quickCheckMessages = readQuickCheck(openedDatabase)
                when {
                    quickCheckMessages.size == 1 &&
                        quickCheckMessages.single().equals("ok", ignoreCase = true) -> {
                        checks +=
                            CheckItem(
                                title = context.getString(R.string.data_recovery_database_check_integrity),
                                detail = context.getString(R.string.data_recovery_database_integrity_ok),
                                status = ItemStatus.PASS
                            )
                    }

                    isIndexOnlyQuickCheckFailure(quickCheckMessages) -> {
                        hasRepairableIssue = true
                        repairActions += RepairAction.REBUILD_INDEXES
                        checks +=
                            CheckItem(
                                title = context.getString(R.string.data_recovery_database_check_integrity),
                                detail = quickCheckMessages.joinToString("\n"),
                                status = ItemStatus.WARNING
                            )
                    }

                    else -> {
                        hasBlockingFailure = true
                        checks +=
                            CheckItem(
                                title = context.getString(R.string.data_recovery_database_check_integrity),
                                detail = quickCheckMessages.joinToString("\n"),
                                status = ItemStatus.FAILURE
                            )
                    }
                }

                val version = readUserVersion(openedDatabase)
                when {
                    version == AppDatabase.DATABASE_VERSION -> {
                        checks +=
                            CheckItem(
                                title = context.getString(R.string.data_recovery_database_check_version),
                                detail = context.getString(R.string.data_recovery_database_version_current, version),
                                status = ItemStatus.PASS
                            )
                    }

                    version in 1 until AppDatabase.DATABASE_VERSION -> {
                        hasRepairableIssue = true
                        repairActions += RepairAction.RUN_ROOM_MIGRATIONS
                        checks +=
                            CheckItem(
                                title = context.getString(R.string.data_recovery_database_check_version),
                                detail =
                                    context.getString(
                                        R.string.data_recovery_database_version_old,
                                        version,
                                        AppDatabase.DATABASE_VERSION
                                    ),
                                status = ItemStatus.WARNING
                            )
                    }

                    version > AppDatabase.DATABASE_VERSION -> {
                        hasBlockingFailure = true
                        checks +=
                            CheckItem(
                                title = context.getString(R.string.data_recovery_database_check_version),
                                detail =
                                    context.getString(
                                        R.string.data_recovery_database_version_newer,
                                        version,
                                        AppDatabase.DATABASE_VERSION
                                    ),
                                status = ItemStatus.FAILURE
                            )
                    }

                    else -> {
                        hasBlockingFailure = true
                        checks +=
                            CheckItem(
                                title = context.getString(R.string.data_recovery_database_check_version),
                                detail = context.getString(R.string.data_recovery_database_version_invalid, version),
                                status = ItemStatus.FAILURE
                            )
                    }
                }

                val foreignKeyViolations = countForeignKeyViolations(openedDatabase)
                if (foreignKeyViolations == 0) {
                    checks +=
                        CheckItem(
                            title = context.getString(R.string.data_recovery_database_check_foreign_keys),
                            detail = context.getString(R.string.data_recovery_database_foreign_keys_ok),
                            status = ItemStatus.PASS
                        )
                } else {
                    hasBlockingFailure = true
                    checks +=
                        CheckItem(
                            title = context.getString(R.string.data_recovery_database_check_foreign_keys),
                            detail =
                                context.getString(
                                    R.string.data_recovery_database_foreign_keys_failed,
                                    foreignKeyViolations
                                ),
                            status = ItemStatus.FAILURE
                        )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Room database health queries failed", e)
            checks +=
                CheckItem(
                    title = context.getString(R.string.data_recovery_database_check_queries),
                    detail =
                        context.getString(
                            R.string.data_recovery_database_check_queries_failed,
                            e.message ?: e.javaClass.simpleName
                        ),
                    status = ItemStatus.FAILURE
                )
            return failureReport(context, databaseFile, checks)
        }

        if (corruptionHandler.corruptionReported) {
            hasBlockingFailure = true
        }

        val processState = mainProcessState(appContext)
        when (processState) {
            MainProcessState.RUNNING -> {
                hasBlockingFailure = true
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_process),
                        detail = context.getString(R.string.data_recovery_database_main_process_running),
                        status = ItemStatus.FAILURE
                    )
            }

            MainProcessState.UNKNOWN -> {
                hasBlockingFailure = true
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_process),
                        detail = context.getString(R.string.data_recovery_database_main_process_unknown),
                        status = ItemStatus.FAILURE
                    )
            }

            MainProcessState.NOT_RUNNING -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_process),
                        detail = context.getString(R.string.data_recovery_database_main_process_stopped),
                        status = ItemStatus.PASS
                    )
            }
        }

        when {
            processState != MainProcessState.NOT_RUNNING -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_room_schema),
                        detail = context.getString(R.string.data_recovery_database_room_schema_process_blocked),
                        status = ItemStatus.FAILURE
                    )
            }

            hasBlockingFailure -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_room_schema),
                        detail = context.getString(R.string.data_recovery_database_room_schema_sqlite_blocked),
                        status = ItemStatus.FAILURE
                    )
            }

            AppDatabase.validateRecoveryCopy(appContext, databaseFile) -> {
                val detail =
                    if (RepairAction.RUN_ROOM_MIGRATIONS in repairActions) {
                        context.getString(
                            R.string.data_recovery_database_room_schema_migration_ok,
                            AppDatabase.DATABASE_VERSION
                        )
                    } else {
                        context.getString(R.string.data_recovery_database_room_schema_current_ok)
                    }
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_room_schema),
                        detail = detail,
                        status = ItemStatus.PASS
                    )
            }

            else -> {
                hasBlockingFailure = true
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_database_check_room_schema),
                        detail = context.getString(R.string.data_recovery_database_room_schema_failed),
                        status = ItemStatus.FAILURE
                    )
            }
        }

        val status =
            when {
                hasBlockingFailure -> Status.MANUAL_RECOVERY_REQUIRED
                hasRepairableIssue -> Status.NEEDS_REPAIR
                else -> Status.HEALTHY
            }
        val supportedActions =
            if (hasBlockingFailure) emptyList() else repairActions.toList()
        return Report(
            status = status,
            summary = summaryFor(context, status),
            databasePath = databaseFile.absolutePath,
            checks = checks,
            repairActions = supportedActions
        )
    }

    private fun failureReport(
        context: Context,
        databaseFile: File,
        checks: List<CheckItem>
    ): Report =
        Report(
            status = Status.MANUAL_RECOVERY_REQUIRED,
            summary = summaryFor(context, Status.MANUAL_RECOVERY_REQUIRED),
            databasePath = databaseFile.absolutePath,
            checks = checks,
            repairActions = emptyList()
        )

    private fun summaryFor(context: Context, status: Status): String =
        when (status) {
            Status.HEALTHY -> context.getString(R.string.data_recovery_database_summary_healthy)
            Status.NEEDS_REPAIR ->
                context.getString(R.string.data_recovery_database_summary_repairable)
            Status.MANUAL_RECOVERY_REQUIRED ->
                context.getString(R.string.data_recovery_database_summary_manual)
        }

    private fun readQuickCheck(database: SQLiteDatabase): List<String> {
        val messages = mutableListOf<String>()
        database.rawQuery("PRAGMA quick_check", null).use { cursor ->
            while (cursor.moveToNext()) {
                messages += cursor.getString(0).orEmpty()
            }
        }
        return messages.ifEmpty { listOf("quick_check returned no result") }
    }

    private fun readUserVersion(database: SQLiteDatabase): Int {
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst()) { "user_version returned no result" }
            return cursor.getInt(0)
        }
    }

    private fun countForeignKeyViolations(database: SQLiteDatabase): Int {
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                count++
            }
            return count
        }
    }

    internal fun isIndexOnlyQuickCheckFailure(messages: List<String>): Boolean {
        val issueLines =
            messages
                .flatMap { it.lineSequence().toList() }
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && !it.startsWith("*** in database") }
        return issueLines.isNotEmpty() &&
            issueLines.all { line ->
                missingIndexEntryPattern.matches(line) ||
                    wrongIndexEntryCountPattern.matches(line)
            }
    }

    private fun rebuildIndexes(context: Context) {
        AppDatabase.closeDatabase()
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        val corruptionHandler = PreservingCorruptionHandler()
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            corruptionHandler
        ).use { database ->
            database.execSQL("REINDEX")
        }
        check(!corruptionHandler.corruptionReported) {
            context.getString(R.string.data_recovery_database_corruption_reported)
        }
    }

    private fun runRoomMigrations(context: Context) {
        AppDatabase.closeDatabase()
        try {
            AppDatabase.getDatabase(context).openHelper.writableDatabase
        } finally {
            AppDatabase.closeDatabase()
        }
    }

    private fun preserveDatabaseFiles(context: Context): File {
        AppDatabase.closeDatabase()
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        val sources = databaseFiles(databaseFile).filter { it.exists() }
        check(sources.isNotEmpty()) {
            context.getString(R.string.data_recovery_database_file_missing)
        }
        sources.forEach { source ->
            check(source.isFile && hasExpectedDatabasePath(source, source.name)) {
                context.getString(R.string.data_recovery_database_file_invalid_path)
            }
        }

        val timestamp =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS"))
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val directory = OperitBackupDirs.roomDbDir()
        val target = File(directory, "room_db_repair_source_${timestamp}_$suffix.zip")
        val temporary = File(directory, ".${target.name}.tmp")
        check(!temporary.exists() && !target.exists()) {
            "Room repair archive path already exists"
        }

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                sources.forEach { source ->
                    output.putNextEntry(ZipEntry(source.name))
                    BufferedInputStream(FileInputStream(source)).use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
            }
            check(temporary.renameTo(target)) {
                "Failed to publish Room repair source archive"
            }
            return target
        } catch (e: Exception) {
            if (temporary.exists() && !temporary.delete()) {
                AppLogger.w(TAG, "Failed to delete incomplete Room repair archive")
            }
            throw e
        }
    }

    private fun databaseFiles(databaseFile: File): List<File> =
        listOf(
            databaseFile,
            File(databaseFile.absolutePath + "-wal"),
            File(databaseFile.absolutePath + "-shm"),
            File(databaseFile.absolutePath + "-journal")
        )

    private fun hasExpectedDatabasePath(file: File, expectedName: String): Boolean {
        val parent = file.parentFile?.canonicalFile ?: return false
        val canonical = file.canonicalFile
        return canonical.parentFile == parent && canonical.name == expectedName
    }

    private fun mainProcessState(context: Context): MainProcessState {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return MainProcessState.UNKNOWN
        val processes = activityManager.runningAppProcesses ?: return MainProcessState.UNKNOWN
        return if (
            processes.any { process ->
                process.pid != Process.myPid() && process.processName == context.packageName
            }
        ) {
            MainProcessState.RUNNING
        } else {
            MainProcessState.NOT_RUNNING
        }
    }

    private fun requireMainProcessStopped(context: Context) {
        when (mainProcessState(context.applicationContext)) {
            MainProcessState.NOT_RUNNING -> Unit
            MainProcessState.RUNNING ->
                throw IllegalStateException(
                    context.getString(R.string.data_recovery_database_main_process_running)
                )
            MainProcessState.UNKNOWN ->
                throw IllegalStateException(
                    context.getString(R.string.data_recovery_database_main_process_unknown)
                )
        }
    }

    private class PreservingCorruptionHandler : DatabaseErrorHandler {
        var corruptionReported: Boolean = false
            private set

        override fun onCorruption(database: SQLiteDatabase) {
            corruptionReported = true
            AppLogger.e(TAG, "SQLite reported corruption; retained source: ${database.path}")
        }
    }
}

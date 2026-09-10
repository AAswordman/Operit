package com.ai.assistance.operit.data.recovery

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.backup.OperitBackupDirs
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** User-triggered physical validation and preservation-first repair for Preferences DataStore files. */
object PreferencesHealthManager {
    private const val TAG = "PreferencesHealth"
    private const val PREFERENCES_SUFFIX = ".preferences_pb"
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

    data class CheckItem(
        val title: String,
        val detail: String,
        val status: ItemStatus
    )

    data class Report(
        val status: Status,
        val summary: String,
        val checks: List<CheckItem>,
        val repairableFileNames: List<String>
    ) {
        val canRepair: Boolean
            get() = status == Status.NEEDS_REPAIR && repairableFileNames.isNotEmpty()
    }

    data class RepairResult(
        val sourceArchive: File,
        val resetFileNames: List<String>,
        val report: Report
    )

    class RepairFailedException(
        val sourceArchive: File,
        cause: Throwable
    ) : IllegalStateException(cause.message, cause)

    private enum class MainProcessState {
        RUNNING,
        NOT_RUNNING,
        UNKNOWN
    }

    private sealed interface CopyValidation {
        data object Readable : CopyValidation

        data class Corrupt(val reason: String) : CopyValidation

        data class Failed(val reason: String) : CopyValidation
    }

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
                    displayContext.getString(R.string.data_recovery_configuration_no_supported_repair)
                }

                requireMainProcessStopped(displayContext)
                val sources = resolveRepairSources(appContext, before.repairableFileNames)
                val archive = preserveConfigurationFiles(sources)
                val resetFiles = mutableListOf<String>()
                try {
                    sources.forEach { source ->
                        requireMainProcessStopped(displayContext)
                        check(validateCopy(appContext, source) is CopyValidation.Corrupt) {
                            displayContext.getString(
                                R.string.data_recovery_configuration_changed_before_repair,
                                source.name
                            )
                        }
                        check(source.delete()) {
                            displayContext.getString(
                                R.string.data_recovery_configuration_delete_failed,
                                source.name
                            )
                        }
                        resetFiles += source.name
                    }

                    RepairResult(
                        sourceArchive = archive,
                        resetFileNames = resetFiles,
                        report = inspectLocked(displayContext)
                    )
                } catch (e: Exception) {
                    throw RepairFailedException(archive, e)
                }
            }
        }

    private suspend fun inspectLocked(context: Context): Report {
        val appContext = context.applicationContext
        val checks = mutableListOf<CheckItem>()
        val repairableFileNames = mutableListOf<String>()
        var hasBlockingFailure = false
        var hasRepairableIssue = false

        val directory = preferencesDirectory(appContext)
        when {
            !directory.exists() -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_configuration_check_files),
                        detail = context.getString(R.string.data_recovery_configuration_files_none),
                        status = ItemStatus.PASS
                    )
                return report(context, checks, repairableFileNames, false, false)
            }

            !directory.isDirectory -> {
                checks +=
                    CheckItem(
                        title = context.getString(R.string.data_recovery_configuration_check_files),
                        detail = context.getString(R.string.data_recovery_configuration_directory_invalid),
                        status = ItemStatus.FAILURE
                    )
                return report(context, checks, repairableFileNames, true, false)
            }
        }

        val preferencePaths =
            directory.listFiles()
                ?.filter { path -> path.name.endsWith(PREFERENCES_SUFFIX) }
                ?.sortedBy { path -> path.name }
                ?: run {
                    checks +=
                        CheckItem(
                            title = context.getString(R.string.data_recovery_configuration_check_files),
                            detail = context.getString(R.string.data_recovery_configuration_directory_unreadable),
                            status = ItemStatus.FAILURE
                        )
                    return report(context, checks, repairableFileNames, true, false)
                }

        if (preferencePaths.isEmpty()) {
            checks +=
                CheckItem(
                    title = context.getString(R.string.data_recovery_configuration_check_files),
                    detail = context.getString(R.string.data_recovery_configuration_files_none),
                    status = ItemStatus.PASS
                )
            return report(context, checks, repairableFileNames, false, false)
        }

        var readableFileCount = 0
        preferencePaths.forEach { source ->
            if (!source.isFile || !hasExpectedPath(source, directory)) {
                hasBlockingFailure = true
                checks +=
                    CheckItem(
                        title = source.name,
                        detail = context.getString(R.string.data_recovery_configuration_file_invalid_path),
                        status = ItemStatus.FAILURE
                    )
                return@forEach
            }

            when (val validation = validateCopy(appContext, source)) {
                CopyValidation.Readable -> readableFileCount++
                is CopyValidation.Corrupt -> {
                    hasRepairableIssue = true
                    repairableFileNames += source.name
                    checks +=
                        CheckItem(
                            title = source.name.removeSuffix(PREFERENCES_SUFFIX),
                            detail = context.getString(R.string.data_recovery_configuration_file_corrupt),
                            status = ItemStatus.WARNING
                        )
                    AppLogger.w(TAG, "Preferences file is corrupt: ${source.name}; ${validation.reason}")
                }

                is CopyValidation.Failed -> {
                    hasBlockingFailure = true
                    checks +=
                        CheckItem(
                            title = source.name.removeSuffix(PREFERENCES_SUFFIX),
                            detail = context.getString(R.string.data_recovery_configuration_file_check_failed),
                            status = ItemStatus.FAILURE
                        )
                    AppLogger.e(
                        TAG,
                        "Preferences file check failed: ${source.name}; ${validation.reason}"
                    )
                }
            }
        }

        if (readableFileCount > 0) {
            checks.add(
                0,
                CheckItem(
                    title = context.getString(R.string.data_recovery_configuration_check_files),
                    detail =
                        context.getString(
                            R.string.data_recovery_configuration_files_ok,
                            readableFileCount
                        ),
                    status = ItemStatus.PASS
                )
            )
        }

        if (hasRepairableIssue && mainProcessState(appContext) != MainProcessState.NOT_RUNNING) {
            hasBlockingFailure = true
        }

        return report(
            context = context,
            checks = checks,
            repairableFileNames = repairableFileNames,
            hasBlockingFailure = hasBlockingFailure,
            hasRepairableIssue = hasRepairableIssue
        )
    }

    private fun report(
        context: Context,
        checks: List<CheckItem>,
        repairableFileNames: List<String>,
        hasBlockingFailure: Boolean,
        hasRepairableIssue: Boolean
    ): Report {
        val status =
            when {
                hasBlockingFailure -> Status.MANUAL_RECOVERY_REQUIRED
                hasRepairableIssue -> Status.NEEDS_REPAIR
                else -> Status.HEALTHY
            }
        return Report(
            status = status,
            summary = summaryFor(context, status),
            checks = checks,
            repairableFileNames =
                if (hasBlockingFailure) emptyList() else repairableFileNames.toList()
        )
    }

    private fun summaryFor(context: Context, status: Status): String =
        when (status) {
            Status.HEALTHY -> context.getString(R.string.data_recovery_database_summary_healthy)
            Status.NEEDS_REPAIR -> context.getString(R.string.data_recovery_database_summary_repairable)
            Status.MANUAL_RECOVERY_REQUIRED ->
                context.getString(R.string.data_recovery_database_summary_manual)
        }

    private suspend fun validateCopy(context: Context, source: File): CopyValidation {
        val directory = File(context.cacheDir, "preferences_health_${UUID.randomUUID()}")
        val copy = File(directory, source.name)
        val job = SupervisorJob()
        return try {
            check(directory.mkdir()) { "Failed to create Preferences validation directory" }
            source.copyTo(copy, overwrite = false)
            val store =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(job + Dispatchers.IO),
                    produceFile = { copy }
                )
            store.data.first()
            CopyValidation.Readable
        } catch (e: CorruptionException) {
            CopyValidation.Corrupt(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Preferences copy validation failed for ${source.name}", e)
            CopyValidation.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            job.cancelAndJoin()
            if (directory.exists() && !directory.deleteRecursively()) {
                AppLogger.w(TAG, "Failed to delete Preferences validation directory")
            }
        }
    }

    private fun resolveRepairSources(context: Context, fileNames: List<String>): List<File> {
        val directory = preferencesDirectory(context)
        check(directory.isDirectory) {
            context.getString(R.string.data_recovery_configuration_directory_invalid)
        }
        return fileNames.map { fileName ->
            check(
                fileName.endsWith(PREFERENCES_SUFFIX) &&
                    File(fileName).name == fileName
            ) {
                context.getString(R.string.data_recovery_configuration_file_invalid_path)
            }
            File(directory, fileName).also { source ->
                check(source.isFile && hasExpectedPath(source, directory)) {
                    context.getString(R.string.data_recovery_configuration_file_invalid_path)
                }
            }
        }
    }

    private fun preserveConfigurationFiles(sources: List<File>): File {
        val timestamp =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS"))
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val directory = OperitBackupDirs.preferencesDir()
        val target = File(directory, "preferences_repair_source_${timestamp}_$suffix.zip")
        val temporary = File(directory, ".${target.name}.tmp")
        check(!temporary.exists() && !target.exists()) {
            "Preferences repair archive path already exists"
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
                "Failed to publish Preferences repair source archive"
            }
            return target
        } catch (e: Exception) {
            if (temporary.exists() && !temporary.delete()) {
                AppLogger.w(TAG, "Failed to delete incomplete Preferences repair archive")
            }
            throw e
        }
    }

    private fun preferencesDirectory(context: Context): File =
        requireNotNull(context.preferencesDataStoreFile("recovery_probe").parentFile)

    private fun hasExpectedPath(file: File, directory: File): Boolean {
        val canonicalDirectory = directory.canonicalFile
        val canonicalFile = file.canonicalFile
        return canonicalFile.parentFile == canonicalDirectory && canonicalFile.name == file.name
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
}

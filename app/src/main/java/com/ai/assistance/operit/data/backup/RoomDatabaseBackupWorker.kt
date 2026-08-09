package com.ai.assistance.operit.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.util.AppLogger

class RoomDatabaseBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "RoomDbBackupWorker"
        const val KEY_FORCE = "force"
    }

    override suspend fun doWork(): Result {
        if (OperitApplication.storageStartupState !=
            OperitApplication.StorageStartupState.READY
        ) {
            AppLogger.e(
                TAG,
                "Room backup refused because storage state=" +
                    OperitApplication.storageStartupState
            )
            return Result.failure()
        }
        val force = inputData.getBoolean(KEY_FORCE, false)
        val preferences = RoomDatabaseBackupPreferences.getInstance(applicationContext)

        return try {
            val result = RoomDatabaseBackupManager.backupIfNeeded(applicationContext, force)
            if (!result.performed) {
                AppLogger.d(TAG, "Backup skipped: ${result.skippedReason}")
            } else {
                AppLogger.i(TAG, "Backup created: ${result.backupFile?.absolutePath}")
            }
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Database backup failed", e)
            try {
                preferences.markFailure(e.localizedMessage ?: e.toString())
            } catch (_: Exception) {
            }
            Result.failure()
        }
    }
}

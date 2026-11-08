package com.ai.assistance.operit.core.diagnostics

import android.content.Context

object CrashRecoveryState {
    private const val PREFS_NAME = "crash_recovery_state"
    private const val KEY_PRESERVE_LOGS_FOR_CRASH_REPORT = "preserve_logs_for_crash_report"

    fun markPendingCrashReportLaunch(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRESERVE_LOGS_FOR_CRASH_REPORT, true)
            .commit()
    }

    fun consumePendingCrashReportLaunch(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldPreserveLogs = prefs.getBoolean(KEY_PRESERVE_LOGS_FOR_CRASH_REPORT, false)
        if (shouldPreserveLogs) {
            prefs.edit().remove(KEY_PRESERVE_LOGS_FOR_CRASH_REPORT).commit()
        }
        return shouldPreserveLogs
    }
}

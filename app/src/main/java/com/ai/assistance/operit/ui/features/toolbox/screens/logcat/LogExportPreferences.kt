package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context

class LogExportPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): LogExportOptions {
        return LogExportOptions(
            excludeDebug = prefs.getBoolean(KEY_EXCLUDE_DEBUG, false),
            excludeSystem = prefs.getBoolean(KEY_EXCLUDE_SYSTEM, false),
            errorContextOnly = prefs.getBoolean(KEY_ERROR_CONTEXT, false),
            hideSensitive = prefs.getBoolean(KEY_HIDE_SENSITIVE, false),
            stripTimestamp = prefs.getBoolean(KEY_STRIP_TIMESTAMP, false)
        )
    }

    fun save(options: LogExportOptions) {
        prefs.edit()
            .putBoolean(KEY_EXCLUDE_DEBUG, options.excludeDebug)
            .putBoolean(KEY_EXCLUDE_SYSTEM, options.excludeSystem)
            .putBoolean(KEY_ERROR_CONTEXT, options.errorContextOnly)
            .putBoolean(KEY_HIDE_SENSITIVE, options.hideSensitive)
            .putBoolean(KEY_STRIP_TIMESTAMP, options.stripTimestamp)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "log_export_preferences"
        private const val KEY_EXCLUDE_DEBUG = "exclude_debug"
        private const val KEY_EXCLUDE_SYSTEM = "exclude_system"
        private const val KEY_ERROR_CONTEXT = "error_context_only"
        private const val KEY_HIDE_SENSITIVE = "hide_sensitive"
        private const val KEY_STRIP_TIMESTAMP = "strip_timestamp"
    }
}

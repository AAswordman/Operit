package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

/**
 * Export-time filters for AppLogger files.
 * All flags default to false so the exported file stays complete.
 */
data class LogExportOptions(
    val excludeDebug: Boolean = false,
    val excludeSystem: Boolean = false,
    val errorContextOnly: Boolean = false,
    val hideSensitive: Boolean = false,
    val stripTimestamp: Boolean = false
) {
    val isIdentity: Boolean
        get() = !excludeDebug &&
            !excludeSystem &&
            !errorContextOnly &&
            !hideSensitive &&
            !stripTimestamp
}

package com.ai.assistance.operit.core.deploy

import java.time.Instant

/**
 * ReleaseConfig — manages release configuration and versioning.
 *
 * Per PROJECT_PLAN.md §12:
 * - Semantic versioning
 * - Changelog auto-generate from commits that pass Quality Gate
 * - GitHub Releases (APK) as primary distribution
 * - Crash report monitoring (opt-in)
 */
class ReleaseConfig {

    companion object {
        const val APP_NAME = "NonO Assistant"
        const val ROOT_PROJECT = "OperitX"
        const val PACKAGE_NAME = "com.ai.nonoassistance"

        // Version info — updated before each release
        var versionMajor = 0
        var versionMinor = 1
        var versionPatch = 0
        var versionPreRelease = "alpha.1"

        /**
         * Get the full version string (e.g., "0.1.0-alpha.1").
         */
        fun getVersionString(): String {
            return if (versionPreRelease.isNotEmpty()) {
                "$versionMajor.$versionMinor.$versionPatch-$versionPreRelease"
            } else {
                "$versionMajor.$versionMinor.$versionPatch"
            }
        }

        /**
         * Get the version code for Android (monotonically increasing integer).
         * Convention: major * 10000 + minor * 100 + patch
         */
        fun getVersionCode(): Int {
            return versionMajor * 10000 + versionMinor * 100 + versionPatch
        }

        /**
         * Get the display name for the app.
         */
        fun getDisplayName(): String = APP_NAME

        /**
         * Get the root project name.
         */
        fun getRootProjectName(): String = ROOT_PROJECT
    }

    /**
     * Generate a changelog entry for the current version.
     */
    fun generateChangelogEntry(changes: List<ChangelogEntry>): String {
        val timestamp = Instant.now().toString().take(10)

        return buildString {
            appendLine("## [$getVersionString()] - $timestamp")
            appendLine()

            val grouped = changes.groupBy { it.type }
            for ((type, entries) in grouped) {
                appendLine("### ${type.displayName}")
                for (entry in entries) {
                    appendLine("- ${entry.description}")
                }
                appendLine()
            }
        }
    }

    data class ChangelogEntry(
        val type: ChangeType,
        val description: String
    )

    enum class ChangeType(val displayName: String) {
        ADDED("Added"),
        CHANGED("Changed"),
        FIXED("Fixed"),
        REMOVED("Removed"),
        SECURITY("Security")
    }
}

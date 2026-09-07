package com.ai.assistance.operit.core.tools.packTool

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A package dependency declared by a ToolPkg manifest.
 *
 * The dependency itself also determines load order: the required package is
 * loaded before the package that declares this entry.
 */
@Serializable
data class ToolPkgManifestRequirement(
    val id: String,
    val description: String,
    @SerialName("min_version") val minVersion: String? = null,
    @SerialName("max_version") val maxVersion: String? = null
) {
    internal fun versionConstraintText(): String? {
        return when {
            minVersion != null && maxVersion != null -> "$minVersion - $maxVersion"
            minVersion != null -> ">= $minVersion"
            maxVersion != null -> "<= $maxVersion"
            else -> null
        }
    }

    internal fun targetVersionFailure(targetVersion: String): String? {
        if (minVersion == null && maxVersion == null) {
            return null
        }

        val normalizedTargetVersion = targetVersion.trim()
        if (normalizedTargetVersion.isBlank()) {
            return "target package '$id' does not declare a version"
        }

        val parsedTargetVersion =
            try {
                ToolPkgPackageVersion.parse(normalizedTargetVersion)
            } catch (error: IllegalArgumentException) {
                return "target package '$id' has an invalid version '$normalizedTargetVersion'"
            }
        val minimumVersion = minVersion?.let { version -> ToolPkgPackageVersion.parse(version) }
        val maximumVersion = maxVersion?.let { version -> ToolPkgPackageVersion.parse(version) }

        return when {
            minimumVersion != null && parsedTargetVersion < minimumVersion ->
                "target package '$id' has version '$normalizedTargetVersion', below minimum '$minVersion'"
            maximumVersion != null && parsedTargetVersion > maximumVersion ->
                "target package '$id' has version '$normalizedTargetVersion', above maximum '$maxVersion'"
            else -> null
        }
    }
}

internal data class ToolPkgPackageVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<ToolPkgPackageVersion> {
    override fun compareTo(other: ToolPkgPackageVersion): Int {
        return compareValuesBy(this, other, ToolPkgPackageVersion::major, ToolPkgPackageVersion::minor, ToolPkgPackageVersion::patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")

        fun parse(value: String): ToolPkgPackageVersion {
            val normalized = value.trim()
            val match = VERSION_PATTERN.matchEntire(normalized)
                ?: throw IllegalArgumentException(
                    "Package version must use major.minor.patch format: '$value'"
                )
            return ToolPkgPackageVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt()
            )
        }
    }
}

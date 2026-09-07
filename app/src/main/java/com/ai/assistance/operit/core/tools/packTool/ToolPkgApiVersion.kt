package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.BuildConfig

internal data class ToolPkgApiVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<ToolPkgApiVersion> {
    override fun compareTo(other: ToolPkgApiVersion): Int {
        return compareValuesBy(
            this,
            other,
            ToolPkgApiVersion::major,
            ToolPkgApiVersion::minor,
            ToolPkgApiVersion::patch
        )
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")

        fun parse(value: String): ToolPkgApiVersion {
            val normalized = value.trim()
            val match = VERSION_PATTERN.matchEntire(normalized)
                ?: throw IllegalArgumentException(
                    "ToolPkg API version must use major.minor.patch format: '$value'"
                )
            return ToolPkgApiVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt()
            )
        }
    }
}

private data class OperitVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int
) : Comparable<OperitVersion> {
    override fun compareTo(other: OperitVersion): Int {
        return compareValuesBy(
            this,
            other,
            OperitVersion::major,
            OperitVersion::minor,
            OperitVersion::patch,
            OperitVersion::build
        )
    }

    companion object {
        private val VERSION_PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:\\+(\\d+))?$")

        fun parse(value: String): OperitVersion {
            val normalized = value.trim()
            val match = VERSION_PATTERN.matchEntire(normalized)
                ?: throw IllegalArgumentException("Operit version is not supported: '$value'")
            return OperitVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                build = match.groupValues[4].takeIf(String::isNotEmpty)?.toInt() ?: 0
            )
        }
    }
}

internal object ToolPkgApiCompatibility {
    const val LEGACY_API_VERSION = "1.0.0"
    const val API_VERSION_1_0_1 = "1.0.1"
    const val API_VERSION_1_0_1_INTRODUCED_IN_OPERIT = "1.12.1+4"

    private val legacyApiVersion = ToolPkgApiVersion.parse(LEGACY_API_VERSION)
    private val apiVersion101 = ToolPkgApiVersion.parse(API_VERSION_1_0_1)
    private val apiVersion101IntroducedIn =
        OperitVersion.parse(API_VERSION_1_0_1_INTRODUCED_IN_OPERIT)

    fun parseDeclaredApiVersion(apiVersion: String): ToolPkgApiVersion {
        return ToolPkgApiVersion.parse(apiVersion.trim().ifBlank { LEGACY_API_VERSION })
    }

    fun supportedApiVersions(operitVersion: String = BuildConfig.VERSION_NAME): List<ToolPkgApiVersion> {
        val currentVersion = OperitVersion.parse(operitVersion)
        return buildList {
            add(legacyApiVersion)
            if (currentVersion >= apiVersion101IntroducedIn) {
                add(apiVersion101)
            }
        }
    }

    fun supportedApiVersionText(operitVersion: String = BuildConfig.VERSION_NAME): String {
        return supportedApiVersions(operitVersion).joinToString(", ")
    }

    fun requireSupported(
        apiVersion: String,
        operitVersion: String = BuildConfig.VERSION_NAME
    ): ToolPkgApiVersion {
        val declaredVersion = parseDeclaredApiVersion(apiVersion)
        val supportedVersions = supportedApiVersions(operitVersion)
        require(declaredVersion in supportedVersions) {
            "ToolPkg API version '$declaredVersion' is not supported by Operit $operitVersion. " +
                "Supported ToolPkg API versions: ${supportedVersions.joinToString(", ")}. " +
                "ToolPkg API $API_VERSION_1_0_1 requires Operit " +
                "$API_VERSION_1_0_1_INTRODUCED_IN_OPERIT or newer."
        }
        return declaredVersion
    }

}

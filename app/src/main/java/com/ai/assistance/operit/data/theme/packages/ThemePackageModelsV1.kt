package com.ai.assistance.operit.data.theme.packages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val PACKAGE_ID_PATTERN = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$")
private val MEMBER_ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")
private val SEMVER_PATTERN =
    Regex(
        "^\\d+\\.\\d+\\.\\d+" +
            "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
    )
private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

@Serializable
@JvmInline
internal value class ThemePackageIdV1(val value: String) {
    init {
        require(PACKAGE_ID_PATTERN.matches(value)) { "Invalid theme package ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemePackageVersionV1(val value: String) {
    init {
        require(SEMVER_PATTERN.matches(value)) { "Invalid theme package version: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeArchiveSha256V1(val value: String) {
    init {
        require(SHA256_PATTERN.matches(value)) { "Invalid theme archive digest: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeVariantIdV1(val value: String) {
    init {
        require(MEMBER_ID_PATTERN.matches(value)) { "Invalid theme variant ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeParameterIdV1(val value: String) {
    init {
        require(MEMBER_ID_PATTERN.matches(value)) { "Invalid theme parameter ID: $value" }
    }
}

/** Identifies one immutable installed package version by content digest. */
@Serializable
internal data class ThemePackageCoordinateV1(
    val packageId: ThemePackageIdV1,
    val version: ThemePackageVersionV1,
    val archiveSha256: ThemeArchiveSha256V1,
)

@Serializable
internal data class ThemePackageReferenceV1(
    val coordinate: ThemePackageCoordinateV1,
)

@Serializable
internal sealed interface ThemeParameterValueV1 {
    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : ThemeParameterValueV1

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : ThemeParameterValueV1

    @Serializable
    @SerialName("integer")
    data class IntegerValue(val value: Long) : ThemeParameterValueV1

    @Serializable
    @SerialName("decimal")
    data class DecimalValue(val value: Double) : ThemeParameterValueV1
}

/** Application-level selection of exactly one theme package version plus its user parameters. */
@Serializable
internal data class ThemeInstanceV1(
    val reference: ThemePackageReferenceV1,
    val variantId: ThemeVariantIdV1? = null,
    val parameterValues: Map<String, ThemeParameterValueV1> = emptyMap(),
) {
    init {
        require(parameterValues.keys.all(MEMBER_ID_PATTERN::matches)) {
            "Theme instance parameter keys must be valid parameter IDs: ${parameterValues.keys}"
        }
    }

    companion object {
        fun defaultBundled(): ThemeInstanceV1 =
            ThemeInstanceV1(
                reference = ThemePackageReferenceV1(ThemePackageDefaultV1.coordinate),
            )
    }
}

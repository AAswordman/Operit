package com.ai.assistance.operit.data.theme.packages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val PACKAGE_ID_PATTERN_V2 = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$")
private val MEMBER_ID_PATTERN_V2 = Regex("^[a-z][a-z0-9_]*$")
private val SEMVER_PATTERN_V2 =
    Regex(
        "^\\d+\\.\\d+\\.\\d+" +
            "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
    )
private val SHA256_PATTERN_V2 = Regex("^[0-9a-f]{64}$")

@Serializable
@JvmInline
internal value class ThemePackageIdV2(val value: String) {
    init {
        require(PACKAGE_ID_PATTERN_V2.matches(value)) { "Invalid theme package ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemePackageVersionV2(val value: String) {
    init {
        require(SEMVER_PATTERN_V2.matches(value)) { "Invalid theme package version: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeArchiveSha256V2(val value: String) {
    init {
        require(SHA256_PATTERN_V2.matches(value)) { "Invalid theme archive digest: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeVariantIdV2(val value: String) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(value)) { "Invalid theme variant ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeParameterIdV2(val value: String) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(value)) { "Invalid theme parameter ID: $value" }
    }
}

/** 产品级约定参数 ID：主题与设置页共享，不属于任何单个包的私有命名。 */
internal object ThemePackageParameterIdsV2 {
    const val PRIMARY_COLOR = "primary_color"
    const val BACKGROUND_IMAGE = "background_image"
}

/** Identifies one immutable installed V2 package by its exact release archive. */
@Serializable
internal data class ThemePackageCoordinateV2(
    val packageId: ThemePackageIdV2,
    val version: ThemePackageVersionV2,
    val archiveSha256: ThemeArchiveSha256V2,
)

@Serializable
internal data class ThemePackageReferenceV2(
    val coordinate: ThemePackageCoordinateV2,
)

@Serializable
internal sealed interface ThemeParameterValueV2 {
    @Serializable
    @SerialName("color")
    data class ColorValue(val argb: Long) : ThemeParameterValueV2 {
        init {
            require(argb in 0..0xFFFFFFFFL) { "Color value must be ARGB within 0..0xffffffff." }
        }
    }

    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : ThemeParameterValueV2

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : ThemeParameterValueV2

    @Serializable
    @SerialName("integer")
    data class IntegerValue(val value: Long) : ThemeParameterValueV2

    @Serializable
    @SerialName("decimal")
    data class DecimalValue(val value: Double) : ThemeParameterValueV2
}

/** Application-level selection of one globally linked V2 theme package. */
@Serializable
internal data class ThemeInstanceV2(
    val reference: ThemePackageReferenceV2,
    val variantId: ThemeVariantIdV2? = null,
    val parameterValues: Map<String, ThemeParameterValueV2> = emptyMap(),
) {
    init {
        require(parameterValues.keys.all(MEMBER_ID_PATTERN_V2::matches)) {
            "Theme instance parameter keys must be valid parameter IDs: ${parameterValues.keys}"
        }
    }

    companion object {
        fun defaultBundled(): ThemeInstanceV2 =
            ThemeInstanceV2(reference = ThemePackageReferenceV2(ThemePackageDefaultV2.coordinate))
    }
}

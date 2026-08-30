package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val MEMBER_ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")
private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

const val THEME_PACKAGE_SCHEMA_VERSION = 1
const val THEME_PACKAGE_MANIFEST_ENTRY = "operit-theme.json"
const val THEME_PACKAGE_EXTENSION = "otheme"
const val THEME_PACKAGE_ZIP_COMMENT = "Operit Theme Package"

/** Locale-keyed text; the key "*" is the fallback entry. */
@Serializable
internal data class ThemePackageLocalizedTextV1(
    val values: Map<String, String>,
) {
    init {
        require(values.isNotEmpty()) { "Localized text must declare at least one entry." }
        require(values.values.all { it.isNotEmpty() }) { "Localized text entries must not be empty." }
    }

    fun resolve(locale: String): String = values[locale] ?: values["*"] ?: values.values.first()
}

@Serializable
internal enum class ThemeAssetKindV1 {
    BITMAP,
    NINE_SLICE,
    FONT,
    PATH,
}

@Serializable
internal data class ThemePackageAssetEntryV1(
    val key: String,
    val path: String,
    val kind: ThemeAssetKindV1,
    val sha256: String,
    val byteSize: Long,
) {
    init {
        require(MEMBER_ID_PATTERN.matches(key)) { "Theme asset key must be a member ID: $key" }
        require(SHA256_PATTERN.matches(sha256)) { "Theme asset digest must be lowercase sha-256." }
        require(byteSize > 0) { "Theme asset byte size must be positive." }
    }
}

@Serializable
internal data class ThemePackageVariantV1(
    val id: String,
    val label: ThemePackageLocalizedTextV1,
) {
    init {
        require(MEMBER_ID_PATTERN.matches(id)) { "Theme variant ID must be a member ID: $id" }
    }
}

@Serializable
internal enum class ThemeParameterTypeV1 {
    COLOR,
    BOOLEAN,
    INTEGER,
    DECIMAL,
    STRING,
}

@Serializable
internal sealed interface ThemeParameterDefaultV1 {
    @Serializable
    @SerialName("color")
    data class ColorValue(val argb: Long) : ThemeParameterDefaultV1 {
        init {
            require(argb in 0..0xFFFFFFFFL) { "Color default must be ARGB within 0..0xffffffff." }
        }
    }

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : ThemeParameterDefaultV1

    @Serializable
    @SerialName("integer")
    data class IntegerValue(val value: Long) : ThemeParameterDefaultV1

    @Serializable
    @SerialName("decimal")
    data class DecimalValue(val value: Double) : ThemeParameterDefaultV1

    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : ThemeParameterDefaultV1

    @Serializable
    @SerialName("unset")
    data object Unset : ThemeParameterDefaultV1
}

@Serializable
internal data class ThemeParameterDefinitionV1(
    val id: String,
    val type: ThemeParameterTypeV1,
    val defaultValue: ThemeParameterDefaultV1 = ThemeParameterDefaultV1.Unset,
    val label: ThemePackageLocalizedTextV1,
) {
    init {
        require(MEMBER_ID_PATTERN.matches(id)) { "Theme parameter ID must be a member ID: $id" }
        require(defaultValue.matches(type)) {
            "Theme parameter $id declares type $type with a mismatched default value."
        }
    }
}

@Serializable
internal data class ThemePackageCapabilitiesV1(
    val hostSurfaces: Set<String>,
    val scenes: Set<String>,
) {
    init {
        require(hostSurfaces.isNotEmpty()) { "Theme package must declare at least one host surface." }
        require(scenes.isNotEmpty()) { "Theme package must declare at least one scene." }
    }
}

/** Root document of one `.otheme` archive, parsed strictly (unknown keys rejected). */
@Serializable
internal data class ThemePackageManifestV1(
    val schemaVersion: Int,
    val packageId: String,
    val version: String,
    val displayName: ThemePackageLocalizedTextV1,
    val author: ThemePackageLocalizedTextV1? = null,
    val description: ThemePackageLocalizedTextV1? = null,
    val basis: ThemePackageCoordinateV1? = null,
    val capabilities: ThemePackageCapabilitiesV1,
    val variants: List<ThemePackageVariantV1> = emptyList(),
    val parameters: List<ThemeParameterDefinitionV1> = emptyList(),
    val assets: List<ThemePackageAssetEntryV1> = emptyList(),
    val tokens: ThemeSceneTokenSetV1 = ThemeSceneTokenSetV1(),
    val scenes: List<ThemeSceneDefinitionV1> = emptyList(),
) {
    init {
        require(schemaVersion == THEME_PACKAGE_SCHEMA_VERSION) {
            "Theme package schema version must be $THEME_PACKAGE_SCHEMA_VERSION."
        }
        // Reusing the shipped value classes validates packageId/version format.
        ThemePackageIdV1(packageId)
        ThemePackageVersionV1(version)
        require(assets.map { it.key }.distinct().size == assets.size) {
            "Theme asset keys must be unique."
        }
        require(assets.map { it.path }.distinct().size == assets.size) {
            "Theme asset paths must be unique."
        }
        require(parameters.map { it.id }.distinct().size == parameters.size) {
            "Theme parameter IDs must be unique."
        }
        require(variants.map { it.id }.distinct().size == variants.size) {
            "Theme variant IDs must be unique."
        }
        scenes.forEach { scene ->
            require(capabilities.scenes.contains(scene.sceneId.value)) {
                "Theme package scene ${scene.sceneId.value} is not declared in capabilities."
            }
        }
    }

    fun coordinateFor(archiveSha256: ThemeArchiveSha256V1): ThemePackageCoordinateV1 =
        ThemePackageCoordinateV1(
            packageId = ThemePackageIdV1(packageId),
            version = ThemePackageVersionV1(version),
            archiveSha256 = archiveSha256,
        )

    fun variantIds(): Set<String> = variants.map { it.id }.toSet()

    fun parameterDefinition(id: String): ThemeParameterDefinitionV1? =
        parameters.firstOrNull { it.id == id }
}

internal fun ThemeParameterDefaultV1.matches(type: ThemeParameterTypeV1): Boolean =
    when (type) {
        ThemeParameterTypeV1.COLOR -> this is ThemeParameterDefaultV1.ColorValue || this is ThemeParameterDefaultV1.Unset
        ThemeParameterTypeV1.BOOLEAN -> this is ThemeParameterDefaultV1.BooleanValue || this is ThemeParameterDefaultV1.Unset
        ThemeParameterTypeV1.INTEGER -> this is ThemeParameterDefaultV1.IntegerValue || this is ThemeParameterDefaultV1.Unset
        ThemeParameterTypeV1.DECIMAL -> this is ThemeParameterDefaultV1.DecimalValue || this is ThemeParameterDefaultV1.Unset
        ThemeParameterTypeV1.STRING -> this is ThemeParameterDefaultV1.StringValue || this is ThemeParameterDefaultV1.Unset
    }

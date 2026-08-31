package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val MEMBER_ID_PATTERN_V2 = Regex("^[a-z][a-z0-9_]*$")
private val SHA256_PATTERN_V2 = Regex("^[0-9a-f]{64}$")

internal const val THEME_PACKAGE_SCHEMA_VERSION_V2 = 2
internal const val THEME_PACKAGE_MANIFEST_ENTRY_V2 = "operit-theme.json"
internal const val THEME_PACKAGE_EXTENSION_V2 = "otheme"
internal const val THEME_PACKAGE_ZIP_COMMENT_V2 = "Operit Theme Package"

/** Locale-keyed text; the key '*' is the required default locale entry. */
@Serializable
internal data class ThemePackageLocalizedTextV2(
    val values: Map<String, String>,
) {
    init {
        require(values.isNotEmpty()) { "Localized text must declare at least one entry." }
        require(values.containsKey("*")) { "Localized text must declare the default '*' entry." }
        require(values.values.all { it.isNotEmpty() }) { "Localized text entries must not be empty." }
    }

    fun resolve(locale: String): String = values[locale] ?: values.getValue("*")
}

@Serializable
internal enum class ThemeAssetKindV2 {
    BITMAP,
    NINE_SLICE,
    FONT,
    PATH,
}

@Serializable
internal data class ThemePackageAssetEntryV2(
    val key: String,
    val path: String,
    val kind: ThemeAssetKindV2,
    val sha256: String,
    val byteSize: Long,
) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(key)) { "Theme asset key must be a member ID: $key" }
        require(
            path.isNotBlank() &&
                !path.startsWith('/') &&
                !path.contains('\\') &&
                !path.contains(':') &&
                path.split('/').none { segment -> segment == ".." },
        ) {
            "Theme asset path must be a portable relative archive path: $path"
        }
        require(SHA256_PATTERN_V2.matches(sha256)) { "Theme asset digest must be lowercase sha-256." }
        require(byteSize > 0) { "Theme asset byte size must be positive." }
    }
}

@Serializable
internal data class ThemePackageVariantV2(
    val id: String,
    val label: ThemePackageLocalizedTextV2,
) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(id)) { "Theme variant ID must be a member ID: $id" }
    }
}

@Serializable
internal data class ThemePackageAttributionV2(
    val text: ThemePackageLocalizedTextV2,
    val sourceUrl: String,
) {
    init {
        require(sourceUrl.startsWith("https://")) {
            "Theme package attribution source URL must use HTTPS."
        }
    }
}

@Serializable
internal enum class ThemeParameterTypeV2 {
    COLOR,
    BOOLEAN,
    INTEGER,
    DECIMAL,
    STRING,
}

@Serializable
internal sealed interface ThemeParameterDefaultV2 {
    @Serializable
    @SerialName("color")
    data class ColorValue(val argb: Long) : ThemeParameterDefaultV2 {
        init {
            require(argb in 0..0xFFFFFFFFL) { "Color default must be ARGB within 0..0xffffffff." }
        }
    }

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : ThemeParameterDefaultV2

    @Serializable
    @SerialName("integer")
    data class IntegerValue(val value: Long) : ThemeParameterDefaultV2

    @Serializable
    @SerialName("decimal")
    data class DecimalValue(val value: Double) : ThemeParameterDefaultV2

    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : ThemeParameterDefaultV2

    @Serializable
    @SerialName("unset")
    data object Unset : ThemeParameterDefaultV2
}

@Serializable
internal data class ThemeParameterDefinitionV2(
    val id: String,
    val type: ThemeParameterTypeV2,
    val defaultValue: ThemeParameterDefaultV2 = ThemeParameterDefaultV2.Unset,
    val label: ThemePackageLocalizedTextV2,
) {
    init {
        ThemeParameterIdV2(id)
        require(defaultValue.matches(type)) {
            "Theme parameter $id declares type $type with a mismatched default value."
        }
    }
}

@Serializable
internal enum class ThemeSystemFontFamilyV2 {
    DEFAULT,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
}

@Serializable
internal data class ThemeTypographyV2(
    val family: ThemeSystemFontFamilyV2 = ThemeSystemFontFamilyV2.DEFAULT,
    val displayScale: Float = 1f,
    val titleScale: Float = 1f,
    val bodyScale: Float = 1f,
    val labelScale: Float = 1f,
    val letterSpacingEm: Float = 0f,
) {
    init {
        listOf(displayScale, titleScale, bodyScale, labelScale).forEach { scale ->
            require(scale in 0.5f..2f) { "Theme typography scales must be within [0.5, 2.0]." }
        }
        require(letterSpacingEm in -0.08f..0.2f) {
            "Theme typography letter spacing must be within [-0.08, 0.2]."
        }
    }
}

@Serializable
internal data class ThemeShapesV2(
    val extraSmallDp: Float,
    val smallDp: Float,
    val mediumDp: Float,
    val largeDp: Float,
    val extraLargeDp: Float,
) {
    init {
        listOf(extraSmallDp, smallDp, mediumDp, largeDp, extraLargeDp).forEach { radius ->
            require(radius in 0f..96f) { "Theme shape radii must be within [0, 96] dp." }
        }
    }
}

/** Full token-backed Material projection. Themes own every role used by native Material widgets. */
@Serializable
internal data class ThemeMaterialColorSchemeV2(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val inversePrimary: String,
    val secondary: String,
    val onSecondary: String,
    val secondaryContainer: String,
    val onSecondaryContainer: String,
    val tertiary: String,
    val onTertiary: String,
    val tertiaryContainer: String,
    val onTertiaryContainer: String,
    val background: String,
    val onBackground: String,
    val surface: String,
    val onSurface: String,
    val surfaceVariant: String,
    val onSurfaceVariant: String,
    val surfaceTint: String,
    val inverseSurface: String,
    val inverseOnSurface: String,
    val error: String,
    val onError: String,
    val errorContainer: String,
    val onErrorContainer: String,
    val outline: String,
    val outlineVariant: String,
    val scrim: String,
    val surfaceBright: String,
    val surfaceDim: String,
    val surfaceContainerLowest: String,
    val surfaceContainerLow: String,
    val surfaceContainer: String,
    val surfaceContainerHigh: String,
    val surfaceContainerHighest: String,
) {
    fun tokenIds(): Set<String> =
        setOf(
            primary,
            onPrimary,
            primaryContainer,
            onPrimaryContainer,
            inversePrimary,
            secondary,
            onSecondary,
            secondaryContainer,
            onSecondaryContainer,
            tertiary,
            onTertiary,
            tertiaryContainer,
            onTertiaryContainer,
            background,
            onBackground,
            surface,
            onSurface,
            surfaceVariant,
            onSurfaceVariant,
            surfaceTint,
            inverseSurface,
            inverseOnSurface,
            error,
            onError,
            errorContainer,
            onErrorContainer,
            outline,
            outlineVariant,
            scrim,
            surfaceBright,
            surfaceDim,
            surfaceContainerLowest,
            surfaceContainerLow,
            surfaceContainer,
            surfaceContainerHigh,
            surfaceContainerHighest,
        ).also { ids -> ids.forEach(::ThemeSceneTokenIdV1) }

    companion object {
        /** 单 token 填充全部角色；仅用于测试与工具，真实主题必须给出完整角色表。 */
        fun uniform(token: String): ThemeMaterialColorSchemeV2 =
            ThemeMaterialColorSchemeV2(
                primary = token,
                onPrimary = token,
                primaryContainer = token,
                onPrimaryContainer = token,
                inversePrimary = token,
                secondary = token,
                onSecondary = token,
                secondaryContainer = token,
                onSecondaryContainer = token,
                tertiary = token,
                onTertiary = token,
                tertiaryContainer = token,
                onTertiaryContainer = token,
                background = token,
                onBackground = token,
                surface = token,
                onSurface = token,
                surfaceVariant = token,
                onSurfaceVariant = token,
                surfaceTint = token,
                inverseSurface = token,
                inverseOnSurface = token,
                error = token,
                onError = token,
                errorContainer = token,
                onErrorContainer = token,
                outline = token,
                outlineVariant = token,
                scrim = token,
                surfaceBright = token,
                surfaceDim = token,
                surfaceContainerLowest = token,
                surfaceContainerLow = token,
                surfaceContainer = token,
                surfaceContainerHigh = token,
                surfaceContainerHighest = token,
            )
    }
}

@Serializable
internal data class ThemeMaterialProjectionV2(
    val colors: ThemeMaterialColorSchemeV2,
    val typography: ThemeTypographyV2,
    val shapes: ThemeShapesV2,
)

@Serializable
internal data class ThemeComponentInsetsV2(
    val startDp: Float = 0f,
    val topDp: Float = 0f,
    val endDp: Float = 0f,
    val bottomDp: Float = 0f,
) {
    init {
        listOf(startDp, topDp, endDp, bottomDp).forEach { value ->
            require(value in 0f..96f) { "Theme component insets must be within [0, 96] dp." }
        }
    }
}

@Serializable
internal data class ThemeComponentFrameStrokeV2(
    val token: String,
    val widthDp: Float,
) {
    init {
        ThemeSceneTokenIdV1(token)
        require(widthDp in 0.25f..16f) { "Theme component frame stroke width must be within [0.25, 16] dp." }
    }
}

/** Explicit geometry owned by a component skin; no implicit outline or corner treatment remains. */
@Serializable
internal sealed interface ThemeComponentFrameSpecV2 {
    @Serializable
    @SerialName("none")
    data object None : ThemeComponentFrameSpecV2

    @Serializable
    @SerialName("round_rect")
    data class RoundRect(
        val cornerRadiusDp: Float,
        val border: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cornerRadiusDp in 0f..96f) { "Round-rect frame radius must be within [0, 96] dp." }
        }
    }

    @Serializable
    @SerialName("cut_corners")
    data class CutCorners(
        val cutSizeDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cutSizeDp in 0.5f..48f) { "Cut-corner frame size must be within [0.5, 48] dp." }
        }
    }

    @Serializable
    @SerialName("hud_notched")
    data class HudNotched(
        val cutSizeDp: Float,
        val notchWidthFraction: Float,
        val notchDepthDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cutSizeDp in 0.5f..48f) { "HUD frame cut size must be within [0.5, 48] dp." }
            require(notchWidthFraction in 0.1f..0.7f) {
                "HUD frame notch width fraction must be within [0.1, 0.7]."
            }
            require(notchDepthDp in 0.5f..48f) { "HUD frame notch depth must be within [0.5, 48] dp." }
        }
    }

    @Serializable
    @SerialName("corner_brackets")
    data class CornerBrackets(
        val cornerCutDp: Float,
        val bracketLengthDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cornerCutDp in 0f..48f) { "Bracket frame corner cut must be within [0, 48] dp." }
            require(bracketLengthDp in 4f..96f) {
                "Bracket frame length must be within [4, 96] dp."
            }
        }
    }

    @Serializable
    @SerialName("segmented_rail")
    data class SegmentedRail(
        val cornerCutDp: Float,
        val railInsetDp: Float,
        val segmentLengthDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cornerCutDp in 0f..48f) { "Rail frame corner cut must be within [0, 48] dp." }
            require(railInsetDp in 0f..48f) { "Rail frame inset must be within [0, 48] dp." }
            require(segmentLengthDp in 4f..160f) {
                "Rail frame segment length must be within [4, 160] dp."
            }
        }
    }
}

internal fun ThemeComponentFrameSpecV2.strokes(): List<ThemeComponentFrameStrokeV2> =
    when (this) {
        ThemeComponentFrameSpecV2.None -> emptyList()
        is ThemeComponentFrameSpecV2.RoundRect -> listOfNotNull(border)
        is ThemeComponentFrameSpecV2.CutCorners -> listOfNotNull(border, accent)
        is ThemeComponentFrameSpecV2.HudNotched -> listOfNotNull(border, accent)
        is ThemeComponentFrameSpecV2.CornerBrackets -> listOfNotNull(border, accent)
        is ThemeComponentFrameSpecV2.SegmentedRail -> listOf(border, accent)
    }

@Serializable
internal data class ThemeComponentStateSkinV2(
    val containerToken: String,
    val contentToken: String,
    val frame: ThemeComponentFrameSpecV2,
    val elevationDp: Float = 0f,
    val contentPadding: ThemeComponentInsetsV2 = ThemeComponentInsetsV2(),
) {
    init {
        ThemeSceneTokenIdV1(containerToken)
        ThemeSceneTokenIdV1(contentToken)
        require(elevationDp in 0f..48f) { "Theme component elevation must be within [0, 48] dp." }
    }
}

@Serializable
internal data class ThemeComponentSkinV2(
    val normal: ThemeComponentStateSkinV2,
    val disabled: ThemeComponentStateSkinV2? = null,
    val selected: ThemeComponentStateSkinV2? = null,
    val focused: ThemeComponentStateSkinV2? = null,
    val error: ThemeComponentStateSkinV2? = null,
)

@Serializable
internal enum class ThemeSurfaceImplementationKindV2 {
    SCENE,
    TEMPLATE,
    HOST_SHELL,
}

@Serializable
internal data class ThemeSurfaceImplementationV2(
    val surfaceId: String,
    val kind: ThemeSurfaceImplementationKindV2,
    val sceneId: String? = null,
) {
    init {
        ThemeSurfaceIdV2(surfaceId)
        when (kind) {
            ThemeSurfaceImplementationKindV2.SCENE -> {
                require(!sceneId.isNullOrBlank()) { "A scene surface must declare its scene ID." }
                ThemeSceneIdV1(requireNotNull(sceneId))
            }

            ThemeSurfaceImplementationKindV2.TEMPLATE,
            ThemeSurfaceImplementationKindV2.HOST_SHELL,
            -> require(sceneId == null) { "Only scene surfaces may declare a scene ID." }
        }
    }
}

/** A child package may explicitly override only the presentation it owns; its base supplies the rest. */
@Serializable
internal data class ThemePackagePresentationPatchV2(
    val material: ThemeMaterialProjectionV2? = null,
    val componentSkins: Map<String, ThemeComponentSkinV2> = emptyMap(),
) {
    init {
        componentSkins.keys.forEach(::ThemeComponentIdV2)
    }
}

/** Root document of one V2 `.otheme` archive, parsed strictly with unknown keys rejected. */
@Serializable
internal data class ThemePackageManifestV2(
    val schemaVersion: Int,
    val packageId: String,
    val version: String,
    val displayName: ThemePackageLocalizedTextV2,
    val author: ThemePackageLocalizedTextV2? = null,
    val description: ThemePackageLocalizedTextV2? = null,
    val attribution: ThemePackageAttributionV2? = null,
    val basis: ThemePackageCoordinateV2? = null,
    val variants: List<ThemePackageVariantV2> = emptyList(),
    val parameters: List<ThemeParameterDefinitionV2> = emptyList(),
    val assets: List<ThemePackageAssetEntryV2> = emptyList(),
    val tokens: ThemeSceneTokenSetV1 = ThemeSceneTokenSetV1(),
    val scenes: List<ThemeSceneDefinitionV1> = emptyList(),
    val surfaces: List<ThemeSurfaceImplementationV2> = emptyList(),
    val presentation: ThemePackagePresentationPatchV2 = ThemePackagePresentationPatchV2(),
) {
    init {
        require(schemaVersion == THEME_PACKAGE_SCHEMA_VERSION_V2) {
            "Theme package schema version must be $THEME_PACKAGE_SCHEMA_VERSION_V2."
        }
        ThemePackageIdV2(packageId)
        ThemePackageVersionV2(version)
        require(assets.map { it.key }.distinct().size == assets.size) { "Theme asset keys must be unique." }
        require(assets.map { it.path }.distinct().size == assets.size) { "Theme asset paths must be unique." }
        require(parameters.map { it.id }.distinct().size == parameters.size) { "Theme parameter IDs must be unique." }
        require(variants.map { it.id }.distinct().size == variants.size) { "Theme variant IDs must be unique." }
        require(scenes.map { it.sceneId }.distinct().size == scenes.size) { "Theme scene IDs must be unique." }
        require(surfaces.map { it.surfaceId }.distinct().size == surfaces.size) {
            "Theme surface implementations must be unique."
        }
        basis?.let { base ->
            require(base.packageId.value != packageId) { "Theme package cannot use itself as its basis." }
        }
        surfaces.filter { it.kind == ThemeSurfaceImplementationKindV2.SCENE }.forEach { surface ->
            require(scenes.any { scene -> scene.sceneId.value == surface.sceneId }) {
                "Theme surface ${surface.surfaceId} references a missing scene ${surface.sceneId}."
            }
        }
    }

    fun coordinateFor(archiveSha256: ThemeArchiveSha256V2): ThemePackageCoordinateV2 =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(packageId),
            version = ThemePackageVersionV2(version),
            archiveSha256 = archiveSha256,
        )

    fun parameterDefinition(id: String): ThemeParameterDefinitionV2? =
        parameters.firstOrNull { it.id == id }
}

internal fun ThemeParameterDefaultV2.matches(type: ThemeParameterTypeV2): Boolean =
    when (type) {
        ThemeParameterTypeV2.COLOR -> this is ThemeParameterDefaultV2.ColorValue || this is ThemeParameterDefaultV2.Unset
        ThemeParameterTypeV2.BOOLEAN -> this is ThemeParameterDefaultV2.BooleanValue || this is ThemeParameterDefaultV2.Unset
        ThemeParameterTypeV2.INTEGER -> this is ThemeParameterDefaultV2.IntegerValue || this is ThemeParameterDefaultV2.Unset
        ThemeParameterTypeV2.DECIMAL -> this is ThemeParameterDefaultV2.DecimalValue || this is ThemeParameterDefaultV2.Unset
        ThemeParameterTypeV2.STRING -> this is ThemeParameterDefaultV2.StringValue || this is ThemeParameterDefaultV2.Unset
    }

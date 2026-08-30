package com.ai.assistance.operit.ui.theme.scene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val SCENE_ID_PATTERN = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$")
private val DOTTED_MEMBER_ID_PATTERN = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*$")
private val MEMBER_ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")

@Serializable
@JvmInline
internal value class ThemeSceneIdV1(val value: String) {
    init {
        require(SCENE_ID_PATTERN.matches(value)) { "Invalid theme scene ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeSceneSlotIdV1(val value: String) {
    init {
        require(DOTTED_MEMBER_ID_PATTERN.matches(value)) { "Invalid theme scene slot ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeSceneNodeIdV1(val value: String) {
    init {
        require(MEMBER_ID_PATTERN.matches(value)) { "Invalid theme scene node ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeSceneAssetIdV1(val value: String) {
    init {
        require(MEMBER_ID_PATTERN.matches(value)) { "Invalid theme scene asset ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeSceneTokenIdV1(val value: String) {
    init {
        require(DOTTED_MEMBER_ID_PATTERN.matches(value)) { "Invalid theme scene token ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeSceneTextKeyIdV1(val value: String) {
    init {
        require(MEMBER_ID_PATTERN.matches(value)) { "Invalid theme scene text key: $value" }
    }
}

@Serializable
internal data class ThemeSceneVersionV1(
    val major: Int,
    val minor: Int,
) {
    init {
        require(major > 0) { "Theme scene major version must be positive." }
        require(minor >= 0) { "Theme scene minor version cannot be negative." }
    }
}

@Serializable
internal enum class ThemeSceneSlotCardinalityV1 {
    REQUIRED_SINGLE,
    OPTIONAL_SINGLE,
    REPEATED,
}

@Serializable
internal enum class ThemeSceneSlotReorderabilityV1 {
    FIXED,
    REORDERABLE,
}

@Serializable
internal data class ThemeSceneSlotContractV1(
    val slotId: ThemeSceneSlotIdV1,
    val cardinality: ThemeSceneSlotCardinalityV1,
    val reorderability: ThemeSceneSlotReorderabilityV1,
)

@Serializable
internal data class ThemeSceneContractV1(
    val sceneId: ThemeSceneIdV1,
    val version: ThemeSceneVersionV1,
    val slotContracts: List<ThemeSceneSlotContractV1>,
)

internal object ThemeSceneCatalogV1 {
    val APP_SHELL = ThemeSceneIdV1("app.shell")
    val CHAT_MAIN = ThemeSceneIdV1("chat.main")
    val SCENE_VERSION_1_0 = ThemeSceneVersionV1(major = 1, minor = 0)

    val contracts: Map<ThemeSceneIdV1, ThemeSceneContractV1> =
        listOf(appShellContract(), chatMainContract()).associateBy { it.sceneId }

    private fun appShellContract() =
        ThemeSceneContractV1(
            sceneId = APP_SHELL,
            version = SCENE_VERSION_1_0,
            slotContracts =
                listOf(
                    slot("app_bar.navigation", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("app_bar.title", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("app_bar.actions", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("navigation.identity_status", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.REORDERABLE),
                    slot("navigation.quick", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.REORDERABLE),
                    slot("navigation.primary", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.REORDERABLE),
                    slot("navigation.plugins", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.REORDERABLE),
                    slot("navigation.system", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.REORDERABLE),
                    slot("route.content", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("announcement", ThemeSceneSlotCardinalityV1.OPTIONAL_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                ),
        )

    private fun chatMainContract() =
        ThemeSceneContractV1(
            sceneId = CHAT_MAIN,
            version = SCENE_VERSION_1_0,
            slotContracts =
                listOf(
                    slot("configuration_gate", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("header", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("transcript", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("composer", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("classic_settings_rail", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                    slot("overlay_stack", ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE, ThemeSceneSlotReorderabilityV1.FIXED),
                ),
        )

    private fun slot(
        id: String,
        cardinality: ThemeSceneSlotCardinalityV1,
        reorderability: ThemeSceneSlotReorderabilityV1,
    ) = ThemeSceneSlotContractV1(
        slotId = ThemeSceneSlotIdV1(id),
        cardinality = cardinality,
        reorderability = reorderability,
    )
}

@Serializable
internal sealed interface ThemeSceneNodeV1 {
    val nodeId: ThemeSceneNodeIdV1
}

@Serializable
@SerialName("stage")
internal data class ThemeSceneStageNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val backgroundColorToken: ThemeSceneTokenIdV1? = null,
    val children: List<ThemeSceneNodeV1> = emptyList(),
) : ThemeSceneNodeV1

@Serializable
@SerialName("layer")
internal data class ThemeSceneLayerNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val children: List<ThemeSceneNodeV1> = emptyList(),
) : ThemeSceneNodeV1

@Serializable
@SerialName("row")
internal data class ThemeSceneRowNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val spacingDp: Float = 0f,
    val children: List<ThemeSceneNodeV1> = emptyList(),
) : ThemeSceneNodeV1

@Serializable
@SerialName("column")
internal data class ThemeSceneColumnNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val spacingDp: Float = 0f,
    val children: List<ThemeSceneNodeV1> = emptyList(),
) : ThemeSceneNodeV1

@Serializable
@SerialName("grid")
internal data class ThemeSceneGridNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val columns: Int,
    val spacingDp: Float = 0f,
    val children: List<ThemeSceneNodeV1> = emptyList(),
) : ThemeSceneNodeV1

@Serializable
@SerialName("frame")
internal data class ThemeSceneFrameNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val anchor: ThemeSceneAnchorV1? = null,
    val width: ThemeSceneSizeV1 = ThemeSceneSizeV1.Wrap,
    val height: ThemeSceneSizeV1 = ThemeSceneSizeV1.Wrap,
    val minWidthDp: Float? = null,
    val maxWidthDp: Float? = null,
    val contentPadding: ThemeSceneEdgeInsetsV1? = null,
    val child: ThemeSceneNodeV1,
) : ThemeSceneNodeV1

@Serializable
@SerialName("host_slot")
internal data class ThemeSceneHostSlotNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val slotId: ThemeSceneSlotIdV1,
    val contentPadding: ThemeSceneEdgeInsetsV1? = null,
) : ThemeSceneNodeV1

@Serializable
@SerialName("surface")
internal data class ThemeSceneSurfaceNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val fillToken: ThemeSceneTokenIdV1? = null,
    val outlineToken: ThemeSceneTokenIdV1? = null,
    val outlineWidthDp: Float = 0f,
    val cornerRadiusDp: Float = 0f,
    val opacity: Float = 1f,
    val child: ThemeSceneNodeV1? = null,
) : ThemeSceneNodeV1

@Serializable
internal enum class ThemeSceneImageFitV1 {
    FILL,
    FIT,
    CROP,
}

@Serializable
@SerialName("image")
internal data class ThemeSceneImageNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val assetId: ThemeSceneAssetIdV1,
    val fit: ThemeSceneImageFitV1 = ThemeSceneImageFitV1.CROP,
) : ThemeSceneNodeV1

@Serializable
@SerialName("nine_slice")
internal data class ThemeSceneNineSliceNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val assetId: ThemeSceneAssetIdV1,
    val capInsets: ThemeSceneEdgeInsetsV1,
    val child: ThemeSceneNodeV1? = null,
) : ThemeSceneNodeV1

@Serializable
@SerialName("text")
internal data class ThemeSceneTextNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val textKey: ThemeSceneTextKeyIdV1,
    val styleToken: ThemeSceneTokenIdV1? = null,
) : ThemeSceneNodeV1

@Serializable
@SerialName("path")
internal data class ThemeScenePathNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val assetId: ThemeSceneAssetIdV1,
    val fillToken: ThemeSceneTokenIdV1? = null,
    val outlineToken: ThemeSceneTokenIdV1? = null,
    val outlineWidthDp: Float = 0f,
    val opacity: Float = 1f,
) : ThemeSceneNodeV1

@Serializable
@SerialName("transform")
internal data class ThemeSceneTransformNodeV1(
    override val nodeId: ThemeSceneNodeIdV1,
    val translationXDp: Float = 0f,
    val translationYDp: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val alpha: Float = 1f,
    val child: ThemeSceneNodeV1,
) : ThemeSceneNodeV1

@Serializable
internal data class ThemeSceneAnchorV1(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

@Serializable
internal sealed interface ThemeSceneSizeV1 {
    @Serializable
    @SerialName("fill")
    data object Fill : ThemeSceneSizeV1

    @Serializable
    @SerialName("wrap")
    data object Wrap : ThemeSceneSizeV1

    @Serializable
    @SerialName("fraction")
    data class Fraction(val value: Float) : ThemeSceneSizeV1
}

@Serializable
internal data class ThemeSceneEdgeInsetsV1(
    val startDp: Float = 0f,
    val topDp: Float = 0f,
    val endDp: Float = 0f,
    val bottomDp: Float = 0f,
)

@Serializable
internal data class ThemeSceneDefinitionV1(
    val sceneId: ThemeSceneIdV1,
    val version: ThemeSceneVersionV1,
    val rootNode: ThemeSceneStageNodeV1,
)

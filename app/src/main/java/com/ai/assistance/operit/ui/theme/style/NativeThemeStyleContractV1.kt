package com.ai.assistance.operit.ui.theme.style

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val STYLE_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:[._][a-z0-9_]+)+$")
private val STYLE_MEMBER_ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")

@Serializable
@JvmInline
internal value class NativeThemeStyleTokenIdV1(val value: String) {
    init {
        require(STYLE_ID_PATTERN.matches(value)) { "Invalid style token ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class NativeThemeComponentFamilyIdV1(val value: String) {
    init {
        require(STYLE_ID_PATTERN.matches(value)) { "Invalid component family ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class NativeThemeStylePartIdV1(val value: String) {
    init {
        require(STYLE_MEMBER_ID_PATTERN.matches(value)) { "Invalid style part ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class NativeThemeStyleRuleIdV1(val value: String) {
    init {
        require(STYLE_MEMBER_ID_PATTERN.matches(value)) { "Invalid style rule ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class NativeThemeStyleResourceIdV1(val value: String) {
    init {
        require(STYLE_ID_PATTERN.matches(value)) { "Invalid style resource ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class NativeThemeStyleLayerIdV1(val value: String) {
    init {
        require(STYLE_ID_PATTERN.matches(value)) { "Invalid style layer ID: $value" }
    }
}

@Serializable
internal enum class NativeThemeStyleValueKindV1 {
    COLOR,
    OPACITY,
    TEXT,
    SHAPE,
    BORDER,
    SHADOW,
    MATERIAL,
    BLUR,
    ICON_CONTAINER,
    MENU,
    METRIC,
    MOTION,
}

@Serializable
internal enum class NativeThemeStylePropertyIdV1(
    val acceptedValueKinds: Set<NativeThemeStyleValueKindV1>,
    val allowsNone: Boolean,
) {
    SURFACE_COLOR(setOf(NativeThemeStyleValueKindV1.COLOR), false),
    CONTENT_COLOR(setOf(NativeThemeStyleValueKindV1.COLOR), false),
    TEXT_STYLE(setOf(NativeThemeStyleValueKindV1.TEXT), false),
    SHAPE(setOf(NativeThemeStyleValueKindV1.SHAPE), false),
    BORDER_STACK(setOf(NativeThemeStyleValueKindV1.BORDER), true),
    WHOLE_LAYER_OPACITY(setOf(NativeThemeStyleValueKindV1.OPACITY), false),
    CONTENT_BLUR(setOf(NativeThemeStyleValueKindV1.BLUR), true),
    BACKDROP_BLUR(setOf(NativeThemeStyleValueKindV1.BLUR), true),
    MATERIAL(setOf(NativeThemeStyleValueKindV1.MATERIAL), true),
    SHADOW_STACK(setOf(NativeThemeStyleValueKindV1.SHADOW), true),
    ICON_CONTAINER(setOf(NativeThemeStyleValueKindV1.ICON_CONTAINER), true),
    MENU(setOf(NativeThemeStyleValueKindV1.MENU), false),
    PADDING(setOf(NativeThemeStyleValueKindV1.METRIC), false),
    ICON_SIZE(setOf(NativeThemeStyleValueKindV1.METRIC), false),
    MOTION(setOf(NativeThemeStyleValueKindV1.MOTION), true),
}

@Serializable
internal enum class NativeThemeHostColorRoleV1 {
    PRIMARY,
    ON_PRIMARY,
    PRIMARY_CONTAINER,
    ON_PRIMARY_CONTAINER,
    SECONDARY,
    ON_SECONDARY,
    SECONDARY_CONTAINER,
    ON_SECONDARY_CONTAINER,
    TERTIARY,
    ON_TERTIARY,
    ERROR,
    ON_ERROR,
    SURFACE,
    SURFACE_VARIANT,
    SURFACE_CONTAINER,
    SURFACE_CONTAINER_HIGH,
    SURFACE_CONTAINER_HIGHEST,
    ON_SURFACE,
    ON_SURFACE_VARIANT,
    OUTLINE,
    OUTLINE_VARIANT,
    SCRIM,
    INVERSE_SURFACE,
    INVERSE_ON_SURFACE,
}

@Serializable
internal sealed interface NativeThemeStyleColorSourceV1 {
    @Serializable
    @SerialName("literal")
    data class Literal(
        val rgba: String,
    ) : NativeThemeStyleColorSourceV1

    @Serializable
    @SerialName("host_role")
    data class HostRole(
        val role: NativeThemeHostColorRoleV1,
    ) : NativeThemeStyleColorSourceV1
}

@Serializable
internal data class NativeThemeStyleColorSpecV1(
    val light: NativeThemeStyleColorSourceV1,
    val dark: NativeThemeStyleColorSourceV1,
)

@Serializable
internal enum class NativeThemeSystemFontFamilyV1 {
    SYSTEM,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    CURSIVE,
}

@Serializable
internal sealed interface NativeThemeStyleFontFamilyV1 {
    @Serializable
    @SerialName("system")
    data class System(
        val family: NativeThemeSystemFontFamilyV1,
    ) : NativeThemeStyleFontFamilyV1

    @Serializable
    @SerialName("asset")
    data class Asset(
        val resourceId: NativeThemeStyleResourceIdV1,
    ) : NativeThemeStyleFontFamilyV1
}

@Serializable
internal data class NativeThemeTextStyleSpecV1(
    val family: NativeThemeStyleFontFamilyV1,
    val fontSizeSp: Float,
    val lineHeightSp: Float,
    val fontWeight: Int,
    val letterSpacingEm: Float = 0f,
    val color: NativeThemeStyleColorSpecV1,
)

@Serializable
internal enum class NativeThemeStyleCornerRadiusUnitV1 {
    DP,
    PERCENT,
}

@Serializable
internal data class NativeThemeStyleCornerRadiusV1(
    val value: Float,
    val unit: NativeThemeStyleCornerRadiusUnitV1,
)

@Serializable
internal sealed interface NativeThemeStyleShapeSpecV1 {
    @Serializable
    @SerialName("rectangle")
    data object Rectangle : NativeThemeStyleShapeSpecV1

    @Serializable
    @SerialName("rounded_corners")
    data class RoundedCorners(
        val topStart: NativeThemeStyleCornerRadiusV1,
        val topEnd: NativeThemeStyleCornerRadiusV1,
        val bottomEnd: NativeThemeStyleCornerRadiusV1,
        val bottomStart: NativeThemeStyleCornerRadiusV1,
    ) : NativeThemeStyleShapeSpecV1

    @Serializable
    @SerialName("capsule")
    data object Capsule : NativeThemeStyleShapeSpecV1
}

@Serializable
internal enum class NativeThemeStyleBorderAlignmentV1 {
    INSIDE,
    CENTER,
    OUTSIDE,
}

@Serializable
internal enum class NativeThemeStyleBorderSideV1 {
    TOP,
    END,
    BOTTOM,
    START,
}

@Serializable
internal sealed interface NativeThemeStyleBrushV1 {
    @Serializable
    @SerialName("solid")
    data class Solid(
        val color: NativeThemeStyleColorSpecV1,
    ) : NativeThemeStyleBrushV1

    @Serializable
    @SerialName("linear_gradient")
    data class LinearGradient(
        val angleDegrees: Float,
        val stops: List<NativeThemeStyleGradientStopV1>,
    ) : NativeThemeStyleBrushV1

    @Serializable
    @SerialName("radial_gradient")
    data class RadialGradient(
        val centerX: Float,
        val centerY: Float,
        val radiusFraction: Float,
        val stops: List<NativeThemeStyleGradientStopV1>,
    ) : NativeThemeStyleBrushV1
}

@Serializable
internal data class NativeThemeStyleGradientStopV1(
    val offset: Float,
    val color: NativeThemeStyleColorSpecV1,
)

@Serializable
internal data class NativeThemeStyleDashPatternV1(
    val onLengthDp: Float,
    val offLengthDp: Float,
    val phaseDp: Float = 0f,
)

@Serializable
internal data class NativeThemeStyleBorderLayerV1(
    val id: NativeThemeStyleRuleIdV1,
    val sides: Set<NativeThemeStyleBorderSideV1>,
    val alignment: NativeThemeStyleBorderAlignmentV1,
    val widthDp: Float,
    val offsetDp: Float = 0f,
    val brush: NativeThemeStyleBrushV1,
    val opacity: Float = 1f,
    val dash: NativeThemeStyleDashPatternV1? = null,
)

@Serializable
internal data class NativeThemeStyleBorderStackSpecV1(
    val layers: List<NativeThemeStyleBorderLayerV1>,
)

@Serializable
internal enum class NativeThemeStyleShadowKindV1 {
    OUTER,
    INNER,
}

@Serializable
internal data class NativeThemeStyleShadowLayerV1(
    val id: NativeThemeStyleRuleIdV1,
    val kind: NativeThemeStyleShadowKindV1,
    val color: NativeThemeStyleColorSpecV1,
    val offsetXDp: Float,
    val offsetYDp: Float,
    val blurRadiusDp: Float,
    val spreadDp: Float = 0f,
)

@Serializable
internal data class NativeThemeStyleShadowStackSpecV1(
    val layers: List<NativeThemeStyleShadowLayerV1>,
)

@Serializable
internal enum class NativeThemeStyleBlurEdgeTreatmentV1 {
    CLIP,
    UNBOUNDED,
}

@Serializable
internal data class NativeThemeStyleBlurSpecV1(
    val radiusDp: Float,
    val edgeTreatment: NativeThemeStyleBlurEdgeTreatmentV1 = NativeThemeStyleBlurEdgeTreatmentV1.CLIP,
)

@Serializable
internal sealed interface NativeThemeStyleMaterialSpecV1 {
    @Serializable
    @SerialName("solid")
    data class Solid(
        val color: NativeThemeStyleColorSpecV1,
    ) : NativeThemeStyleMaterialSpecV1

    @Serializable
    @SerialName("translucent")
    data class Translucent(
        val tint: NativeThemeStyleColorSpecV1,
        val opacity: Float,
    ) : NativeThemeStyleMaterialSpecV1

    @Serializable
    @SerialName("frosted")
    data class Frosted(
        val tint: NativeThemeStyleColorSpecV1,
        val opacity: Float,
        val backdropBlurRadiusDp: Float,
        val saturation: Float,
        val contrast: Float,
        val grainOpacity: Float = 0f,
    ) : NativeThemeStyleMaterialSpecV1

    @Serializable
    @SerialName("liquid")
    data class Liquid(
        val tint: NativeThemeStyleColorSpecV1,
        val opacity: Float,
        val blurRadiusDp: Float,
        val vibrancy: Float,
        val lensHeightDp: Float,
        val refractionAmountDp: Float,
        val chromaticAberration: Boolean,
        val highlightWidthDp: Float,
        val highlightBlurDp: Float,
    ) : NativeThemeStyleMaterialSpecV1

    @Serializable
    @SerialName("water")
    data class Water(
        val tint: NativeThemeStyleColorSpecV1,
        val opacity: Float,
        val frostDp: Float,
        val curve: Float,
        val refraction: Float,
        val dispersion: Float,
        val saturation: Float,
        val contrast: Float,
    ) : NativeThemeStyleMaterialSpecV1
}

@Serializable
internal sealed interface NativeThemeStyleIconContainerSpecV1 {
    @Serializable
    @SerialName("none")
    data object None : NativeThemeStyleIconContainerSpecV1

    @Serializable
    @SerialName("container")
    data class Container(
        val containerSizeDp: Float,
        val iconSizeDp: Float,
        val shape: NativeThemeStyleShapeSpecV1,
        val material: NativeThemeStyleMaterialSpecV1,
        val contentColor: NativeThemeStyleColorSpecV1,
        val border: NativeThemeStyleBorderStackSpecV1? = null,
        val shadows: NativeThemeStyleShadowStackSpecV1? = null,
    ) : NativeThemeStyleIconContainerSpecV1
}

@Serializable
internal data class NativeThemeStyleInsetsV1(
    val startDp: Float,
    val topDp: Float,
    val endDp: Float,
    val bottomDp: Float,
)

@Serializable
internal data class NativeThemeStyleMenuItemSpecV1(
    val containerColor: NativeThemeStyleColorSpecV1,
    val contentColor: NativeThemeStyleColorSpecV1,
    val iconColor: NativeThemeStyleColorSpecV1,
)

@Serializable
internal data class NativeThemeStyleMenuSpecV1(
    val material: NativeThemeStyleMaterialSpecV1,
    val shape: NativeThemeStyleShapeSpecV1,
    val label: NativeThemeTextStyleSpecV1,
    val iconColor: NativeThemeStyleColorSpecV1,
    val dividerColor: NativeThemeStyleColorSpecV1,
    val dividerThicknessDp: Float,
    val widthDp: Float,
    val itemMinHeightDp: Float,
    val contentInsets: NativeThemeStyleInsetsV1,
    val selectedItem: NativeThemeStyleMenuItemSpecV1,
    val disabledItem: NativeThemeStyleMenuItemSpecV1,
    val destructiveItem: NativeThemeStyleMenuItemSpecV1,
    val border: NativeThemeStyleBorderStackSpecV1? = null,
    val shadows: NativeThemeStyleShadowStackSpecV1? = null,
)

@Serializable
internal enum class NativeThemeStyleMetricUnitV1 {
    DP,
    SP,
}

@Serializable
internal data class NativeThemeStyleMetricSpecV1(
    val value: Float,
    val unit: NativeThemeStyleMetricUnitV1,
)

@Serializable
internal enum class NativeThemeStyleMotionEasingV1 {
    LINEAR,
    STANDARD,
    EMPHASIZED,
    DECELERATE,
}

@Serializable
internal data class NativeThemeStyleMotionSpecV1(
    val durationMillis: Int,
    val easing: NativeThemeStyleMotionEasingV1,
)

@Serializable
internal sealed interface NativeThemeStyleValueV1 {
    @Serializable
    @SerialName("none")
    data object None : NativeThemeStyleValueV1

    @Serializable
    @SerialName("token_reference")
    data class TokenReference(
        val tokenId: NativeThemeStyleTokenIdV1,
        val expectedKind: NativeThemeStyleValueKindV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("color")
    data class Color(
        val value: NativeThemeStyleColorSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("opacity")
    data class Opacity(
        val value: Float,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("text")
    data class Text(
        val value: NativeThemeTextStyleSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("shape")
    data class Shape(
        val value: NativeThemeStyleShapeSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("border")
    data class Border(
        val value: NativeThemeStyleBorderStackSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("shadow")
    data class Shadow(
        val value: NativeThemeStyleShadowStackSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("material")
    data class Material(
        val value: NativeThemeStyleMaterialSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("blur")
    data class Blur(
        val value: NativeThemeStyleBlurSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("icon_container")
    data class IconContainer(
        val value: NativeThemeStyleIconContainerSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("menu")
    data class Menu(
        val value: NativeThemeStyleMenuSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("metric")
    data class Metric(
        val value: NativeThemeStyleMetricSpecV1,
    ) : NativeThemeStyleValueV1

    @Serializable
    @SerialName("motion")
    data class Motion(
        val value: NativeThemeStyleMotionSpecV1,
    ) : NativeThemeStyleValueV1
}

internal fun NativeThemeStyleValueV1.valueKindOrNull(): NativeThemeStyleValueKindV1? =
    when (this) {
        NativeThemeStyleValueV1.None -> null
        is NativeThemeStyleValueV1.TokenReference -> expectedKind
        is NativeThemeStyleValueV1.Color -> NativeThemeStyleValueKindV1.COLOR
        is NativeThemeStyleValueV1.Opacity -> NativeThemeStyleValueKindV1.OPACITY
        is NativeThemeStyleValueV1.Text -> NativeThemeStyleValueKindV1.TEXT
        is NativeThemeStyleValueV1.Shape -> NativeThemeStyleValueKindV1.SHAPE
        is NativeThemeStyleValueV1.Border -> NativeThemeStyleValueKindV1.BORDER
        is NativeThemeStyleValueV1.Shadow -> NativeThemeStyleValueKindV1.SHADOW
        is NativeThemeStyleValueV1.Material -> NativeThemeStyleValueKindV1.MATERIAL
        is NativeThemeStyleValueV1.Blur -> NativeThemeStyleValueKindV1.BLUR
        is NativeThemeStyleValueV1.IconContainer -> NativeThemeStyleValueKindV1.ICON_CONTAINER
        is NativeThemeStyleValueV1.Menu -> NativeThemeStyleValueKindV1.MENU
        is NativeThemeStyleValueV1.Metric -> NativeThemeStyleValueKindV1.METRIC
        is NativeThemeStyleValueV1.Motion -> NativeThemeStyleValueKindV1.MOTION
    }

@Serializable
internal data class NativeThemeStyleTokenV1(
    val id: NativeThemeStyleTokenIdV1,
    val value: NativeThemeStyleValueV1,
)

@Serializable
internal data class NativeThemeStylePropertyV1(
    val id: NativeThemeStylePropertyIdV1,
    val value: NativeThemeStyleValueV1,
)

@Serializable
internal data class NativeThemeStylePartPatchV1(
    val part: NativeThemeStylePartIdV1,
    val properties: List<NativeThemeStylePropertyV1>,
)

@Serializable
internal data class NativeThemeStylePatchV1(
    val parts: List<NativeThemeStylePartPatchV1> = emptyList(),
)

@Serializable
internal enum class NativeThemeStyleStateAxisV1 {
    AVAILABILITY,
    SELECTION,
    INTERACTION,
    ACTIVITY,
    VALIDATION,
    EXPANSION,
    CONTENT,
    VARIANT,
}

@Serializable
internal enum class NativeThemeStyleStateValueV1 {
    ENABLED,
    DISABLED,
    SELECTED,
    UNSELECTED,
    RESTING,
    PRESSED,
    FOCUSED,
    HOVERED,
    IDLE,
    LOADING,
    STREAMING,
    SUCCESS,
    ERROR,
    VALID,
    INVALID,
    COLLAPSED,
    EXPANDED,
    EMPTY,
    POPULATED,
    STANDARD,
    CAUTION,
    DESTRUCTIVE,
    NAVIGATION_DESTINATION,
    ACTION,
}

@Serializable
internal data class NativeThemeStyleStateConditionV1(
    val axis: NativeThemeStyleStateAxisV1,
    val value: NativeThemeStyleStateValueV1,
)

@Serializable
internal data class NativeThemeStyleStateAxisContractV1(
    val axis: NativeThemeStyleStateAxisV1,
    val values: Set<NativeThemeStyleStateValueV1>,
)

@Serializable
internal data class NativeThemeStyleStateSelectorV1(
    val conditions: List<NativeThemeStyleStateConditionV1>,
)

@Serializable
internal data class NativeThemeStyleStateRuleV1(
    val id: NativeThemeStyleRuleIdV1,
    val selector: NativeThemeStyleStateSelectorV1,
    val surfaces: Set<NativeThemeHostSurface> = emptySet(),
    val patch: NativeThemeStylePatchV1,
)

@Serializable
internal data class NativeThemeStyleSurfacePatchV1(
    val surface: NativeThemeHostSurface,
    val patch: NativeThemeStylePatchV1,
)

@Serializable
internal data class NativeThemeStyleScopeV1(
    val common: NativeThemeStylePatchV1 = NativeThemeStylePatchV1(),
    val surfaceOverrides: List<NativeThemeStyleSurfacePatchV1> = emptyList(),
    val stateRules: List<NativeThemeStyleStateRuleV1> = emptyList(),
)

@Serializable
internal data class NativeThemeComponentFamilyStyleV1(
    val familyId: NativeThemeComponentFamilyIdV1,
    val scope: NativeThemeStyleScopeV1,
)

@Serializable
internal data class NativeThemeComponentStyleV1(
    val componentId: NativeThemeComponentId,
    val scope: NativeThemeStyleScopeV1,
)

@Serializable
internal data class NativeThemeStyleLayerV1(
    val id: NativeThemeStyleLayerIdV1,
    val tokens: List<NativeThemeStyleTokenV1> = emptyList(),
    val familyStyles: List<NativeThemeComponentFamilyStyleV1> = emptyList(),
    val componentStyles: List<NativeThemeComponentStyleV1> = emptyList(),
)

@Serializable
internal data class NativeThemeStyleCascadeV1(
    val foundation: NativeThemeStyleLayerV1,
    val themeLayers: List<NativeThemeStyleLayerV1>,
    val instance: NativeThemeStyleLayerV1,
) {
    fun orderedLayers(): List<NativeThemeStyleLayerV1> =
        listOf(foundation) + themeLayers + instance
}

@Serializable
internal data class NativeThemeStyleStateVectorV1(
    val values: Map<NativeThemeStyleStateAxisV1, NativeThemeStyleStateValueV1>,
)

internal data class NativeThemeStyleResolutionRequestV1(
    val componentId: NativeThemeComponentId,
    val surface: NativeThemeHostSurface,
    val state: NativeThemeStyleStateVectorV1,
)

internal data class NativeThemeResolvedComponentStyleV1(
    val tokens: Map<NativeThemeStyleTokenIdV1, NativeThemeStyleValueV1>,
    val parts: Map<NativeThemeStylePartIdV1, Map<NativeThemeStylePropertyIdV1, NativeThemeStyleValueV1>>,
)

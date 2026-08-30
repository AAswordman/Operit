package com.ai.assistance.operit.ui.theme.style.compose

import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.ResolvedNativeThemeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStylePartIdsV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatContractV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeComposeHostCapabilityProfileV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeComposeCapabilityProfileIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeComponentStyleV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeResolvedComponentStyleV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderAlignmentV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderLayerV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderSideV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderStackSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBrushV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleCascadeV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleColorSourceV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleColorSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleCornerRadiusUnitV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleCornerRadiusV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleFontFamilyV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleIconContainerSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLayerIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLayerV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleMaterialSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleMetricSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleMetricUnitV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePartIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePartPatchV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePatchV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePropertyIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePropertyV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleResolutionRequestV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleRuleIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleScopeV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleShapeSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateVectorV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleTokenIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleValueV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeSystemFontFamilyV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeTextStyleSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLinkRequestV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLinkResultV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleIssueCodeV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleIssueV1
import com.ai.assistance.operit.ui.theme.style.linkNativeThemeStyleV1
import java.util.Locale
import kotlin.math.roundToInt

internal data class NativeThemeStatTextStylePlanV1(
    val spec: NativeThemeTextStyleSpecV1,
    val color: Color,
)

internal data class NativeThemeStatStylePlanV1(
    val surfaceColor: Color,
    val opacity: Float,
    val shape: NativeThemeStyleShapeSpecV1,
    val border: NativeThemeStyleBorderStackSpecV1?,
    val contentPaddingDp: Float,
    val leadingColor: Color,
    val leadingIconContainer: NativeThemeStyleIconContainerSpecV1.Container?,
    val value: NativeThemeStatTextStylePlanV1,
    val label: NativeThemeStatTextStylePlanV1,
)

internal object NativeThemeNativeV1StyleCompilerV1 {
    private val statHostProfile =
        NativeThemeComposeHostCapabilityProfileV1(
            id = NativeThemeComposeCapabilityProfileIdV1("operit.compose.native_v1.stat"),
            capabilities = emptyList(),
        )

    fun linkStat(
        resolvedTheme: ResolvedNativeThemeV1,
        instanceLayer: NativeThemeStyleLayerV1,
    ): NativeThemeStyleLinkResultV1 {
        val result =
            linkNativeThemeStyleV1(
                NativeThemeStyleLinkRequestV1(
                    cascade =
                        NativeThemeStyleCascadeV1(
                            foundation = statFoundation(resolvedTheme),
                            themeLayers = emptyList(),
                            instance = instanceLayer,
                        ),
                    componentContracts = listOf(NativeThemeStatContractV1.contract),
                    declaredCapabilities = emptyList(),
                hostCapabilityProfile = statHostProfile,
                ),
            )
        if (result !is NativeThemeStyleLinkResultV1.Linked) return result

        val issues = mutableListOf<NativeThemeStyleIssueV1>()
        setOf(NativeThemeHostSurface.MAIN, NativeThemeHostSurface.EDITOR_PREVIEW).forEach { surface ->
            val resolved =
                result.resolve(
                    NativeThemeStyleResolutionRequestV1(
                        componentId = NativeThemeStatContractV1.contract.id,
                        surface = surface,
                        state = NativeThemeStyleStateVectorV1(values = emptyMap()),
                    ),
                )
            validateStatRenderPlan(resolved, surface, issues)
        }
        return if (issues.isEmpty()) result else NativeThemeStyleLinkResultV1.Rejected(issues)
    }

    fun resolveStatForEditorPreview(
        linked: NativeThemeStyleLinkResultV1.Linked,
        darkTheme: Boolean,
    ): NativeThemeStatStylePlanV1 =
        resolveStat(
            linked = linked,
            surface = NativeThemeHostSurface.EDITOR_PREVIEW,
            darkTheme = darkTheme,
        )

    fun resolveStat(
        linked: NativeThemeStyleLinkResultV1.Linked,
        surface: NativeThemeHostSurface,
        darkTheme: Boolean,
    ): NativeThemeStatStylePlanV1 {
        val resolved =
            linked.resolve(
                NativeThemeStyleResolutionRequestV1(
                    componentId = NativeThemeStatContractV1.contract.id,
                    surface = surface,
                    state = NativeThemeStyleStateVectorV1(values = emptyMap()),
                ),
            )
        return resolved.toStatStylePlan(darkTheme)
    }

    private fun statFoundation(resolvedTheme: ResolvedNativeThemeV1): NativeThemeStyleLayerV1 {
        val colors = resolvedTheme.contentColorScheme
        val typographyScale = resolvedTheme.typography.fontScale
        return NativeThemeStyleLayerV1(
            id = NativeThemeStyleLayerIdV1("operit.native_v1.stat.foundation"),
            componentStyles =
                listOf(
                    NativeThemeComponentStyleV1(
                        componentId = NativeThemeStatContractV1.contract.id,
                        scope =
                            NativeThemeStyleScopeV1(
                                common =
                                    NativeThemeStylePatchV1(
                                        parts =
                                            listOf(
                                                NativeThemeStylePartPatchV1(
                                                    part = NativeThemeComponentStylePartIdsV1.surface,
                                                    properties =
                                                        listOf(
                                                            property(
                                                                NativeThemeStylePropertyIdV1.SURFACE_COLOR,
                                                                NativeThemeStyleValueV1.Color(
                                                                    colors.surfaceVariant.copy(alpha = 0.6f)
                                                                        .toStyleColor(),
                                                                ),
                                                            ),
                                                            property(
                                                                NativeThemeStylePropertyIdV1.SHAPE,
                                                                NativeThemeStyleValueV1.Shape(
                                                                    roundedShape(20f),
                                                                ),
                                                            ),
                                                            property(
                                                                NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY,
                                                                NativeThemeStyleValueV1.Opacity(1f),
                                                            ),
                                                        ),
                                                ),
                                                NativeThemeStylePartPatchV1(
                                                    part = NativeThemeComponentStylePartIdsV1.content,
                                                    properties =
                                                        listOf(
                                                            property(
                                                                NativeThemeStylePropertyIdV1.PADDING,
                                                                NativeThemeStyleValueV1.Metric(
                                                                    NativeThemeStyleMetricSpecV1(
                                                                        value = 16f,
                                                                        unit = NativeThemeStyleMetricUnitV1.DP,
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                ),
                                                NativeThemeStylePartPatchV1(
                                                    part = NativeThemeComponentStylePartIdsV1.leading,
                                                    properties =
                                                        listOf(
                                                            property(
                                                                NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                                                                NativeThemeStyleValueV1.Color(
                                                                    colors.primary.toStyleColor(),
                                                                ),
                                                            ),
                                                        ),
                                                ),
                                                NativeThemeStylePartPatchV1(
                                                    part = NativeThemeComponentStylePartIdsV1.value,
                                                    properties =
                                                        listOf(
                                                            property(
                                                                NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                                                                NativeThemeStyleValueV1.Color(
                                                                    colors.onSurface.toStyleColor(),
                                                                ),
                                                            ),
                                                            property(
                                                                NativeThemeStylePropertyIdV1.TEXT_STYLE,
                                                                NativeThemeStyleValueV1.Text(
                                                                    textStyle(
                                                                        color = colors.onSurface,
                                                                        fontSizeSp = 16f * typographyScale,
                                                                        lineHeightSp = 24f * typographyScale,
                                                                        fontWeight = 600,
                                                                        letterSpacingEm = 0.009375f,
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                ),
                                                NativeThemeStylePartPatchV1(
                                                    part = NativeThemeComponentStylePartIdsV1.label,
                                                    properties =
                                                        listOf(
                                                            property(
                                                                NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                                                                NativeThemeStyleValueV1.Color(
                                                                    colors.onSurfaceVariant.toStyleColor(),
                                                                ),
                                                            ),
                                                            property(
                                                                NativeThemeStylePropertyIdV1.TEXT_STYLE,
                                                                NativeThemeStyleValueV1.Text(
                                                                    textStyle(
                                                                        color = colors.onSurfaceVariant,
                                                                        fontSizeSp = 12f * typographyScale,
                                                                        lineHeightSp = 16f * typographyScale,
                                                                        fontWeight = 400,
                                                                        letterSpacingEm = 0.033333335f,
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ),
                    ),
                ),
        )
    }
}

internal object NativeThemeStatStyleInstanceEditorV1 {
    fun setSurfaceColor(
        layer: NativeThemeStyleLayerV1,
        color: Color,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.surface,
            property =
                property(
                    NativeThemeStylePropertyIdV1.SURFACE_COLOR,
                    NativeThemeStyleValueV1.Color(color.toStyleColor()),
                ),
        )

    fun setValueColor(
        layer: NativeThemeStyleLayerV1,
        color: Color,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.value,
            property(
                NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                NativeThemeStyleValueV1.Color(color.toStyleColor()),
            ),
        )

    fun setLabelColor(
        layer: NativeThemeStyleLayerV1,
        color: Color,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.label,
            property(
                NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                NativeThemeStyleValueV1.Color(color.toStyleColor()),
            ),
        )

    fun setRoundedCorners(
        layer: NativeThemeStyleLayerV1,
        radiusDp: Float,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.surface,
            property(
                NativeThemeStylePropertyIdV1.SHAPE,
                NativeThemeStyleValueV1.Shape(roundedShape(radiusDp)),
            ),
        )

    fun setCapsule(layer: NativeThemeStyleLayerV1): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.surface,
            property(
                NativeThemeStylePropertyIdV1.SHAPE,
                NativeThemeStyleValueV1.Shape(NativeThemeStyleShapeSpecV1.Capsule),
            ),
        )

    fun setBorder(
        layer: NativeThemeStyleLayerV1,
        color: Color,
        widthDp: Float,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.surface,
            property(
                NativeThemeStylePropertyIdV1.BORDER_STACK,
                NativeThemeStyleValueV1.Border(
                    NativeThemeStyleBorderStackSpecV1(
                        layers =
                            listOf(
                                NativeThemeStyleBorderLayerV1(
                                    id = NativeThemeStyleRuleIdV1("stat_border"),
                                    sides = NativeThemeStyleBorderSideV1.entries.toSet(),
                                    alignment = NativeThemeStyleBorderAlignmentV1.INSIDE,
                                    widthDp = widthDp,
                                    brush = NativeThemeStyleBrushV1.Solid(color.toStyleColor()),
                                ),
                            ),
                    ),
                ),
            ),
        )

    fun clearBorder(layer: NativeThemeStyleLayerV1): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.surface,
            property(NativeThemeStylePropertyIdV1.BORDER_STACK, NativeThemeStyleValueV1.None),
        )

    fun setOpacity(
        layer: NativeThemeStyleLayerV1,
        opacity: Float,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.surface,
            property(NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY, NativeThemeStyleValueV1.Opacity(opacity)),
        )

    fun setContentPadding(
        layer: NativeThemeStyleLayerV1,
        paddingDp: Float,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.content,
            property(
                NativeThemeStylePropertyIdV1.PADDING,
                NativeThemeStyleValueV1.Metric(
                    NativeThemeStyleMetricSpecV1(paddingDp, NativeThemeStyleMetricUnitV1.DP),
                ),
            ),
        )

    fun setIconContainer(
        layer: NativeThemeStyleLayerV1,
        containerColor: Color,
        iconColor: Color,
    ): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.leading,
            property(
                NativeThemeStylePropertyIdV1.ICON_CONTAINER,
                NativeThemeStyleValueV1.IconContainer(
                    NativeThemeStyleIconContainerSpecV1.Container(
                        containerSizeDp = 40f,
                        iconSizeDp = 20f,
                        shape = NativeThemeStyleShapeSpecV1.Capsule,
                        material =
                            NativeThemeStyleMaterialSpecV1.Solid(
                                containerColor.copy(alpha = 1f).toStyleColor(),
                            ),
                        contentColor = iconColor.toStyleColor(),
                    ),
                ),
            ),
        )

    fun clearIconContainer(layer: NativeThemeStyleLayerV1): NativeThemeStyleLayerV1 =
        layer.withStatProperty(
            part = NativeThemeComponentStylePartIdsV1.leading,
            property(NativeThemeStylePropertyIdV1.ICON_CONTAINER, NativeThemeStyleValueV1.None),
        )
}

internal fun NativeThemeResolvedComponentStyleV1.toStatStylePlan(
    darkTheme: Boolean,
): NativeThemeStatStylePlanV1 {
    val surface = requiredColor(NativeThemeComponentStylePartIdsV1.surface, NativeThemeStylePropertyIdV1.SURFACE_COLOR, darkTheme)
    val material = optionalValue(NativeThemeComponentStylePartIdsV1.surface, NativeThemeStylePropertyIdV1.MATERIAL)
    val surfaceColor =
        when (material?.resolve(tokens)) {
            null,
            NativeThemeStyleValueV1.None -> surface

            else -> requireNotNull(material).resolveSurfaceColor(tokens, darkTheme)
        }
    val shape = requiredShape(NativeThemeComponentStylePartIdsV1.surface)
    val opacity = requiredOpacity(NativeThemeComponentStylePartIdsV1.surface)
    val border = optionalBorder(NativeThemeComponentStylePartIdsV1.surface)
    val padding = requiredMetric(NativeThemeComponentStylePartIdsV1.content, NativeThemeStylePropertyIdV1.PADDING)
    val leadingColor = requiredColor(NativeThemeComponentStylePartIdsV1.leading, NativeThemeStylePropertyIdV1.CONTENT_COLOR, darkTheme)
    val iconContainer = optionalIconContainer(NativeThemeComponentStylePartIdsV1.leading)
    val valueText = requiredText(NativeThemeComponentStylePartIdsV1.value, darkTheme)
    val labelText = requiredText(NativeThemeComponentStylePartIdsV1.label, darkTheme)

    return NativeThemeStatStylePlanV1(
        surfaceColor = surfaceColor,
        opacity = opacity,
        shape = shape,
        border = border,
        contentPaddingDp = padding,
        leadingColor = leadingColor,
        leadingIconContainer = iconContainer,
        value = valueText,
        label = labelText,
    )
}

private fun NativeThemeResolvedComponentStyleV1.requiredColor(
    part: NativeThemeStylePartIdV1,
    property: NativeThemeStylePropertyIdV1,
    darkTheme: Boolean,
): Color {
    val value = requiredValue(part, property)
    val resolved = value.resolve(tokens)
    require(resolved is NativeThemeStyleValueV1.Color) {
        "${part.value}.${property.name} must resolve to a color."
    }
    return resolved.value.resolveColor(darkTheme)
}

private fun NativeThemeResolvedComponentStyleV1.requiredShape(
    part: NativeThemeStylePartIdV1,
): NativeThemeStyleShapeSpecV1 {
    val resolved = requiredValue(part, NativeThemeStylePropertyIdV1.SHAPE).resolve(tokens)
    require(resolved is NativeThemeStyleValueV1.Shape) {
        "${part.value}.SHAPE must resolve to a shape."
    }
    return resolved.value
}

private fun NativeThemeResolvedComponentStyleV1.requiredOpacity(
    part: NativeThemeStylePartIdV1,
): Float {
    val resolved = requiredValue(part, NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY).resolve(tokens)
    require(resolved is NativeThemeStyleValueV1.Opacity) {
        "${part.value}.WHOLE_LAYER_OPACITY must resolve to opacity."
    }
    return resolved.value
}

private fun NativeThemeResolvedComponentStyleV1.requiredMetric(
    part: NativeThemeStylePartIdV1,
    property: NativeThemeStylePropertyIdV1,
): Float {
    val resolved = requiredValue(part, property).resolve(tokens)
    require(resolved is NativeThemeStyleValueV1.Metric && resolved.value.unit == NativeThemeStyleMetricUnitV1.DP) {
        "${part.value}.${property.name} must resolve to a dp metric."
    }
    return resolved.value.value
}

private fun NativeThemeResolvedComponentStyleV1.requiredText(
    part: NativeThemeStylePartIdV1,
    darkTheme: Boolean,
): NativeThemeStatTextStylePlanV1 {
    val text = requiredValue(part, NativeThemeStylePropertyIdV1.TEXT_STYLE).resolve(tokens)
    require(text is NativeThemeStyleValueV1.Text) {
        "${part.value}.TEXT_STYLE must resolve to text."
    }
    // CONTENT_COLOR is the component foreground; TextStyle retains typography metrics.
    return NativeThemeStatTextStylePlanV1(
        spec = text.value,
        color = requiredColor(part, NativeThemeStylePropertyIdV1.CONTENT_COLOR, darkTheme),
    )
}

private fun NativeThemeResolvedComponentStyleV1.optionalBorder(
    part: NativeThemeStylePartIdV1,
): NativeThemeStyleBorderStackSpecV1? {
    val raw = optionalValue(part, NativeThemeStylePropertyIdV1.BORDER_STACK) ?: return null
    return when (val resolved = raw.resolve(tokens)) {
        NativeThemeStyleValueV1.None -> null
        is NativeThemeStyleValueV1.Border -> resolved.value
        else -> error("${part.value}.BORDER_STACK must resolve to a border stack.")
    }
}

private fun NativeThemeResolvedComponentStyleV1.optionalIconContainer(
    part: NativeThemeStylePartIdV1,
): NativeThemeStyleIconContainerSpecV1.Container? {
    val raw = optionalValue(part, NativeThemeStylePropertyIdV1.ICON_CONTAINER) ?: return null
    return when (val resolved = raw.resolve(tokens)) {
        NativeThemeStyleValueV1.None -> null
        is NativeThemeStyleValueV1.IconContainer ->
            when (val container = resolved.value) {
                NativeThemeStyleIconContainerSpecV1.None -> null
                is NativeThemeStyleIconContainerSpecV1.Container -> container
            }

        else -> error("${part.value}.ICON_CONTAINER must resolve to an icon container.")
    }
}

private fun NativeThemeResolvedComponentStyleV1.requiredValue(
    part: NativeThemeStylePartIdV1,
    property: NativeThemeStylePropertyIdV1,
): NativeThemeStyleValueV1 =
    requireNotNull(parts[part]?.get(property)) {
        "Missing required style property ${part.value}.${property.name}."
    }

private fun NativeThemeResolvedComponentStyleV1.optionalValue(
    part: NativeThemeStylePartIdV1,
    property: NativeThemeStylePropertyIdV1,
): NativeThemeStyleValueV1? = parts[part]?.get(property)

private fun NativeThemeStyleValueV1.resolve(
    tokens: Map<NativeThemeStyleTokenIdV1, NativeThemeStyleValueV1>,
    resolving: Set<NativeThemeStyleTokenIdV1> = emptySet(),
): NativeThemeStyleValueV1 =
    when (this) {
        is NativeThemeStyleValueV1.TokenReference -> {
            require(tokenId !in resolving) { "Style token references must be acyclic." }
            tokens.getValue(tokenId).resolve(tokens, resolving + tokenId)
        }

        else -> this
    }

private fun NativeThemeStyleValueV1.resolveSurfaceColor(
    tokens: Map<NativeThemeStyleTokenIdV1, NativeThemeStyleValueV1>,
    darkTheme: Boolean,
): Color =
    when (val resolved = resolve(tokens)) {
        is NativeThemeStyleValueV1.Material ->
            when (val material = resolved.value) {
                is NativeThemeStyleMaterialSpecV1.Solid -> material.color.resolveColor(darkTheme)
                is NativeThemeStyleMaterialSpecV1.Translucent ->
                    material.tint.resolveColor(darkTheme).copy(alpha = material.opacity)

                else -> error("The Stat preview does not support ${material::class.simpleName} material.")
            }

        NativeThemeStyleValueV1.None -> error("A Stat material property cannot resolve to none here.")
        else -> error("The Stat material property must resolve to material.")
    }

internal fun NativeThemeStyleColorSpecV1.resolveColor(darkTheme: Boolean): Color {
    val source = if (darkTheme) dark else light
    return when (source) {
        is NativeThemeStyleColorSourceV1.Literal -> source.rgba.toComposeColor()
        is NativeThemeStyleColorSourceV1.HostRole ->
            error("The Stat preview requires literal style colors, not host color roles.")
    }
}

private fun String.toComposeColor(): Color {
    require(length == 9 && startsWith("#")) { "Colors must use #RRGGBBAA notation." }
    val red = substring(1, 3).toInt(16) / 255f
    val green = substring(3, 5).toInt(16) / 255f
    val blue = substring(5, 7).toInt(16) / 255f
    val alpha = substring(7, 9).toInt(16) / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha)
}

private fun Color.toStyleColor(): NativeThemeStyleColorSpecV1 {
    val hex =
        String.format(
            Locale.US,
            "#%02x%02x%02x%02x",
            (red * 255f).roundToInt(),
            (green * 255f).roundToInt(),
            (blue * 255f).roundToInt(),
            (alpha * 255f).roundToInt(),
        )
    val source = NativeThemeStyleColorSourceV1.Literal(hex)
    return NativeThemeStyleColorSpecV1(light = source, dark = source)
}

private fun roundedShape(radiusDp: Float): NativeThemeStyleShapeSpecV1 {
    val radius = NativeThemeStyleCornerRadiusV1(radiusDp, NativeThemeStyleCornerRadiusUnitV1.DP)
    return NativeThemeStyleShapeSpecV1.RoundedCorners(
        topStart = radius,
        topEnd = radius,
        bottomEnd = radius,
        bottomStart = radius,
    )
}

private fun textStyle(
    color: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    fontWeight: Int,
    letterSpacingEm: Float,
): NativeThemeTextStyleSpecV1 =
    NativeThemeTextStyleSpecV1(
        family = NativeThemeStyleFontFamilyV1.System(NativeThemeSystemFontFamilyV1.SYSTEM),
        fontSizeSp = fontSizeSp,
        lineHeightSp = lineHeightSp,
        fontWeight = fontWeight,
        letterSpacingEm = letterSpacingEm,
        color = color.toStyleColor(),
    )

private fun property(
    id: NativeThemeStylePropertyIdV1,
    value: NativeThemeStyleValueV1,
): NativeThemeStylePropertyV1 = NativeThemeStylePropertyV1(id = id, value = value)

private fun NativeThemeStyleLayerV1.withStatProperty(
    part: NativeThemeStylePartIdV1,
    property: NativeThemeStylePropertyV1,
): NativeThemeStyleLayerV1 = withStatPartProperties(part, listOf(property))

private fun NativeThemeStyleLayerV1.withStatPartProperties(
    part: NativeThemeStylePartIdV1,
    properties: List<NativeThemeStylePropertyV1>,
): NativeThemeStyleLayerV1 {
    val existingComponent = componentStyles.firstOrNull { style -> style.componentId == NativeThemeStatContractV1.contract.id }
    val existingScope = existingComponent?.scope ?: NativeThemeStyleScopeV1()
    val existingParts = existingScope.common.parts
    val existingPart = existingParts.firstOrNull { patch -> patch.part == part }
    val updatedProperties =
        (existingPart?.properties.orEmpty().filterNot { current ->
            properties.any { replacement -> replacement.id == current.id }
        } + properties)
    val updatedPart = NativeThemeStylePartPatchV1(part = part, properties = updatedProperties)
    val updatedScope =
        existingScope.copy(
            common =
                existingScope.common.copy(
                    parts = existingParts.filterNot { patch -> patch.part == part } + updatedPart,
                ),
        )
    val updatedComponent =
        NativeThemeComponentStyleV1(
            componentId = NativeThemeStatContractV1.contract.id,
            scope = updatedScope,
        )
    return copy(
        componentStyles =
            componentStyles.filterNot { style -> style.componentId == NativeThemeStatContractV1.contract.id } +
                updatedComponent,
    )
}

private fun validateStatRenderPlan(
    style: NativeThemeResolvedComponentStyleV1,
    surface: NativeThemeHostSurface,
    issues: MutableList<NativeThemeStyleIssueV1>,
) {
    fun issue(
        part: NativeThemeStylePartIdV1,
        property: NativeThemeStylePropertyIdV1,
        detail: String,
    ) {
        issues +=
            NativeThemeStyleIssueV1(
                code = NativeThemeStyleIssueCodeV1.UNSUPPORTED_COMPOSE_RENDER_PLAN,
                path = "stat.${surface.name}.${part.value}.${property.name}",
                detail = detail,
            )
    }

    fun properties(part: NativeThemeStylePartIdV1): Map<NativeThemeStylePropertyIdV1, NativeThemeStyleValueV1> =
        style.parts[part].orEmpty()

    fun rejectUnexpected(
        part: NativeThemeStylePartIdV1,
        supported: Set<NativeThemeStylePropertyIdV1>,
    ) {
        properties(part).keys.filterNot { property -> property in supported }.forEach { property ->
            issue(part, property, "The Stat renderer does not implement this property.")
        }
    }

    fun resolve(
        part: NativeThemeStylePartIdV1,
        property: NativeThemeStylePropertyIdV1,
    ): NativeThemeStyleValueV1? = properties(part)[property]?.resolve(style.tokens)

    fun validateLiteralColor(
        part: NativeThemeStylePartIdV1,
        property: NativeThemeStylePropertyIdV1,
        value: NativeThemeStyleValueV1?,
    ) {
        if (value !is NativeThemeStyleValueV1.Color || !value.value.isLiteral()) {
            issue(part, property, "The Stat renderer requires a literal light/dark color pair.")
        }
    }

    val surfacePart = NativeThemeComponentStylePartIdsV1.surface
    val contentPart = NativeThemeComponentStylePartIdsV1.content
    val leadingPart = NativeThemeComponentStylePartIdsV1.leading
    val valuePart = NativeThemeComponentStylePartIdsV1.value
    val labelPart = NativeThemeComponentStylePartIdsV1.label
    rejectUnexpected(
        surfacePart,
        setOf(
            NativeThemeStylePropertyIdV1.SURFACE_COLOR,
            NativeThemeStylePropertyIdV1.SHAPE,
            NativeThemeStylePropertyIdV1.BORDER_STACK,
            NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY,
            NativeThemeStylePropertyIdV1.MATERIAL,
        ),
    )
    rejectUnexpected(contentPart, setOf(NativeThemeStylePropertyIdV1.PADDING))
    rejectUnexpected(
        leadingPart,
        setOf(
            NativeThemeStylePropertyIdV1.CONTENT_COLOR,
            NativeThemeStylePropertyIdV1.ICON_CONTAINER,
        ),
    )
    rejectUnexpected(
        valuePart,
        setOf(NativeThemeStylePropertyIdV1.CONTENT_COLOR, NativeThemeStylePropertyIdV1.TEXT_STYLE),
    )
    rejectUnexpected(
        labelPart,
        setOf(NativeThemeStylePropertyIdV1.CONTENT_COLOR, NativeThemeStylePropertyIdV1.TEXT_STYLE),
    )

    validateLiteralColor(
        surfacePart,
        NativeThemeStylePropertyIdV1.SURFACE_COLOR,
        resolve(surfacePart, NativeThemeStylePropertyIdV1.SURFACE_COLOR),
    )
    validateLiteralColor(
        leadingPart,
        NativeThemeStylePropertyIdV1.CONTENT_COLOR,
        resolve(leadingPart, NativeThemeStylePropertyIdV1.CONTENT_COLOR),
    )
    validateStatTextPart(style, valuePart, 600, 0.009375f, issues, surface)
    validateStatTextPart(style, labelPart, 400, 0.033333335f, issues, surface)

    when (val padding = resolve(contentPart, NativeThemeStylePropertyIdV1.PADDING)) {
        is NativeThemeStyleValueV1.Metric -> {
            if (padding.value.unit != NativeThemeStyleMetricUnitV1.DP) {
                issue(contentPart, NativeThemeStylePropertyIdV1.PADDING, "The Stat renderer requires dp padding.")
            }
        }

        else -> issue(contentPart, NativeThemeStylePropertyIdV1.PADDING, "The Stat renderer requires a padding metric.")
    }

    when (val border = resolve(surfacePart, NativeThemeStylePropertyIdV1.BORDER_STACK)) {
        null,
        NativeThemeStyleValueV1.None -> Unit

        is NativeThemeStyleValueV1.Border -> {
            if (!border.value.isStatCompatible()) {
                issue(surfacePart, NativeThemeStylePropertyIdV1.BORDER_STACK, "The Stat renderer supports one inside solid border on all sides.")
            }
        }

        else -> issue(surfacePart, NativeThemeStylePropertyIdV1.BORDER_STACK, "The Stat renderer requires a border stack.")
    }

    when (val material = resolve(surfacePart, NativeThemeStylePropertyIdV1.MATERIAL)) {
        null,
        NativeThemeStyleValueV1.None -> Unit

        is NativeThemeStyleValueV1.Material -> {
            val materialValue = material.value
            val supported =
                when (materialValue) {
                    is NativeThemeStyleMaterialSpecV1.Solid -> materialValue.color.isLiteral()
                    is NativeThemeStyleMaterialSpecV1.Translucent -> materialValue.tint.isLiteral()
                    else -> false
                }
            if (!supported) {
                issue(surfacePart, NativeThemeStylePropertyIdV1.MATERIAL, "The Stat renderer supports solid or translucent literal materials.")
            }
        }

        else -> issue(surfacePart, NativeThemeStylePropertyIdV1.MATERIAL, "The Stat renderer requires a material value.")
    }

    when (val container = resolve(leadingPart, NativeThemeStylePropertyIdV1.ICON_CONTAINER)) {
        null,
        NativeThemeStyleValueV1.None -> Unit

        is NativeThemeStyleValueV1.IconContainer -> {
            val value = container.value
            if (value is NativeThemeStyleIconContainerSpecV1.Container && !value.isStatCompatible()) {
                issue(leadingPart, NativeThemeStylePropertyIdV1.ICON_CONTAINER, "The Stat renderer supports solid or translucent icon containers without shadows.")
            }
        }

        else -> issue(leadingPart, NativeThemeStylePropertyIdV1.ICON_CONTAINER, "The Stat renderer requires an icon container.")
    }
}

private fun validateStatTextPart(
    style: NativeThemeResolvedComponentStyleV1,
    part: NativeThemeStylePartIdV1,
    fontWeight: Int,
    letterSpacingEm: Float,
    issues: MutableList<NativeThemeStyleIssueV1>,
    surface: NativeThemeHostSurface,
) {
    val contentColor = style.parts[part]?.get(NativeThemeStylePropertyIdV1.CONTENT_COLOR)?.resolve(style.tokens)
    val text = style.parts[part]?.get(NativeThemeStylePropertyIdV1.TEXT_STYLE)?.resolve(style.tokens)
    val contentColorSpec =
        when (contentColor) {
            is NativeThemeStyleValueV1.Color -> contentColor.value
            else -> null
        }
    val textSpec =
        when (text) {
            is NativeThemeStyleValueV1.Text -> text.value
            else -> null
        }
    val isCompatible =
        contentColorSpec != null &&
            contentColorSpec.isLiteral() &&
            textSpec != null &&
            textSpec.family == NativeThemeStyleFontFamilyV1.System(NativeThemeSystemFontFamilyV1.SYSTEM) &&
            textSpec.fontWeight == fontWeight &&
            textSpec.letterSpacingEm == letterSpacingEm &&
            textSpec.color.isLiteral()
    if (!isCompatible) {
        issues +=
            NativeThemeStyleIssueV1(
                code = NativeThemeStyleIssueCodeV1.UNSUPPORTED_COMPOSE_RENDER_PLAN,
                path = "stat.${surface.name}.${part.value}.TEXT_STYLE",
                detail = "The Stat renderer supports native_v1 typography metrics and literal colors.",
            )
    }
}

private fun NativeThemeStyleColorSpecV1.isLiteral(): Boolean =
    light is NativeThemeStyleColorSourceV1.Literal && dark is NativeThemeStyleColorSourceV1.Literal

private fun NativeThemeStyleBorderStackSpecV1.isStatCompatible(): Boolean {
    val layer = layers.singleOrNull() ?: return false
    if (layer.alignment != NativeThemeStyleBorderAlignmentV1.INSIDE ||
        layer.offsetDp != 0f ||
        layer.dash != null ||
        layer.sides != NativeThemeStyleBorderSideV1.entries.toSet()
    ) {
        return false
    }
    val brush = layer.brush
    return brush is NativeThemeStyleBrushV1.Solid && brush.color.isLiteral()
}

private fun NativeThemeStyleIconContainerSpecV1.Container.isStatCompatible(): Boolean {
    val materialCompatible =
        when (material) {
            is NativeThemeStyleMaterialSpecV1.Solid -> material.color.isLiteral()
            is NativeThemeStyleMaterialSpecV1.Translucent -> material.tint.isLiteral()
            else -> false
        }
    return materialCompatible &&
        contentColor.isLiteral() &&
        shadows == null &&
        (border == null || border.isStatCompatible())
}

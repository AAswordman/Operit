package com.ai.assistance.operit.ui.theme.style

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentContractV1

internal data class NativeThemeStyleLinkRequestV1(
    val cascade: NativeThemeStyleCascadeV1,
    val componentContracts: List<NativeThemeComponentContractV1>,
    val declaredCapabilities: List<NativeThemeComposeCapabilityRequirementV1>,
    val hostCapabilityProfile: NativeThemeComposeHostCapabilityProfileV1,
)

internal sealed interface NativeThemeStyleLinkResultV1 {
    data class Linked(
        val cascade: NativeThemeStyleCascadeV1,
        val componentContracts: List<NativeThemeComponentContractV1>,
        val capabilities: List<NativeThemeComposeCapabilityRequirementV1>,
    ) : NativeThemeStyleLinkResultV1 {
        fun resolve(
            request: NativeThemeStyleResolutionRequestV1,
        ): NativeThemeResolvedComponentStyleV1 =
            resolveLinkedNativeThemeStyleV1(
                cascade = cascade,
                componentContracts = componentContracts,
                request = request,
            )
    }

    data class Rejected(
        val issues: List<NativeThemeStyleIssueV1>,
    ) : NativeThemeStyleLinkResultV1
}

internal fun linkNativeThemeStyleV1(
    request: NativeThemeStyleLinkRequestV1,
): NativeThemeStyleLinkResultV1 {
    val catalogResult =
        validateNativeThemeStyleCatalogV1(
            cascade = request.cascade,
            componentContracts = request.componentContracts,
        )
    val capabilityResult =
        validateNativeThemeComposeCapabilityRequirementsV1(
            requirements = request.declaredCapabilities,
            hostProfile = request.hostCapabilityProfile,
        )
    val usageIssues =
        if (catalogResult.isValid) {
            validateDeclaredStyleCapabilities(
                usages = collectStyleCapabilityUsages(request.cascade, request.componentContracts),
                declarations = request.declaredCapabilities,
            )
        } else {
            emptyList()
        }
    val issues = catalogResult.issues + capabilityResult.issues + usageIssues
    return if (issues.isEmpty()) {
        NativeThemeStyleLinkResultV1.Linked(
            cascade = request.cascade,
            componentContracts = request.componentContracts,
            capabilities = request.declaredCapabilities,
        )
    } else {
        NativeThemeStyleLinkResultV1.Rejected(issues)
    }
}

private data class NativeThemeComposeCapabilityUsageV1(
    val id: NativeThemeComposeCapabilityIdV1,
    val surfaces: Set<NativeThemeHostSurface>,
)

private fun validateDeclaredStyleCapabilities(
    usages: List<NativeThemeComposeCapabilityUsageV1>,
    declarations: List<NativeThemeComposeCapabilityRequirementV1>,
): List<NativeThemeStyleIssueV1> {
    val issues = mutableListOf<NativeThemeStyleIssueV1>()
    val declarationsById = declarations.associateBy { declaration -> declaration.id }
    val usageIds = usages.map { usage -> usage.id }.toSet()

    usages.forEach { usage ->
        val declaration = declarationsById[usage.id]
        if (declaration == null) {
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.UNDECLARED_STYLE_CAPABILITY,
                    path = "capabilities",
                    detail = "Styles use ${usage.id.name} without declaring it.",
                )
            return@forEach
        }
        val declaredSurfaces = declaration.surfaces.map { requirement -> requirement.surface }.toSet()
        val missingSurfaces = usage.surfaces - declaredSurfaces
        if (missingSurfaces.isNotEmpty()) {
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.DECLARED_CAPABILITY_SURFACE_MISMATCH,
                    path = "capabilities.${usage.id.name}",
                    detail = "Styles use ${usage.id.name} on ${missingSurfaces.joinToString { surface -> surface.name }} without declaring those surfaces.",
                )
        }
        val unusedSurfaces = declaredSurfaces - usage.surfaces
        if (unusedSurfaces.isNotEmpty()) {
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.UNUSED_DECLARED_CAPABILITY_SURFACE,
                    path = "capabilities.${usage.id.name}",
                    detail = "Declared ${usage.id.name} surfaces ${unusedSurfaces.joinToString { surface -> surface.name }} are not used by the linked styles.",
                )
        }
    }
    declarations.filter { declaration -> declaration.id !in usageIds }.forEach { declaration ->
        issues +=
            NativeThemeStyleIssueV1(
                code = NativeThemeStyleIssueCodeV1.UNUSED_DECLARED_CAPABILITY,
                path = "capabilities.${declaration.id.name}",
                detail = "Declared capability is not used by the linked styles.",
            )
    }
    return issues
}

private fun collectStyleCapabilityUsages(
    cascade: NativeThemeStyleCascadeV1,
    componentContracts: List<NativeThemeComponentContractV1>,
): List<NativeThemeComposeCapabilityUsageV1> {
    val tokenValues =
        linkedMapOf<NativeThemeStyleTokenIdV1, NativeThemeStyleValueV1>().apply {
            cascade.orderedLayers().forEach { layer ->
                layer.tokens.forEach { token -> put(token.id, token.value) }
            }
        }
    val usages = mutableMapOf<NativeThemeComposeCapabilityIdV1, MutableSet<NativeThemeHostSurface>>()

    fun record(
        capability: NativeThemeComposeCapabilityIdV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        if (surfaces.isEmpty()) return
        usages.getOrPut(capability) { linkedSetOf() } += surfaces
    }

    fun inspectColor(
        color: NativeThemeStyleColorSpecV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        if (color.light is NativeThemeStyleColorSourceV1.HostRole ||
            color.dark is NativeThemeStyleColorSourceV1.HostRole
        ) {
            record(NativeThemeComposeCapabilityIdV1.DYNAMIC_COLOR, surfaces)
        }
    }

    fun inspectBorder(
        border: NativeThemeStyleBorderStackSpecV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        border.layers.forEach { layer ->
            if (layer.alignment == NativeThemeStyleBorderAlignmentV1.OUTSIDE) {
                record(NativeThemeComposeCapabilityIdV1.OUTSIDE_BORDER, surfaces)
            }
            when (val brush = layer.brush) {
                is NativeThemeStyleBrushV1.Solid -> inspectColor(brush.color, surfaces)
                is NativeThemeStyleBrushV1.LinearGradient ->
                    brush.stops.forEach { stop -> inspectColor(stop.color, surfaces) }

                is NativeThemeStyleBrushV1.RadialGradient ->
                    brush.stops.forEach { stop -> inspectColor(stop.color, surfaces) }
            }
        }
    }

    fun inspectShadows(
        shadows: NativeThemeStyleShadowStackSpecV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        shadows.layers.forEach { shadow ->
            inspectColor(shadow.color, surfaces)
            if (shadow.kind == NativeThemeStyleShadowKindV1.INNER) {
                record(NativeThemeComposeCapabilityIdV1.INNER_SHADOW, surfaces)
            }
        }
    }

    fun inspectMaterial(
        material: NativeThemeStyleMaterialSpecV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        when (material) {
            is NativeThemeStyleMaterialSpecV1.Solid -> inspectColor(material.color, surfaces)
            is NativeThemeStyleMaterialSpecV1.Translucent -> inspectColor(material.tint, surfaces)
            is NativeThemeStyleMaterialSpecV1.Frosted -> {
                inspectColor(material.tint, surfaces)
                record(NativeThemeComposeCapabilityIdV1.BACKDROP_BLUR, surfaces)
            }

            is NativeThemeStyleMaterialSpecV1.Liquid -> {
                inspectColor(material.tint, surfaces)
                record(NativeThemeComposeCapabilityIdV1.BACKDROP_BLUR, surfaces)
                record(NativeThemeComposeCapabilityIdV1.LIQUID_MATERIAL, surfaces)
            }

            is NativeThemeStyleMaterialSpecV1.Water -> {
                inspectColor(material.tint, surfaces)
                record(NativeThemeComposeCapabilityIdV1.WATER_MATERIAL, surfaces)
            }
        }
    }

    fun inspectText(
        text: NativeThemeTextStyleSpecV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        inspectColor(text.color, surfaces)
        if (text.family is NativeThemeStyleFontFamilyV1.Asset) {
            record(NativeThemeComposeCapabilityIdV1.FONT_ASSET, surfaces)
        }
    }

    fun inspectIconContainer(
        container: NativeThemeStyleIconContainerSpecV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        if (container !is NativeThemeStyleIconContainerSpecV1.Container) return
        inspectMaterial(container.material, surfaces)
        inspectColor(container.contentColor, surfaces)
        container.border?.let { border -> inspectBorder(border, surfaces) }
        container.shadows?.let { shadows -> inspectShadows(shadows, surfaces) }
    }

    fun inspectMenu(
        menu: NativeThemeStyleMenuSpecV1,
        surfaces: Set<NativeThemeHostSurface>,
    ) {
        inspectMaterial(menu.material, surfaces)
        inspectText(menu.label, surfaces)
        inspectColor(menu.iconColor, surfaces)
        inspectColor(menu.dividerColor, surfaces)
        listOf(menu.selectedItem, menu.disabledItem, menu.destructiveItem).forEach { item ->
            inspectColor(item.containerColor, surfaces)
            inspectColor(item.contentColor, surfaces)
            inspectColor(item.iconColor, surfaces)
        }
        menu.border?.let { border -> inspectBorder(border, surfaces) }
        menu.shadows?.let { shadows -> inspectShadows(shadows, surfaces) }
    }

    fun inspectValue(
        value: NativeThemeStyleValueV1,
        property: NativeThemeStylePropertyIdV1,
        surfaces: Set<NativeThemeHostSurface>,
        resolvingTokens: Set<NativeThemeStyleTokenIdV1> = emptySet(),
    ) {
        when (value) {
            NativeThemeStyleValueV1.None -> Unit
            is NativeThemeStyleValueV1.TokenReference -> {
                if (value.tokenId !in resolvingTokens) {
                    val target = tokenValues.getValue(value.tokenId)
                    inspectValue(target, property, surfaces, resolvingTokens + value.tokenId)
                }
            }

            is NativeThemeStyleValueV1.Color -> inspectColor(value.value, surfaces)
            is NativeThemeStyleValueV1.Opacity -> Unit
            is NativeThemeStyleValueV1.Text -> inspectText(value.value, surfaces)
            is NativeThemeStyleValueV1.Shape -> Unit
            is NativeThemeStyleValueV1.Border -> inspectBorder(value.value, surfaces)
            is NativeThemeStyleValueV1.Shadow -> inspectShadows(value.value, surfaces)
            is NativeThemeStyleValueV1.Material -> inspectMaterial(value.value, surfaces)
            is NativeThemeStyleValueV1.Blur ->
                when (property) {
                    NativeThemeStylePropertyIdV1.CONTENT_BLUR ->
                        record(NativeThemeComposeCapabilityIdV1.CONTENT_BLUR, surfaces)

                    NativeThemeStylePropertyIdV1.BACKDROP_BLUR ->
                        record(NativeThemeComposeCapabilityIdV1.BACKDROP_BLUR, surfaces)

                    else -> Unit
                }

            is NativeThemeStyleValueV1.IconContainer -> inspectIconContainer(value.value, surfaces)
            is NativeThemeStyleValueV1.Menu -> inspectMenu(value.value, surfaces)
            is NativeThemeStyleValueV1.Metric,
            is NativeThemeStyleValueV1.Motion -> Unit
        }
    }

    componentContracts.forEach { component ->
        val surfaces = component.supportedHostSurfaces.intersect(NativeThemeComposeStyleSurfacesV1.supported)
        val stateVectors = componentStyleStateVectors(component)
        surfaces.forEach { surface ->
            stateVectors.forEach { state ->
                val resolved =
                    resolveNativeThemeStyleCascadeUncheckedV1(
                        cascade = cascade,
                        component = component,
                        request =
                            NativeThemeStyleResolutionRequestV1(
                                componentId = component.id,
                                surface = surface,
                                state = state,
                            ),
                    )
                resolved.parts.values.forEach { properties ->
                    properties.forEach { (property, value) ->
                        inspectValue(value, property, setOf(surface))
                    }
                }
            }
        }
    }

    return usages.map { (id, surfaces) ->
        NativeThemeComposeCapabilityUsageV1(id = id, surfaces = surfaces)
    }
}

private fun componentStyleStateVectors(
    component: NativeThemeComponentContractV1,
): List<NativeThemeStyleStateVectorV1> {
    val stateMaps =
        component.styleStateAxes.fold(
            listOf(emptyMap<NativeThemeStyleStateAxisV1, NativeThemeStyleStateValueV1>()),
        ) { combinations, axisContract ->
            combinations.flatMap { values ->
                listOf(values) +
                    axisContract.values.map { value -> values + (axisContract.axis to value) }
            }
        }
    return stateMaps.map { values -> NativeThemeStyleStateVectorV1(values = values) }
}

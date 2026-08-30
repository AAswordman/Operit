package com.ai.assistance.operit.ui.theme.style

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.validateNativeThemeComponentContractsV1

private val RGBA_PATTERN = Regex("^#[0-9a-fA-F]{8}$")

internal enum class NativeThemeStyleIssueCodeV1 {
    DUPLICATE_LAYER,
    TOO_MANY_THEME_LAYERS,
    DUPLICATE_TOKEN,
    TOKEN_REFERENCE_MISSING,
    TOKEN_REFERENCE_CYCLE,
    TOKEN_KIND_MISMATCH,
    DUPLICATE_FAMILY_STYLE,
    DUPLICATE_COMPONENT_STYLE,
    DUPLICATE_SURFACE_OVERRIDE,
    DUPLICATE_STATE_RULE,
    DUPLICATE_STYLE_PART,
    DUPLICATE_STYLE_PROPERTY,
    DUPLICATE_BORDER_LAYER,
    DUPLICATE_SHADOW_LAYER,
    DUPLICATE_CAPABILITY_REQUIREMENT,
    DUPLICATE_CAPABILITY_SUPPORT,
    DUPLICATE_CAPABILITY_SURFACE,
    UNKNOWN_COMPONENT_STYLE,
    UNKNOWN_FAMILY_STYLE,
    UNKNOWN_STYLE_PART,
    STYLE_PROPERTY_NOT_ALLOWED,
    STYLE_SURFACE_NOT_SUPPORTED,
    STYLE_STATE_AXIS_NOT_SUPPORTED,
    STYLE_STATE_VALUE_NOT_SUPPORTED,
    INCOMPLETE_COMPONENT_STYLE,
    COMPONENT_OUTSIDE_STYLE_API,
    EMPTY_PART_PATCH,
    INVALID_COLOR,
    VALUE_OUT_OF_RANGE,
    INVALID_STATE_CONDITION,
    PROPERTY_KIND_MISMATCH,
    NONE_NOT_ALLOWED,
    SELECTOR_CONFLICT,
    INVALID_CAPABILITY_VERSION,
    EMPTY_CAPABILITY_SURFACE,
    UNSUPPORTED_STYLE_SURFACE,
    MISSING_HOST_CAPABILITY,
    CAPABILITY_VERSION_MISMATCH,
    CAPABILITY_SURFACE_MISMATCH,
    UNDECLARED_STYLE_CAPABILITY,
    DECLARED_CAPABILITY_SURFACE_MISMATCH,
    UNUSED_DECLARED_CAPABILITY,
    UNUSED_DECLARED_CAPABILITY_SURFACE,
    UNSUPPORTED_COMPOSE_RENDER_PLAN,
}

internal data class NativeThemeStyleIssueV1(
    val code: NativeThemeStyleIssueCodeV1,
    val path: String,
    val detail: String,
)

internal data class NativeThemeStyleValidationResultV1(
    val issues: List<NativeThemeStyleIssueV1>,
) {
    val isValid: Boolean
        get() = issues.isEmpty()

    fun requireValid() {
        require(isValid) {
            issues.joinToString(separator = "\n") { issue ->
                "${issue.code}: ${issue.path}: ${issue.detail}"
            }
        }
    }
}

internal fun validateNativeThemeStyleCascadeV1(
    cascade: NativeThemeStyleCascadeV1,
): NativeThemeStyleValidationResultV1 =
    NativeThemeStyleValidatorV1().validate(cascade)

internal fun validateNativeThemeStyleCatalogV1(
    cascade: NativeThemeStyleCascadeV1,
    componentContracts: List<NativeThemeComponentContractV1>,
): NativeThemeStyleValidationResultV1 {
    validateNativeThemeComponentContractsV1(componentContracts)
    val issues = validateNativeThemeStyleCascadeV1(cascade).issues.toMutableList()
    componentContracts.forEach { contract ->
        if (contract.supportedHostSurfaces.intersect(NativeThemeComposeStyleSurfacesV1.supported).isEmpty()) {
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.COMPONENT_OUTSIDE_STYLE_API,
                    path = "componentContracts.${contract.id.value}",
                    detail = "Component does not support a Style API v1 Compose surface.",
                )
        }
    }
    val contractsById = componentContracts.associateBy { contract -> contract.id }
    val contractsByFamily = componentContracts.groupBy { contract -> contract.styleFamily }

    cascade.orderedLayers().forEachIndexed { layerIndex, layer ->
        layer.componentStyles.forEachIndexed { styleIndex, style ->
            val path = "layers[$layerIndex].componentStyles[$styleIndex]"
            val contract = contractsById[style.componentId]
            if (contract == null) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.UNKNOWN_COMPONENT_STYLE,
                        path = path,
                        detail = "Component ${style.componentId.value} is not registered in the style catalog.",
                    )
            } else {
                validateScopeAgainstContracts(
                    scope = style.scope,
                    path = "$path.scope",
                    contracts = listOf(contract),
                    issues = issues,
                )
            }
        }
        layer.familyStyles.forEachIndexed { styleIndex, style ->
            val path = "layers[$layerIndex].familyStyles[$styleIndex]"
            val contracts = contractsByFamily[style.familyId]
            if (contracts.isNullOrEmpty()) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.UNKNOWN_FAMILY_STYLE,
                        path = path,
                        detail = "Component family ${style.familyId.value} is not registered in the style catalog.",
                    )
            } else {
                validateScopeAgainstContracts(
                    scope = style.scope,
                    path = "$path.scope",
                    contracts = contracts,
                    issues = issues,
                )
            }
        }
    }

    componentContracts.forEach { contract ->
        validateRequiredComponentStyle(
            cascade = cascade,
            contract = contract,
            issues = issues,
        )
    }

    return NativeThemeStyleValidationResultV1(issues)
}

internal fun resolveNativeThemeStyleCascadeV1(
    cascade: NativeThemeStyleCascadeV1,
    componentContracts: List<NativeThemeComponentContractV1>,
    request: NativeThemeStyleResolutionRequestV1,
): NativeThemeResolvedComponentStyleV1 {
    validateNativeThemeStyleCatalogV1(cascade, componentContracts).requireValid()
    return resolveLinkedNativeThemeStyleV1(cascade, componentContracts, request)
}

internal fun resolveLinkedNativeThemeStyleV1(
    cascade: NativeThemeStyleCascadeV1,
    componentContracts: List<NativeThemeComponentContractV1>,
    request: NativeThemeStyleResolutionRequestV1,
): NativeThemeResolvedComponentStyleV1 {
    require(request.surface in NativeThemeComposeStyleSurfacesV1.supported) {
        "Style API v1 does not expose ${request.surface.name}."
    }
    val component =
        requireNotNull(componentContracts.singleOrNull { contract -> contract.id == request.componentId }) {
            "Component ${request.componentId.value} is not registered in the style catalog."
        }
    require(request.surface in component.supportedHostSurfaces) {
        "Component ${component.id.value} does not support ${request.surface.name}."
    }

    return resolveNativeThemeStyleCascadeUncheckedV1(cascade, component, request)
}

internal fun resolveNativeThemeStyleCascadeUncheckedV1(
    cascade: NativeThemeStyleCascadeV1,
    component: NativeThemeComponentContractV1,
    request: NativeThemeStyleResolutionRequestV1,
): NativeThemeResolvedComponentStyleV1 {
    val tokens = linkedMapOf<NativeThemeStyleTokenIdV1, NativeThemeStyleValueV1>()
    val parts =
        linkedMapOf<
            NativeThemeStylePartIdV1,
            MutableMap<NativeThemeStylePropertyIdV1, NativeThemeStyleValueV1>,
        >()

    cascade.orderedLayers().forEach { layer ->
        layer.tokens.forEach { token -> tokens[token.id] = token.value }
        layer.familyStyles
            .singleOrNull { style -> style.familyId == component.styleFamily }
            ?.scope
            ?.applyTo(request, parts)
        layer.componentStyles
            .singleOrNull { style -> style.componentId == request.componentId }
            ?.scope
            ?.applyTo(request, parts)
    }

    return NativeThemeResolvedComponentStyleV1(
        tokens = tokens,
        parts = parts.mapValues { (_, properties) -> properties.toMap() },
    )
}

private fun NativeThemeStyleScopeV1.applyTo(
    request: NativeThemeStyleResolutionRequestV1,
    destination: MutableMap<NativeThemeStylePartIdV1, MutableMap<NativeThemeStylePropertyIdV1, NativeThemeStyleValueV1>>,
) {
    common.applyTo(destination)
    surfaceOverrides
        .singleOrNull { override -> override.surface == request.surface }
        ?.patch
        ?.applyTo(destination)
    stateRules
        .asSequence()
        .filter { rule -> rule.surfaces.isEmpty() || request.surface in rule.surfaces }
        .filter { rule -> rule.selector.matches(request.state) }
        .sortedBy { rule -> rule.selector.conditions.size }
        .forEach { rule -> rule.patch.applyTo(destination) }
}

private fun NativeThemeStylePatchV1.applyTo(
    destination: MutableMap<NativeThemeStylePartIdV1, MutableMap<NativeThemeStylePropertyIdV1, NativeThemeStyleValueV1>>,
) {
    parts.forEach { partPatch ->
        val properties = destination.getOrPut(partPatch.part) { linkedMapOf() }
        partPatch.properties.forEach { property -> properties[property.id] = property.value }
    }
}

private fun validateScopeAgainstContracts(
    scope: NativeThemeStyleScopeV1,
    path: String,
    contracts: List<NativeThemeComponentContractV1>,
    issues: MutableList<NativeThemeStyleIssueV1>,
) {
    val partProperties =
        contracts
            .map { contract ->
                contract.styleParts.associate { part -> part.id to part.allowedProperties }
            }
            .reduce { shared, next ->
                shared.mapNotNull { (partId, properties) ->
                    val nextProperties = next[partId] ?: return@mapNotNull null
                    partId to properties.intersect(nextProperties)
                }.toMap()
            }
    val supportedSurfaces =
        contracts
            .map { contract -> contract.supportedHostSurfaces }
            .reduce { shared, next -> shared.intersect(next) }
            .intersect(NativeThemeComposeStyleSurfacesV1.supported)
    val styleStateValues =
        contracts
            .map { contract ->
                contract.styleStateAxes.associate { axis -> axis.axis to axis.values }
            }
            .reduce { shared, next ->
                shared.mapNotNull { (axis, values) ->
                    val nextValues = next[axis] ?: return@mapNotNull null
                    axis to values.intersect(nextValues)
                }.toMap()
            }

    fun validatePatch(
        patch: NativeThemeStylePatchV1,
        patchPath: String,
    ) {
        patch.parts.forEachIndexed { partIndex, partPatch ->
            val partPath = "$patchPath.parts[$partIndex]"
            val allowedProperties = partProperties[partPatch.part]
            if (allowedProperties == null) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.UNKNOWN_STYLE_PART,
                        path = "$partPath.part",
                        detail = "Part ${partPatch.part.value} is not shared by this style scope.",
                    )
                return@forEachIndexed
            }
            partPatch.properties.forEachIndexed { propertyIndex, property ->
                if (property.id !in allowedProperties) {
                    issues +=
                        NativeThemeStyleIssueV1(
                            code = NativeThemeStyleIssueCodeV1.STYLE_PROPERTY_NOT_ALLOWED,
                            path = "$partPath.properties[$propertyIndex]",
                            detail = "Part ${partPatch.part.value} does not allow ${property.id.name}.",
                        )
                }
            }
        }
    }

    fun validateSurfaces(
        surfaces: Set<NativeThemeHostSurface>,
        surfacePath: String,
    ) {
        val unsupported = surfaces - supportedSurfaces
        if (unsupported.isNotEmpty()) {
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.STYLE_SURFACE_NOT_SUPPORTED,
                    path = surfacePath,
                    detail = "This style scope does not support ${unsupported.joinToString { surface -> surface.name }}.",
                )
        }
    }

    validatePatch(scope.common, "$path.common")
    scope.surfaceOverrides.forEachIndexed { index, override ->
        validateSurfaces(setOf(override.surface), "$path.surfaceOverrides[$index].surface")
        validatePatch(override.patch, "$path.surfaceOverrides[$index].patch")
    }
    scope.stateRules.forEachIndexed { index, rule ->
        if (rule.surfaces.isNotEmpty()) {
            validateSurfaces(rule.surfaces, "$path.stateRules[$index].surfaces")
        }
        rule.selector.conditions.forEachIndexed { conditionIndex, condition ->
            val supportedValues = styleStateValues[condition.axis]
            if (supportedValues == null) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.STYLE_STATE_AXIS_NOT_SUPPORTED,
                        path = "$path.stateRules[$index].selector.conditions[$conditionIndex]",
                        detail = "This style scope does not provide ${condition.axis.name} state.",
                    )
            } else if (condition.value !in supportedValues) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.STYLE_STATE_VALUE_NOT_SUPPORTED,
                        path = "$path.stateRules[$index].selector.conditions[$conditionIndex]",
                        detail = "This style scope does not provide ${condition.value.name} for ${condition.axis.name}.",
                    )
            }
        }
        validatePatch(rule.patch, "$path.stateRules[$index].patch")
    }
}

private fun validateRequiredComponentStyle(
    cascade: NativeThemeStyleCascadeV1,
    contract: NativeThemeComponentContractV1,
    issues: MutableList<NativeThemeStyleIssueV1>,
) {
    val styleSurfaces =
        contract.supportedHostSurfaces.intersect(NativeThemeComposeStyleSurfacesV1.supported)
    styleSurfaces.forEach { surface ->
        val resolved =
            resolveNativeThemeStyleCascadeUncheckedV1(
                cascade = cascade,
                component = contract,
                request =
                    NativeThemeStyleResolutionRequestV1(
                        componentId = contract.id,
                        surface = surface,
                        state = NativeThemeStyleStateVectorV1(values = emptyMap()),
                    ),
            )
        contract.styleParts.filter { part -> part.required }.forEach { part ->
            val properties = resolved.parts[part.id]
            if (properties == null) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.INCOMPLETE_COMPONENT_STYLE,
                        path = "components.${contract.id.value}.${surface.name}.${part.id.value}",
                        detail = "Required style part is not resolved.",
                    )
            } else {
                part.requiredProperties.forEach { property ->
                    if (property !in properties) {
                        issues +=
                            NativeThemeStyleIssueV1(
                                code = NativeThemeStyleIssueCodeV1.INCOMPLETE_COMPONENT_STYLE,
                                path = "components.${contract.id.value}.${surface.name}.${part.id.value}.${property.name}",
                                detail = "Required style property is not resolved.",
                            )
                    }
                }
            }
        }
    }
}

private fun NativeThemeStyleStateSelectorV1.matches(
    state: NativeThemeStyleStateVectorV1,
): Boolean =
    conditions.all { condition -> state.values[condition.axis] == condition.value }

private class NativeThemeStyleValidatorV1 {
    private val issues = mutableListOf<NativeThemeStyleIssueV1>()

    fun validate(cascade: NativeThemeStyleCascadeV1): NativeThemeStyleValidationResultV1 {
        val layers = cascade.orderedLayers()
        validateLayerIds(layers)
        if (cascade.themeLayers.size > 8) {
            issue(
                code = NativeThemeStyleIssueCodeV1.TOO_MANY_THEME_LAYERS,
                path = "themeLayers",
                detail = "A style cascade can contain at most eight inherited theme layers.",
            )
        }

        layers.forEachIndexed { index, layer ->
            validateLayer(layer, "layers[$index]")
        }

        val tokens = mergedTokens(layers)
        val tokenKinds = validateTokenGraph(tokens)
        layers.forEachIndexed { index, layer ->
            validateLayerReferences(layer, "layers[$index]", tokens, tokenKinds)
        }
        return NativeThemeStyleValidationResultV1(issues.toList())
    }

    private fun validateLayerIds(layers: List<NativeThemeStyleLayerV1>) {
        duplicateValues(layers.map { layer -> layer.id.value }) { id ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_LAYER,
                path = "layers",
                detail = "Style layer $id is declared more than once.",
            )
        }
    }

    private fun validateLayer(
        layer: NativeThemeStyleLayerV1,
        path: String,
    ) {
        duplicateValues(layer.tokens.map { token -> token.id.value }) { id ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_TOKEN,
                path = "$path.tokens",
                detail = "Token $id is declared more than once in the same layer.",
            )
        }
        layer.tokens.forEachIndexed { index, token ->
            val tokenPath = "$path.tokens[$index]"
            if (token.value == NativeThemeStyleValueV1.None) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.NONE_NOT_ALLOWED,
                    path = "$tokenPath.value",
                    detail = "A token must define a concrete value or a token reference.",
                )
            }
            validateStyleValue(token.value, "$tokenPath.value")
        }

        duplicateValues(layer.familyStyles.map { style -> style.familyId.value }) { id ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_FAMILY_STYLE,
                path = "$path.familyStyles",
                detail = "Component family $id is declared more than once in the same layer.",
            )
        }
        layer.familyStyles.forEachIndexed { index, style ->
            validateScope(style.scope, "$path.familyStyles[$index].scope")
        }

        duplicateValues(layer.componentStyles.map { style -> style.componentId.value }) { id ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_COMPONENT_STYLE,
                path = "$path.componentStyles",
                detail = "Component $id is declared more than once in the same layer.",
            )
        }
        layer.componentStyles.forEachIndexed { index, style ->
            validateScope(style.scope, "$path.componentStyles[$index].scope")
        }
    }

    private fun validateScope(
        scope: NativeThemeStyleScopeV1,
        path: String,
    ) {
        validatePatch(scope.common, "$path.common")
        duplicateValues(scope.surfaceOverrides.map { override -> override.surface.name }) { surface ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_SURFACE_OVERRIDE,
                path = "$path.surfaceOverrides",
                detail = "Surface $surface is declared more than once.",
            )
        }
        scope.surfaceOverrides.forEachIndexed { index, override ->
            validatePatch(override.patch, "$path.surfaceOverrides[$index].patch")
        }

        duplicateValues(scope.stateRules.map { rule -> rule.id.value }) { id ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_STATE_RULE,
                path = "$path.stateRules",
                detail = "State rule $id is declared more than once.",
            )
        }
        scope.stateRules.forEachIndexed { index, rule ->
            validateStateSelector(rule.selector, "$path.stateRules[$index].selector")
            validatePatch(rule.patch, "$path.stateRules[$index].patch")
        }
        validateStateRuleConflicts(scope.stateRules, "$path.stateRules")
    }

    private fun validatePatch(
        patch: NativeThemeStylePatchV1,
        path: String,
    ) {
        duplicateValues(patch.parts.map { part -> part.part.value }) { part ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_STYLE_PART,
                path = "$path.parts",
                detail = "Part $part is declared more than once.",
            )
        }
        patch.parts.forEachIndexed { index, part ->
            val partPath = "$path.parts[$index]"
            if (part.properties.isEmpty()) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.EMPTY_PART_PATCH,
                    path = partPath,
                    detail = "A style part patch must include at least one property.",
                )
            }
            duplicateValues(part.properties.map { property -> property.id.name }) { property ->
                issue(
                    code = NativeThemeStyleIssueCodeV1.DUPLICATE_STYLE_PROPERTY,
                    path = "$partPath.properties",
                    detail = "Property $property is declared more than once for the same part.",
                )
            }
            part.properties.forEachIndexed { propertyIndex, property ->
                validateProperty(property, "$partPath.properties[$propertyIndex]")
            }
        }
    }

    private fun validateProperty(
        property: NativeThemeStylePropertyV1,
        path: String,
    ) {
        if (property.value == NativeThemeStyleValueV1.None) {
            if (!property.id.allowsNone) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.NONE_NOT_ALLOWED,
                    path = "$path.value",
                    detail = "Property ${property.id.name} requires a concrete value.",
                )
            }
            return
        }
        val kind = requireNotNull(property.value.valueKindOrNull())
        if (kind !in property.id.acceptedValueKinds) {
            issue(
                code = NativeThemeStyleIssueCodeV1.PROPERTY_KIND_MISMATCH,
                path = "$path.value",
                detail = "Property ${property.id.name} does not accept ${kind.name} values.",
            )
        }
        validateStyleValue(property.value, "$path.value")
    }

    private fun validateStyleValue(
        value: NativeThemeStyleValueV1,
        path: String,
    ) {
        when (value) {
            NativeThemeStyleValueV1.None,
            is NativeThemeStyleValueV1.TokenReference -> Unit

            is NativeThemeStyleValueV1.Color -> validateColor(value.value, path)
            is NativeThemeStyleValueV1.Opacity -> bounded(value.value, 0f, 1f, path)
            is NativeThemeStyleValueV1.Text -> validateText(value.value, path)
            is NativeThemeStyleValueV1.Shape -> validateShape(value.value, path)
            is NativeThemeStyleValueV1.Border -> validateBorderStack(value.value, path)
            is NativeThemeStyleValueV1.Shadow -> validateShadowStack(value.value, path)
            is NativeThemeStyleValueV1.Material -> validateMaterial(value.value, path)
            is NativeThemeStyleValueV1.Blur -> validateBlur(value.value, path)
            is NativeThemeStyleValueV1.IconContainer -> validateIconContainer(value.value, path)
            is NativeThemeStyleValueV1.Menu -> validateMenu(value.value, path)
            is NativeThemeStyleValueV1.Metric -> bounded(value.value.value, 0f, 512f, "$path.value")
            is NativeThemeStyleValueV1.Motion -> {
                if (value.value.durationMillis !in 0..1000) {
                    issue(
                        code = NativeThemeStyleIssueCodeV1.VALUE_OUT_OF_RANGE,
                        path = "$path.durationMillis",
                        detail = "Motion duration must be between 0 and 1000 milliseconds.",
                    )
                }
            }
        }
    }

    private fun validateColor(
        color: NativeThemeStyleColorSpecV1,
        path: String,
    ) {
        validateColorSource(color.light, "$path.light")
        validateColorSource(color.dark, "$path.dark")
    }

    private fun validateOpaqueColor(
        color: NativeThemeStyleColorSpecV1,
        path: String,
    ) {
        validateColor(color, path)
        listOf("light" to color.light, "dark" to color.dark).forEach { (appearance, source) ->
            if (source is NativeThemeStyleColorSourceV1.Literal &&
                !source.rgba.endsWith("ff", ignoreCase = true)
            ) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.INVALID_COLOR,
                    path = "$path.$appearance.rgba",
                    detail = "Solid material colors must be fully opaque.",
                )
            }
        }
    }

    private fun validateColorSource(
        source: NativeThemeStyleColorSourceV1,
        path: String,
    ) {
        if (source is NativeThemeStyleColorSourceV1.Literal && !RGBA_PATTERN.matches(source.rgba)) {
            issue(
                code = NativeThemeStyleIssueCodeV1.INVALID_COLOR,
                path = "$path.rgba",
                detail = "Colors must use #RRGGBBAA notation.",
            )
        }
    }

    private fun validateText(
        text: NativeThemeTextStyleSpecV1,
        path: String,
    ) {
        bounded(text.fontSizeSp, 8f, 96f, "$path.fontSizeSp")
        bounded(text.lineHeightSp, text.fontSizeSp, 192f, "$path.lineHeightSp")
        if (text.fontWeight !in 100..900 || text.fontWeight % 100 != 0) {
            issue(
                code = NativeThemeStyleIssueCodeV1.VALUE_OUT_OF_RANGE,
                path = "$path.fontWeight",
                detail = "Font weight must be a 100-step value from 100 through 900.",
            )
        }
        bounded(text.letterSpacingEm, -0.05f, 0.20f, "$path.letterSpacingEm")
        validateColor(text.color, "$path.color")
    }

    private fun validateShape(
        shape: NativeThemeStyleShapeSpecV1,
        path: String,
    ) {
        if (shape !is NativeThemeStyleShapeSpecV1.RoundedCorners) return
        listOf(
            "topStart" to shape.topStart,
            "topEnd" to shape.topEnd,
            "bottomEnd" to shape.bottomEnd,
            "bottomStart" to shape.bottomStart,
        ).forEach { (name, radius) ->
            val maximum =
                when (radius.unit) {
                    NativeThemeStyleCornerRadiusUnitV1.DP -> 128f
                    NativeThemeStyleCornerRadiusUnitV1.PERCENT -> 50f
                }
            bounded(radius.value, 0f, maximum, "$path.$name.value")
        }
    }

    private fun validateBorderStack(
        stack: NativeThemeStyleBorderStackSpecV1,
        path: String,
    ) {
        count(stack.layers.size, 1, 8, "$path.layers", "A border stack")
        duplicateValues(stack.layers.map { layer -> layer.id.value }) { id ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_BORDER_LAYER,
                path = "$path.layers",
                detail = "Border layer $id is declared more than once.",
            )
        }
        stack.layers.forEachIndexed { index, layer ->
            val layerPath = "$path.layers[$index]"
            if (layer.sides.isEmpty()) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.VALUE_OUT_OF_RANGE,
                    path = "$layerPath.sides",
                    detail = "A border layer must target at least one side.",
                )
            }
            bounded(layer.widthDp, 0.25f, 16f, "$layerPath.widthDp")
            bounded(layer.offsetDp, -32f, 32f, "$layerPath.offsetDp")
            bounded(layer.opacity, 0f, 1f, "$layerPath.opacity")
            validateBrush(layer.brush, "$layerPath.brush")
            layer.dash?.let { dash ->
                bounded(dash.onLengthDp, 0.25f, 64f, "$layerPath.dash.onLengthDp")
                bounded(dash.offLengthDp, 0.25f, 64f, "$layerPath.dash.offLengthDp")
                bounded(dash.phaseDp, 0f, 128f, "$layerPath.dash.phaseDp")
            }
        }
    }

    private fun validateBrush(
        brush: NativeThemeStyleBrushV1,
        path: String,
    ) {
        when (brush) {
            is NativeThemeStyleBrushV1.Solid -> validateColor(brush.color, "$path.color")
            is NativeThemeStyleBrushV1.LinearGradient -> {
                bounded(brush.angleDegrees, -360f, 360f, "$path.angleDegrees")
                validateGradientStops(brush.stops, "$path.stops")
            }

            is NativeThemeStyleBrushV1.RadialGradient -> {
                bounded(brush.centerX, 0f, 1f, "$path.centerX")
                bounded(brush.centerY, 0f, 1f, "$path.centerY")
                bounded(brush.radiusFraction, 0.01f, 1f, "$path.radiusFraction")
                validateGradientStops(brush.stops, "$path.stops")
            }
        }
    }

    private fun validateGradientStops(
        stops: List<NativeThemeStyleGradientStopV1>,
        path: String,
    ) {
        count(stops.size, 2, 8, path, "A gradient")
        var previousOffset: Float? = null
        stops.forEachIndexed { index, stop ->
            bounded(stop.offset, 0f, 1f, "$path[$index].offset")
            if (previousOffset != null && stop.offset <= previousOffset!!) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.VALUE_OUT_OF_RANGE,
                    path = "$path[$index].offset",
                    detail = "Gradient offsets must be strictly increasing.",
                )
            }
            previousOffset = stop.offset
            validateColor(stop.color, "$path[$index].color")
        }
    }

    private fun validateShadowStack(
        stack: NativeThemeStyleShadowStackSpecV1,
        path: String,
    ) {
        count(stack.layers.size, 1, 4, "$path.layers", "A shadow stack")
        duplicateValues(stack.layers.map { layer -> layer.id.value }) { id ->
            issue(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_SHADOW_LAYER,
                path = "$path.layers",
                detail = "Shadow layer $id is declared more than once.",
            )
        }
        stack.layers.forEachIndexed { index, layer ->
            val layerPath = "$path.layers[$index]"
            validateColor(layer.color, "$layerPath.color")
            bounded(layer.offsetXDp, -64f, 64f, "$layerPath.offsetXDp")
            bounded(layer.offsetYDp, -64f, 64f, "$layerPath.offsetYDp")
            bounded(layer.blurRadiusDp, 0f, 64f, "$layerPath.blurRadiusDp")
            bounded(layer.spreadDp, -16f, 32f, "$layerPath.spreadDp")
        }
    }

    private fun validateBlur(
        blur: NativeThemeStyleBlurSpecV1,
        path: String,
    ) {
        bounded(blur.radiusDp, 0.01f, 64f, "$path.radiusDp")
    }

    private fun validateMaterial(
        material: NativeThemeStyleMaterialSpecV1,
        path: String,
    ) {
        when (material) {
            is NativeThemeStyleMaterialSpecV1.Solid -> validateOpaqueColor(material.color, "$path.color")
            is NativeThemeStyleMaterialSpecV1.Translucent -> {
                validateColor(material.tint, "$path.tint")
                bounded(material.opacity, 0.01f, 1f, "$path.opacity")
            }

            is NativeThemeStyleMaterialSpecV1.Frosted -> {
                validateColor(material.tint, "$path.tint")
                bounded(material.opacity, 0.01f, 1f, "$path.opacity")
                bounded(material.backdropBlurRadiusDp, 0.01f, 32f, "$path.backdropBlurRadiusDp")
                bounded(material.saturation, 0f, 2f, "$path.saturation")
                bounded(material.contrast, 0.5f, 2f, "$path.contrast")
                bounded(material.grainOpacity, 0f, 1f, "$path.grainOpacity")
            }

            is NativeThemeStyleMaterialSpecV1.Liquid -> {
                validateColor(material.tint, "$path.tint")
                bounded(material.opacity, 0.01f, 1f, "$path.opacity")
                bounded(material.blurRadiusDp, 1f, 64f, "$path.blurRadiusDp")
                bounded(material.vibrancy, 0f, 1f, "$path.vibrancy")
                bounded(material.lensHeightDp, 0f, 32f, "$path.lensHeightDp")
                bounded(material.refractionAmountDp, 0f, 48f, "$path.refractionAmountDp")
                bounded(material.highlightWidthDp, 0f, 8f, "$path.highlightWidthDp")
                bounded(material.highlightBlurDp, 0f, 16f, "$path.highlightBlurDp")
            }

            is NativeThemeStyleMaterialSpecV1.Water -> {
                validateColor(material.tint, "$path.tint")
                bounded(material.opacity, 0.01f, 1f, "$path.opacity")
                bounded(material.frostDp, 0f, 32f, "$path.frostDp")
                bounded(material.curve, 0f, 1f, "$path.curve")
                bounded(material.refraction, 0f, 1f, "$path.refraction")
                bounded(material.dispersion, 0f, 1f, "$path.dispersion")
                bounded(material.saturation, 0f, 2f, "$path.saturation")
                bounded(material.contrast, 0.5f, 2f, "$path.contrast")
            }
        }
    }

    private fun validateIconContainer(
        iconContainer: NativeThemeStyleIconContainerSpecV1,
        path: String,
    ) {
        if (iconContainer !is NativeThemeStyleIconContainerSpecV1.Container) return
        bounded(iconContainer.containerSizeDp, 16f, 96f, "$path.containerSizeDp")
        bounded(iconContainer.iconSizeDp, 8f, 64f, "$path.iconSizeDp")
        if (iconContainer.iconSizeDp > iconContainer.containerSizeDp) {
            issue(
                code = NativeThemeStyleIssueCodeV1.VALUE_OUT_OF_RANGE,
                path = "$path.iconSizeDp",
                detail = "An icon cannot be larger than its visual container.",
            )
        }
        validateShape(iconContainer.shape, "$path.shape")
        validateMaterial(iconContainer.material, "$path.material")
        validateColor(iconContainer.contentColor, "$path.contentColor")
        iconContainer.border?.let { border -> validateBorderStack(border, "$path.border") }
        iconContainer.shadows?.let { shadows -> validateShadowStack(shadows, "$path.shadows") }
    }

    private fun validateMenu(
        menu: NativeThemeStyleMenuSpecV1,
        path: String,
    ) {
        validateMaterial(menu.material, "$path.material")
        validateShape(menu.shape, "$path.shape")
        validateText(menu.label, "$path.label")
        validateColor(menu.iconColor, "$path.iconColor")
        validateColor(menu.dividerColor, "$path.dividerColor")
        bounded(menu.dividerThicknessDp, 0f, 4f, "$path.dividerThicknessDp")
        bounded(menu.widthDp, 112f, 560f, "$path.widthDp")
        bounded(menu.itemMinHeightDp, 32f, 96f, "$path.itemMinHeightDp")
        validateInsets(menu.contentInsets, "$path.contentInsets")
        validateMenuItem(menu.selectedItem, "$path.selectedItem")
        validateMenuItem(menu.disabledItem, "$path.disabledItem")
        validateMenuItem(menu.destructiveItem, "$path.destructiveItem")
        menu.border?.let { border -> validateBorderStack(border, "$path.border") }
        menu.shadows?.let { shadows -> validateShadowStack(shadows, "$path.shadows") }
    }

    private fun validateMenuItem(
        item: NativeThemeStyleMenuItemSpecV1,
        path: String,
    ) {
        validateColor(item.containerColor, "$path.containerColor")
        validateColor(item.contentColor, "$path.contentColor")
        validateColor(item.iconColor, "$path.iconColor")
    }

    private fun validateInsets(
        insets: NativeThemeStyleInsetsV1,
        path: String,
    ) {
        bounded(insets.startDp, 0f, 32f, "$path.startDp")
        bounded(insets.topDp, 0f, 32f, "$path.topDp")
        bounded(insets.endDp, 0f, 32f, "$path.endDp")
        bounded(insets.bottomDp, 0f, 32f, "$path.bottomDp")
    }

    private fun validateStateSelector(
        selector: NativeThemeStyleStateSelectorV1,
        path: String,
    ) {
        count(selector.conditions.size, 0, 4, "$path.conditions", "A state selector")
        duplicateValues(selector.conditions.map { condition -> condition.axis.name }) { axis ->
            issue(
                code = NativeThemeStyleIssueCodeV1.INVALID_STATE_CONDITION,
                path = "$path.conditions",
                detail = "State axis $axis is declared more than once.",
            )
        }
        selector.conditions.forEachIndexed { index, condition ->
            if (!condition.axis.accepts(condition.value)) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.INVALID_STATE_CONDITION,
                    path = "$path.conditions[$index]",
                    detail = "State value ${condition.value.name} is not valid for ${condition.axis.name}.",
                )
            }
        }
    }

    private fun validateStateRuleConflicts(
        rules: List<NativeThemeStyleStateRuleV1>,
        path: String,
    ) {
        rules.forEachIndexed { leftIndex, left ->
            rules.drop(leftIndex + 1).forEachIndexed inner@{ relativeIndex, right ->
                val rightIndex = leftIndex + relativeIndex + 1
                if (!left.selector.canMatch(right.selector)) return@inner
                if (left.selector.isStrictlyMoreSpecificThan(right.selector) ||
                    right.selector.isStrictlyMoreSpecificThan(left.selector)
                ) {
                    return@inner
                }
                if (!left.surfaces.overlaps(right.surfaces)) return@inner
                val conflictingProperties = left.patch.addresses().intersect(right.patch.addresses())
                if (conflictingProperties.isNotEmpty()) {
                    issue(
                        code = NativeThemeStyleIssueCodeV1.SELECTOR_CONFLICT,
                        path = "$path[$leftIndex,$rightIndex]",
                        detail = "Selectors can match the same state and write the same part property.",
                    )
                }
            }
        }
    }

    private fun validateTokenGraph(
        tokens: Map<NativeThemeStyleTokenIdV1, NativeThemeStyleTokenV1>,
    ): Map<NativeThemeStyleTokenIdV1, NativeThemeStyleValueKindV1?> {
        val resolvedKinds = mutableMapOf<NativeThemeStyleTokenIdV1, NativeThemeStyleValueKindV1?>()
        val resolving = linkedSetOf<NativeThemeStyleTokenIdV1>()

        fun resolveKind(tokenId: NativeThemeStyleTokenIdV1): NativeThemeStyleValueKindV1? {
            if (tokenId in resolvedKinds) return resolvedKinds.getValue(tokenId)
            if (tokenId in resolving) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.TOKEN_REFERENCE_CYCLE,
                    path = "tokens.${tokenId.value}",
                    detail = "Token references must form an acyclic graph.",
                )
                return null
            }
            val token = tokens[tokenId]
            if (token == null) {
                issue(
                    code = NativeThemeStyleIssueCodeV1.TOKEN_REFERENCE_MISSING,
                    path = "tokens.${tokenId.value}",
                    detail = "Referenced token does not exist in the cascade.",
                )
                return null
            }
            resolving += tokenId
            val value = token.value
            val resolvedKind =
                when (value) {
                    is NativeThemeStyleValueV1.TokenReference -> {
                        val targetKind = resolveKind(value.tokenId)
                        if (targetKind != null && targetKind != value.expectedKind) {
                            issue(
                                code = NativeThemeStyleIssueCodeV1.TOKEN_KIND_MISMATCH,
                                path = "tokens.${token.id.value}",
                                detail = "Token reference expects ${value.expectedKind.name} but resolves to ${targetKind.name}.",
                            )
                        }
                        value.expectedKind
                    }

                    else -> value.valueKindOrNull()
                }
            resolving -= tokenId
            resolvedKinds[tokenId] = resolvedKind
            return resolvedKind
        }

        tokens.keys.forEach(::resolveKind)
        return resolvedKinds
    }

    private fun validateLayerReferences(
        layer: NativeThemeStyleLayerV1,
        path: String,
        tokens: Map<NativeThemeStyleTokenIdV1, NativeThemeStyleTokenV1>,
        tokenKinds: Map<NativeThemeStyleTokenIdV1, NativeThemeStyleValueKindV1?>,
    ) {
        fun validatePatchReferences(
            patch: NativeThemeStylePatchV1,
            patchPath: String,
        ) {
            patch.parts.forEachIndexed { partIndex, part ->
                part.properties.forEachIndexed { propertyIndex, property ->
                    val value = property.value
                    if (value !is NativeThemeStyleValueV1.TokenReference) return@forEachIndexed
                    val propertyPath = "$patchPath.parts[$partIndex].properties[$propertyIndex].value"
                    val target = tokens[value.tokenId]
                    if (target == null) {
                        issue(
                            code = NativeThemeStyleIssueCodeV1.TOKEN_REFERENCE_MISSING,
                            path = propertyPath,
                            detail = "Referenced token ${value.tokenId.value} does not exist in the cascade.",
                        )
                        return@forEachIndexed
                    }
                    val targetKind = tokenKinds[target.id]
                    if (targetKind != null && targetKind != value.expectedKind) {
                        issue(
                            code = NativeThemeStyleIssueCodeV1.TOKEN_KIND_MISMATCH,
                            path = propertyPath,
                            detail = "Property expects ${value.expectedKind.name} but token resolves to ${targetKind.name}.",
                        )
                    }
                }
            }
        }

        fun validateScopeReferences(
            scope: NativeThemeStyleScopeV1,
            scopePath: String,
        ) {
            validatePatchReferences(scope.common, "$scopePath.common")
            scope.surfaceOverrides.forEachIndexed { index, override ->
                validatePatchReferences(override.patch, "$scopePath.surfaceOverrides[$index].patch")
            }
            scope.stateRules.forEachIndexed { index, rule ->
                validatePatchReferences(rule.patch, "$scopePath.stateRules[$index].patch")
            }
        }

        layer.familyStyles.forEachIndexed { index, style ->
            validateScopeReferences(style.scope, "$path.familyStyles[$index].scope")
        }
        layer.componentStyles.forEachIndexed { index, style ->
            validateScopeReferences(style.scope, "$path.componentStyles[$index].scope")
        }
    }

    private fun mergedTokens(
        layers: List<NativeThemeStyleLayerV1>,
    ): Map<NativeThemeStyleTokenIdV1, NativeThemeStyleTokenV1> {
        val tokens = linkedMapOf<NativeThemeStyleTokenIdV1, NativeThemeStyleTokenV1>()
        layers.forEach { layer ->
            layer.tokens.forEach { token -> tokens[token.id] = token }
        }
        return tokens
    }

    private fun bounded(
        value: Float,
        minimum: Float,
        maximum: Float,
        path: String,
    ) {
        if (!value.isFinite() || value < minimum || value > maximum) {
            issue(
                code = NativeThemeStyleIssueCodeV1.VALUE_OUT_OF_RANGE,
                path = path,
                detail = "Value must be finite and between $minimum and $maximum.",
            )
        }
    }

    private fun count(
        value: Int,
        minimum: Int,
        maximum: Int,
        path: String,
        subject: String,
    ) {
        if (value !in minimum..maximum) {
            issue(
                code = NativeThemeStyleIssueCodeV1.VALUE_OUT_OF_RANGE,
                path = path,
                detail = "$subject must contain between $minimum and $maximum entries.",
            )
        }
    }

    private fun issue(
        code: NativeThemeStyleIssueCodeV1,
        path: String,
        detail: String,
    ) {
        issues += NativeThemeStyleIssueV1(code = code, path = path, detail = detail)
    }
}

internal fun NativeThemeStyleStateAxisV1.accepts(
    value: NativeThemeStyleStateValueV1,
): Boolean =
    when (this) {
        NativeThemeStyleStateAxisV1.AVAILABILITY ->
            value in setOf(NativeThemeStyleStateValueV1.ENABLED, NativeThemeStyleStateValueV1.DISABLED)

        NativeThemeStyleStateAxisV1.SELECTION ->
            value in setOf(NativeThemeStyleStateValueV1.SELECTED, NativeThemeStyleStateValueV1.UNSELECTED)

        NativeThemeStyleStateAxisV1.INTERACTION ->
            value in
                setOf(
                    NativeThemeStyleStateValueV1.RESTING,
                    NativeThemeStyleStateValueV1.PRESSED,
                    NativeThemeStyleStateValueV1.FOCUSED,
                    NativeThemeStyleStateValueV1.HOVERED,
                )

        NativeThemeStyleStateAxisV1.ACTIVITY ->
            value in
                setOf(
                    NativeThemeStyleStateValueV1.IDLE,
                    NativeThemeStyleStateValueV1.LOADING,
                    NativeThemeStyleStateValueV1.STREAMING,
                    NativeThemeStyleStateValueV1.SUCCESS,
                    NativeThemeStyleStateValueV1.ERROR,
                )

        NativeThemeStyleStateAxisV1.VALIDATION ->
            value in setOf(NativeThemeStyleStateValueV1.VALID, NativeThemeStyleStateValueV1.INVALID)

        NativeThemeStyleStateAxisV1.EXPANSION ->
            value in setOf(NativeThemeStyleStateValueV1.COLLAPSED, NativeThemeStyleStateValueV1.EXPANDED)

        NativeThemeStyleStateAxisV1.CONTENT ->
            value in setOf(NativeThemeStyleStateValueV1.EMPTY, NativeThemeStyleStateValueV1.POPULATED)

        NativeThemeStyleStateAxisV1.VARIANT ->
            value in
                setOf(
                    NativeThemeStyleStateValueV1.STANDARD,
                    NativeThemeStyleStateValueV1.CAUTION,
                    NativeThemeStyleStateValueV1.DESTRUCTIVE,
                    NativeThemeStyleStateValueV1.NAVIGATION_DESTINATION,
                    NativeThemeStyleStateValueV1.ACTION,
                )
    }

private fun NativeThemeStyleStateSelectorV1.canMatch(
    other: NativeThemeStyleStateSelectorV1,
): Boolean {
    val ownValues = conditions.associate { condition -> condition.axis to condition.value }
    return other.conditions.all { condition ->
        ownValues[condition.axis]?.let { value -> value == condition.value } ?: true
    }
}

private fun NativeThemeStyleStateSelectorV1.isStrictlyMoreSpecificThan(
    other: NativeThemeStyleStateSelectorV1,
): Boolean {
    if (conditions.size <= other.conditions.size) return false
    val ownValues = conditions.associate { condition -> condition.axis to condition.value }
    return other.conditions.all { condition -> ownValues[condition.axis] == condition.value }
}

private fun Set<NativeThemeHostSurface>.overlaps(
    other: Set<NativeThemeHostSurface>,
): Boolean =
    isEmpty() || other.isEmpty() || intersect(other).isNotEmpty()

private data class NativeThemeStylePropertyAddressV1(
    val part: NativeThemeStylePartIdV1,
    val property: NativeThemeStylePropertyIdV1,
)

private fun NativeThemeStylePatchV1.addresses(): Set<NativeThemeStylePropertyAddressV1> =
    parts.flatMap { part ->
        part.properties.map { property ->
            NativeThemeStylePropertyAddressV1(part = part.part, property = property.id)
        }
    }.toSet()

private inline fun <T> duplicateValues(
    values: List<T>,
    onDuplicate: (T) -> Unit,
) {
    values
        .groupingBy { value -> value }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .forEach(onDuplicate)
}

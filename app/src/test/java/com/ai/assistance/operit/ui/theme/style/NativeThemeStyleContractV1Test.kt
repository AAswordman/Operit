package com.ai.assistance.operit.ui.theme.style

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentId
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusContractV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeStyleContractV1Test {
    @Test
    fun cascadeResolvesFamilyComponentAndStateLayersInOrder() {
        val foundation =
            layer(
                id = "operit.foundation",
                familyStyles =
                    listOf(
                        NativeThemeComponentFamilyStyleV1(
                            familyId = familyId,
                            scope =
                                completeActionScope(
                                    surface = "#111111ff",
                                    stateRules =
                                        listOf(
                                            stateRule(
                                                id = "pressed",
                                                condition = interactionCondition,
                                                properties = listOf(surfaceColor("#222222ff")),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )
        val themeLayer =
            layer(
                id = "operit.theme",
                familyStyles =
                    listOf(
                        NativeThemeComponentFamilyStyleV1(
                            familyId = familyId,
                            scope = scope(commonProperties = listOf(surfaceColor("#333333ff"))),
                        ),
                    ),
                componentStyles =
                    listOf(
                        NativeThemeComponentStyleV1(
                            componentId = componentId,
                            scope = scope(commonProperties = listOf(surfaceColor("#444444ff"))),
                        ),
                    ),
            )
        val instance =
            layer(
                id = "operit.instance",
                componentStyles =
                    listOf(
                        NativeThemeComponentStyleV1(
                            componentId = componentId,
                            scope =
                                scope(
                                    stateRules =
                                        listOf(
                                            stateRule(
                                                id = "instance_pressed",
                                                condition = interactionCondition,
                                                properties = listOf(surfaceColor("#555555ff")),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val resolved =
            resolveNativeThemeStyleCascadeV1(
                cascade =
                    NativeThemeStyleCascadeV1(
                        foundation = foundation,
                        themeLayers = listOf(themeLayer),
                        instance = instance,
                    ),
                request =
                    NativeThemeStyleResolutionRequestV1(
                        componentId = componentId,
                        surface = NativeThemeHostSurface.MAIN,
                        state =
                            NativeThemeStyleStateVectorV1(
                                values =
                                    mapOf(
                                        NativeThemeStyleStateAxisV1.INTERACTION to
                                            NativeThemeStyleStateValueV1.PRESSED,
                                    ),
                            ),
                    ),
                componentContracts = listOf(NativeThemeActionButtonContractV1.contract),
            )

        assertEquals(
            surfaceColor("#555555ff").value,
            resolved.parts.getValue(surfacePart).getValue(NativeThemeStylePropertyIdV1.SURFACE_COLOR),
        )
    }

    @Test
    fun contractAcceptsComplexVisualSpecs() {
        val complexBorder =
            NativeThemeStyleBorderStackSpecV1(
                layers =
                    listOf(
                        NativeThemeStyleBorderLayerV1(
                            id = NativeThemeStyleRuleIdV1("outer"),
                            sides = NativeThemeStyleBorderSideV1.entries.toSet(),
                            alignment = NativeThemeStyleBorderAlignmentV1.OUTSIDE,
                            widthDp = 2f,
                            brush =
                                NativeThemeStyleBrushV1.LinearGradient(
                                    angleDegrees = 45f,
                                    stops =
                                        listOf(
                                            NativeThemeStyleGradientStopV1(0f, color("#4e8cffcc")),
                                            NativeThemeStyleGradientStopV1(1f, color("#d259ffff")),
                                        ),
                                ),
                            opacity = 0.8f,
                            dash = NativeThemeStyleDashPatternV1(4f, 2f),
                        ),
                        NativeThemeStyleBorderLayerV1(
                            id = NativeThemeStyleRuleIdV1("inner"),
                            sides = NativeThemeStyleBorderSideV1.entries.toSet(),
                            alignment = NativeThemeStyleBorderAlignmentV1.INSIDE,
                            widthDp = 1f,
                            brush = NativeThemeStyleBrushV1.Solid(color("#ffffff80")),
                        ),
                    ),
            )
        val liquidMaterial =
            NativeThemeStyleMaterialSpecV1.Liquid(
                tint = color("#1c2233ff"),
                opacity = 0.26f,
                blurRadiusDp = 18f,
                vibrancy = 0.72f,
                lensHeightDp = 12f,
                refractionAmountDp = 18f,
                chromaticAberration = true,
                highlightWidthDp = 1f,
                highlightBlurDp = 3f,
            )
        val menu =
            NativeThemeStyleMenuSpecV1(
                material =
                    NativeThemeStyleMaterialSpecV1.Translucent(
                        tint = color("#1c2233ff"),
                        opacity = 0.94f,
                    ),
                shape = NativeThemeStyleShapeSpecV1.Capsule,
                label = textStyle("#ffffffff"),
                iconColor = color("#ffffffff"),
                dividerColor = color("#ffffff33"),
                dividerThicknessDp = 1f,
                widthDp = 240f,
                itemMinHeightDp = 48f,
                contentInsets = NativeThemeStyleInsetsV1(12f, 8f, 12f, 8f),
                selectedItem = menuItem("#2c6fd6ff"),
                disabledItem = menuItem("#565a66ff"),
                destructiveItem = menuItem("#b3261eff"),
                border = complexBorder,
                shadows =
                    NativeThemeStyleShadowStackSpecV1(
                        layers =
                            listOf(
                                NativeThemeStyleShadowLayerV1(
                                    id = NativeThemeStyleRuleIdV1("outer"),
                                    kind = NativeThemeStyleShadowKindV1.OUTER,
                                    color = color("#00000066"),
                                    offsetXDp = 0f,
                                    offsetYDp = 8f,
                                    blurRadiusDp = 20f,
                                ),
                            ),
                    ),
            )
        val iconContainer =
            NativeThemeStyleIconContainerSpecV1.Container(
                containerSizeDp = 40f,
                iconSizeDp = 20f,
                shape = NativeThemeStyleShapeSpecV1.Capsule,
                material =
                    NativeThemeStyleMaterialSpecV1.Frosted(
                        tint = color("#21345aff"),
                        opacity = 0.4f,
                        backdropBlurRadiusDp = 12f,
                        saturation = 1.1f,
                        contrast = 1.2f,
                    ),
                contentColor = color("#ffffffff"),
                border = complexBorder,
            )

        val result =
            validateNativeThemeStyleCascadeV1(
                NativeThemeStyleCascadeV1(
                    foundation =
                        layer(
                            id = "operit.foundation",
                            tokens =
                                listOf(
                                    NativeThemeStyleTokenV1(
                                        id = NativeThemeStyleTokenIdV1("style.shape"),
                                        value =
                                            NativeThemeStyleValueV1.Shape(
                                                NativeThemeStyleShapeSpecV1.Capsule,
                                            ),
                                    ),
                                    NativeThemeStyleTokenV1(
                                        id = NativeThemeStyleTokenIdV1("style.border"),
                                        value = NativeThemeStyleValueV1.Border(complexBorder),
                                    ),
                                    NativeThemeStyleTokenV1(
                                        id = NativeThemeStyleTokenIdV1("style.material"),
                                        value = NativeThemeStyleValueV1.Material(liquidMaterial),
                                    ),
                                    NativeThemeStyleTokenV1(
                                        id = NativeThemeStyleTokenIdV1("style.icon_container"),
                                        value = NativeThemeStyleValueV1.IconContainer(iconContainer),
                                    ),
                                    NativeThemeStyleTokenV1(
                                        id = NativeThemeStyleTokenIdV1("style.menu"),
                                        value = NativeThemeStyleValueV1.Menu(menu),
                                    ),
                                ),
                        ),
                    themeLayers = emptyList(),
                    instance = layer(id = "operit.instance"),
                ),
            )

        assertTrue(result.issues.joinToString { issue -> issue.detail }, result.isValid)
    }

    @Test
    fun contractRejectsDuplicatePropertiesInvalidColorsAndRequiredValues() {
        val invalidScope =
            scope(
                commonProperties =
                    listOf(
                        surfaceColor("invalid"),
                        surfaceColor("#112233ff"),
                        NativeThemeStylePropertyV1(
                            id = NativeThemeStylePropertyIdV1.TEXT_STYLE,
                            value = NativeThemeStyleValueV1.None,
                        ),
                    ),
            )

        val result =
            validateNativeThemeStyleCascadeV1(
                NativeThemeStyleCascadeV1(
                    foundation =
                        layer(
                            id = "operit.foundation",
                            componentStyles =
                                listOf(
                                    NativeThemeComponentStyleV1(
                                        componentId = componentId,
                                        scope = invalidScope,
                                    ),
                                ),
                        ),
                    themeLayers = emptyList(),
                    instance = layer(id = "operit.instance"),
                ),
            )

        assertFalse(result.isValid)
        assertTrue(result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.INVALID_COLOR })
        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.DUPLICATE_STYLE_PROPERTY },
        )
        assertTrue(result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.NONE_NOT_ALLOWED })
    }

    @Test
    fun contractRejectsNonDeterministicStateSelectors() {
        val scope =
            scope(
                stateRules =
                    listOf(
                        stateRule(
                            id = "selected",
                            condition = selectionCondition,
                            properties = listOf(surfaceColor("#112233ff")),
                        ),
                        stateRule(
                            id = "pressed",
                            condition =
                                NativeThemeStyleStateConditionV1(
                                    axis = NativeThemeStyleStateAxisV1.INTERACTION,
                                    value = NativeThemeStyleStateValueV1.PRESSED,
                                ),
                            properties = listOf(surfaceColor("#445566ff")),
                        ),
                    ),
            )

        val result =
            validateNativeThemeStyleCascadeV1(
                NativeThemeStyleCascadeV1(
                    foundation =
                        layer(
                            id = "operit.foundation",
                            componentStyles =
                                listOf(
                                    NativeThemeComponentStyleV1(componentId = componentId, scope = scope),
                                ),
                        ),
                    themeLayers = emptyList(),
                    instance = layer(id = "operit.instance"),
                ),
            )

        assertTrue(result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.SELECTOR_CONFLICT })
    }

    @Test
    fun contractRejectsTokenReferenceCycles() {
        val colorToken = NativeThemeStyleTokenIdV1("color.primary")
        val surfaceToken = NativeThemeStyleTokenIdV1("color.surface")
        val result =
            validateNativeThemeStyleCascadeV1(
                NativeThemeStyleCascadeV1(
                    foundation =
                        layer(
                            id = "operit.foundation",
                            tokens =
                                listOf(
                                    NativeThemeStyleTokenV1(
                                        id = colorToken,
                                        value =
                                            NativeThemeStyleValueV1.TokenReference(
                                                tokenId = surfaceToken,
                                                expectedKind = NativeThemeStyleValueKindV1.COLOR,
                                            ),
                                    ),
                                    NativeThemeStyleTokenV1(
                                        id = surfaceToken,
                                        value =
                                            NativeThemeStyleValueV1.TokenReference(
                                                tokenId = colorToken,
                                                expectedKind = NativeThemeStyleValueKindV1.COLOR,
                                            ),
                                    ),
                                ),
                        ),
                    themeLayers = emptyList(),
                    instance = layer(id = "operit.instance"),
                ),
            )

        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.TOKEN_REFERENCE_CYCLE },
        )
    }

    @Test
    fun styleCatalogRejectsUnknownPartsAndPropertiesOutsideTheComponentContract() {
        val invalidScope =
            NativeThemeStyleScopeV1(
                common =
                    NativeThemeStylePatchV1(
                        parts =
                            listOf(
                                NativeThemeStylePartPatchV1(
                                    part = surfacePart,
                                    properties =
                                        listOf(
                                            NativeThemeStylePropertyV1(
                                                id = NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                                                value = NativeThemeStyleValueV1.Color(color("#112233ff")),
                                            ),
                                        ),
                                ),
                                NativeThemeStylePartPatchV1(
                                    part = NativeThemeStylePartIdV1("unknown_part"),
                                    properties = listOf(surfaceColor("#445566ff")),
                                ),
                            ),
                    ),
            )
        val cascade =
            NativeThemeStyleCascadeV1(
                foundation =
                    layer(
                        id = "operit.foundation",
                        componentStyles =
                            listOf(
                                NativeThemeComponentStyleV1(
                                    componentId = componentId,
                                    scope = invalidScope,
                                ),
                            ),
                    ),
                themeLayers = emptyList(),
                instance = layer(id = "operit.instance"),
            )

        val result =
            validateNativeThemeStyleCatalogV1(
                cascade = cascade,
                componentContracts = listOf(NativeThemeActionButtonContractV1.contract),
            )

        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.STYLE_PROPERTY_NOT_ALLOWED },
        )
        assertTrue(result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.UNKNOWN_STYLE_PART })
    }

    @Test
    fun styleCatalogRequiresEveryRequiredPartAndProperty() {
        val result =
            validateNativeThemeStyleCatalogV1(
                cascade =
                    NativeThemeStyleCascadeV1(
                        foundation = layer(id = "operit.foundation"),
                        themeLayers = emptyList(),
                        instance = layer(id = "operit.instance"),
                    ),
                componentContracts = listOf(NativeThemeActionButtonContractV1.contract),
            )

        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.INCOMPLETE_COMPONENT_STYLE },
        )
    }

    @Test
    fun styleCatalogRejectsStateValuesThatTheComponentCannotProduce() {
        val statusComponentId = NativeThemeComponentId("operit.feedback.operation_status")
        val result =
            validateNativeThemeStyleCatalogV1(
                cascade =
                    NativeThemeStyleCascadeV1(
                        foundation =
                            layer(
                                id = "operit.foundation",
                                componentStyles =
                                    listOf(
                                        NativeThemeComponentStyleV1(
                                            componentId = statusComponentId,
                                            scope =
                                                scope(
                                                    stateRules =
                                                        listOf(
                                                            stateRule(
                                                                id = "streaming",
                                                                condition =
                                                                    NativeThemeStyleStateConditionV1(
                                                                        axis =
                                                                            NativeThemeStyleStateAxisV1.ACTIVITY,
                                                                        value =
                                                                            NativeThemeStyleStateValueV1.STREAMING,
                                                                    ),
                                                                properties =
                                                                    listOf(surfaceColor("#112233ff")),
                                                            ),
                                                        ),
                                                ),
                                        ),
                                    ),
                            ),
                        themeLayers = emptyList(),
                        instance = layer(id = "operit.instance"),
                    ),
                componentContracts = listOf(NativeThemeOperationStatusContractV1.contract),
            )

        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.STYLE_STATE_VALUE_NOT_SUPPORTED },
        )
    }

    @Test
    fun linkerRejectsMaterialThatIsNotDeclaredForItsActualSurface() {
        val liquid =
            NativeThemeStyleMaterialSpecV1.Liquid(
                tint = color("#12233aff"),
                opacity = 0.3f,
                blurRadiusDp = 14f,
                vibrancy = 0.6f,
                lensHeightDp = 8f,
                refractionAmountDp = 12f,
                chromaticAberration = true,
                highlightWidthDp = 1f,
                highlightBlurDp = 2f,
            )
        val request =
            NativeThemeStyleLinkRequestV1(
                cascade =
                    NativeThemeStyleCascadeV1(
                        foundation =
                            layer(
                                id = "operit.foundation",
                                familyStyles =
                                    listOf(
                                        NativeThemeComponentFamilyStyleV1(
                                            familyId = familyId,
                                            scope = completeActionScope("#12233aff", material = liquid),
                                        ),
                                    ),
                            ),
                        themeLayers = emptyList(),
                        instance = layer(id = "operit.instance"),
                    ),
                componentContracts = listOf(NativeThemeActionButtonContractV1.contract),
                declaredCapabilities = emptyList(),
                hostCapabilityProfile =
                    NativeThemeComposeHostCapabilityProfileV1(
                        id = NativeThemeComposeCapabilityProfileIdV1("operit.compose.main"),
                        capabilities = emptyList(),
                    ),
            )

        val result = linkNativeThemeStyleV1(request)

        assertTrue(result is NativeThemeStyleLinkResultV1.Rejected)
        val issues = (result as NativeThemeStyleLinkResultV1.Rejected).issues
        assertTrue(
            issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.UNDECLARED_STYLE_CAPABILITY },
        )
    }

    @Test
    fun linkerUsesTheFinalMaterialAfterAnInstanceOverride() {
        val liquid =
            NativeThemeStyleMaterialSpecV1.Liquid(
                tint = color("#12233aff"),
                opacity = 0.3f,
                blurRadiusDp = 14f,
                vibrancy = 0.6f,
                lensHeightDp = 8f,
                refractionAmountDp = 12f,
                chromaticAberration = true,
                highlightWidthDp = 1f,
                highlightBlurDp = 2f,
            )
        val result =
            linkNativeThemeStyleV1(
                NativeThemeStyleLinkRequestV1(
                    cascade =
                        NativeThemeStyleCascadeV1(
                            foundation =
                                layer(
                                    id = "operit.foundation",
                                    familyStyles =
                                        listOf(
                                            NativeThemeComponentFamilyStyleV1(
                                                familyId = familyId,
                                                scope = completeActionScope("#12233aff", material = liquid),
                                            ),
                                        ),
                                ),
                            themeLayers = emptyList(),
                            instance =
                                layer(
                                    id = "operit.instance",
                                    componentStyles =
                                        listOf(
                                            NativeThemeComponentStyleV1(
                                                componentId = componentId,
                                                scope =
                                                    scope(
                                                        commonProperties =
                                                            listOf(
                                                                NativeThemeStylePropertyV1(
                                                                    id =
                                                                        NativeThemeStylePropertyIdV1.MATERIAL,
                                                                    value = NativeThemeStyleValueV1.None,
                                                                ),
                                                            ),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    componentContracts = listOf(NativeThemeActionButtonContractV1.contract),
                    declaredCapabilities = emptyList(),
                    hostCapabilityProfile =
                        NativeThemeComposeHostCapabilityProfileV1(
                            id = NativeThemeComposeCapabilityProfileIdV1("operit.compose.main"),
                            capabilities = emptyList(),
                        ),
                ),
            )

        assertTrue(result is NativeThemeStyleLinkResultV1.Linked)
    }

    @Test
    fun linkerChecksCapabilitiesForPartialStateVectors() {
        val liquid =
            NativeThemeStyleMaterialSpecV1.Liquid(
                tint = color("#12233aff"),
                opacity = 0.3f,
                blurRadiusDp = 14f,
                vibrancy = 0.6f,
                lensHeightDp = 8f,
                refractionAmountDp = 12f,
                chromaticAberration = true,
                highlightWidthDp = 1f,
                highlightBlurDp = 2f,
            )
        val clearMaterial =
            NativeThemeStylePropertyV1(
                id = NativeThemeStylePropertyIdV1.MATERIAL,
                value = NativeThemeStyleValueV1.None,
            )
        val result =
            linkNativeThemeStyleV1(
                NativeThemeStyleLinkRequestV1(
                    cascade =
                        NativeThemeStyleCascadeV1(
                            foundation =
                                layer(
                                    id = "operit.foundation",
                                    familyStyles =
                                        listOf(
                                            NativeThemeComponentFamilyStyleV1(
                                                familyId = familyId,
                                                scope = completeActionScope("#12233aff", material = liquid),
                                            ),
                                        ),
                                ),
                            themeLayers = emptyList(),
                            instance =
                                layer(
                                    id = "operit.instance",
                                    componentStyles =
                                        listOf(
                                            NativeThemeComponentStyleV1(
                                                componentId = componentId,
                                                scope =
                                                    scope(
                                                        stateRules =
                                                            listOf(
                                                                stateRule(
                                                                    id = "enabled",
                                                                    condition = availabilityEnabledCondition,
                                                                    properties = listOf(clearMaterial),
                                                                ),
                                                                stateRule(
                                                                    id = "disabled",
                                                                    condition = availabilityDisabledCondition,
                                                                    properties = listOf(clearMaterial),
                                                                ),
                                                            ),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    componentContracts = listOf(NativeThemeActionButtonContractV1.contract),
                    declaredCapabilities = emptyList(),
                    hostCapabilityProfile =
                        NativeThemeComposeHostCapabilityProfileV1(
                            id = NativeThemeComposeCapabilityProfileIdV1("operit.compose.main"),
                            capabilities = emptyList(),
                        ),
                ),
            )

        assertTrue(result is NativeThemeStyleLinkResultV1.Rejected)
        val issues = (result as NativeThemeStyleLinkResultV1.Rejected).issues
        assertTrue(
            issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.UNDECLARED_STYLE_CAPABILITY },
        )
    }

    @Test
    fun linkerRequiresDeclaredCapabilitySurfacesToMatchFinalUsage() {
        val result =
            linkNativeThemeStyleV1(
                NativeThemeStyleLinkRequestV1(
                    cascade =
                        NativeThemeStyleCascadeV1(
                            foundation =
                                layer(
                                    id = "operit.foundation",
                                    familyStyles =
                                        listOf(
                                            NativeThemeComponentFamilyStyleV1(
                                                familyId = familyId,
                                                scope = completeActionScope("#12233aff", contentBlur = true),
                                            ),
                                        ),
                                ),
                            themeLayers = emptyList(),
                            instance = layer(id = "operit.instance"),
                        ),
                    componentContracts = listOf(NativeThemeActionButtonContractV1.contract),
                    declaredCapabilities =
                        listOf(
                            NativeThemeComposeCapabilityRequirementV1(
                                id = NativeThemeComposeCapabilityIdV1.CONTENT_BLUR,
                                surfaces =
                                    listOf(
                                        NativeThemeComposeCapabilitySurfaceRequirementV1(
                                            surface = NativeThemeHostSurface.OVERLAY,
                                            minimumVersion =
                                                NativeThemeComposeCapabilityVersionV1(major = 1, minor = 0),
                                        ),
                                    ),
                            ),
                        ),
                    hostCapabilityProfile =
                        NativeThemeComposeHostCapabilityProfileV1(
                            id = NativeThemeComposeCapabilityProfileIdV1("operit.compose.overlay"),
                            capabilities =
                                listOf(
                                    NativeThemeComposeCapabilitySupportV1(
                                        id = NativeThemeComposeCapabilityIdV1.CONTENT_BLUR,
                                        surfaces =
                                            listOf(
                                                NativeThemeComposeCapabilitySurfaceSupportV1(
                                                    surface = NativeThemeHostSurface.OVERLAY,
                                                    version =
                                                        NativeThemeComposeCapabilityVersionV1(
                                                            major = 1,
                                                            minor = 0,
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                ),
            )

        assertTrue(result is NativeThemeStyleLinkResultV1.Rejected)
        val issues = (result as NativeThemeStyleLinkResultV1.Rejected).issues
        assertTrue(
            issues.any {
                issue -> issue.code == NativeThemeStyleIssueCodeV1.DECLARED_CAPABILITY_SURFACE_MISMATCH
            },
        )
        assertTrue(
            issues.any {
                issue -> issue.code == NativeThemeStyleIssueCodeV1.UNUSED_DECLARED_CAPABILITY_SURFACE
            },
        )
    }

    @Test
    fun capabilityValidationRejectsSurfacesOutsideStyleApiV1() {
        val result =
            validateNativeThemeComposeCapabilityRequirementsV1(
                requirements =
                    listOf(
                        NativeThemeComposeCapabilityRequirementV1(
                            id = NativeThemeComposeCapabilityIdV1.CONTENT_BLUR,
                            surfaces =
                                listOf(
                                    NativeThemeComposeCapabilitySurfaceRequirementV1(
                                        surface = NativeThemeHostSurface.GLANCE,
                                        minimumVersion =
                                            NativeThemeComposeCapabilityVersionV1(major = 1, minor = 0),
                                    ),
                                ),
                        ),
                    ),
                hostProfile =
                    NativeThemeComposeHostCapabilityProfileV1(
                        id = NativeThemeComposeCapabilityProfileIdV1("operit.compose.glance"),
                        capabilities =
                            listOf(
                                NativeThemeComposeCapabilitySupportV1(
                                    id = NativeThemeComposeCapabilityIdV1.CONTENT_BLUR,
                                    surfaces =
                                        listOf(
                                            NativeThemeComposeCapabilitySurfaceSupportV1(
                                                surface = NativeThemeHostSurface.GLANCE,
                                                version =
                                                    NativeThemeComposeCapabilityVersionV1(
                                                        major = 1,
                                                        minor = 0,
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )

        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.UNSUPPORTED_STYLE_SURFACE },
        )
    }

    @Test
    fun capabilityValidationRejectsMissingSurfaceAndVersionSupport() {
        val requirement =
            NativeThemeComposeCapabilityRequirementV1(
                id = NativeThemeComposeCapabilityIdV1.LIQUID_MATERIAL,
                surfaces =
                    listOf(
                        NativeThemeComposeCapabilitySurfaceRequirementV1(
                            surface = NativeThemeHostSurface.MAIN,
                            minimumVersion = NativeThemeComposeCapabilityVersionV1(major = 1, minor = 1),
                        ),
                        NativeThemeComposeCapabilitySurfaceRequirementV1(
                            surface = NativeThemeHostSurface.OVERLAY,
                            minimumVersion = NativeThemeComposeCapabilityVersionV1(major = 1, minor = 1),
                        ),
                    ),
            )
        val host =
            NativeThemeComposeHostCapabilityProfileV1(
                id = NativeThemeComposeCapabilityProfileIdV1("operit.compose.main"),
                capabilities =
                    listOf(
                        NativeThemeComposeCapabilitySupportV1(
                            id = NativeThemeComposeCapabilityIdV1.LIQUID_MATERIAL,
                            surfaces =
                                listOf(
                                    NativeThemeComposeCapabilitySurfaceSupportV1(
                                        surface = NativeThemeHostSurface.MAIN,
                                        version =
                                            NativeThemeComposeCapabilityVersionV1(major = 1, minor = 0),
                                    ),
                                ),
                        ),
                    ),
            )

        val result = validateNativeThemeComposeCapabilityRequirementsV1(listOf(requirement), host)

        assertFalse(result.isValid)
        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.CAPABILITY_VERSION_MISMATCH },
        )
        assertTrue(
            result.issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.CAPABILITY_SURFACE_MISMATCH },
        )
    }

    private fun layer(
        id: String,
        tokens: List<NativeThemeStyleTokenV1> = emptyList(),
        familyStyles: List<NativeThemeComponentFamilyStyleV1> = emptyList(),
        componentStyles: List<NativeThemeComponentStyleV1> = emptyList(),
    ): NativeThemeStyleLayerV1 =
        NativeThemeStyleLayerV1(
            id = NativeThemeStyleLayerIdV1(id),
            tokens = tokens,
            familyStyles = familyStyles,
            componentStyles = componentStyles,
        )

    private fun scope(
        commonProperties: List<NativeThemeStylePropertyV1> = emptyList(),
        stateRules: List<NativeThemeStyleStateRuleV1> = emptyList(),
    ): NativeThemeStyleScopeV1 =
        NativeThemeStyleScopeV1(
            common =
                NativeThemeStylePatchV1(
                    parts =
                        commonProperties.takeIf { properties -> properties.isNotEmpty() }
                            ?.let { properties ->
                                listOf(
                                    NativeThemeStylePartPatchV1(
                                        part = surfacePart,
                                        properties = properties,
                                    ),
                                )
                            }
                            ?: emptyList(),
                ),
            stateRules = stateRules,
        )

    private fun completeActionScope(
        surface: String,
        material: NativeThemeStyleMaterialSpecV1? = null,
        contentBlur: Boolean = false,
        stateRules: List<NativeThemeStyleStateRuleV1> = emptyList(),
    ): NativeThemeStyleScopeV1 =
        NativeThemeStyleScopeV1(
            common =
                NativeThemeStylePatchV1(
                    parts =
                        listOf(
                            NativeThemeStylePartPatchV1(
                                part = surfacePart,
                                properties =
                                    listOf(
                                        surfaceColor(surface),
                                        NativeThemeStylePropertyV1(
                                            id = NativeThemeStylePropertyIdV1.SHAPE,
                                            value =
                                                NativeThemeStyleValueV1.Shape(
                                                    NativeThemeStyleShapeSpecV1.Rectangle,
                                                ),
                                        ),
                                    ) +
                                        listOfNotNull(
                                            material?.let { value ->
                                                NativeThemeStylePropertyV1(
                                                    id = NativeThemeStylePropertyIdV1.MATERIAL,
                                                    value = NativeThemeStyleValueV1.Material(value),
                                                )
                                            },
                                            if (contentBlur) {
                                                NativeThemeStylePropertyV1(
                                                    id = NativeThemeStylePropertyIdV1.CONTENT_BLUR,
                                                    value =
                                                        NativeThemeStyleValueV1.Blur(
                                                            NativeThemeStyleBlurSpecV1(radiusDp = 8f),
                                                        ),
                                                )
                                            } else {
                                                null
                                            },
                                        ),
                            ),
                            NativeThemeStylePartPatchV1(
                                part = labelPart,
                                properties =
                                    listOf(
                                        NativeThemeStylePropertyV1(
                                            id = NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                                            value = NativeThemeStyleValueV1.Color(color("#ffffffff")),
                                        ),
                                        NativeThemeStylePropertyV1(
                                            id = NativeThemeStylePropertyIdV1.TEXT_STYLE,
                                            value = NativeThemeStyleValueV1.Text(textStyle("#ffffffff")),
                                        ),
                                    ),
                            ),
                            NativeThemeStylePartPatchV1(
                                part = leadingPart,
                                properties =
                                    listOf(
                                        NativeThemeStylePropertyV1(
                                            id = NativeThemeStylePropertyIdV1.CONTENT_COLOR,
                                            value = NativeThemeStyleValueV1.Color(color("#ffffffff")),
                                        ),
                                    ),
                            ),
                            NativeThemeStylePartPatchV1(
                                part = contentPart,
                                properties =
                                    listOf(
                                        NativeThemeStylePropertyV1(
                                            id = NativeThemeStylePropertyIdV1.PADDING,
                                            value =
                                                NativeThemeStyleValueV1.Metric(
                                                    NativeThemeStyleMetricSpecV1(
                                                        value = 12f,
                                                        unit = NativeThemeStyleMetricUnitV1.DP,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
            stateRules = stateRules,
        )

    private fun stateRule(
        id: String,
        condition: NativeThemeStyleStateConditionV1,
        properties: List<NativeThemeStylePropertyV1>,
    ): NativeThemeStyleStateRuleV1 =
        NativeThemeStyleStateRuleV1(
            id = NativeThemeStyleRuleIdV1(id),
            selector = NativeThemeStyleStateSelectorV1(conditions = listOf(condition)),
            patch =
                NativeThemeStylePatchV1(
                    parts =
                        listOf(
                            NativeThemeStylePartPatchV1(
                                part = surfacePart,
                                properties = properties,
                            ),
                        ),
                ),
        )

    private fun surfaceColor(value: String): NativeThemeStylePropertyV1 =
        NativeThemeStylePropertyV1(
            id = NativeThemeStylePropertyIdV1.SURFACE_COLOR,
            value = NativeThemeStyleValueV1.Color(color(value)),
        )

    private fun color(value: String): NativeThemeStyleColorSpecV1 =
        NativeThemeStyleColorSpecV1(
            light = NativeThemeStyleColorSourceV1.Literal(value),
            dark = NativeThemeStyleColorSourceV1.Literal(value),
        )

    private fun textStyle(value: String): NativeThemeTextStyleSpecV1 =
        NativeThemeTextStyleSpecV1(
            family = NativeThemeStyleFontFamilyV1.System(NativeThemeSystemFontFamilyV1.SANS_SERIF),
            fontSizeSp = 14f,
            lineHeightSp = 20f,
            fontWeight = 400,
            color = color(value),
        )

    private fun menuItem(containerColor: String): NativeThemeStyleMenuItemSpecV1 =
        NativeThemeStyleMenuItemSpecV1(
            containerColor = color(containerColor),
            contentColor = color("#ffffffff"),
            iconColor = color("#ffffffff"),
        )

    private companion object {
        val componentId = NativeThemeComponentId("operit.action.button")
        val familyId = NativeThemeComponentFamilyIdV1("operit.action")
        val surfacePart = NativeThemeStylePartIdV1("surface")
        val labelPart = NativeThemeStylePartIdV1("label")
        val leadingPart = NativeThemeStylePartIdV1("leading")
        val contentPart = NativeThemeStylePartIdV1("content")
        val selectionCondition =
            NativeThemeStyleStateConditionV1(
                axis = NativeThemeStyleStateAxisV1.SELECTION,
                value = NativeThemeStyleStateValueV1.SELECTED,
            )
        val interactionCondition =
            NativeThemeStyleStateConditionV1(
                axis = NativeThemeStyleStateAxisV1.INTERACTION,
                value = NativeThemeStyleStateValueV1.PRESSED,
            )
        val availabilityEnabledCondition =
            NativeThemeStyleStateConditionV1(
                axis = NativeThemeStyleStateAxisV1.AVAILABILITY,
                value = NativeThemeStyleStateValueV1.ENABLED,
            )
        val availabilityDisabledCondition =
            NativeThemeStyleStateConditionV1(
                axis = NativeThemeStyleStateAxisV1.AVAILABILITY,
                value = NativeThemeStyleStateValueV1.DISABLED,
            )
    }
}

package com.ai.assistance.operit.ui.theme.renderer.input

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentAccessibilityRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCategoryV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentEventV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentKeyV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentMemberId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticsV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStylePartContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStylePartIdsV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStylePropertySetsV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateFieldV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateValueV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentValueTypeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeComponentFamilyIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateAxisV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateAxisContractV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateValueV1

internal data class NativeThemeChoiceItemStateV1(
    val label: String,
    val supportingText: String?,
    val selected: Boolean,
    val enabled: Boolean,
)

internal sealed interface NativeThemeChoiceItemEventV1 {
    data object Select : NativeThemeChoiceItemEventV1
}

internal data object NativeThemeChoiceItemSlotsV1

internal object NativeThemeChoiceItemContractV1 {
    val labelFieldId = NativeThemeComponentMemberId("label")
    val supportingTextFieldId = NativeThemeComponentMemberId("supporting_text")
    val selectedFieldId = NativeThemeComponentMemberId("selected")
    val enabledFieldId = NativeThemeComponentMemberId("enabled")
    val selectEventId = NativeThemeComponentMemberId("select")

    val contract =
        NativeThemeComponentContractV1(
            id = NativeThemeComponentId("operit.input.choice_item"),
            version = NativeThemeComponentVersionV1(major = 1, minor = 0),
            category = NativeThemeComponentCategoryV1.INPUT,
            required = true,
            supportedHostSurfaces = setOf(NativeThemeHostSurface.MAIN),
            styleFamily = NativeThemeComponentFamilyIdV1("operit.input"),
            styleParts =
                listOf(
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.surface,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.surface,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.surfaceRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.label,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.text,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.textRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.supportingText,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.text,
                        required = false,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.indicator,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.indicator,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.indicatorRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.content,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.content,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.contentRequired,
                    ),
                ),
            styleStateAxes =
                listOf(
                    NativeThemeStyleStateAxisContractV1(
                        axis = NativeThemeStyleStateAxisV1.AVAILABILITY,
                        values =
                            setOf(
                                NativeThemeStyleStateValueV1.ENABLED,
                                NativeThemeStyleStateValueV1.DISABLED,
                            ),
                    ),
                    NativeThemeStyleStateAxisContractV1(
                        axis = NativeThemeStyleStateAxisV1.SELECTION,
                        values =
                            setOf(
                                NativeThemeStyleStateValueV1.SELECTED,
                                NativeThemeStyleStateValueV1.UNSELECTED,
                            ),
                    ),
                    NativeThemeStyleStateAxisContractV1(
                        axis = NativeThemeStyleStateAxisV1.INTERACTION,
                        values =
                            setOf(
                                NativeThemeStyleStateValueV1.RESTING,
                                NativeThemeStyleStateValueV1.PRESSED,
                                NativeThemeStyleStateValueV1.FOCUSED,
                            ),
                    ),
                ),
            stateFields =
                listOf(
                    NativeThemeComponentStateFieldV1(
                        id = labelFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = supportingTextFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                        required = false,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = selectedFieldId,
                        type = NativeThemeComponentValueTypeV1.BOOLEAN,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = enabledFieldId,
                        type = NativeThemeComponentValueTypeV1.BOOLEAN,
                    ),
                ),
            events = listOf(NativeThemeComponentEventV1(selectEventId)),
            slots = emptyList(),
            semantics =
                NativeThemeComponentSemanticsV1(
                    roles = setOf(NativeThemeComponentSemanticRoleV1.SINGLE_CHOICE_INPUT),
                    accessibleLabelField = labelFieldId,
                    selectedStateField = selectedFieldId,
                    enabledStateField = enabledFieldId,
                    accessibilityRoleBySemanticRole =
                        mapOf(
                            NativeThemeComponentSemanticRoleV1.SINGLE_CHOICE_INPUT to
                                NativeThemeComponentAccessibilityRoleV1.RADIO_BUTTON
                        ),
                    minimumTouchTargetDp = 48,
                ),
            catalogStates =
                setOf(
                    NativeThemeComponentCatalogStateV1.NORMAL,
                    NativeThemeComponentCatalogStateV1.SELECTED,
                    NativeThemeComponentCatalogStateV1.DISABLED,
                ),
        )

    val key =
        NativeThemeComponentKeyV1<
            NativeThemeChoiceItemStateV1,
            NativeThemeChoiceItemEventV1,
            NativeThemeChoiceItemSlotsV1,
        >(
            contract = contract,
            encodeState = { state ->
                buildMap {
                    put(labelFieldId, NativeThemeComponentStateValueV1.Text(state.label))
                    state.supportingText?.let { supportingText ->
                        put(
                            supportingTextFieldId,
                            NativeThemeComponentStateValueV1.Text(supportingText),
                        )
                    }
                    put(
                        selectedFieldId,
                        NativeThemeComponentStateValueV1.BooleanValue(state.selected),
                    )
                    put(enabledFieldId, NativeThemeComponentStateValueV1.BooleanValue(state.enabled))
                }
            },
        )
}

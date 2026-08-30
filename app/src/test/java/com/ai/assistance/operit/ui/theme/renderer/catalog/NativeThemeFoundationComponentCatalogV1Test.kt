package com.ai.assistance.operit.ui.theme.renderer.catalog

import com.ai.assistance.operit.ui.theme.NATIVE_THEME_V1_DEFINITION_ID
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonContractV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonEmphasisV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonEventV1
import com.ai.assistance.operit.ui.theme.renderer.action.dispatchNativeThemeActionButtonEventV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentAccessibilityRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCategoryV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentLiveRegionModeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentMemberId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotCardinalityV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentValueTypeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1
import com.ai.assistance.operit.ui.theme.renderer.contract.validateNativeThemeComponentContractsV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatContractV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusContractV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusKindV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemContractV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemEventV1
import com.ai.assistance.operit.ui.theme.renderer.input.dispatchNativeThemeChoiceItemEventV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeThemeFoundationComponentCatalogV1Test {
    @Test
    fun foundationContractsFreezeTheirIdsVersionsAndCategories() {
        val contracts =
            listOf(
                NativeThemeActionButtonContractV1.contract,
                NativeThemeChoiceItemContractV1.contract,
                NativeThemeSectionContractV1.contract,
                NativeThemeOperationStatusContractV1.contract,
                NativeThemeStatContractV1.contract,
            )

        assertEquals(
            listOf(
                "operit.action.button",
                "operit.input.choice_item",
                "operit.container.section",
                "operit.feedback.operation_status",
                "operit.data_display.stat",
            ),
            contracts.map { contract -> contract.id.value },
        )
        assertEquals(
            listOf(
                NativeThemeComponentCategoryV1.ACTION,
                NativeThemeComponentCategoryV1.INPUT,
                NativeThemeComponentCategoryV1.CONTAINER,
                NativeThemeComponentCategoryV1.FEEDBACK,
                NativeThemeComponentCategoryV1.DATA_DISPLAY,
            ),
            contracts.map { contract -> contract.category },
        )
        assertTrue(contracts.all { contract -> contract.required })
        assertTrue(
            contracts.filterNot { contract -> contract.id == NativeThemeStatContractV1.contract.id }.all { contract ->
                contract.version == NativeThemeComponentVersionV1(major = 1, minor = 0)
            }
        )
        assertEquals(
            NativeThemeComponentVersionV1(major = 1, minor = 1),
            NativeThemeStatContractV1.contract.version,
        )
        assertTrue(
            contracts.filterNot { contract -> contract.id == NativeThemeStatContractV1.contract.id }.all { contract ->
                contract.supportedHostSurfaces == setOf(NativeThemeHostSurface.MAIN)
            }
        )
        assertEquals(
            setOf(NativeThemeHostSurface.MAIN, NativeThemeHostSurface.EDITOR_PREVIEW),
            NativeThemeStatContractV1.contract.supportedHostSurfaces,
        )
    }

    @Test
    fun actionButtonContractDefinesEmphasisEventSlotAndInteractionSemantics() {
        val contract = NativeThemeActionButtonContractV1.contract
        val emphasisField =
            requireNotNull(
                contract.stateFields.single { field ->
                    field.id == NativeThemeActionButtonContractV1.emphasisFieldId
                }
            )

        assertEquals(listOf("label", "enabled", "emphasis"), contract.stateFields.ids())
        assertEquals(NativeThemeComponentValueTypeV1.ENUM, emphasisField.type)
        assertEquals(
            listOf("standard", "caution", "destructive"),
            emphasisField.enumValues.map { value -> value.value },
        )
        assertEquals(listOf("activate"), contract.events.map { event -> event.id.value })
        assertEquals(listOf("leading"), contract.slots.map { slot -> slot.id.value })
        assertEquals(
            NativeThemeComponentSlotCardinalityV1.REQUIRED_SINGLE,
            contract.slots.single().cardinality,
        )
        assertEquals(setOf(NativeThemeComponentSemanticRoleV1.ACTION), contract.semantics.roles)
        assertEquals(
            mapOf(
                NativeThemeComponentSemanticRoleV1.ACTION to
                    NativeThemeComponentAccessibilityRoleV1.BUTTON
            ),
            contract.semantics.accessibilityRoleBySemanticRole,
        )
        assertEquals(NativeThemeActionButtonContractV1.labelFieldId, contract.semantics.accessibleLabelField)
        assertEquals(NativeThemeActionButtonContractV1.enabledFieldId, contract.semantics.enabledStateField)
        assertEquals(48, contract.semantics.minimumTouchTargetDp)
    }

    @Test
    fun choiceSectionStatusAndStatContractsExposeTheirSemanticFields() {
        val choice = NativeThemeChoiceItemContractV1.contract
        val supportingText =
            choice.stateFields.single { field ->
                field.id == NativeThemeChoiceItemContractV1.supportingTextFieldId
            }
        val section = NativeThemeSectionContractV1.contract
        val status = NativeThemeOperationStatusContractV1.contract
        val statusKind =
            status.stateFields.single { field ->
                field.id == NativeThemeOperationStatusContractV1.kindFieldId
            }
        val stat = NativeThemeStatContractV1.contract

        assertEquals(
            listOf("label", "supporting_text", "selected", "enabled"),
            choice.stateFields.ids(),
        )
        assertFalse(supportingText.required)
        assertEquals(listOf("select"), choice.events.map { event -> event.id.value })
        assertTrue(choice.slots.isEmpty())
        assertEquals(NativeThemeChoiceItemContractV1.selectedFieldId, choice.semantics.selectedStateField)
        assertEquals(NativeThemeChoiceItemContractV1.enabledFieldId, choice.semantics.enabledStateField)

        assertEquals(listOf("title", "description"), section.stateFields.ids())
        assertEquals(listOf("leading", "content"), section.slots.map { slot -> slot.id.value })
        assertEquals(NativeThemeSectionContractV1.titleFieldId, section.semantics.headingField)

        assertEquals(listOf("title", "message", "kind"), status.stateFields.ids())
        assertFalse(status.stateFields.first().required)
        assertEquals(
            NativeThemeComponentSlotCardinalityV1.OPTIONAL_SINGLE,
            status.slots.single().cardinality,
        )
        assertEquals(
            NativeThemeOperationStatusContractV1.messageFieldId,
            status.semantics.statusMessageField,
        )
        assertEquals(
            listOf("loading", "success", "error"),
            statusKind.enumValues.map { value -> value.value },
        )
        assertEquals(NativeThemeComponentLiveRegionModeV1.POLITE, status.semantics.liveRegionMode)
        assertEquals(
            setOf(NativeThemeComponentCatalogStateV1.LOADING),
            status.semantics.indeterminateProgressStates,
        )
        assertEquals(
            setOf(NativeThemeOperationStatusContractV1.leadingSlotId),
            status.semantics.decorativeSlotIds,
        )
        assertEquals(
            mapOf(
                NativeThemeComponentCatalogStateV1.NORMAL to
                    NativeThemeOperationStatusContractV1.successKindId,
                NativeThemeComponentCatalogStateV1.LOADING to
                    NativeThemeOperationStatusContractV1.loadingKindId,
                NativeThemeComponentCatalogStateV1.ERROR to
                    NativeThemeOperationStatusContractV1.errorKindId,
            ),
            requireNotNull(status.catalogStateMapping).enumValueByState,
        )

        assertEquals(listOf("label", "value"), stat.stateFields.ids())
        assertEquals(NativeThemeStatContractV1.labelFieldId, stat.semantics.accessibleLabelField)
        assertEquals(NativeThemeStatContractV1.valueFieldId, stat.semantics.displayValueField)
    }

    @Test
    fun nativeV1CatalogRegistersEveryFoundationTypedKey() {
        assertSame(
            NativeThemeComponentCatalogV1.actionButton,
            NativeThemeComponentCatalogV1.requireImplementation(NativeThemeActionButtonContractV1.key),
        )
        assertSame(
            NativeThemeComponentCatalogV1.choiceItem,
            NativeThemeComponentCatalogV1.requireImplementation(NativeThemeChoiceItemContractV1.key),
        )
        assertSame(
            NativeThemeComponentCatalogV1.section,
            NativeThemeComponentCatalogV1.requireImplementation(NativeThemeSectionContractV1.key),
        )
        assertSame(
            NativeThemeComponentCatalogV1.operationStatus,
            NativeThemeComponentCatalogV1.requireImplementation(
                NativeThemeOperationStatusContractV1.key
            ),
        )
        assertSame(
            NativeThemeComponentCatalogV1.stat,
            NativeThemeComponentCatalogV1.requireImplementation(NativeThemeStatContractV1.key),
        )
    }

    @Test
    fun foundationCatalogScenariosCoverDeclaredStatesAndVariants() {
        assertEquals(
            setOf(NativeThemeComponentCatalogStateV1.NORMAL, NativeThemeComponentCatalogStateV1.DISABLED),
            NativeThemeComponentCatalogV1.actionButton.scenarios.catalogStates(),
        )
        assertEquals(
            setOf(
                NativeThemeActionButtonEmphasisV1.STANDARD,
                NativeThemeActionButtonEmphasisV1.CAUTION,
                NativeThemeActionButtonEmphasisV1.DESTRUCTIVE,
            ),
            NativeThemeComponentCatalogV1.actionButton.scenarios.map { scenario ->
                scenario.state.emphasis
            }.toSet(),
        )
        assertEquals(
            setOf(
                NativeThemeComponentCatalogStateV1.NORMAL,
                NativeThemeComponentCatalogStateV1.SELECTED,
                NativeThemeComponentCatalogStateV1.DISABLED,
            ),
            NativeThemeComponentCatalogV1.choiceItem.scenarios.catalogStates(),
        )
        assertEquals(
            setOf(
                NativeThemeComponentCatalogStateV1.NORMAL,
                NativeThemeComponentCatalogStateV1.LOADING,
                NativeThemeComponentCatalogStateV1.ERROR,
            ),
            NativeThemeComponentCatalogV1.operationStatus.scenarios.catalogStates(),
        )
        assertEquals(
            setOf(
                NativeThemeOperationStatusKindV1.LOADING,
                NativeThemeOperationStatusKindV1.SUCCESS,
                NativeThemeOperationStatusKindV1.ERROR,
            ),
            NativeThemeComponentCatalogV1.operationStatus.scenarios.map { scenario ->
                scenario.state.kind
            }.toSet(),
        )
    }

    @Test
    fun foundationEventsInvokeOnlyTheirHostActions() {
        var activations = 0
        var selections = 0

        dispatchNativeThemeActionButtonEventV1(
            event = NativeThemeActionButtonEventV1.Activate,
            enabled = true,
            onActivate = { activations += 1 },
        )
        dispatchNativeThemeChoiceItemEventV1(
            event = NativeThemeChoiceItemEventV1.Select,
            enabled = true,
            onSelect = { selections += 1 },
        )
        dispatchNativeThemeActionButtonEventV1(
            event = NativeThemeActionButtonEventV1.Activate,
            enabled = false,
            onActivate = { activations += 1 },
        )
        dispatchNativeThemeChoiceItemEventV1(
            event = NativeThemeChoiceItemEventV1.Select,
            enabled = false,
            onSelect = { selections += 1 },
        )

        assertEquals(1, activations)
        assertEquals(1, selections)
    }

    @Test
    fun catalogRejectsStateThatContradictsItsDisabledScenario() {
        val implementation = NativeThemeComponentCatalogV1.actionButton
        val invalid =
            implementation.copy(
                scenarios =
                    implementation.scenarios.map { scenario ->
                        if (NativeThemeComponentCatalogStateV1.DISABLED in scenario.catalogStates) {
                            scenario.copy(state = scenario.state.copy(enabled = true))
                        } else {
                            scenario
                        }
                    }
            )

        assertCatalogFailure(
            expectedMessage = "enabled state does not match its catalog states",
            replacement = invalid,
        )
    }

    @Test
    fun catalogRejectsStatusKindThatContradictsItsScenario() {
        val implementation = NativeThemeComponentCatalogV1.operationStatus
        val invalid =
            implementation.copy(
                scenarios =
                    implementation.scenarios.map { scenario ->
                        if (NativeThemeComponentCatalogStateV1.LOADING in scenario.catalogStates) {
                            scenario.copy(
                                state =
                                    scenario.state.copy(
                                        title = "Unexpected success",
                                        kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                    )
                            )
                        } else {
                            scenario
                        }
                    }
            )

        assertCatalogFailure(
            expectedMessage = "enum value does not match its catalog state",
            replacement = invalid,
        )
    }

    @Test
    fun catalogRequiresPresentAndAbsentOptionalFieldScenarios() {
        val implementation = NativeThemeComponentCatalogV1.choiceItem
        val invalid =
            implementation.copy(
                scenarios = implementation.scenarios.filter { scenario ->
                    scenario.state.supportingText != null
                }
            )

        assertCatalogFailure(
            expectedMessage = "must omit optional field supporting_text",
            replacement = invalid,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun contractValidationRejectsEmptyEnumDomains() {
        val contract = NativeThemeActionButtonContractV1.contract
        val fields =
            contract.stateFields.map { field ->
                if (field.id == NativeThemeActionButtonContractV1.emphasisFieldId) {
                    field.copy(enumValues = emptyList())
                } else {
                    field
                }
            }

        validateNativeThemeComponentContractsV1(listOf(contract.copy(stateFields = fields)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun contractValidationRejectsDuplicateEnumValues() {
        val contract = NativeThemeActionButtonContractV1.contract
        val duplicate = NativeThemeActionButtonContractV1.standardEmphasisId
        val fields =
            contract.stateFields.map { field ->
                if (field.id == NativeThemeActionButtonContractV1.emphasisFieldId) {
                    field.copy(enumValues = listOf(duplicate, duplicate))
                } else {
                    field
                }
            }

        validateNativeThemeComponentContractsV1(listOf(contract.copy(stateFields = fields)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun contractValidationRejectsEnumValuesOnTextFields() {
        val contract = NativeThemeActionButtonContractV1.contract
        val fields =
            contract.stateFields.map { field ->
                if (field.id == NativeThemeActionButtonContractV1.labelFieldId) {
                    field.copy(enumValues = listOf(NativeThemeComponentMemberId("invalid")))
                } else {
                    field
                }
            }

        validateNativeThemeComponentContractsV1(listOf(contract.copy(stateFields = fields)))
    }

    private fun List<com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateFieldV1>.ids() =
        map { field -> field.id.value }

    private fun List<NativeThemeComponentCatalogScenarioV1<*>>.catalogStates() =
        flatMap { scenario -> scenario.catalogStates }.toSet()

    private fun assertCatalogFailure(
        expectedMessage: String,
        replacement: NativeThemeComponentImplementationV1<*, *, *>,
    ) {
        val implementations =
            NativeThemeComponentCatalogV1.implementations.map { implementation ->
                if (implementation.contract.id == replacement.contract.id) {
                    replacement
                } else {
                    implementation
                }
            }
        try {
            validateNativeThemeComponentCatalogV1(
                definitionId = NATIVE_THEME_V1_DEFINITION_ID,
                contractKeys = NativeThemeComponentContractsV1.keys,
                implementations = implementations,
            )
            fail("Expected catalog validation to fail with: $expectedMessage")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(expectedMessage))
        }
    }
}

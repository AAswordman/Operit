package com.ai.assistance.operit.ui.theme.renderer.catalog

import com.ai.assistance.operit.ui.theme.NATIVE_THEME_V1_DEFINITION_ID
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCategoryV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentKeyV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentMemberId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotCardinalityV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentValueTypeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1
import com.ai.assistance.operit.ui.theme.renderer.contract.validateNativeThemeComponentContractsV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemContractV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemEventV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemSlotsV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemStateV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.dispatchNativeThemeNavigationDrawerItemEventV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeThemeComponentCatalogV1Test {
    @Test
    fun navigationDrawerItemContractFreezesItsHostBoundary() {
        val contract = NativeThemeNavigationDrawerItemContractV1.contract

        assertEquals("operit.navigation.drawer_item", contract.id.value)
        assertEquals(NativeThemeComponentVersionV1(major = 1, minor = 0), contract.version)
        assertEquals(NativeThemeComponentCategoryV1.NAVIGATION, contract.category)
        assertTrue(contract.required)
        assertEquals(setOf(NativeThemeHostSurface.MAIN), contract.supportedHostSurfaces)
        assertEquals(
            listOf("label", "selected", "enabled", "semantic_role"),
            contract.stateFields.map { field -> field.id.value },
        )
        assertEquals(
            listOf(
                NativeThemeComponentValueTypeV1.TEXT,
                NativeThemeComponentValueTypeV1.BOOLEAN,
                NativeThemeComponentValueTypeV1.BOOLEAN,
                NativeThemeComponentValueTypeV1.SEMANTIC_ROLE,
            ),
            contract.stateFields.map { field -> field.type },
        )
        assertEquals(listOf("activate"), contract.events.map { event -> event.id.value })
        assertEquals(listOf("leading"), contract.slots.map { slot -> slot.id.value })
        assertEquals(
            NativeThemeComponentSlotCardinalityV1.REQUIRED_SINGLE,
            contract.slots.single().cardinality,
        )
        assertEquals(
            setOf(
                NativeThemeComponentSemanticRoleV1.NAVIGATION_DESTINATION,
                NativeThemeComponentSemanticRoleV1.ACTION,
            ),
            contract.semantics.roles,
        )
        assertEquals(
            NativeThemeNavigationDrawerItemContractV1.semanticRoleFieldId,
            contract.semantics.roleStateField,
        )
        assertEquals(
            NativeThemeNavigationDrawerItemContractV1.labelFieldId,
            contract.semantics.accessibleLabelField,
        )
        assertEquals(
            NativeThemeNavigationDrawerItemContractV1.selectedFieldId,
            contract.semantics.selectedStateField,
        )
        assertEquals(
            NativeThemeNavigationDrawerItemContractV1.enabledFieldId,
            contract.semantics.enabledStateField,
        )
        assertEquals(48, contract.semantics.minimumTouchTargetDp)
    }

    @Test
    fun nativeV1CatalogCoversEveryRequiredContractAtItsExactVersion() {
        val requiredContracts = NativeThemeComponentContractsV1.all.filter { contract -> contract.required }
        val contractsById = NativeThemeComponentContractsV1.all.associateBy { contract -> contract.id }

        assertEquals(NATIVE_THEME_V1_DEFINITION_ID, NativeThemeComponentCatalogV1.definitionId)
        assertTrue(
            NativeThemeComponentCatalogV1.implementationsById.keys.containsAll(
                requiredContracts.map { contract -> contract.id }
            )
        )
        assertTrue(
            contractsById.keys.containsAll(NativeThemeComponentCatalogV1.implementationsById.keys)
        )
        NativeThemeComponentCatalogV1.implementations.forEach { implementation ->
            val contract = requireNotNull(contractsById[implementation.contract.id])
            assertEquals(contract, implementation.contract)
            assertEquals(contract.version, implementation.implementedVersion)
        }
        assertSame(
            NativeThemeComponentCatalogV1.navigationDrawerItem,
            NativeThemeComponentCatalogV1.requireImplementation(
                NativeThemeNavigationDrawerItemContractV1.key
            ),
        )
    }

    @Test
    fun navigationDrawerCatalogCoversNormalSelectedAndDisabledStates() {
        val scenarios = NativeThemeComponentCatalogV1.navigationDrawerItem.scenarios
        val scenariosById = scenarios.associateBy { scenario -> scenario.id.value }

        assertEquals(
            setOf(
                NativeThemeComponentCatalogStateV1.NORMAL,
                NativeThemeComponentCatalogStateV1.SELECTED,
                NativeThemeComponentCatalogStateV1.DISABLED,
            ),
            scenarios.flatMap { scenario -> scenario.catalogStates }.toSet(),
        )
        assertEquals(setOf("normal", "selected", "disabled", "action"), scenariosById.keys)
        assertFalse(requireNotNull(scenariosById["normal"]).state.selected)
        assertTrue(requireNotNull(scenariosById["normal"]).state.enabled)
        assertTrue(requireNotNull(scenariosById["selected"]).state.selected)
        assertFalse(requireNotNull(scenariosById["disabled"]).state.enabled)
        assertEquals(
            NativeThemeNavigationDrawerItemSemanticRoleV1.ACTION,
            requireNotNull(scenariosById["action"]).state.semanticRole,
        )
    }

    @Test
    fun catalogRejectsMissingRequiredImplementations() {
        assertCatalogFailure("missing required components", implementations = emptyList())
    }

    @Test
    fun catalogRejectsIncompatibleImplementationVersions() {
        val incompatible =
            NativeThemeComponentCatalogV1.navigationDrawerItem.copy(
                implementedVersion = NativeThemeComponentVersionV1(major = 1, minor = 1)
            )

        assertCatalogFailure(
            expectedMessage = "incompatible version",
            implementations = replaceNavigationImplementation(incompatible),
        )
    }

    @Test
    fun catalogRejectsIncompleteCatalogStateCoverage() {
        val incomplete =
            NativeThemeComponentCatalogV1.navigationDrawerItem.copy(
                scenarios =
                    NativeThemeComponentCatalogV1.navigationDrawerItem.scenarios.filterNot { scenario ->
                        NativeThemeComponentCatalogStateV1.SELECTED in scenario.catalogStates
                    }
            )

        assertCatalogFailure(
            expectedMessage = "cover its catalog states",
            implementations = replaceNavigationImplementation(incomplete),
        )
    }

    @Test
    fun catalogRejectsIncompleteSemanticRoleCoverage() {
        val incomplete =
            NativeThemeComponentCatalogV1.navigationDrawerItem.copy(
                scenarios =
                    NativeThemeComponentCatalogV1.navigationDrawerItem.scenarios.filterNot { scenario ->
                        scenario.state.semanticRole ==
                            NativeThemeNavigationDrawerItemSemanticRoleV1.ACTION
                    }
            )

        assertCatalogFailure(
            expectedMessage = "cover its semantic roles",
            implementations = replaceNavigationImplementation(incomplete),
        )
    }

    @Test
    fun catalogRejectsDuplicateScenarioIds() {
        val scenarios = NativeThemeComponentCatalogV1.navigationDrawerItem.scenarios
        val invalid =
            NativeThemeComponentCatalogV1.navigationDrawerItem.copy(
                scenarios = scenarios + scenarios.first()
            )

        assertCatalogFailure(
            expectedMessage = "scenario IDs must be unique",
            implementations = replaceNavigationImplementation(invalid),
        )
    }

    @Test
    fun catalogRejectsDuplicateImplementationIds() {
        assertCatalogFailure(
            expectedMessage = "implementation IDs must be unique",
            implementations =
                NativeThemeComponentCatalogV1.implementations +
                    NativeThemeComponentCatalogV1.navigationDrawerItem,
        )
    }

    @Test
    fun catalogRejectsUnregisteredTypedKeys() {
        val unregisteredKey =
            NativeThemeComponentKeyV1<
                NativeThemeNavigationDrawerItemStateV1,
                NativeThemeNavigationDrawerItemEventV1,
                NativeThemeNavigationDrawerItemSlotsV1,
            >(
                contract = NativeThemeNavigationDrawerItemContractV1.contract,
                encodeState = NativeThemeNavigationDrawerItemContractV1.key.encodeState,
            )
        val invalid =
            NativeThemeComponentCatalogV1.navigationDrawerItem.copy(key = unregisteredKey)

        assertCatalogFailure(
            expectedMessage = "unregistered typed key",
            implementations = replaceNavigationImplementation(invalid),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun contractValidationRejectsUnknownSemanticStateFields() {
        val contract = NativeThemeNavigationDrawerItemContractV1.contract
        val invalid =
            contract.copy(
                semantics =
                    contract.semantics.copy(
                        accessibleLabelField = NativeThemeComponentMemberId("missing_label")
                    )
            )

        validateNativeThemeComponentContractsV1(listOf(invalid))
    }

    @Test
    fun activateEventInvokesOnlyTheHostAction() {
        var activations = 0

        dispatchNativeThemeNavigationDrawerItemEventV1(
            event = NativeThemeNavigationDrawerItemEventV1.Activate,
            enabled = true,
            onActivate = { activations += 1 },
        )
        dispatchNativeThemeNavigationDrawerItemEventV1(
            event = NativeThemeNavigationDrawerItemEventV1.Activate,
            enabled = false,
            onActivate = { activations += 1 },
        )

        assertEquals(1, activations)
    }

    @Test
    fun catalogScenarioStateRemainsAHostOwnedValue() {
        val state =
            NativeThemeNavigationDrawerItemStateV1(
                label = "Chat",
                selected = true,
                enabled = true,
                semanticRole =
                    NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION,
            )
        val changed = state.copy(selected = false)

        assertTrue(state.selected)
        assertFalse(changed.selected)
    }

    private fun replaceNavigationImplementation(
        replacement: NativeThemeComponentImplementationV1<
            NativeThemeNavigationDrawerItemStateV1,
            NativeThemeNavigationDrawerItemEventV1,
            NativeThemeNavigationDrawerItemSlotsV1,
        >,
    ): List<NativeThemeComponentImplementationV1<*, *, *>> =
        NativeThemeComponentCatalogV1.implementations.map { implementation ->
            if (implementation === NativeThemeComponentCatalogV1.navigationDrawerItem) {
                replacement
            } else {
                implementation
            }
        }

    private fun assertCatalogFailure(
        expectedMessage: String,
        implementations: List<NativeThemeComponentImplementationV1<*, *, *>>,
    ) {
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

package com.ai.assistance.operit.ui.theme.renderer.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.theme.NATIVE_THEME_V1_DEFINITION_ID
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentKeyV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentScenarioId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1
import com.ai.assistance.operit.ui.theme.renderer.contract.validateNativeThemeComponentContractsV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemContractV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemEventV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemRendererV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemSlotsV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.NativeThemeNavigationDrawerItemStateV1
import com.ai.assistance.operit.ui.theme.renderer.navigation.toComponentSemanticRoleV1

internal interface NativeThemeComponentRendererV1<State : Any, Event : Any, Slots : Any> {
    @Composable
    fun render(
        state: State,
        slots: Slots,
        onEvent: (Event) -> Unit,
        modifier: Modifier = Modifier,
    )
}

internal data class NativeThemeComponentCatalogScenarioV1<State : Any>(
    val id: NativeThemeComponentScenarioId,
    val catalogState: NativeThemeComponentCatalogStateV1,
    val state: State,
)

internal data class NativeThemeComponentImplementationV1<
    State : Any,
    Event : Any,
    Slots : Any,
>(
    val key: NativeThemeComponentKeyV1<State, Event, Slots>,
    val implementedVersion: NativeThemeComponentVersionV1,
    val renderer: NativeThemeComponentRendererV1<State, Event, Slots>,
    val scenarios: List<NativeThemeComponentCatalogScenarioV1<State>>,
    val semanticRoleOf: (State) -> NativeThemeComponentSemanticRoleV1,
) {
    val contract: NativeThemeComponentContractV1
        get() = key.contract

    fun catalogSemanticRoles(): Set<NativeThemeComponentSemanticRoleV1> =
        scenarios.map { scenario -> semanticRoleOf(scenario.state) }.toSet()
}

internal object NativeThemeComponentContractsV1 {
    val keys: List<NativeThemeComponentKeyV1<*, *, *>> =
        listOf(NativeThemeNavigationDrawerItemContractV1.key)
    val all = keys.map { key -> key.contract }
}

internal object NativeThemeComponentCatalogV1 {
    val definitionId = NATIVE_THEME_V1_DEFINITION_ID

    val navigationDrawerItem =
        NativeThemeComponentImplementationV1<
            NativeThemeNavigationDrawerItemStateV1,
            NativeThemeNavigationDrawerItemEventV1,
            NativeThemeNavigationDrawerItemSlotsV1,
        >(
            key = NativeThemeNavigationDrawerItemContractV1.key,
            implementedVersion = NativeThemeNavigationDrawerItemContractV1.contract.version,
            renderer = NativeThemeNavigationDrawerItemRendererV1,
            semanticRoleOf = { state -> state.semanticRole.toComponentSemanticRoleV1() },
            scenarios =
                listOf(
                    NativeThemeComponentCatalogScenarioV1(
                        id = NativeThemeComponentScenarioId("normal"),
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        state =
                            NativeThemeNavigationDrawerItemStateV1(
                                label = "Assistant",
                                selected = false,
                                enabled = true,
                                semanticRole =
                                    NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION,
                            ),
                    ),
                    NativeThemeComponentCatalogScenarioV1(
                        id = NativeThemeComponentScenarioId("selected"),
                        catalogState = NativeThemeComponentCatalogStateV1.SELECTED,
                        state =
                            NativeThemeNavigationDrawerItemStateV1(
                                label = "Chat",
                                selected = true,
                                enabled = true,
                                semanticRole =
                                    NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION,
                            ),
                    ),
                    NativeThemeComponentCatalogScenarioV1(
                        id = NativeThemeComponentScenarioId("disabled"),
                        catalogState = NativeThemeComponentCatalogStateV1.DISABLED,
                        state =
                            NativeThemeNavigationDrawerItemStateV1(
                                label = "Unavailable",
                                selected = false,
                                enabled = false,
                                semanticRole =
                                    NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION,
                            ),
                    ),
                    NativeThemeComponentCatalogScenarioV1(
                        id = NativeThemeComponentScenarioId("action"),
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        state =
                            NativeThemeNavigationDrawerItemStateV1(
                                label = "Run action",
                                selected = false,
                                enabled = true,
                                semanticRole = NativeThemeNavigationDrawerItemSemanticRoleV1.ACTION,
                            ),
                    ),
                ),
        )

    val implementations: List<NativeThemeComponentImplementationV1<*, *, *>> =
        listOf(navigationDrawerItem)

    val implementationsById: Map<NativeThemeComponentId, NativeThemeComponentImplementationV1<*, *, *>> =
        implementations.associateBy { implementation -> implementation.contract.id }

    init {
        validateNativeThemeComponentCatalogV1(
            definitionId = definitionId,
            contractKeys = NativeThemeComponentContractsV1.keys,
            implementations = implementations,
        )
    }

    fun requireImplementationMetadata(
        componentId: NativeThemeComponentId,
    ): NativeThemeComponentImplementationV1<*, *, *> =
        checkNotNull(implementationsById[componentId]) {
            "Theme $definitionId does not implement component ${componentId.value}."
        }

    fun <State : Any, Event : Any, Slots : Any> requireImplementation(
        key: NativeThemeComponentKeyV1<State, Event, Slots>,
    ): NativeThemeComponentImplementationV1<State, Event, Slots> {
        val implementation = requireImplementationMetadata(key.contract.id)
        check(implementation.key === key) {
            "Theme $definitionId component ${key.contract.id.value} is registered with another typed key."
        }
        @Suppress("UNCHECKED_CAST")
        return implementation as NativeThemeComponentImplementationV1<State, Event, Slots>
    }
}

internal fun validateNativeThemeComponentCatalogV1(
    definitionId: String,
    contractKeys: List<NativeThemeComponentKeyV1<*, *, *>>,
    implementations: List<NativeThemeComponentImplementationV1<*, *, *>>,
) {
    require(definitionId.isNotBlank()) { "A component catalog must identify its theme definition." }
    val contracts = contractKeys.map { key -> key.contract }
    validateNativeThemeComponentContractsV1(contracts)

    require(
        implementations.map { implementation -> implementation.contract.id }.distinct().size ==
            implementations.size
    ) {
        "Theme $definitionId component implementation IDs must be unique."
    }

    val contractKeysById = contractKeys.associateBy { key -> key.contract.id }
    implementations.forEach { implementation ->
        val registeredKey = requireNotNull(contractKeysById[implementation.contract.id]) {
            "Theme $definitionId implements unknown component ${implementation.contract.id.value}."
        }
        require(implementation.key === registeredKey) {
            "Theme $definitionId component ${implementation.contract.id.value} uses an unregistered typed key."
        }
        val registeredContract = registeredKey.contract
        require(implementation.implementedVersion == registeredContract.version) {
            "Theme $definitionId component ${implementation.contract.id.value} has an incompatible version."
        }
        require(
            implementation.scenarios.map { scenario -> scenario.id }.distinct().size ==
                implementation.scenarios.size
        ) {
            "Theme $definitionId component ${implementation.contract.id.value} scenario IDs must be unique."
        }
        require(
            implementation.scenarios.map { scenario -> scenario.catalogState }.toSet() ==
                registeredContract.catalogStates
        ) {
            "Theme $definitionId component ${implementation.contract.id.value} must cover its catalog states."
        }
        require(
            implementation.catalogSemanticRoles() == registeredContract.semantics.roles
        ) {
            "Theme $definitionId component ${implementation.contract.id.value} must cover its semantic roles."
        }
    }

    val requiredContractIds =
        contracts.filter { contract -> contract.required }.map { contract -> contract.id }.toSet()
    val implementedIds = implementations.map { implementation -> implementation.contract.id }.toSet()
    require(implementedIds.containsAll(requiredContractIds)) {
        val missing = (requiredContractIds - implementedIds).joinToString { id -> id.value }
        "Theme $definitionId is missing required components: $missing"
    }
}

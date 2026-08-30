package com.ai.assistance.operit.ui.theme.renderer.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.theme.NATIVE_THEME_V1_DEFINITION_ID
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonContractV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentKeyV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentMemberId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentScenarioId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateValueV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentValueTypeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1
import com.ai.assistance.operit.ui.theme.renderer.contract.validateNativeThemeComponentContractsV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatContractV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusContractV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemContractV1
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
    val catalogStates: Set<NativeThemeComponentCatalogStateV1>,
    val state: State,
)

internal data class NativeThemeComponentEncodedCatalogScenarioV1(
    val id: NativeThemeComponentScenarioId,
    val catalogStates: Set<NativeThemeComponentCatalogStateV1>,
    val stateValues: Map<NativeThemeComponentMemberId, NativeThemeComponentStateValueV1>,
    val semanticRole: NativeThemeComponentSemanticRoleV1,
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

    fun encodedScenarios(): List<NativeThemeComponentEncodedCatalogScenarioV1> =
        scenarios.map { scenario ->
            NativeThemeComponentEncodedCatalogScenarioV1(
                id = scenario.id,
                catalogStates = scenario.catalogStates,
                stateValues = key.encodeState(scenario.state),
                semanticRole = semanticRoleOf(scenario.state),
            )
        }
}

internal object NativeThemeComponentContractsV1 {
    val keys: List<NativeThemeComponentKeyV1<*, *, *>> =
        listOf(
            NativeThemeNavigationDrawerItemContractV1.key,
            NativeThemeActionButtonContractV1.key,
            NativeThemeChoiceItemContractV1.key,
            NativeThemeSectionContractV1.key,
            NativeThemeOperationStatusContractV1.key,
            NativeThemeStatContractV1.key,
        )
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
            implementedVersion = NativeThemeComponentVersionV1(major = 1, minor = 0),
            renderer = NativeThemeNavigationDrawerItemRendererV1,
            semanticRoleOf = { state -> state.semanticRole.toComponentSemanticRoleV1() },
            scenarios =
                listOf(
                    NativeThemeComponentCatalogScenarioV1(
                        id = NativeThemeComponentScenarioId("normal"),
                        catalogStates = setOf(NativeThemeComponentCatalogStateV1.NORMAL),
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
                        catalogStates = setOf(NativeThemeComponentCatalogStateV1.SELECTED),
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
                        catalogStates = setOf(NativeThemeComponentCatalogStateV1.DISABLED),
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
                        catalogStates = setOf(NativeThemeComponentCatalogStateV1.NORMAL),
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

    val actionButton = NativeThemeFoundationComponentImplementationsV1.actionButton
    val choiceItem = NativeThemeFoundationComponentImplementationsV1.choiceItem
    val section = NativeThemeFoundationComponentImplementationsV1.section
    val operationStatus = NativeThemeFoundationComponentImplementationsV1.operationStatus
    val stat = NativeThemeFoundationComponentImplementationsV1.stat

    val implementations: List<NativeThemeComponentImplementationV1<*, *, *>> =
        listOf(
            navigationDrawerItem,
            actionButton,
            choiceItem,
            section,
            operationStatus,
            stat,
        )

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
            implementation.scenarios.flatMap { scenario -> scenario.catalogStates }.toSet() ==
                registeredContract.catalogStates
        ) {
            "Theme $definitionId component ${implementation.contract.id.value} must cover its catalog states."
        }
        require(
            implementation.catalogSemanticRoles() == registeredContract.semantics.roles
        ) {
            "Theme $definitionId component ${implementation.contract.id.value} must cover its semantic roles."
        }
        validateNativeThemeComponentCatalogScenariosV1(
            definitionId = definitionId,
            contract = registeredContract,
            scenarios = implementation.encodedScenarios(),
        )
    }

    val requiredContractIds =
        contracts.filter { contract -> contract.required }.map { contract -> contract.id }.toSet()
    val implementedIds = implementations.map { implementation -> implementation.contract.id }.toSet()
    require(implementedIds.containsAll(requiredContractIds)) {
        val missing = (requiredContractIds - implementedIds).joinToString { id -> id.value }
        "Theme $definitionId is missing required components: $missing"
    }
}

private fun validateNativeThemeComponentCatalogScenariosV1(
    definitionId: String,
    contract: NativeThemeComponentContractV1,
    scenarios: List<NativeThemeComponentEncodedCatalogScenarioV1>,
) {
    val fieldsById = contract.stateFields.associateBy { field -> field.id }
    val requiredFieldIds =
        contract.stateFields.filter { field -> field.required }.map { field -> field.id }.toSet()
    val componentLabel = "Theme $definitionId component ${contract.id.value}"

    scenarios.forEach { scenario ->
        require(scenario.catalogStates.isNotEmpty()) {
            "$componentLabel scenario ${scenario.id.value} must define at least one catalog state."
        }
        require(scenario.catalogStates.all { state -> state in contract.catalogStates }) {
            "$componentLabel scenario ${scenario.id.value} uses an undeclared catalog state."
        }
        if (NativeThemeComponentCatalogStateV1.NORMAL in scenario.catalogStates) {
            require(scenario.catalogStates.size == 1) {
                "$componentLabel scenario ${scenario.id.value} cannot combine NORMAL with another state."
            }
        }
        require(scenario.stateValues.keys.containsAll(requiredFieldIds)) {
            "$componentLabel scenario ${scenario.id.value} is missing required state fields."
        }
        require(scenario.stateValues.keys.all { fieldId -> fieldId in fieldsById }) {
            "$componentLabel scenario ${scenario.id.value} contains unknown state fields."
        }
        scenario.stateValues.forEach { (fieldId, value) ->
            val field = requireNotNull(fieldsById[fieldId])
            require(value.matches(field.type)) {
                "$componentLabel scenario ${scenario.id.value} field ${fieldId.value} has the wrong type."
            }
            if (value is NativeThemeComponentStateValueV1.EnumValue) {
                require(value.value in field.enumValues) {
                    "$componentLabel scenario ${scenario.id.value} field ${fieldId.value} uses an unknown enum value."
                }
            }
        }

        contract.semantics.roleStateField?.let { fieldId ->
            val encodedRole =
                (scenario.stateValues[fieldId] as NativeThemeComponentStateValueV1.SemanticRoleValue).value
            require(encodedRole == scenario.semanticRole) {
                "$componentLabel scenario ${scenario.id.value} semantic role does not match its state."
            }
        }
        require(scenario.semanticRole in contract.semantics.roles) {
            "$componentLabel scenario ${scenario.id.value} uses an undeclared semantic role."
        }
        contract.semantics.selectedStateField?.let { fieldId ->
            val selected =
                (scenario.stateValues[fieldId] as NativeThemeComponentStateValueV1.BooleanValue).value
            require(selected == (NativeThemeComponentCatalogStateV1.SELECTED in scenario.catalogStates)) {
                "$componentLabel scenario ${scenario.id.value} selected state does not match its catalog states."
            }
        }
        contract.semantics.enabledStateField?.let { fieldId ->
            val enabled =
                (scenario.stateValues[fieldId] as NativeThemeComponentStateValueV1.BooleanValue).value
            require(enabled == (NativeThemeComponentCatalogStateV1.DISABLED !in scenario.catalogStates)) {
                "$componentLabel scenario ${scenario.id.value} enabled state does not match its catalog states."
            }
        }
        contract.catalogStateMapping?.let { mapping ->
            require(scenario.catalogStates.size == 1) {
                "$componentLabel scenario ${scenario.id.value} must use one mapped catalog state."
            }
            val catalogState = scenario.catalogStates.single()
            val encodedValue =
                (scenario.stateValues[mapping.fieldId] as NativeThemeComponentStateValueV1.EnumValue).value
            require(encodedValue == mapping.enumValueByState[catalogState]) {
                "$componentLabel scenario ${scenario.id.value} enum value does not match its catalog state."
            }
        }
    }

    contract.stateFields
        .filter { field -> field.type == NativeThemeComponentValueTypeV1.ENUM }
        .forEach { field ->
        val coveredValues =
            scenarios.mapNotNull { scenario ->
                (scenario.stateValues[field.id] as? NativeThemeComponentStateValueV1.EnumValue)?.value
            }.toSet()
        require(coveredValues == field.enumValues.toSet()) {
            "$componentLabel catalog scenarios must cover enum field ${field.id.value}."
        }
    }
    contract.stateFields.filterNot { field -> field.required }.forEach { field ->
        require(scenarios.any { scenario -> field.id in scenario.stateValues }) {
            "$componentLabel catalog scenarios must cover optional field ${field.id.value}."
        }
        require(scenarios.any { scenario -> field.id !in scenario.stateValues }) {
            "$componentLabel catalog scenarios must omit optional field ${field.id.value}."
        }
    }
}

private fun NativeThemeComponentStateValueV1.matches(
    type: NativeThemeComponentValueTypeV1,
): Boolean =
    when (type) {
        NativeThemeComponentValueTypeV1.TEXT -> this is NativeThemeComponentStateValueV1.Text
        NativeThemeComponentValueTypeV1.BOOLEAN ->
            this is NativeThemeComponentStateValueV1.BooleanValue
        NativeThemeComponentValueTypeV1.INTEGER ->
            this is NativeThemeComponentStateValueV1.IntegerValue
        NativeThemeComponentValueTypeV1.DECIMAL ->
            this is NativeThemeComponentStateValueV1.DecimalValue
        NativeThemeComponentValueTypeV1.ENUM -> this is NativeThemeComponentStateValueV1.EnumValue
        NativeThemeComponentValueTypeV1.SEMANTIC_ROLE ->
            this is NativeThemeComponentStateValueV1.SemanticRoleValue
    }

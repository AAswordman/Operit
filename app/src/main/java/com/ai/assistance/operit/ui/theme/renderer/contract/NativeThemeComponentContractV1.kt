package com.ai.assistance.operit.ui.theme.renderer.contract

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface

private val COMPONENT_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:[._][a-z0-9]+)+$")
private val MEMBER_ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")

@JvmInline
internal value class NativeThemeComponentId(val value: String) {
    init {
        require(COMPONENT_ID_PATTERN.matches(value)) { "Invalid component ID: $value" }
    }
}

@JvmInline
internal value class NativeThemeComponentMemberId(val value: String) {
    init {
        require(MEMBER_ID_PATTERN.matches(value)) { "Invalid component member ID: $value" }
    }
}

@JvmInline
internal value class NativeThemeComponentScenarioId(val value: String) {
    init {
        require(MEMBER_ID_PATTERN.matches(value)) { "Invalid component scenario ID: $value" }
    }
}

internal data class NativeThemeComponentVersionV1(
    val major: Int,
    val minor: Int,
) {
    init {
        require(major > 0) { "Component major version must be positive." }
        require(minor >= 0) { "Component minor version cannot be negative." }
    }
}

internal enum class NativeThemeComponentCategoryV1 {
    HOST_CAPABILITY,
    ACTION,
    INPUT,
    NAVIGATION,
    CONTAINER,
    FEEDBACK,
    DATA_DISPLAY,
    CHAT,
    MODEL,
    CHARACTER,
    MARKET,
    WORKFLOW,
    MEMORY,
    TOOLBOX,
    FLOATING,
}

internal enum class NativeThemeComponentValueTypeV1 {
    TEXT,
    BOOLEAN,
    INTEGER,
    DECIMAL,
    SEMANTIC_ROLE,
}

internal enum class NativeThemeComponentSlotCardinalityV1 {
    REQUIRED_SINGLE,
    OPTIONAL_SINGLE,
    REPEATED,
}

internal enum class NativeThemeComponentSemanticRoleV1 {
    ACTION,
    NAVIGATION_DESTINATION,
    INPUT,
    STATUS,
    CONTENT,
}

internal enum class NativeThemeComponentCatalogStateV1 {
    NORMAL,
    DISABLED,
    SELECTED,
    LOADING,
    ERROR,
    EMPTY,
    STREAMING,
}

internal data class NativeThemeComponentStateFieldV1(
    val id: NativeThemeComponentMemberId,
    val type: NativeThemeComponentValueTypeV1,
)

internal data class NativeThemeComponentEventV1(
    val id: NativeThemeComponentMemberId,
)

internal data class NativeThemeComponentSlotV1(
    val id: NativeThemeComponentMemberId,
    val cardinality: NativeThemeComponentSlotCardinalityV1,
)

internal data class NativeThemeComponentSemanticsV1(
    val roles: Set<NativeThemeComponentSemanticRoleV1>,
    val roleStateField: NativeThemeComponentMemberId? = null,
    val accessibleLabelField: NativeThemeComponentMemberId?,
    val selectedStateField: NativeThemeComponentMemberId? = null,
    val enabledStateField: NativeThemeComponentMemberId? = null,
    val minimumTouchTargetDp: Int? = null,
)

internal data class NativeThemeComponentContractV1(
    val id: NativeThemeComponentId,
    val version: NativeThemeComponentVersionV1,
    val category: NativeThemeComponentCategoryV1,
    val required: Boolean,
    val supportedHostSurfaces: Set<NativeThemeHostSurface>,
    val stateFields: List<NativeThemeComponentStateFieldV1>,
    val events: List<NativeThemeComponentEventV1>,
    val slots: List<NativeThemeComponentSlotV1>,
    val semantics: NativeThemeComponentSemanticsV1,
    val catalogStates: Set<NativeThemeComponentCatalogStateV1>,
)

internal class NativeThemeComponentKeyV1<State : Any, Event : Any, Slots : Any>(
    val contract: NativeThemeComponentContractV1,
)

internal fun validateNativeThemeComponentContractsV1(
    contracts: List<NativeThemeComponentContractV1>,
) {
    require(contracts.isNotEmpty()) { "The native theme component contract set cannot be empty." }
    require(contracts.map { contract -> contract.id }.distinct().size == contracts.size) {
        "Native theme component IDs must be unique."
    }
    contracts.forEach(::validateNativeThemeComponentContractV1)
}

private fun validateNativeThemeComponentContractV1(contract: NativeThemeComponentContractV1) {
    require(contract.supportedHostSurfaces.isNotEmpty()) {
        "Component ${contract.id.value} must support at least one host surface."
    }
    require(contract.stateFields.map { field -> field.id }.distinct().size == contract.stateFields.size) {
        "Component ${contract.id.value} state field IDs must be unique."
    }
    require(contract.events.map { event -> event.id }.distinct().size == contract.events.size) {
        "Component ${contract.id.value} event IDs must be unique."
    }
    require(contract.slots.map { slot -> slot.id }.distinct().size == contract.slots.size) {
        "Component ${contract.id.value} slot IDs must be unique."
    }
    require(NativeThemeComponentCatalogStateV1.NORMAL in contract.catalogStates) {
        "Component ${contract.id.value} must define a normal catalog state."
    }
    require(contract.semantics.roles.isNotEmpty()) {
        "Component ${contract.id.value} must define at least one semantic role."
    }
    if (contract.semantics.roles.size > 1) {
        require(contract.semantics.roleStateField != null) {
            "Component ${contract.id.value} must bind multiple semantic roles to a state field."
        }
    }
    if (
        contract.semantics.roles.any { role ->
            role == NativeThemeComponentSemanticRoleV1.ACTION ||
                role == NativeThemeComponentSemanticRoleV1.NAVIGATION_DESTINATION ||
                role == NativeThemeComponentSemanticRoleV1.INPUT
        }
    ) {
        require(contract.semantics.accessibleLabelField != null) {
            "Interactive component ${contract.id.value} must define an accessible label field."
        }
    }
    if (NativeThemeComponentCatalogStateV1.SELECTED in contract.catalogStates) {
        require(contract.semantics.selectedStateField != null) {
            "Component ${contract.id.value} must bind its selected catalog state."
        }
    }
    if (NativeThemeComponentCatalogStateV1.DISABLED in contract.catalogStates) {
        require(contract.semantics.enabledStateField != null) {
            "Component ${contract.id.value} must bind its disabled catalog state."
        }
    }

    val fieldsById = contract.stateFields.associateBy { field -> field.id }
    validateSemanticField(
        componentId = contract.id,
        fieldId = contract.semantics.roleStateField,
        expectedType = NativeThemeComponentValueTypeV1.SEMANTIC_ROLE,
        fieldsById = fieldsById,
        semanticName = "semantic role",
    )
    validateSemanticField(
        componentId = contract.id,
        fieldId = contract.semantics.accessibleLabelField,
        expectedType = NativeThemeComponentValueTypeV1.TEXT,
        fieldsById = fieldsById,
        semanticName = "accessible label",
    )
    validateSemanticField(
        componentId = contract.id,
        fieldId = contract.semantics.selectedStateField,
        expectedType = NativeThemeComponentValueTypeV1.BOOLEAN,
        fieldsById = fieldsById,
        semanticName = "selected state",
    )
    validateSemanticField(
        componentId = contract.id,
        fieldId = contract.semantics.enabledStateField,
        expectedType = NativeThemeComponentValueTypeV1.BOOLEAN,
        fieldsById = fieldsById,
        semanticName = "enabled state",
    )

    if (contract.events.isNotEmpty()) {
        require((contract.semantics.minimumTouchTargetDp ?: 0) >= 48) {
            "Interactive component ${contract.id.value} must require at least a 48dp touch target."
        }
    }
}

private fun validateSemanticField(
    componentId: NativeThemeComponentId,
    fieldId: NativeThemeComponentMemberId?,
    expectedType: NativeThemeComponentValueTypeV1,
    fieldsById: Map<NativeThemeComponentMemberId, NativeThemeComponentStateFieldV1>,
    semanticName: String,
) {
    if (fieldId == null) return
    val field = requireNotNull(fieldsById[fieldId]) {
        "Component ${componentId.value} $semanticName references unknown field ${fieldId.value}."
    }
    require(field.type == expectedType) {
        "Component ${componentId.value} $semanticName must reference a $expectedType field."
    }
}

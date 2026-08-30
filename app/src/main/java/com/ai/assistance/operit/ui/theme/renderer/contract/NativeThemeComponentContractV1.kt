package com.ai.assistance.operit.ui.theme.renderer.contract

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.style.NativeThemeComponentFamilyIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePartIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePropertyIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateAxisV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateAxisContractV1
import com.ai.assistance.operit.ui.theme.style.accepts
import kotlinx.serialization.Serializable

private val COMPONENT_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:[._][a-z0-9]+)+$")
private val MEMBER_ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")

@Serializable
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
    ENUM,
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
    SINGLE_CHOICE_INPUT,
    STATUS,
    CONTENT,
}

internal enum class NativeThemeComponentAccessibilityRoleV1 {
    BUTTON,
    TAB,
    RADIO_BUTTON,
}

internal enum class NativeThemeComponentLiveRegionModeV1 {
    POLITE,
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
    val required: Boolean = true,
    val enumValues: List<NativeThemeComponentMemberId> = emptyList(),
)

internal sealed interface NativeThemeComponentStateValueV1 {
    data class Text(val value: String) : NativeThemeComponentStateValueV1

    data class BooleanValue(val value: Boolean) : NativeThemeComponentStateValueV1

    data class IntegerValue(val value: Long) : NativeThemeComponentStateValueV1

    data class DecimalValue(val value: Double) : NativeThemeComponentStateValueV1

    data class EnumValue(val value: NativeThemeComponentMemberId) : NativeThemeComponentStateValueV1

    data class SemanticRoleValue(
        val value: NativeThemeComponentSemanticRoleV1,
    ) : NativeThemeComponentStateValueV1
}

internal data class NativeThemeComponentEventV1(
    val id: NativeThemeComponentMemberId,
)

internal data class NativeThemeComponentSlotV1(
    val id: NativeThemeComponentMemberId,
    val cardinality: NativeThemeComponentSlotCardinalityV1,
)

internal data class NativeThemeComponentStylePartContractV1(
    val id: NativeThemeStylePartIdV1,
    val allowedProperties: Set<NativeThemeStylePropertyIdV1>,
    val requiredProperties: Set<NativeThemeStylePropertyIdV1> = emptySet(),
    val required: Boolean = true,
)

internal object NativeThemeComponentStylePartIdsV1 {
    val surface = NativeThemeStylePartIdV1("surface")
    val label = NativeThemeStylePartIdV1("label")
    val supportingText = NativeThemeStylePartIdV1("supporting_text")
    val title = NativeThemeStylePartIdV1("title")
    val description = NativeThemeStylePartIdV1("description")
    val message = NativeThemeStylePartIdV1("message")
    val value = NativeThemeStylePartIdV1("value")
    val leading = NativeThemeStylePartIdV1("leading")
    val indicator = NativeThemeStylePartIdV1("indicator")
    val content = NativeThemeStylePartIdV1("content")
}

internal object NativeThemeComponentStylePropertySetsV1 {
    val surface =
        setOf(
            NativeThemeStylePropertyIdV1.SURFACE_COLOR,
            NativeThemeStylePropertyIdV1.SHAPE,
            NativeThemeStylePropertyIdV1.BORDER_STACK,
            NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY,
            NativeThemeStylePropertyIdV1.CONTENT_BLUR,
            NativeThemeStylePropertyIdV1.BACKDROP_BLUR,
            NativeThemeStylePropertyIdV1.MATERIAL,
            NativeThemeStylePropertyIdV1.SHADOW_STACK,
            NativeThemeStylePropertyIdV1.PADDING,
            NativeThemeStylePropertyIdV1.MOTION,
        )
    val text =
        setOf(
            NativeThemeStylePropertyIdV1.CONTENT_COLOR,
            NativeThemeStylePropertyIdV1.TEXT_STYLE,
            NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY,
            NativeThemeStylePropertyIdV1.MOTION,
        )
    val icon =
        setOf(
            NativeThemeStylePropertyIdV1.CONTENT_COLOR,
            NativeThemeStylePropertyIdV1.ICON_SIZE,
            NativeThemeStylePropertyIdV1.ICON_CONTAINER,
            NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY,
            NativeThemeStylePropertyIdV1.MOTION,
        )
    val indicator =
        setOf(
            NativeThemeStylePropertyIdV1.SURFACE_COLOR,
            NativeThemeStylePropertyIdV1.CONTENT_COLOR,
            NativeThemeStylePropertyIdV1.SHAPE,
            NativeThemeStylePropertyIdV1.BORDER_STACK,
            NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY,
            NativeThemeStylePropertyIdV1.MOTION,
        )
    val content =
        setOf(
            NativeThemeStylePropertyIdV1.SURFACE_COLOR,
            NativeThemeStylePropertyIdV1.CONTENT_COLOR,
            NativeThemeStylePropertyIdV1.SHAPE,
            NativeThemeStylePropertyIdV1.BORDER_STACK,
            NativeThemeStylePropertyIdV1.WHOLE_LAYER_OPACITY,
            NativeThemeStylePropertyIdV1.PADDING,
            NativeThemeStylePropertyIdV1.MOTION,
        )
    val surfaceRequired =
        setOf(
            NativeThemeStylePropertyIdV1.SURFACE_COLOR,
            NativeThemeStylePropertyIdV1.SHAPE,
        )
    val textRequired =
        setOf(
            NativeThemeStylePropertyIdV1.CONTENT_COLOR,
            NativeThemeStylePropertyIdV1.TEXT_STYLE,
        )
    val iconRequired = setOf(NativeThemeStylePropertyIdV1.CONTENT_COLOR)
    val indicatorRequired =
        setOf(
            NativeThemeStylePropertyIdV1.SURFACE_COLOR,
            NativeThemeStylePropertyIdV1.CONTENT_COLOR,
            NativeThemeStylePropertyIdV1.SHAPE,
        )
    val contentRequired = setOf(NativeThemeStylePropertyIdV1.PADDING)
}

internal data class NativeThemeComponentCatalogStateMappingV1(
    val fieldId: NativeThemeComponentMemberId,
    val enumValueByState: Map<NativeThemeComponentCatalogStateV1, NativeThemeComponentMemberId>,
)

internal data class NativeThemeComponentSemanticsV1(
    val roles: Set<NativeThemeComponentSemanticRoleV1>,
    val roleStateField: NativeThemeComponentMemberId? = null,
    val accessibleLabelField: NativeThemeComponentMemberId?,
    val selectedStateField: NativeThemeComponentMemberId? = null,
    val enabledStateField: NativeThemeComponentMemberId? = null,
    val headingField: NativeThemeComponentMemberId? = null,
    val statusMessageField: NativeThemeComponentMemberId? = null,
    val displayValueField: NativeThemeComponentMemberId? = null,
    val accessibilityRoleBySemanticRole:
        Map<NativeThemeComponentSemanticRoleV1, NativeThemeComponentAccessibilityRoleV1> =
        emptyMap(),
    val liveRegionMode: NativeThemeComponentLiveRegionModeV1? = null,
    val indeterminateProgressStates: Set<NativeThemeComponentCatalogStateV1> = emptySet(),
    val decorativeSlotIds: Set<NativeThemeComponentMemberId> = emptySet(),
    val minimumTouchTargetDp: Int? = null,
)

internal data class NativeThemeComponentContractV1(
    val id: NativeThemeComponentId,
    val version: NativeThemeComponentVersionV1,
    val category: NativeThemeComponentCategoryV1,
    val required: Boolean,
    val supportedHostSurfaces: Set<NativeThemeHostSurface>,
    val styleFamily: NativeThemeComponentFamilyIdV1,
    val styleParts: List<NativeThemeComponentStylePartContractV1>,
    val styleStateAxes: List<NativeThemeStyleStateAxisContractV1>,
    val stateFields: List<NativeThemeComponentStateFieldV1>,
    val events: List<NativeThemeComponentEventV1>,
    val slots: List<NativeThemeComponentSlotV1>,
    val semantics: NativeThemeComponentSemanticsV1,
    val catalogStates: Set<NativeThemeComponentCatalogStateV1>,
    val catalogStateMapping: NativeThemeComponentCatalogStateMappingV1? = null,
)

internal class NativeThemeComponentKeyV1<State : Any, Event : Any, Slots : Any>(
    val contract: NativeThemeComponentContractV1,
    val encodeState: (State) -> Map<NativeThemeComponentMemberId, NativeThemeComponentStateValueV1>,
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
    require(contract.styleParts.isNotEmpty()) {
        "Component ${contract.id.value} must declare at least one style part."
    }
    require(contract.styleParts.map { part -> part.id }.distinct().size == contract.styleParts.size) {
        "Component ${contract.id.value} style part IDs must be unique."
    }
    require(contract.styleParts.all { part -> part.allowedProperties.isNotEmpty() }) {
        "Component ${contract.id.value} style parts must allow at least one property."
    }
    require(contract.styleParts.all { part -> part.requiredProperties.all { it in part.allowedProperties } }) {
        "Component ${contract.id.value} required style properties must be allowed by their part."
    }
    require(contract.styleParts.any { part -> part.id == NativeThemeComponentStylePartIdsV1.surface }) {
        "Component ${contract.id.value} must declare a surface style part."
    }
    require(contract.styleParts.any { part -> part.id == NativeThemeComponentStylePartIdsV1.content }) {
        "Component ${contract.id.value} must declare a content style part."
    }
    require(contract.styleStateAxes.map { axis -> axis.axis }.distinct().size == contract.styleStateAxes.size) {
        "Component ${contract.id.value} style state axes must be unique."
    }
    require(contract.styleStateAxes.all { axis -> axis.values.isNotEmpty() }) {
        "Component ${contract.id.value} style state axes must declare at least one value."
    }
    require(contract.styleStateAxes.all { axis -> axis.values.all { value -> axis.axis.accepts(value) } }) {
        "Component ${contract.id.value} style state axes contain invalid values."
    }
    val styleStateCombinationCount =
        contract.styleStateAxes.fold(1L) { count, axis -> count * (axis.values.size + 1) }
    require(styleStateCombinationCount <= 256L) {
        "Component ${contract.id.value} style state combinations must not exceed 256."
    }
    require(contract.stateFields.map { field -> field.id }.distinct().size == contract.stateFields.size) {
        "Component ${contract.id.value} state field IDs must be unique."
    }
    contract.stateFields.forEach { field ->
        if (field.type == NativeThemeComponentValueTypeV1.ENUM) {
            require(field.enumValues.isNotEmpty()) {
                "Component ${contract.id.value} enum field ${field.id.value} must define values."
            }
            require(field.enumValues.distinct().size == field.enumValues.size) {
                "Component ${contract.id.value} enum field ${field.id.value} values must be unique."
            }
        } else {
            require(field.enumValues.isEmpty()) {
                "Component ${contract.id.value} non-enum field ${field.id.value} cannot define enum values."
            }
        }
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
    val interactiveSemanticRoles =
        contract.semantics.roles.intersect(
            setOf(
                NativeThemeComponentSemanticRoleV1.ACTION,
                NativeThemeComponentSemanticRoleV1.NAVIGATION_DESTINATION,
                NativeThemeComponentSemanticRoleV1.SINGLE_CHOICE_INPUT,
            )
        )
    if (interactiveSemanticRoles.isNotEmpty()) {
        require(contract.semantics.accessibleLabelField != null) {
            "Interactive component ${contract.id.value} must define an accessible label field."
        }
        require(
            contract.semantics.accessibilityRoleBySemanticRole.keys == interactiveSemanticRoles
        ) {
            "Interactive component ${contract.id.value} must map every semantic role to an accessibility role."
        }
        validateNativeThemeComponentAccessibilityRolesV1(contract)
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
    val slotsById = contract.slots.associateBy { slot -> slot.id }
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
    validateSemanticField(
        componentId = contract.id,
        fieldId = contract.semantics.headingField,
        expectedType = NativeThemeComponentValueTypeV1.TEXT,
        fieldsById = fieldsById,
        semanticName = "heading",
    )
    validateSemanticField(
        componentId = contract.id,
        fieldId = contract.semantics.statusMessageField,
        expectedType = NativeThemeComponentValueTypeV1.TEXT,
        fieldsById = fieldsById,
        semanticName = "status message",
    )
    validateSemanticField(
        componentId = contract.id,
        fieldId = contract.semantics.displayValueField,
        expectedType = NativeThemeComponentValueTypeV1.TEXT,
        fieldsById = fieldsById,
        semanticName = "display value",
    )

    if (NativeThemeComponentSemanticRoleV1.STATUS in contract.semantics.roles) {
        require(contract.semantics.statusMessageField != null) {
            "Status component ${contract.id.value} must bind its status message."
        }
        require(contract.semantics.liveRegionMode != null) {
            "Status component ${contract.id.value} must define its live region mode."
        }
    }
    if (contract.category == NativeThemeComponentCategoryV1.DATA_DISPLAY) {
        require(contract.semantics.displayValueField != null) {
            "Data display component ${contract.id.value} must bind its display value."
        }
    }

    if (contract.events.isNotEmpty()) {
        require((contract.semantics.minimumTouchTargetDp ?: 0) >= 48) {
            "Interactive component ${contract.id.value} must require at least a 48dp touch target."
        }
    }
    require(contract.semantics.indeterminateProgressStates.all { state -> state in contract.catalogStates }) {
        "Component ${contract.id.value} progress semantics reference an undeclared catalog state."
    }
    require(contract.semantics.decorativeSlotIds.all { slotId -> slotId in slotsById }) {
        "Component ${contract.id.value} decorative semantics reference an unknown slot."
    }

    val dynamicCatalogStates =
        contract.catalogStates.intersect(
            setOf(
                NativeThemeComponentCatalogStateV1.LOADING,
                NativeThemeComponentCatalogStateV1.ERROR,
                NativeThemeComponentCatalogStateV1.EMPTY,
                NativeThemeComponentCatalogStateV1.STREAMING,
            )
        )
    if (dynamicCatalogStates.isNotEmpty()) {
        require(contract.catalogStateMapping != null) {
            "Component ${contract.id.value} must map its dynamic catalog states to an enum field."
        }
    }
    contract.catalogStateMapping?.let { mapping ->
        val field = requireNotNull(fieldsById[mapping.fieldId]) {
            "Component ${contract.id.value} catalog state mapping references unknown field ${mapping.fieldId.value}."
        }
        require(field.type == NativeThemeComponentValueTypeV1.ENUM) {
            "Component ${contract.id.value} catalog state mapping must reference an ENUM field."
        }
        require(field.required) {
            "Component ${contract.id.value} catalog state mapping must reference a required field."
        }
        require(mapping.enumValueByState.keys == contract.catalogStates) {
            "Component ${contract.id.value} catalog state mapping must cover every catalog state."
        }
        require(mapping.enumValueByState.values.all { value -> value in field.enumValues }) {
            "Component ${contract.id.value} catalog state mapping uses an unknown enum value."
        }
        require(mapping.enumValueByState.values.toSet().size == mapping.enumValueByState.size) {
            "Component ${contract.id.value} catalog states must map to distinct enum values."
        }
    }
}

private fun validateNativeThemeComponentAccessibilityRolesV1(
    contract: NativeThemeComponentContractV1,
) {
    contract.semantics.accessibilityRoleBySemanticRole.forEach { (semanticRole, accessibilityRole) ->
        val expectedRole =
            when (semanticRole) {
                NativeThemeComponentSemanticRoleV1.ACTION ->
                    NativeThemeComponentAccessibilityRoleV1.BUTTON
                NativeThemeComponentSemanticRoleV1.NAVIGATION_DESTINATION ->
                    NativeThemeComponentAccessibilityRoleV1.TAB
                NativeThemeComponentSemanticRoleV1.SINGLE_CHOICE_INPUT ->
                    NativeThemeComponentAccessibilityRoleV1.RADIO_BUTTON
                NativeThemeComponentSemanticRoleV1.STATUS,
                NativeThemeComponentSemanticRoleV1.CONTENT -> null
            }
        require(accessibilityRole == expectedRole) {
            "Component ${contract.id.value} maps $semanticRole to the wrong accessibility role."
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
    require(field.required) {
        "Component ${componentId.value} $semanticName must reference a required field."
    }
}

package com.ai.assistance.operit.ui.theme.renderer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotCardinalityV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateFieldV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateValueV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentValueTypeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeComponentFamilyIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateAxisV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateAxisContractV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleStateValueV1

internal enum class NativeThemeNavigationDrawerItemSemanticRoleV1 {
    NAVIGATION_DESTINATION,
    ACTION,
}

internal data class NativeThemeNavigationDrawerItemStateV1(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean,
    val semanticRole: NativeThemeNavigationDrawerItemSemanticRoleV1,
)

internal sealed interface NativeThemeNavigationDrawerItemEventV1 {
    data object Activate : NativeThemeNavigationDrawerItemEventV1
}

internal data class NativeThemeNavigationDrawerItemSlotsV1(
    val leading: @Composable (Modifier) -> Unit,
)

internal fun NativeThemeNavigationDrawerItemSemanticRoleV1.toComponentSemanticRoleV1():
    NativeThemeComponentSemanticRoleV1 =
    when (this) {
        NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION ->
            NativeThemeComponentSemanticRoleV1.NAVIGATION_DESTINATION
        NativeThemeNavigationDrawerItemSemanticRoleV1.ACTION ->
            NativeThemeComponentSemanticRoleV1.ACTION
    }

internal object NativeThemeNavigationDrawerItemContractV1 {
    val labelFieldId = NativeThemeComponentMemberId("label")
    val selectedFieldId = NativeThemeComponentMemberId("selected")
    val enabledFieldId = NativeThemeComponentMemberId("enabled")
    val semanticRoleFieldId = NativeThemeComponentMemberId("semantic_role")
    val activateEventId = NativeThemeComponentMemberId("activate")
    val leadingSlotId = NativeThemeComponentMemberId("leading")

    val contract =
        NativeThemeComponentContractV1(
            id = NativeThemeComponentId("operit.navigation.drawer_item"),
            version = NativeThemeComponentVersionV1(major = 1, minor = 0),
            category = NativeThemeComponentCategoryV1.NAVIGATION,
            required = true,
            supportedHostSurfaces = setOf(NativeThemeHostSurface.MAIN),
            styleFamily = NativeThemeComponentFamilyIdV1("operit.navigation"),
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
                        id = NativeThemeComponentStylePartIdsV1.leading,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.icon,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.iconRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.indicator,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.indicator,
                        required = false,
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
                    NativeThemeStyleStateAxisContractV1(
                        axis = NativeThemeStyleStateAxisV1.VARIANT,
                        values =
                            setOf(
                                NativeThemeStyleStateValueV1.NAVIGATION_DESTINATION,
                                NativeThemeStyleStateValueV1.ACTION,
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
                        id = selectedFieldId,
                        type = NativeThemeComponentValueTypeV1.BOOLEAN,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = enabledFieldId,
                        type = NativeThemeComponentValueTypeV1.BOOLEAN,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = semanticRoleFieldId,
                        type = NativeThemeComponentValueTypeV1.SEMANTIC_ROLE,
                    ),
                ),
            events = listOf(NativeThemeComponentEventV1(activateEventId)),
            slots =
                listOf(
                    NativeThemeComponentSlotV1(
                        id = leadingSlotId,
                        cardinality = NativeThemeComponentSlotCardinalityV1.REQUIRED_SINGLE,
                    ),
                ),
            semantics =
                NativeThemeComponentSemanticsV1(
                    roles =
                        setOf(
                            NativeThemeComponentSemanticRoleV1.NAVIGATION_DESTINATION,
                            NativeThemeComponentSemanticRoleV1.ACTION,
                        ),
                    roleStateField = semanticRoleFieldId,
                    accessibleLabelField = labelFieldId,
                    selectedStateField = selectedFieldId,
                    enabledStateField = enabledFieldId,
                    accessibilityRoleBySemanticRole =
                        mapOf(
                            NativeThemeComponentSemanticRoleV1.NAVIGATION_DESTINATION to
                                NativeThemeComponentAccessibilityRoleV1.TAB,
                            NativeThemeComponentSemanticRoleV1.ACTION to
                                NativeThemeComponentAccessibilityRoleV1.BUTTON,
                        ),
                    decorativeSlotIds = setOf(leadingSlotId),
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
            NativeThemeNavigationDrawerItemStateV1,
            NativeThemeNavigationDrawerItemEventV1,
            NativeThemeNavigationDrawerItemSlotsV1,
        >(
            contract = contract,
            encodeState = { state ->
                mapOf(
                    labelFieldId to NativeThemeComponentStateValueV1.Text(state.label),
                    selectedFieldId to
                        NativeThemeComponentStateValueV1.BooleanValue(state.selected),
                    enabledFieldId to NativeThemeComponentStateValueV1.BooleanValue(state.enabled),
                    semanticRoleFieldId to
                        NativeThemeComponentStateValueV1.SemanticRoleValue(
                            state.semanticRole.toComponentSemanticRoleV1()
                        ),
                )
            },
        )
}

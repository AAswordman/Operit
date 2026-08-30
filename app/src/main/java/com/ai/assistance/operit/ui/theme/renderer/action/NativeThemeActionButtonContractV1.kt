package com.ai.assistance.operit.ui.theme.renderer.action

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

internal enum class NativeThemeActionButtonEmphasisV1 {
    STANDARD,
    CAUTION,
    DESTRUCTIVE,
}

internal data class NativeThemeActionButtonStateV1(
    val label: String,
    val enabled: Boolean,
    val emphasis: NativeThemeActionButtonEmphasisV1,
)

internal sealed interface NativeThemeActionButtonEventV1 {
    data object Activate : NativeThemeActionButtonEventV1
}

internal data class NativeThemeActionButtonSlotsV1(
    val leading: @Composable (Modifier) -> Unit,
)

internal object NativeThemeActionButtonContractV1 {
    val labelFieldId = NativeThemeComponentMemberId("label")
    val enabledFieldId = NativeThemeComponentMemberId("enabled")
    val emphasisFieldId = NativeThemeComponentMemberId("emphasis")
    val standardEmphasisId = NativeThemeComponentMemberId("standard")
    val cautionEmphasisId = NativeThemeComponentMemberId("caution")
    val destructiveEmphasisId = NativeThemeComponentMemberId("destructive")
    val activateEventId = NativeThemeComponentMemberId("activate")
    val leadingSlotId = NativeThemeComponentMemberId("leading")

    val contract =
        NativeThemeComponentContractV1(
            id = NativeThemeComponentId("operit.action.button"),
            version = NativeThemeComponentVersionV1(major = 1, minor = 0),
            category = NativeThemeComponentCategoryV1.ACTION,
            required = true,
            supportedHostSurfaces = setOf(NativeThemeHostSurface.MAIN),
            styleFamily = NativeThemeComponentFamilyIdV1("operit.action"),
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
                                NativeThemeStyleStateValueV1.STANDARD,
                                NativeThemeStyleStateValueV1.CAUTION,
                                NativeThemeStyleStateValueV1.DESTRUCTIVE,
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
                        id = enabledFieldId,
                        type = NativeThemeComponentValueTypeV1.BOOLEAN,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = emphasisFieldId,
                        type = NativeThemeComponentValueTypeV1.ENUM,
                        enumValues =
                            listOf(
                                standardEmphasisId,
                                cautionEmphasisId,
                                destructiveEmphasisId,
                            ),
                    ),
                ),
            events = listOf(NativeThemeComponentEventV1(activateEventId)),
            slots =
                listOf(
                    NativeThemeComponentSlotV1(
                        id = leadingSlotId,
                        cardinality = NativeThemeComponentSlotCardinalityV1.REQUIRED_SINGLE,
                    )
                ),
            semantics =
                NativeThemeComponentSemanticsV1(
                    roles = setOf(NativeThemeComponentSemanticRoleV1.ACTION),
                    accessibleLabelField = labelFieldId,
                    enabledStateField = enabledFieldId,
                    accessibilityRoleBySemanticRole =
                        mapOf(
                            NativeThemeComponentSemanticRoleV1.ACTION to
                                NativeThemeComponentAccessibilityRoleV1.BUTTON
                        ),
                    decorativeSlotIds = setOf(leadingSlotId),
                    minimumTouchTargetDp = 48,
                ),
            catalogStates =
                setOf(
                    NativeThemeComponentCatalogStateV1.NORMAL,
                    NativeThemeComponentCatalogStateV1.DISABLED,
                ),
        )

    val key =
        NativeThemeComponentKeyV1<
            NativeThemeActionButtonStateV1,
            NativeThemeActionButtonEventV1,
            NativeThemeActionButtonSlotsV1,
        >(
            contract = contract,
            encodeState = { state ->
                mapOf(
                    labelFieldId to NativeThemeComponentStateValueV1.Text(state.label),
                    enabledFieldId to NativeThemeComponentStateValueV1.BooleanValue(state.enabled),
                    emphasisFieldId to
                        NativeThemeComponentStateValueV1.EnumValue(
                            when (state.emphasis) {
                                NativeThemeActionButtonEmphasisV1.STANDARD -> standardEmphasisId
                                NativeThemeActionButtonEmphasisV1.CAUTION -> cautionEmphasisId
                                NativeThemeActionButtonEmphasisV1.DESTRUCTIVE -> destructiveEmphasisId
                            }
                        ),
                )
            },
        )
}

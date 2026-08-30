package com.ai.assistance.operit.ui.theme.renderer.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCategoryV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentContractV1
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

internal data class NativeThemeStatStateV1(
    val label: String,
    val value: String,
)

internal sealed interface NativeThemeStatEventV1

internal data class NativeThemeStatSlotsV1(
    val leading: @Composable (Modifier) -> Unit,
)

internal object NativeThemeStatContractV1 {
    val labelFieldId = NativeThemeComponentMemberId("label")
    val valueFieldId = NativeThemeComponentMemberId("value")
    val leadingSlotId = NativeThemeComponentMemberId("leading")

    val contract =
        NativeThemeComponentContractV1(
            id = NativeThemeComponentId("operit.data_display.stat"),
            version = NativeThemeComponentVersionV1(major = 1, minor = 1),
            category = NativeThemeComponentCategoryV1.DATA_DISPLAY,
            required = true,
            supportedHostSurfaces =
                setOf(
                    NativeThemeHostSurface.MAIN,
                    NativeThemeHostSurface.EDITOR_PREVIEW,
                ),
            styleFamily = NativeThemeComponentFamilyIdV1("operit.data_display"),
            styleParts =
                listOf(
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.surface,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.surface,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.surfaceRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.leading,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.icon,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.iconRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.label,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.text,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.textRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.value,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.text,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.textRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.content,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.content,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.contentRequired,
                    ),
                ),
            styleStateAxes = emptyList(),
            stateFields =
                listOf(
                    NativeThemeComponentStateFieldV1(
                        id = labelFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = valueFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                    ),
                ),
            events = emptyList(),
            slots =
                listOf(
                    NativeThemeComponentSlotV1(
                        id = leadingSlotId,
                        cardinality = NativeThemeComponentSlotCardinalityV1.REQUIRED_SINGLE,
                    )
                ),
            semantics =
                NativeThemeComponentSemanticsV1(
                    roles = setOf(NativeThemeComponentSemanticRoleV1.CONTENT),
                    accessibleLabelField = labelFieldId,
                    displayValueField = valueFieldId,
                    decorativeSlotIds = setOf(leadingSlotId),
                ),
            catalogStates = setOf(NativeThemeComponentCatalogStateV1.NORMAL),
        )

    val key =
        NativeThemeComponentKeyV1<
            NativeThemeStatStateV1,
            NativeThemeStatEventV1,
            NativeThemeStatSlotsV1,
        >(
            contract = contract,
            encodeState = { state ->
                mapOf(
                    labelFieldId to NativeThemeComponentStateValueV1.Text(state.label),
                    valueFieldId to NativeThemeComponentStateValueV1.Text(state.value),
                )
            },
        )
}

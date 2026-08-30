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
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotCardinalityV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateFieldV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateValueV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentValueTypeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1

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
            version = NativeThemeComponentVersionV1(major = 1, minor = 0),
            category = NativeThemeComponentCategoryV1.DATA_DISPLAY,
            required = true,
            supportedHostSurfaces = setOf(NativeThemeHostSurface.MAIN),
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

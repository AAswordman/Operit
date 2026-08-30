package com.ai.assistance.operit.ui.theme.renderer.feedback

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateMappingV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCategoryV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentContractV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentKeyV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentLiveRegionModeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentMemberId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticsV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotCardinalityV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSlotV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateFieldV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStateValueV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentValueTypeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1

internal enum class NativeThemeOperationStatusKindV1 {
    LOADING,
    SUCCESS,
    ERROR,
}

internal data class NativeThemeOperationStatusStateV1(
    val title: String?,
    val message: String,
    val kind: NativeThemeOperationStatusKindV1,
)

internal sealed interface NativeThemeOperationStatusEventV1

internal data class NativeThemeOperationStatusSlotsV1(
    val leading: (@Composable (Modifier) -> Unit)?,
)

internal object NativeThemeOperationStatusContractV1 {
    val titleFieldId = NativeThemeComponentMemberId("title")
    val messageFieldId = NativeThemeComponentMemberId("message")
    val kindFieldId = NativeThemeComponentMemberId("kind")
    val loadingKindId = NativeThemeComponentMemberId("loading")
    val successKindId = NativeThemeComponentMemberId("success")
    val errorKindId = NativeThemeComponentMemberId("error")
    val leadingSlotId = NativeThemeComponentMemberId("leading")

    val contract =
        NativeThemeComponentContractV1(
            id = NativeThemeComponentId("operit.feedback.operation_status"),
            version = NativeThemeComponentVersionV1(major = 1, minor = 0),
            category = NativeThemeComponentCategoryV1.FEEDBACK,
            required = true,
            supportedHostSurfaces = setOf(NativeThemeHostSurface.MAIN),
            stateFields =
                listOf(
                    NativeThemeComponentStateFieldV1(
                        id = titleFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                        required = false,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = messageFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = kindFieldId,
                        type = NativeThemeComponentValueTypeV1.ENUM,
                        enumValues = listOf(loadingKindId, successKindId, errorKindId),
                    ),
                ),
            events = emptyList(),
            slots =
                listOf(
                    NativeThemeComponentSlotV1(
                        id = leadingSlotId,
                        cardinality = NativeThemeComponentSlotCardinalityV1.OPTIONAL_SINGLE,
                    )
                ),
            semantics =
                NativeThemeComponentSemanticsV1(
                    roles = setOf(NativeThemeComponentSemanticRoleV1.STATUS),
                    accessibleLabelField = null,
                    statusMessageField = messageFieldId,
                    liveRegionMode = NativeThemeComponentLiveRegionModeV1.POLITE,
                    indeterminateProgressStates =
                        setOf(NativeThemeComponentCatalogStateV1.LOADING),
                    decorativeSlotIds = setOf(leadingSlotId),
                ),
            catalogStates =
                setOf(
                    NativeThemeComponentCatalogStateV1.NORMAL,
                    NativeThemeComponentCatalogStateV1.LOADING,
                    NativeThemeComponentCatalogStateV1.ERROR,
                ),
            catalogStateMapping =
                NativeThemeComponentCatalogStateMappingV1(
                    fieldId = kindFieldId,
                    enumValueByState =
                        mapOf(
                            NativeThemeComponentCatalogStateV1.NORMAL to successKindId,
                            NativeThemeComponentCatalogStateV1.LOADING to loadingKindId,
                            NativeThemeComponentCatalogStateV1.ERROR to errorKindId,
                        ),
                ),
        )

    val key =
        NativeThemeComponentKeyV1<
            NativeThemeOperationStatusStateV1,
            NativeThemeOperationStatusEventV1,
            NativeThemeOperationStatusSlotsV1,
        >(
            contract = contract,
            encodeState = { state ->
                buildMap {
                    state.title?.let { title ->
                        put(titleFieldId, NativeThemeComponentStateValueV1.Text(title))
                    }
                    put(messageFieldId, NativeThemeComponentStateValueV1.Text(state.message))
                    put(
                        kindFieldId,
                        NativeThemeComponentStateValueV1.EnumValue(
                            when (state.kind) {
                                NativeThemeOperationStatusKindV1.LOADING -> loadingKindId
                                NativeThemeOperationStatusKindV1.SUCCESS -> successKindId
                                NativeThemeOperationStatusKindV1.ERROR -> errorKindId
                            }
                        ),
                    )
                }
            },
        )
}

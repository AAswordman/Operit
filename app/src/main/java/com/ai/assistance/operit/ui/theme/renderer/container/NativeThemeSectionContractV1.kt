package com.ai.assistance.operit.ui.theme.renderer.container

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

internal data class NativeThemeSectionStateV1(
    val title: String,
    val description: String,
)

internal sealed interface NativeThemeSectionEventV1

internal data class NativeThemeSectionSlotsV1(
    val leading: @Composable (Modifier) -> Unit,
    val content: @Composable () -> Unit,
)

internal object NativeThemeSectionContractV1 {
    val titleFieldId = NativeThemeComponentMemberId("title")
    val descriptionFieldId = NativeThemeComponentMemberId("description")
    val leadingSlotId = NativeThemeComponentMemberId("leading")
    val contentSlotId = NativeThemeComponentMemberId("content")

    val contract =
        NativeThemeComponentContractV1(
            id = NativeThemeComponentId("operit.container.section"),
            version = NativeThemeComponentVersionV1(major = 1, minor = 0),
            category = NativeThemeComponentCategoryV1.CONTAINER,
            required = true,
            supportedHostSurfaces = setOf(NativeThemeHostSurface.MAIN),
            styleFamily = NativeThemeComponentFamilyIdV1("operit.container"),
            styleParts =
                listOf(
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.surface,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.surface,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.surfaceRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.title,
                        allowedProperties = NativeThemeComponentStylePropertySetsV1.text,
                        requiredProperties = NativeThemeComponentStylePropertySetsV1.textRequired,
                    ),
                    NativeThemeComponentStylePartContractV1(
                        id = NativeThemeComponentStylePartIdsV1.description,
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
            styleStateAxes = emptyList(),
            stateFields =
                listOf(
                    NativeThemeComponentStateFieldV1(
                        id = titleFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                    ),
                    NativeThemeComponentStateFieldV1(
                        id = descriptionFieldId,
                        type = NativeThemeComponentValueTypeV1.TEXT,
                    ),
                ),
            events = emptyList(),
            slots =
                listOf(
                    NativeThemeComponentSlotV1(
                        id = leadingSlotId,
                        cardinality = NativeThemeComponentSlotCardinalityV1.REQUIRED_SINGLE,
                    ),
                    NativeThemeComponentSlotV1(
                        id = contentSlotId,
                        cardinality = NativeThemeComponentSlotCardinalityV1.REQUIRED_SINGLE,
                    ),
                ),
            semantics =
                NativeThemeComponentSemanticsV1(
                    roles = setOf(NativeThemeComponentSemanticRoleV1.CONTENT),
                    accessibleLabelField = null,
                    headingField = titleFieldId,
                    decorativeSlotIds = setOf(leadingSlotId),
                ),
            catalogStates = setOf(NativeThemeComponentCatalogStateV1.NORMAL),
        )

    val key =
        NativeThemeComponentKeyV1<
            NativeThemeSectionStateV1,
            NativeThemeSectionEventV1,
            NativeThemeSectionSlotsV1,
        >(
            contract = contract,
            encodeState = { state ->
                mapOf(
                    titleFieldId to NativeThemeComponentStateValueV1.Text(state.title),
                    descriptionFieldId to NativeThemeComponentStateValueV1.Text(state.description),
                )
            },
        )
}

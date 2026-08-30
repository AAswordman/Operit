package com.ai.assistance.operit.ui.theme.renderer.catalog

import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonContractV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonEmphasisV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonEventV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonRendererV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonSlotsV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonStateV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionContractV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionEventV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionRendererV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionSlotsV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentCatalogStateV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentScenarioId
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentSemanticRoleV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentVersionV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatContractV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatEventV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatRendererV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatSlotsV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatStateV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusContractV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusEventV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusKindV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusRendererV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusSlotsV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusStateV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemContractV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemEventV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemRendererV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemSlotsV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemStateV1

internal object NativeThemeFoundationComponentImplementationsV1 {
    val actionButton =
        NativeThemeComponentImplementationV1<
            NativeThemeActionButtonStateV1,
            NativeThemeActionButtonEventV1,
            NativeThemeActionButtonSlotsV1,
        >(
            key = NativeThemeActionButtonContractV1.key,
            implementedVersion = NativeThemeComponentVersionV1(major = 1, minor = 0),
            renderer = NativeThemeActionButtonRendererV1,
            semanticRoleOf = { NativeThemeComponentSemanticRoleV1.ACTION },
            scenarios =
                listOf(
                    actionButtonScenario(
                        id = "normal",
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        enabled = true,
                        emphasis = NativeThemeActionButtonEmphasisV1.STANDARD,
                    ),
                    actionButtonScenario(
                        id = "disabled",
                        catalogState = NativeThemeComponentCatalogStateV1.DISABLED,
                        enabled = false,
                        emphasis = NativeThemeActionButtonEmphasisV1.STANDARD,
                    ),
                    actionButtonScenario(
                        id = "caution",
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        enabled = true,
                        emphasis = NativeThemeActionButtonEmphasisV1.CAUTION,
                    ),
                    actionButtonScenario(
                        id = "destructive",
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        enabled = true,
                        emphasis = NativeThemeActionButtonEmphasisV1.DESTRUCTIVE,
                    ),
                ),
        )

    val choiceItem =
        NativeThemeComponentImplementationV1<
            NativeThemeChoiceItemStateV1,
            NativeThemeChoiceItemEventV1,
            NativeThemeChoiceItemSlotsV1,
        >(
            key = NativeThemeChoiceItemContractV1.key,
            implementedVersion = NativeThemeComponentVersionV1(major = 1, minor = 0),
            renderer = NativeThemeChoiceItemRendererV1,
            semanticRoleOf = { NativeThemeComponentSemanticRoleV1.SINGLE_CHOICE_INPUT },
            scenarios =
                listOf(
                    choiceItemScenario(
                        id = "normal",
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        selected = false,
                        enabled = true,
                        supportingText = "Keep the current data when a conflict is found.",
                    ),
                    choiceItemScenario(
                        id = "selected",
                        catalogState = NativeThemeComponentCatalogStateV1.SELECTED,
                        selected = true,
                        enabled = true,
                        supportingText = "Replace matching records with imported data.",
                    ),
                    choiceItemScenario(
                        id = "disabled",
                        catalogState = NativeThemeComponentCatalogStateV1.DISABLED,
                        selected = false,
                        enabled = false,
                        supportingText = "This option is unavailable for the selected source.",
                    ),
                    choiceItemScenario(
                        id = "long_description",
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        selected = false,
                        enabled = true,
                        supportingText =
                            "Create independent records while preserving every existing item and link.",
                    ),
                    choiceItemScenario(
                        id = "without_description",
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        selected = false,
                        enabled = true,
                        supportingText = null,
                    ),
                ),
        )

    val section =
        NativeThemeComponentImplementationV1<
            NativeThemeSectionStateV1,
            NativeThemeSectionEventV1,
            NativeThemeSectionSlotsV1,
        >(
            key = NativeThemeSectionContractV1.key,
            implementedVersion = NativeThemeComponentVersionV1(major = 1, minor = 0),
            renderer = NativeThemeSectionRendererV1,
            semanticRoleOf = { NativeThemeComponentSemanticRoleV1.CONTENT },
            scenarios =
                listOf(
                    NativeThemeComponentCatalogScenarioV1(
                        id = NativeThemeComponentScenarioId("normal"),
                        catalogStates = setOf(NativeThemeComponentCatalogStateV1.NORMAL),
                        state =
                            NativeThemeSectionStateV1(
                                title = "Chat history",
                                description = "Export, import, or remove stored conversations.",
                            ),
                    )
                ),
        )

    val operationStatus =
        NativeThemeComponentImplementationV1<
            NativeThemeOperationStatusStateV1,
            NativeThemeOperationStatusEventV1,
            NativeThemeOperationStatusSlotsV1,
        >(
            key = NativeThemeOperationStatusContractV1.key,
            implementedVersion = NativeThemeComponentVersionV1(major = 1, minor = 0),
            renderer = NativeThemeOperationStatusRendererV1,
            semanticRoleOf = { NativeThemeComponentSemanticRoleV1.STATUS },
            scenarios =
                listOf(
                    operationStatusScenario(
                        id = "success",
                        catalogState = NativeThemeComponentCatalogStateV1.NORMAL,
                        title = "Export complete",
                        message = "The backup file is ready.",
                        kind = NativeThemeOperationStatusKindV1.SUCCESS,
                    ),
                    operationStatusScenario(
                        id = "loading",
                        catalogState = NativeThemeComponentCatalogStateV1.LOADING,
                        title = null,
                        message = "Exporting chat history",
                        kind = NativeThemeOperationStatusKindV1.LOADING,
                    ),
                    operationStatusScenario(
                        id = "error",
                        catalogState = NativeThemeComponentCatalogStateV1.ERROR,
                        title = "Export failed",
                        message = "The destination cannot be written.",
                        kind = NativeThemeOperationStatusKindV1.ERROR,
                    ),
                ),
        )

    val stat =
        NativeThemeComponentImplementationV1<
            NativeThemeStatStateV1,
            NativeThemeStatEventV1,
            NativeThemeStatSlotsV1,
        >(
            key = NativeThemeStatContractV1.key,
            implementedVersion = NativeThemeComponentVersionV1(major = 1, minor = 1),
            renderer = NativeThemeStatRendererV1,
            semanticRoleOf = { NativeThemeComponentSemanticRoleV1.CONTENT },
            scenarios =
                listOf(
                    NativeThemeComponentCatalogScenarioV1(
                        id = NativeThemeComponentScenarioId("normal"),
                        catalogStates = setOf(NativeThemeComponentCatalogStateV1.NORMAL),
                        state = NativeThemeStatStateV1(label = "Conversations", value = "128"),
                    )
                ),
        )

    private fun actionButtonScenario(
        id: String,
        catalogState: NativeThemeComponentCatalogStateV1,
        enabled: Boolean,
        emphasis: NativeThemeActionButtonEmphasisV1,
    ) =
        NativeThemeComponentCatalogScenarioV1(
            id = NativeThemeComponentScenarioId(id),
            catalogStates = setOf(catalogState),
            state =
                NativeThemeActionButtonStateV1(
                    label = "Export",
                    enabled = enabled,
                    emphasis = emphasis,
                ),
        )

    private fun choiceItemScenario(
        id: String,
        catalogState: NativeThemeComponentCatalogStateV1,
        selected: Boolean,
        enabled: Boolean,
        supportingText: String?,
    ) =
        NativeThemeComponentCatalogScenarioV1(
            id = NativeThemeComponentScenarioId(id),
            catalogStates = setOf(catalogState),
            state =
                NativeThemeChoiceItemStateV1(
                    label = "Keep existing",
                    supportingText = supportingText,
                    selected = selected,
                    enabled = enabled,
                ),
        )

    private fun operationStatusScenario(
        id: String,
        catalogState: NativeThemeComponentCatalogStateV1,
        title: String?,
        message: String,
        kind: NativeThemeOperationStatusKindV1,
    ) =
        NativeThemeComponentCatalogScenarioV1(
            id = NativeThemeComponentScenarioId(id),
            catalogStates = setOf(catalogState),
            state = NativeThemeOperationStatusStateV1(title = title, message = message, kind = kind),
        )
}

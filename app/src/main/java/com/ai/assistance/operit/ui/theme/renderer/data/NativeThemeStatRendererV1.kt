package com.ai.assistance.operit.ui.theme.renderer.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import com.ai.assistance.operit.ui.theme.LocalResolvedNativeThemeV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentCatalogV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentRendererV1
import com.ai.assistance.operit.ui.theme.style.compose.NativeThemeStyledStatPreviewV1
import com.ai.assistance.operit.ui.theme.style.compose.rememberNativeThemeStatStylePlanV1

internal object NativeThemeStatRendererV1 :
    NativeThemeComponentRendererV1<
        NativeThemeStatStateV1,
        NativeThemeStatEventV1,
        NativeThemeStatSlotsV1,
    > {
    @Composable
    override fun render(
        state: NativeThemeStatStateV1,
        slots: NativeThemeStatSlotsV1,
        onEvent: (NativeThemeStatEventV1) -> Unit,
        modifier: Modifier,
    ) {
        val stylePlan = rememberNativeThemeStatStylePlanV1()
        NativeThemeStyledStatPreviewV1(
            plan = stylePlan,
            darkTheme = LocalResolvedNativeThemeV1.current.darkTheme,
            label = state.label,
            value = state.value,
            leading = { leadingModifier -> slots.leading(leadingModifier.clearAndSetSemantics {}) },
            modifier = modifier.clearAndSetSemantics {
                contentDescription = state.label
                stateDescription = state.value
            },
        )
    }
}

@Composable
internal fun NativeThemeStatV1(
    label: String,
    value: String,
    leading: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    NativeThemeComponentCatalogV1
        .requireImplementation(NativeThemeStatContractV1.key)
        .renderer
        .render(
            state = NativeThemeStatStateV1(label = label, value = value),
            slots = NativeThemeStatSlotsV1(leading = leading),
            onEvent = {},
            modifier = modifier,
        )
}

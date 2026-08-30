package com.ai.assistance.operit.ui.theme.renderer.action

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentCatalogV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentRendererV1

internal object NativeThemeActionButtonRendererV1 :
    NativeThemeComponentRendererV1<
        NativeThemeActionButtonStateV1,
        NativeThemeActionButtonEventV1,
        NativeThemeActionButtonSlotsV1,
    > {
    @Composable
    override fun render(
        state: NativeThemeActionButtonStateV1,
        slots: NativeThemeActionButtonSlotsV1,
        onEvent: (NativeThemeActionButtonEventV1) -> Unit,
        modifier: Modifier,
    ) {
        val colors =
            when (state.emphasis) {
                NativeThemeActionButtonEmphasisV1.STANDARD ->
                    ButtonDefaults.filledTonalButtonColors()
                NativeThemeActionButtonEmphasisV1.CAUTION ->
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    )
                NativeThemeActionButtonEmphasisV1.DESTRUCTIVE ->
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                    )
            }

        FilledTonalButton(
            onClick = { onEvent(NativeThemeActionButtonEventV1.Activate) },
            modifier = Modifier.heightIn(min = 48.dp).then(modifier),
            colors = colors,
            enabled = state.enabled,
            shape = RoundedCornerShape(14.dp),
        ) {
            slots.leading(
                Modifier.size(ButtonDefaults.IconSize).clearAndSetSemantics {}
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = state.label)
        }
    }
}

@Composable
internal fun NativeThemeActionButtonV1(
    label: String,
    leading: @Composable (Modifier) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: NativeThemeActionButtonEmphasisV1 = NativeThemeActionButtonEmphasisV1.STANDARD,
) {
    NativeThemeComponentCatalogV1
        .requireImplementation(NativeThemeActionButtonContractV1.key)
        .renderer
        .render(
            state =
                NativeThemeActionButtonStateV1(
                    label = label,
                    enabled = enabled,
                    emphasis = emphasis,
                ),
            slots = NativeThemeActionButtonSlotsV1(leading = leading),
            onEvent = { event ->
                dispatchNativeThemeActionButtonEventV1(event, enabled, onActivate)
            },
            modifier = modifier,
        )
}

internal fun dispatchNativeThemeActionButtonEventV1(
    event: NativeThemeActionButtonEventV1,
    enabled: Boolean,
    onActivate: () -> Unit,
) {
    when (event) {
        NativeThemeActionButtonEventV1.Activate -> if (enabled) onActivate()
    }
}

package com.ai.assistance.operit.ui.theme.renderer.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentCatalogV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentRendererV1

internal object NativeThemeChoiceItemRendererV1 :
    NativeThemeComponentRendererV1<
        NativeThemeChoiceItemStateV1,
        NativeThemeChoiceItemEventV1,
        NativeThemeChoiceItemSlotsV1,
    > {
    @Composable
    override fun render(
        state: NativeThemeChoiceItemStateV1,
        slots: NativeThemeChoiceItemSlotsV1,
        onEvent: (NativeThemeChoiceItemEventV1) -> Unit,
        modifier: Modifier,
    ) {
        val shape = MaterialTheme.shapes.medium
        Card(
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .then(modifier)
                    .fillMaxWidth()
                    .semantics {
                        role = Role.RadioButton
                        selected = state.selected
                    },
            onClick = { onEvent(NativeThemeChoiceItemEventV1.Select) },
            enabled = state.enabled,
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (state.selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                ),
            border =
                if (state.selected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
            shape = shape,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    RadioButton(
                        selected = state.selected,
                        onClick = null,
                        enabled = state.enabled,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (state.selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    state.supportingText?.let { supportingText ->
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NativeThemeChoiceItemV1(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    NativeThemeComponentCatalogV1
        .requireImplementation(NativeThemeChoiceItemContractV1.key)
        .renderer
        .render(
            state =
                NativeThemeChoiceItemStateV1(
                    label = label,
                    supportingText = supportingText,
                    selected = selected,
                    enabled = enabled,
                ),
            slots = NativeThemeChoiceItemSlotsV1,
            onEvent = { event ->
                dispatchNativeThemeChoiceItemEventV1(event, enabled, onSelect)
            },
            modifier = modifier,
        )
}

internal fun dispatchNativeThemeChoiceItemEventV1(
    event: NativeThemeChoiceItemEventV1,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    when (event) {
        NativeThemeChoiceItemEventV1.Select -> if (enabled) onSelect()
    }
}

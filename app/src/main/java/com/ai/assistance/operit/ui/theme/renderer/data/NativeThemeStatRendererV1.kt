package com.ai.assistance.operit.ui.theme.renderer.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentCatalogV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentRendererV1

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
        Surface(
            modifier =
                modifier.clearAndSetSemantics {
                    contentDescription = state.label
                    stateDescription = state.value
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.primary
                ) {
                    slots.leading(Modifier.clearAndSetSemantics {})
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = state.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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

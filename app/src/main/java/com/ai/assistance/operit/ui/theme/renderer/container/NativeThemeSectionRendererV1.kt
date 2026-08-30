package com.ai.assistance.operit.ui.theme.renderer.container

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentCatalogV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentRendererV1

internal object NativeThemeSectionRendererV1 :
    NativeThemeComponentRendererV1<
        NativeThemeSectionStateV1,
        NativeThemeSectionEventV1,
        NativeThemeSectionSlotsV1,
    > {
    @Composable
    override fun render(
        state: NativeThemeSectionStateV1,
        slots: NativeThemeSectionSlotsV1,
        onEvent: (NativeThemeSectionEventV1) -> Unit,
        modifier: Modifier,
    ) {
        ElevatedCard(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        slots.leading(
                            Modifier.padding(10.dp).clearAndSetSemantics {}
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = state.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                slots.content()
            }
        }
    }
}

@Composable
internal fun NativeThemeSectionV1(
    title: String,
    description: String,
    leading: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NativeThemeComponentCatalogV1
        .requireImplementation(NativeThemeSectionContractV1.key)
        .renderer
        .render(
            state = NativeThemeSectionStateV1(title = title, description = description),
            slots = NativeThemeSectionSlotsV1(leading = leading, content = content),
            onEvent = {},
            modifier = modifier,
        )
}

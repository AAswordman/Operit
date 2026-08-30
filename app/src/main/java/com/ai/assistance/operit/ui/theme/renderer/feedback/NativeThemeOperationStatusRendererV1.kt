package com.ai.assistance.operit.ui.theme.renderer.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentCatalogV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentRendererV1

internal object NativeThemeOperationStatusRendererV1 :
    NativeThemeComponentRendererV1<
        NativeThemeOperationStatusStateV1,
        NativeThemeOperationStatusEventV1,
        NativeThemeOperationStatusSlotsV1,
    > {
    @Composable
    override fun render(
        state: NativeThemeOperationStatusStateV1,
        slots: NativeThemeOperationStatusSlotsV1,
        onEvent: (NativeThemeOperationStatusEventV1) -> Unit,
        modifier: Modifier,
    ) {
        when (state.kind) {
            NativeThemeOperationStatusKindV1.LOADING -> LoadingStatus(state, modifier)
            NativeThemeOperationStatusKindV1.SUCCESS,
            NativeThemeOperationStatusKindV1.ERROR -> ResultStatus(state, slots, modifier)
        }
    }

    @Composable
    private fun LoadingStatus(
        state: NativeThemeOperationStatusStateV1,
        modifier: Modifier,
    ) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).clearAndSetSemantics {}
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    @Composable
    private fun ResultStatus(
        state: NativeThemeOperationStatusStateV1,
        slots: NativeThemeOperationStatusSlotsV1,
        modifier: Modifier,
    ) {
        val isError = state.kind == NativeThemeOperationStatusKindV1.ERROR
        val containerColor =
            if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        val contentColor =
            if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

        Card(
            modifier =
                modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.2f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    slots.leading?.invoke(
                        Modifier.padding(end = 16.dp).clearAndSetSemantics {}
                    )
                }
                Column {
                    state.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NativeThemeOperationStatusV1(
    message: String,
    kind: NativeThemeOperationStatusKindV1,
    modifier: Modifier = Modifier,
    title: String? = null,
    leading: (@Composable (Modifier) -> Unit)? = null,
) {
    NativeThemeComponentCatalogV1
        .requireImplementation(NativeThemeOperationStatusContractV1.key)
        .renderer
        .render(
            state = NativeThemeOperationStatusStateV1(title = title, message = message, kind = kind),
            slots = NativeThemeOperationStatusSlotsV1(leading = leading),
            onEvent = {},
            modifier = modifier,
        )
}

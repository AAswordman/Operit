package com.ai.assistance.operit.ui.features.management.resources.hub

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.models.ResourceDownloadPhase
import kotlin.math.ceil

@Composable
internal fun HubTransferProgress(state: HubState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val installing = state.phase == ResourceDownloadPhase.INSTALLING
        if (state.total > 0 && !installing) {
            val fraction = (state.completed.toDouble() / state.total).coerceIn(0.0, 1.0)
            LinearProgressIndicator(progress = { fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("${resourceSize(state.completed)} / ${resourceSize(state.total)} · ${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        } else LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.phase != null) {
            val text = when (state.phase) {
                ResourceDownloadPhase.CONNECTING -> stringResource(R.string.hub_connecting)
                ResourceDownloadPhase.INSTALLING -> stringResource(R.string.hub_installing)
                ResourceDownloadPhase.DOWNLOADING -> {
                    val remaining = if (state.bytesPerSecond > 0 && state.total > state.completed)
                        ceil((state.total - state.completed) / state.bytesPerSecond).toLong() else null
                    val eta = when {
                        remaining == null -> stringResource(R.string.hub_eta_pending)
                        remaining >= 3600 -> stringResource(R.string.hub_eta_hours, remaining / 3600, remaining % 3600 / 60)
                        remaining >= 60 -> stringResource(R.string.hub_eta_minutes, remaining / 60, remaining % 60)
                        else -> stringResource(R.string.hub_eta_seconds, remaining)
                    }
                    "${resourceSize(state.bytesPerSecond.toLong().coerceAtLeast(0))}/s · $eta"
                }
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun HubActiveTask(state: HubState, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.label} · ${state.entries.firstOrNull { it.id == state.transferId }?.name.orEmpty()}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (!state.deleting) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        HubTransferProgress(state)
    }
}

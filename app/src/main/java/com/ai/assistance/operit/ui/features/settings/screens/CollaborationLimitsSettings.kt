package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.AgentProfileRepository
import com.ai.assistance.operit.core.agent.collaboration.CollaborationLimits

@Composable
internal fun CollaborationLimitsSettings(repository: AgentProfileRepository) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.subagent_v2_limits)) }
    if (!open) return
    val current by repository.collaborationLimits.collectAsState()
    var entries by remember {
        mutableStateOf(listOf(current.maxActive, current.maxDepth,
            current.minWaitMs, current.defaultWaitMs, current.maxWaitMs).map { it.toString() })
    }
    val parsed = runCatching {
        CollaborationLimits(entries[0].toInt(), entries[1].toInt(),
            entries[2].toLong(), entries[3].toLong(), entries[4].toLong()).validate()
    }.getOrNull()
    val labels = listOf(R.string.subagent_v2_max_active,
        R.string.subagent_v2_max_depth, R.string.subagent_v2_min_wait,
        R.string.subagent_v2_default_wait, R.string.subagent_v2_max_wait)
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.subagent_v2_limits)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.subagent_v2_limits_help))
                entries.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value, onValueChange = { next -> entries = entries.toMutableList().also { it[index] = next } },
                        label = { Text(stringResource(labels[index])) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                if (parsed == null) Text(stringResource(R.string.subagent_v2_invalid_limits), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = {
            repository.setCollaborationLimits(requireNotNull(parsed)); open = false
        }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

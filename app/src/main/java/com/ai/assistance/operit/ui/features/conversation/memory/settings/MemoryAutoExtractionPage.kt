package com.ai.assistance.operit.ui.features.conversation.memory.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.memory.extraction.MemoryAutoSaveScheduler
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import kotlin.math.roundToInt

@Composable
internal fun MemoryAutoExtractionPage(
    details: MemorySettingsDetails,
    intervalMinutes: Float,
    extractionRules: String,
    policy: MemorySettingsPolicy,
    onIntervalChange: (Float) -> Unit,
    onRulesChange: (String) -> Unit,
    onPolicyChange: (MemorySettingsPolicy) -> Unit,
    onRefresh: () -> Unit,
) {
    MemorySettingsSection(stringResource(R.string.memory_settings_queue)) {
        val queue = details.queue
        MemorySettingsHint(stringResource(
            R.string.memory_settings_queue_status,
            queue.pendingCandidates, queue.pendingChats, queue.processingCandidates, queue.failedCandidates,
        ))
        val minutesUntilNext = ((details.nextRunAtMs - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
        MemorySettingsHint(if (details.nextRunAtMs == 0L) {
            stringResource(R.string.memory_settings_next_run_unset)
        } else {
            stringResource(R.string.memory_settings_next_run, minutesUntilNext)
        })
        MemorySettingsHint(stringResource(R.string.memory_settings_auto_extract_hint, MemoryAutoSaveScheduler.MIN_TOTAL_CANDIDATES_TO_EXTRACT))
        if (queue.lastError.isNotBlank()) Text(queue.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refresh)) }
    }
    MemorySettingsSection(stringResource(R.string.memory_auto_save_settings_title)) {
        MemorySettingsSlider(
            title = stringResource(R.string.memory_auto_save_interval_minutes),
            value = intervalMinutes,
            valueText = stringResource(R.string.memory_settings_interval_value, intervalMinutes.roundToInt()),
            valueRange = MemorySearchSettingsPreferences.MIN_AUTO_SAVE_INTERVAL_MINUTES.toFloat()..
                MemorySearchSettingsPreferences.MAX_AUTO_SAVE_INTERVAL_MINUTES.toFloat(),
            steps = MemorySearchSettingsPreferences.MAX_AUTO_SAVE_INTERVAL_MINUTES -
                MemorySearchSettingsPreferences.MIN_AUTO_SAVE_INTERVAL_MINUTES - 1,
            onValueChange = onIntervalChange,
        )
        MemorySettingsHint(stringResource(
            R.string.memory_auto_save_interval_hint,
            MemorySearchSettingsPreferences.MIN_AUTO_SAVE_INTERVAL_MINUTES,
            MemorySearchSettingsPreferences.MAX_AUTO_SAVE_INTERVAL_MINUTES,
            MemorySearchSettingsPreferences.DEFAULT_AUTO_SAVE_INTERVAL_MINUTES,
        ))
        OutlinedTextField(
            value = extractionRules,
            onValueChange = onRulesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.memory_extraction_custom_rules)) },
            placeholder = { Text(stringResource(R.string.memory_extraction_custom_rules_placeholder)) },
            minLines = 4,
        )
    }
    MemorySettingsSection(stringResource(R.string.role_user_profile)) {
        MemorySettingsSwitch(
            title = stringResource(R.string.user_configuration_auto_update),
            description = stringResource(R.string.user_configuration_auto_update_desc),
            checked = policy.profileAutoUpdateEnabled,
            onCheckedChange = { onPolicyChange(policy.copy(profileAutoUpdateEnabled = it)) },
        )
        MemorySettingsSwitch(
            title = stringResource(R.string.user_configuration_lock),
            description = stringResource(R.string.memory_settings_profile_lock_hint),
            checked = policy.profileAutoUpdateLocked,
            onCheckedChange = { onPolicyChange(policy.copy(profileAutoUpdateLocked = it)) },
        )
    }
}

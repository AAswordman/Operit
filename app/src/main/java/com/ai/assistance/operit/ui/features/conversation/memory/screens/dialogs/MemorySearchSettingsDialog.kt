package com.ai.assistance.operit.ui.features.conversation.memory.screens.dialogs

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildManager
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildTimeScope
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.EmbeddingRebuildProgress
import com.ai.assistance.operit.data.model.MemorySearchConfig
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.common.dialogs.ContentSizedTabbedDialog
import com.ai.assistance.operit.ui.features.conversation.memory.settings.*
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemoryAutoExtractionPage
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemoryHistoryRebuildPage
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemoryRetrievalPage
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemorySettingsDetails
import com.ai.assistance.operit.ui.features.conversation.memory.settings.MemorySettingsPolicy

@Composable
fun MemorySearchSettingsDialog(
    spaceId: String,
    currentConfig: MemorySearchConfig,
    autoSaveIntervalMinutes: Int,
    memoryExtractionCustomRules: String,
    details: MemorySettingsDetails,
    embeddingModelName: String?,
    dimensionUsage: EmbeddingDimensionUsage,
    rebuildProgress: EmbeddingRebuildProgress,
    historyProgress: ChatMemoryRebuildManager.Progress,
    error: String?,
    isRebuilding: Boolean,
    isSaving: Boolean,
    initialQuery: String,
    onDismiss: () -> Unit,
    onSave: (MemorySearchConfig, Int, String, MemorySettingsPolicy) -> Unit,
    onConfigureEmbedding: () -> Unit,
    onRebuild: () -> Unit,
    onSimulateSearch: (MemorySearchConfig, String, Boolean, Double) -> Unit,
    onStartHistoryRebuild: (List<String>, Int, ChatMemoryRebuildTimeScope) -> Unit,
    onCancelHistoryRebuild: () -> Unit,
    onRefresh: () -> Unit,
) {
    // 首次读取成功后才建立草稿；刷新状态保留同一份草稿和历史选择。
    if (!details.loaded) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.memory_settings_title)) },
            text = {
                if (details.loading) CircularProgressIndicator()
                else details.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            confirmButton = {
                TextButton(onClick = onRefresh, enabled = !details.loading) { Text(stringResource(R.string.refresh)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    var config by remember(spaceId) { mutableStateOf(currentConfig) }
    var interval by remember(spaceId) { mutableStateOf(autoSaveIntervalMinutes) }
    var rules by remember(spaceId) { mutableStateOf(memoryExtractionCustomRules) }
    var policy by remember(spaceId) { mutableStateOf(details.policy) }
    var query by remember(spaceId) { mutableStateOf(initialQuery) }
    var threshold by remember(spaceId) { mutableFloatStateOf(MemoryRepository.DEFAULT_RELEVANCE_THRESHOLD.toFloat()) }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val dirty = config != currentConfig || interval != autoSaveIntervalMinutes || rules != memoryExtractionCustomRules || policy != details.policy
    fun leave(action: () -> Unit) {
        if (isSaving) return
        if (dirty) pendingLeave = action else action()
    }

    ContentSizedTabbedDialog(
        title = stringResource(R.string.memory_settings_title),
        tabTitles = listOf(
            stringResource(R.string.memory_settings_auto_extract_tab),
            stringResource(R.string.memory_settings_retrieval_tab),
            stringResource(R.string.memory_settings_history_tab),
        ),
        pagerState = pagerState,
        onDismiss = { leave(onDismiss) },
        actions = {
            IconButton(enabled = !isSaving && !details.loading, onClick = {
                config = MemorySearchConfig()
                interval = MemorySearchSettingsPreferences.DEFAULT_AUTO_SAVE_INTERVAL_MINUTES
                rules = MemorySearchSettingsPreferences.DEFAULT_MEMORY_EXTRACTION_CUSTOM_RULES
                policy = MemorySettingsPolicy()
                threshold = MemoryRepository.DEFAULT_RELEVANCE_THRESHOLD.toFloat()
            }) { Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.memory_search_reset_default)) }
            TextButton(onClick = { leave(onDismiss) }, enabled = !isSaving) { Text(stringResource(R.string.cancel)) }
            Button(
                onClick = { onSave(config.normalized(), interval, rules, policy) },
                enabled = !isSaving && !details.loading && details.error == null,
            ) { Text(stringResource(R.string.save)) }
        },
    ) { page ->
        Text(stringResource(R.string.memory_settings_space, details.spaceName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (details.loading || isSaving) LinearProgressIndicator()
        (details.error ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (details.loaded && details.hasLegacyEmbeddingConfiguration && embeddingModelName == null) {
            Text(
                stringResource(R.string.memory_embedding_legacy_binding_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { leave(onConfigureEmbedding) }) {
                Text(stringResource(R.string.functional_model_config_title))
            }
        }
        when (page) {
            0 -> MemoryAutoExtractionPage(
                details = details, intervalMinutes = interval.toFloat(), extractionRules = rules, policy = policy,
                onIntervalChange = { interval = kotlin.math.round(it).toInt() },
                onRulesChange = { rules = it }, onPolicyChange = { policy = it }, onRefresh = onRefresh,
            )
            1 -> MemoryRetrievalPage(
                config = config, policy = policy, savedPolicy = details.policy, embeddingModelName = embeddingModelName,
                dimensionUsage = dimensionUsage, rebuildProgress = rebuildProgress, isRebuilding = isRebuilding,
                query = query, threshold = threshold,
                onConfigChange = { config = it }, onPolicyChange = { policy = it },
                onQueryChange = { query = it }, onThresholdChange = { threshold = it },
                onConfigureEmbedding = { leave(onConfigureEmbedding) }, onRebuild = onRebuild,
                onSimulateSearch = { onSimulateSearch(config.normalized(), query, policy.cloudEmbeddingEnabled, threshold.toDouble()) },
            )
            2 -> MemoryHistoryRebuildPage(
                spaceId = spaceId, details = details, progress = historyProgress, onStart = onStartHistoryRebuild,
                onCancel = onCancelHistoryRebuild, onRefresh = onRefresh,
            )
        }
    }
    if (pendingLeave != null) {
        AlertDialog(
            onDismissRequest = { pendingLeave = null },
            title = { Text(stringResource(R.string.memory_settings_unsaved_title)) },
            text = { Text(stringResource(R.string.memory_settings_unsaved_message)) },
            confirmButton = { TextButton(onClick = {
                val action = pendingLeave
                pendingLeave = null
                action?.invoke()
            }) { Text(stringResource(R.string.user_md_discard)) } },
            dismissButton = { TextButton(onClick = { pendingLeave = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

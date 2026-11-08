package com.ai.assistance.operit.ui.features.management.settings.contextsummary

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.config.model.ModelConfigSelection
import com.ai.assistance.operit.data.config.model.getModelByIndex
import com.ai.assistance.operit.data.config.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.management.settings.DebouncedModelConfigAutoSaveEffect
import com.ai.assistance.operit.ui.features.management.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.management.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.management.settings.model.ModelFunctionAssignmentDialog
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.android.LocaleUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun ModelContextSummaryContent(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    showNotification: (String, OKToast.Kind) -> Unit,
) {
    ContextSummaryEditor(config, configManager, saveCoordinator, showNotification)
}

private class ContextSummaryInputException(message: String) : IllegalArgumentException(message)

@Composable
private fun ContextSummaryEditor(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    showNotification: (String, OKToast.Kind) -> Unit,
) {
    val context = LocalContext.current
    val useEnglish = !LocaleUtils.usesChineseContent(context)
    val incoming = ContextSummaryDraft.from(config, useEnglish)
    var draft by remember(config.id) { mutableStateOf(incoming) }
    var synchronizedDraft by remember(config.id) { mutableStateOf(incoming) }
    var contextError by remember(config.id) { mutableStateOf<String?>(null) }
    var summaryError by remember(config.id) { mutableStateOf<String?>(null) }
    var fullscreenEditor by remember(config.id) { mutableStateOf<FullscreenTextEditorRequest?>(null) }
    var editingSummaryModel by remember(config.id) { mutableStateOf(false) }
    val saveMutex = remember(config.id) { Mutex() }
    val background = MaterialTheme.colorScheme.surface
    LaunchedEffect(incoming) {
        draft = draft.mergeUnedited(synchronizedDraft, incoming)
        synchronizedDraft = incoming
    }

    suspend fun persistCurrentDraft() {
        saveMutex.withLock {
            // 在锁内读取最新草稿，避免排队中的保存覆盖继续输入或重置后的值。
            val snapshot = draft
            contextError = null
            summaryError = null
            val invalid = snapshot.validationError()
            if (invalid != null) {
                val message = context.getString(when (invalid) {
                    ContextSummaryValidation.CONTEXT_LENGTH -> R.string.model_config_error_valid_context_length
                    ContextSummaryValidation.MAX_CONTEXT_LENGTH -> R.string.model_config_error_valid_max_context_length
                    ContextSummaryValidation.CONTEXT_LIMIT -> R.string.model_workspace_summary_context_limit
                    ContextSummaryValidation.SUMMARY_THRESHOLD -> R.string.model_workspace_summary_threshold_error
                    ContextSummaryValidation.MESSAGE_COUNT -> R.string.model_config_error_valid_message_count
                })
                when (invalid) {
                    ContextSummaryValidation.CONTEXT_LENGTH,
                    ContextSummaryValidation.MAX_CONTEXT_LENGTH,
                    ContextSummaryValidation.CONTEXT_LIMIT -> contextError = message
                    else -> summaryError = message
                }
                throw ContextSummaryInputException(message)
            }
            configManager.updateContextSummarySettings(
                configId = config.id,
                contextLength = requireNotNull(snapshot.contextLength.toFloatOrNull()),
                maxContextLength = requireNotNull(snapshot.maxContextLength.toFloatOrNull()),
                enableSummary = snapshot.enableSummary,
                summaryTokenThreshold = snapshot.thresholdPercent.toFloatOrNull()?.div(100f),
                enableSummaryByMessageCount = snapshot.byMessageCount,
                summaryMessageCountThreshold = snapshot.messageCount.toIntOrNull(),
                summaryCustomRules = snapshot.rules,
                summarySectionOverrides = FunctionalPrompts.buildSummarySectionOverrides(snapshot.sections, useEnglish),
                enablePreviousSummaryReview = snapshot.previousReview,
                enableSummaryDialogueReview = snapshot.dialogueReview,
                summaryDialogueReviewTitle = snapshot.reviewTitle.trim(),
            )
        }
    }
    RegisterModelConfigSaveAction(saveCoordinator, "context-summary:${config.id}") { _ -> persistCurrentDraft() }
    DebouncedModelConfigAutoSaveEffect(
        effectKey = config.id,
        valueProvider = { draft },
        persist = { persistCurrentDraft() },
        onError = { error ->
            if (error !is ContextSummaryInputException) {
                AppLogger.e("ModelContextSummary", "保存上下文与总结设置失败", error)
                showNotification(context.getString(R.string.save_failed) + ": " + error.message.orEmpty(), OKToast.Kind.Error)
            }
        },
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.model_workspace_summary_scope, config.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RenderContextSummaryConfigSections(
            componentBackgroundColor = background,
            contextLengthInput = draft.contextLength,
            onContextLengthInputChange = { draft = draft.copy(contextLength = it); contextError = null },
            maxContextLengthInput = draft.maxContextLength,
            onMaxContextLengthInputChange = { draft = draft.copy(maxContextLength = it); contextError = null },
            contextError = contextError,
            enableSummary = draft.enableSummary,
            onEnableSummaryChange = { draft = draft.copy(enableSummary = it) },
            summaryTokenThresholdInput = draft.thresholdPercent,
            onSummaryTokenThresholdInputChange = { draft = draft.copy(thresholdPercent = it); summaryError = null },
            enableSummaryByMessageCount = draft.byMessageCount,
            onEnableSummaryByMessageCountChange = { draft = draft.copy(byMessageCount = it) },
            summaryMessageCountThresholdInput = draft.messageCount,
            onSummaryMessageCountThresholdInputChange = { draft = draft.copy(messageCount = it); summaryError = null },
            summaryCustomRulesInput = draft.rules,
            onSummaryCustomRulesInputChange = { draft = draft.copy(rules = it) },
            previousSummaryReviewEnabled = draft.previousReview,
            onPreviousSummaryReviewEnabledChange = { draft = draft.copy(previousReview = it) },
            dialogueReviewEnabled = draft.dialogueReview,
            onDialogueReviewEnabledChange = { draft = draft.copy(dialogueReview = it) },
            dialogueReviewTitleInput = draft.reviewTitle,
            onDialogueReviewTitleChange = { draft = draft.copy(reviewTitle = it) },
            summarySectionsInput = draft.sections,
            onSummarySectionsInputChange = { draft = draft.copy(sections = it) },
            onOpenFullscreenEditor = { title, value, change -> fullscreenEditor = FullscreenTextEditorRequest(title, value, change) },
            summaryError = summaryError,
            summaryModelContent = { SummaryModelLink(configManager) { editingSummaryModel = true } },
        )
    }
    if (editingSummaryModel) {
        ModelFunctionAssignmentDialog(FunctionType.SUMMARY, saveCoordinator) { editingSummaryModel = false }
    }
    fullscreenEditor?.let { request ->
        FullscreenSettingsTextEditor(request, onDismiss = { fullscreenEditor = null })
    }
}

@Composable
private fun SummaryModelLink(configManager: ModelConfigManager, onClick: () -> Unit) {
    val context = LocalContext.current
    val functions = remember { FunctionalConfigManager(context) }
    val mappings by functions.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    val selection by configManager.configSelectionFlow.collectAsState(initial = ModelConfigSelection())
    val mapping = mappings[FunctionType.SUMMARY]
    val bound = selection.allConfigs.find { it.id == mapping?.configId }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Summarize, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.model_workspace_summary_model), style = MaterialTheme.typography.titleSmall)
                if (bound != null && mapping != null) {
                    Text(bound.name + " · " + getModelByIndex(bound.modelName, getValidModelIndex(bound.modelName, mapping.modelIndex)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.model_workspace_summary_model_scope), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
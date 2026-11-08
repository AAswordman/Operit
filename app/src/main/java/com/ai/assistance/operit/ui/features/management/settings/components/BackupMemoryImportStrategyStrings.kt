package com.ai.assistance.operit.ui.features.management.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

internal data class MemoryImportStrategyTexts(
    val question: String,
    val skipTitle: String,
    val skipDesc: String,
    val updateTitle: String,
    val updateDesc: String,
    val createNewTitle: String,
    val createNewDesc: String
)

@Composable
internal fun rememberMemoryImportStrategyTexts(): MemoryImportStrategyTexts {
    return MemoryImportStrategyTexts(
        question = stringResource(R.string.backup_memory_import_strategy_question),
        skipTitle = stringResource(R.string.backup_memory_import_strategy_skip_title),
        skipDesc = stringResource(R.string.backup_memory_import_strategy_skip_desc),
        updateTitle = stringResource(R.string.backup_memory_import_strategy_update_title),
        updateDesc = stringResource(R.string.backup_memory_import_strategy_update_desc),
        createNewTitle = stringResource(R.string.backup_memory_import_strategy_create_new_title),
        createNewDesc = stringResource(R.string.backup_memory_import_strategy_create_new_desc)
    )
}

package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonEmphasisV1
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusKindV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusV1
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCardManagementCard(
    totalCharacterCardCount: Int,
    operationState: CharacterCardOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val characterCardsTitle = stringResource(R.string.backup_character_cards_title)
    NativeThemeSectionV1(
        title = characterCardsTitle,
        description = stringResource(R.string.backup_character_cards_subtitle),
        leading = { modifier ->
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = modifier,
            )
        },
    ) {

            Text(
                text = stringResource(
                    R.string.backup_character_cards_current_count,
                    totalCharacterCardCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_export),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onExport,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_import),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onImport,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            AnimatedVisibility(visible = operationState != CharacterCardOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        CharacterCardOperation.EXPORTING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_exporting, characterCardsTitle),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        CharacterCardOperation.IMPORTING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_importing, characterCardsTitle),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        CharacterCardOperation.EXPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_export_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        CharacterCardOperation.IMPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_import_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        CharacterCardOperation.FAILED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_operation_failed),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.ERROR,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        else -> {}
                    }
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataManagementCard(
    totalChatCount: Int,
    operationState: ChatHistoryOperation,
    operationMessage: String,
    isLongTextExport: Boolean,
    longTextExportProgress: Float,
    longTextExportProcessedCharacters: Long,
    longTextExportTotalCharacters: Long,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    val chatTitle = stringResource(R.string.backup_chat_history)

    NativeThemeSectionV1(
        title = chatTitle,
        description = stringResource(R.string.backup_chat_history_subtitle),
        leading = { modifier ->
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = modifier,
            )
        },
    ) {

            Text(
                text = stringResource(R.string.backup_chat_current_count, totalChatCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_export),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onExport,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_import),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onImport,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_delete_all),
                    leading = { modifier ->
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onDelete,
                    emphasis = NativeThemeActionButtonEmphasisV1.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(visible = operationState != ChatHistoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        ChatHistoryOperation.EXPORTING ->
                            if (isLongTextExport) {
                                TextExportProgressView(
                                    progress = longTextExportProgress,
                                    processedCharacters = longTextExportProcessedCharacters,
                                    totalCharacters = longTextExportTotalCharacters,
                                )
                            } else {
                                NativeThemeOperationStatusV1(
                                    message = stringResource(R.string.backup_exporting, chatTitle),
                                    kind = NativeThemeOperationStatusKindV1.LOADING,
                                )
                            }

                        ChatHistoryOperation.IMPORTING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_importing, chatTitle),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        ChatHistoryOperation.DELETING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_deleting),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        ChatHistoryOperation.EXPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_export_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        ChatHistoryOperation.IMPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_import_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        ChatHistoryOperation.DELETED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_delete_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        ChatHistoryOperation.FAILED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_operation_failed),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.ERROR,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        else -> {}
                    }
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryManagementCard(
    totalMemoryCount: Int,
    totalLinkCount: Int,
    operationState: MemoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val memoryTitle = stringResource(R.string.backup_memory_library)

    NativeThemeSectionV1(
        title = memoryTitle,
        description = stringResource(R.string.backup_memory_library_subtitle),
        leading = { modifier ->
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = modifier,
            )
        },
    ) {

            Text(
                text = stringResource(R.string.backup_memory_current_count, totalMemoryCount, totalLinkCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_export),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onExport,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_import),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onImport,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            AnimatedVisibility(visible = operationState != MemoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        MemoryOperation.EXPORTING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_exporting, memoryTitle),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        MemoryOperation.IMPORTING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_importing, memoryTitle),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        MemoryOperation.EXPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_export_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        MemoryOperation.IMPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_import_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        MemoryOperation.FAILED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_operation_failed),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.ERROR,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        else -> {}
                    }
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelConfigManagementCard(
    totalConfigCount: Int,
    operationState: ModelConfigOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val modelConfigTitle = stringResource(R.string.backup_model_config)

    NativeThemeSectionV1(
        title = modelConfigTitle,
        description = stringResource(R.string.backup_model_config_subtitle),
        leading = { modifier ->
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = modifier,
            )
        },
    ) {

            Text(
                text = stringResource(R.string.backup_model_config_current_count, totalConfigCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_export),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onExport,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NativeThemeActionButtonV1(
                    label = stringResource(R.string.backup_import),
                    leading = { modifier ->
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = modifier)
                    },
                    onActivate = onImport,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            AnimatedVisibility(visible = operationState != ModelConfigOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        ModelConfigOperation.EXPORTING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_exporting, modelConfigTitle),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        ModelConfigOperation.IMPORTING ->
                            NativeThemeOperationStatusV1(
                                message = stringResource(R.string.backup_importing, modelConfigTitle),
                                kind = NativeThemeOperationStatusKindV1.LOADING,
                            )

                        ModelConfigOperation.EXPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_export_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        ModelConfigOperation.IMPORTED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_import_success),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.SUCCESS,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        ModelConfigOperation.FAILED ->
                            NativeThemeOperationStatusV1(
                                title = stringResource(R.string.backup_operation_failed),
                                message = operationMessage,
                                kind = NativeThemeOperationStatusKindV1.ERROR,
                                leading = { modifier ->
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = modifier,
                                    )
                                },
                            )

                        else -> {}
                    }
                }
            }
    }
}

@Composable
private fun TextExportProgressView(
    progress: Float,
    processedCharacters: Long,
    totalCharacters: Long,
) {
    val percentage = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.backup_text_export_progress,
                percentage,
                processedCharacters,
                totalCharacters,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun FaqCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_faq_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.backup_faq_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.HorizontalDivider()
            FaqItem(
                question = stringResource(R.string.backup_faq_why),
                answer = stringResource(R.string.backup_faq_why_answer)
            )
            FaqItem(
                question = stringResource(R.string.backup_faq_where),
                answer = stringResource(R.string.backup_faq_where_answer)
            )
            FaqItem(
                question = stringResource(R.string.backup_faq_duplicate),
                answer = stringResource(R.string.backup_faq_duplicate_answer)
            )
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = answer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

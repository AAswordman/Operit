package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Icon
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.WorkspaceBackupManager
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.compactDialogHeightOrWrapContent
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.rememberCompactDialogMetrics

enum class WorkspaceChangeConfirmMode {
    ROLLBACK,
    EDIT_AND_RESEND
}

@Composable
fun WorkspaceChangeConfirmDialog(
    mode: WorkspaceChangeConfirmMode,
    changes: List<WorkspaceBackupManager.WorkspaceFileChange>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogMetrics = rememberCompactDialogMetrics()
    val previewMaxHeight = if (dialogMetrics.isCompactHeight) 120.dp else 260.dp
    val cardModifier =
        Modifier
            .fillMaxWidth(0.95f)
            .compactDialogHeightOrWrapContent(dialogMetrics)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (mode) {
                            WorkspaceChangeConfirmMode.ROLLBACK -> stringResource(id = R.string.confirm_rollback_title)
                            WorkspaceChangeConfirmMode.EDIT_AND_RESEND -> stringResource(id = R.string.confirm_edit_and_resend_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when (mode) {
                        WorkspaceChangeConfirmMode.ROLLBACK -> stringResource(id = R.string.confirm_rollback_message)
                        WorkspaceChangeConfirmMode.EDIT_AND_RESEND -> stringResource(id = R.string.confirm_edit_and_resend_message)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (changes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.workspace_changes_preview_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = previewMaxHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        ) {
                            changes.forEach { change ->
                                val (statusLabel, statusColor) = when (change.changeType) {
                                    WorkspaceBackupManager.ChangeType.ADDED -> "A" to MaterialTheme.colorScheme.primary
                                    WorkspaceBackupManager.ChangeType.DELETED -> "D" to MaterialTheme.colorScheme.error
                                    WorkspaceBackupManager.ChangeType.MODIFIED -> "M" to MaterialTheme.colorScheme.tertiary
                                }

                                val fileName = change.path.substringAfterLast('/', change.path)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = fileName,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    val changeTextColor = when (change.changeType) {
                                        WorkspaceBackupManager.ChangeType.ADDED -> MaterialTheme.colorScheme.primary
                                        WorkspaceBackupManager.ChangeType.DELETED -> MaterialTheme.colorScheme.error
                                        WorkspaceBackupManager.ChangeType.MODIFIED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val changeText = when (change.changeType) {
                                        WorkspaceBackupManager.ChangeType.ADDED -> "+${change.changedLines}"
                                        WorkspaceBackupManager.ChangeType.DELETED -> "-${change.changedLines}"
                                        WorkspaceBackupManager.ChangeType.MODIFIED -> "±${change.changedLines}"
                                    }

                                    Text(
                                        text = changeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = changeTextColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = when (mode) {
                                WorkspaceChangeConfirmMode.ROLLBACK -> stringResource(id = R.string.confirm_rollback_confirm)
                                WorkspaceChangeConfirmMode.EDIT_AND_RESEND -> stringResource(id = R.string.confirm_edit_and_resend_confirm)
                            }
                        )
                    }
                }
            }
        }
    }
}

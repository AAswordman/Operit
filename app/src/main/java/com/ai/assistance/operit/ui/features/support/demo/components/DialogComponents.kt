package com.ai.assistance.operit.ui.features.support.demo.components

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** Command result dialog that shows command execution output with scrollable content */
@Composable
fun CommandResultDialog(
        showDialog: Boolean,
        onDismiss: () -> Unit,
        title: String,
        content: String,
        context: Context,
        isExecuting: Boolean = false,
        showButtons: Boolean = true,
        allowCopy: Boolean = true
) {
    if (showDialog) {
        AlertDialog(
                onDismissRequest = onDismiss,
                title = { 
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (isExecuting) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 150.dp, max = 350.dp)
                            .fillMaxWidth()
                    ) {
                        // 使用lazyColumn替代简单的垂直滚动更流畅
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                if (allowCopy) {
                                    SelectionContainer {
                                        Text(
                                            text = content,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = content,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (showButtons && !isExecuting) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_confirm)) }
                    }
                },
                dismissButton = {
                    if (showButtons && !isExecuting && allowCopy) {
                        TextButton(
                                onClick = {
                                    try {
                                        val clipboard =
                                                context.getSystemService(
                                                        Context.CLIPBOARD_SERVICE
                                                ) as
                                                        android.content.ClipboardManager
                                        val clip =
                                                android.content.ClipData.newPlainText(
                                                        context.getString(R.string.dialog_command_result),
                                                        content
                                                )
                                        clipboard.setPrimaryClip(clip)
                                        OKToast.success(context, context.getString(R.string.dialog_copied_to_clipboard), OKToast.LENGTH_SHORT)
                                                .show()
                                    } catch (e: Exception) {
                                        OKToast.error(
                                                        context,
                                                        context.getString(R.string.dialog_copy_failed, e.message ?: ""),
                                                        OKToast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                        ) { Text(stringResource(R.string.dialog_copy)) }
                    }
                },
                properties =
                        DialogProperties(
                                dismissOnBackPress = showButtons && !isExecuting,
                                dismissOnClickOutside = showButtons && !isExecuting,
                                usePlatformDefaultWidth = false
                        ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(0.95f)
        )
    }
}

/** Error card that displays when a feature requires permissions that aren't granted */
@Composable
fun FeatureErrorCard(message: String) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                    )
    ) {
        Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp)
        )
    }
}

/** Sample commands card that displays a list of example commands the user can tap */
@Composable
fun SampleCommandsCard(commands: List<Pair<String, String>>, onCommandSelected: (String) -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dialog_select_sample_command), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            commands.forEach { (command, description) ->
                OutlinedButton(
                        onClick = { onCommandSelected(command) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = androidx.compose.ui.Alignment.Start
                    ) {
                        Text(description, style = MaterialTheme.typography.bodyMedium)
                        Text(command, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

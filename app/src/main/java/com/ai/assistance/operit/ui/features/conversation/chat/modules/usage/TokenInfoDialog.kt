package com.ai.assistance.operit.ui.features.conversation.chat.modules.usage

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/**
 * Dialog that explains the token usage policy before redirecting to the token configuration screen.
 *
 * @param onDismiss Callback when the dialog is dismissed
 * @param onConfirm Callback when user confirms and wants to navigate to token page
 */
@Composable
fun TokenInfoDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
        Dialog(
                onDismissRequest = onDismiss,
                properties =
                        DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
                Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                        text = stringResource(id = R.string.token_info_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                        text = stringResource(id = R.string.token_info_content),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        TextButton(
                                                onClick = onDismiss,
                                                modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(id = R.string.token_info_cancel)) }

                                        Button(
                                                onClick = onConfirm,
                                                modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(id = R.string.token_info_confirm)) }
                                }
                        }
                }
        }
}

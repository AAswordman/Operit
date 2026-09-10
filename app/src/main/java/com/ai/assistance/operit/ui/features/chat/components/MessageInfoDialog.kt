package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageInfoDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val unavailable = stringResource(R.string.message_info_unavailable)
    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    }
    val displaySender =
        when (message.sender) {
            "user" -> stringResource(R.string.message_role_user)
            "ai" -> "AI"
            else -> message.sender
        }

    fun formatTimestamp(value: Long): String {
        return if (value > 0L) {
            dateFormatter.format(Date(value))
        } else {
            unavailable
        }
    }

    fun formatDuration(value: Long): String {
        return if (value > 0L) {
            if (value >= 1000L) {
                String.format(Locale.getDefault(), "%.2f s (%d ms)", value / 1000.0, value)
            } else {
                "$value ms"
            }
        } else {
            "0 ms"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.message_info_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MessageInfoCard {
                    MessageInfoTwoColumnRow(
                        leftLabel = stringResource(R.string.message_info_sender),
                        leftValue = displaySender,
                        rightLabel = stringResource(R.string.message_info_role_name),
                        rightValue = message.roleName.ifBlank { unavailable }
                    )
                    MessageInfoTwoColumnRow(
                        leftLabel = stringResource(R.string.message_info_provider),
                        leftValue = message.provider.ifBlank { unavailable },
                        rightLabel = stringResource(R.string.message_info_model_name),
                        rightValue = message.modelName.ifBlank { unavailable }
                    )
                }

                MessageInfoCard {
                    MessageInfoTwoColumnRow(
                        leftLabel = stringResource(R.string.message_info_message_time),
                        leftValue = formatTimestamp(message.timestamp),
                        rightLabel = stringResource(R.string.message_info_sent_at),
                        rightValue = formatTimestamp(message.sentAt)
                    )
                    MessageInfoTwoColumnRow(
                        leftLabel = stringResource(R.string.message_info_wait_duration),
                        leftValue = formatDuration(message.waitDurationMs),
                        rightLabel = stringResource(R.string.message_info_output_duration),
                        rightValue = formatDuration(message.outputDurationMs)
                    )
                }

                MessageInfoCard {
                    MessageInfoTokenRow(
                        inputTokens = message.inputTokens,
                        outputTokens = message.outputTokens,
                        cachedInputTokens = message.cachedInputTokens
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.floating_close))
            }
        }
    )
}

@Composable
private fun MessageInfoCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun MessageInfoTwoColumnRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = leftValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = rightValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MessageInfoTokenRow(
    inputTokens: Long,
    outputTokens: Long,
    cachedInputTokens: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.message_info_token_summary),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text =
                buildString {
                    append(stringResource(R.string.message_info_token_input_short))
                    append(" ")
                    append(inputTokens)
                    append("  ")
                    append(stringResource(R.string.message_info_token_output_short))
                    append(" ")
                    append(outputTokens)
                    append("  ")
                    append(stringResource(R.string.message_info_token_cached_short))
                    append(" ")
                    append(cachedInputTokens)
                    append("  ")
                    append(stringResource(R.string.message_info_token_hit_rate_short))
                    append(" ")
                    append(formatCacheHitRate(cachedInputTokens, inputTokens))
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

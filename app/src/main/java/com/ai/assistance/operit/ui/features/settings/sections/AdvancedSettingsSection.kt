package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.DebouncedModelConfigAutoSaveEffect

@Composable
fun AdvancedSettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    showNotification: (String) -> Unit
) {
    val context = LocalContext.current
    var showRequestQueueControls by remember(config.id) { mutableStateOf(false) }
    var requestLimitPerMinuteInput by
        remember(config.id) {
            mutableStateOf(
                config.requestLimitPerMinute
                    .takeIf { it > 0 }
                    ?.toString()
                    .orEmpty()
            )
        }
    var maxConcurrentRequestsInput by
        remember(config.id) {
            mutableStateOf(
                config.maxConcurrentRequests
                    .takeIf { it > 0 }
                    ?.toString()
                    .orEmpty()
            )
        }

    data class RequestQueueControlState(
        val requestLimitPerMinute: Int,
        val maxConcurrentRequests: Int
    )

    fun currentQueueState() =
        RequestQueueControlState(
            requestLimitPerMinute = requestLimitPerMinuteInput.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            maxConcurrentRequests = maxConcurrentRequestsInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
        )

    DebouncedModelConfigAutoSaveEffect(
        effectKey = config.id,
        valueProvider = { currentQueueState() },
        persist = { state ->
            configManager.updateRequestQueueSettings(
                configId = config.id,
                requestLimitPerMinute = state.requestLimitPerMinute,
                maxConcurrentRequests = state.maxConcurrentRequests
            )
            EnhancedAIService.refreshAllServices(configManager.appContext)
        },
        onError = { e ->
            showNotification(context.getString(R.string.save_failed) + ": " + (e.message ?: ""))
        }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.advanced_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRequestQueueControls = !showRequestQueueControls },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.request_queue_controls),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.request_queue_controls_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector =
                                if (showRequestQueueControls) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(
                        visible = showRequestQueueControls,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsTextField(
                                title = stringResource(R.string.request_limit_per_minute),
                                subtitle = stringResource(R.string.request_limit_per_minute_desc),
                                value = requestLimitPerMinuteInput,
                                onValueChange = {
                                    requestLimitPerMinuteInput = it.filter { ch -> ch.isDigit() }
                                },
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                )
                            )

                            SettingsTextField(
                                title = stringResource(R.string.max_concurrent_requests),
                                subtitle = stringResource(R.string.max_concurrent_requests_desc),
                                value = maxConcurrentRequestsInput,
                                onValueChange = {
                                    maxConcurrentRequestsInput = it.filter { ch -> ch.isDigit() }
                                },
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

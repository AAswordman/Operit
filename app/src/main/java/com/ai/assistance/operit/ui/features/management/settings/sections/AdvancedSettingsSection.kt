package com.ai.assistance.operit.ui.features.management.settings.sections

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.management.settings.DebouncedModelConfigAutoSaveEffect
import com.ai.assistance.operit.ui.features.management.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.management.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceSection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun AdvancedSettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    showNotification: (String, OKToast.Kind) -> Unit
) {
    val context = LocalContext.current
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

    val saveMutex = remember(config.id) { Mutex() }
    suspend fun persistQueue(state: RequestQueueControlState) {
        saveMutex.withLock {
            configManager.updateRequestQueueSettings(config.id, state.requestLimitPerMinute, state.maxConcurrentRequests)
            EnhancedAIService.refreshAllServices(configManager.appContext)
        }
    }
    RegisterModelConfigSaveAction(saveCoordinator, "request-queue:${config.id}") { _ ->
        persistQueue(currentQueueState())
    }
    DebouncedModelConfigAutoSaveEffect(
        effectKey = config.id,
        valueProvider = { currentQueueState() },
        persist = { persistQueue(it) },
        onError = { e -> showNotification(context.getString(R.string.save_failed) + ": " + e.message.orEmpty(), OKToast.Kind.Error) },
    )
    ModelWorkspaceSection(
        title = stringResource(R.string.request_queue_controls),
        subtitle = stringResource(R.string.request_queue_controls_desc),
        stateKey = "queue:${config.id}",
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

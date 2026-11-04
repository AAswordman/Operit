package com.ai.assistance.operit.ui.features.management.settings.keypool

import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.keypool.ApiKeyCooldownStore
import com.ai.assistance.operit.api.chat.keypool.ApiKeyInfo
import kotlinx.coroutines.delay

internal data class ApiKeyCooldownRemaining(val model: String, val seconds: Long)

/** 每秒重读当前内存状态，同时保留全局与各模型的冷却范围。 */
@Composable
internal fun rememberApiKeyCooldowns(
    configId: String,
    keys: List<ApiKeyInfo>,
): Map<String, List<ApiKeyCooldownRemaining>> {
    val cooldowns by produceState<Map<String, List<ApiKeyCooldownRemaining>>>(emptyMap(), configId, keys) {
        if (keys.isEmpty()) return@produceState
        val enabledIds = keys.filter { it.isEnabled }.map { it.id }.toSet()
        while (true) {
            val nowMs = System.currentTimeMillis()
            value = ApiKeyCooldownStore.activeCooldowns(configId, nowMs)
                .filter { it.keyId in enabledIds }
                .groupBy { it.keyId }
                .mapValues { (_, slots) ->
                    slots.sortedBy { it.model }.map {
                        ApiKeyCooldownRemaining(it.model, (it.untilMs - nowMs + 999L) / 1000L)
                    }
                }
            delay(1_000L)
        }
    }
    return cooldowns
}

@Composable
internal fun ApiKeyCooldownLabels(cooldowns: List<ApiKeyCooldownRemaining>) {
    cooldowns.forEach { cooldown ->
        Text(
            text = if (cooldown.model.isEmpty()) {
                stringResource(R.string.api_key_pool_cooling_remaining, cooldown.seconds)
            } else {
                stringResource(R.string.api_key_pool_model_cooling_remaining, cooldown.model, cooldown.seconds)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

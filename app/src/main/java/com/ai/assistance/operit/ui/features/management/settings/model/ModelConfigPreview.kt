package com.ai.assistance.operit.ui.features.management.settings.model

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.config.model.ModelConfigData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private val previewJson = Json { prettyPrint = true }

/** 只展示配置结构，认证内容在构造预览时遮罩，不改变原始配置。 */
internal fun buildModelConfigPreview(config: ModelConfigData): String {
    val preview = buildJsonObject {
        put("name", config.name)
        put("provider", config.apiProviderTypeId)
        put("endpoint", config.apiEndpoint)
        put("models", config.modelName)
        putJsonObject("authentication") {
            put("apiKey", if (config.apiKey.isBlank()) "" else "••••••••")
            put("useMultipleApiKeys", config.useMultipleApiKeys)
            put("keyRotationMode", config.keyRotationMode)
            put("keys", buildJsonArray {
                config.apiKeyPool.forEach { key ->
                    add(buildJsonObject {
                        put("name", key.name)
                        put("enabled", key.isEnabled)
                        put("key", "••••••••")
                    })
                }
            })
        }
        putJsonObject("capabilities") {
            put("toolCall", config.enableToolCall)
            put("stream", config.enableStreamOutput)
            put("images", config.enableDirectImageProcessing)
            put("audio", config.enableDirectAudioProcessing)
            put("video", config.enableDirectVideoProcessing)
        }
        putJsonObject("parameters") {
            if (config.maxTokensEnabled) put("max_tokens", config.maxTokens)
            if (config.temperatureEnabled) put("temperature", config.temperature)
            if (config.topPEnabled) put("top_p", config.topP)
            if (config.topKEnabled) put("top_k", config.topK)
            if (config.presencePenaltyEnabled) put("presence_penalty", config.presencePenalty)
            if (config.frequencyPenaltyEnabled) put("frequency_penalty", config.frequencyPenalty)
            if (config.repetitionPenaltyEnabled) put("repetition_penalty", config.repetitionPenalty)
            put("custom", previewJson.parseToJsonElement(config.customParameters))
        }
        put("thinkingConfigurations", previewJson.parseToJsonElement(config.thinkingConfigurations))
        put("thinkingOptionId", config.thinkingOptionId)
        putJsonObject("requestQueue") {
            put("requestLimitPerMinute", config.requestLimitPerMinute)
            put("maxConcurrentRequests", config.maxConcurrentRequests)
        }
        putJsonObject("headers") {
            previewJson.parseToJsonElement(config.customHeaders).jsonObject.keys.forEach { name ->
                put(name, "••••••••")
            }
        }
    }
    return previewJson.encodeToString(JsonObject.serializer(), preview)
}

@Composable
fun ModelConfigPreviewDialog(preview: String, onDismiss: () -> Unit) {
    ModelWorkspaceDialog(title = stringResource(R.string.model_workspace_config_preview), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.model_workspace_config_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                preview,
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
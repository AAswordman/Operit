package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.api.chat.keypool.ApiKeyFormatValidator
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.util.logging.AppLogger
import org.json.JSONException
import org.json.JSONObject

/** 发送前检查实际绑定的配置，避免把可直接说明的缺失项提交到网络请求。 */
internal fun chatModelConfigurationError(context: Context, config: ModelConfigData): String? {
    // 插件提供商自行定义配置契约；内置本地与账号认证提供商沿用已有的密钥要求。
    val provider = ApiProviderType.fromProviderTypeId(config.apiProviderTypeId) ?: return null
    if (config.modelName.split(',').none { it.isNotBlank() }) {
        return context.getString(R.string.chat_model_configuration_missing_model)
    }
    if (ApiProviderConfigs.requiresApiKey(provider, config.apiEndpoint) &&
        !ApiKeyFormatValidator.hasUsableKey(config) &&
        !hasCustomAuthentication(config.customHeaders)
    ) {
        return context.getString(R.string.chat_model_configuration_invalid_key)
    }
    return null
}

private fun hasCustomAuthentication(customHeaders: String): Boolean {
    val headers = try {
        JSONObject(customHeaders)
    } catch (error: JSONException) {
        AppLogger.w("ChatModelConfiguration", "无法解析模型自定义认证请求头", error)
        return false
    }
    return headers.keys().asSequence().any { name ->
        val isAuthentication = name.equals("Authorization", ignoreCase = true) ||
            name.equals("x-api-key", ignoreCase = true) || name.equals("api-key", ignoreCase = true)
        isAuthentication && headers.optString(name).isNotBlank()
    }
}

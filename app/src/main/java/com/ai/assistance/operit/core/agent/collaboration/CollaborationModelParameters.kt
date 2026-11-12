package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityWireValue
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.getModelByIndex

internal object CollaborationModelParameters {
    fun overrideConfig(config: ModelConfigData, modelIndex: Int, effort: String?): ModelConfigData {
        if (effort == null) return config
        val model = getModelByIndex(config.modelName, modelIndex)
        val mapping = ThinkingQualityMappingRegistry.resolve(
            config.apiProviderTypeId, model, config.apiEndpoint, config.thinkingConfigurations,
        )
        val option = mapping.options.firstOrNull { option ->
            option.id == effort ||
                (option.wireValue as? ThinkingQualityWireValue.Text)?.value == effort
        }
        requireNotNull(option) {
            "reasoning_effort '$effort' is not configured for $model. " +
                "Use a supported thinking option or omit reasoning_effort to inherit the model configuration."
        }
        return config.copy(thinkingOptionId = option.id)
    }
}

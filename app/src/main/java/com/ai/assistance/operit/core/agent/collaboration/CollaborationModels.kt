package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.getModelList

/** Resolve readable model choices to the exact configuration and model index. */
internal object CollaborationModels {
    data class Choice(
        val selector: String,
        val configId: String,
        val modelIndex: Int,
        val provider: String,
    )

    fun choices(configs: List<ModelConfigSummary>): List<Choice> {
        val models = configs.flatMap { config ->
            val configName = config.name.trim().ifEmpty {
                config.apiProviderTypeId.ifBlank { config.apiProviderType.name }
            }
            getModelList(config.modelName).mapIndexed { index, name ->
                Choice("$configName / $name", config.id, index, config.apiProviderTypeId)
            }
        }
        val counts = models.groupingBy { it.selector }.eachCount()
        val qualified = models.map { choice ->
            if (counts.getValue(choice.selector) == 1) choice else choice.copy(
                selector = "${choice.selector} [${choice.provider}]",
            )
        }
        val qualifiedCounts = qualified.groupingBy { it.selector }.eachCount()
        return qualified.map { choice ->
            if (qualifiedCounts.getValue(choice.selector) == 1) choice else choice.copy(
                selector = "${choice.selector} [${choice.configId}; ${choice.modelIndex + 1}]",
            )
        }
    }

    fun resolve(selector: String, configs: List<ModelConfigSummary>): Choice {
        val matching = choices(configs).filter { it.selector == selector }
        require(matching.size == 1) {
            "Unknown or ambiguous model selection. Call list_agent_models and copy its model value."
        }
        return matching.single()
    }
}

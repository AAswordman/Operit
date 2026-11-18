package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityWireValue
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityControl
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.getModelByIndex

internal object CollaborationModelParameters {
    private val levels = listOf("minimal", "low", "medium", "high", "xhigh", "max", "ultra")

    fun thinkingEnabled(inherited: Boolean, effort: String?): Boolean =
        effort?.let { !it.equals("none", ignoreCase = true) } ?: inherited

    fun overrideConfig(config: ModelConfigData, modelIndex: Int, effort: String?): ModelConfigData {
        if (effort == null) return config
        val model = getModelByIndex(config.modelName, modelIndex)
        val mapping = ThinkingQualityMappingRegistry.resolve(
            config.apiProviderTypeId, model, config.apiEndpoint, config.thinkingConfigurations,
        )
        require(mapping.control != ThinkingQualityControl.UNSUPPORTED) {
            "reasoning_effort is not configured for $model"
        }
        if (effort.equals("none", ignoreCase = true)) {
            require(!mapping.reasoningRequired) { "$model requires thinking and cannot use reasoning_effort=none" }
            require(mapping.disabledActions.isNotEmpty()) { "$model has no configured action to disable thinking" }
            return config.copy(thinkingConfigurations = explicitActions(config.thinkingConfigurations))
        }
        if (mapping.control == ThinkingQualityControl.TOGGLE_ONLY) {
            return config.copy(thinkingConfigurations = explicitActions(config.thinkingConfigurations))
        }
        val exact = mapping.options.firstOrNull { option ->
            option.id.equals(effort, ignoreCase = true) ||
                (option.wireValue as? ThinkingQualityWireValue.Text)?.value?.equals(effort, ignoreCase = true) == true
        }
        val requested = levels.indexOf(effort.lowercase(java.util.Locale.ROOT))
        val named = mapping.options.mapNotNull { option ->
            val name = (option.wireValue as? ThinkingQualityWireValue.Text)?.value ?: option.id
            levels.indexOf(name.lowercase(java.util.Locale.ROOT)).takeIf { it >= 0 }?.let { it to option }
        }.sortedBy { it.first }
        // Select only declared options. Higher requests clamp to the model's highest level.
        val option = exact ?: if (requested >= 0 && named.isNotEmpty()) {
            named.firstOrNull { it.first >= requested }?.second ?: named.last().second
        } else if (requested >= 0 && mapping.options.isNotEmpty() &&
            mapping.options.all { it.wireValue is ThinkingQualityWireValue.Number }
        ) {
            val budgets = mapping.options.sortedBy { (it.wireValue as ThinkingQualityWireValue.Number).value }
            budgets[requested.coerceAtMost(budgets.lastIndex)]
        } else null
        requireNotNull(option) {
            "reasoning_effort '$effort' is not configured for $model. " +
                "Use a supported thinking option or omit reasoning_effort to inherit the model configuration."
        }
        return config.copy(thinkingOptionId = option.id,
            thinkingConfigurations = explicitActions(config.thinkingConfigurations))
    }

    /** Explicit per-turn settings take precedence over custom parameters; the saved rules stay unchanged. */
    private fun explicitActions(text: String): String {
        fun force(value: Any?) {
            when (value) {
                is org.json.JSONObject -> value.put("overwrite", true)
                is org.json.JSONArray -> for (i in 0 until value.length()) force(value.opt(i))
            }
        }
        fun rule(value: org.json.JSONObject) {
            listOf("enable", "enabledActions", "on", "disable", "disabledActions", "off").forEach {
                force(value.opt(it))
            }
            value.optJSONArray("options")?.let { options ->
                for (i in 0 until options.length()) options.optJSONObject(i)?.let { option ->
                    option.put("overwrite", true)
                    force(option.opt("actions"))
                }
            }
        }
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            val array = org.json.JSONArray(trimmed)
            for (i in 0 until array.length()) array.optJSONObject(i)?.let(::rule)
            return array.toString()
        }
        val objectValue = org.json.JSONObject(trimmed)
        val rules = objectValue.optJSONArray("rules")
        if (rules == null) rule(objectValue)
        else for (i in 0 until rules.length()) rules.optJSONObject(i)?.let(::rule)
        return objectValue.toString()
    }
}

package com.ai.assistance.operit.data.model

import androidx.annotation.StringRes
import com.ai.assistance.operit.R

/**
 * A data class to hold the static definition of a model parameter.
 * This serves as a single source of truth for standard parameters.
 *
 * @param T The underlying data type of the parameter's value.
 * @property id Unique identifier for the parameter.
 * @property name Default, non-localized name of the parameter.
 * @property nameResId Optional Android string resource ID for the parameter name.
 * @property apiName The name used in the API request body.
 * @property description Default, non-localized description of the parameter.
 * @property descriptionResId Optional Android string resource ID for the parameter description.
 * @property defaultValue The default value for the parameter.
 * @property valueType The type of the parameter's value (e.g., INT, FLOAT).
 * @property category The category the parameter belongs to.
 * @property minValue Optional minimum allowed value.
 * @property maxValue Optional maximum allowed value.
 */
data class ParameterDefinition<T : Any>(
    val id: String,
    val name: String,
    @StringRes val nameResId: Int = 0,
    val apiName: String,
    val description: String,
    @StringRes val descriptionResId: Int = 0,
    val defaultValue: T,
    val valueType: ParameterValueType,
    val category: ParameterCategory,
    val minValue: T? = null,
    val maxValue: T? = null
)

/**
 * A central repository for the definitions of all standard model parameters.
 */
object StandardModelParameters {
    // Default values for standard model parameters
    const val DEFAULT_MAX_TOKENS = 16384
    const val DEFAULT_TEMPERATURE = 1.0f
    const val DEFAULT_TOP_P = 1.0f
    const val DEFAULT_TOP_K = 0
    const val DEFAULT_PRESENCE_PENALTY = 0.0f
    const val DEFAULT_FREQUENCY_PENALTY = 0.0f
    const val DEFAULT_REPETITION_PENALTY = 1.0f

    val DEFINITIONS: List<ParameterDefinition<*>> =
        listOf(
            ParameterDefinition(
                id = "max_tokens",
                name = "Max tokens",
                nameResId = R.string.model_param_max_tokens,
                apiName = "max_tokens",
                description = "Maximum number of tokens to generate in one response",
                descriptionResId = R.string.model_param_max_tokens_desc,
                defaultValue = DEFAULT_MAX_TOKENS,
                valueType = ParameterValueType.INT,
                category = ParameterCategory.GENERATION,
                minValue = 1
            ),
            ParameterDefinition(
                id = "temperature",
                name = "Temperature",
                nameResId = R.string.model_param_temperature,
                apiName = "temperature",
                description = "Controls randomness: lower is more deterministic, higher is more random",
                descriptionResId = R.string.model_param_temperature_desc,
                defaultValue = DEFAULT_TEMPERATURE,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.CREATIVITY,
                minValue = 0.0f,
                maxValue = 2.0f
            ),
            ParameterDefinition(
                id = "top_p",
                name = "Top-p sampling",
                nameResId = R.string.model_param_top_p,
                apiName = "top_p",
                description = "Alternative to temperature: consider only tokens within cumulative probability top-p",
                descriptionResId = R.string.model_param_top_p_desc,
                defaultValue = DEFAULT_TOP_P,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.CREATIVITY,
                minValue = 0.0f,
                maxValue = 1.0f
            ),
            ParameterDefinition(
                id = "top_k",
                name = "Top-k sampling",
                nameResId = R.string.model_param_top_k,
                apiName = "top_k",
                description = "Consider only the top-k tokens by probability. 0 disables",
                descriptionResId = R.string.model_param_top_k_desc,
                defaultValue = DEFAULT_TOP_K,
                valueType = ParameterValueType.INT,
                category = ParameterCategory.CREATIVITY,
                minValue = 0,
                maxValue = 100
            ),
            ParameterDefinition(
                id = "presence_penalty",
                name = "Presence penalty",
                nameResId = R.string.model_param_presence_penalty,
                apiName = "presence_penalty",
                description = "Encourages new topics: higher values reduce repetition of existing tokens",
                descriptionResId = R.string.model_param_presence_penalty_desc,
                defaultValue = DEFAULT_PRESENCE_PENALTY,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.REPETITION,
                minValue = -2.0f,
                maxValue = 2.0f
            ),
            ParameterDefinition(
                id = "frequency_penalty",
                name = "Frequency penalty",
                nameResId = R.string.model_param_frequency_penalty,
                apiName = "frequency_penalty",
                description = "Reduces repetition: higher values penalize tokens based on frequency",
                descriptionResId = R.string.model_param_frequency_penalty_desc,
                defaultValue = DEFAULT_FREQUENCY_PENALTY,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.REPETITION,
                minValue = -2.0f,
                maxValue = 2.0f
            ),
            ParameterDefinition(
                id = "repetition_penalty",
                name = "Repetition penalty",
                nameResId = R.string.model_param_repetition_penalty,
                apiName = "repetition_penalty",
                description = "Further reduces repetition: 1.0 means no penalty; values > 1.0 discourage repetition",
                descriptionResId = R.string.model_param_repetition_penalty_desc,
                defaultValue = DEFAULT_REPETITION_PENALTY,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.REPETITION,
                minValue = 0.0f,
                maxValue = 2.0f
            )
        )
}
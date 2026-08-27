package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class ThinkingQualityControl { LEVELS, TOGGLE_ONLY, UNSUPPORTED }

internal sealed interface ThinkingQualityWireValue {
    data class Text(val value: String) : ThinkingQualityWireValue
    data class Number(val value: Int) : ThinkingQualityWireValue
    data object Omitted : ThinkingQualityWireValue
}

internal data class ThinkingQualityOption(
    val id: String,
    val displayLabel: String,
    val wireValue: ThinkingQualityWireValue,
    val actions: List<ThinkingQualityJsonAction> = emptyList(),
)

internal data class ThinkingQualityJsonAction(
    val path: String,
    val value: Any?,
    val overwrite: Boolean = false,
)

internal data class ThinkingQualityMapping(
    val control: ThinkingQualityControl,
    val parameterLabel: String,
    val options: List<ThinkingQualityOption>,
    val reasoningRequired: Boolean = false,
    val disabledValue: String? = null,
    val enabledActions: List<ThinkingQualityJsonAction> = emptyList(),
    val disabledActions: List<ThinkingQualityJsonAction> = emptyList(),
) {
    companion object {
        fun toggleOnly(parameterLabel: String, reasoningRequired: Boolean = false): ThinkingQualityMapping =
            ThinkingQualityMapping(ThinkingQualityControl.TOGGLE_ONLY, parameterLabel, emptyList(), reasoningRequired)

        fun unsupported(): ThinkingQualityMapping = ThinkingQualityMapping(ThinkingQualityControl.UNSUPPORTED, "", emptyList())
    }

    fun optionFor(id: String): ThinkingQualityOption? = options.firstOrNull { it.id == id }
    fun textValueFor(id: String): String? = (optionFor(id)?.wireValue as? ThinkingQualityWireValue.Text)?.value
    fun numberValueFor(id: String): Int? = (optionFor(id)?.wireValue as? ThinkingQualityWireValue.Number)?.value
}

internal object ThinkingQualityMappingRegistry {
    fun resolve(providerTypeId: String, modelName: String): ThinkingQualityMapping =
        resolve(providerTypeId, modelName, "")

    fun resolve(
        providerTypeId: String,
        modelName: String,
        thinkingConfigurations: String
    ): ThinkingQualityMapping {
        // The JSON array order is the user-visible priority order: the first enabled rule
        // matching both provider and model wins, and later rules are not evaluated.
        return parseRules(thinkingConfigurations)
            .firstOrNull { it.matches(providerTypeId, modelName) }
            ?.toMapping()
            ?: ThinkingQualityMapping.unsupported()
    }

    suspend fun resolveForModel(
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String = ""
    ): ThinkingQualityMapping {
        return resolve(providerTypeId, modelName, thinkingConfigurations)
    }

    fun validateConfigurations(thinkingConfigurations: String) {
        parseRules(thinkingConfigurations)
    }

    fun formatConfigurations(thinkingConfigurations: String): String {
        val text = normalizedJsonText(thinkingConfigurations)
        return when {
            text.startsWith("[") -> JSONArray(text).toString(2)
            text.startsWith("{") -> JSONObject(text).toString(2)
            else -> JSONArray(text).toString(2)
        }
    }

    private fun parseRules(thinkingConfigurations: String): List<ThinkingConfigurationRule> {
        val array = rulesArray(thinkingConfigurations)
        return buildList {
            for (index in 0 until array.length()) {
                val ruleObject = array.optJSONObject(index) ?: continue
                val rule = ThinkingConfigurationRule.fromJson(ruleObject)
                if (rule.enabled) add(rule)
            }
        }
    }

    private fun rulesArray(thinkingConfigurations: String): JSONArray {
        val text = normalizedJsonText(thinkingConfigurations)
        return when {
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> {
                val objectValue = JSONObject(text)
                objectValue.optJSONArray("rules") ?: JSONArray().put(objectValue)
            }
            else -> JSONArray(text)
        }
    }

    private fun normalizedJsonText(thinkingConfigurations: String): String =
        thinkingConfigurations.trim().ifEmpty { "[]" }
}

private data class ThinkingConfigurationRule(
    val id: String,
    val enabled: Boolean,
    val providerIds: List<String>,
    val matcher: ThinkingModelMatcher,
    val control: ThinkingQualityControl,
    val parameterLabel: String,
    val reasoningRequired: Boolean,
    val disabledValue: String?,
    val enabledActions: List<ThinkingQualityJsonAction>,
    val disabledActions: List<ThinkingQualityJsonAction>,
    val options: List<ThinkingQualityOption>,
) {
    fun matches(providerTypeId: String, modelName: String): Boolean {
        val provider = providerTypeId.trim().uppercase(Locale.US)
        val providerMatches =
            providerIds.isEmpty() || providerIds.any { it.equals(provider, ignoreCase = true) }
        return providerMatches && matcher.matches(modelName)
    }

    fun toMapping(): ThinkingQualityMapping =
        ThinkingQualityMapping(
            control = control,
            parameterLabel = parameterLabel,
            options = options,
            reasoningRequired = reasoningRequired,
            disabledValue = disabledValue,
            enabledActions = enabledActions,
            disabledActions = disabledActions,
        )

    companion object {
        fun fromJson(json: JSONObject): ThinkingConfigurationRule {
            val parameterLabel = json.optString("parameterLabel", json.optString("label", "")).trim()
            val enabledActions = json.actionList("enable", "enabledActions", "on")
            val disabledActions = json.actionList("disable", "disabledActions", "off")
            val options = json.optionList(parameterLabel)
            val disabledValue = json.optString("disabledValue", "").trim().ifEmpty {
                disabledActions.firstOrNull { it.path == parameterLabel }?.value as? String ?: ""
            }.ifEmpty { null }

            return ThinkingConfigurationRule(
                id = json.optString("id", ""),
                enabled = json.optBoolean("enabled", true),
                providerIds = json.stringList("providers") + json.stringList("providerTypeIds"),
                matcher = ThinkingModelMatcher.fromJson(json.optJSONObject("match") ?: JSONObject(), json),
                control = when (json.optString("control", "unsupported").trim().lowercase(Locale.US)) {
                    "levels" -> ThinkingQualityControl.LEVELS
                    "toggle_only", "toggle" -> ThinkingQualityControl.TOGGLE_ONLY
                    else -> ThinkingQualityControl.UNSUPPORTED
                },
                parameterLabel = parameterLabel,
                reasoningRequired = json.optBoolean("required", json.optBoolean("reasoningRequired", false)),
                disabledValue = disabledValue,
                enabledActions = enabledActions,
                disabledActions = disabledActions,
                options = options,
            )
        }
    }
}

private data class ThinkingModelMatcher(
    val modelPrefix: List<String>,
    val modelContains: List<String>,
    val modelSuffix: List<String>,
    val modelRegex: List<String>,
    val firstSegment: List<String>,
    val lastSegmentPrefix: List<String>,
    val lastSegmentContains: List<String>,
    val lastSegmentRegex: List<String>,
) {
    fun matches(modelName: String): Boolean {
        if (isEmpty()) return true

        val model = modelName.trim().lowercase(Locale.US)
        val segments = model.split('/').filter(String::isNotEmpty)
        val first = segments.firstOrNull().orEmpty()
        val last = segments.lastOrNull() ?: model

        return modelPrefix.any { model.startsWith(it) } ||
            modelContains.any { model.contains(it) } ||
            modelSuffix.any { model.endsWith(it) } ||
            modelRegex.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(modelName) } ||
            firstSegment.any { first == it } ||
            lastSegmentPrefix.any { last.startsWith(it) } ||
            lastSegmentContains.any { last.contains(it) } ||
            lastSegmentRegex.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(last) }
    }

    private fun isEmpty(): Boolean =
        modelPrefix.isEmpty() &&
            modelContains.isEmpty() &&
            modelSuffix.isEmpty() &&
            modelRegex.isEmpty() &&
            firstSegment.isEmpty() &&
            lastSegmentPrefix.isEmpty() &&
            lastSegmentContains.isEmpty() &&
            lastSegmentRegex.isEmpty()

    companion object {
        fun fromJson(match: JSONObject, root: JSONObject): ThinkingModelMatcher =
            ThinkingModelMatcher(
                modelPrefix = match.stringList("modelPrefix") + root.stringList("modelPrefix"),
                modelContains = match.stringList("modelContains") + root.stringList("modelContains"),
                modelSuffix = match.stringList("modelSuffix") + root.stringList("modelSuffix"),
                modelRegex = match.stringList("modelRegex") + root.stringList("modelRegex"),
                firstSegment = match.stringList("firstSegment") + root.stringList("firstSegment"),
                lastSegmentPrefix = match.stringList("lastSegmentPrefix") + root.stringList("lastSegmentPrefix"),
                lastSegmentContains = match.stringList("lastSegmentContains") + root.stringList("lastSegmentContains"),
                lastSegmentRegex = match.stringList("lastSegmentRegex") + root.stringList("lastSegmentRegex"),
            )
    }
}

internal object ThinkingConfigurationApplier {
    fun apply(
        context: Context,
        requestJson: JSONObject,
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String,
        enableThinking: Boolean,
        // The selected option belongs to the model configuration; never read a global preference here.
        optionId: String,
    ) {
        apply(
            requestJson = requestJson,
            providerTypeId = providerTypeId,
            modelName = modelName,
            apiEndpoint = apiEndpoint,
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = optionId,
        )
    }

    fun apply(
        requestJson: JSONObject,
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String,
        enableThinking: Boolean,
        optionId: String,
    ): ThinkingQualityMapping {
        val mapping = ThinkingQualityMappingRegistry.resolve(providerTypeId, modelName, thinkingConfigurations)
        if (mapping.control == ThinkingQualityControl.UNSUPPORTED) return mapping

        val thinkingEnabled = enableThinking || mapping.reasoningRequired
        val modeActions = if (thinkingEnabled) mapping.enabledActions else mapping.disabledActions
        modeActions.forEach { applyAction(requestJson, it) }

        if (thinkingEnabled && mapping.control == ThinkingQualityControl.LEVELS) {
            val selected = mapping.optionFor(optionId)
                ?: throw IllegalArgumentException("$providerTypeId option is not supported: $optionId")
            selected.actions.forEach { applyAction(requestJson, it) }
        }
        return mapping
    }

    fun modelParameters(
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String,
        enableThinking: Boolean,
        optionId: String,
        protocol: ApiProviderType,
    ): Pair<ThinkingQualityMapping, List<ModelParameter<*>>> {
        val requestJson = JSONObject()
        val mapping = apply(
            requestJson = requestJson,
            providerTypeId = providerTypeId,
            modelName = modelName,
            apiEndpoint = apiEndpoint,
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = optionId,
        )
        return mapping to requestJson.toModelParameters(protocol)
    }

    private fun applyAction(root: JSONObject, action: ThinkingQualityJsonAction) {
        val path = action.path.trim()
        if (path.isEmpty()) return
        if (!action.overwrite && root.hasJsonPath(path)) return
        root.putJsonPath(path, action.value, action.overwrite)
    }
}

private fun JSONObject.optionList(parameterLabel: String): List<ThinkingQualityOption> {
    val array = optJSONArray("options") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val option = array.optJSONObject(index) ?: continue
            val value = option.jsonValue("value")
            val id = option.optString("id", value?.toString().orEmpty()).trim()
            if (id.isEmpty()) continue
            val label = option.optString("label", id).trim()
            val path = option.optString("path", parameterLabel).trim()
            val wireValue = when (value) {
                is Number -> ThinkingQualityWireValue.Number(value.toInt())
                is String -> ThinkingQualityWireValue.Text(value)
                else -> ThinkingQualityWireValue.Omitted
            }
            val directAction =
                if (path.isNotEmpty() && value != null) {
                    listOf(
                        ThinkingQualityJsonAction(
                            path = path,
                            value = value,
                            overwrite = option.optBoolean("overwrite", false),
                        )
                    )
                } else {
                    emptyList()
                }
            add(
                ThinkingQualityOption(
                    id = id,
                    displayLabel = label,
                    wireValue = wireValue,
                    actions = directAction + option.actionList("actions"),
                )
            )
        }
    }
}

private fun JSONObject.actionList(vararg keys: String): List<ThinkingQualityJsonAction> =
    keys.flatMap { key -> actionsFromValue(opt(key)) }

private fun actionsFromValue(value: Any?): List<ThinkingQualityJsonAction> {
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                val item = value.optJSONObject(index) ?: continue
                item.toAction()?.let(::add)
            }
        }
        is JSONObject -> listOfNotNull(value.toAction())
        else -> emptyList()
    }
}

private fun JSONObject.toAction(): ThinkingQualityJsonAction? {
    val path = optString("path", "").trim()
    if (path.isEmpty()) return null
    return ThinkingQualityJsonAction(
        path = path,
        value = jsonValue("value"),
        overwrite = optBoolean("overwrite", false),
    )
}

private fun JSONObject.stringList(key: String): List<String> {
    val value = opt(key)
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                value.optString(index, "").trim().lowercase(Locale.US).takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        is String -> value.trim().lowercase(Locale.US).takeIf(String::isNotEmpty)?.let(::listOf).orEmpty()
        else -> emptyList()
    }
}

private fun JSONObject.jsonValue(key: String): Any? {
    if (!has(key) || isNull(key)) return null
    return opt(key)
}

private fun JSONObject.hasJsonPath(path: String): Boolean {
    val parts = path.split('.').filter(String::isNotEmpty)
    if (parts.isEmpty()) return false
    var current: JSONObject = this
    for (segment in parts.dropLast(1)) {
        val next = current.opt(segment)
        if (next !is JSONObject) return false
        current = next
    }
    return current.has(parts.last())
}

private fun JSONObject.putJsonPath(path: String, value: Any?, overwrite: Boolean) {
    val parts = path.split('.').filter(String::isNotEmpty)
    if (parts.isEmpty()) return

    var current: JSONObject = this
    for (segment in parts.dropLast(1)) {
        val next = current.opt(segment)
        current = when {
            next is JSONObject -> next
            current.has(segment) && !overwrite -> return
            else -> JSONObject().also { current.put(segment, it) }
        }
    }
    current.put(parts.last(), value.cloneJsonValue())
}

private fun Any?.cloneJsonValue(): Any =
    when (this) {
        null -> JSONObject.NULL
        is JSONObject -> JSONObject(toString())
        is JSONArray -> JSONArray(toString())
        else -> this
    }

private fun JSONObject.toModelParameters(protocol: ApiProviderType): List<ModelParameter<*>> {
    val result = mutableListOf<ModelParameter<*>>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = opt(key)
        val category =
            if (protocol.isGeminiProtocol() && key == "thinkingConfig") {
                ParameterCategory.GENERATION
            } else {
                ParameterCategory.OTHER
            }
        when (value) {
            is JSONObject, is JSONArray -> result += thinkingObjectParameter(key, value.toString(), category)
            is Boolean -> result += thinkingBooleanParameter(key, value, category)
            is Number -> {
                val number = value.toDouble()
                if (number % 1.0 == 0.0) {
                    result += thinkingIntParameter(key, value.toInt(), category)
                } else {
                    result += thinkingFloatParameter(key, value.toFloat(), category)
                }
            }
            is String -> result += thinkingStringParameter(key, value, category)
        }
    }
    return result
}

private fun ApiProviderType.isGeminiProtocol(): Boolean =
    this == ApiProviderType.GOOGLE || this == ApiProviderType.GEMINI_GENERIC

private fun thinkingStringParameter(
    apiName: String,
    value: String,
    category: ParameterCategory
): ModelParameter<String> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.STRING,
        category = category,
        isCustom = false,
    )

private fun thinkingIntParameter(
    apiName: String,
    value: Int,
    category: ParameterCategory
): ModelParameter<Int> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.INT,
        category = category,
        isCustom = false,
    )

private fun thinkingFloatParameter(
    apiName: String,
    value: Float,
    category: ParameterCategory
): ModelParameter<Float> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.FLOAT,
        category = category,
        isCustom = false,
    )

private fun thinkingBooleanParameter(
    apiName: String,
    value: Boolean,
    category: ParameterCategory
): ModelParameter<Boolean> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.BOOLEAN,
        category = category,
        isCustom = false,
    )

private fun thinkingObjectParameter(
    apiName: String,
    value: String,
    category: ParameterCategory
): ModelParameter<String> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.OBJECT,
        category = category,
        isCustom = false,
    )

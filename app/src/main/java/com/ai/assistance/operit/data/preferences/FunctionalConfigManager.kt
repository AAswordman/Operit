package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.ai.assistance.operit.data.persistence.PreferenceStateRepairResult
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.persistence.repairPreferenceState
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromJsonElement
import kotlinx.serialization.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// 为功能配置创建专用的DataStore
private val Context.functionalConfigDataStore: DataStore<Preferences> by
        recoverablePreferencesDataStore(name = "functional_configs")

/** 功能配置映射数据，包含配置ID和模型索引 */
@Serializable
data class FunctionConfigMapping(
    val configId: String = FunctionalConfigManager.DEFAULT_CONFIG_ID,
    val modelIndex: Int = 0
)

/** 管理不同功能使用的模型配置 这个类用于将FunctionType映射到对应的ModelConfigID */
class FunctionalConfigManager(private val context: Context) {

    private data class ParsedMapping(
        val values: Map<FunctionType, FunctionConfigMapping>,
        val valid: Boolean,
        val legacy: Boolean,
        val unknownEntries: Map<String, JsonElement>
    )

    // 定义key
    companion object {
        // 功能配置映射key
        val FUNCTION_CONFIG_MAPPING = stringPreferencesKey("function_config_mapping")

        // 默认映射值
        const val DEFAULT_CONFIG_ID = "default"
    }

    // Json解析器
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 获取ModelConfigManager实例用于配置查询
    private val modelConfigManager = ModelConfigManager(context)

    private fun parseMapping(raw: String?): ParsedMapping {
        if (raw.isNullOrBlank() || raw == "{}") {
            return ParsedMapping(
                values = emptyMap(),
                valid = true,
                legacy = false,
                unknownEntries = emptyMap()
            )
        }
        val root =
            try {
                json.parseToJsonElement(raw).jsonObject
            } catch (e: Exception) {
                AppLogger.e(
                    "FunctionalConfigManager",
                    "Invalid persisted functional model mapping",
                    e
                )
                return ParsedMapping(
                    values = emptyMap(),
                    valid = false,
                    legacy = false,
                    unknownEntries = emptyMap()
                )
            }

        val values = linkedMapOf<FunctionType, FunctionConfigMapping>()
        val unknownEntries = linkedMapOf<String, JsonElement>()
        var valid = true
        var legacy = false
        root.forEach { (name, element) ->
            val functionType = FunctionType.values().firstOrNull { it.name == name }
            if (functionType == null) {
                unknownEntries[name] = element
                return@forEach
            }
            val mapping =
                try {
                    json.decodeFromJsonElement<FunctionConfigMapping>(element)
                } catch (newFormatFailure: Exception) {
                    val legacyConfigId =
                        (element as? JsonPrimitive)
                            ?.takeIf { it.isString }
                            ?.content
                    if (legacyConfigId == null) {
                        valid = false
                        AppLogger.e(
                            "FunctionalConfigManager",
                            "Invalid persisted mapping for ${functionType.name}",
                            newFormatFailure
                        )
                        return@forEach
                    }
                    legacy = true
                    FunctionConfigMapping(legacyConfigId, 0)
                }
            values[functionType] = mapping
        }
        return ParsedMapping(
            values = values,
            valid = valid,
            legacy = legacy,
            unknownEntries = unknownEntries
        )
    }

    private fun completeMapping(parsed: Map<FunctionType, FunctionConfigMapping>): Map<FunctionType, FunctionConfigMapping> =
        FunctionType.values().associateWith { functionType ->
            parsed[functionType] ?: FunctionConfigMapping(DEFAULT_CONFIG_ID, 0)
        }

    private fun encodeMapping(
        mapping: Map<FunctionType, FunctionConfigMapping>,
        unknownEntries: Map<String, JsonElement>
    ): String {
        val encodedEntries =
            buildMap<String, JsonElement> {
                putAll(unknownEntries)
                mapping.forEach { (functionType, value) ->
                    put(functionType.name, json.encodeToJsonElement(value))
                }
            }
        return json.encodeToString(JsonObject(encodedEntries))
    }

    // 获取功能配置映射（保持向后兼容）
    val functionConfigMappingFlow: Flow<Map<FunctionType, String>> =
            context.functionalConfigDataStore.data.map { preferences ->
                completeMapping(parseMapping(preferences[FUNCTION_CONFIG_MAPPING]).values)
                    .mapValues { it.value.configId }
            }

    // 获取完整的功能配置映射（包含modelIndex）
    val functionConfigMappingWithIndexFlow: Flow<Map<FunctionType, FunctionConfigMapping>> =
            context.functionalConfigDataStore.data.map { preferences ->
                completeMapping(parseMapping(preferences[FUNCTION_CONFIG_MAPPING]).values)
            }

    suspend fun repairPersistedState(): Boolean {
        val availableConfigs = modelConfigManager.getPersistedConfigsForRecovery()
        val defaultConfig = checkNotNull(availableConfigs[DEFAULT_CONFIG_ID]) {
            "Default model configuration must exist before functional mapping repair"
        }
        return repairPreferenceState(
            context = context,
            storeName = PreferenceStoreCatalog.FUNCTIONAL_CONFIGS,
            dataStore = context.functionalConfigDataStore
        ) { current ->
            val raw = current.asMap().entries
                .firstOrNull { it.key.name == FUNCTION_CONFIG_MAPPING.name }
                ?.value
            val parsed = parseMapping(raw as? String)
            val normalized =
                FunctionType.values().associateWith { functionType ->
                    val candidate =
                        parsed.values[functionType] ?: FunctionConfigMapping(DEFAULT_CONFIG_ID, 0)
                    val selectedConfig = availableConfigs[candidate.configId] ?: defaultConfig
                    FunctionConfigMapping(
                        configId = selectedConfig.id,
                        modelIndex = getValidModelIndex(selectedConfig.modelName, candidate.modelIndex)
                    )
                }
            val issues = linkedSetOf<String>()
            val mutable = current.toMutablePreferences()
            if (raw !is String ||
                !parsed.valid ||
                parsed.legacy ||
                parsed.values != normalized
            ) {
                mutable.asMap().keys
                    .filter { it.name == FUNCTION_CONFIG_MAPPING.name }
                    .forEach { mutable.remove(it) }
                mutable[FUNCTION_CONFIG_MAPPING] =
                    encodeMapping(normalized, parsed.unknownEntries)
                issues += FUNCTION_CONFIG_MAPPING.name
            }
            PreferenceStateRepairResult(mutable.toPreferences(), issues)
        }
    }

    // 初始化，确保有默认映射
    suspend fun initializeIfNeeded() {
        val mapping = functionConfigMappingWithIndexFlow.first()

        // 只在映射真正为空时才创建默认映射，避免覆盖用户已保存的modelIndex
        if (mapping.isEmpty()) {
            val defaultMapping = FunctionType.values().associateWith { FunctionConfigMapping(DEFAULT_CONFIG_ID, 0) }
            saveFunctionConfigMappingWithIndex(defaultMapping)
        }

        // 确保ModelConfigManager也已初始化
        modelConfigManager.initializeIfNeeded()
    }

    // 保存功能配置映射（保持向后兼容）
    suspend fun saveFunctionConfigMapping(mapping: Map<FunctionType, String>) {
        val mappingWithIndex = mapping.entries.associate { 
            it.key to FunctionConfigMapping(it.value, 0) 
        }
        saveFunctionConfigMappingWithIndex(mappingWithIndex)
    }

    // 保存功能配置映射（包含modelIndex）
    suspend fun saveFunctionConfigMappingWithIndex(mapping: Map<FunctionType, FunctionConfigMapping>) {
        context.functionalConfigDataStore.edit { preferences ->
            // A released older build must not delete a mapping owned by a newer build when the
            // user changes one of the function types that this build understands.
            val parsed = parseMapping(preferences[FUNCTION_CONFIG_MAPPING])
            preferences[FUNCTION_CONFIG_MAPPING] =
                encodeMapping(mapping, parsed.unknownEntries)
        }
    }

    // 获取指定功能的配置ID
    suspend fun getConfigIdForFunction(functionType: FunctionType): String {
        val mapping = functionConfigMappingFlow.first()
        return mapping[functionType] ?: DEFAULT_CONFIG_ID
    }

    // 获取指定功能的完整配置（包含modelIndex）
    suspend fun getConfigMappingForFunction(functionType: FunctionType): FunctionConfigMapping {
        val mapping = functionConfigMappingWithIndexFlow.first()
        return mapping[functionType] ?: FunctionConfigMapping(DEFAULT_CONFIG_ID, 0)
    }

    // 设置指定功能的配置ID
    suspend fun setConfigForFunction(functionType: FunctionType, configId: String) {
        setConfigForFunction(functionType, configId, 0)
    }

    // 设置指定功能的配置ID和模型索引
    suspend fun setConfigForFunction(functionType: FunctionType, configId: String, modelIndex: Int) {
        val mapping = functionConfigMappingWithIndexFlow.first().toMutableMap()
        mapping[functionType] = FunctionConfigMapping(configId, modelIndex)
        saveFunctionConfigMappingWithIndex(mapping)
    }

    // 重置指定功能的配置为默认
    suspend fun resetFunctionConfig(functionType: FunctionType) {
        setConfigForFunction(functionType, DEFAULT_CONFIG_ID)
    }

    // 重置所有功能配置为默认
    suspend fun resetAllFunctionConfigs() {
        val defaultMapping = FunctionType.values().associateWith { FunctionConfigMapping(DEFAULT_CONFIG_ID, 0) }
        saveFunctionConfigMappingWithIndex(defaultMapping)
    }
}

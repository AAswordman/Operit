package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.ai.assistance.operit.data.model.FunctionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 为功能配置创建专用的DataStore
private val Context.functionalConfigDataStore: DataStore<Preferences> by
        versionedPreferencesDataStore(
                name = "functional_configs",
                currentVersion = 1,
        ) {
            preferenceSchemaMigration { version, preferences ->
                when (version) {
                    0 -> FunctionalConfigManager.migratePreferencesFromVersionZero(preferences)
                    else -> missingPreferencesSchemaMigration(version)
                }
            }
        }

/** 功能配置映射数据，包含配置ID和模型索引 */
@Serializable
data class FunctionConfigMapping(
    val configId: String = FunctionalConfigManager.DEFAULT_CONFIG_ID,
    val modelIndex: Int = 0
)

internal data class FunctionConfigMappingRepair(
    val mapping: Map<FunctionType, FunctionConfigMapping>,
    val affectedFunctions: List<FunctionType>,
)

internal fun remapDeletedConfigReferences(
    mapping: Map<FunctionType, FunctionConfigMapping>,
    deletedConfigId: String,
): FunctionConfigMappingRepair {
    val updatedMapping = mapping.toMutableMap()
    val affectedFunctions = mapping
        .filterValues { it.configId == deletedConfigId }
        .keys
        .sortedBy { it.name }

    affectedFunctions.forEach { functionType ->
        updatedMapping[functionType] = FunctionConfigMapping(
            configId = FunctionalConfigManager.DEFAULT_CONFIG_ID,
            modelIndex = 0,
        )
    }

    return FunctionConfigMappingRepair(
        mapping = updatedMapping,
        affectedFunctions = affectedFunctions,
    )
}

/** 管理不同功能使用的模型配置 这个类用于将FunctionType映射到对应的ModelConfigID */
class FunctionalConfigManager(private val context: Context) {

    // 定义key
    companion object {
        // 功能配置映射key
        val FUNCTION_CONFIG_MAPPING = stringPreferencesKey("function_config_mapping")

        // 默认映射值
        const val DEFAULT_CONFIG_ID = "default"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        internal fun migratePreferencesFromVersionZero(preferences: MutablePreferences) {
            val mapping = readSchemaZeroMapping(preferences[FUNCTION_CONFIG_MAPPING])
            writeFunctionConfigMapping(preferences, mapping)
        }

        private fun readSchemaZeroMapping(raw: String?): Map<FunctionType, FunctionConfigMapping> {
            if (raw.isNullOrBlank() || raw == "{}") {
                return defaultMapping()
            }

            val decoded =
                    try {
                        json.decodeFromString<Map<String, FunctionConfigMapping>>(raw)
                    } catch (error: SerializationException) {
                        json.decodeFromString<Map<String, String>>(raw)
                                .mapValues { (_, configId) -> FunctionConfigMapping(configId, 0) }
                    }

            return normalizeMapping(decoded)
        }

        private fun readFunctionConfigMapping(
                preferences: Preferences
        ): Map<FunctionType, FunctionConfigMapping> {
            val raw = preferences[FUNCTION_CONFIG_MAPPING]
            if (raw.isNullOrBlank() || raw == "{}") {
                return defaultMapping()
            }
            return normalizeMapping(json.decodeFromString<Map<String, FunctionConfigMapping>>(raw))
        }

        private fun writeFunctionConfigMapping(
                preferences: MutablePreferences,
                mapping: Map<FunctionType, FunctionConfigMapping>
        ) {
            val stringMapping = mapping.entries.associate { it.key.name to it.value }
            preferences[FUNCTION_CONFIG_MAPPING] = json.encodeToString(stringMapping)
        }

        private fun normalizeMapping(
                decoded: Map<String, FunctionConfigMapping>
        ): Map<FunctionType, FunctionConfigMapping> {
            val normalized = defaultMapping().toMutableMap()
            decoded.forEach { (key, value) ->
                normalized[FunctionType.valueOf(key)] = value
            }
            return normalized
        }

        private fun defaultMapping(): Map<FunctionType, FunctionConfigMapping> {
            return FunctionType.values().associateWith { FunctionConfigMapping(DEFAULT_CONFIG_ID, 0) }
        }
    }

    // 获取完整的功能配置映射（包含modelIndex）
    val functionConfigMappingWithIndexFlow: Flow<Map<FunctionType, FunctionConfigMapping>> =
            context.functionalConfigDataStore.data.map { preferences ->
                readFunctionConfigMapping(preferences)
            }

    // 获取功能配置映射（保持向后兼容）
    val functionConfigMappingFlow: Flow<Map<FunctionType, String>> =
            functionConfigMappingWithIndexFlow.map { mapping ->
                mapping.entries.associate { it.key to it.value.configId }
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
            writeFunctionConfigMapping(preferences, mapping)
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

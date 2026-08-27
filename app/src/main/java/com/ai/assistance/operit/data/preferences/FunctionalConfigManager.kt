package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.FunctionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 为功能配置创建专用的DataStore
private val Context.functionalConfigDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "functional_configs")

/** 功能配置映射数据，包含配置ID和模型索引 */
@Serializable
data class FunctionConfigMapping(
    val configId: String = FunctionalConfigManager.DEFAULT_CONFIG_ID,
    val modelIndex: Int = 0
)

/** 管理不同功能使用的模型配置 这个类用于将FunctionType映射到对应的ModelConfigID */
class FunctionalConfigManager(private val context: Context) {

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

    // 获取功能配置映射（保持向后兼容）
    val functionConfigMappingFlow: Flow<Map<FunctionType, String>> =
            context.functionalConfigDataStore.data.map { preferences ->
                val mappingJson = preferences[FUNCTION_CONFIG_MAPPING] ?: "{}"
                if (mappingJson == "{}") {
                    FunctionType.values().associateWith { DEFAULT_CONFIG_ID }
                } else {
                    try {
                        // 尝试新格式（包含modelIndex）
                        val rawMap = json.decodeFromString<Map<String, FunctionConfigMapping>>(mappingJson)
                        rawMap.entries.associate { FunctionType.valueOf(it.key) to it.value.configId }
                    } catch (e: Exception) {
                        try {
                            // 回退到旧格式（只有configId）
                            val rawMap = json.decodeFromString<Map<String, String>>(mappingJson)
                            rawMap.entries.associate { FunctionType.valueOf(it.key) to it.value }
                        } catch (e2: Exception) {
                            FunctionType.values().associateWith { DEFAULT_CONFIG_ID }
                        }
                    }
                }
            }

    // 获取完整的功能配置映射（包含modelIndex）
    val functionConfigMappingWithIndexFlow: Flow<Map<FunctionType, FunctionConfigMapping>> =
            context.functionalConfigDataStore.data.map { preferences ->
                val mappingJson = preferences[FUNCTION_CONFIG_MAPPING] ?: "{}"
                if (mappingJson == "{}") {
                    FunctionType.values().associateWith { FunctionConfigMapping(DEFAULT_CONFIG_ID, 0) }
                } else {
                    try {
                        val rawMap = json.decodeFromString<Map<String, FunctionConfigMapping>>(mappingJson)
                        rawMap.entries.associate { FunctionType.valueOf(it.key) to it.value }
                    } catch (e: Exception) {
                        try {
                            // 从旧格式迁移
                            val rawMap = json.decodeFromString<Map<String, String>>(mappingJson)
                            rawMap.entries.associate { 
                                FunctionType.valueOf(it.key) to FunctionConfigMapping(it.value, 0) 
                            }
                        } catch (e2: Exception) {
                            FunctionType.values().associateWith { FunctionConfigMapping(DEFAULT_CONFIG_ID, 0) }
                        }
                    }
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
        val stringMapping = mapping.entries.associate { it.key.name to it.value }
        context.functionalConfigDataStore.edit { preferences ->
            preferences[FUNCTION_CONFIG_MAPPING] = json.encodeToString(stringMapping)
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

    // 让全部功能跟随当前对话功能的配置，包含多模型索引
    suspend fun followChatConfigForAllFunctions() {
        val chatMapping = getConfigMappingForFunction(FunctionType.CHAT)
        val mapping = FunctionType.values().associateWith { chatMapping }
        saveFunctionConfigMappingWithIndex(mapping)
    }

    // 重置所有功能配置为默认
    suspend fun resetAllFunctionConfigs() {
        val defaultMapping = FunctionType.values().associateWith { FunctionConfigMapping(DEFAULT_CONFIG_ID, 0) }
        saveFunctionConfigMappingWithIndex(defaultMapping)
    }
}

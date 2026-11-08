package com.ai.assistance.operit.data.config.model

import kotlinx.serialization.Serializable

/** 模型参数类，支持泛型以表示不同类型的参数 */
data class ModelParameter<T>(
        val id: String, // 参数唯一ID
        val name: String, // 参数显示名称
        val apiName: String, // API调用中的参数名
        val description: String = "", // 参数描述
        val defaultValue: T, // 参数默认值
        val currentValue: T, // 参数当前值
        val isEnabled: Boolean, // 参数是否启用
        val valueType: ParameterValueType, // 参数值类型
        val minValue: Any? = null, // 最小值(适用于数值类型)
        val maxValue: Any? = null, // 最大值(适用于数值类型)
        val category: ParameterCategory = ParameterCategory.OTHER, // 参数分类
        val isCustom: Boolean = false // 是否为自定义参数
)

/** 参数值类型枚举 */
enum class ParameterValueType {
    INT,
    FLOAT,
    STRING,
    BOOLEAN,
    OBJECT
}

/** 参数分类枚举 */
enum class ParameterCategory {
    GENERATION, // 生成参数
    CREATIVITY, // 创造性参数
    REPETITION, // 重复控制参数
    OTHER // 其他参数
}

/** 自定义参数数据，用于JSON序列化 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.CustomParameterData")
@Serializable
data class CustomParameterData(
        val id: String,
        val name: String,
        val apiName: String,
        val description: String = "",
        val defaultValue: String,
        val currentValue: String,
        val isEnabled: Boolean,
        val valueType: String,
        val minValue: String? = null,
        val maxValue: String? = null,
        val category: String = "OTHER"
)

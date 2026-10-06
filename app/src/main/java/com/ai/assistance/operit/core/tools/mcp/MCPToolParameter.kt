package com.ai.assistance.operit.core.tools.mcp

import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Represents a parameter for an MCP tool */
@Serializable
data class MCPToolParameter(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean = false,
        val defaultValue: String? = null
) {
    /**
     * 尝试根据参数类型自动转换字符串值
     *
     * @param value 输入值（通常是字符串）
     * @return 经过类型转换的值
     */
    fun convertParameterValue(value: Any): Any {
        if (value !is String) return value

        return when (type.lowercase()) {
            "string" -> value
            "number" -> {
                try {
                    if (value.contains(".")) value.toDouble() else value.toLong()
                } catch (e: Exception) {
                    value // 无法转换时返回原始值
                }
            }
            "boolean" -> value.lowercase() == "true"
            "integer" -> {
                try {
                    value.toInt()
                } catch (e: Exception) {
                    value
                }
            }
            "float", "double" -> {
                try {
                    value.toDouble()
                } catch (e: Exception) {
                    value
                }
            }
            "array" -> {
                // 尝试将字符串解析为数组
                Companion.parseArray(value)
            }
            "object" -> {
                // 尝试将字符串解析为对象
                Companion.parseObject(value)
            }
            else -> value // 其他类型保持原样
        }
    }

    companion object {
        /**
         * 尝试智能转换参数类型（无需MCPToolParameter实例）
         *
         * @param value 输入值（通常是字符串）
         * @param typeName 类型名称
         * @return 转换后的值
         */
        fun smartConvert(value: Any, typeName: String?): Any {
            // 如果已经是 List 或 Array，递归处理元素
            if (value is List<*>) {
                return value.map { element -> 
                    if (element != null) smartConvert(element, null) else null 
                }
            }
            
            if (value !is String) return value

            return when (typeName?.lowercase()) {
                "string" -> value
                "number" -> {
                    try {
                        if (value.contains(".")) value.toDouble() else value.toLong()
                    } catch (e: Exception) {
                        value
                    }
                }
                "boolean" -> value.lowercase() == "true"
                "integer" -> {
                    try {
                        value.toInt()
                    } catch (e: Exception) {
                        value
                    }
                }
                "float", "double" -> {
                    try {
                        value.toDouble()
                    } catch (e: Exception) {
                        value
                    }
                }
                "array" -> {
                    // 尝试解析数组
                    parseArray(value)
                }
                "object" -> {
                    // 尝试解析对象
                    parseObject(value)
                }
                null, "" -> {
                    // 仅当未指定类型时，尝试智能猜测
                    when {
                        // 检测是否为对象格式（JSON对象）
                        value.trimStart().startsWith("{") && value.trimEnd().endsWith("}") -> {
                            parseObject(value)
                        }
                        // 检测是否为数组格式（JSON数组或逗号分隔）
                        value.trimStart().startsWith("[") && value.trimEnd().endsWith("]") -> {
                            parseArray(value)
                        }
                        value.matches(Regex("-?\\d+(\\.\\d+)?")) -> {
                            try {
                                if (value.contains(".")) value.toDouble() else value.toLong()
                            } catch (e: Exception) {
                                value
                            }
                        }
                        value.lowercase() == "true" || value.lowercase() == "false" -> {
                            value.lowercase() == "true"
                        }
                        else -> value
                    }
                }
                else -> value
            }
        }

        /**
         * 解析数组字符串，支持 JSON 格式和简单格式
         * 递归处理数组内的元素
         *
         * @param value 字符串值
         * @return 解析后的列表，如果无法解析则返回原始字符串
         */
        private fun parseArray(value: String): Any {
            val trimmed = value.trim()
            
            // 尝试作为 JSON 数组解析
            try {
                val jsonArray = JSONArray(trimmed)
                val result = mutableListOf<Any>()
                
                for (i in 0 until jsonArray.length()) {
                    val element = when {
                        jsonArray.isNull(i) -> null
                        else -> {
                            val rawValue = jsonArray.get(i)
                            // 递归处理数组元素
                            when (rawValue) {
                                is JSONArray -> {
                                    // 嵌套数组，递归处理
                                    parseArray(rawValue.toString())
                                }
                                is JSONObject -> {
                                    // 嵌套对象，递归处理
                                    parseObject(rawValue.toString())
                                }
                                is Number -> rawValue
                                is Boolean -> rawValue
                                is String -> {
                                    // 对字符串元素进行智能转换
                                    smartConvert(rawValue, null)
                                }
                                else -> rawValue
                            }
                        }
                    }
                    if (element != null) {
                        result.add(element)
                    }
                }
                
                return result
            } catch (e: JSONException) {
                // JSON 解析失败，尝试修复常见的非标准格式
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    try {
                        // 尝试修复格式：将逗号分隔的无引号标识符转换为带引号的 JSON 数组
                        val content = trimmed.substring(1, trimmed.length - 1).trim()
                        
                        // 检查是否是简单的标识符列表（只包含字母、数字、下划线和逗号）
                        if (content.matches(Regex("[\\w\\s,_-]+"))) {
                            // 分割元素并添加引号
                            val elements = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            
                            // 如果元素看起来像标识符（非数字），则保留为字符串
                            return elements.map { element -> 
                                if (element.matches(Regex("\\d+"))) {
                                    element.toLongOrNull() ?: element
                                } else if (element.matches(Regex("\\d+\\.\\d+"))) {
                                    element.toDoubleOrNull() ?: element
                                } else {
                                    element // 保留为字符串
                                }
                            }
                        }
                        
                        // 否则，尝试一般的逗号分隔解析
                        val elements = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        return elements.map { element -> smartConvert(element, null) }
                    } catch (ex: Exception) {
                        // 修复失败，返回原始值
                        return value
                    }
                }
                
                // 无法解析，返回原始值
                return value
            } catch (e: Exception) {
                return value
            }
        }
        
        /**
         * 解析对象字符串，支持 JSON 格式
         * 递归处理对象内的值
         *
         * @param value 字符串值
         * @return 解析后的 Map，如果无法解析则返回原始字符串
         */
        private fun parseObject(value: String): Any {
            val trimmed = value.trim()
            
            try {
                val jsonObject = JSONObject(trimmed)
                val result = mutableMapOf<String, Any>()
                
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val rawValue = jsonObject.get(key)
                    
                    val convertedValue = when (rawValue) {
                        is JSONObject -> {
                            // 嵌套对象，递归处理
                            parseObject(rawValue.toString())
                        }
                        is JSONArray -> {
                            // 嵌套数组，递归处理
                            parseArray(rawValue.toString())
                        }
                        is Number -> rawValue
                        is Boolean -> rawValue
                        is String -> {
                            // 对字符串值进行智能转换
                            smartConvert(rawValue, null)
                        }
                        else -> rawValue
                    }
                    
                    result[key] = convertedValue
                }
                
                return result
            } catch (e: JSONException) {
                // JSON 解析失败，返回原始值
                return value
            } catch (e: Exception) {
                return value
            }
        }
    }
}

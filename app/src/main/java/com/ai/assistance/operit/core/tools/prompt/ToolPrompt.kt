package com.ai.assistance.operit.core.tools.prompt

/**
 * 工具参数模式定义（用于定义工具的参数结构）
 */
data class ToolParameterSchema(
    val name: String,
    val type: String = "string", // "string", "boolean", "integer", "number", "object", "array"
    val description: String,
    val required: Boolean = true,
    val default: String? = null
)

/**
 * 工具提示词数据类
 * 表示单个工具的完整提示词信息
 */
data class ToolPrompt(
    val name: String,
    val description: String,
    val parameters: String = "", // 兼容旧格式的字符串参数
    val parametersStructured: List<ToolParameterSchema>? = null, // 新的结构化参数
    val details: String = "",
    val notes: String = ""
) {
    /**
     * 将工具提示词转换为原始系统提示词格式
     */
    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("- $name: $description")

        // 优先使用结构化参数，转换为字符串格式
        val paramsString = if (parametersStructured != null && parametersStructured.isNotEmpty()) {
            parametersStructured.joinToString(", ") { param ->
                // 构建完整的参数描述
                val parts = mutableListOf<String>()

                // 添加主描述
                parts.add(param.description)

                // 如果有默认值且描述中没有提到，添加默认值信息
                if (param.default != null && !param.description.contains("default")) {
                    parts.add("default ${param.default}")
                }

                val fullDesc = parts.joinToString(", ")
                "${param.name} ($fullDesc)"
            }
        } else {
            parameters
        }

        if (paramsString.isNotEmpty()) {
            builder.append(" Parameters: $paramsString")
        }

        if (details.isNotEmpty()) {
            builder.append("\n$details")
        }

        if (notes.isNotEmpty()) {
            builder.append("\n$notes")
        }

        return builder.toString()
    }
}

/**
 * 工具分类数据类
 * 表示一组相关工具的集合
 */
data class SystemToolPromptCategory(
    val categoryName: String,
    val categoryHeader: String = "",
    val tools: List<ToolPrompt>,
    val categoryFooter: String = ""
) {
    /**
     * 将整个分类转换为原始系统提示词格式
     */
    override fun toString(): String {
        val builder = StringBuilder()

        if (categoryName.isNotEmpty()) {
            builder.append(categoryName)
            builder.append(":")
        }

        if (categoryHeader.isNotEmpty()) {
            builder.append("\n")
            builder.append(categoryHeader)
        }

        if (tools.isNotEmpty()) {
            builder.append("\n")
            tools.forEachIndexed { index, tool ->
                builder.append(tool.toString())
                if (index < tools.size - 1) {
                    builder.append("\n")
                }
            }
        }

        if (categoryFooter.isNotEmpty()) {
            builder.append("\n")
            builder.append(categoryFooter)
        }

        return builder.toString()
    }
}

/**
 * 包工具提示词分类数据类
 * 表示来自包（Package）的工具集合，用于动态生成包工具的提示词
 */
data class PackageToolPromptCategory(
    val packageName: String,
    val packageDescription: String,
    val tools: List<ToolPrompt>
) {
    /**
     * 将包工具分类转换为系统提示词格式
     */
    override fun toString(): String {
        val builder = StringBuilder()

        builder.append("Package: $packageName")
        builder.append("\n")
        builder.append("Description: $packageDescription")

        if (tools.isNotEmpty()) {
            builder.append("\n")
            builder.append("Tools:")
            builder.append("\n")
            tools.forEachIndexed { index, tool ->
                builder.append(tool.toString())
                if (index < tools.size - 1) {
                    builder.append("\n")
                }
            }
        }

        return builder.toString()
    }
}

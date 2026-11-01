package com.ai.assistance.operit.core.tools.builtin.a2ui

import com.ai.assistance.operit.data.model.SystemToolPromptCategory
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt

/** 基础定义只说明接口；详细用法由可选外部引导包提供。 */
internal object A2uiToolDefinitions {
    fun category(useEnglish: Boolean): SystemToolPromptCategory {
        fun text(english: String, chinese: String) = if (useEnglish) english else chinese
        return SystemToolPromptCategory(
            categoryName = "A2UI",
            tools = listOf(
                ToolPrompt(
                    name = "a2ui_prepare",
                    description = text("Compile Express and queue a native card for automatic display when this turn completes. The returned reference can optionally choose its position.", "编译 Express 并安排本轮结束时自动展示原生卡片；返回的引用可用于指定展示位置。"),
                    parametersStructured = listOf(
                        ToolParameterSchema("source", "string", text("Express source, up to 65536 characters; query a2ui_catalog for signatures. Progress.number uses a 0 to 1 ratio.", "Express 源码，最多 65536 字符；组件参数见 a2ui_catalog。Progress.number 使用 0～1 的比例。")),
                        ToolParameterSchema("live", "boolean", text("Enable updates during this turn. Initialize $/agent as an object and bind model-owned values below $/agent/.", "允许本轮请求内更新数据；需初始化 $/agent 对象，并将模型拥有的数据绑定到 $/agent/ 下。"), required = false, default = "false"),
                    ),
                ),
                ToolPrompt(
                    name = "a2ui_revise",
                    description = text("Compile and queue a new revision. Previously displayed cards are preserved; unplaced older revisions are replaced.", "编译并安排展示新修订；已展示的旧卡片保留，尚未展示的旧修订由新版替换。"),
                    parametersStructured = listOf(
                        ToolParameterSchema("reference", "string", text("Reference returned by a2ui_prepare or a2ui_revise in this turn.", "本轮 a2ui_prepare 或 a2ui_revise 返回的引用。")),
                        ToolParameterSchema("changes", "string", text("Changed Express declarations, up to 65536 characters.", "变更的 Express 声明，最多 65536 字符。")),
                        ToolParameterSchema("remove", "array", text("Declaration names to remove, at most 128 strings.", "要删除的声明名，最多 128 个字符串。"), required = false, default = "[]"),
                    ),
                ),
                ToolPrompt(
                    name = "a2ui_patch_data",
                    description = text("Update data on a live card from the active turn using its expected version.", "按预期版本更新本轮请求中 live 卡片的数据。"),
                    parametersStructured = listOf(
                        ToolParameterSchema("reference", "string", text("Live card reference from this turn.", "本轮的 live 卡片引用。")),
                        ToolParameterSchema("expected_version", "integer", text("Current data version, initially 0.", "当前数据版本，初始为 0。")),
                        ToolParameterSchema("data", "object", text("Replacement object for $/agent, up to 8192 characters when serialized; omit the outer agent key.", "替换 $/agent 的数据对象，序列化后最多 8192 字符；不再包裹外层 agent 键。")),
                    ),
                ),
                ToolPrompt(
                    name = "a2ui_catalog",
                    description = text("Read the native Express component and function API schema.", "查询原生 Express 组件、函数和参数定义。"),
                ),
                ToolPrompt(
                    name = "a2ui_icons",
                    description = text("Search icon names supported by native A2UI cards.", "查询原生 A2UI 卡片支持的图标名称。"),
                    parametersStructured = listOf(
                        ToolParameterSchema("query", "string", text("Search text, up to 80 characters.", "搜索词，最多 80 字符。"), required = false, default = ""),
                        ToolParameterSchema("limit", "integer", text("Page size, 1 to 100.", "每页数量，1 到 100。"), required = false, default = "30"),
                        ToolParameterSchema("offset", "integer", text("Offset, 0 to 10000.", "偏移量，0 到 10000。"), required = false, default = "0"),
                    ),
                ),
            ),
        )
    }
}
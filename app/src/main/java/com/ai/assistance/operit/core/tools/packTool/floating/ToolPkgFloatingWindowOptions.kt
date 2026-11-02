package com.ai.assistance.operit.core.tools.packTool.floating

import org.json.JSONObject

/**
 * 插件悬浮窗的几何与交互选项，对齐 Operit2 v2 的 FloatingWindowOptions。
 *
 * null 表示"未指定"：open 时按 [DEFAULTS] 补齐布尔默认值，update 时表示保持现值；
 * x/y/width/draggable 等可以始终未指定。序列化时仅输出已解析的非空字段。
 */
data class ToolPkgFloatingWindowOptions(
    val x: Int?,
    val y: Int?,
    val width: Int?,
    val height: Int?,
    val alwaysOnTop: Boolean?,
    val clickThrough: Boolean?,
    val focusable: Boolean?,
    val draggable: Boolean?,
) {
    /**
     * 以本对象为补丁合并到 [base] 之上：非空字段覆盖，空字段保持。
     * Operit2 的 FloatingWindowOptions.merge 同语义。
     */
    fun mergedOver(base: ToolPkgFloatingWindowOptions): ToolPkgFloatingWindowOptions {
        return ToolPkgFloatingWindowOptions(
            x = x ?: base.x,
            y = y ?: base.y,
            width = width ?: base.width,
            height = height ?: base.height,
            alwaysOnTop = alwaysOnTop ?: base.alwaysOnTop,
            clickThrough = clickThrough ?: base.clickThrough,
            focusable = focusable ?: base.focusable,
            draggable = draggable ?: base.draggable,
        )
    }

    /** 仅输出已解析（非空）字段，与 d.ts 中全可选的 FloatingWindowOptions 对齐。 */
    fun toJson(): JSONObject {
        val json = JSONObject()
        x?.let { json.put("x", it) }
        y?.let { json.put("y", it) }
        width?.let { json.put("width", it) }
        height?.let { json.put("height", it) }
        alwaysOnTop?.let { json.put("alwaysOnTop", it) }
        clickThrough?.let { json.put("clickThrough", it) }
        focusable?.let { json.put("focusable", it) }
        draggable?.let { json.put("draggable", it) }
        return json
    }

    companion object {
        /** 布尔默认值对齐 Operit2：alwaysOnTop=true，其余 false。 */
        val DEFAULTS = ToolPkgFloatingWindowOptions(
            x = null,
            y = null,
            width = null,
            height = null,
            alwaysOnTop = true,
            clickThrough = false,
            focusable = false,
            draggable = false,
        )

        const val MIN_SIZE = 16
        const val MAX_SIZE = 4096

        fun fromJson(json: JSONObject?): ToolPkgFloatingWindowOptions {
            if (json == null) return DEFAULTS
            return ToolPkgFloatingWindowOptions(
                x = json.optIntOrNull("x"),
                y = json.optIntOrNull("y"),
                width = json.optIntOrNull("width"),
                height = json.optIntOrNull("height"),
                alwaysOnTop = json.optBooleanOrNull("alwaysOnTop"),
                clickThrough = json.optBooleanOrNull("clickThrough"),
                focusable = json.optBooleanOrNull("focusable"),
                draggable = json.optBooleanOrNull("draggable"),
            )
        }

        private fun JSONObject.optIntOrNull(key: String): Int? =
            if (has(key) && !isNull(key)) getInt(key) else null

        private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
            if (has(key) && !isNull(key)) getBoolean(key) else null
    }
}

package com.ai.assistance.operit.ui.features.support.diagnostics.uidebugger

import android.content.Context
import android.graphics.Rect
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.action.ActionListener

/** UI调试工具的状态类（仅保留布局分析和 Activity 监听相关状态） */
data class UIDebuggerState(
    val elements: List<UIElement> = emptyList(),
    val selectedElementId: String? = null,
    val showActionFeedback: Boolean = false,
    val actionFeedbackMessage: String = "",
    val errorMessage: String? = null,
    val currentAnalyzedActivityName: String? = null,
    val currentAnalyzedPackageName: String? = null,
    // Activity 监听相关状态
    val isActivityListening: Boolean = false,
    val activityEvents: List<ActionListener.ActionEvent> = emptyList(),
    val showActivityMonitor: Boolean = false,
    val currentActivityName: String? = null
)

/** UI元素数据模型 */
data class UIElement(
    val id: String,
    val className: String,
    val resourceId: String? = null,
    val contentDesc: String? = null,
    val text: String = "",
    val bounds: Rect? = null,
    val isClickable: Boolean = false,
    val activityName: String? = null,
    val packageName: String? = null
) {
    fun getTypeDescription(context: Context): String {
        return when {
            className.contains("Button", ignoreCase = true) ->
                context.getString(R.string.uidebugger_element_type_button)
            className.contains("Text", ignoreCase = true) ->
                context.getString(R.string.uidebugger_element_type_text)
            className.contains("Edit", ignoreCase = true) ->
                context.getString(R.string.uidebugger_element_type_input)
            className.contains("Image", ignoreCase = true) ->
                context.getString(R.string.uidebugger_element_type_image)
            className.contains("View", ignoreCase = true) ->
                context.getString(R.string.uidebugger_element_type_view)
            else -> context.getString(R.string.uidebugger_element_type_generic)
        }
    }

    fun getFullDetails(context: Context): String {
        return buildString {
            append(context.getString(R.string.uidebugger_element_detail_class_name, className))
            append("\n")
            if (packageName != null) {
                append(context.getString(R.string.uidebugger_element_detail_package_name, packageName))
                append("\n")
            }
            if (activityName != null) {
                append(context.getString(R.string.uidebugger_element_detail_activity_name, activityName))
                append("\n")
            }
            if (resourceId != null) {
                append(context.getString(R.string.uidebugger_element_detail_resource_id, resourceId))
                append("\n")
            }
            if (contentDesc != null) {
                append(context.getString(R.string.uidebugger_element_detail_content_desc, contentDesc))
                append("\n")
            }
            if (text.isNotEmpty()) {
                append(context.getString(R.string.uidebugger_element_detail_text, text))
                append("\n")
            }
            if (bounds != null)
                append(context.getString(R.string.uidebugger_element_detail_bounds, bounds.left, bounds.top, bounds.right, bounds.bottom) + "\n")
            append(context.getString(R.string.uidebugger_element_detail_clickable, if (isClickable) context.getString(R.string.yes) else context.getString(R.string.no)))
        }
    }
}

/** UI元素操作类型 */
enum class UIElementAction {
    CLICK,
    HIGHLIGHT,
    INSPECT
}

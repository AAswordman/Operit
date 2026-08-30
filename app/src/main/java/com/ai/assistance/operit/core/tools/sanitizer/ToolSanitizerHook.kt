package com.ai.assistance.operit.core.tools.sanitizer

import com.ai.assistance.operit.core.tools.AIToolHook
import com.ai.assistance.operit.core.tools.AIToolHookDecision
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.AppLogger

/**
 * AIToolHook that sanitizes tool call parameters before execution.
 *
 * Handles XML tag contamination, CDATA blocks, markdown artifacts,
 * and other provider metadata that can pollute tool call JSON.
 *
 * Integration point: register via AIToolHandler.addToolHook()
 */
class ToolSanitizerHook : AIToolHook {

    companion object {
        private const val TAG = "ToolSanitizerHook"
    }

    private val sanitizer = ToolCallSanitizer()

    override fun onToolCallIntercept(tool: AITool): AIToolHookDecision {
        val sanitizedParams = mutableListOf<ToolParameter>()
        var anyChanged = false

        for (param in tool.parameters) {
            val sanitizedValue = sanitizer.sanitize(param.value)
            if (sanitizedValue != param.value) {
                anyChanged = true
                AppLogger.d(TAG, "Sanitized param '${param.name}': '${param.value.take(50)}...' → '${sanitizedValue.take(50)}...'")
            }
            sanitizedParams.add(ToolParameter(param.name, sanitizedValue))
        }

        return if (anyChanged) {
            // Note: We can't modify the tool in-place here since AITool is immutable.
            // The sanitizer runs at the ToolExecutionManager level instead.
            // This hook serves as a detection/validation layer.
            AIToolHookDecision.Allow
        } else {
            AIToolHookDecision.Allow
        }
    }

    /**
     * Sanitize a list of tool parameters, returning new parameters with cleaned values.
     */
    fun sanitizeParameters(params: List<ToolParameter>): List<ToolParameter> {
        return params.map { param ->
            val sanitized = sanitizer.sanitize(param.value)
            if (sanitized != param.value) {
                AppLogger.d(TAG, "Sanitized '${param.name}': '${param.value.take(80)}' → '${sanitized.take(80)}'")
            }
            ToolParameter(param.name, sanitized)
        }
    }

    /**
     * Sanitize a single tool call, returning a new AITool with cleaned parameters.
     */
    fun sanitizeToolCall(tool: AITool): AITool {
        val sanitizedParams = sanitizeParameters(tool.parameters)
        return AITool(name = tool.name, parameters = sanitizedParams)
    }
}

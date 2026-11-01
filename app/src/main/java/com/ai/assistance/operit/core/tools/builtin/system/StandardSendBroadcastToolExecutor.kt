package com.ai.assistance.operit.core.tools.builtin.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.ai.assistance.operit.core.tools.IntentResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StandardSendBroadcastToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "SendBroadcastToolExecutor"
    }

    private fun applyComponentName(intent: Intent, rawComponentName: String) {
        val parts = rawComponentName.split("/", limit = 2)
        if (parts.size != 2) {
            return
        }
        val packageName = parts[0].trim()
        val className = parts[1].trim()
        if (packageName.isEmpty() || className.isEmpty()) {
            return
        }
        val normalizedClassName =
            if (className.startsWith(".")) {
                packageName + className
            } else {
                className
            }
        intent.setClassName(packageName, normalizedClassName)
    }

    suspend fun invoke(tool: AITool): ToolResult {
        val validation = validateParameters(tool)
        if (!validation.valid) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = validation.errorMessage
            )
        }

        val action = tool.parameters.find { it.name == "action" }?.value?.trim().orEmpty()
        val uri = tool.parameters.find { it.name == "uri" }?.value
        val packageName = tool.parameters.find { it.name == "package" }?.value
        val componentName = tool.parameters.find { it.name == "component" }?.value

        val extraKey = tool.parameters.find { it.name == "extra_key" }?.value?.trim().orEmpty()
        val extraValue = tool.parameters.find { it.name == "extra_value" }?.value ?: ""
        val extraKey2 = tool.parameters.find { it.name == "extra_key2" }?.value?.trim().orEmpty()
        val extraValue2 = tool.parameters.find { it.name == "extra_value2" }?.value ?: ""

        val extrasJsonString = tool.parameters.find { it.name == "extras" }?.value

        return try {
            val intent = Intent().apply {
                this.action = action
                if (!uri.isNullOrBlank()) {
                    data = Uri.parse(uri)
                }
                if (!packageName.isNullOrBlank()) {
                    `package` = packageName
                }
                if (!componentName.isNullOrBlank()) {
                    applyComponentName(this, componentName)
                }

                if (extraKey.isNotBlank()) {
                    putExtra(extraKey, extraValue)
                }
                if (extraKey2.isNotBlank()) {
                    putExtra(extraKey2, extraValue2)
                }

                if (!extrasJsonString.isNullOrBlank()) {
                    try {
                        val extrasJson = JSONObject(extrasJsonString)
                        val keys = extrasJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = extrasJson.get(key)
                            when (value) {
                                is String -> putExtra(key, value)
                                is Int -> putExtra(key, value)
                                is Boolean -> putExtra(key, value)
                                is Float -> putExtra(key, value)
                                is Double -> putExtra(key, value)
                                is Long -> putExtra(key, value)
                                else -> putExtra(key, value.toString())
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error parsing extras", e)
                    }
                }
            }

            val resultMessage =
                withContext(Dispatchers.Main) {
                    context.sendBroadcast(intent)
                    "Broadcast sent successfully"
                }

            val extrasBundle = Bundle()
            intent.extras?.let { extrasBundle.putAll(it) }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    IntentResultData(
                        action = intent.action ?: "null",
                        uri = intent.data?.toString() ?: "null",
                        package_name = intent.`package` ?: "null",
                        component = intent.component?.flattenToString() ?: "null",
                        flags = intent.flags,
                        extras_count = extrasBundle.size(),
                        result = resultMessage
                    )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error sending broadcast", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Broadcast failed: ${e.message}"
            )
        }
    }

    fun validateParameters(tool: AITool): ToolValidationResult {
        val action = tool.parameters.find { it.name == "action" }?.value
        if (action.isNullOrBlank()) {
            return ToolValidationResult(valid = false, errorMessage = "Missing required parameter: action")
        }
        return ToolValidationResult(valid = true)
    }
}

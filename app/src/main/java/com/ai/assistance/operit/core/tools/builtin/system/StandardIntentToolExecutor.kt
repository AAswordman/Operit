package com.ai.assistance.operit.core.tools.builtin.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.tools.IntentResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for executing Android Intents. This provides the ability to create and launch Android
 * intents for various operations like starting activities, services, or broadcasts.
 */
class StandardIntentToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "IntentToolExecutor"

        // Intent execution types
        const val TYPE_ACTIVITY = "activity"
        const val TYPE_BROADCAST = "broadcast"
        const val TYPE_SERVICE = "service"
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
        // Validate parameters
        val validationResult = validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = validationResult.errorMessage
            )
        }

        val action = tool.parameters.find { it.name == "action" }?.value
        val uri = tool.parameters.find { it.name == "uri" }?.value
        val packageName = tool.parameters.find { it.name == "package" }?.value
        val flags = tool.parameters.find { it.name == "flags" }?.value
        val extras = tool.parameters.find { it.name == "extras" }?.value
        val componentName = tool.parameters.find { it.name == "component" }?.value
        val type = tool.parameters.find { it.name == "type" }?.value ?: TYPE_ACTIVITY

        return try {
            // Create the intent
            val intent = Intent()

            // Set action if provided
            if (!action.isNullOrBlank()) {
                intent.action = action
            }

            // Set data URI if provided
            if (!uri.isNullOrBlank()) {
                intent.data = Uri.parse(uri)
            }

            // Set package if provided
            if (!packageName.isNullOrBlank()) {
                intent.`package` = packageName
            }

            // Set component if provided
            if (!componentName.isNullOrBlank()) {
                applyComponentName(intent, componentName)
            }

            // Set flags if provided
            if (!flags.isNullOrBlank()) {
                try {
                    val flagsJson = JSONArray(flags)
                    var combinedFlags = 0
                    for (i in 0 until flagsJson.length()) {
                        val flag = flagsJson.getInt(i)
                        combinedFlags = combinedFlags or flag
                    }
                    intent.flags = combinedFlags
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error parsing flags", e)
                    // Try to parse as a single integer value
                    try {
                        intent.flags = flags.toInt()
                    } catch (e2: Exception) {
                        AppLogger.e(TAG, "Error parsing flags as integer", e2)
                    }
                }
            }

            // Set extras if provided
            if (!extras.isNullOrBlank()) {
                try {
                    val extrasJson = JSONObject(extras)
                    val keys = extrasJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = extrasJson.get(key)

                        when (value) {
                            is String -> intent.putExtra(key, value)
                            is Int -> intent.putExtra(key, value)
                            is Boolean -> intent.putExtra(key, value)
                            is Float -> intent.putExtra(key, value)
                            is Double -> intent.putExtra(key, value)
                            is Long -> intent.putExtra(key, value)
                            else -> {
                                // Try to detect array types
                                if (value is JSONArray) {
                                    // Handle various array types
                                    if (value.length() > 0) {
                                        val firstItem = value.get(0)
                                        when (firstItem) {
                                            is String -> {
                                                val stringArray =
                                                        Array(value.length()) { i ->
                                                            value.getString(i)
                                                        }
                                                intent.putExtra(key, stringArray)
                                            }
                                            is Int -> {
                                                val intArray =
                                                        IntArray(value.length()) { i ->
                                                            value.getInt(i)
                                                        }
                                                intent.putExtra(key, intArray)
                                            }
                                            else -> {
                                                // Convert to string if type is unsupported
                                                intent.putExtra(key, value.toString())
                                            }
                                        }
                                    }
                                } else {
                                    // Convert to string if type is unsupported
                                    intent.putExtra(key, value.toString())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error parsing extras", e)
                }
            }

            // Check if intent is valid
            if (intent.action == null && componentName.isNullOrBlank()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Intent must have either an action or component specified"
                )
            }

            // Add FLAG_ACTIVITY_NEW_TASK for safety if not already set when starting activity
            // This is needed when starting activities from non-activity contexts
            if (type == TYPE_ACTIVITY && intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Execute the intent based on the requested type using withContext to ensure main
            // thread execution
            try {
                val result =
                        withContext(Dispatchers.Main) {
                            when (type) {
                                TYPE_BROADCAST -> {
                                    context.sendBroadcast(intent)
                                    "Broadcast sent successfully"
                                }
                                TYPE_SERVICE -> {
                                    if (componentName.isNullOrBlank()) {
                                        return@withContext "ERROR: Component must be specified when starting a service"
                                    }
                                    context.startService(intent)
                                    "Service started successfully"
                                }
                                else -> { // Default to activity
                                    context.startActivity(intent)
                                    "Activity started successfully"
                                }
                            }
                        }

                // Handle error from service component check
                if (result.startsWith("ERROR:")) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = result.substring(7) // Remove "ERROR: " prefix
                    )
                }

                // Bundle up the intent details for the response
                val extras = Bundle()
                intent.extras?.let { extras.putAll(it) }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                IntentResultData(
                                        action = intent.action ?: "null",
                                        uri = intent.data?.toString() ?: "null",
                                        package_name = intent.`package` ?: "null",
                                        component = intent.component?.flattenToString() ?: "null",
                                        flags = intent.flags,
                                        extras_count = extras.size(),
                                        result = result
                                )
                )
            } catch (e: Exception) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Intent execution failed: ${e.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing Intent", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Intent execution failed: ${e.message}"
            )
        }
    }

    /** Validates the parameters for the Intent tool. */
    fun validateParameters(tool: AITool): ToolValidationResult {
        val action = tool.parameters.find { it.name == "action" }?.value
        val component = tool.parameters.find { it.name == "component" }?.value
        val type = tool.parameters.find { it.name == "type" }?.value

        if (action.isNullOrBlank() && component.isNullOrBlank()) {
            return ToolValidationResult(
                    valid = false,
                    errorMessage = "Either action or component parameter is required"
            )
        }

        // Validate type parameter if provided
        if (!type.isNullOrBlank() &&
                        type != TYPE_ACTIVITY &&
                        type != TYPE_BROADCAST &&
                        type != TYPE_SERVICE
        ) {
            return ToolValidationResult(
                    valid = false,
                    errorMessage = "Type must be one of: activity, broadcast, service"
            )
        }

        // If type is service, component must be provided
        if (type == TYPE_SERVICE && component.isNullOrBlank()) {
            return ToolValidationResult(
                    valid = false,
                    errorMessage = "Component parameter is required when type is 'service'"
            )
        }

        return ToolValidationResult(valid = true)
    }
}

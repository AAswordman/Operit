package com.ai.assistance.operit.core.tools.builtin.filesystem

import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.StringResultData

object PathValidator {
    fun validateAndroidPath(path: String, toolName: String, paramName: String = "path"): ToolResult? {
        if (path.isBlank()) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "$paramName parameter is required"
            )
        }
        if (!path.startsWith("/")) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = FileOperationData(
                    operation = toolName,
                    path = path,
                    successful = false,
                    details = "Invalid path: '$path'. Path must be an absolute path starting with '/'."
                ),
                error = "Invalid path: '$path'. Path must be an absolute path starting with '/'."
            )
        }
        return null
    }

    fun validateLinuxPath(path: String, toolName: String, paramName: String = "path"): ToolResult? {
        if (path.isBlank()) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "$paramName parameter is required"
            )
        }
        if (!path.startsWith("/") && !path.startsWith("~")) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = FileOperationData(
                    operation = toolName,
                    env = "linux",
                    path = path,
                    successful = false,
                    details = "Invalid path: '$path'. Path must start with '/' or '~'."
                ),
                error = "Invalid path: '$path'. Path must start with '/' or '~'."
            )
        }
        return null
    }
}

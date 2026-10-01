package com.ai.assistance.operit.core.tools.defaultTool.debugger

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AppOperationData
import com.ai.assistance.operit.core.tools.NotificationData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.SystemSettingData
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.AccessibilitySystemOperationTools
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

/** 调试级别的系统操作工具，继承无障碍版本, 并使用shell命令覆盖部分实现 */
open class DebuggerSystemOperationTools(private val context: Context) :
    AccessibilitySystemOperationTools(context) {

    private val TAG = "DebuggerSystemTools"

    override suspend fun modifySystemSetting(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return super.modifySystemSetting(tool)
        }

        val setting = tool.parameters.find { it.name == "setting" }?.value ?: ""
        val value = tool.parameters.find { it.name == "value" }?.value ?: ""
        val namespace = tool.parameters.find { it.name == "namespace" }?.value ?: "system"

        if (setting.isBlank() || value.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide setting and value parameters"
            )
        }

        val validNamespaces = listOf("system", "secure", "global")
        if (!validNamespaces.contains(namespace)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Namespace must be one of: ${validNamespaces.joinToString(", ")}"
            )
        }

        return try {
            val command = "settings put $namespace $setting $value"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                val resultData =
                    SystemSettingData(namespace = namespace, setting = setting, value = value)

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to set setting: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error modifying system setting", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error modifying system setting: ${e.message}"
            )
        }
    }

    override suspend fun getSystemSetting(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return super.getSystemSetting(tool)
        }

        val setting = tool.parameters.find { it.name == "setting" }?.value ?: ""
        val namespace = tool.parameters.find { it.name == "namespace" }?.value ?: "system"

        if (setting.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide setting parameter"
            )
        }

        val validNamespaces = listOf("system", "secure", "global")
        if (!validNamespaces.contains(namespace)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Namespace must be one of: ${validNamespaces.joinToString(", ")}"
            )
        }

        return try {
            val command = "settings get $namespace $setting"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                val resultData =
                    SystemSettingData(
                        namespace = namespace,
                        setting = setting,
                        value = result.stdout.trim()
                    )

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get setting: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting system setting", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting system setting: ${e.message}"
            )
        }
    }

    override suspend fun installApp(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return super.installApp(tool)
        }

        val apkPath = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (apkPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide apk_path parameter"
            )
        }

        if (DebuggerFileSystemTools.isOperitInternalPath(apkPath)) {
            AppLogger.d(
                TAG,
                "installApp detected Operit internal path, delegating to AccessibilitySystemOperationTools"
            )
            return super.installApp(tool)
        }

        val existsResult =
            AndroidShellExecutor.executeShellCommand(
                "test -f $apkPath && echo 'exists' || echo 'not exists'"
            )
        if (existsResult.stdout.trim() != "exists") {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "APK file does not exist: $apkPath"
            )
        }

        return try {
            val command = "pm install -r $apkPath"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success && result.stdout.contains("Success")) {
                val resultData =
                    AppOperationData(
                        operationType = "install",
                        packageName = apkPath,
                        success = true
                    )

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Installation failed: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error installing app", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error installing app: ${e.message}"
            )
        }
    }

    override suspend fun uninstallApp(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return super.uninstallApp(tool)
        }

        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        val keepData = tool.parameters.find { it.name == "keep_data" }?.value?.toBoolean() ?: false

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide package_name parameter"
            )
        }

        val checkCommand = "pm list packages | grep -c \"$packageName\""
        val checkResult = AndroidShellExecutor.executeShellCommand(checkCommand)

        if (checkResult.stdout.trim() == "0") {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "App not installed: $packageName"
            )
        }

        return try {
            val command =
                if (keepData) {
                    "pm uninstall -k $packageName"
                } else {
                    "pm uninstall $packageName"
                }

            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success && result.stdout.contains("Success")) {
                val details = if (keepData) "(keep data)" else ""
                val resultData =
                    AppOperationData(
                        operationType = "uninstall",
                        packageName = packageName,
                        success = true,
                        details = details
                    )

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Uninstallation failed: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error uninstalling app", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error uninstalling app: ${e.message}"
            )
        }
    }

    override suspend fun startApp(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return super.startApp(tool)
        }

        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        val activity = tool.parameters.find { it.name == "activity" }?.value ?: ""

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide package_name parameter"
            )
        }

        return try {
            val command: String
            if (activity.isBlank()) {
                // 使用 am start 命令而不是 monkey，避免修改系统设置（如屏幕旋转）
                // 先获取应用的主 Activity，然后使用 -n 参数启动
                val resolveCmd = "cmd package resolve-activity --brief $packageName 2>/dev/null | tail -n 1"
                val resolveResult = AndroidShellExecutor.executeShellCommand(resolveCmd)
                
                if (resolveResult.success && resolveResult.stdout.isNotBlank()) {
                    val output = resolveResult.stdout.trim()
                    // resolve-activity 返回格式可能是：package/activity 或只有 activity
                    // 也可能返回多行，最后一行是组件名
                    val lines = output.lines().filter { it.isNotBlank() && !it.startsWith("name=") }
                    val mainActivity = lines.lastOrNull()?.trim() ?: output.trim()
                    
                    // 如果返回的是完整组件名（package/activity），直接使用
                    command = if (mainActivity.contains('/')) {
                        "am start -n $mainActivity"
                    } else {
                        // 如果只返回了 Activity 名，拼接包名
                        "am start -n $packageName/$mainActivity"
                    }
                    AppLogger.d(TAG, "Resolved main Activity: $mainActivity, using command: $command")
                } else {
                    // 如果无法解析 Activity，返回错误
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Cannot resolve app main Activity. Please provide activity parameter or ensure app is properly installed. Package: $packageName"
                    )
                }
            } else {
                command = "am start -n $packageName/$activity"
            }

            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                val details = if (activity.isNotBlank()) "Activity: $activity" else ""
                val resultData =
                    AppOperationData(
                        operationType = "start",
                        packageName = packageName,
                        success = true,
                        details = details
                    )

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to start app: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error starting app", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error starting app: ${e.message}"
            )
        }
    }

    override suspend fun stopApp(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return super.stopApp(tool)
        }

        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide package_name parameter"
            )
        }

        return try {
            val command = "am force-stop $packageName"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                val resultData =
                    AppOperationData(
                        operationType = "stop",
                        packageName = packageName,
                        success = true
                    )

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to stop app: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error stopping app", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error stopping app: ${e.message}"
            )
        }
    }

    override suspend fun getNotifications(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return super.getNotifications(tool)
        }

        val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 10
        val includeOngoing =
            tool.parameters.find { it.name == "include_ongoing" }?.value?.toBoolean() ?: false

        return try {
            val command =
                if (includeOngoing) {
                    "dumpsys notification --noredact | grep -E 'pkg=|text=' | head -${limit * 2}"
                } else {
                    "dumpsys notification --noredact | grep -v 'ongoing' | grep -E 'pkg=|text=' | head -${limit * 2}"
                }

            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                val lines = result.stdout.split("\n")
                val notifications = mutableListOf<NotificationData.Notification>()

                var currentPackage = ""
                var currentText = ""

                for (line in lines) {
                    when {
                        line.contains("pkg=") -> {
                            if (currentPackage.isNotEmpty() && currentText.isNotEmpty()) {
                                notifications.add(
                                    NotificationData.Notification(
                                        packageName = currentPackage,
                                        text = currentText,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                currentText = ""
                            }
                            
                            val pkgMatch = Regex("pkg=(\\S+)").find(line)
                            currentPackage = pkgMatch?.groupValues?.getOrNull(1) ?: ""
                        }
                        line.contains("text=") -> {
                            val textMatch = Regex("text=(.+)").find(line)
                            currentText = textMatch?.groupValues?.getOrNull(1) ?: ""
                        }
                    }
                }
                
                if (currentPackage.isNotEmpty() && currentText.isNotEmpty()) {
                    notifications.add(
                        NotificationData.Notification(
                            packageName = currentPackage,
                            text = currentText,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

                val resultData =
                    NotificationData(
                        notifications = notifications,
                        timestamp = System.currentTimeMillis()
                    )

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get notifications: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting notifications", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting notifications: ${e.message}"
            )
        }
    }
}

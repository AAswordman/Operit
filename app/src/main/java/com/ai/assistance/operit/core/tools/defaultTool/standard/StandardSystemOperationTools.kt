package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.AppOperationData
import com.ai.assistance.operit.core.tools.AppUsageTimeEntry
import com.ai.assistance.operit.core.tools.AppUsageTimeResultData
import com.ai.assistance.operit.core.tools.BluetoothBondedDevicesData
import com.ai.assistance.operit.core.tools.BluetoothDeviceData
import com.ai.assistance.operit.core.tools.BluetoothStateData
import com.ai.assistance.operit.core.tools.LocationData
import com.ai.assistance.operit.core.tools.NotificationData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.SystemSettingData
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionPermissionRequestCoordinator
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.app.ActivityManager
import android.content.ComponentName
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import com.ai.assistance.operit.services.notification.OperitNotificationStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AndroidUserPathUtils
import com.ai.assistance.operit.util.OperitPaths

/** 提供系统级操作的工具类 包括系统设置修改、应用安装和卸载等 这些操作需要用户明确授权 */
open class StandardSystemOperationTools(private val context: Context) {

    companion object {
        private const val TAG = "SystemOperationTools"

        private const val AI_REPLY_CHANNEL_ID = "AI_REPLY_CHANNEL"
        private const val AI_REPLY_CHANNEL_NAME = "Chat Completion Reminder"
    }

    private fun isOperitInternalPath(path: String): Boolean {
        val normalizedPath = path.trim()
        val appPackage = BuildConfig.APPLICATION_ID
        return normalizedPath.startsWith("/data/data/$appPackage") ||
            AndroidUserPathUtils.isCurrentUserPackageDataPath(normalizedPath, appPackage)
    }

    private fun stageApkForInstallIfNeeded(apkFile: File): File {
        val apkPath = apkFile.absolutePath
        if (!isOperitInternalPath(apkPath)) {
            return apkFile
        }

        val cleanOnExitDir = OperitPaths.cleanOnExitDir()
        if (!cleanOnExitDir.exists() && !cleanOnExitDir.mkdirs()) {
            throw IllegalStateException("Cannot create cleanOnExit dir: ${cleanOnExitDir.absolutePath}")
        }

        val stagedFileName = "install_${System.currentTimeMillis()}_${apkFile.name}"
        val stagedFile = File(cleanOnExitDir, stagedFileName)
        apkFile.copyTo(stagedFile, overwrite = true)
        AppLogger.d(TAG, "Staged internal apk for installer: $apkPath -> ${stagedFile.absolutePath}")
        return stagedFile
    }

    private fun hasUsageStatsAccess(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }

        return mode == AppOpsManager.MODE_ALLOWED
    }

    open suspend fun toast(tool: AITool): ToolResult {
        val message = tool.parameters.find { it.name == "message" }?.value
        if (message.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide message parameter"
            )
        }

        return try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
            ToolResult(toolName = tool.name, success = true, result = StringResultData("OK"))
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Toast failed: ${e.message}"
            )
        }
    }

    open suspend fun sendNotification(tool: AITool): ToolResult {
        val title = tool.parameters.find { it.name == "title" }?.value?.takeIf { it.isNotBlank() } ?: "Notification"
        val message = tool.parameters.find { it.name == "message" }?.value
        if (message.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide message parameter"
            )
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    AI_REPLY_CHANNEL_ID,
                    AI_REPLY_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
            } else {
                null
            }

            val builder =
                NotificationCompat.Builder(context, AI_REPLY_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message.take(100))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
            }

            val notification = builder.build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            manager.notify(id, notification)

            ToolResult(toolName = tool.name, success = true, result = StringResultData("OK"))
        } catch (e: SecurityException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to send notification (missing permission): ${e.message}"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to send notification: ${e.message}"
            )
        }
    }

    /** 修改系统设置 支持修改各种系统设置，如音量、亮度等 */
    open suspend fun modifySystemSetting(tool: AITool): ToolResult {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            // 自动打开系统设置页面引导用户授权
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "No permission to modify system settings. Settings page opened for you, please grant WRITE_SETTINGS permission and retry."
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "打开设置页面失败", e)
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "No permission to modify system settings and cannot open settings page: ${e.message}"
                )
            }
        }

        return try {
            when (namespace) {
                "system" -> Settings.System.putString(context.contentResolver, setting, value)
                "secure" -> Settings.Secure.putString(context.contentResolver, setting, value)
                "global" -> Settings.Global.putString(context.contentResolver, setting, value)
            }
            val resultData = SystemSettingData(namespace = namespace, setting = setting, value = value)
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "修改系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Security exception when modifying system settings: ${e.message}. This may require higher permissions."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "修改系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error modifying system settings: ${e.message}"
            )
        }
    }

    /** 获取系统设置的当前值 */
    open suspend fun getSystemSetting(tool: AITool): ToolResult {
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
            val value = when (namespace) {
                "system" -> Settings.System.getString(context.contentResolver, setting)
                "secure" -> Settings.Secure.getString(context.contentResolver, setting)
                "global" -> Settings.Global.getString(context.contentResolver, setting)
                else -> null
            }

            if (value != null) {
                val resultData = SystemSettingData(namespace = namespace, setting = setting, value = value)
                ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get setting: setting '$setting' not found in namespace '$namespace'."
                )
            }
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "获取系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Security exception when getting system settings: ${e.message}. This may require higher permissions."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting system settings: ${e.message}"
            )
        }
    }

    /** 安装应用程序 需要APK文件的路径 */
    open suspend fun installApp(tool: AITool): ToolResult {
        val apkPath = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (apkPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = context.getString(R.string.sys_op_must_provide_apk_path)
            )
        }

        val file = File(apkPath)
        if (!file.exists()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "APK file does not exist: $apkPath"
            )
        }

        return try {
            val installFile = stageApkForInstallIfNeeded(file)
            val apkUri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        installFile
                    )
                } else {
                    Uri.fromFile(installFile)
                }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)

            val resultData = AppOperationData(
                operationType = "install_request",
                packageName = apkPath,
                success = true,
                details = context.getString(R.string.sys_op_install_request_sent)
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求安装应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error requesting app installation: ${e.message}"
            )
        }
    }

    /** 卸载应用程序 需要提供包名 */
    open suspend fun uninstallApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide package_name parameter"
            )
        }

        try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "App not installed: $packageName"
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val resultData = AppOperationData(
                operationType = "uninstall_request",
                packageName = packageName,
                success = true,
                details = context.getString(R.string.sys_op_uninstall_request_sent)
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求卸载应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error requesting app uninstallation: ${e.message}"
            )
        }
    }

    /** 获取已安装的应用列表 */
    suspend fun listInstalledApps(tool: AITool): ToolResult {
        val includeSystemApps =
                tool.parameters.find { it.name == "include_system_apps" }?.value?.toBoolean()
                        ?: false
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appDetails = mutableListOf<String>()

            apps.forEach { appInfo ->
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (includeSystemApps || !isSystemApp) {
                    val packageName = appInfo.packageName
                    val appName =
                            try {
                                appInfo.loadLabel(pm).toString()
                            } catch (e: Exception) {
                                AppLogger.w(
                                        TAG,
                                        "Failed to load application label for $packageName",
                                        e
                                )
                                packageName
                            }
                    appDetails.add("$appName ($packageName)")
                }
            }

            val sortedAppDetails = appDetails.sorted()
            val resultData = AppListData(
                includesSystemApps = includeSystemApps, 
                packages = sortedAppDetails
            )
            
            ToolResult(toolName = tool.name, success = true, result = resultData)
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取已安装应用列表时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get app list: ${e.message}"
            )
        }
    }

    /** 启动应用程序 如果提供了activity参数，将启动指定的活动 否则使用默认启动器启动应用 */
    open suspend fun startApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        val activityName = tool.parameters.find { it.name == "activity" }?.value ?: ""

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Must provide package_name parameter"
            )
        }

        return try {
            val intent: Intent? = if (activityName.isBlank()) {
                context.packageManager.getLaunchIntentForPackage(packageName)
            } else {
                Intent(Intent.ACTION_MAIN).also {
                    it.addCategory(Intent.CATEGORY_LAUNCHER)
                    it.component = ComponentName(packageName, activityName)
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                val details = if (activityName.isNotBlank()) "Activity: $activityName" else ""
                val resultData = AppOperationData(
                    operationType = "start",
                    packageName = packageName,
                    success = true,
                    details = details
                )
                ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to start app: Cannot find app launch Intent. Please check package name or Activity name."
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error starting app: ${e.message}"
            )
        }
    }

    /** 停止应用程序 */
    open suspend fun stopApp(tool: AITool): ToolResult {
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
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
            val resultData = AppOperationData(
                operationType = "stop",
                packageName = packageName,
                success = true,
                details = context.getString(R.string.sys_op_stop_app_requested)
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "停止应用时出现安全异常", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to stop app: ${e.message}. Requires KILL_BACKGROUND_PROCESSES permission."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error stopping app: ${e.message}"
            )
        }
    }

    /** 读取设备通知内容 获取当前设备上的通知信息 */
    open suspend fun getNotifications(tool: AITool): ToolResult {
        val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 10
        val includeOngoing =
            tool.parameters.find { it.name == "include_ongoing" }?.value?.toBoolean() ?: false

        val enabledListeners =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: ""
        val myPackageName = context.packageName

        val hasNotificationAccess = enabledListeners
            .split(":")
            .asSequence()
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == myPackageName }

        if (!hasNotificationAccess) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "打开通知使用权设置页面失败", e)
            }

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Cannot read notifications. This app needs to be authorized as a Notification Listener Service."
            )
        }

        return try {
            val notifications = OperitNotificationStore.snapshot(
                limit = limit,
                includeOngoing = includeOngoing
            )
            val resultData = NotificationData(
                notifications = notifications,
                timestamp = System.currentTimeMillis()
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取通知时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting notifications: ${e.message}"
            )
        }
    }

    /** 获取应用使用时长（前台使用时间） */
    open suspend fun getAppUsageTime(tool: AITool): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "App usage time is only supported on Android 5.0 and above."
            )
        }

        val requestedPackageName =
            tool.parameters.find { it.name == "package_name" }?.value?.trim().orEmpty()
        val sinceHours = tool.parameters.find { it.name == "since_hours" }?.value?.toIntOrNull() ?: 24
        val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 10
        val includeSystemApps =
            tool.parameters.find { it.name == "include_system_apps" }?.value?.toBoolean() ?: false

        if (sinceHours <= 0) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "since_hours must be a positive integer."
            )
        }

        if (limit <= 0) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "limit must be a positive integer."
            )
        }

        if (!hasUsageStatsAccess()) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "打开使用情况访问设置页面失败", e)
            }

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Cannot read app usage time. This app needs Usage Access permission. Settings page opened for you."
            )
        }

        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - sinceHours * 60L * 60L * 1000L
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val rawStats =
                usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                ) ?: emptyList()

            val aggregatedStats =
                rawStats
                    .groupBy { it.packageName.orEmpty() }
                    .mapNotNull { (packageName, stats) ->
                        if (packageName.isBlank()) {
                            return@mapNotNull null
                        }

                        val totalForegroundTimeMs = stats.sumOf { it.totalTimeInForeground }
                        if (totalForegroundTimeMs <= 0L) {
                            return@mapNotNull null
                        }

                        val lastTimeUsed = stats.maxOfOrNull { it.lastTimeUsed } ?: 0L
                        val applicationInfo =
                            try {
                                context.packageManager.getApplicationInfo(packageName, 0)
                            } catch (_: PackageManager.NameNotFoundException) {
                                null
                            }
                        val isSystemApp =
                            applicationInfo?.let {
                                (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            } ?: false

                        if (requestedPackageName.isBlank() && !includeSystemApps && isSystemApp) {
                            return@mapNotNull null
                        }

                        if (requestedPackageName.isNotBlank() && requestedPackageName != packageName) {
                            return@mapNotNull null
                        }

                        val appName =
                            if (applicationInfo != null) {
                                try {
                                    applicationInfo.loadLabel(context.packageManager).toString()
                                } catch (_: Exception) {
                                    packageName
                                }
                            } else {
                                packageName
                            }

                        AppUsageTimeEntry(
                            packageName = packageName,
                            appName = appName,
                            totalForegroundTimeMs = totalForegroundTimeMs,
                            lastTimeUsed = lastTimeUsed,
                            isSystemApp = isSystemApp
                        )
                    }
                    .sortedByDescending { it.totalForegroundTimeMs }

            val limitedEntries =
                if (requestedPackageName.isBlank()) aggregatedStats.take(limit) else aggregatedStats.take(1)

            val resultData =
                AppUsageTimeResultData(
                    startTime = startTime,
                    endTime = endTime,
                    sinceHours = sinceHours,
                    requestedPackageName = requestedPackageName.ifBlank { null },
                    includesSystemApps = includeSystemApps,
                    totalEntries = limitedEntries.size,
                    entries = limitedEntries
                )

            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "读取应用使用时长时出现权限异常", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Security exception when reading app usage time: ${e.message}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取应用使用时长时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting app usage time: ${e.message}"
            )
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        return BluetoothSessionManager.adapter(context)
    }

    private fun needsBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (!needsBluetoothConnectPermission()) {
            return true
        }
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
    }

    private fun requiredBluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun bluetoothConnectPermissionError(toolName: String): ToolResult {
        return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Bluetooth nearby devices permission is required. Call request_bluetooth_permission first."
        )
    }

    private fun bluetoothScanPermissionError(toolName: String): ToolResult {
        return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Bluetooth scan permission is required. Call request_bluetooth_permission first."
        )
    }

    private fun bluetoothStateName(state: Int): String {
        return when (state) {
            BluetoothAdapter.STATE_OFF -> "off"
            BluetoothAdapter.STATE_TURNING_ON -> "turning_on"
            BluetoothAdapter.STATE_ON -> "on"
            BluetoothAdapter.STATE_TURNING_OFF -> "turning_off"
            else -> "unknown"
        }
    }

    private fun bluetoothDeviceTypeName(type: Int): String {
        return BluetoothSessionManager.deviceTypeName(type)
    }

    private fun bluetoothBondStateName(state: Int): String {
        return BluetoothSessionManager.bondStateName(state)
    }

    open suspend fun requestBluetoothPermission(tool: AITool): ToolResult {
        if (hasBluetoothConnectPermission() && hasBluetoothScanPermission()) {
            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("Bluetooth permission granted")
            )
        }

        val results =
                WebSessionPermissionRequestCoordinator.requestPermissions(
                        context,
                        requiredBluetoothPermissions()
                )
        val granted =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    results[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                            results[Manifest.permission.BLUETOOTH_SCAN] == true
                } else {
                    results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                }
        return ToolResult(
                toolName = tool.name,
                success = granted,
                result = StringResultData(if (granted) "Bluetooth permission granted" else ""),
                error = if (granted) "" else "Bluetooth nearby devices permission was denied."
        )
    }

    open suspend fun getBluetoothState(tool: AITool): ToolResult {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothStateData(supported = false, enabled = false, state = "unsupported"),
                    error = ""
            )
        }

        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            val stateName = bluetoothStateName(adapter.state)
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            BluetoothStateData(
                                    supported = true,
                                    enabled = adapter.isEnabled,
                                    state = stateName
                            ),
                    error = ""
            )
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "读取蓝牙状态时出现权限异常", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Security exception when reading Bluetooth state: ${e.message}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取蓝牙状态时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading Bluetooth state: ${e.message}"
            )
        }
    }

    open suspend fun requestEnableBluetooth(tool: AITool): ToolResult {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Bluetooth is not supported on this device."
            )
        }

        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            if (adapter.isEnabled) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("Bluetooth is already enabled"),
                        error = ""
                )
            }

            withContext(Dispatchers.Main) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("Bluetooth enable request opened"),
                    error = ""
            )
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "请求开启蓝牙时出现权限异常", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Security exception when requesting Bluetooth enable: ${e.message}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求开启蓝牙时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error requesting Bluetooth enable: ${e.message}"
            )
        }
    }

    open suspend fun listBluetoothBondedDevices(tool: AITool): ToolResult {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Bluetooth is not supported on this device."
            )
        }

        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            val devices =
                    adapter.bondedDevices
                            .map { device ->
                                BluetoothDeviceData(
                                        name = device.name,
                                        address = device.address,
                                        type = bluetoothDeviceTypeName(device.type),
                                        bondState = bluetoothBondStateName(device.bondState)
                                )
                            }
                            .sortedWith(compareBy<BluetoothDeviceData> { it.name ?: "" }.thenBy { it.address })

            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothBondedDevicesData(devices),
                    error = ""
            )
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "读取已配对蓝牙设备时出现权限异常", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Security exception when listing bonded Bluetooth devices: ${e.message}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取已配对蓝牙设备时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error listing bonded Bluetooth devices: ${e.message}"
            )
        }
    }

    open suspend fun scanBluetoothDevices(tool: AITool): ToolResult {
        val durationMs = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 10000L
        val includeBle = tool.parameters.find { it.name == "include_ble" }?.value?.toBoolean() ?: true

        if (!hasBluetoothScanPermission()) {
            return bluetoothScanPermissionError(tool.name)
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.scan(context, durationMs, includeBle),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "扫描蓝牙设备时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error scanning Bluetooth devices: ${e.message}"
            )
        }
    }

    open suspend fun connectBluetooth(tool: AITool): ToolResult {
        val address = tool.parameters.find { it.name == "address" }?.value?.trim().orEmpty()
        val uuid = tool.parameters.find { it.name == "uuid" }?.value
        if (address.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide address parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.connectClassic(context, address, BluetoothSessionManager.parseUuid(uuid)),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "连接蓝牙设备时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error connecting Bluetooth device: ${e.message}")
        }
    }

    open suspend fun listenBluetooth(tool: AITool): ToolResult {
        val name = tool.parameters.find { it.name == "name" }?.value?.takeIf { it.isNotBlank() } ?: "Operit Bluetooth"
        val uuid = tool.parameters.find { it.name == "uuid" }?.value
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.listenClassic(context, name, BluetoothSessionManager.parseUuid(uuid)),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建蓝牙监听时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error listening for Bluetooth connections: ${e.message}")
        }
    }

    open suspend fun acceptBluetooth(tool: AITool): ToolResult {
        val listenerId = tool.parameters.find { it.name == "listener_session_id" }?.value?.trim().orEmpty()
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toIntOrNull() ?: 30000
        if (listenerId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide listener_session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.acceptClassic(listenerId, timeoutMs),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "接受蓝牙连接时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error accepting Bluetooth connection: ${e.message}")
        }
    }

    private fun bluetoothPayload(tool: AITool): ByteArray {
        val text = tool.parameters.find { it.name == "text" }?.value
        val dataBase64 = tool.parameters.find { it.name == "data_base64" }?.value
        return BluetoothSessionManager.decodePayload(text, dataBase64)
    }

    open suspend fun sendBluetooth(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.sendClassic(sessionId, bluetoothPayload(tool)),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "发送蓝牙数据时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error sending Bluetooth data: ${e.message}")
        }
    }

    open suspend fun readBluetooth(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        val maxBytes = tool.parameters.find { it.name == "max_bytes" }?.value?.toIntOrNull() ?: 4096
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 3000L
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.readClassic(sessionId, maxBytes, timeoutMs),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取蓝牙数据时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error reading Bluetooth data: ${e.message}")
        }
    }

    open suspend fun sendAndReadBluetooth(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        val maxBytes = tool.parameters.find { it.name == "max_bytes" }?.value?.toIntOrNull() ?: 4096
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 3000L
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.sendAndReadClassic(sessionId, bluetoothPayload(tool), maxBytes, timeoutMs),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "发送并读取蓝牙数据时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error sending and reading Bluetooth data: ${e.message}")
        }
    }

    open suspend fun closeBluetooth(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        return try {
            BluetoothSessionManager.closeAny(sessionId)
            ToolResult(toolName = tool.name, success = true, result = StringResultData("Bluetooth session closed"), error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "关闭蓝牙会话时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error closing Bluetooth session: ${e.message}")
        }
    }

    open suspend fun connectBle(tool: AITool): ToolResult {
        val address = tool.parameters.find { it.name == "address" }?.value?.trim().orEmpty()
        val autoConnect = tool.parameters.find { it.name == "auto_connect" }?.value?.toBoolean() ?: false
        if (address.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide address parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }

        return try {
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BluetoothSessionManager.connectBle(context, address, autoConnect),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "连接 BLE 设备时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error connecting BLE device: ${e.message}")
        }
    }

    open suspend fun discoverBleServices(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 10000L
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }
        return try {
            ToolResult(toolName = tool.name, success = true, result = BluetoothSessionManager.discoverBleServices(sessionId, timeoutMs), error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "发现 BLE 服务时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error discovering BLE services: ${e.message}")
        }
    }

    private fun uuidParameter(tool: AITool, name: String) =
            java.util.UUID.fromString(tool.parameters.find { it.name == name }?.value?.trim().orEmpty())

    private fun serviceUuid(tool: AITool) = uuidParameter(tool, "service_uuid")

    private fun characteristicUuid(tool: AITool) = uuidParameter(tool, "characteristic_uuid")

    open suspend fun readBleCharacteristic(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 5000L
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }
        return try {
            ToolResult(toolName = tool.name, success = true, result = BluetoothSessionManager.readBleCharacteristic(sessionId, serviceUuid(tool), characteristicUuid(tool), timeoutMs), error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取 BLE 特征时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error reading BLE characteristic: ${e.message}")
        }
    }

    open suspend fun writeBleCharacteristic(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }
        return try {
            ToolResult(toolName = tool.name, success = true, result = BluetoothSessionManager.writeBleCharacteristic(sessionId, serviceUuid(tool), characteristicUuid(tool), bluetoothPayload(tool)), error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "写入 BLE 特征时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error writing BLE characteristic: ${e.message}")
        }
    }

    open suspend fun writeAndReadBleCharacteristic(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 5000L
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }
        return try {
            BluetoothSessionManager.writeBleCharacteristic(
                    sessionId,
                    uuidParameter(tool, "write_service_uuid"),
                    uuidParameter(tool, "write_characteristic_uuid"),
                    bluetoothPayload(tool)
            )
            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            BluetoothSessionManager.readBleCharacteristic(
                                    sessionId,
                                    uuidParameter(tool, "read_service_uuid"),
                                    uuidParameter(tool, "read_characteristic_uuid"),
                                    timeoutMs
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "写入并读取 BLE 特征时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error writing and reading BLE characteristic: ${e.message}")
        }
    }

    open suspend fun subscribeBleCharacteristic(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        val enable = tool.parameters.find { it.name == "enable" }?.value?.toBoolean() ?: true
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }
        return try {
            ToolResult(toolName = tool.name, success = true, result = BluetoothSessionManager.subscribeBleCharacteristic(sessionId, serviceUuid(tool), characteristicUuid(tool), enable), error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "订阅 BLE 特征时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error subscribing BLE characteristic: ${e.message}")
        }
    }

    open suspend fun readBleNotifications(tool: AITool): ToolResult {
        val sessionId = tool.parameters.find { it.name == "session_id" }?.value?.trim().orEmpty()
        val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 20
        if (sessionId.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Must provide session_id parameter")
        }
        if (!hasBluetoothConnectPermission()) {
            return bluetoothConnectPermissionError(tool.name)
        }
        return try {
            ToolResult(toolName = tool.name, success = true, result = BluetoothSessionManager.readBleNotifications(sessionId, limit), error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取 BLE 通知时出错", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error reading BLE notifications: ${e.message}")
        }
    }

    /** 获取设备位置信息 通过系统API获取当前设备位置 */
    suspend fun getDeviceLocation(tool: AITool): ToolResult {
        val timeout = tool.parameters.find { it.name == "timeout" }?.value?.toIntOrNull() ?: 10
        val highAccuracy =
                tool.parameters.find { it.name == "high_accuracy" }?.value?.toBoolean() ?: false
        val includeAddress =
                tool.parameters.find { it.name == "include_address" }?.value?.toBoolean() ?: true

        return try {
            // 检查位置权限
            val hasFineLocationPermission =
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasCoarseLocationPermission =
                    context.checkSelfPermission(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            // 如果没有任何位置权限，返回错误
            if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.sys_op_location_permission_not_granted)
                )
            }

            // 根据精度要求和权限情况决定使用哪种精度
            val actualHighAccuracy = highAccuracy && hasFineLocationPermission

            // 使用Dispatchers.Main确保在主线程上执行位置操作
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            val locationResult =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        kotlinx.coroutines.suspendCancellableCoroutine<Location?> { continuation ->
                            val locationManager =
                                    context.getSystemService(Context.LOCATION_SERVICE) as
                                            LocationManager

                            // 选择合适的位置提供者
                            val provider =
                                    when {
                                        actualHighAccuracy &&
                                                locationManager.isProviderEnabled(
                                                        LocationManager.GPS_PROVIDER
                                                ) -> LocationManager.GPS_PROVIDER
                                        locationManager.isProviderEnabled(
                                                LocationManager.NETWORK_PROVIDER
                                        ) -> LocationManager.NETWORK_PROVIDER
                                        locationManager.isProviderEnabled(
                                                LocationManager.PASSIVE_PROVIDER
                                        ) -> LocationManager.PASSIVE_PROVIDER
                                        else -> null
                                    }

                            if (provider == null) {
                                continuation.resume(null) { AppLogger.e(TAG, "位置请求取消", it) }
                                return@suspendCancellableCoroutine
                            }

                            // 尝试获取最后已知位置
                            val lastKnownLocation =
                                    try {
                                        if (actualHighAccuracy && hasFineLocationPermission) {
                                            locationManager.getLastKnownLocation(
                                                    LocationManager.GPS_PROVIDER
                                            )
                                                    ?: locationManager.getLastKnownLocation(
                                                            LocationManager.NETWORK_PROVIDER
                                                    )
                                        } else if (hasCoarseLocationPermission) {
                                            locationManager.getLastKnownLocation(
                                                    LocationManager.NETWORK_PROVIDER
                                            )
                                                    ?: locationManager.getLastKnownLocation(
                                                            LocationManager.PASSIVE_PROVIDER
                                                    )
                                        } else {
                                            null
                                        }
                                    } catch (e: SecurityException) {
                                        AppLogger.e(TAG, "获取最后已知位置失败", e)
                                        null
                                    }

                            // 如果有最后已知位置且足够新（10分钟内），直接返回
                            if (lastKnownLocation != null &&
                                            System.currentTimeMillis() - lastKnownLocation.time <
                                                    10 * 60 * 1000
                            ) {
                                continuation.resume(lastKnownLocation) { AppLogger.e(TAG, "位置请求取消", it) }
                                return@suspendCancellableCoroutine
                            }

                            // 否则请求位置更新
                            val locationListener =
                                    object : android.location.LocationListener {
                                        override fun onLocationChanged(location: Location) {
                                            locationManager.removeUpdates(this)
                                            continuation.resume(location) {
                                                AppLogger.e(TAG, "位置请求取消", it)
                                            }
                                        }

                                        override fun onProviderDisabled(provider: String) {
                                            // 如果提供者被禁用，尝试使用最后已知位置
                                            if (!continuation.isCompleted) {
                                                if (lastKnownLocation != null) {
                                                    continuation.resume(lastKnownLocation) {
                                                        AppLogger.e(TAG, "位置请求取消", it)
                                                    }
                                                } else {
                                                    continuation.resume(null) {
                                                        AppLogger.e(TAG, "位置请求取消", it)
                                                    }
                                                }
                                            }
                                        }

                                        override fun onProviderEnabled(provider: String) {
                                            // 不需要处理
                                        }

                                        @Deprecated("Deprecated in Java")
                                        override fun onStatusChanged(
                                                provider: String,
                                                status: Int,
                                                extras: android.os.Bundle
                                        ) {
                                            // 不需要处理
                                        }
                                    }

                            try {
                                // 设置位置请求参数
                                locationManager.requestLocationUpdates(
                                        provider,
                                        0, // 最小时间间隔
                                        0f, // 最小距离变化
                                        locationListener
                                )

                                // 设置超时
                                kotlinx.coroutines.GlobalScope.launch {
                                    delay(timeout * 1000L)
                                    // 在主线程上移除更新和恢复协程
                                    kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.Main
                                    ) {
                                        if (!continuation.isCompleted) {
                                            locationManager.removeUpdates(locationListener)
                                            // 如果超时，尝试使用最后已知位置
                                            continuation.resume(lastKnownLocation) {
                                                AppLogger.e(TAG, "位置请求取消", it)
                                            }
                                        }
                                    }
                                }

                                // 如果协程被取消，移除位置更新
                                continuation.invokeOnCancellation {
                                    try {
                                        // 确保在主线程上移除位置更新
                                        kotlinx.coroutines.runBlocking(
                                                kotlinx.coroutines.Dispatchers.Main
                                        ) { locationManager.removeUpdates(locationListener) }
                                    } catch (e: Exception) {
                                        AppLogger.e(TAG, "移除位置更新失败", e)
                                    }
                                }
                            } catch (e: SecurityException) {
                                continuation.resume(lastKnownLocation) { AppLogger.e(TAG, "位置请求取消", it) }
                                AppLogger.e(TAG, "请求位置更新失败", e)
                            }
                        }
                    }

            // 处理位置结果
            if (locationResult == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.sys_op_cannot_get_location)
                )
            }

            val resultData =
                    if (includeAddress) {
                        // 获取地址信息
                        val addressInfo =
                                getAddressFromLocation(
                                        locationResult.latitude,
                                        locationResult.longitude
                                )

                        LocationData(
                                latitude = locationResult.latitude,
                                longitude = locationResult.longitude,
                                accuracy = locationResult.accuracy,
                                provider = locationResult.provider ?: "unknown",
                                timestamp = locationResult.time,
                                rawData = locationResult.toString(),
                                city = addressInfo.city,
                                address = addressInfo.address,
                                country = addressInfo.country,
                                province = addressInfo.province
                        )
                    } else {
                        LocationData(
                                latitude = locationResult.latitude,
                                longitude = locationResult.longitude,
                                accuracy = locationResult.accuracy,
                                provider = locationResult.provider ?: "unknown",
                                timestamp = locationResult.time,
                                rawData = locationResult.toString()
                        )
                    }

            return ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取位置信息时出错", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error getting location information: ${e.message}"
            )
        }
    }

    /**
     * 从经纬度获取地址信息
     * @param latitude 纬度
     * @param longitude 经度
     * @return 包含地址信息的数据类
     */
    private fun getAddressFromLocation(latitude: Double, longitude: Double): AddressInfo {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            // 尝试获取地址
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]

                return AddressInfo(
                        address = address.getAddressLine(0) ?: "",
                        city = address.locality ?: address.subAdminArea ?: "",
                        province = address.adminArea ?: "",
                        country = address.countryName ?: "",
                        postalCode = address.postalCode ?: ""
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取地址信息时出错", e)
        }

        // 如果无法获取地址信息，返回空对象
        return AddressInfo("", "", "", "", "")
    }

    /** 地址信息数据类 */
    data class AddressInfo(
            val address: String, // 完整地址
            val city: String, // 城市
            val province: String, // 省/州
            val country: String, // 国家
            val postalCode: String // 邮政编码
    )
}

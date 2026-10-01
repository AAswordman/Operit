package com.ai.assistance.operit.ui.features.demo.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class SystemPermission { NOTIFICATIONS, APP_LIST, USAGE_ACCESS, WRITE_SETTINGS }

data class SystemPermissionStatus(
    val notifications: Boolean = false,
    val appList: Boolean = false,
    val usageAccess: Boolean = false,
    val writeSettings: Boolean = false
) {
    fun isAvailable(permission: SystemPermission): Boolean = when (permission) {
        SystemPermission.NOTIFICATIONS -> notifications
        SystemPermission.APP_LIST -> appList
        SystemPermission.USAGE_ACCESS -> usageAccess
        SystemPermission.WRITE_SETTINGS -> writeSettings
    }
}

object SystemPermissionSettings {
    fun readStatus(context: Context): SystemPermissionStatus {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val usageMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        // 应用列表没有用户可切换的独立授权；这里检查包可见性权限是否已声明。
        val appListAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.contains(Manifest.permission.QUERY_ALL_PACKAGES) == true
        return SystemPermissionStatus(
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            appList = appListAvailable,
            usageAccess = usageMode == AppOpsManager.MODE_ALLOWED,
            writeSettings = Settings.System.canWrite(context)
        )
    }

    fun open(context: Context, permission: SystemPermission): Boolean {
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        val target = when (permission) {
            SystemPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            SystemPermission.APP_LIST -> Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
            SystemPermission.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SystemPermission.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        try {
            context.startActivity(target)
            return true
        } catch (e: Exception) {
            try {
                context.startActivity(appDetails)
                return true
            } catch (fallbackError: Exception) {
                return false
            }
        }
    }
}

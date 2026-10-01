package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import com.ai.assistance.operit.core.tools.permissions.PermissionBackend
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** 实例按后端缓存，模式到后端的选择在每次操作前重新计算。 */
class ShellExecutorFactory {
    companion object {
        private val executors = mutableMapOf<PermissionBackend, ShellExecutor>()

        @Synchronized
        fun getBackendExecutor(context: Context, backend: PermissionBackend): ShellExecutor =
            executors.getOrPut(backend) {
                val appContext = context.applicationContext
                val executor = when (backend) {
                    PermissionBackend.STANDARD -> StandardShellExecutor(appContext)
                    PermissionBackend.SHIZUKU -> DebuggerShellExecutor(appContext)
                    PermissionBackend.ROOT -> RootShellExecutor(appContext)
                    PermissionBackend.ACCESSIBILITY -> error("Accessibility does not provide a shell backend")
                }
                executor.initialize()
                executor
            }

        fun getExecutor(context: Context, permissionLevel: AndroidPermissionLevel): ShellExecutor =
            getBackendExecutor(context, PermissionCapabilityResolver.shellSnapshot(context, permissionLevel).shellBackend)

        fun getHighestAvailableExecutor(context: Context): Pair<ShellExecutor, ShellExecutor.PermissionStatus> {
            val executor = getExecutor(context, AndroidPermissionLevel.ROOT)
            return executor to executor.hasPermission()
        }

        fun getUserPreferredExecutor(context: Context): ShellExecutor =
            getExecutor(context, androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD)

        fun getHighestAvailableExecutorLegacy(context: Context): ShellExecutor =
            getHighestAvailableExecutor(context).first

        @Synchronized
        fun clearCache(permissionLevel: AndroidPermissionLevel? = null) {
            when (permissionLevel) {
                null -> executors.clear()
                AndroidPermissionLevel.STANDARD -> executors.remove(PermissionBackend.STANDARD)
                AndroidPermissionLevel.ADMIN -> executors.remove(PermissionBackend.SHIZUKU)
                AndroidPermissionLevel.ROOT -> executors.remove(PermissionBackend.ROOT)
            }
        }

        fun getAvailableExecutors(context: Context): Map<AndroidPermissionLevel, Pair<ShellExecutor, ShellExecutor.PermissionStatus>> =
            AndroidPermissionLevel.values().associateWith { level ->
                val executor = getExecutor(context, level)
                executor to executor.hasPermission()
            }
    }
}

package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.core.tools.permissions.PermissionBackend
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** 监听器实例按后端缓存，授权状态不随实例一起缓存。 */
class ActionListenerFactory {
    companion object {
        private val listeners = mutableMapOf<PermissionBackend, ActionListener>()

        @Synchronized
        private fun getBackendListener(context: Context, backend: PermissionBackend): ActionListener =
            listeners.getOrPut(backend) {
                val appContext = context.applicationContext
                val listener = when (backend) {
                    PermissionBackend.STANDARD -> StandardActionListener(appContext)
                    PermissionBackend.ACCESSIBILITY -> AccessibilityActionListener(appContext)
                    PermissionBackend.SHIZUKU -> DebuggerActionListener(appContext)
                    PermissionBackend.ROOT -> RootActionListener(appContext)
                }
                listener.initialize()
                listener
            }

        suspend fun getListener(context: Context, permissionLevel: AndroidPermissionLevel): ActionListener {
            val capabilities = PermissionCapabilityResolver.uiSnapshot(context, permissionLevel)
            return getBackendListener(context, capabilities.uiBackend())
        }

        suspend fun getHighestAvailableListener(context: Context): Pair<ActionListener, ActionListener.PermissionStatus> {
            val listener = getListener(context, AndroidPermissionLevel.ROOT)
            return listener to listener.hasPermission()
        }

        suspend fun getUserPreferredListener(context: Context): ActionListener =
            getListener(context, androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD)

        suspend fun getHighestAvailableListenerLegacy(context: Context): ActionListener =
            getHighestAvailableListener(context).first

        @Synchronized
        fun clearCache(permissionLevel: AndroidPermissionLevel? = null) {
            when (permissionLevel) {
                null -> listeners.clear()
                AndroidPermissionLevel.STANDARD -> listeners.remove(PermissionBackend.STANDARD)
                AndroidPermissionLevel.ADMIN -> {
                    listeners.remove(PermissionBackend.ACCESSIBILITY)
                    listeners.remove(PermissionBackend.SHIZUKU)
                }
                AndroidPermissionLevel.ROOT -> listeners.remove(PermissionBackend.ROOT)
            }
        }

        suspend fun getAvailableListeners(context: Context): Map<AndroidPermissionLevel, Pair<ActionListener, ActionListener.PermissionStatus>> {
            val result = mutableMapOf<AndroidPermissionLevel, Pair<ActionListener, ActionListener.PermissionStatus>>()
            for (level in AndroidPermissionLevel.values()) {
                val listener = getListener(context, level)
                result[level] = listener to listener.hasPermission()
            }
            return result
        }

        suspend fun stopAllListeners(): Boolean {
            val snapshot = synchronized(this) { listeners.values.toList() }
            var allStopped = true
            for (listener in snapshot) {
                if (listener.isListening() && !listener.stopListening()) allStopped = false
            }
            return allStopped
        }
    }
} 
package com.ai.assistance.operit.core.tools.packTool.metadata

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 插件安装与实际执行记录，不从文件修改时间推断历史事件。 */
data class PluginActivity(
    val installedAt: Long = 0L,
    val lastUsedAt: Long = 0L
)

class PluginActivityStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "plugin_activity", Context.MODE_PRIVATE
    )
    private val mutableActivities = MutableStateFlow(readActivities())
    val activities: StateFlow<Map<String, PluginActivity>> = mutableActivities.asStateFlow()

    @Synchronized
    fun recordInstallation(packageName: String, timestamp: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("installed:$packageName", timestamp).apply()
        val current = mutableActivities.value[packageName] ?: PluginActivity()
        mutableActivities.value = mutableActivities.value +
            (packageName to current.copy(installedAt = timestamp))
    }

    @Synchronized
    fun recordUse(packageName: String, timestamp: Long = System.currentTimeMillis()) {
        val current = mutableActivities.value[packageName] ?: PluginActivity()
        val lastUsedAt = maxOf(current.lastUsedAt, timestamp)
        preferences.edit().putLong("used:$packageName", lastUsedAt).apply()
        mutableActivities.value = mutableActivities.value +
            (packageName to current.copy(lastUsedAt = lastUsedAt))
    }

    @Synchronized
    fun remove(packageName: String) {
        preferences.edit()
            .remove("installed:$packageName")
            .remove("used:$packageName")
            .apply()
        mutableActivities.value = mutableActivities.value - packageName
    }

    private fun readActivities(): Map<String, PluginActivity> {
        val values = preferences.all
        val names = values.keys.mapNotNull { key ->
            when {
                key.startsWith("installed:") -> key.removePrefix("installed:")
                key.startsWith("used:") -> key.removePrefix("used:")
                else -> null
            }
        }.toSet()
        return names.associateWith { name ->
            PluginActivity(
                installedAt = preferences.getLong("installed:$name", 0L),
                lastUsedAt = preferences.getLong("used:$name", 0L)
            )
        }
    }

    companion object {
        @Volatile
        private var instance: PluginActivityStore? = null

        fun getInstance(context: Context): PluginActivityStore =
            instance ?: synchronized(this) {
                instance ?: PluginActivityStore(context).also { instance = it }
            }
    }
}

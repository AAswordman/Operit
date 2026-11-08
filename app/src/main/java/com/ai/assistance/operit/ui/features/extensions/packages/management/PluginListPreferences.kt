package com.ai.assistance.operit.ui.features.extensions.packages.management

import android.content.Context

class PluginListPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "plugin_list_presentation", Context.MODE_PRIVATE
    )

    fun hasInvalidValues(): Boolean {
        val saved = preferences.all
        val mode = saved["mode"]
        val filter = saved["filter"]
        val invalid = (saved.containsKey("mode") && (mode !is String || PluginSortMode.entries.none { it.name == mode })) ||
            (saved.containsKey("filter") && (filter !is String || PluginFilter.entries.none { it.name == filter })) ||
            (saved.containsKey("descending") && saved["descending"] !is Boolean)
        if (invalid) {
            com.ai.assistance.operit.util.logging.AppLogger.w("PluginListPreferences", "插件列表展示偏好无效，等待用户重置")
        }
        return invalid
    }

    fun resetPresentation() {
        preferences.edit().remove("mode").remove("filter").remove("descending").apply()
    }

    fun readSort(): PluginSortSelection = PluginSortSelection(
        mode = PluginSortMode.valueOf(preferences.getString("mode", PluginSortMode.CUSTOM.name)!!),
        descending = preferences.getBoolean("descending", false)
    )

    fun readFilter(): PluginFilter =
        PluginFilter.valueOf(preferences.getString("filter", PluginFilter.ALL.name)!!)

    fun saveSort(selection: PluginSortSelection) {
        preferences.edit()
            .putString("mode", selection.mode.name)
            .putBoolean("descending", selection.descending)
            .apply()
    }

    fun saveFilter(filter: PluginFilter) {
        preferences.edit().putString("filter", filter.name).apply()
    }
}

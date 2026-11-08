package com.ai.assistance.operit.ui.features.extensions.packages.management

import java.text.Collator
import java.util.Locale

enum class PluginSortMode { CUSTOM, NAME, SIZE, INSTALLED, LAST_USED, ENABLED }
enum class PluginFilter { ALL, ENABLED, DISABLED, BUILT_IN }

data class PluginSortSelection(
    val mode: PluginSortMode = PluginSortMode.CUSTOM,
    val descending: Boolean = false
)

data class PluginSortEntry(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean,
    val fileSizeBytes: Long,
    val installedAt: Long,
    val lastUsedAt: Long
)

fun sortPluginEntries(
    entries: List<PluginSortEntry>,
    selection: PluginSortSelection,
    customOrder: List<String>
): List<PluginSortEntry> {
    if (selection.mode == PluginSortMode.CUSTOM) {
        val ranks = customOrder.withIndex().associate { it.value to it.index }
        val result = entries.sortedBy { ranks[it.packageName] ?: Int.MAX_VALUE }
        return if (selection.descending) result.reversed() else result
    }

    // 自动排序不读取手动顺序，避免拖动的插件获得永久置顶权重。
    val collator = Collator.getInstance(Locale.CHINA).apply { strength = Collator.PRIMARY }
    val direction = if (selection.descending) -1 else 1
    return entries.sortedWith { left, right ->
        val comparison = when (selection.mode) {
            PluginSortMode.NAME -> direction * collator.compare(left.displayName, right.displayName)
            PluginSortMode.SIZE -> direction * left.fileSizeBytes.compareTo(right.fileSizeBytes)
            PluginSortMode.INSTALLED -> compareRecordedTime(left.installedAt, right.installedAt, direction)
            PluginSortMode.LAST_USED -> compareRecordedTime(left.lastUsedAt, right.lastUsedAt, direction)
            PluginSortMode.ENABLED -> direction * right.enabled.compareTo(left.enabled)
            PluginSortMode.CUSTOM -> error("Custom order is handled separately")
        }
        if (comparison != 0) comparison else left.packageName.compareTo(right.packageName)
    }
}

private fun compareRecordedTime(left: Long, right: Long, direction: Int): Int {
    // 没有历史记录的插件在两个方向下都放到末尾。
    if (left == 0L && right != 0L) return 1
    if (right == 0L && left != 0L) return -1
    return direction * left.compareTo(right)
}

fun mergeVisiblePluginOrder(
    allPluginNames: List<String>,
    previousOrder: List<String>,
    visibleOrder: List<String>
): List<String> {
    val installed = allPluginNames.toSet()
    val completeOrder = previousOrder.filter { it in installed }.distinct().toMutableList()
    completeOrder.addAll(allPluginNames.filterNot { it in completeOrder })
    val visible = visibleOrder.toSet()
    val reordered = visibleOrder.iterator()
    // 筛选或搜索后拖动只替换可见位置，不丢失隐藏插件的顺序。
    return completeOrder.map { if (it in visible) reordered.next() else it }
}

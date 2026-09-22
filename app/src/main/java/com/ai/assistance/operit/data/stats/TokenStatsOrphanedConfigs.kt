package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.preferences.ModelConfigManager

/**
 * 统计页里已经没有对应模型配置的用量身份。
 * 空 configId 是未按配置拆分的旧汇总，不算无效配置。
 */
object TokenStatsOrphanedConfigs {
    fun orphanedConfigIds(
        recordedConfigIds: Collection<String>,
        activeConfigIds: Collection<String>,
        protectedIds: Set<String> = setOf(ModelConfigManager.DEFAULT_CONFIG_ID),
    ): List<String> {
        val active = activeConfigIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (active.isEmpty()) return emptyList()
        return recordedConfigIds
            .map { it.trim() }
            .filter { configId ->
                configId.isNotEmpty() &&
                    configId !in protectedIds &&
                    configId !in active
            }
            .distinct()
            .sorted()
    }
}

package com.ai.assistance.operit.data.stats

/**
 * 评审 P3：模型维度展示名的拼装抽成纯函数，ViewModel 与单测共用同一实现。
 *
 * CONFIG_MODEL 分组下把展示名拼装为"配置名/模型名"：
 * - configId 为 null（MERGED_MODEL 合并组跨配置，无单一配置归属）或空字符串
 *   （legacy 累计导入记录没有配置概念）时保持模型名原样；
 * - 配置已删除（不在 configurationNames 中且 configId 非空）时使用删除提示文案，
 *   与配置详情卡的口径一致。
 */
object TokenStatsDisplayNames {

    fun resolve(
        models: List<TokenStatsDisplayModelBreakdown>,
        configurationNames: Map<String, String>,
        deletedConfigLabel: String,
    ): List<TokenStatsDisplayModelBreakdown> =
        models.map { model ->
            val configId = model.configId
            if (configId.isNullOrEmpty()) {
                model
            } else {
                val configName = configurationNames[configId] ?: deletedConfigLabel
                model.copy(displayName = "$configName/${model.displayName}")
            }
        }
}

package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.ui.features.tokenstats.TokenStatsUiState
import com.ai.assistance.operit.ui.features.tokenstats.TokenUsageStatisticsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 评审 P3：覆盖 ViewModel 层抽出的两个纯函数——展示名拼装（配置名/模型名、
 * 删除配置兜底）与分组切换清空选中集。
 */
class TokenStatsDisplayNamesTest {

    private fun breakdown(
        displayModelId: String,
        displayName: String,
        configId: String?,
    ): TokenStatsDisplayModelBreakdown =
        TokenStatsDisplayModelBreakdown(
            displayModelId = displayModelId,
            displayName = displayName,
            normalizedModel = displayName.trim().lowercase(),
            totals = emptyTotals(),
            identities = emptyList(),
            providerModels = listOf(displayModelId),
            configId = configId,
        )

    private fun emptyTotals(): TokenStatsTotals {
        val empty = TokenStatsTokenAggregate(0L, 0L, 0L, 0L)
        val cost = TokenStatsCostSummary(
            currency = com.ai.assistance.operit.data.collects.PricingCurrency.CNY,
            knownAmount = 0.0,
            unknownContributionCount = 0L,
            totalContributionCount = 0L,
            rateUsed = 1.0,
            originalCurrencyAmounts = emptyMap(),
        )
        return TokenStatsTotals(
            requests = 0L,
            uncachedInput = empty,
            cachedInput = empty,
            totalInput = empty,
            output = empty,
            totalTokens = empty,
            cost = cost,
        )
    }

    @Test
    fun `config grouped model gets config name prefix`() {
        val models = listOf(breakdown("config:c1|model:glm-5.2", "glm-5.2", "c1"))
        val resolved = TokenStatsDisplayNames.resolve(models, mapOf("c1" to "商汤"), "已删除配置")
        assertEquals("商汤/glm-5.2", resolved.single().displayName)
    }

    @Test
    fun `merged grouping keeps plain model name`() {
        val models = listOf(breakdown("model:glm-5.2", "glm-5.2", configId = null))
        val resolved = TokenStatsDisplayNames.resolve(models, mapOf("c1" to "商汤"), "已删除配置")
        assertEquals("glm-5.2", resolved.single().displayName)
    }

    @Test
    fun `legacy empty configId keeps plain model name`() {
        val models = listOf(breakdown("config:|model:glm-5.2", "glm-5.2", configId = ""))
        val resolved = TokenStatsDisplayNames.resolve(models, emptyMap(), "已删除配置")
        assertEquals("glm-5.2", resolved.single().displayName)
    }

    @Test
    fun `deleted configuration falls back to deleted label`() {
        val models = listOf(breakdown("config:c9|model:glm-5.2", "glm-5.2", "c9"))
        val resolved = TokenStatsDisplayNames.resolve(models, emptyMap(), "已删除配置")
        assertEquals("已删除配置/glm-5.2", resolved.single().displayName)
    }

    @Test
    fun `switching grouping clears model selection`() {
        val state = TokenStatsUiState(
            selectedModels = setOf("config:c1|model:glm-5.2"),
            modelGrouping = TokenStatsGrouping.CONFIG_MODEL,
        )
        val switched = TokenUsageStatisticsViewModel.applyGroupingChange(
            state,
            TokenStatsGrouping.MERGED_MODEL,
        )
        assertEquals(TokenStatsGrouping.MERGED_MODEL, switched.modelGrouping)
        assertEquals(emptySet<String>(), switched.selectedModels)
        // 其余状态字段保持不变
        assertEquals(state.loading, switched.loading)
        assertEquals(state.targetCurrency, switched.targetCurrency)
    }
}

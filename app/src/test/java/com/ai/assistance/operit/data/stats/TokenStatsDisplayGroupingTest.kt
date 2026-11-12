package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenStatsDisplayGroupingTest {

    private fun row(
        provider: String,
        model: String,
        configId: String,
        requests: Long = 1L,
        outputTokens: Long = 100L,
    ): TokenUsageModelAggregateRow =
        TokenUsageModelAggregateRow(
            provider = provider,
            model = model,
            configId = configId,
            requests = requests,
            uncachedInputTokens = 10L,
            uncachedInputKnown = requests,
            cachedInputTokens = 0L,
            cachedInputKnown = requests,
            cacheWriteTokens = 0L,
            cacheWriteKnown = requests,
            totalInputTokens = 10L,
            totalInputKnown = requests,
            outputTokens = outputTokens,
            outputKnown = requests,
        )

    private val prices = TokenPriceSettingsSnapshot(
        providerModels = emptyMap(),
        configs = emptyMap(),
    )

    private fun params(grouping: TokenStatsGrouping) = TokenStatsQueryParams(
        targetCurrency = PricingCurrency.CNY,
        grouping = grouping,
    )

    @Test
    fun `config grouping splits identical model across configurations`() {
        val rows = listOf(
            row("OPENAI_GENERIC", "glm-5.2", "config-a"),
            row("OPENAI_GENERIC", "glm-5.2", "config-b"),
        )
        val models = TokenStatsQueryService.buildDisplayModels(rows, prices, params(TokenStatsGrouping.CONFIG_MODEL))

        assertEquals(2, models.size)
        assertEquals(
            setOf("config:config-a|model:glm-5.2", "config:config-b|model:glm-5.2"),
            models.map { it.displayModelId }.toSet(),
        )
        models.forEach { breakdown ->
            assertEquals(1, breakdown.identities.size)
            assertEquals(breakdown.identities.first().configId, breakdown.configId)
        }
    }

    @Test
    fun `config grouping keeps legacy empty configId as its own group`() {
        val rows = listOf(
            row("OPENAI_GENERIC", "glm-5.2", ""),
            row("OPENAI_GENERIC", "glm-5.2", "config-a"),
        )
        val models = TokenStatsQueryService.buildDisplayModels(rows, prices, params(TokenStatsGrouping.CONFIG_MODEL))

        assertEquals(2, models.size)
        val legacy = models.first { it.configId == "" }
        assertEquals("config:|model:glm-5.2", legacy.displayModelId)
        assertEquals("glm-5.2", legacy.displayName)
    }

    @Test
    fun `merged grouping combines identical model across configurations`() {
        val rows = listOf(
            row("OPENAI_GENERIC", "glm-5.2", "config-a"),
            row("OPENAI_GENERIC", "glm-5.2", "config-b"),
            row("OPENAI_GENERIC", "GLM-5.2", "config-a"),
        )
        val models = TokenStatsQueryService.buildDisplayModels(rows, prices, params(TokenStatsGrouping.MERGED_MODEL))

        assertEquals(1, models.size)
        assertEquals("model:glm-5.2", models.single().displayModelId)
        assertNull(models.single().configId)
        assertEquals(3, models.single().identities.size)
        assertEquals(3L, models.single().totals.requests)
    }

    @Test
    fun `config grouping does not merge different models of same configuration`() {
        val rows = listOf(
            row("OPENAI_GENERIC", "glm-5.2", "config-a"),
            row("OPENAI_GENERIC", "deepseek-v4-flash", "config-a"),
        )
        val models = TokenStatsQueryService.buildDisplayModels(rows, prices, params(TokenStatsGrouping.CONFIG_MODEL))

        assertEquals(2, models.size)
        assertEquals(
            setOf("config:config-a|model:glm-5.2", "config:config-a|model:deepseek-v4-flash"),
            models.map { it.displayModelId }.toSet(),
        )
    }
}

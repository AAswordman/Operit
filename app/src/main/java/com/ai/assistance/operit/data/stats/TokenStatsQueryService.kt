package com.ai.assistance.operit.data.stats

import android.content.Context
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.dao.TokenUsageActivityDayRow
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.normalizeProviderModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** SQL-backed statistics queries. Only aggregate rows leave Room. */
object TokenStatsQueryService {
    suspend fun lifetimeOverview(
        context: Context,
        params: TokenStatsQueryParams,
    ): TokenStatsLifetimeOverview {
        val repository = TokenUsageRepository.getInstance(context)
        return repository.withDao { dao ->
            val requestRows = dao.aggregateModelsForLifetime(
                providerModels = params.providerModels.queryValues(),
                allModels = params.providerModels == null,
            )
            val prices = dao.getAllStatsModels().toPriceSnapshot()
            TokenStatsLifetimeOverview(
                totals = combineTotals(requestRows.map { it.toTotals(prices, params) }, params),
                displayModels = buildDisplayModels(requestRows, prices, params),
            )
        }
    }

    suspend fun rangeData(
        context: Context,
        range: TokenStatsTimeRange,
        params: TokenStatsQueryParams,
        zone: ZoneId,
    ): TokenStatsRangeData {
        val repository = TokenUsageRepository.getInstance(context)
        return repository.withDao { dao ->
            val prices = dao.getAllStatsModels().toPriceSnapshot()
            val modelRows = dao.aggregateModelsInRange(
                startMs = range.startMs,
                endMs = range.endMs,
                providerModels = params.providerModels.queryValues(),
                allModels = params.providerModels == null,
            )
            val displayModels = buildDisplayModels(modelRows, prices, params)
            val summary = combineTotals(displayModels.map(TokenStatsDisplayModelBreakdown::totals), params)
            val granularity = TokenStatsTimeRanges.granularityFor(range)
            val starts = TokenStatsTimeRanges.bucketStarts(range, granularity, zone)
            val buckets = starts.mapIndexed { index, bucketStart ->
                val bucketEnd = minOf(
                    range.endMs,
                    TokenStatsTimeRanges.bucketEndMs(starts, index, granularity, zone),
                )
                val bucketRows = dao.aggregateModelsInRange(
                    startMs = maxOf(range.startMs, bucketStart),
                    endMs = bucketEnd,
                    providerModels = params.providerModels.queryValues(),
                    allModels = params.providerModels == null,
                )
                val models = buildDisplayModels(bucketRows, prices, params)
                TokenStatsTrendBucket(
                    bucketStartMs = bucketStart,
                    bucketEndMs = bucketEnd,
                    totals = combineTotals(models.map(TokenStatsDisplayModelBreakdown::totals), params),
                    byModel = models.associate { it.displayModelId to it.totals.toModelBucket() },
                )
            }
            TokenStatsRangeData(
                range = range,
                granularity = granularity,
                eventCount = summary.totalTokens.totalEventCount,
                summary = summary,
                buckets = buckets,
                displayModels = displayModels,
            )
        }
    }

    internal suspend fun earliestOccurredDate(
        context: Context,
        params: TokenStatsQueryParams,
        zone: ZoneId,
    ): LocalDate? {
        val repository = TokenUsageRepository.getInstance(context)
        return repository.withDao { dao ->
            dao.getEarliestOccurredAtMs(
                providerModels = params.providerModels.queryValues(),
                allModels = params.providerModels == null,
            )?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        }
    }

    internal suspend fun activitySnapshot(
        context: Context,
        range: TokenStatsTimeRange,
        params: TokenStatsQueryParams,
        zone: ZoneId,
    ): TokenActivitySnapshot {
        val repository = TokenUsageRepository.getInstance(context)
        return repository.withDao { dao ->
            val days = dao.getActivityDaysInRange(
                startMs = range.startMs,
                endMs = range.endMs,
                providerModels = params.providerModels.queryValues(),
                allModels = params.providerModels == null,
            )
            TokenActivitySnapshot(
                zone = zone,
                dayTotals =
                    days.groupBy(TokenUsageActivityDayRow::localDate).mapValues { (_, rows) ->
                        rows.fold(0L) { total, row -> TokenCostCalculator.saturatedAdd(total, row.tokens) }
                    }.mapKeys { (date, _) -> LocalDate.parse(date) },
            )
        }
    }

    private fun buildDisplayModels(
        rows: List<TokenUsageModelAggregateRow>,
        prices: TokenPriceSettingsSnapshot,
        params: TokenStatsQueryParams,
    ): List<TokenStatsDisplayModelBreakdown> =
        rows.groupBy { row -> displayModelIdFor(row.model) }
            .map { (displayModelId, groupRows) ->
                val identities = groupRows.map { row ->
                    TokenStatsIdentityBreakdown(
                        configId = row.configId,
                        provider = row.provider,
                        model = row.model,
                        totals = row.toTotals(prices, params),
                    )
                }
                TokenStatsDisplayModelBreakdown(
                    displayModelId = displayModelId,
                    displayName = groupRows.first().model,
                    normalizedModel = groupRows.first().model.trim().lowercase(),
                    totals = combineTotals(identities.map(TokenStatsIdentityBreakdown::totals), params),
                    identities = identities,
                    providerModels = groupRows.map(TokenUsageModelAggregateRow::providerModel).distinct(),
                )
            }
            .sortedByDescending { it.totals.totalTokens.knownSum }

    private fun TokenUsageModelAggregateRow.toTotals(
        prices: TokenPriceSettingsSnapshot,
        params: TokenStatsQueryParams,
    ): TokenStatsTotals {
        val usageRow = normalizeLegacyCacheWriteUsage(this)
        val pricingProviderModel = normalizeProviderModel(usageRow.providerModel)
        val pricing = TokenPriceResolver.resolve(
            pricingProviderModel,
            prices.settingFor(pricingProviderModel, usageRow.configId),
        )
        val input = component(usageRow.uncachedInputTokens, usageRow.uncachedInputKnown, usageRow.requests)
        val cached = component(usageRow.cachedInputTokens, usageRow.cachedInputKnown, usageRow.requests)
        val cacheWrite = component(usageRow.cacheWriteTokens, usageRow.cacheWriteKnown, usageRow.requests)
        val totalInput =
            if (usageRow.totalInputKnown > 0L) {
                component(usageRow.totalInputTokens, usageRow.totalInputKnown, usageRow.requests)
            } else {
                combineComponents(listOf(input, cached, cacheWrite), usageRow.requests)
            }
        val output = component(usageRow.outputTokens, usageRow.outputKnown, usageRow.requests)
        return TokenStatsTotals(
            requests = usageRow.requests,
            uncachedInput = input,
            cachedInput = cached,
            totalInput = totalInput,
            output = output,
            totalTokens = combineComponents(listOf(totalInput, output), usageRow.requests),
            cost = TokenCostCalculator.currentCost(
                usageRow,
                pricing,
                params.targetCurrency,
                params.manualRate,
            ),
        )
    }

    private fun combineTotals(
        values: List<TokenStatsTotals>,
        params: TokenStatsQueryParams,
    ): TokenStatsTotals {
        if (values.isEmpty()) return emptyTotals(params)
        return TokenStatsTotals(
            requests = values.sumLong(TokenStatsTotals::requests),
            uncachedInput = values.combineComponents(TokenStatsTotals::uncachedInput),
            cachedInput = values.combineComponents(TokenStatsTotals::cachedInput),
            totalInput = values.combineComponents(TokenStatsTotals::totalInput),
            output = values.combineComponents(TokenStatsTotals::output),
            totalTokens = values.combineComponents(TokenStatsTotals::totalTokens),
            cost = TokenStatsCostSummary(
                currency = params.targetCurrency,
                knownAmount = values.sumOf { it.cost.knownAmount },
                unknownContributionCount = values.sumLong { it.cost.unknownContributionCount },
                totalContributionCount = values.sumLong { it.cost.totalContributionCount },
                rateUsed = params.manualRate,
                originalCurrencyAmounts = values
                    .flatMap { it.cost.originalCurrencyAmounts.entries }
                    .groupBy({ it.key }, { it.value })
                    .mapValues { (_, amounts) -> amounts.sum() },
            ),
        )
    }

    private fun TokenStatsTotals.toModelBucket() = TokenStatsModelBucket(
        requests = requests,
        uncachedInput = uncachedInput.knownSum,
        cachedInput = cachedInput.knownSum,
        output = output.knownSum,
        totalTokens = totalTokens.knownSum,
        totalTokensUnknownEventCount = totalTokens.unknownEventCount,
        unknownTokenEventCount = maxOf(
            uncachedInput.unknownEventCount,
            cachedInput.unknownEventCount,
            output.unknownEventCount,
        ),
        cost = cost,
    )

    private fun emptyTotals(params: TokenStatsQueryParams): TokenStatsTotals {
        val empty = component(0L, 0L, 0L)
        return TokenStatsTotals(
            requests = 0L,
            uncachedInput = empty,
            cachedInput = empty,
            totalInput = empty,
            output = empty,
            totalTokens = empty,
            cost = TokenStatsCostSummary(
                currency = params.targetCurrency,
                knownAmount = 0.0,
                unknownContributionCount = 0L,
                totalContributionCount = 0L,
                rateUsed = params.manualRate,
                originalCurrencyAmounts = emptyMap(),
            ),
        )
    }

    private fun component(sum: Long, known: Long, total: Long) =
        TokenStatsTokenAggregate(sum, known, (total - known).coerceAtLeast(0L), total)

    private fun combineComponents(
        components: List<TokenStatsTokenAggregate>,
        contributionCount: Long,
    ): TokenStatsTokenAggregate {
        val known = components.minOfOrNull(TokenStatsTokenAggregate::knownEventCount) ?: 0L
        return TokenStatsTokenAggregate(
            knownSum = components.sumLong(TokenStatsTokenAggregate::knownSum),
            knownEventCount = known,
            unknownEventCount = (contributionCount - known).coerceAtLeast(0L),
            totalEventCount = contributionCount,
        )
    }

    private fun List<TokenStatsTotals>.combineComponents(
        selector: (TokenStatsTotals) -> TokenStatsTokenAggregate,
    ): TokenStatsTokenAggregate {
        val values = map(selector)
        return TokenStatsTokenAggregate(
            knownSum = values.sumLong(TokenStatsTokenAggregate::knownSum),
            knownEventCount = values.sumLong(TokenStatsTokenAggregate::knownEventCount),
            unknownEventCount = values.sumLong(TokenStatsTokenAggregate::unknownEventCount),
            totalEventCount = values.sumLong(TokenStatsTokenAggregate::totalEventCount),
        )
    }

    private fun <T> Iterable<T>.sumLong(selector: (T) -> Long): Long =
        fold(0L) { sum, item -> TokenCostCalculator.saturatedAdd(sum, selector(item)) }

    private fun Set<String>?.queryValues(): List<String> =
        if (this == null || isEmpty()) listOf("__none__") else toList()

    private fun displayModelIdFor(model: String): String = "model:${model.trim().lowercase()}"

    private fun List<TokenStatsModelEntity>.toPriceSnapshot(): TokenPriceSettingsSnapshot {
        val priceRows = filter(TokenStatsModelEntity::hasPriceSetting)
        return TokenPriceSettingsSnapshot(
            providerModels = priceRows
                .filter { it.configId.isEmpty() }
                .associate { row -> "${row.provider}:${row.model}" to row.toModelPriceSettings() },
            configs = priceRows
                .filter { it.configId.isNotEmpty() }
                .associate { row ->
                    tokenPriceConfigKey("${row.provider}:${row.model}", row.configId) to
                        row.toModelPriceSettings()
                },
        )
    }
}

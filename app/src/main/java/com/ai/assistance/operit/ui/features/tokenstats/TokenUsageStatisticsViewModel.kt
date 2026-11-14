package com.ai.assistance.operit.ui.features.tokenstats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.stats.TokenStatsPreferencesStore
import com.ai.assistance.operit.data.stats.TokenActivityAggregator
import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import com.ai.assistance.operit.data.stats.TokenActivityRangeData
import com.ai.assistance.operit.data.stats.TokenCostCurrency
import com.ai.assistance.operit.data.stats.TokenStatsDisplayModelBreakdown
import com.ai.assistance.operit.data.stats.TokenStatsDisplayNames
import com.ai.assistance.operit.data.stats.TokenStatsDisplayUnit
import com.ai.assistance.operit.data.stats.TokenStatsGrouping
import com.ai.assistance.operit.data.stats.TokenStatsPriceDraft
import com.ai.assistance.operit.data.stats.TokenStatsLifetimeOverview
import com.ai.assistance.operit.data.stats.TokenStatsPriceSetting
import com.ai.assistance.operit.data.stats.TokenStatsQueryParams
import com.ai.assistance.operit.data.stats.TokenStatsQueryService
import com.ai.assistance.operit.data.stats.TokenStatsRangeData
import com.ai.assistance.operit.data.stats.TokenStatsSettingsManager
import com.ai.assistance.operit.data.stats.TokenStatsSettingsStore
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.data.stats.TokenStatsTimeRanges
import com.ai.assistance.operit.util.AppLogger
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TokenActivityUiState(
    val loading: Boolean = true,
    val viewMode: TokenActivityViewMode = TokenActivityViewMode.DAILY,
    val rangeData: TokenActivityRangeData? = null,
)

data class TokenStatsUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val refreshVersion: Long = 0L,
    val lifetime: TokenStatsLifetimeOverview? = null,
    val range: TokenStatsRangeData? = null,
    val currentRange: TokenStatsTimeRange? = null,
    val targetCurrency: PricingCurrency = PricingCurrency.CNY,
    val tokenDisplayUnit: TokenStatsDisplayUnit = TokenStatsDisplayUnit.MILLIONS,
    val manualRate: Double = TokenCostCurrency.DEFAULT_USD_TO_CNY_RATE,
    val rateIsEstimated: Boolean = true,
    val selectedModels: Set<String> = emptySet(),
    val modelGrouping: TokenStatsGrouping = TokenStatsGrouping.CONFIG_MODEL,
    val availableDisplayModels: List<TokenStatsDisplayModelBreakdown> = emptyList(),
    val knownModelNames: Map<String, String> = emptyMap(),
    val configurationNames: Map<String, String> = emptyMap(),
    val priceSettings: List<TokenStatsPriceSetting> = emptyList(),
    val activity: TokenActivityUiState = TokenActivityUiState(),
)

data class TokenStatsActionMessage(
    val text: String,
    val isError: Boolean = false,
)

@android.annotation.SuppressLint("StaticFieldLeak")
class TokenUsageStatisticsViewModel(
    context: Context,
    private val settings: TokenStatsSettingsStore = TokenStatsPreferencesStore(context),
    val zone: ZoneId = ZoneId.systemDefault(),
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val stringResolver: (Int) -> String = { context.applicationContext.getString(it) },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val manager = TokenStatsSettingsManager(appContext)
    private val modelConfigManager = ModelConfigManager(appContext)
    private val tag = "TokenUsageStatisticsViewModel"

    private val _state = MutableStateFlow(TokenStatsUiState())
    val state: StateFlow<TokenStatsUiState> = _state.asStateFlow()

    private val _actionMessage = MutableStateFlow<TokenStatsActionMessage?>(null)
    val actionMessage: StateFlow<TokenStatsActionMessage?> = _actionMessage.asStateFlow()

    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var modeRangeJob: Job? = null
    private val knownModelNames = linkedMapOf<String, String>()
    private val knownModelProviderModels = linkedMapOf<String, Set<String>>()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    fun load() = loadInternal(loadViewMode = false)

    fun loadForEntry() = loadInternal(loadViewMode = true)

    fun setActivityViewMode(mode: TokenActivityViewMode) {
        val snapshot = _state.value
        val currentRange = snapshot.currentRange ?: defaultDateRange(nowMs(), zone)
        val anchorDate = activityRangeAnchorDate(currentRange, zone)
        val providerModels = providerModelsFor(snapshot)
        modeRangeJob?.cancel()
        modeRangeJob = viewModelScope.launch(dispatcher) {
            try {
                val historyStartDate = if (mode == TokenActivityViewMode.CUMULATIVE) {
                    TokenStatsQueryService.earliestOccurredDate(
                        appContext,
                        TokenStatsQueryParams(providerModels = providerModels),
                        zone,
                    )
                } else {
                    null
                }
                val range = activityRangeForMode(mode, anchorDate, historyStartDate, zone)
                    ?: return@launch
                settings.saveActivityViewMode(mode)
                settings.saveTimeRange(range)
                _state.update {
                    it.copy(
                        currentRange = range,
                        activity = it.activity.copy(viewMode = mode),
                    )
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(tag, "Failed to change token activity view mode", e)
            }
        }
    }

    fun toggleTokenDisplayUnit() {
        val unit = _state.value.tokenDisplayUnit.toggled()
        _state.update { it.copy(tokenDisplayUnit = unit) }
        viewModelScope.launch(dispatcher) {
            try {
                settings.saveTokenDisplayUnit(unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(tag, "Failed to save token display unit", e)
            }
        }
    }

    private fun loadInternal(loadViewMode: Boolean) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        val filterSnapshot = _state.value
        loadJob = viewModelScope.launch(dispatcher) {
            try {
                val rateInfo = settings.loadRateWithEstimate()
                val currency = settings.loadTargetCurrency()
                val tokenDisplayUnit = settings.loadTokenDisplayUnit()
                val modelGrouping = settings.loadModelGrouping()
                val entryViewMode = if (loadViewMode) settings.loadActivityViewMode() else null
                val range = settings.loadTimeRange() ?: defaultDateRange(nowMs(), zone)

                if (generation != loadGeneration) return@launch
                _state.update {
                    it.copy(
                        loading = true,
                        errorMessage = null,
                        modelGrouping = modelGrouping,
                        activity = it.activity.copy(
                            loading = true,
                            viewMode = entryViewMode ?: it.activity.viewMode,
                        ),
                    )
                }

                val result = coroutineScope {
                    val pricesDeferred = async(Dispatchers.IO) { manager.allPriceSettings() }
                    val selectedProviderModels = providerModelsFor(filterSnapshot)
                    val rangeParams = TokenStatsQueryParams(
                        targetCurrency = currency,
                        manualRate = rateInfo.first,
                        providerModels = selectedProviderModels,
                        grouping = modelGrouping,
                    )
                    val availableParams = rangeParams.copy(providerModels = null)
                    val lifetimeDeferred = async(Dispatchers.IO) {
                        TokenStatsQueryService.lifetimeOverview(
                            appContext,
                            TokenStatsQueryParams(
                                targetCurrency = currency,
                                manualRate = rateInfo.first,
                                grouping = modelGrouping,
                            ),
                        )
                    }
                    val rangeDeferred = async(Dispatchers.IO) {
                        TokenStatsQueryService.rangeData(appContext, range, rangeParams, zone)
                    }
                    val availableDeferred = async(Dispatchers.IO) {
                        if (selectedProviderModels == null) {
                            null
                        } else {
                            TokenStatsQueryService.rangeData(
                                appContext,
                                range,
                                availableParams,
                                zone,
                            )
                        }
                    }
                    val activityDeferred = async(Dispatchers.IO) {
                        TokenStatsQueryService.activitySnapshot(appContext, range, rangeParams, zone)
                    }
                    val rangeData = rangeDeferred.await()
                    val lifetimeData = lifetimeDeferred.await()
                    val availableData = availableDeferred.await() ?: rangeData
                    // 配置名解析覆盖全部三处查询结果：分组后 lifetime 的模型累计
                    // 与筛选下拉同样需要"配置名/模型名"。
                    val configurationIds =
                        (lifetimeData.displayModels + rangeData.displayModels + availableData.displayModels)
                            .flatMap { it.identities }
                            .map { it.configId }
                            .distinct()
                    val configurationNamesDeferred = async(Dispatchers.IO) {
                        buildMap {
                            configurationIds.forEach { configId ->
                                modelConfigManager.getModelConfig(configId)?.let { config ->
                                    put(configId, config.name)
                                }
                            }
                        }
                    }
                    val configurationNames = configurationNamesDeferred.await()
                    QueryLoadResult(
                        lifetime = lifetimeData.copy(
                            displayModels = resolveDisplayNames(lifetimeData.displayModels, configurationNames),
                        ),
                        range = rangeData.copy(
                            displayModels = resolveDisplayNames(rangeData.displayModels, configurationNames),
                        ),
                        available = availableData.copy(
                            displayModels = resolveDisplayNames(availableData.displayModels, configurationNames),
                        ),
                        prices = pricesDeferred.await(),
                        configurationNames = configurationNames,
                        activity = TokenActivityAggregator.rangeData(activityDeferred.await(), range),
                    )
                }

                if (generation != loadGeneration) return@launch
                rememberModelNames(result.lifetime.displayModels)
                rememberModelNames(result.range?.displayModels.orEmpty())
                rememberModelNames(result.available?.displayModels.orEmpty())
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = null,
                        lifetime = result.lifetime,
                        range = result.range,
                        currentRange = range,
                        targetCurrency = currency,
                        tokenDisplayUnit = tokenDisplayUnit,
                        modelGrouping = modelGrouping,
                        manualRate = rateInfo.first,
                        rateIsEstimated = rateInfo.second,
                        availableDisplayModels = result.available?.displayModels.orEmpty(),
                        knownModelNames = knownModelNames.toMap(),
                        configurationNames = result.configurationNames,
                        priceSettings = result.prices,
                        activity = it.activity.copy(loading = false, rangeData = result.activity),
                        refreshVersion = it.refreshVersion + 1L,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    _state.update {
                        it.copy(
                            loading = false,
                            errorMessage = stringResolver(R.string.token_stats_load_failed),
                            activity = it.activity.copy(loading = false),
                        )
                    }
                }
                AppLogger.e(tag, "Token statistics load failed", e)
            }
        }
    }

    private fun rememberModelNames(models: List<TokenStatsDisplayModelBreakdown>) {
        models.forEach {
            knownModelNames[it.displayModelId] = it.displayName
            knownModelProviderModels[it.displayModelId] =
                knownModelProviderModels[it.displayModelId].orEmpty() + it.providerModels
        }
    }

    /**
     * CONFIG_MODEL 分组下把展示名拼装为"配置名/模型名"。实现抽到
     * [TokenStatsDisplayNames]（纯函数，单测覆盖），这里只注入删除配置的文案。
     */
    private fun resolveDisplayNames(
        models: List<TokenStatsDisplayModelBreakdown>,
        configurationNames: Map<String, String>,
    ): List<TokenStatsDisplayModelBreakdown> =
        TokenStatsDisplayNames.resolve(
            models,
            configurationNames,
            stringResolver(R.string.token_stats_config_deleted),
        )

    private fun providerModelsFor(state: TokenStatsUiState): Set<String>? {
        val providerModelsByDisplayId = knownModelProviderModels.toMutableMap().apply {
            state.availableDisplayModels.forEach { model ->
                this[model.displayModelId] = this[model.displayModelId].orEmpty() + model.providerModels
            }
        }
        if (state.selectedModels.isEmpty()) return null
        return state.selectedModels
            .asSequence()
            .flatMap { providerModelsByDisplayId[it].orEmpty().asSequence() }
            .toSet()
    }

    fun setCustomRange(startMs: Long, endMs: Long): Boolean {
        modeRangeJob?.cancel()
        when (validateCustomRange(startMs, endMs, zone, MAX_CUSTOM_RANGE_DAYS)) {
            CustomRangeValidation.INVALID_BOUNDS -> {
                _actionMessage.value = TokenStatsActionMessage(
                    stringResolver(R.string.token_stats_custom_range_invalid),
                    isError = true,
                )
                return false
            }
            CustomRangeValidation.TOO_LONG -> {
                // A generated cumulative range may exceed the manual range limit;
                // confirming it unchanged must remain a valid no-op selection.
                val currentRange = _state.value.currentRange
                if (currentRange?.let { it.startMs == startMs && it.endMs == endMs } == true) {
                    return saveCustomRange(TokenStatsTimeRanges.customRange(startMs, endMs))
                }
                _actionMessage.value = TokenStatsActionMessage(
                    stringResolver(R.string.token_stats_custom_range_too_long),
                    isError = true,
                )
                return false
            }
            CustomRangeValidation.VALID -> Unit
        }
        return saveCustomRange(TokenStatsTimeRanges.customRange(startMs, endMs))
    }

    private fun saveCustomRange(range: TokenStatsTimeRange): Boolean {
        viewModelScope.launch(dispatcher) {
            settings.saveTimeRange(range)
            load()
        }
        return true
    }

    fun toggleModel(displayModelId: String) {
        modeRangeJob?.cancel()
        _state.update { state ->
            val selected = state.selectedModels.toMutableSet()
            if (!selected.add(displayModelId)) selected.remove(displayModelId)
            state.copy(selectedModels = selected)
        }
        load()
    }

    /**
     * 切换模型维度分组口径。displayModelId 的语义随分组变化（配置组键 vs 模型组键），
     * 旧选中集在新口径下指向别的条目，必须清空让用户在新视图上重选。
     */
    fun setModelGrouping(grouping: TokenStatsGrouping) {
        modeRangeJob?.cancel()
        viewModelScope.launch(dispatcher) {
            try {
                settings.saveModelGrouping(grouping)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(tag, "Failed to save token stats model grouping", e)
                return@launch
            }
            _state.update { applyGroupingChange(it, grouping) }
            load()
        }
    }

    fun selectAllModels() {
        modeRangeJob?.cancel()
        _state.update { it.copy(selectedModels = emptySet()) }
        load()
    }

    fun setTargetCurrency(currency: PricingCurrency) {
        viewModelScope.launch(dispatcher) {
            settings.saveTargetCurrency(currency)
            load()
        }
    }

    fun setManualRate(rate: Double): Boolean {
        if (!rate.isFinite() || rate <= 0.0) return false
        viewModelScope.launch(dispatcher) {
            settings.saveRate(rate)
            load()
        }
        return true
    }

    fun savePrice(draft: TokenStatsPriceDraft) {
        viewModelScope.launch(dispatcher) {
            try {
                manager.savePrice(draft)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = TokenStatsActionMessage(
                    stringResolver(R.string.token_stats_pricing_save_failed),
                    isError = true,
                )
            }
        }
    }

    fun deletePrice(setting: TokenStatsPriceSetting) {
        viewModelScope.launch(dispatcher) {
            try {
                if (setting.scope == com.ai.assistance.operit.data.stats.TokenStatsPriceScope.CONFIG) {
                    manager.resetConfigPrice(setting.providerModel, requireNotNull(setting.configId))
                } else {
                    manager.restoreBuiltInPrice(setting.providerModel)
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = TokenStatsActionMessage(
                    stringResolver(R.string.token_stats_pricing_delete_failed),
                    isError = true,
                )
            }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TokenUsageStatisticsViewModel(appContext) as T
    }

    companion object {
        const val MAX_CUSTOM_RANGE_DAYS = 3 * 366L

        /**
         * 评审 P3：分组切换的状态转移抽成纯函数（单测覆盖）。displayModelId 的语义
         * 随分组变化（配置组键 vs 模型组键），旧选中集在新口径下指向别的条目，
         * 必须清空让用户在新视图上重选。
         */
        fun applyGroupingChange(
            state: TokenStatsUiState,
            grouping: TokenStatsGrouping,
        ): TokenStatsUiState =
            state.copy(modelGrouping = grouping, selectedModels = emptySet())
    }
}

private data class QueryLoadResult(
    val lifetime: TokenStatsLifetimeOverview,
    val range: TokenStatsRangeData?,
    val available: TokenStatsRangeData?,
    val prices: List<TokenStatsPriceSetting>,
    val configurationNames: Map<String, String>,
    val activity: TokenActivityRangeData,
)

private fun defaultDateRange(nowMs: Long, zone: ZoneId): TokenStatsTimeRange {
    val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val start = today.minusDays(29L).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli()
    return TokenStatsTimeRanges.customRange(start, end)
}

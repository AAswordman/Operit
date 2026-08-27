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
    val manualRate: Double = TokenCostCurrency.DEFAULT_USD_TO_CNY_RATE,
    val rateIsEstimated: Boolean = true,
    val selectedModels: Set<String> = emptySet(),
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
    private val knownModelNames = linkedMapOf<String, String>()
    private val knownModelProviderModels = linkedMapOf<String, Set<String>>()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    fun load() = loadInternal()

    fun loadForEntry() = loadInternal()

    fun setActivityViewMode(mode: TokenActivityViewMode) {
        _state.update { it.copy(activity = it.activity.copy(viewMode = mode)) }
    }

    private fun loadInternal() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        val filterSnapshot = _state.value
        loadJob = viewModelScope.launch(dispatcher) {
            try {
                val rateInfo = settings.loadRateWithEstimate()
                val currency = settings.loadTargetCurrency()
                val range = settings.loadTimeRange() ?: defaultDateRange(nowMs(), zone)

                if (generation != loadGeneration) return@launch
                _state.update { it.copy(loading = true, errorMessage = null, activity = it.activity.copy(loading = true)) }

                val result = coroutineScope {
                    val pricesDeferred = async(Dispatchers.IO) { manager.allPriceSettings() }
                    val providerModelsByDisplayId = knownModelProviderModels.toMutableMap().apply {
                        filterSnapshot.availableDisplayModels.forEach { model ->
                            this[model.displayModelId] =
                                this[model.displayModelId].orEmpty() + model.providerModels
                        }
                    }
                    val selectedProviderModels =
                        if (filterSnapshot.selectedModels.isEmpty()) {
                            null
                        } else {
                            filterSnapshot.selectedModels
                                .asSequence()
                                .flatMap { providerModelsByDisplayId[it].orEmpty().asSequence() }
                                .toSet()
                        }
                    val rangeParams = TokenStatsQueryParams(
                        targetCurrency = currency,
                        manualRate = rateInfo.first,
                        providerModels = selectedProviderModels,
                    )
                    val availableParams = rangeParams.copy(providerModels = null)
                    val lifetimeDeferred = async(Dispatchers.IO) {
                        TokenStatsQueryService.lifetimeOverview(
                            appContext,
                            TokenStatsQueryParams(
                                targetCurrency = currency,
                                manualRate = rateInfo.first,
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
                    val configurationIds =
                        rangeData
                            ?.displayModels
                            .orEmpty()
                            .flatMap { it.identities }
                            .mapNotNull { it.configId }
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
                    QueryLoadResult(
                        lifetime = lifetimeDeferred.await(),
                        range = rangeData,
                        available = availableDeferred.await() ?: rangeData,
                        prices = pricesDeferred.await(),
                        configurationNames = configurationNamesDeferred.await(),
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

    fun setCustomRange(startMs: Long, endMs: Long): Boolean {
        when (validateCustomRange(startMs, endMs, zone, MAX_CUSTOM_RANGE_DAYS)) {
            CustomRangeValidation.INVALID_BOUNDS -> {
                _actionMessage.value = TokenStatsActionMessage(
                    stringResolver(R.string.token_stats_custom_range_invalid),
                    isError = true,
                )
                return false
            }
            CustomRangeValidation.TOO_LONG -> {
                _actionMessage.value = TokenStatsActionMessage(
                    stringResolver(R.string.token_stats_custom_range_too_long),
                    isError = true,
                )
                return false
            }
            CustomRangeValidation.VALID -> Unit
        }
        viewModelScope.launch(dispatcher) {
            settings.saveTimeRange(TokenStatsTimeRanges.customRange(startMs, endMs))
            load()
        }
        return true
    }

    fun toggleModel(displayModelId: String) {
        _state.update { state ->
            val selected = state.selectedModels.toMutableSet()
            if (!selected.add(displayModelId)) selected.remove(displayModelId)
            state.copy(selectedModels = selected)
        }
        load()
    }

    fun selectAllModels() {
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

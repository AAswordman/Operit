package com.ai.assistance.operit.data.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.datastore.preferences.core.toPreferences
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.persistence.PreferenceStateRepairResult
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.persistence.repairPreferenceState
import kotlinx.coroutines.flow.first

private val Context.tokenStatsDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "token_stats_preferences")

/** Scalar statistics settings. Structured usage, grouping, and pricing stay in Room. */
internal class TokenStatsPreferences(context: Context) {
    companion object {
        private val TARGET_CURRENCY = stringPreferencesKey("target_currency")
        private val USD_TO_CNY_RATE = doublePreferencesKey("usd_to_cny_rate")
        private val TIME_RANGE_START = longPreferencesKey("time_range_start")
        private val TIME_RANGE_END = longPreferencesKey("time_range_end")
        private val IMPORTED_AT = longPreferencesKey("imported_at_ms")
    }

    private val appContext = context.applicationContext
    private val dataStore = appContext.tokenStatsDataStore

    suspend fun repairPersistedState(): Boolean =
        repairPreferenceState(
            context = appContext,
            storeName = PreferenceStoreCatalog.TOKEN_STATS,
            dataStore = dataStore,
        ) { current ->
            val mutable = current.toMutablePreferences()
            val issues = linkedSetOf<String>()

            val rawCurrency = current.asMap()[TARGET_CURRENCY]
            if (rawCurrency != null) {
                val normalizedCurrency =
                    (rawCurrency as? String)?.let { stored ->
                        PricingCurrency.entries.firstOrNull { it.name == stored }?.name
                    }
                if (normalizedCurrency == null) {
                    mutable[TARGET_CURRENCY] = PricingCurrency.CNY.name
                    issues += TARGET_CURRENCY.name
                }
            }

            val rawRate = current.asMap()[USD_TO_CNY_RATE]
            if (rawRate != null && (rawRate !is Double || !rawRate.isFinite() || rawRate <= 0.0)) {
                mutable.remove(USD_TO_CNY_RATE)
                issues += USD_TO_CNY_RATE.name
            }

            val rawStart = current.asMap()[TIME_RANGE_START]
            val rawEnd = current.asMap()[TIME_RANGE_END]
            val validRange = rawStart is Long && rawEnd is Long && rawEnd > rawStart
            if ((rawStart != null || rawEnd != null) && !validRange) {
                mutable.remove(TIME_RANGE_START)
                mutable.remove(TIME_RANGE_END)
                issues += TIME_RANGE_START.name
                issues += TIME_RANGE_END.name
            }

            val rawImportedAt = current.asMap()[IMPORTED_AT]
            if (rawImportedAt != null && (rawImportedAt !is Long || rawImportedAt <= 0L)) {
                mutable.remove(IMPORTED_AT)
                issues += IMPORTED_AT.name
            }

            PreferenceStateRepairResult(mutable.toPreferences(), issues)
        }

    suspend fun importedAtMs(): Long? = dataStore.data.first()[IMPORTED_AT]

    suspend fun completeMigration(
        importedAtMs: Long,
        releasedUsdToCnyRate: Double?,
    ) {
        dataStore.edit { preferences ->
            releasedUsdToCnyRate?.let { rate -> preferences[USD_TO_CNY_RATE] = rate }
            preferences[IMPORTED_AT] = importedAtMs
        }
    }

    suspend fun loadRateWithEstimate(): Pair<Double, Boolean> {
        val stored = dataStore.data.first()[USD_TO_CNY_RATE]
        return if (stored == null) {
            TokenCostCurrency.DEFAULT_USD_TO_CNY_RATE to true
        } else {
            require(stored.isFinite() && stored > 0.0) { "stored exchange rate is invalid" }
            stored to false
        }
    }

    suspend fun saveRate(rate: Double) {
        require(rate.isFinite() && rate > 0.0) { "exchange rate must be positive and finite" }
        dataStore.edit { preferences -> preferences[USD_TO_CNY_RATE] = rate }
    }

    suspend fun loadTargetCurrency(): PricingCurrency {
        val stored = dataStore.data.first()[TARGET_CURRENCY]
        return stored?.let { PricingCurrency.valueOf(it) } ?: PricingCurrency.CNY
    }

    suspend fun saveTargetCurrency(currency: PricingCurrency) {
        dataStore.edit { preferences -> preferences[TARGET_CURRENCY] = currency.name }
    }

    suspend fun loadTimeRange(): TokenStatsTimeRange? {
        val preferences = dataStore.data.first()
        val startMs = preferences[TIME_RANGE_START] ?: return null
        val endMs = checkNotNull(preferences[TIME_RANGE_END])
        return TokenStatsTimeRanges.customRange(startMs, endMs)
    }

    suspend fun saveTimeRange(range: TokenStatsTimeRange?) {
        dataStore.edit { preferences ->
            if (range == null) {
                preferences.remove(TIME_RANGE_START)
                preferences.remove(TIME_RANGE_END)
                return@edit
            }
            preferences[TIME_RANGE_START] = range.startMs
            preferences[TIME_RANGE_END] = range.endMs
        }
    }
}

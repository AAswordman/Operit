package com.ai.assistance.operit.data.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.collects.PricingCurrency
import kotlinx.coroutines.flow.first

private val Context.tokenStatsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "token_stats_preferences")

/** Scalar statistics settings. Structured usage, grouping, and pricing stay in Room. */
internal class TokenStatsPreferences(context: Context) {
    companion object {
        private val TARGET_CURRENCY = stringPreferencesKey("target_currency")
        private val TOKEN_DISPLAY_UNIT = stringPreferencesKey("token_display_unit")
        private val USD_TO_CNY_RATE = doublePreferencesKey("usd_to_cny_rate")
        private val TIME_RANGE_START = longPreferencesKey("time_range_start")
        private val TIME_RANGE_END = longPreferencesKey("time_range_end")
        private val ACTIVITY_VIEW_MODE = stringPreferencesKey("activity_view_mode")
        private val IMPORTED_AT = longPreferencesKey("imported_at_ms")
    }

    private val dataStore = context.applicationContext.tokenStatsDataStore

    suspend fun importedAtMs(): Long? = dataStore.data.first()[IMPORTED_AT]

    suspend fun completeMigration(importedAtMs: Long, releasedUsdToCnyRate: Double?) {
        dataStore.edit { preferences ->
            releasedUsdToCnyRate?.let { preferences[USD_TO_CNY_RATE] = it }
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

    suspend fun loadTokenDisplayUnit(): TokenStatsDisplayUnit {
        val stored = dataStore.data.first()[TOKEN_DISPLAY_UNIT]
        return stored?.let { TokenStatsDisplayUnit.valueOf(it) }
            ?: TokenStatsDisplayUnit.MILLIONS
    }

    suspend fun saveTokenDisplayUnit(unit: TokenStatsDisplayUnit) {
        dataStore.edit { preferences -> preferences[TOKEN_DISPLAY_UNIT] = unit.name }
    }

    suspend fun loadActivityViewMode(): TokenActivityViewMode {
        val stored = dataStore.data.first()[ACTIVITY_VIEW_MODE]
        return stored?.let { TokenActivityViewMode.valueOf(it) } ?: TokenActivityViewMode.DAILY
    }

    suspend fun saveActivityViewMode(mode: TokenActivityViewMode) {
        dataStore.edit { preferences -> preferences[ACTIVITY_VIEW_MODE] = mode.name }
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

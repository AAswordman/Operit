package com.ai.assistance.operit.data.stats

import android.content.Context
import com.ai.assistance.operit.data.collects.PricingCurrency

interface TokenStatsSettingsStore {
    suspend fun loadRateWithEstimate(): Pair<Double, Boolean>

    suspend fun saveRate(rate: Double)

    suspend fun loadTargetCurrency(): PricingCurrency

    suspend fun saveTargetCurrency(currency: PricingCurrency)

    suspend fun loadTokenDisplayUnit(): TokenStatsDisplayUnit

    suspend fun saveTokenDisplayUnit(unit: TokenStatsDisplayUnit)

    suspend fun loadActivityViewMode(): TokenActivityViewMode

    suspend fun saveActivityViewMode(mode: TokenActivityViewMode)

    suspend fun loadTimeRange(): TokenStatsTimeRange?

    suspend fun saveTimeRange(range: TokenStatsTimeRange?)
}

/** Statistics-only Preferences implementation; structured data remains in Room. */
class TokenStatsPreferencesStore(context: Context) : TokenStatsSettingsStore {
    private val preferences = TokenStatsPreferences(context.applicationContext)

    override suspend fun loadRateWithEstimate(): Pair<Double, Boolean> {
        return preferences.loadRateWithEstimate()
    }

    override suspend fun saveRate(rate: Double) {
        preferences.saveRate(rate)
    }

    override suspend fun loadTargetCurrency(): PricingCurrency {
        return preferences.loadTargetCurrency()
    }

    override suspend fun saveTargetCurrency(currency: PricingCurrency) {
        preferences.saveTargetCurrency(currency)
    }

    override suspend fun loadTokenDisplayUnit(): TokenStatsDisplayUnit {
        return preferences.loadTokenDisplayUnit()
    }

    override suspend fun saveTokenDisplayUnit(unit: TokenStatsDisplayUnit) {
        preferences.saveTokenDisplayUnit(unit)
    }

    override suspend fun loadActivityViewMode(): TokenActivityViewMode {
        return preferences.loadActivityViewMode()
    }

    override suspend fun saveActivityViewMode(mode: TokenActivityViewMode) {
        preferences.saveActivityViewMode(mode)
    }

    override suspend fun loadTimeRange(): TokenStatsTimeRange? {
        return preferences.loadTimeRange()
    }

    override suspend fun saveTimeRange(range: TokenStatsTimeRange?) {
        preferences.saveTimeRange(range)
    }
}

package com.ai.assistance.operit.data.stats

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.dao.TokenUsageDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room owner for successful formal-inference usage and pricing. */
class TokenUsageRepository private constructor(context: Context) {
    companion object {
        private const val TAG = "TokenUsageRepository"
        @Volatile
        private var instance: TokenUsageRepository? = null
        private val databaseAccessMutex = Mutex()

        fun getInstance(context: Context): TokenUsageRepository =
            instance ?: synchronized(this) {
                instance ?: TokenUsageRepository(context.applicationContext).also { instance = it }
            }

        /** Prevent Room access while a restore replaces database files. */
        suspend fun <T> withDatabaseAccess(block: suspend () -> T): T =
            databaseAccessMutex.withLock { block() }

        suspend fun <T> withDatabaseRestore(block: suspend () -> T): T =
            withDatabaseAccess { block() }
    }

    private val appContext = context.applicationContext
    private val legacyDataSource = ApiPreferences.getInstance(appContext)
    private val statsPreferences = TokenStatsPreferences(appContext)
    private val importMutex = Mutex()

    @Volatile
    private var initializationComplete = false

    suspend fun ensureInitialized() {
        if (initializationComplete) return
        importMutex.withLock {
            if (initializationComplete) return
            if (statsPreferences.importedAtMs() == null) {
                val snapshot = legacyDataSource.readTokenStatsMigrationSnapshot()
                withDatabaseAccess {
                    val database = AppDatabase.getDatabase(appContext)
                    database.withTransaction {
                        val dao = database.tokenUsageDao()
                        dao.insertRecords(
                            snapshot.totals.map { total ->
                                TokenUsageRecordEntity(
                                    importKey = "legacy-cumulative:${total.provider}:${total.model}",
                                    occurredAtMs = null,
                                    configId = "",
                                    provider = total.provider,
                                    model = total.model,
                                    requestCount = total.requestCount,
                                    uncachedInputTokens =
                                        (total.inputTokens - total.cachedInputTokens).coerceAtLeast(0L),
                                    cachedInputTokens = total.cachedInputTokens,
                                    totalInputTokens = total.inputTokens,
                                    outputTokens = total.outputTokens,
                                )
                            }
                        )
                        snapshot.prices.forEach { price ->
                            val current = dao.getStatsModel("", price.provider, price.model)
                                ?: TokenStatsModelEntity("", price.provider, price.model)
                            dao.upsertStatsModel(
                                current.copy(
                                    billingMode = price.settings.billingMode?.name,
                                    currency = price.settings.currency?.name,
                                    inputPricePerMillion = price.settings.inputPricePerMillion,
                                    cachedInputPricePerMillion = price.settings.cachedInputPricePerMillion,
                                    cacheWritePricePerMillion = price.settings.cacheWritePricePerMillion,
                                    outputPricePerMillion = price.settings.outputPricePerMillion,
                                    pricePerRequest = price.settings.pricePerRequest,
                                )
                            )
                        }
                    }
                }
                statsPreferences.completeMigration(
                    importedAtMs = System.currentTimeMillis(),
                    releasedUsdToCnyRate = snapshot.usdToCnyRate,
                )
                AppLogger.i(TAG, "Imported ${snapshot.totals.size} cumulative totals and ${snapshot.prices.size} price settings")
            }
            legacyDataSource.clearMigratedTokenStatsData()
            initializationComplete = true
        }
    }

    internal suspend fun <T> withDao(block: suspend (TokenUsageDao) -> T): T {
        ensureInitialized()
        return withDatabaseAccess { block(AppDatabase.getDatabase(appContext).tokenUsageDao()) }
    }

    suspend fun record(record: TokenUsageRecordEntity) {
        withDao { dao -> dao.insertRecord(record) }
    }

    suspend fun clearUsageRecords() {
        withDao { dao -> dao.clearUsageRecords() }
    }
}

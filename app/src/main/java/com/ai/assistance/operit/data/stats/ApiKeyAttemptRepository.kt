package com.ai.assistance.operit.data.stats

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ApiKeyAttemptRecordEntity
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ApiKeyAttemptRepository private constructor(context: Context) {
    companion object {
        private const val TAG = "ApiKeyAttemptRepository"

        @Volatile
        private var instance: ApiKeyAttemptRepository? = null

        fun getInstance(context: Context): ApiKeyAttemptRepository =
            instance
                ?: synchronized(this) {
                    instance
                        ?: ApiKeyAttemptRepository(context.applicationContext).also { instance = it }
                }
    }

    private val appContext = context.applicationContext

    suspend fun record(record: ApiKeyAttemptRecordEntity) {
        TokenUsageRepository.withDatabaseAccess {
            AppDatabase.getDatabase(appContext).apiKeyAttemptDao().insert(record)
        }
    }

    suspend fun recordQuietly(record: ApiKeyAttemptRecordEntity) {
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                record(record)
            } catch (e: Exception) {
                AppLogger.e(TAG, "api key attempt insert failed", e)
            }
        }
    }

    suspend fun recentForConfig(
        configId: String,
        sinceMs: Long,
        limit: Int = 200,
    ): List<ApiKeyAttemptRecordEntity> {
        return TokenUsageRepository.withDatabaseAccess {
            AppDatabase.getDatabase(appContext).apiKeyAttemptDao()
                .recentForConfig(configId, sinceMs, limit)
        }
    }
}
package com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnifiedMarketAuthorViewModel(
    private val context: Context,
    private val authorId: String
) : ViewModel() {
    private val marketStatsApiService = MarketStatsApiService()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _entries = MutableStateFlow<List<MarketV2PublisherEntrySummary>>(emptyList())
    val entries: StateFlow<List<MarketV2PublisherEntrySummary>> = _entries.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    fun loadEntries(refresh: Boolean = false) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            if (!refresh && _hasLoaded.value) return@launch

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val loaded =
                    withContext(Dispatchers.IO) {
                        marketStatsApiService.getPublisherEntries(authorId).getOrThrow()
                    }
                _entries.value = loaded
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load market author entries $authorId", e)
            } finally {
                _hasLoaded.value = true
                _isLoading.value = false
            }
        }
    }

    fun openEntryDetail(
        entry: MarketV2PublisherEntrySummary,
        onLoaded: (MarketV2Entry) -> Unit
    ) {
        viewModelScope.launch {
            if (entry.id.isBlank()) {
                _errorMessage.value = context.getString(R.string.market_error_load_failed)
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null
            try {
                val fullEntry =
                    withContext(Dispatchers.IO) {
                        marketStatsApiService.getEntry(entry.id).getOrThrow()
                    }
                if (fullEntry == null) {
                    _errorMessage.value = context.getString(R.string.market_error_load_failed)
                    return@launch
                }
                onLoaded(fullEntry)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load author market entry ${entry.id}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(
        private val context: Context,
        private val authorId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UnifiedMarketAuthorViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UnifiedMarketAuthorViewModel(context, authorId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "UnifiedMarketAuthorViewModel"
    }
}

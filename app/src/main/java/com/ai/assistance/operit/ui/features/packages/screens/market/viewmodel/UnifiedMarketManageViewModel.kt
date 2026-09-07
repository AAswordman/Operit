package com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UnifiedMarketManageKind(val types: Set<String>) {
    SCRIPT(setOf(MarketStatsType.SCRIPT.wireValue)),
    PACKAGE(setOf(MarketStatsType.PACKAGE.wireValue)),
    ARTIFACT(setOf(MarketStatsType.SCRIPT.wireValue, MarketStatsType.PACKAGE.wireValue)),
    SKILL(setOf(MarketStatsType.SKILL.wireValue)),
    MCP(setOf(MarketStatsType.MCP.wireValue))
}

class UnifiedMarketManageViewModel(
    private val context: Context,
    private val kind: UnifiedMarketManageKind
) : ViewModel() {
    private val marketStatsApiService = MarketStatsApiService()
    private val githubAuth = GitHubAuthPreferences.getInstance(context)

    private val _isLoading = MutableStateFlow(false)
   val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _entries = MutableStateFlow<List<MarketV2PublisherEntrySummary>>(emptyList())
    val entries: StateFlow<List<MarketV2PublisherEntrySummary>> = _entries.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> =
        githubAuth.isLoggedInFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun loadEntries(refresh: Boolean = false) {
       viewModelScope.launch {
           if (_isLoading.value) return@launch
            if (refresh && _isRefreshing.value) return@launch
           if (!refresh && _hasLoaded.value) return@launch
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                return@launch
            }

            if (refresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            _errorMessage.value = null

           try {
                val userInfo = githubAuth.getCurrentUserInfo()
                if (userInfo == null) {
                    _errorMessage.value = context.getString(R.string.skillmarket_unable_get_user_info)
                    return@launch
                }

                val loaded =
                    withContext(Dispatchers.IO) {
                        kind.types
                            .flatMap { type ->
                                marketStatsApiService.getUserPublishedEntries(type).getOrThrow()
                            }
                            .filter { entry -> entry.type.lowercase() in kind.types }
                            .distinctBy { it.id }
                            .sortedByDescending { it.updatedAt }
                    }
                _entries.value = loaded
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load managed market entries", e)
            } finally {
                _hasLoaded.value = true
            if (refresh) {
                _isRefreshing.value = false
            } else {
                _isLoading.value = false
            }
            }
        }
    }

    fun reset() {
       _entries.value = emptyList()
       _hasLoaded.value = false
       _errorMessage.value = null
        _isRefreshing.value = false
   }

    fun clearError() {
        _errorMessage.value = null
    }

    fun openEntryDetail(
        entry: MarketV2PublisherEntrySummary,
        onLoaded: (MarketV2Entry) -> Unit
    ) {
        viewModelScope.launch {
            if (entry.id.isBlank()) {
                _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, "entry not found")
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
                    _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, "entry not found")
                    return@launch
                }
                onLoaded(fullEntry)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load full managed market entry ${entry.id}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun withdrawEntry(entry: MarketV2PublisherEntrySummary) {
        updateEntryState(
            entry = entry,
            stateCode = "withdrawn",
            action = { marketStatsApiService.withdrawEntry(entry.id) },
            successMessage = context.getString(R.string.market_manage_removed, entry.title)
        )
    }

    fun openEntryForRevision(
        entry: MarketV2PublisherEntrySummary,
        onLoaded: (MarketV2Entry) -> Unit
    ) {
        AppLogger.i(
            TAG,
            "revision_gate click entryId=${entry.id} stateCode=${entry.stateCode} " +
                "updatedAt=${entry.updatedAt} next=getMyEntryDetail"
        )
        openOwnedEntryDetail(entry, onLoaded)
    }

    fun openOwnedEntryDetail(
        entry: MarketV2PublisherEntrySummary,
        onLoaded: (MarketV2Entry) -> Unit
    ) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                return@launch
            }
            if (entry.id.isBlank()) {
                _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, "entry not found")
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null
            try {
                val fullEntry =
                    withContext(Dispatchers.IO) {
                        marketStatsApiService.getMyEntryDetail(entry.id).getOrThrow()
                    }
                onLoaded(fullEntry)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load owner detail for managed market entry ${entry.id}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateEntryState(
        entry: MarketV2PublisherEntrySummary,
        stateCode: String,
        action: suspend () -> Result<MarketV2Entry>,
        successMessage: String
    ) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                return@launch
            }
            if (entry.id.isBlank()) {
                _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, "entry not found")
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null
            try {
                action().fold(
                    onSuccess = {
                        _entries.value =
                            _entries.value.map { existing ->
                                if (existing.id == entry.id) existing.copy(stateCode = stateCode) else existing
                            }
                        Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message ?: context.getString(R.string.market_error_action_failed)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_action_failed)
                AppLogger.e(TAG, "Failed to update managed market entry", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    class Factory(
        private val context: Context,
        private val kind: UnifiedMarketManageKind
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UnifiedMarketManageViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UnifiedMarketManageViewModel(context, kind) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "UnifiedMarketManageViewModel"
    }
}



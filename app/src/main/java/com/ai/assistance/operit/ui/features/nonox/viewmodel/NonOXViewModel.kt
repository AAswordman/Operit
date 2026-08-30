package com.ai.assistance.operit.ui.features.nonox.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.integration.NonOIntegrationManager
import com.ai.assistance.operit.util.AppLogger
import com.ai.nonoassistance.memory.ContextBudgetManager
import com.ai.nonoassistance.provider.ModelPricingService
import com.ai.nonoassistance.provider.PricingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for NonOX screens.
 * Manages state for ModeSwitcher, ModelPricing, and BudgetStats screens.
 */
class NonOXViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NonOXViewModel"
    }

    // --- Mode Switcher State ---

    data class ModeSwitcherState(
        val selectedMode: String = "CHAT",
        val modes: List<ModeInfo> = listOf(
            ModeInfo("CHAT", "Chat", "Conversational AI interface with streaming responses", "chat"),
            ModeInfo("IDE", "IDE", "Code editor with file tree, diff view, and AI assistance", "code"),
            ModeInfo("CLI", "CLI", "Full-screen terminal with AI-powered shell commands", "terminal"),
            ModeInfo("IMAGE_GEN", "Image Generation", "Canvas-based interface for AI image generation", "image")
        )
    )

    data class ModeInfo(
        val id: String,
        val displayName: String,
        val description: String,
        val icon: String
    )

    private val _modeSwitcherState = MutableStateFlow(ModeSwitcherState())
    val modeSwitcherState: StateFlow<ModeSwitcherState> = _modeSwitcherState.asStateFlow()

    // --- Model Pricing State ---

    data class PricingState(
        val isLoading: Boolean = true,
        val pricingData: Map<String, PricingConfig.ModelPricing> = emptyMap(),
        val selectedProvider: String? = null,
        val error: String? = null
    )

    private val _pricingState = MutableStateFlow(PricingState())
    val pricingState: StateFlow<PricingState> = _pricingState.asStateFlow()

    // --- Budget Stats State ---

    data class BudgetStatsState(
        val isLoading: Boolean = true,
        val stats: ContextBudgetManager.BudgetStats? = null,
        val error: String? = null
    )

    private val _budgetStatsState = MutableStateFlow(BudgetStatsState())
    val budgetStatsState: StateFlow<BudgetStatsState> = _budgetStatsState.asStateFlow()

    init {
        loadPricingData()
        loadBudgetStats()
    }

    // --- Mode Switcher Actions ---

    fun selectMode(modeId: String) {
        _modeSwitcherState.update { it.copy(selectedMode = modeId) }
        AppLogger.d(TAG, "Mode selected: $modeId")
    }

    // --- Pricing Actions ---

    fun selectProvider(provider: String?) {
        _pricingState.update { it.copy(selectedProvider = provider) }
    }

    private fun loadPricingData() {
        viewModelScope.launch {
            try {
                val pricingService = NonOIntegrationManager.pricingService
                val data = pricingService.getPricing()
                _pricingState.update {
                    it.copy(
                        isLoading = false,
                        pricingData = data,
                        error = null
                    )
                }
                AppLogger.d(TAG, "Loaded pricing data: ${data.size} models")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load pricing data", e)
                _pricingState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun refreshPricing() {
        _pricingState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val pricingService = NonOIntegrationManager.pricingService
                pricingService.refreshPricing()
                val data = pricingService.getPricing()
                _pricingState.update {
                    it.copy(
                        isLoading = false,
                        pricingData = data,
                        error = null
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to refresh pricing data", e)
                _pricingState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    // --- Budget Stats Actions ---

    private fun loadBudgetStats() {
        viewModelScope.launch {
            try {
                val budgetManager = NonOIntegrationManager.contextBudgetManager
                val stats = budgetManager.getBudgetStats()
                _budgetStatsState.update {
                    it.copy(
                        isLoading = false,
                        stats = stats,
                        error = null
                    )
                }
                AppLogger.d(TAG, "Loaded budget stats: ${stats.usedTokens}/${stats.maxTokens} tokens")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load budget stats", e)
                _budgetStatsState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun refreshBudgetStats() {
        _budgetStatsState.update { it.copy(isLoading = true, error = null) }
        loadBudgetStats()
    }
}

package com.ai.assistance.operit.core.integration

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.nonoassistance.memory.ContextBudgetManager
import com.ai.nonoassistance.memory.BudgetConfig
import com.ai.nonoassistance.memory.GlobalMemoryStore
import com.ai.nonoassistance.orchestration.OrchestrationEngine
import com.ai.nonoassistance.orchestration.RoleAssignmentConfig
import com.ai.nonoassistance.provider.OperitProviderAdapter
import com.ai.nonoassistance.provider.ProviderRegistry
import com.ai.nonoassistance.provider.ModelPricingService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Central integration manager for OperitX new modules.
 *
 * Initializes and wires:
 * - ProviderRegistry + OperitProviderAdapter (bridges to existing AIService)
 * - OrchestrationEngine (multi-AI leader/worker)
 * - GlobalMemoryStore + ContextBudgetManager
 * - ToolCallSanitizer (hooked into ToolExecutionManager)
 * - RootExecutionGuard (permission system)
 *
 * Must be initialized after OperitApplication.onCreate().
 */
object NonOIntegrationManager {
    private const val TAG = "NonOIntegrationManager"

    @Volatile
    private var initialized = false

    // Core modules
    lateinit var providerRegistry: ProviderRegistry
        private set
    lateinit var pricingService: ModelPricingService
        private set
    lateinit var orchestrationEngine: OrchestrationEngine
        private set
    lateinit var memoryStore: GlobalMemoryStore
        private set
    lateinit var contextBudgetManager: ContextBudgetManager
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initialize all OperitX modules. Call once from OperitApplication.onCreate().
     */
    fun initialize(context: Context) {
        if (initialized) {
            AppLogger.w(TAG, "NonOIntegrationManager already initialized")
            return
        }

        AppLogger.i(TAG, "Initializing OperitX integration modules...")

        try {
            // 1. Provider system
            providerRegistry = ProviderRegistry()
            pricingService = ModelPricingService()

            // Register existing Operit providers via adapter
            OperitProviderAdapter.registerExistingProviders(providerRegistry)

            // 2. Orchestration engine
            val defaultRoleConfig = RoleAssignmentConfig(
                leaderModelId = "deepseek-chat",
                workerModelIds = listOf("deepseek-chat", "gemini-2.0-flash"),
                maxWorkers = 3,
                maxRetries = 3
            )
            orchestrationEngine = OrchestrationEngine(defaultRoleConfig)

            // 3. Memory & context
            val memoryDir = File(context.filesDir, "nono_memory")
            memoryDir.mkdirs()
            memoryStore = GlobalMemoryStore(memoryDir)

            contextBudgetManager = ContextBudgetManager(
                BudgetConfig(
                    maxTokens = 256_000,
                    autoSummarizeThreshold = 0.8
                )
            )

            // 4. Pricing service is ready (lazy-loaded on first getPricing call)
            AppLogger.i(TAG, "Model pricing service ready")

            initialized = true
            AppLogger.i(TAG, "NonOIntegrationManager initialized successfully")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize NonOIntegrationManager", e)
            throw e
        }
    }

    /**
     * Get the provider adapter for use with existing chat services.
     */
    fun getProviderAdapter(): OperitProviderAdapter {
        check(initialized) { "NonOIntegrationManager not initialized" }
        return OperitProviderAdapter(providerRegistry)
    }

    /**
     * Get budget stats for display in UI.
     */
    fun getBudgetStats(): ContextBudgetManager.BudgetStats {
        check(initialized) { "NonOIntegrationManager not initialized" }
        return contextBudgetManager.getStats()
    }

    /**
     * Check if integration is ready.
     */
    fun isInitialized(): Boolean = initialized
}

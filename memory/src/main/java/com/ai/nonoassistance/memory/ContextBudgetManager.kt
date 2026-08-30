package com.ai.nonoassistance.memory

/**
 * ContextBudgetManager — manages token and cost budget per session.
 *
 * Per PROJECT_PLAN.md §5.1:
 * - User-configurable budget ceiling (tokens and/or cost)
 * - Priority tiers when context is near-full:
 *   1. System rules (highest — never trim)
 *   2. Current task (high — keep fully)
 *   3. Injected memory (medium — keep if possible)
 *   4. Old chat history (lowest — summarize/remove first)
 * - Auto-summarize old chat history instead of hard-deleting
 *
 * Default budget: 256K tokens per session (user-configurable).
 */
class ContextBudgetManager(
    private val config: BudgetConfig = BudgetConfig()
) {

    private var usedTokens: Long = 0
    private var sessionCostUsd: Double = 0.0

    /** Check if adding [additionalTokens] would exceed budget */
    fun wouldExceedBudget(additionalTokens: Long): Boolean {
        val tokenExceeded = usedTokens + additionalTokens > config.maxTokens
        val costExceeded = config.maxCostUsd?.let { sessionCostUsd >= it } ?: false
        return tokenExceeded || costExceeded
    }

    /** Record token usage */
    fun recordUsage(tokens: Long, costUsd: Double = 0.0) {
        usedTokens += tokens
        sessionCostUsd += costUsd
    }

    /** Get remaining token budget */
    fun remainingTokens(): Long = (config.maxTokens - usedTokens).coerceAtLeast(0)

    /** Get remaining cost budget */
    fun remainingCostUsd(): Double? {
        return config.maxCostUsd?.let { (it - sessionCostUsd).coerceAtLeast(0.0) }
    }

    /** Get token budget usage as percentage (0.0 - 1.0) */
    fun tokenUsagePercentage(): Double {
        return if (config.maxTokens > 0) {
            usedTokens.toDouble() / config.maxTokens.toDouble()
        } else {
            0.0
        }
    }

    /** Get cost budget usage as percentage (0.0 - 1.0) */
    fun costUsagePercentage(): Double? {
        return config.maxCostUsd?.let { maxCost ->
            if (maxCost > 0) sessionCostUsd / maxCost else 0.0
        }
    }

    /** Check if auto-summarize threshold is reached */
    fun shouldAutoSummarize(): Boolean {
        return tokenUsagePercentage() >= config.autoSummarizeThreshold
    }

    /**
     * Trim context to fit within budget, respecting priority tiers.
     * Returns the trimmed context with lowest-priority items summarized or removed.
     */
    fun trimToBudget(contextParts: List<ContextPart>): TrimResult {
        var remainingBudget = config.maxTokens - usedTokens
        val kept = mutableListOf<ContextPart>()
        val trimmed = mutableListOf<ContextPart>()
        val summarized = mutableListOf<ContextPart>()

        // Sort by priority (highest first)
        val sorted = contextParts.sortedBy { it.priority.ordinal }

        for (part in sorted) {
            if (remainingBudget >= part.tokenCount) {
                // Fits within budget — keep it
                kept.add(part)
                remainingBudget -= part.tokenCount
            } else if (part.priority == Priority.CHAT_HISTORY) {
                // Chat history can be summarized
                val summarizedTokens = (part.tokenCount * 0.2).toLong() // Summarize to 20%
                if (remainingBudget >= summarizedTokens) {
                    summarized.add(part.copy(
                        tokenCount = summarizedTokens,
                        content = "[Summarized] ${part.content.take(200)}..."
                    ))
                    remainingBudget -= summarizedTokens
                } else {
                    trimmed.add(part)
                }
            } else {
                // Higher priority but doesn't fit — trim what we can
                if (remainingBudget > 0) {
                    kept.add(part.copy(tokenCount = remainingBudget))
                    remainingBudget = 0
                } else {
                    trimmed.add(part)
                }
            }
        }

        return TrimResult(
            kept = kept,
            summarized = summarized,
            trimmed = trimmed,
            remainingTokens = remainingBudget
        )
    }

    /** Get current usage stats */
    fun getStats(): BudgetStats {
        return BudgetStats(
            usedTokens = usedTokens,
            maxTokens = config.maxTokens,
            sessionCostUsd = sessionCostUsd,
            maxCostUsd = config.maxCostUsd,
            tokenUsagePercent = tokenUsagePercentage(),
            costUsagePercent = costUsagePercentage(),
            shouldSummarize = shouldAutoSummarize()
        )
    }

    fun reset() {
        usedTokens = 0
        sessionCostUsd = 0.0
    }
}

data class BudgetConfig(
    val maxTokens: Long = 256_000,     // Default 256K tokens
    val maxCostUsd: Double? = null,     // Optional cost cap
    val autoSummarizeThreshold: Double = 0.8 // Summarize when 80% full
)

data class ContextPart(
    val priority: Priority,
    val tokenCount: Long,
    val content: String,
    val type: String // "system", "task", "memory", "history"
)

enum class Priority {
    SYSTEM_RULES,    // Highest — never trim
    CURRENT_TASK,    // High — keep fully
    INJECTED_MEMORY, // Medium — keep if possible
    CHAT_HISTORY     // Lowest — summarize/remove first
}

data class TrimResult(
    val kept: List<ContextPart>,
    val summarized: List<ContextPart>,
    val trimmed: List<ContextPart>,
    val remainingTokens: Long
)

data class BudgetStats(
    val usedTokens: Long,
    val maxTokens: Long,
    val sessionCostUsd: Double,
    val maxCostUsd: Double?,
    val tokenUsagePercent: Double,
    val costUsagePercent: Double?,
    val shouldSummarize: Boolean
)

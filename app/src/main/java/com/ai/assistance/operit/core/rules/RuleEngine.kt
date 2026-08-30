package com.ai.assistance.operit.core.rules

import kotlinx.serialization.Serializable

/**
 * RuleEngine — manages layered rules with conflict resolution.
 *
 * Per PROJECT_PLAN.md §10:
 * - Rule layers: Global (all projects) → Project (this project) → Session (temporary)
 * - Narrower layer wins when rules conflict
 * - Rules define AI behavior constraints and permissions
 */
class RuleEngine {

    private val globalRules = mutableListOf<Rule>()
    private val projectRules = mutableListOf<Rule>()
    private val sessionRules = mutableListOf<Rule>()

    /**
     * Add a rule to a specific layer.
     */
    fun addRule(rule: Rule, layer: RuleLayer) {
        when (layer) {
            RuleLayer.GLOBAL -> globalRules.add(rule)
            RuleLayer.PROJECT -> projectRules.add(rule)
            RuleLayer.SESSION -> sessionRules.add(rule)
        }
    }

    /**
     * Remove a rule by ID from a specific layer.
     */
    fun removeRule(ruleId: String, layer: RuleLayer) {
        val targetList = when (layer) {
            RuleLayer.GLOBAL -> globalRules
            RuleLayer.PROJECT -> projectRules
            RuleLayer.SESSION -> sessionRules
        }
        targetList.removeAll { it.id == ruleId }
    }

    /**
     * Get all effective rules, with narrower layers overriding wider ones.
     * Session > Project > Global
     */
    fun getEffectiveRules(): List<Rule> {
        val effective = mutableMapOf<String, Rule>()

        // Apply global rules first (lowest priority)
        for (rule in globalRules) {
            effective[rule.key] = rule
        }

        // Override with project rules
        for (rule in projectRules) {
            effective[rule.key] = rule
        }

        // Override with session rules (highest priority)
        for (rule in sessionRules) {
            effective[rule.key] = rule
        }

        return effective.values.toList()
    }

    /**
     * Get rules for a specific layer.
     */
    fun getRules(layer: RuleLayer): List<Rule> {
        return when (layer) {
            RuleLayer.GLOBAL -> globalRules.toList()
            RuleLayer.PROJECT -> projectRules.toList()
            RuleLayer.SESSION -> sessionRules.toList()
        }
    }

    /**
     * Check if a specific rule key exists and get its value.
     */
    fun getRuleValue(key: String): String? {
        return getEffectiveRules().find { it.key == key }?.value
    }

    /**
     * Clear session rules (called when session ends).
     */
    fun clearSessionRules() {
        sessionRules.clear()
    }

    /**
     * Clear all rules.
     */
    fun clearAll() {
        globalRules.clear()
        projectRules.clear()
        sessionRules.clear()
    }

    /**
     * Get rule count per layer.
     */
    fun getRuleCounts(): Map<RuleLayer, Int> {
        return mapOf(
            RuleLayer.GLOBAL to globalRules.size,
            RuleLayer.PROJECT to projectRules.size,
            RuleLayer.SESSION to sessionRules.size
        )
    }
}

@Serializable
data class Rule(
    val id: String,
    val key: String,
    val value: String,
    val description: String = "",
    val enabled: Boolean = true
)

enum class RuleLayer(val displayName: String) {
    GLOBAL("Global"),
    PROJECT("Project"),
    SESSION("Session")
}

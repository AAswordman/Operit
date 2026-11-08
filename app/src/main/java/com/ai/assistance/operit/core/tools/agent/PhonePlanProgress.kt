package com.ai.assistance.operit.core.tools.agent

/** Stop repeated plans that never achieve a checked mutation; video updates and Wait do not count. */
internal class PhonePlanProgress(private val limit: Int = 3) {
    var stalledPlans: Int = 0
        private set

    fun record(verifiedActions: Int, inspect: Boolean): Boolean {
        if (verifiedActions > 0) stalledPlans = 0
        else if (!inspect) stalledPlans++
        return stalledPlans >= limit
    }

    fun reset() { stalledPlans = 0 }
}

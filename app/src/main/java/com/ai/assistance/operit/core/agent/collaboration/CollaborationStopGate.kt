package com.ai.assistance.operit.core.agent.collaboration

/** Access under the coordinator lock. A stop invalidates work prepared before cancellation. */
internal class CollaborationStopGate {
    private val generations = mutableMapOf<String, Long>()
    private val stopping = mutableSetOf<String>()

    fun generation(root: String): Long {
        check(root !in stopping) { "Agent tree is stopping" }
        return generations[root] ?: 0L
    }

    fun checkCurrent(root: String, generation: Long) {
        check(generation(root) == generation) { "Agent operation was cancelled by the user" }
    }

    fun isStopping(root: String): Boolean = root in stopping

    fun begin(root: String): Boolean {
        if (!stopping.add(root)) return false
        generations[root] = (generations[root] ?: 0L) + 1
        return true
    }

    fun end(root: String) {
        stopping.remove(root)
    }
}

package com.ai.assistance.operit.core.agent.collaboration

/** Root-scoped addressing, matching Codex's canonical and caller-relative task paths. */
object AgentPath {
    const val ROOT = "/root"
    private val taskName = Regex("[a-z0-9_]+")

    fun child(parent: String, name: String): String {
        require(name != "root" && taskName.matches(name)) {
            "task_name must contain only lowercase letters, digits, and underscores"
        }
        require(isCanonical(parent)) { "Invalid parent agent path" }
        return "$parent/$name"
    }

    fun resolve(caller: String, target: String): String {
        require(target.isNotBlank()) { "target must not be empty" }
        val result = if (target.startsWith('/')) target else "$caller/$target"
        require(isCanonical(result)) { "Invalid agent path: $target" }
        return result
    }

    fun isCanonical(path: String): Boolean =
        path == ROOT ||
            (path.startsWith("$ROOT/") &&
                path.removePrefix("$ROOT/").split('/').all { it != "root" && taskName.matches(it) })

    fun isWithin(path: String, prefix: String): Boolean =
        path == prefix || path.startsWith("$prefix/")
}

sealed interface AgentFork {
    data object All : AgentFork
    data object None : AgentFork
    data class LastTurns(val count: Int) : AgentFork

    companion object {
        fun parse(value: String?): AgentFork =
            when (val normalized = value?.trim()?.lowercase().orEmpty().ifEmpty { "all" }) {
                "all" -> All
                "none" -> None
                else -> LastTurns(
                    requireNotNull(normalized.toIntOrNull()?.takeIf { it > 0 }) {
                        "fork_turns must be none, all, or a positive integer string"
                    }
                )
            }
    }
}

package com.ai.nonoassistance.tools.permission

import com.ai.assistance.operit.util.AppLogger

/**
 * RootExecutionGuard — controls root/sudo command execution permissions.
 *
 * Per PROJECT_PLAN.md §11 (PermissionMatrix):
 * - Leader agent may approve root commands
 * - Worker agents are restricted to sandbox only
 * - Root commands require explicit user approval or policy-based auto-approval
 *
 * This guard integrates with the existing ToolPermissionSystem to add
 * root-specific permission checks before executing privileged commands.
 *
 * Usage:
 * ```kotlin
 * val guard = RootExecutionGuard()
 * val decision = guard.checkRootCommand("rm -rf /data/test", role = "worker")
 * // decision = Blocked("Worker role cannot execute root commands")
 * ```
 */
class RootExecutionGuard {

    companion object {
        private const val TAG = "RootExecutionGuard"

        // Commands that are always blocked regardless of role
        private val ALWAYS_BLOCKED = listOf(
            "rm -rf /",
            "rm -rf /*",
            "mkfs",
            "dd if=",
            ":(){ :|:& };:",  // Fork bomb
            "chmod -R 777 /",
            "chown -R root:root /",
        )

        // Commands that require explicit user confirmation
        private val REQUIRES_CONFIRMATION = listOf(
            "rm -rf",
            "chmod",
            "chown",
            "mount",
            "umount",
            "reboot",
            "shutdown",
            "kill -9",
            "pkill",
            "apt install",
            "apt remove",
            "pip install",
            "npm install -g",
        )

        // Patterns for detecting root/sudo commands
        private val ROOT_PATTERNS = listOf(
            Regex("""^sudo\s"""),
            Regex("""^su\s"""),
            Regex("""^\s*"""),
            Regex("""^/system/bin/"""),
            Regex("""^/vendor/bin/"""),
        )
    }

    /**
     * Check if a command is a root/privileged command.
     */
    fun isRootCommand(command: String): Boolean {
        val trimmed = command.trim()
        return ROOT_PATTERNS.any { it.containsMatchIn(trimmed) }
    }

    /**
     * Check if a command is allowed for the given role.
     *
     * @param command The command to check
     * @param role The agent role ("leader", "worker", or "user")
     * @param userApproved Whether the user has explicitly approved this command
     * @return Permission decision
     */
    fun checkRootCommand(
        command: String,
        role: String = "user",
        userApproved: Boolean = false
    ): RootPermissionDecision {
        val trimmed = command.trim()

        // Check always-blocked commands
        for (blocked in ALWAYS_BLOCKED) {
            if (trimmed.contains(blocked)) {
                AppLogger.w(TAG, "Command blocked (always): $trimmed")
                return RootPermissionDecision.Blocked(
                    "This command is permanently blocked for safety: $blocked"
                )
            }
        }

        // Non-root commands are always allowed
        if (!isRootCommand(trimmed)) {
            return RootPermissionDecision.Allowed
        }

        // Root commands for workers are always blocked
        if (role == "worker") {
            AppLogger.w(TAG, "Root command blocked for worker role: $trimmed")
            return RootPermissionDecision.Blocked(
                "Worker agents cannot execute root/sudo commands. " +
                "Escalate to leader or ask user for approval."
            )
        }

        // Root commands for leader require user approval (unless auto-approved)
        if (role == "leader" && !userApproved) {
            // Check if command requires explicit confirmation
            val needsConfirmation = REQUIRES_CONFIRMATION.any { trimmed.contains(it) }
            if (needsConfirmation) {
                return RootPermissionDecision.RequiresConfirmation(
                    "Root command requires user approval: $trimmed"
                )
            }
        }

        // Approved root command
        AppLogger.d(TAG, "Root command allowed: $trimmed (role=$role, approved=$userApproved)")
        return RootPermissionDecision.Allowed
    }

    /**
     * Get a safe version of a command by removing dangerous flags.
     * Returns the original command if no sanitization is needed.
     */
    fun sanitizeCommand(command: String): String {
        var result = command.trim()

        // Remove recursive force flags from rm
        result = result.replace(Regex("""rm\s+-rf\b"""), "rm -r")

        // Remove no-preserve-root flag
        result = result.replace(Regex("""--no-preserve-root"""), "")

        return result.trim()
    }

    sealed class RootPermissionDecision {
        object Allowed : RootPermissionDecision()
        data class Blocked(val reason: String) : RootPermissionDecision()
        data class RequiresConfirmation(val message: String) : RootPermissionDecision()
    }
}

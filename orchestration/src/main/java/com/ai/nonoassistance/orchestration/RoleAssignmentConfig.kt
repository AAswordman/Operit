package com.ai.nonoassistance.orchestration

import kotlinx.serialization.Serializable

/**
 * RoleAssignmentConfig — defines leader/worker roles and their capabilities.
 *
 * Per PROJECT_PLAN.md §4.2:
 * - Leader: smartest model, full permissions, decomposes tasks and reviews output
 * - Worker(s): cheaper/faster model, restricted permissions, executes subtasks
 *
 * Users can adjust roles, worker count, and model per role from settings UI.
 */
@Serializable
data class RoleAssignmentConfig(
    val leader: RoleConfig = RoleConfig(
        model = "", // To be configured by user
        responsibilities = listOf(
            Responsibility.DECOMPOSE_TASK,
            Responsibility.REVIEW_OUTPUT,
            Responsibility.DISPATCH_FIX
        ),
        permission = PermissionLevel.FULL
    ),
    val workers: List<RoleConfig> = listOf(
        RoleConfig(
            model = "", // To be configured by user
            responsibilities = listOf(
                Responsibility.EXECUTE_SUBTASK,
                Responsibility.REPORT_RESULT
            ),
            permission = PermissionLevel.RESTRICTED
        )
    ),
    val maxWorkersPerSession: Int = 3
)

@Serializable
data class RoleConfig(
    val model: String,
    val responsibilities: List<Responsibility>,
    val permission: PermissionLevel
)

@Serializable
enum class Responsibility {
    DECOMPOSE_TASK,
    REVIEW_OUTPUT,
    DISPATCH_FIX,
    EXECUTE_SUBTASK,
    REPORT_RESULT
}

@Serializable
enum class PermissionLevel {
    FULL,
    RESTRICTED
}

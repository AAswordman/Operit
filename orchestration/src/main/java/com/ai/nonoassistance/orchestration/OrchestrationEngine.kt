package com.ai.nonoassistance.orchestration

import kotlinx.serialization.Serializable

/**
 * OrchestrationEngine — manages multi-AI leader/worker collaboration.
 *
 * Per PROJECT_PLAN.md §4:
 * - Leader Agent: receives user task → decomposes into subtasks → dispatches to Workers → reviews → merges results
 * - Worker Agent(s): execute individual subtasks, report results back to Leader
 * - Supports parallel subtask execution when subtasks are independent
 * - Retry loop with max retry to prevent infinite loops
 */
class OrchestrationEngine(
    private val config: RoleAssignmentConfig = RoleAssignmentConfig()
) {

    /** Maximum retry count per subtask to prevent infinite loops */
    var maxRetries: Int = 3

    private val taskHistory = mutableListOf<TaskExecution>()

    /**
     * Execute a complex task using leader/worker pattern.
     *
     * Flow:
     * 1. Leader decomposes task into subtasks
     * 2. Dispatch subtasks to workers (parallel if independent)
     * 3. Collect worker results
     * 4. Leader reviews and merges
     * 5. If issues found, dispatch fix (with retry limit)
     * 6. Return final result
     */
    suspend fun executeTask(
        task: String,
        taskDecomposer: TaskDecomposer,
        workerExecutor: WorkerExecutor,
        resultReviewer: ResultReviewer
    ): OrchestrationResult {
        val executionId = generateExecutionId()
        val subtasks = taskDecomposer.decompose(task)

        if (subtasks.isEmpty()) {
            return OrchestrationResult(
                success = false,
                output = "Failed to decompose task into subtasks",
                subtaskResults = emptyList(),
                executionId = executionId
            )
        }

        // Dispatch and execute subtasks with retry
        val subtaskResults = executeSubtasks(subtasks, workerExecutor, resultReviewer)

        // Leader reviews all results
        val reviewResult = resultReviewer.review(task, subtaskResults)

        val execution = TaskExecution(
            id = executionId,
            task = task,
            subtaskCount = subtasks.size,
            success = reviewResult.success,
            timestamp = System.currentTimeMillis()
        )
        taskHistory.add(execution)

        return OrchestrationResult(
            success = reviewResult.success,
            output = reviewResult.mergedOutput,
            subtaskResults = subtaskResults,
            executionId = executionId
        )
    }

    private suspend fun executeSubtasks(
        subtasks: List<Subtask>,
        workerExecutor: WorkerExecutor,
        resultReviewer: ResultReviewer
    ): List<SubtaskResult> {
        val results = mutableListOf<SubtaskResult>()

        // Group subtasks by dependency level for parallel execution
        val independentGroups = groupByDependency(subtasks)

        for (group in independentGroups) {
            // Execute independent subtasks in parallel
            val groupResults = group.map { subtask ->
                executeWithRetry(subtask, workerExecutor, resultReviewer)
            }
            results.addAll(groupResults)
        }

        return results
    }

    private suspend fun executeWithRetry(
        subtask: Subtask,
        workerExecutor: WorkerExecutor,
        resultReviewer: ResultReviewer
    ): SubtaskResult {
        var attempt = 0
        var lastResult: SubtaskResult? = null

        while (attempt < maxRetries) {
            attempt++

            val result = workerExecutor.execute(subtask)
            val review = resultReviewer.reviewSubtask(subtask, result)

            if (review.success) {
                return result.copy(retryCount = attempt - 1)
            }

            // If not the last attempt, create a fix subtask
            if (attempt < maxRetries) {
                val fixSubtask = subtask.copy(
                    id = "${subtask.id}-fix-$attempt",
                    instruction = "${subtask.instruction}\n\nPrevious attempt failed: ${review.feedback}\nPlease fix the issue."
                )
                val fixResult = workerExecutor.execute(fixSubtask)
                if (resultReviewer.reviewSubtask(fixSubtask, fixResult).success) {
                    return fixResult.copy(retryCount = attempt)
                }
            }

            lastResult = result
        }

        // All retries exhausted
        return lastResult?.copy(retryCount = attempt - 1) ?: SubtaskResult(
            subtaskId = subtask.id,
            workerId = "unknown",
            input = subtask.instruction,
            output = "All retries exhausted",
            success = false,
            retryCount = attempt - 1
        )
    }

    /**
     * Group subtasks by dependency level for parallel execution.
     * Subtasks with no dependencies can run in parallel.
     */
    private fun groupByDependency(subtasks: List<Subtask>): List<List<Subtask>> {
        // Simple implementation: treat each subtask as independent
        // Phase 3 TODO: implement proper dependency graph analysis
        return subtasks.map { listOf(it) }
    }

    private fun generateExecutionId(): String {
        return "exec-${System.currentTimeMillis()}-${(Math.random() * 1000).toInt()}"
    }

    /** Get execution history */
    fun getHistory(): List<TaskExecution> = taskHistory.toList()

    /** Clear execution history */
    fun clearHistory() {
        taskHistory.clear()
    }
}

// --- Data Classes ---

@Serializable
data class Subtask(
    val id: String,
    val instruction: String,
    val dependencies: List<String> = emptyList(),
    val assignedWorkerIndex: Int = 0
)

@Serializable
data class SubtaskResult(
    val subtaskId: String,
    val workerId: String,
    val input: String,
    val output: String,
    val success: Boolean,
    val retryCount: Int = 0
)

@Serializable
data class OrchestrationResult(
    val success: Boolean,
    val output: String,
    val subtaskResults: List<SubtaskResult>,
    val executionId: String = ""
)

@Serializable
data class ReviewResult(
    val success: Boolean,
    val mergedOutput: String,
    val feedback: String = ""
)

@Serializable
data class TaskExecution(
    val id: String,
    val task: String,
    val subtaskCount: Int,
    val success: Boolean,
    val timestamp: Long
)

// --- Interfaces for extensibility ---

interface TaskDecomposer {
    /** Decompose a task into subtasks */
    suspend fun decompose(task: String): List<Subtask>
}

interface WorkerExecutor {
    /** Execute a subtask and return the result */
    suspend fun execute(subtask: Subtask): SubtaskResult
}

interface ResultReviewer {
    /** Review the final merged result */
    suspend fun review(task: String, results: List<SubtaskResult>): ReviewResult

    /** Review a single subtask result */
    suspend fun reviewSubtask(subtask: Subtask, result: SubtaskResult): SubtaskReview
}

@Serializable
data class SubtaskReview(
    val success: Boolean,
    val feedback: String = ""
)

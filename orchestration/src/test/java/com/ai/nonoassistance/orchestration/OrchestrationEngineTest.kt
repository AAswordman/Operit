package com.ai.nonoassistance.orchestration

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OrchestrationEngineTest {

    private lateinit var engine: OrchestrationEngine

    @Before
    fun setup() {
        engine = OrchestrationEngine()
        engine.maxRetries = 3
    }

    @Test
    fun `executeTask returns failure when decomposition returns empty`() = runTest {
        val result = engine.executeTask(
            task = "test task",
            taskDecomposer = object : TaskDecomposer {
                override suspend fun decompose(task: String) = emptyList<Subtask>()
            },
            workerExecutor = object : WorkerExecutor {
                override suspend fun execute(subtask: Subtask) = SubtaskResult(
                    subtaskId = subtask.id,
                    workerId = "w1",
                    input = subtask.instruction,
                    output = "done",
                    success = true
                )
            },
            resultReviewer = object : ResultReviewer {
                override suspend fun review(task: String, results: List<SubtaskResult>) = ReviewResult(
                    success = true,
                    mergedOutput = "ok"
                )
                override suspend fun reviewSubtask(subtask: Subtask, result: SubtaskResult) = SubtaskReview(
                    success = true
                )
            }
        )

        assertFalse(result.success)
        assertTrue(result.output.contains("Failed to decompose"))
    }

    @Test
    fun `executeTask succeeds with single subtask`() = runTest {
        val result = engine.executeTask(
            task = "test task",
            taskDecomposer = object : TaskDecomposer {
                override suspend fun decompose(task: String) = listOf(
                    Subtask(id = "s1", instruction = "do something")
                )
            },
            workerExecutor = object : WorkerExecutor {
                override suspend fun execute(subtask: Subtask) = SubtaskResult(
                    subtaskId = subtask.id,
                    workerId = "w1",
                    input = subtask.instruction,
                    output = "completed",
                    success = true
                )
            },
            resultReviewer = object : ResultReviewer {
                override suspend fun review(task: String, results: List<SubtaskResult>) = ReviewResult(
                    success = true,
                    mergedOutput = "All done"
                )
                override suspend fun reviewSubtask(subtask: Subtask, result: SubtaskResult) = SubtaskReview(
                    success = true
                )
            }
        )

        assertTrue(result.success)
        assertEquals("All done", result.output)
        assertEquals(1, result.subtaskResults.size)
    }

    @Test
    fun `executeTask retries on failure`() = runTest {
        var attemptCount = 0

        val result = engine.executeTask(
            task = "test task",
            taskDecomposer = object : TaskDecomposer {
                override suspend fun decompose(task: String) = listOf(
                    Subtask(id = "s1", instruction = "do something")
                )
            },
            workerExecutor = object : WorkerExecutor {
                override suspend fun execute(subtask: Subtask): SubtaskResult {
                    attemptCount++
                    return SubtaskResult(
                        subtaskId = subtask.id,
                        workerId = "w1",
                        input = subtask.instruction,
                        output = if (attemptCount < 3) "failed" else "success",
                        success = attemptCount >= 3
                    )
                }
            },
            resultReviewer = object : ResultReviewer {
                override suspend fun review(task: String, results: List<SubtaskResult>) = ReviewResult(
                    success = true,
                    mergedOutput = "ok"
                )
                override suspend fun reviewSubtask(subtask: Subtask, result: SubtaskResult) = SubtaskReview(
                    success = result.output == "success"
                )
            }
        )

        assertTrue(result.success)
        assertTrue(attemptCount >= 3)
    }

    @Test
    fun `executeTask stops after max retries`() = runTest {
        var attemptCount = 0

        val result = engine.executeTask(
            task = "test task",
            taskDecomposer = object : TaskDecomposer {
                override suspend fun decompose(task: String) = listOf(
                    Subtask(id = "s1", instruction = "do something")
                )
            },
            workerExecutor = object : WorkerExecutor {
                override suspend fun execute(subtask: Subtask): SubtaskResult {
                    attemptCount++
                    return SubtaskResult(
                        subtaskId = subtask.id,
                        workerId = "w1",
                        input = subtask.instruction,
                        output = "always fails",
                        success = false
                    )
                }
            },
            resultReviewer = object : ResultReviewer {
                override suspend fun review(task: String, results: List<SubtaskResult>) = ReviewResult(
                    success = false,
                    mergedOutput = "Failed"
                )
                override suspend fun reviewSubtask(subtask: Subtask, result: SubtaskResult) = SubtaskReview(
                    success = false,
                    feedback = "Wrong output"
                )
            }
        )

        assertFalse(result.success)
        // Each retry loop: 1 execute + 1 fix execute (except last retry)
        // So total calls = maxRetries * 2 - 1 (no fix on last retry)
        assertTrue("attemptCount=$attemptCount should be bounded", attemptCount <= engine.maxRetries * 2)
    }

    @Test
    fun `taskHistory tracks executions`() = runTest {
        assertTrue(engine.getHistory().isEmpty())

        engine.executeTask(
            task = "test",
            taskDecomposer = object : TaskDecomposer {
                override suspend fun decompose(task: String) = listOf(Subtask(id = "s1", instruction = "x"))
            },
            workerExecutor = object : WorkerExecutor {
                override suspend fun execute(subtask: Subtask) = SubtaskResult(
                    subtaskId = "s1", workerId = "w1", input = "x", output = "y", success = true
                )
            },
            resultReviewer = object : ResultReviewer {
                override suspend fun review(task: String, results: List<SubtaskResult>) = ReviewResult(true, "ok")
                override suspend fun reviewSubtask(subtask: Subtask, result: SubtaskResult) = SubtaskReview(true)
            }
        )

        assertEquals(1, engine.getHistory().size)
        assertEquals("test", engine.getHistory()[0].task)
    }

    @Test
    fun `clearHistory removes all entries`() = runTest {
        engine.executeTask(
            task = "test",
            taskDecomposer = object : TaskDecomposer {
                override suspend fun decompose(task: String) = listOf(Subtask(id = "s1", instruction = "x"))
            },
            workerExecutor = object : WorkerExecutor {
                override suspend fun execute(subtask: Subtask) = SubtaskResult(
                    subtaskId = "s1", workerId = "w1", input = "x", output = "y", success = true
                )
            },
            resultReviewer = object : ResultReviewer {
                override suspend fun review(task: String, results: List<SubtaskResult>) = ReviewResult(true, "ok")
                override suspend fun reviewSubtask(subtask: Subtask, result: SubtaskResult) = SubtaskReview(true)
            }
        )

        assertEquals(1, engine.getHistory().size)
        engine.clearHistory()
        assertTrue(engine.getHistory().isEmpty())
    }
}

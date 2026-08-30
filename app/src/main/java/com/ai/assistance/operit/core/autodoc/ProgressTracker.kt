package com.ai.assistance.operit.core.autodoc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * ProgressTracker — calculates and displays project progress.
 *
 * Per PROJECT_PLAN.md §17:
 * - Display % progress of project
 * - Reads from Progress.md
 * - Calculates from task graph (completed tasks / total tasks)
 * - Shows as progress bar in UI
 */
class ProgressTracker(
    private val progressFile: File? = null
) {

    private val _progress = MutableStateFlow(ProjectProgress())
    val progress: StateFlow<ProjectProgress> = _progress.asStateFlow()

    /**
     * Parse Progress.md and calculate overall progress.
     */
    fun calculateProgress(): ProjectProgress {
        if (progressFile == null || !progressFile.exists()) {
            return ProjectProgress()
        }

        val content = progressFile.readText()

        // Count task statuses
        val doneCount = content.lines().count { it.trim().startsWith("- Status:") && it.contains("Done") }
        val inProgressCount = content.lines().count { it.trim().startsWith("- Status:") && it.contains("In Progress") }
        val blockedCount = content.lines().count { it.trim().startsWith("- Status:") && it.contains("Blocked") }
        val totalTasks = doneCount + inProgressCount + blockedCount

        val percentage = if (totalTasks > 0) {
            (doneCount.toDouble() / totalTasks.toDouble() * 100).toInt()
        } else {
            0
        }

        // Parse overall percentage from file if available
        val overallPercent = Regex("""\| Overall % \| (\d+)% \|""")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: percentage

        val result = ProjectProgress(
            completedTasks = doneCount,
            inProgressTasks = inProgressCount,
            blockedTasks = blockedCount,
            totalTasks = totalTasks,
            percentage = overallPercent
        )

        _progress.value = result
        return result
    }

    /**
     * Get a formatted progress bar string.
     */
    fun getProgressBar(width: Int = 20): String {
        val progress = _progress.value
        val filled = (progress.percentage * width / 100).coerceIn(0, width)
        val empty = width - filled

        return "[${"█".repeat(filled)}${"░".repeat(empty)}] ${progress.percentage}%"
    }

    /**
     * Get a human-readable progress summary.
     */
    fun getSummary(): String {
        val progress = _progress.value
        return buildString {
            append("Project Progress: ${progress.percentage}%\n")
            append("Completed: ${progress.completedTasks}/${progress.totalTasks} tasks\n")
            if (progress.inProgressTasks > 0) {
                append("In Progress: ${progress.inProgressTasks}\n")
            }
            if (progress.blockedTasks > 0) {
                append("Blocked: ${progress.blockedTasks}\n")
            }
        }
    }
}

data class ProjectProgress(
    val completedTasks: Int = 0,
    val inProgressTasks: Int = 0,
    val blockedTasks: Int = 0,
    val totalTasks: Int = 0,
    val percentage: Int = 0
)

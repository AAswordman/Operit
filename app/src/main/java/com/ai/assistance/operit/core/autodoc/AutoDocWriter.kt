package com.ai.assistance.operit.core.autodoc

import java.io.File

/**
 * AutoDocWriter — automatically maintains project documentation.
 *
 * Per PROJECT_PLAN.md §8:
 * - Project.md: auto-generate/update with project overview, architecture, decisions
 * - Progress.md: update task status and percentage
 * - SOLUTIONS.md: log problems and fixes encountered during development
 *
 * This is the skeleton implementation. Full integration with the agent system
 * will be completed in Phase 6.
 */
class AutoDocWriter(
    private val docsDir: File
) {

    /**
     * Update Progress.md with task completion status.
     */
    fun updateProgress(
        phaseName: String,
        taskName: String,
        status: TaskStatus,
        percentComplete: Int,
        details: String = ""
    ) {
        val progressFile = File(docsDir, "Progress.md")
        if (!progressFile.exists()) return

        val content = progressFile.readText()
        val timestamp = java.time.Instant.now().toString().take(10)

        // Find and update the task section
        val taskSection = buildString {
            append("### Task: $taskName\n")
            append("- Status: ${status.emoji} ${status.displayName}\n")
            append("- Details: $details\n")
            append("- Last updated: $timestamp\n")
        }

        // Simple append approach (for v1)
        // Phase 6 TODO: implement proper section replacement
        progressFile.appendText("\n\n$taskSection")
    }

    /**
     * Add an entry to SOLUTIONS.md.
     */
    fun addSolution(
        problemTitle: String,
        symptoms: String,
        cause: String,
        solution: String,
        tags: List<String> = emptyList()
    ) {
        val solutionsFile = File(docsDir, "SOLUTIONS.md")
        if (!solutionsFile.exists()) return

        val timestamp = java.time.Instant.now().toString().take(10)
        val tagString = tags.joinToString(" ") { "#$it" }

        val entry = buildString {
            append("\n## [$timestamp] ปัญหา: $problemTitle\n")
            append("**อาการ:** $symptoms\n")
            append("**สาเหตุ:** $cause\n")
            append("**วิธีแก้:** $solution\n")
            append("**Tag:** $tagString\n")
            append("\n---\n")
        }

        solutionsFile.appendText(entry)
    }

    /**
     * Update Project.md decision log.
     */
    fun addDecision(
        decision: String,
        rationale: String
    ) {
        val projectFile = File(docsDir, "Project.md")
        if (!projectFile.exists()) return

        val timestamp = java.time.Instant.now().toString().take(10)
        val entry = "| $timestamp | $decision | $rationale |\n"

        // Find decision log section and append
        // Phase 6 TODO: implement proper section finding
        projectFile.appendText(entry)
    }

    enum class TaskStatus(val displayName: String, val emoji: String) {
        NOT_STARTED("Not started", "⏳"),
        IN_PROGRESS("In Progress", "🔄"),
        DONE("Done", "✅"),
        BLOCKED("Blocked", "🚫")
    }
}

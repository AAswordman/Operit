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
 * This implementation handles proper section replacement in markdown files.
 */
class AutoDocWriter(
    private val docsDir: File
) {

    /**
     * Update Progress.md with task completion status.
     * Replaces existing task section if found, otherwise appends.
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

        val taskSection = buildString {
            append("### Task: $taskName\n")
            append("- Status: ${status.emoji} ${status.displayName}\n")
            append("- % Complete: $percentComplete%\n")
            append("- Details: $details\n")
            append("- Last updated: $timestamp\n")
        }

        val taskHeader = "### Task: $taskName"
        val updatedContent = replaceOrCreateSection(content, taskHeader, taskSection)
        progressFile.writeText(updatedContent)
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
     * Finds the decision log table and appends a new row.
     */
    fun addDecision(
        decision: String,
        rationale: String
    ) {
        val projectFile = File(docsDir, "Project.md")
        if (!projectFile.exists()) return

        val content = projectFile.readText()
        val timestamp = java.time.Instant.now().toString().take(10)
        val entry = "| $timestamp | $decision | $rationale |\n"

        // Try to find decision log table and append after header
        val decisionLogMarker = "| Date | Decision | Rationale |"
        val altMarker = "| วันที่ | การตัดสินใจ | เหตุผล |"

        val markerIndex = content.indexOf(decisionLogMarker).takeIf { it >= 0 }
            ?: content.indexOf(altMarker).takeIf { it >= 0 }

        if (markerIndex != null) {
            // Find end of header row (next line after separator)
            val afterMarker = content.indexOf("\n", markerIndex + 1)
            val afterSeparator = content.indexOf("\n", afterMarker + 1)

            val updatedContent = content.substring(0, afterSeparator + 1) +
                    entry +
                    content.substring(afterSeparator + 1)
            projectFile.writeText(updatedContent)
        } else {
            // No decision log table found — create one
            val newSection = buildString {
                append("\n\n## Decision Log\n\n")
                append("| Date | Decision | Rationale |\n")
                append("|---|---|---|\n")
                append(entry)
            }
            projectFile.appendText(newSection)
        }
    }

    /**
     * Update the overall progress percentage in Progress.md.
     */
    fun updateOverallProgress(percent: Int) {
        val progressFile = File(docsDir, "Progress.md")
        if (!progressFile.exists()) return

        val content = progressFile.readText()
        val timestamp = java.time.Instant.now().toString().take(10)

        // Find the Overall Status table and update the % row
        val overallMarker = "| Overall % |"
        val markerIndex = content.indexOf(overallMarker)

        if (markerIndex >= 0) {
            val lineEnd = content.indexOf("\n", markerIndex)
            val updatedLine = "| Overall % | $percent% |"
            val updatedContent = content.substring(0, markerIndex) + updatedLine + content.substring(lineEnd)
            progressFile.writeText(updatedContent)
        }
    }

    // --- Internal helpers ---

    /**
     * Replace an existing section (identified by header) or create it if not found.
     * A section starts with [header] and ends at the next same-level or higher-level header.
     */
    private fun replaceOrCreateSection(
        content: String,
        sectionHeader: String,
        newContent: String
    ): String {
        val headerIndex = content.indexOf(sectionHeader)
        if (headerIndex < 0) {
            // Section not found — append at end
            return content.trimEnd() + "\n\n" + newContent + "\n"
        }

        // Find the start of the header's line
        val lineStart = content.lastIndexOf("\n", headerIndex).takeIf { it >= 0 }?.plus(1) ?: 0

        // Find end of section (next ### header or end of file)
        val afterHeader = headerIndex + sectionHeader.length
        val nextSectionIndex = content.indexOf("\n### ", afterHeader)

        val sectionEnd = if (nextSectionIndex >= 0) {
            // Include the blank line before next section
            val blankLine = content.lastIndexOf("\n", nextSectionIndex)
            blankLine.takeIf { it > lineStart } ?: nextSectionIndex
        } else {
            content.length
        }

        return content.substring(0, lineStart) + newContent.trimEnd() + "\n" + content.substring(sectionEnd)
    }

    enum class TaskStatus(val displayName: String, val emoji: String) {
        NOT_STARTED("Not started", "⏳"),
        IN_PROGRESS("In Progress", "🔄"),
        DONE("Done", "✅"),
        BLOCKED("Blocked", "🚫")
    }
}

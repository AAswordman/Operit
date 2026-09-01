package com.ai.assistance.operit.core.codeorganizer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CodeOrganizerAgent — runs as a background pass after code edits.
 *
 * Per PROJECT_PLAN.md §16:
 * > จัดระเบียบไฟล์/โค้ด + auto comment
 * > รันเป็น background pass หลังแก้โค้ดเสร็จ
 *
 * Responsibilities:
 * 1. Check file organization (package structure, naming conventions)
 * 2. Detect undocumented public classes/functions
 * 3. Detect dead code (unused private functions, unreachable code)
 * 4. Generate a dry-run diff for user confirmation before applying changes
 */
class CodeOrganizerAgent {

    private val _state = MutableStateFlow<OrganizerState>(OrganizerState.Idle)
    val state: StateFlow<OrganizerState> = _state.asStateFlow()

    /**
     * Run the organization pass on the given source directories.
     * Returns a list of suggested changes that the user must approve before applying.
     */
    suspend fun analyze(projectDir: File): OrganizerReport = withContext(Dispatchers.IO) {
        _state.value = OrganizerState.Analyzing

        val findings = mutableListOf<Finding>()

        // 1. Check for undocumented public classes/functions
        val undocumentedFiles = findUndocumentedPublicMembers(projectDir)
        findings.addAll(undocumentedFiles)

        // 2. Check for unused private functions
        val unusedPrivates = findUnusedPrivateMembers(projectDir)
        findings.addAll(unusedPrivates)

        // 3. Check naming conventions
        val namingIssues = checkNamingConventions(projectDir)
        findings.addAll(namingIssues)

        // 4. Check for very long files that might benefit from splitting
        val largeFiles = findOversizedFiles(projectDir)
        findings.addAll(largeFiles)

        val report = OrganizerReport(
            totalFilesScanned = countSourceFiles(projectDir),
            findings = findings,
            summary = buildSummary(findings)
        )

        _state.value = OrganizerState.Complete(report)
        report
    }

    /**
     * Apply approved findings. Only processes findings the user has approved.
     */
    suspend fun applyApproved(
        projectDir: File,
        approvedFindings: List<Finding>
    ): ApplyResult = withContext(Dispatchers.IO) {
        _state.value = OrganizerState.Applying
        var applied = 0
        var failed = 0

        for (finding in approvedFindings) {
            try {
                when (finding) {
                    is Finding.UndocumentedMember -> {
                        addKdocToFile(projectDir, finding)
                        applied++
                    }
                    is Finding.NamingConvention -> {
                        // Naming fixes require file rename — mark as needing manual action
                        failed++
                    }
                    else -> {
                        // Other findings are informational only
                    }
                }
            } catch (e: Exception) {
                failed++
            }
        }

        val result = ApplyResult(applied = applied, failed = failed)
        _state.value = OrganizerState.Idle
        result
    }

    // --- Analysis helpers ---

    private fun findUndocumentedPublicMembers(dir: File): List<Finding> {
        val findings = mutableListOf<Finding>()
        val ktFiles = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("test") }
            .toList()

        for (file in ktFiles) {
            val lines = file.readLines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                // Detect public class/object/function without KDoc
                if ((line.startsWith("class ") || line.startsWith("object ") ||
                        line.startsWith("fun ")) && !line.startsWith("private ") &&
                    !line.startsWith("internal ")) {

                    // Check if previous non-empty line is a KDoc closing */
                    val hasKDoc = i > 0 && lines.subList(0, i).any { it.trim().startsWith("/**") && it.trim().endsWith("*/") ||
                            (i > 1 && lines[i-1].trim() == "*/") }

                    if (!hasKDoc) {
                        val name = extractMemberName(line)
                        if (name != null && name.length > 2) {
                            findings.add(
                                Finding.UndocumentedMember(
                                    file = file.absolutePath,
                                    line = i + 1,
                                    memberName = name,
                                    memberType = when {
                                        line.startsWith("class ") -> "class"
                                        line.startsWith("object ") -> "object"
                                        line.startsWith("fun ") -> "function"
                                        else -> "member"
                                    }
                                )
                            )
                        }
                    }
                }
                i++
            }
        }
        return findings
    }

    private fun findUnusedPrivateMembers(dir: File): List<Finding> {
        val findings = mutableListOf<Finding>()
        val ktFiles = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("test") }
            .toList()

        // Collect all private function names and their usage counts
        val privateFunctions = mutableMapOf<String, Pair<String, Int>>() // name to (file, usageCount)

        for (file in ktFiles) {
            val content = file.readText()
            val lines = content.lines()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("private fun ")) {
                    val name = trimmed.removePrefix("private fun ")
                        .substringBefore("(")
                        .substringBefore("{")
                        .trim()
                    if (name.length > 2) {
                        // Count usages excluding the declaration itself
                        val usageCount = content.split(name).size - 2 // subtract declaration and itself
                        privateFunctions[name] = Pair(file.absolutePath, usageCount)
                    }
                }
            }
        }

        // Report private functions with 0 usages
        for ((name, pair) in privateFunctions) {
            if (pair.second <= 0) {
                findings.add(
                    Finding.UnusedPrivateMember(
                        file = pair.first,
                        memberName = name,
                        memberType = "function"
                    )
                )
            }
        }

        return findings
    }

    private fun checkNamingConventions(dir: File): List<Finding> {
        val findings = mutableListOf<Finding>()
        val ktFiles = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("test") }
            .toList()

        for (file in ktFiles) {
            // Check if filename matches the primary class/object name
            val fileName = file.nameWithoutExtension
            val content = file.readText()

            // Detect class declarations
            val classMatch = Regex("""(?:data |abstract |open |sealed |private )?class (\w+)""").find(content)
            if (classMatch != null) {
                val className = classMatch.groupValues[1]
                if (className != fileName && !fileName.endsWith("Ext") && !fileName.endsWith("Helper")) {
                    findings.add(
                        Finding.NamingConvention(
                            file = file.absolutePath,
                            message = "File '$fileName.kt' contains primary class '$className' — consider renaming to '$className.kt'"
                        )
                    )
                }
            }
        }
        return findings
    }

    private fun findOversizedFiles(dir: File): List<Finding> {
        val findings = mutableListOf<Finding>()
        val threshold = 500 // lines

        val ktFiles = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("test") }
            .toList()

        for (file in ktFiles) {
            val lineCount = file.readLines().size
            if (lineCount > threshold) {
                findings.add(
                    Finding.OversizedFile(
                        file = file.absolutePath,
                        lineCount = lineCount,
                        suggestion = "Consider splitting into smaller files or extracting inner classes"
                    )
                )
            }
        }
        return findings
    }

    private fun countSourceFiles(dir: File): Int {
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("test") }
            .count()
    }

    private fun extractMemberName(line: String): String? {
        val cleaned = line.replace(Regex("""(class|object|fun|val|var)\s+"""), "")
            .substringBefore("(")
            .substringBefore("{")
            .substringBefore(":")
            .trim()
        return cleaned.ifEmpty { null }
    }

    private fun addKdocToFile(projectDir: File, finding: Finding.UndocumentedMember) {
        val file = File(finding.file)
        if (!file.exists()) return

        val lines = file.readLines().toMutableList()
        val targetLine = finding.line - 1
        if (targetLine < 0 || targetLine >= lines.size) return

        val indent = lines[targetLine].takeWhile { it.isWhitespace() }
        val kdoc = "$indent/** ${finding.memberType.replaceFirstChar { it.uppercase() }}: ${finding.memberName} */"
        lines.add(targetLine, kdoc)
        file.writeText(lines.joinToString("\n"))
    }

    private fun buildSummary(findings: List<Finding>): String {
        if (findings.isEmpty()) return "No issues found. Code is well-organized."

        val undocumented = findings.filterIsInstance<Finding.UndocumentedMember>().size
        val unused = findings.filterIsInstance<Finding.UnusedPrivateMember>().size
        val naming = findings.filterIsInstance<Finding.NamingConvention>().size
        val oversized = findings.filterIsInstance<Finding.OversizedFile>().size

        return buildString {
            append("Found ${findings.size} issues: ")
            if (undocumented > 0) append("$undocumented undocumented members, ")
            if (unused > 0) append("$unused unused private members, ")
            if (naming > 0) append("$naming naming convention issues, ")
            if (oversized > 0) append("$oversized oversized files, ")
            // Remove trailing comma+space
            if (endsWith(", ")) delete(length - 2, length)
        }
    }

    // --- Data classes ---

    sealed class OrganizerState {
        data object Idle : OrganizerState()
        data object Analyzing : OrganizerState()
        data object Applying : OrganizerState()
        data class Complete(val report: OrganizerReport) : OrganizerState()
    }

    data class OrganizerReport(
        val totalFilesScanned: Int,
        val findings: List<Finding>,
        val summary: String
    )

    sealed class Finding {
        data class UndocumentedMember(
            val file: String,
            val line: Int,
            val memberName: String,
            val memberType: String
        ) : Finding()

        data class UnusedPrivateMember(
            val file: String,
            val memberName: String,
            val memberType: String
        ) : Finding()

        data class NamingConvention(
            val file: String,
            val message: String
        ) : Finding()

        data class OversizedFile(
            val file: String,
            val lineCount: Int,
            val suggestion: String
        ) : Finding()
    }

    data class ApplyResult(
        val applied: Int,
        val failed: Int
    )
}

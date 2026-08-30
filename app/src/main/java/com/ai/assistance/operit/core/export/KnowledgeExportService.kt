package com.ai.assistance.operit.core.export

import com.ai.nonoassistance.memory.GlobalMemoryStore
import com.ai.nonoassistance.memory.MemorySnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * KnowledgeExportService — export/import knowledge as .opk bundles.
 *
 * Per PROJECT_PLAN.md §8.5:
 * - .opk = zip of GlobalMemoryStore snapshot + Project.md + Progress.md + config
 * - Import back to another device and merge with existing memory (don't overwrite)
 */
class KnowledgeExportService(
    private val docsDir: File,
    private val memoryStore: GlobalMemoryStore
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Export all knowledge to an .opk file.
     *
     * @param outputPath Path to write the .opk file
     * @return Result with the output file path
     */
    suspend fun exportToOpk(outputPath: File): ExportResult {
        return try {
            val snapshot = memoryStore.exportSnapshot()
            val manifest = OpkManifest(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                memoryLessonCount = snapshot.lessons.size,
                includes = listOf("memory", "project_doc", "progress_doc")
            )

            ZipOutputStream(FileOutputStream(outputPath)).use { zip ->
                // Write manifest
                putZipEntry(zip, "manifest.json",
                    json.encodeToString(OpkManifest.serializer(), manifest))

                // Write memory snapshot
                putZipEntry(zip, "memory.json",
                    json.encodeToString(MemorySnapshot.serializer(), snapshot))

                // Write project docs if they exist
                val projectMd = File(docsDir, "Project.md")
                if (projectMd.exists()) {
                    putZipEntry(zip, "Project.md", projectMd.readText())
                }

                val progressMd = File(docsDir, "Progress.md")
                if (progressMd.exists()) {
                    putZipEntry(zip, "Progress.md", progressMd.readText())
                }

                val solutionsMd = File(docsDir, "SOLUTIONS.md")
                if (solutionsMd.exists()) {
                    putZipEntry(zip, "SOLUTIONS.md", solutionsMd.readText())
                }
            }

            ExportResult.Success(outputPath.absolutePath)
        } catch (e: Exception) {
            ExportResult.Failure("Export failed: ${e.message}")
        }
    }

    /**
     * Import knowledge from an .opk file.
     * Non-destructive: merges with existing data, doesn't overwrite.
     *
     * @param inputPath Path to the .opk file
     * @return Result with import summary
     */
    suspend fun importFromOpk(inputPath: File): ImportResult {
        return try {
            var lessonsImported = 0
            var docsImported = 0

            ZipInputStream(FileInputStream(inputPath)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "memory.json" -> {
                            val content = zip.bufferedReader().readText()
                            val snapshot = json.decodeFromString(MemorySnapshot.serializer(), content)
                            memoryStore.importSnapshot(snapshot)
                            lessonsImported = snapshot.lessons.size
                        }
                        "Project.md" -> {
                            val content = zip.bufferedReader().readText()
                            File(docsDir, "Project.md").writeText(content)
                            docsImported++
                        }
                        "Progress.md" -> {
                            val content = zip.bufferedReader().readText()
                            File(docsDir, "Progress.md").writeText(content)
                            docsImported++
                        }
                        "SOLUTIONS.md" -> {
                            val content = zip.bufferedReader().readText()
                            File(docsDir, "SOLUTIONS.md").writeText(content)
                            docsImported++
                        }
                    }
                    entry = zip.nextEntry
                }
            }

            ImportResult.Success(
                lessonsImported = lessonsImported,
                docsImported = docsImported
            )
        } catch (e: Exception) {
            ImportResult.Failure("Import failed: ${e.message}")
        }
    }

    private fun putZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }

    sealed class ExportResult {
        data class Success(val filePath: String) : ExportResult()
        data class Failure(val error: String) : ExportResult()
    }

    sealed class ImportResult {
        data class Success(val lessonsImported: Int, val docsImported: Int) : ImportResult()
        data class Failure(val error: String) : ImportResult()
    }
}

@Serializable
data class OpkManifest(
    val version: Int,
    val exportedAt: Long,
    val memoryLessonCount: Int,
    val includes: List<String>
)

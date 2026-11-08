package com.ai.assistance.operit.core.tools.skill

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class SkillManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SkillManager"

        @Volatile private var INSTANCE: SkillManager? = null

        fun getInstance(context: Context): SkillManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: SkillManager(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    private val availableSkills = mutableMapOf<String, SkillPackage>()
    private val skillLoadErrors = mutableMapOf<String, String>()

    private fun getSkillsRootDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, "Operit")
        val skillsDir = File(operitDir, "skills")
        if (!skillsDir.exists()) {
            skillsDir.mkdirs()
        }
        return skillsDir
    }

    fun getSkillsDirectoryPath(): String {
        return getSkillsRootDir().absolutePath
    }

    fun refreshAvailableSkills() {
        availableSkills.clear()
        skillLoadErrors.clear()

        val skillsDir = try {
            getSkillsRootDir()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting skills directory", e)
            skillLoadErrors[context.getString(R.string.skills)] =
                context.getString(R.string.skill_error_cannot_access_dir, e.message ?: "")
            return
        }

        if (!skillsDir.exists() || !skillsDir.isDirectory) {
            return
        }

        val children = skillsDir.listFiles() ?: emptyArray()
        for (child in children) {
            if (!child.isDirectory) continue

            val skillFile = File(child, "SKILL.md").let { primary ->
                if (primary.exists()) primary else File(child, "skill.md")
            }

            if (!skillFile.exists() || !skillFile.isFile) {
                skillLoadErrors[child.name] = context.getString(
                    R.string.skill_error_missing_skill_md,
                    child.absolutePath
                )
                continue
            }

            try {
                val (name, description) = parseSkillMetadata(skillFile)
                val skillName = name.ifBlank { child.name }
                val skillDesc = description.ifBlank { "" }

                if (availableSkills.containsKey(skillName)) {
                    val existingDirName = availableSkills[skillName]?.directory?.name ?: skillName
                    skillLoadErrors[child.name] = context.getString(
                        R.string.skill_error_duplicate_scanned_name,
                        skillName,
                        existingDirName
                    )
                    continue
                }

                availableSkills[skillName] = SkillPackage(
                    name = skillName,
                    description = skillDesc,
                    directory = child,
                    skillFile = skillFile
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error loading skill from ${skillFile.absolutePath}", e)
                skillLoadErrors[child.name] = context.getString(
                    R.string.skill_error_scan_failed,
                    e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    private fun parseSkillMetadata(skillFile: File): Pair<String, String> {
        val lines = skillFile.bufferedReader().use { it.readLines() }

        var name = ""
        var description = ""

        if (lines.isNotEmpty() && lines[0].trim() == "---") {
            val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIndex >= 0) {
                val frontmatter = lines.subList(1, endIndex + 1)
                frontmatter.forEach { lineRaw ->
                    val line = lineRaw.trim()
                    val idx = line.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = line.substring(0, idx).trim()
                    val value = unquote(line.substring(idx + 1).trim())
                    when (key.lowercase()) {
                        "name" -> if (name.isBlank()) name = value
                        "description" -> if (description.isBlank()) description = value
                    }
                }
            }
        }

        if (name.isBlank() || description.isBlank()) {
            lines.take(40).forEach { lineRaw ->
                val line = lineRaw.trim()
                val idx = line.indexOf(':')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).trim()
                val value = unquote(line.substring(idx + 1).trim())
                when (key.lowercase()) {
                    "name" -> if (name.isBlank()) name = value
                    "description" -> if (description.isBlank()) description = value
                }
            }
        }

        return Pair(name, description)
    }

    private fun unquote(valueRaw: String): String {
        var value = valueRaw
        if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
            if (value.length >= 2) value = value.substring(1, value.length - 1)
        }
        return value
    }

    fun getAvailableSkills(): Map<String, SkillPackage> {
        refreshAvailableSkills()
        return availableSkills.toMap()
    }

    fun getAvailableSkillsSnapshot(): Pair<Map<String, SkillPackage>, Map<String, String>> {
        refreshAvailableSkills()
        return availableSkills.toMap() to skillLoadErrors.toMap()
    }

    fun getSkillLoadErrors(): Map<String, String> {
        refreshAvailableSkills()
        return skillLoadErrors.toMap()
    }

    fun readSkillContent(skillName: String): String? {
        refreshAvailableSkills()
        val skill = availableSkills[skillName] ?: return null
        return try {
            skill.skillFile.readText()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read SKILL.md for $skillName", e)
            null
        }
    }

    fun deleteSkill(skillName: String): Boolean {
        refreshAvailableSkills()
        val skill = availableSkills[skillName] ?: return false
        return try {
            val ok = skill.directory.deleteRecursively()
            if (ok) {
                availableSkills.remove(skillName)
            }
            ok
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete skill $skillName", e)
            false
        }
    }

    fun getSkillSystemPrompt(skillName: String): String? {
        refreshAvailableSkills()
        val skill = availableSkills[skillName] ?: return null
        val content = try {
            skill.skillFile.readText()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read skill content: ${skill.skillFile.absolutePath}", e)
            ""
        }

        val sb = StringBuilder()
        sb.appendLine("Using package (Skill): ${skill.name}")
        sb.appendLine("Use Time: ${java.time.LocalDateTime.now()}")
        sb.appendLine("Execution policy:")
        sb.appendLine("Prioritize using the skill-provided instructions and bundled scripts, and complete tasks with terminal-related tools.")
        if (skill.description.isNotBlank()) {
            sb.appendLine("Description: ${skill.description}")
        }
        sb.appendLine("SKILL.md path: ${skill.skillFile.absolutePath}")
        sb.appendLine("Skill directory: ${skill.directory.absolutePath}")
        sb.appendLine("Directory structure:")
        sb.appendLine(buildDirectoryTreeText(skill.directory))
        sb.appendLine()
        sb.appendLine("SKILL.md:")
        sb.appendLine(content)

        return sb.toString()
    }

    private fun buildDirectoryTreeText(rootDir: File): String {
        val sb = StringBuilder()

        fun walk(dir: File, indent: String) {
            val children = dir.listFiles()
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()

            for (child in children) {
                sb.append(indent)
                sb.append("- ")
                sb.append(child.name)
                if (child.isDirectory) {
                    sb.appendLine("/")
                    walk(child, indent + "  ")
                } else {
                    sb.appendLine()
                }
            }
        }

        walk(rootDir, indent = "")

        if (sb.length == 0) return "(empty directory)"
        return sb.toString().trimEnd()
    }

    fun importSkillFromZip(zipFile: File): String {
        return importSkillFromZip(zipFile, null)
    }

    data class SkillImportResult(
        val message: String,
        val installedDir: File?
    )

    fun importSkillFromZip(zipFile: File, subDirPathInZip: String?): String {
        return importSkillFromZipDetailed(zipFile, subDirPathInZip).message
    }

    fun importSkillFromZipDetailed(zipFile: File, subDirPathInZip: String?): SkillImportResult {
        if (!zipFile.exists() || !zipFile.canRead()) {
            return SkillImportResult(context.getString(R.string.skill_error_cannot_read_file, zipFile.absolutePath), null)
        }
        if (!zipFile.name.endsWith(".zip", ignoreCase = true)) {
            return SkillImportResult(context.getString(R.string.skill_error_only_support_zip), null)
        }

        val skillsRoot = try {
            getSkillsRootDir()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting skills directory", e)
            return SkillImportResult(context.getString(R.string.skill_error_cannot_access_dir, e.message ?: ""), null)
        }

        val tmpDir = File(skillsRoot, ".import_tmp_${System.currentTimeMillis()}")
        if (!tmpDir.mkdirs()) {
            return SkillImportResult(context.getString(R.string.skill_error_create_tmp_dir_failed, tmpDir.absolutePath), null)
        }

        fun cleanupTmp() {
            try {
                tmpDir.deleteRecursively()
            } catch (_: Exception) {
            }
        }

        try {
            unzipToDirectory(zipFile, tmpDir)

            val normalizedSubDir = subDirPathInZip
                ?.trim()
                ?.trimStart('/')
                ?.trimEnd('/')
                ?.takeIf { it.isNotBlank() }

            val zipRootDir = tmpDir
                .listFiles()
                ?.filter { it.isDirectory }
                ?.singleOrNull()
                ?: tmpDir

            val searchRoot: File = if (normalizedSubDir == null) {
                tmpDir
            } else {
                val baseCanonical = zipRootDir.canonicalFile
                val resolved = File(zipRootDir, normalizedSubDir)
                val resolvedCanonical = resolved.canonicalFile
                if (!resolvedCanonical.path.startsWith(baseCanonical.path + File.separator)) {
                    cleanupTmp()
                    return SkillImportResult(context.getString(R.string.skill_error_import_invalid_path), null)
                }
                if (!resolvedCanonical.exists()) {
                    cleanupTmp()
                    return SkillImportResult(context.getString(R.string.skill_error_import_path_not_found, normalizedSubDir), null)
                }
                resolvedCanonical
            }

            val directSkillFile = if (searchRoot.isDirectory) {
                File(searchRoot, "SKILL.md").let { primary ->
                    if (primary.exists()) primary else File(searchRoot, "skill.md")
                }.takeIf { it.exists() && it.isFile }
            } else {
                null
            }

            val skillMdCandidates = if (directSkillFile != null) {
                listOf(directSkillFile)
            } else {
                searchRoot.walkTopDown()
                    .filter { it.isFile && (it.name.equals("SKILL.md", ignoreCase = true) || it.name.equals("skill.md", ignoreCase = true)) }
                    .take(10)
                    .toList()
            }

            if (skillMdCandidates.isEmpty()) {
                cleanupTmp()
                return SkillImportResult(if (normalizedSubDir == null) {
                    context.getString(R.string.skill_error_import_no_skill_md)
                } else {
                    context.getString(R.string.skill_error_import_no_skill_md_in_path)
                }, null)
            }

            val selectedSkillFile = skillMdCandidates.first()
            val selectedSkillDir = selectedSkillFile.parentFile ?: run {
                cleanupTmp()
                return SkillImportResult(context.getString(R.string.skill_error_import_skill_md_path_invalid), null)
            }

            val (metaName, metaDesc) = parseSkillMetadata(selectedSkillFile)
            val baseName = metaName.ifBlank {
                val isTmpRoot = try {
                    selectedSkillDir.canonicalFile == tmpDir.canonicalFile
                } catch (_: Exception) {
                    selectedSkillDir.absolutePath == tmpDir.absolutePath
                }
                if (isTmpRoot) {
                    zipFile.nameWithoutExtension
                } else {
                    selectedSkillDir.name.ifBlank { zipFile.nameWithoutExtension }
                }
            }
            val finalDir = File(skillsRoot, baseName.trim().ifBlank { "skill" })

            if (finalDir.exists()) {
                cleanupTmp()
                return SkillImportResult(context.getString(R.string.skill_error_import_duplicate_name, finalDir.name), null)
            }

            // Copy the detected skill directory to final location
            selectedSkillDir.copyRecursively(finalDir, overwrite = false)
            cleanupTmp()

            // refresh cache
            refreshAvailableSkills()

            val desc = metaDesc.ifBlank { "" }
            return SkillImportResult(if (desc.isNotBlank()) {
                context.getString(R.string.skill_imported_with_desc, finalDir.name, desc)
            } else {
                context.getString(R.string.skill_imported, finalDir.name)
            }, finalDir)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import skill from zip", e)
            cleanupTmp()
            return SkillImportResult(context.getString(R.string.skill_error_import_failed, e.message ?: ""), null)
        }
    }


    private fun unzipToDirectory(zipFile: File, destinationDir: File) {
        val destCanonical = destinationDir.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break

                val outFile = File(destinationDir, entry.name)
                val outCanonical = outFile.canonicalFile
                if (!outCanonical.path.startsWith(destCanonical.path + File.separator)) {
                    zis.closeEntry()
                    throw IllegalArgumentException("Zip entry is outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                    zis.closeEntry()
                    continue
                }

                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    while (true) {
                        val read = zis.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                    }
                }
                zis.closeEntry()
            }
        }
    }
}

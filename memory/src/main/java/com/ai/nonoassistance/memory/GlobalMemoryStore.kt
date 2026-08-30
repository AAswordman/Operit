package com.ai.nonoassistance.memory

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * GlobalMemoryStore — persistent cross-project memory for learning patterns.
 *
 * Per PROJECT_PLAN.md §4.3 + §14:
 * - Stores patterns of mistakes and fixes from orchestration
 * - Before starting new similar subtasks, system injects relevant lessons into context
 * - Separated from per-project memory
 * - Modeled after SOUL.md policy approach used with Hermes
 *
 * This implementation uses in-memory storage with JSON file persistence.
 * For production, consider Room DB or encrypted file store.
 */
class GlobalMemoryStore(
    private val storageDir: File? = null
) {

    private val lessons = mutableListOf<MemoryLesson>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        // Load from disk if storage dir is provided
        storageDir?.let { loadFromDisk(it) }
    }

    /**
     * Store a lesson learned from orchestration.
     * Called when Leader issues a "fix" command to a Worker.
     */
    suspend fun storeLesson(lesson: MemoryLesson) {
        val lessonWithId = if (lesson.id.isEmpty()) {
            lesson.copy(id = UUID.randomUUID().toString())
        } else {
            lesson
        }

        lessons.add(lessonWithId)

        // Persist to disk
        storageDir?.let { saveToDisk(it) }
    }

    /**
     * Query relevant lessons for a given task description.
     * Uses keyword-based similarity (simple but effective for v1).
     *
     * Future: implement vector similarity search with embeddings.
     */
    suspend fun queryRelevantLessons(taskDescription: String, limit: Int = 5): List<MemoryLesson> {
        val taskWords = tokenize(taskDescription)

        return lessons
            .map { lesson ->
                val score = calculateRelevanceScore(taskWords, lesson)
                lesson to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Get all lessons (for export or debugging).
     */
    fun getAllLessons(): List<MemoryLesson> = lessons.toList()

    /**
     * Get lessons filtered by tag.
     */
    fun getLessonsByTag(tag: String): List<MemoryLesson> {
        return lessons.filter { it.tags.contains(tag) }
    }

    /**
     * Get lessons filtered by provider.
     */
    fun getLessonsByProvider(providerId: String): List<MemoryLesson> {
        return lessons.filter { it.providerId == providerId }
    }

    /**
     * Export all memory as a snapshot for .opk bundle.
     */
    suspend fun exportSnapshot(): MemorySnapshot {
        return MemorySnapshot(
            lessons = lessons.toList(),
            exportedAt = System.currentTimeMillis()
        )
    }

    /**
     * Import and merge a snapshot (non-destructive merge — don't overwrite existing).
     * Deduplicates by taskPattern + mistakeDescription.
     */
    suspend fun importSnapshot(snapshot: MemorySnapshot) {
        val existingKeys = lessons.map { "${it.taskPattern}|${it.mistakeDescription}" }.toSet()

        for (lesson in snapshot.lessons) {
            val key = "${lesson.taskPattern}|${lesson.mistakeDescription}"
            if (key !in existingKeys) {
                lessons.add(lesson.copy(id = UUID.randomUUID().toString()))
            }
        }

        storageDir?.let { saveToDisk(it) }
    }

    /**
     * Remove a lesson by ID.
     */
    suspend fun removeLesson(lessonId: String) {
        lessons.removeAll { it.id == lessonId }
        storageDir?.let { saveToDisk(it) }
    }

    /**
     * Get the total number of stored lessons.
     */
    fun size(): Int = lessons.size

    /**
     * Clear all lessons.
     */
    suspend fun clear() {
        lessons.clear()
        storageDir?.let { saveToDisk(it) }
    }

    // --- Private methods ---

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()
    }

    private fun calculateRelevanceScore(taskWords: Set<String>, lesson: MemoryLesson): Double {
        val lessonWords = tokenize("${lesson.taskPattern} ${lesson.mistakeDescription} ${lesson.fixDescription}")
        val intersection = taskWords.intersect(lessonWords)
        val union = taskWords.union(lessonWords)

        if (union.isEmpty()) return 0.0

        // Jaccard similarity
        return intersection.size.toDouble() / union.size.toDouble()
    }

    private fun saveToDisk(dir: File) {
        try {
            dir.mkdirs()
            val file = File(dir, "global_memory.json")
            val snapshot = MemorySnapshot(
                lessons = lessons.toList(),
                exportedAt = System.currentTimeMillis()
            )
            file.writeText(json.encodeToString(MemorySnapshot.serializer(), snapshot))
        } catch (e: Exception) {
            // Silent fail for storage errors — don't crash the app
        }
    }

    private fun loadFromDisk(dir: File) {
        try {
            val file = File(dir, "global_memory.json")
            if (file.exists()) {
                val content = file.readText()
                val snapshot = json.decodeFromString(MemorySnapshot.serializer(), content)
                lessons.addAll(snapshot.lessons)
            }
        } catch (e: Exception) {
            // Silent fail — start with empty memory
        }
    }
}

@Serializable
data class MemoryLesson(
    val id: String = "",
    val taskPattern: String,
    val mistakeDescription: String,
    val fixDescription: String,
    val providerId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList()
)

@Serializable
data class MemorySnapshot(
    val lessons: List<MemoryLesson>,
    val exportedAt: Long
)

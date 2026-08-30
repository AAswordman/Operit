package com.ai.assistance.operit.core.autodoc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * SessionResumeManager — manages session checkpoints for resuming work.
 *
 * Per PROJECT_PLAN.md §21:
 * - Checkpoint every time a subtask completes
 * - On app restart, offer "Resume from where you left off"
 * - Include summary of what was in progress
 */
class SessionResumeManager(
    private val storageDir: File? = null
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val checkpoints = mutableListOf<SessionCheckpoint>()

    /**
     * Create a checkpoint after a subtask completes.
     */
    fun createCheckpoint(
        taskGraphState: String,
        contextSnapshotPointer: String,
        summary: String
    ): SessionCheckpoint {
        val checkpoint = SessionCheckpoint(
            id = "cp-${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            taskGraphState = taskGraphState,
            contextSnapshotPointer = contextSnapshotPointer,
            summary = summary
        )

        checkpoints.add(checkpoint)
        storageDir?.let { saveToDisk(it) }

        return checkpoint
    }

    /**
     * Get the most recent checkpoint (for resume offer).
     */
    fun getLatestCheckpoint(): SessionCheckpoint? {
        return checkpoints.maxByOrNull { it.timestamp }
    }

    /**
     * Get all checkpoints.
     */
    fun getAllCheckpoints(): List<SessionCheckpoint> = checkpoints.toList()

    /**
     * Check if there's a resumable session.
     */
    fun hasResumableSession(): Boolean {
        return checkpoints.isNotEmpty()
    }

    /**
     * Get a summary of the resumable session.
     */
    fun getResumeSummary(): String? {
        val latest = getLatestCheckpoint() ?: return null
        return "Session from ${java.time.Instant.ofEpochMilli(latest.timestamp)}:\n${latest.summary}"
    }

    /**
     * Clear all checkpoints (after successful resume or manual reset).
     */
    fun clearCheckpoints() {
        checkpoints.clear()
        storageDir?.let { saveToDisk(it) }
    }

    private fun saveToDisk(dir: File) {
        try {
            dir.mkdirs()
            val file = File(dir, "session_checkpoints.json")
            val data = CheckpointData(checkpoints = checkpoints)
            file.writeText(json.encodeToString(CheckpointData.serializer(), data))
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private fun loadFromDisk(dir: File) {
        try {
            val file = File(dir, "session_checkpoints.json")
            if (file.exists()) {
                val content = file.readText()
                val data = json.decodeFromString(CheckpointData.serializer(), content)
                checkpoints.addAll(data.checkpoints)
            }
        } catch (e: Exception) {
            // Silent fail
        }
    }

    init {
        storageDir?.let { loadFromDisk(it) }
    }
}

@Serializable
data class SessionCheckpoint(
    val id: String,
    val timestamp: Long,
    val taskGraphState: String,
    val contextSnapshotPointer: String,
    val summary: String
)

@Serializable
private data class CheckpointData(
    val checkpoints: List<SessionCheckpoint>
)

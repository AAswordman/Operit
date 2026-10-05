package com.ai.assistance.operit.data.storage

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocalModelDeleteOutcome {
    DELETED,
    IN_USE,
    FAILED,
}

class LocalModelUsageHandle internal constructor(
    private val canonicalPath: String,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            LocalModelRuntimeRegistry.release(canonicalPath)
        }
    }
}

object LocalModelRuntimeRegistry {
    private val lock = Any()
    private val referenceCounts = mutableMapOf<String, Int>()
    private val deletionHolds = mutableMapOf<String, Int>()
    private val _activePaths = MutableStateFlow<Set<String>>(emptySet())

    val activePaths: StateFlow<Set<String>> = _activePaths.asStateFlow()

    fun acquire(path: File): LocalModelUsageHandle? {
        val canonicalPath = runCatching { path.canonicalPath }.getOrDefault(path.absolutePath)
        synchronized(lock) {
            if (deletionHolds.keys.any { heldPath -> pathsOverlap(canonicalPath, heldPath) }) {
                return null
            }
            referenceCounts[canonicalPath] = (referenceCounts[canonicalPath] ?: 0) + 1
            publishActivePathsLocked()
        }
        return LocalModelUsageHandle(canonicalPath)
    }

    fun isInUse(path: File): Boolean {
        val canonicalPath = runCatching { path.canonicalPath }.getOrDefault(path.absolutePath)
        synchronized(lock) {
            return isBusyLocked(canonicalPath)
        }
    }

    fun deleteIfUnused(
        path: File,
        onProgress: ((deletedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): LocalModelDeleteOutcome {
        val canonicalFile =
            runCatching { path.canonicalFile }.getOrElse { return LocalModelDeleteOutcome.FAILED }
        val canonicalPath = canonicalFile.path
        synchronized(lock) {
            if (isBusyLocked(canonicalPath)) {
                return LocalModelDeleteOutcome.IN_USE
            }
            deletionHolds[canonicalPath] = (deletionHolds[canonicalPath] ?: 0) + 1
            publishActivePathsLocked()
        }
        try {
            if (!canonicalFile.exists()) {
                onProgress?.invoke(0L, 0L)
                return LocalModelDeleteOutcome.DELETED
            }
            val deleted =
                runCatching {
                    canonicalFile.deleteTreeWithProgress { deletedBytes, totalBytes ->
                        onProgress?.invoke(deletedBytes, totalBytes)
                    }
                }.getOrDefault(false)
            return if (deleted || !canonicalFile.exists()) {
                LocalModelDeleteOutcome.DELETED
            } else {
                LocalModelDeleteOutcome.FAILED
            }
        } finally {
            synchronized(lock) {
                val remaining = (deletionHolds[canonicalPath] ?: 1) - 1
                if (remaining > 0) {
                    deletionHolds[canonicalPath] = remaining
                } else {
                    deletionHolds.remove(canonicalPath)
                }
                publishActivePathsLocked()
            }
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            referenceCounts.clear()
            deletionHolds.clear()
            publishActivePathsLocked()
        }
    }

    internal fun release(canonicalPath: String) {
        synchronized(lock) {
            val remaining = (referenceCounts[canonicalPath] ?: return) - 1
            if (remaining > 0) {
                referenceCounts[canonicalPath] = remaining
            } else {
                referenceCounts.remove(canonicalPath)
            }
            publishActivePathsLocked()
        }
    }

    private fun publishActivePathsLocked() {
        _activePaths.value = (referenceCounts.keys + deletionHolds.keys).toSet()
    }

    private fun isBusyLocked(canonicalPath: String): Boolean =
        referenceCounts.keys.any { activePath -> pathsOverlap(canonicalPath, activePath) } ||
            deletionHolds.keys.any { heldPath -> pathsOverlap(canonicalPath, heldPath) }

    private fun pathsOverlap(first: String, second: String): Boolean =
        isAtOrBelow(first, second) || isAtOrBelow(second, first)

    private fun isAtOrBelow(path: String, parent: String): Boolean =
        path == parent || path.startsWith(parent.trimEnd(File.separatorChar) + File.separator)
}

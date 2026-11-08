package com.ai.assistance.operit.ui.features.conversation.workspace.process

import android.os.FileObserver
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File

data class WorkspaceFileChange(
    val relativePath: String,
    val kind: WorkspaceFileChangeKind
)

enum class WorkspaceFileChangeKind {
    CREATED,
    MODIFIED,
    DELETED,
    MOVED
}

class DepthLimitedFileObserver(
    private val rootDir: File,
    maxDepth: Int,
    private val shouldIgnore: (relativePath: String, isDirectory: Boolean) -> Boolean,
    private val onChange: (WorkspaceFileChange) -> Unit
) {
    companion object {
        private const val TAG = "DepthLimitedFileObserver"
        private const val EVENT_MASK =
            FileObserver.CREATE or
                FileObserver.DELETE or
                FileObserver.MODIFY or
                FileObserver.CLOSE_WRITE or
                FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO or
                FileObserver.DELETE_SELF or
                FileObserver.MOVE_SELF
    }

    private val maxDepth = maxDepth.coerceAtLeast(0)
    private val observers = LinkedHashMap<String, DirectoryObserver>()
    private val rootPath: String = rootDir.canonicalFile.absolutePath

    @Synchronized
    fun start() {
        if (observers.isNotEmpty()) return
        if (!rootDir.exists() || !rootDir.isDirectory) return
        watchDirectory(rootDir.canonicalFile)
        addExistingDirectories(rootDir.canonicalFile, 0)
    }

    @Synchronized
    fun stop() {
        observers.values.forEach { observer ->
            runCatching { observer.stopWatching() }
                .onFailure { AppLogger.w(TAG, "Failed to stop observer for ${observer.directory}", it) }
        }
        observers.clear()
    }

    @Synchronized
    private fun watchDirectory(directory: File) {
        val canonicalDir = directory.canonicalFile
        val relativePath = relativePathFor(canonicalDir)
        if (relativePath != null && shouldIgnore(relativePath, true)) return
        val depth = depthOf(relativePath.orEmpty())
        if (depth > maxDepth) return

        val key = canonicalDir.absolutePath
        if (observers.containsKey(key)) return

        val observer = DirectoryObserver(canonicalDir)
        observer.startWatching()
        observers[key] = observer
    }

    private fun addExistingDirectories(directory: File, depth: Int) {
        if (depth >= maxDepth) return
        directory.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.forEach { child ->
                val relativePath = relativePathFor(child.canonicalFile) ?: return@forEach
                if (shouldIgnore(relativePath, true)) return@forEach
                watchDirectory(child)
                addExistingDirectories(child, depth + 1)
            }
    }

    @Synchronized
    private fun removeDirectoryObservers(directory: File) {
        val canonicalPath = directory.canonicalFile.absolutePath
        val keysToRemove =
            observers.keys.filter { key ->
                key == canonicalPath || key.startsWith("$canonicalPath${File.separator}")
            }
        keysToRemove.forEach { key ->
            observers.remove(key)?.stopWatching()
        }
    }

    private fun relativePathFor(file: File): String? {
        val fullPath = file.canonicalFile.absolutePath
        if (fullPath == rootPath) return ""
        if (!fullPath.startsWith("$rootPath${File.separator}")) return null
        return fullPath.removePrefix("$rootPath${File.separator}").replace(File.separatorChar, '/')
    }

    private fun depthOf(relativePath: String): Int {
        val rel = relativePath.trim('/').replace('\\', '/')
        if (rel.isBlank()) return 0
        return rel.count { it == '/' } + 1
    }

    private fun kindFor(event: Int): WorkspaceFileChangeKind? {
        val normalized = event and FileObserver.ALL_EVENTS
        return when {
            normalized and (FileObserver.CREATE or FileObserver.MOVED_TO) != 0 ->
                WorkspaceFileChangeKind.CREATED
            normalized and (FileObserver.DELETE or FileObserver.MOVED_FROM) != 0 ->
                WorkspaceFileChangeKind.DELETED
            normalized and (FileObserver.MODIFY or FileObserver.CLOSE_WRITE) != 0 ->
                WorkspaceFileChangeKind.MODIFIED
            normalized and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0 ->
                WorkspaceFileChangeKind.MOVED
            else -> null
        }
    }

    private inner class DirectoryObserver(val directory: File) :
        FileObserver(directory.absolutePath, EVENT_MASK) {
        override fun onEvent(event: Int, path: String?) {
            val relativeChild = path?.takeIf { it.isNotBlank() } ?: return
            val changedFile = File(directory, relativeChild)
            val relativePath = relativePathFor(changedFile) ?: return
            val eventKind = kindFor(event) ?: return

            val isDirectoryNow = changedFile.exists() && changedFile.isDirectory
            val wasObservedDirectory =
                synchronized(this@DepthLimitedFileObserver) {
                    observers.containsKey(changedFile.canonicalFile.absolutePath)
                }
            if (shouldIgnore(relativePath, isDirectoryNow || wasObservedDirectory)) return

            if (eventKind == WorkspaceFileChangeKind.CREATED && isDirectoryNow) {
                synchronized(this@DepthLimitedFileObserver) {
                    watchDirectory(changedFile)
                    addExistingDirectories(changedFile, depthOf(relativePath))
                }
            }

            if (eventKind == WorkspaceFileChangeKind.DELETED || eventKind == WorkspaceFileChangeKind.MOVED) {
                synchronized(this@DepthLimitedFileObserver) {
                    removeDirectoryObservers(changedFile)
                }
            }

            onChange(
                WorkspaceFileChange(
                    relativePath = relativePath,
                    kind = eventKind
                )
            )
        }
    }
}

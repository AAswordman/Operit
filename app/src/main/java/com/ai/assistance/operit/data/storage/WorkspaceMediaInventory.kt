package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class WorkspaceMediaKind {
    INTERNAL_WORKSPACE,
    SHARED_WORKSPACE,
    MEDIA_IMAGES,
    MEDIA_AUDIO,
    MEDIA_VIDEO,
    MEDIA_TRANSCODED,
}

data class WorkspaceMediaEntry(
    val id: String,
    val kind: WorkspaceMediaKind,
    val name: String,
    val path: File,
    val bytes: Long,
    val fileCount: Long,
    val boundChatTitle: String?,
    val boundChatCount: Int,
    val lastModifiedMillis: Long,
    val locked: Boolean,
)

data class WorkspaceMediaSnapshot(
    val workspaces: List<WorkspaceMediaEntry>,
    val media: List<WorkspaceMediaEntry>,
    val totalBytes: Long,
    val scannedAtMillis: Long,
)

class WorkspaceMediaInventory(context: Context) {
    private val appContext = context.applicationContext
    private val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
    private val cleaner = SafeDirectoryCleaner()
    private val storageRepository = DataStorageRepository(appContext)

    suspend fun load(): WorkspaceMediaSnapshot = withContext(Dispatchers.IO) {
        val chats = chatHistoryManager.chatHistoriesFlow.first()
        val currentId = chatHistoryManager.currentChatIdFlow.first()
        val internalRoot = File(appContext.filesDir, "workspace")
        val sharedRoot = OperitPaths.workspaceDir()
        val boundByCanonical = mutableMapOf<String, MutableList<String>>()
        chats.forEach { chat ->
            val workspace = chat.workspace ?: return@forEach
            val canonical = File(workspace).canonicalOrAbsolute()
            boundByCanonical.getOrPut(canonical, ::mutableListOf) += chat.title
        }

        fun workspaceEntries(root: File, kind: WorkspaceMediaKind): List<WorkspaceMediaEntry> {
            if (!root.isDirectory) return emptyList()
            return root.listFiles().orEmpty()
                .filter { it.isDirectory && !it.isSymbolic() }
                .map { directory ->
                    val stats = directory.computeStorageStats()
                    val bound = boundByCanonical[directory.canonicalOrAbsolute()].orEmpty()
                    val locked = chats.any { chat ->
                        chat.id == currentId &&
                            chat.workspace != null &&
                            File(chat.workspace).canonicalOrAbsolute() == directory.canonicalOrAbsolute()
                    }
                    WorkspaceMediaEntry(
                        id = "${kind.name}:${directory.canonicalOrAbsolute()}",
                        kind = kind,
                        name = directory.name,
                        path = directory,
                        bytes = stats.bytes,
                        fileCount = stats.fileCount,
                        boundChatTitle = bound.firstOrNull(),
                        boundChatCount = bound.size,
                        lastModifiedMillis = stats.lastModifiedMillis,
                        locked = locked,
                    )
                }
        }

        val imagePool = File(appContext.filesDir, OperitPaths.IMAGE_POOL_DIR_NAME)
        val mediaPool = File(appContext.filesDir, OperitPaths.MEDIA_POOL_DIR_NAME)
        val transcoded = File(appContext.cacheDir, "media_pool_transcoded")
        val media = listOf(
            mediaEntry("images", WorkspaceMediaKind.MEDIA_IMAGES, imagePool) { file ->
                file.extension.lowercase() in IMAGE_EXTENSIONS
            },
            mediaEntry("audio", WorkspaceMediaKind.MEDIA_AUDIO, mediaPool) { file ->
                file.extension.lowercase() in AUDIO_EXTENSIONS
            },
            mediaEntry("video", WorkspaceMediaKind.MEDIA_VIDEO, mediaPool) { file ->
                file.extension.lowercase() in VIDEO_EXTENSIONS
            },
            WorkspaceMediaEntry(
                id = "media:transcoded",
                kind = WorkspaceMediaKind.MEDIA_TRANSCODED,
                name = transcoded.name,
                path = transcoded,
                bytes = transcoded.computeStorageStats().bytes,
                fileCount = transcoded.computeStorageStats().fileCount,
                boundChatTitle = null,
                boundChatCount = 0,
                lastModifiedMillis = transcoded.computeStorageStats().lastModifiedMillis,
                locked = false,
            ),
        )

        val workspaces =
            workspaceEntries(internalRoot, WorkspaceMediaKind.INTERNAL_WORKSPACE) +
                workspaceEntries(sharedRoot, WorkspaceMediaKind.SHARED_WORKSPACE)

        WorkspaceMediaSnapshot(
            workspaces = workspaces.sortedByDescending { it.bytes },
            media = media,
            totalBytes = workspaces.sumOf { it.bytes } + media.sumOf { it.bytes },
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(entries: List<WorkspaceMediaEntry>, onProgress: (String, Int, Int, Long) -> Unit): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        entries.forEachIndexed { index, entry ->
            onProgress(entry.name, index, entries.size, released)
            if (entry.locked) {
                failed++
                return@forEachIndexed
            }
            val result = when (entry.kind) {
                WorkspaceMediaKind.MEDIA_AUDIO -> deleteMatching(entry.path, AUDIO_EXTENSIONS)
                WorkspaceMediaKind.MEDIA_VIDEO -> deleteMatching(entry.path, VIDEO_EXTENSIONS)
                WorkspaceMediaKind.MEDIA_IMAGES,
                WorkspaceMediaKind.MEDIA_TRANSCODED,
                WorkspaceMediaKind.INTERNAL_WORKSPACE,
                WorkspaceMediaKind.SHARED_WORKSPACE ->
                    if (entry.path.isFile) {
                        val bytes = entry.path.length()
                        if (entry.path.delete()) {
                            RawDirectoryCleanupResult(bytes, 1L, 0)
                        } else {
                            RawDirectoryCleanupResult(0L, 0L, 1)
                        }
                    } else {
                        cleaner.cleanDirectory(entry.path)
                    }
            }
            if (result.failedEntryCount == 0 && (result.deletedFileCount > 0L || !entry.path.exists())) {
                deleted++
                released += maxOf(result.deletedBytes, entry.bytes)
            } else {
                failed++
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }

    private fun mediaEntry(
        id: String,
        kind: WorkspaceMediaKind,
        directory: File,
        matcher: (File) -> Boolean,
    ): WorkspaceMediaEntry {
        if (!directory.exists()) {
            return WorkspaceMediaEntry(
                id = "media:$id",
                kind = kind,
                name = directory.name,
                path = directory,
                bytes = 0L,
                fileCount = 0L,
                boundChatTitle = null,
                boundChatCount = 0,
                lastModifiedMillis = 0L,
                locked = false,
            )
        }
        var bytes = 0L
        var files = 0L
        var lastModified = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile && matcher(file)) {
                bytes += file.length()
                files += 1L
                lastModified = maxOf(lastModified, file.lastModified())
            }
        }
        return WorkspaceMediaEntry(
            id = "media:$id",
            kind = kind,
            name = directory.name,
            path = directory,
            bytes = bytes,
            fileCount = files,
            boundChatTitle = null,
            boundChatCount = 0,
            lastModifiedMillis = lastModified,
            locked = false,
        )
    }

    private fun deleteMatching(directory: File, extensions: Set<String>): RawDirectoryCleanupResult {
        if (!directory.exists()) return RawDirectoryCleanupResult(0L, 0L, 0)
        var deletedBytes = 0L
        var deletedFiles = 0L
        var failed = 0
        directory.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.lowercase() in extensions) {
                val bytes = file.length()
                if (file.delete()) {
                    deletedBytes += bytes
                    deletedFiles += 1L
                } else {
                    failed++
                }
            }
        }
        return RawDirectoryCleanupResult(deletedBytes, deletedFiles, failed)
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic")
        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "avi")
    }
}

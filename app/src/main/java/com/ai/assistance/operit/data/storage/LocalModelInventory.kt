package com.ai.assistance.operit.data.storage

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.data.mnn.DownloadState
import com.ai.assistance.operit.data.mnn.MnnDownloadSnapshot
import com.ai.assistance.operit.data.mnn.MnnModelDownloadManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LocalModelKind {
    MNN,
    LLAMA,
    SPEECH,
}

enum class LocalModelCompleteness {
    COMPLETE,
    INCOMPLETE,
    DOWNLOADING,
}

data class LocalModelEntry(
    val id: String,
    val displayName: String,
    val kind: LocalModelKind,
    val path: File,
    val bytes: Long,
    val completeness: LocalModelCompleteness,
    val downloadState: DownloadState? = null,
    val inUse: Boolean = false,
    val canDelete: Boolean = true,
)

class LocalModelInventory(
    context: Context,
    private val downloadManager: MnnModelDownloadManager = MnnModelDownloadManager.getInstance(context),
    private val storageRepository: DataStorageRepository = DataStorageRepository(context),
) {
    private val appContext = context.applicationContext

    suspend fun listModels(): List<LocalModelEntry> = withContext(Dispatchers.IO) {
        val activePaths = LocalModelRuntimeRegistry.activePaths.value
        val snapshots = downloadManager.downloadSnapshots.value.associateBy { canonicalPath(it.modelFolder) }
        val entries = mutableListOf<LocalModelEntry>()
        val seen = mutableSetOf<String>()

        listMnnModels(snapshots, activePaths).forEach { entry ->
            if (seen.add(canonicalPath(entry.path))) entries += entry
        }
        listLlamaModels(activePaths).forEach { entry ->
            if (seen.add(canonicalPath(entry.path))) entries += entry
        }
        listSpeechModels(activePaths).forEach { entry ->
            if (seen.add(canonicalPath(entry.path))) entries += entry
        }
        entries.sortedWith(
            compareBy<LocalModelEntry> { it.kind.ordinal }
                .thenByDescending { it.inUse }
                .thenByDescending { it.bytes }
                .thenBy { it.displayName.lowercase() },
        )
    }

    suspend fun delete(
        entry: LocalModelEntry,
        onProgress: ((deletedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): LocalModelDeleteOutcome = withContext(Dispatchers.IO) {
        val outcome = when (entry.kind) {
            LocalModelKind.MNN -> downloadManager.deleteModelFolder(entry.path, onProgress)
            LocalModelKind.LLAMA,
            LocalModelKind.SPEECH -> LocalModelRuntimeRegistry.deleteIfUnused(entry.path, onProgress)
        }
        if (outcome == LocalModelDeleteOutcome.DELETED) {
            storageRepository.invalidateCache()
        }
        outcome
    }

    private fun listMnnModels(
        snapshots: Map<String, MnnDownloadSnapshot>,
        activePaths: Set<String>,
    ): List<LocalModelEntry> {
        val mnnRoot = MnnModelDownloadManager.MODEL_DIR
        val folders = linkedMapOf<String, File>()
        mnnRoot.listFiles()?.forEach { child ->
            if (child.isDirectory) folders[canonicalPath(child)] = child
        }
        snapshots.values.forEach { snapshot ->
            val hasContent =
                snapshot.modelFolder.exists() ||
                    snapshot.state is DownloadState.Connecting ||
                    snapshot.state is DownloadState.Downloading ||
                    snapshot.state is DownloadState.Paused ||
                    snapshot.state is DownloadState.Failed
            if (hasContent) {
                folders.putIfAbsent(canonicalPath(snapshot.modelFolder), snapshot.modelFolder)
            }
        }

        return folders.values.map { folder ->
            val snapshot = snapshots[canonicalPath(folder)]
            val downloadState = snapshot?.state
            val completeness = mnnCompleteness(folder, downloadState)
            val inUse = isPathInUse(folder, activePaths)
            LocalModelEntry(
                id = "mnn:${folder.name}",
                displayName = snapshot?.modelName ?: folder.name,
                kind = LocalModelKind.MNN,
                path = folder,
                bytes = directorySize(folder),
                completeness = completeness,
                downloadState = downloadState,
                inUse = inUse,
                canDelete = !inUse,
            )
        }
    }

    private fun listLlamaModels(activePaths: Set<String>): List<LocalModelEntry> {
        val llamaRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Operit/models/llama",
        )
        return llamaRoot.listFiles { file ->
            file.isFile && file.name.lowercase().endsWith(".gguf")
        }.orEmpty().map { file ->
            val inUse = isPathInUse(file, activePaths)
            LocalModelEntry(
                id = "llama:${file.name}",
                displayName = file.name,
                kind = LocalModelKind.LLAMA,
                path = file,
                bytes = file.length(),
                completeness = LocalModelCompleteness.COMPLETE,
                inUse = inUse,
                canDelete = !inUse,
            )
        }
    }

    private fun listSpeechModels(activePaths: Set<String>): List<LocalModelEntry> {
        val speechRoot = OperitPaths.sherpaNcnnModelsDir(appContext)
        return speechRoot.listFiles { file -> file.isDirectory }.orEmpty().map { folder ->
            val inUse = isPathInUse(folder, activePaths)
            LocalModelEntry(
                id = "speech:${folder.name}",
                displayName = folder.name,
                kind = LocalModelKind.SPEECH,
                path = folder,
                bytes = directorySize(folder),
                completeness = speechCompleteness(folder),
                inUse = inUse,
                canDelete = !inUse,
            )
        }
    }

    private fun mnnCompleteness(folder: File, downloadState: DownloadState?): LocalModelCompleteness {
        if (downloadState is DownloadState.Connecting || downloadState is DownloadState.Downloading) {
            return LocalModelCompleteness.DOWNLOADING
        }
        val hasConfig = File(folder, "llm_config.json").isFile
        val hasModel = File(folder, "llm.mnn").isFile
        val hasTemp = folder.walkTopDown().any { it.isFile && it.name.endsWith(".tmp") }
        return if (hasConfig && hasModel && !hasTemp) {
            LocalModelCompleteness.COMPLETE
        } else {
            LocalModelCompleteness.INCOMPLETE
        }
    }

    private fun speechCompleteness(folder: File): LocalModelCompleteness {
        val required = listOf(
            "encoder_jit_trace-pnnx.ncnn.param",
            "encoder_jit_trace-pnnx.ncnn.bin",
            "decoder_jit_trace-pnnx.ncnn.param",
            "decoder_jit_trace-pnnx.ncnn.bin",
            "joiner_jit_trace-pnnx.ncnn.param",
            "joiner_jit_trace-pnnx.ncnn.bin",
            "tokens.txt",
        )
        return if (required.all { File(folder, it).isFile }) {
            LocalModelCompleteness.COMPLETE
        } else {
            LocalModelCompleteness.INCOMPLETE
        }
    }

    private fun isPathInUse(path: File, activePaths: Set<String>): Boolean {
        val canonical = canonicalPath(path)
        return activePaths.any { active ->
            canonical == active ||
                canonical.startsWith(active.trimEnd(File.separatorChar) + File.separator) ||
                active.startsWith(canonical.trimEnd(File.separatorChar) + File.separator)
        }
    }

    private fun directorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown().sumOf { file -> if (file.isFile) file.length() else 0L }
    }

    private fun canonicalPath(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
}

package com.ai.assistance.operit.ui.features.conversation.workspace.process

import android.content.Context
import com.ai.assistance.operit.ui.features.conversation.workspace.WatchConfig
import com.ai.assistance.operit.ui.features.conversation.workspace.WorkspaceConfigReader
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File

data class WorkspaceChangeSnapshot(
    val changes: List<WorkspaceFileChange>,
    val omittedCount: Int,
    val initialRootStructure: String? = null
)

class WorkspaceChangeTracker private constructor(private val context: Context) {
    companion object {
        private const val TAG = "WorkspaceChangeTracker"

        @Volatile
        private var instance: WorkspaceChangeTracker? = null

        fun getInstance(context: Context): WorkspaceChangeTracker {
            return instance ?: synchronized(this) {
                instance ?: WorkspaceChangeTracker(context.applicationContext).also { instance = it }
            }
        }
    }

    private data class OwnerBinding(
        val chatId: String,
        val workspacePath: String,
        val workspaceEnv: String?
    )

    private data class MonitorKey(
        val workspacePath: String,
        val workspaceEnv: String?
    )

    private data class TrackedChange(
        val relativePath: String,
        val kind: WorkspaceFileChangeKind
    )

    private class MonitorState(
        val observer: DepthLimitedFileObserver,
        val maxChangedFiles: Int
    ) {
        val changes = LinkedHashMap<String, TrackedChange>()
        val aiChangedPaths = LinkedHashSet<String>()
        var omittedCount: Int = 0
        var initialRootStructure: String? = null
    }

    private val ownerBindings = LinkedHashMap<String, OwnerBinding>()
    private val monitors = LinkedHashMap<MonitorKey, MonitorState>()

    @Synchronized
    fun updateOwner(
        ownerId: String,
        chatId: String?,
        workspacePath: String?,
        workspaceEnv: String?
    ) {
        if (chatId.isNullOrBlank() || workspacePath.isNullOrBlank() || !workspaceEnv.isNullOrBlank()) {
            ownerBindings.remove(ownerId)
            refreshMonitors()
            return
        }

        ownerBindings[ownerId] =
            OwnerBinding(
                chatId = chatId,
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv
            )
        refreshMonitors()
    }

    @Synchronized
    fun clearOwner(ownerId: String) {
        ownerBindings.remove(ownerId)
        refreshMonitors()
    }

    @Synchronized
    fun consumeChanges(
        chatId: String?,
        workspacePath: String?,
        workspaceEnv: String?
    ): WorkspaceChangeSnapshot {
        if (chatId.isNullOrBlank() || workspacePath.isNullOrBlank() || !workspaceEnv.isNullOrBlank()) {
            return WorkspaceChangeSnapshot(emptyList(), 0)
        }

        val key = MonitorKey(workspacePath, workspaceEnv)
        val state = monitors[key] ?: return WorkspaceChangeSnapshot(emptyList(), 0)
        val result =
            state.changes.values.map { change ->
                WorkspaceFileChange(
                    relativePath = change.relativePath,
                    kind = change.kind
                )
            }
        val omittedCount = state.omittedCount
        val initialRootStructure = state.initialRootStructure
        state.changes.clear()
        state.omittedCount = 0
        state.initialRootStructure = null
        state.aiChangedPaths.clear()
        return WorkspaceChangeSnapshot(result, omittedCount, initialRootStructure)
    }

    @Synchronized
    fun ignoreAiChanges(
        workspacePath: String?,
        workspaceEnv: String?,
        affectedPaths: List<String>
    ) {
        if (workspacePath.isNullOrBlank() || !workspaceEnv.isNullOrBlank() || affectedPaths.isEmpty()) return

        val key = MonitorKey(workspacePath, workspaceEnv)
        val state = monitors[key] ?: return
        affectedPaths
            .mapNotNull { path -> makeRelativePath(workspacePath, path) }
            .map { path -> path.trimStart('/').replace('\\', '/') }
            .filter { it.isNotBlank() }
            .forEach { relativePath ->
                state.aiChangedPaths.add(relativePath)
                state.changes.remove(relativePath)
            }
    }

    @Synchronized
    private fun refreshMonitors() {
        val requiredKeys =
            ownerBindings.values
                .map { MonitorKey(it.workspacePath, it.workspaceEnv) }
                .toSet()

        val obsoleteKeys = monitors.keys.filterNot { it in requiredKeys }
        obsoleteKeys.forEach { key ->
            monitors.remove(key)?.observer?.stop()
        }

        requiredKeys.forEach { key ->
            if (!monitors.containsKey(key)) {
                createMonitor(key)?.let { state ->
                    monitors[key] = state
                    state.observer.start()
                }
            }
        }
    }

    private fun createMonitor(key: MonitorKey): MonitorState? {
        val workspaceDir = File(key.workspacePath)
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) return null

        val config = WorkspaceConfigReader.readConfig(key.workspacePath).watch
        if (!config.enabled) return null

        val rules = buildIgnoreRules(workspaceDir, config)
        lateinit var state: MonitorState
        val observer =
            DepthLimitedFileObserver(
                rootDir = workspaceDir,
                maxDepth = config.maxDepth,
                shouldIgnore = { relativePath, isDirectory ->
                    val rel = relativePath.trimStart('/').replace('\\', '/')
                    if (rel.isBlank()) {
                        false
                    } else {
                        GitIgnoreFilter.shouldIgnore(
                            relativePath = rel,
                            fileName = rel.substringAfterLast('/'),
                            isDirectory = isDirectory,
                            rules = rules
                        )
                    }
                },
                onChange = { change ->
                    synchronized(this@WorkspaceChangeTracker) {
                        recordChange(state, change)
                    }
                }
            )
        state = MonitorState(
            observer = observer,
            maxChangedFiles = config.maxChangedFiles.coerceAtLeast(1)
        ).also { monitorState ->
            monitorState.initialRootStructure = buildRootLevelStructure(workspaceDir, rules)
        }
        return state
    }

    private fun buildIgnoreRules(workspaceDir: File, config: WatchConfig): List<String> {
        val rules = LinkedHashSet<String>()
        rules.addAll(GitIgnoreFilter.loadRules(workspaceDir))
        config.exclude
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { rule -> rules.add(rule) }
        return rules.toList()
    }

    private fun recordChange(state: MonitorState, change: WorkspaceFileChange) {
        val relativePath = change.relativePath.trimStart('/').replace('\\', '/')
        if (relativePath.isBlank()) return
        if (isAiChangedPath(state, relativePath)) {
            AppLogger.d(TAG, "Workspace AI change ignored: ${change.kind} $relativePath")
            return
        }

        if (!state.changes.containsKey(relativePath) && state.changes.size >= state.maxChangedFiles) {
            state.omittedCount += 1
            return
        }

        state.changes[relativePath] =
            TrackedChange(
                relativePath = relativePath,
                kind = change.kind
        )
        AppLogger.d(TAG, "Workspace change recorded: ${change.kind} $relativePath")
    }

    private fun isAiChangedPath(state: MonitorState, relativePath: String): Boolean {
        return state.aiChangedPaths.contains(relativePath)
    }

    private fun makeRelativePath(workspacePath: String, fullPath: String): String? {
        val normalizedRoot = File(workspacePath).canonicalFile.absolutePath
        val normalizedFile = File(fullPath).canonicalFile.absolutePath
        if (normalizedFile == normalizedRoot) return ""
        val prefix = "$normalizedRoot${File.separator}"
        if (!normalizedFile.startsWith(prefix)) return null
        return normalizedFile.removePrefix(prefix).replace(File.separatorChar, '/')
    }

    private fun buildRootLevelStructure(workspaceDir: File, rules: List<String>): String {
        val rootItems =
            workspaceDir.listFiles()
                ?.filter { file -> !GitIgnoreFilter.shouldIgnore(file, workspaceDir, rules) }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?: emptyList()

        if (rootItems.isEmpty()) return "工作区为空"

        return buildString {
            rootItems.forEachIndexed { index, file ->
                val prefix = if (index == rootItems.size - 1) "└── " else "├── "
                val icon = if (file.isDirectory) "📁" else "📄"
                append(prefix)
                append(icon)
                append(' ')
                append(file.name)
                if (file.isFile && file.length() > 0) {
                    append(" (")
                    append(formatFileSize(file.length()))
                    append(')')
                }
                appendLine()
            }
        }.trimEnd()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }
}

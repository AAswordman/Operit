package com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class WorkspacePropertiesTarget(val path: String, val environment: String?) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val directory: String get() = path.trimEnd('/').substringBeforeLast('/', "").let {
        if (it.isEmpty()) "/" else "$it/"
    }
}

internal data class PropertyNode(
    val id: String,
    val directory: Boolean,
    val symbolicLink: Boolean,
    val size: Long?,
    val modified: Long?,
)

internal data class WorkspaceProperties(
    val node: PropertyNode,
    val canModifyTime: Boolean,
)

internal data class WorkspaceDirectoryTotals(
    val bytes: Long?,
    val files: Long,
    val directories: Long,
    val unreadable: Long,
    val unknownSizes: Long,
)

internal data class WorkspaceTimeChangeResult(val changed: Long, val failed: Long)

internal interface WorkspacePropertySource {
    val root: String
    val canModifyTime: Boolean
    suspend fun stat(id: String): PropertyNode
    suspend fun children(node: PropertyNode): List<PropertyNode>
    suspend fun setModified(node: PropertyNode, millis: Long)
}

/** 一次面板绑定一个文件来源，统计、写时间与错误处理均在 IO 线程，不追随目录链接。 */
internal class WorkspacePropertiesRepository private constructor(private val source: WorkspacePropertySource) {
    companion object {
        suspend fun open(context: Context, target: WorkspacePropertiesTarget): WorkspacePropertiesRepository = withContext(Dispatchers.IO) {
            val source = when {
                target.environment.isNullOrBlank() || target.environment.equals("android", ignoreCase = true) ->
                    LocalWorkspacePropertySource(context, File(target.path).toPath())
                target.environment?.startsWith("repo:", ignoreCase = true) == true ->
                    SafWorkspacePropertySource.open(context, target)
                target.environment.equals("linux", ignoreCase = true) -> {
                    val remote = SSHFileConnectionManager.getInstance(context).getFileSystemProvider()
                    if (remote != null) RemoteWorkspacePropertySource(remote, target.path)
                    else {
                        val path = when {
                            target.path == "~" -> "/root"
                            target.path.startsWith("~/") -> "/root/" + target.path.removePrefix("~/")
                            else -> target.path
                        }
                        val mapped = PRootMountMapping.mapLinuxPathToHostPath(
                            path, File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu"),
                            context.filesDir.absolutePath, context.applicationInfo.dataDir, context.packageName,
                            context.getSharedPreferences("terminal_settings", Context.MODE_PRIVATE).getBoolean("chroot_enabled", false),
                        )
                        LocalWorkspacePropertySource(context, File(mapped).toPath())
                    }
                }
                else -> error(context.getString(R.string.workspace_properties_source_unsupported))
            }
            WorkspacePropertiesRepository(source)
        }
    }

    suspend fun read(): WorkspaceProperties = withContext(Dispatchers.IO) {
        val node = source.stat(source.root)
        WorkspaceProperties(node, source.canModifyTime && !node.symbolicLink)
    }

    suspend fun totals(): WorkspaceDirectoryTotals = withContext(Dispatchers.IO) {
        val root = source.stat(source.root)
        var bytes = 0L
        var files = 0L
        var directories = 0L
        var unreadable = 0L
        var unknownSizes = 0L
        val pending = ArrayDeque<PropertyNode>()
        pending.add(root)
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val node = pending.removeLast()
            if (!seen.add(node.id)) continue
            if (node.directory && !node.symbolicLink) {
                if (node.id != root.id) directories++
                try {
                    pending.addAll(source.children(node))
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    AppLogger.e("WorkspaceProperties", "统计目录失败：${node.id}", failure)
                    unreadable++
                }
            } else {
                files++
                val size = node.size
                if (size == null) unknownSizes++ else bytes = Math.addExact(bytes, size)
            }
        }
        WorkspaceDirectoryTotals(bytes.takeIf { unreadable == 0L && unknownSizes == 0L }, files, directories, unreadable, unknownSizes)
    }

    suspend fun changeModified(millis: Long, recursive: Boolean): WorkspaceTimeChangeResult = withContext(Dispatchers.IO) {
        check(source.canModifyTime)
        var changed = 0L
        var failed = 0L
        val pending = ArrayDeque<Pair<PropertyNode, Boolean>>()
        val seen = mutableSetOf<String>()
        pending.add(source.stat(source.root) to false)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (node, afterChildren) = pending.removeLast()
            if (node.symbolicLink) continue
            if (!afterChildren) {
                if (!seen.add(node.id)) continue
                if (recursive && node.directory) {
                    // 目录最后更新，避免对子项的操作改变已经设置好的父目录时间。
                    pending.add(node to true)
                    try {
                        source.children(node).forEach { pending.add(it to false) }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (failure: Exception) {
                        AppLogger.e("WorkspaceProperties", "读取待更新时间的子项失败：${node.id}", failure)
                        failed++
                    }
                    continue
                }
            }
            try {
                source.setModified(node, millis)
                changed++
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                AppLogger.e("WorkspaceProperties", "修改时间失败：${node.id}", failure)
                failed++
            }
        }
        WorkspaceTimeChangeResult(changed, failed)
    }
}

private class LocalWorkspacePropertySource(private val context: Context, path: Path) : WorkspacePropertySource {
    override val root: String = path.toAbsolutePath().normalize().toString()
    override val canModifyTime = true
    private val access = WorkspacePropertyAccess(context)

    override suspend fun stat(id: String): PropertyNode {
        currentCoroutineContext().ensureActive()
        access.requireReadable(File(id))
        val attributes = Files.readAttributes(File(id).toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        return PropertyNode(id, attributes.isDirectory, attributes.isSymbolicLink, attributes.size(), attributes.lastModifiedTime().toMillis())
    }

    override suspend fun children(node: PropertyNode): List<PropertyNode> {
        access.requireReadable(File(node.id))
        val result = mutableListOf<PropertyNode>()
        Files.newDirectoryStream(File(node.id).toPath()).use { entries ->
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                result += stat(entry.toString())
            }
        }
        return result
    }

    override suspend fun setModified(node: PropertyNode, millis: Long) {
        currentCoroutineContext().ensureActive()
        val path = File(node.id).toPath()
        check(!Files.isSymbolicLink(path))
        Files.getFileAttributeView(path, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
            .setTimes(FileTime.fromMillis(millis), null, null)
    }
}
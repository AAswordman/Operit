package com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.ai.assistance.operit.R
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** 直接访问共享存储前先检查授权，应用私有目录和 SAF 不受共享存储权限控制。 */
internal class WorkspacePropertyAccess(private val context: Context) {
    private val privateRoots = listOfNotNull(context.filesDir.parentFile, context.noBackupFilesDir) +
            context.getExternalFilesDirs(null).filterNotNull() +
            context.externalCacheDirs.filterNotNull() + context.externalMediaDirs.filterNotNull()
    private val privatePaths = privateRoots.map { it.canonicalFile.toPath() }
    private val sharedRoot = Environment.getExternalStorageDirectory().canonicalFile.toPath()

    fun requireReadable(file: File) {
        val path = file.absoluteFile.toPath().normalize()
        val canonical = if (Files.isSymbolicLink(path)) path else file.canonicalFile.toPath()
        val privatePath = privatePaths.any { canonical.startsWith(it) }
        val sharedPath = canonical.startsWith(File("/storage").toPath()) || canonical.startsWith(sharedRoot)
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (!privatePath && sharedPath && !storageGranted) {
            throw SecurityException(context.getString(R.string.file_manager_permission_denied))
        }
        // 符号链接只读取自身元数据，禁止通过权限检查顺带跟随目标。
        if (!Files.isSymbolicLink(file.toPath()) &&
            Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isReadable(file.toPath())) {
            throw SecurityException(context.getString(R.string.file_manager_permission_denied))
        }
    }
}

package com.ai.assistance.operit.util

import android.content.Context
import java.io.File

/**
 * 在 Android 宿主路径与内置 Ubuntu 路径之间转换。
 */
object PathMapper {
    private const val UBUNTU_ROOT_RELATIVE_PATH = "usr/var/lib/proot-distro/installed-rootfs/ubuntu"

    private fun ubuntuRoot(context: Context): File = File(context.filesDir, UBUNTU_ROOT_RELATIVE_PATH)

    /** 将 Linux 路径转换为内置 Ubuntu rootfs 中的 Android 宿主路径。 */
    fun mapLinuxPath(context: Context, linuxPath: String): String =
        mapLinuxPath(ubuntuRoot(context), linuxPath)

    internal fun mapLinuxPath(ubuntuRoot: File, linuxPath: String): String {
        val relativePath = linuxPath.trimStart('/')
        return if (relativePath.isEmpty()) {
            ubuntuRoot.absolutePath
        } else {
            File(ubuntuRoot, relativePath).absolutePath
        }
    }

    /** 将 rootfs 内的 Android 宿主路径还原为 Linux 路径；路径不在 rootfs 内时返回 null。 */
    fun unmapLinuxPath(context: Context, androidPath: String): String? =
        unmapLinuxPath(ubuntuRoot(context), androidPath)

    internal fun unmapLinuxPath(ubuntuRoot: File, androidPath: String): String? {
        val canonicalRoot = ubuntuRoot.canonicalFile
        val canonicalPath = File(androidPath).canonicalFile
        if (canonicalPath == canonicalRoot) return "/"

        val rootPrefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!canonicalPath.path.startsWith(rootPrefix)) return null

        val relativePath = canonicalPath.path.removePrefix(rootPrefix)
        return "/" + relativePath.replace(File.separatorChar, '/')
    }

    /** 判断环境参数是否指向内置 Linux 终端。 */
    fun isLinuxEnvironment(environment: String?): Boolean {
        return environment?.lowercase() == "linux"
    }

    /** 根据 environment 参数将 Linux 路径转换为 Android 宿主路径。 */
    fun resolvePath(context: Context, path: String, environment: String?): String {
        return if (isLinuxEnvironment(environment)) {
            mapLinuxPath(context, path)
        } else {
            path
        }
    }
}


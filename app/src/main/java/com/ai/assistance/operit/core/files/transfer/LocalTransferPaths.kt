package com.ai.assistance.operit.core.files.transfer

import android.content.Context
import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import java.io.File

/** 与终端共用软挂载映射，不能把 /sdcard 简单拼接到 Ubuntu rootfs 下。 */
internal object LocalTransferPaths {
    fun resolve(context: Context, path: String, environment: String): File {
        if (environment.equals("android", ignoreCase = true)) return File(path)
        require(environment.equals("linux", ignoreCase = true)) { "Not a local file environment: $environment" }
        val expanded = when {
            path == "~" -> "/root"
            path.startsWith("~/") -> "/root/" + path.substring(2)
            else -> path
        }
        return File(PRootMountMapping.mapLinuxPathToHostPath(
            linuxPath = expanded,
            ubuntuRoot = File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu"),
            homeDir = context.filesDir.absolutePath,
            appDataDir = context.applicationInfo.dataDir,
            packageName = context.packageName,
            chrootEnabled = context.getSharedPreferences("terminal_settings", Context.MODE_PRIVATE)
                .getBoolean("chroot_enabled", false),
        ))
    }
}
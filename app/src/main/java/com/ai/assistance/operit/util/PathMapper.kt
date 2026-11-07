package com.ai.assistance.operit.util

import android.content.Context
import java.io.File

/**
 * 路径映射工具类
 * 用于在Android环境和Linux（Ubuntu终端）环境之间转换文件路径
 */
object PathMapper {
    
    /**
     * 将Linux路径转换为Android文件系统中的实际路径
     * 
     * @param context Android上下文
     * @param linuxPath Linux格式的路径，例如 "/home/user/test.txt" 或 "/etc/hosts"
     * @return Android文件系统中的实际绝对路径
     */
    fun mapLinuxPath(context: Context, linuxPath: String): String {
        // Ubuntu根目录位于 {filesDir}/usr/var/lib/proot-distro/installed-rootfs/ubuntu
        val ubuntuRoot = File(
            context.filesDir,
            "usr/var/lib/proot-distro/installed-rootfs/ubuntu"
        )

        // 移除路径开头的/，然后拼接到Ubuntu根目录
        val relativePath = linuxPath.trimStart('/')

        // 如果输入路径为空或只有/，返回Ubuntu根目录
        if (relativePath.isEmpty()) {
            return ubuntuRoot.absolutePath
        }

        return File(ubuntuRoot, relativePath).absolutePath
    }

    /**
     * 修改意图：反向映射——把宿主文件系统上的 proot rootfs 路径还原为 Linux 逻辑路径。
     * 用于 grep 等在宿主路径上执行、但结果需要以 linux 路径回传给模型的场景。
     * 不在 rootfs 内的路径返回 null（调用方保留原路径）。
     */
    fun mapAndroidPathToLinux(context: Context, androidPath: String): String? {
        val ubuntuRoot =
            File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")
                .canonicalFile
        val target =
            runCatching { File(androidPath).canonicalFile }.getOrNull() ?: return null
        val rootPath = ubuntuRoot.absolutePath.trimEnd('/')
        val targetPath = target.absolutePath
        if (targetPath == rootPath) {
            return "/"
        }
        if (targetPath.startsWith("$rootPath/")) {
            return "/" + targetPath.removePrefix("$rootPath/")
        }
        return null
    }
    
    /**
     * 判断是否为Linux环境
     * 
     * @param environment 环境参数值
     * @return 如果是Linux环境返回true，否则返回false
     */
    fun isLinuxEnvironment(environment: String?): Boolean {
        return environment?.lowercase() == "linux"
    }
    
    /**
     * 根据environment参数转换路径
     * 
     * @param context Android上下文
     * @param path 原始路径
     * @param environment 环境参数（"android" 或 "linux"）
     * @return 转换后的实际路径
     */
    fun resolvePath(context: Context, path: String, environment: String?): String {
        return if (isLinuxEnvironment(environment)) {
            mapLinuxPath(context, path)
        } else {
            path
        }
    }
}


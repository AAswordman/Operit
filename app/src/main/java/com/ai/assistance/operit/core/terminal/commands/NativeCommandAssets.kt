package com.ai.assistance.operit.core.terminal.commands

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

internal object NativeCommandAssets {
    fun sha256(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Synchronized
    fun install(destination: File, expectedSha256: String, source: () -> InputStream): File {
        require(expectedSha256.matches(Regex("[0-9a-f]{64}"))) { "终端命令的校验值无效" }
        if (destination.isFile && !Files.isSymbolicLink(destination.toPath()) && sha256(destination) == expectedSha256) {
            return destination
        }
        val parent = checkNotNull(destination.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "无法创建终端命令资源目录" }
        val temporary = File(parent, ".${destination.name}.${UUID.randomUUID()}.tmp")
        try {
            source().use { input ->
                temporary.outputStream().use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            check(sha256(temporary) == expectedSha256) { "终端命令资源校验失败" }
            // 保留正在使用的文件，只有新资源完整可用时才原子替换。
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return destination
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }
}

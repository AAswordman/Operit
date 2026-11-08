package com.ai.assistance.operit.data.resources

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class ResourceFileSpec(val name: String, val size: Long, val sha256: String) {
    init {
        require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name)
        require(size > 0 && sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

internal object ResourceArchive {
    fun verified(file: File, spec: ResourceFileSpec, checkpoint: () -> Unit = {}): Boolean {
        if (!file.isFile || file.length() != spec.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkpoint()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex() == spec.sha256
    }

    fun writeVerified(
        input: InputStream,
        target: File,
        spec: ResourceFileSpec,
        checkpoint: () -> Unit = {},
        onProgress: (Long) -> Unit = {},
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkpoint()
                val count = input.read(buffer)
                if (count < 0) break
                if (count.toLong() > spec.size - written) throw IOException("资源超过固定大小：${spec.name}")
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                written += count
                onProgress(written)
            }
        }
        if (written != spec.size || digest.digest().toHex() != spec.sha256) {
            throw IOException("资源大小或 SHA-256 不匹配：${spec.name}")
        }
    }

    fun install(source: File, target: File) {
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun importZip(
        input: InputStream,
        specs: List<ResourceFileSpec>,
        staging: File,
        destination: File,
        checkpoint: () -> Unit = {},
        onProgress: (Long) -> Unit = {},
    ) {
        check(staging.mkdir())
        try {
            val expected = specs.associateBy { it.name }
            val seen = mutableSetOf<String>()
            var completed = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    checkpoint()
                    val entry = zip.nextEntry ?: break
                    val spec = expected[entry.name] ?: throw IOException("资源包包含未知文件：${entry.name}")
                    if (entry.isDirectory || !seen.add(entry.name)) throw IOException("资源包包含重复或无效文件：${entry.name}")
                    writeVerified(zip, File(staging, spec.name), spec, checkpoint) { onProgress(completed + it) }
                    completed += spec.size
                    zip.closeEntry()
                }
            }
            if (seen != expected.keys) throw IOException("资源包缺少必要文件")
            // 全组校验通过后再替换，损坏的 ZIP 不会覆盖已安装的模型。
            checkpoint()
            specs.forEach { install(File(staging, it.name), File(destination, it.name)) }
        } finally {
            staging.deleteRecursively()
        }
    }

    fun exportZip(
        output: OutputStream,
        specs: List<ResourceFileSpec>,
        directory: File,
        checkpoint: () -> Unit = {},
        onProgress: (Long) -> Unit = {},
    ) {
        var completed = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            // 模型已是二进制压缩格式，避免再次压缩拖慢手机上的备份。
            zip.setLevel(0)
            specs.forEach { spec ->
                checkpoint()
                zip.putNextEntry(ZipEntry(spec.name))
                File(directory, spec.name).inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkpoint()
                        val count = input.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                        completed += count
                        onProgress(completed)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

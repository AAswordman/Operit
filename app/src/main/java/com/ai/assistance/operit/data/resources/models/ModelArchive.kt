package com.ai.assistance.operit.data.resources.models

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** 模型和资源包共用有界解包，完整校验后才进入正式目录。 */
internal object ModelArchive {
    const val MAX_EXPANDED_BYTES = 8L * 1024 * 1024 * 1024

    fun digest(file: File, checkpoint: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                checkpoint()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun verified(root: File, spec: ModelFile, checkpoint: () -> Unit = {}): Boolean {
        val file = root.containedFile(spec.path)
        return file.isFile && file.length() == spec.bytes && digest(file, checkpoint) == spec.sha256
    }

    fun write(input: InputStream, destination: File, spec: ModelFile, checkpoint: () -> Unit, progress: (Long) -> Unit = {}) {
        destination.parentFile!!.mkdirs()
        var bytes = 0L
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                checkpoint()
                val count = input.read(buffer)
                if (count < 0) break
                require(count <= spec.bytes - bytes) { "文件超过清单大小：${spec.path}" }
                output.write(buffer, 0, count)
                bytes += count
                progress(bytes)
            }
        }
        require(bytes == spec.bytes && digest(destination, checkpoint) == spec.sha256) { "文件大小或 SHA-256 不匹配：${spec.path}" }
    }

    fun unpack(input: InputStream, name: String, destination: File, checkpoint: () -> Unit = {}, progress: (Long) -> Unit = {}) {
        check(destination.isDirectory || destination.mkdirs())
        var expanded = 0L
        var entries = 0
        val seen = mutableSetOf<String>()
        fun extract(rawName: String, directory: Boolean, stream: InputStream) {
            checkpoint()
            var relative = rawName.trimEnd('/')
            while (relative.startsWith("./")) relative = relative.removePrefix("./")
            if (directory && (relative == "." || relative.isEmpty())) return
            val target = destination.containedFile(relative)
            require(++entries <= 100_000) { "资源包包含过多条目" }
            if (directory) { check(target.isDirectory || target.mkdirs()); return }
            require(seen.add(relative)) { "资源包包含重复文件：$relative" }
            target.parentFile!!.mkdirs()
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    checkpoint()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(count <= MAX_EXPANDED_BYTES - expanded) { "资源包展开体积过大" }
                    if (destination.usableSpace < count + 1024 * 1024) throw IOException("设备空间不足")
                    output.write(buffer, 0, count)
                    expanded += count
                    progress(expanded)
                }
            }
        }
        when {
            name.endsWith(".zip", true) -> ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    extract(entry.name, entry.isDirectory, zip)
                    zip.closeEntry()
                }
            }
            name.endsWith(".tar.gz", true) || name.endsWith(".tgz", true) -> TarArchiveInputStream(GZIPInputStream(input.buffered())).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    requireSupportedTarEntry(entry)
                    extract(entry.name, entry.isDirectory, tar)
                }
            }
            name.endsWith(".tar.bz2", true) -> TarArchiveInputStream(BZip2CompressorInputStream(input.buffered())).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    requireSupportedTarEntry(entry)
                    extract(entry.name, entry.isDirectory, tar)
                }
            }
            else -> error("支持 ZIP、tar.gz 和 tar.bz2 模型包")
        }
        require(seen.isNotEmpty()) { "资源包没有文件" }
    }

    private fun requireSupportedTarEntry(entry: TarArchiveEntry) {
        // isFile 会按路径后缀判断，必须用 tar 类型标志区分普通文件、目录与链接。
        require(
            entry.linkFlag == TarConstants.LF_OLDNORM ||
                entry.linkFlag == TarConstants.LF_NORMAL ||
                entry.linkFlag == TarConstants.LF_DIR
        ) { "资源包不支持链接或设备条目" }
    }

    fun export(root: File, output: OutputStream, checkpoint: () -> Unit, progress: (Long, Long) -> Unit, extraRoots: Map<String, File> = emptyMap()) {
        val roots = listOf("" to root) + extraRoots.map { (prefix, directory) -> validateRelativePath(prefix); prefix to directory }
        val files = roots.flatMap { (prefix, directory) ->
            directory.walkTopDown().filter(File::isFile).map { file ->
                (if (prefix.isEmpty()) "" else "$prefix/") + file.relativeTo(directory).invariantSeparatorsPath to file
            }.toList()
        }
        require(files.map { it.first }.distinct().size == files.size) { "备份文件路径重复" }
        val total = files.sumOf { it.second.length() }
        var completed = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            zip.setLevel(0)
            files.forEach { (name, file) ->
                checkpoint()
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        checkpoint()
                        val count = input.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                        completed += count
                        progress(completed, total)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    /** 同卷目录切换在独占租约内完成；失败时撤销本次切换，保全原安装。 */
    fun install(source: File, target: File, checkpoint: () -> Unit) =
        install(source, target, target, checkpoint)

    /** 插件跨版本替换锁定整个资源组，不能绕过旧版本的运行引用。 */
    fun install(source: File, target: File, exclusiveDirectory: File, checkpoint: () -> Unit) {
        checkpoint()
        require(target.canonicalFile.toPath().startsWith(exclusiveDirectory.canonicalFile.toPath()))
        check(LocalModelRuntimeRegistry.withExclusiveAccess(exclusiveDirectory) {
            val previous = File(target.parentFile, ".${target.name}.previous-${UUID.randomUUID()}")
            val existed = target.exists()
            if (existed) move(target, previous)
            try {
                move(source, target)
            } catch (failure: Throwable) {
                if (existed) {
                    try { move(previous, target) } catch (undo: Throwable) { failure.addSuppressed(undo) }
                }
                throw failure
            }
            if (existed && !previous.deleteRecursively()) AppLogger.w("ModelArchive", "无法清理已替换的资源目录：${previous.path}")
        }) { "资源正在使用，无法替换" }
    }
    fun move(source: File, target: File) {
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}

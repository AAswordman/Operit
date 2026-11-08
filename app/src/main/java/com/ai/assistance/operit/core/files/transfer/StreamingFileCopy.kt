package com.ai.assistance.operit.core.files.transfer

import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.OpenOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

internal enum class FileCopyPhase { SCANNING, COPYING, COMPLETED }

internal data class FileCopyProgress(
    val phase: FileCopyPhase,
    val currentFile: String,
    val copiedBytes: Long,
    val totalBytes: Long?,
    val copiedFiles: Long,
    val totalFiles: Long,
    val bytesPerSecond: Double?,
) {
    val fraction: Float?
        get() = when {
            phase == FileCopyPhase.COMPLETED -> 1f
            phase == FileCopyPhase.SCANNING || totalBytes == null -> null
            totalBytes > 0 -> (copiedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 0.999f)
            totalFiles > 0 -> (copiedFiles.toDouble() / totalFiles).toFloat().coerceIn(0f, 0.999f)
            else -> 0f
        }

    val remainingBytes: Long?
        get() = totalBytes?.let { (it - copiedBytes).coerceAtLeast(0L) }
}

/** 两次逐层遍历分别统计和复制，不缓存整棵目录树，也不将文件内容转成字符串。 */
internal object StreamingFileCopy {
    const val BUFFER_SIZE = 256 * 1024
    private const val REPORT_INTERVAL_NANOS = 200_000_000L
    private const val SPEED_WINDOW_NANOS = 500_000_000L

    fun copy(
        source: File,
        destination: File,
        recursive: Boolean = true,
        overwrite: Boolean = false,
        onProgress: (FileCopyProgress) -> Unit = {},
        checkCancelled: () -> Unit = {},
    ): FileCopyProgress {
        val sourcePath = source.absoluteFile.toPath().normalize()
        val destinationPath = destination.absoluteFile.toPath().normalize()
        val attributes = Files.readAttributes(sourcePath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val realSource = source.canonicalFile.toPath()
        val realDestination = destination.canonicalFile.toPath()
        require(realSource != realDestination && (!attributes.isDirectory || !realDestination.startsWith(realSource))) {
            "Cannot copy a path onto itself or into its own directory"
        }
        require(recursive || !attributes.isDirectory) { "Cannot copy a directory without recursive=true" }
        if (!overwrite && Files.exists(destinationPath, LinkOption.NOFOLLOW_LINKS)) {
            throw FileAlreadyExistsException(destination.path)
        }

        var phase = FileCopyPhase.SCANNING
        var currentFile = source.name
        var totalBytes = 0L
        var totalFiles = 0L
        var copiedBytes = 0L
        var copiedFiles = 0L
        var lastReportedAt = System.nanoTime()
        var windowStartedAt = lastReportedAt
        var windowStartedBytes = 0L
        var speed: Double? = null

        fun report(force: Boolean = false): FileCopyProgress? {
            checkCancelled()
            val now = System.nanoTime()
            if (phase == FileCopyPhase.COPYING && now - windowStartedAt >= SPEED_WINDOW_NANOS) {
                speed = (copiedBytes - windowStartedBytes) * 1_000_000_000.0 / (now - windowStartedAt)
                windowStartedAt = now
                windowStartedBytes = copiedBytes
            }
            if (!force && now - lastReportedAt < REPORT_INTERVAL_NANOS) return null
            lastReportedAt = now
            return FileCopyProgress(
                phase, currentFile, copiedBytes,
                totalBytes.takeUnless { phase == FileCopyPhase.SCANNING }, totalFiles = totalFiles,
                copiedFiles = copiedFiles, bytesPerSecond = speed,
            ).also(onProgress)
        }

        fun select(path: Path) {
            checkCancelled()
            currentFile = sourcePath.relativize(path).toString().ifEmpty { source.name }
        }

        report(force = true)
        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                select(dir)
                report()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                select(file)
                require(attrs.isRegularFile || attrs.isSymbolicLink) { "Unsupported file type: $file" }
                totalFiles = Math.addExact(totalFiles, 1L)
                if (attrs.isRegularFile) totalBytes = Math.addExact(totalBytes, attrs.size())
                report()
                return FileVisitResult.CONTINUE
            }
        })

        phase = FileCopyPhase.COPYING
        windowStartedAt = System.nanoTime()
        windowStartedBytes = 0L
        report(force = true)
        if (attributes.isDirectory && !overwrite) {
            destinationPath.parent?.let { Files.createDirectories(it) }
            // 原子创建顶层目标，统计期间出现同名目录时也不能合并写入。
            Files.createDirectory(destinationPath)
        }
        // 整个任务复用这一块缓冲区，内存占用不随文件大小或文件数量增长。
        val buffer = ByteArray(BUFFER_SIZE)
        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            private fun target(path: Path): Path = destinationPath.resolve(sourcePath.relativize(path))

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                select(dir)
                Files.createDirectories(target(dir))
                report()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                select(file)
                val target = target(file)
                target.parent?.let { Files.createDirectories(it) }
                if (attrs.isSymbolicLink) {
                    // 不遍历链接目标，避免目录环和将链接指向的外部目录一并复制。
                    if (overwrite) Files.copy(file, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING)
                    else Files.copy(file, target, LinkOption.NOFOLLOW_LINKS)
                } else {
                    require(attrs.isRegularFile) { "Unsupported file type: $file" }
                    val options = if (overwrite) {
                        arrayOf<OpenOption>(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
                    } else {
                        arrayOf<OpenOption>(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
                    }
                    var fileBytes = 0L
                    Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { input ->
                        Files.newOutputStream(target, *options).use { output ->
                            while (true) {
                                checkCancelled()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                output.write(buffer, 0, count)
                                fileBytes += count
                                copiedBytes += count
                                report()
                            }
                        }
                    }
                    if (fileBytes != attrs.size()) throw IOException("Source size changed while copying: $file")
                }
                copiedFiles++
                report()
                return FileVisitResult.CONTINUE
            }
        })
        if (copiedBytes != totalBytes || copiedFiles != totalFiles) {
            throw IOException("Source directory changed while copying: $source")
        }
        phase = FileCopyPhase.COMPLETED
        return checkNotNull(report(force = true))
    }
}

package com.ai.assistance.operit.core.files.transfer

import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamingFileCopyTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun digest(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    @Test fun binaryLargerThanFiftyMiBIsCopiedWithoutTextConversion() {
        val source = temporary.newFile("binary.dat")
        val destination = File(temporary.root, "result.dat")
        // 测试输入自身也逐块生成，不能让测试通过分配整文件字节数组掩盖问题。
        val pattern = ByteArray(256 * 1024) { ((it * 37 + 129) % 256).toByte() }
        source.outputStream().use { output -> repeat(256) { output.write(pattern) } }
        val progress = mutableListOf<FileCopyProgress>()
        val result = StreamingFileCopy.copy(source, destination, onProgress = { progress += it })
        assertEquals(64L * 1024 * 1024, destination.length())
        assertArrayEquals(digest(source), digest(destination))
        assertEquals(source.length(), result.copiedBytes)
        assertEquals(source.length(), result.totalBytes)
        assertEquals(FileCopyPhase.SCANNING, progress.first().phase)
        assertEquals(null, progress.first().totalBytes)
        assertEquals(FileCopyPhase.COMPLETED, progress.last().phase)
        assertEquals(1f, result.fraction)
        assertTrue(progress.filter { it.phase != FileCopyPhase.COMPLETED }.all { it.fraction != 1f })
    }

    @Test fun directoryIncludesHiddenFilesEmptyDirectoriesAndZeroByteFiles() {
        val source = temporary.newFolder("source")
        File(source, ".git/objects").mkdirs()
        File(source, ".git/objects/binary").writeBytes(byteArrayOf(0, -1, -2, 10))
        File(source, "empty").mkdir()
        File(source, "zero").createNewFile()
        repeat(80) { File(source, "item-$it").writeText("内容$it") }
        val destination = File(temporary.root, "copied")
        val result = StreamingFileCopy.copy(source, destination)
        assertTrue(File(destination, "empty").isDirectory)
        assertTrue(File(destination, "zero").isFile)
        assertArrayEquals(byteArrayOf(0, -1, -2, 10), File(destination, ".git/objects/binary").readBytes())
        repeat(80) { assertEquals("内容$it", File(destination, "item-$it").readText()) }
        assertEquals(82L, result.copiedFiles)
        assertEquals(0L, result.remainingBytes)
    }

    @Test fun symbolicDirectoryLinksAreCopiedWithoutFollowingTheirTargets() {
        val source = temporary.newFolder("links")
        File(source, "data").writeBytes(byteArrayOf(1, 2, 3))
        Files.createSymbolicLink(File(source, "loop").toPath(), Paths.get("."))
        val destination = File(temporary.root, "links-copy")
        val result = StreamingFileCopy.copy(source, destination)
        assertEquals(Paths.get("."), Files.readSymbolicLink(File(destination, "loop").toPath()))
        assertEquals(2L, result.copiedFiles)
        assertEquals(3L, result.copiedBytes)
    }

    @Test fun samePathDescendantAndAliasedDescendantAreRejectedBeforeWriting() {
        val source = temporary.newFolder("parent")
        File(source, "child").mkdir()
        assertThrows(IllegalArgumentException::class.java) { StreamingFileCopy.copy(source, source) }
        val descendant = File(source, "child/../copy")
        assertThrows(IllegalArgumentException::class.java) { StreamingFileCopy.copy(source, descendant) }
        val alias = File(temporary.root, "alias")
        Files.createSymbolicLink(alias.toPath(), source.toPath())
        assertThrows(IllegalArgumentException::class.java) { StreamingFileCopy.copy(source, File(alias, "copy")) }
        assertFalse(File(source, "copy").exists())
    }

    @Test fun existingDestinationIsProtectedAndExplicitOverwriteTruncatesIt() {
        val source = temporary.newFile("new-data").apply { writeBytes(byteArrayOf(0, -1)) }
        val destination = temporary.newFile("old-data").apply { writeText("original content") }
        assertThrows(FileAlreadyExistsException::class.java) { StreamingFileCopy.copy(source, destination) }
        assertEquals("original content", destination.readText())
        StreamingFileCopy.copy(source, destination, overwrite = true)
        assertArrayEquals(byteArrayOf(0, -1), destination.readBytes())
    }

    @Test fun failedDirectoryCopyDoesNotReportCompletionOrRemoveItsSource() {
        val source = temporary.newFolder("failure-source")
        File(source, "data").writeText("保留来源")
        val blockedParent = temporary.newFile("blocked")
        val progress = mutableListOf<FileCopyProgress>()
        assertThrows(IOException::class.java) {
            StreamingFileCopy.copy(source, File(blockedParent, "result"), onProgress = { progress += it })
        }
        assertEquals("保留来源", File(source, "data").readText())
        assertTrue(progress.none { it.phase == FileCopyPhase.COMPLETED })
    }

    @Test fun cancellationDuringAFileStopsBeforeCompletionAndRetainsSource() {
        val source = temporary.newFile("cancel-source")
        val buffer = ByteArray(StreamingFileCopy.BUFFER_SIZE) { 31 }
        source.outputStream().use { output -> repeat(4) { output.write(buffer) } }
        val destination = File(temporary.root, "cancel-target")
        val progress = mutableListOf<FileCopyProgress>()
        assertThrows(CancellationException::class.java) {
            StreamingFileCopy.copy(source, destination, onProgress = { progress += it }, checkCancelled = {
                if (destination.length() >= StreamingFileCopy.BUFFER_SIZE) throw CancellationException("取消复制")
            })
        }
        assertEquals(4L * StreamingFileCopy.BUFFER_SIZE, source.length())
        assertTrue(destination.length() < source.length())
        assertTrue(progress.none { it.phase == FileCopyPhase.COMPLETED })
    }

    @Test fun sourceTreeChangesAreReportedAsFailureRatherThanCompleteProgress() {
        val source = temporary.newFolder("changing-source")
        File(source, "first").writeText("first")
        val destination = File(temporary.root, "changing-target")
        var changed = false
        val progress = mutableListOf<FileCopyProgress>()
        assertThrows(IOException::class.java) {
            StreamingFileCopy.copy(source, destination, onProgress = {
                progress += it
                if (!changed && it.phase == FileCopyPhase.COPYING) {
                    changed = true
                    File(source, "added").writeText("added")
                }
            })
        }
        assertTrue(source.exists())
        assertTrue(progress.none { it.phase == FileCopyPhase.COMPLETED })
    }

    @Test fun destinationCreatedDuringScanningIsNotMergedOrOverwritten() {
        val source = temporary.newFolder("conflict-source")
        File(source, "data").writeText("source")
        val destination = File(temporary.root, "conflict-target")
        assertThrows(FileAlreadyExistsException::class.java) {
            StreamingFileCopy.copy(source, destination, onProgress = {
                if (it.phase == FileCopyPhase.COPYING && !destination.exists()) {
                    destination.mkdir()
                    File(destination, "existing").writeText("保留目标")
                }
            })
        }
        assertEquals("保留目标", File(destination, "existing").readText())
        assertFalse(File(destination, "data").exists())
    }

    @Test fun emptyDirectoryCompletesWithZeroBytesAndRecursiveFlagIsEnforced() {
        val source = temporary.newFolder("empty-source")
        val destination = File(temporary.root, "empty-target")
        assertThrows(IllegalArgumentException::class.java) { StreamingFileCopy.copy(source, destination, recursive = false) }
        assertFalse(destination.exists())
        val result = StreamingFileCopy.copy(source, destination)
        assertTrue(destination.isDirectory)
        assertEquals(0L, result.copiedBytes)
        assertEquals(1f, result.fraction)
    }
}
package com.ai.assistance.operit.data.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermissions

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeDirectoryCleanerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cleanDeletesOnlyChildrenAndKeepsPreservedNames() = runTest {
        val root = temporaryFolder.newFolder("cleanup-root")
        val nested = File(root, "nested").apply { mkdirs() }
        val first = File(root, "first.bin").apply { writeBytes(ByteArray(3)) }
        val second = File(nested, "second.bin").apply { writeBytes(ByteArray(5)) }
        val noMedia = File(root, ".nomedia").apply { writeText("") }
        val lock = File(root, "lock").apply { writeText("keep") }

        val result =
            SafeDirectoryCleaner().clean(
                listOf(
                    DirectoryCleanupRequest(
                        directory = root,
                        preservedNames = setOf(".nomedia", "lock"),
                    ),
                ),
            )

        assertTrue(root.isDirectory)
        assertTrue(noMedia.isFile)
        assertTrue(lock.isFile)
        assertFalse(first.exists())
        assertFalse(second.exists())
        assertFalse(nested.exists())
        assertEquals(8L, result.deletedBytes)
        assertEquals(2L, result.deletedFileCount)
        assertEquals(0, result.failedEntryCount)
    }

    @Test
    fun cleanDeduplicatesRootsAndDoesNotFollowSymbolicLinks() = runTest {
        val root = temporaryFolder.newFolder("deduplicated-root")
        val outside = temporaryFolder.newFile("outside-target.bin").apply { writeBytes(ByteArray(13)) }
        File(root, "cached.bin").writeBytes(ByteArray(4))
        val link = File(root, "outside-link")
        val linkCreated = runCatching {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            true
        }.getOrDefault(false)
        assumeTrue("Symbolic links are unavailable on this host", linkCreated)

        val request = DirectoryCleanupRequest(root)
        val result = SafeDirectoryCleaner().clean(listOf(request, request))

        assertTrue(root.isDirectory)
        assertTrue(outside.isFile)
        assertFalse(link.exists())
        assertEquals(4L, result.deletedBytes)
        assertEquals(2L, result.deletedFileCount)
        assertEquals(0, result.failedEntryCount)
    }

    @Test
    fun cleanDeletesEmptyDirectoryWithoutReadPermission() = runTest {
        val root = temporaryFolder.newFolder("empty-restricted-root")
        assumeTrue(Files.getFileStore(root.toPath()).supportsFileAttributeView("posix"))
        val restricted = File(root, "sdcard").apply { mkdir() }
        val original = Files.getPosixFilePermissions(restricted.toPath())
        try {
            Files.setPosixFilePermissions(restricted.toPath(), emptySet())

            val result = SafeDirectoryCleaner().clean(listOf(DirectoryCleanupRequest(root)))

            assertTrue(root.isDirectory)
            assertFalse(restricted.exists())
            assertEquals(0, result.failedEntryCount)
        } finally {
            // 断言失败时也恢复权限，保证临时目录能够被测试框架清理。
            if (restricted.exists()) Files.setPosixFilePermissions(restricted.toPath(), original)
        }
    }

    @Test
    fun cleanRepairsRestrictedUbuntuRootAndNestedDirectoryWhenEnabled() = runTest {
        val root = temporaryFolder.newFolder("restricted-ubuntu-root")
        assumeTrue(Files.getFileStore(root.toPath()).supportsFileAttributeView("posix"))
        val restricted = File(root, "restricted").apply { mkdir() }
        val payload = File(restricted, "payload.bin").apply { writeBytes(ByteArray(7)) }
        val childPermissions = Files.getPosixFilePermissions(restricted.toPath())
        try {
            Files.setPosixFilePermissions(restricted.toPath(), emptySet())

            val result = SafeDirectoryCleaner().clean(
                listOf(DirectoryCleanupRequest(root, repairDirectoryPermissions = true)),
            )

            // 先检查实际清理结果，避免只凭文件存在性判断删除成功。
            assertEquals("首个失败路径：${result.firstFailedPath}", 0, result.failedEntryCount)
            assertEquals(7L, result.deletedBytes)
            assertEquals(1L, result.deletedFileCount)
            assertTrue(root.isDirectory)
            assertTrue(root.canRead() && root.canWrite() && root.canExecute())
            assertTrue(Files.notExists(payload.toPath(), LinkOption.NOFOLLOW_LINKS))
            assertTrue(Files.notExists(restricted.toPath(), LinkOption.NOFOLLOW_LINKS))
            assertTrue(result.firstFailedPath == null)
        } finally {
            if (restricted.exists()) Files.setPosixFilePermissions(restricted.toPath(), childPermissions)
        }
    }

    @Test
    fun cleanDoesNotRepairNonemptyRestrictedDirectoryByDefault() = runTest {
        val root = temporaryFolder.newFolder("default-restricted-root")
        assumeTrue(Files.getFileStore(root.toPath()).supportsFileAttributeView("posix"))
        val restricted = File(root, "restricted").apply { mkdir() }
        val retained = File(restricted, "retained.bin").apply { writeBytes(ByteArray(9)) }
        val original = Files.getPosixFilePermissions(restricted.toPath())
        try {
            Files.setPosixFilePermissions(restricted.toPath(), emptySet())
            // 特权运行环境无法复现普通应用的权限拒绝，不伪造失败结果。
            assumeTrue("当前进程仍能读取权限为 000 的目录", !restricted.canRead())

            val result = SafeDirectoryCleaner().clean(listOf(DirectoryCleanupRequest(root)))

            assertEquals(1, result.failedEntryCount)
            assertEquals(restricted.absolutePath, result.firstFailedPath)
            assertTrue(Files.getPosixFilePermissions(restricted.toPath()).isEmpty())
        } finally {
            Files.setPosixFilePermissions(restricted.toPath(), original)
        }
        assertTrue(retained.isFile)
    }

    @Test
    fun permissionRepairPreservesExcludedDirectoriesAndExternalLinkTargets() = runTest {
        val root = temporaryFolder.newFolder("permission-boundary-root")
        assumeTrue(Files.getFileStore(root.toPath()).supportsFileAttributeView("posix"))
        val outside = temporaryFolder.newFolder("outside-directory")
        val retained = File(outside, "retained.bin").apply { writeBytes(ByteArray(11)) }
        val preserved = File(root, "keep").apply { mkdir() }
        val outsidePermissions = Files.getPosixFilePermissions(outside.toPath())
        val preservedPermissions = Files.getPosixFilePermissions(preserved.toPath())
        val link = File(root, "outside-link")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        try {
            Files.setPosixFilePermissions(outside.toPath(), PosixFilePermissions.fromString("---------"))
            Files.setPosixFilePermissions(preserved.toPath(), emptySet())

            val result = SafeDirectoryCleaner().clean(
                listOf(DirectoryCleanupRequest(root, setOf("keep"), repairDirectoryPermissions = true)),
            )

            assertFalse(Files.isSymbolicLink(link.toPath()))
            assertTrue(Files.getPosixFilePermissions(outside.toPath()).isEmpty())
            assertTrue(Files.getPosixFilePermissions(preserved.toPath()).isEmpty())
            assertEquals(0, result.failedEntryCount)
        } finally {
            Files.setPosixFilePermissions(outside.toPath(), outsidePermissions)
            Files.setPosixFilePermissions(preserved.toPath(), preservedPermissions)
        }
        assertTrue(retained.isFile)
    }

    @Test
    fun cleanRejectsSymbolicLinkAsCleanupRoot() = runTest {
        val target = temporaryFolder.newFolder("target-root")
        val retained = File(target, "retained.bin").apply { writeBytes(ByteArray(6)) }
        val link = File(temporaryFolder.root, "linked-root")
        val linkCreated = runCatching {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        }.getOrDefault(false)
        assumeTrue("Symbolic links are unavailable on this host", linkCreated)

        val result = SafeDirectoryCleaner().clean(listOf(DirectoryCleanupRequest(link)))

        assertTrue(retained.isFile)
        assertTrue(link.exists())
        assertEquals(0L, result.deletedBytes)
        assertEquals(0L, result.deletedFileCount)
        assertEquals(1, result.failedEntryCount)
    }
}

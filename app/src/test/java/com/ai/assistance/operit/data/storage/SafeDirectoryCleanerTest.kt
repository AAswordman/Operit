package com.ai.assistance.operit.data.storage

import java.io.File
import java.nio.file.Files
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

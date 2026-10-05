package com.ai.assistance.operit.data.storage

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileStorageScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun scanUsesLongestRuleAndDoesNotDoubleCountOverlappingRoots() = runTest {
        val root = temporaryFolder.newFolder("scan-root")
        File(root, "other.bin").writeBytes(ByteArray(3))
        val cache = File(root, "cache").apply { mkdirs() }
        File(cache, "cached.bin").writeBytes(ByteArray(5))

        val result =
            FileStorageScanner().scan(
                roots =
                    listOf(
                        StorageScanRoot(root, StorageScope.APP_DATA),
                        StorageScanRoot(cache, StorageScope.USER_FILES),
                    ),
                rules =
                    listOf(
                        StorageScanRule(
                            path = root,
                            category = StorageCategory.OTHER,
                            detail = StorageDetail.OTHER_APP_DATA,
                        ),
                        StorageScanRule(
                            path = cache,
                            category = StorageCategory.CACHE_AND_TEMPORARY,
                            detail = StorageDetail.TEMPORARY_FILES,
                            scope = StorageScope.CACHE,
                            cleanupTarget = CleanupTarget.TEMPORARY_FILES,
                        ),
                    ),
            )

        assertEquals(8L, result.scopeBytes.values.sum())
        assertEquals(3L, result.categoryUsage.getValue(StorageCategory.OTHER).bytes)
        assertEquals(5L, result.categoryUsage.getValue(StorageCategory.CACHE_AND_TEMPORARY).bytes)
        assertEquals(5L, result.cleanupUsage.getValue(CleanupTarget.TEMPORARY_FILES).bytes)
        assertEquals(2L, result.categoryUsage.values.sumOf { it.fileCount })
    }

    @Test
    fun scanAssignsUnknownAppFilesToOther() = runTest {
        val root = temporaryFolder.newFolder("unknown-root")
        File(root, "unknown.bin").writeBytes(ByteArray(7))

        val result =
            FileStorageScanner().scan(
                roots = listOf(StorageScanRoot(root, StorageScope.APP_DATA)),
                rules = emptyList(),
            )

        assertEquals(7L, result.scopeBytes.getValue(StorageScope.APP_DATA))
        assertEquals(7L, result.categoryUsage.getValue(StorageCategory.OTHER).bytes)
        assertEquals(7L, result.detailUsage.getValue(StorageDetail.OTHER_APP_DATA).bytes)
    }

    @Test
    fun scanSkipsSymbolicLinksWithoutFollowingTheirTargets() = runTest {
        val root = temporaryFolder.newFolder("symlink-root")
        val outside = temporaryFolder.newFile("outside.bin").apply { writeBytes(ByteArray(11)) }
        File(root, "inside.bin").writeBytes(ByteArray(2))
        val link = File(root, "outside-link")
        val linkCreated = runCatching {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            true
        }.getOrDefault(false)
        assumeTrue("Symbolic links are unavailable on this host", linkCreated)

        val result =
            FileStorageScanner().scan(
                roots = listOf(StorageScanRoot(root, StorageScope.APP_DATA)),
                rules = emptyList(),
            )

        assertEquals(2L, result.scopeBytes.values.sum())
        assertEquals(1L, result.categoryUsage.getValue(StorageCategory.OTHER).fileCount)
        assertEquals(1, result.skippedSymbolicLinkCount)
        assertTrue(outside.exists())
    }

    @Test
    fun scanRejectsSymbolicLinkAsScanRoot() = runTest {
        val target = temporaryFolder.newFolder("linked-scan-target")
        File(target, "outside.bin").writeBytes(ByteArray(9))
        val link = File(temporaryFolder.root, "linked-scan-root")
        val linkCreated = runCatching {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        }.getOrDefault(false)
        assumeTrue("Symbolic links are unavailable on this host", linkCreated)

        val result =
            FileStorageScanner().scan(
                roots = listOf(StorageScanRoot(link, StorageScope.APP_DATA)),
                rules = emptyList(),
            )

        assertTrue(result.scopeBytes.isEmpty())
        assertEquals(1, result.skippedSymbolicLinkCount)
    }
}

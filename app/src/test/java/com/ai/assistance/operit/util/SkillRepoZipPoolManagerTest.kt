package com.ai.assistance.operit.util

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkillRepoZipPoolManagerTest {

    private var previousSystemLogEnabled = true
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
        tempDir = createTempDir(prefix = "skill-zip-pool-")
        SkillRepoZipPoolManager.initialize(tempDir)
    }

    @After
    fun tearDown() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = true
        tempDir.deleteRecursively()
    }

    @Test
    fun poolKey_usesOwnerRepoAndIdentity() {
        assertEquals(
            "owner/repo@abc123",
            SkillRepoZipPoolManager.poolKey(" owner ", " repo ", " abc123 ")
        )
    }

    @Test
    fun getOrDownloadZip_reusesCachedFileUntilForcedRefresh() = runBlocking {
        val key = SkillRepoZipPoolManager.poolKey("owner", "repo", "sha-old")
        var downloads = 0

        val first = SkillRepoZipPoolManager.getOrDownloadZip(key) { outFile ->
            downloads += 1
            outFile.writeText("old-zip")
            true
        }
        val cached = SkillRepoZipPoolManager.getOrDownloadZip(key) { outFile ->
            downloads += 1
            outFile.writeText("should-not-run")
            true
        }
        val refreshed = SkillRepoZipPoolManager.getOrDownloadZip(
            key = key,
            forceRefresh = true
        ) { outFile ->
            downloads += 1
            outFile.writeText("new-zip")
            true
        }

        assertEquals(2, downloads)
        assertEquals("old-zip", first?.readText())
        assertEquals("old-zip", cached?.readText())
        assertEquals("new-zip", refreshed?.readText())
        assertEquals(
            SkillRepoZipPoolManager.cacheFileName(key),
            refreshed?.name
        )
    }

    @Test
    fun invalidate_deletesCachedZipSoNextCallRedownloads() = runBlocking {
        val key = SkillRepoZipPoolManager.poolKey("owner", "repo", "sha-1")
        var downloads = 0

        SkillRepoZipPoolManager.getOrDownloadZip(key) { outFile ->
            downloads += 1
            outFile.writeText("cached")
            true
        }
        SkillRepoZipPoolManager.invalidate(key)
        val afterInvalidate = SkillRepoZipPoolManager.getOrDownloadZip(key) { outFile ->
            downloads += 1
            outFile.writeText("fresh")
            true
        }

        assertEquals(2, downloads)
        assertEquals("fresh", afterInvalidate?.readText())
        assertFalse(File(tempDir, "skill_repo_zip_pool/${SkillRepoZipPoolManager.cacheFileName("missing")}").exists())
        assertTrue(afterInvalidate!!.exists())
    }
}
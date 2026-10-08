package com.ai.assistance.operit.ui.features.chat.webview.workspace.browser

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Year
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceBrowserSupportTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun singleNamesAllowNormalAndHiddenFilesButRejectPathTraversal() {
        for (name in listOf("中文 文档.md", ".hidden", "a..b", "report.txt")) {
            assertTrue(name, isValidWorkspaceEntryName(name))
        }
        for (name in listOf("", " ", ".", "..", "../a", "folder/a", "folder\\a", "a\u0000b")) {
            assertFalse(name, isValidWorkspaceEntryName(name))
        }
    }

    @Test
    fun modifiedTimeComparesNumericMillisecondsInsteadOfTheirText() {
        assertTrue(directoryModifiedTime("9")!! < directoryModifiedTime("10")!!)
        assertEquals(172800000L, directoryModifiedTime("172800000")!!)
    }

    @Test
    fun offsetAndUtcTimesRepresentTheSameMoment() {
        assertEquals(directoryModifiedTime("2026-10-06T08:00:00+08:00"),
            directoryModifiedTime("2026-10-06T00:00:00Z"))
        val local = LocalDateTime.of(2026, 10, 6, 8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(local, directoryModifiedTime("2026-10-06 08:00:00.000")!!)
    }

    @Test
    fun abbreviatedMonthsAreSortedByDateRatherThanAlphabetically() {
        assertTrue(directoryModifiedTime("Sep 30 12:00")!! < directoryModifiedTime("Oct 01 12:00")!!)
        assertEquals(LocalDateTime.of(Year.now().value, 10, 1, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), directoryModifiedTime("Oct 01 12:00")!!)
        assertNull(directoryModifiedTime("invalid"))
        assertNull(directoryModifiedTime("2026-99-99 12:00:00"))
    }

    @Test
    fun cleanupRemovesOnlyExpiredShareDirectoriesAndKeepsRecentFiles() {
        val now = 20L * 24 * 60 * 60 * 1000
        val staleTime = now - 8L * 24 * 60 * 60 * 1000
        val recentTime = now - 24L * 60 * 60 * 1000
        val root = temporary.newFolder("workspace_shared_files")
        val expired = root.resolve("expired").apply { mkdir() }
        expired.resolve("old.txt").apply { writeText("旧分享"); setLastModified(staleTime) }
        expired.setLastModified(staleTime)
        val active = root.resolve("active").apply { mkdir() }
        active.resolve("current.txt").apply { writeText("最近分享"); setLastModified(recentTime) }
        active.setLastModified(staleTime)
        val unrelated = temporary.newFile("outside-cache.txt")

        pruneWorkspaceShareCache(root, now)

        assertFalse(expired.exists())
        assertEquals("最近分享", active.resolve("current.txt").readText())
        assertTrue(unrelated.exists())
    }
}
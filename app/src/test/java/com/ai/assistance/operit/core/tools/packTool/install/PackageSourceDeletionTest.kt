package com.ai.assistance.operit.core.tools.packTool.install

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackageSourceDeletionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun deletesAllSelectedArchivesOnlyInTheCurrentApplication() {
        val root = temporary.newFolder("com.ai.assistance.operit.kong", "files", "packages")
        val official = temporary.newFolder("com.ai.assistance.operit", "files", "packages")
        val oldArchive = File(root, "plugin-old.toolpkg").apply { writeText("old") }
        val newArchive = File(root, "plugin-new.toolpkg").apply { writeText("new") }
        val other = File(root, "other.js").apply { writeText("other") }
        val officialArchive = File(official, "plugin-new.toolpkg").apply { writeText("official") }
        assertTrue(deleteInstalledPackageSources(root, listOf(oldArchive.path, newArchive.path)))
        assertFalse(oldArchive.exists())
        assertFalse(newArchive.exists())
        assertTrue(other.exists())
        assertTrue(officialArchive.exists())
    }

    @Test fun refusesSourcesOutsideTheCurrentApplicationBeforeDeletingAnything() {
        val root = temporary.newFolder("kong")
        val local = File(root, "plugin.toolpkg").apply { writeText("local") }
        val outside = temporary.newFile("outside.toolpkg")
        assertFalse(deleteInstalledPackageSources(root, listOf(local.path, outside.path)))
        assertTrue(local.exists())
        assertTrue(outside.exists())
    }

    @Test fun missingOrUnknownSourcesNeverReportSuccess() {
        val root = temporary.newFolder("packages")
        assertFalse(deleteInstalledPackageSources(root, emptyList()))
        assertFalse(deleteInstalledPackageSources(root, listOf(File(root, "missing.js").path)))
    }

    @Test fun failedDeletionAndFalseSuccessLeaveTheResultFailed() {
        val root = temporary.newFolder("packages")
        val source = File(root, "renamed.ts").apply { writeText("script") }
        assertFalse(deleteInstalledPackageSources(root, listOf(source.path)) { false })
        assertFalse(deleteInstalledPackageSources(root, listOf(source.path)) { true })
        assertTrue(source.exists())
    }

    @Test fun repeatedSourceReferencesAreDeletedOnce() {
        val root = temporary.newFolder("packages")
        val source = File(root, "plugin.toolpkg").apply { writeText("plugin") }
        assertTrue(deleteInstalledPackageSources(root, listOf(source.path, source.path)))
        assertFalse(source.exists())
    }
}

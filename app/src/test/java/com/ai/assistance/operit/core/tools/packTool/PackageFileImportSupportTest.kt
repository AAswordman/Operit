package com.ai.assistance.operit.core.tools.packTool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageFileImportSupportTest {
    @Test
    fun `accepts js and toolpkg share files`() {
        assertTrue(PackageFileImportSupport.isShareImportableFileName("demo.js"))
        assertTrue(PackageFileImportSupport.isShareImportableFileName("local.demo.toolpkg"))
        assertTrue(PackageFileImportSupport.isShareImportableFileName("path/to/Demo.JS"))
    }

    @Test
    fun `rejects mixed or unsupported share files`() {
        assertFalse(PackageFileImportSupport.isShareImportableFileName("notes.txt"))
        assertFalse(PackageFileImportSupport.isShareImportableFileName("photo.png"))
        assertFalse(PackageFileImportSupport.isShareImportableFileName("script.ts"))
        assertFalse(PackageFileImportSupport.isShareImportableFileName("archive.zip"))
        assertFalse(PackageFileImportSupport.isShareImportableFileName(""))
    }
}
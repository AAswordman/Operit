package com.ai.assistance.operit.core.tools.packTool

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPkgManifestSelectionTest {
    @Test
    fun `preview and loading select the shallow package manifest regardless of zip order`() {
        val archive = zipOf(
            "package/.backup/manifest.json" to manifest("backup"),
            "package/manifest.json" to manifest("package"),
            "package/main.js" to "// package entry"
        )

        val preview = ToolPkgArchiveParser.readToolPkgManifestPreview {
            ByteArrayInputStream(archive)
        }

        assertEquals("package/manifest.json", preview?.entryName)
        assertEquals("package/manifest.json", ToolPkgArchiveParser.findManifestEntry(
            listOf("package/.backup/manifest.json", "package/manifest.json", "package/main.js")
        ))
        assertEquals("package", preview?.manifest?.toolpkgId)
    }

    @Test
    fun `root hjson wins over every other manifest`() {
        assertEquals(
            "manifest.hjson",
            ToolPkgArchiveParser.findManifestEntry(
                listOf("wrapped/manifest.hjson", "manifest.json", "manifest.hjson")
            )
        )
    }

    private fun manifest(id: String): String {
        return """{"toolpkg_id":"$id","main":"main.js"}"""
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }
}

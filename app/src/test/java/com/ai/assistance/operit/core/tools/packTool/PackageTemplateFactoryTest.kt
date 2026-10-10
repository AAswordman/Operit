package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageTemplateFactoryTest {
    @Test
    fun `suggests a toolpkg id from an ascii display name`() {
        assertEquals(
            "local.my_plugin",
            PackageTemplateFactory.suggestPackageId("My Plugin", forToolPkg = true)
        )
    }

    @Test
    fun `falls back when the display name has no latin letters`() {
        assertEquals(
            "local.my_package",
            PackageTemplateFactory.suggestPackageId("我的插件", forToolPkg = true)
        )
        assertEquals(
            "my_package",
            PackageTemplateFactory.suggestPackageId("我的沙盒包", forToolPkg = false)
        )
    }

    @Test
    fun `rejects invalid identifiers`() {
        assertFalse(PackageTemplateFactory.isValidPackageId("1bad", forToolPkg = false))
        assertFalse(PackageTemplateFactory.isValidPackageId("has-dash", forToolPkg = false))
        assertFalse(PackageTemplateFactory.isValidPackageId(".hidden", forToolPkg = true))
        assertTrue(PackageTemplateFactory.isValidPackageId("local.my_plugin", forToolPkg = true))
        assertTrue(PackageTemplateFactory.isValidPackageId("my_package", forToolPkg = false))
    }

    @Test
    fun `writes a sandbox js template with metadata and export`() {
        val dir = File.createTempFile("packages", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            val result =
                PackageTemplateFactory.create(
                    packagesDir = dir,
                    kind = PackageTemplateKind.SANDBOX_JS,
                    displayName = "Demo Package",
                    packageId = "demo_package"
                )
            assertTrue(result.success)
            val file = result.file!!
            assertEquals("demo_package.js", file.name)
            val content = file.readText()
            assertTrue(content.startsWith("/* METADATA"))
            assertTrue(content.contains("\"name\": \"demo_package\""))
            assertTrue(content.contains("exports.hello_world"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `writes a js toolpkg with manifest and main entry`() {
        val dir = File.createTempFile("packages", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            val result =
                PackageTemplateFactory.create(
                    packagesDir = dir,
                    kind = PackageTemplateKind.PLUGIN_JS,
                    displayName = "Demo Plugin",
                    packageId = "local.demo_plugin"
                )
            assertTrue(result.success)
            val file = result.file!!
            assertEquals("local.demo_plugin.toolpkg", file.name)
            ZipFile(file).use { zip ->
                val names = zip.entries().toList().map { it.name }.toSet()
                assertEquals(setOf("manifest.json", "dist/main.js"), names)
                val manifest = zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()
                val main = zip.getInputStream(zip.getEntry("dist/main.js")).bufferedReader().readText()
                assertTrue(manifest.contains("\"toolpkg_id\": \"local.demo_plugin\""))
                assertTrue(manifest.contains("\"main\": \"dist/main.js\""))
                assertTrue(main.contains("function registerToolPkg()"))
                assertTrue(main.contains("exports.registerToolPkg = registerToolPkg;"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `writes a ts toolpkg with source and compiled main`() {
        val dir = File.createTempFile("packages", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            val result =
                PackageTemplateFactory.create(
                    packagesDir = dir,
                    kind = PackageTemplateKind.PLUGIN_TS,
                    displayName = "Demo TS Plugin",
                    packageId = "local.demo_ts"
                )
            assertTrue(result.success)
            ZipFile(result.file!!).use { zip ->
                val names = zip.entries().toList().map { it.name }.toSet()
                assertEquals(
                    setOf("manifest.json", "tsconfig.json", "src/main.ts", "dist/main.js"),
                    names
                )
                val source = zip.getInputStream(zip.getEntry("src/main.ts")).bufferedReader().readText()
                val compiled = zip.getInputStream(zip.getEntry("dist/main.js")).bufferedReader().readText()
                assertTrue(source.contains("export function registerToolPkg()"))
                assertTrue(compiled.contains("exports.registerToolPkg = registerToolPkg;"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `does not overwrite an existing template file`() {
        val dir = File.createTempFile("packages", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            val first =
                PackageTemplateFactory.create(
                    packagesDir = dir,
                    kind = PackageTemplateKind.SANDBOX_JS,
                    displayName = "Demo",
                    packageId = "demo_package"
                )
            val second =
                PackageTemplateFactory.create(
                    packagesDir = dir,
                    kind = PackageTemplateKind.SANDBOX_JS,
                    displayName = "Demo",
                    packageId = "demo_package"
                )
            assertTrue(first.success)
            assertFalse(second.success)
            assertEquals(PackageTemplateCreateError.FILE_EXISTS, second.error)
        } finally {
            dir.deleteRecursively()
        }
    }
}
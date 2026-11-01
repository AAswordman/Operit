package com.ai.assistance.operit.data.themesharing

import com.ai.assistance.operit.data.model.theme.ThemeScriptManifest
import com.ai.assistance.operit.data.model.theme.ThemeStudioMetadata
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeStudioArchiveTest {
    @get:Rule val folder = TemporaryFolder()

    // 此处验证归档与字体魔数，不把合成头部当作可加载的真实字体。
    private val fontHeader = byteArrayOf(0, 1, 0, 0) + ByteArray(8)
    private val source = "module.exports={apiVersion:1,render(context){return context.appearance}};"
    private val manifest = ThemeScriptManifest(
        1, "assets/scripts/main.js", mapOf("bodyFont" to "assets/字体/body.ttf"),
        studio = ThemeStudioMetadata(name = "目录工程", id = "folder_theme", folders = listOf("assets/images/empty/deep")),
    )
    private fun bundle() = ThemeScriptBundle(manifest, source, mapOf("assets/字体/body.ttf" to fontHeader))

    @Test
    fun exportAndImportPreserveEntryResourcesAndEmptyFolders() {
        val (exported, files) = ThemeScriptBundleStore.exportFiles(bundle(), folder.newFolder("export"))
        assertEquals(manifest, exported)
        assertEquals(setOf("assets/scripts/main.js", "assets/字体/body.ttf"), files.keys)
        val document = ThemeTransferSchema.create("目录工程", "作者", ThemePreferenceValues.defaultVisual(), ChatLayoutSettings(), true, exported)
        val archive = folder.newFile("theme.oktheme")
        archive.outputStream().use { ThemeArchive.write(it, document, files) }
        ZipFile(archive).use { zip ->
            assertEquals(setOf("manifest.json", "assets/scripts/main.js", "assets/字体/body.ttf"), zip.entries().toList().map { it.name }.toSet())
        }
        ThemeArchive.read(archive, File(folder.root, "import")).use { prepared ->
            assertEquals(manifest, prepared.document.script)
            val restored = ThemeScriptBundleStore.fromFiles(requireNotNull(prepared.document.script), prepared.assets)
            assertEquals(source, restored.source)
            assertArrayEquals(fontHeader, restored.assets.getValue("assets/字体/body.ttf"))
            assertEquals(listOf("assets/images/empty/deep"), restored.manifest.studio.folders)
        }
    }

    @Test
    fun exportRefusesToOverwriteAnExistingResource() {
        val root = folder.newFolder("conflict")
        val original = File(root, manifest.entry).apply { parentFile.mkdirs(); writeText("existing") }
        try {
            ThemeScriptBundleStore.exportFiles(bundle(), root)
            fail("导出不能覆盖已有的同路径资源")
        } catch (_: IllegalArgumentException) { }
        assertEquals("existing", original.readText())
    }

    @Test
    fun invalidFontHeadersAreRejectedBeforePackaging() {
        for (header in listOf(ByteArray(4), ByteArray(12))) {
            try {
                ThemeScriptBundleStore.validate(bundle().copy(assets = mapOf("assets/字体/body.ttf" to header)))
                fail("应拒绝不完整或错误的字体头")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
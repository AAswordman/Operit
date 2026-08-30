package com.ai.assistance.operit.data.theme.packages

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledCyberGridThemeTest {
    @Test
    fun bundledCyberGridArchivePassesTheSameValidatorAsImportedPackages() {
        val archive = File("src/main/assets/theme-packages/cyber-grid.otheme")
        assertTrue("Bundled cyber package is missing: ${archive.absolutePath}", archive.isFile)

        val validated = ThemePackageArchiveValidatorV1.validate(archive)

        assertEquals("operit.cyber_grid", validated.manifest.packageId)
        assertEquals("1.0.0", validated.manifest.version)
        assertEquals(
            setOf("chat.main"),
            validated.manifest.scenes.map { scene -> scene.sceneId.value }.toSet(),
        )
        ZipFile(archive).use { zip ->
            assertEquals(THEME_PACKAGE_ZIP_COMMENT, zip.comment)
        }
    }
}

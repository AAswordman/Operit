package com.ai.assistance.operit.data.theme.packages

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledDefaultThemeTest {
    @Test
    fun bundledDefaultArchivePassesTheSameValidatorAsImportedPackages() {
        val archive = File("src/main/assets/theme-packages/operit-default.otheme")
        assertTrue("Bundled default package is missing: ${archive.absolutePath}", archive.isFile)

        val validated = ThemePackageArchiveValidatorV1.validate(
            archive,
            expectedSha256 = ThemePackageDefaultV1.ARCHIVE_SHA256,
        )

        assertEquals(ThemePackageDefaultV1.PACKAGE_ID, validated.manifest.packageId)
        assertEquals(ThemePackageDefaultV1.VERSION, validated.manifest.version)
        assertEquals(ThemePackageDefaultV1.coordinate, validated.manifest.coordinateFor(validated.archiveSha256))
        assertEquals(
            setOf("chat.main"),
            validated.manifest.scenes.map { scene -> scene.sceneId.value }.toSet(),
        )
        ZipFile(archive).use { zip ->
            assertEquals(THEME_PACKAGE_ZIP_COMMENT, zip.comment)
        }
    }
}

package com.ai.assistance.operit.data.theme.packages

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置默认主题必须与外置仓库 Release 字节一致，并使用与导入包完全相同的
 * V2 校验器。这是“默认主题不是特殊分支”的静态门槛。
 */
class BundledDefaultThemeV2Test {
    @Test
    fun bundledDefaultArchivePassesTheSameValidatorAsImportedPackages() {
        val archive = File("src/main/assets/theme-packages/operit-default-v2.otheme")
        assertTrue("Bundled default package is missing: ${archive.absolutePath}", archive.isFile)

        val validated =
            ThemePackageArchiveValidatorV2.validate(
                archive,
                expectedSha256 = ThemePackageDefaultV2.ARCHIVE_SHA256,
            )

        assertEquals(ThemePackageDefaultV2.PACKAGE_ID, validated.manifest.packageId)
        assertEquals(ThemePackageDefaultV2.VERSION, validated.manifest.version)
        assertEquals(
            ThemePackageDefaultV2.coordinate,
            validated.manifest.coordinateFor(validated.archiveSha256),
        )
        ZipFile(archive).use { zip ->
            assertEquals(THEME_PACKAGE_ZIP_COMMENT_V2, zip.comment)
        }
    }
}

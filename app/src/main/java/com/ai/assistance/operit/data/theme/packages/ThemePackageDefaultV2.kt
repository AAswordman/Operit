package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact V2 release artifact bundled with Operit and used as the required base theme. */
internal object ThemePackageDefaultV2 {
    const val PACKAGE_ID = "operit.default"
    const val VERSION = "2.1.0"
    const val ARCHIVE_SHA256 = "3ada292d108f11efaaa78e029db307229e2fada18ed15b4bd09a75b8323c8f13"

    private const val ASSET_PATH = "theme-packages/operit-default-v2.otheme"

    val coordinate =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(PACKAGE_ID),
            version = ThemePackageVersionV2(VERSION),
            archiveSha256 = ThemeArchiveSha256V2(ARCHIVE_SHA256),
        )

    suspend fun ensureInstalled(context: Context) {
        withContext(Dispatchers.IO) {
            val installer = ThemePackageInstallerV2.getInstance(context)
            installer.clearUnpublishedV1Installations()
            if (installer.find(coordinate) != null) return@withContext

            val staged = File(context.cacheDir, "operit-default-v2.$THEME_PACKAGE_EXTENSION_V2")
            try {
                context.assets.open(ASSET_PATH).use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                val installed = installer.import(staged, expectedSha256 = ARCHIVE_SHA256)
                check(installed == coordinate) {
                    "Bundled default V2 theme coordinate does not match its release lock."
                }
            } finally {
                staged.delete()
            }
        }
    }

    fun isDefault(coordinate: ThemePackageCoordinateV2): Boolean = coordinate == this.coordinate
}

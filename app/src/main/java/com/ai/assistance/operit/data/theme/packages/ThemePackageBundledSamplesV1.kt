package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled, real `.otheme` samples installed once into the same immutable store as imports. */
internal object ThemePackageBundledSamplesV1 {
    private const val CYBER_GRID_PACKAGE_ID = "operit.cyber_grid"
    private const val CYBER_GRID_VERSION = "1.0.0"
    private const val CYBER_GRID_ASSET_PATH = "theme-packages/cyber-grid.otheme"

    suspend fun installCyberGridIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            val installer = ThemePackageInstallerV1.getInstance(context)
            val installed = installer.catalog().installations.any { installation ->
                installation.coordinate.packageId.value == CYBER_GRID_PACKAGE_ID &&
                    installation.coordinate.version.value == CYBER_GRID_VERSION
            }
            if (installed) return@withContext

            val staged = File(context.cacheDir, "bundled-cyber-grid.$THEME_PACKAGE_EXTENSION")
            try {
                context.assets.open(CYBER_GRID_ASSET_PATH).use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                installer.import(staged)
            } finally {
                staged.delete()
            }
        }
    }

    fun isBundled(coordinate: ThemePackageCoordinateV1): Boolean =
        coordinate.packageId.value == CYBER_GRID_PACKAGE_ID &&
            coordinate.version.value == CYBER_GRID_VERSION
}

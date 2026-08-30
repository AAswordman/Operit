package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

internal class ThemePackageInstallException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Imports `.otheme` archives into immutable installations under
 * `filesDir/theme-packages/installed/`. Validation happens on a staged copy in the cache
 * dir; publication is delegated to the content-addressed [ThemePackagePublicationV1].
 */
internal class ThemePackageInstallerV1 private constructor(
    private val context: Context,
) {
    private val installedRoot: File
        get() = File(context.filesDir, INSTALLED_ROOT)

    private val stagingRoot: File
        get() = File(context.cacheDir, STAGING_ROOT)

    suspend fun import(
        archive: File,
        expectedSha256: String? = null,
    ): ThemePackageCoordinateV1 =
        withContext(Dispatchers.IO) {
            val staged = stage(archive)
            try {
                val validated =
                    ThemePackageArchiveValidatorV1.validate(staged, expectedSha256)
                ThemePackagePublicationV1.publish(
                    validatedArchive = staged,
                    validated = validated,
                    root = installedRoot,
                )
            } finally {
                staged.delete()
            }
        }

    suspend fun uninstall(coordinate: ThemePackageCoordinateV1): Boolean =
        withContext(Dispatchers.IO) {
            val target =
                ThemePackagePublicationV1.installationDir(installedRoot, coordinate)
            if (!target.exists()) return@withContext false
            val active =
                ThemePackageSelectionRepository.getInstance(context)
                    .selectionFlow
                    .first()
                    .let { instance ->
                        (instance.reference as? ThemePackageReferenceV1.Installed)?.coordinate
                    }
            check(active != coordinate) {
                "The active theme package cannot be uninstalled: ${coordinate.packageId.value}"
            }
            ThemePackagePublicationV1.uninstall(installedRoot, coordinate)
        }

    fun catalog(): PublishedThemeCatalogV1 = ThemePackagePublicationV1.catalog(installedRoot)

    fun find(coordinate: ThemePackageCoordinateV1): PublishedThemeInstallationV1? =
        catalog().installations.firstOrNull { installed -> installed.coordinate == coordinate }

    private fun stage(archive: File): File {
        stagingRoot.mkdirs()
        val staged = File(stagingRoot, "${UUID.randomUUID()}.$THEME_PACKAGE_EXTENSION")
        if (!archive.renameTo(staged) && !archive.copyTo(staged, overwrite = true).isFile) {
            throw ThemePackageInstallException("Unable to stage theme archive for validation.")
        }
        return staged
    }

    companion object {
        private const val INSTALLED_ROOT = "theme-packages/installed"
        private const val STAGING_ROOT = "theme-package-staging"

        @Volatile
        private var instance: ThemePackageInstallerV1? = null

        fun getInstance(context: Context): ThemePackageInstallerV1 =
            instance ?: synchronized(this) {
                instance ?: ThemePackageInstallerV1(context.applicationContext).also {
                    instance = it
                }
            }

        fun isThemePackageFileName(name: String): Boolean =
            name.lowercase(Locale.US).endsWith(".$THEME_PACKAGE_EXTENSION")
    }
}

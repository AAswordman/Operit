package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class ThemePackageInstallExceptionV2(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Installs only fully linked V2 packages into immutable content-addressed directories. */
internal class ThemePackageInstallerV2 private constructor(
    private val context: Context,
) {
    private val installedRoot: File
        get() = File(context.filesDir, INSTALLED_ROOT)

    private val stagingRoot: File
        get() = File(context.cacheDir, STAGING_ROOT)

    suspend fun import(
        archive: File,
        expectedSha256: String? = null,
    ): ThemePackageCoordinateV2 =
        withContext(Dispatchers.IO) {
            val staged = stage(archive)
            try {
                val validated = ThemePackageArchiveValidatorV2.validate(staged, expectedSha256)
                validated.manifest.basis?.let { basis ->
                    if (catalog().installations.none { installation -> installation.coordinate == basis }) {
                        throw ThemePackageInstallExceptionV2(
                            "Theme package basis is not installed: ${basis.packageId.value}@${basis.version.value}",
                        )
                    }
                }
                val coordinate = ThemePackagePublicationV2.publish(staged, validated, installedRoot)
                try {
                    val installation = requireNotNull(find(coordinate)) {
                        "Published theme package cannot be found: ${coordinate.packageId.value}."
                    }
                    ThemePackageRuntimeLinkerV2.link(installation, catalog())
                    ThemeRuntimeRepositoryV2.refresh(context)
                    coordinate
                } catch (error: Throwable) {
                    ThemePackagePublicationV2.uninstall(installedRoot, coordinate)
                    throw ThemePackageInstallExceptionV2(
                        "Theme package does not form a complete linked V2 presentation: ${error.message}",
                        error,
                    )
                }
            } finally {
                staged.delete()
            }
        }

    suspend fun uninstall(coordinate: ThemePackageCoordinateV2): Boolean =
        withContext(Dispatchers.IO) {
            val target = ThemePackagePublicationV2.installationDir(installedRoot, coordinate)
            if (!target.exists()) return@withContext false
            check(!ThemePackageDefaultV2.isDefault(coordinate)) {
                "Bundled default theme package cannot be uninstalled: ${coordinate.packageId.value}"
            }
            val active =
                ThemePackageSelectionRepositoryV2.getInstance(context)
                    .selectionFlow
                    .first()
                    .reference.coordinate
            check(active != coordinate) {
                "The active theme package cannot be uninstalled: ${coordinate.packageId.value}"
            }
            val dependents =
                catalog().installations.filter { installation -> installation.manifest.basis == coordinate }
            check(dependents.isEmpty()) {
                "Theme package is required by installed packages: " +
                    dependents.joinToString { installation -> installation.coordinate.packageId.value }
            }
            val uninstalled = ThemePackagePublicationV2.uninstall(installedRoot, coordinate)
            ThemeRuntimeRepositoryV2.refresh(context)
            uninstalled
        }

    fun catalog(): PublishedThemeCatalogV2 = ThemePackagePublicationV2.catalog(installedRoot)

    fun find(coordinate: ThemePackageCoordinateV2): PublishedThemeInstallationV2? =
        catalog().installations.firstOrNull { installation -> installation.coordinate == coordinate }

    fun clearUnpublishedV1Installations() {
        File(context.filesDir, LEGACY_V1_INSTALLED_ROOT).deleteRecursively()
    }

    private fun stage(archive: File): File {
        stagingRoot.mkdirs()
        val staged = File(stagingRoot, "${UUID.randomUUID()}.$THEME_PACKAGE_EXTENSION_V2")
        if (!archive.copyTo(staged, overwrite = true).isFile) {
            throw ThemePackageInstallExceptionV2("Unable to stage theme archive for validation.")
        }
        return staged
    }

    companion object {
        private const val INSTALLED_ROOT = "theme-packages/v2/installed"
        private const val STAGING_ROOT = "theme-packages/v2/staging"
        private const val LEGACY_V1_INSTALLED_ROOT = "theme-packages/installed"

        @Volatile
        private var instance: ThemePackageInstallerV2? = null

        fun getInstance(context: Context): ThemePackageInstallerV2 =
            instance ?: synchronized(this) {
                instance ?: ThemePackageInstallerV2(context.applicationContext).also { created ->
                    instance = created
                }
            }

        fun isThemePackageFileName(name: String): Boolean =
            name.lowercase(Locale.US).endsWith(".$THEME_PACKAGE_EXTENSION_V2")
    }
}

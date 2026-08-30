package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

internal data class PublishedThemeInstallationV1(
    val coordinate: ThemePackageCoordinateV1,
    val manifest: ThemePackageManifestV1,
    val rootDir: File,
)

internal data class PublishedThemeCatalogV1(
    val installations: List<PublishedThemeInstallationV1>,
    val brokenInstallations: List<String>,
)

/**
 * Content-addressed publication layout under a caller-provided root:
 * `<packageId>/<version>/<archiveSha256>/`. Publication writes into a hidden staging
 * directory first and renames it into place, so an installation directory only ever
 * exists in a fully extracted state. The same digest is idempotent.
 */
internal object ThemePackagePublicationV1 {
    private val MANIFEST_JSON =
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
        }

    fun installationDir(
        root: File,
        coordinate: ThemePackageCoordinateV1,
    ): File =
        File(
            root,
            "${coordinate.packageId.value}/${coordinate.version.value}/${coordinate.archiveSha256.value}",
        )

    fun publish(
        validatedArchive: File,
        validated: ThemePackageValidatedArchiveV1,
        root: File,
    ): ThemePackageCoordinateV1 {
        val coordinate = validated.manifest.coordinateFor(validated.archiveSha256)
        val target = installationDir(root, coordinate)
        if (target.exists()) return coordinate

        root.mkdirs()
        val publishing = File(root, ".publishing-${UUID.randomUUID()}")
        try {
            publishing.mkdirs()
            ZipFile(validatedArchive).use { zip ->
                extractEntry(
                    zip,
                    THEME_PACKAGE_MANIFEST_ENTRY,
                    File(publishing, THEME_PACKAGE_MANIFEST_ENTRY),
                )
                validated.manifest.assets.forEach { asset ->
                    extractEntry(zip, asset.path, File(publishing, asset.path))
                }
            }
            if (!publishing.renameTo(target)) {
                throw ThemePackageInstallException(
                    "Unable to publish theme installation at ${target.absolutePath}",
                )
            }
        } finally {
            if (publishing.exists()) publishing.deleteRecursively()
        }
        return coordinate
    }

    fun uninstall(
        root: File,
        coordinate: ThemePackageCoordinateV1,
    ): Boolean {
        val target = installationDir(root, coordinate)
        if (!target.exists()) return false
        target.deleteRecursively()
        target.parentFile?.takeIf { dir -> dir.list()?.isEmpty() == true }?.delete()
        target.parentFile?.parentFile
            ?.takeIf { dir -> dir.list()?.isEmpty() == true }
            ?.delete()
        return true
    }

    fun catalog(root: File): PublishedThemeCatalogV1 {
        if (!root.exists()) return PublishedThemeCatalogV1(emptyList(), emptyList())
        val installations = mutableListOf<PublishedThemeInstallationV1>()
        val broken = mutableListOf<String>()
        root.listFiles()
            ?.filter(File::isDirectory)
            ?.forEach packageDirs@{ packageDir ->
                packageDir.listFiles()
                    ?.filter(File::isDirectory)
                    ?.forEach versionDirs@{ versionDir ->
                        versionDir.listFiles()
                            ?.filter(File::isDirectory)
                            ?.forEach digestDirs@{ digestDir ->
                                val manifestFile =
                                    File(digestDir, THEME_PACKAGE_MANIFEST_ENTRY)
                                if (!manifestFile.isFile) {
                                    broken += digestDir.absolutePath
                                    return@digestDirs
                                }
                                try {
                                    val manifest =
                                        MANIFEST_JSON.decodeFromString<ThemePackageManifestV1>(
                                            manifestFile.readText(Charsets.UTF_8),
                                        )
                                    installations +=
                                        PublishedThemeInstallationV1(
                                            coordinate =
                                                ThemePackageCoordinateV1(
                                                    packageId = ThemePackageIdV1(manifest.packageId),
                                                    version = ThemePackageVersionV1(manifest.version),
                                                    archiveSha256 = ThemeArchiveSha256V1(digestDir.name),
                                                ),
                                            manifest = manifest,
                                            rootDir = digestDir,
                                        )
                                } catch (error: Throwable) {
                                    AppLogger.e(
                                        TAG,
                                        "Broken theme installation at ${digestDir.absolutePath}",
                                        error,
                                    )
                                    broken += digestDir.absolutePath
                                }
                            }
                    }
            }
        installations.sortWith(
            compareBy(
                { installation -> installation.coordinate.packageId.value },
                { installation -> installation.coordinate.version.value },
            ),
        )
        return PublishedThemeCatalogV1(installations, broken)
    }

    private fun extractEntry(
        zip: ZipFile,
        entryName: String,
        targetFile: File,
    ) {
        val entry =
            requireNotNull(zip.getEntry(entryName)) {
                "Validated archive entry disappeared: $entryName"
            }
        val outputDir = requireNotNull(targetFile.parentFile)
        outputDir.mkdirs()
        if (!targetFile.canonicalPath.startsWith(outputDir.canonicalPath + File.separator)) {
            throw ThemePackageInstallException(
                "Refusing to extract outside the installation root: $entryName",
            )
        }
        zip.getInputStream(entry).use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private const val TAG = "ThemePackagePublication"
}

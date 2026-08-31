package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal data class PublishedThemeInstallationV2(
    val coordinate: ThemePackageCoordinateV2,
    val manifest: ThemePackageManifestV2,
    val rootDir: File,
)

internal data class PublishedThemeCatalogV2(
    val installations: List<PublishedThemeInstallationV2>,
    val brokenInstallations: List<String>,
)

/** Publishes validated V2 archives by exact content coordinate into an immutable installation. */
internal object ThemePackagePublicationV2 {
    private val manifestJson = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    fun installationDir(
        root: File,
        coordinate: ThemePackageCoordinateV2,
    ): File =
        File(
            root,
            "${coordinate.packageId.value}/${coordinate.version.value}/${coordinate.archiveSha256.value}",
        )

    fun publish(
        validatedArchive: File,
        validated: ThemePackageValidatedArchiveV2,
        root: File,
    ): ThemePackageCoordinateV2 {
        val coordinate = validated.manifest.coordinateFor(validated.archiveSha256)
        val target = installationDir(root, coordinate)
        if (target.exists()) return coordinate

        root.mkdirs()
        val targetParent = requireNotNull(target.parentFile)
        if (!targetParent.exists() && !targetParent.mkdirs()) {
            throw ThemePackageInstallExceptionV2(
                "Unable to create the theme installation directory: ${targetParent.absolutePath}",
            )
        }
        val publishing = File(root, ".publishing-${UUID.randomUUID()}")
        try {
            publishing.mkdirs()
            ZipFile(validatedArchive).use { zip ->
                extractEntry(
                    zip = zip,
                    entryName = THEME_PACKAGE_MANIFEST_ENTRY_V2,
                    targetFile = File(publishing, THEME_PACKAGE_MANIFEST_ENTRY_V2),
                )
                validated.manifest.assets.forEach { asset ->
                    extractEntry(zip, asset.path, File(publishing, asset.path))
                }
            }
            if (!publishing.renameTo(target)) {
                throw ThemePackageInstallExceptionV2(
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
        coordinate: ThemePackageCoordinateV2,
    ): Boolean {
        val target = installationDir(root, coordinate)
        if (!target.exists()) return false
        target.deleteRecursively()
        target.parentFile?.takeIf { directory -> directory.list()?.isEmpty() == true }?.delete()
        target.parentFile?.parentFile?.takeIf { directory -> directory.list()?.isEmpty() == true }?.delete()
        return true
    }

    fun catalog(root: File): PublishedThemeCatalogV2 {
        if (!root.exists()) return PublishedThemeCatalogV2(emptyList(), emptyList())

        val installations = mutableListOf<PublishedThemeInstallationV2>()
        val broken = mutableListOf<String>()
        root.listFiles()
            ?.filter(File::isDirectory)
            ?.forEach { packageDir ->
                packageDir.listFiles()
                    ?.filter(File::isDirectory)
                    ?.forEach { versionDir ->
                        versionDir.listFiles()
                            ?.filter(File::isDirectory)
                            ?.forEach { digestDir ->
                                val manifestFile = File(digestDir, THEME_PACKAGE_MANIFEST_ENTRY_V2)
                                if (!manifestFile.isFile) {
                                    broken += digestDir.absolutePath
                                    return@forEach
                                }
                                try {
                                    val manifest =
                                        manifestJson.decodeFromString<ThemePackageManifestV2>(
                                            manifestFile.readText(Charsets.UTF_8),
                                        )
                                    installations +=
                                        PublishedThemeInstallationV2(
                                            coordinate =
                                                ThemePackageCoordinateV2(
                                                    packageId = ThemePackageIdV2(manifest.packageId),
                                                    version = ThemePackageVersionV2(manifest.version),
                                                    archiveSha256 = ThemeArchiveSha256V2(digestDir.name),
                                                ),
                                            manifest = manifest,
                                            rootDir = digestDir,
                                        )
                                } catch (error: Throwable) {
                                    AppLogger.e(
                                        TAG,
                                        "Broken V2 theme installation at ${digestDir.absolutePath}",
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
        return PublishedThemeCatalogV2(installations, broken)
    }

    private fun extractEntry(
        zip: ZipFile,
        entryName: String,
        targetFile: File,
    ) {
        val entry = requireNotNull(zip.getEntry(entryName)) {
            "Validated archive entry disappeared: $entryName"
        }
        val outputDir = requireNotNull(targetFile.parentFile)
        outputDir.mkdirs()
        if (!targetFile.canonicalPath.startsWith(outputDir.canonicalPath + File.separator)) {
            throw ThemePackageInstallExceptionV2(
                "Refusing to extract outside the installation root: $entryName",
            )
        }
        zip.getInputStream(entry).use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private const val TAG = "ThemePackagePublicationV2"
}

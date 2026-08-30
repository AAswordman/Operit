package com.ai.assistance.operit.data.theme.packages

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemePackageArchiveAndPublicationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    private val pngBytes =
        byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I'.code.toByte(),
            'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
        )

    private fun minimalManifest(): ThemePackageManifestV1 =
        ThemePackageManifestV1(
            schemaVersion = THEME_PACKAGE_SCHEMA_VERSION,
            packageId = "author.sample",
            version = "1.0.0",
            displayName = ThemePackageLocalizedTextV1(values = mapOf("*" to "Sample")),
            capabilities = ThemePackageCapabilitiesV1(hostSurfaces = setOf("main"), scenes = setOf("chat.main")),
        )

    private fun manifestWithAsset(): Pair<ThemePackageManifestV1, ByteArray> {
        val manifest =
            minimalManifest().copy(
                assets =
                    listOf(
                        ThemePackageAssetEntryV1(
                            key = "logo",
                            path = "assets/logo.png",
                            kind = ThemeAssetKindV1.BITMAP,
                            sha256 = sha256(pngBytes),
                            byteSize = pngBytes.size.toLong(),
                        ),
                    ),
            )
        return manifest to pngBytes
    }

    private fun writeArchive(
        manifest: ThemePackageManifestV1,
        assets: Map<String, ByteArray> = emptyMap(),
        manifestEntryName: String = THEME_PACKAGE_MANIFEST_ENTRY,
        extraEntries: Map<String, ByteArray> = emptyMap(),
        rawManifestJson: String? = null,
    ): File {
        val archive = tmp.newFile("test.otheme")
        ZipOutputStream(archive.outputStream()).use { zip ->
            val manifestJson = rawManifestJson ?: json.encodeToString(manifest)
            zip.putNextEntry(ZipEntry(manifestEntryName))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            assets.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            extraEntries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return archive
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> String.format(Locale.US, "%02x", byte.toInt() and 0xff) }

    @Test
    fun minimalValidArchivePassesValidation() {
        val archive = writeArchive(minimalManifest())
        val validated = ThemePackageArchiveValidatorV1.validate(archive)

        assertEquals("author.sample", validated.manifest.packageId)
        assertEquals("1.0.0", validated.manifest.version)
        assertEquals(
            sha256(archive.readBytes()),
            validated.archiveSha256.value,
        )
    }

    @Test
    fun archiveWithMatchingAssetPassesValidation() {
        val (manifest, bytes) = manifestWithAsset()
        val archive = writeArchive(manifest, assets = mapOf("assets/logo.png" to bytes))

        val validated = ThemePackageArchiveValidatorV1.validate(archive)

        assertEquals(1, validated.manifest.assets.size)
    }

    @Test
    fun missingManifestEntryIsRejected() {
        val archive = writeArchive(minimalManifest(), manifestEntryName = "nested/theme.json")

        assertThrows(ThemePackageArchiveValidationException::class.java) {
            ThemePackageArchiveValidatorV1.validate(archive)
        }
    }

    @Test
    fun unknownManifestFieldIsRejected() {
        val archive = writeArchive(minimalManifest(), rawManifestJson = "{\"schemaVersion\":1,\"extra\":true}")

        assertThrows(ThemePackageArchiveValidationException::class.java) {
            ThemePackageArchiveValidatorV1.validate(archive)
        }
    }

    @Test
    fun digestMismatchIsRejected() {
        val archive = writeArchive(minimalManifest())

        assertThrows(ThemePackageArchiveValidationException::class.java) {
            ThemePackageArchiveValidatorV1.validate(archive, expectedSha256 = "ab".repeat(32))
        }
    }

    @Test
    fun assetDigestMismatchIsRejected() {
        val (manifest, bytes) = manifestWithAsset()
        val tampered = manifest.copy(
            assets = manifest.assets.map { asset ->
                asset.copy(sha256 = "00".repeat(32))
            },
        )
        val archive = writeArchive(tampered, assets = mapOf("assets/logo.png" to bytes))

        assertThrows(ThemePackageArchiveValidationException::class.java) {
            ThemePackageArchiveValidatorV1.validate(archive)
        }
    }

    @Test
    fun wrongMagicAssetIsRejected() {
        val (manifest, _) = manifestWithAsset()
        val textBytes = "not a bitmap".toByteArray(Charsets.UTF_8)
        val fixed =
            manifest.copy(
                assets =
                    manifest.assets.map { asset ->
                        asset.copy(sha256 = sha256(textBytes), byteSize = textBytes.size.toLong())
                    },
            )
        val archive = writeArchive(fixed, assets = mapOf("assets/logo.png" to textBytes))

        assertThrows(ThemePackageArchiveValidationException::class.java) {
            ThemePackageArchiveValidatorV1.validate(archive)
        }
    }

    @Test
    fun pathTraversalEntryIsRejected() {
        val archive = tmp.newFile("traversal.otheme")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }

        assertThrows(ThemePackageArchiveValidationException::class.java) {
            ThemePackageArchiveValidatorV1.validate(archive)
        }
    }

    @Test
    fun futureSchemaVersionIsRejected() {
        val manifest = minimalManifest().copy(schemaVersion = 2)

        assertThrows(IllegalArgumentException::class.java) {
            writeArchive(manifest)
        }
    }

    @Test
    fun publicationIsContentAddressedAndIdempotent() {
        val (manifest, bytes) = manifestWithAsset()
        val archive = writeArchive(manifest, assets = mapOf("assets/logo.png" to bytes))
        val validated = ThemePackageArchiveValidatorV1.validate(archive)
        val root = tmp.newFolder("installed")

        val coordinate =
            ThemePackagePublicationV1.publish(
                validatedArchive = archive,
                validated = validated,
                root = root,
            )
        val publishedDir =
            File(root, "author.sample/1.0.0/${validated.archiveSha256.value}")
        assertTrue(File(publishedDir, THEME_PACKAGE_MANIFEST_ENTRY).isFile)
        assertTrue(File(publishedDir, "assets/logo.png").readBytes().contentEquals(bytes))

        // Re-publishing the same digest is idempotent.
        val again =
            ThemePackagePublicationV1.publish(
                validatedArchive = archive,
                validated = validated,
                root = root,
            )
        assertEquals(coordinate, again)

        val catalog = ThemePackagePublicationV1.catalog(root)
        assertEquals(1, catalog.installations.size)
        assertEquals(coordinate, catalog.installations.single().coordinate)
        assertTrue(catalog.brokenInstallations.isEmpty())
    }

    @Test
    fun uninstallRemovesTheInstallation() {
        val (manifest, bytes) = manifestWithAsset()
        val archive = writeArchive(manifest, assets = mapOf("assets/logo.png" to bytes))
        val validated = ThemePackageArchiveValidatorV1.validate(archive)
        val root = tmp.newFolder("installed2")
        val coordinate =
            ThemePackagePublicationV1.publish(archive, validated, root)

        assertTrue(ThemePackagePublicationV1.uninstall(root, coordinate))
        assertTrue(ThemePackagePublicationV1.catalog(root).installations.isEmpty())
    }
}

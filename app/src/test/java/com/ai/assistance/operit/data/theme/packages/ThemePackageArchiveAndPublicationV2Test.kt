package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneHostSlotNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneSlotIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneVersionV1
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

class ThemePackageArchiveAndPublicationV2Test {
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

    private val tokenSet =
        ThemeSceneTokenSetV1(
            tokens =
                mapOf(
                    "color.background" to
                        ThemeSceneTokenValueV1.ColorToken(0xFF111111, 0xFFEEEEEE),
                ),
        )

    private fun chatMainScene(): com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1 =
        com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1(
            sceneId = ThemeSceneIdV1("chat.main"),
            version = ThemeSceneVersionV1(major = 1, minor = 0),
            rootNode =
                ThemeSceneStageNodeV1(
                    nodeId = ThemeSceneNodeIdV1("root"),
                    backgroundColorToken =
                        com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1("color.background"),
                    children =
                        listOf(
                            "configuration_gate",
                            "header",
                            "transcript",
                            "composer",
                            "classic_settings_rail",
                            "overlay_stack",
                        ).map { slot ->
                            ThemeSceneHostSlotNodeV1(
                                nodeId = ThemeSceneNodeIdV1("${slot}_slot"),
                                slotId = ThemeSceneSlotIdV1(slot),
                            )
                        },
                ),
        )

    internal fun minimalManifest(): ThemePackageManifestV2 =
        ThemePackageManifestV2(
            schemaVersion = THEME_PACKAGE_SCHEMA_VERSION_V2,
            packageId = "author.sample",
            version = "1.0.0",
            displayName = ThemePackageLocalizedTextV2(values = mapOf("*" to "Sample")),
            tokens = tokenSet,
            scenes = listOf(chatMainScene()),
            surfaces =
                listOf(
                    ThemeSurfaceImplementationV2(
                        surfaceId = "chat.main",
                        kind = ThemeSurfaceImplementationKindV2.SCENE,
                        sceneId = "chat.main",
                    ),
                ),
        )

    private fun manifestWithAsset(): Pair<ThemePackageManifestV2, ByteArray> {
        val manifest =
            minimalManifest().copy(
                assets =
                    listOf(
                        ThemePackageAssetEntryV2(
                            key = "logo",
                            path = "assets/logo.png",
                            kind = ThemeAssetKindV2.BITMAP,
                            sha256 = sha256(pngBytes),
                            byteSize = pngBytes.size.toLong(),
                        ),
                    ),
            )
        return manifest to pngBytes
    }

    private fun writeArchive(
        manifest: ThemePackageManifestV2,
        assets: Map<String, ByteArray> = emptyMap(),
        manifestEntryName: String = THEME_PACKAGE_MANIFEST_ENTRY_V2,
        extraEntries: Map<String, ByteArray> = emptyMap(),
        rawManifestJson: String? = null,
        zipComment: String? = THEME_PACKAGE_ZIP_COMMENT_V2,
    ): File {
        val archive = tmp.newFile("test.otheme")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.setComment(zipComment)
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
        val validated = ThemePackageArchiveValidatorV2.validate(archive)

        assertEquals("author.sample", validated.manifest.packageId)
        assertEquals("1.0.0", validated.manifest.version)
        assertEquals(sha256(archive.readBytes()), validated.archiveSha256.value)
    }

    @Test
    fun archiveWithoutZipCommentIsRejected() {
        val archive = writeArchive(minimalManifest(), zipComment = null)

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun archiveWithMatchingAssetPassesValidation() {
        val (manifest, bytes) = manifestWithAsset()
        val archive = writeArchive(manifest, assets = mapOf("assets/logo.png" to bytes))

        val validated = ThemePackageArchiveValidatorV2.validate(archive)

        assertEquals(1, validated.manifest.assets.size)
    }

    @Test
    fun missingManifestEntryIsRejected() {
        val archive = writeArchive(minimalManifest(), manifestEntryName = "nested/theme.json")

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun unknownManifestFieldIsRejected() {
        val archive =
            writeArchive(minimalManifest(), rawManifestJson = "{\"schemaVersion\":2,\"extra\":true}")

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun digestMismatchIsRejected() {
        val archive = writeArchive(minimalManifest())

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive, expectedSha256 = "ab".repeat(32))
        }
    }

    @Test
    fun assetDigestMismatchIsRejected() {
        val (manifest, bytes) = manifestWithAsset()
        val tampered =
            manifest.copy(
                assets = manifest.assets.map { asset -> asset.copy(sha256 = "00".repeat(32)) },
            )
        val archive = writeArchive(tampered, assets = mapOf("assets/logo.png" to bytes))

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
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

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
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

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun futureSchemaVersionIsRejected() {
        val rawManifest =
            json.encodeToString(minimalManifest())
                .replace("\"schemaVersion\":2", "\"schemaVersion\":3")
        val archive = writeArchive(minimalManifest(), rawManifestJson = rawManifest)

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun unsupportedSurfaceDeclarationIsRejected() {
        val manifest =
            minimalManifest().copy(
                surfaces =
                    listOf(
                        ThemeSurfaceImplementationV2(
                            surfaceId = "chat.unknown_surface",
                            kind = ThemeSurfaceImplementationKindV2.TEMPLATE,
                        ),
                    ),
            )
        val archive = writeArchive(manifest)

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun sceneSurfaceWithoutSceneIsRejected() {
        val manifest =
            minimalManifest().copy(
                surfaces =
                    listOf(
                        ThemeSurfaceImplementationV2(
                            surfaceId = "chat.main",
                            kind = ThemeSurfaceImplementationKindV2.SCENE,
                            sceneId = "app.shell",
                        ),
                    ),
            )
        val archive = writeArchive(manifest)

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun materialProjectionWithUnknownLocalTokenIsRejected() {
        val manifest =
            minimalManifest().copy(
                presentation =
                    ThemePackagePresentationPatchV2(
                        material =
                            ThemeMaterialProjectionV2(
                                colors =
                                    ThemeMaterialColorSchemeV2
                                        .uniform("color.background")
                                        .copy(primary = "color.missing"),
                                typography = ThemeTypographyV2(),
                                shapes = ThemeShapesV2(2f, 4f, 8f, 16f, 28f),
                            ),
                    ),
            )
        val archive = writeArchive(manifest)

        assertThrows(ThemePackageArchiveValidationExceptionV2::class.java) {
            ThemePackageArchiveValidatorV2.validate(archive)
        }
    }

    @Test
    fun publicationIsContentAddressedAndIdempotent() {
        val (manifest, bytes) = manifestWithAsset()
        val archive = writeArchive(manifest, assets = mapOf("assets/logo.png" to bytes))
        val validated = ThemePackageArchiveValidatorV2.validate(archive)
        val root = tmp.newFolder("installed")

        val coordinate =
            ThemePackagePublicationV2.publish(
                validatedArchive = archive,
                validated = validated,
                root = root,
            )
        val publishedDir = File(root, "author.sample/1.0.0/${validated.archiveSha256.value}")
        assertTrue(File(publishedDir, THEME_PACKAGE_MANIFEST_ENTRY_V2).isFile)
        assertTrue(File(publishedDir, "assets/logo.png").readBytes().contentEquals(bytes))

        // Re-publishing the same digest is idempotent.
        val again =
            ThemePackagePublicationV2.publish(
                validatedArchive = archive,
                validated = validated,
                root = root,
            )
        assertEquals(coordinate, again)

        val catalog = ThemePackagePublicationV2.catalog(root)
        assertEquals(1, catalog.installations.size)
        assertEquals(coordinate, catalog.installations.single().coordinate)
        assertTrue(catalog.brokenInstallations.isEmpty())
    }

    @Test
    fun uninstallRemovesTheInstallation() {
        val (manifest, bytes) = manifestWithAsset()
        val archive = writeArchive(manifest, assets = mapOf("assets/logo.png" to bytes))
        val validated = ThemePackageArchiveValidatorV2.validate(archive)
        val root = tmp.newFolder("installed2")
        val coordinate = ThemePackagePublicationV2.publish(archive, validated, root)

        assertTrue(ThemePackagePublicationV2.uninstall(root, coordinate))
        assertTrue(ThemePackagePublicationV2.catalog(root).installations.isEmpty())
    }
}

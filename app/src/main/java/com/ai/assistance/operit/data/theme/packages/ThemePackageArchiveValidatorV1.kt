package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneGridNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneIssueCodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNineSliceNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeScenePathNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTextNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.render.parseThemeScenePathCommands
import com.ai.assistance.operit.ui.theme.scene.validateThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.validateThemeSceneV1
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class ThemePackageArchiveValidationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal data class ThemePackageValidatedArchiveV1(
    val manifest: ThemePackageManifestV1,
    val archiveSha256: ThemeArchiveSha256V1,
)

/**
 * Validates one `.otheme` archive end to end: container identity, entry hygiene, strict
 * manifest decoding, per-asset digest and magic-number checks, and scene/token contracts.
 * Every rejection is an explicit [ThemePackageArchiveValidationException]; nothing is repaired.
 */
internal object ThemePackageArchiveValidatorV1 {
    private const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024
    private const val MAX_ENTRIES = 512
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
    private const val MAX_SINGLE_ENTRY_BYTES = 48L * 1024 * 1024
    private const val MAX_COMPRESSION_RATIO = 100

    fun validate(
        file: File,
        expectedSha256: String? = null,
    ): ThemePackageValidatedArchiveV1 {
        if (!file.isFile || !file.canRead()) {
            throw ThemePackageArchiveValidationException("Theme package is not a readable file: ${file.absolutePath}")
        }
        if (file.length() > MAX_ARCHIVE_BYTES) {
            throw ThemePackageArchiveValidationException("Theme package exceeds the ${MAX_ARCHIVE_BYTES / 1024 / 1024} MB archive limit.")
        }

        val archiveDigest = sha256Hex(file)
        if (expectedSha256 != null && !expectedSha256.equals(archiveDigest, ignoreCase = true)) {
            throw ThemePackageArchiveValidationException(
                "Theme package digest mismatch: expected $expectedSha256 but found $archiveDigest.",
            )
        }

        ZipFile(file).use { zip ->
            validateEntries(zip)
            val manifestEntry =
                zip.getEntry(THEME_PACKAGE_MANIFEST_ENTRY)
                    ?: throw ThemePackageArchiveValidationException(
                        "Theme package has no root $THEME_PACKAGE_MANIFEST_ENTRY entry.",
                    )
            val manifest =
                try {
                    val manifestJson = MANIFEST_JSON
                    zip.getInputStream(manifestEntry).use { input ->
                        manifestJson.decodeFromString<ThemePackageManifestV1>(
                            input.readBytes().toString(Charsets.UTF_8),
                        )
                    }
                } catch (error: Throwable) {
                    throw ThemePackageArchiveValidationException(
                        "Theme package manifest is not a valid theme manifest: ${error.message}",
                        error,
                    )
                }

            validateAssets(zip, manifest)
            validateSceneAssetReferences(manifest)
            validateSceneAndTokenContracts(manifest)
            validateBasis(manifest)

            return ThemePackageValidatedArchiveV1(
                manifest = manifest,
                archiveSha256 = ThemeArchiveSha256V1(archiveDigest),
            )
        }
    }

    private fun validateEntries(zip: ZipFile) {
        val entries = zip.entries().toList()
        if (entries.size > MAX_ENTRIES) {
            throw ThemePackageArchiveValidationException("Theme package declares more than $MAX_ENTRIES entries.")
        }
        var totalUncompressed = 0L
        entries.forEach { entry ->
            validateEntryPath(entry)
            if (!entry.isDirectory) {
                val size = entry.size
                if (size > MAX_SINGLE_ENTRY_BYTES) {
                    throw ThemePackageArchiveValidationException(
                        "Theme package entry ${entry.name} exceeds the single-entry size limit.",
                    )
                }
                val compressed = entry.compressedSize
                if (compressed > 0 && size / compressed > MAX_COMPRESSION_RATIO) {
                    throw ThemePackageArchiveValidationException(
                        "Theme package entry ${entry.name} exceeds the compression ratio limit.",
                    )
                }
                totalUncompressed += size
            }
        }
        if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw ThemePackageArchiveValidationException("Theme package exceeds the total uncompressed size limit.")
        }
        val manifestEntries =
            entries.filter { entry -> normalizeEntryName(entry.name) == THEME_PACKAGE_MANIFEST_ENTRY }
        if (manifestEntries.isEmpty()) {
            throw ThemePackageArchiveValidationException(
                "Theme package has no root $THEME_PACKAGE_MANIFEST_ENTRY entry.",
            )
        }
    }

    private fun validateEntryPath(entry: ZipEntry) {
        val name = entry.name
        if (name.startsWith("/") || name.contains("\\") || name.contains(":")) {
            throw ThemePackageArchiveValidationException("Theme package entry path is not relative and portable: $name")
        }
        val segments = name.split('/')
        if (segments.any { segment -> segment == ".." }) {
            throw ThemePackageArchiveValidationException("Theme package entry path escapes the archive root: $name")
        }
    }

    private fun validateAssets(
        zip: ZipFile,
        manifest: ThemePackageManifestV1,
    ) {
        manifest.assets.forEach { asset ->
            val entry =
                zip.getEntry(asset.path)
                    ?: zip.entries().toList().firstOrNull { candidate ->
                        normalizeEntryName(candidate.name) == asset.path
                    }
                    ?: throw ThemePackageArchiveValidationException(
                        "Theme asset ${asset.key} references missing archive entry: ${asset.path}",
                    )
            val bytes =
                zip.getInputStream(entry).use { input -> input.readBytes() }
            if (bytes.size.toLong() != asset.byteSize) {
                throw ThemePackageArchiveValidationException(
                    "Theme asset ${asset.key} byte size does not match the manifest.",
                )
            }
            val digest =
                MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { byte -> String.format(Locale.US, "%02x", byte.toInt() and 0xff) }
            if (digest != asset.sha256) {
                throw ThemePackageArchiveValidationException(
                    "Theme asset ${asset.key} digest does not match the manifest.",
                )
            }
            validateAssetMagic(asset, bytes)
        }
    }

    private fun validateAssetMagic(
        asset: ThemePackageAssetEntryV1,
        bytes: ByteArray,
    ) {
        val failure = { reason: String ->
            ThemePackageArchiveValidationException("Theme asset ${asset.key} ($reason) does not match kind ${asset.kind}.")
        }
        when (asset.kind) {
            ThemeAssetKindV1.BITMAP,
            ThemeAssetKindV1.NINE_SLICE,
            ->
                if (!isBitmap(bytes)) throw failure("content")

            ThemeAssetKindV1.FONT ->
                if (!isFont(bytes)) throw failure("content")

            ThemeAssetKindV1.PATH ->
                try {
                    parseThemeScenePathCommands(bytes.toString(Charsets.UTF_8))
                } catch (error: Throwable) {
                    throw failure("path data")
                }
        }
    }

    private fun validateSceneAssetReferences(manifest: ThemePackageManifestV1) {
        val kindsByKey = manifest.assets.associate { asset -> asset.key to asset.kind }
        manifest.scenes.forEach { scene ->
            collectAssetReferences(scene.rootNode).forEach { (assetKey, expectedKind) ->
                val kind =
                    kindsByKey[assetKey]
                        ?: throw ThemePackageArchiveValidationException(
                            "Scene ${scene.sceneId.value} references unknown asset: $assetKey",
                        )
                if (kind != expectedKind) {
                    throw ThemePackageArchiveValidationException(
                        "Scene ${scene.sceneId.value} uses asset $assetKey as $expectedKind but it is declared as $kind.",
                    )
                }
            }
        }
    }

    private fun validateSceneAndTokenContracts(manifest: ThemePackageManifestV1) {
        validateThemeSceneTokenSetV1(manifest.tokens).forEach { issue ->
            throw ThemePackageArchiveValidationException("Theme token pool is invalid: [$issue.code] ${issue.message}")
        }
        validateFontTokenReferences(manifest)
        val tokenIssues = manifest.scenes.flatMap { scene ->
            validateThemeSceneV1(
                definition = scene,
                contracts = com.ai.assistance.operit.ui.theme.scene.ThemeSceneCatalogV1.contracts,
            )
        }
        tokenIssues.firstOrNull { issue -> issue.code != ThemeSceneIssueCodeV1.UNKNOWN_SCENE }
            ?.let { issue ->
                throw ThemePackageArchiveValidationException("Theme scene is invalid: [$issue.code] ${issue.message}")
            }
        val unknownScenes =
            tokenIssues.filter { issue -> issue.code == ThemeSceneIssueCodeV1.UNKNOWN_SCENE }
        if (unknownScenes.isNotEmpty()) {
            throw ThemePackageArchiveValidationException(
                "Theme package targets scenes the host does not register: " +
                    manifest.scenes.map { it.sceneId.value }.joinToString(),
            )
        }
    }

    private fun validateFontTokenReferences(manifest: ThemePackageManifestV1) {
        val fontKeys =
            manifest.assets
                .filter { asset -> asset.kind == ThemeAssetKindV1.FONT }
                .map { asset -> asset.key }
                .toSet()
        manifest.tokens.tokens.values.forEach { token ->
            if (token is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1.TextStyleToken) {
                val fontAsset = token.fontAsset?.value
                if (fontAsset != null && fontAsset !in fontKeys) {
                    throw ThemePackageArchiveValidationException(
                        "Text style token references unknown font asset: $fontAsset",
                    )
                }
            }
        }
    }

    private fun validateBasis(manifest: ThemePackageManifestV1) {
        val basis = manifest.basis ?: return
        if (basis.packageId.value == manifest.packageId) {
            throw ThemePackageArchiveValidationException("Theme package cannot use itself as its basis.")
        }
    }

    private fun collectAssetReferences(
        node: ThemeSceneNodeV1,
    ): List<Pair<String, ThemeAssetKindV1>> {
        val references = mutableListOf<Pair<String, ThemeAssetKindV1>>()
        fun visit(current: ThemeSceneNodeV1) {
            when (current) {
                is ThemeSceneImageNodeV1 ->
                    references += current.assetId.value to ThemeAssetKindV1.BITMAP

                is ThemeSceneNineSliceNodeV1 ->
                    references += current.assetId.value to ThemeAssetKindV1.NINE_SLICE

                is ThemeScenePathNodeV1 ->
                    references += current.assetId.value to ThemeAssetKindV1.PATH

                is ThemeSceneTextNodeV1 -> Unit

                else -> Unit
            }
            childrenOf(current).forEach(::visit)
        }
        visit(node)
        return references
    }

    private fun childrenOf(node: ThemeSceneNodeV1): List<ThemeSceneNodeV1> =
        when (node) {
            is ThemeSceneStageNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneLayerNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneRowNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneColumnNodeV1 -> node.children
            is ThemeSceneGridNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneFrameNodeV1 -> listOf(node.child)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTransformNodeV1 -> listOf(node.child)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneSurfaceNodeV1 ->
                node.child?.let(::listOf) ?: emptyList()

            is ThemeSceneNineSliceNodeV1 -> node.child?.let(::listOf) ?: emptyList()
            else -> emptyList()
        }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    private fun isBitmap(bytes: ByteArray): Boolean = isPng(bytes) || isJpeg(bytes) || isWebp(bytes)

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()

    private fun isFont(bytes: ByteArray): Boolean =
        (bytes.size >= 4 &&
            bytes[0] == 0x00.toByte() &&
            bytes[1] == 0x01.toByte() &&
            bytes[2] == 0x00.toByte() &&
            bytes[3] == 0x00.toByte()) ||
            (bytes.size >= 4 &&
                bytes[0] == 'O'.code.toByte() &&
                bytes[1] == 'T'.code.toByte() &&
                bytes[2] == 'T'.code.toByte() &&
                bytes[3] == 'O'.code.toByte())

    private val MANIFEST_JSON = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    private fun normalizeEntryName(name: String): String = name.trimStart('.', '/')
}

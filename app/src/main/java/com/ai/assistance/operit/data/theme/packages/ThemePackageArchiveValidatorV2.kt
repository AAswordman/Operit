package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneGridNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneIssueCodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNineSliceNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeScenePathNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTextNodeV1
import com.ai.assistance.operit.ui.theme.scene.validateThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.validateThemeSceneV1
import com.ai.assistance.operit.ui.theme.scene.render.parseThemeScenePathCommands
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal class ThemePackageArchiveValidationExceptionV2(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal data class ThemePackageValidatedArchiveV2(
    val manifest: ThemePackageManifestV2,
    val archiveSha256: ThemeArchiveSha256V2,
)

/**
 * Rejects malformed V2 archives before installation. This is intentionally strict: no manifest,
 * asset, scene, token, or presentation data is repaired at import time.
 */
internal object ThemePackageArchiveValidatorV2 {
    private const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024
    private const val MAX_ENTRIES = 512
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
    private const val MAX_SINGLE_ENTRY_BYTES = 48L * 1024 * 1024
    private const val MAX_COMPRESSION_RATIO = 100

    fun validate(
        file: File,
        expectedSha256: String? = null,
    ): ThemePackageValidatedArchiveV2 {
        if (!file.isFile || !file.canRead()) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package is not a readable file: ${file.absolutePath}",
            )
        }
        if (file.length() > MAX_ARCHIVE_BYTES) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package exceeds the ${MAX_ARCHIVE_BYTES / 1024 / 1024} MB archive limit.",
            )
        }

        val archiveDigest = sha256Hex(file)
        if (expectedSha256 != null && !expectedSha256.equals(archiveDigest, ignoreCase = true)) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package digest mismatch: expected $expectedSha256 but found $archiveDigest.",
            )
        }

        ZipFile(file).use { zip ->
            if (zip.comment != THEME_PACKAGE_ZIP_COMMENT_V2) {
                throw ThemePackageArchiveValidationExceptionV2(
                    "Theme package ZIP comment must be $THEME_PACKAGE_ZIP_COMMENT_V2.",
                )
            }
            validateEntries(zip)
            val manifestEntry =
                zip.getEntry(THEME_PACKAGE_MANIFEST_ENTRY_V2)
                    ?: throw ThemePackageArchiveValidationExceptionV2(
                        "Theme package has no root $THEME_PACKAGE_MANIFEST_ENTRY_V2 entry.",
                    )
            val manifest = decodeManifest(zip, manifestEntry)

            validateAssets(zip, manifest)
            validateSceneAssetReferences(manifest)
            validateSceneAndTokenContracts(manifest)
            validatePresentationShape(manifest)
            validateSurfaceDeclarations(manifest)

            return ThemePackageValidatedArchiveV2(
                manifest = manifest,
                archiveSha256 = ThemeArchiveSha256V2(archiveDigest),
            )
        }
    }

    private fun decodeManifest(zip: ZipFile, entry: ZipEntry): ThemePackageManifestV2 =
        try {
            zip.getInputStream(entry).use { input ->
                MANIFEST_JSON.decodeFromString<ThemePackageManifestV2>(
                    input.readBytes().toString(Charsets.UTF_8),
                )
            }
        } catch (error: Throwable) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package manifest is not a valid V2 theme manifest: ${error.message}",
                error,
            )
        }

    private fun validateEntries(zip: ZipFile) {
        val entries = zip.entries().toList()
        if (entries.size > MAX_ENTRIES) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package declares more than $MAX_ENTRIES entries.",
            )
        }
        var totalUncompressed = 0L
        entries.forEach { entry ->
            validateEntryPath(entry)
            if (!entry.isDirectory) {
                val size = entry.size
                if (size < 0 || size > MAX_SINGLE_ENTRY_BYTES) {
                    throw ThemePackageArchiveValidationExceptionV2(
                        "Theme package entry ${entry.name} exceeds the single-entry size limit.",
                    )
                }
                val compressed = entry.compressedSize
                if (compressed > 0 && size / compressed > MAX_COMPRESSION_RATIO) {
                    throw ThemePackageArchiveValidationExceptionV2(
                        "Theme package entry ${entry.name} exceeds the compression ratio limit.",
                    )
                }
                totalUncompressed += size
            }
        }
        if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package exceeds the total uncompressed size limit.",
            )
        }
        if (entries.count { entry -> normalizeEntryName(entry.name) == THEME_PACKAGE_MANIFEST_ENTRY_V2 } != 1) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package must contain exactly one root $THEME_PACKAGE_MANIFEST_ENTRY_V2 entry.",
            )
        }
    }

    private fun validateEntryPath(entry: ZipEntry) {
        val name = entry.name
        if (name.startsWith("/") || name.contains("\\") || name.contains(":")) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package entry path is not relative and portable: $name",
            )
        }
        if (name.split('/').any { segment -> segment == ".." }) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package entry path escapes the archive root: $name",
            )
        }
    }

    private fun validateAssets(
        zip: ZipFile,
        manifest: ThemePackageManifestV2,
    ) {
        manifest.assets.forEach { asset ->
            val entry =
                zip.getEntry(asset.path)
                    ?: throw ThemePackageArchiveValidationExceptionV2(
                        "Theme asset ${asset.key} references missing archive entry: ${asset.path}",
                    )
            val bytes = zip.getInputStream(entry).use { input -> input.readBytes() }
            if (bytes.size.toLong() != asset.byteSize) {
                throw ThemePackageArchiveValidationExceptionV2(
                    "Theme asset ${asset.key} byte size does not match the manifest.",
                )
            }
            val digest = sha256Hex(bytes)
            if (digest != asset.sha256) {
                throw ThemePackageArchiveValidationExceptionV2(
                    "Theme asset ${asset.key} digest does not match the manifest.",
                )
            }
            validateAssetMagic(asset, bytes)
        }
    }

    private fun validateAssetMagic(
        asset: ThemePackageAssetEntryV2,
        bytes: ByteArray,
    ) {
        val failure = { reason: String ->
            ThemePackageArchiveValidationExceptionV2(
                "Theme asset ${asset.key} ($reason) does not match kind ${asset.kind}.",
            )
        }
        when (asset.kind) {
            ThemeAssetKindV2.BITMAP,
            ThemeAssetKindV2.NINE_SLICE,
            -> if (!isBitmap(bytes)) throw failure("content")

            ThemeAssetKindV2.FONT -> if (!isFont(bytes)) throw failure("content")

            ThemeAssetKindV2.PATH ->
                try {
                    parseThemeScenePathCommands(bytes.toString(Charsets.UTF_8))
                } catch (error: Throwable) {
                    throw failure("path data")
                }
        }
    }

    private fun validateSceneAssetReferences(manifest: ThemePackageManifestV2) {
        val kindsByKey = manifest.assets.associate { asset -> asset.key to asset.kind }
        manifest.scenes.forEach { scene ->
            collectAssetReferences(scene.rootNode).forEach { (assetKey, expectedKind) ->
                val kind = kindsByKey[assetKey]
                if (kind == null && manifest.basis != null) return@forEach
                if (kind == null) {
                    throw ThemePackageArchiveValidationExceptionV2(
                        "Scene ${scene.sceneId.value} references unknown asset: $assetKey",
                    )
                }
                if (kind != expectedKind) {
                    throw ThemePackageArchiveValidationExceptionV2(
                        "Scene ${scene.sceneId.value} uses asset $assetKey as $expectedKind but it is declared as $kind.",
                    )
                }
            }
        }
    }

    private fun validateSceneAndTokenContracts(manifest: ThemePackageManifestV2) {
        validateThemeSceneTokenSetV1(manifest.tokens).forEach { issue ->
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme token pool is invalid: [${issue.code}] ${issue.message}",
            )
        }
        validateFontTokenReferences(manifest)
        validateSceneTokenReferences(manifest)
        val issues =
            manifest.scenes.flatMap { scene -> validateThemeSceneV1(definition = scene) }
        issues.firstOrNull { issue -> issue.code != ThemeSceneIssueCodeV1.UNKNOWN_SCENE }?.let { issue ->
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme scene is invalid: [${issue.code}] ${issue.message}",
            )
        }
        if (issues.any { issue -> issue.code == ThemeSceneIssueCodeV1.UNKNOWN_SCENE }) {
            throw ThemePackageArchiveValidationExceptionV2(
                "Theme package targets scenes the host does not register: " +
                    manifest.scenes.map { scene -> scene.sceneId.value }.joinToString(),
            )
        }
    }

    private fun validateFontTokenReferences(manifest: ThemePackageManifestV2) {
        val fontKeys =
            manifest.assets
                .filter { asset -> asset.kind == ThemeAssetKindV2.FONT }
                .map { asset -> asset.key }
                .toSet()
        manifest.tokens.tokens.values.forEach { token ->
            if (token is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1.TextStyleToken) {
                token.fontAsset?.value?.let { fontAsset ->
                    if (fontAsset !in fontKeys && manifest.basis == null) {
                        throw ThemePackageArchiveValidationExceptionV2(
                            "Text style token references unknown font asset: $fontAsset",
                        )
                    }
                }
            }
        }
    }

    private fun validateSceneTokenReferences(manifest: ThemePackageManifestV2) {
        val tokens = manifest.tokens.tokens
        fun requireColor(tokenId: String, owner: String) {
            val token = tokens[tokenId]
            if (token == null && manifest.basis != null) return
            if (token !is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1.ColorToken) {
                throw ThemePackageArchiveValidationExceptionV2(
                    "$owner references missing or non-color token: $tokenId",
                )
            }
        }
        fun visit(node: ThemeSceneNodeV1) {
            when (node) {
                is ThemeSceneStageNodeV1 -> node.backgroundColorToken?.let { token ->
                    requireColor(token.value, "Scene")
                }

                is com.ai.assistance.operit.ui.theme.scene.ThemeSceneSurfaceNodeV1 -> {
                    node.fillToken?.let { token -> requireColor(token.value, "Scene") }
                    node.outlineToken?.let { token -> requireColor(token.value, "Scene") }
                }

                is ThemeScenePathNodeV1 -> {
                    node.fillToken?.let { token -> requireColor(token.value, "Scene") }
                    node.outlineToken?.let { token -> requireColor(token.value, "Scene") }
                }

                is ThemeSceneTextNodeV1 -> {
                    val styleToken =
                        node.styleToken
                            ?: throw ThemePackageArchiveValidationExceptionV2(
                                "Scene text node ${node.nodeId.value} has no style token.",
                            )
                    val token = tokens[styleToken.value]
                    if (token == null && manifest.basis != null) return@visit
                    if (token !is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1.TextStyleToken) {
                        throw ThemePackageArchiveValidationExceptionV2(
                            "Scene references missing or non-text-style token: ${styleToken.value}",
                        )
                    }
                }

                else -> Unit
            }
            childrenOf(node).forEach(::visit)
        }
        manifest.scenes.forEach { scene -> visit(scene.rootNode) }
    }

    private fun validatePresentationShape(manifest: ThemePackageManifestV2) {
        val localTokens = manifest.tokens.tokens
        val allowsInheritedTokens = manifest.basis != null
        manifest.presentation.material?.let { material ->
            material.colors.tokenIds().forEach { tokenId ->
                val token = localTokens[tokenId]
                if (token == null && allowsInheritedTokens) return@forEach
                if (token !is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1.ColorToken) {
                    throw ThemePackageArchiveValidationExceptionV2(
                        "Material projection references missing or non-color local token: $tokenId",
                    )
                }
            }
        }
        manifest.presentation.componentSkins.forEach { (componentId, skin) ->
            validateComponentSkinLocalTokens(
                componentId = componentId,
                skin = skin,
                tokens = localTokens,
                allowsInheritedTokens = allowsInheritedTokens,
            )
        }
    }

    private fun validateComponentSkinLocalTokens(
        componentId: String,
        skin: ThemeComponentSkinV2,
        tokens: Map<String, com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1>,
        allowsInheritedTokens: Boolean,
    ) {
        listOfNotNull(skin.normal, skin.disabled, skin.selected, skin.focused, skin.error).forEach { state ->
            listOfNotNull(state.containerToken, state.contentToken, state.outlineToken).forEach { tokenId ->
                val token = tokens[tokenId]
                if (token == null && allowsInheritedTokens) return@forEach
                if (token !is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1.ColorToken) {
                    throw ThemePackageArchiveValidationExceptionV2(
                        "Component $componentId references missing or non-color local token: $tokenId",
                    )
                }
            }
        }
    }

    private fun validateSurfaceDeclarations(manifest: ThemePackageManifestV2) {
        val supportedSurfaces = ThemeSurfaceCatalogV2.requiredDailySurfaces.map { surface -> surface.value }.toSet()
        manifest.surfaces.forEach { surface ->
            if (surface.surfaceId !in supportedSurfaces) {
                throw ThemePackageArchiveValidationExceptionV2(
                    "Theme package declares an unsupported surface: ${surface.surfaceId}",
                )
            }
        }
        val supportedComponents = ThemeComponentCatalogV2.requiredComponents.map { component -> component.value }.toSet()
        manifest.presentation.componentSkins.keys.forEach { componentId ->
            if (componentId !in supportedComponents) {
                throw ThemePackageArchiveValidationExceptionV2(
                    "Theme package declares an unsupported component skin: $componentId",
                )
            }
        }
    }

    private fun collectAssetReferences(
        node: ThemeSceneNodeV1,
    ): List<Pair<String, ThemeAssetKindV2>> {
        val references = mutableListOf<Pair<String, ThemeAssetKindV2>>()
        fun visit(current: ThemeSceneNodeV1) {
            when (current) {
                is ThemeSceneImageNodeV1 -> references += current.assetId.value to ThemeAssetKindV2.BITMAP
                is ThemeSceneNineSliceNodeV1 -> references += current.assetId.value to ThemeAssetKindV2.NINE_SLICE
                is ThemeScenePathNodeV1 -> references += current.assetId.value to ThemeAssetKindV2.PATH
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
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneScaffoldNodeV1 ->
                listOfNotNull(node.top, node.content, node.bottom, node.overlay)
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
        return digest.digest().joinToString("") { byte -> String.format(Locale.US, "%02x", byte.toInt() and 0xff) }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> String.format(Locale.US, "%02x", byte.toInt() and 0xff) }

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

    private val MANIFEST_JSON = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    private fun normalizeEntryName(name: String): String = name.trimStart('.', '/')
}

package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.ToolPkgArchiveParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ToolPkgProtection {
    const val PROTECTION_ID = "operit-protected"

    private val magic = byteArrayOf(
        'O'.code.toByte(),
        'P'.code.toByte(),
        'T'.code.toByte(),
        'P'.code.toByte(),
        'R'.code.toByte(),
        'O'.code.toByte(),
        'T'.code.toByte(),
        'A'.code.toByte()
    )

    fun isProtected(bytes: ByteArray): Boolean {
        if (bytes.size < magic.size) return false
        for (index in magic.indices) {
            if (bytes[index] != magic[index]) return false
        }
        return true
    }

    fun decryptIfNeeded(bytes: ByteArray): ByteArray {
        return if (isProtected(bytes)) ToolPkgProtectionNative.decrypt(bytes) else bytes
    }

    fun decodeUtf8(bytes: ByteArray): String {
        return decryptIfNeeded(bytes).toString(StandardCharsets.UTF_8)
    }

    fun encrypt(bytes: ByteArray): ByteArray {
        require(bytes.isNotEmpty()) { "Cannot protect empty content" }
        return if (isProtected(bytes)) bytes else ToolPkgProtectionNative.encrypt(bytes)
    }

    fun protectArtifactFile(context: Context, sourceFile: File, isToolPkg: Boolean): ByteArray {
        require(ToolPkgProtectionNative.isSecretConfigured()) {
            "ToolPkg protection secret is not configured for this build"
        }
        return ToolPkgJsAstMinifier(context).use { minifier ->
            if (isToolPkg) {
                protectToolPkgArchive(sourceFile, minifier)
            } else {
                protectScriptFile(sourceFile, minifier)
            }
        }
    }

    private fun protectScriptFile(sourceFile: File, minifier: ToolPkgJsAstMinifier): ByteArray {
        val minifiedBytes =
            astMinifyBytes(
                bytes = sourceFile.readBytes(),
                entryName = sourceFile.name,
                minifier = minifier
            )
        return encrypt(minifiedBytes)
    }

    private fun protectToolPkgArchive(sourceFile: File, minifier: ToolPkgJsAstMinifier): ByteArray {
        val manifestPreview =
            ToolPkgArchiveParser.readToolPkgManifestPreview { sourceFile.inputStream() }
                ?: throw IllegalArgumentException("manifest.hjson or manifest.json not found")
        val manifest = manifestPreview.manifest
        val manifestBasePath = manifestPreview.entryName.substringBeforeLast('/', missingDelimiterValue = "")
        val manifestEntryName =
            ToolPkgArchiveParser.normalizeZipEntryPath(manifestPreview.entryName)
                ?: throw IllegalArgumentException("Invalid toolpkg manifest entry name")
        val protectedEntryNames = linkedSetOf<String>()

        ToolPkgArchiveParser.resolveManifestRelativeZipEntryPath(manifestBasePath, manifest.main)
            ?.let(protectedEntryNames::add)
        manifest.subpackages.forEach { subpackage ->
            ToolPkgArchiveParser.resolveManifestRelativeZipEntryPath(manifestBasePath, subpackage.entry)
                ?.let(protectedEntryNames::add)
        }

        val outputBytes = ByteArrayOutputStream()
        ZipFile(sourceFile).use { archive ->
            ZipOutputStream(outputBytes).use { zipOutput ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val copiedEntry = ZipEntry(entry.name).apply {
                        time = entry.time
                        comment = entry.comment
                    }
                    zipOutput.putNextEntry(copiedEntry)
                    if (!entry.isDirectory) {
                        val normalizedName = ToolPkgArchiveParser.normalizeZipEntryPath(entry.name)
                        val originalBytes = archive.getInputStream(entry).use { it.readBytes() }
                        val outputEntryBytes =
                            when {
                                normalizedName == null -> originalBytes
                                normalizedName == manifestEntryName -> originalBytes
                                shouldAstMinifyToolPkgEntry(
                                    normalizedName = normalizedName,
                                    protectedEntryNames = protectedEntryNames
                                ) ->
                                    encrypt(
                                        astMinifyBytes(
                                            bytes = originalBytes,
                                            entryName = normalizedName,
                                            minifier = minifier
                                        )
                                    )
                                else ->
                                    encrypt(originalBytes)
                            }
                        zipOutput.write(outputEntryBytes)
                    }
                    zipOutput.closeEntry()
                }
            }
        }
        return outputBytes.toByteArray()
    }

    private fun astMinifyBytes(
        bytes: ByteArray,
        entryName: String,
        minifier: ToolPkgJsAstMinifier
    ): ByteArray {
        val source = String(bytes, StandardCharsets.UTF_8)
        val minified = astMinifySourcePreservingMetadata(source, entryName, minifier)
        return minified.toByteArray(StandardCharsets.UTF_8)
    }

    private fun astMinifySourcePreservingMetadata(
        source: String,
        entryName: String,
        minifier: ToolPkgJsAstMinifier
    ): String {
        val split = splitLeadingMetadataBlock(source)
        if (split != null) {
            val body = split.body.trim()
            require(body.isNotEmpty()) { "JavaScript body after METADATA is empty for $entryName" }
            return split.metadataBlock + minifier.minify(body, entryName)
        }
        return minifier.minify(source, entryName)
    }

    private fun splitLeadingMetadataBlock(source: String): MetadataSplit? {
        val trimmed = source.trimStart()
        val leadingWhitespaceSize = source.length - trimmed.length
        if (!trimmed.startsWith("/*")) return null

        val commentBody = trimmed.substring(2)
        val label = commentBody.trimStart()
        if (!startsWithMetadataLabel(label)) return null

        val commentEnd = trimmed.indexOf("*/")
        if (commentEnd < 0) return null

        val metadataEnd = leadingWhitespaceSize + commentEnd + 2
        return MetadataSplit(
            metadataBlock = source.substring(0, metadataEnd),
            body = source.substring(metadataEnd)
        )
    }

    private fun startsWithMetadataLabel(commentBody: String): Boolean {
        if (!commentBody.startsWith("METADATA")) return false
        val afterLabel = commentBody.substring("METADATA".length)
        val first = afterLabel.firstOrNull() ?: return true
        return first.isWhitespace() || first == '*'
    }

    private fun shouldAstMinifyToolPkgEntry(
        normalizedName: String,
        protectedEntryNames: Set<String>
    ): Boolean {
        if (protectedEntryNames.contains(normalizedName)) return true
        val extension = normalizedName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in setOf("js", "mjs", "cjs", "ts", "jsx", "tsx")
    }

    private data class MetadataSplit(
        val metadataBlock: String,
        val body: String
    )
}

internal object ToolPkgProtectionNative {
    init {
        System.loadLibrary("toolpkgprotect")
    }

    external fun isSecretConfigured(): Boolean

    external fun encrypt(bytes: ByteArray): ByteArray

    external fun decrypt(bytes: ByteArray): ByteArray
}

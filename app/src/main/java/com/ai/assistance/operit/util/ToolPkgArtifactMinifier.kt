package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.ToolPkgArchiveParser
import com.ai.assistance.operit.core.tools.packTool.ToolPkgMarketOrigin
import com.ai.assistance.operit.core.tools.packTool.ToolPkgMarketOriginCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.hjson.JsonValue
import org.json.JSONObject

/** Produces publish artifacts with optional AST minification and mandatory market provenance. */
object ToolPkgArtifactMinifier {
    internal fun processArtifactFile(
        context: Context,
        sourceFile: File,
        isToolPkg: Boolean,
        marketOrigin: ToolPkgMarketOrigin,
        minify: Boolean
    ): ByteArray {
        return ToolPkgJsAstMinifier(context).use { minifier ->
            if (isToolPkg) {
                processToolPkgArchive(sourceFile, minifier, marketOrigin, minify)
            } else {
                processScriptFile(sourceFile, minifier, marketOrigin, minify)
            }
        }
    }

    private fun processScriptFile(
        sourceFile: File,
        minifier: ToolPkgJsAstMinifier,
        marketOrigin: ToolPkgMarketOrigin,
        minify: Boolean
    ): ByteArray {
        val source = sourceFile.readText(StandardCharsets.UTF_8)
        val sourceWithOrigin = injectScriptMarketOriginIntoMetadata(source, marketOrigin)
        if (!minify) {
            return sourceWithOrigin.toByteArray(StandardCharsets.UTF_8)
        }
        return minifyJavaScriptBytes(
            sourceWithOrigin.toByteArray(StandardCharsets.UTF_8),
            sourceFile.name,
            minifier
        )
    }

    private fun processToolPkgArchive(
        sourceFile: File,
        minifier: ToolPkgJsAstMinifier,
        marketOrigin: ToolPkgMarketOrigin,
        minify: Boolean
    ): ByteArray {
        val manifestPreview =
            ToolPkgArchiveParser.readToolPkgManifestPreview { sourceFile.inputStream() }
                ?: throw IllegalArgumentException("manifest.hjson or manifest.json not found")
        val manifestBasePath = manifestPreview.entryName.substringBeforeLast('/', missingDelimiterValue = "")
        val manifestEntryName =
            ToolPkgArchiveParser.normalizeZipEntryPath(manifestPreview.entryName)
                ?: throw IllegalArgumentException("Invalid toolpkg manifest entry name")
        val mainEntryName =
            ToolPkgArchiveParser.resolveManifestRelativeZipEntryPath(
                manifestBasePath,
                manifestPreview.manifest.main
            )
                ?: throw IllegalArgumentException("manifest.main is required")
        val executableEntryNames = linkedSetOf<String>()
        val resourceEntryRoots = linkedSetOf<String>()

        executableEntryNames.add(mainEntryName)
        manifestPreview.manifest.subpackages.forEach { subpackage ->
            ToolPkgArchiveParser.resolveManifestRelativeZipEntryPath(manifestBasePath, subpackage.entry)
                ?.let(executableEntryNames::add)
        }
        manifestPreview.manifest.resources.forEach { resource ->
            ToolPkgArchiveParser.resolveManifestRelativeResourcePath(manifestBasePath, resource.path)
                ?.let(resourceEntryRoots::add)
        }
        manifestPreview.manifest.wasmModules.forEach { module ->
            ToolPkgArchiveParser.resolveManifestRelativeResourcePath(manifestBasePath, module.path)
                ?.let(resourceEntryRoots::add)
        }

        val outputBytes = ByteArrayOutputStream()
        ZipFile(sourceFile).use { archive ->
            val archiveEntries = buildList {
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    add(entries.nextElement())
                }
            }
            val reachableEntryNames =
                if (minify) {
                    // Keep the protected archive aligned with the runtime graph; copying the whole authoring tree defeats source protection and increases load cost.
                    collectReachableToolPkgEntries(
                        archive = archive,
                        entries = archiveEntries,
                        manifestEntryName = manifestEntryName,
                        executableEntryNames = executableEntryNames,
                        resourceEntryRoots = resourceEntryRoots
                    )
                } else {
                    emptySet()
                }
            ZipOutputStream(outputBytes).use { zipOutput ->
                archiveEntries.forEach { entry ->
                    val normalizedName = ToolPkgArchiveParser.normalizeZipEntryPath(entry.name)
                    if (
                        minify &&
                            (normalizedName == null || normalizedName !in reachableEntryNames)
                    ) {
                        return@forEach
                    }
                    val copiedEntry = ZipEntry(entry.name).apply {
                        time = entry.time
                        comment = entry.comment
                    }
                    zipOutput.putNextEntry(copiedEntry)
                    if (!entry.isDirectory) {
                        val originalBytes = archive.getInputStream(entry).use { input -> input.readBytes() }
                        val outputEntryBytes =
                            when {
                                normalizedName == mainEntryName -> {
                                    val mainOrigin =
                                        ToolPkgMarketOrigin(
                                            market = "Operit",
                                            toolpkgId = manifestPreview.manifest.toolpkgId,
                                            version = manifestPreview.manifest.version,
                                            author = marketOrigin.author
                                        )
                                    if (minify) {
                                        minifyJavaScriptBytes(
                                            bytes = originalBytes,
                                            entryName = requireNotNull(normalizedName),
                                            minifier = minifier,
                                            marketOrigin = mainOrigin
                                        )
                                    } else {
                                        injectToolPkgMarketOrigin(originalBytes, mainOrigin)
                                    }
                                }
                                minify &&
                                    normalizedName != null &&
                                    normalizedName != manifestEntryName &&
                                    shouldMinifyToolPkgEntry(
                                        normalizedName,
                                        executableEntryNames,
                                        resourceEntryRoots
                                    ) -> {
                                    minifyJavaScriptBytes(
                                        bytes = originalBytes,
                                        entryName = normalizedName,
                                        minifier = minifier
                                    )
                                }
                                else -> originalBytes
                            }
                        zipOutput.write(outputEntryBytes)
                    }
                    zipOutput.closeEntry()
                }
            }
        }
        return outputBytes.toByteArray()
    }

    private fun collectReachableToolPkgEntries(
        archive: ZipFile,
        entries: List<ZipEntry>,
        manifestEntryName: String,
        executableEntryNames: Set<String>,
        resourceEntryRoots: Set<String>
    ): Set<String> {
        val entriesByName = entries.mapNotNull { entry ->
            ToolPkgArchiveParser.normalizeZipEntryPath(entry.name)?.let { name -> name to entry }
        }.toMap()
        val entryNames = entriesByName.keys
        val reachable = linkedSetOf(manifestEntryName)
        executableEntryNames.forEach(reachable::add)
        resourceEntryRoots.forEach { root ->
            entryNames.filter { name -> name == root || name.startsWith("$root/") }
                .forEach(reachable::add)
        }

        val pending = ArrayDeque(executableEntryNames)
        while (pending.isNotEmpty()) {
            val currentName = pending.removeFirst()
            val entry = entriesByName[currentName] ?: continue
            if (!isJavaScriptEntry(currentName)) continue
            val source = archive.getInputStream(entry).use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
            staticModuleReferencePattern.findAll(source).forEach { match ->
                val specifier = match.groupValues[1].ifBlank { match.groupValues[2] }
                resolveToolPkgModuleEntry(currentName, specifier, entryNames)?.let { resolved ->
                    if (reachable.add(resolved)) {
                        pending.addLast(resolved)
                    }
                }
            }
        }
        return reachable
    }

    private fun resolveToolPkgModuleEntry(
        currentName: String,
        specifier: String,
        entryNames: Set<String>
    ): String? {
        if (!specifier.startsWith(".")) return null
        val baseSegments = currentName.substringBeforeLast('/', missingDelimiterValue = "")
            .split('/')
            .filter(String::isNotBlank)
            .toMutableList()
        specifier.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (baseSegments.isNotEmpty()) baseSegments.removeAt(baseSegments.lastIndex) else return null
                else -> baseSegments.add(segment)
            }
        }
        val modulePath = baseSegments.joinToString("/")
        val candidates = listOf(
            modulePath,
            "$modulePath.js",
            "$modulePath.mjs",
            "$modulePath.cjs",
            "$modulePath.json",
            "$modulePath/index.js"
        )
        return candidates.firstOrNull(entryNames::contains)
    }

    private fun isJavaScriptEntry(name: String): Boolean {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in setOf("js", "mjs", "cjs", "ts", "jsx", "tsx")
    }

    private fun injectToolPkgMarketOrigin(
        bytes: ByteArray,
        marketOrigin: ToolPkgMarketOrigin
    ): ByteArray {
        val source = bytes.toString(StandardCharsets.UTF_8)
        return "$source\n${ToolPkgMarketOriginCodec.encode(marketOrigin)}"
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun minifyJavaScriptBytes(
        bytes: ByteArray,
        entryName: String,
        minifier: ToolPkgJsAstMinifier,
        marketOrigin: ToolPkgMarketOrigin? = null
    ): ByteArray {
        val source = bytes.toString(StandardCharsets.UTF_8)
        // Keep provenance in the executable body so it is minified with the package and not stripped as a comment.
        val sourceWithOrigin =
            if (marketOrigin == null) source else "$source\n${ToolPkgMarketOriginCodec.encode(marketOrigin)}"
        val minified =
            minifyJavaScriptSourcePreservingMetadata(sourceWithOrigin, entryName, minifier)
        return minified.toByteArray(StandardCharsets.UTF_8)
    }

    private fun minifyJavaScriptSourcePreservingMetadata(
        source: String,
        entryName: String,
        minifier: ToolPkgJsAstMinifier
    ): String {
        val metadataBlock = findMetadataBlock(source)
        if (metadataBlock != null) {
            val body = source.removeRange(metadataBlock.range).trim()
            require(body.isNotEmpty()) { "JavaScript body after METADATA is empty for $entryName" }
            return metadataBlock.comment + minifier.minify(body, entryName)
        }
        return minifier.minify(source, entryName)
    }

    private fun findMetadataBlock(source: String): MetadataBlock? {
        val match = metadataContentPattern.find(source) ?: return null
        return MetadataBlock(
            comment = match.value,
            content = match.groupValues[1],
            range = match.range
        )
    }

    private fun injectScriptMarketOriginIntoMetadata(
        source: String,
        marketOrigin: ToolPkgMarketOrigin
    ): String {
        val metadataBlock = findMetadataBlock(source)
            ?: throw IllegalArgumentException("JavaScript package METADATA block is required for marketplace origin")
        val metadata = JSONObject(JsonValue.readHjson(metadataBlock.content).toString())
        metadata.put(SCRIPT_MARKET_ORIGIN_METADATA_KEY, ToolPkgMarketOriginCodec.encodeForMetadata(marketOrigin))
        val updatedMetadataBlock = "/* METADATA\n${metadata}\n*/"
        return source.replaceRange(metadataBlock.range, updatedMetadataBlock)
    }

    private fun shouldMinifyToolPkgEntry(
        normalizedName: String,
        executableEntryNames: Set<String>,
        resourceEntryRoots: Set<String>
    ): Boolean {
        if (resourceEntryRoots.any { root -> normalizedName == root || normalizedName.startsWith("$root/") }) {
            return false
        }
        if (executableEntryNames.contains(normalizedName)) return true
        val extension = normalizedName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in setOf("js", "mjs", "cjs", "ts", "jsx", "tsx")
    }

    private data class MetadataBlock(
        val comment: String,
        val content: String,
        val range: IntRange
    )

    private const val SCRIPT_MARKET_ORIGIN_METADATA_KEY = "__operit_market_origin"
    private val metadataContentPattern = Regex("""(?s)/\*\s*METADATA\s*(.*?)\*/""")
    private val staticModuleReferencePattern =
        Regex("""(?:require\s*\(\s*[\"']([^\"']+)[\"']\s*\)|(?:from|import)\s*[\"']([^\"']+)[\"'])""")
}

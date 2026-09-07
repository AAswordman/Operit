package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import java.io.File
import java.util.zip.ZipFile

internal object ToolPkgLoader {
    fun loadToolPkgFromExternalFile(
        file: File,
        jsEngine: JsEngine,
        parseJsPackage: (String, (String, String) -> Unit) -> ToolPackage?,
        reportPackageLoadError: (key: String, error: String) -> Unit
    ): ToolPkgLoadResult {
        ZipFile(file).use { archive ->
            val entryIndex = ToolPkgArchiveParser.buildZipEntryIndex(archive)
            val readEntryText =
                { path: String ->
                    ToolPkgArchiveParser.readZipEntryText(
                        archive = archive,
                        entryIndex = entryIndex,
                        rawPath = path
                    )
                }
            return jsEngine.withTemporaryToolPkgTextResourceResolver(
                resolver = { _, resourcePath -> readEntryText(resourcePath) }
            ) {
                ToolPkgArchiveParser.parseToolPkgFromIndexedEntries(
                    entryIndex = entryIndex,
                    readEntryText = readEntryText,
                    sourceType = ToolPkgSourceType.EXTERNAL,
                    sourcePath = file.absolutePath,
                    isBuiltIn = false,
                    parseJsPackage = parseJsPackage,
                    parseMainRegistration = { mainScriptText, toolPkgId, mainScriptPath, apiVersion ->
                        parseMainRegistration(mainScriptText, toolPkgId, mainScriptPath, apiVersion, jsEngine)
                    },
                    reportPackageLoadError = reportPackageLoadError
                )
            }
        }
    }

    fun loadToolPkgFromAsset(
        context: Context,
        assetPath: String,
        jsEngine: JsEngine,
        parseJsPackage: (String, (String, String) -> Unit) -> ToolPackage?,
        prepareAssetCache: (ToolPkgManifestPreview) -> File,
        reportPackageLoadError: (key: String, error: String) -> Unit
    ): ToolPkgLoadResult {
        val manifestPreview =
            ToolPkgArchiveParser.readToolPkgManifestPreview(
                inputStreamFactory = { context.assets.open(assetPath) }
            ) ?: throw IllegalArgumentException("manifest.hjson or manifest.json not found")
        val extractedDir = prepareAssetCache(manifestPreview)
        val entryIndex = ToolPkgArchiveParser.buildDirectoryEntryIndex(extractedDir)
        val readEntryText =
            { path: String ->
                ToolPkgArchiveParser.readDirectoryEntryText(
                    rootDir = extractedDir,
                    entryIndex = entryIndex,
                    rawPath = path
                )
            }
        return jsEngine.withTemporaryToolPkgTextResourceResolver(
            resolver = { _, resourcePath -> readEntryText(resourcePath) }
        ) {
            ToolPkgArchiveParser.parseToolPkgFromIndexedEntries(
                entryIndex = entryIndex,
                readEntryText = readEntryText,
                sourceType = ToolPkgSourceType.ASSET,
                sourcePath = assetPath,
                isBuiltIn = true,
                parseJsPackage = parseJsPackage,
                parseMainRegistration = { mainScriptText, toolPkgId, mainScriptPath, apiVersion ->
                    parseMainRegistration(mainScriptText, toolPkgId, mainScriptPath, apiVersion, jsEngine)
                },
                reportPackageLoadError = reportPackageLoadError
            )
        }
    }

    private fun parseMainRegistration(
        mainScriptText: String,
        toolPkgId: String,
        mainScriptPath: String,
        apiVersion: String,
        jsEngine: JsEngine
    ): ToolPkgMainRegistrationParseResult {
        return ToolPkgMainRegistrationScriptParser.parse(
            script = mainScriptText,
            toolPkgId = toolPkgId,
            mainScriptPath = mainScriptPath,
            apiVersion = apiVersion,
            jsEngine = jsEngine
        )
    }
}

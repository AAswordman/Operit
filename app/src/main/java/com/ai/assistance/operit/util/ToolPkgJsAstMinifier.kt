package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.core.tools.javascript.OperitQuickJsEngine
import java.io.Closeable
import java.nio.charset.StandardCharsets
import org.json.JSONArray

internal class ToolPkgJsAstMinifier(context: Context) : Closeable {
    private val engine = OperitQuickJsEngine()

    init {
        val appContext = context.applicationContext
        val terserSource =
            appContext.assets.open(TERSER_ASSET_PATH).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        require(terserSource.isNotBlank()) { "Terser bundle is empty: $TERSER_ASSET_PATH" }

        engine.evaluate<Any?>(terserSource, TERSER_ASSET_PATH)
        engine.evaluate<Any?>(MINIFIER_BOOTSTRAP, "toolpkg/js-ast-minifier.js")
    }

    fun minify(source: String, entryName: String): String {
        require(source.isNotEmpty()) { "Cannot AST-minify empty JavaScript entry: $entryName" }
        require(entryName.isNotBlank()) { "JavaScript entry name is required for AST minification" }

        val argsJson =
            JSONArray()
                .put(source)
                .put(entryName)
                .toString()
        val minified =
            engine.callFunction<String>(
                functionName = MINIFIER_FUNCTION_NAME,
                argsJson = argsJson,
                callSite = "toolpkg/js-ast-minifier/$entryName"
            )
                ?: error("Terser returned null for $entryName")

        require(minified.isNotEmpty()) { "AST-minified output is empty for $entryName" }
        return minified
    }

    override fun close() {
        engine.close()
    }

    private companion object {
        private const val TERSER_ASSET_PATH = "js/terser.bundle.min.js"
        private const val MINIFIER_FUNCTION_NAME = "__operitToolPkgAstMinify"

        private val MINIFIER_BOOTSTRAP =
            """
            (function(root) {
                if (!root.Terser || typeof root.Terser.minify_sync !== "function") {
                    throw new Error("Terser minify_sync is not available");
                }

                root.__operitToolPkgAstMinify = function(source, entryName) {
                    var normalizedEntryName = String(entryName);
                    var result = root.Terser.minify_sync(String(source), {
                        ecma: 2020,
                        module: /\.mjs$/i.test(normalizedEntryName),
                        compress: false,
                        mangle: false,
                        format: {
                            comments: false
                        },
                        sourceMap: false
                    });

                    if (!result || typeof result.code !== "string") {
                        throw new Error("Terser did not return minified code for " + normalizedEntryName);
                    }
                    return result.code;
                };
            })(typeof globalThis !== "undefined" ? globalThis : this);
            """.trimIndent()
    }
}

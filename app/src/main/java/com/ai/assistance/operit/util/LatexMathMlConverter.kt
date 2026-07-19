package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.core.tools.javascript.OperitQuickJsEngine
import java.nio.charset.StandardCharsets
import org.json.JSONArray

internal object LatexMathMlConverter {
    private const val TAG = "LatexMathMlConverter"
    private const val ASSET_PATH = "js/katex.min.js"
    private const val FUNCTION_NAME = "__operitLatexToMathMlBatch"

    private val lock = Any()

    @Volatile
    private var engine: OperitQuickJsEngine? = null

    fun convertAll(context: Context, formulas: List<String>): List<String> {
        if (formulas.isEmpty()) return emptyList()

        return try {
            val results =
                getEngine(context)
                    .callFunction<List<Any?>>(
                        functionName = FUNCTION_NAME,
                        argsJson = JSONArray().put(JSONArray(formulas)).toString(),
                        callSite = "latex-to-mathml-batch"
                    )
                    ?: error("KaTeX returned null")

            formulas.mapIndexed { index, formula ->
                val mathMl = results.getOrNull(index) as? String
                if (mathMl == null) {
                    AppLogger.w(TAG, "KaTeX could not parse formula: $formula")
                    return@mapIndexed formula
                }
                try {
                    MathMlPlainTextConverter.convert(mathMl)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Failed to convert MathML for formula: $formula", error)
                    formula
                }
            }
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to convert LaTeX batch", error)
            formulas
        }
    }

    private fun getEngine(context: Context): OperitQuickJsEngine {
        engine?.let { return it }
        return synchronized(lock) {
            engine ?: createEngine(context.applicationContext).also { engine = it }
        }
    }

    private fun createEngine(context: Context): OperitQuickJsEngine {
        val source =
            context.assets.open(ASSET_PATH).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        require(source.isNotBlank()) { "KaTeX bundle is empty: $ASSET_PATH" }

        return OperitQuickJsEngine().also { quickJs ->
            quickJs.evaluate<Any?>(
                "var exports = {}; var module = { exports: exports };\n$source",
                ASSET_PATH
            )
            quickJs.evaluate<Any?>(BOOTSTRAP, "latex-to-mathml-batch.js")
        }
    }

    private val BOOTSTRAP =
        """
        (function(root) {
            var katex = root.katex || (root.module && root.module.exports);
            if (!katex || typeof katex.renderToString !== "function") {
                throw new Error("KaTeX runtime is unavailable");
            }
            root.$FUNCTION_NAME = function(formulas) {
                return formulas.map(function(latex) {
                    try {
                        return katex.renderToString(String(latex), {
                            displayMode: false,
                            output: "mathml",
                            throwOnError: true,
                            strict: "ignore"
                        });
                    } catch (error) {
                        return null;
                    }
                });
            };
        })(typeof globalThis !== "undefined" ? globalThis : this);
        """.trimIndent()
}

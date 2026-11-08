package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger

private const val TAG = "JsAssetLoader"

private fun readJavaScriptAsset(
    context: Context,
    assetPath: String,
    label: String
): String {
    return try {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to load $label from $assetPath", e)
        ""
    }
}

fun loadUINodeJs(context: Context): String =
    readJavaScriptAsset(context, "js/UINode.js", "UINode.js")

fun loadAndroidUtilsJs(context: Context): String =
    readJavaScriptAsset(context, "js/AndroidUtils.js", "AndroidUtils.js")

fun loadOkHttp3Js(context: Context): String =
    readJavaScriptAsset(context, "js/OkHttp3.js", "OkHttp3.js")

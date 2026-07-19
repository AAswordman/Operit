package com.ai.assistance.operit.ui.common.composedsl

import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.javascript.JsJavaBridgeDelegates
import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionErrorMessage
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ToolPkgProtection
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ToolPkgComposeDslWebView"

private const val WEBVIEW_INTERNAL_JS_INTERFACE_BRIDGE_NAME = "__ComposeDslWebViewHostBridge__"
private const val WEBVIEW_BRIDGE_HTML_MARKER = "data-operit-webview-bridge-runtime=\"1\""
private const val WEBVIEW_NAVIGATION_TIMEOUT_MS = 5_000L
private const val WEBVIEW_RESOURCE_TIMEOUT_MS = 3_000L
private const val WEBVIEW_BRIDGE_TIMEOUT_MS = 20_000L

internal val composeDslWebViewMainHandler by lazy { Handler(Looper.getMainLooper()) }

private data class ComposeDslWebViewControllerDescriptor(
    val key: String,
    val routeInstanceId: String?,
    val executionContextKey: String?
)

internal data class ComposeDslWebViewStateSnapshot(
    val url: String?,
    val title: String?,
    val loading: Boolean,
    val progress: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean
) {
    fun toPayload(): Map<String, Any?> =
        linkedMapOf(
            "url" to url,
            "title" to title,
            "loading" to loading,
            "progress" to progress,
            "canGoBack" to canGoBack,
            "canGoForward" to canGoForward
        )
}

internal data class ComposeDslWebViewBlockingActionResult(
    val actionResult: Any?,
    val message: String?
)

private data class ComposeDslWebViewNavigationDecision(
    val action: String,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap()
)

private data class ComposeDslWebViewResourceResponseSpec(
    val mimeType: String,
    val encoding: String,
    val statusCode: Int,
    val reasonPhrase: String,
    val headers: Map<String, String>,
    val text: String? = null,
    val base64: String? = null,
    val filePath: String? = null
)

private data class ComposeDslWebViewResourceDecision(
    val action: String,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val response: ComposeDslWebViewResourceResponseSpec? = null
)

private class ComposeDslWebViewActionLane(label: String) {
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "OperitComposeDslWebView:$label").apply {
                isDaemon = true
            }
        }

    fun <T> runBlocking(
        timeoutMs: Long,
        block: () -> T
    ): T {
        val future = executor.submit<T> { block() }
        return future.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun executeAsync(block: () -> Unit): Boolean {
        return try {
            executor.submit(block)
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}

internal class ComposeDslWebViewHostContext(
    val routeInstanceId: String,
    val executionContextKey: String,
    private val jsEngine: JsEngine,
    private val runtimeOptionsProvider: () -> Map<String, Any?>,
    private val applyRenderResult: (String, Any?) -> Unit
) {
    fun executeActionBlocking(
        actionId: String,
        payload: Any?
    ): ComposeDslWebViewBlockingActionResult {
        val normalizedActionId = actionId.trim()
        if (normalizedActionId.isBlank()) {
            return ComposeDslWebViewBlockingActionResult(
                actionResult = null,
                message = "compose action id is required"
            )
        }
        return try {
            val rawResult =
                jsEngine.executeComposeDslAction(
                    actionId = normalizedActionId,
                    payload = payload,
                    runtimeOptions = runtimeOptionsProvider(),
                    onIntermediateResult = { intermediateResult ->
                        applyRenderResult("webview_action_intermediate", intermediateResult)
                    }
                )
            applyRenderResult("webview_action_final", rawResult)
            ComposeDslWebViewBlockingActionResult(
                actionResult = parseComposeDslActionResult(rawResult),
                message = extractComposeDslActionError(rawResult)
            )
        } catch (error: Throwable) {
            val message = error.message?.trim().orEmpty().ifBlank { "compose action dispatch failed" }
            AppLogger.e(
                TAG,
                "compose_dsl webview action failed: routeInstanceId=$routeInstanceId, actionId=$normalizedActionId, error=$message",
                error
            )
            ComposeDslWebViewBlockingActionResult(
                actionResult = null,
                message = message
            )
        }
    }

}

private class ComposeDslWebViewControllerBinding(
    val routeInstanceId: String,
    val executionContextKey: String,
    val controllerKey: String,
    @Volatile var webView: WebView?,
    val stateRef: AtomicReference<ComposeDslWebViewStateSnapshot>
) {
    @Volatile
    var pendingRewriteUrl: String? = null

    val javascriptInterfaceActionIds =
        ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
}

internal object ComposeDslWebViewHostRegistry {
    private val bindings =
        ConcurrentHashMap<String, ConcurrentHashMap<String, ComposeDslWebViewControllerBinding>>()
    private val javascriptInterfaceActionIds =
        ConcurrentHashMap<
            String,
            ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, String>>>
            >()

    fun bind(
        executionContextKey: String,
        routeInstanceId: String,
        controllerKey: String,
        webView: WebView,
        stateRef: AtomicReference<ComposeDslWebViewStateSnapshot>
    ) {
        if (executionContextKey.isBlank() || controllerKey.isBlank()) {
            return
        }
        val scopedBindings =
            bindings.getOrPut(executionContextKey) {
                ConcurrentHashMap()
            }
        scopedBindings[controllerKey] =
            ComposeDslWebViewControllerBinding(
                routeInstanceId = routeInstanceId,
                executionContextKey = executionContextKey,
                controllerKey = controllerKey,
                webView = webView,
                stateRef = stateRef
            ).also { binding ->
                val registeredInterfaces =
                    javascriptInterfaceActionIds[executionContextKey]?.get(controllerKey).orEmpty()
                registeredInterfaces.forEach { (interfaceName, methods) ->
                    binding.javascriptInterfaceActionIds[interfaceName] =
                        ConcurrentHashMap(methods)
                }
            }
    }

    fun unbind(
        executionContextKey: String,
        controllerKey: String,
        webView: WebView
    ) {
        val scopedBindings = bindings[executionContextKey] ?: return
        val current = scopedBindings[controllerKey] ?: return
        if (current.webView === webView) {
            scopedBindings.remove(controllerKey)
        }
        if (scopedBindings.isEmpty()) {
            bindings.remove(executionContextKey)
        }
    }

    fun clearExecutionContext(executionContextKey: String) {
        if (executionContextKey.isBlank()) {
            return
        }
        bindings.remove(executionContextKey)
        javascriptInterfaceActionIds.remove(executionContextKey)
    }

    fun updateState(
        executionContextKey: String,
        controllerKey: String,
        state: ComposeDslWebViewStateSnapshot
    ) {
        bindings[executionContextKey]?.get(controllerKey)?.stateRef?.set(state)
    }

    fun markPendingRewrite(
        executionContextKey: String,
        controllerKey: String,
        url: String
    ) {
        bindings[executionContextKey]?.get(controllerKey)?.pendingRewriteUrl = url
    }

    fun consumePendingRewrite(
        executionContextKey: String,
        controllerKey: String,
        url: String
    ): Boolean {
        val binding = bindings[executionContextKey]?.get(controllerKey) ?: return false
        val pendingUrl = binding.pendingRewriteUrl?.trim().orEmpty()
        if (pendingUrl.isBlank() || pendingUrl != url.trim()) {
            return false
        }
        binding.pendingRewriteUrl = null
        return true
    }

    fun findJavascriptInterfaceActionId(
        executionContextKey: String,
        controllerKey: String,
        interfaceName: String,
        methodName: String
    ): String? {
        if (executionContextKey.isBlank() || controllerKey.isBlank()) {
            return null
        }
        val normalizedInterfaceName = interfaceName.trim()
        val normalizedMethodName = methodName.trim()
        if (normalizedInterfaceName.isBlank() || normalizedMethodName.isBlank()) {
            return null
        }
        return bindings[executionContextKey]
            ?.get(controllerKey)
            ?.javascriptInterfaceActionIds
            ?.get(normalizedInterfaceName)
            ?.get(normalizedMethodName)
            ?.trim()
            ?.ifBlank { null }
            ?: javascriptInterfaceActionIds[executionContextKey]
                ?.get(controllerKey)
                ?.get(normalizedInterfaceName)
                ?.get(normalizedMethodName)
                ?.trim()
                ?.ifBlank { null }
    }

    fun registerJavascriptInterface(
        executionContextKey: String,
        controllerKey: String,
        interfaceName: String,
        methodActionIds: Map<String, String>
    ): Boolean {
        val normalizedInterfaceName = interfaceName.trim()
        val normalizedMethods =
            methodActionIds.entries
                .mapNotNull { (methodName, actionId) ->
                    val normalizedMethodName = methodName.trim()
                    val normalizedActionId = actionId.trim()
                    if (normalizedMethodName.isBlank() || normalizedActionId.isBlank()) {
                        null
                    } else {
                        normalizedMethodName to normalizedActionId
                    }
                }
                .toMap()
        if (
            executionContextKey.isBlank() ||
                controllerKey.isBlank() ||
                normalizedInterfaceName.isBlank() ||
                normalizedMethods.isEmpty()
        ) {
            return false
        }
        val scopedInterfaces =
            javascriptInterfaceActionIds.getOrPut(executionContextKey) {
                ConcurrentHashMap()
            }
        val controllerInterfaces =
            scopedInterfaces.getOrPut(controllerKey) {
                ConcurrentHashMap()
            }
        controllerInterfaces[normalizedInterfaceName] = ConcurrentHashMap(normalizedMethods)
        bindings[executionContextKey]
            ?.get(controllerKey)
            ?.javascriptInterfaceActionIds
            ?.set(normalizedInterfaceName, ConcurrentHashMap(normalizedMethods))
        return true
    }

    fun unregisterJavascriptInterface(
        executionContextKey: String,
        controllerKey: String,
        interfaceName: String
    ): Boolean {
        val normalizedInterfaceName = interfaceName.trim()
        if (executionContextKey.isBlank() || controllerKey.isBlank() || normalizedInterfaceName.isBlank()) {
            return false
        }
        val bindingRemoved =
            bindings[executionContextKey]
                ?.get(controllerKey)
                ?.javascriptInterfaceActionIds
                ?.remove(normalizedInterfaceName) != null
        val controllerInterfaces = javascriptInterfaceActionIds[executionContextKey]?.get(controllerKey)
        val storedRemoved = controllerInterfaces?.remove(normalizedInterfaceName) != null
        if (controllerInterfaces?.isEmpty() == true) {
            javascriptInterfaceActionIds[executionContextKey]?.remove(controllerKey)
        }
        if (javascriptInterfaceActionIds[executionContextKey]?.isEmpty() == true) {
            javascriptInterfaceActionIds.remove(executionContextKey)
        }
        return bindingRemoved || storedRemoved
    }

    fun listJavascriptInterfaces(
        executionContextKey: String,
        controllerKey: String,
    ): Map<String, List<String>> {
        val interfaces =
            bindings[executionContextKey]?.get(controllerKey)?.javascriptInterfaceActionIds
                ?: javascriptInterfaceActionIds[executionContextKey]?.get(controllerKey)
                ?: return emptyMap()
        return interfaces.entries
            .associate { (interfaceName, methods) ->
                interfaceName to methods.keys.sorted()
            }
    }

    fun handleControllerCommand(payloadJson: String): String {
        val payload = JsJavaBridgeDelegates.parseJsonObject(payloadJson)
            ?: return buildComposeDslBridgeError("invalid webview controller command payload")
        val executionContextKey = payload.optString("executionContextKey").trim()
        val controllerKey = payload.optString("key").trim()
        val command = payload.optString("command").trim()
        if (executionContextKey.isBlank() || controllerKey.isBlank() || command.isBlank()) {
            return buildComposeDslBridgeError("webview controller command is missing required fields")
        }
        val binding = bindings[executionContextKey]?.get(controllerKey)
        if (binding == null) {
            return when (command) {
                "getState" -> buildComposeDslBridgeSuccess(null)
                "addJavascriptInterface" -> {
                    val commandPayload =
                        (JsJavaBridgeDelegates.decodePlainJsonValue(payload.opt("payload")) as? Map<*, *>)
                            ?.entries
                            ?.associate { entry ->
                                entry.key.toString() to entry.value
                            }
                            .orEmpty()
                    val name = commandPayload["name"]?.toString()?.trim().orEmpty()
                    val methodActionIds =
                        extractComposeDslJavascriptInterfaceMethods(commandPayload["object"])
                    if (name.isBlank() || methodActionIds.isEmpty()) {
                        buildComposeDslBridgeError(
                            "webview controller addJavascriptInterface requires a non-empty name and at least one function method"
                        )
                    } else if (
                        registerJavascriptInterface(
                            executionContextKey = executionContextKey,
                            controllerKey = controllerKey,
                            interfaceName = name,
                            methodActionIds = methodActionIds
                        )
                    ) {
                        buildComposeDslBridgeSuccess(null)
                    } else {
                        buildComposeDslBridgeError(
                            "failed to register webview javascript interface: $name"
                        )
                    }
                }

                "removeJavascriptInterface" -> {
                    val commandPayload =
                        (JsJavaBridgeDelegates.decodePlainJsonValue(payload.opt("payload")) as? Map<*, *>)
                            ?.entries
                            ?.associate { entry ->
                                entry.key.toString() to entry.value
                            }
                            .orEmpty()
                    val name = commandPayload["name"]?.toString()?.trim().orEmpty()
                    if (name.isBlank()) {
                        buildComposeDslBridgeError(
                            "webview controller removeJavascriptInterface requires a non-empty name"
                        )
                    } else {
                        unregisterJavascriptInterface(
                            executionContextKey = executionContextKey,
                            controllerKey = controllerKey,
                            interfaceName = name
                        )
                        buildComposeDslBridgeSuccess(null)
                    }
                }

                else -> {
                    buildComposeDslBridgeError(
                        "webview controller '$controllerKey' is not bound in route '$executionContextKey'"
                    )
                }
            }
        }
        val webView = binding.webView
            ?: return buildComposeDslBridgeError("bound webview is unavailable")
        val commandPayload =
            (JsJavaBridgeDelegates.decodePlainJsonValue(payload.opt("payload")) as? Map<*, *>)
                ?.entries
                ?.associate { entry ->
                    entry.key.toString() to entry.value
                }
                .orEmpty()
        return try {
            when (command) {
                "loadUrl" -> {
                    val url = commandPayload["url"]?.toString()?.trim().orEmpty()
                    if (url.isBlank()) {
                        return buildComposeDslBridgeError("webview controller loadUrl requires a non-empty url")
                    }
                    val headers = toStringMap(commandPayload["headers"])
                    runOnMainBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                        if (headers.isEmpty()) {
                            webView.loadUrl(url)
                        } else {
                            webView.loadUrl(url, headers)
                        }
                    }
                    buildComposeDslBridgeSuccess(null)
                }

                "loadHtml" -> {
                    val html = commandPayload["html"]?.toString().orEmpty()
                    val options = commandPayload["options"] as? Map<*, *>
                    val htmlToLoad = injectComposeDslWebViewBridgeRuntimeIntoHtml(html)
                    runOnMainBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                        webView.loadDataWithBaseURL(
                            options?.get("baseUrl")?.toString(),
                            htmlToLoad,
                            options?.get("mimeType")?.toString() ?: "text/html",
                            options?.get("encoding")?.toString() ?: "UTF-8",
                            null
                        )
                    }
                    buildComposeDslBridgeSuccess(null)
                }

                "reload" -> {
                    runOnMainBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                        webView.reload()
                    }
                    buildComposeDslBridgeSuccess(null)
                }

                "stopLoading" -> {
                    runOnMainBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                        webView.stopLoading()
                    }
                    buildComposeDslBridgeSuccess(null)
                }

                "goBack" -> {
                    runOnMainBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        }
                    }
                    buildComposeDslBridgeSuccess(null)
                }

                "goForward" -> {
                    runOnMainBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                        if (webView.canGoForward()) {
                            webView.goForward()
                        }
                    }
                    buildComposeDslBridgeSuccess(null)
                }

                "clearHistory" -> {
                    runOnMainBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                        webView.clearHistory()
                    }
                    buildComposeDslBridgeSuccess(null)
                }

                "evaluateJavascript" -> {
                    val script = commandPayload["script"]?.toString().orEmpty()
                    buildComposeDslBridgeSuccess(
                        evaluateJavascriptBlocking(
                            webView = webView,
                            script = script,
                            timeoutMs = WEBVIEW_BRIDGE_TIMEOUT_MS
                        )
                    )
                }

                "getState" -> {
                    buildComposeDslBridgeSuccess(binding.stateRef.get().toPayload())
                }

                "addJavascriptInterface" -> {
                    val name = commandPayload["name"]?.toString()?.trim().orEmpty()
                    val methodActionIds =
                        extractComposeDslJavascriptInterfaceMethods(commandPayload["object"])
                    if (name.isBlank() || methodActionIds.isEmpty()) {
                        return buildComposeDslBridgeError(
                            "webview controller addJavascriptInterface requires a non-empty name and at least one function method"
                        )
                    }
                    if (
                        !registerJavascriptInterface(
                            executionContextKey = executionContextKey,
                            controllerKey = controllerKey,
                            interfaceName = name,
                            methodActionIds = methodActionIds
                        )
                    ) {
                        return buildComposeDslBridgeError(
                            "failed to register webview javascript interface: $name"
                        )
                    }
                    refreshComposeDslJavascriptInterfaces(webView)
                    buildComposeDslBridgeSuccess(null)
                }

                "removeJavascriptInterface" -> {
                    val name = commandPayload["name"]?.toString()?.trim().orEmpty()
                    if (name.isBlank()) {
                        return buildComposeDslBridgeError(
                            "webview controller removeJavascriptInterface requires a non-empty name"
                        )
                    }
                    unregisterJavascriptInterface(
                        executionContextKey = executionContextKey,
                        controllerKey = controllerKey,
                        interfaceName = name
                    )
                    refreshComposeDslJavascriptInterfaces(webView)
                    buildComposeDslBridgeSuccess(null)
                }

                else -> buildComposeDslBridgeError("unsupported webview controller command: $command")
            }
        } catch (error: TimeoutException) {
            AppLogger.e(
                TAG,
                "webview controller command timed out: route=${binding.routeInstanceId}, key=$controllerKey, command=$command"
            )
            buildComposeDslBridgeError("webview controller command timed out: $command")
        } catch (error: Throwable) {
            AppLogger.e(
                TAG,
                "webview controller command failed: route=${binding.routeInstanceId}, key=$controllerKey, command=$command, error=${error.message}",
                error
            )
            buildComposeDslBridgeError(error.message?.trim().orEmpty().ifBlank { "webview controller command failed" })
        }
    }
}

private fun runOnMainBlocking(
    timeoutMs: Long,
    block: () -> Unit
) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
        return
    }
    val latch = CountDownLatch(1)
    val errorRef = AtomicReference<Throwable?>(null)
    composeDslWebViewMainHandler.post {
        try {
            block()
        } catch (error: Throwable) {
            errorRef.set(error)
        } finally {
            latch.countDown()
        }
    }
    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw TimeoutException("timed out waiting for main-thread webview operation")
    }
    errorRef.get()?.let { throw it }
}

private fun evaluateJavascriptBlocking(
    webView: WebView,
    script: String,
    timeoutMs: Long
): Any? {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "evaluateJavascriptBlocking cannot run on the main thread"
    }
    val latch = CountDownLatch(1)
    val errorRef = AtomicReference<Throwable?>(null)
    val resultRef = AtomicReference<String?>(null)
    composeDslWebViewMainHandler.post {
        try {
            webView.evaluateJavascript(script) { rawResult ->
                resultRef.set(rawResult)
                latch.countDown()
            }
        } catch (error: Throwable) {
            errorRef.set(error)
            latch.countDown()
        }
    }
    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw TimeoutException("timed out waiting for evaluateJavascript")
    }
    errorRef.get()?.let { throw it }
    return JsJavaBridgeDelegates.parsePlainJsonValueOrRawString(resultRef.get())
}

private fun buildComposeDslBridgeSuccess(data: Any?): String =
    JSONObject()
        .put("success", true)
        .put("data", JsJavaBridgeDelegates.toPlainJsonValue(data))
        .toString()

private fun buildComposeDslBridgeError(message: String): String =
    JSONObject()
        .put("success", false)
        .put("message", message)
        .toString()

private fun parseComposeDslActionResult(rawResult: Any?): Any? {
    val root = JsJavaBridgeDelegates.parseJsonObject(rawResult) ?: return null
    if (!root.has("actionResult")) {
        return null
    }
    return JsJavaBridgeDelegates.decodePlainJsonValue(root.opt("actionResult"))
}

private fun extractComposeDslActionError(rawResult: Any?): String? {
    return extractJsExecutionErrorMessage(rawResult)
}

private fun toStringMap(value: Any?): Map<String, String> {
    val map = value as? Map<*, *> ?: return emptyMap()
    return map.entries
        .mapNotNull { (key, item) ->
            val normalizedKey = key?.toString()?.trim().orEmpty()
            if (normalizedKey.isBlank() || item == null) {
                null
            } else {
                normalizedKey to item.toString()
            }
        }
        .toMap()
}

private fun extractComposeDslJavascriptInterfaceMethods(value: Any?): Map<String, String> {
    val methods = value as? Map<*, *> ?: return emptyMap()
    return methods.entries
        .mapNotNull { (methodName, handlerValue) ->
            val normalizedMethodName = methodName?.toString()?.trim().orEmpty()
            val actionId = ToolPkgComposeDslParser.extractActionId(handlerValue)
            if (normalizedMethodName.isBlank() || actionId.isNullOrBlank()) {
                null
            } else {
                normalizedMethodName to actionId
            }
        }
        .toMap()
}

private fun buildComposeDslWebViewJavascriptInterfaceRuntimeScript(): String {
    val hiddenBridgeNameJson = JSONObject.quote(WEBVIEW_INTERNAL_JS_INTERFACE_BRIDGE_NAME)
    return """
        (function() {
            var hiddenBridgeName = $hiddenBridgeNameJson;
            var hiddenBridge = window[hiddenBridgeName];
            if (!hiddenBridge || typeof hiddenBridge.listInterfaces !== 'function') {
                return;
            }
            function parseValue(rawValue) {
                if (rawValue === undefined || rawValue === null) {
                    return null;
                }
                if (typeof rawValue !== 'string') {
                    return rawValue;
                }
                var trimmed = rawValue.trim();
                if (!trimmed) {
                    return null;
                }
                try {
                    return JSON.parse(trimmed);
                } catch (_error) {
                    return rawValue;
                }
            }
            function unwrapResult(rawValue, label) {
                var parsed = parseValue(rawValue);
                if (parsed && typeof parsed === 'object' && parsed.success === false) {
                    throw new Error(String(parsed.message || ''));
                }
                if (parsed && typeof parsed === 'object' && Object.prototype.hasOwnProperty.call(parsed, 'data')) {
                    return parsed.data;
                }
                return parsed;
            }
            function defineReadonly(target, key, value) {
                Object.defineProperty(target, key, {
                    configurable: true,
                    enumerable: true,
                    writable: false,
                    value: value
                });
            }
            function installInterfaces() {
                var descriptors = unwrapResult(
                    hiddenBridge.listInterfaces(),
                    'compose webview javascript interface discovery failed'
                ) || {};
                var installed =
                    window.__operitComposeDslInstalledJavascriptInterfaces &&
                    typeof window.__operitComposeDslInstalledJavascriptInterfaces === 'object'
                        ? window.__operitComposeDslInstalledJavascriptInterfaces
                        : {};
                for (var previousInterfaceName in installed) {
                    if (
                        Object.prototype.hasOwnProperty.call(installed, previousInterfaceName) &&
                        !Object.prototype.hasOwnProperty.call(descriptors, previousInterfaceName)
                    ) {
                        try {
                            delete window[previousInterfaceName];
                        } catch (_deleteError) {
                        }
                    }
                }
                window.__operitComposeDslInstalledJavascriptInterfaces = {};
                for (var interfaceName in descriptors) {
                    if (!Object.prototype.hasOwnProperty.call(descriptors, interfaceName)) {
                        continue;
                    }
                    var methodNames = Array.isArray(descriptors[interfaceName])
                        ? descriptors[interfaceName]
                        : [];
                    var hostObject = {};
                    window[interfaceName] = hostObject;
                    for (var i = 0; i < methodNames.length; i += 1) {
                        (function(targetObject, resolvedInterfaceName, resolvedMethodName) {
                            defineReadonly(
                                targetObject,
                                resolvedMethodName,
                                function() {
                                    var args = [];
                                    for (var argIndex = 0; argIndex < arguments.length; argIndex += 1) {
                                        args.push(arguments[argIndex]);
                                    }
                                    return unwrapResult(
                                        hiddenBridge.invoke(
                                            resolvedInterfaceName,
                                            resolvedMethodName,
                                            JSON.stringify(args)
                                        ),
                                        'compose webview javascript interface invocation failed'
                                    );
                                }
                            );
                        })(hostObject, interfaceName, String(methodNames[i] || '').trim());
                    }
                    window.__operitComposeDslInstalledJavascriptInterfaces[interfaceName] = true;
                }
            }
            window.__operitInstallComposeDslJavascriptInterfaces = installInterfaces;
            installInterfaces();
        })();
    """.trimIndent()
}

private fun buildComposeDslWebViewBridgeRuntimeScriptTag(): String {
    val scriptBody =
        buildComposeDslWebViewJavascriptInterfaceRuntimeScript()
            .replace("</script>", "<\\/script>")
    return "<script $WEBVIEW_BRIDGE_HTML_MARKER>$scriptBody</script>"
}

private fun injectComposeDslWebViewBridgeRuntimeIntoHtml(html: String): String {
    if (html.contains(WEBVIEW_BRIDGE_HTML_MARKER)) {
        return html
    }
    val scriptTag = buildComposeDslWebViewBridgeRuntimeScriptTag()
    val headCloseRegex = Regex("(?i)</head>")
    if (headCloseRegex.containsMatchIn(html)) {
        return headCloseRegex.replaceFirst(html, "$scriptTag</head>")
    }
    val headOpenRegex = Regex("(?i)<head[^>]*>")
    if (headOpenRegex.containsMatchIn(html)) {
        val match = headOpenRegex.find(html)
        if (match != null) {
            return headOpenRegex.replaceFirst(html, "${match.value}$scriptTag")
        }
    }
    val htmlOpenRegex = Regex("(?i)<html[^>]*>")
    if (htmlOpenRegex.containsMatchIn(html)) {
        val match = htmlOpenRegex.find(html)
        if (match != null) {
            return htmlOpenRegex.replaceFirst(html, "${match.value}<head>$scriptTag</head>")
        }
    }
    return scriptTag + html
}

private fun refreshComposeDslJavascriptInterfaces(webView: WebView) {
    val script =
        """
        (function() {
            if (typeof window.__operitInstallComposeDslJavascriptInterfaces === 'function') {
                window.__operitInstallComposeDslJavascriptInterfaces();
            }
        })();
        """.trimIndent()
    val install = Runnable {
        runCatching {
            webView.evaluateJavascript(script, null)
        }.onFailure { error ->
            AppLogger.e(TAG, "Failed to refresh WebView javascript interfaces", error)
        }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        install.run()
    } else {
        webView.post(install)
    }
}

internal val LocalComposeDslWebViewHost = staticCompositionLocalOf<ComposeDslWebViewHostContext?> {
    null
}

private data class ComposeDslWebViewRequest(
    val url: String?,
    val html: String?,
    val baseUrl: String?,
    val mimeType: String,
    val encoding: String,
    val headers: Map<String, String>
)

private data class ComposeDslWebViewCallbackIds(
    val onPageStarted: String?,
    val onPageFinished: String?,
    val onReceivedError: String?,
    val onReceivedHttpError: String?,
    val onReceivedSslError: String?,
    val onDownloadStart: String?,
    val onConsoleMessage: String?,
    val onUrlChanged: String?,
    val onProgressChanged: String?,
    val onStateChanged: String?,
    val onLifecycleEvent: String?,
    val onShouldOverrideUrlLoading: String?,
    val onInterceptRequest: String?
)

private fun buildComposeDslWebViewRequest(
    props: Map<String, Any?>,
    allowBlank: Boolean = false
): ComposeDslWebViewRequest {
    val url = props.stringOrNull("url")
    val html = props.stringOrNull("html")
    require(allowBlank || url != null || html != null) {
        "WebView requires either 'url' or 'html'."
    }
    val headers =
        (props["headers"] as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (key, value) ->
                val normalizedKey = key?.toString()?.trim().orEmpty()
                if (normalizedKey.isBlank() || value == null) {
                    null
                } else {
                    normalizedKey to value.toString()
                }
            }
            ?.toMap()
            .orEmpty()
    return ComposeDslWebViewRequest(
        url = url,
        html = html,
        baseUrl = props.stringOrNull("baseUrl"),
        mimeType = props.string("mimeType", "text/html"),
        encoding = props.string("encoding", "UTF-8"),
        headers = headers
    )
}

private fun buildComposeDslWebViewControllerDescriptor(
    props: Map<String, Any?>
): ComposeDslWebViewControllerDescriptor? {
    val rawController = props["controller"] as? Map<*, *> ?: return null
    val marker = rawController["__composeWebViewController"] as? Boolean ?: false
    if (!marker) {
        return null
    }
    val key = rawController["key"]?.toString()?.trim().orEmpty()
    if (key.isBlank()) {
        return null
    }
    return ComposeDslWebViewControllerDescriptor(
        key = key,
        routeInstanceId = rawController["routeInstanceId"]?.toString()?.trim()?.ifBlank { null },
        executionContextKey = rawController["executionContextKey"]?.toString()?.trim()?.ifBlank { null }
    )
}

private fun buildComposeDslWebViewStateSnapshot(
    view: WebView?,
    loading: Boolean,
    progress: Int,
    urlOverride: String? = null
): ComposeDslWebViewStateSnapshot =
    ComposeDslWebViewStateSnapshot(
        url = urlOverride ?: view?.url,
        title = view?.title,
        loading = loading,
        progress = progress.coerceIn(0, 100),
        canGoBack = view?.canGoBack() ?: false,
        canGoForward = view?.canGoForward() ?: false
    )

private fun buildComposeDslWebViewLifecyclePayload(
    type: String,
    state: ComposeDslWebViewStateSnapshot,
    extras: Map<String, Any?> = emptyMap()
): Map<String, Any?> =
    linkedMapOf<String, Any?>(
        "type" to type,
        "url" to state.url,
        "title" to state.title,
        "loading" to state.loading,
        "progress" to state.progress,
        "canGoBack" to state.canGoBack,
        "canGoForward" to state.canGoForward
    ).apply {
        putAll(extras)
    }

private fun buildComposeDslWebViewRequestPayload(request: WebResourceRequest): Map<String, Any?> =
    linkedMapOf(
        "url" to request.url.toString(),
        "method" to request.method,
        "headers" to request.requestHeaders.orEmpty(),
        "isMainFrame" to request.isForMainFrame,
        "hasGesture" to request.hasGesture(),
        "isRedirect" to
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                request.isRedirect
            } else {
                false
            },
        "scheme" to request.url.scheme
    )

private fun parseComposeDslWebViewNavigationDecision(raw: Any?): ComposeDslWebViewNavigationDecision? {
    val action =
        when (raw) {
            is String -> raw.trim()
            is Map<*, *> -> raw["action"]?.toString()?.trim().orEmpty()
            else -> ""
        }
    if (action.isBlank()) {
        return null
    }
    val rawMap = raw as? Map<*, *>
    return when (action) {
        "allow", "cancel" -> ComposeDslWebViewNavigationDecision(action = action)
        "rewrite" -> {
            val url = rawMap?.get("url")?.toString()?.trim().orEmpty()
            if (url.isBlank()) {
                null
            } else {
                ComposeDslWebViewNavigationDecision(
                    action = action,
                    url = url,
                    headers = toStringMap(rawMap?.get("headers"))
                )
            }
        }

        "external" -> ComposeDslWebViewNavigationDecision(
            action = action,
            url = rawMap?.get("url")?.toString()?.trim()?.ifBlank { null }
        )

        else -> null
    }
}

private fun parseComposeDslWebViewResourceDecision(raw: Any?): ComposeDslWebViewResourceDecision? {
    val action =
        when (raw) {
            is String -> raw.trim()
            is Map<*, *> -> raw["action"]?.toString()?.trim().orEmpty()
            else -> ""
        }
    if (action.isBlank()) {
        return null
    }
    val rawMap = raw as? Map<*, *>
    return when (action) {
        "allow", "block" -> ComposeDslWebViewResourceDecision(action = action)
        "rewrite" -> {
            val url = rawMap?.get("url")?.toString()?.trim().orEmpty()
            if (url.isBlank()) {
                null
            } else {
                ComposeDslWebViewResourceDecision(
                    action = action,
                    url = url,
                    headers = toStringMap(rawMap?.get("headers"))
                )
            }
        }

        "respond" -> {
            val response = rawMap?.get("response") as? Map<*, *> ?: return null
            val text = response["text"]?.toString()
            val base64 = response["base64"]?.toString()
            val filePath = response["filePath"]?.toString()
            val bodySourceCount =
                listOf(text, base64, filePath)
                    .count { value -> !value.isNullOrBlank() }
            if (bodySourceCount != 1) {
                return null
            }
            ComposeDslWebViewResourceDecision(
                action = action,
                response =
                    ComposeDslWebViewResourceResponseSpec(
                        mimeType = response["mimeType"]?.toString() ?: "text/plain",
                        encoding = response["encoding"]?.toString() ?: "utf-8",
                        statusCode = response["statusCode"]?.toString()?.toIntOrNull() ?: 200,
                        reasonPhrase = response["reasonPhrase"]?.toString() ?: "OK",
                        headers = toStringMap(response["headers"]),
                        text = text,
                        base64 = base64,
                        filePath = filePath
                    )
            )
        }

        else -> null
    }
}

private fun buildComposeDslBlockedWebResourceResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "Blocked",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )

private fun buildComposeDslWebResourceResponse(
    spec: ComposeDslWebViewResourceResponseSpec
): WebResourceResponse? {
    val bodySourceCount =
        listOf(spec.text, spec.base64, spec.filePath)
            .count { value -> !value.isNullOrBlank() }
    if (bodySourceCount != 1) {
        return null
    }
    val bodyBytes =
        when {
            spec.text != null -> {
                val charset =
                    runCatching {
                        java.nio.charset.Charset.forName(spec.encoding)
                    }.getOrDefault(Charsets.UTF_8)
                val textBody =
                    if (spec.mimeType.equals("text/html", ignoreCase = true)) {
                        injectComposeDslWebViewBridgeRuntimeIntoHtml(spec.text)
                    } else {
                        spec.text
                    }
                textBody.toByteArray(charset)
            }
            spec.base64 != null -> Base64.decode(spec.base64, Base64.DEFAULT)
            spec.filePath != null -> {
                val file = File(spec.filePath)
                if (!file.exists() || !file.isFile) {
                    return null
                }
                if (spec.mimeType.equals("text/html", ignoreCase = true)) {
                    val charset =
                        runCatching {
                            java.nio.charset.Charset.forName(spec.encoding)
                        }.getOrDefault(Charsets.UTF_8)
                    val html = ToolPkgProtection.decryptIfNeeded(file.readBytes()).toString(charset)
                    injectComposeDslWebViewBridgeRuntimeIntoHtml(html).toByteArray(charset)
                } else {
                    ToolPkgProtection.decryptIfNeeded(file.readBytes())
                }
            }

            else -> return null
        }
    return WebResourceResponse(
        spec.mimeType,
        spec.encoding,
        spec.statusCode,
        spec.reasonPhrase,
        spec.headers,
        ByteArrayInputStream(bodyBytes)
    )
}

private fun fetchComposeDslRewrittenResource(
    request: WebResourceRequest,
    rewrittenUrl: String,
    rewrittenHeaders: Map<String, String>
): WebResourceResponse? {
    return runCatching {
        val connection = URL(rewrittenUrl).openConnection() as? HttpURLConnection ?: return null
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = request.method.takeIf { method -> !method.isNullOrBlank() } ?: "GET"
        request.requestHeaders.orEmpty().forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }
        rewrittenHeaders.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }
        connection.connect()
        val contentType = connection.contentType.orEmpty()
        val mimeType =
            contentType.substringBefore(';').trim().ifBlank {
                "application/octet-stream"
            }
        val encoding =
            contentType.substringAfter("charset=", "").substringBefore(';').trim().ifBlank {
                connection.contentEncoding?.trim().orEmpty().ifBlank { "utf-8" }
            }
        val responseHeaders =
            connection.headerFields
                .filterKeys { key -> key != null }
                .mapValues { (_, values) ->
                    values?.joinToString(", ").orEmpty()
                }
        val bodyBytes =
            (
                try {
                    connection.inputStream
                } catch (_: Throwable) {
                    connection.errorStream
                }
                    ?: ByteArrayInputStream(ByteArray(0))
                )
                .use(InputStream::readBytes)
        WebResourceResponse(
            mimeType,
            encoding,
            connection.responseCode,
            connection.responseMessage ?: "OK",
            responseHeaders,
            ByteArrayInputStream(bodyBytes)
        ).also {
            connection.disconnect()
        }
    }.getOrElse { error ->
        AppLogger.e(TAG, "compose_dsl rewritten resource fetch failed: ${error.message}", error)
        null
    }
}

private fun parseComposeDslJsonArray(raw: String): List<Any?>? {
    return JsJavaBridgeDelegates.parsePlainJsonArray(raw)
}

private class ComposeDslWebViewPageBridge(
    private val hostContextProvider: () -> ComposeDslWebViewHostContext?,
    private val controllerKeyProvider: () -> String?,
    private val actionLane: ComposeDslWebViewActionLane
) {
    private fun executeJavascriptInterfaceInvocation(
        interfaceName: String,
        methodName: String,
        argsJson: String
    ): ComposeDslWebViewBlockingActionResult {
        val hostContext =
            hostContextProvider()
                ?: return ComposeDslWebViewBlockingActionResult(
                    actionResult = null,
                    message = "compose_dsl webview host context is unavailable"
                )
        val controllerKey = controllerKeyProvider()?.trim().orEmpty()
        if (controllerKey.isBlank()) {
            return ComposeDslWebViewBlockingActionResult(
                actionResult = null,
                message = "compose_dsl webview controller is unavailable"
            )
        }
        val normalizedInterfaceName = interfaceName.trim()
        val normalizedMethodName = methodName.trim()
        if (normalizedInterfaceName.isBlank() || normalizedMethodName.isBlank()) {
            return ComposeDslWebViewBlockingActionResult(
                actionResult = null,
                message = "webview javascript interface name and method are required"
            )
        }
        val args =
            parseComposeDslJsonArray(argsJson)
                ?: return ComposeDslWebViewBlockingActionResult(
                    actionResult = null,
                    message = "webview javascript interface arguments must be a JSON array"
                )
        val actionId =
            ComposeDslWebViewHostRegistry.findJavascriptInterfaceActionId(
                executionContextKey = hostContext.executionContextKey,
                controllerKey = controllerKey,
                interfaceName = normalizedInterfaceName,
                methodName = normalizedMethodName
            )
                ?: return ComposeDslWebViewBlockingActionResult(
                    actionResult = null,
                    message =
                        "javascript interface method is unavailable: $normalizedInterfaceName.$normalizedMethodName"
                )
        return hostContext.executeActionBlocking(actionId, args)
    }

    @JavascriptInterface
    fun listInterfaces(): String {
        val hostContext = hostContextProvider()
            ?: return buildComposeDslBridgeSuccess(emptyMap<String, List<String>>())
        val controllerKey = controllerKeyProvider()?.trim().orEmpty()
        if (controllerKey.isBlank()) {
            return buildComposeDslBridgeSuccess(emptyMap<String, List<String>>())
        }
        return buildComposeDslBridgeSuccess(
            ComposeDslWebViewHostRegistry.listJavascriptInterfaces(
                executionContextKey = hostContext.executionContextKey,
                controllerKey = controllerKey
            )
        )
    }

    @JavascriptInterface
    fun invoke(
        interfaceName: String,
        methodName: String,
        argsJson: String
    ): String {
        return try {
            val result =
                actionLane.runBlocking(WEBVIEW_BRIDGE_TIMEOUT_MS) {
                    executeJavascriptInterfaceInvocation(
                        interfaceName = interfaceName,
                        methodName = methodName,
                        argsJson = argsJson
                    )
                }
            if (result.message.isNullOrBlank()) {
                buildComposeDslBridgeSuccess(result.actionResult)
            } else {
                buildComposeDslBridgeError(result.message)
            }
        } catch (error: TimeoutException) {
            AppLogger.e(TAG, "compose_dsl webview javascript interface invocation timed out")
            buildComposeDslBridgeError("compose_dsl webview javascript interface invocation timed out")
        } catch (error: Throwable) {
            AppLogger.e(TAG, "compose_dsl webview javascript interface invocation failed", error)
            buildComposeDslBridgeError(
                error.message?.trim().orEmpty().ifBlank {
                    "compose_dsl webview javascript interface invocation failed"
                }
            )
        }
    }
}

private fun launchComposeDslExternalUri(
    context: android.content.Context,
    url: String
): Boolean {
    val normalizedUrl = url.trim()
    if (normalizedUrl.isBlank()) {
        return false
    }
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrElse { error ->
        AppLogger.e(TAG, "compose_dsl failed to open external url: $normalizedUrl, error=${error.message}", error)
        false
    }
}

@Composable
internal fun renderWebViewNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val context = LocalContext.current
    val hostContext = LocalComposeDslWebViewHost.current
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val controllerDescriptor = buildComposeDslWebViewControllerDescriptor(props)
    val request =
        buildComposeDslWebViewRequest(
            props = props,
            allowBlank = controllerDescriptor != null
        )
    val modifier =
        applyScopedCommonModifier(Modifier, props, modifierResolver).let { base ->
            if (props.bool("nestedScrollInterop", false)) {
                base.nestedScroll(nestedScrollInterop)
            } else {
                base
            }
        }
    val callbackIds =
        ComposeDslWebViewCallbackIds(
            onPageStarted = ToolPkgComposeDslParser.extractActionId(props["onPageStarted"]),
            onPageFinished = ToolPkgComposeDslParser.extractActionId(props["onPageFinished"]),
            onReceivedError = ToolPkgComposeDslParser.extractActionId(props["onReceivedError"]),
            onReceivedHttpError = ToolPkgComposeDslParser.extractActionId(props["onReceivedHttpError"]),
            onReceivedSslError = ToolPkgComposeDslParser.extractActionId(props["onReceivedSslError"]),
            onDownloadStart = ToolPkgComposeDslParser.extractActionId(props["onDownloadStart"]),
            onConsoleMessage = ToolPkgComposeDslParser.extractActionId(props["onConsoleMessage"]),
            onUrlChanged = ToolPkgComposeDslParser.extractActionId(props["onUrlChanged"]),
            onProgressChanged = ToolPkgComposeDslParser.extractActionId(props["onProgressChanged"]),
            onStateChanged = ToolPkgComposeDslParser.extractActionId(props["onStateChanged"]),
            onLifecycleEvent = ToolPkgComposeDslParser.extractActionId(props["onLifecycleEvent"]),
            onShouldOverrideUrlLoading = ToolPkgComposeDslParser.extractActionId(props["onShouldOverrideUrlLoading"]),
            onInterceptRequest = ToolPkgComposeDslParser.extractActionId(props["onInterceptRequest"])
        )
    val bindableControllerDescriptor =
        if (
            controllerDescriptor != null &&
                hostContext != null &&
                (
                    controllerDescriptor.executionContextKey.isNullOrBlank() ||
                        controllerDescriptor.executionContextKey == hostContext.executionContextKey
                    ) &&
                (
                    controllerDescriptor.routeInstanceId.isNullOrBlank() ||
                        controllerDescriptor.routeInstanceId == hostContext.routeInstanceId
                    )
        ) {
            controllerDescriptor
        } else {
            null
        }
    val webViewScopeKey = remember {
        "webview:javascriptInterfaces"
    }
    val callbackIdsRef = remember(webViewScopeKey) { AtomicReference(callbackIds) }
    val onActionRef = remember(webViewScopeKey) { AtomicReference<(String, Any?) -> Unit>(onAction) }
    val hostContextRef = remember(webViewScopeKey) { AtomicReference(hostContext) }
    val controllerDescriptorRef =
        remember(webViewScopeKey) { AtomicReference(bindableControllerDescriptor) }
    val initialState =
        remember(webViewScopeKey, request.url, request.baseUrl) {
            ComposeDslWebViewStateSnapshot(
                url = request.url ?: request.baseUrl ?: "about:blank",
                title = null,
                loading = false,
                progress = 0,
                canGoBack = false,
                canGoForward = false
            )
        }
    val stateRef = remember(webViewScopeKey) { AtomicReference(initialState) }
    val pendingRewriteUrlRef = remember(webViewScopeKey) { AtomicReference<String?>(null) }
    val disposedRef = remember(webViewScopeKey) { AtomicBoolean(false) }
    val webViewRef = remember(webViewScopeKey) { AtomicReference<WebView?>(null) }
    val scriptHandlerRef = remember(webViewScopeKey) { AtomicReference<ScriptHandler?>(null) }
    val actionLane =
        remember(webViewScopeKey) {
            ComposeDslWebViewActionLane(
                label =
                    listOfNotNull(
                        hostContext?.routeInstanceId?.ifBlank { null },
                        bindableControllerDescriptor?.key?.ifBlank { null }
                    ).joinToString(":").ifBlank { "default" }
            )
        }

    SideEffect {
        callbackIdsRef.set(callbackIds)
        onActionRef.set(onAction)
        hostContextRef.set(hostContext)
        controllerDescriptorRef.set(bindableControllerDescriptor)
    }

    fun emitAction(
        actionId: String?,
        payload: Any?
    ) {
        val normalizedActionId = actionId?.trim().orEmpty()
        if (normalizedActionId.isBlank()) {
            return
        }
        val dispatch = {
            onActionRef.get().invoke(normalizedActionId, payload)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch()
        } else {
            composeDslWebViewMainHandler.post(dispatch)
        }
    }

    fun updateStateSnapshot(
        view: WebView?,
        loading: Boolean? = null,
        progress: Int? = null,
        urlOverride: String? = null,
        forceEmit: Boolean = false
    ): ComposeDslWebViewStateSnapshot {
        val previous = stateRef.get()
        val snapshot =
            ComposeDslWebViewStateSnapshot(
                url = urlOverride ?: view?.url ?: previous.url,
                title = view?.title ?: previous.title,
                loading = loading ?: previous.loading,
                progress = (progress ?: previous.progress).coerceIn(0, 100),
                canGoBack = view?.canGoBack() ?: previous.canGoBack,
                canGoForward = view?.canGoForward() ?: previous.canGoForward
            )
        stateRef.set(snapshot)
        val boundController = controllerDescriptorRef.get()
        val currentHostContext = hostContextRef.get()
        if (boundController != null && currentHostContext != null) {
            ComposeDslWebViewHostRegistry.updateState(
                executionContextKey = currentHostContext.executionContextKey,
                controllerKey = boundController.key,
                state = snapshot
            )
        }
        if (forceEmit || snapshot != previous) {
            emitAction(callbackIdsRef.get().onStateChanged, snapshot.toPayload())
        }
        return snapshot
    }

    fun emitLifecycle(
        type: String,
        state: ComposeDslWebViewStateSnapshot = stateRef.get(),
        extras: Map<String, Any?> = emptyMap()
    ) {
        emitAction(
            callbackIdsRef.get().onLifecycleEvent,
            buildComposeDslWebViewLifecyclePayload(
                type = type,
                state = state,
                extras = extras
            )
        )
    }

    fun notifyUrlChanged(
        url: String?,
        isMainFrame: Boolean,
        method: String?
    ) {
        val normalizedUrl = url?.trim().orEmpty()
        if (normalizedUrl.isBlank()) {
            return
        }
        emitAction(
            callbackIdsRef.get().onUrlChanged,
            mapOf(
                "url" to normalizedUrl,
                "isMainFrame" to isMainFrame,
                "method" to method
            )
        )
    }

    fun installJavascriptInterfacesIfPossible(view: WebView?) {
        if (view == null) {
            return
        }
        refreshComposeDslJavascriptInterfaces(view)
    }

    var pendingFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val fileChooserLauncher: ActivityResultLauncher<Intent>? =
        if (activityResultRegistryOwner != null) {
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val callback = pendingFileChooserCallback
                pendingFileChooserCallback = null
                if (callback == null) {
                    return@rememberLauncherForActivityResult
                }
                val parsedUris =
                    WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
                val intent = result.data
                val directData = intent?.data?.let { arrayOf(it) }
                val clipData =
                    intent?.clipData?.let { clip ->
                        Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                    }
                val uris = parsedUris ?: directData ?: clipData
                callback.onReceiveValue(uris)
            }
        } else {
            null
        }
    val pageBridge =
        remember(webViewScopeKey) {
            ComposeDslWebViewPageBridge(
                hostContextProvider = { hostContextRef.get() },
                controllerKeyProvider = { controllerDescriptorRef.get()?.key },
                actionLane = actionLane
            )
        }

    val webView = remember(context) {
        WebViewConfig.createWebView(context).apply {
            webViewRef.set(this)
            disposedRef.set(false)
            addJavascriptInterface(pageBridge, WEBVIEW_INTERNAL_JS_INTERFACE_BRIDGE_NAME)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                scriptHandlerRef.set(
                    runCatching {
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            buildComposeDslWebViewJavascriptInterfaceRuntimeScript(),
                            setOf("*")
                        )
                    }.getOrElse { error ->
                        AppLogger.e(TAG, "Failed to add WebView javascript interface document-start script", error)
                        null
                    }
                )
            }
            webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        if (disposedRef.get()) {
                            return false
                        }
                        val actualRequest = request ?: return false
                        val requestUrl = actualRequest.url.toString().trim()
                        if (requestUrl.isBlank()) {
                            return false
                        }
                        val pendingRewriteUrl = pendingRewriteUrlRef.get()?.trim().orEmpty()
                        if (pendingRewriteUrl.isNotBlank() && pendingRewriteUrl == requestUrl) {
                            pendingRewriteUrlRef.set(null)
                            val currentHostContext = hostContextRef.get()
                            val boundController = controllerDescriptorRef.get()
                            if (currentHostContext != null && boundController != null) {
                                ComposeDslWebViewHostRegistry.consumePendingRewrite(
                                    executionContextKey = currentHostContext.executionContextKey,
                                    controllerKey = boundController.key,
                                    url = requestUrl
                                )
                            }
                            return false
                        }
                        val currentHostContext = hostContextRef.get() ?: return false
                        val actionId =
                            callbackIdsRef.get().onShouldOverrideUrlLoading
                                ?.takeIf { it.isNotBlank() }
                                ?: return false
                        val result =
                            try {
                                actionLane.runBlocking(WEBVIEW_NAVIGATION_TIMEOUT_MS) {
                                    currentHostContext.executeActionBlocking(
                                        actionId = actionId,
                                        payload = buildComposeDslWebViewRequestPayload(actualRequest)
                                    )
                                }
                            } catch (_: TimeoutException) {
                                AppLogger.e(
                                    TAG,
                                    "compose_dsl webview navigation interception timed out: url=$requestUrl"
                                )
                                return false
                            } catch (error: Throwable) {
                                AppLogger.e(
                                    TAG,
                                    "compose_dsl webview navigation interception failed: url=$requestUrl, error=${error.message}",
                                    error
                                )
                                return false
                            }
                        if (!result.message.isNullOrBlank()) {
                            AppLogger.e(
                                TAG,
                                "compose_dsl webview navigation interceptor returned error: url=$requestUrl, error=${result.message}"
                            )
                            return false
                        }
                        val decision = parseComposeDslWebViewNavigationDecision(result.actionResult)
                        if (decision == null) {
                            return false
                        }
                        return when (decision.action) {
                            "allow" -> false
                            "cancel" -> true
                            "external" -> {
                                val externalUrl = decision.url?.trim().orEmpty().ifBlank { requestUrl }
                                launchComposeDslExternalUri(context, externalUrl)
                            }

                            "rewrite" -> {
                                val targetView = view ?: return false
                                val rewrittenUrl = decision.url?.trim().orEmpty()
                                if (rewrittenUrl.isBlank()) {
                                    return false
                                }
                                val rewrittenHeaders =
                                    actualRequest.requestHeaders
                                        .orEmpty()
                                        .toMutableMap()
                                        .apply { putAll(decision.headers) }
                                pendingRewriteUrlRef.set(rewrittenUrl)
                                val boundController = controllerDescriptorRef.get()
                                if (boundController != null) {
                                    ComposeDslWebViewHostRegistry.markPendingRewrite(
                                        executionContextKey = currentHostContext.executionContextKey,
                                        controllerKey = boundController.key,
                                        url = rewrittenUrl
                                    )
                                }
                                if (rewrittenHeaders.isEmpty()) {
                                    targetView.loadUrl(rewrittenUrl)
                                } else {
                                    targetView.loadUrl(rewrittenUrl, rewrittenHeaders)
                                }
                                true
                            }

                            else -> false
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (disposedRef.get()) {
                            return request?.let { actualRequest ->
                                super.shouldInterceptRequest(view, actualRequest)
                            }
                        }
                        val actualRequest = request ?: return null
                        val currentHostContext = hostContextRef.get()
                            ?: return super.shouldInterceptRequest(view, actualRequest)
                        val actionId =
                            callbackIdsRef.get().onInterceptRequest
                                ?.takeIf { it.isNotBlank() }
                                ?: return super.shouldInterceptRequest(view, actualRequest)
                        val requestUrl = actualRequest.url.toString()
                        val result =
                            try {
                                actionLane.runBlocking(WEBVIEW_RESOURCE_TIMEOUT_MS) {
                                    currentHostContext.executeActionBlocking(
                                        actionId = actionId,
                                        payload = buildComposeDslWebViewRequestPayload(actualRequest)
                                    )
                                }
                            } catch (_: TimeoutException) {
                                AppLogger.e(
                                    TAG,
                                    "compose_dsl webview resource interception timed out: url=$requestUrl"
                                )
                                return super.shouldInterceptRequest(view, actualRequest)
                            } catch (error: Throwable) {
                                AppLogger.e(
                                    TAG,
                                    "compose_dsl webview resource interception failed: url=$requestUrl, error=${error.message}",
                                    error
                                )
                                return super.shouldInterceptRequest(view, actualRequest)
                            }
                        if (!result.message.isNullOrBlank()) {
                            AppLogger.e(
                                TAG,
                                "compose_dsl webview resource interceptor returned error: url=$requestUrl, error=${result.message}"
                            )
                            return super.shouldInterceptRequest(view, actualRequest)
                        }
                        val decision = parseComposeDslWebViewResourceDecision(result.actionResult)
                            ?: return super.shouldInterceptRequest(view, actualRequest)
                        return when (decision.action) {
                            "allow" -> super.shouldInterceptRequest(view, actualRequest)
                            "block" -> buildComposeDslBlockedWebResourceResponse()
                            "rewrite" -> {
                                val rewrittenUrl = decision.url?.trim().orEmpty()
                                if (rewrittenUrl.isBlank()) {
                                    super.shouldInterceptRequest(view, actualRequest)
                                } else {
                                    fetchComposeDslRewrittenResource(
                                        request = actualRequest,
                                        rewrittenUrl = rewrittenUrl,
                                        rewrittenHeaders = decision.headers
                                    ) ?: super.shouldInterceptRequest(view, actualRequest)
                                }
                            }

                            "respond" -> {
                                decision.response?.let { response ->
                                    buildComposeDslWebResourceResponse(spec = response)
                                }
                                    ?: super.shouldInterceptRequest(view, actualRequest)
                            }

                            else -> super.shouldInterceptRequest(view, actualRequest)
                        }
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        url: String?,
                        isReload: Boolean
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (disposedRef.get()) {
                            return
                        }
                        val state =
                            updateStateSnapshot(
                                view = view,
                                urlOverride = url
                            )
                        notifyUrlChanged(
                            url = state.url,
                            isMainFrame = true,
                            method = null
                        )
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        if (disposedRef.get()) {
                            return
                        }
                        installJavascriptInterfacesIfPossible(view)
                        val state =
                            updateStateSnapshot(
                                view = view,
                                loading = true,
                                progress = 0,
                                urlOverride = url
                            )
                        emitAction(
                            callbackIdsRef.get().onPageStarted,
                            mapOf(
                                "url" to state.url,
                                "title" to state.title,
                                "canGoBack" to state.canGoBack,
                                "canGoForward" to state.canGoForward
                            )
                        )
                    }

                    override fun onPageCommitVisible(
                        view: WebView?,
                        url: String?
                    ) {
                        super.onPageCommitVisible(view, url)
                        if (disposedRef.get()) {
                            return
                        }
                        installJavascriptInterfacesIfPossible(view)
                        val state =
                            updateStateSnapshot(
                                view = view,
                                urlOverride = url
                            )
                        emitLifecycle("pageCommitVisible", state)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (disposedRef.get()) {
                            return
                        }
                        installJavascriptInterfacesIfPossible(view)
                        val state =
                            updateStateSnapshot(
                                view = view,
                                loading = false,
                                progress = 100,
                                urlOverride = url
                            )
                        emitAction(
                            callbackIdsRef.get().onPageFinished,
                            mapOf(
                                "url" to state.url,
                                "title" to state.title,
                                "canGoBack" to state.canGoBack,
                                "canGoForward" to state.canGoForward
                            )
                        )
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        if (disposedRef.get()) {
                            return
                        }
                        emitAction(
                            callbackIdsRef.get().onReceivedError,
                            mapOf(
                                "errorCode" to errorCode,
                                "description" to description,
                                "url" to failingUrl
                            )
                        )
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (disposedRef.get()) {
                            return
                        }
                        if (request?.isForMainFrame == true) {
                            updateStateSnapshot(
                                view = view,
                                loading = false,
                                urlOverride = request.url?.toString()
                            )
                        }
                        emitAction(
                            callbackIdsRef.get().onReceivedError,
                            mapOf(
                                "errorCode" to error?.errorCode,
                                "description" to error?.description?.toString(),
                                "url" to request?.url?.toString(),
                                "isMainFrame" to (request?.isForMainFrame ?: true)
                            )
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (disposedRef.get()) {
                            return
                        }
                        emitAction(
                            callbackIdsRef.get().onReceivedHttpError,
                            mapOf(
                                "statusCode" to errorResponse?.statusCode,
                                "reasonPhrase" to errorResponse?.reasonPhrase,
                                "url" to request?.url?.toString(),
                                "isMainFrame" to (request?.isForMainFrame ?: true)
                            )
                        )
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        super.onReceivedSslError(view, handler, error)
                        if (disposedRef.get()) {
                            return
                        }
                        emitAction(
                            callbackIdsRef.get().onReceivedSslError,
                            mapOf(
                                "primaryError" to error?.primaryError,
                                "url" to error?.url
                            )
                        )
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?
                    ): Boolean {
                        val state =
                            updateStateSnapshot(
                                view = view,
                                loading = false
                            )
                        emitLifecycle(
                            type = "renderProcessGone",
                            state = state,
                            extras =
                                mapOf(
                                    "didCrash" to detail?.didCrash(),
                                    "rendererPriorityAtExit" to detail?.rendererPriorityAtExit()
                                )
                        )
                        return true
                    }
                }

            webChromeClient =
                object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message?
                    ): Boolean {
                        val message = resultMsg ?: return false
                        val transport = message.obj as? WebView.WebViewTransport ?: return false
                        transport.webView = view
                        message.sendToTarget()
                        return true
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if (!disposedRef.get() && consoleMessage != null) {
                            emitAction(
                                callbackIdsRef.get().onConsoleMessage,
                                mapOf(
                                    "message" to consoleMessage.message(),
                                    "sourceId" to consoleMessage.sourceId(),
                                    "lineNumber" to consoleMessage.lineNumber(),
                                    "level" to consoleMessage.messageLevel().name
                                )
                            )
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }

                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (disposedRef.get()) {
                            return
                        }
                        val previous = stateRef.get()
                        val state =
                            updateStateSnapshot(
                                view = view,
                                loading = if (newProgress >= 100) previous.loading else true,
                                progress = newProgress
                            )
                        emitAction(
                            callbackIdsRef.get().onProgressChanged,
                            mapOf(
                                "progress" to state.progress,
                                "url" to state.url,
                                "title" to state.title
                            )
                        )
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        if (filePathCallback == null) {
                            return false
                        }
                        val launcher = fileChooserLauncher
                        if (launcher == null) {
                            filePathCallback.onReceiveValue(null)
                            return false
                        }
                        pendingFileChooserCallback?.onReceiveValue(null)
                        pendingFileChooserCallback = filePathCallback
                        return try {
                            val intent =
                                fileChooserParams?.createIntent()
                                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "*/*"
                                    }
                            if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                            launcher.launch(intent)
                            true
                        } catch (error: Throwable) {
                            AppLogger.e(TAG, "compose_dsl webview failed to open file chooser", error)
                            pendingFileChooserCallback?.onReceiveValue(null)
                            pendingFileChooserCallback = null
                            false
                        }
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin.orEmpty(), true, false)
                    }

                    override fun onJsAlert(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: android.webkit.JsResult?
                    ): Boolean {
                        result?.confirm()
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: android.webkit.JsResult?
                    ): Boolean {
                        result?.confirm()
                        return true
                    }

                    override fun onJsPrompt(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        defaultValue: String?,
                        result: android.webkit.JsPromptResult?
                    ): Boolean {
                        result?.confirm(defaultValue)
                        return true
                    }
                }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                if (!disposedRef.get() && !url.isNullOrBlank()) {
                    emitAction(
                        callbackIdsRef.get().onDownloadStart,
                        mapOf(
                            "url" to url,
                            "userAgent" to userAgent,
                            "contentDisposition" to contentDisposition,
                            "mimeType" to mimeType,
                            "contentLength" to contentLength,
                            "suggestedFileName" to
                                URLUtil.guessFileName(url, contentDisposition, mimeType)
                        )
                    )
                }
            }
        }
    }

    DisposableEffect(
        webView,
        bindableControllerDescriptor?.key,
        hostContext?.executionContextKey
    ) {
        if (bindableControllerDescriptor != null && hostContext != null) {
            ComposeDslWebViewHostRegistry.bind(
                executionContextKey = hostContext.executionContextKey,
                routeInstanceId = hostContext.routeInstanceId,
                controllerKey = bindableControllerDescriptor.key,
                webView = webView,
                stateRef = stateRef
            )
            ComposeDslWebViewHostRegistry.updateState(
                executionContextKey = hostContext.executionContextKey,
                controllerKey = bindableControllerDescriptor.key,
                state = stateRef.get()
            )
        }
        onDispose {
            if (bindableControllerDescriptor != null && hostContext != null) {
                ComposeDslWebViewHostRegistry.unbind(
                    executionContextKey = hostContext.executionContextKey,
                    controllerKey = bindableControllerDescriptor.key,
                    webView = webView
                )
            }
        }
    }

    DisposableEffect(webView) {
        webView.post {
            webView.requestFocus()
        }
        val createdState =
            updateStateSnapshot(
                view = webView,
                urlOverride = webView.url ?: stateRef.get().url,
                forceEmit = true
            )
        emitLifecycle("created", createdState)
        onDispose {
            val disposedState =
                updateStateSnapshot(
                    view = webView,
                    loading = false,
                    forceEmit = true
                )
            emitLifecycle("disposed", disposedState)
            disposedRef.set(true)
            pendingFileChooserCallback?.onReceiveValue(null)
            pendingFileChooserCallback = null
            scriptHandlerRef.getAndSet(null)?.let { handler ->
                runCatching { handler.remove() }
            }
            webViewRef.set(null)
            actionLane.shutdown()
            try {
                webView.removeJavascriptInterface(WEBVIEW_INTERNAL_JS_INTERFACE_BRIDGE_NAME)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(
        webView,
        request.url,
        request.html,
        request.baseUrl,
        request.mimeType,
        request.encoding,
        request.headers,
        props.bool("javaScriptEnabled", true),
        props.bool("domStorageEnabled", true),
        props.bool("allowFileAccess", true),
        props.bool("allowContentAccess", true),
        props.bool("supportZoom", true),
        props.bool("useWideViewPort", true),
        props.bool("loadWithOverviewMode", true),
        props.stringOrNull("userAgent")
    ) {
        webView.settings.apply {
            javaScriptEnabled = props.bool("javaScriptEnabled", true)
            domStorageEnabled = props.bool("domStorageEnabled", true)
            databaseEnabled = props.bool("databaseEnabled", true)
            javaScriptCanOpenWindowsAutomatically =
                props.bool("javaScriptCanOpenWindowsAutomatically", true)
            setSupportMultipleWindows(props.bool("supportMultipleWindows", true))
            allowFileAccess = props.bool("allowFileAccess", true)
            allowContentAccess = props.bool("allowContentAccess", true)
            allowFileAccessFromFileURLs = props.bool("allowFileAccessFromFileURLs", true)
            allowUniversalAccessFromFileURLs =
                props.bool("allowUniversalAccessFromFileURLs", true)
            val supportZoom = props.bool("supportZoom", true)
            setSupportZoom(supportZoom)
            builtInZoomControls = props.bool("builtInZoomControls", supportZoom)
            displayZoomControls = props.bool("displayZoomControls", false)
            loadWithOverviewMode = props.bool("loadWithOverviewMode", true)
            useWideViewPort = props.bool("useWideViewPort", true)
            mediaPlaybackRequiresUserGesture =
                props.bool("mediaPlaybackRequiresUserGesture", false)
            textZoom = props.int("textZoom", 100)
            cacheMode = props.webViewCacheMode("cacheMode")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = props.webViewMixedContentMode("mixedContentMode")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = props.bool("safeBrowsingEnabled", true)
            }
            props.stringOrNull("userAgent")?.let { userAgentString = it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(
                webView,
                props.bool("acceptThirdPartyCookies", true)
            )
        }
        installJavascriptInterfacesIfPossible(webView)
        val url = request.url
        if (url != null) {
            if (request.headers.isEmpty()) {
                webView.loadUrl(url)
            } else {
                webView.loadUrl(url, request.headers)
            }
        } else if (request.html != null) {
            val htmlToLoad = injectComposeDslWebViewBridgeRuntimeIntoHtml(request.html.orEmpty())
            webView.loadDataWithBaseURL(
                request.baseUrl,
                htmlToLoad,
                request.mimeType,
                request.encoding,
                null
            )
        } else {
            webView.loadUrl("about:blank")
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView }
    )
}

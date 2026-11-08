package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.runtime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.ParsedUserscriptMetadata
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptCapabilityRegistry
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptInstallSourceType
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptMatcher
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptSupportState
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.install.UserscriptImportCoordinator
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.storage.UserscriptRepository
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.ui.WebSessionUserscriptUiStateStore
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class WebSessionUserscriptManager(
    private val context: Context,
    private val onOpenUserscriptUi: () -> Unit,
    private val onOpenTab: (url: String, active: Boolean) -> String?,
    private val onActivateSession: (sessionId: String) -> Unit,
    private val onCloseSession: (sessionId: String) -> Boolean,
    private val onDownload: (sessionId: String, url: String, fileName: String?) -> Unit,
    private val onMenuCommandsChanged: (sessionId: String?) -> Unit,
    private val onToast: (message: String) -> Unit
) {
    private data class SessionBinding(
        val sessionId: String,
        val webView: WebView,
        val scriptHandler: ScriptHandler?,
        val menuCommands: LinkedHashMap<String, UserscriptPageMenuCommand> = linkedMapOf()
    )

    private data class SessionPageState(
        var pageUrl: String = "about:blank",
        val scriptStatuses: ConcurrentHashMap<Long, UserscriptPageRuntimeStatus> = ConcurrentHashMap()
    )

    companion object {
        private const val TAG = "WebSessionUserscript"
        private const val NOTIFICATION_CHANNEL_ID = "userscript_notifications"
        private const val NOTIFICATION_ID = 50142
    }

    private val repository = UserscriptRepository.getInstance(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val storageNotifier = UserscriptStorageNotifier()
    private val tabStateStore = UserscriptTabStateStore()
    private val cookieService = UserscriptCookieService(CookieManager.getInstance())
    private val webRequestEngine = UserscriptWebRequestEngine()
    private val requestClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private val sessionBindings = ConcurrentHashMap<String, SessionBinding>()
    private val sessionPageStates = ConcurrentHashMap<String, SessionPageState>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val abortedRequestKeys = ConcurrentHashMap.newKeySet<String>()
    private val openedTabOwners = ConcurrentHashMap<String, String>()
    private val audioMuteStates = ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var visibleSessionId: String? = null
    private val supportState =
        UserscriptSupportState(
            isSupported =
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                    WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER),
            reason =
                when {
                    !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ->
                        "Current WebView does not support document-start script injection"
                    !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ->
                        "Current WebView does not support native userscript messaging"
                    else -> null
                }
        )

    val uiStore = WebSessionUserscriptUiStateStore(initialSupportState = supportState)

    init {
        scope.launch {
            repository.installedScriptsFlow.collectLatest { scripts ->
                uiStore.updateScripts(scripts)
                rebuildAllSessionBaselines()
            }
        }
        scope.launch {
            repository.observeRecentLogs().collectLatest { logs ->
                uiStore.updateLogs(logs)
            }
        }
    }

    fun supportState(): UserscriptSupportState = supportState

    fun updateVisibleSession(
        sessionId: String?,
        pageUrl: String?
    ) {
        visibleSessionId = sessionId
        if (!sessionId.isNullOrBlank() && !pageUrl.isNullOrBlank()) {
            val state = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
            if (state.pageUrl != pageUrl) {
                state.pageUrl = pageUrl
                if (state.scriptStatuses.isEmpty()) {
                    rebuildSessionBaseline(sessionId)
                    return
                }
                publishVisibleStatuses()
                return
            }
            if (state.scriptStatuses.isEmpty() && pageUrl != "about:blank") {
                rebuildSessionBaseline(sessionId)
                return
            }
        }
        publishVisibleStatuses()
    }

    fun onPageChanged(
        sessionId: String,
        pageUrl: String,
        forceReset: Boolean = false
    ) {
        val state = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
        if (!forceReset && state.pageUrl == pageUrl && state.scriptStatuses.isNotEmpty()) {
            return
        }
        state.pageUrl = pageUrl
        state.scriptStatuses.clear()
        webRequestEngine.clearSession(sessionId)
        rebuildSessionBaseline(sessionId)
    }

    fun syncUrlChange(
        sessionId: String,
        pageUrl: String
    ) {
        val state = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
        state.pageUrl = pageUrl
        publishVisibleStatuses()
    }

    fun attachSession(
        sessionId: String,
        webView: WebView
    ) {
        if (!supportState.isSupported) {
            return
        }
        val existing = sessionBindings[sessionId]
        if (existing?.webView === webView) {
            return
        }
        sessionPageStates.putIfAbsent(sessionId, SessionPageState())
        val attachNow = {
            runCatching { existing?.scriptHandler?.remove() }
            val scriptHandler =
                runCatching {
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        UserscriptBootstrapScript.documentStartScript(),
                        setOf("*")
                    )
                }.getOrElse { error ->
                    AppLogger.e(TAG, "Failed to add document-start userscript runtime", error)
                    null
                }
            runCatching {
                WebViewCompat.addWebMessageListener(
                    webView,
                    UserscriptBootstrapScript.BRIDGE_NAME,
                    setOf("*"),
                    object : WebViewCompat.WebMessageListener {
                        override fun onPostMessage(
                            view: WebView,
                            message: WebMessageCompat,
                            sourceOrigin: android.net.Uri,
                            isMainFrame: Boolean,
                            replyProxy: JavaScriptReplyProxy
                        ) {
                            handleBridgeMessage(
                                sessionId = sessionId,
                                webView = view,
                                rawMessage = message.data.orEmpty(),
                                replyProxy = replyProxy,
                                isMainFrame = isMainFrame
                            )
                        }
                    }
                )
            }.onFailure { error ->
                AppLogger.e(TAG, "Failed to add userscript message listener", error)
            }
            sessionBindings[sessionId] =
                SessionBinding(
                    sessionId = sessionId,
                    webView = webView,
                    scriptHandler = scriptHandler
                )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            attachNow()
        } else {
            mainHandler.post(attachNow)
        }
    }

    fun detachSession(sessionId: String) {
        val binding = sessionBindings.remove(sessionId) ?: return
        sessionPageStates.remove(sessionId)
        if (visibleSessionId == sessionId) {
            visibleSessionId = null
        }
        val removeNow = {
            runCatching { binding.scriptHandler?.remove() }
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeNow()
        } else {
            mainHandler.post(removeNow)
        }
        activeCalls.entries.removeAll { entry ->
            val remove = entry.key.startsWith("$sessionId:")
            if (remove) {
                runCatching { entry.value.cancel() }
            }
            remove
        }
        webRequestEngine.clearSession(sessionId)
        tabStateStore.clearSession(sessionId)
        audioMuteStates.remove(sessionId)
        openedTabOwners.remove(sessionId)?.let { ownerSessionId ->
            dispatchHostEvent(
                ownerSessionId,
                "open_tab_closed",
                JSONObject().put("sessionId", sessionId)
            )
        }
        onMenuCommandsChanged(sessionId)
        publishVisibleStatuses()
    }

    fun getMenuCommands(sessionId: String?): List<UserscriptPageMenuCommand> {
        if (sessionId.isNullOrBlank()) {
            return emptyList()
        }
        return sessionBindings[sessionId]?.menuCommands?.values?.toList().orEmpty()
    }

    fun invokeMenuCommand(
        sessionId: String?,
        commandId: String
    ) {
        if (sessionId.isNullOrBlank() || commandId.isBlank()) {
            return
        }
        val binding = sessionBindings[sessionId] ?: return
        mainHandler.post {
            val escaped = JSONObject.quote(commandId)
            binding.webView.evaluateJavascript(
                """
                (function() {
                    if (window.__operitUserscriptRuntime &&
                        typeof window.__operitUserscriptRuntime.invokeMenuCommand === "function") {
                        window.__operitUserscriptRuntime.invokeMenuCommand($escaped);
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun beginUrlInstall(rawUrl: String, sourceType: UserscriptInstallSourceType = UserscriptInstallSourceType.REMOTE_URL) {
        if (!supportState.isSupported) {
            mainHandler.post(onOpenUserscriptUi)
            return
        }
        val normalizedUrl = rawUrl.trim()
        val scheme =
            runCatching { android.net.Uri.parse(normalizedUrl).scheme?.lowercase() }.getOrNull()
        if (normalizedUrl.isBlank() || (scheme != "http" && scheme != "https")) {
            onToast(context.getString(R.string.web_session_userscript_invalid_url))
            return
        }
        scope.launch {
            runCatching {
                repository.fetchRemotePreview(normalizedUrl, sourceType)
            }.onSuccess { preview ->
                uiStore.setPendingInstall(preview)
                mainHandler.post(onOpenUserscriptUi)
            }.onFailure { error ->
                repository.log(null, "error", normalizedUrl, error.message ?: "userscript install preview failed")
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun beginLocalImport() {
        if (!supportState.isSupported) {
            mainHandler.post(onOpenUserscriptUi)
            return
        }
        scope.launch {
            val result = UserscriptImportCoordinator.requestImport(context.applicationContext)
            if (result == null) {
                return@launch
            }
            runCatching {
                repository.prepareInstallPreview(
                    rawSource = result.rawSource,
                    sourceType = UserscriptInstallSourceType.LOCAL_FILE,
                    sourceUrl = result.sourceUri,
                    sourceDisplay = result.displayName
                )
            }.onSuccess { preview ->
                uiStore.setPendingInstall(preview)
                mainHandler.post(onOpenUserscriptUi)
            }.onFailure { error ->
                repository.log(null, "error", result.sourceUri, error.message ?: "userscript local import failed")
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun confirmPendingInstall() {
        if (!supportState.isSupported) {
            mainHandler.post(onOpenUserscriptUi)
            return
        }
        val preview = uiStore.state.value.pendingInstall ?: return
        scope.launch {
            runCatching {
                repository.install(preview)
            }.onSuccess { installed ->
                uiStore.setPendingInstall(null)
                mainHandler.post {
                    onToast(
                        context.getString(
                            R.string.web_session_userscript_installed,
                            installed.name
                        )
                    )
                    onOpenUserscriptUi()
                }
            }.onFailure { error ->
                repository.log(preview.existingScriptId, "error", preview.sourceUrl, error.message ?: "userscript install failed")
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun cancelPendingInstall() {
        uiStore.setPendingInstall(null)
    }

    fun setScriptEnabled(
        scriptId: Long,
        enabled: Boolean
    ) {
        scope.launch {
            repository.setEnabled(scriptId, enabled)
        }
    }

    fun deleteScript(scriptId: Long) {
        scope.launch {
            repository.deleteUserscript(scriptId)
            mainHandler.post {
                onToast(context.getString(R.string.web_session_userscript_deleted))
            }
        }
    }

    fun checkForUpdate(scriptId: Long) {
        scope.launch {
            val preview = repository.checkForUpdate(scriptId)
            if (preview != null) {
                uiStore.setPendingInstall(preview)
                mainHandler.post(onOpenUserscriptUi)
            } else {
                mainHandler.post {
                    onToast(context.getString(R.string.web_session_userscript_no_update))
                }
            }
        }
    }

    fun currentInstalledScript(scriptId: Long, callback: (UserscriptListItem?) -> Unit) {
        scope.launch {
            val item = repository.getInstalledScript(scriptId)
            mainHandler.post { callback(item) }
        }
    }

    fun interceptWebRequest(
        sessionId: String,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url?.toString().orEmpty()
        if (url.isBlank()) {
            return null
        }
        val resolution =
            webRequestEngine.resolve(
                sessionId = sessionId,
                url = url,
                requestType = resolveWebRequestType(request)
            )
        if (resolution.matches.isEmpty()) {
            return null
        }
        resolution.matches.forEach { match ->
            dispatchHostEvent(
                sessionId,
                "web_request_event",
                JSONObject()
                    .put("registrationId", match.registrationId)
                    .put("scriptId", match.scriptId)
                    .put("url", url)
                    .put("type", resolveWebRequestType(request))
                    .put("source", match.source)
            )
        }
        val action = resolution.mergedAction
        if (!action.requiresInterception()) {
            return null
        }
        if (action.cancel) {
            return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        val targetUrl = action.redirectUrl ?: url
        return runCatching {
            val requestBuilder = Request.Builder().url(targetUrl)
            request.requestHeaders.orEmpty().forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            action.requestHeaders.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            val response = requestClient.newCall(requestBuilder.get().build()).execute()
            val bodyBytes =
                action.responseBody?.toByteArray(Charsets.UTF_8)
                    ?: response.body?.bytes()
                    ?: ByteArray(0)
            val responseHeaders =
                response.headers.toMultimap()
                    .mapValues { (_, values) -> values.joinToString(", ") }
                    .toMutableMap()
                    .apply { putAll(action.responseHeaders) }
            val mimeType =
                response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.ifBlank { "application/octet-stream" }
                    ?: "application/octet-stream"
            val encoding =
                response.header("Content-Type")
                    ?.substringAfter("charset=", "")
                    ?.substringBefore(';')
                    ?.ifBlank { "utf-8" }
                    ?: "utf-8"
            WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message,
                responseHeaders,
                ByteArrayInputStream(bodyBytes)
            ).also {
                response.close()
            }
        }.getOrElse { error ->
            AppLogger.w(TAG, "userscript webRequest intercept failed: ${error.message}")
            null
        }
    }

    private fun handleBridgeMessage(
        sessionId: String,
        webView: WebView,
        rawMessage: String,
        replyProxy: JavaScriptReplyProxy,
        isMainFrame: Boolean
    ) {
        val message = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return
        val type = message.optString("type", "")
        val requestId = message.optString("requestId", "")
        val payload = message.optJSONObject("payload") ?: JSONObject()
        when (type) {
            "bootstrap_request" -> {
                scope.launch {
                    runCatching {
                        val href = payload.optString("href", "")
                        val isTopFrame = payload.optBoolean("isTopFrame", isMainFrame)
                        if (isMainFrame) {
                            onPageChanged(sessionId, href)
                        }
                        val bootstrapPayload = repository.buildBootstrapPayload(sessionId, href, isTopFrame)
                        if (isMainFrame) {
                            val state = sessionPageStates.getOrPut(sessionId) { SessionPageState(pageUrl = href) }
                            bootstrapPayload.scripts.forEach { script ->
                                state.scriptStatuses[script.scriptId] =
                                    UserscriptPageRuntimeStatus(
                                        state = UserscriptPageRuntimeState.QUEUED,
                                        detail = script.runAt
                                    )
                            }
                            publishVisibleStatuses()
                        }
                        postRpcSuccess(
                            replyProxy = replyProxy,
                            requestId = requestId,
                            payload =
                                JSONObject().put(
                                    "payloadJson",
                                    json.encodeToString(bootstrapPayload)
                                )
                        )
                    }.onFailure { error ->
                        postRpcError(replyProxy, requestId, error.message ?: "bootstrap_request_failed")
                    }
                }
            }

            "script_status" -> {
                if (!isMainFrame) {
                    return
                }
                val scriptId = payload.optLong("scriptId")
                if (scriptId > 0L) {
                    upsertRuntimeStatus(
                        sessionId = sessionId,
                        scriptId = scriptId,
                        rawState = payload.optString("state", ""),
                        detail = payload.optString("message", "").ifBlank { null }
                    )
                }
            }

            "runtime_log" -> {
                scope.launch {
                    repository.log(
                        userscriptId = payload.optLong("scriptId").takeIf { it > 0L },
                        level = payload.optString("level", "info"),
                        pageUrl = payload.optString("pageUrl", "").ifBlank { null },
                        message = payload.optString("message", "userscript runtime message")
                    )
                }
            }

            "menu_reset" -> {
                if (!isMainFrame) {
                    return
                }
                sessionBindings[sessionId]?.menuCommands?.clear()
                onMenuCommandsChanged(sessionId)
            }

            "register_menu_command" -> {
                if (!isMainFrame) {
                    return
                }
                val commandId = payload.optString("commandId", "").trim()
                val title = payload.optString("title", "").trim()
                val userscriptId = payload.optLong("scriptId")
                val binding = sessionBindings[sessionId]
                if (binding != null && commandId.isNotBlank() && title.isNotBlank()) {
                    binding.menuCommands[commandId] =
                        UserscriptPageMenuCommand(
                            commandId = commandId,
                            title = title,
                            userscriptId = userscriptId
                        )
                    onMenuCommandsChanged(sessionId)
                }
            }

            "unregister_menu_command" -> {
                if (!isMainFrame) {
                    return
                }
                val commandId = payload.optString("commandId", "").trim()
                sessionBindings[sessionId]?.menuCommands?.remove(commandId)
                onMenuCommandsChanged(sessionId)
            }

            "storage_set" -> {
                val scriptId = payload.optLong("scriptId")
                val key = payload.optString("key", "")
                val valueJson = payload.optString("valueJson", "null")
                if (scriptId > 0L && key.isNotBlank()) {
                    scope.launch {
                        persistValueAndBroadcast(
                            sourceSessionId = sessionId,
                            scriptId = scriptId,
                            key = key,
                            valueJson = valueJson
                        )
                    }
                }
            }

            "storage_set_many" -> {
                val scriptId = payload.optLong("scriptId")
                val values = payload.optJSONObject("values") ?: JSONObject()
                if (scriptId > 0L) {
                    scope.launch {
                        values.keys().forEach { key ->
                            persistValueAndBroadcast(
                                sourceSessionId = sessionId,
                                scriptId = scriptId,
                                key = key,
                                valueJson = values.optString(key, "null")
                            )
                        }
                    }
                }
            }

            "storage_delete" -> {
                val scriptId = payload.optLong("scriptId")
                val key = payload.optString("key", "")
                if (scriptId > 0L && key.isNotBlank()) {
                    scope.launch {
                        deleteValueAndBroadcast(
                            sourceSessionId = sessionId,
                            scriptId = scriptId,
                            key = key
                        )
                    }
                }
            }

            "storage_delete_many" -> {
                val scriptId = payload.optLong("scriptId")
                val keys = payload.optJSONArray("keys") ?: org.json.JSONArray()
                if (scriptId > 0L) {
                    scope.launch {
                        for (index in 0 until keys.length()) {
                            val key = keys.optString(index).trim()
                            if (key.isNotBlank()) {
                                deleteValueAndBroadcast(
                                    sourceSessionId = sessionId,
                                    scriptId = scriptId,
                                    key = key
                                )
                            }
                        }
                    }
                }
            }

            "url_change" -> {
                if (!isMainFrame) {
                    return
                }
                val href = payload.optString("href", "").trim()
                if (href.isNotBlank()) {
                    syncUrlChange(sessionId, href)
                }
            }

            "gm_open_in_tab" -> {
                val url = payload.optString("url", "").trim()
                if (url.isBlank()) {
                    if (requestId.isNotBlank()) {
                        postRpcError(replyProxy, requestId, "open_tab_url_is_empty")
                    }
                    return
                }
                mainHandler.post {
                    val openedSessionId = onOpenTab(url, payload.optBoolean("active", true))
                    if (!openedSessionId.isNullOrBlank()) {
                        openedTabOwners[openedSessionId] = sessionId
                        if (requestId.isNotBlank()) {
                            postRpcSuccess(
                                replyProxy,
                                requestId,
                                JSONObject().put("sessionId", openedSessionId)
                            )
                        }
                    } else if (requestId.isNotBlank()) {
                        postRpcError(replyProxy, requestId, "open_tab_failed")
                    }
                }
            }

            "gm_focus_tab" -> {
                val targetSessionId = payload.optString("sessionId", sessionId).trim()
                mainHandler.post {
                    if (targetSessionId.isBlank() || !sessionBindings.containsKey(targetSessionId)) {
                        if (requestId.isNotBlank()) {
                            postRpcError(replyProxy, requestId, "focus_tab_failed")
                        }
                        return@post
                    }
                    onActivateSession(targetSessionId)
                    if (requestId.isNotBlank()) {
                        postRpcSuccess(replyProxy, requestId, JSONObject().put("sessionId", targetSessionId))
                    }
                }
            }

            "gm_close_tab" -> {
                val targetSessionId = payload.optString("sessionId", sessionId).trim()
                mainHandler.post {
                    val closed =
                        if (targetSessionId.isNotBlank()) {
                            onCloseSession(targetSessionId)
                        } else {
                            false
                        }
                    if (requestId.isNotBlank()) {
                        if (closed) {
                            postRpcSuccess(replyProxy, requestId, JSONObject().put("sessionId", targetSessionId))
                        } else {
                            postRpcError(replyProxy, requestId, "close_tab_failed")
                        }
                    }
                }
            }

            "gm_get_tab" -> handleGetTab(sessionId, payload, replyProxy, requestId)
            "gm_save_tab" -> handleSaveTab(sessionId, payload, replyProxy, requestId)
            "gm_get_tabs" -> handleGetTabs(payload, replyProxy, requestId)
            "gm_cookie" -> handleCookie(payload, replyProxy, requestId)
            "gm_audio" -> handleAudio(sessionId, payload, replyProxy, requestId)
            "gm_web_request_register" -> handleRegisterWebRequest(sessionId, payload, replyProxy, requestId)
            "gm_web_request_unregister" -> handleUnregisterWebRequest(payload, replyProxy, requestId)
            "gm_set_clipboard" -> handleSetClipboard(payload, replyProxy, requestId)
            "gm_notification" -> handleNotification(payload, replyProxy, requestId)
            "gm_download" -> handleDownload(sessionId, payload, replyProxy, requestId)
            "gm_xmlhttp_request" -> handleXmlHttpRequest(sessionId, payload, replyProxy, requestId)
            "gm_abort_request" -> {
                val gmRequestId = payload.optString("requestId", "").trim()
                if (gmRequestId.isNotBlank()) {
                    val requestKey = "$sessionId:$gmRequestId"
                    abortedRequestKeys.add(requestKey)
                    activeCalls.remove(requestKey)?.cancel()
                }
            }
        }
    }

    private fun handleSetClipboard(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val text = payload.optString("text", "")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "clipboard_service_unavailable")
            }
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("userscript", text))
        if (requestId.isNotBlank()) {
            postRpcSuccess(replyProxy, requestId, JSONObject().put("text", text))
        }
    }

    private fun handleNotification(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val title = payload.optString("title", "").ifBlank { context.getString(R.string.web_session_userscript_notification_title) }
        val text = payload.optString("text", "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                onToast(context.getString(R.string.web_session_userscript_notification_denied))
                if (requestId.isNotBlank()) {
                    postRpcError(replyProxy, requestId, "notification_permission_denied")
                }
                return
            }
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager == null) {
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "notification_manager_unavailable")
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.web_session_userscript_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID + payload.optLong("scriptId").toInt(), notification)
        if (requestId.isNotBlank()) {
            postRpcSuccess(
                replyProxy,
                requestId,
                JSONObject()
                    .put("shown", true)
                    .put("title", title)
                    .put("text", text)
            )
        }
    }

    private fun handleDownload(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val url = payload.optString("url", "").trim()
        if (url.isBlank()) {
            onToast(context.getString(R.string.web_session_userscript_download_failed))
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "download_url_is_empty")
            }
            return
        }
        mainHandler.post {
            onDownload(sessionId, url, payload.optString("fileName", "").ifBlank { null })
            if (requestId.isNotBlank()) {
                postRpcSuccess(
                    replyProxy,
                    requestId,
                    JSONObject()
                        .put("started", true)
                        .put("url", url)
                        .put("fileName", payload.optString("fileName", ""))
                )
            }
        }
    }

    private suspend fun persistValueAndBroadcast(
        sourceSessionId: String,
        scriptId: Long,
        key: String,
        valueJson: String
    ) {
        val oldValueJson = repository.readValueJson(scriptId, key)
        repository.persistValue(scriptId, key, valueJson)
        broadcastStorageChange(
            sourceSessionId = sourceSessionId,
            change =
                UserscriptStorageChange(
                    scriptId = scriptId,
                    key = key,
                    oldValueJson = oldValueJson,
                    newValueJson = valueJson,
                    remote = true
                )
        )
    }

    private suspend fun deleteValueAndBroadcast(
        sourceSessionId: String,
        scriptId: Long,
        key: String
    ) {
        val oldValueJson = repository.readValueJson(scriptId, key)
        repository.deleteValue(scriptId, key)
        broadcastStorageChange(
            sourceSessionId = sourceSessionId,
            change =
                UserscriptStorageChange(
                    scriptId = scriptId,
                    key = key,
                    oldValueJson = oldValueJson,
                    newValueJson = null,
                    remote = true
                )
        )
    }

    private fun broadcastStorageChange(
        sourceSessionId: String,
        change: UserscriptStorageChange
    ) {
        val payload = storageNotifier.toPayload(change)
        sessionBindings.keys.forEach { targetSessionId ->
            if (targetSessionId != sourceSessionId) {
                dispatchHostEvent(targetSessionId, "storage_changed", payload)
            }
        }
    }

    private fun handleGetTab(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        postRpcSuccess(
            replyProxy,
            requestId,
            JSONObject().put("tabJson", tabStateStore.getTab(scriptId, sessionId))
        )
    }

    private fun handleSaveTab(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        val tabJson = payload.optString("tabJson", "{}")
        tabStateStore.saveTab(scriptId, sessionId, tabJson)
        postRpcSuccess(replyProxy, requestId, JSONObject().put("tabJson", tabJson))
    }

    private fun handleGetTabs(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        val jsonPayload =
            JSONObject()
        tabStateStore.getTabs(scriptId).forEach { (sessionId, tabJson) ->
            jsonPayload.put(sessionId, tabJson)
        }
        postRpcSuccess(replyProxy, requestId, JSONObject().put("tabsJson", jsonPayload.toString()))
    }

    private fun handleCookie(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val details = payload.optJSONObject("details") ?: JSONObject()
        val pageUrl = payload.optString("pageUrl", "")
        runCatching {
            when (payload.optString("action", "").trim()) {
                "list" -> {
                    val cookiesJson = org.json.JSONArray()
                    cookieService.list(details, pageUrl).forEach { cookie ->
                        cookiesJson.put(cookie.toJson())
                    }
                    postRpcSuccess(replyProxy, requestId, JSONObject().put("cookiesJson", cookiesJson.toString()))
                }
                "set" -> {
                    val cookie = cookieService.set(details, pageUrl)
                    postRpcSuccess(replyProxy, requestId, JSONObject().put("cookieJson", cookie.toJson().toString()))
                }
                "delete" -> {
                    cookieService.delete(details, pageUrl)
                    postRpcSuccess(replyProxy, requestId, JSONObject().put("deleted", true))
                }
                else -> postRpcError(replyProxy, requestId, "unsupported_cookie_action")
            }
        }.onFailure { error ->
            postRpcError(replyProxy, requestId, error.message ?: "gm_cookie_failed")
        }
    }

    private fun handleAudio(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val binding = sessionBindings[sessionId]
        if (binding == null) {
            postRpcError(replyProxy, requestId, "session_not_found")
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            postRpcError(replyProxy, requestId, "mute_audio_not_supported")
            return
        }
        val action = payload.optString("action", "").trim()
        mainHandler.post {
            runCatching {
                when (action) {
                    "get_state" -> {
                        val muted = WebViewCompat.isAudioMuted(binding.webView)
                        audioMuteStates[sessionId] = muted
                        postRpcSuccess(
                            replyProxy,
                            requestId,
                            JSONObject().put("stateJson", JSONObject().put("muted", muted).toString())
                        )
                    }
                    "set_mute" -> {
                        val muted = payload.optBoolean("muted", false)
                        WebViewCompat.setAudioMuted(binding.webView, muted)
                        audioMuteStates[sessionId] = muted
                        dispatchHostEvent(
                            sessionId,
                            "audio_state_changed",
                            JSONObject().put("muted", muted)
                        )
                        postRpcSuccess(
                            replyProxy,
                            requestId,
                            JSONObject().put("stateJson", JSONObject().put("muted", muted).toString())
                        )
                    }
                    else -> postRpcError(replyProxy, requestId, "unsupported_audio_action")
                }
            }.onFailure { error ->
                postRpcError(replyProxy, requestId, error.message ?: "gm_audio_failed")
            }
        }
    }

    private fun handleRegisterWebRequest(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        val rulesJson = payload.optString("rulesJson", "[]")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        runCatching {
            val registrationId =
                webRequestEngine.register(
                    scriptId = scriptId,
                    sessionId = sessionId,
                    rulesJson = rulesJson,
                    source = payload.optString("source", "runtime").ifBlank { "runtime" }
                )
            postRpcSuccess(replyProxy, requestId, JSONObject().put("registrationId", registrationId))
        }.onFailure { error ->
            postRpcError(replyProxy, requestId, error.message ?: "gm_web_request_register_failed")
        }
    }

    private fun handleUnregisterWebRequest(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val registrationId = payload.optString("registrationId", "").trim()
        if (registrationId.isBlank()) {
            postRpcError(replyProxy, requestId, "invalid_registration_id")
            return
        }
        val removed = webRequestEngine.unregister(registrationId)
        if (removed) {
            postRpcSuccess(replyProxy, requestId, JSONObject().put("registrationId", registrationId))
        } else {
            postRpcError(replyProxy, requestId, "registration_not_found")
        }
    }

    private fun handleXmlHttpRequest(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val gmRequestId = payload.optString("requestId", "").trim()
        val scriptId = payload.optLong("scriptId")
        val targetUrl = payload.optString("url", "").trim()
        val pageUrl = payload.optString("pageUrl", "").trim()
        if (gmRequestId.isBlank() || scriptId <= 0L || targetUrl.isBlank()) {
            postRpcError(replyProxy, requestId, "invalid_xhr_request")
            return
        }
        scope.launch {
            val installed = repository.getInstalledScript(scriptId)
            if (installed == null) {
                postRpcError(replyProxy, requestId, "userscript_not_found")
                return@launch
            }
            val metadata = installed.toMetadata()
            if (!UserscriptMatcher.isConnectAllowed(metadata, pageUrl, targetUrl)) {
                repository.log(scriptId, "error", pageUrl, "GM_xmlhttpRequest blocked by @connect: $targetUrl")
                postRpcError(replyProxy, requestId, "connect_not_allowed")
                return@launch
            }
            runCatching {
                startXmlHttpRequest(sessionId, gmRequestId, scriptId, pageUrl, payload, replyProxy)
                postRpcSuccess(replyProxy, requestId, JSONObject().put("accepted", true))
            }.onFailure { error ->
                repository.log(scriptId, "error", pageUrl, error.message ?: "GM_xmlhttpRequest failed")
                postRpcError(replyProxy, requestId, error.message ?: "xhr_start_failed")
            }
        }
    }

    private fun startXmlHttpRequest(
        sessionId: String,
        gmRequestId: String,
        scriptId: Long,
        pageUrl: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy
    ) {
        val method = payload.optString("method", "GET").uppercase()
        val url = payload.optString("url", "")
        val responseType = payload.optString("responseType", "text")
        val timeoutMs = payload.optLong("timeoutMs", 0L).coerceAtLeast(0L)
        val anonymous = payload.optBoolean("anonymous", false)
        val bodyData = payload.opt("data")
        val requestType = payload.optString("requestType", "xhr").ifBlank { "xhr" }
        val headers = jsonObjectToMap(payload.optJSONObject("headers")).toMutableMap()
        val webRequestResolution = webRequestEngine.resolve(sessionId, url, requestType)
        webRequestResolution.matches.forEach { match ->
            dispatchHostEvent(
                sessionId,
                "web_request_event",
                JSONObject()
                    .put("registrationId", match.registrationId)
                    .put("scriptId", match.scriptId)
                    .put("url", url)
                    .put("type", requestType)
                    .put("source", match.source)
            )
        }
        val action = webRequestResolution.mergedAction
        if (action.cancel) {
            throw IllegalStateException("GM_webRequest canceled request")
        }
        val targetUrl = action.redirectUrl ?: url
        headers.putAll(action.requestHeaders)

        val requestBuilder = Request.Builder().url(targetUrl)
        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        if (!anonymous) {
            CookieManager.getInstance().getCookie(targetUrl)?.takeIf { it.isNotBlank() }?.let { cookie ->
                if (!headers.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                    requestBuilder.header("Cookie", cookie)
                }
            }
        }

        val requestBody =
            if (method == "GET" || method == "HEAD") {
                null
            } else {
                val bodyText =
                    when (bodyData) {
                        null, JSONObject.NULL -> ""
                        is JSONObject, is org.json.JSONArray -> bodyData.toString()
                        else -> bodyData.toString()
                    }
                val mediaType = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.toMediaTypeOrNull()
                bodyText.toRequestBody(mediaType)
            }
        requestBuilder.method(method, requestBody)

        val call = requestClient.newCall(requestBuilder.build())
        if (timeoutMs > 0L) {
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        }
        val requestKey = "$sessionId:$gmRequestId"
        activeCalls[requestKey] = call
        postXhrEvent(
            replyProxy = replyProxy,
            requestId = gmRequestId,
            eventType = "readystatechange",
            terminal = false,
            payload =
                JSONObject()
                    .put("readyState", 1)
                    .put("status", 0)
                    .put("statusText", "")
                    .put("finalUrl", targetUrl)
        )
        postXhrEvent(
            replyProxy = replyProxy,
            requestId = gmRequestId,
            eventType = "loadstart",
            terminal = false,
            payload =
                JSONObject()
                    .put("readyState", 1)
                    .put("status", 0)
                    .put("statusText", "")
                    .put("finalUrl", targetUrl)
        )

        scope.launch {
            try {
                val response = call.execute()
                if (!anonymous) {
                    response.headers("Set-Cookie").forEach { cookie ->
                        CookieManager.getInstance().setCookie(targetUrl, cookie)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().flush()
                    }
                }
                val body = response.body
                val total = body?.contentLength()?.takeIf { it >= 0L } ?: 0L
                val output = ByteArrayOutputStream()
                if (body != null) {
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var loaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) {
                                break
                            }
                            output.write(buffer, 0, count)
                            loaded += count
                            postXhrEvent(
                                replyProxy = replyProxy,
                                requestId = gmRequestId,
                                eventType = "readystatechange",
                                terminal = false,
                                payload =
                                    JSONObject()
                                        .put("loaded", loaded)
                                        .put("total", total)
                                        .put("readyState", 3)
                            )
                            postXhrEvent(
                                replyProxy = replyProxy,
                                requestId = gmRequestId,
                                eventType = "progress",
                                terminal = false,
                                payload =
                                    JSONObject()
                                        .put("loaded", loaded)
                                        .put("total", total)
                                        .put("readyState", 3)
                            )
                        }
                    }
                }
                val bytes = output.toByteArray()
                val headersJson = JSONObject()
                response.headers.toMultimap().forEach { (key, values) ->
                    headersJson.put(key, values.joinToString(", "))
                }
                val payloadJson =
                    JSONObject()
                        .put("status", response.code)
                        .put("statusText", response.message)
                        .put("readyState", 4)
                        .put("headers", headersJson)
                        .put("finalUrl", response.request.url.toString())
                        .put("loaded", bytes.size.toLong())
                        .put("total", total)
                when (responseType.lowercase()) {
                    "arraybuffer", "blob" -> {
                        val resolvedBytes =
                            action.responseBody?.toByteArray(Charsets.UTF_8)
                                ?: bytes
                        payloadJson.put(
                            "response",
                            Base64.encodeToString(resolvedBytes, Base64.NO_WRAP)
                        )
                        payloadJson.put("responseEncoding", "base64")
                        payloadJson.put("responseType", responseType.lowercase())
                    }
                    else -> {
                        val resolvedText = action.responseBody ?: bytes.toString(Charsets.UTF_8)
                        payloadJson.put("responseText", resolvedText)
                        payloadJson.put("response", resolvedText)
                    }
                }
                action.responseHeaders.forEach { (key, value) ->
                    headersJson.put(key, value)
                }
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "readystatechange",
                    terminal = false,
                    payload = JSONObject(payloadJson.toString())
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "load",
                    terminal = false,
                    payload = payloadJson
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "loadend",
                    terminal = true,
                    payload = payloadJson
                )
                response.close()
            } catch (error: Throwable) {
                if (abortedRequestKeys.remove(requestKey)) {
                    return@launch
                }
                val eventType = if (error is java.io.InterruptedIOException) "timeout" else "error"
                val errorPayload =
                    JSONObject()
                        .put("status", 0)
                        .put("statusText", error.message ?: eventType)
                        .put("readyState", 4)
                        .put("finalUrl", payload.optString("url", ""))
                        .put("responseText", "")
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "readystatechange",
                    terminal = false,
                    payload = JSONObject(errorPayload.toString())
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = eventType,
                    terminal = false,
                    payload = errorPayload
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "loadend",
                    terminal = true,
                    payload = errorPayload
                )
                repository.log(scriptId, "error", pageUrl, error.message ?: "GM_xmlhttpRequest failed")
            } finally {
                activeCalls.remove(requestKey)
                abortedRequestKeys.remove(requestKey)
            }
        }
    }

    private fun jsonObjectToMap(raw: JSONObject?): Map<String, String> {
        if (raw == null) {
            return emptyMap()
        }
        return buildMap {
            raw.keys().forEach { key ->
                put(key, raw.optString(key, ""))
            }
        }
    }

    private fun rebuildAllSessionBaselines() {
        sessionPageStates.keys.forEach { sessionId ->
            rebuildSessionBaseline(sessionId)
        }
    }

    private fun rebuildSessionBaseline(sessionId: String) {
        val pageState = sessionPageStates[sessionId] ?: return
        val scripts = uiStore.state.value.installedScripts
        val currentPageUrl = pageState.pageUrl
        val existing = LinkedHashMap(pageState.scriptStatuses)
        pageState.scriptStatuses.clear()
        scripts.forEach { script ->
            pageState.scriptStatuses[script.id] = baselineStatus(script, currentPageUrl)
        }
        existing.forEach { (scriptId, status) ->
            val baseline = pageState.scriptStatuses[scriptId] ?: return@forEach
            if (baseline.state == UserscriptPageRuntimeState.QUEUED ||
                baseline.state == UserscriptPageRuntimeState.RUNNING ||
                baseline.state == UserscriptPageRuntimeState.SUCCESS ||
                baseline.state == UserscriptPageRuntimeState.ERROR
            ) {
                pageState.scriptStatuses[scriptId] = status
            }
        }
        publishVisibleStatuses()
    }

    private fun baselineStatus(
        script: UserscriptListItem,
        pageUrl: String
    ): UserscriptPageRuntimeStatus {
        return when {
            !script.enabled ->
                UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.DISABLED)
            script.blockedReasons.isNotEmpty() ->
                UserscriptPageRuntimeStatus(
                    UserscriptPageRuntimeState.UNSUPPORTED,
                    detail = script.blockedReasons.joinToString()
                )
            pageUrl.isBlank() || pageUrl == "about:blank" ->
                UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.NOT_MATCHED)
            UserscriptMatcher.matches(
                metadata = script.toMetadata(),
                pageUrl = pageUrl,
                isTopFrame = true
            ) ->
                UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.QUEUED)
            else ->
                UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.NOT_MATCHED)
        }
    }

    private fun upsertRuntimeStatus(
        sessionId: String,
        scriptId: Long,
        rawState: String,
        detail: String?
    ) {
        val mappedState =
            when (rawState.trim().lowercase()) {
                "running" -> UserscriptPageRuntimeState.RUNNING
                "success" -> UserscriptPageRuntimeState.SUCCESS
                "error" -> UserscriptPageRuntimeState.ERROR
                else -> UserscriptPageRuntimeState.QUEUED
            }
        val pageState = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
        pageState.scriptStatuses[scriptId] =
            UserscriptPageRuntimeStatus(
                state = mappedState,
                detail = detail
            )
        publishVisibleStatuses()
    }

    private fun publishVisibleStatuses() {
        val visibleStatuses =
            visibleSessionId
                ?.let { sessionPageStates[it]?.scriptStatuses?.toMap() }
                .orEmpty()
        uiStore.updateCurrentPageStatuses(visibleStatuses)
    }

    private fun dispatchHostEvent(
        sessionId: String,
        eventType: String,
        payload: JSONObject
    ) {
        val binding = sessionBindings[sessionId] ?: return
        val rawMessage =
            JSONObject()
                .put("type", eventType)
                .put("payload", payload)
                .toString()
        val escapedMessage = JSONObject.quote(rawMessage)
        mainHandler.post {
            runCatching {
                binding.webView.evaluateJavascript(
                    """
                    (function() {
                        const runtime = window.__operitUserscriptRuntime;
                        if (!runtime || typeof runtime.dispatchHostEvent !== "function") {
                            return;
                        }
                        runtime.dispatchHostEvent(JSON.parse($escapedMessage));
                    })();
                    """.trimIndent(),
                    null
                )
            }.onFailure { error ->
                AppLogger.w(TAG, "Failed to dispatch userscript host event: ${error.message}")
            }
        }
    }

    private fun resolveWebRequestType(request: WebResourceRequest): String {
        if (request.isForMainFrame) {
            return "main_frame"
        }
        val lowerUrl = request.url?.toString().orEmpty().lowercase()
        val accept = request.requestHeaders?.entries?.firstOrNull {
            it.key.equals("Accept", ignoreCase = true)
        }?.value?.lowercase().orEmpty()
        return when {
            accept.contains("text/css") || lowerUrl.endsWith(".css") -> "stylesheet"
            accept.contains("javascript") || lowerUrl.endsWith(".js") -> "script"
            accept.contains("image/") || lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".svg") -> "image"
            accept.contains("font/") || lowerUrl.endsWith(".woff") || lowerUrl.endsWith(".woff2") || lowerUrl.endsWith(".ttf") -> "font"
            accept.contains("video/") || accept.contains("audio/") -> "media"
            accept.contains("application/json") || accept.contains("text/plain") -> "fetch"
            else -> "other"
        }
    }

    private fun UserscriptListItem.toMetadata(): ParsedUserscriptMetadata =
        ParsedUserscriptMetadata(
            name = name,
            namespace = namespace,
            version = version,
            description = description,
            homepage = homepage,
            website = website,
            supportUrl = supportUrl,
            downloadUrl = downloadUrl,
            updateUrl = updateUrl,
            grants = grants,
            matches = matches,
            includes = includes,
            excludes = excludes,
            excludeMatches = excludeMatches,
            connects = connects,
            requires = requires,
            resources = resources,
            icons = icons,
            tags = tags,
            sandbox = sandbox,
            runIn = runIn,
            unwrap = unwrap,
            webRequestRules = webRequestRules
        )

    private fun postRpcSuccess(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        payload: JSONObject
    ) {
        postBridgeMessage(
            replyProxy,
            JSONObject()
                .put("type", "rpc_response")
                .put("requestId", requestId)
                .put("payload", payload)
        )
    }

    private fun postRpcError(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        error: String
    ) {
        postBridgeMessage(
            replyProxy,
            JSONObject()
                .put("type", "rpc_response")
                .put("requestId", requestId)
                .put("error", error)
        )
    }

    private fun postXhrEvent(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        eventType: String,
        terminal: Boolean,
        payload: JSONObject
    ) {
        postBridgeMessage(
            replyProxy,
            JSONObject()
                .put("type", "xhr_event")
                .put("requestId", requestId)
                .put("eventType", eventType)
                .put("terminal", terminal)
                .put("payload", payload)
        )
    }

    private fun postBridgeMessage(
        replyProxy: JavaScriptReplyProxy,
        payload: JSONObject
    ) {
        mainHandler.post {
            runCatching { replyProxy.postMessage(payload.toString()) }
                .onFailure { AppLogger.w(TAG, "Failed to post userscript bridge message: ${it.message}") }
        }
    }
}

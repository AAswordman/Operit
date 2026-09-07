package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.*
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallSourceType
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime.WebSessionUserscriptManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage.UserscriptRepository
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class StandardBrowserSessionTools(internal val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "BrowserSessionTools"
        internal const val DEFAULT_TIMEOUT_MS = 10_000L
        internal const val MAX_EVENT_LOG_ENTRIES = 500
        internal const val DESKTOP_CHROME_MAJOR_VERSION = "124"
        internal const val DESKTOP_CHROME_FULL_VERSION = "124.0.0.0"
        internal const val DEFAULT_DESKTOP_VIEWPORT_WIDTH = 1280
        internal const val DEFAULT_DESKTOP_VIEWPORT_HEIGHT = 720
        internal const val MAX_VIEWPORT_DIMENSION_CSS_PX = 4096
        internal const val MAX_VIEWPORT_PIXEL_COUNT = 8_388_608L
        internal const val MAX_VIEWPORT_LAYOUT_PIXEL_COUNT = 33_554_432L
        internal const val MAX_SCREENSHOT_DIMENSION_PX = 32_768
        internal const val MAX_SCREENSHOT_PIXEL_COUNT = 16_777_216L
        internal const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$DESKTOP_CHROME_FULL_VERSION Safari/537.36"
        internal const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        internal val mainHandler = Handler(Looper.getMainLooper())

        internal val sessions = ConcurrentHashMap<String, WebSession>()
        internal val sessionOrder = mutableListOf<String>()
        internal val pendingAsyncJsCalls = ConcurrentHashMap<String, PendingAsyncJsCall>()
        private val snapshotGenerationSeed = AtomicLong(0L)

        internal val sessionOrderLock = Any()
        internal val overlayLock = Any()
        internal val sessionConfigLock = Any()

        @Volatile internal var browserHost: WebSessionBrowserHost? = null
        @Volatile internal var activeSessionId: String? = null
        @Volatile internal var desktopModeEnabled: Boolean = true
        @Volatile internal var desktopModeInitialized: Boolean = false
        @Volatile internal var pendingExternalOpenRequest: PendingExternalOpenRequest? = null
    }

    internal val historyStore by lazy { WebSessionHistoryStore.getInstance(context.applicationContext) }
    private val userscriptRepository by lazy { UserscriptRepository.getInstance(context.applicationContext) }
    internal val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val userscriptManager by lazy {
        WebSessionUserscriptManager(
            context = context.applicationContext,
            onOpenUserscriptUi = {
                mainHandler.post {
                    openUserscriptSheetOnMain()
                }
            },
            onOpenTab = { url, active ->
                runOnMainSync<String?> {
                    openUserscriptTabOnMain(context.applicationContext, url, active)
                }
            },
            onActivateSession = { sessionId ->
                runOnMainSync<Unit> {
                    activateSessionOnMain(sessionId)
                }
            },
            onCloseSession = { sessionId ->
                closeSession(sessionId)
            },
            onDownload = { sessionId, url, fileName ->
                runOnMainSync<Unit> {
                    handleUserscriptDownloadOnMain(sessionId, url, fileName)
                }
            },
            onMenuCommandsChanged = { sessionId ->
                mainHandler.post {
                    refreshSessionUiOnMain(sessionId)
                }
            },
            onToast = { message -> showToast(message) }
        )
    }

    init {
        ensureDesktopModeInitialized()
        initializeBrowserDownloadSupport()
    }

    internal data class WebSession(
        val id: String,
        val webView: WebView,
        val sessionName: String?,
        val customUserAgent: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        @Volatile var currentUrl: String = "about:blank"
        @Volatile var pageTitle: String = ""
        @Volatile var pageLoaded: Boolean = false
        @Volatile var isLoading: Boolean = false
        @Volatile var canGoBack: Boolean = false
        @Volatile var canGoForward: Boolean = false
        @Volatile var hasSslError: Boolean = false
        @Volatile var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
        @Volatile var lastFileChooserRequestAt: Long = 0L
        @Volatile var lastDownloadEvent: WebDownloadEvent? = null
        @Volatile var lastDownloadEventAt: Long = 0L
        @Volatile var pendingDialog: PendingDialog? = null
        @Volatile var viewportWidthPx: Int? = null
        @Volatile var viewportHeightPx: Int? = null
        @Volatile var hasExplicitViewportSize: Boolean = false
        @Volatile var lastSnapshot: BrowserSnapshot? = null
        val stateSignal: Object = Object()
        @Volatile var stateVersion: Long = 0L
        val consoleEntries: MutableList<BrowserConsoleEntry> = mutableListOf()
        val networkEntries: MutableList<BrowserNetworkRequestEntry> = mutableListOf()
    }

    internal data class BrowserActionSettlementPolicy(
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        val waitForDocumentReady: Boolean = false,
        val waitForNavigationChange: Boolean = false,
        val waitForText: String? = null,
        val waitForTextGone: String? = null,
        val waitForTimeSeconds: Double? = null,
        val allowActivePageSwitch: Boolean = true,
        val captureSnapshot: Boolean = true
    )

    internal data class BrowserActionMarkers(
        val initialSessionId: String,
        val initialUrl: String,
        val consoleTimestamp: Long,
        val downloadTimestamp: Long,
        val snapshotGeneration: Long,
        val startedAt: Long
    )

    internal data class BrowserActionSettlement(
        val registry: BrowserPageRegistry,
        val session: WebSession,
        val snapshot: BrowserSnapshot?,
        val consoleMarker: Long,
        val downloadMarker: Long,
        val timedOut: Boolean = false
    )

    override fun invoke(tool: AITool): ToolResult {
        return try {
            when (tool.name) {
                "browser_click" -> browserClick(tool)
                "browser_close" -> browserClose(tool)
                "browser_close_all" -> browserCloseAll(tool)
                "browser_console_messages" -> browserConsoleMessages(tool)
                "browser_drag" -> browserDrag(tool)
                "browser_evaluate" -> browserEvaluate(tool)
                "browser_file_upload" -> browserFileUpload(tool)
                "browser_fill_form" -> browserFillForm(tool)
                "browser_handle_dialog" -> browserHandleDialog(tool)
                "browser_hover" -> browserHover(tool)
                "browser_navigate" -> browserNavigate(tool)
                "browser_navigate_back" -> browserNavigateBack(tool)
                "browser_network_requests" -> browserNetworkRequests(tool)
                "browser_press_key" -> browserPressKey(tool)
                "browser_resize" -> browserResize(tool)
                "browser_run_code" -> browserRunCode(tool)
                "browser_select_option" -> browserSelectOption(tool)
                "browser_wait_for" -> browserWaitFor(tool)
                "browser_snapshot" -> browserSnapshot(tool)
                "browser_tabs" -> browserTabs(tool)
                "browser_take_screenshot" -> browserTakeScreenshot(tool)
                "browser_type" -> browserType(tool)
                else -> error(tool.name, "Unsupported browser session tool: ${tool.name}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Tool execution failed: ${tool.name}", e)
            error(tool.name, e.message ?: "Unknown error")
        }
    }

    private fun browserNavigate(tool: AITool): ToolResult {
        val targetUrl = param(tool, "url")?.trim()
        if (targetUrl.isNullOrBlank()) {
            return error(tool.name, "url is required")
        }
        ensureOverlayPermission(tool.name)?.let { return it }
        val headers = parseHeaders(param(tool, "headers"))
        val session =
            runOnMainSync {
                getSession(null) ?: createSessionTabOnMain(context.applicationContext, "about:blank")
            }
        val markers = captureActionMarkers(session)

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
            navigateSessionOnMain(session, targetUrl, headers)
        }
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy =
                    BrowserActionSettlementPolicy(
                        timeoutMs = DEFAULT_TIMEOUT_MS.coerceAtLeast(6_000L),
                        waitForDocumentReady = true,
                        waitForNavigationChange = true,
                        captureSnapshot = false
                    )
            )

        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = "await page.goto(${quoteJsCode(targetUrl)});",
                result = "Navigated to ${settlement.session.currentUrl.ifBlank { targetUrl }}"
            )
        )
    }

    private fun browserClick(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val ref = param(tool, "ref")?.trim()?.takeIf { it.isNotBlank() }
        val selector = param(tool, "selector")?.trim()?.takeIf { it.isNotBlank() }
        if (ref == null && selector == null) {
            return error(tool.name, "ref or selector is required")
        }

        val buttonRaw = param(tool, "button")?.trim()
        val button =
            when {
                buttonRaw.isNullOrBlank() -> "left"
                buttonRaw == "left" || buttonRaw == "right" || buttonRaw == "middle" -> buttonRaw
                else -> return error(tool.name, "button must be one of: left, right, middle")
            }

        val doubleClick = boolParam(tool, "doubleClick", false)

        val (modifiers, invalidModifiers) = parseClickModifiers(param(tool, "modifiers"))
        if (invalidModifiers.isNotEmpty()) {
            return error(
                tool.name,
                "Invalid modifiers: ${invalidModifiers.joinToString(", ")}. Allowed: Alt, Control, ControlOrMeta, Meta, Shift"
            )
        }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        if (ref != null && requireSnapshotNode(session, ref) == null) {
            return pageError(
                tool.name,
                session,
                "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
            )
        }
        val markers = captureActionMarkers(session)
        val code =
            when {
                ref != null -> buildClickCode(session, ref, button, doubleClick, modifiers)
                else -> buildClickCodeForSelector(selector!!, button, doubleClick, modifiers)
            }
        val useNativeTap =
            ref != null &&
                button == "left" &&
                !doubleClick &&
                modifiers.isEmpty()
        val jsResult =
            when {
                useNativeTap && dispatchNativeTapByRef(session.webView, ref!!) == true ->
                    JSONObject()
                        .put("ok", true)
                        .put("ref", ref)
                        .put("button", button)
                        .put("doubleClick", false)
                        .put("activationMethod", "native_webview_tap")
                ref != null ->
                    dispatchClickByRef(
                        webView = session.webView,
                        ref = ref,
                        button = button,
                        modifiers = modifiers,
                        doubleClick = doubleClick
                    )
                else ->
                    dispatchClickBySelector(
                        webView = session.webView,
                        selector = selector!!,
                        button = button,
                        modifiers = modifiers,
                        doubleClick = doubleClick
                    )
            }
        if (jsResult?.optBoolean("ok", false) != true) {
            if (jsResult?.optString("error") == "ref_not_found") {
                return pageError(
                    tool.name,
                    session,
                    "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
                )
            }
            if (jsResult?.optString("error") == "selector_not_found") {
                return pageError(
                    tool.name,
                    session,
                    "Selector ${selector ?: ""} did not match any elements."
                )
            }
            return error(tool.name, "Click failed: ${jsResult?.optString("error") ?: "unknown"}")
        }
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy =
                    BrowserActionSettlementPolicy(
                        waitForDocumentReady = true,
                        waitForTimeSeconds = 0.5
                    )
            )
        val downloadEvent = latestBrowserDownloadEventAfter(settlement.downloadMarker)
        if (downloadEvent?.status == "failed") {
            return pageError(
                tool.name,
                settlement.session,
                "Click triggered a download, but it failed: ${downloadEvent.error ?: downloadEvent.fileName}"
            )
        }

        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = code,
                result =
                    when {
                        ref != null -> "Clicked ref=$ref with button=$button${if (doubleClick) " (double)" else ""}"
                        else -> "Clicked selector=${selector ?: ""} with button=$button${if (doubleClick) " (double)" else ""}"
                    }
            )
        )
    }

    private fun dispatchNativeTapByRef(webView: WebView, ref: String): Boolean {
        val rect = resolveElementRect(webView, ref) ?: return false
        val pageScale = webView.scale.takeIf { it > 0f } ?: 1f
        val x =
            (((rect.left + rect.right) / 2f) * pageScale)
                .coerceIn(1f, (webView.width - 1).coerceAtLeast(1).toFloat())
        val y =
            (((rect.top + rect.bottom) / 2f) * pageScale)
                .coerceIn(1f, (webView.height - 1).coerceAtLeast(1).toFloat())
        return runOnMainSync {
            try {
                webView.requestFocus()
                val downTime = SystemClock.uptimeMillis()
                val down =
                    MotionEvent.obtain(
                        downTime,
                        downTime,
                        MotionEvent.ACTION_DOWN,
                        x,
                        y,
                        0
                    )
                val up =
                    MotionEvent.obtain(
                        downTime,
                        downTime + 16L,
                        MotionEvent.ACTION_UP,
                        x,
                        y,
                        0
                    )
                try {
                    val downHandled = webView.dispatchTouchEvent(down)
                    val upHandled = webView.dispatchTouchEvent(up)
                    downHandled || upHandled
                } finally {
                    down.recycle()
                    up.recycle()
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun buildClickCodeForSelector(
        selector: String,
        button: String,
        doubleClick: Boolean,
        modifiers: Set<String>
    ): String {
        val method = if (doubleClick) "dblclick" else "click"
        val options = mutableListOf<String>()
        if (button != "left") {
            options += "button: ${quoteJsCode(button)}"
        }
        if (modifiers.isNotEmpty()) {
            options += "modifiers: ${renderJsArrayCode(modifiers.toList())}"
        }
        val locator = "page.locator(${quoteJsCode(selector)})"
        return if (options.isEmpty()) {
            "await $locator.$method();"
        } else {
            "await $locator.$method({ ${options.joinToString(", ")} });"
        }
    }

    private fun dispatchClickByRef(
        webView: WebView,
        ref: String,
        button: String,
        modifiers: Set<String>,
        doubleClick: Boolean
    ): JSONObject? {
        val buttonValue =
            when (button) {
                "middle" -> 1
                "right" -> 2
                else -> 0
            }
        val buttonsValue =
            when (button) {
                "middle" -> 4
                "right" -> 2
                else -> 1
            }

        val altKey = modifiers.contains("Alt")
        val controlKey = modifiers.contains("Control") || modifiers.contains("ControlOrMeta")
        val metaKey = modifiers.contains("Meta") || modifiers.contains("ControlOrMeta")
        val shiftKey = modifiers.contains("Shift")

        val script =
            """
            (function() {
                try {
                    const refValue = ${quoteJs(ref)};
                    ${browserRefResolverScript()}
                    const resolved = __operitResolveRef(refValue);
                    if (!resolved || !resolved.element) {
                        return JSON.stringify({ ok: false, error: "ref_not_found", ref: refValue });
                    }
                    const target = resolved.element;
                    const targetWindow = resolved.window || window;
                    const anchor = target.closest('a[href]');
                    try { target.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
                    const rect = target.getBoundingClientRect();
                    const x = rect.left + rect.width / 2;
                    const y = rect.top + rect.height / 2;

                    try { target.focus({ preventScroll: true }); } catch (_) {}

                    const buttonValue = ${buttonValue};
                    const buttonsValue = ${buttonsValue};
                    const altKey = ${if (altKey) "true" else "false"};
                    const ctrlKey = ${if (controlKey) "true" else "false"};
                    const metaKey = ${if (metaKey) "true" else "false"};
                    const shiftKey = ${if (shiftKey) "true" else "false"};

                    function emit(type, detail) {
                        try {
                            const MouseEventCtor = targetWindow.MouseEvent || MouseEvent;
                            target.dispatchEvent(new MouseEventCtor(type, {
                                bubbles: true,
                                cancelable: true,
                                composed: true,
                                view: targetWindow,
                                detail: detail,
                                clientX: x,
                                clientY: y,
                                screenX: x,
                                screenY: y,
                                button: buttonValue,
                                buttons: buttonsValue,
                                altKey,
                                ctrlKey,
                                metaKey,
                                shiftKey
                            }));
                        } catch (_) {}
                    }

                    function clickOnce(detail) {
                        emit("mousedown", detail);
                        emit("mouseup", detail);
                        emit("click", detail);
                    }

                    let activationMethod = "mouse_event";
                    let activationTag = String(target.tagName || "").toLowerCase();
                    const nativeAnchorClickEligible = !${if (doubleClick) "true" else "false"} &&
                        buttonValue === 0 && !altKey && !ctrlKey && !metaKey && !shiftKey &&
                        !!anchor && typeof anchor.click === "function";

                    setTimeout(() => {
                        try {
                            if (nativeAnchorClickEligible) {
                                activationMethod = "native_anchor_click";
                                activationTag = String(anchor.tagName || "").toLowerCase();
                                anchor.click();
                            } else if (${if (doubleClick) "true" else "false"}) {
                                clickOnce(1);
                                clickOnce(2);
                                emit("dblclick", 2);
                            } else {
                                clickOnce(1);
                            }
                        } catch (_) {}
                    }, 0);

                    return JSON.stringify({
                        ok: true,
                        ref: refValue,
                        button: ${quoteJs(button)},
                        doubleClick: ${if (doubleClick) "true" else "false"},
                        tag: String(target.tagName || "").toLowerCase(),
                        activationMethod,
                        activationTag,
                        href: anchor ? String(anchor.href || "") : ""
                    });
                } catch (e) {
                    return JSON.stringify({ ok: false, error: String(e) });
                }
            })();
            """.trimIndent()

        return runJsonScript(webView, script, "click_dispatch_error")
    }

    private fun dispatchHoverByRef(webView: WebView, ref: String): JSONObject? {
        val script =
            """
            (function() {
                try {
                    const refValue = ${quoteJs(ref)};
                    ${browserRefResolverScript()}
                    const resolved = __operitResolveRef(refValue);
                    if (!resolved || !resolved.element) {
                        return JSON.stringify({ ok: false, error: "ref_not_found" });
                    }
                    const target = resolved.element;
                    const targetWindow = resolved.window || window;
                    try { target.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
                    const rect = target.getBoundingClientRect();
                    const x = rect.left + rect.width / 2;
                    const y = rect.top + rect.height / 2;
                    ["pointerover", "mouseover", "mouseenter", "mousemove"].forEach((type) => {
                        try {
                            const MouseEventCtor = targetWindow.MouseEvent || MouseEvent;
                            target.dispatchEvent(new MouseEventCtor(type, {
                                bubbles: true,
                                cancelable: true,
                                composed: true,
                                view: targetWindow,
                                clientX: x,
                                clientY: y
                            }));
                        } catch (_) {}
                    });
                    return JSON.stringify({ ok: true, ref: refValue });
                } catch (e) {
                    return JSON.stringify({ ok: false, error: String(e) });
                }
            })();
            """.trimIndent()
        return runJsonScript(webView, script, "hover_dispatch_error")
    }

    private fun dispatchDragByRef(webView: WebView, startRef: String, endRef: String): JSONObject? {
        val script =
            """
            (function() {
                try {
                    ${browserRefResolverScript()}
                    const startResolved = __operitResolveRef(${quoteJs(startRef)});
                    const endResolved = __operitResolveRef(${quoteJs(endRef)});
                    const start = startResolved && startResolved.element;
                    const end = endResolved && endResolved.element;
                    if (!start || !end) {
                        return JSON.stringify({ ok: false, error: !start ? "start_ref_not_found" : "end_ref_not_found" });
                    }
                    const startWindow = (startResolved && startResolved.window) || window;
                    const endWindow = (endResolved && endResolved.window) || window;
                    try { start.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
                    try { end.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
                    const DataTransferCtor = startWindow.DataTransfer || endWindow.DataTransfer || (typeof DataTransfer === "function" ? DataTransfer : null);
                    const DragEventCtor = startWindow.DragEvent || endWindow.DragEvent || (typeof DragEvent === "function" ? DragEvent : null);
                    const EventCtor = startWindow.Event || endWindow.Event || Event;
                    const dataTransfer = typeof DataTransferCtor === "function" ? new DataTransferCtor() : null;
                    const dispatchDrag = (target, type) => {
                        try {
                            const event = DragEventCtor ? new DragEventCtor(type, {
                                bubbles: true,
                                cancelable: true,
                                composed: true,
                                dataTransfer
                            }) : new EventCtor(type, { bubbles: true, cancelable: true, composed: true });
                            target.dispatchEvent(event);
                        } catch (_) {
                            try {
                                const fallback = new EventCtor(type, { bubbles: true, cancelable: true, composed: true });
                                fallback.dataTransfer = dataTransfer;
                                target.dispatchEvent(fallback);
                            } catch (_) {}
                        }
                    };
                    dispatchDrag(start, "dragstart");
                    dispatchDrag(end, "dragenter");
                    dispatchDrag(end, "dragover");
                    dispatchDrag(end, "drop");
                    dispatchDrag(start, "dragend");
                    return JSON.stringify({ ok: true, startRef: ${quoteJs(startRef)}, endRef: ${quoteJs(endRef)} });
                } catch (e) {
                    return JSON.stringify({ ok: false, error: String(e) });
                }
            })();
            """.trimIndent()
        return runJsonScript(webView, script, "drag_dispatch_error")
    }

    private fun dispatchClickBySelector(
        webView: WebView,
        selector: String,
        button: String,
        modifiers: Set<String>,
        doubleClick: Boolean
    ): JSONObject? {
        val buttonValue =
            when (button) {
                "middle" -> 1
                "right" -> 2
                else -> 0
            }
        val buttonsValue =
            when (button) {
                "middle" -> 4
                "right" -> 2
                else -> 1
            }

        val altKey = modifiers.contains("Alt")
        val controlKey = modifiers.contains("Control") || modifiers.contains("ControlOrMeta")
        val metaKey = modifiers.contains("Meta") || modifiers.contains("ControlOrMeta")
        val shiftKey = modifiers.contains("Shift")

        val script =
            """
            (function() {
                try {
                    const selectorValue = ${quoteJs(selector)};
                    const target = document.querySelector(selectorValue);
                    if (!target) {
                        return JSON.stringify({ ok: false, error: "selector_not_found", selector: selectorValue });
                    }
                    const targetWindow = target.ownerDocument && target.ownerDocument.defaultView ? target.ownerDocument.defaultView : window;
                    const anchor = target.closest('a[href]');
                    try { target.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
                    const rect = target.getBoundingClientRect();
                    const x = rect.left + rect.width / 2;
                    const y = rect.top + rect.height / 2;

                    try { target.focus({ preventScroll: true }); } catch (_) {}

                    const buttonValue = ${buttonValue};
                    const buttonsValue = ${buttonsValue};
                    const altKey = ${if (altKey) "true" else "false"};
                    const ctrlKey = ${if (controlKey) "true" else "false"};
                    const metaKey = ${if (metaKey) "true" else "false"};
                    const shiftKey = ${if (shiftKey) "true" else "false"};

                    function emit(type, detail) {
                        try {
                            const MouseEventCtor = targetWindow.MouseEvent || MouseEvent;
                            target.dispatchEvent(new MouseEventCtor(type, {
                                bubbles: true,
                                cancelable: true,
                                composed: true,
                                view: targetWindow,
                                detail: detail,
                                clientX: x,
                                clientY: y,
                                screenX: x,
                                screenY: y,
                                button: buttonValue,
                                buttons: buttonsValue,
                                altKey,
                                ctrlKey,
                                metaKey,
                                shiftKey
                            }));
                        } catch (_) {}
                    }

                    function clickOnce(detail) {
                        emit("mousedown", detail);
                        emit("mouseup", detail);
                        emit("click", detail);
                    }

                    let activationMethod = "mouse_event";
                    let activationTag = String(target.tagName || "").toLowerCase();
                    const nativeAnchorClickEligible = !${if (doubleClick) "true" else "false"} &&
                        buttonValue === 0 && !altKey && !ctrlKey && !metaKey && !shiftKey &&
                        !!anchor && typeof anchor.click === "function";

                    setTimeout(() => {
                        try {
                            if (nativeAnchorClickEligible) {
                                activationMethod = "anchor_click";
                                activationTag = String(anchor.tagName || "").toLowerCase();
                                anchor.click();
                                return;
                            }
                            clickOnce(1);
                            if (${if (doubleClick) "true" else "false"}) {
                                emit("mousedown", 2);
                                emit("mouseup", 2);
                                emit("click", 2);
                                emit("dblclick", 2);
                            }
                            if (buttonValue === 0 && typeof target.click === "function") {
                                activationMethod = "native_click";
                                target.click();
                            }
                        } catch (_) {}
                    }, 0);

                    return JSON.stringify({
                        ok: true,
                        selector: selectorValue,
                        button: ${quoteJs(button)},
                        doubleClick: ${if (doubleClick) "true" else "false"},
                        activationMethod,
                        activationTag
                    });
                } catch (e) {
                    return JSON.stringify({ ok: false, error: String(e) });
                }
            })();
            """.trimIndent()
        return runJsonScript(webView, script, "click_dispatch_error")
    }

    internal fun runJsonScript(webView: WebView, script: String, fallbackError: String): JSONObject? {
        return try {
            val payload =
                evaluateJavascriptAsync(
                    webView,
                    script,
                    DEFAULT_TIMEOUT_MS.coerceIn(2_000L, 8_000L)
                )
            JSONObject(extractAsyncJsValue(payload))
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: fallbackError)
        }
    }

    private fun parseClickModifiers(raw: String?): Pair<Set<String>, List<String>> {
        if (raw.isNullOrBlank()) {
            return emptySet<String>() to emptyList()
        }

        val allowed = setOf("Alt", "Control", "ControlOrMeta", "Meta", "Shift")
        val parsed = linkedSetOf<String>()
        val invalid = mutableListOf<String>()

        val arr =
            try {
                JSONArray(raw)
            } catch (_: Exception) {
                return emptySet<String>() to listOf(raw)
            }

        for (i in 0 until arr.length()) {
            val token = arr.optString(i, "").trim()
            if (token in allowed) {
                parsed.add(token)
            } else {
                invalid.add(token.ifBlank { "<empty>" })
            }
        }

        return parsed to invalid
    }

    internal fun readCurrentUrl(webView: WebView, fallback: String): String {
        return runCatching {
            extractAsyncJsValue(
                evaluateJavascriptAsync(
                    webView,
                    "(function(){ return String(location.href || ''); })();",
                    2_000L
                )
            )
        }.getOrDefault(fallback)
    }

    private fun browserFileUpload(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")

        val rawPaths = param(tool, "paths")?.trim().orEmpty()
        val shouldCancel = rawPaths.isBlank()

        val files: List<File> =
            if (shouldCancel) {
                emptyList()
            } else {
                val pathList = parseStringArrayParam(rawPaths)
                    ?: return error(tool.name, "paths must be a JSON array")

                val resolved = mutableListOf<File>()
                for (rawPath in pathList) {
                    val path = rawPath.trim()
                    if (path.isBlank()) {
                        return error(tool.name, "paths contains an empty item")
                    }
                    val file = File(path)
                    if (!file.isAbsolute) {
                        return error(tool.name, "path must be absolute: $path")
                    }
                    if (!file.exists() || !file.isFile) {
                        return error(tool.name, "file does not exist: $path")
                    }
                    resolved.add(file)
                }
                resolved
            }

        var resolvedCode: String? = null
        var resolvedResult: String? = null
        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        val markers = captureActionMarkers(session)
        val callbackError =
            runOnMainSync {
                ensureSessionAttachedOnMain(session.id)

                val callback = session.pendingFileChooserCallback
                    ?: return@runOnMainSync "No file chooser is active"

                return@runOnMainSync try {
                    if (shouldCancel) {
                        callback.onReceiveValue(null)
                        session.pendingFileChooserCallback = null
                        notifySessionStateChanged(session)
                        resolvedCode = "await fileChooser.cancel();"
                        resolvedResult = "Cancelled the active file chooser."
                    } else {
                        val uris = files.map { Uri.fromFile(it) }.toTypedArray()
                        callback.onReceiveValue(uris)
                        session.pendingFileChooserCallback = null
                        notifySessionStateChanged(session)
                        resolvedCode =
                            "await fileChooser.setFiles(${renderJsArrayCode(files.map { it.absolutePath })});"
                        resolvedResult =
                            "Uploaded ${files.size} file(s): ${files.joinToString(", ") { it.absolutePath }}"
                    }
                    null
                } catch (e: Exception) {
                    "Failed to resolve file chooser: ${e.message}"
                }
            }

        if (callbackError != null) {
            return error(tool.name, callbackError)
        }
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy = BrowserActionSettlementPolicy(waitForDocumentReady = true)
            )
        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = resolvedCode,
                result = resolvedResult
            )
        )
    }

    private fun browserWaitFor(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val timeRaw = param(tool, "time")?.trim()?.takeIf { it.isNotBlank() }
        val parsedTimeSeconds = timeRaw?.toDoubleOrNull()
        if (timeRaw != null && parsedTimeSeconds == null) {
            return error(tool.name, "time must be a number")
        }
        val timeSeconds = parsedTimeSeconds?.takeIf { it > 0.0 }
        val text = param(tool, "text")?.takeIf { it.isNotBlank() }
        val textGone = param(tool, "textGone")?.takeIf { it.isNotBlank() }
        if (timeSeconds == null && text == null && textGone == null) {
            return error(tool.name, "One of time, text, or textGone is required")
        }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        val markers = captureActionMarkers(session)
        timeSeconds?.let { seconds ->
            Thread.sleep(((seconds * 1000.0).toLong()).coerceAtLeast(0L).coerceAtMost(30_000L))
        }
        if (textGone != null && !waitForTextState(session, textGone = textGone)) {
            return error(tool.name, "Timeout waiting for the requested condition")
        }
        if (text != null && !waitForTextState(session, text = text)) {
            return error(tool.name, "Timeout waiting for the requested condition")
        }
        val finalSession = sessionById(session.id) ?: session
        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(finalSession.id)
        }
        val settlement =
            BrowserActionSettlement(
                registry = buildPageRegistry(),
                session = finalSession,
                snapshot = latestSnapshot(finalSession),
                consoleMarker = markers.consoleTimestamp,
                downloadMarker = markers.downloadTimestamp,
                timedOut = false
            )

        val summary =
            when {
                text != null && textGone != null -> "Waited until \"$text\" appeared and \"$textGone\" disappeared."
                text != null -> "Waited until \"$text\" appeared."
                textGone != null -> "Waited until \"$textGone\" disappeared."
                else -> "Waited for ${timeSeconds ?: 0.0} second(s)."
            }

        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = buildWaitForCode(timeSeconds, text, textGone),
                result = summary
            )
        )
    }

    private fun browserSnapshot(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val selector = param(tool, "selector")?.trim()?.takeIf { it.isNotBlank() }
        val depthRaw = param(tool, "depth")?.trim()?.takeIf { it.isNotBlank() }
        val depth =
            when {
                depthRaw == null -> null
                depthRaw.toIntOrNull() == null -> return error(tool.name, "depth must be a non-negative integer")
                depthRaw.toInt() < 0 -> return error(tool.name, "depth must be a non-negative integer")
                else -> depthRaw.toInt()
            }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }

        val snapshot = captureSnapshotModel(session, selector = selector, depth = depth)
        session.lastSnapshot = snapshot
        val filename = param(tool, "filename")?.trim()?.takeIf { it.isNotBlank() }
        val snapshotSection =
            if (filename != null) {
                formatBrowserFileLink(
                    "Snapshot",
                    writeBrowserTextOutput(filename, snapshot.yaml, "page", "yml")
                )
            } else {
                snapshot.yaml
            }

        return ok(
            tool.name,
            buildBrowserResponse(
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                snapshot = snapshotSection,
                modalState = renderModalState(session),
                downloads = renderManagedDownloads(session, marker = 0L, includeAll = true)
            )
        )
    }

    private fun browserNavigateBack(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val couldGoBack = session.canGoBack || runCatching { session.webView.canGoBack() }.getOrDefault(false)
        val markers = captureActionMarkers(session)

        runOnMainSync(timeoutMs = 8_000L) {
            ensureSessionAttachedOnMain(session.id)
            if (session.webView.canGoBack()) {
                session.webView.goBack()
            }
            refreshNavigationStateAsync(session)
            Unit
        }
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy =
                    BrowserActionSettlementPolicy(
                        timeoutMs = DEFAULT_TIMEOUT_MS.coerceAtLeast(4_000L),
                        waitForDocumentReady = true,
                        waitForNavigationChange = couldGoBack,
                        captureSnapshot = false
                    )
            )

        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = "await page.goBack();",
                result =
                    if (couldGoBack) {
                        "Navigated back."
                    } else {
                        "No back history entry was available."
                    }
            )
        )
    }

    private fun browserHover(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val ref = param(tool, "ref")?.trim()?.takeIf { it.isNotBlank() }
            ?: return error(tool.name, "ref is required")

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        if (requireSnapshotNode(session, ref) == null) {
            return pageError(
                tool.name,
                session,
                "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
            )
        }
        val markers = captureActionMarkers(session)
        val jsResult = dispatchHoverByRef(session.webView, ref)
        if (jsResult?.optBoolean("ok", false) != true) {
            return if (jsResult?.optString("error") == "ref_not_found") {
                pageError(
                    tool.name,
                    session,
                    "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
                )
            } else {
                error(tool.name, "Hover failed: ${jsResult?.optString("error") ?: "unknown"}")
            }
        }
        val settlement = settleBrowserAction(session, markers)

        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = "await ${locatorExpressionForRef(session, ref)}.hover();",
                result = "Hovered ref=$ref"
            )
        )
    }

    private fun browserDrag(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val startRef = param(tool, "startRef")?.trim()?.takeIf { it.isNotBlank() }
            ?: return error(tool.name, "startRef is required")
        val endRef = param(tool, "endRef")?.trim()?.takeIf { it.isNotBlank() }
            ?: return error(tool.name, "endRef is required")

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        if (requireSnapshotNode(session, startRef) == null) {
            return pageError(
                tool.name,
                session,
                "Ref $startRef was not found in the current snapshot. Capture a new snapshot and retry."
            )
        }
        if (requireSnapshotNode(session, endRef) == null) {
            return pageError(
                tool.name,
                session,
                "Ref $endRef was not found in the current snapshot. Capture a new snapshot and retry."
            )
        }
        val markers = captureActionMarkers(session)
        val jsResult = dispatchDragByRef(session.webView, startRef, endRef)
        if (jsResult?.optBoolean("ok", false) != true) {
            return when (jsResult?.optString("error")) {
                "start_ref_not_found" ->
                    pageError(
                        tool.name,
                        session,
                        "Ref $startRef was not found in the current snapshot. Capture a new snapshot and retry."
                    )
                "end_ref_not_found" ->
                    pageError(
                        tool.name,
                        session,
                        "Ref $endRef was not found in the current snapshot. Capture a new snapshot and retry."
                    )
                else -> error(tool.name, "Drag failed: ${jsResult?.optString("error") ?: "unknown"}")
            }
        }
        val settlement = settleBrowserAction(session, markers)
        val startLocator = locatorExpressionForRef(session, startRef)
        val endLocator = locatorExpressionForRef(session, endRef)

        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = "await $startLocator.dragTo($endLocator);",
                result = "Dragged ref=$startRef to ref=$endRef"
            )
        )
    }

    private fun browserEvaluate(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val functionSource = param(tool, "function")?.trim()
        if (functionSource.isNullOrBlank()) {
            return error(tool.name, "function is required")
        }
        val ref = param(tool, "ref")?.trim()?.takeIf { it.isNotBlank() }
        val elementDescription = param(tool, "element")?.trim()?.takeIf { it.isNotBlank() }
        if (!elementDescription.isNullOrBlank() && ref == null) {
            return error(tool.name, "ref is required when element is provided")
        }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        if (ref != null && requireSnapshotNode(session, ref) == null) {
            return pageError(
                tool.name,
                session,
                "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
            )
        }
        val markers = captureActionMarkers(session)
        val value = evaluatePageFunction(session.webView, functionSource, ref, DEFAULT_TIMEOUT_MS)
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy = BrowserActionSettlementPolicy(waitForDocumentReady = true)
            )
        val code =
            if (ref != null) {
                "await ${locatorExpressionForRef(session, ref)}.evaluate(${functionSource});"
            } else {
                "await page.evaluate(${functionSource});"
            }
        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = code,
                result = value
            )
        )
    }

    private fun browserFillForm(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val rawFields = param(tool, "fields")?.trim()
        if (rawFields.isNullOrBlank()) {
            return error(tool.name, "fields is required")
        }
        val fields = parseFormFields(rawFields) ?: return error(tool.name, "fields must be a JSON array")
        if (fields.isEmpty()) {
            return error(tool.name, "fields must not be empty")
        }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        fields.forEach { field ->
            val ref = field.optString("ref").trim()
            if (ref.isNotBlank() && requireSnapshotNode(session, ref) == null) {
                return pageError(
                    tool.name,
                    session,
                    "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
                )
            }
        }
        val markers = captureActionMarkers(session)
        val result = fillFormFields(session.webView, rawFields)
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy = BrowserActionSettlementPolicy(waitForDocumentReady = true)
            )
        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = "await page.fillForm(/* ${fields.size} field(s) */);",
                result = result
            )
        )
    }

    private fun browserHandleDialog(tool: AITool): ToolResult {
        val acceptRaw = param(tool, "accept")
            ?: return error(tool.name, "accept is required")
        val accept =
            acceptRaw.equals("true", ignoreCase = true) ||
                acceptRaw == "1" ||
                acceptRaw.equals("yes", ignoreCase = true)
        val promptText = param(tool, "promptText")
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val pending = session.pendingDialog ?: return error(tool.name, "No dialog is currently open")
        val markers = captureActionMarkers(session)

        runOnMainSync<Unit> {
            resolvePendingDialogOnMain(
                session = session,
                accept = accept,
                promptText = promptText
            )
        }
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy = BrowserActionSettlementPolicy(waitForDocumentReady = true)
            )

        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code =
                    if (accept) {
                        "await dialog.accept(${quoteJsCode(promptText ?: pending.defaultValue.orEmpty())});"
                    } else {
                        "await dialog.dismiss();"
                    },
                modalState = "No modal dialog is currently open.",
                result = "Handled ${pending.type} dialog."
            )
        )
    }

    private fun browserConsoleMessages(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val level = param(tool, "level")?.trim()?.lowercase(Locale.ROOT).orEmpty().ifBlank { "info" }
        if (level !in setOf("error", "warning", "warn", "info", "debug")) {
            return error(tool.name, "level must be one of: error, warning, info, debug")
        }
        val rendered = renderAllConsoleMessages(session, level)
        val filename = param(tool, "filename")?.trim()?.takeIf { it.isNotBlank() }
        val sectionContent =
            if (filename != null) {
                "Saved console messages to ${writeBrowserTextOutput(filename, rendered, "console_messages", "log")}"
            } else {
                rendered
            }
        return ok(
            tool.name,
            buildBrowserResponse(
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                consoleMessages = sectionContent
            )
        )
    }

    private fun browserNetworkRequests(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val includeStatic = boolParam(tool, "includeStatic", false)
        val rendered = renderNetworkRequestLog(session, includeStatic)
        val filename = param(tool, "filename")?.trim()?.takeIf { it.isNotBlank() }
        val resultText =
            if (filename != null) {
                "Saved network requests to ${writeBrowserTextOutput(filename, rendered, "network_requests", "log")}"
            } else {
                rendered
            }
        return ok(
            tool.name,
            buildBrowserResponse(
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                result = resultText
            )
        )
    }

    private fun browserPressKey(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val key = param(tool, "key")?.trim()
        if (key.isNullOrBlank()) {
            return error(tool.name, "key is required")
        }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        val markers = captureActionMarkers(session)
        val result = pressKeyOnPage(session.webView, key)
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy =
                    if (key.equals("Enter", ignoreCase = true)) {
                        BrowserActionSettlementPolicy(
                            waitForDocumentReady = true,
                            waitForTimeSeconds = 0.5
                        )
                    } else {
                        BrowserActionSettlementPolicy()
                    }
            )
        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = "await page.keyboard.press(${quoteJsCode(key)});",
                result = result
            )
        )
    }

    private fun browserResize(tool: AITool): ToolResult {
        ensureOverlayPermission(tool.name)?.let { return it }
        val width = intParam(tool, "width", -1)
        val height = intParam(tool, "height", -1)
        if (width <= 0 || height <= 0) {
            return error(tool.name, "width and height must be positive integers")
        }
        val density =
            context.resources.displayMetrics.density
                .takeIf { it.isFinite() && it > 0f }
                ?: 1f
        val cssPixelCount = width.toLong() * height.toLong()
        val layoutWidth = kotlin.math.ceil(width.toDouble() * density).toLong()
        val layoutHeight = kotlin.math.ceil(height.toDouble() * density).toLong()
        val layoutPixelCount = layoutWidth * layoutHeight
        if (
            width > MAX_VIEWPORT_DIMENSION_CSS_PX ||
                height > MAX_VIEWPORT_DIMENSION_CSS_PX ||
                cssPixelCount > MAX_VIEWPORT_PIXEL_COUNT ||
                layoutPixelCount > MAX_VIEWPORT_LAYOUT_PIXEL_COUNT
        ) {
            return error(
                tool.name,
                "viewport is too large for this device; maximum side is " +
                    "$MAX_VIEWPORT_DIMENSION_CSS_PX CSS px and the requested pixel area " +
                    "must fit the device rendering budget"
            )
        }

        val session =
            runOnMainSync {
                getSession(null) ?: createSessionTabOnMain(context.applicationContext, "about:blank")
            }
        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
            session.viewportWidthPx = width
            session.viewportHeightPx = height
            session.hasExplicitViewportSize = true
            browserHost?.setViewportSize(width, height)
            applyViewportOverride(session)
            refreshSessionUiOnMain(session.id)
        }
        Thread.sleep(100)

        return ok(
            tool.name,
            buildBrowserResponse(
                code = "await page.setViewportSize({ width: $width, height: $height });",
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                snapshot = captureSnapshotText(session),
                result = "Resized browser viewport to ${width}x${height}."
            )
        )
    }

    private fun browserRunCode(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val code = param(tool, "code")?.trim()
        if (code.isNullOrBlank()) {
            return error(tool.name, "code is required")
        }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        val markers = captureActionMarkers(session)
        val result = runPlaywrightLikeCode(session, code)
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy = BrowserActionSettlementPolicy(waitForDocumentReady = true)
            )
        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = code,
                result = result
            )
        )
    }

    private fun browserSelectOption(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val ref = param(tool, "ref")?.trim()?.takeIf { it.isNotBlank() }
            ?: return error(tool.name, "ref is required")
        val values = parseStringArrayParam(param(tool, "values"))
            ?: return error(tool.name, "values must be a JSON array")
        if (values.isEmpty()) {
            return error(tool.name, "values must not be empty")
        }

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        if (requireSnapshotNode(session, ref) == null) {
            return pageError(
                tool.name,
                session,
                "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
            )
        }
        val markers = captureActionMarkers(session)
        val result = selectOptionsByRef(session.webView, ref, values)
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy = BrowserActionSettlementPolicy(waitForDocumentReady = true)
            )
        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = "await ${locatorExpressionForRef(session, ref)}.selectOption(${renderJsArrayCode(values)});",
                result = result
            )
        )
    }

    private fun browserTabs(tool: AITool): ToolResult {
        val action = param(tool, "action")?.trim()?.lowercase(Locale.ROOT)
            ?: return error(tool.name, "action is required")
        return when (action) {
            "list" -> {
                val registry = buildPageRegistry()
                val session = registry.activeSessionId?.let { sessionId -> sessionById(sessionId) }
                ok(
                    tool.name,
                    buildBrowserResponse(
                        openTabs = renderOpenTabs(registry),
                        pageState = session?.let { activeSession -> renderPageState(activeSession) } ?: "No active page.",
                        snapshot = session?.let { activeSession -> captureSnapshotText(activeSession) },
                        result = "Listed open tabs."
                    )
                )
            }

            "create" -> {
                ensureOverlayPermission(tool.name)?.let { return it }
                val session =
                    runOnMainSync {
                        createSessionTabOnMain(context.applicationContext, initialUrl = "about:blank")
                    }
                val settlement = settleBrowserAction(session, captureActionMarkers(session))
                ok(
                    tool.name,
                    buildSettledBrowserResponse(
                        settlement = settlement,
                        result = "Created tab ${currentTabIndex(session.id)}."
                    )
                )
            }

            "select" -> {
                val index = intParam(tool, "index", -1)
                if (index < 0) {
                    return error(tool.name, "index is required for action=select")
                }
                val targetId = sessionIdAtIndex(index) ?: return error(tool.name, "Tab index out of range: $index")
                runOnMainSync<Unit> {
                    ensureSessionAttachedOnMain(targetId)
                }
                val session = sessions[targetId] ?: return error(tool.name, "Tab index out of range: $index")
                val settlement = settleBrowserAction(session, captureActionMarkers(session))
                ok(
                    tool.name,
                    buildSettledBrowserResponse(
                        settlement = settlement,
                        result = "Selected tab $index."
                    )
                )
            }

            "close" -> {
                val requestedIndex = param(tool, "index")?.trim()?.toIntOrNull()
                val targetId =
                    when {
                        requestedIndex != null -> sessionIdAtIndex(requestedIndex)
                        else -> resolvePreferredSessionId()
                    } ?: return error(tool.name, "No tab available to close")
                if (!closeSession(targetId)) {
                    return error(tool.name, "Failed to close tab")
                }
                val registry = buildPageRegistry()
                val active = registry.activeSessionId?.let { sessionId -> sessionById(sessionId) }
                ok(
                    tool.name,
                    buildBrowserResponse(
                        openTabs = renderOpenTabs(registry),
                        pageState = active?.let { activeSession -> renderPageState(activeSession) } ?: "No active page.",
                        snapshot = active?.let { activeSession -> captureSnapshotText(activeSession) },
                        result =
                            if (active != null) {
                                "Closed tab. Active tab is now ${currentTabIndex(active.id)}."
                            } else {
                                "Closed the last tab."
                            }
                    )
                )
            }

            else -> error(tool.name, "action must be one of: list, create, select, close")
        }
    }

    private fun browserTakeScreenshot(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val type = param(tool, "type")?.trim()?.lowercase(Locale.ROOT).orEmpty().ifBlank { "png" }
        if (type !in setOf("png", "jpeg", "jpg")) {
            return error(tool.name, "type must be png or jpeg")
        }
        val element = param(tool, "element")?.trim()?.takeIf { it.isNotBlank() }
        val ref = param(tool, "ref")?.trim()?.takeIf { it.isNotBlank() }
        val fullPage = boolParam(tool, "fullPage", false)
        if (element != null && ref == null) {
            return error(tool.name, "ref is required when element is provided")
        }
        if (fullPage && ref != null) {
            return error(tool.name, "fullPage cannot be used with element screenshots")
        }
        val filename = param(tool, "filename")?.trim()?.takeIf { it.isNotBlank() }
        val savedPath = takeScreenshot(session, filename, type, fullPage, ref)
        return ok(
            tool.name,
            buildBrowserResponse(
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                result = "Saved screenshot to $savedPath"
            )
        )
    }

    private fun browserType(tool: AITool): ToolResult {
        val session = getSession(null) ?: return error(tool.name, "No active browser tab")
        val ref = param(tool, "ref")?.trim()?.takeIf { it.isNotBlank() }
            ?: return error(tool.name, "ref is required")
        val text = param(tool, "text") ?: return error(tool.name, "text is required")
        val submit = boolParam(tool, "submit", false)
        val slowly = boolParam(tool, "slowly", false)

        runOnMainSync<Unit> {
            ensureSessionAttachedOnMain(session.id)
        }
        if (requireSnapshotNode(session, ref) == null) {
            return pageError(
                tool.name,
                session,
                "Ref $ref was not found in the current snapshot. Capture a new snapshot and retry."
            )
        }
        val markers = captureActionMarkers(session)
        val result = typeIntoElementByRef(session.webView, ref, text, submit, slowly)
        val settlement =
            settleBrowserAction(
                initialSession = session,
                markers = markers,
                policy =
                    if (submit || slowly) {
                        BrowserActionSettlementPolicy(
                            waitForDocumentReady = true,
                            waitForTimeSeconds = 0.5
                        )
                    } else {
                        BrowserActionSettlementPolicy()
                    }
            )
        val code =
            buildString {
                val locator = locatorExpressionForRef(session, ref)
                if (slowly) {
                    append("await $locator.pressSequentially(${quoteJsCode(text)});")
                } else {
                    append("await $locator.fill(${quoteJsCode(text)});")
                }
                if (submit) {
                    append("\nawait $locator.press('Enter');")
                }
            }
        return ok(
            tool.name,
            buildSettledBrowserResponse(
                settlement = settlement,
                code = code,
                result = result
            )
        )
    }

    private fun browserClose(tool: AITool): ToolResult {
        val activeId = resolvePreferredSessionId()
            ?: return ok(tool.name, buildBrowserResponse(openTabs = "No open tabs.", result = "No browser tab was open."))
        if (!closeSession(activeId)) {
            return error(tool.name, "Failed to close the current tab")
        }
        val registry = buildPageRegistry()
        val active = registry.activeSessionId?.let { sessionId -> sessionById(sessionId) }
        return ok(
            tool.name,
            buildBrowserResponse(
                openTabs = renderOpenTabs(registry),
                pageState = active?.let { activeSession -> renderPageState(activeSession) } ?: "No active page.",
                snapshot = active?.let { activeSession -> captureSnapshotText(activeSession) },
                result =
                    if (active != null) {
                        "Closed the current tab."
                    } else {
                        "Closed the last tab and browser overlay."
                    }
            )
        )
    }

    private fun browserCloseAll(tool: AITool): ToolResult {
        val ids = orderedSessionIds()
        if (ids.isEmpty()) {
            return ok(tool.name, buildBrowserResponse(openTabs = "No open tabs.", result = "No browser tabs were open."))
        }

        ids.forEach { sessionId ->
            closeSession(sessionId)
        }

        return ok(
            tool.name,
            buildBrowserResponse(
                openTabs = "No open tabs.",
                pageState = "No active page.",
                result = "Closed all browser tabs."
            )
        )
    }

    private fun determineExtensionFromMimeType(mimeType: String): String =
        when {
            mimeType.lowercase(Locale.ROOT).startsWith("image/") -> ".${mimeType.lowercase(Locale.ROOT).substringAfter('/')}"
            mimeType.lowercase(Locale.ROOT).startsWith("audio/") -> ".${mimeType.lowercase(Locale.ROOT).substringAfter('/')}"
            mimeType.lowercase(Locale.ROOT).startsWith("video/") -> ".${mimeType.lowercase(Locale.ROOT).substringAfter('/')}"
            mimeType.lowercase(Locale.ROOT).contains("pdf") -> ".pdf"
            mimeType.lowercase(Locale.ROOT).contains("json") -> ".json"
            mimeType.lowercase(Locale.ROOT).contains("xml") -> ".xml"
            mimeType.lowercase(Locale.ROOT).contains("csv") -> ".csv"
            mimeType.lowercase(Locale.ROOT).contains("zip") -> ".zip"
            mimeType.lowercase(Locale.ROOT).contains("html") -> ".html"
            mimeType.lowercase(Locale.ROOT).contains("javascript") -> ".js"
            mimeType.lowercase(Locale.ROOT).contains("plain") -> ".txt"
            else -> ".bin"
        }

    private fun quoteJs(value: String): String = JSONObject.quote(value)

    internal fun quoteJsCode(value: String): String {
        val escaped =
            buildString(value.length + 8) {
                value.forEach { ch ->
                    when (ch) {
                        '\\' -> append("\\\\")
                        '\'' -> append("\\'")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
            }
        return "'$escaped'"
    }

    internal fun renderJsArrayCode(values: Collection<String>): String =
        values.joinToString(prefix = "[", postfix = "]", separator = ", ") { quoteJsCode(it) }

    internal fun nextSnapshotGeneration(): Long = snapshotGenerationSeed.incrementAndGet()
    internal fun <T> runOnMainSync(timeoutMs: Long = 8_000L, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }

        val latch = CountDownLatch(1)
        var result: T? = null
        var error: Throwable? = null

        mainHandler.post {
            try {
                result = block()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw RuntimeException("Main-thread operation timeout (${timeoutMs}ms)")
        }

        if (error != null) {
            throw RuntimeException(error)
        }

        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

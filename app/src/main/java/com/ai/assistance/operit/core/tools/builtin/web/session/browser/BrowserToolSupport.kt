package com.ai.assistance.operit.core.tools.builtin.web.session.browser

import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.StandardBrowserSessionTools
import java.util.Locale
import java.util.concurrent.CountDownLatch
import org.json.JSONArray
import org.json.JSONObject

internal data class BrowserPageRegistry(
    val orderedSessionIds: List<String>,
    val activeSessionId: String?,
    val overlayExpanded: Boolean,
    val snapshots: Map<String, BrowserSnapshot?>
)

internal data class BrowserSnapshot(
    val sessionId: String,
    val generation: Long,
    val yaml: String,
    val nodesByRef: Map<String, BrowserSnapshotNode>,
    val createdAt: Long = System.currentTimeMillis()
)

internal data class BrowserSnapshotNode(
    val ref: String,
    val role: String,
    val name: String
)

internal data class BrowserConsoleEntry(
    val level: String,
    val message: String,
    val sourceId: String? = null,
    val lineNumber: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

internal data class BrowserNetworkRequestEntry(
    val method: String,
    val url: String,
    val isMainFrame: Boolean,
    val isStatic: Boolean,
    val headers: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

internal data class PendingDialog(
    val type: String,
    val message: String,
    val defaultValue: String? = null,
    val url: String? = null,
    val jsResult: JsResult? = null,
    val jsPromptResult: JsPromptResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)

internal data class PendingAsyncJsCall(
    val latch: CountDownLatch = CountDownLatch(1),
    @Volatile var result: String? = null,
    @Volatile var error: String? = null
)

internal data class WebDownloadEvent(
    val status: String,
    val type: String,
    val fileName: String,
    val url: String? = null,
    val mimeType: String? = null,
    val savedPath: String? = null,
    val downloadId: Long? = null,
    val error: String? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("status", status)
            .put("type", type)
            .put("file_name", fileName)
            .also { json ->
                if (!url.isNullOrBlank()) {
                    json.put("url", url)
                }
                if (!mimeType.isNullOrBlank()) {
                    json.put("mime_type", mimeType)
                }
                if (!savedPath.isNullOrBlank()) {
                    json.put("saved_path", savedPath)
                }
                if (downloadId != null) {
                    json.put("download_id", downloadId)
                }
                if (!error.isNullOrBlank()) {
                    json.put("error", error)
                }
            }
}

internal fun buildBrowserResponse(
    code: String? = null,
    openTabs: String? = null,
    pageState: String? = null,
    snapshot: String? = null,
    consoleMessages: String? = null,
    modalState: String? = null,
    downloads: String? = null,
    result: String? = null,
    error: String? = null
): String {
    val sections = mutableListOf<String>()
    if (!code.isNullOrBlank()) {
        sections += "### Ran Playwright code\n```js\n${code.trim()}\n```"
    }
    if (!openTabs.isNullOrBlank()) {
        sections += "### Open tabs\n${openTabs.trim()}"
    }
    if (!pageState.isNullOrBlank()) {
        sections += "### Page\n${pageState.trim()}"
    }
    if (snapshot != null) {
        sections += "### Snapshot\n${formatSnapshotSection(snapshot)}"
    }
    if (!consoleMessages.isNullOrBlank()) {
        sections += "### New console messages\n${consoleMessages.trim()}"
    }
    if (!modalState.isNullOrBlank()) {
        sections += "### Modal state\n${modalState.trim()}"
    }
    if (!downloads.isNullOrBlank()) {
        sections += "### Downloads\n${downloads.trim()}"
    }
    if (!result.isNullOrBlank()) {
        sections += "### Result\n${result.trim()}"
    }
    if (!error.isNullOrBlank()) {
        sections += "### Error\n${error.trim()}"
    }
    return sections.joinToString("\n\n")
}

private fun formatSnapshotSection(snapshot: String): String {
    val trimmed = snapshot.trim()
    return when {
        trimmed.startsWith("- [Snapshot](") -> trimmed
        trimmed.startsWith("```yaml") -> trimmed
        else -> "```yaml\n$trimmed\n```"
    }
}

internal typealias BrowserToolSession = StandardBrowserSessionTools.WebSession
internal typealias BrowserToolActionPolicy = StandardBrowserSessionTools.BrowserActionSettlementPolicy
internal typealias BrowserToolActionMarkers = StandardBrowserSessionTools.BrowserActionMarkers
internal typealias BrowserToolActionSettlement = StandardBrowserSessionTools.BrowserActionSettlement
internal typealias BrowserSnapshotSession = BrowserToolSession

internal fun StandardBrowserSessionTools.latestConsoleTimestamp(session: BrowserToolSession): Long =
    synchronized(session.consoleEntries) {
        session.consoleEntries.lastOrNull()?.timestamp ?: 0L
    }

internal fun StandardBrowserSessionTools.appendConsoleEntry(
    session: BrowserToolSession,
    entry: BrowserConsoleEntry
) {
    synchronized(session.consoleEntries) {
        session.consoleEntries += entry
        if (session.consoleEntries.size > StandardBrowserSessionTools.MAX_EVENT_LOG_ENTRIES) {
            session.consoleEntries.removeAt(0)
        }
    }
    notifySessionStateChanged(session)
}

internal fun StandardBrowserSessionTools.clearEventLogs(session: BrowserToolSession) {
    synchronized(session.consoleEntries) {
        session.consoleEntries.clear()
    }
    synchronized(session.networkEntries) {
        session.networkEntries.clear()
    }
}

internal fun StandardBrowserSessionTools.recordNetworkRequest(
    session: BrowserToolSession,
    request: WebResourceRequest
) {
    val url = request.url?.toString().orEmpty()
    if (url.isBlank()) {
        return
    }
    val headers = request.requestHeaders?.mapKeys { it.key ?: "" } ?: emptyMap()
    val acceptHeader = headers.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
    val entry =
        com.ai.assistance.operit.core.tools.builtin.web.session.browser.BrowserNetworkRequestEntry(
            method = request.method.orEmpty().ifBlank { "GET" },
            url = url,
            isMainFrame = request.isForMainFrame,
            isStatic = isStaticRequest(url, acceptHeader),
            headers = headers
        )
    synchronized(session.networkEntries) {
        session.networkEntries += entry
        if (session.networkEntries.size > StandardBrowserSessionTools.MAX_EVENT_LOG_ENTRIES) {
            session.networkEntries.removeAt(0)
        }
    }
}

internal fun StandardBrowserSessionTools.notifySessionStateChanged(session: BrowserToolSession) {
    synchronized(session.stateSignal) {
        session.stateVersion += 1L
        session.stateSignal.notifyAll()
    }
}

internal fun StandardBrowserSessionTools.awaitSessionStateChange(
    session: BrowserToolSession,
    observedVersion: Long,
    timeoutMs: Long
): Long {
    val safeTimeoutMs = timeoutMs.coerceAtLeast(1L)
    synchronized(session.stateSignal) {
        if (session.stateVersion == observedVersion) {
            runCatching {
                session.stateSignal.wait(safeTimeoutMs)
            }
        }
        return session.stateVersion
    }
}

internal fun StandardBrowserSessionTools.renderAllConsoleMessages(
    session: BrowserToolSession,
    level: String
): String {
    val threshold = consoleSeverity(level)
    val messages =
        synchronized(session.consoleEntries) {
            session.consoleEntries.toList()
        }.filter { consoleSeverity(it.level) <= threshold }
    if (messages.isEmpty()) {
        return "No console messages."
    }
    return messages.joinToString("\n") { entry ->
        val source = if (!entry.sourceId.isNullOrBlank()) " (${entry.sourceId}:${entry.lineNumber ?: 0})" else ""
        "- [${normalizeConsoleLevel(entry.level)}] ${entry.message}$source"
    }
}

internal fun StandardBrowserSessionTools.renderNewConsoleMessages(
    session: BrowserToolSession,
    marker: Long
): String? {
    val messages =
        synchronized(session.consoleEntries) {
            session.consoleEntries.filter { it.timestamp > marker }
        }
    if (messages.isEmpty()) {
        return null
    }
    return messages.joinToString("\n") { entry ->
        val source = if (!entry.sourceId.isNullOrBlank()) " (${entry.sourceId}:${entry.lineNumber ?: 0})" else ""
        "- [${normalizeConsoleLevel(entry.level)}] ${entry.message}$source"
    }
}

internal fun StandardBrowserSessionTools.renderNetworkRequestLog(
    session: BrowserToolSession,
    includeStatic: Boolean
): String {
    val entries =
        synchronized(session.networkEntries) {
            session.networkEntries.toList()
        }.filter { includeStatic || !it.isStatic }
    if (entries.isEmpty()) {
        return "No network requests recorded for the current page."
    }
    return entries.joinToString("\n") { entry ->
        val frameTag = if (entry.isMainFrame) " [main-frame]" else ""
        val staticTag = if (entry.isStatic) " [static]" else ""
        "- ${entry.method} ${entry.url}$frameTag$staticTag"
    }
}

internal fun StandardBrowserSessionTools.renderModalState(session: BrowserToolSession): String? {
    val dialog = session.pendingDialog ?: return null
    return buildString {
        appendLine("- Type: ${dialog.type}")
        appendLine("- Message: ${dialog.message.ifBlank { "(empty)" }}")
        if (!dialog.defaultValue.isNullOrBlank()) {
            appendLine("- Default value: ${dialog.defaultValue}")
        }
        append("- URL: ${dialog.url.orEmpty().ifBlank { session.currentUrl.ifBlank { "about:blank" } }}")
    }
}

internal fun StandardBrowserSessionTools.renderDownloads(
    session: BrowserToolSession,
    marker: Long
): String? = renderManagedDownloads(session, marker)

internal fun StandardBrowserSessionTools.requireSnapshotNode(
    session: BrowserToolSession,
    ref: String
): BrowserSnapshotNode? {
    val snapshot = session.lastSnapshot ?: latestSnapshot(session)
    return snapshot.nodesByRef[ref]
}

internal fun StandardBrowserSessionTools.captureActionMarkers(
    session: BrowserToolSession
): BrowserToolActionMarkers =
    BrowserToolActionMarkers(
        initialSessionId = session.id,
        initialUrl = readCurrentUrl(session.webView, session.currentUrl).ifBlank { session.currentUrl },
        consoleTimestamp = latestConsoleTimestamp(session),
        downloadTimestamp = latestBrowserDownloadEventAt(),
        snapshotGeneration = session.lastSnapshot?.generation ?: 0L,
        startedAt = System.currentTimeMillis()
    )

internal fun StandardBrowserSessionTools.isDocumentReady(session: BrowserToolSession): Boolean {
    if (session.pageLoaded && !session.isLoading) {
        return true
    }
    val ready =
        runCatching {
            extractAsyncJsValue(
                evaluateJavascriptAsync(
                    session.webView,
                    "(function(){ return String(document.readyState || ''); })();",
                    2_000L
                )
            )
        }.getOrNull()
    return ready == "complete" || ready == "interactive"
}

internal fun StandardBrowserSessionTools.waitForDocumentReady(
    session: BrowserToolSession,
    timeoutMs: Long
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(250L)
    var observedVersion = session.stateVersion
    while (System.currentTimeMillis() < deadline) {
        if (isDocumentReady(session)) {
            return true
        }
        val remainingMs = deadline - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            break
        }
        observedVersion =
            awaitSessionStateChange(
                session,
                observedVersion,
                remainingMs.coerceAtMost(120L)
            )
    }
    return false
}

internal fun StandardBrowserSessionTools.matchesTextState(
    session: BrowserToolSession,
    text: String?,
    textGone: String?
): Boolean {
    val bodyText =
        runCatching {
            extractAsyncJsValue(
                evaluateJavascriptAsync(
                    session.webView,
                    "(function(){ return String((document.body && document.body.innerText) || ''); })();",
                    2_000L
                )
            )
        }.getOrElse { "" }
    val containsWanted = text == null || bodyText.contains(text)
    val goneSatisfied = textGone == null || !bodyText.contains(textGone)
    return containsWanted && goneSatisfied
}

internal fun StandardBrowserSessionTools.buildWaitForCode(
    timeSeconds: Double?,
    text: String?,
    textGone: String?
): String =
    buildString {
        timeSeconds?.let { seconds ->
            appendLine("await new Promise(f => setTimeout(f, ${seconds} * 1000));")
        }
        textGone?.let { gone ->
            appendLine("await page.getByText(${quoteJsCode(gone)}).first().waitFor({ state: 'hidden' });")
        }
        text?.let { visible ->
            append("await page.getByText(${quoteJsCode(visible)}).first().waitFor({ state: 'visible' });")
        }
    }.trim()

internal fun StandardBrowserSessionTools.waitForTextState(
    session: BrowserToolSession,
    text: String? = null,
    textGone: String? = null,
    timeoutMs: Long = StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(200L)
    var observedVersion = session.stateVersion
    while (System.currentTimeMillis() < deadline) {
        if (matchesTextState(session, text = text, textGone = textGone)) {
            return true
        }
        val remainingMs = deadline - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            break
        }
        observedVersion =
            awaitSessionStateChange(
                session,
                observedVersion,
                remainingMs.coerceAtMost(120L)
            )
    }
    return matchesTextState(session, text = text, textGone = textGone)
}

internal fun StandardBrowserSessionTools.buildClickCode(
    session: BrowserToolSession,
    ref: String,
    button: String,
    doubleClick: Boolean,
    modifiers: Set<String>
): String {
    val locator = locatorExpressionForRef(session, ref)
    val method = if (doubleClick) "dblclick" else "click"
    val options = mutableListOf<String>()
    if (button != "left") {
        options += "button: ${quoteJsCode(button)}"
    }
    if (modifiers.isNotEmpty()) {
        options += "modifiers: ${renderJsArrayCode(modifiers.toList())}"
    }
    return if (options.isEmpty()) {
        "await $locator.$method();"
    } else {
        "await $locator.$method({ ${options.joinToString(", ")} });"
    }
}

internal fun StandardBrowserSessionTools.settleBrowserAction(
    initialSession: BrowserToolSession,
    markers: BrowserToolActionMarkers,
    policy: BrowserToolActionPolicy = BrowserToolActionPolicy()
): BrowserToolActionSettlement {
    val deadline = markers.startedAt + policy.timeoutMs.coerceAtLeast(250L)
    var candidateSession = sessionById(markers.initialSessionId) ?: initialSession
    var observedVersion = candidateSession.stateVersion

    while (System.currentTimeMillis() < deadline) {
        val registry = buildPageRegistry()
        val activeSession =
            when {
                policy.allowActivePageSwitch -> registry.activeSessionId?.let(::sessionById)
                else -> sessionById(markers.initialSessionId)
            } ?: sessionById(markers.initialSessionId)
                ?: candidateSession

        candidateSession = activeSession
        observedVersion = activeSession.stateVersion
        if (actionSettled(activeSession, markers, policy)) {
            runOnMainSync<Unit> {
                ensureSessionAttachedOnMain(activeSession.id)
            }
            val snapshot =
                if (policy.captureSnapshot && canCaptureSnapshotForSettlement(activeSession)) {
                    latestSnapshot(activeSession)
                } else {
                    null
                }
            val finalRegistry = buildPageRegistry()
            return BrowserToolActionSettlement(
                registry = finalRegistry,
                session = activeSession,
                snapshot = snapshot,
                consoleMarker = if (activeSession.id == markers.initialSessionId) markers.consoleTimestamp else 0L,
                downloadMarker = markers.downloadTimestamp,
                timedOut = false
            )
        }

        val remainingMs = deadline - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            break
        }
        observedVersion =
            awaitSessionStateChange(
                activeSession,
                observedVersion,
                remainingMs.coerceAtMost(120L)
            )
    }

    // 等待动作稳定已超时，由调用入口返回失败，不继续报告操作成功。
    throw IllegalStateException(context.getString(R.string.browser_error_action_timeout))
}

private fun StandardBrowserSessionTools.canCaptureSnapshotForSettlement(
    session: BrowserToolSession
): Boolean = session.pendingDialog == null && session.pendingFileChooserCallback == null

internal fun StandardBrowserSessionTools.latestSnapshot(
    session: BrowserToolSession,
    forceRefresh: Boolean = true
): BrowserSnapshot {
    if (!forceRefresh) {
        session.lastSnapshot?.let { return it }
    }
    val snapshot = captureSnapshotModel(session)
    session.lastSnapshot = snapshot
    return snapshot
}

private fun StandardBrowserSessionTools.actionSettled(
    session: BrowserToolSession,
    markers: BrowserToolActionMarkers,
    policy: BrowserToolActionPolicy
): Boolean {
    val requiredElapsedMs = ((policy.waitForTimeSeconds ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
    if (System.currentTimeMillis() - markers.startedAt < requiredElapsedMs) {
        return false
    }

    val dialogOpened = session.pendingDialog?.timestamp?.let { it >= markers.startedAt } == true
    val fileChooserOpened =
        session.pendingFileChooserCallback != null && session.lastFileChooserRequestAt >= markers.startedAt
    val downloadTriggered = latestBrowserDownloadEventAt() > markers.downloadTimestamp
    if (dialogOpened || fileChooserOpened || downloadTriggered) {
        return true
    }

    if (policy.waitForText != null || policy.waitForTextGone != null) {
        return matchesTextState(session, policy.waitForText, policy.waitForTextGone)
    }

    val activeSwitched = policy.allowActivePageSwitch && session.id != markers.initialSessionId
    val currentUrl = readCurrentUrl(session.webView, session.currentUrl).ifBlank { session.currentUrl }
    val urlChanged = currentUrl != markers.initialUrl

    if (policy.waitForNavigationChange && !activeSwitched && !urlChanged) {
        return false
    }

    val ready = isDocumentReady(session)
    if (policy.waitForDocumentReady || policy.waitForNavigationChange || activeSwitched || urlChanged) {
        return ready
    }

    if (session.isLoading && !ready) {
        return false
    }

    return ready && System.currentTimeMillis() - markers.startedAt >= 150L
}

private fun isStaticRequest(url: String, acceptHeader: String?): Boolean {
    val lowerUrl = url.lowercase(Locale.ROOT)
    val lowerAccept = acceptHeader?.lowercase(Locale.ROOT).orEmpty()
    return lowerAccept.contains("image/") ||
        lowerAccept.contains("font/") ||
        lowerAccept.contains("text/css") ||
        lowerAccept.contains("javascript") ||
        lowerUrl.endsWith(".png") ||
        lowerUrl.endsWith(".jpg") ||
        lowerUrl.endsWith(".jpeg") ||
        lowerUrl.endsWith(".gif") ||
        lowerUrl.endsWith(".svg") ||
        lowerUrl.endsWith(".css") ||
        lowerUrl.endsWith(".js") ||
        lowerUrl.endsWith(".woff") ||
        lowerUrl.endsWith(".woff2") ||
        lowerUrl.endsWith(".ttf")
}

private fun normalizeConsoleLevel(level: String): String =
    when (level.lowercase(Locale.ROOT)) {
        "warn" -> "warning"
        "tip", "log" -> "info"
        else -> level.lowercase(Locale.ROOT)
    }

private fun consoleSeverity(level: String): Int =
    when (normalizeConsoleLevel(level)) {
        "error" -> 0
        "warning" -> 1
        "info" -> 2
        else -> 3
    }

private fun formatDownloadEvent(event: WebDownloadEvent): String =
    buildString {
        appendLine("- Status: ${event.status}")
        appendLine("- Type: ${event.type}")
        appendLine("- File: ${event.fileName}")
        if (!event.url.isNullOrBlank()) {
            appendLine("- URL: ${event.url}")
        }
        if (!event.savedPath.isNullOrBlank()) {
            appendLine("- Saved path: ${event.savedPath}")
        }
        if (!event.error.isNullOrBlank()) {
            append("- Error: ${event.error}")
        }
    }

internal fun StandardBrowserSessionTools.captureSnapshotText(session: BrowserSnapshotSession): String =
    latestSnapshot(session).yaml

internal fun StandardBrowserSessionTools.snapshotNode(
    session: BrowserSnapshotSession,
    ref: String
): BrowserSnapshotNode? {
    val snapshot = session.lastSnapshot ?: latestSnapshot(session)
    return snapshot.nodesByRef[ref]
}

internal fun StandardBrowserSessionTools.captureSnapshotModel(
    session: BrowserSnapshotSession,
    selector: String? = null,
    depth: Int? = null
): BrowserSnapshot {
    val selectorLiteral = selector?.let(JSONObject::quote) ?: "null"
    val depthLiteral = depth?.toString() ?: "null"
    val script =
        """
        (function() {
            const selector = $selectorLiteral;
            const depthLimit = $depthLiteral;
            const normalize = (value) => String(value == null ? "" : value).replace(/\s+/g, " ").trim();
            const escapeQuoted = (value) => String(value == null ? "" : value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
            const yamlScalar = (value) => {
                const text = normalize(value);
                if (!text) {
                    return "";
                }
                if (/^[A-Za-z0-9 _.,!?/+-]+$/.test(text) && !text.includes(": ")) {
                    return text;
                }
                return '"' + escapeQuoted(text) + '"';
            };
            const quotedName = (value) => '"' + escapeQuoted(normalize(value)) + '"';
            const collectWindows = () => {
                const queue = [window];
                const visited = new Set();
                const result = [];
                while (queue.length) {
                    const currentWindow = queue.shift();
                    if (!currentWindow || visited.has(currentWindow)) {
                        continue;
                    }
                    visited.add(currentWindow);
                    let currentDocument;
                    try {
                        currentDocument = currentWindow.document;
                    } catch (_) {
                        continue;
                    }
                    if (!currentDocument) {
                        continue;
                    }
                    result.push(currentWindow);
                    Array.from(currentDocument.querySelectorAll("iframe, frame")).forEach((frameElement) => {
                        try {
                            if (frameElement.contentWindow) {
                                queue.push(frameElement.contentWindow);
                            }
                        } catch (_) {}
                    });
                }
                return result;
            };
            try {
                const isVisible = (element) => {
                    if (!element || element.nodeType !== Node.ELEMENT_NODE) {
                        return false;
                    }
                    const tagName = String(element.tagName || "").toLowerCase();
                    if (tagName === "body" || tagName === "html") {
                        return true;
                    }
                    const currentWindow = element.ownerDocument && element.ownerDocument.defaultView;
                    if (!currentWindow) {
                        return false;
                    }
                    const style = currentWindow.getComputedStyle(element);
                    if (!style || style.visibility === "hidden" || style.display === "none") {
                        return false;
                    }
                    const rect = element.getBoundingClientRect();
                    return rect.width > 0 || rect.height > 0 || element.getClientRects().length > 0;
                };
                const existingRefElements = [];
                collectWindows().forEach((currentWindow) => {
                    try {
                        Array.from(currentWindow.document.querySelectorAll("[aria-ref]")).forEach((element) => {
                            existingRefElements.push(element);
                        });
                    } catch (_) {}
                });
                const existingRefNumbers = existingRefElements
                    .map((element) => {
                        const match = /^e(\d+)$/.exec(String(element.getAttribute("aria-ref") || ""));
                        return match ? parseInt(match[1], 10) : 0;
                    })
                    .filter((value) => Number.isFinite(value) && value > 0);
                let nextRef = existingRefNumbers.length ? Math.max.apply(null, existingRefNumbers) + 1 : 1;
                const ensureRef = (element) => {
                    let ref = normalize(element.getAttribute("aria-ref"));
                    if (!ref) {
                        ref = "e" + nextRef++;
                        element.setAttribute("aria-ref", ref);
                    }
                    return ref;
                };
                const resolveLabelledBy = (element) => {
                    const ids = normalize(element.getAttribute("aria-labelledby")).split(" ").filter(Boolean);
                    if (!ids.length) {
                        return "";
                    }
                    return normalize(
                        ids.map((id) => {
                            const labelElement = element.ownerDocument.getElementById(id);
                            return labelElement ? normalize(labelElement.innerText || labelElement.textContent) : "";
                        }).filter(Boolean).join(" ")
                    );
                };
                const associatedLabel = (element) => {
                    try {
                        if (element.labels && element.labels.length) {
                            return normalize(
                                Array.from(element.labels)
                                    .map((label) => normalize(label.innerText || label.textContent))
                                    .join(" ")
                            );
                        }
                    } catch (_) {}
                    return "";
                };
                const nodes = [];
                const inlineLeafText = (element) => {
                    const tagName = String(element.tagName || "").toLowerCase();
                    if (tagName === "input" || tagName === "textarea" || tagName === "select") {
                        return "";
                    }
                    return normalize(element.innerText || element.textContent);
                };
                const hasAccessibleLabelHint = (element) => {
                    return !!(
                        normalize(element.getAttribute("aria-label")) ||
                        resolveLabelledBy(element) ||
                        normalize(element.getAttribute("title"))
                    );
                };
                const roleFor = (element) => {
                    const explicitRole = normalize(element.getAttribute("role")).toLowerCase();
                    if (explicitRole && explicitRole !== "presentation" && explicitRole !== "none") {
                        return explicitRole;
                    }
                    const tagName = String(element.tagName || "").toLowerCase();
                    switch (tagName) {
                        case "a":
                            return element.hasAttribute("href") ? "link" : null;
                        case "button":
                            return "button";
                        case "textarea":
                            return "textbox";
                        case "select":
                            return element.multiple || Number(element.size || 0) > 1 ? "listbox" : "combobox";
                        case "img":
                            return "img";
                        case "iframe":
                        case "frame":
                            return "iframe";
                        case "ul":
                        case "ol":
                            return "list";
                        case "li":
                            return "listitem";
                        case "main":
                            return "main";
                        case "nav":
                            return "navigation";
                        case "header":
                            return "banner";
                        case "footer":
                            return "contentinfo";
                        case "article":
                            return "article";
                        case "form":
                            return "form";
                        case "dialog":
                            return "dialog";
                        case "details":
                            return "group";
                        case "fieldset":
                            return "group";
                        case "p":
                            return "paragraph";
                        case "section":
                            return hasAccessibleLabelHint(element) ? "region" : null;
                        case "summary":
                            return "button";
                        case "table":
                            return "table";
                        case "tr":
                            return "row";
                        case "td":
                            return "cell";
                        case "th":
                            return element.getAttribute("scope") === "row" ? "rowheader" : "columnheader";
                        case "option":
                            return "option";
                        case "h1":
                        case "h2":
                        case "h3":
                        case "h4":
                        case "h5":
                        case "h6":
                            return "heading";
                        case "input": {
                            const type = normalize(element.getAttribute("type") || "text").toLowerCase();
                            switch (type) {
                                case "button":
                                case "submit":
                                case "reset":
                                    return "button";
                                case "checkbox":
                                    return "checkbox";
                                case "radio":
                                    return "radio";
                                case "range":
                                    return "slider";
                                case "search":
                                case "email":
                                case "number":
                                case "password":
                                case "tel":
                                case "text":
                                case "url":
                                case "":
                                    return "textbox";
                                default:
                                    return element.tabIndex >= 0 ? "generic" : null;
                            }
                        }
                        default:
                            return element.tabIndex >= 0 || element.isContentEditable ? "generic" : null;
                    }
                };
                const nameFor = (element, role) => {
                    const labelledBy = resolveLabelledBy(element);
                    if (labelledBy) {
                        return labelledBy;
                    }
                    const ariaLabel = normalize(element.getAttribute("aria-label"));
                    if (ariaLabel) {
                        return ariaLabel;
                    }
                    const title = normalize(element.getAttribute("title"));
                    const placeholder = normalize(element.getAttribute("placeholder"));
                    const alt = normalize(element.getAttribute("alt"));
                    if (role === "textbox" || role === "checkbox" || role === "radio" || role === "combobox" || role === "listbox" || role === "slider") {
                        return associatedLabel(element) || placeholder || title || alt;
                    }
                    if (role === "button") {
                        return normalize(element.getAttribute("value")) || inlineLeafText(element) || title;
                    }
                    if (role === "img") {
                        return alt || title;
                    }
                    if (role === "iframe") {
                        return normalize(element.getAttribute("name")) || title;
                    }
                    if (role === "option") {
                        return normalize(element.getAttribute("label")) || inlineLeafText(element) || title;
                    }
                    if (role === "heading" || role === "link") {
                        return inlineLeafText(element) || title;
                    }
                    if (role === "dialog" || role === "main" || role === "navigation" || role === "banner" || role === "contentinfo" || role === "article" || role === "form" || role === "region" || role === "list" || role === "table" || role === "group") {
                        return title;
                    }
                    return "";
                };
                const needsRef = (element, role) => {
                    if (!role) {
                        return false;
                    }
                    if (role === "iframe") {
                        return true;
                    }
                    if (role === "generic") {
                        return element.tabIndex >= 0 || element.isContentEditable;
                    }
                    return role === "link" ||
                        role === "button" ||
                        role === "checkbox" ||
                        role === "radio" ||
                        role === "textbox" ||
                        role === "combobox" ||
                        role === "listbox" ||
                        role === "slider";
                };
                const stateTokens = (element, role) => {
                    const tokens = [];
                    const checked = normalize(element.getAttribute("aria-checked"));
                    if (role === "checkbox" || role === "radio") {
                        if (checked) {
                            tokens.push(checked === "true" ? "checked" : "checked=" + checked);
                        } else if (typeof element.checked === "boolean") {
                            tokens.push(element.checked ? "checked" : "checked=false");
                        }
                    }
                    const pressed = normalize(element.getAttribute("aria-pressed"));
                    if (pressed) {
                        tokens.push("pressed=" + pressed);
                    }
                    const selected = normalize(element.getAttribute("aria-selected"));
                    if (selected) {
                        tokens.push(selected === "true" ? "selected" : "selected=" + selected);
                    }
                    const expanded = normalize(element.getAttribute("aria-expanded"));
                    if (expanded) {
                        tokens.push("expanded=" + expanded);
                    }
                    if (element.disabled || normalize(element.getAttribute("aria-disabled")) === "true") {
                        tokens.push("disabled");
                    }
                    const level = role === "heading"
                        ? (normalize(element.getAttribute("aria-level")) || ((/^h([1-6])$/.exec(String(element.tagName || "").toLowerCase()) || [])[1] || ""))
                        : "";
                    if (level) {
                        tokens.push("level=" + level);
                    }
                    return tokens;
                };
                const directTextEntries = (element) => {
                    const entries = [];
                    let buffer = [];
                    const flush = () => {
                        const text = normalize(buffer.join(" "));
                        if (text) {
                            entries.push({ kind: "text", text: text });
                        }
                        buffer = [];
                    };
                    Array.from(element.childNodes).forEach((node) => {
                        if (node.nodeType === Node.TEXT_NODE) {
                            buffer.push(node.textContent || "");
                            return;
                        }
                        if (node.nodeType === Node.ELEMENT_NODE && String(node.tagName || "").toLowerCase() === "br") {
                            buffer.push(" ");
                            return;
                        }
                        flush();
                    });
                    flush();
                    return entries;
                };
                const shouldEmitElement = (element, role, name) => {
                    if (!role) {
                        return false;
                    }
                    if (role === "generic") {
                        return needsRef(element, role);
                    }
                    return (role !== "form" && role !== "region") || !!name;
                };
                const inlineTextRoles = new Set(["paragraph", "listitem", "group", "cell", "rowheader", "columnheader"]);
                const namedRoles = new Set(["heading", "link", "button", "checkbox", "radio", "textbox", "combobox", "listbox", "slider", "img", "dialog", "iframe", "list", "main", "navigation", "banner", "contentinfo", "article", "form", "region", "table", "group", "option"]);
                const textEquivalentNameRoles = new Set(["heading", "link", "button", "option"]);
                const collapseTextEntries = (entries) => {
                    if (!entries.length || entries.some((entry) => entry.kind !== "text")) {
                        return null;
                    }
                    return normalize(entries.map((entry) => entry.text).join(" "));
                };
                const collectEntries = (element, remainingDepth) => {
                    if (!element || element.nodeType !== Node.ELEMENT_NODE || !isVisible(element) || (remainingDepth != null && remainingDepth < 0)) {
                        return [];
                    }
                    const role = roleFor(element);
                    const name = role ? nameFor(element, role) : "";
                    const includeSelf = shouldEmitElement(element, role, name);
                    if (role === "iframe") {
                        const ref = ensureRef(element);
                        const attributes = stateTokens(element, role);
                        attributes.push("ref=" + ref);
                        nodes.push({ ref: ref, role: role, name: name });
                        let children = [];
                        if (remainingDepth == null || remainingDepth > 0) {
                            try {
                                const frameDocument = element.contentDocument;
                                const frameRoot = frameDocument && (frameDocument.body || frameDocument.documentElement);
                                if (frameRoot) {
                                    children = collectEntries(frameRoot, remainingDepth == null ? null : remainingDepth - 1);
                                }
                            } catch (_) {}
                        }
                        return [{ kind: "element", role: role, name: name, attributes: attributes, inlineText: "", children: children }];
                    }
                    const childDepth = includeSelf && remainingDepth != null ? remainingDepth - 1 : remainingDepth;
                    const allowChildTraversal = childDepth == null || childDepth >= 0;
                    const textEntries = directTextEntries(element);
                    const combinedChildren = [];
                    let textIndex = 0;
                    Array.from(element.childNodes).forEach((childNode) => {
                        if (childNode.nodeType === Node.TEXT_NODE || (childNode.nodeType === Node.ELEMENT_NODE && String(childNode.tagName || "").toLowerCase() === "br")) {
                            const nextText = textEntries[textIndex];
                            if (nextText) {
                                combinedChildren.push(nextText);
                                textIndex++;
                            }
                            return;
                        }
                        if (childNode.nodeType === Node.ELEMENT_NODE && allowChildTraversal) {
                            collectEntries(childNode, childDepth).forEach((entry) => {
                                combinedChildren.push(entry);
                            });
                        }
                    });
                    if (!includeSelf) {
                        if (combinedChildren.length) {
                            return combinedChildren;
                        }
                        const text = inlineLeafText(element);
                        return text ? [{ kind: "text", text: text }] : [];
                    }
                    const collapsedText = collapseTextEntries(combinedChildren);
                    const attributes = stateTokens(element, role);
                    if (needsRef(element, role)) {
                        const ref = ensureRef(element);
                        attributes.push("ref=" + ref);
                        nodes.push({ ref: ref, role: role, name: name });
                    }
                    let inlineText = "";
                    let children = combinedChildren;
                    if (role === "textbox") {
                        const value = normalize(element.value);
                        if (value && value !== name) {
                            inlineText = value;
                            children = [];
                        }
                    } else if (inlineTextRoles.has(role)) {
                        const textValue = collapsedText || (!allowChildTraversal ? inlineLeafText(element) : "");
                        if (textValue) {
                            inlineText = textValue;
                            children = [];
                        }
                    } else if (textEquivalentNameRoles.has(role) && name && collapsedText) {
                        children = [];
                    } else if (collapsedText) {
                        inlineText = collapsedText;
                        children = [];
                    }
                    return [{ kind: "element", role: role, name: name, attributes: attributes, inlineText: inlineText, children: children }];
                };
                const renderEntry = (entry, indent, lines) => {
                    const prefix = "  ".repeat(indent) + "- ";
                    if (entry.kind === "text") {
                        lines.push(prefix + "text: " + yamlScalar(entry.text));
                        return;
                    }
                    let line = prefix + entry.role;
                    if (entry.name && namedRoles.has(entry.role)) {
                        line += " " + quotedName(entry.name);
                    }
                    entry.attributes.forEach((token) => {
                        line += " [" + token + "]";
                    });
                    if (entry.inlineText) {
                        line += ": " + yamlScalar(entry.inlineText);
                    } else if (entry.children.length) {
                        line += ":";
                    }
                    lines.push(line);
                    entry.children.forEach((child) => renderEntry(child, indent + 1, lines));
                };
                const root = selector ? document.querySelector(selector) : (document.body || document.documentElement);
                if (!root) {
                    throw new Error(selector ? '"' + selector + '" does not match any elements.' : "No root element available.");
                }
                const entries = collectEntries(root, depthLimit);
                const lines = [];
                entries.forEach((entry) => renderEntry(entry, 0, lines));
                return JSON.stringify({
                    ok: true,
                    yaml: lines.join("\n"),
                    nodes
                });
            } catch (e) {
                return JSON.stringify({
                    ok: false,
                    error: String(e && e.message ? e.message : e)
                });
            }
        })();
        """.trimIndent()
    val json = runJsonScript(session.webView, script, "snapshot_capture_error")
    if (json?.optBoolean("ok", false) != true) {
        throw RuntimeException(json?.optString("error").orEmpty().ifBlank { "snapshot_capture_error" })
    }
    val nodes = mutableMapOf<String, BrowserSnapshotNode>()
    val array = json?.optJSONArray("nodes") ?: JSONArray()
    for (index in 0 until array.length()) {
        val node = array.optJSONObject(index) ?: continue
        val ref = node.optString("ref").trim()
        if (ref.isBlank()) {
            continue
        }
        nodes[ref] =
            BrowserSnapshotNode(
                ref = ref,
                role = node.optString("role", "generic"),
                name = node.optString("name")
            )
    }
    return BrowserSnapshot(
        sessionId = session.id,
        generation = nextSnapshotGeneration(),
        yaml = json.optString("yaml").trim(),
        nodesByRef = nodes
    )
}

internal fun StandardBrowserSessionTools.locatorExpressionForRef(
    session: BrowserSnapshotSession,
    ref: String
): String {
    val node = snapshotNode(session, ref)
    if (node == null) {
        return "page.locator('[aria-ref=${ref}]')"
    }
    val role = node.role.trim()
    val name = node.name.trim()
    return when {
        role.isBlank() || role == "generic" -> "page.locator('[aria-ref=${ref}]')"
        name.isNotBlank() -> "page.getByRole(${quoteJsCode(role)}, { name: ${quoteJsCode(name)} })"
        else -> "page.getByRole(${quoteJsCode(role)})"
    }
}

internal fun formatBrowserFileLink(title: String, path: String): String =
    "- [$title](${path.replace('\\', '/')})"

internal fun browserRefResolverScript(functionName: String = "__operitResolveRef"): String =
    """
    const $functionName = (refValue) => {
        const wantedRef = String(refValue || "");
        const queue = [window];
        const visited = new Set();
        while (queue.length) {
            const currentWindow = queue.shift();
            if (!currentWindow || visited.has(currentWindow)) {
                continue;
            }
            visited.add(currentWindow);
            let currentDocument;
            try {
                currentDocument = currentWindow.document;
            } catch (_) {
                continue;
            }
            if (!currentDocument) {
                continue;
            }
            const target = Array.from(currentDocument.querySelectorAll("[aria-ref]")).find((element) => {
                return String(element.getAttribute("aria-ref") || "") === wantedRef;
            });
            if (target) {
                return { element: target, window: currentWindow };
            }
            Array.from(currentDocument.querySelectorAll("iframe, frame")).forEach((frameElement) => {
                try {
                    if (frameElement.contentWindow && !visited.has(frameElement.contentWindow)) {
                        queue.push(frameElement.contentWindow);
                    }
                } catch (_) {}
            });
        }
        return null;
    };
    """.trimIndent()

package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

private const val EXECUTION_SUPPORT_TAG = "BrowserSessionTools"

internal class BrowserWebDownloadBridge(
    private val tools: StandardBrowserSessionTools,
    private val session: BrowserToolSession
) {
    @JavascriptInterface
    fun downloadBase64(base64Data: String?, fileName: String?, mimeType: String?) {
        tools.handleInlineDownload(
            session = session,
            base64Data = base64Data.orEmpty(),
            fileName = fileName.orEmpty(),
            mimeType = mimeType.orEmpty(),
            type = "inline"
        )
    }

    @JavascriptInterface
    fun log(message: String?) {
        AppLogger.d(EXECUTION_SUPPORT_TAG, "web download bridge: ${message.orEmpty()}")
    }
}

internal class BrowserAsyncBridge {
    @JavascriptInterface
    fun resolve(callId: String?, payload: String?) {
        if (callId.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)?.let { pending ->
            pending.result = payload
            pending.latch.countDown()
        }
    }

    @JavascriptInterface
    fun reject(callId: String?, error: String?) {
        if (callId.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)?.let { pending ->
            pending.error = error ?: "Unknown JavaScript error"
            pending.latch.countDown()
        }
    }
}

internal class BrowserTextSelectionBridge {
    @JavascriptInterface
    fun showActions(anchorX: Double, anchorY: Double) {
        StandardBrowserSessionTools.mainHandler.post {
            StandardBrowserSessionTools.browserHost?.showTextSelectionActionsOverlay(anchorX, anchorY)
        }
    }

    @JavascriptInterface
    fun performHapticFeedback() {
        StandardBrowserSessionTools.mainHandler.post {
            StandardBrowserSessionTools.browserHost?.performTextSelectionHaptic()
        }
    }

    @JavascriptInterface
    fun hideActions() {
        StandardBrowserSessionTools.mainHandler.post {
            StandardBrowserSessionTools.browserHost?.hideTextSelectionActionsOverlay()
        }
    }
}

internal fun StandardBrowserSessionTools.createDownloadListener(
    session: BrowserToolSession
): DownloadListener {
    return DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        try {
            when {
                url.startsWith("blob:") -> {
                    injectBlobDownloaderScript(session, url)
                }

                url.startsWith("data:") -> {
                    handleInlineDownload(
                        session = session,
                        base64Data = url,
                        fileName = "",
                        mimeType = guessMimeTypeFromDataUrl(url).ifBlank { mimetype.orEmpty() },
                        type = "data",
                        sourceUrl = url
                    )
                }

                else -> {
                    handleRegularDownload(
                        session = session,
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimetype
                    )
                }
            }
        } catch (e: Exception) {
            recordDownloadEvent(
                session,
                WebDownloadEvent(
                    status = "failed",
                    type =
                        when {
                            url.startsWith("blob:") -> "blob"
                            url.startsWith("data:") -> "data"
                            else -> "http"
                        },
                    fileName =
                        if (url.startsWith("data:") || url.startsWith("blob:")) {
                            resolveInlineDownloadFileName("", mimetype.orEmpty().ifBlank { guessMimeTypeFromDataUrl(url) })
                        } else {
                            sanitizeFileName(URLUtil.guessFileName(url, contentDisposition, mimetype))
                        },
                    url = url,
                    mimeType = mimetype,
                    error = e.message ?: "download_failed"
                )
            )
            AppLogger.e(EXECUTION_SUPPORT_TAG, "Download failed for session=${session.id}: ${e.message}", e)
            StandardBrowserSessionTools.mainHandler.post {
                Toast.makeText(
                    context,
                    context.getString(R.string.download_failed, e.message ?: url),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

internal fun StandardBrowserSessionTools.injectTextSelectionHelper(webView: WebView) {
    val script =
        """
        (function() {
            if (window.__operitTextSelectionHelperInjected) {
                return;
            }
            window.__operitTextSelectionHelperInjected = true;

            const uiAttribute = "data-operit-selection-ui";
            const state = {
                longPressTimer: null,
                longPressArmed: false,
                startX: 0,
                startY: 0,
                range: null,
                control: null,
                selectedText: "",
                overlay: null,
                highlights: [],
                startHandle: null,
                endHandle: null,
                draggingHandle: "",
                dragFrame: 0,
                dragX: 0,
                dragY: 0,
                dragAdjustX: 0,
                dragAdjustY: 0
            };

            function clearLongPressTimer() {
                if (state.longPressTimer !== null) {
                    clearTimeout(state.longPressTimer);
                    state.longPressTimer = null;
                }
            }

            function isSelectionUiTarget(target) {
                return !!target && !!target.closest && !!target.closest("[" + uiAttribute + "='true']");
            }

            function isEditableTextControl(element) {
                return !!element &&
                    (element.tagName === "TEXTAREA" ||
                        (element.tagName === "INPUT" &&
                            !/^(button|checkbox|color|file|hidden|image|radio|range|reset|submit)$/i.test(element.type || "")));
            }

            function clearNativeSelection() {
                const selection = window.getSelection();
                if (selection) {
                    selection.removeAllRanges();
                }
            }

            function performHapticFeedback() {
                window.OperitTextSelectionBridge.performHapticFeedback();
            }

            function ensureOverlay() {
                if (state.overlay && state.overlay.parentNode) {
                    return state.overlay;
                }
                const overlay = document.createElement("div");
                overlay.setAttribute(uiAttribute, "true");
                overlay.style.position = "fixed";
                overlay.style.left = "0";
                overlay.style.top = "0";
                overlay.style.right = "0";
                overlay.style.bottom = "0";
                overlay.style.pointerEvents = "none";
                overlay.style.zIndex = "2147483647";
                overlay.style.contain = "layout style paint";
                document.documentElement.appendChild(overlay);
                state.overlay = overlay;
                return overlay;
            }

            function rangeFromPoint(x, y) {
                const previousStartPointerEvents = state.startHandle ? state.startHandle.style.pointerEvents : "";
                const previousEndPointerEvents = state.endHandle ? state.endHandle.style.pointerEvents : "";
                if (state.startHandle) {
                    state.startHandle.style.pointerEvents = "none";
                }
                if (state.endHandle) {
                    state.endHandle.style.pointerEvents = "none";
                }
                let pointRange = null;
                if (document.caretRangeFromPoint) {
                    pointRange = document.caretRangeFromPoint(x, y);
                } else if (document.caretPositionFromPoint) {
                    const position = document.caretPositionFromPoint(x, y);
                    if (position) {
                        pointRange = document.createRange();
                        pointRange.setStart(position.offsetNode, position.offset);
                        pointRange.collapse(true);
                    }
                }
                if (state.startHandle) {
                    state.startHandle.style.pointerEvents = previousStartPointerEvents;
                }
                if (state.endHandle) {
                    state.endHandle.style.pointerEvents = previousEndPointerEvents;
                }
                return pointRange;
            }

            function isSelectableCharacter(character) {
                return !!character && !/[\s.,!?;:()[\]{}"']/.test(character);
            }

            function wordRangeFromPointRange(pointRange) {
                const node = pointRange ? pointRange.startContainer : null;
                if (!node || node.nodeType !== Node.TEXT_NODE) {
                    return null;
                }
                const text = String(node.textContent || "");
                if (!text.trim()) {
                    return null;
                }

                let cursor = Math.max(0, Math.min(pointRange.startOffset, text.length));
                if (cursor === text.length && cursor > 0) {
                    cursor -= 1;
                }
                if (!isSelectableCharacter(text.charAt(cursor)) && cursor > 0 && isSelectableCharacter(text.charAt(cursor - 1))) {
                    cursor -= 1;
                }

                let start = cursor;
                let end = cursor + 1;
                while (start > 0 && isSelectableCharacter(text.charAt(start - 1))) {
                    start -= 1;
                }
                while (end < text.length && isSelectableCharacter(text.charAt(end))) {
                    end += 1;
                }

                if (start >= end || !text.slice(start, end).trim()) {
                    return null;
                }

                const range = document.createRange();
                range.setStart(node, start);
                range.setEnd(node, end);
                return range;
            }

            function textNodeFromElement(element, x, y) {
                if (!element) {
                    return null;
                }
                const walker = document.createTreeWalker(
                    element,
                    NodeFilter.SHOW_TEXT,
                    {
                        acceptNode: function(node) {
                            const text = String(node.textContent || "");
                            if (!text.trim()) {
                                return NodeFilter.FILTER_REJECT;
                            }

                            const range = document.createRange();
                            range.selectNodeContents(node);
                            const rects = range.getClientRects();
                            for (let index = 0; index < rects.length; index += 1) {
                                const rect = rects[index];
                                if (
                                    x >= rect.left &&
                                    x <= rect.right &&
                                    y >= rect.top &&
                                    y <= rect.bottom
                                ) {
                                    return NodeFilter.FILTER_ACCEPT;
                                }
                            }
                            return NodeFilter.FILTER_SKIP;
                        }
                    }
                );
                return walker.nextNode();
            }

            function clearVisuals() {
                state.highlights.forEach(function(highlight) {
                    if (highlight.parentNode) {
                        highlight.parentNode.removeChild(highlight);
                    }
                });
                state.highlights = [];
                [state.startHandle, state.endHandle].forEach(function(handle) {
                    if (handle && handle.parentNode) {
                        handle.parentNode.removeChild(handle);
                    }
                });
                state.startHandle = null;
                state.endHandle = null;
            }

            function selectionRects(range) {
                const rects = Array.from(range.getClientRects())
                    .filter(function(rect) {
                        return rect.width > 0 && rect.height > 0;
                    });
                if (rects.length > 0) {
                    return rects;
                }
                const rect = range.getBoundingClientRect();
                if (rect.width > 0 || rect.height > 0) {
                    return [rect];
                }
                return [];
            }

            function unionRects(rects) {
                let left = rects[0].left;
                let top = rects[0].top;
                let right = rects[0].right;
                let bottom = rects[0].bottom;
                rects.forEach(function(rect) {
                    left = Math.min(left, rect.left);
                    top = Math.min(top, rect.top);
                    right = Math.max(right, rect.right);
                    bottom = Math.max(bottom, rect.bottom);
                });
                return { left: left, top: top, right: right, bottom: bottom };
            }

            function setHighlightRect(highlight, rect) {
                highlight.setAttribute(uiAttribute, "true");
                highlight.style.position = "fixed";
                highlight.style.left = rect.left + "px";
                highlight.style.top = rect.top + "px";
                highlight.style.width = rect.width + "px";
                highlight.style.height = rect.height + "px";
                highlight.style.backgroundColor = "rgba(25, 118, 210, 0.32)";
                highlight.style.borderRadius = "2px";
                highlight.style.pointerEvents = "none";
            }

            function renderHighlights(rects) {
                const overlay = ensureOverlay();
                rects.forEach(function(rect, index) {
                    let highlight = state.highlights[index];
                    if (!highlight) {
                        highlight = document.createElement("div");
                        highlight.setAttribute(uiAttribute, "true");
                        overlay.appendChild(highlight);
                        state.highlights[index] = highlight;
                    }
                    setHighlightRect(highlight, rect);
                });
                while (state.highlights.length > rects.length) {
                    const highlight = state.highlights.pop();
                    if (highlight && highlight.parentNode) {
                        highlight.parentNode.removeChild(highlight);
                    }
                }
            }

            function createHandleElement(kind) {
                const overlay = ensureOverlay();
                const handle = document.createElement("div");
                const knob = document.createElement("div");
                const stem = document.createElement("div");
                handle.setAttribute(uiAttribute, "true");
                knob.setAttribute(uiAttribute, "true");
                stem.setAttribute(uiAttribute, "true");
                handle.style.position = "fixed";
                handle.style.width = "32px";
                handle.style.pointerEvents = "auto";
                handle.style.touchAction = "none";
                stem.style.position = "absolute";
                stem.style.left = "15px";
                stem.style.top = "0";
                stem.style.width = "2px";
                stem.style.backgroundColor = "#1976d2";
                stem.style.borderRadius = "2px";
                knob.style.position = "absolute";
                knob.style.left = "8px";
                knob.style.width = "16px";
                knob.style.height = "16px";
                knob.style.borderRadius = "50%";
                knob.style.backgroundColor = "#1976d2";
                knob.style.boxShadow = "0 1px 4px rgba(0,0,0,0.35)";
                handle.appendChild(stem);
                handle.appendChild(knob);
                handle.addEventListener("touchstart", function(event) {
                    const point = eventPoint(event);
                    const rect = handle.__operitBoundaryRect;
                    const boundaryX = handle.__operitBoundaryX;
                    const boundaryY = rect.top + rect.height / 2;
                    state.draggingHandle = kind;
                    state.dragAdjustX = boundaryX - point.x;
                    state.dragAdjustY = boundaryY - point.y;
                    window.OperitTextSelectionBridge.hideActions();
                    performHapticFeedback();
                    event.preventDefault();
                    event.stopPropagation();
                }, { capture: true, passive: false });
                overlay.appendChild(handle);
                return handle;
            }

            function updateHandle(kind, rect) {
                let handle = kind === "start" ? state.startHandle : state.endHandle;
                if (!handle || !handle.parentNode) {
                    handle = createHandleElement(kind);
                    if (kind === "start") {
                        state.startHandle = handle;
                    } else {
                        state.endHandle = handle;
                    }
                }
                const handleHeight = Math.max(30, rect.height + 18);
                handle.style.height = handleHeight + "px";
                handle.style.left = (kind === "start" ? rect.left - 16 : rect.right - 16) + "px";
                handle.style.top = rect.top + "px";
                handle.__operitBoundaryRect = rect;
                handle.__operitBoundaryX = kind === "start" ? rect.left : rect.right;
                const stem = handle.firstChild;
                const knob = handle.lastChild;
                stem.style.height = Math.max(1, rect.height + 7) + "px";
                knob.style.top = Math.max(0, rect.height - 1) + "px";
            }

            function showActionsForRect(rect) {
                const anchorX = (rect.left + rect.right) / 2;
                const anchorY = rect.top;
                window.OperitTextSelectionBridge.showActions(anchorX, anchorY);
            }

            function renderRangeSelection(showActions) {
                if (!state.range) {
                    clearVisuals();
                    clearNativeSelection();
                    return;
                }
                const rects = selectionRects(state.range);
                if (rects.length === 0) {
                    clearVisuals();
                    clearNativeSelection();
                    return;
                }
                renderHighlights(rects);
                updateHandle("start", rects[0]);
                updateHandle("end", rects[rects.length - 1]);
                state.selectedText = String(state.range.toString() || "");
                clearNativeSelection();
                if (showActions) {
                    showActionsForRect(unionRects(rects));
                }
            }

            function renderControlSelection(showActions) {
                const control = state.control;
                if (!control) {
                    clearVisuals();
                    clearNativeSelection();
                    return;
                }
                const rect = control.getBoundingClientRect();
                renderHighlights([rect]);
                updateHandle("start", rect);
                updateHandle("end", rect);
                if (showActions) {
                    showActionsForRect(rect);
                }
            }

            function selectRange(range) {
                const text = String(range.toString() || "");
                if (!text.trim()) {
                    return false;
                }
                clearOperitSelection(false);
                state.range = range.cloneRange();
                state.control = null;
                state.selectedText = text;
                renderRangeSelection(true);
                performHapticFeedback();
                return true;
            }

            function selectControlContents(control) {
                const text = String(control.value || "");
                if (!text.trim()) {
                    return false;
                }
                clearOperitSelection(false);
                control.focus();
                control.setSelectionRange(0, text.length);
                state.control = control;
                state.range = null;
                state.selectedText = text;
                renderControlSelection(true);
                performHapticFeedback();
                return true;
            }

            function selectAtPoint(x, y) {
                const element = document.elementFromPoint(x, y);
                const control =
                    isEditableTextControl(element)
                        ? element
                        : element && element.closest
                            ? element.closest("input, textarea")
                            : null;
                if (
                    isEditableTextControl(control) &&
                    typeof control.setSelectionRange === "function"
                ) {
                    selectControlContents(control);
                    return;
                }

                const pointRange = rangeFromPoint(x, y);
                const wordRange = wordRangeFromPointRange(pointRange);
                if (wordRange && selectRange(wordRange)) {
                    return;
                }

                const textNode = textNodeFromElement(element, x, y);
                if (textNode) {
                    const range = document.createRange();
                    range.selectNodeContents(textNode);
                    selectRange(range);
                }
            }

            function compareBoundaryPoint(nodeA, offsetA, nodeB, offsetB) {
                const pointA = document.createRange();
                const pointB = document.createRange();
                pointA.setStart(nodeA, offsetA);
                pointA.collapse(true);
                pointB.setStart(nodeB, offsetB);
                pointB.collapse(true);
                return pointA.compareBoundaryPoints(Range.START_TO_START, pointB);
            }

            function updateDraggedSelection(x, y) {
                if (!state.range || !state.draggingHandle) {
                    return;
                }
                const pointRange = rangeFromPoint(x, y);
                if (!pointRange) {
                    return;
                }
                const current = state.range;
                const nextRange = document.createRange();
                if (state.draggingHandle === "start") {
                    const order = compareBoundaryPoint(
                        pointRange.startContainer,
                        pointRange.startOffset,
                        current.endContainer,
                        current.endOffset
                    );
                    if (order <= 0) {
                        nextRange.setStart(pointRange.startContainer, pointRange.startOffset);
                        nextRange.setEnd(current.endContainer, current.endOffset);
                    } else {
                        nextRange.setStart(current.endContainer, current.endOffset);
                        nextRange.setEnd(pointRange.startContainer, pointRange.startOffset);
                        state.draggingHandle = "end";
                    }
                } else {
                    const order = compareBoundaryPoint(
                        current.startContainer,
                        current.startOffset,
                        pointRange.startContainer,
                        pointRange.startOffset
                    );
                    if (order <= 0) {
                        nextRange.setStart(current.startContainer, current.startOffset);
                        nextRange.setEnd(pointRange.startContainer, pointRange.startOffset);
                    } else {
                        nextRange.setStart(pointRange.startContainer, pointRange.startOffset);
                        nextRange.setEnd(current.startContainer, current.startOffset);
                        state.draggingHandle = "start";
                    }
                }
                if (!String(nextRange.toString() || "").trim()) {
                    return;
                }
                state.range = nextRange;
                renderRangeSelection(false);
            }

            function scheduleDraggedSelection(x, y) {
                state.dragX = x + state.dragAdjustX;
                state.dragY = y + state.dragAdjustY;
                if (state.dragFrame) {
                    return;
                }
                state.dragFrame = requestAnimationFrame(function() {
                    state.dragFrame = 0;
                    updateDraggedSelection(state.dragX, state.dragY);
                });
            }

            function eventPoint(event) {
                const touch =
                    event.touches && event.touches.length > 0
                        ? event.touches[0]
                        : event.changedTouches && event.changedTouches.length > 0
                            ? event.changedTouches[0]
                            : null;
                if (touch) {
                    return { x: touch.clientX, y: touch.clientY };
                }
                return { x: event.clientX, y: event.clientY };
            }

            function selectedControlText() {
                const control = state.control;
                if (
                    control &&
                    typeof control.selectionStart === "number" &&
                    typeof control.selectionEnd === "number"
                ) {
                    return String(control.value || "").slice(control.selectionStart, control.selectionEnd);
                }
                return state.selectedText;
            }

            function selectAllText() {
                const active = document.activeElement;
                if (
                    isEditableTextControl(active) &&
                    typeof active.setSelectionRange === "function"
                ) {
                    selectControlContents(active);
                    return;
                }
                const body = document.body;
                if (!body) {
                    return;
                }
                const range = document.createRange();
                range.selectNodeContents(body);
                selectRange(range);
            }

            function clearOperitSelection(hideActions) {
                if (state.dragFrame) {
                    cancelAnimationFrame(state.dragFrame);
                    state.dragFrame = 0;
                }
                clearVisuals();
                state.range = null;
                state.control = null;
                state.selectedText = "";
                const active = document.activeElement;
                if (
                    isEditableTextControl(active) &&
                    typeof active.selectionEnd === "number" &&
                    typeof active.setSelectionRange === "function"
                ) {
                    const end = active.selectionEnd;
                    active.setSelectionRange(end, end);
                }
                clearNativeSelection();
                if (hideActions) {
                    window.OperitTextSelectionBridge.hideActions();
                }
            }

            window.__operitTextSelection = {
                getText: function() {
                    return selectedControlText();
                },
                clear: function() {
                    clearOperitSelection(true);
                },
                selectAll: function() {
                    selectAllText();
                }
            };

            document.addEventListener("touchstart", function(event) {
                if (isSelectionUiTarget(event.target)) {
                    return;
                }
                clearLongPressTimer();
                state.longPressArmed = false;
                if (state.selectedText) {
                    clearOperitSelection(true);
                }
                if (!event.touches || event.touches.length !== 1) {
                    return;
                }
                const touch = event.touches[0];
                state.startX = touch.clientX;
                state.startY = touch.clientY;
                state.longPressTimer = setTimeout(function() {
                    state.longPressTimer = null;
                    state.longPressArmed = true;
                    selectAtPoint(state.startX, state.startY);
                }, 560);
            }, { capture: true, passive: false });

            document.addEventListener("touchmove", function(event) {
                if (state.draggingHandle) {
                    const point = eventPoint(event);
                    scheduleDraggedSelection(point.x, point.y);
                    event.preventDefault();
                    event.stopPropagation();
                    return;
                }
                if (!event.touches || event.touches.length !== 1) {
                    clearLongPressTimer();
                    return;
                }
                if (state.longPressArmed) {
                    event.preventDefault();
                    event.stopPropagation();
                    return;
                }
                const touch = event.touches[0];
                const dx = touch.clientX - state.startX;
                const dy = touch.clientY - state.startY;
                if (dx * dx + dy * dy > 144) {
                    clearLongPressTimer();
                }
            }, { capture: true, passive: false });

            document.addEventListener("touchend", function(event) {
                if (state.draggingHandle) {
                    if (state.dragFrame) {
                        cancelAnimationFrame(state.dragFrame);
                        state.dragFrame = 0;
                        updateDraggedSelection(state.dragX, state.dragY);
                    }
                    state.draggingHandle = "";
                    event.preventDefault();
                    event.stopPropagation();
                    if (state.range) {
                        renderRangeSelection(true);
                    }
                    return;
                }
                clearLongPressTimer();
                if (state.longPressArmed || state.selectedText) {
                    event.preventDefault();
                    event.stopPropagation();
                    setTimeout(clearNativeSelection, 0);
                }
                state.longPressArmed = false;
            }, { capture: true, passive: false });

            document.addEventListener("touchcancel", function() {
                clearLongPressTimer();
                state.longPressArmed = false;
                if (state.dragFrame) {
                    cancelAnimationFrame(state.dragFrame);
                    state.dragFrame = 0;
                }
                state.draggingHandle = "";
            }, { capture: true, passive: false });

            document.addEventListener("contextmenu", function(event) {
                if (state.selectedText) {
                    event.preventDefault();
                    event.stopPropagation();
                }
            }, true);

            document.addEventListener("selectionchange", function() {
                if (state.selectedText) {
                    setTimeout(clearNativeSelection, 0);
                }
            }, true);

            document.addEventListener("scroll", function() {
                if (state.range) {
                    renderRangeSelection(true);
                } else if (state.control) {
                    renderControlSelection(true);
                }
            }, true);

            window.addEventListener("resize", function() {
                if (state.range) {
                    renderRangeSelection(true);
                } else if (state.control) {
                    renderControlSelection(true);
                }
            }, true);
        })();
        """.trimIndent()

    runCatching { webView.evaluateJavascript(script, null) }
        .onFailure { AppLogger.w(EXECUTION_SUPPORT_TAG, "Failed to inject text selection helper: ${it.message}") }
}

internal fun StandardBrowserSessionTools.handleRegularDownload(
    session: BrowserToolSession,
    url: String,
    userAgent: String,
    contentDisposition: String?,
    mimeType: String?
) {
    startBrowserManagedDownload(
        session = session,
        url = url,
        userAgent = userAgent,
        contentDisposition = contentDisposition,
        mimeType = mimeType
    )
}

internal fun StandardBrowserSessionTools.injectDownloadHelper(webView: WebView) {
    val script =
        """
        (function() {
            if (window.__operitDownloadHelperInjected) {
                return;
            }
            window.__operitDownloadHelperInjected = true;

            function guessName(anchor) {
                if (!anchor) return "";
                const raw = String(anchor.getAttribute("download") || "").trim();
                if (raw) return raw;
                try {
                    const url = String(anchor.href || "");
                    if (!url) return "";
                    const pathname = new URL(url, location.href).pathname || "";
                    const last = pathname.split("/").filter(Boolean).pop() || "";
                    return decodeURIComponent(last || "");
                } catch (_) {
                    return "";
                }
            }

            function downloadBlob(blobUrl, fileName) {
                fetch(blobUrl)
                    .then((response) => response.blob())
                    .then((blob) => new Promise((resolve, reject) => {
                        const reader = new FileReader();
                        reader.onload = function() {
                            resolve({ data: String(reader.result || ""), type: String(blob.type || "") });
                        };
                        reader.onerror = function() {
                            reject(reader.error || new Error("blob_reader_failed"));
                        };
                        reader.readAsDataURL(blob);
                    }))
                    .then((payload) => {
                        window.OperitWebDownloadBridge.downloadBase64(payload.data, fileName || "", payload.type || "application/octet-stream");
                    })
                    .catch((error) => {
                        window.OperitWebDownloadBridge.log("blob download failed: " + String(error || "unknown"));
                    });
            }

            document.addEventListener("click", function(event) {
                const anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
                if (!anchor) {
                    return;
                }
                const href = String(anchor.href || "");
                if (!href) {
                    return;
                }
                const fileName = guessName(anchor);
                if (href.startsWith("blob:")) {
                    event.preventDefault();
                    downloadBlob(href, fileName);
                } else if (href.startsWith("data:")) {
                    event.preventDefault();
                    const mimeType = href.slice(5).split(";")[0] || "application/octet-stream";
                    window.OperitWebDownloadBridge.downloadBase64(href, fileName || "", mimeType);
                }
            }, true);
        })();
        """.trimIndent()

    runCatching { webView.evaluateJavascript(script, null) }
        .onFailure { AppLogger.w(EXECUTION_SUPPORT_TAG, "Failed to inject download helper: ${it.message}") }
}

internal fun StandardBrowserSessionTools.injectBlobDownloaderScript(
    session: BrowserToolSession,
    blobUrl: String
) {
    val script =
        """
        (function() {
            fetch(${quoteJs(blobUrl)})
                .then((response) => response.blob())
                .then((blob) => new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = function() {
                        resolve({ data: String(reader.result || ""), type: String(blob.type || "") });
                    };
                    reader.onerror = function() {
                        reject(reader.error || new Error("blob_reader_failed"));
                    };
                    reader.readAsDataURL(blob);
                }))
                .then((payload) => {
                    window.OperitWebDownloadBridge.downloadBase64(
                        payload.data,
                        "download_${System.currentTimeMillis()}",
                        payload.type || "application/octet-stream"
                    );
                })
                .catch((error) => {
                    window.OperitWebDownloadBridge.log("blob downloader script failed: " + String(error || "unknown"));
                });
        })();
        """.trimIndent()

    runCatching {
        StandardBrowserSessionTools.mainHandler.post {
            runCatching {
                if (session.webView.isAttachedToWindow) {
                    session.webView.evaluateJavascript(script, null)
                } else {
                    recordDownloadEvent(
                        session,
                        WebDownloadEvent(
                            status = "failed",
                            type = "blob",
                            fileName = "download_${System.currentTimeMillis()}",
                            url = blobUrl,
                            error = "webview_not_attached"
                        )
                    )
                }
            }.onFailure { postError ->
                recordDownloadEvent(
                    session,
                    WebDownloadEvent(
                        status = "failed",
                        type = "blob",
                        fileName = "download_${System.currentTimeMillis()}",
                        url = blobUrl,
                        error = postError.message ?: "blob_download_script_failed"
                    )
                )
            }
        }
    }.onFailure {
        recordDownloadEvent(
            session,
            WebDownloadEvent(
                status = "failed",
                type = "blob",
                fileName = "download_${System.currentTimeMillis()}",
                url = blobUrl,
                error = it.message ?: "blob_download_script_failed"
            )
        )
    }
}

internal fun StandardBrowserSessionTools.handleInlineDownload(
    session: BrowserToolSession,
    base64Data: String,
    fileName: String,
    mimeType: String,
    type: String,
    sourceUrl: String? = null
) {
    runCatching {
        startInlineManagedDownload(
            session = session,
            type = type,
            base64Data = base64Data,
            fileName = fileName,
            mimeType = mimeType,
            sourceUrl = sourceUrl
        )
    }.onFailure { error ->
        val resolvedFileName = resolveInlineDownloadFileName(fileName, mimeType)
        recordDownloadEvent(
            session,
            WebDownloadEvent(
                status = "failed",
                type = type,
                fileName = resolvedFileName,
                mimeType = mimeType,
                error = error.message ?: "inline_download_failed"
            )
        )
        StandardBrowserSessionTools.mainHandler.post {
            Toast.makeText(
                context,
                context.getString(R.string.download_failed, error.message ?: resolvedFileName),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

internal fun StandardBrowserSessionTools.recordDownloadEvent(
    session: BrowserToolSession,
    event: WebDownloadEvent
) {
    session.lastDownloadEvent = event
    session.lastDownloadEventAt = System.currentTimeMillis()
    notifySessionStateChanged(session)
    AppLogger.d(
        EXECUTION_SUPPORT_TAG,
        "web download event session=${session.id}, status=${event.status}, type=${event.type}, file=${event.fileName}, url=${event.url.orEmpty()}"
    )
}

internal fun StandardBrowserSessionTools.guessMimeTypeFromDataUrl(dataUrl: String): String {
    if (!dataUrl.startsWith("data:")) {
        return "application/octet-stream"
    }
    return dataUrl.substringAfter("data:", "")
        .substringBefore(';', "application/octet-stream")
        .ifBlank { "application/octet-stream" }
}

internal fun StandardBrowserSessionTools.resolveInlineDownloadFileName(
    fileName: String,
    mimeType: String
): String {
    val trimmed = fileName.trim()
    if (trimmed.isNotBlank()) {
        return sanitizeFileName(trimmed)
    }
    return sanitizeFileName(
        "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}${determineExtensionFromMimeType(mimeType)}"
    )
}

private fun determineExtensionFromMimeType(mimeType: String): String {
    val lowerMimeType = mimeType.lowercase(Locale.ROOT)
    return when {
        lowerMimeType.startsWith("image/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.startsWith("audio/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.startsWith("video/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.contains("pdf") -> ".pdf"
        lowerMimeType.contains("json") -> ".json"
        lowerMimeType.contains("xml") -> ".xml"
        lowerMimeType.contains("csv") -> ".csv"
        lowerMimeType.contains("zip") -> ".zip"
        lowerMimeType.contains("html") -> ".html"
        lowerMimeType.contains("javascript") -> ".js"
        lowerMimeType.contains("plain") -> ".txt"
        else -> ".bin"
    }
}

internal fun StandardBrowserSessionTools.sanitizeFileName(fileName: String): String {
    val sanitized = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
    return if (sanitized.isBlank()) {
        "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
    } else {
        sanitized
    }
}

internal fun StandardBrowserSessionTools.getSession(sessionId: String?): BrowserToolSession? {
    if (!sessionId.isNullOrBlank()) {
        return sessionById(sessionId)
    }

    return buildPageRegistry().activeSessionId?.let(::sessionById)
}

internal fun StandardBrowserSessionTools.sessionById(sessionId: String): BrowserToolSession? =
    StandardBrowserSessionTools.sessions[sessionId]

internal fun StandardBrowserSessionTools.resolvePreferredSessionId(): String? {
    val registry = buildPageRegistry()
    StandardBrowserSessionTools.activeSessionId = registry.activeSessionId
    return registry.activeSessionId
}

internal fun StandardBrowserSessionTools.addSessionOrder(sessionId: String) {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        if (!StandardBrowserSessionTools.sessionOrder.contains(sessionId)) {
            StandardBrowserSessionTools.sessionOrder.add(sessionId)
        }
    }
}

internal fun StandardBrowserSessionTools.removeSessionOrder(sessionId: String) {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        StandardBrowserSessionTools.sessionOrder.remove(sessionId)
    }
}

internal fun StandardBrowserSessionTools.listSessionIdsInOrder(): List<String> {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        return StandardBrowserSessionTools.sessionOrder.toList()
    }
}

private fun quoteJs(value: String): String = JSONObject.quote(value)

internal fun StandardBrowserSessionTools.quoteJsCode(value: String): String {
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

internal fun StandardBrowserSessionTools.renderJsArrayCode(values: Collection<String>): String =
    values.joinToString(prefix = "[", postfix = "]", separator = ", ") { quoteJsCode(it) }

private fun playwrightLikeInputRuntimeJs(): String =
    """
    const __operitPw = (() => {
        const setValueInputTypes = new Set(["color", "date", "time", "datetime-local", "month", "range", "week"]);
        const typeIntoInputTypes = new Set(["", "email", "number", "password", "search", "tel", "text", "url"]);

        function createError(message) {
            throw new Error(message);
        }

        function retarget(node, behavior) {
            let element = node && node.nodeType === Node.ELEMENT_NODE ? node : node && node.parentElement;
            if (!element) {
                return null;
            }
            if (behavior === "none" || behavior === "no-follow-label") {
                return element;
            }
            if (!element.matches("input, textarea, select") && !element.isContentEditable) {
                if (behavior === "button-link") {
                    element = element.closest("button, [role=button], a, [role=link]") || element;
                } else {
                    element = element.closest("button, [role=button], [role=checkbox], [role=radio]") || element;
                }
            }
            if (behavior === "follow-label") {
                if (!element.matches("a, input, textarea, button, select, [role=link], [role=button], [role=checkbox], [role=radio]") && !element.isContentEditable) {
                    const enclosingLabel = element.closest("label");
                    if (enclosingLabel && enclosingLabel.control) {
                        element = enclosingLabel.control;
                    }
                }
            }
            return element;
        }

        function isVisible(element) {
            const style = element.ownerDocument.defaultView.getComputedStyle(element);
            if (!style || style.visibility === "hidden" || style.visibility === "collapse" || style.display === "none") {
                return false;
            }
            const rect = element.getBoundingClientRect();
            return !!(rect.width || rect.height || element.getClientRects().length);
        }

        const ariaCheckedRoles = new Set(["checkbox", "menuitemcheckbox", "option", "radio", "switch", "menuitemradio", "treeitem"]);
        const ariaDisabledRoles = new Set(["application", "button", "composite", "gridcell", "group", "input", "link", "menuitem", "scrollbar", "separator", "tab", "checkbox", "columnheader", "combobox", "grid", "listbox", "menu", "menubar", "menuitemcheckbox", "menuitemradio", "option", "radio", "radiogroup", "row", "rowheader", "searchbox", "select", "slider", "spinbutton", "switch", "tablist", "textbox", "toolbar", "tree", "treegrid", "treeitem"]);
        const ariaReadonlyRoles = new Set(["checkbox", "combobox", "grid", "gridcell", "listbox", "radiogroup", "slider", "spinbutton", "textbox", "columnheader", "rowheader", "searchbox", "switch", "treegrid"]);

        function elementTagName(element) {
            const tagName = element && element.tagName;
            if (typeof tagName === "string") {
                return tagName.toUpperCase();
            }
            return String(tagName || "").toUpperCase();
        }

        function ariaRole(element) {
            return String((element.getAttribute && element.getAttribute("role")) || "");
        }

        function parentElementOrShadowHost(element) {
            if (!element) {
                return null;
            }
            if (element.parentElement) {
                return element.parentElement;
            }
            const parentNode = element.parentNode;
            return parentNode && parentNode.host ? parentNode.host : null;
        }

        function belongsToDisabledOptGroup(element) {
            return elementTagName(element) === "OPTION" && !!element.closest("OPTGROUP[DISABLED]");
        }

        function belongsToDisabledFieldSet(element) {
            const fieldSetElement = element && element.closest ? element.closest("FIELDSET[DISABLED]") : null;
            if (!fieldSetElement) {
                return false;
            }
            const legendElement = fieldSetElement.querySelector(":scope > LEGEND");
            return !legendElement || !legendElement.contains(element);
        }

        function isNativelyDisabled(element) {
            const isNativeFormControl = ["BUTTON", "INPUT", "SELECT", "TEXTAREA", "OPTION", "OPTGROUP"].includes(elementTagName(element));
            return isNativeFormControl && (element.hasAttribute("disabled") || belongsToDisabledOptGroup(element) || belongsToDisabledFieldSet(element));
        }

        function hasExplicitAriaDisabled(element, isAncestor) {
            if (!element) {
                return false;
            }
            if (isAncestor || ariaDisabledRoles.has(ariaRole(element))) {
                const attribute = String((element.getAttribute("aria-disabled") || "")).toLowerCase();
                if (attribute === "true") {
                    return true;
                }
                if (attribute === "false") {
                    return false;
                }
                return hasExplicitAriaDisabled(parentElementOrShadowHost(element), true);
            }
            return false;
        }

        function getAriaDisabled(element) {
            return isNativelyDisabled(element) || hasExplicitAriaDisabled(element, false);
        }

        function getChecked(element, allowMixed) {
            const tagName = elementTagName(element);
            if (allowMixed && tagName === "INPUT" && element.indeterminate) {
                return "mixed";
            }
            if (tagName === "INPUT" && ["checkbox", "radio"].includes(String(element.type || "").toLowerCase())) {
                return !!element.checked;
            }
            if (ariaCheckedRoles.has(ariaRole(element))) {
                const checked = element.getAttribute("aria-checked");
                if (checked === "true") {
                    return true;
                }
                if (allowMixed && checked === "mixed") {
                    return "mixed";
                }
                return false;
            }
            return "error";
        }

        function getCheckedAllowMixed(element) {
            return getChecked(element, true);
        }

        function getCheckedWithoutMixed(element) {
            return getChecked(element, false);
        }

        function getReadonly(element) {
            const tagName = elementTagName(element);
            if (["INPUT", "TEXTAREA", "SELECT"].includes(tagName)) {
                return element.hasAttribute("readonly");
            }
            if (ariaReadonlyRoles.has(ariaRole(element))) {
                return element.getAttribute("aria-readonly") === "true";
            }
            if (element.isContentEditable) {
                return false;
            }
            return "error";
        }

        function elementState(node, state) {
            const element = retarget(node, state === "visible" || state === "hidden" ? "none" : "follow-label");
            if (!element || !element.isConnected) {
                if (state === "hidden") {
                    return { matches: true, received: "hidden" };
                }
                return { matches: false, received: "error:notconnected" };
            }
            if (state === "visible" || state === "hidden") {
                const visible = isVisible(element);
                return {
                    matches: state === "visible" ? visible : !visible,
                    received: visible ? "visible" : "hidden"
                };
            }
            if (state === "enabled" || state === "disabled") {
                const disabled = getAriaDisabled(element);
                return {
                    matches: state === "disabled" ? disabled : !disabled,
                    received: disabled ? "disabled" : "enabled"
                };
            }
            if (state === "editable") {
                const disabled = getAriaDisabled(element);
                const readonly = getReadonly(element);
                if (readonly === "error") {
                    createError("Element is not an <input>, <textarea>, <select> or [contenteditable] element");
                }
                return {
                    matches: !disabled && !readonly,
                    received: disabled ? "disabled" : readonly ? "readOnly" : "editable"
                };
            }
            if (state === "checked" || state === "unchecked") {
                const checked = getCheckedWithoutMixed(element);
                if (checked === "error") {
                    createError("Not a checkbox or radio button");
                }
                return {
                    matches: state === "checked" ? checked : !checked,
                    received: checked ? "checked" : "unchecked",
                    isRadio: elementTagName(element) === "INPUT" && String(element.type || "").toLowerCase() === "radio"
                };
            }
            if (state === "indeterminate") {
                const checked = getCheckedAllowMixed(element);
                if (checked === "error") {
                    createError("Not a checkbox or radio button");
                }
                return {
                    matches: checked === "mixed",
                    received: checked === true ? "checked" : checked === false ? "unchecked" : "mixed"
                };
            }
            createError("Unsupported element state: " + state);
        }

        function checkElementStates(node, states) {
            for (const state of states) {
                const result = elementState(node, state);
                if (result.received === "error:notconnected") {
                    return "error:notconnected";
                }
                if (!result.matches) {
                    return { missingState: state };
                }
            }
            return null;
        }

        function ensureStatesOrThrow(node, states) {
            const result = checkElementStates(node, states);
            if (result === "error:notconnected") {
                createError("Element is not connected");
            }
            if (result) {
                createError("Element is not " + result.missingState);
            }
        }

        function selectText(node) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            if (element.nodeName.toLowerCase() === "input") {
                element.select();
                element.focus();
                return "done";
            }
            if (element.nodeName.toLowerCase() === "textarea") {
                element.selectionStart = 0;
                element.selectionEnd = element.value.length;
                element.focus();
                return "done";
            }
            element.focus();
            const range = element.ownerDocument.createRange();
            range.selectNodeContents(element);
            const selection = element.ownerDocument.defaultView.getSelection();
            if (selection) {
                selection.removeAllRanges();
                selection.addRange(range);
            }
            return "done";
        }

        function activelyFocused(node) {
            const activeElement = node.getRootNode().activeElement;
            const isFocused = activeElement === node && !!node.ownerDocument && node.ownerDocument.hasFocus();
            return { activeElement, isFocused };
        }

        function focusNode(node, resetSelectionIfNotFocused) {
            if (!node.isConnected) {
                return "error:notconnected";
            }
            if (node.nodeType !== Node.ELEMENT_NODE) {
                createError("Node is not an element");
            }
            const state = activelyFocused(node);
            if (node.isContentEditable && !state.isFocused && state.activeElement && state.activeElement.blur) {
                state.activeElement.blur();
            }
            node.focus();
            node.focus();
            if (resetSelectionIfNotFocused && !state.isFocused && node.nodeName.toLowerCase() === "input") {
                try {
                    node.setSelectionRange(0, 0);
                } catch (_) {
                }
            }
            return "done";
        }

        function setControlValue(element, value) {
            const prototype = element.nodeName.toLowerCase() === "textarea" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const descriptor = Object.getOwnPropertyDescriptor(prototype, "value");
            if (!descriptor || !descriptor.set) {
                createError("Cannot access value setter");
            }
            descriptor.set.call(element, value);
        }

        function dispatchSimpleInput(element) {
            element.dispatchEvent(new Event("input", { bubbles: true, composed: true }));
        }

        function dispatchChange(element) {
            element.dispatchEvent(new Event("change", { bubbles: true }));
        }

        function dispatchBeforeInput(element, inputType, data) {
            return element.dispatchEvent(new InputEvent("beforeinput", {
                bubbles: true,
                cancelable: true,
                composed: true,
                inputType: inputType,
                data: data
            }));
        }

        function dispatchInput(element, inputType, data) {
            element.dispatchEvent(new InputEvent("input", {
                bubbles: true,
                composed: true,
                inputType: inputType,
                data: data
            }));
        }

        function replaceSelectionWithText(element, text) {
            if (element.matches("input, textarea")) {
                const currentValue = String(element.value || "");
                const start = typeof element.selectionStart === "number" ? element.selectionStart : 0;
                const end = typeof element.selectionEnd === "number" ? element.selectionEnd : currentValue.length;
                const nextValue = currentValue.slice(0, start) + text + currentValue.slice(end);
                setControlValue(element, nextValue);
                if (typeof element.setSelectionRange === "function") {
                    const caret = start + text.length;
                    try {
                        element.setSelectionRange(caret, caret);
                    } catch (_) {
                    }
                }
                return;
            }
            const selection = element.ownerDocument.defaultView.getSelection();
            if (!selection) {
                createError("Selection is not available");
            }
            if (!selection.rangeCount || !element.contains(selection.anchorNode)) {
                const resetRange = element.ownerDocument.createRange();
                resetRange.selectNodeContents(element);
                resetRange.collapse(false);
                selection.removeAllRanges();
                selection.addRange(resetRange);
            }
            const range = selection.getRangeAt(0);
            range.deleteContents();
            if (text) {
                const textNode = element.ownerDocument.createTextNode(text);
                range.insertNode(textNode);
                range.setStartAfter(textNode);
            }
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
        }

        function insertTextWithoutKeyboard(element, text) {
            const stringValue = text == null ? "" : String(text);
            if (!dispatchBeforeInput(element, "insertText", stringValue)) {
                return "done";
            }
            replaceSelectionWithText(element, stringValue);
            dispatchInput(element, "insertText", stringValue);
            return "done";
        }

        function deleteSelectionOrCharacter(element, direction) {
            if (element.matches("input, textarea")) {
                const currentValue = String(element.value || "");
                const start = typeof element.selectionStart === "number" ? element.selectionStart : currentValue.length;
                const end = typeof element.selectionEnd === "number" ? element.selectionEnd : start;
                let from = start;
                let to = end;
                if (from === to) {
                    if (direction === "backward" && from > 0) {
                        from -= 1;
                    }
                    if (direction === "forward" && to < currentValue.length) {
                        to += 1;
                    }
                }
                setControlValue(element, currentValue.slice(0, from) + currentValue.slice(to));
                if (typeof element.setSelectionRange === "function") {
                    try {
                        element.setSelectionRange(from, from);
                    } catch (_) {
                    }
                }
                return;
            }
            const selection = element.ownerDocument.defaultView.getSelection();
            if (!selection) {
                createError("Selection is not available");
            }
            if (!selection.rangeCount || !element.contains(selection.anchorNode)) {
                const range = element.ownerDocument.createRange();
                range.selectNodeContents(element);
                range.collapse(false);
                selection.removeAllRanges();
                selection.addRange(range);
            }
            const range = selection.getRangeAt(0);
            if (range.collapsed) {
                const container = range.startContainer;
                const offset = range.startOffset;
                if (container.nodeType === Node.TEXT_NODE) {
                    const length = String(container.textContent || "").length;
                    if (direction === "backward" && offset > 0) {
                        range.setStart(container, offset - 1);
                    } else if (direction === "forward" && offset < length) {
                        range.setEnd(container, offset + 1);
                    } else {
                        return;
                    }
                } else {
                    const childNodes = container.childNodes || [];
                    const targetNode = direction === "backward" ? childNodes[offset - 1] : childNodes[offset];
                    if (!targetNode) {
                        return;
                    }
                    if (targetNode.nodeType === Node.TEXT_NODE) {
                        const textLength = String(targetNode.textContent || "").length;
                        if (direction === "backward") {
                            range.setStart(targetNode, Math.max(0, textLength - 1));
                            range.setEnd(targetNode, textLength);
                        } else {
                            range.setStart(targetNode, 0);
                            range.setEnd(targetNode, Math.min(1, textLength));
                        }
                    } else {
                        range.selectNode(targetNode);
                    }
                }
            }
            range.deleteContents();
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
        }

        function deleteTextWithoutKeyboard(element, direction) {
            const inputType = direction === "forward" ? "deleteContentForward" : "deleteContentBackward";
            if (!dispatchBeforeInput(element, inputType, null)) {
                return "done";
            }
            deleteSelectionOrCharacter(element, direction);
            dispatchInput(element, inputType, null);
            return "done";
        }

        function dispatchKeyEvent(element, type, key) {
            return element.dispatchEvent(new KeyboardEvent(type, {
                key: key,
                bubbles: true,
                cancelable: true,
                composed: true
            }));
        }

        function fillElement(node, value) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible", "enabled", "editable"]);
            if (element.nodeName.toLowerCase() === "input") {
                const input = element;
                const type = String(input.type || "").toLowerCase();
                if (!typeIntoInputTypes.has(type) && !setValueInputTypes.has(type)) {
                    createError('Input of type "' + type + '" cannot be filled');
                }
                let normalizedValue = String(value);
                if (type === "number") {
                    normalizedValue = normalizedValue.trim();
                    if (isNaN(Number(normalizedValue))) {
                        createError("Cannot type text into input[type=number]");
                    }
                }
                if (type === "color") {
                    normalizedValue = normalizedValue.toLowerCase();
                }
                if (setValueInputTypes.has(type)) {
                    normalizedValue = normalizedValue.trim();
                    input.focus();
                    setControlValue(input, normalizedValue);
                    if (input.value !== normalizedValue) {
                        createError("Malformed value");
                    }
                    dispatchSimpleInput(input);
                    dispatchChange(input);
                    return "done";
                }
                selectText(input);
                return "needsinput";
            }
            if (element.nodeName.toLowerCase() === "textarea") {
                selectText(element);
                return "needsinput";
            }
            if (!element.isContentEditable) {
                createError("Element is not an <input>, <textarea> or [contenteditable] element");
            }
            selectText(element);
            return "needsinput";
        }

        function completeFill(node, value) {
            const stringValue = value == null ? "" : String(value);
            const result = fillElement(node, stringValue);
            if (result === "needsinput") {
                return stringValue ? insertTextWithoutKeyboard(retarget(node, "follow-label"), stringValue) : deleteTextWithoutKeyboard(retarget(node, "follow-label"), "forward");
            }
            return result;
        }

        async function typeCharacter(element, ch) {
            const isPrintableAscii = /^[\u0020-\u007e]$/.test(ch);
            if (isPrintableAscii) {
                return pressKey(element, ch);
            }
            return insertTextWithoutKeyboard(element, ch);
        }

        async function typeElement(node, text, delayMs) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            const focusResult = focusNode(element, true);
            if (focusResult !== "done") {
                return focusResult;
            }
            for (const rawCh of String(text)) {
                if (rawCh === "\r") {
                    continue;
                }
                if (rawCh === "\n") {
                    const enterResult = pressEnter(element);
                    if (enterResult !== "done") {
                        return enterResult;
                    }
                } else {
                    const typeResult = await typeCharacter(element, rawCh);
                    if (typeResult !== "done") {
                        return typeResult;
                    }
                }
                if (delayMs > 0) {
                    await new Promise((resolve) => setTimeout(resolve, delayMs));
                }
            }
            return "done";
        }

        function pressEnter(node) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            const keydownAccepted = dispatchKeyEvent(element, "keydown", "Enter");
            if (keydownAccepted) {
                dispatchKeyEvent(element, "keypress", "Enter");
                if (element.nodeName.toLowerCase() === "textarea" || element.isContentEditable) {
                    if (dispatchBeforeInput(element, "insertLineBreak", "\n")) {
                        replaceSelectionWithText(element, "\n");
                        dispatchInput(element, "insertLineBreak", "\n");
                    }
                } else {
                    const form = element.form || (element.closest && element.closest("form"));
                    if (form && typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                    }
                }
            }
            dispatchKeyEvent(element, "keyup", "Enter");
            return "done";
        }

        function pressKey(node, key) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            const keyValue = String(key);
            const editable = element.matches("input, textarea") || element.isContentEditable;
            if (keyValue === "Enter") {
                return pressEnter(element);
            }
            const keydownAccepted = dispatchKeyEvent(element, "keydown", keyValue);
            if (keydownAccepted) {
                if (keyValue.length === 1) {
                    const keypressAccepted = dispatchKeyEvent(element, "keypress", keyValue);
                    if (keypressAccepted && editable) {
                        insertTextWithoutKeyboard(element, keyValue);
                    }
                } else if (keyValue === "Backspace" && editable) {
                    deleteTextWithoutKeyboard(element, "backward");
                } else if (keyValue === "Delete" && editable) {
                    deleteTextWithoutKeyboard(element, "forward");
                }
            }
            dispatchKeyEvent(element, "keyup", keyValue);
            return "done";
        }

        function setChecked(node, desiredState) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible", "enabled"]);
            const currentState = elementState(element, "checked");
            if (currentState.received === "error:notconnected") {
                return "error:notconnected";
            }
            if (currentState.matches === desiredState) {
                return "done";
            }
            if (!desiredState && currentState.isRadio) {
                createError("Cannot uncheck radio button. Radio buttons can only be unchecked by selecting another radio button in the same group.");
            }
            if (typeof element.click !== "function") {
                createError("Element is not clickable");
            }
            element.click();
            const finalState = elementState(element, "checked");
            if (finalState.received === "error:notconnected") {
                return "error:notconnected";
            }
            if (finalState.matches !== desiredState) {
                createError("Clicking the checkbox did not change its state");
            }
            return "done";
        }

        function selectOptions(node, optionsToSelect) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible", "enabled"]);
            if (element.nodeName.toLowerCase() !== "select") {
                createError("Element is not a <select> element");
            }
            const select = element;
            const options = Array.from(select.options || []);
            const selectedOptions = [];
            let remaining = optionsToSelect.slice();
            for (let index = 0; index < options.length; index += 1) {
                const option = options[index];
                const filter = (optionToSelect) => {
                    if (optionToSelect instanceof Node) {
                        return option === optionToSelect;
                    }
                    let matches = true;
                    if (optionToSelect.valueOrLabel !== undefined) {
                        matches = matches && (String(optionToSelect.valueOrLabel) === String(option.value) || String(optionToSelect.valueOrLabel) === String(option.label));
                    }
                    if (optionToSelect.value !== undefined) {
                        matches = matches && String(optionToSelect.value) === String(option.value);
                    }
                    if (optionToSelect.label !== undefined) {
                        matches = matches && String(optionToSelect.label) === String(option.label);
                    }
                    if (optionToSelect.index !== undefined) {
                        matches = matches && Number(optionToSelect.index) === index;
                    }
                    return matches;
                };
                if (!remaining.some(filter)) {
                    continue;
                }
                if (!elementState(option, "enabled").matches) {
                    return "error:optionnotenabled";
                }
                selectedOptions.push(option);
                if (select.multiple) {
                    remaining = remaining.filter((candidate) => !filter(candidate));
                } else {
                    remaining = [];
                    break;
                }
            }
            if (remaining.length) {
                return "error:optionsnotfound";
            }
            select.value = void 0;
            selectedOptions.forEach((option) => {
                option.selected = true;
            });
            dispatchSimpleInput(select);
            dispatchChange(select);
            return selectedOptions.map((option) => String(option.value));
        }

        return {
            fillElement: fillElement,
            completeFill: completeFill,
            typeElement: typeElement,
            pressKey: pressKey,
            pressEnter: pressEnter,
            setChecked: setChecked,
            selectOptions: selectOptions
        };
    })();
    """.trimIndent()

internal fun StandardBrowserSessionTools.ensureOverlayPermission(toolName: String): ToolResult? {
    val appContext = context.applicationContext
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
        error(toolName, "Overlay permission is required for browser tools.")
    } else {
        null
    }
}

internal fun StandardBrowserSessionTools.buildPageRegistry(): BrowserPageRegistry {
    val orderedIds = orderedSessionIdsFromState()
    val activeId =
        StandardBrowserSessionTools.activeSessionId?.takeIf { sessionById(it) != null }
            ?: orderedIds.firstOrNull()
    return BrowserPageRegistry(
        orderedSessionIds = orderedIds,
        activeSessionId = activeId,
        overlayExpanded = StandardBrowserSessionTools.browserHost?.isExpanded() == true,
        snapshots = orderedIds.associateWith { id -> sessionById(id)?.lastSnapshot }
    )
}

private fun StandardBrowserSessionTools.orderedSessionIdsFromState(): List<String> {
    val orderedIds = LinkedHashSet<String>()
    listSessionIdsInOrder()
        .filter { sessionById(it) != null }
        .forEach(orderedIds::add)
    StandardBrowserSessionTools.sessions.values
        .sortedWith(compareBy<BrowserToolSession> { it.createdAt }.thenBy { it.id })
        .mapTo(orderedIds) { it.id }
    return orderedIds.toList()
}

internal fun StandardBrowserSessionTools.orderedSessionIds(): List<String> =
    buildPageRegistry().orderedSessionIds

internal fun StandardBrowserSessionTools.currentTabIndex(sessionId: String): Int =
    buildPageRegistry().orderedSessionIds.indexOf(sessionId)

internal fun StandardBrowserSessionTools.sessionIdAtIndex(index: Int): String? =
    buildPageRegistry().orderedSessionIds.getOrNull(index)

internal fun StandardBrowserSessionTools.pageError(
    toolName: String,
    session: BrowserToolSession?,
    message: String
): ToolResult {
    val rendered =
        if (session != null) {
            buildBrowserResponse(
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                snapshot = captureSnapshotText(session),
                modalState = renderModalState(session),
                error = message
            )
        } else {
            buildBrowserResponse(error = message)
        }
    return ToolResult(
        toolName = toolName,
        success = false,
        result = StringResultData(rendered),
        error = rendered
    )
}

internal fun StandardBrowserSessionTools.buildSettledBrowserResponse(
    settlement: StandardBrowserSessionTools.BrowserActionSettlement,
    code: String? = null,
    snapshot: String? = settlement.snapshot?.yaml,
    consoleMessages: String? = renderNewConsoleMessages(settlement.session, settlement.consoleMarker),
    modalState: String? = renderModalState(settlement.session),
    downloads: String? = renderDownloads(settlement.session, settlement.downloadMarker),
    result: String? = null,
    error: String? = null
): String =
    buildBrowserResponse(
        code = code,
        openTabs = renderOpenTabs(settlement.registry),
        pageState = renderPageState(settlement.session),
        snapshot = snapshot,
        consoleMessages = consoleMessages,
        modalState = modalState,
        downloads = downloads,
        result = result,
        error = error
    )

internal fun StandardBrowserSessionTools.renderOpenTabs(
    registry: BrowserPageRegistry = buildPageRegistry()
): String {
    val activeId = registry.activeSessionId
    val ordered = registry.orderedSessionIds.mapNotNull(::sessionById)
    if (ordered.isEmpty()) {
        return "No open tabs."
    }
    return ordered.mapIndexed { index, session ->
        val active = if (session.id == activeId) " [active]" else ""
        val title = sessionDisplayTitle(session).ifBlank { "about:blank" }
        "- [$index] $title$active\n  ${session.currentUrl.ifBlank { "about:blank" }}"
    }.joinToString("\n")
}

internal fun StandardBrowserSessionTools.renderPageState(session: BrowserToolSession): String {
    val viewport =
        StandardBrowserSessionTools.browserHost?.currentViewportSize()
            ?: Pair(
                (session.viewportWidthPx ?: session.webView.width).coerceAtLeast(0),
                (session.viewportHeightPx ?: session.webView.height).coerceAtLeast(0)
            )
    return buildString {
        appendLine("- Title: ${session.pageTitle.ifBlank { "about:blank" }}")
        appendLine("- URL: ${session.currentUrl.ifBlank { "about:blank" }}")
        appendLine("- Loading: ${if (session.isLoading) "yes" else "no"}")
        appendLine("- Can go back: ${if (session.canGoBack) "yes" else "no"}")
        appendLine("- Can go forward: ${if (session.canGoForward) "yes" else "no"}")
        append("- Viewport: ${viewport.first}x${viewport.second}")
    }
}

internal fun StandardBrowserSessionTools.locatorExpressionForElement(
    session: BrowserToolSession,
    ref: String,
    selectorSuffix: String
): String = locatorExpressionForRef(session, ref) + selectorSuffix

internal fun StandardBrowserSessionTools.evaluateJavascriptAsync(
    webView: WebView,
    expression: String,
    timeoutMs: Long
): String {
    val callId = UUID.randomUUID().toString()
    val pending = PendingAsyncJsCall()
    StandardBrowserSessionTools.pendingAsyncJsCalls[callId] = pending
    StandardBrowserSessionTools.mainHandler.post {
        try {
            if (!webView.isAttachedToWindow) {
                StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
                pending.error = "WebView is not attached"
                pending.latch.countDown()
                return@post
            }
            val wrapped =
                """
                (function() {
                    try {
                        const operitExpression = ${quoteJs(expression)};
                        const operitValue = (0, eval)(operitExpression);
                        Promise.resolve(operitValue)
                            .then(function(value) {
                                let payload;
                                try {
                                    payload = JSON.stringify({ ok: true, value: value });
                                } catch (_) {
                                    payload = JSON.stringify({ ok: true, value: String(value) });
                                }
                                window.OperitAsyncBridge.resolve(${quoteJs(callId)}, payload);
                            })
                            .catch(function(error) {
                                window.OperitAsyncBridge.reject(
                                    ${quoteJs(callId)},
                                    String(error && (error.stack || error.message || error) || "Async execution failed")
                                );
                            });
                    } catch (error) {
                        window.OperitAsyncBridge.reject(
                            ${quoteJs(callId)},
                            String(error && (error.stack || error.message || error) || "Async execution failed")
                        );
                    }
                    return true;
                })();
                """.trimIndent()
            webView.evaluateJavascript(wrapped, null)
        } catch (e: Exception) {
            StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
            pending.error = e.message ?: "Async JavaScript wrapper error"
            pending.latch.countDown()
        }
    }
    if (!pending.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
        throw RuntimeException("JavaScript execution timeout (${timeoutMs}ms)")
    }
    pending.error?.let { throw RuntimeException(it) }
    return pending.result ?: "{\"ok\":true,\"value\":null}"
}

internal fun extractAsyncJsValue(payload: String): String {
    val json = JSONObject(payload)
    val value = json.opt("value")
    return when (value) {
        null, JSONObject.NULL -> ""
        is String -> value
        else -> value.toString()
    }
}

internal fun StandardBrowserSessionTools.evaluatePageFunction(
    webView: WebView,
    functionSource: String,
    ref: String?,
    timeoutMs: Long
): String {
    val expression =
        """
        (async function() {
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref.orEmpty())};
            const target =
                refValue
                    ? (__operitResolveRef(refValue) || {}).element
                    : undefined;
            if (refValue && !target) {
                throw new Error("ref_not_found");
            }
            const fn = (${functionSource});
            if (typeof fn !== "function") {
                throw new Error("function must evaluate to a callable value");
            }
            return refValue ? await fn(target) : await fn();
        })()
        """.trimIndent()
    return extractAsyncJsValue(evaluateJavascriptAsync(webView, expression, timeoutMs))
}

internal fun StandardBrowserSessionTools.parseFormFields(rawFields: String): List<JSONObject>? {
    return try {
        val array = JSONArray(rawFields)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index))
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun StandardBrowserSessionTools.fillFormFields(
    webView: WebView,
    rawFields: String
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            ${browserRefResolverScript()}
            const fields = $rawFields;
            const results = [];
            const findByRef = (refValue) => {
                const resolved = __operitResolveRef(String(refValue || ''));
                return resolved ? resolved.element : null;
            };
            fields.forEach((field, index) => {
                const target =
                    field.ref ? findByRef(field.ref) :
                    field.selector ? document.querySelector(String(field.selector)) :
                    null;
                if (!target) {
                    throw new Error("field_not_found:" + (field.name || index));
                }
                const type = String(field.type || target.type || target.tagName || "").toLowerCase();
                const value = String(field.value ?? "");
                let result = "done";
                if (type === "textbox" || type === "slider") {
                    result = __operitPw.completeFill(target, value);
                } else if (type === "checkbox" || type === "radio") {
                    result = __operitPw.setChecked(target, value.toLowerCase() === "true");
                } else if (type === "combobox" || String(target.tagName || "").toLowerCase() === "select") {
                    result = __operitPw.selectOptions(target, [{ valueOrLabel: value }]);
                } else {
                    result = __operitPw.completeFill(target, value);
                }
                if (result !== "done" && !Array.isArray(result)) {
                    throw new Error(String(result));
                }
                results.push(String(field.name || ("field_" + (index + 1))) + " => " + type);
            });
            return results.join("\n");
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.pressKeyOnPage(
    webView: WebView,
    key: String
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            const keyValue = ${quoteJs(key)};
            const target = document.activeElement || document.body || document.documentElement;
            if (!target) {
                throw new Error("No focus target available.");
            }
            const result = __operitPw.pressKey(target, keyValue);
            if (result !== "done") {
                throw new Error(String(result));
            }
            return "Pressed " + keyValue;
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.selectOptionsByRef(
    webView: WebView,
    ref: String,
    values: List<String>
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref)};
            const resolved = __operitResolveRef(refValue);
            const target = resolved ? resolved.element : null;
            if (!target) {
                throw new Error("ref_not_found");
            }
            const values = ${JSONArray(values).toString()}.map((value) => ({ valueOrLabel: value }));
            const result = __operitPw.selectOptions(target, values);
            if (!Array.isArray(result)) {
                throw new Error(String(result));
            }
            return "Selected " + result.join(", ");
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.typeIntoElementByRef(
    webView: WebView,
    ref: String,
    text: String,
    submit: Boolean,
    slowly: Boolean
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref)};
            const resolved = __operitResolveRef(refValue);
            const target = resolved ? resolved.element : null;
            if (!target) {
                throw new Error("ref_not_found");
            }
            const textValue = ${quoteJs(text)};
            const submitValue = ${if (submit) "true" else "false"};
            const slowValue = ${if (slowly) "true" else "false"};
            if (slowValue) {
                const typeResult = await __operitPw.typeElement(target, textValue, 35);
                if (typeResult !== "done") {
                    throw new Error(String(typeResult));
                }
            } else {
                const fillResult = __operitPw.completeFill(target, textValue);
                if (fillResult !== "done") {
                    throw new Error(String(fillResult));
                }
            }
            if (submitValue) {
                const submitResult = __operitPw.pressEnter(target);
                if (submitResult !== "done") {
                    throw new Error(String(submitResult));
                }
            }
            return "Typed " + textValue.length + " character(s).";
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.writeBrowserTextOutput(
    filename: String,
    content: String,
    defaultPrefix: String,
    extension: String
): String {
    val resolved = resolveBrowserOutputFile(filename, defaultPrefix, extension)
    resolved.parentFile?.mkdirs()
    resolved.writeText(content)
    return resolved.absolutePath
}

internal fun StandardBrowserSessionTools.resolveBrowserOutputFile(
    filename: String?,
    defaultPrefix: String,
    extension: String
): File {
    val baseDir = File(context.cacheDir, "browser-output").apply { mkdirs() }
    if (!filename.isNullOrBlank()) {
        val candidate = File(filename)
        return if (candidate.isAbsolute) {
            candidate
        } else {
            File(baseDir, filename)
        }
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(baseDir, "${defaultPrefix}_$timestamp.$extension")
}

internal fun StandardBrowserSessionTools.takeScreenshot(
    session: BrowserToolSession,
    filename: String?,
    type: String,
    fullPage: Boolean,
    ref: String?
): String {
    return runOnMainSync {
        ensureSessionAttachedOnMain(session.id)
        val resolvedType = if (type == "jpg") "jpeg" else type
        val output = resolveBrowserOutputFile(filename, "page", if (resolvedType == "png") "png" else "jpg")
        output.parentFile?.mkdirs()
        val bitmap =
            when {
                fullPage ->
                    captureFullPageBitmap(
                        session.webView,
                        session.viewportWidthPx,
                        session.viewportHeightPx
                    )
                ref != null ->
                    captureElementBitmap(
                        session.webView,
                        ref,
                        session.viewportWidthPx,
                        session.viewportHeightPx
                    )
                else ->
                    captureViewportBitmap(
                        session.webView,
                        session.viewportWidthPx,
                        session.viewportHeightPx
                    )
            }
        FileOutputStream(output).use { stream ->
            bitmap.compress(
                if (resolvedType == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                92,
                stream
            )
        }
        bitmap.recycle()
        output.absolutePath
    }
}

internal fun StandardBrowserSessionTools.captureViewportBitmap(
    webView: WebView,
    viewportWidthCssPx: Int? = null,
    viewportHeightCssPx: Int? = null
): Bitmap {
    val sourceWidth = webView.width.coerceAtLeast(1)
    val sourceHeight = webView.height.coerceAtLeast(1)
    val width = viewportWidthCssPx ?: sourceWidth
    val height = viewportHeightCssPx ?: sourceHeight
    val bitmap = createBrowserScreenshotBitmap(width, height)
    val canvas = Canvas(bitmap)
    if (width != sourceWidth || height != sourceHeight) {
        canvas.scale(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
    }
    webView.draw(canvas)
    return bitmap
}

internal fun StandardBrowserSessionTools.captureFullPageBitmap(
    webView: WebView,
    viewportWidthCssPx: Int? = null,
    viewportHeightCssPx: Int? = null
): Bitmap {
    val pageSize = resolveFullPageCssSize(webView)
    val originalWidth = webView.width.coerceAtLeast(1)
    val originalHeight = webView.height.coerceAtLeast(1)
    val originalScrollX = webView.scrollX
    val originalScrollY = webView.scrollY
    val hasCssViewport = viewportWidthCssPx != null && viewportHeightCssPx != null
    val outputWidth =
        if (hasCssViewport) {
            pageSize.contentWidthCssPx
        } else {
            (pageSize.contentWidthCssPx * (webView.scale.takeIf { it > 0f } ?: 1f))
                .toInt()
                .coerceAtLeast(originalWidth)
        }
    val outputHeight =
        if (hasCssViewport) {
            pageSize.contentHeightCssPx
        } else {
            (pageSize.contentHeightCssPx * (webView.scale.takeIf { it > 0f } ?: 1f))
                .toInt()
                .coerceAtLeast(originalHeight)
        }
    val layoutScaleX = originalWidth.toFloat() / pageSize.viewportWidthCssPx.coerceAtLeast(1)
    val layoutScaleY = originalHeight.toFloat() / pageSize.viewportHeightCssPx.coerceAtLeast(1)
    val layoutWidth =
        kotlin.math.ceil(pageSize.contentWidthCssPx * layoutScaleX)
            .toLong()
            .also { require(it in 1..Int.MAX_VALUE.toLong()) { "Full-page layout width is too large" } }
            .toInt()
    val layoutHeight =
        kotlin.math.ceil(pageSize.contentHeightCssPx * layoutScaleY)
            .toLong()
            .also { require(it in 1..Int.MAX_VALUE.toLong()) { "Full-page layout height is too large" } }
            .toInt()
    val layoutPixelCount = layoutWidth.toLong() * layoutHeight.toLong()
    require(
        layoutWidth <= StandardBrowserSessionTools.MAX_SCREENSHOT_DIMENSION_PX &&
            layoutHeight <= StandardBrowserSessionTools.MAX_SCREENSHOT_DIMENSION_PX &&
            layoutPixelCount <= StandardBrowserSessionTools.MAX_VIEWPORT_LAYOUT_PIXEL_COUNT
    ) {
        "Full-page layout dimensions ${layoutWidth}x$layoutHeight exceed the safe rendering budget"
    }
    val bitmap = createBrowserScreenshotBitmap(outputWidth, outputHeight)
    val canvas = Canvas(bitmap)
    canvas.scale(outputWidth.toFloat() / layoutWidth, outputHeight.toFloat() / layoutHeight)
    try {
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(layoutWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(layoutHeight, View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, layoutWidth, layoutHeight)
        webView.scrollTo(0, 0)
        webView.draw(canvas)
        return bitmap
    } catch (failure: Throwable) {
        bitmap.recycle()
        throw failure
    } finally {
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, originalWidth, originalHeight)
        webView.scrollTo(originalScrollX, originalScrollY)
    }
}

internal data class BrowserFullPageCssSize(
    val contentWidthCssPx: Int,
    val contentHeightCssPx: Int,
    val viewportWidthCssPx: Int,
    val viewportHeightCssPx: Int
)

internal fun StandardBrowserSessionTools.resolveFullPageCssSize(
    webView: WebView
): BrowserFullPageCssSize {
    val pageSize =
        runJsonScript(
            webView,
            """
            (function() {
                const doc = document.documentElement;
                const body = document.body;
                const width = Math.max(
                    window.innerWidth || 0,
                    doc ? (doc.scrollWidth || 0) : 0,
                    doc ? (doc.offsetWidth || 0) : 0,
                    body ? (body.scrollWidth || 0) : 0,
                    body ? (body.offsetWidth || 0) : 0
                );
                const height = Math.max(
                    window.innerHeight || 0,
                    doc ? (doc.scrollHeight || 0) : 0,
                    doc ? (doc.offsetHeight || 0) : 0,
                    body ? (body.scrollHeight || 0) : 0,
                    body ? (body.offsetHeight || 0) : 0
                );
                return JSON.stringify({
                    ok: true,
                    width,
                    height,
                    viewportWidth: window.innerWidth || 0,
                    viewportHeight: window.innerHeight || 0
                });
            })();
            """.trimIndent(),
            "page_size_error"
        )

    val fallbackScale = webView.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val fallbackViewportWidth = (webView.width / fallbackScale).toInt().coerceAtLeast(1)
    val fallbackViewportHeight = (webView.height / fallbackScale).toInt().coerceAtLeast(1)
    val viewportWidth =
        (pageSize?.optDouble("viewportWidth", 0.0) ?: 0.0)
            .toInt()
            .takeIf { it > 0 }
            ?: fallbackViewportWidth
    val viewportHeight =
        (pageSize?.optDouble("viewportHeight", 0.0) ?: 0.0)
            .toInt()
            .takeIf { it > 0 }
            ?: fallbackViewportHeight
    return BrowserFullPageCssSize(
        contentWidthCssPx =
            (pageSize?.optDouble("width", 0.0) ?: 0.0).toInt().coerceAtLeast(viewportWidth),
        contentHeightCssPx =
            (pageSize?.optDouble("height", 0.0) ?: 0.0).toInt().coerceAtLeast(viewportHeight),
        viewportWidthCssPx = viewportWidth,
        viewportHeightCssPx = viewportHeight
    )
}

internal fun StandardBrowserSessionTools.createBrowserScreenshotBitmap(width: Int, height: Int): Bitmap {
    require(width > 0 && height > 0) { "Screenshot dimensions must be positive" }
    require(
        width <= StandardBrowserSessionTools.MAX_SCREENSHOT_DIMENSION_PX &&
            height <= StandardBrowserSessionTools.MAX_SCREENSHOT_DIMENSION_PX
    ) {
        "Screenshot dimensions ${width}x$height exceed the safe side-length limit"
    }
    val pixelCount = width.toLong() * height.toLong()
    require(pixelCount <= StandardBrowserSessionTools.MAX_SCREENSHOT_PIXEL_COUNT) {
        "Screenshot dimensions ${width}x$height exceed the safe pixel budget"
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}

internal fun StandardBrowserSessionTools.captureElementBitmap(
    webView: WebView,
    ref: String,
    viewportWidthCssPx: Int? = null,
    viewportHeightCssPx: Int? = null
): Bitmap {
    val rect = resolveElementRect(webView, ref) ?: throw RuntimeException("ref_not_found")
    val bitmap = captureViewportBitmap(webView, viewportWidthCssPx, viewportHeightCssPx)
    val left = rect.left.coerceIn(0, bitmap.width - 1)
    val top = rect.top.coerceIn(0, bitmap.height - 1)
    val width = rect.width().coerceAtLeast(1).coerceAtMost(bitmap.width - left)
    val height = rect.height().coerceAtLeast(1).coerceAtMost(bitmap.height - top)
    val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
    if (cropped !== bitmap) {
        bitmap.recycle()
    }
    return cropped
}

internal fun StandardBrowserSessionTools.resolveElementRect(
    webView: WebView,
    ref: String
): Rect? {
    val script =
        """
        (function() {
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref)};
            const resolved = __operitResolveRef(refValue);
            const target = resolved ? resolved.element : null;
            const targetWindow = resolved ? (resolved.window || window) : window;
            if (!target) {
                return JSON.stringify({ ok: false, error: "ref_not_found" });
            }
            try { target.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
            let rect = target.getBoundingClientRect();
            let currentWindow = targetWindow;
            while (currentWindow && currentWindow !== window) {
                let frameElement = null;
                try {
                    frameElement = currentWindow.frameElement;
                } catch (_) {
                    frameElement = null;
                }
                if (!frameElement) {
                    break;
                }
                const frameRect = frameElement.getBoundingClientRect();
                rect = {
                    left: rect.left + frameRect.left,
                    top: rect.top + frameRect.top,
                    right: rect.right + frameRect.left,
                    bottom: rect.bottom + frameRect.top
                };
                try {
                    currentWindow = frameElement.ownerDocument.defaultView;
                } catch (_) {
                    break;
                }
            }
            return JSON.stringify({
                ok: true,
                left: Math.max(0, Math.floor(rect.left)),
                top: Math.max(0, Math.floor(rect.top)),
                right: Math.max(0, Math.ceil(rect.right)),
                bottom: Math.max(0, Math.ceil(rect.bottom))
            });
        })();
        """.trimIndent()
    val json = runJsonScript(webView, script, "element_rect_error") ?: return null
    if (!json.optBoolean("ok", false)) {
        return null
    }
    return Rect(
        json.optInt("left"),
        json.optInt("top"),
        json.optInt("right"),
        json.optInt("bottom")
    )
}

internal fun StandardBrowserSessionTools.runPlaywrightLikeCode(
    session: BrowserToolSession,
    code: String
): String {
    val expression =
        """
        (async function() {
            const isVisible = (el) => {
                if (!el || el.nodeType !== 1) return false;
                const style = window.getComputedStyle(el);
                if (!style || style.visibility === "hidden" || style.display === "none") return false;
                const rect = el.getBoundingClientRect();
                return rect.width > 0 || rect.height > 0;
            };
            const roleFor = (el) => {
                const explicit = String(el.getAttribute("role") || "").trim();
                if (explicit) return explicit;
                const tag = String(el.tagName || "").toLowerCase();
                if (tag === "a") return "link";
                if (tag === "button") return "button";
                if (tag === "select") return "combobox";
                if (tag === "textarea") return "textbox";
                if (tag === "input") {
                    const type = String(el.getAttribute("type") || "").toLowerCase();
                    if (type === "checkbox") return "checkbox";
                    if (type === "radio") return "radio";
                    if (type === "submit" || type === "button" || type === "reset") return "button";
                    return "textbox";
                }
                return "generic";
            };
            const nameFor = (el) => String(
                el.getAttribute("aria-label") ||
                el.getAttribute("title") ||
                el.getAttribute("placeholder") ||
                el.getAttribute("alt") ||
                el.getAttribute("value") ||
                el.innerText ||
                el.textContent ||
                ""
            ).replace(/\s+/g, " ").trim();
            const findByRole = (role, name) => Array.from(document.querySelectorAll("*"))
                .filter((el) => isVisible(el) && roleFor(el) === String(role))
                .find((el) => name == null || nameFor(el) === String(name));
            const makeLocator = (resolver, description) => ({
                async click() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    try { el.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
                    try { el.focus({ preventScroll: true }); } catch (_) {}
                    setTimeout(() => { try { el.click(); } catch (_) {} }, 0);
                    await new Promise((resolve) => setTimeout(resolve, 60));
                    return null;
                },
                async hover() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    const rect = el.getBoundingClientRect();
                    ["pointerover", "mouseover", "mouseenter", "mousemove"].forEach((type) => {
                        el.dispatchEvent(new MouseEvent(type, {
                            bubbles: true,
                            cancelable: true,
                            clientX: rect.left + rect.width / 2,
                            clientY: rect.top + rect.height / 2
                        }));
                    });
                    return null;
                },
                async fill(value) {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    el.value = String(value ?? "");
                    el.dispatchEvent(new Event("input", { bubbles: true }));
                    el.dispatchEvent(new Event("change", { bubbles: true }));
                    return null;
                },
                async selectOption(values) {
                    const el = resolver();
                    if (!el || !el.options) throw new Error(description + " is not a select element");
                    const wanted = Array.isArray(values) ? values.map((item) => String(item)) : [String(values)];
                    Array.from(el.options).forEach((option) => {
                        option.selected = wanted.includes(String(option.value)) || wanted.includes(String(option.text));
                    });
                    el.dispatchEvent(new Event("input", { bubbles: true }));
                    el.dispatchEvent(new Event("change", { bubbles: true }));
                    return null;
                },
                async textContent() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    return String(el.textContent || "");
                }
            });
            const page = {
                async title() { return String(document.title || ""); },
                async url() { return String(location.href || ""); },
                async evaluate(fn) {
                    if (typeof fn !== "function") throw new Error("page.evaluate expects a function");
                    return await fn();
                },
                async waitForTimeout(ms) {
                    await new Promise((resolve) => setTimeout(resolve, Number(ms) || 0));
                    return null;
                },
                locator(selector) {
                    return makeLocator(() => document.querySelector(String(selector)), "locator(" + selector + ")");
                },
                getByRole(role, options) {
                    const name = options && Object.prototype.hasOwnProperty.call(options, "name") ? options.name : null;
                    return makeLocator(() => findByRole(role, name), "getByRole(" + role + ")");
                },
                keyboard: {
                    async press(key) {
                        const target = document.activeElement || document.body || document.documentElement;
                        if (!target) throw new Error("No active element");
                        target.dispatchEvent(new KeyboardEvent("keydown", { key: String(key), bubbles: true, cancelable: true }));
                        target.dispatchEvent(new KeyboardEvent("keyup", { key: String(key), bubbles: true, cancelable: true }));
                        return null;
                    }
                },
                async goto() {
                    throw new Error("page.goto is not supported in Android WebView browser_run_code. Use browser.goto instead.");
                },
                async goBack() {
                    throw new Error("page.goBack is not supported in Android WebView browser_run_code. Use browser.back instead.");
                },
                async setViewportSize() {
                    throw new Error("page.setViewportSize is not supported in browser_run_code. Use browser.resize instead.");
                }
            };
            const codeSource = ${quoteJs(code)};
            const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;

            let fn = null;
            try {
                const maybeFn = (0, eval)("(" + codeSource + ")");
                if (typeof maybeFn === "function") {
                    fn = maybeFn;
                }
            } catch (_) {}

            let value;
            if (fn) {
                value = await fn(page);
            } else {
                const runner = new AsyncFunction("page", "console", codeSource);
                value = await runner(page, console);
            }
            return value == null ? "" : value;
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            session.webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(15_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.parseHeaders(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) {
        return emptyMap()
    }

    return try {
        val json = JSONObject(raw)
        val keys = json.keys()
        val out = mutableMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = json.optString(key, "")
        }
        out
    } catch (e: Exception) {
        AppLogger.w(EXECUTION_SUPPORT_TAG, "Invalid headers JSON: ${e.message}")
        emptyMap()
    }
}

internal fun StandardBrowserSessionTools.parseStringArrayParam(raw: String?): List<String>? {
    if (raw.isNullOrBlank()) {
        return emptyList()
    }

    return try {
        val array = JSONArray(raw)
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            if (value == null || value == JSONObject.NULL) {
                continue
            }
            out.add(value.toString())
        }
        out
    } catch (e: Exception) {
        AppLogger.w(EXECUTION_SUPPORT_TAG, "Invalid array JSON: ${e.message}")
        null
    }
}

internal fun StandardBrowserSessionTools.param(tool: AITool, name: String): String? =
    tool.parameters.find { it.name == name }?.value

internal fun StandardBrowserSessionTools.boolParam(
    tool: AITool,
    name: String,
    default: Boolean
): Boolean {
    return when (param(tool, name)?.trim()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> default
    }
}

internal fun StandardBrowserSessionTools.intParam(tool: AITool, name: String, default: Int): Int {
    return param(tool, name)?.trim()?.toIntOrNull() ?: default
}

internal fun StandardBrowserSessionTools.longParam(tool: AITool, name: String, default: Long): Long {
    return param(tool, name)?.trim()?.toLongOrNull() ?: default
}

internal fun StandardBrowserSessionTools.ok(toolName: String, payload: JSONObject): ToolResult {
    return ok(toolName, payload.toString())
}

internal fun StandardBrowserSessionTools.ok(toolName: String, payload: String): ToolResult {
    return ToolResult(
        toolName = toolName,
        success = true,
        result = StringResultData(payload)
    )
}

internal fun StandardBrowserSessionTools.error(toolName: String, message: String): ToolResult {
    val rendered = buildBrowserResponse(error = message)
    return ToolResult(
        toolName = toolName,
        success = false,
        result = StringResultData(rendered),
        error = rendered
    )
}

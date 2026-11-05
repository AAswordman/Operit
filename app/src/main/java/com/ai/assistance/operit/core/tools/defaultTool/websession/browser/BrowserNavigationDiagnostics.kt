package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.os.SystemClock
import android.webkit.WebView
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONException
import org.json.JSONObject

internal data class BrowserDomClickEvent(
    val matchesTarget: Boolean,
    val defaultPrevented: Boolean,
    val trusted: Boolean,
    val count: Int
)

internal data class BrowserClickTrace(
    val id: String,
    val startedAtElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    @Volatile var capturedEvent: BrowserDomClickEvent? = null
    @Volatile var completedEvent: BrowserDomClickEvent? = null
    @Volatile var navigationFailure: String? = null
}

internal fun StandardBrowserSessionTools.traceBrowserNavigation(
    session: BrowserToolSession,
    phase: String,
    view: WebView = session.webView,
    url: String? = null,
    details: JSONObject? = null
) {
    val callerThread = Thread.currentThread().name
    runOnMainSync<Unit> {
        val payload =
            JSONObject()
                .put("phase", phase)
                .put("session", session.id)
                .put("activeSession", StandardBrowserSessionTools.activeSessionId)
                .put("webView", System.identityHashCode(view))
                .put("sessionWebView", System.identityHashCode(session.webView))
                .put("attached", view.isAttachedToWindow)
                .put("callerThread", callerThread)
                .put("navigationSequence", session.navigationSequence)
                .put("callbackUrl", url)
                .put("webViewUrl", if (view.isAttachedToWindow) view.url else JSONObject.NULL)
                .put("stateUrl", session.currentUrl)
                .put("pendingUrl", session.pendingNavigationUrl)
                .put("loading", session.isLoading)
                .put("details", details)
        session.lastClickTrace?.let { trace ->
            payload
                .put("lastClickId", trace.id)
                .put("clickAgeMs", SystemClock.elapsedRealtime() - trace.startedAtElapsedMs)
        }
        AppLogger.d("BrowserNavigation", payload.toString())
    }
}

internal fun StandardBrowserSessionTools.recordBrowserClickEvent(
    session: BrowserToolSession,
    payload: String
) {
    try {
        val data = JSONObject(payload)
        val trace = session.lastClickTrace ?: return
        if (data.getString("clickId") != trace.id) return
        when (data.getString("phase")) {
            "dom-click-capture", "dom-click-final" -> {
                val event =
                    BrowserDomClickEvent(
                        matchesTarget = data.getBoolean("matchesTarget"),
                        defaultPrevented = data.getBoolean("defaultPrevented"),
                        trusted = data.getBoolean("trusted"),
                        count = data.getInt("eventCount")
                    )
                if (data.getString("phase") == "dom-click-capture") {
                    trace.capturedEvent = event
                } else {
                    trace.completedEvent = event
                }
            }
        }
        notifySessionStateChanged(session)
    } catch (e: JSONException) {
        AppLogger.w("BrowserNavigation", "Invalid click observation for session=${session.id}", e)
    }
}

internal fun StandardBrowserSessionTools.prepareBrowserClickTrace(
    session: BrowserToolSession,
    trace: BrowserClickTrace,
    ref: String?,
    selector: String?
): JSONObject? {
    val resolveTarget =
        if (ref != null) {
            """
            ${browserRefResolverScript()}
            const resolved = __operitResolveRef(${JSONObject.quote(ref)});
            if (!resolved) {
                return JSON.stringify({ ok: false, error: "ref_not_found" });
            }
            const target = resolved.element;
            const targetWindow = resolved.window;
            """.trimIndent()
        } else {
            """
            const target = document.querySelector(${JSONObject.quote(requireNotNull(selector))});
            if (!target) {
                return JSON.stringify({ ok: false, error: "selector_not_found" });
            }
            const targetWindow = target.ownerDocument.defaultView;
            """.trimIndent()
        }
    val script =
        """
        (async function() {
            try {
                $resolveTarget
                const targetDocument = target.ownerDocument;
                const previousTrace = window.__operitBrowserClickTrace;
                if (previousTrace) previousTrace.dispose();

                // Smooth scrolling can leave the old rect valid until the next layout. Scroll
                // instantly, including ancestor frames, then require stable visible geometry.
                target.scrollIntoView({ behavior: "instant", block: "center", inline: "center" });
                let currentWindow = targetWindow;
                while (currentWindow !== window) {
                    const frame = currentWindow.frameElement;
                    if (!frame) throw new Error("frame_not_reachable");
                    frame.scrollIntoView({ behavior: "instant", block: "center", inline: "center" });
                    currentWindow = frame.ownerDocument.defaultView;
                }
                const intersect = (a, b) => ({
                    left: Math.max(a.left, b.left), top: Math.max(a.top, b.top),
                    right: Math.min(a.right, b.right), bottom: Math.min(a.bottom, b.bottom)
                });
                const measure = () => {
                    if (!target.isConnected) throw new Error("click_target_detached");
                    const localRect = target.getBoundingClientRect();
                    let visible = intersect(localRect, {
                        left: 0, top: 0, right: targetWindow.innerWidth, bottom: targetWindow.innerHeight
                    });
                    const frames = [];
                    let childWindow = targetWindow;
                    while (childWindow !== window) {
                        const frame = childWindow.frameElement;
                        if (!frame || frame.offsetWidth <= 0 || frame.offsetHeight <= 0) {
                            throw new Error("frame_not_visible");
                        }
                        const bounds = frame.getBoundingClientRect();
                        const scaleX = bounds.width / frame.offsetWidth;
                        const scaleY = bounds.height / frame.offsetHeight;
                        if (scaleX <= 0 || scaleY <= 0) throw new Error("frame_not_visible");
                        const originX = bounds.left + frame.clientLeft * scaleX;
                        const originY = bounds.top + frame.clientTop * scaleY;
                        const parentWindow = frame.ownerDocument.defaultView;
                        frames.push({ frame, parentWindow, scaleX, scaleY, originX, originY });
                        visible = intersect({
                            left: originX + visible.left * scaleX,
                            top: originY + visible.top * scaleY,
                            right: originX + visible.right * scaleX,
                            bottom: originY + visible.bottom * scaleY
                        }, { left: 0, top: 0, right: parentWindow.innerWidth, bottom: parentWindow.innerHeight });
                        childWindow = parentWindow;
                    }
                    const viewport = window.visualViewport;
                    if (!viewport) throw new Error("visual_viewport_unavailable");
                    visible = intersect(visible, {
                        left: viewport.offsetLeft, top: viewport.offsetTop,
                        right: viewport.offsetLeft + viewport.width,
                        bottom: viewport.offsetTop + viewport.height
                    });
                    if (visible.right <= visible.left || visible.bottom <= visible.top) {
                        throw new Error("element_outside_viewport");
                    }
                    const rootX = (visible.left + visible.right) / 2;
                    const rootY = (visible.top + visible.bottom) / 2;
                    let localX = rootX;
                    let localY = rootY;
                    let hitTarget = true;
                    for (let index = frames.length - 1; index >= 0; index--) {
                        const item = frames[index];
                        if (item.parentWindow.document.elementFromPoint(localX, localY) !== item.frame) {
                            hitTarget = false;
                        }
                        localX = (localX - item.originX) / item.scaleX;
                        localY = (localY - item.originY) / item.scaleY;
                    }
                    const hit = targetDocument.elementFromPoint(localX, localY);
                    hitTarget = hitTarget && (hit === target || (hit !== null && target.contains(hit)));
                    return {
                        rect: { left: localRect.left, top: localRect.top, right: localRect.right, bottom: localRect.bottom },
                        point: { x: rootX - viewport.offsetLeft, y: rootY - viewport.offsetTop },
                        targetPoint: { x: localX, y: localY },
                        viewport: {
                            width: viewport.width, height: viewport.height,
                            offsetLeft: viewport.offsetLeft, offsetTop: viewport.offsetTop
                        },
                        hitTarget
                    };
                };
                let geometry;
                let previousSignature = "";
                let stableSamples = 0;
                const deadline = performance.now() + 3000;
                while (performance.now() < deadline) {
                    const measured = measure();
                    const signature = JSON.stringify(measured);
                    stableSamples = signature === previousSignature ? stableSamples + 1 : 0;
                    if (stableSamples >= 1) {
                        if (!measured.hitTarget) throw new Error("element_obscured");
                        geometry = measured;
                        break;
                    }
                    previousSignature = signature;
                    await new Promise(resolve => window.setTimeout(resolve, 32));
                }
                if (!geometry) throw new Error("element_layout_unstable");

                const anchor = target.closest('a[href]');
                let navigationWindow = targetWindow;
                let namedFrame = null;
                if (anchor) {
                    const name = anchor.target.toLowerCase();
                    if (name === "_top") navigationWindow = window;
                    else if (name === "_parent") navigationWindow = targetWindow.parent;
                    else if (name === "_blank") navigationWindow = null;
                    else if (name !== "" && name !== "_self") {
                        // window.frames is also a Window: indexing by names such as "location"
                        // can return a built-in property rather than a browsing context.
                        const windows = [window];
                        const visited = new Set();
                        while (windows.length && namedFrame === null) {
                            const owner = windows.shift();
                            if (visited.has(owner)) continue;
                            visited.add(owner);
                            let frames;
                            try {
                                frames = owner.document.querySelectorAll("iframe, frame");
                            } catch (error) {
                                if (error.name !== "SecurityError") throw error;
                                continue;
                            }
                            for (const frame of frames) {
                                if (frame.name === anchor.target) {
                                    namedFrame = frame;
                                    break;
                                }
                                if (frame.contentWindow !== null) windows.push(frame.contentWindow);
                            }
                        }
                        if (namedFrame !== null && namedFrame.contentWindow === null) {
                            throw new Error("target_frame_not_ready");
                        }
                        navigationWindow = namedFrame === null ? null : namedFrame.contentWindow;
                    }
                }
                let initialNavigationUrl = null;
                let initialNavigationUrlReadable = false;
                if (navigationWindow !== null) {
                    try {
                        initialNavigationUrl = navigationWindow.location.href;
                        initialNavigationUrlReadable = true;
                    } catch (error) {
                        if (error.name !== "SecurityError") throw error;
                    }
                }
                const navigationFrame = namedFrame !== null ? namedFrame :
                    (navigationWindow === null || navigationWindow === window ? null : navigationWindow.frameElement);
                const navigationExpected = anchor !== null &&
                    (anchor.protocol === "http:" || anchor.protocol === "https:") &&
                    !anchor.hasAttribute("download") &&
                    (navigationWindow === null || !initialNavigationUrlReadable || anchor.href !== initialNavigationUrl);
                const trace = {
                    id: ${JSONObject.quote(trace.id)}, target, targetWindow, geometry,
                    navigationWindow, initialNavigationUrl, frameLoads: 0, eventCount: 0
                };
                const emitTrace = (phase, data) => window.OperitAsyncBridge.recordClick(
                    JSON.stringify({ clickId: trace.id, phase, documentUrl: targetDocument.URL, ...data })
                );
                const onClick = (event) => {
                    trace.eventCount += 1;
                    const actualTarget = event.target;
                    const actualAnchor = actualTarget instanceof targetWindow.Element
                        ? actualTarget.closest('a[href]') : null;
                    const rect = target.getBoundingClientRect();
                    const data = {
                        eventCount: trace.eventCount, trusted: event.isTrusted,
                        pointerType: event.pointerType,
                        tag: actualTarget.nodeName,
                        matchesTarget: actualTarget === target || target.contains(actualTarget),
                        href: actualAnchor ? actualAnchor.href : null,
                        x: event.clientX, y: event.clientY,
                        button: event.button, detail: event.detail,
                        defaultPrevented: event.defaultPrevented,
                        targetRectAtEvent: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom }
                    };
                    emitTrace("dom-click-capture", data);
                    window.setTimeout(() => {
                        data.defaultPrevented = event.defaultPrevented;
                        emitTrace("dom-click-final", data);
                    }, 0);
                };
                const onFrameLoad = () => {
                    trace.frameLoads += 1;
                    emitTrace("frame-load", { frameLoads: trace.frameLoads });
                };
                const expiry = window.setTimeout(() => trace.dispose(), ${StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS + 2000L});
                trace.dispose = () => {
                    targetDocument.removeEventListener("click", onClick, true);
                    if (navigationFrame !== null) navigationFrame.removeEventListener("load", onFrameLoad);
                    window.clearTimeout(expiry);
                    if (window.__operitBrowserClickTrace === trace) delete window.__operitBrowserClickTrace;
                };
                window.__operitBrowserClickTrace = trace;
                targetDocument.addEventListener("click", onClick, { capture: true, passive: true });
                if (navigationFrame !== null) navigationFrame.addEventListener("load", onFrameLoad);
                return JSON.stringify({
                    ok: true, clickId: trace.id, documentUrl: targetDocument.URL,
                    topDocumentUrl: document.URL, tag: target.tagName,
                    href: anchor ? anchor.href : null,
                    navigationExpected, opensNewContext: navigationWindow === null,
                    ...geometry
                });
            } catch (error) {
                return JSON.stringify({ ok: false, error: String(error) });
            }
        })();
        """.trimIndent()
    return runJsonScript(session.webView, script, "click_target_preparation_error")
}

internal fun StandardBrowserSessionTools.observeBrowserClickDocument(
    session: BrowserToolSession,
    trace: BrowserClickTrace
): JSONObject? {
    val script =
        """
        (function() {
            const trace = window.__operitBrowserClickTrace;
            const observerPresent = !!trace && trace.id === ${JSONObject.quote(trace.id)};
            let navigationUrl = null;
            let navigationUrlReadable = false;
            let navigationObserved = false;
            let navigationReady = false;
            if (observerPresent && trace.navigationWindow !== null) {
                try {
                    navigationUrl = trace.navigationWindow.location.href;
                    navigationUrlReadable = true;
                    navigationObserved = navigationUrl !== trace.initialNavigationUrl;
                    const state = trace.navigationWindow.document.readyState;
                    navigationReady = state === "interactive" || state === "complete";
                } catch (error) {
                    // A same-origin iframe can navigate cross-origin. Report the load event as
                    // lifecycle evidence without claiming access to its new document URL.
                    if (error.name !== "SecurityError") throw error;
                    navigationObserved = trace.frameLoads > 0;
                    navigationReady = navigationObserved;
                }
            }
            return JSON.stringify({
                ok: true, clickId: ${JSONObject.quote(trace.id)},
                documentUrl: document.URL, locationHref: location.href,
                readyState: document.readyState, observerPresent,
                observedClicks: observerPresent ? trace.eventCount : null,
                navigationUrl, navigationUrlReadable, navigationObserved, navigationReady
            });
        })();
        """.trimIndent()
    return runJsonScript(session.webView, script, "click_document_observation_error")
}

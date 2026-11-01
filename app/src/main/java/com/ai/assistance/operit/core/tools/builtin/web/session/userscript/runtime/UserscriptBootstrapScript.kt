package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.runtime

internal object UserscriptBootstrapScript {
    const val BRIDGE_NAME = "OperitUserscriptBridge"

    fun documentStartScript(): String =
        """
        (function() {
            if (window.__operitUserscriptBootstrapInstalled) {
                return;
            }
            window.__operitUserscriptBootstrapInstalled = true;
            const bridge = window.${BRIDGE_NAME};
            if (!bridge || typeof bridge.postMessage !== 'function') {
                return;
            }

            const runtime = {
                pending: new Map(),
                xhrRequests: new Map(),
                menuCallbacks: new Map(),
                valueListeners: new Map(),
                webRequestListeners: new Map(),
                audioListeners: new Map(),
                openTabHandles: new Map(),
                sequence: 0,
                urlChangeInstalled: false,
                lastHref: String(location.href || ""),
                post(type, payload) {
                    const message = JSON.stringify({ type, payload: payload || {} });
                    bridge.postMessage(message);
                },
                request(type, payload) {
                    const requestId = "req_" + (++runtime.sequence) + "_" + Date.now();
                    const message = JSON.stringify({ type, requestId, payload: payload || {} });
                    return new Promise((resolve, reject) => {
                        const timeout = setTimeout(() => {
                            runtime.pending.delete(requestId);
                            reject(new Error("userscript_host_timeout:" + type));
                        }, 20000);
                        runtime.pending.set(requestId, { resolve, reject, timeout });
                        bridge.postMessage(message);
                    });
                },
                resolvePending(message) {
                    const pending = runtime.pending.get(message.requestId);
                    if (!pending) {
                        return;
                    }
                    runtime.pending.delete(message.requestId);
                    clearTimeout(pending.timeout);
                    if (message.error) {
                        pending.reject(new Error(String(message.error)));
                    } else {
                        pending.resolve(message.payload);
                    }
                },
                normalizeValue(value, fallbackValue) {
                    if (typeof value === "undefined") {
                        return fallbackValue;
                    }
                    return value;
                },
                buildResponseHeaders(headers) {
                    if (!headers || typeof headers !== "object") {
                        return "";
                    }
                    return Object.entries(headers).map(([key, value]) => key + ": " + String(value)).join("\r\n");
                },
                decodeBinaryResponse(response) {
                    const base64 = typeof response === "string" ? response : "";
                    if (!base64) {
                        return new Uint8Array(0);
                    }
                    const raw = atob(base64);
                    const bytes = new Uint8Array(raw.length);
                    for (let index = 0; index < raw.length; index += 1) {
                        bytes[index] = raw.charCodeAt(index);
                    }
                    return bytes;
                },
                parseStoredValues(values) {
                    const parsed = {};
                    Object.entries(values || {}).forEach(([key, raw]) => {
                        parsed[key] = runtime.parseJsonValue(raw, raw);
                    });
                    return parsed;
                },
                parseJsonValue(rawValue, fallbackValue) {
                    if (typeof rawValue !== "string" || rawValue === "") {
                        return fallbackValue;
                    }
                    try {
                        return JSON.parse(rawValue);
                    } catch (_) {
                        return fallbackValue;
                    }
                },
                addStyle(cssText) {
                    const style = document.createElement("style");
                    style.textContent = String(cssText || "");
                    (document.head || document.documentElement || document.body).appendChild(style);
                    return style;
                },
                addElement(arg1, arg2, arg3) {
                    let parent = document.body || document.documentElement;
                    let tagName = arg1;
                    let attributes = arg2;
                    if (arg1 && typeof arg1.appendChild === "function" && typeof arg2 === "string") {
                        parent = arg1;
                        tagName = arg2;
                        attributes = arg3;
                    }
                    const resolvedTag = String(tagName || "").trim();
                    if (!resolvedTag) {
                        throw new Error("GM_addElement requires a tag name");
                    }
                    const element = document.createElement(resolvedTag);
                    if (attributes && typeof attributes === "object") {
                        Object.entries(attributes).forEach(([key, value]) => {
                            if (key === "textContent") {
                                element.textContent = String(value);
                            } else if (key === "innerHTML") {
                                element.innerHTML = String(value);
                            } else if (key in element) {
                                try {
                                    element[key] = value;
                                } catch (_) {
                                    element.setAttribute(key, String(value));
                                }
                            } else {
                                element.setAttribute(key, String(value));
                            }
                        });
                    }
                    (parent || document.body || document.documentElement).appendChild(element);
                    return element;
                },
                log(level, message, scriptId, pageUrl) {
                    const resolvedLevel = String(level || "info");
                    try {
                        const logger = console[resolvedLevel] || console.log;
                        logger.call(console, message);
                    } catch (_) {}
                    runtime.post("runtime_log", {
                        scriptId: scriptId || 0,
                        level: resolvedLevel,
                        message: String(message || ""),
                        pageUrl: pageUrl || String(location.href || "")
                    });
                },
                emitValueChange(scriptId, key, oldValue, newValue, remote) {
                    runtime.valueListeners.forEach((listener) => {
                        if (listener.scriptId !== scriptId || listener.key !== key) {
                            return;
                        }
                        try {
                            listener.callback(key, oldValue, newValue, !!remote);
                        } catch (error) {
                            runtime.log("error", String(error && (error.stack || error.message || error) || "value listener failed"), scriptId, location.href);
                        }
                    });
                },
                dispatchHostEvent(message) {
                    const payload = message && message.payload ? message.payload : {};
                    switch (String(message && message.type || "")) {
                        case "storage_changed":
                            runtime.emitValueChange(
                                Number(payload.scriptId || 0),
                                String(payload.key || ""),
                                runtime.parseJsonValue(payload.oldValueJson, undefined),
                                runtime.parseJsonValue(payload.newValueJson, undefined),
                                !!payload.remote
                            );
                            return;
                        case "audio_state_changed":
                            runtime.audioListeners.forEach((entry) => {
                                try {
                                    if (entry && typeof entry.callback === "function") {
                                        entry.callback({ muted: !!payload.muted });
                                    }
                                } catch (error) {
                                    runtime.log("error", String(error), 0, location.href);
                                }
                            });
                            return;
                        case "open_tab_closed": {
                            const handle = runtime.openTabHandles.get(String(payload.sessionId || ""));
                            if (handle) {
                                handle.closed = true;
                                runtime.openTabHandles.delete(String(payload.sessionId || ""));
                                if (typeof handle.onclose === "function") {
                                    handle.onclose();
                                }
                            }
                            return;
                        }
                        case "web_request_event": {
                            const listener = runtime.webRequestListeners.get(String(payload.registrationId || ""));
                            if (typeof listener === "function") {
                                listener(payload);
                            }
                            return;
                        }
                    }
                },
                installUrlChangeHooks() {
                    if (runtime.urlChangeInstalled) {
                        return;
                    }
                    runtime.urlChangeInstalled = true;
                    runtime.lastHref = String(location.href || "");
                    const emitUrlChange = function(source) {
                        const href = String(location.href || "");
                        if (href === runtime.lastHref) {
                            return;
                        }
                        const previous = runtime.lastHref;
                        runtime.lastHref = href;
                        const event = new CustomEvent("urlchange", { detail: { href: href, previous: previous, source: source } });
                        event.href = href;
                        event.prevHref = previous;
                        window.dispatchEvent(event);
                        runtime.post("url_change", { href: href, previous: previous, source: source });
                    };
                    const patchHistoryMethod = function(name) {
                        const original = history[name];
                        if (typeof original !== "function") {
                            return;
                        }
                        history[name] = function() {
                            const result = original.apply(this, arguments);
                            emitUrlChange(name);
                            return result;
                        };
                    };
                    patchHistoryMethod("pushState");
                    patchHistoryMethod("replaceState");
                    window.addEventListener("popstate", function() { emitUrlChange("popstate"); });
                    window.addEventListener("hashchange", function() { emitUrlChange("hashchange"); });
                },
                createWindowProxy(script, features) {
                    let onUrlChangeHandler = null;
                    const options = features || {};
                    const enableFocus = !!options.focus;
                    const enableClose = !!options.close;
                    const enableUrlChange = !!options.onUrlChange;
                    if (!enableFocus && !enableClose && !enableUrlChange) {
                        return window;
                    }
                    if (typeof Proxy !== "function") {
                        return window;
                    }
                    return new Proxy(window, {
                        get(target, property) {
                            if (enableFocus && property === "focus") {
                                return function() {
                                    return runtime.request("gm_focus_tab", { sessionId: script.sessionId });
                                };
                            }
                            if (enableClose && property === "close") {
                                return function() {
                                    return runtime.request("gm_close_tab", { sessionId: script.sessionId });
                                };
                            }
                            if (enableUrlChange && property === "onurlchange") {
                                return onUrlChangeHandler;
                            }
                            const value = target[property];
                            return typeof value === "function" ? value.bind(target) : value;
                        },
                        set(target, property, value) {
                            if (enableUrlChange && property === "onurlchange") {
                                if (onUrlChangeHandler) {
                                    window.removeEventListener("urlchange", onUrlChangeHandler);
                                }
                                onUrlChangeHandler = typeof value === "function" ? value : null;
                                if (onUrlChangeHandler) {
                                    window.addEventListener("urlchange", onUrlChangeHandler);
                                }
                                return true;
                            }
                            target[property] = value;
                            return true;
                        }
                    });
                },
                installScript(script) {
                    const metadata = JSON.parse(script.metadataJson || "{}");
                    const grants = new Set(script.capabilities || []);
                    const grantNone = grants.has("none");
                    const windowFeatures = {
                        focus: grants.has("window.focus"),
                        close: grants.has("window.close"),
                        onUrlChange: grants.has("window.onurlchange")
                    };
                    const resourceMap = script.resources || {};
                    const storage = runtime.parseStoredValues(script.values || {});
                    const windowProxy = runtime.createWindowProxy(script, windowFeatures);
                    let tabStateCache = null;
                    const makeInfo = function() {
                        const rawHeaders = Array.isArray(metadata.rawHeaders) ? metadata.rawHeaders : [];
                        const metadataBlockText = String(metadata.metadataBlock || "").trim();
                        const scriptMetaStr =
                            metadataBlockText
                                ? "// ==UserScript==\n" + metadataBlockText + "\n// ==/UserScript=="
                                : "";
                        const scriptInfo = {
                            name: String(metadata.name || script.name || ""),
                            namespace: metadata.namespace || script.namespace || "",
                            version: String(metadata.version || script.version || ""),
                            description: metadata.description || "",
                            matches: Array.isArray(metadata.matches) ? metadata.matches.slice() : [],
                            includes: Array.isArray(metadata.includes) ? metadata.includes.slice() : [],
                            excludes: Array.isArray(metadata.excludes) ? metadata.excludes.slice() : [],
                            excludeMatches: Array.isArray(metadata.excludeMatches) ? metadata.excludeMatches.slice() : [],
                            grants: Array.isArray(metadata.grants) ? metadata.grants.slice() : [],
                            resources: Array.isArray(metadata.resources) ? metadata.resources.slice() : [],
                            requires: Array.isArray(metadata.requires) ? metadata.requires.slice() : [],
                            runAt: String(metadata.runAt || script.runAt || "document-end"),
                            noframes: !!metadata.noFrames,
                            unwrap: !!metadata.unwrap
                        };
                        return {
                            script: scriptInfo,
                            scriptHandler: "Tampermonkey",
                            version: "5.3.3",
                            scriptWillUpdate: !!metadata.updateUrl,
                            scriptSource: String(script.code || ""),
                            scriptMetaStr: scriptMetaStr,
                            scriptUpdateURL: metadata.updateUrl || metadata.downloadUrl || "",
                            scriptDownloadURL: metadata.downloadUrl || metadata.updateUrl || "",
                            scriptNamespace: metadata.namespace || "",
                            scriptName: String(metadata.name || script.name || ""),
                            scriptVersion: String(metadata.version || script.version || ""),
                            platform: {
                                browserName: navigator.userAgent,
                                os: navigator.platform,
                                arch: "webview"
                            },
                            rawHeaders: rawHeaders
                        };
                    };
                    const hasGrant = function() {
                        if (grantNone || grants.size === 0) {
                            return false;
                        }
                        for (let index = 0; index < arguments.length; index += 1) {
                            if (grants.has(arguments[index])) {
                                return true;
                            }
                        }
                        return false;
                    };
                    const persistSet = function(key, value) {
                        const oldValue = storage[key];
                        storage[key] = value;
                        runtime.post("storage_set", {
                            scriptId: script.scriptId,
                            key: key,
                            valueJson: JSON.stringify(value)
                        });
                        runtime.emitValueChange(script.scriptId, key, oldValue, value, false);
                    };
                    const persistDelete = function(key) {
                        const oldValue = storage[key];
                        delete storage[key];
                        runtime.post("storage_delete", {
                            scriptId: script.scriptId,
                            key: key
                        });
                        runtime.emitValueChange(script.scriptId, key, oldValue, undefined, false);
                    };
                    const setValues = function(values) {
                        const payload = {};
                        Object.entries(values || {}).forEach(([key, value]) => {
                            const oldValue = storage[key];
                            storage[key] = value;
                            payload[key] = JSON.stringify(value);
                            runtime.emitValueChange(script.scriptId, key, oldValue, value, false);
                        });
                        runtime.post("storage_set_many", {
                            scriptId: script.scriptId,
                            values: payload
                        });
                    };
                    const deleteValues = function(keys) {
                        const normalizedKeys =
                            Array.isArray(keys)
                                ? keys.map((value) => String(value))
                                : typeof keys === "string"
                                    ? [keys]
                                    : Object.keys(keys || {});
                        normalizedKeys.forEach((key) => {
                            const oldValue = storage[key];
                            delete storage[key];
                            runtime.emitValueChange(script.scriptId, key, oldValue, undefined, false);
                        });
                        runtime.post("storage_delete_many", {
                            scriptId: script.scriptId,
                            keys: normalizedKeys
                        });
                    };
                    const getValues = function(keysOrDefaults) {
                        if (Array.isArray(keysOrDefaults)) {
                            const result = {};
                            keysOrDefaults.forEach((key) => {
                                result[key] = storage[key];
                            });
                            return result;
                        }
                        if (keysOrDefaults && typeof keysOrDefaults === "object") {
                            const result = {};
                            Object.entries(keysOrDefaults).forEach(([key, fallbackValue]) => {
                                result[key] = runtime.normalizeValue(storage[key], fallbackValue);
                            });
                            return result;
                        }
                        return Object.assign({}, storage);
                    };
                    const warnUnsupported = function(feature, detail) {
                        runtime.log("warn", feature + ": " + detail, script.scriptId, location.href);
                    };
                    const registerMenuCommand = function(caption, callback, optionsOrAccessKey) {
                        if (typeof callback !== "function") {
                            throw new Error("GM_registerMenuCommand requires a callback");
                        }
                        const options =
                            typeof optionsOrAccessKey === "string"
                                ? { accessKey: optionsOrAccessKey }
                                : (optionsOrAccessKey || {});
                        const commandId = String(options.id || ("menu_" + script.scriptId + "_" + (++runtime.sequence)));
                        runtime.menuCallbacks.set(commandId, callback);
                        runtime.post("register_menu_command", {
                            commandId: commandId,
                            title: String(caption || ""),
                            scriptId: script.scriptId,
                            accessKey: String(options.accessKey || ""),
                            autoClose: options.autoClose !== false
                        });
                        return commandId;
                    };
                    const unregisterMenuCommand = function(commandId) {
                        runtime.menuCallbacks.delete(commandId);
                        runtime.post("unregister_menu_command", {
                            commandId: String(commandId || ""),
                            scriptId: script.scriptId
                        });
                    };
                    const xmlHttpRequest = function(details) {
                        const requestDetails = details || {};
                        const requestData = requestDetails.data;
                        if ((typeof ArrayBuffer === "function" && requestData instanceof ArrayBuffer) ||
                            (typeof ArrayBuffer === "function" && ArrayBuffer.isView && ArrayBuffer.isView(requestData)) ||
                            (typeof Blob === "function" && requestData instanceof Blob) ||
                            (typeof FormData === "function" && requestData instanceof FormData)) {
                            throw new Error("GM_xmlhttpRequest does not support binary request bodies in WebSession");
                        }
                        const requestId = "xhr_" + script.scriptId + "_" + (++runtime.sequence);
                        runtime.xhrRequests.set(requestId, requestDetails);
                        runtime.post("gm_xmlhttp_request", {
                            requestId,
                            scriptId: script.scriptId,
                            pageUrl: location.href,
                            method: String(requestDetails.method || "GET"),
                            url: String(requestDetails.url || ""),
                            headers: requestDetails.headers || {},
                            data: typeof requestData === "undefined" ? null : requestData,
                            responseType: String(requestDetails.responseType || "text"),
                            timeoutMs: Number(requestDetails.timeout || 0),
                            anonymous: !!requestDetails.anonymous,
                            requestType: "xhr"
                        });
                        return {
                            abort: function() {
                                if (typeof requestDetails.onabort === "function") {
                                    requestDetails.onabort({ status: 0, readyState: 4, responseText: "" });
                                }
                                runtime.post("gm_abort_request", { requestId: requestId });
                                runtime.xhrRequests.delete(requestId);
                            }
                        };
                    };
                    const download = function(arg1, arg2) {
                        const options = typeof arg1 === "string" ? { url: arg1, name: arg2 } : (arg1 || {});
                        const url = typeof options.url === "string" ? options.url.trim() : "";
                        if (!url) {
                            throw new Error("GM_download requires a url");
                        }
                        if (typeof options.url !== "string") {
                            throw new Error("GM_download only supports string URLs in WebSession");
                        }
                        if (typeof options.onload === "function" ||
                            typeof options.onprogress === "function" ||
                            typeof options.onreadystatechange === "function" ||
                            typeof options.ontimeout === "function") {
                            warnUnsupported("GM_download", "completion and progress callbacks are not supported in WebSession");
                        }
                        const handle = {
                            abort: function() {
                                throw new Error("GM_download abort is not supported in WebSession");
                            },
                            promise: null
                        };
                        handle.promise =
                            runtime.request("gm_download", {
                                scriptId: script.scriptId,
                                url: url,
                                fileName: options.name ? String(options.name) : "",
                                saveAs: !!options.saveAs,
                                conflictAction: String(options.conflictAction || "")
                            }).then((payload) => ({
                                started: !!payload.started,
                                url: String(payload.url || url),
                                fileName: String(payload.fileName || options.name || "")
                            }));
                        handle.promise.catch((error) => {
                            if (typeof options.onerror === "function") {
                                try {
                                    options.onerror({ error: String(error) });
                                } catch (callbackError) {
                                    runtime.log("error", String(callbackError), script.scriptId, location.href);
                                }
                            }
                            runtime.log("error", String(error), script.scriptId, location.href);
                        });
                        return handle;
                    };
                    const addValueChangeListener = function(key, callback) {
                        if (typeof callback !== "function") {
                            throw new Error("GM_addValueChangeListener requires a callback");
                        }
                        const listenerId = "value_" + script.scriptId + "_" + (++runtime.sequence);
                        runtime.valueListeners.set(listenerId, {
                            scriptId: script.scriptId,
                            key: String(key || ""),
                            callback: callback
                        });
                        return listenerId;
                    };
                    const removeValueChangeListener = function(listenerId) {
                        runtime.valueListeners.delete(String(listenerId || ""));
                    };
                    const getTab = function(callback) {
                        const promise =
                            runtime.request("gm_get_tab", {
                                scriptId: script.scriptId
                            }).then((payload) => {
                                tabStateCache = JSON.parse(String(payload.tabJson || "{}"));
                                return tabStateCache;
                            });
                        if (typeof callback === "function") {
                            promise.then((value) => callback(value));
                        }
                        return promise;
                    };
                    const saveTab = function(tab, callback) {
                        const nextTab =
                            tab && typeof tab === "object"
                                ? tab
                                : (tabStateCache && typeof tabStateCache === "object" ? tabStateCache : {});
                        const promise =
                            runtime.request("gm_save_tab", {
                                scriptId: script.scriptId,
                                tabJson: JSON.stringify(nextTab)
                            }).then((payload) => {
                                tabStateCache = JSON.parse(String(payload.tabJson || "{}"));
                                return tabStateCache;
                            });
                        if (typeof callback === "function") {
                            promise.then((value) => callback(value));
                        }
                        return promise;
                    };
                    const getTabs = function(callback) {
                        const promise =
                            runtime.request("gm_get_tabs", {
                                scriptId: script.scriptId
                            }).then((payload) => {
                                const rawTabs = JSON.parse(String(payload.tabsJson || "{}"));
                                const parsed = {};
                                Object.entries(rawTabs).forEach(([sessionId, tabJson]) => {
                                    parsed[sessionId] = runtime.parseJsonValue(tabJson, {});
                                });
                                return parsed;
                            });
                        if (typeof callback === "function") {
                            promise.then((value) => callback(value));
                        }
                        return promise;
                    };
                    const cookieApi = {
                        list: function(details, callback) {
                            const resolvedDetails = typeof details === "function" ? {} : (details || {});
                            const resolvedCallback = typeof details === "function" ? details : callback;
                            const promise =
                                runtime.request("gm_cookie", {
                                    action: "list",
                                    scriptId: script.scriptId,
                                    pageUrl: location.href,
                                    details: resolvedDetails
                                }).then((payload) => JSON.parse(String(payload.cookiesJson || "[]")));
                            if (typeof resolvedCallback === "function") {
                                promise.then((value) => resolvedCallback(value, null)).catch((error) => resolvedCallback(undefined, error));
                            }
                            return promise;
                        },
                        set: function(details, callback) {
                            const resolvedDetails = typeof details === "function" ? {} : (details || {});
                            const resolvedCallback = typeof details === "function" ? details : callback;
                            const promise =
                                runtime.request("gm_cookie", {
                                    action: "set",
                                    scriptId: script.scriptId,
                                    pageUrl: location.href,
                                    details: resolvedDetails
                                }).then((payload) => JSON.parse(String(payload.cookieJson || "{}")));
                            if (typeof resolvedCallback === "function") {
                                promise.then((value) => resolvedCallback(value, null)).catch((error) => resolvedCallback(undefined, error));
                            }
                            return promise;
                        },
                        delete: function(details, callback) {
                            const resolvedDetails = typeof details === "function" ? {} : (details || {});
                            const resolvedCallback = typeof details === "function" ? details : callback;
                            const promise =
                                runtime.request("gm_cookie", {
                                    action: "delete",
                                    scriptId: script.scriptId,
                                    pageUrl: location.href,
                                    details: resolvedDetails
                                }).then(() => true);
                            if (typeof resolvedCallback === "function") {
                                promise.then((value) => resolvedCallback(value, null)).catch((error) => resolvedCallback(undefined, error));
                            }
                            return promise;
                        }
                    };
                    const audioApi = {
                        setMute: function(details, callback) {
                            const muted = typeof details === "boolean" ? details : !!(details && details.muted);
                            const promise =
                                runtime.request("gm_audio", {
                                    action: "set_mute",
                                    scriptId: script.scriptId,
                                    muted: muted
                                }).then((payload) => JSON.parse(String(payload.stateJson || "{\"muted\":false}")));
                            if (typeof callback === "function") {
                                promise.then((value) => callback(value));
                            }
                            return promise;
                        },
                        getState: function(callback) {
                            const promise =
                                runtime.request("gm_audio", {
                                    action: "get_state",
                                    scriptId: script.scriptId
                                }).then((payload) => JSON.parse(String(payload.stateJson || "{\"muted\":false}")));
                            if (typeof callback === "function") {
                                promise.then((value) => callback(value));
                            }
                            return promise;
                        },
                        addStateChangeListener: function(listener, callback) {
                            const resolvedCallback =
                                typeof listener === "function"
                                    ? listener
                                    : callback;
                            if (typeof resolvedCallback !== "function") {
                                throw new Error("GM.audio.addStateChangeListener requires a callback");
                            }
                            const listenerId = "audio_" + script.scriptId + "_" + (++runtime.sequence);
                            runtime.audioListeners.set(listenerId, {
                                listener: typeof listener === "string" ? listener : "",
                                callback: resolvedCallback
                            });
                            return listenerId;
                        },
                        removeStateChangeListener: function(listenerOrId, callback) {
                            if (typeof callback === "function") {
                                runtime.audioListeners.forEach((entry, key) => {
                                    if (entry && entry.listener === String(listenerOrId || "") && entry.callback === callback) {
                                        runtime.audioListeners.delete(key);
                                    }
                                });
                                return;
                            }
                            runtime.audioListeners.delete(String(listenerOrId || ""));
                        }
                    };
                    const createOpenTabHandle = function(url, options, promiseBased) {
                        const resolvedOptions =
                            typeof options === "boolean"
                                ? { active: options }
                                : options === false
                                    ? { active: false }
                                    : (options || {});
                        const handle = {
                            sessionId: "",
                            closed: false,
                            onclose: null,
                            name: resolvedOptions && resolvedOptions.name ? String(resolvedOptions.name) : "",
                            close: function() {
                                if (!handle.sessionId || handle.closed) {
                                    return Promise.resolve(false);
                                }
                                return runtime.request("gm_close_tab", { sessionId: handle.sessionId }).then(() => {
                                    handle.closed = true;
                                    return true;
                                });
                            },
                            focus: function() {
                                if (!handle.sessionId) {
                                    return Promise.resolve();
                                }
                                return runtime.request("gm_focus_tab", { sessionId: handle.sessionId });
                            }
                        };
                        const promise =
                            runtime.request("gm_open_in_tab", {
                                scriptId: script.scriptId,
                                url: String(url || ""),
                                active: resolvedOptions.active !== false
                            }).then((payload) => {
                                handle.sessionId = String(payload.sessionId || "");
                                if (handle.sessionId) {
                                    runtime.openTabHandles.set(handle.sessionId, handle);
                                }
                                return handle;
                            });
                        if (promiseBased) {
                            return promise;
                        }
                        promise.catch((error) => runtime.log("error", String(error), script.scriptId, location.href));
                        return handle;
                    };
                    const createWebRequestHandle = function(rules, listener, promiseBased, source) {
                        const normalizedRules = [];
                        (Array.isArray(rules) ? rules : [rules]).forEach((rule) => {
                            if (typeof rule !== "string") {
                                normalizedRules.push(rule);
                                return;
                            }
                            const trimmed = rule.trim();
                            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                                try {
                                    const parsedRule = JSON.parse(trimmed);
                                    if (Array.isArray(parsedRule)) {
                                        parsedRule.forEach((item) => normalizedRules.push(item));
                                    } else {
                                        normalizedRules.push(parsedRule);
                                    }
                                    return;
                                } catch (_) {}
                            }
                            normalizedRules.push(rule);
                        });
                        const promise =
                            runtime.request("gm_web_request_register", {
                                scriptId: script.scriptId,
                                source: source || "runtime",
                                rulesJson: JSON.stringify(normalizedRules)
                            }).then((payload) => {
                                const handle = {
                                    registrationId: String(payload.registrationId || ""),
                                    unregister: function() {
                                        if (!handle.registrationId) {
                                            return;
                                        }
                                        runtime.webRequestListeners.delete(handle.registrationId);
                                        runtime.request("gm_web_request_unregister", { registrationId: handle.registrationId })
                                            .catch((error) => runtime.log("error", String(error), script.scriptId, location.href));
                                    }
                                };
                                if (handle.registrationId && typeof listener === "function") {
                                    runtime.webRequestListeners.set(handle.registrationId, listener);
                                }
                                return handle;
                            });
                        if (promiseBased) {
                            return promise;
                        }
                        const placeholder = { registrationId: "", unregister: function() {} };
                        promise.then((handle) => {
                            placeholder.registrationId = handle.registrationId;
                            placeholder.unregister = handle.unregister;
                        }).catch((error) => runtime.log("error", String(error), script.scriptId, location.href));
                        return placeholder;
                    };
                    const setClipboardValue = function(text, info, callback) {
                        const payload = {
                            scriptId: script.scriptId,
                            text: String(text || "")
                        };
                        if (typeof info === "string" && info) {
                            payload.type = info;
                        } else if (info && typeof info === "object") {
                            payload.type = String(info.type || "");
                            payload.mimetype = String(info.mimetype || "");
                        }
                        const promise = runtime.request("gm_set_clipboard", payload);
                        if (typeof callback === "function") {
                            promise.then(() => callback()).catch((error) => callback(error));
                        }
                        return promise;
                    };
                    const normalizeNotificationArgs = function(arg1, arg2, arg3, arg4) {
                        if (arg1 && typeof arg1 === "object" && !Array.isArray(arg1)) {
                            return {
                                title: String(arg1.title || script.name || ""),
                                text: String(arg1.text || arg1.body || ""),
                                image: String(arg1.image || arg1.highlight || ""),
                                onclick: typeof arg1.onclick === "function" ? arg1.onclick : null,
                                ondone: typeof arg2 === "function" ? arg2 : (typeof arg1.ondone === "function" ? arg1.ondone : null),
                                silent: !!arg1.silent,
                                timeout: Number(arg1.timeout || 0)
                            };
                        }
                        return {
                            title: String(arg2 || script.name || ""),
                            text: String(arg1 || ""),
                            image: typeof arg3 === "function" ? "" : String(arg3 || ""),
                            onclick: typeof arg3 === "function" ? arg3 : (typeof arg4 === "function" ? arg4 : null),
                            ondone: null,
                            silent: false,
                            timeout: 0
                        };
                    };
                    const showNotification = function(arg1, arg2, arg3, arg4) {
                        const details = normalizeNotificationArgs(arg1, arg2, arg3, arg4);
                        if (details.onclick || details.ondone) {
                            warnUnsupported("GM_notification", "onclick and ondone callbacks are not supported in WebSession");
                        }
                        return runtime.request("gm_notification", {
                            scriptId: script.scriptId,
                            title: details.title,
                            text: details.text,
                            image: details.image,
                            silent: details.silent,
                            timeout: details.timeout
                        });
                    };
                    const gmObject = {};
                    const legacy = {};
                    if (hasGrant("GM.info") || grantNone) {
                        gmObject.info = makeInfo();
                        legacy.GM_info = makeInfo();
                    }
                    if (hasGrant("GM.getValue")) {
                        gmObject.getValue = function(key, defaultValue) { return Promise.resolve(runtime.normalizeValue(storage[key], defaultValue)); };
                        legacy.GM_getValue = function(key, defaultValue) { return runtime.normalizeValue(storage[key], defaultValue); };
                    }
                    if (hasGrant("GM.setValue")) {
                        gmObject.setValue = function(key, value) { persistSet(String(key), value); return Promise.resolve(); };
                        legacy.GM_setValue = function(key, value) { persistSet(String(key), value); };
                    }
                    if (hasGrant("GM.deleteValue")) {
                        gmObject.deleteValue = function(key) { persistDelete(String(key)); return Promise.resolve(); };
                        legacy.GM_deleteValue = function(key) { persistDelete(String(key)); };
                    }
                    if (hasGrant("GM.listValues")) {
                        gmObject.listValues = function() { return Promise.resolve(Object.keys(storage)); };
                        legacy.GM_listValues = function() { return Object.keys(storage); };
                    }
                    if (hasGrant("GM.getValues")) {
                        gmObject.getValues = function(keysOrDefaults) { return Promise.resolve(getValues(keysOrDefaults)); };
                        legacy.GM_getValues = function(keysOrDefaults) { return getValues(keysOrDefaults); };
                    }
                    if (hasGrant("GM.setValues")) {
                        gmObject.setValues = function(values) { setValues(values || {}); return Promise.resolve(); };
                        legacy.GM_setValues = function(values) { setValues(values || {}); };
                    }
                    if (hasGrant("GM.deleteValues")) {
                        gmObject.deleteValues = function(keys) { deleteValues(keys); return Promise.resolve(); };
                        legacy.GM_deleteValues = function(keys) { deleteValues(keys); };
                    }
                    if (hasGrant("GM.addValueChangeListener")) {
                        gmObject.addValueChangeListener = function(key, callback) { return Promise.resolve().then(() => addValueChangeListener(key, callback)); };
                        legacy.GM_addValueChangeListener = addValueChangeListener;
                    }
                    if (hasGrant("GM.removeValueChangeListener")) {
                        gmObject.removeValueChangeListener = function(listenerId) { removeValueChangeListener(listenerId); return Promise.resolve(); };
                        legacy.GM_removeValueChangeListener = removeValueChangeListener;
                    }
                    if (hasGrant("GM.addStyle")) {
                        gmObject.addStyle = function(cssText) { return runtime.addStyle(cssText); };
                        legacy.GM_addStyle = function(cssText) { return runtime.addStyle(cssText); };
                    }
                    if (hasGrant("GM.addElement")) {
                        gmObject.addElement = function(arg1, arg2, arg3) { return Promise.resolve().then(() => runtime.addElement(arg1, arg2, arg3)); };
                        legacy.GM_addElement = runtime.addElement;
                    }
                    if (hasGrant("GM.getResourceText")) {
                        gmObject.getResourceText = function(name) { const resource = resourceMap[String(name)] || {}; return Promise.resolve(resource.text || ""); };
                        legacy.GM_getResourceText = function(name) { const resource = resourceMap[String(name)] || {}; return resource.text || ""; };
                    }
                    if (hasGrant("GM.getResourceURL")) {
                        gmObject.getResourceURL = function(name) { const resource = resourceMap[String(name)] || {}; return Promise.resolve(resource.dataUrl || ""); };
                        legacy.GM_getResourceURL = function(name) { const resource = resourceMap[String(name)] || {}; return resource.dataUrl || ""; };
                    }
                    if (hasGrant("GM.log")) {
                        gmObject.log = function() { runtime.log("info", Array.from(arguments).map((value) => String(value)).join(" "), script.scriptId, location.href); return Promise.resolve(); };
                        legacy.GM_log = function() { runtime.log("info", Array.from(arguments).map((value) => String(value)).join(" "), script.scriptId, location.href); };
                    }
                    if (hasGrant("GM.xmlHttpRequest")) {
                        gmObject.xmlHttpRequest = function(details) { return xmlHttpRequest(details); };
                        legacy.GM_xmlhttpRequest = function(details) { return xmlHttpRequest(details); };
                        legacy.GM_xmlHttpRequest = legacy.GM_xmlhttpRequest;
                    }
                    if (hasGrant("GM.download")) {
                        gmObject.download = function(arg1, arg2) { return Promise.resolve().then(() => download(arg1, arg2).promise).then(() => undefined); };
                        legacy.GM_download = function(arg1, arg2) { return download(arg1, arg2); };
                    }
                    if (hasGrant("GM.openInTab")) {
                        gmObject.openInTab = function(url, options) { return createOpenTabHandle(url, options, true); };
                        legacy.GM_openInTab = function(url, options) { return createOpenTabHandle(url, options, false); };
                    }
                    if (hasGrant("GM.registerMenuCommand")) {
                        gmObject.registerMenuCommand = function(caption, callback, optionsOrAccessKey) { return Promise.resolve().then(() => registerMenuCommand(caption, callback, optionsOrAccessKey)); };
                        legacy.GM_registerMenuCommand = registerMenuCommand;
                    }
                    if (hasGrant("GM.unregisterMenuCommand")) {
                        gmObject.unregisterMenuCommand = function(commandId) { unregisterMenuCommand(commandId); return Promise.resolve(); };
                        legacy.GM_unregisterMenuCommand = unregisterMenuCommand;
                    }
                    if (hasGrant("GM.setClipboard")) {
                        gmObject.setClipboard = function(text, info, callback) { return Promise.resolve().then(() => setClipboardValue(text, info, callback)).then(() => undefined); };
                        legacy.GM_setClipboard = function(text, info, callback) {
                            setClipboardValue(text, info, callback)
                                .catch((error) => runtime.log("error", String(error), script.scriptId, location.href));
                        };
                    }
                    if (hasGrant("GM.notification")) {
                        gmObject.notification = function(arg1, arg2, arg3, arg4) { return Promise.resolve().then(() => showNotification(arg1, arg2, arg3, arg4)).then(() => undefined); };
                        legacy.GM_notification = function(arg1, arg2, arg3, arg4) {
                            showNotification(arg1, arg2, arg3, arg4)
                                .catch((error) => runtime.log("error", String(error), script.scriptId, location.href));
                        };
                    }
                    if (hasGrant("GM.getTab")) {
                        gmObject.getTab = function() { return getTab(); };
                        legacy.GM_getTab = getTab;
                    }
                    if (hasGrant("GM.saveTab")) {
                        gmObject.saveTab = function(tab) { return saveTab(tab); };
                        legacy.GM_saveTab = saveTab;
                    }
                    if (hasGrant("GM.getTabs")) {
                        gmObject.getTabs = function() { return getTabs(); };
                        legacy.GM_getTabs = getTabs;
                    }
                    if (hasGrant("GM.cookie")) {
                        gmObject.cookie = cookieApi;
                        legacy.GM_cookie = cookieApi;
                    }
                    if (hasGrant("GM.audio")) {
                        gmObject.audio = audioApi;
                        legacy.GM_audio = audioApi;
                    }
                    if (hasGrant("GM.webRequest")) {
                        gmObject.webRequest = function(rules, listener) { return createWebRequestHandle(rules, listener, true, "runtime"); };
                        legacy.GM_webRequest = function(rules, listener) { return createWebRequestHandle(rules, listener, false, "runtime"); };
                    }
                    if (Array.isArray(metadata.webRequestRules) && metadata.webRequestRules.length > 0) {
                        createWebRequestHandle(metadata.webRequestRules, null, false, "metadata");
                    }
                    const usesProxyWindow = windowFeatures.focus || windowFeatures.close || windowFeatures.onUrlChange;
                    const exposedWindow = usesProxyWindow ? windowProxy : window;
                    const exposedClose =
                        hasGrant("window.close")
                            ? function() { return runtime.request("gm_close_tab", { sessionId: script.sessionId }); }
                            : (typeof window.close === "function" ? window.close.bind(window) : undefined);
                    const exposedFocus =
                        hasGrant("window.focus")
                            ? function() { return runtime.request("gm_focus_tab", { sessionId: script.sessionId }); }
                            : (typeof window.focus === "function" ? window.focus.bind(window) : undefined);

                    try {
                        runtime.post("script_status", {
                            scriptId: script.scriptId,
                            state: "running",
                            pageUrl: location.href
                        });
                        const before = script.requires || [];
                        const source =
                            String(before.join("\n")) +
                            "\n" +
                            String(script.code || "") +
                            "\n//# sourceURL=userscript:" + encodeURIComponent(String(script.name || "script"));
                        const executor = new Function(
                            "GM",
                            "GM_info",
                            "GM_getValue",
                            "GM_setValue",
                            "GM_deleteValue",
                            "GM_listValues",
                            "GM_getValues",
                            "GM_setValues",
                            "GM_deleteValues",
                            "GM_addValueChangeListener",
                            "GM_removeValueChangeListener",
                            "GM_addStyle",
                            "GM_addElement",
                            "GM_getResourceText",
                            "GM_getResourceURL",
                            "GM_log",
                            "GM_xmlhttpRequest",
                            "GM_xmlHttpRequest",
                            "GM_download",
                            "GM_openInTab",
                            "GM_registerMenuCommand",
                            "GM_unregisterMenuCommand",
                            "GM_setClipboard",
                            "GM_notification",
                            "GM_getTab",
                            "GM_saveTab",
                            "GM_getTabs",
                            "GM_cookie",
                            "GM_audio",
                            "GM_webRequest",
                            "unsafeWindow",
                            "window",
                            "close",
                            "focus",
                            source
                        );
                        executor(
                            gmObject,
                            legacy.GM_info,
                            legacy.GM_getValue,
                            legacy.GM_setValue,
                            legacy.GM_deleteValue,
                            legacy.GM_listValues,
                            legacy.GM_getValues,
                            legacy.GM_setValues,
                            legacy.GM_deleteValues,
                            legacy.GM_addValueChangeListener,
                            legacy.GM_removeValueChangeListener,
                            legacy.GM_addStyle,
                            legacy.GM_addElement,
                            legacy.GM_getResourceText,
                            legacy.GM_getResourceURL,
                            legacy.GM_log,
                            legacy.GM_xmlhttpRequest,
                            legacy.GM_xmlHttpRequest,
                            legacy.GM_download,
                            legacy.GM_openInTab,
                            legacy.GM_registerMenuCommand,
                            legacy.GM_unregisterMenuCommand,
                            legacy.GM_setClipboard,
                            legacy.GM_notification,
                            legacy.GM_getTab,
                            legacy.GM_saveTab,
                            legacy.GM_getTabs,
                            legacy.GM_cookie,
                            legacy.GM_audio,
                            legacy.GM_webRequest,
                            hasGrant("unsafeWindow") ? window : undefined,
                            exposedWindow,
                            exposedClose,
                            exposedFocus
                        );
                        runtime.post("script_status", {
                            scriptId: script.scriptId,
                            state: "success",
                            pageUrl: location.href
                        });
                    } catch (error) {
                        const message = String(error && (error.stack || error.message || error) || "userscript execution failed");
                        runtime.post("script_status", {
                            scriptId: script.scriptId,
                            state: "error",
                            message: message,
                            pageUrl: location.href
                        });
                        runtime.post("runtime_log", {
                            scriptId: script.scriptId,
                            level: "error",
                            message: message,
                            pageUrl: location.href
                        });
                    }
                },
                scheduleScripts(scripts) {
                    const grouped = {
                        "document-start": [],
                        "document-end": [],
                        "document-idle": []
                    };
                    (scripts || []).forEach((script) => {
                        const slot = grouped[String(script.runAt || "document-end")] || grouped["document-end"];
                        slot.push(script);
                    });
                    grouped["document-start"].forEach(runtime.installScript);
                    const runEnd = function() {
                        grouped["document-end"].forEach(runtime.installScript);
                    };
                    const runIdle = function() {
                        const idleRunner = function() {
                            grouped["document-idle"].forEach(runtime.installScript);
                        };
                        if (typeof requestIdleCallback === "function") {
                            requestIdleCallback(idleRunner);
                        } else {
                            setTimeout(idleRunner, 0);
                        }
                    };
                    if (document.readyState === "loading") {
                        document.addEventListener("DOMContentLoaded", runEnd, { once: true });
                    } else {
                        runEnd();
                    }
                    if (document.readyState === "complete") {
                        runIdle();
                    } else {
                        window.addEventListener("load", runIdle, { once: true });
                    }
                },
                handleXhrEvent(message) {
                    const request = runtime.xhrRequests.get(message.requestId);
                    if (!request) {
                        return;
                    }
                    const response = message.payload || {};
                    const callbackName = "on" + String(message.eventType || "");
                    const callback = request[callbackName];
                    if (typeof callback === "function") {
                        let resolvedResponse = typeof response.response !== "undefined" ? response.response : response.responseText;
                        const resolvedResponseType = String(response.responseType || request.responseType || "").toLowerCase();
                        if (response.responseEncoding === "base64") {
                            const bytes = runtime.decodeBinaryResponse(response.response);
                            if (resolvedResponseType === "arraybuffer") {
                                resolvedResponse = bytes.buffer.slice(0);
                            } else if (resolvedResponseType === "blob") {
                                resolvedResponse = new Blob([bytes]);
                            } else {
                                resolvedResponse = bytes;
                            }
                        }
                        callback({
                            status: Number(response.status || 0),
                            statusText: String(response.statusText || ""),
                            readyState: Number(response.readyState || 4),
                            responseHeaders: runtime.buildResponseHeaders(response.headers),
                            finalUrl: String(response.finalUrl || ""),
                            responseText: typeof response.responseText === "string" ? response.responseText : "",
                            response: resolvedResponse,
                            loaded: Number(response.loaded || 0),
                            total: Number(response.total || 0),
                            lengthComputable: Number(response.total || 0) > 0,
                            context: request.context
                        });
                    }
                    if (message.terminal) {
                        runtime.xhrRequests.delete(message.requestId);
                    }
                },
                invokeMenuCommand(commandId) {
                    const callback = runtime.menuCallbacks.get(String(commandId || ""));
                    if (typeof callback === "function") {
                        callback({
                            type: "menucommand",
                            commandId: String(commandId || ""),
                            trusted: true
                        });
                    }
                }
            };

            bridge.onmessage = function(event) {
                try {
                    const message = JSON.parse(String(event && event.data || "{}"));
                    if (message.type === "rpc_response") {
                        runtime.resolvePending(message);
                        return;
                    }
                    if (message.type === "xhr_event") {
                        runtime.handleXhrEvent(message);
                        return;
                    }
                    runtime.dispatchHostEvent(message);
                } catch (error) {
                    runtime.log("error", String(error && (error.stack || error.message || error) || "userscript bridge parse failed"), 0, location.href);
                }
            };

            window.__operitUserscriptRuntime = runtime;
            runtime.installUrlChangeHooks();
            runtime.post("menu_reset", {});
            runtime.request("bootstrap_request", {
                href: String(location.href || ""),
                isTopFrame: window.top === window
            }).then(function(payload) {
                const parsed = JSON.parse(String((payload && payload.payloadJson) || "{\"scripts\":[]}"));
                runtime.scheduleScripts(parsed.scripts || []);
            }).catch(function(error) {
                runtime.log("error", String(error && (error.stack || error.message || error) || "userscript bootstrap failed"), 0, location.href);
            });
        })();
        """.trimIndent()
}

package com.ai.assistance.operit.core.tools.javascript

internal fun buildComposeDslContextBridgeDefinition(): String {
    return """
        var OperitComposeDslRuntime = (function() {
            function cloneObject(input) {
                if (!input || typeof input !== 'object' || Array.isArray(input)) {
                    return {};
                }
                var out = {};
                for (var key in input) {
                    if (Object.prototype.hasOwnProperty.call(input, key)) {
                        out[key] = input[key];
                    }
                }
                return out;
            }

            function normalizeChildren(children) {
                if (!children) {
                    return [];
                }
                if (Array.isArray(children)) {
                    return children;
                }
                return [children];
            }

            function isComposeNodeLike(value) {
                return !!(
                    value &&
                    typeof value === 'object' &&
                    value.__composeNode === true &&
                    typeof value.type === 'string'
                );
            }

            function flattenComposeSlotValue(value, out) {
                if (value == null) {
                    return;
                }
                if (Array.isArray(value)) {
                    for (var i = 0; i < value.length; i += 1) {
                        flattenComposeSlotValue(value[i], out);
                    }
                    return;
                }
                if (isComposeNodeLike(value)) {
                    out.push(value);
                }
            }

            function normalizeSlotChildren(value) {
                var out = [];
                flattenComposeSlotValue(value, out);
                return out;
            }

            function invokeNative(methodName, args) {
                try {
                    if (
                        typeof NativeInterface === 'undefined' ||
                        !NativeInterface ||
                        typeof NativeInterface[methodName] !== 'function'
                    ) {
                        return undefined;
                    }
                    return NativeInterface[methodName].apply(NativeInterface, args || []);
                } catch (e) {
                    console.error('Native bridge call failed for ' + methodName + ':', e);
                    return undefined;
                }
            }

            function __operit_define_unit_getter(unitName) {
                try {
                    if (!unitName || typeof unitName !== 'string') {
                        return;
                    }
                    if (Object.prototype.hasOwnProperty.call(Number.prototype, unitName)) {
                        return;
                    }
                    Object.defineProperty(Number.prototype, unitName, {
                        configurable: true,
                        enumerable: false,
                        get: function() {
                            return { __unit: unitName, value: this.valueOf() };
                        }
                    });
                } catch (e) {
                }
            }

            function __operit_define_array_unit_getter(unitName) {
                try {
                    if (!unitName || typeof unitName !== 'string') {
                        return;
                    }
                    if (Object.prototype.hasOwnProperty.call(Array.prototype, unitName)) {
                        return;
                    }
                    Object.defineProperty(Array.prototype, unitName, {
                        configurable: true,
                        enumerable: false,
                        get: function() {
                            if (!Array.isArray(this)) {
                                return [];
                            }
                            return this.map(function(item) {
                                return { __unit: unitName, value: item };
                            });
                        }
                    });
                } catch (e) {
                }
            }

            __operit_define_unit_getter('px');
            __operit_define_unit_getter('dp');
            __operit_define_unit_getter('fraction');
            __operit_define_array_unit_getter('px');
            __operit_define_array_unit_getter('dp');
            __operit_define_array_unit_getter('fraction');

            function formatTemplateInternal(template, values) {
                var result = String(template || '');
                var source = values && typeof values === 'object' ? values : {};
                for (var key in source) {
                    if (Object.prototype.hasOwnProperty.call(source, key)) {
                        var value = source[key];
                        var placeholder = '{' + key + '}';
                        result = result.split(placeholder).join(value == null ? '' : String(value));
                    }
                }
                return result;
            }

            function parseJsonValue(rawValue) {
                if (rawValue === undefined || rawValue === null) {
                    return undefined;
                }
                if (typeof rawValue !== 'string') {
                    return rawValue;
                }
                var trimmed = rawValue.trim();
                if (!trimmed) {
                    return undefined;
                }
                try {
                    return JSON.parse(trimmed);
                } catch (e) {
                    return rawValue;
                }
            }

            function unwrapNativeResult(rawValue, label) {
                var parsed = parseJsonValue(rawValue);
                if (
                    parsed &&
                    typeof parsed === 'object' &&
                    parsed.success === false
                ) {
                    throw createUserFacingError(parsed.message, parsed);
                }
                if (
                    parsed &&
                    typeof parsed === 'object' &&
                    Object.prototype.hasOwnProperty.call(parsed, 'data')
                ) {
                    return parsed.data;
                }
                return parsed;
            }

            function createUserFacingError(message, detailData) {
                return {
                    name: 'Error',
                    message: String(message || '').trim(),
                    data: detailData,
                    toString: function() {
                        return this.message;
                    }
                };
            }

            function createContext(runtimeOptions) {
                var options = runtimeOptions && typeof runtimeOptions === 'object' ? runtimeOptions : {};
                var runtime = {
                    stateStore: cloneObject(options.state),
                    memoStore: cloneObject(options.memo),
                    moduleSpec:
                        options.moduleSpec && typeof options.moduleSpec === 'object'
                            ? options.moduleSpec
                            : {},
                    packageName: String(options.packageName || options.__operit_ui_package_name || ''),
                    toolPkgId: String(options.toolPkgId || options.__operit_ui_toolpkg_id || ''),
                    uiModuleId: String(options.uiModuleId || options.__operit_ui_module_id || ''),
                    routeInstanceId: String(options.routeInstanceId || options.__operit_route_instance_id || ''),
                    executionContextKey: String(
                        options.executionContextKey || options.__operit_compose_execution_context_key || ''
                    ),
                    callRuntime:
                        options.__operit_call_runtime && typeof options.__operit_call_runtime === 'object'
                            ? options.__operit_call_runtime
                            : null,
                    actionStore: {},
                    actionCounter: 0,
                    stateChangeListeners: [],
                    stateChangeScheduled: false,
                    stateDirty: false,
                    pendingStateChangePromise: null
                };

                function registerAction(handler) {
                    runtime.actionCounter += 1;
                    var actionId = '__action_' + runtime.actionCounter;
                    runtime.actionStore[actionId] = handler;
                    return actionId;
                }

                function notifyStateChanged() {
                    runtime.stateDirty = true;
                    if (runtime.stateChangeScheduled) {
                        return;
                    }
                    runtime.stateChangeScheduled = true;
                    runtime.pendingStateChangePromise = Promise.resolve().then(function() {
                        try {
                            runtime.stateChangeScheduled = false;
                            if (!runtime.stateDirty) {
                                return;
                            }
                            runtime.stateDirty = false;
                            flushStateChangeListeners();
                        } finally {
                            runtime.pendingStateChangePromise = null;
                        }
                    });
                }

                function flushStateChangeListeners() {
                    if (!runtime.stateChangeListeners || runtime.stateChangeListeners.length <= 0) {
                        return;
                    }
                    var listeners = runtime.stateChangeListeners.slice();
                    for (var i = 0; i < listeners.length; i += 1) {
                        try {
                            listeners[i]();
                        } catch (e) {
                            try {
                                console.warn('compose_dsl state listener failed:', e);
                            } catch (__ignore) {
                            }
                        }
                    }
                }

                function subscribeStateChange(listener) {
                    if (typeof listener !== 'function') {
                        return function() {};
                    }
                    runtime.stateChangeListeners.push(listener);
                    var active = true;
                    return function() {
                        if (!active) {
                            return;
                        }
                        active = false;
                        var index = runtime.stateChangeListeners.indexOf(listener);
                        if (index >= 0) {
                            runtime.stateChangeListeners.splice(index, 1);
                        }
                    };
                }

                function flushPendingStateChanges() {
                    if (runtime.pendingStateChangePromise && typeof runtime.pendingStateChangePromise.then === 'function') {
                        return runtime.pendingStateChangePromise;
                    }
                    return Promise.resolve();
                }

                function normalizePropValue(value) {
                    if (typeof value === 'function') {
                        return { __actionId: registerAction(value) };
                    }
                    if (Array.isArray(value)) {
                        return value.map(function(item) {
                            return normalizePropValue(item);
                        });
                    }
                    if (value && typeof value === 'object') {
                        var normalized = {};
                        for (var key in value) {
                            if (Object.prototype.hasOwnProperty.call(value, key)) {
                                normalized[key] = normalizePropValue(value[key]);
                            }
                        }
                        return normalized;
                    }
                    return value;
                }

                function createNode(type, props, children) {
                    var rawProps = props && typeof props === 'object' ? props : {};
                    var normalizedProps = {};
                    var normalizedSlots = {};
                    for (var key in rawProps) {
                        if (Object.prototype.hasOwnProperty.call(rawProps, key)) {
                            var rawValue = rawProps[key];
                            var slotChildren = normalizeSlotChildren(rawValue);
                            if (slotChildren.length > 0) {
                                normalizedSlots[key] = slotChildren;
                                continue;
                            }
                            normalizedProps[key] = normalizePropValue(rawValue);
                        }
                    }
                    var normalizedChildren = normalizeChildren(children);
                    return {
                        __composeNode: true,
                        type: String(type || ''),
                        props: normalizedProps,
                        children: normalizedChildren,
                        slots: normalizedSlots
                    };
                }

                function resolvePackageName(value) {
                    var name = String(value || runtime.packageName || '').trim();
                    return name;
                }

                function useState(key, initialValue) {
                    var stateKey = String(key || '').trim();
                    if (!stateKey) {
                        throw new Error('useState key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.stateStore, stateKey)) {
                        runtime.stateStore[stateKey] = initialValue;
                    }
                    return [
                        runtime.stateStore[stateKey],
                        function(nextValue) {
                            runtime.stateStore[stateKey] = nextValue;
                            notifyStateChanged();
                        }
                    ];
                }

                function useMutable(key, initialValue) {
                    var stateKey = String(key || '').trim();
                    if (!stateKey) {
                        throw new Error('useMutable key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.memoStore, stateKey)) {
                        runtime.memoStore[stateKey] = initialValue;
                    }
                    return [
                        runtime.memoStore[stateKey],
                        function(nextValue) {
                            runtime.memoStore[stateKey] = nextValue;
                        }
                    ];
                }

                function useRef(key, initialValue) {
                    var stateKey = String(key || '').trim();
                    if (!stateKey) {
                        throw new Error('useRef key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.memoStore, stateKey)) {
                        runtime.memoStore[stateKey] = { current: initialValue };
                    }
                    return runtime.memoStore[stateKey];
                }

                function useMemo(key, factory, deps) {
                    var memoKey = String(key || '').trim();
                    if (!memoKey) {
                        throw new Error('useMemo key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.memoStore, memoKey)) {
                        runtime.memoStore[memoKey] =
                            typeof factory === 'function' ? factory() : factory;
                    }
                    return runtime.memoStore[memoKey];
                }

                function normalizeToolName(targetPackage, toolName) {
                    var basePackage = String(targetPackage || '').trim();
                    var normalizedTool = String(toolName || '').trim();
                    if (!normalizedTool) {
                        return '';
                    }
                    if (normalizedTool.indexOf(':') >= 0 || !basePackage) {
                        return normalizedTool;
                    }
                    return basePackage + ':' + normalizedTool;
                }

                function createNodeFactory(type) {
                    var nodeType = String(type || '');
                    return function(props, children) {
                        return createNode(nodeType, props, children);
                    };
                }

                function createWebViewController(key) {
                    var controllerKey = String(key || '').trim();
                    if (!controllerKey) {
                        throw new Error('webview controller key is required');
                    }
                    var descriptor = {
                        __composeWebViewController: true,
                        key: controllerKey,
                        routeInstanceId: runtime.routeInstanceId || '',
                        executionContextKey: runtime.executionContextKey || ''
                    };
                    function invokeControllerCommand(command, payload) {
                        return unwrapNativeResult(
                            invokeNative('composeWebViewControllerCommand', [
                                JSON.stringify({
                                    command: String(command || ''),
                                    key: controllerKey,
                                    routeInstanceId: runtime.routeInstanceId || '',
                                    executionContextKey: runtime.executionContextKey || '',
                                    payload: normalizePropValue(
                                        payload && typeof payload === 'object'
                                            ? payload
                                            : {}
                                    )
                                })
                            ]),
                            'compose webview controller command failed'
                        );
                    }
                    function nextWebViewControllerCallbackId() {
                        return '__operit_compose_webview_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10);
                    }
                    function invokeControllerCommandSuspend(command, payload) {
                        if (typeof Promise !== 'function') {
                            throw new Error('Promise is required for suspend webview controller command');
                        }
                        return new Promise(function(resolve, reject) {
                            if (
                                typeof NativeInterface === 'undefined' ||
                                !NativeInterface ||
                                typeof NativeInterface.composeWebViewControllerCommandSuspend !== 'function'
                            ) {
                                reject(createUserFacingError('NativeInterface.composeWebViewControllerCommandSuspend is unavailable'));
                                return;
                            }
                            var root = typeof globalThis !== 'undefined'
                                ? globalThis
                                : (typeof window !== 'undefined' ? window : this);
                            var callbackTarget = typeof window !== 'undefined' ? window : root;
                            var callbackId = nextWebViewControllerCallbackId();
                            callbackTarget[callbackId] = function(result, isError) {
                                delete callbackTarget[callbackId];
                                try {
                                    if (isError) {
                                        reject(createUserFacingError(result.message, result));
                                        return;
                                    }
                                    resolve(
                                        unwrapNativeResult(
                                            result,
                                            'compose webview controller command failed'
                                        )
                                    );
                                } catch (callbackError) {
                                    reject(callbackError);
                                }
                            };
                            try {
                                NativeInterface.composeWebViewControllerCommandSuspend(
                                    JSON.stringify({
                                        command: String(command || ''),
                                        key: controllerKey,
                                        routeInstanceId: runtime.routeInstanceId || '',
                                        executionContextKey: runtime.executionContextKey || '',
                                        payload: normalizePropValue(
                                            payload && typeof payload === 'object'
                                                ? payload
                                            : {}
                                        )
                                    }),
                                    callbackId
                                );
                            } catch (invokeError) {
                                delete callbackTarget[callbackId];
                                reject(invokeError);
                            }
                        });
                    }
                    function defineMethod(target, name, handler) {
                        Object.defineProperty(target, name, {
                            configurable: false,
                            enumerable: false,
                            writable: false,
                            value: handler
                        });
                    }

                    var controller = cloneObject(descriptor);
                    defineMethod(controller, 'toJSON', function() {
                        return cloneObject(descriptor);
                    });
                    defineMethod(controller, 'loadUrl', function(url, headers) {
                        var finalUrl = String(url || '').trim();
                        if (!finalUrl) {
                            throw new Error('webview controller loadUrl requires a non-empty url');
                        }
                        invokeControllerCommand('loadUrl', {
                            url: finalUrl,
                            headers: headers && typeof headers === 'object' ? headers : {}
                        });
                    });
                    defineMethod(controller, 'loadHtml', function(html, options) {
                        invokeControllerCommand('loadHtml', {
                            html: html == null ? '' : String(html),
                            options: options && typeof options === 'object' ? options : {}
                        });
                    });
                    defineMethod(controller, 'reload', function() {
                        invokeControllerCommand('reload', {});
                    });
                    defineMethod(controller, 'stopLoading', function() {
                        invokeControllerCommand('stopLoading', {});
                    });
                    defineMethod(controller, 'goBack', function() {
                        invokeControllerCommand('goBack', {});
                    });
                    defineMethod(controller, 'goForward', function() {
                        invokeControllerCommand('goForward', {});
                    });
                    defineMethod(controller, 'clearHistory', function() {
                        invokeControllerCommand('clearHistory', {});
                    });
                    defineMethod(controller, 'evaluateJavascript', function(script) {
                        return Promise.resolve(
                            invokeControllerCommandSuspend('evaluateJavascript', {
                                script: script == null ? '' : String(script)
                            })
                        );
                    });
                    defineMethod(controller, 'getState', function() {
                        return invokeControllerCommand('getState', {});
                    });
                    defineMethod(controller, 'addJavascriptInterface', function(name, object) {
                        var interfaceName = String(name || '').trim();
                        if (!interfaceName) {
                            throw new Error('webview controller addJavascriptInterface requires a non-empty name');
                        }
                        if (!object || typeof object !== 'object' || Array.isArray(object)) {
                            throw new Error('webview controller addJavascriptInterface requires an object');
                        }
                        invokeControllerCommand('addJavascriptInterface', {
                            name: interfaceName,
                            object: object
                        });
                    });
                    defineMethod(controller, 'removeJavascriptInterface', function(name) {
                        var interfaceName = String(name || '').trim();
                        if (!interfaceName) {
                            throw new Error('webview controller removeJavascriptInterface requires a non-empty name');
                        }
                        invokeControllerCommand('removeJavascriptInterface', {
                            name: interfaceName
                        });
                    });
                    return controller;
                }

                function createModifierProxy(ops) {
                    var chain = Array.isArray(ops) ? ops.slice() : [];
                    var target = {
                        __modifierOps: chain
                    };
                    return new Proxy(target, {
                        get: function(_target, key) {
                            if (key === '__modifierOps') {
                                return chain;
                            }
                            if (key === 'toJSON') {
                                return function() {
                                    return { __modifierOps: chain.slice() };
                                };
                            }
                            if (key === 'then') {
                                return undefined;
                            }
                            if (typeof key !== 'string') {
                                return undefined;
                            }
                            return function() {
                                var args = [];
                                for (var i = 0; i < arguments.length; i += 1) {
                                    args.push(normalizePropValue(arguments[i]));
                                }
                                var next = chain.slice();
                                next.push({
                                    name: key,
                                    args: args
                                });
                                return createModifierProxy(next);
                            };
                        }
                    });
                }

                function createColorToken(name, alpha) {
                    return {
                        __colorToken: String(name || ''),
                        alpha: typeof alpha === 'number' ? alpha : undefined,
                        copy: function(options) {
                            var nextAlpha = options && typeof options.alpha === 'number' ? options.alpha : alpha;
                            return createColorToken(name, nextAlpha);
                        }
                    };
                }

                var MaterialTheme = {
                    colorScheme: new Proxy({}, {
                        get: function(_target, key) {
                            if (typeof key !== 'string') {
                                return undefined;
                            }
                            return createColorToken(key);
                        }
                    })
                };

                var uiProxy = new Proxy({}, {
                    get: function(_target, key) {
                        if (typeof key !== 'string') {
                            return undefined;
                        }
                        return createNodeFactory(key);
                    }
                });

                var ctx = {
                    MaterialTheme: MaterialTheme,
                    useState: useState,
                    useMutable: useMutable,
                    useRef: useRef,
                    useMemo: useMemo,
                    callTool: function(toolName, params) {
                        return toolCall(toolName, params || {});
                    },
                    toolCall: function() {
                        return toolCall.apply(null, arguments);
                    },
                    getEnv: function(key) {
                        if (runtime.callRuntime && typeof runtime.callRuntime.getEnv === 'function') {
                            return runtime.callRuntime.getEnv(key);
                        }
                        return undefined;
                    },
                    setEnv: function(key, value) {
                        invokeNative('setEnv', [
                            String(key || ''),
                            value === undefined || value === null ? '' : String(value)
                        ]);
                        return Promise.resolve();
                    },
                    setEnvs: function(values) {
                        var payload = values && typeof values === 'object' ? values : {};
                        invokeNative('setEnvs', [JSON.stringify(payload)]);
                        return Promise.resolve();
                    },
                    navigate: function(route, args) {
                        var routeId = String(route || '').trim();
                        if (!routeId) {
                            return Promise.reject(createUserFacingError('route is required'));
                        }
                        invokeNative('navigateToRoute', [
                            routeId,
                            JSON.stringify(args && typeof args === 'object' ? args : {})
                        ]);
                        return Promise.resolve();
                    },
                    listRoutes: function() {
                        var json = invokeNative('listRoutes', []);
                        if (typeof json !== 'string' || !json.trim()) {
                            return [];
                        }
                        try {
                            var parsed = JSON.parse(json);
                            return Array.isArray(parsed) ? parsed : [];
                        } catch (e) {
                            return [];
                        }
                    },
                    getHostRoutes: function() {
                        var json = invokeNative('listHostRoutes', []);
                        if (typeof json !== 'string' || !json.trim()) {
                            return [];
                        }
                        try {
                            var parsed = JSON.parse(json);
                            return Array.isArray(parsed) ? parsed : [];
                        } catch (e) {
                            return [];
                        }
                    },
                    showToast: function(message) {
                        return toolCall('toast', { message: String(message || '') });
                    },
                    reportError: function(error) {
                        console.error('compose_dsl reportError:', error);
                        return Promise.resolve();
                    },
                    createWebViewController: function(key) {
                        return createWebViewController(key);
                    },
                    openFilePicker: function(options) {
                        if (typeof Promise !== 'function') {
                            throw new Error('Promise is required for openFilePicker');
                        }
                        return new Promise(function(resolve, reject) {
                            if (
                                typeof NativeInterface === 'undefined' ||
                                !NativeInterface ||
                                typeof NativeInterface.composeOpenFilePickerSuspend !== 'function'
                            ) {
                                reject(createUserFacingError('NativeInterface.composeOpenFilePickerSuspend is unavailable'));
                                return;
                            }
                            var root = typeof globalThis !== 'undefined'
                                ? globalThis
                                : (typeof window !== 'undefined' ? window : this);
                            var callbackTarget = typeof window !== 'undefined' ? window : root;
                            var callbackId =
                                '__operit_compose_file_picker_' +
                                Date.now() +
                                '_' +
                                Math.random().toString(36).slice(2, 10);
                            callbackTarget[callbackId] = function(result, isError) {
                                delete callbackTarget[callbackId];
                                try {
                                    if (isError) {
                                        reject(createUserFacingError(result.message, result));
                                        return;
                                    }
                                    resolve(
                                        unwrapNativeResult(
                                            result,
                                            'openFilePicker failed'
                                        )
                                    );
                                } catch (callbackError) {
                                    reject(callbackError);
                                }
                            };
                            try {
                                NativeInterface.composeOpenFilePickerSuspend(
                                    JSON.stringify({
                                        routeInstanceId: runtime.routeInstanceId || '',
                                        executionContextKey: runtime.executionContextKey || '',
                                        options: normalizePropValue(
                                            options && typeof options === 'object'
                                                ? options
                                                : {}
                                        )
                                    }),
                                    callbackId
                                );
                            } catch (invokeError) {
                                delete callbackTarget[callbackId];
                                reject(invokeError);
                            }
                        });
                    },
                    measureText: function(options) {
                        var payload = options && typeof options === 'object' ? options : {};
                        var json = invokeNative('measureComposeText', [JSON.stringify(payload)]);
                        if (typeof json !== 'string' || !json.trim()) {
                            throw new Error('measureText failed to return data');
                        }
                        return JSON.parse(json);
                    },
                    getModuleSpec: function() {
                        return runtime.moduleSpec;
                    },
                    formatTemplate: function(template, values) {
                        return formatTemplateInternal(template, values);
                    },
                    getCurrentPackageName: function() {
                        return runtime.packageName || undefined;
                    },
                    getCurrentToolPkgId: function() {
                        return runtime.toolPkgId || undefined;
                    },
                    getCurrentUiModuleId: function() {
                        return runtime.uiModuleId || undefined;
                    },
                    isPackageImported: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve(false);
                        }
                        var result = invokeNative('isPackageImported', [target]);
                        if (result === true || result === false || result === 'true' || result === 'false') {
                            return Promise.resolve(result === true || result === 'true');
                        }
                        return toolCall('is_package_imported', { package_name: target });
                    },
                    importPackage: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('importPackage', [target]);
                        if (result !== undefined && result !== null) {
                            return Promise.resolve(result);
                        }
                        return toolCall('import_package', { package_name: target });
                    },
                    removePackage: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('removePackage', [target]);
                        if (result !== undefined && result !== null) {
                            return Promise.resolve(result);
                        }
                        return toolCall('remove_package', { package_name: target });
                    },
                    usePackage: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('usePackage', [target]);
                        if (result !== undefined && result !== null) {
                            return Promise.resolve(result);
                        }
                        return toolCall('use_package', { package_name: target });
                    },
                    listImportedPackages: function() {
                        var json = invokeNative('listImportedPackagesJson', []);
                        if (typeof json === 'string' && json.trim()) {
                            try {
                                return Promise.resolve(JSON.parse(json));
                            } catch (e) {
                                return Promise.resolve([]);
                            }
                        }
                        return toolCall('list_imported_packages', {});
                    },
                    resolveToolName: function(request) {
                        var req = request && typeof request === 'object' ? request : {};
                        var packageName = String(req.packageName || runtime.packageName || '');
                        var subpackageId = String(req.subpackageId || '');
                        var toolName = String(req.toolName || '');
                        var preferImported = req.preferImported === false ? 'false' : 'true';
                        if (!toolName) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('resolveToolName', [
                            packageName,
                            subpackageId,
                            toolName,
                            preferImported
                        ]);
                        if (typeof result === 'string' && result.trim()) {
                            return Promise.resolve(result);
                        }
                        return Promise.resolve(normalizeToolName(packageName, toolName));
                    },
                    h: function(type, props, children) {
                        return createNode(String(type || ''), props, children);
                    },
                    Modifier: createModifierProxy([]),
                    UI: uiProxy
                };

                return {
                    ctx: ctx,
                    state: runtime.stateStore,
                    memo: runtime.memoStore,
                    setCallRuntime: function(nextCallRuntime) {
                        if (nextCallRuntime && typeof nextCallRuntime === 'object') {
                            runtime.callRuntime = nextCallRuntime;
                        }
                    },
                    invokeAction: function(actionId, payload, payloadIsArgumentList) {
                        var id = String(actionId || '').trim();
                        if (!id) {
                            throw new Error('compose action id is required');
                        }
                        var handler = runtime.actionStore[id];
                        if (typeof handler !== 'function') {
                            throw new Error('compose action not found: ' + id);
                        }
                        // A WebView javascript-interface call carries the page's positional
                        // argument list, so the handler declared as (a, b, c) must be invoked
                        // with those three arguments. Passing the list through as one payload
                        // would hand the handler a single array and leave b/c undefined, which
                        // is what stringified into the "collapsed comma list" reported in #1345.
                        if (payloadIsArgumentList === true) {
                            return handler.apply(null, payload);
                        }
                        var normalizedPayload = payload;
                        if (
                            normalizedPayload &&
                            typeof normalizedPayload === 'object' &&
                            normalizedPayload.__composeTextFieldPayload === true &&
                            Object.prototype.hasOwnProperty.call(normalizedPayload, 'value')
                        ) {
                            normalizedPayload = normalizedPayload.value;
                        }
                        return handler(normalizedPayload);
                    },
                    subscribeStateChange: subscribeStateChange,
                    flushStateChanges: flushPendingStateChanges
                };
            }

            return {
                createContext: createContext
            };
        })();
    """.trimIndent()
}

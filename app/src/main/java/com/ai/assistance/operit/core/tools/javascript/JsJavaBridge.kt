package com.ai.assistance.operit.core.tools.javascript

internal fun buildJavaClassBridgeDefinition(): String {
    return """
        (function createJavaBridge(ownerCallId, sharedState) {
            function hasNative(methodName) {
                return (
                    typeof NativeInterface !== 'undefined' &&
                    NativeInterface &&
                    typeof NativeInterface[methodName] === 'function'
                );
            }

            function normalizeBridgeBoolean(value) {
                if (value === true || value === false) {
                    return value;
                }
                if (typeof value === 'string') {
                    var normalized = value.trim().toLowerCase();
                    if (normalized === 'true') {
                        return true;
                    }
                    if (normalized === 'false' || normalized === '') {
                        return false;
                    }
                }
                if (typeof value === 'number') {
                    return value !== 0;
                }
                return !!value;
            }

            function classExistsRaw(className) {
                if (!hasNative('javaClassExists')) {
                    return false;
                }
                try {
                    return normalizeBridgeBoolean(
                        NativeInterface.javaClassExists(String(className || ''))
                    );
                } catch (_e) {
                    return false;
                }
            }

            function isPropertyKeyName(value) {
                return typeof value === 'string' && value.length > 0;
            }

            var __javaBridgeJsObjectStore = sharedState.objects;
            var __javaHandleRegistrations = new Map();
            var __javaInstanceFinalizer = new FinalizationRegistry(function(heldValue) {
                finalizeInstanceProxy(heldValue);
            });

            function registerJsObject(value) {
                sharedState.nextObjectId += 1;
                var id = '__java_js_obj_' +
                    (ownerCallId ? ownerCallId + '_' : '') + sharedState.nextObjectId;
                // Promise results and interface arguments must retain the facade's exact owner;
                // a global bridge wrapping returned Java handles would lose that ownership.
                __javaBridgeJsObjectStore[id] = {
                    value: value,
                    ownerCallId: ownerCallId,
                    wrapValue: wrapValue
                };
                return id;
            }

            function releaseJsObject(objectId) {
                var normalized = String(objectId || '').trim();
                if (!normalized) {
                    return false;
                }
                if (Object.prototype.hasOwnProperty.call(__javaBridgeJsObjectStore, normalized)) {
                    delete __javaBridgeJsObjectStore[normalized];
                    return true;
                }
                return false;
            }

            function normalizeHandleValue(value) {
                return String(value || '').trim();
            }

            function registerInstanceProxy(handle, proxyObject) {
                var normalized = normalizeHandleValue(handle);
                if (!normalized || !proxyObject || typeof proxyObject !== 'object') {
                    return;
                }

                var token = {};
                var tokenSet = __javaHandleRegistrations.get(normalized);
                if (!tokenSet) {
                    tokenSet = new Set();
                    __javaHandleRegistrations.set(normalized, tokenSet);
                }
                tokenSet.add(token);

                __javaInstanceFinalizer.register(
                    proxyObject,
                    {
                        handle: normalized,
                        token: token
                    },
                    token
                );
            }

            function clearInstanceHandleRegistrations(handle) {
                var normalized = normalizeHandleValue(handle);
                if (!normalized) {
                    return;
                }

                var tokenSet = __javaHandleRegistrations.get(normalized);
                if (!tokenSet) {
                    return;
                }

                tokenSet.forEach(function(token) {
                    __javaInstanceFinalizer.unregister(token);
                });
                __javaHandleRegistrations.delete(normalized);
            }

            function releaseInstanceHandle(handle, ignoreErrors) {
                var normalized = normalizeHandleValue(handle);
                if (!normalized) {
                    return false;
                }

                try {
                    var released = !!invokeBridge('__javaReleaseInstanceInternal', [normalized]);
                    clearInstanceHandleRegistrations(normalized);
                    return released;
                } catch (error) {
                    if (ignoreErrors) {
                        clearInstanceHandleRegistrations(normalized);
                        return false;
                    }
                    throw error;
                }
            }

            function finalizeInstanceProxy(heldValue) {
                if (!heldValue || typeof heldValue !== 'object') {
                    return;
                }

                var normalized = normalizeHandleValue(heldValue.handle);
                if (!normalized) {
                    return;
                }

                var tokenSet = __javaHandleRegistrations.get(normalized);
                if (!tokenSet) {
                    return;
                }

                tokenSet.delete(heldValue.token);
                if (tokenSet.size > 0) {
                    return;
                }

                __javaHandleRegistrations.delete(normalized);
                releaseInstanceHandle(normalized, true);
            }

            function normalizeInterfaceName(value) {
                if (value === null || value === undefined) {
                    return '';
                }

                if (typeof value === 'function' || typeof value === 'object') {
                    try {
                        if (
                            Object.prototype.hasOwnProperty.call(value, 'className') &&
                            typeof value.className === 'string'
                        ) {
                            var classNameValue = String(value.className || '').trim();
                            if (classNameValue) {
                                return classNameValue;
                            }
                        }
                    } catch (_error) {
                    }

                    try {
                        if (
                            Object.prototype.hasOwnProperty.call(value, '__javaClass') &&
                            typeof value.__javaClass === 'string'
                        ) {
                            var javaClassValue = String(value.__javaClass || '').trim();
                            if (javaClassValue) {
                                return javaClassValue;
                            }
                        }
                    } catch (_error2) {
                    }
                }

                if (typeof value === 'function' || typeof value === 'object') {
                    return '';
                }
                return String(value || '').trim();
            }

            function isJavaClassReference(value) {
                if (!value || (typeof value !== 'function' && typeof value !== 'object')) {
                    return false;
                }
                try {
                    if (
                        Object.prototype.hasOwnProperty.call(value, 'className') &&
                        typeof value.className === 'string' &&
                        String(value.className || '').trim().length > 0
                    ) {
                        return true;
                    }
                } catch (_error) {
                }
                try {
                    if (
                        Object.prototype.hasOwnProperty.call(value, '__javaClass') &&
                        typeof value.__javaClass === 'string' &&
                        String(value.__javaClass || '').trim().length > 0
                    ) {
                        return true;
                    }
                } catch (_error2) {
                }
                return false;
            }

            function normalizeInterfaceNames(interfaceNameOrNames) {
                if (Array.isArray(interfaceNameOrNames)) {
                    return interfaceNameOrNames
                        .map(function(item) {
                            return normalizeInterfaceName(item);
                        })
                        .filter(Boolean);
                }
                var single = normalizeInterfaceName(interfaceNameOrNames);
                if (!single) {
                    return [];
                }
                return [single];
            }

            function attachJsInterfaceValue(marker, jsValue) {
                if (!marker || typeof marker !== 'object' || jsValue === undefined) {
                    return marker;
                }
                try {
                    Object.defineProperty(marker, '__javaJsValue', {
                        value: jsValue,
                        writable: true,
                        configurable: true,
                        enumerable: false
                    });
                } catch (_error) {
                    marker.__javaJsValue = jsValue;
                }
                return marker;
            }

            function getJsInterfaceValue(marker) {
                if (!marker || typeof marker !== 'object') {
                    return undefined;
                }
                try {
                    return marker.__javaJsValue;
                } catch (_error) {
                    return undefined;
                }
            }

            function hasUsableJavaInstanceMarker(value) {
                if (!value || typeof value !== 'object') {
                    return false;
                }
                try {
                    return (
                        Object.prototype.hasOwnProperty.call(value, '__javaHandle') &&
                        Object.prototype.hasOwnProperty.call(value, '__javaClass') &&
                        typeof value.__javaHandle === 'string' &&
                        typeof value.__javaClass === 'string' &&
                        String(value.__javaHandle || '').trim().length > 0 &&
                        String(value.__javaClass || '').trim().length > 0
                    );
                } catch (_error) {
                    return false;
                }
            }

            function ensureJsInterfaceMarkerRegistered(marker) {
                if (!marker || typeof marker !== 'object') {
                    throw new Error('js interface marker is required');
                }

                var currentId = String(marker.__javaJsObjectId || '').trim();
                if (
                    currentId &&
                    Object.prototype.hasOwnProperty.call(__javaBridgeJsObjectStore, currentId)
                ) {
                    marker.__javaJsObjectId = currentId;
                    return marker;
                }

                var jsValue = getJsInterfaceValue(marker);
                if (jsValue === undefined) {
                    throw new Error(
                        currentId
                            ? ('js interface implementation is unavailable: ' + currentId)
                            : 'js interface implementation is unavailable'
                    );
                }

                var nextId = registerJsObject(jsValue);
                marker.__javaJsObjectId = nextId;
                return marker;
            }

            function buildJsInterfaceMarker(objectId, interfaceNames, jsValue) {
                var marker = {
                    __javaJsInterface: true,
                    __javaJsObjectId: String(objectId || ''),
                    __javaInterfaces: normalizeInterfaceNames(interfaceNames)
                };
                return attachJsInterfaceValue(marker, jsValue);
            }

            function invokeRegisteredJsObject(objectId, methodName, args) {
                var id = String(objectId || '').trim();
                if (!id) {
                    throw new Error('jsObjectId is required');
                }
                if (!Object.prototype.hasOwnProperty.call(__javaBridgeJsObjectStore, id)) {
                    throw new Error('js object not found: ' + id);
                }

                var registration = __javaBridgeJsObjectStore[id];
                if (registration.ownerCallId) {
                    var callState = globalThis.__operitGetCallState(registration.ownerCallId);
                    if (!callState || callState.completed) {
                        throw new Error('JavaScript execution session has ended');
                    }
                }
                var target = registration.value;
                var normalizedMethod = String(methodName || '').trim();
                var normalizedArgs = Array.isArray(args)
                    ? args.map(function(item) {
                        return registration.wrapValue(item);
                    })
                    : [];

                if (typeof target === 'function') {
                    return target.apply(null, normalizedArgs);
                }

                if (!target || typeof target !== 'object') {
                    throw new Error('js object is not callable: ' + id);
                }

                if (
                    normalizedMethod &&
                    typeof target[normalizedMethod] === 'function'
                ) {
                    return target[normalizedMethod].apply(target, normalizedArgs);
                }

                if (normalizedMethod && normalizedArgs.length === 0 && normalizedMethod in target) {
                    return target[normalizedMethod];
                }

                throw new Error(
                    'method not found on js interface object: ' +
                        (normalizedMethod || '<empty>')
                );
            }

            function unwrapValue(value) {
                if (typeof value === 'function') {
                    return ensureJsInterfaceMarkerRegistered(
                        buildJsInterfaceMarker('', [], value)
                    );
                }
                if (!value || typeof value !== 'object') {
                    return value;
                }
                if (hasUsableJavaInstanceMarker(value)) {
                    return {
                        __javaHandle: String(value.__javaHandle),
                        __javaClass: String(value.__javaClass)
                    };
                }
                if (
                    Object.prototype.hasOwnProperty.call(value, '__javaJsInterface') &&
                    Object.prototype.hasOwnProperty.call(value, '__javaJsObjectId')
                ) {
                    return ensureJsInterfaceMarkerRegistered(
                        buildJsInterfaceMarker(
                            String(value.__javaJsObjectId || ''),
                            value.__javaInterfaces,
                            getJsInterfaceValue(value)
                        )
                    );
                }
                if (Array.isArray(value)) {
                    return value.map(function(item) {
                        return unwrapValue(item);
                    });
                }
                var out = {};
                for (var key in value) {
                    if (Object.prototype.hasOwnProperty.call(value, key)) {
                        out[key] = unwrapValue(value[key]);
                    }
                }
                return out;
            }

            function wrapValue(value) {
                if (!value || typeof value !== 'object') {
                    return value;
                }
                if (hasUsableJavaInstanceMarker(value)) {
                    return createInstanceProxy(
                        String(value.__javaClass),
                        String(value.__javaHandle)
                    );
                }
                if (Array.isArray(value)) {
                    return value.map(function(item) {
                        return wrapValue(item);
                    });
                }
                var out = {};
                for (var key in value) {
                    if (Object.prototype.hasOwnProperty.call(value, key)) {
                        out[key] = wrapValue(value[key]);
                    }
                }
                return out;
            }

            function normalizeArgs(argsLike) {
                var args = [];
                for (var index = 0; index < argsLike.length; index += 1) {
                    args.push(unwrapValue(argsLike[index]));
                }
                return args;
            }

            function scheduleSuspendCall(invoker, args) {
                var argList = Array.isArray(args) ? args.slice() : [];
                if (typeof Promise !== 'function') {
                    throw new Error('Promise is required for suspend call');
                }
                return new Promise(function(resolve, reject) {
                    var callbackId = '';
                    var settled = false;
                    var promiseCallback = function(error, value) {
                        if (settled) {
                            return;
                        }
                        settled = true;
                        try {
                            if (error) {
                                reject(new Error(error));
                            } else {
                                resolve(value);
                            }
                        } finally {
                            if (callbackId) {
                                releaseJsObject(callbackId);
                                callbackId = '';
                            }
                        }
                    };
                    callbackId = registerJsObject(promiseCallback);
                    try {
                        invoker(callbackId, argList);
                    } catch (error) {
                        if (callbackId) {
                            releaseJsObject(callbackId);
                            callbackId = '';
                        }
                        throw error;
                    }
                });
            }

            function invokeBridge(methodName, args) {
                if (!hasNative(methodName)) {
                    throw new Error('NativeInterface.' + methodName + ' is unavailable');
                }

                var raw = NativeInterface[methodName].apply(NativeInterface, args || []);
                var parsed;
                try {
                    parsed = JSON.parse(String(raw || ''));
                } catch (e) {
                    throw new Error('Invalid bridge result from ' + methodName + ': ' + String(raw));
                }

                if (!parsed || parsed.success !== true) {
                    var message =
                        parsed && typeof parsed.message === 'string' && parsed.message.length > 0
                            ? parsed.message
                            : '';
                    throw new Error(message);
                }

                return wrapValue(parsed.data);
            }

            function invokeNativeSuspend(methodName, args) {
                if (ownerCallId) {
                    methodName += 'ForCall';
                    args = [ownerCallId].concat(args);
                }
                if (!hasNative(methodName)) {
                    throw new Error('NativeInterface.' + methodName + ' is unavailable');
                }
                NativeInterface[methodName].apply(NativeInterface, args || []);
            }

            function invokeBridgeBoolean(methodName, args) {
                return !!invokeBridge(methodName, args);
            }

            function isPlainInterfaceImplementationCandidate(value) {
                return (
                    (typeof value === 'function' ||
                        (value &&
                            typeof value === 'object' &&
                            !Array.isArray(value))) &&
                    !isJavaClassReference(value)
                );
            }

            function shouldSugarInterfaceConstruction(error) {
                var message =
                    error && typeof error.message === 'string'
                        ? error.message
                        : String(error || '');
                return message.indexOf(' is an interface; use Java.implement(') >= 0;
            }

            function callInstanceBridge(handle, methodName, args) {
                return invokeBridge('javaCallInstance', [
                    handle,
                    String(methodName || ''),
                    JSON.stringify(normalizeArgs(args || []))
                ]);
            }

            function tryProcessPendingJavaBridgeCallback() {
                if (
                    !hasNative('javaPollPendingJsCallback') ||
                    !hasNative('javaResolvePendingJsCallback')
                ) {
                    return false;
                }

                var raw = NativeInterface.javaPollPendingJsCallback();
                if (raw === undefined || raw === null) {
                    return false;
                }

                var text = String(raw || '').trim();
                if (!text) {
                    return false;
                }

                var request;
                try {
                    request = JSON.parse(text);
                } catch (_parseError) {
                    return false;
                }

                if (!request || !request.id) {
                    return false;
                }

                var payload;
                try {
                    var parsedArgs = [];
                    if (typeof request.argsJson === 'string' && request.argsJson.trim()) {
                        var candidateArgs = JSON.parse(request.argsJson);
                        if (Array.isArray(candidateArgs)) {
                            parsedArgs = candidateArgs;
                        }
                    }
                    var value = invokeRegisteredJsObject(
                        request.jsObjectId,
                        request.methodName,
                        parsedArgs
                    );
                    payload = JSON.stringify({
                        success: true,
                        data: unwrapValue(value)
                    });
                } catch (error) {
                    payload = JSON.stringify({
                        success: false,
                        error: error && error.message ? error.message : String(error)
                    });
                }

                NativeInterface.javaResolvePendingJsCallback(String(request.id), payload);
                return true;
            }

            function processPendingJavaBridgeCallbacks(maxCount) {
                var limit =
                    typeof maxCount === 'number' && isFinite(maxCount) && maxCount > 0
                        ? Math.floor(maxCount)
                        : 1;
                var processed = 0;
                while (processed < limit && tryProcessPendingJavaBridgeCallback()) {
                    processed += 1;
                }
                return processed;
            }

            if (typeof globalThis !== 'undefined') {
                globalThis.__operitProcessPendingJavaBridgeCallbacks = processPendingJavaBridgeCallbacks;
            }

            function sleepForJavaBridgeWait(durationMs) {
                var normalized =
                    typeof durationMs === 'number' && isFinite(durationMs)
                        ? Math.max(0, Math.floor(durationMs))
                        : 0;
                if (normalized <= 0) {
                    return;
                }
                if (hasNative('javaSleepMillis')) {
                    NativeInterface.javaSleepMillis(String(normalized));
                    return;
                }
                var startedAt = Date.now();
                while (Date.now() - startedAt < normalized) {
                }
            }

            function waitForThreadJoinWithCallbacks(handle, argsLike) {
                var args = Array.prototype.slice.call(argsLike || []);
                var timeoutMs = 0;
                if (args.length >= 1) {
                    var millis = Number(args[0]);
                    if (isFinite(millis)) {
                        timeoutMs = Math.max(0, Math.floor(millis));
                    }
                }
                var deadline = timeoutMs > 0 ? Date.now() + timeoutMs : 0;

                while (true) {
                    if (!callInstanceBridge(handle, 'isAlive', [])) {
                        return;
                    }
                    var processed = processPendingJavaBridgeCallbacks(32);
                    if (deadline > 0 && Date.now() >= deadline) {
                        return;
                    }
                    sleepForJavaBridgeWait(processed > 0 ? 0 : 10);
                }
            }

            function resolveFutureTimeoutMs(args) {
                if (!Array.isArray(args) || args.length === 0) {
                    return 0;
                }
                var rawTimeout = Number(args[0]);
                if (!isFinite(rawTimeout)) {
                    return 0;
                }
                if (
                    args.length >= 2 &&
                    args[1] &&
                    typeof args[1].toMillis === 'function'
                ) {
                    try {
                        var converted = Number(args[1].toMillis(rawTimeout));
                        if (isFinite(converted)) {
                            return Math.max(0, Math.floor(converted));
                        }
                    } catch (_error) {
                    }
                }
                if (args.length >= 2 && typeof args[1] === 'string') {
                    var unitName = String(args[1] || '').trim().toUpperCase();
                    if (unitName === 'NANOSECONDS') {
                        return Math.max(0, Math.floor(rawTimeout / 1e6));
                    }
                    if (unitName === 'MICROSECONDS') {
                        return Math.max(0, Math.floor(rawTimeout / 1e3));
                    }
                    if (unitName === 'MILLISECONDS') {
                        return Math.max(0, Math.floor(rawTimeout));
                    }
                    if (unitName === 'SECONDS') {
                        return Math.max(0, Math.floor(rawTimeout * 1000));
                    }
                    if (unitName === 'MINUTES') {
                        return Math.max(0, Math.floor(rawTimeout * 60 * 1000));
                    }
                    if (unitName === 'HOURS') {
                        return Math.max(0, Math.floor(rawTimeout * 60 * 60 * 1000));
                    }
                    if (unitName === 'DAYS') {
                        return Math.max(0, Math.floor(rawTimeout * 24 * 60 * 60 * 1000));
                    }
                }
                return Math.max(0, Math.floor(rawTimeout));
            }

            function waitForFutureTaskResultWithCallbacks(handle, argsLike) {
                var args = Array.prototype.slice.call(argsLike || []);
                var timeoutMs = resolveFutureTimeoutMs(args);
                var deadline = timeoutMs > 0 ? Date.now() + timeoutMs : 0;

                while (true) {
                    if (callInstanceBridge(handle, 'isDone', [])) {
                        return callInstanceBridge(
                            handle,
                            'get',
                            args.length > 0 ? args : []
                        );
                    }
                    var processed = processPendingJavaBridgeCallbacks(32);
                    if (deadline > 0 && Date.now() >= deadline) {
                        throw new Error('FutureTask.get timed out after ' + timeoutMs + 'ms');
                    }
                    sleepForJavaBridgeWait(processed > 0 ? 0 : 10);
                }
            }

            function normalizeExternalCodeLoadOptions(options) {
                if (options === undefined || options === null) {
                    return {};
                }
                if (typeof options === 'string') {
                    return {
                        nativeLibraryDir: String(options || '').trim()
                    };
                }
                if (typeof options !== 'object' || Array.isArray(options)) {
                    throw new Error('load options must be an object or native library dir string');
                }

                var normalized = {};
                if (
                    typeof options.nativeLibraryDir === 'string' &&
                    options.nativeLibraryDir.trim().length > 0
                ) {
                    normalized.nativeLibraryDir = options.nativeLibraryDir.trim();
                }
                if (Array.isArray(options.childFirstPrefixes)) {
                    var childFirstPrefixes = options.childFirstPrefixes
                        .map(function(prefix) {
                            return String(prefix || '').trim();
                        })
                        .filter(function(prefix) {
                            return prefix.length > 0;
                        });
                    if (childFirstPrefixes.length > 0) {
                        normalized.childFirstPrefixes = Array.from(new Set(childFirstPrefixes));
                    }
                }
                return normalized;
            }

            function loadExternalCode(methodName, path, options) {
                var normalizedPath = String(path || '').trim();
                if (!normalizedPath) {
                    throw new Error('external code path is required');
                }
                return invokeBridge(methodName, [
                    normalizedPath,
                    JSON.stringify(normalizeExternalCodeLoadOptions(options))
                ]);
            }

            function createInstanceProxy(className, handle) {
                var target = {
                    __javaClass: className,
                    __javaHandle: handle,
                    className: className,
                    handle: handle,
                    call: function(methodName) {
                        var args = Array.prototype.slice.call(arguments, 1);
                        return invokeBridge('javaCallInstance', [
                            handle,
                            String(methodName || ''),
                            JSON.stringify(normalizeArgs(args))
                        ]);
                    },
                    callSuspend: function(methodName) {
                        var args = Array.prototype.slice.call(arguments, 1);
                        return scheduleSuspendCall(
                            function(callbackId, payloadArgs) {
                                invokeNativeSuspend('javaCallInstanceSuspend', [
                                    handle,
                                    String(methodName || ''),
                                    JSON.stringify(normalizeArgs(payloadArgs)),
                                    callbackId
                                ]);
                            },
                            args
                        );
                    },
                    get: function(fieldName) {
                        if (arguments.length === 0) {
                            return invokeBridge('javaCallInstance', [
                                handle,
                                'get',
                                '[]'
                            ]);
                        }
                        return invokeBridge('javaGetInstanceField', [
                            handle,
                            String(fieldName || '')
                        ]);
                    },
                    set: function(fieldName, value) {
                        if (arguments.length === 1) {
                            return invokeBridge('javaCallInstance', [
                                handle,
                                'set',
                                JSON.stringify(normalizeArgs([fieldName]))
                            ]);
                        }
                        return invokeBridge('javaSetInstanceField', [
                            handle,
                            String(fieldName || ''),
                            JSON.stringify(unwrapValue(value))
                        ]);
                    },
                    toJSON: function() {
                        return {
                            __javaHandle: handle,
                            __javaClass: className
                        };
                    },
                    toString: function() {
                        return invokeBridge('javaCallInstance', [
                            handle,
                            'toString',
                            '[]'
                        ]);
                    }
                };

                if (className === 'java.lang.Thread') {
                    target.join = function() {
                        return waitForThreadJoinWithCallbacks(handle, arguments);
                    };
                }

                if (className === 'java.util.concurrent.FutureTask') {
                    target.get = function() {
                        return waitForFutureTaskResultWithCallbacks(handle, arguments);
                    };
                }

                var proxy = new Proxy(target, {
                    get: function(obj, prop) {
                        if (prop in obj) {
                            return obj[prop];
                        }
                        if (prop === Symbol.toStringTag) {
                            return 'JavaObject';
                        }
                        if (prop === 'then') {
                            return undefined;
                        }
                        if (typeof prop !== 'string') {
                            return undefined;
                        }
                        try {
                            if (invokeBridgeBoolean('javaHasInstanceMethod', [
                                handle,
                                prop
                            ])) {
                                return function() {
                                    var args = Array.prototype.slice.call(arguments);
                                    return invokeBridge('javaCallInstance', [
                                        handle,
                                        prop,
                                        JSON.stringify(normalizeArgs(args))
                                    ]);
                                };
                            }
                        } catch (_methodProbeError) {
                        }
                        try {
                            return invokeBridge('javaGetInstanceField', [
                                handle,
                                prop
                            ]);
                        } catch (_fieldError) {
                        }
                        return function() {
                            var args = Array.prototype.slice.call(arguments);
                            return invokeBridge('javaCallInstance', [
                                handle,
                                prop,
                                JSON.stringify(normalizeArgs(args))
                            ]);
                        };
                    },
                    set: function(obj, prop, value) {
                        if (prop in obj) {
                            obj[prop] = value;
                            return true;
                        }
                        if (typeof prop !== 'string') {
                            return false;
                        }
                        invokeBridge('javaSetInstanceField', [
                            handle,
                            prop,
                            JSON.stringify(unwrapValue(value))
                        ]);
                        return true;
                    }
                });

                registerInstanceProxy(handle, proxy);
                return proxy;
            }

            function shouldFallbackToCompanion(message) {
                if (!message) {
                    return false;
                }
                var text = String(message);
                return (
                    text.indexOf("method '") >= 0 &&
                    text.indexOf(" not found on ") >= 0
                ) || (text.indexOf("no method '") >= 0 && text.indexOf(" matched on ") >= 0);
            }

            function callCompanionInstanceMethod(className, methodName, args) {
                try {
                    var companionInstance = invokeBridge('javaGetStaticField', [
                        className,
                        'Companion'
                    ]);
                    if (
                        companionInstance &&
                        typeof companionInstance[methodName] === 'function'
                    ) {
                        return { hit: true, value: companionInstance[methodName].apply(companionInstance, args || []) };
                    }
                } catch (_e) {
                }
                return { hit: false };
            }

            function callStaticWithCompanionFallback(className, methodName, args) {
                try {
                    return invokeBridge('javaCallStatic', [
                        className,
                        String(methodName || ''),
                        JSON.stringify(normalizeArgs(args || []))
                    ]);
                } catch (e) {
                    if (!shouldFallbackToCompanion(e && e.message)) {
                        throw e;
                    }
                    var instanceAttempt = callCompanionInstanceMethod(className, methodName, args);
                    if (instanceAttempt && instanceAttempt.hit) {
                        return instanceAttempt.value;
                    }
                    var companionClassName = className + '${'$'}Companion';
                    if (classExistsRaw(companionClassName)) {
                        try {
                            return invokeBridge('javaCallStatic', [
                                companionClassName,
                                String(methodName || ''),
                                JSON.stringify(normalizeArgs(args || []))
                            ]);
                        } catch (e2) {
                            if (!shouldFallbackToCompanion(e2 && e2.message)) {
                                throw e2;
                            }
                            var retryInstance = callCompanionInstanceMethod(className, methodName, args);
                            if (retryInstance && retryInstance.hit) {
                                return retryInstance.value;
                            }
                            throw e2;
                        }
                    }
                    throw e;
                }
            }

            function createClassProxy(className) {
                var target = function() {
                    return target.newInstance.apply(target, arguments);
                };

                target.className = className;
                target.exists = function() {
                    return hasNative('javaClassExists') &&
                        normalizeBridgeBoolean(NativeInterface.javaClassExists(className));
                };
                target.newInstance = function() {
                    var rawArgs = Array.prototype.slice.call(arguments);
                    var args = normalizeArgs(rawArgs);
                    try {
                        return invokeBridge('javaNewInstance', [className, JSON.stringify(args)]);
                    } catch (error) {
                        if (
                            rawArgs.length === 1 &&
                            isPlainInterfaceImplementationCandidate(rawArgs[0]) &&
                            shouldSugarInterfaceConstruction(error)
                        ) {
                            return JavaApi.implement(className, rawArgs[0]);
                        }
                        throw error;
                    }
                };
                target.callStatic = function(methodName) {
                    var args = Array.prototype.slice.call(arguments, 1);
                    return callStaticWithCompanionFallback(className, methodName, args);
                };
                target.callSuspend = function(methodName) {
                    var args = Array.prototype.slice.call(arguments, 1);
                    return scheduleSuspendCall(
                        function(callbackId, payloadArgs) {
                            invokeNativeSuspend('javaCallStaticSuspend', [
                                className,
                                String(methodName || ''),
                                JSON.stringify(normalizeArgs(payloadArgs)),
                                callbackId
                            ]);
                        },
                        args
                    );
                };
                target.getStatic = function(fieldName) {
                    return invokeBridge('javaGetStaticField', [
                        className,
                        String(fieldName || '')
                    ]);
                };
                target.setStatic = function(fieldName, value) {
                    return invokeBridge('javaSetStaticField', [
                        className,
                        String(fieldName || ''),
                        JSON.stringify(unwrapValue(value))
                    ]);
                };
                target.toString = function() {
                    return '[JavaClass ' + className + ']';
                };

                return new Proxy(target, {
                    get: function(obj, prop) {
                        if (prop in obj) {
                            return obj[prop];
                        }
                        if (prop === Symbol.toStringTag) {
                            return 'JavaClass';
                        }
                        if (prop === 'then') {
                            return undefined;
                        }
                        if (typeof prop !== 'string') {
                            return undefined;
                        }
                        try {
                            return invokeBridge('javaGetStaticField', [
                                className,
                                prop
                            ]);
                        } catch (_fieldError) {
                        }
                        var nestedClassName = className + '$' + prop;
                        if (classExistsRaw(nestedClassName)) {
                            return createClassProxy(nestedClassName);
                        }
                        var nestedUpperClassName = className + '$' + prop.toUpperCase();
                        if (
                            nestedUpperClassName !== nestedClassName &&
                            classExistsRaw(nestedUpperClassName)
                        ) {
                            return createClassProxy(nestedUpperClassName);
                        }
                        return function() {
                            var args = Array.prototype.slice.call(arguments);
                            return callStaticWithCompanionFallback(className, prop, args);
                        };
                    },
                    apply: function(obj, _thisArg, args) {
                        return obj.newInstance.apply(obj, args || []);
                    },
                    construct: function(obj, args) {
                        return obj.newInstance.apply(obj, args || []);
                    },
                    set: function(obj, prop, value) {
                        if (prop in obj) {
                            obj[prop] = value;
                            return true;
                        }
                        if (typeof prop !== 'string') {
                            return false;
                        }
                        invokeBridge('javaSetStaticField', [
                            className,
                            prop,
                            JSON.stringify(unwrapValue(value))
                        ]);
                        return true;
                    }
                });
            }

            function createPackageProxy(parts) {
                var pathParts = Array.isArray(parts) ? parts.slice() : [];
                var target = function() {
                    var fullName = pathParts.join('.');
                    if (!fullName) {
                        throw new Error('cannot instantiate empty package path');
                    }
                    if (!classExistsRaw(fullName)) {
                        throw new Error('class not found: ' + fullName);
                    }
                    var cls = createClassProxy(fullName);
                    return cls.newInstance.apply(cls, arguments);
                };

                target.path = pathParts.join('.');
                target.toString = function() {
                    return '[JavaPackage ' + target.path + ']';
                };

                return new Proxy(target, {
                    get: function(obj, prop) {
                        if (prop in obj) {
                            return obj[prop];
                        }
                        if (prop === Symbol.toStringTag) {
                            return 'JavaPackage';
                        }
                        if (prop === 'then') {
                            return undefined;
                        }
                        if (!isPropertyKeyName(prop)) {
                            return undefined;
                        }

                        var nextParts = pathParts.concat([prop]);
                        var candidate = nextParts.join('.');
                        if (classExistsRaw(candidate)) {
                            return createClassProxy(candidate);
                        }
                        return createPackageProxy(nextParts);
                    },
                    apply: function(_obj, _thisArg, args) {
                        var fullName = pathParts.join('.');
                        if (!fullName) {
                            throw new Error('cannot call empty package path');
                        }
                        if (!classExistsRaw(fullName)) {
                            throw new Error('class not found: ' + fullName);
                        }
                        var cls = createClassProxy(fullName);
                        return cls.newInstance.apply(cls, args || []);
                    },
                    construct: function(_obj, args) {
                        var fullName = pathParts.join('.');
                        if (!fullName) {
                            throw new Error('cannot construct empty package path');
                        }
                        if (!classExistsRaw(fullName)) {
                            throw new Error('class not found: ' + fullName);
                        }
                        var cls = createClassProxy(fullName);
                        return cls.newInstance.apply(cls, args || []);
                    }
                });
            }

            var JavaApi = {
                type: function(className) {
                    var normalized = String(className || '').trim();
                    if (!normalized) {
                        throw new Error('class name is required');
                    }
                    return createClassProxy(normalized);
                },
                use: function(className) {
                    return this.type(className);
                },
                importClass: function(className) {
                    return this.type(className);
                },
                package: function(packageName) {
                    var normalized = String(packageName || '').trim();
                    if (!normalized) {
                        throw new Error('package name is required');
                    }
                    return createPackageProxy(normalized.split('.').filter(Boolean));
                },
                implement: function(interfaceNameOrNames, impl) {
                    var actualImpl = impl;
                    var interfaceNamesInput = interfaceNameOrNames;
                    if (
                        actualImpl === undefined &&
                        (typeof interfaceNameOrNames === 'function' ||
                            (interfaceNameOrNames &&
                                typeof interfaceNameOrNames === 'object' &&
                                !Array.isArray(interfaceNameOrNames))) &&
                        !isJavaClassReference(interfaceNameOrNames)
                    ) {
                        actualImpl = interfaceNameOrNames;
                        interfaceNamesInput = [];
                    }

                    if (actualImpl === null || actualImpl === undefined) {
                        throw new Error('implement target is required');
                    }
                    if (typeof actualImpl !== 'function' && typeof actualImpl !== 'object') {
                        throw new Error('implement target must be a function or object');
                    }
                    var interfaceNames = normalizeInterfaceNames(interfaceNamesInput);
                    return buildJsInterfaceMarker('', interfaceNames, actualImpl);
                },
                proxy: function(interfaceNameOrNames, impl) {
                    return this.implement(interfaceNameOrNames, impl);
                },
                classExists: function(className) {
                    var normalized = String(className || '').trim();
                    if (!normalized) {
                        return false;
                    }
                    return classExistsRaw(normalized);
                },
                loadDex: function(path, options) {
                    return loadExternalCode('javaLoadDex', path, options);
                },
                loadJar: function(path, options) {
                    return loadExternalCode('javaLoadJar', path, options);
                },
                listLoadedCodePaths: function() {
                    return invokeBridge('javaListLoadedCodePaths', []);
                },
                callStatic: function(className, methodName) {
                    var normalizedClass = String(className || '').trim();
                    var normalizedMethod = String(methodName || '').trim();
                    var args = Array.prototype.slice.call(arguments, 2);
                    return invokeBridge('javaCallStatic', [
                        normalizedClass,
                        normalizedMethod,
                        JSON.stringify(normalizeArgs(args))
                    ]);
                },
                callSuspend: function(className, methodName) {
                    var normalizedClass = String(className || '').trim();
                    var normalizedMethod = String(methodName || '').trim();
                    var args = Array.prototype.slice.call(arguments, 2);
                    return scheduleSuspendCall(
                        function(callbackId, payloadArgs) {
                            invokeNativeSuspend('javaCallStaticSuspend', [
                                normalizedClass,
                                normalizedMethod,
                                JSON.stringify(normalizeArgs(payloadArgs)),
                                callbackId
                            ]);
                        },
                        args
                    );
                },
                newInstance: function(className) {
                    var normalizedClass = String(className || '').trim();
                    var args = Array.prototype.slice.call(arguments, 1);
                    return invokeBridge('javaNewInstance', [
                        normalizedClass,
                        JSON.stringify(normalizeArgs(args))
                    ]);
                },
                getApplicationContext: function() {
                    return invokeBridge('javaGetApplicationContext', []);
                },
                getContext: function() {
                    return this.getApplicationContext();
                },
                getCurrentActivity: function() {
                    return invokeBridge('javaGetCurrentActivity', []);
                },
                getActivity: function() {
                    return this.getCurrentActivity();
                },
                toString: function() {
                    return '[JavaBridge]';
                }
            };

            var Java = new Proxy(JavaApi, {
                get: function(obj, prop) {
                    if (prop in obj) {
                        return obj[prop];
                    }
                    if (prop === Symbol.toStringTag) {
                        return 'JavaBridge';
                    }
                    if (prop === 'then') {
                        return undefined;
                    }
                    if (!isPropertyKeyName(prop)) {
                        return undefined;
                    }

                    if (classExistsRaw(prop)) {
                        return createClassProxy(prop);
                    }
                    return createPackageProxy([prop]);
                }
            });

            if (!ownerCallId && typeof globalThis !== 'undefined') {
                globalThis.__operitJavaBridgeInvokeJsObject = function(jsObjectId, methodName, args) {
                    return invokeRegisteredJsObject(jsObjectId, methodName, args);
                };
                globalThis.__operitJavaBridgeReleaseJsObject = function(jsObjectId) {
                    return releaseJsObject(jsObjectId);
                };
                globalThis.__operitJavaBridgeWrapValue = wrapValue;
                globalThis.__operitJavaBridgeForCall = function(callId) {
                    if (typeof callId !== 'string' || !callId.trim()) {
                        throw new Error('JavaScript execution call id is required');
                    }
                    return createJavaBridge(callId, sharedState);
                };
                globalThis.__operitJavaBridgeReleaseCallObjects = function(callId) {
                    Object.keys(__javaBridgeJsObjectStore).forEach(function(objectId) {
                        if (__javaBridgeJsObjectStore[objectId].ownerCallId === callId) {
                            delete __javaBridgeJsObjectStore[objectId];
                        }
                    });
                };
            }

            if (!ownerCallId && typeof globalThis !== 'undefined') {
                globalThis.Java = Java;
                globalThis.Kotlin = Java;
            }
            if (!ownerCallId && typeof window !== 'undefined') {
                window.Java = Java;
                window.Kotlin = Java;
            }
            return Java;
        })('', { objects: Object.create(null), nextObjectId: 0 });
        """.trimIndent()
}

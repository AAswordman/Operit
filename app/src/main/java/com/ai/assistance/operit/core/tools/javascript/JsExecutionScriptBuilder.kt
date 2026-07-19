package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONObject

internal const val TOOLPKG_EXECUTION_ENTRY_FUNCTION = "__operitExecuteScriptFunction"

private fun buildExecutionPreludeSource(): String {
    return """
        function __operitGetActiveCallRuntime() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var runtime =
                root &&
                root.__operit_call_runtime_ref &&
                typeof root.__operit_call_runtime_ref === 'object'
                    ? root.__operit_call_runtime_ref
                    : __operit_call_runtime;
            return runtime && typeof runtime === 'object' ? runtime : __operit_call_runtime;
        }
        function __operitInvokeCallRuntime(methodName, argsLike) {
            var runtime = __operitGetActiveCallRuntime();
            var method = runtime ? runtime[methodName] : undefined;
            if (typeof method !== 'function') {
                return undefined;
            }
            return method.apply(runtime, Array.prototype.slice.call(argsLike || []));
        }
        function __operitInvokeCallRuntimeConsole(methodName, argsLike) {
            var runtime = __operitGetActiveCallRuntime();
            var runtimeConsole = runtime && runtime.console ? runtime.console : null;
            var method = runtimeConsole ? runtimeConsole[methodName] : undefined;
            if (typeof method !== 'function') {
                return undefined;
            }
            return method.apply(runtimeConsole, Array.prototype.slice.call(argsLike || []));
        }
        var sendIntermediateResult = function() { return __operitInvokeCallRuntime('sendIntermediateResult', arguments); };
        var emit = function() { return __operitInvokeCallRuntime('emit', arguments); };
        var delta = function() { return __operitInvokeCallRuntime('delta', arguments); };
        var log = function() { return __operitInvokeCallRuntime('log', arguments); };
        var update = function() { return __operitInvokeCallRuntime('update', arguments); };
        var done = function() { return __operitInvokeCallRuntime('done', arguments); };
        var complete = function() { return __operitInvokeCallRuntime('complete', arguments); };
        var getEnv = function() { return __operitInvokeCallRuntime('getEnv', arguments); };
        var getPluginConfigDir = function() { return __operitInvokeCallRuntime('getPluginConfigDir', arguments); };
        var getState = function() { return __operitInvokeCallRuntime('getState', arguments); };
        var getLang = function() { return __operitInvokeCallRuntime('getLang', arguments); };
        var getCallerName = function() { return __operitInvokeCallRuntime('getCallerName', arguments); };
        var getChatId = function() { return __operitInvokeCallRuntime('getChatId', arguments); };
        var getCallerCardId = function() { return __operitInvokeCallRuntime('getCallerCardId', arguments); };
        var __handleAsync = function() { return __operitInvokeCallRuntime('handleAsync', arguments); };
        var console = {
            log: function() { return __operitInvokeCallRuntimeConsole('log', arguments); },
            info: function() { return __operitInvokeCallRuntimeConsole('info', arguments); },
            warn: function() { return __operitInvokeCallRuntimeConsole('warn', arguments); },
            error: function() { return __operitInvokeCallRuntimeConsole('error', arguments); }
        };
        var reportDetailedError = function() { return __operitInvokeCallRuntime('reportDetailedError', arguments); };
        var ToolPkg = globalThis.ToolPkg;
        var Tools = globalThis.Tools;
        var Java = globalThis.Java;
        var Android = globalThis.Android;
        var Intent = globalThis.Intent;
        var PackageManager = globalThis.PackageManager;
        var ContentProvider = globalThis.ContentProvider;
        var SystemManager = globalThis.SystemManager;
        var DeviceController = globalThis.DeviceController;
        var OperitComposeDslRuntime = globalThis.OperitComposeDslRuntime;
        var CryptoJS = globalThis.CryptoJS;
        var Jimp = globalThis.Jimp;
        var UINode = globalThis.UINode;
        var OkHttpClientBuilder = globalThis.OkHttpClientBuilder;
        var OkHttpClient = globalThis.OkHttpClient;
        var RequestBuilder = globalThis.RequestBuilder;
        var OkHttp = globalThis.OkHttp;
        var pako = globalThis.pako;
        var _ = globalThis._;
        var dataUtils = globalThis.dataUtils;
        var toolCall = globalThis.toolCall;
    """.trimIndent()
}

internal fun buildExecutionRuntimeBridgeScript(): String {
    val preludeSource = JSONObject.quote(buildExecutionPreludeSource())
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            if (typeof root.$TOOLPKG_EXECUTION_ENTRY_FUNCTION === 'function') {
                return;
            }

            var runtimePrelude = $preludeSource;

            function text(value) {
                return value == null ? '' : String(value);
            }

            function toBoolean(value) {
                if (value === true || value === false) {
                    return value;
                }
                if (typeof value === 'string') {
                    var normalized = value.trim().toLowerCase();
                    return normalized === 'true' || normalized === '1';
                }
                return !!value;
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
                        text(value.__javaHandle).trim().length > 0 &&
                        text(value.__javaClass).trim().length > 0
                    );
                } catch (_javaMarkerError) {
                    return false;
                }
            }

            function normalizeSerializableValue(value, seen) {
                if (
                    value == null ||
                    typeof value === 'string' ||
                    typeof value === 'number' ||
                    typeof value === 'boolean'
                ) {
                    return value;
                }
                if (typeof value === 'bigint') {
                    return text(value);
                }
                if (typeof value === 'function') {
                    return text(value);
                }
                if (typeof value !== 'object') {
                    return text(value);
                }

                if (!seen) {
                    seen = [];
                }
                if (seen.indexOf(value) >= 0) {
                    return '[Circular]';
                }
                seen.push(value);

                try {
                    if (typeof value.toJSON === 'function') {
                        return normalizeSerializableValue(value.toJSON(), seen);
                    }

                    if (Array.isArray(value)) {
                        return value.map(function(item) {
                            return normalizeSerializableValue(item, seen);
                        });
                    }

                    if (hasUsableJavaInstanceMarker(value)) {
                        return {
                            __javaHandle: text(value.__javaHandle),
                            __javaClass: text(value.__javaClass)
                        };
                    }

                    var out = {};
                    var keys = Object.keys(value);
                    for (var i = 0; i < keys.length; i += 1) {
                        var key = keys[i];
                        out[key] = normalizeSerializableValue(value[key], seen);
                    }
                    return out;
                } finally {
                    seen.pop();
                }
            }

            function serializeOrThrow(value) {
                return JSON.stringify(normalizeSerializableValue(value, []));
            }

            function safeSerialize(value) {
                try {
                    return serializeOrThrow(value);
                } catch (error) {
                    return JSON.stringify({
                        error: 'Failed to serialize value',
                        message: text(error && error.message ? error.message : error),
                        value: text(value).slice(0, 1000)
                    });
                }
            }

            function getCallState(callId) {
                return typeof root.__operitGetCallState === 'function'
                    ? root.__operitGetCallState(callId)
                    : null;
            }

            function normalizePath(pathValue) {
                var parts = text(pathValue).replace(/\\/g, '/').split('/');
                var stack = [];
                for (var i = 0; i < parts.length; i += 1) {
                    var part = parts[i];
                    if (!part || part === '.') {
                        continue;
                    }
                    if (part === '..') {
                        if (stack.length > 0) {
                            stack.pop();
                        }
                        continue;
                    }
                    stack.push(part);
                }
                return stack.join('/');
            }

            function dirname(pathValue) {
                var normalized = normalizePath(pathValue);
                var index = normalized.lastIndexOf('/');
                return index < 0 ? '' : normalized.slice(0, index);
            }

            function resolveModulePath(request, fromPath) {
                var normalized = text(request).replace(/\\/g, '/').trim();
                if (!normalized) {
                    return '';
                }
                if (!(normalized.startsWith('.') || normalized.startsWith('/'))) {
                    return normalized;
                }
                if (normalized.startsWith('/')) {
                    return normalizePath(normalized);
                }
                var base = dirname(fromPath);
                return normalizePath(base ? base + '/' + normalized : normalized);
            }

            function buildCandidatePaths(modulePath) {
                var normalized = normalizePath(modulePath);
                if (!normalized) {
                    return [];
                }
                if (/\.[a-z0-9]+$/i.test(normalized)) {
                    return [normalized];
                }
                return [
                    normalized,
                    normalized + '.js',
                    normalized + '.json',
                    normalized + '/index.js',
                    normalized + '/index.json'
                ];
            }

            function hashText(value) {
                var textValue = text(value);
                var hash = 0;
                for (var i = 0; i < textValue.length; i += 1) {
                    hash = (((hash << 5) - hash) + textValue.charCodeAt(i)) | 0;
                }
                return (hash >>> 0).toString(16);
            }

            function ensureFactoryCache() {
                if (!root.__operitFactoryCache || typeof root.__operitFactoryCache !== 'object') {
                    root.__operitFactoryCache = Object.create(null);
                }
                return root.__operitFactoryCache;
            }

            function ensureModuleInstanceCache() {
                if (!root.__operitModuleInstanceCache || typeof root.__operitModuleInstanceCache !== 'object') {
                    root.__operitModuleInstanceCache = Object.create(null);
                }
                return root.__operitModuleInstanceCache;
            }

            function buildFactoryKey(kind, identity, source) {
                return [text(kind), text(identity), text(source).length, hashText(source)].join(':');
            }

            function buildModuleInstanceKey(kind, identity, source) {
                return ['instance', text(kind), text(identity), text(source).length, hashText(source)].join(':');
            }

            function createFactory(source) {
                return new Function(
                    'module',
                    'exports',
                    'require',
                    '__operit_call_runtime',
                    runtimePrelude + '\n' + source
                );
            }

            function getFactory(kind, identity, source) {
                var key = buildFactoryKey(kind, identity, source);
                var cache = ensureFactoryCache();
                if (typeof cache[key] === 'function') {
                    return cache[key];
                }
                var factory = createFactory(source);
                cache[key] = factory;
                return factory;
            }

            function tagModuleExports(modulePath, exportsRef) {
                if (typeof exportsRef === 'function') {
                    try { exportsRef.__operit_toolpkg_module_path = modulePath; } catch (_e) {}
                    return;
                }
                if (!exportsRef || typeof exportsRef !== 'object') {
                    return;
                }
                try { exportsRef.__operit_toolpkg_module_path = modulePath; } catch (_e) {}
                Object.keys(exportsRef).forEach(function(key) {
                    if (typeof exportsRef[key] === 'function') {
                        try { exportsRef[key].__operit_toolpkg_module_path = modulePath; } catch (_e) {}
                        try { exportsRef[key].__operit_toolpkg_export_name = key; } catch (_e2) {}
                    }
                });
            }

            function createRegistrationScreenPlaceholder(modulePath) {
                function ScreenPlaceholder() {
                    return null;
                }
                try { ScreenPlaceholder.__operit_toolpkg_module_path = modulePath; } catch (_e) {}
                return ScreenPlaceholder;
            }

            function normalizeComposeResult(value) {
                if (!value || typeof value !== 'object' || !value.composeDsl || typeof value.composeDsl !== 'object') {
                    return value;
                }
                if (!Object.prototype.hasOwnProperty.call(value.composeDsl, 'screen')) {
                    return value;
                }
                var screenRef = value.composeDsl.screen;
                var resolved = '';
                if (typeof screenRef === 'function') {
                    resolved = text(screenRef.__operit_toolpkg_module_path).trim();
                } else if (
                    screenRef &&
                    typeof screenRef === 'object' &&
                    typeof screenRef.default === 'function'
                ) {
                    resolved = text(screenRef.default.__operit_toolpkg_module_path).trim();
                } else if (typeof screenRef === 'string') {
                    throw new Error('composeDsl.screen must be a compose_dsl screen function, not a string path');
                }
                if (!resolved) {
                    throw new Error('composeDsl.screen is missing a toolpkg module path marker');
                }
                value.composeDsl.screen = resolved.replace(/\\/g, '/');
                return value;
            }

            function findTargetFunction(exportsRef, moduleRef, functionName) {
                if (exportsRef && typeof exportsRef[functionName] === 'function') {
                    return exportsRef[functionName];
                }
                if (moduleRef && moduleRef.exports && typeof moduleRef.exports[functionName] === 'function') {
                    return moduleRef.exports[functionName];
                }
                if (typeof root[functionName] === 'function') {
                    return root[functionName];
                }
                return null;
            }

            function buildAvailableFunctions(exportsRef, moduleRef) {
                var names = [];
                function collect(target) {
                    if (!target || typeof target !== 'object') {
                        return;
                    }
                    Object.keys(target).forEach(function(key) {
                        if (typeof target[key] === 'function' && names.indexOf(key) < 0) {
                            names.push(key);
                        }
                    });
                }
                collect(exportsRef);
                collect(moduleRef && moduleRef.exports ? moduleRef.exports : null);
                return names;
            }

            root.$TOOLPKG_EXECUTION_ENTRY_FUNCTION = function(
                callId,
                params,
                scriptText,
                targetFunctionName,
                timeoutSec,
                preTimeoutMs
            ) {
                var registerCallSession =
                    typeof root.__operitRegisterCallSession === 'function'
                        ? root.__operitRegisterCallSession
                        : null;
                if (typeof registerCallSession !== 'function') {
                    NativeInterface.setCallError(
                        callId,
                        JSON.stringify({
                            success: false,
                            message: 'JS execution runtime bridge is unavailable'
                        })
                    );
                    return;
                }
                var hasTimeout = timeoutSec !== null && typeof timeoutSec !== 'undefined';
                var safeTimeoutSec = hasTimeout ? Math.max(1, Number(timeoutSec) || 1) : null;
                var safePreTimeoutMs = hasTimeout
                    ? Math.max(1000, Number(preTimeoutMs) || 1000)
                    : null;
                var callState = registerCallSession(callId, params);
                var previousCallId = root.__operitCurrentCallId;
                var previousCallRuntime = root.__operit_call_runtime_ref;
                root.__operitCurrentCallId = callId;

                function markStage(stage) {
                    if (callState) {
                        callState.lastExecStage = text(stage);
                    }
                }

                function markFunction(name) {
                    if (callState) {
                        callState.lastExecFunction = text(name);
                    }
                }

                function markRequire(request, fromPath, resolvedPath) {
                    if (!callState) {
                        return;
                    }
                    callState.lastRequireRequest = text(request);
                    callState.lastRequireFrom = text(fromPath);
                    callState.lastRequireResolved = text(resolvedPath);
                }

                function markModule(modulePath) {
                    if (callState) {
                        callState.lastModulePath = text(modulePath);
                    }
                }

                function clearExecutionTimeouts() {
                    if (!callState) {
                        return;
                    }
                    try {
                        if (callState.safetyTimeout) clearTimeout(callState.safetyTimeout);
                        if (callState.safetyTimeoutFinal) clearTimeout(callState.safetyTimeoutFinal);
                    } catch (_e) {
                    }
                    callState.safetyTimeout = null;
                    callState.safetyTimeoutFinal = null;
                }

                function finalizeCall() {
                    clearExecutionTimeouts();
                    if (root.__operitCurrentCallId === callId) {
                        root.__operitCurrentCallId =
                            typeof previousCallId === 'string' ? previousCallId : '';
                    }
                    if (root.__operit_call_runtime_ref === callRuntime) {
                        if (
                            previousCallRuntime &&
                            typeof previousCallRuntime === 'object'
                        ) {
                            root.__operit_call_runtime_ref = previousCallRuntime;
                        } else {
                            try {
                                delete root.__operit_call_runtime_ref;
                            } catch (_deleteRuntimeError) {
                                root.__operit_call_runtime_ref = null;
                            }
                        }
                    }
                    if (typeof root.__operitCleanupCallSession === 'function') {
                        root.__operitCleanupCallSession(callId);
                    }
                }

                function isActive() {
                    var state = getCallState(callId);
                    return !!(state && !state.completed);
                }

                function emitSerializedResult(resultText) {
                    var state = getCallState(callId);
                    if (!state || state.completed) {
                        return;
                    }
                    state.completed = true;
                    NativeInterface.setCallResult(callId, resultText);
                    finalizeCall();
                }

                function emitError(message) {
                    var state = getCallState(callId);
                    if (!state || state.completed) {
                        return;
                    }
                    state.completed = true;
                    NativeInterface.setCallError(
                        callId,
                        JSON.stringify({
                            success: false,
                            message: text(message)
                        })
                    );
                    finalizeCall();
                }

                function readCallValue(key, defaultValue) {
                    var state = getCallState(callId);
                    var currentParams =
                        state && state.params && typeof state.params === 'object'
                            ? state.params
                            : null;
                    var value = currentParams ? currentParams[key] : undefined;
                    return value == null || value === '' ? defaultValue : text(value);
                }

                if (hasTimeout) {
                    callState.safetyTimeout = setTimeout(function() {
                        if (!isActive()) {
                            return;
                        }
                        callState.safetyTimeoutFinal = setTimeout(function() {
                            emitError('Script execution timed out after ' + safeTimeoutSec + ' seconds');
                        }, 5000);
                    }, safePreTimeoutMs);
                }

                function callRuntimeReport(error, context) {
                    if (typeof root.__operitReportDetailedErrorForCall === 'function') {
                        return root.__operitReportDetailedErrorForCall(callId, error, context);
                    }
                    return {
                        formatted: text(context) + ': ' + text(error),
                        details: { message: text(error), stack: text(error), lineNumber: 0 }
                    };
                }

                function emitIntermediate(value) {
                    if (isActive()) {
                        NativeInterface.sendCallIntermediateResult(callId, safeSerialize(value));
                    }
                }

                function complete(value) {
                    try {
                        emitSerializedResult(serializeOrThrow(normalizeComposeResult(value)));
                    } catch (error) {
                        var report = callRuntimeReport(error, 'Result Serialization Failure');
                        var serializationMessage =
                            report &&
                            report.details &&
                            typeof report.details.message === 'string' &&
                            report.details.message
                                ? report.details.message
                                : text(error && error.message ? error.message : error);
                        emitError('Result serialization failed: ' + serializationMessage);
                    }
                }

                function handleAsync(value) {
                    if (!value || typeof value.then !== 'function') {
                        return false;
                    }
                    Promise.resolve(value)
                        .then(function(result) {
                            if (isActive()) {
                                complete(result);
                            }
                        })
                        .catch(function(error) {
                            if (!isActive()) {
                                return;
                            }
                            var report = callRuntimeReport(error, 'Async Promise Rejection');
                            var rejectionMessage =
                                report &&
                                report.details &&
                                typeof report.details.message === 'string' &&
                                report.details.message
                                    ? report.details.message
                                    : text(error && error.message ? error.message : error);
                            emitError(rejectionMessage || 'Promise rejection');
                        });
                    return true;
                }

                function createRuntime() {
                    return {
                        emit: emitIntermediate,
                        delta: emitIntermediate,
                        log: emitIntermediate,
                        update: emitIntermediate,
                        sendIntermediateResult: emitIntermediate,
                        done: complete,
                        complete: complete,
                        getEnv: function(key) {
                            var value = NativeInterface.getEnvForCall(callId, text(key).trim());
                            return value == null || value === '' ? undefined : text(value);
                        },
                        getPluginConfigDir: function(pluginId) {
                            var explicitId = pluginId == null ? '' : text(pluginId).trim();
                            var resolvedId =
                                explicitId ||
                                readCallValue('__operit_ui_package_name', '') ||
                                readCallValue('toolPkgId', '') ||
                                readCallValue('containerPackageName', '') ||
                                readCallValue('__operit_package_name', '');
                            if (
                                !resolvedId ||
                                typeof NativeInterface === 'undefined' ||
                                !NativeInterface ||
                                typeof NativeInterface.getPluginConfigDir !== 'function'
                            ) {
                                return '';
                            }
                            var path = NativeInterface.getPluginConfigDir(resolvedId);
                            return typeof path === 'string' ? path : '';
                        },
                        getState: function() { return readCallValue('__operit_package_state', undefined); },
                        getLang: function() { return readCallValue('__operit_package_lang', 'en'); },
                        getCallerName: function() { return readCallValue('__operit_package_caller_name', undefined); },
                        getChatId: function() { return readCallValue('__operit_package_chat_id', undefined); },
                        getCallerCardId: function() { return readCallValue('__operit_package_caller_card_id', undefined); },
                        reportDetailedError: callRuntimeReport,
                        handleAsync: handleAsync,
                        console: {
                            log: function() { NativeInterface.logInfoForCall(callId, Array.prototype.slice.call(arguments).join(' ')); },
                            info: function() { NativeInterface.logInfoForCall(callId, Array.prototype.slice.call(arguments).join(' ')); },
                            warn: function() { NativeInterface.logInfoForCall(callId, Array.prototype.slice.call(arguments).join(' ')); },
                            error: function() { NativeInterface.logErrorForCall(callId, Array.prototype.slice.call(arguments).join(' ')); }
                        }
                    };
                }

                var callRuntime = createRuntime();
                root.__operit_call_runtime_ref = callRuntime;
                var registrationMode = toBoolean(readCallValue('__operit_registration_mode', false));
                var packageTarget =
                    readCallValue('__operit_ui_package_name', '') ||
                    readCallValue('toolPkgId', '');
                var screenPath = normalizePath(
                    readCallValue(
                        '__operit_script_screen',
                        params && params.moduleSpec && params.moduleSpec.screen
                            ? text(params.moduleSpec.screen)
                            : ''
                    )
                );
                var moduleCache = registrationMode
                    ? Object.create(null)
                    : ensureModuleInstanceCache();

                function readToolPkgModule(modulePath) {
                    if (
                        !packageTarget ||
                        typeof NativeInterface === 'undefined' ||
                        !NativeInterface ||
                        typeof NativeInterface.readToolPkgTextResource !== 'function'
                    ) {
                        return null;
                    }
                    var candidates = buildCandidatePaths(modulePath);
                    for (var i = 0; i < candidates.length; i += 1) {
                        var candidate = candidates[i];
                        var textResult = NativeInterface.readToolPkgTextResource(packageTarget, candidate);
                        if (typeof textResult === 'string' && textResult.length > 0) {
                            return { path: candidate, text: textResult };
                        }
                    }
                    return null;
                }

                function getCurrentToolPkgRuntimeKind() {
                    var explicitRuntime = text(
                        readCallValue('__operit_toolpkg_runtime_kind', '')
                    ).trim().toLowerCase();
                    if (
                        explicitRuntime === 'main' ||
                        explicitRuntime === 'ui' ||
                        explicitRuntime === 'sandbox' ||
                        explicitRuntime === 'provider'
                    ) {
                        return explicitRuntime;
                    }
                    var contextKey = text(
                        readCallValue(
                            '__operit_execution_context_key',
                            readCallValue('executionContextKey', '')
                        )
                    ).trim();
                    if (/^toolpkg_provider:/i.test(contextKey)) {
                        return 'provider';
                    }
                    var subpackageId = text(
                        readCallValue('__operit_toolpkg_subpackage_id', '')
                    ).trim();
                    return subpackageId.length > 0 ? 'sandbox' : 'main';
                }

                function isLocalUiModulePath(modulePath) {
                    return /\.ui\.js$/i.test(normalizePath(modulePath));
                }

                function getCurrentToolPkgExecutionContextKey() {
                    var composeContextKey = text(
                        readCallValue(
                            '__operit_compose_execution_context_key',
                            readCallValue('executionContextKey', '')
                        )
                    ).trim();
                    var scopedContextKey = text(
                        readCallValue('__operit_execution_context_key', '')
                    ).trim();
                    if (composeContextKey.length > 0) {
                        return composeContextKey;
                    }
                    if (scopedContextKey.length > 0) {
                        return scopedContextKey;
                    }
                    return packageTarget ? 'toolpkg_main:' + packageTarget : '';
                }

                function ensureToolPkgIpcRegistry() {
                    var registry = root.__operitToolPkgIpcRegistry;
                    if (!registry || typeof registry !== 'object') {
                        registry = Object.create(null);
                        root.__operitToolPkgIpcRegistry = registry;
                    }
                    return registry;
                }

                function normalizeToolPkgIpcChannel(channel) {
                    return text(channel).trim();
                }

                function registerToolPkgIpcHandler(channel, handler) {
                    var normalizedChannel = normalizeToolPkgIpcChannel(channel);
                    if (normalizedChannel.length === 0) {
                        throw new Error('ToolPkg.ipc channel is required');
                    }
                    if (typeof handler !== 'function') {
                        throw new Error('ToolPkg.ipc handler must be a function');
                    }
                    ensureToolPkgIpcRegistry()[normalizedChannel] = handler;
                    return function() {
                        var registry = ensureToolPkgIpcRegistry();
                        if (registry[normalizedChannel] === handler) {
                            delete registry[normalizedChannel];
                        }
                    };
                }

                function unregisterToolPkgIpcHandler(channel, handler) {
                    var normalizedChannel = normalizeToolPkgIpcChannel(channel);
                    if (normalizedChannel.length === 0) {
                        return false;
                    }
                    var registry = ensureToolPkgIpcRegistry();
                    if (arguments.length > 1 && registry[normalizedChannel] !== handler) {
                        return false;
                    }
                    if (typeof registry[normalizedChannel] === 'function') {
                        delete registry[normalizedChannel];
                        return true;
                    }
                    return false;
                }

                function invokeToolPkgIpcLocal(channel, payload, meta) {
                    var normalizedChannel = normalizeToolPkgIpcChannel(channel);
                    if (normalizedChannel.length === 0) {
                        throw new Error('ToolPkg.ipc channel is required');
                    }
                    var registry = ensureToolPkgIpcRegistry();
                    var handler = registry[normalizedChannel];
                    if (typeof handler !== 'function') {
                        throw new Error('ToolPkg.ipc channel is not registered: ' + normalizedChannel);
                    }
                    return handler(
                        payload,
                        meta && typeof meta === 'object' ? meta : {}
                    );
                }

                function ensureToolPkgIpcApi() {
                    var toolPkgApi = root.ToolPkg && typeof root.ToolPkg === 'object'
                        ? root.ToolPkg
                        : {};
                    if (root.ToolPkg !== toolPkgApi) {
                        root.ToolPkg = toolPkgApi;
                    }
                    var ipcApi = toolPkgApi.ipc && typeof toolPkgApi.ipc === 'object'
                        ? toolPkgApi.ipc
                        : {};

                    ipcApi.on = function(channel, handler) {
                        return registerToolPkgIpcHandler(channel, handler);
                    };
                    ipcApi.off = function(channel, handler) {
                        return unregisterToolPkgIpcHandler(channel, handler);
                    };
                    ipcApi.call = function(channel, payload, options) {
                        var normalizedChannel = normalizeToolPkgIpcChannel(channel);
                        if (normalizedChannel.length === 0) {
                            return Promise.reject(new Error('ToolPkg.ipc channel is required'));
                        }
                        var callOptions = options && typeof options === 'object' ? options : {};
                        var targetRuntime = text(callOptions.targetRuntime || '').trim().toLowerCase();
                        if (
                            targetRuntime &&
                            targetRuntime !== 'main' &&
                            targetRuntime !== 'ui' &&
                            targetRuntime !== 'sandbox' &&
                            targetRuntime !== 'provider'
                        ) {
                            return Promise.reject(new Error('ToolPkg.ipc targetRuntime is invalid: ' + targetRuntime));
                        }
                        var targetContextKey = text(callOptions.targetContextKey || '').trim();
                        var hasTargetOptions = targetRuntime.length > 0 || targetContextKey.length > 0;
                        var currentContextKey = getCurrentToolPkgExecutionContextKey();
                        var currentRuntime = getCurrentToolPkgRuntimeKind();
                        if (
                            currentRuntime === 'main' &&
                            (
                                !hasTargetOptions ||
                                (
                                    (targetRuntime.length === 0 || targetRuntime === 'main') &&
                                    (targetContextKey.length === 0 || targetContextKey === currentContextKey)
                                )
                            )
                        ) {
                            try {
                                return Promise.resolve(
                                    invokeToolPkgIpcLocal(normalizedChannel, payload, {
                                        channel: normalizedChannel,
                                        callerContextKey: currentContextKey,
                                        currentContextKey: currentContextKey,
                                        currentRuntime: currentRuntime,
                                        packageTarget: packageTarget
                                    })
                                );
                            } catch (error) {
                                return Promise.reject(error);
                            }
                        }
                        if (
                            targetContextKey.length > 0 &&
                            targetContextKey === currentContextKey &&
                            targetRuntime.length > 0 &&
                            targetRuntime !== currentRuntime
                        ) {
                            return Promise.reject(
                                new Error(
                                    'ToolPkg.ipc targetRuntime does not match current runtime: ' +
                                        targetRuntime +
                                        ' != ' +
                                        currentRuntime
                                )
                            );
                        }
                        if (targetContextKey.length > 0 && targetContextKey === currentContextKey) {
                            try {
                                return Promise.resolve(
                                    invokeToolPkgIpcLocal(normalizedChannel, payload, {
                                        channel: normalizedChannel,
                                        callerContextKey: currentContextKey,
                                        currentContextKey: currentContextKey,
                                        currentRuntime: currentRuntime,
                                        packageTarget: packageTarget
                                    })
                                );
                            } catch (error) {
                                return Promise.reject(error);
                            }
                        }
                        if (
                            !packageTarget ||
                            typeof NativeInterface === 'undefined' ||
                            !NativeInterface ||
                            typeof NativeInterface.invokeToolPkgIpcAsync !== 'function'
                        ) {
                            return Promise.reject(new Error('ToolPkg.ipc runtime bridge is unavailable'));
                        }
                        var payloadJson;
                        try {
                            payloadJson = serializeOrThrow(payload);
                        } catch (error) {
                            try {
                                if (
                                    typeof NativeInterface !== 'undefined' &&
                                    NativeInterface &&
                                    typeof NativeInterface.logErrorForCall === 'function'
                                ) {
                                    NativeInterface.logErrorForCall(
                                        callId,
                                        'ToolPkg.ipc payload serialization failed: ' +
                                            text(error && error.message ? error.message : error)
                                    );
                                }
                            } catch (_logIpcPayloadError) {}
                            return Promise.reject(error);
                        }
                        return new Promise(function(resolve, reject) {
                            var callbackId =
                                '__operit_toolpkg_ipc_' +
                                Date.now() +
                                '_' +
                                Math.random().toString(36).slice(2, 10);
                            root[callbackId] = function(resultJson, isError) {
                                try {
                                    delete root[callbackId];
                                } catch (_deleteCallbackError) {
                                    root[callbackId] = undefined;
                                }
                                if (isError) {
                                    reject(new Error(text(resultJson).trim() || 'ToolPkg.ipc call failed'));
                                    return;
                                }
                                var parsed;
                                try {
                                    parsed = JSON.parse(text(resultJson) || 'null');
                                } catch (error) {
                                    try {
                                        if (
                                            typeof NativeInterface !== 'undefined' &&
                                            NativeInterface &&
                                            typeof NativeInterface.logErrorForCall === 'function'
                                        ) {
                                            var resultType = resultJson === null ? 'null' : typeof resultJson;
                                            var preview = text(resultJson).slice(0, 500);
                                            NativeInterface.logErrorForCall(
                                                callId,
                                                'ToolPkg.ipc returned invalid JSON: ' +
                                                    text(error && error.message ? error.message : error) +
                                                    ', resultType=' + resultType +
                                                    ', preview=' + preview
                                            );
                                        }
                                    } catch (_logIpcParseError) {}
                                    reject(
                                        new Error(
                                            'ToolPkg.ipc returned invalid JSON: ' +
                                                text(error && error.message ? error.message : error)
                                        )
                                    );
                                    return;
                                }
                                if (parsed && parsed.success === true) {
                                    resolve(parsed.value);
                                    return;
                                }
                                reject(
                                    new Error(
                                        parsed && typeof parsed.message === 'string' && parsed.message.trim().length > 0
                                            ? parsed.message.trim()
                                            : 'ToolPkg.ipc call failed'
                                    )
                                );
                            };
                            try {
                                NativeInterface.invokeToolPkgIpcAsync(
                                    callbackId,
                                    packageTarget,
                                    currentContextKey,
                                    targetContextKey,
                                    targetRuntime,
                                    normalizedChannel,
                                    payloadJson
                                );
                            } catch (error) {
                                try {
                                    delete root[callbackId];
                                } catch (_deleteCallbackInvokeError) {
                                    root[callbackId] = undefined;
                                }
                                reject(error);
                            }
                        });
                    };

                    toolPkgApi.ipc = ipcApi;
                    root.__operitInvokeToolPkgIpcLocal = invokeToolPkgIpcLocal;
                }

                function ensureToolPkgWasmApi() {
                    var toolPkgApi = root.ToolPkg && typeof root.ToolPkg === 'object'
                        ? root.ToolPkg
                        : {};
                    if (root.ToolPkg !== toolPkgApi) {
                        root.ToolPkg = toolPkgApi;
                    }
                    var wasmApi = toolPkgApi.wasm && typeof toolPkgApi.wasm === 'object'
                        ? toolPkgApi.wasm
                        : {};

                    function isFiniteNumber(value) {
                        return typeof value === 'number' && isFinite(value);
                    }

                    function requireIntegerNumber(value, label) {
                        if (!isFiniteNumber(value) || value % 1 !== 0) {
                            throw new Error(label + ' must be an integer');
                        }
                        return value;
                    }

                    function normalizeI32(value, label) {
                        var numberValue = requireIntegerNumber(Number(value), label);
                        if (numberValue < -2147483648 || numberValue > 2147483647) {
                            throw new Error(label + ' is outside i32 range');
                        }
                        return numberValue;
                    }

                    function normalizeI64(value, label) {
                        if (typeof value === 'bigint') {
                            return value.toString();
                        }
                        if (typeof value === 'string') {
                            var trimmed = value.trim();
                            if (!/^-?[0-9]+$/.test(trimmed)) {
                                throw new Error(label + ' must be a signed integer string');
                            }
                            return trimmed;
                        }
                        if (typeof value === 'number') {
                            var numberValue = requireIntegerNumber(value, label);
                            if (Math.abs(numberValue) > 9007199254740991) {
                                throw new Error(label + ' i64 number must be passed as a string');
                            }
                            return String(numberValue);
                        }
                        throw new Error(label + ' must be an i64 string');
                    }

                    function normalizeFloat(value, label) {
                        var numberValue = Number(value);
                        if (!isFiniteNumber(numberValue)) {
                            throw new Error(label + ' must be a finite number');
                        }
                        return numberValue;
                    }

                    function normalizeWasmArg(arg, index) {
                        if (!arg || typeof arg !== 'object' || Array.isArray(arg)) {
                            throw new Error('WASM argument ' + index + ' must be an object');
                        }
                        var type = text(arg.type).trim().toLowerCase();
                        if (!type) {
                            throw new Error('WASM argument ' + index + ' type is required');
                        }
                        if (!Object.prototype.hasOwnProperty.call(arg, 'value')) {
                            throw new Error('WASM argument ' + index + ' value is required');
                        }
                        var label = 'WASM argument ' + index;
                        if (type === 'i32') {
                            return { type: 'i32', value: normalizeI32(arg.value, label) };
                        }
                        if (type === 'i64') {
                            return { type: 'i64', value: normalizeI64(arg.value, label) };
                        }
                        if (type === 'f32') {
                            return { type: 'f32', value: normalizeFloat(arg.value, label) };
                        }
                        if (type === 'f64') {
                            return { type: 'f64', value: normalizeFloat(arg.value, label) };
                        }
                        throw new Error('Unsupported WASM argument type at index ' + index + ': ' + type);
                    }

                    function decodeWasmValue(result) {
                        if (!result || typeof result !== 'object') {
                            return undefined;
                        }
                        var type = text(result.type).trim().toLowerCase();
                        var value = result.value;
                        if (type === 'i32') {
                            return Number(value);
                        }
                        if (type === 'i64') {
                            return text(value);
                        }
                        if (type === 'f32' || type === 'f64') {
                            if (value === 'NaN') {
                                return NaN;
                            }
                            if (value === 'Infinity') {
                                return Infinity;
                            }
                            if (value === '-Infinity') {
                                return -Infinity;
                            }
                            return Number(value);
                        }
                        return value;
                    }

                    function decodeWasmResults(results) {
                        if (!Array.isArray(results) || results.length === 0) {
                            return undefined;
                        }
                        if (results.length === 1) {
                            return decodeWasmValue(results[0]);
                        }
                        return results.map(function(item) {
                            var decoded = {
                                type: text(item && item.type),
                                value: decodeWasmValue(item)
                            };
                            if (item && Object.prototype.hasOwnProperty.call(item, 'bits')) {
                                decoded.bits = text(item.bits);
                            }
                            return decoded;
                        });
                    }

                    wasmApi.call = function(moduleId, exportName, args) {
                        var normalizedModuleId = text(moduleId).trim();
                        var normalizedExportName = text(exportName).trim();
                        if (!normalizedModuleId) {
                            return Promise.reject(new Error('ToolPkg.wasm module id is required'));
                        }
                        if (!normalizedExportName) {
                            return Promise.reject(new Error('ToolPkg.wasm export name is required'));
                        }
                        if (
                            !packageTarget ||
                            typeof NativeInterface === 'undefined' ||
                            !NativeInterface ||
                            typeof NativeInterface.callToolPkgWasm !== 'function'
                        ) {
                            return Promise.reject(new Error('ToolPkg.wasm runtime bridge is unavailable'));
                        }

                        var rawArgs = args == null ? [] : args;
                        if (!Array.isArray(rawArgs)) {
                            return Promise.reject(new Error('ToolPkg.wasm args must be an array'));
                        }

                        return new Promise(function(resolve, reject) {
                            try {
                                var normalizedArgs = rawArgs.map(function(arg, index) {
                                    return normalizeWasmArg(arg, index);
                                });
                                var resultJson = NativeInterface.callToolPkgWasm(
                                    packageTarget,
                                    normalizedModuleId,
                                    normalizedExportName,
                                    JSON.stringify(normalizedArgs)
                                );
                                var parsed;
                                try {
                                    parsed = JSON.parse(text(resultJson) || '{}');
                                } catch (error) {
                                    reject(
                                        new Error(
                                            'ToolPkg.wasm returned invalid JSON: ' +
                                                text(error && error.message ? error.message : error)
                                        )
                                    );
                                    return;
                                }
                                if (!parsed || parsed.success !== true) {
                                    reject(
                                        new Error(
                                            parsed && typeof parsed.message === 'string' && parsed.message.trim().length > 0
                                                ? parsed.message.trim()
                                                : 'ToolPkg.wasm call failed'
                                        )
                                    );
                                    return;
                                }
                                resolve(decodeWasmResults(parsed.results));
                            } catch (error) {
                                reject(error);
                            }
                        });
                    };

                    toolPkgApi.wasm = wasmApi;
                }

                ensureToolPkgIpcApi();
                ensureToolPkgWasmApi();

                function canSerializeAsPlainObject(value) {
                    if (!value || typeof value !== 'object') {
                        return false;
                    }
                    if (Array.isArray(value)) {
                        return true;
                    }
                    var prototype = Object.getPrototypeOf(value);
                    return prototype === Object.prototype || prototype === null;
                }

                function executeModule(modulePath, moduleText, requireInternal) {
                    markStage('execute_required_module');
                    markModule(modulePath);

                    var moduleKey = buildModuleInstanceKey('module', packageTarget + ':' + modulePath, moduleText);
                    if (moduleCache[moduleKey]) {
                        return moduleCache[moduleKey].exports;
                    }

                    var module = { exports: {} };
                    moduleCache[moduleKey] = module;

                    if (/\.json$/i.test(modulePath)) {
                        try {
                            module.exports = JSON.parse(moduleText);
                            return module.exports;
                        } catch (error) {
                            delete moduleCache[moduleKey];
                            throw error;
                        }
                    }

                    var localRequire = function(nextName) {
                        return requireInternal(nextName, modulePath);
                    };
                    var factory = getFactory('module', packageTarget + ':' + modulePath, moduleText);
                    var previousActiveModule = root.__operitActiveModule;
                    var previousActiveExports = root.__operitActiveModuleExports;
                    root.__operitActiveModule = module;
                    root.__operitActiveModuleExports = module.exports;
                    try {
                        factory(module, module.exports, localRequire, callRuntime);
                    } catch (error) {
                        delete moduleCache[moduleKey];
                        throw error;
                    } finally {
                        root.__operitActiveModule = previousActiveModule;
                        root.__operitActiveModuleExports = previousActiveExports;
                    }
                    tagModuleExports(modulePath, module.exports);
                    return module.exports;
                }

                function requireInternal(moduleName, fromPath) {
                    var request = text(moduleName).trim();
                    if (request === 'lodash') {
                        return root._;
                    }
                    if (request === 'uuid') {
                        return {
                            v4: function() {
                                return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(char) {
                                    var random = Math.random() * 16 | 0;
                                    var value = char === 'x' ? random : ((random & 0x3) | 0x8);
                                    return value.toString(16);
                                });
                            }
                        };
                    }
                    if (request === 'axios') {
                        return {
                            get: function(url, config) {
                                return root.toolCall('http_request', config ? Object.assign({ url: url }, config) : { url: url });
                            },
                            post: function(url, data, config) {
                                return root.toolCall('http_request', config ? Object.assign({ url: url, data: data }, config) : { url: url, data: data });
                            }
                        };
                    }
                    if (!(request.startsWith('.') || request.startsWith('/'))) {
                        return {};
                    }

                    var resolvedPath = resolveModulePath(request, fromPath || screenPath);
                    markStage('require_module');
                    markRequire(request, fromPath || screenPath || '<root>', resolvedPath);
                    markModule(resolvedPath);

                    if (registrationMode && isLocalUiModulePath(resolvedPath)) {
                        return createRegistrationScreenPlaceholder(resolvedPath);
                    }

                    var loaded = readToolPkgModule(resolvedPath);
                    if (!loaded) {
                        throw new Error(
                            'Cannot resolve module "' + request + '" from "' + (fromPath || screenPath || '<root>') + '"'
                        );
                    }
                    return executeModule(loaded.path, loaded.text, requireInternal);
                }

                try {
                    markFunction(targetFunctionName);
                    var mainModuleIdentity = packageTarget + ':' + (screenPath || '<root>');
                    var mainModuleKey = buildModuleInstanceKey('main', mainModuleIdentity, scriptText);
                    var module = moduleCache[mainModuleKey];
                    var exports = module && module.exports ? module.exports : null;
                    var require = function(moduleName) {
                        markStage('require_request');
                        markRequire(moduleName, screenPath || '<root>', '');
                        return requireInternal(moduleName, screenPath);
                    };

                    if (!module) {
                        module = { exports: {} };
                        moduleCache[mainModuleKey] = module;
                        exports = module.exports;
                        markStage('compile_main_script');
                        var mainFactory = getFactory('main', packageTarget + ':' + screenPath, scriptText);
                        markStage('execute_main_script');
                        var previousActiveModule = root.__operitActiveModule;
                        var previousActiveExports = root.__operitActiveModuleExports;
                        root.__operitActiveModule = module;
                        root.__operitActiveModuleExports = exports;
                        try {
                            mainFactory(module, exports, require, callRuntime);
                        } catch (error) {
                            delete moduleCache[mainModuleKey];
                            throw error;
                        } finally {
                            root.__operitActiveModule = previousActiveModule;
                            root.__operitActiveModuleExports = previousActiveExports;
                        }
                    } else {
                        if (exports == null) {
                            exports = {};
                            module.exports = exports;
                        }
                        markStage('reuse_main_script');
                    }
                    var rootExports = module.exports || exports || {};
                    tagModuleExports(screenPath || '<root>', rootExports);

                    var inlineFunctionName = readCallValue('__operit_inline_function_name', '');
                    var inlineFunctionSource = readCallValue('__operit_inline_function_source', '');
                    if (inlineFunctionName && inlineFunctionSource) {
                        markStage('evaluate_inline_hook_function');
                        var inlineFunction = eval('(' + inlineFunctionSource + ')');
                        if (typeof inlineFunction !== 'function') {
                            throw new Error('inline hook source did not evaluate to function');
                        }
                        rootExports[inlineFunctionName] = inlineFunction;
                        module.exports[inlineFunctionName] = inlineFunction;
                    }

                    var targetFunction = findTargetFunction(rootExports, module, targetFunctionName);
                    if (typeof targetFunction !== 'function') {
                        emitError(
                            "Function '" +
                                targetFunctionName +
                                "' not found in script. Available functions: " +
                                buildAvailableFunctions(rootExports, module).join(', ')
                        );
                        return;
                    }

                    markStage('invoke_target_function');
                    var previousModule = callState.currentModule;
                    var previousExports = callState.currentModuleExports;
                    var previousActiveModule = root.__operitActiveModule;
                    var previousActiveExports = root.__operitActiveModuleExports;
                    callState.currentModule = module;
                    callState.currentModuleExports = rootExports;
                    root.__operitActiveModule = module;
                    root.__operitActiveModuleExports = rootExports;
                    var functionResult;
                    try {
                        functionResult = targetFunction(params);
                    } finally {
                        callState.currentModule = previousModule;
                        callState.currentModuleExports = previousExports;
                        root.__operitActiveModule = previousActiveModule;
                        root.__operitActiveModuleExports = previousActiveExports;
                    }

                    markStage('handle_function_result');
                    if (!handleAsync(functionResult)) {
                        complete(functionResult);
                    }
                } catch (error) {
                    var runtimeContext = typeof root.__operitBuildRuntimeContext === 'function'
                        ? text(root.__operitBuildRuntimeContext(callId))
                        : '';
                    emitError(
                        'Script error: ' +
                            text(error && error.message ? error.message : error) +
                            (runtimeContext ? '\nRuntime Context: ' + runtimeContext : '') +
                            (error && error.stack ? '\nStack: ' + text(error.stack) : '')
                    );
                }
            };
        })();
    """.trimIndent()
}

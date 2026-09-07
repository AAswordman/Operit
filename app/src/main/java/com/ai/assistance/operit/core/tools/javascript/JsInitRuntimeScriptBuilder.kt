package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONObject

internal fun buildInitRuntimeModules(
    operitDownloadDir: String,
    operitCleanOnExitDir: String
): List<JsBootstrapModule> {
    return listOf(
        JsBootstrapModule(
            fileName = "quickjs/init/runtime-expose.js",
            source = buildRuntimeExposeScript()
        ),
        JsBootstrapModule(
            fileName = "quickjs/init/runtime-constants.js",
            source = buildRuntimeConstantsScript(
                operitDownloadDir = operitDownloadDir,
                operitCleanOnExitDir = operitCleanOnExitDir
            )
        ),
        JsBootstrapModule(
            fileName = "quickjs/init/runtime-call-registry.js",
            source = buildRuntimeCallRegistryScript()
        ),
        JsBootstrapModule(
            fileName = "quickjs/init/toolpkg-api-runtime.js",
            source = buildToolPkgApiRuntimeScript()
        ),
        JsBootstrapModule(
            fileName = "quickjs/init/runtime-errors.js",
            source = buildRuntimeErrorScript()
        ),
        JsBootstrapModule(
            fileName = "quickjs/init/runtime-tool-call.js",
            source = buildRuntimeToolCallScript(),
            globals = listOf("toolCall")
        )
    )
}

private fun buildRuntimeExposeScript(): String {
    return """
        function __operitExpose(name, value) {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var key = name == null ? '' : String(name).trim();
            if (!key || value === undefined) {
                return;
            }
            root[key] = value;
        }
    """.trimIndent()
}

private fun buildRuntimeConstantsScript(
    operitDownloadDir: String,
    operitCleanOnExitDir: String
): String {
    return """
        (function() {
            var expose = typeof __operitExpose === 'function'
                ? __operitExpose
                : globalThis.__operitExpose;
            if (typeof expose !== 'function') {
                throw new Error('__operitExpose is unavailable');
            }
            expose('OPERIT_DOWNLOAD_DIR', ${JSONObject.quote(operitDownloadDir)});
            expose('OPERIT_CLEAN_ON_EXIT_DIR', ${JSONObject.quote(operitCleanOnExitDir)});
        })();
    """.trimIndent()
}

private fun buildRuntimeCallRegistryScript(): String {
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var windowRef = typeof window !== 'undefined' ? window : root;
            var expose = typeof __operitExpose === 'function'
                ? __operitExpose
                : globalThis.__operitExpose;

            function asString(value) {
                return value == null ? '' : String(value);
            }

            function normalizeCallId(value) {
                return asString(value).trim();
            }

            function ensureCallRegistry() {
                if (!windowRef.__operitCallRegistry || typeof windowRef.__operitCallRegistry !== 'object') {
                    windowRef.__operitCallRegistry = {};
                }
                return windowRef.__operitCallRegistry;
            }

            function getCallState(callId) {
                var resolvedCallId = normalizeCallId(callId);
                if (!resolvedCallId) {
                    return null;
                }
                var registry = ensureCallRegistry();
                var state = registry[resolvedCallId];
                return state && typeof state === 'object' ? state : null;
            }

            function clearCallTimers(callState) {
                if (!callState || typeof callState !== 'object') {
                    return;
                }
                try {
                    if (callState.safetyTimeout) {
                        clearTimeout(callState.safetyTimeout);
                    }
                    if (callState.safetyTimeoutFinal) {
                        clearTimeout(callState.safetyTimeoutFinal);
                    }
                } catch (_error) {
                }
                callState.safetyTimeout = null;
                callState.safetyTimeoutFinal = null;
            }

            function registerCallSession(callId, params, toolPkgApi) {
                var resolvedCallId = normalizeCallId(callId);
                if (!resolvedCallId) {
                    throw new Error('callId is required');
                }
                var registry = ensureCallRegistry();
                var state = registry[resolvedCallId];
                var callState = state && typeof state === 'object' ? state : {};
                callState.callId = resolvedCallId;
                callState.params = params && typeof params === 'object' ? params : {};
                callState.toolPkgApi =
                    toolPkgApi && typeof toolPkgApi === 'object'
                        ? toolPkgApi
                        : null;
                callState.completed = false;
                callState.safetyTimeout = null;
                callState.safetyTimeoutFinal = null;
                callState.lastExecStage = '';
                callState.lastExecFunction = '';
                callState.lastModulePath = '';
                callState.lastRequireRequest = '';
                callState.lastRequireFrom = '';
                callState.lastRequireResolved = '';
                callState.currentModule = null;
                callState.currentModuleExports = null;
                registry[resolvedCallId] = callState;
                return callState;
            }

            function cleanupCallSession(callId) {
                var resolvedCallId = normalizeCallId(callId);
                if (!resolvedCallId) {
                    return;
                }
                var registry = ensureCallRegistry();
                var callState = registry[resolvedCallId];
                clearCallTimers(callState);
                delete registry[resolvedCallId];
            }

            function cancelCallSession(callId) {
                var callState = getCallState(callId);
                if (!callState || callState.completed) {
                    return false;
                }
                callState.completed = true;
                clearCallTimers(callState);
                return true;
            }

            function buildRuntimeContext(callId) {
                var callState = getCallState(callId);
                var mapping = [
                    ['lastExecStage', 'stage'],
                    ['lastExecFunction', 'function'],
                    ['lastModulePath', 'module'],
                    ['lastRequireRequest', 'require'],
                    ['lastRequireFrom', 'from'],
                    ['lastRequireResolved', 'resolved']
                ];
                var parts = [];
                for (var i = 0; i < mapping.length; i += 1) {
                    var key = mapping[i][0];
                    var label = mapping[i][1];
                    var value = callState ? callState[key] : undefined;
                    if (value != null && asString(value).trim().length > 0) {
                        parts.push(label + '=' + asString(value));
                    }
                }
                return parts.join(', ');
            }

            expose('__operitGetCallState', getCallState);
            expose('__operitRegisterCallSession', registerCallSession);
            expose('__operitCleanupCallSession', cleanupCallSession);
            expose('__operitCancelCallSession', cancelCallSession);
            expose('__operitBuildRuntimeContext', buildRuntimeContext);

            windowRef.__operitGetActiveModuleExports = function() {
                if (
                    windowRef.__operitActiveModule &&
                    typeof windowRef.__operitActiveModule === 'object' &&
                    windowRef.__operitActiveModule.exports
                ) {
                    return windowRef.__operitActiveModule.exports;
                }
                var exportsRef = windowRef.__operitActiveModuleExports;
                return exportsRef && typeof exportsRef === 'object' ? exportsRef : exportsRef || null;
            };
        })();
    """.trimIndent()
}

private fun buildRuntimeErrorScript(): String {
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var windowRef = typeof window !== 'undefined' ? window : root;

            function asString(value) {
                return value == null ? '' : String(value);
            }

            function callNativeOptional(methodName) {
                if (
                    typeof NativeInterface === 'undefined' ||
                    !NativeInterface ||
                    typeof NativeInterface[methodName] !== 'function'
                ) {
                    return undefined;
                }
                var args = Array.prototype.slice.call(arguments, 1);
                try {
                    return NativeInterface[methodName].apply(NativeInterface, args);
                } catch (_error) {
                    return undefined;
                }
            }

            function formatErrorDetails(error) {
                var name = asString(error && error.name ? error.name : 'Error');
                var message = asString(error && error.message ? error.message : error);
                var stack = asString(error && error.stack ? error.stack : 'No stack trace');
                var lineNumber = 0;
                var fileName = '';
                var stackMatch = stack.match(/at\s+.*?\s+\((.+):(\d+):(\d+)\)/);
                if (stackMatch) {
                    fileName = asString(stackMatch[1]);
                    lineNumber = Number(stackMatch[2]) || 0;
                }
                return {
                    formatted: name + ': ' + message + '\nStack: ' + stack,
                    details: {
                        name: name,
                        message: message,
                        stack: stack,
                        fileName: fileName,
                        lineNumber: lineNumber
                    }
                };
            }

            function reportDetailedErrorForCall(callId, error, context) {
                var details = formatErrorDetails(error);
                var resolvedCallId = asString(callId).trim();
                if (resolvedCallId) {
                    callNativeOptional(
                        'reportErrorForCall',
                        resolvedCallId,
                        asString(details.details.name || 'Error'),
                        asString(details.details.message || ''),
                        Number(details.details.lineNumber) || 0,
                        asString(details.details.stack || '')
                    );
                } else {
                    callNativeOptional(
                        'reportError',
                        asString(details.details.name || 'Error'),
                        asString(details.details.message || ''),
                        Number(details.details.lineNumber) || 0,
                        asString(details.details.stack || '')
                    );
                }
                return {
                    formatted: 'Context: ' + asString(context || 'unknown') + '\n' + details.formatted,
                    details: details.details
                };
            }

            windowRef.__operitReportDetailedErrorForCall = reportDetailedErrorForCall;
            windowRef.reportDetailedError = function(error, context) {
                return reportDetailedErrorForCall('', error, context);
            };
        })();
    """.trimIndent()
}

private fun buildRuntimeToolCallScript(): String {
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var windowRef = typeof window !== 'undefined' ? window : root;
            var expose = typeof __operitExpose === 'function'
                ? __operitExpose
                : globalThis.__operitExpose;

            function asString(value) {
                return value == null ? '' : String(value);
            }

            function callNative(methodName) {
                if (
                    typeof NativeInterface === 'undefined' ||
                    !NativeInterface ||
                    typeof NativeInterface[methodName] !== 'function'
                ) {
                    throw new Error('NativeInterface.' + methodName + ' is unavailable');
                }
                var args = Array.prototype.slice.call(arguments, 1);
                return NativeInterface[methodName].apply(NativeInterface, args);
            }

            function clonePlainObject(value) {
                if (!value || typeof value !== 'object' || Array.isArray(value)) {
                    return {};
                }
                var copy = {};
                var keys = Object.keys(value);
                for (var i = 0; i < keys.length; i += 1) {
                    copy[keys[i]] = value[keys[i]];
                }
                return copy;
            }

            function normalizeToolCallOptions(value) {
                if (!value || typeof value !== 'object') {
                    return {};
                }
                return {
                    onIntermediateResult:
                        typeof value.onIntermediateResult === 'function'
                            ? value.onIntermediateResult
                            : null
                };
            }

            function parseToolCallArguments(rawArgs) {
                if (rawArgs.length === 1 && typeof rawArgs[0] === 'object') {
                    return {
                        type: asString(rawArgs[0].type || 'default'),
                        name: asString(rawArgs[0].name || ''),
                        params: clonePlainObject(rawArgs[0].params),
                        options: normalizeToolCallOptions(rawArgs[0])
                    };
                }
                if (rawArgs.length === 1 && typeof rawArgs[0] === 'string') {
                    return { type: 'default', name: asString(rawArgs[0]), params: {}, options: {} };
                }
                if (rawArgs.length === 2 && typeof rawArgs[1] === 'object') {
                    return {
                        type: 'default',
                        name: asString(rawArgs[0]),
                        params: clonePlainObject(rawArgs[1]),
                        options: {}
                    };
                }
                if (rawArgs.length === 3 && typeof rawArgs[1] === 'object' && typeof rawArgs[2] === 'object') {
                    return {
                        type: 'default',
                        name: asString(rawArgs[0]),
                        params: clonePlainObject(rawArgs[1]),
                        options: normalizeToolCallOptions(rawArgs[2])
                    };
                }
                return {
                    type: asString(rawArgs[0] || 'default'),
                    name: asString(rawArgs[1] || ''),
                    params: clonePlainObject(rawArgs[2]),
                    options: normalizeToolCallOptions(rawArgs[3])
                };
            }

            function stringifyToolResultDetail(detail) {
                if (detail == null) {
                    return '';
                }
                if (typeof detail === 'string') {
                    return detail.trim();
                }
                try {
                    return JSON.stringify(detail);
                } catch (_error) {
                    return asString(detail).trim();
                }
            }

            function createUserFacingError(message, detailData) {
                return {
                    name: 'Error',
                    message: asString(message).trim(),
                    data: detailData,
                    toString: function() {
                        return this.message;
                    }
                };
            }

            function buildToolError(result) {
                var message = asString(result && result.message).trim();
                var detailData =
                    result && typeof result === 'object' && Object.prototype.hasOwnProperty.call(result, 'data')
                        ? result.data
                        : undefined;
                return createUserFacingError(message, detailData);
            }

            function parseToolResult(result, isError) {
                if (isError) {
                    if (result && typeof result === 'object' && result.success === false) {
                        throw buildToolError(result);
                    }
                    throw createUserFacingError(
                        typeof result === 'string' ? result : JSON.stringify(result),
                        result && typeof result === 'object' ? result : undefined
                    );
                }
                if (result && typeof result === 'object' && Object.prototype.hasOwnProperty.call(result, 'success')) {
                    if (result.success) {
                        return result.data;
                    }
                    throw buildToolError(result);
                }
                if (typeof result === 'string' && result.length > 1) {
                    var first = result.charAt(0);
                    if (first === '{' || first === '[') {
                        try {
                            var parsed = JSON.parse(result);
                            return parseToolResult(parsed, false);
                        } catch (_error) {
                            return result;
                        }
                    }
                }
                return result;
            }

            function nextToolCallbackId() {
                return '__operit_tool_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10);
            }

            function toolCall() {
                var rawArgs = arguments;
                return new Promise(function(resolve, reject) {
                    try {
                        var parsed = parseToolCallArguments(rawArgs);
                        var apiRuntime = root.__operitToolPkgApi;
                        if (!apiRuntime || typeof apiRuntime.currentCallId !== 'function') {
                            throw new Error('ToolPkg API runtime is unavailable');
                        }
                        var callId = apiRuntime.currentCallId();
                        if (!callId) {
                            throw new Error('toolCall requires an active execution call');
                        }
                        var nativeParams = parsed.params || {};
                        var callbackId = nextToolCallbackId();
                        var intermediateCallbackId =
                            parsed.options && parsed.options.onIntermediateResult
                                ? nextToolCallbackId()
                                : '';
                        windowRef[callbackId] = function(result, isError) {
                            delete windowRef[callbackId];
                            if (intermediateCallbackId) {
                                delete windowRef[intermediateCallbackId];
                            }
                            try {
                                resolve(parseToolResult(result, !!isError));
                            } catch (error) {
                                reject(error);
                            }
                        };
                        if (intermediateCallbackId) {
                            windowRef[intermediateCallbackId] = function(result, isError) {
                                if (isError) {
                                    try {
                                        reject(parseToolResult(result, true));
                                    } catch (error) {
                                        reject(error);
                                    }
                                    return;
                                }
                                try {
                                    parsed.options.onIntermediateResult(parseToolResult(result, false));
                                } catch (error) {
                                    reject(error);
                                }
                            };
                            callNative(
                                'callToolAsyncStreamingForCall',
                                callbackId,
                                intermediateCallbackId,
                                callId,
                                parsed.type || 'default',
                                parsed.name,
                                JSON.stringify(nativeParams)
                            );
                        } else {
                            callNative(
                                'callToolAsyncForCall',
                                callbackId,
                                callId,
                                parsed.type || 'default',
                                parsed.name,
                                JSON.stringify(nativeParams)
                            );
                        }
                    } catch (error) {
                        reject(error);
                    }
                });
            }

            expose('toolCall', toolCall);
        })();
    """.trimIndent()
}

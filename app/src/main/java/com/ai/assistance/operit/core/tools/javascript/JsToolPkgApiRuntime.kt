package com.ai.assistance.operit.core.tools.javascript

internal fun buildToolPkgApiRuntimeScript(): String {
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var expose = root.__operitExpose;
            if (typeof expose !== 'function') {
                throw new Error('__operitExpose is unavailable');
            }

            function currentCallId() {
                return String(root.__operitCurrentCallId || '').trim();
            }

            function currentCallState() {
                var callId = currentCallId();
                return callId && typeof root.__operitGetCallState === 'function'
                    ? root.__operitGetCallState(callId)
                    : null;
            }

            function currentApiVersion() {
                var state = currentCallState();
                var context =
                    state && state.toolPkgApi && typeof state.toolPkgApi === 'object'
                        ? state.toolPkgApi
                        : null;
                var version = context && context.apiVersion;
                return typeof version === 'string' ? version.trim() : '';
            }

            function parseVersion(value) {
                var text = typeof value === 'string' ? value.trim() : '';
                var match = /^([0-9]+)\.([0-9]+)\.([0-9]+)$/.exec(text);
                if (!match) {
                    return null;
                }
                return {
                    text: text,
                    parts: [Number(match[1]), Number(match[2]), Number(match[3])]
                };
            }

            function requireVersion(value, label) {
                var parsed = parseVersion(value);
                if (!parsed) {
                    throw new Error(label + ' must use major.minor.patch format');
                }
                return parsed;
            }

            function compareVersions(left, right) {
                for (var index = 0; index < 3; index += 1) {
                    if (left.parts[index] > right.parts[index]) return 1;
                    if (left.parts[index] < right.parts[index]) return -1;
                }
                return 0;
            }

            function unsupported(apiName, requiredVersion, currentVersion) {
                var currentText = currentVersion || 'unknown';
                throw new Error(
                    apiName + ' requires ToolPkg API ' + requiredVersion +
                    ', but manifest.api_version is ' + currentText + '.'
                );
            }

            function requireCurrentVersion(apiName) {
                var currentText = currentApiVersion();
                var current = parseVersion(currentText);
                if (!current) {
                    throw new Error(
                        apiName + ' requires a ToolPkg execution context with a valid manifest.api_version.'
                    );
                }
                return current;
            }

            function cleanNamespace(value) {
                if (typeof value !== 'string' || value.trim() !== value || !value) {
                    throw new Error('ToolPkg API namespace must be a non-empty trimmed string.');
                }
                var segments = value.split('.');
                for (var index = 0; index < segments.length; index += 1) {
                    if (!segments[index]) {
                        throw new Error('ToolPkg API namespace must not contain empty path segments.');
                    }
                }
                return value;
            }

            function normalizeVariants(apiName, variants) {
                if (!Array.isArray(variants) || variants.length === 0) {
                    throw new Error(apiName + ' must declare at least one API version variant.');
                }
                var normalized = [];
                var seenSince = {};
                for (var index = 0; index < variants.length; index += 1) {
                    var variant = variants[index];
                    if (!variant || typeof variant !== 'object' || Array.isArray(variant)) {
                        throw new Error(apiName + ' variant must be an object.');
                    }
                    if (typeof variant.invoke !== 'function') {
                        throw new Error(apiName + ' variant must declare an implementation function.');
                    }
                    var since = requireVersion(
                        variant.since,
                        apiName + ' variant since'
                    );
                    if (seenSince[since.text]) {
                        throw new Error(apiName + ' declares duplicate variant since ' + since.text + '.');
                    }
                    seenSince[since.text] = true;
                    normalized.push({
                        since: since,
                        invoke: variant.invoke
                    });
                }
                normalized.sort(function(left, right) {
                    return compareVersions(left.since, right.since);
                });
                return normalized;
            }

            function selectVariant(apiName, introduced, normalizedVariants) {
                var current = requireCurrentVersion(apiName);
                if (compareVersions(current, introduced) < 0) {
                    unsupported(apiName, introduced.text, current.text);
                }
                var selected = null;
                for (var index = 0; index < normalizedVariants.length; index += 1) {
                    var variant = normalizedVariants[index];
                    if (compareVersions(current, variant.since) >= 0) {
                        selected = variant;
                    }
                }
                if (!selected) {
                    throw new Error(
                        apiName + ' has no implementation for ToolPkg API ' + current.text + '.'
                    );
                }
                return selected.invoke;
            }

            function versionedMethod(apiName, variants) {
                var normalizedName = cleanNamespace(apiName);
                var normalizedVariants = normalizeVariants(normalizedName, variants);
                var introduced = normalizedVariants[0].since;
                return function() {
                    var invoke = selectVariant(normalizedName, introduced, normalizedVariants);
                    return invoke.apply(this, arguments);
                };
            }

            function method() {
                var variants = [];
                var builder = {
                    __operitToolPkgApiMethod: true,
                    since: function(apiVersion, invoke) {
                        variants.push({
                            since: apiVersion,
                            invoke: invoke
                        });
                        return builder;
                    },
                    build: function(apiName) {
                        return versionedMethod(apiName, variants);
                    }
                };
                return builder;
            }

            function namespace(publicName, members) {
                var normalizedNamespace = cleanNamespace(publicName);
                if (!members || typeof members !== 'object' || Array.isArray(members)) {
                    throw new Error(normalizedNamespace + ' members must be an object.');
                }
                var output = {};
                Object.keys(members).forEach(function(memberName) {
                    var member = members[memberName];
                    output[memberName] =
                        member &&
                        member.__operitToolPkgApiMethod === true &&
                        typeof member.build === 'function'
                            ? member.build(normalizedNamespace + '.' + memberName)
                            : member;
                });
                return output;
            }

            expose('__operitToolPkgApi', {
                currentCallId: currentCallId,
                currentVersion: currentApiVersion,
                namespace: namespace,
                method: method
            });
        })();
    """.trimIndent()
}

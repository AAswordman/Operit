package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.packTool.ToolPkgMarketOrigin
import com.ai.assistance.operit.core.tools.packTool.ToolPkgMarketOriginCodec
import org.json.JSONObject
import org.json.JSONTokener

internal data class ToolPkgMainRegistrationCapture(
    val toolboxUiModules: List<String>,
    val uiRoutes: List<String>,
    val navigationEntries: List<String>,
    val desktopWidgets: List<String>,
    val appLifecycleHooks: List<String>,
    val messageProcessingPlugins: List<String>,
    val xmlRenderPlugins: List<String>,
    val inputMenuTogglePlugins: List<String>,
    val chatInputHooks: List<String>,
    val chatViewHooks: List<String>,
    val chatMessageHooks: List<String>,
    val toolLifecycleHooks: List<String>,
    val promptInputHooks: List<String>,
    val promptHistoryHooks: List<String>,
    val promptEstimateHistoryHooks: List<String>,
    val systemPromptComposeHooks: List<String>,
    val toolPromptComposeHooks: List<String>,
    val promptFinalizeHooks: List<String>,
    val promptEstimateFinalizeHooks: List<String>,
    val summaryGenerateHooks: List<String>,
    val aiProviders: List<String>,
    val marketOrigin: ToolPkgMarketOrigin?
)

private enum class RegistrationBucket {
    TOOLBOX_UI,
    UI_ROUTE,
    NAVIGATION_ENTRY,
    DESKTOP_WIDGET,
    APP_LIFECYCLE,
    MESSAGE_PROCESSING,
    XML_RENDER,
    INPUT_MENU_TOGGLE,
    CHAT_INPUT,
    CHAT_VIEW,
    CHAT_MESSAGE,
    TOOL_LIFECYCLE,
    PROMPT_INPUT,
    PROMPT_HISTORY,
    PROMPT_ESTIMATE_HISTORY,
    SYSTEM_PROMPT_COMPOSE,
    TOOL_PROMPT_COMPOSE,
    PROMPT_FINALIZE,
    PROMPT_ESTIMATE_FINALIZE,
    SUMMARY_GENERATE,
    AI_PROVIDER
}

internal class JsToolPkgRegistrationSession {
    private val lock = Any()
    private var capture: MutableMap<RegistrationBucket, MutableList<String>>? = null
    private var marketOrigin: ToolPkgMarketOrigin? = null

    fun begin() {
        synchronized(lock) {
            capture = mutableMapOf()
            marketOrigin = null
        }
    }

    fun isActive(): Boolean {
        synchronized(lock) {
            return capture != null
        }
    }

    fun appendToolboxUiModule(specJson: String) = append(RegistrationBucket.TOOLBOX_UI, specJson)
    fun appendUiRoute(specJson: String) = append(RegistrationBucket.UI_ROUTE, specJson)
    fun appendNavigationEntry(specJson: String) = append(RegistrationBucket.NAVIGATION_ENTRY, specJson)
    fun appendDesktopWidget(specJson: String) = append(RegistrationBucket.DESKTOP_WIDGET, specJson)
    fun appendAppLifecycleHook(specJson: String) = append(RegistrationBucket.APP_LIFECYCLE, specJson)
    fun appendMessageProcessingPlugin(specJson: String) =
        append(RegistrationBucket.MESSAGE_PROCESSING, specJson)

    fun appendXmlRenderPlugin(specJson: String) = append(RegistrationBucket.XML_RENDER, specJson)
    fun appendInputMenuTogglePlugin(specJson: String) =
        append(RegistrationBucket.INPUT_MENU_TOGGLE, specJson)

    fun appendChatInputHook(specJson: String) =
        append(RegistrationBucket.CHAT_INPUT, specJson)

    fun appendChatViewHook(specJson: String) =
        append(RegistrationBucket.CHAT_VIEW, specJson)

    fun appendChatMessageHook(specJson: String) =
        append(RegistrationBucket.CHAT_MESSAGE, specJson)

    fun appendToolLifecycleHook(specJson: String) =
        append(RegistrationBucket.TOOL_LIFECYCLE, specJson)

    fun appendPromptInputHook(specJson: String) = append(RegistrationBucket.PROMPT_INPUT, specJson)
    fun appendPromptHistoryHook(specJson: String) =
        append(RegistrationBucket.PROMPT_HISTORY, specJson)
    fun appendPromptEstimateHistoryHook(specJson: String) =
        append(RegistrationBucket.PROMPT_ESTIMATE_HISTORY, specJson)

    fun appendSystemPromptComposeHook(specJson: String) =
        append(RegistrationBucket.SYSTEM_PROMPT_COMPOSE, specJson)

    fun appendToolPromptComposeHook(specJson: String) =
        append(RegistrationBucket.TOOL_PROMPT_COMPOSE, specJson)

    fun appendPromptFinalizeHook(specJson: String) =
        append(RegistrationBucket.PROMPT_FINALIZE, specJson)
    fun appendPromptEstimateFinalizeHook(specJson: String) =
        append(RegistrationBucket.PROMPT_ESTIMATE_FINALIZE, specJson)
    fun appendSummaryGenerateHook(specJson: String) =
        append(RegistrationBucket.SUMMARY_GENERATE, specJson)
    fun appendAiProvider(specJson: String) =
        append(RegistrationBucket.AI_PROVIDER, specJson)

    fun captureMarketOrigin(specJson: String) {
        synchronized(lock) {
            // Marketplace publishing appends this call to the main module. The module can be
            // evaluated again by runtime hooks after registration has closed, where the marker
            // must remain harmless instead of aborting the hook execution.
            if (capture == null) return
            // Provenance is optional for legacy packages; malformed markers never become trusted metadata.
            marketOrigin = ToolPkgMarketOriginCodec.parse(specJson)
        }
    }

    fun finish(executionResult: Any?): ToolPkgMainRegistrationCapture {
        val errorMessage = extractJsExecutionErrorMessage(executionResult)
        if (errorMessage != null) {
            throw IllegalStateException(errorMessage)
        }
        synchronized(lock) {
            val current = capture.orEmpty()
            fun read(bucket: RegistrationBucket): List<String> = current[bucket]?.toList().orEmpty()
            return ToolPkgMainRegistrationCapture(
                toolboxUiModules = read(RegistrationBucket.TOOLBOX_UI),
                uiRoutes = read(RegistrationBucket.UI_ROUTE),
                navigationEntries = read(RegistrationBucket.NAVIGATION_ENTRY),
                desktopWidgets = read(RegistrationBucket.DESKTOP_WIDGET),
                appLifecycleHooks = read(RegistrationBucket.APP_LIFECYCLE),
                messageProcessingPlugins = read(RegistrationBucket.MESSAGE_PROCESSING),
                xmlRenderPlugins = read(RegistrationBucket.XML_RENDER),
                inputMenuTogglePlugins = read(RegistrationBucket.INPUT_MENU_TOGGLE),
                chatInputHooks = read(RegistrationBucket.CHAT_INPUT),
                chatViewHooks = read(RegistrationBucket.CHAT_VIEW),
                chatMessageHooks = read(RegistrationBucket.CHAT_MESSAGE),
                toolLifecycleHooks = read(RegistrationBucket.TOOL_LIFECYCLE),
                promptInputHooks = read(RegistrationBucket.PROMPT_INPUT),
                promptHistoryHooks = read(RegistrationBucket.PROMPT_HISTORY),
                promptEstimateHistoryHooks = read(RegistrationBucket.PROMPT_ESTIMATE_HISTORY),
                systemPromptComposeHooks = read(RegistrationBucket.SYSTEM_PROMPT_COMPOSE),
                toolPromptComposeHooks = read(RegistrationBucket.TOOL_PROMPT_COMPOSE),
                promptFinalizeHooks = read(RegistrationBucket.PROMPT_FINALIZE),
                promptEstimateFinalizeHooks = read(RegistrationBucket.PROMPT_ESTIMATE_FINALIZE),
                summaryGenerateHooks = read(RegistrationBucket.SUMMARY_GENERATE),
                aiProviders = read(RegistrationBucket.AI_PROVIDER),
                marketOrigin = marketOrigin
            )
        }
    }

    fun end() {
        synchronized(lock) {
            capture = null
            marketOrigin = null
        }
    }

    private fun append(bucket: RegistrationBucket, specJson: String) {
        val normalized = normalizeRegistrationSpec(specJson)
        synchronized(lock) {
            val target = capture ?: error("toolpkg registration session is not active")
            target.getOrPut(bucket) { mutableListOf() }.add(normalized)
        }
    }

    private fun normalizeRegistrationSpec(specJson: String): String {
        val trimmed = specJson.trim()
        require(trimmed.isNotEmpty()) { "toolpkg registration payload is empty" }
        val parsed = JSONTokener(trimmed).nextValue()
        require(parsed is JSONObject) { "toolpkg registration payload must be a JSON object" }
        return parsed.toString()
    }
}

internal fun buildToolPkgRegistrationBridgeScript(): String {
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var moduleRefFunctionCounter = 0;

            function installGlobal(name, value) {
                var key = String(name || '').trim();
                if (!key || value === undefined) {
                    return;
                }
                try { globalThis[key] = value; } catch (_e) {}
                try { window[key] = value; } catch (_e2) {}
            }

            function requireNative(name) {
                if (
                    typeof NativeInterface === 'undefined' ||
                    !NativeInterface ||
                    typeof NativeInterface[name] !== 'function'
                ) {
                    throw new Error('NativeInterface.' + name + ' is unavailable');
                }
                return NativeInterface[name].bind(NativeInterface);
            }

            function captureMarketOrigin(encoded, key) {
                if (!Array.isArray(encoded)) {
                    throw new Error('ToolPkg marketplace origin payload must be an array');
                }
                var xorKey = Number(key);
                if (!Number.isInteger(xorKey) || xorKey < 0 || xorKey > 255) {
                    throw new Error('ToolPkg marketplace origin key is invalid');
                }
                var json = '';
                for (var index = 0; index < encoded.length; index += 1) {
                    var value = Number(encoded[index]);
                    if (!Number.isInteger(value) || value < 0 || value > 255) {
                        throw new Error('ToolPkg marketplace origin payload byte is invalid');
                    }
                    json += String.fromCharCode(value ^ xorKey);
                }
                requireNative('captureToolPkgMarketOrigin')(json);
            }

            function copyObject(source, excludedKey) {
                var output = {};
                var keys = Object.keys(source || {});
                for (var i = 0; i < keys.length; i += 1) {
                    var key = keys[i];
                    if (key !== excludedKey) {
                        output[key] = source[key];
                    }
                }
                return output;
            }

            function getActiveExports() {
                return typeof root.__operitGetActiveModuleExports === 'function'
                    ? root.__operitGetActiveModuleExports()
                    : null;
            }

            function resolveExportedFunctionName(fn) {
                var exportsRef = getActiveExports();
                if (!exportsRef || typeof exportsRef !== 'object') {
                    return '';
                }
                var keys = Object.keys(exportsRef);
                for (var i = 0; i < keys.length; i += 1) {
                    if (exportsRef[keys[i]] === fn) {
                        return keys[i];
                    }
                }
                return '';
            }

            function buildGeneratedFunctionName(definition) {
                moduleRefFunctionCounter += 1;
                var rawId = String((definition && definition.id) || 'hook');
                var safeId = rawId.replace(/[^a-zA-Z0-9_$]/g, '_') || 'hook';
                return '__operit_module_ref_hook_' + safeId + '_' + moduleRefFunctionCounter;
            }

            function activeModulePath() {
                var exportsRef = getActiveExports();
                if (!exportsRef || typeof exportsRef !== 'object') {
                    return '';
                }
                return typeof exportsRef.__operit_toolpkg_module_path === 'string'
                    ? exportsRef.__operit_toolpkg_module_path.trim().replace(/\\/g, '/')
                    : '';
            }

            function dirname(path) {
                var normalized = String(path || '').replace(/\\/g, '/');
                var slash = normalized.lastIndexOf('/');
                return slash >= 0 ? normalized.slice(0, slash) : '';
            }

            function relativeRequirePath(fromModulePath, targetModulePath) {
                var fromDir = dirname(fromModulePath);
                var target = String(targetModulePath || '').replace(/\\/g, '/');
                if (!fromDir) {
                    return './' + target;
                }
                var fromParts = fromDir.split('/').filter(Boolean);
                var targetParts = target.split('/').filter(Boolean);
                while (fromParts.length > 0 && targetParts.length > 0 && fromParts[0] === targetParts[0]) {
                    fromParts.shift();
                    targetParts.shift();
                }
                var up = fromParts.map(function() { return '..'; });
                var parts = up.concat(targetParts);
                var rel = parts.join('/');
                return rel.startsWith('.') ? rel : './' + rel;
            }

            function buildModuleRefFunctionSource(requirePath, exportName) {
                return 'function() {' +
                    'var moduleRef = require(' + JSON.stringify(requirePath) + ');' +
                    'var fn = moduleRef && moduleRef[' + JSON.stringify(exportName) + '];' +
                    'if (typeof fn !== "function") {' +
                        'throw new Error("ToolPkg registered function export not found: ' + exportName.replace(/"/g, '\\"') + '");' +
                    '}' +
                    'return fn.apply(null, arguments);' +
                '}';
            }

            function resolveDurableFunctionRef(fn, definition, label) {
                var exportedName = resolveExportedFunctionName(fn);
                if (exportedName) {
                    return {
                        name: exportedName,
                        source: ''
                    };
                }
                var modulePath = typeof fn.__operit_toolpkg_module_path === 'string'
                    ? fn.__operit_toolpkg_module_path.trim().replace(/\\/g, '/')
                    : '';
                var exportName = typeof fn.__operit_toolpkg_export_name === 'string'
                    ? fn.__operit_toolpkg_export_name.trim()
                    : '';
                if (!modulePath || !exportName) {
                    throw new Error(label + ' function must be exported from a toolpkg module');
                }
                var fromModulePath = activeModulePath();
                var functionName = buildGeneratedFunctionName(definition);
                return {
                    name: functionName,
                    source: buildModuleRefFunctionSource(relativeRequirePath(fromModulePath, modulePath), exportName)
                };
            }

            function normalizeFunctionField(definition, fieldName, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var normalized = copyObject(definition, fieldName);
                var fn = definition[fieldName];
                if (typeof fn !== 'function') {
                    throw new Error(label + ' requires a function reference');
                }
                var functionRef = resolveDurableFunctionRef(fn, definition, label);
                normalized[fieldName] = functionRef.name;
                if (functionRef.source) {
                    normalized.function_source = functionRef.source;
                }
                return normalized;
            }

            function normalizeNestedFunctionField(definition, fieldName, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var fieldValue = definition[fieldName];
                if (!fieldValue || typeof fieldValue !== 'object' || Array.isArray(fieldValue)) {
                    throw new Error(label + ' requires an object field: ' + fieldName);
                }
                var fn = fieldValue.function;
                if (typeof fn !== 'function') {
                    throw new Error(label + '.' + fieldName + '.function must be a function reference');
                }
                var functionRef = resolveDurableFunctionRef(fn, {
                    id: String((definition && definition.id) || 'provider') + '_' + fieldName
                }, label + '.' + fieldName);
                var normalizedField = copyObject(fieldValue, 'function');
                normalizedField.function = functionRef.name;
                if (functionRef.source) {
                    normalizedField.function_source = functionRef.source;
                }
                return normalizedField;
            }

            function normalizeAiProviderDefinition(definition, label) {
                var normalized = copyObject(definition, '');
                [
                    'listModels',
                    'sendMessage',
                    'testConnection',
                    'calculateInputTokens'
                ].forEach(function(fieldName) {
                    normalized[fieldName] = normalizeNestedFunctionField(definition, fieldName, label);
                });
                return normalized;
            }

            function normalizeScreenField(definition, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var normalized = copyObject(definition, 'screen');
                var screen = definition.screen;
                var path = '';
                if (typeof screen === 'string') {
                    path = screen.trim().replace(/\\/g, '/');
                } else if (typeof screen === 'function' && typeof screen.__operit_toolpkg_module_path === 'string') {
                    path = screen.__operit_toolpkg_module_path.trim().replace(/\\/g, '/');
                } else if (
                    screen &&
                    typeof screen === 'object' &&
                    typeof screen.default === 'function' &&
                    typeof screen.default.__operit_toolpkg_module_path === 'string'
                ) {
                    path = screen.default.__operit_toolpkg_module_path.trim().replace(/\\/g, '/');
                }
                if (!path) {
                    throw new Error(label + ' requires a serializable screen reference');
                }
                normalized.screen = path;
                return normalized;
            }

            function registerWithNative(definition, label, nativeMethod, fieldName) {
                var normalized = fieldName
                    ? normalizeFunctionField(definition, fieldName, label)
                    : normalizeScreenField(definition, label);
                requireNative(nativeMethod)(JSON.stringify(normalized));
            }

            function normalizeNavigationEntryDefinition(definition, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                if (typeof definition.action === 'function') {
                    return normalizeFunctionField(definition, 'action', label);
                }
                return copyObject(definition, '');
            }

            function resolveCurrentToolPkgTarget() {
                var callId = String(root.__operitCurrentCallId || '').trim();
                var callState =
                    callId && typeof root.__operitGetCallState === 'function'
                        ? root.__operitGetCallState(callId)
                        : null;
                var params =
                    callState && callState.params && typeof callState.params === 'object'
                        ? callState.params
                        : null;
                if (!params) {
                    return '';
                }
                var candidates = [
                    params.__operit_ui_package_name,
                    params.toolPkgId,
                    params.containerPackageName,
                    params.__operit_toolpkg_subpackage_id,
                    params.__operit_package_name
                ];
                for (var i = 0; i < candidates.length; i += 1) {
                    var value = String(candidates[i] || '').trim();
                    if (value) {
                        return value;
                    }
                }
                return '';
            }

            function readToolPkgResource(key, outputFileName, internal) {
                var resourceKey = String(key || '').trim();
                if (!resourceKey) {
                    return Promise.reject(new Error('resource key is required'));
                }
                var target = resolveCurrentToolPkgTarget();
                if (!target) {
                    return Promise.reject(new Error('package/toolpkg runtime target is empty'));
                }
                var path = requireNative('readToolPkgResource')(
                    target,
                    resourceKey,
                    outputFileName == null ? '' : String(outputFileName).trim(),
                    internal === true ? 'true' : ''
                );
                if (typeof path === 'string' && path.trim()) {
                    return Promise.resolve(path);
                }
                return Promise.reject(new Error('resource not found: ' + resourceKey));
            }

            function getToolPkgConfigDir(pluginId) {
                var explicitId = String(pluginId || '').trim();
                var target = explicitId || resolveCurrentToolPkgTarget();
                if (!target) {
                    throw new Error('package/toolpkg runtime target is empty');
                }
                var path = requireNative('getPluginConfigDir')(target);
                if (typeof path === 'string' && path.trim()) {
                    return path;
                }
                throw new Error('plugin config dir is unavailable for ' + target);
            }

            var api = {
                _m: captureMarketOrigin,
                registerToolboxUiModule: function(definition) {
                    registerWithNative(
                        definition,
                        'registerToolPkgToolboxUiModule',
                        'registerToolPkgToolboxUiModule',
                        ''
                    );
                },
                registerUiRoute: function(definition) {
                    registerWithNative(
                        definition,
                        'registerToolPkgUiRoute',
                        'registerToolPkgUiRoute',
                        ''
                    );
                },
                registerNavigationEntry: function(definition) {
                    var normalized = normalizeNavigationEntryDefinition(
                        definition,
                        'registerToolPkgNavigationEntry'
                    );
                    requireNative('registerToolPkgNavigationEntry')(
                        JSON.stringify(normalized)
                    );
                },
                registerDesktopWidget: function(definition) {
                    requireNative('registerToolPkgDesktopWidget')(
                        JSON.stringify(copyObject(definition, ''))
                    );
                },
                readResource: readToolPkgResource,
                getConfigDir: getToolPkgConfigDir
            };

            [
                ['registerAppLifecycleHook', 'registerToolPkgAppLifecycleHook'],
                ['registerMessageProcessingPlugin', 'registerToolPkgMessageProcessingPlugin'],
                ['registerXmlRenderPlugin', 'registerToolPkgXmlRenderPlugin'],
                ['registerInputMenuTogglePlugin', 'registerToolPkgInputMenuTogglePlugin'],
                ['registerChatInputHook', 'registerToolPkgChatInputHook'],
                ['registerChatViewHook', 'registerToolPkgChatViewHook'],
                ['registerChatMessageHook', 'registerToolPkgChatMessageHook'],
                ['registerToolLifecycleHook', 'registerToolPkgToolLifecycleHook'],
                ['registerPromptInputHook', 'registerToolPkgPromptInputHook'],
                ['registerPromptHistoryHook', 'registerToolPkgPromptHistoryHook'],
                ['registerPromptEstimateHistoryHook', 'registerToolPkgPromptEstimateHistoryHook'],
                ['registerSystemPromptComposeHook', 'registerToolPkgSystemPromptComposeHook'],
                ['registerToolPromptComposeHook', 'registerToolPkgToolPromptComposeHook'],
                ['registerPromptFinalizeHook', 'registerToolPkgPromptFinalizeHook'],
                ['registerPromptEstimateFinalizeHook', 'registerToolPkgPromptEstimateFinalizeHook'],
                ['registerSummaryGenerateHook', 'registerToolPkgSummaryGenerateHook']
            ].forEach(function(entry) {
                var apiName = entry[0];
                var nativeMethod = entry[1];
                api[apiName] = function(definition) {
                    registerWithNative(definition, apiName, nativeMethod, 'function');
                };
            });

            api.registerAiProvider = function(definition) {
                var normalized = normalizeAiProviderDefinition(definition, 'registerAiProvider');
                requireNative('registerToolPkgAiProvider')(JSON.stringify(normalized));
            };

            installGlobal('ToolPkg', api);
        })();
    """.trimIndent()
}

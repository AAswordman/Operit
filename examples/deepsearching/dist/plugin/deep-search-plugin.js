"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onApplicationCreate = onApplicationCreate;
exports.onMessageProcessing = onMessageProcessing;
exports.onXmlRender = onXmlRender;
exports.onInputMenuToggle = onInputMenuToggle;
const plan_mode_manager_1 = require("../planning/plan-mode-manager");
const plan_xml_render_plugin_1 = require("./plan-xml-render-plugin");
const i18n_1 = require("../i18n");
const prompt_turns_1 = require("../prompt-turns");
const ApiPreferences = Java.com.ai.assistance.operit.data.preferences.ApiPreferences;
const EnhancedAIService = Java.com.ai.assistance.operit.api.chat.EnhancedAIService;
const MessageProcessingController = Java.com.ai.assistance.operit.core.chat.plugins.MessageProcessingController;
const ToolPkgMessageProcessingCancellationRegistry = Java.com.ai.assistance.operit.plugins.toolpkg.ToolPkgMessageProcessingCancellationRegistry;
const FEATURE_KEY = "ai_planning";
const PROBE_LOG_TAG = "[deepsearching_probe]";
function logProbe(message) {
    console.log(`${PROBE_LOG_TAG} ${message}`);
}
function getAppContext() {
    if (typeof Java.getApplicationContext !== "function") {
        return null;
    }
    return Java.getApplicationContext();
}
function isDeepSearchEnabled(context) {
    return Boolean(ApiPreferences.getFeatureToggleBlocking(context, FEATURE_KEY, false));
}
function setDeepSearchEnabled(context, enabled) {
    ApiPreferences.setFeatureToggleBlocking(context, FEATURE_KEY, !!enabled);
}
function disableDeepSearchAfterSend(context, reason) {
    try {
        setDeepSearchEnabled(context, false);
        logProbe(`autoDisableDeepSearch reason=${reason}`);
    }
    catch (error) {
        console.log("deepsearching auto disable error", String(error));
        logProbe(`autoDisableDeepSearch error reason=${reason} error=${String(error)}`);
    }
}
function normalizePayload(input) {
    const record = input;
    if (record && record.eventPayload && typeof record.eventPayload === "object") {
        return record.eventPayload;
    }
    return record || {};
}
function getI18n() {
    const locale = getLang();
    return (0, i18n_1.resolveDeepSearchI18n)(locale);
}
function registerToolPkg() {
    console.log("deepsearching registerToolPkg start");
    console.log("deepsearching skip: registerUiModule");
    ToolPkg.registerAppLifecycleHook({
        id: "deepsearching_app_create",
        event: "application_on_create",
        function: onApplicationCreate,
    });
    console.log("deepsearching registered: registerAppLifecycleHook");
    ToolPkg.registerMessageProcessingPlugin({
        id: "deepsearching_message_plugin",
        function: onMessageProcessing,
    });
    console.log("deepsearching registered: registerMessageProcessingPlugin");
    ToolPkg.registerXmlRenderPlugin({
        id: "deepsearching_xml_plan",
        tag: "plan",
        function: onXmlRender,
    });
    console.log("deepsearching registered: registerXmlRenderPlugin");
    ToolPkg.registerInputMenuTogglePlugin({
        id: "deepsearching_input_menu_toggle",
        function: onInputMenuToggle,
    });
    console.log("deepsearching registered: registerInputMenuTogglePlugin");
    console.log("deepsearching registerToolPkg done");
    return true;
}
function onApplicationCreate(input) {
    console.log("deepsearching onApplicationCreate", JSON.stringify(input ?? null));
}
async function onMessageProcessing(input) {
    const totalStartTime = Date.now();
    const payload = normalizePayload(input);
    const probeOnly = Boolean(payload.probeOnly ?? false);
    const executionId = String(payload.executionId ?? "").trim();
    const message = String(payload.messageContent ?? "").trim();
    logProbe(`onMessageProcessing start probeOnly=${probeOnly} executionId=${executionId || "none"} messageLength=${message.length}`);
    if (!message) {
        logProbe(`onMessageProcessing return matched=false reason=empty_message elapsedMs=${Date.now() - totalStartTime}`);
        return { matched: false };
    }
    let context = null;
    let enhancedAIService = null;
    let manager = null;
    let cancellationHandle = null;
    try {
        const getContextStartTime = Date.now();
        context = getAppContext();
        logProbe(`getAppContext elapsedMs=${Date.now() - getContextStartTime} hasContext=${Boolean(context)}`);
        if (!context) {
            logProbe(`onMessageProcessing return matched=false reason=no_context elapsedMs=${Date.now() - totalStartTime}`);
            return { matched: false };
        }
        const featureToggleStartTime = Date.now();
        const enabled = isDeepSearchEnabled(context);
        logProbe(`isDeepSearchEnabled elapsedMs=${Date.now() - featureToggleStartTime} enabled=${enabled}`);
        if (!enabled) {
            logProbe(`onMessageProcessing return matched=false reason=feature_disabled elapsedMs=${Date.now() - totalStartTime}`);
            return { matched: false };
        }
        const getServiceStartTime = Date.now();
        const chatId = String(payload.chatId ?? "").trim();
        enhancedAIService = chatId
            ? EnhancedAIService.getChatInstance(context, chatId)
            : EnhancedAIService.getInstance(context);
        logProbe(`${chatId ? "EnhancedAIService.getChatInstance" : "EnhancedAIService.getInstance"} elapsedMs=${Date.now() - getServiceStartTime} chatId=${chatId || "none"}`);
        const createManagerStartTime = Date.now();
        manager = new plan_mode_manager_1.PlanModeManager(context, enhancedAIService);
        logProbe(`PlanModeManager ctor elapsedMs=${Date.now() - createManagerStartTime}`);
        const shouldUseStartTime = Date.now();
        const shouldUse = manager.shouldUseDeepSearchMode(message);
        logProbe(`shouldUseDeepSearchMode elapsedMs=${Date.now() - shouldUseStartTime} shouldUse=${shouldUse}`);
        if (probeOnly) {
            logProbe(`onMessageProcessing return probe matched=${shouldUse} elapsedMs=${Date.now() - totalStartTime}`);
            return { matched: shouldUse };
        }
        disableDeepSearchAfterSend(context, "execute_start");
        if (!executionId) {
            throw new Error("deepsearching missing executionId");
        }
        cancellationHandle = Java.proxy(MessageProcessingController, {
            cancel() {
                manager?.cancel();
            },
        });
        ToolPkgMessageProcessingCancellationRegistry.register(executionId, cancellationHandle);
        const history = (0, prompt_turns_1.normalizePromptTurnList)(payload.chatHistory);
        const workspacePath = payload.workspacePath ?? null;
        const maxTokens = Number(payload.maxTokens ?? 0);
        const tokenUsageThreshold = Number(payload.tokenUsageThreshold ?? 0);
        logProbe(`executionContext historySize=${history.length} workspaceBound=${Boolean(workspacePath)} maxTokens=${maxTokens} tokenUsageThreshold=${tokenUsageThreshold}`);
        if (!maxTokens || !tokenUsageThreshold) {
            console.log("deepsearching missing maxTokens/tokenUsageThreshold");
            logProbe(`onMessageProcessing return matched=false reason=missing_limits elapsedMs=${Date.now() - totalStartTime}`);
            return { matched: false };
        }
        const emitIntermediateChunk = (chunk) => {
            if (!chunk)
                return;
            if (typeof sendIntermediateResult === "function") {
                sendIntermediateResult({ chunk });
            }
        };
        const executeDeepSearchModeStartTime = Date.now();
        const text = await manager.executeDeepSearchMode(message, history, workspacePath, maxTokens, tokenUsageThreshold, emitIntermediateChunk);
        logProbe(`executeDeepSearchMode elapsedMs=${Date.now() - executeDeepSearchModeStartTime} hasText=${Boolean(text)}`);
        if (!text) {
            logProbe(`onMessageProcessing return matched=false reason=empty_result elapsedMs=${Date.now() - totalStartTime}`);
            return { matched: false };
        }
        logProbe(`onMessageProcessing return matched=true elapsedMs=${Date.now() - totalStartTime} textLength=${text.length}`);
        return { matched: true, text };
    }
    catch (error) {
        console.log("deepsearching onMessageProcessing error", String(error));
        logProbe(`onMessageProcessing error elapsedMs=${Date.now() - totalStartTime} error=${String(error)}`);
        return { matched: false };
    }
    finally {
        try {
            if (executionId) {
                ToolPkgMessageProcessingCancellationRegistry.unregister(executionId);
            }
        }
        catch (_e) { }
        logProbe(`onMessageProcessing finally elapsedMs=${Date.now() - totalStartTime}`);
    }
}
function onXmlRender(event) {
    const payload = normalizePayload(event);
    const xmlContent = String(payload.xmlContent ?? "");
    const tagName = String(payload.tagName ?? "");
    console.log("deepsearching onXmlRender input", JSON.stringify({
        tagName,
        xmlLength: xmlContent.length,
        preview: xmlContent.slice(0, 120)
    }));
    if (!xmlContent) {
        console.log("deepsearching onXmlRender skip: empty xmlContent");
        return { handled: false };
    }
    const result = (0, plan_xml_render_plugin_1.renderPlanXml)(xmlContent, tagName);
    console.log("deepsearching onXmlRender result", JSON.stringify({
        tagName,
        handled: Boolean(result?.handled),
        hasComposeDsl: Boolean(result?.composeDsl),
        composeDslStateKeys: result?.composeDsl?.state ? Object.keys(result.composeDsl.state) : []
    }));
    return result;
}
function onInputMenuToggle(input) {
    const payload = normalizePayload(input);
    const action = String(payload.action ?? "").toLowerCase();
    let context = null;
    try {
        context = getAppContext();
        if (!context)
            return [];
        if (action === "toggle") {
            const current = isDeepSearchEnabled(context);
            setDeepSearchEnabled(context, !current);
            return [];
        }
        if (action !== "create") {
            return [];
        }
        const enabled = isDeepSearchEnabled(context);
        const i18n = getI18n();
        return [
            {
                id: FEATURE_KEY,
                title: i18n.menuTitle,
                description: i18n.menuDescription,
                isChecked: enabled,
            },
        ];
    }
    catch (error) {
        console.log("deepsearching onInputMenuToggle error", String(error));
        return [];
    }
}

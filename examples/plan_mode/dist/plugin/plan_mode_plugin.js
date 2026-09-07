"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onInputMenuToggle = onInputMenuToggle;
exports.onChatViewEvent = onChatViewEvent;
exports.onSystemPromptCompose = onSystemPromptCompose;
exports.onToolPromptCompose = onToolPromptCompose;
exports.onPromptFinalize = onPromptFinalize;
exports.onPromptEstimateFinalize = onPromptEstimateFinalize;
exports.registerToolPkg = registerToolPkg;
const plan_mode_constants_js_1 = require("../shared/plan_mode_constants.js");
const plan_mode_i18n_js_1 = require("../shared/plan_mode_i18n.js");
const plan_mode_runtime_ipc_js_1 = require("../shared/plan_mode_runtime_ipc.js");
const plan_mode_prompt_js_1 = require("../shared/plan_mode_prompt.js");
const plan_mode_execution_js_1 = require("../shared/plan_mode_execution.js");
const plan_mode_ask_execution_js_1 = require("../shared/plan_mode_ask_execution.js");
const plan_mode_workspace_js_1 = require("../shared/plan_mode_workspace.js");
const planask_xml_render_plugin_js_1 = require("./planask-xml-render-plugin.js");
const plantodo_xml_render_plugin_js_1 = require("./plantodo-xml-render-plugin.js");
const PLAN_MODE_BLOCKED_TOOL_NAMES = new Set([
    "apply_file",
    "create_file",
    "edit_file",
    "delete_file",
]);
let planModeIpcRegistered = false;
function usesChatPrompt(payload) {
    const promptFunctionType = payload.promptFunctionType;
    if (promptFunctionType !== undefined && promptFunctionType !== "") {
        return promptFunctionType === "CHAT";
    }
    const functionType = payload.functionType;
    return functionType === undefined || functionType === "" || functionType === "CHAT";
}
function isPlanModeRuntime(value) {
    return value === "main" || value === "floating";
}
function isNonEmptyString(value) {
    return typeof value === "string" && value.trim() !== "";
}
async function handleSubmitPlanaskAnswersIpc(message) {
    const text = (0, plan_mode_i18n_js_1.resolvePlanModeI18n)();
    try {
        const activeView = await plan_mode_runtime_ipc_js_1.PlanModeShared.getSingleActiveChatView();
        if (!activeView) {
            await Tools.System.toast(text.toastChatViewMissing);
            return {
                success: false,
                error: text.toastChatViewMissing,
            };
        }
        void Tools.Chat.sendMessage(message, activeView.chatId, undefined, undefined, { runtime: activeView.runtime }).catch((error) => {
            const errorText = error instanceof Error
                ? error.message || "error"
                : (typeof error === "string" || error == null ? error || "error" : "error");
            const toastMessage = `${text.askToastAnswerSendFailedPrefix}${errorText}`;
            void Tools.System.toast(toastMessage);
        });
        await Tools.System.toast(text.askToastAnswerSent);
        return { success: true };
    }
    catch (error) {
        const errorText = error instanceof Error
            ? error.message || "error"
            : (typeof error === "string" || error == null ? error || "error" : "error");
        const messageText = `${text.askToastAnswerSendFailedPrefix}${errorText}`;
        await Tools.System.toast(messageText);
        return {
            success: false,
            error: messageText,
        };
    }
}
async function handleStartImplementationIpc(planContent) {
    const text = (0, plan_mode_i18n_js_1.resolvePlanModeI18n)();
    const normalizedPlanContent = planContent.trim();
    if (!normalizedPlanContent) {
        const messageText = text.toastPlanEmpty;
        await Tools.System.toast(messageText);
        return { success: false, error: messageText };
    }
    try {
        const activeView = await plan_mode_runtime_ipc_js_1.PlanModeShared.getSingleActiveChatView();
        if (!activeView) {
            await Tools.System.toast(text.toastChatViewMissing);
            return { success: false, error: text.toastChatViewMissing };
        }
        if (!(await plan_mode_runtime_ipc_js_1.PlanModeShared.claimPlanStart(normalizedPlanContent))) {
            await Tools.System.toast(text.toastPlanAlreadyStarted);
            return { success: true, alreadyStarted: true };
        }
        try {
            const written = await plan_mode_runtime_ipc_js_1.PlanModeShared.writePlanFile(activeView.chatId, normalizedPlanContent);
            await plan_mode_runtime_ipc_js_1.PlanModeShared.disable(written.chatId);
            void Tools.Chat.sendMessage(text.implementationMessage, written.chatId, undefined, undefined, { runtime: activeView.runtime }).catch((error) => {
                const errorText = error instanceof Error
                    ? error.message || "error"
                    : (typeof error === "string" || error == null ? error || "error" : "error");
                const messageText = `${text.toastPlanSendFailedPrefix}${errorText}`;
                void Tools.System.toast(messageText);
            });
            void Tools.System.toast(text.toastPlanStarted);
            return { success: true };
        }
        catch (error) {
            await plan_mode_runtime_ipc_js_1.PlanModeShared.forgetPlanStart(normalizedPlanContent);
            throw error;
        }
    }
    catch (error) {
        const errorText = error instanceof Error
            ? error.message || "error"
            : (typeof error === "string" || error == null ? error || "error" : "error");
        const messageText = `${text.toastPlanWriteFailedPrefix}${errorText}`;
        await Tools.System.toast(messageText);
        return { success: false, error: messageText };
    }
}
async function handleIsPlanStartedIpc(planContent) {
    try {
        return await plan_mode_runtime_ipc_js_1.PlanModeShared.isPlanStarted(planContent);
    }
    catch (error) {
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("handleIsPlanStartedIpc.error", {
            message: error instanceof Error ? error.message : "error",
        });
        return false;
    }
}
function registerPlanModeIpc() {
    if (planModeIpcRegistered) {
        return;
    }
    planModeIpcRegistered = true;
    (0, plan_mode_runtime_ipc_js_1.registerSharedMethods)(plan_mode_runtime_ipc_js_1.PlanModeShared);
    ToolPkg.ipc.on(plan_mode_ask_execution_js_1.PLAN_MODE_SUBMIT_ANSWERS_IPC_CHANNEL, handleSubmitPlanaskAnswersIpc);
    ToolPkg.ipc.on(plan_mode_execution_js_1.PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL, handleStartImplementationIpc);
    ToolPkg.ipc.on(plan_mode_execution_js_1.PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL, handleIsPlanStartedIpc);
}
function filterPlanModeTools(availableTools) {
    return availableTools.filter((tool) => !PLAN_MODE_BLOCKED_TOOL_NAMES.has(tool.name));
}
function appendPlanPromptToPreparedHistory(preparedHistory, extraPrompt) {
    const nextHistory = preparedHistory.slice();
    const systemIndex = nextHistory.findIndex((turn) => turn.kind === "SYSTEM");
    if (systemIndex < 0) {
        return [
            {
                kind: "SYSTEM",
                content: extraPrompt,
            },
            ...nextHistory,
        ];
    }
    const systemTurn = nextHistory[systemIndex];
    nextHistory[systemIndex] = {
        ...systemTurn,
        content: (0, plan_mode_prompt_js_1.appendPrompt)(systemTurn.content, extraPrompt),
    };
    return nextHistory;
}
async function onInputMenuToggle(event) {
    try {
        const payload = event.eventPayload;
        const action = payload.action;
        const runtime = payload.runtime;
        const chatId = payload.chatId;
        if ((action !== "create" && action !== "toggle") || !isPlanModeRuntime(runtime) || chatId === undefined) {
            return null;
        }
        const text = (0, plan_mode_i18n_js_1.resolvePlanModeI18n)();
        const enabled = await plan_mode_runtime_ipc_js_1.PlanModeShared.isEnabled(chatId);
        const workspace = await plan_mode_runtime_ipc_js_1.PlanModeShared.resolveWorkspace(chatId, runtime);
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onInputMenuToggle", {
            action,
            runtime,
            chatId,
            enabled,
            hasWorkspace: !!workspace,
            workspacePath: workspace?.workspacePath,
        });
        if (action === "create") {
            return {
                toggles: [
                    {
                        id: plan_mode_constants_js_1.TOGGLE_ID,
                        title: text.menuTitle,
                        description: workspace
                            ? enabled
                                ? text.menuDescriptionEnabled
                                : text.menuDescriptionDisabled
                            : text.menuDescriptionWorkspaceMissing,
                        icon: plan_mode_constants_js_1.TOGGLE_ICON,
                        isChecked: enabled,
                    },
                ],
            };
        }
        if (action === "toggle" && payload.toggleId === plan_mode_constants_js_1.TOGGLE_ID) {
            if (enabled) {
                (0, plan_mode_workspace_js_1.logPlanModeDebug)("toggle.disable", { chatId, runtime });
                await plan_mode_runtime_ipc_js_1.PlanModeShared.disable(chatId);
                return null;
            }
            if (!workspace) {
                (0, plan_mode_workspace_js_1.logPlanModeDebug)("toggle.enable_denied_workspace_missing", { chatId, runtime });
                await Tools.System.toast(text.toastWorkspaceRequired);
                return null;
            }
            (0, plan_mode_workspace_js_1.logPlanModeDebug)("toggle.enable", {
                chatId,
                runtime,
                workspacePath: workspace.workspacePath,
                workspaceEnv: workspace.workspaceEnv,
            });
            await plan_mode_runtime_ipc_js_1.PlanModeShared.enable(workspace.chatId);
        }
        return null;
    }
    catch (error) {
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onInputMenuToggle.error", {
            message: error instanceof Error ? error.message : "error",
        });
        return null;
    }
}
async function onChatViewEvent(event) {
    try {
        const payload = event.eventPayload;
        const eventName = event.eventName;
        const runtime = payload.runtime;
        const viewId = payload.viewId;
        const chatId = payload.chatId;
        const workspacePath = payload.workspacePath;
        const workspaceEnv = payload.workspaceEnv;
        const title = payload.title;
        if (!isPlanModeRuntime(runtime) ||
            !isNonEmptyString(viewId) ||
            !isNonEmptyString(chatId) ||
            !isNonEmptyString(workspacePath) ||
            typeof title !== "string") {
            (0, plan_mode_workspace_js_1.logPlanModeDebug)("onChatViewEvent.ignore_invalid_workspace", {
                eventName,
                runtime,
                viewId: typeof viewId === "string" ? viewId : undefined,
                chatId: typeof chatId === "string" ? chatId : undefined,
                workspacePath: typeof workspacePath === "string" ? workspacePath : workspacePath === null ? "null" : undefined,
                workspaceEnv: typeof workspaceEnv === "string" ? workspaceEnv : workspaceEnv === null ? "null" : undefined,
            });
            return;
        }
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onChatViewEvent", {
            eventName,
            runtime,
            viewId,
            chatId,
            workspacePath,
            workspaceEnv,
            title,
        });
        if (eventName === "view_closed") {
            await plan_mode_runtime_ipc_js_1.PlanModeShared.removeTrackedChatView(runtime, viewId);
            return;
        }
        await plan_mode_runtime_ipc_js_1.PlanModeShared.upsertTrackedChatView({
            viewId,
            runtime,
            chatId,
            workspacePath,
            workspaceEnv: isNonEmptyString(workspaceEnv) ? workspaceEnv : undefined,
            title,
            updatedAt: Date.now(),
        });
    }
    catch (error) {
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onChatViewEvent.error", {
            message: error instanceof Error ? error.message : "error",
        });
    }
}
async function onSystemPromptCompose(event) {
    try {
        const payload = event.eventPayload;
        const stage = payload.stage === undefined ? event.eventName : payload.stage;
        if (stage !== "after_compose_system_prompt" || !usesChatPrompt(payload)) {
            return null;
        }
        const useEnglish = payload.useEnglish === true;
        const chatId = payload.chatId;
        const currentPrompt = payload.systemPrompt;
        if (chatId === undefined || currentPrompt === undefined) {
            return null;
        }
        if (await plan_mode_runtime_ipc_js_1.PlanModeShared.isEnabled(chatId)) {
            return {
                systemPrompt: (0, plan_mode_prompt_js_1.appendPrompt)(currentPrompt, (0, plan_mode_prompt_js_1.buildPlanningModePrompt)(useEnglish)),
            };
        }
        const workspace = await plan_mode_runtime_ipc_js_1.PlanModeShared.resolveWorkspace(chatId);
        if (!workspace || !(await plan_mode_runtime_ipc_js_1.PlanModeShared.hasPlanFile(workspace.chatId))) {
            return null;
        }
        return {
            systemPrompt: (0, plan_mode_prompt_js_1.appendPrompt)(currentPrompt, (0, plan_mode_prompt_js_1.buildExistingPlanPrompt)(useEnglish)),
        };
    }
    catch (error) {
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onSystemPromptCompose.error", {
            message: error instanceof Error ? error.message : "error",
        });
        return null;
    }
}
async function onToolPromptCompose(event) {
    try {
        const payload = event.eventPayload;
        const stage = payload.stage === undefined ? event.eventName : payload.stage;
        if (stage !== "filter_tool_prompt_items" && stage !== "filter_tool_call_tools") {
            return null;
        }
        const chatId = payload.chatId;
        const availableTools = payload.availableTools;
        if (chatId === undefined || availableTools === undefined || !usesChatPrompt(payload)) {
            return null;
        }
        if (!(await plan_mode_runtime_ipc_js_1.PlanModeShared.isEnabled(chatId))) {
            return null;
        }
        const filteredTools = filterPlanModeTools(availableTools);
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onToolPromptCompose", {
            chatId,
            stage,
            toolCountBefore: availableTools.length,
            toolCountAfter: filteredTools.length,
        });
        return {
            availableTools: filteredTools,
        };
    }
    catch (error) {
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onToolPromptCompose.error", {
            message: error instanceof Error ? error.message : "error",
        });
        return null;
    }
}
async function buildPromptFinalizeResult(payload) {
    const chatId = payload.chatId;
    const preparedHistory = payload.preparedHistory;
    if (chatId === undefined || preparedHistory === undefined || !usesChatPrompt(payload)) {
        return null;
    }
    const useEnglish = payload.useEnglish === true;
    if (await plan_mode_runtime_ipc_js_1.PlanModeShared.isEnabled(chatId)) {
        return {
            preparedHistory: appendPlanPromptToPreparedHistory(preparedHistory, (0, plan_mode_prompt_js_1.buildPlanningModePrompt)(useEnglish)),
        };
    }
    const workspace = await plan_mode_runtime_ipc_js_1.PlanModeShared.resolveWorkspace(chatId);
    if (!workspace || !(await plan_mode_runtime_ipc_js_1.PlanModeShared.hasPlanFile(workspace.chatId))) {
        return null;
    }
    return {
        preparedHistory: appendPlanPromptToPreparedHistory(preparedHistory, (0, plan_mode_prompt_js_1.buildExistingPlanPrompt)(useEnglish)),
    };
}
async function onPromptFinalize(event) {
    try {
        const payload = event.eventPayload;
        const stage = payload.stage === undefined ? event.eventName : payload.stage;
        if (stage !== "before_send_to_model") {
            return null;
        }
        return await buildPromptFinalizeResult(payload);
    }
    catch (error) {
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onPromptFinalize.error", {
            message: error instanceof Error ? error.message : "error",
        });
        return null;
    }
}
async function onPromptEstimateFinalize(event) {
    try {
        const payload = event.eventPayload;
        const stage = payload.stage === undefined ? event.eventName : payload.stage;
        if (stage !== "before_send_to_model") {
            return null;
        }
        return await buildPromptFinalizeResult(payload);
    }
    catch (error) {
        (0, plan_mode_workspace_js_1.logPlanModeDebug)("onPromptEstimateFinalize.error", {
            message: error instanceof Error ? error.message : "error",
        });
        return null;
    }
}
registerPlanModeIpc();
function registerToolPkg() {
    ToolPkg.registerInputMenuTogglePlugin({
        id: plan_mode_constants_js_1.MENU_HOOK_ID,
        function: onInputMenuToggle,
    });
    ToolPkg.registerChatViewHook({
        id: plan_mode_constants_js_1.CHAT_VIEW_HOOK_ID,
        function: onChatViewEvent,
    });
    ToolPkg.registerSystemPromptComposeHook({
        id: plan_mode_constants_js_1.PROMPT_HOOK_ID,
        function: onSystemPromptCompose,
    });
    ToolPkg.registerToolPromptComposeHook({
        id: plan_mode_constants_js_1.TOOL_PROMPT_HOOK_ID,
        function: onToolPromptCompose,
    });
    ToolPkg.registerPromptFinalizeHook({
        id: plan_mode_constants_js_1.PROMPT_FINALIZE_HOOK_ID,
        function: onPromptFinalize,
    });
    ToolPkg.registerPromptEstimateFinalizeHook({
        id: plan_mode_constants_js_1.PROMPT_ESTIMATE_FINALIZE_HOOK_ID,
        function: onPromptEstimateFinalize,
    });
    ToolPkg.registerXmlRenderPlugin({
        id: plan_mode_constants_js_1.XML_RENDER_HOOK_ID,
        tag: plan_mode_constants_js_1.XML_TAG,
        function: plantodo_xml_render_plugin_js_1.onPlantodoXmlRender,
    });
    ToolPkg.registerXmlRenderPlugin({
        id: plan_mode_constants_js_1.PLANASK_XML_RENDER_HOOK_ID,
        tag: plan_mode_constants_js_1.PLANASK_XML_TAG,
        function: planask_xml_render_plugin_js_1.onPlanaskXmlRender,
    });
    return true;
}

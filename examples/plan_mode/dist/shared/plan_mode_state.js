"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.readPlanModeStateSnapshot = readPlanModeStateSnapshot;
exports.readPlanModeStateAsync = readPlanModeStateAsync;
exports.isPlanModeEnabledInState = isPlanModeEnabledInState;
exports.setPlanModeEnabledForChatAsync = setPlanModeEnabledForChatAsync;
exports.isPlanStartRecorded = isPlanStartRecorded;
exports.recordPlanStart = recordPlanStart;
exports.forgetPlanStart = forgetPlanStart;
exports.readActiveChatViewForRuntime = readActiveChatViewForRuntime;
exports.readTrackedChatViewByChatId = readTrackedChatViewByChatId;
exports.readSingleActiveChatView = readSingleActiveChatView;
exports.upsertTrackedChatViewAsync = upsertTrackedChatViewAsync;
exports.removeTrackedChatViewAsync = removeTrackedChatViewAsync;
const state = {
    enabledChatIds: {},
    startedPlanKeys: {},
    trackedViewsByChatId: {},
};
function cloneTrackedChatView(view) {
    return {
        viewId: view.viewId,
        runtime: view.runtime,
        chatId: view.chatId,
        workspacePath: view.workspacePath,
        workspaceEnv: view.workspaceEnv,
        title: view.title,
        updatedAt: view.updatedAt,
    };
}
function cloneTrackedViewsByChatId() {
    const next = {};
    Object.entries(state.trackedViewsByChatId).forEach(([chatId, view]) => {
        next[chatId] = cloneTrackedChatView(view);
    });
    return next;
}
function readPlanModeStateSnapshot() {
    return {
        enabledChatIds: { ...state.enabledChatIds },
        startedPlanKeys: { ...state.startedPlanKeys },
        trackedViewsByChatId: cloneTrackedViewsByChatId(),
    };
}
async function readPlanModeStateAsync() {
    return readPlanModeStateSnapshot();
}
function isPlanModeEnabledInState(chatId) {
    return state.enabledChatIds[chatId] === true;
}
async function setPlanModeEnabledForChatAsync(chatId, enabled) {
    if (enabled) {
        state.enabledChatIds[chatId] = true;
        return;
    }
    delete state.enabledChatIds[chatId];
}
function isPlanStartRecorded(planKey) {
    return state.startedPlanKeys[planKey] === true;
}
function recordPlanStart(planKey) {
    state.startedPlanKeys[planKey] = true;
}
function forgetPlanStart(planKey) {
    delete state.startedPlanKeys[planKey];
}
function readActiveChatViewForRuntime(runtime) {
    const view = Object.values(state.trackedViewsByChatId).find((item) => item.runtime === runtime);
    return view ? cloneTrackedChatView(view) : null;
}
function readTrackedChatViewByChatId(chatId) {
    const view = state.trackedViewsByChatId[chatId];
    return view ? cloneTrackedChatView(view) : null;
}
function readSingleActiveChatView() {
    const views = Object.values(state.trackedViewsByChatId);
    if (views.length !== 1) {
        return null;
    }
    return cloneTrackedChatView(views[0]);
}
async function upsertTrackedChatViewAsync(view) {
    Object.entries(state.trackedViewsByChatId).forEach(([chatId, trackedView]) => {
        if (trackedView.runtime === view.runtime && chatId !== view.chatId) {
            delete state.trackedViewsByChatId[chatId];
        }
    });
    state.trackedViewsByChatId[view.chatId] = cloneTrackedChatView(view);
}
async function removeTrackedChatViewAsync(runtime, viewId) {
    Object.entries(state.trackedViewsByChatId).forEach(([chatId, trackedView]) => {
        if (trackedView.runtime === runtime && trackedView.viewId === viewId) {
            delete state.trackedViewsByChatId[chatId];
        }
    });
}

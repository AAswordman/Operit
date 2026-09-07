"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.normalizePlanText = normalizePlanText;
exports.normalizePlanContent = normalizePlanContent;
exports.resolvePlanFileBinding = resolvePlanFileBinding;
exports.hasPlanFile = hasPlanFile;
exports.readPlanFile = readPlanFile;
exports.planFileMatchesContent = planFileMatchesContent;
exports.writePlanFile = writePlanFile;
exports.deletePlanFile = deletePlanFile;
const plan_mode_constants_js_1 = require("./plan_mode_constants.js");
const plan_mode_workspace_js_1 = require("./plan_mode_workspace.js");
function normalizePlanText(content) {
    return content.replace(/\r\n/g, "\n").trim();
}
function normalizePlanContent(content) {
    const normalized = normalizePlanText(content);
    if (!normalized) {
        throw new Error("plan content is empty");
    }
    return `${normalized}\n`;
}
function resolvePlanFileBinding(chatId) {
    const binding = (0, plan_mode_workspace_js_1.resolveChatWorkspace)(chatId);
    if (!binding) {
        return null;
    }
    return {
        ...binding,
        path: (0, plan_mode_workspace_js_1.buildPlanFilePath)(binding.workspacePath),
    };
}
async function hasPlanFile(chatId) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        return false;
    }
    const result = await Tools.Files.exists(binding.path, "android");
    return result.exists;
}
async function readPlanFile(chatId) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        return null;
    }
    const exists = await Tools.Files.exists(binding.path, "android");
    if (!exists.exists) {
        return null;
    }
    const result = await Tools.Files.read({ path: binding.path, environment: "android" });
    return {
        ...binding,
        content: result.content.replace(/\r\n/g, "\n"),
    };
}
async function planFileMatchesContent(chatId, content) {
    const normalized = normalizePlanText(content);
    if (!normalized) {
        return false;
    }
    const plan = await readPlanFile(chatId);
    return plan !== null && normalizePlanText(plan.content) === normalized;
}
async function writePlanFile(chatId, content) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        throw new Error("workspace is not bound");
    }
    const normalized = normalizePlanContent(content);
    await Tools.Files.mkdir(`${binding.workspacePath}/${plan_mode_constants_js_1.PLAN_FILE_DIRECTORY_NAME}`, true, "android");
    await Tools.Files.write(binding.path, normalized, false, "android");
    return {
        ...binding,
        path: binding.path,
        content: normalized,
    };
}
async function deletePlanFile(chatId) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        throw new Error("workspace is not bound");
    }
    const exists = await Tools.Files.exists(binding.path, "android");
    if (exists.exists) {
        await Tools.Files.deleteFile(binding.path, false, "android");
    }
    return {
        chatId: binding.chatId,
        workspacePath: binding.workspacePath,
        path: binding.path,
        deleted: exists.exists,
    };
}

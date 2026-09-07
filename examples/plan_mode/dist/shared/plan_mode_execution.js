"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL = exports.PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL = void 0;
exports.isPlanImplementationStarted = isPlanImplementationStarted;
exports.startPlanImplementation = startPlanImplementation;
const planModeI18n = __importStar(require("./plan_mode_i18n.js"));
exports.PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL = "plan_mode.start_implementation";
exports.PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL = "plan_mode.is_plan_started";
/**
 * Renderer state is rebuilt from scratch whenever the chat view is recreated, so the
 * plan card asks the plugin runtime whether this exact plan was already handed off.
 */
async function isPlanImplementationStarted(planContent) {
    const normalizedPlanContent = planContent.trim();
    if (!normalizedPlanContent) {
        return false;
    }
    try {
        return await ToolPkg.ipc.call(exports.PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL, normalizedPlanContent);
    }
    catch (error) {
        const errorText = error instanceof Error
            ? error.message || "error"
            : (typeof error === "string" || error == null ? error || "error" : "error");
        console.error(`[plan_mode_execution] isPlanImplementationStarted failed: channel=${exports.PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL}, planLength=${normalizedPlanContent.length}, error=${errorText}`);
        return false;
    }
}
async function startPlanImplementation(planContent) {
    const text = planModeI18n.resolvePlanModeI18n();
    const normalizedPlanContent = planContent.trim();
    if (!normalizedPlanContent) {
        const message = text.toastPlanEmpty;
        await Tools.System.toast(message);
        return { success: false, error: message };
    }
    try {
        return await ToolPkg.ipc.call(exports.PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL, normalizedPlanContent);
    }
    catch (error) {
        const errorText = error instanceof Error
            ? error.message || "error"
            : (typeof error === "string" || error == null ? error || "error" : "error");
        const message = `${text.toastPlanWriteFailedPrefix}${errorText}`;
        console.error(`[plan_mode_execution] startPlanImplementation failed: channel=${exports.PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL}, planLength=${normalizedPlanContent.length}, error=${errorText}`);
        await Tools.System.toast(message);
        return { success: false, error: message };
    }
}

"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.PlanModeShared = exports.PLAN_MODE_REMOVE_TRACKED_CHAT_VIEW_IPC_CHANNEL = exports.PLAN_MODE_UPSERT_TRACKED_CHAT_VIEW_IPC_CHANNEL = exports.PLAN_MODE_FORGET_PLAN_START_IPC_CHANNEL = exports.PLAN_MODE_CLAIM_PLAN_START_IPC_CHANNEL = exports.PLAN_MODE_IS_PLAN_STARTED_SHARED_IPC_CHANNEL = exports.PLAN_MODE_WRITE_PLAN_FILE_IPC_CHANNEL = exports.PLAN_MODE_HAS_PLAN_FILE_IPC_CHANNEL = exports.PLAN_MODE_RESOLVE_WORKSPACE_IPC_CHANNEL = exports.PLAN_MODE_DISABLE_IPC_CHANNEL = exports.PLAN_MODE_ENABLE_IPC_CHANNEL = exports.PLAN_MODE_IS_ENABLED_IPC_CHANNEL = exports.PLAN_MODE_GET_SINGLE_ACTIVE_CHAT_VIEW_IPC_CHANNEL = void 0;
exports.registerSharedMethods = registerSharedMethods;
const plan_mode_mode_js_1 = require("./plan_mode_mode.js");
const plan_mode_plan_file_js_1 = require("./plan_mode_plan_file.js");
const plan_mode_state_js_1 = require("./plan_mode_state.js");
const plan_mode_workspace_js_1 = require("./plan_mode_workspace.js");
const sharedMethodMetadataMap = new WeakMap();
function readSharedMethodMetadata(target) {
    return sharedMethodMetadataMap.get(target) ?? [];
}
function appendSharedMethodMetadata(target, metadata) {
    const existing = readSharedMethodMetadata(target);
    sharedMethodMetadataMap.set(target, [...existing, metadata]);
}
function Shared(channel) {
    return function (target, propertyKey, descriptor) {
        const original = descriptor.value;
        if (!original) {
            throw new Error(`@Shared can only decorate methods: ${propertyKey}`);
        }
        appendSharedMethodMetadata(target, {
            channel,
            methodName: propertyKey,
            original: original,
        });
        descriptor.value = (async (...args) => {
            return await ToolPkg.ipc.call(channel, args);
        });
    };
}
function registerSharedMethods(target) {
    const entries = readSharedMethodMetadata(target);
    entries.forEach((entry) => {
        ToolPkg.ipc.on(entry.channel, async (payload) => {
            const args = Array.isArray(payload) ? payload : [];
            return await entry.original.apply(target, args);
        });
    });
}
exports.PLAN_MODE_GET_SINGLE_ACTIVE_CHAT_VIEW_IPC_CHANNEL = "plan_mode.get_single_active_chat_view";
exports.PLAN_MODE_IS_ENABLED_IPC_CHANNEL = "plan_mode.is_enabled";
exports.PLAN_MODE_ENABLE_IPC_CHANNEL = "plan_mode.enable";
exports.PLAN_MODE_DISABLE_IPC_CHANNEL = "plan_mode.disable";
exports.PLAN_MODE_RESOLVE_WORKSPACE_IPC_CHANNEL = "plan_mode.resolve_workspace";
exports.PLAN_MODE_HAS_PLAN_FILE_IPC_CHANNEL = "plan_mode.has_plan_file";
exports.PLAN_MODE_WRITE_PLAN_FILE_IPC_CHANNEL = "plan_mode.write_plan_file";
exports.PLAN_MODE_IS_PLAN_STARTED_SHARED_IPC_CHANNEL = "plan_mode.shared_is_plan_started";
exports.PLAN_MODE_CLAIM_PLAN_START_IPC_CHANNEL = "plan_mode.claim_plan_start";
exports.PLAN_MODE_FORGET_PLAN_START_IPC_CHANNEL = "plan_mode.forget_plan_start";
exports.PLAN_MODE_UPSERT_TRACKED_CHAT_VIEW_IPC_CHANNEL = "plan_mode.upsert_tracked_chat_view";
exports.PLAN_MODE_REMOVE_TRACKED_CHAT_VIEW_IPC_CHANNEL = "plan_mode.remove_tracked_chat_view";
class PlanModeShared {
    static async getSingleActiveChatView() {
        return (0, plan_mode_state_js_1.readSingleActiveChatView)();
    }
    static async isEnabled(chatId) {
        return (0, plan_mode_mode_js_1.isPlanModeEnabledForChat)(chatId);
    }
    static async enable(chatId) {
        await (0, plan_mode_mode_js_1.enablePlanModeForChat)(chatId);
    }
    static async disable(chatId) {
        await (0, plan_mode_mode_js_1.disablePlanMode)(chatId);
    }
    static async resolveWorkspace(chatId, runtime) {
        return (0, plan_mode_workspace_js_1.resolveChatWorkspace)(chatId, runtime);
    }
    static async hasPlanFile(chatId) {
        return await (0, plan_mode_plan_file_js_1.hasPlanFile)(chatId);
    }
    static async writePlanFile(chatId, content) {
        return await (0, plan_mode_plan_file_js_1.writePlanFile)(chatId, content);
    }
    /**
     * A plan counts as started once this session handed it off, or once the workspace plan
     * file still holds exactly this plan because an earlier session started it.
     */
    static async isPlanStarted(planContent) {
        const planKey = (0, plan_mode_plan_file_js_1.normalizePlanText)(planContent);
        if (!planKey) {
            return false;
        }
        if ((0, plan_mode_state_js_1.isPlanStartRecorded)(planKey)) {
            return true;
        }
        const view = (0, plan_mode_state_js_1.readSingleActiveChatView)();
        if (!view) {
            return false;
        }
        return await (0, plan_mode_plan_file_js_1.planFileMatchesContent)(view.chatId, planKey);
    }
    static async claimPlanStart(planContent) {
        const planKey = (0, plan_mode_plan_file_js_1.normalizePlanText)(planContent);
        if (!planKey) {
            return false;
        }
        if ((0, plan_mode_state_js_1.isPlanStartRecorded)(planKey)) {
            return false;
        }
        // Record before awaiting so a second tap cannot slip through the same check.
        (0, plan_mode_state_js_1.recordPlanStart)(planKey);
        const view = (0, plan_mode_state_js_1.readSingleActiveChatView)();
        if (view && (await (0, plan_mode_plan_file_js_1.planFileMatchesContent)(view.chatId, planKey))) {
            return false;
        }
        return true;
    }
    static async forgetPlanStart(planContent) {
        (0, plan_mode_state_js_1.forgetPlanStart)((0, plan_mode_plan_file_js_1.normalizePlanText)(planContent));
    }
    static async upsertTrackedChatView(view) {
        await (0, plan_mode_state_js_1.upsertTrackedChatViewAsync)(view);
    }
    static async removeTrackedChatView(runtime, viewId) {
        await (0, plan_mode_state_js_1.removeTrackedChatViewAsync)(runtime, viewId);
    }
}
exports.PlanModeShared = PlanModeShared;
__decorate([
    Shared(exports.PLAN_MODE_GET_SINGLE_ACTIVE_CHAT_VIEW_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], PlanModeShared, "getSingleActiveChatView", null);
__decorate([
    Shared(exports.PLAN_MODE_IS_ENABLED_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "isEnabled", null);
__decorate([
    Shared(exports.PLAN_MODE_ENABLE_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "enable", null);
__decorate([
    Shared(exports.PLAN_MODE_DISABLE_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "disable", null);
__decorate([
    Shared(exports.PLAN_MODE_RESOLVE_WORKSPACE_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "resolveWorkspace", null);
__decorate([
    Shared(exports.PLAN_MODE_HAS_PLAN_FILE_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "hasPlanFile", null);
__decorate([
    Shared(exports.PLAN_MODE_WRITE_PLAN_FILE_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "writePlanFile", null);
__decorate([
    Shared(exports.PLAN_MODE_IS_PLAN_STARTED_SHARED_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "isPlanStarted", null);
__decorate([
    Shared(exports.PLAN_MODE_CLAIM_PLAN_START_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "claimPlanStart", null);
__decorate([
    Shared(exports.PLAN_MODE_FORGET_PLAN_START_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "forgetPlanStart", null);
__decorate([
    Shared(exports.PLAN_MODE_UPSERT_TRACKED_CHAT_VIEW_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "upsertTrackedChatView", null);
__decorate([
    Shared(exports.PLAN_MODE_REMOVE_TRACKED_CHAT_VIEW_IPC_CHANNEL),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], PlanModeShared, "removeTrackedChatView", null);

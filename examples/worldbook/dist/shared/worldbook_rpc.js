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
exports.registerWorldBookStorageService = registerWorldBookStorageService;
const service = __importStar(require("./worldbook_service.js"));
const PREFIX = "worldbook.storage.";
let registered = false;
function errorDetails(error) {
    if (error !== null && typeof error === "object" && "code" in error && "message" in error &&
        typeof error.code === "string" && typeof error.message === "string") {
        return { code: error.code, message: error.message, details: "details" in error ? error.details : undefined };
    }
    if (error instanceof Error)
        return { code: "WORLD_BOOK_STORAGE_ERROR", message: error.message };
    throw error;
}
function bind(name, handler) {
    ToolPkg.ipc.on(PREFIX + name, async (payload) => {
        try {
            return { ok: true, value: await handler(payload) };
        }
        catch (error) {
            console.error("World book UI operation failed", name, error);
            return { ok: false, error: errorDetails(error) };
        }
    });
}
function registerWorldBookStorageService() {
    if (registered)
        return;
    registered = true;
    bind("listEntries", () => service.listWorldBookEntries());
    bind("getEntry", (id) => service.getWorldBookEntry(id));
    bind("createEntry", (params) => service.createWorldBookEntry(params));
    bind("importEntries", (params) => service.importWorldBookEntries(params));
    bind("updateEntry", (params) => service.updateWorldBookEntry(params));
    bind("deleteEntry", (id) => service.deleteWorldBookEntry(id));
    bind("toggleEntry", (id) => service.toggleWorldBookEntry(id));
    bind("listCharacterCards", () => service.listWorldBookCharacterCards());
    bind("listGroups", () => service.listWorldBookGroups());
    bind("createGroup", (params) => service.createWorldBookGroup(params));
    bind("deleteGroup", (params) => service.deleteWorldBookGroup(params.id, params.deleteEntries));
    bind("copyEntry", (params) => service.copyEntryToGroup(params.entryId, params.targetGroupId));
    bind("updateGroup", (params) => service.updateWorldBookGroup(params));
}

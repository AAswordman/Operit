"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.installMockRuntime = installMockRuntime;
exports.clearModule = clearModule;
exports.loadWorldBookModules = loadWorldBookModules;
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
function normalizePath(value) {
    return value.replace(/\\/g, "/");
}
function ensureDir(dirPath) {
    node_fs_1.default.mkdirSync(dirPath, { recursive: true });
}
function makeFsTools() {
    return {
        async mkdir(targetPath) {
            ensureDir(targetPath);
            return { success: true };
        },
        async exists(targetPath) {
            const exists = node_fs_1.default.existsSync(targetPath);
            if (!exists) {
                return { exists: false, isDirectory: false, size: 0 };
            }
            const stat = node_fs_1.default.statSync(targetPath);
            return {
                exists: true,
                isDirectory: stat.isDirectory(),
                size: stat.size
            };
        },
        async read(targetPath) {
            return { content: node_fs_1.default.readFileSync(targetPath, "utf8") };
        },
        async write(targetPath, content) {
            ensureDir(node_path_1.default.dirname(targetPath));
            node_fs_1.default.writeFileSync(targetPath, String(content), "utf8");
            return { success: true };
        },
        async deleteFile(targetPath, recursive) {
            node_fs_1.default.rmSync(targetPath, { recursive: !!recursive, force: true });
            return { success: true };
        }
    };
}
function installMockRuntime(options) {
    const packageId = String(options?.packageId || "com.operit.worldbook");
    const configDir = node_path_1.default.join(process.cwd(), "temp", options?.rootDirName || `worldbook_test_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`);
    const packageConfigDir = node_path_1.default.join(configDir, packageId);
    node_fs_1.default.rmSync(configDir, { recursive: true, force: true });
    ensureDir(packageConfigDir);
    const callerCardId = String(options?.callerCardId || "card_1");
    const characterCards = options?.characterCards || [
        {
            id: callerCardId,
            name: "Test Card",
            description: "",
            isDefault: false
        }
    ];
    const chatsById = options?.chatsById || {
        chat_1: {
            id: "chat_1",
            characterCardName: "Test Card"
        }
    };
    const messagesByChatId = new Map();
    globalThis.getCallerCardId = () => callerCardId;
    globalThis.getLang = () => String(options?.lang || "zh-CN");
    globalThis.Tools = {
        Files: makeFsTools(),
        Chat: {
            async listCharacterCards() {
                return { cards: characterCards };
            },
            async findChat(params) {
                return { chat: chatsById[String(params?.query || "")] || null };
            },
            async getMessages(chatId, opts) {
                const messages = messagesByChatId.get(String(chatId || "")) || [];
                const order = String(opts?.order || "asc").toLowerCase();
                const sorted = [...messages].sort((left, right) => order === "desc" ? right.timestamp - left.timestamp : left.timestamp - right.timestamp);
                return { messages: sorted };
            }
        }
    };
    const bridges = require(node_path_1.default.join(process.cwd(), "tools/toolpkg/node_storage_runtime.cjs"));
    globalThis.Java = bridges.createNodeJavaBridge();
    globalThis.ToolPkg = {
        ipc: bridges.createNodeIpc(),
        getConfigDir(pluginId) {
            const effectiveId = String(pluginId || packageId || "").trim() || packageId;
            const targetDir = node_path_1.default.join(configDir, effectiveId);
            ensureDir(targetDir);
            return normalizePath(targetDir);
        },
        registerUiRoute() { },
        registerNavigationEntry() { },
        registerSystemPromptComposeHook() { },
        registerPromptFinalizeHook() { }
    };
    return {
        configDir: normalizePath(configDir),
        packageConfigDir: normalizePath(packageConfigDir),
        setMessages(chatId, messages) {
            messagesByChatId.set(String(chatId || ""), [...messages]);
        },
        writePackageFile(fileName, content) {
            const targetPath = node_path_1.default.join(packageConfigDir, fileName);
            ensureDir(node_path_1.default.dirname(targetPath));
            node_fs_1.default.writeFileSync(targetPath, content, "utf8");
        },
        readPackageFile(fileName) {
            return node_fs_1.default.readFileSync(node_path_1.default.join(packageConfigDir, fileName), "utf8");
        }
    };
}
function clearModule(modulePath) {
    const resolved = require.resolve(modulePath);
    delete require.cache[resolved];
}
function loadWorldBookModules() {
    clearModule("../../dist/shared/worldbook_storage.js");
    clearModule("../../dist/shared/worldbook_service.js");
    clearModule("../../dist/shared/worldbook_variables.js");
    clearModule("../../dist/shared/worldbook_rpc.js");
    clearModule("../../dist/shared/worldbook_client.js");
    clearModule("../../dist/main.js");
    const storage = require("../../dist/shared/worldbook_storage.js");
    const service = require("../../dist/shared/worldbook_service.js");
    const variables = require("../../dist/shared/worldbook_variables.js");
    const main = require("../../dist/main.js");
    require("../../dist/shared/worldbook_rpc.js").registerWorldBookStorageService();
    const client = require("../../dist/shared/worldbook_client.js");
    return { storage, service, variables, main, client };
}

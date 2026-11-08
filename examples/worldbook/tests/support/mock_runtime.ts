import fs from "node:fs";
import os from "node:os";
import path from "node:path";

export interface MockMessage {
  sender: string;
  content: string;
  timestamp: number;
}

export interface MockRuntimeState {
  configDir: string;
  packageConfigDir: string;
  setMessages(chatId: string, messages: MockMessage[]): void;
  writePackageFile(fileName: string, content: string): void;
  readPackageFile(fileName: string): string;
}

interface MockChatRecord {
  id: string;
  characterCardName?: string;
}

interface MockCharacterCard {
  id: string;
  name: string;
  description: string;
  isDefault: boolean;
}

function normalizePath(value: string): string {
  return value.replace(/\\/g, "/");
}

function ensureDir(dirPath: string): void {
  fs.mkdirSync(dirPath, { recursive: true });
}

function makeFsTools() {
  return {
    async mkdir(targetPath: string) {
      ensureDir(targetPath);
      return { success: true };
    },
    async exists(targetPath: string) {
      const exists = fs.existsSync(targetPath);
      if (!exists) {
        return { exists: false, isDirectory: false, size: 0 };
      }
      const stat = fs.statSync(targetPath);
      return {
        exists: true,
        isDirectory: stat.isDirectory(),
        size: stat.size
      };
    },
    async read(targetPath: string) {
      return { content: fs.readFileSync(targetPath, "utf8") };
    },
    async write(targetPath: string, content: string) {
      ensureDir(path.dirname(targetPath));
      fs.writeFileSync(targetPath, String(content), "utf8");
      return { success: true };
    },
    async deleteFile(targetPath: string, recursive?: boolean) {
      fs.rmSync(targetPath, { recursive: !!recursive, force: true });
      return { success: true };
    }
  };
}

export function installMockRuntime(options?: {
  callerCardId?: string;
  packageId?: string;
  characterCards?: MockCharacterCard[];
  chatsById?: Record<string, MockChatRecord>;
  lang?: string;
  rootDirName?: string;
}): MockRuntimeState {
  const packageId = String(options?.packageId || "com.operit.worldbook");
  const configDir = path.join(
    process.cwd(),
    "temp",
    options?.rootDirName || `worldbook_test_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
  );
  const packageConfigDir = path.join(configDir, packageId);
  fs.rmSync(configDir, { recursive: true, force: true });
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
  const messagesByChatId = new Map<string, MockMessage[]>();

  (globalThis as Record<string, unknown>).getCallerCardId = () => callerCardId;
  (globalThis as Record<string, unknown>).getLang = () => String(options?.lang || "zh-CN");
  (globalThis as Record<string, unknown>).Tools = {
    Files: makeFsTools(),
    Chat: {
      async listCharacterCards() {
        return { cards: characterCards };
      },
      async findChat(params: { query: string }) {
        return { chat: chatsById[String(params?.query || "")] || null };
      },
      async getMessages(chatId: string, opts?: { order?: string }) {
        const messages = messagesByChatId.get(String(chatId || "")) || [];
        const order = String(opts?.order || "asc").toLowerCase();
        const sorted = [...messages].sort((left, right) =>
          order === "desc" ? right.timestamp - left.timestamp : left.timestamp - right.timestamp
        );
        return { messages: sorted };
      }
    }
  };
  const bridges = require(path.join(process.cwd(), "tools/toolpkg/node_storage_runtime.cjs"));
  (globalThis as Record<string, unknown>).Java = bridges.createNodeJavaBridge();
  (globalThis as Record<string, unknown>).ToolPkg = {
    ipc: bridges.createNodeIpc(),
    getConfigDir(pluginId?: string) {
      const effectiveId = String(pluginId || packageId || "").trim() || packageId;
      const targetDir = path.join(configDir, effectiveId);
      ensureDir(targetDir);
      return normalizePath(targetDir);
    },
    registerUiRoute() {},
    registerNavigationEntry() {},
    registerSystemPromptComposeHook() {},
    registerPromptFinalizeHook() {}
  };

  return {
    configDir: normalizePath(configDir),
    packageConfigDir: normalizePath(packageConfigDir),
    setMessages(chatId: string, messages: MockMessage[]) {
      messagesByChatId.set(String(chatId || ""), [...messages]);
    },
    writePackageFile(fileName: string, content: string) {
      const targetPath = path.join(packageConfigDir, fileName);
      ensureDir(path.dirname(targetPath));
      fs.writeFileSync(targetPath, content, "utf8");
    },
    readPackageFile(fileName: string): string {
      return fs.readFileSync(path.join(packageConfigDir, fileName), "utf8");
    }
  };
}

export function clearModule(modulePath: string): void {
  const resolved = require.resolve(modulePath);
  delete require.cache[resolved];
}

export function loadWorldBookModules() {
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

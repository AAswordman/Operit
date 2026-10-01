#!/usr/bin/env node

"use strict";

const fs = require("fs");
const os = require("os");
const path = require("path");

const MANIFEST_FILENAMES = ["manifest.json", "manifest.hjson"];

function printUsage() {
  console.log(`Usage:
  node tools/toolpkg/toolpkg_hook_runner.js --source <toolpkg_folder|manifest|main.js> --kind <kind> --event <event_name> [options]

Kinds:
  system_prompt
  prompt_finalize
  prompt_input
  prompt_history
  message_processing
  chat_input
  tool_lifecycle
  summary_generate

Options:
  --payload <json|@file>     Hook eventPayload JSON, defaults to {}
  --fixtures <json|@file>    Mock runtime fixtures JSON, defaults to {}
  --hook-id <id>             Invoke only one registered hook
  --config-dir <path>        Override ToolPkg.getConfigDir()
  --reset-config             Delete config dir before running
  --pretty                   Pretty-print JSON result

Fixture JSON example:
{
  "lang": "zh-CN",
  "callerCardId": "card_1",
  "characterCards": [{ "id": "card_1", "name": "Test Card", "description": "", "isDefault": false }],
  "chatsById": {
    "chat_1": { "id": "chat_1", "characterCardName": "Test Card" }
  },
  "messagesByChatId": {
    "chat_1": [
      { "sender": "assistant", "timestamp": 1710000000000, "content": "<JSONPatch>[]</JSONPatch>" }
    ]
  }
}`);
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

function parseArgs(argv) {
  const options = {
    payload: "{}",
    fixtures: "{}",
    pretty: false,
    resetConfig: false
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    switch (arg) {
      case "--source":
        options.source = argv[++index];
        break;
      case "--kind":
        options.kind = argv[++index];
        break;
      case "--event":
        options.event = argv[++index];
        break;
      case "--payload":
        options.payload = argv[++index];
        break;
      case "--fixtures":
        options.fixtures = argv[++index];
        break;
      case "--hook-id":
        options.hookId = argv[++index];
        break;
      case "--config-dir":
        options.configDir = argv[++index];
        break;
      case "--reset-config":
        options.resetConfig = true;
        break;
      case "--pretty":
        options.pretty = true;
        break;
      case "--help":
      case "-h":
        printUsage();
        process.exit(0);
      default:
        fail(`Unknown argument: ${arg}`);
    }
  }

  if (!options.source || !options.kind || !options.event) {
    printUsage();
    process.exit(1);
  }

  return options;
}

function readJsonInput(inputValue, label) {
  const raw = String(inputValue || "").trim();
  if (!raw) {
    return {};
  }
  const content = raw.startsWith("@")
    ? fs.readFileSync(path.resolve(raw.slice(1)), "utf8")
    : raw;
  try {
    return JSON.parse(content);
  } catch (error) {
    fail(`${label} is not valid JSON: ${error.message}`);
  }
}

function resolveManifestFromFolder(folderPath) {
  for (const name of MANIFEST_FILENAMES) {
    const manifestPath = path.join(folderPath, name);
    if (fs.existsSync(manifestPath)) {
      return manifestPath;
    }
  }
  fail(`Missing manifest.json/manifest.hjson in ${folderPath}`);
}

function parseManifestText(text, manifestPath) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (_error) {
    fail(`Only JSON manifest is supported by this runner: ${manifestPath}`);
  }

  const packageId = String(parsed.toolpkg_id || "").trim();
  const mainEntry = String(parsed.main || "").trim().replace(/\\/g, "/");
  if (!packageId) {
    fail(`manifest.toolpkg_id is required: ${manifestPath}`);
  }
  if (!mainEntry) {
    fail(`manifest.main is required: ${manifestPath}`);
  }

  return { packageId, mainEntry };
}

function resolveSource(sourceArg) {
  const resolvedPath = path.resolve(sourceArg);
  if (!fs.existsSync(resolvedPath)) {
    fail(`Source does not exist: ${resolvedPath}`);
  }

  const stat = fs.statSync(resolvedPath);
  if (stat.isDirectory()) {
    const manifestPath = resolveManifestFromFolder(resolvedPath);
    const manifestText = fs.readFileSync(manifestPath, "utf8");
    const manifest = parseManifestText(manifestText, manifestPath);
    return {
      packageRoot: resolvedPath,
      packageId: manifest.packageId,
      mainFile: path.resolve(resolvedPath, manifest.mainEntry)
    };
  }

  if (resolvedPath.endsWith("manifest.json")) {
    const manifestText = fs.readFileSync(resolvedPath, "utf8");
    const manifest = parseManifestText(manifestText, resolvedPath);
    return {
      packageRoot: path.dirname(resolvedPath),
      packageId: manifest.packageId,
      mainFile: path.resolve(path.dirname(resolvedPath), manifest.mainEntry)
    };
  }

  if (resolvedPath.endsWith(".js")) {
    return {
      packageRoot: path.dirname(resolvedPath),
      packageId: path.basename(path.dirname(resolvedPath)),
      mainFile: resolvedPath
    };
  }

  fail(`Unsupported source path: ${resolvedPath}`);
}

function ensureDirectory(targetPath) {
  fs.mkdirSync(targetPath, { recursive: true });
}

function createFsTools() {
  return {
    async mkdir(targetPath) {
      ensureDirectory(targetPath);
      return { success: true };
    },
    async exists(targetPath) {
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
    async read(targetPath) {
      return { content: fs.readFileSync(targetPath, "utf8") };
    },
    async write(targetPath, content) {
      ensureDirectory(path.dirname(targetPath));
      fs.writeFileSync(targetPath, String(content), "utf8");
      return { success: true };
    },
    async deleteFile(targetPath, recursive) {
      fs.rmSync(targetPath, { recursive: !!recursive, force: true });
      return { success: true };
    }
  };
}

function createChatTools(fixtures) {
  const characterCards = Array.isArray(fixtures.characterCards) ? fixtures.characterCards : [];
  const chatsById = fixtures.chatsById && typeof fixtures.chatsById === "object"
    ? fixtures.chatsById
    : {};
  const messagesByChatId = fixtures.messagesByChatId && typeof fixtures.messagesByChatId === "object"
    ? fixtures.messagesByChatId
    : {};

  return {
    async listCharacterCards() {
      return { cards: characterCards };
    },
    async findChat({ query }) {
      const key = String(query || "");
      return { chat: chatsById[key] || null };
    },
    async getMessages(chatId, options = {}) {
      const key = String(chatId || "");
      const order = String(options.order || "asc").toLowerCase();
      const messages = Array.isArray(messagesByChatId[key]) ? messagesByChatId[key] : [];
      const sorted = [...messages].sort((left, right) =>
        order === "desc"
          ? Number(right.timestamp || 0) - Number(left.timestamp || 0)
          : Number(left.timestamp || 0) - Number(right.timestamp || 0)
      );
      return { messages: sorted };
    }
  };
}

function createHookRegistry() {
  return {
    system_prompt: [],
    prompt_finalize: [],
    prompt_input: [],
    prompt_history: [],
    message_processing: [],
    chat_input: [],
    tool_lifecycle: [],
    summary_generate: []
  };
}

function installRuntimeGlobals({ packageId, configDir, fixtures, registry }) {
  global.getLang = () => String(fixtures.lang || "zh-CN");
  global.getCallerCardId = () => {
    const value = fixtures.callerCardId;
    return value == null ? undefined : String(value);
  };

  global.Tools = {
    Files: createFsTools(),
    Chat: createChatTools(fixtures)
  };

  global.ToolPkg = {
    getConfigDir(pluginId) {
      const suffix = pluginId ? String(pluginId).trim() : packageId;
      const target = path.resolve(configDir, suffix || packageId);
      ensureDirectory(target);
      return target.replace(/\\/g, "/");
    },
    registerUiRoute() {},
    registerNavigationEntry() {},
    registerFloatingWindow() {},
    registerToolboxUiModule() {},
    registerAppLifecycleHook() {},
    registerToolPromptComposeHook() {},
    registerPromptEstimateHistoryHook() {},
    registerPromptEstimateFinalizeHook() {},
    registerAiProvider() {},
    registerInputMenuTogglePlugin() {},
    registerXmlRenderPlugin() {},
    registerMessageProcessingPlugin(definition) {
      registry.message_processing.push(definition);
    },
    registerChatInputHook(definition) {
      registry.chat_input.push(definition);
    },
    registerToolLifecycleHook(definition) {
      registry.tool_lifecycle.push(definition);
    },
    registerPromptInputHook(definition) {
      registry.prompt_input.push(definition);
    },
    registerPromptHistoryHook(definition) {
      registry.prompt_history.push(definition);
    },
    registerSystemPromptComposeHook(definition) {
      registry.system_prompt.push(definition);
    },
    registerPromptFinalizeHook(definition) {
      registry.prompt_finalize.push(definition);
    },
    registerSummaryGenerateHook(definition) {
      registry.summary_generate.push(definition);
    }
  };

  global.registerToolPkgUiRoute = global.ToolPkg.registerUiRoute;
  global.registerToolPkgNavigationEntry = global.ToolPkg.registerNavigationEntry;
  global.registerToolPkgFloatingWindow = global.ToolPkg.registerFloatingWindow;
  global.registerToolPkgToolboxUiModule = global.ToolPkg.registerToolboxUiModule;
  global.registerToolPkgAppLifecycleHook = global.ToolPkg.registerAppLifecycleHook;
  global.registerToolPkgMessageProcessingPlugin = global.ToolPkg.registerMessageProcessingPlugin;
  global.registerToolPkgChatInputHook = global.ToolPkg.registerChatInputHook;
  global.registerToolPkgToolLifecycleHook = global.ToolPkg.registerToolLifecycleHook;
  global.registerToolPkgPromptInputHook = global.ToolPkg.registerPromptInputHook;
  global.registerToolPkgPromptHistoryHook = global.ToolPkg.registerPromptHistoryHook;
  global.registerToolPkgPromptEstimateHistoryHook = global.ToolPkg.registerPromptEstimateHistoryHook;
  global.registerToolPkgSystemPromptComposeHook = global.ToolPkg.registerSystemPromptComposeHook;
  global.registerToolPkgToolPromptComposeHook = global.ToolPkg.registerToolPromptComposeHook;
  global.registerToolPkgPromptFinalizeHook = global.ToolPkg.registerPromptFinalizeHook;
  global.registerToolPkgPromptEstimateFinalizeHook = global.ToolPkg.registerPromptEstimateFinalizeHook;
  global.registerToolPkgSummaryGenerateHook = global.ToolPkg.registerSummaryGenerateHook;
  global.registerToolPkgAiProvider = global.ToolPkg.registerAiProvider;
}

function pickHooks(registry, kind, hookId) {
  const hooks = registry[kind];
  if (!Array.isArray(hooks)) {
    fail(`Unsupported hook kind: ${kind}`);
  }

  if (!hookId) {
    return hooks;
  }

  const matched = hooks.filter((item) => String(item.id || "") === String(hookId));
  if (matched.length === 0) {
    fail(`No hook found for kind=${kind} hookId=${hookId}`);
  }
  return matched;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const source = resolveSource(options.source);
  const payload = readJsonInput(options.payload, "payload");
  const fixtures = readJsonInput(options.fixtures, "fixtures");
  const configDir = path.resolve(
    options.configDir || path.join(os.tmpdir(), "operit_toolpkg_hook_runner")
  );

  if (options.resetConfig) {
    fs.rmSync(configDir, { recursive: true, force: true });
  }
  ensureDirectory(configDir);

  const registry = createHookRegistry();
  installRuntimeGlobals({
    packageId: source.packageId,
    configDir,
    fixtures,
    registry
  });

  const loadedMain = require(source.mainFile);
  if (loadedMain && typeof loadedMain.registerToolPkg === "function") {
    await loadedMain.registerToolPkg();
  }

  const hooks = pickHooks(registry, options.kind, options.hookId);
  const baseEvent = {
    event: options.event,
    eventName: options.event,
    eventPayload: payload,
    toolPkgId: source.packageId,
    timestampMs: Date.now()
  };

  const results = [];
  for (const hook of hooks) {
    const hookEvent = {
      ...baseEvent,
      hookId: hook.id
    };
    const value = await hook.function(hookEvent);
    results.push({
      id: hook.id,
      result: value === undefined ? null : value
    });
  }

  const output = {
    packageId: source.packageId,
    mainFile: source.mainFile,
    kind: options.kind,
    event: options.event,
    hookCount: results.length,
    results
  };

  console.log(JSON.stringify(output, null, options.pretty ? 2 : 0));
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});

"use strict";

const assert = require("node:assert/strict");
const path = require("node:path");
const { test } = require("node:test");

const repoRoot = path.resolve(__dirname, "../..");
const mainPath = path.join(repoRoot, "examples", "message_insert", "dist", "main.js");
const sharedPath = path.join(repoRoot, "examples", "message_insert", "dist", "shared.js");
const uiPath = path.join(
  repoRoot,
  "examples",
  "message_insert",
  "dist",
  "ui",
  "index.ui.js"
);

async function withMessageInsertRuntime(run) {
  const originalJava = global.Java;
  const originalGetChatId = global.getChatId;
  const originalGetLang = global.getLang;
  const originalConsoleInfo = console.info;
  const originalConsoleError = console.error;
  const infoMessages = [];
  const errorMessages = [];
  const preferenceValues = new Map();
  const processingStates = [];

  const preferences = {
    getString(key, defaultValue) {
      return preferenceValues.has(key) ? preferenceValues.get(key) : defaultValue;
    },
    edit() {
      const editor = {
        putString(key, value) {
          preferenceValues.set(key, value);
          return editor;
        },
        apply() {},
      };
      return editor;
    },
  };
  const context = {
    getSharedPreferences() {
      return preferences;
    },
  };
  const service = {
    setInputProcessingState(state) {
      processingStates.push(state);
    },
  };

  global.Java = {
    getApplicationContext() {
      return context;
    },
    newInstance(type, text) {
      return { type, text };
    },
    com: {
      ai: {
        assistance: {
          operit: {
            api: {
              chat: {
                EnhancedAIService: {
                  getChatInstance() {
                    return service;
                  },
                  getInstance() {
                    return service;
                  },
                },
              },
            },
          },
        },
      },
    },
  };
  global.getChatId = () => "chat-1";
  global.getLang = () => "en-US";
  console.info = (...args) => infoMessages.push(args.join(" "));
  console.error = (...args) => errorMessages.push(args.join(" "));

  delete require.cache[mainPath];
  delete require.cache[sharedPath];
  delete require.cache[uiPath];
  require.cache[uiPath] = {
    id: uiPath,
    filename: uiPath,
    loaded: true,
    exports: { __esModule: true, default: {} },
  };

  try {
    const shared = require(sharedPath);
    const main = require(mainPath);
    await run({ main, shared, infoMessages, errorMessages, processingStates });
  } finally {
    delete require.cache[mainPath];
    delete require.cache[sharedPath];
    delete require.cache[uiPath];
    console.info = originalConsoleInfo;
    console.error = originalConsoleError;
    global.Java = originalJava;
    global.getChatId = originalGetChatId;
    global.getLang = originalGetLang;
  }
}

function hookEvent(stage, processedInput = "hello") {
  return {
    eventName: stage,
    eventPayload: {
      stage,
      processedInput,
      chatId: "chat-1",
      metadata: {},
    },
  };
}

test("repeated normal no-op hook and menu calls do not emit info logs", async () => {
  await withMessageInsertRuntime(async ({ main, shared, infoMessages, errorMessages }) => {
    let persistInjectedContent = true;
    shared.loadSettings = () => ({ persistInjectedContent });

    for (let index = 0; index < 100; index += 1) {
      assert.equal(await main.onPromptInput(hookEvent("after_process")), null);
      assert.equal(await main.onPromptFinalize(hookEvent("before_finalize_prompt")), null);
      assert.equal(main.onInputMenuToggle({ eventPayload: { action: "create" } }).length, 1);
      assert.deepEqual(main.onInputMenuToggle({ eventPayload: { action: "refresh" } }), []);

      persistInjectedContent = false;
      assert.equal(await main.onPromptInput(hookEvent("before_process")), null);
      persistInjectedContent = true;
      assert.equal(await main.onPromptFinalize(hookEvent("before_send_to_model")), null);

      assert.equal(await main.onPromptInput(hookEvent("before_process", "   ")), null);
      persistInjectedContent = false;
      assert.equal(await main.onPromptFinalize(hookEvent("before_send_to_model", "   ")), null);
      persistInjectedContent = true;

      assert.equal(await shared.appendExtraInfoToMessage("hello", "chat-1"), null);
    }

    assert.deepEqual(infoMessages, []);
    assert.deepEqual(errorMessages, []);
  });
});

test("successful injection keeps concise lifecycle diagnostics and behavior", async () => {
  await withMessageInsertRuntime(async ({ main, shared, infoMessages, errorMessages, processingStates }) => {
    const calls = [];
    shared.loadSettings = () => ({ persistInjectedContent: true });
    shared.appendExtraInfoToMessage = async (...args) => {
      calls.push(args);
      return "hello <attachment/>";
    };

    const result = await main.onPromptInput(hookEvent("before_process"));

    assert.equal(result, "hello <attachment/>");
    assert.deepEqual(calls, [["hello", "chat-1", undefined]]);
    assert.equal(processingStates.length, 1);
    assert.equal(infoMessages.length, 2);
    assert.match(infoMessages[0], /\[message_insert\] injection\.started/);
    assert.match(infoMessages[1], /\[message_insert\] injection\.completed injected=true/);
    assert.deepEqual(errorMessages, []);
  });
});

test("failed injection preserves the error log and rejection", async () => {
  await withMessageInsertRuntime(async ({ main, shared, infoMessages, errorMessages }) => {
    shared.loadSettings = () => ({ persistInjectedContent: true });
    shared.appendExtraInfoToMessage = async () => {
      throw new Error("injection exploded");
    };

    await assert.rejects(main.onPromptInput(hookEvent("before_process")), /injection exploded/);

    assert.equal(infoMessages.length, 1);
    assert.match(infoMessages[0], /\[message_insert\] injection\.started/);
    assert.equal(errorMessages.length, 1);
    assert.match(errorMessages[0], /\[message_insert\] injection\.failed/);
    assert.match(errorMessages[0], /error=injection exploded/);
  });
});

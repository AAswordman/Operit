#!/usr/bin/env node
"use strict";

// Regression harness for ToolPkg prompt hooks (issue #1258).
//
// Runs the REAL generated JavaScript that the Android host injects into QuickJS:
// the ToolPkg registration bridge, the API runtime and the execution runtime are
// extracted verbatim from their Kotlin string templates. A minimal *external*
// ToolPkg is then loaded through `registerToolPkg()` and its registered prompt
// hooks are dispatched through `__operitExecuteScriptFunction` exactly the way
// `runToolPkgMainHook` drives them for a normal chat request.
//
// This covers, without an Android device:
//   * external package load   -> prompt hooks are captured with a resolvable ref
//   * normal chat dispatch    -> each supported prompt hook fires and mutates
//
// The host's `NativeInterface` is stubbed to mirror OperitQuickJsEngine.dispatch
// NativeCall: every method call is routed through `__call(method, JSON-args)`.

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPO_ROOT = path.resolve(__dirname, "..", "..");
const JS_DIR = path.join(
  REPO_ROOT,
  "app/src/main/java/com/ai/assistance/operit/core/tools/javascript"
);
const QUICKJS_DIR = path.join(
  REPO_ROOT,
  "quickjs/src/main/java/com/ai/assistance/operit/core/tools/javascript"
);

// ---------------------------------------------------------------------------
// Kotlin raw-string extraction
// ---------------------------------------------------------------------------

function trimIndent(raw) {
  const lines = raw.replace(/^\r?\n/, "").replace(/\r?\n\s*$/, "").split(/\r?\n/);
  let minIndent = Infinity;
  for (const line of lines) {
    if (line.trim().length === 0) continue;
    const match = /^[ \t]*/.exec(line)[0];
    minIndent = Math.min(minIndent, match.length);
  }
  if (!Number.isFinite(minIndent)) minIndent = 0;
  return lines.map((line) => line.slice(minIndent)).join("\n");
}

function readKotlin(dir, fileName) {
  return fs.readFileSync(path.join(dir, fileName), "utf8");
}

function extractTrimIndentBlock(source, startMarker) {
  const startIndex = source.indexOf(startMarker);
  if (startIndex < 0) throw new Error(`marker not found: ${startMarker}`);
  const openIndex = source.indexOf('"""', startIndex);
  const closeIndex = source.indexOf('"""', openIndex + 3);
  if (openIndex < 0 || closeIndex < 0) throw new Error(`unterminated block after ${startMarker}`);
  return trimIndent(source.slice(openIndex + 3, closeIndex));
}

function buildExecutionPrelude() {
  return extractTrimIndentBlock(
    readKotlin(JS_DIR, "JsExecutionScriptBuilder.kt"),
    "private fun buildExecutionPreludeSource()"
  );
}

function buildExecutionRuntimeBridge() {
  const source = readKotlin(JS_DIR, "JsExecutionScriptBuilder.kt");
  let script = extractTrimIndentBlock(source, "internal fun buildExecutionRuntimeBridgeScript()");
  script = script.replace(/\$preludeSource/g, JSON.stringify(buildExecutionPrelude()));
  script = script.replace(/\$TOOLPKG_EXECUTION_ENTRY_FUNCTION/g, "__operitExecuteScriptFunction");
  return script;
}

function buildRegistrationBridge() {
  return extractTrimIndentBlock(
    readKotlin(JS_DIR, "JsToolPkgRegistration.kt"),
    "internal fun buildToolPkgRegistrationBridgeScript()"
  );
}

function buildToolPkgApiRuntime() {
  return extractTrimIndentBlock(
    readKotlin(JS_DIR, "JsToolPkgApiRuntime.kt"),
    "internal fun buildToolPkgApiRuntimeScript()"
  );
}

function buildInitRuntime() {
  const source = readKotlin(JS_DIR, "JsInitRuntimeScriptBuilder.kt");
  const expose = extractTrimIndentBlock(source, "private fun buildRuntimeExposeScript()");
  const constants = extractTrimIndentBlock(source, "private fun buildRuntimeConstantsScript(")
    .replace(/\$\{JSONObject\.quote\(operitDownloadDir\)\}/g, JSON.stringify("/tmp/download"))
    .replace(/\$\{JSONObject\.quote\(operitCleanOnExitDir\)\}/g, JSON.stringify("/tmp/clean"));
  const callRegistry = extractTrimIndentBlock(source, "private fun buildRuntimeCallRegistryScript()");
  return { expose, constants, callRegistry };
}

function buildCompatScript() {
  return extractTrimIndentBlock(
    readKotlin(QUICKJS_DIR, "QuickJsNativeCompatScriptBuilder.kt"),
    "internal fun buildQuickJsCompatScript()"
  );
}

// ---------------------------------------------------------------------------
// Host stub mirroring OperitQuickJsEngine.dispatchNativeCall
// ---------------------------------------------------------------------------

function createHost({ packageFiles, captured, results, errors, logs, env }) {
  return {
    setCallResult(callId, resultText) { results.set(callId, resultText); return null; },
    setCallError(callId, errorJson) { errors.set(callId, errorJson); return null; },
    sendCallIntermediateResult() { return null; },
    scheduleTimer() { return null; },
    cancelTimer() { return null; },
    getEnvForCall(_callId, key) {
      const value = env[String(key)];
      return value == null ? "" : String(value);
    },
    getPluginConfigDir(id) { return `/tmp/toolpkg-config/${id}`; },
    logInfoForCall(callId, message) { logs.push({ callId, level: "info", message }); return null; },
    logWarnForCall(callId, message) { logs.push({ callId, level: "warn", message }); return null; },
    logErrorForCall(callId, message) { logs.push({ callId, level: "error", message }); return null; },
    readToolPkgResource() { return ""; },
    readToolPkgTextResource(packageName, resourcePath) {
      const text = packageFiles[`${packageName}:${resourcePath}`];
      return typeof text === "string" ? text : "";
    },
    captureToolPkgMarketOrigin() { return null; },
    registerToolPkgSystemPromptComposeHook(spec) {
      captured.push({ native: "registerToolPkgSystemPromptComposeHook", spec }); return null;
    },
    registerToolPkgToolPromptComposeHook(spec) {
      captured.push({ native: "registerToolPkgToolPromptComposeHook", spec }); return null;
    },
    registerToolPkgPromptFinalizeHook(spec) {
      captured.push({ native: "registerToolPkgPromptFinalizeHook", spec }); return null;
    },
    registerToolPkgPromptHistoryHook(spec) {
      captured.push({ native: "registerToolPkgPromptHistoryHook", spec }); return null;
    },
    registerToolPkgPromptInputHook(spec) {
      captured.push({ native: "registerToolPkgPromptInputHook", spec }); return null;
    },
  };
}

function createNativeProxy(host) {
  return new Proxy(
    {},
    {
      get(_target, property) {
        if (property === "__call") {
          return (method, argsJson) => {
            const args = argsJson == null ? [] : JSON.parse(argsJson);
            const fn = host[method];
            if (typeof fn !== "function") {
              throw new Error(`NativeInterface method not found: ${method}/${args.length}`);
            }
            const value = fn.apply(host, args);
            return value == null ? null : String(value);
          };
        }
        return (...args) => {
          const fn = host[property];
          if (typeof fn !== "function") {
            throw new Error(`NativeInterface method not found: ${String(property)}/${args.length}`);
          }
          const value = fn.apply(host, args);
          return value == null ? null : String(value);
        };
      },
    }
  );
}

function createEngine({ packageFiles = {}, env = {} } = {}) {
  const captured = [];
  const results = new Map();
  const errors = new Map();
  const logs = [];
  const host = createHost({ packageFiles, captured, results, errors, logs, env });

  const sandbox = {};
  sandbox.globalThis = sandbox;
  sandbox.console = {
    log: (...a) => logs.push({ level: "console", message: a.map(String).join(" ") }),
    info: (...a) => logs.push({ level: "console", message: a.map(String).join(" ") }),
    warn: (...a) => logs.push({ level: "console", message: a.map(String).join(" ") }),
    error: (...a) => logs.push({ level: "console", message: a.map(String).join(" ") }),
  };
  sandbox.NativeInterface = createNativeProxy(host);
  sandbox.setTimeout = () => 0;
  sandbox.clearTimeout = () => {};
  sandbox.setInterval = () => 0;
  sandbox.clearInterval = () => {};

  const context = vm.createContext(sandbox);
  const init = buildInitRuntime();
  for (const [name, source] of [
    ["compat.js", buildCompatScript()],
    ["expose.js", init.expose],
    ["constants.js", init.constants],
    ["call-registry.js", init.callRegistry],
    ["toolpkg-api-runtime.js", buildToolPkgApiRuntime()],
    ["execution-runtime.js", buildExecutionRuntimeBridge()],
    ["toolpkg-bridge.js", buildRegistrationBridge()],
  ]) {
    vm.runInContext(source, context, { filename: `bootstrap/${name}` });
  }

  let callCounter = 0;
  function executeScriptFunction({ script, functionName, params, apiVersion = "1.0.0" }) {
    callCounter += 1;
    const callId = `call_${callCounter}`;
    sandbox.__operitExecuteScriptFunction(
      callId,
      params,
      script,
      functionName,
      12,
      null,
      { apiVersion }
    );
    if (errors.has(callId)) return { ok: false, error: errors.get(callId) };
    return { ok: true, raw: results.get(callId) };
  }

  return { captured, logs, executeScriptFunction };
}

// ---------------------------------------------------------------------------
// Minimal external ToolPkg
// ---------------------------------------------------------------------------

const TOOLPKG_ID = "com.operit.issue1258.repro";
const MAIN_ENTRY = "dist/main.js";

// Mirrors the reporter's package: CommonJS exports + one register*Hook per family.
const MAIN_JS = `
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onInput = onInput;
exports.onHistory = onHistory;
exports.onSystemPromptCompose = onSystemPromptCompose;
exports.onToolPromptCompose = onToolPromptCompose;
exports.onFinalize = onFinalize;

function registerToolPkg() {
  ToolPkg.registerPromptInputHook({ id: "repro_input", function: onInput });
  ToolPkg.registerPromptHistoryHook({ id: "repro_history", function: onHistory });
  ToolPkg.registerSystemPromptComposeHook({ id: "repro_compose", function: onSystemPromptCompose });
  ToolPkg.registerToolPromptComposeHook({ id: "repro_tool_compose", function: onToolPromptCompose });
  ToolPkg.registerPromptFinalizeHook({ id: "repro_finalize", function: onFinalize });
  return true;
}

function onInput(input) {
  var payload = (input && input.eventPayload) || {};
  return { processedInput: "[input] " + (payload.processedInput || "") };
}

function onHistory(input) {
  var payload = (input && input.eventPayload) || {};
  return { preparedHistory: (payload.preparedHistory || []).concat([{ kind: "USER", content: "[history]", toolName: null, metadata: {} }]) };
}

function onSystemPromptCompose(input) {
  var payload = (input && input.eventPayload) || {};
  return { systemPrompt: (payload.systemPrompt || "") + "\\n[compose]" };
}

function onToolPromptCompose(input) {
  var payload = (input && input.eventPayload) || {};
  return { toolPrompt: (payload.toolPrompt || "") + "\\n[tool-compose]" };
}

function onFinalize(input) {
  var payload = (input && input.eventPayload) || {};
  return { processedInput: "[finalize] " + (payload.processedInput || "") };
}
`;

const PACKAGE_FILES = { [`${TOOLPKG_ID}:${MAIN_ENTRY}`]: MAIN_JS };

// Stage names mirror SystemPromptConfig / ConversationService / EnhancedAIService.
const DISPATCH_PLAN = [
  {
    native: "registerToolPkgPromptInputHook",
    stage: "after_process",
    payload: () => ({ stage: "after_process", processedInput: "hello" }),
    expect: (result) => result.includes("[input] hello"),
  },
  {
    native: "registerToolPkgPromptHistoryHook",
    stage: "after_prepare_history",
    payload: () => ({ stage: "after_prepare_history", preparedHistory: [] }),
    expect: (result) => result.includes("[history]"),
  },
  {
    native: "registerToolPkgSystemPromptComposeHook",
    stage: "after_compose_system_prompt",
    payload: () => ({ stage: "after_compose_system_prompt", systemPrompt: "BASE" }),
    expect: (result) => result.includes("[compose]") && result.includes("BASE"),
  },
  {
    native: "registerToolPkgToolPromptComposeHook",
    stage: "filter_tool_call_tools",
    payload: () => ({ stage: "filter_tool_call_tools", toolPrompt: "TOOLS" }),
    expect: (result) => result.includes("[tool-compose]") && result.includes("TOOLS"),
  },
  {
    native: "registerToolPkgPromptFinalizeHook",
    stage: "before_finalize_prompt",
    payload: () => ({ stage: "before_finalize_prompt", processedInput: "hello" }),
    expect: (result) => result.includes("[finalize] hello"),
  },
];

// ---------------------------------------------------------------------------
// Test body
// ---------------------------------------------------------------------------

let failures = 0;

function check(condition, label) {
  if (condition) {
    console.log(`  PASS  ${label}`);
  } else {
    failures += 1;
    console.log(`  FAIL  ${label}`);
  }
}

function registerExternalPkg() {
  const engine = createEngine({ packageFiles: PACKAGE_FILES });
  const result = engine.executeScriptFunction({
    script: MAIN_JS,
    functionName: "registerToolPkg",
    params: {
      toolPkgId: TOOLPKG_ID,
      __operit_ui_package_name: TOOLPKG_ID,
      __operit_plugin_id: `registerToolPkg:${TOOLPKG_ID}`,
      __operit_registration_mode: true,
      __operit_script_screen: MAIN_ENTRY,
    },
    apiVersion: "1.0.0",
  });
  return { engine, result };
}

function dispatchHook(registration, plan) {
  const spec = JSON.parse(registration.spec);
  const engine = createEngine({ packageFiles: PACKAGE_FILES });
  const params = {
    event: plan.stage,
    eventName: plan.stage,
    eventPayload: plan.payload(),
    timestampMs: Date.now(),
    functionName: spec.function,
    toolPkgId: TOOLPKG_ID,
    containerPackageName: TOOLPKG_ID,
    __operit_ui_package_name: TOOLPKG_ID,
    __operit_script_screen: MAIN_ENTRY,
    pluginId: spec.id,
  };
  if (spec.function_source) {
    params.__operit_inline_function_name = spec.function;
    params.__operit_inline_function_source = spec.function_source;
  }
  return engine.executeScriptFunction({
    script: MAIN_JS,
    functionName: spec.function,
    params,
    apiVersion: "1.0.0",
  });
}

function main() {
  console.log("== external ToolPkg: registration ==");
  const { engine: regEngine, result: regResult } = registerExternalPkg();
  check(regResult.ok, "registerToolPkg() completes without error");

  const promptRegs = regEngine.captured.filter((c) => /registerToolPkg.*Hook/.test(c.native));
  check(promptRegs.length === DISPATCH_PLAN.length, `all ${DISPATCH_PLAN.length} prompt hooks captured`);

  for (const plan of DISPATCH_PLAN) {
    const reg = promptRegs.find((c) => c.native === plan.native);
    if (!reg) {
      failures += 1;
      console.log(`  FAIL  ${plan.native} captured`);
      continue;
    }
    console.log(`  -- ${plan.native} --`);
    const spec = JSON.parse(reg.spec);
    check(Boolean(spec.function), `${plan.native} resolves a function reference (got '${spec.function}')`);
    check(Boolean(spec.id), `${plan.native} keeps its hook id`);

    const dispatch = dispatchHook(reg, plan);
    check(dispatch.ok, `${plan.native} dispatch succeeds`);
    if (dispatch.ok) {
      check(
        plan.expect(String(dispatch.raw)),
        `${plan.native} mutation applied at '${plan.stage}' (result: ${String(dispatch.raw)})`
      );
    } else {
      console.log(`       error: ${String(dispatch.error).split("\n")[0]}`);
    }
  }

  // A hook function that is NOT exported must fail loudly at registration, never
  // silently drop (this is the "registration failure must give a clear diagnostic"
  // half of issue #1258).
  console.log("== external ToolPkg: unexported hook function must fail loudly ==");
  const badEngine = createEngine({
    packageFiles: {
      [`${TOOLPKG_ID}:bad.js`]: `
        exports.registerToolPkg = function () {
          function hidden() { return null; }
          ToolPkg.registerPromptFinalizeHook({ id: "bad", function: hidden });
          return true;
        };
      `,
    },
  });
  const badResult = badEngine.executeScriptFunction({
    script: `
      exports.registerToolPkg = function () {
        function hidden() { return null; }
        ToolPkg.registerPromptFinalizeHook({ id: "bad", function: hidden });
        return true;
      };
    `,
    functionName: "registerToolPkg",
    params: {
      toolPkgId: TOOLPKG_ID,
      __operit_ui_package_name: TOOLPKG_ID,
      __operit_plugin_id: `registerToolPkg:${TOOLPKG_ID}`,
      __operit_registration_mode: true,
      __operit_script_screen: "bad.js",
    },
    apiVersion: "1.0.0",
  });
  check(!badResult.ok, "unexported hook function throws (no silent skip)");
  check(
    !badResult.ok && String(badResult.error).includes("must be exported"),
    "error names the export requirement"
  );

  console.log("");
  console.log(failures === 0 ? "ALL PROMPT-HOOK CHECKS PASSED" : `${failures} CHECK(S) FAILED`);
  process.exit(failures === 0 ? 0 : 1);
}

main();

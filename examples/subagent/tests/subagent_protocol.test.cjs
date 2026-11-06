const { test } = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const protocol = require("../dist/shared/subagent_protocol.js");
const renderer = require("../dist/plugin/subagent-xml-render-plugin.js");

function tool(id = "tool-1", success = true, executed = true) {
  return {
    id, toolName: "read_file", arguments: [{ name: "path", value: "/task/input.txt" }],
    success, result: success ? "actual file content" : "", error: success ? null : "Permission denied",
    executed, resultTruncated: false,
  };
}

function outcome(tools = [], declaration = { status: "completed", summary: "2", evidence_ids: [] }) {
  return {
    reason: "model_finished", error: null, toolSessionScopeId: "scope-1",
    finalText: JSON.stringify(declaration), executedToolCount: tools.filter(item => item.executed).length, tools,
  };
}

test("rejects empty tasks and invalid budgets without rounding or accepting unbounded values", () => {
  for (const task of ["", " \n\t "]) assert.throws(() => protocol.validateTask({ task }), { reason: "invalid_task" });
  assert.equal(protocol.validateTask({ task: "  calculate  " }).maxToolCalls, 8);
  for (const max_tool_calls of [1, 64]) assert.equal(protocol.validateTask({ task: "work", max_tool_calls }).maxToolCalls, max_tool_calls);
  for (const max_tool_calls of [0, -1, 0.5, 1.5, 65, 999999, NaN, Infinity, null, "8"]) {
    assert.throws(() => protocol.validateTask({ task: "work", max_tool_calls }), { reason: "invalid_budget" });
  }
});

test("only accepts JSON arrays of nonempty path strings", () => {
  for (const target_paths_json of ["", "{}", "null", '["valid",2]', '["  "]']) {
    assert.throws(() => protocol.validateTask({ task: "work", target_paths_json }), { reason: "invalid_target_paths" });
  }
  assert.deepEqual(protocol.validateTask({ task: "work", target_paths_json: '[" /a ","/b"]' }).targetPaths, ["/a", "/b"]);
});

test("requires a complete explicit final declaration and preserves literal tags in JSON strings", () => {
  const declaration = { status: "completed", summary: 'literal <think>x</think>, <tool_A name="x">y</tool_A> & </final>', evidence_ids: [] };
  const json = JSON.stringify(declaration);
  assert.deepEqual(protocol.parseModelDeclaration(`<think>private</think><search>internal</search>\n${json}`), declaration);
  assert.deepEqual(protocol.parseModelDeclaration(`\n\`\`\`json\n${json}\n\`\`\``), declaration);
  for (const raw of ["a useful summary", `${json}\nextra`, `${json}\n${json}`, `<tool_X>internal ${json}`, `<think>secret z ${json}`, `<tool_X name="x"`, `${json}<think>partial`, `\`\`\`json\n${json}`]) {
    assert.throws(() => protocol.parseModelDeclaration(raw));
  }
});

test("completed tool work must cite real successful evidence and cannot hide failures", () => {
  const success = tool();
  assert.equal(protocol.decideOutcome(outcome([success], { status: "completed", summary: "read", evidence_ids: [success.id] })).status, "completed");
  assert.throws(() => protocol.decideOutcome(outcome([success])), { reason: "insufficient_evidence" });
  assert.throws(() => protocol.decideOutcome(outcome([success], { status: "completed", summary: "read", evidence_ids: ["another-run"] })), { reason: "invalid_evidence" });
  const blocked = protocol.decideOutcome(outcome([success, tool("tool-2", false)], { status: "completed", summary: "I finished", evidence_ids: [success.id] }));
  assert.equal(blocked.status, "blocked");
  assert.equal(blocked.reason, "tool_failure");
  assert.equal(protocol.decideOutcome(outcome()).status, "completed");
});

test("host limits, permission and execution errors override any model claim", () => {
  for (const reason of ["tool_budget_exhausted", "delegation_denied", "permission_denied", "token_limit", "cancelled", "execution_error"]) {
    const result = protocol.decideOutcome({ ...outcome(), reason, error: "host evidence" });
    assert.equal(result.reason, reason);
    assert.equal(result.status, reason === "execution_error" ? "failed" : "blocked");
  }
  assert.throws(() => protocol.parseHostOutcome(JSON.stringify({ ...outcome([tool()]), executedToolCount: 0 })), { reason: "invalid_host_outcome" });
  assert.throws(() => protocol.parseHostOutcome(JSON.stringify(outcome([tool(), tool()]))), { reason: "invalid_host_outcome" });
});

test("XML remains complete with large evidence, escaped summaries and explicit blocked state", () => {
  const tools = Array.from({ length: 64 }, (_, index) => ({ ...tool(`tool-${index}`), result: "x".repeat(32000), resultTruncated: true }));
  const decision = { status: "blocked", reason: "tool_failure", summary: "<&>😀".repeat(3000), evidenceIds: ["tool-1"] };
  const xml = protocol.finalXml("run-1", decision, tools, "scope-1", "/evidence/run-1.json", 64);
  assert.ok(xml.length < 16000);
  assert.ok(xml.endsWith("</evidence></subagent>"));
  assert.match(xml, /summary_truncated="true"/);
  assert.doesNotMatch(xml, /x{100}/);
  assert.equal(renderer.parseSubagentProtocol(xml).status, "blocked");
  const failure = protocol.toolReturn(xml, decision);
  assert.equal(failure.success, false);
  assert.equal(failure.data, xml);
});

test("new renderer preserves summary text and supports old final messages", () => {
  const summary = '<final success="false">literal</final> & <subagent>also literal</subagent>';
  const decision = { status: "completed", reason: "completed", summary, evidenceIds: [] };
  assert.equal(renderer.parseSubagentProtocol(protocol.finalXml("run", decision, [], "scope", null, 0)).text, summary);
  assert.equal(renderer.parseSubagentProtocol('<subagent><final run="old" success="true" tool_count="0">old result</final></subagent>').status, "completed");
  assert.equal(renderer.parseSubagentProtocol('<subagent><final run="old" success="false" tool_count="0">old error</final></subagent>').status, "failed");
  assert.equal(renderer.parseSubagentProtocol('<subagent><final status="failed" success="true">inconsistent</final></subagent>'), null);
});

test("failure wrappers render the blocked summary once and cannot paint an outer failure green", () => {
  global.getLang = () => "en";
  const decision = { status: "blocked", reason: "tool_failure", summary: "the actual blocker", evidenceIds: [] };
  const xml = protocol.finalXml("run", decision, [], "scope", null, 0);
  const event = {
    event: "xml_render", eventName: "xml_render",
    eventPayload: {
      tagName: "tool_result",
      xmlContent: `<tool_result_X name="subagent:subagent_run" status="error"><content><error>Subagent blocked: tool_failure\n\n${xml}</error></content></tool_result_X>`,
    },
  };
  const result = renderer.onToolResultXmlRender(event);
  assert.equal(result.composeDsl.state.tone, "blocked");
  assert.equal(result.composeDsl.state.summary, "the actual blocker");
  event.eventPayload.xmlContent = '<tool_result_X name="subagent:subagent_run" status="error"><content><subagent><final success="true">old completed claim</final></subagent></content></tool_result_X>';
  assert.equal(renderer.onToolResultXmlRender(event).composeDsl.state.tone, "failure");
});

let uuidSequence = 0;
function installRuntime(hostOutcome, options = {}) {
  const state = { modelCalls: 0, contextCaptures: 0, intermediate: [], writes: new Map(), sendOptions: null };
  const unit = {};
  const service = {
    async callSuspend(method, value) {
      if (method === "getModelConfigForFunction") {
        state.modelCalls += 1;
        if (options.beforeConfigReturn) await options.beforeConfigReturn();
        return { call: getter => getter === "getContextLength" ? 48 : 0.7 };
      }
      assert.equal(method, "sendMessage");
      state.modelCalls += 1;
      state.sendOptions = value;
      return {
        async callSuspend(methodName, collector) {
          assert.equal(methodName, "collect");
          for (const record of hostOutcome.tools) value.callbacks.onSubtaskToolResult(JSON.stringify(record));
          if (options.nonFatalError) value.callbacks.onNonFatalError(options.nonFatalError);
          collector.emit("This old streamed text is not a completion declaration");
          if (!options.missingOutcome) value.callbacks.onSubtaskCompleted(JSON.stringify(hostOutcome));
          if (options.collectError) throw new Error("collect rethrew after host completion");
        },
      };
    },
  };
  const java = {
    type(name) {
      if (name.endsWith("EnhancedAIService")) return { getInstance: () => service };
      if (name.endsWith("$SendMessageOptions")) return class SendMessageOptions {};
      if (name === "java.util.UUID") return { callStatic: () => ({ toString: () => `uuid-${++uuidSequence}` }) };
      if (name.endsWith("SystemPromptConfig")) return { getStatic: () => "base prompt" };
      return { getStatic: () => unit };
    },
    getApplicationContext: () => ({}),
  };
  const context = {
    callId: "call-for-test", java,
    chatId: options.chatId ?? "parent-chat", callerCardId: "parent-card", language: options.language ?? "en",
    emitIntermediate: value => state.intermediate.push(value),
  };
  global.getToolExecutionContext = () => { state.contextCaptures += 1; return context; };
  global.Java = { type() { throw new Error("The unbound global Java facade must not be used"); } };
  global.Tools = { Files: {
    async mkdir() { return { successful: true, details: "created" }; },
    async write(file, content) {
      if (options.writeError) return { successful: false, details: "storage failure" };
      state.writes.set(file, content);
      return { successful: true, details: "written" };
    },
  } };
  global.getChatId = () => "parent-chat";
  global.getCallerCardId = () => "parent-card";
  global.getLang = () => "en";
  global.sendIntermediateResult = value => state.intermediate.push(value);
  global.OPERIT_CLEAN_ON_EXIT_DIR = "/evidence";
  const packagePath = path.resolve(__dirname, "../dist/packages/subagent.js");
  delete require.cache[packagePath];
  return { state, run: require(packagePath).subagent_run };
}

test("entry rejects invalid input before model calls or progress and keeps external failure status", async () => {
  const { state, run } = installRuntime(outcome());
  const result = await run({ task: " \n " });
  assert.equal(result.success, false);
  assert.match(result.data, /reason="invalid_task"/);
  assert.equal(state.modelCalls, 0);
  assert.deepEqual(state.intermediate, []);
});

test("entry passes identity and budget and archives authentic evidence rather than streamed text", async () => {
  const record = tool();
  const host = outcome([record], { status: "completed", summary: "verified", evidence_ids: [record.id] });
  const { state, run } = installRuntime(host, { nonFatalError: "a recovered model attempt failed" });
  const result = await run({ task: "read the file", max_tool_calls: 3 });
  assert.equal(typeof result, "string");
  assert.equal(state.sendOptions.chatId, "parent-chat");
  assert.equal(state.sendOptions.roleCardId, "parent-card");
  assert.equal(state.sendOptions.subTask, true);
  assert.equal(state.sendOptions.subtaskMaxToolCalls, 3);
  assert.equal(state.contextCaptures, 1);
  assert.equal(state.writes.size, 1);
  const archive = JSON.parse([...state.writes.values()][0]);
  assert.deepEqual(archive.outcome.tools, [record]);
  assert.equal(renderer.parseSubagentProtocol(result).text, "verified");
  assert.ok(state.intermediate.some(xml => /stage="tool" count="1"/.test(xml)));
});

test("write failure is explicit and never becomes evidence-free completed", async () => {
  const record = tool();
  const { run } = installRuntime(outcome([record], { status: "completed", summary: "read", evidence_ids: [record.id] }), { writeError: true });
  const result = await run({ task: "read" });
  assert.equal(result.success, false);
  assert.match(result.data, /reason="evidence_write_failed"/);
  assert.doesNotMatch(result.data, /evidence_path="/);
});

test("missing completion cannot turn an old streamed summary into success", async () => {
  const { run } = installRuntime(outcome(), { missingOutcome: true });
  const result = await run({ task: "calculate" });
  assert.equal(result.success, false);
  assert.match(result.data, /reason="missing_host_outcome"/);
});

test("collect rethrow preserves the host cancellation or execution error reason", async () => {
  for (const reason of ["cancelled", "execution_error"]) {
    const { run } = installRuntime({ ...outcome(), reason, error: "actual terminal reason" }, { collectError: true });
    const result = await run({ task: "work" });
    assert.equal(result.success, false);
    assert.match(result.data, new RegExp(`reason="${reason}"`));
  }
});

test("interleaved entries keep the identity, language and Java facade captured before await", async () => {
  let releaseFirst;
  const firstGate = new Promise(resolve => { releaseFirst = resolve; });
  const first = installRuntime(outcome(), { chatId: "first-chat", language: "en", beforeConfigReturn: () => firstGate });
  const firstResultPromise = first.run({ task: "first calculation" });
  const second = installRuntime(outcome(), { chatId: "second-chat", language: "zh" });
  const secondResult = await second.run({ task: "second calculation" });
  releaseFirst();
  const firstResult = await firstResultPromise;
  assert.equal(typeof firstResult, "string");
  assert.equal(typeof secondResult, "string");
  assert.equal(first.state.sendOptions.chatId, "first-chat");
  assert.equal(second.state.sendOptions.chatId, "second-chat");
  assert.equal(first.state.contextCaptures, 1);
  assert.equal(second.state.contextCaptures, 1);
  assert.ok(first.state.intermediate.every(xml => !xml.includes("正在")));
  assert.ok(second.state.intermediate.some(xml => xml.includes("正在")));
  assert.notEqual(renderer.parseSubagentProtocol(firstResult).runId, renderer.parseSubagentProtocol(secondResult).runId);
});

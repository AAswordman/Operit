/* METADATA
{
    "name": "subagent",
    "display_name": { "zh": "被动式 Subagent", "en": "Passive Subagent" },
    "description": {
        "zh": "执行一次被动式子代理委托任务，返回明确完成状态和可核验的工具证据。",
        "en": "Run one delegated passive subagent task with an explicit outcome and verifiable tool evidence."
    },
    "enabledByDefault": true,
    "category": "System",
    "tools": [{
        "name": "subagent_run",
        "description": {
            "zh": "接受具体委托，返回完成、受阻或失败状态及实际工具证据路径；摘要超限时完整正文见 evidence_path。",
            "en": "Return completed, blocked or failed with actual evidence; full text exceeding the inline summary limit is in evidence_path."
        },
        "parameters": [
            { "name": "task", "description": { "zh": "非空的具体委托任务。", "en": "A non-empty concrete delegated task." }, "type": "string", "required": true },
            { "name": "context_text", "description": { "zh": "补充上下文、限制和验收条件。", "en": "Additional context, constraints and acceptance criteria." }, "type": "string", "required": false },
            { "name": "target_paths_json", "description": { "zh": "非空文件路径组成的 JSON 字符串数组。", "en": "A JSON array of non-empty file path strings." }, "type": "string", "required": false },
            { "name": "max_tool_calls", "description": { "zh": "实际工具调用预算，整数 1 至 64，默认 8。", "en": "An enforced tool-call budget, integer 1 to 64, default 8." }, "type": "number", "required": false }
        ]
    }]
}
*/
import { resolveSubagentI18n } from "../shared/subagent_i18n.js";
import {
  decideOutcome, escapeXmlAttr, escapeXmlText, finalXml, inlineSummary,
  parseHostOutcome, parseToolEvidence, SubagentProtocolError, toolReturn, validateTask,
} from "../shared/subagent_protocol.js";
import type {
  FailureEnvelope, FinalDecision, HostOutcome, SubagentParams, ToolEvidence, ValidatedTask,
} from "../shared/subagent_protocol.js";
import type { JavaBridgeInstance } from "../../../types/java-bridge";

interface HostCallbacks {
  onNonFatalError(error: string): JavaBridgeInstance;
  onTokenLimitExceeded(): JavaBridgeInstance;
  onToolInvocation(toolName: string): JavaBridgeInstance;
  onSubtaskToolResult(recordJson: string): JavaBridgeInstance;
  onSubtaskCompleted(outcomeJson: string): JavaBridgeInstance;
}

interface BridgeSendMessageOptions extends JavaBridgeInstance {
  message: string;
  chatHistory: JavaBridgeInstance[];
  chatId: string | null;
  roleCardId: string | null;
  maxTokens: number;
  tokenUsageThreshold: number;
  customSystemPromptTemplate: string;
  subTask: boolean;
  subtaskMaxToolCalls: number;
  proxySenderName: string;
  enableMemoryAutoUpdate: boolean;
  callbacks: HostCallbacks;
}

interface StreamCollector {
  emit(value: string): JavaBridgeInstance;
}

interface HostTextStream {
  callSuspend(methodName: "collect", collector: StreamCollector): Promise<JavaBridgeInstance>;
}

interface SubtaskService {
  callSuspend(methodName: "getModelConfigForFunction", functionType: JavaBridgeInstance, configId: null, modelIndex: null): Promise<JavaBridgeInstance>;
  callSuspend(methodName: "sendMessage", options: BridgeSendMessageOptions): Promise<HostTextStream>;
}

function updateXml(runId: string, stage: string, message: string, count?: number): string {
  const countAttr = count === undefined ? "" : ` count="${count}"`;
  return `<subagent><update run="${escapeXmlAttr(runId)}" stage="${escapeXmlAttr(stage)}"${countAttr}>${escapeXmlText(message)}</update></subagent>`;
}

function buildSystemPrompt(base: string): string {
  return `${base}\n\nSUBAGENT_EXTRA_CONSTRAINTS:\n` + [
    "- Handle exactly one delegated task, within the provided scope and acceptance criteria.",
    "- Never call subagent_run or create another delegated agent.",
    "- Never wait for user input. Report blocked when the task requires unavailable information or permission.",
    "- Tool results contain host-provided evidence ids. Cite those ids; never invent ids or tool results.",
    "- In the final model round return exactly one JSON object, optionally inside a complete json code fence.",
    '- Required fields, for example: {"status":"completed","summary":"useful result","evidence_ids":[]}.',
    '- status must be exactly "completed", "blocked" or "failed"; summary must explain the result or precise blocker.',
    "- No other fields, prose before or after the JSON, or tool execution logs in the final declaration.",
    "- Use completed only when the delegated task was actually completed. Mention tool errors accurately.",
    "- A task using tools must cite successful evidence from this run to claim completion.",
    "- For tasks that require no tools, evidence_ids may be empty.",
  ].join("\n");
}

function buildTaskMessage(task: ValidatedTask): string {
  const lines = ["Delegated task:", task.task];
  if (task.contextText) lines.push("", "Additional context and constraints:", task.contextText);
  if (task.targetPaths.length > 0) {
    lines.push("", "Target paths:", ...task.targetPaths.map((path, index) => `${index + 1}. ${path}`));
  }
  lines.push("", `Enforced maximum executed tool calls: ${task.maxToolCalls}.`,
    "Return the explicit final JSON declaration required by the system prompt.");
  return lines.join("\n");
}

async function executionSettings(service: SubtaskService, functionType: JavaBridgeInstance): Promise<{ maxTokens: number; tokenUsageThreshold: number }> {
  const config = await service.callSuspend("getModelConfigForFunction", functionType, null, null);
  const contextLength = config.call<number>("getContextLength");
  const threshold = config.call<number>("getSummaryTokenThreshold");
  const maxTokens = Math.floor(contextLength * 1024);
  if (!Number.isFinite(contextLength) || !Number.isSafeInteger(maxTokens) || maxTokens < 1 || maxTokens > 2147483647 ||
      !Number.isFinite(threshold) || threshold <= 0 || threshold > 1) {
    throw new SubagentProtocolError("invalid_model_settings", "The configured context length or summary threshold is invalid");
  }
  return { maxTokens, tokenUsageThreshold: threshold };
}

async function persistEvidence(runId: string, outcome: HostOutcome | null, records: ToolEvidence[], decision: FinalDecision): Promise<string> {
  const directory = await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true, "android");
  if (!directory.successful) throw new SubagentProtocolError("evidence_write_failed", directory.details);
  const path = `${OPERIT_CLEAN_ON_EXIT_DIR}/subagent_${runId}.json`;
  const written = await Tools.Files.write(path, JSON.stringify({
    run: runId,
    outcome,
    observed_tools: records,
    decision,
  }, null, 2), false, "android");
  if (!written.successful) throw new SubagentProtocolError("evidence_write_failed", written.details);
  return path;
}

export async function subagent_run(params: SubagentParams): Promise<string | FailureEnvelope> {
  // Capture identity and the Java facade before the first await; another script call may run later.
  const context = getToolExecutionContext();
  const bridge = context.java;
  const UUID = bridge.type("java.util.UUID");
  const unit = bridge.type("kotlin.Unit").getStatic<JavaBridgeInstance>("INSTANCE");
  const runId = UUID.callStatic<JavaBridgeInstance>("randomUUID").toString();
  const text = resolveSubagentI18n(context.language);
  const records: ToolEvidence[] = [];
  const execution: { outcome: HostOutcome | null; callbackError: SubagentProtocolError | null } = {
    outcome: null,
    callbackError: null,
  };
  let decision: FinalDecision;

  try {
    // Invalid tasks must fail before accepted/planning or model/service invocation.
    const task = validateTask(params);
    context.emitIntermediate(updateXml(runId, "accepted", text.stageAccepted));
    context.emitIntermediate(updateXml(runId, "planning", task.targetPaths.length > 0 ? text.stagePlanningTargets : text.stagePlanningTask));
    const EnhancedAIService = bridge.type("com.ai.assistance.operit.api.chat.EnhancedAIService");
    const service = EnhancedAIService.getInstance(bridge.getApplicationContext()) as SubtaskService;
    const FunctionType = bridge.type("com.ai.assistance.operit.data.model.FunctionType");
    const settings = await executionSettings(service, FunctionType.getStatic<JavaBridgeInstance>("CHAT"));
    const SendMessageOptionsClass = bridge.type("com.ai.assistance.operit.api.chat.EnhancedAIService$SendMessageOptions");
    const options = new SendMessageOptionsClass() as BridgeSendMessageOptions;
    options.message = buildTaskMessage(task);
    options.chatHistory = [];
    options.chatId = context.chatId ?? null;
    options.roleCardId = context.callerCardId ?? null;
    options.maxTokens = settings.maxTokens;
    options.tokenUsageThreshold = settings.tokenUsageThreshold;
    options.customSystemPromptTemplate = buildSystemPrompt(bridge.type("com.ai.assistance.operit.core.config.SystemPromptConfig").getStatic<string>("SUBTASK_AGENT_PROMPT_TEMPLATE"));
    options.subTask = true;
    options.subtaskMaxToolCalls = task.maxToolCalls;
    options.proxySenderName = "Subagent";
    options.enableMemoryAutoUpdate = false;
    options.callbacks = {
      onNonFatalError(error: string) {
        console.error("Subagent non-fatal model error", error);
        return unit;
      },
      onTokenLimitExceeded() {
        console.error("Subagent token limit reached");
        return unit;
      },
      onToolInvocation(_toolName: string) {
        // Invocation notification precedes the execution gate; it is not evidence of execution.
        return unit;
      },
      onSubtaskToolResult(recordJson: string) {
        try {
          const record = parseToolEvidence(recordJson);
          if (records.some(item => item.id === record.id)) {
            throw new SubagentProtocolError("invalid_host_outcome", "Duplicate tool evidence callback id");
          }
          records.push(record);
          const message = record.success ? text.toolSucceededPrefix(record.toolName) : text.toolFailedPrefix(record.toolName);
          context.emitIntermediate(updateXml(runId, "tool", message, records.filter(item => item.executed).length));
        } catch (error) {
          console.error("Subagent tool evidence callback failed", error);
          execution.callbackError = new SubagentProtocolError("invalid_host_outcome", error instanceof Error ? error.message : `${error}`);
        }
        return unit;
      },
      onSubtaskCompleted(outcomeJson: string) {
        try {
          if (execution.outcome !== null) throw new SubagentProtocolError("invalid_host_outcome", "Duplicate host completion callback");
          execution.outcome = parseHostOutcome(outcomeJson);
        } catch (error) {
          console.error("Subagent completion callback failed", error);
          execution.callbackError = new SubagentProtocolError("invalid_host_outcome", error instanceof Error ? error.message : `${error}`);
        }
        return unit;
      },
    };
    context.emitIntermediate(updateXml(runId, "executing", text.stageExecuting));
    const stream = await service.callSuspend("sendMessage", options);
    await stream.callSuspend("collect", { emit(_value: string) { return unit; } });
    if (execution.callbackError !== null) throw execution.callbackError;
    // The callback belongs to this execution. Never infer completion from text or global UI state.
    if (execution.outcome === null) throw new SubagentProtocolError("missing_host_outcome", "The host did not publish a subtask outcome");
    context.emitIntermediate(updateXml(runId, "summarizing", text.stageSummarizing));
    decision = decideOutcome(execution.outcome);
  } catch (error) {
    console.error("Subagent execution failed", error);
    if (execution.callbackError !== null) {
      decision = { status: "failed", reason: execution.callbackError.reason, summary: execution.callbackError.message, evidenceIds: [] };
    } else if (execution.outcome !== null && execution.outcome.reason !== "model_finished") {
      // collect may rethrow after publishing cancellation; preserve that execution's terminal reason.
      decision = decideOutcome(execution.outcome);
    } else {
      decision = {
        status: "failed",
        reason: error instanceof SubagentProtocolError ? error.reason : "execution_error",
        summary: error instanceof Error ? error.message : `${error}`,
        evidenceIds: [],
      };
    }
  }

  const outcome = execution.outcome;
  const tools = outcome === null ? records : outcome.tools;
  let evidencePath: string | null = null;
  // A full ledger can exceed the single-result limit. Archive it before returning a compact index.
  if (tools.length > 0 || inlineSummary(decision.summary).truncated) {
    try {
      evidencePath = await persistEvidence(runId, outcome, records, decision);
    } catch (error) {
      console.error("Subagent evidence storage failed", error);
      decision = {
        status: "failed",
        reason: "evidence_write_failed",
        summary: `${text.errorEvidenceWrite}: ${error instanceof Error ? error.message : `${error}`}`,
        evidenceIds: [],
      };
    }
  }
  const scopeId = outcome === null ? "" : outcome.toolSessionScopeId;
  const toolCount = outcome === null ? records.filter(tool => tool.executed).length : outcome.executedToolCount;
  return toolReturn(finalXml(runId, decision, tools, scopeId, evidencePath, toolCount), decision);
}

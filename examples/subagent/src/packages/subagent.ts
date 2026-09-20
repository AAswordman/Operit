/* METADATA
{
    "name": "subagent",
    "display_name": {
        "zh": "被动式 Subagent",
        "en": "Passive Subagent"
    },
    "description": {
        "zh": "执行一次被动式子代理委托任务，使用轻量 XML 文本协议返回启动、进度和最终总结。",
        "en": "Run one delegated passive subagent task and return start, progress, and final summary through a lightweight XML text protocol."
    },
    "enabledByDefault": true,
    "category": "System",
    "tools": [
        {
            "name": "subagent_run",
            "description": {
                "zh": "接受主 agent 的具体委托任务，内部以子任务模式调用 AI，并以轻量 XML 协议返回简洁进度与最终总结。",
                "en": "Accept a concrete delegated task from the main agent, call AI in subtask mode internally, and return concise progress plus a final summary using a lightweight XML protocol."
            },
            "parameters": [
                {
                    "name": "task",
                    "description": {
                        "zh": "主 agent 委托的具体任务。",
                        "en": "The concrete task delegated by the main agent."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "context_text",
                    "description": {
                        "zh": "补充上下文，例如限制条件、验收标准或资源编号。",
                        "en": "Additional context such as constraints, acceptance criteria, or resource identifiers."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "target_paths_json",
                    "description": {
                        "zh": "JSON 字符串数组，表示优先处理的文件路径列表。",
                        "en": "A JSON string array containing file paths to prioritize."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "max_tool_calls",
                    "description": {
                        "zh": "建议的最大工具调用次数，默认 8。",
                        "en": "Suggested maximum number of tool calls, default 8."
                    },
                    "type": "number",
                    "required": false
                }
            ]
        }
    ]
}
*/
import { resolveSubagentI18n } from "../shared/subagent_i18n.js";
import type { JavaBridgeValue } from "../../../types/java-bridge";

const EnhancedAIService = Java.com.ai.assistance.operit.api.chat.EnhancedAIService;
const FunctionType = Java.com.ai.assistance.operit.data.model.FunctionType;
const SystemPromptConfig = Java.com.ai.assistance.operit.core.config.SystemPromptConfig;
const Unit = Java.kotlin.Unit;
const PromptTurnClass = Java.type("com.ai.assistance.operit.core.chat.hooks.PromptTurn");
const PromptTurnKindClass = Java.type("com.ai.assistance.operit.core.chat.hooks.PromptTurnKind");
const SendMessageOptionsClass = Java.type(
  "com.ai.assistance.operit.api.chat.EnhancedAIService$SendMessageOptions"
);

const TOOL_TAG = /<tool\b[\s\S]*?<\/tool>/gi;
const TOOL_SELF_CLOSING = /<tool\b[^>]*\/>/gi;
const TOOL_RESULT_TAG = /<tool_result\b[\s\S]*?<\/tool_result>/gi;
const TOOL_RESULT_SELF = /<tool_result\b[^>]*\/>/gi;
const STATUS_TAG = /<status\b[\s\S]*?<\/status>/gi;
const STATUS_SELF = /<status\b[^>]*\/>/gi;
const THINK_TAG = /<think(?:ing)?>[\s\S]*?(<\/think(?:ing)?>|\z)/gi;
const SEARCH_TAG = /<search>[\s\S]*?(<\/search>|\z)/gi;

interface SendMessageOptions {
  message: string;
  chatHistory: ToolPkg.PromptTurn[];
  maxTokens: number;
  tokenUsageThreshold: number;
  customSystemPromptTemplate: string;
  onToolInvocation?: (toolName: string) => void;
}

interface ExecutionSettings {
  maxTokens: number;
  tokenUsageThreshold: number;
}

interface SubagentParams {
  task: string;
  context_text?: string;
  target_paths_json?: string;
  max_tool_calls?: number;
}

function asText(value: unknown): string {
  if (value === undefined || value === null) {
    return "";
  }
  return String(value);
}

function normalizeWhitespace(value: unknown): string {
  return asText(value).replace(/\s+/g, " ").trim();
}

function clipText(value: unknown, maxLength: number): string {
  const text = normalizeWhitespace(value);
  if (!text) {
    return "";
  }
  if (text.length <= maxLength) {
    return text;
  }
  return text.slice(0, Math.max(0, maxLength - 3)).trimEnd() + "...";
}

function escapeXmlText(value: unknown): string {
  return asText(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function escapeXmlAttr(value: unknown): string {
  return escapeXmlText(value).replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

function toErrorText(error: unknown): string {
  if (error instanceof Error) {
    return error.message || String(error);
  }
  return asText(error) || "unknown error";
}

function readBridgeValue(target: any, key: string): unknown {
  if (!target) {
    return undefined;
  }

  try {
    const value = target[key];
    if (value !== undefined) {
      return value;
    }
  } catch (_error) { }

  const capitalized = key.charAt(0).toUpperCase() + key.slice(1);
  const getterNames = ["get" + capitalized, "is" + capitalized];
  for (const getterName of getterNames) {
    try {
      if (typeof target[getterName] === "function") {
        return target[getterName]();
      }
    } catch (_error) { }
  }

  return undefined;
}

function removeThinkingContent(raw: unknown): string {
  return asText(raw).replace(THINK_TAG, "").replace(SEARCH_TAG, "").trim();
}

function stripMarkup(text: unknown): string {
  return asText(text)
    .replace(TOOL_TAG, "")
    .replace(TOOL_SELF_CLOSING, "")
    .replace(TOOL_RESULT_TAG, "")
    .replace(TOOL_RESULT_SELF, "")
    .replace(STATUS_TAG, "")
    .replace(STATUS_SELF, "")
    .trim();
}

function extractFinalNonToolAssistantContent(raw: unknown): string {
  const noThinking = removeThinkingContent(asText(raw).trim());
  const lastToolLike =
    /(<tool\s+name="([^"]+)"[\s\S]*?<\/tool>)|(<tool_result([^>]*)>[\s\S]*?<\/tool_result>)/gi;

  let lastMatch: RegExpExecArray | null = null;
  let match: RegExpExecArray | null;
  while ((match = lastToolLike.exec(noThinking)) !== null) {
    lastMatch = match;
  }

  const tail = lastMatch
    ? noThinking.substring((lastMatch.index || 0) + lastMatch[0].length)
    : noThinking;
  const tailStripped = stripMarkup(tail);
  if (tailStripped) {
    return tailStripped;
  }

  const fullStripped = stripMarkup(noThinking);
  if (!fullStripped) {
    return "";
  }

  const parts = fullStripped
    .split(/\n\s*\n+/)
    .map((item) => item.trim())
    .filter(Boolean);
  return parts.length > 0 ? parts[parts.length - 1] : fullStripped;
}

function toKotlinPromptTurnList(history: ToolPkg.PromptTurn[]): JavaBridgeValue[] {
  return (history || []).map((turn) =>
    new PromptTurnClass(
      resolvePromptTurnKind(turn.kind),
      String(turn.content ?? ""),
      typeof turn.toolName === "string" ? turn.toolName : null,
      isJsonObject(turn.metadata) ? turn.metadata : {}
    )
  );
}

function resolvePromptTurnKind(kind: ToolPkg.PromptTurnKind): JavaBridgeValue {
  switch (kind) {
    case "SYSTEM":
      return PromptTurnKindClass.SYSTEM;
    case "ASSISTANT":
      return PromptTurnKindClass.ASSISTANT;
    case "TOOL_CALL":
      return PromptTurnKindClass.TOOL_CALL;
    case "TOOL_RESULT":
      return PromptTurnKindClass.TOOL_RESULT;
    case "SUMMARY":
      return PromptTurnKindClass.SUMMARY;
    case "USER":
    default:
      return PromptTurnKindClass.USER;
  }
}

function isJsonObject(value: unknown): value is ToolPkg.JsonObject {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function createSendMessageOptions(options: SendMessageOptions): JavaBridgeValue {
  const javaOptions = new SendMessageOptionsClass();
  javaOptions.message = String(options.message ?? "");
  javaOptions.chatHistory = toKotlinPromptTurnList(options.chatHistory || []);
  javaOptions.maxTokens = Number(options.maxTokens);
  javaOptions.tokenUsageThreshold = Number(options.tokenUsageThreshold);
  javaOptions.customSystemPromptTemplate = options.customSystemPromptTemplate || null;
  javaOptions.subTask = true;
  javaOptions.proxySenderName = "Subagent";
  javaOptions.enableMemoryAutoUpdate = false;
  return javaOptions;
}

async function collectStreamToString(stream: {
  callSuspend: (...args: unknown[]) => Promise<unknown>;
}): Promise<string> {
  let buffer = "";
  const collector = {
    emit(value: unknown) {
      buffer += String(value || "");
      return Unit.INSTANCE;
    },
  };

  await stream.callSuspend("collect", collector);
  return buffer;
}

async function sendMessage(
  enhancedAIService: { callSuspend: (...args: unknown[]) => Promise<unknown> },
  options: SendMessageOptions
): Promise<string> {
  const javaOptions = createSendMessageOptions(options) as {
    callbacks?: unknown;
  };
  javaOptions.callbacks = options.onToolInvocation
    ? {
      onToolInvocation(toolName: string) {
        options.onToolInvocation?.(toolName);
        return Unit.INSTANCE;
      }
    }
    : null;

  const stream = await enhancedAIService.callSuspend(
    "sendMessage",
    javaOptions
  );

  return collectStreamToString(stream as { callSuspend: (...args: unknown[]) => Promise<unknown> });
}

function getAppContext(): unknown {
  if (typeof Java.getApplicationContext !== "function") {
    return null;
  }
  return Java.getApplicationContext();
}

function createRunId(): string {
  const now = Date.now().toString(36).slice(-6);
  const random = Math.floor(Math.random() * 1296)
    .toString(36)
    .padStart(2, "0");
  return "s" + now + random;
}

function parseTargetPaths(params: SubagentParams): string[] {
  const raw = asText(params.target_paths_json).trim();
  if (!raw) {
    return [];
  }

  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error("target_paths_json must be a JSON string array");
  }

  return parsed.map((item) => asText(item).trim()).filter(Boolean);
}

function parseMaxToolCalls(params: SubagentParams): number {
  const raw = params.max_tool_calls;
  if (raw === undefined || raw === null || String(raw).trim() === "") {
    return 8;
  }

  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error("max_tool_calls must be a positive number");
  }
  return Math.floor(value);
}

function stageUpdateXml(
  runId: string,
  stage: string,
  message: string,
  attrs?: Record<string, unknown>
): string {
  const extraAttrs = attrs || {};
  const attrSegments = [
    `run="${escapeXmlAttr(runId)}"`,
    `stage="${escapeXmlAttr(stage)}"`,
  ];

  Object.keys(extraAttrs).forEach((key) => {
    const value = extraAttrs[key];
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      attrSegments.push(`${key}="${escapeXmlAttr(String(value))}"`);
    }
  });

  return `<subagent><update ${attrSegments.join(" ")}>${escapeXmlText(message)}</update></subagent>`;
}

function finalXml(
  runId: string,
  success: boolean,
  toolCount: number,
  message: string
): string {
  const attrs = [
    `run="${escapeXmlAttr(runId)}"`,
    `success="${success ? "true" : "false"}"`,
    `tool_count="${escapeXmlAttr(String(toolCount))}"`,
  ];

  return `<subagent><final ${attrs.join(" ")}>${escapeXmlText(message)}</final></subagent>`;
}

function emitIntermediate(xmlText: string): void {
  if (typeof sendIntermediateResult === "function") {
    sendIntermediateResult(xmlText);
  }
}

function resolveExecutionSettings(
  enhancedAIService: { callSuspend: (...args: unknown[]) => Promise<unknown> }
): Promise<ExecutionSettings> {
  return enhancedAIService
    .callSuspend("getModelConfigForFunction", FunctionType.CHAT, null, null)
    .then((config) => {
      const contextLength = Number(readBridgeValue(config, "contextLength"));
      const threshold = Number(readBridgeValue(config, "summaryTokenThreshold"));
      return {
        maxTokens:
          Number.isFinite(contextLength) && contextLength > 0
            ? Math.floor(contextLength * 1024)
            : 48 * 1024,
        tokenUsageThreshold:
          Number.isFinite(threshold) && threshold > 0 && threshold <= 1
            ? threshold
            : 0.7,
      };
    })
    .catch((error) => {
      console.log("subagent resolveExecutionSettings error", String(error));
      return {
        maxTokens: 48 * 1024,
        tokenUsageThreshold: 0.7,
      };
    });
}

function buildCustomSystemPromptTemplate(): string {
  const base = String(SystemPromptConfig.SUBTASK_AGENT_PROMPT_TEMPLATE || "").trim();
  const extra = [
    "SUBAGENT_EXTRA_CONSTRAINTS:",
    "- You are running inside the passive subagent tool.",
    "- Handle exactly one delegated task in this run.",
    "- Focus tightly on the delegated task and the provided target paths.",
    "- Avoid unrelated exploration and keep tool usage lean.",
    "- Never call subagent_run and never create another delegated agent.",
    "- Never wait for user input. If blocked, briefly state the blocker and finish.",
    "- Your final answer must be short and directly usable by the parent agent.",
  ].join("\n");

  return `${base}\n\n${extra}`.trim();
}

function buildDelegatedTaskMessage(
  task: string,
  contextText: string,
  targetPaths: string[],
  maxToolCalls: number
): string {
  const lines: string[] = [];
  lines.push("Delegated task from the parent agent:");
  lines.push(task);

  if (contextText) {
    lines.push("");
    lines.push("Extra context and constraints:");
    lines.push(contextText);
  }

  if (targetPaths.length > 0) {
    lines.push("");
    lines.push(
      "Priority target paths. Stay focused on these unless expansion is strictly necessary:"
    );
    targetPaths.forEach((path, index) => {
      lines.push(`${index + 1}. ${path}`);
    });
  }

  lines.push("");
  lines.push("Execution rules:");
  lines.push("- Focus only on this delegated task.");
  lines.push("- Prefer the listed target paths and avoid unrelated exploration.");
  lines.push(`- Use tools only when necessary. Target maximum tool calls: ${maxToolCalls}.`);
  lines.push("- Never call subagent_run or delegate to another subagent.");
  lines.push("- Do not ask the user questions.");
  lines.push("- Final output must be 2-6 short lines with only the useful result summary.");
  return lines.join("\n");
}

function internalToolUpdateText(toolName: string): string {
  const text = resolveSubagentI18n();
  const raw = asText(toolName).trim();
  if (!raw || raw === "package_proxy") {
    return text.toolTriggeredGeneric;
  }
  const shortName = raw.includes(":")
    ? raw.substring(raw.lastIndexOf(":") + 1).trim()
    : raw;
  return shortName ? text.toolCalledPrefix(shortName) : text.toolTriggeredGeneric;
}

async function runSubagent(params: SubagentParams): Promise<string> {
  const runId = createRunId();
  let toolCount = 0;

  try {
    const task = asText(params.task).trim();
    const contextText = asText(params.context_text).trim();
    const targetPaths = parseTargetPaths(params);
    const maxToolCalls = parseMaxToolCalls(params);
    const text = resolveSubagentI18n();

    emitIntermediate(stageUpdateXml(runId, "accepted", text.stageAccepted));
    emitIntermediate(
      stageUpdateXml(
        runId,
        "planning",
        targetPaths.length > 0 ? text.stagePlanningTargets : text.stagePlanningTask
      )
    );

    const context = getAppContext();
    if (!context) {
      throw new Error(text.errorNoAppContext);
    }

    const enhancedAIService = EnhancedAIService.getInstance(context);
    const settings = await resolveExecutionSettings(enhancedAIService);

    emitIntermediate(stageUpdateXml(runId, "executing", text.stageExecuting));

    const raw = await sendMessage(enhancedAIService, {
      message: buildDelegatedTaskMessage(task, contextText, targetPaths, maxToolCalls),
      chatHistory: [],
      maxTokens: settings.maxTokens,
      tokenUsageThreshold: settings.tokenUsageThreshold,
      customSystemPromptTemplate: buildCustomSystemPromptTemplate(),
      onToolInvocation: (toolName: string) => {
        toolCount += 1;
        emitIntermediate(
          stageUpdateXml(runId, "tool", internalToolUpdateText(toolName), {
            count: toolCount,
          })
        );
      },
    });

    emitIntermediate(stageUpdateXml(runId, "summarizing", text.stageSummarizing));

    const summary = clipText(extractFinalNonToolAssistantContent(raw), 320);
    if (!summary) {
      throw new Error(text.errorNoSummary);
    }

    return finalXml(runId, true, toolCount, summary);
  } catch (error) {
    return finalXml(
      runId,
      false,
      toolCount,
      clipText(resolveSubagentI18n().errorExecutionFailedPrefix(toErrorText(error)), 220)
    );
  }
}

export async function subagent_run(params: SubagentParams): Promise<string> {
  try {
    return await runSubagent(params);
  } catch (error) {
    const runId = createRunId();
    return finalXml(
      runId,
      false,
      0,
      clipText(resolveSubagentI18n().errorExecutionFailedPrefix(toErrorText(error)), 220)
    );
  }
}

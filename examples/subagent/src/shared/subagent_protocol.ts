import type { ToolPkg } from "../../../types";

export const DEFAULT_MAX_TOOL_CALLS = 8;
export const MAX_TOOL_CALLS = 64;
export const MAX_INLINE_SUMMARY_XML_CHARS = 4000;

export type SubagentStatus = "completed" | "blocked" | "failed";
export type HostReason =
  | "model_finished" | "tool_budget_exhausted" | "delegation_denied"
  | "permission_denied" | "token_limit" | "cancelled" | "execution_error";

export interface SubagentParams {
  task: string;
  context_text?: string;
  target_paths_json?: string;
  max_tool_calls?: number;
}

export interface ValidatedTask {
  task: string;
  contextText: string;
  targetPaths: string[];
  maxToolCalls: number;
}

export interface ToolEvidence {
  id: string;
  toolName: string;
  arguments: { name: string; value: string }[];
  success: boolean;
  result: string;
  error: string | null;
  executed: boolean;
  resultTruncated: boolean;
}

export interface HostOutcome {
  reason: HostReason;
  error: string | null;
  toolSessionScopeId: string;
  finalText: string;
  executedToolCount: number;
  tools: ToolEvidence[];
}

export interface ModelDeclaration {
  status: SubagentStatus;
  summary: string;
  evidence_ids: string[];
}

export interface FinalDecision {
  status: SubagentStatus;
  reason: string;
  summary: string;
  evidenceIds: string[];
}

export interface FailureEnvelope {
  success: false;
  message: string;
  data: string;
}

export class SubagentProtocolError extends Error {
  constructor(readonly reason: string, message: string) {
    super(message);
    this.name = "SubagentProtocolError";
  }
}

export function validateTask(params: SubagentParams): ValidatedTask {
  if (typeof params.task !== "string" || params.task.trim() === "") {
    throw new SubagentProtocolError("invalid_task", "task must be a non-empty string");
  }
  const maxToolCalls = params.max_tool_calls === undefined
    ? DEFAULT_MAX_TOOL_CALLS : params.max_tool_calls;
  if (!Number.isInteger(maxToolCalls) || maxToolCalls < 1 || maxToolCalls > MAX_TOOL_CALLS) {
    throw new SubagentProtocolError("invalid_budget", "max_tool_calls must be an integer from 1 to 64");
  }
  let targetPaths: string[] = [];
  if (params.target_paths_json !== undefined) {
    const value = parseJson(params.target_paths_json, "invalid_target_paths");
    if (!Array.isArray(value) || value.some(path => typeof path !== "string" || path.trim() === "")) {
      throw new SubagentProtocolError("invalid_target_paths", "target_paths_json must be an array of non-empty strings");
    }
    targetPaths = value.map(path => {
      if (typeof path !== "string") {
        throw new SubagentProtocolError("invalid_target_paths", "Each target path must be a string");
      }
      return path.trim();
    });
  }
  return { task: params.task.trim(), contextText: (params.context_text ?? "").trim(), targetPaths, maxToolCalls };
}

function parseJson(text: string, reason: string): ToolPkg.JsonValue {
  try {
    const value: ToolPkg.JsonValue = JSON.parse(text);
    return value;
  } catch (error) {
    console.error("Subagent JSON parsing failed", error);
    throw new SubagentProtocolError(reason, "Expected valid JSON");
  }
}

function objectValue(value: ToolPkg.JsonValue, reason: string): ToolPkg.JsonObject {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new SubagentProtocolError(reason, "Expected a JSON object");
  }
  return value;
}

function stringValue(value: ToolPkg.JsonValue, field: string): string {
  if (typeof value !== "string") {
    throw new SubagentProtocolError("invalid_host_outcome", `${field} must be a string`);
  }
  return value;
}

function booleanValue(value: ToolPkg.JsonValue, field: string): boolean {
  if (typeof value !== "boolean") {
    throw new SubagentProtocolError("invalid_host_outcome", `${field} must be a boolean`);
  }
  return value;
}

function nullableError(value: ToolPkg.JsonValue): string | null {
  return value === null ? null : stringValue(value, "error");
}

function readToolEvidence(value: ToolPkg.JsonValue): ToolEvidence {
  const record = objectValue(value, "invalid_host_outcome");
  if (!Array.isArray(record.arguments)) {
    throw new SubagentProtocolError("invalid_host_outcome", "Tool arguments must be an array");
  }
  const id = stringValue(record.id, "id");
  const toolName = stringValue(record.toolName, "toolName");
  if (!id || !toolName) {
    throw new SubagentProtocolError("invalid_host_outcome", "Tool evidence requires an id and toolName");
  }
  return {
    id,
    toolName,
    arguments: record.arguments.map(argument => {
      const item = objectValue(argument, "invalid_host_outcome");
      return { name: stringValue(item.name, "argument.name"), value: stringValue(item.value, "argument.value") };
    }),
    success: booleanValue(record.success, "success"),
    result: stringValue(record.result, "result"),
    error: nullableError(record.error),
    executed: booleanValue(record.executed, "executed"),
    resultTruncated: booleanValue(record.resultTruncated, "resultTruncated"),
  };
}

export function parseToolEvidence(json: string): ToolEvidence {
  return readToolEvidence(parseJson(json, "invalid_host_outcome"));
}

function hostReason(value: ToolPkg.JsonValue): HostReason {
  switch (value) {
    case "model_finished": case "tool_budget_exhausted": case "delegation_denied":
    case "permission_denied": case "token_limit": case "cancelled": case "execution_error":
      return value;
    default:
      throw new SubagentProtocolError("invalid_host_outcome", "Unknown host completion reason");
  }
}

export function parseHostOutcome(json: string): HostOutcome {
  const value = objectValue(parseJson(json, "invalid_host_outcome"), "invalid_host_outcome");
  if (!Array.isArray(value.tools) || typeof value.executedToolCount !== "number" || !Number.isInteger(value.executedToolCount)) {
    throw new SubagentProtocolError("invalid_host_outcome", "Invalid host tool ledger or executedToolCount");
  }
  const tools = value.tools.map(readToolEvidence);
  if (new Set(tools.map(tool => tool.id)).size !== tools.length || value.executedToolCount < 0 ||
      value.executedToolCount > MAX_TOOL_CALLS || tools.filter(tool => tool.executed).length !== value.executedToolCount) {
    throw new SubagentProtocolError("invalid_host_outcome", "Host tool ledger ids or executed count are inconsistent");
  }
  return {
    reason: hostReason(value.reason),
    error: nullableError(value.error),
    toolSessionScopeId: stringValue(value.toolSessionScopeId, "toolSessionScopeId"),
    finalText: stringValue(value.finalText, "finalText"),
    executedToolCount: value.executedToolCount,
    tools,
  };
}

// Only leading internal blocks are syntax. Tags inside the declaration's JSON strings are data.
const INTERNAL_TAG_NAME = /^(?:think|thinking|search|status|tool(?:_(?!result(?:_|$))[A-Za-z0-9_]+)?|tool_result(?:_[A-Za-z0-9_]+)?)$/i;

export function finalDeclarationText(raw: string): string {
  let remaining = raw.trim();
  while (remaining.startsWith("<")) {
    const opening = /^<([A-Za-z][A-Za-z0-9_]*)(?=[\s/>]|$)/.exec(remaining);
    if (!opening || !INTERNAL_TAG_NAME.test(opening[1])) break;
    let quote = "";
    let tagEnd = -1;
    for (let index = opening[0].length; index < remaining.length; index += 1) {
      const char = remaining[index];
      if (quote) {
        if (char === quote) quote = "";
      } else if (char === '"' || char === "'") {
        quote = char;
      } else if (char === ">") {
        tagEnd = index;
        break;
      }
    }
    if (tagEnd < 0) throw new SubagentProtocolError("invalid_outcome", "Incomplete leading internal tag");
    if (remaining[tagEnd - 1] === "/") {
      remaining = remaining.slice(tagEnd + 1).trim();
      continue;
    }
    const closing = new RegExp(`</${opening[1]}\\s*>`, "i").exec(remaining.slice(tagEnd + 1));
    if (!closing) throw new SubagentProtocolError("invalid_outcome", "Unclosed leading internal block");
    remaining = remaining.slice(tagEnd + 1 + closing.index + closing[0].length).trim();
  }
  if (remaining.startsWith("```")) {
    const fenced = /^```json[ \t]*\r?\n([\s\S]*?)\r?\n```$/i.exec(remaining);
    if (!fenced) throw new SubagentProtocolError("invalid_outcome", "Expected a complete json code fence");
    remaining = fenced[1].trim();
  }
  if (!remaining) throw new SubagentProtocolError("missing_outcome", "The final model round did not contain a declaration");
  return remaining;
}

export function parseModelDeclaration(raw: string): ModelDeclaration {
  const value = objectValue(parseJson(finalDeclarationText(raw), "invalid_outcome"), "invalid_outcome");
  if (Object.keys(value).sort().join(",") !== "evidence_ids,status,summary" ||
      (value.status !== "completed" && value.status !== "blocked" && value.status !== "failed") ||
      typeof value.summary !== "string" || !value.summary.trim() || !Array.isArray(value.evidence_ids)) {
    throw new SubagentProtocolError("invalid_outcome", "Expected status, a non-empty summary and evidence_ids only");
  }
  const evidenceIds = value.evidence_ids.map(id => {
    if (typeof id !== "string" || !id) throw new SubagentProtocolError("invalid_evidence", "Evidence ids must be non-empty strings");
    return id;
  });
  if (new Set(evidenceIds).size !== evidenceIds.length) {
    throw new SubagentProtocolError("invalid_evidence", "Evidence ids must not repeat");
  }
  return { status: value.status, summary: value.summary.trim(), evidence_ids: evidenceIds };
}

export function decideOutcome(outcome: HostOutcome): FinalDecision {
  if (outcome.reason !== "model_finished") {
    const failed = outcome.reason === "execution_error";
    return {
      status: failed ? "failed" : "blocked",
      reason: outcome.reason,
      summary: outcome.error === null ? outcome.reason : `${outcome.reason}: ${outcome.error}`,
      evidenceIds: [],
    };
  }
  const declaration = parseModelDeclaration(outcome.finalText);
  const ledger = new Map<string, ToolEvidence>();
  outcome.tools.forEach(tool => ledger.set(tool.id, tool));
  if (declaration.evidence_ids.some(id => !ledger.has(id))) {
    throw new SubagentProtocolError("invalid_evidence", "The declaration referenced evidence outside this run");
  }
  if (declaration.status === "completed" && outcome.tools.some(tool => !tool.success)) {
    return {
      status: "blocked",
      reason: "tool_failure",
      summary: `Tool failures remain in the host ledger; completion is unverified.\n${declaration.summary}`,
      evidenceIds: declaration.evidence_ids,
    };
  }
  if (declaration.status === "completed" && outcome.tools.length > 0 &&
      !declaration.evidence_ids.some(id => { const tool = ledger.get(id); return tool !== undefined && tool.executed && tool.success; })) {
    throw new SubagentProtocolError("insufficient_evidence", "Completion must reference successful tool evidence from this run");
  }
  return {
    status: declaration.status,
    reason: declaration.status === "completed" ? "completed" : `model_${declaration.status}`,
    summary: declaration.summary,
    evidenceIds: declaration.evidence_ids,
  };
}

export function escapeXmlText(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export function escapeXmlAttr(value: string): string {
  return escapeXmlText(value).replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

export function inlineSummary(summary: string): { xml: string; truncated: boolean } {
  let xml = "";
  for (const char of summary) {
    const escaped = escapeXmlText(char);
    if (xml.length + escaped.length > MAX_INLINE_SUMMARY_XML_CHARS) return { xml, truncated: true };
    xml += escaped;
  }
  return { xml, truncated: false };
}

export function finalXml(runId: string, decision: FinalDecision, tools: ToolEvidence[], scopeId: string, evidencePath: string | null, executedToolCount: number): string {
  const summary = inlineSummary(decision.summary);
  const attrs = [
    `run="${escapeXmlAttr(runId)}"`, `success="${decision.status === "completed"}"`,
    `tool_count="${executedToolCount}"`,
    `status="${decision.status}"`, `reason="${escapeXmlAttr(decision.reason)}"`,
    `summary_truncated="${summary.truncated}"`,
  ];
  if (evidencePath !== null) attrs.push(`evidence_path="${escapeXmlAttr(evidencePath)}"`);
  const index = {
    tool_session_scope_id: scopeId,
    evidence_path: evidencePath,
    tool_ids: tools.slice(0, 8).map(tool => tool.id),
    successful_ids: tools.filter(tool => tool.success).slice(0, 8).map(tool => tool.id),
    failed_ids: tools.filter(tool => !tool.success).slice(0, 8).map(tool => tool.id),
    tool_record_count: tools.length,
    executed_tool_count: executedToolCount,
    failure_count: tools.filter(tool => !tool.success).length,
    index_truncated: tools.length > 8,
  };
  return `<subagent><final ${attrs.join(" ")}>${summary.xml}</final><evidence encoding="json">${escapeXmlText(JSON.stringify(index))}</evidence></subagent>`;
}

export function toolReturn(xml: string, decision: FinalDecision): string | FailureEnvelope {
  return decision.status === "completed" ? xml : {
    success: false,
    message: `Subagent ${decision.status}: ${decision.reason}`,
    data: xml,
  };
}

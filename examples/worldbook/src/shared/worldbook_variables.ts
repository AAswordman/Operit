import { getWorldBookDir, ensureWorldBookStorage, readWorldBookJson, writeWorldBookJson, withWorldBookStorage } from "./worldbook_storage.js";

interface WorldBookVariableEntryLike {
  name?: string;
  content?: string;
  character_card_id?: string;
}

interface ChatMessageInfoLike {
  sender?: string;
  content?: string;
  timestamp?: number;
}

interface WorldBookChatVariableState {
  local_variables: Record<string, unknown>;
  processed_message_timestamps: number[];
  last_scanned_timestamp: number;
}

interface WorldBookVariableStore {
  global_variables: Record<string, unknown>;
  character_variables: Record<string, Record<string, unknown>>;
  chats: Record<string, WorldBookChatVariableState>;
}

export interface WorldBookVariableRenderContext {
  localVariables: Record<string, unknown>;
  characterVariables: Record<string, unknown>;
  globalVariables: Record<string, unknown>;
}

interface JsonPatchOperation {
  op?: string;
  path?: string;
  from?: string;
  to?: string;
  value?: unknown;
}

const VARIABLE_STORE_FILE = "variables.json";
const DEFAULT_VARIABLE_NAME = "stat_data";

function getVariableStoreFile(): string {
  return `${getWorldBookDir()}/${VARIABLE_STORE_FILE}`;
}

function isVariableRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
async function readVariableStore(): Promise<WorldBookVariableStore> {
  await ensureWorldBookStorage();
  const parsed = await readWorldBookJson(getVariableStoreFile());
  if (!isVariableRecord(parsed) || !isVariableRecord(parsed.global_variables) ||
      !isVariableRecord(parsed.character_variables) || !isVariableRecord(parsed.chats)) {
    console.error("Invalid world book variable store", getVariableStoreFile());
    throw new Error("世界书变量文件格式无效，已停止本次操作");
  }
  const characterVariables: WorldBookVariableStore["character_variables"] = {};
  for (const [key, value] of Object.entries(parsed.character_variables)) {
    if (!isVariableRecord(value)) {
      console.error("Invalid world book character variables", key);
      throw new Error("世界书角色变量格式无效，已停止本次操作");
    }
    characterVariables[key] = value;
  }
  const chats: WorldBookVariableStore["chats"] = {};
  for (const [key, value] of Object.entries(parsed.chats)) {
    if (!isVariableRecord(value) || !isVariableRecord(value.local_variables) ||
        !Array.isArray(value.processed_message_timestamps) ||
        !value.processed_message_timestamps.every((item) => typeof item === "number" && Number.isFinite(item)) ||
        typeof value.last_scanned_timestamp !== "number" || !Number.isFinite(value.last_scanned_timestamp)) {
      console.error("Invalid world book chat variables", key);
      throw new Error("世界书会话变量格式无效，已停止本次操作");
    }
    chats[key] = {
      local_variables: value.local_variables,
      processed_message_timestamps: value.processed_message_timestamps,
      last_scanned_timestamp: value.last_scanned_timestamp
    };
  }
  return { global_variables: parsed.global_variables, character_variables: characterVariables, chats };
}
async function writeVariableStore(store: WorldBookVariableStore): Promise<void> {
  await writeWorldBookJson(getVariableStoreFile(), store);
}
function createEmptyChatState(): WorldBookChatVariableState {
  return {
    local_variables: {},
    processed_message_timestamps: [],
    last_scanned_timestamp: 0
  };
}

function matchesCharacterCard(entry: WorldBookVariableEntryLike, callerCardId: string): boolean {
  const targetCardId = String(entry.character_card_id || "").trim();
  if (!targetCardId) {
    return true;
  }
  return !!callerCardId && callerCardId === targetCardId;
}

function cloneVariableTemplate(template: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(template)) as Record<string, unknown>;
}

function decodeYamlScalar(raw: string): unknown {
  const value = raw.trim();
  if (value === "null") {
    return null;
  }
  if (value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  if (value === "{}") {
    return {};
  }
  if (value === "[]") {
    return [];
  }
  if (/^-?\d+(?:\.\d+)?$/.test(value)) {
    return Number(value);
  }
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  return value;
}

function countIndent(line: string): number {
  let count = 0;
  while (count < line.length && line[count] === " ") {
    count += 1;
  }
  return count;
}

function parseIndentedYamlLike(text: string): unknown {
  const lines = text
    .replace(/\r/g, "")
    .split("\n")
    .filter((line) => line.trim().length > 0);

  function parseBlock(startIndex: number, indent: number): [unknown, number] {
    if (startIndex >= lines.length) {
      return [null, startIndex];
    }

    const currentLine = lines[startIndex];
    const currentIndent = countIndent(currentLine);
    if (currentIndent < indent) {
      return [null, startIndex];
    }

    const trimmed = currentLine.trimStart();
    if (trimmed.startsWith("- ")) {
      return parseArray(startIndex, indent);
    }
    return parseObject(startIndex, indent);
  }

  function parseObject(startIndex: number, indent: number): [Record<string, unknown>, number] {
    const result: Record<string, unknown> = {};
    let index = startIndex;

    while (index < lines.length) {
      const rawLine = lines[index];
      const lineIndent = countIndent(rawLine);
      if (lineIndent < indent) {
        break;
      }
      if (lineIndent > indent) {
        index += 1;
        continue;
      }

      const trimmed = rawLine.trim();
      if (trimmed.startsWith("- ")) {
        break;
      }

      const colonIndex = trimmed.indexOf(":");
      if (colonIndex === -1) {
        index += 1;
        continue;
      }

      const key = trimmed.slice(0, colonIndex).trim();
      const remainder = trimmed.slice(colonIndex + 1).trim();

      if (remainder.length > 0) {
        result[key] = decodeYamlScalar(remainder);
        index += 1;
        continue;
      }

      const nextIndex = index + 1;
      if (nextIndex >= lines.length) {
        result[key] = null;
        index = nextIndex;
        continue;
      }

      const nextIndent = countIndent(lines[nextIndex]);
      if (nextIndent <= lineIndent) {
        result[key] = null;
        index = nextIndex;
        continue;
      }

      const [nestedValue, consumedIndex] = parseBlock(nextIndex, nextIndent);
      result[key] = nestedValue;
      index = consumedIndex;
    }

    return [result, index];
  }

  function parseArray(startIndex: number, indent: number): [unknown[], number] {
    const result: unknown[] = [];
    let index = startIndex;

    while (index < lines.length) {
      const rawLine = lines[index];
      const lineIndent = countIndent(rawLine);
      if (lineIndent < indent) {
        break;
      }
      if (lineIndent !== indent) {
        index += 1;
        continue;
      }

      const trimmed = rawLine.trim();
      if (!trimmed.startsWith("- ")) {
        break;
      }

      const itemText = trimmed.slice(2).trim();
      if (!itemText) {
        const nextIndex = index + 1;
        if (nextIndex < lines.length && countIndent(lines[nextIndex]) > lineIndent) {
          const [nestedValue, consumedIndex] = parseBlock(nextIndex, countIndent(lines[nextIndex]));
          result.push(nestedValue);
          index = consumedIndex;
        } else {
          result.push(null);
          index = nextIndex;
        }
        continue;
      }

      const colonIndex = itemText.indexOf(":");
      if (colonIndex !== -1) {
        const key = itemText.slice(0, colonIndex).trim();
        const remainder = itemText.slice(colonIndex + 1).trim();
        const itemObject: Record<string, unknown> = {};
        itemObject[key] = remainder.length > 0 ? decodeYamlScalar(remainder) : null;

        let nextIndex = index + 1;
        if (nextIndex < lines.length && countIndent(lines[nextIndex]) > lineIndent) {
          const [nestedValue, consumedIndex] = parseBlock(nextIndex, countIndent(lines[nextIndex]));
          if (nestedValue && typeof nestedValue === "object" && !Array.isArray(nestedValue)) {
            Object.assign(itemObject, nestedValue as Record<string, unknown>);
          }
          nextIndex = consumedIndex;
        }

        result.push(itemObject);
        index = nextIndex;
        continue;
      }

      result.push(decodeYamlScalar(itemText));
      index += 1;
    }

    return [result, index];
  }

  const [parsed] = parseBlock(0, countIndent(lines[0] || ""));
  return parsed;
}

function findInitVariableTemplate(
  entries: WorldBookVariableEntryLike[],
  callerCardId: string
): Record<string, unknown> | null {
  const initEntry = entries.find(
    (entry) =>
      matchesCharacterCard(entry, callerCardId) &&
      /\[?\s*InitVar\s*\]?/i.test(String(entry.name || ""))
  );
  if (!initEntry?.content) {
    return null;
  }

  try {
    const parsed = parseIndentedYamlLike(String(initEntry.content || ""));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null;
  } catch (_error) {
    return null;
  }
}

function pathTokensFromPointer(path: string): string[] {
  if (!path || path === "/") {
    return [];
  }
  return path
    .split("/")
    .slice(1)
    .map((token) => token.replace(/~1/g, "/").replace(/~0/g, "~"));
}

function containsProtectedToken(tokens: string[]): boolean {
  return tokens.some((token) => token.startsWith("_"));
}

function resolveVariableRoot(
  variableMap: Record<string, unknown>,
  pathTokens: string[]
): { root: Record<string, unknown> | unknown[]; tokens: string[] } {
  if (pathTokens.length === 0) {
    return { root: variableMap, tokens: pathTokens };
  }

  if (pathTokens[0] in variableMap) {
    return { root: variableMap, tokens: pathTokens };
  }

  const defaultRoot = variableMap[DEFAULT_VARIABLE_NAME];
  if (defaultRoot && typeof defaultRoot === "object" && !Array.isArray(defaultRoot)) {
    return { root: defaultRoot as Record<string, unknown>, tokens: pathTokens };
  }

  return { root: variableMap, tokens: pathTokens };
}

function getValueByPointer(root: unknown, tokens: string[]): unknown {
  let current = root;
  for (const token of tokens) {
    if (Array.isArray(current)) {
      const index = Number(token);
      if (!Number.isInteger(index) || index < 0 || index >= current.length) {
        return undefined;
      }
      current = current[index];
      continue;
    }
    if (!current || typeof current !== "object") {
      return undefined;
    }
    current = (current as Record<string, unknown>)[token];
  }
  return current;
}

function getContainerAndKey(
  root: Record<string, unknown> | unknown[],
  tokens: string[],
  createMissingParents: boolean
): { container: Record<string, unknown> | unknown[]; key: string } | null {
  if (tokens.length === 0) {
    return null;
  }

  let current: Record<string, unknown> | unknown[] = root;
  for (let index = 0; index < tokens.length - 1; index += 1) {
    const token = tokens[index];
    const nextToken = tokens[index + 1];

    if (Array.isArray(current)) {
      const currentIndex = Number(token);
      if (!Number.isInteger(currentIndex) || currentIndex < 0) {
        return null;
      }
      let nextValue = current[currentIndex];
      if (nextValue == null && createMissingParents) {
        nextValue = /^\d+$/.test(nextToken) ? [] : {};
        current[currentIndex] = nextValue;
      }
      if (!nextValue || typeof nextValue !== "object") {
        return null;
      }
      current = nextValue as Record<string, unknown> | unknown[];
      continue;
    }

    let nextValue = current[token];
    if (nextValue == null && createMissingParents) {
      nextValue = /^\d+$/.test(nextToken) ? [] : {};
      current[token] = nextValue;
    }
    if (!nextValue || typeof nextValue !== "object") {
      return null;
    }
    current = nextValue as Record<string, unknown> | unknown[];
  }

  return {
    container: current,
    key: tokens[tokens.length - 1]
  };
}

function setValueByPointer(
  root: Record<string, unknown> | unknown[],
  tokens: string[],
  value: unknown,
  createMissingParents: boolean
): boolean {
  if (tokens.length === 0) {
    return false;
  }

  const target = getContainerAndKey(root, tokens, createMissingParents);
  if (!target) {
    return false;
  }

  if (Array.isArray(target.container)) {
    if (target.key === "-") {
      target.container.push(value);
      return true;
    }

    const index = Number(target.key);
    if (!Number.isInteger(index) || index < 0) {
      return false;
    }

    if (index >= target.container.length && !createMissingParents) {
      return false;
    }
    target.container[index] = value;
    return true;
  }

  target.container[target.key] = value;
  return true;
}

function removeValueByPointer(root: Record<string, unknown> | unknown[], tokens: string[]): boolean {
  if (tokens.length === 0) {
    return false;
  }
  const target = getContainerAndKey(root, tokens, false);
  if (!target) {
    return false;
  }

  if (Array.isArray(target.container)) {
    const index = Number(target.key);
    if (!Number.isInteger(index) || index < 0 || index >= target.container.length) {
      return false;
    }
    target.container.splice(index, 1);
    return true;
  }

  if (!(target.key in target.container)) {
    return false;
  }
  delete target.container[target.key];
  return true;
}

function applyPatchOperations(
  variableMap: Record<string, unknown>,
  operations: JsonPatchOperation[]
): boolean {
  let changed = false;

  for (const operation of operations) {
    const op = String(operation.op || "").trim().toLowerCase();
    const path = String((op === "move" ? operation.to ?? operation.path : operation.path) || "").trim();
    if (!op || !path) {
      continue;
    }

    const rawTokens = pathTokensFromPointer(path);
    if (containsProtectedToken(rawTokens)) {
      continue;
    }
    const resolved = resolveVariableRoot(variableMap, rawTokens);
    const tokens = resolved.tokens;
    const root = resolved.root;

    if (op === "replace") {
      changed = setValueByPointer(root, tokens, operation.value, false) || changed;
      continue;
    }

    if (op === "insert") {
      changed = setValueByPointer(root, tokens, operation.value, true) || changed;
      continue;
    }

    if (op === "remove") {
      changed = removeValueByPointer(root, tokens) || changed;
      continue;
    }

    if (op === "delta") {
      const currentValue = getValueByPointer(root, tokens);
      const deltaValue = Number(operation.value);
      if (typeof currentValue === "number" && Number.isFinite(deltaValue)) {
        changed = setValueByPointer(root, tokens, currentValue + deltaValue, false) || changed;
      }
      continue;
    }

    if (op === "move") {
      const fromPath = String(operation.from || "").trim();
      if (!fromPath) {
        continue;
      }
      const fromTokens = pathTokensFromPointer(fromPath);
      if (containsProtectedToken(fromTokens)) {
        continue;
      }
      const fromResolved = resolveVariableRoot(variableMap, fromTokens);
      const movedValue = getValueByPointer(fromResolved.root, fromResolved.tokens);
      if (typeof movedValue === "undefined") {
        continue;
      }
      const removed = removeValueByPointer(fromResolved.root, fromResolved.tokens);
      const inserted = setValueByPointer(root, tokens, movedValue, true);
      changed = (removed || inserted) || changed;
    }
  }

  return changed;
}

function extractJsonPatchOperations(messageContent: string): JsonPatchOperation[] {
  const operations: JsonPatchOperation[] = [];
  const normalized = messageContent.replace(/\r/g, "");
  const xmlPatchPattern =
    /<UpdateVariable>[\s\S]*?<JSONPatch>\s*```(?:json)?\s*([\s\S]*?)\s*```?\s*<\/JSONPatch>[\s\S]*?<\/UpdateVariable>|<UpdateVariable>[\s\S]*?<JSONPatch>\s*([\s\S]*?)\s*<\/JSONPatch>[\s\S]*?<\/UpdateVariable>|<JSONPatch>\s*```(?:json)?\s*([\s\S]*?)\s*```?\s*<\/JSONPatch>|<JSONPatch>\s*([\s\S]*?)\s*<\/JSONPatch>/gi;

  let match: RegExpExecArray | null;
  while ((match = xmlPatchPattern.exec(normalized))) {
    const candidate = [match[1], match[2], match[3], match[4]].find((item) => !!item);
    if (!candidate) {
      continue;
    }

    const cleaned = candidate.trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
    try {
      const parsed = JSON.parse(cleaned);
      if (Array.isArray(parsed)) {
        for (const item of parsed) {
          if (item && typeof item === "object") {
            operations.push(item as JsonPatchOperation);
          }
        }
      }
    } catch (_error) {
    }
  }

  return operations;
}

function isAssistantMessage(sender: string): boolean {
  const normalized = sender.trim().toLowerCase();
  return normalized === "ai" || normalized === "assistant";
}

function formatScalar(value: unknown): string {
  if (value == null) {
    return "null";
  }
  if (typeof value === "boolean" || typeof value === "number") {
    return String(value);
  }
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value);
}

function appendFormattedValue(lines: string[], value: unknown, indent: number) {
  const padding = " ".repeat(indent);

  if (Array.isArray(value)) {
    if (value.length === 0) {
      lines.push(`${padding}[]`);
      return;
    }
    for (const item of value) {
      if (item && typeof item === "object") {
        lines.push(`${padding}-`);
        appendFormattedObject(lines, item as Record<string, unknown>, indent + 2);
      } else {
        lines.push(`${padding}- ${formatScalar(item)}`);
      }
    }
    return;
  }

  if (value && typeof value === "object") {
    appendFormattedObject(lines, value as Record<string, unknown>, indent);
    return;
  }

  lines.push(`${padding}${formatScalar(value)}`);
}

function appendFormattedObject(lines: string[], record: Record<string, unknown>, indent: number) {
  const padding = " ".repeat(indent);
  for (const [key, value] of Object.entries(record)) {
    if (Array.isArray(value)) {
      if (value.length === 0) {
        lines.push(`${padding}${key}: []`);
      } else {
        lines.push(`${padding}${key}:`);
        appendFormattedValue(lines, value, indent + 2);
      }
      continue;
    }

    if (value && typeof value === "object") {
      const objectEntries = Object.keys(value as Record<string, unknown>);
      if (objectEntries.length === 0) {
        lines.push(`${padding}${key}: {}`);
      } else {
        lines.push(`${padding}${key}:`);
        appendFormattedObject(lines, value as Record<string, unknown>, indent + 2);
      }
      continue;
    }

    if (typeof value === "string" && value.includes("\n")) {
      lines.push(`${padding}${key}: |-`);
      for (const line of value.split("\n")) {
        lines.push(`${" ".repeat(indent + 2)}${line}`);
      }
      continue;
    }

    lines.push(`${padding}${key}: ${formatScalar(value)}`);
  }
}

function formatVariableValue(value: unknown): string {
  if (value == null) {
    return "";
  }
  const lines: string[] = [];
  appendFormattedValue(lines, value, 0);
  return lines.join("\n");
}

function sanitizeAssistantVariableValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => sanitizeAssistantVariableValue(item));
  }
  if (!value || typeof value !== "object") {
    return value;
  }

  const sanitized: Record<string, unknown> = {};
  for (const [key, nestedValue] of Object.entries(value as Record<string, unknown>)) {
    if (key.startsWith("$")) {
      continue;
    }
    sanitized[key] = sanitizeAssistantVariableValue(nestedValue);
  }
  return sanitized;
}

function stringifyAssistantVariableValue(value: unknown): string {
  const sanitizedValue = sanitizeAssistantVariableValue(value);
  if (typeof sanitizedValue === "undefined") {
    return "";
  }
  const serialized = JSON.stringify(sanitizedValue);
  return typeof serialized === "string" ? serialized : "";
}

function getByDotPath(root: Record<string, unknown>, path: string): unknown {
  const tokens = path
    .split(".")
    .map((token) => token.trim())
    .filter((token) => token.length > 0);

  let current: unknown = root;
  for (const token of tokens) {
    if (Array.isArray(current)) {
      const index = Number(token);
      if (!Number.isInteger(index) || index < 0 || index >= current.length) {
        return undefined;
      }
      current = current[index];
      continue;
    }
    if (!current || typeof current !== "object") {
      return undefined;
    }
    current = (current as Record<string, unknown>)[token];
  }
  return current;
}

function getScopedVariableValue(
  context: WorldBookVariableRenderContext,
  scope: "message" | "chat" | "character" | "global",
  path: string
): unknown {
  if (scope === "global") {
    return getByDotPath(context.globalVariables, path);
  }
  if (scope === "character") {
    return getByDotPath(context.characterVariables, path);
  }
  return getByDotPath(context.localVariables, path);
}

export async function syncWorldBookVariableContext(
  chatId: string,
  entries: WorldBookVariableEntryLike[],
  callerCardId: string
): Promise<WorldBookVariableRenderContext | null> {
  return await withWorldBookStorage(async () => {
    const normalizedChatId = String(chatId || "").trim();
    if (!normalizedChatId) {
      return null;
    }

    const store = await readVariableStore();
    const chatState = store.chats[normalizedChatId] || createEmptyChatState();
    const normalizedCallerCardId = String(callerCardId || "").trim();
    const characterVariables =
      normalizedCallerCardId && store.character_variables[normalizedCallerCardId]
        ? store.character_variables[normalizedCallerCardId]
        : {};
    let changed = false;
    let stateDirty = !(normalizedChatId in store.chats);
    let characterStateDirty = false;
    const initTemplate = findInitVariableTemplate(entries, callerCardId);

    if (
      !chatState.local_variables[DEFAULT_VARIABLE_NAME] ||
      typeof chatState.local_variables[DEFAULT_VARIABLE_NAME] !== "object"
    ) {
      if (initTemplate) {
        chatState.local_variables[DEFAULT_VARIABLE_NAME] = cloneVariableTemplate(initTemplate);
        changed = true;
        stateDirty = true;
      }
    }

    if (
      normalizedCallerCardId &&
      (
        !characterVariables[DEFAULT_VARIABLE_NAME] ||
        typeof characterVariables[DEFAULT_VARIABLE_NAME] !== "object"
      ) &&
      initTemplate
    ) {
      characterVariables[DEFAULT_VARIABLE_NAME] = cloneVariableTemplate(initTemplate);
      store.character_variables[normalizedCallerCardId] = characterVariables;
      changed = true;
      characterStateDirty = true;
    }

    try {
      const messageResult = await Tools.Chat.getMessages(normalizedChatId, { order: "asc" });
      const messages = Array.isArray(messageResult?.messages)
        ? (messageResult.messages as ChatMessageInfoLike[])
        : [];

      const processedSet = new Set<number>(chatState.processed_message_timestamps || []);
      let latestTimestamp = chatState.last_scanned_timestamp || 0;

      for (const message of messages) {
        const timestamp = Number(message.timestamp || 0);
        if (!timestamp || timestamp <= latestTimestamp || processedSet.has(timestamp)) {
          continue;
        }

        const sender = String(message.sender || "");
        if (!isAssistantMessage(sender)) {
          latestTimestamp = Math.max(latestTimestamp, timestamp);
          processedSet.add(timestamp);
          stateDirty = true;
          continue;
        }

        const content = String(message.content || "");
        const operations = extractJsonPatchOperations(content);
        if (operations.length > 0) {
          const patchChanged = applyPatchOperations(chatState.local_variables, operations);
          changed = patchChanged || changed;
          stateDirty = patchChanged || stateDirty;
        }

        latestTimestamp = Math.max(latestTimestamp, timestamp);
        processedSet.add(timestamp);
        stateDirty = true;
      }

      chatState.last_scanned_timestamp = latestTimestamp;
      chatState.processed_message_timestamps = [...processedSet].sort((left, right) => left - right);
    } catch (error) {
      console.error("World book message history read failed", error);
      throw error;
    }

    store.chats[normalizedChatId] = chatState;
    if (normalizedCallerCardId && characterStateDirty) {
      store.character_variables[normalizedCallerCardId] = characterVariables;
    }
    if (changed || stateDirty || characterStateDirty) {
      await writeVariableStore(store);
    }

    return {
      localVariables: chatState.local_variables,
      characterVariables,
      globalVariables: store.global_variables
    };
  });
}

export function renderWorldBookContent(
  content: string,
  context: WorldBookVariableRenderContext | null
): string {
  if (!context) {
    return content;
  }

  let rendered = String(content || "");

  rendered = rendered.replace(
    /\{\{\s*format_(message|chat|character|global)_variable::([^}]+)\}\}/gi,
    (_match, scope: string, path: string) => {
      const value = getScopedVariableValue(
        context,
        (String(scope || "").trim().toLowerCase() as "message" | "chat" | "character" | "global"),
        String(path || "").trim()
      );
      return typeof value === "undefined" ? "" : formatVariableValue(sanitizeAssistantVariableValue(value));
    }
  );

  rendered = rendered.replace(
    /\{\{\s*get_(message|chat|character|global)_variable::([^}]+)\}\}/gi,
    (_match, scope: string, path: string) => {
      const value = getScopedVariableValue(
        context,
        (String(scope || "").trim().toLowerCase() as "message" | "chat" | "character" | "global"),
        String(path || "").trim()
      );
      return typeof value === "undefined" ? "" : stringifyAssistantVariableValue(value);
    }
  );

  rendered = rendered.replace(/\{\{\s*getvar::([^}]+)\}\}/gi, (_match, path: string) => {
    const value = getByDotPath(context.localVariables, String(path || "").trim());
    return typeof value === "undefined" ? "" : formatScalar(value);
  });

  rendered = rendered.replace(/\{\{\s*getglobalvar::([^}]+)\}\}/gi, (_match, path: string) => {
    const value = getByDotPath(context.globalVariables, String(path || "").trim());
    return typeof value === "undefined" ? "" : formatScalar(value);
  });

  rendered = rendered.replace(/\{\{\s*\.([A-Za-z][A-Za-z0-9_-]*)\s*\}\}/g, (_match, path: string) => {
    const value = getByDotPath(context.localVariables, String(path || "").trim());
    return typeof value === "undefined" ? "" : formatScalar(value);
  });

  rendered = rendered.replace(/\{\{\s*\$([A-Za-z][A-Za-z0-9_-]*)\s*\}\}/g, (_match, path: string) => {
    const value = getByDotPath(context.globalVariables, String(path || "").trim());
    return typeof value === "undefined" ? "" : formatScalar(value);
  });

  rendered = rendered.replace(/<%=\s*getvar\(\s*['"]([^'"]+)['"]\s*\)\s*%>/gi, (_match, path: string) => {
    const value = getByDotPath(context.localVariables, String(path || "").trim());
    return typeof value === "undefined" ? "" : formatScalar(value);
  });

  rendered = rendered.replace(
    /<%=\s*getglobalvar\(\s*['"]([^'"]+)['"]\s*\)\s*%>/gi,
    (_match, path: string) => {
      const value = getByDotPath(context.globalVariables, String(path || "").trim());
      return typeof value === "undefined" ? "" : formatScalar(value);
    }
  );

  return rendered;
}

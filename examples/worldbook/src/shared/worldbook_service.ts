import {
  withWorldBookStorage,
  readWorldBookEntries,
  readWorldBookGroups,
  writeWorldBookEntries,
  writeWorldBookGroups
} from "./worldbook_storage.js";

export interface WorldBookEntry {
  id: string;
  name: string;
  content: string;
  keywords: string[];
  is_regex: boolean;
  case_sensitive: boolean;
  always_active: boolean;
  enabled: boolean;
  priority: number;
  scan_depth: number;
  inject_target: "system" | "user" | "assistant";
  inject_position?: "prepend" | "append" | "at_depth";
  insertion_depth?: number;
  character_card_id: string;
  created_at: string;
  updated_at: string;
}

export interface WorldBookListEntry {
  id: string;
  name: string;
  enabled: boolean;
  always_active: boolean;
  priority: number;
  keywords: string[];
  is_regex: boolean;
  scan_depth: number;
  inject_target: "system" | "user" | "assistant";
  inject_position?: "prepend" | "append" | "at_depth";
  insertion_depth?: number;
  character_card_id: string;
}

export interface WorldBookMutationParams {
  id?: string;
  name?: string;
  content?: string;
  keywords?: string;
  is_regex?: boolean;
  case_sensitive?: boolean;
  always_active?: boolean;
  enabled?: boolean;
  priority?: number;
  scan_depth?: number;
  inject_target?: string;
  inject_position?: string;
  insertion_depth?: number;
  character_card_id?: string;
}

export interface WorldBookImportParams {
  path?: string;
  content?: string;
  character_card_id?: string;
}

export interface WorldBookImportResult {
  source_type: string;
  imported_count: number;
  warning_count: number;
  warnings: string[];
  entries: WorldBookEntry[];
}

export interface WorldBookError {
  code: string;
  message: string;
  details?: unknown;
}

interface CharacterCardSummary {
  id?: string;
  name?: string;
  description?: string;
  isDefault?: boolean;
}

export interface CharacterCardOption {
  id: string;
  name: string;
  description: string;
  isDefault: boolean;
}

interface NormalizedImportEntry {
  name: string;
  content: string;
  keywords: string[];
  is_regex: boolean;
  case_sensitive: boolean;
  always_active: boolean;
  enabled: boolean;
  priority: number;
  scan_depth: number;
  inject_target: "system" | "user" | "assistant";
  inject_position?: "prepend" | "append" | "at_depth";
  insertion_depth?: number;
}

interface ParsedImportPayload {
  sourceType: string;
  warnings: string[];
  entries: NormalizedImportEntry[];
}

function generateId(): string {
  return `wb_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

function splitKeywords(raw?: string): string[] {
  if (!raw) {
    return [];
  }
  return raw
    .split(/[,，]/)
    .map((keyword) => keyword.trim())
    .filter((keyword) => keyword.length > 0);
}

function normalizeNumber(value: unknown, fallbackValue: number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : fallbackValue;
}

function normalizeInjectTarget(value: unknown): "system" | "user" | "assistant" {
  if (value === "user") {
    return "user";
  }
  if (value === "assistant") {
    return "assistant";
  }
  return "system";
}

function normalizeInjectPosition(value: unknown): "prepend" | "append" | "at_depth" {
  if (value === "prepend") {
    return "prepend";
  }
  if (value === "at_depth") {
    return "at_depth";
  }
  return "append";
}

function worldBookError(code: string, message: string, details?: unknown): WorldBookError {
  return { code, message, details };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function hasOwn(record: Record<string, unknown>, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(record, key);
}

function isNullish(value: unknown): boolean {
  return value == null;
}

function toKeywordArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .map((item) => String(item || "").trim())
      .filter((item) => item.length > 0);
  }
  if (typeof value === "string") {
    return splitKeywords(value);
  }
  return [];
}

function requireStringField(record: Record<string, unknown>, key: string, fieldLabel: string): string {
  const value = record[key];
  if (typeof value !== "string") {
    throw worldBookError("INVALID_FIELD_TYPE", `${fieldLabel} 必须是字符串`);
  }
  return value.trim();
}

function readOptionalBooleanField(
  record: Record<string, unknown>,
  key: string,
  defaultValue: boolean,
  fieldLabel: string
): boolean {
  if (!hasOwn(record, key)) {
    return defaultValue;
  }
  const value = record[key];
  if (isNullish(value)) {
    return defaultValue;
  }
  if (typeof value !== "boolean") {
    throw worldBookError("INVALID_FIELD_TYPE", `${fieldLabel} 必须是布尔值`);
  }
  return value;
}

function readOptionalNumberField(
  record: Record<string, unknown>,
  key: string,
  defaultValue: number,
  fieldLabel: string
): number {
  if (!hasOwn(record, key)) {
    return defaultValue;
  }
  const value = record[key];
  if (isNullish(value)) {
    return defaultValue;
  }
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw worldBookError("INVALID_FIELD_TYPE", `${fieldLabel} 必须是数字`);
  }
  return value;
}

function readOptionalKeywordArrayField(
  record: Record<string, unknown>,
  key: string,
  fieldLabel: string
): string[] {
  if (!hasOwn(record, key)) {
    return [];
  }
  const value = record[key];
  if (isNullish(value)) {
    return [];
  }
  if (!Array.isArray(value)) {
    throw worldBookError("INVALID_FIELD_TYPE", `${fieldLabel} 必须是字符串数组`);
  }
  const keywords = value.map((item) => {
    if (typeof item !== "string") {
      throw worldBookError("INVALID_FIELD_TYPE", `${fieldLabel} 必须是字符串数组`);
    }
    return item.trim();
  });
  return keywords.filter((item) => item.length > 0);
}

function addWarning(warnings: string[], warning: string) {
  if (!warnings.includes(warning)) {
    warnings.push(warning);
  }
}

function addTemplateCompatibilityWarnings(content: string, warnings: string[]) {
  if (/\{\{\s*get_preset_variable::/i.test(content)) {
    addWarning(
      warnings,
      "导入源包含酒馆助手的 preset 变量读取宏，但当前宿主没有把活动预设上下文暴露给插件，这部分语法仍会保留原文。"
    );
  }

  if (/\{\{\s*format_preset_variable::/i.test(content)) {
    addWarning(
      warnings,
      "导入源包含酒馆助手的 preset 变量格式化宏，但当前宿主没有把活动预设上下文暴露给插件，这部分语法仍会保留原文。"
    );
  }

  if (/\{\{\s*(?:set|inc|dec)(?:global)?var::/i.test(content)) {
    addWarning(
      warnings,
      "导入源包含会修改变量的 SillyTavern 宏。当前插件只在回合结束后解析 <UpdateVariable>/<JSONPatch>，不会执行这些内联写变量宏。"
    );
  }

  if (/\{\{\s*[.$][A-Za-z][A-Za-z0-9_-]*\s*(?:\+\+|--|\+=|-=|\|\|=?|\?\?=?|==|!=|>=|<=|>|<|=)[^}]*\}\}/.test(content)) {
    addWarning(
      warnings,
      "导入源包含带运算或赋值的 SillyTavern 变量简写表达式。当前插件只支持只读取值写法（如 {{.var}} / {{$var}}），不会执行这类内联表达式。"
    );
  }
}

function resolveCharacterBookRoleTarget(rawRole: unknown, warnings: string[]): "system" | "user" | "assistant" {
  if (rawRole == null) {
    return "system";
  }

  if (rawRole === 0 || rawRole === "system") {
    return "system";
  }
  if (rawRole === 1 || rawRole === "user") {
    return "user";
  }
  if (rawRole === 2 || rawRole === "assistant" || rawRole === "char") {
    return "assistant";
  }

  addWarning(warnings, `角色卡世界书包含未识别的 role 值 ${String(rawRole)}，已按 system 注入处理。`);
  return "system";
}

function resolveCharacterBookPlacement(
  rawEntry: Record<string, unknown>,
  extensions: Record<string, unknown>,
  warnings: string[]
): Pick<NormalizedImportEntry, "inject_target" | "inject_position" | "insertion_depth"> {
  const position = typeof rawEntry.position === "string" ? rawEntry.position.trim().toLowerCase() : "";
  const roleValue = hasOwn(rawEntry, "role") ? rawEntry.role : extensions.role;
  const depthValue = hasOwn(rawEntry, "depth") ? rawEntry.depth : extensions.depth;

  switch (position) {
    case "":
      return {
        inject_target: "system",
        inject_position: "append",
        insertion_depth: 0
      };
    case "before_char":
      return {
        inject_target: "system",
        inject_position: "prepend",
        insertion_depth: 0
      };
    case "after_char":
      return {
        inject_target: "system",
        inject_position: "append",
        insertion_depth: 0
      };
    case "top_an":
      addWarning(warnings, "角色卡世界书位置 top_an 已近似映射为系统提示词顶部插入。");
      return {
        inject_target: "system",
        inject_position: "prepend",
        insertion_depth: 0
      };
    case "bottom_an":
    case "after_examples":
      addWarning(warnings, `角色卡世界书位置 ${position} 已近似映射为系统提示词尾部插入。`);
      return {
        inject_target: "system",
        inject_position: "append",
        insertion_depth: 0
      };
    case "at_depth":
    case "in_chat":
      return {
        inject_target: resolveCharacterBookRoleTarget(roleValue, warnings),
        inject_position: "at_depth",
        insertion_depth: normalizeNumber(depthValue, 0)
      };
    default:
      addWarning(warnings, `角色卡世界书位置 ${position} 当前未精确支持，已按系统提示词尾部插入。`);
      return {
        inject_target: "system",
        inject_position: "append",
        insertion_depth: 0
      };
  }
}

function buildImportedEntry(
  normalized: NormalizedImportEntry,
  characterCardId: string,
  now: string
): WorldBookEntry {
  return {
    id: generateId(),
    name: normalized.name,
    content: normalized.content,
    keywords: normalized.keywords,
    is_regex: normalized.is_regex,
    case_sensitive: normalized.case_sensitive,
    always_active: normalized.always_active,
    enabled: normalized.enabled,
    priority: normalized.priority,
    scan_depth: normalized.scan_depth,
    inject_target: normalized.inject_target,
    inject_position: normalizeInjectPosition(normalized.inject_position),
    insertion_depth: normalizeNumber(normalized.insertion_depth, 0),
    character_card_id: characterCardId,
    created_at: now,
    updated_at: now
  };
}

function parseOperitEntryArray(rawEntries: unknown[]): ParsedImportPayload | null {
  const warnings: string[] = [];
  const entries: NormalizedImportEntry[] = [];

  for (const rawEntry of rawEntries) {
    if (!isRecord(rawEntry)) {
      continue;
    }

    const name = requireStringField(rawEntry, "name", "Operit 条目 name");
    const content = requireStringField(rawEntry, "content", "Operit 条目 content");
    if (!name || !content) {
      continue;
    }

    entries.push({
      name,
      content,
      keywords: readOptionalKeywordArrayField(rawEntry, "keywords", "Operit 条目 keywords"),
      is_regex: readOptionalBooleanField(rawEntry, "is_regex", false, "Operit 条目 is_regex"),
      case_sensitive: readOptionalBooleanField(rawEntry, "case_sensitive", false, "Operit 条目 case_sensitive"),
      always_active: readOptionalBooleanField(rawEntry, "always_active", false, "Operit 条目 always_active"),
      enabled: readOptionalBooleanField(rawEntry, "enabled", true, "Operit 条目 enabled"),
      priority: readOptionalNumberField(rawEntry, "priority", 50, "Operit 条目 priority"),
      scan_depth: readOptionalNumberField(rawEntry, "scan_depth", 0, "Operit 条目 scan_depth"),
      inject_target: normalizeInjectTarget(rawEntry.inject_target),
      inject_position: normalizeInjectPosition(rawEntry.inject_position),
      insertion_depth: readOptionalNumberField(rawEntry, "insertion_depth", 0, "Operit 条目 insertion_depth")
    });
  }

  if (entries.length === 0) {
    return null;
  }

  return {
    sourceType: "operit_entries",
    warnings,
    entries
  };
}

function parseSillyTavernRecord(
  rawEntry: Record<string, unknown>,
  warnings: string[]
): NormalizedImportEntry | null {
  const name =
    typeof rawEntry.name === "string"
      ? rawEntry.name.trim()
      : typeof rawEntry.comment === "string"
        ? rawEntry.comment.trim()
        : typeof rawEntry.id === "string"
          ? rawEntry.id.trim()
          : "";
  const content = requireStringField(rawEntry, "content", "SillyTavern 条目 content");
  if (!name || !content) {
    return null;
  }
  addTemplateCompatibilityWarnings(content, warnings);

  const keywords = readOptionalKeywordArrayField(rawEntry, "key", "SillyTavern 条目 key");
  const alwaysActive = readOptionalBooleanField(rawEntry, "constant", false, "SillyTavern 条目 constant");
  if (keywords.length === 0 && !alwaysActive) {
    addWarning(warnings, "部分条目没有可导入的主关键词，且不是常驻条目，已跳过。");
    return null;
  }

  const secondaryKeys = readOptionalKeywordArrayField(rawEntry, "keysecondary", "SillyTavern 条目 keysecondary");
  if (secondaryKeys.length > 0) {
    addWarning(warnings, "导入源包含次级关键词 keysecondary，当前版本未原样支持，已忽略。");
  }
  if (readOptionalBooleanField(rawEntry, "selective", false, "SillyTavern 条目 selective")) {
    addWarning(warnings, "导入源包含 selective 逻辑，当前版本未原样支持，已按主关键词导入。");
  }
  if (rawEntry.role != null) {
    addWarning(warnings, "导入源包含额外注入位置字段 role，当前版本未映射，已按系统提示词导入。");
  }

  return {
    name,
    content,
    keywords,
    is_regex: readOptionalBooleanField(rawEntry, "use_regex", false, "SillyTavern 条目 use_regex"),
    case_sensitive: readOptionalBooleanField(rawEntry, "caseSensitive", false, "SillyTavern 条目 caseSensitive"),
    always_active: alwaysActive,
    enabled: !readOptionalBooleanField(rawEntry, "disable", false, "SillyTavern 条目 disable"),
    priority: hasOwn(rawEntry, "display_index")
      ? readOptionalNumberField(rawEntry, "display_index", 50, "SillyTavern 条目 display_index")
      : readOptionalNumberField(rawEntry, "order", 50, "SillyTavern 条目 order"),
    scan_depth: hasOwn(rawEntry, "scanDepth")
      ? readOptionalNumberField(rawEntry, "scanDepth", 0, "SillyTavern 条目 scanDepth")
      : readOptionalNumberField(rawEntry, "depth", 0, "SillyTavern 条目 depth"),
    inject_target: "system",
    inject_position: "append",
    insertion_depth: 0
  };
}

function parseSillyTavernWorldBook(raw: Record<string, unknown>): ParsedImportPayload | null {
  if (!isRecord(raw.entries)) {
    return null;
  }

  const warnings: string[] = [];
  const entries: NormalizedImportEntry[] = [];

  for (const rawEntry of Object.values(raw.entries)) {
    if (!isRecord(rawEntry)) {
      continue;
    }
    const parsedEntry = parseSillyTavernRecord(rawEntry, warnings);
    if (parsedEntry) {
      entries.push(parsedEntry);
    }
  }

  if (entries.length === 0) {
    return null;
  }

  entries.sort((left, right) => right.priority - left.priority);
  return {
    sourceType: "sillytavern_worldbook",
    warnings,
    entries
  };
}

function parseCharacterBookEntry(
  rawEntry: Record<string, unknown>,
  warnings: string[]
): NormalizedImportEntry | null {
  const name =
    typeof rawEntry.comment === "string"
      ? rawEntry.comment.trim()
      : typeof rawEntry.name === "string"
        ? rawEntry.name.trim()
        : typeof rawEntry.id === "string"
          ? rawEntry.id.trim()
          : "";
  const content = requireStringField(rawEntry, "content", "character_book 条目 content");
  if (!name || !content) {
    return null;
  }
  addTemplateCompatibilityWarnings(content, warnings);

  const keywords = readOptionalKeywordArrayField(rawEntry, "keys", "character_book 条目 keys");
  const alwaysActive = readOptionalBooleanField(rawEntry, "constant", false, "character_book 条目 constant");
  if (keywords.length === 0 && !alwaysActive) {
    addWarning(warnings, "部分角色卡世界书条目没有主关键词，且不是常驻条目，已跳过。");
    return null;
  }

  const secondaryKeys = readOptionalKeywordArrayField(rawEntry, "secondary_keys", "character_book 条目 secondary_keys");
  if (secondaryKeys.length > 0) {
    addWarning(warnings, "角色卡世界书包含 secondary_keys，当前版本未原样支持，已忽略。");
  }
  if (readOptionalBooleanField(rawEntry, "selective", false, "character_book 条目 selective")) {
    addWarning(warnings, "角色卡世界书包含 selective 逻辑，当前版本未原样支持，已按主关键词导入。");
  }

  const extensions = isRecord(rawEntry.extensions) ? rawEntry.extensions : {};
  const placement = resolveCharacterBookPlacement(rawEntry, extensions, warnings);
  return {
    name,
    content,
    keywords,
    is_regex: readOptionalBooleanField(rawEntry, "use_regex", false, "character_book 条目 use_regex"),
    case_sensitive: readOptionalBooleanField(extensions, "case_sensitive", false, "character_book 条目 extensions.case_sensitive"),
    always_active: alwaysActive,
    enabled: readOptionalBooleanField(rawEntry, "enabled", true, "character_book 条目 enabled"),
    priority: readOptionalNumberField(rawEntry, "insertion_order", 50, "character_book 条目 insertion_order"),
    scan_depth: readOptionalNumberField(extensions, "depth", 0, "character_book 条目 extensions.depth"),
    inject_target: placement.inject_target,
    inject_position: placement.inject_position,
    insertion_depth: placement.insertion_depth
  };
}

function parseCharacterBook(rawBook: Record<string, unknown>): ParsedImportPayload | null {
  if (!Array.isArray(rawBook.entries)) {
    return null;
  }

  const warnings: string[] = [];
  const entries: NormalizedImportEntry[] = [];

  for (const rawEntry of rawBook.entries) {
    if (!isRecord(rawEntry)) {
      continue;
    }
    const parsedEntry = parseCharacterBookEntry(rawEntry, warnings);
    if (parsedEntry) {
      entries.push(parsedEntry);
    }
  }

  if (entries.length === 0) {
    return null;
  }

  entries.sort((left, right) => right.priority - left.priority);
  return {
    sourceType: "character_book",
    warnings,
    entries
  };
}

function parseImportedWorldBookPayload(raw: unknown): ParsedImportPayload {
  if (Array.isArray(raw)) {
    const parsedOperit = parseOperitEntryArray(raw);
    if (parsedOperit) {
      return parsedOperit;
    }
  }

  if (!isRecord(raw)) {
    throw worldBookError("INVALID_WORLD_BOOK_JSON", "导入文件不是有效的世界书 JSON 结构");
  }

  const embeddedCharacterBook = isRecord(raw.character_book)
    ? parseCharacterBook(raw.character_book)
    : null;
  if (embeddedCharacterBook) {
    return embeddedCharacterBook;
  }

  const wrappedOriginalData = isRecord(raw.originalData) ? raw.originalData : null;
  if (wrappedOriginalData) {
    const parsedOriginalCharacterBook = parseCharacterBook(wrappedOriginalData);
    if (parsedOriginalCharacterBook) {
      return parsedOriginalCharacterBook;
    }

    const parsedOriginalWorldBook = parseSillyTavernWorldBook(wrappedOriginalData);
    if (parsedOriginalWorldBook) {
      return parsedOriginalWorldBook;
    }
  }

  const parsedWorldBook = parseSillyTavernWorldBook(raw);
  if (parsedWorldBook) {
    return parsedWorldBook;
  }

  const parsedCharacterBook = parseCharacterBook(raw);
  if (parsedCharacterBook) {
    return parsedCharacterBook;
  }

  if (Array.isArray(raw.entries)) {
    const parsedEntries = parseCharacterBook(raw);
    if (parsedEntries) {
      return parsedEntries;
    }
  }

  throw worldBookError(
    "UNSUPPORTED_WORLD_BOOK_FORMAT",
    "暂不支持该世界书格式，当前支持 Operit、SillyTavern 独立 lorebook，以及角色卡内嵌 character_book"
  );
}

async function loadEntries(): Promise<WorldBookEntry[]> {
  return await readWorldBookEntries<WorldBookEntry>();
}

async function saveEntries(entries: WorldBookEntry[]): Promise<void> {
  await writeWorldBookEntries(entries);
}

function requireEntryId(id: string | undefined): string {
  const normalizedId = String(id || "").trim();
  if (!normalizedId) {
    throw worldBookError("INVALID_ENTRY_ID", "条目 ID 不能为空");
  }
  return normalizedId;
}

function findEntryIndex(entries: WorldBookEntry[], id: string | undefined): number {
  const targetId = requireEntryId(id);
  const index = entries.findIndex((entry) => entry.id === targetId);
  if (index === -1) {
    throw worldBookError("ENTRY_NOT_FOUND", `条目不存在: ${targetId}`);
  }
  return index;
}

export function toWorldBookListEntry(entry: WorldBookEntry): WorldBookListEntry {
  return {
    id: entry.id,
    name: entry.name,
    enabled: entry.enabled,
    always_active: entry.always_active,
    priority: entry.priority,
    keywords: entry.keywords || [],
    is_regex: entry.is_regex || false,
    scan_depth: entry.scan_depth ?? 0,
    inject_target: entry.inject_target || "system",
    inject_position: normalizeInjectPosition(entry.inject_position),
    insertion_depth: normalizeNumber(entry.insertion_depth, 0),
    character_card_id: entry.character_card_id || ""
  };
}

export async function listWorldBookEntries(): Promise<WorldBookListEntry[]> {
  return await withWorldBookStorage(async () => {
    const entries = await loadEntries();
    return entries
      .map(toWorldBookListEntry)
      .sort((left, right) => right.priority - left.priority);
  });
}

export async function getWorldBookEntry(id: string): Promise<WorldBookEntry> {
  return await withWorldBookStorage(async () => {
    const entries = await loadEntries();
    return entries[findEntryIndex(entries, id)];
  });
}

export async function createWorldBookEntry(params: WorldBookMutationParams): Promise<WorldBookEntry> {
  return await withWorldBookStorage(async () => {
    const entries = await loadEntries();
    const now = new Date().toISOString();
    const injectTarget = normalizeInjectTarget(params.inject_target);
    const injectPosition =
      injectTarget === "assistant" ? "at_depth" : normalizeInjectPosition(params.inject_position);
    const entry: WorldBookEntry = {
      id: generateId(),
      name: String(params.name || ""),
      content: String(params.content || ""),
      keywords: splitKeywords(params.keywords),
      is_regex: params.is_regex === true,
      case_sensitive: params.case_sensitive === true,
      always_active: params.always_active === true,
      enabled: params.enabled !== false,
      priority: normalizeNumber(params.priority, 50),
      scan_depth: normalizeNumber(params.scan_depth, 0),
      inject_target: injectTarget,
      inject_position: injectPosition,
      insertion_depth: normalizeNumber(params.insertion_depth, 0),
      character_card_id: String(params.character_card_id || "").trim(),
      created_at: now,
      updated_at: now
    };

    entries.push(entry);
    await saveEntries(entries);
    return entry;
  });
}

export async function importWorldBookEntries(params: WorldBookImportParams): Promise<WorldBookImportResult> {
  return await withWorldBookStorage(async () => {
    const rawContent = String(params.content || "").trim();
    let sourceContent = rawContent;

    if (!sourceContent) {
      const path = String(params.path || "").trim();
      if (!path) {
        throw worldBookError("IMPORT_PATH_REQUIRED", "导入路径不能为空");
      }
      const fileResult = await Tools.Files.read(path);
      sourceContent = String(fileResult?.content || "").trim();
      if (!sourceContent) {
        throw worldBookError("IMPORT_FILE_EMPTY", "导入文件为空");
      }
    }

    let parsedJson: unknown;
    try {
      parsedJson = JSON.parse(sourceContent);
    } catch (error) {
      throw worldBookError(
        "INVALID_JSON",
        `导入文件不是有效的 JSON: ${(error as { message: string }).message}`
      );
    }

    const parsedPayload = parseImportedWorldBookPayload(parsedJson);
    if (parsedPayload.entries.length === 0) {
      throw worldBookError("NO_IMPORTABLE_ENTRIES", "没有可导入的世界书条目");
    }

    const entries = await loadEntries();
    const now = new Date().toISOString();
    const characterCardId = String(params.character_card_id || "").trim();
    const importedEntries = parsedPayload.entries.map((entry) =>
      buildImportedEntry(entry, characterCardId, now)
    );

    entries.push(...importedEntries);
    await saveEntries(entries);

    return {
      source_type: parsedPayload.sourceType,
      imported_count: importedEntries.length,
      warning_count: parsedPayload.warnings.length,
      warnings: parsedPayload.warnings,
      entries: importedEntries
    };
  });
}

export async function updateWorldBookEntry(params: WorldBookMutationParams): Promise<WorldBookEntry> {
  return await withWorldBookStorage(async () => {
    const entries = await loadEntries();
    const index = findEntryIndex(entries, params.id);
    const nextEntry = { ...entries[index] };

    if (params.name != null) {
      nextEntry.name = String(params.name);
    }
    if (params.content != null) {
      nextEntry.content = String(params.content);
    }
    if (params.keywords != null) {
      nextEntry.keywords = splitKeywords(params.keywords);
    }
    if (params.is_regex != null) {
      nextEntry.is_regex = params.is_regex === true;
    }
    if (params.case_sensitive != null) {
      nextEntry.case_sensitive = params.case_sensitive === true;
    }
    if (params.always_active != null) {
      nextEntry.always_active = params.always_active === true;
    }
    if (params.enabled != null) {
      nextEntry.enabled = params.enabled !== false;
    }
    if (params.priority != null) {
      nextEntry.priority = normalizeNumber(params.priority, nextEntry.priority);
    }
    if (params.scan_depth != null) {
      nextEntry.scan_depth = normalizeNumber(params.scan_depth, nextEntry.scan_depth);
    }
    if (params.inject_target != null) {
      nextEntry.inject_target = normalizeInjectTarget(params.inject_target);
    }
    if (params.inject_position != null) {
      nextEntry.inject_position = normalizeInjectPosition(params.inject_position);
    }
    if (params.insertion_depth != null) {
      nextEntry.insertion_depth = normalizeNumber(params.insertion_depth, nextEntry.insertion_depth ?? 0);
    }
    if (nextEntry.inject_target === "assistant") {
      nextEntry.inject_position = "at_depth";
    }
    if (params.character_card_id != null) {
      nextEntry.character_card_id = String(params.character_card_id || "").trim();
    }

    nextEntry.updated_at = new Date().toISOString();
    entries[index] = nextEntry;
    await saveEntries(entries);
    return nextEntry;
  });
}

export async function deleteWorldBookEntry(id: string): Promise<WorldBookEntry> {
  return await withWorldBookStorage(async () => {
    const entries = await loadEntries();
    const groups = await readWorldBookGroups<WorldBookGroup>();
    const index = findEntryIndex(entries, id);
    const [removed] = entries.splice(index, 1);
    let groupsDirty = false;
    for (const group of groups) {
      const remaining = group.entry_ids.filter((entryId) => entryId !== id);
      if (remaining.length !== group.entry_ids.length) {
        group.entry_ids = remaining;
        groupsDirty = true;
      }
    }
    // References first: an interrupted later write may retain an ungrouped entry, not lose other entries.
    if (groupsDirty) await writeWorldBookGroups(groups);
    await saveEntries(entries);
    return removed;
  });
}

export async function toggleWorldBookEntry(id: string): Promise<WorldBookListEntry> {
  return await withWorldBookStorage(async () => {
    const entries = await loadEntries();
    const index = findEntryIndex(entries, id);
    const nextEntry: WorldBookEntry = {
      ...entries[index],
      enabled: !entries[index].enabled,
      updated_at: new Date().toISOString()
    };
    entries[index] = nextEntry;
    await saveEntries(entries);
    return toWorldBookListEntry(nextEntry);
  });
}

export async function listWorldBookCharacterCards(): Promise<CharacterCardOption[]> {
  const result = await Tools.Chat.listCharacterCards();
  const cards = Array.isArray(result?.cards) ? (result.cards as CharacterCardSummary[]) : [];
  return cards
    .map((card) => ({
      id: String(card?.id || "").trim(),
      name: String(card?.name || "").trim(),
      description: String(card?.description || "").trim(),
      isDefault: card?.isDefault === true
    }))
    .filter((card) => !!card.id);
}

// --- 分组类型与 CRUD ---

export interface WorldBookGroup {
  id: string;
  name: string;
  entry_ids: string[];
  character_card_ids: string[];
  created_at: string;
  updated_at: string;
}

export interface WorldBookGroupMutationParams {
  id: string;
  name?: string;
  entry_ids?: string[];
  character_card_ids?: string[];
}

export interface WorldBookGroupCreateParams {
  name: string;
  entry_ids: string[];
  character_card_ids: string[];
}

function generateGroupId(): string {
  return `wg_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

function normalizeWorldBookGroupName(name: string): string {
  const normalized = name.trim();
  if (normalized.length === 0) {
    throw worldBookError("GROUP_NAME_REQUIRED", "分组名称不能为空");
  }
  return normalized;
}

export async function listWorldBookGroups(): Promise<WorldBookGroup[]> {
  return await withWorldBookStorage(async () => {
    const groups = await readWorldBookGroups<WorldBookGroup>();
    return groups.sort((a, b) => a.name.localeCompare(b.name));
  });
}

export async function createWorldBookGroup(params: WorldBookGroupCreateParams): Promise<WorldBookGroup> {
  return await withWorldBookStorage(async () => {
    const groups = await readWorldBookGroups<WorldBookGroup>();
    const now = new Date().toISOString();
    const group: WorldBookGroup = {
      id: generateGroupId(),
      name: normalizeWorldBookGroupName(params.name),
      entry_ids: params.entry_ids,
      character_card_ids: params.character_card_ids,
      created_at: now,
      updated_at: now
    };
    groups.push(group);
    await writeWorldBookGroups(groups);
    return group;
  });
}

export async function deleteWorldBookGroup(id: string, deleteEntries: boolean): Promise<{ removedGroup: WorldBookGroup; removedEntries: WorldBookEntry[] }> {
  return await withWorldBookStorage(async () => {
    const groups = await readWorldBookGroups<WorldBookGroup>();
    const index = groups.findIndex((group) => group.id === id);
    if (index === -1) throw worldBookError("GROUP_NOT_FOUND", "分组不存在");
    const [removedGroup] = groups.splice(index, 1);
    const entries = deleteEntries ? await loadEntries() : [];
    const referenced = new Set(groups.flatMap((group) => group.entry_ids));
    const removedEntries = entries.filter((entry) => removedGroup.entry_ids.includes(entry.id) && !referenced.has(entry.id));
    const removedIds = new Set(removedEntries.map((entry) => entry.id));
    await writeWorldBookGroups(groups);
    if (removedEntries.length > 0) {
      await saveEntries(entries.filter((entry) => !removedIds.has(entry.id)));
    }
    return { removedGroup, removedEntries };
  });
}

export async function copyEntryToGroup(entryId: string, targetGroupId: string): Promise<{ newEntry: WorldBookEntry }> {
  return await withWorldBookStorage(async () => {
    const entries = await loadEntries();
    const groups = await readWorldBookGroups<WorldBookGroup>();
    const target = targetGroupId ? groups.find((group) => group.id === targetGroupId) : undefined;
    if (targetGroupId && !target) throw worldBookError("GROUP_NOT_FOUND", "分组不存在");
    const originalEntry = entries[findEntryIndex(entries, entryId)];
    const now = new Date().toISOString();
    const newEntry: WorldBookEntry = {
      ...originalEntry,
      id: generateId(),
      name: `${originalEntry.name}(副本)`,
      created_at: now,
      updated_at: now
    };
    entries.push(newEntry);
    await saveEntries(entries);
    if (target) {
      target.entry_ids.push(newEntry.id);
      target.updated_at = now;
      await writeWorldBookGroups(groups);
    }
    return { newEntry };
  });
}

export async function updateWorldBookGroup(params: WorldBookGroupMutationParams): Promise<WorldBookGroup> {
  return await withWorldBookStorage(async () => {
    const groups = await readWorldBookGroups<WorldBookGroup>();
    const index = groups.findIndex((g) => g.id === params.id);
    if (index === -1) throw worldBookError("GROUP_NOT_FOUND", "分组不存在");
    const now = new Date().toISOString();
    if (params.name != null) {
      groups[index].name = normalizeWorldBookGroupName(params.name);
    }
    if (params.entry_ids != null) {
      groups[index].entry_ids = params.entry_ids;
    }
    if (params.character_card_ids != null) {
      groups[index].character_card_ids = params.character_card_ids;
    }
    groups[index].updated_at = now;
    await writeWorldBookGroups(groups);
    return groups[index];
  });
}

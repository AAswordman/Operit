"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.systemPromptHook = systemPromptHook;
exports.finalizeHook = finalizeHook;
exports.registerToolPkg = registerToolPkg;
const index_ui_js_1 = __importDefault(require("./ui/worldbook_manager/index.ui.js"));
const worldbook_rpc_js_1 = require("./shared/worldbook_rpc.js");
const worldbook_storage_js_1 = require("./shared/worldbook_storage.js");
const worldbook_variables_js_1 = require("./shared/worldbook_variables.js");
const WORLDBOOK_ROUTE = "toolpkg:com.operit.worldbook:ui:worldbook_manager";
function matchesEntry(entry, text) {
    if (!entry.keywords || entry.keywords.length === 0) {
        return false;
    }
    for (const keyword of entry.keywords) {
        if (!keyword) {
            continue;
        }
        try {
            if (entry.is_regex) {
                if (new RegExp(keyword, entry.case_sensitive ? "g" : "gi").test(text)) {
                    return true;
                }
                continue;
            }
            if (entry.case_sensitive) {
                if (text.includes(keyword)) {
                    return true;
                }
            }
            else if (text.toLowerCase().includes(keyword.toLowerCase())) {
                return true;
            }
        }
        catch (_error) {
            // Ignore malformed regex entries instead of breaking prompt assembly.
        }
    }
    return false;
}
function buildInjection(entries) {
    const parts = ["<worldbook>"];
    for (const entry of entries) {
        parts.push(`<entry name="${entry.name}">`);
        parts.push(entry.content);
        parts.push("</entry>");
    }
    parts.push("</worldbook>");
    return parts.join("\n");
}
function normalizeInjectPosition(entry) {
    return entry.inject_position === "prepend" || entry.inject_position === "at_depth"
        ? entry.inject_position
        : "append";
}
function applyTextInjection(baseText, prependEntries, appendEntries) {
    const parts = [];
    if (prependEntries.length > 0) {
        parts.push(buildInjection(prependEntries));
    }
    if (baseText) {
        parts.push(baseText);
    }
    if (appendEntries.length > 0) {
        parts.push(buildInjection(appendEntries));
    }
    return parts.join("\n");
}
function splitEntriesByPosition(entries) {
    const prependEntries = [];
    const appendEntries = [];
    for (const entry of entries) {
        if (normalizeInjectPosition(entry) === "prepend") {
            prependEntries.push(entry);
        }
        else {
            appendEntries.push(entry);
        }
    }
    return { prependEntries, appendEntries };
}
function toPromptTurnKind(entry) {
    if (entry.inject_target === "assistant") {
        return "ASSISTANT";
    }
    if (entry.inject_target === "user") {
        return "USER";
    }
    return "SYSTEM";
}
function insertAtDepthEntries(history, entries) {
    if (entries.length === 0) {
        return history;
    }
    const grouped = new Map();
    for (const entry of entries) {
        const depth = Math.max(0, Number(entry.insertion_depth ?? 0) || 0);
        const kind = toPromptTurnKind(entry);
        const key = `${kind}:${depth}`;
        const group = grouped.get(key);
        if (group) {
            group.entries.push(entry);
            continue;
        }
        grouped.set(key, { depth, kind, entries: [entry] });
    }
    const nextHistory = [...history];
    const groups = [...grouped.values()].sort((left, right) => right.depth - left.depth);
    for (const group of groups) {
        const insertIndex = Math.max(0, nextHistory.length - group.depth);
        nextHistory.splice(insertIndex, 0, {
            kind: group.kind,
            content: buildInjection(group.entries)
        });
    }
    return nextHistory;
}
function matchesCharacterCard(entry, callerCardId) {
    const targetCardId = (entry.character_card_id || "").trim();
    if (!targetCardId) {
        return true;
    }
    return !!callerCardId && callerCardId === targetCardId;
}
async function resolveCurrentCharacterCardId(event) {
    try {
        const directCardId = typeof getCallerCardId === "function" ? getCallerCardId() : undefined;
        if (directCardId && String(directCardId).trim()) {
            return String(directCardId).trim();
        }
    }
    catch (_error) {
        // Ignore direct getter failure and fall back to chat lookup.
    }
    try {
        const chatId = event?.eventPayload?.chatId;
        if (!chatId) {
            return "";
        }
        const chatResult = await Tools.Chat.findChat({
            query: String(chatId),
            match: "exact",
            index: 0
        });
        const cardName = String(chatResult?.chat?.characterCardName || "").trim();
        if (!cardName) {
            return "";
        }
        const cardResult = await Tools.Chat.listCharacterCards();
        const cards = Array.isArray(cardResult?.cards)
            ? cardResult.cards
            : [];
        const matchedCard = cards.find((card) => String(card?.name || "").trim() === cardName);
        return matchedCard?.id ? String(matchedCard.id).trim() : "";
    }
    catch (_error) {
        return "";
    }
}
async function readEnabledEntries() {
    try {
        const parsed = await (0, worldbook_storage_js_1.readWorldBookEntries)();
        const enabledEntries = parsed.filter((entry) => entry && entry.enabled !== false);
        enabledEntries.sort((left, right) => (right.priority || 50) - (left.priority || 50));
        return enabledEntries;
    }
    catch (_error) {
        return [];
    }
}
function renderEntries(entries, variableContext) {
    if (!variableContext) {
        return entries;
    }
    return entries.map((entry) => ({
        ...entry,
        content: (0, worldbook_variables_js_1.renderWorldBookContent)(String(entry.content || ""), variableContext)
    }));
}
async function resolveVariableContext(event, callerCardId) {
    const chatId = String(event?.eventPayload?.chatId || "").trim();
    if (!chatId) {
        return null;
    }
    try {
        const allEntries = await (0, worldbook_storage_js_1.readWorldBookEntries)();
        return await (0, worldbook_variables_js_1.syncWorldBookVariableContext)(chatId, allEntries, callerCardId);
    }
    catch (_error) {
        return null;
    }
}
async function systemPromptHook(event) {
    const stage = event.eventName || event.event;
    if (stage !== "after_compose_system_prompt") {
        return null;
    }
    const enabledEntries = await readEnabledEntries();
    const callerCardId = await resolveCurrentCharacterCardId(event);
    const variableContext = await resolveVariableContext(event, callerCardId);
    const hitEntries = enabledEntries.filter((entry) => entry.always_active &&
        entry.inject_target === "system" &&
        normalizeInjectPosition(entry) !== "at_depth" &&
        matchesCharacterCard(entry, callerCardId));
    if (hitEntries.length === 0) {
        return null;
    }
    const currentPrompt = event.eventPayload?.systemPrompt || "";
    const renderedEntries = renderEntries(hitEntries, variableContext);
    const { prependEntries, appendEntries } = splitEntriesByPosition(renderedEntries);
    return { systemPrompt: applyTextInjection(currentPrompt, prependEntries, appendEntries) };
}
async function finalizeHook(event) {
    const stage = event.eventName || event.event;
    if (stage !== "before_finalize_prompt") {
        return null;
    }
    const enabledEntries = await readEnabledEntries();
    const promptEntries = enabledEntries.filter((entry) => entry.inject_target === "user" || normalizeInjectPosition(entry) === "at_depth");
    const keywordEntries = enabledEntries.filter((entry) => !entry.always_active);
    const payload = event.eventPayload || {};
    const history = (payload.preparedHistory || payload.chatHistory || []);
    const callerCardId = await resolveCurrentCharacterCardId(event);
    const variableContext = await resolveVariableContext(event, callerCardId);
    const hitSystemEntries = [];
    const hitUserEntries = [];
    const hitChatEntries = [];
    for (const entry of promptEntries) {
        if (!matchesCharacterCard(entry, callerCardId)) {
            continue;
        }
        if (entry.always_active) {
            if (normalizeInjectPosition(entry) === "at_depth" || entry.inject_target === "assistant") {
                hitChatEntries.push(entry);
            }
            else {
                hitUserEntries.push(entry);
            }
        }
    }
    for (const entry of keywordEntries) {
        if (!matchesCharacterCard(entry, callerCardId)) {
            continue;
        }
        const texts = [];
        if (payload.rawInput) {
            texts.push(payload.rawInput);
        }
        if (payload.processedInput && payload.processedInput !== payload.rawInput) {
            texts.push(payload.processedInput);
        }
        const depth = entry.scan_depth != null ? entry.scan_depth : 0;
        if (depth > 0) {
            const userTurns = history.filter((turn) => turn && turn.kind === "USER" && turn.content);
            const startIndex = Math.max(0, userTurns.length - depth);
            for (let index = startIndex; index < userTurns.length; index += 1) {
                const turn = userTurns[index];
                if (turn?.content) {
                    texts.push(turn.content);
                }
            }
        }
        const scanText = texts.join("\n");
        if (scanText && matchesEntry(entry, scanText)) {
            if (normalizeInjectPosition(entry) === "at_depth" || entry.inject_target === "assistant") {
                hitChatEntries.push(entry);
            }
            else if (entry.inject_target === "user") {
                hitUserEntries.push(entry);
            }
            else {
                hitSystemEntries.push(entry);
            }
        }
    }
    if (hitSystemEntries.length === 0 && hitUserEntries.length === 0 && hitChatEntries.length === 0) {
        return null;
    }
    let nextHistory = [...history];
    let nextProcessedInput = String(payload.processedInput || payload.rawInput || "");
    if (hitSystemEntries.length > 0) {
        const renderedSystemEntries = renderEntries(hitSystemEntries, variableContext);
        const { prependEntries, appendEntries } = splitEntriesByPosition(renderedSystemEntries);
        let injected = false;
        const sysNext = [];
        for (const turn of nextHistory) {
            if (!injected && turn.kind === "SYSTEM") {
                const nextContent = applyTextInjection(turn.content || "", prependEntries, appendEntries);
                sysNext.push({ ...turn, content: nextContent });
                injected = true;
                continue;
            }
            sysNext.push(turn);
        }
        if (!injected) {
            sysNext.unshift({
                kind: "SYSTEM",
                content: applyTextInjection("", prependEntries, appendEntries)
            });
        }
        nextHistory = sysNext;
    }
    if (hitUserEntries.length > 0) {
        const renderedUserEntries = renderEntries(hitUserEntries, variableContext);
        const { prependEntries, appendEntries } = splitEntriesByPosition(renderedUserEntries);
        nextProcessedInput = applyTextInjection(nextProcessedInput, prependEntries, appendEntries);
    }
    if (hitChatEntries.length > 0) {
        nextHistory = insertAtDepthEntries(nextHistory, renderEntries(hitChatEntries, variableContext));
    }
    return {
        preparedHistory: nextHistory,
        processedInput: nextProcessedInput
    };
}
function registerToolPkg() {
    (0, worldbook_rpc_js_1.registerWorldBookStorageService)();
    ToolPkg.registerUiRoute({
        id: "worldbook_manager",
        route: WORLDBOOK_ROUTE,
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
        params: {},
        title: {
            zh: "世界书管理",
            en: "World Book Manager"
        }
    });
    ToolPkg.registerNavigationEntry({
        id: "worldbook_manager_details",
        route: WORLDBOOK_ROUTE,
        surface: "package_details",
        title: {
            zh: "世界书管理",
            en: "World Book Manager"
        },
        icon: Icons.Book,
        order: 210
    });
    ToolPkg.registerSystemPromptComposeHook({
        id: "worldbook_always_active",
        function: systemPromptHook
    });
    ToolPkg.registerPromptFinalizeHook({
        id: "worldbook_keyword_inject",
        function: finalizeHook
    });
    return true;
}

"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.onToolXmlRender = onToolXmlRender;
exports.onToolResultXmlRender = onToolResultXmlRender;
const subagent_status_ui_js_1 = __importDefault(require("../ui/subagent-status.ui.js"));
const subagent_i18n_js_1 = require("../shared/subagent_i18n.js");
const SUBAGENT_TOOL_NAME = "subagent_run";
function normalizePayload(input) {
    const record = input;
    if (record && record.eventPayload && typeof record.eventPayload === "object") {
        return record.eventPayload;
    }
    return record || {};
}
function decodeXml(value) {
    return String(value || "")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, "\"")
        .replace(/&#39;/g, "'")
        .replace(/&amp;/g, "&");
}
function maybeParseJsonString(value) {
    const text = String(value || "").trim();
    if (!text) {
        return null;
    }
    try {
        const parsed = JSON.parse(text);
        return typeof parsed === "string" ? parsed : null;
    }
    catch (_error) {
        return null;
    }
}
function unwrapSerializedString(value) {
    let current = String(value || "").trim();
    let depth = 0;
    while (current && depth < 3) {
        const parsed = maybeParseJsonString(current);
        if (parsed === null) {
            break;
        }
        current = String(parsed || "").trim();
        depth += 1;
    }
    return current;
}
function normalizeProtocolText(value) {
    return decodeXml(unwrapSerializedString(value))
        .replace(/\\"/g, "\"")
        .replace(/\\n/g, "\n")
        .replace(/\\r/g, "\r")
        .replace(/\\t/g, "\t")
        .trim();
}
function normalizeWhitespace(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
}
function summarizeText(value, maxLength) {
    const text = normalizeWhitespace(decodeXml(value));
    if (!text) {
        return "";
    }
    if (text.length <= maxLength) {
        return text;
    }
    return text.slice(0, Math.max(0, maxLength - 3)).trimEnd() + "...";
}
function parseTagAttributes(fragment) {
    const attrs = {};
    const normalizedFragment = normalizeProtocolText(fragment);
    const regex = /([A-Za-z_][A-Za-z0-9_\-]*)\s*=\s*"([^"]*)"/g;
    let match;
    while ((match = regex.exec(normalizedFragment)) !== null) {
        attrs[match[1]] = decodeXml(match[2]);
    }
    return attrs;
}
function parseParamTags(xmlContent) {
    const params = {};
    const regex = /<param\s+name="([^"]+)">([\s\S]*?)<\/param>/gi;
    let match;
    while ((match = regex.exec(String(xmlContent || ""))) !== null) {
        params[match[1]] = decodeXml(match[2]);
    }
    return params;
}
function parseJsonObject(raw) {
    const text = String(raw || "").trim();
    if (!text) {
        return {};
    }
    try {
        const parsed = JSON.parse(text);
        if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
            return parsed;
        }
    }
    catch (_error) { }
    return {};
}
function shortToolName(toolName) {
    const raw = String(toolName || "").trim();
    if (!raw) {
        return "";
    }
    if (raw.includes(":")) {
        return raw.substring(raw.lastIndexOf(":") + 1).trim();
    }
    return raw;
}
function isSubagentToolName(toolName) {
    return shortToolName(toolName) === SUBAGENT_TOOL_NAME;
}
function resolveToolCallInfo(xmlContent) {
    const openTagMatch = /<([A-Za-z][A-Za-z0-9_]*)\b([^>]*)>/i.exec(String(xmlContent || ""));
    const attrs = parseTagAttributes(openTagMatch ? openTagMatch[2] : "");
    const rawToolName = String(attrs.name || "").trim();
    const params = parseParamTags(xmlContent);
    if (rawToolName === "package_proxy") {
        const forwardedToolName = String(params.tool_name || "").trim();
        const forwardedParams = parseJsonObject(params.params);
        return {
            rawToolName,
            toolName: forwardedToolName || rawToolName,
            params: forwardedParams,
        };
    }
    return {
        rawToolName,
        toolName: rawToolName,
        params,
    };
}
function extractToolResultInfo(xmlContent) {
    const openTagMatch = /<([A-Za-z][A-Za-z0-9_]*)\b([^>]*)>/i.exec(String(xmlContent || ""));
    const attrs = parseTagAttributes(openTagMatch ? openTagMatch[2] : "");
    const contentMatch = /<content>([\s\S]*?)<\/content>/i.exec(String(xmlContent || ""));
    return {
        toolName: String(attrs.name || "").trim(),
        status: String(attrs.status || "success").trim().toLowerCase(),
        content: contentMatch ? String(contentMatch[1] || "").trim() : "",
    };
}
function countTargetPaths(raw) {
    const text = String(raw || "").trim();
    if (!text) {
        return 0;
    }
    try {
        const parsed = JSON.parse(text);
        if (!Array.isArray(parsed)) {
            return 0;
        }
        return parsed
            .map((item) => String(item || "").trim())
            .filter(Boolean)
            .length;
    }
    catch (_error) {
        return 0;
    }
}
function stageLabel(stage, text) {
    switch (String(stage || "").trim().toLowerCase()) {
        case "accepted":
            return text.stageLabelAccepted;
        case "planning":
            return text.stageLabelPlanning;
        case "tool":
            return text.stageLabelTool;
        case "executing":
            return text.stageLabelExecuting;
        case "summarizing":
            return text.stageLabelSummarizing;
        default:
            return text.stageLabelDefault;
    }
}
function stageTone(stage) {
    const normalized = String(stage || "").trim().toLowerCase();
    if (normalized === "accepted") {
        return "start";
    }
    return "progress";
}
function parseSubagentProtocol(content) {
    const normalizedContent = normalizeProtocolText(content);
    if (!normalizedContent) {
        return null;
    }
    const fragments = [];
    fragments.push(normalizedContent);
    normalizedContent
        .split(/\r?\n+/)
        .map((item) => normalizeProtocolText(item))
        .filter(Boolean)
        .forEach((item) => fragments.push(item));
    const blocks = [];
    fragments.forEach((fragment) => {
        const matches = fragment.match(/<subagent\b[^>]*>[\s\S]*?<\/subagent>/gi);
        if (matches) {
            matches.forEach((match) => {
                const normalizedBlock = normalizeProtocolText(match);
                if (normalizedBlock) {
                    blocks.push(normalizedBlock);
                }
            });
        }
    });
    for (let index = blocks.length - 1; index >= 0; index -= 1) {
        const block = blocks[index];
        const wrapperMatch = /<subagent\b[^>]*>([\s\S]*?)<\/subagent>/i.exec(block);
        if (!wrapperMatch) {
            continue;
        }
        const body = wrapperMatch[1];
        const finalMatch = /<final\b([^>]*)>([\s\S]*?)<\/final>/i.exec(body);
        if (finalMatch) {
            const attrs = parseTagAttributes(finalMatch[1]);
            const toolCountNumber = Number(attrs.tool_count || "");
            return {
                kind: "final",
                runId: String(attrs.run || "").trim(),
                success: String(attrs.success || "").trim().toLowerCase() === "true",
                toolCount: Number.isFinite(toolCountNumber) ? toolCountNumber : undefined,
                text: summarizeText(finalMatch[2], 220),
            };
        }
        const updateMatch = /<update\b([^>]*)>([\s\S]*?)<\/update>/i.exec(body);
        if (updateMatch) {
            const attrs = parseTagAttributes(updateMatch[1]);
            const countNumber = Number(attrs.count || "");
            return {
                kind: "update",
                runId: String(attrs.run || "").trim(),
                stage: String(attrs.stage || "").trim(),
                count: Number.isFinite(countNumber) ? countNumber : undefined,
                text: summarizeText(updateMatch[2], 140),
            };
        }
    }
    return null;
}
function createComposeResult(state) {
    return {
        handled: true,
        composeDsl: {
            screen: subagent_status_ui_js_1.default,
            state,
            memo: {},
        },
    };
}
function buildToolStartState(info) {
    const text = (0, subagent_i18n_js_1.resolveSubagentI18n)();
    const taskSummary = summarizeText(info.params.task, 96);
    const targetCount = countTargetPaths(info.params.target_paths_json);
    return {
        title: text.titleStarted,
        summary: taskSummary,
        detail: targetCount > 0 ? text.detailTargetCount(targetCount) : "",
        badge: targetCount > 0 ? text.badgeTargetCount(targetCount) : "",
        tone: "start",
        variant: "tool_start",
    };
}
function buildUpdateState(message) {
    const text = (0, subagent_i18n_js_1.resolveSubagentI18n)();
    const detailParts = [];
    if (message.runId) {
        detailParts.push(text.detailRun(message.runId));
    }
    return {
        title: `Subagent ${stageLabel(message.stage, text)}`,
        summary: message.text,
        detail: detailParts.join(" · "),
        badge: message.count && message.count > 0 ? text.badgeToolCount(message.count) : "",
        tone: stageTone(message.stage),
        variant: "update",
    };
}
function buildFinalState(message) {
    const text = (0, subagent_i18n_js_1.resolveSubagentI18n)();
    const detailParts = [];
    if (message.runId) {
        detailParts.push(text.detailRun(message.runId));
    }
    if (message.toolCount && message.toolCount > 0) {
        detailParts.push(text.detailToolCalls(message.toolCount));
    }
    return {
        title: message.success ? text.titleCompleted : text.titleFailed,
        summary: message.text,
        detail: detailParts.join(" · "),
        badge: message.success ? text.badgeSuccess : text.badgeFailure,
        tone: message.success ? "success" : "failure",
        variant: "final",
    };
}
function onToolXmlRender(event) {
    const payload = normalizePayload(event);
    const tagName = String(payload.tagName || "").trim().toLowerCase();
    const xmlContent = String(payload.xmlContent || "");
    if (tagName !== "tool" || !xmlContent) {
        return { handled: false };
    }
    const info = resolveToolCallInfo(xmlContent);
    if (!isSubagentToolName(info.toolName)) {
        return { handled: false };
    }
    return createComposeResult(buildToolStartState(info));
}
function onToolResultXmlRender(event) {
    const payload = normalizePayload(event);
    const tagName = String(payload.tagName || "").trim().toLowerCase();
    const xmlContent = String(payload.xmlContent || "");
    if (tagName !== "tool_result" || !xmlContent) {
        return { handled: false };
    }
    const info = extractToolResultInfo(xmlContent);
    if (!isSubagentToolName(info.toolName)) {
        return { handled: false };
    }
    const protocol = parseSubagentProtocol(info.content);
    if (!protocol) {
        return { handled: false };
    }
    if (protocol.kind === "update") {
        return createComposeResult(buildUpdateState(protocol));
    }
    return createComposeResult(buildFinalState(protocol));
}

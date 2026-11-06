"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseSubagentProtocol = parseSubagentProtocol;
exports.onToolXmlRender = onToolXmlRender;
exports.onToolResultXmlRender = onToolResultXmlRender;
const subagent_status_ui_js_1 = __importDefault(require("../ui/subagent-status.ui.js"));
const subagent_i18n_js_1 = require("../shared/subagent_i18n.js");
function decodeXml(value) {
    return value.replace(/&lt;/g, "<").replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, "&");
}
function parseTagAttributes(fragment) {
    const attrs = {};
    const regex = /([A-Za-z_][A-Za-z0-9_\-]*)\s*=\s*"([^"]*)"/g;
    let match;
    while ((match = regex.exec(fragment)) !== null)
        attrs[match[1]] = decodeXml(match[2]);
    return attrs;
}
function shortToolName(name) {
    return name.substring(name.lastIndexOf(":") + 1).trim();
}
function resolveToolCallInfo(xml) {
    const opening = /^\s*<[A-Za-z][A-Za-z0-9_]*\b([^>]*)>/.exec(xml);
    if (!opening)
        return null;
    const attrs = parseTagAttributes(opening[1]);
    const params = {};
    const paramPattern = /<param\s+name="([^"]+)">([\s\S]*?)<\/param>/gi;
    let param;
    while ((param = paramPattern.exec(xml)) !== null)
        params[param[1]] = decodeXml(param[2]);
    const name = attrs.name ?? "";
    if (name !== "package_proxy") {
        return { toolName: name, task: params.task ?? "", targetPathsJson: params.target_paths_json };
    }
    try {
        const forwarded = JSON.parse(params.params ?? "{}");
        if (forwarded === null || typeof forwarded !== "object" || Array.isArray(forwarded))
            return null;
        return {
            toolName: params.tool_name ?? "",
            task: typeof forwarded.task === "string" ? forwarded.task : "",
            targetPathsJson: typeof forwarded.target_paths_json === "string" ? forwarded.target_paths_json : undefined,
        };
    }
    catch (error) {
        console.error("Subagent renderer could not parse forwarded parameters", error);
        return null;
    }
}
function legacyProtocolContent(content) {
    let text = content.trim();
    // Older script results serialized the XML string as JSON, sometimes in another serialized string.
    for (let depth = 0; depth < 3 && text.startsWith('"'); depth += 1) {
        try {
            const value = JSON.parse(text);
            if (typeof value !== "string")
                return "";
            text = value.trim();
        }
        catch (error) {
            console.error("Subagent renderer could not decode a serialized result", error);
            return "";
        }
    }
    // Decode only an encoded wrapper. Decoding a live wrapper would turn escaped summary text into tags.
    return text.startsWith("&lt;subagent") ? decodeXml(text) : text;
}
function optionalCount(value) {
    if (value === undefined || !/^\d+$/.test(value))
        return undefined;
    const count = Number(value);
    return Number.isSafeInteger(count) ? count : undefined;
}
function parseSubagentProtocol(content) {
    const blocks = legacyProtocolContent(content).match(/<subagent\b[^>]*>[\s\S]*?<\/subagent>/gi);
    if (!blocks || blocks.length === 0)
        return null;
    const body = blocks[blocks.length - 1];
    const final = /<final\b([^>]*)>([\s\S]*?)<\/final>/i.exec(body);
    if (final) {
        const attrs = parseTagAttributes(final[1]);
        let status;
        if (attrs.status === undefined) {
            // Historical final messages predate status; success is their explicit protocol field.
            status = attrs.success === "true" ? "completed" : "failed";
        }
        else if (attrs.status === "completed" || attrs.status === "blocked" || attrs.status === "failed") {
            status = attrs.status;
            if ((status === "completed") !== (attrs.success === "true"))
                return null;
        }
        else {
            return null;
        }
        return {
            kind: "final",
            runId: attrs.run ?? "",
            status,
            toolCount: optionalCount(attrs.tool_count),
            evidencePath: attrs.evidence_path ?? "",
            text: decodeXml(final[2]).trim(),
        };
    }
    const update = /<update\b([^>]*)>([\s\S]*?)<\/update>/i.exec(body);
    if (!update)
        return null;
    const attrs = parseTagAttributes(update[1]);
    return {
        kind: "update", runId: attrs.run ?? "", stage: attrs.stage ?? "",
        count: optionalCount(attrs.count), text: decodeXml(update[2]).trim(),
    };
}
function compose(state) {
    return { handled: true, composeDsl: { screen: subagent_status_ui_js_1.default, state, memo: {} } };
}
function stageLabel(stage, text) {
    switch (stage) {
        case "accepted": return text.stageLabelAccepted;
        case "planning": return text.stageLabelPlanning;
        case "executing": return text.stageLabelExecuting;
        case "tool": return text.stageLabelTool;
        case "summarizing": return text.stageLabelSummarizing;
        default: return text.stageLabelDefault;
    }
}
function onToolXmlRender(event) {
    const { tagName, xmlContent } = event.eventPayload;
    if (tagName !== "tool" || xmlContent === undefined)
        return { handled: false };
    const info = resolveToolCallInfo(xmlContent);
    if (info === null || shortToolName(info.toolName) !== "subagent_run")
        return { handled: false };
    const text = (0, subagent_i18n_js_1.resolveSubagentI18n)();
    return compose({
        title: text.titleStarted, summary: info.task, detail: "", badge: "", tone: "start", variant: "tool_start",
    });
}
function onToolResultXmlRender(event) {
    const { tagName, xmlContent } = event.eventPayload;
    if (tagName !== "tool_result" || xmlContent === undefined)
        return { handled: false };
    const opening = /^\s*<[A-Za-z][A-Za-z0-9_]*\b([^>]*)>/.exec(xmlContent);
    const content = /<content>([\s\S]*?)<\/content>/i.exec(xmlContent);
    if (!opening || !content)
        return { handled: false };
    const attrs = parseTagAttributes(opening[1]);
    if (shortToolName(attrs.name ?? "") !== "subagent_run")
        return { handled: false };
    const message = parseSubagentProtocol(content[1]);
    if (message === null)
        return { handled: false };
    const text = (0, subagent_i18n_js_1.resolveSubagentI18n)();
    const details = message.runId ? [text.detailRun(message.runId)] : [];
    if (message.kind === "update") {
        return compose({
            title: `Subagent ${stageLabel(message.stage, text)}`, summary: message.text,
            detail: details.join(" · "), badge: message.count === undefined ? "" : text.badgeToolCount(message.count),
            tone: message.stage === "accepted" ? "start" : "progress", variant: "update",
        });
    }
    const status = attrs.status === "error" && message.status === "completed" ? "failed" : message.status;
    if (message.toolCount !== undefined)
        details.push(text.detailToolCalls(message.toolCount));
    if (message.evidencePath)
        details.push(message.evidencePath);
    return compose({
        title: status === "completed" ? text.titleCompleted : status === "blocked" ? text.titleBlocked : text.titleFailed,
        summary: message.text, detail: details.join(" · "),
        badge: status === "completed" ? text.badgeSuccess : status === "blocked" ? text.badgeBlocked : text.badgeFailure,
        tone: status === "completed" ? "success" : status === "blocked" ? "blocked" : "failure", variant: "final",
    });
}

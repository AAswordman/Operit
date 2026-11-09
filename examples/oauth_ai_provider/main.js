"use strict";

// Plain CommonJS: this example can be imported directly without a TypeScript build step.
exports.registerToolPkg = registerToolPkg;
exports.listModels = listModels;
exports.sendMessage = sendMessage;
exports.testConnection = testConnection;
exports.calculateInputTokens = calculateInputTokens;

function registerToolPkg() {
    if (typeof ToolPkg.supportsAiProviderAuth !== "function" ||
            !ToolPkg.supportsAiProviderAuth("oauth2")) {
        throw new Error("This package requires host-managed AI provider OAuth support. Update Operit first.");
    }
    ToolPkg.registerAiProvider({
        id: "example_oauth_text_provider",
        displayName: "OAuth 示例供应商",
        description: "Host-managed OAuth with a text-only OpenAI-compatible transport example",
        auth: {
            type: "oauth2",
            clientId: "replace-with-your-registered-public-client-id",
            authorizationEndpoint: "https://identity.example.invalid/authorize",
            tokenEndpoint: "https://identity.example.invalid/token",
            scopes: ["offline_access"],
            redirectPort: 0,
            redirectPath: "/oauth/callback"
        },
        listModels: { function: listModels },
        sendMessage: { function: sendMessage },
        testConnection: { function: testConnection },
        calculateInputTokens: { function: calculateInputTokens }
    });
    return true;
}

function endpoint(payload, models) {
    const value = String(payload.config.apiEndpoint || "").trim();
    if (!value.startsWith("https://") || !/\/chat\/completions\/?$/.test(value)) {
        throw new Error("This example requires an HTTPS endpoint ending in /chat/completions");
    }
    return models ? value.replace(/\/chat\/completions\/?$/, "/models") : value;
}

async function request(payload, url, body) {
    const auth = payload.auth;
    if (!auth || auth.type !== "oauth2" || auth.tokenType !== "Bearer" || !auth.accessToken) {
        throw new Error("Sign in to the provider in model settings first");
    }
    // Do not fall back to config.apiKey or read/write the host's refresh-token store.
    const client = OkHttp.newBuilder().connectTimeout(30000).readTimeout(30000).writeTimeout(30000).build();
    const builder = client.newRequest().url(url).method(body === undefined ? "GET" : "POST")
        .headers({ "Content-Type": "application/json", "Authorization": `Bearer ${auth.accessToken}` });
    if (body !== undefined) builder.body(body, "json");
    const response = await builder.build().execute();
    if (!response.isSuccessful()) {
        // Never echo provider response bodies or retry chat submissions after an authentication error.
        throw new Error(`Provider request failed (HTTP ${response.statusCode})`);
    }
    return response.json();
}

/** @param {import('../types/toolpkg').ToolPkg.AiProviderListModelsEvent} event */
async function listModels(event) {
    const payload = event.eventPayload;
    const result = await request(payload, endpoint(payload, true));
    if (!Array.isArray(result.data)) throw new Error("Provider returned an invalid model list");
    return { models: result.data.filter(item => item && typeof item.id === "string" && item.id.trim())
        .map(item => ({ id: item.id, name: item.id })) };
}

/** @param {import('../types/toolpkg').ToolPkg.AiProviderSendMessageEvent} event */
async function sendMessage(event) {
    const payload = event.eventPayload;
    const model = String(payload.config.modelName || "").trim();
    if (!model) throw new Error("Choose a model in settings first");
    const messages = (payload.chatHistory || []).map(turn => {
        const roles = { SYSTEM: "system", SUMMARY: "system", USER: "user", ASSISTANT: "assistant" };
        const role = roles[turn.kind];
        if (!role) throw new Error("This example supports text turns only; implement tool/media transport for production use");
        return { role, content: String(turn.content || "") };
    });
    const result = await request(payload, endpoint(payload, false), { model, messages, stream: false });
    const text = result.choices && result.choices[0] && result.choices[0].message && result.choices[0].message.content;
    if (typeof text !== "string") throw new Error("Provider returned an invalid text response");
    const usage = result.usage || {};
    const count = value => typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : 0;
    return { text, usage: { input: count(usage.prompt_tokens), output: count(usage.completion_tokens) } };
}

async function testConnection(event) {
    await listModels(event);
    return { success: true, message: "Connection successful" };
}

function calculateInputTokens(event) {
    const turns = event.eventPayload.chatHistory || [];
    return { tokens: Math.ceil(turns.reduce((sum, turn) => sum + String(turn.content || "").length, 0) / 4) };
}

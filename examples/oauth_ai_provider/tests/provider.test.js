"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const provider = require("../main.js");

const payload = () => ({ config: { apiEndpoint: "https://api.example/v1/chat/completions", apiKey: "must-not-use", modelName: "model" },
    auth: { type: "oauth2", tokenType: "Bearer", accessToken: "test-access" },
    chatHistory: [{ kind: "USER", content: "Hello" }] });

function httpMock(reply, statusCode = 200) {
    const calls = [];
    global.OkHttp = {
        newBuilder() {
            const client = { connectTimeout() { return this; }, readTimeout() { return this; }, writeTimeout() { return this; },
                build() { return { newRequest() {
                    const call = {};
                    return { url(value) { call.url = value; return this; }, method(value) { call.method = value; return this; },
                        headers(value) { call.headers = value; return this; }, body(value, type) { call.body = value; call.bodyType = type; return this; },
                        build() { return { async execute() {
                            calls.push(call);
                            return { statusCode, isSuccessful: () => statusCode >= 200 && statusCode < 300, json: async () => reply };
                        } }; }
                    };
                } }; }
            };
            return client;
        }
    };
    return calls;
}

test("old hosts fail closed before registering an OAuth provider", () => {
    let registrations = 0;
    global.ToolPkg = { registerAiProvider() { registrations++; } };
    assert.throws(() => provider.registerToolPkg(), /Update Operit/);
    global.ToolPkg.supportsAiProviderAuth = () => false;
    assert.throws(() => provider.registerToolPkg(), /Update Operit/);
    assert.equal(registrations, 0);
});

test("capable hosts get declarative OAuth metadata and four exported handlers", () => {
    let definition;
    global.ToolPkg = { supportsAiProviderAuth: type => type === "oauth2", registerAiProvider(value) { definition = value; } };
    assert.equal(provider.registerToolPkg(), true);
    assert.equal(definition.auth.type, "oauth2");
    assert.equal(definition.auth.redirectPort, 0);
    for (const key of ["listModels", "sendMessage", "testConnection", "calculateInputTokens"]) {
        assert.equal(definition[key].function, provider[key]);
    }
    assert.equal(definition.auth.clientSecret, undefined);
});

test("model list uses only the request-scoped access token", async () => {
    const calls = httpMock({ data: [{ id: "one" }, {}, { id: "two" }] });
    assert.deepEqual(await provider.listModels({ eventPayload: payload() }), { models: [{ id: "one", name: "one" }, { id: "two", name: "two" }] });
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, "https://api.example/v1/models");
    assert.equal(calls[0].headers.Authorization, "Bearer test-access");
    assert.ok(!JSON.stringify(calls).includes("must-not-use"));
});

test("no auth never falls back to an API key or sends a request", async () => {
    const calls = httpMock({});
    const p = payload(); delete p.auth;
    await assert.rejects(provider.listModels({ eventPayload: p }), /Sign in/);
    assert.equal(calls.length, 0);
});

test("chat returns content and usage without echoing credentials", async () => {
    const calls = httpMock({ choices: [{ message: { content: "Hi" } }], usage: { prompt_tokens: 2, completion_tokens: 1 } });
    const reply = await provider.sendMessage({ eventPayload: payload() });
    assert.deepEqual(reply, { text: "Hi", usage: { input: 2, output: 1 } });
    assert.equal(calls[0].method, "POST");
    assert.deepEqual(calls[0].body.messages, [{ role: "user", content: "Hello" }]);
    assert.equal(calls[0].body.stream, false);
    assert.ok(!JSON.stringify(reply).includes("test-access"));
});

test("HTTP errors are sanitized and chat is not replayed", async () => {
    const calls = httpMock({ error: "SECRET_RESPONSE" }, 401);
    await assert.rejects(provider.sendMessage({ eventPayload: payload() }), error => {
        assert.match(error.message, /HTTP 401/);
        assert.ok(!error.message.includes("SECRET_RESPONSE"));
        return true;
    });
    assert.equal(calls.length, 1);
});

test("token estimation needs neither credentials nor network", () => {
    const calls = httpMock({});
    assert.deepEqual(provider.calculateInputTokens({ eventPayload: { chatHistory: [{ content: "12345678" }] } }), { tokens: 2 });
    assert.equal(calls.length, 0);
});

test("invalid endpoint and unsupported tool histories are explicit errors", async () => {
    const calls = httpMock({});
    const p = payload(); p.config.apiEndpoint = "http://api.example/v1/chat/completions";
    await assert.rejects(provider.listModels({ eventPayload: p }), /HTTPS/);
    const tools = payload(); tools.chatHistory = [{ kind: "TOOL_CALL", content: "test" }];
    await assert.rejects(provider.sendMessage({ eventPayload: tools }), /text turns only/);
    assert.equal(calls.length, 0);
});

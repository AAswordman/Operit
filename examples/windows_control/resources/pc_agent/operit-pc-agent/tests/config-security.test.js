const assert = require("node:assert/strict");
const os = require("node:os");
const path = require("node:path");
const { Readable } = require("node:stream");
const { after, before, test } = require("node:test");

const agentRoot = process.env.OPERIT_PC_AGENT_ROOT || path.resolve(__dirname, "..");
const { createApiHandler } = require(path.join(agentRoot, "src/handlers/api-handler"));
const TOKEN = "fixture-api-token";
const REMOTE = "198.51.100.10";
const HOST = "192.0.2.20:58321";
const originalNetworkInterfaces = os.networkInterfaces;

before(() => {
  os.networkInterfaces = () => ({
    Ethernet: [
      { address: "192.0.2.20", family: "IPv4", internal: false },
      { address: "2001:db8::20", family: "IPv6", internal: false }
    ]
  });
});
after(() => {
  os.networkInterfaces = originalNetworkInterfaces;
});

function createHarness({ startupIssue = { issueType: "bindAddressUnavailable" } } = {}) {
  const state = {
    config: {
      bindAddress: "192.0.2.20",
      port: 58321,
      maxCommandMs: 30000,
      apiToken: TOKEN,
      allowedPresets: ["health_probe"]
    }
  };
  const effects = { configSaves: 0, startupReads: 0, startupSaves: 0, restarts: 0 };
  const warnings = [];
  const handler = createApiHandler({
    state,
    configStore: {
      ensureApiToken: (value) => String(value || "fixture-generated-token"),
      normalizeAllowedPresets: (value) => value,
      saveConfig: () => { effects.configSaves += 1; }
    },
    startupStateStore: {
      loadState: () => { effects.startupReads += 1; return startupIssue; },
      saveState: () => { effects.startupSaves += 1; }
    },
    restartAgent: () => { effects.restarts += 1; return true; },
    processService: {
      getNetworkSnapshot: () => ({ recommendedHost: "192.0.2.30" })
    },
    fileService: {},
    logger: {
      info() {},
      error() {},
      warn: (event, data) => warnings.push({ event, data })
    },
    presetCommands: {},
    runtimeInfo: {},
    versionInfo: { agentVersion: "fixture" }
  });

  async function request(method, pathname, body, options = {}) {
    const req = Readable.from(body === undefined ? [] : [Buffer.from(JSON.stringify(body))]);
    req.method = method;
    req.socket = { remoteAddress: REMOTE, ...options.socket };
    req.headers = { host: HOST, ...options.headers };
    const response = {};
    const res = {
      writeHead: (status, headers) => { response.status = status; response.headers = headers; },
      end: (payload) => { response.json = JSON.parse(payload); }
    };
    assert.equal(await handler.handleApiRequest(req, res, new URL(`http://agent.test${pathname}`)), true);
    return response;
  }
  return { state, effects, warnings, request };
}

const localRequests = [
  ["IPv4 loopback", "127.0.0.1", "127.0.0.1:58321"],
  ["IPv6 loopback", "::1", "[::1]:58321"],
  ["localhost", "127.0.0.1", "localhost:58321"],
  ["IPv4-mapped loopback", "::ffff:127.0.0.1", "127.0.0.1:58321"],
  ["local LAN address", "192.0.2.20", HOST],
  ["IPv4-mapped LAN address", "::ffff:192.0.2.20", HOST],
  ["local IPv6 address", "2001:db8::20", "[2001:db8::20]:58321"],
  ["expanded local IPv6 address", "2001:0db8:0:0:0:0:0:20", "[2001:db8::20]:58321"]
];
for (const [name, remoteAddress, host] of localRequests) {
  test(`local pairing retains token: ${name}`, async () => {
    const harness = createHarness();
    const headers = { host, origin: `http://${host}`, "sec-fetch-site": "same-origin" };
    const options = { socket: { remoteAddress }, headers };
    const result = await harness.request("GET", "/api/config", undefined, options);
    assert.equal(result.status, 200);
    assert.equal(result.json.apiToken, TOKEN);
    assert.equal(result.json.apiTokenConfigured, true);
    for (const route of ["/api/config", "/api/startup/apply_recommended_bind"]) {
      const updated = await harness.request("POST", route, {}, options);
      assert.equal(updated.status, 200);
      assert.equal(updated.json.config.apiToken, TOKEN);
    }
  });
}

const untrustedRequests = [
  ["remote IPv4", { socket: { remoteAddress: REMOTE } }],
  ["remote IPv4-mapped IPv6", { socket: { remoteAddress: "::ffff:198.51.100.10" } }],
  ["remote IPv6", { socket: { remoteAddress: "2001:db8::99" } }],
  ["missing peer address", { socket: { remoteAddress: undefined } }],
  ["invalid peer address", { socket: { remoteAddress: "localhost" } }],
  ["spoofed forwarded headers", { headers: { "x-forwarded-for": "127.0.0.1", "x-real-ip": "192.0.2.20", forwarded: "for=127.0.0.1" } }],
  ["remote peer with localhost Host", { headers: { host: "localhost:58321" } }],
  ["DNS rebinding Host", { socket: { remoteAddress: "127.0.0.1" }, headers: { host: "external.test:58321" } }],
  ["missing Host", { socket: { remoteAddress: "127.0.0.1" }, headers: { host: undefined } }],
  ["invalid Host", { socket: { remoteAddress: "127.0.0.1" }, headers: { host: "[invalid" } }],
  ["cross-origin browser request", { socket: { remoteAddress: "127.0.0.1" }, headers: { origin: "https://external.test" } }],
  ["null Origin", { socket: { remoteAddress: "127.0.0.1" }, headers: { origin: "null" } }],
  ["same-host different-port Origin", { socket: { remoteAddress: "127.0.0.1" }, headers: { origin: "http://192.0.2.20:58322" } }],
  ["cross-site fetch", { socket: { remoteAddress: "127.0.0.1" }, headers: { "sec-fetch-site": "cross-site" } }],
  ["same-site different-origin fetch", { socket: { remoteAddress: "127.0.0.1" }, headers: { "sec-fetch-site": "same-site" } }]
];
for (const [name, options] of untrustedRequests) {
  test(`untrusted configuration requests are redacted and rejected: ${name}`, async () => {
    const harness = createHarness();
    const result = await harness.request("GET", "/api/config", undefined, options);
    assert.equal(result.status, 200);
    assert.equal(result.json.apiToken, "");
    assert.equal(result.json.apiTokenConfigured, true);
    assert.equal(result.headers["Cache-Control"], "no-store");
    const originalConfig = structuredClone(harness.state.config);
    for (const route of ["/api/config", "/api/startup/apply_recommended_bind"]) {
      const denied = await harness.request("POST", route, { apiToken: "replacement-token", bindAddress: "0.0.0.0" }, options);
      assert.equal(denied.status, 401);
      assert.deepEqual(denied.json, { ok: false, error: "Unauthorized" });
      assert.deepEqual(harness.state.config, originalConfig);
      assert.deepEqual(harness.effects, { configSaves: 0, startupReads: 0, startupSaves: 0, restarts: 0 });
    }
  });
}

for (const route of ["/api/config", "/api/startup/apply_recommended_bind"]) {
  test(`wrong token cannot mutate configuration: ${route}`, async () => {
    const harness = createHarness();
    const result = await harness.request("POST", route, { token: "wrong-token", apiToken: "replacement-token" });
    assert.equal(result.status, 401);
    assert.equal(harness.state.config.apiToken, TOKEN);
    assert.equal(harness.effects.configSaves, 0);
    assert.equal(JSON.stringify(harness.warnings).includes("wrong-token"), false);
  });

  test(`current token authorizes remote writes without disclosure: ${route}`, async () => {
    const harness = createHarness();
    const result = await harness.request("POST", route, { token: TOKEN, maxCommandMs: 40000 });
    assert.equal(result.status, 200);
    assert.equal(result.json.config.apiToken, "");
    assert.equal(result.json.config.apiTokenConfigured, true);
    assert.equal(harness.effects.configSaves, 1);
    assert.equal(harness.effects.restarts, route === "/api/config" ? 0 : 1);
  });
}

test("token rotation authenticates the old token before accepting its replacement", async () => {
  const harness = createHarness();
  for (const key of ["apiToken", "api_token"]) {
    const denied = await harness.request("POST", "/api/config", { [key]: "replacement-token" });
    assert.equal(denied.status, 401);
    assert.equal(harness.state.config.apiToken, TOKEN);
  }
  const updated = await harness.request("POST", "/api/config", { token: TOKEN, api_token: "replacement-token" });
  assert.equal(updated.status, 200);
  assert.equal(updated.json.config.apiToken, "");
  assert.equal(harness.state.config.apiToken, "replacement-token");
  assert.equal((await harness.request("POST", "/api/config", { token: TOKEN })).status, 401);
  assert.equal((await harness.request("POST", "/api/config", { token: "replacement-token" })).status, 200);
});

test("local wizard can regenerate and read a token", async () => {
  const harness = createHarness();
  const options = { socket: { remoteAddress: "127.0.0.1" } };
  const updated = await harness.request("POST", "/api/config", { apiToken: "" }, options);
  assert.equal(updated.status, 200);
  assert.equal(updated.json.config.apiToken, "fixture-generated-token");
  assert.equal((await harness.request("GET", "/api/config", undefined, options)).json.apiToken, "fixture-generated-token");
});

test("missing configured token never authorizes an empty remote token", async () => {
  const harness = createHarness();
  harness.state.config.apiToken = "";
  assert.equal((await harness.request("GET", "/api/config")).json.apiTokenConfigured, false);
  assert.equal((await harness.request("POST", "/api/config", { token: "" })).status, 401);
});

test("recovery authenticates before checking startup state", async () => {
  const harness = createHarness({ startupIssue: null });
  assert.equal((await harness.request("POST", "/api/startup/apply_recommended_bind", {})).status, 401);
  assert.equal(harness.effects.startupReads, 0);
  assert.equal((await harness.request("POST", "/api/startup/apply_recommended_bind", { token: TOKEN })).status, 400);
  assert.equal(harness.effects.configSaves, 0);
  assert.equal(harness.effects.restarts, 0);
});

test("local privilege does not bypass command, process or file authentication", async () => {
  const harness = createHarness();
  const options = { socket: { remoteAddress: "127.0.0.1" } };
  for (const route of ["/api/command/execute", "/api/process/list", "/api/file/read"]) {
    assert.equal((await harness.request("POST", route, {}, options)).status, 401);
  }
});

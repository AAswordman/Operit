const os = require("os");
const net = require("net");
const { readJsonBody, sendJson, parseBoolean } = require("../lib/http-utils");

const FILE_WRITE_JSON_MAX_BODY_BYTES = 8 * 1024 * 1024;
const FILE_WRITE_BASE64_JSON_MAX_BODY_BYTES = 24 * 1024 * 1024;

function buildPublicConfig(config, versionInfo, options = {}) {
  return {
    bindAddress: config.bindAddress,
    port: config.port,
    maxCommandMs: config.maxCommandMs,
    allowedPresets: config.allowedPresets,
    apiTokenConfigured: !!config.apiToken,
    apiToken: options.includeApiToken ? config.apiToken || "" : "",
    version: versionInfo.agentVersion
  };
}

function pickConfigInput(body, camelKey, snakeKey) {
  if (!body || typeof body !== "object") {
    return undefined;
  }

  if (body[camelKey] !== undefined) {
    return body[camelKey];
  }

  if (snakeKey && body[snakeKey] !== undefined) {
    return body[snakeKey];
  }

  return undefined;
}

function isAuthorized(config, token) {
  if (!config.apiToken) {
    return false;
  }

  return String(token || "") === config.apiToken;
}

function normalizeIpAddress(value) {
  const address = String(value || "").trim().toLowerCase();
  if (net.isIP(address) === 4) {
    return address;
  }
  if (net.isIP(address) !== 6 || address.includes("%")) {
    return "";
  }

  const normalized = new URL(`http://[${address}]`).hostname.slice(1, -1);
  const mappedIpv4 = /^::ffff:([0-9a-f]+):([0-9a-f]+)$/.exec(normalized);
  if (mappedIpv4) {
    const high = parseInt(mappedIpv4[1], 16);
    const low = parseInt(mappedIpv4[2], 16);
    return [high >> 8, high & 255, low >> 8, low & 255].join(".");
  }
  return normalized;
}

function isLocalRequest(req) {
  const remoteAddress = normalizeIpAddress(req && req.socket && req.socket.remoteAddress);
  if (!remoteAddress) {
    return false;
  }

  const localAddresses = new Set(["127.0.0.1", "::1"]);
  for (const interfaces of Object.values(os.networkInterfaces())) {
    for (const networkInterface of interfaces || []) {
      const address = normalizeIpAddress(networkInterface && networkInterface.address);
      if (address) {
        localAddresses.add(address);
      }
    }
  }
  const isLocalAddress = (address) => localAddresses.has(address) || address.startsWith("127.");
  if (!isLocalAddress(remoteAddress)) {
    return false;
  }

  // 本机浏览器也会发起外部网页请求，免认证管理必须同时防止跨站请求和 DNS 重绑定。
  const headers = req.headers || {};
  let requestOrigin;
  try {
    const requestUrl = new URL(`http://${headers.host}`);
    const hostname = requestUrl.hostname;
    const hostAddress = normalizeIpAddress(hostname.startsWith("[") ? hostname.slice(1, -1) : hostname);
    if (hostname !== "localhost" && !isLocalAddress(hostAddress)) {
      return false;
    }
    requestOrigin = requestUrl.origin;
  } catch {
    return false;
  }

  if (headers.origin !== undefined && headers.origin !== requestOrigin) {
    return false;
  }
  const fetchSite = headers["sec-fetch-site"];
  return fetchSite === undefined || fetchSite === "same-origin" || fetchSite === "none";
}

function isConfigRequestAuthorized(req, config, token) {
  return isLocalRequest(req) || isAuthorized(config, token);
}

function createApiHandler({
  state,
  configStore,
  startupStateStore,
  restartAgent,
  processService,
  fileService,
  logger,
  presetCommands,
  runtimeInfo,
  versionInfo
}) {
  function isUsableRecommendedBind(value) {
    const text = String(value || "").trim();
    if (!text) {
      return false;
    }
    if (text === "127.0.0.1" || text === "0.0.0.0" || text === "localhost" || text === "::1") {
      return false;
    }
    return true;
  }

  function getStartupIssueState() {
    if (!startupStateStore) {
      return null;
    }
    return startupStateStore.loadState();
  }

  function getPresetList(config) {
    return Object.entries(presetCommands).map(([name, item]) => ({
      name,
      shell: item.shell,
      description: item.description,
      allowed: config.allowedPresets.includes(name)
    }));
  }

  function unauthorized(res, routeName, tokenProvided, extra = {}) {
    logger.warn(`${routeName}.unauthorized`, {
      tokenProvided: !!tokenProvided,
      ...extra
    });
    sendJson(res, 401, { ok: false, error: "Unauthorized" });
  }

  function normalizeInputToken(value) {
    return String(value || "")
      .trim()
      .toLowerCase()
      .replace(/[\s-]+/g, "_");
  }

  function controlTokenToSequence(tokenInput) {
    const token = normalizeInputToken(tokenInput);
    const map = {
      enter: "\r",
      return: "\r",
      newline: "\n",
      cr: "\r",
      lf: "\n",
      tab: "\t",
      backspace: "\b",
      esc: "\u001b",
      escape: "\u001b",
      delete: "\u001b[3~",
      del: "\u001b[3~",
      insert: "\u001b[2~",
      ins: "\u001b[2~",
      up: "\u001b[A",
      down: "\u001b[B",
      left: "\u001b[D",
      right: "\u001b[C",
      home: "\u001b[H",
      end: "\u001b[F",
      pageup: "\u001b[5~",
      pagedown: "\u001b[6~",
      ctrl_c: "\u0003",
      ctrl_d: "\u0004",
      ctrl_z: "\u001a"
    };

    if (!map[token]) {
      throw new Error(`Unsupported control: ${tokenInput}`);
    }

    return map[token];
  }

  function ctrlInputToSequence(keyInput) {
    const key = String(keyInput || "").trim();
    if (!key) {
      throw new Error("Invalid ctrl input key");
    }

    const normalized = key.toLowerCase();
    if (normalized === "space") {
      return "\u0000";
    }

    if (normalized.length === 1 && normalized >= "a" && normalized <= "z") {
      return String.fromCharCode(normalized.charCodeAt(0) - 96);
    }

    if (key.length === 1) {
      switch (key) {
        case "@":
        case "2":
          return "\u0000";
        case "[":
        case "3":
          return "\u001b";
        case "\\":
        case "4":
          return "\u001c";
        case "]":
        case "5":
          return "\u001d";
        case "^":
        case "6":
          return "\u001e";
        case "_":
        case "7":
        case "-":
          return "\u001f";
        case "?":
        case "8":
          return "\u007f";
        default:
          break;
      }
    }

    throw new Error(`Unsupported ctrl key: ${keyInput}`);
  }

  function parseModifierControl(controlInput) {
    const raw = String(controlInput || "").trim().toLowerCase();
    if (!raw) {
      return null;
    }

    const normalized = raw.replace(/\s+/g, "").replace(/_/g, "+").replace(/-/g, "+");
    const parts = normalized.split("+").filter((item) => !!item);
    if (!parts.length) {
      return null;
    }

    const set = new Set(parts);
    for (const item of set) {
      if (item !== "ctrl" && item !== "alt" && item !== "shift") {
        return null;
      }
    }

    return set;
  }

  function applyModifierControl(inputText, modifierSet) {
    if (!modifierSet || !modifierSet.size) {
      return inputText;
    }

    let sequence = String(inputText || "");

    if (modifierSet.has("ctrl")) {
      if (sequence.length !== 1) {
        throw new Error("ctrl modifier requires input to be a single character");
      }
      sequence = ctrlInputToSequence(sequence);
    } else if (modifierSet.has("shift")) {
      sequence = sequence.toUpperCase();
    }

    if (modifierSet.has("alt")) {
      sequence = `\u001b${sequence}`;
    }

    return sequence;
  }

  function resolveProcessWriteInput(body) {
    const hasInput = body.input !== undefined && body.input !== null;
    const hasControl = body.control !== undefined && body.control !== null && String(body.control).trim() !== "";

    if (!hasInput && !hasControl) {
      throw new Error("Missing input: provide input or control");
    }

    const inputText = hasInput ? body.input : "";
    if (hasInput && typeof inputText !== "string") {
      throw new Error("input must be a string");
    }

    let sequence = "";
    let inputConsumedByModifier = false;

    if (hasControl) {
      const modifierSet = parseModifierControl(body.control);
      if (modifierSet && modifierSet.size > 0) {
        if (!hasInput) {
          throw new Error("modifier control requires input");
        }
        sequence += applyModifierControl(inputText, modifierSet);
        inputConsumedByModifier = true;
      } else {
        sequence += controlTokenToSequence(body.control);
      }
    }

    if (hasInput && !inputConsumedByModifier) {
      sequence += inputText;
    }

    const repeatRaw = body.repeat !== undefined ? body.repeat : body.times;
    if (repeatRaw !== undefined && repeatRaw !== null && repeatRaw !== "") {
      const repeat = Number(repeatRaw);
      if (!Number.isFinite(repeat) || repeat < 1 || repeat > 1000) {
        throw new Error("repeat must be an integer in range 1..1000");
      }
      sequence = sequence.repeat(Math.floor(repeat));
    }

    if (!sequence) {
      throw new Error("Resolved input is empty");
    }

    return sequence;
  }

  async function handleApiRequest(req, res, url) {
    const config = state.config;

    if (req.method === "GET" && url.pathname === "/api/health") {
      const network = processService.getNetworkSnapshot();
      const user = processService.getUserSnapshot();
      const startupIssue = getStartupIssueState();

      sendJson(res, 200, {
        ok: true,
        pid: runtimeInfo.pid(),
        host: runtimeInfo.host(),
        uptimeSec: runtimeInfo.uptimeSec(),
        bindAddress: config.bindAddress,
        runtimeBindAddress: runtimeInfo.runtimeBindAddress ? runtimeInfo.runtimeBindAddress() : config.bindAddress,
        bindOverrideActive: runtimeInfo.bindOverrideActive ? !!runtimeInfo.bindOverrideActive() : false,
        port: config.port,
        mode: "http-agent",
        version: versionInfo.agentVersion,
        network,
        user,
        startupIssue
      });
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/startup/state") {
      sendJson(res, 200, {
        ok: true,
        issue: getStartupIssueState()
      });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/startup/apply_recommended_bind") {
      try {
        const body = await readJsonBody(req);
        if (!isConfigRequestAuthorized(req, state.config, body.token)) {
          unauthorized(res, "startup.apply_recommended_bind", body.token);
          return true;
        }

        const issue = getStartupIssueState();
        if (!issue || issue.issueType !== "bindAddressUnavailable") {
          sendJson(res, 400, { ok: false, error: "No bindAddressUnavailable startup issue" });
          return true;
        }

        const network = processService.getNetworkSnapshot();
        const recommendedHost = String(network.recommendedHost || "").trim();
        if (!isUsableRecommendedBind(recommendedHost)) {
          sendJson(res, 400, { ok: false, error: "No usable IPv4 candidate available now" });
          return true;
        }

        const previousBindAddress = state.config.bindAddress;
        state.config = {
          ...state.config,
          bindAddress: recommendedHost
        };
        configStore.saveConfig(state.config);

        startupStateStore.saveState({
          ...issue,
          status: "applied_restarting",
          previousBindAddress,
          appliedBindAddress: recommendedHost,
          appliedAt: new Date().toISOString(),
          network
        });

        logger.info("startup.bind_recovery.applied", {
          previousBindAddress,
          appliedBindAddress: recommendedHost,
          port: state.config.port
        });

        if (typeof restartAgent === "function") {
          const scheduled = restartAgent("api.startup.apply_recommended_bind");
          if (!scheduled) {
            sendJson(res, 409, { ok: false, error: "Restart already scheduled" });
            return true;
          }
        }

        sendJson(res, 200, {
          ok: true,
          restartScheduled: true,
          bindAddress: recommendedHost,
          config: buildPublicConfig(state.config, versionInfo, { includeApiToken: isLocalRequest(req) })
        });
      } catch (error) {
        logger.error("startup.bind_recovery.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/config") {
      sendJson(res, 200, buildPublicConfig(config, versionInfo, { includeApiToken: isLocalRequest(req) }));
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/presets") {
      sendJson(res, 200, {
        items: getPresetList(config)
      });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/config") {
      try {
        const body = await readJsonBody(req);
        if (!isConfigRequestAuthorized(req, state.config, body.token)) {
          unauthorized(res, "config.update", body.token);
          return true;
        }

        const nextConfig = { ...state.config };

        const bindAddressInput = pickConfigInput(body, "bindAddress", "bind_address");
        if (typeof bindAddressInput === "string" && bindAddressInput.trim()) {
          nextConfig.bindAddress = bindAddressInput.trim();
        }

        const portInput = pickConfigInput(body, "port", null);
        if (portInput !== undefined) {
          const parsed = Number(portInput);
          if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 65535) {
            sendJson(res, 400, { ok: false, error: "Invalid port" });
            return true;
          }
          nextConfig.port = Math.floor(parsed);
        }

        const maxCommandMsInput = pickConfigInput(body, "maxCommandMs", "max_command_ms");
        if (maxCommandMsInput !== undefined) {
          const parsed = Number(maxCommandMsInput);
          if (!Number.isFinite(parsed) || parsed < 1000 || parsed > 600000) {
            sendJson(res, 400, { ok: false, error: "maxCommandMs must be 1000..600000" });
            return true;
          }
          nextConfig.maxCommandMs = Math.floor(parsed);
        }

        const apiTokenInput = pickConfigInput(body, "apiToken", "api_token");
        if (apiTokenInput !== undefined) {
          nextConfig.apiToken = configStore.ensureApiToken(apiTokenInput);
        }

        nextConfig.apiToken = configStore.ensureApiToken(nextConfig.apiToken);

        const allowedPresetsInput = pickConfigInput(body, "allowedPresets", "allowed_presets");
        if (Array.isArray(allowedPresetsInput)) {
          nextConfig.allowedPresets = configStore.normalizeAllowedPresets(allowedPresetsInput);
        }

        const restartRequired =
          nextConfig.port !== state.config.port || nextConfig.bindAddress !== state.config.bindAddress;

        state.config = nextConfig;
        configStore.saveConfig(state.config);

        logger.info("config.update.success", {
          bindAddress: state.config.bindAddress,
          port: state.config.port,
          maxCommandMs: state.config.maxCommandMs,
          allowedPresets: state.config.allowedPresets,
          restartRequired
        });

        sendJson(res, 200, {
          ok: true,
          restartRequired,
          config: buildPublicConfig(state.config, versionInfo, { includeApiToken: isLocalRequest(req) })
        });
      } catch (error) {
        logger.error("config.update.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/command/execute") {
      try {
        const body = await readJsonBody(req);

        logger.info("command.execute.request", {
          hasPreset: !!body.preset,
          preset: body.preset || null,
          shell: body.shell || null,
          rawCommandLength: typeof body.command === "string" ? body.command.length : 0,
          timeoutMs: body.timeoutMs || null,
          tokenProvided: !!body.token
        });

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "command.execute", body.token, {
            preset: body.preset || null
          });
          return true;
        }

        const timeout =
          typeof body.timeoutMs === "number" && body.timeoutMs > 0
            ? Math.min(body.timeoutMs, 600000)
            : state.config.maxCommandMs;

        let shell = "powershell";
        let command = "";

        if (body.preset) {
          if (!presetCommands[body.preset]) {
            logger.warn("command.execute.invalidPreset", { preset: body.preset });
            sendJson(res, 400, { ok: false, error: "Unknown preset" });
            return true;
          }

          if (!state.config.allowedPresets.includes(body.preset)) {
            logger.warn("command.execute.presetNotAllowed", { preset: body.preset });
            sendJson(res, 403, { ok: false, error: "Preset is not allowed by config" });
            return true;
          }

          shell = presetCommands[body.preset].shell;
          command = presetCommands[body.preset].command;
        } else {
          if (!body.command || typeof body.command !== "string") {
            logger.warn("command.execute.missingCommand", {});
            sendJson(res, 400, { ok: false, error: "Missing command" });
            return true;
          }

          shell = String(body.shell || "powershell").toLowerCase();
          command = body.command;
        }

        const result = await processService.runCommand(shell, command, timeout);

        logger.info("command.execute.result", {
          ok: result.exitCode === 0,
          shell,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdoutLength: result.stdout.length,
          stderrLength: result.stderr.length
        });

        sendJson(res, 200, {
          ok: result.exitCode === 0,
          shell,
          command,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdout: result.stdout,
          stderr: result.stderr
        });
      } catch (error) {
        logger.error("command.execute.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/start") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.start", body.token);
          return true;
        }

        const result = processService.startSession(body.shell, body.command, {
          maxRuntimeMs: body.max_runtime_ms !== undefined ? body.max_runtime_ms : body.maxRuntimeMs
        });

        logger.info("process.start.success", {
          sessionId: result.sessionId,
          shell: result.shell,
          pid: result.pid,
          status: result.status,
          hasCommand: typeof body.command === "string" && body.command.trim().length > 0
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.start.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/read") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.read", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const result = await processService.readSession(sessionId, {
          viewMode: body.view_mode !== undefined ? body.view_mode : body.viewMode,
          stdoutOffset: body.stdout_offset !== undefined ? body.stdout_offset : body.stdoutOffset,
          stderrOffset: body.stderr_offset !== undefined ? body.stderr_offset : body.stderrOffset,
          maxChars: body.max_chars !== undefined ? body.max_chars : body.maxChars
        });

        logger.info("process.read.success", {
          sessionId: result.sessionId,
          status: result.status,
          stdoutLength: result.stdout.length,
          stderrLength: result.stderr.length,
          stdoutOffset: result.stdoutOffset,
          stderrOffset: result.stderrOffset,
          hasMore: result.hasMore
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.read.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/write") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.write", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const resolvedInput = resolveProcessWriteInput(body);
        const result = await processService.writeSession(sessionId, resolvedInput);

        logger.info("process.write.success", {
          sessionId: result.sessionId,
          status: result.status,
          acceptedChars: result.acceptedChars
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.write.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/resize") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.resize", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const result = processService.resizeSession(sessionId, {
          cols: body.cols,
          rows: body.rows
        });

        logger.info("process.resize.success", {
          sessionId: result.sessionId,
          status: result.status,
          cols: result.cols,
          rows: result.rows
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.resize.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/terminate") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.terminate", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const result = processService.terminateSession(sessionId, {
          remove: parseBoolean(body.remove, false)
        });

        logger.info("process.terminate.success", {
          sessionId: result.sessionId,
          status: result.status,
          wasRunning: result.wasRunning,
          signalSent: result.signalSent,
          removed: result.removed
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.terminate.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/list") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.list", body.token);
          return true;
        }

        const includeExited = parseBoolean(
          body.include_exited !== undefined ? body.include_exited : body.includeExited,
          true
        );
        const result = processService.listSessions({ includeExited });

        logger.info("process.list.success", {
          includeExited,
          count: result.items.length
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.list.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/list") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.list", body.token);
          return true;
        }

        const result = fileService.listDirectory(body.path, body.depth);
        logger.info("file.list.success", {
          path: result.path,
          depth: result.depth,
          itemCount: result.items.length
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.list.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read", body.token);
          return true;
        }

        const result = fileService.readTextFile(body.path, body.encoding);
        logger.info("file.read.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_segment") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_segment", body.token);
          return true;
        }

        const result = fileService.readTextSegment(body.path, {
          offset: body.offset,
          length: body.length,
          encoding: body.encoding
        });

        logger.info("file.read_segment.success", {
          path: result.path,
          offset: result.offset,
          length: result.length,
          totalBytes: result.totalBytes,
          eof: result.eof,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_segment.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_lines") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_lines", body.token);
          return true;
        }

        const result = fileService.readTextLines(body.path, {
          line_start: body.line_start,
          line_end: body.line_end,
          encoding: body.encoding
        });

        logger.info("file.read_lines.success", {
          path: result.path,
          lineStart: result.lineStart,
          lineEnd: result.lineEnd,
          totalLines: result.totalLines,
          eof: result.eof,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_lines.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/write") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.write", body.token);
          return true;
        }

        const result = fileService.writeTextFile(body.path, body.content, body.encoding);
        logger.info("file.write.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.write.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/edit") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.edit", body.token);
          return true;
        }

        const result = fileService.editTextFile(
          body.path,
          body.old_text,
          body.new_text,
          body.expected_replacements,
          body.encoding
        );
        logger.info("file.edit.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding,
          replacements: result.replacements,
          expectedReplacements: result.expectedReplacements
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.edit.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_base64") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_base64", body.token);
          return true;
        }

        const result = fileService.readBase64File(body.path, {
          offset: body.offset,
          length: body.length
        });

        logger.info("file.read_base64.success", {
          path: result.path,
          offset: result.offset,
          length: result.length,
          totalBytes: result.totalBytes,
          eof: result.eof
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_base64.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/write_base64") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_BASE64_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.write_base64", body.token);
          return true;
        }

        const result = fileService.writeBase64File(body.path, body.base64);
        logger.info("file.write_base64.success", {
          path: result.path,
          sizeBytes: result.sizeBytes
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.write_base64.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    return false;
  }

  return {
    handleApiRequest
  };
}

module.exports = {
  createApiHandler
};

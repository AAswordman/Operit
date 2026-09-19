"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.queryLocalQQBotServiceStatusAsync = queryLocalQQBotServiceStatusAsync;
exports.queryQueuedEventsFromServiceAsync = queryQueuedEventsFromServiceAsync;
exports.removeQueuedEventsFromServiceAsync = removeQueuedEventsFromServiceAsync;
exports.clearQueuedEventsFromServiceAsync = clearQueuedEventsFromServiceAsync;
exports.buildServiceStatusAsync = buildServiceStatusAsync;
exports.stopQQBotServiceInternalAsync = stopQQBotServiceInternalAsync;
exports.ensureQQBotServiceStarted = ensureQQBotServiceStarted;
const qqbot_common_1 = require("./qqbot_common");
const qqbot_openapi_1 = require("./qqbot_openapi");
const qqbot_state_1 = require("./qqbot_state");
const HIDDEN_TERMINAL_EXECUTOR_KEY = "qqbot_gateway_service";
function getLocalServiceBaseUrl() {
    return `http://127.0.0.1:${qqbot_common_1.LOCAL_SERVICE_PORT}`;
}
async function withPromiseTimeout(promise, timeoutMs, label) {
    let timerId = null;
    try {
        return await Promise.race([
            promise,
            new Promise((_resolve, reject) => {
                timerId = setTimeout(() => {
                    reject(new Error(`${label} timed out after ${timeoutMs}ms`));
                }, Math.max(1, timeoutMs));
            })
        ]);
    }
    finally {
        if (timerId != null) {
            clearTimeout(timerId);
        }
    }
}
async function sleepMsAsync(ms) {
    await Tools.System.sleep(ms);
}
async function runTerminalCommand(command, timeoutMs) {
    return await Tools.System.terminal.hiddenExec(command, {
        executorKey: HIDDEN_TERMINAL_EXECUTOR_KEY,
        timeoutMs
    });
}
async function ensureTerminalPythonAvailable() {
    const result = await runTerminalCommand("python3 - <<'PY'\nimport http.server, json, os, socket, ssl, sys, threading, urllib.request\nprint('__PY_OK__')\nPY", 5000);
    if (Number(result.exitCode || 0) !== 0 || !(0, qqbot_common_1.asText)(result.output).includes("__PY_OK__")) {
        throw new Error((0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(result.output).trim(), "python3 is required for qqbot gateway service"));
    }
}
async function readGatewayServiceScriptPath() {
    return await ToolPkg.readResource(qqbot_common_1.TERMINAL_SERVICE_RESOURCE_KEY, qqbot_common_1.TERMINAL_SERVICE_OUTPUT_FILE_NAME, true);
}
function parseProcessPids(output) {
    const matches = [];
    const seen = new Set();
    const lines = output.split(/\r?\n/g);
    for (let index = 0; index < lines.length; index += 1) {
        const line = lines[index].trim();
        if (!line || !/^\d+$/.test(line) || seen.has(line)) {
            continue;
        }
        seen.add(line);
        matches.push(line);
    }
    return matches;
}
function uniqueStrings(values) {
    const result = [];
    const seen = new Set();
    for (let index = 0; index < values.length; index += 1) {
        const trimmed = (0, qqbot_common_1.asText)(values[index]).trim();
        if (!trimmed || seen.has(trimmed)) {
            continue;
        }
        seen.add(trimmed);
        result.push(trimmed);
    }
    return result;
}
async function isProcessAliveAsync(pid) {
    const trimmed = pid.trim();
    if (!trimmed) {
        return false;
    }
    const probe = await runTerminalCommand(`kill -0 ${(0, qqbot_common_1.shellQuote)(trimmed)} >/dev/null 2>&1`, 1500);
    return Number(probe.exitCode ?? 1) === 0;
}
async function listQQBotServicePidsAsync() {
    const stateDir = (0, qqbot_state_1.getStateDirectoryPath)();
    const scriptName = qqbot_common_1.TERMINAL_SERVICE_OUTPUT_FILE_NAME;
    const result = await runTerminalCommand([
        "python3 - <<'PY'",
        "import os",
        `needle = ${JSON.stringify(scriptName)}`,
        `state = ${JSON.stringify(stateDir)}`,
        "for name in os.listdir('/proc'):",
        "    if not name.isdigit():",
        "        continue",
        "    try:",
        "        with open('/proc/%s/cmdline' % name, 'rb') as handle:",
        "            cmd = handle.read().replace(b'\\x00', b' ').decode('utf-8', 'replace')",
        "    except OSError:",
        "        continue",
        "    if needle in cmd and '--state-dir' in cmd and state in cmd:",
        "        print(name)",
        "PY"
    ].join("\n"), 3000);
    return parseProcessPids((0, qqbot_common_1.asText)(result.output));
}
async function requestLocalServiceAsync(path, method, body, timeoutMs) {
    return await (0, qqbot_openapi_1.requestJson)(`${getLocalServiceBaseUrl()}${path}`, method, {
        Accept: "application/json",
        ...(body ? { "Content-Type": "application/json; charset=utf-8" } : {})
    }, body, timeoutMs);
}
async function requestLocalServiceJsonOrThrow(path, method, body, timeoutMs) {
    const response = await requestLocalServiceAsync(path, method, body, timeoutMs);
    if (!response.success) {
        throw new Error((0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(response.json.error), response.body, `Local QQ Bot service ${path} failed`));
    }
    return response.json;
}
async function queryLocalQQBotServiceStatusAsync(timeoutMs = 1200) {
    try {
        const response = await withPromiseTimeout(requestLocalServiceAsync("/status", "GET", null, timeoutMs), timeoutMs + 200, "Local QQ Bot service /status request");
        if (!response.success || !(0, qqbot_common_1.hasOwn)(response.json, "mode")) {
            return null;
        }
        return response.json;
    }
    catch (_error) {
        return null;
    }
}
async function queryQueuedEventsFromServiceAsync(params = {}, timeoutMs = 4000) {
    const body = {};
    if (params.limit !== undefined) {
        body.limit = params.limit;
    }
    if (params.consume !== undefined) {
        body.consume = params.consume;
    }
    if (params.scene !== undefined) {
        body.scene = params.scene;
    }
    if (params.event_type !== undefined) {
        body.eventType = params.event_type;
    }
    if (params.include_raw !== undefined) {
        body.includeRaw = params.include_raw;
    }
    return await requestLocalServiceJsonOrThrow("/events/query", "POST", body, timeoutMs);
}
async function removeQueuedEventsFromServiceAsync(eventKeys, timeoutMs = 4000) {
    return await requestLocalServiceJsonOrThrow("/events/remove", "POST", {
        eventKeys
    }, timeoutMs);
}
async function clearQueuedEventsFromServiceAsync(timeoutMs = 4000) {
    return await requestLocalServiceJsonOrThrow("/events/clear", "POST", {}, timeoutMs);
}
function buildServiceLaunchCommand(scriptPath, snapshot, source) {
    const stateDir = (0, qqbot_state_1.getStateDirectoryPath)();
    const logPath = (0, qqbot_state_1.getServiceLogPath)();
    const sourceLabel = (0, qqbot_common_1.firstNonBlank)(source, "manual");
    const controlToken = (0, qqbot_common_1.createControlToken)();
    return [
        "nohup",
        "python3",
        (0, qqbot_common_1.shellQuote)(scriptPath),
        "--state-dir", (0, qqbot_common_1.shellQuote)(stateDir),
        "--app-id", (0, qqbot_common_1.shellQuote)(snapshot.appId),
        "--app-secret", (0, qqbot_common_1.shellQuote)(snapshot.appSecret),
        "--use-sandbox", (0, qqbot_common_1.shellQuote)(snapshot.useSandbox ? "true" : "false"),
        "--source", (0, qqbot_common_1.shellQuote)(sourceLabel),
        "--package-version", (0, qqbot_common_1.shellQuote)(qqbot_common_1.PACKAGE_VERSION),
        "--intents", (0, qqbot_common_1.shellQuote)(String(qqbot_common_1.DEFAULT_GATEWAY_INTENTS)),
        "--control-token", (0, qqbot_common_1.shellQuote)(controlToken),
        "--control-port", (0, qqbot_common_1.shellQuote)(String(qqbot_common_1.LOCAL_SERVICE_PORT)),
        `> ${(0, qqbot_common_1.shellQuote)(logPath)} 2>&1 & echo $!`
    ].join(" ");
}
async function readServiceLogTailAsync(maxChars = 4000) {
    const log = await (0, qqbot_state_1.readTextFileWithTools)((0, qqbot_state_1.getServiceLogPath)());
    if (log.length <= maxChars) {
        return log;
    }
    return log.slice(log.length - maxChars);
}
function isServiceConfigMatching(runtimeStatus, snapshot) {
    return (0, qqbot_common_1.asText)(runtimeStatus.appId).trim() === snapshot.appId
        && (0, qqbot_common_1.toBoolean)(runtimeStatus.useSandbox, false) === snapshot.useSandbox;
}
async function waitForServiceReadyAsync(timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    let lastStatus = null;
    while (Date.now() <= deadline) {
        const localStatus = await queryLocalQQBotServiceStatusAsync(1200);
        if (localStatus) {
            lastStatus = localStatus;
            if ((0, qqbot_common_1.toBoolean)(localStatus.running, false) && (0, qqbot_common_1.toBoolean)(localStatus.connected, false)) {
                return {
                    ready: true,
                    status: localStatus
                };
            }
        }
        await sleepMsAsync(qqbot_common_1.SERVICE_POLL_INTERVAL_MS);
    }
    return {
        ready: false,
        status: lastStatus || {},
        logTail: await readServiceLogTailAsync()
    };
}
async function launchGatewayServiceAsync(snapshot, source) {
    await ensureTerminalPythonAvailable();
    await (0, qqbot_state_1.deleteFileIfExistsAsync)((0, qqbot_state_1.getServiceLogPath)());
    const scriptPath = await readGatewayServiceScriptPath();
    const command = buildServiceLaunchCommand(scriptPath, snapshot, source);
    const result = await runTerminalCommand(command, 8000);
    if (Number(result.exitCode || 0) !== 0) {
        throw new Error((0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(result.output).trim(), "Failed to launch QQ Bot gateway service"));
    }
    const pid = (0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)((0, qqbot_common_1.asText)(result.output).trim().split(/\s+/g).pop()));
    return {
        command,
        launchOutput: (0, qqbot_common_1.asText)(result.output).trim(),
        pid
    };
}
function buildDefaultQueueSummary() {
    return {
        pendingCount: 0,
        oldestEventAt: "",
        newestEventAt: ""
    };
}
function buildDefaultContactSummary() {
    return {
        userCount: 0,
        groupCount: 0,
        recentUsers: [],
        recentGroups: []
    };
}
async function buildServiceStatusAsync(options = {}) {
    const snapshot = options.snapshot ?? await (0, qqbot_state_1.readConfigSnapshotAsync)();
    const includeContacts = options.includeContacts !== false;
    const localStatus = await queryLocalQQBotServiceStatusAsync();
    if (!localStatus) {
        return {
            healthy: false,
            running: false,
            connected: false,
            configMatchesCurrent: false,
            configuredAppId: snapshot.appId,
            configuredUseSandbox: snapshot.useSandbox,
            runtimeAppId: "",
            runtimeUseSandbox: false,
            servicePids: [],
            stateDirectoryPath: (0, qqbot_state_1.getStateDirectoryPath)(),
            logFilePath: (0, qqbot_state_1.getServiceLogPath)(),
            runtime: {
                mode: "websocket_gateway",
                status: "stopped",
                pid: "",
                startedAt: 0,
                lastPacketAt: 0,
                lastEventAt: 0,
                lastHeartbeatSentAt: 0,
                lastHeartbeatAckAt: 0,
                packetCount: 0,
                eventCount: 0,
                reconnectCount: 0,
                sessionId: "",
                seq: 0,
                gatewayUrl: "",
                botUserId: "",
                botUsername: "",
                lastError: "",
                source: ""
            },
            queue: buildDefaultQueueSummary(),
            ...(includeContacts ? {
                contacts: buildDefaultContactSummary()
            } : {})
        };
    }
    const running = (0, qqbot_common_1.toBoolean)(localStatus.running, false);
    const connected = running && (0, qqbot_common_1.toBoolean)(localStatus.connected, false);
    const pid = (0, qqbot_common_1.asText)(localStatus.pid).trim();
    const configMatchesCurrent = running && isServiceConfigMatching(localStatus, snapshot);
    const queue = (0, qqbot_common_1.hasOwn)(localStatus, "queue") && typeof localStatus.queue === "object" && localStatus.queue
        ? localStatus.queue
        : buildDefaultQueueSummary();
    const contacts = (0, qqbot_common_1.hasOwn)(localStatus, "contacts") && typeof localStatus.contacts === "object" && localStatus.contacts
        ? localStatus.contacts
        : buildDefaultContactSummary();
    return {
        healthy: connected,
        running,
        connected,
        configMatchesCurrent,
        configuredAppId: snapshot.appId,
        configuredUseSandbox: snapshot.useSandbox,
        runtimeAppId: (0, qqbot_common_1.asText)(localStatus.appId).trim(),
        runtimeUseSandbox: (0, qqbot_common_1.toBoolean)(localStatus.useSandbox, false),
        servicePids: pid ? [pid] : [],
        stateDirectoryPath: (0, qqbot_state_1.getStateDirectoryPath)(),
        logFilePath: (0, qqbot_state_1.getServiceLogPath)(),
        runtime: {
            mode: (0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(localStatus.mode), "websocket_gateway"),
            status: running ? (0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(localStatus.status), connected ? "connected" : "running") : "stopped",
            pid,
            startedAt: Number(localStatus.startedAt == null ? 0 : localStatus.startedAt),
            lastPacketAt: Number(localStatus.lastPacketAt == null ? 0 : localStatus.lastPacketAt),
            lastEventAt: Number(localStatus.lastEventAt == null ? 0 : localStatus.lastEventAt),
            lastHeartbeatSentAt: Number(localStatus.lastHeartbeatSentAt == null ? 0 : localStatus.lastHeartbeatSentAt),
            lastHeartbeatAckAt: Number(localStatus.lastHeartbeatAckAt == null ? 0 : localStatus.lastHeartbeatAckAt),
            packetCount: Number(localStatus.packetCount == null ? 0 : localStatus.packetCount),
            eventCount: Number(localStatus.eventCount == null ? 0 : localStatus.eventCount),
            reconnectCount: Number(localStatus.reconnectCount == null ? 0 : localStatus.reconnectCount),
            sessionId: (0, qqbot_common_1.asText)(localStatus.sessionId),
            seq: Number(localStatus.seq == null ? 0 : localStatus.seq),
            gatewayUrl: (0, qqbot_common_1.asText)(localStatus.gatewayUrl),
            botUserId: (0, qqbot_common_1.asText)(localStatus.botUserId),
            botUsername: (0, qqbot_common_1.asText)(localStatus.botUsername),
            lastError: (0, qqbot_common_1.asText)(localStatus.lastError),
            source: (0, qqbot_common_1.asText)(localStatus.source)
        },
        queue,
        ...(includeContacts ? {
            contacts
        } : {})
    };
}
async function waitForProcessExitAsync(pid, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() <= deadline) {
        if (!(await isProcessAliveAsync(pid))) {
            return true;
        }
        await sleepMsAsync(qqbot_common_1.SERVICE_POLL_INTERVAL_MS);
    }
    return false;
}
async function stopProcessByPidAsync(pid, timeoutMs) {
    if (!pid.trim()) {
        return { stopped: true, killedBy: "none" };
    }
    await runTerminalCommand(`kill ${(0, qqbot_common_1.shellQuote)(pid)} >/dev/null 2>&1`, 1500);
    if (await waitForProcessExitAsync(pid, Math.max(800, Math.floor(timeoutMs / 2)))) {
        return { stopped: true, killedBy: "term" };
    }
    await runTerminalCommand(`kill -9 ${(0, qqbot_common_1.shellQuote)(pid)} >/dev/null 2>&1`, 1500);
    const stopped = await waitForProcessExitAsync(pid, Math.max(800, Math.floor(timeoutMs / 2)));
    return { stopped, killedBy: stopped ? "kill9" : "failed" };
}
async function requestLocalServiceStopAsync(timeoutMs) {
    try {
        const response = await requestLocalServiceAsync("/control", "POST", {
            action: "stop"
        }, timeoutMs);
        return response.success && response.json.accepted !== false;
    }
    catch (_error) {
        return false;
    }
}
async function stopQQBotServiceInternalAsync(timeoutMs) {
    const currentStatus = await queryLocalQQBotServiceStatusAsync();
    const candidatePids = uniqueStrings([
        (0, qqbot_common_1.asText)(currentStatus?.pid)
    ]);
    if (candidatePids.length === 0) {
        candidatePids.push(...(await listQQBotServicePidsAsync()));
    }
    const alivePids = [];
    for (let index = 0; index < candidatePids.length; index += 1) {
        const pid = candidatePids[index];
        if (await isProcessAliveAsync(pid)) {
            alivePids.push(pid);
        }
    }
    const running = (0, qqbot_common_1.toBoolean)(currentStatus?.running, false);
    if (!running && alivePids.length === 0) {
        return {
            success: true,
            alreadyStopped: true,
            packageVersion: qqbot_common_1.PACKAGE_VERSION
        };
    }
    const controlRequested = running ? await requestLocalServiceStopAsync(Math.min(timeoutMs, 4000)) : false;
    if (controlRequested) {
        for (let index = 0; index < alivePids.length; index += 1) {
            await waitForProcessExitAsync(alivePids[index], Math.max(1000, Math.floor(timeoutMs / 3)));
        }
    }
    const remainingPids = [];
    for (let index = 0; index < alivePids.length; index += 1) {
        const pid = alivePids[index];
        if (await isProcessAliveAsync(pid)) {
            remainingPids.push(pid);
        }
    }
    if (remainingPids.length === 0) {
        return {
            success: true,
            alreadyStopped: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            stop: controlRequested ? [{ method: "control" }] : []
        };
    }
    const stopResults = [];
    let allStopped = true;
    for (let index = 0; index < remainingPids.length; index += 1) {
        const pid = remainingPids[index];
        const stopped = await stopProcessByPidAsync(pid, timeoutMs);
        stopResults.push({
            pid,
            ...stopped
        });
        if (!(0, qqbot_common_1.toBoolean)(stopped.stopped, false)) {
            allStopped = false;
        }
    }
    return {
        success: allStopped,
        alreadyStopped: false,
        packageVersion: qqbot_common_1.PACKAGE_VERSION,
        pids: remainingPids,
        stop: stopResults
    };
}
async function ensureQQBotServiceStarted(options = {}) {
    const timeoutMs = (0, qqbot_common_1.parsePositiveInt)(options.timeout_ms, "timeout_ms", qqbot_common_1.DEFAULT_SERVICE_WAIT_MS);
    const source = (0, qqbot_common_1.firstNonBlank)(options.source, "manual");
    const allowMissingConfig = (0, qqbot_common_1.parseOptionalBoolean)(options.allow_missing_config, "allow_missing_config") === true;
    const shouldRestart = (0, qqbot_common_1.parseOptionalBoolean)(options.restart, "restart") === true;
    const snapshot = await (0, qqbot_state_1.readConfigSnapshotAsync)();
    if ((!snapshot.appId || !snapshot.appSecret) && allowMissingConfig) {
        return {
            ok: true,
            skipped: true,
            reason: "missing_credentials",
            source,
            lifecycleEvent: (0, qqbot_common_1.firstNonBlank)(options.lifecycle_event, source),
            status: (0, qqbot_state_1.buildStatus)(snapshot)
        };
    }
    if (!snapshot.appId) {
        throw new Error("Missing env: QQBOT_APP_ID");
    }
    if (!snapshot.appSecret) {
        throw new Error("Missing env: QQBOT_APP_SECRET");
    }
    const localStatus = await queryLocalQQBotServiceStatusAsync();
    const running = (0, qqbot_common_1.toBoolean)(localStatus?.running, false);
    const connected = running && (0, qqbot_common_1.toBoolean)(localStatus?.connected, false);
    const configMatchesCurrent = running && localStatus ? isServiceConfigMatching(localStatus, snapshot) : false;
    if (running && connected && configMatchesCurrent && !shouldRestart) {
        return {
            ok: true,
            started: false,
            source,
            lifecycleEvent: (0, qqbot_common_1.firstNonBlank)(options.lifecycle_event, source),
            reason: "already_running",
            status: (0, qqbot_state_1.buildStatus)(snapshot),
            service: await buildServiceStatusAsync({ snapshot })
        };
    }
    if (running && !connected && configMatchesCurrent && !shouldRestart) {
        const ready = await waitForServiceReadyAsync(timeoutMs);
        if ((0, qqbot_common_1.toBoolean)(ready.ready, false)) {
            return {
                ok: true,
                started: false,
                source,
                lifecycleEvent: (0, qqbot_common_1.firstNonBlank)(options.lifecycle_event, source),
                reason: "already_running_wait_ready",
                status: (0, qqbot_state_1.buildStatus)(snapshot),
                service: await buildServiceStatusAsync({ snapshot }),
                ready
            };
        }
    }
    if (running || shouldRestart) {
        await stopQQBotServiceInternalAsync(timeoutMs);
    }
    const launch = await launchGatewayServiceAsync(snapshot, source);
    const ready = await waitForServiceReadyAsync(timeoutMs);
    if (!(0, qqbot_common_1.toBoolean)(ready.ready, false)) {
        throw new Error((0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(ready.status && ready.status.lastError), (0, qqbot_common_1.asText)(ready.logTail), "QQ Bot gateway service failed to become ready"));
    }
    return {
        ok: true,
        started: true,
        source,
        lifecycleEvent: (0, qqbot_common_1.firstNonBlank)(options.lifecycle_event, source),
        status: (0, qqbot_state_1.buildStatus)(snapshot),
        launch,
        service: await buildServiceStatusAsync({ snapshot })
    };
}

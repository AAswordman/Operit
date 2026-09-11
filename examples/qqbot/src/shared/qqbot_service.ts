import {
    DEFAULT_GATEWAY_INTENTS,
    DEFAULT_SERVICE_WAIT_MS,
    EnsureQQBotServiceOptions,
    JsonObject,
    LOCAL_SERVICE_PORT,
    PACKAGE_VERSION,
    QQBotConfigSnapshot,
    QQBotReceiveEventsParams,
    RequestJsonResult,
    SERVICE_POLL_INTERVAL_MS,
    TERMINAL_SERVICE_OUTPUT_FILE_NAME,
    TERMINAL_SERVICE_RESOURCE_KEY,
    asText,
    createControlToken,
    firstNonBlank,
    hasOwn,
    parseOptionalBoolean,
    parsePositiveInt,
    safeErrorMessage,
    shellQuote,
    toBoolean
} from "./qqbot_common";
import { requestJson } from "./qqbot_openapi";
import {
    buildStatus,
    deleteFileIfExistsAsync,
    getServiceLogPath,
    getStateDirectoryPath,
    readConfigSnapshotAsync,
    readTextFileWithTools
} from "./qqbot_state";

const HIDDEN_TERMINAL_EXECUTOR_KEY = "qqbot_gateway_service";

function getLocalServiceBaseUrl(): string {
    return `http://127.0.0.1:${LOCAL_SERVICE_PORT}`;
}

async function withPromiseTimeout<T>(
    promise: Promise<T>,
    timeoutMs: number,
    label: string
): Promise<T> {
    let timerId: ReturnType<typeof setTimeout> | null = null;
    try {
        return await Promise.race([
            promise,
            new Promise<T>((_resolve, reject) => {
                timerId = setTimeout(() => {
                    reject(new Error(`${label} timed out after ${timeoutMs}ms`));
                }, Math.max(1, timeoutMs));
            })
        ]);
    } finally {
        if (timerId != null) {
            clearTimeout(timerId);
        }
    }
}

async function sleepMsAsync(ms: number): Promise<void> {
    await Tools.System.sleep(ms);
}

async function runTerminalCommand(command: string, timeoutMs: number): Promise<any> {
    return await Tools.System.terminal.hiddenExec(command, {
        executorKey: HIDDEN_TERMINAL_EXECUTOR_KEY,
        timeoutMs
    });
}

async function ensureTerminalPythonAvailable(): Promise<void> {
    const result = await runTerminalCommand(
        "python3 - <<'PY'\nimport http.server, json, os, socket, ssl, sys, threading, urllib.request\nprint('__PY_OK__')\nPY",
        5000
    );
    if (Number(result.exitCode || 0) !== 0 || !asText(result.output).includes("__PY_OK__")) {
        throw new Error(firstNonBlank(asText(result.output).trim(), "python3 is required for qqbot gateway service"));
    }
}

async function readGatewayServiceScriptPath(): Promise<string> {
    return await ToolPkg.readResource(
        TERMINAL_SERVICE_RESOURCE_KEY,
        TERMINAL_SERVICE_OUTPUT_FILE_NAME,
        true
    );
}

function parseProcessPids(output: string): string[] {
    const matches: string[] = [];
    const seen = new Set<string>();
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

function uniqueStrings(values: Array<string | null | undefined>): string[] {
    const result: string[] = [];
    const seen = new Set<string>();
    for (let index = 0; index < values.length; index += 1) {
        const trimmed = asText(values[index]).trim();
        if (!trimmed || seen.has(trimmed)) {
            continue;
        }
        seen.add(trimmed);
        result.push(trimmed);
    }
    return result;
}

async function isProcessAliveAsync(pid: string): Promise<boolean> {
    const trimmed = pid.trim();
    if (!trimmed) {
        return false;
    }
    const probe = await runTerminalCommand(`kill -0 ${shellQuote(trimmed)} >/dev/null 2>&1`, 1500);
    return Number(probe.exitCode ?? 1) === 0;
}

async function listQQBotServicePidsAsync(): Promise<string[]> {
    const stateDir = getStateDirectoryPath();
    const scriptName = TERMINAL_SERVICE_OUTPUT_FILE_NAME;
    const result = await runTerminalCommand(
        [
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
        ].join("\n"),
        3000
    );
    return parseProcessPids(asText(result.output));
}

async function requestLocalServiceAsync(
    path: string,
    method: "GET" | "POST",
    body: JsonObject | null,
    timeoutMs: number
): Promise<RequestJsonResult> {
    return await requestJson(
        `${getLocalServiceBaseUrl()}${path}`,
        method,
        {
            Accept: "application/json",
            ...(body ? { "Content-Type": "application/json; charset=utf-8" } : {})
        },
        body,
        timeoutMs
    );
}

async function requestLocalServiceJsonOrThrow(
    path: string,
    method: "GET" | "POST",
    body: JsonObject | null,
    timeoutMs: number
): Promise<JsonObject> {
    const response = await requestLocalServiceAsync(path, method, body, timeoutMs);
    if (!response.success) {
        throw new Error(firstNonBlank(asText(response.json.error), response.body, `Local QQ Bot service ${path} failed`));
    }
    return response.json;
}

export async function queryLocalQQBotServiceStatusAsync(timeoutMs = 1200): Promise<JsonObject | null> {
    try {
        const response = await withPromiseTimeout(
            requestLocalServiceAsync("/status", "GET", null, timeoutMs),
            timeoutMs + 200,
            "Local QQ Bot service /status request"
        );
        if (!response.success || !hasOwn(response.json, "mode")) {
            return null;
        }
        return response.json;
    } catch (_error) {
        return null;
    }
}

export async function queryQueuedEventsFromServiceAsync(
    params: QQBotReceiveEventsParams = {},
    timeoutMs = 4000
): Promise<JsonObject> {
    const body: JsonObject = {};
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
    return await requestLocalServiceJsonOrThrow(
        "/events/query",
        "POST",
        body,
        timeoutMs
    );
}

export async function removeQueuedEventsFromServiceAsync(eventKeys: string[], timeoutMs = 4000): Promise<JsonObject> {
    return await requestLocalServiceJsonOrThrow(
        "/events/remove",
        "POST",
        {
            eventKeys
        },
        timeoutMs
    );
}

export async function clearQueuedEventsFromServiceAsync(timeoutMs = 4000): Promise<JsonObject> {
    return await requestLocalServiceJsonOrThrow("/events/clear", "POST", {}, timeoutMs);
}

function buildServiceLaunchCommand(scriptPath: string, snapshot: QQBotConfigSnapshot, source: string): string {
    const stateDir = getStateDirectoryPath();
    const logPath = getServiceLogPath();
    const sourceLabel = firstNonBlank(source, "manual");
    const controlToken = createControlToken();
    return [
        "nohup",
        "python3",
        shellQuote(scriptPath),
        "--state-dir", shellQuote(stateDir),
        "--app-id", shellQuote(snapshot.appId),
        "--app-secret", shellQuote(snapshot.appSecret),
        "--use-sandbox", shellQuote(snapshot.useSandbox ? "true" : "false"),
        "--source", shellQuote(sourceLabel),
        "--package-version", shellQuote(PACKAGE_VERSION),
        "--intents", shellQuote(String(DEFAULT_GATEWAY_INTENTS)),
        "--control-token", shellQuote(controlToken),
        "--control-port", shellQuote(String(LOCAL_SERVICE_PORT)),
        `> ${shellQuote(logPath)} 2>&1 & echo $!`
    ].join(" ");
}

async function readServiceLogTailAsync(maxChars = 4000): Promise<string> {
    const log = await readTextFileWithTools(getServiceLogPath());
    if (log.length <= maxChars) {
        return log;
    }
    return log.slice(log.length - maxChars);
}

function isServiceConfigMatching(runtimeStatus: JsonObject, snapshot: QQBotConfigSnapshot): boolean {
    return asText(runtimeStatus.appId).trim() === snapshot.appId
        && toBoolean(runtimeStatus.useSandbox, false) === snapshot.useSandbox;
}

async function waitForServiceReadyAsync(timeoutMs: number): Promise<JsonObject> {
    const deadline = Date.now() + timeoutMs;
    let lastStatus: JsonObject | null = null;
    while (Date.now() <= deadline) {
        const localStatus = await queryLocalQQBotServiceStatusAsync(1200);
        if (localStatus) {
            lastStatus = localStatus;
            if (toBoolean(localStatus.running, false) && toBoolean(localStatus.connected, false)) {
                return {
                    ready: true,
                    status: localStatus
                };
            }
        }
        await sleepMsAsync(SERVICE_POLL_INTERVAL_MS);
    }
    return {
        ready: false,
        status: lastStatus || {},
        logTail: await readServiceLogTailAsync()
    };
}

async function launchGatewayServiceAsync(snapshot: QQBotConfigSnapshot, source: string): Promise<JsonObject> {
    await ensureTerminalPythonAvailable();
    await deleteFileIfExistsAsync(getServiceLogPath());
    const scriptPath = await readGatewayServiceScriptPath();
    const command = buildServiceLaunchCommand(scriptPath, snapshot, source);
    const result = await runTerminalCommand(command, 8000);
    if (Number(result.exitCode || 0) !== 0) {
        throw new Error(firstNonBlank(asText(result.output).trim(), "Failed to launch QQ Bot gateway service"));
    }

    const pid = firstNonBlank(
        asText(asText(result.output).trim().split(/\s+/g).pop())
    );
    return {
        command,
        launchOutput: asText(result.output).trim(),
        pid
    };
}

function buildDefaultQueueSummary(): JsonObject {
    return {
        pendingCount: 0,
        oldestEventAt: "",
        newestEventAt: ""
    };
}

function buildDefaultContactSummary(): JsonObject {
    return {
        userCount: 0,
        groupCount: 0,
        recentUsers: [],
        recentGroups: []
    };
}

export async function buildServiceStatusAsync(options: {
    snapshot?: QQBotConfigSnapshot;
    includeContacts?: boolean;
} = {}): Promise<JsonObject> {
    const snapshot = options.snapshot ?? await readConfigSnapshotAsync();
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
            stateDirectoryPath: getStateDirectoryPath(),
            logFilePath: getServiceLogPath(),
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

    const running = toBoolean(localStatus.running, false);
    const connected = running && toBoolean(localStatus.connected, false);
    const pid = asText(localStatus.pid).trim();
    const configMatchesCurrent = running && isServiceConfigMatching(localStatus, snapshot);
    const queue = hasOwn(localStatus, "queue") && typeof localStatus.queue === "object" && localStatus.queue
        ? localStatus.queue as JsonObject
        : buildDefaultQueueSummary();
    const contacts = hasOwn(localStatus, "contacts") && typeof localStatus.contacts === "object" && localStatus.contacts
        ? localStatus.contacts as JsonObject
        : buildDefaultContactSummary();

    return {
        healthy: connected,
        running,
        connected,
        configMatchesCurrent,
        configuredAppId: snapshot.appId,
        configuredUseSandbox: snapshot.useSandbox,
        runtimeAppId: asText(localStatus.appId).trim(),
        runtimeUseSandbox: toBoolean(localStatus.useSandbox, false),
        servicePids: pid ? [pid] : [],
        stateDirectoryPath: getStateDirectoryPath(),
        logFilePath: getServiceLogPath(),
        runtime: {
            mode: firstNonBlank(asText(localStatus.mode), "websocket_gateway"),
            status: running ? firstNonBlank(asText(localStatus.status), connected ? "connected" : "running") : "stopped",
            pid,
            startedAt: Number(localStatus.startedAt == null ? 0 : localStatus.startedAt),
            lastPacketAt: Number(localStatus.lastPacketAt == null ? 0 : localStatus.lastPacketAt),
            lastEventAt: Number(localStatus.lastEventAt == null ? 0 : localStatus.lastEventAt),
            lastHeartbeatSentAt: Number(localStatus.lastHeartbeatSentAt == null ? 0 : localStatus.lastHeartbeatSentAt),
            lastHeartbeatAckAt: Number(localStatus.lastHeartbeatAckAt == null ? 0 : localStatus.lastHeartbeatAckAt),
            packetCount: Number(localStatus.packetCount == null ? 0 : localStatus.packetCount),
            eventCount: Number(localStatus.eventCount == null ? 0 : localStatus.eventCount),
            reconnectCount: Number(localStatus.reconnectCount == null ? 0 : localStatus.reconnectCount),
            sessionId: asText(localStatus.sessionId),
            seq: Number(localStatus.seq == null ? 0 : localStatus.seq),
            gatewayUrl: asText(localStatus.gatewayUrl),
            botUserId: asText(localStatus.botUserId),
            botUsername: asText(localStatus.botUsername),
            lastError: asText(localStatus.lastError),
            source: asText(localStatus.source)
        },
        queue,
        ...(includeContacts ? {
            contacts
        } : {})
    };
}

async function waitForProcessExitAsync(pid: string, timeoutMs: number): Promise<boolean> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() <= deadline) {
        if (!(await isProcessAliveAsync(pid))) {
            return true;
        }
        await sleepMsAsync(SERVICE_POLL_INTERVAL_MS);
    }
    return false;
}

async function stopProcessByPidAsync(pid: string, timeoutMs: number): Promise<JsonObject> {
    if (!pid.trim()) {
        return { stopped: true, killedBy: "none" };
    }
    await runTerminalCommand(`kill ${shellQuote(pid)} >/dev/null 2>&1`, 1500);
    if (await waitForProcessExitAsync(pid, Math.max(800, Math.floor(timeoutMs / 2)))) {
        return { stopped: true, killedBy: "term" };
    }
    await runTerminalCommand(`kill -9 ${shellQuote(pid)} >/dev/null 2>&1`, 1500);
    const stopped = await waitForProcessExitAsync(pid, Math.max(800, Math.floor(timeoutMs / 2)));
    return { stopped, killedBy: stopped ? "kill9" : "failed" };
}

async function requestLocalServiceStopAsync(timeoutMs: number): Promise<boolean> {
    try {
        const response = await requestLocalServiceAsync(
            "/control",
            "POST",
            {
                action: "stop"
            },
            timeoutMs
        );
        return response.success && response.json.accepted !== false;
    } catch (_error) {
        return false;
    }
}

export async function stopQQBotServiceInternalAsync(timeoutMs: number): Promise<JsonObject> {
    const currentStatus = await queryLocalQQBotServiceStatusAsync();
    const candidatePids = uniqueStrings([
        asText(currentStatus?.pid)
    ]);
    if (candidatePids.length === 0) {
        candidatePids.push(...(await listQQBotServicePidsAsync()));
    }
    const alivePids: string[] = [];
    for (let index = 0; index < candidatePids.length; index += 1) {
        const pid = candidatePids[index];
        if (await isProcessAliveAsync(pid)) {
            alivePids.push(pid);
        }
    }

    const running = toBoolean(currentStatus?.running, false);
    if (!running && alivePids.length === 0) {
        return {
            success: true,
            alreadyStopped: true,
            packageVersion: PACKAGE_VERSION
        };
    }

    const controlRequested = running ? await requestLocalServiceStopAsync(Math.min(timeoutMs, 4000)) : false;
    if (controlRequested) {
        for (let index = 0; index < alivePids.length; index += 1) {
            await waitForProcessExitAsync(alivePids[index], Math.max(1000, Math.floor(timeoutMs / 3)));
        }
    }

    const remainingPids: string[] = [];
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
            packageVersion: PACKAGE_VERSION,
            stop: controlRequested ? [{ method: "control" }] : []
        };
    }

    const stopResults: JsonObject[] = [];
    let allStopped = true;
    for (let index = 0; index < remainingPids.length; index += 1) {
        const pid = remainingPids[index];
        const stopped = await stopProcessByPidAsync(pid, timeoutMs);
        stopResults.push({
            pid,
            ...stopped
        });
        if (!toBoolean(stopped.stopped, false)) {
            allStopped = false;
        }
    }

    return {
        success: allStopped,
        alreadyStopped: false,
        packageVersion: PACKAGE_VERSION,
        pids: remainingPids,
        stop: stopResults
    };
}

export async function ensureQQBotServiceStarted(options: EnsureQQBotServiceOptions = {}): Promise<JsonObject> {
    const timeoutMs = parsePositiveInt(options.timeout_ms, "timeout_ms", DEFAULT_SERVICE_WAIT_MS);
    const source = firstNonBlank(options.source, "manual");
    const allowMissingConfig = parseOptionalBoolean(options.allow_missing_config, "allow_missing_config") === true;
    const shouldRestart = parseOptionalBoolean(options.restart, "restart") === true;
    const snapshot = await readConfigSnapshotAsync();

    if ((!snapshot.appId || !snapshot.appSecret) && allowMissingConfig) {
        return {
            ok: true,
            skipped: true,
            reason: "missing_credentials",
            source,
            lifecycleEvent: firstNonBlank(options.lifecycle_event, source),
            status: buildStatus(snapshot)
        };
    }

    if (!snapshot.appId) {
        throw new Error("Missing env: QQBOT_APP_ID");
    }
    if (!snapshot.appSecret) {
        throw new Error("Missing env: QQBOT_APP_SECRET");
    }

    const localStatus = await queryLocalQQBotServiceStatusAsync();
    const running = toBoolean(localStatus?.running, false);
    const connected = running && toBoolean(localStatus?.connected, false);
    const configMatchesCurrent = running && localStatus ? isServiceConfigMatching(localStatus, snapshot) : false;

    if (running && connected && configMatchesCurrent && !shouldRestart) {
        return {
            ok: true,
            started: false,
            source,
            lifecycleEvent: firstNonBlank(options.lifecycle_event, source),
            reason: "already_running",
            status: buildStatus(snapshot),
            service: await buildServiceStatusAsync({ snapshot })
        };
    }

    if (running && !connected && configMatchesCurrent && !shouldRestart) {
        const ready = await waitForServiceReadyAsync(timeoutMs);
        if (toBoolean(ready.ready, false)) {
            return {
                ok: true,
                started: false,
                source,
                lifecycleEvent: firstNonBlank(options.lifecycle_event, source),
                reason: "already_running_wait_ready",
                status: buildStatus(snapshot),
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
    if (!toBoolean(ready.ready, false)) {
        throw new Error(
            firstNonBlank(
                asText(ready.status && (ready.status as JsonObject).lastError),
                asText(ready.logTail),
                "QQ Bot gateway service failed to become ready"
            )
        );
    }

    return {
        ok: true,
        started: true,
        source,
        lifecycleEvent: firstNonBlank(options.lifecycle_event, source),
        status: buildStatus(snapshot),
        launch,
        service: await buildServiceStatusAsync({ snapshot })
    };
}

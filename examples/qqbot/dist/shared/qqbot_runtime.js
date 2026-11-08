"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ensureQQBotServiceStarted = void 0;
exports.onQQBotListenerApplicationCreate = onQQBotListenerApplicationCreate;
exports.onQQBotListenerApplicationForeground = onQQBotListenerApplicationForeground;
exports.qqbot_configure = qqbot_configure;
exports.qqbot_status = qqbot_status;
exports.qqbot_dashboard_status = qqbot_dashboard_status;
exports.qqbot_service_start = qqbot_service_start;
exports.qqbot_service_stop = qqbot_service_stop;
exports.qqbot_receive_events = qqbot_receive_events;
exports.qqbot_clear_events = qqbot_clear_events;
exports.qqbot_test_connection = qqbot_test_connection;
exports.qqbot_send_c2c_message = qqbot_send_c2c_message;
exports.qqbot_send_group_message = qqbot_send_group_message;
const qqbot_common_1 = require("./qqbot_common");
const qqbot_state_1 = require("./qqbot_state");
const qqbot_openapi_1 = require("./qqbot_openapi");
const qqbot_service_1 = require("./qqbot_service");
Object.defineProperty(exports, "ensureQQBotServiceStarted", { enumerable: true, get: function () { return qqbot_service_1.ensureQQBotServiceStarted; } });
const qqbot_auto_reply_1 = require("./qqbot_auto_reply");
const qqbot_common_2 = require("./qqbot_common");
function logQQBotRuntime(message) {
    console.log(`[qqbot_runtime] ${message}`);
}
function previewJson(value, maxLength = 1200) {
    try {
        const text = JSON.stringify(value);
        if (typeof text !== "string") {
            return "";
        }
        return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
    }
    catch (_error) {
        return "[unserializable]";
    }
}
async function receiveQueuedEventsAsync(params = {}) {
    const limit = Math.min(qqbot_common_2.MAX_RECEIVE_LIMIT, (0, qqbot_common_1.parsePositiveInt)(params.limit, "limit", qqbot_common_2.DEFAULT_RECEIVE_LIMIT));
    const consume = (0, qqbot_common_1.parseOptionalBoolean)(params.consume, "consume") !== false;
    const includeRaw = (0, qqbot_common_1.parseOptionalBoolean)(params.include_raw, "include_raw") === true;
    const scene = (0, qqbot_common_1.asText)(params.scene).trim().toLowerCase();
    const eventType = (0, qqbot_common_1.asText)(params.event_type).trim();
    const result = await (0, qqbot_service_1.queryQueuedEventsFromServiceAsync)({
        limit,
        consume,
        scene,
        event_type: eventType,
        include_raw: includeRaw
    });
    return {
        consume,
        filter: {
            scene,
            eventType
        },
        returnedCount: Number(result.returnedCount ?? 0),
        remainingCount: Number(result.remainingCount ?? 0),
        events: Array.isArray(result.events) ? result.events : []
    };
}
async function clearQueuedEventsInternalAsync() {
    return await (0, qqbot_service_1.clearQueuedEventsFromServiceAsync)();
}
async function qqbot_configure(params = {}) {
    try {
        const before = await (0, qqbot_state_1.readConfigSnapshotAsync)();
        const updatedEnvironmentKeys = [];
        const updatedConfigFields = [];
        if ((0, qqbot_common_1.hasOwn)(params, "app_id")) {
            await (0, qqbot_state_1.writeEnv)(qqbot_common_2.ENV_KEYS.appId, (0, qqbot_common_1.asText)(params.app_id).trim());
            updatedEnvironmentKeys.push(qqbot_common_2.ENV_KEYS.appId);
        }
        if ((0, qqbot_common_1.hasOwn)(params, "app_secret")) {
            await (0, qqbot_state_1.writeEnv)(qqbot_common_2.ENV_KEYS.appSecret, (0, qqbot_common_1.asText)(params.app_secret).trim());
            updatedEnvironmentKeys.push(qqbot_common_2.ENV_KEYS.appSecret);
        }
        if ((0, qqbot_common_1.hasOwn)(params, "use_sandbox")) {
            await (0, qqbot_state_1.updatePersistedConfigAsync)({
                useSandbox: (0, qqbot_common_1.parseOptionalBoolean)(params.use_sandbox, "use_sandbox") === true
            });
            updatedConfigFields.push("useSandbox");
        }
        const after = await (0, qqbot_state_1.readConfigSnapshotAsync)();
        const status = (0, qqbot_state_1.buildStatus)(after);
        const shouldTest = (0, qqbot_common_1.parseOptionalBoolean)(params.test_connection, "test_connection") === true;
        const shouldRestart = (0, qqbot_common_1.parseOptionalBoolean)(params.restart_service, "restart_service") === true;
        const credentialsChanged = (0, qqbot_common_1.hasOwn)(params, "app_id") || (0, qqbot_common_1.hasOwn)(params, "app_secret");
        const useSandboxChanged = before.useSandbox !== after.useSandbox;
        const credentialsReady = !!after.appId && !!after.appSecret;
        let serviceResult = null;
        if (!credentialsReady || !after.listenerEnabled) {
            serviceResult = await (0, qqbot_service_1.stopQQBotServiceInternalAsync)(qqbot_common_1.DEFAULT_SERVICE_WAIT_MS);
        }
        else {
            serviceResult = await (0, qqbot_service_1.ensureQQBotServiceStarted)({
                restart: shouldRestart || credentialsChanged || useSandboxChanged,
                timeout_ms: qqbot_common_1.DEFAULT_SERVICE_WAIT_MS,
                source: "qqbot_configure"
            });
        }
        const result = {
            success: true,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            updatedEnvironmentKeys,
            updatedConfigFields,
            status,
            service: serviceResult
        };
        if (shouldTest) {
            result.connection = await qqbot_test_connection();
            result.success = !!result.connection.success;
        }
        return result;
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_status(params = {}) {
    try {
        const snapshot = await (0, qqbot_state_1.readConfigSnapshotAsync)();
        const summaryOnly = (0, qqbot_common_1.parseOptionalBoolean)(params.summary_only, "summary_only") === true;
        const service = await (0, qqbot_service_1.buildServiceStatusAsync)({
            snapshot,
            includeContacts: !summaryOnly
        });
        return {
            success: true,
            ...(0, qqbot_state_1.buildStatus)(snapshot),
            service,
            queue: service.queue
        };
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_dashboard_status(params = {}) {
    try {
        const snapshot = await (0, qqbot_state_1.readConfigSnapshotAsync)();
        const summaryOnly = (0, qqbot_common_1.parseOptionalBoolean)(params.summary_only, "summary_only") === true;
        logQQBotRuntime(`dashboard status start: summaryOnly=${summaryOnly}`);
        const [service, autoReply] = await Promise.all([
            (0, qqbot_service_1.buildServiceStatusAsync)({
                snapshot,
                includeContacts: !summaryOnly
            }),
            (0, qqbot_auto_reply_1.qqbot_auto_reply_status)({
                summary_only: summaryOnly
            })
        ]);
        if (autoReply.success === false) {
            console.error(`[qqbot_runtime] dashboard autoReply status failed: ${previewJson(autoReply)}`);
        }
        return {
            success: true,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            ...(0, qqbot_state_1.buildStatus)(snapshot),
            service,
            queue: service.queue,
            autoReply
        };
    }
    catch (error) {
        console.error(`[qqbot_runtime] dashboard status failed: ${(0, qqbot_common_1.safeErrorMessage)(error)}`);
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_service_start(params = {}) {
    try {
        await (0, qqbot_state_1.updatePersistedConfigAsync)({
            listenerEnabled: true
        });
        try {
            return {
                success: true,
                packageVersion: qqbot_common_1.PACKAGE_VERSION,
                listenerEnabled: true,
                ...(await (0, qqbot_service_1.ensureQQBotServiceStarted)({
                    restart: (0, qqbot_common_1.parseOptionalBoolean)(params.restart, "restart") === true,
                    timeout_ms: (0, qqbot_common_1.parsePositiveInt)(params.timeout_ms, "timeout_ms", qqbot_common_1.DEFAULT_SERVICE_WAIT_MS),
                    source: "qqbot_service_start"
                }))
            };
        }
        catch (startError) {
            return {
                success: true,
                packageVersion: qqbot_common_1.PACKAGE_VERSION,
                listenerEnabled: true,
                warning: (0, qqbot_common_1.safeErrorMessage)(startError),
                service: await (0, qqbot_service_1.buildServiceStatusAsync)()
            };
        }
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_service_stop(params = {}) {
    try {
        const timeoutMs = (0, qqbot_common_1.parsePositiveInt)(params.timeout_ms, "timeout_ms", qqbot_common_1.DEFAULT_SERVICE_WAIT_MS);
        await (0, qqbot_state_1.updatePersistedConfigAsync)({
            listenerEnabled: false
        });
        try {
            await (0, qqbot_auto_reply_1.qqbot_auto_reply_configure)({
                enabled: false
            });
        }
        catch (autoReplyError) {
            console.error(`[qqbot_runtime] disable auto-reply after listener stop failed: ${(0, qqbot_common_1.safeErrorMessage)(autoReplyError)}`);
        }
        let result;
        try {
            result = await (0, qqbot_service_1.stopQQBotServiceInternalAsync)(timeoutMs);
        }
        catch (stopError) {
            console.error(`[qqbot_runtime] stop gateway after listener disable failed: ${(0, qqbot_common_1.safeErrorMessage)(stopError)}`);
            return {
                success: true,
                packageVersion: qqbot_common_1.PACKAGE_VERSION,
                listenerEnabled: false,
                warning: (0, qqbot_common_1.safeErrorMessage)(stopError),
                service: await (0, qqbot_service_1.buildServiceStatusAsync)()
            };
        }
        return {
            ...result,
            listenerEnabled: false,
            service: await (0, qqbot_service_1.buildServiceStatusAsync)()
        };
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_receive_events(params = {}) {
    try {
        const autoStart = (0, qqbot_common_1.parseOptionalBoolean)(params.auto_start, "auto_start") !== false;
        if (autoStart) {
            await (0, qqbot_service_1.ensureQQBotServiceStarted)({
                allow_missing_config: false,
                timeout_ms: qqbot_common_1.DEFAULT_SERVICE_WAIT_MS,
                source: "qqbot_receive_events"
            });
        }
        return {
            success: true,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            ...(await receiveQueuedEventsAsync(params)),
            service: await (0, qqbot_service_1.buildServiceStatusAsync)()
        };
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_clear_events() {
    try {
        const cleared = await clearQueuedEventsInternalAsync();
        const service = await (0, qqbot_service_1.buildServiceStatusAsync)({
            includeContacts: false
        });
        return {
            success: true,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            ...cleared,
            queue: service.queue
        };
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_test_connection(params = {}) {
    try {
        const timeoutMs = (0, qqbot_openapi_1.resolveTimeoutMs)(params.timeout_ms);
        const snapshot = await (0, qqbot_state_1.requireConfiguredSnapshotAsync)();
        const token = await (0, qqbot_openapi_1.fetchAccessToken)(snapshot, timeoutMs);
        const me = await (0, qqbot_openapi_1.openApiRequest)(snapshot, "/users/@me", "GET", null, timeoutMs);
        const gateway = await (0, qqbot_openapi_1.fetchGatewayInfo)(snapshot, timeoutMs);
        return {
            success: me.success,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            accessTokenType: token.tokenType,
            accessTokenExpiresIn: token.expiresIn,
            httpStatus: me.statusCode,
            profile: me.json,
            gateway,
            status: (0, qqbot_state_1.buildStatus)(snapshot),
            error: me.success ? "" : (0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(me.json.message), `HTTP ${me.statusCode}`)
        };
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function onQQBotListenerApplicationCreate() {
    try {
        logQQBotRuntime("listener create hook start");
        const snapshot = await (0, qqbot_state_1.readConfigSnapshotAsync)();
        if (!snapshot.listenerEnabled) {
            await (0, qqbot_service_1.stopQQBotServiceInternalAsync)(qqbot_common_1.DEFAULT_SERVICE_WAIT_MS);
            logQQBotRuntime("listener create hook skipped: listener disabled");
            return {
                ok: true,
                listenerEnabled: false,
                started: false
            };
        }
        const result = await (0, qqbot_service_1.ensureQQBotServiceStarted)({
            allow_missing_config: false,
            timeout_ms: qqbot_common_1.DEFAULT_SERVICE_WAIT_MS,
            source: "application_on_create_listener"
        });
        logQQBotRuntime(`listener create hook completed: ${JSON.stringify({
            started: result.started === true,
            reason: result.reason ?? "",
            source: result.source ?? ""
        })}`);
        return {
            ok: true,
            listenerEnabled: true,
            started: result.started === true,
            result
        };
    }
    catch (error) {
        console.error(`[qqbot_runtime] listener create hook failed: ${(0, qqbot_common_1.safeErrorMessage)(error)}`);
        return {
            ok: false,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function onQQBotListenerApplicationForeground() {
    try {
        logQQBotRuntime("listener foreground hook start");
        const snapshot = await (0, qqbot_state_1.readConfigSnapshotAsync)();
        if (!snapshot.listenerEnabled) {
            logQQBotRuntime("listener foreground hook skipped: listener disabled");
            return {
                ok: true,
                listenerEnabled: false,
                started: false
            };
        }
        const result = await (0, qqbot_service_1.ensureQQBotServiceStarted)({
            allow_missing_config: false,
            timeout_ms: qqbot_common_1.DEFAULT_SERVICE_WAIT_MS,
            source: "application_on_foreground_listener"
        });
        logQQBotRuntime(`listener foreground hook completed: ${JSON.stringify({
            started: result.started === true,
            reason: result.reason ?? "",
            source: result.source ?? ""
        })}`);
        return {
            ok: true,
            listenerEnabled: true,
            started: result.started === true,
            result
        };
    }
    catch (error) {
        console.error(`[qqbot_runtime] listener foreground hook failed: ${(0, qqbot_common_1.safeErrorMessage)(error)}`);
        return {
            ok: false,
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_send_c2c_message(params) {
    try {
        const openid = (0, qqbot_common_1.asText)(params.openid).trim();
        if (!openid) {
            throw new Error("Missing param: openid");
        }
        const timeoutMs = (0, qqbot_openapi_1.resolveTimeoutMs)(params.timeout_ms);
        const snapshot = await (0, qqbot_state_1.requireConfiguredSnapshotAsync)();
        const body = (0, qqbot_openapi_1.buildSendMessageBody)(params);
        const response = await (0, qqbot_openapi_1.openApiRequest)(snapshot, `/v2/users/${encodeURIComponent(openid)}/messages`, "POST", body, timeoutMs);
        return {
            success: response.success,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            scene: "c2c",
            openid,
            requestBody: body,
            httpStatus: response.statusCode,
            response: response.json,
            error: response.success ? "" : (0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(response.json.message), `HTTP ${response.statusCode}`)
        };
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            scene: "c2c",
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}
async function qqbot_send_group_message(params) {
    try {
        const groupOpenid = (0, qqbot_common_1.asText)(params.group_openid).trim();
        if (!groupOpenid) {
            throw new Error("Missing param: group_openid");
        }
        const timeoutMs = (0, qqbot_openapi_1.resolveTimeoutMs)(params.timeout_ms);
        const snapshot = await (0, qqbot_state_1.requireConfiguredSnapshotAsync)();
        const body = (0, qqbot_openapi_1.buildSendMessageBody)(params);
        const response = await (0, qqbot_openapi_1.openApiRequest)(snapshot, `/v2/groups/${encodeURIComponent(groupOpenid)}/messages`, "POST", body, timeoutMs);
        return {
            success: response.success,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            scene: "group",
            groupOpenid,
            requestBody: body,
            httpStatus: response.statusCode,
            response: response.json,
            error: response.success ? "" : (0, qqbot_common_1.firstNonBlank)((0, qqbot_common_1.asText)(response.json.message), `HTTP ${response.statusCode}`)
        };
    }
    catch (error) {
        return {
            success: false,
            packageVersion: qqbot_common_1.PACKAGE_VERSION,
            scene: "group",
            error: (0, qqbot_common_1.safeErrorMessage)(error)
        };
    }
}

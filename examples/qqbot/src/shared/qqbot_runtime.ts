import {
    QQBotActionResult,
    QQBotClearEventsResult,
    DEFAULT_SERVICE_WAIT_MS,
    JsonObject,
    PACKAGE_VERSION,
    QQBotConfigureResult,
    QQBotConfigureParams,
    QQBotConnectionTestResult,
    QQBotDashboardStatusParams,
    QQBotDashboardStatusResult,
    QQBotLifecycleResult,
    QQBotReceiveEventsParams,
    QQBotReceiveEventsResult,
    QQBotSendC2CMessageParams,
    QQBotSendGroupMessageParams,
    QQBotSendMessageResult,
    QQBotServiceStartParams,
    QQBotServiceStopParams,
    QQBotStatusResult,
    QQBotTestConnectionParams,
    asText,
    firstNonBlank,
    hasOwn,
    parseOptionalBoolean,
    parsePositiveInt,
    safeErrorMessage
} from "./qqbot_common";
import {
    buildStatus,
    readConfigSnapshotAsync,
    requireConfiguredSnapshotAsync,
    updatePersistedConfigAsync,
    writeEnv,
} from "./qqbot_state";
import {
    buildSendMessageBody,
    fetchAccessToken,
    fetchGatewayInfo,
    openApiRequest,
    resolveTimeoutMs
} from "./qqbot_openapi";
import {
    buildServiceStatusAsync,
    clearQueuedEventsFromServiceAsync,
    ensureQQBotServiceStarted,
    queryQueuedEventsFromServiceAsync,
    stopQQBotServiceInternalAsync
} from "./qqbot_service";
import { qqbot_auto_reply_configure, qqbot_auto_reply_status } from "./qqbot_auto_reply";
import { ENV_KEYS, MAX_RECEIVE_LIMIT, DEFAULT_RECEIVE_LIMIT } from "./qqbot_common";

function logQQBotRuntime(message: string): void {
    console.log(`[qqbot_runtime] ${message}`);
}

function previewJson(value: unknown, maxLength = 1200): string {
    try {
        const text = JSON.stringify(value);
        if (typeof text !== "string") {
            return "";
        }
        return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
    } catch (_error) {
        return "[unserializable]";
    }
}

async function receiveQueuedEventsAsync(params: QQBotReceiveEventsParams = {}): Promise<JsonObject> {
    const limit = Math.min(
        MAX_RECEIVE_LIMIT,
        parsePositiveInt(params.limit, "limit", DEFAULT_RECEIVE_LIMIT)
    );
    const consume = parseOptionalBoolean(params.consume, "consume") !== false;
    const includeRaw = parseOptionalBoolean(params.include_raw, "include_raw") === true;
    const scene = asText(params.scene).trim().toLowerCase();
    const eventType = asText(params.event_type).trim();
    const result = await queryQueuedEventsFromServiceAsync({
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

async function clearQueuedEventsInternalAsync(): Promise<JsonObject> {
    return await clearQueuedEventsFromServiceAsync();
}

async function qqbot_configure(params: QQBotConfigureParams = {}): Promise<QQBotConfigureResult> {
    try {
        const before = await readConfigSnapshotAsync();
        const updatedEnvironmentKeys: string[] = [];
        const updatedConfigFields: string[] = [];

        if (hasOwn(params, "app_id")) {
            await writeEnv(ENV_KEYS.appId, asText(params.app_id).trim());
            updatedEnvironmentKeys.push(ENV_KEYS.appId);
        }
        if (hasOwn(params, "app_secret")) {
            await writeEnv(ENV_KEYS.appSecret, asText(params.app_secret).trim());
            updatedEnvironmentKeys.push(ENV_KEYS.appSecret);
        }
        if (hasOwn(params, "use_sandbox")) {
            await updatePersistedConfigAsync({
                useSandbox: parseOptionalBoolean(params.use_sandbox, "use_sandbox") === true
            });
            updatedConfigFields.push("useSandbox");
        }

        const after = await readConfigSnapshotAsync();
        const status = buildStatus(after);
        const shouldTest = parseOptionalBoolean(params.test_connection, "test_connection") === true;
        const shouldRestart = parseOptionalBoolean(params.restart_service, "restart_service") === true;
        const credentialsChanged = hasOwn(params, "app_id") || hasOwn(params, "app_secret");
        const useSandboxChanged = before.useSandbox !== after.useSandbox;
        const credentialsReady = !!after.appId && !!after.appSecret;

        let serviceResult: any = null;
        if (!credentialsReady || !after.listenerEnabled) {
            serviceResult = await stopQQBotServiceInternalAsync(DEFAULT_SERVICE_WAIT_MS);
        } else {
            serviceResult = await ensureQQBotServiceStarted({
                restart: shouldRestart || credentialsChanged || useSandboxChanged,
                timeout_ms: DEFAULT_SERVICE_WAIT_MS,
                source: "qqbot_configure"
            });
        }

        const result: JsonObject = {
            success: true,
            packageVersion: PACKAGE_VERSION,
            updatedEnvironmentKeys,
            updatedConfigFields,
            status,
            service: serviceResult
        };

        if (shouldTest) {
            result.connection = await qqbot_test_connection();
            result.success = !!(result.connection as any).success;
        }

        return result;
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_status(params: QQBotDashboardStatusParams = {}): Promise<QQBotStatusResult> {
    try {
        const snapshot = await readConfigSnapshotAsync();
        const summaryOnly = parseOptionalBoolean(params.summary_only, "summary_only") === true;
        const service = await buildServiceStatusAsync({
            snapshot,
            includeContacts: !summaryOnly
        });
        return {
            success: true,
            ...buildStatus(snapshot),
            service,
            queue: service.queue
        };
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_dashboard_status(
    params: QQBotDashboardStatusParams = {}
): Promise<QQBotDashboardStatusResult> {
    try {
        const snapshot = await readConfigSnapshotAsync();
        const summaryOnly = parseOptionalBoolean(params.summary_only, "summary_only") === true;
        logQQBotRuntime(`dashboard status start: summaryOnly=${summaryOnly}`);
        const [service, autoReply] = await Promise.all([
            buildServiceStatusAsync({
                snapshot,
                includeContacts: !summaryOnly
            }),
            qqbot_auto_reply_status({
                summary_only: summaryOnly
            })
        ]);
        if (autoReply.success === false) {
            console.error(`[qqbot_runtime] dashboard autoReply status failed: ${previewJson(autoReply)}`);
        }
        return {
            success: true,
            packageVersion: PACKAGE_VERSION,
            ...buildStatus(snapshot),
            service,
            queue: service.queue,
            autoReply
        };
    } catch (error: any) {
        console.error(`[qqbot_runtime] dashboard status failed: ${safeErrorMessage(error)}`);
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_service_start(params: QQBotServiceStartParams = {}): Promise<QQBotActionResult> {
    try {
        await updatePersistedConfigAsync({
            listenerEnabled: true
        });
        try {
            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                listenerEnabled: true,
                ...(await ensureQQBotServiceStarted({
                    restart: parseOptionalBoolean(params.restart, "restart") === true,
                    timeout_ms: parsePositiveInt(params.timeout_ms, "timeout_ms", DEFAULT_SERVICE_WAIT_MS),
                    source: "qqbot_service_start"
                }))
            };
        } catch (startError: any) {
            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                listenerEnabled: true,
                warning: safeErrorMessage(startError),
                service: await buildServiceStatusAsync()
            };
        }
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_service_stop(params: QQBotServiceStopParams = {}): Promise<QQBotActionResult> {
    try {
        const timeoutMs = parsePositiveInt(params.timeout_ms, "timeout_ms", DEFAULT_SERVICE_WAIT_MS);
        await updatePersistedConfigAsync({
            listenerEnabled: false
        });
        try {
            await qqbot_auto_reply_configure({
                enabled: false
            });
        } catch (autoReplyError: any) {
            console.error(`[qqbot_runtime] disable auto-reply after listener stop failed: ${safeErrorMessage(autoReplyError)}`);
        }
        let result: JsonObject;
        try {
            result = await stopQQBotServiceInternalAsync(timeoutMs);
        } catch (stopError: any) {
            console.error(`[qqbot_runtime] stop gateway after listener disable failed: ${safeErrorMessage(stopError)}`);
            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                listenerEnabled: false,
                warning: safeErrorMessage(stopError),
                service: await buildServiceStatusAsync()
            };
        }
        return {
            ...result,
            listenerEnabled: false,
            service: await buildServiceStatusAsync()
        };
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_receive_events(params: QQBotReceiveEventsParams = {}): Promise<QQBotReceiveEventsResult> {
    try {
        const autoStart = parseOptionalBoolean(params.auto_start, "auto_start") !== false;
        if (autoStart) {
            await ensureQQBotServiceStarted({
                allow_missing_config: false,
                timeout_ms: DEFAULT_SERVICE_WAIT_MS,
                source: "qqbot_receive_events"
            });
        }

        return {
            success: true,
            packageVersion: PACKAGE_VERSION,
            ...(await receiveQueuedEventsAsync(params)),
            service: await buildServiceStatusAsync()
        };
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_clear_events(): Promise<QQBotClearEventsResult> {
    try {
        const cleared = await clearQueuedEventsInternalAsync();
        const service = await buildServiceStatusAsync({
            includeContacts: false
        });
        return {
            success: true,
            packageVersion: PACKAGE_VERSION,
            ...cleared,
            queue: service.queue
        };
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_test_connection(
    params: QQBotTestConnectionParams = {}
): Promise<QQBotConnectionTestResult> {
    try {
        const timeoutMs = resolveTimeoutMs(params.timeout_ms);
        const snapshot = await requireConfiguredSnapshotAsync();
        const token = await fetchAccessToken(snapshot, timeoutMs);
        const me = await openApiRequest(snapshot, "/users/@me", "GET", null, timeoutMs);
        const gateway = await fetchGatewayInfo(snapshot, timeoutMs);

        return {
            success: me.success,
            packageVersion: PACKAGE_VERSION,
            accessTokenType: token.tokenType,
            accessTokenExpiresIn: token.expiresIn,
            httpStatus: me.statusCode,
            profile: me.json,
            gateway,
            status: buildStatus(snapshot),
            error: me.success ? "" : firstNonBlank(asText(me.json.message), `HTTP ${me.statusCode}`)
        };
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: safeErrorMessage(error)
        };
    }
}

export async function onQQBotListenerApplicationCreate(): Promise<QQBotLifecycleResult> {
    try {
        logQQBotRuntime("listener create hook start");
        const snapshot = await readConfigSnapshotAsync();
        if (!snapshot.listenerEnabled) {
            await stopQQBotServiceInternalAsync(DEFAULT_SERVICE_WAIT_MS);
            logQQBotRuntime("listener create hook skipped: listener disabled");
            return {
                ok: true,
                listenerEnabled: false,
                started: false
            };
        }

        const result = await ensureQQBotServiceStarted({
            allow_missing_config: false,
            timeout_ms: DEFAULT_SERVICE_WAIT_MS,
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
    } catch (error: any) {
        console.error(`[qqbot_runtime] listener create hook failed: ${safeErrorMessage(error)}`);
        return {
            ok: false,
            error: safeErrorMessage(error)
        };
    }
}

export async function onQQBotListenerApplicationForeground(): Promise<QQBotLifecycleResult> {
    try {
        logQQBotRuntime("listener foreground hook start");
        const snapshot = await readConfigSnapshotAsync();
        if (!snapshot.listenerEnabled) {
            logQQBotRuntime("listener foreground hook skipped: listener disabled");
            return {
                ok: true,
                listenerEnabled: false,
                started: false
            };
        }

        const result = await ensureQQBotServiceStarted({
            allow_missing_config: false,
            timeout_ms: DEFAULT_SERVICE_WAIT_MS,
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
    } catch (error: any) {
        console.error(`[qqbot_runtime] listener foreground hook failed: ${safeErrorMessage(error)}`);
        return {
            ok: false,
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_send_c2c_message(params: QQBotSendC2CMessageParams): Promise<QQBotSendMessageResult> {
    try {
        const openid = asText(params.openid).trim();
        if (!openid) {
            throw new Error("Missing param: openid");
        }
        const timeoutMs = resolveTimeoutMs(params.timeout_ms);
        const snapshot = await requireConfiguredSnapshotAsync();
        const body = buildSendMessageBody(params);
        const response = await openApiRequest(
            snapshot,
            `/v2/users/${encodeURIComponent(openid)}/messages`,
            "POST",
            body,
            timeoutMs
        );

        return {
            success: response.success,
            packageVersion: PACKAGE_VERSION,
            scene: "c2c",
            openid,
            requestBody: body,
            httpStatus: response.statusCode,
            response: response.json,
            error: response.success ? "" : firstNonBlank(asText(response.json.message), `HTTP ${response.statusCode}`)
        };
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            scene: "c2c",
            error: safeErrorMessage(error)
        };
    }
}

async function qqbot_send_group_message(
    params: QQBotSendGroupMessageParams
): Promise<QQBotSendMessageResult> {
    try {
        const groupOpenid = asText(params.group_openid).trim();
        if (!groupOpenid) {
            throw new Error("Missing param: group_openid");
        }
        const timeoutMs = resolveTimeoutMs(params.timeout_ms);
        const snapshot = await requireConfiguredSnapshotAsync();
        const body = buildSendMessageBody(params);
        const response = await openApiRequest(
            snapshot,
            `/v2/groups/${encodeURIComponent(groupOpenid)}/messages`,
            "POST",
            body,
            timeoutMs
        );

        return {
            success: response.success,
            packageVersion: PACKAGE_VERSION,
            scene: "group",
            groupOpenid,
            requestBody: body,
            httpStatus: response.statusCode,
            response: response.json,
            error: response.success ? "" : firstNonBlank(asText(response.json.message), `HTTP ${response.statusCode}`)
        };
    } catch (error: any) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            scene: "group",
            error: safeErrorMessage(error)
        };
    }
}

export {
    ensureQQBotServiceStarted,
    qqbot_configure,
    qqbot_status,
    qqbot_dashboard_status,
    qqbot_service_start,
    qqbot_service_stop,
    qqbot_receive_events,
    qqbot_clear_events,
    qqbot_test_connection,
    qqbot_send_c2c_message,
    qqbot_send_group_message
};

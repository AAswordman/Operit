"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const qqbot_common_js_1 = require("../../shared/qqbot_common.js");
const qqbot_ipc_js_1 = require("../../shared/qqbot_ipc.js");
function resolveText() {
    const locale = typeof getLang === "function" ? String(getLang() || "").trim().toLowerCase() : "";
    if (locale.startsWith("en")) {
        return {
            title: "QQ Bot Settings",
            subtitle: "Manage credentials, message listener, and automatic AI reply from one place.",
            statusTitle: "Current Status",
            statusConfigured: "Configured",
            statusNotConfigured: "Not configured",
            statusConfiguredMode: "Configured mode",
            statusRuntimeMode: "Running mode",
            statusServiceRunning: "Listener running",
            statusServiceStopped: "Listener stopped",
            statusServiceSandbox: "Sandbox",
            statusServiceProduction: "Production",
            statusServiceMismatch: "Current listener config does not match settings",
            statusLoopRunning: "Auto reply running",
            statusLoopStopped: "Auto reply stopped",
            statusQueue: "Queued messages",
            statusBot: "Bot account",
            statusError: "Last error",
            credentialsTitle: "Credentials",
            appIdLabel: "App ID",
            appSecretLabel: "App Secret",
            appSecretHint: "Leave App Secret blank to keep the current value.",
            sandboxTitle: "Use sandbox",
            sandboxDesc: "Enable Tencent QQ Bot sandbox OpenAPI and Gateway endpoints.",
            saveCredentials: "Save Credentials",
            saveAndTest: "Save and Test",
            automationTitle: "Automation",
            c2cTitle: "Reply to private chats",
            c2cDesc: "Handle inbound C2C messages.",
            groupTitle: "Reply to group chats",
            groupDesc: "Handle inbound group messages.",
            waifuTitle: "Waifu mode",
            waifuDesc: "Generate replies in waifu mode. Enabled by default.",
            pollLabel: "Poll interval (ms)",
            pollHint: "How often the auto reply loop checks the local QQ message queue.",
            aiTimeoutLabel: "AI timeout (ms)",
            aiTimeoutHint: "Maximum wait time for Operit AI to generate a reply.",
            chatGroupLabel: "Operit chat group",
            cardIdLabel: "Character Card (Optional)",
            cardDropdownNoCharacterCard: "No character card binding",
            cardDropdownBoundCard: "Character card bound",
            cardDropdownUnbound: "Current chat uses no character card",
            cardDropdownLoading: "Loading...",
            cardDropdownNoCards: "No character cards available",
            cardDropdownHint: "After selecting a card, QQ Bot auto reply will use that character card when creating chats and sending messages.",
            instructionLabel: "Bridge instruction",
            saveAutomation: "Save Automation",
            controlsTitle: "Controls",
            listenerSwitchTitle: "Listener",
            listenerSwitchDesc: "Keep the QQ Gateway listener running when entering Operit.",
            autoReplySwitchTitle: "Auto reply",
            autoReplySwitchDesc: "Requires listener to be enabled. Turning listener off also turns this off.",
            refreshStatus: "Refresh Status",
            runOnce: "Run Once",
            loading: "Working...",
            savingDone: "Settings saved.",
            testingDone: "Saved and connection tested.",
            actionDone: "Action completed.",
            saveErrorPrefix: "Failed: ",
            invalidNumber: "Please enter a valid positive number.",
            leaveBlankToKeep: "Leave blank to keep current value."
        };
    }
    return {
        title: "QQ Bot 设置",
        subtitle: "把凭证、收消息监听和自动 AI 回复放到一个页面里管理。",
        statusTitle: "当前状态",
        statusConfigured: "已配置",
        statusNotConfigured: "未配置",
        statusConfiguredMode: "当前设置模式",
        statusRuntimeMode: "当前运行模式",
        statusServiceRunning: "监听服务运行中",
        statusServiceStopped: "监听服务未运行",
        statusServiceSandbox: "沙箱",
        statusServiceProduction: "正式环境",
        statusServiceMismatch: "当前监听服务配置与设置不一致",
        statusLoopRunning: "自动回复运行中",
        statusLoopStopped: "自动回复未运行",
        statusQueue: "消息队列",
        statusBot: "机器人账号",
        statusError: "最近错误",
        credentialsTitle: "凭证配置",
        appIdLabel: "App ID",
        appSecretLabel: "App Secret",
        appSecretHint: "App Secret 留空就表示保持当前值不变。",
        sandboxTitle: "使用沙箱",
        sandboxDesc: "启用腾讯 QQ Bot 的沙箱 OpenAPI 和 Gateway 地址。",
        saveCredentials: "保存凭证",
        saveAndTest: "保存并测试",
        automationTitle: "自动化",
        c2cTitle: "处理私聊消息",
        c2cDesc: "自动回复收到的 C2C 私聊消息。",
        groupTitle: "处理群消息",
        groupDesc: "自动回复收到的群消息。",
        waifuTitle: "Waifu 模式",
        waifuDesc: "让自动回复按 waifu 模式生成内容，默认开启。",
        pollLabel: "轮询间隔（毫秒）",
        pollHint: "自动回复循环检查本地 QQ 消息队列的频率。",
        aiTimeoutLabel: "AI 超时（毫秒）",
        aiTimeoutHint: "等待 Operit AI 生成回复的最长时间。",
        chatGroupLabel: "Operit 会话分组",
        cardIdLabel: "绑定角色卡（可选）",
        cardDropdownNoCharacterCard: "不绑定角色卡",
        cardDropdownBoundCard: "已绑定角色卡",
        cardDropdownUnbound: "当前不使用角色卡",
        cardDropdownLoading: "加载中...",
        cardDropdownNoCards: "没有可用角色卡",
        cardDropdownHint: "选择角色卡后，QQ Bot 自动回复在创建会话和发送消息时会使用该角色卡。",
        instructionLabel: "桥接指令",
        saveAutomation: "保存自动化设置",
        controlsTitle: "运行控制",
        listenerSwitchTitle: "监听开关",
        listenerSwitchDesc: "进入 Operit 时按这个开关恢复 QQ Gateway 监听。",
        autoReplySwitchTitle: "自动回复开关",
        autoReplySwitchDesc: "依赖监听开启。关闭监听时，这个开关会自动关闭。",
        refreshStatus: "刷新状态",
        runOnce: "手动跑一次",
        loading: "处理中...",
        savingDone: "设置已保存。",
        testingDone: "设置已保存，并完成连接测试。",
        actionDone: "操作已完成。",
        saveErrorPrefix: "失败：",
        invalidNumber: "请输入有效的正整数。",
        leaveBlankToKeep: "留空表示保持当前值。"
    };
}
function useStateValue(ctx, key, initialValue) {
    const pair = ctx.useState(key, initialValue);
    return { value: pair[0], set: pair[1] };
}
function firstNonBlank(...values) {
    for (const value of values) {
        if (typeof value === "string" && value.trim()) {
            return value.trim();
        }
    }
    return "";
}
function asBoolean(value, fallback = false) {
    if (typeof value === "boolean") {
        return value;
    }
    if (typeof value === "string") {
        const normalized = value.trim().toLowerCase();
        if (normalized === "true") {
            return true;
        }
        if (normalized === "false") {
            return false;
        }
    }
    return fallback;
}
function readPendingCount(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
        return null;
    }
    const pendingCount = Reflect.get(value, "pendingCount");
    return typeof pendingCount === "number" && Number.isFinite(pendingCount) ? pendingCount : null;
}
function asPositiveNumber(raw) {
    const value = Number(raw.trim());
    if (!Number.isFinite(value) || value <= 0) {
        return null;
    }
    return Math.floor(value);
}
function toErrorText(error) {
    if (error instanceof Error) {
        return error.message || "unknown";
    }
    return String(error || "unknown");
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
function logSettingsError(message, details) {
    if (details === undefined) {
        console.error(`[qqbot_settings] ${message}`);
        return;
    }
    console.error(`[qqbot_settings] ${message}: ${previewJson(details)}`);
}
function readEnvValue(ctx, key) {
    return String(ctx.getEnv(key) || "").trim();
}
function createSectionTitle(ctx, icon, title) {
    return ctx.UI.Row({ verticalAlignment: "center" }, [
        ctx.UI.Icon({ name: icon, tint: "primary", size: 20 }),
        ctx.UI.Spacer({ width: 8 }),
        ctx.UI.Text({
            text: title,
            style: "titleMedium",
            fontWeight: "bold",
            color: "primary"
        })
    ]);
}
function createToggleRow(ctx, title, subtitle, checked, onCheckedChange, enabled = true) {
    return ctx.UI.Row({
        fillMaxWidth: true,
        verticalAlignment: "center",
        horizontalArrangement: "spaceBetween"
    }, [
        ctx.UI.Column({ weight: 1, spacing: 4 }, [
            ctx.UI.Text({
                text: title,
                style: "bodyMedium",
                fontWeight: "medium"
            }),
            ctx.UI.Text({
                text: subtitle,
                style: "bodySmall",
                color: "onSurfaceVariant"
            })
        ]),
        ctx.UI.Spacer({ width: 12 }),
        ctx.UI.Switch({
            checked,
            enabled,
            onCheckedChange
        })
    ]);
}
function buildBotLabel(runtimeStatus) {
    const username = firstNonBlank(String(runtimeStatus?.service?.runtime?.botUsername || ""));
    const userId = firstNonBlank(String(runtimeStatus?.service?.runtime?.botUserId || ""));
    if (username && userId) {
        return `${username} (${userId})`;
    }
    return firstNonBlank(username, userId);
}
function buildStatusModel(runtimeStatus, autoReplyStatus) {
    return {
        configured: asBoolean(runtimeStatus?.configured),
        appId: String(runtimeStatus?.appId || "").trim(),
        useSandbox: asBoolean(runtimeStatus?.useSandbox),
        listenerEnabled: asBoolean(runtimeStatus?.listenerEnabled),
        serviceRunning: asBoolean(runtimeStatus?.service?.running),
        serviceHealthy: asBoolean(runtimeStatus?.service?.healthy),
        serviceConfigMatchesCurrent: asBoolean(runtimeStatus?.service?.configMatchesCurrent, true),
        serviceConfiguredSandbox: asBoolean(runtimeStatus?.service?.configuredUseSandbox, asBoolean(runtimeStatus?.useSandbox)),
        serviceRuntimeSandbox: asBoolean(runtimeStatus?.service?.runtimeUseSandbox, asBoolean(runtimeStatus?.useSandbox)),
        queuePending: readPendingCount(runtimeStatus?.queue) ?? readPendingCount(runtimeStatus?.service?.queue) ?? 0,
        botLabel: buildBotLabel(runtimeStatus),
        serviceError: firstNonBlank(String(runtimeStatus?.service?.runtime?.lastError || ""), String(runtimeStatus?.error || "")),
        autoReplyEnabled: asBoolean(autoReplyStatus?.config?.enabled),
        autoReplyRunning: asBoolean(autoReplyStatus?.runtime?.running),
        autoReplyError: firstNonBlank(String(autoReplyStatus?.runtime?.lastError || ""), String(autoReplyStatus?.error || ""))
    };
}
function Screen(ctx) {
    const text = resolveText();
    const envAppId = readEnvValue(ctx, qqbot_common_js_1.ENV_KEYS.appId);
    const envAppSecret = readEnvValue(ctx, qqbot_common_js_1.ENV_KEYS.appSecret);
    const statusState = useStateValue(ctx, "status", {
        configured: false,
        appId: "",
        useSandbox: false,
        listenerEnabled: false,
        serviceRunning: false,
        serviceHealthy: false,
        serviceConfigMatchesCurrent: true,
        serviceConfiguredSandbox: false,
        serviceRuntimeSandbox: false,
        queuePending: 0,
        botLabel: "",
        serviceError: "",
        autoReplyEnabled: false,
        autoReplyRunning: false,
        autoReplyError: ""
    });
    const appIdState = useStateValue(ctx, "appId", envAppId);
    const appSecretState = useStateValue(ctx, "appSecret", envAppSecret);
    const useSandboxState = useStateValue(ctx, "useSandbox", false);
    const listenerEnabledState = useStateValue(ctx, "listenerEnabled", false);
    const autoReplyEnabledState = useStateValue(ctx, "autoReplyEnabled", false);
    const c2cEnabledState = useStateValue(ctx, "c2cEnabled", true);
    const groupEnabledState = useStateValue(ctx, "groupEnabled", true);
    const waifuState = useStateValue(ctx, "waifu", true);
    const pollIntervalInputState = useStateValue(ctx, "pollIntervalInput", "3000");
    const aiTimeoutInputState = useStateValue(ctx, "aiTimeoutInput", "180000");
    const chatGroupState = useStateValue(ctx, "chatGroup", "QQ Bot");
    const characterCardIdState = useStateValue(ctx, "characterCardId", "");
    const showCardPickerState = useStateValue(ctx, "showCardPicker", false);
    const loadingCardsState = useStateValue(ctx, "loadingCards", false);
    const availableCardsState = useStateValue(ctx, "availableCards", []);
    const hasLoadedCardsState = useStateValue(ctx, "hasLoadedCards", false);
    const instructionState = useStateValue(ctx, "instruction", "");
    const busyActionState = useStateValue(ctx, "busyAction", "");
    const successMessageState = useStateValue(ctx, "successMessage", "");
    const errorMessageState = useStateValue(ctx, "errorMessage", "");
    const hasInitializedState = useStateValue(ctx, "hasInitialized", false);
    const isBusy = (action) => busyActionState.value === action;
    const isAnyBusy = busyActionState.value !== "";
    const clearMessages = () => {
        successMessageState.set("");
        errorMessageState.set("");
    };
    const queryCharacterCards = async (showErrorToast) => {
        try {
            const result = await Tools.Chat.listCharacterCards();
            const cards = (Array.isArray(result?.cards) ? result.cards : [])
                .map((card) => ({
                id: String(card?.id || "").trim(),
                name: String(card?.name || "").trim(),
                description: String(card?.description || "").trim(),
                isDefault: card?.isDefault === true
            }))
                .filter((card) => !!card.id);
            availableCardsState.set(cards);
            hasLoadedCardsState.set(true);
            return { success: true, cards };
        }
        catch (error) {
            if (showErrorToast) {
                ctx.showToast(`${text.saveErrorPrefix}${toErrorText(error)}`);
            }
            return { success: false, cards: [] };
        }
    };
    const ensureCharacterCardsLoaded = async (showErrorToast) => {
        if (hasLoadedCardsState.value) {
            return availableCardsState.value;
        }
        const result = await queryCharacterCards(showErrorToast);
        return result.cards;
    };
    const findCharacterCard = (cardId) => {
        const targetId = String(cardId || "").trim();
        if (!targetId) {
            return null;
        }
        return availableCardsState.value.find((card) => card.id === targetId) || null;
    };
    const resolveCharacterCardName = (cardId) => {
        const matchedCard = findCharacterCard(cardId);
        return String(matchedCard?.name || "").trim();
    };
    const getSelectedCharacterCardLabel = () => {
        if (!characterCardIdState.value.trim()) {
            return text.cardDropdownNoCharacterCard;
        }
        return resolveCharacterCardName(characterCardIdState.value) || text.cardDropdownBoundCard;
    };
    const loadCardPicker = async () => {
        if (showCardPickerState.value) {
            showCardPickerState.set(false);
            loadingCardsState.set(false);
            return;
        }
        showCardPickerState.set(true);
        if (hasLoadedCardsState.value) {
            loadingCardsState.set(false);
            return;
        }
        loadingCardsState.set(true);
        const result = await queryCharacterCards(true);
        loadingCardsState.set(false);
        if (!result.success) {
            showCardPickerState.set(false);
        }
    };
    const pickCharacterCard = (cardId) => {
        characterCardIdState.set(String(cardId || "").trim());
        showCardPickerState.set(false);
        loadingCardsState.set(false);
    };
    const clearCharacterCardBinding = () => {
        characterCardIdState.set("");
        showCardPickerState.set(false);
        loadingCardsState.set(false);
    };
    const refreshAll = async (clearStateMessages = true, markBusy = true) => {
        if (markBusy) {
            busyActionState.set("refresh");
        }
        if (clearStateMessages) {
            clearMessages();
        }
        try {
            const params = { summary_only: true };
            const dashboardStatus = await (0, qqbot_ipc_js_1.withContext)("main", { params }, async () => await (0, qqbot_ipc_js_1.qqbot_dashboard_status)(params));
            if (!dashboardStatus?.success) {
                logSettingsError("dashboardStatus returned failure", dashboardStatus);
                throw new Error(String(dashboardStatus?.error || "qqbot_dashboard_status failed"));
            }
            const runtimeStatus = dashboardStatus;
            const autoReplyStatus = dashboardStatus?.autoReply;
            if (autoReplyStatus?.success === false) {
                logSettingsError("autoReply status returned failure", autoReplyStatus);
                throw new Error(String(autoReplyStatus?.error || "qqbot_auto_reply_status failed"));
            }
            const nextStatus = buildStatusModel(runtimeStatus, autoReplyStatus);
            statusState.set(nextStatus);
            appIdState.set(firstNonBlank(nextStatus.appId, appIdState.value, envAppId));
            appSecretState.set(firstNonBlank(appSecretState.value, envAppSecret));
            useSandboxState.set(nextStatus.useSandbox);
            listenerEnabledState.set(nextStatus.listenerEnabled);
            autoReplyEnabledState.set(nextStatus.listenerEnabled && asBoolean(autoReplyStatus?.config?.enabled, false));
            c2cEnabledState.set(asBoolean(autoReplyStatus?.config?.c2cEnabled, true));
            groupEnabledState.set(asBoolean(autoReplyStatus?.config?.groupEnabled, true));
            waifuState.set(asBoolean(autoReplyStatus?.config?.waifu, true));
            pollIntervalInputState.set(String(autoReplyStatus?.config?.pollIntervalMs || 3000));
            aiTimeoutInputState.set(String(autoReplyStatus?.config?.aiTimeoutMs || 180000));
            chatGroupState.set(String(autoReplyStatus?.config?.chatGroup || "QQ Bot"));
            const nextCharacterCardId = String(autoReplyStatus?.config?.characterCardId || "").trim();
            characterCardIdState.set(nextCharacterCardId);
            if (nextCharacterCardId && !findCharacterCard(nextCharacterCardId)) {
                await ensureCharacterCardsLoaded(false);
            }
            instructionState.set(String(autoReplyStatus?.config?.assistantInstruction || ""));
        }
        catch (error) {
            logSettingsError("refreshAll failed", {
                message: toErrorText(error),
                clearStateMessages,
                markBusy
            });
            errorMessageState.set(`${text.saveErrorPrefix}${toErrorText(error)}`);
        }
        finally {
            if (markBusy) {
                busyActionState.set("");
            }
        }
    };
    const runAction = async (action, runner, successMessage) => {
        busyActionState.set(action);
        clearMessages();
        try {
            const result = await runner();
            if (result && result.success === false) {
                throw new Error(String(result.error || "unknown"));
            }
            await refreshAll(false, false);
            successMessageState.set(successMessage);
        }
        catch (error) {
            errorMessageState.set(`${text.saveErrorPrefix}${toErrorText(error)}`);
        }
        finally {
            busyActionState.set("");
        }
    };
    const saveCredentials = async (testConnection) => {
        const params = {
            app_id: appIdState.value.trim(),
            use_sandbox: useSandboxState.value,
            test_connection: testConnection,
            restart_service: autoReplyEnabledState.value
        };
        if (appSecretState.value.trim()) {
            params.app_secret = appSecretState.value.trim();
        }
        await runAction(testConnection ? "save_and_test" : "save_credentials", async () => await (0, qqbot_ipc_js_1.withContext)("main", { params }, async () => await (0, qqbot_ipc_js_1.qqbot_configure)(params)), testConnection ? text.testingDone : text.savingDone);
    };
    const saveSandboxSetting = async (checked) => {
        useSandboxState.set(checked);
        const params = { use_sandbox: checked };
        await runAction("save_credentials", async () => await (0, qqbot_ipc_js_1.withContext)("main", { params }, async () => await (0, qqbot_ipc_js_1.qqbot_configure)(params)), text.savingDone);
    };
    const buildAutomationParams = () => {
        const pollIntervalMs = asPositiveNumber(pollIntervalInputState.value);
        const aiTimeoutMs = asPositiveNumber(aiTimeoutInputState.value);
        if (pollIntervalMs == null || aiTimeoutMs == null) {
            throw new Error(text.invalidNumber);
        }
        return {
            enabled: autoReplyEnabledState.value,
            c2c_enabled: c2cEnabledState.value,
            group_enabled: groupEnabledState.value,
            waifu: waifuState.value,
            poll_interval_ms: pollIntervalMs,
            ai_timeout_ms: aiTimeoutMs,
            chat_group: chatGroupState.value.trim(),
            character_card_id: characterCardIdState.value.trim(),
            assistant_instruction: instructionState.value.trim(),
            start_now: autoReplyEnabledState.value
        };
    };
    const saveAutomation = async () => {
        const params = buildAutomationParams();
        await runAction("save_automation", async () => await (0, qqbot_ipc_js_1.withContext)("main", { params }, async () => await (0, qqbot_ipc_js_1.qqbot_auto_reply_configure)(params)), text.savingDone);
    };
    const toggleAutoReplyEnabled = async (checked) => {
        if (!listenerEnabledState.value) {
            autoReplyEnabledState.set(false);
            return;
        }
        autoReplyEnabledState.set(checked);
        if (isAnyBusy) {
            return;
        }
        const params = {
            ...buildAutomationParams(),
            enabled: checked,
            start_now: checked
        };
        await runAction("save_automation", async () => await (0, qqbot_ipc_js_1.withContext)("main", { params }, async () => await (0, qqbot_ipc_js_1.qqbot_auto_reply_configure)(params)), text.savingDone);
    };
    const toggleListenerEnabled = async (checked) => {
        listenerEnabledState.set(checked);
        if (!checked) {
            autoReplyEnabledState.set(false);
        }
        if (isAnyBusy) {
            return;
        }
        await runAction(checked ? "start_service" : "stop_service", async () => {
            return await (0, qqbot_ipc_js_1.withContext)("main", { checked }, async () => checked ? await (0, qqbot_ipc_js_1.qqbot_service_start)({}) : await (0, qqbot_ipc_js_1.qqbot_service_stop)({}), qqbot_common_js_1.LISTENER_TOGGLE_IPC_TIMEOUT_MS);
        }, text.actionDone);
    };
    const statusLines = [
        `${statusState.value.configured ? text.statusConfigured : text.statusNotConfigured}`,
        `${statusState.value.serviceRunning ? text.statusServiceRunning : text.statusServiceStopped}${statusState.value.serviceHealthy ? " / healthy" : ""}`,
        `${text.statusConfiguredMode}: ${statusState.value.serviceConfiguredSandbox ? text.statusServiceSandbox : text.statusServiceProduction}`,
        `${text.statusRuntimeMode}: ${statusState.value.serviceRunning ? (statusState.value.serviceRuntimeSandbox ? text.statusServiceSandbox : text.statusServiceProduction) : text.statusServiceStopped}`,
        `${statusState.value.autoReplyRunning ? text.statusLoopRunning : text.statusLoopStopped}${statusState.value.autoReplyEnabled ? " / enabled" : ""}`,
        `${text.statusQueue}: ${statusState.value.queuePending}`
    ];
    if (statusState.value.serviceRunning && !statusState.value.serviceConfigMatchesCurrent) {
        statusLines.push(text.statusServiceMismatch);
    }
    if (statusState.value.botLabel) {
        statusLines.push(`${text.statusBot}: ${statusState.value.botLabel}`);
    }
    const mergedError = firstNonBlank(statusState.value.autoReplyError, statusState.value.serviceError);
    if (mergedError) {
        statusLines.push(`${text.statusError}: ${mergedError}`);
    }
    const rootChildren = [
        ctx.UI.Row({ verticalAlignment: "center" }, [
            ctx.UI.Icon({ name: "chat", tint: "primary", size: 24 }),
            ctx.UI.Spacer({ width: 8 }),
            ctx.UI.Text({
                text: text.title,
                style: "headlineSmall",
                fontWeight: "bold"
            })
        ]),
        ctx.UI.Text({
            text: text.subtitle,
            style: "bodyMedium",
            color: "onSurfaceVariant"
        }),
        createSectionTitle(ctx, "info", text.statusTitle),
        ctx.UI.Card({ fillMaxWidth: true }, [
            ctx.UI.Column({ padding: 16, spacing: 8 }, statusLines.map((line, index) => ctx.UI.Text({
                key: `status-${index}`,
                text: line,
                style: "bodyMedium",
                color: "onSurface"
            })))
        ]),
        createSectionTitle(ctx, "key", text.credentialsTitle),
        ctx.UI.Card({ fillMaxWidth: true }, [
            ctx.UI.Column({ padding: 16, spacing: 12 }, [
                ctx.UI.TextField({
                    label: text.appIdLabel,
                    value: appIdState.value,
                    onValueChange: appIdState.set,
                    singleLine: true
                }),
                ctx.UI.TextField({
                    label: text.appSecretLabel,
                    value: appSecretState.value,
                    onValueChange: appSecretState.set,
                    singleLine: true,
                    isPassword: true
                }),
                ctx.UI.Text({
                    text: `${text.appSecretHint} ${text.leaveBlankToKeep}`,
                    style: "bodySmall",
                    color: "onSurfaceVariant"
                }),
                createToggleRow(ctx, text.sandboxTitle, text.sandboxDesc, useSandboxState.value, async (checked) => {
                    if (isAnyBusy) {
                        return;
                    }
                    await saveSandboxSetting(checked);
                }, !isAnyBusy),
                ctx.UI.Button({
                    text: isBusy("save_credentials") ? text.loading : text.saveCredentials,
                    enabled: !isAnyBusy,
                    fillMaxWidth: true,
                    onClick: async () => await saveCredentials(false)
                }),
                ctx.UI.Button({
                    text: isBusy("save_and_test") ? text.loading : text.saveAndTest,
                    enabled: !isAnyBusy,
                    fillMaxWidth: true,
                    onClick: async () => await saveCredentials(true)
                })
            ])
        ]),
        createSectionTitle(ctx, "settings", text.automationTitle),
        ctx.UI.Card({ fillMaxWidth: true }, [
            ctx.UI.Column({ padding: 16, spacing: 12 }, [
                createToggleRow(ctx, text.c2cTitle, text.c2cDesc, c2cEnabledState.value, c2cEnabledState.set, !isAnyBusy),
                createToggleRow(ctx, text.groupTitle, text.groupDesc, groupEnabledState.value, groupEnabledState.set, !isAnyBusy),
                createToggleRow(ctx, text.waifuTitle, text.waifuDesc, waifuState.value, waifuState.set, !isAnyBusy),
                ctx.UI.TextField({
                    label: text.pollLabel,
                    value: pollIntervalInputState.value,
                    onValueChange: pollIntervalInputState.set,
                    singleLine: true
                }),
                ctx.UI.Text({
                    text: text.pollHint,
                    style: "bodySmall",
                    color: "onSurfaceVariant"
                }),
                ctx.UI.TextField({
                    label: text.aiTimeoutLabel,
                    value: aiTimeoutInputState.value,
                    onValueChange: aiTimeoutInputState.set,
                    singleLine: true
                }),
                ctx.UI.Text({
                    text: text.aiTimeoutHint,
                    style: "bodySmall",
                    color: "onSurfaceVariant"
                }),
                ctx.UI.TextField({
                    label: text.chatGroupLabel,
                    value: chatGroupState.value,
                    onValueChange: chatGroupState.set,
                    singleLine: true
                }),
                ctx.UI.Text({
                    text: text.cardIdLabel,
                    style: "labelMedium",
                    fontWeight: "bold",
                    color: "onSurface"
                }),
                ctx.UI.Box({
                    fillMaxWidth: true
                }, [
                    ctx.UI.OutlinedButton({
                        onClick: async () => await loadCardPicker(),
                        fillMaxWidth: true
                    }, [
                        ctx.UI.Row({
                            fillMaxWidth: true,
                            horizontalArrangement: "spaceBetween",
                            verticalAlignment: "center"
                        }, [
                            ctx.UI.Column({ weight: 1, spacing: 2 }, [
                                ctx.UI.Text({
                                    text: getSelectedCharacterCardLabel(),
                                    color: "onSurface",
                                    fontWeight: "medium",
                                    maxLines: 1,
                                    overflow: "ellipsis"
                                }),
                                ctx.UI.Text({
                                    text: characterCardIdState.value.trim()
                                        ? text.cardDropdownBoundCard
                                        : text.cardDropdownUnbound,
                                    style: "bodySmall",
                                    color: "onSurfaceVariant"
                                })
                            ]),
                            ctx.UI.Icon({
                                name: showCardPickerState.value ? "arrowDropUp" : "arrowDropDown",
                                tint: "onSurfaceVariant",
                                size: 20
                            })
                        ])
                    ]),
                    ctx.UI.DropdownMenu({
                        expanded: showCardPickerState.value,
                        properties: {
                            focusable: true
                        },
                        onDismissRequest: () => {
                            showCardPickerState.set(false);
                            loadingCardsState.set(false);
                        }
                    }, [
                        ctx.UI.Box({
                            modifier: ctx.Modifier
                                .fillMaxWidth()
                                .clickable(() => clearCharacterCardBinding())
                                .padding({ horizontal: 16, vertical: 12 })
                        }, [
                            ctx.UI.Text({
                                text: text.cardDropdownNoCharacterCard,
                                color: "onSurface",
                                fontWeight: !characterCardIdState.value.trim() ? "bold" : "normal"
                            })
                        ]),
                        ctx.UI.HorizontalDivider({
                            color: "outlineVariant",
                            thickness: 1
                        }),
                        ...(loadingCardsState.value
                            ? [
                                ctx.UI.Box({
                                    modifier: ctx.Modifier
                                        .fillMaxWidth()
                                        .padding({ horizontal: 16, vertical: 12 })
                                }, [
                                    ctx.UI.Text({
                                        text: text.cardDropdownLoading,
                                        color: "onSurfaceVariant"
                                    })
                                ])
                            ]
                            : availableCardsState.value.length === 0
                                ? [
                                    ctx.UI.Box({
                                        modifier: ctx.Modifier
                                            .fillMaxWidth()
                                            .padding({ horizontal: 16, vertical: 12 })
                                    }, [
                                        ctx.UI.Text({
                                            text: text.cardDropdownNoCards,
                                            color: "onSurfaceVariant"
                                        })
                                    ])
                                ]
                                : availableCardsState.value.map((card) => ctx.UI.Box({
                                    modifier: ctx.Modifier
                                        .fillMaxWidth()
                                        .clickable(() => pickCharacterCard(card.id))
                                        .padding({ horizontal: 16, vertical: 12 })
                                }, [
                                    ctx.UI.Row({
                                        fillMaxWidth: true,
                                        horizontalArrangement: "spaceBetween",
                                        verticalAlignment: "center"
                                    }, [
                                        ctx.UI.Column({ weight: 1, spacing: 2 }, [
                                            ctx.UI.Text({
                                                text: card.name,
                                                color: "onSurface",
                                                fontWeight: card.id === characterCardIdState.value.trim() ? "bold" : "normal",
                                                maxLines: 1,
                                                overflow: "ellipsis"
                                            }),
                                            ...(card.description
                                                ? [
                                                    ctx.UI.Text({
                                                        text: card.description,
                                                        style: "bodySmall",
                                                        color: "onSurfaceVariant",
                                                        maxLines: 1,
                                                        overflow: "ellipsis"
                                                    })
                                                ]
                                                : [])
                                        ]),
                                        card.id === characterCardIdState.value.trim()
                                            ? ctx.UI.Icon({
                                                name: "check",
                                                tint: "primary",
                                                size: 18
                                            })
                                            : ctx.UI.Spacer({ width: 18 })
                                    ])
                                ])))
                    ])
                ]),
                ctx.UI.Text({
                    text: text.cardDropdownHint,
                    style: "bodySmall",
                    color: "onSurfaceVariant"
                }),
                ctx.UI.TextField({
                    label: text.instructionLabel,
                    value: instructionState.value,
                    onValueChange: instructionState.set,
                    minLines: 5
                }),
                ctx.UI.Button({
                    text: isBusy("save_automation") ? text.loading : text.saveAutomation,
                    enabled: !isAnyBusy,
                    fillMaxWidth: true,
                    onClick: async () => await saveAutomation()
                })
            ])
        ]),
        createSectionTitle(ctx, "bolt", text.controlsTitle),
        ctx.UI.Card({ fillMaxWidth: true }, [
            ctx.UI.Column({ padding: 16, spacing: 10 }, [
                ctx.UI.Button({
                    text: isBusy("refresh") ? text.loading : text.refreshStatus,
                    enabled: !isAnyBusy,
                    fillMaxWidth: true,
                    onClick: refreshAll
                }),
                createToggleRow(ctx, text.listenerSwitchTitle, text.listenerSwitchDesc, listenerEnabledState.value, toggleListenerEnabled, !isAnyBusy),
                createToggleRow(ctx, text.autoReplySwitchTitle, text.autoReplySwitchDesc, autoReplyEnabledState.value, toggleAutoReplyEnabled, !isAnyBusy && listenerEnabledState.value),
                ctx.UI.Button({
                    text: isBusy("run_once") ? text.loading : text.runOnce,
                    enabled: !isAnyBusy,
                    fillMaxWidth: true,
                    onClick: async () => await runAction("run_once", async () => await (0, qqbot_ipc_js_1.withContext)("main", {}, async () => await (0, qqbot_ipc_js_1.qqbot_auto_reply_run_once)()), text.actionDone)
                })
            ])
        ])
    ];
    if (successMessageState.value.trim()) {
        rootChildren.push(ctx.UI.Card({ containerColor: "primaryContainer", fillMaxWidth: true }, [
            ctx.UI.Row({ padding: 14, verticalAlignment: "center" }, [
                ctx.UI.Icon({ name: "checkCircle", tint: "onPrimaryContainer" }),
                ctx.UI.Spacer({ width: 8 }),
                ctx.UI.Text({
                    text: successMessageState.value,
                    style: "bodyMedium",
                    color: "onPrimaryContainer"
                })
            ])
        ]));
    }
    if (errorMessageState.value.trim()) {
        rootChildren.push(ctx.UI.Card({ containerColor: "errorContainer", fillMaxWidth: true }, [
            ctx.UI.Row({ padding: 14, verticalAlignment: "center" }, [
                ctx.UI.Icon({ name: "error", tint: "onErrorContainer" }),
                ctx.UI.Spacer({ width: 8 }),
                ctx.UI.Text({
                    text: errorMessageState.value,
                    style: "bodyMedium",
                    color: "onErrorContainer"
                })
            ])
        ]));
    }
    return ctx.UI.LazyColumn({
        fillMaxSize: true,
        padding: 16,
        spacing: 16,
        onLoad: async () => {
            if (!hasInitializedState.value) {
                hasInitializedState.set(true);
                appIdState.set(firstNonBlank(appIdState.value, envAppId));
                appSecretState.set(firstNonBlank(appSecretState.value, envAppSecret));
                await refreshAll();
            }
        }
    }, rootChildren);
}

"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ENV_KEYS = exports.DEFAULT_GATEWAY_INTENTS = exports.SANDBOX_API_BASE_URL = exports.API_BASE_URL = exports.TOKEN_URL = exports.LOCAL_SERVICE_PORT = exports.TERMINAL_SESSION_NAME = exports.TERMINAL_SERVICE_OUTPUT_FILE_NAME = exports.TERMINAL_SERVICE_RESOURCE_KEY = exports.LOG_FILE_NAME = exports.CONFIG_FILE_NAME = exports.QQBOT_TOOLPKG_ID = exports.SERVICE_POLL_INTERVAL_MS = exports.MAX_RECEIVE_LIMIT = exports.DEFAULT_RECEIVE_LIMIT = exports.LISTENER_TOGGLE_IPC_TIMEOUT_MS = exports.DEFAULT_SERVICE_WAIT_MS = exports.DEFAULT_TIMEOUT_MS = exports.PACKAGE_VERSION = void 0;
exports.asText = asText;
exports.hasOwn = hasOwn;
exports.isObject = isObject;
exports.firstNonBlank = firstNonBlank;
exports.safeErrorMessage = safeErrorMessage;
exports.parsePositiveInt = parsePositiveInt;
exports.parseOptionalBoolean = parseOptionalBoolean;
exports.toBoolean = toBoolean;
exports.parseMessageType = parseMessageType;
exports.parseMsgSeq = parseMsgSeq;
exports.parseJsonObject = parseJsonObject;
exports.parseJsonArray = parseJsonArray;
exports.toHttpTimeoutSeconds = toHttpTimeoutSeconds;
exports.maskSecret = maskSecret;
exports.shellQuote = shellQuote;
exports.createControlToken = createControlToken;
exports.PACKAGE_VERSION = "0.3.1";
exports.DEFAULT_TIMEOUT_MS = 20000;
exports.DEFAULT_SERVICE_WAIT_MS = 8000;
exports.LISTENER_TOGGLE_IPC_TIMEOUT_MS = 45000;
exports.DEFAULT_RECEIVE_LIMIT = 20;
exports.MAX_RECEIVE_LIMIT = 100;
exports.SERVICE_POLL_INTERVAL_MS = 200;
exports.QQBOT_TOOLPKG_ID = "com.operit.qqbot_bundle";
exports.CONFIG_FILE_NAME = "config.json";
exports.LOG_FILE_NAME = "gateway_service.log";
exports.TERMINAL_SERVICE_RESOURCE_KEY = "qqbot_gateway_service_py";
exports.TERMINAL_SERVICE_OUTPUT_FILE_NAME = "qqbot_gateway_service.py";
exports.TERMINAL_SESSION_NAME = "qqbot_gateway_service";
exports.LOCAL_SERVICE_PORT = 32145;
exports.TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
exports.API_BASE_URL = "https://api.sgroup.qq.com";
exports.SANDBOX_API_BASE_URL = "https://sandbox.api.sgroup.qq.com";
exports.DEFAULT_GATEWAY_INTENTS = (1 << 30) | (1 << 12) | (1 << 25) | (1 << 26);
exports.ENV_KEYS = {
    appId: "QQBOT_APP_ID",
    appSecret: "QQBOT_APP_SECRET"
};
function asText(value) {
    return String(value == null ? "" : value);
}
function hasOwn(value, key) {
    return !!value && typeof value === "object" && Object.prototype.hasOwnProperty.call(value, key);
}
function isObject(value) {
    return !!value && typeof value === "object" && !Array.isArray(value);
}
function firstNonBlank(...values) {
    for (let index = 0; index < values.length; index += 1) {
        const candidate = values[index];
        if (typeof candidate === "string" && candidate.trim()) {
            return candidate.trim();
        }
    }
    return "";
}
function safeErrorMessage(error) {
    try {
        if (typeof error === "string") {
            return error;
        }
        if (error && typeof error.message === "string" && error.message.trim()) {
            return error.message.trim();
        }
        return String(error == null ? "" : error);
    }
    catch (_innerError) {
        return "Unknown error";
    }
}
function parsePositiveInt(value, fieldName, fallbackValue) {
    const raw = asText(value).trim();
    if (!raw) {
        return fallbackValue;
    }
    const parsed = Number(raw);
    if (!Number.isInteger(parsed) || parsed <= 0) {
        throw new Error(`Invalid ${fieldName}: expected positive integer`);
    }
    return parsed;
}
function parseOptionalBoolean(value, fieldName) {
    if (value === undefined) {
        return undefined;
    }
    if (typeof value === "boolean") {
        return value;
    }
    const raw = asText(value).trim().toLowerCase();
    if (!raw) {
        return undefined;
    }
    if (raw === "true" || raw === "1" || raw === "yes") {
        return true;
    }
    if (raw === "false" || raw === "0" || raw === "no") {
        return false;
    }
    throw new Error(`Invalid ${fieldName}: expected boolean`);
}
function toBoolean(value, fallbackValue = false) {
    try {
        const parsed = parseOptionalBoolean(value, "boolean");
        return parsed === undefined ? fallbackValue : parsed;
    }
    catch (_error) {
        return fallbackValue;
    }
}
function parseMessageType(value) {
    const raw = asText(value).trim();
    if (!raw) {
        return 0;
    }
    const parsed = Number(raw);
    if (!Number.isInteger(parsed) || parsed < 0) {
        throw new Error("Invalid msg_type: expected non-negative integer");
    }
    return parsed;
}
function parseMsgSeq(value) {
    const raw = asText(value).trim();
    if (!raw) {
        return 1;
    }
    const parsed = Number(raw);
    if (!Number.isInteger(parsed) || parsed <= 0) {
        throw new Error("Invalid msg_seq: expected positive integer");
    }
    return parsed;
}
function parseJsonObject(content) {
    const trimmed = content.trim();
    if (!trimmed) {
        return {};
    }
    const parsed = JSON.parse(trimmed);
    if (!isObject(parsed)) {
        throw new Error("Expected JSON object");
    }
    return parsed;
}
function parseJsonArray(content) {
    const trimmed = content.trim();
    if (!trimmed) {
        return [];
    }
    const parsed = JSON.parse(trimmed);
    if (!Array.isArray(parsed)) {
        throw new Error("Expected JSON array");
    }
    return parsed.filter(isObject);
}
function toHttpTimeoutSeconds(timeoutMs) {
    return Math.max(1, Math.ceil(timeoutMs / 1000));
}
function maskSecret(secret) {
    const value = secret.trim();
    if (!value) {
        return "";
    }
    if (value.length <= 6) {
        return `${value.slice(0, 1)}***${value.slice(-1)}`;
    }
    return `${value.slice(0, 3)}***${value.slice(-3)}`;
}
function shellQuote(value) {
    return `'${String(value).replace(/'/g, `'\"'\"'`)}'`;
}
function createControlToken() {
    const random = Math.random().toString(36).slice(2);
    return `qqbot_${Date.now().toString(36)}_${random}`;
}

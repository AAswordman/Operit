"use strict";

// METADATA
// {
//     "name": "chat_runtime_state_monitor",
//     "display_name": { "zh": "聊天运行状态监视器", "en": "Chat Runtime State Monitor" },
//     "description": { "zh": "被动监听聊天运行状态变化，自动写入 JSONL 文件。", "en": "Passive monitor for chat runtime state changes, auto-writes JSONL." },
//     "category": "Chat"
// }

var ChatRuntimeStateMonitor = (function () {
    var SESSION_ID = "chat_state_monitor";
    var DEFAULT_OUTPUT_PATH = "/sdcard/Download/chat_runtime_state.jsonl";
    var _outputPath = DEFAULT_OUTPUT_PATH;
    var _enabled = false;
    var _sampleCount = 0;
    var _startedAt = null;

    // ---- 文件写入（无需 Shizuku） ----
    function shellQuote(v) {
        return "'" + String(v).replace(/'/g, "'\\''") + "'";
    }

    function utf8ByteLen(s) {
        var n = 0;
        for (var i = 0; i < s.length; i++) {
            var c = s.charCodeAt(i);
            if (c < 0x80) n += 1;
            else if (c < 0x800) n += 2;
            else if (c >= 0xD800 && c <= 0xDBFF) { n += 4; i++; }
            else n += 3;
        }
        return n;
    }

    function errorText(e) {
        if (e && typeof e.message === "string") return e.message;
        return String(e);
    }

    async function writeJsonl(record) {
        var line = JSON.stringify(record);
        var filePath = _outputPath;
        try {
            var session = await Tools.System.terminal.create(SESSION_ID);
            var sid = session.sessionId;
            var checkCmd = "python3 -c \"import os,sys;p=sys.argv[1];d=os.path.dirname(p) or '.';print('PARENT_MISSING' if not os.path.exists(d) else ('NO_WRITE' if not os.access(d, os.W_OK) else 'OK'))\" " + shellQuote(filePath);
            var cr = await Tools.System.terminal.exec(sid, checkCmd, 30000);
            var status = ((cr && cr.output) ? String(cr.output) : "").trim().split("\n").pop().trim();
            if (status === "PARENT_MISSING") return "[write skip] output dir not found: " + filePath;
            if (status === "NO_WRITE") return "[write skip] no write permission: " + filePath;

            // 用 printf 追加写入一行
            var writeCmd = "printf '%s\\n' " + shellQuote(line) + " >> " + shellQuote(filePath);
            var wr = await Tools.System.terminal.exec(sid, writeCmd, 30000);
            var wout = ((wr && wr.output) ? String(wr.output) : "").trim();
            if (wout.indexOf("Error") >= 0 || wout.indexOf("Traceback") >= 0) {
                return "[write fail] " + wout.slice(-200);
            }
            return null;
        } catch (e) {
            return "[write error] " + errorText(e);
        }
    }

    function makeRecord(chatId, action, userState, appState, toolName, errorInfo) {
        return {
            ts: Date.now(),
            ts_iso: new Date().toISOString(),
            chat_id: chatId || null,
            action: action || null,
            user_state: userState || null,
            app_state: appState || null,
            tool_name: toolName || null,
            error: errorInfo || null,
            global: "global"
        };
    }

    function makeRecordSession(chatId, action, userState, appState, toolName, errorInfo) {
        return {
            ts: Date.now(),
            ts_iso: new Date().toISOString(),
            chat_id: chatId || null,
            action: action || null,
            user_state: userState || null,
            app_state: appState || null,
            tool_name: toolName || null,
            error: errorInfo || null,
            global: "session"
        };
    }

    function makeErrorRecord(errorMessage) {
        return {
            ts: Date.now(),
            ts_iso: new Date().toISOString(),
            error: String(errorMessage || "unknown")
        };
    }

    // ---- hook handler ----
    function onActionStateChange(event) {
        if (!_enabled) return;
        var scope = event.scope;
        var chatId = event.chatId;
        var action = event.action;
        var userState = event.userState;
        var appState = event.applicationState;
        var toolName = event.toolName;
        var errorInfo = null;
        if (event.errorMessage) {
            errorInfo = {
                source: event.errorSource || null,
                code: event.errorCode || null,
                message: event.errorMessage,
                recoverable: !!event.errorRecoverable,
                retry_attempt: event.retryAttempt || null
            };
        }

        var record;
        if (scope === "global") {
            record = makeRecord(chatId, action, userState, appState, toolName, errorInfo);
        } else {
            record = makeRecordSession(chatId, action, userState, appState, toolName, errorInfo);
        }

        writeJsonl(record).then(function (err) {
            if (err) console.error("[chat_state_monitor write]", err);
        }).catch(function () {});
    }

    // ---- 导出函数 ----
    function monitor_chat_runtime_state(params) {
        // 工具入口（保留，兼容旧调用）
        // 但主要通过 hook 自动运行
        if (params && params.output_path) {
            _outputPath = String(params.output_path).trim() || DEFAULT_OUTPUT_PATH;
        }
        _enabled = true;
        if (!_startedAt) _startedAt = new Date().toISOString();
        console.log("[chat_runtime_state_monitor] started, output=" + _outputPath);
    }

    function stop_monitor() {
        _enabled = false;
        console.log("[chat_runtime_state_monitor] stopped");
    }

    function get_status() {
        return {
            enabled: _enabled,
            output_path: _outputPath,
            started_at: _startedAt
        };
    }

    // ---- 插件主入口 ----
    function main() {
        try {
            // 注册状态变化 hook
            ToolPkg.registerChatActionStateHook({
                id: "chat_state_monitor",
                function: onActionStateChange
            });
            _enabled = true;
            _startedAt = new Date().toISOString();
            console.log("[chat_runtime_state_monitor] registered, output=" + _outputPath);
            if (typeof complete === "function") {
                complete({
                    success: true,
                    message: "聊天运行状态监视器已启动，自动记录 state_snapshot 和 state_changed 事件",
                    data: { output_path: _outputPath }
                });
            }
        } catch (e) {
            console.error("[chat_runtime_state_monitor init error]", e);
            if (typeof complete === "function") {
                complete({
                    success: false,
                    message: "启动失败: " + errorText(e)
                });
            }
        }
    }

    return {
        main: main,
        monitor_chat_runtime_state: monitor_chat_runtime_state,
        stop_monitor: stop_monitor,
        get_status: get_status,
        onActionStateChange: onActionStateChange
    };
})();

exports.main = ChatRuntimeStateMonitor.main;
exports.monitor_chat_runtime_state = ChatRuntimeStateMonitor.monitor_chat_runtime_state;
exports.stop_monitor = ChatRuntimeStateMonitor.stop_monitor;
exports.get_status = ChatRuntimeStateMonitor.get_status;

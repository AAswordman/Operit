"use strict";

/* METADATA
{
    "name": "chat_runtime_state_monitor",
    "display_name": {
        "zh": "聊天运行状态监视器",
        "en": "Chat Runtime State Monitor"
    },
    "description": {
        "zh": "被动监听聊天运行状态变化（state_snapshot / state_changed），自动写入 JSONL 文件，无需 Shizuku 授权。",
        "en": "Passive monitor for chat runtime state changes (state_snapshot / state_changed), auto-writes JSONL without Shizuku."
    },
    "enabledByDefault": false,
    "category": "Chat",
    "tools": [
        {
            "name": "monitor_chat_runtime_state",
            "description": {
                "zh": "配置并启动聊天运行状态监听（插件安装后自动启动，无需手动调用）",
                "en": "Configure and start chat runtime state monitoring (auto-starts on plugin install, no manual call needed)"
            },
            "parameters": [
                { "name": "output_path", "description": { "zh": "可选：JSONL 输出路径（默认 /sdcard/Download/chat_runtime_state.jsonl）", "en": "Optional: JSONL output path (default /sdcard/Download/chat_runtime_state.jsonl)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "get_monitor_status",
            "description": { "zh": "查看当前监听状态和输出路径", "en": "Check current monitoring status and output path" },
            "parameters": []
        },
        {
            "name": "stop_monitor",
            "description": { "zh": "停止监听", "en": "Stop monitoring" },
            "parameters": []
        }
    ]
}
*/

var ChatRuntimeStateMonitor = (function () {
    var SESSION_ID = "chat_state_monitor";
    var DEFAULT_OUTPUT_PATH = "/sdcard/Download/chat_runtime_state.jsonl";
    var _outputPath = DEFAULT_OUTPUT_PATH;
    var _enabled = false;
    var _startedAt = null;

    function shellQuote(v) {
        return "'" + String(v).replace(/'/g, "'\\''") + "'";
    }

    function errorText(e) {
        if (e && typeof e.message === "string") return e.message;
        return String(e);
    }

    function makeRecord(event) {
        var r = {
            ts: Date.now(),
            ts_iso: new Date().toISOString(),
            scope: event.scope,
            chat_id: event.chatId || null,
            action: event.action || null,
            user_state: event.userState || null,
            app_state: event.applicationState || null,
            tool_name: event.toolName || null
        };
        if (event.errorMessage) {
            r.error = {
                source: event.errorSource || null,
                code: event.errorCode || null,
                message: event.errorMessage,
                recoverable: !!event.errorRecoverable,
                retry_attempt: event.retryAttempt || null
            };
        }
        return r;
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
            if (status === "PARENT_MISSING") return "[skip] dir not found: " + filePath;
            if (status === "NO_WRITE") return "[skip] no write: " + filePath;
            var writeCmd = "printf '%s\\n' " + shellQuote(line) + " >> " + shellQuote(filePath);
            var wr = await Tools.System.terminal.exec(sid, writeCmd, 30000);
            var wout = ((wr && wr.output) ? String(wr.output) : "").trim();
            if (wout.indexOf("Error") >= 0 || wout.indexOf("Traceback") >= 0) {
                return "[fail] " + wout.slice(-200);
            }
            return null;
        } catch (e) {
            return "[error] " + errorText(e);
        }
    }

    function onActionStateChange(event) {
        if (!_enabled) return;
        var record = makeRecord(event);
        writeJsonl(record).then(function (err) {
            if (err) console.error("[chat_state_monitor]", err);
        }).catch(function () {});
    }

    function monitor_chat_runtime_state(params) {
        if (params && params.output_path) {
            _outputPath = String(params.output_path).trim() || DEFAULT_OUTPUT_PATH;
        }
        _enabled = true;
        if (!_startedAt) _startedAt = new Date().toISOString();
        console.log("[chat_state_monitor] started, output=" + _outputPath);
    }

    function get_monitor_status() {
        return {
            enabled: _enabled,
            output_path: _outputPath,
            started_at: _startedAt
        };
    }

    function stop_monitor() {
        _enabled = false;
        console.log("[chat_state_monitor] stopped");
    }

    function main() {
        try {
            ToolPkg.registerChatActionStateHook({
                id: "chat_runtime_state_monitor",
                function: onActionStateChange
            });
            _enabled = true;
            _startedAt = new Date().toISOString();
            console.log("[chat_state_monitor] hook registered, output=" + _outputPath);
        } catch (e) {
            console.error("[chat_state_monitor] init error", e);
        }
    }

    return {
        main: main,
        monitor_chat_runtime_state: monitor_chat_runtime_state,
        get_monitor_status: get_monitor_status,
        stop_monitor: stop_monitor
    };
})();

exports.main = ChatRuntimeStateMonitor.main;
exports.monitor_chat_runtime_state = ChatRuntimeStateMonitor.monitor_chat_runtime_state;
exports.get_monitor_status = ChatRuntimeStateMonitor.get_monitor_status;
exports.stop_monitor = ChatRuntimeStateMonitor.stop_monitor;

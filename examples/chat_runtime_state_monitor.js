"use strict";

/* METADATA
{
    "name": "chat_runtime_state_monitor",
    "display_name": {
        "zh": "聊天运行状态监视器",
        "en": "Chat Runtime State Monitor"
    },
    "description": {
        "zh": "查询当前对话运行状态并追加写入 JSONL 文件（无需 Shizuku）。",
        "en": "Query current chat runtime state and append to JSONL (no Shizuku)."
    },
    "enabledByDefault": false,
    "category": "Chat",
    "tools": [
        {
            "name": "monitor_chat_runtime_state",
            "description": {
                "zh": "立即查询一次聊天运行状态（aiBehavior / userState / applicationState 等）并追加写入 JSONL 文件。",
                "en": "Query chat runtime state (aiBehavior/userState/applicationState) once and append to JSONL file."
            },
            "parameters": [
                { "name": "chat_id", "description": { "zh": "可选：目标对话 ID；为空时读取当前对话", "en": "Optional: target chat ID; omit for current chat" }, "type": "string", "required": false },
                { "name": "output_path", "description": { "zh": "可选：JSONL 输出路径（默认 /sdcard/Download/chat_runtime_state.jsonl）", "en": "Optional: JSONL output path (default /sdcard/Download/chat_runtime_state.jsonl)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "read_monitor_log",
            "description": { "zh": "读取已写入的 JSONL 记录文件。", "en": "Read the written JSONL log file." },
            "parameters": [
                { "name": "output_path", "description": { "zh": "可选：文件路径（默认 /sdcard/Download/chat_runtime_state.jsonl）", "en": "Optional: file path (default /sdcard/Download/chat_runtime_state.jsonl)" }, "type": "string", "required": false },
                { "name": "last_n", "description": { "zh": "可选：只读最后 N 行（默认全部）", "en": "Optional: read only last N lines (default all)" }, "type": "number", "required": false }
            ]
        }
    ]
}
*/

var ChatRuntimeStateMonitor = (function () {
    var SESSION_ID = "chat_state_monitor";
    var DEFAULT_OUTPUT_PATH = "/sdcard/Download/chat_runtime_state.jsonl";
    var ALLOWED_OUTPUT_PREFIXES = ["/sdcard/Download/", "/data/user/0/com.ai.assistance.operit/files/"];

    function validateOutputPath(filePath) {
        var normalized = String(filePath || "").trim();
        if (!normalized || normalized.indexOf("\\0") >= 0 || normalized.indexOf("..") >= 0 ||
            !ALLOWED_OUTPUT_PREFIXES.some(function (prefix) { return normalized.indexOf(prefix) === 0; })) {
            throw new Error("输出路径必须位于 Download 或应用私有目录: " + normalized);
        }
        return normalized;
    }
    function shellQuote(v) {
        return "'" + String(v).replace(/'/g, "'\\''") + "'";
    }

    function errorText(e) {
        if (e && typeof e.message === "string") return e.message;
        return String(e);
    }

    async function writeJsonl(filePath, line) {
        try {
            filePath = validateOutputPath(filePath);
            var session = await Tools.System.terminal.create(SESSION_ID);
            var sid = session.sessionId;
            var checkCmd = "python3 -c \"import os,sys;p=sys.argv[1];d=os.path.dirname(p) or '.';print('PARENT_MISSING' if not os.path.exists(d) else ('NO_WRITE' if not os.access(d, os.W_OK) else 'OK'))\" " + shellQuote(filePath);
            var cr = await Tools.System.terminal.exec(sid, checkCmd, 30000);
            var status = ((cr && cr.output) ? String(cr.output) : "").trim().split("\n").pop().trim();
            if (status === "PARENT_MISSING") throw new Error("输出目录不存在: " + filePath);
            if (status === "NO_WRITE") throw new Error("无写入权限: " + filePath);
            var writeCmd = "printf '%s\\n' " + shellQuote(line) + " >> " + shellQuote(filePath);
            var wr = await Tools.System.terminal.exec(sid, writeCmd, 30000);
            var wout = ((wr && wr.output) ? String(wr.output) : "").trim();
            if (wout.indexOf("Error") >= 0 || wout.indexOf("Traceback") >= 0) throw new Error("写入失败: " + wout.slice(-200));
            return null;
        } catch (e) {
            return errorText(e);
        }
    }

    async function readJsonl(filePath, lastN) {
        try {
            filePath = validateOutputPath(filePath);
            var session = await Tools.System.terminal.create(SESSION_ID);
            var sid = session.sessionId;
            var readCmd;
            if (lastN && lastN > 0) {
                readCmd = "tail -n " + parseInt(lastN, 10) + " " + shellQuote(filePath);
            } else {
                readCmd = "cat " + shellQuote(filePath);
            }
            var rr = await Tools.System.terminal.exec(sid, readCmd, 30000);
            var rout = ((rr && rr.output) ? String(rr.output) : "");
            if (rout.indexOf("No such file") >= 0 || rout.indexOf("not found") >= 0) throw new Error("文件不存在: " + filePath);
            var lines = rout.trim().split("\n").filter(function (l) { return l.length > 0; });
            var records = [];
            for (var i = 0; i < lines.length; i++) {
                try { records.push(JSON.parse(lines[i])); } catch (_) {}
            }
            return { success: true, data: records, total: records.length };
        } catch (e) {
            return { success: false, error: errorText(e) };
        }
    }

    async function monitor_chat_runtime_state(params) {
        var chatId = (params && params.chat_id) ? String(params.chat_id).trim() : "";
        var filePath = (params && params.output_path) ? String(params.output_path).trim() : DEFAULT_OUTPUT_PATH;

        // 查询状态
        var stateResult;
        try {
            stateResult = await Tools.Chat.getCurrentChatRuntimeState(chatId || undefined);
        } catch (e) {
            return { success: false, message: "查询聊天状态失败: " + errorText(e) };
        }

        if (!stateResult || !stateResult.success) {
            return { success: false, message: "获取状态失败", data: stateResult };
        }

        var d = stateResult.data || {};
        var record = {
            ts: Date.now(),
            ts_iso: new Date().toISOString(),
            chat_id: d.chatId || null,
            aiBehavior: d.aiBehavior || null,
            userState: d.userState || null,
            applicationState: d.applicationState || null,
            toolName: d.toolName || null,
            isIdle: d.isIdle || false,
            isActive: d.isActive || false
        };
        if (d.error) {
            record.error = {
                source: d.error.source || null,
                code: d.error.code || null,
                message: d.error.message || null,
                recoverable: !!d.error.recoverable,
                app_code: d.error.appCode || null,
                provider_code: d.error.providerCode || null,
                http_status_code: d.error.httpStatusCode || null
            };
        }
        if (d.retry) {
            record.retry = {
                attempt: d.retry.attempt || null,
                max_attempts: d.retry.maxAttempts || null,
                retry_after_ms: d.retry.retryAfterMs || null
            };
        }

        // 追加写入文件
        var writeErr = await writeJsonl(filePath, JSON.stringify(record));

        return {
            success: writeErr === null,
            message: writeErr ? "查询成功但写入失败: " + writeErr : "状态已记录到文件",
            data: record,
            output_path: filePath
        };
    }

    async function read_monitor_log(params) {
        var filePath = (params && params.output_path) ? String(params.output_path).trim() : DEFAULT_OUTPUT_PATH;
        var lastN = (params && params.last_n) ? parseInt(params.last_n, 10) : 0;
        var result = await readJsonl(filePath, lastN);
        if (result.success) {
            return {
                success: true,
                message: "共读取 " + result.total + " 条记录",
                data: result.data,
                total: result.total,
                output_path: filePath
            };
        } else {
            return { success: false, message: "读取失败: " + result.error };
        }
    }

    return {
        monitor_chat_runtime_state: monitor_chat_runtime_state,
        read_monitor_log: read_monitor_log
    };
})();

exports.monitor_chat_runtime_state = ChatRuntimeStateMonitor.monitor_chat_runtime_state;
exports.read_monitor_log = ChatRuntimeStateMonitor.read_monitor_log;

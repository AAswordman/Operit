/*
METADATA
{
    "name": "super_admin",
    "display_name": {
        "zh": "超级管理员",
        "en": "Super Admin"
    },
    "description": {
        "zh": "超级管理员工具集，提供终端命令和Shell操作的高级功能。terminal工具运行在Ubuntu环境中（已正确挂载sdcard和storage），shell工具通过Shizuku/Root直接执行Android系统命令。适合需要进行底层系统管理和命令行操作的场景。",
        "en": "Super admin toolkit providing advanced terminal and shell capabilities. The terminal tool runs in an Ubuntu environment (with sdcard/storage mounted). The shell tool executes Android system commands directly via Shizuku/Root. Useful for low-level system administration and CLI operations."
    },
    "enabledByDefault": true,
    "category": "System",
    "tools": [
        {
            "name": "terminal",
            "description": {
                "zh": "在Ubuntu环境中执行命令并收集输出结果。运行环境：完整的Ubuntu系统，已正确挂载sdcard和storage目录，可访问Android存储空间。所有命令将会在相同的会话执行且上下文连贯。强烈建议每次都显式传 timeoutMs，避免命令卡住。禁止使用 `set -e`、`set -o errexit` 等会改变 shell 退出行为的命令，这会导致终端会话退出并返回失败。若未传，前台默认15秒超时；background=true 时不使用该默认超时。命令超时时精确取消本次命令；无法恢复时关闭会话。",
                "en": "Execute commands in an Ubuntu environment and collect output. Environment: full Ubuntu system with sdcard/storage mounted, allowing access to Android storage. Automatically preserves working-directory context. Strongly recommend explicitly passing timeoutMs every time to avoid hangs. Do not use commands such as `set -e` or `set -o errexit` that change shell exit behavior, because they can terminate the terminal session and return failure. If omitted, foreground mode defaults to 15s timeout; background=true does not use this default timeout. Timeout cancels this command precisely; an unrecoverable session is closed."
            },
            "parameters": [
                {
                    "name": "command",
                    "description": {
                        "zh": "要执行的命令",
                        "en": "Command to execute."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "background",
                    "description": {
                        "zh": "是否在后台运行命令,\"true\" 表示后台执行并立即返回,适合启动服务器等长时间运行的任务（AI 不会收到该命令的输出结果），\"false\" 或未提供则前台执行并等待并返回命令结果",
                        "en": "Run command in background. 'true' runs in background and returns immediately (good for long-running tasks like servers; AI will not receive output). 'false' or omitted runs in foreground and returns the command result."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeoutMs",
                    "description": {
                        "zh": "可选超时（毫秒，最低3000ms）。强烈建议显式传入；未传时前台默认15000ms，background=true时不使用默认超时。",
                        "en": "Optional timeout (ms, minimum 3000ms). Strongly recommended to pass explicitly; if omitted, foreground defaults to 15000ms, and background=true does not use the default timeout."
                    },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_wait",
            "description": {
                "zh": "等待同一终端会话中的上一条命令执行完成。与 sleep 不同，本工具会在命令实际完成时提前返回，而不是固定睡眠。超时时仅取消本次排队等待；使用 terminal_poll 等待托管任务不会取消任务。",
                "en": "Wait until the previous command in the same terminal session finishes. Unlike sleep, this tool can return early as soon as the command actually completes. Timeout cancels only this queued wait. Use terminal_poll to wait for a managed task without cancellation."
            },
            "parameters": [
                {
                    "name": "sessionId",
                    "description": {
                        "zh": "可选目标会话ID。不传则使用当前对话的默认会话；无 chatId 时为 super_admin_default_session。",
                        "en": "Optional target session ID. If omitted, uses the current chat's default session; without chatId it is super_admin_default_session."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeoutMs",
                    "description": {
                        "zh": "可选超时（毫秒，最低3000ms）。未传时默认300000ms（5分钟）。",
                        "en": "Optional timeout (ms, minimum 3000ms). Defaults to 300000ms (5 minutes) if omitted."
                    },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_start",
            "description": {
                "zh": "启动托管终端命令，返回 runId 和当前状态。yieldMs 只限制本次等待；timeoutMs 限制含排队的总执行时间。命令仍运行时用 terminal_poll 查询同一 runId，不要重跑。output/screen 是替换快照。",
                "en": "Start a managed terminal command and return runId/status. yieldMs limits this wait; timeoutMs limits total execution including queue time. Poll the same runId; do not restart. output/screen are replacement snapshots."
            },
            "parameters": [
                {
                    "name": "command",
                    "description": {
                        "zh": "要执行的命令",
                        "en": "要执行的命令"
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "sessionId",
                    "description": {
                        "zh": "可选会话 ID；未传则创建或获取当前对话默认会话",
                        "en": "可选会话 ID；未传则创建或获取当前对话默认会话"
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeoutMs",
                    "description": {
                        "zh": "执行期限，至少 3000ms，默认 1800000ms",
                        "en": "执行期限，至少 3000ms，默认 1800000ms"
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "yieldMs",
                    "description": {
                        "zh": "单次等待 0～30000ms，默认 10000ms；到期不取消命令",
                        "en": "单次等待 0～30000ms，默认 10000ms；到期不取消命令"
                    },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_poll",
            "description": {
                "zh": "查询同一 runId 的输出尾部、动态画面和状态；等待结束不取消任务，不发送探测命令。失效 ID 会报错，不自动重跑。",
                "en": "Poll output tail, screen and status for the same runId without cancelling or submitting probes. Expired IDs fail explicitly."
            },
            "parameters": [
                {
                    "name": "runId",
                    "description": {
                        "zh": "terminal_start 返回的任务 ID",
                        "en": "terminal_start 返回的任务 ID"
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "yieldMs",
                    "description": {
                        "zh": "单次等待 0～30000ms，默认 10000ms",
                        "en": "单次等待 0～30000ms，默认 10000ms"
                    },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_cancel",
            "description": {
                "zh": "精确取消指定 runId；排队任务只移除自己，运行任务中断后不能恢复时退役会话。查看 cancellationOutcome 确认清理结果。",
                "en": "Cancel the specified runId and inspect cancellationOutcome. Queued tasks only remove themselves; unrecoverable running sessions are retired."
            },
            "parameters": [
                {
                    "name": "runId",
                    "description": {
                        "zh": "terminal_start 返回的任务 ID",
                        "en": "terminal_start 返回的任务 ID"
                    },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "terminal_getscreen",
            "description": {
                "zh": "获取目标终端会话可见屏幕内容（仅一屏，不包含历史滚动缓冲）。可通过 sessionId 指定会话；不传则使用当前对话的默认会话。",
                "en": "Get the visible screen content for the target terminal session (single screen only, no scrollback history). Pass sessionId to target a session; if omitted, uses the current chat's default session."
            },
            "parameters": [
                {
                    "name": "sessionId",
                    "description": {
                        "zh": "可选目标会话ID。不传则使用当前对话的默认会话；无 chatId 时为 super_admin_default_session。",
                        "en": "Optional target session ID. If omitted, uses the current chat's default session; without chatId it is super_admin_default_session."
                    },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_input",
            "description": {
                "zh": "向目标终端会话写入输入。可通过 sessionId 指定会话；不传则使用当前对话的默认会话。input 与 control 至少传一个。常见用法：先写 input，再写 control=enter 提交；control=ctrl 且 input=c 可发送 Ctrl+C。",
                "en": "Write input to the target terminal session. Pass sessionId to target a session; if omitted, uses the current chat's default session. Provide at least one of input or control. Typical usage: send input first, then control=enter to submit; use control=ctrl with input=c for Ctrl+C."
            },
            "parameters": [
                {
                    "name": "sessionId",
                    "description": {
                        "zh": "可选目标会话ID。不传则使用当前对话的默认会话；无 chatId 时为 super_admin_default_session。",
                        "en": "Optional target session ID. If omitted, uses the current chat's default session; without chatId it is super_admin_default_session."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "input",
                    "description": {
                        "zh": "写入终端的文本",
                        "en": "Text to write to terminal."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "control",
                    "description": {
                        "zh": "控制键，例如 enter / tab / esc / ctrl",
                        "en": "Control key, e.g. enter / tab / esc / ctrl."
                    },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "shell",
            "description": {
                "zh": "通过Shizuku/Root权限直接在Android系统中执行Shell命令。运行环境：直接访问Android系统，具有系统级权限，适用于需要操作Android系统底层的场景（如pm、am等系统命令）。",
                "en": "Execute shell commands directly on Android with Shizuku/Root. Environment: direct Android system access with system-level privileges, suitable for low-level commands such as pm/am."
            },
            "parameters": [
                {
                    "name": "command",
                    "description": {
                        "zh": "要执行的Shell命令",
                        "en": "Shell command to execute."
                    },
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}*/

const superAdmin = (function () {
    const MAX_INLINE_TERMINAL_OUTPUT_CHARS = 12_000;
    const DEFAULT_FOREGROUND_TIMEOUT_MS = 15_000;
    const DEFAULT_WAIT_TIMEOUT_MS = 300_000;
    const MIN_TIMEOUT_MS = 3_000;
    const DEFAULT_TERMINAL_SESSION_NAME = "super_admin_default_session";
    const BACKGROUND_TERMINAL_SESSION_PREFIX = "super_admin_background";

    function getCurrentChatSessionSuffix(): string {
        const chatId = getChatId();
        if (chatId === undefined) {
            return "";
        }
        const normalizedChatId = chatId.trim();
        if (!normalizedChatId) {
            return "";
        }
        return normalizedChatId.replace(/[^a-zA-Z0-9._-]+/g, "_");
    }

    function getDefaultTerminalSessionName(): string {
        const chatSuffix = getCurrentChatSessionSuffix();
        return chatSuffix
            ? `${DEFAULT_TERMINAL_SESSION_NAME}_${chatSuffix}`
            : DEFAULT_TERMINAL_SESSION_NAME;
    }

    function getBackgroundTerminalSessionName(): string {
        const chatSuffix = getCurrentChatSessionSuffix();
        const prefix = chatSuffix
            ? `${BACKGROUND_TERMINAL_SESSION_PREFIX}_${chatSuffix}`
            : BACKGROUND_TERMINAL_SESSION_PREFIX;
        return `${prefix}_${Date.now()}`;
    }

    async function persistTerminalOutputIfTooLong(command: string, result: any): Promise<any | null> {
        const outputStr = typeof result?.output === "string"
            ? result.output
            : String(result?.output ?? "");

        if (outputStr.length <= MAX_INLINE_TERMINAL_OUTPUT_CHARS) {
            return null;
        }

        await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);

        const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
        const rand = Math.floor(Math.random() * 1_000_000);
        const filePath = `${OPERIT_CLEAN_ON_EXIT_DIR}/terminal_output_${timestamp}_${rand}.log`;

        await Tools.Files.write(filePath, outputStr, false);

        return {
            command,
            output: "(saved_to_file)",
            exitCode: result?.exitCode,
            sessionId: result?.sessionId,
            timedOut: result?.timedOut === true,
            context_preserved: result?.timedOut !== true,
            output_saved_to: filePath,
            output_chars: outputStr.length,
            operit_clean_on_exit_dir: OPERIT_CLEAN_ON_EXIT_DIR,
            hint: "Output is large and saved to file. Use read_file_part or grep_code to inspect it.",
        };
    }

    /**
     * 在Ubuntu环境中执行终端命令并收集输出结果
     * 运行环境：完整的Ubuntu系统，已正确挂载sdcard和storage目录
     * 禁止使用 set -e / set -o errexit 等会改变 shell 退出行为的命令，否则可能导致终端会话退出并卡死
     * @param command - 要执行的命令
     * @param background - 是否后台运行（"true" 为后台执行并立即返回，适合启动服务器等长时间运行任务，AI 不会收到该命令的输出结果）
     * @param timeoutMs - 可选的超时时间（毫秒，最低 3000ms）。强烈建议显式传入；前台未传时默认 15000ms，后台模式不应用该默认值。
     */
    async function terminal(params: { command: string, background?: string, timeoutMs?: string }): Promise<any> {
        try {
            if (!params.command) {
                throw new Error("命令不能为空");
            }

            const command = params.command;
            const background = params.background;
            const timeoutMs = params.timeoutMs;

            console.log(`执行终端命令: ${command}`);

            const isBackground = background === "true";
            let timeout: number | undefined;
            if (!isBackground) {
                if (timeoutMs !== undefined) {
                    const parsedTimeout = parseInt(timeoutMs, 10);
                    if (!Number.isFinite(parsedTimeout) || parsedTimeout < MIN_TIMEOUT_MS) {
                        throw new Error(`timeoutMs必须是整数且不少于${MIN_TIMEOUT_MS}毫秒`);
                    }
                    timeout = parsedTimeout;
                } else {
                    timeout = DEFAULT_FOREGROUND_TIMEOUT_MS;
                }
            }

            if (isBackground) {
                const session = await Tools.System.terminal.create(getBackgroundTerminalSessionName());
                const sessionId = session.sessionId;

                // 调用系统工具执行终端命令
                (async () => {
                    try {
                        await Tools.System.terminal.exec(sessionId, command);
                    } catch (error) {
                        console.error(`[terminal/background] 错误: ${error.message}`);
                        console.error(error.stack);
                    }
                })();

                return {
                    command: command,
                    background: true,
                    sessionId: sessionId,
                    started: true
                };
            }

            // 创建或获取一个默认会话
            const session = await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;

            // 调用系统工具执行终端命令
            const result = await Tools.System.terminal.exec(sessionId, command, timeout);
            const timedOut = result.timedOut === true;

            const persistedResult = await persistTerminalOutputIfTooLong(command, result);
            if (persistedResult) {
                persistedResult.timeoutMsUsed = timeout;
                return persistedResult;
            }

            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode,
                sessionId: result.sessionId,
                timedOut: timedOut,
                timeoutMsUsed: timeout,
                context_preserved: !timedOut
            };
        } catch (error) {
            console.error(`[terminal] 错误: ${error.message}`);
            console.error(error.stack);
            // 保留终端失败的结构化结果，让外层显示失败并保留输出和退出码。
            if (error.data && typeof error.data.exitCode === "number" && error.data.exitCode !== 0) {
                return { success: false, message: error.message, data: error.data };
            }
            throw error;
        }
    }

    async function bash(params: { command: string, background?: string, timeoutMs?: string }): Promise<any> {
        return terminal(params);
    }

    /**
     * 等待同一终端会话中的上一条命令执行完成
     * 原理：向同会话追加一个内部 marker 命令。由于会话按序执行，marker 开始执行即代表前序命令已完成。
     * @param sessionId - 可选会话ID；不传时使用当前对话的默认会话，无 chatId 时为 super_admin_default_session
     * @param timeoutMs - 可选超时（毫秒，最低 3000ms）；未传默认 300000ms
     */
    async function terminal_wait(params: { sessionId?: string, timeoutMs?: string } = {}): Promise<any> {
        try {
            const timeoutMs = params.timeoutMs;
            let timeout = DEFAULT_WAIT_TIMEOUT_MS;
            if (timeoutMs !== undefined) {
                const parsedTimeout = parseInt(timeoutMs, 10);
                if (!Number.isFinite(parsedTimeout) || parsedTimeout < MIN_TIMEOUT_MS) {
                    throw new Error(`timeoutMs必须是整数且不少于${MIN_TIMEOUT_MS}毫秒`);
                }
                timeout = parsedTimeout;
            }

            const session =
                params.sessionId
                    ? { sessionId: params.sessionId }
                    : await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;

            const marker = `__OPERIT_TERMINAL_WAIT_DONE_${Date.now()}_${Math.floor(Math.random() * 1_000_000)}__`;
            const waitCommand = `printf '${marker}\\n'`;
            const startedAt = Date.now();
            const result = await Tools.System.terminal.exec(sessionId, waitCommand, timeout);
            const elapsedMs = Date.now() - startedAt;
            const timedOut = result?.timedOut === true;

            const outputStr = typeof result?.output === "string"
                ? result.output
                : String(result?.output ?? "");
            const markerSeen = outputStr.includes(marker);

            return {
                sessionId,
                timedOut,
                timeoutMsUsed: timeout,
                elapsedMs,
                waitCompleted: !timedOut && markerSeen,
                markerSeen,
                exitCode: result?.exitCode,
                context_preserved: !timedOut
            };
        } catch (error) {
            console.error(`[terminal_wait] 错误: ${error.message}`);
            console.error(error.stack);
            throw error;
        }
    }

    /**
     * 通过Shizuku/Root权限在Android系统中执行Shell命令
     * 运行环境：直接访问Android系统，具有系统级权限
     * @param command - 要执行的Shell命令
     */
    async function shell(params: { command: string }): Promise<any> {
        try {
            if (!params.command) {
                throw new Error("命令不能为空");
            }
            const command = params.command;

            console.log(`执行Shell命令: ${command}`);

            // 通过Shizuku/Root权限执行shell操作
            const result = await Tools.System.shell(`${command}`);

            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode
            };
        } catch (error) {
            console.error(`[shell] 错误: ${error.message}`);
            console.error(error.stack);

            throw error;
        }
    }

    /**
     * 获取目标终端会话可见屏幕内容（仅一屏，不包含历史）
     * @param sessionId - 可选会话ID；不传时使用当前对话的默认会话，无 chatId 时为 super_admin_default_session
     */
    async function terminal_getscreen(params: { sessionId?: string } = {}): Promise<any> {
        try {
            const session =
                params.sessionId
                    ? { sessionId: params.sessionId }
                    : await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;
            const result = await Tools.System.terminal.screen(sessionId);
            return {
                sessionId: result.sessionId ?? sessionId,
                rows: result.rows,
                cols: result.cols,
                content: result.content
            };
        } catch (error) {
            console.error(`[terminal_getscreen] 错误: ${error.message}`);
            console.error(error.stack);
            throw error;
        }
    }

    /**
     * 向目标终端会话写入输入
     * @param sessionId - 可选会话ID；不传时使用当前对话的默认会话，无 chatId 时为 super_admin_default_session
     * @param input - 文本输入
     * @param control - 控制键
     */
    async function terminal_input(params: { sessionId?: string, input?: string, control?: string } = {}): Promise<any> {
        try {
            if (params.input === undefined && params.control === undefined) {
                throw new Error("input和control至少需要提供一个");
            }

            const session =
                params.sessionId
                    ? { sessionId: params.sessionId }
                    : await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;
            const result = await Tools.System.terminal.input(sessionId, {
                input: params.input,
                control: params.control
            });

            return {
                sessionId,
                input: params.input,
                control: params.control,
                result
            };
        } catch (error) {
            console.error(`[terminal_input] 错误: ${error.message}`);
            console.error(error.stack);
            throw error;
        }
    }

    /** 新入口保留任务 ID，查询请求结束后命令仍由应用持有。 */
    function taskMilliseconds(value: string | undefined, name: string, minimum: number, maximum = Number.MAX_SAFE_INTEGER): number | undefined {
        if (value === undefined) return undefined;
        const parsed = Number(value);
        if (!/^\d+$/.test(value) || !Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
            throw new Error(`${name}必须是${minimum}～${maximum}之间的整数毫秒`);
        }
        return parsed;
    }

    async function terminal_start(params: { command: string, sessionId?: string, timeoutMs?: string, yieldMs?: string }): Promise<TerminalTaskResultData> {
        try {
            if (!params.command.trim()) throw new Error("命令不能为空");
            const timeoutMs = taskMilliseconds(params.timeoutMs, "timeoutMs", MIN_TIMEOUT_MS);
            const yieldMs = taskMilliseconds(params.yieldMs, "yieldMs", 0, 30_000);
            const sessionId = params.sessionId === undefined
                ? (await Tools.System.terminal.create(getDefaultTerminalSessionName())).sessionId
                : params.sessionId;
            return await Tools.System.terminal.start(sessionId, params.command, { timeoutMs, yieldMs });
        } catch (error) {
            console.error("[terminal_start] 托管启动失败", error);
            throw error;
        }
    }

    async function terminal_poll(params: { runId: string, yieldMs?: string }): Promise<TerminalTaskResultData> {
        try {
            const yieldMs = taskMilliseconds(params.yieldMs, "yieldMs", 0, 30_000);
            return await Tools.System.terminal.poll(params.runId, { yieldMs });
        } catch (error) {
            console.error("[terminal_poll] 托管查询失败", error);
            throw error;
        }
    }

    async function terminal_cancel(params: { runId: string }): Promise<TerminalTaskResultData> {
        try {
            return await Tools.System.terminal.cancel(params.runId);
        } catch (error) {
            console.error("[terminal_cancel] 托管取消失败", error);
            throw error;
        }
    }

    return {
        terminal,
        bash,
        terminal_wait,
        terminal_start,
        terminal_poll,
        terminal_cancel,
        terminal_getscreen,
        terminal_input,
        shell
    };
})();

// 逐个导出
exports.terminal = superAdmin.terminal;
exports.bash = superAdmin.bash;
exports.terminal_wait = superAdmin.terminal_wait;
exports.terminal_start = superAdmin.terminal_start;
exports.terminal_poll = superAdmin.terminal_poll;
exports.terminal_cancel = superAdmin.terminal_cancel;
exports.terminal_getscreen = superAdmin.terminal_getscreen;
exports.terminal_input = superAdmin.terminal_input;
exports.shell = superAdmin.shell; 

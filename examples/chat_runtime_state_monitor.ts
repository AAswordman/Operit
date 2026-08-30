/* METADATA
{
    "name": "chat_runtime_state_monitor",
    "display_name": {
        "zh": "聊天运行状态监视器",
        "en": "Chat Runtime State Monitor"
    },
    "description": {
        "zh": "每隔 5 秒读取一次当前或指定对话的运行状态，并将采样结果写入 JSONL 文件。",
        "en": "Read the runtime state of the current or specified chat every 5 seconds and write samples to a JSONL file."
    },
    "category": "Chat",
    "tools": [
        {
            "name": "monitor_chat_runtime_state",
            "description": {
                "zh": "立即采样一次，之后每隔 5 秒采样并写入文件；默认采样 12 次。",
                "en": "Sample immediately, then every 5 seconds and write each result to a file; defaults to 12 samples."
            },
            "parameters": [
                { "name": "chat_id", "description": { "zh": "可选：目标对话 ID；为空时读取当前对话", "en": "Optional target chat ID; omit for the current chat" }, "type": "string", "required": false },
                { "name": "output_path", "description": { "zh": "可选：JSONL 输出路径", "en": "Optional JSONL output path" }, "type": "string", "required": false, "default": "/sdcard/Download/chat_runtime_state.jsonl" },
                { "name": "sample_count", "description": { "zh": "可选：采样次数，默认 12，范围 1-120", "en": "Optional sample count, default 12, range 1-120" }, "type": "number", "required": false, "default": 12 },
                { "name": "append", "description": { "zh": "可选：是否保留输出文件中的旧记录，默认 false", "en": "Optional: preserve existing records in the output file, default false" }, "type": "boolean", "required": false, "default": false }
            ]
        }
    ]
} */

/// <reference path="./types/index.d.ts" />

const ChatRuntimeStateMonitor = (function () {
    const SAMPLE_INTERVAL_MS = 5000;
    const DEFAULT_OUTPUT_PATH = '/sdcard/Download/chat_runtime_state.jsonl';
    const DEFAULT_SAMPLE_COUNT = 12;
    const MAX_SAMPLE_COUNT = 120;

    interface MonitorParams {
        chat_id?: string;
        output_path?: string;
        sample_count?: number;
        append?: boolean;
    }

    function sleep(milliseconds: number): Promise<void> {
        return new Promise((resolve) => setTimeout(resolve, milliseconds));
    }

    function normalizeSampleCount(value: unknown): number {
        if (value === undefined || value === null) return DEFAULT_SAMPLE_COUNT;
        const count = Math.floor(Number(value));
        if (!isFinite(count) || count < 1 || count > MAX_SAMPLE_COUNT) {
            throw new Error(`sample_count must be between 1 and ${MAX_SAMPLE_COUNT}`);
        }
        return count;
    }

    function errorText(error: any): string {
        if (error && typeof error.message === 'string') return error.message;
        return String(error);
    }

    async function monitor_chat_runtime_state_impl(params: MonitorParams) {
        const chatId = (params?.chat_id ?? '').toString().trim();
        const outputPath = (params?.output_path ?? DEFAULT_OUTPUT_PATH).toString().trim();
        const sampleCount = normalizeSampleCount(params?.sample_count);
        const preserveExisting = params?.append === true;

        if (!outputPath) throw new Error('output_path cannot be empty');

        const startedAt = new Date().toISOString();
        let successfulSamples = 0;
        let failedSamples = 0;

        for (let index = 0; index < sampleCount; index += 1) {
            if (index > 0) await sleep(SAMPLE_INTERVAL_MS);

            const sampledAt = new Date().toISOString();
            let record: Record<string, any>;
            try {
                const state = await Tools.Chat.getCurrentChatRuntimeState(chatId || undefined);
                successfulSamples += 1;
                record = {
                    sample: index + 1,
                    sampled_at: sampledAt,
                    chat_id: chatId || null,
                    success: true,
                    state,
                };
            } catch (error: any) {
                failedSamples += 1;
                record = {
                    sample: index + 1,
                    sampled_at: sampledAt,
                    chat_id: chatId || null,
                    success: false,
                    error: errorText(error),
                };
            }

            const append = preserveExisting || index > 0;
            await Tools.Files.write(outputPath, JSON.stringify(record) + '\n', append, 'android');
        }

        return {
            success: failedSamples === 0,
            message: failedSamples === 0 ? '聊天运行状态采样完成' : '采样完成，但部分状态读取失败',
            data: {
                chat_id: chatId || null,
                output_path: outputPath,
                interval_ms: SAMPLE_INTERVAL_MS,
                sample_count: sampleCount,
                successful_samples: successfulSamples,
                failed_samples: failedSamples,
                started_at: startedAt,
                finished_at: new Date().toISOString(),
            },
        };
    }

    async function monitor_chat_runtime_state(params?: MonitorParams) {
        try {
            complete(await monitor_chat_runtime_state_impl(params || {}));
        } catch (error: any) {
            console.error('monitor_chat_runtime_state failed', error);
            complete({
                success: false,
                message: `聊天运行状态监视失败: ${errorText(error)}`,
            });
        }
    }

    return { monitor_chat_runtime_state };
})();

exports.monitor_chat_runtime_state = ChatRuntimeStateMonitor.monitor_chat_runtime_state;

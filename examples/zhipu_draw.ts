/* METADATA
{
  "name": "zhipu_draw",
  "display_name": {
    "zh": "智谱生图",
    "en": "Zhipu Draw"
  },
  "description": {
    "zh": "使用智谱AI图像生成API根据提示词画图（当前官方云端接口仅支持文生图，不支持传入参考图）。将图片保存到本地 /sdcard/Download/Operit/plugins/draw/zhipu_draw/draws/ 目录，并返回 Markdown 图片提示。",
    "en": "Generate images via Zhipu AI image generation API from a prompt (the official cloud API currently supports text-to-image only, not image-to-image). Saves to /sdcard/Download/Operit/plugins/draw/zhipu_draw/draws/ and returns a Markdown image reference."
  },
  "env": [
    {
      "name": "ZHIPU_API_KEY",
      "description": {
        "zh": "智谱API Key（必填）",
        "en": "Zhipu API key (required)"
      },
      "required": true
    },
    {
      "name": "ZHIPU_IMAGE_MODEL",
      "description": {
        "zh": "默认绘图模型（可选；当 draw_image 未传 model 时使用）",
        "en": "Default image model (optional; used when draw_image doesn't pass model)"
      },
      "required": false
    }
  ],
  "category": "Draw",
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "根据提示词调用智谱AI图像生成接口生成图片（仅文生图，官方云端接口暂不支持参考图）。保存到本地并返回 Markdown 图片提示。",
        "en": "Generate an image via Zhipu AI image generation API using a prompt (text-to-image only; the official cloud API does not currently accept reference images). Saves locally and returns a Markdown image reference."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图提示词（中文或英文）", "en": "Prompt for image generation (Chinese or English)" }, "type": "string", "required": true },
        { "name": "size", "description": { "zh": "图片尺寸，例如 '1024x1024' 或 '1280x1280'（可选，默认1024x1024）", "en": "Image size, e.g. '1024x1024' or '1280x1280' (optional, default 1024x1024)" }, "type": "string", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false },
        { "name": "model", "description": { "zh": "模型名称（可选；不传则使用环境变量 ZHIPU_IMAGE_MODEL，否则默认glm-image）", "en": "Model name (optional; falls back to env ZHIPU_IMAGE_MODEL)" }, "type": "string", "required": false }
      ]
    }
  ]
}*/

/// <reference path="./types/index.d.ts" />

const zhipuDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();

    const API_BASE_URL = "https://open.bigmodel.cn/api/paas/v4/images/generations";
    const DRAW_ROOT_DIR = getPluginConfigDir("draw");
    const STORAGE_DIR = `${DRAW_ROOT_DIR}/zhipu_draw`;
    const DRAWS_DIR = `${STORAGE_DIR}/draws`;

    interface DrawImageParams {
        prompt: string;
        size?: string;
        file_name?: string;
        model?: string;
    }

    interface ZhipuImageApiResult {
        url: string;
        model?: string;
    }

    interface DrawImageResult {
        file_path: string;
        file_uri: string;
        markdown: string;
        prompt: string;
        hint: string;
    }

    function getErrorMessage(error: unknown): string {
        if (error instanceof Error) return error.message;
        return String(error);
    }

    function getErrorStack(error: unknown): string | undefined {
        if (error instanceof Error) return error.stack;
        return undefined;
    }

    function getApiKey(): string {
        const apiKey = getEnv("ZHIPU_API_KEY");
        if (!apiKey) {
            throw new Error("ZHIPU_API_KEY 未配置，请在环境变量中设置智谱的 API Key。");
        }
        return apiKey;
    }

    function sanitizeFileName(name: string): string {
        const safe = name.replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) {
            return `zhipu_draw_${Date.now()}`;
        }
        return safe.substring(0, 80);
    }

    function buildFileName(prompt: string, customName?: string | null): string {
        if (customName && customName.trim().length > 0) {
            return sanitizeFileName(customName);
        }
        const shortPrompt = prompt.length > 40 ? `${prompt.substring(0, 40)}...` : prompt;
        const base = sanitizeFileName(shortPrompt || "image");
        const timestamp = Date.now();
        return `${base}_${timestamp}`;
    }

    async function ensureDirectories(): Promise<void> {
        const dirs = [DRAW_ROOT_DIR, STORAGE_DIR, DRAWS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            } catch (e: unknown) {
                console.warn(`创建目录异常: ${dir} -> ${getErrorMessage(e)}`);
            }
        }
    }

    async function callZhipuImageAPI(params: {
        prompt: string;
        size?: string;
        model?: string;
    }): Promise<ZhipuImageApiResult> {
        const apiKey = getApiKey();
        const modelFromParam = (params.model || "").trim();
        const modelFromEnv = (getEnv("ZHIPU_IMAGE_MODEL") || "").trim();
        const effectiveModel = modelFromParam || modelFromEnv || "glm-image";

        const body = {
            model: effectiveModel,
            prompt: params.prompt,
            size: params.size || "1024x1024"
        };

        const headers = {
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`
        };

        const request = client
            .newRequest()
            .url(API_BASE_URL)
            .method("POST")
            .headers(headers)
            .body(JSON.stringify(body), "json");

        const response = await request.build().execute();

        if (!response.isSuccessful()) {
            throw new Error(`智谱图片 API 调用失败: ${response.statusCode} - ${response.content}`);
        }

        let parsed: unknown;
        try {
            parsed = JSON.parse(response.content) as unknown;
        } catch (e: unknown) {
            throw new Error(`解析智谱响应失败: ${getErrorMessage(e)}`);
        }

        const first = (parsed as any)?.data?.[0];
        if (!first || !first.url) {
            throw new Error("智谱响应中未找到有效的图片数据，请检查API返回格式");
        }

        const result: ZhipuImageApiResult = {
            url: String(first.url),
            model: first.model ? String(first.model) : undefined
        };

        if (!result.model) {
            result.model = effectiveModel;
        }

        return result;
    }

    async function draw_image(params: DrawImageParams): Promise<DrawImageResult> {
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }

        const prompt = params.prompt.trim();
        const size = params.size || "1024x1024";
        const model = params.model;

        await ensureDirectories();

        const apiResult = await callZhipuImageAPI({ prompt, size, model });

        if (!apiResult.url || !apiResult.url.trim()) {
            throw new Error("API响应中未包含图片URL");
        }

        const baseName = buildFileName(prompt, params.file_name);
        const filePath = `${DRAWS_DIR}/${baseName}.png`;

        const downloadResult = await Tools.Files.download(apiResult.url, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载图片失败: ${downloadResult.details}`);
        }

        const fileUri = `file://${filePath}`;
        const markdown = `![AI生成的图片](${fileUri})`;

        const hintLines: string[] = [];
        hintLines.push(`图片已生成并保存在本地 ${DRAWS_DIR}。`);
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push(`提示词: ${prompt}`);
        hintLines.push(`模型: ${apiResult.model || "glm-image"}`);
        hintLines.push("");
        hintLines.push("在后续回答中，请直接输出下面这一行 Markdown 来展示这张图片：");
        hintLines.push("");
        hintLines.push(markdown);

        return {
            file_path: filePath,
            file_uri: fileUri,
            markdown,
            prompt,
            hint: hintLines.join("\n")
        };
    }

    async function draw_image_wrapper(params: DrawImageParams) {
        try {
            const result = await draw_image(params);
            complete({
                success: true,
                message: `图片生成成功，已保存到 ${DRAWS_DIR}，并返回 Markdown 图片提示。`,
                data: result
            });
        } catch (error: unknown) {
            console.error("draw_image 执行失败:", error);
            complete({
                success: false,
                message: `图片生成失败: ${getErrorMessage(error)}`,
                error_stack: getErrorStack(error)
            });
        }
    }

    return {
        draw_image: draw_image_wrapper
    };
})();

exports.draw_image = zhipuDraw.draw_image;

"use strict";
/* METADATA
{
  "name": "qwen_draw",

  "display_name": {
      "zh": "Qwen 绘图",
      "en": "Qwen Draw"
  },
  "description": {
    "zh": "使用阿里云百炼/DashScope 画图。无参考图走文生图异步任务；传入 image_urls 或 image_paths 时走图生图（1-3 张参考图，本地图转成 data URI）。结果保存到 /sdcard/Download/Operit/plugins/draw/qwen_draw/draws/，并返回 Markdown 图片提示。",
    "en": "Generate images via Alibaba Cloud Model Studio (DashScope). Text-to-image uses the async task API; image-to-image accepts 1-3 reference images via image_urls or local image_paths (local files are sent as data URIs). Saves to /sdcard/Download/Operit/plugins/draw/qwen_draw/draws/ and returns a Markdown image reference."
  },
  "env": [
    {
      "name": "DASHSCOPE_API_KEY",
      "description": {
        "zh": "DashScope API Key（必填）",
        "en": "DashScope API key (required)"
      },
      "required": true
    },
    {
      "name": "DASHSCOPE_API_BASE_URL",
      "description": {
        "zh": "DashScope API Base URL（可选，不填则默认 https://dashscope.aliyuncs.com；国际站可用 https://dashscope-intl.aliyuncs.com）",
        "en": "DashScope API base URL (optional; defaults to https://dashscope.aliyuncs.com; intl: https://dashscope-intl.aliyuncs.com)"
      },
      "required": false
    },
    {
      "name": "QWEN_IMAGE_MODEL",
      "description": {
        "zh": "默认文生图模型（可选；当 draw_image 未传 model 且无参考图时使用，例如 qwen-image-plus 或 wan2.2-t2i-flash）",
        "en": "Default text-to-image model (optional; used when draw_image omits model and no reference images are given), e.g. qwen-image-plus or wan2.2-t2i-flash"
      },
      "required": false
    },
    {
      "name": "QWEN_IMAGE_EDIT_MODEL",
      "description": {
        "zh": "默认图生图/图像编辑模型（可选；当传入参考图且未传 model 时使用，默认 qwen-image-edit-plus）",
        "en": "Default image-edit model (optional; used when reference images are given and model is omitted, default qwen-image-edit-plus)"
      },
      "required": false
    }
  ],
  "category": "Draw",
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "根据提示词调用 DashScope 生成或编辑图片。传入 image_urls 或 image_paths 时走图生图（1-3 张，本地图转 data URI）。保存到本地并返回 Markdown 图片提示。",
        "en": "Generate or edit an image via DashScope. Pass image_urls or image_paths for image-to-image (1-3 images; local files are sent as data URIs). Saves locally and returns a Markdown image reference."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图或编辑提示词（英文或中文皆可）", "en": "Prompt for image generation or editing (Chinese or English)" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "模型名称（可选；文生图默认 QWEN_IMAGE_MODEL / qwen-image-plus，图生图默认 QWEN_IMAGE_EDIT_MODEL / qwen-image-edit-plus）", "en": "Model name (optional; text-to-image falls back to QWEN_IMAGE_MODEL / qwen-image-plus, image-to-image to QWEN_IMAGE_EDIT_MODEL / qwen-image-edit-plus)" }, "type": "string", "required": false },
        { "name": "size", "description": { "zh": "输出图像分辨率，如 '1664*928' 或 '1024x1024'（可选；部分编辑模型可能不支持）", "en": "Output image resolution, e.g. '1664*928' or '1024x1024' (optional; some edit models may not support it)" }, "type": "string", "required": false },
        { "name": "n", "description": { "zh": "生成图片数量（可选，默认 1）", "en": "Number of images (optional; default 1)" }, "type": "number", "required": false },
        { "name": "negative_prompt", "description": { "zh": "负面提示词（可选）", "en": "Negative prompt (optional)" }, "type": "string", "required": false },
        { "name": "prompt_extend", "description": { "zh": "是否开启 prompt 智能改写（可选，默认 true）", "en": "Enable prompt extension (optional; default true)" }, "type": "boolean", "required": false },
        { "name": "watermark", "description": { "zh": "是否加水印（可选，默认 false）", "en": "Enable watermark (optional; default false)" }, "type": "boolean", "required": false },
        { "name": "image_urls", "description": { "zh": "参考图公网 URL 数组（可选；图生图用，最多 3 张）。支持字符串数组、JSON 字符串或逗号分隔字符串", "en": "Public reference image URLs for image-to-image (optional, up to 3). Accepts a string array, JSON string, or comma-separated string." }, "type": "array", "required": false },
        { "name": "image_paths", "description": { "zh": "参考图本地路径数组（可选；图生图用，会转成 data URI，最多 3 张）", "en": "Local reference image paths for image-to-image (optional; converted to data URIs, up to 3)" }, "type": "array", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false },
        { "name": "api_base_url", "description": { "zh": "DashScope API Base URL（不传则取环境变量 DASHSCOPE_API_BASE_URL 或默认 https://dashscope.aliyuncs.com ）", "en": "DashScope API base URL (optional; falls back to env DASHSCOPE_API_BASE_URL or https://dashscope.aliyuncs.com)" }, "type": "string", "required": false },
        { "name": "poll_interval_ms", "description": { "zh": "轮询间隔（毫秒），默认 2000", "en": "Polling interval (milliseconds), default 2000" }, "type": "number", "required": false },
        { "name": "max_wait_time_ms", "description": { "zh": "最长等待时间（毫秒），默认 10 分钟", "en": "Max wait time (milliseconds), default 10 minutes" }, "type": "number", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const qwenDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();
    const DEFAULT_API_BASE_URL = "https://dashscope.aliyuncs.com";
    const DEFAULT_MODEL = "qwen-image-plus";
    const DEFAULT_EDIT_MODEL = "qwen-image-edit-plus";
    const MAX_IMAGE_REFERENCES = 3;
    const DRAW_ROOT_DIR = getPluginConfigDir("draw");
    const STORAGE_DIR = `${DRAW_ROOT_DIR}/qwen_draw`;
    const DRAWS_DIR = `${STORAGE_DIR}/draws`;
    const POLL_INTERVAL_MS = 2000;
    const MAX_WAIT_TIME_MS = 600000;
    function isRecord(value) {
        return typeof value === "object" && value !== null;
    }
    function getErrorMessage(error) {
        if (error instanceof Error)
            return error.message;
        return String(error);
    }
    function getErrorStack(error) {
        if (error instanceof Error)
            return error.stack;
        return undefined;
    }
    function normalizePositiveInt(value, fallback) {
        if (value === undefined || value === null)
            return fallback;
        const n = typeof value === "number" ? value : parseInt(String(value), 10);
        if (!Number.isFinite(n) || n <= 0)
            return fallback;
        return Math.floor(n);
    }
    function joinUrl(baseUrl, path) {
        const normalizedBase = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
        const normalizedPath = path.startsWith("/") ? path.slice(1) : path;
        return `${normalizedBase}${normalizedPath}`;
    }
    function getApiKey() {
        const apiKey = getEnv("DASHSCOPE_API_KEY");
        if (!apiKey) {
            throw new Error("DASHSCOPE_API_KEY 未配置，请在环境变量中设置 DashScope 的 API Key。");
        }
        return apiKey;
    }
    function getApiBaseUrl(customBaseUrl) {
        const fromParam = (customBaseUrl || "").trim();
        if (fromParam)
            return fromParam;
        const fromEnv = (getEnv("DASHSCOPE_API_BASE_URL") || "").trim();
        if (fromEnv)
            return fromEnv;
        return DEFAULT_API_BASE_URL;
    }
    function getTextToImageEndpoint(baseUrl) {
        const trimmed = baseUrl.trim();
        if (!trimmed)
            return joinUrl(DEFAULT_API_BASE_URL, "api/v1/services/aigc/text2image/image-synthesis");
        if (trimmed.includes("/api/v1/services/aigc/text2image/image-synthesis"))
            return trimmed;
        if (trimmed.includes("/api/v1/services/aigc/multimodal-generation/generation")) {
            return trimmed.replace("/api/v1/services/aigc/multimodal-generation/generation", "/api/v1/services/aigc/text2image/image-synthesis");
        }
        if (trimmed.endsWith("/api/v1"))
            return joinUrl(trimmed, "services/aigc/text2image/image-synthesis");
        if (trimmed.endsWith("/api/v1/"))
            return joinUrl(trimmed, "services/aigc/text2image/image-synthesis");
        return joinUrl(trimmed, "api/v1/services/aigc/text2image/image-synthesis");
    }
    function getImageEditEndpoint(baseUrl) {
        const trimmed = baseUrl.trim();
        if (!trimmed)
            return joinUrl(DEFAULT_API_BASE_URL, "api/v1/services/aigc/multimodal-generation/generation");
        if (trimmed.includes("/api/v1/services/aigc/multimodal-generation/generation"))
            return trimmed;
        if (trimmed.includes("/api/v1/services/aigc/text2image/image-synthesis")) {
            return trimmed.replace("/api/v1/services/aigc/text2image/image-synthesis", "/api/v1/services/aigc/multimodal-generation/generation");
        }
        if (trimmed.endsWith("/api/v1"))
            return joinUrl(trimmed, "services/aigc/multimodal-generation/generation");
        if (trimmed.endsWith("/api/v1/"))
            return joinUrl(trimmed, "services/aigc/multimodal-generation/generation");
        return joinUrl(trimmed, "api/v1/services/aigc/multimodal-generation/generation");
    }
    function getTaskEndpoint(baseUrl, taskId) {
        const trimmed = baseUrl.trim();
        const safeBase = trimmed || DEFAULT_API_BASE_URL;
        if (safeBase.includes("/api/v1/tasks/")) {
            const idx = safeBase.indexOf("/api/v1/tasks/");
            return safeBase.substring(0, idx) + `/api/v1/tasks/${taskId}`;
        }
        if (safeBase.endsWith("/api/v1"))
            return joinUrl(safeBase, `tasks/${taskId}`);
        if (safeBase.endsWith("/api/v1/"))
            return joinUrl(safeBase, `tasks/${taskId}`);
        return joinUrl(safeBase, `api/v1/tasks/${taskId}`);
    }
    function sanitizeFileName(name) {
        const safe = name.replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe)
            return `qwen_draw_${Date.now()}`;
        return safe.substring(0, 80);
    }
    function buildFileName(prompt, customName) {
        if (customName && customName.trim().length > 0) {
            return sanitizeFileName(customName);
        }
        const shortPrompt = prompt.length > 40 ? `${prompt.substring(0, 40)}...` : prompt;
        const base = sanitizeFileName(shortPrompt || "image");
        const timestamp = Date.now();
        return `${base}_${timestamp}`;
    }
    function normalizeSize(size) {
        const raw = String(size || "").trim();
        if (!raw)
            return undefined;
        const cleaned = raw.replace(/×/g, "*").replace(/x/gi, "*").replace(/\s+/g, "");
        if (!/^\d+\*\d+$/.test(cleaned))
            return undefined;
        return cleaned;
    }
    function parseStringList(value, fieldName) {
        if (value === undefined || value === null || value === "") {
            return [];
        }
        if (Array.isArray(value)) {
            return value.map(item => String(item || "").trim()).filter(item => item.length > 0);
        }
        if (typeof value === "string") {
            const trimmed = value.trim();
            if (!trimmed)
                return [];
            try {
                const parsed = JSON.parse(trimmed);
                if (Array.isArray(parsed)) {
                    return parsed.map(item => String(item || "").trim()).filter(item => item.length > 0);
                }
            }
            catch {
                // ignore and try comma-separated parsing
            }
            return trimmed.split(",").map(item => item.trim()).filter(item => item.length > 0);
        }
        throw new Error(`${fieldName} 必须是字符串数组、JSON 字符串或逗号分隔字符串。`);
    }
    function isProbablyUrl(value) {
        return /^https?:\/\//i.test(String(value || "").trim());
    }
    function guessMimeTypeFromPath(filePath) {
        const lower = String(filePath || "").toLowerCase();
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".webp"))
            return "image/webp";
        if (lower.endsWith(".gif"))
            return "image/gif";
        return "image/png";
    }
    async function readLocalImageAsDataUrl(filePath) {
        const trimmedPath = String(filePath || "").trim();
        if (!trimmedPath) {
            throw new Error("image_path 不能为空。");
        }
        const existsResult = await Tools.Files.exists(trimmedPath);
        if (!existsResult.exists) {
            throw new Error(`参考图文件不存在: ${trimmedPath}`);
        }
        const binaryResult = await Tools.Files.readBinary(trimmedPath);
        const base64Content = binaryResult && binaryResult.contentBase64
            ? String(binaryResult.contentBase64).trim()
            : "";
        if (!base64Content) {
            throw new Error(`读取本地图片失败: ${trimmedPath}`);
        }
        return `data:${guessMimeTypeFromPath(trimmedPath)};base64,${base64Content}`;
    }
    async function resolveImageReferences(imageUrls, imagePaths) {
        const resolvedUrls = parseStringList(imageUrls, "image_urls");
        const resolvedPaths = parseStringList(imagePaths, "image_paths");
        for (const url of resolvedUrls) {
            if (!isProbablyUrl(url) && !url.toLowerCase().startsWith("data:image/")) {
                throw new Error(`image_urls 中包含无效链接: ${url}`);
            }
        }
        for (const filePath of resolvedPaths) {
            resolvedUrls.push(await readLocalImageAsDataUrl(filePath));
        }
        if (resolvedUrls.length > MAX_IMAGE_REFERENCES) {
            throw new Error(`图生图参考图最多 ${MAX_IMAGE_REFERENCES} 张。`);
        }
        return resolvedUrls;
    }
    function guessExtensionFromUrl(url) {
        const match = url.match(/\.(png|jpg|jpeg|webp|gif)(?:\?|#|$)/i);
        if (match && match[1])
            return match[1].toLowerCase();
        return "png";
    }
    async function ensureDirectories() {
        const dirs = [DRAW_ROOT_DIR, STORAGE_DIR, DRAWS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            }
            catch (e) {
                console.warn(`创建目录异常: ${dir} -> ${getErrorMessage(e)}`);
            }
        }
    }
    function extractImmediateImageUrl(parsed) {
        const output = isRecord(parsed) && isRecord(parsed["output"]) ? parsed["output"] : null;
        if (!output)
            return "";
        const results = output["results"];
        const firstResult = Array.isArray(results) && results.length > 0 ? results[0] : null;
        if (isRecord(firstResult)) {
            const url = firstResult["url"];
            if ((typeof url === "string" || typeof url === "number") && String(url).trim()) {
                return String(url).trim();
            }
        }
        const choices = output["choices"];
        const firstChoice = Array.isArray(choices) && choices.length > 0 ? choices[0] : null;
        const message = isRecord(firstChoice) && isRecord(firstChoice["message"]) ? firstChoice["message"] : null;
        const content = message ? message["content"] : null;
        if (Array.isArray(content)) {
            for (const item of content) {
                if (!isRecord(item))
                    continue;
                const image = item["image"];
                if (typeof image === "string" && image.trim())
                    return image.trim();
                const url = item["url"] || item["image_url"];
                if (typeof url === "string" && url.trim())
                    return url.trim();
            }
        }
        return "";
    }
    async function createTask(params) {
        const apiKey = getApiKey();
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const references = await resolveImageReferences(params.image_urls, params.image_paths);
        const isEdit = references.length > 0;
        const endpoint = isEdit ? getImageEditEndpoint(apiBaseUrl) : getTextToImageEndpoint(apiBaseUrl);
        const modelFromParam = (params.model || "").trim();
        const modelFromEnv = (getEnv(isEdit ? "QWEN_IMAGE_EDIT_MODEL" : "QWEN_IMAGE_MODEL") || "").trim();
        const effectiveModel = modelFromParam || modelFromEnv || (isEdit ? DEFAULT_EDIT_MODEL : DEFAULT_MODEL);
        const parameters = {
            n: typeof params.n === "number" && Number.isFinite(params.n) && params.n > 0 ? Math.floor(params.n) : 1,
            prompt_extend: params.prompt_extend === undefined ? true : !!params.prompt_extend,
            watermark: params.watermark === undefined ? false : !!params.watermark
        };
        const normalizedSize = normalizeSize(params.size);
        if (normalizedSize) {
            parameters.size = normalizedSize;
        }
        const negativePrompt = (params.negative_prompt || "").trim();
        if (negativePrompt) {
            parameters.negative_prompt = negativePrompt;
        }
        const content = [];
        for (const image of references) {
            content.push({ image });
        }
        content.push({ text: params.prompt });
        const body = isEdit
            ? {
                model: effectiveModel,
                input: {
                    messages: [
                        {
                            role: "user",
                            content
                        }
                    ]
                },
                parameters
            }
            : {
                model: effectiveModel,
                input: {
                    prompt: params.prompt
                },
                parameters
            };
        const headers = {
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`,
            "X-DashScope-Async": "enable"
        };
        const request = client
            .newRequest()
            .url(endpoint)
            .method("POST")
            .headers(headers)
            .body(JSON.stringify(body), "json");
        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`DashScope 创建任务失败: ${response.statusCode} - ${response.content}`);
        }
        let parsed;
        try {
            parsed = JSON.parse(response.content);
        }
        catch (e) {
            throw new Error(`解析 DashScope 创建任务响应失败: ${getErrorMessage(e)}`);
        }
        const output = isRecord(parsed) && isRecord(parsed["output"]) ? parsed["output"] : null;
        const taskId = output && typeof output["task_id"] === "string" ? output["task_id"] : "";
        const immediateUrl = extractImmediateImageUrl(parsed);
        if (!taskId && !immediateUrl) {
            throw new Error(`DashScope 响应中未找到 output.task_id 或图片 URL: ${response.content}`);
        }
        return {
            task_id: taskId,
            image_url: immediateUrl,
            effective_model: effectiveModel,
            reference_count: references.length
        };
    }
    async function pollTask(params) {
        const apiKey = getApiKey();
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const pollIntervalMs = normalizePositiveInt(params.poll_interval_ms, POLL_INTERVAL_MS);
        const maxWaitTimeMs = normalizePositiveInt(params.max_wait_time_ms, MAX_WAIT_TIME_MS);
        const startTime = Date.now();
        let attempts = 0;
        while (Date.now() - startTime < maxWaitTimeMs) {
            attempts++;
            const endpoint = getTaskEndpoint(apiBaseUrl, params.task_id);
            const headers = {
                "accept": "application/json",
                "Authorization": `Bearer ${apiKey}`
            };
            const request = client
                .newRequest()
                .url(endpoint)
                .method("GET")
                .headers(headers);
            const response = await request.build().execute();
            if (!response.isSuccessful()) {
                throw new Error(`DashScope 查询任务失败: ${response.statusCode} - ${response.content}`);
            }
            let parsed;
            try {
                parsed = JSON.parse(response.content);
            }
            catch (e) {
                throw new Error(`解析 DashScope 查询任务响应失败: ${getErrorMessage(e)}`);
            }
            const output = isRecord(parsed) && isRecord(parsed["output"]) ? parsed["output"] : null;
            const taskStatus = output && typeof output["task_status"] === "string" ? String(output["task_status"]) : "";
            if (taskStatus === "SUCCEEDED") {
                const results = output ? output["results"] : null;
                const first = Array.isArray(results) && results.length > 0 ? results[0] : null;
                const url = isRecord(first) ? first["url"] : undefined;
                if ((typeof url !== "string" && typeof url !== "number") || String(url).trim().length === 0) {
                    throw new Error(`任务已完成但未找到图片 URL: ${response.content}`);
                }
                return { image_url: String(url), task_status: taskStatus };
            }
            if (taskStatus === "FAILED") {
                throw new Error(`任务失败: ${response.content}`);
            }
            if (attempts % 5 === 0) {
                console.log(`任务状态: ${taskStatus || "UNKNOWN"}，继续等待...`);
            }
            await Tools.System.sleep(pollIntervalMs);
        }
        throw new Error(`任务超时: 等待超过${Math.ceil(maxWaitTimeMs / 60000)}分钟仍未完成`);
    }
    async function draw_image(params) {
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }
        const prompt = params.prompt.trim();
        await ensureDirectories();
        const createResult = await createTask({
            prompt,
            model: params.model,
            size: params.size,
            n: params.n,
            negative_prompt: params.negative_prompt,
            prompt_extend: params.prompt_extend,
            watermark: params.watermark,
            api_base_url: params.api_base_url,
            image_urls: params.image_urls,
            image_paths: params.image_paths
        });
        const pollResult = createResult.image_url
            ? { image_url: createResult.image_url, task_status: "SUCCEEDED" }
            : await pollTask({
                task_id: createResult.task_id,
                api_base_url: params.api_base_url,
                poll_interval_ms: params.poll_interval_ms,
                max_wait_time_ms: params.max_wait_time_ms
            });
        const ext = guessExtensionFromUrl(pollResult.image_url);
        const baseName = buildFileName(prompt, params.file_name);
        const filePath = `${DRAWS_DIR}/${baseName}.${ext}`;
        const downloadResult = await Tools.Files.download(pollResult.image_url, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载图片失败: ${downloadResult.details}`);
        }
        const fileUri = `file://${filePath}`;
        const markdown = `![AI生成的图片](${fileUri})`;
        const hintLines = [];
        hintLines.push(`图片已生成并保存在本地 ${DRAWS_DIR}。`);
        if (createResult.reference_count > 0) {
            hintLines.push(`本次使用了 ${createResult.reference_count} 张参考图。`);
        }
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push("");
        hintLines.push("在后续回答中，请直接输出下面这一行 Markdown 来展示这张图片：");
        hintLines.push("");
        hintLines.push(markdown);
        return {
            file_path: filePath,
            file_uri: fileUri,
            markdown,
            prompt,
            model: createResult.effective_model,
            task_id: createResult.task_id || null,
            task_status: pollResult.task_status,
            image_url: pollResult.image_url,
            reference_count: createResult.reference_count,
            hint: hintLines.join("\n")
        };
    }
    async function draw_image_wrapper(params) {
        try {
            const result = await draw_image(params);
            complete({
                success: true,
                message: `图片生成成功，已保存到 ${DRAWS_DIR}，并返回 Markdown 图片提示。`,
                data: result
            });
        }
        catch (error) {
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
exports.draw_image = qwenDraw.draw_image;

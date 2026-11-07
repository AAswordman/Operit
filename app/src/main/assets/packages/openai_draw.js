"use strict";
/* METADATA
{
  "name": "openai_draw",

  "display_name": {
      "zh": "OpenAI 绘图",
      "en": "OpenAI Draw"
  },
  "description": {
    "zh": "使用 OpenAI 格式图像接口生成或编辑图片，支持 GPT Image 2.6 GIF 动图。支持公网 URL 和本地参考图，保存原始图片或动图到 /sdcard/Download/Operit/plugins/draw/openai_draw/draws/，并返回 Markdown 图片提示。",
    "en": "Generate or edit images with an OpenAI-compatible API, including animated GIFs with GPT Image 2.6. Accepts public URLs and local reference images. Saves original image or animation bytes to /sdcard/Download/Operit/plugins/draw/openai_draw/draws/ and returns a Markdown image reference."
  },
  "category": "Draw",
  "env": [
    {
      "name": "OPENAI_API_KEY",
      "description": {
        "zh": "OpenAI API Key（必填）",
        "en": "OpenAI API key (required)"
      },
      "required": true
    },
    {
      "name": "OPENAI_API_BASE_URL",
      "description": {
        "zh": "OpenAI API Base URL（可选，不填则默认 https://api.openai.com ）",
        "en": "OpenAI API base URL (optional; defaults to https://api.openai.com)"
      },
      "required": false
    },
    {
      "name": "OPENAI_IMAGE_MODEL",
      "description": {
        "zh": "默认绘图模型（可选；当 draw_image 未传 model 时使用）。GPT Image 2.6：gpt-image-2.6-sunburst / gpt-image-2.6-flare；GIF 需要对应模型的动图访问权限",
        "en": "Default image model (optional; used when draw_image does not pass model). GPT Image 2.6: gpt-image-2.6-sunburst / gpt-image-2.6-flare. GIF output requires animation access for the selected model."
      },
      "required": false
    }
  ],
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "生成或编辑图片。使用 gpt-image-2.6-sunburst / gpt-image-2.6-flare 并设置 output_format=gif 可生成循环动图，提示词中描述动作和循环。image_urls / image_paths 提供参考图；结果保存到本地并返回 Markdown。",
        "en": "Generate or edit images. Use gpt-image-2.6-sunburst / gpt-image-2.6-flare with output_format=gif for animated loops; describe the motion and loop in the prompt. Supply reference images with image_urls / image_paths. Saves locally and returns Markdown."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图或编辑提示词（英文或中文皆可）", "en": "Prompt for image generation or editing (Chinese or English)" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "模型名称（可选；不传则使用环境变量 OPENAI_IMAGE_MODEL，再不行使用默认值）", "en": "Model name (optional; falls back to env OPENAI_IMAGE_MODEL, then default)" }, "type": "string", "required": false },
        { "name": "size", "description": { "zh": "图片或动图单帧尺寸，例如 1024x1024 或 auto。GIF 推荐 auto；自定义 GIF 宽高须为 16 的倍数，且符合模型总像素限制", "en": "Image or GIF frame size, e.g. 1024x1024 or auto. For GIF, auto is recommended; custom dimensions must be multiples of 16 and fit the model pixel budget." }, "type": "string", "required": false },
        { "name": "quality", "description": { "zh": "图片质量（可选；不传则使用接口默认值）。GPT Image 支持 low / medium / high / auto，gpt-image-2.5 系列额外支持 xhigh / max", "en": "Image quality (optional; omitted means the API default). GPT Image supports low / medium / high / auto; the gpt-image-2.5 series also supports xhigh / max" }, "type": "string", "required": false },
        { "name": "background", "description": { "zh": "背景（可选）：auto / opaque / transparent。transparent 仅用于支持透明背景的模型和 png / webp 输出", "en": "Background (optional): auto / opaque / transparent. transparent requires model support and png / webp output." }, "type": "string", "required": false },
        { "name": "output_format", "description": { "zh": "输出格式：png / jpeg / webp / gif；默认 png。GPT Image 2.6 使用 gif 生成动图，保存文件扩展名与格式一致", "en": "Output format: png / jpeg / webp / gif; default png. Use gif with GPT Image 2.6 for animation. The saved extension matches the format." }, "type": "string", "required": false },
        { "name": "frame_count", "description": { "zh": "仅用于 gif：2–16 帧的整数，不传则由接口使用默认 9 帧", "en": "GIF only: integer from 2 to 16; the API defaults to 9 frames when omitted." }, "type": "number", "required": false },
        { "name": "frame_duration_ms", "description": { "zh": "仅用于 gif：每帧毫秒数，整数且至少 20；推荐 20–2000，接口默认 200，编码按 10 ms 取整", "en": "GIF only: integer milliseconds per frame, at least 20; recommended 20–2000. The API defaults to 200 and encodes delays rounded to 10 ms." }, "type": "number", "required": false },
        { "name": "complexity", "description": { "zh": "GPT Image 2.6 规划复杂度：low / medium / high；与图片 quality 独立。不传使用模型默认值", "en": "GPT Image 2.6 planning complexity: low / medium / high, independent of rendering quality. Omit to use the model default." }, "type": "string", "required": false },
        { "name": "image_urls", "description": { "zh": "参考图公网 URL 数组（可选；图生图用）。支持字符串数组、JSON 字符串或逗号分隔字符串", "en": "Public reference image URLs for image-to-image (optional). Accepts a string array, JSON string, or comma-separated string." }, "type": "array", "required": false },
        { "name": "image_paths", "description": { "zh": "参考图本地路径数组（可选；图生图用，直接 multipart 上传，不必先上床）。支持字符串数组、JSON 字符串或逗号分隔字符串", "en": "Local reference image paths for image-to-image (optional; uploaded as multipart, no image host required). Accepts a string array, JSON string, or comma-separated string." }, "type": "array", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false },
        { "name": "api_base_url", "description": { "zh": "OpenAI API Base URL（不传则取环境变量 OPENAI_API_BASE_URL 或默认 https://api.openai.com ）", "en": "OpenAI API base URL (optional; falls back to env OPENAI_API_BASE_URL or https://api.openai.com)" }, "type": "string", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const openaiDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();
    const DEFAULT_API_BASE_URL = "https://api.openai.com";
    const DEFAULT_MODEL = "gpt-image-1";
    const DRAW_ROOT_DIR = getPluginConfigDir("draw");
    const STORAGE_DIR = `${DRAW_ROOT_DIR}/openai_draw`;
    const DRAWS_DIR = `${STORAGE_DIR}/draws`;
    function getApiKey() {
        const apiKey = getEnv("OPENAI_API_KEY");
        if (!apiKey) {
            throw new Error("OPENAI_API_KEY 未配置，请在环境变量中设置 OpenAI 的 API Key。");
        }
        return apiKey;
    }
    function joinUrl(baseUrl, path) {
        const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
        const normalizedPath = path.startsWith('/') ? path.slice(1) : path;
        return `${normalizedBase}${normalizedPath}`;
    }
    function getApiBaseUrl(customBaseUrl) {
        const fromParam = (customBaseUrl || "").trim();
        if (fromParam)
            return fromParam;
        const fromEnv = (getEnv("OPENAI_API_BASE_URL") || "").trim();
        if (fromEnv)
            return fromEnv;
        return DEFAULT_API_BASE_URL;
    }
    function getImageEndpoint(baseUrl, kind = "generations") {
        const trimmed = baseUrl.trim();
        const path = kind === "edits" ? "images/edits" : "images/generations";
        if (!trimmed)
            return joinUrl(DEFAULT_API_BASE_URL, `v1/${path}`);
        if (trimmed.includes(`/v1/${path}`))
            return trimmed;
        if (trimmed.includes("/v1/images/generations") && kind === "edits") {
            return trimmed.replace("/v1/images/generations", "/v1/images/edits");
        }
        if (trimmed.includes("/v1/images/edits") && kind === "generations") {
            return trimmed.replace("/v1/images/edits", "/v1/images/generations");
        }
        if (trimmed.endsWith("/v1"))
            return joinUrl(trimmed, path);
        if (trimmed.endsWith("/v1/"))
            return joinUrl(trimmed, path);
        return joinUrl(trimmed, `v1/${path}`);
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
        return "application/octet-stream";
    }
    function fileNameFromPath(filePath, fallbackIndex) {
        const normalized = String(filePath || "").replace(/\\/g, "/");
        const name = normalized.split("/").pop() || "";
        return name || `reference_${fallbackIndex}.png`;
    }
    function sanitizeFileName(name) {
        const safe = name.replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) {
            return `openai_draw_${Date.now()}`;
        }
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
                console.warn(`创建目录异常: ${dir} -> ${e?.message || e}`);
            }
        }
    }
    function normalizeBase64(base64) {
        const raw = base64.trim();
        if (!raw)
            return raw;
        // Some providers may return: data:image/png;base64,xxxx
        const prefixIndex = raw.indexOf("base64,");
        if (raw.startsWith("data:") && prefixIndex >= 0) {
            return raw.substring(prefixIndex + "base64,".length).trim();
        }
        return raw;
    }
    function putOptionalField(target, key, value) {
        const trimmed = (value ?? "").trim();
        if (trimmed) {
            target[key] = trimmed;
        }
    }
    function validateOutputOptions(params, size, background) {
        const format = params.output_format ?? "png";
        if (!["png", "jpeg", "webp", "gif"].includes(format)) {
            throw new Error("output_format 必须是 png、jpeg、webp 或 gif。");
        }
        if (params.complexity !== undefined && !["low", "medium", "high"].includes(params.complexity)) {
            throw new Error("complexity 必须是 low、medium 或 high。");
        }
        if (format !== "gif" && (params.frame_count !== undefined || params.frame_duration_ms !== undefined)) {
            throw new Error("frame_count 和 frame_duration_ms 只能用于 output_format=gif。");
        }
        if (params.frame_count !== undefined &&
            (!Number.isInteger(params.frame_count) || params.frame_count < 2 || params.frame_count > 16)) {
            throw new Error("frame_count 必须是 2–16 的整数。");
        }
        if (params.frame_duration_ms !== undefined &&
            (!Number.isSafeInteger(params.frame_duration_ms) || params.frame_duration_ms < 20)) {
            throw new Error("frame_duration_ms 必须是至少 20 的整数毫秒数。");
        }
        if (background?.trim() === "transparent" && format !== "png" && format !== "webp") {
            throw new Error("transparent 背景需要 png 或 webp 输出。");
        }
        const requestedSize = size?.trim();
        if (format === "gif" && requestedSize && requestedSize !== "auto") {
            const dimensions = /^(\d+)x(\d+)$/.exec(requestedSize);
            if (!dimensions || dimensions.slice(1).some(value => {
                const edge = Number(value);
                return !Number.isSafeInteger(edge) || edge <= 0 || edge % 16 !== 0;
            })) {
                throw new Error("GIF size 必须是 auto 或宽高均为正数且为 16 倍数的 WIDTHxHEIGHT。");
            }
        }
        return format;
    }
    async function parseOpenAIImageResponse(content, effectiveModel, referenceCount, outputFormat) {
        let parsed;
        try {
            parsed = JSON.parse(content);
        }
        catch (error) {
            console.error("解析 OpenAI 图片响应失败:", error);
            throw new Error("解析 OpenAI 响应失败，请检查接口返回格式。");
        }
        const item = parsed?.data?.[0];
        if (!item) {
            throw new Error("OpenAI 响应中未找到 data[0]，请检查接口返回格式。");
        }
        if (parsed.output_format !== undefined && parsed.output_format !== outputFormat) {
            throw new Error(`接口返回格式 ${parsed.output_format} 与请求的 ${outputFormat} 不一致。`);
        }
        if (typeof item.b64_json === "string" && item.b64_json.trim().length > 0) {
            return {
                b64_json: item.b64_json,
                revised_prompt: item.revised_prompt,
                effective_model: effectiveModel,
                reference_count: referenceCount,
                output_format: outputFormat
            };
        }
        if (typeof item.url === "string" && item.url.trim().length > 0) {
            const tmpName = `openai_tmp_${Date.now()}`;
            const tmpPath = `${DRAWS_DIR}/${tmpName}.${outputFormat}`;
            const downloadResult = await Tools.Files.download(item.url, tmpPath);
            if (!downloadResult.successful) {
                throw new Error(`下载图片失败: ${downloadResult.details}`);
            }
            const readBinary = await Tools.Files.readBinary(tmpPath);
            const contentBase64 = readBinary.contentBase64;
            if (contentBase64.trim().length === 0) {
                throw new Error("读取下载图片失败: contentBase64 为空");
            }
            try {
                await Tools.Files.deleteFile(tmpPath);
            }
            catch (error) {
                console.error(`清理 OpenAI 下载图片临时文件失败: ${tmpPath}`, error);
            }
            return {
                b64_json: contentBase64,
                revised_prompt: item.revised_prompt,
                effective_model: effectiveModel,
                reference_count: referenceCount,
                output_format: outputFormat
            };
        }
        throw new Error("OpenAI 响应中未找到 b64_json 或 url，请检查模型/参数以及接口兼容性。");
    }
    async function callOpenAIImageApi(params) {
        const outputFormat = validateOutputOptions(params, params.size, params.background);
        const apiKey = getApiKey();
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const imageUrls = parseStringList(params.image_urls, "image_urls");
        const imagePaths = parseStringList(params.image_paths, "image_paths");
        const modelFromParam = (params.model || "").trim();
        const modelFromEnv = (getEnv("OPENAI_IMAGE_MODEL") || "").trim();
        const effectiveModel = modelFromParam || modelFromEnv || DEFAULT_MODEL;
        const referenceCount = imageUrls.length + imagePaths.length;
        const imageFields = {};
        putOptionalField(imageFields, "size", params.size);
        putOptionalField(imageFields, "quality", params.quality);
        putOptionalField(imageFields, "background", params.background);
        putOptionalField(imageFields, "output_format", params.output_format);
        putOptionalField(imageFields, "complexity", params.complexity);
        for (const url of imageUrls) {
            if (!isProbablyUrl(url)) {
                throw new Error(`image_urls 中包含无效链接: ${url}`);
            }
        }
        for (const filePath of imagePaths) {
            const existsResult = await Tools.Files.exists(filePath);
            if (!existsResult.exists) {
                throw new Error(`参考图文件不存在: ${filePath}`);
            }
        }
        if (referenceCount > 0) {
            const endpoint = getImageEndpoint(apiBaseUrl, "edits");
            const headers = {
                "accept": "application/json",
                "Authorization": `Bearer ${apiKey}`
            };
            const tempFiles = [];
            try {
                const files = [];
                // OpenAI 图片编辑接口只接受 multipart 文件；远程参考图也必须先下载再作为 image[] 上传。
                for (let index = 0; index < imageUrls.length; index += 1) {
                    const tempPath = `${DRAWS_DIR}/openai_ref_${Date.now()}_${index}.png`;
                    const downloadResult = await Tools.Files.download(imageUrls[index], tempPath);
                    if (!downloadResult.successful) {
                        throw new Error(`下载参考图失败: ${downloadResult.details}`);
                    }
                    tempFiles.push(tempPath);
                    files.push({
                        field_name: "image[]",
                        file_path: tempPath,
                        content_type: "image/png",
                        file_name: `reference_url_${index + 1}.png`
                    });
                }
                imagePaths.forEach((filePath, index) => {
                    files.push({
                        field_name: "image[]",
                        file_path: filePath,
                        content_type: guessMimeTypeFromPath(filePath),
                        file_name: fileNameFromPath(filePath, index + 1)
                    });
                });
                const formData = {
                    model: effectiveModel,
                    prompt: params.prompt,
                    ...imageFields
                };
                // multipart 字段必须是文本；JSON 生成请求中的帧参数则保持数字类型。
                if (params.frame_count !== undefined) formData.frame_count = params.frame_count.toString();
                if (params.frame_duration_ms !== undefined) formData.frame_duration_ms = params.frame_duration_ms.toString();
                const response = await Tools.Net.uploadFile({
                    url: endpoint,
                    method: "POST",
                    headers,
                    form_data: formData,
                    files
                });
                if (response.statusCode < 200 || response.statusCode >= 300) {
                    throw new Error(`OpenAI 图片编辑 API 调用失败: ${response.statusCode} - ${response.content}`);
                }
                return parseOpenAIImageResponse(response.content, effectiveModel, referenceCount, outputFormat);
            }
            finally {
                for (const tempPath of tempFiles) {
                    try {
                        await Tools.Files.deleteFile(tempPath);
                    }
                    catch (error) {
                        console.error(`清理 OpenAI 临时参考图失败: ${tempPath}`, error);
                    }
                }
            }
        }
        const endpoint = getImageEndpoint(apiBaseUrl, "generations");
        // 不发送 response_format：GPT Image 系列会以 400 Unknown parameter 拒绝该参数，
        // 且固定返回 b64_json；返回 url 的模型由 parseOpenAIImageResponse 下载保存。
        const body = {
            model: effectiveModel,
            prompt: params.prompt,
            ...imageFields,
            frame_count: params.frame_count,
            frame_duration_ms: params.frame_duration_ms
        };
        const request = client
            .newRequest()
            .url(endpoint)
            .method("POST")
            .headers({
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`
        })
            .body(JSON.stringify(body), "json");
        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`OpenAI 图片 API 调用失败: ${response.statusCode} - ${response.content}`);
        }
        return parseOpenAIImageResponse(response.content, effectiveModel, 0, outputFormat);
    }
    async function draw_image(params) {
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }
        const prompt = params.prompt.trim();
        await ensureDirectories();
        const apiResult = await callOpenAIImageApi({
            prompt,
            model: params.model,
            size: params.size,
            quality: params.quality,
            background: params.background,
            output_format: params.output_format,
            frame_count: params.frame_count,
            frame_duration_ms: params.frame_duration_ms,
            complexity: params.complexity,
            api_base_url: params.api_base_url,
            image_urls: params.image_urls,
            image_paths: params.image_paths
        });
        const baseName = buildFileName(prompt, params.file_name);
        const filePath = `${DRAWS_DIR}/${baseName}.${apiResult.output_format}`;
        const imageBase64 = normalizeBase64(apiResult.b64_json);
        // 保留 GIF 原始帧；拒绝把代理返回的静态图片以 .gif 命名并宣称动图成功。
        if (apiResult.output_format === "gif" &&
            !imageBase64.startsWith("R0lGODdh") && !imageBase64.startsWith("R0lGODlh")) {
            throw new Error("请求了 GIF 动图，但接口返回的数据不是 GIF 文件。");
        }
        const writeResult = await Tools.Files.writeBinary(filePath, imageBase64);
        if (!writeResult.successful) {
            throw new Error(`保存图片失败: ${writeResult.details}`);
        }
        const fileUri = `file://${filePath}`;
        const mediaLabel = apiResult.output_format === "gif" ? "动图" : "图片";
        const markdown = `![AI生成的${mediaLabel}](${fileUri})`;
        const hintLines = [];
        hintLines.push(`${mediaLabel}已生成并保存在本地 ${DRAWS_DIR}。`);
        if (apiResult.reference_count > 0) {
            hintLines.push(`本次使用了 ${apiResult.reference_count} 张参考图。`);
        }
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push("");
        hintLines.push(`在后续回答中，请直接输出下面这一行 Markdown 来展示这张${mediaLabel}：`);
        hintLines.push("");
        hintLines.push(markdown);
        return {
            file_path: filePath,
            file_uri: fileUri,
            markdown,
            prompt,
            revised_prompt: apiResult.revised_prompt || null,
            model: apiResult.effective_model,
            reference_count: apiResult.reference_count,
            output_format: apiResult.output_format,
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
                message: `图片生成失败: ${error?.message || error}`,
                error_stack: error?.stack
            });
        }
    }
    return {
        draw_image: draw_image_wrapper
    };
})();
exports.draw_image = openaiDraw.draw_image;

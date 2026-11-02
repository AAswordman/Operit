"use strict";
/* METADATA
{
  "name": "openai_draw",

  "display_name": {
      "zh": "OpenAI 绘图",
      "en": "OpenAI Draw"
  },
  "description": {
    "zh": "使用 OpenAI 格式图像接口画图：无参考图走 /v1/images/generations，有参考图走 /v1/images/edits。支持公网 URL 和本地图片路径（本地图直接 multipart 上传，不必先上床）。结果保存到 /sdcard/Download/Operit/plugins/draw/openai_draw/draws/，并返回 Markdown 图片提示。",
    "en": "Generate images with an OpenAI-compatible API. Text-to-image uses /v1/images/generations; image-to-image uses /v1/images/edits. Accepts public URLs and local file paths (local files are uploaded as multipart, no image host required). Saves to /sdcard/Download/Operit/plugins/draw/openai_draw/draws/ and returns a Markdown image reference."
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
        "zh": "默认绘图模型（可选；当 draw_image 未传 model 时使用）",
        "en": "Default image model (optional; used when draw_image doesn't pass model)"
      },
      "required": false
    }
  ],
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "根据提示词调用 OpenAI 格式图像接口生成或编辑图片。传入 image_urls 或 image_paths 时走图生图。本地图片直接上传，不必先上床。保存到本地并返回 Markdown 图片提示。",
        "en": "Generate or edit an image via an OpenAI-compatible endpoint. Pass image_urls or image_paths for image-to-image. Local files are uploaded directly. Saves locally and returns a Markdown image reference."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图或编辑提示词（英文或中文皆可）", "en": "Prompt for image generation or editing (Chinese or English)" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "模型名称（可选；不传则使用环境变量 OPENAI_IMAGE_MODEL，再不行使用默认值）", "en": "Model name (optional; falls back to env OPENAI_IMAGE_MODEL, then default)" }, "type": "string", "required": false },
        { "name": "size", "description": { "zh": "图片尺寸，例如 '1024x1024'，可选", "en": "Image size, e.g. '1024x1024' (optional)" }, "type": "string", "required": false },
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
        const raw = String(base64 || "").trim();
        if (!raw)
            return raw;
        // Some providers may return: data:image/png;base64,xxxx
        const prefixIndex = raw.indexOf("base64,");
        if (raw.startsWith("data:") && prefixIndex >= 0) {
            return raw.substring(prefixIndex + "base64,".length).trim();
        }
        return raw;
    }
    async function parseOpenAIImageResponse(content, effectiveModel, referenceCount) {
        let parsed;
        try {
            parsed = JSON.parse(content);
        }
        catch (e) {
            throw new Error(`解析 OpenAI 响应失败: ${e?.message || e}`);
        }
        const item = parsed?.data?.[0];
        if (!item) {
            throw new Error("OpenAI 响应中未找到 data[0]，请检查接口返回格式。");
        }
        if (item.b64_json && String(item.b64_json).trim().length > 0) {
            return {
                b64_json: String(item.b64_json),
                revised_prompt: item.revised_prompt ? String(item.revised_prompt) : undefined,
                effective_model: effectiveModel,
                reference_count: referenceCount
            };
        }
        if (item.url && String(item.url).trim().length > 0) {
            const tmpName = `openai_tmp_${Date.now()}`;
            const tmpPath = `${DRAWS_DIR}/${tmpName}.png`;
            const downloadResult = await Tools.Files.download(String(item.url), tmpPath);
            if (!downloadResult.successful) {
                throw new Error(`下载图片失败: ${downloadResult.details}`);
            }
            const readBinary = await Tools.Files.readBinary(tmpPath);
            const contentBase64 = readBinary?.contentBase64;
            if (!contentBase64 || String(contentBase64).trim().length === 0) {
                throw new Error("读取下载图片失败: contentBase64 为空");
            }
            try {
                await Tools.Files.deleteFile(tmpPath);
            }
            catch {
                // ignore
            }
            return {
                b64_json: String(contentBase64),
                revised_prompt: item.revised_prompt ? String(item.revised_prompt) : undefined,
                effective_model: effectiveModel,
                reference_count: referenceCount
            };
        }
        throw new Error("OpenAI 响应中未找到 b64_json 或 url，请检查模型/参数以及接口兼容性。");
    }
    async function callOpenAIImageApi(params) {
        const apiKey = getApiKey();
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const imageUrls = parseStringList(params.image_urls, "image_urls");
        const imagePaths = parseStringList(params.image_paths, "image_paths");
        const modelFromParam = (params.model || "").trim();
        const modelFromEnv = (getEnv("OPENAI_IMAGE_MODEL") || "").trim();
        const effectiveModel = modelFromParam || modelFromEnv || DEFAULT_MODEL;
        const referenceCount = imageUrls.length + imagePaths.length;
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
                    prompt: params.prompt
                };
                if (params.size && params.size.trim().length > 0) {
                    formData.size = params.size.trim();
                }
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
                return parseOpenAIImageResponse(response.content, effectiveModel, referenceCount);
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
        const body = {
            model: effectiveModel,
            prompt: params.prompt,
            response_format: "b64_json"
        };
        if (params.size && params.size.trim().length > 0) {
            body.size = params.size.trim();
        }
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
        return parseOpenAIImageResponse(response.content, effectiveModel, 0);
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
            api_base_url: params.api_base_url,
            image_urls: params.image_urls,
            image_paths: params.image_paths
        });
        const baseName = buildFileName(prompt, params.file_name);
        const filePath = `${DRAWS_DIR}/${baseName}.png`;
        const writeResult = await Tools.Files.writeBinary(filePath, normalizeBase64(apiResult.b64_json));
        if (!writeResult.successful) {
            throw new Error(`保存图片失败: ${writeResult.details}`);
        }
        const fileUri = `file://${filePath}`;
        const markdown = `![AI生成的图片](${fileUri})`;
        const hintLines = [];
        hintLines.push(`图片已生成并保存在本地 ${DRAWS_DIR}。`);
        if (apiResult.reference_count > 0) {
            hintLines.push(`本次使用了 ${apiResult.reference_count} 张参考图。`);
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
            revised_prompt: apiResult.revised_prompt || null,
            model: apiResult.effective_model,
            reference_count: apiResult.reference_count,
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

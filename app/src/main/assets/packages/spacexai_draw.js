"use strict";
/* METADATA
{
  "name": "spacexai_draw",
  "display_name": {
    "zh": "SpaceXAI 图片与视频",
    "en": "SpaceXAI Images and Video"
  },
  "description": {
    "zh": "使用 SpaceXAI 接口生成图片和视频，并保存到本地。图片支持文生图和图生图：可传公网参考图 URL，或本地图片路径（本地图会转成 data URI，不必先上传图床）。",
    "en": "Generate images and videos with the SpaceXAI APIs and save them locally. Images support text-to-image and image-to-image via public reference URLs or local file paths (local files are sent as data URIs, no image host required)."
  },
  "env": [
    {
      "name": "XAI_API_KEY",
      "description": {
        "zh": "SpaceXAI API Key（必填）",
        "en": "SpaceXAI API key (required)"
      },
      "required": true
    },
    {
      "name": "XAI_API_BASE_URL",
      "description": {
        "zh": "SpaceXAI API 基地址（可选；默认 https://api.x.ai/v1）",
        "en": "SpaceXAI API base URL (optional; default https://api.x.ai/v1)"
      },
      "required": false
    },
    {
      "name": "XAI_IMAGE_MODEL",
      "description": {
        "zh": "默认图片模型（可选；未传 model 时使用，默认 grok-imagine-image-2.0）",
        "en": "Default image model (optional; used when model is omitted, default grok-imagine-image-2.0)"
      },
      "required": false
    },
    {
      "name": "XAI_VIDEO_MODEL",
      "description": {
        "zh": "默认视频模型（可选；未传 model 时使用，默认 grok-imagine-video）",
        "en": "Default video model (optional; used when model is omitted, default grok-imagine-video)"
      },
      "required": false
    }
  ],
  "category": "Draw",
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "根据提示词调用 SpaceXAI 图像 API 生成或编辑图片。无参考图走文生图；传入 image_urls 或 image_paths 时走图生图（最多 5 张）。本地图片会转成 data URI。保存到本地并返回 Markdown 图片提示。",
        "en": "Generate or edit an image with the SpaceXAI image API. Text-to-image when no reference is given; image-to-image when image_urls or image_paths are provided (up to 5). Local files are sent as data URIs. Saves locally and returns a Markdown image reference."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图或编辑提示词（支持多行，英文或中文皆可）", "en": "Multiline prompt for image generation or editing (Chinese or English)" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "SpaceXAI 图像模型名称；不传则优先取 XAI_IMAGE_MODEL，再用默认值 grok-imagine-image-2.0", "en": "SpaceXAI image model name; falls back to XAI_IMAGE_MODEL, then grok-imagine-image-2.0" }, "type": "string", "required": false },
        { "name": "aspect_ratio", "description": { "zh": "画布宽高比，例如 9:16 或 2:3；只控制画面形状，不控制清晰度（可选）", "en": "Canvas aspect ratio, e.g. 9:16 or 2:3; controls shape, not detail (optional)" }, "type": "string", "required": false },
        { "name": "resolution", "description": { "zh": "输出分辨率档位，可选 1k、2k 或 4k；控制像素与细节量，不改变宽高比（可选）", "en": "Output resolution tier: 1k, 2k, or 4k; controls pixel detail without changing aspect ratio (optional)" }, "type": "string", "required": false },
        { "name": "quality", "description": { "zh": "生成质量档位，可选 low、medium 或 auto；影响质量、耗时与成本，不改变宽高比或分辨率（可选）", "en": "Generation quality: low, medium, or auto; affects quality, latency, and cost, not dimensions (optional)" }, "type": "string", "required": false },
        { "name": "image_urls", "description": { "zh": "参考图公网 URL 数组（可选；图生图用）。支持字符串数组、JSON 字符串或逗号分隔字符串，最多 5 张", "en": "Public reference image URLs for image-to-image (optional). Accepts a string array, JSON string, or comma-separated string. Up to 5 images." }, "type": "array", "required": false },
        { "name": "image_paths", "description": { "zh": "参考图本地路径数组（可选；图生图用，会转成 data URI，不必先上传图床）。支持字符串数组、JSON 字符串或逗号分隔字符串，最多 5 张", "en": "Local reference image paths for image-to-image (optional; converted to data URIs, no image host required). Accepts a string array, JSON string, or comma-separated string. Up to 5 images." }, "type": "array", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false }
      ]
    },
    {
      "name": "draw_video",
      "description": {
        "zh": "根据提示词调用 SpaceXAI 视频生成 API 生成视频，支持文生视频、图生视频和视频编辑，轮询完成后下载到本地并返回本地视频链接提示。",
        "en": "Generate a video with the SpaceXAI video API. Supports text-to-video, image-to-video, and video editing. Polls until completion, downloads locally, and returns local video link hints."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "视频提示词（支持多行）", "en": "Multiline video prompt" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "视频模型；不传则优先取 XAI_VIDEO_MODEL，再用默认值 grok-imagine-video", "en": "Video model; falls back to XAI_VIDEO_MODEL, then grok-imagine-video" }, "type": "string", "required": false },
        { "name": "aspect_ratio", "description": { "zh": "输出比例，可选 1:1、16:9、9:16、4:3、3:4、3:2、2:3；默认 16:9；视频编辑模式不支持", "en": "Output aspect ratio. Supported: 1:1, 16:9, 9:16, 4:3, 3:4, 3:2, 2:3. Defaults to 16:9; not supported for video editing." }, "type": "string", "required": false },
        { "name": "resolution", "description": { "zh": "输出分辨率，仅支持 480p 或 720p；默认 480p；视频编辑模式不支持", "en": "Output resolution, only 480p or 720p. Defaults to 480p; not supported for video editing." }, "type": "string", "required": false },
        { "name": "duration", "description": { "zh": "输出时长，支持 1-15 秒；默认 5；视频编辑模式不支持", "en": "Output duration from 1 to 15 seconds. Defaults to 5; not supported for video editing." }, "type": "number", "required": false },
        { "name": "image_url", "description": { "zh": "图生视频输入图 URL（可选）", "en": "Input image URL for image-to-video (optional)" }, "type": "string", "required": false },
        { "name": "image_path", "description": { "zh": "图生视频输入图本地路径（可选，会转成 data URL）", "en": "Local input image path for image-to-video (optional; converted to a data URL)" }, "type": "string", "required": false },
        { "name": "video_url", "description": { "zh": "视频编辑输入视频 URL（可选）", "en": "Input video URL for video editing (optional)" }, "type": "string", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false },
        { "name": "poll_interval_ms", "description": { "zh": "轮询间隔毫秒数，默认 5000", "en": "Polling interval in milliseconds, default 5000" }, "type": "number", "required": false },
        { "name": "max_wait_time_ms", "description": { "zh": "最大等待毫秒数，默认 600000", "en": "Maximum wait time in milliseconds, default 600000" }, "type": "number", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const spacexaiDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();
    const DEFAULT_IMAGE_MODEL = "grok-imagine-image-2.0";
    const DEFAULT_VIDEO_MODEL = "grok-imagine-video";
    const DEFAULT_POLL_INTERVAL_MS = 5000;
    const DEFAULT_MAX_WAIT_TIME_MS = 600000;
    const DEFAULT_VIDEO_ASPECT_RATIO = "16:9";
    const DEFAULT_VIDEO_RESOLUTION = "480p";
    const DEFAULT_VIDEO_DURATION = 5;
    const IMAGE_RESOLUTIONS = ["1k", "2k", "4k"];
    const VIDEO_ASPECT_RATIOS = ["1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"];
    const VIDEO_RESOLUTIONS = ["480p", "720p"];
    const MIN_VIDEO_DURATION = 1;
    const MAX_VIDEO_DURATION = 15;
    const DEFAULT_BASE_URL = "https://api.x.ai/v1";
    const DRAW_ROOT_DIR = getPluginConfigDir("draw");
    const STORAGE_DIR = `${DRAW_ROOT_DIR}/spacexai_draw`;
    const DRAWS_DIR = `${STORAGE_DIR}/draws`;
    const VIDEOS_DIR = `${STORAGE_DIR}/videos`;
    const MAX_IMAGE_REFERENCES = 5;
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
    function isRecord(value) {
        return typeof value === "object" && value !== null && !Array.isArray(value);
    }
    function getApiKey() {
        const apiKey = getEnv("XAI_API_KEY");
        if (!apiKey) {
            throw new Error("XAI_API_KEY 未配置，请在环境变量中设置 SpaceXAI 的 API Key。");
        }
        return apiKey;
    }
    function getBaseUrl() {
        const base = String(getEnv("XAI_API_BASE_URL") || getEnv("XAI_BASE_URL") || "").trim();
        return (base || DEFAULT_BASE_URL).replace(/\/+$/, "");
    }
    function getImageApiEndpoint() {
        return `${getBaseUrl()}/images/generations`;
    }
    function getImageEditApiEndpoint() {
        return `${getBaseUrl()}/images/edits`;
    }
    function getVideoGenerationEndpoint() {
        return `${getBaseUrl()}/videos/generations`;
    }
    function getVideoQueryEndpoint(requestId) {
        return `${getBaseUrl()}/videos/${encodeURIComponent(requestId)}`;
    }
    function getDefaultImageModel() {
        const fromEnv = String(getEnv("XAI_IMAGE_MODEL") || "").trim();
        return fromEnv || DEFAULT_IMAGE_MODEL;
    }
    function getDefaultVideoModel() {
        const fromEnv = String(getEnv("XAI_VIDEO_MODEL") || "").trim();
        return fromEnv || DEFAULT_VIDEO_MODEL;
    }
    function sanitizeFileName(name, fallbackPrefix) {
        const safe = String(name || "").replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) {
            return `${fallbackPrefix}_${Date.now()}`;
        }
        return safe.substring(0, 80);
    }
    function buildFileName(prompt, customName, fallbackPrefix) {
        if (customName && customName.trim().length > 0) {
            return sanitizeFileName(customName, fallbackPrefix);
        }
        const shortPrompt = prompt.length > 40 ? `${prompt.substring(0, 40)}...` : prompt;
        const base = sanitizeFileName(shortPrompt || fallbackPrefix, fallbackPrefix);
        return `${base}_${Date.now()}`;
    }
    function guessExtensionFromUrl(url, fallback) {
        const match = String(url || "").match(/\.(png|jpg|jpeg|webp|gif|mp4|mov|webm|mkv)(?:\?|#|$)/i);
        if (match && match[1]) {
            return match[1].toLowerCase();
        }
        return fallback;
    }
    function guessMimeTypeFromPath(path) {
        const normalized = String(path || "").trim().toLowerCase();
        if (normalized.endsWith(".png"))
            return "image/png";
        if (normalized.endsWith(".webp"))
            return "image/webp";
        if (normalized.endsWith(".gif"))
            return "image/gif";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg"))
            return "image/jpeg";
        return "application/octet-stream";
    }
    function isProbablyUrl(value) {
        return /^https?:\/\//i.test(String(value || "").trim());
    }
    function normalizePositiveInteger(value, fallback) {
        if (value === undefined || value === null || value === "")
            return fallback;
        const parsed = typeof value === "number" ? value : parseInt(String(value), 10);
        if (!Number.isFinite(parsed) || parsed <= 0)
            return fallback;
        return Math.floor(parsed);
    }
    function normalizeImageResolution(value) {
        const raw = String(value || "").trim().toLowerCase();
        if (!raw)
            return "";
        if (!IMAGE_RESOLUTIONS.includes(raw)) {
            throw new Error(`图片 resolution 仅支持 ${IMAGE_RESOLUTIONS.join("、")}。`);
        }
        return raw;
    }
    function normalizeVideoAspectRatio(value) {
        const raw = String(value || "").trim();
        if (!raw)
            return DEFAULT_VIDEO_ASPECT_RATIO;
        if (!VIDEO_ASPECT_RATIOS.includes(raw)) {
            throw new Error(`aspect_ratio 仅支持 ${VIDEO_ASPECT_RATIOS.join(" 或 ")}。`);
        }
        return raw;
    }
    function normalizeVideoResolution(value) {
        const raw = String(value || "").trim().toLowerCase();
        if (!raw)
            return DEFAULT_VIDEO_RESOLUTION;
        if (!VIDEO_RESOLUTIONS.includes(raw)) {
            throw new Error(`resolution 仅支持 ${VIDEO_RESOLUTIONS.join(" 或 ")}。`);
        }
        return raw;
    }
    function normalizeVideoDuration(value) {
        if (value === undefined || value === null || value === "")
            return DEFAULT_VIDEO_DURATION;
        const parsed = typeof value === "number" ? value : parseInt(String(value), 10);
        if (!Number.isFinite(parsed)) {
            throw new Error("duration 必须是数字。");
        }
        const normalized = Math.floor(parsed);
        if (normalized < MIN_VIDEO_DURATION || normalized > MAX_VIDEO_DURATION) {
            throw new Error(`duration 仅支持 ${MIN_VIDEO_DURATION}-${MAX_VIDEO_DURATION} 秒。`);
        }
        return normalized;
    }
    async function ensureDirectories() {
        const dirs = [DRAW_ROOT_DIR, STORAGE_DIR, DRAWS_DIR, VIDEOS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            }
            catch (error) {
                console.warn(`创建目录异常: ${dir} -> ${getErrorMessage(error)}`);
            }
        }
    }
    async function parseJsonResponse(response, label) {
        try {
            const parsed = JSON.parse(response.content);
            if (!isRecord(parsed)) {
                throw new Error("响应不是对象");
            }
            return parsed;
        }
        catch (error) {
            throw new Error(`解析 ${label} 响应失败: ${getErrorMessage(error)}`);
        }
    }
    function extractApiErrorMessage(payload) {
        const directError = payload.error;
        if (typeof directError === "string" && directError.trim()) {
            return directError.trim();
        }
        if (isRecord(directError)) {
            if (typeof directError.message === "string" && directError.message.trim()) {
                return directError.message.trim();
            }
            if (typeof directError.code === "string" && directError.code.trim()) {
                return directError.code.trim();
            }
        }
        return "";
    }
    function parseImageDataUrl(value) {
        const raw = String(value || "").trim();
        if (!raw.toLowerCase().startsWith("data:"))
            return null;
        const commaIndex = raw.indexOf(",");
        if (commaIndex < 0)
            return null;
        const header = raw.substring(5, commaIndex);
        if (!/(?:^|;)base64(?:;|$)/i.test(header))
            return null;
        const base64 = raw.substring(commaIndex + 1).replace(/\s+/g, "");
        if (!base64)
            return null;
        const mimeType = String(header.split(";")[0] || "image/png").trim().toLowerCase();
        return {
            base64,
            mime_type: mimeType || "image/png"
        };
    }
    function isLikelyBase64(value) {
        const normalized = String(value || "").replace(/\s+/g, "");
        return normalized.length >= 16
            && normalized.length % 4 !== 1
            && /^[A-Za-z0-9+/]*={0,2}$/.test(normalized);
    }
    function normalizeImageValue(value, sourceField, allowRawBase64, mimeType = "") {
        if (typeof value !== "string")
            return null;
        const raw = value.trim();
        if (!raw)
            return null;
        const dataUrl = parseImageDataUrl(raw);
        if (dataUrl) {
            return {
                response_format: "b64_json",
                image_url: "",
                b64_json: dataUrl.base64,
                mime_type: dataUrl.mime_type,
                source_field: sourceField
            };
        }
        if (isProbablyUrl(raw)) {
            return {
                response_format: "url",
                image_url: raw,
                b64_json: "",
                mime_type: "",
                source_field: sourceField
            };
        }
        if (allowRawBase64 && isLikelyBase64(raw)) {
            return {
                response_format: "b64_json",
                image_url: "",
                b64_json: raw.replace(/\s+/g, ""),
                mime_type: String(mimeType || "image/png").trim().toLowerCase(),
                source_field: sourceField
            };
        }
        return null;
    }
    function extractImageFromRecord(record, sourcePrefix) {
        const mimeType = typeof record.mime_type === "string"
            ? record.mime_type
            : (typeof record.content_type === "string" ? record.content_type : "");
        for (const field of ["url", "image_url"]) {
            const result = normalizeImageValue(record[field], `${sourcePrefix}.${field}`, false, mimeType);
            if (result)
                return result;
        }
        for (const field of ["b64_json", "base64"]) {
            const result = normalizeImageValue(record[field], `${sourcePrefix}.${field}`, true, mimeType);
            if (result)
                return result;
        }
        return null;
    }
    function extractImagePayload(payload) {
        for (const arrayField of ["data", "images"]) {
            const values = Array.isArray(payload[arrayField]) ? payload[arrayField] : [];
            if (values.length === 0)
                continue;
            const first = values[0];
            const result = isRecord(first)
                ? extractImageFromRecord(first, `${arrayField}[0]`)
                : normalizeImageValue(first, `${arrayField}[0]`, true);
            if (result)
                return result;
        }
        return extractImageFromRecord(payload, "top_level");
    }
    function summarizeObjectKeys(value) {
        if (!isRecord(value))
            return "none";
        const keys = Object.keys(value).sort().slice(0, 20);
        return keys.length > 0 ? keys.join(",") : "empty";
    }
    function summarizeFirstArrayItem(payload, field) {
        const values = Array.isArray(payload[field]) ? payload[field] : [];
        if (values.length === 0)
            return "none";
        const first = values[0];
        if (isRecord(first))
            return `keys=[${summarizeObjectKeys(first)}]`;
        return typeof first;
    }
    function extractEndpointHost(endpoint) {
        const match = String(endpoint || "").match(/^https?:\/\/([^/?#]+)/i);
        return match && match[1] ? match[1].replace(/^.*@/, "") : "unknown";
    }
    function sanitizeDiagnosticText(value) {
        return String(value || "")
            .replace(/bearer\s+[^\s,;]+/ig, "Bearer [redacted]")
            .replace(/(api[_ -]?key|authorization|token)\s*[:=]\s*[^\s,;]+/ig, "$1=[redacted]")
            .substring(0, 240);
    }
    function buildImageResponseDiagnostic(payload, statusCode, endpoint) {
        const apiError = sanitizeDiagnosticText(extractApiErrorMessage(payload));
        const parts = [
            `HTTP ${statusCode}`,
            `endpoint_host=${extractEndpointHost(endpoint)}`,
            `top_level_keys=[${summarizeObjectKeys(payload)}]`,
            `data[0]=${summarizeFirstArrayItem(payload, "data")}`,
            `images[0]=${summarizeFirstArrayItem(payload, "images")}`
        ];
        if (apiError)
            parts.push(`api_error=${apiError}`);
        return parts.join("; ");
    }
    function guessImageExtension(mimeType, base64) {
        const normalizedMime = String(mimeType || "").trim().toLowerCase();
        if (normalizedMime === "image/jpeg" || normalizedMime === "image/jpg")
            return "jpg";
        if (normalizedMime === "image/webp")
            return "webp";
        if (normalizedMime === "image/gif")
            return "gif";
        if (normalizedMime === "image/png")
            return "png";
        const prefix = String(base64 || "").substring(0, 16);
        if (prefix.startsWith("/9j/"))
            return "jpg";
        if (prefix.startsWith("UklGR"))
            return "webp";
        if (prefix.startsWith("R0lGOD"))
            return "gif";
        return "png";
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
    function buildImageReference(url) {
        return {
            url,
            type: "image_url"
        };
    }
    async function readLocalImageAsDataUrl(filePath) {
        const trimmedPath = String(filePath || "").trim();
        if (!trimmedPath) {
            throw new Error("image_path 不能为空。");
        }
        const existsResult = await Tools.Files.exists(trimmedPath);
        if (!existsResult.exists) {
            throw new Error(`本地图片不存在: ${trimmedPath}`);
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
    async function resolveVideoInputs(imageUrl, imagePath, videoUrl) {
        const trimmedImageUrl = String(imageUrl || "").trim();
        const trimmedImagePath = String(imagePath || "").trim();
        const trimmedVideoUrl = String(videoUrl || "").trim();
        const imageInputCount = (trimmedImageUrl ? 1 : 0) + (trimmedImagePath ? 1 : 0);
        if (imageInputCount > 1) {
            throw new Error("image_url 和 image_path 只能二选一，请不要同时传。");
        }
        if (trimmedVideoUrl && imageInputCount > 0) {
            throw new Error("视频生成一次只能使用一种输入源：纯文本、图片或视频。请不要同时传 image_* 和 video_url。");
        }
        if (trimmedImageUrl) {
            if (!isProbablyUrl(trimmedImageUrl)) {
                throw new Error("image_url 必须是 http 或 https 链接。");
            }
            return {
                image: trimmedImageUrl,
                input_type: "image_url"
            };
        }
        if (trimmedImagePath) {
            return {
                image: await readLocalImageAsDataUrl(trimmedImagePath),
                input_type: "image_path"
            };
        }
        if (trimmedVideoUrl) {
            if (!isProbablyUrl(trimmedVideoUrl)) {
                throw new Error("video_url 必须是 http 或 https 链接。");
            }
            return {
                video_url: trimmedVideoUrl,
                input_type: "video_url"
            };
        }
        return {
            input_type: "text"
        };
    }
    async function callXaiImageApi(params) {
        const apiKey = getApiKey();
        const modelFromParam = String(params.model || "").trim();
        const effectiveModel = modelFromParam || getDefaultImageModel();
        const references = await resolveImageReferences(params.image_urls, params.image_paths);
        const body = {
            model: effectiveModel,
            prompt: params.prompt
        };
        for (const field of ["aspect_ratio", "quality"]) {
            const value = String(params[field] || "").trim();
            if (value)
                body[field] = value;
        }
        const resolution = normalizeImageResolution(params.resolution);
        if (resolution)
            body.resolution = resolution;
        if (references.length === 1) {
            body.image = buildImageReference(references[0]);
        }
        else if (references.length > 1) {
            body.image_urls = references;
        }
        const endpoint = references.length > 0 ? getImageEditApiEndpoint() : getImageApiEndpoint();
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
            let failurePayload = {};
            try {
                const parsedFailure = JSON.parse(response.content);
                if (isRecord(parsedFailure))
                    failurePayload = parsedFailure;
            }
            catch (_) {
                // Keep diagnostics structural when an upstream returns non-JSON content.
            }
            const diagnostic = buildImageResponseDiagnostic(failurePayload, response.statusCode, endpoint);
            throw new Error(`xAI 图片 API 调用失败。${diagnostic}`);
        }
        const parsed = await parseJsonResponse(response, "xAI 图片生成");
        const imageResult = extractImagePayload(parsed);
        if (!imageResult) {
            const diagnostic = buildImageResponseDiagnostic(parsed, response.statusCode, endpoint);
            throw new Error(`xAI 响应中未找到图片数据，请检查模型和参数是否正确。${diagnostic}`);
        }
        return {
            ...imageResult,
            effective_model: effectiveModel,
            reference_count: references.length
        };
    }
    async function createVideoTask(params) {
        const apiKey = getApiKey();
        const modelFromParam = String(params.model || "").trim();
        const effectiveModel = modelFromParam || getDefaultVideoModel();
        const body = {
            model: effectiveModel,
            prompt: String(params.prompt || "").trim()
        };
        const isVideoEdit = Boolean(params.video_url);
        if (!isVideoEdit) {
            body.aspect_ratio = normalizeVideoAspectRatio(params.aspect_ratio);
            body.resolution = normalizeVideoResolution(params.resolution);
            body.duration = normalizeVideoDuration(params.duration);
        }
        if (params.image) {
            body.image = {
                url: params.image
            };
        }
        if (params.video_url) {
            body.video_url = params.video_url;
        }
        const request = client
            .newRequest()
            .url(getVideoGenerationEndpoint())
            .method("POST")
            .headers({
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`
        })
            .body(JSON.stringify(body), "json");
        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`xAI 视频创建任务失败: ${response.statusCode} - ${response.content}`);
        }
        const parsed = await parseJsonResponse(response, "xAI 视频创建");
        const requestId = typeof parsed.request_id === "string" ? parsed.request_id.trim() : "";
        const status = typeof parsed.status === "string" ? parsed.status.trim() : "";
        if (!requestId) {
            throw new Error(`xAI 视频创建响应中未找到 request_id: ${response.content}`);
        }
        return {
            request_id: requestId,
            status,
            effective_model: effectiveModel,
            aspect_ratio: typeof body.aspect_ratio === "string" ? body.aspect_ratio : null,
            resolution: typeof body.resolution === "string" ? body.resolution : null,
            duration: typeof body.duration === "number" ? body.duration : null
        };
    }
    async function queryVideoTaskStatus(requestId) {
        const apiKey = getApiKey();
        const endpoint = getVideoQueryEndpoint(requestId);
        const request = client
            .newRequest()
            .url(endpoint)
            .method("GET")
            .headers({
            "accept": "application/json",
            "Authorization": `Bearer ${apiKey}`
        });
        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`xAI 视频查询失败: ${response.statusCode} - ${response.content}`);
        }
        const parsed = await parseJsonResponse(response, "xAI 视频查询");
        const status = typeof parsed.status === "string" ? parsed.status.trim() : "";
        const video = isRecord(parsed.video) ? parsed.video : null;
        const videoUrl = video && typeof video.url === "string" ? video.url.trim() : "";
        const contentType = video && typeof video.content_type === "string" ? video.content_type.trim() : "";
        const expiresAt = video && typeof video.expires_at === "string" ? video.expires_at.trim() : "";
        const duration = video && typeof video.duration === "number" ? video.duration : null;
        const respectModeration = video && typeof video.respect_moderation === "string"
            ? video.respect_moderation.trim()
            : "";
        const errorMessage = extractApiErrorMessage(parsed);
        return {
            status,
            video_url: videoUrl,
            content_type: contentType,
            expires_at: expiresAt,
            duration,
            respect_moderation: respectModeration,
            error_message: errorMessage
        };
    }
    function normalizeVideoTaskStatus(status) {
        const normalized = String(status || "").trim().toLowerCase();
        if (normalized === "done")
            return "completed";
        if (normalized === "expired")
            return "failed";
        return "processing";
    }
    async function draw_image(params) {
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }
        const prompt = params.prompt.trim();
        await ensureDirectories();
        const apiResult = await callXaiImageApi({
            prompt,
            model: params.model,
            aspect_ratio: params.aspect_ratio,
            resolution: params.resolution,
            quality: params.quality,
            image_urls: params.image_urls,
            image_paths: params.image_paths
        });
        const ext = apiResult.response_format === "url"
            ? guessExtensionFromUrl(apiResult.image_url, "png")
            : guessImageExtension(apiResult.mime_type, apiResult.b64_json);
        const baseName = buildFileName(prompt, params.file_name, "spacexai_draw");
        const filePath = `${DRAWS_DIR}/${baseName}.${ext}`;
        if (apiResult.response_format === "url") {
            const downloadResult = await Tools.Files.download(apiResult.image_url, filePath);
            if (!downloadResult.successful) {
                throw new Error(`下载图片失败: ${downloadResult.details}`);
            }
        }
        else {
            const writeResult = await Tools.Files.writeBinary(filePath, apiResult.b64_json);
            if (!writeResult.successful) {
                throw new Error(`保存 base64 图片失败: ${writeResult.details}`);
            }
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
            prompt,
            model: apiResult.effective_model,
            response_format: apiResult.response_format,
            response_source: apiResult.source_field,
            remote_image_url: apiResult.image_url || null,
            reference_count: apiResult.reference_count,
            file_path: filePath,
            file_uri: fileUri,
            markdown,
            hint: hintLines.join("\n")
        };
    }
    async function draw_video(params) {
        const prompt = String(params && params.prompt ? params.prompt : "").trim();
        if (!prompt) {
            throw new Error("prompt 不能为空。");
        }
        await ensureDirectories();
        const resolvedInputs = await resolveVideoInputs(params.image_url, params.image_path, params.video_url);
        const createdTask = await createVideoTask({
            ...params,
            prompt,
            image: resolvedInputs.image,
            video_url: resolvedInputs.video_url
        });
        const pollIntervalMs = normalizePositiveInteger(params.poll_interval_ms, DEFAULT_POLL_INTERVAL_MS);
        const maxWaitTimeMs = normalizePositiveInteger(params.max_wait_time_ms, DEFAULT_MAX_WAIT_TIME_MS);
        const deadline = Date.now() + maxWaitTimeMs;
        let latestStatus = createdTask.status;
        let latestErrorMessage = "";
        let remoteVideoUrl = "";
        let remoteContentType = "";
        let expiresAt = "";
        let remoteDuration = createdTask.duration;
        let remoteRespectModeration = "";
        while (Date.now() <= deadline) {
            const statusResult = await queryVideoTaskStatus(createdTask.request_id);
            latestStatus = statusResult.status;
            latestErrorMessage = statusResult.error_message;
            remoteContentType = statusResult.content_type;
            expiresAt = statusResult.expires_at;
            remoteDuration = statusResult.duration;
            remoteRespectModeration = statusResult.respect_moderation;
            const normalizedStatus = normalizeVideoTaskStatus(statusResult.status);
            if (normalizedStatus === "completed") {
                remoteVideoUrl = statusResult.video_url;
                if (!remoteVideoUrl) {
                    throw new Error("视频任务已完成，但响应中未找到 video.url。");
                }
                break;
            }
            if (normalizedStatus === "failed") {
                throw new Error(`视频生成失败或过期: ${latestErrorMessage || latestStatus || "接口未返回失败原因"}`);
            }
            await Tools.System.sleep(pollIntervalMs);
        }
        if (!remoteVideoUrl) {
            throw new Error(`视频生成超时，最后状态为 ${latestStatus || "未知"}${latestErrorMessage ? `，原因：${latestErrorMessage}` : ""}`);
        }
        const extension = guessExtensionFromUrl(remoteVideoUrl, "mp4");
        const baseName = buildFileName(prompt, params.file_name, "spacexai_video");
        const filePath = `${VIDEOS_DIR}/${baseName}.${extension}`;
        const downloadResult = await Tools.Files.download(remoteVideoUrl, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载视频失败: ${downloadResult.details}`);
        }
        const fileUri = `file://${filePath}`;
        const markdownLink = `[点击查看生成的视频](${fileUri})`;
        const htmlVideo = `<video controls src="${fileUri}"></video>`;
        const hintLines = [];
        hintLines.push(`视频已生成并保存在本地 ${VIDEOS_DIR}。`);
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push("");
        hintLines.push("后续回答如果要给出视频，请优先返回这个本地链接：");
        hintLines.push(markdownLink);
        hintLines.push("");
        hintLines.push("如果当前渲染环境支持 HTML 视频标签，也可以使用：");
        hintLines.push(htmlVideo);
        return {
            prompt,
            model: createdTask.effective_model,
            request_id: createdTask.request_id,
            status: latestStatus,
            input_type: resolvedInputs.input_type,
            aspect_ratio: createdTask.aspect_ratio,
            resolution: createdTask.resolution,
            duration: remoteDuration,
            remote_video_url: remoteVideoUrl,
            remote_content_type: remoteContentType,
            remote_expires_at: expiresAt,
            remote_respect_moderation: remoteRespectModeration,
            file_path: filePath,
            file_uri: fileUri,
            markdown_link: markdownLink,
            html_video: htmlVideo,
            hint: hintLines.join("\n")
        };
    }
    async function draw_image_wrapper(params) {
        try {
            const result = await draw_image(params);
            complete({
                success: true,
                message: "SpaceXAI 图片生成成功，已保存到本地。",
                data: result
            });
        }
        catch (error) {
            console.error("draw_image 执行失败:", error);
            complete({
                success: false,
                message: `SpaceXAI 图片生成失败: ${getErrorMessage(error)}`,
                error_stack: getErrorStack(error)
            });
        }
    }
    async function draw_video_wrapper(params) {
        try {
            const result = await draw_video((params || {}));
            complete({
                success: true,
                message: "SpaceXAI 视频生成成功，已下载到本地。",
                data: result
            });
        }
        catch (error) {
            console.error("draw_video 执行失败:", error);
            complete({
                success: false,
                message: `SpaceXAI 视频生成失败: ${getErrorMessage(error)}`,
                error_stack: getErrorStack(error)
            });
        }
    }
    return {
        draw_image: draw_image_wrapper,
        draw_video: draw_video_wrapper
    };
})();
exports.draw_image = spacexaiDraw.draw_image;
exports.draw_video = spacexaiDraw.draw_video;

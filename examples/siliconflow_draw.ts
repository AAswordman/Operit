/* METADATA
{
  "name": "siliconflow_draw",
  "display_name": {
    "zh": "硅基流动绘图",
    "en": "SiliconFlow Draw"
  },
  "description": {
    "zh": "使用 SiliconFlow 官方图像与视频接口生成图片和视频。图片默认走 /v1/images/generations；传入 image_urls 或 image_paths 时走图生图（本地图转 data URI）。视频走 /v1/video/submit + /v1/video/status。生成结果会立即下载到本地。",
    "en": "Generate images and videos with SiliconFlow official APIs. Images default to /v1/images/generations; image-to-image uses image_urls or local image_paths (local files become data URIs). Videos use /v1/video/submit + /v1/video/status. Generated assets are downloaded locally immediately."
  },
  "category": "Draw",
  "env": [
    {
      "name": "SILICONFLOW_API_KEY",
      "description": {
        "zh": "SiliconFlow API Key（必填）",
        "en": "SiliconFlow API key (required)"
      },
      "required": true
    },
    {
      "name": "SILICONFLOW_API_BASE_URL",
      "description": {
        "zh": "SiliconFlow API Base URL（可选，不填默认 https://api.siliconflow.cn ）",
        "en": "SiliconFlow API base URL (optional; defaults to https://api.siliconflow.cn)"
      },
      "required": false
    },
    {
      "name": "SILICONFLOW_IMAGE_MODEL",
      "description": {
        "zh": "默认生图模型（可选；例如 Kwai-Kolors/Kolors、Qwen/Qwen-Image）",
        "en": "Default image model (optional), e.g. Kwai-Kolors/Kolors or Qwen/Qwen-Image"
      },
      "required": false
    },
    {
      "name": "SILICONFLOW_VIDEO_MODEL",
      "description": {
        "zh": "默认生视频模型（可选；例如 Wan-AI/Wan2.2-T2V-A14B 或 Wan-AI/Wan2.2-I2V-A14B）",
        "en": "Default video model (optional), e.g. Wan-AI/Wan2.2-T2V-A14B or Wan-AI/Wan2.2-I2V-A14B"
      },
      "required": false
    }
  ],
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "调用 SiliconFlow 官方图片接口生图或图生图。传入 image_urls 或 image_paths 时走参考图编辑；本地图转 data URI。下载到本地并返回 Markdown 图片提示。",
        "en": "Generate or edit images with SiliconFlow. Pass image_urls or image_paths for image-to-image; local files are sent as data URIs. Downloads locally and returns Markdown image hints."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "生图或编辑提示词", "en": "Image prompt" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "模型名；不传则优先取 SILICONFLOW_IMAGE_MODEL。有参考图且未传 model 时默认 Qwen/Qwen-Image-Edit，否则 Kwai-Kolors/Kolors", "en": "Model name; falls back to SILICONFLOW_IMAGE_MODEL. With reference images and no model, defaults to Qwen/Qwen-Image-Edit, otherwise Kwai-Kolors/Kolors" }, "type": "string", "required": false },
        { "name": "image_size", "description": { "zh": "图片尺寸，例如 1024x1024；未传时默认 1024x1024。Qwen 图像编辑模型不支持此参数", "en": "Image size, e.g. 1024x1024; defaults to 1024x1024. Qwen image-edit models do not support this parameter" }, "type": "string", "required": false },
        { "name": "negative_prompt", "description": { "zh": "负面提示词（可选）", "en": "Negative prompt (optional)" }, "type": "string", "required": false },
        { "name": "batch_size", "description": { "zh": "一次生成多少张图（可选）", "en": "Number of images to generate (optional)" }, "type": "number", "required": false },
        { "name": "num_inference_steps", "description": { "zh": "推理步数（可选）", "en": "Inference steps (optional)" }, "type": "number", "required": false },
        { "name": "guidance_scale", "description": { "zh": "提示词引导强度（可选）", "en": "Guidance scale (optional)" }, "type": "number", "required": false },
        { "name": "seed", "description": { "zh": "随机种子（可选）", "en": "Seed (optional)" }, "type": "number", "required": false },
        { "name": "image_urls", "description": { "zh": "参考图公网 URL 数组（可选；图生图用）。支持字符串数组、JSON 字符串或逗号分隔字符串", "en": "Public reference image URLs for image-to-image (optional). Accepts a string array, JSON string, or comma-separated string." }, "type": "array", "required": false },
        { "name": "image_paths", "description": { "zh": "参考图本地路径数组（可选；图生图用，会转成 data URI）", "en": "Local reference image paths for image-to-image (optional; converted to data URIs)" }, "type": "array", "required": false },
        { "name": "file_name", "description": { "zh": "本地保存文件名（不含扩展名）", "en": "Local output filename without extension" }, "type": "string", "required": false },
        { "name": "api_base_url", "description": { "zh": "自定义 API Base URL（可选）", "en": "Custom API base URL (optional)" }, "type": "string", "required": false }
      ]
    },
    {
      "name": "draw_video",
      "description": {
        "zh": "调用 SiliconFlow 官方视频生成接口生视频，轮询状态，下载到本地并返回本地视频链接提示。",
        "en": "Generate videos with SiliconFlow official video API, poll status, download locally, and return local video link hints."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "生视频提示词", "en": "Video prompt" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "模型名；不传则优先取 SILICONFLOW_VIDEO_MODEL，若传了 image_url/image_path 默认走 Wan-AI/Wan2.2-I2V-A14B，否则默认 Wan-AI/Wan2.2-T2V-A14B", "en": "Model name; falls back to SILICONFLOW_VIDEO_MODEL. If image_url/image_path is provided, defaults to Wan-AI/Wan2.2-I2V-A14B; otherwise Wan-AI/Wan2.2-T2V-A14B" }, "type": "string", "required": false },
        { "name": "image_size", "description": { "zh": "视频尺寸，可选 1280x720、720x1280、960x960；默认 1280x720", "en": "Video size: 1280x720, 720x1280, or 960x960; defaults to 1280x720" }, "type": "string", "required": false },
        { "name": "negative_prompt", "description": { "zh": "负面提示词（可选）", "en": "Negative prompt (optional)" }, "type": "string", "required": false },
        { "name": "image_url", "description": { "zh": "图生视频输入图 URL（可选）", "en": "Input image URL for image-to-video (optional)" }, "type": "string", "required": false },
        { "name": "image_path", "description": { "zh": "图生视频输入图本地路径（可选，会转成 data URL 上传）", "en": "Local input image path for image-to-video (optional; converted to data URL)" }, "type": "string", "required": false },
        { "name": "seed", "description": { "zh": "随机种子（可选）", "en": "Seed (optional)" }, "type": "number", "required": false },
        { "name": "file_name", "description": { "zh": "本地保存文件名（不含扩展名）", "en": "Local output filename without extension" }, "type": "string", "required": false },
        { "name": "api_base_url", "description": { "zh": "自定义 API Base URL（可选）", "en": "Custom API base URL (optional)" }, "type": "string", "required": false },
        { "name": "poll_interval_ms", "description": { "zh": "轮询间隔毫秒数，默认 5000", "en": "Polling interval in ms, default 5000" }, "type": "number", "required": false },
        { "name": "max_wait_time_ms", "description": { "zh": "最大等待毫秒数，默认 600000", "en": "Maximum wait time in ms, default 600000" }, "type": "number", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />

const siliconflowDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();

    const DEFAULT_API_BASE_URL = "https://api.siliconflow.cn";
    const DEFAULT_IMAGE_MODEL = "Kwai-Kolors/Kolors";
    const DEFAULT_IMAGE_EDIT_MODEL = "Qwen/Qwen-Image-Edit";
    const DEFAULT_IMAGE_SIZE = "1024x1024";
    const DEFAULT_VIDEO_TEXT_MODEL = "Wan-AI/Wan2.2-T2V-A14B";
    const DEFAULT_VIDEO_IMAGE_MODEL = "Wan-AI/Wan2.2-I2V-A14B";
    const DEFAULT_VIDEO_SIZE = "1280x720";
    const DEFAULT_POLL_INTERVAL_MS = 5000;
    const DEFAULT_MAX_WAIT_TIME_MS = 600000;
    const VIDEO_SIZE_OPTIONS = ["1280x720", "720x1280", "960x960"];

    const DRAW_ROOT_DIR = getPluginConfigDir("draw");
    const STORAGE_DIR = `${DRAW_ROOT_DIR}/siliconflow_draw`;
    const DRAWS_DIR = `${STORAGE_DIR}/draws`;
    const VIDEOS_DIR = `${STORAGE_DIR}/videos`;

    type JsonMap = Record<string, any>;

    type ImageParams = {
        prompt: string;
        model?: string;
        image_size?: string;
        negative_prompt?: string;
        batch_size?: number;
        num_inference_steps?: number;
        guidance_scale?: number;
        seed?: number;
        image_urls?: string[] | string;
        image_paths?: string[] | string;
        file_name?: string;
        api_base_url?: string;
    };

    type VideoParams = {
        prompt: string;
        model?: string;
        image_size?: string;
        negative_prompt?: string;
        image_url?: string;
        image_path?: string;
        seed?: number;
        file_name?: string;
        api_base_url?: string;
        poll_interval_ms?: number;
        max_wait_time_ms?: number;
    };

    type ImageApiResult = {
        effective_model: string;
        image_urls: string[];
        seed: any;
        reference_count: number;
    };

    type VideoTaskResult = {
        api_base_url: string;
        effective_model: string;
        request_id: string;
        seed: any;
    };

    type VideoStatusResult = {
        status: string;
        reason: string;
        video_url: string;
        seed: any;
    };

    type ImageFileItem = {
        file_path: string;
        file_uri: string;
        markdown: string;
    };

    function getApiKey() {
        const apiKey = getEnv("SILICONFLOW_API_KEY");
        if (!apiKey) {
            throw new Error("SILICONFLOW_API_KEY 未配置，请先在环境变量中设置硅基流动 API Key。");
        }
        return apiKey;
    }

    function getApiBaseUrl(customBaseUrl) {
        const fromParam = String(customBaseUrl || "").trim();
        if (fromParam) return fromParam;

        const fromEnv = String(getEnv("SILICONFLOW_API_BASE_URL") || "").trim();
        if (fromEnv) return fromEnv;

        return DEFAULT_API_BASE_URL;
    }

    function joinUrl(baseUrl, path) {
        const normalizedBase = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
        const normalizedPath = path.startsWith("/") ? path.slice(1) : path;
        return `${normalizedBase}${normalizedPath}`;
    }

    function getImageEndpoint(baseUrl) {
        const trimmed = String(baseUrl || "").trim();
        if (!trimmed) return joinUrl(DEFAULT_API_BASE_URL, "v1/images/generations");
        if (trimmed.includes("/v1/images/generations")) return trimmed;
        if (trimmed.endsWith("/v1")) return joinUrl(trimmed, "images/generations");
        if (trimmed.endsWith("/v1/")) return joinUrl(trimmed, "images/generations");
        return joinUrl(trimmed, "v1/images/generations");
    }

    function getVideoSubmitEndpoint(baseUrl) {
        const trimmed = String(baseUrl || "").trim();
        if (!trimmed) return joinUrl(DEFAULT_API_BASE_URL, "v1/video/submit");
        if (trimmed.includes("/v1/video/submit")) return trimmed;
        if (trimmed.endsWith("/v1")) return joinUrl(trimmed, "video/submit");
        if (trimmed.endsWith("/v1/")) return joinUrl(trimmed, "video/submit");
        return joinUrl(trimmed, "v1/video/submit");
    }

    function getVideoStatusEndpoint(baseUrl) {
        const trimmed = String(baseUrl || "").trim();
        if (!trimmed) return joinUrl(DEFAULT_API_BASE_URL, "v1/video/status");
        if (trimmed.includes("/v1/video/status")) return trimmed;
        if (trimmed.endsWith("/v1")) return joinUrl(trimmed, "video/status");
        if (trimmed.endsWith("/v1/")) return joinUrl(trimmed, "video/status");
        return joinUrl(trimmed, "v1/video/status");
    }

    function sanitizeFileName(name, prefix) {
        const safe = String(name || "").replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) return `${prefix}_${Date.now()}`;
        return safe.substring(0, 80);
    }

    function buildFileBaseName(prompt, customName, prefix) {
        if (customName && String(customName).trim().length > 0) {
            return sanitizeFileName(customName, prefix);
        }
        const rawPrompt = String(prompt || "").trim();
        const shortPrompt = rawPrompt.length > 40 ? `${rawPrompt.substring(0, 40)}...` : rawPrompt;
        return `${sanitizeFileName(shortPrompt || prefix, prefix)}_${Date.now()}`;
    }

    function getErrorMessage(error) {
        if (error instanceof Error) return error.message;
        return String(error);
    }

    function guessMimeTypeFromPath(filePath) {
        const lower = String(filePath || "").toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }

    function guessExtensionFromUrl(url, fallbackExtension) {
        const match = String(url || "").match(/\.([a-z0-9]{2,5})(?:[?#].*)?$/i);
        if (match && match[1]) return match[1].toLowerCase();
        return fallbackExtension;
    }

    function parsePositiveInteger(value, fieldName) {
        if (value === undefined || value === null || value === "") return undefined;
        const numberValue = Number(value);
        if (!Number.isFinite(numberValue) || numberValue <= 0) {
            throw new Error(`${fieldName} 必须是正整数。`);
        }
        return Math.floor(numberValue);
    }

    function parseFiniteNumber(value, fieldName) {
        if (value === undefined || value === null || value === "") return undefined;
        const numberValue = Number(value);
        if (!Number.isFinite(numberValue)) {
            throw new Error(`${fieldName} 必须是有效数字。`);
        }
        return numberValue;
    }

    function parseSeed(value) {
        if (value === undefined || value === null || value === "") return undefined;
        const numberValue = Number(value);
        if (!Number.isFinite(numberValue)) {
            throw new Error("seed 必须是有效数字。");
        }
        return Math.floor(numberValue);
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
            if (!trimmed) return [];
            try {
                const parsed = JSON.parse(trimmed);
                if (Array.isArray(parsed)) {
                    return parsed.map(item => String(item || "").trim()).filter(item => item.length > 0);
                }
            } catch {
                // ignore and try comma-separated parsing
            }
            return trimmed.split(",").map(item => item.trim()).filter(item => item.length > 0);
        }
        throw new Error(`${fieldName} 必须是字符串数组、JSON 字符串或逗号分隔字符串。`);
    }

    async function resolveImageReferences(imageUrls, imagePaths) {
        const resolvedUrls = parseStringList(imageUrls, "image_urls");
        const resolvedPaths = parseStringList(imagePaths, "image_paths");
        for (const url of resolvedUrls) {
            if (!isProbablyUrl(url) && !String(url).toLowerCase().startsWith("data:image/")) {
                throw new Error(`image_urls 中包含无效链接: ${url}`);
            }
        }
        for (const filePath of resolvedPaths) {
            resolvedUrls.push(await readLocalImageAsDataUrl(filePath));
        }
        return resolvedUrls;
    }

    function normalizeImageSize(imageSize, model) {
        const trimmedModel = String(model || "").trim().toLowerCase();
        const rawSize = String(imageSize || "").trim();
        const isQwenEditModel = trimmedModel.startsWith("qwen/qwen-image-edit");
        if (isQwenEditModel) {
            if (rawSize) {
                throw new Error("当前模型为 Qwen 图像编辑模型，官方文档标注不支持 image_size 参数，请去掉 image_size。");
            }
            return undefined;
        }
        if (!rawSize) return DEFAULT_IMAGE_SIZE;
        if (!/^\d+x\d+$/i.test(rawSize)) {
            throw new Error("image_size 格式必须是 widthxheight，例如 1024x1024。");
        }
        return rawSize.toLowerCase();
    }

    function normalizeVideoSize(imageSize) {
        const rawSize = String(imageSize || "").trim();
        if (!rawSize) return DEFAULT_VIDEO_SIZE;
        if (!VIDEO_SIZE_OPTIONS.includes(rawSize)) {
            throw new Error(`image_size 仅支持 ${VIDEO_SIZE_OPTIONS.join("、")}。`);
        }
        return rawSize;
    }

    function isProbablyUrl(value) {
        return /^https?:\/\//i.test(String(value || "").trim());
    }

    async function ensureDirectories() {
        const dirs = [DRAW_ROOT_DIR, STORAGE_DIR, DRAWS_DIR, VIDEOS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            } catch (error) {
                console.warn(`创建目录异常: ${dir} -> ${getErrorMessage(error)}`);
            }
        }
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
        const base64Content = binaryResult && binaryResult.contentBase64 ? String(binaryResult.contentBase64).trim() : "";
        if (!base64Content) {
            throw new Error(`读取本地图片失败: ${trimmedPath}`);
        }
        return `data:${guessMimeTypeFromPath(trimmedPath)};base64,${base64Content}`;
    }

    async function resolveVideoImageInput(imageUrl, imagePath) {
        const trimmedUrl = String(imageUrl || "").trim();
        const trimmedPath = String(imagePath || "").trim();
        if (trimmedUrl && trimmedPath) {
            throw new Error("image_url 和 image_path 只能二选一，请不要同时传。");
        }
        if (trimmedUrl) {
            if (!isProbablyUrl(trimmedUrl)) {
                throw new Error("image_url 必须是 http 或 https 链接。");
            }
            return trimmedUrl;
        }
        if (trimmedPath) {
            return await readLocalImageAsDataUrl(trimmedPath);
        }
        return undefined;
    }

    async function executeJsonRequest(url: string, body: JsonMap): Promise<any> {
        const apiKey = getApiKey();
        const request = client
            .newRequest()
            .url(url)
            .method("POST")
            .headers({
                "accept": "application/json",
                "content-type": "application/json",
                "Authorization": `Bearer ${apiKey}`
            })
            .body(JSON.stringify(body), "json");

        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`HTTP ${response.statusCode}: ${response.content}`);
        }
        try {
            return JSON.parse(response.content);
        } catch (error) {
            throw new Error(`解析响应 JSON 失败: ${getErrorMessage(error)}`);
        }
    }

    async function callImageApi(params: ImageParams): Promise<ImageApiResult> {
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const endpoint = getImageEndpoint(apiBaseUrl);
        const references = await resolveImageReferences(params.image_urls, params.image_paths);

        const modelFromParam = String(params.model || "").trim();
        const modelFromEnv = String(getEnv("SILICONFLOW_IMAGE_MODEL") || "").trim();
        const effectiveModel = modelFromParam || modelFromEnv || (references.length > 0 ? DEFAULT_IMAGE_EDIT_MODEL : DEFAULT_IMAGE_MODEL);

        const body: JsonMap = {
            model: effectiveModel,
            prompt: String(params.prompt || "").trim()
        };

        if (references.length === 1) {
            body.image = references[0];
        } else if (references.length > 1) {
            body.image = references;
        }

        const imageSize = normalizeImageSize(params.image_size, effectiveModel);
        if (imageSize) body.image_size = imageSize;

        const negativePrompt = String(params.negative_prompt || "").trim();
        if (negativePrompt) body.negative_prompt = negativePrompt;

        const batchSize = parsePositiveInteger(params.batch_size, "batch_size");
        if (batchSize !== undefined) body.batch_size = batchSize;

        const numInferenceSteps = parsePositiveInteger(params.num_inference_steps, "num_inference_steps");
        if (numInferenceSteps !== undefined) body.num_inference_steps = numInferenceSteps;

        const guidanceScale = parseFiniteNumber(params.guidance_scale, "guidance_scale");
        if (guidanceScale !== undefined) body.guidance_scale = guidanceScale;

        const seed = parseSeed(params.seed);
        if (seed !== undefined) body.seed = seed;

        const parsed: any = await executeJsonRequest(endpoint, body);
        const images = Array.isArray(parsed && parsed.images) ? parsed.images : [];
        const imageUrls = images
            .map(item => item && item.url ? String(item.url).trim() : "")
            .filter(item => item.length > 0);

        if (imageUrls.length === 0) {
            throw new Error("图片接口返回中未找到 images[].url，请检查模型与参数是否匹配。");
        }

        return {
            effective_model: effectiveModel,
            image_urls: imageUrls,
            seed: parsed && parsed.seed !== undefined ? parsed.seed : (seed !== undefined ? seed : null),
            reference_count: references.length
        };
    }

    async function createVideoTask(params: VideoParams, imageInput?: string): Promise<VideoTaskResult> {
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const endpoint = getVideoSubmitEndpoint(apiBaseUrl);

        const modelFromParam = String(params.model || "").trim();
        const modelFromEnv = String(getEnv("SILICONFLOW_VIDEO_MODEL") || "").trim();
        const defaultVideoModel = imageInput ? DEFAULT_VIDEO_IMAGE_MODEL : DEFAULT_VIDEO_TEXT_MODEL;
        const effectiveModel = modelFromParam || modelFromEnv || defaultVideoModel;

        if (effectiveModel === DEFAULT_VIDEO_IMAGE_MODEL && !imageInput) {
            throw new Error(`当前模型 ${DEFAULT_VIDEO_IMAGE_MODEL} 为图生视频模型，必须传 image_url 或 image_path。`);
        }

        const body: JsonMap = {
            model: effectiveModel,
            prompt: String(params.prompt || "").trim(),
            image_size: normalizeVideoSize(params.image_size)
        };

        const negativePrompt = String(params.negative_prompt || "").trim();
        if (negativePrompt) body.negative_prompt = negativePrompt;

        if (imageInput) body.image = imageInput;

        const seed = parseSeed(params.seed);
        if (seed !== undefined) body.seed = seed;

        const parsed: any = await executeJsonRequest(endpoint, body);
        const requestId = parsed && parsed.requestId ? String(parsed.requestId).trim() : "";
        if (!requestId) {
            throw new Error("视频接口返回中未找到 requestId。");
        }

        return {
            api_base_url: apiBaseUrl,
            effective_model: effectiveModel,
            request_id: requestId,
            seed: seed !== undefined ? seed : null
        };
    }

    async function queryVideoStatus(apiBaseUrl: string, requestId: string): Promise<VideoStatusResult> {
        const endpoint = getVideoStatusEndpoint(apiBaseUrl);
        const parsed: any = await executeJsonRequest(endpoint, { requestId });
        const status = parsed && parsed.status ? String(parsed.status).trim() : "";
        const reason = parsed && parsed.reason ? String(parsed.reason).trim() : "";
        const videos: any[] = parsed && parsed.results && Array.isArray(parsed.results.videos) ? parsed.results.videos : [];
        const videoUrl = videos.length > 0 && videos[0] && videos[0].url ? String(videos[0].url).trim() : "";
        const seed = parsed && parsed.results && parsed.results.seed !== undefined ? parsed.results.seed : null;
        return {
            status,
            reason,
            video_url: videoUrl,
            seed
        };
    }

    function normalizeVideoStatus(status) {
        return String(status || "").trim().toLowerCase();
    }

    async function draw_image(params: ImageParams) {
        const prompt = String(params && params.prompt ? params.prompt : "").trim();
        if (!prompt) {
            throw new Error("prompt 不能为空。");
        }

        await ensureDirectories();

        const apiResult = await callImageApi(params);
        const baseName = buildFileBaseName(prompt, params.file_name, "siliconflow_image");
        const files: ImageFileItem[] = [];

        for (let index = 0; index < apiResult.image_urls.length; index += 1) {
            const imageUrl = apiResult.image_urls[index];
            const extension = guessExtensionFromUrl(imageUrl, "png");
            const suffix = apiResult.image_urls.length > 1 ? `_${index + 1}` : "";
            const filePath = `${DRAWS_DIR}/${baseName}${suffix}.${extension}`;
            const downloadResult = await Tools.Files.download(imageUrl, filePath);
            if (!downloadResult.successful) {
                throw new Error(`下载图片失败: ${downloadResult.details}`);
            }
            const fileUri = `file://${filePath}`;
            files.push({
                file_path: filePath,
                file_uri: fileUri,
                markdown: `![AI生成的图片${apiResult.image_urls.length > 1 ? ` ${index + 1}` : ""}](${fileUri})`
            });
        }

        const hintLines: string[] = [];
        hintLines.push(`图片已生成并下载到 ${DRAWS_DIR}。`);
        hintLines.push(`共生成 ${files.length} 张。`);
        if (apiResult.reference_count > 0) {
            hintLines.push(`本次使用了 ${apiResult.reference_count} 张参考图。`);
        }
        hintLines.push("");
        hintLines.push("后续回答如果需要展示图片，请直接输出下面这些 Markdown：");
        hintLines.push("");
        for (const fileItem of files) {
            hintLines.push(fileItem.markdown);
        }

        return {
            prompt,
            model: apiResult.effective_model,
            seed: apiResult.seed,
            reference_count: apiResult.reference_count,
            file_path: files[0].file_path,
            file_uri: files[0].file_uri,
            markdown: files[0].markdown,
            files,
            hint: hintLines.join("\n")
        };
    }

    async function draw_video(params: VideoParams) {
        const prompt = String(params && params.prompt ? params.prompt : "").trim();
        if (!prompt) {
            throw new Error("prompt 不能为空。");
        }

        await ensureDirectories();

        const imageInput = await resolveVideoImageInput(params.image_url, params.image_path);
        const createdTask = await createVideoTask(params, imageInput);
        const pollIntervalMs = parsePositiveInteger(params.poll_interval_ms, "poll_interval_ms") || DEFAULT_POLL_INTERVAL_MS;
        const maxWaitTimeMs = parsePositiveInteger(params.max_wait_time_ms, "max_wait_time_ms") || DEFAULT_MAX_WAIT_TIME_MS;
        const deadline = Date.now() + maxWaitTimeMs;

        let latestStatus = "";
        let latestReason = "";
        let remoteVideoUrl = "";
        let resultSeed = createdTask.seed;

        while (Date.now() <= deadline) {
            const statusResult = await queryVideoStatus(createdTask.api_base_url, createdTask.request_id);
            latestStatus = statusResult.status;
            latestReason = statusResult.reason;
            resultSeed = statusResult.seed !== null && statusResult.seed !== undefined ? statusResult.seed : resultSeed;

            const normalizedStatus = normalizeVideoStatus(statusResult.status);
            if (normalizedStatus === "succeed") {
                remoteVideoUrl = statusResult.video_url;
                if (!remoteVideoUrl) {
                    throw new Error("视频任务已成功，但返回中未找到 results.videos[0].url。");
                }
                break;
            }

            if (normalizedStatus === "failed") {
                throw new Error(`视频生成失败: ${latestReason || "接口未返回失败原因"}`);
            }

            await Tools.System.sleep(pollIntervalMs);
        }

        if (!remoteVideoUrl) {
            throw new Error(`视频生成超时，最后状态为 ${latestStatus || "未知"}${latestReason ? `，原因：${latestReason}` : ""}`);
        }

        const baseName = buildFileBaseName(prompt, params.file_name, "siliconflow_video");
        const extension = guessExtensionFromUrl(remoteVideoUrl, "mp4");
        const filePath = `${VIDEOS_DIR}/${baseName}.${extension}`;
        const downloadResult = await Tools.Files.download(remoteVideoUrl, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载视频失败: ${downloadResult.details}`);
        }

        const fileUri = `file://${filePath}`;
        const markdownLink = `[点击查看生成的视频](${fileUri})`;
        const htmlVideo = `<video controls src="${fileUri}"></video>`;
        const hintLines: string[] = [];
        hintLines.push(`视频已生成并下载到 ${VIDEOS_DIR}。`);
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
            seed: resultSeed,
            file_path: filePath,
            file_uri: fileUri,
            remote_video_url: remoteVideoUrl,
            markdown_link: markdownLink,
            html_video: htmlVideo,
            hint: hintLines.join("\n")
        };
    }

    async function draw_image_wrapper(params: ImageParams) {
        try {
            const result = await draw_image(params || {});
            complete({
                success: true,
                message: "SiliconFlow 图片生成成功，已下载到本地。",
                data: result
            });
        } catch (error) {
            console.error("draw_image 执行失败:", error);
            complete({
                success: false,
                message: `SiliconFlow 图片生成失败: ${getErrorMessage(error)}`,
                error_stack: error && error.stack ? error.stack : undefined
            });
        }
    }

    async function draw_video_wrapper(params: VideoParams) {
        try {
            const result = await draw_video(params || {});
            complete({
                success: true,
                message: "SiliconFlow 视频生成成功，已下载到本地。",
                data: result
            });
        } catch (error) {
            console.error("draw_video 执行失败:", error);
            complete({
                success: false,
                message: `SiliconFlow 视频生成失败: ${getErrorMessage(error)}`,
                error_stack: error && error.stack ? error.stack : undefined
            });
        }
    }

    return {
        draw_image: draw_image_wrapper,
        draw_video: draw_video_wrapper
    };
})();

exports.draw_image = siliconflowDraw.draw_image;
exports.draw_video = siliconflowDraw.draw_video;

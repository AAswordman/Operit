"use strict";
// @ts-nocheck
/* METADATA
{
    "name": "apk_reverse",
    "display_name": {
        "zh": "APK 逆向工具包",
        "en": "APK Reverse Toolkit"
    },
    "description": {
        "zh": "基于内置 dex-jar 运行时的 APK 逆向工具包，提供 inspect/decode/jadx/build/sign/search 等直调能力。JADX 支持独立进程隔离、内存耗尽时保留部分结果并可从断点续跑。",
        "en": "APK reverse toolkit backed by bundled dex-jar runtimes, providing direct inspect/decode/jadx/build/sign/search capabilities. JADX runs in an isolated process, keeps partial results when memory runs out, and can resume from the truncation point."
    },
    "enabledByDefault": true,
    "category": "System",
    "tools": [
        {
            "name": "usage_advice",
            "description": {
                "zh": "返回当前 APK 逆向工具包的使用说明与资源状态。",
                "en": "Return usage notes and runtime resource status for the APK reverse toolkit."
            },
            "parameters": [],
            "advice": true
        },
        {
            "name": "apk_reverse_selftest",
            "description": {
                "zh": "逐阶段自检：分开测试资源读取、dex-jar 加载、Java.type 解析和 helper 调用，并返回每一步的具体错误。",
                "en": "Stage-by-stage self test: probes resource read, dex-jar load, Java.type resolution and a helper call, returning the concrete error for each step."
            },
            "parameters": [
                { "name": "input_apk_path", "description": { "zh": "可选，用于测试 helper 实际调用的 APK 路径。", "en": "Optional APK path used to exercise a real helper call." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apk_reverse_inspect",
            "description": {
                "zh": "检查 APK 基本信息、组件、权限、签名文件摘要、dex 与 so 分布。",
                "en": "Inspect APK metadata, components, permissions, signature-file digests, dex files, and native libraries."
            },
            "parameters": [
                { "name": "input_apk_path", "description": { "zh": "APK 文件路径。", "en": "Path to the APK file." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "apk_reverse_decode",
            "description": {
                "zh": "用 apktool 直调方式将 APK 解包到目录。",
                "en": "Decode an APK into a directory using the direct apktool bridge."
            },
            "parameters": [
                { "name": "input_apk_path", "description": { "zh": "APK 文件路径。", "en": "Path to the APK file." }, "type": "string", "required": true },
                { "name": "output_dir", "description": { "zh": "输出目录。", "en": "Output directory." }, "type": "string", "required": true },
                { "name": "force", "description": { "zh": "是否覆盖输出目录。", "en": "Whether to overwrite the output directory." }, "type": "string", "required": false },
                { "name": "frame_path", "description": { "zh": "自定义 framework 目录。", "en": "Custom framework directory." }, "type": "string", "required": false },
                { "name": "frame_tag", "description": { "zh": "自定义 framework tag。", "en": "Custom framework tag." }, "type": "string", "required": false },
                { "name": "jobs", "description": { "zh": "并发线程数，1-16，默认 1。大型 APK 建议保持 1。", "en": "Concurrency thread count, 1-16, default 1. Keep 1 for large APKs." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apk_reverse_jadx",
            "description": {
                "zh": "用 bundled JADX dex-jar 直调反编译 APK 到目录。",
                "en": "Decompile an APK into a directory through the bundled JADX dex-jar runtime."
            },
            "parameters": [
                { "name": "input_apk_path", "description": { "zh": "APK 文件路径。", "en": "Path to the APK file." }, "type": "string", "required": true },
                { "name": "output_dir", "description": { "zh": "输出目录。", "en": "Output directory." }, "type": "string", "required": true },
                { "name": "jobs", "description": { "zh": "并发线程数，1-16，默认 1。大型 APK 建议保持 1。", "en": "Concurrency thread count, 1-16, default 1. Keep 1 for large APKs." }, "type": "string", "required": false },
                { "name": "deobf", "description": { "zh": "是否开启 deobfuscation。", "en": "Whether to enable deobfuscation." }, "type": "string", "required": false },
                { "name": "show_inconsistent_code", "description": { "zh": "是否显示不一致代码。", "en": "Whether to show inconsistent code." }, "type": "string", "required": false },
                { "name": "isolated", "description": { "zh": "是否在独立进程中运行 JADX，默认 true。开启后 JADX 获得自己的堆，子进程 OOM 不会让宿主应用闪退。", "en": "Run JADX in a separate process, default TRUE. The child gets its own heap so a child OOM cannot crash the host app. Do not set false for large APKs: in-process JADX has been measured to crash the host." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apk_reverse_search_text",
            "description": {
                "zh": "在解包目录、JADX 输出目录或 APK 临时工作区中进行文本搜索。",
                "en": "Search text in a decoded directory, JADX output directory, or an APK-backed temporary workspace."
            },
            "parameters": [
                { "name": "input_path", "description": { "zh": "目录路径或 APK 路径。", "en": "Directory path or APK path." }, "type": "string", "required": true },
                { "name": "query", "description": { "zh": "搜索词或正则。", "en": "Search query or regex." }, "type": "string", "required": true },
                { "name": "scope", "description": { "zh": "manifest/res/smali/jadx/native_strings/all。", "en": "manifest/res/smali/jadx/native_strings/all." }, "type": "string", "required": false },
                { "name": "regex", "description": { "zh": "是否按正则搜索。", "en": "Whether to interpret query as regex." }, "type": "string", "required": false },
                { "name": "case_insensitive", "description": { "zh": "是否忽略大小写。", "en": "Whether to ignore case." }, "type": "string", "required": false },
                { "name": "max_results", "description": { "zh": "最大结果数。", "en": "Maximum result count." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apk_reverse_search_address",
            "description": {
                "zh": "在资源、smali、JADX 输出与 native so 中搜索地址/引用/字节模式。",
                "en": "Search resource IDs, references, offsets, and byte patterns across resources, smali, JADX output, and native libraries."
            },
            "parameters": [
                { "name": "input_path", "description": { "zh": "目录路径或 APK 路径。", "en": "Directory path or APK path." }, "type": "string", "required": true },
                { "name": "query", "description": { "zh": "资源 ID、引用名、偏移或 hex 模式。", "en": "Resource ID, reference name, offset, or hex pattern." }, "type": "string", "required": true },
                { "name": "scope", "description": { "zh": "resource_id/smali_ref/jadx_ref/native_symbol/native_offset/hex_bytes/all。", "en": "resource_id/smali_ref/jadx_ref/native_symbol/native_offset/hex_bytes/all." }, "type": "string", "required": false },
                { "name": "max_results", "description": { "zh": "最大结果数。", "en": "Maximum result count." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apk_reverse_build",
            "description": {
                "zh": "对解包目录执行 apktool build 直调重编译。",
                "en": "Rebuild a decoded directory through the direct apktool build bridge."
            },
            "parameters": [
                { "name": "decoded_dir", "description": { "zh": "解包目录路径。", "en": "Decoded directory path." }, "type": "string", "required": true },
                { "name": "output_apk_path", "description": { "zh": "输出 APK 路径。", "en": "Output APK path." }, "type": "string", "required": true },
                { "name": "frame_path", "description": { "zh": "自定义 framework 目录。", "en": "Custom framework directory." }, "type": "string", "required": false },
                { "name": "frame_tag", "description": { "zh": "自定义 framework tag。", "en": "Custom framework tag." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apk_reverse_sign",
            "description": {
                "zh": "直接使用 apksig API 对 APK 进行 debug 或 keystore 签名。",
                "en": "Sign an APK directly through apksig APIs using debug or keystore mode."
            },
            "parameters": [
                { "name": "input_apk_path", "description": { "zh": "输入 APK 路径。", "en": "Input APK path." }, "type": "string", "required": true },
                { "name": "output_apk_path", "description": { "zh": "输出 APK 路径。", "en": "Output APK path." }, "type": "string", "required": true },
                { "name": "sign_mode", "description": { "zh": "debug 或 keystore。", "en": "debug or keystore." }, "type": "string", "required": true },
                { "name": "keystore_path", "description": { "zh": "keystore 文件路径。", "en": "Keystore file path." }, "type": "string", "required": false },
                { "name": "storepass", "description": { "zh": "keystore 密码。", "en": "Keystore password." }, "type": "string", "required": false },
                { "name": "alias", "description": { "zh": "密钥别名。", "en": "Key alias." }, "type": "string", "required": false },
                { "name": "keypass", "description": { "zh": "密钥密码。", "en": "Key password." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apk_reverse_build_and_sign",
            "description": {
                "zh": "先 build 再 sign，直接产出签名 APK。",
                "en": "Build first and sign immediately, producing a signed APK."
            },
            "parameters": [
                { "name": "decoded_dir", "description": { "zh": "解包目录路径。", "en": "Decoded directory path." }, "type": "string", "required": true },
                { "name": "output_apk_path", "description": { "zh": "输出签名 APK 路径。", "en": "Output signed APK path." }, "type": "string", "required": true },
                { "name": "sign_mode", "description": { "zh": "debug 或 keystore。", "en": "debug or keystore." }, "type": "string", "required": true },
                { "name": "frame_path", "description": { "zh": "自定义 framework 目录。", "en": "Custom framework directory." }, "type": "string", "required": false },
                { "name": "frame_tag", "description": { "zh": "自定义 framework tag。", "en": "Custom framework tag." }, "type": "string", "required": false },
                { "name": "keystore_path", "description": { "zh": "keystore 文件路径。", "en": "Keystore file path." }, "type": "string", "required": false },
                { "name": "storepass", "description": { "zh": "keystore 密码。", "en": "Keystore password." }, "type": "string", "required": false },
                { "name": "alias", "description": { "zh": "密钥别名。", "en": "Key alias." }, "type": "string", "required": false },
                { "name": "keypass", "description": { "zh": "密钥密码。", "en": "Key password." }, "type": "string", "required": false }
            ]
        }
    ]
}
*/
Object.defineProperty(exports, "__esModule", { value: true });
exports.usage_advice = usage_advice;
exports.apk_reverse_selftest = apk_reverse_selftest;
exports.apk_reverse_inspect = apk_reverse_inspect;
exports.apk_reverse_decode = apk_reverse_decode;
exports.apk_reverse_jadx = apk_reverse_jadx;
exports.apk_reverse_search_text = apk_reverse_search_text;
exports.apk_reverse_search_address = apk_reverse_search_address;
exports.apk_reverse_build = apk_reverse_build;
exports.apk_reverse_sign = apk_reverse_sign;
exports.apk_reverse_build_and_sign = apk_reverse_build_and_sign;
exports.ensureHelperRuntimeLoaded = ensureHelperRuntimeLoaded;
const PACKAGE_VERSION = "1.1.0";
const APKTOOL_VERSION = "3.0.1";
const JADX_VERSION = "1.5.2";
const APKTOOL_RUNTIME_RESOURCE_KEY = "apktool_runtime_android_jar";
const APKTOOL_ANDROID_FRAMEWORK_RESOURCE_KEY = "apktool_android_framework_jar";
const JADX_RUNTIME_RESOURCE_KEY = "jadx_runtime_android_jar";
const HELPER_RUNTIME_RESOURCE_KEY = "apk_reverse_helper_runtime_android_jar";
const APKTOOL_RUNTIME_OUTPUT_FILE_NAME = "apktool-runtime-android.jar";
const APKTOOL_ANDROID_FRAMEWORK_OUTPUT_FILE_NAME = "android-framework.jar";
const JADX_RUNTIME_OUTPUT_FILE_NAME = "jadx-runtime-android.jar";
const HELPER_RUNTIME_OUTPUT_FILE_NAME = "apk-reverse-helper-runtime-android.jar";
const APKTOOL_RUNTIME_SOURCE_ARTIFACT = "org.apktool:apktool-cli:3.0.1";
const JADX_RUNTIME_SOURCE_ARTIFACT = "io.github.skylot:jadx-core:1.5.2";
const TEMP_ROOT_DIR_NAME = "apk_reverse_runtime";
const DEFAULT_MAX_RESULTS = 100;
const MIN_MAX_RESULTS = 1;
const HARD_MAX_RESULTS = 500;
const INLINE_RESULT_CHAR_LIMIT = 24000;
const MAX_TEXT_FILE_BYTES = 2 * 1024 * 1024;
const MAX_BINARY_WINDOW_BYTES = 96;
const APKTOOL_RUNTIME_CHILD_FIRST_PREFIXES = [
    "antlr.",
    "brut.",
    "com.android.",
    "com.beust.",
    "com.google.",
    "javax.annotation.",
    "org.antlr.",
    "org.apache.",
    "org.jspecify.",
    "org.stringtemplate.",
    "org.xmlpull."
];
const JADX_RUNTIME_CHILD_FIRST_PREFIXES = [
    "jadx.",
    "org.intellij.",
    "org.jetbrains.",
    "org.slf4j.",
    "com.android."
];
const HELPER_RUNTIME_CHILD_FIRST_PREFIXES = [
    "com.operit.apkreverse."
];
const JVM_COMPAT_OS_NAME = "Linux";
const JVM_COMPAT_OS_ARCH = "aarch64";
const JVM_COMPAT_ARCH_DATA_MODEL = "64";
let helperRuntimeLoadSequence = 0;
const MIN_JOBS = 1;
const MAX_JOBS = 16;
const DEFAULT_JOBS = 1;
let apktoolRuntimePromise = null;
let jadxRuntimePromise = null;
let helperRuntimePromise = null;
let helperRuntimeSignature = "";
let frameworkJarPromise = null;
const loadedRuntimeChain = [];
function recordRuntimeInChain(name) {
    if (loadedRuntimeChain.indexOf(name) < 0) {
        loadedRuntimeChain.push(name);
    }
}
function currentRuntimeChainSignature() {
    return loadedRuntimeChain.join(">");
}
let heavyOperationChain = Promise.resolve();
let heavyOperationActive = null;
function clampMaxResults(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
        return DEFAULT_MAX_RESULTS;
    }
    const rounded = Math.floor(numeric);
    if (rounded < MIN_MAX_RESULTS) {
        return MIN_MAX_RESULTS;
    }
    if (rounded > HARD_MAX_RESULTS) {
        return HARD_MAX_RESULTS;
    }
    return rounded;
}
function normalizeJobs(params, key) {
    const raw = optionalInteger(params, key || "jobs", DEFAULT_JOBS);
    const value = Number(raw);
    if (!Number.isFinite(value)) {
        return DEFAULT_JOBS;
    }
    const rounded = Math.floor(value);
    if (rounded < MIN_JOBS) {
        return MIN_JOBS;
    }
    if (rounded > MAX_JOBS) {
        return MAX_JOBS;
    }
    return rounded;
}
function runExclusiveHeavyOperation(label, action) {
    const run = heavyOperationChain.then(async () => {
        heavyOperationActive = label;
        try {
            return await action();
        }
        finally {
            heavyOperationActive = null;
        }
    });
    heavyOperationChain = run.then(function () {
        return undefined;
    }, function () {
        return undefined;
    });
    return run;
}
function asText(value) {
    if (value === undefined || value === null) {
        return "";
    }
    return String(value);
}
function hasOwn(object, key) {
    return !!object && Object.prototype.hasOwnProperty.call(object, key);
}
function isProvided(value) {
    if (value === undefined || value === null) {
        return false;
    }
    if (typeof value === "string") {
        return value.trim().length > 0;
    }
    if (Array.isArray(value)) {
        return value.length > 0;
    }
    return true;
}
function toErrorText(error) {
    const detail = describeError(error);
    return detail.message || "unknown error";
}
function describeError(error) {
    const detail = {
        errorType: "",
        message: "",
        stack: "",
        cause: "",
        raw: ""
    };
    if (error === undefined || error === null) {
        detail.errorType = "NullError";
        detail.message = "unknown error (no error object was thrown)";
        return detail;
    }
    detail.raw = safeStringify(error);
    if (error instanceof Error) {
        detail.errorType = asText(error.name) || "Error";
        detail.message = asText(error.message) || detail.raw;
        detail.stack = asText(error.stack);
        if (error.cause !== undefined && error.cause !== null) {
            detail.cause = safeStringify(error.cause);
        }
        return detail;
    }
    // Rhino/Java interop: a thrown Java exception often surfaces as a host object
    // that only reveals itself through getClass()/getMessage()/javaException.
    const javaDetail = describeJavaThrowable(error);
    if (javaDetail) {
        return javaDetail;
    }
    if (typeof error === "object") {
        detail.errorType = asText(error.name) || asText(error.errorType) || "ObjectError";
        detail.message = asText(error.message) || asText(error.error) || detail.raw;
        detail.stack = asText(error.stack);
        return detail;
    }
    detail.errorType = typeof error;
    detail.message = detail.raw;
    return detail;
}
function describeJavaThrowable(candidate) {
    const throwable = extractJavaThrowable(candidate);
    if (!throwable) {
        return null;
    }
    const detail = {
        errorType: "",
        message: "",
        stack: "",
        cause: "",
        raw: ""
    };
    try {
        detail.errorType = asText(throwable.getClass().getName());
    }
    catch (_ignored) {
        detail.errorType = "JavaThrowable";
    }
    try {
        detail.message = asText(throwable.getMessage());
    }
    catch (_ignored) {
        detail.message = "";
    }
    try {
        detail.raw = asText(throwable.toString());
    }
    catch (_ignored) {
        detail.raw = detail.errorType;
    }
    if (!detail.message) {
        detail.message = detail.raw || detail.errorType;
    }
    try {
        const frames = throwable.getStackTrace();
        if (frames && frames.length) {
            const lines = [];
            const limit = Math.min(frames.length, 12);
            for (let index = 0; index < limit; index += 1) {
                lines.push("    at " + asText(frames[index]));
            }
            detail.stack = lines.join("\n");
        }
    }
    catch (_ignored) {
        detail.stack = "";
    }
    try {
        const cause = throwable.getCause();
        if (cause) {
            detail.cause = asText(cause.toString());
        }
    }
    catch (_ignored) {
        detail.cause = "";
    }
    return detail;
}
function extractJavaThrowable(candidate) {
    if (!candidate) {
        return null;
    }
    const probes = [candidate, candidate.javaException, candidate.rhinoException];
    for (let index = 0; index < probes.length; index += 1) {
        const probe = probes[index];
        if (!probe) {
            continue;
        }
        try {
            if (typeof probe.getClass === "function" && typeof probe.getMessage === "function") {
                return probe;
            }
        }
        catch (_ignored) {
            // not a Java object, keep probing
        }
    }
    return null;
}
function safeStringify(value) {
    try {
        const text = asText(value);
        if (text && text !== "[object Object]") {
            return text;
        }
    }
    catch (_ignored) {
        // fall through to JSON
    }
    try {
        return JSON.stringify(value);
    }
    catch (_ignored) {
        return "[unserializable error object]";
    }
}
function requireText(params, key) {
    const value = asText(params && params[key]).trim();
    if (!value) {
        throw new Error(`Missing required parameter: ${key}`);
    }
    return value;
}
function optionalText(params, key) {
    const value = asText(params && params[key]).trim();
    return value || undefined;
}
function parseBoolean(value, key) {
    if (typeof value === "boolean") {
        return value;
    }
    const normalized = asText(value).trim().toLowerCase();
    if (!normalized) {
        throw new Error(`${key} must be a boolean`);
    }
    if (["1", "true", "yes", "y", "on"].includes(normalized)) {
        return true;
    }
    if (["0", "false", "no", "n", "off"].includes(normalized)) {
        return false;
    }
    throw new Error(`${key} must be a boolean`);
}
function optionalBoolean(params, key, fallbackValue) {
    if (!hasOwn(params, key) || !isProvided(params[key])) {
        return fallbackValue;
    }
    return parseBoolean(params[key], key);
}
function parseInteger(value, key) {
    const parsed = Number(value);
    if (!Number.isInteger(parsed)) {
        throw new Error(`${key} must be an integer`);
    }
    return parsed;
}
function optionalInteger(params, key, fallbackValue) {
    if (!hasOwn(params, key) || !isProvided(params[key])) {
        return fallbackValue;
    }
    return parseInteger(params[key], key);
}
function normalizeToken(value) {
    return asText(value).trim().toLowerCase().replace(/[\s-]+/g, "_");
}
function escapeRegExp(value) {
    return asText(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
function toHex(value, width) {
    let hex = Math.max(0, Number(value) || 0).toString(16);
    while (hex.length < width) {
        hex = `0${hex}`;
    }
    return hex;
}
function toUnsignedByte(value) {
    return Number(value) & 0xff;
}
function hexBytes(bytes, start, length) {
    const parts = [];
    const limit = Math.min(bytes.length, start + length);
    for (let index = start; index < limit; index += 1) {
        parts.push(toHex(toUnsignedByte(bytes[index]), 2));
    }
    return parts.join(" ");
}
function getCommonClasses() {
    return {
        File: Java.type("java.io.File"),
        FilesNio: Java.type("java.nio.file.Files"),
        StandardCharsets: Java.type("java.nio.charset.StandardCharsets"),
        JString: Java.type("java.lang.String"),
        ZipFile: Java.type("java.util.zip.ZipFile"),
        MessageDigest: Java.type("java.security.MessageDigest"),
        FileOutputStream: Java.type("java.io.FileOutputStream"),
        BufferedOutputStream: Java.type("java.io.BufferedOutputStream"),
        ByteArrayOutputStream: Java.type("java.io.ByteArrayOutputStream"),
        ArrayList: Java.type("java.util.ArrayList"),
        Collections: Java.type("java.util.Collections"),
        FileInputStream: Java.type("java.io.FileInputStream"),
        KeyStoreHelper: Java.type("com.ai.assistance.operit.core.subpack.KeyStoreHelper"),
        ApkSignerBuilder: Java.type("com.android.apksig.ApkSigner$Builder"),
        ApkSignerConfigBuilder: Java.type("com.android.apksig.ApkSigner$SignerConfig$Builder")
    };
}
function ensureJvmCompatibilitySystemProperties() {
    const System = Java.type("java.lang.System");
    const Locale = Java.type("java.util.Locale");
    const File = Java.type("java.io.File");
    const filesDir = new File("/data/data/com.ai.assistance.operit/files");
    const cacheDir = new File("/data/data/com.ai.assistance.operit/cache");
    const locale = Locale.getDefault();
    const country = asText(locale.getCountry()).trim();
    ensureSystemProperty(System, "os.name", JVM_COMPAT_OS_NAME);
    ensureSystemProperty(System, "os.arch", JVM_COMPAT_OS_ARCH);
    ensureSystemProperty(System, "sun.arch.data.model", JVM_COMPAT_ARCH_DATA_MODEL);
    ensureSystemProperty(System, "user.home", asText(filesDir.getAbsolutePath()));
    ensureSystemProperty(System, "user.dir", asText(filesDir.getAbsolutePath()));
    ensureSystemProperty(System, "java.io.tmpdir", asText(cacheDir.getAbsolutePath()));
    ensureSystemProperty(System, "user.language", asText(locale.getLanguage()).trim() || "en");
    if (country) {
        ensureSystemProperty(System, "user.country", country);
    }
}
function ensureSystemProperty(System, key, value) {
    const normalizedValue = asText(value).trim();
    if (!normalizedValue) {
        return;
    }
    const current = asText(System.getProperty(key)).trim();
    if (!current) {
        System.setProperty(key, normalizedValue);
    }
}
function collectJvmCompatibilitySystemProperties() {
    const System = Java.type("java.lang.System");
    return {
        osName: asText(System.getProperty("os.name")),
        osArch: asText(System.getProperty("os.arch")),
        sunArchDataModel: asText(System.getProperty("sun.arch.data.model")),
        userHome: asText(System.getProperty("user.home")),
        userDir: asText(System.getProperty("user.dir")),
        javaIoTmpdir: asText(System.getProperty("java.io.tmpdir")),
        userLanguage: asText(System.getProperty("user.language")),
        userCountry: asText(System.getProperty("user.country"))
    };
}
async function loadDexJarRuntime(resourceKey, outputFileName, childFirstPrefixes, missingMessage) {
    ensureJvmCompatibilitySystemProperties();
    let runtimeJarPath;
    try {
        runtimeJarPath = await ToolPkg.readResource(resourceKey, outputFileName, true);
    }
    catch (error) {
        const detail = describeError(error);
        throw new Error(`${missingMessage} [stage=readResource resourceKey=${resourceKey} outputFileName=${outputFileName}]: `
            + `${detail.errorType}: ${detail.message}`);
    }
    if (!isProvided(runtimeJarPath)) {
        throw new Error(`${missingMessage} [stage=readResource resourceKey=${resourceKey}]: `
            + `ToolPkg.readResource returned an empty path`);
    }
    let loadInfo;
    try {
        loadInfo = Java.loadJar(runtimeJarPath, {
            childFirstPrefixes
        });
    }
    catch (error) {
        const detail = describeError(error);
        throw new Error(`Failed to load dex-jar runtime [stage=Java.loadJar resourceKey=${resourceKey} `
            + `jarPath=${runtimeJarPath} childFirstPrefixes=${childFirstPrefixes.join(",")}]: `
            + `${detail.errorType}: ${detail.message}`
            + (detail.stack ? ` | stack:\n${detail.stack}` : ""));
    }
    return {
        runtimeJarPath,
        loadInfo
    };
}
async function ensureApktoolRuntimeLoaded() {
    if (!apktoolRuntimePromise) {
        apktoolRuntimePromise = loadApktoolRuntime().catch((error) => {
            apktoolRuntimePromise = null;
            throw error;
        });
    }
    return apktoolRuntimePromise;
}
async function loadApktoolRuntime() {
    recordRuntimeInChain("apktool");
    const runtime = await loadDexJarRuntime(APKTOOL_RUNTIME_RESOURCE_KEY, APKTOOL_RUNTIME_OUTPUT_FILE_NAME, APKTOOL_RUNTIME_CHILD_FIRST_PREFIXES, "Missing bundled apktool runtime resource");
    return {
        ...runtime,
        sourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT
    };
}
async function ensureJadxRuntimeLoaded() {
    if (!jadxRuntimePromise) {
        jadxRuntimePromise = loadJadxRuntime().catch((error) => {
            jadxRuntimePromise = null;
            throw error;
        });
    }
    return jadxRuntimePromise;
}
async function loadJadxRuntime() {
    recordRuntimeInChain("jadx");
    const runtime = await loadDexJarRuntime(JADX_RUNTIME_RESOURCE_KEY, JADX_RUNTIME_OUTPUT_FILE_NAME, JADX_RUNTIME_CHILD_FIRST_PREFIXES, "Missing bundled JADX runtime resource. Run build_runtime_android_resources.ps1 and regenerate the toolpkg resources");
    return {
        ...runtime,
        sourceArtifact: JADX_RUNTIME_SOURCE_ARTIFACT
    };
}
async function ensureHelperRuntimeLoaded(tag) {
    // The helper must always sit at the tail of the host ClassLoader chain, otherwise
    // Java.type() inside the helper cannot resolve classes from runtimes that joined
    // the chain later (brut.androlib.Config, jadx.api.*, ...).
    const signature = currentRuntimeChainSignature();
    if (helperRuntimePromise && helperRuntimeSignature === signature) {
        return helperRuntimePromise;
    }
    helperRuntimeSignature = signature;
    helperRuntimePromise = loadHelperRuntime(tag).catch((error) => {
        helperRuntimePromise = null;
        helperRuntimeSignature = "";
        throw error;
    });
    return helperRuntimePromise;
}
async function loadHelperRuntime(tag) {
    const outputFileName = nextHelperRuntimeOutputFileName(tag);
    const runtime = await loadDexJarRuntime(HELPER_RUNTIME_RESOURCE_KEY, outputFileName, HELPER_RUNTIME_CHILD_FIRST_PREFIXES, "Missing bundled APK reverse helper runtime resource. Run build_runtime_android_resources.ps1 and regenerate the toolpkg resources");
    return runtime;
}
function nextHelperRuntimeOutputFileName(tag) {
    helperRuntimeLoadSequence += 1;
    const normalizedTag = asText(tag).trim().toLowerCase().replace(/[^a-z0-9]+/g, "_") || "helper";
    const extensionIndex = HELPER_RUNTIME_OUTPUT_FILE_NAME.lastIndexOf(".");
    const suffix = `${normalizedTag}-load${helperRuntimeLoadSequence}`;
    if (extensionIndex < 0) {
        return `${HELPER_RUNTIME_OUTPUT_FILE_NAME}-${suffix}`;
    }
    return `${HELPER_RUNTIME_OUTPUT_FILE_NAME.slice(0, extensionIndex)}-${suffix}${HELPER_RUNTIME_OUTPUT_FILE_NAME.slice(extensionIndex)}`;
}
async function isBundledResourceAvailable(resourceKey, outputFileName) {
    try {
        const resourcePath = await ToolPkg.readResource(resourceKey, outputFileName, true);
        return isProvided(resourcePath);
    }
    catch (_error) {
        return false;
    }
}
function getHelperBridgeClasses() {
    return {
        ApkReverseHelperFacade: Java.type("com.operit.apkreverse.runtime.ApkReverseHelperFacade")
    };
}
async function callHelperFacade(methodName, invoke, helperLoadTag) {
    const runtime = await ensureHelperRuntimeLoaded(helperLoadTag || methodName);
    let classes;
    try {
        classes = getHelperBridgeClasses();
    }
    catch (error) {
        const detail = describeError(error);
        throw new Error(`Failed to resolve helper bridge class `
            + `[stage=Java.type className=com.operit.apkreverse.runtime.ApkReverseHelperFacade `
            + `method=${methodName} helperJar=${runtime.runtimeJarPath}]: `
            + `${detail.errorType}: ${detail.message}`);
    }
    let raw;
    try {
        raw = invoke(classes.ApkReverseHelperFacade);
    }
    catch (error) {
        const detail = describeError(error);
        throw new Error(`Helper runtime call failed `
            + `[stage=invoke method=${methodName} helperJar=${runtime.runtimeJarPath}]: `
            + `${detail.errorType}: ${detail.message}`
            + (detail.cause ? ` | caused by: ${detail.cause}` : "")
            + (detail.stack ? ` | stack:\n${detail.stack}` : ""));
    }
    const text = asText(raw).trim();
    if (!text) {
        throw new Error(`Helper runtime returned empty payload `
            + `[stage=invoke method=${methodName} helperJar=${runtime.runtimeJarPath}]`);
    }
    let parsed;
    try {
        parsed = JSON.parse(text);
    }
    catch (error) {
        const detail = describeError(error);
        throw new Error(`Failed to parse helper runtime payload `
            + `[stage=JSON.parse method=${methodName} payloadLength=${text.length}]: `
            + `${detail.errorType}: ${detail.message} | payloadHead=${text.slice(0, 400)}`);
    }
    return {
        runtime,
        payload: parsed
    };
}
async function ensureFrameworkJarPath() {
    if (!frameworkJarPromise) {
        frameworkJarPromise = loadFrameworkJarPath().catch((error) => {
            frameworkJarPromise = null;
            throw error;
        });
    }
    return frameworkJarPromise;
}
async function loadFrameworkJarPath() {
    return ToolPkg.readResource(APKTOOL_ANDROID_FRAMEWORK_RESOURCE_KEY, APKTOOL_ANDROID_FRAMEWORK_OUTPUT_FILE_NAME, true);
}
function getApplicationContext() {
    return Java.getApplicationContext();
}
function getRuntimeCacheRoot() {
    const classes = getCommonClasses();
    const cacheDir = getApplicationContext().getCacheDir();
    const root = new classes.File(cacheDir, TEMP_ROOT_DIR_NAME);
    if (!root.exists()) {
        root.mkdirs();
    }
    return root;
}
function createTempDir(prefix) {
    const classes = getCommonClasses();
    const root = getRuntimeCacheRoot();
    const stamp = `${prefix}_${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
    const dir = new classes.File(root, stamp);
    if (!dir.exists() && !dir.mkdirs()) {
        throw new Error(`Failed to create temp directory: ${dir.getAbsolutePath()}`);
    }
    return dir;
}
function createTempFile(prefix, suffix) {
    const dir = createTempDir(prefix);
    const classes = getCommonClasses();
    return new classes.File(dir, `${prefix}${suffix}`);
}
function deleteRecursively(file) {
    if (!file || !file.exists()) {
        return;
    }
    if (file.isDirectory()) {
        const children = file.listFiles();
        if (children) {
            const length = Number(children.length);
            for (let index = 0; index < length; index += 1) {
                deleteRecursively(children[index]);
            }
        }
    }
    file.delete();
}
function requireExistingRegularFile(file, parameterName) {
    const absolutePath = asText(file.getAbsolutePath());
    if (!file.exists()) {
        throw new Error(`${parameterName} does not exist: ${absolutePath}`);
    }
    if (!file.isFile()) {
        throw new Error(`${parameterName} is not a file: ${absolutePath}`);
    }
    return absolutePath;
}
function requireExistingDirectory(file, parameterName) {
    const absolutePath = asText(file.getAbsolutePath());
    if (!file.exists()) {
        throw new Error(`${parameterName} does not exist: ${absolutePath}`);
    }
    if (!file.isDirectory()) {
        throw new Error(`${parameterName} is not a directory: ${absolutePath}`);
    }
    return absolutePath;
}
function writeUtf8Text(file, text) {
    const classes = getCommonClasses();
    const parent = file.getParentFile();
    if (parent && !parent.exists()) {
        parent.mkdirs();
    }
    const bytes = new classes.JString(asText(text)).getBytes(classes.StandardCharsets.UTF_8);
    classes.FilesNio.write(file.toPath(), bytes);
}
function fileExtension(pathText) {
    const normalized = asText(pathText).trim().toLowerCase();
    const index = normalized.lastIndexOf(".");
    if (index < 0) {
        return "";
    }
    return normalized.slice(index);
}
function maybePersistLargeField(payload, key, stem) {
    const raw = JSON.stringify(payload[key], null, 2);
    if (raw.length <= INLINE_RESULT_CHAR_LIMIT) {
        return payload;
    }
    const file = new (getCommonClasses().File)(createTempDir(`${stem}_payload`), `${stem}.json`);
    writeUtf8Text(file, raw);
    payload[`${key}SavedTo`] = asText(file.getAbsolutePath());
    payload[`${key}Persisted`] = true;
    payload[key] = payload[key].slice(0, 10);
    payload[`${key}PartialCount`] = payload[key].length;
    payload[`${key}TotalCount`] = JSON.parse(raw).length;
    return payload;
}
function normalizeSearchScope(value, allowed, fallbackValue) {
    const token = normalizeToken(value || fallbackValue);
    if (!allowed.includes(token)) {
        throw new Error(`Unsupported scope: ${value}`);
    }
    return token;
}
function normalizeInputFile(path, parameterName) {
    const classes = getCommonClasses();
    const file = new classes.File(path);
    requireExistingRegularFile(file, parameterName);
    return file;
}
function defaultDecodeOutputDir(inputApkPath) {
    const trimmed = asText(inputApkPath).trim();
    if (!trimmed) {
        throw new Error("input_apk_path must not be blank");
    }
    if (trimmed.toLowerCase().endsWith(".apk")) {
        return trimmed.slice(0, -4).trim();
    }
    return `${trimmed}.out`;
}
async function decodeApkInternal(inputApkPath, outputDir, params) {
    // Order matters: apktool joins the ClassLoader chain first, then the helper is
    // (re)loaded on top of it so brut.androlib.* becomes resolvable from the helper.
    const runtime = await ensureApktoolRuntimeLoaded();
    const frameworkJarPath = await ensureFrameworkJarPath();
    await ensureHelperRuntimeLoaded("apktool");
    const helper = await callHelperFacade("decodeApk", (Facade) => Facade.decodeApk(inputApkPath, outputDir, frameworkJarPath, APKTOOL_VERSION, normalizeJobs(params, "jobs"), optionalText(params, "frame_path") || "", optionalText(params, "frame_tag") || "", optionalBoolean(params, "force", false), optionalBoolean(params, "no_src", false), optionalBoolean(params, "no_res", false), optionalBoolean(params, "only_manifest", false), optionalBoolean(params, "no_assets", false), optionalBoolean(params, "verbose", false), optionalBoolean(params, "quiet", false)), "apktool");
    return {
        runtime,
        frameworkInfo: helper.payload.frameworkInfo,
        appliedConfig: helper.payload.appliedConfig,
        inputApkPath: helper.payload.inputApkPath,
        outputDir: helper.payload.outputDir
    };
}
async function buildApkInternal(decodedDir, outputApkPath, params) {
    const runtime = await ensureApktoolRuntimeLoaded();
    const frameworkJarPath = await ensureFrameworkJarPath();
    await ensureHelperRuntimeLoaded("apktool_build");
    const helper = await callHelperFacade("buildApk", (Facade) => Facade.buildApk(decodedDir, outputApkPath, frameworkJarPath, APKTOOL_VERSION, normalizeJobs(params, "jobs"), optionalText(params, "frame_path") || "", optionalText(params, "frame_tag") || "", optionalBoolean(params, "force", false), optionalBoolean(params, "verbose", false), optionalBoolean(params, "quiet", false)), "apktool_build");
    return {
        runtime,
        frameworkInfo: helper.payload.frameworkInfo,
        appliedConfig: helper.payload.appliedConfig,
        decodedDir: helper.payload.decodedDir,
        outputApkPath: helper.payload.outputApkPath
    };
}
function baseSuccessPayload(runtimeExtras) {
    return {
        success: true,
        packageName: "apk_reverse",
        packageVersion: PACKAGE_VERSION,
        apktoolVersion: APKTOOL_VERSION,
        jadxVersion: JADX_VERSION,
        apktoolRuntimeSourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT,
        jvmCompatibilitySystemProperties: collectJvmCompatibilitySystemProperties(),
        ...runtimeExtras
    };
}
function baseFailurePayload(operation, error, context) {
    const detail = describeError(error);
    const parts = [];
    parts.push("[" + operation + "] " + (detail.errorType || "Error") + ": "
        + (detail.message || "unknown error"));
    if (detail.cause) {
        parts.push("caused by: " + detail.cause);
    }
    if (context) {
        const contextText = safeStringify(context);
        if (contextText) {
            parts.push("context: " + contextText);
        }
    }
    if (heavyOperationActive) {
        parts.push("activeHeavyOperation: " + heavyOperationActive);
    }
    parts.push("runtimeChain: " + (currentRuntimeChainSignature() || "(empty)"));
    if (detail.stack) {
        parts.push("stack:\n" + detail.stack);
    }
    const combined = parts.join(" | ");
    return {
        success: false,
        operation,
        packageName: "apk_reverse",
        packageVersion: PACKAGE_VERSION,
        apktoolVersion: APKTOOL_VERSION,
        jadxVersion: JADX_VERSION,
        // `message` is the field the host reads for its own error surface.
        message: combined,
        error: combined,
        errorType: detail.errorType,
        errorMessage: detail.message,
        errorCause: detail.cause,
        errorStack: detail.stack,
        errorRaw: detail.raw,
        activeHeavyOperation: heavyOperationActive,
        context: context || null
    };
}
async function usage_advice() {
    try {
        return buildUsageAdvice();
    }
    catch (error) {
        return baseFailurePayload("usage_advice", error);
    }
}
function buildUsageAdvice() {
    const apktoolRuntimeLoaded = apktoolRuntimePromise !== null;
    const jadxRuntimeLoaded = jadxRuntimePromise !== null;
    const helperRuntimeLoaded = helperRuntimePromise !== null;
    return {
        success: true,
        packageName: "apk_reverse",
        packageVersion: PACKAGE_VERSION,
        apktoolVersion: APKTOOL_VERSION,
        jadxVersion: JADX_VERSION,
        runtimeLoadMode: "ToolPkg.readResource + Java.loadJar(childFirstPrefixes=...) with chain-aware helper reload",
        loadedRuntimeChain: loadedRuntimeChain.slice(),
        helperRuntimeSignature,
        activeHeavyOperation: heavyOperationActive,
        jobsRange: { min: MIN_JOBS, max: MAX_JOBS, default: DEFAULT_JOBS },
        maxResultsRange: { min: MIN_MAX_RESULTS, max: HARD_MAX_RESULTS, default: DEFAULT_MAX_RESULTS },
        runtimeResources: {
            apktool: {
                resourceKey: APKTOOL_RUNTIME_RESOURCE_KEY,
                sourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT,
                loaded: apktoolRuntimeLoaded
            },
            jadx: {
                resourceKey: JADX_RUNTIME_RESOURCE_KEY,
                sourceArtifact: JADX_RUNTIME_SOURCE_ARTIFACT,
                loaded: jadxRuntimeLoaded
            },
            helper: {
                resourceKey: HELPER_RUNTIME_RESOURCE_KEY,
                loaded: helperRuntimeLoaded
            }
        },
        supportedCommands: [
            "apk_reverse_selftest",
            "apk_reverse_inspect",
            "apk_reverse_decode",
            "apk_reverse_jadx",
            "apk_reverse_search_text",
            "apk_reverse_search_address",
            "apk_reverse_build",
            "apk_reverse_sign",
            "apk_reverse_build_and_sign"
        ],
        notes: [
            "Primary APKTool and JADX flows are implemented through helper-backed Java bridge calls and bundled dex-jar resources.",
            "JADX decompilation now runs through the helper runtime so Android-specific compatibility stays in Java.",
            "Helper-backed bridge calls reload the helper jar per invocation so apktool and JADX classloader chains stay valid within a shared JS session.",
            "JADX runtime and helper runtime are expected to be produced by build_runtime_android_resources.ps1 before packaging.",
            "Search results larger than the inline limit are persisted into a temp JSON file and returned by path.",
            "decode, build and jadx are serialized through a single exclusive queue so their heap peaks never overlap in one process.",
            "JADX runs with jadx.api.impl.NoOpCodeCache, saves resources first, then streams sources one class at a time and unloads each class right after writing it.",
            "jobs is clamped to 1-16 and defaults to 1; raise it only for small or medium APKs.",
            "apk_reverse_jadx runs isolated by default (isolated=true): JADX executes in a separate app_process with its own heap, so a child OOM returns a structured failure instead of crashing the host app.",
            "Read-only dex copies for the isolated child are staged in private app storage, never in the output directory, because shared storage is FUSE-backed and cannot hold read-only dex files.",
            "If the isolated run fails, do not retry with isolated=false on a large APK: in-process JADX shares the host heap and has been measured to make the host drop frames and crash."
        ]
    };
}
async function apk_reverse_decode(params) {
    try {
        const inputApkPath = requireText(params, "input_apk_path");
        const outputDir = requireText(params, "output_dir");
        const result = await runExclusiveHeavyOperation("decode", () => decodeApkInternal(inputApkPath, outputDir, params || {}));
        return {
            ...baseSuccessPayload({
                runtimeJarPath: result.runtime.runtimeJarPath,
                loadInfo: result.runtime.loadInfo
            }),
            operation: "decode",
            inputApkPath: result.inputApkPath,
            outputDir: result.outputDir,
            frameworkInfo: result.frameworkInfo,
            appliedConfig: result.appliedConfig
        };
    }
    catch (error) {
        return baseFailurePayload("decode", error, { input_apk_path: asText(params && params.input_apk_path), output_dir: asText(params && params.output_dir) });
    }
}
async function apk_reverse_build(params) {
    try {
        const decodedDir = requireText(params, "decoded_dir");
        const outputApkPath = requireText(params, "output_apk_path");
        const result = await runExclusiveHeavyOperation("build", () => buildApkInternal(decodedDir, outputApkPath, params || {}));
        return {
            ...baseSuccessPayload({
                runtimeJarPath: result.runtime.runtimeJarPath,
                loadInfo: result.runtime.loadInfo
            }),
            operation: "build",
            decodedDir: result.decodedDir,
            outputApkPath: result.outputApkPath,
            frameworkInfo: result.frameworkInfo,
            appliedConfig: result.appliedConfig
        };
    }
    catch (error) {
        return baseFailurePayload("build", error, { decoded_dir: asText(params && params.decoded_dir), output_apk_path: asText(params && params.output_apk_path) });
    }
}
async function apk_reverse_selftest(params) {
    const stages = [];
    async function probe(name, action) {
        const startedAt = Date.now();
        try {
            const value = await action();
            stages.push({
                stage: name,
                ok: true,
                elapsedMs: Date.now() - startedAt,
                detail: value === undefined ? "" : safeStringify(value).slice(0, 600)
            });
            return { ok: true, value };
        }
        catch (error) {
            const detail = describeError(error);
            stages.push({
                stage: name,
                ok: false,
                elapsedMs: Date.now() - startedAt,
                errorType: detail.errorType,
                errorMessage: detail.message,
                errorCause: detail.cause,
                errorStack: detail.stack ? detail.stack.slice(0, 1200) : "",
                errorRaw: detail.raw ? detail.raw.slice(0, 600) : ""
            });
            return { ok: false, error: error };
        }
    }
    await probe("jvm_compat_properties", () => {
        ensureJvmCompatibilitySystemProperties();
        return collectJvmCompatibilitySystemProperties();
    });
    const helperResource = await probe("read_resource_helper", () => ToolPkg.readResource(HELPER_RUNTIME_RESOURCE_KEY, HELPER_RUNTIME_OUTPUT_FILE_NAME, true));
    const apktoolResource = await probe("read_resource_apktool", () => ToolPkg.readResource(APKTOOL_RUNTIME_RESOURCE_KEY, APKTOOL_RUNTIME_OUTPUT_FILE_NAME, true));
    const jadxResource = await probe("read_resource_jadx", () => ToolPkg.readResource(JADX_RUNTIME_RESOURCE_KEY, JADX_RUNTIME_OUTPUT_FILE_NAME, true));
    await probe("read_resource_framework", () => ToolPkg.readResource(APKTOOL_ANDROID_FRAMEWORK_RESOURCE_KEY, APKTOOL_ANDROID_FRAMEWORK_OUTPUT_FILE_NAME, true));
    if (helperResource.ok) {
        await probe("load_jar_helper", async () => {
            const runtime = await ensureHelperRuntimeLoaded("selftest");
            return { runtimeJarPath: runtime.runtimeJarPath, loadInfo: runtime.loadInfo };
        });
        await probe("java_type_helper_facade", () => {
            const classes = getHelperBridgeClasses();
            return asText(classes.ApkReverseHelperFacade);
        });
    }
    if (apktoolResource.ok) {
        await probe("load_jar_apktool", async () => {
            const runtime = await ensureApktoolRuntimeLoaded();
            return { runtimeJarPath: runtime.runtimeJarPath, loadInfo: runtime.loadInfo };
        });
    }
    if (jadxResource.ok) {
        await probe("load_jar_jadx", async () => {
            const runtime = await ensureJadxRuntimeLoaded();
            return { runtimeJarPath: runtime.runtimeJarPath, loadInfo: runtime.loadInfo };
        });
        await probe("java_type_jadx_args", () => asText(Java.type("jadx.api.JadxArgs")));
        await probe("java_type_jadx_noop_cache", () => asText(Java.type("jadx.api.impl.NoOpCodeCache")));
    }
    const probeApkPath = optionalText(params, "input_apk_path");
    if (probeApkPath) {
        await probe("helper_call_inspect", async () => {
            const helper = await callHelperFacade("inspectApk", (Facade) => Facade.inspectApk(probeApkPath), "selftest_inspect");
            return {
                helperRuntimeJarPath: helper.runtime.runtimeJarPath,
                payloadKeys: Object.keys(helper.payload || {})
            };
        });
    }
    const failed = stages.filter((entry) => !entry.ok);
    const summary = failed.length === 0
        ? "all stages passed"
        : `first failing stage: ${failed[0].stage} -> ${failed[0].errorType}: ${failed[0].errorMessage}`;
    return {
        success: failed.length === 0,
        operation: "selftest",
        packageName: "apk_reverse",
        packageVersion: PACKAGE_VERSION,
        message: summary,
        error: failed.length === 0 ? "" : summary,
        stageCount: stages.length,
        failedStageCount: failed.length,
        firstFailedStage: failed.length === 0 ? "" : failed[0].stage,
        stages,
        probedApkPath: probeApkPath || ""
    };
}
async function apk_reverse_inspect(params) {
    try {
        const inputApkPath = requireText(params, "input_apk_path");
        const helper = await callHelperFacade("inspectApk", (Facade) => Facade.inspectApk(inputApkPath));
        return {
            ...baseSuccessPayload({
                helperRuntimeJarPath: helper.runtime.runtimeJarPath,
                helperLoadInfo: helper.runtime.loadInfo
            }),
            operation: "inspect",
            ...helper.payload
        };
    }
    catch (error) {
        return baseFailurePayload("inspect", error, { input_apk_path: asText(params && params.input_apk_path) });
    }
}
async function apk_reverse_jadx(params) {
    try {
        const inputApkPath = requireText(params, "input_apk_path");
        const outputDir = requireText(params, "output_dir");
        const jadxJobs = normalizeJobs(params, "jobs");
        const useIsolatedProcess = optionalBoolean(params, "isolated", true);
        const jadxRuntime = await ensureJadxRuntimeLoaded();
        const deobfEnabled = optionalBoolean(params, "deobf", false);
        const showInconsistent = optionalBoolean(params, "show_inconsistent_code", false);
        const helper = await runExclusiveHeavyOperation(useIsolatedProcess ? "jadx_isolated" : "jadx", async () => {
            if (!useIsolatedProcess) {
                return callHelperFacade("decompileJadx", (Facade) => Facade.decompileJadx(inputApkPath, outputDir, jadxJobs, deobfEnabled, showInconsistent), "jadx");
            }
            const helperRuntime = await ensureHelperRuntimeLoaded("jadx_isolated");
            return callHelperFacade("decompileJadxIsolated", (Facade) => Facade.decompileJadxIsolated(inputApkPath, outputDir, jadxJobs, deobfEnabled, showInconsistent, helperRuntime.runtimeJarPath, jadxRuntime.runtimeJarPath, outputDir), "jadx_isolated");
        });
        return {
            ...baseSuccessPayload({
                jadxRuntimeJarPath: jadxRuntime.runtimeJarPath,
                jadxLoadInfo: jadxRuntime.loadInfo,
                helperRuntimeJarPath: helper.runtime.runtimeJarPath,
                helperLoadInfo: helper.runtime.loadInfo
            }),
            operation: "jadx",
            jobs: jadxJobs,
            isolated: useIsolatedProcess,
            ...helper.payload
        };
    }
    catch (error) {
        const isolatedRequested = optionalBoolean(params, "isolated", true);
        const payload = baseFailurePayload("jadx", error, {
            input_apk_path: asText(params && params.input_apk_path),
            output_dir: asText(params && params.output_dir),
            jobs: asText(params && params.jobs),
            isolated: isolatedRequested
        });
        if (isolatedRequested) {
            payload.isolatedFailed = true;
            payload.doNotFallBackInProcess = true;
            payload.recoveryAdvice = "Isolated JADX failed. Do NOT retry with isolated=false on a "
                + "large APK: in-process JADX shares the host heap and has been measured to make "
                + "the host app drop frames and crash. Fix the isolated failure reported above, or "
                + "retry isolated after restarting the app.";
            payload.message = payload.message + " | " + payload.recoveryAdvice;
            payload.error = payload.message;
        }
        return payload;
    }
}
async function apk_reverse_search_text(params) {
    try {
        const inputPath = requireText(params, "input_path");
        const query = requireText(params, "query");
        const scope = normalizeSearchScope(optionalText(params, "scope") || "all", ["manifest", "res", "smali", "jadx", "native_strings", "all"], "all");
        const regexEnabled = optionalBoolean(params, "regex", false);
        const caseInsensitive = optionalBoolean(params, "case_insensitive", true);
        const maxResults = clampMaxResults(optionalInteger(params, "max_results", DEFAULT_MAX_RESULTS));
        const helper = await callHelperFacade("searchText", (Facade) => Facade.searchText(inputPath, query, scope, regexEnabled, caseInsensitive, maxResults));
        const payload = {
            ...baseSuccessPayload({
                helperRuntimeJarPath: helper.runtime.runtimeJarPath,
                helperLoadInfo: helper.runtime.loadInfo
            }),
            operation: "search_text",
            inputPath,
            scope,
            regex: regexEnabled,
            caseInsensitive,
            maxResults,
            matchCount: optionalInteger(helper.payload, "matchCount", 0),
            matches: Array.isArray(helper.payload.matches) ? helper.payload.matches.slice(0, maxResults) : []
        };
        return maybePersistLargeField(payload, "matches", "search_text_matches");
    }
    catch (error) {
        return baseFailurePayload("search_text", error, { input_path: asText(params && params.input_path) });
    }
}
async function apk_reverse_search_address(params) {
    try {
        const inputPath = requireText(params, "input_path");
        const query = requireText(params, "query");
        const scope = normalizeSearchScope(optionalText(params, "scope") || "all", ["resource_id", "smali_ref", "jadx_ref", "native_symbol", "native_offset", "hex_bytes", "all"], "all");
        const maxResults = clampMaxResults(optionalInteger(params, "max_results", DEFAULT_MAX_RESULTS));
        const helper = await callHelperFacade("searchAddress", (Facade) => Facade.searchAddress(inputPath, query, scope, maxResults));
        const payload = {
            ...baseSuccessPayload({
                helperRuntimeJarPath: helper.runtime.runtimeJarPath,
                helperLoadInfo: helper.runtime.loadInfo
            }),
            operation: "search_address",
            inputPath,
            scope,
            maxResults,
            matchCount: optionalInteger(helper.payload, "matchCount", 0),
            matches: Array.isArray(helper.payload.matches) ? helper.payload.matches : []
        };
        return maybePersistLargeField(payload, "matches", "search_address_matches");
    }
    catch (error) {
        return baseFailurePayload("search_address", error, { input_path: asText(params && params.input_path) });
    }
}
async function signApkInternal(inputApkPath, outputApkPath, params) {
    const inputApkFile = normalizeInputFile(inputApkPath, "input_apk_path");
    const helper = await callHelperFacade("signApk", (Facade) => Facade.signApk(getApplicationContext(), asText(inputApkFile.getAbsolutePath()), outputApkPath, requireText(params, "sign_mode"), optionalText(params, "keystore_path") || "", optionalText(params, "storepass") || "", optionalText(params, "alias") || "", optionalText(params, "keypass") || "", 0));
    return {
        helperRuntimeJarPath: helper.runtime.runtimeJarPath,
        helperLoadInfo: helper.runtime.loadInfo,
        ...helper.payload
    };
}
async function apk_reverse_sign(params) {
    try {
        const inputApkPath = requireText(params, "input_apk_path");
        const outputApkPath = requireText(params, "output_apk_path");
        const result = await signApkInternal(inputApkPath, outputApkPath, params || {});
        return {
            ...baseSuccessPayload({
                helperRuntimeJarPath: result.helperRuntimeJarPath,
                helperLoadInfo: result.helperLoadInfo
            }),
            operation: "sign",
            ...result
        };
    }
    catch (error) {
        return baseFailurePayload("sign", error, { input_apk_path: asText(params && params.input_apk_path), sign_mode: asText(params && params.sign_mode) });
    }
}
async function apk_reverse_build_and_sign(params) {
    let unsignedFile = null;
    try {
        const decodedDir = requireText(params, "decoded_dir");
        const outputApkPath = requireText(params, "output_apk_path");
        unsignedFile = createTempFile("unsigned_build", ".apk");
        const buildResult = await buildApkInternal(decodedDir, asText(unsignedFile.getAbsolutePath()), params || {});
        const signResult = await signApkInternal(asText(unsignedFile.getAbsolutePath()), outputApkPath, params || {});
        return {
            ...baseSuccessPayload({
                runtimeJarPath: buildResult.runtime.runtimeJarPath,
                loadInfo: buildResult.runtime.loadInfo,
                helperRuntimeJarPath: signResult.helperRuntimeJarPath,
                helperLoadInfo: signResult.helperLoadInfo
            }),
            operation: "build_and_sign",
            decodedDir: buildResult.decodedDir,
            unsignedApkPath: buildResult.outputApkPath,
            outputApkPath: signResult.outputApkPath,
            signMode: signResult.signMode,
            alias: signResult.alias,
            keystorePath: signResult.keystorePath,
            frameworkInfo: buildResult.frameworkInfo,
            appliedConfig: buildResult.appliedConfig
        };
    }
    catch (error) {
        return baseFailurePayload("build_and_sign", error, { decoded_dir: asText(params && params.decoded_dir), output_apk_path: asText(params && params.output_apk_path) });
    }
    finally {
        if (unsignedFile) {
            const parent = unsignedFile.getParentFile();
            deleteRecursively(parent);
        }
    }
}

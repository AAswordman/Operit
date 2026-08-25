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
        "zh": "基于内置 dex-jar 运行时的 APK 逆向工具包，提供 inspect/decode/jadx/build/sign/search 能力。",
        "en": "APK reverse toolkit backed by bundled dex-jar runtimes, providing inspect/decode/jadx/build/sign/search capabilities."
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
                { "name": "frame_tag", "description": { "zh": "自定义 framework tag。", "en": "Custom framework tag." }, "type": "string", "required": false }
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
                { "name": "deobf", "description": { "zh": "是否开启 deobfuscation。", "en": "Whether to enable deobfuscation." }, "type": "string", "required": false },
                { "name": "show_inconsistent_code", "description": { "zh": "是否显示不一致代码。", "en": "Whether to show inconsistent code." }, "type": "string", "required": false }
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
exports.apk_reverse_inspect = apk_reverse_inspect;
exports.apk_reverse_decode = apk_reverse_decode;
exports.apk_reverse_jadx = apk_reverse_jadx;
exports.apk_reverse_search_text = apk_reverse_search_text;
exports.apk_reverse_search_address = apk_reverse_search_address;
exports.apk_reverse_build = apk_reverse_build;
exports.apk_reverse_sign = apk_reverse_sign;
exports.apk_reverse_build_and_sign = apk_reverse_build_and_sign;
exports.ensureHelperRuntimeLoaded = ensureHelperRuntimeLoaded;
const PACKAGE_VERSION = "1.0.3";
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
const MAX_SEARCH_RESULTS = 1000;
const DEFAULT_APKTOOL_JOBS = 1;
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
// Helper runtime paths are stable per backend slot so Java.loadJar can reuse classloaders.
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
    if (error instanceof Error) {
        return error.message || String(error);
    }
    return asText(error) || "unknown error";
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
        throw new Error(`${missingMessage}: ${toErrorText(error)}`);
    }
    const loadInfo = Java.loadJar(runtimeJarPath, {
        childFirstPrefixes
    });
    return {
        runtimeJarPath,
        loadInfo
    };
}
async function ensureApktoolRuntimeLoaded() {
    const runtime = await loadDexJarRuntime(APKTOOL_RUNTIME_RESOURCE_KEY, APKTOOL_RUNTIME_OUTPUT_FILE_NAME, APKTOOL_RUNTIME_CHILD_FIRST_PREFIXES, "Missing bundled apktool runtime resource");
    return {
        ...runtime,
        sourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT
    };
}
async function ensureJadxRuntimeLoaded() {
    const runtime = await loadDexJarRuntime(JADX_RUNTIME_RESOURCE_KEY, JADX_RUNTIME_OUTPUT_FILE_NAME, JADX_RUNTIME_CHILD_FIRST_PREFIXES, "Missing bundled JADX runtime resource. Run build_runtime_android_resources.ps1 and regenerate the toolpkg resources");
    return {
        ...runtime,
        sourceArtifact: JADX_RUNTIME_SOURCE_ARTIFACT
    };
}
async function ensureHelperRuntimeLoaded(tag) {
    const outputFileName = nextHelperRuntimeOutputFileName(tag);
    const runtime = await loadDexJarRuntime(HELPER_RUNTIME_RESOURCE_KEY, outputFileName, HELPER_RUNTIME_CHILD_FIRST_PREFIXES, "Missing bundled APK reverse helper runtime resource. Run build_runtime_android_resources.ps1 and regenerate the toolpkg resources");
    return runtime;
}
function nextHelperRuntimeOutputFileName(tag) {
    const normalizedTag = asText(tag).trim().toLowerCase().replace(/[^a-z0-9]+/g, "_") || "helper";
    const slot = normalizedTag === "apktool" || normalizedTag === "apktool_build"
        ? "apktool"
        : normalizedTag === "jadx"
            ? "jadx"
            : "helper";
    const extensionIndex = HELPER_RUNTIME_OUTPUT_FILE_NAME.lastIndexOf(".");
    const suffix = `${slot}-runtime`;
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
    const runtime = await ensureHelperRuntimeLoaded(helperLoadTag || "helper");
    const classes = getHelperBridgeClasses();
    const raw = invoke(classes.ApkReverseHelperFacade);
    const text = asText(raw).trim();
    if (!text) {
        throw new Error(`Helper runtime returned empty payload for ${methodName}`);
    }
    let parsed;
    try {
        parsed = JSON.parse(text);
    }
    catch (error) {
        throw new Error(`Failed to parse helper runtime payload for ${methodName}: ${toErrorText(error)}`);
    }
    return {
        runtime,
        payload: parsed
    };
}
async function ensureFrameworkJarPath() {
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
function boundedMaxResults(params) {
    const requested = optionalInteger(params, "max_results", DEFAULT_MAX_RESULTS);
    return requested > 0 ? Math.min(requested, MAX_SEARCH_RESULTS) : DEFAULT_MAX_RESULTS;
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
    const runtime = await ensureApktoolRuntimeLoaded();
    const frameworkJarPath = await ensureFrameworkJarPath();
    const helper = await callHelperFacade("decodeApk", (Facade) => Facade.decodeApk(inputApkPath, outputDir, frameworkJarPath, APKTOOL_VERSION, optionalInteger(params, "jobs", DEFAULT_APKTOOL_JOBS), optionalText(params, "frame_path") || "", optionalText(params, "frame_tag") || "", optionalBoolean(params, "force", false), optionalBoolean(params, "no_src", false), optionalBoolean(params, "no_res", false), optionalBoolean(params, "only_manifest", false), optionalBoolean(params, "no_assets", false), optionalBoolean(params, "verbose", false), optionalBoolean(params, "quiet", false)), "apktool");
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
    const helper = await callHelperFacade("buildApk", (Facade) => Facade.buildApk(decodedDir, outputApkPath, frameworkJarPath, APKTOOL_VERSION, optionalInteger(params, "jobs", DEFAULT_APKTOOL_JOBS), optionalText(params, "frame_path") || "", optionalText(params, "frame_tag") || "", optionalBoolean(params, "force", false), optionalBoolean(params, "verbose", false), optionalBoolean(params, "quiet", false)), "apktool");
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
function baseFailurePayload(operation, error) {
    return {
        success: false,
        operation,
        packageName: "apk_reverse",
        packageVersion: PACKAGE_VERSION,
        apktoolVersion: APKTOOL_VERSION,
        jadxVersion: JADX_VERSION,
        error: toErrorText(error)
    };
}
async function usage_advice() {
    const hasApktoolRuntime = await isBundledResourceAvailable(APKTOOL_RUNTIME_RESOURCE_KEY, APKTOOL_RUNTIME_OUTPUT_FILE_NAME);
    const hasJadxRuntime = await isBundledResourceAvailable(JADX_RUNTIME_RESOURCE_KEY, JADX_RUNTIME_OUTPUT_FILE_NAME);
    const hasHelperRuntime = await isBundledResourceAvailable(HELPER_RUNTIME_RESOURCE_KEY, HELPER_RUNTIME_OUTPUT_FILE_NAME);
    return {
        success: true,
        packageName: "apk_reverse",
        packageVersion: PACKAGE_VERSION,
        apktoolVersion: APKTOOL_VERSION,
        jadxVersion: JADX_VERSION,
        runtimeLoadMode: "ToolPkg.readResource + Java.loadJar(childFirstPrefixes=...)",
        runtimeResources: {
            apktool: {
                resourceKey: APKTOOL_RUNTIME_RESOURCE_KEY,
                sourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT,
                bundled: hasApktoolRuntime
            },
            jadx: {
                resourceKey: JADX_RUNTIME_RESOURCE_KEY,
                sourceArtifact: JADX_RUNTIME_SOURCE_ARTIFACT,
                bundled: hasJadxRuntime
            },
            helper: {
                resourceKey: HELPER_RUNTIME_RESOURCE_KEY,
                bundled: hasHelperRuntime
            }
        },
        supportedCommands: [
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
            "Helper-backed bridge calls use bounded helper runtime slots so apktool and JADX classloader chains remain valid within a shared JS session.",
            "JADX runtime and helper runtime are expected to be produced by build_runtime_android_resources.ps1 before packaging.",
            "Search results larger than the inline limit are persisted into a temp JSON file and returned by path."
        ]
    };
}
async function apk_reverse_decode(params) {
    try {
        const inputApkPath = requireText(params, "input_apk_path");
        const outputDir = requireText(params, "output_dir");
        const result = await decodeApkInternal(inputApkPath, outputDir, params || {});
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
        return baseFailurePayload("decode", error);
    }
}
async function apk_reverse_build(params) {
    try {
        const decodedDir = requireText(params, "decoded_dir");
        const outputApkPath = requireText(params, "output_apk_path");
        const result = await buildApkInternal(decodedDir, outputApkPath, params || {});
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
        return baseFailurePayload("build", error);
    }
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
        return baseFailurePayload("inspect", error);
    }
}
async function apk_reverse_jadx(params) {
    try {
        const inputApkPath = requireText(params, "input_apk_path");
        const outputDir = requireText(params, "output_dir");
        const jadxRuntime = await ensureJadxRuntimeLoaded();
        const helper = await callHelperFacade("decompileJadx", (Facade) => Facade.decompileJadx(inputApkPath, outputDir, optionalInteger(params, "jobs", 1), optionalBoolean(params, "deobf", false), optionalBoolean(params, "show_inconsistent_code", false)), "jadx");
        return {
            ...baseSuccessPayload({
                jadxRuntimeJarPath: jadxRuntime.runtimeJarPath,
                jadxLoadInfo: jadxRuntime.loadInfo,
                helperRuntimeJarPath: helper.runtime.runtimeJarPath,
                helperLoadInfo: helper.runtime.loadInfo
            }),
            operation: "jadx",
            ...helper.payload
        };
    }
    catch (error) {
        return baseFailurePayload("jadx", error);
    }
}
async function apk_reverse_search_text(params) {
    try {
        const inputPath = requireText(params, "input_path");
        const query = requireText(params, "query");
        const scope = normalizeSearchScope(optionalText(params, "scope") || "all", ["manifest", "res", "smali", "jadx", "native_strings", "all"], "all");
        const regexEnabled = optionalBoolean(params, "regex", false);
        const caseInsensitive = optionalBoolean(params, "case_insensitive", true);
        const maxResults = boundedMaxResults(params);
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
        return baseFailurePayload("search_text", error);
    }
}
async function apk_reverse_search_address(params) {
    try {
        const inputPath = requireText(params, "input_path");
        const query = requireText(params, "query");
        const scope = normalizeSearchScope(optionalText(params, "scope") || "all", ["resource_id", "smali_ref", "jadx_ref", "native_symbol", "native_offset", "hex_bytes", "all"], "all");
        const maxResults = boundedMaxResults(params);
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
        return baseFailurePayload("search_address", error);
    }
}
async function signApkInternal(inputApkPath, outputApkPath, params, helperLoadTag = "helper") {
    const inputApkFile = normalizeInputFile(inputApkPath, "input_apk_path");
    const helper = await callHelperFacade("signApk", (Facade) => Facade.signApk(getApplicationContext(), asText(inputApkFile.getAbsolutePath()), outputApkPath, requireText(params, "sign_mode"), optionalText(params, "keystore_path") || "", optionalText(params, "storepass") || "", optionalText(params, "alias") || "", optionalText(params, "keypass") || "", 0), helperLoadTag);
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
        return baseFailurePayload("sign", error);
    }
}
async function apk_reverse_build_and_sign(params) {
    let unsignedFile = null;
    try {
        const decodedDir = requireText(params, "decoded_dir");
        const outputApkPath = requireText(params, "output_apk_path");
        unsignedFile = createTempFile("unsigned_build", ".apk");
        const buildResult = await buildApkInternal(decodedDir, asText(unsignedFile.getAbsolutePath()), params || {});
        const signResult = await signApkInternal(asText(unsignedFile.getAbsolutePath()), outputApkPath, params || {}, "apktool");
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
        return baseFailurePayload("build_and_sign", error);
    }
    finally {
        if (unsignedFile) {
            const parent = unsignedFile.getParentFile();
            deleteRecursively(parent);
        }
    }
}

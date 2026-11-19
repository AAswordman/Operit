#include <jni.h>

#include <sys/auxv.h>
// [PATCH 0017] 运行时自证身份 + 设备 CPU 特性探测。
// 背景：0015 是纯编译期修改（-march=armv8.2-a+dotprod），APK 外观
//       （versionName）不变，用户装没装对新包无法从日志判断。
//       这里在 session 创建时打印：
//         1. 构建标签（CMake 注入 OPERIT_BUILD_TAG，每个补丁版唯一）
//         2. 内核上报的 hwcap：dotprod / i8mm / fp16 是否可用
//       若日志出现 [operit-build cpu11] 即可确认新包已生效。
#ifndef OPERIT_BUILD_TAG
#define OPERIT_BUILD_TAG "unknown"
#endif

// [PATCH 0025 / gpu7] OpenCL 构建标记。CMake 在 TARGET ggml-opencl 存在时注入
// OPERIT_HAS_OPENCL=1；CPU-only 构建不注入。用于让日志自证「这个 APK
// 到底有没有把 GPU 后端编进来」，避免重演 0015 那种纯编译期改动无法
// 从外观判断的验证盲区。
#ifdef OPERIT_HAS_OPENCL
#define OPERIT_OPENCL_BUILT 1
#else
#define OPERIT_OPENCL_BUILT 0
#endif
#ifndef HWCAP_ASIMDDP
#define HWCAP_ASIMDDP (1UL << 20)
#endif
#ifndef HWCAP_ASIMDHP
#define HWCAP_ASIMDHP (1UL << 10)
#endif
#ifndef HWCAP2_I8MM
#define HWCAP2_I8MM (1UL << 13)
#endif
#ifndef HWCAP2_SVE
#define HWCAP2_SVE (1UL << 1)
#endif

#include <android/log.h>
#include <cstdlib>
// [PATCH 0027 / gpu10] offload_kqv A/B 开关：access(F_OK) 探测 flag 文件，
// __system_property_get() 读 adb 侧兜底属性。两者都是 bionic 提供的基础 API。
#include <unistd.h>
#include <sys/system_properties.h>

// [PATCH 0025 / gpu7] 默认关闭 OpenCL 后端，保留已真机验证的零崩溃纯 CPU 基线。
// gpu5/gup6 的「库加载即设 GGML_DISABLE_*=1」在 18:59 native 日志验证了
// 4 分钟 / 7 轮推理 / crash buffer 为空。gpu7 沿用这个设计，只把后端从
// Vulkan 换成 OpenCL（Vulkan 已由 gpu6 实测「注册可用、执行不可用」）。
// GGML_DISABLE_VULKAN=1 一并保留作双保险：即使意外残留 libggml-vulkan.so，
// 也不会被 load_best("vulkan") dlopen。
// 进程级一次性激活见 operitActivateOpenclBackend()：仅 n_gpu_layers>0 时
// unsetenv("GGML_DISABLE_OPENCL") + ggml_backend_load_all() 追加注册。
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    setenv("GGML_DISABLE_OPENCL", "1", 1);
    setenv("GGML_DISABLE_VULKAN", "1", 1);
    __android_log_print(ANDROID_LOG_INFO, "llama.cpp",
                        "[gpu7 gate] JNI_OnLoad: CPU-only baseline kept (DISABLE_OPENCL=1, "
                        "DISABLE_VULKAN=1); OpenCL activated ONCE process-wide when GPU layers > 0");
    return JNI_VERSION_1_6;
}

// [PATCH 0019] 后端设备枚举：运行时打印 llama.cpp 实际注册了哪些后端，
// 用于确认 Vulkan 是否真的被发现（而不只是"编进去了"）。
#include "ggml-backend.h"

#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#if defined(OPERIT_HAS_LLAMA_CPP) && OPERIT_HAS_LLAMA_CPP
#include "chat.h"
#include "llama.h"
#include "nlohmann/json.hpp"
#include <cstdlib>
#include <ctime>
#include <algorithm>
#include <exception>

// [PATCH 0025 / gpu7] OpenCL 安全初始化 + 信号防护（替换 gpu2/PATCH 0020 的 Vulkan 版）。
//
// gpu7 彻底不编 Vulkan，改走 llama.cpp 官方 OpenCL 后端（Adreno 专用 kernels）。
// 沿用 gpu6 的进程级 std::call_once 决策：JNI_OnLoad 默认 DISABLE_OPENCL=1，
// 仅 layers>0 时一次性激活。这里提供两件事：
//   1) operitOpenclProbeOnce()：在激活前「最保守」地探测 libOpenCL.so 能否
//      dlopen、ICD Loader 能否枚举到高通 platform/device。全部用 dlsym +
//      手写函数签名，不依赖任何 OpenCL 头文件（LlamaWrapper 不需要 CL include）。
//   2) sigsetjmp(SIGSEGV/SIGBUS) 包住探测与 supports_gpu_offload 调用，
//      驱动若在 clGetPlatformIDs / 后端注册阶段崩，恢复 DISABLE_OPENCL=1
//      并回退纯 CPU，进程不拉崩。
// 注意：OpenCL 侧的失败多为 clGetPlatformIDs 返回 0 个 platform（ICD 找不到
// vendor 实现），这是「无 GPU 可用」而非崩溃，probe 会返回非 0 并静默回退。
#if defined(OPERIT_HAS_OPENCL) && defined(OPERIT_OPENCL_SAFE_INIT)
#include <dlfcn.h>
#include <setjmp.h>
#include <signal.h>

// 探测块位于底部日志宏定义之前，使用独立日志宏（tag 同为 llama.cpp）。
#define OPLOGI(...) __android_log_print(ANDROID_LOG_INFO,  "llama.cpp", __VA_ARGS__)
#define OPLOGE(...) __android_log_print(ANDROID_LOG_ERROR, "llama.cpp", __VA_ARGS__)

static sigjmp_buf g_operitGpuProbeJmp;
static struct sigaction g_operitGpuProbeOldSegv;
static struct sigaction g_operitGpuProbeOldBus;

static void operitGpuProbeCrashHandler(int sig, siginfo_t*, void*) {
    // 信号上下文里只做一件事：跳回探测现场。
    siglongjmp(g_operitGpuProbeJmp, sig == SIGBUS ? 2 : 1);
}

static void operitGpuProbeInstall() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = operitGpuProbeCrashHandler;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGSEGV, &sa, &g_operitGpuProbeOldSegv);
    sigaction(SIGBUS, &sa, &g_operitGpuProbeOldBus);
}

static void operitGpuProbeRestore() {
    sigaction(SIGSEGV, &g_operitGpuProbeOldSegv, nullptr);
    sigaction(SIGBUS, &g_operitGpuProbeOldBus, nullptr);
}

// cl_int/cl_uint 为 32 位整型，cl_platform_id/cl_device_id 为指针，
// cl_bitfield/cl_device_type 为 64 位。手写签名以避免引入 CL 头文件。
typedef int  (*operit_clGetPlatformIDs_t)(unsigned int, void **, unsigned int *);
typedef int  (*operit_clGetPlatformInfo_t)(void *, unsigned int, size_t, void *, size_t *);
typedef int  (*operit_clGetDeviceIDs_t)(void *, unsigned long, unsigned int, void **, unsigned int *);
typedef int  (*operit_clGetDeviceInfo_t)(void *, unsigned int, size_t, void *, size_t *);

static constexpr unsigned int OPERIT_CL_PLATFORM_NAME  = 0x0902;
static constexpr unsigned int OPERIT_CL_DEVICE_NAME    = 0x102B;
static constexpr unsigned long OPERIT_CL_DEVICE_TYPE_GPU = 0x4ULL;

// 返回 0 = OpenCL 可用；非 0 = 失败码。
static int operitOpenclProbeOnce() {
    void * h = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (!h) {
        OPLOGI("[gpu7 probe] step=dlopen(libOpenCL.so) result=FAIL err=%s", dlerror());
        return 1;
    }
    auto pGetPlatformIDs = (operit_clGetPlatformIDs_t) dlsym(h, "clGetPlatformIDs");
    auto pGetPlatformInfo = (operit_clGetPlatformInfo_t) dlsym(h, "clGetPlatformInfo");
    auto pGetDeviceIDs = (operit_clGetDeviceIDs_t) dlsym(h, "clGetDeviceIDs");
    auto pGetDeviceInfo = (operit_clGetDeviceInfo_t) dlsym(h, "clGetDeviceInfo");
    if (!pGetPlatformIDs || !pGetPlatformInfo) {
        OPLOGI("[gpu7 probe] step=dlsym result=FAIL (clGetPlatformIDs/Info missing)");
        return 2;
    }
    unsigned int nPlat = 0;
    int r = pGetPlatformIDs(0, nullptr, &nPlat);
    if (r != 0 || nPlat == 0) {
        OPLOGI("[gpu7 probe] step=clGetPlatformIDs result=FAIL rc=%d platforms=%u "
               "(ICD loader found no vendor ICD; check /system/vendor/Khronos/OpenCL/vendors)",
               r, nPlat);
        return 3;
    }
    std::vector<void *> plats(nPlat);
    if (pGetPlatformIDs(nPlat, plats.data(), nullptr) != 0) {
        OPLOGI("[gpu7 probe] step=clGetPlatformIDs(2nd) result=FAIL");
        return 4;
    }
    OPLOGI("[gpu7 probe] platforms=%u", nPlat);
    unsigned int nGpuDev = 0;
    for (unsigned int i = 0; i < nPlat; ++i) {
        char name[256] = {0};
        size_t nameLen = 0;
        // 关键：clGetPlatformInfo 若失败，上游 ggml_opencl_probe_devices() 的
        // CL_CHECK 会 GGML_ASSERT(0) -> abort（SIGABRT，sigsetjmp 拦不住）。
        // 这里先自己调一次，失败就直接判定不可用，避免 load_all() 走到那条路径。
        if (pGetPlatformInfo(plats[i], OPERIT_CL_PLATFORM_NAME, sizeof(name), name, &nameLen) != 0) {
            OPLOGI("[gpu7 probe] platform[%u] clGetPlatformInfo FAILED -> unsafe, abort activation", i);
            return 5;
        }
        OPLOGI("[gpu7 probe] platform[%u] name='%s'", i, name);
        if (!pGetDeviceIDs || !pGetDeviceInfo) continue;
        unsigned int nDev = 0;
        if (pGetDeviceIDs(plats[i], OPERIT_CL_DEVICE_TYPE_GPU, 0, nullptr, &nDev) != 0 || nDev == 0) {
            OPLOGI("[gpu7 probe] platform[%u] clGetDeviceIDs(GPU) FAILED/empty -> skip", i);
            continue;
        }
        std::vector<void *> devs(nDev);
        if (pGetDeviceIDs(plats[i], OPERIT_CL_DEVICE_TYPE_GPU, nDev, devs.data(), nullptr) != 0) {
            continue;
        }
        nGpuDev += nDev;
        for (unsigned int j = 0; j < nDev && j < 4; ++j) {
            char dn[256] = {0};
            size_t dnLen = 0;
            pGetDeviceInfo(devs[j], OPERIT_CL_DEVICE_NAME, sizeof(dn), dn, &dnLen);
            OPLOGI("[gpu7 probe]   gpu_device[%u] name='%s'", j, dn);
        }
    }
    // 只有真正枚举到 GPU 设备才放行；否则（ICD 找不到高通 vendor 实现、
    // 或 VNDK/linker namespace 拒绝）提前 DISABLE_OPENCL=1，让 load_best
    // 连 dlopen 都不做，彻底规避上游 CL_CHECK 的 abort 路径。
    if (nGpuDev == 0) {
        OPLOGI("[gpu7 probe] result=NO_GPU_DEVICE platforms=%u -> CPU-only fallback", nPlat);
        return 6;
    }
    OPLOGI("[gpu7 probe] result=OK platforms=%u gpu_devices=%u", nPlat, nGpuDev);
    return 0;
}

static void operitOpenclSafeGate() {
    if (getenv("GGML_DISABLE_OPENCL")) {
        OPLOGI("[gpu7 gate] GGML_DISABLE_OPENCL already set -> OpenCL skipped");
        return;
    }
    if (getenv("OPERIT_DISABLE_OPENCL")) {
        setenv("GGML_DISABLE_OPENCL", "1", 1);
        OPLOGI("[gpu7 gate] disabled via OPERIT_DISABLE_OPENCL");
        return;
    }
    operitGpuProbeInstall();
    int crashSig = sigsetjmp(g_operitGpuProbeJmp, 1);
    if (crashSig != 0) {
        setenv("GGML_DISABLE_OPENCL", "1", 1);
        operitGpuProbeRestore();
        OPLOGE("[gpu7 gate] probe CRASHED (signal=%d) -> GGML_DISABLE_OPENCL=1, "
               "falling back to CPU-only. Model loading will proceed normally.", crashSig);
        return;
    }
    const int rc = operitOpenclProbeOnce();
    operitGpuProbeRestore();
    if (rc != 0) {
        setenv("GGML_DISABLE_OPENCL", "1", 1);
        OPLOGE("[gpu7 gate] probe FAILED rc=%d -> GGML_DISABLE_OPENCL=1, CPU-only fallback", rc);
    } else {
        OPLOGI("[gpu7 gate] probe PASSED -> OpenCL backend will be registered");
    }
}

// 把信号防护覆盖到 llama_supports_gpu_offload() 本身：它内部会触发
// ggml_backend_load_all() / registry 构造，是驱动最可能崩的位置之一。
static bool operitProtectedSupportsGpuOffload() {
    operitGpuProbeInstall();
    int crashSig = sigsetjmp(g_operitGpuProbeJmp, 1);
    if (crashSig != 0) {
        operitGpuProbeRestore();
        setenv("GGML_DISABLE_OPENCL", "1", 1);
        OPLOGE("[gpu7 shield] supports_gpu_offload CRASHED (signal=%d) during backend "
               "registry init -> GGML_DISABLE_OPENCL=1, CPU-only fallback from now on.",
               crashSig);
        return false;
    }
    const bool r = llama_supports_gpu_offload();
    operitGpuProbeRestore();
    return r;
}
#else
static bool operitProtectedSupportsGpuOffload() {
    return llama_supports_gpu_offload();
}
#endif // OPERIT_HAS_OPENCL && OPERIT_OPENCL_SAFE_INIT
#include <memory>
#include <sstream>

struct ToolCallGrammarConfigNative {
    std::string grammar;
    bool lazy = false;
    std::vector<std::string> triggerPatterns;
    std::vector<llama_token> triggerTokens;
    std::string generationPrompt;
};
#endif

#define TAG "LlamaNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::string jstringToString(JNIEnv * env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char * cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string out(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return out;
}

#if defined(OPERIT_HAS_LLAMA_CPP) && OPERIT_HAS_LLAMA_CPP
static llama_sampler * createSamplerChain(
        const llama_vocab * vocab,
        float temperature,
        float topP,
        int32_t topK,
        int32_t penaltyLastN,
        float repeatPenalty,
        float frequencyPenalty,
        float presencePenalty,
        uint32_t seed,
        const ToolCallGrammarConfigNative * grammarConfig
) {
    if (topP < 0.0f) topP = 0.0f;
    if (topP > 1.0f) topP = 1.0f;
    if (topK < 0) topK = 0;
    if (penaltyLastN < -1) penaltyLastN = -1;
    if (repeatPenalty < 0.0f) repeatPenalty = 0.0f;

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler * chain = llama_sampler_chain_init(sparams);
    if (!chain) return nullptr;

    llama_sampler_chain_add(chain, llama_sampler_init_penalties(
            penaltyLastN,
            repeatPenalty,
            frequencyPenalty,
            presencePenalty
    ));

    llama_sampler_chain_add(chain, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));

    if (grammarConfig != nullptr && !grammarConfig->grammar.empty()) {
        if (vocab == nullptr) {
            llama_sampler_free(chain);
            return nullptr;
        }

        llama_sampler * grammarSampler = nullptr;
        if (grammarConfig->lazy) {
            std::vector<const char *> triggerPatternsC;
            triggerPatternsC.reserve(grammarConfig->triggerPatterns.size());
            for (const auto & pattern : grammarConfig->triggerPatterns) {
                if (!pattern.empty()) {
                    triggerPatternsC.push_back(pattern.c_str());
                }
            }

            grammarSampler = llama_sampler_init_grammar_lazy_patterns(
                vocab,
                grammarConfig->grammar.c_str(),
                "root",
                triggerPatternsC.data(),
                triggerPatternsC.size(),
                grammarConfig->triggerTokens.data(),
                grammarConfig->triggerTokens.size()
            );
        } else {
            grammarSampler = llama_sampler_init_grammar(
                vocab,
                grammarConfig->grammar.c_str(),
                "root"
            );
        }

        if (!grammarSampler) {
            llama_sampler_free(chain);
            return nullptr;
        }

        llama_sampler_chain_add(chain, grammarSampler);
    }

    llama_sampler_chain_add(chain, llama_sampler_init_dist(seed));

    return chain;
}
#endif

static jstring stringToJstring(JNIEnv * env, const std::string & str) {
    return env->NewStringUTF(str.c_str());
}

static jstring bytesUtf8ToJstring(JNIEnv * env, const std::string & bytes) {
    std::u16string out;
    out.reserve(bytes.size());

    const unsigned char * s = reinterpret_cast<const unsigned char *>(bytes.data());
    size_t i = 0;
    while (i < bytes.size()) {
        uint32_t cp = 0;
        const unsigned char c0 = s[i];

        if (c0 < 0x80) {
            cp = c0;
            i += 1;
        } else if ((c0 & 0xE0) == 0xC0 && i + 1 < bytes.size()) {
            const unsigned char c1 = s[i + 1];
            if ((c1 & 0xC0) != 0x80) {
                cp = 0xFFFD;
                i += 1;
            } else {
                cp = ((c0 & 0x1F) << 6) | (c1 & 0x3F);
                if (cp < 0x80) cp = 0xFFFD;
                i += 2;
            }
        } else if ((c0 & 0xF0) == 0xE0 && i + 2 < bytes.size()) {
            const unsigned char c1 = s[i + 1];
            const unsigned char c2 = s[i + 2];
            if (((c1 & 0xC0) != 0x80) || ((c2 & 0xC0) != 0x80)) {
                cp = 0xFFFD;
                i += 1;
            } else {
                cp = ((c0 & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F);
                if (cp < 0x800) cp = 0xFFFD;
                i += 3;
            }
        } else if ((c0 & 0xF8) == 0xF0 && i + 3 < bytes.size()) {
            const unsigned char c1 = s[i + 1];
            const unsigned char c2 = s[i + 2];
            const unsigned char c3 = s[i + 3];
            if (((c1 & 0xC0) != 0x80) || ((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80)) {
                cp = 0xFFFD;
                i += 1;
            } else {
                cp = ((c0 & 0x07) << 18) | ((c1 & 0x3F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                if (cp < 0x10000 || cp > 0x10FFFF) cp = 0xFFFD;
                i += 4;
            }
        } else {
            cp = 0xFFFD;
            i += 1;
        }

        if (cp <= 0xFFFF) {
            out.push_back(static_cast<char16_t>(cp));
        } else {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        }
    }

    return env->NewString(reinterpret_cast<const jchar *>(out.data()), static_cast<jsize>(out.size()));
}

#if !(defined(OPERIT_HAS_LLAMA_CPP) && OPERIT_HAS_LLAMA_CPP)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeIsAvailable(JNIEnv * env, jclass clazz) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGetUnavailableReason(JNIEnv * env, jclass clazz) {
    const char * msg = "llama.cpp native backend is not built. Ensure CMake fetched llama.cpp and links target 'llama'.";
    return env->NewStringUTF(msg);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCreateSession(
        JNIEnv * env,
        jclass clazz,
        jstring pathModel,
        jint nThreads,
        jint nCtx,
        jint nBatch,
        jint nUBatch,
        jint nGpuLayers,
        jboolean useMmap,
        jboolean flashAttention,
        jboolean kvUnified,
        jboolean offloadKqv
) {
    (void) env;
    (void) clazz;
    (void) pathModel;
    (void) nThreads;
    (void) nCtx;
    (void) nBatch;
    (void) nUBatch;
    (void) nGpuLayers;
    (void) useMmap;
    (void) flashAttention;
    (void) kvUnified;
    (void) offloadKqv;
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeReleaseSession(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCancel(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCountTokens(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring text) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) text;
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeSetSamplingParams(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat repetitionPenalty,
        jfloat frequencyPenalty,
        jfloat presencePenalty,
        jint penaltyLastN
) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) temperature;
    (void) topP;
    (void) topK;
    (void) repetitionPenalty;
    (void) frequencyPenalty;
    (void) presencePenalty;
    (void) penaltyLastN;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeApplyChatTemplate(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jobjectArray roles,
        jobjectArray contents,
        jboolean enableThinking,
        jboolean addAssistant
) {
    (void) clazz;
    (void) sessionPtr;
    (void) roles;
    (void) contents;
    (void) enableThinking;
    (void) addAssistant;
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeApplyStructuredChatTemplate(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jstring messagesJson,
        jstring toolsJson,
        jboolean enableThinking,
        jboolean addAssistant
) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) messagesJson;
    (void) toolsJson;
    (void) enableThinking;
    (void) addAssistant;
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGenerateStream(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring prompt, jint maxTokens, jobject callback) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) prompt;
    (void) maxTokens;
    (void) callback;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeSetToolCallGrammar(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jstring grammar,
        jobjectArray triggerPatterns
) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) grammar;
    (void) triggerPatterns;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeClearToolCallGrammar(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeParseToolCallResponse(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring content) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) content;
    return nullptr;
}

#else

namespace {

struct SamplingParamsNative {
    float temperature = 1.0f;
    float topP = 1.0f;
    int32_t topK = 0;
    int32_t penaltyLastN = 64;
    float repeatPenalty = 1.0f;
    float frequencyPenalty = 0.0f;
    float presencePenalty = 0.0f;
    uint32_t seed = static_cast<uint32_t>(std::rand());
};

struct LlamaSessionNative {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * sampler = nullptr;
    common_chat_templates_ptr chatTemplates;
    SamplingParamsNative samplingParams;
    ToolCallGrammarConfigNative toolCallGrammar;
    common_chat_parser_params toolCallParserParams;
    bool hasToolCallParser = false;
    std::atomic_bool cancel{false};

    // [PATCH 0002] KV 前缀复用状态。
    // kvTokens 保存上一次实际写入 KV cache 的完整 token 序列（prompt + 已生成）。
    // 下次请求时与新 prompt 逐 token 比对，只 decode 差异部分。
    // 必须严格逐 token 比较：任何模糊匹配都会让模型看到错位的上下文。
    std::vector<llama_token> kvTokens;
    uint64_t kvReusedTotal = 0;
    uint64_t kvDecodedTotal = 0;

    // [PATCH 0008] 推理互斥锁。
    // llama_context 不是线程安全的。实测崩溃：两个线程在 1 秒内同时对同一
    // session 调用 llama_decode（测试连接 119 token / 正式对话 528 token），
    // KV cache 与计算 buffer 被并发改写，ggml 检测到状态不一致后 ggml_abort。
    // 这里串行化整个生成过程：后到的请求排队等待，而不是并发破坏上下文。
    std::mutex inferenceMu;
};

// [PATCH 0002] 最长公共前缀长度（严格逐 token 相等）。
static int32_t commonPrefixLen(const std::vector<llama_token> & a,
                               const std::vector<llama_token> & b) {
    const size_t n = std::min(a.size(), b.size());
    size_t i = 0;
    while (i < n && a[i] == b[i]) {
        ++i;
    }
    return static_cast<int32_t>(i);
}

static std::once_flag gBackendInitOnce;

// [PATCH 0009] 进程级全局推理锁。
//
// 为什么 session 级锁不够：实测 App 会为「测试连接」和「正式对话」各建一个
// LlamaSessionNative（两个不同的 llama_context，甚至重复加载同一份模型）。
// session 级 mutex 各自独立，完全挡不住并发：
//   20:26:53.463 tid=30730 Creating llama session   (A)
//   20:26:53.680 tid=30735 Creating llama session   (B)
//   20:26:55.044 A: prefill 119 token
//   20:26:56.522 B: prefill 528 token
//   20:26:56.724 ggml_abort
//
// 根因：ggml-cpu 后端的 scratch buffer / 工作线程是后端级共享资源，
// 多个 context 并发 decode 会交错写入同一块 scratch，触发断言。
// 因此必须串行化整个进程内的 decode，而不只是单个 session。
//
// [PATCH 0026 / gpu9] 2026-11-07 05:10 升级为「进程级 native 生命周期锁」。
// 上面 0009 只把锁加在 nativeGenerateStream（推理）上，加载与释放仍裸奔。
// 3B Q4_0 冷启动实测（VERIFY 第 16 节）：App 会在同一进程内并行调用两次
// nativeCreateSession（相隔 51 ms，崩溃线程 DefaultDispatch），两者同时进入
// ggml-opencl.cpp 的 transpose_2d()；该函数持有函数级 static、无锁的
// ggml_cl_buffer buf，一个 session 在 buf.allocate() 里 clReleaseMemObject
// 并重建 cl_mem，另一个拿着旧 cl_mem 调 clCreateSubBuffer -> -38
// CL_INVALID_MEM_OBJECT -> CL_CHECK 的 GGML_ASSERT(0) -> ggml_abort -> SIGABRT。
// 栈：nativeCreateSession -> load_tensors -> load_all_data -> libggml-opencl.so。
//
// llama.cpp 的 context 本来就不是线程安全的；并发推理属于应用层用法问题，
// 应由应用层串行化，不构成「GPU 路线不可用」的证据（成功进程单 session
// 同样加载 1656.34 MiB 到 GPU、37/37 全卸载并正常出字）。
//
// 修复：把同一把锁的覆盖范围扩展为 加载(create) / 释放(release) / 推理(generate)
// 三段。上游 llama.cpp / ggml-opencl.cpp 一律不改。
// 注意：create 内无 env->Call* / New* 等 JNI 回调（abort_callback 是 C 函数指针），
// 持锁期间不会回调 Java 造成死锁；所有提前 return 由 lock_guard 自动解锁。
static std::mutex gInferenceMutex;

// [PATCH 0010] 诊断辅助：读取 /proc/self/stat 的 major/minor page fault 计数。
// 若 mmap 下 prefill 慢，majflt 会暴涨（每次都要从 UFS 读页）。
// [PATCH 0014] 读进程 CPU 时间（utime/stime，单位 tick）。
// 用于判断 llama_decode 期间 CPU 是否真在消耗：
//   时间在涨 → 在计算（慢 = 算力/内存问题）
//   时间不动 → 死等（锁 / IO / 缺页）
static bool readCpuTime(long & utime, long & stime) {
    FILE * f = std::fopen("/proc/self/stat", "r");
    if (!f) return false;
    char buf[2048];
    size_t n = std::fread(buf, 1, sizeof(buf) - 1, f);
    std::fclose(f);
    if (n == 0) return false;
    buf[n] = '\0';
    const char * p = std::strrchr(buf, ')');
    if (!p) return false;
    p++;
    // 第 14 字段 utime，第 15 字段 stime（1-based），state 是第 3 字段
    long v = 0;
    int field = 3;
    while (*p && field <= 15) {
        while (*p == ' ') p++;
        if (!*p) break;
        const char * q = p;
        while (*q && *q != ' ') q++;
        if (field == 14 || field == 15) {
            char tmp[32];
            size_t len = static_cast<size_t>(q - p);
            if (len >= sizeof(tmp)) len = sizeof(tmp) - 1;
            std::memcpy(tmp, p, len);
            tmp[len] = '\0';
            v = std::strtol(tmp, nullptr, 10);
            if (field == 14) utime = v; else stime = v;
        }
        field++;
        p = q;
    }
    return field > 15;
}

// [PATCH 0012] 修掉 0010 里的 /proc/self/stat 解析 bug。
// 原实现把右括号后的第一个字段当成 state（实际是空格分隔的 state 字符），
// 用 strtol 不断跳过非数字，会把后续字段整体错位一格，导致 minor/major 恒为 0。
// 这里改成"数空格"的方式按字段序号取值，避免手数偏移。
static bool readPageFaults(long & minflt, long & majflt) {
    FILE * f = std::fopen("/proc/self/stat", "r");
    if (!f) return false;
    char buf[2048];
    size_t n = std::fread(buf, 1, sizeof(buf) - 1, f);
    std::fclose(f);
    if (n == 0) return false;
    buf[n] = '\0';

    // comm 字段可能含空格和括号，跳过它：从最后一个 ')' 之后开始。
    const char * p = std::strrchr(buf, ')');
    if (!p) return false;
    p++;  // 现在 p 指向空格

    // 右括号之后依次是第 3 个字段 state。我们要第 10 字段 minflt、第 12 字段 majflt，
    // 按空格分隔逐字段读取（字段内容可能是非数字如 state='S'、flags 为二进制标记）。
    long v = 0;
    int field = 3;
    while (*p && field <= 12) {
        while (*p == ' ') p++;
        if (!*p) break;
        // 找到下一个空格
        const char * q = p;
        while (*q && *q != ' ') q++;
        if (field == 10 || field == 12) {
            char tmp[32];
            size_t len = static_cast<size_t>(q - p);
            if (len >= sizeof(tmp)) len = sizeof(tmp) - 1;
            std::memcpy(tmp, p, len);
            tmp[len] = '\0';
            v = std::strtol(tmp, nullptr, 10);
            if (field == 10) minflt = v; else majflt = v;
        }
        field++;
        p = q;
    }
    return field > 12;
}

static void logPrefillDiagnostics(const char * tag) {
    long minflt = -1, majflt = -1;
    if (readPageFaults(minflt, majflt)) {
        LOGI("[diag:%s] page_faults minor=%ld major=%ld", tag, minflt, majflt);
    }
    // CPU 频率（大核 cpu7）
    FILE * f = std::fopen("/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq", "r");
    if (!f) f = std::fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "r");
    if (f) {
        long khz = 0;
        if (std::fscanf(f, "%ld", &khz) == 1) {
            LOGI("[diag:%s] cpu_freq=%ld kHz", tag, khz);
        }
        std::fclose(f);
    }
}

// [PATCH 0014] 把 llama.cpp / ggml 的内部日志接进 logcat。
//
// 这是定位「prefill 不返回」的关键手段：llama.cpp 内部每个阶段都有
// LLAMA_LOG_INFO，默认写 stderr，在 Android 上被丢弃。
// 接出来后能看到：
//   - llama_context: n_ctx/n_batch/n_ubatch 的真实取值
//   - llama_context: reserving ...            ← sched_reserve 开始
//   - graph_reserve: reserving a graph ...    ← 计算图规模
//   - llama_context: reserve took xx ms       ← 分配耗时
//   - llama_kv_cache 的分配情况
// 卡在哪一步一目了然。
static void llamaLogCallback(enum ggml_log_level level, const char * text, void * user_data) {
    (void) user_data;
    if (text == nullptr) return;
    // text 自带换行，去掉尾部换行避免 logcat 出现空行
    std::string msg(text);
    while (!msg.empty() && (msg.back() == '\n' || msg.back() == '\r')) {
        msg.pop_back();
    }
    if (msg.empty()) return;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: __android_log_write(ANDROID_LOG_ERROR,   "llama.cpp", msg.c_str()); break;
        case GGML_LOG_LEVEL_WARN:  __android_log_write(ANDROID_LOG_WARN,    "llama.cpp", msg.c_str()); break;
        case GGML_LOG_LEVEL_DEBUG: __android_log_write(ANDROID_LOG_DEBUG,   "llama.cpp", msg.c_str()); break;
        default:                   __android_log_write(ANDROID_LOG_INFO,    "llama.cpp", msg.c_str()); break;
    }
}

static void ensureBackendInit() {
    std::call_once(gBackendInitOnce, []() {
        // [PATCH 0020] 必须先于一切 ggml 后端注册：探测失败即设
        // GGML_DISABLE_VULKAN，registry 初始化将完全跳过 Vulkan。
#if defined(OPERIT_HAS_OPENCL) && defined(OPERIT_OPENCL_SAFE_INIT)
        operitOpenclSafeGate();
#endif
        llama_log_set(llamaLogCallback, nullptr);
        llama_backend_init();
        std::srand(static_cast<unsigned int>(std::time(nullptr)));
        LOGI("llama_backend_init done (llama.cpp logs routed to logcat tag 'llama.cpp')");
    });
}

// [PATCH 0025 / gpu7] 进程级一次性 OpenCL 后端激活。
//
// 沿用 gpu6 (PATCH 0024) 的设计，仅把后端从 Vulkan 换成 OpenCL：
//   - JNI_OnLoad 设 DISABLE_OPENCL=1，ensureBackendInit() 先让 registry 单例
//     在绝对安全的纯 CPU 路径下构造完毕；
//   - 只有 n_gpu_layers>0 时，std::call_once 一次性 unsetenv +
//     ggml_backend_load_all() 追加注册 libggml-opencl.so；
//   - register_backend() 按 reg 指针去重，registry 构造后再 load_all() 是
//     安全且有效的，新后端会被追加进 devices 向量；
//   - 上游 ggml-backend-reg.cpp 已补 GGML_DISABLE_OPENCL 闸门（PATCH 0025），
//     否则启动期 0.5B 服务（layers=0）就会把 OpenCL 一起 dlopen；
//   - 激活全程 sigsetjmp 防护，崩溃恢复 DISABLE_OPENCL=1 并回退纯 CPU。
static std::once_flag gOpenclActivateOnce;
static std::atomic<bool> gOpenclActivated{false};

// 真正把 OpenCL 后端注册进 registry。返回是否成功发现 GPU 设备。
static bool operitActivateOpenclBackend() {
    std::call_once(gOpenclActivateOnce, []() {
        unsetenv("GGML_DISABLE_OPENCL");
        const size_t nBefore = ggml_backend_dev_count();
        LOGI("[gpu7 activate] begin: dev_count_before=%zu env_cleared=1", nBefore);

#if defined(OPERIT_HAS_OPENCL) && defined(OPERIT_OPENCL_SAFE_INIT)
        operitGpuProbeInstall();
        int crashSig = sigsetjmp(g_operitGpuProbeJmp, 1);
        if (crashSig != 0) {
            operitGpuProbeRestore();
            setenv("GGML_DISABLE_OPENCL", "1", 1);
            LOGE("[gpu7 activate] CRASHED (signal=%d) during OpenCL backend "
                 "registration -> CPU-only fallback (process kept alive).", crashSig);
            return;
        }
        const int probeRc = operitOpenclProbeOnce();
        if (probeRc != 0) {
            operitGpuProbeRestore();
            setenv("GGML_DISABLE_OPENCL", "1", 1);
            LOGE("[gpu7 activate] probe FAILED rc=%d -> CPU-only fallback", probeRc);
            return;
        }
        // registry 单例可能已由纯 CPU 首会话构造，再调一次 load_all() 会把
        // libggml-opencl.so 追加进来（register_backend 按 reg 指针去重）。
        ggml_backend_load_all();
        operitGpuProbeRestore();
#else
        ggml_backend_load_all();
#endif
        const size_t nAfter = ggml_backend_dev_count();
        size_t nGpu = 0;
        for (size_t i = 0; i < nAfter; ++i) {
            ggml_backend_dev_t dev = ggml_backend_dev_get(i);
            if (!dev) continue;
            const auto type = ggml_backend_dev_type(dev);
            if (type == GGML_BACKEND_DEVICE_TYPE_GPU ||
                type == GGML_BACKEND_DEVICE_TYPE_IGPU) ++nGpu;
        }
        LOGI("[gpu7 activate] done: dev_count=%zu gpu_devices=%zu "
             "ggml_disable_opencl=%d", nAfter, nGpu,
             getenv("GGML_DISABLE_OPENCL") ? 1 : 0);
        if (nGpu > 0) {
            gOpenclActivated.store(true, std::memory_order_release);
            LOGI("[gpu7 activate] SUCCESS: OpenCL backend registered, "
                 "GPU offload is now available for sessions with n_gpu_layers>0");
        } else {
            setenv("GGML_DISABLE_OPENCL", "1", 1);
            LOGE("[gpu7 activate] SUCCESS-path but NO GPU device found after "
                 "load_all (dlopen/ICD unavailable) -> CPU-only fallback");
        }
    });
    return gOpenclActivated.load(std::memory_order_acquire);
}

static uint32_t positiveOrDefaultUInt(jint value, uint32_t defaultValue) {
    return value > 0 ? static_cast<uint32_t>(value) : defaultValue;
}

static int32_t positiveOrDefaultInt(jint value, int32_t defaultValue) {
    return value > 0 ? static_cast<int32_t>(value) : defaultValue;
}

static bool jbooleanToBool(jboolean value) {
    return value == JNI_TRUE;
}

// [PATCH 0003] 读取 CPU 拓扑，推算推荐线程数。
//
// 为什么要做：Operit 默认 nThreads=4（LlamaSession.Config 第 13 行）。
// 骁龙 8 Gen 2 是 1×X3 + 4×A715 + 3×A510，只开 4 线程会完全用不到 X3
// 大核；而开 8 线程又会把线程调度到 A510 小核上，实测反而更慢。
//
// 读取 /sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq 判断簇：
//   取各核最大频率，把接近最高值的核视为大核。
// 无 Root 也能读该文件（普通应用有读权限）。
//
// 返回值：大核数 + 1，上限 6，下限 4。
//   8 Gen 2: 大核(X3)=1 → 1+1=2 太小，故另加"性能簇总数"逻辑
static int32_t detectRecommendedThreads() {
    std::vector<long> freqs;
    for (int cpu = 0; cpu < 16; ++cpu) {
        char path[128];
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", cpu);
        FILE * f = fopen(path, "r");
        if (!f) {
            if (cpu > 0) break;   // 连续 8 核都没有则认为到头
            continue;
        }
        long khz = 0;
        if (fscanf(f, "%ld", &khz) != 1) khz = 0;
        fclose(f);
        if (khz > 0) freqs.push_back(khz);
    }
    if (freqs.empty()) {
        LOGI("CPU topology: cpufreq unreadable, fallback to 4 threads");
        return 4;
    }

    // 分簇：按频率降序，相邻差异 < 15% 视为同簇
    std::vector<long> sorted = freqs;
    std::sort(sorted.begin(), sorted.end(), std::greater<long>());
    const long topFreq = sorted.front();
    // 性能簇 = 频率在最高频 70% 以上的核
    const long perfThreshold = static_cast<long>(topFreq * 0.70);
    int perfCores = 0;
    for (long f : freqs) {
        if (f >= perfThreshold) ++perfCores;
    }

    // 推荐值：性能核数量，clamp 到 [4, 6]
    // 8 Gen 2: X3(1) + A715(4) 都在 70% 阈值以上 → perfCores=5 → 取 6
    // 理由：llama.cpp 的 n_threads 通常比物理大核数开略多更利于流水线
    int32_t recommended = perfCores + 1;
    if (recommended < 4) recommended = 4;
    if (recommended > 6) recommended = 6;

    LOGI("CPU topology: cores=%zu topFreq=%ldkHz perfCores=%d recommended=%d",
         freqs.size(), topFreq, perfCores, recommended);
    return recommended;
}

// [PATCH 0013] GGUF 完整性校验。
//
// 只解析文件头和张量表，不读张量数据，代价极低（毫秒级）。
// 校验项：
//   1. magic == "GGUF"
//   2. 版本号 2 或 3
//   3. 每个张量的 offset + nbytes <= 实际文件大小
//   4. 张量对应的数据区不早于张量表结束位置
//
// 只做"是否截断"这一件事，不做量化格式合法性校验（那交给 llama.cpp）。
namespace ggufcheck {

struct Reader {
    const uint8_t * p;
    const uint8_t * end;
    bool ok = true;

    bool need(size_t n) {
        if (!ok || static_cast<size_t>(end - p) < n) {
            ok = false;
            return false;
        }
        return true;
    }
    template <typename T>
    T get() {
        T v{};
        if (!need(sizeof(T))) return v;
        std::memcpy(&v, p, sizeof(T));
        p += sizeof(T);
        return v;
    }
    std::string str() {
        const uint64_t n = get<uint64_t>();
        if (!ok || !need(static_cast<size_t>(n))) return {};
        std::string s(reinterpret_cast<const char *>(p), static_cast<size_t>(n));
        p += n;
        return s;
    }
    void skip_value(uint32_t type);
};

// gguf 类型枚举
enum : uint32_t {
    T_UINT8 = 0, T_INT8 = 1, T_UINT16 = 2, T_INT16 = 3,
    T_UINT32 = 4, T_INT32 = 5, T_FLOAT32 = 6, T_BOOL = 7,
    T_STRING = 8, T_ARRAY = 9, T_UINT64 = 10, T_INT64 = 11, T_FLOAT64 = 12,
};

void Reader::skip_value(uint32_t type) {
    switch (type) {
        case T_UINT8: case T_INT8: case T_BOOL:   p += 1; break;
        case T_UINT16: case T_INT16:              p += 2; break;
        case T_UINT32: case T_INT32: case T_FLOAT32: p += 4; break;
        case T_UINT64: case T_INT64: case T_FLOAT64: p += 8; break;
        case T_STRING: (void) str(); return;
        case T_ARRAY: {
            const uint32_t et = get<uint32_t>();
            const uint64_t n  = get<uint64_t>();
            if (!ok) return;
            if (et == T_STRING) {
                for (uint64_t i = 0; i < n && ok; ++i) (void) str();
            } else {
                static const size_t kSize[13] = {1,1,2,2,4,4,4,1,0,0,8,8,8};
                if (et > 12) { ok = false; return; }
                if (!need(static_cast<size_t>(n) * kSize[et])) return;
                p += static_cast<size_t>(n) * kSize[et];
            }
            return;
        }
        default: ok = false; return;
    }
    if (p > end) ok = false;
}

// ggml 各种量化类型每个 block 的元素数与字节数
static bool blockLayout(uint32_t type, uint64_t & blockElems, uint64_t & blockBytes) {
    switch (type) {
        case 0:  blockElems = 1;   blockBytes = 4;  return true;  // F32
        case 1:  blockElems = 1;   blockBytes = 2;  return true;  // F16
        case 2:  blockElems = 32;  blockBytes = 18; return true;  // Q4_0
        case 3:  blockElems = 32;  blockBytes = 20; return true;  // Q4_1
        case 6:  blockElems = 32;  blockBytes = 22; return true;  // Q5_0
        case 7:  blockElems = 32;  blockBytes = 24; return true;  // Q5_1
        case 8:  blockElems = 32;  blockBytes = 34; return true;  // Q8_0
        case 9:  blockElems = 32;  blockBytes = 36; return true;  // Q8_1
        case 10: blockElems = 256; blockBytes = 84;  return true;  // Q2_K
        case 11: blockElems = 256; blockBytes = 110; return true;  // Q3_K
        case 12: blockElems = 256; blockBytes = 144; return true;  // Q4_K
        case 13: blockElems = 256; blockBytes = 176; return true;  // Q5_K
        case 14: blockElems = 256; blockBytes = 210; return true;  // Q6_K
        case 15: blockElems = 256; blockBytes = 292; return true;  // Q8_K
        case 16: blockElems = 256; blockBytes = 66;  return true;  // IQ2_XXS
        case 17: blockElems = 256; blockBytes = 74;  return true;  // IQ2_XS
        case 18: blockElems = 256; blockBytes = 98;  return true;  // IQ3_XXS
        case 19: blockElems = 256; blockBytes = 50;  return true;  // IQ1_S
        case 20: blockElems = 32;  blockBytes = 18;  return true;  // IQ4_NL
        case 21: blockElems = 256; blockBytes = 110; return true;  // IQ3_S
        case 22: blockElems = 256; blockBytes = 82;  return true;  // IQ2_S
        case 23: blockElems = 256; blockBytes = 136; return true;  // IQ4_XS
        case 24: blockElems = 1;   blockBytes = 1;   return true;  // I8
        case 25: blockElems = 1;   blockBytes = 2;   return true;  // I16
        case 26: blockElems = 1;   blockBytes = 4;   return true;  // I32
        case 27: blockElems = 1;   blockBytes = 8;   return true;  // I64
        case 28: blockElems = 1;   blockBytes = 8;   return true;  // F64
        case 29: blockElems = 256; blockBytes = 56;  return true;  // IQ1_M
        case 30: blockElems = 1;   blockBytes = 2;   return true;  // BF16
        case 34: blockElems = 256; blockBytes = 54;  return true;  // TQ1_0
        case 35: blockElems = 256; blockBytes = 66;  return true;  // TQ2_0
        case 39: blockElems = 32;  blockBytes = 17;  return true;  // MXFP4
        default: return false;
    }
}

} // namespace ggufcheck

static bool validateGgufFile(const std::string & path, std::string & err) {
    FILE * f = std::fopen(path.c_str(), "rb");
    if (!f) {
        err = "cannot open file";
        return false;
    }
    std::fseek(f, 0, SEEK_END);
    const long long fileSize = ftell(f);
    std::fseek(f, 0, SEEK_SET);
    if (fileSize <= 0) {
        std::fclose(f);
        err = "empty file";
        return false;
    }

    // 只读文件头 + 张量表；张量表之后的内容不读。
    // 元数据区通常 < 10MB，读 64MB 足够覆盖。
    const size_t kHead = 64u * 1024u * 1024u;
    const size_t toRead = static_cast<size_t>(std::min<long long>(fileSize, kHead));
    std::vector<uint8_t> buf(toRead);
    const size_t got = std::fread(buf.data(), 1, toRead, f);
    std::fclose(f);
    if (got < 24) {
        err = "file too small to be a GGUF";
        return false;
    }

    ggufcheck::Reader r{buf.data(), buf.data() + got};
    char magic[4];
    std::memcpy(magic, r.p, 4);
    r.p += 4;
    if (std::memcmp(magic, "GGUF", 4) != 0) {
        err = "not a GGUF file (bad magic)";
        return false;
    }

    const uint32_t version = r.get<uint32_t>();
    if (version != 2 && version != 3) {
        err = "unsupported GGUF version " + std::to_string(version);
        return false;
    }

    const uint64_t nTensors = r.get<uint64_t>();
    const uint64_t nKv      = r.get<uint64_t>();
    if (!r.ok) {
        err = "truncated header";
        return false;
    }
    if (nTensors == 0 || nTensors > 100000 || nKv > 100000) {
        err = "implausible tensor/kv count (bad file)";
        return false;
    }

    // 跳过 KV 区
    for (uint64_t i = 0; i < nKv && r.ok; ++i) {
        (void) r.str();
        const uint32_t t = r.get<uint32_t>();
        if (!r.ok) break;
        r.skip_value(t);
    }
    if (!r.ok) {
        err = "truncated metadata (KV section) — file is incomplete";
        return false;
    }

    // 解析张量表，找最大 end offset
    uint64_t maxEnd = 0;
    bool sawTensor = false;
    bool sawUnknownType = false;   // [REVIEW P3] 有未知量化类型时尺寸校验只是下界
    for (uint64_t i = 0; i < nTensors && r.ok; ++i) {
        (void) r.str();
        const uint32_t nd = r.get<uint32_t>();
        if (!r.ok || nd == 0 || nd > 8) break;
        uint64_t ne = 1;
        for (uint32_t d = 0; d < nd; ++d) {
            const uint64_t dim = r.get<uint64_t>();
            // 维度乘积溢出会让 nbytes 回绕，截断文件反而能通过校验。
            // 单个 GGUF 张量不可能超过 2^40 个元素。
            if (!r.ok || dim == 0 || dim > (1ull << 40) || ne > (1ull << 40) / dim) {
                err = "malformed tensor shape — file is corrupt";
                return false;
            }
            ne *= dim;
        }
        const uint32_t type = r.get<uint32_t>();
        const uint64_t off  = r.get<uint64_t>();
        if (!r.ok) break;

        uint64_t blockElems = 1, blockBytes = 1;
        if (!ggufcheck::blockLayout(type, blockElems, blockBytes)) {
            // [REVIEW P3] 未知量化类型算不出精确字节数，但仍然能做下界校验：
            // 张量数据起点已经越过文件末尾 → 一定是截断，不必等 llama.cpp 去报。
            sawUnknownType = true;
            if (off >= static_cast<uint64_t>(fileSize)) {
                err = "tensor data offset beyond end of file — file is incomplete";
                return false;
            }
            continue;
        }
        const uint64_t nBlocks = (ne + blockElems - 1) / blockElems;
        const uint64_t nbytes  = nBlocks * blockBytes;
        const uint64_t end     = off + nbytes;
        if (end > maxEnd) maxEnd = end;
        sawTensor = true;
    }
    if (!r.ok) {
        err = "truncated metadata (tensor table) — file is incomplete";
        return false;
    }
    if (!sawTensor && !sawUnknownType) {
        err = "no readable tensors";
        return false;
    }

    // 张量表结束位置（按 GGUF 规范对齐到 32 字节）
    const size_t tableEnd = static_cast<size_t>(r.p - buf.data());
    const uint64_t dataStart = (static_cast<uint64_t>(tableEnd) + 31u) & ~31ull;
    const uint64_t required  = dataStart + maxEnd;

    if (static_cast<uint64_t>(fileSize) < required) {
        char msg[512];
        std::snprintf(msg, sizeof(msg),
                      "model file is INCOMPLETE: actual %lld MB, needs %lld MB (%.1f%% downloaded). "
                      "Delete and re-download the .gguf file.",
                      static_cast<long long>(fileSize / (1024 * 1024)),
                      static_cast<long long>(required / (1024 * 1024)),
                      100.0 * static_cast<double>(fileSize) / static_cast<double>(required));
        err = msg;
        return false;
    }

    LOGI("GGUF validation ok: %lld MB, %llu tensors, needs %lld MB",
         static_cast<long long>(fileSize / (1024 * 1024)),
         static_cast<unsigned long long>(nTensors),
         static_cast<long long>(required / (1024 * 1024)));
    if (sawUnknownType) {
        // [REVIEW P3] 有未知量化类型时，上面算的 required 只是下界，
        // 截断可能漏检。这里明说，不要让人以为校验是完整的。
        LOGI("GGUF validation note: unknown quantization type present, size check is a lower bound");
    }
    return true;
}

static std::string gLastError;

static void setLastError(const std::string & msg) {
    gLastError = msg;
    LOGE("last error: %s", msg.c_str());
}

// [PATCH 0013] 供 Java 侧读取创建失败的具体原因。
static bool validateGgufFile(const std::string & path, std::string & err);

// [PATCH 0012] prefill watchdog。
// 现象：n_batch/n_ctx 都正常、CPU 未降频、mmap 已生效，但 119 token 的 prefill
// 进入 llama_decode 后超过 8 分钟没有任何返回，UI 表现为"永远没回复"。
// 与其让用户干等，不如用 ggml 的 abort_callback 兜底：
// decode 期间回调会被周期性调用，一旦超过 kPrefillTimeoutMs 就返回 true 让
// ggml 层层退出，llama_decode 返回 2（aborted），上层再走正常错误路径。
// [PATCH 0019] 90s -> 300s。
// 原因：启用 Vulkan 后，首次 llama_decode 需要编译大量 compute pipeline 并把
// 4.3GB 级权重上传到 GPU，冷启动可能明显超过 90 秒。原 watchdog 会直接
// std::_Exit(134) 杀进程，GPU 版会把正常冷启动误判为卡死。
// CPU 版（-O3 修复后）prefill 229 token 仅几秒，不受影响。
static constexpr int64_t kPrefillTimeoutMs = 300000;
static std::atomic<int64_t> gPrefillDeadlineMs{0};  // 0 表示未启用

static int64_t nowMsSteady() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::steady_clock::now().time_since_epoch()).count();
}

static bool abortCallback(void * user_data) {
    auto * session = reinterpret_cast<LlamaSessionNative *>(user_data);
    if (session != nullptr && session->cancel.load()) {
        return true;
    }
    const int64_t deadline = gPrefillDeadlineMs.load();
    if (deadline != 0 && nowMsSteady() > deadline) {
        LOGI("prefill watchdog fired: llama_decode exceeded %lld ms, aborting",
             static_cast<long long>(kPrefillTimeoutMs));
        return true;
    }
    return false;
}

static bool rebuildSamplerForSession(LlamaSessionNative * session) {
    if (session == nullptr || session->model == nullptr || session->ctx == nullptr) {
        return false;
    }

    const llama_vocab * vocab = llama_model_get_vocab(session->model);

    llama_sampler * next = createSamplerChain(
        vocab,
        session->samplingParams.temperature,
        session->samplingParams.topP,
        session->samplingParams.topK,
        session->samplingParams.penaltyLastN,
        session->samplingParams.repeatPenalty,
        session->samplingParams.frequencyPenalty,
        session->samplingParams.presencePenalty,
        session->samplingParams.seed,
        &session->toolCallGrammar
    );

    if (!next) {
        return false;
    }

    if (session->sampler) {
        llama_sampler_free(session->sampler);
        session->sampler = nullptr;
    }

    session->sampler = next;
    return true;
}

static int32_t tokenizeText(const llama_vocab * vocab, const std::string & text, bool addSpecial) {
    if (vocab == nullptr) return 0;
    int32_t capacity = static_cast<int32_t>(text.size()) + 8;
    std::vector<llama_token> tokens;
    tokens.resize(std::max(16, capacity));

    int32_t n = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        addSpecial,
        true
    );

    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(
            vocab,
            text.c_str(),
            static_cast<int32_t>(text.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            addSpecial,
            true
        );
    }

    return std::max<int32_t>(0, n);
}

static std::vector<llama_token> tokenizeTextToVector(const llama_vocab * vocab, const std::string & text, bool addSpecial) {
    std::vector<llama_token> tokens;
    if (vocab == nullptr || text.empty()) {
        return tokens;
    }

    int32_t capacity = static_cast<int32_t>(text.size()) + 8;
    tokens.resize(std::max(16, capacity));

    int32_t n = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        addSpecial,
        true
    );

    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(
            vocab,
            text.c_str(),
            static_cast<int32_t>(text.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            addSpecial,
            true
        );
    }

    if (n <= 0) {
        tokens.clear();
        return tokens;
    }

    tokens.resize(static_cast<size_t>(n));
    return tokens;
}

static ToolCallGrammarConfigNative buildToolCallGrammarConfig(const common_chat_params & params) {
    ToolCallGrammarConfigNative config;
    config.grammar = params.grammar;
    config.lazy = params.grammar_lazy;
    config.generationPrompt = params.generation_prompt;

    for (const auto & trigger : params.grammar_triggers) {
        switch (trigger.type) {
            case COMMON_GRAMMAR_TRIGGER_TYPE_WORD:
                config.triggerPatterns.push_back(regex_escape(trigger.value));
                break;
            case COMMON_GRAMMAR_TRIGGER_TYPE_PATTERN:
                config.triggerPatterns.push_back(trigger.value);
                break;
            case COMMON_GRAMMAR_TRIGGER_TYPE_PATTERN_FULL:
                if (trigger.value.empty()) {
                    config.triggerPatterns.push_back("^$");
                } else {
                    std::string anchored;
                    if (trigger.value.front() != '^') {
                        anchored.push_back('^');
                    }
                    anchored += trigger.value;
                    if (trigger.value.back() != '$') {
                        anchored.push_back('$');
                    }
                    config.triggerPatterns.push_back(std::move(anchored));
                }
                break;
            case COMMON_GRAMMAR_TRIGGER_TYPE_TOKEN:
                if (trigger.token != LLAMA_TOKEN_NULL) {
                    config.triggerTokens.push_back(trigger.token);
                }
                break;
            default:
                break;
        }
    }

    return config;
}

static void resetToolCallState(LlamaSessionNative * session) {
    if (session == nullptr) {
        return;
    }

    session->toolCallGrammar = ToolCallGrammarConfigNative{};
    session->toolCallParserParams = common_chat_parser_params();
    session->hasToolCallParser = false;
}

static bool initializeChatTemplatesForSession(LlamaSessionNative * session) {
    if (session == nullptr || session->model == nullptr) {
        return false;
    }

    try {
        session->chatTemplates = common_chat_templates_init(session->model, "");
        return static_cast<bool>(session->chatTemplates);
    } catch (const std::exception & e) {
        LOGE("Failed to initialize chat templates: %s", e.what());
        session->chatTemplates.reset();
        return false;
    } catch (...) {
        LOGE("Failed to initialize chat templates: unknown error");
        session->chatTemplates.reset();
        return false;
    }
}

static bool buildChatMessages(
        const std::vector<std::string> & roles,
        const std::vector<std::string> & contents,
        std::vector<common_chat_msg> & outMessages
) {
    if (roles.size() != contents.size()) {
        return false;
    }

    outMessages.clear();
    outMessages.reserve(roles.size());

    for (size_t i = 0; i < roles.size(); ++i) {
        common_chat_msg msg;
        msg.role = roles[i];
        msg.content = contents[i];
        outMessages.push_back(std::move(msg));
    }

    return true;
}

static bool tokenToPiece(const llama_vocab * vocab, llama_token token, std::string & out) {
    if (vocab == nullptr) return false;
    std::vector<char> buf;
    buf.resize(256);

    int32_t n = llama_token_to_piece(vocab, token, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    if (n < 0) {
        buf.resize(static_cast<size_t>(-n));
        n = llama_token_to_piece(vocab, token, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    }
    if (n <= 0) return false;
    out.assign(buf.data(), buf.data() + n);
    return true;
}

static void prefillToolCallGenerationPrompt(LlamaSessionNative * session) {
    if (session == nullptr || session->model == nullptr || session->sampler == nullptr) {
        return;
    }
    if (session->toolCallGrammar.grammar.empty() || session->toolCallGrammar.generationPrompt.empty()) {
        return;
    }

    const llama_vocab * vocab = llama_model_get_vocab(session->model);
    auto tokens = tokenizeTextToVector(vocab, session->toolCallGrammar.generationPrompt, false);
    for (const auto token : tokens) {
        llama_sampler_accept(session->sampler, token);
    }
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeIsAvailable(JNIEnv * env, jclass clazz) {
    (void) env;
    (void) clazz;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGetLastError(JNIEnv * env, jclass clazz) {
    (void) clazz;
    return env->NewStringUTF(gLastError.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGetUnavailableReason(JNIEnv * env, jclass clazz) {
    (void) clazz;
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCreateSession(
        JNIEnv * env,
        jclass clazz,
        jstring pathModel,
        jint nThreads,
        jint nCtx,
        jint nBatch,
        jint nUBatch,
        jint nGpuLayers,
        jboolean useMmap,
        jboolean flashAttention,
        jboolean kvUnified,
        jboolean offloadKqv
) {
    (void) clazz;

    // [PATCH 0026 / gpu9] 进程级串行化 session 创建（含后端激活与模型加载）。
    // 与 nativeGenerateStream / nativeReleaseSession 共用同一把 gInferenceMutex，
    // 使两个 session 不会同时进入 ggml-opencl 的 transpose_2d() 静态 buffer。
    // 后到的 create 在此排队，直到前一个 create 结束并释放锁。
    std::lock_guard<std::mutex> lifecycleGuard(gInferenceMutex);
    LOGI("[gpu9 lock] nativeCreateSession acquired process-wide lock");

    // [PATCH 0025 / gpu7] 进程级一次性决策（沿用 gpu6 的 call_once 设计，
    // 后端从 Vulkan 换成 OpenCL）。registry 是 get_reg() 内的函数级 static
    // 单例，启动期先创建的 0.5B 服务（layers=0）会把它冻结为「仅 CPU」，
    // 所以必须：ensureBackendInit() 在前（DISABLE_OPENCL=1 安全构造），
    // 仅 n_gpu_layers>0 时才一次性追加注册，激活失败自动回退纯 CPU。
    ensureBackendInit();
    const bool openclRequested = (nGpuLayers > 0);
    const bool openclActive = openclRequested && operitActivateOpenclBackend();
    if (openclRequested) {
        LOGI("[gpu7 gate] n_gpu_layers=%d -> OpenCL activation %s",
             nGpuLayers, openclActive ? "SUCCEEDED" : "FAILED (CPU-only fallback)");
    } else {
        LOGI("[gpu7 gate] n_gpu_layers=0 -> CPU-only baseline, no OpenCL dlopen");
    }

    const std::string modelPath = jstringToString(env, pathModel);
    // [PATCH 0003] 未显式指定时按 CPU 拓扑推荐，而非硬编码 4
    const int32_t effectiveThreads =
        positiveOrDefaultInt(nThreads, detectRecommendedThreads());
    const bool gpuOffloadSupported = openclActive && operitProtectedSupportsGpuOffload();  // [PATCH 0025] 信号防护版
    const int32_t requestedGpuLayers = std::max<int32_t>(0, static_cast<int32_t>(nGpuLayers));
    const int32_t effectiveGpuLayers = gpuOffloadSupported ? requestedGpuLayers : 0;
    // [PATCH 0006] Android 上强制启用 mmap。
    // 实测：use_mmap=0 时 0.5B 模型首次 prefill 229 token 耗时 180s+（约 1.2 tok/s），
    // 而正常应为 100~300 tok/s。原因是匿名内存（400MB 模型 + n_ubatch=512 的中间
    // buffer）在 HyperOS 内存管控下被压入 zram，每次权重访问都要解压。
    // 改用 mmap 后模型走 page cache，内存不足时内核可直接丢弃干净页，不再反复换页。
    const bool requestedUseMmap = jbooleanToBool(useMmap);
    const bool effectiveUseMmap = true;  // Android 上恒开；requestedUseMmap 仅用于日志
    if (!requestedUseMmap) {
        LOGI("use_mmap was requested OFF by caller; overriding to ON (Android page-cache strategy)");
    }
    // [PATCH 0025 / gpu7] OpenCL 后端下强制关闭 Flash Attention。
    // 上游 Issue #21501「flash_attn crashes at graph_reserve with OpenCL backend」：
    // scheduler 找不到 flash_attn_ext 的 OpenCL kernel，禁用 FA 可避免崩溃。
    // 用户历史配置本就是 flash_attn=0，这里在 native 层兜底，防止 App 侧误开。
    const bool requestedFlashAttention = jbooleanToBool(flashAttention);
    const bool effectiveFlashAttention = false;
    if (requestedFlashAttention) {
        LOGI("flash_attn was requested ON by caller; overriding to OFF "
             "(OpenCL backend: FA conflicts with ggml-opencl, see llama.cpp #21501)");
    }
    const bool effectiveKvUnified = jbooleanToBool(kvUnified);

    // [PATCH 0028 / gpu11] offload_kqv 日用化：默认开启 + 显式关闭通道。
    //
    // 背景：0027（gpu10）用 flag 文件 / 属性做 A/B 开关。实测结论是「保留 =1」：
    //   graph splits 74→2、decode 中位 5.09→14.93 tok/s（反超 CPU-only 10.20）、
    //   crash 6 轮全空、=1 档回复纯中文无乱码。
    //   详见 offload_kqv-A-B判读报告-20261107-0930.md（§9 输出正确性闭环）。
    //
    // 本版把实验结论固化成默认行为，去掉「每次开机都要放 flag 文件」的依赖：
    //   1) Java 层 AIServiceFactory.kt:253 改为读取 config.llamaOffloadKqv，
    //      ModelConfigData.kt:175 默认值改 true（源码级正解）。
    //   2) dex 层对 classes29.dex 做 1 字节 patch（const/4 v11 #0→#1），
    //      等价于「配置默认 true 且用户未改」——避免 Gradle 全量重编 43 个 dex；
    //      完整可配置版需走 Gradle 重编，本版未做。
    //   3) 本函数在 native 决策点做最终保护，并提供双向覆盖：
    //        默认（无任何标记）               -> 沿用 Java 值（=1）→ 开机即用
    //        /sdcard/Download/operit_kqv_off  -> 强制 =0（回退通道）
    //        setprop debug.operit.kqv off     -> 强制 =0（adb 兜底）
    //        /sdcard/Download/operit_kqv_on  -> 强制 =1（兼容 gpu10 A/B）
    //        setprop debug.operit.kqv 1       -> 强制 =1
    //      off 优先于 on，避免两个标记同时存在时语义不明。
    //
    //      ⚠️ 关闭值刻意不用 "0"：gpu10 的 A/B 脚本收尾会执行
    //      `setprop debug.operit.kqv 0`，而系统属性要到手机重启才清零。
    //      用户刚跑完 A/B 就覆盖装 gpu11，这个残留的 0 会把日用默认
    //      重新压回 =0，且极难排查。故 "0" 一律视为「未设置」，
    //      只有显式 "off" 才关闭。App 内最简回退是把 GPU 层数设 0
    //      （kqvGpuReady=false 自动 =0），不需要 adb。
    //
    // 语义边界不变：offload_kqv 只改 KV 的「后端归属」，不改总量
    // （本次 3B @ n_ctx=2048 恒 72.00 MiB）；layers=0 或 OpenCL 激活失败
    // 时强制 0，纯 CPU 档不受影响。
    static const char * const kKqvOnFlagPath  = "/sdcard/Download/operit_kqv_on";
    static const char * const kKqvOffFlagPath = "/sdcard/Download/operit_kqv_off";
    const bool kqvFileOn  = (access(kKqvOnFlagPath,  F_OK) == 0);
    const bool kqvFileOff = (access(kKqvOffFlagPath, F_OK) == 0);
    char kqvProp[PROP_VALUE_MAX] = {0};
    __system_property_get("debug.operit.kqv", kqvProp);
    const bool kqvPropOn  = (kqvProp[0] == '1' && kqvProp[1] == '\0');
    // "0" 视为未设置（避开 A/B 脚本收尾的 setprop 0 残留），仅 "off" 关闭
    const bool kqvPropOff = (kqvProp[0] == 'o' && kqvProp[1] == 'f' &&
                             kqvProp[2] == 'f' && kqvProp[3] == '\0');
    const bool kqvForceOff = kqvFileOff || kqvPropOff;
    const bool kqvForceOn  = kqvFileOn  || kqvPropOn;
    const bool requestedOffloadKqv = jbooleanToBool(offloadKqv);
    const bool kqvGpuReady = gpuOffloadSupported && effectiveGpuLayers > 0;
    // 优先级：GPU 就绪 -> 未被显式关闭 -> （Java 请求 or 显式开启）
    const bool effectiveOffloadKqv =
        kqvGpuReady && !kqvForceOff && (requestedOffloadKqv || kqvForceOn);

    LOGI("[kqv-ab] on_file=%d off_file=%d prop=debug.operit.kqv[%s] requested=%d "
         "force_off=%d force_on=%d gpu_ready=%d(layers=%d,supported=%d) "
         "-> offload_kqv=%d [gpu11 default=1]",
         kqvFileOn ? 1 : 0, kqvFileOff ? 1 : 0,
         kqvProp[0] ? kqvProp : "<unset>",
         requestedOffloadKqv ? 1 : 0,
         kqvForceOff ? 1 : 0, kqvForceOn ? 1 : 0,
         kqvGpuReady ? 1 : 0, effectiveGpuLayers,
         gpuOffloadSupported ? 1 : 0,
         effectiveOffloadKqv ? 1 : 0);
    if (!kqvGpuReady && (kqvForceOn || requestedOffloadKqv)) {
        LOGI("[kqv-ab][warn] KQV offload requested but GPU offload inactive; "
             "offload_kqv stays 0. Set GPU layers=99 and confirm OpenCL activation.");
    }
    if (kqvForceOff) {
        LOGI("[kqv-ab][off] explicit off flag overrides default; offload_kqv=0");
    }

    auto * session = new (std::nothrow) LlamaSessionNative();
    if (!session) {
        LOGE("Failed to allocate session");
        return 0;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = effectiveGpuLayers;
    // llama.cpp now exposes model memory mapping through load_mode; the old
    // use_mmap/use_mlock fields no longer exist in llama_model_params.
    mparams.load_mode = effectiveUseMmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;
    // [PATCH 0011] 关闭 use_extra_bufts。
    // 这是上游默认 true 的优化项（为 CPU 后端预分配对齐 buffer）。实测在
    // Android + mmap + Q4_K_M 的组合下，prefill 进入后永不返回（119 token
    // 超过 8 分钟无 out）。该优化对移动端收益不明，先关掉排除干扰。
    mparams.use_extra_bufts = false;

    // [PATCH 0017] 运行时自证身份 + 设备 CPU 特性。
    // [PATCH 0019] 增补：枚举已注册后端 + GPU offload 状态。
    // 用户侧日志必须能看到 [operit-build gpu1-vk] 才说明装的是 GPU 版。
    {
        const unsigned long hw = getauxval(AT_HWCAP);
        const unsigned long hw2 = getauxval(AT_HWCAP2);
        LOGI(
            "[operit-build %s] device hwcap: dotprod=%d fp16=%d i8mm=%d sve=%d (hwcap=0x%lx hwcap2=0x%lx) compile_arch=%s",
            OPERIT_BUILD_TAG,
            (hw & HWCAP_ASIMDDP) ? 1 : 0,
            (hw & HWCAP_ASIMDHP) ? 1 : 0,
            (hw2 & HWCAP2_I8MM) ? 1 : 0,
            (hw2 & HWCAP2_SVE) ? 1 : 0,
            hw, hw2,
            "-march=armv8.2-a+dotprod");

        // ---- GPU 后端运行时自证 ----
        // 注意：llama_supports_gpu_offload() 内部会触发 ggml_backend_load_all()，
        // 上面已调用过，所以这里 registry 已就绪，可直接枚举。
        const size_t nDev = ggml_backend_dev_count();
        // [PATCH 0025 / gpu7] 打印后端注册结果与禁用开关状态，便于 adb 一步定位。
        // 判据：dev_count>1 且 gpu_devices>0 才说明 OpenCL 后端真的被注册。
        LOGI("[gpu7 backend] dev_count=%zu ggml_disable_opencl=%d opencl_active=%d", nDev,
             getenv("GGML_DISABLE_OPENCL") ? 1 : 0,
             gOpenclActivated.load(std::memory_order_acquire) ? 1 : 0);
        std::string devList;
        int nGpuDev = 0;
        for (size_t i = 0; i < nDev; ++i) {
            ggml_backend_dev_t dev = ggml_backend_dev_get(i);
            if (!dev) continue;
            const auto type = ggml_backend_dev_type(dev);
            const bool isGpu = (type == GGML_BACKEND_DEVICE_TYPE_GPU ||
                                type == GGML_BACKEND_DEVICE_TYPE_IGPU);
            if (isGpu) ++nGpuDev;
            if (!devList.empty()) devList += " | ";
            devList += ggml_backend_dev_name(dev);
            devList += isGpu ? "[GPU]" : "[CPU]";
        }
        LOGI(
            "[operit-build %s] backend: opencl_built=%d gpu_support=%d gpu_devices=%d "
            "n_gpu_layers=%d devices=[%s]",
            OPERIT_BUILD_TAG,
            OPERIT_OPENCL_BUILT,
            gpuOffloadSupported ? 1 : 0,
            nGpuDev,
            static_cast<int>(effectiveGpuLayers),
            devList.c_str());

        if (OPERIT_OPENCL_BUILT && !gpuOffloadSupported) {
            LOGE("[operit-build %s] OpenCL was BUILT but no GPU device was registered "
                 "(libOpenCL.so/ICD unavailable). Falling back to CPU.", OPERIT_BUILD_TAG);
        }
        if (gpuOffloadSupported && effectiveGpuLayers == 0) {
            LOGI("[operit-build %s] GPU backend ready but n_gpu_layers=0 -> CPU-only this session. "
                 "Set 'GPU layers' in Settings (e.g. 99) to offload.", OPERIT_BUILD_TAG);
        }
    }

    LOGI(
        "Creating llama session. model=%s threads=%d n_ctx=%d n_batch=%d n_ubatch=%d gpu_layers=%d use_mmap=%d flash_attn=%d kv_unified=%d offload_kqv=%d gpu_support=%d",
        modelPath.c_str(),
        effectiveThreads,
        static_cast<int>(nCtx),
        static_cast<int>(nBatch),
        static_cast<int>(nUBatch),
        effectiveGpuLayers,
        effectiveUseMmap ? 1 : 0,
        effectiveFlashAttention ? 1 : 0,
        effectiveKvUnified ? 1 : 0,
        effectiveOffloadKqv ? 1 : 0,
        gpuOffloadSupported ? 1 : 0
    );

    if (requestedGpuLayers > 0 && !gpuOffloadSupported) {
        LOGI("GPU layers requested but this build has no GPU offload backend; continuing on CPU");
    }

    // [PATCH 0013] 加载前校验 GGUF 文件完整性。
    //
    // 这是本次「3 分钟无输出」的真正根因，也是前面 cpu2~cpu7 所有调参都无效的原因：
    // 用户的模型文件是下载中断留下的残片 —— 实际 271 MB，
    // 而 GGUF 头部声明的张量偏移要求至少 4466 MB（完成度仅 6.1%）。
    //
    // llama.cpp 本身不校验这一点：
    //   1. llama_model_loader::init_mappings() 只在传了 progress_callback 时才比对
    //      size_done >= size_data，我们的 JNI 没传回调，直接跳过；
    //   2. llama_mmap 只 mmap 了 file->size()（271MB），
    //      但随后按元数据里的张量偏移（最大 4.6GB）去访问 → 越界。
    //   表现为：mmap 命中不了有效页，内核在缺页处理上反复重试，
    //   prefill 永远不返回（不崩溃、不报错，CPU 还在转）。
    //
    // 因此在加载前自己算一遍：最后一个张量偏移 + 其字节数 必须 <= 文件大小。
    {
        std::string err;
        if (!validateGgufFile(modelPath, err)) {
            LOGE("Model file validation failed: %s", err.c_str());
            setLastError(err);
            delete session;
            return 0;
        }
    }

    session->model = llama_model_load_from_file(modelPath.c_str(), mparams);
    if (!session->model) {
        LOGE("Failed to load model from file");
        setLastError("llama_model_load_from_file failed (see logcat for llama.cpp reason)");
        delete session;
        return 0;
    }

    // [PATCH 0004] 模板初始化失败不阻断 session —— Java 侧会回退到手工 ChatML。
    // 原实现这里 return 0，导致缺 chat_template 元数据的模型（0.5B / 部分 Gemma）
    // 直接建不起 session，表现为"测试连接闪退/失败"。
    if (!initializeChatTemplatesForSession(session)) {
        LOGI("Failed to initialize chat templates for model; will fall back to manual ChatML");
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = positiveOrDefaultUInt(nCtx, 0u);
    if (cparams.n_ctx == 0) {
        cparams.n_ctx = static_cast<uint32_t>(llama_model_n_ctx_train(session->model));
    }
    // [PATCH 0010] n_batch 必须等于 n_ctx。
    //
    // 实测崩溃：prompt_tokens=528，n_batch=512 → 84ms 内 ggml_abort。
    // 原因：llama_decode 的单次 batch 容量是 n_batch，528 > 512 直接断言失败。
    // Operit 会向 system prompt 注入大量工具定义（<tools> 段），528 token 很常见，
    // 未来换 7B 模型或开更多工具会更长，512 根本不够。
    // 因此直接把 n_batch 设为 n_ctx：一次 decode 能吃下整个 prompt，
    // 真正的计算切片由 n_ubatch 控制（见下）。
    const uint32_t defaultBatch = cparams.n_ctx;
    cparams.n_batch = std::min<uint32_t>(
        std::max<uint32_t>(positiveOrDefaultUInt(nBatch, defaultBatch), defaultBatch),
        cparams.n_ctx);
    constexpr uint32_t kMaxUbatch = 128;
    cparams.n_ubatch = std::min<uint32_t>(
        positiveOrDefaultUInt(nUBatch, std::min<uint32_t>(cparams.n_batch, kMaxUbatch)),
        std::min<uint32_t>(cparams.n_batch, kMaxUbatch));
    cparams.n_seq_max = 1;
    cparams.n_threads = effectiveThreads;
    cparams.n_threads_batch = effectiveThreads;
    cparams.flash_attn_type =
        effectiveFlashAttention ? LLAMA_FLASH_ATTN_TYPE_ENABLED : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    cparams.offload_kqv = effectiveOffloadKqv;
    cparams.kv_unified = effectiveKvUnified;
    cparams.abort_callback = abortCallback;
    cparams.abort_callback_data = session;

    session->ctx = llama_init_from_model(session->model, cparams);
    if (!session->ctx) {
        LOGE("Failed to create context");
        llama_model_free(session->model);
        delete session;
        return 0;
    }

    llama_set_n_threads(session->ctx, effectiveThreads, effectiveThreads);

    // [PATCH 0012] 打出实际生效的参数。之前日志打的是调用方传入的原始值，
    // 排查时无法确认 PATCH 0010 是否真的生效（实际生效 n_batch 曾是 2048 而日志显示 512）。
    LOGI(
        "Context created. effective n_ctx=%u n_batch=%u n_ubatch=%u n_threads=%d flash_attn=%d kv_unified=%d",
        llama_n_ctx(session->ctx),
        llama_n_batch(session->ctx),
        llama_n_ubatch(session->ctx),
        llama_n_threads(session->ctx),
        effectiveFlashAttention ? 1 : 0,
        effectiveKvUnified ? 1 : 0
    );

    session->samplingParams = SamplingParamsNative{};
    session->samplingParams.seed = static_cast<uint32_t>(std::rand());

    if (!rebuildSamplerForSession(session)) {
        LOGE("Failed to create sampler chain");
        llama_free(session->ctx);
        llama_model_free(session->model);
        delete session;
        return 0;
    }

    session->cancel.store(false);

    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeReleaseSession(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;

    if (sessionPtr == 0) return;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);

    // [PATCH 0026 / gpu9] 释放也纳入同一把进程级锁：llama_model_free /
    // llama_free 会触碰后端级共享 buffer，不能与另一个 session 的 create
    // 或 decode 交错执行。若在别的 session 正推理时调用，会排队等待。
    std::lock_guard<std::mutex> lifecycleGuard(gInferenceMutex);
    LOGI("[gpu9 lock] nativeReleaseSession acquired process-wide lock, session=%p",
         static_cast<void *>(session));

    if (session->sampler) {
        llama_sampler_free(session->sampler);
        session->sampler = nullptr;
    }

    if (session->ctx) {
        llama_free(session->ctx);
        session->ctx = nullptr;
    }

    session->chatTemplates.reset();

    if (session->model) {
        llama_model_free(session->model);
        session->model = nullptr;
    }

    delete session;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCancel(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;
    if (sessionPtr == 0) return;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    session->cancel.store(true);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCountTokens(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring text) {
    (void) clazz;
    if (sessionPtr == 0) return 0;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->model) return 0;
    const llama_vocab * vocab = llama_model_get_vocab(session->model);
    const std::string input = jstringToString(env, text);
    return static_cast<jint>(tokenizeText(vocab, input, true));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeSetSamplingParams(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat repetitionPenalty,
        jfloat frequencyPenalty,
        jfloat presencePenalty,
        jint penaltyLastN
) {
    (void) env;
    (void) clazz;

    if (sessionPtr == 0) return JNI_FALSE;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->ctx || !session->model) return JNI_FALSE;

    session->samplingParams.temperature = (float) temperature;
    session->samplingParams.topP = (float) topP;
    session->samplingParams.topK = (int32_t) topK;
    session->samplingParams.penaltyLastN = (int32_t) penaltyLastN;
    session->samplingParams.repeatPenalty = (float) repetitionPenalty;
    session->samplingParams.frequencyPenalty = (float) frequencyPenalty;
    session->samplingParams.presencePenalty = (float) presencePenalty;
    session->samplingParams.seed = static_cast<uint32_t>(std::rand());

    if (!rebuildSamplerForSession(session)) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeSetToolCallGrammar(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jstring grammar,
        jobjectArray triggerPatterns
) {
    (void) clazz;

    if (sessionPtr == 0 || grammar == nullptr) return JNI_FALSE;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->ctx || !session->model) return JNI_FALSE;

    const std::string grammarStr = jstringToString(env, grammar);
    if (grammarStr.empty()) {
        return JNI_FALSE;
    }

    std::vector<std::string> patterns;
    if (triggerPatterns != nullptr) {
        const jsize count = env->GetArrayLength(triggerPatterns);
        patterns.reserve(static_cast<size_t>(count));
        for (jsize i = 0; i < count; ++i) {
            auto jPattern = reinterpret_cast<jstring>(env->GetObjectArrayElement(triggerPatterns, i));
            if (jPattern != nullptr) {
                const std::string pattern = jstringToString(env, jPattern);
                if (!pattern.empty()) {
                    patterns.push_back(pattern);
                }
                env->DeleteLocalRef(jPattern);
            }
        }
    }

    const ToolCallGrammarConfigNative previousConfig = session->toolCallGrammar;
    const common_chat_parser_params previousParserParams = session->toolCallParserParams;
    const bool previousHasParser = session->hasToolCallParser;

    session->toolCallGrammar.grammar = grammarStr;
    session->toolCallGrammar.lazy = !patterns.empty();
    session->toolCallGrammar.triggerPatterns = patterns;
    session->toolCallGrammar.triggerTokens.clear();
    session->toolCallGrammar.generationPrompt.clear();
    session->toolCallParserParams = common_chat_parser_params();
    session->hasToolCallParser = false;

    if (!rebuildSamplerForSession(session)) {
        session->toolCallGrammar = previousConfig;
        session->toolCallParserParams = previousParserParams;
        session->hasToolCallParser = previousHasParser;
        (void) rebuildSamplerForSession(session);
        LOGE("Failed to enable tool-call grammar");
        return JNI_FALSE;
    }

    LOGI("Tool-call grammar enabled. trigger_patterns=%zu", session->toolCallGrammar.triggerPatterns.size());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeClearToolCallGrammar(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;

    if (sessionPtr == 0) return JNI_FALSE;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->ctx || !session->model) return JNI_FALSE;

    const ToolCallGrammarConfigNative previousConfig = session->toolCallGrammar;
    const common_chat_parser_params previousParserParams = session->toolCallParserParams;
    const bool previousHasParser = session->hasToolCallParser;

    resetToolCallState(session);

    if (!rebuildSamplerForSession(session)) {
        session->toolCallGrammar = previousConfig;
        session->toolCallParserParams = previousParserParams;
        session->hasToolCallParser = previousHasParser;
        (void) rebuildSamplerForSession(session);
        LOGE("Failed to clear tool-call grammar");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeApplyChatTemplate(
    JNIEnv * env,
    jclass clazz,
    jlong sessionPtr,
        jobjectArray roles,
        jobjectArray contents,
        jboolean enableThinking,
        jboolean addAssistant
) {
    (void) clazz;

    if (sessionPtr == 0 || roles == nullptr || contents == nullptr) return nullptr;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    // [PATCH 0004] 允许 chatTemplates 为空，下面会走手工 ChatML 兜底
    if (!session->model) return nullptr;

    const jsize nRoles = env->GetArrayLength(roles);
    const jsize nContents = env->GetArrayLength(contents);
    if (nRoles <= 0 || nContents <= 0 || nRoles != nContents) return nullptr;

    std::vector<std::string> roleBuf;
    std::vector<std::string> contentBuf;
    roleBuf.reserve(static_cast<size_t>(nRoles));
    contentBuf.reserve(static_cast<size_t>(nRoles));

    for (jsize i = 0; i < nRoles; i++) {
        auto jrole = (jstring) env->GetObjectArrayElement(roles, i);
        auto jcontent = (jstring) env->GetObjectArrayElement(contents, i);
        roleBuf.push_back(jstringToString(env, jrole));
        contentBuf.push_back(jstringToString(env, jcontent));
        if (jrole) env->DeleteLocalRef(jrole);
        if (jcontent) env->DeleteLocalRef(jcontent);
    }

    std::vector<common_chat_msg> messages;
    if (!buildChatMessages(roleBuf, contentBuf, messages)) {
        return nullptr;
    }

    common_chat_templates_inputs inputs;
    inputs.messages = std::move(messages);
    inputs.add_generation_prompt = addAssistant == JNI_TRUE;
    inputs.enable_thinking = enableThinking == JNI_TRUE;

    // [PATCH 0004] Jinja 引擎在某些模型模板上会段错误（0.5B / Gemma 3n 实测闪退），
    // 先试 Jinja，异常或空结果则降级到内置模板解析，最后兜底手工 ChatML。
    if (session->chatTemplates) {
        for (int attempt = 0; attempt < 2; ++attempt) {
            inputs.use_jinja = (attempt == 0);
            try {
                const common_chat_params params =
                    common_chat_templates_apply(session->chatTemplates.get(), inputs);
                if (!params.prompt.empty()) {
                    return bytesUtf8ToJstring(env, params.prompt);
                }
                LOGI("chat template attempt %d produced empty prompt", attempt);
            } catch (const std::exception & e) {
                LOGE("Failed to apply chat template (jinja=%d): %s", attempt == 0, e.what());
            } catch (...) {
                LOGE("Failed to apply chat template (jinja=%d): unknown error", attempt == 0);
            }
        }
    } else {
        LOGI("chat templates unavailable, using manual ChatML");
    }

    // 最后兜底：手工拼 ChatML，保证对话不中断
    {
        std::string manual;
        for (size_t i = 0; i < roleBuf.size(); ++i) {
            manual += "<|im_start|>";
            manual += roleBuf[i];
            manual += "\n";
            manual += contentBuf[i];
            manual += "<|im_end|>\n";
        }
        if (addAssistant == JNI_TRUE) {
            manual += "<|im_start|>assistant\n";
        }
        if (!manual.empty()) {
            LOGI("chat template: falling back to manual ChatML (%zu bytes)", manual.size());
            return bytesUtf8ToJstring(env, manual);
        }
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeApplyStructuredChatTemplate(
    JNIEnv * env,
    jclass clazz,
    jlong sessionPtr,
        jstring messagesJson,
        jstring toolsJson,
        jboolean enableThinking,
        jboolean addAssistant
) {
    (void) clazz;

    if (sessionPtr == 0 || messagesJson == nullptr) return nullptr;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    // [PATCH 0004] 允许 chatTemplates 为空，下面会走手工 ChatML 兜底
    if (!session->model || !session->ctx) return nullptr;

    const std::string messagesStr = jstringToString(env, messagesJson);
    const std::string toolsStr = jstringToString(env, toolsJson);

    const ToolCallGrammarConfigNative previousConfig = session->toolCallGrammar;
    const common_chat_parser_params previousParserParams = session->toolCallParserParams;
    const bool previousHasParser = session->hasToolCallParser;

    try {
        const auto messages = nlohmann::ordered_json::parse(messagesStr);
        const auto tools = toolsStr.empty()
            ? nlohmann::ordered_json()
            : nlohmann::ordered_json::parse(toolsStr);

        common_chat_templates_inputs inputs;
        inputs.messages = common_chat_msgs_parse_oaicompat(messages);
        inputs.tools = common_chat_tools_parse_oaicompat(tools);
        inputs.tool_choice = inputs.tools.empty()
            ? COMMON_CHAT_TOOL_CHOICE_NONE
            : COMMON_CHAT_TOOL_CHOICE_AUTO;
        inputs.add_generation_prompt = addAssistant == JNI_TRUE;
        inputs.enable_thinking = enableThinking == JNI_TRUE;

        // [PATCH 0004] 同 nativeApplyChatTemplate：Jinja 失败则降级
        common_chat_params params;
        bool gotParams = false;
        for (int attempt = 0; session->chatTemplates && attempt < 2 && !gotParams; ++attempt) {
            inputs.use_jinja = (attempt == 0);
            try {
                params = common_chat_templates_apply(session->chatTemplates.get(), inputs);
                if (!params.prompt.empty()) gotParams = true;
                else LOGI("structured chat template attempt %d empty", attempt);
            } catch (const std::exception & e) {
                LOGE("structured chat template (jinja=%d) failed: %s", attempt == 0, e.what());
            } catch (...) {
                LOGE("structured chat template (jinja=%d) failed: unknown", attempt == 0);
            }
        }
        if (!gotParams) {
            // 兜底：手工 ChatML（无 tool 段，但保证有 prompt 可用）
            std::string manual;
            const auto & msgsJson = messages;
            if (msgsJson.is_array()) {
                for (const auto & m : msgsJson) {
                    manual += "<|im_start|>";
                    manual += m.value("role", std::string("user"));
                    manual += "\n";
                    const auto & c = m["content"];
                    manual += c.is_string() ? c.get<std::string>() : c.dump();
                    manual += "<|im_end|>\n";
                }
            }
            if (addAssistant == JNI_TRUE) manual += "<|im_start|>assistant\n";
            if (!manual.empty()) {
                LOGI("structured chat template: fallback ChatML (%zu bytes)", manual.size());
                session->toolCallGrammar = previousConfig;
                session->toolCallParserParams = previousParserParams;
                session->hasToolCallParser = previousHasParser;
                (void) rebuildSamplerForSession(session);
                return bytesUtf8ToJstring(env, manual);
            }
            return nullptr;
        }

        session->toolCallGrammar = buildToolCallGrammarConfig(params);
        session->toolCallParserParams = common_chat_parser_params(params);
        session->toolCallParserParams.parse_tool_calls = true;
        session->hasToolCallParser = !params.parser.empty();
        if (session->hasToolCallParser) {
            session->toolCallParserParams.parser.load(params.parser);
        }

        if (!rebuildSamplerForSession(session)) {
            session->toolCallGrammar = previousConfig;
            session->toolCallParserParams = previousParserParams;
            session->hasToolCallParser = previousHasParser;
            (void) rebuildSamplerForSession(session);
            LOGE("Failed to apply structured chat template sampler state");
            return nullptr;
        }

        return bytesUtf8ToJstring(env, params.prompt);
    } catch (const std::exception & e) {
        session->toolCallGrammar = previousConfig;
        session->toolCallParserParams = previousParserParams;
        session->hasToolCallParser = previousHasParser;
        (void) rebuildSamplerForSession(session);
        LOGE("Failed to apply structured chat template: %s", e.what());
        return nullptr;
    } catch (...) {
        session->toolCallGrammar = previousConfig;
        session->toolCallParserParams = previousParserParams;
        session->hasToolCallParser = previousHasParser;
        (void) rebuildSamplerForSession(session);
        LOGE("Failed to apply structured chat template: unknown error");
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeParseToolCallResponse(
    JNIEnv * env,
    jclass clazz,
    jlong sessionPtr,
    jstring content
) {
    (void) clazz;

    if (sessionPtr == 0 || content == nullptr) return nullptr;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->hasToolCallParser) return nullptr;

    const std::string contentStr = jstringToString(env, content);
    if (contentStr.empty()) {
        return nullptr;
    }

    try {
        const common_chat_msg parsed = common_chat_parse(contentStr, false, session->toolCallParserParams);
        if (parsed.tool_calls.empty()) {
            return nullptr;
        }

        auto normalized = nlohmann::ordered_json::object();
        normalized["tool_calls"] = parsed.to_json_oaicompat()["tool_calls"];
        return bytesUtf8ToJstring(env, normalized.dump());
    } catch (const std::exception & e) {
        LOGE("Failed to parse tool-call response: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Failed to parse tool-call response: unknown error");
        return nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGenerateStream(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring prompt, jint maxTokens, jobject callback) {
    (void) clazz;

    if (sessionPtr == 0 || callback == nullptr) return JNI_FALSE;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->model || !session->ctx || !session->sampler) return JNI_FALSE;

    // [PATCH 0009] 进程级串行化。多个 session 共享 ggml-cpu 后端的 scratch
    // buffer，并发 decode 必定崩溃。这里用全局锁，后到请求排队等待。
    // [PATCH 0026 / gpu9] 该锁现为「加载/释放/推理」三段共用，见 675 行注释。
    // create/release 已先行持锁，故并发的第二个 session 会卡在 create 排队，
    // 不会与正在 decode 的 session 交错。
    {
        const auto waitStart = std::chrono::steady_clock::now();
        if (!gInferenceMutex.try_lock()) {
            LOGI("inference busy (another session decoding); waiting for global lock, session=%p",
                 static_cast<void *>(session));
            gInferenceMutex.lock();
            const auto waitedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                                      std::chrono::steady_clock::now() - waitStart).count();
            LOGI("acquired global inference lock after %lld ms, session=%p",
                 static_cast<long long>(waitedMs), static_cast<void *>(session));
        }
    }
    // 持有到函数结束；任何提前 return 都会自动解锁。
    std::lock_guard<std::mutex> inferenceGuard(gInferenceMutex, std::adopt_lock);

    session->cancel.store(false);

    // [PATCH 0002] KV 不再无条件清空：改为在拿到 promptTokens 后做前缀比对，
    // 只清理"可复用前缀之后"的残留部分。sampler 仍每次 reset。
    if (session->sampler) {
        llama_sampler_reset(session->sampler);
    }

    const std::string promptStr = jstringToString(env, prompt);
    const llama_vocab * vocab = llama_model_get_vocab(session->model);

    // Resolve callback method
    jclass cbCls = env->GetObjectClass(callback);
    if (!cbCls) return JNI_FALSE;
    jmethodID midOnToken = env->GetMethodID(cbCls, "onToken", "(Ljava/lang/String;)Z");
    if (!midOnToken) return JNI_FALSE;

    // Tokenize prompt
    int32_t capacity = static_cast<int32_t>(promptStr.size()) + 8;
    std::vector<llama_token> promptTokens;
    promptTokens.resize(std::max(16, capacity));
    int32_t nPrompt = llama_tokenize(
        vocab,
        promptStr.c_str(),
        static_cast<int32_t>(promptStr.size()),
        promptTokens.data(),
        static_cast<int32_t>(promptTokens.size()),
        true,
        true
    );
    if (nPrompt < 0) {
        promptTokens.resize(static_cast<size_t>(-nPrompt));
        nPrompt = llama_tokenize(
            vocab,
            promptStr.c_str(),
            static_cast<int32_t>(promptStr.size()),
            promptTokens.data(),
            static_cast<int32_t>(promptTokens.size()),
            true,
            true
        );
    }
    if (nPrompt <= 0) {
        LOGE("Tokenize prompt failed");
        return JNI_FALSE;
    }
    promptTokens.resize(static_cast<size_t>(nPrompt));

    // Avoid prompts that end with EOG/EOS tokens (some vocabs add EOS automatically when add_special=true)
    while (!promptTokens.empty() && llama_vocab_is_eog(vocab, promptTokens.back())) {
        promptTokens.pop_back();
    }
    if (promptTokens.empty()) {
        LOGE("Prompt tokenization resulted in only EOG/EOS tokens");
        return JNI_FALSE;
    }

    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(session->ctx));
    int maxNew = maxTokens <= 0 ? 256 : static_cast<int>(maxTokens);
    if (n_ctx > 0) {
        const int32_t reserveForGeneration = std::max<int32_t>(32, std::min<int32_t>(maxNew, n_ctx / 4));
        const int32_t maxPromptTokens = std::max<int32_t>(1, n_ctx - reserveForGeneration);
        if (static_cast<int32_t>(promptTokens.size()) > maxPromptTokens) {
            const size_t drop = promptTokens.size() - static_cast<size_t>(maxPromptTokens);
            const auto dropCount = static_cast<std::vector<llama_token>::difference_type>(drop);
            promptTokens.erase(promptTokens.begin(), promptTokens.begin() + dropCount);
            LOGI("Prompt truncated to fit context: kept=%d dropped=%zu n_ctx=%d", maxPromptTokens, drop, n_ctx);
        }
    }

    if (promptTokens.empty()) {
        LOGE("Prompt became empty after truncation");
        return JNI_FALSE;
    }

    LOGI(
        "Prefill decode start: prompt_tokens=%zu n_ctx=%d n_batch=%u max_new=%d",
        promptTokens.size(),
        n_ctx,
        llama_n_batch(session->ctx),
        maxNew
    );

    // ─────────────────────────────────────────────────────────────────
    // [PATCH 0002] KV 前缀复用
    //
    // 比对 session->kvTokens（上次进 KV 的序列）与新 promptTokens：
    //   reuseLen == 上次长度      → 纯追加，零清理（多轮对话最常见）
    //   0 < reuseLen < 上次长度   → 截断后续，重新 decode 尾部
    //   reuseLen == 0            → 无复用，全清后从头 decode
    // ─────────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────
    // [REVIEW P1] kvTokens 一致性守卫。
    //
    // kvTokens 记录"上次真正写进 KV 的序列"，下一轮据此算可复用前缀。
    // 只有当函数走完全部解码、在末尾重新赋值时，它才是可信的。
    // 任何提前返回都意味着 KV 与 kvTokens 已经不一致：
    //   - prefill 被取消 / watchdog 触发 → llama_decode 返回 2，KV 里只有 [0,reuseLen)
    //   - 逐 token decode 失败 → 尾部 token 没进 KV
    // 此时不清空，下一轮 commonPrefixLen 会算出一个越过"空洞"的前缀长度，
    // 模型带着缺失的上下文继续生成：不崩、不报错，只是答案悄悄变错。
    // 守卫在析构时清空（committed=false），成功路径显式提交。
    // ─────────────────────────────────────────────────────────────────
    struct KvTokensGuard {
        LlamaSessionNative * session;
        bool committed = false;
        explicit KvTokensGuard(LlamaSessionNative * s) : session(s) {}
        ~KvTokensGuard() {
            if (!committed && session != nullptr && !session->kvTokens.empty()) {
                LOGI("KV reuse state dropped: previous generation did not complete");
                session->kvTokens.clear();
            }
        }
    } kvTokensGuard(session);

    llama_memory_t mem = llama_get_memory(session->ctx);
    int32_t reuseLen = 0;
    if (mem && !session->kvTokens.empty()) {
        reuseLen = commonPrefixLen(session->kvTokens, promptTokens);
        // 安全阀：抽检前缀末端 token 是否真的一致（防御性冗余校验）
        if (reuseLen > 0) {
            const llama_token expect = session->kvTokens[reuseLen - 1];
            const llama_token actual = promptTokens[reuseLen - 1];
            if (expect != actual) {
                LOGI("KV reuse aborted: prefix mismatch at %d", reuseLen - 1);
                reuseLen = 0;
            }
        }
    }

    // [REVIEW] 整段命中时必须回退一个 token。
    // kvTokens 里含上一轮生成的内容，重发同一个 prompt（"重新生成"/重试）时
    // commonPrefixLen 会等于 promptTokens.size()，tailCount 变成 0：
    // llama_decode 对空 batch 返回 -1，而且拿不到最后一个位置的 logits，
    // 采样器无从下手。留一个 token 重算即可同时解决这两点。
    const int32_t promptLen = static_cast<int32_t>(promptTokens.size());
    if (promptLen > 0 && reuseLen >= promptLen) {
        reuseLen = promptLen - 1;
    }

    if (reuseLen > 0) {
        // 保留 [0, reuseLen)，丢弃其后所有残留 KV
        if (!llama_memory_seq_rm(mem, 0, reuseLen, -1)) {
            LOGI("KV seq_rm failed at reuseLen=%d, falling back to full clear", reuseLen);
            llama_memory_clear(mem, true);
            reuseLen = 0;
        }
    } else if (mem) {
        // 无可复用，必须全清，否则残留 KV 污染结果
        llama_memory_clear(mem, true);
    }

    session->kvReusedTotal += static_cast<uint64_t>(reuseLen);

    const int32_t tailCount = static_cast<int32_t>(promptTokens.size()) - reuseLen;
    LOGI("KV reuse: reused=%d tail=%d total=%zu (%d%% cached)",
         reuseLen,
         tailCount,
         promptTokens.size(),
         promptTokens.empty() ? 0
                              : static_cast<int>(100.0 * reuseLen / promptTokens.size()));

    int32_t n_past = reuseLen;

    // 只 decode 前缀之后的部分
    llama_token * tailPtr = promptTokens.data() + reuseLen;
    llama_batch batch = llama_batch_get_one(tailPtr, tailCount);
    // llama_batch_get_one() may leave batch.logits == nullptr (default behavior is: only last token outputs logits)
    // so never write to it unless it's allocated.
    if (batch.logits != nullptr && batch.n_tokens > 0) {
        batch.logits[batch.n_tokens - 1] = 1;
    }

    if (llama_model_has_encoder(session->model)) {
        if (llama_encode(session->ctx, batch) != 0) {
            LOGE("llama_encode failed");
            return JNI_FALSE;
        }

        llama_token decoder_start_token_id = llama_model_decoder_start_token(session->model);
        if (decoder_start_token_id == -1) {
            decoder_start_token_id = llama_vocab_bos(vocab);
        }

        batch = llama_batch_get_one(&decoder_start_token_id, 1);
        if (batch.logits != nullptr) {
            batch.logits[0] = 1;
        }
    }

    // [PATCH 0006] 分段计时：把 prefill 打成可测量的阶段，定位耗时黑洞。
    // 实测线索：线程4 / 229 token 曾耗 156s；线程6 首次 >180s 而后续 10s，
    // 指向"首次 prefill"路径而非纯算力。日志带 mem 信息可判断是否被 swap。
    const auto prefillStart = std::chrono::steady_clock::now();
    auto msSince = [](std::chrono::steady_clock::time_point t0) {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
                   std::chrono::steady_clock::now() - t0).count();
    };

    LOGI("prefill begin: tokens=%d n_batch=%u n_ubatch=%u n_threads=%d",
         batch.n_tokens, llama_n_batch(session->ctx), llama_n_ubatch(session->ctx),
         (int) llama_n_threads(session->ctx));

    // [PATCH 0014] decode 心跳。llama_decode 内部是黑盒，卡住时无法区分
    // "真在算" 和 "死等"。起一个后台线程，每 2 秒打一条，同时读
    // 进程的 utime/stime 看 CPU 到底有没有在消耗 ——
    //   CPU 时间在涨 → 在算，只是慢（算力/内存问题）
    //   CPU 时间不动 → 死等（锁/IO/缺页问题）
    //
    // 为什么必须由心跳线程做超时兜底：
    //   PATCH 0012 的 watchdog 依赖 ggml 的 abort_callback，而该回调只在
    //   计算图执行期间被周期调用。如果卡在 llama_decode 入口的
    //   sched_reserve()（分配计算图 / KV buffer）阶段，回调根本不会被触发，
    //   watchdog 形同虚设 —— 这正是用户等 3 分钟也没见到 fired 的原因。
    //   心跳线程独立于 llama 内部，超时后打印诊断并以非零码退出，
    //   让上层得到明确失败而不是无限挂起。
    std::atomic<bool> decodeRunning{true};
    std::thread heartbeat([&decodeRunning, session, prefillStart]() {
        int beat = 0;
        long prevUtime = -1, prevStime = -1;
        bool stalled = false;
        while (decodeRunning.load(std::memory_order_relaxed)) {
            for (int i = 0; i < 20 && decodeRunning.load(std::memory_order_relaxed); ++i) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
            if (!decodeRunning.load(std::memory_order_relaxed)) break;
            ++beat;
            long mn = 0, mj = 0;
            readPageFaults(mn, mj);
            long utime = 0, stime = 0;
            readCpuTime(utime, stime);

            const bool cpuAdvanced = (prevUtime >= 0) && (utime != prevUtime || stime != prevStime);
            if (prevUtime >= 0 && !cpuAdvanced) stalled = true;
            prevUtime = utime;
            prevStime = stime;

            const auto elapsedMs =
                std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now() - prefillStart).count();

            LOGI("[heartbeat %d] %lld ms in llama_decode; cpu_utime=%ld cpu_stime=%ld "
                 "cpu_advanced=%d pagefault_minor=%ld major=%ld n_past_est=%d",
                 beat, static_cast<long long>(elapsedMs), utime, stime,
                 cpuAdvanced ? 1 : 0, mn, mj, 0);

            if (elapsedMs > kPrefillTimeoutMs) {
                LOGE("=== PREFILL HUNG for %lld ms, cancelling request ===",
                     static_cast<long long>(elapsedMs));
                if (stalled) {
                    LOGE("Diagnosis: CPU time did NOT advance -> thread is blocked "
                         "(lock contention / IO stall / page-in wait), not computing.");
                } else {
                    LOGE("Diagnosis: CPU time WAS advancing -> computation is running but "
                         "abnormally slow (thermal throttle / memory pressure / too-large model).");
                }
                LOGE("Last heartbeat: cpu_utime=%ld cpu_stime=%ld minor=%ld major=%ld",
                     utime, stime, mn, mj);
                LOGE("Check 'llama.cpp' tag logs above for the exact stage "
                     "(look at the last line printed by llama_context / graph_reserve).");
                std::fflush(nullptr);
                // [REVIEW P1] 不再 std::_Exit(134)。
                // 杀进程等于把整个 App 状态丢掉，用户看到的是"闪退"而不是"这次请求失败"；
                // 而且大模型冷启动在热节流设备上超过 300 s 是完全可能的。
                // 改为标记取消：abortCallback 下一次被调用时返回 true，llama_decode
                // 返回 2，请求走正常错误路径，进程与其他会话都不受影响。
                session->cancel.store(true);
                gPrefillDeadlineMs.store(0);
                LOGE("prefill watchdog: request marked as cancelled, process stays alive");
                break;   // 只报一次，别每 2 秒刷屏
            }
        }
    });
    logPrefillDiagnostics("before");

    // [PATCH 0012] 启用 watchdog（只在 prefill 期间，逐 token decode 靠 UI 取消）
    gPrefillDeadlineMs.store(nowMsSteady() + kPrefillTimeoutMs);
    int32_t ret = llama_decode(session->ctx, batch);
    gPrefillDeadlineMs.store(0);

    // [PATCH 0014] 停心跳。必须 join，否则 std::thread 析构会 terminate。
    decodeRunning.store(false);
    if (heartbeat.joinable()) heartbeat.join();

    LOGI("prefill decode done: %lld ms, tokens=%d (%.2f tok/s), ret=%d",
         static_cast<long long>(msSince(prefillStart)),
         batch.n_tokens,
         msSince(prefillStart) > 0
             ? (1000.0 * batch.n_tokens / static_cast<double>(msSince(prefillStart)))
             : 0.0,
         ret);
    logPrefillDiagnostics("after");
    if (ret != 0 && ret != 1) {
        // 1 is a warning; 2 is aborted
        if (ret == 2) {
            LOGI("decode aborted (prompt)");
        } else {
            LOGE("llama_decode failed for prompt ret=%d", ret);
        }
        return JNI_FALSE;
    }

    // n_past for subsequent single-token decoding
    // [PATCH 0002] encoder 模型每次都要跑完整输入，无法复用 KV，保持原行为。
    if (llama_model_has_encoder(session->model)) {
        n_past = 1;
        session->kvTokens.clear();
    } else {
        n_past = static_cast<int32_t>(promptTokens.size());
    }

    prefillToolCallGenerationPrompt(session);

    // Generation loop
    std::vector<llama_token> generatedTokens;
    generatedTokens.reserve(static_cast<size_t>(maxNew));
    std::string prevDecoded;
    std::vector<char> detokBuf;
    // [REVIEW] 只统计"确实 decode 进 KV"的生成 token 数。循环可能在采样后、
    // decode 前 break（回调返回 false、n_ctx 上限、取消），这些 token 在 KV
    // 里并不存在，不能算进 kvTokens。
    int32_t decodedInLoop = 0;

    for (int i = 0; i < maxNew; i++) {
        if (session->cancel.load()) {
            LOGI("generation cancelled");
            break;
        }

        const llama_token newToken = llama_sampler_sample(session->sampler, session->ctx, -1);
        llama_sampler_accept(session->sampler, newToken);

        if (i == 0) {
            LOGI("first sampled token=%d eog=%d", (int) newToken, (int) llama_vocab_is_eog(vocab, newToken));
        }

        if (llama_vocab_is_eog(vocab, newToken)) {
            break;
        }

        // Detokenize the generated token sequence to produce valid UTF-8 text.
        // Token pieces may split multi-byte sequences; emitting per-token pieces often results in mojibake.
        generatedTokens.push_back(newToken);

        int32_t detokCap = std::max<int32_t>(64, static_cast<int32_t>(generatedTokens.size() * 8 + 32));
        detokBuf.resize(static_cast<size_t>(detokCap));

        int32_t nDetok = llama_detokenize(
            vocab,
            generatedTokens.data(),
            static_cast<int32_t>(generatedTokens.size()),
            detokBuf.data(),
            static_cast<int32_t>(detokBuf.size()),
            true,
            false
        );
        if (nDetok < 0) {
            detokBuf.resize(static_cast<size_t>(-nDetok));
            nDetok = llama_detokenize(
                vocab,
                generatedTokens.data(),
                static_cast<int32_t>(generatedTokens.size()),
                detokBuf.data(),
                static_cast<int32_t>(detokBuf.size()),
                true,
                false
            );
        }

        std::string decodedNow;
        if (nDetok > 0) {
            decodedNow.assign(detokBuf.data(), detokBuf.data() + nDetok);
        }

        std::string delta;
        if (!prevDecoded.empty() && decodedNow.rfind(prevDecoded, 0) == 0) {
            delta = decodedNow.substr(prevDecoded.size());
        } else {
            delta = decodedNow;
        }
        prevDecoded = decodedNow;

        if (!delta.empty()) {
            jstring jdelta = bytesUtf8ToJstring(env, delta);
            if (jdelta == nullptr || env->ExceptionCheck()) {
                env->ExceptionClear();
            } else {
                const jboolean keepGoing = env->CallBooleanMethod(callback, midOnToken, jdelta);
                env->DeleteLocalRef(jdelta);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    LOGE("Java callback threw exception; stopping generation");
                    break;
                }
                if (!keepGoing) {
                    break;
                }
            }
        }

        if (n_ctx > 0 && n_past >= n_ctx) {
            LOGI("context window reached: n_past=%d n_ctx=%d", n_past, n_ctx);
            break;
        }

        llama_token next = newToken;
        batch = llama_batch_get_one(&next, 1);
        if (batch.pos != nullptr) {
            batch.pos[0] = n_past;
        }
        if (batch.logits != nullptr) {
            batch.logits[0] = 1;
        }
        // [PATCH 0012] 逐 token decode 计时。卡死时必须能区分是 prefill 还是这里。
        const auto tokStart = std::chrono::steady_clock::now();
        ret = llama_decode(session->ctx, batch);
        const auto tokMs = msSince(tokStart);
        if (i < 8) {
            LOGI("sample #%d n_past=%d decode=%lld ms (%.2f tok/s)",
                 i, n_past, static_cast<long long>(tokMs),
                 tokMs > 0 ? 1000.0 / static_cast<double>(tokMs) : 0.0);
        }
        if (ret != 0 && ret != 1) {
            if (ret == 2) {
                LOGI("decode aborted");
                break;
            }
            LOGE("llama_decode failed ret=%d", ret);
            return JNI_FALSE;
        }

        n_past += 1;
        decodedInLoop += 1;
    }
    // [PATCH 0002] 记录真正进了 KV 的序列，供下一轮做前缀比对。
    //
    // [REVIEW] 只记到"确实 decode 过"的位置。此前按 generatedTokens 全量记录，
    // 而循环可能在采样之后、decode 之前 break（回调返回 false、n_ctx 上限、
    // 取消），那些 token 在 KV 里并不存在。下一轮 commonPrefixLen 就会算到
    // 未解码的位置上，模型带着空洞继续生成 —— 不崩、不报错，答案悄悄变错。
    //
    // encoder 模型每轮都重跑完整输入、KV 里只有 decoder 侧，不能登记可复用前缀。
    if (!llama_model_has_encoder(session->model)) {
        session->kvTokens.assign(promptTokens.begin(), promptTokens.end());
        session->kvTokens.insert(session->kvTokens.end(),
                                 generatedTokens.begin(),
                                 generatedTokens.begin() + decodedInLoop);
    }
    kvTokensGuard.committed = true;   // [REVIEW P1] 走到这里 KV 与 kvTokens 才一致
    session->kvDecodedTotal += static_cast<uint64_t>(tailCount);
    if (session->kvReusedTotal > 0) {
        const double total = static_cast<double>(session->kvReusedTotal + session->kvDecodedTotal);
        LOGI("KV cumulative: reused=%llu decoded=%llu (%.1f%% saved)",
             static_cast<unsigned long long>(session->kvReusedTotal),
             static_cast<unsigned long long>(session->kvDecodedTotal),
             total > 0 ? 100.0 * session->kvReusedTotal / total : 0.0);
    }

    return JNI_TRUE;
}

#endif

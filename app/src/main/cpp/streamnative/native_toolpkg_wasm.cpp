#include <jni.h>
#include <wasm_export.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <iomanip>
#include <limits>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

namespace {

constexpr uint32_t kInstanceStackSize = 64U * 1024U;
constexpr uint32_t kInstanceHeapSize = 64U * 1024U;
constexpr uint32_t kExecEnvStackSize = 64U * 1024U;
constexpr size_t kErrorBufferSize = 512U;

constexpr jint kTypeI32 = 1;
constexpr jint kTypeI64 = 2;
constexpr jint kTypeF32 = 3;
constexpr jint kTypeF64 = 4;

std::once_flag g_runtime_init_once;
bool g_runtime_ready = false;
std::string g_runtime_init_error;

struct ToolPkgWasmModule {
    std::vector<uint8_t> bytes;
    wasm_module_t module = nullptr;
    wasm_module_inst_t instance = nullptr;
    wasm_exec_env_t exec_env = nullptr;
    std::mutex mutex;
};

void assemblyscriptAbort(
        wasm_exec_env_t exec_env,
        int32_t,
        int32_t,
        int32_t line,
        int32_t column) {
    wasm_module_inst_t module_inst = wasm_runtime_get_module_inst(exec_env);
    if (module_inst != nullptr) {
        std::ostringstream message;
        message << "AssemblyScript abort";
        if (line > 0 || column > 0) {
            message << " at " << line << ":" << column;
        }
        wasm_runtime_set_exception(module_inst, message.str().c_str());
    }
}

NativeSymbol g_env_symbols[] = {
        {"abort", reinterpret_cast<void *>(assemblyscriptAbort), "(iiii)", nullptr},
};

std::string jsonEscape(const std::string &value) {
    std::ostringstream out;
    for (unsigned char ch : value) {
        switch (ch) {
            case '"':
                out << "\\\"";
                break;
            case '\\':
                out << "\\\\";
                break;
            case '\b':
                out << "\\b";
                break;
            case '\f':
                out << "\\f";
                break;
            case '\n':
                out << "\\n";
                break;
            case '\r':
                out << "\\r";
                break;
            case '\t':
                out << "\\t";
                break;
            default:
                if (ch < 0x20U) {
                    out << "\\u"
                        << std::hex << std::setw(4) << std::setfill('0')
                        << static_cast<int>(ch)
                        << std::dec << std::setfill(' ');
                } else {
                    out << static_cast<char>(ch);
                }
                break;
        }
    }
    return out.str();
}

std::string jsonFailure(const std::string &message) {
    return "{\"success\":false,\"message\":\"" + jsonEscape(message) + "\"}";
}

std::string jsonNumber(double value) {
    std::ostringstream out;
    out << std::setprecision(std::numeric_limits<double>::max_digits10) << value;
    return out.str();
}

std::string jsonFloatValue(double value) {
    if (std::isnan(value)) {
        return "\"NaN\"";
    }
    if (std::isinf(value)) {
        return value > 0 ? "\"Infinity\"" : "\"-Infinity\"";
    }
    return jsonNumber(value);
}

std::string wasmKindName(wasm_valkind_t kind) {
    switch (kind) {
        case WASM_I32:
            return "i32";
        case WASM_I64:
            return "i64";
        case WASM_F32:
            return "f32";
        case WASM_F64:
            return "f64";
        default:
            return "unsupported(" + std::to_string(static_cast<int>(kind)) + ")";
    }
}

bool typeCodeToKind(jint type_code, wasm_valkind_t *kind) {
    switch (type_code) {
        case kTypeI32:
            *kind = WASM_I32;
            return true;
        case kTypeI64:
            *kind = WASM_I64;
            return true;
        case kTypeF32:
            *kind = WASM_F32;
            return true;
        case kTypeF64:
            *kind = WASM_F64;
            return true;
        default:
            return false;
    }
}

void destroyModule(ToolPkgWasmModule *handle) {
    if (handle == nullptr) {
        return;
    }
    if (handle->exec_env != nullptr) {
        wasm_runtime_destroy_exec_env(handle->exec_env);
        handle->exec_env = nullptr;
    }
    if (handle->instance != nullptr) {
        wasm_runtime_deinstantiate(handle->instance);
        handle->instance = nullptr;
    }
    if (handle->module != nullptr) {
        wasm_runtime_unload(handle->module);
        handle->module = nullptr;
    }
    delete handle;
}

void throwJava(JNIEnv *env, const char *class_name, const std::string &message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

std::vector<uint8_t> jbyteArrayToVector(JNIEnv *env, jbyteArray bytes) {
    if (bytes == nullptr) {
        return {};
    }
    const jsize length = env->GetArrayLength(bytes);
    std::vector<uint8_t> out(static_cast<size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
    }
    return out;
}

std::string jstringToString(JNIEnv *env, jstring value) {
    if (value == nullptr) {
        return "";
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

bool ensureRuntime() {
    std::call_once(g_runtime_init_once, []() {
        RuntimeInitArgs init_args;
        std::memset(&init_args, 0, sizeof(init_args));
        init_args.mem_alloc_type = Alloc_With_System_Allocator;
        init_args.native_module_name = "env";
        init_args.native_symbols = g_env_symbols;
        init_args.n_native_symbols =
                static_cast<uint32_t>(sizeof(g_env_symbols) / sizeof(g_env_symbols[0]));

        if (wasm_runtime_full_init(&init_args)) {
            g_runtime_ready = true;
        } else {
            g_runtime_ready = false;
            g_runtime_init_error = "WAMR runtime initialization failed";
        }
    });
    return g_runtime_ready;
}

std::string buildResultJson(
        const std::vector<wasm_val_t> &results,
        const std::vector<wasm_valkind_t> &result_types) {
    std::ostringstream out;
    out << "{\"success\":true,\"results\":[";
    for (size_t index = 0; index < results.size(); ++index) {
        if (index > 0) {
            out << ",";
        }
        const wasm_val_t &value = results[index];
        const wasm_valkind_t kind = result_types[index];
        out << "{\"type\":\"" << wasmKindName(kind) << "\",\"value\":";
        switch (kind) {
            case WASM_I32:
                out << value.of.i32;
                break;
            case WASM_I64:
                out << "\"" << value.of.i64 << "\"";
                break;
            case WASM_F32: {
                uint32_t bits = 0;
                std::memcpy(&bits, &value.of.f32, sizeof(bits));
                out << jsonFloatValue(static_cast<double>(value.of.f32))
                    << ",\"bits\":" << bits;
                break;
            }
            case WASM_F64: {
                uint64_t bits = 0;
                std::memcpy(&bits, &value.of.f64, sizeof(bits));
                out << jsonFloatValue(value.of.f64)
                    << ",\"bits\":\"" << bits << "\"";
                break;
            }
            default:
                out << "null";
                break;
        }
        out << "}";
    }
    out << "]}";
    return out.str();
}

std::string callWasm(
        ToolPkgWasmModule *handle,
        const std::string &function_name,
        const std::vector<jint> &arg_type_codes,
        const std::vector<jlong> &arg_bits) {
    if (handle == nullptr || handle->instance == nullptr || handle->exec_env == nullptr) {
        return jsonFailure("WASM module handle is invalid");
    }
    if (function_name.empty()) {
        return jsonFailure("WASM export name is required");
    }

    std::lock_guard<std::mutex> lock(handle->mutex);
    wasm_function_inst_t function =
            wasm_runtime_lookup_function(handle->instance, function_name.c_str());
    if (function == nullptr) {
        return jsonFailure("WASM export not found: " + function_name);
    }

    const uint32_t param_count = wasm_func_get_param_count(function, handle->instance);
    if (param_count != arg_type_codes.size() || param_count != arg_bits.size()) {
        std::ostringstream message;
        message << "WASM argument count mismatch for " << function_name
                << ": expected " << param_count
                << ", got " << arg_type_codes.size();
        return jsonFailure(message.str());
    }

    std::vector<wasm_valkind_t> param_types(param_count);
    if (param_count > 0) {
        wasm_func_get_param_types(function, handle->instance, param_types.data());
    }

    std::vector<wasm_val_t> args(param_count);
    for (uint32_t index = 0; index < param_count; ++index) {
        wasm_valkind_t provided_kind = WASM_I32;
        if (!typeCodeToKind(arg_type_codes[index], &provided_kind)) {
            return jsonFailure("Unsupported WASM argument type code: " + std::to_string(arg_type_codes[index]));
        }
        const wasm_valkind_t expected_kind = param_types[index];
        if (expected_kind != provided_kind) {
            std::ostringstream message;
            message << "WASM argument type mismatch at index " << index
                    << ": expected " << wasmKindName(expected_kind)
                    << ", got " << wasmKindName(provided_kind);
            return jsonFailure(message.str());
        }

        args[index].kind = provided_kind;
        switch (provided_kind) {
            case WASM_I32:
                args[index].of.i32 = static_cast<int32_t>(arg_bits[index]);
                break;
            case WASM_I64:
                args[index].of.i64 = static_cast<int64_t>(arg_bits[index]);
                break;
            case WASM_F32: {
                const uint32_t raw = static_cast<uint32_t>(arg_bits[index]);
                float value = 0.0f;
                std::memcpy(&value, &raw, sizeof(value));
                args[index].of.f32 = value;
                break;
            }
            case WASM_F64: {
                const uint64_t raw = static_cast<uint64_t>(arg_bits[index]);
                double value = 0.0;
                std::memcpy(&value, &raw, sizeof(value));
                args[index].of.f64 = value;
                break;
            }
            default:
                return jsonFailure("Unsupported WASM argument type: " + wasmKindName(provided_kind));
        }
    }

    const uint32_t result_count = wasm_func_get_result_count(function, handle->instance);
    std::vector<wasm_valkind_t> result_types(result_count);
    if (result_count > 0) {
        wasm_func_get_result_types(function, handle->instance, result_types.data());
    }
    for (uint32_t index = 0; index < result_count; ++index) {
        if (result_types[index] != WASM_I32 &&
            result_types[index] != WASM_I64 &&
            result_types[index] != WASM_F32 &&
            result_types[index] != WASM_F64) {
            return jsonFailure("Unsupported WASM result type: " + wasmKindName(result_types[index]));
        }
    }

    std::vector<wasm_val_t> results(result_count);
    for (uint32_t index = 0; index < result_count; ++index) {
        results[index].kind = result_types[index];
    }

    const bool ok =
            wasm_runtime_call_wasm_a(
                    handle->exec_env,
                    function,
                    result_count,
                    results.empty() ? nullptr : results.data(),
                    param_count,
                    args.empty() ? nullptr : args.data());
    if (!ok) {
        const char *exception = wasm_runtime_get_exception(handle->instance);
        if (exception != nullptr && std::strlen(exception) > 0) {
            return jsonFailure(exception);
        }
        return jsonFailure("WASM function call failed: " + function_name);
    }

    return buildResultJson(results, result_types);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_operit_util_ToolPkgWasmNative_load(JNIEnv *env, jobject, jbyteArray bytes) {
    if (!ensureRuntime()) {
        throwJava(
                env,
                "java/lang/IllegalStateException",
                g_runtime_init_error.empty() ? "WAMR runtime initialization failed" : g_runtime_init_error);
        return 0;
    }

    auto wasm_bytes = jbyteArrayToVector(env, bytes);
    if (env->ExceptionCheck()) {
        return 0;
    }
    if (wasm_bytes.empty()) {
        throwJava(env, "java/lang/IllegalArgumentException", "WASM module bytes are empty");
        return 0;
    }
    if (wasm_bytes.size() > std::numeric_limits<uint32_t>::max()) {
        throwJava(env, "java/lang/IllegalArgumentException", "WASM module is too large");
        return 0;
    }

    auto *handle = new ToolPkgWasmModule();
    handle->bytes = std::move(wasm_bytes);

    std::array<char, kErrorBufferSize> error_buffer = {};
    handle->module =
            wasm_runtime_load(
                    handle->bytes.data(),
                    static_cast<uint32_t>(handle->bytes.size()),
                    error_buffer.data(),
                    static_cast<uint32_t>(error_buffer.size()));
    if (handle->module == nullptr) {
        std::string message = error_buffer.data();
        destroyModule(handle);
        throwJava(
                env,
                "java/lang/IllegalArgumentException",
                message.empty() ? "WASM module load failed" : message);
        return 0;
    }

    error_buffer.fill('\0');
    handle->instance =
            wasm_runtime_instantiate(
                    handle->module,
                    kInstanceStackSize,
                    kInstanceHeapSize,
                    error_buffer.data(),
                    static_cast<uint32_t>(error_buffer.size()));
    if (handle->instance == nullptr) {
        std::string message = error_buffer.data();
        destroyModule(handle);
        throwJava(
                env,
                "java/lang/IllegalArgumentException",
                message.empty() ? "WASM module instantiate failed" : message);
        return 0;
    }

    handle->exec_env = wasm_runtime_create_exec_env(handle->instance, kExecEnvStackSize);
    if (handle->exec_env == nullptr) {
        destroyModule(handle);
        throwJava(env, "java/lang/IllegalStateException", "WASM execution environment creation failed");
        return 0;
    }

    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_operit_util_ToolPkgWasmNative_call(
        JNIEnv *env,
        jobject,
        jlong handle_value,
        jstring function_name,
        jintArray arg_types,
        jlongArray arg_bits_array) {
    if (handle_value == 0) {
        const std::string failure = jsonFailure("WASM module handle is invalid");
        return env->NewStringUTF(failure.c_str());
    }
    if (arg_types == nullptr || arg_bits_array == nullptr) {
        const std::string failure = jsonFailure("WASM argument arrays are required");
        return env->NewStringUTF(failure.c_str());
    }

    const jsize type_count = env->GetArrayLength(arg_types);
    const jsize bits_count = env->GetArrayLength(arg_bits_array);
    if (type_count != bits_count) {
        const std::string failure = jsonFailure("WASM argument type and value arrays differ in length");
        return env->NewStringUTF(failure.c_str());
    }

    std::vector<jint> type_codes(static_cast<size_t>(type_count));
    std::vector<jlong> arg_bits(static_cast<size_t>(bits_count));
    if (type_count > 0) {
        env->GetIntArrayRegion(arg_types, 0, type_count, type_codes.data());
        if (env->ExceptionCheck()) {
            return nullptr;
        }
        env->GetLongArrayRegion(arg_bits_array, 0, bits_count, arg_bits.data());
        if (env->ExceptionCheck()) {
            return nullptr;
        }
    }

    auto *handle = reinterpret_cast<ToolPkgWasmModule *>(handle_value);
    const std::string result =
            callWasm(
                    handle,
                    jstringToString(env, function_name),
                    type_codes,
                    arg_bits);
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_operit_util_ToolPkgWasmNative_close(JNIEnv *, jobject, jlong handle_value) {
    auto *handle = reinterpret_cast<ToolPkgWasmModule *>(handle_value);
    destroyModule(handle);
}

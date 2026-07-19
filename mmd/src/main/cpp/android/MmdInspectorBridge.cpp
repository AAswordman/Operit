#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <initializer_list>
#include <mutex>
#include <string>

#include "Saba/Model/MMD/PMDFile.h"
#include "Saba/Model/MMD/PMXFile.h"
#include "Saba/Model/MMD/VMDFile.h"

namespace {

constexpr int64_t kModelFormatPmd = 1;
constexpr int64_t kModelFormatPmx = 2;
constexpr const char* kUnavailableReason =
    "Saba native source is not available. Ensure CMake fetched Saba and built MmdWrapper with OPERIT_HAS_SABA.";

std::mutex gLastErrorMutex;
std::string gLastError;

void SetLastError(const std::string& error) {
    {
        std::lock_guard<std::mutex> lock(gLastErrorMutex);
        gLastError = error;
    }
    __android_log_print(ANDROID_LOG_ERROR, "MmdInspectorBridge", "%s", error.c_str());
}

void ClearLastError() {
    std::lock_guard<std::mutex> lock(gLastErrorMutex);
    gLastError.clear();
}

std::string GetLastErrorCopy() {
    std::lock_guard<std::mutex> lock(gLastErrorMutex);
    return gLastError;
}

std::string JStringToString(JNIEnv* env, jstring value) {
    if (env == nullptr || value == nullptr) {
        return "";
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring StringToJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

jlongArray BuildLongArray(JNIEnv* env, std::initializer_list<jlong> values) {
    jlongArray result = env->NewLongArray(static_cast<jsize>(values.size()));
    if (result == nullptr) {
        return nullptr;
    }

    env->SetLongArrayRegion(result, 0, static_cast<jsize>(values.size()), values.begin());
    return result;
}

std::string ToLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

std::string GetFileExtension(const std::string& path) {
    const size_t dotPosition = path.find_last_of('.');
    if (dotPosition == std::string::npos) {
        return "";
    }
    return ToLowerAscii(path.substr(dotPosition + 1));
}

enum class ModelFileType {
    Unknown = 0,
    Pmd,
    Pmx,
};

struct ModelParseResult {
    ModelFileType fileType = ModelFileType::Unknown;
    std::string modelName;
    int64_t vertexCount = 0;
    int64_t faceCount = 0;
    int64_t materialCount = 0;
    int64_t boneCount = 0;
    int64_t morphCount = 0;
    int64_t rigidBodyCount = 0;
    int64_t jointCount = 0;
};

struct MotionParseResult {
    std::string modelName;
    int64_t motionCount = 0;
    int64_t morphCount = 0;
    int64_t cameraCount = 0;
    int64_t lightCount = 0;
    int64_t shadowCount = 0;
    int64_t ikCount = 0;
};

ModelFileType DetectModelFileType(const std::string& modelPath) {
    const std::string extension = GetFileExtension(modelPath);
    if (extension == "pmd") {
        return ModelFileType::Pmd;
    }
    if (extension == "pmx") {
        return ModelFileType::Pmx;
    }
    return ModelFileType::Unknown;
}

int64_t ToModelFormatId(ModelFileType fileType) {
    if (fileType == ModelFileType::Pmd) {
        return kModelFormatPmd;
    }
    if (fileType == ModelFileType::Pmx) {
        return kModelFormatPmx;
    }
    return 0;
}

bool ParseModelFile(const std::string& modelPath, ModelParseResult* outResult, std::string* outError) {
    if (outResult == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: model output buffer is null.";
        }
        return false;
    }

    const ModelFileType modelFileType = DetectModelFileType(modelPath);
    if (modelFileType == ModelFileType::Unknown) {
        if (outError != nullptr) {
            *outError = "unsupported model extension; expected .pmd or .pmx.";
        }
        return false;
    }

    if (modelFileType == ModelFileType::Pmd) {
        saba::PMDFile pmdFile;
        if (!saba::ReadPMDFile(&pmdFile, modelPath.c_str())) {
            if (outError != nullptr) {
                *outError = "failed to parse PMD file: " + modelPath;
            }
            return false;
        }

        outResult->fileType = modelFileType;
        outResult->modelName = pmdFile.m_header.m_modelName.ToUtf8String();
        outResult->vertexCount = static_cast<int64_t>(pmdFile.m_vertices.size());
        outResult->faceCount = static_cast<int64_t>(pmdFile.m_faces.size());
        outResult->materialCount = static_cast<int64_t>(pmdFile.m_materials.size());
        outResult->boneCount = static_cast<int64_t>(pmdFile.m_bones.size());
        outResult->morphCount = static_cast<int64_t>(pmdFile.m_morphs.size());
        outResult->rigidBodyCount = static_cast<int64_t>(pmdFile.m_rigidBodies.size());
        outResult->jointCount = static_cast<int64_t>(pmdFile.m_joints.size());
        return true;
    }

    saba::PMXFile pmxFile;
    if (!saba::ReadPMXFile(&pmxFile, modelPath.c_str())) {
        if (outError != nullptr) {
            *outError = "failed to parse PMX file: " + modelPath;
        }
        return false;
    }

    outResult->fileType = modelFileType;
    outResult->modelName = pmxFile.m_info.m_modelName;
    outResult->vertexCount = static_cast<int64_t>(pmxFile.m_vertices.size());
    outResult->faceCount = static_cast<int64_t>(pmxFile.m_faces.size());
    outResult->materialCount = static_cast<int64_t>(pmxFile.m_materials.size());
    outResult->boneCount = static_cast<int64_t>(pmxFile.m_bones.size());
    outResult->morphCount = static_cast<int64_t>(pmxFile.m_morphs.size());
    outResult->rigidBodyCount = static_cast<int64_t>(pmxFile.m_rigidbodies.size());
    outResult->jointCount = static_cast<int64_t>(pmxFile.m_joints.size());
    return true;
}

bool ParseMotionFile(const std::string& motionPath, MotionParseResult* outResult, std::string* outError) {
    if (outResult == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: motion output buffer is null.";
        }
        return false;
    }

    if (GetFileExtension(motionPath) != "vmd") {
        if (outError != nullptr) {
            *outError = "unsupported motion extension; expected .vmd.";
        }
        return false;
    }

    saba::VMDFile vmdFile;
    if (!saba::ReadVMDFile(&vmdFile, motionPath.c_str())) {
        if (outError != nullptr) {
            *outError = "failed to parse VMD file: " + motionPath;
        }
        return false;
    }

    outResult->modelName = vmdFile.m_header.m_modelName.ToUtf8String();
    outResult->motionCount = static_cast<int64_t>(vmdFile.m_motions.size());
    outResult->morphCount = static_cast<int64_t>(vmdFile.m_morphs.size());
    outResult->cameraCount = static_cast<int64_t>(vmdFile.m_cameras.size());
    outResult->lightCount = static_cast<int64_t>(vmdFile.m_lights.size());
    outResult->shadowCount = static_cast<int64_t>(vmdFile.m_shadows.size());
    outResult->ikCount = static_cast<int64_t>(vmdFile.m_iks.size());
    return true;
}

bool ReadMotionMaxFrame(const std::string& motionPath, int32_t* outMaxFrame, std::string* outError) {
    if (outMaxFrame == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: max motion frame output buffer is null.";
        }
        return false;
    }

    if (GetFileExtension(motionPath) != "vmd") {
        if (outError != nullptr) {
            *outError = "unsupported motion extension; expected .vmd.";
        }
        return false;
    }

    saba::VMDFile vmdFile;
    if (!saba::ReadVMDFile(&vmdFile, motionPath.c_str())) {
        if (outError != nullptr) {
            *outError = "failed to parse VMD file: " + motionPath;
        }
        return false;
    }

    uint32_t maxFrame = 0;
    for (const auto& motion : vmdFile.m_motions) {
        maxFrame = std::max(maxFrame, motion.m_frame);
    }
    for (const auto& morph : vmdFile.m_morphs) {
        maxFrame = std::max(maxFrame, morph.m_frame);
    }

    *outMaxFrame = static_cast<int32_t>(maxFrame);
    return true;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeIsAvailable(JNIEnv*, jclass) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeGetUnavailableReason(JNIEnv* env, jclass) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    return StringToJString(env, "");
#else
    return StringToJString(env, kUnavailableReason);
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeGetLastError(JNIEnv* env, jclass) {
    return StringToJString(env, GetLastErrorCopy());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadModelName(JNIEnv* env, jclass, jstring pathModel) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string modelPath = JStringToString(env, pathModel);
    if (modelPath.empty()) {
        SetLastError("model path is empty.");
        return nullptr;
    }

    ModelParseResult parsedModel;
    std::string parseError;
    if (!ParseModelFile(modelPath, &parsedModel, &parseError)) {
        SetLastError(parseError);
        return nullptr;
    }

    ClearLastError();
    return StringToJString(env, parsedModel.modelName);
#else
    SetLastError(kUnavailableReason);
    (void) pathModel;
    return nullptr;
#endif
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadModelSummary(JNIEnv* env, jclass, jstring pathModel) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string modelPath = JStringToString(env, pathModel);
    if (modelPath.empty()) {
        SetLastError("model path is empty.");
        return nullptr;
    }

    ModelParseResult parsedModel;
    std::string parseError;
    if (!ParseModelFile(modelPath, &parsedModel, &parseError)) {
        SetLastError(parseError);
        return nullptr;
    }

    ClearLastError();
    return BuildLongArray(
        env,
        {
            static_cast<jlong>(ToModelFormatId(parsedModel.fileType)),
            static_cast<jlong>(parsedModel.vertexCount),
            static_cast<jlong>(parsedModel.faceCount),
            static_cast<jlong>(parsedModel.materialCount),
            static_cast<jlong>(parsedModel.boneCount),
            static_cast<jlong>(parsedModel.morphCount),
            static_cast<jlong>(parsedModel.rigidBodyCount),
            static_cast<jlong>(parsedModel.jointCount),
        }
    );
#else
    SetLastError(kUnavailableReason);
    (void) env;
    (void) pathModel;
    return nullptr;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadMotionModelName(JNIEnv* env, jclass, jstring pathMotion) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string motionPath = JStringToString(env, pathMotion);
    if (motionPath.empty()) {
        SetLastError("motion path is empty.");
        return nullptr;
    }

    MotionParseResult parsedMotion;
    std::string parseError;
    if (!ParseMotionFile(motionPath, &parsedMotion, &parseError)) {
        SetLastError(parseError);
        return nullptr;
    }

    ClearLastError();
    return StringToJString(env, parsedMotion.modelName);
#else
    SetLastError(kUnavailableReason);
    (void) pathMotion;
    return nullptr;
#endif
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadMotionSummary(JNIEnv* env, jclass, jstring pathMotion) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string motionPath = JStringToString(env, pathMotion);
    if (motionPath.empty()) {
        SetLastError("motion path is empty.");
        return nullptr;
    }

    MotionParseResult parsedMotion;
    std::string parseError;
    if (!ParseMotionFile(motionPath, &parsedMotion, &parseError)) {
        SetLastError(parseError);
        return nullptr;
    }

    ClearLastError();
    return BuildLongArray(
        env,
        {
            static_cast<jlong>(parsedMotion.motionCount),
            static_cast<jlong>(parsedMotion.morphCount),
            static_cast<jlong>(parsedMotion.cameraCount),
            static_cast<jlong>(parsedMotion.lightCount),
            static_cast<jlong>(parsedMotion.shadowCount),
            static_cast<jlong>(parsedMotion.ikCount),
        }
    );
#else
    SetLastError(kUnavailableReason);
    (void) env;
    (void) pathMotion;
    return nullptr;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadMotionMaxFrame(JNIEnv* env, jclass, jstring pathMotion) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string motionPath = JStringToString(env, pathMotion);
    if (motionPath.empty()) {
        SetLastError("motion path is empty.");
        return -1;
    }

    int32_t maxFrame = 0;
    std::string parseError;
    if (!ReadMotionMaxFrame(motionPath, &maxFrame, &parseError)) {
        SetLastError(parseError);
        return -1;
    }

    ClearLastError();
    return static_cast<jint>(maxFrame);
#else
    SetLastError(kUnavailableReason);
    (void) env;
    (void) pathMotion;
    return -1;
#endif
}

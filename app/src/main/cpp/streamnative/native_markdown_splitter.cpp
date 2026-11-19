#include <jni.h>

#include <vector>

#include "streamnative/StreamOperators.h"

namespace {

inline jintArray segmentsToJIntArray(JNIEnv* env, const std::vector<streamnative::Segment>& segments) {
    jintArray out = env->NewIntArray(static_cast<jsize>(segments.size() * 3));
    if (out == nullptr) {
        return nullptr;
    }

    std::vector<jint> flat;
    flat.reserve(segments.size() * 3);
    for (const auto& s : segments) {
        flat.push_back(static_cast<jint>(s.type));
        flat.push_back(static_cast<jint>(s.start));
        flat.push_back(static_cast<jint>(s.end));
    }

    env->SetIntArrayRegion(out, 0, static_cast<jsize>(flat.size()), flat.data());
    return out;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_operit_util_streamnative_NativeMarkdownSplitter_nativeCreateBlockSession(
        JNIEnv* /*env*/,
        jobject /*thiz*/
) {
    auto* s = streamnative::createMarkdownBlockSession();
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_operit_util_streamnative_NativeMarkdownSplitter_nativeCreateInlineSession(
        JNIEnv* /*env*/,
        jobject /*thiz*/
) {
    auto* s = streamnative::createMarkdownInlineSession();
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_operit_util_streamnative_NativeMarkdownSplitter_nativeDestroySession(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle
) {
    auto* s = reinterpret_cast<streamnative::MarkdownSession*>(handle);
    streamnative::destroyMarkdownSession(s);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_ai_assistance_operit_util_streamnative_NativeMarkdownSplitter_nativeFinish(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle
) {
    auto* session = reinterpret_cast<streamnative::MarkdownSession*>(handle);
    return segmentsToJIntArray(env, streamnative::markdownSessionFinish(session));
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_ai_assistance_operit_util_streamnative_NativeMarkdownSplitter_nativePush(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle,
        jstring chunk
) {
    if (handle == 0 || chunk == nullptr) {
        return env->NewIntArray(0);
    }

    auto* s = reinterpret_cast<streamnative::MarkdownSession*>(handle);

    const jsize len = env->GetStringLength(chunk);
    const jchar* chars = env->GetStringChars(chunk, nullptr);

    std::vector<streamnative::Segment> segments = streamnative::markdownSessionPush(s, chars, static_cast<int>(len));

    env->ReleaseStringChars(chunk, chars);

    return segmentsToJIntArray(env, segments);
}

#include <jni.h>
#include <vector>
#include <android/log.h>

#include "unicorn_bridge.h"
#include "memory.h"

#define LOG_TAG "VxpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeCreateEngine(JNIEnv* /*env*/, jobject /*thiz*/) {
    uc_engine* uc = vxp_create_arm_engine();
    return reinterpret_cast<jlong>(uc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeDestroyEngine(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* uc = reinterpret_cast<uc_engine*>(handle);
    vxp_destroy_engine(uc);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeMapRegion(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jlong base, jlong size, jint perms, jbyteArray initialData) {

    auto* uc = reinterpret_cast<uc_engine*>(handle);
    if (uc == nullptr) {
        LOGE("nativeMapRegion called with null engine handle");
        return JNI_FALSE;
    }

    std::vector<uint8_t> buf;
    const uint8_t* initPtr = nullptr;
    size_t initLen = 0;

    if (initialData != nullptr) {
        jsize len = env->GetArrayLength(initialData);
        if (len > 0) {
            buf.resize(static_cast<size_t>(len));
            env->GetByteArrayRegion(initialData, 0, len, reinterpret_cast<jbyte*>(buf.data()));
            initPtr = buf.data();
            initLen = static_cast<size_t>(len);
        }
    }

    bool ok = vxp_map_region(
            uc,
            static_cast<uint64_t>(base),
            static_cast<uint64_t>(size),
            static_cast<uint32_t>(perms),
            initPtr,
            initLen);

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeReadBytes(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jlong address, jint length) {

    auto* uc = reinterpret_cast<uc_engine*>(handle);
    if (uc == nullptr || length <= 0) {
        return nullptr;
    }

    std::vector<uint8_t> buf(static_cast<size_t>(length));
    if (!vxp_read_memory(uc, static_cast<uint64_t>(address), buf.data(), buf.size())) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(length);
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, length, reinterpret_cast<jbyte*>(buf.data()));
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeWriteBytes(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jlong address, jbyteArray data) {

    auto* uc = reinterpret_cast<uc_engine*>(handle);
    if (uc == nullptr || data == nullptr) {
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_FALSE;

    std::vector<uint8_t> buf(static_cast<size_t>(len));
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte*>(buf.data()));

    bool ok = vxp_write_memory(uc, static_cast<uint64_t>(address), buf.data(), buf.size());
    return ok ? JNI_TRUE : JNI_FALSE;
}

#include <jni.h>
#include <vector>
#include <android/log.h>

#include "cpu_bridge.h"
#include "vxp_memory.h"
#include "fault_diagnostics.h"

#define LOG_TAG "VxpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeCreateEngine(JNIEnv* /*env*/, jobject /*thiz*/) {
    VxpEngine* engine = vxp_create_arm_engine();
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeDestroyEngine(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    vxp_destroy_engine(engine);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeMapRegion(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jlong base, jlong size, jint perms, jbyteArray initialData) {

    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    if (engine == nullptr) {
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

    bool ok = engine->memory.mapRegion(
            static_cast<uint64_t>(base),
            static_cast<uint64_t>(size),
            static_cast<uint32_t>(perms),
            initPtr,
            initLen);

    if (!ok) {
        LOGE("mapRegion(base=0x%llx, size=0x%llx, perms=%d) failed",
             (unsigned long long) base, (unsigned long long) size, perms);
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeReadBytes(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jlong address, jint length) {

    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    if (engine == nullptr || length <= 0) {
        return nullptr;
    }

    std::vector<uint8_t> buf(static_cast<size_t>(length));
    if (!engine->memory.apiRead(static_cast<uint64_t>(address), buf.data(), buf.size())) {
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

    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    if (engine == nullptr || data == nullptr) {
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_FALSE;

    std::vector<uint8_t> buf(static_cast<size_t>(len));
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte*>(buf.data()));

    bool ok = engine->memory.apiWrite(static_cast<uint64_t>(address), buf.data(), buf.size());
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeInstallFaultDiagnostics(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    return static_cast<jlong>(vxp_install_fault_diagnostics_hook(engine));
}

extern "C" JNIEXPORT void JNICALL
Java_com_nokia_vxp_memory_MemoryManager_nativeRemoveFaultDiagnostics(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jlong hookHandle) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    vxp_remove_fault_diagnostics_hook(engine, static_cast<uint64_t>(hookHandle));
}

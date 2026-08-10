#include <jni.h>
#include <android/log.h>

#include <cstdint>

#include "cpu_bridge.h"

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


// ============================================================================
// com.nokia.vxp.cpu.CpuState
// ============================================================================

extern "C"
JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeGetRegister(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle,
        jint regId) {

    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        LOGE(
            "CpuState.nativeGetRegister: null handle"
        );

        return 0;
    }

    const uint32_t value = vxp_get_register(
        uc,
        static_cast<int>(regId)
    );

    /*
     * Java/Kotlin Long is signed 64-bit.
     *
     * The VXP register itself is a logical unsigned 32-bit value.
     * Widen through uint64_t so values such as:
     *
     *   0xFFFFFFFF
     *
     * become:
     *
     *   4294967295
     *
     * rather than:
     *
     *   -1
     */
    return static_cast<jlong>(
        static_cast<uint64_t>(value)
    );
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle,
        jint regId,
        jlong value) {

    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        LOGE(
            "CpuState.nativeSetRegister: null handle"
        );

        return JNI_FALSE;
    }

    const uint32_t registerValue =
        static_cast<uint32_t>(
            static_cast<uint64_t>(value)
        );

    const bool ok = vxp_set_register(
        uc,
        static_cast<int>(regId),
        registerValue
    );

    return ok ? JNI_TRUE : JNI_FALSE;
}


// ============================================================================
// com.nokia.vxp.cpu.Executor
// ============================================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeRun(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle,
        jlong startAddress,
        jlong endAddress,
        jlong timeoutMicros,
        jlong maxInstructions) {

    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        LOGE(
            "Executor.nativeRun: null handle"
        );

        return static_cast<jint>(UC_ERR_HANDLE);
    }

    const uint64_t start =
        static_cast<uint64_t>(startAddress);

    const uint64_t end =
        static_cast<uint64_t>(endAddress);

    const uint64_t timeout =
        static_cast<uint64_t>(timeoutMicros);

    const size_t count =
        static_cast<size_t>(
            static_cast<uint64_t>(maxInstructions)
        );

    const uc_err err = vxp_run(
        uc,
        start,
        end,
        timeout,
        count
    );

    return static_cast<jint>(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStep(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle) {

    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        LOGE(
            "Executor.nativeStep: null handle"
        );

        return static_cast<jint>(UC_ERR_HANDLE);
    }

    const uc_err err = vxp_step(uc);

    return static_cast<jint>(err);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStop(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle) {

    auto* uc = reinterpret_cast<uc_engine*>(
        static_cast<uintptr_t>(handle)
    );

    if (uc == nullptr) {
        LOGW(
            "Executor.nativeStop: null handle"
        );

        return;
    }

    vxp_stop(uc);
}


// ============================================================================
// Error string
// ============================================================================

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeErrorString(
        JNIEnv* env,
        jobject /*thiz*/,
        jint code) {

    if (env == nullptr) {
        return nullptr;
    }

    const uc_err err =
        static_cast<uc_err>(code);

    const char* message =
        uc_strerror(err);

    if (message == nullptr) {
        message = "Unknown Unicorn error";
    }

    return env->NewStringUTF(message);
}
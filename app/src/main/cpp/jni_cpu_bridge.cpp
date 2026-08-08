
#include <jni.h>
#include <android/log.h>
#include <cstdint>

#include "cpu_bridge.h"

#define LOG_TAG "VxpNative"

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// -----------------------------------------------------------------------------
// com.nokia.vxp.cpu.CpuState
// -----------------------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeGetRegister(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle,
    jint regId)
{
    auto* uc =
        reinterpret_cast<uc_engine*>(handle);

    if (uc == nullptr)
    {
        LOGE(
            "nativeGetRegister: uc == nullptr, regId=%d",
            static_cast<int>(regId)
        );

        return 0;
    }

    const uint32_t value =
        vxp_get_register(
            uc,
            static_cast<int>(regId)
        );

    return static_cast<jlong>(
        static_cast<uint64_t>(value)
    );
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle,
    jint regId,
    jlong value)
{
    auto* uc =
        reinterpret_cast<uc_engine*>(handle);

    if (uc == nullptr)
    {
        LOGE(
            "nativeSetRegister: uc == nullptr, regId=%d",
            static_cast<int>(regId)
        );

        return JNI_FALSE;
    }

    const bool ok =
        vxp_set_register(
            uc,
            static_cast<int>(regId),
            static_cast<uint32_t>(value)
        );

    return ok
        ? JNI_TRUE
        : JNI_FALSE;
}


// -----------------------------------------------------------------------------
// com.nokia.vxp.cpu.Executor
// -----------------------------------------------------------------------------

extern "C" JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeRun(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle,
    jlong startAddress,
    jlong endAddress,
    jlong timeoutMicros,
    jlong maxInstructions)
{
    auto* uc =
        reinterpret_cast<uc_engine*>(handle);

    if (uc == nullptr)
    {
        LOGE(
            "nativeRun: uc == nullptr"
        );

        return static_cast<jint>(
            UC_ERR_HANDLE
        );
    }

    const uc_err err =
        vxp_run(
            uc,
            static_cast<uint64_t>(startAddress),
            static_cast<uint64_t>(endAddress),
            static_cast<uint64_t>(timeoutMicros),
            static_cast<size_t>(maxInstructions)
        );

    return static_cast<jint>(err);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStep(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle)
{
    auto* uc =
        reinterpret_cast<uc_engine*>(handle);

    if (uc == nullptr)
    {
        LOGE(
            "nativeStep: uc == nullptr"
        );

        return static_cast<jint>(
            UC_ERR_HANDLE
        );
    }

    const uc_err err =
        vxp_step(uc);

    return static_cast<jint>(err);
}


extern "C" JNIEXPORT void JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStop(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle)
{
    auto* uc =
        reinterpret_cast<uc_engine*>(handle);

    if (uc == nullptr)
    {
        LOGE(
            "nativeStop: uc == nullptr"
        );

        return;
    }

    vxp_stop(uc);
}


// -----------------------------------------------------------------------------
// Convert Unicorn error code to a Java String
// -----------------------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeErrorString(
    JNIEnv* env,
    jobject /*thiz*/,
    jint code)
{
    if (env == nullptr)
    {
        return nullptr;
    }

    const char* msg =
        uc_strerror(
            static_cast<uc_err>(code)
        );

    if (msg == nullptr)
    {
        msg = "Unknown Unicorn error";
    }

    return env->NewStringUTF(msg);
}

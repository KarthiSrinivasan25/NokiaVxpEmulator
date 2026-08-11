#include <jni.h>
#include <android/log.h>

#include "cpu_bridge.h"

#define LOG_TAG "VxpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --- com.nokia.vxp.cpu.CpuState -------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeGetRegister(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint regId) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    uint32_t value = vxp_get_register(engine, regId);
    // Widen unsigned 32-bit to Kotlin's signed Long without sign-extension
    // artifacts (register values like PC/SP are logically unsigned).
    return static_cast<jlong>(static_cast<uint64_t>(value));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint regId, jlong value) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    bool ok = vxp_set_register(engine, regId, static_cast<uint32_t>(value));
    return ok ? JNI_TRUE : JNI_FALSE;
}

// --- com.nokia.vxp.cpu.Executor --------------------------------------

extern "C" JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeRun(
        JNIEnv* /*env*/, jobject /*thiz*/,
        jlong handle, jlong startAddress, jlong endAddress,
        jlong timeoutMicros, jlong maxInstructions) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    VxpErr err = vxp_run(
            engine,
            static_cast<uint64_t>(startAddress),
            static_cast<uint64_t>(endAddress),
            static_cast<uint64_t>(timeoutMicros),
            static_cast<size_t>(maxInstructions));
    return static_cast<jint>(err);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStep(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    VxpErr err = vxp_step(engine);
    return static_cast<jint>(err);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStop(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* engine = reinterpret_cast<VxpEngine*>(handle);
    vxp_stop(engine);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeErrorString(JNIEnv* env, jobject /*thiz*/, jint code) {
    // Delegates to our own vxp_strerror (the custom interpreter's
    // equivalent of Unicorn's uc_strerror) rather than duplicating (and
    // risking mis-transcribing) the VxpErr enum's meanings in Kotlin.
    const char* msg = vxp_strerror(static_cast<VxpErr>(code));
    return env->NewStringUTF(msg);
}

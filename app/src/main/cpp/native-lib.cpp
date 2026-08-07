#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Matches com.nokia.vxp.nativecore.NativeBridge.getNativeVersion()
extern "C" JNIEXPORT jstring JNICALL
Java_com_nokia_vxp_nativecore_NativeBridge_getNativeVersion(JNIEnv *env, jobject /* this */) {
    LOGI("native core loaded");
#ifdef VXP_HAVE_UNICORN_BUILD
    std::string version = "NokiaVxpEmulator native core - Unicorn ARM engine, cpu/memory/mre bridges active";
#else
    std::string version = "NokiaVxpEmulator native core - WARNING: built without external/unicorn, "
                           "cpu/memory/mre bridges are NOT compiled in (see CMakeLists.txt)";
#endif
    return env->NewStringUTF(version.c_str());
}

// Matches com.nokia.vxp.nativecore.NativeBridge.nativeInit()
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_nativecore_NativeBridge_nativeInit(JNIEnv *env, jobject /* this */) {
    // Real per-session init (Unicorn engine creation, memory mapping,
    // dispatch hook installation) happens per-load in memory/MemoryManager,
    // cpu/Executor, and mre/VmDispatcher instead of here - this hook is
    // just a one-time "did the native library load correctly at all"
    // check called once from NativeBridge's init block, independent of
    // whether any VXP file has been loaded yet.
    LOGI("nativeInit() called - native library loaded successfully");
    return JNI_TRUE;
}

#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Matches com.nokia.vxp.nativecore.NativeBridge.getNativeVersion()
extern "C" JNIEXPORT jstring JNICALL
Java_com_nokia_vxp_nativecore_NativeBridge_getNativeVersion(JNIEnv *env, jobject /* this */) {
    LOGI("native core loaded (scaffold build)");
    std::string version = "NokiaVxpEmulator native core v0.1 (scaffold - no CPU/loader wired yet)";
    return env->NewStringUTF(version.c_str());
}

// Matches com.nokia.vxp.nativecore.NativeBridge.nativeInit()
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nokia_vxp_nativecore_NativeBridge_nativeInit(JNIEnv *env, jobject /* this */) {
    // Real init (Unicorn context creation, memory map setup, etc.) will be
    // wired here once the memory/ and loader/ modules are implemented.
    LOGI("nativeInit() called - scaffold only, currently a no-op");
    return JNI_TRUE;
}

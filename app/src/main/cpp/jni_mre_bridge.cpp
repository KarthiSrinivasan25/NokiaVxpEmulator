#include <jni.h>

#include "vm_dispatch_bridge.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_mre_VmDispatcher_nativeInstall(JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* engine = reinterpret_cast<VxpEngine*>(engineHandle);

    // Global ref: the hook callback outlives this JNI call's local-ref
    // scope, so it needs a reference that survives beyond this function.
    jobject globalRef = env->NewGlobalRef(thiz);
    uint64_t hookHandle = vxp_install_dispatch_hook(env, engine, globalRef);

    if (hookHandle == 0) {
        env->DeleteGlobalRef(globalRef);
    }
    return static_cast<jlong>(hookHandle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nokia_vxp_mre_VmDispatcher_nativeRemove(JNIEnv* /*env*/, jobject /*thiz*/, jlong engineHandle, jlong hookHandle) {
    auto* engine = reinterpret_cast<VxpEngine*>(engineHandle);
    vxp_remove_dispatch_hook(engine, static_cast<uint64_t>(hookHandle));
}

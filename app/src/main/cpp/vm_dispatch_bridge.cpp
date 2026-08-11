#include "vm_dispatch_bridge.h"

#include <android/log.h>
#include <limits>

#define LOG_TAG "VxpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

// Must match mre.VmDispatcher.UNHANDLED_SENTINEL exactly - the value
// Kotlin returns from onSyscallTrap() to mean "no handler registered
// for this address, let the real fault happen."
constexpr int64_t UNHANDLED_SENTINEL = std::numeric_limits<int64_t>::min();

struct DispatchContext {
    JNIEnv* env;
    jobject dispatcherGlobalRef;
    jmethodID onSyscallTrapMethod;
};

bool onFetchUnmapped(VxpEngine* engine, uint64_t address,
                      uint32_t r0, uint32_t r1, uint32_t r2, uint32_t r3, void* userData) {
    auto* ctx = reinterpret_cast<DispatchContext*>(userData);
    if (ctx == nullptr || ctx->env == nullptr || ctx->dispatcherGlobalRef == nullptr) {
        return false;
    }

    // AAPCS: first four integer/pointer args are in R0-R3. Any call
    // needing more args than that isn't representable through this trap
    // yet (would need to also read the guest's stack) - out of scope
    // until a real vm_* call is confirmed to need more than 4 args.
    jlong result = ctx->env->CallLongMethod(
        ctx->dispatcherGlobalRef, ctx->onSyscallTrapMethod,
        static_cast<jlong>(address),
        static_cast<jlong>(r0), static_cast<jlong>(r1),
        static_cast<jlong>(r2), static_cast<jlong>(r3)
    );

    if (ctx->env->ExceptionCheck()) {
        LOGE("Exception thrown from VmDispatcher.onSyscallTrap - clearing and treating as unhandled");
        ctx->env->ExceptionDescribe();
        ctx->env->ExceptionClear();
        return false;
    }

    if (result == UNHANDLED_SENTINEL) {
        LOGI("Unhandled guest call to unmapped address 0x%llx - letting the real fault happen",
             (unsigned long long) address);
        return false;
    }

    // Simulate "the call returned": R0 = result, PC = LR.
    vxp_set_register(engine, VXP_REG_R0, static_cast<uint32_t>(result));
    uint32_t lr = vxp_get_register(engine, VXP_REG_LR);
    vxp_set_register(engine, VXP_REG_PC, lr);

    return true; // tells the interpreter the access is now fine; execution resumes at the new PC
}

} // namespace

uint64_t vxp_install_dispatch_hook(JNIEnv* env, VxpEngine* engine, jobject dispatcherGlobalRef) {
    if (engine == nullptr || dispatcherGlobalRef == nullptr) {
        LOGE("vxp_install_dispatch_hook: null engine or dispatcher ref");
        return 0;
    }

    jclass cls = env->GetObjectClass(dispatcherGlobalRef);
    jmethodID methodId = env->GetMethodID(cls, "onSyscallTrap", "(JJJJJ)J");
    if (methodId == nullptr) {
        LOGE("Could not find VmDispatcher.onSyscallTrap(JJJJJ)J - check the Kotlin method signature matches");
        env->ExceptionClear();
        return 0;
    }

    auto* ctx = new DispatchContext{env, dispatcherGlobalRef, methodId};

    engine->fetchHook = &onFetchUnmapped;
    engine->fetchHookUserData = ctx;

    LOGI("Guest-call dispatch trap installed");
    // Handle is the context pointer itself - the one thing that uniquely
    // identifies this installation, and what vxp_remove_dispatch_hook()
    // needs back to know it's clearing the same installation it set up
    // (rather than one some other, unrelated caller may have since replaced).
    return reinterpret_cast<uint64_t>(ctx);
}

void vxp_remove_dispatch_hook(VxpEngine* engine, uint64_t hookHandle) {
    if (engine == nullptr || hookHandle == 0) return;
    if (reinterpret_cast<uint64_t>(engine->fetchHookUserData) == hookHandle) {
        engine->fetchHook = nullptr;
        engine->fetchHookUserData = nullptr;
    }
    // The DispatchContext allocated above is intentionally leaked here -
    // it's one small fixed-size allocation per session, and freeing it
    // safely would require certainty that nothing will invoke the hook
    // again after this returns. Reclaimed when the process exits.
}

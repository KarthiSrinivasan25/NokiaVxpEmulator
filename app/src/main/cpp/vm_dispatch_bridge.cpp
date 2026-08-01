#include "vm_dispatch_bridge.h"
#include "cpu_bridge.h"

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

bool onFetchUnmapped(uc_engine* uc, uc_mem_type /*type*/, uint64_t address,
                      int /*size*/, int64_t /*value*/, void* userData) {
    auto* ctx = reinterpret_cast<DispatchContext*>(userData);
    if (ctx == nullptr || ctx->env == nullptr || ctx->dispatcherGlobalRef == nullptr) {
        return false;
    }

    // AAPCS: first four integer/pointer args are in R0-R3. Any call
    // needing more args than that isn't representable through this trap
    // yet (would need to also read the guest's stack) - out of scope
    // until a real vm_* call is confirmed to need more than 4 args.
    uint32_t r0 = vxp_get_register(uc, VXP_REG_R0);
    uint32_t r1 = vxp_get_register(uc, VXP_REG_R1);
    uint32_t r2 = vxp_get_register(uc, VXP_REG_R2);
    uint32_t r3 = vxp_get_register(uc, VXP_REG_R3);

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
    vxp_set_register(uc, VXP_REG_R0, static_cast<uint32_t>(result));
    uint32_t lr = vxp_get_register(uc, VXP_REG_LR);
    vxp_set_register(uc, VXP_REG_PC, lr);

    return true; // tells Unicorn the access is now fine; execution resumes at the new PC
}

} // namespace

uint64_t vxp_install_dispatch_hook(JNIEnv* env, uc_engine* uc, jobject dispatcherGlobalRef) {
    if (uc == nullptr || dispatcherGlobalRef == nullptr) {
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

    uc_hook hook;
    uc_err err = uc_hook_add(
        uc, &hook, UC_HOOK_MEM_FETCH_UNMAPPED,
        reinterpret_cast<void*>(&onFetchUnmapped), ctx, 1, 0
    );
    if (err != UC_ERR_OK) {
        LOGE("uc_hook_add for dispatch trap failed: %s", uc_strerror(err));
        delete ctx;
        return 0;
    }

    LOGI("Guest-call dispatch trap installed");
    return static_cast<uint64_t>(hook);
}

void vxp_remove_dispatch_hook(uc_engine* uc, uint64_t hookHandle) {
    if (uc == nullptr || hookHandle == 0) return;
    uc_hook_del(uc, static_cast<uc_hook>(hookHandle));
    // The DispatchContext allocated above is intentionally leaked here -
    // it's one small fixed-size allocation per session, and freeing it
    // safely would require certainty that Unicorn won't invoke the hook
    // again after uc_hook_del() returns, which isn't guaranteed to be
    // synchronous across Unicorn versions. Reclaimed when the process exits.
}

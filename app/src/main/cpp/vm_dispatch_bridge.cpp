
#include "vm_dispatch_bridge.h"
#include "cpu_bridge.h"

#include <android/log.h>
#include <limits>

#define LOG_TAG "VxpNative"

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

// Must match mre.VmDispatcher.UNHANDLED_SENTINEL.
constexpr int64_t UNHANDLED_SENTINEL =
    std::numeric_limits<int64_t>::min();

struct DispatchContext {
    JNIEnv* env;
    jobject dispatcherGlobalRef;
    jmethodID onSyscallTrapMethod;
};

// -----------------------------------------------------------------------------
// Handle execution of an instruction fetch from an unmapped address.
//
// This is used as the guest-call trap mechanism. The guest branches/calls
// into an unmapped address, and we give Kotlin a chance to handle that
// address as a VM/native call.
//
// This hook does NOT handle READ_UNMAPPED or WRITE_UNMAPPED faults.
// -----------------------------------------------------------------------------

static bool onFetchUnmapped(
    uc_engine* uc,
    uc_mem_type /*type*/,
    uint64_t address,
    int /*size*/,
    int64_t /*value*/,
    void* userData) {

    auto* ctx =
        reinterpret_cast<DispatchContext*>(userData);

    if (ctx == nullptr ||
        ctx->env == nullptr ||
        ctx->dispatcherGlobalRef == nullptr ||
        ctx->onSyscallTrapMethod == nullptr) {

        LOGE(
            "onFetchUnmapped: invalid dispatch context"
        );

        return false;
    }

    // AAPCS uses R0-R3 for the first four integer/pointer arguments.
    uint32_t r0 = vxp_get_register(
        uc,
        VXP_REG_R0
    );

    uint32_t r1 = vxp_get_register(
        uc,
        VXP_REG_R1
    );

    uint32_t r2 = vxp_get_register(
        uc,
        VXP_REG_R2
    );

    uint32_t r3 = vxp_get_register(
        uc,
        VXP_REG_R3
    );

    LOGI(
        "Guest call trap: address=0x%08llx "
        "R0=0x%08x "
        "R1=0x%08x "
        "R2=0x%08x "
        "R3=0x%08x",
        static_cast<unsigned long long>(address),
        r0,
        r1,
        r2,
        r3
    );

    // Call Kotlin:
    //
    // onSyscallTrap(
    //     address,
    //     r0,
    //     r1,
    //     r2,
    //     r3
    // )
    //
    // Signature:
    // (JJJJJ)J

    jlong result =
        ctx->env->CallLongMethod(
            ctx->dispatcherGlobalRef,
            ctx->onSyscallTrapMethod,
            static_cast<jlong>(address),
            static_cast<jlong>(r0),
            static_cast<jlong>(r1),
            static_cast<jlong>(r2),
            static_cast<jlong>(r3)
        );

    // Kotlin threw an exception.
    if (ctx->env->ExceptionCheck()) {

        LOGE(
            "Exception thrown from "
            "VmDispatcher.onSyscallTrap()"
        );

        ctx->env->ExceptionDescribe();
        ctx->env->ExceptionClear();

        return false;
    }

    // Kotlin says this address has no handler.
    //
    // Returning false lets Unicorn report the original unmapped
    // instruction-fetch fault.
    if (result == UNHANDLED_SENTINEL) {

        LOGI(
            "Unhandled guest call: "
            "address=0x%08llx",
            static_cast<unsigned long long>(address)
        );

        return false;
    }

    // -------------------------------------------------------------------------
    // Simulate a normal function return.
    //
    // Return value -> R0
    // Return address -> PC
    // -------------------------------------------------------------------------

    bool r0Ok = vxp_set_register(
        uc,
        VXP_REG_R0,
        static_cast<uint32_t>(result)
    );

    if (!r0Ok) {
        LOGE(
            "Failed to set R0 for guest-call result"
        );

        return false;
    }

    uint32_t lr =
        vxp_get_register(
            uc,
            VXP_REG_LR
        );

    bool pcOk = vxp_set_register(
        uc,
        VXP_REG_PC,
        lr
    );

    if (!pcOk) {
        LOGE(
            "Failed to restore PC from LR=0x%08x",
            lr
        );

        return false;
    }

    LOGI(
        "Guest call returned: "
        "result=0x%08x "
        "PC=0x%08x",
        static_cast<uint32_t>(result),
        lr
    );

    // Tell Unicorn that the fault was handled and execution may continue.
    return true;
}

} // namespace

// -----------------------------------------------------------------------------
// Install guest-call dispatch hook.
// -----------------------------------------------------------------------------

uint64_t vxp_install_dispatch_hook(
    JNIEnv* env,
    uc_engine* uc,
    jobject dispatcherGlobalRef) {

    if (env == nullptr) {
        LOGE(
            "vxp_install_dispatch_hook: env == nullptr"
        );
        return 0;
    }

    if (uc == nullptr) {
        LOGE(
            "vxp_install_dispatch_hook: uc == nullptr"
        );
        return 0;
    }

    if (dispatcherGlobalRef == nullptr) {
        LOGE(
            "vxp_install_dispatch_hook: dispatcherGlobalRef == nullptr"
        );
        return 0;
    }

    jclass cls =
        env->GetObjectClass(
            dispatcherGlobalRef
        );

    if (cls == nullptr) {
        LOGE(
            "GetObjectClass() failed"
        );
        return 0;
    }

    jmethodID methodId =
        env->GetMethodID(
            cls,
            "onSyscallTrap",
            "(JJJJJ)J"
        );

    if (methodId == nullptr) {

        LOGE(
            "Could not find "
            "VmDispatcher.onSyscallTrap(JJJJJ)J"
        );

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteLocalRef(cls);

        return 0;
    }

    auto* ctx =
        new DispatchContext{
            env,
            dispatcherGlobalRef,
            methodId
        };

    uc_hook hook = 0;

    uc_err err =
        uc_hook_add(
            uc,
            &hook,
            UC_HOOK_MEM_FETCH_UNMAPPED,
            reinterpret_cast<void*>(&onFetchUnmapped),
            ctx,
            1,
            0
        );

    env->DeleteLocalRef(cls);

    if (err != UC_ERR_OK) {

        LOGE(
            "uc_hook_add("
            "UC_HOOK_MEM_FETCH_UNMAPPED"
            ") failed: %s",
            uc_strerror(err)
        );

        delete ctx;

        return 0;
    }

    LOGI(
        "Guest-call dispatch trap installed"
    );

    return static_cast<uint64_t>(hook);
}

// -----------------------------------------------------------------------------
// Remove guest-call dispatch hook.
// -----------------------------------------------------------------------------

void vxp_remove_dispatch_hook(
    uc_engine* uc,
    uint64_t hookHandle) {

    if (uc == nullptr) {
        return;
    }

    if (hookHandle == 0) {
        return;
    }

    uc_err err =
        uc_hook_del(
            uc,
            static_cast<uc_hook>(hookHandle)
        );

    if (err != UC_ERR_OK) {

        LOGE(
            "uc_hook_del(dispatch hook) failed: %s",
            uc_strerror(err)
        );

        return;
    }

    LOGI(
        "Guest-call dispatch trap removed"
    );

    // IMPORTANT:
    //
    // The DispatchContext contains a JNI global reference supplied by
    // the caller. Ownership/lifetime of that global reference should be
    // managed by the code that created it.
    //
    // Do not delete ctx here unless the surrounding lifecycle guarantees
    // that Unicorn cannot call the hook after uc_hook_del().
}

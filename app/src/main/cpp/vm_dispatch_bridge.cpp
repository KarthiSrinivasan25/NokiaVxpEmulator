#include "vm_dispatch_bridge.h"
#include "cpu_bridge.h"

#include <android/log.h>
#include <jni.h>
#include <limits>
#include <cstdint>

#define LOG_TAG "VxpNative"

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


namespace {

// Must match mre.VmDispatcher.UNHANDLED_SENTINEL
constexpr int64_t UNHANDLED_SENTINEL =
        std::numeric_limits<int64_t>::min();


struct DispatchContext {
    JNIEnv* env;
    jobject dispatcherGlobalRef;
    jmethodID onSyscallTrapMethod;
};


static bool isPlausibleGuestAddress(uint64_t address) {

    /*
     * VXP executable address space observed in your logs:
     *
     *   code starts around 0x00008000
     *   loaded code/data ends around 0x00033000
     *
     * Do NOT treat arbitrary values such as
     * 0x54535250 as guest syscall addresses.
     */

    if (address < 0x8000) {
        return false;
    }

    if (address > 0x00330000) {
        return false;
    }

    return true;
}


bool onFetchUnmapped(
        uc_engine* uc,
        uc_mem_type /*type*/,
        uint64_t address,
        int /*size*/,
        int64_t /*value*/,
        void* userData) {

    auto* ctx =
            reinterpret_cast<DispatchContext*>(userData);


    if (uc == nullptr) {
        LOGE("Dispatch hook: uc == NULL");
        return false;
    }


    if (ctx == nullptr) {
        LOGE(
                "Dispatch hook: context == NULL "
                "for address 0x%08llx",
                (unsigned long long) address
        );

        return false;
    }


    /*
     * IMPORTANT:
     *
     * An arbitrary unmapped address is NOT automatically a
     * VM syscall.
     *
     * In particular:
     *
     *     0x54535250
     *
     * is outside the VXP executable address range seen in
     * this emulator.
     */
    if (!isPlausibleGuestAddress(address)) {

        uint32_t pc =
                vxp_get_register(uc, VXP_REG_PC);

        uint32_t lr =
                vxp_get_register(uc, VXP_REG_LR);

        uint32_t r0 =
                vxp_get_register(uc, VXP_REG_R0);

        uint32_t r1 =
                vxp_get_register(uc, VXP_REG_R1);

        uint32_t r2 =
                vxp_get_register(uc, VXP_REG_R2);

        uint32_t r3 =
                vxp_get_register(uc, VXP_REG_R3);


        LOGE(
                "Unmapped fetch is NOT a guest syscall"
        );

        LOGE(
                "Target : 0x%08llx",
                (unsigned long long) address
        );

        LOGE(
                "PC     : 0x%08x",
                pc
        );

        LOGE(
                "LR     : 0x%08x",
                lr
        );

        LOGE(
                "R0     : 0x%08x",
                r0
        );

        LOGE(
                "R1     : 0x%08x",
                r1
        );

        LOGE(
                "R2     : 0x%08x",
                r2
        );

        LOGE(
                "R3     : 0x%08x",
                r3
        );

        /*
         * Returning false is intentional.
         *
         * Unicorn will return:
         *
         *     UC_ERR_FETCH_UNMAPPED
         *
         * instead of pretending this address is a VM syscall.
         */
        return false;
    }


    /*
     * Only now do we call the Kotlin dispatcher.
     */
    if (ctx->env == nullptr ||
        ctx->dispatcherGlobalRef == nullptr ||
        ctx->onSyscallTrapMethod == nullptr) {

        LOGE(
                "Dispatch context is incomplete"
        );

        return false;
    }


    uint32_t r0 =
            vxp_get_register(uc, VXP_REG_R0);

    uint32_t r1 =
            vxp_get_register(uc, VXP_REG_R1);

    uint32_t r2 =
            vxp_get_register(uc, VXP_REG_R2);

    uint32_t r3 =
            vxp_get_register(uc, VXP_REG_R3);


    LOGI(
            "Possible guest call:"
            " address=0x%08llx"
            " R0=0x%08x"
            " R1=0x%08x"
            " R2=0x%08x"
            " R3=0x%08x",
            (unsigned long long) address,
            r0,
            r1,
            r2,
            r3
    );


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


    if (ctx->env->ExceptionCheck()) {

        LOGE(
                "Exception thrown from "
                "VmDispatcher.onSyscallTrap()"
        );

        ctx->env->ExceptionDescribe();
        ctx->env->ExceptionClear();

        return false;
    }


    if (result == UNHANDLED_SENTINEL) {

        LOGI(
                "Guest call not handled:"
                " address=0x%08llx",
                (unsigned long long) address
        );

        return false;
    }


    /*
     * Handler returned a value.
     *
     * Simulate function return:
     *
     *     R0 = return value
     *     PC = LR
     */
    vxp_set_register(
            uc,
            VXP_REG_R0,
            static_cast<uint32_t>(result)
    );


    uint32_t lr =
            vxp_get_register(
                    uc,
                    VXP_REG_LR
            );


    /*
     * LR itself must be a valid executable address.
     */
    if (!isPlausibleGuestAddress(lr)) {

        LOGE(
                "Dispatcher returned but LR is invalid:"
                " LR=0x%08x",
                lr
        );

        return false;
    }


    vxp_set_register(
            uc,
            VXP_REG_PC,
            lr
    );


    LOGI(
            "Guest call handled:"
            " target=0x%08llx"
            " result=0x%08llx"
            " returnPC=0x%08x",
            (unsigned long long) address,
            (unsigned long long) result,
            lr
    );


    return true;
}

} // namespace


uint64_t vxp_install_dispatch_hook(
        JNIEnv* env,
        uc_engine* uc,
        jobject dispatcherGlobalRef) {

    if (env == nullptr) {

        LOGE(
                "vxp_install_dispatch_hook:"
                " env == NULL"
        );

        return 0;
    }


    if (uc == nullptr) {

        LOGE(
                "vxp_install_dispatch_hook:"
                " uc == NULL"
        );

        return 0;
    }


    if (dispatcherGlobalRef == nullptr) {

        LOGE(
                "vxp_install_dispatch_hook:"
                " dispatcherGlobalRef == NULL"
        );

        return 0;
    }


    jclass cls =
            env->GetObjectClass(
                    dispatcherGlobalRef
            );


    if (cls == nullptr) {

        LOGE(
                "Could not get VmDispatcher class"
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
                "Could not find:"
                " VmDispatcher.onSyscallTrap(JJJJJ)J"
        );

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteLocalRef(cls);

        return 0;
    }


    /*
     * Global reference is owned by the caller.
     */
    auto* ctx =
            new DispatchContext{
                    env,
                    dispatcherGlobalRef,
                    methodId
            };


    uc_hook hook{};


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
                "uc_hook_add for dispatch trap failed: %s",
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


void vxp_remove_dispatch_hook(
        uc_engine* uc,
        uint64_t hookHandle) {

    if (uc == nullptr ||
        hookHandle == 0) {

        return;
    }


    uc_err err =
            uc_hook_del(
                    uc,
                    static_cast<uc_hook>(hookHandle)
            );


    if (err != UC_ERR_OK) {

        LOGE(
                "Failed to remove dispatch hook:"
                " %s",
                uc_strerror(err)
        );

        return;
    }


    LOGI(
            "Guest-call dispatch trap removed"
    );
}
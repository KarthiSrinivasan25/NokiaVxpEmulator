#include "vm_dispatch_bridge.h"
#include "cpu_bridge.h"

#include <android/log.h>
#include <jni.h>
#include <limits>
#include <cstdint>

#define LOG_TAG "VxpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

// Must match VmDispatcher.UNHANDLED_SENTINEL.
constexpr int64_t UNHANDLED_SENTINEL =
        std::numeric_limits<int64_t>::min();

// Reserved virtual address range used for VM API trap addresses.
//
// Your VmSymbolBinder currently generates addresses such as:
//
//   0xfeed0000
//   0xfeed0010
//   0xfeed0020
//   ...
//   0xfeed0260
//
// All of these currently fit inside this 4 KB page.
constexpr uint64_t TRAP_BASE = 0xfeed0000ULL;
constexpr uint64_t TRAP_SIZE = 0x1000ULL;
constexpr uint64_t TRAP_END  = TRAP_BASE + TRAP_SIZE - 1;

struct DispatchContext {
    JavaVM* vm = nullptr;
    jobject dispatcherGlobalRef = nullptr;
    jmethodID onSyscallTrapMethod = nullptr;

    uc_hook codeHook = 0;
    bool trapPageMapped = false;
};

static bool isTrapAddress(uint64_t address) {
    return address >= TRAP_BASE && address <= TRAP_END;
}

static JNIEnv* getJNIEnv(JavaVM* vm, bool* attached) {
    if (attached != nullptr) {
        *attached = false;
    }

    if (vm == nullptr) {
        return nullptr;
    }

    JNIEnv* env = nullptr;

    jint result = vm->GetEnv(
            reinterpret_cast<void**>(&env),
            JNI_VERSION_1_6
    );

    if (result == JNI_OK) {
        return env;
    }

    if (result != JNI_EDETACHED) {
        return nullptr;
    }

    // EmulatorLoop may run on a native-created thread, so attach it
    // before calling into Kotlin/Java.
#if defined(__ANDROID__)
    JavaVMAttachArgs args{};
    args.version = JNI_VERSION_1_6;
    args.name = const_cast<char*>("VXP-EmulatorLoop");
    args.group = nullptr;

    result = vm->AttachCurrentThread(&env, &args);
#else
    result = vm->AttachCurrentThread(
            reinterpret_cast<void**>(&env),
            nullptr
    );
#endif

    if (result != JNI_OK || env == nullptr) {
        LOGE("Failed to attach native thread to JVM: %d", result);
        return nullptr;
    }

    if (attached != nullptr) {
        *attached = true;
    }

    return env;
}

static void detachJNI(JavaVM* vm, bool attached) {
    if (attached && vm != nullptr) {
        vm->DetachCurrentThread();
    }
}

/*
 * Called BEFORE Unicorn executes an instruction at the trap address.
 *
 * This is fundamentally different from UC_HOOK_MEM_FETCH_UNMAPPED:
 *
 *     old:
 *       execute unmapped address
 *              -> memory fault
 *              -> try to recover
 *
 *     new:
 *       PC == 0xfeedxxxx
 *              -> code hook fires
 *              -> dispatch syscall
 *              -> PC = LR
 *              -> continue normally
 */
static void onTrapCode(
        uc_engine* uc,
        uint64_t address,
        uint32_t /*size*/,
        void* userData) {

    auto* ctx = reinterpret_cast<DispatchContext*>(userData);

    if (ctx == nullptr || ctx->vm == nullptr ||
        ctx->dispatcherGlobalRef == nullptr ||
        ctx->onSyscallTrapMethod == nullptr) {

        LOGE("Invalid dispatch context at trap address 0x%llx",
             static_cast<unsigned long long>(address));

        // Stop rather than execute garbage at the trap address.
        uc_emu_stop(uc);
        return;
    }

    if (!isTrapAddress(address)) {
        return;
    }

    /*
     * Read guest arguments using the AAPCS ARM convention:
     *
     *   R0-R3 = first four integer/pointer arguments
     */
    uint32_t r0 = vxp_get_register(uc, VXP_REG_R0);
    uint32_t r1 = vxp_get_register(uc, VXP_REG_R1);
    uint32_t r2 = vxp_get_register(uc, VXP_REG_R2);
    uint32_t r3 = vxp_get_register(uc, VXP_REG_R3);

    /*
     * LR must be captured BEFORE changing PC.
     */
    uint32_t lr = vxp_get_register(uc, VXP_REG_LR);

    bool attached = false;
    JNIEnv* env = getJNIEnv(ctx->vm, &attached);

    if (env == nullptr) {
        LOGE(
                "Could not obtain JNIEnv for syscall 0x%llx",
                static_cast<unsigned long long>(address)
        );

        uc_emu_stop(uc);
        return;
    }

    jlong result = env->CallLongMethod(
            ctx->dispatcherGlobalRef,
            ctx->onSyscallTrapMethod,
            static_cast<jlong>(address),
            static_cast<jlong>(r0),
            static_cast<jlong>(r1),
            static_cast<jlong>(r2),
            static_cast<jlong>(r3)
    );

    if (env->ExceptionCheck()) {
        LOGE(
                "Exception thrown from VmDispatcher.onSyscallTrap "
                "for address 0x%llx",
                static_cast<unsigned long long>(address)
        );

        env->ExceptionDescribe();
        env->ExceptionClear();

        detachJNI(ctx->vm, attached);

        /*
         * Don't execute the fake trap instruction.
         * Stop the current run and let the executor report the failure.
         */
        uc_emu_stop(uc);
        return;
    }

    detachJNI(ctx->vm, attached);

    if (result == UNHANDLED_SENTINEL) {
        LOGE(
                "Unhandled VM API trap at 0x%llx",
                static_cast<unsigned long long>(address)
        );

        /*
         * We deliberately don't execute the fake trap address.
         * Stop execution because there is no implementation.
         */
        uc_emu_stop(uc);
        return;
    }

    /*
     * VM API return value.
     *
     * VXP is 32-bit ARM, so the Long returned by Kotlin is narrowed
     * to the guest's 32-bit R0.
     */
    uint32_t returnValue = static_cast<uint32_t>(result);

    vxp_set_register(
            uc,
            VXP_REG_R0,
            returnValue
    );

    /*
     * Simulate:
     *
     *     BX LR
     *
     * The fake trap is now effectively a function call that returned.
     */
    vxp_set_register(
            uc,
            VXP_REG_PC,
            lr
    );

    LOGI(
            "VM API trap handled: address=0x%llx LR=0x%08x R0=0x%08x",
            static_cast<unsigned long long>(address),
            lr,
            returnValue
    );

    /*
     * IMPORTANT:
     *
     * We do NOT call uc_emu_stop() here.
     *
     * The UC_HOOK_CODE callback fires before the fake instruction
     * executes. Since PC has now been changed to LR, Unicorn should
     * continue from LR.
     */
}


/*
 * Install the VM API trap page.
 */
static bool mapTrapPage(
        uc_engine* uc,
        DispatchContext* ctx) {

    if (uc == nullptr || ctx == nullptr) {
        return false;
    }

    /*
     * Map the reserved trap address range as executable.
     *
     * We don't need actual ARM instructions in this page because the
     * UC_HOOK_CODE callback catches execution before Unicorn executes
     * the instruction.
     */
    uc_err err = uc_mem_map(
            uc,
            TRAP_BASE,
            TRAP_SIZE,
            UC_PROT_READ | UC_PROT_EXEC
    );

    if (err == UC_ERR_MAP) {
        /*
         * It may already have been mapped.
         *
         * Check whether this is a harmless "already mapped" situation.
         * We don't treat it as fatal because the important requirement
         * is that the trap addresses are executable/mapped.
         */
        LOGI(
                "Trap page 0x%llx-0x%llx may already be mapped",
                static_cast<unsigned long long>(TRAP_BASE),
                static_cast<unsigned long long>(TRAP_END)
        );
    } else if (err != UC_ERR_OK) {
        LOGE(
                "Failed to map VM trap page 0x%llx: %s",
                static_cast<unsigned long long>(TRAP_BASE),
                uc_strerror(err)
        );
        return false;
    } else {
        LOGI(
                "Mapped VM API trap page: 0x%llx-0x%llx",
                static_cast<unsigned long long>(TRAP_BASE),
                static_cast<unsigned long long>(TRAP_END)
        );
    }

    ctx->trapPageMapped = true;
    return true;
}

} // namespace


uint64_t vxp_install_dispatch_hook(
        JNIEnv* env,
        uc_engine* uc,
        jobject dispatcherGlobalRef) {

    if (env == nullptr || uc == nullptr ||
        dispatcherGlobalRef == nullptr) {

        LOGE(
                "vxp_install_dispatch_hook: null engine, env, "
                "or dispatcher reference"
        );

        return 0;
    }

    /*
     * Get JavaVM. We store JavaVM, NOT JNIEnv.
     *
     * JNIEnv is thread-local.
     */
    JavaVM* vm = nullptr;

    if (env->GetJavaVM(&vm) != JNI_OK || vm == nullptr) {
        LOGE("Could not obtain JavaVM");
        return 0;
    }

    jclass cls = env->GetObjectClass(dispatcherGlobalRef);

    if (cls == nullptr) {
        LOGE("Could not obtain VmDispatcher class");
        return 0;
    }

    /*
     * Kotlin:
     *
     * private fun onSyscallTrap(
     *     address: Long,
     *     r0: Long,
     *     r1: Long,
     *     r2: Long,
     *     r3: Long
     * ): Long
     *
     * JNI signature:
     *
     * (JJJJJ)J
     */
    jmethodID methodId = env->GetMethodID(
            cls,
            "onSyscallTrap",
            "(JJJJJ)J"
    );

    env->DeleteLocalRef(cls);

    if (methodId == nullptr) {
        LOGE(
                "Could not find "
                "VmDispatcher.onSyscallTrap(JJJJJ)J"
        );

        env->ExceptionClear();
        return 0;
    }

    /*
     * Create a real global reference.
     *
     * The native hook can live longer than this JNI call.
     */
    jobject globalRef = env->NewGlobalRef(dispatcherGlobalRef);

    if (globalRef == nullptr) {
        LOGE("Could not create VmDispatcher global reference");
        return 0;
    }

    auto* ctx = new DispatchContext{};
    ctx->vm = vm;
    ctx->dispatcherGlobalRef = globalRef;
    ctx->onSyscallTrapMethod = methodId;

    /*
     * Reserve/map the fake VM API address range.
     */
    if (!mapTrapPage(uc, ctx)) {
        env->DeleteGlobalRef(globalRef);
        delete ctx;
        return 0;
    }

    /*
     * Intercept execution of 0xfeedxxxx.
     *
     * Do NOT use UC_HOOK_MEM_FETCH_UNMAPPED here.
     */
    uc_err err = uc_hook_add(
            uc,
            &ctx->codeHook,
            UC_HOOK_CODE,
            reinterpret_cast<void*>(&onTrapCode),
            ctx,
            TRAP_BASE,
            TRAP_END
    );

    if (err != UC_ERR_OK) {
        LOGE(
                "uc_hook_add for VM API code hook failed: %s",
                uc_strerror(err)
        );

        /*
         * Only unmap if we successfully mapped it ourselves.
         */
        if (ctx->trapPageMapped) {
            uc_mem_unmap(
                    uc,
                    TRAP_BASE,
                    TRAP_SIZE
            );
        }

        env->DeleteGlobalRef(globalRef);
        delete ctx;

        return 0;
    }

    LOGI(
            "Guest-call dispatch hook installed: "
            "trap range 0x%llx-0x%llx",
            static_cast<unsigned long long>(TRAP_BASE),
            static_cast<unsigned long long>(TRAP_END)
    );

    /*
     * Return the context pointer as the native handle.
     *
     * This is preferable to returning only the uc_hook handle because
     * we need the context later to remove the hook and release the
     * JNI global reference.
     */
    return reinterpret_cast<uint64_t>(ctx);
}


void vxp_remove_dispatch_hook(
        uc_engine* uc,
        uint64_t hookHandle) {

    if (uc == nullptr || hookHandle == 0) {
        return;
    }

    auto* ctx =
            reinterpret_cast<DispatchContext*>(hookHandle);

    if (ctx == nullptr) {
        return;
    }

    /*
     * Remove the code hook.
     */
    if (ctx->codeHook != 0) {
        uc_hook_del(
                uc,
                ctx->codeHook
        );

        ctx->codeHook = 0;
    }

    /*
     * Unmap the reserved trap page.
     *
     * Only do this if this session actually mapped it.
     */
    if (ctx->trapPageMapped) {
        uc_err err = uc_mem_unmap(
                uc,
                TRAP_BASE,
                TRAP_SIZE
        );

        if (err != UC_ERR_OK) {
            LOGE(
                    "Failed to unmap VM trap page: %s",
                    uc_strerror(err)
            );
        }

        ctx->trapPageMapped = false;
    }

    /*
     * Release the Java global reference.
     *
     * We need a valid JNIEnv on the current thread.
     */
    if (ctx->vm != nullptr &&
        ctx->dispatcherGlobalRef != nullptr) {

        bool attached = false;
        JNIEnv* env = getJNIEnv(
                ctx->vm,
                &attached
        );

        if (env != nullptr) {
            env->DeleteGlobalRef(
                    ctx->dispatcherGlobalRef
            );
        }

        detachJNI(
                ctx->vm,
                attached
        );

        ctx->dispatcherGlobalRef = nullptr;
    }

    delete ctx;

    LOGI("Guest-call dispatch hook removed");
}
// app/src/main/cpp/cpu_bridge.cpp

#include <jni.h>

#include <android/log.h>

#include <stdint.h>
#include <stddef.h>
#include <limits.h>

#include "unicorn/unicorn.h"

#define LOG_TAG "VxpNative"

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


// ============================================================================
// Internal helpers
// ============================================================================

namespace {

static uc_engine* asEngine(jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    return reinterpret_cast<uc_engine*>(
            static_cast<uintptr_t>(handle));
}

static jint toJniError(uc_err err)
{
    return static_cast<jint>(err);
}

} // namespace


// ============================================================================
// C API
//
// These functions are the ONLY definitions of the vxp_* symbols.
//
// jni_cpu_bridge.cpp must only declare these functions, not define them.
// ============================================================================

extern "C"
uc_err vxp_set_register(
        uc_engine* uc,
        int regId,
        uint32_t value)
{
    if (uc == nullptr) {
        LOGE("vxp_set_register: null Unicorn engine");
        return UC_ERR_HANDLE;
    }

    /*
     * Unicorn's uc_reg_write() expects a pointer to the register value.
     *
     * For ARM32 general-purpose registers this is a 32-bit value.
     */
    uint32_t v = value;

    uc_err err = uc_reg_write(
            uc,
            regId,
            &v);

    if (err != UC_ERR_OK) {
        LOGE(
                "uc_reg_write(reg=%d,value=0x%08x) failed: %s",
                regId,
                value,
                uc_strerror(err));
    }

    return err;
}


extern "C"
uc_err vxp_run(
        uc_engine* uc,
        uint64_t start,
        uint64_t end,
        uint64_t timeout,
        uint64_t count)
{
    if (uc == nullptr) {
        LOGE("vxp_run: null Unicorn engine");
        return UC_ERR_HANDLE;
    }

    LOGI(
            "Starting Unicorn: start=0x%llx end=0x%llx "
            "timeout=%llu count=%llu",
            static_cast<unsigned long long>(start),
            static_cast<unsigned long long>(end),
            static_cast<unsigned long long>(timeout),
            static_cast<unsigned long long>(count));

    uc_err err = uc_emu_start(
            uc,
            start,
            end,
            timeout,
            count);

    if (err != UC_ERR_OK) {
        uint32_t pc = 0;
        uint32_t sp = 0;
        uint32_t lr = 0;

        uc_reg_read(uc, UC_ARM_REG_PC, &pc);
        uc_reg_read(uc, UC_ARM_REG_SP, &sp);
        uc_reg_read(uc, UC_ARM_REG_LR, &lr);

        LOGE(
                "uc_emu_start failed: %s (%d) "
                "PC=0x%08x SP=0x%08x LR=0x%08x",
                uc_strerror(err),
                static_cast<int>(err),
                pc,
                sp,
                lr);
    }

    return err;
}


extern "C"
uc_err vxp_step(
        uc_engine* uc)
{
    if (uc == nullptr) {
        LOGE("vxp_step: null Unicorn engine");
        return UC_ERR_HANDLE;
    }

    /*
     * Read current PC first.
     *
     * Using the current PC explicitly is safer than passing zero to
     * uc_emu_start(), because the guest may legitimately be executing
     * from address zero.
     */
    uint32_t pc = 0;

    uc_err err = uc_reg_read(
            uc,
            UC_ARM_REG_PC,
            &pc);

    if (err != UC_ERR_OK) {
        LOGE(
                "vxp_step: unable to read PC: %s",
                uc_strerror(err));

        return err;
    }

    /*
     * ARM instructions are normally 4 bytes.
     *
     * If the CPSR T bit is set, the guest is in Thumb mode and an
     * instruction can be 2 or 4 bytes. Unicorn's count=1 handles the
     * actual instruction size, so the end address is kept sufficiently
     * large.
     */
    uint64_t start = static_cast<uint64_t>(pc);

    LOGI(
            "Single-step: PC=0x%08x",
            pc);

    return uc_emu_start(
            uc,
            start,
            UINT64_MAX,
            0,
            1);
}


extern "C"
uc_err vxp_stop(
        uc_engine* uc)
{
    if (uc == nullptr) {
        LOGE("vxp_stop: null Unicorn engine");
        return UC_ERR_HANDLE;
    }

    uc_err err = uc_emu_stop(uc);

    if (err != UC_ERR_OK) {
        LOGE(
                "uc_emu_stop failed: %s",
                uc_strerror(err));
    }

    return err;
}


// ============================================================================
// JNI
//
// IMPORTANT:
// These JNI functions are defined ONLY HERE.
//
// jni_cpu_bridge.cpp must NOT contain another copy.
// ============================================================================


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister(
        JNIEnv* env,
        jclass clazz,
        jlong nativeHandle,
        jint regId,
        jint value)
{
    (void)env;
    (void)clazz;

    uc_engine* uc = asEngine(nativeHandle);

    if (uc == nullptr) {
        LOGE(
                "nativeSetRegister: invalid native handle");
        return toJniError(UC_ERR_HANDLE);
    }

    uc_err err = vxp_set_register(
            uc,
            static_cast<int>(regId),
            static_cast<uint32_t>(value));

    return toJniError(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeRun(
        JNIEnv* env,
        jclass clazz,
        jlong nativeHandle,
        jlong start,
        jlong end,
        jlong timeout,
        jlong count)
{
    (void)env;
    (void)clazz;

    uc_engine* uc = asEngine(nativeHandle);

    if (uc == nullptr) {
        LOGE(
                "nativeRun: invalid native handle");
        return toJniError(UC_ERR_HANDLE);
    }

    uc_err err = vxp_run(
            uc,
            static_cast<uint64_t>(start),
            static_cast<uint64_t>(end),
            static_cast<uint64_t>(timeout),
            static_cast<uint64_t>(count));

    return toJniError(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStep(
        JNIEnv* env,
        jclass clazz,
        jlong nativeHandle)
{
    (void)env;
    (void)clazz;

    uc_engine* uc = asEngine(nativeHandle);

    if (uc == nullptr) {
        LOGE(
                "nativeStep: invalid native handle");
        return toJniError(UC_ERR_HANDLE);
    }

    uc_err err = vxp_step(uc);

    return toJniError(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStop(
        JNIEnv* env,
        jclass clazz,
        jlong nativeHandle)
{
    (void)env;
    (void)clazz;

    uc_engine* uc = asEngine(nativeHandle);

    if (uc == nullptr) {
        LOGE(
                "nativeStop: invalid native handle");
        return toJniError(UC_ERR_HANDLE);
    }

    uc_err err = vxp_stop(uc);

    return toJniError(err);
}
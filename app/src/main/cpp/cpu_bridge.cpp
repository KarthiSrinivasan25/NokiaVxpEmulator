// app/src/main/cpp/cpu_bridge.cpp

#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <cstring>

#include "unicorn/unicorn.h"

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

static const char* ucErrorName(uc_err err) {
    switch (err) {
        case UC_ERR_OK:            return "OK";
        case UC_ERR_NOMEM:         return "NOMEM";
        case UC_ERR_ARCH:          return "ARCH";
        case UC_ERR_HANDLE:        return "HANDLE";
        case UC_ERR_MODE:          return "MODE";
        case UC_ERR_VERSION:       return "VERSION";
        case UC_ERR_READ_UNMAPPED: return "READ_UNMAPPED";
        case UC_ERR_WRITE_UNMAPPED:return "WRITE_UNMAPPED";
        case UC_ERR_FETCH_UNMAPPED:return "FETCH_UNMAPPED";
        case UC_ERR_HOOK:          return "HOOK";
        case UC_ERR_INSN_INVALID:  return "INSN_INVALID";
        case UC_ERR_MAP:           return "MAP";
        case UC_ERR_WRITE_PROT:    return "WRITE_PROT";
        case UC_ERR_READ_PROT:     return "READ_PROT";
        case UC_ERR_FETCH_PROT:    return "FETCH_PROT";
        case UC_ERR_ARG:           return "ARG";
        case UC_ERR_READ_UNALIGNED:return "READ_UNALIGNED";
        case UC_ERR_WRITE_UNALIGNED:return "WRITE_UNALIGNED";
        case UC_ERR_FETCH_UNALIGNED:return "FETCH_UNALIGNED";
        case UC_ERR_HOOK_EXIST:    return "HOOK_EXIST";
        case UC_ERR_RESOURCE:      return "RESOURCE";
        case UC_ERR_EXCEPTION:     return "EXCEPTION";
        default:                   return "UNKNOWN";
    }
}

/*
 * Convert a generic register number into the Unicorn ARM register ID.
 *
 * The Java/native side normally passes ARM register numbers:
 *
 *   0  -> R0
 *   1  -> R1
 *   ...
 *   12 -> R12
 *   13 -> SP
 *   14 -> LR
 *   15 -> PC
 *   16 -> CPSR
 *
 * We also accept the actual Unicorn constants. This makes the bridge
 * tolerant of callers using either numbering scheme.
 */
static int normalizeArmRegister(int reg) {
    switch (reg) {
        case 0:  return UC_ARM_REG_R0;
        case 1:  return UC_ARM_REG_R1;
        case 2:  return UC_ARM_REG_R2;
        case 3:  return UC_ARM_REG_R3;
        case 4:  return UC_ARM_REG_R4;
        case 5:  return UC_ARM_REG_R5;
        case 6:  return UC_ARM_REG_R6;
        case 7:  return UC_ARM_REG_R7;
        case 8:  return UC_ARM_REG_R8;
        case 9:  return UC_ARM_REG_R9;
        case 10: return UC_ARM_REG_R10;
        case 11: return UC_ARM_REG_R11;
        case 12: return UC_ARM_REG_R12;
        case 13: return UC_ARM_REG_SP;
        case 14: return UC_ARM_REG_LR;
        case 15: return UC_ARM_REG_PC;
        case 16: return UC_ARM_REG_CPSR;

        default:
            /*
             * If the caller already supplied a Unicorn register ID,
             * preserve it.
             */
            return reg;
    }
}

static bool validEngine(uc_engine* uc) {
    if (uc == nullptr) {
        LOGE("Unicorn engine is null");
        return false;
    }

    return true;
}

static void logPcSp(uc_engine* uc, const char* operation) {
    if (!uc) {
        return;
    }

    uint32_t pc = 0;
    uint32_t sp = 0;
    uint32_t lr = 0;

    uc_err e1 = uc_reg_read(uc, UC_ARM_REG_PC, &pc);
    uc_err e2 = uc_reg_read(uc, UC_ARM_REG_SP, &sp);
    uc_err e3 = uc_reg_read(uc, UC_ARM_REG_LR, &lr);

    if (e1 == UC_ERR_OK && e2 == UC_ERR_OK && e3 == UC_ERR_OK) {
        LOGI(
            "%s: PC=0x%08x SP=0x%08x LR=0x%08x",
            operation,
            pc,
            sp,
            lr
        );
    }
}

} // namespace


/*
 * IMPORTANT:
 *
 * These functions deliberately use extern "C".
 *
 * The linker errors you showed are looking for:
 *
 *   vxp_set_register
 *   vxp_run
 *   vxp_step
 *   vxp_stop
 *
 * without C++ name mangling.
 *
 * Therefore these definitions must have C linkage.
 */


/* ============================================================
 * Register write
 * ============================================================ */

extern "C"
uc_err vxp_set_register(
    uc_engine* uc,
    int reg,
    uint32_t value
) {
    if (!validEngine(uc)) {
        return UC_ERR_HANDLE;
    }

    const int ucReg = normalizeArmRegister(reg);

    uint32_t v = value;

    uc_err err = uc_reg_write(
        uc,
        ucReg,
        &v
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_reg_write(reg=%d -> %d, value=0x%08x) failed: %s (%d)",
            reg,
            ucReg,
            value,
            uc_strerror(err),
            static_cast<int>(err)
        );
        return err;
    }

    LOGI(
        "Register set: reg=%d unicornReg=%d value=0x%08x",
        reg,
        ucReg,
        value
    );

    return UC_ERR_OK;
}


/* ============================================================
 * Optional register read
 *
 * Keep this here as well because some versions of the Java
 * bridge expect it.
 * ============================================================ */

extern "C"
uc_err vxp_get_register(
    uc_engine* uc,
    int reg,
    uint32_t* value
) {
    if (!validEngine(uc)) {
        return UC_ERR_HANDLE;
    }

    if (value == nullptr) {
        return UC_ERR_ARG;
    }

    const int ucReg = normalizeArmRegister(reg);

    uint32_t v = 0;

    uc_err err = uc_reg_read(
        uc,
        ucReg,
        &v
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_reg_read(reg=%d -> %d) failed: %s (%d)",
            reg,
            ucReg,
            uc_strerror(err),
            static_cast<int>(err)
        );
        return err;
    }

    *value = v;

    return UC_ERR_OK;
}


/* ============================================================
 * Run
 *
 * Signature required by your linker:
 *
 * vxp_run(
 *     uc_struct*,
 *     unsigned long,
 *     unsigned long,
 *     unsigned long,
 *     unsigned long
 * )
 *
 * Arguments:
 *
 *   uc       = Unicorn engine
 *   start    = guest start address
 *   end      = guest end address
 *   timeout  = Unicorn timeout in microseconds
 *   count    = instruction count; 0 means unlimited
 *
 * Returns Unicorn's uc_err.
 * ============================================================ */

extern "C"
uc_err vxp_run(
    uc_engine* uc,
    uint64_t start,
    uint64_t end,
    uint64_t timeout,
    uint64_t count
) {
    if (!validEngine(uc)) {
        return UC_ERR_HANDLE;
    }

    LOGI(
        "vxp_run(start=0x%llx, end=0x%llx, timeout=%llu, count=%llu)",
        static_cast<unsigned long long>(start),
        static_cast<unsigned long long>(end),
        static_cast<unsigned long long>(timeout),
        static_cast<unsigned long long>(count)
    );

    uc_err err = uc_emu_start(
        uc,
        start,
        end,
        timeout,
        count
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_emu_start(start=0x%llx, end=0x%llx) failed: %s (%d)",
            static_cast<unsigned long long>(start),
            static_cast<unsigned long long>(end),
            uc_strerror(err),
            static_cast<int>(err)
        );

        logPcSp(uc, "vxp_run fault");
    } else {
        logPcSp(uc, "vxp_run finished");
    }

    return err;
}


/* ============================================================
 * Single-step
 *
 * Executes exactly one ARM instruction.
 * ============================================================ */

extern "C"
uc_err vxp_step(
    uc_engine* uc
) {
    if (!validEngine(uc)) {
        return UC_ERR_HANDLE;
    }

    uint32_t pc = 0;

    uc_err readErr = uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &pc
    );

    if (readErr != UC_ERR_OK) {
        LOGE(
            "Unable to read PC before step: %s (%d)",
            uc_strerror(readErr),
            static_cast<int>(readErr)
        );

        return readErr;
    }

    LOGI(
        "vxp_step: PC=0x%08x",
        pc
    );

    /*
     * count=1 means execute exactly one instruction.
     *
     * end=0 is accepted by Unicorn as an effectively unrestricted
     * end address for this use. We use UINT64_MAX instead to avoid
     * accidentally stopping at address zero.
     */
    uc_err err = uc_emu_start(
        uc,
        static_cast<uint64_t>(pc),
        UINT64_MAX,
        0,
        1
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "vxp_step failed at PC=0x%08x: %s (%d)",
            pc,
            uc_strerror(err),
            static_cast<int>(err)
        );

        logPcSp(uc, "vxp_step fault");
    }

    return err;
}


/* ============================================================
 * Stop
 * ============================================================ */

extern "C"
uc_err vxp_stop(
    uc_engine* uc
) {
    if (!validEngine(uc)) {
        return UC_ERR_HANDLE;
    }

    uc_err err = uc_emu_stop(uc);

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_emu_stop failed: %s (%d)",
            uc_strerror(err),
            static_cast<int>(err)
        );
    } else {
        LOGI("Unicorn execution stopped");
    }

    return err;
}


/* ============================================================
 * JNI bridge
 *
 * These match the JNI names visible in your linker output:
 *
 * Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister
 * Java_com_nokia_vxp_cpu_Executor_nativeRun
 * Java_com_nokia_vxp_cpu_Executor_nativeStep
 * Java_com_nokia_vxp_cpu_Executor_nativeStop
 *
 * The native handle is assumed to be a jlong containing
 * uc_engine*.
 * ============================================================ */

extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_CpuState_nativeSetRegister(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong nativeEngine,
    jint reg,
    jint value
) {
    uc_engine* uc =
        reinterpret_cast<uc_engine*>(
            static_cast<uintptr_t>(nativeEngine)
        );

    uc_err err = vxp_set_register(
        uc,
        static_cast<int>(reg),
        static_cast<uint32_t>(value)
    );

    return static_cast<jint>(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeRun(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong nativeEngine,
    jlong start,
    jlong end,
    jlong timeout,
    jlong count
) {
    uc_engine* uc =
        reinterpret_cast<uc_engine*>(
            static_cast<uintptr_t>(nativeEngine)
        );

    uc_err err = vxp_run(
        uc,
        static_cast<uint64_t>(start),
        static_cast<uint64_t>(end),
        static_cast<uint64_t>(timeout),
        static_cast<uint64_t>(count)
    );

    return static_cast<jint>(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStep(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong nativeEngine
) {
    uc_engine* uc =
        reinterpret_cast<uc_engine*>(
            static_cast<uintptr_t>(nativeEngine)
        );

    uc_err err = vxp_step(uc);

    return static_cast<jint>(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_cpu_Executor_nativeStop(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong nativeEngine
) {
    uc_engine* uc =
        reinterpret_cast<uc_engine*>(
            static_cast<uintptr_t>(nativeEngine)
        );

    uc_err err = vxp_stop(uc);

    return static_cast<jint>(err);
}
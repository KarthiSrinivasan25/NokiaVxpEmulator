#include "cpu_bridge.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Translates our stable VxpRegisterId to Unicorn's real UC_ARM_REG_*
// symbol. Resolved at compile time against the actual header, so we
// never have to hardcode (and risk getting wrong) Unicorn's internal
// numeric values.
static int toUnicornRegId(int regId) {
    switch (regId) {
        case VXP_REG_R0:  return UC_ARM_REG_R0;
        case VXP_REG_R1:  return UC_ARM_REG_R1;
        case VXP_REG_R2:  return UC_ARM_REG_R2;
        case VXP_REG_R3:  return UC_ARM_REG_R3;
        case VXP_REG_R4:  return UC_ARM_REG_R4;
        case VXP_REG_R5:  return UC_ARM_REG_R5;
        case VXP_REG_R6:  return UC_ARM_REG_R6;
        case VXP_REG_R7:  return UC_ARM_REG_R7;
        case VXP_REG_R8:  return UC_ARM_REG_R8;
        case VXP_REG_R9:  return UC_ARM_REG_R9;
        case VXP_REG_R10: return UC_ARM_REG_R10;
        case VXP_REG_R11: return UC_ARM_REG_R11;
        case VXP_REG_R12: return UC_ARM_REG_R12;
        case VXP_REG_SP:  return UC_ARM_REG_SP;
        case VXP_REG_LR:  return UC_ARM_REG_LR;
        case VXP_REG_PC:  return UC_ARM_REG_PC;
        case VXP_REG_CPSR: return UC_ARM_REG_CPSR;
        default:
            LOGW("toUnicornRegId: unknown VxpRegisterId %d", regId);
            return -1;
    }
}

uint32_t vxp_get_register(uc_engine* uc, int regId) {
    if (uc == nullptr) return 0;
    int ucReg = toUnicornRegId(regId);
    if (ucReg < 0) return 0;

    uint32_t value = 0;
    uc_err err = uc_reg_read(uc, ucReg, &value);
    if (err != UC_ERR_OK) {
        LOGE("uc_reg_read(regId=%d) failed: %s", regId, uc_strerror(err));
        return 0;
    }
    return value;
}

bool vxp_set_register(uc_engine* uc, int regId, uint32_t value) {
    if (uc == nullptr) return false;
    int ucReg = toUnicornRegId(regId);
    if (ucReg < 0) return false;

    uc_err err = uc_reg_write(uc, ucReg, &value);
    if (err != UC_ERR_OK) {
        LOGE("uc_reg_write(regId=%d, value=0x%x) failed: %s", regId, value, uc_strerror(err));
        return false;
    }
    return true;
}

uc_err vxp_run(uc_engine* uc, uint64_t startAddress, uint64_t endAddress,
               uint64_t timeoutMicros, size_t maxInstructions) {
    if (uc == nullptr) return UC_ERR_HANDLE;

    uc_err err = uc_emu_start(uc, startAddress, endAddress, timeoutMicros, maxInstructions);
    if (err != UC_ERR_OK) {
        LOGE("uc_emu_start(start=0x%llx, end=0x%llx) failed: %s",
             (unsigned long long) startAddress, (unsigned long long) endAddress, uc_strerror(err));
    }
    return err;
}

uc_err vxp_step(uc_engine* uc) {
    if (uc == nullptr) return UC_ERR_HANDLE;

    // uc_emu_start's `begin` parameter sets PC before running, so we must
    // read the real current PC first - passing a sentinel here would jump
    // execution to garbage instead of single-stepping from where it is.
    uint32_t currentPc = 0;
    uc_err err = uc_reg_read(uc, UC_ARM_REG_PC, &currentPc);
    if (err != UC_ERR_OK) {
        LOGE("vxp_step: failed to read current PC: %s", uc_strerror(err));
        return err;
    }

    // `until` must NOT be a literal 0 here - Unicorn treats it as a real
    // target address, and 0 is a legitimate guest address for some real
    // VXP files (confirmed: gtrxAC/peanut.vxp's PT_LOAD segment starts
    // exactly at vaddr 0x0). count=1 already bounds this to a single
    // instruction regardless, so use a guaranteed-unreachable "until"
    // instead - matches cpu/Executor.kt's NO_END_ADDRESS_LIMIT reasoning.
    constexpr uint64_t NO_END_ADDRESS_LIMIT = 0xFFFFFFFFULL;
    err = uc_emu_start(uc, currentPc, /*until=*/ NO_END_ADDRESS_LIMIT, /*timeout=*/ 0, /*count=*/ 1);
    if (err != UC_ERR_OK) {
        LOGE("vxp_step failed: %s", uc_strerror(err));
    }
    return err;
}

void vxp_stop(uc_engine* uc) {
    if (uc == nullptr) return;
    uc_err err = uc_emu_stop(uc);
    if (err != UC_ERR_OK) {
        LOGE("uc_emu_stop failed: %s", uc_strerror(err));
    }
}

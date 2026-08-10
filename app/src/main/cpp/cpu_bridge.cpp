#include "cpu_bridge.h"

#include <android/log.h>
#include <cstdint>
#include <cstddef>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


// -----------------------------------------------------------------------------
// VXP register ID -> Unicorn ARM register ID
// -----------------------------------------------------------------------------

static int toUnicornRegId(int regId) {
    switch (regId) {

        case VXP_REG_R0:
            return UC_ARM_REG_R0;

        case VXP_REG_R1:
            return UC_ARM_REG_R1;

        case VXP_REG_R2:
            return UC_ARM_REG_R2;

        case VXP_REG_R3:
            return UC_ARM_REG_R3;

        case VXP_REG_R4:
            return UC_ARM_REG_R4;

        case VXP_REG_R5:
            return UC_ARM_REG_R5;

        case VXP_REG_R6:
            return UC_ARM_REG_R6;

        case VXP_REG_R7:
            return UC_ARM_REG_R7;

        case VXP_REG_R8:
            return UC_ARM_REG_R8;

        case VXP_REG_R9:
            return UC_ARM_REG_R9;

        case VXP_REG_R10:
            return UC_ARM_REG_R10;

        case VXP_REG_R11:
            return UC_ARM_REG_R11;

        case VXP_REG_R12:
            return UC_ARM_REG_R12;

        case VXP_REG_SP:
            return UC_ARM_REG_SP;

        case VXP_REG_LR:
            return UC_ARM_REG_LR;

        case VXP_REG_PC:
            return UC_ARM_REG_PC;

        case VXP_REG_CPSR:
            return UC_ARM_REG_CPSR;

        default:
            LOGW(
                "toUnicornRegId: unknown VxpRegisterId %d",
                regId
            );
            return -1;
    }
}


// -----------------------------------------------------------------------------
// Register access
// -----------------------------------------------------------------------------

uint32_t vxp_get_register(
        uc_engine* uc,
        int regId) {

    if (uc == nullptr) {
        LOGE("vxp_get_register: null Unicorn handle");
        return 0;
    }

    const int ucReg = toUnicornRegId(regId);

    if (ucReg < 0) {
        LOGE(
            "vxp_get_register: invalid register id %d",
            regId
        );
        return 0;
    }

    uint32_t value = 0;

    const uc_err err = uc_reg_read(
        uc,
        ucReg,
        &value
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_reg_read(regId=%d) failed: %s",
            regId,
            uc_strerror(err)
        );

        return 0;
    }

    return value;
}


bool vxp_set_register(
        uc_engine* uc,
        int regId,
        uint32_t value) {

    if (uc == nullptr) {
        LOGE("vxp_set_register: null Unicorn handle");
        return false;
    }

    const int ucReg = toUnicornRegId(regId);

    if (ucReg < 0) {
        LOGE(
            "vxp_set_register: invalid register id %d",
            regId
        );
        return false;
    }

    const uc_err err = uc_reg_write(
        uc,
        ucReg,
        &value
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_reg_write(regId=%d, value=0x%08x) failed: %s",
            regId,
            value,
            uc_strerror(err)
        );

        return false;
    }

    return true;
}


// -----------------------------------------------------------------------------
// Run
// -----------------------------------------------------------------------------

uc_err vxp_run(
        uc_engine* uc,
        uint64_t startAddress,
        uint64_t endAddress,
        uint64_t timeoutMicros,
        size_t maxInstructions) {

    if (uc == nullptr) {
        LOGE("vxp_run: null Unicorn handle");
        return UC_ERR_HANDLE;
    }

    LOGI(
        "vxp_run: start=0x%llx end=0x%llx timeout=%llu count=%zu",
        static_cast<unsigned long long>(startAddress),
        static_cast<unsigned long long>(endAddress),
        static_cast<unsigned long long>(timeoutMicros),
        maxInstructions
    );

    const uc_err err = uc_emu_start(
        uc,
        startAddress,
        endAddress,
        timeoutMicros,
        maxInstructions
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_emu_start(start=0x%llx, end=0x%llx) failed: %s",
            static_cast<unsigned long long>(startAddress),
            static_cast<unsigned long long>(endAddress),
            uc_strerror(err)
        );
    }

    return err;
}


// -----------------------------------------------------------------------------
// Single-step
// -----------------------------------------------------------------------------

uc_err vxp_step(
        uc_engine* uc) {

    if (uc == nullptr) {
        LOGE("vxp_step: null Unicorn handle");
        return UC_ERR_HANDLE;
    }

    uint32_t currentPc = 0;

    uc_err err = uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &currentPc
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "vxp_step: failed to read current PC: %s",
            uc_strerror(err)
        );

        return err;
    }

    /*
     * Important:
     *
     * uc_emu_start(begin, until, timeout, count)
     *
     * The `begin` address is used as the starting PC.
     *
     * Therefore we must use the actual current PC here.
     *
     * count=1 guarantees that only one instruction is executed.
     */
    constexpr uint64_t NO_END_ADDRESS_LIMIT = 0xFFFFFFFFULL;

    err = uc_emu_start(
        uc,
        static_cast<uint64_t>(currentPc),
        NO_END_ADDRESS_LIMIT,
        0,
        1
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "vxp_step: PC=0x%08x failed: %s",
            currentPc,
            uc_strerror(err)
        );
    }

    return err;
}


// -----------------------------------------------------------------------------
// Stop
// -----------------------------------------------------------------------------

void vxp_stop(
        uc_engine* uc) {

    if (uc == nullptr) {
        LOGW("vxp_stop: null Unicorn handle");
        return;
    }

    const uc_err err = uc_emu_stop(uc);

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_emu_stop failed: %s",
            uc_strerror(err)
        );
    }
}
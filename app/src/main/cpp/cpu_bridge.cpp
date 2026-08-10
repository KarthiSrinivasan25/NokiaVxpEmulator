#include <cstdint>
#include <cstring>

#include <unicorn/unicorn.h>

//
// cpu_bridge.cpp
//
// IMPORTANT:
// This file contains ONLY the generic VXP/Unicorn CPU bridge.
//
// JNI functions belong in:
//     jni_cpu_bridge.cpp
//
// Do NOT put Java_com_nokia_vxp_cpu_* functions in this file.
//

namespace {

static inline bool valid_engine(uc_engine* uc) {
    return uc != nullptr;
}

} // namespace


// ============================================================================
// Register access
// ============================================================================

uint32_t vxp_get_register(uc_engine* uc, int reg) {
    if (!valid_engine(uc)) {
        return 0;
    }

    uint32_t value = 0;

    uc_err err = uc_reg_read(
        uc,
        reg,
        &value
    );

    if (err != UC_ERR_OK) {
        return 0;
    }

    return value;
}


void vxp_set_register(
    uc_engine* uc,
    int reg,
    uint32_t value
) {
    if (!valid_engine(uc)) {
        return;
    }

    uc_reg_write(
        uc,
        reg,
        &value
    );
}


// ============================================================================
// CPU execution
// ============================================================================

uc_err vxp_run(
    uc_engine* uc,
    uint64_t start,
    uint64_t end,
    uint64_t timeout,
    uint64_t count
) {
    if (!valid_engine(uc)) {
        return UC_ERR_HANDLE;
    }

    /*
     * Unicorn:
     *
     * uc_emu_start(
     *     uc,
     *     begin,
     *     until,
     *     timeout,
     *     count
     * );
     *
     * count == 0 means execute until the end address,
     * timeout == 0 means no timeout.
     */

    return uc_emu_start(
        uc,
        start,
        end,
        timeout,
        count
    );
}


// ============================================================================
// Single-step
// ============================================================================

uc_err vxp_step(uc_engine* uc) {
    if (!valid_engine(uc)) {
        return UC_ERR_HANDLE;
    }

    uint32_t pc = 0;

    uc_err err = uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &pc
    );

    if (err != UC_ERR_OK) {
        return err;
    }

    /*
     * Execute exactly one ARM instruction.
     *
     * The current PC is used as the start address.
     * count = 1 guarantees a single instruction.
     */

    return uc_emu_start(
        uc,
        static_cast<uint64_t>(pc),
        0,
        0,
        1
    );
}


// ============================================================================
// Stop execution
// ============================================================================

uc_err vxp_stop(uc_engine* uc) {
    if (!valid_engine(uc)) {
        return UC_ERR_HANDLE;
    }

    return uc_emu_stop(uc);
}
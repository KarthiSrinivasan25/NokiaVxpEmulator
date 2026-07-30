#pragma once

#include <unicorn/unicorn.h>
#include <cstdint>

// Our own stable register IDs (mirrors cpu/Registers.kt exactly - keep
// both in sync). Deliberately NOT Unicorn's raw UC_ARM_REG_* values, so
// the Kotlin side never has to know or guess Unicorn's internal numbering;
// vxp_get_register/vxp_set_register translate via a switch statement
// against the real symbolic constants from <unicorn/unicorn.h>.
enum VxpRegisterId {
    VXP_REG_R0 = 0, VXP_REG_R1, VXP_REG_R2, VXP_REG_R3,
    VXP_REG_R4, VXP_REG_R5, VXP_REG_R6, VXP_REG_R7,
    VXP_REG_R8, VXP_REG_R9, VXP_REG_R10, VXP_REG_R11, VXP_REG_R12,
    VXP_REG_SP = 13, VXP_REG_LR = 14, VXP_REG_PC = 15,
    VXP_REG_CPSR = 16
};

// Returns 0 on an unknown register id or a null engine (can't distinguish
// that from a genuine zero value - callers needing to detect failure
// should check the engine handle themselves first).
uint32_t vxp_get_register(uc_engine* uc, int regId);

bool vxp_set_register(uc_engine* uc, int regId, uint32_t value);

// Runs the engine from [startAddress] until [endAddress] is reached (0 =
// run until explicitly stopped / an error / instruction limit hit),
// stopping early after maxInstructions if > 0. Returns the uc_err code
// (UC_ERR_OK on a clean stop).
uc_err vxp_run(uc_engine* uc, uint64_t startAddress, uint64_t endAddress,
               uint64_t timeoutMicros, size_t maxInstructions);

// Executes exactly one instruction starting at the CPU's current PC.
uc_err vxp_step(uc_engine* uc);

void vxp_stop(uc_engine* uc);

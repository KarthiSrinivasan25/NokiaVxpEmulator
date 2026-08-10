#ifndef VXP_CPU_BRIDGE_H
#define VXP_CPU_BRIDGE_H

#include <cstddef>
#include <cstdint>

#include <unicorn/unicorn.h>
#include <unicorn/arm.h>


// ============================================================================
// Stable VXP register IDs
//
// These IDs are part of the VXP application's API.
// Do NOT use the Unicorn numeric values here.
// ============================================================================

enum VxpRegisterId {

    VXP_REG_R0 = 0,
    VXP_REG_R1 = 1,
    VXP_REG_R2 = 2,
    VXP_REG_R3 = 3,

    VXP_REG_R4 = 4,
    VXP_REG_R5 = 5,
    VXP_REG_R6 = 6,
    VXP_REG_R7 = 7,

    VXP_REG_R8 = 8,
    VXP_REG_R9 = 9,
    VXP_REG_R10 = 10,
    VXP_REG_R11 = 11,

    VXP_REG_R12 = 12,

    VXP_REG_SP = 13,
    VXP_REG_LR = 14,
    VXP_REG_PC = 15,

    VXP_REG_CPSR = 16
};


// ============================================================================
// Register API
// ============================================================================

uint32_t vxp_get_register(
        uc_engine* uc,
        int regId
);

bool vxp_set_register(
        uc_engine* uc,
        int regId,
        uint32_t value
);


// ============================================================================
// Execution API
// ============================================================================

uc_err vxp_run(
        uc_engine* uc,
        uint64_t startAddress,
        uint64_t endAddress,
        uint64_t timeoutMicros,
        size_t maxInstructions
);

uc_err vxp_step(
        uc_engine* uc
);

void vxp_stop(
        uc_engine* uc
);

#endif // VXP_CPU_BRIDGE_H
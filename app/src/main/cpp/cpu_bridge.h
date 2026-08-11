#pragma once

#include "arm_cpu.h"
#include "vxp_memory.h"
#include <cstdint>
#include <cstddef>

// Our own stable register IDs (mirrors cpu/Registers.kt exactly - keep
// both in sync).
enum VxpRegisterId {
    VXP_REG_R0 = 0, VXP_REG_R1, VXP_REG_R2, VXP_REG_R3,
    VXP_REG_R4, VXP_REG_R5, VXP_REG_R6, VXP_REG_R7,
    VXP_REG_R8, VXP_REG_R9, VXP_REG_R10, VXP_REG_R11, VXP_REG_R12,
    VXP_REG_SP = 13, VXP_REG_LR = 14, VXP_REG_PC = 15,
    VXP_REG_CPSR = 16
};

// Our own error codes, taking over the role Unicorn's uc_err used to play.
// Executor.kt only ever compares against 0 for success and otherwise
// displays nativeErrorString(code), so keeping 0 = success and giving
// every other case a distinct code + string is all that's required to
// stay a drop-in replacement.
enum VxpErr {
    VXP_ERR_OK = 0,
    VXP_ERR_HANDLE,
    VXP_ERR_READ_UNMAPPED,
    VXP_ERR_WRITE_UNMAPPED,
    VXP_ERR_FETCH_UNMAPPED,
    VXP_ERR_READ_PROT,
    VXP_ERR_WRITE_PROT,
    VXP_ERR_FETCH_PROT,
    VXP_ERR_INSN_INVALID,
    VXP_ERR_MAP,
};

const char* vxp_strerror(VxpErr err);

// Fetch-unmapped trap callback, used by vm_dispatch_bridge.cpp to stand in
// for "the guest just called an MRE OS API function" (see that file's doc
// comment for the full rationale - unchanged from the Unicorn-backed
// version other than taking a VxpEngine* instead of a uc_engine*).
// Return true if the callback updated CPU state to resolve the access
// (execution resumes at the new PC); false to let the real fault surface.
using VxpFetchUnmappedHook = bool (*)(struct VxpEngine* engine, uint64_t address,
                                       uint32_t r0, uint32_t r1, uint32_t r2, uint32_t r3,
                                       void* userData);

// Diagnostic-only hook fired on ANY fault (never handles it) - used by
// fault_diagnostics.cpp to log the faulting address/PC/SP/LR to logcat
// before the real error propagates up to Executor.kt.
using VxpInvalidAccessHook = void (*)(struct VxpEngine* engine, VxpErr err, uint64_t address,
                                       int size, void* userData);

// Owns one running emulator session's CPU + memory. Opaque to Kotlin (held
// as a jlong handle), same role uc_engine* used to play.
struct VxpEngine {
    VxpMemory memory;
    ArmCpu cpu;
    volatile bool stopRequested = false;

    VxpFetchUnmappedHook fetchHook = nullptr;
    void* fetchHookUserData = nullptr;
    VxpInvalidAccessHook invalidHook = nullptr;
    void* invalidHookUserData = nullptr;

    VxpEngine() : cpu(&memory) {}
};

// Creates a fresh engine (ARM state, all registers zeroed, CPSR = 0x10).
// Never fails (no external library/subprocess to fail to open, unlike
// Unicorn) - kept as a pointer return for interface parity and so a
// future real failure mode (e.g. allocation failure) has somewhere to go.
VxpEngine* vxp_create_arm_engine();
void vxp_destroy_engine(VxpEngine* engine);

// Returns 0 on an unknown register id or a null engine.
uint32_t vxp_get_register(VxpEngine* engine, int regId);
bool vxp_set_register(VxpEngine* engine, int regId, uint32_t value);

// Runs from [startAddress] until [endAddress] is reached (checked BEFORE
// executing the instruction at that address, matching Unicorn's uc_emu_start
// semantics that Executor.kt's NO_END_ADDRESS_LIMIT convention already
// relies on), for at most [maxInstructions] (0 = unlimited) and/or
// [timeoutMicros] (0 = unlimited). Returns VXP_ERR_OK on a clean stop
// (address/instruction/timeout limit reached, or stop() called).
VxpErr vxp_run(VxpEngine* engine, uint64_t startAddress, uint64_t endAddress,
               uint64_t timeoutMicros, size_t maxInstructions);

// Executes exactly one guest instruction from the current PC (a handled
// fetch-unmapped trap doesn't count against this - matches the old
// Unicorn-backed vxp_step's uc_emu_start(count=1) behavior).
VxpErr vxp_step(VxpEngine* engine);

void vxp_stop(VxpEngine* engine);

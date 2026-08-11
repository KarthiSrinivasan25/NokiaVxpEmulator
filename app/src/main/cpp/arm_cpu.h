#pragma once

#include <cstdint>
#include <cstddef>

// ---------------------------------------------------------------------------
// Custom ARMv4T (ARM7TDMI-class) interpreter.
//
// This replaces Unicorn Engine as the CPU emulation backend. It's a plain
// fetch-decode-execute interpreter (no JIT, no dynamic recompilation) that
// implements the subset of the ARM and Thumb instruction sets that real
// ARM7/ARM9-class firmware compiled for MRE-era Nokia phones actually uses:
// data-processing, branches (incl. BX/BLX-style mode switches), single/
// halfword/signed data transfer, block data transfer (LDM/STM), and
// multiply. Anything genuinely exotic (coprocessor instructions, etc.)
// wasn't in scope for the phones this targets and is reported as an
// invalid-instruction fault rather than silently mis-executed.
//
// Design note: this header (and arm_cpu.cpp) is intentionally free of any
// Android/JNI/Unicorn dependency so the interpreter itself can be unit
// tested as plain host C++. The Android-facing glue (guest memory backed
// by paged regions, JNI hooks for the MRE syscall trap, logcat diagnostics)
// lives in vxp_engine.h/.cpp and memory.h/.cpp, which wrap this class.
// ---------------------------------------------------------------------------

enum VxpRegId {
    ARM_R0 = 0, ARM_R1, ARM_R2, ARM_R3, ARM_R4, ARM_R5, ARM_R6, ARM_R7,
    ARM_R8, ARM_R9, ARM_R10, ARM_R11, ARM_R12,
    ARM_SP = 13, ARM_LR = 14, ARM_PC = 15,
    ARM_CPSR = 16
};

// CPSR bit positions (matches cpu/Flags.kt on the Kotlin side).
enum CpsrBits {
    CPSR_BIT_N = 31,
    CPSR_BIT_Z = 30,
    CPSR_BIT_C = 29,
    CPSR_BIT_V = 28,
    CPSR_BIT_Q = 27,
    CPSR_BIT_T = 5, // Thumb state
};

// Outcome of a single fetch/execute step. Mirrors the fault categories the
// rest of the codebase already understands (see memory.h's old Unicorn
// uc_mem_type usage) so callers upstream don't need to change.
enum class ArmFault {
    None = 0,
    ReadUnmapped,
    WriteUnmapped,
    FetchUnmapped,
    ReadProt,
    WriteProt,
    FetchProt,
    InvalidInsn,
};

// Abstract guest-memory interface the interpreter reads/writes/fetches
// through. Implemented by VxpMemory (memory.h/.cpp) against the real paged
// guest address space; kept abstract here purely so arm_cpu.cpp/.h don't
// need to know about that representation (or Android) at all.
class ArmMemoryBus {
public:
    virtual ~ArmMemoryBus() = default;

    // Read/write `len` bytes at `address`. Return false (and set *fault to
    // the specific reason) if the access can't be satisfied - unmapped or
    // insufficient permissions. `forExec` marks an instruction fetch
    // (checked against exec permission / triggers the fetch-unmapped hook
    // path rather than a plain data fault).
    virtual bool read(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) = 0;
    virtual bool write(uint32_t address, const uint8_t* data, size_t len, ArmFault* fault) = 0;
    virtual bool fetch(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) = 0;
};

class ArmCpu {
public:
    explicit ArmCpu(ArmMemoryBus* bus) : bus_(bus) {}

    uint32_t getReg(int regId) const;
    void setReg(int regId, uint32_t value);

    uint32_t cpsr() const { return cpsr_; }
    void setCpsr(uint32_t value) { cpsr_ = value; }

    bool thumbMode() const { return (cpsr_ >> CPSR_BIT_T) & 1u; }

    // Raw PC (the actual address of the instruction about to execute),
    // without the ARM7TDMI pipeline read-bias getReg(ARM_PC) applies.
    // Used by the run-loop (vxp_engine.cpp) for endAddress comparisons
    // and step counting - those need the real fetch address, not the
    // "what a guest instruction sees when it reads R15" value.
    uint32_t rawPc() const { return r_[ARM_PC]; }

    // Executes exactly one instruction at the current PC (ARM or Thumb,
    // per the CPSR T bit). Returns the fault that stopped execution, or
    // ArmFault::None on a normal single-instruction step. On a fault,
    // PC is left pointing at the faulting instruction (not advanced),
    // matching Unicorn's observable behavior that callers already rely on.
    ArmFault stepOne();

private:
    ArmMemoryBus* bus_;
    uint32_t r_[16] = {0};
    uint32_t cpsr_ = 0x10; // User mode, ARM state, all flags clear

    // --- condition / flag helpers ---
    bool conditionPassed(uint32_t cond) const;
    void setNZ(uint32_t result);
    void setNZCV_add(uint32_t a, uint32_t b, uint32_t result);
    void setNZCV_sub(uint32_t a, uint32_t b, uint32_t result);

    // --- ARM (32-bit) execution ---
    ArmFault stepArm();
    struct Shifted { uint32_t value; bool carryOut; };
    Shifted shifterOperand(uint32_t insn, bool immediateForm);
    ArmFault execDataProcessing(uint32_t insn);
    ArmFault execBranch(uint32_t insn);
    ArmFault execBranchExchange(uint32_t insn);
    ArmFault execSingleDataTransfer(uint32_t insn);
    ArmFault execHalfwordSignedTransfer(uint32_t insn);
    ArmFault execBlockDataTransfer(uint32_t insn);
    ArmFault execMultiply(uint32_t insn);

    // --- Thumb (16-bit) execution ---
    ArmFault stepThumb();
    ArmFault execThumb(uint16_t insn);
};

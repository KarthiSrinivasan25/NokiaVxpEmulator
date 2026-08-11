#include "cpu_bridge.h"

#include <android/log.h>
#include <chrono>

#define LOG_TAG "VxpNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

// An address guaranteed to never be legitimately reached by guest code -
// used as the "no end-address limit" sentinel, matching Executor.kt's own
// NO_END_ADDRESS_LIMIT constant and reasoning (0 is a real, legitimate
// guest address for some real VXP files, so it can't double as "no limit").
constexpr uint64_t NO_END_ADDRESS_LIMIT = 0xFFFFFFFFULL;

VxpErr faultToErr(ArmFault fault) {
    switch (fault) {
        case ArmFault::None:           return VXP_ERR_OK;
        case ArmFault::ReadUnmapped:   return VXP_ERR_READ_UNMAPPED;
        case ArmFault::WriteUnmapped:  return VXP_ERR_WRITE_UNMAPPED;
        case ArmFault::FetchUnmapped:  return VXP_ERR_FETCH_UNMAPPED;
        case ArmFault::ReadProt:       return VXP_ERR_READ_PROT;
        case ArmFault::WriteProt:      return VXP_ERR_WRITE_PROT;
        case ArmFault::FetchProt:      return VXP_ERR_FETCH_PROT;
        case ArmFault::InvalidInsn:    return VXP_ERR_INSN_INVALID;
    }
    return VXP_ERR_INSN_INVALID;
}

// Real ARM cores (and Unicorn/QEMU, which this replaces) treat bit0 of
// any *directly written* PC value as an ARM/Thumb interworking selector -
// the same convention ELF entry points and Thumb function pointers use
// (an odd address means "this is a Thumb target"): writing 0x951D to PC
// switches CPSR.T on and actually starts execution at 0x951C. This is
// NOT something arm_cpu.cpp's execBranchExchange() needs to duplicate
// (BX/BLX already do their own bit0 handling per-instruction) - it only
// matters for the two places PC gets set *from the outside*, bypassing
// any instruction: CpuState.setRegister(PC, ...) (initEntry() et al,
// here) and vxp_run()'s own startAddress seed (below). Skipping this
// was silently corrupting any odd (Thumb-marked) entry/callback address
// into a misaligned PC instead of switching modes, matching exactly the
// "invalid or unsupported instruction" fault seen with PC==LR==an odd
// address right after a fresh initEntry().
void setPcHonoringThumbBit(VxpEngine* engine, uint32_t value) {
    uint32_t cpsr = engine->cpu.cpsr();
    if (value & 1u) {
        cpsr |= (1u << CPSR_BIT_T);
    } else {
        cpsr &= ~(1u << CPSR_BIT_T);
    }
    engine->cpu.setCpsr(cpsr);
    engine->cpu.setReg(ARM_PC, value & ~1u);
}

} // namespace

const char* vxp_strerror(VxpErr err) {
    switch (err) {
        case VXP_ERR_OK:              return "OK";
        case VXP_ERR_HANDLE:          return "invalid engine handle";
        case VXP_ERR_READ_UNMAPPED:   return "invalid memory read (unmapped)";
        case VXP_ERR_WRITE_UNMAPPED:  return "invalid memory write (unmapped)";
        case VXP_ERR_FETCH_UNMAPPED:  return "invalid instruction fetch (unmapped)";
        case VXP_ERR_READ_PROT:       return "invalid memory read (not readable)";
        case VXP_ERR_WRITE_PROT:      return "invalid memory write (not writable)";
        case VXP_ERR_FETCH_PROT:      return "invalid instruction fetch (not executable)";
        case VXP_ERR_INSN_INVALID:    return "invalid or unsupported instruction";
        case VXP_ERR_MAP:             return "invalid memory mapping request";
        default:                      return "unknown error";
    }
}

VxpEngine* vxp_create_arm_engine() {
    auto* engine = new VxpEngine();
    LOGI("Custom ARM interpreter engine created (ARMv4T-class, no external CPU library)");
    return engine;
}

void vxp_destroy_engine(VxpEngine* engine) {
    if (engine == nullptr) return;
    delete engine;
    LOGI("Engine destroyed");
}

uint32_t vxp_get_register(VxpEngine* engine, int regId) {
    if (engine == nullptr) return 0;
    if (regId < 0 || regId > VXP_REG_CPSR) {
        LOGW("vxp_get_register: unknown regId %d", regId);
        return 0;
    }
    // Note: unlike the guest-visible getReg() used during instruction
    // execution, register inspection from Kotlin (CpuState.getRegister)
    // wants the raw architectural value - PC without the pipeline +8/+4
    // read-bias - since that's what a debugger/register-viewer should
    // display. rawPc() gives that for PC; every other register has no bias.
    if (regId == VXP_REG_PC) return engine->cpu.rawPc();
    if (regId == VXP_REG_CPSR) return engine->cpu.cpsr();
    return engine->cpu.getReg(regId);
}

bool vxp_set_register(VxpEngine* engine, int regId, uint32_t value) {
    if (engine == nullptr) return false;
    if (regId < 0 || regId > VXP_REG_CPSR) {
        LOGW("vxp_set_register: unknown regId %d", regId);
        return false;
    }
    if (regId == VXP_REG_PC) {
        setPcHonoringThumbBit(engine, value);
    } else {
        engine->cpu.setReg(regId, value);
    }
    return true;
}

VxpErr vxp_run(VxpEngine* engine, uint64_t startAddress, uint64_t endAddress,
               uint64_t timeoutMicros, size_t maxInstructions) {
    if (engine == nullptr) return VXP_ERR_HANDLE;

    engine->stopRequested = false;
    setPcHonoringThumbBit(engine, static_cast<uint32_t>(startAddress));

    const auto start = std::chrono::steady_clock::now();
    size_t executed = 0;

    while (true) {
        if (engine->stopRequested) {
            return VXP_ERR_OK;
        }
        if (endAddress != NO_END_ADDRESS_LIMIT && engine->cpu.rawPc() == static_cast<uint32_t>(endAddress)) {
            return VXP_ERR_OK;
        }
        if (maxInstructions > 0 && executed >= maxInstructions) {
            return VXP_ERR_OK;
        }
        if (timeoutMicros > 0) {
            auto elapsedUs = std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now() - start).count();
            if (static_cast<uint64_t>(elapsedUs) >= timeoutMicros) {
                return VXP_ERR_OK;
            }
        }

        uint32_t faultPc = engine->cpu.rawPc();
        ArmFault fault = engine->cpu.stepOne();

        if (fault == ArmFault::FetchUnmapped && engine->fetchHook != nullptr) {
            uint32_t r0 = engine->cpu.getReg(ARM_R0), r1 = engine->cpu.getReg(ARM_R1);
            uint32_t r2 = engine->cpu.getReg(ARM_R2), r3 = engine->cpu.getReg(ARM_R3);
            bool handled = engine->fetchHook(engine, faultPc, r0, r1, r2, r3, engine->fetchHookUserData);
            if (handled) {
                executed++;
                continue;
            }
        }

        if (fault != ArmFault::None) {
            VxpErr err = faultToErr(fault);
            // Fetch faults happen AT the instruction address (PC); read/
            // write faults happen at whatever data address the faulting
            // LDR/STR/LDM/STM actually targeted, which is very often a
            // different address from PC - use lastFaultAddress() for
            // those, or the log just repeats the instruction location and
            // looks like "it faulted writing to itself", which is
            // virtually never actually what happened.
            bool isFetchFault = (fault == ArmFault::FetchUnmapped || fault == ArmFault::FetchProt);
            uint32_t reportAddress = isFetchFault ? faultPc : engine->cpu.lastFaultAddress();
            if (engine->invalidHook != nullptr) {
                engine->invalidHook(engine, err, reportAddress, 4, engine->invalidHookUserData);
            }
            LOGE("vxp_run: stopped with %s at pc=0x%x (fault address=0x%x)",
                 vxp_strerror(err), faultPc, reportAddress);
            return err;
        }

        executed++;
    }
}

VxpErr vxp_step(VxpEngine* engine) {
    if (engine == nullptr) return VXP_ERR_HANDLE;

    // Loop so a handled fetch-unmapped trap (an MRE syscall trampoline)
    // doesn't itself count as "the one instruction" - matches the old
    // Unicorn-backed step's observable behavior.
    for (int guard = 0; guard < 64; guard++) {
        uint32_t faultPc = engine->cpu.rawPc();
        ArmFault fault = engine->cpu.stepOne();

        if (fault == ArmFault::FetchUnmapped && engine->fetchHook != nullptr) {
            uint32_t r0 = engine->cpu.getReg(ARM_R0), r1 = engine->cpu.getReg(ARM_R1);
            uint32_t r2 = engine->cpu.getReg(ARM_R2), r3 = engine->cpu.getReg(ARM_R3);
            bool handled = engine->fetchHook(engine, faultPc, r0, r1, r2, r3, engine->fetchHookUserData);
            if (handled) continue;
        }

        if (fault != ArmFault::None) {
            VxpErr err = faultToErr(fault);
            bool isFetchFault = (fault == ArmFault::FetchUnmapped || fault == ArmFault::FetchProt);
            uint32_t reportAddress = isFetchFault ? faultPc : engine->cpu.lastFaultAddress();
            if (engine->invalidHook != nullptr) {
                engine->invalidHook(engine, err, reportAddress, 4, engine->invalidHookUserData);
            }
            LOGE("vxp_step: failed with %s at pc=0x%x (fault address=0x%x)",
                 vxp_strerror(err), faultPc, reportAddress);
            return err;
        }
        return VXP_ERR_OK;
    }

    LOGE("vxp_step: exceeded trap-resolution guard (64 unmapped-fetch traps in a row)");
    return VXP_ERR_FETCH_UNMAPPED;
}

void vxp_stop(VxpEngine* engine) {
    if (engine == nullptr) return;
    engine->stopRequested = true;
}

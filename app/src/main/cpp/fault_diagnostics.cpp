#include "fault_diagnostics.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

// Sentinel "installed" handle. VxpEngine (unlike Unicorn) only has a
// single invalidHook slot rather than a chain of installable hooks, so
// there's nothing richer to hand back here - this just needs to be
// non-zero so callers can tell "installed" from "failed to install".
constexpr uint64_t FAULT_DIAGNOSTICS_HOOK_HANDLE = 1;

void onInvalidAccess(VxpEngine* engine, VxpErr err, uint64_t address, int size, void* /*userData*/) {
    uint32_t pc = vxp_get_register(engine, VXP_REG_PC);
    uint32_t sp = vxp_get_register(engine, VXP_REG_SP);
    uint32_t lr = vxp_get_register(engine, VXP_REG_LR);
    uint32_t r0 = vxp_get_register(engine, ARM_R0);
    uint32_t r1 = vxp_get_register(engine, ARM_R1);
    uint32_t r2 = vxp_get_register(engine, ARM_R2);
    uint32_t r3 = vxp_get_register(engine, ARM_R3);
    bool thumb = (vxp_get_register(engine, VXP_REG_CPSR) & (1u << CPSR_BIT_T)) != 0;

    // Best-effort: pull the raw faulting instruction's own bytes via the
    // backdoor apiRead (bypasses exec-permission checks, unlike a real
    // guest fetch) so the log has enough to identify exactly which
    // encoding faulted, not just where. Thumb is 2 bytes, ARM is 4.
    uint32_t insnWord = 0;
    uint8_t insnBuf[4] = {0, 0, 0, 0};
    size_t insnLen = thumb ? 2 : 4;
    bool gotInsn = engine->memory.apiRead(pc, insnBuf, insnLen);
    if (gotInsn) {
        insnWord = static_cast<uint32_t>(insnBuf[0]) | (static_cast<uint32_t>(insnBuf[1]) << 8) |
                   (static_cast<uint32_t>(insnBuf[2]) << 16) | (static_cast<uint32_t>(insnBuf[3]) << 24);
    }

    LOGE(
        "MEMORY FAULT: %s at guest address=0x%llx size=%d | PC=0x%08x SP=0x%08x LR=0x%08x "
        "| insn(%s)=0x%0*x | R0=0x%08x R1=0x%08x R2=0x%08x R3=0x%08x",
        vxp_strerror(err), (unsigned long long) address, size, pc, sp, lr,
        thumb ? "thumb" : "arm", thumb ? 4 : 8, insnWord,
        r0, r1, r2, r3
    );

    // Never "handles" anything - this hook is purely diagnostic. The
    // real fault always still propagates to Executor.kt exactly as it
    // would without this hook installed; this log line just precedes it
    // with the address/PC/instruction/registers that the bare error
    // message alone can't convey.
}

} // namespace

uint64_t vxp_install_fault_diagnostics_hook(VxpEngine* engine) {
    if (engine == nullptr) return 0;

    engine->invalidHook = &onInvalidAccess;
    engine->invalidHookUserData = nullptr;

    LOGI("Fault diagnostics hook installed");
    return FAULT_DIAGNOSTICS_HOOK_HANDLE;
}

void vxp_remove_fault_diagnostics_hook(VxpEngine* engine, uint64_t hookHandle) {
    if (engine == nullptr || hookHandle == 0) return;
    engine->invalidHook = nullptr;
    engine->invalidHookUserData = nullptr;
}

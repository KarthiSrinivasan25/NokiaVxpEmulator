#include "fault_diagnostics.h"
#include "cpu_bridge.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

const char* memTypeName(uc_mem_type type) {
    switch (type) {
        case UC_MEM_READ_UNMAPPED: return "READ_UNMAPPED";
        case UC_MEM_WRITE_UNMAPPED: return "WRITE_UNMAPPED";
        case UC_MEM_FETCH_UNMAPPED: return "FETCH_UNMAPPED";
        case UC_MEM_READ_PROT: return "READ_PROT (mapped but not readable)";
        case UC_MEM_WRITE_PROT: return "WRITE_PROT (mapped but not writable)";
        case UC_MEM_FETCH_PROT: return "FETCH_PROT (mapped but not executable)";
        default: return "UNKNOWN";
    }
}

bool onMemInvalid(uc_engine* uc, uc_mem_type type, uint64_t address, int size, int64_t value, void* /*userData*/) {
    uint32_t pc = vxp_get_register(uc, VXP_REG_PC);
    uint32_t sp = vxp_get_register(uc, VXP_REG_SP);
    uint32_t lr = vxp_get_register(uc, VXP_REG_LR);

    LOGE(
        "MEMORY FAULT: %s at guest address=0x%llx size=%d value=0x%llx | PC=0x%08x SP=0x%08x LR=0x%08x",
        memTypeName(type), (unsigned long long) address, size, (unsigned long long) value, pc, sp, lr
    );

    // Never "handle" it - this hook is purely diagnostic. Returning
    // false lets Unicorn raise the real fault exactly as it would have
    // without this hook installed; RunResult.Error's message is
    // unchanged, but this log line now precedes it with the address/PC
    // that message alone can't convey.
    return false;
}

} // namespace

uint64_t vxp_install_fault_diagnostics_hook(uc_engine* uc) {
    if (uc == nullptr) return 0;

    uc_hook hook;
    uc_err err = uc_hook_add(
        uc, &hook, UC_HOOK_MEM_INVALID,
        reinterpret_cast<void*>(&onMemInvalid), nullptr, 1, 0
    );
    if (err != UC_ERR_OK) {
        LOGE("uc_hook_add for fault diagnostics failed: %s", uc_strerror(err));
        return 0;
    }

    LOGI("Fault diagnostics hook installed");
    return static_cast<uint64_t>(hook);
}

void vxp_remove_fault_diagnostics_hook(uc_engine* uc, uint64_t hookHandle) {
    if (uc == nullptr || hookHandle == 0) return;
    uc_hook_del(uc, static_cast<uc_hook>(hookHandle));
}

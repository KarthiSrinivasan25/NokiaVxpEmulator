#include "fault_diagnostics.h"
#include "cpu_bridge.h"

#include <android/log.h>
#include <unicorn/unicorn.h>
#include <cstdint>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

const char* memTypeName(uc_mem_type type) {
    switch (type) {
        case UC_MEM_READ_UNMAPPED:
            return "READ_UNMAPPED";

        case UC_MEM_WRITE_UNMAPPED:
            return "WRITE_UNMAPPED";

        case UC_MEM_FETCH_UNMAPPED:
            return "FETCH_UNMAPPED";

        case UC_MEM_READ_PROT:
            return "READ_PROT";

        case UC_MEM_WRITE_PROT:
            return "WRITE_PROT";

        case UC_MEM_FETCH_PROT:
            return "FETCH_PROT";

        default:
            return "UNKNOWN";
    }
}

bool onMemInvalid(
        uc_engine* uc,
        uc_mem_type type,
        uint64_t address,
        int size,
        int64_t value,
        void* /*userData*/) {

    if (uc == nullptr) {
        LOGE("MEMORY FAULT: Unicorn engine is NULL");
        return false;
    }

    uint32_t pc  = vxp_get_register(uc, VXP_REG_PC);
    uint32_t sp  = vxp_get_register(uc, VXP_REG_SP);
    uint32_t lr  = vxp_get_register(uc, VXP_REG_LR);

    uint32_t r0  = vxp_get_register(uc, VXP_REG_R0);
    uint32_t r1  = vxp_get_register(uc, VXP_REG_R1);
    uint32_t r2  = vxp_get_register(uc, VXP_REG_R2);
    uint32_t r3  = vxp_get_register(uc, VXP_REG_R3);

    uint32_t r4  = vxp_get_register(uc, VXP_REG_R4);
    uint32_t r5  = vxp_get_register(uc, VXP_REG_R5);
    uint32_t r6  = vxp_get_register(uc, VXP_REG_R6);
    uint32_t r7  = vxp_get_register(uc, VXP_REG_R7);

    uint32_t r8  = vxp_get_register(uc, VXP_REG_R8);
    uint32_t r9  = vxp_get_register(uc, VXP_REG_R9);
    uint32_t r10 = vxp_get_register(uc, VXP_REG_R10);
    uint32_t r11 = vxp_get_register(uc, VXP_REG_R11);
    uint32_t r12 = vxp_get_register(uc, VXP_REG_R12);

    uint32_t cpsr = vxp_get_register(uc, VXP_REG_CPSR);

    LOGE("========================================");
    LOGE("          VXP MEMORY FAULT");
    LOGE("========================================");

    LOGE("Type    : %s", memTypeName(type));
    LOGE("Address : 0x%08llx",
         (unsigned long long) address);

    LOGE("Size    : %d", size);

    LOGE("Value   : 0x%08llx",
         (unsigned long long) value);

    LOGE("----------------------------------------");

    LOGE("PC      : 0x%08x", pc);
    LOGE("SP      : 0x%08x", sp);
    LOGE("LR      : 0x%08x", lr);
    LOGE("CPSR    : 0x%08x", cpsr);

    LOGE("----------------------------------------");

    LOGE(
        "R0 = 0x%08x  R1 = 0x%08x  R2 = 0x%08x  R3 = 0x%08x",
        r0, r1, r2, r3
    );

    LOGE(
        "R4 = 0x%08x  R5 = 0x%08x  R6 = 0x%08x  R7 = 0x%08x",
        r4, r5, r6, r7
    );

    LOGE(
        "R8 = 0x%08x  R9 = 0x%08x  R10 = 0x%08x  R11 = 0x%08x",
        r8, r9, r10, r11
    );

    LOGE(
        "R12 = 0x%08x",
        r12
    );

    LOGE("----------------------------------------");

    LOGE(
        "Fault address relative to PC: %+lld",
        (long long)((int64_t)address - (int64_t)pc)
    );

    LOGE(
        "Fault address relative to R9: %+lld",
        (long long)((int64_t)address - (int64_t)r9)
    );

    LOGE("========================================");

    /*
     * IMPORTANT:
     *
     * Do NOT return true here.
     *
     * Returning true would tell Unicorn that the invalid memory
     * access was handled. We want the real UC_ERR_WRITE_UNMAPPED /
     * UC_ERR_READ_UNMAPPED error to propagate to the emulator.
     */
    return false;
}

} // namespace


uint64_t vxp_install_fault_diagnostics_hook(uc_engine* uc) {

    if (uc == nullptr) {
        LOGE("Cannot install fault hook: uc == NULL");
        return 0;
    }

    uc_hook hook{};

    uc_err err = uc_hook_add(
        uc,
        &hook,
        UC_HOOK_MEM_INVALID,
        reinterpret_cast<void*>(&onMemInvalid),
        nullptr,
        1,
        0
    );

    if (err != UC_ERR_OK) {

        LOGE(
            "uc_hook_add(UC_HOOK_MEM_INVALID) FAILED: %s",
            uc_strerror(err)
        );

        return 0;
    }

    LOGI(
        "Fault diagnostics hook installed successfully. "
        "Hook=%p",
        (void*)hook
    );

    return static_cast<uint64_t>(hook);
}


void vxp_remove_fault_diagnostics_hook(
        uc_engine* uc,
        uint64_t hookHandle) {

    if (uc == nullptr || hookHandle == 0) {
        return;
    }

    uc_err err =
        uc_hook_del(
            uc,
            static_cast<uc_hook>(hookHandle)
        );

    if (err != UC_ERR_OK) {

        LOGE(
            "uc_hook_del failed: %s",
            uc_strerror(err)
        );

        return;
    }

    LOGI("Fault diagnostics hook removed");
}
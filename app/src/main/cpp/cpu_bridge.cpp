#include "cpu_bridge.h"

#include <android/log.h>
#include <cstdio>
#include <cstdint>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


// ---------------------------------------------------------
// Instruction trace
// ---------------------------------------------------------

static void trace_code(
        uc_engine *uc,
        uint64_t address,
        uint32_t size,
        void *user_data)
{
    // Only trace beginning of program for now.
    if (address < 0x8000 || address >= 0x8100)
        return;

    uint32_t r0 = 0;
    uint32_t r1 = 0;
    uint32_t r2 = 0;
    uint32_t r3 = 0;
    uint32_t sp = 0;
    uint32_t lr = 0;
    uint32_t pc = 0;

    uc_reg_read(uc, UC_ARM_REG_R0, &r0);
    uc_reg_read(uc, UC_ARM_REG_R1, &r1);
    uc_reg_read(uc, UC_ARM_REG_R2, &r2);
    uc_reg_read(uc, UC_ARM_REG_R3, &r3);

    uc_reg_read(uc, UC_ARM_REG_SP, &sp);
    uc_reg_read(uc, UC_ARM_REG_LR, &lr);
    uc_reg_read(uc, UC_ARM_REG_PC, &pc);

    uint8_t bytes[8] = {};

    if (size > sizeof(bytes))
        size = sizeof(bytes);

    uc_err err = uc_mem_read(
            uc,
            address,
            bytes,
            size);

    if (err == UC_ERR_OK) {
        char hex[32] = {};
        char *p = hex;

        for (uint32_t i = 0; i < size; i++) {
            p += snprintf(
                    p,
                    sizeof(hex) - (p - hex),
                    "%02X ",
                    bytes[i]);
        }

        LOGI(
            "EXEC PC=0x%08llX size=%u bytes=[%s]"
            " R0=%08X R1=%08X R2=%08X R3=%08X"
            " SP=%08X LR=%08X",
            (unsigned long long)address,
            size,
            hex,
            r0, r1, r2, r3,
            sp,
            lr);
    } else {
        LOGI(
            "EXEC PC=0x%08llX size=%u"
            " R0=%08X R1=%08X R2=%08X R3=%08X"
            " SP=%08X LR=%08X",
            (unsigned long long)address,
            size,
            r0, r1, r2, r3,
            sp,
            lr);
    }
}


// ---------------------------------------------------------
// Install trace
// ---------------------------------------------------------

void install_instruction_trace(uc_engine *uc)
{
    if (uc == nullptr)
        return;

    uc_hook trace;

    uc_err err = uc_hook_add(
        uc,
        &trace,
        UC_HOOK_CODE,
        trace_code,
        nullptr,
        0,
        0);

    if (err != UC_ERR_OK) {
        LOGE(
            "Instruction trace hook failed: %s",
            uc_strerror(err));
        return;
    }

    LOGI("Instruction trace hook installed");
}